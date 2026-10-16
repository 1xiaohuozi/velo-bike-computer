package local.velo.bridge;
import java.util.*;

/** A bounded local road sketch. Geometry is geographic, never tied to a screen frame. */
public final class RoadContext {
 public final double lat,lon;public final long saved;public final List<double[]> edges;
 public RoadContext(double a,double b,long t,List<double[]> e){lat=a;lon=b;saved=t;edges=Collections.unmodifiableList(e);}
 public boolean covers(double a,double b){return distance(lat,lon,a,b)<350;}
 public static double distance(double a,double b,double c,double d){return Math.hypot(Math.toRadians(c-a)*6371000,Math.toRadians(d-b)*6371000*Math.cos(Math.toRadians(a)));}
 public String packet(RouteModel route,RouteModel.Fix fix,long seq){
  double[] ll=MapMath.point(route,fix.x,fix.y);if(!covers(ll[0],ll[1]))return "B,"+seq+",0\n";
  int span=fix.viewportSpan>0?fix.viewportSpan:RouteModel.span(fix);double r=Math.toRadians(fix.heading);ArrayList<double[]> pieces=new ArrayList<>();
  for(double[] e:edges){double[] a=route.local(e[0],e[1]),b=route.local(e[2],e[3]);RouteModel.addPiece(pieces,fix,span,Math.cos(r),Math.sin(r),a[0],a[1],b[0],b[1],(int)e[4],Math.hypot((a[0]+b[0])/2-fix.x,(a[1]+b[1])/2-fix.y));}
  // Prefer nearby junctions; do not join unrelated fragments when capping detail.
  pieces.sort((a,b)->Double.compare(a[5],b[5]));int n=Math.min(72,pieces.size());StringBuilder out=new StringBuilder("B,").append(seq).append(',').append(n);
  for(int i=0;i<n;i++){double[] p=pieces.get(i);for(int j=0;j<4;j++)out.append(',').append(RouteModel.pixel(p[j]));out.append(',').append((int)p[4]);}
  return out.append('\n').toString();
 }
}
