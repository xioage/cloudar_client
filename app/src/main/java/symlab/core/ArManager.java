package symlab.core;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import org.opencv.core.MatOfPoint3f;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

import symlab.core.model.MarkerGroup;
import symlab.core.adapter.RenderAdapter;
import symlab.core.track.MarkerImpl;
import symlab.core.track.TrackingTask;
import symlab.core.network.ReceivingTask;
import symlab.core.network.TransmissionTask;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ArManager {

    static private ArManager instance;

    private final int FRAME_THREAD_SIZE = 2;

    private Handler handlerUtil;
    private Handler handlerNetwork;
    private Handler[] handlerFrame;

    private TrackingTask[] taskFrame;
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

    private Handler createAndStartThread(String name, int priority){
        HandlerThread handlerThread = new HandlerThread(name, priority);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    public void init(final RenderAdapter renderAdapter){
        System.loadLibrary("opencv_java");
        try {
            serverAddr = new InetSocketAddress(Constants.ip, Constants.portNum);
            dataChannel = DatagramChannel.open();
            dataChannel.configureBlocking(false);
            dataChannel.socket().connect(serverAddr);
        } catch (Exception e) {
        }

        this.handlerUtil = createAndStartThread("util thread", Process.THREAD_PRIORITY_DEFAULT); //start util thread
        this.handlerNetwork = createAndStartThread("network thread", 1);
        this.handlerFrame = new Handler[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){ //start frame processing thread
            this.handlerFrame[i] = createAndStartThread(String.format("frame thread %d", i), -1);
        }

        markerManager = new MarkerImpl(handlerUtil);
        taskTransmission = new TransmissionTask(dataChannel, serverAddr);
        taskReceiving = new ReceivingTask(dataChannel);
        taskFrame = new TrackingTask[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){
            taskFrame[i] = new TrackingTask();
            taskFrame[i].setCallback(markerManager);
        }

        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onSample(int frameId, byte[] frameData) {
                taskTransmission.setData(frameId, frameData);
                handlerNetwork.post(taskTransmission);
                taskReceiving.updateLatestSentID(frameId);
            }

            @Override
            public void onMarkersChanged(ArrayList<MarkerImpl.Marker> markers) {
                /**
                 * TODO further modify the adding render information
                 *
                 * marker is only provide the basic model matrix information here.
                 *
                 * Now we just directly send the marker information to renderAdapter, and what to render
                 * according to these marker is fixed in {@link symlab.cloudridar.MyRenderer}
                 * However, in the future, the render content should be added here to build a concrete
                 * data structure, for example RenderObjects. then send out by renderAdapter.
                 */
                renderAdapter.onRender(markers);
            }
        });

        taskReceiving.setCallback(new ReceivingTask.Callback() {
            @Override
            public void onReceive(int resultID, MarkerGroup markerGroup) {
                /**
                 * TODO further modify to support that origin size returned by server.
                 *
                 * now we are simply input the origin size by constants, in the future, these data should
                 * be returned by server. Whatever how the {@link MarkerGroup}  would changed,
                 * the input structure {@link symlab.core.track.MarkerImpl.Marker} remains the same.
                 * All we need to do is transforming the size here.
                 */
                ArrayList<MarkerImpl.Marker> arrayList = new ArrayList<MarkerImpl.Marker>();
                for (int i=0; i<markerGroup.size(); i++){
                    MatOfPoint3f origin = new MatOfPoint3f();
                    origin.fromArray(Constants.posterPointsData[markerGroup.getID(0)]);
                    arrayList.add(new MarkerImpl.Marker(markerGroup.getID(i), origin,  markerGroup.getRec(i)));
                }
                markerManager.updateMarkers(arrayList, resultID);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            for(int i = 0; i < FRAME_THREAD_SIZE; i++)
                    handlerFrame[i].getLooper().quitSafely();
            handlerNetwork.getLooper().quitSafely();
            handlerUtil.getLooper().quitSafely();
        }
    }

    public void driveFrame(byte[] frameData){
        int taskId = frameID % FRAME_THREAD_SIZE;
        TrackingTask task = taskFrame[taskId];
        if (task.isBusy()) return;
        task.setFrameData(++frameID, frameData);
        handlerFrame[taskId].post(task);

        handlerNetwork.post(taskReceiving);
    }

    public int frameSnapshot(){
        return frameID;
    }
}
