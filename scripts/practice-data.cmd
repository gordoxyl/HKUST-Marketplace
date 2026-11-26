@echo off
rem Adds practice listings and deals to HKUST Marketplace's saved data (Windows).
rem Runs practice-data.ps1 without needing to change PowerShell's script policy.
rem Usage: scripts\practice-data.cmd [-Buyer name] [-Force]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0practice-data.ps1" %*
exit /b %ERRORLEVEL%
