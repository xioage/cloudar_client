package symlab.CloudAR.track;

import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedList;

import static symlab.CloudAR.Constants.Eval;
import static symlab.CloudAR.Constants.previewHeight;
import static symlab.CloudAR.Constants.previewWidth;
import static symlab.CloudAR.Constants.scale;

/**
 * Created by wzhangal on 4/24/2017.
 */

public class MatchingTask implements Runnable{

    private byte[] frameData;
    private int frameID;

    FeatureDetector detector_surf = FeatureDetector.create(FeatureDetector.ORB);
    DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
    DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
    MatOfKeyPoint targetKeypoint = new MatOfKeyPoint();
    Mat targetDescriptor = new Mat();

    public void setFrameData(int frameID, byte[] frameData){
        this.frameData = frameData;
        this.frameID = frameID;
    }

    public void run() {
        Mat YUV = new Mat(previewHeight + previewHeight / 2, previewWidth, CvType.CV_8UC1);
        Mat YUVScaled = new Mat((previewHeight + previewHeight / 2) / scale, previewWidth / scale, CvType.CV_8UC1);
        Mat GrayScaled = new Mat(previewHeight / scale, previewWidth / scale, CvType.CV_8UC1);
        MatOfKeyPoint points = new MatOfKeyPoint();
        Mat descriptors = new Mat();

        Long tsLong = System.currentTimeMillis();
        String ts_start = tsLong.toString();
        Log.d(Eval, "feature start " + frameID + " data: " + ts_start);

        YUV.put(0, 0, frameData);
        Imgproc.resize(YUV, YUVScaled, YUVScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
        Imgproc.cvtColor(YUVScaled, GrayScaled, Imgproc.COLOR_YUV420sp2GRAY);


        detector_surf.detect(GrayScaled, points);
        descriptorExtractor.compute(GrayScaled, points, descriptors);

        tsLong = System.currentTimeMillis();
        String ts_end = tsLong.toString();
        Log.d(Eval, "feature end " + frameID + " data: " + ts_end);

        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors, targetDescriptor, matches);
        LinkedList<DMatch> matchesList = new LinkedList<>(matches.toList());
        LinkedList<DMatch> good_matches = new LinkedList<>();

        double min_dist = 100;
        for(int i = 0; i < matchesList.size(); i++) {
            double dist = matchesList.get(i).distance;
            if(dist < min_dist)
                min_dist = dist;
        }

        for (int i=0;i<descriptors.rows();i++){
            if(matchesList.get(i).distance<3*min_dist) {// 3*min_dist is my threshold here
                good_matches.addLast(matchesList.get(i));
            }
        }

        LinkedList<Point> objList = new LinkedList<>();
        LinkedList<Point> sceneList = new LinkedList<>();
        for(int i=0;i<good_matches.size();i++){
            objList.addLast(targetKeypoint.toList().get(good_matches.get(i).trainIdx).pt);
            sceneList.addLast(points.toList().get(good_matches.get(i).queryIdx).pt);
        }

        MatOfPoint2f obj = new MatOfPoint2f();
        MatOfPoint2f scene = new MatOfPoint2f();
        obj.fromList(objList);
        scene.fromList(sceneList);
        Mat H = Calib3d.findHomography(obj,scene);

        tsLong = System.currentTimeMillis();
        String ts_match = tsLong.toString();
        Log.d(Eval, "match end " + frameID + " data: " + ts_match);

    }
}
