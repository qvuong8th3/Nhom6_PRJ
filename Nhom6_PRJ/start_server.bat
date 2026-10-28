@echo off
echo ============================================
echo   AITA Git Analytics - Khoi dong may chu
echo ============================================

set JAVA_HOME=C:\Program Files\Java\jdk-17
set CATALINA_HOME=C:\Users\asus\Downloads\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122

echo [1/3] Build project (Maven)...
call "%~dp0mvnw.cmd" clean package -DskipTests -q
if errorlevel 1 (
    echo [LOI] Build that bai!
    pause
    exit /b 1
)
echo [OK] Build thanh cong!

echo [2/3] Deploy WAR len Tomcat...
copy /Y "%~dp0target\gitanalytics.war" "%CATALINA_HOME%\webapps\" >nul
echo [OK] Deploy xong!

echo [3/3] Khoi dong Tomcat...
start "" "%CATALINA_HOME%\bin\startup.bat"

echo.
echo Cho Tomcat khoi dong (10 giay)...
timeout /t 10 /nobreak >nul

echo.
echo ============================================
echo  Ung dung da san sang tai:
echo  http://localhost:8080/gitanalytics
echo ============================================
start "" "http://localhost:8080/gitanalytics"
pause
