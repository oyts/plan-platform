@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-formal-clients.ps1 %*
if errorlevel 1 echo Formal client preparation failed. Keep the error above; do not publish partial artifacts.
pause
