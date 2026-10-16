#pragma once
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
namespace RoadFrame {
inline bool parse(const char *line,unsigned long expected,uint8_t output[72][5],int &count){
 unsigned long seq;int n,used=0;if(sscanf(line,"B,%lu,%d%n",&seq,&n,&used)!=2||seq!=expected||n<0||n>72)return false;
 uint8_t pending[72][5];const char *cursor=line+used;
 for(int i=0;i<n;i++)for(int j=0;j<5;j++){if(*cursor++!=',')return false;char *end;long v=strtol(cursor,&end,10);if(end==cursor||v<0||v>(j==4?2:127))return false;pending[i][j]=(uint8_t)v;cursor=end;}
 if(*cursor)return false;memcpy(output,pending,n*5);count=n;return true;
}
}
