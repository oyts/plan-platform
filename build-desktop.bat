@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\desktop.ps1" -Action build
if errorlevel 1 (
  echo Desktop build failed. Keep the error above for troubleshooting.
  pause
  exit /b 1
)
echo Desktop installer is in desktop-artifacts.
pause
endlocal
