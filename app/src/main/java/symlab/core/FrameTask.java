package symlab.core;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.rajawali3d.renderer.Renderer;

import java.util.ArrayList;
import java.util.List;

import symlab.cloudridar.HistoryTrackingPoints;
import symlab.cloudridar.Markers;
import symlab.cloudridar.PosterRenderer;

/**
 * Created by st0rm23 on 2017/2/18.
 */

//Core class for processing, with tracking and all kinds of calculation.
//Before you fully understand the logic, don't change it.
public class FrameTask extends AsyncTask<byte[], Void, Void> {


    private int frmID;
    private HistoryTrackingPoints curHistory;
    private MatOfPoint2f[] Recs2;
    private MatOfPoint2f scenePoints;
    private Mat rvec, tvec;
    private Mat rotation;

    private boolean viewReady = false;
    private boolean recoverFrame = false;
    private Long tsLong;
    private PosterRenderer mRenderer;

    public FrameTask(int frmID, PosterRenderer renderer) {
        this.frmID = frmID;
        this.mRenderer = renderer;
    }


    private Mat procGrayImg(byte[] frmdata){
        SharedMemory.YUVMatTrack.put(0, 0, frmdata);
        Imgproc.resize(SharedMemory.YUVMatTrack, SharedMemory.YUVMatScaled, SharedMemory.YUVMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
        Mat GRAYMat = new Mat(Constants.previewHeight / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        Imgproc.cvtColor(SharedMemory.YUVMatScaled, GRAYMat, Imgproc.COLOR_YUV2GRAY_420);

        return GRAYMat;
    }

    private Pair<MatOfPoint2f, MatOfPoint2f> traceFeaturePoint(Mat pre, Mat next, MatOfPoint2f prePoint, int[] bitmap){
        MatOfByte status = new MatOfByte();
        MatOfFloat err = new MatOfFloat();
        MatOfPoint2f nextPoint = new MatOfPoint2f();
        Video.calcOpticalFlowPyrLK(pre, next, prePoint, nextPoint, status, err, Constants.winSize, 3, Constants.termcrit, 0, 0.001);

        byte[] statusArray = status.toArray();
        int totalValid = 0;
        for (int x : statusArray) totalValid += x;

        MatOfPoint2f refinedPrePoint = new MatOfPoint2f(prePoint.rowRange(0, totalValid));
        MatOfPoint2f refinedNextPoint = new MatOfPoint2f(nextPoint.rowRange(0, totalValid));

        int iterator = 0, refinedCount = 0;
        for (int i=0; i<Constants.MAX_POINTS; i++){ //计算剩下的枚举器
            if (bitmap[i] != 0){ //上一帧中的还有效
                if (statusArray[iterator] != 0){ //当前帧也有效
                    refinedPrePoint.put(refinedCount, 0, prePoint.get(iterator, 0));
                    refinedNextPoint.put(refinedCount, 0, nextPoint.get(iterator, 0));
                    refinedCount++;

                    if (SharedMemory.EnableMultipleTracking) bitmap[i] = bitmap[i];
                    else bitmap[i] = 1;
                } else { //已经消失了
                    bitmap[i] = 0;
                }
                iterator++; //下一对特征点
                if (iterator == statusArray.length) break;
            }
        }

        return new Pair<MatOfPoint2f, MatOfPoint2f>(refinedPrePoint, refinedNextPoint);
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

    private void findHomography(MatOfPoint2f oldFeatures, MatOfPoint2f nowFeatures, int[] bitmap, Markers markers, boolean enableMultiple){
        if (!enableMultiple) {
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

    private void initialize(Mat GRAYMat){
        Log.v(Constants.TAG, "get tracking points");
        MatOfPoint initial = new MatOfPoint();
        Imgproc.goodFeaturesToTrack(GRAYMat, initial, Constants.MAX_POINTS, 0.01, 10, new Mat(), 3, false, 0.04);
        SharedMemory.Points1 = new MatOfPoint2f(initial.toArray());
        Imgproc.cornerSubPix(GRAYMat, SharedMemory.Points1, Constants.subPixWinSize, new org.opencv.core.Size(-1, -1), Constants.termcrit);
        SharedMemory.trackingID++;

        SharedMemory.bitmap1 = new int[Constants.MAX_POINTS];
        if (!SharedMemory.EnableMultipleTracking || !SharedMemory.PosterChanged) {
            for (int i = 0; i < SharedMemory.Points1.rows(); i++)
                SharedMemory.bitmap1[i] = 1;
        } else {
            for (int i = 0; i < SharedMemory.Markers1.Num; i++) {
                for (int j = 0; j < Constants.MAX_POINTS; j++) {
                    if (isInside(new Point(SharedMemory.Points1.get(j, 0)), SharedMemory.Markers1.Recs[i])) {
                        SharedMemory.bitmap1[j] = SharedMemory.Markers1.IDs[i];
                        SharedMemory.Markers1.TrackingPointsNums[i]++;
                    }
                }
                Log.v(Constants.TAG, "Marker " + SharedMemory.Markers1.IDs[i] + " points num: " + SharedMemory.Markers1.TrackingPointsNums[i]);
            }
        }
    }

    @Override
    protected Void doInBackground(byte[]... frmdata) {
        //Long tsLong = System.currentTimeMillis();
        //String ts_getCameraFrame = tsLong.toString();
        //Log.d(Eval, "get frame " + frmID + " data: " + ts_getCameraFrame);

        Mat GRAYMat = procGrayImg(frmdata[0]);

        //tsLong = System.currentTimeMillis();
        //String ts_resize = tsLong.toString();
        //Log.d(Eval, "resize camera frame " + frmID + ": " + ts_resize);

        if (SharedMemory.PosterRecognized) {
            SharedMemory.Markers1 = SharedMemory.Markers0;
            SharedMemory.PosterRecognized = false;
            recoverFrame = true;
        }

        if (SharedMemory.Points1 != null) { //前一帧有feature
            Pair<MatOfPoint2f, MatOfPoint2f> result = traceFeaturePoint(SharedMemory.PreGRAYMat, GRAYMat, SharedMemory.Points1, SharedMemory.bitmap1);
            MatOfPoint2f prePoints = result.first;
            MatOfPoint2f nextPoints = result.second;

            int count = prePoints.rows();

            SharedMemory.PreGRAYMat = GRAYMat;
            //tsLong = System.currentTimeMillis();
            //String ts_getPoints = tsLong.toString();
            //Log.d(Eval, "get points " + frmID + " :" + ts_getPoints);

            Log.v(Constants.TAG, "tracking points left: " + count);

            if (count > 4) {
                if (SharedMemory.Markers1 != null && SharedMemory.Markers1.Num != 0) {
                    if (recoverFrame) { //服务器返回，校准homography
                        //Log.d(TAG, "frm " + frmID + " recover from " + resID);
                        while ((curHistory = SharedMemory.HistoryQueue.poll()).HistoryFrameID != SharedMemory.resID);

                        if (curHistory.HistoryTrackingID == SharedMemory.trackingID) {
                            curHistory.HistoryPoints = refineFeaturePoint(SharedMemory.bitmap1, count, curHistory.historybitmap, curHistory.HistoryPoints);
                            boolean enableMultiple = SharedMemory.EnableMultipleTracking && !SharedMemory.PosterChanged;
                            findHomography(curHistory.HistoryPoints, nextPoints, SharedMemory.bitmap1, SharedMemory.Markers1, enableMultiple);
                        } else {
                            Log.e(Constants.TAG, "tried to recover from late result, tracking points not match");
                        }
                    } else { //本地校准homography
                        boolean enableMultiple = SharedMemory.EnableMultipleTracking && !SharedMemory.PosterChanged;
                        findHomography(prePoints, nextPoints, SharedMemory.bitmap1, SharedMemory.Markers1, enableMultiple);
                    }

                    SharedMemory.Points1 = nextPoints;

                    Recs2 = new MatOfPoint2f[SharedMemory.Markers1.Num];
                    for (int m = 0; m < SharedMemory.Markers1.Num; m++) {
                        if(SharedMemory.Markers1.Recs[m] != null && SharedMemory.Markers1.Homographys[m] != null) {
                            Recs2[m] = new MatOfPoint2f();
                            Core.perspectiveTransform(SharedMemory.Markers1.Recs[m], Recs2[m], SharedMemory.Markers1.Homographys[m]);
                            viewReady = true;
                        } else {
                            Log.d(Constants.TAG, "null rec");
                            viewReady = false;
                        }
                    }
                    SharedMemory.Markers1.Recs = Recs2;

                    if(SharedMemory.ShowGL && viewReady) {
                        SharedMemory.posterPoints.fromArray(Constants.posterPointsData[SharedMemory.Markers1.IDs[0]]);
                        scenePoints = new MatOfPoint2f(Recs2[0].clone());
                        float x, y;
                        float[] xy = new float[2];
                        x = y = 0;
                        for(int ii = 0; ii < 4; ii++)
                        {
                            scenePoints.get(ii, 0, xy);
                            x += xy[0];
                            y += xy[1];
                        }
                        x /= 4;
                        y /= 4;
                        if(x < 0 || y < 0 || x > 1920/Constants.scale || y > 1080/Constants.scale) {
                            Log.d(Constants.TAG, "marker out of view, lost tracking");
                            ((PosterRenderer) mRenderer).onPosterChanged(null);
                            SharedMemory.InitializeNeeded = true;
                        }
                        else {
                            rvec = new Mat();
                            tvec = new Mat();
                            Calib3d.solvePnP(SharedMemory.posterPoints, scenePoints, SharedMemory.cameraMatrix, SharedMemory.distCoeffs, rvec, tvec);

                            rotation = new Mat();
                            Calib3d.Rodrigues(rvec, rotation);
                            for (int row = 0; row < 3; row++) {
                                for (int col = 0; col < 3; col++) {
                                    SharedMemory.viewMatrix.put(row, col, rotation.get(row, col));
                                }
                                SharedMemory.viewMatrix.put(row, 3, tvec.get(row, 0));
                            }
                            SharedMemory.viewMatrix.put(3, 3, 1.0);
                            Core.gemm(SharedMemory.cvToGl, SharedMemory.viewMatrix, 1, new Mat(), 0, SharedMemory.viewMatrix, 0);

                            for (int col = 0; col < 4; col++)
                                for (int row = 0; row < 4; row++)
                                    SharedMemory.glViewMatrixData[col * 4 + row] = SharedMemory.viewMatrix.get(row, col)[0];

                            ((PosterRenderer) mRenderer).setGlViewMatrix(SharedMemory.glViewMatrixData);
                            //tsLong = System.currentTimeMillis();
                            //String ts_showResult = tsLong.toString();
                            //Log.d(Eval, "frm " + frmID + " showed: " + ts_showResult);
                        }

                        if(recoverFrame) {
                        }
                    }
                } else {
                    SharedMemory.Points1 = nextPoints;
                }

            } else {
                Log.v(Constants.TAG, "too few tracking points, track again");
                SharedMemory.InitializeNeeded = true;
            }
        } else {
            SharedMemory.PreGRAYMat = GRAYMat;
        }

        if (SharedMemory.InitializeNeeded) {
            initialize(GRAYMat);
            SharedMemory.InitializeNeeded = false;
        }

        if (frmID % Constants.FREQUENCY == 10)
            SharedMemory.HistoryQueue.add(new HistoryTrackingPoints(frmID, SharedMemory.trackingID, new MatOfPoint2f(SharedMemory.Points1.clone()), SharedMemory.bitmap1.clone()));

        return null;
    }

    @Override
    public void onPostExecute(Void result) {
        if (listener != null)listener.onFinish();
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

    private CallbackListener listener;

    public void setCallbackListener(CallbackListener listener) {
        this.listener = listener;
    }

    public interface CallbackListener {
        void onFinish();
    }
}
