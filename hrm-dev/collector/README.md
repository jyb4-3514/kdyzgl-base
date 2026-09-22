# 二期采集端 · Collector（Python 单栈）

快递驿站智汇系统二期「驿站包裹数据采集」的采集端应用。**第一版范围**：登录模块 + 多多账号密码配置模块 + MySQL 登录态持久化。

对应 ADR：`hrm-dev/docs/collector-architecture-adr.md`（重点 §3.3 红线 C1–C5、§18 技术栈与 CDP 补丁、§19 多通道、§20 凭据托管与自动登录）；对应规划：`hrm-dev/docs/collector-project-plan.md`（P1 取数 PoC）。

---

## 1. 环境要求

| 项 | 要求 | 说明 |
| --- | --- | --- |
| 操作系统 | Windows 10/11 | DPAPI 加密与 pywin32 仅 Windows 可用 |
| Python | >= 3.11（采集机计划 3.13.15） | 配置读取依赖标准库 `tomllib`（3.11+） |
| MySQL | 8.0（本地服务，仅监听 127.0.0.1） | 库名 `yizhan_collector`，应用账号 `yizhan@127.0.0.1` |
| 浏览器 | Playwright 自带 Chromium | `python -m playwright install chromium` |

先执行环境脚本（幂等）：`hrm-dev/collector/scripts/env-setup.ps1` —— 安装 Python / MySQL、建库建账号、生成 DPAPI 加密的本地密钥文件。

## 2. 安装

在 `hrm-dev/collector` 目录下执行：

```powershell
python -m pip install -r requirements.txt
python -m playwright install chromium
Copy-Item config\settings.example.toml config\settings.toml    # 再按采集机实际情况修改
```

依赖说明见 `requirements.txt` 内注释（含 License 与用途）。**注意**：MySQL 8 默认认证插件为 `caching_sha2_password`，因此必须安装 `PyMySQL[rsa]`（附带 `cryptography`），否则首次连接会报认证插件错误。

命令执行方式（包位于 `src/`，需把 `src` 加入模块搜索路径）：

```powershell
$env:PYTHONPATH = "src"
python -m collector --help
```

## 3. 首次凭据初始化

**顺序不能颠倒**：先 `env-setup.ps1`（创建密钥文件 + 写入 `mysql` 段），后 `init-local-secrets.ps1`（只增改 `pdd` 段）。

```powershell
# ① 环境与 MySQL 凭据（由环境脚本负责）
powershell -ExecutionPolicy Bypass -File scripts\env-setup.ps1

# ② 多多账号密码（加密写入 pdd 段；密钥文件默认按脚本位置推导）
powershell -ExecutionPolicy Bypass -File scripts\init-local-secrets.ps1 `
    -Site 'https://mdkd.pinduoduo.com/login' `
    -Account '<多多账号，建议由站长本人提供>' `
    -PasswordSecure (Read-Host -AsSecureString '请输入多多账号密码') `
    -AuthorizedBy '<授权人姓名>' `
    -AuthorizationNote '<授权台账编号/备注>'

# 站长改密后覆盖（凭据轮换）
... -Force
```

脚本行为：

- 幂等：已有 `pdd` 段且未加 `-Force` 时**跳过并提示**，不覆盖；
- 明文零落地：只在内存加密，不写临时文件、不打印密码，日志只打印脱敏账号；
- 文件以 **UTF-8 带 BOM** 写入（PowerShell 5.1 中文系统下无 BOM 会乱码）；
- `-AuthorizedBy` 必填：ADR §20.2.9 要求先归档站长书面授权。

密钥文件：`config/local/local.secrets.json`（已被 `hrm-dev/collector/.gitignore` 的 `config/local/*` 排除，**严禁入库**）。

## 4. 「多多采集账号密码配置」模块（本版核心交付）

**落点**：`src/collector/config/pdd_account.py`

**职责**：从 DPAPI 本地密钥文件加载并校验多多账号密码 → 暴露不可变值对象 `PddAccount`。

| 成员 | 用途 |
| --- | --- |
| `site` / `account` / `password` / `authorized_by` / `authorized_at` | 值对象字段；`password` 标记 `repr=False` |
| `masked_account()` | 脱敏账号，如 `166****9983`（日志、数据库展示列使用） |
| `account_hash()` | 账号 SHA-256，数据库去重/幂等键（**不是**保密手段） |
| `has_authorization()` | 是否已登记站长书面授权 |
| `masked_description()` | 供日志的一行摘要，绝不含密码 |
| `load_pdd_account(secrets_file=None)` | 加载 + 校验；缺失 `pdd` 段或解密失败即抛 `PddAccountError` 并给可执行指引 |

用法示例：

```python
from collector.config.pdd_account import load_pdd_account

account = load_pdd_account()                 # 默认读 config/local/local.secrets.json
print(account.masked_description())          # site=... account=166****9983 authorizedBy=张三
# account.password 仅在本进程内即时使用；__repr__ / __str__ / 日志一律显示 ***
```

安全约定：任何日志、异常、`__repr__`/`__str__` 只出现脱敏账号；**绝不静默使用默认账号密码**。底层实现见 `src/collector/security/dpapi.py`（DPAPI CurrentUser 作用域）。

## 5. 人工介入登录（首次接入 / 登录态失效后的标准操作）

> **为什么需要它**：目标站登录页会弹滑块 / 验证码等挑战，采集端**不识别、不拖动、不破解**（红线 C2/C8），检出即停。
> 但自动路径（`--run`）停下的同时会把浏览器一起关掉，现场无从在窗口中完成验证——**提示文案与真实行为不符**（已复现的流程缺陷）。
> 因此**人工完成挑战必须走 `login --manual`**：它把窗口保持打开交给现场人工，程序只做**只读**轮询。
> `--manual` 与 `--run` **互不调用**，也**不是**默认行为。

**命令**：

```powershell
$env:PYTHONPATH = "src"
python -m collector login --manual                # 默认预填账号密码：人工只需完成挑战并点「登录」
python -m collector login --manual --no-prefill   # 纯手工：连账号密码也由人工填
```

**参数**：

| 参数 | 说明 |
| --- | --- |
| `--manual` | 人工介入登录模式（与 `--check` / `--run` 三选一，互斥） |
| `--no-prefill` | 仅用于 `--manual`：不预填账号密码，由人工全部手填（与 `--check`/`--run` 同用会被判为参数错误） |
| `--settings` / `--secrets` | 公共参数，同其它子命令 |

`[login]` 段配置项（见 `config/settings.example.toml`）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `manual_wait_minutes` | `10` | 等待现场人工完成挑战的最长分钟数，超时按退出码 2 结束 |
| `manual_poll_interval_s` | `5` | 只读轮询间隔（秒） |

**要求有头**：若 `browser.headless = true`，程序会直接以**明确错误**退出并提示改为 `false`（不会静默替你改配置）——人工看不到无头窗口，本模式无意义。

**预期输出（节选）**：

```text
[人工介入登录] 浏览器已打开，请勿关闭窗口（关闭即中止本轮）。
请在浏览器窗口中依次完成：
  1) 若当前停在「短信登录」，先点「密码登录」页签；
  2) 完成页面上的滑块 / 验证码等挑战；
  3) 点击「登录」按钮。
账号与密码已自动预填（程序不会自动提交），你只需完成挑战并点「登录」。
程序每 5 秒只读检测一次登录态（不点击、不输入，不会干扰你的操作），最多等待 10 分钟。
登录成功后程序会自动保存会话并写库，无需任何额外操作。
...
结论：LOGIN_SUCCEEDED（数据库登录态=SUCCESS）
cookie 条数：12（仅计数，不输出内容）
```

**结束分支与退出码**：

| 分支 | 退出码 | 提示要点 |
| --- | --- | --- |
| 登录成功 | `0` | 导出 `storage_state`、`pdd_login_session` 置 `SUCCESS`，打印脱敏摘要（cookie 计数 / 落盘路径 / 预计失效时间） |
| 凭据错误 | `1` | 「账号或密码错误」不重试，提示站长重跑 `init-local-secrets.ps1 -Force` 更新凭据 |
| 等满未成功（超时） | `2` | 「未在 N 分钟内检测到登录成功」+ 排查建议；落库 `MANUAL_REQUIRED` |
| 人工关闭了窗口 | `2` | 「浏览器窗口已被关闭，本轮人工登录已中止」——**明确说明不是「未检测到登录」** |

**失败排查**：

| 现象 | 处理 |
| --- | --- |
| 提示 `browser.headless = true` | 把 `config/settings.toml` 的 `[browser] headless` 改为 `false`（或设 `YIZHAN__BROWSER__HEADLESS=false`）后重跑。 |
| 提示「账号与密码未预填（预填失败：…）」 | 预填**不致命**：按提示在窗口中手动填写账号密码后继续完成挑战即可；并按提示里的原因检查 `selectors.py`（多为登录页改版）。以 `--no-prefill` 启动时该提示属**预期**。 |
| 提示「未在 N 分钟内检测到登录成功」 | 确认是否已点「登录」、账号密码是否正确、窗口是否仍在登录页或已跳转工作台。仍无解时看 §9.2：`WORKBENCH_MARKERS` 尚未在采集机实测，成功后判定可能未命中，需先导出工作台 DOM 校准 `selectors.py`。 |
| 提示「浏览器窗口已被关闭」 | 窗口被关导致本轮中止（**不是**超时），重跑 `login --manual` 即可。 |
| 中途想放弃 | 直接关掉浏览器窗口即可，程序会识别并以退出码 2 结束，不会误报成超时。 |

> 期间程序**只读**：只读取页面地址、可见性与正文，不点击、不输入、不滚动，不会与你的操作打架。
> 挑战仍由人工完成，程序**不做**任何识别 / 模拟拖动 / 破解（红线不变）。

## 6. CLI 用法

| 命令 | 说明 | 退出码 |
| --- | --- | --- |
| `python -m collector check-env` | 自检：Python 版本、依赖、配置文件、本地凭据、MySQL 连通性 | 0 通过 / 1 有失败项 |
| `python -m collector init-db` | 按 `src/collector/db/schema.sql` 幂等建表 | 0 / 1 |
| `python -m collector login --check` | 只探测当前登录态，不做登录动作 | 0 已登录 / 1 未登录或失败 / 2 需人工介入 |
| `python -m collector login --run` | 执行自动登录（已登录则跳过）；检出挑战即**停止自动化**并指向 `--manual` | 0 成功 / 1 失败 / 2 需人工介入 |
| `python -m collector login --manual` | **人工介入登录**：有头浏览器交给现场人工，程序只读轮询登录态（见 §5） | 0 成功 / 1 凭据错误或失败 / 2 超时或窗口被关 |
| `python -m collector session list --limit 20` | 列出最近的登录会话记录（脱敏） | 0 / 1 |

公共参数：`--settings <路径>`、`--secrets <路径>`。

## 7. MySQL 表结构要点

建表脚本：`src/collector/db/schema.sql`（仅 DDL）。时间列一律 **UTC**（连接建立时执行 `SET time_zone='+00:00'`）。

**`pdd_login_session`（登录会话 / 登录态持久化）**：一账号一站点一行，幂等 upsert。

| 列 | 说明 |
| --- | --- |
| `account_masked` / `account_hash` | 脱敏账号 / 账号哈希（去重键） |
| `site` | 登录站点 URL（可配，入口与工作台可能不同域名） |
| `login_status` | `PENDING` / `SUCCESS` / `FAILED` / `MANUAL_REQUIRED` / `EXPIRED`（VARCHAR + 应用层枚举，避免 MySQL ENUM 增值得改表） |
| `login_at` / `expire_at` | 登录成功时间 / 预计失效时间（`expire_at` 按 `session_ttl_hours` 估算，**未实测**） |
| `storage_state_ref` | Playwright `storage_state` **落盘路径**（只存路径，不存内容；不存 Cookie 明文） |
| `cookie_count` | 登录态 cookie 条数（仅计数，不留值） |
| `last_check_at` / `fail_reason` | 最近探测时间 / 失败或人工介入原因（已脱敏） |
| `create_time` / `update_time` | UTC 时间戳 |

唯一键 `uk_pdd_login_session_account_site(account_hash, site)` 同时承担 `account_hash` 查询（前缀即哈希），故不另建冗余索引；另有 `idx_pdd_login_session_login_status`。

**`pdd_collect_cursor`（采集游标，为 P1-03 断点续采打地基）**：`station_code` / `task_key` / `cursor_value` / `last_run_at`，唯一键 `uk_pdd_collect_cursor_station_task(station_code, task_key)`。

## 8. 登录模块设计要点

| 主题 | 决策 |
| --- | --- |
| 浏览器 | Playwright `launch_persistent_context`，`user_data_dir = runtime/browser-profile`（被 .gitignore 排除）；多账号 = 每账号独立 profile（ADR §18.3 补丁 3） |
| 登录态载体 | **profile 为权威载体**（ADR §20.2.5）；`storage_state` 仅作诊断导出，落 `runtime/storage-state/<account_hash>.json` |
| 登录结果判定 | **不只看 URL**：① 域名白名单（允许白名单域子域）② 工作台特征元素（**未实测**：`DIV[role=menuitem]` / 「运单查询」）③ 登录表单是否仍存在；三者组合判定，见 `pdd.judge_login_result` |
| 登录方式切换 | 实测目标站**默认停在「短信登录」**，此时密码框不可见。本项目走密码登录，登录前先点「密码登录」页签；以「密码框可见」为**幂等判据**，已就绪则跳过点击；切换失败给出指向 `dump-login-page.py` 与 `selectors.py` 的可执行报错（不泛化成「登录失败」）|
| 选择器策略 | 全部集中在 `src/collector/login/selectors.py`，**实测定位优先 → 语义定位次之 → 通用 CSS 兜底**；每条带「依据」与 `verified` 标记。2026-09-22 采集机实测已确认 4 类元素（密码登录页签 / `#mobile` / `input.password-input` / `button.login-btn`），并按两种作用域锚点生成 8 条实测候选，其余仍标未核实。同 id（`#mobile`）与同 class（`button.login-btn`）元素**各挂多份**，故交互类 CSS 候选一律带 `:visible` **并收窄到激活面板作用域**：`ACTIVE_PANEL`（class 锚点 `.rocket-tabs-tabpane-active`）与 `ACTIVE_PANEL_ARIA`（aria 锚点 `div[role="tabpanel"][aria-hidden="false"]`）并列、互为兜底。`:visible` 只看包围盒、不看 `aria-hidden`，单独用不足以区分「当前生效面板」；作用域锚点则回答「元素属于哪个面板」，两者叠加使用。非作用域候选仅作最后兜底 |
| 提交动作 | `pdd.submit_with_degradation`：**多路径降级链**，顺序 A 回车提交（密码框 `press('Enter')`，按键不依赖鼠标落点、天然绕开遮挡）→ B 真实点击（`click_with_occlusion_guard`：居中滚动 + `elementFromPoint` 落点校验，通过才 `click(timeout=短)`）→ C 强制点击 `click(force=True)` → D 合成点击 `dispatch_event('click')`。每条路径**短超时**（`login.submit_path_timeout_ms`，默认 4000ms），执行后用**可观测信号**判定是否生效（URL 变化 / 工作台特征 / 挑战 / 凭据错误 / 表单消失），未生效才降级；全部未生效抛 `LoginElementBlockedError` 并点明每条路径与最后报错。**D 为 `isTrusted=false` 的合成 DOM 事件**（非真实鼠标动作，也绕不过页面自身校验与风控），仅最后兜底 |
| 填表自检 | 提交前 `pdd.verify_credentials_filled`：校验账号框与密码框**各自非空**——只读取并比较**字符数**，密码值不返回、不记日志、不进异常（红线）；任一为空即报错并指向 `selectors.py`，绝不带着空框提交 |
| 提交路径预算配置 | `login.submit_path_timeout_ms`（单条路径交互超时，默认 4000）与 `login.submit_effect_probe_ms`（单条路径生效观测预算，默认 3000）：默认「4 条 ×(4000+3000)=28000ms」≈ `login.submit_wait_ms`（30000），全链总预算与之一致，杜绝单条路径吃满 30s |
| 异常收敛 | `run()` 全流程收敛：定位失败 / 超时 / 遮挡 / 浏览器错误 → 落库 `FAILED` 并返回失败结果（退出码 1）；挑战/风控 → 落库 `MANUAL_REQUIRED` 后抛 `ManualInterventionRequired`（退出码 2）。CLI 顶层兜底，**不输出裸 traceback**；堆栈只进 debug 级日志 |
| 仿人工 | 仅限**节奏与停顿**（`src/collector/login/humanize.py`）：逐字符 60–180ms 随机、字段间停顿、提交前停顿；参数全部可配 |
| 人工介入触发（`--run`） | 页面文本命中挑战信号（验证码 / 滑块 / 短信验证 / 风控提示）→ **立即停止自动化**，抛 `ManualInterventionRequired`，登录态置 `MANUAL_REQUIRED`，退出码 2 并打印指引。指引**只**指向 `login --manual`，**不再**声称"在浏览器窗口中手动完成"（检出后浏览器会随上下文关闭，窗口不存在） |
| 人工介入登录（`--manual`） | `PddLoginService.manual`：`browser.headless=true` 时以 `ManualLoginConfigError` 明确报错（不静默改有头）；打开有头持久化上下文 → 可选预填（`--no-prefill` 则完全不触碰页面）→ `on_ready` 回调打印现场指引 → `poll_manual_login` **只读**轮询（复用 `judge_login_result`，不点击/不输入/不滚动）→ 成功导出会话并落库 `SUCCESS`。与 `run` 互不调用、非默认行为 |
| 人工登录结束分支 | 成功 → 退出码 0；凭据错误 → 退出码 1（落库 `FAILED`）；超时 → `ManualLoginTimeoutError` 退出码 2（落库 `MANUAL_REQUIRED`）；人工关窗 → `ManualBrowserClosedError` 退出码 2（落库 `MANUAL_REQUIRED`，**不伪装**成"未检测到登录"）。关窗识别：`page.is_closed()` + 捕获 Playwright `TargetClosedError`（见 `pdd.is_browser_closed_error`） |
| 凭据错误 | 明确「账号或密码错误」→ **不重试**，提示站长更新凭据（`init-local-secrets.ps1 -Force`） |
| 自动登录开关 | `login.auto_login_enabled = false` 时直接回落人工（ADR §20.2.9 生效条件未闭环的站点应关闭）；`--manual` 不受该开关限制 |

### 红线（代码内已收口）

- **不做反检测伪装**：不注入 stealth / 不改 `navigator.webdriver` / 不伪造 UA 指纹 / 不用代理；`browser.validate_extra_args` 会**拒绝启动**含 `--remote-debugging-address`、`--remote-allow-origins`、`--user-agent`、`--proxy-server` 的参数。
- **不逆向签名、不自造请求**：登录提交走页面自身流程，不接触 `anti-content`。
- **验证码不自动识别**：无任何打码/识别代码路径，命中即停。
- 只连 `127.0.0.1`：`db.host` 非本机直接拒绝启动。
- 日志全局脱敏：`RedactionFormatter` 对整条日志（含异常堆栈）替换 `password/pwd/cookie/authorization/anti-content/etag/pdd-id` 及运行期登记的明文值，另有手机号兜底脱敏。

### 选择器校准（目标站改版后如何重新导出）

登录页 DOM 只能在采集机实测，因此**改版后不要靠猜**：先用只读诊断脚本导出真实结构，再回填 `selectors.py`。

```powershell
$env:PYTHONPATH = "src"
# ① 直接导出登录页结构
python scripts/dump-login-page.py --url https://mdkd.pinduoduo.com/login
# ② 先点击页签再导出（短信/密码页签面板同时在 DOM 上，切页签只切显隐）
python scripts/dump-login-page.py --url https://mdkd.pinduoduo.com/login --click-text 密码登录
# ③ 页面较慢时多等；排障时可强制无头
python scripts/dump-login-page.py --url https://mdkd.pinduoduo.com/login --settle-ms 6000 --headless
```

- `--url` 覆盖目标地址（不改配置文件）；`--click-text` 可多次指定，逐个点击后再导出；`--settle-ms` 为加载后额外等待毫秒；`--out` 指定输出路径。
- 脚本**只读**：不输入内容、不点击提交、不保存 Cookie 明文、不做任何验证码处理；产出落 `runtime/diagnostics/login-page-<时间>.json`（被 .gitignore 排除），控制台同步打印 input / button 与关键词命中摘要。
- 校准规则：实测命中的条目把 `verified` 置 `True`、`basis` 写明**实测时间 + 地址 + 命中的真实属性**；未实测的保持 `verified=False`。**同 id / 同 class 存在两份时，交互候选必须带 `:visible`**（该文件头有完整说明与 Playwright 官方依据链接）。

## 9. 已知限制与未验证项（如实声明）

### 9.1 已由采集机实测确认（2026-09-22，PC-20260112CXLA / Win10 19045.4291）

| 项 | 实测结论 |
| --- | --- |
| 真实登录入口 | `https://mdkd.pinduoduo.com/login`（`https://mdkd.pinduoduo.com/` 会 302 跳到这里，标题「代收点」） |
| `mcmd.pinduoduo.com/home` | **营销落地页**：标题「快递代收官网」，`input 数=0`、无密码框，文案为「入驻福利」「短信全免费」等 |
| 登录方式 | 页面提供「短信登录 / 密码登录 / 微信登录」三种，**默认停在「短信登录」**（此时密码框不可见）。本项目走密码登录方式，**需先切页签** |
| 密码登录页签 DOM | 密码框 `input.password-input`（id=`password`，该页签下唯一）；账号框 `#mobile`（class 含 `rocket-input`）；提交按钮 `button.login-btn` |
| 同元素两份 | 短信/密码页签面板**同时挂在 DOM**，切换页签只切显隐 → `#mobile` 与 `button.login-btn` 各两份，必须用 `:visible` 约束区分 |
| **激活面板（作用域锚点）** | 被填值的 `#mobile` 与目标按钮所在面板为 `div[role=tabpanel][aria-hidden=false].rocket-tabs-tabpane-active`；其中 `aria-hidden=true` 的为非活动面板。据此设两个作用域锚点：`selectors.ACTIVE_PANEL`（class 锚点 `.rocket-tabs-tabpane-active`）与 `selectors.ACTIVE_PANEL_ARIA`（aria 锚点 `div[role="tabpanel"][aria-hidden="false"]`），交互类实测候选两种写法并列、互为兜底 |
| **提交按钮点击被拦截** | 实测日志：`button.login-btn:visible` 已定位成功（`element is visible, enabled and stable`），但点击坐标被激活面板的 `#mobile` 子树与 `div.rocket-tabs...login-tabs` 页签容器覆盖（pointer events 拦截），Playwright 重试 30s 后抛 `TimeoutError`。**本次对策**：提交改为多路径降级链（回车 → 真实点击 → 强制点击 → 合成点击），每条短超时并用可观测信号判定生效；`run()` 全流程收敛、不输出裸 traceback |
| **账号框已填入** | 实测 traceback 的拦截者日志中出现 `<input id="mobile" ... value="16626369983" class="rocket-input"/>`，证明账号框已被逐字符填入（`humanize` 输入与页签切换均生效） |
| 采集机环境 | Python 3.13.15 + MySQL 8.0.46，依赖已装、Chromium 已下载，`check-env` 全绿 |

### 9.2 仍未实测（不得当作已验证）

1. **密码框是否被正确填入**：本轮 traceback 只暴露了账号框（`#mobile`）的 `value`，**密码框的 value 未验证**（密码值也不应出现在日志中，故只能靠「提交前自检通过」这一信号间接确认）。
2. **提交降级链未在采集机实跑**：新增的 A~D 四条路径与「提交是否生效」的信号判定目前**只有单测覆盖**（纯逻辑、假页面），尚未在真实 Chromium 上跑通；哪条路径最终生效、`Escape` 能否收起浮层均待实测。
3. **提交后是否出现滑块 / 图形验证码**：本轮只做只读结构导出，**未提交表单**，未实测。
4. **登录成功后的工作台 DOM 与跳转目标**：尚未成功登录，故 `WORKBENCH_MARKERS`（`div[role=menuitem]` / 「运单查询」）**仍属未实测**——其 `verified=True` 的依据是站点分析文档核实，**不是**采集机实测。
5. **挑战信号表未校准**：`CHALLENGE_SIGNALS` 仍为通用中文文案推断（ADR 待验证项 V17 / V21）。**已知风险**：密码登录页 DOM 中存在「获取验证码」按钮，通用信号「验证码」可能被误命中而直接回落人工；是否误命中取决于该按钮在密码页签下是否可见，**待实跑确认**。
6. **会话有效期未实测**：`session_ttl_hours`（默认 12h）为保守估值，仅用于估算 `expire_at`，待 V18 实测回填。
7. **未运行验证项**：开发机无 MySQL、无企业微信，且未安装 Playwright/PyMySQL，故以下**从未真实执行**：真实开浏览器、真实登录、真实连库建表、DPAPI 真实解密。以上均在采集机验证。
8. **storage_state 落盘与 ADR 表述的差异**：ADR §20.2.5 表述「登录态仍落在 profile、采集端不另行落盘」。本版按任务要求实现了 `storage_state` 导出（含 Cookie，属敏感物，落 `runtime/` 且不入库不入 Git）。若评审认定 profile 已足够，可将 `login.export_storage_state` 置 `false` 彻底关闭（代码内已标 `TODO(扩展)`）。
9. **版本与多实例**：本版为**单机单实例单账号**，未做多账号并发（代码内已标 `TODO(扩展)`）。
10. **DPAPI 作用域约束**：密文为 CurrentUser 作用域，只能由生成它的同一 Windows 账户在同一台机器解密；若管理员**重置**该账户密码，旧密文可能不可恢复（ADR §20.3.1）。
11. **PyMySQL 版本**：`PyMySQL[rsa]==1.2.*` 的次要版本号取自 PyPI 当前发布线，未在采集机实装验证。
12. **人工介入登录（`login --manual`）未在采集机实跑**：轮询四分支（成功 / 超时 / 凭据错误 / 关窗）、只读契约、headless 报错、`--no-prefill`、预填降级脱敏**仅有纯逻辑单测覆盖**（假页面 / 假时钟）；真实 Chromium 上的关窗识别（`page.is_closed()` 与 Playwright `TargetClosedError`）、预填交互、`manual_wait_minutes` 实际等待**待采集机实测**。另：成功判定复用尚未实测的 `WORKBENCH_MARKERS`，若登录实际已成功却停在「未检测到登录成功」，需先导出工作台 DOM 校准 `selectors.py`。

## 10. 故障处理

| 现象 | 处理 |
| --- | --- |
| **登录时出现验证码 / 滑块 / 短信验证** | 这是**预期行为**：自动路径（`--run`）会立即停止并退出码 2，**浏览器随上下文一并关闭**。要人工完成验证请改用 `python -m collector login --manual`（窗口保持打开、程序只读等待，见 §5）；成功后程序会自动保存会话，无需再跑 `--check`。**禁止**接入任何打码/识别服务（红线 C2/C8）。 |
| 提示「未定位到账号/密码输入框」「未定位到「密码登录」页签」「已点击「密码登录」页签但密码框仍未出现」 | 登录页结构或文案变化。**先导出真实 DOM 再校准**：`python scripts/dump-login-page.py --url <登录地址> [--click-text 密码登录]`，对照产出改 `src/collector/login/selectors.py`（改版只需改这一个文件）。同 id / 同 class 有两份时记得带 `:visible`，并保留激活面板作用域锚点（`ACTIVE_PANEL` class 锚点 / `ACTIVE_PANEL_ARIA` aria 锚点）。 |
| 提示「提交前自检失败：账号/密码输入框为空」 | 输入未真正落到框里（定位可能选到了隐藏副本）。先跑上面的 DOM 导出校准 `ACCOUNT_INPUTS` / `PASSWORD_INPUTS`；自检只统计字符数，**不会**打印密码值。 |
| **提交后长期无跳转 / 「提交按钮被其它元素遮挡」/ 「N 条提交路径均未使登录生效」** | 提交走**多路径降级链**（A 回车 → B 真实点击 → C 强制点击 → D 合成点击），每条短超时并用可观测信号判定生效，日志逐条打印「尝试路径 X / 已生效 / 未生效降级」。四条全部未生效说明按钮被覆盖或页面结构变化：先导出 DOM 复核 `SUBMIT_BUTTONS` 与激活面板锚点，必要时上调 `login.submit_path_timeout_ms` / `submit_effect_probe_ms`（默认 4000 / 3000，全链总预算与 `submit_wait_ms` 对齐）。**严禁**把「点了没反应」当成功。 |
| 提示密码框定位不到、但手机号框正常 | 目标站**默认停在「短信登录」**、密码框此时不可见。本项目走密码登录，登录流程会先点「密码登录」页签；若页签文案变了按上一行重新导出校准 `LOGIN_MODE_SWITCHERS`。 |
| 提示「账号或密码错误」 | 凭据已失效。请站长确认后重跑 `init-local-secrets.ps1 ... -Force` 覆盖 `pdd` 段。 |
| 提示「DPAPI 解密失败」 | 密文与当前 Windows 账户/机器不匹配。请在生成密文的**同一账户**下执行；跨机器/跨账户复制密钥文件无效。 |
| 提示「未找到本地密钥文件」 | 先跑 `scripts\env-setup.ps1`，再跑 `scripts\init-local-secrets.ps1`。 |
| `check-env` 报 `pymysql` 缺失 | `python -m pip install -r requirements.txt`（须含 `[rsa]`）。 |
| 启动浏览器报未安装 | `python -m playwright install chromium`。 |
| MySQL 连接超时 | 确认服务 `MySQL80` 运行中且仅监听 `127.0.0.1:3306`：`Get-Service MySQL80`。 |

## 11. 开发与自检

```powershell
python -m compileall -q src tests          # 语法编译
python -m pytest tests -q                   # 纯逻辑单测（不连库、不开浏览器）
```

测试覆盖：配置加载与环境变量覆盖、DPAPI 封装（打桩）、`PddAccount` 脱敏与校验、仿人工延时区间边界、repository 参数化 SQL 与幂等 upsert、建表语句切分、登录结果判定与挑战识别、选择器表的实测优先级与可见性约束（`:visible`）与激活面板作用域锚点（class / aria）、登录方式页签切换的幂等判定与失败报错、**提交多路径降级链（顺序、短超时透传、单条失败不中断、全部未生效报错）与生效信号判定**、**填表自检（仅统计字符数、密码值不入日志/异常）**、日志脱敏、**人工介入登录（轮询四分支 / 只读不干扰 / headless 明确报错 / `--no-prefill` 不触碰页面 / 预填降级原因脱敏 / 与 `--run` 互斥且不互相调用）**。

日志：`runtime/logs/collector.log`（滚动，全局脱敏）；凭据初始化日志：`runtime/logs/init-local-secrets.log`。

## 12. 目录结构

```text
hrm-dev/collector/
├── config/
│   ├── settings.example.toml      # 非敏感配置模板（入库）
│   └── local/                     # 本地凭据（DPAPI 密文，不入库）
├── scripts/
│   ├── env-setup.ps1              # 环境 + MySQL 凭据（已存在，本版未改动）
│   ├── init-local-secrets.ps1     # 多多账号密码加密写入
│   └── dump-login-page.py         # 只读导出登录页 DOM，用于校准 selectors.py
├── src/collector/
│   ├── __main__.py                # CLI 入口
│   ├── logging_setup.py           # 日志分级 + 全局脱敏
│   ├── config/
│   │   ├── settings.py            # 非敏感配置加载
│   │   └── pdd_account.py         # ★ 多多账号密码配置模块
│   ├── security/dpapi.py          # DPAPI 加解密与密钥文件读取
│   ├── db/
│   │   ├── connection.py          # MySQL 连接（仅 127.0.0.1）
│   │   ├── schema.sql             # 建表 DDL
│   │   ├── repository.py          # 会话与游标读写
│   │   └── migrate.py             # 幂等建表
│   └── login/
│       ├── browser.py             # 持久化上下文（无反检测注入）
│       ├── humanize.py            # 仿人工节奏
│       ├── selectors.py           # 选择器与挑战信号（集中一处）
│       └── pdd.py                 # 登录流程编排
└── tests/                         # pytest 纯逻辑单测
```
