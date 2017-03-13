package symlab.core.impl;

import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.appdatasearch.Feature;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.rajawali3d.math.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.cloudridar.Markers;
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
                /*
                boolean markersChanged = true;
                if (MarkerImpl.this.markers != null && Arrays.equals(MarkerImpl.this.markers.IDs, markers.IDs))
                   markersChanged = false;
                if (!markersChanged && MarkerImpl.this.markers != null)
                    markers.TrackingPointsNums = MarkerImpl.this.markers.TrackingPointsNums;

                if (markersChanged && adapter != null) adapter.onMarkerChanged(markers);
                origin = new ArrayList<Marker>();
                for (Marker marker : markers)
                    origin.add(marker.clone());
                    */
                newMarkerFlag = true;
                MarkerImpl.this.markers = markers;
                MarkerImpl.this.markerFrameId = markerFrameId;
            }
        });
    }

    public void getMarkers(final MarkerCallback callback){
        handler.post(new Runnable() {
            @Override
            public void run() {
                /*
                if (callback != null && markers != null)
                    callback.onResult(markers.clone());
                */
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
            Log.e(Constants.TAG, "tried to recover from late result, tracking points not match");
            return null;
        }
        return refineFeaturePoint(nowBitmap, nowCount, current.historybitmap, current.HistoryPoints);
    }

    private void findHomography(MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures, int[] bitmap, ArrayList<Marker> markers){
        if (!Constants.EnableMultipleTracking) {
            Mat homography = Calib3d.findHomography(oldFeatures, nowFeatures, Calib3d.RANSAC, 3);
            for (Marker marker : markers) marker.homography = homography;
        } else {
            for (Marker marker : markers){
                if (!marker.isValid) continue;
                int count = 0;
                int pointer = 0;
                for (int n = 0; n < Constants.MAX_POINTS; n++) {
                    if (bitmap[n] != 0){
                        if (isInside(new Point(oldFeatures.get(pointer, 0)), marker.vertices)) {
                            points1.put(count, 0, oldFeatures.get(pointer, 0));
                            points2.put(count, 0, nowFeatures.get(pointer, 0));
                            count++;
                        }
                        pointer++;
                    }
                }
                MatOfPoint2f subPoints1 = new MatOfPoint2f(points1.rowRange(0, count));
                MatOfPoint2f subPoints2 = new MatOfPoint2f(points2.rowRange(0, count));
                if (count >= 4) {
                    /*
                    Log.v("number", String.format("%d", count));
                    StringBuilder sb = new StringBuilder();
                    sb.append("old subPoints\n");
                    for (int i=0; i<subPoints1.rows(); i++){
                        sb.append(String.format("(%f, %f),\n", subPoints1.get(i, 0)[0], subPoints1.get(i, 0)[1]));
                    }
                    sb.append("new subPoints\n");
                    for (int i=0; i<subPoints1.rows(); i++){
                        sb.append(String.format("(%f, %f),\n", subPoints2.get(i, 0)[0], subPoints2.get(i, 0)[1]));
                    }
                    Log.v("marker subPoints", sb.toString());
                    */
                    marker.homography = Calib3d.findHomography(subPoints1, subPoints2, Calib3d.RANSAC, 3);
                }
                else {
                    marker.isValid = false;
                    /*
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("total %d\n", oldFeatures.rows()));
                    for (int i=0; i<oldFeatures.rows(); i++){
                        sb.append(String.format("(%f, %f),\n", oldFeatures.get(i, 0)[0],oldFeatures.get(i, 0)[1]));
                    }
                    for (Marker tmp : markers){
                        sb.append(String.format("vertices \n"));
                        for (int j=0; j<tmp.vertices.rows(); j++){
                            sb.append(String.format("(%f, %f),\n", tmp.vertices.get(j, 0)[0], tmp.vertices.get(j, 0)[1]));
                        }
                    }


                    for (Marker tmp : origin){
                        sb.append(String.format("origin \n"));
                        for (int j=0; j<tmp.vertices.rows(); j++){
                            sb.append(String.format("(%f, %f),\n", tmp.vertices.get(j, 0)[0], tmp.vertices.get(j, 0)[1]));
                        }
                    }
                    Log.v("not valid", sb.toString());
                    */

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
                Log.d(Constants.TAG, "null rec");  //if no homography then the recs will be null
            }
        }
    }

    private void calcModelMatrix(ArrayList<Marker> markers){
        for (Marker marker : markers){
            if (marker.origin == null || !marker.isValid) continue;
            Mat rvec = new Mat();
            Mat tvec = new Mat();
            Calib3d.solvePnP(marker.origin, marker.vertices, Constants.cameraMatrix, Constants.distCoeffs, rvec, tvec);

            double[] rotation = new double[3];
            double[] translation = new double[3];


            for (int i=0; i<3; i++) {
                rotation[i] = rvec.get(i, 0)[0];
                translation[i] = -tvec.get(i, 0)[0];
            }
            rotation[0] = -rotation[0];
            translation[0] = -translation[0];
            marker.orientation = new Pair<>(translation, rotation);
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
                if (isTriggered)
                    historyQueue.add(new HistoryTrackingPoints(frameID, trackingID, switchFeatures, bitmap));
            }
        });
    }

    @Override
    public void onFinish(final int frameID, final MatOfPoint2f preFeature, final MatOfPoint2f features, final int[] bitmap) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                MatOfPoint2f oldFeatures = newMarkerFlag ? getHistoryFeatures(markerFrameId, trackingID, bitmap, features.rows()) : preFeature;
                newMarkerFlag = false;
                if (oldFeatures != null && markers != null) {
                    findHomography(oldFeatures, features, bitmap, markers);
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
        private MatOfPoint2f vertices;
        private Mat homography;
        private Pair<double[], double[]> orientation;
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
                marker.orientation = new Pair<>(orientation.first.clone(), orientation.second.clone());
            return marker;
        }

        public Pair<double[], double[]> getOrientation(){
            return orientation;
        }

        public boolean isValid(){
            return isValid;
        }
    }
}
