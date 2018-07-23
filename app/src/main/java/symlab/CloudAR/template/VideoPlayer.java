package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.RectangularPrism;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.util.ObjectColorPicker;

import java.io.IOException;
import java.util.Stack;

import symlab.CloudAR.Constants;
import symlab.posterApp.R;
import symlab.CloudAR.renderer.ARContent;

/**
 * Created by wzhangal on 3/9/2017.
 */

public class VideoPlayer implements ARContent{
    private Plane mBase, mButton, mTrailer;
    private Line3D mBorder, mCover;
    private MediaPlayer mMediaPlayer;
    private StreamingTexture mVideoTexture;
    private String url = "rtsp://";
    private int videoID;

    private boolean videoOn = false;
    private boolean onlineVideo = false;

    public VideoPlayer(int videoID) {
        this.videoID = videoID;
    }

    @Override
    public void init(Context context, ObjectColorPicker mPicker, float width, float height) {
        Log.d(Constants.Eval, "image border created: " + width + " x " + height);
        mBase = new Plane(0.001f, 0.001f, 1, 1);
        mBase.setPosition(0, 0, 0);
        Material baseMaterial = new Material();
        baseMaterial.enableLighting(false);
        baseMaterial.setColor(Color.WHITE);
        baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mBase.setMaterial(baseMaterial);
        mBase.setVisible(true);

        float w = width / 2.0f;
        float h = height / 2.0f;
        float d = width / 3.0f;

        mTrailer = new Plane(height * 16 / 9, height, 1, 1, Vector3.Axis.Z);
        mTrailer.setPosition(0, 0, 0.1);
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
        Material trailerMaterial = new Material();
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

        mButton = new Plane(height / 2, height / 2, 1, 1, Vector3.Axis.Z);
        mButton.setPosition(0, 0, 0.1);
        Material buttonMaterial = new Material();
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

        Stack<Vector3> points = new Stack<>();
        points.add(new Vector3(-w, -h, 0));
        points.add(new Vector3(-w, h, 0));
        points.add(new Vector3(-w, h, 0));
        points.add(new Vector3(w, h, 0));
        points.add(new Vector3(w, h, 0));
        points.add(new Vector3(w, -h, 0));
        points.add(new Vector3(w, -h, 0));
        points.add(new Vector3(-w, -h, 0));

        mBorder = new Line3D(points, 10, Color.RED);
        mBorder.setMaterial(new Material());
        mBorder.setVisible(true);
        mBase.addChild(mBorder);

        points = new Stack<>();
        points.add(new Vector3(-w, -h, 0));
        points.add(new Vector3(-w, -h, d));
        points.add(new Vector3(-w, h, d));
        points.add(new Vector3(-w, h, 0));
        points.add(new Vector3(-w, h, d));
        points.add(new Vector3(w, h, d));
        points.add(new Vector3(w, h, 0));
        points.add(new Vector3(w, h, d));
        points.add(new Vector3(w, -h, d));
        points.add(new Vector3(w, -h, 0));
        points.add(new Vector3(w, -h, d));
        points.add(new Vector3(-w, -h, d));

        mCover = new Line3D(points, 10, Color.GREEN);
        mCover.setMaterial(new Material());
        mCover.setVisible(true);
        mBase.addChild(mCover);

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
    public boolean onTouch(Object3D object) {
        if(object == mButton) {
            mButton.setVisible(false);
            mBorder.setVisible(false);
            mCover.setVisible(false);
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
            mTrailer.setVisible(false);
            mButton.setVisible(true);
            mBorder.setVisible(true);
            mCover.setVisible(true);
            videoOn = false;

            return false;
        } else {
            return false;
        }
    }

    @Override
    public void onAnnotationReceived(String annotationFile) {
    }

    @Override
    public void destroy() {
        if(mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying())
                mMediaPlayer.pause();
            mMediaPlayer.release();
        }
    }
}