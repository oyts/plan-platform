@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-client-update-release.ps1 -ForTesting
if errorlevel 1 echo Preparation failed. Keep the error above.
pause
