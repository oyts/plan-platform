@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\cloud-native-acceptance.ps1
if errorlevel 1 (
  echo Cloud native acceptance failed. See acceptance-results\cloud-native\latest.html.
) else (
  echo Cloud native acceptance passed. Report: acceptance-results\cloud-native\latest.html.
)
pause
