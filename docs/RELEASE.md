# 天地历发布与复核

当前版本：`0.2.0`（versionCode `2`），包名 `com.tiandili.calendar`，Android 8.0 / API 26 起，targetSdk 36。

## 构建签名安装包

在项目根目录运行：

```sh
python3 scripts/build_release.py
```

脚本首次生成本地 RSA 3072 位 PKCS#12 密钥，后续构建复用同一密钥。输出为 `app/build/outputs/apk/release/app-release.apk`，启用 R8 和资源压缩。发布签名通过环境变量传给 Gradle，密码不写入构建配置或命令行参数。

本地密钥与凭据保存在 `.signing/tiandili-release.p12` 和 `.signing/signing.json`。请将整个 `.signing` 目录单独备份到可信位置；同包名覆盖升级需要同一签名。交付目录不包含密钥或密码。脚本在文件不完整或无法验证密钥时停止，不会自动替换现有密钥。

已有发布密钥可通过以下四个环境变量提供：

```text
TIANDILI_KEYSTORE_PATH
TIANDILI_KEY_ALIAS
TIANDILI_STORE_PASSWORD
TIANDILI_KEY_PASSWORD
```

生成签名配置但不构建：

```sh
python3 scripts/build_release.py --prepare-only
```

## 验证

```sh
scripts/gradlew-local.sh :app:testDebugUnitTest \
  :app:testWidgetRendering :app:testUiRendering \
  :app:testAlarmApi26 :app:testAlarmApi35 \
  :app:testStartupApi26 :app:testStartupApi35 :app:compileDebugAndroidTestKotlin \
  :app:lintDebug :app:assembleDebug
python3 scripts/build_release.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

页面、Glance 小组件、不同 API 的响铃和启动检查分别使用独立 JVM，避免 Robolectric 的原生字体及 SQLite 库在 SDK 沙箱间重复加载。页面预览为 Android 原生 Compose View 树经原生 Canvas 渲染的 PNG，样例记录与天气用于展示，不代表真机截图。天气趋势验证包含实际触控事件的纵向、横向滑动和最后一天点选。Android 仪器测试在当前环境只编译，未运行。

`test_release_archive.py` 读取真实压缩 APK 的 DEX，检查 Room、Worker、响铃组件和生成的 Moshi 适配器是否存在，以及缓存状态枚举字段是否保留。它还比较许可文本、确认不含开发预览 Activity，并检查四 ABI、DEX 038 与全部 ELF LOAD 段的 16 KB 对齐。这些属于发布结构检查，不能代替设备上的运行测试。

使用项目 JDK 和 SDK 检验签名与 ZIP 对齐：

```sh
export JAVA_HOME="$PWD/.toolchains/jdk-17/Contents/Home"
.android-sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs \
  --min-sdk-version 26 --max-sdk-version 36 \
  app/build/outputs/apk/release/app-release.apk
.android-sdk/build-tools/36.0.0/zipalign -c -P 16 4 \
  app/build/outputs/apk/release/app-release.apk
```

许可更新流程见 [原始许可核验](licenses/README.md)。发布时保存 APK、SHA-256、证书指纹、`mapping.txt`、依赖清单和验证报告；升级依赖后重新核对 LICENSE 与 NOTICE。

## 安装与设备验证

将交付的 APK 传到 Android 手机，打开文件并允许文件来源安装，或使用已授权的设备：

```sh
adb install -r releases/0.2.0/tiandili-0.2.0-release.apk
```

真机仍需确认首次启动、创建日程及提醒授权、通知到达、重启后的提醒恢复、天气查询、备份导入导出，以及 2×2 / 4×2 小组件添加与点击。不同厂商对后台运行与启动器尺寸的处理需要设备验证。

0.2.0 可用同一签名覆盖 0.1.0，Room 数据库通过显式迁移从 3 升至 4，旧记录取普通日程类型；JSON 备份升级为格式 2，继续接受格式 1。照片备份只保存系统文件引用，换机后需复制原图并重新选择。更新检查读取 `releases/latest.json`；该文件和新 APK 同步到仓库后生效。

当前宿主环境无法启动 Android 模拟器：硬件虚拟化访问被拒绝，软件加速启动也失败。因此交付报告仅声明 JVM / Robolectric 测试、构建、静态检查及 APK 结构检查结果，不声明真机或模拟器端到端测试通过。
