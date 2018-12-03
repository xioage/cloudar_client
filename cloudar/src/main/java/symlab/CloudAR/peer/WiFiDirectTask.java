package symlab.CloudAR.peer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;

import static android.os.Looper.getMainLooper;

/**
 * Created by wzhangal on 5/22/2017.
 */

public class WiFiDirectTask implements PeerTask {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private IntentFilter mIntentFilter;

    private Context context;
    private Handler handler;
    private String TAG = "wifidirect";
    private boolean isConnected = false;
    private boolean isServer = false;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private SendTask sendTask;

    private int port = 22222;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    private Collection<WifiP2pDevice> curDeviceList;
    private byte[] data, result, status;

    private Callback callback;

    public WiFiDirectTask(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
    }

    public void setData(byte[] data) {
        if(this.isConnected) {
            sendTask.setOutputBuffer(data);
            handler.post(sendTask);
        } else
            this.data = data;
    }

    public void setResult(byte[] result) {
        if(this.isConnected && this.data == null) {
            sendTask.setOutputBuffer(result);
            handler.post(sendTask);
        } else
            this.result = result;
    }

    @Override
    public void setAnnotationStatus(byte[] status) {
        if(this.isConnected && this.data == null && this.result == null) {
            sendTask.setOutputBuffer(status);
            handler.post(sendTask);
        } else
            this.status = status;
    }

    @Override
    public void start() {
        Log.d(TAG, "start");
        mManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(context, getMainLooper(), null);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        context.registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        this.context.unregisterReceiver(mReceiver);

        if(connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if(connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if(acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        disconnect();
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void disconnect() {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "peer disconnected");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "peer disconnect error");
            }
        });
    }

    private void discoverPeers() {
        Log.d(TAG, "discover peers");
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "discover peers failed");
            }
        });
    }

    private void connectToPeer(final WifiP2pDevice device) {
        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //success logic
                Log.d(TAG, "peer connected");
            }

            @Override
            public void onFailure(int reason) {
                //failure logic
            }
        });
    }

    private class AcceptThread extends Thread {
        private ServerSocket serverSocket;

        public AcceptThread() {
            Log.d(TAG, "accept thread");
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                Log.d(TAG, "wait for incoming connection");
                socket = serverSocket.accept();

                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (IOException e) {
                Log.e(TAG, "receive error");
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectThread extends Thread {
        private InetAddress address;

        public ConnectThread(InetAddress address) {
            Log.d(TAG, "connect thread");
            this.address = address;
        }

        public void run() {
            try {
                Thread.sleep(2000);
                Log.d(TAG, "try to connect to server");
                socket = new Socket();
                socket.bind(null);
                socket.connect((new InetSocketAddress(address, port)), 500);

                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private Socket mSocket;
        private boolean running = true;
        private boolean recoReceived = false;

        private byte[] packet;
        private int curLength;
        private int frameID, dataType, packetLength, markerNum;

        public ConnectedThread(Socket socket) {
            Log.d(TAG, "connected thread");
            mSocket = socket;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "socket failed");
            }

            isConnected = true;
            sendTask = new SendTask();
            //if(data != null) setOutputBuffer(data);
            //if(result != null) setOutputBuffer(result);
            //if(isServer) setOutputBuffer(new byte[5000000]);
        }

        public void run() {
            byte[] buffer = new byte[50000];
            byte[] tmp = new byte[4];
            int bytes;
            int startPoint;
            int annoLen = 0;
            Long ts_s = null, ts_e;

            while(running) {
                try {
                    bytes = inputStream.read(buffer);
                    if(!recoReceived) {
                        startPoint = 0;
                        if (bytes > 0) {
                            //Log.d(TAG, "received: " + bytes + "ts: " + System.currentTimeMillis().toString());
                        }

                        while (bytes > 0) {
                            if (curLength == 0) {
                                System.arraycopy(buffer, startPoint, tmp, 0, 4);
                                frameID = ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).getInt();
                                System.arraycopy(buffer, startPoint + 4, tmp, 0, 4);
                                dataType = ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).getInt();
                                System.arraycopy(buffer, startPoint + 8, tmp, 0, 4);
                                if (dataType == peerBoundary) {
                                    packetLength = 500;
                                    markerNum = ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).getInt();
                                } else if (dataType == peerImage){
                                    packetLength = ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).getInt();
                                } else {
                                    packetLength = 16;
                                }

                                packet = new byte[packetLength];
                                startPoint += 12;
                                bytes -= 12;
                            }

                            if (bytes < packetLength - curLength) {
                                System.arraycopy(buffer, startPoint, packet, curLength, bytes);
                                curLength += bytes;
                                startPoint += bytes;
                                bytes = 0;
                            } else {
                                System.arraycopy(buffer, startPoint, packet, curLength, packetLength - curLength);
                                if (dataType == peerBoundary) {
                                    callback.onReceive(frameID, dataType, markerNum, packet);
                                    recoReceived = true;
                                } else {
                                    callback.onReceive(frameID, dataType, packetLength, packet);
                                }

                                startPoint += packetLength - curLength;
                                bytes -= packetLength - curLength;
                                curLength = 0;
                            }
                        }
                    } else {
                        if(annoLen == 0) {
                            ts_s = System.currentTimeMillis();
                        }
                        annoLen += bytes;
                        if(annoLen == 5000000) {
                            ts_e = System.currentTimeMillis();
                            Log.d(TAG, "anno received in " + (ts_e - ts_s));
                        }
                    }
                } catch (IOException e) {
                    Log.d(TAG, "read from socket failed");
                    break;
                }
            }
        }

        void cancel() {
            try {
                running = false;
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendTask implements Runnable {
        private byte[] outputBuffer;

        void setOutputBuffer(byte[] buffer) {
            this.outputBuffer = buffer;
        }

        @Override
        public void run() {
            try {
                outputStream.write(outputBuffer);
                //Log.d(TAG, "data sent: " + outputBuffer.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList peers) {
                    Collection<WifiP2pDevice> deviceList = peers.getDeviceList();
                    if(curDeviceList != null)
                        return;

                    for (WifiP2pDevice device : deviceList) {
                        Log.d(TAG, "connect to new peer: " + device.deviceName);
                        connectToPeer(device);
                    }
                    curDeviceList = deviceList;
                }
            };

            WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    InetAddress groupOwnerAddress = info.groupOwnerAddress;

                    if(info.groupFormed && info.isGroupOwner) {
                        Log.d(TAG, "serves as server");
                        callback.onPeerDiscoveryFinished(true);

                        acceptThread = new AcceptThread();
                        acceptThread.start();
                        isServer = true;
                    } else if(info.groupFormed) {
                        Log.d(TAG, "serves as client");
                        callback.onPeerDiscoveryFinished(true);

                        connectThread = new ConnectThread(groupOwnerAddress);
                        connectThread.start();
                    }
                }
            };

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "wifi p2p enabled");
                    discoverPeers();
                } else {
                    Log.d(TAG, "wifi p2p not enabled");
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                // request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                Log.d(TAG, "peers changed");
                if (mManager != null) {
                    mManager.requestPeers(mChannel, peerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                Log.d(TAG, "connection changed");
                if (mManager != null) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                    if (networkInfo.isConnected()) {
                        Log.d(TAG, "connection established");
                        mManager.requestConnectionInfo(mChannel, connectionInfoListener);
                    } else {
                        Log.d(TAG, "not connected");
                        callback.onPeerDiscoveryFinished(false);
                        discoverPeers();
                    }
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                //Log.d(TAG, "this device changed");
            }
        }
    };
}
