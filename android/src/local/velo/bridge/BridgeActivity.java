package local.velo.bridge;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;

public class BridgeActivity extends Activity {
  final int INK=Color.rgb(24,36,55),GREEN=Color.rgb(18,93,80),MUTED=Color.rgb(117,128,146),PAPER=Color.rgb(246,248,245);
  TextView routeInfo,status,connection,message,originText,destinationText;
  Button planButton,startButton;
  RoutePreview preview,fullMap;Dialog mapDialog;LocationManager mapLocations;LocationListener mapLocationListener;Handler handler=new Handler();
  Place origin,destination;volatile boolean planning=false;boolean draftChanged=false;
  LocationManager locator;LocationListener locating;
  static final class Place {final double lat,lon;final String name;Place(String n,double a,double o){name=n;lat=a;lon=o;}}
  Runnable refresh=new Runnable(){public void run(){
    RouteModel r=RouteStore.route;
    connection.setText(RideService.connected?"●  码表已连接":RideService.running?"○  正在连接码表":"○  码表待连接");connection.setTextColor(RideService.connected?GREEN:MUTED);
    routeInfo.setText(draftChanged?"地点已选中 · 等待规划":r==null?"尚未规划路线":r.name+"\n"+String.format(Locale.CHINA,"%.2f km",r.length/1000)+(r.plannedSeconds>0?"  ·  约 "+(int)Math.ceil(r.plannedSeconds/60)+" 分钟":"")+"  ·  "+(RouteStore.replay?"模拟回放":r.roadInstructions?"自行车路线":"GPX 轨迹"));
    status.setText(RideService.running?RideService.status+"\n"+RideService.directionStatus+"\n"+RideService.roadStatus:draftChanged?"点击规划自行车路线，确认后再开始骑行。":r==null?"选好起点与目的地，规划你的下一程。":"路线已载入，确认预览后即可发送到码表。");
    startButton.setEnabled(!planning && (RideService.running || (!draftChanged && r!=null)));startButton.setText(RideService.running?"骑行中 · 查看码表":draftChanged?"请先规划新路线":"开始骑行  →");syncMaps();handler.postDelayed(this,1000);
  }};
  int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  TextView text(String content,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(content);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",0));return v;}
  LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
  void space(LinearLayout root,int height){root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
  LinearLayout card(LinearLayout root){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(surface(Color.WHITE,22));root.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
  Button action(String label,boolean primary,Runnable callback){Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setBackground(surface(primary?GREEN:Color.rgb(226,239,225),16));b.setMinHeight(dp(52));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setOnClickListener(v->callback.run());return b;}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  void note(String s){message.setText(s);}
  @Override public void onCreate(Bundle state){
    super.onCreate(state);getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    RouteStore.load(this);origin=readPlace("origin");destination=readPlace("destination");draftChanged=getSharedPreferences("navigation",0).getBoolean("draft_changed",false);
    buildHome();
  }
  void buildHome(){
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);LinearLayout root=column();root.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(root);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("VELO",30,INK,true),new LinearLayout.LayoutParams(0,-2,1));
    TextView menu=text("设置  ⋯",15,GREEN,true);menu.setPadding(dp(12),dp(12),dp(6),dp(12));menu.setOnClickListener(v->settings());top.addView(menu);root.addView(top);root.addView(text("下一程，轻松出发。",15,MUTED,false));space(root,16);
    connection=text("○  码表待连接",13,MUTED,true);root.addView(connection);space(root,14);
    LinearLayout mapCard=column();mapCard.setBackground(surface(Color.rgb(18,42,33),24));mapCard.setClipToOutline(true);
    LinearLayout mapHeader=new LinearLayout(this);mapHeader.setPadding(dp(16),dp(14),dp(16),0);mapHeader.addView(text("周边地图 · 长按选点",12,Color.rgb(188,213,195),true),new LinearLayout.LayoutParams(0,-2,1));mapHeader.addView(text("N ↑",12,Color.rgb(188,213,195),true));mapCard.addView(mapHeader);preview=new RoutePreview(this);preview.listener=(lat,lon,pin)->mapPoint(lat,lon,pin);mapCard.addView(preview,new LinearLayout.LayoutParams(-1,dp(300)));Button expand=action("展开地图 · 标签 · 路线播放",false,()->openMap());mapCard.addView(expand,new LinearLayout.LayoutParams(-1,dp(48)));root.addView(mapCard);space(root,16);
    LinearLayout points=card(root);points.addView(text("从哪里出发",11,MUTED,true));originText=text(origin==null?"◎  点击获取当前位置":"✓ 起点："+origin.name,17,INK,true);originText.setPadding(0,dp(10),0,dp(16));originText.setOnClickListener(v->originOptions());points.addView(originText);
    View divider=new View(this);divider.setBackgroundColor(PAPER);points.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));space(points,14);
    points.addView(text("想骑到哪里",11,MUTED,true));destinationText=text(destination==null?"⌖  搜索目的地":"✓ 目的地："+destination.name,17,GREEN,true);destinationText.setPadding(0,dp(10),0,dp(10));destinationText.setOnClickListener(v->search(false));points.addView(destinationText);space(points,8);
    planButton=action("规划自行车路线",true,()->plan());points.addView(planButton,new LinearLayout.LayoutParams(-1,dp(52)));message=text("选中地点后会显示名称；更换地点后请重新规划。",12,MUTED,false);space(points,12);points.addView(message);space(root,16);
    LinearLayout summary=card(root);summary.addView(text("本次路线",12,MUTED,true));space(summary,8);routeInfo=text("尚未规划路线",17,INK,true);summary.addView(routeInfo);space(summary,12);status=text("",13,MUTED,false);summary.addView(status);space(root,12);
    TextView tools=text("导入 GPX  ·  路线回放  ·  连接设置",13,GREEN,true);tools.setGravity(Gravity.CENTER);tools.setPadding(0,dp(14),0,dp(14));tools.setOnClickListener(v->settings());root.addView(tools);
    TextView credit=text("路线：BRouter · © OpenStreetMap contributors\nv1.0.0  /  周边地图 · 标签 · 地图预演 · © OpenStreetMap contributors",10,MUTED,false);credit.setGravity(Gravity.CENTER);credit.setOnClickListener(v->about());root.addView(credit);space(root,90);
    FrameLayout page=new FrameLayout(this);page.addView(scroll,new FrameLayout.LayoutParams(-1,-1));LinearLayout dock=column();dock.setPadding(dp(20),dp(12),dp(20),dp(12));dock.setBackgroundColor(PAPER);startButton=action("开始骑行  →",true,()->choose());dock.addView(startButton,new LinearLayout.LayoutParams(-1,dp(54)));page.addView(dock,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(page);
  }
  void syncMaps(){preview.from=origin;preview.to=destination;preview.pending=draftChanged;preview.invalidate();if(fullMap!=null){fullMap.from=origin;fullMap.to=destination;fullMap.pending=draftChanged;fullMap.invalidate();}}
  void openMap(){
    if(mapDialog!=null&&mapDialog.isShowing())return;
    Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);mapDialog=dialog;LinearLayout page=column();page.setBackgroundColor(PAPER);page.setPadding(dp(12),dp(10),dp(12),dp(10));
    LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);Button back=action("‹ 返回",false,()->dialog.dismiss());title.addView(back,new LinearLayout.LayoutParams(dp(90),dp(45)));TextView heading=text("周边地图",21,INK,true);heading.setPadding(dp(12),0,0,0);title.addView(heading,new LinearLayout.LayoutParams(0,-2,1));TextView source=text("搜索地点",12,GREEN,true);source.setPadding(dp(8),dp(12),dp(8),dp(12));source.setOnClickListener(v->search(false));title.addView(source);page.addView(title);space(page,8);
    TextView help=text("长按地图选点或加标签 · 双指缩放 · 点标签可编辑",12,MUTED,false);page.addView(help);space(page,8);
    final RoutePreview map=new RoutePreview(this);fullMap=map;preview.suspended=true;map.setId(0x1f010001);map.listener=(lat,lon,pin)->mapPoint(lat,lon,pin);page.addView(map,new LinearLayout.LayoutParams(-1,0,1));syncMaps();space(page,8);
    LinearLayout controls=new LinearLayout(this);String[] captions={"＋","－","全路线","定位","标签"};for(int i=0;i<captions.length;i++){final int n=i;Button b=action(captions[i],false,()->{if(n==0)map.changeZoom(1);else if(n==1)map.changeZoom(-1);else if(n==2)map.fit();else if(n==3)mapPosition();else mapPins();});b.setTextSize(13);b.setPadding(dp(3),0,dp(3),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setSingleLine(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(2),0,dp(2),0);controls.addView(b,lp);}page.addView(controls);space(page,8);
    TextView playback=text("地图预演：只在手机播放，不发送到码表。",12,MUTED,false);page.addView(playback);SeekBar seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(191,211,196)));page.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));Button play=action("播放路线",true,()->{if(!map.toggleReplay())toast("请先规划路线，再开始地图预演");});LinearLayout bottomActions=new LinearLayout(this);Button routeAction=action("规划路线",false,()->plan());LinearLayout.LayoutParams leftAction=new LinearLayout.LayoutParams(0,dp(48),1);leftAction.setMargins(0,0,dp(6),0);bottomActions.addView(routeAction,leftAction);bottomActions.addView(play,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(bottomActions);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)map.seek(p/1000.0);}});
    Runnable ui=new Runnable(){public void run(){if(!dialog.isShowing()||map==null)return;routeAction.setEnabled(!planning);routeAction.setText(planning?"正在规划…":"规划路线");play.setEnabled(!planning&&RouteStore.route!=null&&!draftChanged);play.setAlpha(play.isEnabled()?1f:0.45f);seek.setEnabled(RouteStore.route!=null&&!draftChanged&&!planning);seek.setProgress((int)(map.fraction()*1000));play.setText(map.replayRunning?"暂停预演":map.replayMode?"继续 / 重新预演":"播放路线");playback.setText(map.replayMode?String.format(Locale.CHINA,"地图预演 · %.0f%% · 非实际位置",map.fraction()*100):draftChanged||RouteStore.route==null?"先规划路线，再播放预演（不发送到码表）。":"地图预演：只在手机播放，不发送到码表。");handler.postDelayed(this,300);}};
    dialog.setOnDismissListener(d->{handler.removeCallbacks(ui);if(mapDialog==dialog){stopMapPosition();fullMap=null;mapDialog=null;preview.suspended=false;}preview.invalidate();});dialog.setContentView(page);dialog.show();dialog.getWindow().setLayout(-1,-1);handler.post(ui);
  }
  void mapPoint(double lat,double lon,MapPins.Pin pin){
    if(planning){toast("正在规划，请稍候再修改地点");return;}String name=pin==null?String.format(Locale.US,"地图选点 %.6f, %.6f",lat,lon):pin.name;
    String[] choices=pin==null?new String[]{"设为目的地","设为起点","添加标签"}:new String[]{"设为目的地","设为起点","重命名标签","删除标签"};
    new AlertDialog.Builder(this).setTitle(name).setItems(choices,(d,n)->{if(n<=1){select(n==1,new Place(name,lat,lon));syncMaps();toast(n==0?"目的地已选中，返回首页规划路线":"起点已选中，返回首页规划路线");}else if(n==2)editPin(pin,lat,lon);else new AlertDialog.Builder(this).setTitle("删除标签？").setMessage(name).setPositiveButton("删除",(dd,nn)->{try{MapPins.remove(this,pin.id);syncMaps();}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("取消",null).show();}).setNegativeButton("取消",null).show();
  }
  void editPin(MapPins.Pin pin,double lat,double lon){EditText input=new EditText(this);input.setSingleLine(true);input.setText(pin==null?"":pin.name);input.setHint("例如 补水点、休息点");AlertDialog dialog=new AlertDialog.Builder(this).setTitle(pin==null?"添加地图标签":"重命名标签").setView(input).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{MapPins.save(this,pin==null?null:pin.id,input.getText().toString(),lat,lon);dialog.dismiss();syncMaps();toast("标签已保存在手机");}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();}
  void mapPins(){java.util.List<MapPins.Pin> pins=MapPins.load(this);if(pins.isEmpty()){toast("长按地图，再选择“添加标签”");return;}String[] names=new String[pins.size()];for(int i=0;i<pins.size();i++)names[i]=pins.get(i).name;new AlertDialog.Builder(this).setTitle("我的地图标签").setItems(names,(d,n)->{MapPins.Pin p=pins.get(n);fullMap.center(p.lat,p.lon,17);mapPoint(p.lat,p.lon,p);}).setNegativeButton("关闭",null).show();}
  void mapSource(){
    String[] names={"清晰街道（默认）","简洁浅色","夜间地图","自定义矢量样式地址"};String[] styles={VectorMap.DEFAULT,"https://tiles.openfreemap.org/styles/positron","https://tiles.openfreemap.org/styles/dark"};
    new AlertDialog.Builder(this).setTitle("地图显示风格").setItems(names,(d,n)->{if(n<3){getSharedPreferences("navigation",0).edit().putString("map_vector_style",styles[n]).remove("map_vector_credit").apply();syncMaps();return;}
      LinearLayout body=column();body.setPadding(dp(18),dp(8),dp(18),dp(8));EditText url=new EditText(this);url.setTextSize(13);url.setText(getSharedPreferences("navigation",0).getString("map_vector_style",VectorMap.DEFAULT));body.addView(url);EditText credit=new EditText(this);credit.setTextSize(13);credit.setText(getSharedPreferences("navigation",0).getString("map_vector_credit","© OpenMapTiles · © OpenStreetMap contributors"));body.addView(credit);
      AlertDialog edit=new AlertDialog.Builder(this).setTitle("MapLibre 矢量样式 HTTPS 地址").setView(body).setPositiveButton("保存",null).setNegativeButton("取消",null).create();edit.setOnShowListener(v->edit.getButton(-1).setOnClickListener(b->{try{String s=url.getText().toString().trim(),c=credit.getText().toString().trim();VectorMap.validate(s);if(c.isEmpty()||c.length()>70)throw new IOException("请填写地图版权署名");getSharedPreferences("navigation",0).edit().putString("map_vector_style",s).putString("map_vector_credit",c).apply();edit.dismiss();syncMaps();}catch(Exception e){url.setError(e.getMessage());}}));edit.show();
    }).setNegativeButton("关闭",null).show();
  }

  void mapPosition(){
    if(fullMap==null)return;fullMap.live();if(!RouteStore.replay&&RideService.previewFix!=null&&RideService.previewRoute==RouteStore.route){fullMap.zoom=Math.max(fullMap.zoom,16);fullMap.invalidate();return;}
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){permissions();toast("允许精确定位后再点击定位");return;}
    stopMapPosition();mapLocations=(LocationManager)getSystemService(LOCATION_SERVICE);mapLocationListener=new LocationListener(){public void onLocationChanged(Location p){if(p.hasAccuracy()&&p.getAccuracy()<=50&&SystemClock.elapsedRealtime()-p.getElapsedRealtimeNanos()/1000000<30000&&fullMap!=null){fullMap.deviceLocation=new Location(p);fullMap.center(p.getLatitude(),p.getLongitude(),16);stopMapPosition();}}public void onProviderDisabled(String p){}public void onProviderEnabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};
    boolean any=false;for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{if(mapLocations.isProviderEnabled(provider)){Location last=mapLocations.getLastKnownLocation(provider);if(last!=null){mapLocationListener.onLocationChanged(last);if(mapLocationListener==null)return;}mapLocations.requestLocationUpdates(provider,1000,0,mapLocationListener);any=true;}}catch(Exception ignored){}
    if(!any){stopMapPosition();toast("请打开手机定位，或搜索地点后在地图查看");return;}toast("正在定位…");handler.postDelayed(()->{if(mapLocationListener!=null){stopMapPosition();toast("暂未取得准确位置，请到室外重试");}},20000);
  }
  void stopMapPosition(){try{if(mapLocations!=null&&mapLocationListener!=null)mapLocations.removeUpdates(mapLocationListener);}catch(Exception ignored){}mapLocationListener=null;}

  void originOptions(){if(planning){toast("正在规划，请稍候");return;}new AlertDialog.Builder(this).setTitle("选择起点").setItems(new String[]{"使用手机当前位置","搜索起点 / 输入经纬度"},(d,n)->{if(n==0)locate();else search(true);}).show();}
  Place readPlace(String key){try{org.json.JSONObject p=new org.json.JSONObject(getSharedPreferences("navigation",0).getString(key,""));double lat=p.getDouble("lat"),lon=p.getDouble("lon");if(PlaceCoordinates.parse(lat+","+lon)==null)return null;return new Place(p.getString("name"),lat,lon);}catch(Exception e){return null;}}
  void select(boolean start,Place p){
    if(planning)return;if(start){origin=p;originText.setText("✓ 起点："+p.name);}else{destination=p;destinationText.setText("✓ 目的地："+p.name);}
    draftChanged=true;try{org.json.JSONObject saved=new org.json.JSONObject();saved.put("name",p.name);saved.put("lat",p.lat);saved.put("lon",p.lon);getSharedPreferences("navigation",0).edit().putString(start?"origin":"destination",saved.toString()).putBoolean("draft_changed",true).apply();
      org.json.JSONArray history=new org.json.JSONArray(getSharedPreferences("navigation",0).getString("places_recent","[]")),next=new org.json.JSONArray();next.put(saved);for(int i=0;i<history.length()&&next.length()<10;i++){org.json.JSONObject old=history.getJSONObject(i);if(Math.abs(old.getDouble("lat")-p.lat)>0.000001||Math.abs(old.getDouble("lon")-p.lon)>0.000001)next.put(old);}getSharedPreferences("navigation",0).edit().putString("places_recent",next.toString()).apply();
    }catch(Exception ignored){}
    note((start?"起点":"目的地")+"已选中："+p.name+"。点击“规划自行车路线”继续。");startButton.setEnabled(false);
  }
  void search(boolean start){
    if(planning){toast("正在规划，请稍候");return;}
    Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout page=column();page.setPadding(dp(20),dp(18),dp(20),dp(12));page.setBackgroundColor(PAPER);
    Button back=action("‹ 返回 · "+(start?"选择起点":"选择目的地"),false,()->dialog.dismiss());page.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));space(page,16);
    EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(18);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint("地名、地址、小数或度分秒坐标");input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);page.addView(input,new LinearLayout.LayoutParams(-1,dp(60)));
    TextView state=text("输入地点后点搜索；点选结果并确认，即可选中。",13,MUTED,false);space(page,8);page.addView(state);space(page,10);
    Button go=action("搜索地点",true,()->{});page.addView(go,new LinearLayout.LayoutParams(-1,dp(50)));space(page,10);
    ListView list=new ListView(this);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));TextView credit=text("Photon / 系统搜索备用 · © OpenStreetMap contributors\n免费公共服务，地点覆盖和可用性有限；仅点击搜索时联网。",11,MUTED,false);page.addView(credit);
    ArrayList<Place> results=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){public View getView(int position,View convert,ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTextColor(INK);v.setTextSize(16);v.setPadding(dp(8),dp(16),dp(8),dp(16));return v;}};list.setAdapter(adapter);
    try{org.json.JSONArray history=new org.json.JSONArray(getSharedPreferences("navigation",0).getString("places_recent","[]"));for(int i=0;i<history.length();i++){org.json.JSONObject p=history.getJSONObject(i);Place place=new Place(p.getString("name"),p.getDouble("lat"),p.getDouble("lon"));results.add(place);labels.add("最近："+place.name+String.format(Locale.US,"\n%.6f, %.6f",place.lat,place.lon));}adapter.notifyDataSetChanged();}catch(Exception ignored){}
    list.setOnItemClickListener((parent,row,position,id)->{Place p=results.get(position);new AlertDialog.Builder(this).setTitle(start?"确认起点":"确认目的地").setMessage(p.name+String.format(Locale.US,"\n纬度 %.6f\n经度 %.6f",p.lat,p.lon)).setPositiveButton(start?"设为起点":"设为目的地",(d,n)->{select(start,p);dialog.dismiss();}).setNegativeButton("返回结果",null).show();});
    Runnable request=()->{
      String query=input.getText().toString().trim();if(query.length()<2){input.setError("请输入地点或坐标");return;}((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
      double[] xy=PlaceCoordinates.parse(query);results.clear();labels.clear();adapter.notifyDataSetChanged();
      if(xy!=null){results.add(new Place((start?"坐标起点 ":"坐标目的地 ")+query,xy[0],xy[1]));labels.add("坐标："+query+"\n点此确认" );adapter.notifyDataSetChanged();state.setText("坐标有效，点击下方结果确认选中。");return;}
      if(query.contains("°")||query.contains("º")||(query.contains(",")||query.contains("，")||query.trim().contains(" "))&&query.matches("[+\\-0-9.,，\\s]+")){state.setText("坐标格式不正确。支持小数经纬度、度分秒；请检查分秒范围和 N/S/E/W。");return;}
      go.setEnabled(false);input.setEnabled(false);state.setText("正在搜索地点…");final Place bias=origin;
      new Thread(()->{try{List<PlaceSearch.Entry> matches;boolean fallback=false;try{matches=PlaceSearch.search(getSharedPreferences("navigation",0).getString("search_endpoint",PlaceSearch.DEFAULT_ENDPOINT),query,bias==null?null:bias.lat,bias==null?null:bias.lon);}catch(Exception primary){matches=systemSearch(query);fallback=true;}if(matches.isEmpty()&&!fallback){matches=systemSearch(query);fallback=true;}final List<PlaceSearch.Entry> found=matches;final boolean backup=fallback;
        runOnUiThread(()->{if(!dialog.isShowing()||isFinishing())return;for(PlaceSearch.Entry p:found){results.add(new Place(p.name,p.lat,p.lon));labels.add(p.label());}adapter.notifyDataSetChanged();state.setText(found.isEmpty()?"未找到地点。试试当地语言名称、完整地址或加上城市名。":(backup?"备用系统搜索：":"Photon 搜索：")+"找到 "+found.size()+" 个地点，请核对地址后点选并确认。");});
      }catch(Exception e){runOnUiThread(()->{if(dialog.isShowing())state.setText("搜索未完成："+(e instanceof java.net.SocketTimeoutException?"网络超时，请重试":e.getMessage())+"。也可使用最近地点或坐标。");});}
      finally{runOnUiThread(()->{if(dialog.isShowing()){go.setEnabled(true);input.setEnabled(true);}});}},"place-search").start();
    };go.setOnClickListener(v->request.run());input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){if(go.isEnabled())request.run();return true;}return false;});
    dialog.setContentView(page);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();dialog.getWindow().setLayout(-1,-1);back.requestFocus();
  }
  List<PlaceSearch.Entry> systemSearch(String query)throws Exception{
    if(!Geocoder.isPresent())throw new IOException("公共搜索暂不可达，手机也没有备用地名搜索服务");
    java.util.concurrent.FutureTask<List<Address>> task=new java.util.concurrent.FutureTask<>(()->new Geocoder(this,Locale.getDefault()).getFromLocationName(query,8));
    Thread worker=new Thread(task,"system-geocoder");worker.setDaemon(true);worker.start();
    try{List<Address> addresses=task.get(8,java.util.concurrent.TimeUnit.SECONDS);List<PlaceSearch.Entry> found=new ArrayList<>();if(addresses!=null)for(Address a:addresses){if(!a.hasLatitude()||!a.hasLongitude()||PlaceCoordinates.parse(a.getLatitude()+","+a.getLongitude())==null)continue;String name=a.getFeatureName();if(name==null||name.trim().isEmpty())name=query;found.add(new PlaceSearch.Entry(name,a.getAddressLine(0)==null?"":a.getAddressLine(0),a.getLatitude(),a.getLongitude()));}return found;}
    catch(java.util.concurrent.TimeoutException e){task.cancel(true);throw new IOException("公共搜索和备用搜索均未响应，请检查网络后重试");}
  }
  void locate(){
    if(planning)return;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){permissions();toast("允许精确定位后，再点击当前位置");return;}
    stopLocating();locator=(LocationManager)getSystemService(LOCATION_SERVICE);note("正在获取当前位置，室内可用“搜索起点”先测试。");
    locating=new LocationListener(){public void onLocationChanged(Location l){if(l.hasAccuracy()&&l.getAccuracy()<=50&&SystemClock.elapsedRealtime()-l.getElapsedRealtimeNanos()/1000000<30000){select(true,new Place(String.format(Locale.US,"当前位置 %.5f, %.5f",l.getLatitude(),l.getLongitude()),l.getLatitude(),l.getLongitude()));stopLocating();}}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};
    boolean requested=false;for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{if(locator.isProviderEnabled(provider)){Location last=locator.getLastKnownLocation(provider);if(last!=null){locating.onLocationChanged(last);if(locating==null)return;}locator.requestLocationUpdates(provider,1000,0,locating);requested=true;}}catch(Exception ignored){}
    if(!requested){stopLocating();note("请打开手机定位，或通过搜索选择起点。");return;}handler.postDelayed(()->{if(locating!=null){stopLocating();note("暂未取得准确定位；可到室外重试，或搜索起点。");}},45000);
  }
  void stopLocating(){try{if(locator!=null&&locating!=null)locator.removeUpdates(locating);}catch(Exception ignored){}locating=null;}
  void plan(){
    if(planning)return;if(destination==null){search(false);return;}if(origin==null){locate();return;}
    Place from=origin,to=destination;planning=true;stopLocating();planButton.setEnabled(false);planButton.setText("正在规划…");note("正在获取自行车路线与路口指令…");
    new Thread(()->{try{BikePlanner.Result result=BikePlanner.plan(this,from.lat,from.lon,to.lat,to.lon,to.name);RouteStore.saveOnline(this,result);runOnUiThread(()->{draftChanged=false;getSharedPreferences("navigation",0).edit().putBoolean("draft_changed",false).apply();note("目的地："+to.name+"。路线已保存，请确认预览后开始骑行。");toast("自行车路线已规划完成");});}
    catch(Exception e){runOnUiThread(()->{note("规划失败："+e.getMessage()+"。原有路线仍保留。");toast("规划失败："+e.getMessage());});}finally{runOnUiThread(()->{planning=false;planButton.setEnabled(true);planButton.setText("规划自行车路线");});}},"bike-planner").start();
  }
  void settings(){
    String[] options={"蓝牙配对","授予定位 / 蓝牙权限","导入 GPX","回放当前路线（模拟）","载入虚构路线回放","切回真实 GPS 跟随","结束骑行","路线服务地址","地点搜索服务地址","导出最近骑行诊断","周边地图与路线播放","地图来源（高级）","反向行驶提醒","码表周边道路","关于与数据来源"};
    new AlertDialog.Builder(this).setTitle("骑行工具").setItems(options,(d,n)->{
      if(planning&&n>=2){toast("请等待规划完成");return;}
      if(n==0)startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));else if(n==1)permissions();
      else if(n==2){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,42);}
      else if(n==3){if(RouteStore.route==null){toast("请先规划或导入路线");return;}draftChanged=false;getSharedPreferences("navigation",0).edit().putBoolean("draft_changed",false).apply();RouteStore.set(RouteStore.route,true);if(!RideService.running)choose();}
      else if(n==4){draftChanged=false;getSharedPreferences("navigation",0).edit().putBoolean("draft_changed",false).apply();RouteStore.set(RouteModel.demo(),true);if(!RideService.running)choose();}
      else if(n==5){if(RouteStore.route==null){toast("请先规划或导入路线");return;}if(RouteStore.route.name.equals("室内回放路线（虚构）")){toast("虚构路线不能用于真实导航，请先规划路线");return;}RouteStore.set(RouteStore.route,false);if(!RideService.running)choose();}
      else if(n==6)stopService(new Intent(this,RideService.class));else if(n==7)endpoint();else if(n==8)searchEndpoint();else if(n==9)exportDiagnostics();else if(n==10)openMap();else if(n==11)mapSource();else if(n==12)directionAlerts();else if(n==13)boardRoads();else about();
    }).setNegativeButton("关闭",null).show();
  }
  void boardRoads(){new AlertDialog.Builder(this).setTitle("码表周边道路").setMessage("手机通过 OpenFreeMap / OpenStreetMap 加载当前位置附近道路，服务会收到所查看的地图区域。根据所在区域加载矢量瓦片，缓存最近区域；失败时仍可显示原路线。码表优先显示附近路口，细节会简化，不代表所有道路均可骑行。").setPositiveButton("加载周边道路",(d,n)->getSharedPreferences("navigation",0).edit().putBoolean("board_roads",true).apply()).setNegativeButton("仅显示路线",(d,n)->getSharedPreferences("navigation",0).edit().putBoolean("board_roads",false).apply()).show();}
  void directionAlerts(){new AlertDialog.Builder(this).setTitle("反向行驶提醒").setMessage("连续 GPS 位移确认走反后提醒；停车不判断朝向，模拟回放不报警。系统静音时不发声或振动。").setPositiveButton("声音和振动",(d,n)->getSharedPreferences("navigation",0).edit().putString("wrong_way_alert","both").apply()).setNeutralButton("仅振动",(d,n)->getSharedPreferences("navigation",0).edit().putString("wrong_way_alert","vibrate").apply()).setNegativeButton("关闭提醒",(d,n)->getSharedPreferences("navigation",0).edit().putString("wrong_way_alert","off").apply()).show();}
  void exportDiagnostics(){
    new AlertDialog.Builder(this).setTitle("导出骑行诊断").setMessage("记录包含精确位置、路线、速度、定位精度和转向提示。只保存在本机；导出后由你选择保存或分享位置。\n\n保留最近 5 次，单次上限约 8 MB。模拟回放会明确标记。此记录用于排查导航，暂不支持恢复骑行。").setPositiveButton("生成诊断文件",(d,n)->{
      note("正在整理最近一次骑行诊断…");new Thread(()->{try{RideLog active=RideService.activeLog;if(active!=null)active.flush();File file=RideLog.export(this);android.net.Uri uri=android.net.Uri.parse("content://local.velo.bridge.diagnostics/"+file.getName());runOnUiThread(()->{if(isFinishing())return;Intent share=new Intent(Intent.ACTION_SEND);share.setType("application/zip");share.putExtra(Intent.EXTRA_STREAM,uri);share.setClipData(ClipData.newRawUri("VELO 骑行诊断",uri));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"保存或分享骑行诊断"));note("诊断文件已生成。");});}catch(Exception e){runOnUiThread(()->note("诊断导出未完成："+e.getMessage()));}},"ride-export").start();
    }).setNegativeButton("取消",null).show();
  }
  void searchEndpoint(){EditText input=new EditText(this);input.setText(getSharedPreferences("navigation",0).getString("search_endpoint",PlaceSearch.DEFAULT_ENDPOINT));new AlertDialog.Builder(this).setTitle("Photon 兼容 HTTPS 搜索接口").setView(input).setPositiveButton("保存",(d,n)->{try{String value=input.getText().toString().trim();PlaceSearch.url(value,"test",null,null);getSharedPreferences("navigation",0).edit().putString("search_endpoint",value).apply();toast("搜索接口已保存");}catch(Exception e){toast(e.getMessage());}}).setNeutralButton("恢复默认",(d,n)->getSharedPreferences("navigation",0).edit().remove("search_endpoint").apply()).setNegativeButton("取消",null).show();}
  void endpoint(){EditText input=new EditText(this);input.setText(BikePlanner.endpoint(this));input.setSingleLine(true);new AlertDialog.Builder(this).setTitle("BRouter 兼容 HTTPS 接口").setView(input).setPositiveButton("保存",(d,n)->{try{BikePlanner.requestUrl(input.getText().toString().trim(),35,139,35.01,139.01);getSharedPreferences("navigation",0).edit().putString("endpoint",input.getText().toString().trim()).apply();toast("服务地址已保存");}catch(Exception e){toast(e.getMessage());}}).setNeutralButton("恢复默认",(d,n)->getSharedPreferences("navigation",0).edit().remove("endpoint").apply()).setNegativeButton("取消",null).show();}
  void about(){new AlertDialog.Builder(this).setTitle("VELO 1.0.0").setMessage("界面参考 BRouter-web 的规划流程与 Material 的层级，使用原生 Android 自行实现，未移植它们的 UI 源码。\n\n路线：BRouter trekking / OpenStreetMap。规划时起终点发送至路线服务；地点搜索使用 Photon / OpenStreetMap，搜索词与起点附近坐标（若已选择）会发送至搜索服务。公共搜索不提供 Google 同等覆盖。搜索与规划手动请求；地图根据可见区域加载。下载路线后可离线跟随，但不能保证离线底图可用。公共服务无可用性保证。码表周边道路由 OpenFreeMap / OpenStreetMap 提供，骑行和真实路线回放时将所查看的地图区域发送至 tiles.openfreemap.org，按移动区域加载矢量地图并缓存；可在设置关闭。\n\n连续定位与航向辅助匹配，转弯分阶段提示；位置不确定时暂停明确转向。码表随航向转动 · 手机采用 MapLibre + OpenFreeMap 矢量底图，文字与道路按屏幕分辨率渲染，支持在线街道地图、地图选点、标签与本地路线预演；地图请求会向底图服务发送当前查看区域。偏航需手动重新规划。骑行诊断仅保存在手机，最多保留最近 5 次，可从设置手动导出。环岛按服务返回的出口编号提示；未知出口不猜测。路线可能包含需下车推行路段。\n\n数据 © OpenStreetMap contributors，ODbL。\nhttps://www.openstreetmap.org/copyright\nhttps://github.com/abrensch/brouter\nhttps://github.com/nrenner/brouter-web\nhttps://github.com/material-components/material-components-android").setNeutralButton("开源许可",(d,n)->licenses()).setPositiveButton("知道了",null).show();}
  void licenses(){try{StringBuilder text=new StringBuilder();for(String name:new String[]{"BRouter-LICENSE.txt","MapLibre-LICENSE.txt"}){text.append(name).append("\n\n");try(InputStream in=getAssets().open("notices/"+name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);text.append(new String(bytes.toByteArray(),"UTF-8")).append("\n\n");}}new AlertDialog.Builder(this).setTitle("BRouter / MapLibre 开源许可").setMessage(text.toString()).setPositiveButton("关闭",null).show();}catch(Exception e){toast("许可说明读取失败");}}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(request!=42||result!=RESULT_OK||data==null)return;android.net.Uri uri=data.getData();note("正在读取 GPX…");
    new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
      byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("文件超过 4 MB");bytes.write(chunk,0,n);}byte[] raw=bytes.toByteArray();RouteModel route=RouteStore.parse(new ByteArrayInputStream(raw));File temp=new File(getFilesDir(),"route.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(raw);}if(!temp.renameTo(new File(getFilesDir(),"route.gpx")))throw new IOException("保存失败");
      getSharedPreferences("navigation",0).edit().putString("last_source","gpx").commit();RouteStore.set(route,false);runOnUiThread(()->{draftChanged=false;getSharedPreferences("navigation",0).edit().putBoolean("draft_changed",false).apply();note("GPX 已载入，可开始跟随或从工具中回放。");});
    }catch(Exception e){runOnUiThread(()->note("导入失败："+e.getMessage()));}},"gpx-import").start();
  }
  boolean allowed(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);}
  void permissions(){ArrayList<String> p=new ArrayList<>();p.add(Manifest.permission.ACCESS_FINE_LOCATION);p.add(Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=31)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);requestPermissions(p.toArray(new String[0]),1);}
  void choose(){
    if(planning){toast("请等待路线规划完成");return;}if(draftChanged){toast("地点已更改，请先重新规划路线");return;}if(RouteStore.route==null){toast("请先规划或载入路线");return;}if(RideService.running){toast("骑行已启动，路线会同步更新；结束请到骑行工具");return;}if(!allowed()){permissions();toast("授权后请再点开始骑行");return;}
    BluetoothAdapter adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();if(adapter==null||!adapter.isEnabled()){toast("请先打开蓝牙并配对 VELO-ESP32");return;}ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());if(devices.isEmpty()){toast("请先在设置中配对 VELO-ESP32");return;}
    String[] names=new String[devices.size()];for(int i=0;i<devices.size();i++)names[i]=devices.get(i).getName()+"\n"+devices.get(i).getAddress();new AlertDialog.Builder(this).setTitle("连接你的码表").setItems(names,(d,i)->{Intent start=new Intent(this,RideService.class);start.putExtra("address",devices.get(i).getAddress());try{startForegroundService(start);}catch(Exception e){toast("启动失败，请检查定位与蓝牙权限");}}).setNegativeButton("取消",null).show();
  }
  @Override public void onResume(){super.onResume();preview.suspended=mapDialog!=null&&mapDialog.isShowing();if(fullMap!=null)fullMap.suspended=false;handler.post(refresh);}
  @Override public void onPause(){handler.removeCallbacks(refresh);stopLocating();stopMapPosition();preview.suspended=true;if(fullMap!=null){fullMap.replayRunning=false;fullMap.suspended=true;}super.onPause();}
  @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);stopLocating();stopMapPosition();if(mapDialog!=null)mapDialog.dismiss();super.onDestroy();}
}
