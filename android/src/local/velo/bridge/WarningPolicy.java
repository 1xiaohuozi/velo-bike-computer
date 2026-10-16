package local.velo.bridge;
/** At most one alert per 30 seconds in a continuous wrong-way episode. */
public final class WarningPolicy {
 boolean episode;long last=-1;
 public void reset(){episode=false;last=-1;}
 public boolean update(int phase,boolean replay,boolean fresh,boolean movingOpposite,long time){
  if(replay||!fresh||phase!=RouteTracker.WRONG_WAY){episode=false;return false;}
  if(!movingOpposite)return false;
  if(!episode||last<0||time-last>=30000){episode=true;last=time;return true;}return false;
 }
}
