package symlab.core.model;

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

    public boolean update(int id, T value){
        int tmp = id - 1;
        if (tmp != this.id) return false;
        this.value = value;
        this.id = id;
        return true;
    }

    public void blockingUpdate(int id, T value){
        int tmp = id - 1;
        while (tmp != this.id);
        this.value = value;
        this.id = id;
    }

    public T getValue(){
        return this.value;
    }

    public T blockingGetValue(int id){
        int tmp = id;
        while (tmp != this.id);
        return value;
    }
}
