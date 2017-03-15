package symlab.cloudridar;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;
import org.rajawali3d.util.RajLog;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import symlab.core.impl.MarkerGroup;
import symlab.core.renderer.MyCamera;

/**
 * Created by wzhangal on 7/27/2016.
 */
public class PosterRenderer extends Renderer implements OnObjectPickedListener {
    private int scale;
    private MyCamera mCamera = new MyCamera();
    private ObjectColorPicker mPicker;
    private Trailer[] trailers = new Trailer[2];
    private List<Integer> curMarkerIDs = new ArrayList<>();

    public PosterRenderer(Context context, int scale) {
        super(context);
        this.scale = scale;
    }

    @Override
    protected void initScene() {
        PointLight light = new PointLight();
        light.setPosition(10, 0, 3);
        light.setLookAt(0, 0, -1);
        light.setPower(1f);
        getCurrentScene().addLight(light);

        mPicker = new ObjectColorPicker(this);
        mPicker.setOnObjectPickedListener(this);

        trailers[0] = new Trailer(getContext(), mPicker);
        trailers[0].hide();
        trailers[0].setPosition(0, 0, 0);
        getCurrentScene().addChild(trailers[0]);
        for (int i = 1; i < trailers.length; i++) {
            trailers[i] = new Trailer(getContext(), mPicker);
            trailers[i].hide();
            trailers[i].setPosition(i * -16, 0, 0);
            trailers[0].addChild(trailers[i]);
        }

        mCamera.setProjectionMatrix(calcProjectionMatrix());
        getCurrentScene().replaceAndSwitchCamera(getCurrentCamera(), mCamera);
    }

    private double[] calcProjectionMatrix() {
        double[] projectionMatrix = new double[16];
        double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};

        double fx = cameraMatrixData[0][0];
        double fy = cameraMatrixData[1][1];
        double cx = cameraMatrixData[0][2];
        double cy = cameraMatrixData[1][2];
        int width = 1920 / scale;
        int height = 1080 / scale;
        int far = 1000;
        int near = 2;

        projectionMatrix[0] = 2 * fx / width;
        projectionMatrix[1] = 0;
        projectionMatrix[2] = 0;
        projectionMatrix[3] = 0;

        projectionMatrix[4] = 0;
        projectionMatrix[5] = 2 * fy / height;
        projectionMatrix[6] = 0;
        projectionMatrix[7] = 0;

        projectionMatrix[8] = 1.0 - 2 * cx / width;
        projectionMatrix[9] = 2 * cy / height - 1.0;
        projectionMatrix[10] = -(far + near) / (far - near);
        projectionMatrix[11] = -1.0;

        projectionMatrix[12] = 0;
        projectionMatrix[13] = 0;
        projectionMatrix[14] = -2.0 * far * near / (far - near);
        projectionMatrix[15] = 0;

        return projectionMatrix;
    }

    public void onPosterChanged(MarkerGroup markerGroup) {
        List<Integer> newPoster = markerGroup.getIDs();
        List<Integer> expiredPoster = new ArrayList<>(curMarkerIDs);
        expiredPoster.removeAll(newPoster);
        newPoster.removeAll(curMarkerIDs);
        Log.d("render", "new poster: " + newPoster.size() + "expired poster: " + expiredPoster.size());

        for (int i = 0; i < trailers.length; i++) {
            int trailerID = trailers[i].getID();

            if (trailerID == -1 && newPoster.size() > 0) {
                int curID = newPoster.get(0);
                trailers[i].setTrailerContent(curID);
                trailers[i].show();
                newPoster.remove(0);
            } else if (expiredPoster.size() > 0) {
                if (expiredPoster.contains(trailerID)) {
                    trailers[i].onDisappear();
                    expiredPoster.remove(expiredPoster.indexOf(trailerID));
                }
            }
        }

        curMarkerIDs = markerGroup.getIDs();
    }

    @Override
    public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
        for (int i = 0; i < trailers.length; i++)
            trailers[i].updateTexture();
    }

    public void setGlViewMatrix(double[] glViewMatrixData) {
        mCamera.setViewMatrix(glViewMatrixData);
    }

    @Override
    public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
        super.onRenderSurfaceCreated(config, gl, width, height);
    }

    public void onActivityPause() {
        for (int i = 0; i < trailers.length; i++) {
            trailers[i].onDisappear();
        }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
    }

    public void getObjectAt(float x, float y) {
        mPicker.getObjectAt(x, y);
    }

    public void onObjectPicked(Object3D object) {
        int pickedTrailer = -1;

        for(int i = 0; i < trailers.length; i++) {
            if(trailers[i].onTouch(object)) {
                pickedTrailer = i;
            }
        }
        for(int i = 0; i < trailers.length; i++) {
            if(pickedTrailer == -1)
                trailers[i].show();
            else if(pickedTrailer != i)
                trailers[i].hide();
        }
    }

    @Override
    public void onNoObjectPicked() {
        RajLog.w("No object picked!");
    }
}