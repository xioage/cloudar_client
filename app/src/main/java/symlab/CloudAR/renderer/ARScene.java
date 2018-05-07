package symlab.CloudAR.renderer;

import android.util.SparseArray;

import org.rajawali3d.Object3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wzhangal on 3/8/2017.
 */

public class ARScene {
    private ARContent universalContent;
    private SparseArray<ARContent> contents;
    private List<float[]> lights;

    protected ARScene() {
        contents = new SparseArray<>();
        lights = new ArrayList<>();
    }

    protected void bind(int markerID, ARContent content) {
        this.contents.put(markerID, content);
    }

    protected void addUniversalContent(ARContent content) {
        this.universalContent = content;
    }

    protected void addLight(float[] light) {
        this.lights.add(light);
    }

    public ARContent getContentByID(int markerID) {
        return contents.get(markerID, universalContent);
    }

    public List<float[]> getLights() {
        return this.lights;
    }

    public void updateTexture(List<Integer> curMarkerIDs) {
        for(Integer curID : curMarkerIDs) {
            contents.get(curID, universalContent).updateTexture();
        }
    }

    public void onTouch(List<Integer> curMarkerIDs, Object3D object) {
        for(Integer curID : curMarkerIDs) {
            contents.get(curID, universalContent).onTouch(object);
        }
    }

    public void onActivityPause() {
        for(int i = 0; i < contents.size(); i++)
            contents.valueAt(i).destroy();
        if(universalContent != null)
            universalContent.destroy();
    }
}

