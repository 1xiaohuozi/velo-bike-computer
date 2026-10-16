package local.velo.bridge;
/** Web Mercator geometry, independent of Android. */
public final class MapMath {
 public static double wrap(double x){return x-Math.floor(x);}
 public static double x(double lon){return wrap((lon+180)/360);}
 public static double y(double lat){double r=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)));return Math.max(0,Math.min(1,(1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2));}
 public static double lon(double x){return wrap(x)*360-180;}
 public static double lat(double y){return Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*Math.max(0,Math.min(1,y))))));}
 public static double delta(double x,double center){double d=x-center;return d-Math.floor(d+0.5);}
 public static int tile(int x,int zoom){int n=1<<zoom;return (x%n+n)%n;}
 public static double[] point(RouteModel route,double px,double py){return new double[]{route.lat0+Math.toDegrees(py/6371000),route.lon0+Math.toDegrees(px/(6371000*route.cosLat))};}
}
