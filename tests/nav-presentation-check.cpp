#include <cassert>
#include <cstring>
#include <iostream>
#include "../firmware/BikeDash/NavStage.h"
#include "../firmware/BikeDash/NavState.h"
int main(){int phase=0,accuracy=0;assert(NavStage::parse("T,12,9,5",12,phase,accuracy)&&phase==9);assert(NavStage::parse("T,12,10,5",12,phase,accuracy)&&phase==10);assert(!NavStage::parse("T,12,11,5",12,phase,accuracy)&&phase==10);assert(!NavStage::parse("T,13,9,5",12,phase,accuracy));assert(strcmp(NavState::title(9),"GPS FIX OK")==0);assert(strcmp(NavState::title(7),"CHECK GPS")==0);assert(strcmp(NavState::help(9),"MOVE TO SET HEADING")==0);assert(strcmp(NavState::title(10),"STOPPED")==0);std::cout<<"8 firmware state and protocol checks passed\n";}
