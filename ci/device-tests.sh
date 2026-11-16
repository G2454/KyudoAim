#!/usr/bin/env bash
# Runs the instrumented tests (app/src/androidTest) on the connected device or emulator.
#   bash ci/device-tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
./gradlew --stacktrace connectedDebugAndroidTest 2>&1 | tee gradle-device.log
