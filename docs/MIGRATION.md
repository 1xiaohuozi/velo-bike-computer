# VELO 项目迁移与维护手册

整理日期：2026-10-16（日本时间）。当前 App 1.0.4 / versionCode 104；固件 0.10.1。原作者账号：1xiaohuozi。仓库名称 velo-bike-computer。

## 1. 新电脑第一步

```powershell
git clone https://github.com/1xiaohuozi/velo-bike-computer.git
cd velo-bike-computer
```

也可在 GitHub 点击 Code → Download ZIP。无需安装整个旧电脑的 Codex 环境，正常开发不依赖聊天记录。

- 直接使用：安装 dist/VELO-PhoneBridge-v1.0.4.apk；同一手机覆盖安装，不要先卸载。
- 继续开发：安装下面的工具，恢复私有签名备份，然后执行 scripts/build-android.ps1。
- 码表已装有 0.10.1 时，无需因为迁移电脑而重新烧录。COM8 是旧电脑端口，换电脑可能改变。
- Android 源码可用 Android Studio 或任意编辑器浏览，但没有 Gradle 工程配置，不能直接假定 Android Studio 的 Run 按钮可构建。实际入口是 PowerShell 脚本。

## 2. 原环境及新电脑依赖

| 工具 | 原环境 / 需要的内容 |
| --- | --- |
| 系统 | Windows + PowerShell；脚本使用 .exe / .bat，不宣称支持 macOS / Linux |
| Java | JDK 20.0.1；Java 源码按 source/target 8 编译，脚本使用 jar --create，需要现代 JDK |
| Android | SDK Platform 35、Build Tools 35.0.0、Platform Tools |
| App 配置 | 包名 local.velo.bridge，minSdk 26，targetSdk 34，versionCode 104 |
| Arduino CLI | 原环境 1.5.1；Arduino IDE 也可以打开固件草图 |
| ESP32 开发板包 | esp32:esp32 3.3.12；开发板 ESP32 Dev Module |
| Arduino 库 | BusIO 1.17.4、GFX 1.12.6、ST7735/ST7789 1.11.0，源码已同梱 |
| 地图引擎 | MapLibre GL JS 5.10.0，JS/CSS 已同梱，不需要 npm 构建 |
| 可选测试 | Android 模拟器 API 34；C++ 测试需要 g++ 或等效编译器 |

JDK、Android SDK、ESP32 核心和编译器体积较大，未打入仓库。新电脑安装后设置：

```powershell
$env:JAVA_HOME='C:\path\to\jdk'
$env:ANDROID_SDK_ROOT='C:\path\to\Android\Sdk'
.\scripts\check-environment.ps1
```

Android 构建脚本依次使用 ANDROID_SDK_ROOT、ANDROID_HOME 或 LOCALAPPDATA/Android/Sdk。请通过 Android SDK Manager 安装上述版本。

Arduino CLI 安装 ESP32 核心示例：

```powershell
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.3.12 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
```

## 3. 原 APK 签名密钥：必须私下迁移

原电脑 outputs/VELO-签名密钥备份-请勿上传GitHub.zip 包含原密钥与密码说明，它不属于 Git 仓库。请通过可信的私有方式带到新电脑。

1. 解压其中 private-signing 目录到仓库根目录。
2. 按私有备份中的说明设置密码；不要把密码提交 Git。

```powershell
$env:VELO_SIGNING_KEY=(Resolve-Path '.\private-signing\velo-test.keystore').Path
$env:VELO_KEY_ALIAS='velo'
$env:VELO_KEYSTORE_PASSWORD='从私有备份读取'
$env:VELO_KEY_PASSWORD='从私有备份读取'
.\scripts\build-android.ps1
```

输出 dist/VELO-PhoneBridge-v1.0.4.apk。若需要发布下一版，同时更新 android/AndroidManifest.xml 的 versionCode/versionName 和构建脚本输出文件名，避免版本号与文件名错位。

仓库中 APK 已签名，安装不需要密钥。只有重新构建且需要覆盖旧安装时，才必须使用同一签名。请勿将新生成的密钥冒充原密钥；Android 不允许不同签名直接覆盖。当前签名方案用于开发，应当保密，尚未配置 Play 分发、签名轮换或生产发布流程。

## 4. ESP32、显示屏与烧录

硬件为 Ideaspark ESP32-WROOM-32，板载 ST7789 135×240、CH340、USB-C。没有外接 GPS、轮速传感器或电子罗盘。无线使用 Bluetooth Classic SPP，设备名 VELO-ESP32，UUID 00001101-0000-1000-8000-00805F9B34FB。ESP32-S3 等不支持相同经典蓝牙的型号不能直接替换。

| 屏幕信号 | GPIO |
| --- | --- |
| SCLK | 18 |
| MOSI | 23 |
| CS | 15 |
| DC | 2 |
| RST | 4 |
| BL | 32，HIGH 点亮 |
| 页面切换 | BOOT/GPIO0，INPUT_PULLUP，LOW 表示按下 |

初始化：SPI.begin(18,-1,23,15)，lcd.init(135,240)，rotation=0，SPI 27 MHz。串口 115200。帧缓冲为 RGB565 135×240；约每 200 ms 绘制一次。具体物理 PCB 引脚以该型号板卡为准。

```powershell
# 仅从源码构建
.\scripts\firmware.ps1 -Action build
# 上传仓库附带的已验证旧固件。此命令会写入板子。
.\scripts\firmware.ps1 -Action upload -Port COM8
# CLI 不在 PATH 时
.\scripts\firmware.ps1 -Action build -Cli 'C:\path\to\arduino-cli.exe'
```

编译输出位于 work/firmware-build。upload 操作明确使用 firmware/prebuilt-v0.10.1 中的随包固件，不会自动上传刚刚重编译的产物。要上传新编译结果，使用 arduino-cli upload --input-dir work/firmware-build，并填写实际串口和 fqbn。

若屏幕亮但空白，先核查型号、GPIO32 背光、CS/DC/RST 引脚与驱动初始化，不要随意套用其他 ST7789 板子的配置。烧录时关闭串口监视器；必要时按住 BOOT 进入下载模式。

## 5. 模块与数据流

| 文件 / 模块 | 职责 |
| --- | --- |
| android/src/local/velo/bridge/MainActivity.java | 地图、收藏、路线、设备四个入口，地点角色、规划交互、清除路线 |
| BridgeActivity.java | 公共控件、权限、定位及历史兼容逻辑；不是当前首页 |
| UiStyle / UiIcon / MapSheet | 原生样式、图标、动画及可拖动面板 |
| AppStore.java | SQLite 数据库 velo-personal.db，schema 2：收藏、分组、路线、骑行、迁移标记 |
| PlaceCoordinates / PlaceSearch | 度分秒与小数坐标解析、Photon 搜索及系统搜索备用 |
| BikePlanner / RouteCodec | BRouter 路线请求、分段合并、JSON 保存 |
| RouteStore | 活跃路线模型及持久化；last_source=none 阻止清除后恢复旧缓存 |
| RoutePreview / VectorMap / MapMath | WebView 中的 MapLibre 底图与原生路线/标记覆盖层 |
| RideService | 前台定位、速度、里程、时间、匹配、转向、暂停、记录 |
| DeviceLinkService | 独立经典蓝牙连接、自动重试、每秒发送、最近发送内容 |
| RouteTracker / TravelDirection / WrongWayMonitor | 路线匹配、移动航向及持续反向状态 |
| HeadingFilter / ViewScale / NavPresentation | 航向平滑、显示尺度、定位正常但方向未确定的展示 |
| NearbyRoads / MvtRoads / RoadContext | 获取并解析周边矢量路网，生成小屏道路线段 |
| BoardPreview | 按已发送数据重绘手机中的码表预览，不读取硬件帧缓冲或实际页面 |
| RideLog / DiagnosticsProvider | 本机诊断与用户发起的导出 |
| firmware/BikeDash/*.h | 航向数学、导航阶段、状态文案和周边道路线段验证 |

当前主流程不依赖 Google 地图通知。MapsListener/NavParser 等历史兼容源码仍保留，但不能据此认为当前 App 必须开启通知读取权限。

## 6. 蓝牙/串口协议概要

ASCII 文本，以换行分帧。单位：速度 km/h，累计距离 km，时间秒，导航距离 m。

```text
D,speed,trip,seconds,turn,meters
H,seq,R|G,off,turn,meters,left,span,heading,exit,quality,n,x1,y1,x2,y2,pass,...
B,seq,n,x1,y1,x2,y2,kind,...
T,seq,phase,accuracy
```

- turn：S 直行、L 左转、R 右转、U 掉头、O 环岛、A 到达、N 无可靠转向。
- H：朝向朝上的路线帧，最多 40 段，坐标 0～127；R 为回放，G 为 GPS。
- B：周边道路，最多 72 段；与 H 的 seq 对齐，kind 为道路等级。
- T：phase 0～10；5 到达、6 偏航、7 定位/匹配待确认、8 反向、9 定位有效但等待移动确定方向、10 停止。具体判定以源码为准。
- 命令 LIVE、DEMO、MAPOFF、GPSWAIT、PAGE、STATUS；USB SCREEN 可导出调试帧缓冲，目前不是手机远程屏幕接口。
- 超过约 5 秒无有效数据则不继续显示陈旧路线。手机端每秒发送，不存在硬件画面确认回执。

## 7. 手机权限、操作与个人数据

1. 系统蓝牙中配对 VELO-ESP32，在 App 设备页选定一次；以后打开 App 自动连接。
2. 开始骑行前允许精确定位、附近设备；通知权限用于看见常驻状态。
3. 在地图详情明确设为起点/终点，按需添加途经点，确认坐标，再点击规划。
4. 获取当前位置不会自动提交路线。新建路线清空旧草稿；收藏的加入顺序不决定起终点。
5. 首页/规划页可清除当前路线。骑行中先确认结束并保存记录；已保存路线与收藏保留。

迁移电脑不必迁移手机数据，同一手机覆盖安装可继续使用其本地数据。若同时更换手机，请先从旧手机导出收藏 GeoJSON、需要的路线 GPX 和诊断记录，再在新手机导入支持的数据。当前 allowBackup=false；没有整库自动云同步或骑行记录的完整跨手机恢复工具。不要假定下载源码就会带走手机个人数据。

本仓库不包含用户手机数据库、原始手机照片、调试账号凭证、APK 签名密钥、Android SDK、JDK 或整套 Arduino 核心。

## 8. 测试与已知限制

```powershell
# 需要先构建 App，并启动可丢弃的 API 34 模拟器。
.\scripts\test-android.ps1 -Suite clear -Device emulator-5554
# 可选：native / ride / preview / route / clear
```

测试会修改收藏和路线、授予/撤回模拟器权限、启动/结束骑行；只允许 emulator-* 设备，不要对日常手机运行。native 与 route 使用真实公共网络服务，不能当作离线稳定测试。自动化未设置为 GitHub CI。

开发时各版本分别通过：清除/UI 19 项、应用集成 59 项、指定起终点 31 项、码表预览 16 项、GPS 服务 23 项。它们不是同一次全量验收，不代表所有手机与户外场景都验证通过。

尚需完成：锁屏/省电/后台长时间实机测试；进程退出后的自动恢复；偏航重规划体验；硬件实际状态回传；更完整离线缓存；真实户外转向与反向提示验证。公共 Photon/BRouter/OpenFreeMap 服务无项目提供的 SLA。

不要声称已具备罗盘、完全独立于手机的 GPS 导航、全离线地图、成熟商业导航的可靠性或像素级硬件镜像。用户要求的后台稳定性方案已讨论，但唤醒锁和完整恢复功能还未据此实现。

## 9. 交接给下一位开发者

先阅读本手册、三语 README、MainActivity / RouteStore / RideService / DeviceLinkService，再阅读近期 docs/history/VELO-v1.0.x 文档。历史文档中的旧局域网 IP、旧版本与早期 Google 通知方案不可当作当前配置。不要丢弃原签名密钥，不要把 work 构建缓存提交 Git。

维护 UI 时重点回归：起终点不会被定位覆盖；途经点顺序；长按坐标与底图对齐；面板拖动；清除路线后重启不复活；结束骑行保持蓝牙；预览不把陈旧发送数据当作实时硬件画面。
