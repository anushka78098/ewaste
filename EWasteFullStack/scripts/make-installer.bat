@echo off
rem Builds a Windows .exe installer for the Swing client. Needs JDK 14+ and the WiX Toolset 3 (https://wixtoolset.org) on PATH.
rem Use "app-image" as the first argument to skip WiX and just get a runnable folder.
cd /d "%~dp0\.."
if not exist dist\ewaste-client.jar ( echo Run scripts\package.sh once in Git Bash/WSL, or compile with build.bat and jar the client manually. & exit /b 1 )
set TYPE=%1
if "%TYPE%"=="" set TYPE=exe
if exist installers rmdir /s /q installers
jpackage --type %TYPE% --name EWasteClient --app-version 1.0 --vendor "E-Waste Manager" --win-menu --win-shortcut --input dist --main-jar ewaste-client.jar --main-class com.ewaste.client.ClientMain --dest installers
echo Done: see the installers folder
