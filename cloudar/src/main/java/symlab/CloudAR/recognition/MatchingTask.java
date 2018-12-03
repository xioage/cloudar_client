package symlab.CloudAR.recognition;

import symlab.CloudAR.definition.MarkerGroup;

public interface MatchingTask extends Runnable{
    void setData(int frameID, byte[] frameData, int offset);

    void setCallback(Callback callback);
    interface Callback {
        void onFinish(MarkerGroup markerGroup, int frameID);
    }
}
