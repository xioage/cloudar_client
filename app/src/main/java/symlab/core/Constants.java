package symlab.core;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.TermCriteria;

/**
 * Created by st0rm23 on 2017/2/19.
 */

public class Constants {

    static public final int FREQUENCY = 30;
    static public final int previewWidth = 1920;
    static public final int previewHeight = 1080;
    static public final int scale = 4;
    static public final int MAX_POINTS = 240;
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
}
