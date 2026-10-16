package local.velo.bridge;
import android.content.*;import android.graphics.*;import android.location.*;import android.os.*;import android.view.*;
import java.util.*;

/** Interactive north-up street map. Local preview playback never changes RideService. */
public final class RoutePreview extends android.widget.FrameLayout {
 public interface Listener {void point(double lat,double lon,MapPins.Pin pin);}
 final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final VectorMap engine;
 final ArrayList<MainActivity.Place> via=new ArrayList<>();MainActivity.Place from,to,selection;boolean pending,suspended;Listener listener;
 int bottomInset=0;boolean showSaved=true,compactUi=false;Location deviceLocation;
 double centerX=.5,centerY=.5,zoom=2;String signature="";boolean touching,follow,multiTouch;
 boolean replayMode,replayRunning;double replayDistance;long replayTick;RouteModel replayRoute;
 final GestureDetector gesture;final ScaleGestureDetector scaling;final float density;
 public RoutePreview(Context c){super(c);density=getResources().getDisplayMetrics().density;engine=new VectorMap(c,this);addView(engine.web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentDescription("周边地图，可拖动缩放，长按选择地点和添加标签");setFocusable(true);
  android.content.SharedPreferences prefs=c.getSharedPreferences("navigation",0);centerX=MapMath.x(Double.longBitsToDouble(prefs.getLong("map_lon",Double.doubleToLongBits(0))));centerY=MapMath.y(Double.longBitsToDouble(prefs.getLong("map_lat",Double.doubleToLongBits(0))));zoom=prefs.getFloat("map_zoom",2);
  gesture=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onScroll(MotionEvent first,MotionEvent now,float dx,float dy){if(!scaling.isInProgress()){double w=world();centerX=MapMath.wrap(centerX+dx/w);centerY=clamp(centerY+dy/w);follow=false;invalidate();}return true;}
   public void onLongPress(MotionEvent e){if(listener!=null){follow=false;replayRunning=false;double[] point=coordinate(e.getX(),e.getY());listener.point(point[0],point[1],hit(e.getX(),e.getY()));}}
   public boolean onSingleTapConfirmed(MotionEvent e){if(e.getY()>getHeight()-24*density){try{getContext().startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://openfreemap.org/")));}catch(Exception ignored){}return true;}MapPins.Pin pin=hit(e.getX(),e.getY());if(pin!=null&&listener!=null)listener.point(pin.lat,pin.lon,pin);performClick();return true;}
   public boolean onDoubleTap(MotionEvent e){zoomAt(zoom+1,e.getX(),e.getY());return true;}});
  scaling=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoomAt(zoom+Math.log(d.getScaleFactor())/Math.log(2),d.getFocusX(),d.getFocusY());return true;}});
 }
 static double clamp(double y){return Math.max(0,Math.min(1,y));}double world(){return 256*density*Math.pow(2,zoom);}
 void zoomAt(double value,double px,double py){double old=world(),nx=centerX+(px-getWidth()/2.0)/old,ny=centerY+(py-getHeight()/2.0)/old;zoom=Math.max(2,Math.min(19,value));centerX=MapMath.wrap(nx-(px-getWidth()/2.0)/world());centerY=clamp(ny-(py-getHeight()/2.0)/world());follow=false;invalidate();}
 public void changeZoom(int delta){zoomAt(zoom+delta,getWidth()/2f,getHeight()/2f);}
 public void center(double lat,double lon,double level){centerX=MapMath.x(lon);zoom=Math.max(2,Math.min(19,level));centerY=clamp(MapMath.y(lat)+bottomInset/(2*world()));follow=false;invalidate();}
 double[] coordinate(double sx,double sy){return new double[]{MapMath.lat(centerY+(sy-getHeight()/2.0)/world()),MapMath.lon(centerX+(sx-getWidth()/2.0)/world())};}
 float sx(double lon){return (float)(getWidth()/2.0+MapMath.delta(MapMath.x(lon),centerX)*world());}float sy(double lat){return (float)(getHeight()/2.0+(MapMath.y(lat)-centerY)*world());}
 MapPins.Pin hit(float x,float y){for(MapPins.Pin pin:showSaved?MapPins.load(getContext()):Collections.<MapPins.Pin>emptyList()){float px=sx(pin.lon),py=sy(pin.lat);pen.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);float label=pen.measureText(pin.name.length()>14?pin.name.substring(0,14)+"…":pin.name);if(Math.hypot(px-x,py-y)<24*density||(x>=px+10*density&&x<=px+20*density+label&&Math.abs(y-py)<16*density))return pin;}return null;}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){touching=true;multiTouch=false;follow=false;replayRunning=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(e.getPointerCount()>1&&!multiTouch){multiTouch=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);gesture.onTouchEvent(cancel);cancel.recycle();}scaling.onTouchEvent(e);if(!multiTouch)gesture.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();replayRunning=false;new Handler(Looper.getMainLooper()).post(()->engine.close());getContext().getSharedPreferences("navigation",0).edit().putLong("map_lon",Double.doubleToLongBits(MapMath.lon(centerX))).putLong("map_lat",Double.doubleToLongBits(MapMath.lat(centerY))).putFloat("map_zoom",(float)zoom).apply();}
 public void fit(){
  RouteModel r=pending?null:RouteStore.route;ArrayList<double[]> pts=new ArrayList<>();if(r!=null){for(int i=0;i<r.x.length;i++)pts.add(MapMath.point(r,r.x[i],r.y[i]));}else{if(from!=null)pts.add(new double[]{from.lat,from.lon});for(MainActivity.Place v:via)pts.add(new double[]{v.lat,v.lon});if(to!=null)pts.add(new double[]{to.lat,to.lon});}
  if(pts.isEmpty()){invalidate();return;}if(pts.size()==1){center(pts.get(0)[0],pts.get(0)[1],16);return;}
  double base=MapMath.x(pts.get(0)[1]),minX=0,maxX=0,minY=MapMath.y(pts.get(0)[0]),maxY=minY;
  for(double[] p:pts){double x=MapMath.delta(MapMath.x(p[1]),base),y=MapMath.y(p[0]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
  centerX=MapMath.wrap(base+(minX+maxX)/2);centerY=(minY+maxY)/2;double width=Math.max(80*density,getWidth()-90*density),height=Math.max(80*density,getHeight()-bottomInset-110*density);
  zoom=Math.max(2,Math.min(17,Math.log(Math.min(width/Math.max(1e-8,maxX-minX),height/Math.max(1e-8,maxY-minY))/(256*density))/Math.log(2)));centerY=clamp(centerY+bottomInset/(2*world()));follow=false;invalidate();
 }
 public boolean toggleReplay(){RouteModel r=RouteStore.route;if(r==null||pending)return false;if(replayRoute!=r){replayRoute=r;replayDistance=0;}replayMode=true;if(replayDistance>=r.length)replayDistance=0;replayRunning=!replayRunning;replayTick=SystemClock.elapsedRealtime();invalidate();return true;}
 public void seek(double fraction){RouteModel r=RouteStore.route;if(r==null||pending)return;replayRoute=r;replayMode=true;replayDistance=r.length*Math.max(0,Math.min(1,fraction));replayTick=SystemClock.elapsedRealtime();invalidate();}
 public void live(){replayMode=false;replayRunning=false;follow=true;invalidate();}
 public double fraction(){return replayRoute==null?0:replayDistance/Math.max(1,replayRoute.length);}
 @Override protected void dispatchDraw(Canvas canvas){RouteModel route=RouteStore.route;
  String next=(pending||route==null?"D:"+(from==null?"":from.lat+","+from.lon)+":"+(to==null?"":to.lat+","+to.lon):"R:"+System.identityHashCode(route));
  if(!next.equals(signature)){signature=next;if(!compactUi)fit();if(replayRoute!=route||pending){replayMode=false;replayRunning=false;}}
  RouteModel.Fix fix=RideService.previewRoute==route&&!RouteStore.replay?RideService.previewFix:null;
  if(replayMode&&replayRoute==route&&!pending){long now=SystemClock.elapsedRealtime();if(replayRunning){replayDistance=Math.min(route.length,replayDistance+Math.max(0,now-replayTick)/1000.0*8);replayTick=now;if(replayDistance>=route.length)replayRunning=false;}fix=route.at(replayDistance);}
  if(follow&&!touching&&selection==null&&fix!=null){double[] p=MapMath.point(route,fix.x,fix.y);centerX=MapMath.x(p[1]);centerY=clamp(MapMath.y(p[0])+bottomInset/(2*world()));}
  engine.render(MapMath.lon(centerX),MapMath.lat(centerY),zoom);super.dispatchDraw(canvas);
  canvas.save();canvas.clipRect(0,0,getWidth(),getHeight()-24*density);
  if(route!=null&&!pending){Path path=new Path();for(int i=0;i<route.x.length;i++){double[] p=MapMath.point(route,route.x[i],route.y[i]);if(i==0)path.moveTo(sx(p[1]),sy(p[0]));else path.lineTo(sx(p[1]),sy(p[0]));}pen.setStyle(Paint.Style.STROKE);pen.setStrokeJoin(Paint.Join.ROUND);pen.setStrokeCap(Paint.Cap.ROUND);pen.setColor(Color.WHITE);pen.setStrokeWidth(8*density);canvas.drawPath(path,pen);pen.setColor(Color.rgb(45,91,227));pen.setStrokeWidth(4*density);canvas.drawPath(path,pen);pen.setStyle(Paint.Style.FILL);double[] start=MapMath.point(route,route.x[0],route.y[0]),end=MapMath.point(route,route.x[route.x.length-1],route.y[route.y.length-1]);marker(canvas,start[0],start[1],"起",Color.rgb(45,91,227));marker(canvas,end[0],end[1],"终",Color.rgb(205,69,54));}
  else{if(from!=null)marker(canvas,from.lat,from.lon,"起",Color.rgb(45,91,227));if(to!=null)marker(canvas,to.lat,to.lon,"终",Color.rgb(205,69,54));}
  for(MapPins.Pin pin:showSaved?MapPins.load(getContext()):Collections.<MapPins.Pin>emptyList()){marker(canvas,pin.lat,pin.lon,"★",pin.color);float x=sx(pin.lon),y=sy(pin.lat);if(x>=0&&x<=getWidth()&&y>=0&&y<getHeight()){pen.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);String label=pin.name.length()>14?pin.name.substring(0,14)+"…":pin.name;float w=pen.measureText(label);pen.setColor(Color.WHITE);canvas.drawRoundRect(x+11*density,y-13*density,x+19*density+w,y+8*density,4*density,4*density,pen);pen.setColor(Color.rgb(74,40,104));canvas.drawText(label,x+15*density,y+2*density,pen);}}
  for(int i=0;i<via.size();i++)marker(canvas,via.get(i).lat,via.get(i).lon,String.valueOf(i+1),Color.rgb(186,125,36));
  if(pending){if(from!=null)marker(canvas,from.lat,from.lon,"起",Color.rgb(45,91,227));if(to!=null)marker(canvas,to.lat,to.lon,"终",Color.rgb(205,69,54));}
  if(selection!=null)marker(canvas,selection.lat,selection.lon,"选",Color.rgb(222,131,26));
  if(fix!=null&&route!=null&&!pending){double[] p=MapMath.point(route,fix.x,fix.y);dot(canvas,p[0],p[1],replayMode?Color.rgb(222,131,26):Color.rgb(45,91,227));}
  else if(deviceLocation!=null&&SystemClock.elapsedRealtime()-deviceLocation.getElapsedRealtimeNanos()/1000000<30000)dot(canvas,deviceLocation.getLatitude(),deviceLocation.getLongitude(),Color.rgb(45,91,227));
  String info=replayMode?"地图预演 · 非实际位置 · 不发送到码表":engine.status.equals("unsupported")?"地图渲染不可用，请更新系统 WebView":engine.status.equals("error")?"底图加载失败，请检查网络":!engine.status.equals("ready")?"清晰底图加载中…":pending?"地点已选中 · 等待规划":"长按选点或加标签 · 双指缩放";if(!compactUi||replayMode||!engine.status.equals("ready"))caption(canvas,info,8*density,compactUi?Math.max(8*density,getHeight()-bottomInset-85*density):8*density);
  drawScale(canvas);canvas.restore();pen.setColor(Color.WHITE);canvas.drawRect(0,getHeight()-bottomInset-24*density,getWidth(),getHeight()-bottomInset,pen);pen.setColor(Color.rgb(48,77,63));pen.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);String credit=getContext().getSharedPreferences("navigation",0).getString("map_vector_credit","© OpenMapTiles · © OpenStreetMap contributors");canvas.drawText(credit,6*density,getHeight()-bottomInset-8*density,pen);
  if(replayRunning&&!suspended)postInvalidateDelayed(200);
 }
 void dot(Canvas canvas,double lat,double lon,int color){float x=sx(lon),y=sy(lat);pen.setColor(Color.WHITE);canvas.drawCircle(x,y,9*density,pen);pen.setColor(color);canvas.drawCircle(x,y,6*density,pen);}
 void marker(Canvas canvas,double lat,double lon,String name,int color){float x=sx(lon),y=sy(lat);if(x< -24*density||x>getWidth()+24*density||y< -24*density||y>getHeight()+24*density)return;pen.setColor(Color.WHITE);canvas.drawCircle(x,y,11*density,pen);pen.setColor(color);canvas.drawCircle(x,y,9*density,pen);pen.setColor(Color.WHITE);pen.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(name,x-pen.measureText(name)/2,y+4*density,pen);}
 void caption(Canvas canvas,String text,float x,float y){pen.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);while(text.length()>1&&pen.measureText(text)>getWidth()-28*density)text=text.substring(0,text.length()-2)+"…";pen.setColor(Color.argb(232,255,255,255));canvas.drawRoundRect(x,y,x+pen.measureText(text)+12*density,y+24*density,6*density,6*density,pen);pen.setColor(Color.rgb(24,54,39));canvas.drawText(text,x+6*density,y+16*density,pen);}
 void drawScale(Canvas canvas){double meters=40075016.686*Math.cos(Math.toRadians(MapMath.lat(centerY)))/world(),target=meters*72*density,base=Math.pow(10,Math.floor(Math.log10(Math.max(1,target))));double length=base;for(int v:new int[]{1,2,5})if(v*base<=target)length=v*base;float pixels=(float)(length/meters),y=getHeight()-bottomInset-35*density;pen.setColor(Color.WHITE);canvas.drawRect(8*density,y-20*density,20*density+pixels,y+6*density,pen);pen.setColor(Color.DKGRAY);pen.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(length>=1000?String.format(Locale.US,"%.1f km",length/1000):(int)length+" m",12*density,y-6*density,pen);pen.setStrokeWidth(2*density);canvas.drawLine(12*density,y,12*density+pixels,y,pen);}
}
