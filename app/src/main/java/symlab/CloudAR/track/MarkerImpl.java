package symlab.CloudAR.track;

import android.os.Handler;
import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.LinkedList;
import java.util.Queue;

import symlab.CloudAR.Constants;
import symlab.CloudAR.marker.Marker;
import symlab.CloudAR.marker.MarkerGroup;

/**
 * Created by st0rm23 on 2017/2/20.
 * MarkerImpl is a implementation of {@link TrackingTask.Callback},
 * MarkerImpl is used for calculating the markers position and rotation.
 *
 * User interact with this class via 3 main method, like following:
 * 1 {@link #updateMarkers(MarkerGroup, int)}: this is the positive way to input the initial marker information
 * 2 {@link Callback#onMarkersChanged(MarkerGroup)}: this is the passive way to receive result marker with calculated modelMatrix
 *
 * To use this class, firstly implement the callback,
 * then use {@link #updateMarkers(MarkerGroup, int)} to input the markers you want to track
 *
 *  all the work in MarkerImpl is done within a single thread.
 *
 * @author Lin Binghui
 * @version 1.0
 */
public class MarkerImpl implements TrackingTask.Callback{

    private MarkerGroup markerGroup;
    private boolean newMarkerFlag;
    private int trackingID;
    private int markerFrameId;
    private Handler handler;
    private Queue<HistoryTrackingPoints> historyQueue ;
    private MatOfPoint2f points1 = new MatOfPoint2f(new Mat(Constants.MAX_POINTS, 1, CvType.CV_32FC2));
    private MatOfPoint2f points2 = new MatOfPoint2f(new Mat(Constants.MAX_POINTS, 1, CvType.CV_32FC2));

    public MarkerImpl(Handler handler){
        this.handler = handler;
        historyQueue = new LinkedList<>();
    }

    /**
     * input the markers for further tracking
     *
     * @param newMarkerGroup marker information for tracking
     * @param newMarkerFrameId frame id when marker represented for.
     */
    public void updateMarkers(final MarkerGroup newMarkerGroup, final int newMarkerFrameId){
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (newMarkerGroup == null) return;
                newMarkerFlag = true;
                markerGroup = newMarkerGroup;
                markerFrameId = newMarkerFrameId;
            }
        });
    }

    private boolean isInside(Point point, Mat rect) {
        double px = point.x;
        double py = point.y;

        int pointCount = rect.rows();
        boolean isInside = true;

        double dx1 = px - rect.get(pointCount - 1, 0)[0];
        double dy1 = py - rect.get(pointCount - 1, 0)[1];
        double dx2 = px - rect.get(0, 0)[0];
        double dy2 = py - rect.get(0, 0)[1];
        double sign = (dx1 * dy2 - dx2 * dy1) > 0 ? 1 : -1;
        for (int k=1; k<pointCount; k++){
            dx1 = px - rect.get(k-1, 0)[0];
            dy1 = py - rect.get(k-1, 0)[1];
            dx2 = px - rect.get(k, 0)[0];
            dy2 = py - rect.get(k, 0)[1];
            if (sign * (dx1 * dy2 - dx2 * dy1) <= 0 ) isInside = false;
        }

        return isInside;
    }

    private MatOfPoint2f refineFeaturePoint(int[] standardBitmap, int totalValid, int[] oldBitmap, MatOfPoint2f oldFeaturePoint){
        MatOfPoint2f refinedFeature = new MatOfPoint2f(oldFeaturePoint.rowRange(0, totalValid));

        int iterator = 0, refinedCount = 0;
        for (int i=0; i<Constants.MAX_POINTS; i++){
            if (oldBitmap[i] != 0){
                if (standardBitmap[i] != 0){
                    refinedFeature.put(refinedCount, 0, oldFeaturePoint.get(iterator, 0));
                    refinedCount++;
                }
                iterator++;
            }
            oldBitmap[i] = standardBitmap[i];
        }
        return refinedFeature;
    }

    private MatOfPoint2f getHistoryFeatures(int frameId, int trackingId, int[] nowBitmap, int nowCount){
        HistoryTrackingPoints current;
        while ((current = historyQueue.poll()).HistoryFrameID != frameId);
        if (current == null || current.HistoryTrackingID != trackingId) {
            return null;
        }
        return refineFeaturePoint(nowBitmap, nowCount, current.historybitmap, current.HistoryPoints);
    }

    private void findHomography(int frameId, MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures){
        if (!Constants.EnableMultipleTracking) {
            Mat homography = Calib3d.findHomography(oldFeatures, nowFeatures, Calib3d.RANSAC, 2);
            for (int i = 0; i < markerGroup.size(); i++)
                markerGroup.getMarkerByIndex(i).setHomography(homography);
        } else {
            for (int i = 0; i < markerGroup.size(); i++) {
                Marker marker = markerGroup.getMarkerByIndex(i);

                if (!marker.isValid()) continue;
                int n = oldFeatures.rows();
                int count = 0;
                for (int j = 0; j < n; j++) {
                    if (isInside(new Point(oldFeatures.get(j, 0)), marker.getVertices())) {
                        points1.put(count, 0, oldFeatures.get(j, 0));
                        points2.put(count, 0, nowFeatures.get(j, 0));
                        count++;
                    }
                }
                MatOfPoint2f subPoints1 = new MatOfPoint2f(points1.rowRange(0, count));
                MatOfPoint2f subPoints2 = new MatOfPoint2f(points2.rowRange(0, count));
                if (count >= 4) {
                    marker.setHomography(Calib3d.findHomography(subPoints1, subPoints2, 0, 0));
                }
                else {
                    marker.setValid(false);
                }

            }
        }
    }

    private void transformBound(){
        for (int i = 0; i < markerGroup.size(); i++){
            Marker marker = markerGroup.getMarkerByIndex(i);

            if(marker.isValid()) {
                MatOfPoint2f newRecs = new MatOfPoint2f();
                Core.perspectiveTransform(marker.getVertices(), newRecs, marker.getHomography());
                marker.setVertices(newRecs);
            } else {
            //    Log.d(Constants.TAG, "null rec");  //if no homography then the recs will be null
            }
        }
    }

    private void calcModelMatrix(){
        for (int i = 0; i < markerGroup.size(); i++){
            Marker marker = markerGroup.getMarkerByIndex(i);

            if (marker.getOrigin() == null || !marker.isValid()) continue;
            Mat rvec = new Mat();
            Mat tvec = new Mat();
            Calib3d.solvePnP(marker.getOrigin(), marker.getVertices(), Constants.cameraMatrix, Constants.distCoeffs, rvec, tvec);

            Mat viewMatrix = Mat.zeros(4, 4, CvType.CV_64FC1);
            Mat rotation = new Mat();
            Calib3d.Rodrigues(rvec, rotation);

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    viewMatrix.put(row, col, rotation.get(row, col));
                }
                viewMatrix.put(row, 3, tvec.get(row, 0));
            }
            viewMatrix.put(3, 3, 1.0);
            Core.gemm(Constants.cvToGl, viewMatrix, 1, new Mat(), 0, viewMatrix, 0);

            float[] modelMatrix = new float[16];
            for (int col = 0; col < 4; col++)
                for (int row = 0; row < 4; row++)
                    modelMatrix[col * 4 + row] = (float)viewMatrix.get(row, col)[0];

            marker.setOrientation(modelMatrix);
        }
    }

    @Override
    public boolean onStart(final int frameID, final byte[] frameData) {
        return false;
    }

    @Override
    public void onPreSwitch(final int frameID, final MatOfPoint2f switchFeatures, final int[] bitmap, final boolean isTriggered) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                trackingID++;
                if (isTriggered) {
                    historyQueue.add(new HistoryTrackingPoints(frameID, trackingID, switchFeatures, bitmap));
                }
            }
        });
    }

    @Override
    public void onFinish(final int frameID, final MatOfPoint2f preFeature, final MatOfPoint2f features, final int[] bitmap) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                MatOfPoint2f oldFeatures = newMarkerFlag ? getHistoryFeatures(markerFrameId, trackingID, bitmap, features.rows()) : preFeature;

                if (oldFeatures != null && markerGroup != null) {
                    findHomography(frameID, oldFeatures, features);
                    transformBound();
                    calcModelMatrix();
                    if (callback != null) {
                        if(newMarkerFlag)
                            callback.onMarkersRecognized(markerGroup);
                        else
                            callback.onMarkersChanged(markerGroup);
                    }
                }
                newMarkerFlag = false;
            }
        });
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onMarkersRecognized(MarkerGroup markerGroup);

        /**
         * this method will be invoked each frame. And in each marker,
         * the function {@link Marker#getOrientation()} is valid to output the correct marker model matrix
         *
         * @param markerGroup current valid markers on the screen
         */
        void onMarkersChanged(MarkerGroup markerGroup);
    }
}
