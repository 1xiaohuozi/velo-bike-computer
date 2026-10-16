package local.velo.bridge;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.*;

/** Private bounded diagnostic logs; export only via an explicit user action. */
public final class RideLog {
  static final long LIMIT=8*1024*1024;final File file;final long started;volatile boolean closed,full;volatile String error="";
  final ConcurrentLinkedQueue<JSONObject> queue=new ConcurrentLinkedQueue<>();
  public RideLog(Context context,long time)throws Exception{
    started=time;File dir=new File(context.getFilesDir(),"rides");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法建立记录目录");
    file=new File(dir,"ride-"+System.currentTimeMillis()+".jsonl");if(!file.createNewFile())throw new IOException("记录文件已存在");
    File[] files=dir.listFiles((d,n)->n.matches("ride-[0-9]+\\.jsonl"));if(files!=null){Arrays.sort(files,Comparator.comparing(File::getName));for(int i=0;i<files.length-5;i++)if(!files[i].equals(file))files[i].delete();}
    context.getSharedPreferences("navigation",0).edit().putString("last_ride_log",file.getName()).apply();event("ride_start",time,"version","1.0.0","wall_time_ms",System.currentTimeMillis());flush();
  }
  public void event(String type,long time,Object... values){if(closed||full||queue.size()>=128)return;try{JSONObject row=new JSONObject();row.put("event",type);row.put("elapsed_ms",Math.max(0,time-started));for(int i=0;i+1<values.length;i+=2){Object value=values[i+1];if(value instanceof Double&&!Double.isFinite((Double)value))value=JSONObject.NULL;if(value instanceof Float&&!Float.isFinite((Float)value))value=JSONObject.NULL;row.put(values[i].toString(),value==null?JSONObject.NULL:value);}queue.offer(row);}catch(Exception ignored){}}
  public void route(RouteModel route,long revision,boolean replay,long time){
    if(route==null){event("route",time,"revision",revision,"available",false);return;}
    try{JSONArray points=new JSONArray();for(int i=0;i<route.x.length;i++)points.put(new JSONArray(new double[]{route.lat0+Math.toDegrees(route.y[i]/6371000),route.lon0+Math.toDegrees(route.x[i]/(6371000*route.cosLat))}));JSONArray maneuvers=new JSONArray();for(RouteModel.Maneuver m:route.maneuvers)maneuvers.put(new JSONArray().put(m.progress).put(String.valueOf(m.direction)).put(m.exit));event("route",time,"revision",revision,"name",route.name,"replay",replay,"coordinates_lat_lon",points,"maneuvers_progress_turn_exit",maneuvers);}catch(Exception ignored){}
  }
  public synchronized void flush(){
    if(closed||full)return;try(FileOutputStream out=new FileOutputStream(file,true)){
      long length=file.length();JSONObject row;while((row=queue.poll())!=null){byte[] bytes=(row.toString()+"\n").getBytes(StandardCharsets.UTF_8);if(length+bytes.length>LIMIT){out.write("{\"event\":\"log_limit_reached\"}\n".getBytes(StandardCharsets.UTF_8));full=true;queue.clear();break;}out.write(bytes);length+=bytes.length;}
    }catch(Exception e){error="记录写入失败";queue.clear();}
  }
  public synchronized void close(long time){if(closed)return;event("ride_end",time);flush();closed=true;}
  public static File export(Context context)throws Exception{
    String name=context.getSharedPreferences("navigation",0).getString("last_ride_log","");if(!name.matches("ride-[0-9]+\\.jsonl"))throw new IOException("尚无骑行记录，请先开始一次骑行");
    File source=new File(new File(context.getFilesDir(),"rides"),name);if(!source.isFile())throw new IOException("骑行记录不存在");
    RideLog active=RideService.activeLog;if(active!=null&&active.file.equals(source)){synchronized(active){active.flush();return zip(context,source,name);}}
    return zip(context,source,name);
  }
  private static File zip(Context context,File source,String name)throws Exception{
    File directory=new File(context.getCacheDir(),"ride-exports");directory.mkdirs();File[] old=directory.listFiles();if(old!=null)for(File f:old)if(f.getName().matches("ride-[0-9]+\\.zip"))f.delete();
    File target=new File(directory,name.replace(".jsonl",".zip"));long remaining=Math.min(source.length(),LIMIT+1000);
    try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(target));InputStream in=new FileInputStream(source)){
      zip.putNextEntry(new ZipEntry("diagnostics.jsonl"));byte[] bytes=new byte[8192];int n;while(remaining>0&&(n=in.read(bytes,0,(int)Math.min(bytes.length,remaining)))!=-1){zip.write(bytes,0,n);remaining-=n;}zip.closeEntry();
      zip.putNextEntry(new ZipEntry("说明.txt"));zip.write(("VELO 1.0.0 本地骑行诊断\n包含精确位置、路线、速度、定位精度、转向阶段和连接状态，请自行选择分享对象。\n模拟回放标记 replay=true，不代表实际骑行。\n仅保留最近 5 次；单次记录上限约 8 MB，达到上限后停止记录。\n骑行中导出只包含导出时已有记录；进程异常终止可能没有 ride_end。\n当前阶段不支持由记录恢复骑行，或自动偏航重算。\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }return target;
  }
}
