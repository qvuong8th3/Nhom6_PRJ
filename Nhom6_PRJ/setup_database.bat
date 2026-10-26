@echo off
chcp 65001 >nul
echo Đang chạy script thiết lập và đồng bộ Database AITA...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_database.ps1"
pause
