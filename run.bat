@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac >nul 2>nul
if errorlevel 1 (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0show-java-requirement.ps1"
    exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0show-java-requirement.ps1"
    exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\main\java\ut99\*.java
if errorlevel 1 exit /b 1
java -cp out ut99.ServerBrowserApp
