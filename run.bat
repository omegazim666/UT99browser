@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\main\java\ut99\*.java
if errorlevel 1 exit /b 1
java -cp out ut99.ServerBrowserApp
