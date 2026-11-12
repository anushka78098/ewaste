@echo off
cd /d "%~dp0"
if not exist out\com (
  call build.bat
  if errorlevel 1 exit /b 1
)
if not exist lib-fx ( echo JavaFX not installed yet - run scripts\setup-javafx.bat first & exit /b 1 )
if not exist out-fx mkdir out-fx
dir /s /b src-fx\*.java > out-fx\sources.txt
javac -d out-fx --module-path lib-fx --add-modules javafx.controls -cp out @out-fx\sources.txt
if errorlevel 1 (echo JavaFX build FAILED & exit /b 1)
echo JavaFX client built to out-fx
