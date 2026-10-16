package local.velo.bridge;
import java.util.*;
/** Travel course from GPS velocity or a sufficiently long, accurate positional baseline. */
public final class TravelDirection {
 static final class Sample {final double x,y,accuracy;final long time;Sample(double a,double b,double c,long t){x=a;y=b;accuracy=c;time=t;}}
 final ArrayDeque<Sample> history=new ArrayDeque<>();long latest=-1;
 public static final class Result {public final Double course;public final double speed,baseline;public final String source;Result(Double c,double s,double b,String from){course=c;speed=s;baseline=b;source=from;}}
 Result result=new Result(null,-1,0,"unknown");
 public void reset(){history.clear();latest=-1;result=new Result(null,-1,0,"unknown");}
 public Result update(double x,double y,double accuracy,double speed,Double gpsBearing,Double bearingAccuracy,long time){
  if(time<=latest)return result;if(latest>=0&&time-latest>5000)history.clear();latest=time;
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(accuracy)||accuracy<0||accuracy>25){history.clear();return result=new Result(null,speed,0,"weak_gps");}
  while(!history.isEmpty()&&time-history.peekFirst().time>12000)history.removeFirst();
  if(speed>=0&&speed<1){history.clear();history.add(new Sample(x,y,accuracy,time));return result=new Result(null,speed,0,"stopped");}
  Double derived=null;double baseline=0,derivedSpeed=-1;
  for(Sample p:history){double seconds=(time-p.time)/1000.0,distance=Math.hypot(x-p.x,y-p.y);if(seconds>=3&&distance>=Math.max(12,(accuracy+p.accuracy)*1.2)&&distance/seconds>=1.2&&distance/seconds<=41.67){derived=HeadingFilter.normalize(Math.toDegrees(Math.atan2(x-p.x,y-p.y)));baseline=distance;derivedSpeed=distance/seconds;break;}}
  history.addLast(new Sample(x,y,accuracy,time));while(history.size()>20)history.removeFirst();double motion=speed>=0?speed:derivedSpeed;
  boolean bearingGood=gpsBearing!=null&&Double.isFinite(gpsBearing)&&motion>=1.5&&(bearingAccuracy!=null&&Double.isFinite(bearingAccuracy)&&bearingAccuracy>=0&&bearingAccuracy<=30||bearingAccuracy==null&&derived!=null&&Math.abs(HeadingFilter.difference(gpsBearing,derived))<=40);
  if(bearingGood)return result=new Result(HeadingFilter.normalize(gpsBearing),motion,baseline,"gps_velocity");
  return result=new Result(derived,motion,baseline,derived==null?"collecting_track":"gps_track");
 }
 public static String compass(double angle){return new String[]{"北","东北","东","东南","南","西南","西","西北"}[(int)Math.floor((HeadingFilter.normalize(angle)+22.5)/45)%8];}
}
