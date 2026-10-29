@echo off
chcp 65001 >nul
echo ====================================================================
echo   AITA GIT ANALYTICS - KHOI DONG MAY CHU WEB (NHOM 6)
echo ====================================================================

if exist "%~dp0run_app.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_app.ps1"
) else if exist "%~dp0Nhom6_PRJ\run_app.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Nhom6_PRJ\run_app.ps1"
) else (
    echo [!] Khong tim thay script run_app.ps1
)
pause
