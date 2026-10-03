@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\update-lab.ps1 -Action build
if errorlevel 1 (
  echo Local update preparation failed. Keep the error above.
  pause
  exit /b 1
)
echo Test packages are ready in client-update-artifacts\local. Nothing was installed.
pause
