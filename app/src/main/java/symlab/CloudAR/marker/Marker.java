package symlab.CloudAR.marker;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Size;

/**
 * Created by wzhangal on 4/25/2017.
 *
 * a marker for MarkerImpl to track.
 * user input the basic information of marker including id, origin vertices, screen vertices.
 * when finished calculating in MarkerImpl.Callback#onMarkersChanged(ArrayList), user can
 * get the correct model view matrix via {@link Marker#getOrientation()}
 *
 * @author Lin Binghui
 * @version 1.0
 */

public class Marker {
    private int ID;
    private String name;
    private Size size;
    private MatOfPoint2f vertices;
    private MatOfPoint3f origin;
    private Mat homography;
    private int trackingPointsNum;
    private double[] orientation;
    private boolean isValid;

    public Marker(int ID, String name, Size size, MatOfPoint2f vertices) {
        this.ID = ID;
        this.name = name;
        this.size = size;
        this.vertices = vertices;

        this.origin = new MatOfPoint3f();
        double w = size.width / 25;
        double h = size.height / 25;
        this.origin.fromArray(new Point3(-w, h, 0), new Point3(w, h, 0), new Point3(w, -h, 0), new Point3(-w, -h, 0));

        this.isValid = true;
    }

    public int getId(){
        return this.ID;
    }

    public boolean isValid(){
        return this.isValid;
    }

    public MatOfPoint2f getVertices() {
        return this.vertices;
    }

    public MatOfPoint3f getOrigin() {
        return this.origin;
    }

    /**
     * get the model matrix of model
     *
     * @return double[16] represent for 4x4 model view matrix
     */
    public double[] getOrientation(){
        return this.orientation;
    }

    public Mat getHomography() {
        return this.homography;
    }

    public void setVertices(MatOfPoint2f vertices) {
        this.vertices = vertices;
    }

    public void setHomography(Mat homography) {
        this.homography = homography;
    }

    public void setOrientation(double[] orientation) {
        this.orientation = orientation;
    }

    public void setValid(boolean valid) {
        this.isValid = valid;
    }
}