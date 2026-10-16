package local.velo.bridge;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** BRouter GeoJSON client. Requests only occur after an explicit planning action. */
public final class BikePlanner {
  public static final String DEFAULT_ENDPOINT="https://brouter.de/brouter";
  private static long lastRequest=0;
  public static String endpoint(Context c){return c.getSharedPreferences("navigation",0).getString("endpoint",DEFAULT_ENDPOINT);}
  public static String requestUrl(String endpoint,double lat1,double lon1,double lat2,double lon2)throws Exception{
    for(double v:new double[]{lat1,lon1,lat2,lon2})if(!Double.isFinite(v))throw new IOException("坐标无效");
    if(Math.abs(lat1)>80||Math.abs(lat2)>80||Math.abs(lon1)>180||Math.abs(lon2)>180)throw new IOException("坐标超出支持范围");
    URI uri=new URI(endpoint);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getRawQuery()!=null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IOException("服务地址必须是不含参数的 HTTPS BRouter 接口");
    return endpoint+"?lonlats="+lon1+","+lat1+"%7C"+lon2+","+lat2+"&profile=trekking&alternativeidx=0&format=geojson&timode=2";
  }
  public static final class Result {public final RouteModel route;public final String json;Result(RouteModel r,String j){route=r;json=j;}}
  public static synchronized Result plan(Context c,double lat1,double lon1,double lat2,double lon2,String name)throws Exception{
    double distance=Math.hypot(Math.toRadians(lat2-lat1)*6371000,Math.toRadians(lon2-lon1)*6371000*Math.cos(Math.toRadians(lat1)));
    if(distance<20)throw new IOException("起点与终点过近，请选择至少相隔 20 米的位置");
    if(distance>80000)throw new IOException("公共服务测试版先限制为直线距离 80 km 内");
    long wait=1500-(System.currentTimeMillis()-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=System.currentTimeMillis();
    HttpURLConnection connection=(HttpURLConnection)new URL(requestUrl(endpoint(c),lat1,lon1,lat2,lon2)).openConnection();
    connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setRequestProperty("User-Agent","VELO-BikeDash/0.5 (personal bicycle navigation prototype)");
    connection.setRequestProperty("Accept","application/geo+json, application/json");connection.setInstanceFollowRedirects(false);
    try{
      int status=connection.getResponseCode();if(status!=200)throw new IOException("路线服务返回 HTTP "+status+"，请稍后重试");
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){
        byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("路线响应过大");bytes.write(block,0,n);}
      }
      String raw=new String(bytes.toByteArray(),StandardCharsets.UTF_8);return new Result(parse(raw,name),raw);
    }finally{connection.disconnect();}
  }
  public static char direction(int command){
    if(command==1)return 'S';if(command==2||command==3||command==4||command==8||command==17)return 'L';
    if(command==5||command==6||command==7||command==9||command==18)return 'R';
    if(command==10||command==11||command==15)return 'U';if(command==13||command==14)return 'O';if(command==100)return 'A';return 'N';
  }
  public static RouteModel parse(String raw,String name)throws Exception{
    JSONObject root=new JSONObject(raw);JSONArray features=root.getJSONArray("features");
    if(features.length()==0)throw new IOException("没有可用的自行车路线");
    JSONObject feature=features.getJSONObject(0),geometry=feature.getJSONObject("geometry");
    if(!"LineString".equals(geometry.getString("type")))throw new IOException("路线几何格式不支持");
    JSONArray coordinates=geometry.getJSONArray("coordinates");ArrayList<double[]> points=new ArrayList<>();
    if(coordinates.length()>12000)throw new IOException("路线过于复杂，请缩短路程");
    for(int i=0;i<coordinates.length();i++){JSONArray p=coordinates.getJSONArray(i);points.add(new double[]{p.getDouble(1),p.getDouble(0)});}
    RouteModel route=new RouteModel(name,points,true);JSONObject props=feature.getJSONObject("properties");
    route.plannedSeconds=props.optDouble("total-time",0);
    JSONArray hints=props.optJSONArray("voicehints");ArrayList<RouteModel.Maneuver> maneuvers=new ArrayList<>();double previous=0;
    if(hints!=null)for(int i=0;i<hints.length();i++){
      JSONArray hint=hints.getJSONArray(i);int index=hint.getInt(0),command=hint.getInt(1);
      if(index<0||index>=points.size())throw new IOException("转向指令的位置无效");
      double[] p=points.get(index);double progress=route.progressAt(p[0],p[1],previous);previous=progress;
      int exit=command==13||command==14?Math.abs(hint.optInt(2,0)):0;if(exit>20)exit=0;
      char direction=direction(command);String text=direction=='O'?"环岛："+(exit>0?"第 "+exit+" 个出口":"出口未知，请结合路线确认"):direction=='N'?"该路口类型暂未支持":direction=='L'?"左转":direction=='R'?"右转":direction=='U'?"掉头":"前行";
      maneuvers.add(new RouteModel.Maneuver(progress,direction,text,exit));
    }
    if(hints==null||hints.length()==0)throw new IOException("服务没有返回转向指令，请更换路线或重试");
    route.maneuvers=Collections.unmodifiableList(maneuvers);return route;
  }
}
