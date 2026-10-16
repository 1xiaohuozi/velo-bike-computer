#include <stdint.h>
#include <cassert>
#include <fstream>
#include <iostream>
#include "../firmware/BikeDash/RoadFrame.h"
int main(){uint8_t lines[72][5];int n=0;std::ifstream in("work/board-roads.txt");std::string s;while(std::getline(in,s))if(s.rfind("B,",0)==0)break;assert(RoadFrame::parse(s.c_str(),123,lines,n));assert(n>0&&n<=72);int old=n;assert(!RoadFrame::parse("B,124,0",123,lines,n));assert(n==old);assert(!RoadFrame::parse("B,123,1,0,0,999,0,1",123,lines,n));assert(n==old);assert(!RoadFrame::parse("B,123,1,0,0,0,0,3",123,lines,n));assert(!RoadFrame::parse("B,123,73",123,lines,n));assert(!RoadFrame::parse("B,123,0,junk",123,lines,n));assert(!RoadFrame::parse("B,123,1,0",123,lines,n));assert(RoadFrame::parse("B,123,0",123,lines,n)&&n==0);std::cout<<"8 road frame atomicity and bounds checks passed\n";}
