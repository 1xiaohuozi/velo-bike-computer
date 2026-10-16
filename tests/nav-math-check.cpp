#include "../firmware/BikeDash/NavMath.h"
#include <cassert>
#include <cstdio>
int main(){
  assert(fabs(NavMath::delta(1,359)-2)<0.001);assert(fabs(NavMath::delta(359,1)+2)<0.001);
  float h=NavMath::smooth(359,1,0.5);assert(h<0.01||h>359.99);
  float x=34,y=92;NavMath::rotate(x,y,64,92,90);assert(fabs(x-64)<0.01&&fabs(y-62)<0.01);
  float x1=-50,y1=60,x2=200,y2=60;assert(NavMath::clip(x1,y1,x2,y2));assert(fabs(x1)<0.01&&fabs(x2-127)<0.01);
  x1=-30;y1=-30;x2=-20;y2=-20;assert(!NavMath::clip(x1,y1,x2,y2));
  assert(NavMath::scaleMeters(150)==50&&NavMath::scaleMeters(90)==20&&NavMath::scaleMeters(300)==100);
  puts("Board angle-wrap, interpolation, rotation and clipping checks passed");
}
