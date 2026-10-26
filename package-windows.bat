@echo off
setlocal
cd /d "%~dp0"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" if exist "%JAVA_HOME%\bin\jlink.exe" set "JDK_BIN=%JAVA_HOME%\bin"
if not defined JDK_BIN (
    for /f "delims=" %%I in ('where javac 2^>nul') do if exist "%%~dpIjlink.exe" set "JDK_BIN=%%~dpI"
)

if not defined JDK_BIN (
    echo A Java 11 or newer JDK with javac and jlink is required to build the Windows package.
    exit /b 1
)
if not exist "%JDK_BIN%\jlink.exe" (
    echo Could not find jlink.exe in "%JDK_BIN%".
    echo Install a full JDK 11 or newer to build the standalone package.
    exit /b 1
)

set /p VERSION=<version.txt
if not defined VERSION (
    echo Could not read the version from version.txt.
    exit /b 1
)
set "PACKAGE_NAME=UT99_Browser-%VERSION%"
set "PACKAGE_DIR=dist\%PACKAGE_NAME%"
set "ARCHIVE_PATH=dist\%PACKAGE_NAME%.zip"
if exist "%PACKAGE_DIR%" rmdir /s /q "%PACKAGE_DIR%"
if exist "%ARCHIVE_PATH%" del /q "%ARCHIVE_PATH%"
if not exist dist mkdir dist
if not exist "%PACKAGE_DIR%\app" mkdir "%PACKAGE_DIR%\app"
if not exist "%PACKAGE_DIR%\src\img" mkdir "%PACKAGE_DIR%\src\img"

"%JDK_BIN%\javac.exe" -encoding UTF-8 --release 11 -d "%PACKAGE_DIR%\app" src\main\java\ut99\*.java
if errorlevel 1 exit /b 1

"%JDK_BIN%\jlink.exe" --add-modules java.desktop,java.net.http,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output "%PACKAGE_DIR%\runtime"
if errorlevel 1 exit /b 1

copy /y src\img\Giblogo-mirror.gif "%PACKAGE_DIR%\src\img\Giblogo-mirror.gif" >nul
if errorlevel 1 exit /b 1
copy /y src\img\discord.png "%PACKAGE_DIR%\src\img\discord.png" >nul
if errorlevel 1 exit /b 1
copy /y README.md "%PACKAGE_DIR%\README.md" >nul
if errorlevel 1 exit /b 1
copy /y version.txt "%PACKAGE_DIR%\version.txt" >nul
if errorlevel 1 exit /b 1
copy /y run-standalone.bat "%PACKAGE_DIR%\UT99 Browser.bat" >nul
if errorlevel 1 exit /b 1
copy /y update-windows.ps1 "%PACKAGE_DIR%\update-windows.ps1" >nul
if errorlevel 1 exit /b 1

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%PACKAGE_DIR%\*' -DestinationPath '%ARCHIVE_PATH%' -Force"
if errorlevel 1 exit /b 1

echo.
echo Standalone package created:
echo %ARCHIVE_PATH%
echo Share the ZIP. Users can extract it and run UT99 Browser.bat without installing Java.
