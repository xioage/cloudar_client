package symlab.CloudAR.network;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import symlab.CloudAR.Constants;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class TransmissionTask implements Runnable {

    private final int MESSAGE_META = 0;
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

    public Mat YUVMatTrans, YUVMatScaled, GrayScaled;

    public TransmissionTask(DatagramChannel datagramChannel, SocketAddress serverAddress) {
        this.datagramChannel = datagramChannel;
        this.serverAddress = serverAddress;

        YUVMatTrans = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.recoScale, Constants.previewWidth / Constants.recoScale, CvType.CV_8UC1);
        GrayScaled = new Mat(Constants.previewHeight / Constants.recoScale, Constants.previewWidth / Constants.recoScale, CvType.CV_8UC1);
    }

    public void setData(int frmID, byte[] frameData){
        this.frmID = frmID;
        this.frameData = frameData;

        if (this.frmID <= 5) dataType = MESSAGE_META;
        else dataType = IMAGE_DETECT;
    }

    @Override
    public void run() {
        if (dataType == IMAGE_DETECT) {
            YUVMatTrans.put(0, 0, frameData);

            Imgproc.resize(YUVMatTrans, YUVMatScaled, YUVMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            Imgproc.cvtColor(YUVMatScaled, GrayScaled, Imgproc.COLOR_YUV420sp2GRAY);
        }

        if (dataType == IMAGE_DETECT) {
            MatOfByte imgbuff = new MatOfByte();
            Highgui.imencode(".jpg", GrayScaled, imgbuff, Constants.Image_Params);

            datasize = (int) (imgbuff.total() * imgbuff.channels());
            frmdataToSend = new byte[datasize];

            imgbuff.get(0, 0, frmdataToSend);
        } else if (dataType == MESSAGE_META) {
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

            if (dataType == MESSAGE_META)
                Log.d(Constants.Eval, "metadata " + frmID + " sent ");
            else
                Log.d(Constants.Eval, "frame " + frmID + " sent ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
