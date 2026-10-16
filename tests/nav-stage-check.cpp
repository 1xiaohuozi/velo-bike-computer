#include "../firmware/BikeDash/NavStage.h"
#include <cassert>
#include <iostream>
int main(){int p=-1,a=0;assert(NavStage::parse("T,123,3,8",123,p,a)&&p==3&&a==8);assert(!NavStage::parse("T,122,1,5",123,p,a)&&p==3);assert(!NavStage::parse("T,123,11,5",123,p,a));assert(!NavStage::parse("T,123,1,1000",123,p,a));assert(!NavStage::parse("T,123,1,5junk",123,p,a));assert(!NavStage::parse("T,123,1",123,p,a));assert(NavStage::parse("T,123,7,25",123,p,a)&&p==7);std::cout<<"7 stage protocol checks passed\n";}
