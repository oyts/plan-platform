@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-device.ps1 -Action install
if errorlevel 1 echo Preparation failed. Keep the error above.
pause
endlocal
