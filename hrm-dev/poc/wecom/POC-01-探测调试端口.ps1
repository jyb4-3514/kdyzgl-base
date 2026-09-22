#Requires -Version 5.1
<#
    POC-01  企微 PC 客户端是否暴露 CDP 调试端口（决定 ADR 第 5.1 章 R1 可行性）

    依据：hrm-dev/docs/wecom-integration-adr.md
          - 第 7 章 POC-01（最小做法 / 通过判据 / 失败判据）
          - 第 14.2 章 W-C1 六条判定标准（本脚本对第 1/2/3 条自证）
          - 第 19 章 V31（客户端版本登记）

    红线（脚本已内建）：
      1. 只读优先：不带 -Launch 时只做静态枚举与端口探测，不动企微进程
      2. 不修改企微安装目录、不写注册表、不注入 DLL、不读取进程内存（不使用任何内存读写类 Windows API）
      3. 带 -Launch 时仅在用户确认后：关闭当前实例 → 以 --remote-debugging-port 重新启动（无持久化改动，
         关闭该实例并按正常方式重开企微即完全恢复）

    零安装：仅使用 Windows 自带 Windows PowerShell 5.1 + .NET Framework

    用法：
      只读探测：        powershell -ExecutionPolicy Bypass -File "POC-01-探测调试端口.ps1"
      带调试端口重启：  powershell -ExecutionPolicy Bypass -File "POC-01-探测调试端口.ps1" -Launch
      自动化免确认：    powershell -ExecutionPolicy Bypass -File "POC-01-探测调试端口.ps1" -Launch -Yes
#>
[CmdletBinding()]
param(
    # CDP 调试端口（启动时使用，也只探测该端口）
    [int]$Port = 9222,
    # 结果输出目录（默认脚本所在目录）
    [string]$OutputDir = $PSScriptRoot,
    # 显式传入才会执行「关闭实例 → 带调试端口重启」；不传则全程只读
    [switch]$Launch,
    # 跳过交互确认（仍须显式 -Launch）；供自动化调用
    [switch]$Yes,
    # 追加启动参数（例如部分 Chromium 版本 attach 需要的 --remote-allow-origins=*），按空格拆分
    [string]$ExtraArgs = ""
)

$ErrorActionPreference = 'Continue'
# 控制台按 UTF-8 输出，避免中文乱码（PS 5.1 默认 GBK 控制台）
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { $OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ==================== 通用工具 ====================

# 无 BOM UTF-8 写文件（结果文件要进 Git，避免 BOM 干扰后续解析）
function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

# 路径脱敏：隐藏本机用户名等个人标识
function Protect-Path([string]$P) {
    if ([string]::IsNullOrEmpty($P)) { return $P }
    return [regex]::Replace($P, '(?i)\\Users\\[^\\]+', '\Users\<USER>')
}

# 文本脱敏：手机号仅留后 4 位、邮箱/身份证占位、超长截断
function Protect-Text([string]$T, [int]$MaxLen = 60) {
    if ([string]::IsNullOrEmpty($T)) { return $T }
    $s = $T
    # 11 位手机号 → 前缀掩码，仅留后 4 位（对齐红线：手机号脱敏）
    $s = [regex]::Replace($s, '(?<!\d)1[3-9]\d{9}(?!\d)', { param($m) '****' + $m.Value.Substring(7) })
    # 邮箱 / 18 位身份证 直接占位（真实业务标识不进结果文件）
    $s = [regex]::Replace($s, '[\w\.\-]+@[\w\-]+\.[\w\.\-]+', '<EMAIL>')
    $s = [regex]::Replace($s, '\d{17}[\dXx]', '<IDCARD>')
    if ($s.Length -gt $MaxLen) { $s = $s.Substring(0, $MaxLen) + '…' }
    return $s
}

# 环境自检：脚本必须先自检运行环境并写入结果文件
function Get-EnvInfo([string]$ScriptName) {
    $isElevated = $false
    try {
        $wi = [Security.Principal.WindowsIdentity]::GetCurrent()
        $wp = New-Object Security.Principal.WindowsPrincipal($wi)
        $isElevated = $wp.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch {}

    $uiaOk = $false
    try {
        Add-Type -AssemblyName UIAutomationClient -ErrorAction Stop
        Add-Type -AssemblyName UIAutomationTypes -ErrorAction Stop
        $uiaOk = $true
    } catch { $uiaOk = $false }

    # Python 是否存在（POC-02/05 备选脚本依赖）
    $pyFound = $false; $pyVer = $null
    foreach ($cand in @('python', 'py')) {
        try {
            $out = & $cand '--version' 2>&1
            if ($LASTEXITCODE -eq 0 -and $out) { $pyFound = $true; $pyVer = ("$out").Trim(); break }
        } catch {}
    }

    return [ordered]@{
        script             = $ScriptName
        hostOs             = [System.Environment]::OSVersion.VersionString
        powershellVersion  = $PSVersionTable.PSVersion.ToString()
        powershellEdition  = $(if ($PSVersionTable.PSEdition) { $PSVersionTable.PSEdition } else { 'Desktop' })
        isElevated         = $isElevated
        dotnetUiaAvailable = $uiaOk
        pythonFound        = $pyFound
        pythonVersion      = $pyVer
    }
}

# 原始 HTTP GET（不走系统代理，避免 localhost 被代理劫持；PS 5.1 下比 Invoke-WebRequest 更可控）
function Get-HttpText([string]$Url, [int]$TimeoutSec = 5) {
    $result = [ordered]@{ ok = $false; status = $null; body = $null; error = $null }
    try {
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method = 'GET'
        $req.Proxy = $null
        $req.Timeout = $TimeoutSec * 1000
        $req.ReadWriteTimeout = $TimeoutSec * 1000
        $resp = $req.GetResponse()
        $result.status = [int]$resp.StatusCode
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $result.body = $sr.ReadToEnd()
        $sr.Close(); $resp.Close()
        $result.ok = $true
    } catch [System.Net.WebException] {
        $ex = $_.Exception
        if ($ex.Response) { $result.status = [int]$ex.Response.StatusCode }
        $result.error = $ex.Message
    } catch {
        $result.error = $_.Exception.Message
    }
    return $result
}

# ==================== 1. 定位企微客户端 ====================

# 从注册表候选键中找安装信息（只读枚举，不写注册表）
function Get-WeComRegistryInfo {
    $checked = @()
    $found = @()
    $keys = @(
        'HKCU:\Software\Tencent\WXWork',
        'HKLM:\SOFTWARE\Tencent\WXWork',
        'HKLM:\SOFTWARE\WOW6432Node\Tencent\WXWork',
        'HKCU:\Software\Tencent\WeCom',
        'HKLM:\SOFTWARE\Tencent\WeCom',
        'HKLM:\SOFTWARE\WOW6432Node\Tencent\WeCom'
    )
    foreach ($k in $keys) {
        $exists = Test-Path $k
        $checked += [ordered]@{ key = $k; exists = $exists }
        if (-not $exists) { continue }
        try {
            $item = Get-ItemProperty -Path $k -ErrorAction Stop
            $names = @($item.PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' } | ForEach-Object { $_.Name })
            # 只挑与安装路径相关的值（名字含 install / path / dir）
            $pathVals = [ordered]@{}
            foreach ($n in $names) {
                if ($n -match '(?i)install|path|dir') {
                    $v = [string]$item.$n
                    if (-not [string]::IsNullOrEmpty($v)) { $pathVals[$n] = (Protect-Path $v) }
                }
            }
            $found += [ordered]@{ key = $k; valueNames = $names; pathLikeValues = $pathVals }
        } catch {
            $found += [ordered]@{ key = $k; valueNames = @(); pathLikeValues = [ordered]@{}; error = $_.Exception.Message }
        }
    }
    return [ordered]@{ checkedKeys = $checked; matchedKeys = $found }
}

# 常见安装目录探测（不递归全盘）
function Get-WeComInstallCandidates {
    $cands = @()
    $roots = @()
    if ($env:ProgramFiles) { $roots += $env:ProgramFiles }
    if (${env:ProgramFiles(x86)}) { $roots += ${env:ProgramFiles(x86)} }
    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'Tencent') }
    if ($env:APPDATA) { $roots += (Join-Path $env:APPDATA 'Tencent') }

    foreach ($r in $roots) {
        foreach ($sub in @('WXWork', 'WeCom', 'Tencent\WXWork')) {
            $exe = Join-Path (Join-Path $r $sub) 'WXWork.exe'
            $cands += [ordered]@{ exePath = (Protect-Path $exe); exists = (Test-Path $exe) }
        }
    }
    return $cands
}

# 读取 exe 版本信息（V31 需要的客户端版本登记原始数据）
function Get-ExeVersionInfo([string]$ExePath) {
    try {
        $vi = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($ExePath)
        return [ordered]@{
            fileVersion    = $vi.FileVersion
            productVersion = $vi.ProductVersion
            companyName    = $vi.CompanyName
            fileDescription = $vi.FileDescription
        }
    } catch {
        return [ordered]@{ error = $_.Exception.Message }
    }
}

# ==================== 2. 进程与技术栈特征 ====================

# 企微进程枚举（按进程名 + 按可执行文件路径双通道）
function Get-WeComProcessInfo {
    $list = @()
    $procs = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '(?i)^wxwork' })
    foreach ($p in $procs) {
        $path = $null
        try { $path = $p.Path } catch { $path = $null }
        $list += [ordered]@{
            pid           = $p.Id
            processName   = $p.ProcessName
            exePath       = (Protect-Path $path)
            pathReadable  = [bool]$path
        }
    }
    if ($list.Count -eq 0) {
        # 兜底：按主模块路径含 \WXWork\ 匹配（进程名异常时使用）
        $all = [System.Diagnostics.Process]::GetProcesses()
        foreach ($p in $all) {
            try {
                $fn = $p.MainModule.FileName
                if ($fn -like '*\WXWork\*') {
                    $list += [ordered]@{
                        pid = $p.Id; processName = $p.ProcessName
                        exePath = (Protect-Path $fn); pathReadable = $true
                    }
                }
            } catch {} finally { try { $p.Dispose() } catch {} }
        }
    }
    return $list
}

# CEF / Electron 特征模块名（仅做文件名匹配，不读内存）
$script:CefModuleKeywords = @(
    'libcef.dll', 'chrome_elf.dll', 'chrome.dll', 'node.dll',
    'ffmpeg.dll', 'libegl.dll', 'libglesv2.dll', 'vk_swiftshader.dll',
    'v8_context_snapshot', 'icudtl.dat', 'resources.pak'
)

# 进程模块枚举：优先 .NET Modules，失败则回退 tasklist /m（均为模块名枚举，非内存读取）
function Get-WeComModuleInfo([int]$Pid) {
    $modules = @()
    $method = 'dotnet'
    try {
        $p = Get-Process -Id $Pid -ErrorAction Stop
        $modules = @($p.Modules | ForEach-Object { $_.ModuleName })
    } catch {
        $method = 'tasklist'
        try {
            $raw = & tasklist /fi ("pid eq $Pid") /m /fo csv 2>$null
            # tasklist /m 输出形如 "WXWork.exe","1234","module1, module2"
            if ($raw -and $raw.Count -ge 2) {
                $parts = ($raw[1] -split '","')
                if ($parts.Count -ge 3) {
                    $mods = $parts[2].Trim('"')
                    $modules = @($mods -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' -and $_ -ne 'N/A' })
                }
            }
        } catch { $method = 'failed' }
    }
    $hit = @($modules | Where-Object { $m = $_.ToLower(); $script:CefModuleKeywords | Where-Object { $m -like "*$_*" } })
    return [ordered]@{
        method          = $method
        moduleCount     = $modules.Count
        cefFeatureModules = $hit
        cefFeatureHit   = ($hit.Count -gt 0)
        moduleNamesSample = @($modules | Select-Object -First 60)
    }
}

# 安装目录下的 CEF 特征文件（只做文件名枚举）
function Get-WeComDirFeature([string]$Dir) {
    $names = @()
    if ([string]::IsNullOrEmpty($Dir) -or -not (Test-Path $Dir)) {
        return [ordered]@{ dirReadable = $false; matchedFiles = @(); hasResourcesDir = $false; hasLocalesDir = $false }
    }
    try {
        $names = @(Get-ChildItem -Path $Dir -File -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
    } catch {}
    $matched = @($names | Where-Object { $n = $_.ToLower(); $script:CefModuleKeywords | Where-Object { $n -like "*$_*" } })
    return [ordered]@{
        dirReadable     = $true
        matchedFiles    = $matched
        hasResourcesDir = (Test-Path (Join-Path $Dir 'resources'))
        hasLocalesDir   = (Test-Path (Join-Path $Dir 'locales'))
    }
}

# ==================== 3. CDP 端口探测 ====================

# 解析 /json/list 响应：只保留结构信息与占位标题，避免真实群名/人名进结果文件
function Convert-TargetList([string]$Body) {
    $out = @()
    $types = @()
    if ([string]::IsNullOrEmpty($Body)) { return @{ targets = @(); targetTypes = @() } }
    try {
        $arr = $Body | ConvertFrom-Json
        $i = 0
        foreach ($t in $arr) {
            $i++
            $types += ([string]$t.type)
            $url = [string]$t.url
            $safeUrl = $url
            if ($url) { $safeUrl = [regex]::Replace($url, '\?.*$', '?<QUERY_REDACTED>') }
            $out += [ordered]@{
                type      = [string]$t.type
                title     = "TITLE_$i"          # 占位：target 标题可能含群名/人名
                titleLen  = ([string]$t.title).Length
                url       = $safeUrl
                hasWsUrl  = [bool]($t.webSocketDebuggerUrl)   # 仅记录是否存在，不落 ws 地址
            }
        }
    } catch {
        return @{ targets = @(); targetTypes = @(); parseError = $_.Exception.Message }
    }
    return @{ targets = $out; targetTypes = @($types | Select-Object -Unique) }
}

# ==================== 主流程 ====================

$outputFile = Join-Path $OutputDir 'POC-01-结果.json'
Write-Host "==== POC-01 企微 CDP 调试端口探测 ====" -ForegroundColor Cyan
Write-Host "输出文件：$outputFile"

$envInfo = Get-EnvInfo 'POC-01'
Write-Host ("[环境] Windows PowerShell {0} | 管理员={1} | .NET UIA 可加载={2} | Python={3}" -f `
    $envInfo.powershellVersion, $envInfo.isElevated, $envInfo.dotnetUiaAvailable, $(if ($envInfo.pythonFound) { $envInfo.pythonVersion } else { '未找到' }))

# --- 1. 定位客户端 ---
$regInfo     = Get-WeComRegistryInfo
$candList    = Get-WeComInstallCandidates
$procsBefore = Get-WeComProcessInfo

$installPath = $null
$exePath     = $null
foreach ($p in $procsBefore) { if ($p.exePath) { $exePath = $p.exePath; break } }
if (-not $exePath) {
    foreach ($c in $candList) { if ($c.exists) { $exePath = $c.exePath; break } }
}
foreach ($m in $regInfo.matchedKeys) {
    foreach ($k in $m.pathLikeValues.Keys) {
        $v = [string]$m.pathLikeValues[$k]
        if ($v -and (Test-Path $v)) { $installPath = $v; break }
    }
    if ($installPath) { break }
}
if (-not $installPath -and $exePath) { $installPath = Split-Path -Parent $exePath }

# exePath 脱敏后可能含 <USER>，Test-Path 会失败，这里用原始路径重新取
$rawExePath = $exePath
if ($exePath -and $exePath -like '*<USER>*') {
    foreach ($p in $procsBefore) { try { if ((Get-Process -Id $p.pid).Path) { $rawExePath = (Get-Process -Id $p.pid).Path; break } } catch {} }
    if ($rawExePath -like '*<USER>*') {
        foreach ($c in $candList) { if ($c.exists) { $rawExePath = ($c.exePath -replace '<USER>', $env:USERNAME); break } }
    }
}

$versionInfo = if ($rawExePath -and (Test-Path $rawExePath)) { Get-ExeVersionInfo $rawExePath } else { [ordered]@{ error = 'exe 路径不可用，无法读取版本' } }

Write-Host ("[定位] 进程数={0} | 安装目录={1} | 主程序={2}" -f $procsBefore.Count, $(if ($installPath) { $installPath } else { '未找到' }), $(if ($exePath) { $exePath } else { '未找到' }))

# --- 2. 技术栈特征 ---
$moduleInfo = [ordered]@{ method = 'n/a'; moduleCount = 0; cefFeatureModules = @(); cefFeatureHit = $false; moduleNamesSample = @() }
if ($procsBefore.Count -gt 0) { $moduleInfo = Get-WeComModuleInfo $procsBefore[0].pid }

$rawInstallDir = if ($installPath) { $installPath -replace '<USER>', $env:USERNAME } else { $null }
$dirFeature = Get-WeComDirFeature $rawInstallDir

Write-Host ("[特征] 模块枚举方式={0} | CEF 特征命中={1} | 命中模块={2}" -f `
    $moduleInfo.method, $moduleInfo.cefFeatureHit, (($moduleInfo.cefFeatureModules) -join ', '))

# --- 3. 端口探测（只读 / 带调试端口重启） ---
$debugPortLaunched = $false
$jsonVersion = [ordered]@{ ok = $false; status = $null; body = $null; error = $null }
$jsonList    = [ordered]@{ ok = $false; status = $null; body = $null; error = $null }
$targets = @(); $targetTypes = @()
$launchNote = ''
$portInUse = $false

$baseUrl = "http://127.0.0.1:$Port"

if (-not $Launch) {
    $launchNote = '未使用 -Launch：本次仅只读探测，未重启企微。若端口不可用属预期，不可据此判定 R1 失败。'
    Write-Host "[探测] 只读模式，探测 $baseUrl ..." -ForegroundColor Yellow
    $jsonVersion = Get-HttpText "$baseUrl/json/version"
    $jsonList    = Get-HttpText "$baseUrl/json/list"
} else {
    # 关闭实例 → 带调试端口重启（需用户确认）
    Write-Host ""
    Write-Host "即将执行【关闭当前企业微信 → 以 --remote-debugging-port=$Port 重新启动】。" -ForegroundColor Yellow
    Write-Host "该操作不修改安装目录、不写注册表；关闭该实例并用正常方式重开企微即可完全恢复。" -ForegroundColor Yellow
    $confirm = $true
    if (-not $Yes) {
        $ans = Read-Host "确认执行？输入大写 Y 继续，其他任意键取消"
        $confirm = ($ans -eq 'Y')
    }
    if (-not $confirm) {
        $launchNote = '用户未确认，已跳过重启步骤。'
        Write-Host "[中止] 已跳过重启，仅完成只读部分。" -ForegroundColor Yellow
    } else {
        if (-not $rawExePath -or -not (Test-Path $rawExePath)) {
            $launchNote = '未能定位 WXWork.exe，无法带调试端口启动。'
            Write-Host "[失败] $launchNote" -ForegroundColor Red
        } else {
            # 优雅关闭现有实例
            $existing = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '(?i)^wxwork' })
            foreach ($p in $existing) {
                try { [void]$p.CloseMainWindow() } catch {}
            }
            Start-Sleep -Seconds 3
            foreach ($p in @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '(?i)^wxwork' })) {
                try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
            }
            Start-Sleep -Seconds 2

            $args = @("--remote-debugging-port=$Port")
            if ($ExtraArgs) { $args += ($ExtraArgs -split '\s+' | Where-Object { $_ -ne '' }) }
            Write-Host ("[启动] {0} {1}" -f $rawExePath, ($args -join ' '))
            try {
                Start-Process -FilePath $rawExePath -ArgumentList $args
                $debugPortLaunched = $true
            } catch {
                $launchNote = "启动失败：$($_.Exception.Message)"
                Write-Host "[失败] $launchNote" -ForegroundColor Red
            }
            if ($debugPortLaunched) {
                Write-Host "[等待] 等待客户端加载并按调试端口监听（15 秒）..." -ForegroundColor Yellow
                Start-Sleep -Seconds 15
            }
        }
    }
}

# 端口探测（无论是否重启都执行一次）
$jsonVersion = Get-HttpText "$baseUrl/json/version"
$jsonList    = Get-HttpText "$baseUrl/json/list"
$portInUse   = $jsonVersion.ok

if ($jsonList.ok -and $jsonList.body) {
    $parsed = Convert-TargetList $jsonList.body
    $targets = $parsed.targets
    $targetTypes = $parsed.targetTypes
}

Write-Host ("[结果] /json/version HTTP={0} | /json/list HTTP={1} | target 数={2}" -f `
    $(if ($jsonVersion.status) { $jsonVersion.status } else { '无响应' }), `
    $(if ($jsonList.status) { $jsonList.status } else { '无响应' }), `
    $targets.Count)

# --- 4. 判定 ---
$verdict = 'INCONCLUSIVE'
$evidenceNote = ''
if ($targets.Count -gt 0) {
    $verdict = 'PASS'
    $evidenceNote = 'CDP 端口可用且已列出 target：R1（客户端 DOM/CDP 旁路）具备初步可行性。按 ADR 通过判据，仍需在实现阶段验证「能 attach 且能读到会话列表相关 DOM/可访问性节点」（POC-01 范围不含 DOM 读取）。标题已占位（TITLE_n），避免群名/人名入库。'
} elseif ($portInUse) {
    $verdict = 'INCONCLUSIVE'
    $evidenceNote = '/json/version 可达但 /json/list 未返回可解析 target。可能为：端口被其他进程占用、target 尚未创建、或响应格式不符。请核对 /json/version 中浏览器标识后重跑，或改端口重试。'
} else {
    if ($Launch -and $debugPortLaunched) {
        $verdict = 'FAIL'
        $evidenceNote = '已带 --remote-debugging-port 启动，但调试端口不可达：R1 不成立（转 POC-02）。若需再确认，可追加 -ExtraArgs "--remote-allow-origins=*" 重试。注意：端口不可达亦可能因客户端以单实例方式复用了已运行进程。'
    } else {
        $verdict = 'INCONCLUSIVE'
        $evidenceNote = '本次未执行带调试端口启动（' + $(if ($launchNote) { $launchNote } else { '只读模式' }) + '），端口不可达属预期，不能据此判定 R1 失败。需以管理员身份或按手册执行 -Launch 模式复测。'
    }
}

# ==================== 输出 ====================
$result = [ordered]@{
    poc                = 'POC-01'
    title              = '企微 PC 客户端 CDP 调试端口探测'
    generatedAt        = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    env                = $envInfo
    clientInstallPath  = (Protect-Path $installPath)
    clientVersion      = $versionInfo
    exePath            = (Protect-Path $exePath)
    registryInfo       = $regInfo
    installCandidates  = $candList
    processFound       = ($procsBefore.Count -gt 0)
    processCount       = $procsBefore.Count
    processList        = $procsBefore
    cefFeatureModules  = $moduleInfo.cefFeatureModules
    cefFeatureHit      = $moduleInfo.cefFeatureHit
    moduleEnumMethod   = $moduleInfo.method
    moduleCount        = $moduleInfo.moduleCount
    moduleNamesSample  = $moduleInfo.moduleNamesSample
    installDirFeature  = $dirFeature
    probePort          = $Port
    debugPortLaunched  = $debugPortLaunched
    launchNote         = $launchNote
    jsonVersionStatus  = $jsonVersion.status
    jsonVersionError   = $jsonVersion.error
    jsonVersionBody    = $(if ($jsonVersion.body) { Protect-Text $jsonVersion.body 400 } else { $null })
    jsonListStatus     = $jsonList.status
    jsonListError      = $jsonList.error
    jsonListTargets    = $targets
    jsonListTargetTypes = $targetTypes
    verdict            = $verdict
    evidenceNote       = $evidenceNote
    rollback           = '关闭本次带调试端口启动的企微实例，再按正常方式（开始菜单/桌面快捷方式）启动企业微信，即完全恢复；本操作不产生任何持久化改动。'
}

$json = $result | ConvertTo-Json -Depth 12
Write-Utf8NoBom $outputFile $json

Write-Host ""
Write-Host "==== 结论 ====" -ForegroundColor Cyan
Write-Host ("verdict = {0}" -f $verdict) -ForegroundColor $(if ($verdict -eq 'PASS') { 'Green' } elseif ($verdict -eq 'FAIL') { 'Red' } else { 'Yellow' })
Write-Host ("说明：{0}" -f $evidenceNote)
Write-Host ("结果已写入：{0}" -f $outputFile)
Write-Host ""
Write-Host "回滚：关闭本次启动的企微实例，按正常方式重开企业微信即可。" -ForegroundColor Yellow
