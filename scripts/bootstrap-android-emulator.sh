#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

export JAVA_HOME="$ROOT_DIR/.toolchains/jdk-17/Contents/Home"
export ANDROID_HOME="$ROOT_DIR/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$ROOT_DIR/.android-user"
export ANDROID_AVD_HOME="$ROOT_DIR/.android-avd"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

SYSTEM_IMAGE="system-images;android-36;google_apis;arm64-v8a"
AVD_NAME="tiandili-api36"

sdkmanager "emulator" "$SYSTEM_IMAGE"

if [[ ! -f "$ANDROID_AVD_HOME/$AVD_NAME.ini" ]]; then
  mkdir -p "$ANDROID_AVD_HOME" "$ANDROID_USER_HOME"
  echo "no" | avdmanager create avd \
    --force \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "pixel_6"
fi

echo "Emulator ready: $AVD_NAME"
