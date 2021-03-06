package symlab.CloudAR.definition;

import android.content.res.Resources;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.TermCriteria;
import org.opencv.highgui.Highgui;

/**
 * Created by st0rm23 on 2017/2/19.
 */

public class Constants {
    static public final int FREQUENCY = 120;
    static public final int TIMEOUT = 30;
    static public final int previewWidth = 1920;
    static public final int previewHeight = 1080;
    static public final int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    static public final int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
    static public final int scale = 4;
    static public final int recoScale = 4;
    static public final int cropScale = 3;
    static public final int MAX_POINTS = 100;
    static public final org.opencv.core.Size winSize = new org.opencv.core.Size(10, 10);
    static public final org.opencv.core.Size subPixWinSize = new org.opencv.core.Size(1, 1);
    static public final TermCriteria termcrit = new TermCriteria(TermCriteria.MAX_ITER | TermCriteria.EPS, 20, 0.03);
    static public final String TAG = "Poster";
    static public final String Eval = "Evaluation";
    static public final double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 240}, {0, 3.9324438974006659e+002, 135}, {0, 0, 1}};
    //static public final double[][] cameraMatrixData = new double[][]{{803.52, 0, 480}, {0, 803.52, 270}, {0, 0, 1}};
    static private final double[] distCoeffsData = new double[]{0, 0, 0, 0, 0};
    static private final double[][] cvToGlData = new double[][]{{1.0, 0, 0, 0}, {0, -1.0, 0, 0}, {0, 0, -1.0, 0}, {0, 0, 0, 1.0}};

    static public final MatOfInt Image_Params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, 70);
    static public final int TRACKING_THRESHOLD = 10;
    static public final int SWITCH_THRESHOLD = 20;

    static public final boolean EnableMultipleTracking = false;
    static public final boolean Enable2DView = true;
    static public final boolean EnableUniversalContent = false;

    static public final MatOfDouble distCoeffs = new MatOfDouble();
    static public final Mat cameraMatrix = new Mat(3, 3, CvType.CV_64FC1);
    static public final Mat cvToGl = new Mat(4, 4, CvType.CV_64FC1);

    static public final int RES_SIZE = 512;

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
