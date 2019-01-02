package org.vlfeat;

/**
 * Created by wzhangal on 7/5/2017.
 */

public class VLFeat {
    public VLFeat() {}
    public native String version();
    public native void addImage(long descriptors);
    public native void trainPCA();
    public native void trainGMM();
    public native void FVEncodeDatabaseGMM();
    public native int[] matchGMM(long descriptors);
    public native void trainBMM();
    public native void FVEncodeDatabaseBMM();
    public native int[] matchBMM(long descriptors);
}
