@echo off
cd /d "%~dp0"
if not exist out\com (
  call build.bat
  if errorlevel 1 exit /b 1
)
java -cp "out;lib/*" com.ewaste.client.ClientMain %*
