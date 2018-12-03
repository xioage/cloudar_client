package symlab.CloudAR.manager;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.definition.MarkerGroup;
import symlab.CloudAR.network.AnnotationTask;
import symlab.CloudAR.network.ConnectionTask;
import symlab.CloudAR.network.ReceivingTask;
import symlab.CloudAR.network.SendingTask;
import symlab.CloudAR.network.TCPReceivingTask;
import symlab.CloudAR.network.TCPSendingTask;
import symlab.CloudAR.network.UDPReceivingTask;
import symlab.CloudAR.network.UDPSendingTask;
import symlab.CloudAR.recognition.MatchingTask;
import symlab.CloudAR.recognition.MatchingTaskNative;
import symlab.CloudAR.recognition.MatchingTaskSIFT;
import symlab.CloudAR.track.MarkerImpl;
import symlab.CloudAR.track.TrackingTask;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ARManager {
    Handler handlerUtil;
    Handler handlerFrame;
    Handler handlerNetwork;

    MarkerImpl markerManager;
    TrackingTask taskFrame;
    ConnectionTask taskConnection;
    SendingTask taskSending;
    ReceivingTask taskReceiving;
    MatchingTask taskMatching;
    AnnotationTask taskAnnotation;

    Channel channel;

    Context context;
    boolean isCloudBased;
    boolean isCloudAnnotation;
    boolean isNativeMatching;
    boolean isUDPBased = false;
    boolean isAnnotationReceived = true;
    Set<Integer> contentIDs;

    int frameID = 0;
    SparseArray<String> annotations;

    public ARManager(){}

    private Handler createAndStartThread(String name, int priority){
        HandlerThread handlerThread = new HandlerThread(name, priority);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    public void init(Context context, boolean isCloudBased, boolean isCloudAnnotation, boolean isNativeMatching, Set<Integer> contentIDs){
        this.context = context;
        this.isCloudAnnotation = isCloudAnnotation;
        this.isNativeMatching = isNativeMatching;
        this.isCloudBased = isCloudBased;
        this.contentIDs = contentIDs;

        System.loadLibrary("opencv_java");
        System.loadLibrary("nonfree");
        System.loadLibrary("vlfeat");

        this.handlerUtil = createAndStartThread("util thread", Process.THREAD_PRIORITY_DEFAULT); //start util thread
        this.handlerFrame = createAndStartThread("frame thread" , -1); //start frame processing thread
        this.handlerNetwork = createAndStartThread("network thread", 1);
    }

    public void start() {
        if(isCloudBased) {
            taskConnection = new ConnectionTask(isUDPBased);
            taskConnection.setCallback(new ConnectionTask.Callback() {
                @Override
                public void onConnectionBuilt(String ip, int port, Channel chan, SocketAddress serverAddress) {
                    channel = chan;

                    if(isUDPBased) {
                        taskSending = new UDPSendingTask((DatagramChannel)channel, serverAddress);
                        taskReceiving = new UDPReceivingTask((DatagramChannel)channel, contentIDs);
                    } else {
                        taskSending = new TCPSendingTask((SocketChannel)channel, serverAddress);
                        taskReceiving = new TCPReceivingTask((SocketChannel)channel, contentIDs);
                    }
                    taskReceiving.setCallback(new ReceivingTask.Callback() {
                        @Override
                        public void onReceive(int resultID, MarkerGroup markerGroup) {
                            markerManager.updateMarkers(markerGroup, resultID);
                        }

                        @Override
                        public void onTimeout() {
                            callback.onCloudTimeout();
                        }
                    });
                    if(isCloudAnnotation) {
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
                    }
                }
            });
            handlerNetwork.post(taskConnection);
        } else {
            if (isNativeMatching) taskMatching = new MatchingTaskNative(context, contentIDs);
            else                  taskMatching = new MatchingTaskSIFT(context, contentIDs);
            taskMatching.setCallback(new MatchingTask.Callback() {
                @Override
                public void onFinish(MarkerGroup markerGroup, int frameID) {
                    markerManager.updateMarkers(markerGroup, frameID);
                }
            });
            handlerNetwork.post(taskMatching);  //initialization
        }

        markerManager = new MarkerImpl(handlerUtil);
        markerManager.setCallback(new MarkerImpl.Callback() {
            @Override
            public void onMarkersRecognized(MarkerGroup markerGroup) {
                callback.onMarkersReady(markerGroup);

                if(isCloudBased && isCloudAnnotation) {
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
        taskFrame = new TrackingTask();
        taskFrame.setCallback(markerManager);
    }

    public void stop() {
        if(isCloudBased) handlerNetwork.post(new Runnable() {
            @Override
            public void run() {
                try {
                    channel.close();
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

    public void recognize(byte[] frameData, float x, float y) {
        if(!isAnnotationReceived) return;

        taskFrame.setFrameData(++frameID, frameData, true);
        handlerFrame.post(taskFrame);

        int halfWidth = Constants.previewWidth / Constants.cropScale / 2;
        int offset = (int)x - halfWidth;
        if(offset < 0)
            offset = 0;
        else if(offset > (Constants.previewWidth - halfWidth * 2))
            offset = Constants.previewWidth - halfWidth * 2;

        if(isCloudBased) {
            taskSending.setData(frameID, frameData, offset);
            handlerNetwork.post(taskSending);
            taskReceiving.updateLatestSentID(frameID, offset);
        } else {
            taskMatching.setData(frameID, frameData, offset);
            handlerNetwork.post(taskMatching);
        }
    }

    public void driveFrame(byte[] frameData) {
        if (taskFrame.isBusy()) return;
        taskFrame.setFrameData(++frameID, frameData, false);
        handlerFrame.post(taskFrame);

        if(isCloudBased) handlerNetwork.post(taskReceiving);
    }

    ARManager.Callback callback;

    public void setCallback(ARManager.Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onMarkersReady(MarkerGroup markerGroup);
        void onMarkersChanged(MarkerGroup markerGroup);
        void onCloudTimeout();
        void onAnnotationReceived(int markerID, String annotationFile);
        void onAnnotationStatusReceived(float[] status);
    }
}
