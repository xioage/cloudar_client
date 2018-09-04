package org.vlfeat;

/**
 * Created by wzhangal on 7/5/2017.
 */

public class VLFeat {
    public VLFeat() {}
    public native String version();
    public native void addImage(long descriptors);
    public native void trainPCA();
    public native void trainGMM(String path);
    public native void loadGMM(String path);
    public native void FVEncodeDatabase();
    public native int match(long descriptors);
}
