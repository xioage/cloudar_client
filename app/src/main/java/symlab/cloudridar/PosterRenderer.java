package symlab.cloudridar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.AlphaMapTexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.RectangularPrism;
import org.rajawali3d.primitives.Sphere;
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
    private Cover[] covers = new Cover[3];
    private ObjectColorPicker mPicker;
    private double[][] cameraMatrixData = new double[][]{{3.9324438974006659e+002, 0, 2.3950000000000000e+002}, {0, 3.9324438974006659e+002, 1.3450000000000000e+002}, {0, 0, 1}};
    private String url = "rtsp://";
    private String TAG = "PosterRenderer";

    private boolean onlineVideo = false;
    private boolean enableVideo = false;

    public PosterRenderer(Context context, int scale) {
        super(context);
        this.scale = scale;
    }

    @Override

    protected void initScene() {
        Log.d(TAG, "initScene called()");
        PointLight light = new PointLight();
        light.setPosition(10, 0, 3);
        light.setLookAt(0, 0, -1);
        light.setPower(1f);
        getCurrentScene().addLight(light);

        mPicker = new ObjectColorPicker(this);
        mPicker.setOnObjectPickedListener(this);

        trailers[0] = new Trailer();
        trailers[0].hide();
        trailers[0].setPosition(0, 0, 0);
        getCurrentScene().addChild(trailers[0]);
        for (int i = 1; i < 2; i++) {
            trailers[i] = new Trailer();
            trailers[i].hide();
            trailers[i].setPosition(i * -16, 0, 0);
            trailers[0].addChild(trailers[i]);
        }

        for(int i = 0; i < 3; i++) {
            covers[i] = new Cover(i+3);
            covers[i].setPosition(10, 0, 0);
            covers[i].setVisible(false);
            trailers[0].addChild(covers[i]);
        }

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

    private void clear() {
        if(trailerNum > 0) {
            for (int i = 0; i < trailerNum; i++) {
                trailers[i].onPause();
                trailers[i].hide();
            }
        }
        for (int i = 0; i < 3; i++) {
            covers[i].hide();
        }
    }

    public void onPosterChanged(Markers markers) {
        clear();

        if(markers != null && markers.Num > 0) {
            trailerNum = 0;
            for (int i = 0; i < 2 && i < markers.Num; i++) {
                int curID = markers.IDs[i];
                switch(curID) {
                    case 0:
                    case 1:
                        if(enableVideo)
                            trailers[i].setTrailerContent(curID);
                        trailers[i].show();
                        trailerNum++;
                        break;
                    case 2:
                    case 3:
                    case 4:
                        covers[curID-2].show();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
        Matrix4 glViewMatrix = new Matrix4(glViewMatrixData);
        getCurrentCamera().setPosition(glViewMatrix.getTranslation().inverse());
        trailers[0].setRotation(glViewMatrix.inverse());
        for (int i = 0; i < trailerNum; i++)
            trailers[i].updateTexture();
        for (int i = 0; i < 3; i++)
            covers[i].onRender();
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
        int pickedTrailer = -1;

        for(int i = 0; i < trailerNum; i++) {
            if(trailers[i].onTouch(object)) {
                pickedTrailer = i;
            }
        }
        for(int i = 0; i < trailerNum; i++) {
            if(pickedTrailer == -1)
                trailers[i].show();
            else if(pickedTrailer != i)
                trailers[i].hide();
        }

        for(int i = 0; i < 3; i++)
            covers[i].onTouch(object);
    }

    @Override
    public void onNoObjectPicked() {
        RajLog.w("No object picked!");
    }

    public void onActivityPause() {
        clear();
    }

    public void setGlViewMatrix(double[] glViewMatrixData) {
        this.glViewMatrixData = glViewMatrixData;
    }

    private class Trailer extends Plane {
        private RectangularPrism mBoard;
        private Plane mButton, mTrailer;
        private Material baseMaterial, boardMaterial, trailerMaterial, buttonMaterial;
        private MediaPlayer mMediaPlayer;
        private StreamingTexture mVideoTexture;
        private boolean videoOn = false;

        public Trailer() {
            super(1, 1, 1, 1);
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
                        mMediaPlayer = MediaPlayer.create(getContext(), R.raw.london);
                        break;
                    case 1:
                        mMediaPlayer = MediaPlayer.create(getContext(), R.raw.batmanvsuperman);
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

        boolean onTouch(Object3D object) {
            if(!enableVideo)
                return false;

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

        public void onPause() {
            if(enableVideo && mMediaPlayer.isPlaying())
                mMediaPlayer.stop();
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

    private class Cover extends Plane {
        private Material contentMaterial;
        private Object3D mSphere;
        private String url;

        public Cover(int ID) {
            super(10, 10, 1, 1);
            contentMaterial = new Material();
            try {
                switch(ID) {
                    case 3:
                        contentMaterial.addTexture(new Texture("beers", R.drawable.beers));
                        url = "https://www.amazon.com/500-Beers-Compendium-Sellers-Publishing/dp/1416207880/ref=sr_1_1?ie=UTF8&qid=1471501468&sr=8-1&keywords=500+beers";
                        break;
                    case 4:
                        contentMaterial.addTexture(new Texture("mobydick", R.drawable.mobydick));
                        url = "https://www.amazon.com/Moby-Dick-Herman-Melville/dp/1503280780/ref=sr_1_2?ie=UTF8&qid=1471501519&sr=8-2&keywords=mobydick";
                        break;
                    case 5:
                        contentMaterial.addTexture(new Texture("whatif", R.drawable.whatif));
                        url = "https://www.amazon.com/What-If-Scientific-Hypothetical-Questions/dp/0544272994/ref=sr_1_1?ie=UTF8&qid=1471501539&sr=8-1&keywords=what+if";
                        break;
                    default:
                        break;
                }
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }
            contentMaterial.setColorInfluence(0);
            contentMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            this.setMaterial(contentMaterial);

            try {
                Material material = new Material();
                material.addTexture(new Texture("earthColors",
                        R.drawable.earthtruecolor_nasa_big));
                material.setColorInfluence(0);
                mSphere = new Sphere(0.6f, 24, 24);
                mSphere.setMaterial(material);
                mSphere.setPosition(-4, -4, 0.4f);
                this.addChild(mSphere);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }

            mPicker.registerObject(mSphere);
        }

        public void onRender() {
            mSphere.rotate(Vector3.Axis.Y, 1.0);
        }

        public int onTouch(Object3D object) {
            if(object == mSphere) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                getContext().startActivity(browserIntent);
            }
            return 0;
        }

        public void onPause() {
        }

        public void hide() {
            this.setVisible(false);
        }

        public void show() {
            this.setVisible(true);
        }
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