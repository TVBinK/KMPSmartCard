@echo off
echo ========================================
echo   Bus Card Management System
echo ========================================
echo.

echo Starting UI Client...
echo.
echo IMPORTANT: Before running, please ensure:
echo   1. JCIDE Simulator is running
echo   2. BusCardApplet is installed on simulator
echo   3. Or connect a physical Java Card via PC/SC reader
echo.

REM Start UI client
start "Bus Card UI Client" cmd /k "gradlew.bat run"

echo.
echo ========================================
echo   UI Client is starting...
echo ========================================
echo.
echo Press any key to close this window
pause > nul

