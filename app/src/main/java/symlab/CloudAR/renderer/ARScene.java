package symlab.CloudAR.renderer;

import android.util.SparseArray;

import org.rajawali3d.Object3D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by wzhangal on 3/8/2017.
 */

public class ARScene {
    private ARContent universalContent;
    private SparseArray<ARContent> contents;
    private Set<Integer> contentIDs;
    private List<float[]> lights;

    protected ARScene() {
        contents = new SparseArray<>();
        contentIDs = new HashSet<>();
        lights = new ArrayList<>();
    }

    protected void bind(int markerID, ARContent content) {
        this.contents.put(markerID, content);
        this.contentIDs.add(markerID);
    }

    protected void addUniversalContent(ARContent content) {
        this.universalContent = content;
    }

    protected void addLight(float[] light) {
        this.lights.add(light);
    }

    public Set<Integer> getContentIDs() {
        if(this.universalContent != null)
            return null;
        else
            return this.contentIDs;
    }

    public ARContent getContentByID(int markerID) {
        return contents.get(markerID, universalContent);
    }

    public List<float[]> getLights() {
        return this.lights;
    }

    public void updateTexture(List<Integer> curMarkerIDs) {
        for(Integer curID : curMarkerIDs) {
            if(contents.get(curID, universalContent) != null)
                contents.get(curID, universalContent).updateTexture();
        }
    }

    public void onTouch(List<Integer> curMarkerIDs, Object3D object) {
        for(Integer curID : curMarkerIDs) {
            if (contents.get(curID, universalContent) != null)
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

