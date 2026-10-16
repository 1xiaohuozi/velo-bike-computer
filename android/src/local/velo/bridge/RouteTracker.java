package local.velo.bridge;
import java.util.*;

/** Stateful matching and maneuver confirmation, in meters and monotonic milliseconds. */
public final class RouteTracker {
  public static final int FAR=0,PREPARE=1,APPROACH=2,NOW=3,PASSED=4,ARRIVED=5,OFF_ROUTE=6,CHECKING=7,WRONG_WAY=8,DIRECTION_WAIT=9,STOPPED=10;
  final RouteModel route;final List<RouteModel.Maneuver> turns=new ArrayList<>();
  double progress=-1,lastX,lastY,rejoinProgress=-1;long lastTime=-1,outSince=-1,passedUntil;int outside,inside,rejoin,turnConfirm,arrival,next,stable;boolean off;
  final WrongWayMonitor wrongWay=new WrongWayMonitor();public double expectedHeading;
  public boolean reverseEvidence(){return wrongWay.evidence;}
  public String reason="initial";
  public RouteTracker(RouteModel r){route=r;
    if(r.roadInstructions){for(RouteModel.Maneuver m:r.maneuvers)if("LRUON".indexOf(m.direction)>=0)turns.add(m);}
    else for(int i=1;i<r.x.length-1;i++){double a=Math.toDegrees(Math.atan2(r.x[i+1]-r.x[i],r.y[i+1]-r.y[i])-Math.atan2(r.x[i]-r.x[i-1],r.y[i]-r.y[i-1]));a=HeadingFilter.difference(a,0);if(Math.abs(a)>=35)turns.add(new RouteModel.Maneuver(r.along[i],Math.abs(a)>150?'U':a>0?'R':'L',"GPX bend"));}
  }
  RouteModel.Fix candidate(double px,double py,Double course,double minimum,double maximum,double expected){
    RouteModel.Fix best=null;double bestScore=Double.MAX_VALUE;
    for(int i=0;i<route.x.length-1;i++){
      if(route.along[i+1]<minimum||route.along[i]>maximum)continue;
      double t=RouteModel.project(px,py,route.x[i],route.y[i],route.x[i+1],route.y[i+1]);double s=route.along[i]+t*(route.along[i+1]-route.along[i]);if(s<minimum||s>maximum)continue;
      double error=Math.hypot(px-route.x[i]-t*(route.x[i+1]-route.x[i]),py-route.y[i]-t*(route.y[i+1]-route.y[i]));double heading=HeadingFilter.normalize(Math.toDegrees(Math.atan2(route.x[i+1]-route.x[i],route.y[i+1]-route.y[i])));
      double angle=course==null?0:Math.abs(HeadingFilter.difference(course,heading));double score=error+(angle>60?(angle-60)*0.35:0)+(progress<0?0:Math.min(25,Math.abs(s-expected)*0.12));
      if(score<bestScore){bestScore=score;best=new RouteModel.Fix();best.x=px;best.y=py;best.progress=s;best.error=error;best.heading=heading;}
    }return best;
  }
  public RouteModel.Fix update(double px,double py,double accuracy,double speed,Double course,long time){
    double dt=lastTime<0?1:Math.max(0.1,(time-lastTime)/1000.0);boolean fresh=lastTime<0||time>lastTime;boolean moving=speed>=1.0;
    if(!moving)course=null;reason="matched";
    double ahead=Math.min(250,Math.max(40,Math.max(0,speed)*Math.min(5,dt)+accuracy*2+10));
    RouteModel.Fix f=candidate(px,py,course,progress<0?0:Math.max(0,progress-200),progress<0?route.length:progress+ahead,progress<0?0:progress+Math.max(0,speed)*Math.min(5,dt));
    if(f==null){f=route.at(Math.max(0,progress));f.x=px;f.y=py;f.error=Double.MAX_VALUE;}
    // A full-route rejoin requires three consistent samples; a single jump cannot teleport progress.
    if(f.error>35&&(off||dt>5)){
      RouteModel.Fix global=candidate(px,py,course,0,route.length,progress<0?0:progress);
      if(global!=null&&global.error<=20&&(course==null||Math.abs(HeadingFilter.difference(course,global.heading))<75)){
        if(fresh){rejoin=Math.abs(global.progress-rejoinProgress)<=Math.max(35,Math.max(0,speed)*dt+accuracy*2)?rejoin+1:1;rejoinProgress=global.progress;}
        if(rejoin>=3){f=global;progress=f.progress;stable=0;reason="rejoined";rejoin=0;while(next<turns.size()&&turns.get(next).progress<f.progress-15)next++;turnConfirm=0;}
      }else rejoin=0;
    }else rejoin=0;
    double enter=Math.max(35,accuracy*1.8),leave=Math.max(18,accuracy*1.1);
    if(fresh){
      if(dt>5)stable=0;
      if(f.error<=leave)stable++;else stable=0;
      if(f.error>enter){outside++;inside=0;if(outSince<0)outSince=time;if(outside>=3&&time-outSince>=2000)off=true;}
      else if(f.error<=leave){outside=0;outSince=-1;if(++inside>=3)off=false;}
      else{outside=0;outSince=-1;inside=0;}
    }
    boolean suspect=f.error>enter||off;double matchedProgress=f.progress;expectedHeading=f.heading;
    if(!suspect&&progress>=0){
      if(speed>=0&&!moving&&dt<=5&&Math.hypot(px-lastX,py-lastY)<=Math.max(10,accuracy)){f.progress=progress;f.heading=route.at(progress).heading;reason="stationary_hold";}
      else if(f.progress<progress){f.progress=progress;reason="regression_hold";}
    }
    boolean nearU=next<turns.size()&&turns.get(next).direction=='U'&&Math.abs(turns.get(next).progress-f.progress)<30;
    boolean previouslyWrong=wrongWay.active;
    boolean wrong=wrongWay.update(px,py,accuracy,speed,course,expectedHeading,time,!suspect&&f.error<=leave&&stable>=3,nearU)&&!suspect&&accuracy<=18;
    if(previouslyWrong&&!wrongWay.active&&fresh&&!suspect){f.progress=matchedProgress;progress=matchedProgress;next=0;while(next<turns.size()&&turns.get(next).progress<progress-15)next++;turnConfirm=0;arrival=0;reason="direction_recovered";}
    f.offRoute=off;
    if(suspect||wrong){f.turn='N';f.meters=-1;f.phase=off?OFF_ROUTE:wrong?WRONG_WAY:CHECKING;reason=off?"off_route":wrong?"wrong_way":"checking_deviation";}
    else{
      if(progress<0)while(next<turns.size()&&turns.get(next).progress<f.progress-15)next++;
      if(next<turns.size()){
        RouteModel.Maneuver m=turns.get(next);double outgoing=route.at(Math.min(route.length,m.progress+12)).heading;
        boolean passed=f.progress>=m.progress+8&&(moving||speed<0)&&(course==null?f.progress>=m.progress+15:Math.abs(HeadingFilter.difference(course,outgoing))<=65);
        if(fresh){if(passed)turnConfirm++;else turnConfirm=0;}
        if(turnConfirm>=2){next++;turnConfirm=0;passedUntil=time+2000;reason="turn_confirmed";}
      }
      f.turn=next<turns.size()?turns.get(next).direction:'S';f.exit=next<turns.size()?turns.get(next).exit:0;
      f.meters=(int)Math.max(0,Math.round((next<turns.size()?turns.get(next).progress:route.length)-f.progress));
      double endDistance=Math.hypot(px-route.x[route.x.length-1],py-route.y[route.y.length-1]);
      if(fresh){if(route.length-f.progress<=12&&endDistance<=18&&accuracy<=15)arrival++;else arrival=0;}
      if(arrival>=3){f.turn='A';f.meters=0;f.phase=ARRIVED;reason="arrival_confirmed";}
      else{
        f.phase=phase(f.meters,speed,f.turn);
        if(next<turns.size()&&f.progress>turns.get(next).progress+40){f.turn='N';f.phase=CHECKING;reason="turn_unconfirmed";}
        else if(stable<3){f.turn='N';f.phase=CHECKING;reason="acquiring_route";}
        else if((accuracy>18&&f.phase>=APPROACH)||(accuracy>12&&f.phase==NOW)){f.turn='N';f.phase=CHECKING;reason="weak_accuracy_near_turn";}
        else if(time<passedUntil&&f.phase<APPROACH){f.phase=PASSED;}
      }
      if(fresh)progress=f.progress;
    }
    if(fresh){lastX=px;lastY=py;lastTime=time;}
    return f;
  }
  public static int phase(int meters,double speed,char turn){
    if(turn=='A')return ARRIVED;if("LRUO".indexOf(turn)<0)return FAR;
    double v=Math.max(0,speed);if(meters<=Math.max(15,Math.min(30,v*3)))return NOW;
    if(meters<=Math.max(40,Math.min(90,v*7)))return APPROACH;
    if(meters<=Math.max(100,Math.min(250,v*18)))return PREPARE;return FAR;
  }
  public static String phaseText(int phase){return new String[]{"沿路线前行","提前预告","接近路口","临近转弯","转弯已通过","已到达","偏离路线","定位待确认","行进方向相反","定位正常，请移动以确认方向","已停车，保留下一转向"}[Math.max(0,Math.min(10,phase))];}
}
