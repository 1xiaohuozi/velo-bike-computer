package local.velo.bridge;
import java.util.*;

/** Offline track guidance. Geometry-derived bends are not road-junction instructions. */
public final class RouteModel {
  public final double[] x,y,along;
  public final String name;
  public final double lat0,lon0,cosLat;
  public final double length;
  public final boolean roadInstructions;
  public List<Maneuver> maneuvers=Collections.emptyList();
  public double plannedSeconds=0;
  public static final class Maneuver {
    public final double progress;public final char direction;public final String text;public final int exit;
    public Maneuver(double p,char d,String t){this(p,d,t,0);}
    public Maneuver(double p,char d,String t,int e){progress=p;direction=d;text=t;exit=e;}
  }
  public RouteModel(String name,List<double[]> coordinates) {this(name,coordinates,false);}
  public RouteModel(String name,List<double[]> coordinates,boolean road) {
    roadInstructions=road;
    if(coordinates.size()<2 || coordinates.size()>12000)throw new IllegalArgumentException("路线需包含 2～12000 个点");
    this.name=name;lat0=coordinates.get(0)[0];lon0=coordinates.get(0)[1];cosLat=Math.cos(Math.toRadians(lat0));
    if(Math.abs(lat0)>80)throw new IllegalArgumentException("暂不支持极地路线");
    ArrayList<double[]> clean=new ArrayList<>();
    for(double[] c:coordinates){
      if(!Double.isFinite(c[0])||!Double.isFinite(c[1])||Math.abs(c[0])>90||Math.abs(c[1])>180)throw new IllegalArgumentException("坐标无效");
      double px=Math.toRadians(c[1]-lon0)*6371000*cosLat, py=Math.toRadians(c[0]-lat0)*6371000;
      if(Math.hypot(px,py)>200000)throw new IllegalArgumentException("第一版支持起点周围 200 km 内路线");
      if(clean.isEmpty()||Math.hypot(px-clean.get(clean.size()-1)[0],py-clean.get(clean.size()-1)[1])>=(road?0.01:1))clean.add(new double[]{px,py});
    }
    if(clean.size()<2)throw new IllegalArgumentException("路线长度不足");
    // Iterative Douglas-Peucker: remove GPS jitter without recursive stack growth.
    boolean[] keep=new boolean[clean.size()];keep[0]=keep[keep.length-1]=true;
    if(road)Arrays.fill(keep,true);
    ArrayDeque<int[]> stack=new ArrayDeque<>();stack.push(new int[]{0,clean.size()-1});
    if(road)stack.clear();
    while(!stack.isEmpty()){
      int[] range=stack.pop();double best=36;int found=-1;
      double[] a=clean.get(range[0]),b=clean.get(range[1]);
      for(int i=range[0]+1;i<range[1];i++){
        double[] p=clean.get(i);double t=project(p[0],p[1],a[0],a[1],b[0],b[1]);
        double d=sq(p[0]-(a[0]+t*(b[0]-a[0])))+sq(p[1]-(a[1]+t*(b[1]-a[1])));
        if(d>best){best=d;found=i;}
      }
      if(found>=0){keep[found]=true;stack.push(new int[]{range[0],found});stack.push(new int[]{found,range[1]});}
    }
    int n=0;for(boolean b:keep)if(b)n++;
    x=new double[n];y=new double[n];along=new double[n];int j=0;
    for(int i=0;i<clean.size();i++)if(keep[i]){x[j]=clean.get(i)[0];y[j]=clean.get(i)[1];if(j>0)along[j]=along[j-1]+Math.hypot(x[j]-x[j-1],y[j]-y[j-1]);j++;}
    length=along[n-1];
  }
  static double sq(double a){return a*a;}
  static double project(double px,double py,double ax,double ay,double bx,double by){double dx=bx-ax,dy=by-ay;double den=dx*dx+dy*dy;return den==0?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/den));}
  public double[] local(double lat,double lon){return new double[]{Math.toRadians(lon-lon0)*6371000*cosLat,Math.toRadians(lat-lat0)*6371000};}
  public static final class Fix {
    public double x,y,progress,error,heading; public char turn='N';public int meters=-1,exit=0,headingQuality=0,viewportSpan=0,phase=0;public boolean offRoute;
  }
  public Fix at(double distance){
    double s=Math.max(0,Math.min(length,distance));int i=0;while(i<x.length-2 && along[i+1]<s)i++;
    double t=(s-along[i])/(along[i+1]-along[i]);
    Fix f=new Fix();f.x=x[i]+t*(x[i+1]-x[i]);f.y=y[i]+t*(y[i+1]-y[i]);f.progress=s;
    f.heading=(Math.toDegrees(Math.atan2(x[i+1]-x[i],y[i+1]-y[i]))+360)%360;instruction(f);return f;
  }
  public Fix match(double px,double py,double previous){
    Fix f=new Fix();f.x=px;f.y=py;f.error=Double.MAX_VALUE;
    for(int i=0;i<x.length-1;i++){
      if(previous>=0 && (along[i+1]<previous-60 || along[i]>previous+250))continue;
      double t=project(px,py,x[i],y[i],x[i+1],y[i+1]);double s=along[i]+t*(along[i+1]-along[i]);
      if(previous>=0 && (s<previous-60 || s>previous+250))continue;
      double error=Math.hypot(px-(x[i]+t*(x[i+1]-x[i])),py-(y[i]+t*(y[i+1]-y[i])));
      if(error<f.error){f.error=error;f.progress=s;f.heading=(Math.toDegrees(Math.atan2(x[i+1]-x[i],y[i+1]-y[i]))+360)%360;}
    }
    f.offRoute=f.error>35;
    if(f.offRoute){f.turn='N';return f;}
    instruction(f);return f;
  }
  void instruction(Fix f){
    if(length-f.progress<=12){f.turn='A';f.meters=0;return;}
    if(roadInstructions){
      for(Maneuver m:maneuvers)if(m.progress>f.progress+1){f.turn=m.direction;f.meters=(int)Math.round(m.progress-f.progress);f.exit=m.exit;return;}
      f.turn='S';f.meters=(int)Math.round(length-f.progress);return;
    }
    for(int i=1;i<x.length-1;i++)if(along[i]>f.progress+1){
      double incoming=Math.atan2(x[i]-x[i-1],y[i]-y[i-1]),outgoing=Math.atan2(x[i+1]-x[i],y[i+1]-y[i]);
      double angle=Math.toDegrees(outgoing-incoming);while(angle>180)angle-=360;while(angle< -180)angle+=360;
      if(Math.abs(angle)>=35){f.turn=Math.abs(angle)>150?'U':angle>0?'R':'L';f.meters=(int)Math.round(along[i]-f.progress);return;}
    }
    f.turn='S';f.meters=(int)Math.round(length-f.progress);
  }
  public double progressAt(double lat,double lon,double minimum){
    double[] point=local(lat,lon);double best=Double.MAX_VALUE,progress=minimum;
    for(int i=0;i<x.length-1;i++){
      double t=project(point[0],point[1],x[i],y[i],x[i+1],y[i+1]);double s=along[i]+t*(along[i+1]-along[i]);
      if(s+0.01<minimum)continue;
      double d=Math.hypot(point[0]-(x[i]+t*(x[i+1]-x[i])),point[1]-(y[i]+t*(y[i+1]-y[i])));
      if(d<best){best=d;progress=s;}
    }
    return progress;
  }
  // Clip each segment into a 128x128 north-up viewport; never join separate fragments.
  public int[][] segments(Fix f,int span){
    ArrayList<int[]> result=new ArrayList<>();
    for(int i=0;i<x.length-1 && result.size()<40;i++){
      double ax=64+(x[i]-f.x)*128/span,ay=64-(y[i]-f.y)*128/span;
      double bx=64+(x[i+1]-f.x)*128/span,by=64-(y[i+1]-f.y)*128/span;
      double dx=bx-ax,dy=by-ay;double lo=0,hi=1;
      double[] p={-dx,dx,-dy,dy},q={ax,127-ax,ay,127-ay};boolean ok=true;
      for(int k=0;k<4;k++){if(p[k]==0){if(q[k]<0){ok=false;break;}}else{double r=q[k]/p[k];if(p[k]<0)lo=Math.max(lo,r);else hi=Math.min(hi,r);}}
      if(ok && lo<=hi)result.add(new int[]{pixel(ax+lo*dx),pixel(ay+lo*dy),pixel(ax+hi*dx),pixel(ay+hi*dy)});
    }
    return result.toArray(new int[0][]);
  }
  static int pixel(double p){return (int)Math.max(0,Math.min(127,Math.round(p)));}
  /** Course-up: rotate before clipping, anchor below center to show more route ahead. */
  public int[][] headingSegments(Fix f,int span){
    ArrayList<double[]> pieces=new ArrayList<>();double radians=Math.toRadians(f.heading),cos=Math.cos(radians),sin=Math.sin(radians);
    for(int i=0;i<x.length-1;i++){
      double length=along[i+1]-along[i];
      if(along[i+1]<f.progress-100 || along[i]>f.progress+span*2)continue;
      double split=Math.max(0,Math.min(1,(f.progress-along[i])/length));
      if(split>0)addPiece(pieces,f,span,cos,sin,x[i],y[i],x[i]+split*(x[i+1]-x[i]),y[i]+split*(y[i+1]-y[i]),0,Math.abs(f.progress-along[i+1]));
      if(split<1)addPiece(pieces,f,span,cos,sin,x[i]+split*(x[i+1]-x[i]),y[i]+split*(y[i+1]-y[i]),x[i+1],y[i+1],1,Math.abs(along[i]-f.progress));
    }
    pieces.sort((a,b)->Double.compare(a[5],b[5]));int n=Math.min(40,pieces.size());int[][] lines=new int[n][5];
    for(int i=0;i<n;i++){double[] p=pieces.get(i);for(int j=0;j<4;j++)lines[i][j]=pixel(p[j]);lines[i][4]=(int)p[4];}
    return lines;
  }
  static void addPiece(List<double[]> pieces,Fix f,int span,double cos,double sin,double x1,double y1,double x2,double y2,int future,double priority){
    double ax=64+((x1-f.x)*cos-(y1-f.y)*sin)*128/span,ay=92-((x1-f.x)*sin+(y1-f.y)*cos)*128/span;
    double bx=64+((x2-f.x)*cos-(y2-f.y)*sin)*128/span,by=92-((x2-f.x)*sin+(y2-f.y)*cos)*128/span;
    double dx=bx-ax,dy=by-ay,lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={ax,127-ax,ay,127-ay};
    for(int k=0;k<4;k++){if(p[k]==0){if(q[k]<0)return;}else{double t=q[k]/p[k];if(p[k]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);}}
    if(lo<=hi)pieces.add(new double[]{ax+lo*dx,ay+lo*dy,ax+hi*dx,ay+hi*dy,future,priority});
  }
  public static int span(Fix f){return f.offRoute?300:f.turn=='A'?90:f.meters>=0&&f.meters<35?90:f.meters>=0&&f.meters<100?150:300;}
  public String packet(Fix f,boolean replay,long sequence){
    int span=f.viewportSpan>0?f.viewportSpan:span(f);int[][] lines=headingSegments(f,span);
    StringBuilder b=new StringBuilder("H,").append(sequence).append(',').append(replay?'R':'G').append(',').append(f.offRoute?1:0).append(',').append(f.turn).append(',').append(f.meters).append(',').append((int)Math.max(0,length-f.progress)).append(',').append(span).append(',').append((int)HeadingFilter.normalize(f.heading)).append(',').append(f.exit).append(',').append(replay?2:f.headingQuality).append(',').append(lines.length);
    for(int[] line:lines)for(int v:line)b.append(',').append(v);
    return b.append('\n').toString();
  }
  public static RouteModel demo(){
    double lat=35.68,lon=139.76;double[][] meters={{0,0},{0,100},{80,100},{80,200},{-20,200},{-20,300},{60,300}};
    ArrayList<double[]> c=new ArrayList<>();for(double[] p:meters)c.add(new double[]{lat+Math.toDegrees(p[1]/6371000),lon+Math.toDegrees(p[0]/(6371000*Math.cos(Math.toRadians(lat))))});
    return new RouteModel("室内回放路线（虚构）",c);
  }
}
