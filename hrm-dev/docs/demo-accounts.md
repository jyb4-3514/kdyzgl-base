# 联调/演示账号清单（测试库 `kdyzgl_test`）

> 维护：数据库工程师 `express-station-database-engineer` | 更新：2026-09-25
> 数据来源：`hrm-dev/sql/seed/kdyzgl_test_seed.sql`（第 §2 节既有 56 行 + 第 §2.1 节新增 8 行 = **64 个账号**）
> 相关：`docs/db.md`（结构/枚举）、`docs/deploy.md` §3.6（种子执行与回滚）、`docs/multi-client-architecture.md` §2.4（端职责）

> ⚠️ **本清单仅适用于测试库 `kdyzgl_test`。** 禁止用于 `kdyzgl`（结构库，保持干净）与 `courier_station`（现网，绝不可碰）。
> 本文只写**项目公开约定的演示口令 `demo1234`** 与测试环境固定短信码 `000000`，**不含任何真实凭据、真实手机号或服务器信息**。

---

## 1. 速查：按端可用账号

口令说明：下述账号的登录口令**统一为项目公开约定 `demo1234`**（脚本内只存 BCrypt 散列，不存明文）。`boss`/`manager`/`staff`/`staff2` 为**易记标准化演示账号**（口头讲解用）；其余账号为批量铺底数据，同样可直接登录。

### 1.1 网页端 / PC 管理端（`X-Client-Type: ADMIN`，**仅 `ADMIN`**）

| 用户名 | 角色 | 口令 | 说明 |
| --- | --- | --- | --- |
| `boss` | ADMIN | `demo1234` | **标准化演示账号**，唯一能登网页端/PC 端的推荐账号 |
| `admin` | ADMIN | `demo1234` | 系统管理员（既有） |
| `admin_pwd0` | ADMIN | `demo1234` | 演示管理员（既有） |
| `admin2` | ADMIN | `demo1234` | 演示管理员二（既有） |
| `16626369983` | ADMIN | `demo1234` | 预留管理员（既有） |

> 非 `ADMIN` 角色访问网页端/PC 端会被服务端拦截，返回 **错误码 `1110 该账号无权登录此端`**（HTTP 200 + `{code:1110}`）。可用 `manager` 或 `staff` 复现该拦截。

### 1.2 移动端 · 管理视角（老板端，`ADMIN` + `STATION_ADMIN`）

| 用户名 | 角色 | 所属驿站 | 口令 | 说明 |
| --- | --- | --- | --- | --- |
| `manager` | STATION_ADMIN | 驿站1 城东 | `demo1234` | **标准化演示账号**，移动端管理视角；兼验 PC 端 1110 拦截 |
| `st001_admin` | STATION_ADMIN | 驿站1 城东 | `demo1234` | 站长（既有） |
| `boss` | ADMIN | — | `demo1234` | 管理层经营视角（全站可见） |

> 驿站 2–8 各有 1 名站长（`st002_admin` … `st008_admin`），清单见 §3。

### 1.3 移动端 · 员工端（驿站助手，**不限角色**）

| 用户名 | 角色 | 所属驿站 | 口令 | 说明 |
| --- | --- | --- | --- | --- |
| `staff` | STAFF | 驿站1 城东 | `demo1234` | **标准化演示账号**，员工端主用 |
| `staff2` | STAFF | 驿站2 城西 | `demo1234` | 备用员工账号 |
| `st001_staff` | STAFF | 驿站1 城东 | `demo1234` | 城东员工（既有） |
| `staff13` … `staff56` | STAFF | 驿站1–7 | `demo1234` | 批量铺底员工（既有；`staff56` 为禁用样例，见 §3） |

---

## 2. 按角色汇总

| 角色 | 账号数 | 代表账号 | 说明 |
| --- | --- | --- | --- |
| `ADMIN` | 5 | `boss`、`admin` | 网页端/PC 端唯一可登角色；`station_id` 为空 |
| `STATION_ADMIN` | 9 | `manager`、`st001_admin` | 每站 1 名站长（驿站 1–8），移动端管理视角 |
| `STAFF` | 50 | `staff`、`staff2`、`staff13`… | 员工端主力；含 1 个禁用样例 `staff56` |
| **合计** | **64** | — | 56 既有 + 8 新增 |

---

## 3. 全量账号表（64）

> 列：`username` / 角色 / 所属驿站 / 状态 / 可登端。状态：`1=启用`、`0=禁用`。
> 可登端缩写：**PC**=网页端/PC 管理端（仅 ADMIN）、**M**=移动端管理视角（ADMIN+STATION_ADMIN）、**S**=移动端员工端（不限角色）。

### 3.1 ADMIN（5）

| # | username | 角色 | 驿站 | 状态 | 可登端 |
| --- | --- | --- | --- | --- | --- |
| 1 | `admin` | ADMIN | — | 1 | PC / M / S |
| 2 | `admin_pwd0` | ADMIN | — | 1 | PC / M / S |
| 5 | `16626369983` | ADMIN | — | 1 | PC / M / S |
| 12 | `admin2` | ADMIN | — | 1 | PC / M / S |
| 57 | `boss` | ADMIN | — | 1 | PC / M / S |

### 3.2 STATION_ADMIN（9）

| # | username | 角色 | 驿站 | 状态 | 可登端 |
| --- | --- | --- | --- | --- | --- |
| 3 | `st001_admin` | STATION_ADMIN | 1 城东 | 1 | M / S |
| 6 | `st002_admin` | STATION_ADMIN | 2 城西 | 1 | M / S |
| 7 | `st003_admin` | STATION_ADMIN | 3 城南 | 1 | M / S |
| 8 | `st004_admin` | STATION_ADMIN | 4 城北 | 1 | M / S |
| 9 | `st005_admin` | STATION_ADMIN | 5 高新 | 1 | M / S |
| 10 | `st006_admin` | STATION_ADMIN | 6 大学城 | 1 | M / S |
| 11 | `st007_admin` | STATION_ADMIN | 7 老城 | 1 | M / S |
| 58 | `manager` | STATION_ADMIN | 1 城东 | 1 | M / S |
| 61 | `st008_admin` | STATION_ADMIN | 8 开发区分站（停用） | 1 | M / S |

### 3.3 STAFF（50）

| # | username | 驿站 | 状态 | # | username | 驿站 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 4 | `st001_staff` | 1 城东 | 1 | 36 | `staff36` | 7 老城 | 1 |
| 13 | `staff13` | 1 城东 | 1 | 37 | `staff37` | 2 城西 | 1 |
| 14 | `staff14` | 1 城东 | 1 | 38 | `staff38` | 3 城南 | 1 |
| 15 | `staff15` | 1 城东 | 1 | 39 | `staff39` | 4 城北 | 1 |
| 16 | `staff16` | 1 城东 | 1 | 40 | `staff40` | 5 高新 | 1 |
| 17 | `staff17` | 1 城东 | 1 | 41 | `staff41` | 6 大学城 | 1 |
| 18 | `staff18` | 1 城东 | 1 | 42 | `staff42` | 7 老城 | 1 |
| 19 | `staff19` | 2 城西 | 1 | 43 | `staff43` | 2 城西 | 1 |
| 20 | `staff20` | 3 城南 | 1 | 44 | `staff44` | 3 城南 | 1 |
| 21 | `staff21` | 4 城北 | 1 | 45 | `staff45` | 4 城北 | 1 |
| 22 | `staff22` | 5 高新 | 1 | 46 | `staff46` | 5 高新 | 1 |
| 23 | `staff23` | 6 大学城 | 1 | 47 | `staff47` | 6 大学城 | 1 |
| 24 | `staff24` | 7 老城 | 1 | 48 | `staff48` | 7 老城 | 1 |
| 25 | `staff25` | 2 城西 | 1 | 49 | `staff49` | 2 城西 | 1 |
| 26 | `staff26` | 3 城南 | 1 | 50 | `staff50` | 3 城南 | 1 |
| 27 | `staff27` | 4 城北 | 1 | 51 | `staff51` | 4 城北 | 1 |
| 28 | `staff28` | 5 高新 | 1 | 52 | `staff52` | 5 高新 | 1 |
| 29 | `staff29` | 6 大学城 | 1 | 53 | `staff53` | 6 大学城 | 1 |
| 30 | `staff30` | 7 老城 | 1 | 54 | `staff54` | 7 老城 | 1 |
| 31 | `staff31` | 2 城西 | 1 | 55 | `staff55` | 2 城西 | 1 |
| 32 | `staff32` | 3 城南 | 1 | 56 | `staff56` | 3 城南 | **0（禁用样例）** |
| 33 | `staff33` | 4 城北 | 1 | 59 | `staff` | 1 城东 | 1 |
| 34 | `staff34` | 5 高新 | 1 | 60 | `staff2` | 2 城西 | 1 |
| 35 | `staff35` | 6 大学城 | 1 | 62 | `st008_staff` | 8 开发区分站（停用） | 1 |
| — | — | — | — | 63 | `st008_staff2` | 8 开发区分站（停用） | 1 |
| — | — | — | — | 64 | `st008_staff3` | 8 开发区分站（停用） | 1 |

> 说明：`staff56`（id 56）为**有意保留的禁用账号样例**，用于演示「含禁用」筛选口径；它**不能登录**（服务端返回账号已禁用）。驿站 8「开发区分站」本身 `status=0`（停用），其为「停用驿站」演示样例，故该站账号在页面上通常不出现在启用驿站的可选范围内。

---

## 4. 各端准入规则

| 端 | 允许角色 | 判定依据 | 不通过时 |
| --- | --- | --- | --- |
| 网页端 / PC 管理端 | **仅 `ADMIN`** | 服务端 `hrm.auth.pc-allowed-roles=ADMIN`（`X-Client-Type: ADMIN` 时校验角色） | **错误码 `1110 该账号无权登录此端`**（HTTP 200 + `{code:1110}`） |
| 移动端 · 管理视角（老板端） | `ADMIN` + `STATION_ADMIN` | 前端按角色渲染管理侧入口；服务端以 `@RequireRoles` + 数据范围三层收敛 | 管理侧接口按角色返回无权限/空 |
| 移动端 · 员工端（驿站助手） | **不限角色** | 员工端无独立端点，任何已登录角色均可进入 | — |

> 技术细节：
> - 未上报端类型（缺省回落 `WEB`）时**不施加角色约束**，行为与本改造前一致；仅显式 `X-Client-Type: ADMIN`（PC 管理端）才收紧为仅 `ADMIN`。
> - 上表为可判定口径，最终以服务端 `@RequireRoles` + 数据范围为准（详见 `docs/update-log.md` 2026-09-24「PC 端仅 ADMIN 可登录」条目、`docs/multi-client-architecture.md` §2.4）。

---

## 5. 登录前置说明

- **双通道登录**：登录页支持**密码登录**与**短信验证码登录**两条通道。
  - 密码通道：用户名 + 口令 `demo1234`（所有 64 个账号通用，禁用账号除外）。
  - 短信通道：手机号 + 短信验证码；账号须已绑定手机号（本清单账号均填了占位手机号 `139xxxx`），否则返回 `1109 未绑手机号`。
- **测试环境固定短信验证码为 `000000`**：由配置项 `hrm.sms.dev-fixed-code`（默认 `"000000"`）提供，**仅在未配置短信厂商凭据（`hrm.sms.provider=none`，非生产）时生效**。
  - **生产必须替换**：生产环境 `provider` 须为 `aliyun` 并配置真实凭据，否则应用启动即 fail-fast；`dev-fixed-code` 在生产**不会生效**，且验证码绝不打印到日志/响应体。
- **口令说明**：脚本内 `employee.password` 只存 **BCrypt 散列**（`$2a$` / cost=10 / 60 字符），对应公开约定口令 `demo1234`；**脚本与本文均不含明文以外的真实凭据**。
- **免首登改密**：全部账号 `pwd_changed=1`，登录后不会被强制跳转改密页，便于演示。

---

## 6. 账号来源、隔离与重灌命令

- **来源**：由 `hrm-dev/sql/seed/kdyzgl_test_seed.sql` 灌入 → 目标库 **`kdyzgl_test`**（测试库，Flyway 已至 v15）。
- **库隔离（务必分清）**：

  | 库 | 用途 | 是否可执行本种子 |
  | --- | --- | --- |
  | `kdyzgl_test` | 联调/演示 | **仅此库** |
  | `kdyzgl` | 结构库（保持干净） | **禁止** |
  | `courier_station` | 现网快递系统 | **绝对禁止** |

- **完整重灌（重置型种子，需主智能体 C 档授权后在服务器执行）**：

  ```bash
  # 目标库显式写死，避免误连 kdyzgl / courier_station
  mysql --default-character-set=utf8mb4 kdyzgl_test < hrm-dev/sql/seed/kdyzgl_test_seed.sql
  ```

  该脚本先按「子表→父表」整表 `DELETE` 再 `INSERT`（无 DDL、不 `DROP`/不 `TRUNCATE`），可重复执行，**重跑即回到种子态**。

- **仅补新增账号（可选替代方案，不重置既有数据）**：若测试库已在运行、只想追加本次新增的 8 个账号，可只执行 §2.1 块（执行前须设 `@base`）：

  ```sql
  SET NAMES utf8mb4;
  SET @base := CURDATE();
  -- 复制 kdyzgl_test_seed.sql 第 §2.1 节（`INSERT INTO employee ...` 那一段）粘贴执行
  ```

  注意：此替代方案要求目标库中**尚不存在** id 57–64 的 `employee` 行；若已存在需先清理或改走完整重灌。

- **执行后一键核对**：脚本末尾内置各表 `COUNT(*)` 查询，其中 `employee` 应为 **64**。若走替代方案，可单独核对：

  ```bash
  mysql -uroot -p kdyzgl_test -e "SELECT COUNT(*) AS employee_total FROM employee;"
  ```

---

## 7. 数据覆盖范围与已知缺口

- **新增 8 个账号（id 57–64）仅落 `employee` 行**，其**考勤/排班/工资/人事档案（`hr_profile`/`hr_salary`/`attendance_*`/`payroll*`）等业务数据有意留空**：
  - 原因：这些账号定位于「预留 + 可直接登录」，登录与端准入只依赖 `employee`；补齐全量业务数据会显著增加种子体积与维护成本，且非本次目标。
  - 影响：以新账号登录后，个人侧「我的考勤/工资/档案」等页面可能为空态（属正常空态，非缺陷）；管理员可在系统内补录，或后续另立种子分节。
- **`hr_profile`/`hr_salary` 仍为 56 行**（仅覆盖既有 56 个员工），与 `employee` 的 64 行形成有意的 8 行差；脚本末尾校验查询按实际 `COUNT(*)` 输出，不硬编码期望值，故不会误报。
- **驿站 8 账号可登录但驿站停用**：`st008_admin`/`st008_staff*` 账号 `status=1` 可登录，其所属驿站 `status=0`（停用）——用于演示「站点停用」与「账号启用」相互独立的场景。

---

## 8. 维护与变更

- 变更种子账号须改 `hrm-dev/sql/seed/kdyzgl_test_seed.sql`，并在 `hrm-dev/docs/update-log.md` 追加条目；**已执行的迁移脚本永不修改**（本文件非 Flyway 迁移，不受版本号约束，但仍须保证幂等与「既有 56 行不动」）。
- 新增账号命名须**逐个核对与现有 64 个 `username` 不重复**；`id` 续接（当前最大 64，下一可用 65）。
- 口令散列口径不变（BCrypt `$2a$` cost=10）；**禁止在脚本/文档写入明文以外的真实凭据或真实手机号**。
