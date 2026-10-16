import local.velo.bridge.*;import java.util.*;import java.io.*;import java.nio.file.*;
public class MvtCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 static byte[] ints(int... values){ByteArrayOutputStream out=new ByteArrayOutputStream();for(int v:values){while((v&~127)!=0){out.write((v&127)|128);v>>>=7;}out.write(v);}return out.toByteArray();}
 static byte[] join(byte[]... parts)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] p:parts)out.write(p);return out.toByteArray();}
 static byte[] field(int tag,byte[] raw)throws Exception{return join(ints(tag,raw.length),raw);}
 static byte[] tile(byte[] geometry,byte[] tags)throws Exception{byte[] f=join(field(18,tags),ints(24,2),field(34,geometry));byte[] layer=join(field(10,"transportation".getBytes("UTF-8")),field(18,f),field(26,"class".getBytes("UTF-8")),field(34,field(10,"primary".getBytes("UTF-8"))),ints(40,4096,120,2));return field(26,layer);}
 static void reject(byte[] b,String label)throws Exception{boolean bad=false;try{MvtRoads.decode(b,14,14268,6569);}catch(Exception e){bad=true;}check(bad,label);}
 public static void main(String[] args)throws Exception{
  byte[] real=Files.readAllBytes(Paths.get("tests/assets/road-real.pbf"));List<double[]> roads=MvtRoads.decode(real,14,14268,6569);check(roads.size()>100,"real vector roads decoded");for(double[] e:roads){check(e[4]>=0&&e[4]<=2,"road classification");check(Math.abs(e[0]-33.55)<.1&&Math.abs(e[1]-133.5)<.1,"geographic coordinates");}
  byte[] g=ints(9,200,200,10,200,0,9,200,400,10,200,0);List<double[]> pieces=MvtRoads.decode(tile(g,ints(0,0)),14,14268,6569);check(pieces.size()==2,"multiline has no bridge between parts");check(pieces.get(0)[4]==2,"major street hierarchy");check(pieces.get(0)[3]<pieces.get(1)[1],"disjoint pieces preserved");
  reject(new byte[]{26,127},"truncated length");reject(tile(ints(9,200),ints(0,0)),"truncated point");reject(tile(ints(11,0,0),ints(0,0)),"unknown command");reject(tile(g,ints(1,0)),"bad tag key");reject(tile(g,ints(0,9)),"bad tag value");reject(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,1},"varint overflow");
  check(MvtRoads.decode(new byte[0],14,14268,6569).isEmpty(),"empty ocean tile");
  System.out.println(n+" vector tile geometry and malformed-input checks passed; road edges="+roads.size());
 }
}
