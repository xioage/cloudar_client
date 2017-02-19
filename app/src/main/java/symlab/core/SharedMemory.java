package symlab.core;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;

import java.util.LinkedList;
import java.util.Queue;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.cloudridar.Markers;

/**
 * Created by st0rm23 on 2017/2/19.
 */

public class SharedMemory {

    static public Mat YUVMatTrack, YUVMatTrans, YUVMatScaled;
    static public Mat BGRMat, BGRMatScaled;
    static public Mat PreGRAYMat;

    public static boolean InitializeNeeded = true;
    public static boolean PosterChanged = false;
    public static boolean PosterRecognized = false;
    public static boolean Show2DView = true;
    public static boolean ShowGL = true;
    public static boolean EnableMultipleTracking = false;

    public static int frmID = 1;
    public static int lastSentID = -1;
    public static int resID;
    public static int trackingID = 0;
    public static int oldFrmID;
    public static MatOfPoint2f Points1;
    public static int[] bitmap1;
    public static Queue<HistoryTrackingPoints> HistoryQueue = new LinkedList<HistoryTrackingPoints>();
    public static Markers Markers0, Markers1;
    static public MatOfDouble distCoeffs = new MatOfDouble();
    static public MatOfPoint3f posterPoints = new MatOfPoint3f();
    static public Mat cameraMatrix = new Mat(3, 3, CvType.CV_64FC1);
    static public Mat cvToGl = new Mat(4, 4, CvType.CV_64FC1);
    static public Mat viewMatrix = Mat.zeros(4, 4, CvType.CV_64FC1);
    static public double[] glViewMatrixData;

    static public void initMemory(){
        YUVMatTrack = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatTrans = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        BGRMat = new Mat(Constants.previewHeight, Constants.previewWidth, CvType.CV_8UC3);
        BGRMatScaled = new Mat(Constants.previewHeight / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC3);

        for(int i = 0; i < 3; i++)
            for(int j = 0; j < 3; j++)
                cameraMatrix.put(i, j, Constants.cameraMatrixData[i][j]);
        distCoeffs.fromArray(Constants.distCoeffsData);
        for(int i = 0; i < 4; i++)
            for(int j = 0; j < 4; j++)
                cvToGl.put(i, j, Constants.cvToGlData[i][j]);
    }
}
