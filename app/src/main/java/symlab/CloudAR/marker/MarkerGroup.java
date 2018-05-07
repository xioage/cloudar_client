package symlab.CloudAR.marker;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wzhangal on 3/10/2017.
 */

public class MarkerGroup {
    private List<Marker> markers;
    private List<Integer> IDs;

    public MarkerGroup() {
        markers = new ArrayList<>();
        IDs = new ArrayList<>();
    }

    public int size() {
        return this.markers.size();
    }

    public void addMarker(Marker marker) {
        this.markers.add(marker);
        this.IDs.add(marker.ID);
    }

    public Marker getMarkerByIndex(int index) {
        return markers.get(index);
    }

    public Marker getMarkerByID(int markerID) {
        int index = 0;
        for(Integer ID: IDs) {
            if(ID == markerID) break;
            index++;
        }
        return markers.get(index);
    }

    public List<Integer> getIDs() {
        return this.IDs;
    }

    public boolean equals(List<Integer> newIDs) {
        List<Integer> curList = new ArrayList<>(this.IDs);
        List<Integer> newList = new ArrayList<>(newIDs);

        curList.removeAll(newIDs);
        newList.removeAll(this.IDs);

        if(curList.size() == 0 && newList.size() == 0)
            return true;
        else
            return false;
    }
}
