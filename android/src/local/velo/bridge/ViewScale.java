package local.velo.bridge;
/** Meter-wide viewport with hysteresis so GPS jitter cannot toggle zoom each second. */
public final class ViewScale {
  private int width=300;
  public void reset(){width=300;}
  public int update(RouteModel.Fix f){
    if(f.offRoute){width=300;return width;}if(f.turn=='A'){width=90;return width;}
    if(f.meters<0){width=300;return width;}
    if(width==300 && f.meters<85)width=150;
    if(width==150 && f.meters<28)width=90;
    if(width==90 && f.meters>45)width=150;
    if(width==150 && f.meters>120)width=300;
    return width;
  }
}
