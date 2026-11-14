#!/usr/bin/env python3
"""Assemble bundled notices from the resolved release artifacts and reviewed upstream texts."""

import argparse
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
APACHE_NAMES = {
    "The Apache Software License, Version 2.0", "The Apache License, Version 2.0",
    "Apache-2.0",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path,
                        default=ROOT / "app/build/outputs/licenses/resolved-artifacts.json")
    parser.add_argument("--cache", type=Path,
                        default=ROOT / ".gradle-home/caches/modules-2/files-2.1")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "app/src/main/assets/open_source_licenses.txt")
    arguments = parser.parse_args()
    groups = {"Apache-2.0": set(), "MIT": set(), "BSD-3-Clause": set()}
    for artifact in json.loads(arguments.inventory.read_text()):
        coordinate = artifact["coordinate"]
        group, name, version = coordinate.split(":")
        poms = list((arguments.cache / group / name / version).rglob("*.pom"))
        if not poms:
            raise ValueError(f"Missing POM for {coordinate}")
        license_names = {item.findtext("m:name", namespaces=NS)
                         for pom in poms
                         for item in ET.parse(pom).findall("m:licenses/m:license", NS)}
        # This artifact inherits its Apache license from guava-parent:26.0-android;
        # its source header was also checked in listenablefuture-1.0-sources.jar.
        if not license_names and coordinate == "com.google.guava:listenablefuture:1.0":
            license_names = {"Apache-2.0"}
        if len(license_names) != 1:
            raise ValueError(f"Unreviewed license set for {coordinate}: {license_names}")
        license_name = next(iter(license_names))
        if license_name in APACHE_NAMES:
            license_name = "Apache-2.0"
        elif license_name in {"MIT License", "The MIT License"}:
            license_name = "MIT"
        if license_name not in groups:
            raise ValueError(f"Unreviewed license for {coordinate}: {license_name}")
        groups[license_name].add(coordinate)

    upstream = ROOT / "docs/licenses"
    text = [
        "天地历 · 开源许可与数据来源",
        "以下保留各开源组件的版权与完整许可。组件名称不表示其作者对本应用的认可。",
        "\nApache License 2.0 组件",
        "AndroidX — The Android Open Source Project",
        "Retrofit / OkHttp / Moshi / Okio — Square, Inc.",
        "Kotlin / kotlinx — JetBrains s.r.o. and Kotlin Programming Language contributors",
        "Guava ListenableFuture — Copyright (C) 2007 The Guava Authors",
        "JSpecify — JSpecify contributors",
        "\n".join(sorted(groups["Apache-2.0"])),
        (upstream / "APACHE-2.0.txt").read_text(),
        "\nMIT 组件",
        "\n".join(sorted(groups["MIT"])),
        "lunar-java 1.7.7 — https://6tail.cn/calendar/",
        (upstream / "LUNAR-MIT.txt").read_text(),
        "Kizitonwose Calendar 2.8.0 — https://kizitonwose.github.io/Calendar/",
        (upstream / "CALENDAR-MIT.txt").read_text(),
        "\nBSD-3-Clause · Protocol Buffers",
        "\n".join(sorted(groups["BSD-3-Clause"])),
        (upstream / "PROTOBUF-BSD.txt").read_text(),
        "\nOkHttp Public Suffix List · MPL 2.0",
        (upstream / "OKHTTP-PUBLICSUFFIX-NOTICE.txt").read_text(),
        "Source: https://publicsuffix.org/list/public_suffix_list.dat",
        (upstream / "MPL-2.0.txt").read_text(),
        "\n天气数据 · Open-Meteo / CC BY 4.0",
        "https://open-meteo.com/",
        "https://creativecommons.org/licenses/by/4.0/",
        "本应用对天气数据进行了中文化、单位转换与界面编排。",
    ]
    output = "\n\n".join(text) + "\n"
    with tempfile.NamedTemporaryFile("w", dir=arguments.output.parent, delete=False,
                                     encoding="utf-8") as temporary:
        temporary.write(output)
        temporary.flush()
        os.fsync(temporary.fileno())
        path = temporary.name
    os.replace(path, arguments.output)
    print(f"Bundled {sum(map(len, groups.values()))} dependency notices: {arguments.output}")


if __name__ == "__main__":
    main()
