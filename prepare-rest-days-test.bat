@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-device.ps1 -Action install -Port 43132
if errorlevel 1 pause
endlocal
