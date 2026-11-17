# 天地历 Android 安装包与兼容性

本次重新编译的文件为 `tiandili-0.1.0-universal.apk`，约 2.90 MiB。它是独立签名 APK，不需要另外下载分包。

## 支持范围

| 项目 | 结果 |
| --- | --- |
| Android 系统 | 最低 Android 8.0 / API 26；targetSdk 36 |
| 普通手机架构 | ARM 32 位 armeabi-v7a、ARM 64 位 arm64-v8a |
| 其他架构 | x86、x86_64 |
| 字节码 | DEX 038，适用于 Android 8.0 起的运行时 |
| 签名 | Android API 26–36 范围验证通过 |
| 16 KB 内存页 | APK ZIP 对齐及全部 8 个原生库的 ELF LOAD 段检查通过 |
| 原生依赖 | 所需动态库均为 Android 系统库，没有遗漏的第三方动态库 |
| 包名与版本 | com.tiandili.calendar，0.1.0 / versionCode 1 |

## 本次验证

重新执行了 Release Kotlin 编译、APK 打包签名与 Release Lint；构建成功，Lint 结果为 0 问题。

Robolectric 在 Android 8.0（API 26）和 Android 15（API 35）两个平台模型下，完成了应用启动、月历、日程、天气、设置及返回月历的检查，共 2 项通过，无失败或跳过。

另有 4 项实际压缩 APK 结构检查通过，覆盖 Room / Worker / Moshi 反射入口、缓存枚举字段保留、完整许可文本，以及开发预览 Activity 的排除。

上述平台模型检查运行在 JVM 中。当前没有连接 Android 真机，未进行手机安装、实机通知到达或厂商启动器小组件测试。

## 手机上安装

1. 将 APK 文件保存或传送到手机。
2. 在手机文件管理器中打开 APK。
3. 如果系统提示，允许当前文件管理器或浏览器“安装未知应用”，然后确认安装。
4. 启动天地历。保存提醒或使用当前地点天气时，按应用提示授予对应权限。

本包复用此前发布版的签名，可覆盖安装相同包名、相同签名的已有发布版。若手机已安装其他签名的同包名应用，系统会拒绝覆盖；需要先导出个人日程备份，再卸载旧应用并安装本包。

手机系统低于 Android 8.0 时无法安装本包。当前版本未提供 Android 7 及更早系统的支持。

## 文件校验

APK SHA-256：

```text
b9ff385c0decaaf42d6f0735daf1ad76161a1ca1dae3ffec300e8f40021725e5
```

校验和另见 `SHA256SUMS.txt`；架构、签名证书、原生库和来源提交记录见 `compatibility.json`，平台启动检查记录见 `startup-check.json`。
