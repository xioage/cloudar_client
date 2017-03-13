package symlab.cloudridar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.rajawali3d.view.ISurface;

import java.util.ArrayList;

import symlab.core.ArManager;
import symlab.core.Constants;
import symlab.core.adapter.MarkerCallback;
import symlab.core.adapter.RenderAdapter;
import symlab.core.impl.MarkerImpl;


public class MainActivity extends Activity {

    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    protected ISurface mRenderSurface;
    protected MyRenderer mRenderer;

    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private DrawOnTop mDraw;
    private byte[] callbackBuffer;
    private int time_o, time_n, fps;
    private Markers markers;

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

        if (Constants.Show2DView) {
            mDraw = new DrawOnTop(this);
            addContentView(mDraw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (Constants.ShowGL) {
            mRenderSurface = (org.rajawali3d.view.SurfaceView) findViewById(R.id.rajwali_surface);
            mRenderer = new MyRenderer(this, Constants.scale);
            mRenderSurface.setSurfaceRenderer(mRenderer);
        }

        ArManager.getInstance().init(new RenderAdapter() {
            @Override
            public void onRender(ArrayList<MarkerImpl.Marker> markers) {
                mRenderer.updateMarker(markers);
            }
        });
    }

    @Override
    public void onStart() {
        Log.i(Constants.TAG, " onStart() called.");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.i(Constants.TAG, " onResume() called.");
        super.onResume();

        mCamera = Camera.open();
        ArManager.getInstance().start();
    }

    @Override
    public void onPause() {
        Log.i(Constants.TAG, " onPause() called.");
        super.onPause();


        if (mInPreview)
            mCamera.stopPreview();

        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.release();
        mCamera = null;
        mInPreview = false;

        ArManager.getInstance().stop();
    }

    @Override
    public void onStop() {
        Log.i(Constants.TAG, " onStop() called.");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(Constants.TAG, " onDestroy() called.");
        super.onDestroy();
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

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(Constants.TAG, " surfaceCreated() called.");
            initPreview(Constants.previewWidth, Constants.previewHeight);
            if (mCameraConfigured && mCamera != null) {
                mCamera.startPreview();
                mCamera.autoFocus(null);
                mInPreview = true;
            }
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
            if (count % 30 == 1) {
                mDraw.updateData(ArManager.getInstance().frameSnapshot());
            }
            ArManager.getInstance().getMarkers(new MarkerCallback() {
                @Override
                public void onResult(final Markers markers) {
                    mDraw.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.markers = markers;
                            mDraw.invalidate();
                        }
                    });
                }
            });
        }
    };

    class DrawOnTop extends View {
        Paint paintWord;
        Paint paintLine;
        private boolean ShowFPS = true;
        private boolean ShowEdge = true;
        private boolean ShowName = true;
        private int preFrameID;
        private int dispScale = Constants.scale;

        public DrawOnTop(Context context) {
            super(context);

            paintWord = new Paint();
            paintWord.setStyle(Paint.Style.STROKE);
            paintWord.setStrokeWidth(5);
            paintWord.setColor(Color.RED);
            paintWord.setTextAlign(Paint.Align.CENTER);
            paintWord.setTextSize(50);


            paintLine = new Paint();
            paintLine.setStyle(Paint.Style.STROKE);
            paintLine.setStrokeWidth(10);
            paintLine.setColor(Color.GREEN);
        }

        public void updateData(int frameID){
            if (ShowFPS) {
                time_n = (int) System.currentTimeMillis();
                fps = 1000 * (frameID - preFrameID) / (time_n - time_o);
                time_o = time_n;
                preFrameID = frameID;
            }
        }

        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);

            if (ShowFPS) {
                canvas.drawText("fps: " + fps, 100, 50, paintWord);
            }
            if (ShowEdge ) {
                if (markers != null && markers.Num != 0){
                    float[][] points = new float[4][2];
                    for (int i = 0; i < markers.Num; i++) {
                        if (markers.Recs[i] == null) continue;
                        for (int j = 0; j < 4; j++)
                            markers.Recs[i].get(j, 0, points[j]);
                        for (int j = 0; j < 4; j++) {
                            canvas.drawLine(dispScale * points[j][0], dispScale * points[j][1], dispScale * points[(j + 1) % 4][0], dispScale * points[(j + 1) % 4][1], paintLine);
                        }
                        if(ShowName)
                            canvas.drawText(markers.Names[i], dispScale * points[0][0] + 400, dispScale * points[0][1] + 200, paintWord);
                    }
                }
            }
        }
    }
}