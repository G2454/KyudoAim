# CI helpers

The GitHub Actions workflows in `.github/workflows/` use these two files:

- `kotlin-problem-matcher.json` turns Kotlin compiler errors into annotations on the run page.
- `report_failures.py` does the same for failed tests and Gradle errors when a run fails.
