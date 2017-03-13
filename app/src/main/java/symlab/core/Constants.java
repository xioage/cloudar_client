package symlab.core;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.TermCriteria;
import org.opencv.highgui.Highgui;

/**
 * Created by st0rm23 on 2017/2/19.
 */

public class Constants {
    static public final String ip = "104.199.140.59";
    static public final int portNum = 51717;

    static public final int FREQUENCY = 30;
    static public final int previewWidth = 1920;
    static public final int previewHeight = 1080;
    static public final int scale = 4;
    static public final int MAX_POINTS = 60;
    static public final org.opencv.core.Size winSize = new org.opencv.core.Size(10, 10);
    static public final org.opencv.core.Size subPixWinSize = new org.opencv.core.Size(31, 31);
    static public final TermCriteria termcrit = new TermCriteria(TermCriteria.MAX_ITER | TermCriteria.EPS, 20, 0.03);
    static public final String TAG = "Poster";
    static public final String Eval = "Evaluation";
    static public final Point3[][] posterPointsData = new Point3[][]{{new Point3(-5, 7.4, 0), new Point3(5, 7.4, 0), new Point3(5, -7.4, 0), new Point3(-5, -7.4, 0)},
            {new Point3(-5, 7.2, 0), new Point3(5, 7.2, 0), new Point3(5, -7.2, 0), new Point3(-5, -7.2, 0)},
            {new Point3(5, 5, 0), new Point3(15, 5, 0), new Point3(15, -5, 0), new Point3(5, -5, 0)},
            {new Point3(5, 8, 0), new Point3(15, 8, 0), new Point3(15, -8, 0), new Point3(5, -8, 0)},
            {new Point3(5, 6.6, 0), new Point3(15, 6.6, 0), new Point3(15, -6.6, 0), new Point3(5, -6.6, 0)}};
    static public final double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};
    static public final double[] distCoeffsData = new double[]{2.8048006231906419e-001, -1.1828928706191699e+000, 0, 0, 1.4865861018485209e+000};
    static public final double[][] cvToGlData = new double[][]{{1.0, 0, 0, 0}, {0, -1.0, 0, 0}, {0, 0, -1.0, 0}, {0, 0, 0, 1.0}};

    static public final MatOfInt Image_Params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, 50);
    static public final int TRACKING_THRESHOLD = 10;
    static public final int SWITCH_THRESHOLD = 20;

    static public final boolean EnableMultipleTracking = true;
    static public final boolean ShowGL = true;
    static public final boolean Show2DView = true;

    static public final MatOfDouble distCoeffs = new MatOfDouble();
    static public final Mat cameraMatrix = new Mat(3, 3, CvType.CV_64FC1);
    static public final Mat cvToGl = new Mat(4, 4, CvType.CV_64FC1);

    static {
        for(int i = 0; i < 3; i++)
            for(int j = 0; j < 3; j++)
                cameraMatrix.put(i, j, Constants.cameraMatrixData[i][j]);
        distCoeffs.fromArray(Constants.distCoeffsData);
        for(int i = 0; i < 4; i++)
            for(int j = 0; j < 4; j++)
                cvToGl.put(i, j, Constants.cvToGlData[i][j]);
    }


}
