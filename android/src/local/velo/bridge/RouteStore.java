package local.velo.bridge;
import android.content.Context;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public final class RouteStore {
  static volatile RouteModel route;
  static volatile boolean replay=false;
  static volatile long revision=0;
  static boolean initialized=false;
  static final class Selection {final RouteModel route;final boolean replay;final long revision;Selection(RouteModel r,boolean p,long v){route=r;replay=p;revision=v;}}
  static synchronized Selection snapshot(){return new Selection(route,replay,revision);}
  static synchronized void set(RouteModel r,boolean simulated){route=r;replay=simulated;revision++;initialized=true;}
  static synchronized void load(Context c){
    if(initialized)return;initialized=true;if("none".equals(c.getSharedPreferences("navigation",0).getString("last_source","")))return;
    if("model".equals(c.getSharedPreferences("navigation",0).getString("last_source",""))){try{set(RouteCodec.decode(readFile(new File(c.getFilesDir(),"current-route.json"))),false);}catch(Exception ignored){}return;}
    if("online".equals(c.getSharedPreferences("navigation",0).getString("last_source","gpx"))){
      File f=new File(c.getFilesDir(),"planned-route.json");
      if(f.exists())try{JSONObject saved=new JSONObject(readFile(f));set(BikePlanner.parse(saved.getString("response"),saved.getString("name")),false);}catch(Exception ignored){}
      return;
    }
    File f=new File(c.getFilesDir(),"route.gpx");if(f.exists())try(InputStream in=new FileInputStream(f)){set(parse(in),false);}catch(Exception ignored){}
  }
  static void saveActive(Context c,RouteModel route)throws Exception{String raw=RouteCodec.encode(route);File temp=new File(c.getFilesDir(),"current-route.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(raw.getBytes(StandardCharsets.UTF_8));}if(!temp.renameTo(new File(c.getFilesDir(),"current-route.json")))throw new IOException("路线保存失败");c.getSharedPreferences("navigation",0).edit().putString("last_source","model").commit();set(route,false);}
  static synchronized void clearActive(Context c)throws IOException{if(!c.getSharedPreferences("navigation",0).edit().putString("last_source","none").remove("origin").remove("destination").remove("route_via").putBoolean("draft_changed",false).commit())throw new IOException("清除路线未保存，请重试");set(null,false);}
  static String readFile(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>5*1024*1024)throw new IOException("缓存文件过大");bytes.write(block,0,n);}return new String(bytes.toByteArray(),StandardCharsets.UTF_8);}}
  static void saveOnline(Context c,BikePlanner.Result result)throws Exception{
    JSONObject saved=new JSONObject();saved.put("name",result.route.name);saved.put("response",result.json);
    File temp=new File(c.getFilesDir(),"planned-route.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(saved.toString().getBytes(StandardCharsets.UTF_8));}
    if(!temp.renameTo(new File(c.getFilesDir(),"planned-route.json")))throw new IOException("保存路线失败");
    c.getSharedPreferences("navigation",0).edit().putString("last_source","online").commit();set(result.route,false);
  }
  static RouteModel parse(InputStream in)throws Exception {
    XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(in,null);
    ArrayList<double[]> points=new ArrayList<>();String kind="";int segments=0,paths=0;
    for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
      if(event==XmlPullParser.DOCDECL)throw new IOException("不支持包含外部声明的 GPX");
      if(event!=XmlPullParser.START_TAG)continue;
      String tag=parser.getName();
      if((tag.equals("trk")||tag.equals("rte")) && ++paths>1)throw new IOException("请导入一条路线，不要合并多条路线");
      if(tag.equals("trkseg") && ++segments>1)throw new IOException("请导入单段连续轨迹，避免跨段连线");
      if(tag.equals("trkpt")||tag.equals("rtept")){
        if(kind.isEmpty())kind=tag;if(!tag.equals(kind))throw new IOException("请使用仅含一条轨迹的 GPX");
        if(points.size()>=12000)throw new IOException("路线点数超过 12000");
        points.add(new double[]{Double.parseDouble(parser.getAttributeValue(null,"lat")),Double.parseDouble(parser.getAttributeValue(null,"lon"))});
      }
    }
    return new RouteModel("导入的 GPX 路线",points);
  }
}
