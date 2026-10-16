import local.velo.bridge.*;import java.nio.file.*;import java.util.*;
public class RoadCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 public static void main(String[] a)throws Exception{
  String raw=new String(Files.readAllBytes(Paths.get("tests/assets/nearby-kochi.json")),"UTF-8");ArrayList<double[]> vector=new ArrayList<>(MvtRoads.decode(Files.readAllBytes(Paths.get("tests/assets/road-real.pbf")),14,14268,6569));vector.addAll(MvtRoads.decode(Files.readAllBytes(Paths.get("tests/assets/road-real-east.pbf")),14,14269,6569));RoadContext roads=new RoadContext(33.551833,133.507556,0,vector);check(roads.edges.size()>100,"real nearby road geometry");
  RouteModel route=new RouteModel("real test area",Arrays.asList(new double[]{33.551833,133.507556},new double[]{33.554,133.507556}));
  RouteModel.Fix f=route.at(0);f.viewportSpan=300;
  for(int angle:new int[]{0,45,90,180,270,359}){f.heading=angle;String packet=roads.packet(route,f,123);check(packet.length()<1536,"serial line bound");String[] fields=packet.trim().split(",");int count=Integer.parseInt(fields[2]);check(count>0&&count<=72,"bounded nearby detail");check(fields.length==3+count*5,"wire field count");for(int i=3;i<fields.length;i++){int v=Integer.parseInt(fields[i]);check(v>=0&&v<=((i-3)%5==4?2:127),"coordinate clipped");}}
  f.x=4000;check(roads.packet(route,f,5).equals("B,5,0\n"),"old area never follows rider");
  check(roads.covers(33.551833,133.507556)&&!roads.covers(34,134),"coverage boundary");
  // A real local street supplies the illustrative route; packets are explicitly REPLAY.
  org.json.JSONArray ways=new org.json.JSONObject(raw).getJSONArray("elements");double best=1e9;ArrayList<double[]> selected=null;
  for(int i=0;i<ways.length();i++){org.json.JSONObject w=ways.getJSONObject(i);org.json.JSONArray g=w.optJSONArray("geometry");if(g==null||g.length()<3)continue;ArrayList<double[]> p=new ArrayList<>();double len=0,min=1e9;for(int j=0;j<g.length();j++){org.json.JSONObject q=g.getJSONObject(j);double lat=q.getDouble("lat"),lon=q.getDouble("lon");if(j>0)len+=RoadContext.distance(p.get(j-1)[0],p.get(j-1)[1],lat,lon);p.add(new double[]{lat,lon});min=Math.min(min,RoadContext.distance(lat,lon,33.551833,133.507556));}if(len>90&&min<best){best=min;selected=p;}}
  RouteModel scene=new RouteModel("OSM street preview",selected);RouteModel.Fix view=scene.at(Math.min(35,scene.length/3));view.viewportSpan=300;view.turn='L';view.meters=80;view.phase=2;
  String p=scene.packet(view,true,123)+roads.packet(scene,view,123)+"T,123,2,5\n";Files.write(Paths.get("tests/assets/board-roads.txt"),p.getBytes("US-ASCII"));
  view.phase=8;view.turn='N';view.meters=-1;Files.write(Paths.get("tests/assets/board-roads-wrong.txt"),(scene.packet(view,true,123)+roads.packet(scene,view,123)+"T,123,8,5\n").getBytes("US-ASCII"));
  System.out.println(n+" nearby-road geometry and wire checks passed; real edges="+roads.edges.size());
 }
}
