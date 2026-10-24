@echo off
setlocal
cd /d "%~dp0"
start "" "%~dp0runtime\bin\javaw.exe" -cp "%~dp0app" ut99.ServerBrowserApp
