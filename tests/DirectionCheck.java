import local.velo.bridge.*;
public class DirectionCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 public static void main(String[] a){
  TravelDirection d=new TravelDirection();TravelDirection.Result r=null;
  for(int i=0;i<12;i++)r=d.update(i%2,0,5,0,null,null,i*1000);check(r.course==null,"stationary jitter");
  d.reset();for(int i=0;i<7;i++)r=d.update(i*3,0,5,3,null,null,i*1000);check(r.course!=null&&Math.abs(r.course-90)<1,"east from positions");
  check(d.update(999,999,5,3,null,null,6000)==r,"duplicate ignored");
  r=d.update(21,0,5,3,null,null,20000);check(r.course==null,"gap resets baseline");
  d.reset();r=d.update(0,0,5,3,270.,10.,0);check(r.course==270.,"accurate velocity");
  d.reset();r=d.update(0,0,5,3,270.,90.,0);check(r.course==null,"uncertain bearing");
  d.reset();for(int i=0;i<7;i++)r=d.update(0,-i*3,5,-1,null,null,i*1000);check(r.course!=null&&r.course==180.&&r.speed>=1.2,"missing speed derived");
  WrongWayMonitor m=new WrongWayMonitor();for(int i=0;i<12;i++)m.update(i%2,0,5,3,270.,90,1000*i,true,false);check(!m.active,"opposite bearing without movement");
  m.reset();for(int i=0;i<7;i++)m.update(100-i*3,0,5,3,270.,90,1000*i,true,false);check(m.active&&m.evidence,"real reverse");
  for(int i=0;i<5;i++)m.update(82+i*3,0,5,3,90.,90,7000+1000*i,true,false);check(!m.active,"forward recovery");
  m.reset();for(int i=0;i<10;i++)m.update(100-i*3,0,5,3,270.,90,1000*i,true,true);check(!m.active,"planned U turn");
  m.reset();for(int i=0;i<10;i++)m.update(100-i*3,0,22,3,270.,90,1000*i,true,false);check(!m.active,"weak GPS");
  m.reset();for(int i=0;i<10;i++)m.update(0,i*3,5,3,0.,90,1000*i,true,false);check(!m.active,"perpendicular motion");
  WarningPolicy p=new WarningPolicy();check(!p.update(8,true,true,true,0),"replay silent");check(p.update(8,false,true,true,1000),"first warning");check(!p.update(8,false,true,true,2000),"no spam");check(!p.update(8,false,true,false,32000),"stopped silent");check(p.update(8,false,true,true,33000),"repeat after cooldown");check(!p.update(8,false,false,true,34000),"stale silent");check(p.update(8,false,true,true,35000),"fresh episode");
  RouteTracker tracker=new RouteTracker(NavigationCheck.route(new double[][]{{0,0},{500,0}}));RouteModel.Fix f=null;
  for(int i=0;i<5;i++)f=tracker.update(100+i*3,0,5,3,90.,i*1000);
  for(int i=0;i<8;i++)f=tracker.update(112-i*3,0,5,3,270.,5000+i*1000);check(f.phase==8,"forward then reverse integrated");
  for(int i=0;i<7;i++)f=tracker.update(91+i*3,0,5,3,90.,13000+i*1000);check(f.phase!=8&&Math.abs(f.progress-109)<1,"recovery resets held progress");
  System.out.println(n+" direction and warning checks passed");
 }
}
