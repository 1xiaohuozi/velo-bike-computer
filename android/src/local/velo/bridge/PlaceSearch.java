package local.velo.bridge;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Manual, low-volume Photon search; no requests while typing. */
public final class PlaceSearch {
  public static final String DEFAULT_ENDPOINT="https://photon.komoot.io/api/";
  private static long lastRequest;
  public static final class Entry {
    public final String name,address;public final double lat,lon;
    public Entry(String n,String a,double x,double y){name=n;address=a;lat=x;lon=y;}
    public String label(){return name+"\n"+address+String.format(Locale.US,"\n%.6f, %.6f",lat,lon);}
  }
  public static String url(String endpoint,String query,Double lat,Double lon)throws Exception{
    URI u=new URI(endpoint);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawQuery()!=null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("请输入不含参数的 HTTPS Photon 接口");
    String result=endpoint+"?q="+URLEncoder.encode(query,"UTF-8")+"&limit=8";
    if(lat!=null&&lon!=null&&Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=80&&Math.abs(lon)<=180)result+="&lat="+lat+"&lon="+lon;
    return result;
  }
  public static List<Entry> parse(String raw)throws Exception{
    JSONArray features=new JSONObject(raw).getJSONArray("features");List<Entry> found=new ArrayList<>();Set<String> seen=new HashSet<>();
    for(int i=0;i<Math.min(32,features.length());i++){
      try{JSONObject f=features.getJSONObject(i),g=f.getJSONObject("geometry"),p=f.getJSONObject("properties");if(!"Point".equals(g.optString("type")))continue;
        JSONArray xy=g.getJSONArray("coordinates");double lon=xy.getDouble(0),lat=xy.getDouble(1);if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>80||Math.abs(lon)>180)continue;
        String name=p.optString("name","").trim();if(name.isEmpty())name=p.optString("street","未命名地点");
        LinkedHashSet<String> parts=new LinkedHashSet<>();for(String field:new String[]{"country","state","city","district","street","housenumber"}){String s=p.optString(field,"").trim();if(!s.isEmpty()&&!s.equals(name))parts.add(s);}
        String address=String.join(" · ",parts);String key=name+":"+lat+":"+lon;if(seen.add(key))found.add(new Entry(name,address,lat,lon));
      }catch(JSONException ignored){}
    }return found;
  }
  public static synchronized List<Entry> search(String endpoint,String query,Double lat,Double lon)throws Exception{
    long wait=1200-(System.nanoTime()/1000000-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=System.nanoTime()/1000000;
    HttpURLConnection c=(HttpURLConnection)new URL(url(endpoint,query,lat,lon)).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","VELO-BikeDash/0.7 (personal bicycle computer)");c.setRequestProperty("Accept","application/json");
    try{int status=c.getResponseCode();if(status!=200)throw new IOException(status==429?"搜索服务繁忙，请稍后重试":"搜索服务返回 HTTP "+status);
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("搜索响应过大");bytes.write(block,0,n);}}
      return parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
    }finally{c.disconnect();}
  }
}
