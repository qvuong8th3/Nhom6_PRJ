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
    if (Test-Path (Join-Path $t "bin\bootstrap.jar")) {
        $tomcatDir = $t
        break
    }
}

# 3. Tim Java JDK tuong thich cho Tomcat (JDK 11, 17, 21)
$javaExe = "java.exe"
$possibleJdks = @(
    "C:\Program Files\Java\jdk-17\bin\java.exe",
    "C:\Program Files\Java\jdk-11\bin\java.exe",
    "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe",
    "C:\Program Files\Eclipse Adoptium\jdk-11\bin\java.exe",
    "C:\Program Files\Java\bin\java.exe"
)

foreach ($j in $possibleJdks) {
    if (Test-Path $j) {
        $javaExe = $j
        break
    }
}

if ($tomcatDir) {
    $webappsDir = Join-Path $tomcatDir "webapps"
    Write-Host "[*] Chep gitanalytics.war vao Tomcat: $webappsDir" -ForegroundColor Yellow
    Copy-Item $warFile -Destination (Join-Path $webappsDir "gitanalytics.war") -Force

    # Kiem tra xem Tomcat co dang chay tren port 8080 khong
    $conn = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
    if (-not $conn.TcpTestSucceeded) {
        Write-Host "[*] Dang khoi dong Apache Tomcat..." -ForegroundColor Green
        $cp = "$tomcatDir\bin\bootstrap.jar;$tomcatDir\bin\tomcat-juli.jar"
        Start-Process -FilePath $javaExe -ArgumentList "-Dcatalina.home=`"$tomcatDir`" -Dcatalina.base=`"$tomcatDir`" -Djava.io.tmpdir=`"$tomcatDir\temp`" -cp `"$cp`" org.apache.catalina.startup.Bootstrap start" -WindowStyle Normal
        Start-Sleep -Seconds 4
    } else {
        Write-Host "[OK] Apache Tomcat dang chay tren port 8080!" -ForegroundColor Green
    }

    Write-Host "[*] Dang mo trinh duyet..." -ForegroundColor Cyan
    Start-Process "http://localhost:8080/gitanalytics/login.jsp"
} else {
    Write-Host "[i] Khong tim thay thu muc Tomcat 9 tu dong." -ForegroundColor Yellow
    Write-Host "    Hay chep thu cong file sau vao thu muc webapps cua Tomcat:" -ForegroundColor White
    Write-Host "    $warFile" -ForegroundColor Cyan
    Write-Host "    Sau do mo trinh duyet: http://localhost:8080/gitanalytics/login.jsp" -ForegroundColor White
}
