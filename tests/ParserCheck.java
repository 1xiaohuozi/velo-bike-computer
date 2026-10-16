import local.velo.bridge.NavParser;
public class ParserCheck {
  static void check(String text,char direction,int meters){
    NavParser.Turn t=NavParser.parse(text);
    if(t.direction!=direction || t.meters!=meters)throw new AssertionError(text+" -> "+t.direction+","+t.meters);
  }
  public static void main(String[] args){
    check("In 200 m Turn left onto Main Street",'L',200);
    check("Turn right in 1.2 km",'R',1200);
    check("300米后右转",'R',300);
    check("200 メートル先 左折",'L',200);
    check("Make a U-turn in 500 ft",'U',152);
    check("Continue straight for 2 km",'S',2000);
    check("At the roundabout turn left in 200 m",'N',-1);
    check("Turn left then turn right in 100 m",'N',100);
    check("Google Maps is running",'N',-1);
    check("You have arrived",'A',-1);
    check("0 米 向东前行",'S',0);
    check("0 米\n向东前行\n退出导航",'S',0);
    check("向东北方向前进 100 米",'S',100);
    check("向西南前行 50 米",'S',50);
    check("Head northeast 0 m",'S',0);
    check("東に進む 20 メートル",'S',20);
    check("向東前進 0 米",'S',0);
    check("沿东大街行驶 50 米",'N',50);
    System.out.println("18 navigation parser checks passed");
  }
}
