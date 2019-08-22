package symlab.CloudAR.recognition;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.vlfeat.VLFeat;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.definition.Marker;
import symlab.CloudAR.definition.MarkerGroup;
import symlab.cloudar.R;

import static symlab.CloudAR.definition.Constants.Eval;
import static symlab.CloudAR.definition.Constants.cropScale;
import static symlab.CloudAR.definition.Constants.previewHeight;
import static symlab.CloudAR.definition.Constants.previewWidth;
import static symlab.CloudAR.definition.Constants.recoScale;

/**
 * Created by wzhangal on 4/24/2017.
 */

public class MatchingTaskRetrieval implements MatchingTask{
    private Callback callback;
    private VLFeat vl;

    private Context context;
    private Set<Integer> contentIDs;
    private int featureType;
    private byte[] frameData;
    private int frameID;
    private int offset;
    private float goodMatchThreshold;

    private List<Mat> images;
    private List<MatOfKeyPoint> localKeyPoints;
    private List<Mat> localDescriptors;

    private FeatureDetector detector;
    private DescriptorExtractor descriptorExtractor;
    private DescriptorMatcher matcher;

    private Mat YUV = new Mat(previewHeight + previewHeight / 2, previewWidth, CvType.CV_8UC1);
    private Mat YUVScaled = new Mat((previewHeight + previewHeight / 2) / recoScale, previewWidth / recoScale, CvType.CV_8UC1);
    private Mat GrayCropped = new Mat(previewHeight / recoScale, previewWidth / recoScale / cropScale, CvType.CV_8UC1);

    private int recoTrackRatio = Constants.scale / Constants.recoScale;

    public MatchingTaskRetrieval(Context context, Set<Integer> contentIDs, int featureType) {
        this.context = context;
        this.contentIDs = contentIDs;
        this.featureType = featureType;

        switch (featureType) {
            case MatchingTask.orb:
                detector = FeatureDetector.create(FeatureDetector.ORB);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/orb_params.yml");
                goodMatchThreshold = 60;
                break;
            case MatchingTask.sift:
                detector = FeatureDetector.create(FeatureDetector.SIFT);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.SIFT);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
                goodMatchThreshold = 150;
                break;
        }
        vl = new VLFeat();

        test();
    }

    @Override
    public void setData(int frameID, byte[] frameData, int offset){
        this.frameData = frameData;
        this.frameID = frameID;
        this.offset = offset / Constants.recoScale;
    }

    @Override
    public void run() {
        Long ts;
        if(this.images == null) {
            ts = System.currentTimeMillis();
            Log.d(Eval, "local extraction start: " + ts.toString());

            images = new LinkedList<>();
            localKeyPoints = new LinkedList<>();
            localDescriptors = new LinkedList<>();

            try {
                images.add(Utils.loadResource(context, R.drawable.aquaman));
                images.add(Utils.loadResource(context, R.drawable.fantastic));
                images.add(Utils.loadResource(context, R.drawable.smallfoot));
                images.add(Utils.loadResource(context, R.drawable.bvs_poster));
                images.add(Utils.loadResource(context, R.drawable.london_poster));
                images.add(Utils.loadResource(context, R.drawable.mjd_poster));
                images.add(Utils.loadResource(context, R.drawable.tfos_poster));
                images.add(Utils.loadResource(context, R.drawable.tig_poster));
                images.add(Utils.loadResource(context, R.drawable.avengers));
                images.add(Utils.loadResource(context, R.drawable.ready));
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (Mat image : images) {
                MatOfKeyPoint localKeypoint = new MatOfKeyPoint();
                Mat localDescriptor = new Mat();

                int scale = 2;
                Imgproc.resize(image, image, new Size(image.width() / scale, image.height() / scale), 0, 0, Imgproc.INTER_LINEAR);
                Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2GRAY);

                detector.detect(image, localKeypoint);
                descriptorExtractor.compute(image, localKeypoint, localDescriptor);
                vl.addImage(localDescriptor.nativeObj);
                localKeyPoints.add(localKeypoint);
                localDescriptors.add(localDescriptor);
            }

            switch (featureType) {
                case MatchingTask.orb:
                    vl.trainBMM();
                    vl.FVEncodeDatabaseBMM();
                    break;
                case MatchingTask.sift:
                    vl.trainPCA();
                    vl.trainGMM();
                    vl.FVEncodeDatabaseGMM();
                    break;
            }
        } else {
            Log.d(Eval, "before pre-processing: " + System.currentTimeMillis());

            MatOfKeyPoint points = new MatOfKeyPoint();
            Mat descriptors = new Mat();

            YUV.put(0, 0, frameData);
            Imgproc.resize(YUV, YUVScaled, YUVScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            Mat YUVCropped = new Mat(YUVScaled, new Rect(this.offset, 0, Constants.previewWidth / recoScale / cropScale, (Constants.previewHeight + Constants.previewHeight / 2) / recoScale));
            Imgproc.cvtColor(YUVCropped, GrayCropped, Imgproc.COLOR_YUV420sp2GRAY);

            Log.d(Eval, "matching start: " + System.currentTimeMillis());

            detector.detect(GrayCropped, points);
            descriptorExtractor.compute(GrayCropped, points, descriptors);
            Log.d(Eval, "" + points.rows() + " feature detected at " + System.currentTimeMillis());

            int[] good_indexes = {0};
            switch (featureType) {
                case MatchingTask.orb:
                    good_indexes = vl.matchBMM(descriptors.nativeObj);
                    break;
                case MatchingTask.sift:
                    good_indexes = vl.matchGMM(descriptors.nativeObj);
                    break;
            }
            Log.d(Eval, "indexes " + good_indexes[0] + " at " + System.currentTimeMillis());

            good_indexes[0] = 2;
            int best_index = 0;
            MatOfKeyPoint best_keypoint = new MatOfKeyPoint();
            LinkedList<DMatch> best_matches = new LinkedList<>();
            for(int n = 0; n < good_indexes.length; n++) {
                MatOfDMatch matches = new MatOfDMatch();
                matcher.match(descriptors, localDescriptors.get(good_indexes[n]), matches);
                LinkedList<DMatch> matchesList = new LinkedList<>(matches.toList());
                LinkedList<DMatch> good_matches = new LinkedList<>();

                for (int i = 0; i < descriptors.rows(); i++) {
                    if (matchesList.get(i).distance < this.goodMatchThreshold) {
                        good_matches.addLast(matchesList.get(i));
                    }
                }
                Log.d(Constants.TAG, "good matches: " + good_matches.size() + " at " + System.currentTimeMillis());

                if(good_matches.size() > best_matches.size()) {
                    best_keypoint = localKeyPoints.get(good_indexes[n]);
                    best_matches = good_matches;
                    best_index = good_indexes[n];
                }
            }
            Log.d(Eval, "feature matching finished: " + System.currentTimeMillis());

            MarkerGroup markerGroup = new MarkerGroup();
            if (best_matches.size() >= 15) {
                LinkedList<Point> objList = new LinkedList<>();
                LinkedList<Point> sceneList = new LinkedList<>();
                for (int i = 0; i < best_matches.size(); i++) {
                    objList.addLast(best_keypoint.toList().get(best_matches.get(i).trainIdx).pt);
                    sceneList.addLast(points.toList().get(best_matches.get(i).queryIdx).pt);
                }

                MatOfPoint2f obj = new MatOfPoint2f();
                MatOfPoint2f scene = new MatOfPoint2f();
                obj.fromList(objList);
                scene.fromList(sceneList);

                Mat Homography = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 2);

                int height = images.get(best_index).height();
                int width = images.get(best_index).width();

                MatOfPoint2f sceneCorners = new MatOfPoint2f();
                MatOfPoint2f targetCorners = new MatOfPoint2f();
                targetCorners.alloc(4);
                Point[] pointArray = new Point[4];
                pointArray[0] = new Point(0, 0);
                pointArray[1] = new Point(width, 0);
                pointArray[2] = new Point(width, height);
                pointArray[3] = new Point(0, height);
                targetCorners.fromArray(pointArray);
                Core.perspectiveTransform(targetCorners, sceneCorners, Homography);

                pointArray = sceneCorners.toArray();
                for (int j = 0; j < 4; j++)
                    pointArray[j] = new Point((pointArray[j].x + this.offset)/recoTrackRatio, pointArray[j].y/recoTrackRatio);
                sceneCorners.fromArray(pointArray);

                String Name = "localImage" + best_index;

                if(contentIDs == null || contentIDs.contains(best_index))
                    markerGroup.addMarker(new Marker(best_index, Name, new Size(width, height), sceneCorners));
            } else {
                Log.d(Constants.TAG, "good matches not enough");
            }

            this.callback.onFinish(markerGroup, frameID);

            ts = System.currentTimeMillis();
            Log.d(Eval, "matching end: " + ts.toString());
        }
    }

    public void test() {
        int featureType = 1;
        Log.d(Eval, "testing start feature " + featureType);

        int feature = 0, retrieval = 0, match = 0, pose = 0;
        Long ts0, ts1, ts2, ts3, ts4;
        switch (featureType) {
            case 0:
                detector = FeatureDetector.create(FeatureDetector.SIFT);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.SIFT);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
                detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/sift_params.yml");
                goodMatchThreshold = 200;
                break;
            case 1:
                detector = FeatureDetector.create(FeatureDetector.SURF);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.SURF);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
                detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/surf_params.yml");
                goodMatchThreshold = 0.3f;
                break;
            case 2:
                detector = FeatureDetector.create(FeatureDetector.ORB);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/orb_params.yml");
                goodMatchThreshold = 60;
                break;
            case 3:
                detector = FeatureDetector.create(FeatureDetector.FAST);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.FREAK);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/fast_params.yml");
                goodMatchThreshold = 200;
                break;
            case 4:
                detector = FeatureDetector.create(FeatureDetector.BRISK);
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.BRISK);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                //detector.read(Environment.getExternalStorageDirectory()+"/CloudAR/params/brisk_params.yml");
                goodMatchThreshold = 120;
                break;
        }

        String trainPath = Environment.getExternalStorageDirectory().toString() + "/CloudAR/ii";
        String testPath = Environment.getExternalStorageDirectory().toString() + "/CloudAR/eval2";

        File trainDir = new File(trainPath);
        File[] trainFiles = trainDir.listFiles();
        int totalFeatures = 0;
        for(int i = 0; i < trainFiles.length; i++) {
            Mat image = Highgui.imread(trainPath + "/" + trainFiles[i].getName());

            MatOfKeyPoint localKeypoint = new MatOfKeyPoint();
            Mat localDescriptor = new Mat();
            detector.detect(image, localKeypoint);
            descriptorExtractor.compute(image, localKeypoint, localDescriptor);
            totalFeatures += localDescriptor.rows();

            vl.addImage(localDescriptor.nativeObj);
            Log.d(Eval, "training image " + i + " added");
        }
        Log.d(Eval, "total feature num: " + totalFeatures);

        if(featureType < 2) {
            //vl.loadPCA(Environment.getExternalStorageDirectory() + "/CloudAR/params/", featureType);
            vl.loadGMM(Environment.getExternalStorageDirectory() + "/CloudAR/params/", featureType, false);
            vl.FVEncodeDatabaseGMMNoPCA();
        } else {
            vl.loadBMM(Environment.getExternalStorageDirectory() + "/CloudAR/params/", featureType);
            vl.FVEncodeDatabaseBMM();
        }
        Log.d(Eval, "database encoded");

        int hit = 0;
        File testDir = new File(testPath);
        File[] testFiles = testDir.listFiles();
        for(int i = 0; i < testFiles.length; i++) {
            Log.d(Eval, "testing image " + i);
            Mat image = Highgui.imread(testPath + "/" + testFiles[i].getName());

            ts0 = System.currentTimeMillis();
            MatOfKeyPoint localKeypoint = new MatOfKeyPoint();
            Mat localDescriptor = new Mat();
            detector.detect(image, localKeypoint);
            if(localKeypoint.rows() > 500) localKeypoint = new MatOfKeyPoint(localKeypoint.rowRange(0, 499));
            Log.d(Eval, "feature num: " + localKeypoint.rows());
            descriptorExtractor.compute(image, localKeypoint, localDescriptor);
            ts1 = System.currentTimeMillis();
            feature += (int)(ts1 - ts0);

            int[] good_indexes;
            if(featureType < 2)
                good_indexes = vl.matchGMMNoPCA(localDescriptor.nativeObj);
            else
                good_indexes = vl.matchBMM(localDescriptor.nativeObj);

            for(int idx = 0; idx < good_indexes.length; idx++) {
                if(good_indexes[idx] == i)
                    hit++;
            }

            ts2 = System.currentTimeMillis();
            retrieval += (int)(ts2 - ts1);

            Mat OriginalImage = Highgui.imread(trainPath + "/" + trainFiles[i].getName());
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            Mat descriptors = new Mat();
            detector.detect(OriginalImage, keypoints);
            if(keypoints.rows() > 500) keypoints = new MatOfKeyPoint(keypoints.rowRange(0, 499));
            descriptorExtractor.compute(OriginalImage, keypoints, descriptors);

            ts2 = System.currentTimeMillis();
            MatOfDMatch matches = new MatOfDMatch();
            matcher.match(localDescriptor, descriptors, matches);
            LinkedList<DMatch> matchesList = new LinkedList<>(matches.toList());
            LinkedList<DMatch> best_matches = new LinkedList<>();

            for (int ii = 0; ii < localDescriptor.rows(); ii++) {
                if (matchesList.get(ii).distance < this.goodMatchThreshold) {
                    best_matches.addLast(matchesList.get(ii));
                }
            }
            Log.d(Constants.TAG, "good matches: " + best_matches.size());

            ts3 = System.currentTimeMillis();
            match += (int)(ts3 - ts2);

            if (best_matches.size() >= 4) {
                LinkedList<Point> objList = new LinkedList<>();
                LinkedList<Point> sceneList = new LinkedList<>();
                for (int ii = 0; ii < best_matches.size(); ii++) {
                    objList.addLast(keypoints.toList().get(best_matches.get(ii).trainIdx).pt);
                    sceneList.addLast(localKeypoint.toList().get(best_matches.get(ii).queryIdx).pt);
                }

                MatOfPoint2f obj = new MatOfPoint2f();
                MatOfPoint2f scene = new MatOfPoint2f();
                obj.fromList(objList);
                scene.fromList(sceneList);

                Mat Homography = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 2);

                int height = image.height();
                int width = image.width();

                MatOfPoint2f sceneCorners = new MatOfPoint2f();
                MatOfPoint2f targetCorners = new MatOfPoint2f();
                targetCorners.alloc(4);
                Point[] pointArray = new Point[4];
                pointArray[0] = new Point(0, 0);
                pointArray[1] = new Point(width, 0);
                pointArray[2] = new Point(width, height);
                pointArray[3] = new Point(0, height);
                targetCorners.fromArray(pointArray);
                Core.perspectiveTransform(targetCorners, sceneCorners, Homography);

                pointArray = sceneCorners.toArray();
                for (int j = 0; j < 4; j++)
                    pointArray[j] = new Point((pointArray[j].x + this.offset)/recoTrackRatio, pointArray[j].y/recoTrackRatio);
                sceneCorners.fromArray(pointArray);
            } else {
                Log.d(Constants.TAG, "good matches not enough");
            }

            ts4 = System.currentTimeMillis();
            pose += (int)(ts4 - ts3);
        }
        Log.d(Eval, "good result: " + hit);
        Log.d(Eval, "time " + feature + " " + retrieval + " " + match + " " + pose);
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}
