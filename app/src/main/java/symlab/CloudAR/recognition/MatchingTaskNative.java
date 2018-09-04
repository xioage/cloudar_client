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
import org.opencv.imgproc.Imgproc;
import org.vlfeat.VLFeat;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import symlab.CloudAR.Constants;
import symlab.CloudAR.marker.Marker;
import symlab.CloudAR.marker.MarkerGroup;
import symlab.posterApp.R;

import static symlab.CloudAR.Constants.Eval;
import static symlab.CloudAR.Constants.TAG;
import static symlab.CloudAR.Constants.cropScale;
import static symlab.CloudAR.Constants.previewHeight;
import static symlab.CloudAR.Constants.previewWidth;
import static symlab.CloudAR.Constants.recoScale;

/**
 * Created by wzhangal on 4/24/2017.
 */

public class MatchingTaskNative implements MatchingTask{
    private Callback callback;
    private VLFeat vl;

    private Context context;
    private Set<Integer> contentIDs;
    private byte[] frameData;
    private int frameID;
    private int offset;

    private List<Mat> images;
    private List<MatOfKeyPoint> localKeypoints;
    private List<Mat> localDescriptors;

    private FeatureDetector detector;
    private DescriptorExtractor descriptorExtractor;
    private DescriptorMatcher matcher;

    private Mat YUV = new Mat(previewHeight + previewHeight / 2, previewWidth, CvType.CV_8UC1);
    private Mat YUVScaled = new Mat((previewHeight + previewHeight / 2) / recoScale, previewWidth / recoScale, CvType.CV_8UC1);
    private Mat GrayCropped = new Mat(previewHeight / recoScale, previewWidth / recoScale / cropScale, CvType.CV_8UC1);

    private int recoTrackRatio = Constants.scale / Constants.recoScale;

    public MatchingTaskNative(Context context, Set<Integer> contentIDs) {
        this.context = context;
        this.contentIDs = contentIDs;

        detector = FeatureDetector.create(FeatureDetector.SIFT);
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.SIFT);
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
        vl = new VLFeat();
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
            localKeypoints = new LinkedList<>();
            localDescriptors = new LinkedList<>();

            try {
                images.add(Utils.loadResource(context, R.drawable.aquaman));
                images.add(Utils.loadResource(context, R.drawable.fantastic));
                images.add(Utils.loadResource(context, R.drawable.smallfoot));
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
                localKeypoints.add(localKeypoint);
                descriptorExtractor.compute(image, localKeypoint, localDescriptor);
                localDescriptors.add(localDescriptor);
                vl.addImage(localDescriptor.nativeObj);
            }

            vl.trainPCA();
            vl.trainGMM(Environment.getExternalStorageDirectory().getPath());
            vl.FVEncodeDatabase();
        } else {
            ts = System.currentTimeMillis();
            Log.d(Eval, "matching start: " + ts.toString());

            MatOfKeyPoint points = new MatOfKeyPoint();
            Mat descriptors = new Mat();

            YUV.put(0, 0, frameData);
            Imgproc.resize(YUV, YUVScaled, YUVScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            Mat YUVCropped = new Mat(YUVScaled, new Rect(this.offset, 0, Constants.previewWidth / recoScale / cropScale, Constants.previewHeight / recoScale));
            Imgproc.cvtColor(YUVCropped, GrayCropped, Imgproc.COLOR_YUV420sp2GRAY);

            detector.detect(GrayCropped, points);
            descriptorExtractor.compute(GrayCropped, points, descriptors);

            int best_index = vl.match(descriptors.nativeObj);

            MatOfDMatch matches = new MatOfDMatch();
            matcher.match(descriptors, localDescriptors.get(best_index), matches);
            LinkedList<DMatch> matchesList = new LinkedList<>(matches.toList());
            LinkedList<DMatch> best_matches = new LinkedList<>();

            for (int i = 0; i < descriptors.rows(); i++) {
                if (matchesList.get(i).distance < 150) {
                    best_matches.addLast(matchesList.get(i));
                }
            }
            Log.d(Constants.TAG, "good matches:" + best_matches.size());

            MarkerGroup markerGroup = new MarkerGroup();
            if (best_matches.size() >= 15) {
                LinkedList<Point> objList = new LinkedList<>();
                LinkedList<Point> sceneList = new LinkedList<>();
                for (int i = 0; i < best_matches.size(); i++) {
                    objList.addLast(localKeypoints.get(best_index).toList().get(best_matches.get(i).trainIdx).pt);
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

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}
