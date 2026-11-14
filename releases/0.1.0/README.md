# 天地历 0.1.0

安装文件：`tiandili-0.1.0-release.apk`。包名 `com.tiandili.calendar`，支持 Android 8.0 及以上，versionCode `1`，targetSdk `36`。

将 APK 传到手机后打开安装。若使用已授权的 USB 设备，可运行：

```sh
adb install -r tiandili-0.1.0-release.apk
```

已安装的同包名应用需要同一证书才能覆盖升级。构建使用的本地密钥保存在项目 `.signing` 目录，请独立备份，升级时复用。该目录不包含在本交付包中。

本版包含月历与农历黄历、日程与重复规则、提醒、本地天气缓存、多城市天气、设置、JSON 备份导入导出，以及 2×2 / 4×2 桌面小组件。首次使用提醒或当前地点天气时，请按应用提示授予对应权限。

校验和见 `SHA256SUMS.txt`，包信息与签名证书见 `release-metadata.json`，验证范围与限制见 `VERIFICATION.md`。`widget-previews` 内图片使用固定测试数据，包含正常字体和 1.3 倍字体预览。

当前环境未运行真机或模拟器端到端测试。手机上仍需验证首次启动、通知与重启恢复、导入导出、天气请求及小组件添加和点击。详细构建流程见项目 `docs/RELEASE.md`。
