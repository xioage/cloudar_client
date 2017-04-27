package symlab.CloudAR.renderer;

import java.util.List;
import java.util.Map;

/**
 * Created by wzhangal on 3/8/2017.
 */

public interface ARScene {
    void bind(int markerID, ARContent content);
    Map<Integer, ARContent> getContents();
    List<float[]> getLights();
}
