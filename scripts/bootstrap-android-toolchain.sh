#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLCHAINS_DIR="$ROOT_DIR/.toolchains"
SDK_DIR="$ROOT_DIR/.android-sdk"
DOWNLOADS_DIR="$TOOLCHAINS_DIR/downloads"

JDK_VERSION="17.0.21_9"
JDK_ARCHIVE="OpenJDK17U-jdk_aarch64_mac_hotspot_${JDK_VERSION}.tar.gz"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/$JDK_ARCHIVE"
JDK_SHA256="78b08c7f5a0598242d5719c022e19479cb4a3aa45c37c19726a757a469ed1c15"

GRADLE_VERSION="8.13"
GRADLE_ARCHIVE="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/$GRADLE_ARCHIVE"
GRADLE_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"

ANDROID_TOOLS_ARCHIVE="commandlinetools-mac-13114758_latest.zip"
ANDROID_TOOLS_URL="https://dl.google.com/android/repository/$ANDROID_TOOLS_ARCHIVE"
ANDROID_TOOLS_SHA1="c3e06a1959762e89167d1cbaa988605f6f7c1d24"

mkdir -p "$DOWNLOADS_DIR" "$SDK_DIR"

verify_checksum() {
  local algorithm="$1"
  local expected="$2"
  local file="$3"
  local actual
  actual="$(shasum -a "$algorithm" "$file" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "Checksum mismatch for $file" >&2
    return 1
  fi
}

download() {
  local url="$1"
  local output="$2"
  local algorithm="$3"
  local expected="$4"

  if [[ ! -f "$output" ]] || ! verify_checksum "$algorithm" "$expected" "$output"; then
    rm -f "$output"
    curl --fail --location --retry 3 --silent --show-error --output "$output" "$url"
    verify_checksum "$algorithm" "$expected" "$output"
  fi
}

download "$JDK_URL" "$DOWNLOADS_DIR/$JDK_ARCHIVE" 256 "$JDK_SHA256"
if [[ ! -x "$TOOLCHAINS_DIR/jdk-17/Contents/Home/bin/java" ]]; then
  temp_jdk="$(mktemp -d "$TOOLCHAINS_DIR/jdk.XXXXXX")"
  tar -xzf "$DOWNLOADS_DIR/$JDK_ARCHIVE" -C "$temp_jdk"
  extracted_jdk="$(find "$temp_jdk" -mindepth 1 -maxdepth 1 -type d -print -quit)"
  rm -rf "$TOOLCHAINS_DIR/jdk-17"
  mv "$extracted_jdk" "$TOOLCHAINS_DIR/jdk-17"
  rmdir "$temp_jdk"
fi

download "$GRADLE_URL" "$DOWNLOADS_DIR/$GRADLE_ARCHIVE" 256 "$GRADLE_SHA256"
if [[ ! -x "$TOOLCHAINS_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
  unzip -q "$DOWNLOADS_DIR/$GRADLE_ARCHIVE" -d "$TOOLCHAINS_DIR"
fi

download "$ANDROID_TOOLS_URL" "$DOWNLOADS_DIR/$ANDROID_TOOLS_ARCHIVE" 1 "$ANDROID_TOOLS_SHA1"
if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  temp_tools="$(mktemp -d "$TOOLCHAINS_DIR/android-tools.XXXXXX")"
  unzip -q "$DOWNLOADS_DIR/$ANDROID_TOOLS_ARCHIVE" -d "$temp_tools"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$temp_tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rmdir "$temp_tools"
fi

export JAVA_HOME="$TOOLCHAINS_DIR/jdk-17/Contents/Home"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$JAVA_HOME/bin:$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail

sdkmanager \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ROOT_DIR/local.properties"

echo "Android toolchain ready."
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "GRADLE_HOME=$TOOLCHAINS_DIR/gradle-$GRADLE_VERSION"
