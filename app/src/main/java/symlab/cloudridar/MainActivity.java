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

import org.rajawali3d.view.ISurface;

import symlab.core.ArManager;
import symlab.core.Constants;
import symlab.core.adapter.RenderAdapter;


public class MainActivity extends Activity implements View.OnTouchListener {

    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    protected ISurface mRenderSurface;
    protected PosterRenderer mRenderer;

    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private DrawOnTop mDraw;
    private byte[] callbackBuffer;
    private int time_o, time_n, fps;

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
            mRenderSurface = (ISurface) findViewById(R.id.rajwali_surface);
            mRenderer = new PosterRenderer(this, Constants.scale);
            mRenderSurface.setSurfaceRenderer(mRenderer);
            ((View) mRenderSurface).setOnTouchListener(this);
        }

        ArManager.getInstance().init(new RenderAdapter() {
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

        if (Constants.ShowGL)
            mRenderer.onActivityPause();

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

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(Constants.TAG, "touch event");
        if (Constants.ShowGL && event.getAction() == MotionEvent.ACTION_DOWN) {
            mRenderer.getObjectAt(event.getX(), event.getY());
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
                mDraw.invalidate();
            }
        }
    };

    class DrawOnTop extends View {
        Paint paintWord;
        private boolean ShowFPS = true;
        private int preFrameID;

        public DrawOnTop(Context context) {
            super(context);

            paintWord = new Paint();
            paintWord.setStyle(Paint.Style.STROKE);
            paintWord.setStrokeWidth(5);
            paintWord.setColor(Color.RED);
            paintWord.setTextAlign(Paint.Align.CENTER);
            paintWord.setTextSize(50);
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
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (ShowFPS) {
                canvas.drawText("fps: " + fps, 100, 50, paintWord);
            }
        }
    }
}