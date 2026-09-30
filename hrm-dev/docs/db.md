# 快递驿站智汇系统 · 数据库设计文档（一期 4 表 → 145 接口 44 表）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v2.5（v1.0 = 一期 4 表；v2.0 = 第 8 章 P1~P10 共 33 表；v2.1 = 第 10 章 登录体系改造 1 表 + `hr_flow` 补列；v2.2 = 第 11 章 员工自助注册 1 表 + `employee` 活跃唯一/岗位 + `hr_flow` 来源列；v2.3 = §8.6 薪资结算自动化 3 表 + `payroll` 补 3 列；v2.4 = §8.6.5~§8.6.7 同步设计 v1.1 claim 槽位语义：`success_key`→`claim_key`、补 `skip_code`、`payroll_day` 1-31；**v2.5 = §8.6.6~§8.6.8 同步 `V21`：`payroll_log` 冗余定位列、`payroll_run.attempt_date` + `uk_attempt` + 索引精简、新表 `station_payroll_setting_log`、`notification.type` 列 COMMENT 补 7/8/9（仅注释不改列型）**；**v2.6 = §12 驿站精灵管理能力扩展数据层增量（`V22` `attendance_schedule` 生成列式活跃唯一键 + `V23` 操作审计留痕表 `operation_audit_log`）**） |
| 编写日期 | 2026-09-06（v1.0）/ 2026-09-24（v2.0）/ 2026-09-24（v2.1）/ 2026-09-26（v2.2）/ 2026-09-27（v2.3）/ 2026-09-27（v2.4）/ 2026-09-27（v2.5）/ 2026-09-27（v2.6） |
| 状态 | 第 1~7 章（一期）已评审；第 8 章（二期扩展）已落库（V3~V13 全部执行成功）；第 10 章（登录改造）待主智能体 Review；第 11 章（员工自助注册 V16~V19）静态产出，待技术评审/报审；§8.6.5~§8.6.8（薪资结算自动化 V20 + V21）静态产出，待技术评审/报审；第 12 章（驿站精灵管理能力扩展 V22/V23）静态产出，待技术评审复评/报审 |
| 数据库 | **MySQL 8.0 单库**（决策：`postgresql/` 目录冻结不再维护，保留不删避免历史引用断裂） |
| 库名 | `kdyzgl`（utf8mb4 / utf8mb4_0900_ai_ci） |
| 关联文档 | [requirement.md](requirement.md)、[api.md](api.md)、[plan.md](plan.md)、[server-architecture.md](server-architecture.md)、[algo-hrm-server.md](algo-hrm-server.md)、[multi-client-architecture.md](multi-client-architecture.md)、[security-auth-review.md](security-auth-review.md)、[registration-design.md](registration-design.md) |
| 迁移落位 | `hrm-server/src/main/resources/db/migration/mysql/V1..V23`；快照 `sql/schema/mysql/init.sql` |

> **v2.0 变更范围**：第 1~7 章为一期权威基准，**字段与语义保持冻结不改**；第 8 章按
> `server-architecture.md` §4（表清单/字段与索引策略/Flyway 版本规划/P10 大表策略）与
> `algo-hrm-server.md`（参数外置与 S7 索引建议）产出 P1~P10 共 33 张新表的结构定义，
> 字段真源为 `hrm-demo/src/shared/mock/` 各 store（前端 VO 驼峰值由后端 Java 转换，库内 snake_case）。
> 批次与 Flyway 版本号 1:1 对应（P1→V3 … P10→V13）。
>
> **v2.1 变更范围**：新增第 10 章「登录体系改造表结构设计」，按 `multi-client-architecture.md` §4.2.1
> （设备信任模型）与 `security-auth-review.md` §3/§4.2（服务端持有信任态）产出 **V14**（`hr_flow` 补
> `operator_id` / `operator_name` 两列）与 **V15**（`auth_trusted_device` 1 表）。**V1~V13 未改动**；
> 表总数 37 → **38**。本章同时登记「哪些敏感数据不建表（走 Redis）」的取舍（§10.4）。
>
> **v2.2 变更范围**：新增第 11 章「员工自助注册表结构设计」，按 `registration-design.md`（v1.3，技术评审复评
> 「通过」）§2/§7/§11.6/§11.9 产出 **V16**（`employee.phone` 活跃唯一，注册前置缺陷修复 M-5）、**V17**
> （`employee_registration` 1 表，注册事实与凭据载体）、**V18**（`hr_flow.source` 来源留痕 M-9）、**V19**
> （`employee.position` 方案乙 U-07）。**V1~V15 未改动**；表总数 38 → **39**。**仅同步 `sql/schema/mysql/init.sql`**
> （`postgresql/` 自 V2 起冻结，本期不产出 pg 脚本与 pg 快照，见 §5.3）。**V16 执行前须预检 0 行**、
> 全部脚本**属 C 档（未执行）**。
>
> **v2.3 变更范围**：在 §8.6 计薪段内新增 **§8.6.5~§8.6.7** 三节，按 `payroll-automation-design.md`（**v1.1**，claim 槽位语义）
> §3 数据设计产出 **V20**（`V20__payroll_automation.sql`）：新建 `station_payroll_setting`（驿站级算薪配置）、
> `payroll_log`（追加型操作留痕）、`payroll_run`（自动算薪运行记录与认领槽位幂等）3 表，并为 `payroll` 补
> `paid_by_id` / `paid_by_name` / `paid_time` 3 列与 `status` 列 8 态 COMMENT 说明。**V1~V19 未改动**；
> 表总数 39 → **42**。**仅同步 `sql/schema/mysql/init.sql`**（`postgresql/` 自 V2 起冻结，本期不产出 pg 脚本与 pg 快照，见 §5.3）。
> 全部脚本**属 C 档（未执行）**；且据调度规则 P0.6 / L8，本方案为**方案阶段产物**，须先经技术评审工程师评估
> 「通过 / 有条件通过」方可报主智能体审批、再进入 B1 执行。
>
> **v2.4 变更范围（本次）**：承接技术评审**必改项 2**（数据层三方与 v1.1 不一致）与安全评估 **M-1 / REG-01**，将
> §8.6.5~§8.6.7 由 v1.0 语义同步为设计 v1.1 的 **claim 认领槽位语义**：`payroll_run.success_key`→**`claim_key`**、
> 唯一键 `uk_payroll_run_success`→**`uk_payroll_run_claim (station_id, claim_key)`**、新增 `skip_code` 列
> （`payroll_run` 16→**17 列**）、`RUNNING`/`SUCCESS`/`SKIPPED` 均写 `claim_key` 占位、`FAILED` 置 `NULL` 释放；
> `station_payroll_setting.payroll_day` 口径由「建议 1-28」改 **1-31（月末钳位）**；并删除 v1.0 反向断言
> （「`RUNNING`/`FAILED`/`SKIPPED` 可多行」「`success_key` 仅 `SUCCESS` 写值」）。同步落点：**§8.6.3 / §8.6.5 / §8.6.7 /
> §8.11 / §8.12 / §9.1 Q-DB-7/Q-DB-9 / §9.3 / §9.4 U-13** 与 `V20` / `init.sql` 三方逐列一致。**V1~V19 未改动**；
> 表总数不变（仍 **42**）。**V20 仍属 C 档（未执行）**。
>
> **v2.5 变更范围（本次）**：承接 `payroll-automation-design.md` **v1.4** §3.2/§3.3/§3.6 与 `algorithm-payroll-scheduling.md`
> **v1.2** §1.5/§12.2 + `payroll-automation-design.md` v1.4 §4.5（通知类型 7/8/9），产出新迁移 **`V21__payroll_log_locator.sql`**（范围四段）：① `payroll_log` 补冗余定位列
> `employee_id` / `month`（+ 更新 `payroll_id` 注释标注孤儿风险）+ 索引 `idx_payroll_log_emp_month`（M-2②/M-4）；
> ② `payroll_run` 补 `attempt_date DATE NOT NULL`（三段式落地，NOT NULL 无默认）+ **唯一键 `uk_attempt
> (station_id, target_month, attempt_date)`**（主代理 v1.4 裁定：日粒度 DB 硬防线，与 `uk_payroll_run_claim` 正交共存）
> + **删除冗余索引 `idx_payroll_run_station_month`**（被 `uk_attempt` 最左前缀覆盖）+ `skip_code` COMMENT 去 `EXHAUSTED`
> + 表 COMMENT 加「每自然日至多一次」；③ 新表 `station_payroll_setting_log`（M-9 配置变更审计，追加型，10 列）；
> ④ `notification.type` 补列 COMMENT（`1-6`→`1-9`，方案 §4.5 通知类型 7/8/9；仅注释、不改列型）。
> 同步落点：**§5.3 / §8.0 / §8.2.1 / §8.6.6 / §8.6.7 / §8.6.8 / §8.11 / §8.12 / §8.13 / §9.1 Q-DB-7/Q-DB-9/Q-DB-10 / §9.3 / §9.4 U-1/U-14**
> 与 `V21` / `init.sql` 三方逐列一致。**`V1`~`V20` 未改动**（V21 未触碰 V20）；表总数 **42 → 43**。**V21 属 C 档（未执行）**。
> `notification.type` COMMENT（`1-6`→`1-9`）**并入本轮 `V21` 段 4**（主代理裁定「一次做完、不另开迁移版本」）：仅更新列 COMMENT 说明 7/8/9，`type` 仍 `TINYINT NOT NULL` 无默认、**不改列型**；白名单拆分（`sendSystem` 独立 `SYSTEM_TYPES{7,8,9}`、公告白名单维持 `1..6`）属 **B4 代码改动**。见 §8.2.1 / §8.12。
>
> **v2.6 变更范围（本次）**：承接 `boss-management-architecture.md` §2.4（ARCH-S-1）/ §3（ARCH-S-2）与
> `tech-review-boss-management.md` 必改项 **M-1 / M-6 / M-7**，产出两个新迁移：**`V22__attendance_schedule_multi_shift.sql`**
> （`attendance_schedule` 补**生成列** `active_shift_key` + **唯一键** `uk_attendance_schedule_active_shift`，以「活跃唯一」收口多班次；
> 保留两个既有普通索引——**表达式唯一键不吃其最左前缀**，不删索引）与 **`V23__operation_audit_log.sql`**（新表
> `operation_audit_log`，通用追加型操作审计留痕，覆盖 employee/station 的增改启停删重置口令，时间列命名对齐既有留痕
> **`time`**，口令只记布尔、绝不落明文/散列）。同步落点：**§8.3.3 / §8.11 / §8.12 / §8.13 / §9.1 Q-DB-11 / §9.3 / §9.4 U-15 /
> 第 12 章** 与 `V22`/`V23` / `init.sql` 三方逐列一致。**`V1`~`V21` 未改动**；表总数 **43 → 44**（+1 表 `operation_audit_log`；
> `attendance_schedule` +1 列 +1 唯一键）。两脚本**均属 C 档（未执行）**，且据调度规则 **P0.6 / L8** 为方案阶段产物，
> 须先经技术评审工程师复评「通过 / 有条件通过」方可报主智能体审批、再进入 B1/B7 执行。

***

## 1. 设计总则与公共约定

### 1.1 命名与基础约定

- 表名 / 字段名一律 `snake_case`；主键统一 `id`；时间字段统一 `create_time` / `update_time`；
- 所有字符串长度按「字符」计（MySQL utf8mb4 与 PostgreSQL 均为字符语义，无偏差）；
- 逻辑删除统一字段 `is_deleted`（0=否，1=是），配合 MyBatis-Plus `@TableLogic` 全局配置。

### 1.2 主键策略：自增（决策 D13）

| 项 | MySQL 8 | PostgreSQL |
| ---- | ---- | ---- |
| 主键定义 | `BIGINT NOT NULL AUTO_INCREMENT` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` |

**为什么自增而不是雪花 ID**：一期表量级小（员工 < 5000），自增 id 短，前端 JavaScript 无 Long 超过 2^53 的精度丢失问题，无需做 id 转字符串的特殊序列化；`GENERATED BY DEFAULT AS IDENTITY` 允许显式插入种子 id 且是 PG 官方推荐写法。二期包裹表（20 万行级）单表自增完全够用，届时若引入分表再单独评估雪花方案，不影响一期结构。

### 1.3 逻辑外键（决策 D6）+ 唯一性策略（决策 D7）

**不建物理外键（FOREIGN KEY）**，关联关系以下表「逻辑外键」描述，引用完整性由 Service 层校验。

**为什么**：物理外键会在删改时引入锁竞争与级联行为隐患；逻辑删除场景下外键约束与 `is_deleted` 语义冲突（被引用行逻辑删除仍占用约束）；未来若分库分表或迁移数据，物理外键是最大阻力。管理端写入并发极低，Service 层校验足以保证一致性。

**唯一性由 Service 层「活跃数据查重」保证，不建数据库唯一索引**（涉及字段：`employee.username`、`employee.phone`、`station.code`、部门同级名称）。

**为什么**：逻辑删除后行仍物理存在，数据库唯一索引会阻止已删除账号名的复用；MySQL 不支持部分索引（`WHERE is_deleted=0`），PostgreSQL 支持但双库行为不一致；`UNIQUE(username, is_deleted)` 在多次删除同名账号时依然冲突。统一由 Service 查重（并发风险在管理端低并发场景可忽略），普通索引加速查重即可。二期如需强约束，可演进为「删除时置 `is_deleted=id`」策略，属增量变更。

### 1.4 时间字段：应用层填充（决策 D8）

- DDL 仅为 `create_time` / `update_time` 设 `DEFAULT CURRENT_TIMESTAMP` 兜底；
- **不使用** MySQL 的 `ON UPDATE CURRENT_TIMESTAMP`，统一由 MyBatis-Plus `MetaObjectHandler` 在应用层插入/更新时填充。

**为什么**：PostgreSQL 无对应子句，若 MySQL 依赖该子句会导致双库行为不一致（手工 SQL 更新时 PG 不刷新 update_time）；填充逻辑收敛在应用层一处，双库行为完全一致且便于单测。

### 1.5 MySQL / PostgreSQL 类型映射总表

| 用途 | MySQL 8 | PostgreSQL | 说明 |
| ---- | ---- | ---- | ---- |
| 主键 | BIGINT AUTO_INCREMENT | BIGINT GENERATED BY DEFAULT AS IDENTITY | 均允许显式插入种子 id |
| 布尔/小枚举 | TINYINT | SMALLINT | PG 无 TINYINT |
| 时间（秒级） | DATETIME | TIMESTAMP（无时区） | 应用/服务器统一 Asia/Shanghai |
| 日期 | DATE | DATE | 一致 |
| 字符串 | VARCHAR(n)（utf8mb4） | VARCHAR(n)（UTF8） | 一致 |
| 普通整数 | INT | INTEGER | 同义 |
| 自动更新时间 | 不使用 ON UPDATE | 不支持 | 应用层填充，双库一致 |

> 注意：PostgreSQL 索引名为库级唯一，因此两库索引名统一带表名前缀（如 `idx_employee_username`），保证脚本可互相对照。

***

## 2. ER 关系说明

```text
department（部门，自关联树）           station（驿站）
      │ parent_id（自关联）                  │
      │ 1                                  │ 1
      │                                    │
      │ N（dept_id，逻辑外键）              │ N（station_id，逻辑外键）
      └──────────────→ employee（员工 = 账号） ←──────────────┘
                              │ 1
                              │
                              │ N（employee_id，逻辑外键，历史留痕不校验）
                        login_log（登录日志）
```

| 关系 | 基数 | 说明 |
| ---- | ---- | ---- |
| department → employee | 1 : N | 员工归属部门，可为空（未分配）；删除部门前校验无归属员工 |
| station → employee | 1 : N | 员工归属驿站，可为空；**二期包裹数据可见范围的划分基础**；删除驿站前校验无归属员工 |
| department → department | 1 : N | `parent_id` 自关联，`0` 表示根节点 |
| employee → login_log | 1 : N | 登录日志历史留痕，员工删除后日志保留（不做反向校验） |

***

## 3. 表结构详细设计

### 3.1 department（部门表）

**用途**：树形组织架构，供员工归属与筛选。

| 字段 | MySQL 类型 | PG 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| parent_id | BIGINT | BIGINT | 否 | 0 | 父部门 ID，0=根节点 |
| dept_name | VARCHAR(50) | VARCHAR(50) | 否 | - | 部门名称 |
| sort_order | INT | INTEGER | 否 | 0 | 同级排序（升序） |
| is_deleted | TINYINT | SMALLINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 更新时间（应用层维护） |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_department_parent_id | parent_id | 组树查询、子部门存在性校验 |
| idx_department_dept_name | dept_name | 同级重名查重加速 |

**业务规则（Service 层）**：同级（同一 parent_id 下活跃部门）名称唯一；删除前置校验无子部门、无归属员工。

**设计取舍**：不设 `path` 物化路径字段——部门量级 < 200，子树查询用内存递归即可，避免 path 字段的维护一致性成本（员工筛选「含子部门」由后端内存递归实现，见 [api.md](api.md) 4.3.1）。

### 3.2 station（驿站表）

**用途**：驿站主数据；`code` 是二期爬虫任务下发与数据回推的对接键。

| 字段 | MySQL 类型 | PG 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| code | VARCHAR(50) | VARCHAR(50) | 否 | - | 驿站编码，活跃唯一，格式 `^[A-Za-z0-9_-]{2,50}$` |
| station_name | VARCHAR(50) | VARCHAR(50) | 否 | - | 驿站名称 |
| contact_person | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 负责人姓名 |
| contact_phone | VARCHAR(20) | VARCHAR(20) | 是 | NULL | 联系电话（出参脱敏，规则同手机号） |
| address | VARCHAR(255) | VARCHAR(255) | 是 | NULL | 详细地址 |
| status | TINYINT | SMALLINT | 否 | 1 | 状态：0=停用，1=启用 |
| remark | VARCHAR(255) | VARCHAR(255) | 是 | NULL | 备注 |
| is_deleted | TINYINT | SMALLINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 更新时间（应用层维护） |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_station_code | code | 编码查重、二期按编码定位驿站 |
| idx_station_status | status | 状态筛选 |

**业务规则**：`code` 活跃唯一（Service 查重）；一期允许修改编码，**二期爬虫对接上线后编码冻结**（编码已作为外部系统关联键，变更会切断映射，届时接口将拒绝修改并在文档升级说明）；删除前置校验无归属员工。

**二期预留说明**：二期 `parcel` 表将以 `station_id` 为核心维度（包裹归属），`code` 用于爬虫侧识别驿站；本表结构无需变更即可承接。

### 3.3 employee（员工表 = 账号表，决策 D1）

**用途**：员工档案与登录账号合一。

| 字段 | MySQL 类型 | PG 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| username | VARCHAR(30) | VARCHAR(30) | 否 | - | 登录账号，活跃唯一，`^[a-zA-Z][a-zA-Z0-9_]{3,29}$`，创建后不可修改 |
| password | VARCHAR(100) | VARCHAR(100) | 否 | - | BCrypt 散列（`$2a$`，cost=10，60 字符，冗余长度） |
| real_name | VARCHAR(50) | VARCHAR(50) | 否 | - | 员工姓名 |
| phone | VARCHAR(20) | VARCHAR(20) | 否 | - | 手机号，`^1[3-9]\d{9}$`，活跃唯一，出参脱敏 |
| gender | TINYINT | SMALLINT | 否 | 0 | 性别：0=未知，1=男，2=女 |
| dept_id | BIGINT | BIGINT | 是 | NULL | 所属部门（逻辑外键 department.id） |
| station_id | BIGINT | BIGINT | 是 | NULL | 归属驿站（逻辑外键 station.id，二期数据同步基础） |
| role | VARCHAR(20) | VARCHAR(20) | 否 | 'STAFF' | 角色：ADMIN / STATION_ADMIN（二期启用）/ STAFF（决策 D11） |
| status | TINYINT | SMALLINT | 否 | 1 | 状态：0=禁用，1=启用 |
| pwd_changed | TINYINT | SMALLINT | 否 | 0 | 密码是否已由本人修改：0=未修改（首登强制改密），1=已修改 |
| entry_date | DATE | DATE | 是 | NULL | 入职日期 |
| last_login_time | DATETIME | TIMESTAMP | 是 | NULL | 最后成功登录时间 |
| remark | VARCHAR(255) | VARCHAR(255) | 是 | NULL | 备注 |
| position | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 岗位（员工档案属性，**权威事实**；自由文本，与 `hr_flow.position` 双写；存量未登记为 NULL；**V19 补列**，方案乙 U-07） |
| is_deleted | TINYINT | SMALLINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 更新时间（应用层维护） |
| phone_active | VARCHAR(20) | — | 是 | 生成列 | **MySQL 生成列**（`IF(is_deleted=0, phone, NULL) STORED`）：仅活跃行取 phone，否则 NULL；承载「手机号活跃唯一」（**V16**，PG 目录冻结不产出对应定义） |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_employee_username | username | 登录查询、账号查重 |
| idx_employee_phone | phone | 手机号查重、关键字筛选 |
| idx_employee_dept_id | dept_id | 部门归属统计、部门删除前校验 |
| idx_employee_station_id | station_id | 驿站归属统计、驿站删除前校验、二期包裹关联员工维度 |
| idx_employee_create_time | create_time | 默认排序（创建时间倒序） |
| **uk_employee_phone_active** | phone_active | **UNIQUE**，手机号**活跃唯一**（`is_deleted=0` 行唯一；NULL 可重复 → 已删号可复用；**V16** 新增，D7 显式例外） |

**为什么不再多建索引**：员工表 < 5000 行，任何组合条件在现有索引下都是毫秒级；`keyword` 为前模糊 `LIKE '%x%'` 不走索引（这是主动取舍，量级小可全扫，不为模糊搜索引入全文索引的复杂度）；`status` 单列区分度低，由其他条件组合过滤即可。索引克制是为写入性能与维护成本让路。

**D7 唯一性例外（V16，M-5）**：`phone` 原按决策 D7 仅由 Service 层「活跃查重」保证（无 DB 唯一索引）。注册为**公开端点**、并发高于后台，且 `phone` 为**登录标识**，重复号后果为按 phone `selectOne` 命中多行 → 目标账号登录/短信登录 **DoS**（非仅脏数据），故增设**活跃唯一**约束（生成列 `phone_active` + `uk_employee_phone_active`）——此为 D7 的**显式例外**（登记同 §10.2 体例）。DB 唯一约束为最终防线，Service 查重仅为友好报错；**迁移前须跑存量重复号预检且返回 0 行**（见第 11 章）。

**业务规则（Service 层）**：
- 新增/编辑：`username`、`phone` 活跃唯一（编辑排除自身）；归属部门须存在且未删除，归属驿站须存在、未删除且启用（停用驿站不可新归属，存量归属保留）；
- 角色白名单：一期接口仅接受 ADMIN / STAFF（STATION_ADMIN 请求返回 400）；
- 自我保护与最后管理员保护（见 [requirement.md](requirement.md) 5.4）；
- 禁用/删除/重置密码 → 删除对应 Redis 会话。

### 3.4 login_log（登录日志表）

**用途**：登录审计 + 看板「今日登录数」数据来源。

| 字段 | MySQL 类型 | PG 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| username | VARCHAR(30) | VARCHAR(30) | 否 | - | 尝试登录的账号名（登录失败时仍记录） |
| employee_id | BIGINT | BIGINT | 是 | NULL | 员工 ID（账号存在时记录，逻辑外键，历史留痕） |
| login_ip | VARCHAR(50) | VARCHAR(50) | 否 | '' | 客户端 IP（Nginx 透传 X-Forwarded-For 首个，见部署） |
| login_result | TINYINT | SMALLINT | 否 | - | 登录结果：0=失败，1=成功 |
| fail_reason | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 失败原因（账号密码错误 / 账号已禁用） |
| user_agent | VARCHAR(255) | VARCHAR(255) | 是 | NULL | 浏览器 UA（截断至 255） |
| login_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 登录时间 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_login_log_login_time | login_time | 看板「今日登录数」按日查询 |
| idx_login_log_employee_id | employee_id | 按人追溯登录历史 |

**设计说明**：
- 本表为日志表，**只插入、不更新、不删除**，故不设 `is_deleted` / `update_time`，业务时间即 `login_time`（统一时间字段约定的例外，理由：不可变数据无更新语义）；
- 增长预估年增 < 50 万行，现有索引可覆盖查询；归档策略（按年清理/迁移）二期评估，一期不做分区。

***

## 4. 参考DDL（脚本以本文件为唯一基准）

> A01/A02 任务产出的 Flyway 脚本必须与本节逐项一致；`V2__init_data.sql` 中的 BCrypt 散列生成方式见第 5 章。

### 4.1 MySQL 8（db/migration/mysql/V1__init_schema.sql）

```sql
-- ----------------------------------------------------------------
-- 快递驿站智汇系统 一期表结构（MySQL 8）
-- ----------------------------------------------------------------

CREATE TABLE `department` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父部门ID，0=根节点',
  `dept_name`   VARCHAR(50) NOT NULL COMMENT '部门名称',
  `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '同级排序（升序）',
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_department_parent_id` (`parent_id`),
  KEY `idx_department_dept_name` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（树形结构）';

CREATE TABLE `station` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`           VARCHAR(50)  NOT NULL COMMENT '驿站编码，活跃唯一，二期爬虫对接标识',
  `station_name`   VARCHAR(50)  NOT NULL COMMENT '驿站名称',
  `contact_person` VARCHAR(50)  DEFAULT NULL COMMENT '负责人姓名',
  `contact_phone`  VARCHAR(20)  DEFAULT NULL COMMENT '联系电话（出参脱敏）',
  `address`        VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_station_code` (`code`),
  KEY `idx_station_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站表';

CREATE TABLE `employee` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(30)  NOT NULL COMMENT '登录账号，活跃唯一，创建后不可修改',
  `password`        VARCHAR(100) NOT NULL COMMENT '密码散列（BCrypt $2a$ cost=10）',
  `real_name`       VARCHAR(50)  NOT NULL COMMENT '员工姓名',
  `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号，活跃唯一，出参脱敏',
  `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女',
  `dept_id`         BIGINT       DEFAULT NULL COMMENT '所属部门ID（逻辑外键 department.id）',
  `station_id`      BIGINT       DEFAULT NULL COMMENT '归属驿站ID（逻辑外键 station.id）',
  `role`            VARCHAR(20)  NOT NULL DEFAULT 'STAFF' COMMENT '角色：ADMIN/STATION_ADMIN(二期)/STAFF',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `pwd_changed`     TINYINT      NOT NULL DEFAULT 0 COMMENT '密码是否已由本人修改：0=未修改，1=已修改',
  `entry_date`      DATE         DEFAULT NULL COMMENT '入职日期',
  `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后成功登录时间',
  `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_employee_username` (`username`),
  KEY `idx_employee_phone` (`phone`),
  KEY `idx_employee_dept_id` (`dept_id`),
  KEY `idx_employee_station_id` (`station_id`),
  KEY `idx_employee_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工表（员工即账号）';

CREATE TABLE `login_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`     VARCHAR(30)  NOT NULL COMMENT '尝试登录的账号名',
  `employee_id`  BIGINT       DEFAULT NULL COMMENT '员工ID（账号存在时记录）',
  `login_ip`     VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '客户端IP（取 X-Forwarded-For 首个）',
  `login_result` TINYINT      NOT NULL COMMENT '登录结果：0=失败，1=成功',
  `fail_reason`  VARCHAR(50)  DEFAULT NULL COMMENT '失败原因（账号密码错误/账号已禁用）',
  `user_agent`   VARCHAR(255) DEFAULT NULL COMMENT '浏览器UA（截断至255）',
  `login_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_login_time` (`login_time`),
  KEY `idx_login_log_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表（只插不改不删）';
```

### 4.2 PostgreSQL（db/migration/postgresql/V1__init_schema.sql）

与 MySQL 版字段、索引一一对应，差异点：`SMALLINT` 替代 `TINYINT`、`TIMESTAMP` 替代 `DATETIME`、`INTEGER` 替代 `INT`、`GENERATED BY DEFAULT AS IDENTITY` 替代 `AUTO_INCREMENT`、注释用 `COMMENT ON`、索引名单独 `CREATE INDEX`。示意（以 employee 为例，其余三表同规则转换）：

```sql
CREATE TABLE employee (
  id              BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  username        VARCHAR(30)  NOT NULL,
  password        VARCHAR(100) NOT NULL,
  real_name       VARCHAR(50)  NOT NULL,
  phone           VARCHAR(20)  NOT NULL,
  gender          SMALLINT     NOT NULL DEFAULT 0,
  dept_id         BIGINT       DEFAULT NULL,
  station_id      BIGINT       DEFAULT NULL,
  role            VARCHAR(20)  NOT NULL DEFAULT 'STAFF',
  status          SMALLINT     NOT NULL DEFAULT 1,
  pwd_changed     SMALLINT     NOT NULL DEFAULT 0,
  entry_date      DATE         DEFAULT NULL,
  last_login_time TIMESTAMP    DEFAULT NULL,
  remark          VARCHAR(255) DEFAULT NULL,
  is_deleted      SMALLINT     NOT NULL DEFAULT 0,
  create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE employee IS '员工表（员工即账号）';
COMMENT ON COLUMN employee.username IS '登录账号，活跃唯一，创建后不可修改';
COMMENT ON COLUMN employee.password IS '密码散列（BCrypt $2a$ cost=10）';
-- ...其余字段注释与 MySQL 版一一对应（完整脚本由任务 A02 产出）

CREATE INDEX idx_employee_username     ON employee (username);
CREATE INDEX idx_employee_phone        ON employee (phone);
CREATE INDEX idx_employee_dept_id      ON employee (dept_id);
CREATE INDEX idx_employee_station_id   ON employee (station_id);
CREATE INDEX idx_employee_create_time  ON employee (create_time);
```

***

## 5. 初始化数据与版本管理（决策 D2）

### 5.1 Flyway 目录与配置

```text
hrm-server/src/main/resources/db/migration/
├── mysql/
│   ├── V1__init_schema.sql    4 张表结构
│   └── V2__init_data.sql      种子数据
└── postgresql/
    ├── V1__init_schema.sql
    └── V2__init_data.sql
```

配置（服务器侧外置配置，仓库 `env.example` 模板对应项）：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/{vendor}
```

**为什么用 `{vendor}`**：Flyway 原生占位符，按实际连接的数据库自动选择 `mysql/` 或 `postgresql/` 目录，切换数据库零代码改动。

**依赖注意**：Spring Boot 3 + Flyway 9/10 需额外引入 `flyway-mysql`（MySQL）与 `flyway-database-postgresql`（Flyway 10 起 PG 支持独立成模块）。

**纪律**：脚本一旦在任何环境执行过，禁止修改，只能新增 `V3+` 增量脚本（一期上线前允许修订 V1/V2）。

### 5.2 V2 种子数据

| 数据 | 内容 |
| ---- | ---- |
| 部门 | id=1，`总公司`（根节点） |
| 管理员 | id=1，username=`admin`，role=ADMIN，status=1，pwd_changed=0，dept_id=1，手机号 `13800000000`（占位，非真实数据） |

```sql
-- MySQL 版示意（PostgreSQL 版语法相同）
INSERT INTO department (id, parent_id, dept_name, sort_order) VALUES (1, 0, '总公司', 1);

INSERT INTO employee (id, username, password, real_name, phone, gender, dept_id, station_id, role, status, pwd_changed)
VALUES (1, 'admin', '{BCrypt散列，见下方说明}', '系统管理员', '13800000000', 0, 1, NULL, 'ADMIN', 1, 0);
```

**初始密码与散列生成规则（安全红线）**：
1. 初始密码为部署占位值 `change_me_admin_init_password`，实际值由部署者确定，**真实密码只存服务器侧/线下记录，绝不写入 Git 与文档**；
2. 种子脚本中的散列必须用与后端一致的 `spring-security-crypto` `BCryptPasswordEncoder`（`$2a$`，cost=10）生成——生成工具随任务 B01 的工程提供（临时 main 方法或测试类），散列固化进 V2 后提交（BCrypt 单向散列可入库，不构成泄露）；
3. `pwd_changed=0` 保证首登强制改密（双保险，见 [requirement.md](requirement.md) 决策 D1）。

**为什么种子数据不算「业务数据」**：部门「总公司」与管理员账号属于系统初始化结构（无业务含义），符合「仓库不放真实业务数据」红线；真实员工档案一律通过页面或导入功能录入。

### 5.3 sql/schema 快照同步（仓库规范 6.1）

`hrm-dev/sql/schema/mysql/init.sql` 存放「当前最新结构的完整快照（含注释）」，供评审与 DBA 查看；**执行来源唯一为 Flyway 目录**。约束：任何 Flyway 新增结构脚本（V3+）必须同步刷新快照（一期由任务 A05 落实，后续变更沿用）。

- **当前状态（v2.5）**：`mysql/init.sql` == Flyway `V1 + V2(种子) + V3..V21` 的最终结构态（共 **43** 张表）；V3~V13 已随第 8 章落库，V14/V15 随第 10 章落库，V16~V19 随第 11 章产出（**尚未执行，C 档**），V20（薪资结算自动化）随 §8.6.5~§8.6.7、**V21（薪资结算自动化 v1.2/v1.3/v1.4 增量）** 随 §8.6.6~§8.6.8 产出（**尚未执行，C 档**）。
- **`postgresql/init.sql` 冻结**：不再维护、不再随变更刷新，仅保留避免历史引用断裂（`spring.flyway.locations={vendor}` 只会选中 `mysql/`）；**本期 V16~V21 不产出 pg 脚本与 pg 快照**（与 `registration-design.md` §0.4/§7、`payroll-automation-design.md` §3 一致）。
- **快照与迁移一致性核对结论（v2.2）**：`mysql/init.sql` 与 `V1 + V3..V19` 表/列/索引逐项比对一致——含 `employee` 新列 `phone_active`（生成列）/ `position` + 唯一键 `uk_employee_phone_active`、`hr_flow` 新列 `source`（`NOT NULL DEFAULT 'ADMIN'`）、新表 `employee_registration`（21 列 + 1 唯一 + 3 普通索引）。
- **快照与迁移一致性核对结论（v2.4）**：`mysql/init.sql` 与 `V1 + V3..V20` 表/列/索引逐项比对一致——新增 `station_payroll_setting`（10 列 + 1 普通索引）、`payroll_log`（14 列 + 2 普通索引）、`payroll_run`（**17 列** + 1 唯一 `uk_payroll_run_claim` + 2 普通索引），`payroll` 表内联补 `paid_by_id` / `paid_by_name` / `paid_time` 3 列并更新 `status` 列 8 态 COMMENT；`payroll_run` 已按设计 v1.1 落 `claim_key` / `skip_code`。**静态比对，未实跑**（`SHOW CREATE TABLE` 收敛到服务器阶段，见 §9.4 U-13）。
- **快照与迁移一致性核对结论（v2.5 / V21）**：`mysql/init.sql` 与 `V1 + V3..V21` 表/列/索引逐项比对一致——`payroll_log` 表内联补 `employee_id` / `month` 两列 + `idx_payroll_log_emp_month`（表内 **16 列 + 3 普通索引**）；`payroll_run` 表内联补 `attempt_date`（`NOT NULL`）+ 唯一键 `uk_attempt (station_id, target_month, attempt_date)`，**移除**冗余普通索引 `idx_payroll_run_station_month`（被 `uk_attempt` 最左前缀覆盖，见 §8.6.7），`skip_code` 列 COMMENT 去 `EXHAUSTED`（表内 **18 列 + 2 唯一 + 1 普通索引**）；新增 `station_payroll_setting_log`（**10 列 + 1 普通索引**，追加型无 `is_deleted` / `update_time`）。**静态比对，未实跑**（收敛到服务器阶段，见 §9.4 U-14）。

***

## 6. 二期 / 三期扩展预留

### 6.1 为什么一期结构能承接二期 20 万包裹数据

1. `station.code` 已作为对接键设计，二期爬虫任务下发/数据回推直接使用，驿站表零变更；
2. `employee.station_id` 已建索引，二期「按驿站划分数据可见范围」（站长看本站、员工看本站）直接复用归属关系；
3. 员工逻辑删除保留 id 稳定性，二期包裹表引用的取件员工 id 不因离职而失联。

### 6.2 二期新增表预告（概要，届时专项设计评审）

> **v2.0 更新**：本节预告已在第 8 章正式落地（`parcel` / `sync_task` / `sync_log` 等）。
> 本章保留为设计演进记录；**具体字段与索引以第 8 章为准**。

| 预告表 | 用途 | 关键设计要点（初步） |
| ---- | ---- | ---- |
| parcel | 包裹数据 | 复合索引 `(station_id, status)`；唯一键 `station_id + waybill_no` 防重复入库；同步批次号字段；避免 `SELECT *` 与深度分页（游标/延迟关联） |
| sync_task | 同步任务 | 任务状态机（待领取/执行中/完成/失败）；供微主机轮询 `/api/v1/crawler/*`（API Key 认证） |
| sync_log | 同步日志 | 每批次抓取结果留痕，失败重试依据 |

### 6.3 三期预告

工单表（类型/流转状态机/SLA）、通知记录表；`employee.role` 届时正式启用 STATION_ADMIN 分权。

## 7. M11 请假与运行日志表（Demo 增量）

> 来源：`docs/demo-leave-design.md`。Demo 阶段这三张表只存在于 Mock 持久化桶
> （`hrm-demo/src/shared/mock/{leaveStore,clientLogStore}.js`，localStorage 整表快照）；
> 下表为后端建表口径提案，命名与一期规范一致（snake_case、主键 `id`、`create_time/update_time`、索引 `idx_表名_字段`）。

### 7.1 leave_request（请假申请单）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | BIGINT PK | 自增 |
| employee_id | BIGINT | 申请人（逻辑外键 → employee.id） |
| station_id | BIGINT | 申请时归属驿站（初审站长判定依据，见设计规范 Q9） |
| leave_type | VARCHAR(20) | 假别枚举（`LEAVE_TYPE` 键） |
| start_date / end_date | DATE | 起止日期 |
| start_period / end_period | VARCHAR(2) | 半天粒度 AM / PM |
| reason | VARCHAR(200) | 请假事由 2–200 字 |
| natural_days | DECIMAL(4,1) | 自然天数（半天粒度 0.5） |
| counted_days | DECIMAL(4,1) | 申请时预估的计薪天数（逐日查排班） |
| counted_days_snapshot | JSON | 终审通过时的计薪天数快照 `{naturalDays, countedDays, scheduleDigest}` |
| status | VARCHAR(20) | 6 态：PENDING_STATION / PENDING_BOSS / APPROVED / REJECTED / CANCELLED / REVOKED |
| reject_stage | VARCHAR(10) NULL | STATION / BOSS（仅 REJECTED 有值） |
| origin_id | BIGINT NULL | 驳回后重提指向的原单（T9） |
| station_approver_id / station_approve_time / station_approve_remark | — | 初审（与终审分列，避免相互覆盖） |
| approver_id / approve_time / approve_remark | — | 终审 |
| cancel_by_id / cancel_time | — | 申请人撤销 |
| revoker_id / revoke_time / revoke_reason | — | 审批人撤回 |
| apply_time / update_time | DATETIME | |

索引：`idx_leave_request_employee_status (employee_id, status)`、`idx_leave_request_station_status (station_id, status)`、`idx_leave_request_date (start_date, end_date)`。

### 7.2 leave_log（请假操作留痕，D6 审计）

`id / leave_id / action / operator_id / operator_name / operator_role / time / from_status / to_status / before(JSON) / after(JSON) / remark`；
索引 `idx_leave_log_leave (leave_id, time)`。`action` 取值见 `dict.LEAVE_LOG_ACTION`（含 `NOTIFY_SKIP` 排障留痕）。

### 7.3 client_log（前端运行日志，D6）

`id / time / level / source / employee_id NULL / route / message / stack / method / path / status / code / duration / ua / count / first_time / last_time`；
索引 `idx_client_log_time (time)`、`idx_client_log_level (level)`。
**写入前必须白名单脱敏**：token / 密码 / 身份证 / 手机号全量 / 银行卡 / 请求响应体原文一律不入库（见 api.md 7.5）。

***

## 8. 二期扩展表结构设计（P1~P10，共 33 表）

> 权威依据：[server-architecture.md](server-architecture.md) §4.1（表清单）、§4.2（字段与索引策略）、
> §4.3（`parcel` 大表策略）、§4.4（Flyway 版本规划）、§4.6（面向频繁迭代的 ADR-04）、§5.2（入库可热改）；
> [algo-hrm-server.md](algo-hrm-server.md) §11（参数外置）、§13（S7 索引建议）。
> 字段真源：`hrm-dev/hrm-demo/src/shared/mock/` 各 store + `constants/dict.js`。
> DDL 落位：`hrm-server/src/main/resources/db/migration/mysql/V3..V13`；快照 `sql/schema/mysql/init.sql`。

### 8.0 通用约定（本章所有表适用）

1. **命名**：表 / 字段 `snake_case`；主键统一 `id`（`BIGINT AUTO_INCREMENT`）；索引 `idx_表名_字段`；
   时间 `create_time` / `update_time`；逻辑删除 `is_deleted`（0=否，1=是）。
2. **不建物理外键**（决策 D6）：本章「逻辑关系」一律为 Service 层校验；**不建数据库唯一索引**（决策 D7），
   「活跃唯一」由 Service 层查重 + 普通索引加速（架构 §4.6(2)）。
3. **时间填充**（决策 D8）：`create_time` / `update_time` 仅 `DEFAULT CURRENT_TIMESTAMP` 兜底，
   由应用层 `MetaObjectHandler` 填充，不使用 `ON UPDATE CURRENT_TIMESTAMP`。
4. **追加型日志 / 留痕表例外**：`client_log`、`hr_salary_log`、`leave_log`、`work_order_timeline`、
   `work_order_transfer`、`sync_task_log`、`payroll_log`（§8.6.6，v2.3 新增）、`station_payroll_setting_log`（§8.6.8，v2.5 新增） **不设 `is_deleted` / `update_time`**（不可变数据无更新与删除语义，
   沿用 `login_log` 的例外约定）；其业务时间字段为 `time` / `create_time` / `log_time` / `transfer_time`。
5. **面向频繁迭代（ADR-04）**：只加列不删列、不改列类型；新列可空或带默认值；加列优先
   `ALGORITHM=INSTANT/INPLACE`；索引变更附回滚脚本；不用存储过程 / 触发器 / 物理外键。
6. **JSON 列使用边界**：仅用于「低频读取、结构多变」字段，**高频筛选 / 排序 / 聚合字段一律显式列 + 索引**。
   本章 JSON 列全集：`attendance_rule.wifi_list`、`attendance_rule.check_periods`、`kpi_score.metric_detail`、
   `hr_salary.allowances`、`hr_salary_log.allowances`、`payroll.rule_snapshot`、`payroll_rule_item.params`、
   `leave_request.counted_days_snapshot`、`leave_log.before/after`（§7.2 既有）、`payroll_log.before/after`（§8.6.6，v2.3 新增，与 `leave_log` 同口径）、`station_payroll_setting_log.before/after`（§8.6.8，v2.5 新增，配置变更白名单快照）、`sync_config_item.constraints`、
   `sync_config_option.extra_attrs`、`sync_config_option.legacy_codes`（后两者为本章新增，见 §8.12 核对项）。
7. **禁止 `SELECT *` 友好化**：列表 / 统计查询所需字段均落入对应索引（见 §8.11 覆盖映射），
   禁止把核心字段塞 JSON、禁止为模糊搜索滥引全文索引。

### 8.1 P1 · 运行日志

#### 8.1.1 `client_log`（V3）— 前端运行日志

**用途**：三端上报、仅 ADMIN 查看的排障日志；支持指纹去重聚合（`count` / `first_time` / `last_time`）。
**字段与语义**：见 §7.3（既有定义，本章不重复）；DDL 见 `V3__client_log.sql`。
**索引**：`idx_client_log_time (time)`、`idx_client_log_level (level)`。
**逻辑关系**：`employee_id` → `employee.id`（逻辑外键，未登录为空；历史留痕不校验）。
**查询走索引**：列表按 `time` 倒序 + `level` 筛选（`idx_client_log_time` / `idx_client_log_level`）；
`source` / `employeeId` / 时间区间为附加过滤；`keyword` 为 `message` 前缀模糊 `LIKE`，不建索引（日志表量级可控）。
**例外**：追加型，只插不改（`count` 累加）不删（清空=物理删除），无 `is_deleted` / `update_time`。

### 8.2 P2 · 通知

#### 8.2.1 `notification`（V4）— 站内通知

**用途**：站内信（系统联动 + 手工发布）。系统联动含工单指派/流转、同步失败、请假（type 5/6）、薪资（type 7/8/9，V21 段 4 补注）；手工发布由 ADMIN 按范围扇出。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| employee_id | BIGINT | 否 | - | 接收人（逻辑外键 employee.id） |
| type | TINYINT | 否 | - | 通知类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果 7=工资单待审核（→管理员） 8=工资单已发布（→员工本人） 9=工资单异议退回（→管理员）；7/8/9 由 V21 段 4 补注（仅注释、不改列型） |
| title | VARCHAR(100) | 否 | - | 标题（1-100 字） |
| content | VARCHAR(500) | 否 | '' | 内容（1-500 字） |
| biz_type | VARCHAR(32) | 是 | NULL | 跳转类型：`work_order` / `sync_task` / `leave`（公告为空） |
| biz_id | BIGINT | 是 | NULL | 跳转对象 ID |
| is_read | TINYINT | 否 | 0 | 是否已读：0=未读，1=已读 |
| read_time | DATETIME | 是 | NULL | 已读时间 |
| is_published | TINYINT | 否 | 0 | 来源：0=系统联动，1=手工发布 |
| publisher_id | BIGINT | 是 | NULL | 发布人（手工发布，逻辑外键 employee.id） |
| publisher_name | VARCHAR(50) | 是 | NULL | 发布人姓名快照 |
| publish_scope | VARCHAR(10) | 是 | NULL | 发布范围：`ALL` / `STATION` / `EMPLOYEE`（系统联动为空） |
| is_deleted | TINYINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time / update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 应用层维护 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_notification_employee_read | (employee_id, is_read) | 本人未读计数 + 列表 isRead 筛选 |
| idx_notification_employee_time | (employee_id, create_time) | 本人列表按创建时间倒序 |

**逻辑关系**：`employee_id` / `publisher_id` → `employee.id`；`biz_id` 指向 `work_order` / `sync_task` / `leave_request` 的 id（前端点击跳转，非外键）。
**查询走索引**：`GET /notifications`（employee_id + create_time）、`unread-count`（employee_id + is_read）、`read-all`（employee_id + is_read）。

### 8.3 P3 · 考勤与排班

#### 8.3.1 `attendance_rule`（V5）— 打卡规则（一驿一条）

**用途**：驿站打卡规则；`check_periods`（时段明细）是打卡时间判定的唯一真源，`check_frequency` 只给段数，`work_start_time/work_end_time` 为派生值。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 station.id，一驿一条，活跃唯一） |
| rule_name | VARCHAR(50) | 否 | - | 规则名称 |
| enable_wifi / enable_location / enable_time_window | TINYINT | 否 | 1 | 启用 WiFi / 定位 / 时间窗校验：0/1 |
| match_mode | VARCHAR(10) | 否 | 'ALL' | 校验项组合：`ALL`=全部满足，`ANY`=任一满足 |
| wifi_list | JSON | 是 | NULL | WiFi 白名单 `[{ssid,bssid}]`（低频读取） |
| longitude / latitude | DECIMAL(10,6) | 是 | NULL | 电子围栏中心经纬度 |
| radius | INT | 是 | NULL | 围栏半径（米） |
| check_frequency | INT | 否 | 2 | 每日打卡次数：2=单时段，4=双时段 |
| check_periods | JSON | 是 | NULL | 时段明细 `[{name,startTime,endTime}]`（唯一真源） |
| allow_early_min / allow_late_min | INT | 否 | 30 / 60 | 时间窗提前量 / 延后量（分钟） |
| work_start_time / work_end_time | VARCHAR(5) | 是 | NULL | 派生：首段开始 / 末段结束（HH:mm，可 24:00） |
| late_threshold_min / early_leave_threshold_min | INT | 否 | 30 | 迟到 / 早退判定阈值（分钟） |
| status | TINYINT | 否 | 1 | 状态：0=停用，1=启用 |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：`idx_attendance_rule_station (station_id)`（规则定位 + 活跃唯一查重）。
**逻辑关系**：`station_id` → `station.id`（1:1）。
**查询走索引**：按 `station_id` 取规则（`idx_attendance_rule_station`）。

#### 8.3.2 `attendance_shift`（V5）— 班次

**用途**：驿站班次（早/中/晚），供排班与打卡判定引用；`end_time` 允许 `24:00` 表示跨零点收班。
**字段**：`id` / `station_id`(逻辑外键 station.id) / `shift_name` VARCHAR(20) / `start_time` VARCHAR(5) / `end_time` VARCHAR(5) / `color` VARCHAR(20) NULL / `rest_minutes` INT(默认60) / `status` TINYINT(0=停用,1=启用) / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_attendance_shift_station (station_id)`。
**逻辑关系**：`station_id` → `station.id`；被 `attendance_schedule.shift_id` 引用（删除前 Service 校验是否被排班引用）。
**查询走索引**：按 `station_id` 列班次（`idx_attendance_shift_station`）。

#### 8.3.3 `attendance_schedule`（V5 / V22 活跃唯一键）— 排班

**用途**：按周排班矩阵（员工 × 日期 × 班次）；**一天支持多班次**（同员工同天可并存不同 `shift_id`）。
**字段**：`id` / `station_id`(逻辑外键 station.id) / `employee_id`(逻辑外键 employee.id) / `work_date` DATE / `shift_id`(逻辑外键 attendance_shift.id) / `is_deleted` / `create_time` / `update_time` / **`active_shift_key`（V22 生成列）**。
**`active_shift_key`（V22，生成列 · 活跃唯一）**：`VARCHAR(64)`，`GENERATED ALWAYS AS (IF(is_deleted=0, CONCAT(employee_id,'|',work_date,'|',shift_id), NULL)) STORED`。
「**活跃唯一**」语义：`is_deleted=1`（软删）行生成列为 `NULL`，唯一索引对 `NULL` 不去重 → **软删行不占键**，「清空班次（软删）→ 再排同班次」不再撞 1062；同员工同天不同 `shift_id` 因拼接含 `shift_id` 可并存。**但活跃行**同 `(employee_id, work_date, shift_id)` 仍被 DB 原子拒绝（1062）—— 即多班次的「活跃唯一」最终防线 + 并发防线。
**为何用生成列式部分唯一（评审 M-1）**：`AttendanceSchedule` 实体带 `@TableLogic`（**逻辑删**，非架构/算法此前所述「物理删」），裸三元 `UNIQUE` 会与软删互斥 → 改用本项目既有手法（对齐 §11.2 `employee.phone_active` / V16）。
**索引**：

| 索引名 | 类型 | 字段 | 用途 |
| ---- | ---- | ---- | ---- |
| idx_attendance_schedule_station_date | 普通 | (station_id, work_date) | 按驿站取周排班矩阵 |
| idx_attendance_schedule_emp_date | 普通 | (employee_id, work_date) | 员工排班查询 + 活跃查重（员工+日期） |
| uk_attendance_schedule_active_shift | **UNIQUE**（V22） | (active_shift_key) | 「活跃唯一」最终防线：活跃行 `(employee_id, work_date, shift_id)` 唯一 |

**冗余索引取舍（V22 结论：两个普通索引均保留，不删）**：新唯一键建在**单个生成列表达式**上，其最左前缀即该表达式本身，**不能**被 `WHERE station_id / employee_id / work_date` 使用（表达式索引不按组成列下钻）→ 既有两个普通索引**均不被覆盖**，全部保留。对照 §8.6.7 `uk_attempt`（建在原始复合列上、其最左前缀可吃普通索引并删之），本处前提不同，故不删任何索引。
**逻辑关系**：`station_id`→`station.id`；`employee_id`→`employee.id`；`shift_id`→`attendance_shift.id`。
**查询走索引**：周矩阵按 `(station_id, work_date)`；「我的排班」与计薪天数逐日查排班按 `(employee_id, work_date)`。
**唯一键例外**：`uk_attendance_schedule_active_shift` 为决策 D7（Service 查重 + 普通索引）的**显式例外**（多班次并发 + 软删复用语义须 DB 硬防线），登记见 §9.1 Q-DB-11；生成列式部分唯一与 `employee`（§11.2）、`payroll_run`（§8.6.7）同属既有范式。

#### 8.3.4 `attendance_record`（V5）— 打卡记录（打卡事实）

**用途**：打卡事实；出勤口径（应到=排班人数、实到=非 ABNORMAL 上班卡）在 Service 聚合。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| employee_id / station_id | BIGINT | 否 | - | 员工 / 驿站（逻辑外键） |
| work_date | DATE | 否 | - | 工作日期 |
| period_index | INT | 否 | 0 | 时段序号（从 0 起） |
| period_name | VARCHAR(20) | 是 | NULL | 时段名快照 |
| check_type | VARCHAR(5) | 否 | - | 打卡类型：`ON`=上班卡，`OFF`=下班卡 |
| check_time | DATETIME | 否 | - | 打卡时间 |
| status | VARCHAR(16) | 否 | - | 打卡状态：`NORMAL` / `LATE` / `EARLY_LEAVE` / `ABNORMAL` |
| source | VARCHAR(10) | 否 | 'NORMAL' | 来源：`NORMAL`=正常打卡，`MAKEUP`=补卡补录 |
| check_mode | VARCHAR(20) | 是 | NULL | 命中校验项：`WIFI` / `LOCATION` / `WIFI+LOCATION`（补卡为空） |
| wifi_ssid | VARCHAR(64) | 是 | NULL | 打卡时 WiFi SSID |
| wifi_matched | TINYINT | 是 | NULL | WiFi 是否命中：0/1（补卡为空） |
| longitude / latitude | DECIMAL(10,6) | 是 | NULL | 打卡经纬度 |
| distance | DECIMAL(10,1) | 是 | NULL | 距围栏中心距离（米） |
| location_matched | TINYINT | 是 | NULL | 定位是否命中：0/1（补卡为空） |
| remark | VARCHAR(255) | 是 | NULL | 备注（迟到/早退/异常原因） |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_attendance_record_emp_date | (employee_id, work_date) | 员工考勤记录 / 逐日统计 + 槽位去重 |
| idx_attendance_record_station_date | (station_id, work_date) | 按驿站取记录（列表/概况） |

**逻辑关系**：`employee_id`→`employee.id`；`station_id`→`station.id`。
**查询走索引**：记录列表按 `(station_id|employee_id, work_date)` + `status` 附加过滤；`check_type`/`period_index` 为去重槽位判定，由 `(employee_id, work_date)` 覆盖。

#### 8.3.5 `attendance_makeup`（V5）— 补卡申请

**用途**：补卡申请与审批；通过后回写 `attendance_record`（`source=MAKEUP`）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| employee_id / station_id | BIGINT | 否 | - | 申请人 / 驿站（逻辑外键） |
| work_date | DATE | 否 | - | 补卡日期 |
| period_index | INT | 否 | 0 | 时段序号 |
| period_name | VARCHAR(20) | 是 | NULL | 时段名快照 |
| check_type | VARCHAR(5) | 否 | - | 打卡类型：`ON` / `OFF` |
| reason | VARCHAR(200) | 否 | - | 补卡理由 |
| status | VARCHAR(16) | 否 | 'PENDING' | 状态：`PENDING` / `APPROVED` / `REJECTED` |
| apply_time | DATETIME | 否 | CURRENT_TIMESTAMP | 申请时间 |
| approver_id | BIGINT | 是 | NULL | 审批人（ADMIN，逻辑外键 employee.id） |
| approve_time | DATETIME | 是 | NULL | 审批时间 |
| approve_remark | VARCHAR(200) | 是 | NULL | 审批意见 |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：`idx_attendance_makeup_emp_status (employee_id, status)`、`idx_attendance_makeup_station_status (station_id, status)`。
**逻辑关系**：`employee_id` / `station_id` / `approver_id` → 相应 id（逻辑外键）。
**查询走索引**：员工端（employee_id + status）、管理端（station_id + status）；日期区间为附加过滤。

### 8.4 P4 · KPI 考核

#### 8.4.1 `kpi_metric`（V6）— 指标配置（业务口径可热改）

**用途**：KPI 指标配置（权重 / 目标 / 评分规则 / 适用角色 / 启用）；算分时快照进 `kpi_score.metric_detail`。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| metric_key | VARCHAR(40) | 否 | - | 指标键（活跃唯一） |
| metric_name | VARCHAR(50) | 否 | - | 指标名 |
| metric_type | VARCHAR(20) | 否 | - | 类型：`PARCEL` / `PICKUP` / `COMPLAINT` / `ATTENDANCE` / `SERVICE` / `WORK_ORDER` / `TRAINING` / `OTHER` |
| weight | INT | 否 | 0 | 权重（启用指标合计须 100） |
| target_value | DECIMAL(12,2) | 是 | NULL | 目标值 |
| unit | VARCHAR(10) | 是 | NULL | 单位 |
| direction | VARCHAR(5) | 否 | 'UP' | 方向：`UP`=越高越好，`DOWN`=越低越好 |
| score_mode | VARCHAR(10) | 否 | 'LINEAR' | 评分规则：`LINEAR` / `TIERED` / `BINARY` |
| full_score | DECIMAL(6,2) | 否 | 100 | 单项满分 |
| role_scope | VARCHAR(60) | 是 | NULL | 适用角色，逗号分隔（`ADMIN` / `STATION_ADMIN` / `STAFF`），空=全员 |
| enabled | TINYINT | 否 | 1 | 启用：0=停用，1=启用 |
| sort_order | INT | 否 | 0 | 排序 |
| remark | VARCHAR(255) | 是 | NULL | 备注 |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：`idx_kpi_metric_key (metric_key)`（查重）、`idx_kpi_metric_enabled_sort (enabled, sort_order)`（列指标按序）。
**逻辑关系**：被 `kpi_score.metric_detail` 快照引用（metricKey/metricId 冗余进 JSON，不建外键）。
**查询走索引**：列表按 `(enabled, sort_order)`；键查重按 `metric_key`。
**设计说明**：`score_mode`+`full_score` 由 Mock `scoreRule{mode,fullScore}` 拆列；`role_scope` 由数组串化为逗号串（低频，Service 与数组互转）——避免核心配置进 JSON（架构 §4.6(3)）。

#### 8.4.2 `kpi_score`（V6）— 月度评分（一员工一账期一行，含算分快照）

**用途**：按月按员工汇总；`metric_detail` 快照逐指标算分（目标/实际/达成率/单项分/加权分/评分规则），支持明细页逐项解释。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| employee_id | BIGINT | 否 | - | 员工（逻辑外键 employee.id） |
| station_id | BIGINT | 是 | NULL | 统计时归属驿站（范围收敛/排行用，逻辑外键 station.id） |
| month | CHAR(7) | 否 | - | 考核月份 `yyyy-MM` |
| total_score | DECIMAL(5,1) | 否 | 0 | 加权总分 |
| achievement_rate | DECIMAL(6,4) | 否 | 0 | 平均达成率 |
| level | VARCHAR(16) | 是 | NULL | 等级：`EXCELLENT` / `GOOD` / `PASS` / `IMPROVE` |
| metric_count | INT | 否 | 0 | 参与指标数 |
| weight_sum | INT | 否 | 0 | 参与权重合计 |
| metric_detail | JSON | 是 | NULL | 逐指标算分快照（低频读取） |
| calculate_time | DATETIME | 是 | NULL | 算分时间 |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_kpi_score_emp_month | (employee_id, month) | 员工月度明细 + 活跃唯一查重（员工+月份） |
| idx_kpi_score_month_station | (month, station_id) | 月度列表 / 非 ADMIN 静默收敛 |
| idx_kpi_score_month_score | (month, total_score) | 排名榜按总分排序 |

**逻辑关系**：`employee_id`→`employee.id`；`station_id`→`station.id`。
**查询走索引**：`scores`（month + station）、`ranking`（month + total_score DESC）、`{employeeId}`（employee_id + month）。
**设计说明**：`station_id` 为范围收敛所需必要列（架构 §4.2 未列，见 §8.12 核对项）。

### 8.5 P5 · 人事

#### 8.5.1 `hr_profile`（V7）— 人事档案（员工 1:1）

**用途**：合同 / 试用期 / 社保基数 / 学历 / 紧急联系人 / 银行卡；**离职判定以 `leave_date` 为准**（非 `employee.status`）。
**字段**：`id` / `employee_id`(逻辑外键 employee.id，1:1，活跃唯一) / `education` VARCHAR(20)（`MASTER`/`BACHELOR`/`COLLEGE`/`HIGH_SCHOOL`）/ `contract_type` VARCHAR(20)（`FIXED_TERM`/`NON_FIXED_TERM`/`INTERN`/`DISPATCH`）/ `contract_start` DATE NULL / `contract_end` DATE NULL / `probation_months` INT(0) / `probation_end` DATE NULL / `regular_date` DATE NULL / `social_security_base` DECIMAL(12,2) NULL / `emergency_contact_name` VARCHAR(50) NULL / `emergency_contact_phone` VARCHAR(20) NULL（脱敏）/ `emergency_contact_relation` VARCHAR(20) NULL / `bank_name` VARCHAR(50) NULL / `bank_account` VARCHAR(32) NULL（脱敏）/ `leave_date` DATE NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_hr_profile_employee_id (employee_id)`（详情 + 活跃唯一查重）。
**逻辑关系**：`employee_id` → `employee.id`（1:1）。
**查询走索引**：按 `employee_id` 取档案（`idx_hr_profile_employee_id`）。

#### 8.5.2 `hr_salary`（V7）— 当前定薪（员工 1:1）

**用途**：当前定薪（基本工资 / 岗位工资 / 绩效基数 / 津贴项），是财务 FIXED 规则项的唯一取数来源。
**字段**：`id` / `employee_id`(1:1，活跃唯一) / `basic_salary` DECIMAL(12,2)(0) / `post_salary` DECIMAL(12,2)(0) / `performance_base` DECIMAL(12,2)(0) / `allowances` JSON(`[{key,name,amount}]`) / `allowances_total` DECIMAL(12,2)(0) / `total_salary` DECIMAL(12,2)(0) / `effective_date` DATE NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_hr_salary_employee_id (employee_id)`。
**逻辑关系**：`employee_id` → `employee.id`（1:1）；被财务 `payroll_item`（FIXED/KPI 项）取数。
**查询走索引**：按 `employee_id` 取当前定薪。

#### 8.5.3 `hr_salary_log`（V7）— 调薪留痕（追加型）

**用途**：调薪与入职定薪留痕，只增不改；当前定薪 = 最新生效一条。
**字段**：`id` / `employee_id` / `change_type` VARCHAR(20)（`ENTRY`=入职定薪，`ADJUST`=调薪）/ `basic_salary` / `post_salary` / `performance_base` / `allowances` JSON / `allowances_total` / `total_salary`（均 DECIMAL(12,2)）/ `effective_date` DATE NULL / `reason` VARCHAR(200) NULL / `operator_id` BIGINT NULL / `operator_name` VARCHAR(50) NULL（快照）/ `create_time`。
**索引**：`idx_hr_salary_log_emp (employee_id, effective_date)`。
**逻辑关系**：`employee_id` / `operator_id` → `employee.id`。
**查询走索引**：某员工调薪历史按 `(employee_id, effective_date)` 倒序。
**例外**：追加型，无 `is_deleted` / `update_time`。

#### 8.5.4 `hr_flow`（V7）— 入职/离职流程（flow_type 区分）

**用途**：入职（`ONBOARDING`）与离职（`OFFBOARDING`）共用一张表；`steps` 拆到 `hr_flow_step`，出参 Service 组装回 `steps[]`。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| flow_type | VARCHAR(16) | 否 | - | `ONBOARDING` / `OFFBOARDING` |
| flow_no | VARCHAR(40) | 否 | - | 流程编号（活跃唯一） |
| candidate_name | VARCHAR(50) | 是 | NULL | 候选人姓名（入职） |
| employee_id | BIGINT | 是 | NULL | 员工（离职；入职建档后回填） |
| phone | VARCHAR(20) | 是 | NULL | 联系电话（入职） |
| gender | TINYINT | 是 | NULL | 0=未知，1=男，2=女 |
| education | VARCHAR(20) | 是 | NULL | 学历（入职） |
| dept_id / station_id | BIGINT | 是 | NULL | 部门 / 驿站（逻辑外键） |
| position | VARCHAR(50) | 是 | NULL | 岗位 |
| role | VARCHAR(20) | 是 | NULL | `STAFF` / `STATION_ADMIN` |
| expected_entry_date | DATE | 是 | NULL | 预计入职日期（入职） |
| type | VARCHAR(20) | 是 | NULL | 离职类型：`RESIGN` / `DISMISS` / `RETIRE`（契约字段 type） |
| reason | VARCHAR(200) | 是 | NULL | 离职原因 |
| last_work_date | DATE | 是 | NULL | 最后工作日（离职） |
| settlement_payroll_id | BIGINT | 是 | NULL | 结算单 ID（SETTLEMENT 回填，逻辑外键 payroll.id） |
| settlement_payroll_no | VARCHAR(40) | 是 | NULL | 结算单号快照 |
| settlement_amount | DECIMAL(12,2) | 是 | NULL | 结算金额快照 |
| leave_date | DATE | 是 | NULL | 离岗日期（LEAVE 步骤写入） |
| status | VARCHAR(16) | 否 | 'IN_PROGRESS' | `IN_PROGRESS` / `COMPLETED` / `REJECTED` |
| reject_reason | VARCHAR(200) | 是 | NULL | 驳回原因 |
| rejected_by | VARCHAR(50) | 是 | NULL | 驳回人姓名快照 |
| rejected_time | DATETIME | 是 | NULL | 驳回时间 |
| current_step_key | VARCHAR(30) | 是 | NULL | 当前待办步骤键 |
| remark | VARCHAR(255) | 是 | NULL | 备注 |
| operator_id | BIGINT | 是 | NULL | 创建人（逻辑外键 employee.id；**V14 补列**） |
| operator_name | VARCHAR(50) | 是 | NULL | 创建人姓名快照（**V14 补列**） |
| source | VARCHAR(16) | 否 | 'ADMIN' | 业务来源：`ADMIN`=后台创建，`SELF_REGISTER`=员工自助注册（**V18 补列**，M-9 来源留痕） |
| is_deleted / create_time / update_time | — | — | — | 通用约定；V14 两列以列尾追加（列序在 `update_time` 之后），V18 `source` 再列尾追加 |

**索引**：`idx_hr_flow_no (flow_no)`、`idx_hr_flow_type_status (flow_type, status)`、`idx_hr_flow_employee (employee_id)`。
**逻辑关系**：`employee_id` / `dept_id` / `station_id` → 相应 id；`settlement_payroll_id` → `payroll.id`（逻辑外键，结算单由领域事件创建）；`operator_id` → `employee.id`（创建人，逻辑外键）。
**查询走索引**：入职/离职列表按 `(flow_type, status)` + 编号/姓名关键字；员工查在职流程按 `employee_id`。
**V14 变更（表结构缺口修复）**：V7 建表漏建创建人两列，`HrFlow.operatorId/operatorName` 原以 `@TableField(exist = false)` 规避（见 `docs/update-log.md`「服务端编译修复」条目）。V14 `ALTER TABLE ... ADD COLUMN` 可空补列（INSTANT，无锁无重建），语义对齐 `hr_flow_step.operator_id/operator_name` 与 Mock `hrStore.js`（`createOnboarding/createOffboarding` 写入 `operator.id`/`operator.real_name`，`toFlowVO` 回填出参）。**联动项**：补列后由后端工程师移除 `HrFlow` 两字段的 `exist = false`（含 2 处 `TODO(扩展)` 注释），本角色不改 Java 源码。
**V18 变更（来源留痕 M-9）**：`ALTER TABLE hr_flow ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'ADMIN'`（列尾追加、INSTANT）。存量行（全部为后台创建）由 `DEFAULT` 一次性回填为 `ADMIN`，与业务事实一致、无需额外 UPDATE；**不加索引**（低基数）。`source` 为**业务来源**（`ADMIN`/`SELF_REGISTER`），与 `employee_registration.source` 的**注册渠道**（`STAFF_H5`）语义不同、并存。

#### 8.5.5 `hr_flow_step`（V7）— 流程步骤（子表）

**用途**：入职/离职步骤条；`status`（`PENDING`/`DONE`）按序推进。
**字段**：`id` / `flow_id`(逻辑外键 hr_flow.id) / `step_key` VARCHAR(30) / `step_name` VARCHAR(50) / `step_order` INT(0) / `status` VARCHAR(10)('PENDING') / `operator_id` BIGINT NULL / `operator_name` VARCHAR(50) NULL / `operate_time` DATETIME NULL / `remark` VARCHAR(200) NULL / `create_time` / `update_time`。
**索引**：`idx_hr_flow_step_flow (flow_id, step_order)`。
**逻辑关系**：`flow_id` → `hr_flow.id`；`operator_id` → `employee.id`。
**查询走索引**：按 `(flow_id, step_order)` 组装步骤条。
**例外**：子表随主表，无 `is_deleted`（in-place 更新状态）。

### 8.6 P6 · 财务

> §8.6.5~§8.6.8（`station_payroll_setting` / `payroll_log` / `payroll_run` / `station_payroll_setting_log`）为 **v2.3/v2.5（V20 + V21 薪资结算自动化）增量**，
> 依据 `payroll-automation-design.md` §3；**独立于 P1~P10 口径**（本章「共 33 表」计数不含这 4 张新表，全局表数见 §5.3 / §9.3 的 **43**）。

#### 8.6.1 `payroll_rule`（V8）— 计薪规则（表驱动）

**字段**：`id` / `rule_name` VARCHAR(50) / `remark` VARCHAR(255) NULL / `status` TINYINT（0=停用,1=启用）/ `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_payroll_rule_status (status)`。
**逻辑关系**：被 `payroll.rule_id` 引用（删除前 Service 校验是否被工资单引用）；含 `payroll_rule_item`。
**查询走索引**：取启用规则按 `status`。

#### 8.6.2 `payroll_rule_item`（V8）— 计薪规则项

**用途**：规则项声明「来源 + 参数」，新增来源不改代码（算法 S2 注册表）。
**字段**：`id` / `rule_id`(逻辑外键 payroll_rule.id) / `item_key` VARCHAR(40) / `item_name` VARCHAR(50) / `item_type` VARCHAR(16)（`ADDITION`=增项，`DEDUCTION`=扣项）/ `source` VARCHAR(16)（`FIXED` / `ATTENDANCE` / `KPI` / `MANUAL` / `PRORATED`）/ `params` JSON（结构随来源）/ `enabled` TINYINT(1) / `sort_order` INT(0) / `is_deleted` / `create_time` / `update_time`。
> `source` 取值集合为**文档枚举**（列 `VARCHAR(16)` + 列 COMMENT，无 CHECK/ENUM 约束），运行时唯一真源为 `PayrollSource` 枚举；`PRORATED`（出勤折算，S2b 班次制）列宽 8 字符不改列型。
**索引**：`idx_payroll_rule_item_rule (rule_id, sort_order)`。
**逻辑关系**：`rule_id` → `payroll_rule.id`。
**查询走索引**：按 `(rule_id, sort_order)` 取规则项。

#### 8.6.3 `payroll`（V8 / V20 补列）— 工资单

**用途**：月度工资单 / 离职结算单；状态机（V8 六态，**V20 补 `OBJECTED` / `PAID` 至八态**）；`rule_snapshot` 存算薪时的规则快照（历史可解释）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| payroll_no | VARCHAR(40) | 否 | - | 工资单号（活跃唯一） |
| employee_id | BIGINT | 否 | - | 员工（逻辑外键 employee.id） |
| station_id | BIGINT | 是 | NULL | 归属驿站（范围收敛，逻辑外键 station.id） |
| month | CHAR(7) | 否 | - | 账期 `yyyy-MM` |
| bill_type | VARCHAR(16) | 否 | 'MONTHLY' | `MONTHLY`=月度工资单，`SETTLEMENT`=离职结算单 |
| rule_id | BIGINT | 是 | NULL | 计薪规则（逻辑外键 payroll_rule.id） |
| rule_name | VARCHAR(50) | 是 | NULL | 规则名快照 |
| rule_snapshot | JSON | 是 | NULL | 算薪时的规则快照 |
| addition_total / deduction_total | DECIMAL(12,2) | 否 | 0 | 增项 / 扣项合计 |
| gross_amount | DECIMAL(12,2) | 否 | 0 | 应发合计（=增项合计） |
| net_amount | DECIMAL(12,2) | 否 | 0 | 实发净额（应发-扣项） |
| status | VARCHAR(20) | 否 | 'DRAFT' | `DRAFT` / `PENDING_APPROVAL` / `APPROVED` / `REJECTED` / `PUBLISHED` / `CONFIRMED` / `OBJECTED` / `PAID`（后两态 V20 补；枚举顺序按 `PayrollStatus` 末尾追加，既有 6 键相对顺序不变） |
| remark / approve_remark | VARCHAR(255) | 是 | NULL | 备注 / 审核意见 |
| approver_id / approver_name / approve_time | — | 是 | NULL | 审核信息 |
| publisher_id / publisher_name / publish_time | — | 是 | NULL | 发布信息 |
| confirm_time | DATETIME | 是 | NULL | 员工确认时间 |
| objection_reason / objection_time | VARCHAR · DATETIME | 是 | NULL | 员工异议 |
| paid_by_id / paid_by_name / paid_time | BIGINT · VARCHAR(50) · DATETIME | 是 | NULL | 确认发放人 / 姓名快照 / 发放时间（**V20 补列**；`PAID` 终态当前态展示，逻辑外键 `employee.id`） |
| offboarding_id | BIGINT | 是 | NULL | 离职流程（结算单来源，逻辑外键 hr_flow.id） |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

> **V20 变更（薪资结算自动化）**：加 `paid_by_id` / `paid_by_name` / `paid_time` 三列（列尾追加、可空、无默认，NULL 表示「未发放」），
> 并以 `ALTER TABLE ... MODIFY COLUMN` 仅更新 `status` 列 COMMENT 为八态枚举（**类型 `VARCHAR(20)` / 长度 / 默认值 `'DRAFT'` / 空性均不变，无数据转换**）。
> 当前态列只保留**最后一次**发放信息；全量事件流（每次审批 / 发布 / 异议 / 再发布 / 发放）见 `payroll_log`（§8.6.6）。
> `payroll` 非大表（一员工一账期一行），**不触发**「大表变更须数据库 + 算法联评」；`paid_*` 仅当前态展示，**不加索引**。

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_payroll_payroll_no | (payroll_no) | 单号查重 / 定位 |
| idx_payroll_emp_month_bill | (employee_id, month, bill_type) | 员工单 / 生成查重（员工+月份+类型；**普通索引，非唯一**，单据级无 DB 幂等） |
| idx_payroll_month_status | (month, status) | 列表 + 状态计数 |
| idx_payroll_month_station | (month, station_id) | 驿站范围收敛 |

**逻辑关系**：`employee_id`→`employee.id`；`station_id`→`station.id`；`rule_id`→`payroll_rule.id`；`offboarding_id`→`hr_flow.id`；含 `payroll_item`。
**查询走索引**：`payrolls`（month + status / station）、`my`（employee_id + status + month）、`generate` 查重（employee_id + month + bill_type，**普通索引，由 Service 活跃查重，非 DB 唯一**）。

#### 8.6.4 `payroll_item`（V8）— 工资单明细（子表）

**用途**：逐项金额与取数解释（替代 Mock 内嵌 `items[]`），Service 组装回 `items[]`。
**字段**：`id` / `payroll_id`(逻辑外键 payroll.id) / `item_key` VARCHAR(40) / `item_name` VARCHAR(50) / `item_type` VARCHAR(16)（`ADDITION`/`DEDUCTION`）/ `source` VARCHAR(16)（`FIXED`/`ATTENDANCE`/`KPI`/`MANUAL`/`PRORATED`）/ `amount` DECIMAL(12,2)(0，正数，增/扣由 item_type 承载) / `detail` VARCHAR(255) NULL / `sort_order` INT(0) / `is_deleted` / `create_time` / `update_time`。
> `source` 取值集合同 §8.6.2（真源 `PayrollSource` 枚举）；`PRORATED` 为 S2b 班次制新增。
**索引**：`idx_payroll_item_payroll (payroll_id, sort_order)`。
**逻辑关系**：`payroll_id` → `payroll.id`。
**查询走索引**：按 `(payroll_id, sort_order)` 取明细。

#### 8.6.5 `station_payroll_setting`（V20）— 驿站级算薪配置（一驿一条）

**用途**：承载「每个驿站各自配置」的自动算薪日与开关（`payroll-automation-design.md` §3.1 / Q4/Q5）；**一驿一条**，
形态对齐 `attendance_rule`（V5）——`station_id` NOT NULL + 普通索引，活跃唯一由 Service 查重保证（决策 D7），**不建 DB 唯一索引**。
`enabled` 默认 0（**默认不自动跑数**，安全）；`notify_enabled` 默认 1（生成即推管理员，Q8）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 `station.id`，一驿一条，活跃唯一由 Service 查重） |
| enabled | TINYINT | 否 | 0 | 是否启用自动算薪：0=停用（默认），1=启用 |
| payroll_day | INT | 否 | 1 | 算薪日=每月第几天（**定稿 1-31**；月末缺日由调度**钳位到当月最后一天**，Service 校验） |
| payroll_time | VARCHAR(5) | 否 | '09:00' | 执行时间 `HH:mm`（`Asia/Shanghai` 墙钟） |
| notify_enabled | TINYINT | 否 | 1 | 生成后是否推送管理员：0=不推，1=推（默认） |
| remark | VARCHAR(255) | 是 | NULL | 备注 |
| is_deleted | TINYINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time / update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 应用层填充（D8） |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_station_payroll_setting_station | (station_id) | 一驿一条查重 + 到点轮询扫描启用配置 |

**逻辑关系**：`station_id` → `station.id`（逻辑外键，D6）。
**查询走索引**：设置页按驿站取配置、调度轮询按 `station_id` 扫描（表小，走 `idx_station_payroll_setting_station`）。
**唯一性说明**：不建 DB 唯一索引（D7），一驿一条由 Service 活跃查重保证（对齐 `attendance_rule` 决策 D7 与 §8.0(2)）。

#### 8.6.6 `payroll_log`（V20 / V21 补列）— 工资单操作留痕（追加型）

**用途**：工资单**全量事件流**（append-only），承载 Q3/Q6/Q9 的全链路留痕与追溯；形态对齐 `leave_log`（V9）。
与 `payroll` 上审批 / 发布 / 异议 / 发放字段的关系：后者为**当前态快照（latest，只留最后一次）**，本表为**全量事件流（每一次都追加）**，非简单冗余。
`reason` 为手工加扣款事由（Q3 必填）/ 异议原因 / 驳回意见 / 再发布处理说明（**由 Service 校验，非 DB 约束**）。
**`payroll_id` 为孤儿风险列**：`generate` 覆盖重建会物理删除 `DRAFT`/`REJECTED` 单，本列可能指向已删单；故 V21 补**冗余定位列** `employee_id` + `month`，使留痕可脱离 `payroll_id` 按「员工 + 账期」独立检索（M-2②/M-4）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| payroll_id | BIGINT | 否 | - | 工资单（逻辑外键 `payroll.id`；可能指向已删单，孤儿风险） |
| employee_id | BIGINT | 是 | NULL | **冗余定位列（V21 补）**：留痕所属员工（逻辑外键 `employee.id`），使留痕可脱离已删 `payroll_id` 检索 |
| month | CHAR(7) | 是 | NULL | **冗余定位列（V21 补）**：账期 `yyyy-MM` |
| action | VARCHAR(32) | 否 | - | 动作：`GENERATE_AUTO`/`GENERATE_MANUAL`/`ITEM_ADD`/`ITEM_UPDATE`/`SUBMIT`/`APPROVE`/`REJECT`/`PUBLISH`/`REPUBLISH`/`CONFIRM`/`OBJECTION`/`PAY`/`NOTIFY`/`NOTIFY_SKIP` |
| operator_id | BIGINT | 是 | NULL | 操作人（逻辑外键 `employee.id`；SYSTEM 为空） |
| operator_name | VARCHAR(50) | 是 | NULL | 操作人姓名快照 |
| operator_role | VARCHAR(20) | 是 | NULL | 操作人角色快照 |
| operator_type | VARCHAR(16) | 否 | 'USER' | 操作主体：`USER`=人工，`SYSTEM`=自动调度 |
| time | DATETIME | 否 | CURRENT_TIMESTAMP | 操作时间（**只插不改，无 `update_time`**） |
| from_status | VARCHAR(20) | 是 | NULL | 变更前状态 |
| to_status | VARCHAR(20) | 是 | NULL | 变更后状态 |
| reason | VARCHAR(200) | 是 | NULL | 事由：手工加扣款必填（Q3）/ 异议原因 / 驳回意见 / 再发布处理说明 |
| before | JSON | 是 | NULL | 变更前快照（金额 / 合计等，低频读取） |
| after | JSON | 是 | NULL | 变更后快照（金额 / 合计等，低频读取） |
| remark | VARCHAR(200) | 是 | NULL | 备注 / 排障说明 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_payroll_log_payroll | (payroll_id, `time`) | 详情页留痕时间线（按工资单取事件流） |
| idx_payroll_log_action_time | (action, `time`) | 审计查询（按动作类型 / 时段） |
| idx_payroll_log_emp_month | (employee_id, `month`, `time`) | **V21 新增**：按「员工 + 账期」独立检索留痕，覆盖重建后仍可达（不经由已删 `payroll_id`） |

**逻辑关系**：`payroll_id` → `payroll.id`；`employee_id` → `employee.id`；`operator_id` → `employee.id`（均逻辑外键，D6）。
**查询走索引**：详情时间线走 `idx_payroll_log_payroll`；审计统计走 `idx_payroll_log_action_time`；**「员工 + 账期」跨单检索走 `idx_payroll_log_emp_month`**。
**例外**：追加型留痕，**不设 `is_deleted` / `update_time`**（§8.0(4)），永不 UPDATE / DELETE。
**迁移安排（V21）**：`payroll_id` 列 COMMENT 更新（标注孤儿风险）+ `employee_id` / `month` 两列 + `idx_payroll_log_emp_month` 由 `V21` 的 `ALTER TABLE` 补入；`payroll_log` 由 `V20` 新建、**尚无存量，无需回填**（加可空列亦不影响存量）。配置变更事件**不在本表**（`payroll_id NOT NULL` 无法承载无工资单事件），另立 `station_payroll_setting_log`（§8.6.8）。

#### 8.6.7 `payroll_run`（V20 / V21 补列）— 自动算薪运行记录（认领槽位幂等 + 日粒度闸门）

**用途**：记录自动算薪每轮执行结果，并承载**补跑幂等**（`payroll-automation-design.md` §3.3 / Q7）；V21 增**日粒度闸门**（U-06）。
**幂等硬约束（两个唯一键正交共存，构成 Layer 0 DB 硬防线）**：
- **`uk_payroll_run_claim (station_id, claim_key)`（v1.1 认领槽位，管「跨日终态」）**：`claim_key` = `target_month`，`RUNNING`/`SUCCESS`/`SKIPPED` **均写值（占位）**，`FAILED` **置 `NULL`（释放，允许重试）**；MySQL 唯一索引允许多个 NULL，故同一 `(station_id, target_month)` **至多一条占位行**（`RUNNING`/`SUCCESS`/`SKIPPED` 互斥）、`FAILED` 可多行。`RUNNING` 亦占位：第二个并发执行者 `INSERT` 即在唯一约束上触发 1062 被原子拒绝，零重复算薪。
- **`uk_attempt (station_id, target_month, attempt_date)`（v1.3 / U-06，管「日内一次」）**：同一驿站同一账期**每自然日至多一条运行记录**（无论 `RUNNING`/`SUCCESS`/`FAILED`/`SKIPPED`）；同日第 2 次 `INSERT` 撞 1062 被原子拒绝。**`FAILED` 只释放 `claim_key`、不释放当日 `attempt_date`**，故续跑只能推至**次日**（与 U-06「日粒度持续重试」一致）。
**`attempt_date`（V21 补，`NOT NULL` 且无默认）**：写 `ZonedDateTime.now(zone).toLocalDate()`；为「每自然日至多一次」闸门依据与**连续失败天数**统计口径。非空落地见下方「迁移安排（V21）」。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 `station.id`） |
| target_month | CHAR(7) | 否 | - | 目标账期 `yyyy-MM` |
| attempt_date | DATE | 否 | - | **本次尝试的自然日（V21 补，U-06）**：`Asia/Shanghai` 墙钟 `now.toLocalDate()`；日粒度闸门依据 + 连续失败天数统计口径 |
| trigger_type | VARCHAR(16) | 否 | - | 触发方式：`AUTO`=定时到点，`CATCH_UP`=补跑，`MANUAL`=手工触发 |
| due_at | DATETIME | 否 | - | 本次应执行时刻（`Asia/Shanghai` 墙钟，判定「错过」的基准） |
| status | VARCHAR(16) | 否 | 'RUNNING' | 结果：`RUNNING`/`SUCCESS`/`FAILED`/`SKIPPED` |
| skip_code | VARCHAR(24) | 是 | NULL | **跳过码（机器可读，V21 去 `EXHAUSTED`）**：`BLOCKED_9405`/`CONFIG_INVALID`/`DRAFT_PROTECTED`；配合 `skip_reason` 供指标统计与「是否重试」判定（无硬上限，连续失败改为告警） |
| skip_reason | VARCHAR(200) | 是 | NULL | 跳过原因（人类可读，如「该账期已生成 9405（单号 …）」） |
| generated_count | INT | 是 | NULL | 生成单据数 |
| fail_reason | VARCHAR(500) | 是 | NULL | 失败原因（截断，不落敏感信息） |
| claim_key | CHAR(7) | 是 | NULL | **认领槽位**（v1.1 由 `success_key` 改名并升级语义）：`= target_month`；`RUNNING`/`SUCCESS`/`SKIPPED` 写值（占位）、`FAILED` 置 NULL（释放，允许重试） |
| operator_id | BIGINT | 是 | NULL | 手工触发人（`MANUAL` 时，逻辑外键 `employee.id`） |
| start_time | DATETIME | 否 | CURRENT_TIMESTAMP | 开始时间 |
| finish_time | DATETIME | 是 | NULL | 结束时间 |
| is_deleted | TINYINT | 否 | 0 | 逻辑删除：0=否，1=是（**只增不删、无删除入口，业务永不置位**，Q-DB-9） |
| create_time / update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 应用层填充（D8） |

**索引**：

| 索引名 | 类型 | 字段 | 用途 |
| ---- | ---- | ---- | ---- |
| uk_payroll_run_claim | **UNIQUE** | (station_id, claim_key) | 认领槽位幂等（跨日终态占位：`RUNNING`/`SUCCESS`/`SKIPPED` 每驿站每账期至多一行；`FAILED` 置 NULL 可多行） |
| uk_attempt | **UNIQUE** | (station_id, target_month, attempt_date) | **V21 新增**：日粒度闸门（同一驿站同一账期每自然日至多一条运行记录；同为「本驿站本账期运行历史 / 连续失败天数统计」的覆盖索引） |
| idx_payroll_run_status_time | 普通 | (status, start_time) | 查询在跑 / 失败列表、僵死 `RUNNING` 扫描 |

> **索引精简（V21，最小冗余，主代理 v1.4 §13.5 已裁定）**：原普通索引 `idx_payroll_run_station_month (station_id, target_month)` 是 `uk_attempt` 的**最左前缀**，被完全覆盖，保留即冗余（增写成本、查询零增益）→ `V21` 以 `DROP INDEX` **移除**。原拟「扩为 `(station_id, target_month, attempt_date)`」的普通索引与 `uk_attempt` 键**完全相同**，会使全表出现一对「同列同序、唯一 / 普通各一」的重复索引，故**不另补**——`uk_attempt` 即该键的唯一索引（唯一索引同样支持最左前缀查询）。
>
> **防线与 `force` 交互（v1.4）**：`uk_attempt` 为「每自然日至多一次」的 **DB 硬防线**；`force=true` 仅能跳过**应用层**日粒度短路，**不可绕过 `uk_attempt`** → 同日再试必回 `9410`。该收敛为「防线上移」的固有后果，方案 §13.5 已**登记为主代理待确认项**（`force` 语义是否调整），**不涉本表结构**。

**逻辑关系**：`station_id` → `station.id`；`operator_id` → `employee.id`（均逻辑外键，D6）。
**查询走索引**：运行记录列表 / 日粒度判定 / 连续失败统计走 `uk_attempt`（最左前缀 `(station_id, target_month)`）；在跑 / 失败列表走 `idx_payroll_run_status_time`；跨日终态幂等判定走 `uk_payroll_run_claim`。
**唯一索引例外说明**：本表**两个**唯一键均为 D7 的**显式例外**——`uk_payroll_run_claim` 理由是「重叠 tick / 多实例重复触发」须由 DB 硬约束兜底（v1.1 认领槽位，见 §9.1 Q-DB-7）；`uk_attempt` 理由是「每自然日至多一次」须 **DB 原子拒绝**（U-06，日粒度闸门不可仅靠应用层判定），见 §9.1 Q-DB-7 与 §12.2；`station_payroll_setting` 仍守 D7（Service 查重）。
**迁移安排（V21，`attempt_date` 非空落地）**：`V20` 同批建表、`payroll_run` **尚无存量**；`V21` 采用**三段式 DDL** 保证「表非空时不静默失败」且最终满足「`NOT NULL` 且无默认」——① `ADD COLUMN attempt_date DATE NULL`；② `MODIFY COLUMN ... DATE NOT NULL DEFAULT (CURRENT_DATE)`（对预期不存在的存量 NULL 行以执行当日填充，规避严格模式零日期报错）；③ `ALTER COLUMN attempt_date DROP DEFAULT`（撤默认，应用层必须显式写入）。**前置校验**：若执行时 `payroll_run` 非空且同 `(station_id, target_month)` 存在多行，则 `ADD UNIQUE KEY uk_attempt` 会因同日冲突报错（1062）→ 须停手按 C 档先回填 / 清重复行（设计 §6② B1）。

#### 8.6.8 `station_payroll_setting_log`（V21）— 驿站算薪配置变更审计（追加型）

**用途**：记录**驿站算薪配置**（§8.6.5）的变更历史，承载安全评估 M-9「配置变更留痕、**启用 0→1 可追溯**」（`payroll-automation-design.md` §3.6，主代理 M-9 裁定）；形态与 `payroll_log`（§8.6.6）同构、与 `leave_log`（§7.2/V9）同口径。
**为何不复用 `payroll_log`**：`payroll_log.payroll_id` 为 `NOT NULL`，结构上无法承载「无工资单」的配置事件；复用既有系统日志会混淆审计归属，故**另立专表**。
**写入关系（M-9 硬要求）**：`PUT /payroll-settings/{stationId}`（I-3）**每次保存成功即在同一事务内追加一条**：`action` 取 `CREATE`（首次创建）/ `ENABLE`（`enabled` 0→1）/ `DISABLE`（1→0）/ `UPDATE`（其余字段变更）；`before` / `after` 只写白名单键（`enabled` / `payrollDay` / `payrollTime` / `notifyEnabled` / `remark`），`CREATE` 时 `before` 为 NULL。查询接口 I-9（ADMIN）按 `station_id` + `time` 倒序。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 `station.id`；非空，与 `payroll_log.payroll_id` 等价定位） |
| action | VARCHAR(16) | 否 | - | 动作：`CREATE`=首次创建 / `UPDATE`=字段变更 / `ENABLE`=启用(0→1) / `DISABLE`=停用(1→0)（每次保存必写一条） |
| operator_id | BIGINT | 是 | NULL | 操作人（逻辑外键 `employee.id`） |
| operator_name | VARCHAR(50) | 是 | NULL | 操作人姓名快照 |
| operator_role | VARCHAR(20) | 是 | NULL | 操作人角色快照 |
| before | JSON | 是 | NULL | 变更前快照（白名单键：`enabled`/`payrollDay`/`payrollTime`/`notifyEnabled`/`remark`；`CREATE` 时为 NULL） |
| after | JSON | 是 | NULL | 变更后快照（同白名单键，**不含凭据 / 个人信息**） |
| time | DATETIME | 否 | CURRENT_TIMESTAMP | 操作时间（**只插不改，无 `update_time`**） |
| remark | VARCHAR(200) | 是 | NULL | 备注 |

**索引**：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_station_payroll_setting_log_station_time | (station_id, `time`) | 按驿站查变更历史主路径（对应 I-9） |

**逻辑关系**：`station_id` → `station.id`；`operator_id` → `employee.id`（均逻辑外键，D6）。
**查询走索引**：配置变更历史（I-9）走 `idx_station_payroll_setting_log_station_time`。
**例外**：追加型审计表，**不设 `is_deleted` / `update_time`**（§8.0(4)），只增不改、无删除入口。

### 8.7 P7 · 请假

`leave_request`（§7.1）、`leave_log`（§7.2）字段与索引已在一期文档定义，V9 按其口径落 DDL，本章不重复；本节补充 `leave_setting`。

#### 8.7.1 `leave_setting`（V9）— 请假全局设置（单行开关）

**用途**：全局开关 `leaveDeductEnabled`（默认 false）。
**字段**：`id` / `leave_deduct_enabled` TINYINT(0)（0=不扣（默认），1=请假按缺勤计）/ `create_time` / `update_time`。
**索引**：无（单行表）。
**逻辑关系**：无。
**例外**：单行配置，无删除语义，不设 `is_deleted`。

### 8.8 P8 · 工单

#### 8.8.1 `work_order`（V10）— 工单

**用途**：工单主表；处理时间线拆到 `work_order_timeline`（替代 Mock `handle_log` 内嵌 JSON，出参 Service 组装回 `handleLog[]`）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| order_no | VARCHAR(40) | 否 | - | 工单号（活跃唯一） |
| type | TINYINT | 否 | - | 类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他 |
| status | TINYINT | 否 | 0 | 状态：0=待处理 1=处理中 2=已解决 3=已关闭 |
| priority | TINYINT | 否 | 1 | 优先级：0=低 1=中 2=高 |
| title | VARCHAR(100) | 否 | - | 标题（1-100 字） |
| content | VARCHAR(500) | 是 | NULL | 描述（≤500 字） |
| source | VARCHAR(16) | 否 | 'MANUAL' | 来源：`MANUAL`=手工，`AUTO_WECHAT`=企微自动派发 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 station.id） |
| parcel_id | BIGINT | 是 | NULL | 关联包裹（逻辑外键 parcel.id） |
| waybill_no | VARCHAR(50) | 是 | NULL | 关联运单号 |
| reporter_id | BIGINT | 是 | NULL | 上报人（企微自动派发为空） |
| assignee_id | BIGINT | 是 | NULL | 处理人（无候选为空=转人工） |
| sla_deadline | DATETIME | 是 | NULL | SLA 截止时间 |
| resolved_time / closed_time | DATETIME | 是 | NULL | 解决 / 关闭时间 |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**：`idx_work_order_order_no (order_no)`、`idx_work_order_station_status (station_id, status)`、`idx_work_order_assignee_status (assignee_id, status)`、`idx_work_order_sla (sla_deadline)`。
**逻辑关系**：`station_id`→`station.id`；`reporter_id`/`assignee_id`→`employee.id`；`parcel_id`→`parcel.id`；含 `work_order_timeline` / `work_order_transfer`。
**查询走索引**：列表（station_id + status(+type/priority/assignee 附加)）；超时筛选（sla_deadline）；详情/查重（order_no）。

#### 8.8.2 `work_order_timeline`（V10）— 处理时间线（追加型）

**字段**：`id` / `work_order_id`(逻辑外键 work_order.id) / `action` VARCHAR(20)（`create`/`accept`/`resolve`/`close`/`reopen`/`assign`/`transfer`/`auto_dispatch`）/ `operator_id` BIGINT NULL / `operator_name` VARCHAR(50) NULL / `content` VARCHAR(500) NULL / `time` DATETIME。
**索引**：`idx_work_order_timeline_order (work_order_id, time)`。
**逻辑关系**：`work_order_id`→`work_order.id`；`operator_id`→`employee.id`。
**例外**：追加型，无 `is_deleted` / `update_time`。

#### 8.8.3 `work_order_transfer`（V10）— 转单留痕（追加型）

**字段**：`id` / `work_order_id` / `from_employee_id` BIGINT NULL / `from_employee_name` VARCHAR(50) NULL / `to_employee_id` BIGINT / `to_employee_name` VARCHAR(50) NULL / `reason` VARCHAR(200) / `operator_id` BIGINT NULL / `operator_name` VARCHAR(50) NULL / `transfer_time` DATETIME。
**索引**：`idx_work_order_transfer_order (work_order_id, transfer_time)`。
**逻辑关系**：`work_order_id`→`work_order.id`；`from/to/operator`_id → `employee.id`。
**例外**：追加型，无 `is_deleted` / `update_time`。

#### 8.8.4 `work_order_dispatch_rule`（V10）— 企微自动派单规则（可热改）

**字段**：`id` / `keyword` VARCHAR(20) / `work_order_type` TINYINT（1-4）/ `priority` TINYINT(1)（0-2）/ `default_assignee_id` BIGINT NULL / `enabled` TINYINT(1) / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_work_order_dispatch_rule_keyword (keyword)`。
**逻辑关系**：`default_assignee_id` → `employee.id`。
**查询走索引**：命中规则扫描按 `keyword`（低容量，Service 顺序判定）。

### 8.9 P9 · 同步与配置中心

#### 8.9.1 `sync_task`（V11）— 同步任务（状态机四态）

**字段**：`id` / `station_id`(逻辑外键 station.id) / `batch_no` VARCHAR(40)（活跃唯一，对接键）/ `status` TINYINT(0)（0=待领取 1=执行中 2=成功 3=失败）/ `parcel_total` INT(0) / `success_count` INT(0) / `fail_count` INT(0) / `retry_count` INT(0) / `error_msg` VARCHAR(255) NULL / `assign_time` DATETIME NULL / `start_time` DATETIME NULL / `finish_time` DATETIME NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_task_batch_no (batch_no)`、`idx_sync_task_station_status (station_id, status)`、`idx_sync_task_create_time (create_time)`。
**逻辑关系**：`station_id`→`station.id`；含 `sync_task_log`；被 `parcel.sync_batch_no` 关联（按批次号，非外键）。
**查询走索引**：列表按 `(station_id, status)` + `create_time` 倒序；批次查重/定位按 `batch_no`。

#### 8.9.2 `sync_task_log`（V11）— 同步任务日志（追加型）

**字段**：`id` / `task_id`(逻辑外键 sync_task.id) / `batch_no` VARCHAR(40) NULL / `level` TINYINT(0)（0=INFO 1=WARN 2=ERROR）/ `message` VARCHAR(1000) / `log_time` DATETIME。
**索引**：`idx_sync_task_log_task (task_id, log_time)`。
**例外**：追加型，无 `is_deleted` / `update_time`。

#### 8.9.3 `sync_station_config`（V12）— 驿站采集配置（一驿一行）

**字段**：`id` / `station_id`(一驿一行，活跃唯一) / `enabled` TINYINT(0)（采集开关）/ `frequency` VARCHAR(20) NULL（旧码 `HOURLY`/`EVERY_2H`/`EVERY_4H`/`DAILY` → 迁移后选项 Key）/ `data_source` VARCHAR(50) NULL（迁移后选项 Key；未配置为空）/ `collect_start_time` VARCHAR(5) NULL / `collect_end_time` VARCHAR(5) NULL / `last_collect_time` DATETIME NULL / `last_collect_status` VARCHAR(20)('NEVER')（`SUCCESS`/`FAILED`/`NEVER`）/ `status` TINYINT(1)（配置行：0=停用,1=启用）/ `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_station_config_station (station_id)`。
**逻辑关系**：`station_id` → `station.id`（1:1）。
**查询走索引**：按 `station_id` 取配置。

#### 8.9.4 `sync_config_item`（V12）— 配置项定义

**字段**：`id` / `item_key` VARCHAR(40)（活跃唯一）/ `name` VARCHAR(20) / `description` VARCHAR(100) NULL / `value_type` VARCHAR(20)（`SINGLE_SELECT`/`NUMBER`/`TEXT`/`TIME`/`TIME_RANGE`）/ `required` TINYINT(0) / `default_value` VARCHAR(255) NULL / `unit` VARCHAR(8) NULL / `constraints` JSON / `option_set_key` VARCHAR(40) NULL / `scope` VARCHAR(10)('STATION')（`GLOBAL`/`STATION`）/ `sort` INT(0) / `enabled` TINYINT(1) / `builtin` TINYINT(0)（0=可删，1=仅停用）/ `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_config_item_key (item_key)`、`idx_sync_config_item_sort (sort)`。
**逻辑关系**：`option_set_key` → `sync_config_option.set_key`（逻辑关联，非外键）。
**查询走索引**：列表按 `sort`；键查重按 `item_key`。

#### 8.9.5 `sync_config_option`（V12）— 选项集选项

**字段**：`id` / `set_key` VARCHAR(40)（`data_source`/`collect_frequency`/`time_template`）/ `option_key` VARCHAR(40)（集合内活跃唯一）/ `label` VARCHAR(40) / `extra_attrs` JSON（按选项集约定）/ `sort` INT(0) / `enabled` TINYINT(1) / `builtin` TINYINT(0) / `source` VARCHAR(16)('MANUAL')（`BUILTIN`/`MANUAL`/`MIGRATED`）/ `legacy_codes` JSON / `remark` VARCHAR(100) NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_config_option_set_key (set_key, option_key)`。
**逻辑关系**：被 `sync_config_item.option_set_key` 引用。
**查询走索引**：按 `(set_key, option_key)` 查重/取选项。

#### 8.9.6 `sync_config_global`（V12）— 全局默认值

**字段**：`id` / `item_key` VARCHAR(40)（活跃唯一）/ `value` VARCHAR(255) NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_config_global_item_key (item_key)`。
**逻辑关系**：`item_key` → `sync_config_item.item_key`（逻辑关联）。
**查询走索引**：按 `item_key` 取全局默认值。

#### 8.9.7 `sync_config_station_override`（V12）— 驿站覆盖值

**字段**：`id` / `station_id`(逻辑外键 station.id) / `item_key` VARCHAR(40) / `value` VARCHAR(255) NULL / `is_deleted` / `create_time` / `update_time`。
**索引**：`idx_sync_override_station_item (station_id, item_key)`。
**逻辑关系**：`station_id`→`station.id`；`item_key`→`sync_config_item.item_key`。
**查询走索引**：按 `(station_id, item_key)` 取覆盖值 + 活跃唯一查重。

### 8.10 P10 · 包裹（20 万级大表）

#### 8.10.1 `parcel`（V13）— 包裹数据

**用途**：包裹主数据，按驿站划分数据可见范围；列表默认按入库时间倒序（游标分页，ADR-06）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| station_id | BIGINT | 否 | - | 驿站（逻辑外键 station.id，可见范围核心维度） |
| waybill_no | VARCHAR(50) | 否 | - | 运单号（与 station_id 组合活跃唯一，Service 查重） |
| status | TINYINT | 否 | 0 | 状态：0=待入库 1=在库待取 2=已取件 3=异常 4=已退回 |
| receiver_name | VARCHAR(50) | 是 | NULL | 收件人姓名（出参脱敏） |
| receiver_phone | VARCHAR(20) | 是 | NULL | 收件人手机号（出参脱敏） |
| shelf_code | VARCHAR(20) | 是 | NULL | 货架位编码 |
| inbound_time | DATETIME | 否 | - | 入库时间（列表默认排序键） |
| pickup_employee_id | BIGINT | 是 | NULL | 取件员工（逻辑外键 employee.id） |
| pickup_time | DATETIME | 是 | NULL | 取件时间 |
| sync_batch_no | VARCHAR(40) | 是 | NULL | 来源同步批次号（逻辑外键 sync_task.batch_no） |
| remark | VARCHAR(255) | 是 | NULL | 备注（异常件说明） |
| is_deleted / create_time / update_time | — | — | — | 通用约定 |

**索引**（架构 §4.3 四条 + 算法 §13；**已联评：算法 §13**）：

| 索引名 | 字段 | 用途 |
| ---- | ---- | ---- |
| idx_parcel_station_waybill | (station_id, waybill_no) | 防重复入库（Service 活跃查重 + 普通索引） |
| idx_parcel_station_status | (station_id, status) | 按驿站/状态筛选 |
| idx_parcel_station_status_inbound | (station_id, status, inbound_time DESC) | 列表默认排序 + 状态筛选一次走索引 |
| idx_parcel_station_inbound | (station_id, inbound_time DESC) | 趋势 / 近 N 天聚合 |

**逻辑关系**：`station_id`→`station.id`；`pickup_employee_id`→`employee.id`；`sync_batch_no`→`sync_task.batch_no`。
**查询走索引**：`parcels` 列表（`idx_parcel_station_status_inbound`，须 `type=range/ref`、无 `Using filesort`，收敛服务器实测）；`summary`/`trend`/`ranking` 聚合走 `idx_parcel_station_inbound`；运单号精确查走 `idx_parcel_station_waybill`。
**大表纪律**：`CREATE TABLE` + 独立 `CREATE INDEX`；分页默认游标；禁止 `SELECT *`；结构变更须数据库 + 算法联评；加索引附回滚脚本并优先 `ALGORITHM=INPLACE`。容量：20 万行约 106 MB（算法 S7），落 `/data` 数据盘不足 0.3%。

### 8.11 覆盖索引与查询映射（禁止 `SELECT *` 友好化）

| 查询场景 | 目标表 | 命中索引 |
| ---- | ---- | ---- |
| 站内信列表 / 未读数 / 全部已读 | notification | idx_notification_employee_time / idx_notification_employee_read |
| 打卡记录列表 / 概况 | attendance_record | idx_attendance_record_station_date / idx_attendance_record_emp_date |
| 排班周矩阵 / 我的排班 / 活跃唯一收口 | attendance_schedule | idx_attendance_schedule_station_date / idx_attendance_schedule_emp_date / uk_attendance_schedule_active_shift |
| 补卡列表（管理端 / 员工端） | attendance_makeup | idx_attendance_makeup_station_status / idx_attendance_makeup_emp_status |
| KPI 明细 / 列表 / 排行 | kpi_score | idx_kpi_score_emp_month / idx_kpi_score_month_station / idx_kpi_score_month_score |
| 调薪历史 | hr_salary_log | idx_hr_salary_log_emp |
| 工资单列表 / 我的 / 幂等 | payroll | idx_payroll_month_status / idx_payroll_emp_month_bill / idx_payroll_month_station |
| 算薪配置读取 / 到点轮询扫描 | station_payroll_setting | idx_station_payroll_setting_station |
| 工资单留痕时间线 / 审计 / 员工+账期检索 | payroll_log | idx_payroll_log_payroll / idx_payroll_log_action_time / idx_payroll_log_emp_month |
| 运行记录列表 / 日粒度闸门 / 跨日占位幂等 | payroll_run | uk_attempt / uk_payroll_run_claim / idx_payroll_run_status_time |
| 算薪配置变更历史（I-9） | station_payroll_setting_log | idx_station_payroll_setting_log_station_time |
| 账号/驿站操作留痕（按对象 / 按操作人 / 按动作） | operation_audit_log | idx_operation_audit_target / idx_operation_audit_operator / idx_operation_audit_action_time |
| 请假待办 / 日期相交 | leave_request | idx_leave_request_station_status / idx_leave_request_date |
| 工单列表 / 超时 / 详情 | work_order | idx_work_order_station_status / idx_work_order_assignee_status / idx_work_order_sla / idx_work_order_order_no |
| 同步任务列表 / 日志 | sync_task / sync_task_log | idx_sync_task_station_status / idx_sync_task_log_task |
| 配置中心读写 | sync_config_* | idx_sync_config_item_key / idx_sync_config_option_set_key / idx_sync_config_global_item_key / idx_sync_override_station_item |
| 包裹列表 / 聚合 / 运单查 | parcel | idx_parcel_station_status_inbound / idx_parcel_station_inbound / idx_parcel_station_waybill |

### 8.12 与架构/算法的一致性核对与差异（守契约）

| 核对项 | 依据口径 | 本章处置 | 结论 |
| ---- | ---- | ---- | ---- |
| 表数量 | 架构 §4.1「约 36 张」 | 既有 4 + 新增 33 = **37 张**（leave/client_log 计入） | 与「约 36」同量级，差异为**计数口径**，非结构冲突 |
| `parcel` 唯一索引命名 | 架构 §4.3 写 `uk_parcel_station_waybill` | 按决策 D7 + 架构 §4.6(2)「Service 查重 + 普通索引」落为 **`idx_parcel_station_waybill`（普通索引）** | **命名纠正**：`uk_` 前缀与「不建唯一索引」冲突，改 `idx_`；见 §9.1 待确认 |
| `parcel` 字段名 | 架构 §4.2 写 `shelf_no` / `batch_no` | 按 Mock 真源落为 **`shelf_code` / `sync_batch_no`** | **命名以 Mock 为准**（任务字段真源优先），语义一致 |
| `payroll` 金额字段 | 架构 §4.2 写 `total_amount` / `net_amount` | 按 Mock 真源落为 **`gross_amount`（应发）** / `net_amount` | **字段名纠正**：`total_amount` → `gross_amount`（=增项合计） |
| `kpi_score` 结构 | 架构 §4.2 列 `total_score` / `level` / `metric_detail` | 落为一员工一账期一行 + `metric_detail` JSON 快照 | 一致；另补 `station_id`（范围收敛/排行所需，见下条） |
| `kpi_score` / `kpi_metric` 附加列 | 架构 §4.2 未列 | 补 `kpi_score.station_id`（范围收敛）、`kpi_metric.score_mode/full_score/role_scope`（替代 JSON） | **必要补充**，非语义冲突 |
| JSON 列边界 | 架构 §4.2 列举 8 类 JSON 字段 | 新增 `sync_config_option.extra_attrs` / `legacy_codes` 两个同类 JSON | **新增 2 列**（同属「低频读取、结构多变」），登记见 §9.1 待确认 |
| 参数外置承载 | 算法 §11（`hrm.algo.*` 超参） | 算法超参**不入库**（走配置，重启生效）；业务口径入库（`kpi_metric` / `payroll_rule(_item)` / `attendance_rule` / `work_order_dispatch_rule` / 配置中心四层 / `leave_setting`） | 与架构 §5.2 ADR-05 一致 |
| `parcel` 索引 | 算法 §13 追加两条建议 | 全部采纳（业务加 `idx_parcel_station_status_inbound` 与 `idx_parcel_station_inbound`） | **已联评：算法 §13**，不冲突 |
| `leave_request` / `leave_log` / `client_log` | db.md §7.1/7.2/7.3 | 字段与索引沿用，V9/V3 按其口径落 DDL，不新增语义 | 一致 |
| 表数量（v2.3 / V20） | `payroll-automation-design.md` §3（3 新表 + `payroll` 加列） | 表总数 **39 → 42**（新增 `station_payroll_setting` / `payroll_log` / `payroll_run`），`payroll` 补 `paid_by_id` / `paid_by_name` / `paid_time` 3 列 | 与上游方案 §3 一致 |
| `payroll_run` 唯一索引（v2.4 修订 / V20） | 决策 D7「不建 DB 唯一索引」 | 落 `uk_payroll_run_claim (station_id, claim_key)`（**显式例外**：重叠 tick / 多实例重复触发须 DB 硬约束兜底；v1.1 认领槽位 `RUNNING`/`SUCCESS`/`SKIPPED` 写 `claim_key` 占位、`FAILED` 置 NULL 释放）；`station_payroll_setting` 仍守 D7（Service 查重） | **D7 显式例外**，登记见 §9.1 Q-DB-7 |
| 表数量（v2.5 / V21） | `payroll-automation-design.md` v1.4 §3.2/§3.3/§3.6 + `algorithm-payroll-scheduling.md` v1.2 §1.5/§12.2（`payroll_log` 定位列 / `payroll_run.attempt_date` / 新表 `station_payroll_setting_log`） | 表总数 **42 → 43**（新增 `station_payroll_setting_log`）；`payroll_log` 补 `employee_id` / `month` 2 列 + 索引；`payroll_run` 补 `attempt_date` + 唯一键 `uk_attempt` | 与上游方案 v1.4 §6「V21 范围」三段一致 |
| `payroll_run` 第二唯一键 + 索引精简（v2.5 / V21） | 决策 D7「不建 DB 唯一索引」；算法 v1.2 §1.5「`uk_attempt` 管日内、`uk_claim` 管终态」；方案 v1.4 §3.3/§13.5 主代理裁定「日粒度硬防线 = DB 唯一键 `uk_attempt`，删冗余 `idx_payroll_run_station_month`」 | ① 落 `uk_attempt (station_id, target_month, attempt_date)`（**D7 显式例外**：日粒度闸门须 DB 原子拒绝，见 §9.1 Q-DB-7）；② **DROP** 冗余普通索引 `idx_payroll_run_station_month`（被 `uk_attempt` 最左前缀覆盖）；③ **不另补**普通 `(station_id, target_month, attempt_date)` 索引（与 `uk_attempt` 键完全相同，属重复索引） | **D7 显式例外 + 最小冗余**，与方案 v1.4 §13.5 裁定一致；`force` 同日再试不可绕过 `uk_attempt`（→ `9410`），该收敛已登记为主代理待确认项 |
| `notification.type` COMMENT 同步（7/8/9 / v2.5 / V21 段 4） | 方案 §4.5（`sendSystem` 独立白名单 `{7,8,9}`、公告白名单维持 `1..6`）、§6② 回改项 5、主代理裁定「一次做完、不另开迁移版本」 | **已并入 `V21` 段 4**：`ALTER TABLE notification MODIFY COLUMN type` 仅更新 COMMENT，补 7=工资单待审核（→管理员）/ 8=工资单已发布（→员工本人）/ 9=工资单异议退回（→管理员）；`type` 仍 `TINYINT NOT NULL`、无默认，**不改列型、无 DML**；`init.sql` 与 §8.2.1 同步 | **本轮已随 `V21` 同步**（原「不在本轮 / 待 B4 独立迁移」判断依主代理裁定撤销）；白名单拆分（`SYSTEM_TYPES{7,8,9}` 独立、公告白名单维持 `1..6`）仍属 **B4 代码改动**，不在数据层 |
| `attendance_schedule` 活跃唯一键（v2.6 / V22） | 决策 D7「不建 DB 唯一索引」；架构 §2.4 ARCH-S-1「多班次须 DB 最终防线」；评审 M-1「须与 `@TableLogic` 逻辑删自洽」 | ① 落**生成列** `active_shift_key = IF(is_deleted=0, CONCAT(employee_id,'|',work_date,'|',shift_id), NULL) STORED` + `UNIQUE(active_shift_key)`（**D7 显式例外**，登记 §9.1 Q-DB-11）；② **保留**两个既有普通索引（表达式唯一键不吃其最左前缀，不构成冗余）；③ 不补 `attendance_shift` 时段字段（架构 D-6） | **D7 显式例外 + 与逻辑删自洽**；形态对齐 `employee.phone_active`（§11.2 / V16） |
| `operation_audit_log`（v2.6 / V23） | 架构 §3 ARCH-S-2（安全 M-1 / REG-01）+ 评审核对 5、M-7 | 新建通用追加型审计表 16 列、3 索引；时间列命名 **`time`**（对齐 leave_log / payroll_log / station_payroll_setting_log，M-7）；无 `is_deleted`/`update_time`；`before`/`after`/`changed_fields` 白名单化、口令只记布尔 | **与既有留痕口径一致**；口令不落明文/散列（§3.4） |
| 表数量（v2.6 / V22+V23） | 架构 §0.2「迁移 = 2 个（`V22` 排班唯一键、`V23` 操作审计表）」 | 表总数 **43 → 44**（+1 表 `operation_audit_log`）；`attendance_schedule` +1 生成列 +1 唯一键、既有索引不删 | 与上游方案 §2.8「结构变更 2」一致 |

### 8.13 迁移与回滚索引

| 版本 | 文件 | 表 |
| ---- | ---- | ---- |
| V3 | [V3__client_log.sql](../../hrm-server/src/main/resources/db/migration/mysql/V3__client_log.sql) | client_log |
| V4 | [V4__notification.sql](../../hrm-server/src/main/resources/db/migration/mysql/V4__notification.sql) | notification |
| V5 | [V5__attendance.sql](../../hrm-server/src/main/resources/db/migration/mysql/V5__attendance.sql) | attendance_rule / attendance_shift / attendance_schedule / attendance_record / attendance_makeup |
| V6 | [V6__kpi.sql](../../hrm-server/src/main/resources/db/migration/mysql/V6__kpi.sql) | kpi_metric / kpi_score |
| V7 | [V7__hr.sql](../../hrm-server/src/main/resources/db/migration/mysql/V7__hr.sql) | hr_profile / hr_salary / hr_salary_log / hr_flow / hr_flow_step |
| V8 | [V8__payroll.sql](../../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql) | payroll_rule / payroll_rule_item / payroll / payroll_item |
| V9 | [V9__leave.sql](../../hrm-server/src/main/resources/db/migration/mysql/V9__leave.sql) | leave_request / leave_log / leave_setting |
| V10 | [V10__work_order.sql](../../hrm-server/src/main/resources/db/migration/mysql/V10__work_order.sql) | work_order / work_order_timeline / work_order_transfer / work_order_dispatch_rule |
| V11 | [V11__sync_task.sql](../../hrm-server/src/main/resources/db/migration/mysql/V11__sync_task.sql) | sync_task / sync_task_log |
| V12 | [V12__sync_config_center.sql](../../hrm-server/src/main/resources/db/migration/mysql/V12__sync_config_center.sql) | sync_station_config / sync_config_item / sync_config_option / sync_config_global / sync_config_station_override |
| V13 | [V13__parcel.sql](../../hrm-server/src/main/resources/db/migration/mysql/V13__parcel.sql) | parcel |
| V14 | [V14__hr_flow_operator_columns.sql](../../hrm-server/src/main/resources/db/migration/mysql/V14__hr_flow_operator_columns.sql) | hr_flow（补 operator_id / operator_name 两列） |
| V15 | [V15__auth_trusted_device.sql](../../hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql) | auth_trusted_device |
| V16 | [V16__employee_phone_unique.sql](../../hrm-server/src/main/resources/db/migration/mysql/V16__employee_phone_unique.sql) | employee（补生成列 phone_active + 唯一键 uk_employee_phone_active，活跃唯一 M-5） |
| V17 | [V17__employee_registration.sql](../../hrm-server/src/main/resources/db/migration/mysql/V17__employee_registration.sql) | employee_registration |
| V18 | [V18__hr_flow_source.sql](../../hrm-server/src/main/resources/db/migration/mysql/V18__hr_flow_source.sql) | hr_flow（补 source 列，M-9） |
| V19 | [V19__employee_position.sql](../../hrm-server/src/main/resources/db/migration/mysql/V19__employee_position.sql) | employee（补 position 列，方案乙 U-07） |
| V20 | [V20__payroll_automation.sql](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql) | station_payroll_setting / payroll_log / payroll_run；payroll（补 paid_by_id / paid_by_name / paid_time 三列 + status COMMENT 8 态） |
| V21 | [V21__payroll_log_locator.sql](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql) | payroll_log（补 employee_id / month + idx_payroll_log_emp_month）；payroll_run（补 attempt_date + uk_attempt、DROP idx_payroll_run_station_month、skip_code COMMENT 去 EXHAUSTED、表 COMMENT 更新）；station_payroll_setting_log（新表）；notification（type 列 COMMENT 补 7/8/9，仅注释不改列型） |
| V22 | [V22__attendance_schedule_multi_shift.sql](../../hrm-server/src/main/resources/db/migration/mysql/V22__attendance_schedule_multi_shift.sql) | attendance_schedule（补生成列 active_shift_key + 唯一键 uk_attendance_schedule_active_shift；两个既有普通索引不删；表 COMMENT 更新为「员工+日期+班次 活跃唯一」） |
| V23 | [V23__operation_audit_log.sql](../../hrm-server/src/main/resources/db/migration/mysql/V23__operation_audit_log.sql) | operation_audit_log（新表，16 列 + 3 索引，追加型，口令只记布尔） |

- **回滚**：每个脚本尾部自带 `-- 回滚:` 注释段（`DROP TABLE` / `DROP COLUMN` / `DROP INDEX`），人工执行；不使用 Flyway undo（社区版不支持）。
- **版本单调性**：V3 < V4 < … < V23；V3~V13 与批次 P1~P10 顺序一致（未发生**版本顺延**，`employee` 无需补索引，见 §9.2）；V14/V15 为登录体系改造（M3）与前置修复，V16~V19 为员工自助注册批次（registration-design.md §7 定稿：V16 前置修复 → V17 新表 → V18 来源 → V19 岗位），V20 为薪资结算自动化批次（payroll-automation-design.md §3/§6-B1：3 新表 + `payroll` 加列），V21 为其 v1.2/v1.3/v1.4 增量（方案 v1.4 §6「V21 范围」四段：`payroll_log` 定位列 + `payroll_run.attempt_date` + `uk_attempt` + 删冗余索引 + 新表 `station_payroll_setting_log` + `notification.type` 注释补 7/8/9），V22/V23 为驿站精灵管理能力扩展数据层（架构 §2.4/§3：`attendance_schedule` 生成列式活跃唯一键 + `operation_audit_log` 新表），无跳号/回填/复用。
- **迁移执行**：属 C 档（结构变更），须主智能体三步授权后由运维执行；上线前备份库。**V16 执行前须先跑存量重复手机号预检（第 11 章）并返回 0 行**，否则迁移以 1062 失败。**V20 / V21 执行前须先经 P0.6 技术评审「通过 / 有条件通过」并完成 B0 契约定稿（api.md / db.md 补录）**（payroll-automation-design.md §6）。**V21 前置校验**：`payroll_run` / `payroll_log` 由 V20 同批新建、预期无存量；若 `payroll_run` 非空且同 `(station_id, target_month)` 存在多行，`ADD UNIQUE KEY uk_attempt` 会因同日冲突报 1062 → 须停手按 C 档先回填 / 清重复行（见 §8.6.7）。**V22 前置校验**：须先跑存量查重预检**②（仅活跃行重复组）返回 0 行**，否则 `ADD UNIQUE KEY uk_attendance_schedule_active_shift` 以 1062 失败；预检范围**须含 `is_deleted=1` 的软删行**（区分诊断/阻断口径，见 §12.2）。**V23 为新表**，无存量、无预检。**V22 / V23 执行前须先经 P0.6 技术评审复评「通过 / 有条件通过」**（本次为评审打回后修订，须重评）。

***

## 9. 静态自检、待确认项与未验证项

> 本机无 JDK / MySQL / Redis，**未实跑迁移**；以下为静态核对结论与需服务器复核项。

### 9.1 待主智能体/相关方确认项（本体无法单方裁定）

| # | 事项 | 现状与依据 | 建议处置 |
| - | ---- | ---- | ---- |
| Q-DB-1 | `parcel` 查重索引命名 | 架构 §4.3 写 `uk_parcel_station_waybill`，但 §4.6(2) 与决策 D7 要求「Service 查重 + 普通索引」 | 已落 `idx_parcel_station_waybill`（普通索引）；如确认可用数据库唯一索引，需用户裁定（架构开放问题 8-12）后另立新版本 |
| Q-DB-2 | `sync_config_option.extra_attrs` / `legacy_codes` 使用 JSON | 架构 §4.2 列举的 JSON 字段未含二者，但同属「低频读取、结构多变」 | 已按 JSON 落地；如要求严格收敛到列举集，可改为显式列（`interval_minutes` / `template_start_time` / `template_end_time` + `legacy_codes` 串化） |
| Q-DB-3 | `hr_flow.type` 列名 | 契约字段为 `type`（离职类型），与 `flow_type` 并存 | 已按契约用 `type`；如后端希望更表意的 `offboarding_type`，需前端/后端同步确认后另立版本 |
| Q-DB-4 | `kpi_score` 一员工一账期 vs 一员工一账期一指标 | 架构 §4.2 为一员工一账期 + `metric_detail` JSON；Mock 为逐指标行 | 已按架构落聚合行；如需明细可查，`metric_detail` 已含逐项快照 |
| Q-DB-5 | `auth_trusted_device` 建**唯一索引** vs 决策 D7 | 决策 D7 要求「Service 查重 + 普通索引」，但架构 §4.2.1 对设备信任表明确要求 `(employee_id, device_fingerprint)` **唯一、幂等 upsert** | 已按架构落 **`uk_auth_trusted_device_emp_fp`（唯一键）**，为 D7 的**显式例外**：本表用业务标志 `revoked`（非 `is_deleted`）表达撤销，撤销后复用同一行重信，与 D7 担心的「逻辑删除后唯一键阻止复用」场景不冲突（论证见 §10.2）。如主智能体/相关方要求严格回到 D7，则需放弃 DB 唯一约束、改为 Service 查重 + 普通索引，并另立新版本 |
| Q-DB-6 | `auth_sms_log`（架构 §4.2.1 表2「短信发送审计，可选但建议」）是否纳入本轮 | 本期任务范围为「设备信任表 + `hr_flow` 补列」两个变更；§4.2.1 将 `auth_sms_log` 标为可选 | **本轮未纳入**（不建表、不出脚本）。如需审计短信发送（频控/降级/失败归因），属可落库审计表（脱敏手机号、不含验证码明文），建议由主智能体排期另立 **V16**；`security-auth-review.md` §4.4 的「验证码绝不入日志/审计」红线不变 |
| Q-DB-7 | `payroll_run` 建**硬唯一键** vs 决策 D7（v2.4 修订 / V20；**v2.5 / V21 增第二键**） | 决策 D7 要求「Service 查重 + 普通索引」，但 `payroll-automation-design.md` §3.3 明确以 `uk_payroll_run_claim (station_id, claim_key)` 作「重叠 tick / 多实例重复触发」的**最后一道防线**（v1.1 认领槽位：`claim_key = target_month`，`RUNNING`/`SUCCESS`/`SKIPPED` 均写值占位、`FAILED` 置 NULL 释放，靠「唯一索引允许多 NULL」实现占位行互斥）；**V21 再落 `uk_attempt (station_id, target_month, attempt_date)`** 作「每自然日至多一次」日粒度闸门（U-06，`algorithm-payroll-scheduling.md` v1.2 §12.2：不可仅靠应用层判定，须 DB 原子拒绝同日第 2 条） | **两个唯一键**均按方案落 **D7 显式例外**（登记同 §8.12）：前者兜底跨日终态占位、后者兜底日内一次，二者**正交共存**；与 `auth_trusted_device`（§9.1 Q-DB-5）同类。如要求严格回 D7，则多实例重复触发与日粒度闸门只能在应用层兜底（弱化保障），须主智能体裁定 |
| Q-DB-8 | `payroll.status` 八态**枚举顺序**（v2.3 / V20） | 上游方案 §2.1「目标状态集合」表把 `OBJECTED` 列在 `PUBLISHED` 与 `CONFIRMED` 之间，而 §2.7.2 明确「新增 `OBJECTED`、`PAID` **追加在枚举末尾**」以保持既有 6 键相对顺序不变 | 列 COMMENT 采用**末尾追加顺序**：`DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED/OBJECTED/PAID`（与 §2.7.2 及运行时 `PayrollStatus.values()` 一致，§2.1 表仅为概念分组）。如评审要求以 §2.1 顺序为准，须先确认前端字典键序兼容，再另立版本 |
| Q-DB-9 | `payroll_run` 的 `is_deleted` 与硬唯一键的语义边界（v2.4 修订 / V20；**v2.5 落档**） | 方案 §3.3 同时给了 `is_deleted`（逻辑删除）与硬唯一键 `(station_id, claim_key)`；唯一索引**不过滤** `is_deleted`，故一条被逻辑删除的**占位行**（`RUNNING`/`SUCCESS`/`SKIPPED`）仍占用唯一键、会阻止同驿站同账期再次写入占位行 | **落档结论（方案 v1.3 §0.7/§3.3-D1 裁定）：维持「运行记录只增不删、不提供删除入口」前提**（`is_deleted` 仅为结构一致性兜底、业务永不置位），被逻辑删除的占位行仍占键位在本业务下**属有意为之**（防重复算薪）；**不改**生成列式部分唯一（无产品需求 + 增复杂度）。如后续确需「删除后重跑」，须改生成列式部分唯一（对齐 V16 手法）并另立版本（`TODO(扩展)` T3）。**评审口径**：方案侧给裁定、数据层据此落档（V21 未改本列语义）；**此点仍请技术评审重评确认** |
| Q-DB-10 | `payroll_run.attempt_date` + `uk_attempt` 与 `force=true`「同日再试」的交互（v2.5 / V21，**主代理 v1.4 §13.5 待确认项**） | 主代理 v1.4 裁定「日粒度硬防线 = DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`」，故 `force=true` 仅跳过**应用层**短路、**不可同日再插行**（必回 `9410`）；`attempt_date` 每行恒有值（`NOT NULL`、永不置 NULL），与 `uk_payroll_run_claim`（跨日终态占位、`FAILED` 置 NULL）正交共存 | 数据层按裁定落地（`uk_attempt` 已落、`force` 收敛为**应用层语义**）；方案 §13.5 已登记为**主代理待确认项**（是否调整 `force` 语义 / 改运维另行处置）；**不涉本表结构变更** |
| Q-DB-11 | `attendance_schedule` 建**生成列式唯一键** vs 决策 D7；以及 `attendance_shift` 是否补时段字段（v2.6 / V22） | 决策 D7 要求「Service 查重 + 普通索引」，但架构 §2.4 ARCH-S-1 要求多班次有 DB 最终防线；评审 M-1 指出裸三元 `UNIQUE` 与 `@TableLogic` 逻辑删互斥（清空软删→再排撞 1062）。另有架构 D-6「本批不补 `attendance_shift` 时段/序号字段」（算法 §8 可能提及 `period_type`/`pair_id`） | 已按主代理裁定落 **生成列 `active_shift_key` + `UNIQUE`**（**D7 显式例外**，软删行置 NULL 不占键、与逻辑删自洽；形态对齐 `employee.phone_active` §11.2）；两个既有普通索引**保留**（表达式键不覆盖）。`attendance_shift` 时段字段**不在本批**，待**算法/架构确认**后另立 V24+（§12.2 待确认项）。如主智能体要求严格回 D7，则改用 Service 查重并承担并发重复风险，须另立版本 |

### 9.2 `employee` 是否需要补索引（P0 备注「如需」的核对结论）

**结论：不需要补索引，不新增 `V3__employee_role_index.sql`，版本号为 V3~V13（未顺延）。** 依据：

1. `employee` 现有索引 `idx_employee_username / idx_employee_phone / idx_employee_dept_id / idx_employee_station_id / idx_employee_create_time` 已覆盖一期与二期全部查询模式：
   - **数据范围收敛（L1）** 只覆盖 query 参数 `stationId` → SQL `WHERE station_id = ?`，由 `idx_employee_station_id` 支撑；
   - **部门归属统计 / 删除前置校验** 由 `idx_employee_dept_id` 支撑；
   - **关键字筛选** `real_name / username LIKE '%x%'` 为前模糊，不建索引是**主动取舍**（一期已论证）；
   - **默认排序** `create_time` 倒序由 `idx_employee_create_time` 支撑。
2. **三角色门槛属端点级（Interceptor 注解），不是 SQL `WHERE role = ?`**：角色判定在 `RequireRolesInterceptor` 用 `UserContext.getRole()` 比对，不落到 `employee` 表查询；唯一按 `role` 过滤的「最后一个管理员」计数（`role='ADMIN' AND status=1`）是低频 COUNT，且表 < 5000 行，全扫 < 1 ms。
3. **`role` 单列区分度极低（仅 3 值）**，为它建索引属滥加索引（写入成本换不到查询收益），与一期「索引克制」原则冲突。
4. 若后续出现「按角色分页列表」的**高频**端点，届时按 ADR-04「只加不删、新版本号」补 `idx_employee_role` 或复合索引即可。

### 9.3 静态自检清单（逐项）

| 检查项 | 方法 | 结论 |
| ---- | ---- | ---- |
| MySQL 8 语法 | 逐脚本核对：`CREATE TABLE ... ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`、注释 `COMMENT`、JSON 列可空、`CREATE INDEX ... DESC`（8.0 支持降序索引） | 通过（未实跑） |
| 表名 / 索引名唯一性 | 全库检索：**44** 表名无重复；索引名全局无跨表冲突（V14/V15、V16~V19 及 V20/V21/V22/V23 新增索引均带表名前缀，与既有索引名无交集） | 通过 |
| 字段名唯一性（表内） | 逐表核对无重复列；`hr_flow` 补列后表内 32 列无重名；`employee` 补 `phone_active`/`position` 后表内 19 列无重名；`auth_trusted_device` 18 列、`employee_registration` 21 列无重名；V20 新增 `station_payroll_setting` 10 列、`payroll_log` 14 列、`payroll_run` **17 列**无重名，`payroll` 补 3 列后表内 32 列无重名；**V21 后 `payroll_log` 16 列、`payroll_run` 18 列、`station_payroll_setting_log` 10 列**无重名；**V22 `attendance_schedule` 补 `active_shift_key` 后表内 9 列、V23 `operation_audit_log` 16 列**无重名 | 通过 |
| 枚举取值与 Mock 一致 | 对照 `dict.js`：notification.type 1-6（公告白名单）+ 7/8/9（薪资系统联动，V21 段 4 补注释；前端字典随 B4 同批）、parcel.status 0-4、sync.status 0-3、work_order.type/status/priority、payroll.status 8 态（V20 补 `OBJECTED`/`PAID`，另见 §9.1 Q-DB-8）、hr.flow/step、leave.status/leave_type/action、kpi 类型/等级、attendance.status/source/check_type 等；V15 `platform` 取值对照架构 §4.1.3（ANDROID/IOS/H5/WEB） | 通过（逐条比对 store 与 dict；payroll 两态待前端字典同批） |
| 批次 / 版本号单调 | V3→V23；V3~V13 与 P1→P10 一致，V14/V15 为登录改造批次，V16~V19 为员工自助注册批次（V16→V17→V18→V19），V20 为薪资结算自动化批次（3 新表 + `payroll` 加列），V21 为其 v1.2/v1.3/v1.4 增量（`payroll_log` 定位列 + `payroll_run.attempt_date` + `uk_attempt` + 删冗余索引 + 新表 `station_payroll_setting_log` + `notification.type` 注释补 7/8/9），V22/V23 为驿站精灵管理能力扩展（`attendance_schedule` 生成列式活跃唯一键 + `operation_audit_log` 新表），无跳号/回填/复用 | 通过 |
| 索引变更附回滚 | V13 `CREATE INDEX` 附 `DROP INDEX` 回滚注释；V14/V19 附 `DROP COLUMN`；V15/V17 附 `DROP TABLE`（内联索引随表删）；V16 附 `DROP INDEX` + `DROP COLUMN`（先删索引后删列）；V18 附 `DROP COLUMN`；V20 附 `DROP TABLE ×3` + `DROP COLUMN ×3` + `MODIFY COLUMN` 还原（先撤 payroll 加列 / COMMENT，再 DROP 三新表）；V21 附 `DROP TABLE ×1` + `DROP INDEX ×2`（`uk_attempt` / `idx_payroll_log_emp_month`）+ `ADD INDEX`（还原 `idx_payroll_run_station_month`）+ `MODIFY COLUMN` ×3（还原 `skip_code` 注释、`payroll_log.payroll_id` 注释、`notification.type` 注释）+ 表 COMMENT 还原 + `DROP COLUMN` ×3（`attempt_date` / `month` / `employee_id`）；**V22 附 `DROP INDEX` + `DROP COLUMN` + 表 COMMENT 还原（先删索引后删列）；V23 附 `DROP TABLE`（索引内联随表删）** | 通过 |
| 未改历史脚本 | `V1`~`V13` 未触碰（git 校验）；V14 仅 `ALTER TABLE hr_flow ADD COLUMN`，未 `DROP`/`MODIFY` 既有列；**V16~V23 均为新增文件，未触碰 V1~V22** | 通过 |
| 新增列可空 / 带默认值 | V14 两新列均可空（`DEFAULT NULL`）；V15 非空列均带 `DEFAULT` 或为业务必填（`employee_id`/`device_fingerprint`/`device_token_hash`/`platform`/`first_seen_time`/`last_seen_time`）；V16 `phone_active` 为生成列、V19 `position` 可空、V18 `source` 非空带 `DEFAULT 'ADMIN'`；V17 非空列均带 `DEFAULT` 或为业务必填（`apply_no`/`real_name`/`phone`/`source`/`status`）；V20 `payroll` 三列均可空无默认，三新表非空列均带 `DEFAULT` 或为业务必填（`station_id`/`target_month`/`trigger_type`/`due_at` 等），无「无默认 NOT NULL 新列」误用；**V21 `payroll_log` 两新列可空（`DEFAULT NULL`）、`payroll_run.attempt_date` 经「先 NULL → `NOT NULL` → 撤默认」三段式最终为 `NOT NULL` 无默认（应用层必填）、`station_payroll_setting_log` 非空列均带 `DEFAULT` 或为业务必填（`station_id`/`action`/`time`）**，无「无默认 NOT NULL 新列」误用；**V22 `attendance_schedule.active_shift_key` 为 `STORED` 生成列（非持久业务数据、无需默认）；V23 `operation_audit_log` 非空列均带 `DEFAULT` 或为业务必填（`target_type`/`target_id`/`action`/`operator_type`/`result`/`time`）**，无「无默认 NOT NULL 新列」误用 | 通过 |
| 敏感值不落库 | V15 全文检索无 `token` 明文列、无正则/验证码列；仅存 `device_token_hash`（摘要）；`last_ip` 出参脱敏由应用层实现。V17 仅存 `password_hash`（BCrypt 摘要）/`query_token_hash`（SHA-256 摘要，一期恒不写入），无明文口令/查询凭据列。V21 `station_payroll_setting_log.before/after` 仅白名单键（不含凭据 / 个人信息）。**V23 `operation_audit_log` 无任何口令列；`before`/`after`/`changed_fields` 白名单化，口令只记布尔（`{"password":"SET"/"RESET"}`），全文检索无口令明文/散列字段** | 通过（静态检索） |
| 无存储过程 / 触发器 / 物理外键 | 全文检索 `PROCEDURE` / `TRIGGER` / `FOREIGN KEY` | 通过（0 命中） |
| 快照与迁移一致 | `init.sql` == V1+V3..V23 表/列/索引逐项比对（含 `hr_flow` 新列 `source`、`employee` 新列 `phone_active`/`position` + `uk_employee_phone_active`、`auth_trusted_device` 表与 3 索引、`employee_registration` 表与 4 索引、V20 `station_payroll_setting`/`payroll_log`/`payroll_run` 三表与其索引、`payroll` 新列 `paid_by_id`/`paid_by_name`/`paid_time` 与 `status` 8 态 COMMENT；**V21 `payroll_log` 补 `employee_id`/`month` + `idx_payroll_log_emp_month`、`payroll_run` 补 `attempt_date` + `uk_attempt` 且移除 `idx_payroll_run_station_month`、`skip_code` COMMENT 去 `EXHAUSTED`、新表 `station_payroll_setting_log` + 其索引；**V22 `attendance_schedule` 补生成列 `active_shift_key` + `uk_attendance_schedule_active_shift`（两个既有普通索引保留）+ 表 COMMENT 更新；V23 新表 `operation_audit_log` + 3 索引**） | 通过（表 **44**、逐列核对；静态比对，未实跑） |
| 无真实数据 / 凭据 | 脚本仅 DDL 与中文注释；无 INSERT（业务数据）、无 IP / 口令 / 密钥 / 令牌明文 | 通过 |
| 中文注释覆盖 | 每列均有 `COMMENT`，每表均有表注释与设计说明头 | 通过 |

### 9.4 未验证项清单（收敛到服务器阶段）

| # | 项 | 复核方法 | 通过标准 |
| - | -- | ---- | ---- |
| U-1 | Flyway 迁移可执行性 | 服务器 `mvn` 启动触发 V3~V21，`flyway_schema_history` 逐条 success | V1~V21 共 21 条迁移无失败；checksum 稳定；V16~V21 为新增最新六条（V16 须预检 0 行后执行；V20/V21 须先过 P0.6 技术评审） |
| U-2 | 表 / 索引真实结构 | `SHOW CREATE TABLE` 逐表比对快照 | 与 `init.sql` 一致 |
| U-3 | `parcel` 索引命中 | `EXPLAIN` 列表查询（按驿站+状态，入库时间倒序） | `type=range/ref`，无 `Using filesort`，命中 `idx_parcel_station_status_inbound` |
| U-4 | `parcel` 分页性能 | 首页 / 第 1000 页 `EXPLAIN ANALYZE` | 游标首页 <20 ms；深分页不达标签发 TODO-1（算法 §12.3） |
| U-5 | `parcel` 落盘容量 | `SHOW TABLE STATUS LIKE 'parcel'` 取 `Data_length`+`Index_length` | 与估算约 106 MB 同量级（±50%） |
| U-6 | 建库字符集 / 排序规则 | `SHOW CREATE DATABASE kdyzgl` | utf8mb4 / utf8mb4_0900_ai_ci |
| U-7 | 应用账号权限（不授 DROP） | `SHOW GRANTS FOR 'hrm_app'@'%'` | 仅 `kdyzgl` 的 DML/DDL（无 DROP、无全局） |
| U-8 | `JSON` 列读写与 MyBatis-Plus 映射 | 服务器冒烟：配置中心增删改、KPI 快照、考勤时段 | 读写正常、无乱码 |
| U-9 | 时间填充行为（应用层） | 服务器验证 `MetaObjectHandler` 填充 `create_time`/`update_time` | 插入/更新均被填充 |
| U-10 | 落盘路径 | 确认 `datadir=/data/mysql-host`、临时目录落 `/data` | 无系统盘写入 |
| U-11 | `hr_flow` 补列后与 Java 映射一致 | 服务器 `SHOW CREATE TABLE hr_flow` 比对 + 后端移除 `HrFlow` 两字段 `exist = false` 后冒烟创建流程 | `operator_id`/`operator_name` 正常落库与回读；出参 `operatorId`/`operatorName` 非空 |
| U-12 | `auth_trusted_device` 唯一键与 upsert 行为 | 服务器 `SHOW CREATE TABLE auth_trusted_device`；同员工同 `device_fingerprint` 重复登记 | 唯一键生效、不产生重复行、`last_seen_time` 被刷新 |
| U-13 | V20 结构真实性与认领槽位幂等键行为 | 服务器 `SHOW CREATE TABLE station_payroll_setting / payroll_log / payroll_run / payroll` 比对快照；对同一 `(station_id, target_month)` **连续写两条 `RUNNING`**；并对 `FAILED`（`claim_key=NULL`）验证可多行 | 表 / 列 / 索引与 `init.sql` 一致；`payroll` 新列与 `status` 8 态 COMMENT 生效；`payroll_run` 含 `uk_payroll_run_claim (station_id, claim_key)` 与 `skip_code`；**第二条 `RUNNING` 触发 1062（认领槽位占位生效）**；`RUNNING`/`SUCCESS`/`SKIPPED` 各至多一条占位行、`FAILED`（`claim_key=NULL`）可多行 |
| U-14 | V21 结构真实性、`attempt_date` 非空落地与日粒度唯一键行为 | ① 服务器 `SHOW CREATE TABLE payroll_log / payroll_run / station_payroll_setting_log` 比对快照；② 确认 `payroll_run.attempt_date` 为 `NOT NULL` 且**无 `DEFAULT`**（`SHOW CREATE TABLE` 无 DEFAULT 子句）、`skip_code` COMMENT 无 `EXHAUSTED`、`idx_payroll_run_station_month` 已不存在、`uk_attempt` 存在；③ 对同一 `(station_id, target_month)` **同日**写两条（第二条无论何状态）：**第二条触发 1062（`uk_attempt` 生效）**；④ 同 `(station_id, target_month)` **不同 `attempt_date`** 写两条：均成功（跨日重试可落行）；⑤ `payroll_log` 两新列与 `idx_payroll_log_emp_month` 生效 | 表 / 列 / 索引与 `init.sql` 一致；`attempt_date` 满足 `NOT NULL` 无默认；`uk_attempt` 与 `uk_payroll_run_claim` 正交共存；`station_payroll_setting_log` 10 列 + 1 索引、无 `is_deleted`/`update_time`；**V21 前置校验：执行后 `payroll_run` 的行均含非空 `attempt_date`** |
| U-15 | V22 生成列/活跃唯一行为 + V23 审计表结构真实性 | ① 服务器 `SHOW CREATE TABLE attendance_schedule`：确认生成列 `active_shift_key` 为 `STORED`、表达式为 `IF(is_deleted=0, CONCAT(employee_id,'|',work_date,'|',shift_id), NULL)`、存在 `UNIQUE KEY uk_attendance_schedule_active_shift`，且 `idx_attendance_schedule_station_date` / `idx_attendance_schedule_emp_date` **仍在**；② **活跃唯一**：同 `(employee_id, work_date, shift_id)` 活跃行写第二条 → **1062**；③ **软删复用**：将已存在活跃行软删（`is_deleted=1`）后再写同 `(employee_id, work_date, shift_id)` → **成功**（软删行生成列为 NULL，不占键）；④ 同员工同天不同 `shift_id` → 均成功（多班次并存）；⑤ 服务器 `SHOW CREATE TABLE operation_audit_log` 比对快照；检索确认**无口令明文/散列字段**、`before`/`after`/`changed_fields` 仅白名单键 | V22 表 / 列 / 索引与 `init.sql` 一致；活跃重复 1062、软删可复用、多班次并存三项行为符合「活跃唯一」语义；V23 16 列 + 3 索引、时间列 `time`、无 `is_deleted`/`update_time`；口令零明文/散列 |

***

## 10. 登录体系改造表结构设计（V14 / V15）

> 权威依据：[multi-client-architecture.md](multi-client-architecture.md) §4.1.3（设备信息采集字段）、§4.2（设备信任模型）、§4.3（会话与时效）；
> 安全依据：[security-auth-review.md](security-auth-review.md) §3（信任态必须由服务端持有）、§4.2（服务端签发 `device_token`，指纹仅弱信号）、§4.4（验证码不入日志/审计）。
> DDL 落位：`V14__hr_flow_operator_columns.sql`、`V15__auth_trusted_device.sql`；快照 `sql/schema/mysql/init.sql`。
> 归属批次：登录体系改造 **M3**（设备信任表 + 指纹）；M1（会话多端化地基）/ M2（配置命名空间 + 适配器端口）均不改表结构。

> **文档定位纠正**：任务背景称设备信任模型在 `multi-client-architecture.md` **§3.2**——实测该文档
> **§3 为「服务器拆分方案」**，设备信任模型实为 **§4.2**（含 §4.2.1 表清单 / §4.2.2 与现有表关系 /
> §4.3 会话与时效）。本章及 V15 依据一律按 §4.2 落地。

### 10.1 本章新增概览

| 版本 | 变更 | 对象（域） | 表数变化 |
| ---- | ---- | ---- | ---- |
| **V14** | `hr_flow` 补 2 列（`operator_id` / `operator_name`） | 人事域（修复 V7 建表缺口） | +0 表，+2 列 |
| **V15** | 新建 `auth_trusted_device`（受信设备） | 认证域（登录体系改造） | +1 表 |

- 表总数 **37 → 38**；`V1`~`V13` 未改动；`init.sql` 快照随之刷新（== V1+V3..V15）。
- 两文件**一文件一职责**：V14 只做补列（不动结构），V15 只建认证域新表。

### 10.2 `auth_trusted_device`（V15）— 受信设备（服务端持有信任态）

**用途**：记录「员工 ↔ 已信任设备」绑定，承载登录免二次验证的**信任态**。核心设计原则（安全报告 §4.2 高危缺陷整改）：
**信任态由服务端持有**——登录/二次验证通过后由服务端签发 `device_token`，本表仅存其**摘要**（`device_token_hash`，SHA-256 hex）；
前端采集的设备属性仅为**弱信号**，经服务端外置盐 HMAC 后落 `device_fingerprint`，用于幂等登记与审计，**不作放行依据**。

**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| employee_id | BIGINT | 否 | - | 归属员工（逻辑外键 employee.id） |
| device_fingerprint | CHAR(64) | 否 | - | 服务端设备指纹摘要（HMAC-SHA256 hex；弱信号，幂等键，非放行依据） |
| device_token_hash | CHAR(64) | 否 | - | 服务端签发 device_token 的摘要（SHA-256 hex；仅存摘要，绝不存明文） |
| device_id | VARCHAR(64) | 是 | NULL | 前端上报设备ID（仅展示/排障，不作放行依据） |
| platform | VARCHAR(16) | 否 | - | 端平台：ANDROID/IOS/H5/WEB |
| model | VARCHAR(64) | 是 | NULL | 设备型号（弱信号快照，仅审计） |
| os_version | VARCHAR(32) | 是 | NULL | 系统版本（弱信号快照，仅审计） |
| app_version | VARCHAR(32) | 是 | NULL | 壳版本（H5 为空；弱信号快照，仅审计） |
| last_ip | VARCHAR(45) | 是 | NULL | 最近来源 IP（IPv4/IPv6；出参脱敏） |
| first_seen_time | DATETIME | 否 | - | 首次受信时间（信任建立时刻，重信不复位；= 任务语义 `trusted_at`） |
| last_seen_time | DATETIME | 否 | - | 最近活跃时间（每次成功校验刷新；= 任务语义 `last_seen_at`） |
| expires_at | DATETIME | 是 | NULL | 信任有效期截止（到期须重新验证；NULL=由配置周期决定） |
| trusted | TINYINT | 否 | 1 | 是否受信：0=否，1=是 |
| revoked | TINYINT | 否 | 0 | 是否已撤销：0=否，1=是（软撤销，保留审计） |
| revoked_at | DATETIME | 是 | NULL | 撤销时间（改密/强制下线/自助撤销时写入，审计） |
| create_time / update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 应用层填充（D8；不使用 ON UPDATE） |

**索引**：

| 索引名 | 类型 / 字段 | 用途 |
| ---- | ---- | ---- |
| uk_auth_trusted_device_emp_fp | UNIQUE (employee_id, device_fingerprint) | 幂等 upsert（同员工同设备只一行）；最左前缀支撑「按员工列设备」 |
| idx_auth_trusted_device_fp | (device_fingerprint) | 登录按指纹反查信任态 |
| idx_auth_trusted_device_token | (device_token_hash) | 客户端持 device_token 时按其摘要校验 |

**枚举取值**：`platform` ∈ {ANDROID, IOS, H5, WEB}（架构 §4.1.3）；`trusted` ∈ {0,1}；`revoked` ∈ {0,1}。

**逻辑关系**：`employee_id` → `employee.id`（逻辑外键，D6，不建物理外键）；设备列表（C1）、撤销（C2）严格限本人（`employee_id` 归属校验，安全报告 §4.2）。
**查询走索引**：设备列表按 `employee_id`（唯一键最左前缀）；登录判定先按 `(employee_id, device_fingerprint)` 命中唯一键、再比对 `device_token_hash`；独立持令牌时走 `idx_auth_trusted_device_token`。

**偏离 db.md §8.0 的两点例外（显式登记，非疏漏）**：

1. **不设 `is_deleted`，改用业务标志 `revoked` + `revoked_at`**。理由：撤销信任是**业务状态**而非逻辑删除——已撤销设备仍需在设备列表中可查、可审计（对应错误码 1107「设备已被撤销」），逻辑删除会使其对用户不可见；且撤销后**复用同一行重新受信**（upsert），若用 `is_deleted` 会与 MyBatis-Plus 全局逻辑删除过滤及唯一键复用语义冲突。
2. **建数据库唯一索引**（`uk_auth_trusted_device_emp_fp`），为决策 D7「不建唯一索引」的**显式例外**。依据：架构 §4.2.1 对该表明确要求 `(employee_id, device_fingerprint)` **唯一、幂等 upsert**；又因第 1 点（撤销≠逻辑删除、行可复用），D7 担心的「逻辑删除后唯一键阻止复用」场景在此**不成立**。若相关方要求严格回到 D7，须放弃 DB 唯一约束、改 Service 查重 + 普通索引并另立版本（见 §9.1 Q-DB-5）。

### 10.3 `hr_flow` 补列（V14）

见 §8.5.4「V14 变更」与 §8.13 迁移索引。要点：`ALTER TABLE hr_flow ADD COLUMN operator_id BIGINT / operator_name VARCHAR(50)`，均可空、列尾追加（INSTANT，无锁无重建），只加不删不改类型（ADR-04）。语义对齐 `hr_flow_step` 与 Mock `hrStore.js`；**联动项**为后端移除 `HrFlow` 两字段的 `@TableField(exist = false)`。

### 10.4 为什么不建表（敏感 / 短生命周期数据一律走 Redis）

| 数据 | 承载方式 | 为什么不落库 |
| ---- | ---- | ---- |
| **短信验证码**（6 位） | Redis `hrm:sms:code:{scene}:{phone}`，TTL 300s，校验成功即删（一次性） | ① **敏感明文红线**：验证码明文落库违反「敏感值不落库明文」（安全报告 §4.4 ⑥/⑦）；② 生命周期极短（≤5min）、高频读写、需原子 TTL 与一次性删除，落库产生海量无用写入与清理负担 |
| 验证码**校验失败计数** | Redis `hrm:sms:attempt:{scene}:{phone}`，TTL 300s | 同属短时状态；达上限即作废验证码（错误码 1103），无需持久化 |
| **二次验证票据** `twoFactorTicket` | Redis（短 TTL，如 5min） | 仅衔接「密码登录 → 短信二次验证」的一次性凭据，流转即失效；落库无审计价值且扩大泄露面 |
| **`device_token` 明文** | 仅服务端签发 → 下发客户端（HttpOnly Cookie 或独立 claim） | 库中**只存摘要** `device_token_hash`；明文落库=泄露即等价长期信任凭证 |
| **登录会话**（含 `sid`/`jti`/`role`/`stationId`） | Redis `hrm:session:{sid}` + 索引 `hrm:session:idx:{employeeId}`（M1） | 会话为可容忍丢失的短时状态（丢失=重登），且需 TTL 双控与高频读写；落库会拖慢认证主链路 |
| **设备指纹盐** `hrm.auth.device-fingerprint-salt` | 服务器外置配置（主智能体托管） | 密钥/盐的安全红线：不入库、不进仓库、不进日志 |

> 上述「不建表」均为**主动取舍**，与 `security-auth-review.md` §4.4 的验证码红线一致；Redis 键规范见架构 §2.3.2 / §4.4.3。

### 10.5 与架构 / 安全报告的一致性核对（守契约）

| 核对项 | 依据口径 | 本章处置 | 结论 |
| ---- | ---- | ---- | ---- |
| 表名 / 字段 | 架构 §4.2.1 表1 `auth_trusted_device` | 表名与字段逐个沿用架构定义 | 一致 |
| 唯一索引 | 架构 §4.2.1 写 `uk_auth_device_emp_fp (employee_id, device_fingerprint)` | 落 `uk_auth_trusted_device_emp_fp`（**命名纠正**：按 §1.5 / skill 规范「索引名带**完整表名**前缀，双库可对照」，架构用缩写 `auth_device` 与 db.md「`idx_表名_字段`」冲突，此处以 db.md 为准） | **命名纠正**，语义一致（唯一、幂等 upsert） |
| 次级索引 | 架构 §4.2.1 写 `idx_auth_device_fp` | 落 `idx_auth_trusted_device_fp`（同名纠正，同上） | 命名纠正 |
| 新增令牌摘要列 | 任务要求「服务端签发设备标识/令牌摘要，仅存摘要」；架构 §4.2.1 未列 | 新增 `device_token_hash` CHAR(64)（+ `idx_auth_trusted_device_token`） | **必要补充**（安全报告 §4.2 核心整改：信任态服务端持有），非语义冲突 |
| 时效字段 | 任务列的 `trusted_at` / `last_seen_at` | 沿用架构 `first_seen_time` / `last_seen_time`（语义一一对应）；并按安全报告 §4.2「设有效期」补 `expires_at` | 命名以架构为准 + 必要补充 |
| 撤销字段 | 任务列 `revoked_at`（或 status）；架构 §4.2.1 列 `trusted` / `revoked` | 沿用 `trusted` / `revoked`，并补 `revoked_at`（审计时间） | 一致 + 必要补充 |
| 敏感值不落库 | 安全报告「指纹仅弱信号、不得作放行依据」 | `device_fingerprint` 标注弱信号/审计用；`device_token_hash` 仅摘要；验证码等走 Redis（§10.4） | 一致 |
| `auth_sms_log` | 架构 §4.2.1 表2「可选但建议」 | 本轮**未纳入**（超出「两个变更」范围），登记为待裁定（§9.1 Q-DB-6），可另立新版本号 | 已登记差异，非遗漏 |

***

> **收敛声明**：本章 DDL 与快照均为**静态产出**，本机无 MySQL，**未实跑迁移**；`SHOW CREATE TABLE` 逐表比对、
> Flyway `flyway_schema_history` 校验、唯一键 upsert 行为验证**收敛到服务器阶段**（见 §9.4 U-1/U-2/U-11/U-12）。
> **迁移执行属 C 档**，须主智能体三步授权后由运维执行，上线前备份库。

***

## 11. 员工自助注册表结构设计（V16~V19）

> **权威设计**：[registration-design.md](registration-design.md)（**v1.3**，技术评审复评「通过」）§2 数据设计、§7 兼容与迁移、
> §11.6 手机号唯一缺陷（M-5）、§11.9 岗位方案乙（U-07）；批次 §9-B2。
> **DDL 落位**：`V16__employee_phone_unique.sql`、`V17__employee_registration.sql`、`V18__hr_flow_source.sql`、
> `V19__employee_position.sql`；快照 `sql/schema/mysql/init.sql`。**仅 MySQL**（`postgresql/` 自 V2 冻结，
> 本期**不产出 pg 脚本与 pg 快照**，见 §0.4/§7 与 §5.3）。

### 11.1 本章新增概览

| 版本 | 变更 | 域 | 增量 |
| ---- | ---- | ---- | ---- |
| **V16** | `employee` 补生成列 `phone_active` + 唯一键 `uk_employee_phone_active`（活跃唯一） | 员工/账号 | +0 表，+1 列，+1 唯一索引 |
| **V17** | 新建 `employee_registration`（注册事实与凭据载体） | 员工自助注册 | +1 表 |
| **V18** | `hr_flow` 补 `source` 列（`NOT NULL DEFAULT 'ADMIN'`） | 人事域（M-9 来源留痕） | +0 表，+1 列 |
| **V19** | `employee` 补 `position` 列（岗位进档案，方案乙） | 员工档案（U-07） | +0 表，+1 列 |

- 表总数 **38 → 39**；`V1`~`V15` 未改动；`init.sql` 快照随之刷新（== V1+V3..V19）。
- **迁移顺序与依赖**：V16（既有缺陷修复，**与注册解耦、可先行**）→ V17（新表）→ V18（来源）→ V19（岗位）。**V16 为注册上线前置**（M-5）。
- 各脚本**一文件一职责**、**注释附回滚语句**；全部**属 C 档（结构变更）**，须主智能体 §10.3 三步授权后执行，执行前备份。

### 11.2 `employee` 活跃唯一（V16）— 手机号缺陷修复（M-5 / REG-04）

**用途**：消除既有缺口——`createEmployeeForFlow` 仅校验 `username`、不校验 `phone`，`employee.phone` 无 DB 唯一索引，
重复号会使按 phone `selectOne` 命中多行抛异常 → 目标账号登录/短信登录 **DoS**。

**实现（U-05 定稿，仅 mysql）**：

```sql
ALTER TABLE `employee`
  ADD COLUMN `phone_active` VARCHAR(20)
      GENERATED ALWAYS AS (IF(`is_deleted` = 0, `phone`, NULL)) STORED
      COMMENT '活跃手机号生成列：is_deleted=0 取 phone，否则 NULL；仅活跃行唯一（V16）',
  ADD UNIQUE KEY `uk_employee_phone_active` (`phone_active`);
```

- **仅活跃行唯一**：`is_deleted=1` 行 `phone_active=NULL`，`UNIQUE` 对 NULL 不去重 → **已删号可复用**；无需 PG 式部分索引。
- **硬性断言**：若活跃行存在重复 `phone`，**本 ALTER 自身以 1062 报错、迁移失败**（MySQL 8 单条 DDL 原子，失败不残留半成品）——即「预检非 0 行 → 迁移失败」。
- **D7 显式例外**：理由为公开端点并发 + `phone` 为登录标识（重复号后果为登录 DoS，非仅脏数据），登记同 §10.2 体例。
- **生成列须重建表**（ALGORITHM=COPY/INPLACE）；`employee` < 5000 行、非大表，**不触发**「大表变更须数据库+算法联评」。
- 不删既有 `idx_employee_phone`（仍服务查重/筛选），不改既有列、不回填数据。
- 回滚：`DROP INDEX uk_employee_phone_active` → `DROP COLUMN phone_active`（先索引后列）。

**执行前置 · 存量重复手机号预检 SQL（必须返回 0 行）**：

```sql
-- MySQL：活跃员工中重复手机号（必须含 AND phone IS NOT NULL，
-- 否则 is_deleted=0 且 phone IS NULL 的多行会被 GROUP BY 归为一组误报）
SELECT phone,
       COUNT(*)                     AS cnt,
       GROUP_CONCAT(id ORDER BY id) AS ids,
       GROUP_CONCAT(real_name)       AS names
FROM employee
WHERE is_deleted = 0
  AND phone IS NOT NULL
GROUP BY phone
HAVING COUNT(*) > 1;
```

> 预检**非 0 行 → 先人工去重**（保留哪条属**数据变更**，须 C 档授权 + 人工确认，安全 R-5），去重后再执行 V16；禁止未去重强跑。

### 11.3 `employee_registration`（V17）— 员工自助注册申请单

**用途**：注册事实与**凭据载体**（密码散列、查询凭据、来源审计）；审批载体仍为 `hr_flow`（复用步骤机/权限/PC 审批台），
两表以 `registration.flow_id ↔ hr_flow.id` **1:1** 关联，职责单一、互不污染（registration-design.md §2.1 结论③）。

**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| apply_no | VARCHAR(32) | 否 | - | 申请编号（`UNIQUE`，形如 `RG-YYYYMMDD-0001`，两段式生成） |
| flow_id | BIGINT | 是 | NULL | 关联审批单（逻辑外键 `hr_flow.id`，提交时写入） |
| real_name | VARCHAR(50) | 否 | - | 姓名（2-20，对齐既有 onboarding 校验口径） |
| phone | VARCHAR(20) | 否 | - | 手机号（`^1[3-9]\d{9}$`；活跃唯一由 employee/hr_flow 侧收口） |
| password_hash | VARCHAR(100) | 是 | NULL | 注册自设密码 BCrypt 散列（cost=10；**仅合规留痕、非初始口令**；终态置 NULL） |
| apply_station_id | BIGINT | 是 | NULL | 意向驿站（逻辑外键 `station.id`；**仅意向**，M-3） |
| apply_position | VARCHAR(50) | 是 | NULL | 意向岗位（自由文本，对齐 `hr_flow.position`；**仅意向**，M-3） |
| source | VARCHAR(16) | 否 | 'STAFF_H5' | 注册渠道来源（审计；一期仅 `STAFF_H5`） |
| agreement_version | VARCHAR(20) | 是 | NULL | 已同意的服务条款版本（合规留痕） |
| query_token_hash | VARCHAR(64) | 是 | NULL | 查询凭据 SHA-256（**一期不启用、恒不写入**，R-3/U-06；列保留供后续自助查询） |
| status | VARCHAR(16) | 否 | 'SUBMITTED' | `SUBMITTED`/`APPROVED`/`REJECTED`/`EXPIRED`（`CANCELLED` 保留不用） |
| reject_reason | VARCHAR(200) | 是 | NULL | 驳回原因快照 |
| approved_employee_id | BIGINT | 是 | NULL | 通过后生成的员工（逻辑外键 `employee.id`） |
| approve_time | DATETIME | 是 | NULL | 通过时间 |
| cancel_time | DATETIME | 是 | NULL | 取消时间（一期不用，随 R-4 取消而保留列） |
| expire_time | DATETIME | 是 | NULL | 失效判定基准（`create_time + 7` 天，惰性判定） |
| client_ip | VARCHAR(50) | 是 | NULL | 提交来源 IP（审计；口径对齐 `login_log.login_ip`；出参脱敏） |
| is_deleted | TINYINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time / update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 应用层填充（D8） |

**索引**：

| 索引名 | 类型 | 字段 | 用途 |
| ---- | ---- | ---- | ---- |
| uk_employee_registration_apply_no | **UNIQUE** | apply_no | 申请编号唯一（U-17，公开端点并发高、无逻辑删除复用语义） |
| idx_employee_registration_phone | 普通 | phone | 重复提交查重 |
| idx_employee_registration_flow | 普通 | flow_id | 审批单 ↔ 申请单回关联 |
| idx_employee_registration_status | 普通 | status, create_time | 列表（按状态 + 时间倒序） |

**逻辑关系**：`flow_id` → `hr_flow.id`；`apply_station_id` → `station.id`；`approved_employee_id` → `employee.id`（均逻辑外键，D6）。
**凭据卫生**：`password_hash` / `query_token_hash` 在进入任一终态（`APPROVED`/`REJECTED`/`EXPIRED`）时**由 Service 同事务置 NULL**（应用层实现，非 DB 约束）。
**唯一索引例外**：`apply_no` 增设 `UNIQUE`（U-17）；`phone` 仍走 Service 查重 + 普通索引，与 D7 不矛盾（§2.4）。
**`TODO(扩展)`**：M-8 清理任务若按 `status + expire_time` 过滤，可后续补 `idx_employee_registration_expire (status, expire_time)`；本期按批次口径仅落 3 普通索引。
**回滚**：`DROP TABLE IF EXISTS employee_registration`（索引内联，随表删）。

### 11.4 `hr_flow` 补列（V18 / M-9）

见 §8.5.4「V18 变更」。要点：`ALTER TABLE hr_flow ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'ADMIN'`（列尾追加、INSTANT）。
存量行由 `DEFAULT` 一次性回填 `ADMIN`（与业务事实一致、无需 UPDATE）；**不加索引**（低基数）。
`source` 为**业务来源**（`ADMIN`/`SELF_REGISTER`），与 §11.3 `employee_registration.source` 的**注册渠道**（`STAFF_H5`）语义不同、并存。
**回滚**：`DROP COLUMN source`（数据丢失范围 = 补列后写入的来源标识）。

### 11.5 `employee` 补列（V19 / 方案乙 U-07）

见 §3.3「position」行。要点：`ALTER TABLE employee ADD COLUMN position VARCHAR(50) DEFAULT NULL`（列尾追加、INSTANT）。
**存量不回填**（NULL 表示未登记，不以历史 `flow.position` 伪造档案事实；如需回填另立 C 档脚本，T10/E7）；**不加索引**（低基数）；
**不字典化**（最小形态，`TODO(扩展): 岗位字典 T9`）。`employee.position` 为**权威事实**，`hr_flow.position` 为该次流程**过程值与留痕**，
双写点唯一（`assignForFlow` 同方法同事务内双写，禁止他处单独写）。**回滚**：`DROP COLUMN position`。

### 11.6 与方案 / 上游的一致性核对（守契约）

| 核对项 | 依据口径 | 本章处置 | 结论 |
| ---- | ---- | ---- | ---- |
| V16 形态 | registration-design.md §11.6（仅 mysql） | 生成列 `phone_active` + `UNIQUE(phone_active)`，NULL 可重复 | 一致 |
| V17 字段 / 索引 | §2.2 字段级设计表 | 逐列落库；`apply_no` UNIQUE（U-17）+ 3 普通索引 | 一致 |
| V18 / V19 | §7 / §11.9 | 加列（`NOT NULL DEFAULT 'ADMIN'` / `NULL`），不加索引 | 一致 |
| 表总数 | §7 同步项「38 → 39」 | 38 → 39（仅新增 1 表） | 一致 |
| pg 冻结 | §0.4/§7、db.md:8/401 | 仅 mysql 脚本 + mysql 快照，不产出 pg | 一致 |
| C 档 | §7 / §9-B2 / §11.6④ | 全部脚本未执行，须主智能体三步授权 | 一致（未执行） |

> **收敛声明**：本章 DDL 与快照均为**静态产出**，本机无 MySQL，**未实跑迁移**；`SHOW CREATE TABLE` 逐表比对、
> Flyway `flyway_schema_history` 校验、V16 预检实跑、唯一约束行为验证**收敛到服务器阶段**（见 §9.4 U-1/U-2）。
> **迁移执行属 C 档**，须主智能体三步授权后由运维执行；**执行前须先跑 §11.2 预检返回 0 行并备份库**。

***

## 12. 驿站精灵管理能力扩展表结构设计（V22 / V23）

> **权威设计**：[boss-management-architecture.md](boss-management-architecture.md) §2.4（ARCH-S-1 排班多班次唯一键）、
> §3（ARCH-S-2 审计留痕，安全 M-1 / REG-01）；**评审**：[tech-review-boss-management.md](tech-review-boss-management.md)（**打回**，
> 本次为修订稿，必改项 M-1 / M-6 / M-7）。
> **DDL 落位**：`V22__attendance_schedule_multi_shift.sql`、`V23__operation_audit_log.sql`；快照 `sql/schema/mysql/init.sql`。
> **仅 MySQL**（`postgresql/` 自 V2 冻结，本期**不产出 pg 脚本与 pg 快照**，见 §5.3）。
> 本机无 MySQL，本章为**静态产出，未实跑**；`SHOW CREATE TABLE` 与唯一约束行为**收敛到 §9.4 U-15**。

### 12.1 本章新增概览

| 版本 | 变更 | 域（批次） | 增量 |
| ---- | ---- | ---- | ---- |
| **V22** | `attendance_schedule` 补生成列 `active_shift_key` + 唯一键 `uk_attendance_schedule_active_shift`（活跃唯一） | 考勤/排班（B7） | +0 表，+1 生成列，+1 唯一索引 |
| **V23** | 新建 `operation_audit_log`（操作审计留痕，追加型） | 审计（B1，安全 M-1 / REG-01） | +1 表 |

- 表总数 **43 → 44**；`V1`~`V21` 未改动；`init.sql` 快照随之刷新（== V1+V3..V23）。
- 两脚本**一文件一职责**、**注释附回滚语句**；全部**属 C 档（结构变更）**，须主智能体 §10.3 三步授权后由运维执行，执行前备份。
- **前置门禁**：两脚本须先经 **P0.6 技术评审复评「通过 / 有条件通过」**（本次为评审**打回**后修订，须重评）；V22 另需**用户裁定 A-①/A-②** 与**预检② 返回 0 行**；V23 另需**安全复验 M-1**。

### 12.2 `attendance_schedule` 活跃唯一改造（V22）

**用途**：支撑「一天多班次」（同员工同天可并存不同 `shift_id`），并为多班次提供 **DB 层「活跃唯一」最终防线 + 并发防线**（§8.3.3 已列字段/索引，本节补迁移专用说明）。

**变更语句（DDL-only，无 DML）**：

```sql
ALTER TABLE `attendance_schedule`
  ADD COLUMN `active_shift_key` VARCHAR(64)
      GENERATED ALWAYS AS (IF(`is_deleted` = 0, CONCAT(`employee_id`, '|', `work_date`, '|', `shift_id`), NULL)) STORED
      COMMENT '活跃排班键生成列：is_deleted=0 时拼 employee_id|work_date|shift_id，否则 NULL；仅活跃行唯一（V22）',
  ADD UNIQUE KEY `uk_attendance_schedule_active_shift` (`active_shift_key`),
  COMMENT = '排班（员工+日期+班次 活跃唯一：生成列 active_shift_key 唯一键收口「活跃唯一」，软删行置 NULL 不占键；V22）';
```

- **生成列表达式与类型**：`VARCHAR(64)`，`IF(is_deleted=0, CONCAT(employee_id,'|',work_date,'|',shift_id), NULL) STORED`。最长 `20+1+10+1+20=52 < 64`（`employee_id`/`shift_id` 为 BIGINT ≤ 20 位、`work_date` 定宽 10 位）。
- **与逻辑删自洽（评审 M-1）**：`AttendanceSchedule` 实体带 `@TableLogic`（**逻辑删**），软删行生成列置 `NULL` → 唯一索引允许多 NULL → **软删行不占键**，「清空班次（软删）→ 再排同班次」不再 1062；活跃行同 `(employee_id, work_date, shift_id)` 仍被原子拒绝。
- **冗余索引取舍（结论：两个普通索引均保留）**：唯一键建在**单个生成列表达式**上，最左前缀即表达式本身，**不能**服务 `WHERE station_id / employee_id / work_date`（表达式索引不按组成列下钻）→ `idx_attendance_schedule_station_date`、`idx_attendance_schedule_emp_date` **均不被覆盖，全部保留**。对照 §8.6.7 `uk_attempt`（原始复合列、可覆盖普通索引故删之），本处前提不同，不删任何索引。
- **规模与锁**：STORED 生成列须重建表（`ALGORITHM=COPY/INPLACE`）；`attendance_schedule` 非现有设计所列大表（对照 `parcel` 20 万级），但**本机未实测存量行数** → 建议低峰执行，**是否触发「大表（数据库+算法）联评」由主智能体按实测行数裁定**。

**执行前置 · 存量查重预检（评审 M-6：范围必须显式包含 `is_deleted=1` 的软删行）**：

```sql
-- 预检①（全量，含软删行；诊断用，允许 > 0 行）：核实软删行确实不占键（全为 NULL）并留证
SELECT employee_id, work_date, shift_id,
       SUM(is_deleted = 0) AS active_cnt,
       SUM(is_deleted = 1) AS deleted_cnt,
       GROUP_CONCAT(CONCAT(id, ':', is_deleted) ORDER BY id) AS id_deleted_pairs
FROM attendance_schedule
GROUP BY employee_id, work_date, shift_id
HAVING SUM(is_deleted = 0) > 1 OR SUM(is_deleted = 1) > 1;

-- 预检②（阻断判据：仅活跃行重复组，必须返回 0 行）—— 生成列式唯一下的正确判据
SELECT employee_id, work_date, shift_id,
       COUNT(*)                     AS active_cnt,
       GROUP_CONCAT(id ORDER BY id) AS ids
FROM attendance_schedule
WHERE is_deleted = 0
GROUP BY employee_id, work_date, shift_id
HAVING COUNT(*) > 1;
```

> **纳入软删行的理由（M-6）**：原口径默认「物理删」、只查活跃行；若采用**裸三元 `UNIQUE`**，则 `is_deleted=1` 行同样占键，预检漏软删行 → 通过后建键仍 1062。本脚本改用生成列式部分唯一后，**建键失败的充要条件 = 预检② 非 0 行**（仅活跃重复）；预检① 的软删分组即便 > 0 也**不阻断**（软删行 → NULL）。预检② 非 0 行须先人工合并/软删重复活跃行（属数据变更，须 C 档授权 + 人工确认）后再执行，禁止未处理强跑。
**回滚**：`DROP INDEX uk_attendance_schedule_active_shift` → `DROP COLUMN active_shift_key` → 表 COMMENT 还原（对齐 V16 手法，先索引后列）；若已依赖多班次，回滚须与后端 Service 查重键（`(employee_id, work_date)` → `+shift_id`）同批。
**`TODO(扩展)` / 待确认（不在本批，勿擅自加列）**：`attendance_shift` 是否补时段/序号字段（`period_type`/`pair_id`/`shift_no`），属架构 D-6「本批不补」，待**算法/架构确认**后另立 V24+（登记 Q-DB-11 / §6.3 T3）。

### 12.3 `operation_audit_log`（V23）— 操作审计留痕（追加型）

**用途**：通用追加型操作审计，承载安全必做项 **M-1 / REG-01** —— 覆盖 `employee` / `station` 的**新增 / 编辑 / 启停 / 删除 / 重置口令** 9 个写入点的留痕（架构 §3.3），回答「**谁在何时把什么从 X 改成 Y**」。形态对齐 `payroll_log`（§8.6.6）/ `station_payroll_setting_log`（§8.6.8）/ `leave_log`（§7.2）。
**字段**：

| 字段 | 类型 | 允许空 | 默认值 | 注释 |
| ---- | ---- | ---- | ---- | ---- |
| id | BIGINT AI | 否 | - | 主键 |
| operator_id | BIGINT | 是 | NULL | 操作人（逻辑外键 `employee.id`；SYSTEM 触发为空） |
| operator_name | VARCHAR(50) | 是 | NULL | 操作人姓名快照（对齐 `payroll_log`） |
| operator_role | VARCHAR(20) | 是 | NULL | 操作人角色快照（`ADMIN`/`STATION_ADMIN`/`STAFF`） |
| operator_type | VARCHAR(16) | 否 | 'USER' | 操作主体：`USER`=人工，`SYSTEM`=系统自动（对齐 `payroll_log.operator_type`） |
| target_type | VARCHAR(16) | 否 | - | 目标类型：`EMPLOYEE`=员工账号 / `STATION`=驿站（扩展只增取值，不改表结构） |
| target_id | BIGINT | 否 | - | 目标主键（逻辑外键：按 `target_type` 指向 `employee.id` 或 `station.id`） |
| target_name | VARCHAR(64) | 是 | NULL | 目标名称快照（员工姓名 / 驿站名；目标逻辑删后仍可知「改的是谁/哪个驿站」，免回表） |
| action | VARCHAR(16) | 否 | - | 动作：`CREATE`=新增 / `UPDATE`=编辑 / `CHANGE_STATUS`=启停 / `DELETE`=删除 / `RESET_PASSWORD`=重置口令 |
| before | JSON | 是 | NULL | 变更前快照（**白名单键**；口令只允许布尔标记，如 `{"password":"RESET"}`，**绝不落明文或散列**） |
| after | JSON | 是 | NULL | 变更后快照（**白名单键**；同 `before` 口令约束） |
| changed_fields | JSON | 是 | NULL | 发生变化的字段名白名单（JSON 数组，如 `["realName","phone"]`）；口令变更只记 `"password"` 字段名 |
| client_ip | VARCHAR(50) | 是 | NULL | 客户端 IP（Nginx 透传 `X-Forwarded-For` 首个；口径对齐 `login_log.login_ip` / `employee_registration.client_ip`） |
| result | VARCHAR(10) | 否 | 'SUCCESS' | 结果：`SUCCESS`=成功，`FAIL`=失败 |
| fail_reason | VARCHAR(200) | 是 | NULL | 失败原因（截断，不落敏感信息 / 口令；`result=FAIL` 时可选填） |
| time | DATETIME | 否 | CURRENT_TIMESTAMP | 操作时间（**只插不改，无 `update_time`**；命名对齐 `leave_log`/`payroll_log` 的 `time`，评审 M-7） |

**索引**（两条主路径 + 动作审计）：

| 索引名 | 类型 | 字段 | 用途 |
| ---- | ---- | ---- | ---- |
| idx_operation_audit_target | 普通 | (target_type, target_id, `time`) | 按目标对象查历史（「谁在何时改了哪个站长/哪个驿站」） |
| idx_operation_audit_operator | 普通 | (operator_id, `time`) | 按操作人/时间审计 |
| idx_operation_audit_action_time | 普通 | (action, `time`) | 按动作类型/时段审计（如统计全部 `RESET_PASSWORD`） |

**逻辑关系**：`operator_id` → `employee.id`；`target_id` 按 `target_type` → `employee.id`（`EMPLOYEE`）或 `station.id`（`STATION`）（均逻辑外键，D6）。
**查询走索引**：按对象取留痕走 `idx_operation_audit_target`；按操作人检索走 `idx_operation_audit_operator`；按动作类型/时段审计走 `idx_operation_audit_action_time`。
**例外（追加型）**：**不设 `is_deleted` / `update_time`**（§8.0(4)），应用层禁止 UPDATE / DELETE，只增不改。
**时间列命名（评审 M-7 核对结论）**：既有留痕表业务时间列**统一为 `time`**——`leave_log.time`（§7.2）、`payroll_log.time`（§8.6.6）、`station_payroll_setting_log.time`（§8.6.8）→ 本表定名 **`time`**（非 `create_time`），不新造第三套命名。
**口令脱敏硬约束（§3.4，M-1 验收）**：**任何字段都不得落明文或 BCrypt 散列**；`before` / `after` / `changed_fields` 一律**白名单化**，口令只允许记录**布尔标记**（`{"password":"SET"}` / `{"password":"RESET"}`）；验收口径：**全表检索无口令明文/散列**。
**写入关系**：与业务写同事务或失败可补偿（二选一由后端定稿，架构 T9）；写入实现（AOP 切面 / Service 显式）由后端定稿。`changed_fields` / `before` / `after` 的键名白名单由后端服务端裁剪（对齐 api.md I-7），非 DB 约束。
**回滚**：`DROP TABLE IF EXISTS operation_audit_log`（索引内联，随表删）；若已产生审计数据，回滚前须确认留痕已另有归档。
**`TODO(扩展)` / 待确认**：审计范围是否扩展至更多敏感写操作（部门 / 角色 / 薪资规则等）——架构 A-⑥ 待主智能体裁定，本期按 M-1 最小集落表，后续**只增 `target_type`/`action` 枚举**，不改表结构；归档 / 保留策略本期不做（架构 §3.5 / T6）。

### 12.4 与方案 / 评审的一致性核对（守契约）

| 核对项 | 依据口径 | 本章处置 | 结论 |
| ---- | ---- | ---- | ---- |
| V22 键形态 | 主代理裁定「生成列式部分唯一」+ 评审 M-1「须与 `@TableLogic` 逻辑删自洽」 | 生成列 `active_shift_key` + `UNIQUE`，软删行置 NULL 不占键 | 一致（对齐 §11.2 / V16 手法） |
| V22 索引取舍 | 架构 §2.4 交数据库工程师评估删冗余索引 | **两个既有普通索引均保留**（表达式唯一键不吃其最左前缀） | 一致（结论：不构成冗余） |
| V22 预检 | 评审 M-6「范围含软删行」 | 预检①（全量含软删，诊断）+ 预检②（仅活跃，阻断，必须 0 行） | 一致（区分诊断/阻断口径） |
| V23 字段 / 索引 | 架构 §3.2 表设计顶层（含 `operator_type`）+ 评审核对 5 | 16 列 + 3 索引；`action` 取 `CHANGE_STATUS`（主代理指令，架构 §3.2 原写 `STATUS`） | 一致（`CHANGE_STATUS` 按主代理指令） |
| V23 时间列命名 | 评审 M-7 对齐既有留痕 | `time`（对齐 `leave_log`/`payroll_log`/`station_payroll_setting_log`） | 一致（不新造第三套命名） |
| V23 口令安全 | 架构 §3.4 | 无口令列；`before`/`after`/`changed_fields` 白名单化、口令只记布尔 | 一致（全表无明文/散列） |
| 表总数 | 架构 §0.2「迁移 2 个」/ §2.8「结构变更 2」 | 43 → 44（+1 表；`attendance_schedule` +1 列 +1 唯一键） | 一致 |
| pg 冻结 | §0.4/§5.3、db.md:8 | 仅 mysql 脚本 + mysql 快照，不产出 pg | 一致 |
| C 档 | §5 B1/B7、§10.3 | 全部脚本未执行，须主智能体三步授权 | 一致（未执行） |

**待确认项（须上游拍板）**：① `attendance_shift` 时段/序号字段（架构 D-6 / Q-DB-11，待算法/架构）；② 审计最小集是否扩展（架构 A-⑥，待主智能体）；③ `action` 取值口径（本表按主代理指令用 `CHANGE_STATUS`，架构 §3.2 原写 `STATUS`，须与后端 `api.md` 定稿同步）。

> **收敛声明**：本章 DDL 与快照均为**静态产出**，本机无 MySQL，**未实跑迁移**；`SHOW CREATE TABLE` 逐表比对、V22 预检实跑、活跃唯一/软删复用行为、V23 口令脱敏检索**收敛到服务器阶段**（见 §9.4 U-15）。
> **迁移执行属 C 档**，须主智能体三步授权后由运维执行；**V22 执行前须先跑 §12.2 预检② 返回 0 行并备份库**。

