@echo off
chcp 65001 >nul
echo Đang kiểm tra và khởi động ứng dụng AITA Git Analytics...
if exist "%~dp0run_app.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_app.ps1"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\run_app.ps1"
)
pause
