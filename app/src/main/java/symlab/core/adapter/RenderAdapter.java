package symlab.core.adapter;

import symlab.core.impl.MarkerGroup;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public interface RenderAdapter{
    void onMarkerChanged(MarkerGroup markerGroup);
    void onRender(double[] glViewMatrix);
}
