package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.util.ObjectColorPicker;

import java.util.Stack;

import symlab.CloudAR.renderer.ARContent;

/**
 * Created by wzhangal on 1/24/2018.
 */

public class ImageBorder implements ARContent {
    private Context context;
    private ObjectColorPicker mPicker;
    private Plane mBase;
    private Line3D mBorder;
    private int color;
    private double w = 5.1;
    private double h = 7.2;

    public ImageBorder(int color) {
        this.color = color;
    }

    @Override
    public void init(Context context, ObjectColorPicker mPicker, float width, float height) {
        this.context = context;
        this.mPicker = mPicker;

        mBase = new Plane(0.1f, 0.1f, 1, 1);
        mBase.setPosition(0, 0, 0);
        Material baseMaterial = new Material();
        baseMaterial.enableLighting(false);
        baseMaterial.setColor(Color.TRANSPARENT);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mBase.setMaterial(baseMaterial);
        mBase.setVisible(true);

        Stack<Vector3> points = new Stack<>();
        points.add(new Vector3(-w, -h, 0));
        points.add(new Vector3(-w, h, 0));
        points.add(new Vector3(-w, h, 0));
        points.add(new Vector3(w, h, 0));
        points.add(new Vector3(w, h, 0));
        points.add(new Vector3(w, -h, 0));
        points.add(new Vector3(w, -h, 0));
        points.add(new Vector3(-w, -h, 0));

        mBorder = new Line3D(points, 20, this.color);
        mBorder.setMaterial(new Material());
        mBase.addChild(mBorder);

        mPicker.registerObject(mBase);
    }

    @Override
    public Object3D getObject() {
        return this.mBase;
    }

    @Override
    public void updateTexture() {
    }

    @Override
    public boolean onTouch(Object3D object) {
        return false;
    }

    @Override
    public void moveObject(float x, float y) {}

    @Override
    public void scaleObject(float scaleFactor) {}

    @Override
    public void setObjectStatus(float[] status) {}

    @Override
    public float[] serializeStatus() {return null;}

    @Override
    public void onAnnotationReceived(String annotationFile) {
    }

    @Override
    public void destroy() {
    }
}
