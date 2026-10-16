package local.velo.bridge;
import java.util.Locale;
import java.util.regex.*;
public final class PlaceCoordinates {
  private static final Pattern ANGLE=Pattern.compile("([0-9]{1,3}(?:\\.[0-9]+)?)\\s*°\\s*(?:([0-9]{1,2}(?:\\.[0-9]+)?)\\s*'\\s*(?:([0-9]{1,2}(?:\\.[0-9]+)?)\\s*\")?)?\\s*([NSEW])");
  public static double[] parse(String raw){
    if(raw==null)return null;
    String cleaned=raw.trim().replace('，',',').replace('（','(').replace('）',')');
    if(cleaned.startsWith("(")&&cleaned.endsWith(")"))cleaned=cleaned.substring(1,cleaned.length()-1).trim();
    if(cleaned.indexOf('°')>=0||cleaned.indexOf('º')>=0)return angles(cleaned);
    String[] tokens=cleaned.split(cleaned.contains(",")?"\\s*,\\s*":"\\s+",-1);if(tokens.length!=2)return null;
    try{double lat=Double.parseDouble(tokens[0]),lon=Double.parseDouble(tokens[1]);if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>80||Math.abs(lon)>180)return null;return new double[]{lat,lon};}catch(NumberFormatException e){return null;}
  }
  private static double[] angles(String raw){
    String s=raw.toUpperCase(Locale.ROOT).replace('º','°').replace('′','\'').replace('’','\'').replace('″','"').replace('“','"').replace('”','"').replace("''","\"").replace('\u00a0',' ').replace('\u202f',' ');
    Matcher m=ANGLE.matcher(s);double lat=Double.NaN,lon=Double.NaN;int end=0,count=0;
    while(m.find()){
      if(!s.substring(end,m.start()).matches("\\s*,?\\s*")||++count>2)return null;
      double deg=Double.parseDouble(m.group(1)),min=m.group(2)==null?0:Double.parseDouble(m.group(2)),sec=m.group(3)==null?0:Double.parseDouble(m.group(3));
      if(min>=60||sec>=60||(m.group(2)!=null&&deg%1!=0)||(m.group(3)!=null&&min%1!=0))return null;
      double value=deg+min/60+sec/3600;char axis=m.group(4).charAt(0);if(axis=='S'||axis=='W')value=-value;
      if(axis=='N'||axis=='S'){if(!Double.isNaN(lat))return null;lat=value;}else{if(!Double.isNaN(lon))return null;lon=value;}
      end=m.end();
    }
    if(count!=2||!s.substring(end).trim().isEmpty()||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>80||Math.abs(lon)>180)return null;
    return new double[]{lat,lon};
  }
}
