package symlab.cloudridar;

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

/**
 * Created by wzhangal on 3/9/2017.
 */

public class Trailer extends Plane {
    private Context context;
    private RectangularPrism mBoard;
    private Plane mButton, mTrailer;
    private Material baseMaterial, boardMaterial, trailerMaterial, buttonMaterial;
    private MediaPlayer mMediaPlayer;
    private StreamingTexture mVideoTexture;
    private String url = "rtsp://";
    private boolean videoOn = false;
    private int ID = -1;

    private boolean onlineVideo = false;

    public Trailer(Context context, ObjectColorPicker mPicker) {
        super(1, 1, 1, 1);
        this.context = context;

        baseMaterial = new Material();
        baseMaterial.enableLighting(false);
        baseMaterial.setColor(Color.TRANSPARENT);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        this.setMaterial(baseMaterial);

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
        this.addChild(mBoard);

        mTrailer = new Plane((float)26.4, (float)15.2, 1, 1, Vector3.Axis.Z);
        mTrailer.setPosition(0, 0, 1.1);

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
        this.addChild(mButton);

        mPicker.registerObject(mTrailer);
        mPicker.registerObject(mButton);
    }

    public void setTrailerContent(int movieID) {
        ID = movieID;

        if (onlineVideo) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mMediaPlayer.setDataSource(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            switch(movieID) {
                case 0:
                    mMediaPlayer = MediaPlayer.create(context, R.raw.london);
                    break;
                case 1:
                    mMediaPlayer = MediaPlayer.create(context, R.raw.batmanvsuperman);
                    break;
                default:
                    break;
            }
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
        this.addChild(mTrailer);
    }

    public void updateTexture() {
        if(videoOn)
            mVideoTexture.update();
    }

    public int getID() {
        return ID;
    }

    public boolean onTouch(Object3D object) {
        if(object == mButton) {
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
        } else if(object == mTrailer){
            mMediaPlayer.pause();
            mBoard.setVisible(false);
            mTrailer.setVisible(false);
            mButton.setVisible(true);
            videoOn = false;
        }
        return videoOn;
    }

    public void onDisappear() {
        hide();

        if(mMediaPlayer != null)
            mMediaPlayer.release();
        mVideoTexture = null;
        trailerMaterial = null;
        this.removeChild(mTrailer);
        ID = -1;
    }

    public void hide() {
        mBoard.setVisible(false);
        mTrailer.setVisible(false);
        mButton.setVisible(false);
    }

    public void show() {
        mButton.setVisible(true);
    }
}


/*
textMaterial = new Material();
        textMaterial.enableLighting(true);
        textMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());

        mTextBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        mTextCanvas = new Canvas(mTextBitmap);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.RED);
        mTextPaint.setTextSize(20);
        mTextCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mTextCanvas.drawText("hello world", 10, 20, mTextPaint);

        mTextTexture = new AlphaMapTexture("textTexture", mTextBitmap);
        try {
        textMaterial.addTexture(mTextTexture);
        } catch (ATexture.TextureException e) {
        e.printStackTrace();
        }
        textMaterial.setColorInfluence(1);

        mText = new Plane(8, 10, 1, 1, Vector3.Axis.Z);
        mText.setColor(Color.RED);
        mText.setPosition(0, 0, 0.1);
        mText.setMaterial(textMaterial);
        mText.setVisible(true);
        this.addChild(mText);*/