package local.velo.bridge;
import android.content.res.ColorStateList;import android.graphics.Color;import android.graphics.drawable.*;import android.view.*;import android.view.animation.*;
/** Shared native components: surfaces, ripple feedback and restrained transitions. */
public final class UiStyle {
 static GradientDrawable shape(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
 static void ripple(View view,int color,float radius){view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24125d50),shape(color,radius),shape(Color.WHITE,radius)));}
 static void enter(View view,float distance){view.animate().cancel();view.setAlpha(0);view.setTranslationY(distance);view.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();}
 static void select(View view){view.setScaleX(.94f);view.setScaleY(.94f);view.animate().scaleX(1).scaleY(1).setDuration(210).setInterpolator(new DecelerateInterpolator()).start();}
}
