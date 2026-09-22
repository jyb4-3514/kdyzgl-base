#Requires -Version 5.1
<#
    POC-05  群机器人「消息推送」限频与超长行为实测 —— PowerShell 备选方案

    说明：主方案是同目录的 POC-05-限频与超长.py（Python 标准库 urllib，零第三方依赖）。
          本 PowerShell 版本用于「目标机未装 Python」时的等价执行，行为与主方案一致。

    依据：hrm-dev/docs/wecom-integration-adr.md 第 7 章 POC-05、第 14.1 章 W-C5、第 19 章 V33/V35
    官方依据（已核实）：
      - 帮助中心 docid=14931：每个「消息推送」每分钟不超过 20 条
      - 开发文档 path/91770：text ≤2048 字节、markdown ≤4096 字节

    安全护栏（硬性，与 Python 版一致）：
      1. webhook 只从环境变量 WECOM_POC_WEBHOOK 读取；输出中只出现脱敏形式 ...?key=****
      2. 发送总条数硬上限 30；每条间隔不低于 3 秒
      3. 支持立即停止：输出目录放 POC-05-STOP.flag，或按 Ctrl+C（已用 try/finally 保证部分结果落盘）
      4. 必须显式传入 -Execute 才会真正发送
      5. 仅允许在专用内部测试群执行

    用法：
      $env:WECOM_POC_WEBHOOK = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<你的key>"
      # 可选（V35 交叉验证，需第二个不同的机器人）：
      $env:WECOM_POC_WEBHOOK_2 = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<第二个key>"

      powershell -ExecutionPolicy Bypass -File "POC-05-限频与超长.ps1"             # 仅自检，不发消息
      powershell -ExecutionPolicy Bypass -File "POC-05-限频与超长.ps1" -Execute     # 真正执行
#>
[CmdletBinding()]
param(
    [string]$OutputDir = $PSScriptRoot,
    [switch]$Execute,
    [double]$IntervalSec = 3.0,
    [int]$ProbeCount = 21
)

$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ==================== 护栏常量 ====================
$MAX_TOTAL_SENDS       = 30
$MIN_INTERVAL_SEC      = 3.0
$MARKDOWN_BYTES_TARGET = 4200
$STOP_FLAG_NAME        = 'POC-05-STOP.flag'
$ENV_WEBHOOK           = 'WECOM_POC_WEBHOOK'
$ENV_WEBHOOK_2         = 'WECOM_POC_WEBHOOK_2'
$WEBHOOK_SEND_PATH     = '/cgi-bin/webhook/send'

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Mask-Webhook([string]$Url) {
    if ([string]::IsNullOrEmpty($Url)) { return '' }
    return [regex]::Replace($Url, '(?i)(key=)[^&\s]+', '$1****')
}

function Get-ErrLabel([int]$Errcode) {
    switch ($Errcode) {
        0 { return '成功' }
        40058 { return 'markdown 内容超长（实测返回 40058）' }
        default { return '其他错误（如实记录，不预设含义）' }
    }
}

function Send-JsonPost([string]$Url, $Payload, [int]$TimeoutSec = 10) {
    $result = [ordered]@{ httpStatus = $null; body = $null; error = $null }
    try {
        $json = $Payload | ConvertTo-Json -Depth 6 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method = 'POST'
        $req.Proxy = $null
        $req.ContentType = 'application/json; charset=utf-8'
        $req.Timeout = $TimeoutSec * 1000
        $req.ReadWriteTimeout = $TimeoutSec * 1000
        $req.ContentLength = $bytes.Length
        $s = $req.GetRequestStream()
        $s.Write($bytes, 0, $bytes.Length)
        $s.Close()
        $resp = $req.GetResponse()
        $result.httpStatus = [int]$resp.StatusCode
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $result.body = $sr.ReadToEnd(); $sr.Close(); $resp.Close()
    } catch [System.Net.WebException] {
        $ex = $_.Exception
        if ($ex.Response) {
            $result.httpStatus = [int]$ex.Response.StatusCode
            try {
                $sr = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
                $result.body = $sr.ReadToEnd(); $sr.Close()
            } catch {}
        }
        $result.error = $ex.Message
    } catch {
        $result.error = $_.Exception.Message
    }
    return $result
}

# ==================== 主流程 ====================
$outFile  = Join-Path $OutputDir 'POC-05-结果.json'
$stopFlag = Join-Path $OutputDir $STOP_FLAG_NAME

$interval = [Math]::Max([double]$IntervalSec, $MIN_INTERVAL_SEC)
if ([double]$IntervalSec -lt $MIN_INTERVAL_SEC) {
    Write-Host ("[护栏] 请求间隔 {0}s 低于硬下限，已强制提升为 {1}s" -f $IntervalSec, $MIN_INTERVAL_SEC) -ForegroundColor Yellow
}

$probeCount = [int]$ProbeCount
if ((2 + $probeCount) -gt $MAX_TOTAL_SENDS) {
    $probeCount = $MAX_TOTAL_SENDS - 2
    Write-Host ("[护栏] 计划条数超上限，限频探测条数收敛为 {0}（总计 {1}）" -f $probeCount, (2 + $probeCount)) -ForegroundColor Yellow
}

$hook1 = [string]$env:WECOM_POC_WEBHOOK
$hook2 = [string]$env:WECOM_POC_WEBHOOK_2

$result = [ordered]@{
    poc = 'POC-05'
    title = '群机器人限频与超长行为实测（PowerShell 备选）'
    generatedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    env = [ordered]@{
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        hostOs = [System.Environment]::OSVersion.VersionString
        intervalSec = $interval
        maxTotalSends = $MAX_TOTAL_SENDS
        plannedSends = (2 + $probeCount)
        markdownBytesTarget = $MARKDOWN_BYTES_TARGET
        webhook1Masked = (Mask-Webhook $hook1)
        webhook2Masked = $(if ($hook2) { Mask-Webhook $hook2 } else { $null })
        secondWebhookProvided = [bool]$hook2
    }
    executed = $false
    baseline = $null
    tooLongCase = $null
    rateLimitProbe = @()
    crossRobotProbe = @()
    observedLimitPerMinute = $null
    countDimensionVerdict = 'INCONCLUSIVE_SINGLE_WEBHOOK'
    verdict = 'INCONCLUSIVE'
    evidenceNote = ''
    redactionNote = 'webhook 仅以 ...key=**** 形式出现在结果中；未落任何完整 webhook、群名、成员名。'
}

Write-Host "==== POC-05 群机器人限频与超长实测（PowerShell）====" -ForegroundColor Cyan
Write-Host ("[环境] PowerShell {0} | 间隔 {1}s | 计划发送 {2} 条（上限 {3}）" -f $PSVersionTable.PSVersion.ToString(), $interval, (2 + $probeCount), $MAX_TOTAL_SENDS)
Write-Host ("[目标] webhook1 = {0}" -f (Mask-Webhook $hook1))
if ($hook2) { Write-Host ("[目标] webhook2 = {0}（V35 交叉验证）" -f (Mask-Webhook $hook2)) }
Write-Host ("[停止] 如需立即中止：在输出目录创建 {0}，或按 Ctrl+C" -f $STOP_FLAG_NAME)

if ([string]::IsNullOrWhiteSpace($hook1)) {
    $result.verdict = 'FAIL'
    $result.evidenceNote = "环境变量 $ENV_WEBHOOK 缺失。请先在当前窗口设置该变量，再重跑；脚本不会回退到任何内置默认值。"
    Write-Utf8NoBom $outFile ($result | ConvertTo-Json -Depth 10)
    Write-Host "[失败] 未找到环境变量 $ENV_WEBHOOK，已写出诊断结果：$outFile" -ForegroundColor Red
    exit 2
}
if (-not ($hook1.StartsWith('https://') -and $hook1.Contains($WEBHOOK_SEND_PATH))) {
    $result.verdict = 'FAIL'
    $result.evidenceNote = "环境变量 $ENV_WEBHOOK 的取值不像企业微信 webhook 地址（应含 $WEBHOOK_SEND_PATH?key=...）。请核对后重试。"
    Write-Utf8NoBom $outFile ($result | ConvertTo-Json -Depth 10)
    Write-Host "[失败] webhook 格式不符合预期，已写出诊断结果：$outFile" -ForegroundColor Red
    exit 2
}

if (-not $Execute) {
    $result.verdict = 'INCONCLUSIVE'
    $result.evidenceNote = '环境自检通过，但未传入 -Execute，未发送任何消息。确认无误后加 -Execute 执行。'
    Write-Utf8NoBom $outFile ($result | ConvertTo-Json -Depth 10)
    Write-Host "[自检] 未发送消息（缺少 -Execute）。已写出自检结果：$outFile" -ForegroundColor Yellow
    exit 3
}

$records = @()
$seq = 1

function Send-One([string]$Url, $Payload, [int]$SeqNo, [string]$Kind) {
    $ts = (Get-Date).ToString('HH:mm:ss.fff')
    $r = Send-JsonPost $Url $Payload
    $errcode = $null; $errmsg = ''
    if ($r.body) {
        try {
            $o = $r.body | ConvertFrom-Json
            if ($null -ne $o.errcode) { $errcode = [int]$o.errcode }
            if ($o.errmsg) { $errmsg = ([string]$o.errmsg) }
            if ($errmsg.Length -gt 120) { $errmsg = $errmsg.Substring(0, 120) }
        } catch {}
    }
    $rec = [ordered]@{
        seq = $SeqNo
        kind = $Kind
        timestamp = $ts
        httpStatus = $r.httpStatus
        errcode = $errcode
        errmsg = $errmsg
        errmsgLabel = $(if ($null -ne $errcode) { Get-ErrLabel $errcode } else { '无有效响应' })
        responseBodyLen = $(if ($r.body) { $r.body.Length } else { 0 })
        error = $r.error
    }
    return $rec
}

try {
    # ① 基线 text
    Write-Host "`n[1/4] 基线 text ..."
    $rec = Send-One $hook1 @{ msgtype = 'text'; text = @{ content = 'POC-05 基线测试（内部测试群，请忽略）' } } $seq 'baseline-text'
    $seq++
    Write-Host ("  [{0}] #{1} http={2} errcode={3} {4}" -f $rec.timestamp, $rec.seq, $rec.httpStatus, $rec.errcode, $rec.errmsgLabel)
    $result.baseline = [ordered]@{ httpStatus = $rec.httpStatus; errcode = $rec.errcode; errmsg = $rec.errmsg }
    Start-Sleep -Seconds $interval

    # ② 超长 markdown
    Write-Host ("`n[2/4] 超长 markdown（构造 {0} 字节）..." -f $MARKDOWN_BYTES_TARGET)
    $content = "# POC-05 超长测试`n" + ('A' * $MARKDOWN_BYTES_TARGET)
    $constructed = [System.Text.Encoding]::UTF8.GetByteCount($content)
    $rec = Send-One $hook1 @{ msgtype = 'markdown'; markdown = @{ content = $content } } $seq 'too-long-markdown'
    $seq++
    Write-Host ("  [{0}] #{1} http={2} errcode={3} {4}" -f $rec.timestamp, $rec.seq, $rec.httpStatus, $rec.errcode, $rec.errmsgLabel)
    $result.tooLongCase = [ordered]@{
        constructedBytes = $constructed
        httpStatus = $rec.httpStatus
        errcode = $rec.errcode
        errmsg = $rec.errmsg
    }
    Start-Sleep -Seconds $interval

    # ③ 限频边界探测
    Write-Host ("`n[3/4] 限频边界探测（{0} 条 × {1}s）..." -f $probeCount, $interval)
    $probeList = @()
    for ($i = 0; $i -lt $probeCount; $i++) {
        if (Test-Path $stopFlag) { Write-Host '  [停止] 检测到停止标志文件，提前结束限频探测。' -ForegroundColor Yellow; break }
        $rec = Send-One $hook1 @{ msgtype = 'text'; text = @{ content = ("POC-05 限频探测 #{0}" -f ($i + 1)) } } $seq 'rate-probe'
        $seq++
        Write-Host ("  [{0}] #{1} http={2} errcode={3} {4}" -f $rec.timestamp, $rec.seq, $rec.httpStatus, $rec.errcode, $rec.errmsgLabel)
        $probeList += [ordered]@{
            seq = $rec.seq; timestamp = $rec.timestamp; httpStatus = $rec.httpStatus
            errcode = $rec.errcode; errmsg = $rec.errmsg
        }
        if ($i -lt ($probeCount - 1)) { Start-Sleep -Seconds $interval }
    }
    $result.rateLimitProbe = $probeList

    # 观测推断
    $firstFail = @($probeList | Where-Object { $null -ne $_.errcode -and $_.errcode -ne 0 } | Select-Object -First 1)
    if ($firstFail.Count -eq 0) {
        $okc = @($probeList | Where-Object { $_.errcode -eq 0 }).Count
        $result.observedLimitPerMinute = ("未触发（{0} 条探测全部成功，间隔 {1}s 时约等于 20 条/分钟，未越界）" -f $okc, $interval)
    } else {
        $before = @($probeList | Where-Object { $_.seq -lt $firstFail[0].seq -and $_.errcode -eq 0 }).Count
        $result.observedLimitPerMinute = ("首次非 0 errcode 出现在第 {0} 条探测（errcode={1}）；此前成功 {2} 条。属实测推断口径，非官方承诺值。" -f $firstFail[0].seq, $firstFail[0].errcode, $before)
    }

    # ④ V35 交叉验证
    if ($hook2) {
        Write-Host "`n[4/4] V35 交叉验证：向 webhook2（另一机器人）发送 2 条 ..."
        $crossList = @(); $crossOk = 0
        for ($j = 0; $j -lt 2; $j++) {
            if (Test-Path $stopFlag) { break }
            $rec = Send-One $hook2 @{ msgtype = 'text'; text = @{ content = ("POC-05 V35 交叉验证 #{0}" -f ($j + 1)) } } (100 + $j) 'cross-robot'
            Write-Host ("  [{0}] #{1} http={2} errcode={3} {4}" -f $rec.timestamp, $rec.seq, $rec.httpStatus, $rec.errcode, $rec.errmsgLabel)
            $crossList += [ordered]@{
                seq = $rec.seq; timestamp = $rec.timestamp; httpStatus = $rec.httpStatus
                errcode = $rec.errcode; errmsg = $rec.errmsg
            }
            if ($rec.errcode -eq 0) { $crossOk++ }
            if ($j -eq 0) { Start-Sleep -Seconds $interval }
        }
        $result.crossRobotProbe = $crossList
        if ($crossOk -eq 2) {
            $result.countDimensionVerdict = 'PER_ROBOT'
            $result.evidenceNote = 'webhook2（另一机器人）在 webhook1 疑似受限后仍可正常发送 → 限频按「机器人」独立计数（V35 支持 PER_ROBOT）。'
        } elseif ($crossOk -eq 0) {
            $result.countDimensionVerdict = 'PER_GROUP'
            $result.evidenceNote = 'webhook2 同样被拒 → 限频不像「按机器人」独立；疑似按「群」或存在其他共享维度（V35 未完全闭环）。'
        } else {
            $result.countDimensionVerdict = 'INCONCLUSIVE'
            $result.evidenceNote = 'webhook2 交叉结果不一致，无法判定计数维度；V35 保持未闭环。'
        }
    } else {
        $result.countDimensionVerdict = 'INCONCLUSIVE_SINGLE_WEBHOOK'
        $result.evidenceNote = '仅提供单个 webhook，无法验证计数维度；按 W-C5 保守口径（同一机器人跨群共享配额）实现，V35 未闭环。'
    }

    $result.executed = $true

    # 汇总判定
    if ($result.baseline -and $result.baseline.errcode -eq 0 -and $result.tooLongCase) {
        $got40058 = ($result.tooLongCase.errcode -eq 40058)
        $tripped = (@($probeList | Where-Object { $null -ne $_.errcode -and $_.errcode -ne 0 }).Count -gt 0)
        if ($got40058 -and $tripped) {
            $result.verdict = 'PASS'
            $result.evidenceNote = ($result.evidenceNote + ' ') + '基线成功、超长返回 40058、限频边界已触发：可用于标定令牌桶参数与内容校验。'
        } elseif ($got40058) {
            $result.verdict = 'INCONCLUSIVE'
            $result.evidenceNote = ($result.evidenceNote + ' ') + '基线成功且超长返回 40058，但 3 秒间隔护栏下未触发限频；未越界即视为「合规未触发」。'
        } elseif ($tripped) {
            $result.verdict = 'INCONCLUSIVE'
            $result.evidenceNote = ($result.evidenceNote + ' ') + '触发限频但超长未返回 40058，需核对实际错误文案后回填 V33。'
        } else {
            $result.verdict = 'INCONCLUSIVE'
            $result.evidenceNote = ($result.evidenceNote + ' ') + '未观察到限频与超长预期行为，需核对 webhook 与测试群后重跑。'
        }
    }
} catch {
    $result.evidenceNote = ($result.evidenceNote + ' ') + ("执行中断：{0}" -f $_.Exception.Message)
} finally {
    # Ctrl+C / 异常时也保证已采集的部分结果落盘
    Write-Utf8NoBom $outFile ($result | ConvertTo-Json -Depth 12)
    Write-Host ("`n结果已写入：{0}" -f $outFile) -ForegroundColor Cyan
}

Write-Host "`n==== 结论 ====" -ForegroundColor Cyan
Write-Host ("verdict = {0}" -f $result.verdict) -ForegroundColor $(if ($result.verdict -eq 'PASS') { 'Green' } elseif ($result.verdict -eq 'FAIL') { 'Red' } else { 'Yellow' })
Write-Host ("countDimensionVerdict = {0}" -f $result.countDimensionVerdict)
Write-Host ("观察值：{0}" -f $result.observedLimitPerMinute)
Write-Host ("说明：{0}" -f $result.evidenceNote)
