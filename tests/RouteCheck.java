import local.velo.bridge.RouteModel;
import java.util.*;
public class RouteCheck {
  static int checks=0;
  static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
  public static void main(String[] a){
    RouteModel r=RouteModel.demo();
    ok(Math.abs(r.length-560)<0.1,"route length");
    RouteModel.Fix start=r.at(0);ok(start.turn=='R'&&Math.abs(start.meters-100)<2,"first right turn");
    RouteModel.Fix next=r.at(130);ok(next.turn=='L'&&Math.abs(next.meters-50)<2,"next left turn");
    ok(r.at(99).turn=='R',"retain maneuver until junction");
    ok(r.at(r.length).turn=='A',"arrival");
    RouteModel.Fix point=r.at(140);RouteModel.Fix match=r.match(point.x,point.y,130);
    ok(!match.offRoute && Math.abs(match.progress-140)<0.1,"project to route");
    ok(r.match(1000,1000,130).offRoute,"off-route detection");
    for(double s=0;s<r.length;s+=8){RouteModel.Fix f=r.at(s);String packet=r.packet(f,true,1);ok(packet.length()<1536,"wire size");ok(packet.startsWith("H,1,R,0,"),"replay wire label");
      for(int[] line:r.segments(f,120))for(int v:line)ok(v>=0&&v<=127,"clipping bounds");}
    ArrayList<double[]> noisy=new ArrayList<>();for(int i=0;i<100;i++)noisy.add(new double[]{35+i*0.00001,139+Math.sin(i)*0.000005});
    RouteModel straight=new RouteModel("noise",noisy);ok(straight.x.length==2,"suppress GPS bend noise");
    boolean rejected=false;try{new RouteModel("invalid",Arrays.asList(new double[]{Double.NaN,0},new double[]{0,0}));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"reject invalid coordinates");
    System.out.println(checks+" route geometry, guidance and wire checks passed");
  }
}
