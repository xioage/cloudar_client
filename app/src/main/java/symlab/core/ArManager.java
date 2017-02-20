package symlab.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import symlab.cloudridar.Markers;
import symlab.core.task.FrameTrackingTask;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ArManager {

    static private ArManager instance;
    private Context context;

    private final int FRAME_THREAD_SIZE = 2;

    private HandlerThread mRenderThread;
    private HandlerThread[] mFrameThread;
    private HandlerThread mUtilThread;

    private Handler handlerRender;
    private Handler handlerUtil;
    private Handler[] handlerFrame;
    private FrameTrackingTask[] taskFrame;

    private int frameID;

    private ArManager(){ super(); }

    static public ArManager getInstance() {
        synchronized (ArManager.class){
            if (instance == null) instance = new ArManager();
        }
        return instance;
    }

    public void init(Context context){
        this.context = context;

        //start render thread
        this.mRenderThread = new HandlerThread("render thread");
        this.mRenderThread.start();
        this.handlerRender = new Handler(mRenderThread.getLooper());

        //start util thread
        this.mUtilThread = new HandlerThread("util thread");
        this.mUtilThread.start();
        this.handlerUtil = new Handler(mUtilThread.getLooper());

        //start frame processing thread
        this.mFrameThread = new HandlerThread[FRAME_THREAD_SIZE];
        this.taskFrame = new FrameTrackingTask[FRAME_THREAD_SIZE];
        for (int i = 0; i< FRAME_THREAD_SIZE; i++){
            mFrameThread[i] = new HandlerThread( String.format("frame thread %d", i));
            mFrameThread[i].start();
            handlerFrame[i] = new Handler(mFrameThread[i].getLooper());
            taskFrame[i] = new FrameTrackingTask();
        }
    }

    public void initializeFrame(){

    }

    public void processFrame(byte[] frameData){
        int taskId = frameID % FRAME_THREAD_SIZE;
        FrameTrackingTask task = taskFrame[taskId];
        if (task.isBusy()) return;
        frameID++;
        task.setFrameData(frameID, frameData);
        handlerFrame[taskId].post(task);
    }

    public void start(){

    }

    public void setMarker(Markers marker){

    }



}
