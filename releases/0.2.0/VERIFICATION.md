# 天地历 0.2.0 验证记录

验证日期：2026-11-18。已生成签名 APK；未进行真机或模拟器端到端测试。

## 安装包

| 项目 | 结果 |
| --- | --- |
| 文件 | tiandili-0.2.0-release.apk |
| 包名与版本 | com.tiandili.calendar · 0.2.0 / versionCode 2 |
| minSdk / targetSdk | 26 / 36 |
| 大小 | 3,138,095 字节，约 2.99 MiB |
| 构建 | Release，R8 / 资源压缩，debuggable=false |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64 |
| 签名验证 | apksigner API 26–36；v2、v3 通过 |
| 对齐 | zipalign 16 KB 通过，8 个原生库的全部 ELF LOAD 段均为 16 KB 对齐 |
| DEX | 038，符合 Android 8 加载格式 |

APK SHA-256：

```text
2d1a54d979c62f1bb0cc2ced0964259f8d141e21af0efa038fb26e2eb359434b
```

证书 SHA-256 与 0.1.0 一致：

```text
b80e3f9ddf83d3f70b9e7c7a246adadb124885899174a86776123ca9e50f70bc
```

## 测试与构建

| 检查 | 结果 |
| --- | --- |
| 普通业务 / Compose 回归 | 272 项通过 |
| 独立 JVM 小组件渲染 | 2 项通过 |
| 独立 JVM 页面渲染与触控 | 9 项通过 |
| Android 8 / API 26 响铃服务 | 2 项通过 |
| Android 15 / API 35 响铃服务 | 2 项通过 |
| Android 8 / API 26 应用启动 | 1 项通过 |
| Android 15 / API 35 应用启动 | 1 项通过 |
| 完整回归合计 | 289 项，0 失败 / 错误 / 跳过 |
| 最终设置链接写法复核 | 12 项相关测试再次通过 |
| 发布工具与压缩 APK 检查 | 10 项通过 |
| Debug / Release Lint | 均 0 问题 |
| Debug / 签名 Release 构建 | 通过 |
| Android 仪器测试 Kotlin 编译 | 通过，未运行 |
| 原文许可与当前依赖核对 | 107 个归档 / 106 个坐标；APK 通知文本逐字节一致 |

实际压缩 APK 的 DEX 检查保留了 Room 实现、后台 Worker、小组件适配器、新子事项与更新元数据 JSON 适配器，以及响铃 Activity / Service。四 ABI、DEX 038 与每个 ELF LOAD 段直接从最终 APK 读取验证。

完整回归日志保留了一条 Robolectric 原生字体临时目录清理错误；该错误发生于测试后的清理阶段，测试报告与 Gradle 均成功，不属于应用运行时失败。最初两条链接 KTX 写法提示已修正，最终 Debug / Release Lint XML 均无问题。

## 原生预览与本次修正

`ui-previews` 包含月历、黄历、天气、我的、待办编辑、设置六个页面，以及 320 dp / 1.3 倍字体、深色、天气最后一天选中的额外预览。`preview-overview.png` 汇总六个正常页面。

这些图片来自实际 Compose View 树的原生 Canvas 渲染，使用固定样例记录和气象数据。验证明确允许长节日和事项标签采用省略号；其他可见文本检查未发生高度裁剪。天气测试通过真实触控事件纵向滚动、横向滑到第十五天并点击选择。

检查期间修正了冷启动导航图尚未创建时的跳转、已选待办分类被横向裁剪、编辑器仍显示底部导航及大字体长入口拥挤的问题。编辑器关闭后恢复导航。

## 数据与设备边界

旧数据库通过显式 3 → 4 迁移，旧 JSON 备份格式 1 可读，新格式为 2。照片保存系统文件引用，备份未打包原图；换机需另行复制照片并重新选择。

API 26 / 35 启动检查使用 JVM / Robolectric，验证实际 Application / MainActivity、数据库版本 4、子事项持久化及待办编辑器入口。签名和对齐检查也不等同于设备运行测试。

真机仍需检查实际通知和响铃、全屏闹钟授权、手机厂商后台限制、重启恢复、文件授权以及启动器小组件。当前宿主无法启动模拟器，未再次重试不可用的硬件或软件加速路径。

## 原始证据

`test-results.json`、`test-results/`、`final-settings-test-results/`、两份 Lint XML、`verification-build.log`、`final-settings-lint.log`、`release-build.log`、`release-tools-tests.txt`、`apk-badging.txt`、`AndroidManifest.xml`、签名与对齐输出、`native-alignment.json`、发布元数据、许可清单和 `mapping.txt.gz` 随包保留。
