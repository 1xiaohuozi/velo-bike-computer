package local.velo.bridge;
import java.util.Locale;
import java.util.regex.*;

/** Conservative text fallback: Google Maps does not publish a turn-notification schema. */
public final class NavParser {
  public static final class Turn {
    public final char direction; public final int meters;
    public Turn(char d,int m) {direction=d;meters=m;}
  }
  public static Turn parse(String raw) {
    String s=raw.toLowerCase(Locale.ROOT);
    char d='N';
    if(s.matches("(?s).*(roundabout|环岛|環島|ロータリー|ラウンドアバウト).*$")) return new Turn('N',-1);
    if(s.matches("(?s).*(u.turn|掉头|掉頭|折り返し|uターン).*$")) d='U';
    else if(s.matches("(?s).*(arrived|you.have.arrived|到达目的地|抵達目的地|目的地に到着).*$")) d='A';
    else {
      boolean left=s.matches("(?s).*(turn left|keep left|slight left|sharp left|左转|左轉|左折).*$");
      boolean right=s.matches("(?s).*(turn right|keep right|slight right|sharp right|右转|右轉|右折).*$");
      if(left && !right)d='L'; else if(right && !left)d='R';
      else if(!left && !right && s.matches("(?s).*(continue straight|go straight|直行|直進|向[东西南北東西]{1,2}(?:方向)?(?:前行|前进|前進)|朝[东西南北東西]{1,2}(?:方向)?(?:前行|前进|前進)|head (?:north|south|east|west)(?:east|west)?|[東西南北]{1,2}に進む).*$"))d='S';
    }
    Matcher distance=Pattern.compile("(?<![\\d.])([0-9]+(?:[.,][0-9]+)?)\\s*(kilometers?|kilometres?|km|公里|千米|キロメートル|meters?|metres?|m|米|メートル|feet|ft|miles?|mi)(?![a-z])").matcher(s);
    int m=-1;
    if(distance.find()) {
      double v=Double.parseDouble(distance.group(1).replace(',','.'));
      String unit=distance.group(2);
      if(unit.startsWith("k")||unit.equals("公里")||unit.equals("千米")||unit.startsWith("キロ"))v*=1000;
      else if(unit.equals("ft")||unit.equals("feet"))v*=0.3048;
      else if(unit.equals("mi")||unit.startsWith("mile"))v*=1609.344;
      if(v<=1000000)m=(int)Math.round(v);
    }
    return new Turn(d,m);
  }
}
