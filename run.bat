@echo off
pushd "%~dp0"
java -jar dist\ssd_ip_cp4_2026f_224110_app.jar %*
if errorlevel 1 pause
popd
