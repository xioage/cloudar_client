package symlab.posterApp;

import symlab.CloudAR.definition.Constants;
import symlab.CloudAR.renderer.ARScene;
import symlab.CloudAR.template.DownloadedVideoPlayer;
import symlab.CloudAR.template.VideoPlayer;

/**
 * Created by wzhangal on 4/26/2017.
 */

public class PosterScene extends ARScene {

    public PosterScene() {
        if(Constants.EnableUniversalContent) {
            DownloadedVideoPlayer videoPlayer = new DownloadedVideoPlayer();
            addUniversalContent(videoPlayer);
        }
        //VideoPlayer videoPlayer = new VideoPlayer(R.raw.london);
        //bind(0, videoPlayer);
        //VideoPlayer videoPlayer1 = new VideoPlayer(R.raw.batmanvsuperman);
        //bind(1, videoPlayer1);
        VideoPlayer videoPlayer2 = new VideoPlayer(R.raw.aquaman);
        bind(0, videoPlayer2);
        VideoPlayer videoPlayer3 = new VideoPlayer(R.raw.fantastic);
        bind(1, videoPlayer3);
        VideoPlayer videoPlayer4 = new VideoPlayer(R.raw.smallfoot);
        bind(2, videoPlayer4);

        addLight(new float[]{0, 0, 1, 0, 0, 0, 1});
        addInteraction(clicking);
    }
}