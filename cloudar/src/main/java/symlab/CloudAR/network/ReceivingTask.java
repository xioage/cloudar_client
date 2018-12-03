package symlab.CloudAR.network;

import symlab.CloudAR.definition.MarkerGroup;

public interface ReceivingTask extends Runnable{
    void updateLatestSentID(int lastSentID, int offset);

    void setCallback(Callback callback);
    interface Callback {
        void onReceive(int resultID, MarkerGroup markerGroup);
        void onTimeout();
    }
}
