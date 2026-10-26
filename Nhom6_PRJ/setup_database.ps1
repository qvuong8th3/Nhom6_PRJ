$ErrorActionPreference = "Continue"

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "  AITA GIT ANALYTICS - AUTO DATABASE SYNC & SETUP (NHOM 6)        " -ForegroundColor Yellow
Write-Host "====================================================================" -ForegroundColor Cyan

# 1. Tim sqlcmd
$sqlcmdPath = Get-Command "sqlcmd" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $sqlcmdPath) {
    $possiblePaths = @(
        "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE",
        "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE",
        "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\130\Tools\Binn\SQLCMD.EXE",
        "C:\Program Files\Microsoft SQL Server\110\Tools\Binn\SQLCMD.EXE"
    )
    foreach ($p in $possiblePaths) {
        if (Test-Path $p) {
            $sqlcmdPath = $p
            break
        }
    }
}

if (-not $sqlcmdPath) {
    Write-Host "[!] Khong tim thay cong cu sqlcmd tren may ban." -ForegroundColor Red
    Write-Host "    Vui long mo SQL Server Management Studio (SSMS) va mo file:" -ForegroundColor Yellow
    Write-Host "    Nhom6_PRJ\database\init_full_database.sql roi nhan Execute (F5)." -ForegroundColor White
    exit 1
}

Write-Host "[OK] Tim thay SQLCMD: $sqlcmdPath" -ForegroundColor Green

# 2. Do tim Server & Port hoat dong
$candidates = @(
    "tcp:127.0.0.1,1434",
    "tcp:127.0.0.1,1433",
    "localhost,1434",
    "localhost,1433",
    "localhost\SQLEXPRESS",
    ".\SQLEXPRESS",
    "localhost"
)

$workingServer = $null
$detectedPort = "1433"

Write-Host "[*] Dang kiem tra ket noi den cac instance SQL Server cuc bo..." -ForegroundColor Yellow
foreach ($cand in $candidates) {
    try {
        $testQuery = 'SELECT @@SERVERNAME;'
        $out = & $sqlcmdPath -S $cand -E -W -Q $testQuery 2>&1
        if ($LASTEXITCODE -eq 0 -and ($out -notmatch "Error:")) {
            $workingServer = $cand
            if ($cand -match ",(\d+)") {
                $detectedPort = $Matches[1]
            } elseif ($cand -match "1434") {
                $detectedPort = "1434"
            }
            Write-Host "[OK] Ket noi thanh cong den: $cand" -ForegroundColor Green
            break
        }
    } catch {}
}

if (-not $workingServer) {
    Write-Host "[!] Khong the ket noi den SQL Server cuc bo bang Windows Authentication." -ForegroundColor Red
    Write-Host "    Hay dam bao Service SQL Server (MSSQLSERVER) hoac (SQLEXPRESS) dang chay (RUNNING)." -ForegroundColor Yellow
    exit 1
}

# 3. Tim duong dan file init_full_database.sql
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlFile = Join-Path $scriptDir "database\init_full_database.sql"
if (-not (Test-Path $sqlFile)) {
    $sqlFile = Join-Path $scriptDir "Nhom6_PRJ\database\init_full_database.sql"
}
if (-not (Test-Path $sqlFile)) {
    $sqlFile = Join-Path $scriptDir "init_full_database.sql"
}

if (-not (Test-Path $sqlFile)) {
    Write-Host "[!] Khong tim thay file init_full_database.sql!" -ForegroundColor Red
    exit 1
}

Write-Host "[*] Dang thuc thi dong bo database tu file: $sqlFile" -ForegroundColor Yellow
$execOutput = & $sqlcmdPath -S $workingServer -E -i $sqlFile 2>&1
foreach ($line in $execOutput) {
    if ($line -match "Msg \d+, Level \d+") {
        Write-Host "   $line" -ForegroundColor Red
    } elseif ($line -match "ADMIN|GIANG VIEN|SINH VIEN|HOAN TAT|THANH CONG") {
        Write-Host "   $line" -ForegroundColor Green
    }
}

# 4. Kich hoat Mixed Mode va cap quyen cho user sa
try {
    $configQuery = "EXEC xp_instance_regwrite N'HKEY_LOCAL_MACHINE', N'Software\Microsoft\MSSQLServer\MSSQLServer', N'LoginMode', REG_DWORD, 2; ALTER LOGIN sa WITH PASSWORD = '123', CHECK_POLICY = OFF; ALTER LOGIN sa ENABLE;"
    & $sqlcmdPath -S $workingServer -E -Q $configQuery 2>&1 | Out-Null
} catch {}

# 5. Cap nhat hoac tao file db.properties
$targetDirs = @(
    (Join-Path $scriptDir "src\main\resources"),
    (Join-Path $scriptDir "Nhom6_PRJ\src\main\resources")
)

foreach ($td in $targetDirs) {
    if (Test-Path $td) {
        $propFile = Join-Path $td "db.properties"
        $propLines = @(
            "# ============================================================================",
            "# CAU HINH KET NOI DATABASE CHO DU AN AITA GIT ANALYTICS - NHOM 6",
            "# Tu dong tao boi script setup_database.ps1",
            "# ============================================================================",
            "",
            "# --- LUA CHON 1: CSDL ONLINE / CLOUD TRÊN WEB (DUNG CHUNG TOAN BO NHOM) ---",
            "# Neu muon dung CSDL chung tren Web, bo dau # o dong db.url ben duoi:",
            "# db.url=jdbc:sqlserver://aita-cloud.database.windows.net:1433;databaseName=AITA_DB;user=aita;password=Pass@123;encrypt=true;trustServerCertificate=true;",
            "",
            "# --- LUA CHON 2: CSDL LOCAL TREN MAY BAN ---",
            "db.server=localhost",
            "db.port=$detectedPort",
            "db.name=AITA_DB",
            "db.user=sa",
            "db.password=123"
        )
        $propLines | Set-Content -Path $propFile -Encoding UTF8
        Write-Host "[OK] Da tao cau hinh CSDL tai: $propFile" -ForegroundColor Green
    }
}

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "  DONG BO CO SO DU LIEU HOAN TAT 100%!                             " -ForegroundColor Green
Write-Host "  Server:   $workingServer (Port: $detectedPort)" -ForegroundColor White
Write-Host "  Database: AITA_DB" -ForegroundColor White
Write-Host "  User sa:  sa / 123" -ForegroundColor White
Write-Host ""
Write-Host "  Cac tai khoan kiem thu san sang dang nhap:" -ForegroundColor Yellow
Write-Host "  - ADMIN:      admin       / Admin@123456" -ForegroundColor Cyan
Write-Host "  - GIANG VIEN: lecturer01  / 123456" -ForegroundColor Cyan
Write-Host "  - SINH VIEN:  student01   / 123456 (student01 - student06)" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan
