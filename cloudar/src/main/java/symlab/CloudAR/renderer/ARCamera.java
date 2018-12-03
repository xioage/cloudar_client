package symlab.CloudAR.renderer;

import org.rajawali3d.cameras.Camera;
import org.rajawali3d.math.Matrix;
import org.rajawali3d.math.Matrix4;

import symlab.CloudAR.definition.Constants;

/**
 * Created by wzhangal on 3/8/2017.
 */

public class ARCamera extends Camera {
    private final Matrix4 projectionMatrix;
    private Matrix4 viewMatrix;

    public ARCamera() {
        super();
        this.projectionMatrix = new Matrix4(calcProjectionMatrix());
        double[] view = new double[16];
        Matrix.setIdentityM(view, 0);
        this.viewMatrix = new Matrix4(view);
    }

    private double[] calcProjectionMatrix() {
        double[] projectionMatrix = new double[16];

        double fx = Constants.cameraMatrixData[0][0];
        double fy = Constants.cameraMatrixData[1][1];
        double cx = Constants.cameraMatrixData[0][2];
        double cy = Constants.cameraMatrixData[1][2];
        int width = Constants.previewWidth / Constants.scale;
        int height = Constants.previewHeight / Constants.scale;
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
