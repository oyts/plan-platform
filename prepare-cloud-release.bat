@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-cloud-release.ps1
if errorlevel 1 echo Release preparation failed. Keep the error above.
pause
