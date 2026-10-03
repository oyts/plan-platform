@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\update-lab.ps1 -Action stop
if errorlevel 1 goto failed
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\update-lab.ps1 -Action build -Stage safety
if errorlevel 1 goto failed
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\update-lab.ps1 -Action start
if errorlevel 1 goto failed
echo Safety target 0.1.3 is ready. Source is idle; nothing was installed.
echo Existing 0.1.2 baseline packages and business API were not changed.
pause
exit /b 0

:failed
echo Safety preparation failed. Keep the error above. No client was installed.
pause
exit /b 1
