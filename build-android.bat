@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android.ps1 -Action build
if errorlevel 1 (echo Android build failed. Keep the error above.)
pause
