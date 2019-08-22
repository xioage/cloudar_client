package symlab.CloudAR.manager;

import android.util.Log;

import java.nio.ByteBuffer;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.peer.PeerTask;
import symlab.CloudAR.peer.WiFiDirectTask;

public class ARManagerMultipleUser extends ARManager {
    private PeerTask taskPeer;
    private boolean enablePeer = false;

    public ARManagerMultipleUser() {
        super();
    }

    @Override
    public void start() {
        super.start();

        if(enablePeer) {
            taskPeer = new WiFiDirectTask(context, handlerNetwork);
            taskPeer.setCallback(new PeerTask.Callback() {
                @Override
                public void onPeerDiscoveryFinished(boolean peerFound) {
                    if (peerFound) {
                        Log.d(Constants.TAG, "working as client");
                        //isServer = false;
                    } else {
                        Log.d(Constants.TAG, "working as server");
                        //isServer = true;
                    }
                }

                @Override
                public void onReceive(int frameID, int dataType, int size, byte[] data) {
                    switch (dataType) {
                        case PeerTask.peerStatus:
                            //Log.d(Constants.TAG, "peer status received");
                            ByteBuffer buffer = ByteBuffer.allocate(size);
                            buffer.put(data);
                            buffer.flip();
                            float[] status = new float[size / 4];
                            for (int i = 0; i < size / 4; i++)
                                status[i] = buffer.getFloat();
                            callback.onAnnotationStatusReceived(status);
                            break;
                    }
                }
            });
            taskPeer.start();
        }
    }

    @Override
    public void stop() {
        super.stop();

        if(enablePeer) taskPeer.stop();
    }

    @Override
    public void driveFrame(byte[] frameData) {
        if(true) return;
        if(frameID == 10) {
            recognize(frameData, Constants.previewWidth / 2, Constants.previewHeight / 2);
            return;
        }
        if (taskFrame.isBusy()) return;
        taskFrame.setFrameData(++frameID, frameData, false);
        handlerFrame.post(taskFrame);

        if(isCloudBased) handlerNetwork.post(taskReceiving);
    }

    public void shareAnnotationStatus(float[] status) {
        if(status == null) return;
        ByteBuffer buffer = ByteBuffer.allocate(4 * status.length + 12);
        buffer.putInt(0);
        buffer.putInt(PeerTask.peerStatus);
        buffer.putInt(0);
        for(float value : status)
            buffer.putFloat(value);

        if(enablePeer) taskPeer.setAnnotationStatus(buffer.array());
    }
}
