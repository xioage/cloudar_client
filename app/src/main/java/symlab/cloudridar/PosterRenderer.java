package symlab.cloudridar;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;
import org.rajawali3d.util.RajLog;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wzhangal on 7/27/2016.
 */
public class PosterRenderer extends Renderer implements OnObjectPickedListener {
    private int scale;
    private int trailerNum = 0;
    private double[] projectionMatrix = new double[16];
    private double[] glViewMatrixData = new double[16];
    private Trailer[] trailers = new Trailer[3];
    private ObjectColorPicker mPicker;
    private double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};
    private String url = "rtsp://r3---sn-a5mlrn7z.googlevideo.com/Cj0LENy73wIaNAmbcM1-dQ4L3BMYDSANFC1GWINXMOCoAUIASARgsfKvgpK37N1VigELbzZvTGdJZTNQWVEM/824954E4363243DE10C4E9BA586A5726C9F7FA52.44183CA7CEE3269200216C160CD19953ABFACBA7/yt6/1/video.3gp";

    private boolean onlineVideo = false;

    public PosterRenderer(Context context, int scale) {
        super(context);
        this.scale = scale;
    }

    @Override
    protected void initScene() {
        mPicker = new ObjectColorPicker(this);
        mPicker.setOnObjectPickedListener(this);

        calcProjectionMatrix();
        getCurrentCamera().setProjectionMatrix(new Matrix4(projectionMatrix));
    }

    private void calcProjectionMatrix() {
        double fx = cameraMatrixData[0][0];
        double fy = cameraMatrixData[1][1];
        double cx = cameraMatrixData[0][2];
        double cy = cameraMatrixData[1][2];
        int width = 1920 / scale;
        int height = 1080 / scale;
        int far = 1000;
        int near = 2;

        projectionMatrix[0] = 2 * fx / width;
        projectionMatrix[1] = 0;
        projectionMatrix[2] = 0;
        projectionMatrix[3] = 0;

        projectionMatrix[4] = 0;
        projectionMatrix[5] = 2 * fy / height;
        projectionMatrix[6] = 0;
        projectionMatrix[7] = 0;

        projectionMatrix[8] = 1.0 - 2 * cx / width;
        projectionMatrix[9] = 2 * cy / height - 1.0;
        projectionMatrix[10] = -(far + near) / (far - near);
        projectionMatrix[11] = -1.0;

        projectionMatrix[12] = 0;
        projectionMatrix[13] = 0;
        projectionMatrix[14] = -2.0 * far * near / (far - near);
        projectionMatrix[15] = 0;
    }

    public void onPosterChanged(Markers markers) {
        if(trailerNum == 2)
            return;
        if(trailers[0] != null) {
            for (int i = 1; i < trailerNum; i++) {
                trailers[0].removeChild(trailers[i]);
                trailers[i].onPause();
                trailers[i].destroy();
            }
            getCurrentScene().removeChild(trailers[0]);
            trailers[0].onPause();
            trailers[0].destroy();
        }

        if(markers.Num > 0) {
            trailers[0] = new Trailer(markers.Names[0]);
            trailers[0].setPosition(0, 0, 0);
            getCurrentScene().addChild(trailers[0]);

            trailerNum = 1;
            for (int i = 1; i < 3 && i < markers.Num; i++) {
                trailers[i] = new Trailer(markers.Names[i]);
                trailers[i].setPosition(i * -16, 0, 0);
                trailers[0].addChild(trailers[i]);
                trailerNum++;
            }
        }
    }

    @Override
    public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
        if(trailers[0] != null) {
            Matrix4 glViewMatrix = new Matrix4(glViewMatrixData);
            getCurrentCamera().setPosition(glViewMatrix.getTranslation().inverse());
            trailers[0].setRotation(glViewMatrix.inverse());
            for (int i = 0; i < trailerNum; i++)
                trailers[i].updateTexture();
        }
    }

    @Override
    public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
        super.onRenderSurfaceCreated(config, gl, width, height);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
    }

    public void getObjectAt(float x, float y) {
        mPicker.getObjectAt(x, y);
    }

    public void onObjectPicked(Object3D object) {
        for(int i = 0; i < trailerNum; i++)
            trailers[i].onTouch(object);
    }

    @Override
    public void onNoObjectPicked() {
        RajLog.w("No object picked!");
    }

    public void onActivityPause() {
        for(int i = 0; i < trailerNum; i++)
            trailers[i].onPause();
    }

    public void setGlViewMatrix(double[] glViewMatrixData) {
        this.glViewMatrixData = glViewMatrixData;
    }

    private class Trailer extends Plane {
        private Plane mButton, mTrailer;
        private Material baseMaterial, buttonMaterial, trailerMaterial;
        private MediaPlayer mMediaPlayer;
        private StreamingTexture mVideoTexture;
        private boolean playVideo = false;

        public Trailer(String movieName) {
            super(1, 1, 1, 1);
            baseMaterial = new Material();
            baseMaterial.enableLighting(false);
            baseMaterial.setColor(Color.RED);
            baseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            this.setMaterial(baseMaterial);

            mButton = new Plane(6, 4, 1, 1, Vector3.Axis.Z);
            mButton.setPosition(0, 0, 0.2);
            buttonMaterial = new Material();
            try {
                buttonMaterial.addTexture(new Texture("youtube_button",
                        R.drawable.youtube_play_button));
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }
            buttonMaterial.setColorInfluence(0);
            mButton.setMaterial(buttonMaterial);
            this.addChild(mButton);

            mTrailer = new Plane(16, 9, 1, 1, Vector3.Axis.Z);
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
                if(movieName.contains("London"))
                    mMediaPlayer = MediaPlayer.create(getContext(), R.raw.london);
                else if(movieName.contains("Batman"))
                    mMediaPlayer = MediaPlayer.create(getContext(), R.raw.batmanvsuperman);
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

            mPicker.registerObject(mButton);
            mPicker.registerObject(mTrailer);
        }

        public void updateTexture() {
            if(playVideo)
                mVideoTexture.update();
        }

        public void onTouch(Object3D object) {
            if(object == mButton) {
                mButton.setVisible(false);
                mTrailer.setVisible(true);
                if(onlineVideo) {
                    try {
                        mMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mMediaPlayer.start();
                playVideo = true;
            } else if(object == mTrailer){
                mMediaPlayer.pause();
                mTrailer.setVisible(false);
                mButton.setVisible(true);
                playVideo = false;
            }
        }

        public void onPause() {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
    }
}
