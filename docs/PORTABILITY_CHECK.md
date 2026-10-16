# 迁移目录验证记录 / Portability checks

2026-10-16：从本仓库独立目录重新构建 Android 1.0.4 成功，APK v2/v3 签名验证通过。

使用 scripts/test-android.ps1 -Suite clear，在 API 34 的可丢弃模拟器中对本仓库重编译 APK 运行 19 项检查，结果通过。包含清除路线、取消、冷加载、保留收藏与路线库、结束骑行记录和界面过渡。

三语 README 的本地图片和相互链接均已检查存在。公开文件已检查不含 APK 签名密钥、GitHub 访问令牌、用户手机数据库和手机原始照片。SHA256SUMS.txt 用于核对随包 APK 与固件二进制。

这验证了迁移目录的构建与部分运行流程，不代表已经在另一台物理电脑或 TCL 手机完成测试。Android SDK、JDK、Arduino 核心仍需在新电脑安装；签名密钥需私下恢复。

同日从 firmware/BikeDash 源码及同梱 Adafruit 库完成 ESP32 编译：程序 1,134,024 / 1,310,720 字节，静态 RAM 44,252 / 327,680 字节。仅编译，未重新烧录实物。仓库 prebuilt-v0.10.1 保持原先已交付的二进制，新的编译结果保存在忽略的 work/firmware-build，不承诺与历史二进制逐字节一致。

迁移版 scripts/test-firmware.ps1 也已运行通过，覆盖角度/旋转/裁剪、导航阶段、状态文案及道路线段边界与原子更新四组 C++ 测试。
