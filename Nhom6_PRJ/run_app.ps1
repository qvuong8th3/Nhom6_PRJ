$ErrorActionPreference = "Continue"

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "  AITA GIT ANALYTICS - BUILD & RUN WEB APPLICATION                " -ForegroundColor Yellow
Write-Host "====================================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Join-Path $scriptDir "Nhom6_PRJ"
if (-not (Test-Path (Join-Path $projectDir "pom.xml"))) {
    $projectDir = $scriptDir
}

$pomFile = Join-Path $projectDir "pom.xml"
if (-not (Test-Path $pomFile)) {
    Write-Host "[!] Khong tim thay file pom.xml tai: $pomFile" -ForegroundColor Red
    exit 1
}

# 1. Tim Maven
$mvnPath = Get-Command "mvn" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $mvnPath) {
    $possibleMvn = @(
        "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\NetBeans-18\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\NetBeans-19\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd",
        "C:\Program Files\apache-maven\bin\mvn.cmd"
    )
    foreach ($m in $possibleMvn) {
        if (Test-Path $m) {
            $mvnPath = $m
            break
        }
    }
}

if (-not $mvnPath) {
    Write-Host "[!] Khong tim thay Maven tren may ban. Hay dam bao Maven da duoc cai dat." -ForegroundColor Red
    exit 1
}

Write-Host "[*] Dang build file WAR bang Maven..." -ForegroundColor Yellow
& $mvnPath -f $pomFile -DskipTests package

$warFile = Join-Path $projectDir "target\gitanalytics.war"
if (-not (Test-Path $warFile)) {
    Write-Host "[!] Khong tim thay file WAR duoc build tai: $warFile" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Build thanh cong file WAR: $warFile" -ForegroundColor Green

# 2. Tim Apache Tomcat 9
$tomcatDir = $null
$possibleTomcat = @(
    "C:\Users\asus\Downloads\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122",
    "C:\Program Files\Apache Software Foundation\Tomcat 9.0",
    "C:\apache-tomcat-9.0",
    "C:\Tomcat9"
)

foreach ($t in $possibleTomcat) {
    if (Test-Path (Join-Path $t "bin\startup.bat")) {
        $tomcatDir = $t
        break
    }
}

if ($tomcatDir) {
    $webappsDir = Join-Path $tomcatDir "webapps"
    Write-Host "[*] Chep gitanalytics.war vao Tomcat: $webappsDir" -ForegroundColor Yellow
    Copy-Item $warFile -Destination (Join-Path $webappsDir "gitanalytics.war") -Force

    Write-Host "[*] Dang khoi dong Apache Tomcat..." -ForegroundColor Green
    $startupBat = Join-Path $tomcatDir "bin\startup.bat"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c `"$startupBat`""

    Start-Sleep -Seconds 3
    Start-Process "http://localhost:8080/gitanalytics/login.jsp"
} else {
    Write-Host "[i] Khong tim thay thu muc Tomcat 9 tu dong." -ForegroundColor Yellow
    Write-Host "    Hay chep thu cong file sau vao thu muc webapps cua Tomcat:" -ForegroundColor White
    Write-Host "    $warFile" -ForegroundColor Cyan
    Write-Host "    Sau do mo trinh duyet: http://localhost:8080/gitanalytics/login.jsp" -ForegroundColor White
}
