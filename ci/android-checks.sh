#!/usr/bin/env bash
# Everything the "Android CI" workflow checks, in order. Also works locally (Git Bash on Windows):
#   bash ci/android-checks.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== 1/4 Unit, Robolectric and UI tests (with coverage) =="
./gradlew --stacktrace testDebugUnitTest koverXmlReportDebug koverHtmlReportDebug 2>&1 | tee gradle-test.log
python3 ci/coverage_summary.py

echo "== 2/4 Debug APK =="
./gradlew --stacktrace assembleDebug 2>&1 | tee gradle-build.log

echo "== 3/4 Android Lint =="
./gradlew --stacktrace lintDebug 2>&1 | tee gradle-lint.log

echo "== 4/4 Minimum coverage =="
./gradlew --stacktrace koverVerifyDebug 2>&1 | tee gradle-coverage.log
