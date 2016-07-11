package symlab.cloudridar;

import org.opencv.core.MatOfPoint2f;

/**
 * Created by wzhangal on 4/14/2016.
 */
public class HistoryTrackingPoints {
    public int HistoryFrameID;
    public int HistoryTrackingID;
    public MatOfPoint2f HistoryPoints;
    public int[] historybitmap;

    public HistoryTrackingPoints(int HistoryFrameID, int HistoryTrackingID, MatOfPoint2f HistoryPoints, int[] historybitmap) {
        this.HistoryFrameID = HistoryFrameID;
        this.HistoryTrackingID = HistoryTrackingID;
        this.HistoryPoints = HistoryPoints;
        this.historybitmap = historybitmap;
    }
}
