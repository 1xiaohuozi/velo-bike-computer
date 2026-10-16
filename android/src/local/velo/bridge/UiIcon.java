package local.velo.bridge;
import android.content.Context;import android.graphics.*;import android.view.View;
public final class UiIcon extends View {
 final Paint p=new Paint(3);final String name;final int color;
 public UiIcon(Context c,String n,int col){super(c);name=n;color=col;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void line(Canvas c,float... xy){Path a=new Path();a.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)a.lineTo(xy[i],xy[i+1]);c.drawPath(a,p);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
 switch(name){case "map":line(c,3,5,9,3,15,5,21,3,21,19,15,21,9,19,3,21,3,5);line(c,9,3,9,19);line(c,15,5,15,21);break;
 case "star":Path s=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4.5f,x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)s.moveTo(x,y);else s.lineTo(x,y);}s.close();c.drawPath(s,p);break;
 case "route":c.drawCircle(6,5,2.5f,p);c.drawCircle(18,19,2.5f,p);Path r=new Path();r.moveTo(6,8);r.lineTo(6,13);r.cubicTo(6,17,19,17,19,12);r.cubicTo(19,8,13,9,13,9);c.drawPath(r,p);line(c,18,2,18,7);line(c,15.5f,4.5f,20.5f,4.5f);break;
 case "device":c.drawRoundRect(6,3,18,21,3,3,p);line(c,9,7,15,7);line(c,9,11,15,11);line(c,11,17,13,17);break;
 case "search":c.drawCircle(10.5f,10.5f,6,p);line(c,15,15,21,21);break;
 case "target":c.drawCircle(12,12,7,p);c.drawCircle(12,12,2,p);line(c,12,1,12,5);line(c,12,19,12,23);line(c,1,12,5,12);line(c,19,12,23,12);break;
 case "layers":line(c,2,8,12,3,22,8,12,13,2,8);line(c,2,12,12,17,22,12);line(c,2,16,12,21,22,16);break;
 case "pin":c.drawCircle(12,9,6.5f,p);c.drawCircle(12,9,2,p);line(c,7,13,12,22,17,13);break;
 case "back":line(c,14,5,7,12,14,19);line(c,7,12,21,12);break;
 case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
 case "close":line(c,6,6,18,18);line(c,6,18,18,6);break;
 default:c.drawCircle(12,12,7,p);}
 c.restore();}
}
