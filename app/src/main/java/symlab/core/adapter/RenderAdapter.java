package symlab.core.adapter;

import symlab.cloudridar.Markers;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public interface RenderAdapter{
    void onMarkerChanged(Markers markers);
    void onRender(double[] glViewMatrix);
}
