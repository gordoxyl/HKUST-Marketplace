@echo off
rem Resets HKUST Marketplace's saved data to the demo data (Windows).
rem Runs reset-data.ps1 without needing to change PowerShell's script policy.
rem Usage: scripts\reset-data.cmd [-NoBackup]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-data.ps1" %*
exit /b %ERRORLEVEL%
