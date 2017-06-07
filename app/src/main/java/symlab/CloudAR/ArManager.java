package symlab.CloudAR;

import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import symlab.CloudAR.marker.MarkerGroup;
import symlab.CloudAR.renderer.ARRenderer;
import symlab.CloudAR.track.MarkerImpl;
import symlab.CloudAR.track.TrackingTask;
import symlab.CloudAR.network.ReceivingTask;
import symlab.CloudAR.network.TransmissionTask;

import static symlab.CloudAR.Constants.TAG;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ArManager {

    static private ArManager instance;

    private Handler handlerUtil;
    private Handler handlerNetwork;
    private Handler handlerFrame;

    private MarkerImpl markerManager;
    private TransmissionTask taskTransmission;
    private ReceivingTask taskReceiving;
    private TrackingTask taskFrame;

    private DatagramChannel dataChannel;
    private SocketAddress serverAddr;

    private ARRenderer mRenderer;

    private int frameID = 0;

    private ArManager(){ super(); }

    static public ArManager getInstance() {
        synchronized (ArManager.class){
            if (instance == null) instance = new ArManager();
        }
        return instance;
    }

    private Handler createAndStartThread(String name, int priority){
        HandlerThread handlerThread = new HandlerThread(name, priority);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    private void initConnection() {
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"cloudConfig.txt");
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            String ip = br.readLine();
            int portNum = Integer.parseInt(br.readLine());
            br.close();

            serverAddr = new InetSocketAddress(ip, portNum);
            dataChannel = DatagramChannel.open();
            dataChannel.configureBlocking(false);
            dataChannel.socket().connect(serverAddr);
        } catch (IOException e) {
            Log.d(TAG, "config file error");
        } catch (Exception e) {}
    }

    public void init(final ARRenderer mRenderer){
        this.mRenderer = mRenderer;

        System.loadLibrary("opencv_java");
        initConnection();

        this.handlerUtil = createAndStartThread("util thread", Process.THREAD_PRIORITY_DEFAULT); //start util thread
        this.handlerNetwork = createAndStartThread("network thread", 1);
        this.handlerFrame = createAndStartThread("frame thread" , -1); //start frame processing thread

        markerManager = new MarkerImpl(handlerUtil);
        taskTransmission = new TransmissionTask(dataChannel, serverAddr);
        taskReceiving = new ReceivingTask(dataChannel);
        taskFrame = new TrackingTask(markerManager);

        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onSample(int frameId, byte[] frameData) {
                taskTransmission.setData(frameId, frameData);
                handlerNetwork.post(taskTransmission);
                taskReceiving.updateLatestSentID(frameId);
            }

            @Override
            public void onMarkersChanged(MarkerGroup markerGroup) {
                mRenderer.updateContents(markerGroup);
            }
        });

        taskReceiving.setCallback(new ReceivingTask.Callback() {
            @Override
            public void onReceive(int resultID, MarkerGroup markerGroup) {
                markerManager.updateMarkers(markerGroup, resultID);
            }
        });
    }

    public void start() {
    }

    public void stop() {
        handlerNetwork.post(new Runnable() {
            @Override
            public void run() {
                try {
                    dataChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            handlerFrame.getLooper().quitSafely();
            handlerNetwork.getLooper().quitSafely();
            handlerUtil.getLooper().quitSafely();
        }
    }

    public void driveFrame(byte[] frameData){
        if (taskFrame.isBusy()) return;
        taskFrame.setFrameData(++frameID, frameData);
        handlerFrame.post(taskFrame);

        handlerNetwork.post(taskReceiving);
    }

    public int frameSnapshot(){
        return frameID;
    }
}
