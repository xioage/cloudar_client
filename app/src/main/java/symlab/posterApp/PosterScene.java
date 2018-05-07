package symlab.posterApp;

import symlab.CloudAR.renderer.ARScene;
import symlab.CloudAR.template.DownloadedVideoPlayer;

/**
 * Created by wzhangal on 4/26/2017.
 */

public class PosterScene extends ARScene {

    public PosterScene() {
        DownloadedVideoPlayer videoPlayer = new DownloadedVideoPlayer();
        addUniversalContent(videoPlayer);

        addLight(new float[]{0, 0, 10, 0, 0, 0, 1});
    }
}