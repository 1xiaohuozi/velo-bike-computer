#pragma once
namespace NavState {
inline const char* title(int phase){switch(phase){case 6:return "OFF ROUTE";case 7:return "CHECK GPS";case 8:return "WRONG WAY";case 9:return "GPS FIX OK";case 10:return "STOPPED";default:return "ROUTE READY";}}
inline const char* help(int phase){switch(phase){case 6:return "REPLAN ON PHONE";case 7:return "WAIT FOR GOOD FIX";case 8:return "CHECK DIRECTION";case 9:return "MOVE TO SET HEADING";case 10:return "NEXT TURN BELOW";default:return "FOLLOW ROUTE";}}
}
