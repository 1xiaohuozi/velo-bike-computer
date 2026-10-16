#pragma once
#include <stdio.h>
namespace NavStage {
inline bool parse(const char* line,unsigned long expected,int& phase,int& accuracy){
  unsigned long sequence;int p,a,used=0;
  if(sscanf(line,"T,%lu,%d,%d%n",&sequence,&p,&a,&used)!=3||line[used]!='\0'||sequence!=expected||p<0||p>10||a<0||a>999)return false;
  phase=p;accuracy=a;return true;
}
}
