package symlab.CloudAR;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import symlab.CloudAR.marker.MarkerGroup;
import symlab.CloudAR.network.AnnotationTask;
import symlab.CloudAR.track.MarkerImpl;
import symlab.CloudAR.track.MatchingTask;
import symlab.CloudAR.track.MatchingTaskSlow;
import symlab.CloudAR.track.TrackingTask;
import symlab.CloudAR.network.ReceivingTask;
import symlab.CloudAR.network.TransmissionTask;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ARManager {

    static private ARManager instance;

    private Handler handlerUtil;
    private Handler handlerFrame;
    private Handler handlerNetwork;

    private MarkerImpl markerManager;
    private TrackingTask taskFrame;
    private TransmissionTask taskTransmission;
    private ReceivingTask taskReceiving;
    private MatchingTaskSlow taskMatching;
    private AnnotationTask taskAnnotation;

    private DatagramChannel dataChannel;
    private SocketAddress serverAddr;
    private String ip;
    private int port;

    private Context context;
    private boolean isCloudBased;
    private boolean isAnnotationReceived = true;

    private int frameID = 0;
    private MarkerGroup markers;
    private SparseArray<String> annotations;

    private ARManager(){ super(); }

    static public ARManager getInstance() {
        synchronized (ARManager.class){
            if (instance == null) instance = new ARManager();
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
        File file = new File(sdcard,"CloudAR/cloudConfig.txt");
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            ip = br.readLine();
            port = Integer.parseInt(br.readLine());
            br.close();

            serverAddr = new InetSocketAddress(ip, port);
            dataChannel = DatagramChannel.open();
            dataChannel.configureBlocking(false);
            dataChannel.socket().connect(serverAddr);
        } catch (IOException e) {
            Log.d(Constants.TAG, "config file error");
        } catch (Exception e) {}
    }

    public void init(Context context, boolean isCloudBased){
        this.context = context;
        this.isCloudBased = isCloudBased;

        System.loadLibrary("opencv_java");
        System.loadLibrary("nonfree");

        this.handlerUtil = createAndStartThread("util thread", Process.THREAD_PRIORITY_DEFAULT); //start util thread
        this.handlerFrame = createAndStartThread("frame thread" , -1); //start frame processing thread
        this.handlerNetwork = createAndStartThread("network thread", 1);
    }

    public void start() {
        if(isCloudBased) initConnection();

        markerManager = new MarkerImpl(handlerUtil);
        taskFrame = new TrackingTask();
        taskFrame.setCallback(markerManager);
        if(isCloudBased) {
            taskTransmission = new TransmissionTask(dataChannel, serverAddr);
            taskReceiving = new ReceivingTask(dataChannel);
            taskReceiving.setCallback(new ReceivingTask.Callback() {
                @Override
                public void onReceive(int resultID, MarkerGroup markerGroup) {
                    markerManager.updateMarkers(markerGroup, resultID);
                }
            });
            taskAnnotation = new AnnotationTask(ip, port);
            taskAnnotation.setCallback(new AnnotationTask.Callback() {
                @Override
                public void onReceive(int markerID, String filePath) {
                    callback.onAnnotationReceived(markerID, filePath);
                    isAnnotationReceived = true;
                    annotations.append(markerID, filePath);
                }
            });
            annotations = new SparseArray<>();
        } else {
            taskMatching = new MatchingTaskSlow(context);
            taskMatching.setCallback(new MatchingTaskSlow.Callback() {
                @Override
                public void onFinish(MarkerGroup markerGroup, int frameID) {
                    markerManager.updateMarkers(markerGroup, frameID);
                }
            });
            handlerNetwork.post(taskMatching);
        }

        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onMarkersRecognized(MarkerGroup markerGroup) {
                callback.onMarkersReady(markerGroup);

                if(isCloudBased) {
                    int markerID = markerGroup.getIDs().get(0);
                    String annotation = annotations.get(markerID);
                    if (annotation != null) {
                        Log.d(Constants.TAG, "local annotation found: " + annotation);
                        callback.onAnnotationReceived(markerID, annotation);
                        isAnnotationReceived = true;
                    } else {
                        taskAnnotation.setMarkerID(markerID);
                        handlerNetwork.post(taskAnnotation);
                        isAnnotationReceived = false;
                    }
                }
            }

            @Override
            public void onMarkersChanged(MarkerGroup markerGroup) {
                callback.onMarkersChanged(markerGroup);
            }
        });
    }

    public void stop() {
        if(isCloudBased) handlerNetwork.post(new Runnable() {
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
            handlerUtil.getLooper().quitSafely();
            handlerNetwork.getLooper().quitSafely();
        }
    }

    public void recognize(byte[] frameData) {
        if(!isAnnotationReceived) return;

        taskFrame.setFrameData(++frameID, frameData, true);
        handlerFrame.post(taskFrame);

        if(isCloudBased) {
            taskTransmission.setData(frameID, frameData);
            handlerNetwork.post(taskTransmission);
            taskReceiving.updateLatestSentID(frameID);
        } else {
            taskMatching.setData(frameID, frameData);
            handlerNetwork.post(taskMatching);
        }
    }

    public void driveFrame(byte[] frameData) {
        if (taskFrame.isBusy()) return;
        taskFrame.setFrameData(++frameID, frameData, false);
        handlerFrame.post(taskFrame);

        if(isCloudBased) handlerNetwork.post(taskReceiving);
    }

    public int frameSnapshot() {
        return frameID;
    }

    private ARManager.Callback callback;

    public void setCallback(ARManager.Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onMarkersReady(MarkerGroup markerGroup);
        void onMarkersChanged(MarkerGroup markerGroup);
        void onAnnotationReceived(int markerID, String annotationFile);
    }
}
