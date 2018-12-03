package symlab.CloudAR.peer;

/**
 * Created by wzhangal on 5/3/2017.
 */

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothTask implements PeerTask{
    // Debugging
    private static final String TAG = "BluetoothTask";
    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "Bluetooth";
    // Unique UUID for this application
    private static final UUID MY_UUID_INSECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private Context context;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private boolean isServer = true;
    private byte[] data, result;

    private Callback callback;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new Bluetooth session.
     *
     */
    public BluetoothTask(Context context) {
        this.context = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mReceiver, filter);
        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(mReceiver, filter);
    }

    public void setData(byte[] data) {
        if(mState == STATE_CONNECTED)
            write(data);
        else
            this.data = data;
    }

    public void setResult(byte[] result) {
        if(mState == STATE_CONNECTED && this.data == null)
            write(result);
        else
            this.result = result;
    }

    private synchronized void setState(int state) {
        //Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    public void setAnnotationStatus(byte[] status) {}

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");
        setState(STATE_LISTEN);
        mAdapter.setName("carsDevice");

        if (mAdapter.isDiscovering()) {
            Log.d(TAG, "is discovering");
            mAdapter.cancelDiscovery();
        }
        mAdapter.startDiscovery();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
        if(isServer) {
            if (this.data != null) write(this.data);
            if (this.result != null) write(this.result);
        }
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        setState(STATE_NONE);
        mAdapter.setName("carsDevice");

        context.unregisterReceiver(mReceiver);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
        }
        // Perform the write unsynchronized
        mConnectedThread.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        //BluetoothTask.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        //BluetoothTask.this.start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            Log.d(TAG, "accept thread starts");
            try {
                mmServerSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_SECURE, MY_UUID_INSECURE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Accept() failed");
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothTask.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread");

        }

        public void cancel() {
            Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            Log.d(TAG, "connect thread starts");
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                 tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            if(mAdapter.isDiscovering())
                mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothTask.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private byte[] packet;
        private int frameID, dataType, packetLength;
        private int curLength = 0;
        private byte[] tmp = new byte[4];

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            byte[] head = null;
            int bytes;
            Long tsLong;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    //tsLong = System.currentTimeMillis();
                    //Log.d(TAG, "packet start: " + tsLong.toString() + " " + bytes);

                    if(curLength == 0) {
                        if(head == null) {
                            System.arraycopy(buffer, 0, tmp, 0, 4);
                            frameID = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            System.arraycopy(buffer, 4, tmp, 0, 4);
                            dataType = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            System.arraycopy(buffer, 8, tmp, 0, 4);
                            packetLength = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            if(dataType == peerBoundary)
                                packet = new byte[500];
                            else
                                packet = new byte[packetLength];
                            System.arraycopy(buffer, 12, packet, 0, bytes - 12);
                            curLength += bytes - 12;
                        } else {
                            byte[] res = new byte[512];
                            System.arraycopy(head, 0, res, 0, head.length);
                            System.arraycopy(buffer, 0, res, head.length, bytes);
                            dataType = peerBoundary;
                            System.arraycopy(res, 8, tmp, 0, 4);
                            packetLength = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            head = new byte[500];
                            System.arraycopy(res, 12, head, 0, 500);
                        }
                    } else {
                        if(bytes <= packetLength - curLength) {
                            System.arraycopy(buffer, 0, packet, curLength, bytes);
                            curLength += bytes;
                        } else {
                            System.arraycopy(buffer, 0, packet, curLength, packetLength - curLength);
                            head = new byte[bytes - packetLength + curLength];
                            System.arraycopy(buffer, packetLength - curLength, head, 0, head.length);
                            curLength = packetLength;
                        }
                    }

                    if(dataType == peerImage && curLength == packetLength) {
                        callback.onReceive(frameID, dataType, packetLength, packet);
                        packet = null;
                        curLength = 0;
                    } else if(dataType == peerBoundary) {
                        callback.onReceive(frameID, dataType, packetLength, packet);
                        packet = null;
                        curLength = 0;
                    }

                    if(head != null && head.length == 512) {
                        System.arraycopy(head, 8, tmp, 0, 4);
                        packetLength = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        byte[] res = new byte[500];
                        System.arraycopy(head, 12, res, 0, 500);
                        callback.onReceive(frameID, peerBoundary, packetLength, res);
                        head = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected");
                    connectionLost();
                    // Start the service over to restart listening mode
                    //BluetoothTask.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                Log.d(TAG, "data sent: " + buffer.length);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        ParcelUuid[] uuids;
        Long ts_start, ts_now;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                Log.d(TAG, "device found: " + deviceName);
                if(deviceName != null && deviceName.equals("carsServer")) {
                    mAdapter.cancelDiscovery();
                    connect(device);
                    isServer = false;
                    callback.onPeerDiscoveryFinished(true);
                }

                if(ts_start == null)
                    ts_start = System.currentTimeMillis();
                else {
                    ts_now = System.currentTimeMillis();
                    if((ts_now - ts_start) > 2000)
                        mAdapter.cancelDiscovery();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(isServer) {
                    mAdapter.setName("carsServer");
                    callback.onPeerDiscoveryFinished(false);
                    mSecureAcceptThread = new AcceptThread();
                    mSecureAcceptThread.start();
                }
            }
        }
    };

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}

