import local.velo.bridge.*;
import java.nio.file.*;
import java.nio.charset.*;
public class PlannerCheck {
  static int count=0;static void check(boolean b,String s){count++;if(!b)throw new AssertionError(s);}
  public static void main(String[] args)throws Exception{
    String raw=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
    RouteModel route=BikePlanner.parse(raw,"Tokyo public-place routing fixture");
    check(route.roadInstructions,"road guidance enabled");check(route.x.length>20,"geometry parsed");check(route.maneuvers.size()==15,"maneuvers parsed");check(route.plannedSeconds==317,"duration parsed");check(route.length>1000&&route.length<1600,"route distance");
    double progress=-1;for(RouteModel.Maneuver m:route.maneuvers){check(m.progress>=progress,"ordered maneuvers");progress=m.progress;}
    check(BikePlanner.direction(2)=='L'&&BikePlanner.direction(5)=='R'&&BikePlanner.direction(10)=='U',"BRouter turn mapping");check(BikePlanner.direction(13)=='O',"roundabout type explicit");
    org.json.JSONObject changed=new org.json.JSONObject(raw);org.json.JSONArray first=changed.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getJSONArray("voicehints").getJSONArray(0);first.put(1,14);first.put(2,-3);RouteModel roundabout=BikePlanner.parse(changed.toString(),"roundabout");check(roundabout.maneuvers.get(0).direction=='O'&&roundabout.maneuvers.get(0).exit==3,"signed BRouter exit normalized without guessing");
    check(PlaceCoordinates.parse("35.6812,139.7671")[1]==139.7671,"coordinate order");check(PlaceCoordinates.parse("35.68，139.76")!=null,"Chinese comma");check(PlaceCoordinates.parse("NaN,139")==null,"reject NaN");check(PlaceCoordinates.parse("Tokyo")==null,"place-name fallback");check(PlaceCoordinates.parse("139,35")==null,"reject inverted range");
    check(BikePlanner.requestUrl("https://brouter.de/brouter",35,139,35.01,139.01).contains("profile=trekking"),"cycling profile");
    boolean blocked=false;try{BikePlanner.requestUrl("http://example.com",35,139,35.1,139.1);}catch(Exception e){blocked=true;}check(blocked,"HTTPS only");
    Files.write(Paths.get(args[1]),route.packet(route.at(30),true,501).getBytes(StandardCharsets.US_ASCII));
    System.out.println(count+" planner, turn, endpoint and coordinate checks passed");
  }
}
