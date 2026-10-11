@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy-cloud-release.ps1
if errorlevel 1 echo Deployment failed. Public entry was not automatically changed.
pause
