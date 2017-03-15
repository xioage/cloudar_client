package symlab.core.impl;

import android.opengl.Matrix;
import android.os.Handler;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.core.Constants;
import symlab.core.adapter.MarkerCallback;
import symlab.core.adapter.RenderAdapter;
import symlab.core.task.TrackingTask;

/**
 * Created by st0rm23 on 2017/2/20.
 * MarkerImpl is a implementation of {@link symlab.core.task.TrackingTask.Callback},
 * MarkerImpl is used for calculating the markers position and rotation.
 *
 * User interact with this class via 3 main method, like following:
 * 1 {@link #updateMarkers(ArrayList, int)}: this is the positive way to input the initial marker information
 * 2 {@link Callback#onMarkersChanged(ArrayList)}: this is the passive way to receive result marker with calculated modelMatrix
 * 3 {@link Callback#onSample(int, byte[])}: this must be implemented to send frame data to server for recognizing
 *
 * To use this class, firstly implement the callback,
 * then use {@link #updateMarkers(ArrayList, int)} to input the markers you want to track
 *
 *  all the work in MarkerImpl is done within a single thread.
 *
 * @author Lin Binghui
 * @version 1.0
 */
public class MarkerImpl implements TrackingTask.Callback{

    private ArrayList<Marker> markers;
    private boolean newMarkerFlag;
    private int trackingID;
    private int markerFrameId;
    private Handler handler;
    private Queue<HistoryTrackingPoints> historyQueue ;
    private MatOfPoint2f points1 = new MatOfPoint2f(new Mat(Constants.MAX_POINTS, 1, CvType.CV_32FC2));
    private MatOfPoint2f points2 = new MatOfPoint2f(new Mat(Constants.MAX_POINTS, 1, CvType.CV_32FC2));

    private ArrayList<Marker> origin;

    public MarkerImpl(Handler handler){
        this.handler = handler;
        historyQueue = new LinkedList<HistoryTrackingPoints>();
    }

    /**
     * input the markers for further tracking
     *
     * @param markers marker information for tracking
     * @param markerFrameId frame id when marker represented for.
     */
    public void updateMarkers(final ArrayList<Marker> markers, final int markerFrameId){
        if (markers == null || markers.size() == 0) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                origin = new ArrayList<Marker>();
                for (Marker marker : markers)
                    origin.add(marker.clone());
                newMarkerFlag = true;
                MarkerImpl.this.markers = markers;
                MarkerImpl.this.markerFrameId = markerFrameId;
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
            if (oldBitmap[i] != 0){ //旧的还有效
                if (standardBitmap[i] != 0){ //现在还有效
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

    private void findHomography(int frameId, MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures, ArrayList<Marker> markers){
        if (!Constants.EnableMultipleTracking) {
            Mat homography = Calib3d.findHomography(oldFeatures, nowFeatures, Calib3d.RANSAC, 2);
            for (Marker marker : markers) marker.homography = homography;
        } else {
            for (Marker marker : markers){
                if (!marker.isValid) continue;
                int n = oldFeatures.rows();
                int count = 0;
                for (int i=0; i<n; i++) {
                    if (isInside(new Point(oldFeatures.get(i, 0)), marker.vertices)) {
                        points1.put(count, 0, oldFeatures.get(i, 0));
                        points2.put(count, 0, nowFeatures.get(i, 0));
                        count++;
                    }
                }
                MatOfPoint2f subPoints1 = new MatOfPoint2f(points1.rowRange(0, count));
                MatOfPoint2f subPoints2 = new MatOfPoint2f(points2.rowRange(0, count));
                if (count >= 4) {
                    marker.homography = Calib3d.findHomography(subPoints1, subPoints2, 0, 0);
                }
                else {
                    marker.isValid = false;
                }

            }
        }
    }

    private void transformBound(ArrayList<Marker> markers){
        for (Marker marker : markers){
            if(marker.isValid) {
                MatOfPoint2f newRecs = new MatOfPoint2f();
                Core.perspectiveTransform(marker.vertices, newRecs, marker.homography);
                marker.vertices = newRecs;
            } else {
            //    Log.d(Constants.TAG, "null rec");  //if no homography then the recs will be null
            }
        }
    }

    private void calcModelMatrix(ArrayList<Marker> markers){
        for (Marker marker : markers){
            if (marker.origin == null || !marker.isValid) continue;
            Mat rvec = new Mat();
            Mat tvec = new Mat();
            Calib3d.solvePnP(marker.origin, marker.vertices, Constants.cameraMatrix, Constants.distCoeffs, rvec, tvec);

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

            double[] modelMatrix = new double[16];
            for (int col = 0; col < 4; col++)
                for (int row = 0; row < 4; row++)
                    modelMatrix[col * 4 + row] = viewMatrix.get(row, col)[0];

            marker.orientation = modelMatrix;
        }
    }

    @Override
    public boolean onStart(final int frameID, final byte[] frameData) {
        if (frameID % Constants.FREQUENCY == 10){
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) callback.onSample(frameID, frameData);
                }
            });
            return true;
        }
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
               // if (newMarkerFlag) Log.v("received", String.format("received frame %d", frameID));
                newMarkerFlag = false;
                if (oldFeatures != null && markers != null) {
                    findHomography(frameID, oldFeatures, features, markers);
                    transformBound(markers);
                    calcModelMatrix(markers);
                    if (callback != null) {
                        ArrayList arrayList = new ArrayList();
                        for (Marker marker : markers)
                            if (marker.isValid) arrayList.add(marker.clone());
                        callback.onMarkersChanged(arrayList);
                    }
                }
            }
        });
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        /**
         * this method will be invoked periodically to notify user sending frameData to server.
         *
         * @param frameId current frame id
         * @param frameData current frame data
         */
        void onSample(int frameId, byte[] frameData);

        /**
         * this method will be invoked each frame. And in each marker,
         * the function {@link Marker#getOrientation()} is valid to output the correct marker model matrix
         *
         * @param markers current valid markers on the screen
         */
        void onMarkersChanged(ArrayList<Marker> markers);
    }

    /**
     * a marker for {@link MarkerImpl} to track.
     * user input the basic information of marker including id, origin vertices, screen vertices.
     * when finished calculating in {@link MarkerImpl.Callback#onMarkersChanged(ArrayList)}, user can
     * get the correct model view matrix via {@link Marker#getOrientation()}
     *
     * @author Lin Binghui
     * @version 1.0
     */
    static public class Marker{
        private int id;
        private final MatOfPoint3f origin;
        protected MatOfPoint2f vertices;
        private Mat homography;
        private double[] orientation;
        private boolean isValid;

        /**
         * create a marker for tracking position
         *
         * @param id id specified by user for distinguishing marker
         * @param origin origin vertices of this marker in real word, whose unit is meter.
         * @param vertices picture vertices of this marker on screen, whose unit is scaled pixel
         */
        public Marker(int id, MatOfPoint3f origin, MatOfPoint2f vertices){
            this.id = id;
            this.origin = origin;
            this.vertices = vertices;
            this.isValid = true;
        }

        public int getId(){
            return id;
        }

        public Marker clone(){
            Marker marker = new Marker(id, origin, new MatOfPoint2f(vertices.clone()));
            if (orientation != null)
                marker.orientation = orientation.clone();
            return marker;
        }

        /**
         * get the model matrix of model
         *
         * @return double[16] represent for 4x4 model view matrix
         */
        public double[] getOrientation(){
            return orientation;
        }

        public boolean isValid(){
            return isValid;
        }
    }
}
