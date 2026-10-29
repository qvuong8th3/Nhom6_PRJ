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

# 1. Tim Java JDK
$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not $javaExe) {
    $javaCmd = Get-Command "java.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($javaCmd) { $javaExe = $javaCmd }
}
if (-not $javaExe) {
    $possibleJdks = @(
        "C:\Program Files\Microsoft\jdk-11.0.32.101-hotspot\bin\java.exe",
        "C:\Program Files\Java\jdk-17\bin\java.exe",
        "C:\Program Files\Java\jdk-11\bin\java.exe",
        "C:\Program Files\Java\jdk-21\bin\java.exe",
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
}

if ($javaExe -and (-not $env:JAVA_HOME)) {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
}

# 2. Tim Maven
$mvnPath = Get-Command "mvn" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $mvnPath) {
    $mvnwCandidate = Join-Path $projectDir "mvnw.cmd"
    if (Test-Path $mvnwCandidate) {
        $mvnPath = $mvnwCandidate
    }
}
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

$warFile = Join-Path $projectDir "target\gitanalytics.war"
if ($mvnPath) {
    Write-Host "[*] Dang build file WAR bang Maven ($mvnPath)..." -ForegroundColor Yellow
    & $mvnPath -f $pomFile -DskipTests package
} else {
    Write-Host "[i] Khong tim thay Maven, su dung file WAR da build san." -ForegroundColor Yellow
}

if (-not (Test-Path $warFile)) {
    Write-Host "[!] Khong tim thay file WAR tai: $warFile" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] San sang file WAR: $warFile" -ForegroundColor Green

# 3. Tim Apache Tomcat 9 tu dong
$tomcatDir = $null
$possibleTomcat = @(
    $env:CATALINA_HOME,
    $env:TOMCAT_HOME,
    "E:\Download\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122",
    "C:\Users\asus\Downloads\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122",
    "$env:USERPROFILE\Downloads\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122",
    "C:\Program Files\Apache Software Foundation\Tomcat 9.0",
    "C:\apache-tomcat-9.0",
    "C:\Tomcat9",
    "D:\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122",
    "E:\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122"
)

# Kiem tra thu vien rsp cua VS Code Server Connector
$rspServersDir = "$env:USERPROFILE\.rsp\redhat-community-server-connector\servers"
if (Test-Path $rspServersDir) {
    Get-ChildItem -Path $rspServersDir -File -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $json = Get-Content $_.FullName -Raw | ConvertFrom-Json
            if ($json.'server.home.dir') {
                $possibleTomcat += $json.'server.home.dir'
            }
        } catch {}
    }
}

# Tim quet them trong Downloads nguoi dung
$downloadTomcats = Get-ChildItem -Path "$env:USERPROFILE\Downloads" -Directory -Filter "*tomcat*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
if ($downloadTomcats) {
    $possibleTomcat += $downloadTomcats
}

foreach ($t in $possibleTomcat) {
    if ($t -and (Test-Path (Join-Path $t "bin\bootstrap.jar"))) {
        $tomcatDir = $t
        break
    }
    if ($t -and (Test-Path $t)) {
        $sub = Get-ChildItem -Path $t -Directory -Filter "apache-tomcat*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
        foreach ($st in $sub) {
            if (Test-Path (Join-Path $st "bin\bootstrap.jar")) {
                $tomcatDir = $st
                break
            }
        }
        if ($tomcatDir) { break }
    }
}

if ($tomcatDir) {
    $env:CATALINA_HOME = $tomcatDir
    $webappsDir = Join-Path $tomcatDir "webapps"
    Write-Host "[*] Tim thay Tomcat: $tomcatDir" -ForegroundColor Green
    Write-Host "[*] Chep gitanalytics.war vao Tomcat webapps..." -ForegroundColor Yellow
    Copy-Item $warFile -Destination (Join-Path $webappsDir "gitanalytics.war") -Force

    # Kiem tra xem Tomcat co dang chay tren port 8080 khong
    $conn = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
    if (-not $conn.TcpTestSucceeded) {
        Write-Host "[*] Dang khoi dong Apache Tomcat..." -ForegroundColor Green
        $startupBat = Join-Path $tomcatDir "bin\startup.bat"
        if (Test-Path $startupBat) {
            Start-Process -FilePath "cmd.exe" -ArgumentList "/c `"$startupBat`"" -WindowStyle Minimized
        } elseif ($javaExe) {
            $cp = "$tomcatDir\bin\bootstrap.jar;$tomcatDir\bin\tomcat-juli.jar"
            Start-Process -FilePath $javaExe -ArgumentList "-Dcatalina.home=`"$tomcatDir`" -Dcatalina.base=`"$tomcatDir`" -Djava.io.tmpdir=`"$tomcatDir\temp`" -cp `"$cp`" org.apache.catalina.startup.Bootstrap start" -WindowStyle Normal
        }

        Write-Host "[*] Dang cho may chu Tomcat san sang..." -NoNewline -ForegroundColor Yellow
        $ready = $false
        for ($i = 0; $i -lt 15; $i++) {
            Start-Sleep -Seconds 1
            Write-Host "." -NoNewline -ForegroundColor Yellow
            $conn = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
            if ($conn.TcpTestSucceeded) {
                $ready = $true
                break
            }
        }
        Write-Host ""
        if ($ready) {
            Write-Host "[OK] Apache Tomcat da khoi dong thanh cong!" -ForegroundColor Green
        } else {
            Write-Host "[i] Tomcat dang khoi dong, tiep tuc mo trinh duyet..." -ForegroundColor Yellow
        }
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
