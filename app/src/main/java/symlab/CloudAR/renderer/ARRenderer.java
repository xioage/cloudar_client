package symlab.CloudAR.renderer;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;
import org.rajawali3d.util.RajLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import symlab.CloudAR.Constants;
import symlab.CloudAR.marker.Marker;
import symlab.CloudAR.marker.MarkerGroup;

/**
 * Created by wzhangal on 7/27/2016.
 */
public class ARRenderer extends Renderer implements OnObjectPickedListener {
    private ARCamera mCamera;
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
            PointLight pointLight = new PointLight();
            pointLight.setPosition(light[0], light[1], light[2]);
            pointLight.setLookAt(light[3], light[4], light[5]);
            pointLight.setPower(light[6]);
            getCurrentScene().addLight(pointLight);
        }

        for (Map.Entry<Integer, ARContent> content : mScene.getContents().entrySet()) {
            content.getValue().init(getContext(), mPicker);
            content.getValue().hide();
            getCurrentScene().addChild(content.getValue().getObject());
        }

        mCamera = new ARCamera();
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
                mScene.getContents().get(expiredID).hide();
            }

            for (Integer newID : newIDs){
                mScene.getContents().get(newID).show();
            }
        }

        for (int i = 0; i < markerGroup.size(); i++){
            Marker marker = markerGroup.getMarker(i);
            double[] orientation = marker.getOrientation();

            if(orientation != null) {
                Matrix4 matrix = new Matrix4(orientation);
                Object3D object = mScene.getContents().get(marker.getId()).getObject();
                object.setPosition(matrix.getTranslation());
                object.setRotation(matrix.inverse());
            }
        }

        if(hasChanged)
            this.curMarkerIDs = new ArrayList<>(markerGroup.getIDs());
    }

    @Override
    public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
        for (Map.Entry<Integer, ARContent> content : mScene.getContents().entrySet())
            content.getValue().updateTexture();
    }

    public void onActivityPause() {
        for (Map.Entry<Integer, ARContent> content : mScene.getContents().entrySet())
            content.getValue().onDisappear();
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
        for(Map.Entry<Integer, ARContent> content : mScene.getContents().entrySet()) {
            content.getValue().onTouch(object);
        }
    }

    @Override
    public void onNoObjectPicked() {
        RajLog.w("No object picked!");
    }
}