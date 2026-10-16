import local.velo.bridge.*;
import java.util.*;
public class HeadingCheck {
  static int n=0;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
  static RouteModel line(double east,double north){double lat=35.68,lon=139.76;return new RouteModel("test",Arrays.asList(new double[]{lat,lon},new double[]{lat+Math.toDegrees(north/6371000),lon+Math.toDegrees(east/(6371000*Math.cos(Math.toRadians(lat))))}));}
  public static void main(String[] args){
    for(double[] cardinal:new double[][]{{0,200},{200,0},{0,-200},{-200,0}}){RouteModel r=line(cardinal[0],cardinal[1]);RouteModel.Fix f=r.at(80);boolean ahead=false,past=false;
      for(int[] s:r.headingSegments(f,150)){for(int i=0;i<4;i++)check(s[i]>=0&&s[i]<=127,"heading-up clipping");check(Math.abs(s[0]-64)<=1&&Math.abs(s[2]-64)<=1,"course forward centered");
        if(s[4]==1){ahead=true;check(s[3]<s[1],"future route always up");}else{past=true;check(s[1]>s[3],"past route below marker");}}
      check(ahead&&past,"both route styles retained");}
    HeadingFilter h=new HeadingFilter();h.update(359,359.0,true,0);double result=h.update(0,1.0,true,1000);check(result<10||result>350,"cross north shortest path");double held=h.update(180,180.0,false,2000);check(held==result,"stationary heading held");check(h.update(180,null,true,3000)==held,"missing course held");
    ViewScale z=new ViewScale();RouteModel.Fix f=line(0,200).at(0);f.meters=84;check(z.update(f)==150,"approach zoom");f.meters=35;check(z.update(f)==150,"hysteresis avoids early toggle");f.meters=27;check(z.update(f)==90,"junction zoom");f.meters=36;check(z.update(f)==90,"junction zoom held");f.meters=46;check(z.update(f)==150,"zoom out threshold");f.offRoute=true;check(z.update(f)==300,"offroute wide view");
    RouteModel round=line(0,200);round.maneuvers=Arrays.asList(new RouteModel.Maneuver(100,'O',"roundabout",3));
    // Explicit service instructions carry their exit independently of map geometry.
    RouteModel service=new RouteModel("roundabout",Arrays.asList(new double[]{35.68,139.76},new double[]{35.682,139.76}),true);service.maneuvers=Arrays.asList(new RouteModel.Maneuver(100,'O',"roundabout",3));f=service.at(40);check(f.turn=='O'&&f.exit==3,"roundabout exit carried");check(service.packet(f,true,3).startsWith("H,3,R,0,O,60,"),"roundabout wire packet");
    System.out.println(n+" course-up, heading, zoom and maneuver checks passed");
  }
}
