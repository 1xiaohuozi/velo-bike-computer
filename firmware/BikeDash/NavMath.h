#pragma once
#include <math.h>
namespace NavMath {
inline float wrap(float h){h=fmodf(h,360.0f);return h<0?h+360:h;}
inline float delta(float target,float current){return fmodf(wrap(target-current)+540.0f,360.0f)-180.0f;}
inline float smooth(float current,float target,float amount){return wrap(current+delta(target,current)*amount);}
inline int scaleMeters(int span){int value=10;const int candidates[]={10,20,50,100,200,500};for(int candidate:candidates)if(candidate<=span/3)value=candidate;return value;}
inline void rotate(float &x,float &y,float cx,float cy,float degrees){float r=degrees*0.01745329252f,c=cosf(r),s=sinf(r),dx=x-cx,dy=y-cy;x=cx+c*dx-s*dy;y=cy+s*dx+c*dy;}
inline bool clip(float &x1,float &y1,float &x2,float &y2){
  float dx=x2-x1,dy=y2-y1,lo=0,hi=1,p[4]={-dx,dx,-dy,dy},q[4]={x1,127-x1,y1,127-y1};
  for(int i=0;i<4;i++){if(fabsf(p[i])<0.000001f){if(q[i]<0)return false;}else{float t=q[i]/p[i];if(p[i]<0){if(t>lo)lo=t;}else if(t<hi)hi=t;}}
  if(lo>hi)return false;x2=x1+hi*dx;y2=y1+hi*dy;x1+=lo*dx;y1+=lo*dy;return true;
}
}
