# Renders every app screen with demo data and saves PNGs to docs\screenshots\.
# Runs on the JVM with Robolectric + Roborazzi: no phone or emulator needed.
#
#   .\scripts\screenshots.ps1             record all screens
#   .\scripts\screenshots.ps1 formCheck   record one screen (test method name)
param([string]$Screen = "")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

New-Item -ItemType Directory -Force -Path "docs\screenshots" | Out-Null
$gradleArgs = @("recordRoborazziDebug", "--stacktrace")
if ($Screen -ne "") {
    $gradleArgs += @("--tests", "com.kyudo.aimhelper.screenshots.ScreenshotTest.$Screen")
}

& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Screenshots in docs\screenshots\:"
Get-ChildItem "docs\screenshots\*.png" | ForEach-Object { Write-Host "  $($_.Name)" }
