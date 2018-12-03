package symlab.CloudAR.network;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

import symlab.CloudAR.definition.Constants;

public class AnnotationTask implements Runnable {
    private byte[] buffer = new byte[10000];
    private String ip;
    private int port;
    private int markerID;
    private BufferedInputStream inputStream;
    private FileOutputStream file;
    private String filePath;

    public AnnotationTask(String ip, int port){
        this.ip = ip;
        this.port = port;
    }

    public void setMarkerID(int markerID) {
        this.markerID = markerID;
    }

    @Override
    public void run() {
        Log.d(Constants.TAG, "annotation task starts for marker " + markerID);
        Long ts_s, ts_e;
        int totalLength = 0;
        filePath = "/CloudAR/" + markerID + ".mp4";
        try {
            file = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            Socket socket = new Socket(ip, port);
            inputStream = new BufferedInputStream(socket.getInputStream());
            ts_s = System.currentTimeMillis();
            while(true) {
                int len = inputStream.read(buffer);
                if(len == -1) break;
                file.write(buffer, 0, len);
                totalLength += len;
            }
            file.close();
            callback.onReceive(markerID, filePath);
            ts_e = System.currentTimeMillis();
            Log.d(Constants.Eval, "total " + totalLength + " received using " + (ts_e - ts_s));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onReceive(int markerID, String filePath);
    }
}