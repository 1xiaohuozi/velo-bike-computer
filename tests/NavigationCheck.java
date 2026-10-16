import local.velo.bridge.*;import java.util.*;
public class NavigationCheck {
 static int n;static void check(boolean ok,String s){n++;if(!ok)throw new AssertionError(s);}
 static RouteModel route(double[][] meters){ArrayList<double[]> p=new ArrayList<>();for(double[] xy:meters)p.add(new double[]{35+Math.toDegrees(xy[1]/6371000),139+Math.toDegrees(xy[0]/(6371000*Math.cos(Math.toRadians(35))))});return new RouteModel("test",p,true);}
 static RouteTracker tracker(){RouteModel r=route(new double[][]{{0,0},{100,0},{100,100},{200,100}});r.maneuvers=Arrays.asList(new RouteModel.Maneuver(100,'L',"left"),new RouteModel.Maneuver(200,'R',"right"));return new RouteTracker(r);}
 public static void main(String[] args){
  RouteTracker t=tracker();RouteModel.Fix f=t.update(10,0,5,4,90.,0);check(f.phase==7,"initial match requires confirmation");t.update(14,0,5,4,90.,1000);f=t.update(18,0,5,4,90.,2000);check(f.turn=='L'&&f.phase==1,"confirmed advance warning");
  f=t.update(19,2,5,0,null,3000);check(Math.abs(f.progress-18)<.01,"stationary drift holds progress");
  for(int x=25;x<=70;x+=5)f=t.update(x,0,5,5,90.,4000+x*200);check(f.turn=='L'&&f.phase==2,"approach phase");
  for(int x=75;x<=95;x+=5)f=t.update(x,0,5,5,90.,20000+x*200);check(f.turn=='L'&&f.phase==3,"turn now phase");
  f=t.update(99,0,5,5,90.,40000);check(f.turn=='L',"no premature turn consumption");
  f=t.update(100,9,5,5,0.,41000);check(f.turn=='L',"one crossing sample insufficient");f=t.update(100,14,5,5,0.,42000);check(f.turn=='R',"confirmed left switches to next right");
  RouteTracker o=tracker();for(int i=0;i<3;i++)o.update(40+i,0,5,4,90.,i*1000);f=o.update(45,-60,5,4,90.,3000);check(!f.offRoute&&f.turn=='N',"one excursion suppresses turn without declaring off route");f=o.update(46,0,5,4,90.,4000);check(!f.offRoute,"single GPS drift recovers");
  for(int i=0;i<3;i++)f=o.update(50+i,-70,5,4,90.,5000+i*1000);check(f.offRoute&&f.phase==6,"sustained deviation confirms off route");
  for(int i=0;i<3;i++)f=o.update(55+i,0,5,4,90.,8000+i*1000);check(!f.offRoute,"three reliable samples recover");
  RouteTracker weak=tracker();for(int i=0;i<3;i++)f=weak.update(85+i,0,24,4,90.,i*1000);check(f.phase==7&&f.turn=='N',"weak accuracy does not issue turn now");
  RouteTracker reverse=tracker();for(int i=0;i<9;i++)f=reverse.update(50-i*3,0,5,3,270.,i*1000);check(f.phase==8&&f.turn=='N',"opposite travel suppresses misleading guidance");
  RouteModel parallel=route(new double[][]{{0,0},{100,0},{100,10},{0,10}});RouteTracker p=new RouteTracker(parallel);for(int i=0;i<3;i++)f=p.update(40-i*3,5,5,3,270.,i*1000);check(f.progress>150,"heading disambiguates opposite parallel segment");
  RouteModel straight=route(new double[][]{{0,0},{1000,0}});RouteTracker jump=new RouteTracker(straight);for(int i=0;i<3;i++)jump.update(10+i*4,0,5,4,90.,i*1000);f=jump.update(800,0,5,4,90.,3000);check(f.turn=='N'&&f.progress<100,"single teleport cannot jump route progress");
  RouteTracker arrival=new RouteTracker(route(new double[][]{{0,0},{100,0}}));for(int i=0;i<3;i++)f=arrival.update(90+i*3,0,5,2,90.,i*1000);check(f.turn=='A'&&f.phase==5,"arrival requires consecutive endpoint fixes");
  RouteTracker gap=tracker();for(int i=0;i<3;i++)gap.update(30+i,0,5,4,90.,i*1000);f=gap.update(40,0,5,4,90.,20000);check(f.phase==7,"GPS gap requires new confirmation");
  RouteTracker duplicate=tracker();f=duplicate.update(40,0,5,4,90.,1000);for(int i=0;i<5;i++)f=duplicate.update(40,0,5,4,90.,1000);check(f.phase==7,"repeated same fix cannot build confidence");
  check(RouteTracker.phase(20,8,'R')==3&&RouteTracker.phase(20,2,'R')==2,"speed aware warning distance");
  RouteModel ur=route(new double[][]{{0,0},{100,0},{0,0}});ur.maneuvers=Arrays.asList(new RouteModel.Maneuver(100,'U',"u turn"));RouteTracker ut=new RouteTracker(ur);for(int i=0;i<3;i++)f=ut.update(80+i*5,0,5,5,90.,i*1000);check(f.turn=='U',"U turn preview retained");ut.update(99,0,5,5,90.,3000);f=ut.update(94,0,5,5,270.,4000);check(f.turn=='U',"U turn not completed on one reverse sample");ut.update(90,0,5,5,270.,5000);f=ut.update(85,0,5,5,270.,6000);check(f.turn=='S'&&f.phase!=8,"U turn confirmed without wrong-way false alarm");
  RouteModel rr=route(new double[][]{{0,0},{100,0},{100,100}});rr.maneuvers=Arrays.asList(new RouteModel.Maneuver(100,'O',"roundabout",3));RouteTracker rt=new RouteTracker(rr);for(int i=0;i<3;i++)f=rt.update(60+i*5,0,5,5,90.,i*1000);check(f.turn=='O'&&f.exit==3,"roundabout exit survives staged guidance");
  RouteModel cross=route(new double[][]{{-100,0},{100,0},{0,100},{0,-100}});RouteTracker ct=new RouteTracker(cross);for(int i=0;i<6;i++)f=ct.update(-30+i*6,0,5,6,90.,i*1000);check(f.progress<150,"self crossing does not skip to later route leg");
  RouteTracker rejoin=new RouteTracker(straight);for(int i=0;i<3;i++)rejoin.update(10+i*4,0,5,4,90.,i*1000);for(int i=0;i<10;i++)f=rejoin.update(800+i*3,0,5,3,90.,3000+i*1000);check(!f.offRoute&&f.progress>800,"sustained valid rejoin recovers rather than deadlocking");
  RouteTracker unknown=new RouteTracker(straight);for(int i=0;i<4;i++)f=unknown.update(10+i*4,0,5,-1,null,i*1000);check(f.progress>20,"missing speed is not treated as a stopped bicycle");
  System.out.println(n+" navigation scenario checks passed");
 }
}
