package symlab.CloudAR.network;

public interface SendingTask extends Runnable{
    void setData(int frmID, byte[] frameData, int offset);
}
