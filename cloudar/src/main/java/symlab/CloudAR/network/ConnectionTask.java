package symlab.CloudAR.network;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

import symlab.CloudAR.definition.Constants;

public class ConnectionTask implements Runnable {
    private Channel channel;
    private SocketAddress serverAddress;
    private String ip;
    private int port;
    private boolean isUDPBased;

    public ConnectionTask(boolean isUDPBased) {
        this.isUDPBased = isUDPBased;
    }

    @Override
    public void run() {
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"CloudAR/cloudConfig.txt");
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            ip = br.readLine();
            port = Integer.parseInt(br.readLine());
            br.close();

            serverAddress = new InetSocketAddress(ip, port);
            if(isUDPBased) {
                channel = DatagramChannel.open();
                ((DatagramChannel)channel).connect(serverAddress);
                ((DatagramChannel)channel).configureBlocking(false);
            } else {
                channel = SocketChannel.open();
                ((SocketChannel)channel).connect(serverAddress);
                ((SocketChannel)channel).configureBlocking(false);
            }

            callback.onConnectionBuilt(ip, port, channel, serverAddress);
        } catch (IOException e) {
            Log.d(Constants.TAG, "config file error");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onConnectionBuilt(String ip, int port, Channel chan, SocketAddress serverAddress);
    }
}
