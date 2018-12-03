package symlab.posterApp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import org.rajawali3d.view.ISurface;

import symlab.CloudAR.manager.ARManager;
import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.definition.MarkerGroup;
import symlab.CloudAR.renderer.AR2DView;
import symlab.CloudAR.renderer.ARRenderer;
import symlab.CloudAR.renderer.ARScene;

public class PosterActivity extends Activity {
    private ARScene mScene;
    private SurfaceView mPreview;
    private AR2DView mDraw;
    private SurfaceHolder mPreviewHolder;
    private ISurface mRenderSurface;
    private ARRenderer mRenderer;
    private ARManager mManager;

    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private boolean mSurfaceCreated = false;
    private boolean managerStarted = false;
    private boolean somethingRecognized = false;
    private boolean recoFlag = false;
    private byte[] callbackBuffer;
    private boolean isCloudBased = false;
    private float touchX, touchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(Constants.TAG, " onCreate() called.");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mPreview = (SurfaceView)findViewById(R.id.preview);
        mPreview.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        mPreviewHolder = mPreview.getHolder();
        mPreviewHolder.addCallback(surfaceCallback);
        mPreview.setZOrderMediaOverlay(false);

        mScene = new PosterScene();
        mRenderSurface = (org.rajawali3d.view.SurfaceView) findViewById(R.id.rajwali_surface);
        mRenderer = new ARRenderer(this, mScene);
        mRenderSurface.setSurfaceRenderer(mRenderer);
        mRenderer.setCallback(new ARRenderer.Callback() {
            @Override
            public void onTouchResponse(boolean somethingPicked) {
                if(!somethingPicked && !somethingRecognized) recoFlag = true;
            }
        });

        mDraw = (AR2DView)findViewById(R.id.textview);
        mDraw.setCloudStatus(isCloudBased);

        mManager = new ARManager();
        mManager.init(this, isCloudBased, false, true, mScene.getContentIDs());
        mManager.setCallback(new ARManager.Callback() {
            @Override
            public void onMarkersReady(MarkerGroup markerGroup) {
                mRenderer.updateContents(markerGroup);
                if(markerGroup.size() > 0) mDraw.setStatus(2);
                else mDraw.setStatus(4);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDraw.invalidate();
                    }
                });
                somethingRecognized = markerGroup.size() > 0;
            }

            @Override
            public void onMarkersChanged(MarkerGroup markerGroup) {
                mRenderer.updateContents(markerGroup);
            }

            @Override
            public void onCloudTimeout() {
                somethingRecognized = false;
                mDraw.setStatus(5);
                mDraw.invalidate();
            }

            @Override
            public void onAnnotationReceived(int markerID, String annotationFile) {
                mRenderer.updateAnnotation(markerID, annotationFile);
            }

            @Override
            public void onAnnotationStatusReceived(float[] status) {}
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

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        else if(mCamera == null) {
            mCamera = Camera.open();
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        else if(!managerStarted) {
            mManager.start();
            managerStarted = true;
        }
    }

    @Override
    public void onPause() {
        Log.i(Constants.TAG, " onPause() called.");
        super.onPause();

        if (mInPreview) {
            mCamera.stopPreview();

            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
            mInPreview = false;
        }

        if(managerStarted)
            mManager.stop();
        mRenderer.onActivityPause();
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
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mRenderer.getObjectAt(ev.getX(), ev.getY());
            touchX = ev.getX() / (1.0f * Constants.screenWidth / Constants.previewWidth);
            touchY = ev.getY() / (1.0f * Constants.screenHeight / Constants.previewHeight);
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // BEGIN_INCLUDE(onRequestPermissionsResult)
        if (requestCode == 0) {
            // Request for camera permission.
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted. Start camera preview Activity.
                mCamera = Camera.open();
                if(mSurfaceCreated && !mInPreview) configureCameraAndPreview();
            } else {
            }
        } else if (requestCode == 1) {
            if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mManager.start();
                managerStarted = true;
            } else {
            }
        }
        // END_INCLUDE(onRequestPermissionsResult)
    }

    private void configureCameraAndPreview() {
        if (!mCameraConfigured) {
            Camera.Parameters params = mCamera.getParameters();
            params.setPreviewSize(Constants.previewWidth, Constants.previewHeight);

            callbackBuffer = new byte[(Constants.previewHeight + Constants.previewHeight / 2) * Constants.previewWidth];
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

        mCamera.startPreview();
        mCamera.autoFocus(null);
        mInPreview = true;
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(Constants.TAG, " surfaceCreated() called.");
            mSurfaceCreated = true;
            if (mCamera != null) configureCameraAndPreview();
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

            if (recoFlag) {
                mManager.recognize(data, touchX, touchY);
                recoFlag = false;
                somethingRecognized = true;
                mDraw.setStatus(1);
            } else {
                mManager.driveFrame(data);
            }
            mDraw.pulse();
        }
    };
}