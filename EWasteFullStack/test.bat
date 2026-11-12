@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.ApiTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.AuctionTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.MailTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.DashboardTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.DecisionEngineTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.server.ThrottleTest
if errorlevel 1 exit /b 1
if exist lib\sqlite-jdbc-3.36.0.3.jar (
  echo --- same tests against SQLite ---
  java -cp "out;lib/*" com.ewaste.ApiTest sqlite
  if errorlevel 1 exit /b 1
  java -cp "out;lib/*" com.ewaste.AuctionTest sqlite
  if errorlevel 1 exit /b 1
)
