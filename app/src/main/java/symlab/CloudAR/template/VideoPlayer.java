package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.RectangularPrism;
import org.rajawali3d.util.ObjectColorPicker;

import java.io.IOException;

import symlab.posterApp.R;
import symlab.CloudAR.renderer.ARContent;

/**
 * Created by wzhangal on 3/9/2017.
 */

public class VideoPlayer implements ARContent{
    private Context context;
    private ObjectColorPicker mPicker;
    private RectangularPrism mBoard;
    private Plane mBase, mButton, mTrailer;
    private Material baseMaterial, boardMaterial, trailerMaterial, buttonMaterial;
    private MediaPlayer mMediaPlayer;
    private StreamingTexture mVideoTexture;
    private String url = "rtsp://";
    private int videoID;

    private boolean Recognized = false;
    private boolean videoOn = false;
    private boolean onlineVideo = false;

    public VideoPlayer(int videoID) {
        this.videoID = videoID;
    }

    @Override
    public void init(Context context, ObjectColorPicker mPicker) {
        this.context = context;
        this.mPicker = mPicker;

        mBase = new Plane(1, 1, 1, 1);
        mBase.setPosition(0, 0, 0);
        baseMaterial = new Material();
        baseMaterial.enableLighting(false);
        baseMaterial.setColor(Color.TRANSPARENT);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mBase.setMaterial(baseMaterial);
        mBase.setVisible(false);

        mBoard = new RectangularPrism((float)26.8, (float)15.6, (float)1);
        mBoard.setPosition(0, 0, 0.5);
        boardMaterial = new Material();
        try {
            boardMaterial.addTexture(new Texture("bluelight",
                    R.drawable.lightblue));
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        boardMaterial.setColorInfluence(0);
        mBoard.setMaterial(boardMaterial);
        mBoard.setVisible(false);
        mBase.addChild(mBoard);

        mTrailer = new Plane((float)26.4, (float)15.2, 1, 1, Vector3.Axis.Z);
        mTrailer.setPosition(0, 0, 1.1);
        if (onlineVideo) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mMediaPlayer.setDataSource(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            mMediaPlayer = MediaPlayer.create(context, this.videoID);
        }
        mMediaPlayer.setLooping(false);
        mVideoTexture = new StreamingTexture("trailer", mMediaPlayer);
        trailerMaterial = new Material();
        trailerMaterial.setColorInfluence(0);
        trailerMaterial.enableLighting(false);
        try {
            trailerMaterial.addTexture(mVideoTexture);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        mTrailer.setMaterial(trailerMaterial);
        mTrailer.setVisible(false);
        mBase.addChild(mTrailer);

        mButton = new Plane(8, 8, 1, 1, Vector3.Axis.Z);
        mButton.setPosition(0, 0, 0.2);
        buttonMaterial = new Material();
        try {
            buttonMaterial.addTexture(new Texture("youtube_button",
                    R.drawable.youtubebutton));
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        buttonMaterial.setColorInfluence(0);
        mButton.setMaterial(buttonMaterial);
        mButton.setVisible(true);
        mBase.addChild(mButton);

        mPicker.registerObject(mTrailer);
        mPicker.registerObject(mButton);
    }

    @Override
    public Object3D getObject() {
        return this.mBase;
    }

    @Override
    public void updateTexture() {
        if(videoOn)
            mVideoTexture.update();
    }

    @Override
    public void onTargetRecognized() {
        Recognized = true;
        mBase.setVisible(true);
    }

    @Override
    public void onTargetDisappear() {
        Recognized = false;
        mBase.setVisible(false);
        mBoard.setVisible(false);
        mTrailer.setVisible(false);
        mButton.setVisible(true);
        videoOn = false;
    }

    @Override
    public boolean onTouch(Object3D object) {
        if(!Recognized) {
            return false;
        } else if(object == mButton) {
            mButton.setVisible(false);
            mBoard.setVisible(true);
            mTrailer.setVisible(true);
            if(onlineVideo) {
                try {
                    mMediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mMediaPlayer.start();
            videoOn = true;

            return true;
        } else if(object == mTrailer){
            mMediaPlayer.pause();
            mBase.setVisible(false);
            mBoard.setVisible(false);
            mTrailer.setVisible(false);
            mButton.setVisible(true);
            videoOn = false;

            return false;
        } else {
            return false;
        }
    }

    @Override
    public void onSceneContentPicked(boolean isPicked) {
        if(!Recognized) {
            return;
        } else if(isPicked && !videoOn) {
            mBase.setVisible(false);
        } else {
            mBase.setVisible(true);
        }
    }

    @Override
    public void onDestruction() {
        if(mMediaPlayer != null)
            mMediaPlayer.release();
    }
}