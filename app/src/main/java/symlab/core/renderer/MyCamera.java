package symlab.core.renderer;

import org.rajawali3d.cameras.Camera;
import org.rajawali3d.math.Matrix4;

/**
 * Created by wzhangal on 3/8/2017.
 */

public class MyCamera extends Camera {
    private Matrix4 projectionMatrix = new Matrix4();
    private Matrix4 viewMatrix = new Matrix4();

    public MyCamera() {
        super();
    }

    public void setProjectionMatrix(double[] projectionMatrix) {
        this.projectionMatrix = new Matrix4(projectionMatrix);
    }

    public void setViewMatrix(double[] viewMatrix) {
        this.viewMatrix = new Matrix4(viewMatrix);
    }

    @Override
    public Matrix4 getViewMatrix() {
        return this.viewMatrix;
    }

    @Override
    public Matrix4 getProjectionMatrix() {
        return this.projectionMatrix;
    }
}
