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

import symlab.core.ArManager;
import symlab.core.Constants;
import symlab.core.SharedMemory;
import symlab.core.adapter.RenderAdapter;


public class MainActivity extends Activity implements View.OnTouchListener {

    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private DrawOnTop mDraw;
    private byte[] callbackBuffer;
    private int time_o, time_n, fps;

    private SocketAddress serverAddress;
    private DatagramChannel dataChannel;
    private int portNum;
    private String ip;
    private float dispScale;

    protected ISurface mRenderSurface;
    protected PosterRenderer mRenderer;

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
        if (SharedMemory.Show2DView) {
            mDraw = new DrawOnTop(this);
            addContentView(mDraw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (SharedMemory.ShowGL) {
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

        ArManager.getInstance().init(dataChannel, serverAddress, new RenderAdapter() {
            @Override
            public void onMarkerChanged(Markers markers) {
                mRenderer.onPosterChanged(markers);
            }

            @Override
            public void onRender(double[] glViewMatrix) {
                mRenderer.setGlViewMatrix(glViewMatrix);
            }
        });
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
        if (SharedMemory.ShowGL)
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

        private int count = 0;

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mCamera.addCallbackBuffer(callbackBuffer);

            ArManager.getInstance().driveFrame(data);
            count++;
            if (count % 30 == 1) mDraw.invalidate();
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
                    if (ShowName)
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
}

