# CI helpers

The GitHub Actions workflows in `.github/workflows/` only call the scripts here, so what CI does can change without editing the workflows.

- `android-checks.sh`: everything **Android CI** runs. Unit, Robolectric and UI tests with coverage, then the debug APK, Android Lint and the minimum-coverage check. Run it locally with `bash ci/android-checks.sh`.
- `device-tests.sh`: the instrumented tests that **Device tests** runs on an emulator. Locally: start an emulator or plug in a phone, then `bash ci/device-tests.sh`.
- `coverage_summary.py`: prints line coverage per package to the log and to the run's summary page.
- `kotlin-problem-matcher.json` and `report_failures.py`: turn compiler errors, failed tests and Gradle errors into annotations on the run page.
- `workflows/`: a copy of the workflow files. GitHub only runs them from `.github/workflows/`, so copy them there whenever they change here.
