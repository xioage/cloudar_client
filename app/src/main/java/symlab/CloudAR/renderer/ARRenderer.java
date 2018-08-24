package symlab.CloudAR.renderer;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;

import java.util.ArrayList;
import java.util.List;

import symlab.CloudAR.Constants;
import symlab.CloudAR.marker.Marker;
import symlab.CloudAR.marker.MarkerGroup;

/**
 * Created by wzhangal on 7/27/2016.
 */
public class ARRenderer extends Renderer implements OnObjectPickedListener {
    private ARScene mScene;
    private ObjectColorPicker mPicker;
    private List<Integer> curMarkerIDs;

    public ARRenderer(Context context, ARScene mScene) {
        super(context);
        this.mScene = mScene;
        this.curMarkerIDs = new ArrayList<>();
    }

    @Override
    protected void initScene() {
        this.mPicker = new ObjectColorPicker(this);
        this.mPicker.setOnObjectPickedListener(this);

        for(float[] light : mScene.getLights()) {
            DirectionalLight directionalLight = new DirectionalLight();
            directionalLight.setPosition(light[0], light[1], light[2]);
            directionalLight.setLookAt(light[3], light[4], light[5]);
            directionalLight.setPower(light[6]);
            directionalLight.enableLookAt();
            getCurrentScene().addLight(directionalLight);
        }

        ARCamera mCamera = new ARCamera();
        getCurrentScene().replaceAndSwitchCamera(getCurrentCamera(), mCamera);
    }

    public void updateContents(MarkerGroup markerGroup){
        boolean hasChanged = !markerGroup.equals(this.curMarkerIDs);

        if (hasChanged){
            List<Integer> newIDs = new ArrayList<>(markerGroup.getIDs());
            List<Integer> expiredIDs = new ArrayList<>(curMarkerIDs);
            expiredIDs.removeAll(newIDs);
            newIDs.removeAll(this.curMarkerIDs);

            for (Integer expiredID : expiredIDs) {
                ARContent expiredContent = mScene.getContentByID(expiredID);
                expiredContent.destroy();
                getCurrentScene().removeChild(expiredContent.getObject());
            }

            for (Integer newID : newIDs) {
                ARContent newContent = mScene.getContentByID(newID);
                if(newContent != null) {
                    Marker marker = markerGroup.getMarkerByID(newID);
                    newContent.init(mContext, mPicker, (float) marker.size.width, (float) marker.size.height);
                    getCurrentScene().addChild(newContent.getObject());
                }
            }
        }

        for (int i = 0; i < markerGroup.size(); i++){
            Marker marker = markerGroup.getMarkerByIndex(i);
            float[] orientation = marker.getOrientation();

            if(orientation != null) {
                Matrix4 matrix = new Matrix4(orientation);
                Object3D object = mScene.getContentByID(marker.ID).getObject();
                object.setPosition(matrix.getTranslation());
                object.setRotation(matrix.inverse());
            }
        }

        if(hasChanged)
            this.curMarkerIDs = new ArrayList<>(markerGroup.getIDs());
    }

    public void updateAnnotation(int markerID, String annotationFile) {
        mScene.getContentByID(markerID).onAnnotationReceived(annotationFile);
    }

    @Override
    public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
        mScene.updateTexture(curMarkerIDs);
    }

    public void onActivityPause() {
        mScene.onActivityPause();
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

    @Override
    public void onObjectPicked(Object3D object) {
        Log.v(Constants.TAG, "something picked");
        callback.onTouchResponse(true);

        mScene.onTouch(curMarkerIDs, object);
    }

    @Override
    public void onNoObjectPicked() {
        Log.v(Constants.TAG, "No object picked!");
        callback.onTouchResponse(false);
    }

    private ARRenderer.Callback callback;

    public void setCallback(ARRenderer.Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onTouchResponse(boolean somethingPicked);
    }
}