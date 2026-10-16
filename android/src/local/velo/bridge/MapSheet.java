package local.velo.bridge;
import android.content.Context;import android.view.*;import android.widget.ScrollView;import android.animation.ValueAnimator;
/** The top 40 dp are a persistent drag target; content scrolls below it. */
public final class MapSheet extends ScrollView {
 interface Changed{void height(int h);}final float density;final Changed changed;boolean dragging;float downY;int downHeight;ValueAnimator animator;
 MapSheet(Context c,Changed callback){super(c);density=getResources().getDisplayMetrics().density;changed=callback;setContentDescription("地图面板，拖动顶部上下展开或收起");}
 int limit(){return getParent() instanceof View?((View)getParent()).getHeight():getHeight();}
 void height(int value){int h=Math.max((int)(64*density),Math.min((int)(limit()*.82),value));getLayoutParams().height=h;requestLayout();changed.height(h);}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=e.getY()<40*density;if(dragging){if(animator!=null)animator.cancel();downY=e.getRawY();downHeight=getHeight();return true;}}return super.onInterceptTouchEvent(e);}
 @Override public boolean onTouchEvent(MotionEvent e){if(!dragging)return super.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_MOVE){height(downHeight+Math.round(downY-e.getRawY()));return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;int h=getLayoutParams().height>0?getLayoutParams().height:getHeight(),target=(int)(64*density);for(int snap:new int[]{(int)(limit()*.43),(int)(limit()*.82)})if(Math.abs(snap-h)<Math.abs(target-h))target=snap;animator=ValueAnimator.ofInt(h,target);animator.setDuration(180);animator.addUpdateListener(a->height((Integer)a.getAnimatedValue()));animator.start();performClick();return true;}return true;}
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){if(getHeight()>70*density){super.dispatchDraw(canvas);return;}canvas.save();canvas.clipRect(0,0,getWidth(),32*density);super.dispatchDraw(canvas);canvas.restore();android.graphics.Paint paint=new android.graphics.Paint(3);paint.setColor(0xff758196);paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);String hint="上滑展开 · 规划与收藏";canvas.drawText(hint,(getWidth()-paint.measureText(hint))/2,50*density,paint);}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
