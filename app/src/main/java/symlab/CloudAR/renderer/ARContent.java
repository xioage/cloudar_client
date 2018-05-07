package symlab.CloudAR.renderer;

import android.content.Context;

import org.rajawali3d.Object3D;
import org.rajawali3d.util.ObjectColorPicker;

/**
 * Created by wzhangal on 4/26/2017.
 */

public interface ARContent {
    void init(Context context, ObjectColorPicker mPicker, float width, float height);
    Object3D getObject();
    void updateTexture();
    boolean onTouch(Object3D object);
    void onAnnotationReceived(String annotationFile);
    void destroy();
}
