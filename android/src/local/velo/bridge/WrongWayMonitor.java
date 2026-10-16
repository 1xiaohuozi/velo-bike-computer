package local.velo.bridge;
/** Sustained opposite motion, with net displacement proof and hysteresis on recovery. */
public final class WrongWayMonitor {
 long last=-1,wrongSince=-1,rightSince=-1;int wrongSamples,rightSamples;double ax,ay,rx,ry,wrongHeading,rightHeading;
 public boolean active,evidence;public double angle,backwardMeters;
 public void reset(){last=-1;wrongSince=rightSince=-1;wrongSamples=rightSamples=0;active=evidence=false;backwardMeters=0;}
 public boolean update(double x,double y,double accuracy,double speed,Double course,double expected,long time,boolean onRoute,boolean permittedUTurn){
  if(time<=last)return active;if(last>=0&&time-last>5000)reset();last=time;evidence=false;
  if(permittedUTurn){reset();last=time;return false;}
  if(!onRoute||accuracy>18||course==null||speed<1.2){wrongSince=rightSince=-1;wrongSamples=rightSamples=0;return active;}
  angle=Math.abs(HeadingFilter.difference(course,expected));
  if(angle>=125){
    evidence=true;rightSince=-1;rightSamples=0;if(wrongSince<0){wrongSince=time;ax=x;ay=y;wrongHeading=expected;wrongSamples=0;}wrongSamples++;
    double r=Math.toRadians(wrongHeading);backwardMeters=-((x-ax)*Math.sin(r)+(y-ay)*Math.cos(r));
    if(wrongSamples>=4&&time-wrongSince>=4000&&backwardMeters>=Math.max(12,accuracy*2))active=true;
  }else if(angle<=60){
    wrongSince=-1;wrongSamples=0;if(rightSince<0){rightSince=time;rx=x;ry=y;rightHeading=expected;rightSamples=0;}rightSamples++;double r=Math.toRadians(rightHeading),forward=(x-rx)*Math.sin(r)+(y-ry)*Math.cos(r);
    if(active&&rightSamples>=3&&time-rightSince>=3000&&forward>=Math.max(8,accuracy*1.5))active=false;
  }else{wrongSince=rightSince=-1;wrongSamples=rightSamples=0;}
  return active;
 }
}
