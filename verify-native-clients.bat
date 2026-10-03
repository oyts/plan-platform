@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\native-regression.ps1 -Action run
if errorlevel 1 (
  echo Native regression failed. Keep the error above and see acceptance-results\native.
) else (
  echo Native regression passed. Report: acceptance-results\native\latest.html
)
pause
