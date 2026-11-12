@echo off
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java test\*.java > sources.txt
javac -d out -cp "lib/*" @sources.txt
if errorlevel 1 (del sources.txt & echo Build FAILED & exit /b 1)
del sources.txt
echo Build OK -^> out
