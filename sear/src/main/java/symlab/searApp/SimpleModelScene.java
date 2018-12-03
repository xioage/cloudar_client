package symlab.searApp;

import symlab.CloudAR.renderer.ARScene;
import symlab.CloudAR.template.SimpleModel;

public class SimpleModelScene extends ARScene {

    SimpleModelScene() {
        SimpleModel simpleModel = new SimpleModel();
        bind(0, simpleModel);

        addLight(new float[]{0, 0, 10, 0, 0, 0, 1});
        addInteraction(dragging);
        addInteraction(scaling);
    }
}
