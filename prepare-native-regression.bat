@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\native-regression.ps1 -Action build
if errorlevel 1 (
  echo Regression build failed. Keep the error above.
) else (
  echo Isolated regression clients are ready. Run verify-native-clients.bat with an authorized phone.
)
pause
