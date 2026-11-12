@echo off
cd /d "%~dp0"
if not exist out-fx\com (
  call build-fx.bat
  if errorlevel 1 exit /b 1
)
java --module-path lib-fx --add-modules javafx.controls -cp "out;out-fx" com.ewaste.fx.FxLauncher %*
