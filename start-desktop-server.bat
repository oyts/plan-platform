@echo off
setlocal
cd /d "%~dp0backend"
set "SPRING_DATASOURCE_URL=jdbc:h2:file:./data/desktop-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
set "REGISTRATION_ENABLED=true"
set "REFRESH_COOKIE_SECURE=false"
echo Local desktop test API: http://127.0.0.1:43129
echo Keep this window open while testing. Data is stored in backend/data/desktop-local.
call mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=43129"
if errorlevel 1 pause
endlocal
