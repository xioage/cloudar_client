package symlab.CloudAR.template;

import android.content.Context;
import android.graphics.Color;
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
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.util.ObjectColorPicker;

import java.io.IOException;
import java.util.Stack;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.renderer.ARContent;
import symlab.cloudar.R;

/**
 * Created by wzhangal on 3/9/2017.
 */

public class DownloadedVideoPlayer implements ARContent{
    private Plane mBase, mButton, mTrailer;
    private Line3D mBorder, mCover;
    private Object3D mSphere;
    private MediaPlayer mMediaPlayer;
    private StreamingTexture mVideoTexture;
    private String annotationContent;

    private boolean videoOn = false;

    public DownloadedVideoPlayer() {
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
        mMediaPlayer = new MediaPlayer();
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
        mButton.setVisible(false);
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

        if(this.annotationContent == null) {
            try {
                Material material = new Material();
                material.addTexture(new Texture("earthColors",
                        R.drawable.earthtruecolor_nasa_big));
                material.setColorInfluence(0);
                SpecularMethod.Phong phongMethod = new SpecularMethod.Phong();
                phongMethod.setShininess(100);
                material.setDiffuseMethod(new DiffuseMethod.Lambert());
                material.setSpecularMethod(phongMethod);
                material.enableLighting(true);
                mSphere = new Sphere(d / 2, 24, 24);
                mSphere.setMaterial(material);
                mSphere.setPosition(0, 0, d / 2);
                mSphere.setVisible(true);
                mBase.addChild(mSphere);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }
        } else {
            mButton.setVisible(true);
            try {
                mMediaPlayer.setDataSource(Environment.getExternalStorageDirectory().getPath() + this.annotationContent);
                mMediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mPicker.registerObject(mTrailer);
        mPicker.registerObject(mButton);
    }

    @Override
    public Object3D getObject() {
        return this.mBase;
    }

    @Override
    public void updateTexture() {
        if(mSphere != null)
            mSphere.rotate(Vector3.Axis.Y, -1.0);
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
    public void moveObject(float x, float y) {}

    @Override
    public void scaleObject(float scaleFactor) {}

    @Override
    public void setObjectStatus(float[] status) {}

    @Override
    public float[] serializeStatus() {return null;}

    @Override
    public void onAnnotationReceived(String annotationFile) {
        if(this.mBase == null) {
            this.annotationContent = annotationFile;
        } else if(this.annotationContent == null) {
            this.annotationContent = annotationFile;

            mBase.removeChild(mSphere);
            mSphere = null;
            mButton.setVisible(true);

            try {
                mMediaPlayer.setDataSource(Environment.getExternalStorageDirectory().getPath() + annotationFile);
                mMediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        if(mMediaPlayer != null) {
            if(mMediaPlayer.isPlaying())
                mMediaPlayer.pause();
            mMediaPlayer.release();
        }
        this.annotationContent = null;
    }
}