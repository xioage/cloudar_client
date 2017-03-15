package symlab.core.adapter;

import java.util.ArrayList;

import symlab.core.track.MarkerImpl;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public interface RenderAdapter{
    void onRender(ArrayList<MarkerImpl.Marker> markers);
}
