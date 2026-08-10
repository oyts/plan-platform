@echo off
setlocal
start "Plan Platform API" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"
start "Plan Platform Web" cmd /k "cd /d %~dp0web && npm run dev"
echo Backend: http://localhost:8080
echo Web:     http://localhost:43128
endlocal
