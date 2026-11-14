#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

export JAVA_HOME="$ROOT_DIR/.toolchains/jdk-17/Contents/Home"
export ANDROID_HOME="$ROOT_DIR/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$ROOT_DIR/.gradle-home"

exec "$ROOT_DIR/gradlew" "$@"
