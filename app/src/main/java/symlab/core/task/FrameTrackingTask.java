package symlab.core.task;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Pair;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.Queue;

import symlab.cloudridar.Markers;
import symlab.core.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class FrameTrackingTask implements Runnable{

    private byte[] frameData;
    private int frameID;
    private boolean busy;

    private Mat YUVMatTrack, YUVMatTrans, YUVMatScaled;
    private Mat BGRMat, BGRMatScaled;
    private Mat PreGRAYMat;

    private static boolean EnableMultipleTracking = false;

    private class SerialModel<T>{
        public int id;
        public T value;

        public boolean update(int id, T value){
            int tmp = id - 1;
            if (tmp != this.id) return false;
            this.value = value;
            this.id = id;
            return true;
        }

        public void blockingUpdate(int id, T value){
            int tmp = id - 1;
            while (tmp != this.id);
            this.value = value;
            this.id = id;
        }

        public T getValue(){
            return this.value;
        }

        public T blockingGetValue(int id){
            int tmp = id;
            while (tmp != this.id);
            return value;
        }
    }

    private static SerialModel<int[]> serialBitmap;
    private static SerialModel<MatOfPoint2f> serialFeature;
    private static SerialModel<Mat> serialGRAYMat;

    public FrameTrackingTask(){
        YUVMatTrack = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatTrans = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        BGRMat = new Mat(Constants.previewHeight, Constants.previewWidth, CvType.CV_8UC3);
        BGRMatScaled = new Mat(Constants.previewHeight / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC3);
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

    private Pair<MatOfPoint2f, MatOfPoint2f> traceFeaturePoint(MatOfPoint2f prePoint, MatOfPoint2f nextPoint, MatOfByte status,  int[] bitmap){

        byte[] statusArray = status.toArray();
        int totalValid = 0;
        for (int x : statusArray) totalValid += x;

        MatOfPoint2f refinedPrePoint = new MatOfPoint2f(prePoint.rowRange(0, totalValid));
        MatOfPoint2f refinedNextPoint = new MatOfPoint2f(nextPoint.rowRange(0, totalValid));

        int iterator = 0, refinedCount = 0;
        for (int i = 0; i< Constants.MAX_POINTS; i++){ //计算剩下的枚举器
            if (bitmap[i] != 0){ //上一帧中的还有效
                if (statusArray[iterator] != 0){ //当前帧也有效
                    refinedPrePoint.put(refinedCount, 0, prePoint.get(iterator, 0));
                    refinedNextPoint.put(refinedCount, 0, nextPoint.get(iterator, 0));
                    refinedCount++;

                    if (EnableMultipleTracking) bitmap[i] = bitmap[i];
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


    private Pair<MatOfPoint2f, int[]> initialize(Mat GRAYMat){
        Log.v(Constants.TAG, "get tracking points");
        MatOfPoint initial = new MatOfPoint();
        Imgproc.goodFeaturesToTrack(GRAYMat, initial, Constants.MAX_POINTS, 0.01, 10, new Mat(), 3, false, 0.04);
        MatOfPoint2f points = new MatOfPoint2f(initial.toArray());
        Imgproc.cornerSubPix(GRAYMat, points, Constants.subPixWinSize, new org.opencv.core.Size(-1, -1), Constants.termcrit);

        int[] bitmap = null;
        if (callback != null) bitmap = callback.getInitializeBitmap(points);
        if (bitmap == null){
            bitmap = new int[Constants.MAX_POINTS];
            for (int i = 0; i < points.rows(); i++) bitmap[i] = 1;
        }
        return new Pair<MatOfPoint2f, int[]>(points, bitmap);
    }

    @Override
    public void run() {
        busy = true;
        if (callback != null) callback.onStart(frameID, frameData);

        Mat GRAYMat = procGrayImg(frameData);

        MatOfPoint2f resultPreFeature = null;
        MatOfPoint2f resultFeature = null;
        int[] resultBitmap = null;

        Mat preGRAYMat = serialGRAYMat.blockingGetValue(frameID-1);
        MatOfPoint2f preFeature = serialFeature.blockingGetValue(frameID-1);
        serialGRAYMat.blockingUpdate(frameID, GRAYMat);

        if (preFeature == null){
            Pair<MatOfPoint2f, int[]> result = initialize(GRAYMat);
            serialFeature.blockingUpdate(frameID, result.first);
            serialBitmap.blockingUpdate(frameID, result.second);
            resultFeature = result.first;
            resultBitmap = result.second;
        } else { //前一帧有feature
            MatOfByte status = new MatOfByte();
            MatOfFloat err = new MatOfFloat();
            MatOfPoint2f nextFeature = new MatOfPoint2f();
            Video.calcOpticalFlowPyrLK(preGRAYMat, GRAYMat, preFeature, nextFeature, status, err, Constants.winSize, 3, Constants.termcrit, 0, 0.001);

            int[] bitmap = new int[Constants.MAX_POINTS];
            int[] tmp = serialBitmap.blockingGetValue(frameID-1);
            System.arraycopy(tmp, 0, bitmap, 0, tmp.length);
            Pair<MatOfPoint2f, MatOfPoint2f> result = traceFeaturePoint(preFeature, nextFeature, status, bitmap);
            MatOfPoint2f prePoints = result.first;
            MatOfPoint2f nextPoints = result.second;
            int count = prePoints.rows();

            //tsLong = System.currentTimeMillis();
            //String ts_getPoints = tsLong.toString();
            //Log.d(Eval, "get points " + frmID + " :" + ts_getPoints);
            Log.v(Constants.TAG, "tracking points left: " + count);

            if (count <= Constants.TRACKING_THRESHOLD){
                Log.v(Constants.TAG, "too few tracking points, track again");

                Pair<MatOfPoint2f, int[]> pair = initialize(GRAYMat);
                serialFeature.blockingUpdate(frameID, pair.first);
                serialBitmap.blockingUpdate(frameID, pair.second);
                resultFeature = pair.first;
                resultBitmap = pair.second;
            } else { //feature is enough
                serialFeature.blockingUpdate(frameID, nextPoints);
                serialBitmap.blockingUpdate(frameID, bitmap);

                resultFeature = nextPoints;
                resultBitmap = bitmap;
                resultPreFeature = preFeature;
            }
        }
        if (callback != null)
            callback.onFinish(frameID, resultPreFeature, new MatOfPoint2f(resultFeature), resultBitmap.clone());

        busy = false;
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onStart(int frameID, byte[] frameData);
        void onFinish(int frameID, MatOfPoint2f preFeature, MatOfPoint2f features, int[] bitmap);
        int[] getInitializeBitmap(MatOfPoint2f feature);
    }
}
