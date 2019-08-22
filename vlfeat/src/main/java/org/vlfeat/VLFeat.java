package org.vlfeat;

/**
 * Created by wzhangal on 7/5/2017.
 */

public class VLFeat {
    public VLFeat() {}
    public native String version();
    public native void addImage(long descriptors);

    public native void trainPCA();
    public native void loadPCA(String path, int featureType);
    public native void trainGMM();
    public native void loadGMM(String path, int featureType, boolean isPCAEnabled);
    public native void FVEncodeDatabaseGMM();
    public native void FVEncodeDatabaseGMMNoPCA();
    public native int[] matchGMM(long descriptors);
    public native int[] matchGMMNoPCA(long descriptors);

    public native void trainBMM();
    public native void loadBMM(String path, int featureType);
    public native void FVEncodeDatabaseBMM();
    public native int[] matchBMM(long descriptors);
}
