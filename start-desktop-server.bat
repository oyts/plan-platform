@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\native-test-server.ps1
if errorlevel 1 pause
endlocal
