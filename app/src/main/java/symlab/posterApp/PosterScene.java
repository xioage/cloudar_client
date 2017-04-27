package symlab.posterApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import symlab.CloudAR.renderer.ARContent;
import symlab.CloudAR.renderer.ARScene;
import symlab.CloudAR.template.VideoPlayer;

/**
 * Created by wzhangal on 4/26/2017.
 */

public class PosterScene implements ARScene {
    private Map<Integer, ARContent> contents;
    private List<float[]> lights;

    public PosterScene() {
        this.contents = new HashMap<>();
        this.lights = new ArrayList<>();

        VideoPlayer videoPlayer1 = new VideoPlayer(R.raw.london);
        VideoPlayer videoPlayer2 = new VideoPlayer(R.raw.batmanvsuperman);
        bind(0, videoPlayer1);
        bind(1, videoPlayer2);

        this.lights.add(new float[]{10, 0, 3, 0, 0, -1, 1});
    }

    @Override
    public void bind(int markerID, ARContent content) {
        this.contents.put(markerID, content);
    }

    @Override
    public Map<Integer, ARContent> getContents() {
        return this.contents;
    }

    @Override
    public List<float[]> getLights() {
        return this.lights;
    }
}
