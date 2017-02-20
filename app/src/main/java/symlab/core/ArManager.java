package symlab.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

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
    private DatagramChannel dataChannel;
    private SocketAddress serverAddr;
    private MarkerImpl markerManager;

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

    public void init(DatagramChannel datagramChannel, SocketAddress socketAddress, RenderAdapter renderAdapter){

        this.handlerUtil = createAndStartThread("util thread");//start util thread
        this.handlerNetwork = createAndStartThread("network thread");


        markerManager = new MarkerImpl(handlerUtil);

        this.handlerFrame = new Handler[FRAME_THREAD_SIZE];
        this.taskFrame = new FrameTrackingTask[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){ //start frame processing thread
            this.handlerFrame[i] = createAndStartThread(String.format("frame thread %d", i));
            taskFrame[i] = new FrameTrackingTask();
            taskFrame[i].setCallback(markerManager);
        }

        this.dataChannel = datagramChannel;
        this.serverAddr = socketAddress;

        this.taskTransmission = new TransmissionTask();
        this.taskReceiving = new ReceivingTask(datagramChannel);

        markerManager.setRenderAdapter(renderAdapter);
        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onSample(int frameId, byte[] frameData) {
                ArManager.this.taskTransmission.setData(frameId, frameData, dataChannel, serverAddr);
                handlerNetwork.post(ArManager.this.taskTransmission);
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

    public void driveFrame(byte[] frameData){
        int taskId = frameID % FRAME_THREAD_SIZE;
        FrameTrackingTask task = taskFrame[taskId];
        if (task.isBusy()) return;
        task.setFrameData(++frameID, frameData);
        handlerFrame[taskId].post(task);

        handlerNetwork.post(this.taskReceiving);
    }

    public int frameSnapshot(){
        return frameID;
    }
}
