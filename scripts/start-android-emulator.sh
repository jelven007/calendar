#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

export ANDROID_HOME="$ROOT_DIR/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$ROOT_DIR/.android-user"
export ANDROID_AVD_HOME="$ROOT_DIR/.android-avd"

exec "$ANDROID_HOME/emulator/emulator" \
  -avd "tiandili-api36" \
  -accel off \
  -no-audio \
  -no-boot-anim \
  -no-window \
  -no-snapshot \
  -no-snapshot-save \
  -gpu swiftshader_indirect
