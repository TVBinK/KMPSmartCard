@echo off
echo ========================================
echo   Bus Card Management System
echo ========================================
echo.

echo Starting in 2 terminals:
echo   Terminal 1: Card Simulator (Port 9025)
echo   Terminal 2: UI Client
echo.

REM Terminal 1: Start simulator (dùng Gradle)
start "Bus Card Simulator" cmd /k "gradlew.bat runSimulator"

REM Wait for simulator to start
echo Waiting for simulator to start...
timeout /t 3 /nobreak > nul

REM Terminal 2: Start UI client
start "Bus Card UI Client" cmd /k "gradlew.bat run"

echo.
echo ========================================
echo   Both components are starting...
echo ========================================
echo.
echo Press any key to close this window
pause > nul

