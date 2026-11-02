#!/usr/bin/env bash
# Renders every app screen with demo data and saves PNGs to docs/screenshots/.
# Runs on the JVM with Robolectric + Roborazzi: no phone or emulator needed.
#
#   ./scripts/screenshots.sh            record all screens
#   ./scripts/screenshots.sh formCheck  record one screen (test method name)
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p docs/screenshots
filter=()
if [[ $# -gt 0 ]]; then
  filter=(--tests "com.kyudo.aimhelper.screenshots.ScreenshotTest.$1")
fi

./gradlew recordRoborazziDebug "${filter[@]}" --stacktrace

echo
echo "Screenshots in docs/screenshots/:"
ls -1 docs/screenshots/*.png
