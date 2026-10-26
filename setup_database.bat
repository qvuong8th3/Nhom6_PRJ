@echo off
chcp 65001 >nul
echo Đang chạy script thiết lập và đồng bộ Database AITA...
if exist "%~dp0Nhom6_PRJ\setup_database.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Nhom6_PRJ\setup_database.ps1"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_database.ps1"
)
pause
