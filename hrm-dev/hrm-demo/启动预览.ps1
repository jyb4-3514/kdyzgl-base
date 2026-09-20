# 快递驿站智汇系统 · 三端演示 Demo 本地预览启动器
# 说明：纯前端 Mock 数据，无需后端 / 数据库 / JDK；关闭本窗口即停止服务

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

$URL = 'http://localhost:5188/'
$PORT = 5188

function Write-Line { param([string]$Text, [string]$Color = 'Gray') Write-Host $Text -ForegroundColor $Color }

Write-Line '==========================================================' 'Cyan'
Write-Line '   快递驿站智汇系统 · 三端演示 Demo' 'Cyan'
Write-Line '   纯前端 Mock 数据，无需后端 / 数据库 / JDK' 'Cyan'
Write-Line '==========================================================' 'Cyan'
Write-Host ''

# 端口探测：服务已在运行则直接开浏览器，避免重复启动导致端口冲突
function Test-PortListening {
  param([int]$Port)
  try {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { return $true }
  } catch { }
  # 兜底：netstat 解析（老系统无 Get-NetTCPConnection 时）
  $hits = netstat -ano 2>$null | Select-String ":$Port\s" | Select-String 'LISTENING'
  return [bool]$hits
}

if (Test-PortListening -Port $PORT) {
  Write-Line '[提示] 服务已在运行，正在打开浏览器...' 'Yellow'
  Start-Process $URL
  Start-Sleep -Seconds 2
  exit 0
}

# 首次运行自动安装依赖
if (-not (Test-Path (Join-Path $PSScriptRoot 'node_modules'))) {
  Write-Line '[1/2] 首次运行，正在安装依赖，约需 1-2 分钟...' 'Yellow'
  & npm install
  if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Line '[错误] 依赖安装失败，请确认已安装 Node.js 18 及以上版本' 'Red'
    Write-Host ''
    Read-Host '按回车键退出'
    exit 1
  }
} else {
  Write-Line '[1/2] 依赖已就绪' 'Green'
}

Write-Line '[2/2] 正在启动本地服务，浏览器将在约 6 秒后自动打开...' 'Yellow'
Write-Host ''
Write-Line '  入口页   http://localhost:5188/'
Write-Line '  网页端   http://localhost:5188/pc.html'
Write-Line '  移动端   http://localhost:5188/mobile.html'
Write-Host ''
Write-Line '  演示账号（密码统一为 demo1234）'
Write-Line '    admin         管理员  ->  网页端 / 老板端'
Write-Line '    st001_admin   站长    ->  员工端'
Write-Line '    st001_staff   员工    ->  员工端'
Write-Host ''
Write-Line '  关闭本窗口即停止服务。' 'DarkGray'
Write-Line '----------------------------------------------------------'
Write-Host ''

# 用「系统默认浏览器」打开（ShellExecute 语义），不使用任何内置预览
# 需要指定某个已安装的浏览器时设 HRM_BROWSER，例如强制 Edge：
#   $env:HRM_BROWSER = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$browser = if ($env:HRM_BROWSER -and (Test-Path $env:HRM_BROWSER)) { $env:HRM_BROWSER } else { '' }
$openExpr = if ($browser) { "Start-Process -FilePath '$browser' -ArgumentList '$URL'" } else { "Start-Process '$URL'" }

# 独立进程延时开浏览器：等服务真正起来，避免白屏
Start-Process -FilePath 'powershell' -WindowStyle Hidden -ArgumentList @(
  '-NoProfile', '-Command', "Start-Sleep -Seconds 6; $openExpr"
)

# 前台运行 dev server（关闭窗口即终止）
& npm run dev

Write-Host ''
Write-Line '服务已停止。按回车键关闭窗口。' 'DarkGray'
Read-Host
