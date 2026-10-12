@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\public-web-acceptance.ps1
if errorlevel 1 (
  echo Public website acceptance failed. See acceptance-results\public-web\latest.html.
) else (
  echo Public website acceptance passed. Report: acceptance-results\public-web\latest.html.
)
pause
