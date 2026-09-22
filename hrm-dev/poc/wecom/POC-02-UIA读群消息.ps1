#Requires -Version 5.1
<#
    POC-02  UI Automation 能否读到群消息文本（决定 ADR 第 5.1 章 R2 可行性）——零安装主方案

    依据：hrm-dev/docs/wecom-integration-adr.md
          - 第 7 章 POC-02（最小做法 / 通过判据 / 失败判据）
          - 第 19 章 V31（两次冷启动后定位规则是否仍成立）

    实现：纯 Windows PowerShell 5.1 + .NET Framework 自带 System.Windows.Automation
          （UIAutomationClient / UIAutomationTypes），无需安装任何第三方包。
          这是本 POC 的主方案；pywinauto 版本仅为备选（见同目录 .py，手册已标注需 pip 安装）。

    红线：只读。不做 DLL 注入、不读取进程内存（不使用任何内存读写类 Windows API）、不 hook、不发送任何消息。

    脱敏（硬性）：结果文件会进 Git，故
          - 会话名统一写 GROUP_n（1 对 1 会话无法在不泄露的前提下区分，同样按 GROUP_n 占位）
          - 发送者统一写 MEMBER_n
          - 消息文本截断至 60 字符，并掩码手机号/邮箱/身份证
          - 每条均附 nameLen / textLen，用于证明「确实读到了内容」而无需泄露内容

    用法（冷启动前后各跑一次，输出两份文件便于比对）：
      第 1 次： powershell -ExecutionPolicy Bypass -File "POC-02-UIA读群消息.ps1" -RunIndex 1
      第 2 次： powershell -ExecutionPolicy Bypass -File "POC-02-UIA读群消息.ps1" -RunIndex 2
      更保险（只留长度、不留文本）： ... -RunIndex 1 -RedactMode full
#>
[CmdletBinding()]
param(
    # 1 = POC-02-结果-第1次.json；2 = POC-02-结果-第2次.json
    [ValidateSet(1, 2)]
    [int]$RunIndex = 1,
    [string]$OutputDir = $PSScriptRoot,
    # alias = 文本脱敏后保留（默认，用于证明读到文本）；full = 文本仅留长度
    [ValidateSet('alias', 'full')]
    [string]$RedactMode = 'alias',
    # 企微进程名（不区分大小写的正则）
    [string]$WeComProcessName = '^wxwork',
    # UIA 树遍历上限，防止深树/异常控件导致卡死
    [int]$MaxDepth = 25,
    [int]$MaxNodes = 6000,
    [int]$TimeBudgetSec = 90,
    # UIA 单次调用的整体保护（秒）
    [int]$NodeSampleLimit = 10
)

$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { $OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ==================== 通用工具（与 POC-01 同源实现，脚本各自独立以便单文件分发）====================

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Protect-Text([string]$T, [int]$MaxLen = 60) {
    if ([string]::IsNullOrEmpty($T)) { return $T }
    $s = $T
    $s = [regex]::Replace($s, '(?<!\d)1[3-9]\d{9}(?!\d)', { param($m) '****' + $m.Value.Substring(7) })
    $s = [regex]::Replace($s, '[\w\.\-]+@[\w\-]+\.[\w\.\-]+', '<EMAIL>')
    $s = [regex]::Replace($s, '\d{17}[\dXx]', '<IDCARD>')
    if ($s.Length -gt $MaxLen) { $s = $s.Substring(0, $MaxLen) + '…' }
    return $s
}

function Get-PythonInfo {
    foreach ($cand in @('python', 'py')) {
        try {
            $out = & $cand '--version' 2>&1
            if ($LASTEXITCODE -eq 0 -and $out) { return @{ found = $true; version = ("$out").Trim() } }
        } catch {}
    }
    return @{ found = $false; version = $null }
}

# ==================== UIA 装载与节点读写 ====================

$uiaLoaded = $false
$uiaLoadError = $null
try {
    Add-Type -AssemblyName UIAutomationClient -ErrorAction Stop
    Add-Type -AssemblyName UIAutomationTypes -ErrorAction Stop
    $uiaLoaded = $true
} catch {
    $uiaLoadError = $_.Exception.Message
}

# 读取单个 UIA 节点的语义属性（属性访问可能抛 ElementNotAvailableException，需逐项兜底）
function Get-NodeInfo($El, [int]$Depth) {
    $ct = $null; $nm = $null; $aid = $null; $cls = $null; $nwh = $null
    try { $ct = $El.Current.ControlType.ProgrammaticName -replace '^ControlType\.', '' } catch {}
    try { $nm = $El.Current.Name } catch {}
    try { $aid = $El.Current.AutomationId } catch {}
    try { $cls = $El.Current.ClassName } catch {}
    try { $nwh = $El.Current.NativeWindowHandle } catch {}
    return [ordered]@{
        depth        = $Depth
        controlType  = $ct
        name         = $nm          # 仅内存使用，落盘前统一脱敏
        nameLen      = $(if ($nm) { $nm.Length } else { 0 })
        automationId = $aid
        className    = $cls
        nativeHandle = $nwh
    }
}

# 广度优先遍历控件视图树，受 MaxDepth / MaxNodes / 时间预算三重保护
function Get-UiaFlatTree($RootEl, [int]$MaxDepth, [int]$MaxNodes, [int]$TimeBudgetSec) {
    $nodes = New-Object System.Collections.ArrayList
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $stack = New-Object System.Collections.Stack
    $stack.Push(@{ el = $RootEl; d = 0 })
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $maxDepthSeen = 0
    $truncated = $false

    while ($stack.Count -gt 0) {
        if ($nodes.Count -ge $MaxNodes) { $truncated = $true; break }
        if ($sw.Elapsed.TotalSeconds -ge $TimeBudgetSec) { $truncated = $true; break }

        $item = $stack.Pop()
        $el = $item.el; $d = $item.d
        if ($d -gt $maxDepthSeen) { $maxDepthSeen = $d }
        if ($d -gt $MaxDepth) { continue }

        $info = Get-NodeInfo $el $d
        $null = $nodes.Add(@{ info = $info; el = $el })

        # 子节点入栈（逆序压栈保证左到右顺序）
        $children = New-Object System.Collections.ArrayList
        try {
            $child = $walker.GetFirstChild($el)
            while ($child -ne $null) {
                $null = $children.Add($child)
                $child = $walker.GetNextSibling($child)
            }
        } catch {}
        for ($i = $children.Count - 1; $i -ge 0; $i--) {
            $stack.Push(@{ el = $children[$i]; d = ($d + 1) })
        }
    }
    return @{
        nodes        = $nodes
        treeDepth    = $maxDepthSeen
        nodeCount    = $nodes.Count
        truncated    = $truncated
        elapsedMs    = [int]$sw.ElapsedMilliseconds
    }
}

# ==================== 主流程 ====================

$pyInfo = Get-PythonInfo
$outFile = Join-Path $OutputDir ("POC-02-结果-第{0}次.json" -f $RunIndex)

Write-Host "==== POC-02 UIA 读群消息（第 $RunIndex 次） ====" -ForegroundColor Cyan
Write-Host "输出文件：$outFile"
Write-Host ("[环境] Windows PowerShell {0} | .NET UIA 可加载={1} | Python={2}" -f `
    $PSVersionTable.PSVersion.ToString(), $uiaLoaded, $(if ($pyInfo.found) { $pyInfo.version } else { '未找到' }))

$sessionListFound = $false
$sessionItems = @()
$messageAreaFound = $false
$messageSamples = @()
$inputBoxFound = $false
$treeDepth = 0
$nodeCount = 0
$locatorRuleDesc = ''
$verdict = 'INCONCLUSIVE'
$evidenceNote = ''
$controlTypeCounts = [ordered]@{}
$deepestSamples = @()
$windowsInfo = @()

if (-not $uiaLoaded) {
    $verdict = 'INCONCLUSIVE'
    $evidenceNote = "无法加载 .NET UIA 程序集：$uiaLoadError。请改用 Windows PowerShell 5.1（powershell.exe）运行；PowerShell 7 (.NET Core) 下 System.Windows.Automation 的装载方式不同，可能失败。"
} else {
    # --- 1. 定位企微顶层窗口 ---
    $ae = [System.Windows.Automation.AutomationElement]
    $root = $ae::RootElement
    $procs = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match $WeComProcessName })

    if ($procs.Count -eq 0) {
        $verdict = 'FAIL'
        $evidenceNote = "未找到企微进程（进程名正则：$WeComProcessName）。请确认企业微信 PC 客户端已人工登录并处于运行状态。"
    } else {
        $allWindows = New-Object System.Collections.ArrayList
        foreach ($p in $procs) {
            try {
                $cond = New-Object System.Windows.Automation.PropertyCondition($ae::ProcessIdProperty, $p.Id)
                $wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)
                foreach ($w in $wins) {
                    $null = $allWindows.Add($w)
                }
            } catch {}
        }

        Write-Host ("[窗口] 企微进程数={0} | 顶层窗口数={1}" -f $procs.Count, $allWindows.Count)

        if ($allWindows.Count -eq 0) {
            $verdict = 'INCONCLUSIVE'
            $evidenceNote = '找到企微进程但未枚举到顶层窗口（可能被最小化到托盘、或无 UIA 可达窗口）。请将企微主窗口显示在桌面后重跑。'
        } else {
            # --- 2. 遍历所有窗口的 UIA 树 ---
            $allNodes = New-Object System.Collections.ArrayList
            $globalMaxDepth = 0
            $totalNodes = 0
            $anyTruncated = $false
            $sumElapsed = 0

            foreach ($w in $allWindows) {
                $res = Get-UiaFlatTree $w $MaxDepth $MaxNodes $TimeBudgetSec
                foreach ($n in $res.nodes) { $null = $allNodes.Add($n) }
                if ($res.treeDepth -gt $globalMaxDepth) { $globalMaxDepth = $res.treeDepth }
                $totalNodes += $res.nodeCount
                if ($res.truncated) { $anyTruncated = $true }
                $sumElapsed += $res.elapsedMs
                $wi = Get-NodeInfo $w 0
                $windowsInfo += [ordered]@{
                    name         = ("WINDOW_{0}" -f ($windowsInfo.Count + 1))   # 窗口标题可能含个人/群名，占位
                    nameLen      = $wi.nameLen
                    controlType  = $wi.controlType
                    className    = $wi.className
                    automationId = $wi.automationId
                    nodeCount    = $res.nodeCount
                    treeDepth    = $res.treeDepth
                }
            }

            $treeDepth = $globalMaxDepth
            $nodeCount = $totalNodes

            # 控件类型分布（用于诊断）
            $grouped = $allNodes | Group-Object { $_.info.controlType } | Sort-Object Count -Descending
            foreach ($g in $grouped) { $controlTypeCounts[$g.Name] = $g.Count }

            # 最深层级节点采样（优雅降级时的诊断依据）
            $deepestSamples = @($allNodes | Where-Object { $_.info.depth -eq $globalMaxDepth } |
                Select-Object -First $NodeSampleLimit | ForEach-Object {
                    [ordered]@{
                        controlType  = $_.info.controlType
                        className    = $_.info.className
                        automationId = $_.info.automationId
                        nameLen      = $_.info.nameLen
                    }
                })

            # --- 3. 会话列表定位（List/ListItem 或 TreeItem 聚集）---
            $listItems = @($allNodes | Where-Object { $_.info.controlType -in @('ListItem', 'TreeItem') -and $_.info.nameLen -gt 0 })
            if ($listItems.Count -ge 2) {
                $sessionListFound = $true
                $sessionAlias = @{}
                $n = 0
                foreach ($it in $listItems) {
                    $n++
                    $alias = "GROUP_$n"
                    if ($it.info.name) { $sessionAlias[$it.info.name] = $alias }
                    $sessionItems += [ordered]@{
                        name         = $alias
                        nameLen      = $it.info.nameLen
                        controlType  = $it.info.controlType
                        automationId = $it.info.automationId
                        className    = $it.info.className
                    }
                    if ($sessionItems.Count -ge 30) { break }
                }
            }

            # --- 4. 消息区定位（含 Document 宿主下 Text 节点）---
            $textNodes = @($allNodes | Where-Object { $_.info.controlType -in @('Text', 'Edit') -and $_.info.nameLen -gt 0 })
            $inputBox = @($allNodes | Where-Object { $_.info.controlType -eq 'Edit' } | Select-Object -First 1)
            $inputBoxFound = ($inputBox.Count -gt 0)

            # 排除会话列表条目本身的文本，其余 Text 视为消息候选
            $sessionNames = @($listItems | ForEach-Object { $_.info.name })
            $msgTextNodes = @($textNodes | Where-Object { $sessionNames -notcontains $_.info.name } |
                Where-Object { $_.info.controlType -eq 'Text' })

            $memberAlias = @{}
            $m = 0
            if ($msgTextNodes.Count -gt 0) {
                $messageAreaFound = $true
                # 取最靠后（通常为最新消息）的若干条作为样本
                $picked = @($msgTextNodes | Select-Object -Last 10)
                foreach ($t in $picked) {
                    $rawText = $t.info.name
                    # 发送者启发式：同级 ListItem 的首个 Text 视为发送者（标注为启发式，可能不成立）
                    $sender = ''
                    $aliasHit = $null
                    foreach ($k in $memberAlias.Keys) { if ($rawText -like "*$k*") { $aliasHit = $memberAlias[$k]; break } }
                    if (-not $aliasHit) {
                        $m++
                        $aliasHit = "MEMBER_$m"
                        # 仅当文本很短（像昵称）时才登记为发送者别名，避免把正文误当昵称
                        if ($rawText.Length -le 12) { $memberAlias[$rawText] = $aliasHit; $sender = $aliasHit } else { $sender = ''; $m-- }
                    } else {
                        $sender = $aliasHit
                    }

                    $safeText = $rawText
                    foreach ($k in $memberAlias.Keys) { $safeText = $safeText.Replace($k, $memberAlias[$k]) }
                    foreach ($k in $sessionNames) { $safeText = $safeText.Replace($k, 'GROUP_x') }
                    if ($RedactMode -eq 'full') {
                        $safeText = ("<TEXT len={0}>" -f $rawText.Length)
                    } else {
                        $safeText = Protect-Text $safeText 60
                    }

                    $messageSamples += [ordered]@{
                        text         = $safeText
                        textLen      = $rawText.Length
                        sender       = $sender
                        time         = ''            # UIA 不保证暴露独立时间字段；如需时间请由正文启发式提取
                        controlType  = $t.info.controlType
                        automationId = $t.info.automationId
                    }
                }
            }

            # --- 5. 定位规则固化（两次冷启动比对的关键字段）---
            $msgHostCls = if ($msgTextNodes.Count -gt 0) { $msgTextNodes[0].info.className } else { '' }
            $listHostCls = if ($listItems.Count -gt 0) { $listItems[0].info.className } else { '' }
            $editAid = if ($inputBoxFound) { $inputBox[0].info.automationId } else { '' }
            $locatorRuleDesc = ("sessionList=ControlType:{0}/ClassName:{1}(count={2}); messageText=ControlType:Text/ClassName:{3}(count={4}); inputBox=ControlType:Edit/AutomationId:{5}; treeDepth={6}; nodeCount={7}" -f `
                $(if ($listItems.Count -gt 0) { $listItems[0].info.controlType } else { 'NA' }), `
                $listHostCls, $listItems.Count, $msgHostCls, $msgTextNodes.Count, $editAid, $treeDepth, $nodeCount)

            # --- 6. 判定（照抄 ADR 第 7 章 POC-02 判据）---
            if ($messageAreaFound -and $sessionListFound) {
                $verdict = 'PASS'
                $evidenceNote = '同时取到消息文本与会话名，R2（UI Automation 读取）具备可行性。按 ADR 通过判据还需「两次冷启动后定位规则仍成立」——请在第 2 次运行后比对两份 results 的 locatorRuleDesc / treeDepth / nodeCount。已占位：会话名=GROUP_n、发送者=MEMBER_n、文本≤60 字符且手机号等已脱敏。'
            } elseif ($messageAreaFound -and -not $sessionListFound) {
                $verdict = 'INCONCLUSIVE'
                $evidenceNote = '取到消息文本但未识别到会话列表控件（会话列表可能为虚拟列表或控件类型非 ListItem/TreeItem）。请结合 controlTypeCounts 与 deepestSamples 判断，必要时人工调整定位规则。'
            } elseif (-not $messageAreaFound -and $sessionListFound) {
                $verdict = 'INCONCLUSIVE'
                $evidenceNote = '只取到会话列表，未取到消息区文本。这是 R2 的典型降级形态（只能取到窗口壳）。可尝试：① 在企微中打开一个有历史消息的群并置前；② 以 --force-renderer-accessibility 参数重启企微后重跑（不改安装目录）；③ 检查是否被虚拟列表只渲染可视区影响。'
            } else {
                $verdict = 'FAIL'
                $evidenceNote = '既未取到会话列表也未取到消息文本，仅枚举到窗口壳。R2 不成立。请用 controlTypeCounts / deepestSamples 作为诊断证据回填，并转 ADR 第 21 章 Q7 决策。'
            }
            if ($anyTruncated) {
                $evidenceNote += " （注意：本次遍历触达 MaxDepth=$MaxDepth / MaxNodes=$MaxNodes / 时间预算=$TimeBudgetSec s 之一，结果可能不完整，可用更大上限重跑。）"
            }
        }
    }
}

# ==================== 输出 ====================
$result = [ordered]@{
    poc                = 'POC-02'
    title              = 'UIA 读群消息（零安装 PowerShell + .NET UIA 主方案）'
    runIndex           = $RunIndex
    generatedAt        = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    env                = [ordered]@{
        hostOs             = [System.Environment]::OSVersion.VersionString
        powershellVersion  = $PSVersionTable.PSVersion.ToString()
        powershellEdition  = $(if ($PSVersionTable.PSEdition) { $PSVersionTable.PSEdition } else { 'Desktop' })
        dotnetUiaAvailable = $uiaLoaded
        uiaLoadError       = $uiaLoadError
        pythonFound        = $pyInfo.found
        pythonVersion      = $pyInfo.version
        redactMode         = $RedactMode
    }
    wecomProcessPattern = $WeComProcessName
    windows            = $windowsInfo
    sessionListFound   = $sessionListFound
    sessionItems       = $sessionItems
    messageAreaFound   = $messageAreaFound
    messageSamples     = $messageSamples
    inputBoxFound      = $inputBoxFound
    treeDepth          = $treeDepth
    nodeCount          = $nodeCount
    controlTypeCounts  = $controlTypeCounts
    deepestSamples     = $deepestSamples
    locatorRuleDesc    = $locatorRuleDesc
    verdict            = $verdict
    evidenceNote       = $evidenceNote
    redactionNote      = '会话名=GROUP_n、发送者=MEMBER_n、消息文本≤60 字符；手机号仅留后 4 位，邮箱/身份证占位。结果文件将进 Git，故不落任何真实群名与姓名。'
}

$json = $result | ConvertTo-Json -Depth 12
Write-Utf8NoBom $outFile $json

Write-Host ""
Write-Host "==== 结论（第 $RunIndex 次）====" -ForegroundColor Cyan
Write-Host ("verdict = {0}" -f $verdict) -ForegroundColor $(if ($verdict -eq 'PASS') { 'Green' } elseif ($verdict -eq 'FAIL') { 'Red' } else { 'Yellow' })
Write-Host ("sessionListFound={0} | sessionItems={1} | messageAreaFound={2} | messageSamples={3} | inputBoxFound={4}" -f $sessionListFound, $sessionItems.Count, $messageAreaFound, $messageSamples.Count, $inputBoxFound)
Write-Host ("treeDepth={0} | nodeCount={1}" -f $treeDepth, $nodeCount)
Write-Host ("说明：{0}" -f $evidenceNote)
Write-Host ("结果已写入：{0}" -f $outFile)
Write-Host ""
Write-Host "下一步：关闭企业微信 → 重新打开（冷启动）→ 以 -RunIndex 2 再跑一次 → 比对两份 locatorRuleDesc。" -ForegroundColor Yellow
