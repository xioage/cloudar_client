package symlab.core.impl;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.widget.Adapter;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.cloudridar.Markers;
import symlab.core.Constants;
import symlab.core.adapter.RenderAdapter;
import symlab.core.task.FrameTrackingTask;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class MarkerImpl implements FrameTrackingTask.Callback{

    private Markers markers;
    private boolean newMarkerFlag;
    private int trackingID;
    private int resultID;
    private Handler handler;
    private Queue<HistoryTrackingPoints> historyQueue ;

    public MarkerImpl(Handler handler){
        this.handler = handler;
        historyQueue = new LinkedList<HistoryTrackingPoints>();
    }

    public void updateMarkers(final Markers markers, final int resultID){
        if (markers == null || markers.Num == 0) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean markersChanged = false;
                if (MarkerImpl.this.markers != null)
                   markersChanged = !Arrays.equals(MarkerImpl.this.markers.IDs, markers.IDs);
                else
                   markersChanged = true;

                if (!markersChanged && MarkerImpl.this.markers != null)
                    markers.TrackingPointsNums = MarkerImpl.this.markers.TrackingPointsNums;

                if (markersChanged && adapter != null) adapter.onMarkerChanged(markers);

                newMarkerFlag = true;
                MarkerImpl.this.markers = markers;
                MarkerImpl.this.resultID = resultID;
            }
        });
    }

    private boolean isInside(Point point, Mat Rec) {
        int i, j;
        boolean result = false;

        for (i = 0, j = 3; i < 4; j = i++) {
            if ((Rec.get(i, 0)[1] > point.y) != (Rec.get(j, 0)[1] > point.y) &&
                    (point.x < (Rec.get(j, 0)[0] - Rec.get(i, 0)[0]) * (point.y - Rec.get(i, 0)[1]) / (Rec.get(j, 0)[1] - Rec.get(i, 0)[1]) + Rec.get(i, 0)[0])) {
                result = !result;
            }
        }
        return result;
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

    private void findHomography(MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures, int[] bitmap, Markers markers){
        if (!Constants.EnableMultipleTracking) {
            Mat homography = Calib3d.findHomography(oldFeatures, nowFeatures, Calib3d.RANSAC, 3);
            for (int m = 0; m < markers.Num; m++) markers.Homographys[m] = homography;
        } else {
            MatOfPoint2f[] subPoints1 = new MatOfPoint2f[markers.Num];
            MatOfPoint2f[] subPoints2 = new MatOfPoint2f[markers.Num];
            for (int m = 0; m < markers.Num; m++) {
                subPoints1[m] = new MatOfPoint2f(new Mat(markers.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                subPoints2[m] = new MatOfPoint2f(new Mat(markers.TrackingPointsNums[m], 1, CvType.CV_32FC2));
                int count = 0;
                for (int n = 0; n < nowFeatures.rows(); n++) {
                    if (bitmap[n] == markers.IDs[m]) {
                        subPoints1[m].put(count, 0, oldFeatures.get(n, 0));
                        subPoints2[m].put(count++, 0, nowFeatures.get(n, 0));
                    }
                }
                markers.Homographys[m] = Calib3d.findHomography(subPoints1[m], subPoints2[m], Calib3d.RANSAC, 3);
            }
        }
    }

    private boolean isInsideViewport(MatOfPoint2f points){
        float x, y;
        float[] xy = new float[2];
        x = y = 0;
        for(int i = 0; i < 4; i++)
        {
            points.get(i, 0, xy);
            x += xy[0];
            y += xy[1];
        }
        x /= 4;
        y /= 4;

        return  !(x < 0 || y < 0 || x > Constants.previewWidth/Constants.scale || y > Constants.previewHeight/Constants.scale);
    }

    private boolean shouldSample(int frameID){
        return frameID % Constants.FREQUENCY == 10;
    };

    @Override
    public void onStart(final int frameID, final byte[] frameData) {
        if (shouldSample(frameID)){
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) callback.onSample(frameID, frameData);
                }
            });
        }
    }

    @Override
    public void onFinish(final int frameID, final MatOfPoint2f preFeature, final MatOfPoint2f features, final int[] bitmap) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (preFeature == null){
                    trackingID++;
                } else {
                    if (markers != null){
                        if (!newMarkerFlag){//rectify markers from previous frame
                            findHomography(preFeature, features, bitmap, markers);
                        } else {//rectify markers from server
                            //Log.d(TAG, "frm " + frmID + " recover from " + resID);
                            HistoryTrackingPoints current;
                            while ((current = historyQueue.poll()).HistoryFrameID != resultID);

                            int count = features.rows();
                            if (current.HistoryTrackingID == trackingID) {
                                current.HistoryPoints = refineFeaturePoint(bitmap, count, current.historybitmap, current.HistoryPoints);
                                findHomography(current.HistoryPoints, features, bitmap, markers);
                            } else {
                                Log.e(Constants.TAG, "tried to recover from late result, tracking points not match");
                            }
                            newMarkerFlag = false;
                        }

                        boolean viewReady = false;

                        MatOfPoint2f[] Recs2 = new MatOfPoint2f[markers.Num];
                        for (int m = 0; m < markers.Num; m++) {
                            if(markers.Recs[m] != null && markers.Homographys[m] != null) {
                                Recs2[m] = new MatOfPoint2f();
                                Core.perspectiveTransform(markers.Recs[m], Recs2[m], markers.Homographys[m]);
                                viewReady = true;
                            } else {
                                Log.d(Constants.TAG, "null rec");
                            }
                        }
                        markers.Recs = Recs2;

                        if(Constants.ShowGL && viewReady) {
                            MatOfPoint3f posterPoints = new MatOfPoint3f();
                            posterPoints.fromArray(Constants.posterPointsData[markers.IDs[0]]);
                            MatOfPoint2f scenePoints = new MatOfPoint2f(Recs2[0].clone());

                            if(false == isInsideViewport(scenePoints)) {
                                Log.d(Constants.TAG, "marker out of view, lost tracking");
                                if (adapter != null) adapter.onMarkerChanged(null);
                            }
                            else {
                                Mat rvec = new Mat();
                                Mat tvec = new Mat();
                                Calib3d.solvePnP(posterPoints, scenePoints, Constants.cameraMatrix, Constants.distCoeffs, rvec, tvec);

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

                                double[] glViewMatrixData = new double[16];
                                for (int col = 0; col < 4; col++)
                                    for (int row = 0; row < 4; row++)
                                        glViewMatrixData[col * 4 + row] = viewMatrix.get(row, col)[0];
                                if (adapter != null) adapter.onRender(glViewMatrixData);

                                //tsLong = System.currentTimeMillis();
                                //String ts_showResult = tsLong.toString();
                                //Log.d(Eval, "frm " + frmID + " showed: " + ts_showResult);
                            }
                        }
                    }
                }

                if (shouldSample(frameID)){
                    historyQueue.add(new HistoryTrackingPoints(frameID, trackingID, features, bitmap));
                }
            }
        });
    }

    @Override
    public int[] getInitializeBitmap(MatOfPoint2f feature) {
        int[] bitmap = new int[Constants.MAX_POINTS];
        if (false == Constants.EnableMultipleTracking) {
            for (int i = 0; i <feature.rows(); i++)
                bitmap[i] = 1;
        } else {
            for (int i = 0; i < markers.Num; i++) {
                for (int j = 0; j < Constants.MAX_POINTS; j++) {
                    if (isInside(new Point(feature.get(j, 0)), markers.Recs[i])) {
                        bitmap[j] = markers.IDs[i];
                        markers.TrackingPointsNums[i]++;
                    }
                }
                Log.v(Constants.TAG, "Marker " + markers.IDs[i] + " points num: " + markers.TrackingPointsNums[i]);
            }
        }
        return bitmap;
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

}
