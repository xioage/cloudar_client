package com.rzheng.mobistream;

import org.opencv.core.Mat;

/**
 * Created by wzhangal on 4/14/2016.
 */
public class Markers {
    public int Num;
    public int[] IDs;
    public String[] Names;
    public Mat[] Recs;
    public Mat[] Homographys;
    public int[] TrackingPointsNums;

    public Markers(int Num) {
        this.Num = Num;
        this.IDs = new int[Num];
        this.Names = new String[Num];
        this.Recs = new Mat[Num];
        this.Homographys = new Mat[Num];
        this.TrackingPointsNums = new int[Num];
    }
}
