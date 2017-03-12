package symlab.core.impl;

import android.os.Handler;
import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.core.Constants;
import symlab.core.adapter.RenderAdapter;
import symlab.core.task.FrameTrackingTask;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class MarkerImpl implements FrameTrackingTask.Callback{

    private MarkerGroup markerGroup;
    private boolean newMarkerFlag;
    private int trackingID;
    private int resultID;
    private Handler handler;
    private Queue<HistoryTrackingPoints> historyQueue ;

    public MarkerImpl(Handler handler){
        this.handler = handler;
        historyQueue = new LinkedList<HistoryTrackingPoints>();
    }

    public void updateMarkers(final MarkerGroup markerGroup, final int resultID){
        if (markerGroup == null) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean markersChanged;

                if (MarkerImpl.this.markerGroup != null)
                   markersChanged = MarkerImpl.this.markerGroup.equals(markerGroup.getIDs());
                else
                   markersChanged = true;

                if (!markersChanged && MarkerImpl.this.markerGroup != null)
                    markerGroup.setTrackingPointsNums(MarkerImpl.this.markerGroup.getTrackingPointsNums());

                if (markersChanged && adapter != null) adapter.onMarkerChanged(markerGroup);

                newMarkerFlag = true;
                MarkerImpl.this.markerGroup = markerGroup;
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
            if (oldBitmap[i] != 0){ //old is still valid
                if (standardBitmap[i] != 0){ //new is still valid
                    refinedFeature.put(refinedCount, 0, oldFeaturePoint.get(iterator, 0));
                    refinedCount++;
                }
                iterator++;
            }
            oldBitmap[i] = standardBitmap[i];
        }
        return refinedFeature;
    }

    private void findHomography(MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures, int[] bitmap, MarkerGroup markerGroup){
        if (!Constants.EnableMultipleTracking) {
            Mat homography = Calib3d.findHomography(oldFeatures, nowFeatures, Calib3d.RANSAC, 3);
            for (int m = 0; m < markerGroup.size(); m++)
                markerGroup.setHomography(m, homography);
        } else {
            MatOfPoint2f[] subPoints1 = new MatOfPoint2f[markerGroup.size()];
            MatOfPoint2f[] subPoints2 = new MatOfPoint2f[markerGroup.size()];
            for (int m = 0; m < markerGroup.size(); m++) {
                subPoints1[m] = new MatOfPoint2f(new Mat(markerGroup.getTrackingPointsNum(m), 1, CvType.CV_32FC2));
                subPoints2[m] = new MatOfPoint2f(new Mat(markerGroup.getTrackingPointsNum(m), 1, CvType.CV_32FC2));
                int count = 0;
                for (int n = 0; n < nowFeatures.rows(); n++) {
                    if (bitmap[n] == markerGroup.getID(m)) {
                        subPoints1[m].put(count, 0, oldFeatures.get(n, 0));
                        subPoints2[m].put(count++, 0, nowFeatures.get(n, 0));
                    }
                }
                markerGroup.setHomography(m, Calib3d.findHomography(subPoints1[m], subPoints2[m], Calib3d.RANSAC, 3));
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
                    if (markerGroup != null){
                        if (!newMarkerFlag){//rectify markers from previous frame
                            findHomography(preFeature, features, bitmap, markerGroup);
                        } else {//rectify markers from server
                            //Log.d(TAG, "frm " + frmID + " recover from " + resID);
                            HistoryTrackingPoints current;
                            while ((current = historyQueue.poll()).HistoryFrameID != resultID);

                            int count = features.rows();
                            if (current.HistoryTrackingID == trackingID) {
                                current.HistoryPoints = refineFeaturePoint(bitmap, count, current.historybitmap, current.HistoryPoints);
                                findHomography(current.HistoryPoints, features, bitmap, markerGroup);
                            } else {
                                Log.e(Constants.TAG, "tried to recover from late result, tracking points not match");
                            }
                            newMarkerFlag = false;
                        }

                        boolean viewReady = false;

                        List<MatOfPoint2f> Recs2 = new ArrayList<>();
                        for (int i = 0; i < markerGroup.size(); i++)
                            Recs2.add(new MatOfPoint2f());

                        for (int m = 0; m < markerGroup.size(); m++) {
                            Core.perspectiveTransform(markerGroup.getRec(m), Recs2.get(m), markerGroup.getHomography(m));
                            viewReady = true;
                        }
                        markerGroup.setRecs(Recs2);

                        if(Constants.ShowGL && viewReady) {
                            MatOfPoint3f posterPoints = new MatOfPoint3f();
                            posterPoints.fromArray(Constants.posterPointsData[0]);
                            MatOfPoint2f scenePoints = new MatOfPoint2f(Recs2.get(0));

                            if(!isInsideViewport(scenePoints)) {
                                Log.d(Constants.TAG, "marker out of view, lost tracking");
                                markerGroup.removeMarkerByID(markerGroup.getID(0));
                                if (adapter != null) adapter.onMarkerChanged(markerGroup);
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
            for (int i = 0; i < markerGroup.size(); i++) {
                int Num = 0;
                for (int j = 0; j < Constants.MAX_POINTS; j++) {
                    if (isInside(new Point(feature.get(j, 0)), markerGroup.getRec(i))) {
                        bitmap[j] = markerGroup.getID(i);
                        Num++;
                    }
                }
                markerGroup.setTrackingPointsNum(i, Num);
                //Log.v(Constants.TAG, "Marker " + markers.IDs[i] + " points num: " + markers.TrackingPointsNums[i]);
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
