package symlab.CloudAR.peer;

public interface PeerTask {
    int peerImage = 0;
    int peerBoundary = 1;
    int peerStatus = 2;

    void start();
    void setAnnotationStatus(byte[] status);
    void stop();

    void setCallback(Callback callback);
    interface Callback {
        void onPeerDiscoveryFinished(boolean peerFound);
        void onReceive(int frameID, int dataType, int size, byte[] data);
    }
}
