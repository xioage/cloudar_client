package symlab.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

import symlab.cloudridar.Markers;
import symlab.core.adapter.RenderAdapter;
import symlab.core.impl.MarkerImpl;
import symlab.core.task.FrameTrackingTask;
import symlab.core.task.ReceivingTask;
import symlab.core.task.TransmissionTask;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ArManager {

    static private ArManager instance;

    private final int FRAME_THREAD_SIZE = 2;

    private Handler handlerUtil;
    private Handler handlerNetwork;
    private Handler[] handlerFrame;

    private FrameTrackingTask[] taskFrame;
    private ReceivingTask taskReceiving;
    private TransmissionTask taskTransmission;
    private MarkerImpl markerManager;
    private DatagramChannel dataChannel;
    private SocketAddress serverAddr;

    private int frameID = 0;

    private ArManager(){ super(); }

    static public ArManager getInstance() {
        synchronized (ArManager.class){
            if (instance == null) instance = new ArManager();
        }
        return instance;
    }

    private Handler createAndStartThread(String name){
        HandlerThread handlerThread = new HandlerThread(name);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    public void init(RenderAdapter renderAdapter){
        System.loadLibrary("opencv_java");

        this.handlerUtil = createAndStartThread("util thread"); //start util thread
        this.handlerNetwork = createAndStartThread("network thread");
        this.handlerFrame = new Handler[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){ //start frame processing thread
            this.handlerFrame[i] = createAndStartThread(String.format("frame thread %d", i));
        }

        handlerNetwork.post(new Runnable() {
            @Override
            public void run() {
                try {
                    serverAddr = new InetSocketAddress(Constants.ip, Constants.portNum);
                    dataChannel = DatagramChannel.open();
                    dataChannel.configureBlocking(false);
                    dataChannel.socket().connect(serverAddr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        while(dataChannel == null);

        markerManager = new MarkerImpl(handlerUtil);
        taskTransmission = new TransmissionTask(dataChannel, serverAddr);
        taskReceiving = new ReceivingTask(dataChannel);
        taskFrame = new FrameTrackingTask[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){
            taskFrame[i] = new FrameTrackingTask();
            taskFrame[i].setCallback(markerManager);
        }

        markerManager.setRenderAdapter(renderAdapter);
        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onSample(int frameId, byte[] frameData) {
                taskTransmission.setData(frameId, frameData);
                handlerNetwork.post(taskTransmission);
                taskReceiving.updateLatestSentID(frameId);
            }
        });

        taskReceiving.setCallback(new ReceivingTask.Callback() {
            @Override
            public void onReceive(int resultID, Markers markers) {
                markerManager.updateMarkers(markers, resultID);
            }
        });
    }

    public void start() {
        taskTransmission.setData(0, null);
        for(int i = 0; i < 3; i++)
            handlerNetwork.post(taskTransmission);
    }

    public void stop() {
        taskTransmission.setData(-1, null);
        for(int i = 0; i < 3; i++)
            handlerNetwork.post(taskTransmission);

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

        for(int i = 0; i < FRAME_THREAD_SIZE; i++)
            handlerFrame[i].getLooper().quitSafely();
        handlerNetwork.getLooper().quitSafely();
        handlerUtil.getLooper().quitSafely();
    }

    public void driveFrame(byte[] frameData){
        int taskId = frameID % FRAME_THREAD_SIZE;
        FrameTrackingTask task = taskFrame[taskId];
        if (task.isBusy()) return;
        task.setFrameData(++frameID, frameData);
        handlerFrame[taskId].post(task);

        handlerNetwork.post(taskReceiving);
    }

    public int frameSnapshot(){
        return frameID;
    }
}
