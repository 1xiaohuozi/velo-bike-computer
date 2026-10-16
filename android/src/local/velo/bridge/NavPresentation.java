package local.velo.bridge;
/** Display state must never destroy the matcher's next maneuver or GPS evidence. */
public final class NavPresentation {
 public static RouteModel.Fix gps(RouteModel.Fix source,boolean directionSeen,Double course,double speed){
  RouteModel.Fix f=new RouteModel.Fix();f.x=source.x;f.y=source.y;f.progress=source.progress;f.error=source.error;f.heading=source.heading;f.turn=source.turn;f.meters=source.meters;f.exit=source.exit;f.headingQuality=source.headingQuality;f.viewportSpan=source.viewportSpan;f.phase=source.phase;f.offRoute=source.offRoute;
  if(source.phase==RouteTracker.ARRIVED||source.phase==RouteTracker.OFF_ROUTE||source.phase==RouteTracker.CHECKING||source.phase==RouteTracker.WRONG_WAY)return f;
  if(!directionSeen||course==null&&(speed<0||speed>=1)){f.phase=RouteTracker.DIRECTION_WAIT;f.turn='N';f.meters=-1;}
  else if(speed>=0&&speed<1.2)f.phase=RouteTracker.STOPPED;
  return f;
 }
}
