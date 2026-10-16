package local.velo.bridge;
import android.content.Context;import java.util.*;
public final class MapPins {
 public static final class Pin {public final String id,name,group,note;public final double lat,lon;public final int color;Pin(String i,String n,double a,double o){this(i,n,a,o,"其他","",0xff2d5be3);}Pin(String i,String n,double a,double o,String g,String t,int c){id=i;name=n;lat=a;lon=o;group=g;note=t;color=c;}}
 public static List<Pin> load(Context c){AppStore db=AppStore.get(c);try{db.migrate();}catch(Exception ignored){}return db.favorites(true);}
 public static synchronized void save(Context c,String id,String name,double lat,double lon)throws Exception{AppStore.validate(name,lat,lon);AppStore.get(c).save(new Pin(id==null?UUID.randomUUID().toString():id,name,lat,lon));}
 public static synchronized void remove(Context c,String id)throws Exception{AppStore.get(c).remove(id);}
}