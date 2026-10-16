#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include <BluetoothSerial.h>
#include <math.h>
#include "NavMath.h"
#include "NavStage.h"
#include "NavState.h"
#include "RoadFrame.h"
#include <Fonts/FreeSansBold18pt7b.h>
#include <Fonts/FreeSansBold12pt7b.h>

// Ideaspark 1.14 inch ST7789 135x240. Change only for a different PCB.
Adafruit_ST7789 lcd(15, 2, 4);
BluetoothSerial phone;
GFXcanvas16 frame(135, 240);
bool demo = false, haveData = false;
float speed = 0, trip = 0;
unsigned long rideSeconds = 0, lastData = 0, lastFrame = 0;
char turn = 'S';
int remaining = 0;
String usbLine, btLine;
unsigned long acceptedPackets=0, rejectedPackets=0;
uint8_t mapSegments[40][5];
uint8_t roadSegments[72][5];int roadCount=0;unsigned long roadSequence=0;
int mapCount=0,mapSpan=300,mapHeading=0,mapLeft=0;
int mapExit=0,mapQuality=0;
int mapPhase=-1,mapAccuracy=0;
float displayHeading=0;
bool mapHeadingUp=false,gpsWait=false;
bool haveMap=false,mapReplay=false,mapOffRoute=false;
bool showStats=false,buttonDown=false;
unsigned long buttonChanged=0;
unsigned long mapUpdated=0,mapSequence=0,mapPackets=0;
const uint16_t BG=0x0841, FG=0xFFFF, GREEN=0xA7E0, MUTED=0x8C91;

// Protocol: D,speed_kmh,trip_km,elapsed_seconds,S|L|R|U|A,meters\n
// DEMO / LIVE switch modes. LIVE never fabricates measurements.
void draw();
bool mapCommand(const String &line) {
  unsigned long seq; char mode,t;int off,dist,left,span,heading,n,used=0,exit=0,quality=0;
  bool headingUp=line.startsWith("H,");
  if(headingUp){if(sscanf(line.c_str(),"H,%lu,%c,%d,%c,%d,%d,%d,%d,%d,%d,%d%n",&seq,&mode,&off,&t,&dist,&left,&span,&heading,&exit,&quality,&n,&used)!=11)return false;}
  else if(sscanf(line.c_str(),"M,%lu,%c,%d,%c,%d,%d,%d,%d,%d%n",&seq,&mode,&off,&t,&dist,&left,&span,&heading,&n,&used)!=9)return false;
  if((mode!='R' && mode!='G') || (off!=0 && off!=1) || !strchr("SLRUANO",t) || dist< -1 || dist>1000000 || left<0 || left>1000000 || span<50 || span>2000 || heading<0 || heading>359 || n<0 || n>40 || exit<0 || exit>20 || quality<0 || quality>2)return false;
  uint8_t pending[40][5];const char *cursor=line.c_str()+used;
  for(int i=0;i<n;i++){pending[i][4]=1;for(int j=0;j<(headingUp?5:4);j++){
    if(*cursor++!=',')return false;
    char *end;long value=strtol(cursor,&end,10);
    if(end==cursor || value<0 || value>(j==4?1:127))return false;
    pending[i][j]=(uint8_t)value;cursor=end;
  }}
  if(*cursor!='\0')return false;
  if(!haveMap || headingUp!=mapHeadingUp || millis()-mapUpdated>=5000)displayHeading=heading;
  roadCount=0;roadSequence=0;memcpy(mapSegments,pending,n*5);mapCount=n;mapSpan=span;mapHeading=heading;
  mapHeadingUp=headingUp;mapExit=exit;mapQuality=quality;gpsWait=false;
  mapPhase=-1;mapAccuracy=0;
  mapLeft=left;mapReplay=mode=='R';mapOffRoute=off==1;mapUpdated=millis();mapSequence=seq;
      turn=t;remaining=dist;haveMap=true;mapPackets++;return true;
}
void command(String line) {
  line.trim();
  if(line=="SCREEN"){
    draw();
    const uint16_t *pixels=frame.getBuffer();int runs=0;
    for(int i=0;i<135*240;){uint16_t color=pixels[i];int end=i+1;while(end<135*240&&pixels[end]==color)end++;runs++;i=end;}
    Serial.printf("FB16 %d\n",runs);
    for(int i=0;i<135*240;){uint16_t color=pixels[i];int end=i+1;while(end<135*240&&pixels[end]==color)end++;uint16_t count=end-i;Serial.write((uint8_t*)&count,2);Serial.write((uint8_t*)&color,2);i=end;}
    return;
  }
  if (line == "STATUS") {
    Serial.printf("STATUS accepted=%lu rejected=%lu fresh=%d bt=%d demo=%d maps=%lu mapfresh=%d segments=%d seq=%lu replay=%d up=%d heading=%d shown=%.1f exit=%d quality=%d gpswait=%d phase=%d accuracy=%d roads=%d\n",
      acceptedPackets,rejectedPackets,haveData && millis()-lastData<5000,phone.hasClient(),demo,mapPackets,haveMap && millis()-mapUpdated<5000,mapCount,mapSequence,mapReplay,mapHeadingUp,mapHeading,displayHeading,mapExit,mapQuality,gpsWait,mapPhase,mapAccuracy,roadSequence==mapSequence?roadCount:0);
    return;
  }
  if (line == "DEMO") { demo=true; return; }
  if (line == "LIVE") { demo=false; haveData=false; haveMap=false;gpsWait=false; return; }
  if (line == "MAPOFF" || line=="GPSWAIT") {haveMap=false;gpsWait=line=="GPSWAIT";turn='N';remaining=-1;return;}
  if (line == "PAGE") {showStats=!showStats;return;}
  if(line.startsWith("B,")){if(!haveMap||!mapHeadingUp||millis()-mapUpdated>=5000||!RoadFrame::parse(line.c_str(),mapSequence,roadSegments,roadCount))rejectedPackets++;else roadSequence=mapSequence;return;}
  if(line.startsWith("T,")){if(!haveMap||millis()-mapUpdated>=5000||!NavStage::parse(line.c_str(),mapSequence,mapPhase,mapAccuracy))rejectedPackets++;return;}
  if(line.startsWith("M,")||line.startsWith("H,")){if(!mapCommand(line))rejectedPackets++;return;}
  float v,k; unsigned long s; char t; int m,used=0;
  // %n records consumed characters but does not count as a conversion.
  if(sscanf(line.c_str(),"D,%f,%f,%lu,%c,%d%n",&v,&k,&s,&t,&m,&used)==5 &&
     used==(int)line.length() && isfinite(v) && isfinite(k) &&
     v>=-1 && v<=150 && k>=0 && k<=100000 && s<=31536000 &&
     m>=-1 && m<=1000000 && strchr("SLRUANO",t)) {
    speed=v; trip=k; rideSeconds=s; turn=t; remaining=m;
    lastData=millis(); haveData=true; demo=false;
    acceptedPackets++;
  } else if(line.length()) {rejectedPackets++;}
}
void input(Stream &port, String &buffer) {
  for(int n=0; n<160 && port.available(); n++) {
    char c=port.read();
    if(c=='\n') {command(buffer); buffer="";}
    else if(c!='\r') {if(buffer.length()<1536) buffer+=c; else buffer="!OVERFLOW!";}
  }
}
void label(int x,int y,const String &s,int size,uint16_t color) {
  frame.setFont(NULL);frame.setTextColor(color); frame.setTextSize(size); frame.setCursor(x,y); frame.print(s);
}
void arrow(char t) {
  if(t=='O'){frame.drawCircle(67,155,16,GREEN);frame.drawCircle(67,155,15,GREEN);label(64,152,mapExit>0?String(mapExit):"?",1,FG);return;}
  if(t=='A') {frame.drawCircle(67,155,16,GREEN);frame.fillCircle(67,155,6,GREEN);return;}
  if(t=='L' || t=='R') {
    int d=t=='L'?-1:1;
    frame.fillRect(63,152,8,26,GREEN);
    frame.fillRect(d<0?39:63,148,32,8,GREEN);
    int tip=67+d*34;
    frame.fillTriangle(tip,152,tip-d*17,136,tip-d*17,168,GREEN);
  } else if(t=='U') {
    frame.drawRoundRect(48,137,38,37,15,GREEN);
    frame.drawRoundRect(49,138,36,35,14,GREEN);
    frame.fillTriangle(40,159,58,159,49,177,GREEN);
  } else {
    frame.fillRect(63,149,8,29,GREEN);
    frame.fillTriangle(67,132,49,153,85,153,GREEN);
  }
}
String distanceText(int meters){if(meters<0)return "-- m";if(meters>=1000)return String(meters/1000.0,meters<10000?1:0)+" km";int step=meters>=100?10:meters>=20?5:1;return String((meters+step/2)/step*step)+" m";}
void navGlyph(char t,int x,int y,uint16_t color){
  if(t=='L'||t=='R'){int d=t=='L'?-1:1;frame.fillRect(x-d*6-2,y-9,5,22,color);frame.fillRect(x-7,y-11,15,5,color);frame.fillTriangle(x+d*14,y-9,x+d*4,y-18,x+d*4,y,color);}
  else if(t=='O'){frame.drawCircle(x,y,11,color);frame.drawCircle(x,y,10,color);label(x-(mapExit>=10?6:3),y-3,mapExit>0?String(mapExit):"?",1,color);}
  else if(t=='U'){frame.drawRoundRect(x-8,y-14,17,22,7,color);frame.fillTriangle(x-13,y+4,x-3,y+4,x-8,y+12,color);}
  else if(t=='N'){label(x-5,y-8,"?",2,color);}
  else if(t=='A'){frame.drawCircle(x,y,10,color);frame.fillCircle(x,y,4,color);}
  else {frame.fillRect(x-2,y-2,5,17,color);frame.fillTriangle(x,y-16,x-9,y-4,x+9,y-4,color);}
}
void mapView(float v,float tripKm,unsigned long secs) {
  frame.fillScreen(BG);frame.setTextWrap(false);
  displayHeading=NavMath::smooth(displayHeading,mapHeading,0.45f);
  const uint16_t PANEL=0x10C4,CYAN=0x4EFF;
  bool roads=roadSequence==mapSequence&&roadCount>0;
  frame.fillCircle(8,8,2,mapReplay?0xFD20:GREEN);
  label(15,4,"VELO",1,FG);
  label(87,4,mapReplay?"REPLAY":mapPhase==9?"GPS FIX":mapPhase==10?"STOP":mapPhase==7?"GPS ?":"GPS",1,mapReplay?0xFD20:MUTED);
  frame.setFont(&FreeSansBold18pt7b);frame.setTextSize(1);frame.setTextColor(FG);frame.setCursor(5,46);frame.print(v>=0?String(v,1):"--.-");
  label(104,36,"km/h",1,MUTED);
  frame.fillRect(4,54,128,128,PANEL);
  // Geographic road sketch sits below the planned route and shares its camera.
  if(roads)for(int kind=0;kind<3;kind++)for(int i=0;i<roadCount;i++){
    if(roadSegments[i][4]!=kind)continue;
    float a=roadSegments[i][0],b=roadSegments[i][1],c=roadSegments[i][2],d=roadSegments[i][3];
    float angle=NavMath::delta(mapHeading,displayHeading);NavMath::rotate(a,b,64,92,angle);NavMath::rotate(c,d,64,92,angle);
    if(!NavMath::clip(a,b,c,d))continue;
    int x1=4+lroundf(a),y1=54+lroundf(b),x2=4+lroundf(c),y2=54+lroundf(d);
    uint16_t color=kind==0?0x3269:kind==1?0x4B0C:0x6C10;
    frame.drawLine(x1,y1,x2,y2,color);
    if(kind==2){if(abs(y2-y1)>abs(x2-x1))frame.drawLine(constrain(x1+1,4,131),y1,constrain(x2+1,4,131),y2,color);else frame.drawLine(x1,constrain(y1+1,54,181),x2,constrain(y2+1,54,181),color);}
  }
  for(int pass=0;pass<2;pass++)for(int i=0;i<mapCount;i++){
    if(mapSegments[i][4]!=pass)continue;
    float a=mapSegments[i][0],b=mapSegments[i][1],c=mapSegments[i][2],d=mapSegments[i][3];
    if(mapHeadingUp){float angle=NavMath::delta(mapHeading,displayHeading);NavMath::rotate(a,b,64,92,angle);NavMath::rotate(c,d,64,92,angle);}
    if(!NavMath::clip(a,b,c,d))continue;
    int x1=4+lroundf(a),y1=54+lroundf(b),x2=4+lroundf(c),y2=54+lroundf(d);
    uint16_t color=pass?GREEN:0x5B8C;
    // A dark casing keeps the selected route distinct at crossings.
    bool vertical=abs(y2-y1)>abs(x2-x1);
    for(int j=-2;j<=2;j++)frame.drawLine(constrain(x1+(vertical?j:0),4,131),constrain(y1+(vertical?0:j),54,181),constrain(x2+(vertical?j:0),4,131),constrain(y2+(vertical?0:j),54,181),PANEL);
    for(int j=-1;j<=1;j++)frame.drawLine(constrain(x1+(vertical?j:0),4,131),constrain(y1+(vertical?0:j),54,181),constrain(x2+(vertical?j:0),4,131),constrain(y2+(vertical?0:j),54,181),color);
  }
  if(!roads){frame.fillRoundRect(8,59,65,12,3,BG);label(11,62,"ROUTE ONLY",1,MUTED);}
  float radians=(mapHeadingUp?displayHeading:0)*PI/180.0;
  frame.fillCircle(119,68,10,BG);frame.drawCircle(119,68,9,0x4B0C);label(117-sin(radians)*5,65-cos(radians)*5,"N",1,FG);
  int cx=68,cy=mapHeadingUp?146:118;float marker=mapHeadingUp?0:mapHeading*PI/180.0;
  uint16_t position=mapPhase==8?0xFA08:mapQuality==0&&!mapReplay?0xFD20:CYAN;
  frame.fillCircle(cx,cy,10,BG);frame.drawCircle(cx,cy,9,FG);
  if(mapPhase==9||(mapPhase==7&&mapQuality==0))frame.fillCircle(cx,cy,4,position);else frame.fillTriangle(cx+sin(marker)*8,cy-cos(marker)*8,cx-sin(marker)*5+cos(marker)*5,cy+cos(marker)*5+sin(marker)*5,cx-sin(marker)*5-cos(marker)*5,cy+cos(marker)*5-sin(marker)*5,position);
  int scale=NavMath::scaleMeters(mapSpan),pixels=lroundf(scale*128.0f/mapSpan);
  frame.fillRoundRect(8,162,52,17,3,BG);label(11,164,String(scale)+" m",1,MUTED);frame.drawFastHLine(11,176,pixels,FG);frame.drawFastVLine(11,173,4,FG);frame.drawFastVLine(11+pixels-1,173,4,FG);
  bool uncertain=mapPhase==7||mapPhase==8||mapPhase==9;
  bool urgent=!mapOffRoute&&!uncertain&&strchr("LRUO",turn)&&(mapPhase>=0?mapPhase==3:remaining>=0&&remaining<=30);
  uint16_t banner=mapPhase==8?0xC9A3:mapOffRoute||mapPhase==7?0x9240:urgent?GREEN:0x1925,ink=urgent?BG:FG;
  frame.fillRoundRect(4,187,128,39,6,banner);
  navGlyph(mapOffRoute||uncertain?'N':turn,22,207,ink);
  String instruction=mapOffRoute?"OFF ROUTE":mapPhase==8?"WRONG WAY":mapPhase==7?"CHECK GPS":mapPhase==9?"GPS FIX OK":mapPhase==10?"STOPPED":turn=='L'?"TURN LEFT":turn=='R'?"TURN RIGHT":turn=='U'?"U TURN":turn=='O'?"ROUNDABOUT":turn=='A'?"ARRIVED":turn=='N'?"CHECK ROUTE":"STRAIGHT";
  label(42,192,instruction,1,ink);
  String detail=mapOffRoute?"REPLAN":mapPhase==8?"TURN BACK":mapPhase==7?"WAIT GPS":mapPhase==9?"MOVE TO SET":turn=='A'?"FINISH":turn=='N'?"CHECK":distanceText(remaining);
  if(mapOffRoute||uncertain||turn=='A'||turn=='N')label(42,210,detail,1,ink);
  else {frame.setFont(&FreeSansBold12pt7b);frame.setTextSize(1);frame.setTextColor(ink);frame.setCursor(40,221);frame.print(detail);}
  label(5,232,distanceText(mapLeft)+" LEFT",1,MUTED);label(111,232,roads?"OSM":"GPS",1,roads?MUTED:0x5B6B);
}

void draw() {
  unsigned long now=millis();
  bool valid=demo || (haveData && now-lastData<5000);
  float v=speed,k=trip; unsigned long secs=rideSeconds; char t=turn; int m=remaining;
  if(demo) {
    v=23.5+4*sin(now/4000.0); secs=now/1000; k=secs*23.5/3600;
    t=(now/10000)%2?'L':'S'; m=300-(now/100%300);
  }
  bool mapFresh=!demo && valid && haveMap && now-mapUpdated<5000;
  if(mapFresh&&!showStats&&mapHeadingUp&&roadSequence!=mapSequence&&now-mapUpdated<80)return;
  if(mapFresh && !showStats){mapView(v,k,secs);lcd.drawRGBBitmap(0,0,frame.getBuffer(),135,240);return;}
  if(!demo&&gpsWait&&valid&&!showStats){
    frame.fillScreen(BG);label(7,7,"VELO",1,GREEN);label(7,52,"GPS WAIT",2,0xFD20);label(7,91,"LOCATION LOST",1,FG);label(7,110,"NO OLD DIRECTIONS",1,MUTED);label(7,231,"PHONE GPS REQUIRED",1,MUTED);lcd.drawRGBBitmap(0,0,frame.getBuffer(),135,240);return;
  }
  if(!demo&&haveMap&&!mapFresh){t='N';m=-1;}
  frame.fillScreen(BG);
  label(7,7,"VELO",1,GREEN);
  label(65,7,demo?"DEMO":mapFresh && mapReplay?"REPLAY":valid?"LIVE":"NO DATA",1,demo || (mapFresh && mapReplay)?0xFD20:MUTED);
  frame.drawFastHLine(7,23,121,0x2945);
  String sp=valid && v>=0?String(v,1):"--.-";
  label((135-sp.length()*24)/2,36,sp,4,FG);
  label(53,73,"km/h",1,MUTED);
  frame.drawFastHLine(7,92,121,0x2945);
  label(8,101,"TRIP km",1,MUTED); label(76,101,"TIME",1,MUTED);
  label(8,116,valid?String(k,2):"--",1,FG);
  char timeText[20]; snprintf(timeText,sizeof(timeText),"%02lu:%02lu:%02lu",secs/3600,secs/60%60,secs%60);
  label(76,116,valid?String(timeText):"--:--",1,FG);
  if(mapFresh&&mapPhase>=6&&mapPhase<=9){
    label(8,150,NavState::title(mapPhase),1,mapPhase==8?0xFA08:FG);
    label(8,171,NavState::help(mapPhase),1,MUTED);
    label(8,199,"LEFT "+distanceText(mapLeft),1,GREEN);
  } else if(valid&&gpsWait){
    label(8,153,"WAITING FOR GPS",1,0xFD20);label(8,175,"TRIP DATA KEPT",1,MUTED);
  } else if(valid && t=='N') {
    label(19,153,"NO TURN DATA",1,MUTED);
    label(19,174,"CHECK PHONE",1,MUTED);
  } else if(valid) {
    arrow(t);
    String dist=t=='A'?"ARRIVED":m<0?"-- m":String(m)+" m";
    label((135-dist.length()*12)/2,187,dist,2,GREEN);
    String hint=t=='L'?"TURN LEFT":t=='R'?"TURN RIGHT":t=='U'?"U TURN":t=='O'?"ROUNDABOUT":t=='A'?"DESTINATION":"GO STRAIGHT";
    label((135-hint.length()*6)/2,213,hint,1,FG);
  } else {label(16,157,"WAITING FOR",1,MUTED); label(16,174,"PHONE DATA",1,MUTED);}
  label(7,231,demo || (mapFresh && mapReplay)?"SIMULATED DATA":mapFresh?mapPhase==9?"MOVE TO START":mapPhase==10?"STOPPED - GPS OK":"PHONE CONNECTED":gpsWait?"GPS SIGNAL LOST":valid?"PHONE CONNECTED":"CONNECT PHONE",1,MUTED);
  lcd.drawRGBBitmap(0,0,frame.getBuffer(),135,240);
}
void setup() {
  Serial.begin(115200);
  pinMode(0,INPUT_PULLUP);
  pinMode(32,OUTPUT); digitalWrite(32,HIGH);
  SPI.begin(18,-1,23,15);
  lcd.init(135,240); lcd.setRotation(0); lcd.setSPISpeed(27000000);
  lcd.fillScreen(BG);
  phone.begin("VELO-ESP32");
  Serial.println("VELO ready. Waiting for phone. Bluetooth: VELO-ESP32");
  Serial.println("LIVE or D,23.5,1.25,600,L,120 (newline terminated)");
}
void loop() {
  bool pressed=digitalRead(0)==LOW;
  if(pressed!=buttonDown && millis()-buttonChanged>80){buttonChanged=millis();buttonDown=pressed;if(pressed)showStats=!showStats;}
  input(Serial,usbLine); input(phone,btLine);
  if(millis()-lastFrame>=200) {lastFrame=millis();draw();}
  delay(2);
}
