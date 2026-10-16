# 迁移目录验证记录 / Portability checks

2026-10-16：从本仓库独立目录重新构建 Android 1.0.4 成功，APK v2/v3 签名验证通过。

使用 scripts/test-android.ps1 -Suite clear，在 API 34 的可丢弃模拟器中对本仓库重编译 APK 运行 19 项检查，结果通过。包含清除路线、取消、冷加载、保留收藏与路线库、结束骑行记录和界面过渡。

三语 README 的本地图片和相互链接均已检查存在。公开文件已检查不含 APK 签名密钥、GitHub 访问令牌、用户手机数据库和手机原始照片。SHA256SUMS.txt 用于核对随包 APK 与固件二进制。

这验证了迁移目录的构建与部分运行流程，不代表已经在另一台物理电脑或 TCL 手机完成测试。Android SDK、JDK、Arduino 核心仍需在新电脑安装；签名密钥需私下恢复。
