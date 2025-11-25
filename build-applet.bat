@echo off
REM Script để build Java Card Applet thành CAP file
REM Yêu cầu: JCIDE hoặc Oracle Java Card SDK

echo ========================================
echo   Build Java Card Applet
echo ========================================
echo.

REM Kiểm tra biến môi trường JCIDE
if "%JCIDE_HOME%"=="" (
    echo [WARNING] JCIDE_HOME chua duoc dat!
    echo Vui long dat bien moi truong JCIDE_HOME tro den thu muc cai dat JCIDE
    echo Vi du: set JCIDE_HOME=C:\Program Files\JCIDE
    echo.
    echo Hoac su dung Oracle Java Card SDK:
    echo set JC_HOME=C:\Program Files\Oracle\JavaCard\jc310_kit
    echo.
    pause
    exit /b 1
)

echo JCIDE_HOME: %JCIDE_HOME%
echo.

REM Tạo thư mục output nếu chưa có
if not exist "build\applet" mkdir "build\applet"

REM Đường dẫn đến file applet
set APPLET_SRC=src\main\java\com\buscardmanagement\applet\BusCardApplet.java
set PACKAGE_NAME=com.buscardmanagement.applet
set APPLET_CLASS=BusCardApplet
set AID=11223344550001

echo [1/3] Compiling Java Card Applet...
echo Source: %APPLET_SRC%
echo.

REM Sử dụng JCIDE để compile
if exist "%JCIDE_HOME%\bin\jcide.exe" (
    echo Su dung JCIDE de compile...
    "%JCIDE_HOME%\bin\jcide.exe" -compile "%APPLET_SRC%" -out "build\applet"
) else if exist "%JCIDE_HOME%\bin\javacardc.exe" (
    echo Su dung Oracle Java Card SDK de compile...
    "%JCIDE_HOME%\bin\javacardc.exe" -exportpath "%JCIDE_HOME%\api_export_files" -out CAP EXP -d "build\applet" -applet %AID% %PACKAGE_NAME%.%APPLET_CLASS% %PACKAGE_NAME% %APPLET_CLASS% %AID%
) else (
    echo [ERROR] Khong tim thay JCIDE hoac Oracle Java Card SDK!
    echo Vui long kiem tra JCIDE_HOME hoac JC_HOME
    pause
    exit /b 1
)

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compile that bai!
    pause
    exit /b 1
)

echo.
echo [2/3] Building CAP file...
echo.

REM Tìm file CAP đã được tạo
if exist "build\applet\*.cap" (
    echo [SUCCESS] CAP file da duoc tao thanh cong!
    echo.
    echo File CAP: build\applet\*.cap
    echo.
    echo [3/3] De cai dat applet len Java Card:
    echo   1. Mo JCIDE
    echo   2. Load CAP file: build\applet\*.cap
    echo   3. Install applet len card hoac simulator
    echo.
) else (
    echo [WARNING] Khong tim thay file CAP!
    echo Vui long kiem tra lai qua trinh compile
    echo.
)

pause

