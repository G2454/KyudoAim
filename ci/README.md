# CI files

- `kotlin-problem-matcher.json` and `report_failures.py` turn compiler errors and failed tests into annotations on the GitHub Actions run page. The workflows use them.
- `workflows/` holds a copy of the two GitHub Actions workflows. GitHub only runs them from `.github/workflows/`, so if that folder is missing, copy `android.yml` and `screenshots.yml` there.
