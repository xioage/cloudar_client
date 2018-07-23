package symlab.posterApp;

import symlab.CloudAR.renderer.ARScene;
import symlab.CloudAR.template.DownloadedVideoPlayer;
import symlab.CloudAR.template.VideoPlayer;

/**
 * Created by wzhangal on 4/26/2017.
 */

public class PosterScene extends ARScene {

    public PosterScene() {
        //DownloadedVideoPlayer videoPlayer = new DownloadedVideoPlayer();
        //addUniversalContent(videoPlayer);
        VideoPlayer videoPlayer = new VideoPlayer(R.raw.london);
        bind(0, videoPlayer);
        VideoPlayer videoPlayer1 = new VideoPlayer(R.raw.batmanvsuperman);
        bind(1, videoPlayer1);

        addLight(new float[]{0, 0, 10, 0, 0, 0, 1});
    }
}