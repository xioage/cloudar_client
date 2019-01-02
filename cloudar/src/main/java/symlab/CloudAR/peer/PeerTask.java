package symlab.CloudAR.peer;

public interface PeerTask {
    int peerEchoRequest = 0;
    int peerEchoResponse = 1;
    int peerImage = 2;
    int peerBoundary = 3;
    int peerStatus = 4;

    void start();
    void setAnnotationStatus(byte[] status);
    void stop();

    void setCallback(Callback callback);
    interface Callback {
        void onPeerDiscoveryFinished(boolean peerFound);
        void onReceive(int frameID, int dataType, int size, byte[] data);
    }
}
