package symlab.cloudridar;

import android.content.Context;
import android.graphics.Color;
import android.util.Pair;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.cameras.Camera;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.math.Matrix;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.renderer.Renderer;

import java.util.ArrayList;

import symlab.core.track.MarkerImpl;

/**
 * Created by wzhangal on 7/27/2016.
 */
public class MyRenderer extends Renderer {
    private int scale;
    private ArrayList<Pair<MarkerImpl.Marker, Object3D>> data;

    public MyRenderer(Context context, int scale) {
        super(context);
        this.scale = scale;
    }

    private MyCamera myCamera;

    @Override
    protected void initScene() {
        PointLight light = new PointLight();
        light.setY(2f);
        light.setPower(20f);
        getCurrentScene().addLight(light);

        myCamera = new MyCamera();
        getCurrentScene().replaceAndSwitchCamera(getCurrentCamera(), myCamera);
        data = new ArrayList<>();
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    public void updateMarker(ArrayList<MarkerImpl.Marker> markers){
        boolean hasChanged = false;
        if (markers.size() != data.size()) hasChanged = true;
        if (!hasChanged){
            for (int i=0; i<markers.size(); i++){
                if (data.get(i).first.getId() != markers.get(i).getId()){
                    hasChanged = true;
                    break;
                }
                Object3D cube = data.get(i).second;


                Matrix4 matrix = new Matrix4(markers.get(i).getOrientation());
                cube.setPosition(matrix.getTranslation());
                cube.setRotation(matrix.inverse());
            }
        }
        if (hasChanged){
            for (Pair<MarkerImpl.Marker, Object3D> pair : data){
                getCurrentScene().removeChild(pair.second);
            }
            data.clear();
            for (MarkerImpl.Marker marker : markers){
                Cube cube = new Cube(5);
                Material baseMaterial = new Material();
                baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
                SpecularMethod.Phong phongMethod = new SpecularMethod.Phong();
                phongMethod.setShininess(180);
                baseMaterial.setSpecularMethod(phongMethod);
                baseMaterial.setAmbientIntensity(0, 0, 0);
                baseMaterial.enableLighting(true);
                baseMaterial.setColor(Color.RED);
                cube.setMaterial(baseMaterial);
                Matrix4 matrix = new Matrix4(marker.getOrientation());
                cube.setPosition(matrix.getTranslation());
                cube.setRotation(matrix.inverse());

                data.add(new Pair<MarkerImpl.Marker, Object3D>(marker, cube));
                getCurrentScene().addChild(cube);
            }
        }
    }

    private class MyCamera extends Camera{

        private final double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};
        private final double[] projectionMatrix;
        private double[] viewMatrix;

        public MyCamera(){
            projectionMatrix = calcProjectionMatrix(cameraMatrixData);
            viewMatrix = new double[16];
            Matrix.setIdentityM(viewMatrix, 0);
        }

        private double[] calcProjectionMatrix(double[][] cameraMatrixData) {
            double[] projectionMatrix = new double[16];

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


        @Override
        public Matrix4 getViewMatrix() {
            return new Matrix4(viewMatrix);
        }

        @Override
        public Matrix4 getProjectionMatrix() {
            return new Matrix4(projectionMatrix);
        }
    }
}


