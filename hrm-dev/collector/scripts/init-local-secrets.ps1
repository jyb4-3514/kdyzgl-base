<#
  采集端 · 多多采集账号密码写入本地加密密钥文件（幂等，可重复执行）

  作用：
    把多多账号与密码用 Windows DPAPI（CurrentUser 作用域）加密后，写入
    config\local\local.secrets.json 的 pdd 段，供 Python 侧解密用于自动登录。

  前提：
    先执行过 scripts\env-setup.ps1 —— 由它创建该密钥文件并写入 mysql 段；
    本脚本只增改 pdd 段，绝不触碰 mysql 段。

  红线：
    - 明文零落地：不写任何文件、不打印、不进日志；只在内存中加密后写密文
    - 真实凭据不入 Git（config\local\* 已被 .gitignore 排除）
    - 日志只打印脱敏账号

  用法（在 hrm-dev\collector 目录下执行；非交互场景由主智能体调用）：
    powershell -ExecutionPolicy Bypass -File scripts\init-local-secrets.ps1 `
        -Site 'https://mcmd.pinduoduo.com/home' -Account '16600000000' `
        -PasswordSecure (Read-Host -AsSecureString '请输入多多账号密码') `
        -AuthorizedBy '张三' -AuthorizationNote '2026-09-22 站长书面授权，扫描件见授权台账 #12'

    # 已有 pdd 段时覆盖（站长改密、凭据错误后轮换）
    ... -Force

    # 指定密钥文件位置（默认按脚本位置推导为 ..\config\local\local.secrets.json）
    ... -SecretsFile 'D:\yizhan\hrm-dev\collector\config\local\local.secrets.json'

  退出码：0 成功或按幂等跳过；1 失败
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Site,
    [Parameter(Mandatory = $true)][string]$Account,
    # 二选一：推荐 -PasswordSecure（不进命令历史）；-Password 为明文参数，仅作兜底
    [string]$Password,
    [System.Security.SecureString]$PasswordSecure,
    [Parameter(Mandatory = $true)][string]$AuthorizedBy,
    [string]$AuthorizationNote = '',
    [string]$SecretsFile = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# 默认密钥文件：按脚本位置推导（<collector>\scripts → <collector>\config\local\local.secrets.json）
if ([string]::IsNullOrWhiteSpace($SecretsFile)) {
    $SecretsFile = Join-Path (Split-Path -Parent $PSScriptRoot) 'config\local\local.secrets.json'
}
$SecretsFile = [System.IO.Path]::GetFullPath($SecretsFile)

# UTF-8 带 BOM：PowerShell 5.1 在中文系统上按 GBK 解码无 BOM 的 UTF-8 会导致乱码，
# 故写入与日志一律显式使用带 BOM 的 UTF-8 编码对象。
$Utf8Bom = New-Object System.Text.UTF8Encoding($true)

$LogDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime\logs'
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }
$LogFile = Join-Path $LogDir 'init-local-secrets.log'

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    # 只写脱敏后的消息，调用方负责不传入明文
    $line = '[{0}] [{1}] {2}' -f (Get-Date).ToString('HH:mm:ss'), $Level, $Message
    Write-Host $line
    [System.IO.File]::AppendAllText($LogFile, $line + [Environment]::NewLine, $Utf8Bom)
}

function Protect-Text {
    param([string]$Plain)
    Add-Type -AssemblyName System.Security
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Plain)
    $enc = [System.Security.Cryptography.ProtectedData]::Protect(
        $bytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
    return [Convert]::ToBase64String($enc)
}

function Convert-SecureStringToPlain {
    # 仅在内存中转换：SecureString -> BSTR -> .NET String -> 立即清零 BSTR
    param([System.Security.SecureString]$Secure)
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Get-MaskedAccount {
    # 与 Python 侧 PddAccount.masked_account 保持同一口径
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    if ($Text.Length -le 2) { return ('*' * $Text.Length) }
    if ($Text.Length -ge 7) {
        return $Text.Substring(0, 3) + ('*' * 4) + $Text.Substring($Text.Length - 4)
    }
    return $Text.Substring(0, 1) + ('*' * ($Text.Length - 2)) + $Text.Substring($Text.Length - 1)
}

Write-Log '========== 多多账号密码写入开始 =========='

# ---------------- 入参校验 ----------------
$Site = $Site.Trim()
$Account = $Account.Trim()
if ($Site -notmatch '^https://') {
    Write-Log ('站点 URL 必须以 https:// 开头（当前：{0}）' -f $Site) 'ERROR'
    exit 1
}
if ([string]::IsNullOrWhiteSpace($Account)) {
    Write-Log '账号不能为空' 'ERROR'
    exit 1
}
if ([string]::IsNullOrWhiteSpace($AuthorizedBy)) {
    Write-Log '授权人（-AuthorizedBy）不能为空：ADR §20.2.9 要求先归档站长书面授权' 'ERROR'
    exit 1
}
if ([string]::IsNullOrWhiteSpace($Password) -and ($null -eq $PasswordSecure)) {
    Write-Log '必须提供 -PasswordSecure 或 -Password（推荐 -PasswordSecure，避免明文进命令历史）' 'ERROR'
    exit 1
}

if (-not (Test-Path $SecretsFile)) {
    Write-Log ('未找到本地密钥文件：{0}' -f $SecretsFile) 'ERROR'
    Write-Log '请先执行 scripts\env-setup.ps1 完成环境与 MySQL 初始化（它会创建该文件并写入 mysql 段）' 'ERROR'
    exit 1
}

# ---------------- 读取既有文件（保留 mysql 段） ----------------
try {
    $existingText = [System.IO.File]::ReadAllText($SecretsFile, [System.Text.Encoding]::UTF8)
    $payload = $existingText | ConvertFrom-Json
} catch {
    Write-Log ('本地密钥文件读取或解析失败：{0}' -f $_.Exception.Message) 'ERROR'
    exit 1
}

if ($payload.PSObject.Properties.Name -contains 'pdd' -and -not $Force) {
    Write-Log ('密钥文件已存在 pdd 段，按幂等跳过；如需覆盖请追加 -Force（当前授权人：{0}）' -f $payload.pdd.authorizedBy) 'WARN'
    Write-Log '========== 多多账号密码写入结束（未修改） =========='
    exit 0
}

# ---------------- 加密写入 ----------------
$plainPassword = $null
if ($null -ne $PasswordSecure) {
    $plainPassword = Convert-SecureStringToPlain -Secure $PasswordSecure
} else {
    $plainPassword = $Password
}

$accountEnc = $null
$passwordEnc = $null
try {
    $accountEnc = Protect-Text $Account
    $passwordEnc = Protect-Text $plainPassword
} catch {
    Write-Log ('DPAPI 加密失败：{0}' -f $_.Exception.Message) 'ERROR'
    exit 1
} finally {
    # 尽力清除内存中的明文引用（.NET 字符串不可变，无法保证彻底清零，仅减少驻留）
    $plainPassword = $null
    [System.GC]::Collect()
}

$pdd = [ordered]@{
    site              = $Site
    accountEnc        = $accountEnc
    passwordEnc       = $passwordEnc
    authorizedBy      = $AuthorizedBy
    authorizedAt      = (Get-Date).ToString('s')
    authorizationNote = $AuthorizationNote
}
$payload | Add-Member -NotePropertyName pdd -NotePropertyValue $pdd -Force

try {
    $json = $payload | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText($SecretsFile, $json, $Utf8Bom)
} catch {
    Write-Log ('密钥文件写入失败：{0}' -f $_.Exception.Message) 'ERROR'
    exit 1
}

$masked = Get-MaskedAccount $Account
Write-Log ('pdd 段已写入（DPAPI 密文）：account={0}，授权人={1}，站点={2}' -f $masked, $AuthorizedBy, $Site)
Write-Log ('密钥文件：{0}' -f $SecretsFile)
Write-Log '提醒：请确认账号密码已由站长本人提供并完成书面授权归档（ADR §20.6.1）'
Write-Log '========== 多多账号密码写入结束 =========='
exit 0
