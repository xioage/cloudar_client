package symlab.core.model;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wzhangal on 3/10/2017.
 */

public class MarkerGroup {
    private int Num;
    private List<Integer> IDs;
    private List<String> Names;
    private List<MatOfPoint2f> Recs;
    private List<Mat> Homographys;
    private List<Integer> TrackingPointsNums;

    public MarkerGroup() {
        this.Num = 0;
        this.IDs = new ArrayList<>();
        this.Names = new ArrayList<>();
        this.Recs = new ArrayList<>();
        this.Homographys = new ArrayList<>();
        this.TrackingPointsNums = new ArrayList<>();
    }

    public int size() {
        return this.Num;
    }

    public void addMarker(int ID, String Name, MatOfPoint2f Rec) {
        this.IDs.add(ID);
        this.Names.add(Name);
        this.Recs.add(Rec);
        this.Homographys.add(new Mat());
        this.TrackingPointsNums.add(0);
        this.Num++;
    }

    public void removeMarkerByID (int ID) {
        int index = IDs.indexOf(ID);
        this.IDs.remove(index);
        this.Names.remove(index);
        this.Recs.remove(index);
        this.Homographys.remove(index);
        this.TrackingPointsNums.remove(index);
        this.Num--;
    }

    public int getID (int index) {
        return this.IDs.get(index);
    }

    public List<Integer> getIDs () {
        return this.IDs;
    }

    public MatOfPoint2f getRec (int index) {
        return this.Recs.get(index);
    }

    public Mat getHomography (int index) {
        return this.Homographys.get(index);
    }

    public int getTrackingPointsNum (int index) {
        return this.TrackingPointsNums.get(index);
    }

    public List<Integer> getTrackingPointsNums () {
        return this.TrackingPointsNums;
    }

    public boolean equals (List<Integer> IDs) {
        List<Integer> curList = new ArrayList<>(this.IDs);

        curList.removeAll(IDs);
        IDs.removeAll(this.IDs);

        if(curList.size() == 0 && IDs.size() == 0)
            return true;
        else
            return false;
    }

    public void setRecs (List<MatOfPoint2f> Recs) {
        this.Recs = Recs;
    }

    public void setTrackingPointsNum (int index, int Num) {
        this.TrackingPointsNums.set(index, Num);
    }

    public void setTrackingPointsNums (List<Integer> trackingPointsNums) {
        this.TrackingPointsNums = trackingPointsNums;
    }

    public void setHomography (int index, Mat Homography) {
        this.Homographys.set(index, Homography);
    }
}
