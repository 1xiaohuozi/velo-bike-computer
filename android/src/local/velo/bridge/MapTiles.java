package local.velo.bridge;
import android.content.Context;import android.graphics.*;import android.net.http.HttpResponseCache;import android.util.LruCache;import android.view.View;
import java.io.*;import java.net.*;import java.lang.ref.WeakReference;import java.util.*;import java.util.concurrent.*;
/** Only tiles in the currently visible viewport. HTTP cache honors server expiry/validators. */
public final class MapTiles {
 public static final String DEFAULT="https://tile.openstreetmap.org/{z}/{x}/{y}.png";
 static MapTiles instance;static volatile boolean noNetworkForTests=false;
 final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 final Set<String> pending=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
 final ConcurrentHashMap<String,Long> failed=new ConcurrentHashMap<>();
 final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,20,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32));
 boolean cacheReady;
 private MapTiles(Context c){try{if(HttpResponseCache.getInstalled()==null)HttpResponseCache.install(new File(c.getCacheDir(),"map-http"),64*1024*1024);cacheReady=true;}catch(IOException e){cacheReady=false;}pool.allowCoreThreadTimeOut(true);}
 public static synchronized MapTiles get(Context c){if(instance==null)instance=new MapTiles(c.getApplicationContext());return instance;}
 public static void validate(String template)throws Exception{if(!template.contains("{z}")||!template.contains("{x}")||!template.contains("{y}"))throw new IOException("地图地址需要包含 {z}、{x}、{y}");URI u=new URI(template.replace("{z}","1").replace("{x}","1").replace("{y}","1"));if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("地图来源必须使用 HTTPS");}
 String key(String source,int z,int x,int y){return source+"|"+z+"/"+x+"/"+y;}
 public Bitmap peek(String s,int z,int x,int y){return memory.get(key(s,z,x,y));}
 public boolean unavailable(String s,int z,int x,int y){Long t=failed.get(key(s,z,x,y));return !cacheReady||t!=null&&System.currentTimeMillis()-t<30000;}
 public void request(String source,int z,int x,int y,View owner){
  if(noNetworkForTests||!cacheReady||!owner.isAttachedToWindow()||!owner.isShown())return;String key=key(source,z,x,y);if(memory.get(key)!=null||unavailable(source,z,x,y)||!pending.add(key))return;
  WeakReference<View> ref=new WeakReference<>(owner);
  try{pool.execute(()->{HttpURLConnection c=null;try{View v=ref.get();if(v==null||!v.isShown()||!v.isAttachedToWindow()||v.getWindowVisibility()!=View.VISIBLE||v instanceof RoutePreview&&((RoutePreview)v).suspended)return;
    String url=source.replace("{z}",String.valueOf(z)).replace("{x}",String.valueOf(x)).replace("{y}",String.valueOf(y));c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setUseCaches(true);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","VELO-BikeDash/0.9 (personal bicycle computer; local.velo.bridge)");
    if(c.getResponseCode()!=200)throw new IOException("tile HTTP "+c.getResponseCode());ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] part=new byte[4096];int n;while((n=in.read(part))!=-1){if(bytes.size()+n>512*1024)throw new IOException("tile too large");bytes.write(part,0,n);}}
    byte[] raw=bytes.toByteArray();BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,info);if(info.outWidth<=0||info.outHeight<=0||info.outWidth>1024||info.outHeight>1024)throw new IOException("tile dimensions");Bitmap bitmap=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bitmap==null)throw new IOException("tile image");memory.put(key,bitmap);failed.remove(key);
   }catch(Exception e){if(failed.size()>256)failed.clear();failed.put(key,System.currentTimeMillis());}finally{if(c!=null)c.disconnect();pending.remove(key);View v=ref.get();if(v!=null)v.postInvalidate();}});
  }catch(RejectedExecutionException e){pending.remove(key);}
 }
}
