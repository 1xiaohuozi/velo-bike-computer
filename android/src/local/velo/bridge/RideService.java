package local.velo.bridge;
import android.app.*;import android.bluetooth.*;import android.content.*;import android.location.*;import android.os.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

public class RideService extends Service implements LocationListener {
  static volatile boolean paused=false;static volatile float speedKmh=-1;static volatile double tripKm;static volatile long elapsedSeconds;boolean phoneOnly;long pauseStarted,pausedTotal,sessionWall,lastCheckpoint;String recordId;final ArrayList<double[]> trace=new ArrayList<>();
  static volatile String status="尚未开始骑行";static volatile boolean running=false,connected=false;
  static volatile String directionStatus="行驶一小段后判断方向，原地转动不能判断";
  static volatile RouteModel.Fix previewFix;static volatile RouteModel previewRoute;
  static volatile RideLog activeLog;static volatile String roadStatus="周边道路待加载";NearbyRoads nearby;
  volatile boolean alive=false;volatile BluetoothSocket socket;volatile Location latest;
  Location anchor;double tripMeters;long started;
  LocationManager locations;Thread worker,navigator;NotificationManager notices;
  long routeRevision=-1,replayStarted,sequence,lastSample=-1;double replayProgress=-1;
  RouteTracker tracker;RouteModel.Fix matched;boolean lastGps;
  final TravelDirection travelDirection=new TravelDirection();TravelDirection.Result travel;boolean directionSeen;final WarningPolicy warningPolicy=new WarningPolicy();PhoneWarnings warnings;
  final HeadingFilter headingFilter=new HeadingFilter();final ViewScale viewScale=new ViewScale();RideLog log;
  static final class Frame {final String text;final long time;Frame(String s,long t){text=s;time=t;}}
  volatile Frame frame;static volatile Frame outgoing;
  Notification notification(String s){PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"ride").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("VELO 骑行导航").setContentText(s).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();}
  @Override public IBinder onBind(Intent i){return null;}
  @Override public void onCreate(){super.onCreate();warnings=new PhoneWarnings(this);nearby=new NearbyRoads(this);notices=getSystemService(NotificationManager.class);notices.createNotificationChannel(new NotificationChannel("ride","骑行连接状态",NotificationManager.IMPORTANCE_LOW));}
  @Override public int onStartCommand(Intent intent,int flags,int id){
    if(intent!=null&&"velo.PAUSE".equals(intent.getAction())&&alive){long now=SystemClock.elapsedRealtime();if(!paused){paused=true;pauseStarted=now;}else{paused=false;pausedTotal+=now-pauseStarted;}synchronized(this){anchor=null;}return START_NOT_STICKY;}
    if(alive)return START_NOT_STICKY;
    if(intent==null||intent.getStringExtra("address")==null){stopSelf();return START_NOT_STICKY;}
    String address=intent.getStringExtra("address");phoneOnly="PHONE".equals(address);if(Build.VERSION.SDK_INT>=29)startForeground(1,notification("正在启动导航"),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION|(phoneOnly?0:android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE));else startForeground(1,notification("正在启动导航"));speedKmh=-1;tripKm=0;elapsedSeconds=0;paused=false;pausedTotal=0;sessionWall=System.currentTimeMillis();recordId=UUID.randomUUID().toString();trace.clear();started=SystemClock.elapsedRealtime();latest=null;anchor=null;tripMeters=0;RouteStore.load(this);
    try{log=new RideLog(this,started);activeLog=log;}catch(Exception e){log=null;activeLog=null;}
    locations=(LocationManager)getSystemService(LOCATION_SERVICE);
    try{locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}catch(Exception e){status="无法启动 GPS，请授予精确定位并打开系统定位";stopSelf();return START_NOT_STICKY;}
    alive=true;running=true;
    navigator=new Thread(()->{while(alive){try{tick();Thread.sleep(1000);}catch(InterruptedException e){break;}catch(Exception e){event("navigation_error",SystemClock.elapsedRealtime(),"reason",e.getClass().getSimpleName());frame=null;status="导航计算待恢复，请停稳检查手机";try{Thread.sleep(1000);}catch(InterruptedException interrupted){break;}}}},"velo-navigation");navigator.start();
    if(!phoneOnly)startForegroundService(new Intent(this,DeviceLinkService.class).putExtra("address",address));return START_NOT_STICKY;
  }
  void event(String type,long time,Object... fields){if(log!=null)log.event(type,time,fields);}
  void tick(){
    long now=SystemClock.elapsedRealtime();Location fix=latest;long sample=fix==null?-1:fix.getElapsedRealtimeNanos();
    boolean gps=fix!=null&&now-sample/1000000<5000&&now-sample/1000000>=-1000;
    float speed=gps&&fix.hasSpeed()?Math.max(0,Math.min(150,fix.getSpeed()*3.6f)):-1;
    double distance;synchronized(this){distance=tripMeters;}
    RouteStore.Selection selection=RouteStore.snapshot();RouteModel route=selection.route;boolean replay=selection.replay;
    if(routeRevision!=selection.revision){routeRevision=selection.revision;replayStarted=now;lastSample=-1;replayProgress=-1;tracker=route==null?null:new RouteTracker(route);matched=null;travelDirection.reset();travel=null;directionSeen=false;warningPolicy.reset();headingFilter.reset();viewScale.reset();if(log!=null)log.route(route,routeRevision,replay,now);}
    RouteModel.Fix routeFix=null;Double bearing=null;boolean moving=false,reliable=false;
    if(route!=null){
      if(replay){
        double traveled=((now-replayStarted)/1000.0*8)%(route.length+32);if(traveled<replayProgress){tracker=new RouteTracker(route);headingFilter.reset();viewScale.reset();}replayProgress=traveled;
        RouteModel.Fix position=route.at(Math.min(route.length,traveled));moving=traveled<route.length;speed=moving?28.8f:0;distance=position.progress;bearing=moving?Double.valueOf(position.heading):null;
        routeFix=tracker.update(position.x,position.y,3,moving?8:0,bearing,now);matched=routeFix;
      }else if(gps){
        if(sample!=lastSample){double[] point=route.local(fix.getLatitude(),fix.getLongitude());travel=travelDirection.update(point[0],point[1],fix.getAccuracy(),fix.hasSpeed()?fix.getSpeed():-1,fix.hasBearing()?Double.valueOf(fix.getBearing()):null,fix.hasBearingAccuracy()?Double.valueOf(fix.getBearingAccuracyDegrees()):null,sample/1000000);matched=tracker.update(point[0],point[1],fix.getAccuracy(),travel.speed,travel.course,sample/1000000);lastSample=sample;}
        moving=travel!=null&&travel.speed>=1.2;bearing=travel==null?null:travel.course;reliable=moving&&bearing!=null;if(reliable)directionSeen=true;
        routeFix=matched;
      }
    }
    if(!gps&&!replay)directionSeen=false;
    if(gps&&!fix.hasSpeed()&&travel!=null&&travel.speed>=0)speed=(float)(travel.speed*3.6);
    if(routeFix!=null){Double course=replay?bearing:reliable?bearing:null;
      routeFix.heading=headingFilter.update(routeFix.heading,course,moving,now);routeFix.headingQuality=replay?2:reliable?1:0;routeFix.viewportSpan=viewScale.update(routeFix);}
    if(routeFix!=null&&!replay){routeFix=NavPresentation.gps(routeFix,directionSeen,bearing,travel==null?-1:travel.speed);if(!directionSeen)routeFix.heading=0;}
    directionStatus=replay?"模拟回放，不是实际行进方向":!gps?"行进方向未知：等待有效 GPS":reliable?"行进方向："+TravelDirection.compass(bearing)+String.format(Locale.CHINA," %.0f°",bearing)+(travel!=null&&"gps_track".equals(travel.source)?"（连续定位判断）":"（GPS 判断）"):directionSeen&&!moving?"停车或低速：保持上次行进方向，原地转动不会更新":"行进方向待确认：请向前行驶一小段";
    if(paused&&routeFix!=null){routeFix.phase=RouteTracker.STOPPED;routeFix.turn='N';routeFix.meters=-1;}
    speedKmh=speed;tripKm=distance/1000;elapsedSeconds=Math.max(0,(now-started-pausedTotal-(paused?now-pauseStarted:0))/1000);
    previewRoute=route;previewFix=routeFix;
    char turn=routeFix==null?'N':routeFix.turn;int meters=routeFix==null?-1:routeFix.meters;
    String text=String.format(Locale.US,"D,%.1f,%.3f,%d,%c,%d\n",speed,distance/1000,elapsedSeconds,turn,meters);
    if(routeFix!=null){long seq=++sequence;boolean surroundings=!replay||!"室内回放路线（虚构）".equals(route.name);if(surroundings)nearby.update(route,routeFix);roadStatus=surroundings?nearby.status:"虚构路线回放仅显示路线";text+=route.packet(routeFix,replay,seq)+(surroundings?nearby.packet(route,routeFix,seq):"B,"+seq+",0\n")+"T,"+seq+","+routeFix.phase+","+(replay?3:Math.round(fix.getAccuracy()))+"\n";}else text+=route==null?"MAPOFF\n":"GPSWAIT\n";
    frame=new Frame(text,now);outgoing=frame;
    String direction=routeFix!=null&&routeFix.phase==RouteTracker.DIRECTION_WAIT?"向前行驶一小段后开始转向提示":turn=='L'?"左转":turn=='R'?"右转":turn=='U'?"掉头":turn=='O'?"环岛出口 "+(routeFix.exit>0?routeFix.exit:"待确认"):turn=='A'?"到达终点":turn=='N'?"请核对路线":"前行";
    status=(phoneOnly?"仅手机导航 · ":connected?"码表已连接 · ":"码表未连接 · ")+(route==null?"请先规划或载入路线":replay?"模拟回放 · ":"骑行导航 · ");
    if(route!=null)status+=routeFix==null?"等待有效 GPS":RouteTracker.phaseText(routeFix.phase)+" · "+direction+(meters<0?"":" "+meters+" m");
    if(paused)status="已暂停 · 不累计距离与骑行时长";
    if(log==null||!log.error.isEmpty())status+=" · 诊断记录不可用";else if(log.full)status+=" · 记录已达容量上限";
    if(lastGps!=gps){event("gps_state",now,"usable",gps);lastGps=gps;}
    event("navigation",now,"replay",replay,"revision",routeRevision,"gps_fresh",gps,"bluetooth",connected,"speed_kmh",speed,"trip_m",distance,"progress_m",routeFix==null?null:routeFix.progress,"cross_track_m",routeFix==null?null:routeFix.error,"heading",routeFix==null?null:routeFix.heading,"turn",String.valueOf(turn),"turn_m",meters,"phase",routeFix==null?-1:routeFix.phase,"reason",routeFix==null?"gps_wait":tracker.reason);
    boolean opposite=tracker!=null&&tracker.reverseEvidence()&&moving&&reliable;
    if(warningPolicy.update(routeFix==null?-1:routeFix.phase,replay,gps&&now-sample/1000000<2500,opposite,now)){String outcome=warnings.warn();event("wrong_way_alert",now,"outcome",outcome,"course",bearing,"route_heading",tracker.expectedHeading);}
    event("travel_direction",now,"course",bearing,"source",travel==null?"unknown":travel.source,"baseline_m",travel==null?0:travel.baseline,"expected_heading",tracker==null?null:tracker.expectedHeading,"opposite_evidence",opposite);
    if(recordId!=null&&now-lastCheckpoint>=15000){checkpoint(true);lastCheckpoint=now;}
    if(log!=null)log.flush();notices.notify(1,notification(status));
  }
  void connect(String address){
    while(alive){try{
      BluetoothAdapter adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();socket=adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket.connect();connected=true;event("bluetooth",SystemClock.elapsedRealtime(),"connected",true);
      OutputStream out=socket.getOutputStream();out.write("LIVE\n".getBytes(StandardCharsets.US_ASCII));
      while(alive){Frame current=frame;String text=current!=null&&SystemClock.elapsedRealtime()-current.time<5000?current.text:"D,-1,0,0,N,-1\nGPSWAIT\n";out.write(text.getBytes(StandardCharsets.US_ASCII));out.flush();Thread.sleep(1000);}
    }catch(Exception e){if(alive)event("bluetooth",SystemClock.elapsedRealtime(),"connected",false,"reason",e.getClass().getSimpleName());}
    finally{connected=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;}
    if(alive)try{Thread.sleep(3000);}catch(InterruptedException e){break;}}
  }
  @Override public synchronized void onLocationChanged(Location fix){
    long now=SystemClock.elapsedRealtime();String rejection="";long age=now-fix.getElapsedRealtimeNanos()/1000000;
    if(!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude())||Math.abs(fix.getLatitude())>80||Math.abs(fix.getLongitude())>180)rejection="invalid_coordinates";
    else if(!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<0||fix.getAccuracy()>25)rejection="weak_accuracy";else if(fix.isFromMockProvider())rejection="mock_location";else if(age>5000||age< -1000)rejection="stale_time";
    else if(fix.hasSpeed()&&(!Float.isFinite(fix.getSpeed())||fix.getSpeed()<0||fix.getSpeed()>41.67))rejection="invalid_speed";
    if(rejection.isEmpty()&&latest!=null){double dt=(fix.getElapsedRealtimeNanos()-latest.getElapsedRealtimeNanos())/1e9;if(dt<=0)rejection="out_of_order";else if(dt<=5&&latest.distanceTo(fix)>Math.max(35,Math.max(12,fix.hasSpeed()?fix.getSpeed()+8:12)*dt+fix.getAccuracy()*2))rejection="position_jump";}
    event("gps",now,"lat",fix.getLatitude(),"lon",fix.getLongitude(),"accuracy_m",fix.hasAccuracy()?fix.getAccuracy():null,"speed_mps",fix.hasSpeed()?fix.getSpeed():null,"bearing",fix.hasBearing()?fix.getBearing():null,"age_ms",age,"accepted",rejection.isEmpty(),"reason",rejection);
    if(!rejection.isEmpty())return;latest=new Location(fix);
    if(paused){anchor=new Location(fix);return;}
    if(!RouteStore.replay&&trace.size()<24000){double[] last=trace.isEmpty()?null:trace.get(trace.size()-1);if(last==null||fix.hasSpeed()&&fix.getSpeed()>=0.8&&RoadContext.distance(last[0],last[1],fix.getLatitude(),fix.getLongitude())>=Math.max(3,fix.getAccuracy()/2))trace.add(new double[]{fix.getLatitude(),fix.getLongitude()});}
    if(anchor==null){anchor=new Location(fix);return;}float meters=anchor.distanceTo(fix);double seconds=(fix.getElapsedRealtimeNanos()-anchor.getElapsedRealtimeNanos())/1e9;if(seconds<=0)return;
    if(seconds>15){anchor=new Location(fix);return;}if(meters/seconds>41.67)return;
    if(fix.hasSpeed()&&fix.getSpeed()>=0.8&&meters>=Math.max(3,Math.min(10,fix.getAccuracy()/2))){tripMeters+=meters;anchor=new Location(fix);}else if(fix.hasSpeed()&&fix.getSpeed()<0.8)anchor=new Location(fix);
  }
  @Override public synchronized void onProviderDisabled(String provider){latest=null;anchor=null;event("provider",SystemClock.elapsedRealtime(),"enabled",false);}
  @Override public void onProviderEnabled(String provider){event("provider",SystemClock.elapsedRealtime(),"enabled",true);}
  @Override public void onStatusChanged(String provider,int status,Bundle extras){}
  synchronized void checkpoint(boolean active){if(recordId==null||(active&&!alive))return;try{org.json.JSONArray a=new org.json.JSONArray();synchronized(this){for(double[] p:trace)a.put(new org.json.JSONArray().put(p[0]).put(p[1]));}RouteStore.Selection selection=RouteStore.snapshot();AppStore.get(this).ride(recordId,selection.route==null?"骑行记录":selection.route.name,sessionWall,elapsedSeconds,tripKm*1000,selection.replay,active,a.toString());}catch(Exception e){event("record_error",SystemClock.elapsedRealtime(),"reason",e.getClass().getSimpleName());}}
  @Override public void onDestroy(){alive=false;checkpoint(false);paused=false;running=false;outgoing=null;previewFix=null;previewRoute=null;try{if(locations!=null)locations.removeUpdates(this);}catch(Exception ignored){}try{if(socket!=null)socket.close();}catch(Exception ignored){}if(worker!=null)worker.interrupt();if(navigator!=null)navigator.interrupt();if(log!=null)log.close(SystemClock.elapsedRealtime());activeLog=null;if(warnings!=null)warnings.close();if(nearby!=null)nearby.close();directionStatus="骑行已结束，未检测当前行进方向";status=log==null?"骑行已结束，诊断记录不可用":"骑行已结束，诊断记录已保存在手机";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
