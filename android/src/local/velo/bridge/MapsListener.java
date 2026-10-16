package local.velo.bridge;
import android.app.Notification;
import android.os.SystemClock;
import android.service.notification.*;

public class MapsListener extends NotificationListenerService {
  static volatile NavParser.Turn turn=new NavParser.Turn('N',-1);
  static volatile long updated=0;
  static volatile String key="";
  static volatile boolean connected=false;
  static volatile String diagnostic="通知监听尚未连接；请检查通知访问开关";
  static volatile String preview="尚未收到 Google 地图通知";
  static volatile MapsListener instance;
  static volatile int received=0;
  @Override public void onListenerConnected() {
    connected=true; instance=this; diagnostic="通知监听已连接，等待 Google 地图通知";
    refreshActive();
  }
  void refreshActive() {
    StatusBarNotification[] active=getActiveNotifications();
    if(active!=null)for(StatusBarNotification n:active)onNotificationPosted(n);
  }
  @Override public void onNotificationPosted(StatusBarNotification item) {
    if(!"com.google.android.apps.maps".equals(item.getPackageName()))return;
    Notification n=item.getNotification();
    // Some Maps versions do not mark navigation notifications as ongoing.
    // Preview is only held in memory for on-device troubleshooting, never logged.
    CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE,"");
    CharSequence text=n.extras.getCharSequence(Notification.EXTRA_TEXT,"");
    CharSequence big=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT,"");
    CharSequence[] lines=n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
    String detail=title+"\n"+text+"\n"+big;
    if(lines!=null)for(CharSequence line:lines)detail+="\n"+line;
    received++; preview=detail.length()>1200?detail.substring(0,1200):detail;
    NavParser.Turn parsed=NavParser.parse(title+" "+text);
    if(parsed.direction=='N')parsed=NavParser.parse(title+" "+big);
    if(parsed.direction=='N' && lines!=null)for(CharSequence line:lines){
      NavParser.Turn candidate=NavParser.parse(String.valueOf(line));
      if(candidate.direction!='N'){parsed=candidate;break;}
    }
    diagnostic=parsed.direction=='N'?"已收到地图通知，但没有识别到转向（见下方原文）":"已识别转向 "+parsed.direction+" / "+parsed.meters+" m";
    // Do not let an unrelated non-navigation Maps notification erase a turn.
    if(parsed.direction=='N' && !item.isOngoing() && !item.getKey().equals(key))return;
    key=item.getKey(); turn=parsed; updated=SystemClock.elapsedRealtime();
  }
  @Override public void onNotificationRemoved(StatusBarNotification item) {
    if(item.getKey().equals(key)) {turn=new NavParser.Turn('N',-1);updated=0;key="";}
  }
  @Override public void onListenerDisconnected() {connected=false;instance=null;diagnostic="通知监听已断开，请重新开启通知访问";turn=new NavParser.Turn('N',-1);updated=0;}
}
