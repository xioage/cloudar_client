package symlab.CloudAR.track;

import android.util.Pair;

import org.opencv.core.MatOfPoint2f;

import java.util.ArrayList;
import java.util.Hashtable;

import symlab.CloudAR.marker.Marker;

/**
 * Created by st0rm23 on 2017/3/14.
 */

public class Debuger {
    volatile static public Hashtable<Integer, Pair<MatOfPoint2f, MatOfPoint2f>> map = new Hashtable<>();
    volatile static public Hashtable<Integer, ArrayList<Marker>> mapMaker = new Hashtable<>();

    static public void saveFeature(int frameId, MatOfPoint2f old, MatOfPoint2f now){
        map.put((frameId + 100) % 100, new Pair<MatOfPoint2f, MatOfPoint2f>(new MatOfPoint2f(old.clone()), new MatOfPoint2f(now.clone())));
    }

    static public void saveMarker(int frameId, ArrayList<Marker> markers){
        mapMaker.put((frameId + 100) % 100, markers);
    }

    static public Pair<MatOfPoint2f, MatOfPoint2f> getFeature(int frameId){
        return map.get((frameId+100) % 100);
    }

    static public ArrayList<Marker> getMarkers(int frameId){
        return mapMaker.get((frameId+100) % 100);
    }

    static public String debug(String name, ArrayList<Marker> markers){
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s = [", name));
        for (Marker tmp : markers){
            sb.append(String.format("["));
            for (int j=0; j<tmp.getVertices().rows(); j++){
                sb.append(String.format("(%f, %f),", tmp.getVertices().get(j, 0)[0], tmp.getVertices().get(j, 0)[1]));
            }
            sb.append(String.format("],"));
        }
        sb.append(String.format("]"));
        return sb.toString();
    }

    static public String debug(String name, Marker marker){
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s_%s = [", name, marker.getId()));
        for (int j=0; j<marker.getVertices().rows(); j++){
            sb.append(String.format("(%f, %f),", marker.getVertices().get(j, 0)[0], marker.getVertices().get(j, 0)[1]));
        }
        sb.append(String.format("],"));
        return sb.toString();
    }

    static public String debug(String name, MatOfPoint2f points){
        if (points == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#number %d\n", points.rows()));
        sb.append(String.format("%s = [", name));
        for (int i=0; i<points.rows(); i++){
            sb.append(String.format("(%f, %f),", points.get(i, 0)[0], points.get(i, 0)[1]));
        }
        sb.append(String.format("]"));
        return sb.toString();
    }

}
