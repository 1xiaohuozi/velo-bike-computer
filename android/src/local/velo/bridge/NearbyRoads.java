package local.velo.bridge;
import android.content.Context;import android.os.SystemClock;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.GZIPInputStream;import org.json.*;
/** Uses the same OpenFreeMap vector source as the phone map; no Overpass queries. */
public final class NearbyRoads {
 final Context context;volatile RoadContext roads;volatile boolean busy,closed;long attempted=-1,retry=15000;volatile HttpURLConnection connection;
 public volatile String lastError="",status="周边道路待加载";
 public NearbyRoads(Context c){context=c;}
 public void update(RouteModel route,RouteModel.Fix fix){
  if(closed||busy)return;if(!context.getSharedPreferences("navigation",0).getBoolean("board_roads",true)){status="周边道路已关闭";return;}
  double[] ll=MapMath.point(route,fix.x,fix.y);RoadContext current=roads;if(current!=null&&current.covers(ll[0],ll[1])){status=current.edges.isEmpty()?"附近暂无道路数据":"周边道路已就绪";return;}
  long now=SystemClock.elapsedRealtime();if(attempted>=0&&now-attempted<retry)return;attempted=now;busy=true;status="正在加载码表周边道路";
  new Thread(()->{try{
   File dir=new File(context.getCacheDir(),"board-vector-roads");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("缓存不可用");
   String template=template(dir);int z=Math.abs(ll[0])>75?12:Math.abs(ll[0])>60?13:14,n=1<<z;
   double radius=650/(2*Math.PI*6371000*Math.cos(Math.toRadians(ll[0]))),wx=MapMath.x(ll[1]),wy=MapMath.y(ll[0]);
   int x0=(int)Math.floor((wx-radius)*n),x1=(int)Math.floor((wx+radius)*n),y0=Math.max(0,(int)Math.floor((wy-radius)*n)),y1=Math.min(n-1,(int)Math.floor((wy+radius)*n));
   if((x1-x0+1)*(y1-y0+1)>9)throw new IOException("区域过大");ArrayList<double[]> edges=new ArrayList<>();
   for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++){
    if(closed)return;int tx=MapMath.tile(x,z);String url=template.replace("{z}",""+z).replace("{x}",""+tx).replace("{y}",""+y);
    File file=new File(dir,"tile_"+Integer.toHexString(template.hashCode())+"_"+z+"_"+tx+"_"+y);byte[] tile;
    if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<604800000L){try(InputStream in=new FileInputStream(file)){tile=read(in,4*1024*1024);}}
    else{tile=fetch(url,4*1024*1024);MvtRoads.decode(tile,z,tx,y);save(file,tile);}
    for(double[] e:MvtRoads.decode(tile,z,tx,y)){
     double[] a=route.local(e[0],e[1]),b=route.local(e[2],e[3]);double t=RouteModel.project(fix.x,fix.y,a[0],a[1],b[0],b[1]);
     if(Math.hypot(a[0]+t*(b[0]-a[0])-fix.x,a[1]+t*(b[1]-a[1])-fix.y)<950)edges.add(e);
     if(edges.size()>30000)throw new IOException("附近道路过多");
    }
   }
   if(closed)return;roads=new RoadContext(ll[0],ll[1],System.currentTimeMillis(),edges);status=edges.isEmpty()?"附近暂无道路数据":"周边道路已就绪";lastError="";retry=15000;
   File[] files=dir.listFiles((d,name)->name.startsWith("tile_"));if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));long total=0;for(int i=0;i<files.length;i++){total+=files[i].length();if(i>=64||total>20*1024*1024)files[i].delete();}}
  }catch(Exception e){lastError=e.toString();android.util.Log.w("VeloRoads","Vector roads unavailable",e);if(!closed)status="周边道路暂不可用，保留路线";retry=Math.min(300000,retry*2);}finally{busy=false;}},"velo-vector-roads").start();
 }
 String template(File dir)throws Exception{
  File file=new File(dir,"source.json");byte[] raw=null;if(file.isFile())try(InputStream in=new FileInputStream(file)){raw=read(in,1024*1024);}
  if(raw==null||System.currentTimeMillis()-file.lastModified()>86400000)try{raw=fetch("https://tiles.openfreemap.org/planet",1024*1024);save(file,raw);}catch(Exception e){if(raw==null)throw e;}
  String template=new JSONObject(new String(raw,StandardCharsets.UTF_8)).getJSONArray("tiles").getString(0);
  if(!template.startsWith("https://tiles.openfreemap.org/")||!template.contains("{z}")||!template.contains("{x}")||!template.contains("{y}"))throw new IOException("地图来源无效");return template;
 }
 byte[] fetch(String url,int limit)throws IOException{
  if(closed)throw new IOException("已结束");HttpURLConnection request=(HttpURLConnection)new URL(url).openConnection();connection=request;request.setConnectTimeout(8000);request.setReadTimeout(12000);request.setInstanceFollowRedirects(false);request.setRequestProperty("User-Agent","VELO-BikeDash/0.10 (personal bicycle navigator)");request.setRequestProperty("Accept-Encoding","gzip");
  try{if(closed)throw new IOException("已结束");int code=request.getResponseCode();if(code!=200)throw new IOException("地图 HTTP "+code);try(InputStream in="gzip".equalsIgnoreCase(request.getContentEncoding())?new GZIPInputStream(request.getInputStream()):request.getInputStream()){byte[] raw=read(in,limit);if(raw.length>2&&(raw[0]&255)==31&&(raw[1]&255)==139)try(InputStream gz=new GZIPInputStream(new ByteArrayInputStream(raw))){return read(gz,limit);}return raw;}}finally{request.disconnect();connection=null;}
 }
 static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("地图响应过大");out.write(buffer,0,n);}return out.toByteArray();}
 static void save(File file,byte[] bytes)throws IOException{File pending=new File(file.getPath()+".tmp");try(OutputStream out=new FileOutputStream(pending)){out.write(bytes);}if(!pending.renameTo(file)){pending.delete();throw new IOException("缓存保存失败");}}
 public String packet(RouteModel route,RouteModel.Fix fix,long seq){RoadContext current=roads;return current==null||!context.getSharedPreferences("navigation",0).getBoolean("board_roads",true)?"B,"+seq+",0\n":current.packet(route,fix,seq);}
 public void close(){closed=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();}
}