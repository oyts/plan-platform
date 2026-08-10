@echo off
setlocal
cd /d %~dp0backend
call mvn test
if errorlevel 1 exit /b 1
cd /d %~dp0web
call npm run build
if errorlevel 1 exit /b 1
echo All checks passed.
endlocal
