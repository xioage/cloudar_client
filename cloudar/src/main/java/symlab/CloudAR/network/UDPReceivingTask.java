package symlab.CloudAR.network;

import android.util.Log;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Set;

import symlab.CloudAR.definition.Marker;
import symlab.CloudAR.definition.MarkerGroup;
import symlab.CloudAR.definition.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class UDPReceivingTask implements ReceivingTask {

    private ByteBuffer resPacket = ByteBuffer.allocate(Constants.RES_SIZE);

    private byte[] res;
    private float[] floatres = new float[8];
    private Point[] pointArray = new Point[4];
    private byte[] tmp = new byte[4];
    private byte[] name = new byte[56];
    private int newMarkerNum;
    private int lastSentID;
    private int recoTrackRatio = Constants.scale / Constants.recoScale;
    private int offset;
    private int timeoutCounter = Constants.TIMEOUT + 1;

    private DatagramChannel datagramChannel;
    private Set<Integer> contentIDs;
    private Callback callback;

    public UDPReceivingTask(DatagramChannel datagramChannel, Set<Integer> contentIDs){
        this.datagramChannel = datagramChannel;
        this.contentIDs = contentIDs;
    }

    @Override
    public void updateLatestSentID(int lastSentID, int offset){
        this.lastSentID = lastSentID;
        this.offset = offset / Constants.recoScale;
        this.timeoutCounter = 0;
    }

    @Override
    public void run() {
        this.timeoutCounter++;
        if (this.timeoutCounter >= Constants.TIMEOUT) {
            if(this.timeoutCounter == Constants.TIMEOUT)
                this.callback.onTimeout();
            return;
        }

        resPacket.clear();
        try {
            if (datagramChannel.receive(resPacket) != null) {
                res = resPacket.array();
                Log.v(Constants.TAG, "something received");
            } else {
                res = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (res != null) {
            System.arraycopy(res, 0, tmp, 0, 4);
            int resultID = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
            System.arraycopy(res, 8, tmp, 0, 4);
            newMarkerNum = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

            if (resultID <= 5) {
                Log.d(Constants.Eval, "metadata " + resultID + " received ");
            } else if (resultID == lastSentID) {
                Log.d(Constants.Eval, "" + newMarkerNum + " res " + resultID + " received ");
                MarkerGroup markerGroup = new MarkerGroup();

                for (int i = 0; i < newMarkerNum; i++) {
                    MatOfPoint2f Rec = new MatOfPoint2f();
                    Rec.alloc(4);
                    System.arraycopy(res, 12 + i * 100, tmp, 0, 4);
                    int ID = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    System.arraycopy(res, 16 + i * 100, tmp, 0, 4);
                    int height = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    System.arraycopy(res, 20 + i * 100, tmp, 0, 4);
                    int width = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    for (int j = 0; j < 8; j++) {
                        System.arraycopy(res, 24 + i * 100 + j * 4, tmp, 0, 4);
                        floatres[j] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    }
                    for (int j = 0; j < 4; j++)
                        pointArray[j] = new Point((floatres[j * 2] + offset)/recoTrackRatio, floatres[j * 2 + 1]/recoTrackRatio);
                    Rec.fromArray(pointArray);

                    System.arraycopy(res, 56 + i * 100, name, 0, 56);
                    String markerName = new String(name);
                    String Name = markerName;
                    //String Name = markerName.substring(0, markerName.indexOf("."));

                    if(contentIDs == null || contentIDs.contains(ID))
                        markerGroup.addMarker(new Marker(ID, Name, new Size(width/100.0, height/100.0), Rec));
                }

                if (callback != null){
                    callback.onReceive(resultID, markerGroup);
                    this.timeoutCounter = Constants.TIMEOUT + 1;
                }
            } else {
                Log.d(Constants.TAG, "discard outdate result: " + resultID);
            }
        }
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}
