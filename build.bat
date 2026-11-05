@echo off
setlocal
pushd "%~dp0"
if not exist build\classes mkdir build\classes
if not exist dist mkdir dist
javac --release 17 -encoding UTF-8 -d build\classes src\irrigation\*.java
if errorlevel 1 goto failed
jar --create --file dist\ssd_ip_cp4_2026f_224110_app.jar --main-class irrigation.MainFrame -C build\classes .
if errorlevel 1 goto failed
javadoc -quiet -Xdoclint:all,-missing -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -d docs\api src\irrigation\*.java
if errorlevel 1 goto failed
echo Build completed.
popd
exit /b 0
:failed
echo Build failed. Check that JDK 17 or newer is installed.
popd
pause
exit /b 1
