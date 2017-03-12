package symlab.core.model;

import android.util.Log;

/**
 * Created by st0rm23 on 2017/2/20.
 */

public class SerialModel<T>{
    public int id;
    public T value;

    public SerialModel(int id, T value){
        this.id = id;
        this.value = value;
    }


    public T blockingGetValue(int id){
        synchronized (this){
            int tmp = id;
            while (tmp != this.id) {
                long now = System.currentTimeMillis();
                try{
                    this.wait();
                } catch (Exception e){
                    e.printStackTrace();
                }
                Log.v("thread blocking", String.format("frame %d blocked for %dms", tmp, System.currentTimeMillis() - now));
            };
            return value;
        }
    }

    public void blockingUpdate(int id, T value){
        synchronized (this){
            int tmp = id - 1;
            while (tmp != this.id) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            };
            this.value = value;
            this.id = id;
            this.notifyAll();
        }
    }
}
