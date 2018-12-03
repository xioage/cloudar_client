package symlab.CloudAR.network;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

import symlab.CloudAR.definition.Constants;

import static symlab.CloudAR.definition.Constants.cropScale;
import static symlab.CloudAR.definition.Constants.recoScale;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class TCPSendingTask implements SendingTask {

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
    private SocketChannel socketChannel;
    private SocketAddress serverAddress;

    private Mat YUVMatTrans, YUVMatScaled, YUVCropped, GrayCropped;
    private int offset;

    public TCPSendingTask(SocketChannel socketChannel, SocketAddress serverAddress) {
        this.socketChannel = socketChannel;
        this.serverAddress = serverAddress;

        YUVMatTrans = new Mat(Constants.previewHeight + Constants.previewHeight / 2, Constants.previewWidth, CvType.CV_8UC1);
        YUVMatScaled = new Mat((Constants.previewHeight + Constants.previewHeight / 2) / Constants.recoScale, Constants.previewWidth / Constants.recoScale, CvType.CV_8UC1);
        GrayCropped = new Mat(Constants.previewHeight / Constants.recoScale, Constants.previewWidth / Constants.recoScale / Constants.cropScale, CvType.CV_8UC1);
    }

    @Override
    public void setData(int frmID, byte[] frameData, int offset) {
        this.frmID = frmID;
        this.frameData = frameData;
        this.offset = offset / Constants.recoScale;

        if (this.frmID <= 5) dataType = MESSAGE_META;
        else dataType = IMAGE_DETECT;
    }

    @Override
    public void run() {
        if (dataType == IMAGE_DETECT) {
            YUVMatTrans.put(0, 0, frameData);

            Imgproc.resize(YUVMatTrans, YUVMatScaled, YUVMatScaled.size(), 0, 0, Imgproc.INTER_LINEAR);
            YUVCropped = new Mat(YUVMatScaled, new Rect(this.offset, 0, Constants.previewWidth / recoScale / cropScale, Constants.previewHeight / recoScale));
            Imgproc.cvtColor(YUVCropped, GrayCropped, Imgproc.COLOR_YUV420sp2GRAY);
        }

        if (dataType == IMAGE_DETECT) {
            MatOfByte imgbuff = new MatOfByte();
            Highgui.imencode(".jpg", GrayCropped, imgbuff, Constants.Image_Params);

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
            socketChannel.write(buffer);

            if (dataType == MESSAGE_META)
                Log.d(Constants.Eval, "metadata " + frmID + " sent ");
            else
                Log.d(Constants.Eval, "frame " + frmID + " sent ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
