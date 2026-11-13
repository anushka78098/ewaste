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
java -cp "out;lib/*" com.ewaste.EcoChallengeTest
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.ewaste.LoginModeTest
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
if defined EWASTE_TEST_POSTGRES_API_URL (
  if not defined EWASTE_TEST_POSTGRES_AUCTION_URL (
    echo EWASTE_TEST_POSTGRES_AUCTION_URL is required when running PostgreSQL integration tests.
    exit /b 1
  )
  echo --- API and auction tests against disposable PostgreSQL ---
  java -cp "out;lib/*" com.ewaste.ApiTest postgres
  if errorlevel 1 exit /b 1
  java -cp "out;lib/*" com.ewaste.AuctionTest postgres
  if errorlevel 1 exit /b 1
)
if defined EWASTE_TEST_POSTGRES_AUCTION_URL (
  if not defined EWASTE_TEST_POSTGRES_API_URL (
    echo EWASTE_TEST_POSTGRES_API_URL is required when running PostgreSQL integration tests.
    exit /b 1
  )
)
