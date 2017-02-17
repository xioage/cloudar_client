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


public class MainActivity extends Activity implements View.OnTouchListener{

    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private DrawOnTop mDraw;
    private static final String TAG = "Poster";
    private static final String Eval = "Evaluation";
    private byte[] callbackBuffer;
    private Queue<Integer> senderTskQueue = new LinkedList<Integer>();
    private int time_o, time_n, fps;

    private SocketAddress serverAddress;
    private DatagramChannel dataChannel;
    private int portNum;
    private String ip;
    ByteBuffer resPacket = ByteBuffer.allocate(400);

    private Mat YUVMatTrack, YUVMatTrans, YUVMatScaled;
    private Mat BGRMat, BGRMatScaled;
    private Mat PreGRAYMat;

    private int frmID = 1;
    private int lastSentID = -1;
    private int resID;
    private int trackingID = 0;
    private int oldFrmID;
    private MatOfPoint2f Points1;
    private int[] bitmap1;
    private Queue<HistoryTrackingPoints> HistoryQueue = new LinkedList<HistoryTrackingPoints>();
    private Markers Markers0, Markers1;

    private MatOfInt params;
    private FeatureDetector detector;
    private TermCriteria termcrit;
    private org.opencv.core.Size subPixWinSize;
    private org.opencv.core.Size winSize;

    private boolean InitializeNeeded = true;
    private boolean PosterChanged = false;
    private boolean PosterRecognized = false;
    private boolean Show2DView = true;
    private boolean ShowGL = true;
    private boolean EnableMultipleTracking = false;

    private final int scale = 4;
    private final int MESSAGE_ECHO = 0;
    private final int IMAGE_TRACK = 1;
    private final int IMAGE_DETECT = 2;
    private final int TRACKPOINTS = 3;
    private final int FEATURES = 4;
    private final int MAX_POINTS = 240;
    private final int FREQUENCY = 30;
    private final int previewWidth = 1920;
    private final int previewHeight = 1080;
    private float dispScale;

    protected ISurface mRenderSurface;
    protected ISurfaceRenderer mRenderer;

    private Mat cameraMatrix = new Mat(3, 3, CvType.CV_64FC1);
    private MatOfDouble distCoeffs = new MatOfDouble();
    private MatOfPoint3f posterPoints = new MatOfPoint3f();
    private Mat cvToGl = new Mat(4, 4, CvType.CV_64FC1);
    private Mat viewMatrix = Mat.zeros(4, 4, CvType.CV_64FC1);
    private double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};
    private double[] distCoeffsData = new double[]{2.8048006231906419e-001, -1.1828928706191699e+000, 0, 0, 1.4865861018485209e+000};
    private Point3[][] posterPointsData = new Point3[][]{{new Point3(-5, 7.4, 0), new Point3(5, 7.4, 0), new Point3(5, -7.4, 0), new Point3(-5, -7.4, 0)},
            {new Point3(-5, 7.2, 0), new Point3(5, 7.2, 0), new Point3(5, -7.2, 0), new Point3(-5, -7.2, 0)},
            {new Point3(5, 5, 0), new Point3(15, 5, 0), new Point3(15, -5, 0), new Point3(5, -5, 0)},
            {new Point3(5, 8, 0), new Point3(15, 8, 0), new Point3(15, -8, 0), new Point3(5, -8, 0)},
            {new Point3(5, 6.6, 0), new Point3(15, 6.6, 0), new Point3(15, -6.6, 0), new Point3(5, -6.6, 0)}};
    private double[][] cvToGlData = new double[][]{{1.0, 0, 0, 0}, {0, -1.0, 0, 0}, {0, 0, -1.0, 0}, {0, 0, 0, 1.0}};
    private double[] glViewMatrixData;

    static {
        System.loadLibrary("opencv_java");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, " onCreate() called.");
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
            dispScale = scale;
        else if (disp.getHeight() == 1440)
            dispScale = (float) 1.33 * scale;

        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"cloudConfig.txt");

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            ip = br.readLine();
            portNum = Integer.parseInt(br.readLine());
            br.close();
        }
        catch (IOException e) {
            Log.d(TAG, "config file error");
        }

        if(Show2DView) {
            mDraw = new DrawOnTop(this);
            addContentView(mDraw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if(ShowGL) {
            mRenderSurface = (ISurface) findViewById(R.id.rajwali_surface);
            mRenderer = new PosterRenderer(this, scale);
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

        YUVMatTrack = new Mat(previewHeight + previewHeight / 2, previewWidth, CvType.CV_8UC1);
        YUVMatTrans = new Mat(previewHeight + previewHeight / 2, previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((previewHeight + previewHeight / 2) / scale, previewWidth / scale, CvType.CV_8UC1);
        BGRMat = new Mat(previewHeight, previewWidth, CvType.CV_8UC3);
        BGRMatScaled = new Mat(previewHeight / scale, previewWidth / scale, CvType.CV_8UC3);
        params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, 50);
        detector = FeatureDetector.create(FeatureDetector.SURF);
        termcrit = new TermCriteria(TermCriteria.MAX_ITER | TermCriteria.EPS, 20, 0.03);
        subPixWinSize = new org.opencv.core.Size(10, 10);
        winSize = new org.opencv.core.Size(31, 31);
        for(int i = 0; i < 3; i++)
            for(int j = 0; j < 3; j++)
                cameraMatrix.put(i, j, cameraMatrixData[i][j]);
        distCoeffs.fromArray(distCoeffsData);
        for(int i = 0; i < 4; i++)
            for(int j = 0; j < 4; j++)
                cvToGl.put(i, j, cvToGlData[i][j]);
    }

    @Override
    public void onStart() {
        Log.i(TAG, " onStart() called.");
        super.onStart();

        new startTransmissionTask().execute();
    }

    @Override
    public void onResume() {
        Log.i(TAG, " onResume() called.");
        super.onResume();
        mCamera = Camera.open();
        glViewMatrixData = new double[16];
        Markers0 = null;
        Markers1 = null;
    }

    @Override
    public void onPause() {
        Log.i(TAG, " onPause() called.");
        if(ShowGL)
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
        Log.i(TAG, " onStop() called.");
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
        Log.i(TAG, " onDestroy() called.");
        super.onDestroy();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(TAG, "touch event");
        if (ShowGL && event.getAction() == MotionEvent.ACTION_DOWN) {
            ((PosterRenderer) mRenderer).getObjectAt(event.getX(), event.getY());
        }

        return this.onTouchEvent(event);
    }

    private void initPreview(int width, int height) {
        Log.i(TAG, "initPreview() called");
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
                Log.e(TAG, "Exception in initPreview()", t);
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
            Log.i(TAG, " surfaceCreated() called.");
            initPreview(previewWidth, previewHeight);
            startPreview();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, " surfaceChanged() called.");
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, " surfaceDestroyed() called.");
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
                new frmTrackingTask(frmID).execute(data);

                if(frmID <= 5)
                    new frmTransmissionTask(frmID).execute();

                if (frmID % FREQUENCY == 10) {
                    lastSentID = frmID;
                    Long tsLong = System.currentTimeMillis();
                    String ts_getCameraFrame = tsLong.toString();
                    Log.d(Eval, "get frame " + frmID + " data: " + ts_getCameraFrame);
                    new frmTransmissionTask(lastSentID).execute(data);
                }
                new resReceivingTask().execute();

                frmID++;
            }
        }
    };

    private class startTransmissionTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... frmdata) {
            try {
                Log.d(TAG, "sending start signals");
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
                Log.d(TAG, "sending end signals");
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

    //Core class for processing, with tracking and all kinds of calculation.
    //Before you fully understand the logic, don't change it.
    private class frmTrackingTask extends AsyncTask<byte[], Void, Void> {
        private int frmID;
        private HistoryTrackingPoints curHistory;
        private MatOfPoint2f Points2;
        private MatOfPoint2f[] Recs2;
        private int[] bitmap2;
        private MatOfPoint2f[] subHistoryPoints, subPoints1, subPoints2;
        private MatOfPoint2f scenePoints;
        private Mat rvec, tvec;
        private Mat rotation;

        private boolean viewReady = false;
        private boolean recoverFrame = false;
        private Long tsLong;

        public frmTrackingTask(int frmID) {
            this.frmID = frmID;
        }

        @Override
        protected Void doInBackground(byte[]... frmdata) {
            //Long tsLong = System.currentTimeMillis();
            //String ts_getCameraFrame = tsLong.toString();
            //Log.d(Eval, "get frame " + frmID + " data: " + ts_getCameraFrame);

            YUVMatTrack.put(0, 0, frmdata[0]);
            Imgproc.resize(YUVMatTrack, YUVMatScaled, YUVMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            Mat GRAYMat = new Mat(previewHeight / scale, previewWidth / scale, CvType.CV_8UC1);
            Imgproc.cvtColor(YUVMatScaled, GRAYMat, Imgproc.COLOR_YUV2GRAY_420);

            //tsLong = System.currentTimeMillis();
            //String ts_resize = tsLong.toString();
            //Log.d(Eval, "resize camera frame " + frmID + ": " + ts_resize);

            if (PosterRecognized) {
                Markers1 = Markers0;
                PosterRecognized = false;
                recoverFrame = true;
            }

            if (Points1 != null) {
                MatOfByte status = new MatOfByte();
                MatOfFloat err = new MatOfFloat();
                Points2 = new MatOfPoint2f();
                Video.calcOpticalFlowPyrLK(PreGRAYMat, GRAYMat, Points1, Points2, status, err, winSize, 3, termcrit, 0, 0.001);
                PreGRAYMat = GRAYMat;
                //tsLong = System.currentTimeMillis();
                //String ts_getPoints = tsLong.toString();
                //Log.d(Eval, "get points " + frmID + " :" + ts_getPoints);

                bitmap2 = new int[MAX_POINTS];
                int i, k, j;
                for (i = k = j = 0; i < Points2.rows(); i++) {
                    while (j < MAX_POINTS && bitmap1[j] == 0)
                        j++;

                    if (j == MAX_POINTS)
                        break;

                    if (status.toArray()[i] == 0) {
                        j++;
                        continue;
                    }

                    if (k != i) {
                        Points1.put(k, 0, Points1.get(i, 0));
                        Points2.put(k, 0, Points2.get(i, 0));
                    }
                    k++;
                    if (EnableMultipleTracking)
                        bitmap2[j] = bitmap1[j];
                    else
                        bitmap2[j] = 1;
                    j++;
                }
                if (k != Points1.rows())
                    Points1 = new MatOfPoint2f(Points1.rowRange(0, k));
                if (k != Points2.rows())
                    Points2 = new MatOfPoint2f(Points2.rowRange(0, k));
                Log.v(TAG, "tracking points left: " + k);

                if (k > 4) {
                    if (Markers1 != null && Markers1.Num != 0) {
                        if (recoverFrame) {
                            //Log.d(TAG, "frm " + frmID + " recover from " + resID);

                            do {
                                curHistory = HistoryQueue.poll();
                            } while (curHistory.HistoryFrameID != resID);

                            if (curHistory.HistoryTrackingID == trackingID) {

                                for (i = k = j = 0; i < curHistory.HistoryPoints.rows(); i++) {
                                    while (curHistory.historybitmap[j] == 0) j++;

                                    if (bitmap2[j] == 0) {
                                        j++;
                                        continue;
                                    }

                                    curHistory.HistoryPoints.put(k++, 0, curHistory.HistoryPoints.get(i, 0));
                                    j++;
                                }
                                curHistory.HistoryPoints = new MatOfPoint2f(curHistory.HistoryPoints.rowRange(0, k));

                                if (!EnableMultipleTracking || PosterChanged) {
                                    Mat H = Calib3d.findHomography(curHistory.HistoryPoints, Points2, Calib3d.RANSAC, 3);
                                    for (int m = 0; m < Markers1.Num; m++) {
                                        Markers1.Homographys[m] = H;
                                    }
                                } else {
                                    subHistoryPoints = new MatOfPoint2f[Markers1.Num];
                                    subPoints2 = new MatOfPoint2f[Markers1.Num];
                                    for (int m = 0; m < Markers1.Num; m++) {
                                        subHistoryPoints[m] = new MatOfPoint2f(new Mat(Markers1.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                                        subPoints2[m] = new MatOfPoint2f(new Mat(Markers1.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                                        int count = 0;
                                        for (int n = 0; n < Points2.rows(); n++) {
                                            if (bitmap2[n] == Markers1.IDs[m]) {
                                                subHistoryPoints[m].put(count, 0, curHistory.HistoryPoints.get(n, 0));
                                                subPoints2[m].put(count++, 0, Points2.get(n, 0));
                                            }
                                        }
                                        Markers1.Homographys[m] = Calib3d.findHomography(subHistoryPoints[m], subPoints2[m], Calib3d.RANSAC, 3);
                                    }
                                }
                            } else {
                                Log.e(TAG, "tried to recover from late result, tracking points not match");
                            }
                        } else {
                            if (EnableMultipleTracking) {
                                subPoints1 = new MatOfPoint2f[Markers1.Num];
                                subPoints2 = new MatOfPoint2f[Markers1.Num];

                                for (int m = 0; m < Markers1.Num; m++) {
                                    subPoints1[m] = new MatOfPoint2f(new Mat(Markers1.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                                    subPoints2[m] = new MatOfPoint2f(new Mat(Markers1.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                                    int count = 0;
                                    for (int n = 0; n < Points2.rows(); n++) {
                                        if (bitmap2[n] == Markers1.IDs[m]) {
                                            subPoints1[m].put(count, 0, Points1.get(n, 0));
                                            subPoints2[m].put(count++, 0, Points2.get(n, 0));
                                        }
                                    }
                                    Markers1.Homographys[m] = Calib3d.findHomography(subPoints1[m], subPoints2[m], Calib3d.RANSAC, 3);
                                }
                            } else {
                                Mat H = Calib3d.findHomography(Points1, Points2, Calib3d.RANSAC, 3);
                                for (int m = 0; m < Markers1.Num; m++) {
                                    Markers1.Homographys[m] = H;
                                }
                            }
                        }

                        Points1 = Points2;
                        bitmap1 = bitmap2;

                        Recs2 = new MatOfPoint2f[Markers1.Num];
                        for (int m = 0; m < Markers1.Num; m++) {
                            if(Markers1.Recs[m] != null && Markers1.Homographys[m] != null) {
                                Recs2[m] = new MatOfPoint2f();
                                Core.perspectiveTransform(Markers1.Recs[m], Recs2[m], Markers1.Homographys[m]);
                                viewReady = true;
                            } else {
                                Log.d(TAG, "null rec");
                                viewReady = false;
                            }
                        }

                        Markers1.Recs = Recs2;

                        if(ShowGL && viewReady) {
                            posterPoints.fromArray(posterPointsData[Markers1.IDs[0]]);
                            scenePoints = new MatOfPoint2f(Recs2[0].clone());
                            float x, y;
                            float[] xy = new float[2];
                            x = y = 0;
                            for(int ii = 0; ii < 4; ii++)
                            {
                                scenePoints.get(ii, 0, xy);
                                x += xy[0];
                                y += xy[1];
                            }
                            x /= 4;
                            y /= 4;
                            if(x < 0 || y < 0 || x > 1920/scale || y > 1080/scale) {
                                Log.d(TAG, "marker out of view, lost tracking");
                                ((PosterRenderer) mRenderer).onPosterChanged(null);
                                InitializeNeeded = true;
                            }
                            else {
                                rvec = new Mat();
                                tvec = new Mat();
                                Calib3d.solvePnP(posterPoints, scenePoints, cameraMatrix, distCoeffs, rvec, tvec);

                                rotation = new Mat();
                                Calib3d.Rodrigues(rvec, rotation);
                                for (int row = 0; row < 3; row++) {
                                    for (int col = 0; col < 3; col++) {
                                        viewMatrix.put(row, col, rotation.get(row, col));
                                    }
                                    viewMatrix.put(row, 3, tvec.get(row, 0));
                                }
                                viewMatrix.put(3, 3, 1.0);
                                Core.gemm(cvToGl, viewMatrix, 1, new Mat(), 0, viewMatrix, 0);

                                for (int col = 0; col < 4; col++)
                                    for (int row = 0; row < 4; row++)
                                        glViewMatrixData[col * 4 + row] = viewMatrix.get(row, col)[0];

                                ((PosterRenderer) mRenderer).setGlViewMatrix(glViewMatrixData);
                                //tsLong = System.currentTimeMillis();
                                //String ts_showResult = tsLong.toString();
                                //Log.d(Eval, "frm " + frmID + " showed: " + ts_showResult);
                            }

                            if(recoverFrame) {
                            }
                        }
                    } else {
                        Points1 = Points2;
                        bitmap1 = bitmap2;
                    }

                } else {
                    Log.v(TAG, "too few tracking points, track again");
                    InitializeNeeded = true;
                }
            } else {
                PreGRAYMat = GRAYMat;
            }

            if (InitializeNeeded) {
                Log.v(TAG, "get tracking points");
                MatOfPoint initial = new MatOfPoint();
                Imgproc.goodFeaturesToTrack(GRAYMat, initial, MAX_POINTS, 0.01, 10, new Mat(), 3, false, 0.04);
                Points1 = new MatOfPoint2f(initial.toArray());
                Imgproc.cornerSubPix(GRAYMat, Points1, subPixWinSize, new org.opencv.core.Size(-1, -1), termcrit);
                trackingID++;

                bitmap1 = new int[MAX_POINTS];
                if (!EnableMultipleTracking || !PosterChanged) {
                    for (int i = 0; i < Points1.rows(); i++)
                        bitmap1[i] = 1;
                } else {
                    for (int i = 0; i < Markers1.Num; i++) {
                        for (int j = 0; j < MAX_POINTS; j++) {
                            if (isInside(new Point(Points1.get(j, 0)), Markers1.Recs[i])) {
                                bitmap1[j] = Markers1.IDs[i];
                                Markers1.TrackingPointsNums[i]++;
                            }
                        }
                        Log.v(TAG, "Marker " + Markers1.IDs[i] + " points num: " + Markers1.TrackingPointsNums[i]);
                    }
                }

                InitializeNeeded = false;
            }

            if (frmID % FREQUENCY == 10)
                HistoryQueue.add(new HistoryTrackingPoints(frmID, trackingID, new MatOfPoint2f(Points1.clone()), bitmap1.clone()));

            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            if(Show2DView && frmID%30 == 1) mDraw.invalidate();
            senderTskQueue.add(frmID%2);
        }

        private boolean isInside(Point point, Mat Rec) {
            int i, j;
            boolean result = false;

            for (i = 0, j = 3; i < 4; j = i++) {
                if ((Rec.get(i, 0)[1] > point.y) != (Rec.get(j, 0)[1] > point.y) &&
                        (point.x < (Rec.get(j, 0)[0] - Rec.get(i, 0)[0]) * (point.y - Rec.get(i, 0)[1]) / (Rec.get(j, 0)[1] - Rec.get(i, 0)[1]) + Rec.get(i, 0)[0])) {
                    result = !result;
                }
            }
            return result;
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
                YUVMatTrans.put(0, 0, frmdata[0]);
                Imgproc.cvtColor(YUVMatTrans, BGRMat, Imgproc.COLOR_YUV420sp2BGR);
                Imgproc.resize(BGRMat, BGRMatScaled, BGRMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            }

            if (dataType == IMAGE_DETECT) {
                MatOfByte imgbuff = new MatOfByte();
                Highgui.imencode(".jpg", BGRMatScaled, imgbuff, params);

                datasize = (int) (imgbuff.total() * imgbuff.channels());
                frmdataToSend = new byte[datasize];

                imgbuff.get(0, 0, frmdataToSend);
            } else if (dataType == TRACKPOINTS) {
                Point[] points = Points1.toArray();
                datasize = 8 * points.length + MAX_POINTS;
                frmdataToSend = new byte[datasize];
                byte[] tmp;
                for (int i = 0; i < points.length; i++) {
                    tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) points[i].x).array();
                    System.arraycopy(tmp, 0, frmdataToSend, i * 8, 4);
                    tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) points[i].y).array();
                    System.arraycopy(tmp, 0, frmdataToSend, i * 8 + 4, 4);
                }
                System.arraycopy(bitmap1, 0, frmdataToSend, datasize - MAX_POINTS, MAX_POINTS);
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
                    Log.d(Eval, "echo " + frmID + " sent: " + ts_sendCameraFrame);
                else
                    Log.d(Eval, "frame " + frmID + " sent: " + ts_sendCameraFrame);
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
                    Log.v(TAG, "nothing received");
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (res != null) {
                System.arraycopy(res, 0, tmp, 0, 4);
                resID = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                System.arraycopy(res, 4, tmp, 0, 4);
                newMarkerNum = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                Long tsLong = System.currentTimeMillis();
                String ts_getResult = tsLong.toString();

                if (resID <= 5) {
                    Log.d(Eval, "echo " + resID + " received: " + ts_getResult);
                } else if (resID == lastSentID) {
                    Log.d(Eval, "res " + resID + " received: " + ts_getResult);
                    Markers0 = new Markers(newMarkerNum);

                    for (int i = 0; i < newMarkerNum; i++) {
                        Markers0.Recs[i] = new MatOfPoint2f();
                        Markers0.Recs[i].alloc(4);
                        System.arraycopy(res, 8 + i * 100, tmp, 0, 4);
                        Markers0.IDs[i] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                        for (int j = 0; j < 8; j++) {
                            System.arraycopy(res, 12 + i * 100 + j * 4, tmp, 0, 4);
                            floatres[j] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                        }
                        for (int j = 0; j < 4; j++)
                            pointArray[j] = new Point(floatres[j * 2], floatres[j * 2 + 1]);
                        Markers0.Recs[i].fromArray(pointArray);

                        System.arraycopy(res, 44 + i * 100, name, 0, 64);
                        String markerName = new String(name);
                        Markers0.Names[i] = markerName.substring(0, markerName.indexOf("."));
                    }

                    if (Markers1 != null)
                        PosterChanged = !Arrays.equals(Markers1.IDs, Markers0.IDs);
                    else
                        PosterChanged = true;

                    if (!PosterChanged && Markers1 != null)
                        Markers0.TrackingPointsNums = Markers1.TrackingPointsNums;

                    InitializeNeeded = PosterChanged;
                    PosterRecognized = true;

                    if(ShowGL && PosterChanged)
                        ((PosterRenderer) mRenderer).onPosterChanged(Markers0);
                } else {
                    Log.d(TAG, "discard outdate result: " + resID);
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
                    fps = 1000 * (frmID - oldFrmID) / (time_n - time_o);
                    time_o = time_n;
                    oldFrmID = frmID;
                }
                canvas.drawText("fps: " + fps, 100, 50, paintWord);
            }

            if (ShowEdge && Markers1 != null && Markers1.Num != 0) {
                float[][] points = new float[4][2];
                for (int i = 0; i < Markers1.Num; i++) {
                    for (int j = 0; j < 4; j++)
                        Markers1.Recs[i].get(j, 0, points[j]);
                    for (int j = 0; j < 4; j++)
                        canvas.drawLine(dispScale * points[j][0], dispScale * points[j][1], dispScale * points[(j + 1) % 4][0], dispScale * points[(j + 1) % 4][1], paintLine);
                    if(ShowName)
                        canvas.drawText(Markers1.Names[i], dispScale * points[0][0] + 400, dispScale * points[0][1] + 200, paintWord);
                }
            }

            if (ShowPoints && Points1 != null) {
                Point[] points = Points1.toArray();
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
