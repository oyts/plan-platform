@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\native-test-server.ps1 -Port 43132 -DatabaseName rest-days-test
if errorlevel 1 pause
endlocal
