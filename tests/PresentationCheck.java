import local.velo.bridge.*;
public class PresentationCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  RouteModel.Fix raw=new RouteModel.Fix();raw.turn='L';raw.meters=80;raw.phase=2;raw.progress=40;raw.heading=90;raw.viewportSpan=150;
  RouteModel.Fix f=NavPresentation.gps(raw,false,null,0);check(f.phase==9&&f.turn=='N',"initially parked: GPS fix but no course");check(raw.turn=='L'&&raw.meters==80&&raw.phase==2,"display never destroys matched maneuver");
  f=NavPresentation.gps(raw,true,null,0);check(f.phase==10&&f.turn=='L'&&f.meters==80,"stopped after motion keeps next turn");
  f=NavPresentation.gps(raw,true,90.,4);check(f.phase==2&&f.turn=='L',"resuming restores turn");
  f=NavPresentation.gps(raw,true,null,4);check(f.phase==9,"moving without a reliable course waits");
  f=NavPresentation.gps(raw,true,null,-1);check(f.phase==9,"missing speed is not called parked");
  for(int p:new int[]{5,6,7,8}){raw.phase=p;f=NavPresentation.gps(raw,false,null,0);check(f.phase==p,"arrival and safety states preserved "+p);}
  check(RouteTracker.phaseText(9).contains("定位正常")&&RouteTracker.phaseText(10).contains("停车"),"phone labels distinguish fix/course/stop");
  System.out.println(n+" navigation presentation and non-mutation checks passed");
 }
}
