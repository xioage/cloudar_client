package symlab.core.task;

import android.os.Handler;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import symlab.core.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class TransmissionTask implements Runnable {

    private final int MESSAGE_ECHO = 0;
    private final int IMAGE_DETECT = 2;

    private int dataType;
    private int frmID;
    private byte[] frmdataToSend;
    private byte[] frmid;
    private byte[] datatype;
    private byte[] frmsize;
    private byte[] packetContent;
    private int datasize;
    private byte[] frameData;
    private DatagramChannel datagramChannel;
    private SocketAddress serverAddress;

    public Mat YUVMatTrack, YUVMatTrans, YUVMatScaled;
    public Mat BGRMat, BGRMatScaled;

    public TransmissionTask() {
        YUVMatTrack = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatTrans = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC1);
        BGRMat = new Mat(Constants.previewHeight, Constants.previewWidth, CvType.CV_8UC3);
        BGRMatScaled = new Mat(Constants.previewHeight / Constants.scale, Constants.previewWidth / Constants.scale, CvType.CV_8UC3);
    }

    public void setData(int frmID, byte[] frameData, DatagramChannel datagramChannel, SocketAddress serverAddress){
        if (this.frmID <= 5) dataType = MESSAGE_ECHO;
        else dataType = IMAGE_DETECT;

        this.frmID = frmID;
        this.frameData = frameData;
        this.datagramChannel = datagramChannel;
        this.serverAddress = serverAddress;
    }

    @Override
    public void run() {
        if (dataType == IMAGE_DETECT) {
            YUVMatTrans.put(0, 0, frameData);
            Imgproc.cvtColor(YUVMatTrans, BGRMat, Imgproc.COLOR_YUV420sp2BGR);
            Imgproc.resize(BGRMat, BGRMatScaled, BGRMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
        }

        if (dataType == IMAGE_DETECT) {
            MatOfByte imgbuff = new MatOfByte();
            Highgui.imencode(".jpg", BGRMatScaled, imgbuff, Constants.Image_Params);

            datasize = (int) (imgbuff.total() * imgbuff.channels());
            frmdataToSend = new byte[datasize];

            imgbuff.get(0, 0, frmdataToSend);
        } else if (dataType == MESSAGE_ECHO) {
            datasize = 0;
            frmdataToSend = null;
        }
        packetContent = new byte[12 + datasize];

        frmid = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(frmID).array();
        datatype = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataType).array();
        frmsize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(datasize).array();
        System.arraycopy(frmid, 0, packetContent, 0, 4);
        System.arraycopy(datatype, 0, packetContent, 4, 4);
        System.arraycopy(frmsize, 0, packetContent, 8, 4);
        if (frmdataToSend != null)
            System.arraycopy(frmdataToSend, 0, packetContent, 12, datasize);

        try {
            ByteBuffer buffer = ByteBuffer.allocate(packetContent.length).put(packetContent);
            buffer.flip();
            datagramChannel.send(buffer, serverAddress);
            //Log.d(Eval, "sent size: " + packetContent.length);

            Long tsLong = System.currentTimeMillis();
            String ts_sendCameraFrame = tsLong.toString();
            if (frmID <= 5)
                Log.d(Constants.Eval, "echo " + frmID + " sent: " + ts_sendCameraFrame);
            else
                Log.d(Constants.Eval, "frame " + frmID + " sent: " + ts_sendCameraFrame);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
