<#
  采集端环境自检与安装（幂等，可重复执行）

  作用：
    1. 自检并安装 Python 3.13.15（当前 bugfix 线，有 Windows 安装器）
       —— 不装 3.12.x：3.12 自 3.12.11 起为「仅源码安全修复版」，无 Windows 安装器，
          最后一个带安装器的 3.12.10 停留在 2025-04，缺 2026 年的安全修复。
    2. 自检并安装 MySQL 8.0.46（ZIP 版，注册为 Windows 服务，仅监听 127.0.0.1）
    3. 初始化数据库与最小权限应用账号；root / 应用账号密码随机生成
    4. 密码以 Windows DPAPI（CurrentUser 作用域）加密后写入仓库外的本地密钥文件

  红线：
    - MySQL 只绑定 127.0.0.1，不对内网/公网开放 3306
    - 生成的密码只落本地加密文件，绝不写入仓库、绝不打印完整明文到日志
    - 不修改系统既有软件；不写注册表项（安装器自身行为除外）

  用法（管理员 PowerShell）：
    powershell -ExecutionPolicy Bypass -File env-setup.ps1
    powershell -ExecutionPolicy Bypass -File env-setup.ps1 -SkipMySQL
    powershell -ExecutionPolicy Bypass -File env-setup.ps1 -SkipPython
#>
[CmdletBinding()]
param(
    # 日志与标记文件目录
    [string]$WorkDir = 'C:\yizhan-env',
    # 本地密钥文件（位于仓库内但被 .gitignore 覆盖）
    [string]$SecretsFile = 'D:\yizhan\hrm-dev\collector\config\local\local.secrets.json',
    # 仅自检不安装
    [switch]$CheckOnly,
    [switch]$SkipPython,
    [switch]$SkipMySQL
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# ---------------- 版本与地址 ----------------
$PyVersion  = '3.13.15'
# 下载地址按「国内镜像优先、官方兜底」排列。
# 实测（2026-09-22，采集机 PC-20260112CXLA）：
#   python.org 约 655 B/s —— 29MB 需十余小时，等同不可用；npmmirror 约 2640 KB/s。
#   MySQL 各国内高校/云镜像（tuna/aliyun/ustc/163/nju/huaweicloud）均返回 403/404，
#   仅 cdn.mysql.com 可用（约 370 KB/s）。
$PyUrls = @(
    "https://registry.npmmirror.com/-/binary/python/$PyVersion/python-$PyVersion-amd64.exe",
    "https://www.python.org/ftp/python/$PyVersion/python-$PyVersion-amd64.exe"
)
$MyVersion  = '8.0.46'
$MyUrls = @(
    "https://cdn.mysql.com/Downloads/MySQL-8.0/mysql-$MyVersion-winx64.zip",
    "https://dev.mysql.com/get/Downloads/MySQL-8.0/mysql-$MyVersion-winx64.zip"
)
$MyRoot     = 'C:\mysql'
$MyBasedir  = Join-Path $MyRoot "mysql-$MyVersion-winx64"
$MyData     = Join-Path $MyRoot 'data'
$MyIni      = Join-Path $MyRoot 'my.ini'
$MyService  = 'MySQL80'
$DbName     = 'yizhan_collector'
$DbAppUser  = 'yizhan'

# ---------------- 基础设施 ----------------
if (-not (Test-Path $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null }
$LogFile  = Join-Path $WorkDir 'env-setup.log'
$DoneFile = Join-Path $WorkDir 'env-setup.result.json'
$State    = [ordered]@{
    startedAt   = (Get-Date).ToString('s')
    finishedAt  = $null
    python      = [ordered]@{ status = 'PENDING' }
    mysql       = [ordered]@{ status = 'PENDING' }
    database    = [ordered]@{ status = 'PENDING' }
    secrets     = [ordered]@{ status = 'PENDING'; path = $SecretsFile }
    errors      = @()
}

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '[{0}] [{1}] {2}' -f (Get-Date).ToString('HH:mm:ss'), $Level, $Message
    Write-Host $line
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}
function Save-State {
    $State.finishedAt = (Get-Date).ToString('s')
    ($State | ConvertTo-Json -Depth 6) | Set-Content -Path $DoneFile -Encoding UTF8
}
function Fail-Setup {
    param([string]$Stage, [string]$Message)
    $State.errors += ('{0}: {1}' -f $Stage, $Message)
    Write-Log ('失败 —— {0}' -f $Message) 'ERROR'
    Save-State
    exit 1
}
function New-StrongPassword {
    param([int]$Length = 24)
    # 去掉引号/反斜杠等易引发转义问题的字符，避免 SQL 与命令行转义踩坑
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%^*_-+='
    $bytes = New-Object byte[] $Length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($chars[$b % $chars.Length]) }
    return $sb.ToString()
}
function Protect-Text {
    param([string]$Plain)
    Add-Type -AssemblyName System.Security
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Plain)
    $enc = [System.Security.Cryptography.ProtectedData]::Protect(
        $bytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
    return [Convert]::ToBase64String($enc)
}
function Get-RealPython {
    # 只认真实 Python 安装，排除 WindowsApps 下的应用商店别名占位符
    $candidates = @()
    $candidates += Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Programs\Python') -Filter 'python.exe' -Recurse -ErrorAction SilentlyContinue
    $candidates += Get-ChildItem -Path 'C:\Program Files' -Filter 'python.exe' -Recurse -Depth 3 -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*Python*' }
    foreach ($c in $candidates) {
        if ($c.FullName -like '*WindowsApps*') { continue }
        return $c.FullName
    }
    return $null
}
function Download-File {
    param([string[]]$Url, [string]$OutFile, [long]$MinBytes = 1MB)
    if (Test-Path $OutFile) {
        $len = (Get-Item $OutFile).Length
        if ($len -ge $MinBytes) {
            Write-Log ('已存在且大小合规（{0} 字节），跳过下载：{1}' -f $len, $OutFile)
            return $true
        }
        # 上次下载被中断会留下半成品。若不校验大小就沿用，会把损坏文件当"已下载"，
        # 后续静默安装/解压必然失败且难定位 —— 故删除后重下。
        Write-Log ('发现不完整下载（{0} 字节 < 下限 {1} 字节），删除后重新下载' -f $len, $MinBytes) 'WARN'
        Remove-Item -Path $OutFile -Force -ErrorAction SilentlyContinue
    }
    # 逐个地址尝试：单一下载源被限速或阻断时不至于把整条安装流程拖死
    foreach ($u in $Url) {
        Write-Log ('下载：{0}' -f $u)
        try {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            Invoke-WebRequest -Uri $u -OutFile $OutFile -UseBasicParsing -TimeoutSec 1800
            $sw.Stop()
            # 下载后再次校验大小：HTTP 200 但连接中断同样会留下半成品
            $len = (Get-Item $OutFile).Length
            if ($len -lt $MinBytes) {
                Write-Log ('下载后大小异常（{0} 字节 < 下限 {1} 字节），删除并尝试下一个地址' -f $len, $MinBytes) 'WARN'
                Remove-Item -Path $OutFile -Force -ErrorAction SilentlyContinue
                continue
            }
            $sizeMb = [math]::Round($len / 1MB, 1)
            Write-Log ('下载完成：{0} MB，耗时 {1} 秒' -f $sizeMb, [math]::Round($sw.Elapsed.TotalSeconds, 1))
            return $true
        } catch {
            Write-Log ('下载失败：{0}' -f $_.Exception.Message) 'WARN'
            Remove-Item -Path $OutFile -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Log '所有下载地址均失败' 'ERROR'
    return $false
}
function Invoke-Mysqld {
    # 用 Start-Process 显式捕获 stdout/stderr。
    # 起因：mysqld 注册服务失败时不往控制台输出，只写 mysql-error.log，
    # 导致脚本静默失败、只能靠"服务不存在"这种滞后现象反推 —— 必须让失败可见。
    param([string]$Exe, [string[]]$ArgList, [string]$Stage, [string]$What)
    $outLog = Join-Path $WorkDir 'mysqld-stdout.log'
    $errLog = Join-Path $WorkDir 'mysqld-stderr.log'
    $p = Start-Process -FilePath $Exe -ArgumentList $ArgList -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    foreach ($l in @(Get-Content $outLog -ErrorAction SilentlyContinue)) { if (("$l").Trim()) { Write-Log ('  [out] {0}' -f $l) } }
    foreach ($l in @(Get-Content $errLog -ErrorAction SilentlyContinue)) { if (("$l").Trim()) { Write-Log ('  [err] {0}' -f $l) 'WARN' } }
    if ($p.ExitCode -ne 0) {
        Write-Log ('{0} 失败（退出码 {1}），详见 C:\mysql\mysql-error.log' -f $What, $p.ExitCode) 'ERROR'
        Fail-Setup $Stage ('{0} 失败（退出码 {1}）' -f $What, $p.ExitCode)
    }
}

Write-Log '========== 采集端环境自检开始 =========='
Write-Log ('主机：{0} ；用户：{1}' -f $env:COMPUTERNAME, $env:USERNAME)
Write-Log ('管理员：{0}' -f ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))

# ============================================================
# 一、Python
# ============================================================
Write-Log '---------- [1/4] Python ----------'
$pyExe = Get-RealPython
if ($pyExe) {
    $ver = (& $pyExe --version) 2>&1
    Write-Log ('已安装 Python：{0} -> {1}' -f $pyExe, $ver)
    $State.python = [ordered]@{ status = 'ALREADY_INSTALLED'; path = $pyExe; version = "$ver" }
} elseif ($SkipPython) {
    Write-Log '按参数跳过 Python 安装' 'WARN'
    $State.python = [ordered]@{ status = 'SKIPPED' }
} elseif ($CheckOnly) {
    Write-Log '仅自检：未发现真实 Python 安装（WindowsApps 下的 python.exe 是应用商店别名，不可用）' 'WARN'
    $State.python = [ordered]@{ status = 'MISSING' }
} else {
    Write-Log '未发现真实 Python 安装，开始安装'
    $pyInstaller = Join-Path $WorkDir "python-$PyVersion-amd64.exe"
    if (-not (Download-File -Url $PyUrls -OutFile $pyInstaller -MinBytes 20MB)) {
        Fail-Setup 'python' 'Python 安装包下载失败'
    }
    Write-Log '静默安装 Python（InstallAllUsers=1，PrependPath=1，含 pip 与 py 启动器）'
    $args = @(
        '/quiet', 'InstallAllUsers=1', 'PrependPath=1',
        'Include_pip=1', 'Include_launcher=1', 'Include_test=0', 'Include_doc=0'
    )
    $p = Start-Process -FilePath $pyInstaller -ArgumentList $args -Wait -PassThru
    Write-Log ('安装器退出码：{0}' -f $p.ExitCode)
    Start-Sleep -Seconds 5
    $pyExe = Get-RealPython
    if (-not $pyExe) { Fail-Setup 'python' '安装后仍未找到真实 python.exe' }
    $ver = (& $pyExe --version) 2>&1
    Write-Log ('安装成功：{0} -> {1}' -f $pyExe, $ver)
    $State.python = [ordered]@{ status = 'INSTALLED'; path = $pyExe; version = "$ver" }
}

# ============================================================
# 二、MySQL
# ============================================================
Write-Log '---------- [2/4] MySQL ----------'
$mysqld = Join-Path $MyBasedir 'bin\mysqld.exe'
$mysqlCli = Join-Path $MyBasedir 'bin\mysql.exe'
$svc = Get-Service -Name $MyService -ErrorAction SilentlyContinue
$myInstalled = (Test-Path $mysqld) -and ($svc -ne $null)

if ($myInstalled) {
    Write-Log ('已安装 MySQL 服务 {0}，当前状态：{1}' -f $MyService, $svc.Status)
    # 服务可能已注册但处于停止态（上一次运行中断），不拉起会直接导致后续连库失败
    if ($svc.Status -ne 'Running') {
        Write-Log '服务未运行，执行启动'
        Start-Service -Name $MyService -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 8
        $svc = Get-Service -Name $MyService -ErrorAction SilentlyContinue
    }
    if (-not $svc -or $svc.Status -ne 'Running') {
        Fail-Setup 'mysql' ('已安装的服务未处于运行状态：' + ($(if($svc){$svc.Status}else{'不存在'})))
    }
    Write-Log ('服务运行中：{0}' -f $svc.Status)
    $State.mysql = [ordered]@{ status = 'ALREADY_INSTALLED'; basedir = $MyBasedir; service = $MyService }
} elseif ($SkipMySQL) {
    Write-Log '按参数跳过 MySQL 安装' 'WARN'
    $State.mysql = [ordered]@{ status = 'SKIPPED' }
} elseif ($CheckOnly) {
    Write-Log '仅自检：未发现 MySQL' 'WARN'
    $State.mysql = [ordered]@{ status = 'MISSING' }
} else {
    if (-not (Test-Path $MyRoot)) { New-Item -ItemType Directory -Path $MyRoot -Force | Out-Null }
    $zip = Join-Path $WorkDir "mysql-$MyVersion-winx64.zip"
    if (-not (Download-File -Url $MyUrls -OutFile $zip -MinBytes 200MB)) {
        Fail-Setup 'mysql' 'MySQL 压缩包下载失败'
    }
    Write-Log ('解压到 {0}（约 236MB，请稍候）' -f $MyRoot)
    if (Test-Path $MyBasedir) { Remove-Item -Path $MyBasedir -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $MyRoot -Force
    if (-not (Test-Path $mysqld)) { Fail-Setup 'mysql' ('解压后未找到 mysqld.exe：' + $mysqld) }

    # my.ini —— 关键：bind-address 只监听本机
    $ini = @"
[mysqld]
basedir=$($MyBasedir -replace '\\','/')
datadir=$($MyData -replace '\\','/')
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
max_connections=100
default-storage-engine=INNODB
log-error=$($MyRoot -replace '\\','/')/mysql-error.log

[client]
port=3306
default-character-set=utf8mb4
"@
    Set-Content -Path $MyIni -Value $ini -Encoding ASCII
    Write-Log ('已写入配置文件：{0}（bind-address=127.0.0.1）' -f $MyIni)

    if (Test-Path $MyData) {
        Write-Log '数据目录已存在，跳过 initialize（幂等）'
    } else {
        Write-Log '初始化数据目录（--initialize-insecure，稍后立即设置强密码）'
        Invoke-Mysqld -Exe $mysqld -ArgList @("--defaults-file=$MyIni", '--initialize-insecure') -Stage 'mysql' -What '数据目录初始化'
        if (-not (Test-Path (Join-Path $MyData 'mysql'))) { Fail-Setup 'mysql' '数据目录初始化失败' }
    }

    if ($svc) {
        Write-Log ('服务 {0} 已存在，跳过注册' -f $MyService)
    } else {
        Write-Log ('注册 Windows 服务：{0}' -f $MyService)
        # 参数顺序是硬要求：--install 必须排在 --defaults-file 之前。
        # 反了会被 mysqld 当成服务器选项，报 "unknown option '--install'"，且该错误只写进
        # mysql-error.log 不落控制台（已实测踩坑，故同时接入 Invoke-Mysqld 的输出捕获）。
        Invoke-Mysqld -Exe $mysqld -ArgList @('--install', $MyService, "--defaults-file=$MyIni") -Stage 'mysql' -What '注册 Windows 服务'
    }
    # 服务可能已注册但停止（上一次运行中断或刚被外部注册）
    $svc = Get-Service -Name $MyService -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -ne 'Running') {
        Write-Log ('服务 {0} 当前为 {1}，执行启动' -f $MyService, $svc.Status)
        Start-Service -Name $MyService -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 8
    $svc = Get-Service -Name $MyService -ErrorAction SilentlyContinue
    if (-not $svc -or $svc.Status -ne 'Running') {
        Fail-Setup 'mysql' ('服务未处于运行状态：' + ($(if($svc){$svc.Status}else{'不存在'})))
    }
    Write-Log ('服务运行中：{0}（监听 127.0.0.1:3306）' -f $svc.Status)
    $State.mysql = [ordered]@{ status = 'INSTALLED'; basedir = $MyBasedir; service = $MyService; port = 3306; bindAddress = '127.0.0.1' }
}

# ============================================================
# 三、数据库与应用账号
# ============================================================
Write-Log '---------- [3/4] 数据库与应用账号 ----------'
$rootPwd = $null
$appPwd = $null
if (Test-Path $SecretsFile) {
    Write-Log ('本地密钥文件已存在，沿用既有密码：{0}' -f $SecretsFile)
    $State.database = [ordered]@{ status = 'ALREADY_EXISTS' }
    $State.secrets = [ordered]@{ status = 'ALREADY_EXISTS'; path = $SecretsFile }
} elseif ($State.mysql.status -in @('INSTALLED', 'ALREADY_INSTALLED')) {
    if (-not (Test-Path $mysqlCli)) { Fail-Setup 'database' ('未找到 mysql 客户端：' + $mysqlCli) }
    $rootPwd = New-StrongPassword 24
    $appPwd = New-StrongPassword 24
    Write-Log '设置 root 强密码并创建数据库与最小权限应用账号（密码不打印）'
    $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPwd';
CREATE DATABASE IF NOT EXISTS $DbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$DbAppUser'@'127.0.0.1' IDENTIFIED BY '$appPwd';
ALTER USER '$DbAppUser'@'127.0.0.1' IDENTIFIED BY '$appPwd';
GRANT ALL PRIVILEGES ON $DbName.* TO '$DbAppUser'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
    $tmpSql = Join-Path $WorkDir 'init-db.sql'
    Set-Content -Path $tmpSql -Value $sql -Encoding ASCII
    & $mysqlCli "--defaults-file=$MyIni" '-u' 'root' "-e" "source $($tmpSql -replace '\\','/')" 2>&1 | ForEach-Object { Write-Log $_ }
    # 用新 root 密码验证连通性
    $probe = & $mysqlCli "--defaults-file=$MyIni" '-u' 'root' "-p$rootPwd" '-h127.0.0.1' '-N' '-e' 'SELECT 1;' 2>&1
    if ("$probe" -notmatch '1') {
        Fail-Setup 'database' ('root 密码设置后连通性验证失败：' + "$probe")
    }
    Write-Log '数据库连通性验证通过（root，127.0.0.1）'
    Remove-Item -Path $tmpSql -Force -ErrorAction SilentlyContinue
    $State.database = [ordered]@{ status = 'CREATED'; database = $DbName; appUser = "$DbAppUser@127.0.0.1" }

    # 写入 DPAPI 加密的本地密钥文件
    Write-Log ('写入 DPAPI 加密密钥文件（避免明文落盘）：{0}' -f $SecretsFile)
    $dir = Split-Path -Parent $SecretsFile
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $payload = [ordered]@{
        schema         = 'local-secrets/v1'
        encryptedWith  = 'Windows DPAPI / CurrentUser'
        createdAt      = (Get-Date).ToString('s')
        createdByUser  = $env:USERNAME
        note           = '本文件含敏感凭据，已被 .gitignore 排除，严禁提交到 Git'
        mysql          = [ordered]@{
            host     = '127.0.0.1'
            port     = 3306
            database = $DbName
            appUser  = $DbAppUser
            appUserPasswordEnc  = (Protect-Text $appPwd)
            rootUser            = 'root'
            rootPasswordEnc     = (Protect-Text $rootPwd)
        }
    }
    ($payload | ConvertTo-Json -Depth 6) | Set-Content -Path $SecretsFile -Encoding UTF8
    $State.secrets = [ordered]@{ status = 'WRITTEN'; path = $SecretsFile }
    Write-Log '密钥文件已写入（内容为 DPAPI 密文）'
} else {
    Write-Log 'MySQL 不可用，跳过数据库初始化' 'WARN'
    $State.database = [ordered]@{ status = 'SKIPPED' }
    $State.secrets = [ordered]@{ status = 'SKIPPED'; path = $SecretsFile }
}

# ============================================================
# 四、汇总
# ============================================================
Write-Log '---------- [4/4] 汇总 ----------'
Save-State
Write-Log ('结果文件：{0}' -f $DoneFile)
Write-Log ('日志文件：{0}' -f $LogFile)
Write-Log '========== 采集端环境自检结束 =========='
Write-Host ''
Get-Content -Path $DoneFile -Encoding UTF8
exit 0
