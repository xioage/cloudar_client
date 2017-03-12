package symlab.cloudridar;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

/**
 * Created by wzhangal on 4/14/2016.
 */
public class Markers {
    public int Num;
    public int[] IDs;
    public String[] Names;
    public MatOfPoint2f[] Recs;
    public Mat[] Homographys;
    public int[] TrackingPointsNums;

    public Markers(int Num) {
        this.Num = Num;
        this.IDs = new int[Num];
        this.Names = new String[Num];
        this.Recs = new MatOfPoint2f[Num];
        this.Homographys = new Mat[Num];
        this.TrackingPointsNums = new int[Num];
    }

    public Markers clone(){
        Markers newMarker = new Markers(Num);
        newMarker.IDs = IDs.clone();
        newMarker.Names = Names.clone();
        newMarker.Recs = Recs.clone();
        newMarker.Homographys = Homographys.clone();
        newMarker.TrackingPointsNums = TrackingPointsNums.clone();
        return newMarker;
    }
}
