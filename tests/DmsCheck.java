import local.velo.bridge.PlaceCoordinates;
public class DmsCheck {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  double[] p=PlaceCoordinates.parse("33°33'06.6\"N 133°30'27.2\"E");check(p!=null,"user pasted DMS");check(Math.abs(p[0]-33.551833333333)<1e-10&&Math.abs(p[1]-133.507555555556)<1e-10,"correct conversion");
  p=PlaceCoordinates.parse("133°30′27.2″e, 33°33′06.6″n");check(p!=null&&p[0]>33&&p[1]>133,"Unicode prime and axis order");
  p=PlaceCoordinates.parse("33°33'06.6\"S 133°30'27.2\"W");check(p[0]<0&&p[1]<0,"hemisphere signs");
  check(PlaceCoordinates.parse("33°33.11'N 133°30.453333'E")!=null,"degrees decimal minutes");
  check(PlaceCoordinates.parse("33°60'0\"N 133°30'0\"E")==null,"invalid minutes");check(PlaceCoordinates.parse("33°33'60\"N 133°30'0\"E")==null,"invalid seconds");
  check(PlaceCoordinates.parse("33°33'0\"N 33°33'0\"S")==null,"duplicate latitude axis");check(PlaceCoordinates.parse("33°33'0\"N 180°0'1\"E")==null,"longitude overflow");
  check(PlaceCoordinates.parse("garbage 33°33'0\"N 133°30'0\"E")==null,"reject extra text");check(PlaceCoordinates.parse("33.551833,133.507556")!=null,"decimal regression");
  System.out.println(n+" DMS and decimal checks passed");System.out.printf(java.util.Locale.US,"%.8f, %.8f%n",33+33/60.0+6.6/3600,133+30/60.0+27.2/3600);
 }
}
