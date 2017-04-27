package symlab.CloudAR.track;

import android.util.Pair;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import symlab.CloudAR.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class TrackingTask implements Runnable{

    private byte[] frameData;
    private int frameID;
    private boolean busy;

    private Mat YUVMatTrack, YUVMatScaled;

    private static SerialModel<TrackModel> serialTrackModel;

    public TrackingTask(){
        frameID = 0;
        busy = false;
        YUVMatTrack = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        serialTrackModel = new SerialModel<>(frameID, null);
    }

    public boolean isBusy(){
        return busy;
    }

    public void setFrameData(int frameID, byte[] frameData){
        this.frameData = frameData;
        this.frameID = frameID;
    }

    private Mat procGrayImg(byte[] frmdata){
        YUVMatTrack.put(0, 0, frmdata);
        Imgproc.resize(YUVMatTrack, YUVMatScaled, YUVMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
        Mat GRAYMat = new Mat(Constants.previewHeight / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        Imgproc.cvtColor(YUVMatScaled, GRAYMat, Imgproc.COLOR_YUV2GRAY_420);
        return GRAYMat;
    }

    private Pair<MatOfPoint2f, int[]> initialize(Mat GRAYMat){
        MatOfPoint initial = new MatOfPoint();
        Imgproc.goodFeaturesToTrack(GRAYMat, initial, Constants.MAX_POINTS, 0.01, 10, new Mat(), 3, false, 0.04);
        MatOfPoint2f points = new MatOfPoint2f(initial.toArray());
        Imgproc.cornerSubPix(GRAYMat, points, Constants.subPixWinSize, new org.opencv.core.Size(-1, -1), Constants.termcrit);

        int[] bitmap = new int[Constants.MAX_POINTS];
        for (int i = 0; i < points.rows(); i++) bitmap[i] = 1;
        return new Pair<>(points, bitmap);
    }

    private Pair<Pair<MatOfPoint2f, MatOfPoint2f>, int[]> getFeaturesFlow(Mat preGRAYMat, MatOfPoint2f preFeature, int[] preBitmap, Mat nowGRAYMat ){
        MatOfByte status = new MatOfByte();
        MatOfFloat err = new MatOfFloat();
        MatOfPoint2f nextFeature = new MatOfPoint2f();
        Video.calcOpticalFlowPyrLK(preGRAYMat, nowGRAYMat, preFeature, nextFeature, status, err, Constants.winSize, 3, Constants.termcrit, 0, 0.001);
        byte[] statusArray = status.toArray();
        int totalValid = 0;
        for (int x : statusArray)
            if (x != 0) totalValid++;

        int[] bitmap = preBitmap.clone();
        MatOfPoint2f refinedPrePoint = new MatOfPoint2f(new Mat(totalValid, 1, preFeature.type()));
        MatOfPoint2f refinedNextPoint = new MatOfPoint2f(new Mat(totalValid, 1, nextFeature.type()));

        int iterator = 0, refinedCount = 0;
        for (int i = 0; i< Constants.MAX_POINTS; i++){ //计算剩下的枚举器
            if (bitmap[i] != 0){ //上一帧中的还有效
                if (statusArray[iterator] != 0){ //当前帧也有效
                    refinedPrePoint.put(refinedCount, 0, preFeature.get(iterator, 0).clone());
                    refinedNextPoint.put(refinedCount, 0, nextFeature.get(iterator, 0).clone());
                    refinedCount++;
                } else { //已经消失了
                    bitmap[i] = 0;
                }
                iterator++; //下一对特征点
                if (iterator == statusArray.length) break;
            }
        }

        return new Pair<>(new Pair<>(refinedPrePoint, refinedNextPoint), bitmap);
    }

    @Override
    public void run() {
        busy = true;
        boolean needSwitch = false;
        if (callback != null) needSwitch = callback.onStart(frameID, frameData);

        Mat GRAYMat = procGrayImg(frameData);

        TrackModel newTrackModel = new TrackModel();
        TrackModel preTrackModel = serialTrackModel.blockingGetValue(frameID-1);
        newTrackModel.GRAYMat = GRAYMat;

        Pair<Pair<MatOfPoint2f, MatOfPoint2f>, int[]> optFlow = null;
        boolean canTrack = !needSwitch && (preTrackModel != null);
        if (canTrack){
            /**
             * optFlow.first.first   previous feature
             * optFlow.first.first   temporary feature
             * optFlow.second        temporary bitmap
             */
            optFlow = getFeaturesFlow(preTrackModel.GRAYMat, preTrackModel.features, preTrackModel.bitmap, GRAYMat);
            int count = optFlow.first.first.rows();
            //if (count < 40) Log.v(Constants.TAG, "tracking points left: " + count);
            if (count > Constants.SWITCH_THRESHOLD) {
                newTrackModel.features = optFlow.first.second;
                newTrackModel.bitmap = optFlow.second;
                if (callback != null)
                    callback.onFinish(frameID, optFlow.first.first, new MatOfPoint2f(newTrackModel.features.clone()), newTrackModel.bitmap.clone());
                canTrack = true;
            } else canTrack = false;
        }

        if (!canTrack){
            Pair<MatOfPoint2f, int[]> result = initialize(GRAYMat);
            newTrackModel.features = result.first;
            newTrackModel.bitmap = result.second;

            if (callback != null){
                if (optFlow != null && optFlow.first.first.rows() > Constants.TRACKING_THRESHOLD) { //optFlow can be used
                    callback.onFinish(frameID, optFlow.first.first, optFlow.first.second, optFlow.second);
                    callback.onPreSwitch(frameID, new MatOfPoint2f(newTrackModel.features.clone()), newTrackModel.bitmap.clone(), needSwitch);
                } else  {//optFlow can not be used
                    callback.onPreSwitch(frameID, new MatOfPoint2f(newTrackModel.features.clone()), newTrackModel.bitmap.clone(), needSwitch);
                    callback.onFinish(frameID, null, new MatOfPoint2f(newTrackModel.features.clone()), newTrackModel.bitmap.clone());
                }
            }
        }
        serialTrackModel.blockingUpdate(frameID, newTrackModel);
        busy = false;
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        boolean onStart(int frameID, byte[] frameData);
        void onPreSwitch(int frameID, MatOfPoint2f switchFeatures, int[] bitmap, boolean isTriggered);
        void onFinish(int frameID, MatOfPoint2f preFeature, MatOfPoint2f nowFeatures, int[] bitmap);
    }

    private static class TrackModel{
        protected Mat GRAYMat;
        protected int[] bitmap;
        protected MatOfPoint2f features;
    }

}
