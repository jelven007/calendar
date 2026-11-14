# 天地历 0.1.0 验证记录

验证日期：2026-11-08。签名安装包已构建，支持 Android 8.0 及以上；当前验证范围不包含设备端运行。

## 交付包

| 项目 | 结果 |
| --- | --- |
| 文件 | tiandili-0.1.0-release.apk |
| 包名 | com.tiandili.calendar |
| 版本 | 0.1.0 / versionCode 1 |
| minSdk / targetSdk | 26 / 36 |
| 文件大小 | 3,039,633 字节，约 2.90 MiB |
| 构建 | Release，R8 与资源压缩开启，debuggable=false |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64 |
| 签名 | APK Signature Scheme v2、v3 均验证通过 |
| ZIP / 原生库对齐 | ZIP 16 KB 检查通过；8 个原生库的 LOAD 段均至少 16 KB 对齐 |

APK SHA-256：

```text
ac8babae18fc54828600854c42dddccd00ae9b6ade926e16c038610afc3bd459
```

签名证书 SHA-256：

```text
b80e3f9ddf83d3f70b9e7c7a246adadb124885899174a86776123ca9e50f70bc
```

签名为本地生成的 RSA 3072 位发布证书。后续更新须复用项目 `.signing` 目录中的密钥；交付包不包含该目录。

## 新鲜验证结果

| 检查 | 结果 |
| --- | --- |
| testDebugUnitTest | 57 个测试套件，225 项通过，0 失败 / 错误 / 跳过 |
| testWidgetRendering | 独立 JVM 中 2 项通过，0 失败 / 错误 / 跳过 |
| 发布工具与实际 APK 检查 | 8 项通过 |
| Debug Lint | 0 问题 |
| Release Lint | 0 问题 |
| Debug APK 构建 | 通过 |
| 签名 Release APK 构建 | 通过 |
| Android 仪器测试 Kotlin 编译 | 通过，未运行 |
| apksigner / zipalign | 均返回 0 |

构建与 Android 测试命令：

```sh
scripts/gradlew-local.sh :app:testDebugUnitTest --rerun \
  :app:testWidgetRendering --rerun :app:compileDebugAndroidTestKotlin \
  :app:lintDebug :app:assembleDebug
python3 scripts/build_release.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

原始证据保存在 `verification-build.log`、`release-build.log`、`release-tools-tests.txt`、`test-results.json`、两份 Lint XML、`signature-verification.txt` 和 `alignment-verification.txt`。

## 发布阶段修正与检查

1. 首次压缩 APK 将 `CacheFreshness` 的 FRESH / STALE / EXPIRED 字段改名。Moshi 的枚举适配器按原名反射查找字段，因此补入仅针对该枚举的保留规则。实际 APK 的 DEX 检查先失败，修复后通过。
2. 日历依赖的运行时 Compose 预览 Activity 被带入发布清单，已通过 Release 专用清单移除。最终 APK 的清单及 DEX 均确认没有该入口。删除标记上的 `MissingClass` 抑制仅针对标记本身。
3. 实际 DEX 保留 Room 数据库实现、三项后台 Worker 和小组件的三个生成 JSON 适配器。此检查确认结构保留，未声称运行时端到端通过。
4. APK 内许可文本与核验后的文本逐字节一致。运行时解析得到 107 个归档、106 个不同 Maven 坐标；已补齐两份 MIT 原文、Apache 2.0、Protobuf BSD、OkHttp 公共后缀 NOTICE 和 MPL 2.0。许可来源与指纹随包归档。
5. 密钥工具测试确认有效密钥可复用，凭据缺失或密钥损坏时停止，不覆盖已有文件，也不输出密码。

`widget-previews` 保留正常字体和 1.3 倍字体的 2×2 / 4×2 小组件预览，使用固定样本数据；已查看紧凑布局和大字体宽布局。图片来自 Robolectric 原生渲染，不代表特定手机启动器截图。

## 验证边界

当前宿主环境无法启动 Android 模拟器：硬件虚拟化访问被拒绝，软件加速启动也失败。未进行真机安装或模拟器端到端测试。

仍需在设备确认首次启动、日程提醒授权与通知到达、重启恢复、天气实际请求、备份导入导出，以及小组件添加、尺寸和点击。厂商后台限制与启动器差异属于未验证部分。
