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

    public void getMarkers(final MarkerCallback callback){
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
                    if (adapter != null) {
                        ArrayList arrayList = new ArrayList();
                        for (Marker marker : markers)
                            if (marker.isValid) arrayList.add(marker.clone());
                        adapter.onRender(arrayList);
                    }
                }
            }
        });
    }

    private RenderAdapter adapter;

    public void setRenderAdapter(RenderAdapter adapter){
        this.adapter = adapter;
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onSample(int frameId, byte[] frameData);
    }


    static public class Marker{
        private int id;
        private final MatOfPoint3f origin;
        protected MatOfPoint2f vertices;
        private Mat homography;
        private double[] orientation;
        private boolean isValid;

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

        public double[] getOrientation(){
            return orientation;
        }

        public boolean isValid(){
            return isValid;
        }
    }
}
