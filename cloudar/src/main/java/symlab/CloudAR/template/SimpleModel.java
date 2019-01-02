package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.util.ObjectColorPicker;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.renderer.ARContent;

public class SimpleModel implements ARContent {
    private Plane mBase;
    private Cube[] cubes;
    private int activeIndex = 1;
    private float radius = 40, margin = 5;
    private float[] curX, curY, curZ, curScale;
    private int[] colors;

    @Override
    public void init(Context context, ObjectColorPicker mPicker, float width, float height) {
        mBase = new Plane(width+10, height+10, 1, 1);
        mBase.setPosition(0, 0, 0);
        Material baseMaterial = new Material();
        baseMaterial.enableLighting(true);
        baseMaterial.setColor(Color.LTGRAY);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        baseMaterial.setColorInfluence(10);
        mBase.setMaterial(baseMaterial);
        mBase.setVisible(true);

        curX = new float[]{0, 0};
        curY = new float[]{height/4, -height/4};
        curZ = new float[]{radius+margin, radius+margin};
        curScale = new float[]{1, 1};
        colors = new int[]{Color.GREEN, Color.RED};

        cubes = new Cube[2];
        for(int i = 0; i < 2; i++) {
            cubes[i] = new Cube(2 * radius);
            cubes[i].setPosition(curX[i], curY[i], curZ[i]);
            Material cubeMaterial = new Material();
            cubeMaterial.enableLighting(true);
            cubeMaterial.setColor(colors[i]);
            cubeMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            SpecularMethod.Phong phongMethod = new SpecularMethod.Phong();
            phongMethod.setShininess(180);
            cubeMaterial.setSpecularMethod(phongMethod);
            cubes[i].setMaterial(cubeMaterial);
            cubes[i].setVisible(true);
            mBase.addChild(cubes[i]);
            mPicker.registerObject(cubes[i]);
        }
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
        activeIndex = -1;
        for(int i = 0; i < 2; i++) {
            if(object == cubes[i]) {
                activeIndex = i;
                break;
            }
        }
        Log.d(Constants.TAG, "cur active object: " + activeIndex);
        return true;
    }

    @Override
    public void moveObject(float x, float y) {
        if(activeIndex < 0) return;

        curX[activeIndex] += x;
        curY[activeIndex] += y;
        cubes[activeIndex].setX(curX[activeIndex]);
        cubes[activeIndex].setY(curY[activeIndex]);
    }

    @Override
    public void scaleObject(float scaleFactor) {
        if(activeIndex < 0) return;

        curZ[activeIndex] = scaleFactor * radius + margin;
        curScale[activeIndex] = scaleFactor;
        cubes[activeIndex].setZ(curZ[activeIndex]);
        cubes[activeIndex].setScale(scaleFactor);
    }

    @Override
    public void setObjectStatus(float[] status) {
        Log.d(Constants.TAG, "apply peer status at " + System.currentTimeMillis());
        int curIndex = (int)status[0];
        curX[curIndex] = status[1];
        curY[curIndex] = status[2];
        curZ[curIndex] = status[3];
        curScale[curIndex] = status[4];
        cubes[curIndex].setX(curX[curIndex]);
        cubes[curIndex].setY(curY[curIndex]);
        cubes[curIndex].setZ(curZ[curIndex]);
        cubes[curIndex].setScale(curScale[curIndex]);
    }

    @Override
    public float[] serializeStatus() {
        return new float[]{activeIndex, curX[activeIndex], curY[activeIndex], curZ[activeIndex], curScale[activeIndex]};
    }

    @Override
    public void onAnnotationReceived(String annotationFile) {

    }

    @Override
    public void destroy() {

    }
}
