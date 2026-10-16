package local.velo.bridge;
/** Course-up display heading. No magnetometer: hold at low speed or poor bearing. */
public final class HeadingFilter {
  private boolean initialized=false;private double heading;private long updated;
  public void reset(){initialized=false;}
  public static double normalize(double a){a%=360;return a<0?a+360:a;}
  public static double difference(double target,double current){return (normalize(target-current)+540)%360-180;}
  public double update(double fallback,Double course,boolean moving,long now){
    if(!initialized){heading=normalize(course==null?fallback:course);updated=now;initialized=true;return heading;}
    if(!moving || course==null){updated=now;return heading;}
    double alpha=1-Math.exp(-Math.max(0,now-updated)/650.0);
    heading=normalize(heading+difference(course,heading)*alpha);updated=now;return heading;
  }
}
