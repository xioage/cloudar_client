package symlab.core;

import android.content.Context;

/**
 * Created by st0rm23 on 2017/2/18.
 */

public class ArManager {

    static private ArManager instance;
    private Context context;

    private ArManager(){ super(); }

    static public ArManager getInstance() {
        synchronized (ArManager.class){
            if (instance == null) instance = new ArManager();
        }
        return instance;
    }

    private void init(Context context){
        this.context = context;
    }
}
