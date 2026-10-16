package local.velo.bridge;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Bounded Mapbox Vector Tile 2.x decoder: transportation line geometry only. */
public final class MvtRoads {
 static final class P {
  final byte[] data;int at,end;P(byte[] b){data=b;end=b.length;}P(byte[] b,int a,int z){data=b;at=a;end=z;}
  long number()throws IOException{long v=0;for(int i=0;i<10;i++){if(at>=end)throw new IOException("Truncated protobuf");int b=data[at++]&255;if(i==9&&(b&254)!=0)throw new IOException("Varint overflow");v|=(long)(b&127)<<(i*7);if((b&128)==0)return v;}throw new IOException("Varint overflow");}
  int integer()throws IOException{long n=number();if(n<0||n>Integer.MAX_VALUE)throw new IOException("Integer overflow");return(int)n;}
  P bytes()throws IOException{int n=integer();if(n>end-at)throw new IOException("Invalid field length");P p=new P(data,at,at+n);at+=n;return p;}
  String text()throws IOException{P p=bytes();if(p.end-p.at>65536)throw new IOException("String too long");return new String(data,p.at,p.end-p.at,StandardCharsets.UTF_8);}
  void skip(int wire)throws IOException{if(wire==0)number();else if(wire==2)bytes();else{int n=wire==1?8:wire==5?4:-1;if(n<0||n>end-at)throw new IOException("Invalid protobuf wire");at+=n;}}
 }
 static int signed(int n){return(n>>>1)^-(n&1);}
 public static List<double[]> decode(byte[] raw,int z,int tx,int ty)throws IOException{
  if(raw.length>4*1024*1024||z<0||z>14||tx<0||ty<0||tx>=(1<<z)||ty>=(1<<z))throw new IOException("Invalid vector tile");
  ArrayList<double[]> edges=new ArrayList<>();P p=new P(raw);while(p.at<p.end){int tag=p.integer();if(tag==26)layer(p.bytes(),z,tx,ty,edges);else p.skip(tag&7);}return edges;
 }
 static void layer(P p,int z,int tx,int ty,List<double[]> edges)throws IOException{
  String name="";int extent=4096;ArrayList<P> features=new ArrayList<>();ArrayList<String> keys=new ArrayList<>(),values=new ArrayList<>();
  while(p.at<p.end){int tag=p.integer();if(tag==10)name=p.text();else if(tag==18){features.add(p.bytes());if(features.size()>60000)throw new IOException("Too many features");}else if(tag==26)keys.add(p.text());else if(tag==34){P v=p.bytes();String value="";while(v.at<v.end){int t=v.integer();if(t==10)value=v.text();else v.skip(t&7);}values.add(value);}else if(tag==40)extent=p.integer();else p.skip(tag&7);}
  if(!"transportation".equals(name))return;if(extent<1||extent>65536)throw new IOException("Invalid extent");
  for(P feature:features)feature(feature,keys,values,extent,z,tx,ty,edges);
 }
 static void feature(P p,List<String> keys,List<String> values,int extent,int z,int tx,int ty,List<double[]> edges)throws IOException{
  P tags=null,geometry=null;int type=0;while(p.at<p.end){int t=p.integer();if(t==18)tags=p.bytes();else if(t==24)type=p.integer();else if(t==34)geometry=p.bytes();else p.skip(t&7);}
  if(type!=2||geometry==null)return;String kind="";if(tags!=null)while(tags.at<tags.end){int k=tags.integer(),v=tags.integer();if(k>=keys.size()||v>=values.size())throw new IOException("Invalid feature tags");if("class".equals(keys.get(k)))kind=values.get(v);}
  if(kind.matches("rail|transit|aerialway|ferry")||kind.isEmpty())return;int style=kind.matches("motorway|trunk|primary|secondary|tertiary")?2:kind.matches("path|track|pedestrian")?0:1;
  int x=0,y=0,points=0;double lat=0,lon=0;boolean previous=false;
  while(geometry.at<geometry.end){int command=geometry.integer(),id=command&7,count=command>>>3;if(count<1||count>100000)throw new IOException("Invalid geometry command");if(id==7){if(count!=1)throw new IOException("Invalid close path");previous=false;continue;}if(id!=1&&id!=2)throw new IOException("Unsupported geometry command");
   for(int i=0;i<count;i++){x=Math.addExact(x,signed(geometry.integer()));y=Math.addExact(y,signed(geometry.integer()));if(++points>100000||Math.abs((long)x)>extent*4L||Math.abs((long)y)>extent*4L)throw new IOException("Geometry outside tile bounds");
    double a=MapMath.lat((ty+(double)y/extent)/(1<<z)),b=MapMath.lon((tx+(double)x/extent)/(1<<z));
    if(id==2&&previous){edges.add(new double[]{lat,lon,a,b,style});if(edges.size()>100000)throw new IOException("Too many road edges");}lat=a;lon=b;previous=true;
   }
  }
 }
}
