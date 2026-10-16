import local.velo.bridge.*;
public class MapCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){for(double lat:new double[]{-80,-45,0,33.55183333,80})for(double lon:new double[]{-179,-60,0,133.50755556,179}){check(Math.abs(MapMath.lat(MapMath.y(lat))-lat)<1e-8,"latitude roundtrip");check(Math.abs(MapMath.lon(MapMath.x(lon))-lon)<1e-8,"longitude roundtrip");}check(MapMath.tile(-1,3)==7,"wrapped left tile");check(MapMath.tile(8,3)==0,"wrapped right tile");check(Math.abs(MapMath.delta(.99,.01)+.02)<1e-8,"date line screen distance");check(Double.isFinite(MapMath.y(90)),"Mercator pole clamp");System.out.println(n+" map projection checks passed");}
}
