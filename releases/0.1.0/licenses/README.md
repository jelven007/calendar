# 原始许可核验

发布依赖来自 Gradle 的 `releaseRuntimeClasspath`，不以版本目录或测试依赖代替。当前解析结果包含 107 个归档、106 个不同的 Maven 坐标；压缩构建可能移除其中未使用的代码。

| 本地原文 | 核验出处 |
| --- | --- |
| LUNAR-MIT.txt | 6tail/lunar-java，标签 `v1.7.7`，`LICENSE`；作者 Gitee 发布的同名原文一致 |
| CALENDAR-MIT.txt | kizitonwose/Calendar，标签 `2.8.0`，`LICENSE.md` |
| APACHE-2.0.txt | https://www.apache.org/licenses/LICENSE-2.0.txt |
| PROTOBUF-BSD.txt | protocolbuffers/protobuf，标签 `v28.2`，`LICENSE`；AndroidX 两项 external-protobuf 的 POM 明确声明 BSD-3-Clause |
| OKHTTP-PUBLICSUFFIX-NOTICE.txt | okhttp-4.12.0.jar 内 `okhttp3/internal/publicsuffix/NOTICE` |
| MPL-2.0.txt | https://www.mozilla.org/media/MPL/2.0/index.815ca599c9df.txt |

MIT 版权原文分别为 `Copyright (c) 2018 6tail` 和 `Copyright (c) 2019 Kizito Nwose`。旧的 Calendar 作者标签已纠正。

`com.google.guava:listenablefuture:1.0` 从父 POM 继承 Apache 2.0；其 sources.jar 还包含 `Copyright (C) 2007 The Guava Authors` 和 Apache 2.0 声明。Glance 与 DataStore 的 external-protobuf sources.jar 未含源文件，以发布 POM 及上游完整 BSD 文本核验。

复核和重新生成：

```sh
scripts/gradlew-local.sh :app:writeReleaseDependencyInventory \
  --init-script scripts/release-dependencies.init.gradle
python3 scripts/assemble_licenses.py
```

组装工具对未知或缺失许可报错，并保留现有许可文件。升级依赖后须复核新 POM 及归档中的 NOTICE；不要直接沿用本次人工核验结论。
