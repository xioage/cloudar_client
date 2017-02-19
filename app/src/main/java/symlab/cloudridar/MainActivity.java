package symlab.cloudridar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.TermCriteria;
import org.opencv.features2d.FeatureDetector;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.rajawali3d.renderer.ISurfaceRenderer;
import org.rajawali3d.view.ISurface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import symlab.core.Constants;
import symlab.core.FrameTask;
import symlab.core.SharedMemory;


public class MainActivity extends Activity implements View.OnTouchListener{

    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private DrawOnTop mDraw;
    private byte[] callbackBuffer;
    private Queue<Integer> senderTskQueue = new LinkedList<Integer>();
    private int time_o, time_n, fps;

    private SocketAddress serverAddress;
    private DatagramChannel dataChannel;
    private int portNum;
    private String ip;
    ByteBuffer resPacket = ByteBuffer.allocate(400);


    private MatOfInt params;
    private FeatureDetector detector;
    private TermCriteria termcrit;

    private final int MESSAGE_ECHO = 0;
    private final int IMAGE_TRACK = 1;
    private final int IMAGE_DETECT = 2;
    private final int TRACKPOINTS = 3;
    private final int FEATURES = 4;
    private float dispScale;

    protected ISurface mRenderSurface;
    protected ISurfaceRenderer mRenderer;

    static {
        System.loadLibrary("opencv_java");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(Constants.TAG, " onCreate() called.");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mPreview = (SurfaceView) findViewById(R.id.preview);
        mPreview.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        mPreviewHolder = mPreview.getHolder();
        mPreviewHolder.addCallback(surfaceCallback);
        mPreview.setZOrderMediaOverlay(false);

        Display disp = getWindowManager().getDefaultDisplay();
        if (disp.getHeight() == 1080)
            dispScale = Constants.scale;
        else if (disp.getHeight() == 1440)
            dispScale = (float) 1.33 * Constants.scale;

        /*
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"cloudConfig.txt");

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            ip = br.readLine();
            portNum = Integer.parseInt(br.readLine());
            br.close();
        }
        catch (IOException e) {
            Log.d(Constants.TAG, "config file error");
        }
        */

        ip = "104.199.140.59";
        portNum = 51717;

        SharedMemory.initMemory();
        if(SharedMemory.Show2DView) {
            mDraw = new DrawOnTop(this);
            addContentView(mDraw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if(SharedMemory.ShowGL) {
            mRenderSurface = (ISurface) findViewById(R.id.rajwali_surface);
            mRenderer = new PosterRenderer(this, Constants.scale);
            mRenderSurface.setSurfaceRenderer(mRenderer);
            ((View) mRenderSurface).setOnTouchListener(this);
        }

        try {
            serverAddress = new InetSocketAddress(ip, portNum);
            dataChannel = DatagramChannel.open();
            dataChannel.configureBlocking(false);
            dataChannel.socket().connect(serverAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 1; i <= 2; i++) senderTskQueue.add(i);

        params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, 50);
        detector = FeatureDetector.create(FeatureDetector.SURF);
    }

    @Override
    public void onStart() {
        Log.i(Constants.TAG, " onStart() called.");
        super.onStart();

        new startTransmissionTask().execute();
    }

    @Override
    public void onResume() {
        Log.i(Constants.TAG, " onResume() called.");
        super.onResume();
        mCamera = Camera.open();
        SharedMemory.glViewMatrixData = new double[16];
        SharedMemory.Markers0 = null;
        SharedMemory.Markers1 = null;
    }

    @Override
    public void onPause() {
        Log.i(Constants.TAG, " onPause() called.");
        if(SharedMemory.ShowGL)
            ((PosterRenderer) mRenderer).onActivityPause();

        if (mInPreview)
            mCamera.stopPreview();

        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.release();
        mCamera = null;
        mInPreview = false;
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(Constants.TAG, " onStop() called.");
        super.onStop();
        new endTransmissionTask().execute();
    }

    @Override
    protected void onDestroy() {
        try {
            dataChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        senderTskQueue.clear();
        Log.i(Constants.TAG, " onDestroy() called.");
        super.onDestroy();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(Constants.TAG, "touch event");
        if (SharedMemory.ShowGL && event.getAction() == MotionEvent.ACTION_DOWN) {
            ((PosterRenderer) mRenderer).getObjectAt(event.getX(), event.getY());
        }

        return this.onTouchEvent(event);
    }

    private void initPreview(int width, int height) {
        Log.i(Constants.TAG, "initPreview() called");
        if (mCamera != null && mPreviewHolder.getSurface() != null) {
            if (!mCameraConfigured) {
                Camera.Parameters params = mCamera.getParameters();
                params.setPreviewSize(width, height);

                callbackBuffer = new byte[(height + height / 2) * width];
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                params.set("camera-id", 2);
                mCamera.setParameters(params);
                mCameraConfigured = true;
            }

            try {
                mCamera.setPreviewDisplay(mPreviewHolder);
                mCamera.addCallbackBuffer(callbackBuffer);
                mCamera.setPreviewCallbackWithBuffer(frameIMGProcCallback);
            } catch (Throwable t) {
                Log.e(Constants.TAG, "Exception in initPreview()", t);
            }
        }
    }

    private void startPreview() {
        if (mCameraConfigured && mCamera != null) {
            mCamera.startPreview();
            mCamera.autoFocus(null);
            mInPreview = true;
        }
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(Constants.TAG, " surfaceCreated() called.");
            initPreview(Constants.previewWidth, Constants.previewHeight);
            startPreview();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(Constants.TAG, " surfaceChanged() called.");
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(Constants.TAG, " surfaceDestroyed() called.");
        }
    };

    Camera.PreviewCallback frameIMGProcCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mCamera.addCallbackBuffer(callbackBuffer);

            if (senderTskQueue.peek() != null) {
                //Long tsLong = System.currentTimeMillis();
                //String ts_getCameraFrame = tsLong.toString();
                //Log.d(Eval, "get camera frame " + frmID + ": " + ts_getCameraFrame);
                senderTskQueue.poll();
                FrameTask frameTask = new FrameTask(SharedMemory.frmID, (PosterRenderer) mRenderer);
                frameTask.setCallbackListener(new FrameTask.CallbackListener() {
                    @Override
                    public void onFinish() {
                        if(SharedMemory.Show2DView && SharedMemory.frmID%30 == 1) mDraw.invalidate();
                        senderTskQueue.add(SharedMemory.frmID%2);
                    }
                });
                frameTask.execute(data);
                if(SharedMemory.frmID <= 5)
                    new frmTransmissionTask(SharedMemory.frmID).execute();

                if (SharedMemory.frmID % Constants.FREQUENCY == 10) {
                    SharedMemory.lastSentID = SharedMemory.frmID;
                    Long tsLong = System.currentTimeMillis();
                    String ts_getCameraFrame = tsLong.toString();
                    Log.d(Constants.Eval, "get frame " + SharedMemory.frmID + " data: " + ts_getCameraFrame);
                    new frmTransmissionTask(SharedMemory.lastSentID).execute(data);
                }
                new resReceivingTask().execute();

                SharedMemory.frmID++;
            }
        }
    };

    private class startTransmissionTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... frmdata) {
            try {
                Log.d(Constants.TAG, "sending start signals");
                for (int i = 0; i < 3; i++) {
                    ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0);
                    buffer.flip();
                    dataChannel.send(buffer, serverAddress);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
        }
    }

    private class endTransmissionTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... frmdata) {
            try {
                Log.d(Constants.TAG, "sending end signals");
                for (int i = 0; i < 1; i++) {
                    ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1);
                    buffer.flip();
                    dataChannel.send(buffer, serverAddress);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
        }
    }

    private class frmTransmissionTask extends AsyncTask<byte[], Void, Void> {
        private int dataType;
        private int frmID;
        private byte[] frmdataToSend;
        private byte[] frmid;
        private byte[] datatype;
        private byte[] frmsize;
        private byte[] packetContent;
        private int datasize;

        public frmTransmissionTask(int frmID) {
            this.frmID = frmID;
            if(this.frmID <= 5)
                dataType = MESSAGE_ECHO;
            else
                dataType = IMAGE_DETECT;
        }

        @Override
        protected Void doInBackground(byte[]... frmdata) {
            if(dataType == IMAGE_DETECT) {
                SharedMemory.YUVMatTrans.put(0, 0, frmdata[0]);
                Imgproc.cvtColor(SharedMemory.YUVMatTrans, SharedMemory.BGRMat, Imgproc.COLOR_YUV420sp2BGR);
                Imgproc.resize(SharedMemory.BGRMat, SharedMemory.BGRMatScaled, SharedMemory.BGRMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            }

            if (dataType == IMAGE_DETECT) {
                MatOfByte imgbuff = new MatOfByte();
                Highgui.imencode(".jpg", SharedMemory.BGRMatScaled, imgbuff, params);

                datasize = (int) (imgbuff.total() * imgbuff.channels());
                frmdataToSend = new byte[datasize];

                imgbuff.get(0, 0, frmdataToSend);
            } else if (dataType == TRACKPOINTS) {
                Point[] points = SharedMemory.Points1.toArray();
                datasize = 8 * points.length + Constants.MAX_POINTS;
                frmdataToSend = new byte[datasize];
                byte[] tmp;
                for (int i = 0; i < points.length; i++) {
                    tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) points[i].x).array();
                    System.arraycopy(tmp, 0, frmdataToSend, i * 8, 4);
                    tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) points[i].y).array();
                    System.arraycopy(tmp, 0, frmdataToSend, i * 8 + 4, 4);
                }
                System.arraycopy(SharedMemory.bitmap1, 0, frmdataToSend, datasize - Constants.MAX_POINTS, Constants.MAX_POINTS);
            }  else if (dataType == MESSAGE_ECHO) {
                datasize = 0;
                frmdataToSend = null;
            }
            packetContent = new byte[12 + datasize];

            frmid = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(frmID).array();
            datatype = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataType).array();
            frmsize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(datasize).array();
            System.arraycopy(frmid, 0, packetContent, 0, 4);
            System.arraycopy(datatype, 0, packetContent, 4, 4);
            System.arraycopy(frmsize, 0, packetContent, 8, 4);
            if(frmdataToSend != null)
                System.arraycopy(frmdataToSend, 0, packetContent, 12, datasize);

            try {
                ByteBuffer buffer = ByteBuffer.allocate(packetContent.length).put(packetContent);
                buffer.flip();
                dataChannel.send(buffer, serverAddress);
                //Log.d(Eval, "sent size: " + packetContent.length);

                Long tsLong = System.currentTimeMillis();
                String ts_sendCameraFrame = tsLong.toString();
                if(frmID <= 5)
                    Log.d(Constants.Eval, "echo " + frmID + " sent: " + ts_sendCameraFrame);
                else
                    Log.d(Constants.Eval, "frame " + frmID + " sent: " + ts_sendCameraFrame);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    private class resReceivingTask extends AsyncTask<Void, Void, Void> {
        private byte[] res;
        private float[] floatres = new float[8];
        private Point[] pointArray = new Point[4];
        private byte[] tmp = new byte[4];
        private byte[] name = new byte[64];
        private int newMarkerNum;

        protected Void doInBackground(Void... arg) {
            resPacket.clear();
            try {
                if (dataChannel.receive(resPacket) != null) {
                    res = resPacket.array();
                    //Log.d(Eval, "received size: " + res.length);
                }
                else
                    Log.v(Constants.TAG, "nothing received");
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (res != null) {
                System.arraycopy(res, 0, tmp, 0, 4);
                SharedMemory.resID = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                System.arraycopy(res, 4, tmp, 0, 4);
                newMarkerNum = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                Long tsLong = System.currentTimeMillis();
                String ts_getResult = tsLong.toString();

                if (SharedMemory.resID <= 5) {
                    Log.d(Constants.Eval, "echo " + SharedMemory.resID + " received: " + ts_getResult);
                } else if (SharedMemory.resID == SharedMemory.lastSentID) {
                    Log.d(Constants.Eval, "res " + SharedMemory.resID + " received: " + ts_getResult);
                    SharedMemory.Markers0 = new Markers(newMarkerNum);

                    for (int i = 0; i < newMarkerNum; i++) {
                        SharedMemory.Markers0.Recs[i] = new MatOfPoint2f();
                        SharedMemory.Markers0.Recs[i].alloc(4);
                        System.arraycopy(res, 8 + i * 100, tmp, 0, 4);
                        SharedMemory.Markers0.IDs[i] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                        for (int j = 0; j < 8; j++) {
                            System.arraycopy(res, 12 + i * 100 + j * 4, tmp, 0, 4);
                            floatres[j] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                        }
                        for (int j = 0; j < 4; j++)
                            pointArray[j] = new Point(floatres[j * 2], floatres[j * 2 + 1]);
                        SharedMemory.Markers0.Recs[i].fromArray(pointArray);

                        System.arraycopy(res, 44 + i * 100, name, 0, 64);
                        String markerName = new String(name);
                        SharedMemory.Markers0.Names[i] = markerName.substring(0, markerName.indexOf("."));
                    }

                    if (SharedMemory.Markers1 != null)
                        SharedMemory.PosterChanged = !Arrays.equals(SharedMemory.Markers1.IDs, SharedMemory.Markers0.IDs);
                    else
                        SharedMemory.PosterChanged = true;

                    if (!SharedMemory.PosterChanged && SharedMemory.Markers1 != null)
                        SharedMemory.Markers0.TrackingPointsNums = SharedMemory.Markers1.TrackingPointsNums;

                    SharedMemory.InitializeNeeded = SharedMemory.PosterChanged;
                    SharedMemory.PosterRecognized = true;

                    if(SharedMemory.ShowGL && SharedMemory.PosterChanged)
                        ((PosterRenderer) mRenderer).onPosterChanged(SharedMemory.Markers0);
                } else {
                    Log.d(Constants.TAG, "discard outdate result: " + SharedMemory.resID);
                }
            }
            return null;
        }
    }

    class DrawOnTop extends View {
        Paint paintPoint;
        Paint paintLine;
        Paint paintWord;
        private boolean ShowFPS = true;
        private boolean ShowEdge = false;
        private boolean ShowName = false;
        private boolean ShowPoints = false;

        public DrawOnTop(Context context) {
            super(context);
            paintPoint = new Paint();
            paintPoint.setStyle(Paint.Style.STROKE);
            paintPoint.setStrokeWidth(2);
            paintPoint.setColor(Color.YELLOW);

            paintLine = new Paint();
            paintLine.setStyle(Paint.Style.STROKE);
            paintLine.setStrokeWidth(10);
            paintLine.setColor(Color.GREEN);

            paintWord = new Paint();
            paintWord.setStyle(Paint.Style.STROKE);
            paintWord.setStrokeWidth(5);
            paintWord.setColor(Color.RED);
            paintWord.setTextAlign(Paint.Align.CENTER);
            paintWord.setTextSize(50);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (ShowFPS) {
                if (true) {
                    time_n = (int) System.currentTimeMillis();
                    fps = 1000 * (SharedMemory.frmID - SharedMemory.oldFrmID) / (time_n - time_o);
                    time_o = time_n;
                    SharedMemory.oldFrmID = SharedMemory.frmID;
                }
                canvas.drawText("fps: " + fps, 100, 50, paintWord);
            }

            if (ShowEdge && SharedMemory.Markers1 != null && SharedMemory.Markers1.Num != 0) {
                float[][] points = new float[4][2];
                for (int i = 0; i < SharedMemory.Markers1.Num; i++) {
                    for (int j = 0; j < 4; j++)
                        SharedMemory.Markers1.Recs[i].get(j, 0, points[j]);
                    for (int j = 0; j < 4; j++)
                        canvas.drawLine(dispScale * points[j][0], dispScale * points[j][1], dispScale * points[(j + 1) % 4][0], dispScale * points[(j + 1) % 4][1], paintLine);
                    if(ShowName)
                        canvas.drawText(SharedMemory.Markers1.Names[i], dispScale * points[0][0] + 400, dispScale * points[0][1] + 200, paintWord);
                }
            }

            if (ShowPoints && SharedMemory.Points1 != null) {
                Point[] points = SharedMemory.Points1.toArray();
                for (int i = 0; i < points.length; i++) {
                    canvas.drawCircle(dispScale * (float) points[i].x, dispScale * (float) points[i].y, 5, paintPoint);
                }
            }
        }
    }

    /*
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private class resReceivingTask extends AsyncTask<Void, Void, Void> {
        private int ressize;
        private byte[] res = new byte[400];
        private float[] floatres = new float[100];
        private byte[] tmp = new byte[4];
        DatagramPacket resPacket = new DatagramPacket(res, res.length);

        public resReceivingTask () {
        }

        protected Void doInBackground(Void... arg) {
            while(true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    receiverudpsocket.receive(resPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                    //Log.i(TAG, "Asynctask - " + tskId + " failed.");
                }

                System.arraycopy(res, 0, tmp, 0, 4);
                ressize = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                Log.d(TAG, "res size: " + ressize);

                if (ressize != 0) {
                    for (int i = 0; i < ressize / 4; i++) {
                        System.arraycopy(res, i * 4 + 4, tmp, 0, 4);
                        floatres[i] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat() * scale;
                    }

                    resPoints = new float[ressize / 2];
                    int j = 0;

                    for (int i = 0; i < ressize / 32; i += 8) {
                        resPoints[j++] = size.height - floatres[i + 1];
                        resPoints[j++] = floatres[i];
                        resPoints[j++] = size.height - floatres[i + 3];
                        resPoints[j++] = floatres[i + 2];

                        resPoints[j++] = size.height - floatres[i + 3];
                        resPoints[j++] = floatres[i + 2];
                        resPoints[j++] = size.height - floatres[i + 5];
                        resPoints[j++] = floatres[i + 4];

                        resPoints[j++] = size.height - floatres[i + 5];
                        resPoints[j++] = floatres[i + 4];
                        resPoints[j++] = size.height - floatres[i + 7];
                        resPoints[j++] = floatres[i + 6];

                        resPoints[j++] = size.height - floatres[i + 7];
                        resPoints[j++] = floatres[i + 6];
                        resPoints[j++] = size.height - floatres[i + 1];
                        resPoints[j++] = floatres[i];
                    }
                    publishProgress();
                }
            }
        }

        @Override
        public void onProgressUpdate(Void... progress) {
            mDraw.invalidate();
        }
    }

    private class socketCreationTask extends AsyncTask<Void, Void, Void> {
        String desAddress;
        int dstPort;

        socketCreationTask(String addr, int port) {
            this.desAddress = addr;
            this.dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg) {
            try {
                socket = new Socket(desAddress, dstPort);
                Log.i(TAG, "Socket established");
                oStream = socket.getOutputStream();
                iStream = socket.getInputStream();
                for (int i = 1; i <= 2; i++) senderTskQueue.add(i);
                for (int i = 1; i <= 2; i++) receiverTskQueue.add(i);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class frmTransmissionTask extends AsyncTask<byte[], Void, Void> {
        private int tskId;
        private boolean NFT_flag = false;
        private byte[] frmdataToSend;
        private byte[] frmsize;
        private byte[] packetContent;
        private int datasize;

        private int datasize_float;
        private float[] frmdata_float;
        Long ts_n, ts_o, timediff;

        public frmTransmissionTask(int tskId) {
            this.tskId = tskId;
            //Log.i(TAG, "Asynctask - " + tskId + " started.");
        }

        @Override
        protected Void doInBackground(byte[]... frmdata) {
            if (oStream != null) {   // oStream maybe set to null by previous failed asynctask
                try {
                    // 1. do yuv2rgb in android
                    YUVMat.put(0, 0, frmdata[0]);
                    Imgproc.cvtColor(YUVMat, BGRMat, Imgproc.COLOR_YUV420sp2BGR);
                    Log.v(TAG, "BGRMatScaled: " + BGRMatScaled.size());
                    Imgproc.resize(BGRMat, BGRMatScaled, BGRMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);

                    if(NFT_flag){
                        Mat GRAYMat = new Mat(size.height / scale, size.width / scale, CvType.CV_8UC1);
                        Imgproc.cvtColor(BGRMatScaled, GRAYMat, Imgproc.COLOR_BGR2GRAY);
                        Imgproc.equalizeHist(GRAYMat, GRAYMat);

                        MatOfKeyPoint keypoints_scene = new MatOfKeyPoint();

                        ts_o = System.currentTimeMillis();

                        detector.detect(GRAYMat, keypoints_scene);

                        ts_n = System.currentTimeMillis();

                        timediff = ts_n - ts_o;
                        Log.d(TAG, "timediff for detect: " + timediff);

                        datasize_float = (int)(keypoints_scene.total() * keypoints_scene.channels());
                        frmdata_float = new float[datasize_float];

                        keypoints_scene.get(0,0, frmdata_float);

                        datasize = datasize_float * 4;
                        frmdataToSend = new byte[datasize];

                        ts_o = System.currentTimeMillis();
                        byte[] tmp;
                        for(int i = 0; i < datasize_float; i++) {
                            tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(frmdata_float[i]).array();
                            frmdataToSend[i*4] = tmp[0];
                            frmdataToSend[i*4+1] = tmp[1];
                            frmdataToSend[i*4+2] = tmp[2];
                            frmdataToSend[i*4+3] = tmp[3];
                        }
                        ts_n = System.currentTimeMillis();
                        timediff = ts_n - ts_o;
                        Log.d(TAG, "timediff for array: " + timediff);
                    }
                    else{
                        //ts_o = System.currentTimeMillis();
                        MatOfByte imgbuff = new MatOfByte();
                        Highgui.imencode(".jpg", BGRMatScaled, imgbuff, params);

                        //ts_n = System.currentTimeMillis();
                        //timediff = ts_n - ts_o;
                        //Log.d(TAG, "timediff for jpg: " + timediff);

                        datasize = (int)(imgbuff.total() * imgbuff.channels());
                        frmdataToSend = new byte[datasize];

                        imgbuff.get(0, 0, frmdataToSend);
                    }

                    Log.d(TAG, "datasize: " + datasize);
                    frmsize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(datasize).array();

                    oStream.write(frmsize);
                    oStream.write(frmdataToSend);

                } catch (IOException e) {
                    e.printStackTrace();
                    if (socket != null) {
                        Log.i(TAG, "Connection lost.");
                        try {
                            oStream.close();
                            socket.close();
                            oStream = null;
                            socket = null;
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            //Log.i(TAG, "Asynctask - " + tskId + " ended.");
            senderTskQueue.add(this.tskId);
        }
    }

    private class resReceivingTask extends AsyncTask<Void, Void, Void> {
        private int tskID;
        private int ressize, respoint;
        private byte[] res = new byte[400];
        private float[] floatres = new float[50];

        public resReceivingTask (int tskID) {
            this.tskID = tskID;
        }
        protected Void doInBackground(Void... arg) {
            if (iStream != null) {
                try {
                    byte[] tmp = new byte[4];
                    iStream.read(tmp, 0, 4);
                    ressize = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    Log.d(TAG, "res size: " + ressize);

                    if (ressize != 0) {
                        int len = iStream.read(res, 0, ressize);
                        if (len < ressize)
                            Log.d(TAG, "error receive res");

                        respoint = 0;

                        for (int i = 0; i < ressize / 4; i++) {
                            for (int j = 0; j < 4; j++)
                                tmp[j] = res[respoint++];
                            floatres[i] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat() * scale;
                        }

                        resPoints = new float[ressize/2];
                        int j = 0;

                        for (int i = 0; i < ressize/32; i+=8) {
                            resPoints[j++] = size.height - floatres[i+1];
                            resPoints[j++] = floatres[i];
                            resPoints[j++] = size.height - floatres[i+3];
                            resPoints[j++] = floatres[i+2];

                            resPoints[j++] = size.height - floatres[i+3];
                            resPoints[j++] = floatres[i+2];
                            resPoints[j++] = size.height - floatres[i+5];
                            resPoints[j++] = floatres[i+4];

                            resPoints[j++] = size.height - floatres[i+5];
                            resPoints[j++] = floatres[i+4];
                            resPoints[j++] = size.height - floatres[i+7];
                            resPoints[j++] = floatres[i+6];

                            resPoints[j++] = size.height - floatres[i+7];
                            resPoints[j++] = floatres[i+6];
                            resPoints[j++] = size.height - floatres[i+1];
                            resPoints[j++] = floatres[i];
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (socket != null) {
                        Log.i(TAG, "Connection lost.");
                        try {
                            iStream.close();
                            iStream = null;
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    //Log.i(TAG, "Asynctask - " + tskId + " failed.");
                }
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            mDraw.invalidate();
            receiverTskQueue.add(this.tskID);
        }
    }
    */

    /*
    public void SaveImage(Mat mat) {

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        Log.v(TAG, path.toString());
        Long tsLong = System.currentTimeMillis()/100;
        String ts = tsLong.toString();
        String filename = "frm"+ts+".jpg";
        File file = new File(path, filename);

        Boolean bool;
        filename = file.toString();
        bool = Highgui.imwrite(filename, mat);

        if (bool == true)
            Log.d(TAG, "SUCCESS writing image to external storage");
        else
            Log.d(TAG, "Fail writing image to external storage");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    */
}
