import local.velo.bridge.*;
public class SearchCheck {
  static int n;static void check(boolean ok,String s){n++;if(!ok)throw new AssertionError(s);}
  public static void main(String[] args)throws Exception{
    check(PlaceCoordinates.parse("35.68,139.77")[0]==35.68,"lat lon order");
    check(PlaceCoordinates.parse("（35.68，139.77）")!=null,"copied fullwidth coordinate");
    check(PlaceCoordinates.parse("35.68 139.77")!=null,"space separated");
    check(PlaceCoordinates.parse("139.77,35.68")==null,"inverted coordinate rejected");
    check(PlaceCoordinates.parse("NaN,139.77")==null,"non finite rejected");
    String raw="{\"features\":[{\"geometry\":{\"type\":\"Point\",\"coordinates\":[139.7671,35.6812]},\"properties\":{\"name\":\"東京駅\",\"city\":\"千代田区\",\"country\":\"日本\"}},{\"geometry\":{\"type\":\"Point\",\"coordinates\":[139.7671,35.6812]},\"properties\":{\"name\":\"東京駅\"}},{\"geometry\":{\"type\":\"Point\",\"coordinates\":[999,35]},\"properties\":{}},{\"geometry\":{\"type\":\"LineString\"},\"properties\":{}}]}";
    java.util.List<PlaceSearch.Entry> results=PlaceSearch.parse(raw);check(results.size()==1,"dedupe and reject invalid geometry");check(results.get(0).name.equals("東京駅"),"local language name");check(results.get(0).lat==35.6812,"GeoJSON lon lat converted");check(results.get(0).label().contains("千代田区"),"address visible");
    String url=PlaceSearch.url(PlaceSearch.DEFAULT_ENDPOINT,"東京駅 & cafe",35.68,139.77);check(url.contains("%26")&&url.contains("&lat=35.68"),"query escaped and location biased");
    boolean bad=false;try{PlaceSearch.url("http://example.com","x",null,null);}catch(Exception e){bad=true;}check(bad,"HTTPS required");
    check(PlaceSearch.parse("{\"features\":[]}").isEmpty(),"empty search handled");System.out.println(n+" search and coordinate checks passed");
  }
}
