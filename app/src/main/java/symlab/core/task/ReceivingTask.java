package symlab.core.task;

import android.util.Log;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.rajawali3d.loader.LoaderGCode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import symlab.cloudridar.Markers;
import symlab.core.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class ReceivingTask implements Runnable{

    private ByteBuffer resPacket = ByteBuffer.allocate(400);

    private byte[] res;
    private float[] floatres = new float[8];
    private Point[] pointArray = new Point[4];
    private byte[] tmp = new byte[4];
    private byte[] name = new byte[64];
    private int newMarkerNum;
    private int lastSentID;

    private DatagramChannel datagramChannel;

    public ReceivingTask(DatagramChannel datagramChannel){
        this.datagramChannel = datagramChannel;
    }

    public void updateLatestSentID(int lastSentID){
        this.lastSentID = lastSentID;
    }

    @Override
    public void run() {
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
            System.arraycopy(res, 4, tmp, 0, 4);
            newMarkerNum = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

            //Long tsLong = System.currentTimeMillis();
            //String ts_getResult = tsLong.toString();

            if (resultID <= 5) {
                Log.d(Constants.Eval, "metadata " + resultID + " received ");
            } else if (resultID == lastSentID) {
                Log.d(Constants.Eval, "res " + resultID + " received ");
                Markers markers = new Markers(newMarkerNum);

                for (int i = 0; i < newMarkerNum; i++) {
                    markers.Recs[i] = new MatOfPoint2f();
                    markers.Recs[i].alloc(4);
                    System.arraycopy(res, 8 + i * 100, tmp, 0, 4);
                    markers.IDs[i] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    for (int j = 0; j < 8; j++) {
                        System.arraycopy(res, 12 + i * 100 + j * 4, tmp, 0, 4);
                        floatres[j] = ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    }
                    for (int j = 0; j < 4; j++)
                        pointArray[j] = new Point(floatres[j * 2], floatres[j * 2 + 1]);
                    markers.Recs[i].fromArray(pointArray);

                    System.arraycopy(res, 44 + i * 100, name, 0, 64);
                    String markerName = new String(name);
                    markers.Names[i] = markerName.substring(0, markerName.indexOf("."));
                }

                if (callback != null){
                    callback.onReceive(resultID, markers);
                }
            } else {
                Log.d(Constants.TAG, "discard outdate result: " + resultID);
            }
        }
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onReceive(int resultID, Markers markers);
    }
}
