@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-signing-backup.ps1
if errorlevel 1 echo Backup or restore proof failed. Keep the error above. Original signing keys were not replaced.
pause
