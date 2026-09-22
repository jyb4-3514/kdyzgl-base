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
    -Site 'https://mcmd.pinduoduo.com/home' `
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

## 5. CLI 用法

| 命令 | 说明 | 退出码 |
| --- | --- | --- |
| `python -m collector check-env` | 自检：Python 版本、依赖、配置文件、本地凭据、MySQL 连通性 | 0 通过 / 1 有失败项 |
| `python -m collector init-db` | 按 `src/collector/db/schema.sql` 幂等建表 | 0 / 1 |
| `python -m collector login --check` | 只探测当前登录态，不做登录动作 | 0 已登录 / 1 未登录或失败 / 2 需人工介入 |
| `python -m collector login --run` | 执行登录（已登录则跳过） | 同上 |
| `python -m collector session list --limit 20` | 列出最近的登录会话记录（脱敏） | 0 / 1 |

公共参数：`--settings <路径>`、`--secrets <路径>`。

## 6. MySQL 表结构要点

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

## 7. 登录模块设计要点

| 主题 | 决策 |
| --- | --- |
| 浏览器 | Playwright `launch_persistent_context`，`user_data_dir = runtime/browser-profile`（被 .gitignore 排除）；多账号 = 每账号独立 profile（ADR §18.3 补丁 3） |
| 登录态载体 | **profile 为权威载体**（ADR §20.2.5）；`storage_state` 仅作诊断导出，落 `runtime/storage-state/<account_hash>.json` |
| 登录结果判定 | **不只看 URL**：① 域名白名单（允许白名单域子域）② 工作台特征元素（已核实的 `DIV[role=menuitem]` / 「运单查询」）③ 登录表单是否仍存在；三者组合判定，见 `pdd.judge_login_result` |
| 选择器策略 | 全部集中在 `src/collector/login/selectors.py`，语义定位优先、通用 CSS 兜底；每条带「依据」与 `verified` 标记。**目标站登录页的真实 DOM 未实测，故一律标 `未核实，需在采集机实测确认`** |
| 仿人工 | 仅限**节奏与停顿**（`src/collector/login/humanize.py`）：逐字符 60–180ms 随机、字段间停顿、提交前停顿；参数全部可配 |
| 人工介入触发 | 页面文本命中挑战信号（验证码 / 滑块 / 短信验证 / 风控提示）→ 立即停止，抛 `ManualInterventionRequired`，登录态置 `MANUAL_REQUIRED`，退出码 2 并打印指引 |
| 凭据错误 | 明确「账号或密码错误」→ **不重试**，提示站长更新凭据（`init-local-secrets.ps1 -Force`） |
| 自动登录开关 | `login.auto_login_enabled = false` 时直接回落人工（ADR §20.2.9 生效条件未闭环的站点应关闭） |

### 红线（代码内已收口）

- **不做反检测伪装**：不注入 stealth / 不改 `navigator.webdriver` / 不伪造 UA 指纹 / 不用代理；`browser.validate_extra_args` 会**拒绝启动**含 `--remote-debugging-address`、`--remote-allow-origins`、`--user-agent`、`--proxy-server` 的参数。
- **不逆向签名、不自造请求**：登录提交走页面自身流程，不接触 `anti-content`。
- **验证码不自动识别**：无任何打码/识别代码路径，命中即停。
- 只连 `127.0.0.1`：`db.host` 非本机直接拒绝启动。
- 日志全局脱敏：`RedactionFormatter` 对整条日志（含异常堆栈）替换 `password/pwd/cookie/authorization/anti-content/etag/pdd-id` 及运行期登记的明文值，另有手机号兜底脱敏。

## 8. 已知限制与未验证项（如实声明）

1. **登录页 DOM 未实测**：`selectors.py` 中除站点分析已核实的两条（`div[role=menuitem]`、「运单查询」）外，其余均为语义候选，**标注为未核实**，需在采集机打开登录页实测校准。
2. **站点 URL 待确认**：ADR 已核实入口 `mcmd.pinduoduo.com` 可能只是营销落地页、真实工作台为 `mdkd.pinduoduo.com`，故 `site.login_url` / `site.workbench_url` 均为配置项，首次实测须确认并回填。
3. **挑战信号表未校准**：`CHALLENGE_SIGNALS` 为通用中文文案推断，需按真实登录页校准（ADR 待验证项 V17 / V21）。
4. **会话有效期未实测**：`session_ttl_hours`（默认 12h）为保守估值，仅用于估算 `expire_at`，待 V18 实测回填。
5. **未运行验证项**：开发机无 MySQL、无企业微信，且未安装 Playwright/PyMySQL，故以下**从未真实执行**：真实开浏览器、真实登录、真实连库建表、DPAPI 真实解密。以上均在采集机验证。
6. **storage_state 落盘与 ADR 表述的差异**：ADR §20.2.5 表述「登录态仍落在 profile、采集端不另行落盘」。本版按任务要求实现了 `storage_state` 导出（含 Cookie，属敏感物，落 `runtime/` 且不入库不入 Git）。若评审认定 profile 已足够，可将 `login.export_storage_state` 置 `false` 彻底关闭（代码内已标 `TODO(扩展)`）。
7. **版本与多实例**：本版为**单机单实例单账号**，未做多账号并发（代码内已标 `TODO(扩展)`）。
8. **DPAPI 作用域约束**：密文为 CurrentUser 作用域，只能由生成它的同一 Windows 账户在同一台机器解密；若管理员**重置**该账户密码，旧密文可能不可恢复（ADR §20.3.1）。
9. **PyMySQL 版本**：`PyMySQL[rsa]==1.2.*` 的次要版本号取自 PyPI 当前发布线，未在采集机实装验证。

## 9. 故障处理

| 现象 | 处理 |
| --- | --- |
| **登录时出现验证码 / 滑块 / 短信验证** | 这是**预期行为**：采集端会立即停止并退出码 2。请在采集机浏览器窗口中**人工完成验证**，然后重跑 `python -m collector login --check` 确认登录态有效。**禁止**接入任何打码/识别服务（红线 C2/C8）。 |
| 提示「未定位到账号/密码输入框」 | 登录页结构变化。核对 `src/collector/login/selectors.py` 的候选选择器并实测校准（改版只需改这一个文件）。 |
| 提示「账号或密码错误」 | 凭据已失效。请站长确认后重跑 `init-local-secrets.ps1 ... -Force` 覆盖 `pdd` 段。 |
| 提示「DPAPI 解密失败」 | 密文与当前 Windows 账户/机器不匹配。请在生成密文的**同一账户**下执行；跨机器/跨账户复制密钥文件无效。 |
| 提示「未找到本地密钥文件」 | 先跑 `scripts\env-setup.ps1`，再跑 `scripts\init-local-secrets.ps1`。 |
| `check-env` 报 `pymysql` 缺失 | `python -m pip install -r requirements.txt`（须含 `[rsa]`）。 |
| 启动浏览器报未安装 | `python -m playwright install chromium`。 |
| MySQL 连接超时 | 确认服务 `MySQL80` 运行中且仅监听 `127.0.0.1:3306`：`Get-Service MySQL80`。 |

## 10. 开发与自检

```powershell
python -m compileall -q src tests          # 语法编译
python -m pytest tests -q                   # 纯逻辑单测（不连库、不开浏览器）
```

测试覆盖：配置加载与环境变量覆盖、DPAPI 封装（打桩）、`PddAccount` 脱敏与校验、仿人工延时区间边界、repository 参数化 SQL 与幂等 upsert、建表语句切分、登录结果判定与挑战识别、日志脱敏。

日志：`runtime/logs/collector.log`（滚动，全局脱敏）；凭据初始化日志：`runtime/logs/init-local-secrets.log`。

## 11. 目录结构

```text
hrm-dev/collector/
├── config/
│   ├── settings.example.toml      # 非敏感配置模板（入库）
│   └── local/                     # 本地凭据（DPAPI 密文，不入库）
├── scripts/
│   ├── env-setup.ps1              # 环境 + MySQL 凭据（已存在，本版未改动）
│   └── init-local-secrets.ps1     # 多多账号密码加密写入
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
