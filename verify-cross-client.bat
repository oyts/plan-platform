@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\acceptance.ps1 -InstallBrowser
if errorlevel 1 (
  echo Acceptance failed. Keep the error above.
) else (
  echo Browser acceptance complete. Follow docs\cross-client-acceptance.md for native devices.
)
pause
endlocal
