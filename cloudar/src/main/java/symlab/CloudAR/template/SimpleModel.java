package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.util.ObjectColorPicker;

import symlab.CloudAR.renderer.ARContent;

public class SimpleModel implements ARContent {
    private Plane mBase;
    private Cube cube;
    private float curX = 0, curY = 0, curZ = 0, curScale = 1;

    @Override
    public void init(Context context, ObjectColorPicker mPicker, float width, float height) {
        mBase = new Plane(0.001f, 0.001f, 1, 1);
        mBase.setPosition(0, 0, 0);
        Material baseMaterial = new Material();
        baseMaterial.enableLighting(false);
        baseMaterial.setColor(Color.WHITE);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mBase.setMaterial(baseMaterial);
        mBase.setVisible(true);

        cube = new Cube(100);
        cube.setPosition(0, 0, 50);
        Material cubeMaterial = new Material();
        cubeMaterial.enableLighting(true);
        cubeMaterial.setColor(Color.WHITE);
        cubeMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        cube.setMaterial(cubeMaterial);
        cube.setVisible(true);
        mBase.addChild(cube);
    }

    @Override
    public Object3D getObject() {
        return mBase;
    }

    @Override
    public void updateTexture() {

    }

    @Override
    public boolean onTouch(Object3D object) {
        return false;
    }

    @Override
    public void moveObject(float x, float y) {
        curX += x;
        curY += y;
        cube.setX(curX);
        cube.setY(curY);
    }

    @Override
    public void scaleObject(float scaleFactor) {
        curZ = scaleFactor * 50;
        curScale = scaleFactor;
        cube.setZ(curZ);
        cube.setScale(scaleFactor);
    }

    @Override
    public void setObjectStatus(float[] status) {
        curX = status[0];
        curY = status[1];
        curZ = status[2];
        curScale = status[3];
        cube.setX(curX);
        cube.setY(curY);
        cube.setZ(curZ);
        cube.setScale(curScale);
    }

    @Override
    public float[] serializeStatus() {
        return new float[]{curX, curY, curZ, curScale};
    }

    @Override
    public void onAnnotationReceived(String annotationFile) {

    }

    @Override
    public void destroy() {

    }
}
