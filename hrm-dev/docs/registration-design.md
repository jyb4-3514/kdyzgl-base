# 员工自助注册 · 架构设计方案（含接口契约草案 / 数据表设计 / 流程设计）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.1（草案 · 安全评估响应增量）** |
| 编写日期 | 2026-09-26 |
| 状态 | **待技术评审**（本方案属方案阶段产物，报主智能体审批前须经技术评审工程师评估；本次修订**不宣称已通过**） |
| 产出角色 | 架构师 `express-station-architect` |
| 上游需求口径 | **用户已确认**（见 §0.3）；本方案只做设计，不改需求 |
| 关联文档 | [api.md](api.md)（体例与编号真源）、[db.md](db.md)（表结构规范真源）、[requirement.md](requirement.md)、[security-auth-review.md](security-auth-review.md)、[multi-client-architecture.md](multi-client-architecture.md)、[plan.md](plan.md) |
| 安全面并行产物 | [security-registration-review.md](security-registration-review.md)（331 行，结论**「阻断（公网）」**）——已在本版 **§11** 逐条响应（原 v1.0 的"不存在"阻塞项已解除，见 §0.2-③） |
| 本方案边界 | 只产出设计；**不含**业务实现代码、不含 Flyway 脚本正文、不执行部署/MCP/git |

***

## 0. 取证方式与已核实事实

### 0.1 取证方式

只读代码与文档，未运行任何构建/测试（本机无 JDK/MySQL/Redis，见 [TASK.md](../TASK.md) 纪律 2）。全部结论标注 `文件:行号` 出处；不确定处标「需确认」并给确认方式。

### 0.2 事实性纠正（任务描述 / 既有材料 与实测出入）

| # | 描述 | 实测事实 | 出处 |
| --- | --- | --- | --- |
| ① | 任务称「hrm-server 存在，含入离职/员工/薪资/短信」 | **成立**（首次 `LS` 因输出截断未见 `hrm-server`，`Glob` 复核后确认存在） | — |
| ② | 任务称登录页在 `hrm-clients/apps/staff-h5/src/views/login/index.vue` | **成立**（该文件存在，双 Tab + `createCountdown` + `isPhone` 均在） | [index.vue:74-102](../hrm-clients/apps/staff-h5/src/views/login/index.vue#L74-L102) |
| ③ | 任务称存在 `docs/security-registration-review.md`（并行安全评估） | **~~不存在~~ 已被修订推翻**：该报告**已产出（331 行，结论「阻断（公网）」）**，本方案 v1.1 已逐条响应，见 **§11** | `security-registration-review.md` |
| ④ | [api.md](api.md) 头部标「接口总数 46」，§4.0 概览亦为 46 | **~~与代码不一致／历史版本残留~~ 已被本次复核推翻**：§4.0 的 46 **已自洽**（认证4+看板1+员工10+部门4+驿站5+考勤10+补卡4+班次4+排班4＝46，考勤域 22 端点为**主智能体已补录**）。但全仓 controller 方法级映射共 **151** 个，§4.0 仅覆盖 46、§7 覆盖 16，**缺口 83 端点**——新接口沿用**体例**而非沿用**计数**；复核结论见 **§11.10** | [api.md:184-234](api.md)、[api.md:1134](api.md)；controller 目录见 [controller/](../hrm-server/src/main/java/com/qiujie/controller/) |
| ⑤ | [requirement.md](requirement.md) 明确「忘记密码自助找回」**不做**（走线下） | 成立：「忘记密码自助找回 → 线下联系管理员重置」 | [requirement.md:336](requirement.md) |
| ⑥ | 任务称「岗位」可能是实体 | **不存在「岗位」实体**。`position` 在 `hr_flow` 为 `VARCHAR(50)` 自由文本，`employee` 表**无 position 列**（岗位仅存于流程表）。**不得臆造岗位表** | [HrFlow.java:56-57](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L56-L57)、[db.md:766](db.md)、[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66) |

### 0.3 用户已确认的需求口径（不改，只映射）

1. 入口：驿站助手（员工端 H5，`apps/staff-h5`）**登录页**新增「员工注册」入口。
2. 注册字段：姓名 + 手机号 + **短信验证码** + 意向驿站 + 意向岗位 + 本人设置的密码。
3. 提交后：生成**入职审批单**，进入**既有入离职模块的入职审批**流程（人工审批）。
4. 审批通过后三件事**同时完成**：生成员工档案 + 设置薪资 + 设置岗位/站点/角色。
5. 本接口**公网可达、无需登录**（未鉴权入口）。

### 0.4 既有可复用资产（本方案的落点，均为实测）

| 资产 | 关键事实 | 出处 |
| --- | --- | --- |
| 入职流程状态机 | `HrFlow.status` ∈ `IN_PROGRESS` / `COMPLETED` / `REJECTED`（**无** CANCELLED/EXPIRED） | [HrConstants.java:28-30](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L28-L30)、[db.md:776](db.md) |
| 入职步骤（顺序真源） | `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY → DONE` | [HrConstants.java:59-65](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L59-L65) |
| 「三件事」= 既有 3 个步骤的副作用 | 建档=`createEmployeeForFlow`；设岗位站点角色=`assignForFlow`；设薪=`salaryForFlow`；最后 `finishOnboarding` 转在职 | [HrFlowServiceImpl.java:264-398](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L398) |
| `hr_flow` 已含注册要素字段 | `candidate_name` / `phone` / `station_id` / `position` / `role` / `dept_id` / `expected_entry_date` | [HrFlow.java:35-63](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L35-L63) |
| 建档要求入参 | `CREATE_ACCOUNT` 需 `username`（字母开头 4-30）+ `password`（8-20 含字母数字）+ `deptId` + `stationId`；建档即写 `employee` + `hr_profile` + **0 值 `hr_salary`** | [HrFlowServiceImpl.java:264-338](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L338) |
| 建档口径 | 首落 `employee.status=0`（未生效）、`pwd_changed=0`（首登强制改密），最后一歩才置 1 | [HrFlowServiceImpl.java:301-303](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301-L303)、[:397](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L397) |
| 角色赋值 | `assignForFlow` 仅接受 `STATION_ADMIN` / `STAFF`；`createOnboarding` 恒建 `role=STAFF` | [HrFlowServiceImpl.java:364-370](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L364-L370)、[:119](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119) |
| 入离职端点权限 | **10 个端点全部仅 ADMIN** | [HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111) |
| 手机号唯一性 | `employee.phone` 活跃唯一由 **Service 查重**（不建 DB 唯一索引），重复回 `2003` | [EmployeeServiceImpl.java:126-132](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L126-L132)、[db.md:42-50](db.md) |
| 短信场景枚举 | `SmsScene` ∈ `LOGIN` / `DEVICE_VERIFY` / `PERIODIC_REAUTH`；**未知 scene 回落 LOGIN** | [SmsScene.java:12-16](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L12-L16)、[AuthServiceImpl.java:606-618](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L618) |
| 短信发码前置 | `LOGIN` 场景**要求手机号已绑定账号**，否则 `1109`（防枚举）；频控 4 维（手机号/IP/设备/账号） | [AuthServiceImpl.java:259-279](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L259-L279)、[SmsCodeStore.java:93-115](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115) |
| 短信配置 | `hrm.sms.*`：`provider`（`none` 降级/`aliyun` 生产）、频控/TTL/尝试上限全部外置 | [SmsProperties.java:19-116](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L19-L116) |
| 公开端点单一真源 | `PublicEndpoints.PATHS` 6 条；新增公开端点**扩大攻击面，公网暴露前须再过 P0.5 安全评估** | [PublicEndpoints.java:13-47](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L47) |
| 驿站接口 | `GET /api/v1/stations` **仅 ADMIN**，当前**无公开驿站选项端点** | [StationController.java:27-39](../hrm-server/src/main/java/com/qiujie/controller/station/StationController.java#L27-L39) |
| 迁移现状 | 最大版本 `V15__auth_trusted_device.sql`；快照 `sql/schema/{mysql,postgresql}/init.sql` | [migration/mysql/](../hrm-server/src/main/resources/db/migration/mysql/)、[sql/schema/mysql/init.sql](../sql/schema/mysql/init.sql) |

***

## 1. 流程设计

### 1.1 端到端流程（文字状态机）

```text
[staff-h5 登录页] 「员工注册」入口
        │
        ▼
S1 填手机号 → 点「获取验证码」→ POST /auth/sms/send (scene=REGISTER)
        │        频控命中 → 1101（前端倒计时，复用 createCountdown）
        │        通道失败 → 1105
        ▼
S2 填 姓名 / 验证码 / 意向驿站 / 意向岗位 / 密码 → POST /registration
        │        验证码错/过期 → 1102；尝试超限 → 1103
        │        （校验顺序见 §3.4：先校验证码，再做「已注册」判定）
        ▼
   建 employee_registration（status=SUBMITTED, apply_no, query_token 明文仅此一次返回）
   + 建 hr_flow（flow_type=ONBOARDING, operator=SYSTEM/注册来源, status=IN_PROGRESS）
   + 回填 registration.flow_id      ← 单事务
        │
        ▼
S3 返回 { applyNo, queryToken, status }；前端落地本地存储，供「申请进度查询」
        │
        ▼
[A] 进入既有入离职模块：GET /hr/onboarding 列表可见该单（人工审批）
        │
        ├── ADMIN 驳回 ── POST /hr/onboarding/{id}/reject
        │        → hr_flow.status=REJECTED；registration.status=REJECTED
        │        → 申请人可重新注册（生成**新**申请；旧单保留痕迹）
        │
        ▼
S4 ADMIN 审批通过 ── POST /hr/onboarding/{id}/approve   【聚合联动，见 §4】
        │        同一事务按序执行既有 4 步副作用：
        │          CREATE_ACCOUNT(建档 employee+hr_profile+0值薪资)
        │          → ASSIGN_STATION(岗位/站点/角色)
        │          → SET_SALARY(定薪)
        │          ~~→ DONE(转在职 status=1)~~   ← **已被安全评估 M-4 推翻**（审批通过不得自动激活，见 §11.2/M-4、§11.8）
        │          → 修订后：仅前 3 步，停在 SET_SALARY；hr_flow 保持 IN_PROGRESS，employee.status 保持 0（待激活）
        │        → registration.status=APPROVED, approved_employee_id 回填
        │        → DONE（转在职/激活）改由 ADMIN 二次显式确认（复用 completeOnboardingStep DONE 或新端点）
        ▼
S5 员工首次登录（员工端）：**须先经 DONE 激活（status=1）**；初始口令由 ADMIN 一次性设定、`pwd_changed=0` 首登强制改密；口径见 §1.4 与 §11.8
```

### 1.2 状态机（两张表，职责分离）

**`registration.status`（申请侧权威状态）**

| 状态 | 含义 | 进入条件 | 出边 |
| --- | --- | --- | --- |
| `SUBMITTED` | 已提交，审批中 | 提交成功（单事务） | → `APPROVED` / `REJECTED` / `CANCELLED` / `EXPIRED` |
| `APPROVED` | 审批通过且联动成功 | 聚合审批事务提交成功 | 终态 |
| `REJECTED` | 已驳回 | ADMIN 驳回 | 终态（可另起新申请） |
| `CANCELLED` | 申请人撤回 | 申请人凭凭据撤回 | 终态 |
| `EXPIRED` | 超时失效 | `now ≥ expire_time` 且仍 `SUBMITTED`（惰性判定或定时任务） | 终态 |

**`hr_flow.status`（审批载体，沿用既有三态，不改枚举）**

| 事件 | hr_flow 处理 | 说明 |
| --- | --- | --- |
| 提交 | 新建 `IN_PROGRESS` | 步骤全部 `PENDING` |
| 驳回 | `REJECTED` | 复用既有 `rejectOnboarding` |
| 撤回 / 超时 | **置 `REJECTED`**，`reject_reason` 写「申请人撤回」/「超时失效」 | **不改 `hr_flow` 状态枚举**（避免动既有契约）；语义稍宽，已登记为 §8 替代方案 |

> **为什么申请侧与流程侧状态分离**：`hr_flow` 是**审批载体**（既有域，权限 ADMIN，端点已定稿），`registration` 是**注册事实与凭据载体**（含密码散列、查询凭据、来源留痕，且申请人尚非 `employee`）。合并到一张表会让"未登录申请人"的凭据与审计信息污染既有流程表语义，并迫使修改既有状态枚举（契约破坏）。

### 1.3 关键分支

| 分支 | 判定口径 | 产出 |
| --- | --- | --- |
| **重复提交** | 同 `phone` 存在 `status=SUBMITTED` 的 registration | `9307`（§3.3）；不新建单 |
| **已注册账号** | 提交时（**验证码校验通过之后**）查 `employee.phone` 活跃记录命中 | `9310`；**不在发码环节提前暴露**（§3.4） |
| **驳回** | ADMIN 驳回，原因 2-200 字（复用 `HrFlowRejectRequest` 校验） | 两表同时置 `REJECTED`；申请人重新注册 = 新单 |
| **撤回** | 申请人凭 `applyNo + queryToken`，仅 `SUBMITTED` 可撤 | 两表同时置终态（hr_flow→`REJECTED`） |
| **超时** | `expire_time = create_time + N 天`（N 待裁定，建议 7） | 两表同时置终态；**默认不启用自动任务**，先支持惰性判定（查询/审批前判） |

### 1.4 员工首次登录与改密口径

既有口径：建档 `pwd_changed=0` → 首登强制改密（[HrFlowServiceImpl.java:302](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L302)）。

本方案冲突点：申请人**已自设密码**，再强制改密属冗余；但 [security-auth-review.md:278](security-auth-review.md) 的 **R6** 要求「首登强制改密服务端强制」是为**共享初始口令**场景设防。

**建议（需用户裁定，见 §8-Q2）**：自设密码场景 `pwd_changed=1`（视为本人已设置），并在 §8 登记该口径变更对 R6 的适用性说明。**替代方案**：仍置 0，首登强制改密（口径与既有完全一致，安全性更高、体验多一步）。

> 无论取何种口径，**服务端强制**（`pwd_changed=0` 时除改密/登出/me 外一律拒）属 R6 范围，本方案不改变其实现状态（现状为"仅前端实现，服务端未强制"，见 [security-auth-review.md:132](security-auth-review.md)）。

***

## 2. 数据设计

### 2.1 三选一取舍论证

| 方案 | 论证 | 结论 |
| --- | --- | --- |
| ① 只新增专用表 `employee_registration` | 注册事实与既有 `hr_flow` **完全平行**：审批步骤机、步骤条、驳回理由、操作留痕、PC 审批台全部要在新表**重造一遍**，与既有入离职模块形成两套审批实现（违反规则 §2 复用）。且需求明确「进入**既有**入离职模块的入职审批」 | **否决** |
| ② 只复用 `hr_flow` 承载 | `hr_flow` 已含 `candidate_name/phone/station_id/position/role`，且**步骤机即审批**，零新增表。但缺三样**必须**承载物：⒜ 申请人自设密码的 BCrypt 散列（`hr_flow` 无密码列，把散列塞进流程表属语义污染）；⒝ 未登录申请人的**查询/撤回凭据**（申请人尚无 `employee_id`，无身份锚点）；⒞ 注册来源/条款同意/申请编号等**注册事实审计**。另需改 `hr_flow` 加列（扩大既有表语义面） | **否决** |
| ③ **申请表 + 流程单（推荐）** | `hr_flow` 继续做**审批载体**（复用步骤机/权限/PC 审批台，零改造）；新增 `employee_registration` 做**注册事实与凭据载体**（密码散列、查询凭据、来源审计），两表以 `registration.flow_id ↔ hr_flow.id` 1:1 关联。职责单一、互不污染、复用最大化 | **采纳** |

> 与 [db.md:474-480](db.md) 「字段真源 + DDL 落位」体例一致；与 [db.md:436-459](db.md) `leave_request`（申请单独立建表 + 状态机 + 审批分槽）为**同构先例**。

### 2.2 新表 `employee_registration` 字段级设计

> 遵循 [db.md:482-501](db.md) §8.0：`snake_case`、主键 `id`、时间 `create_time/update_time`、逻辑删除 `is_deleted`、**不建物理外键、不建 DB 唯一索引**（活跃唯一走 Service 查重 + 普通索引）。

| 字段 | MySQL | PG | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| apply_no | VARCHAR(32) | VARCHAR(32) | 否 | - | 申请编号（活跃唯一，形如 `RG-YYYYMMDD-0001`，编号生成对齐 `flow_no` 两段式：先占位插入取 id 再回填，见 [HrFlowServiceImpl.java:452-471](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L452-L471)） |
| flow_id | BIGINT | BIGINT | 是 | NULL | 关联审批单（逻辑外键 `hr_flow.id`，提交时写入） |
| real_name | VARCHAR(50) | VARCHAR(50) | 否 | - | 姓名（2-20，**对齐既有** `validateOnboardingCreate` 口径） |
| phone | VARCHAR(20) | VARCHAR(20) | 否 | - | 手机号（`^1[3-9]\d{9}$`；活跃唯一） |
| password_hash | VARCHAR(100) | VARCHAR(100) | **是** | NULL | 自设密码 BCrypt 散列（规格同 `employee.password`，cost=10）；**终态一律置 NULL**（凭据卫生，见下） |
| apply_station_id | BIGINT | BIGINT | 是 | NULL | 意向驿站（逻辑外键 `station.id`） |
| apply_position | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 意向岗位（**自由文本**，对齐 `hr_flow.position`；**无岗位实体**，见 §0.2-⑥） |
| source | VARCHAR(16) | VARCHAR(16) | 否 | 'STAFF_H5' | 注册来源（审计；一期仅 `STAFF_H5`） |
| agreement_version | VARCHAR(20) | VARCHAR(20) | 是 | NULL | 已同意的服务条款版本（合规留痕） |
| query_token_hash | VARCHAR(64) | VARCHAR(64) | **是** | NULL | 查询凭据 SHA-256（明文**仅在提交响应返回一次**，永不落库/日志）；终态可置 NULL |
| status | VARCHAR(16) | VARCHAR(16) | 否 | 'SUBMITTED' | `SUBMITTED`/`APPROVED`/`REJECTED`/`CANCELLED`/`EXPIRED` |
| reject_reason | VARCHAR(200) | VARCHAR(200) | 是 | NULL | 驳回原因快照 |
| approved_employee_id | BIGINT | BIGINT | 是 | NULL | 通过后生成的员工（逻辑外键 `employee.id`） |
| approve_time | DATETIME | TIMESTAMP | 是 | NULL | 通过时间 |
| cancel_time | DATETIME | TIMESTAMP | 是 | NULL | 撤回时间 |
| expire_time | DATETIME | TIMESTAMP | 是 | NULL | 失效判定基准（`create_time + N 天`） |
| client_ip | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 提交来源 IP（审计；口径对齐 `login_log.login_ip`，[db.md:206](db.md)） |
| is_deleted | TINYINT | SMALLINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time / update_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 应用层 `MetaObjectHandler` 填充 |

**索引**（`idx_表名_字段`）：

| 索引名 | 字段 | 用途 |
| --- | --- | --- |
| idx_employee_registration_phone | phone | 重复提交查重 / 已注册判定 |
| idx_employee_registration_flow | flow_id | 审批单 ↔ 申请单回关联 |
| idx_employee_registration_status | status, create_time | 列表（按状态 + 时间倒序） |

**凭据卫生规则**：`password_hash` / `query_token_hash` 在进入任一终态（`APPROVED`/`REJECTED`/`CANCELLED`/`EXPIRED`）时**同事务置 NULL**——避免"已失效申请长期残留可用凭据"。这也是把密码放申请单而非 `hr_flow` 的附加理由。

### 2.3 与既有表的字段映射

| 注册侧 | → 落点 | 映射口径 |
| --- | --- | --- |
| `real_name` | `hr_flow.candidate_name` → `employee.real_name` | 建档时 `employee.real_name = flow.candidate_name`（[HrFlowServiceImpl.java:295](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L295)）；**姓名不可在审批时被静默改写** |
| `phone` | `hr_flow.phone` → `employee.phone` | 同值传递（[HrFlowServiceImpl.java:296](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L296)） |
| `apply_station_id` | `hr_flow.station_id` → `employee.station_id` | **意向**，审批时可由 ADMIN 改定（`assignForFlow` 允许覆盖） |
| `apply_position` | `hr_flow.position` | 仅流程表；`employee` **无** position 列，故岗位不落员工表（见 §0.2-⑥） |
| ~~`password_hash`~~ | ~~→ `employee.password`~~ | **~~新增通道：以申请单散列作为初始密码~~ 已被安全评估 M-4 推翻**：不得复用注册所填密码；初始口令由 ADMIN 在 `CREATE_ACCOUNT` 一次性设定（既有事实⑪），`pwd_changed=0` 强制首登改密。注册密码去向见 §11.8（待裁定 U-01） |
| （注册不采集） | `employee.username` | 注册**不采集**登录账号；由审批人提供或按 §8-Q4 规则生成 |
| （注册不采集） | `hr_flow.dept_id` → `employee.dept_id` | 注册**不采集部门**；`CREATE_ACCOUNT` 要求 `deptId` 非空，故须审批时必填（见 §8-Q9） |
| （注册不采集） | `employee.role` | 恒 `STAFF`（既有 `createOnboarding` 口径）；`STATION_ADMIN` 仅可由审批人在 `ASSIGN_STATION` 显式授予（见 §5.4） |
| （注册不采集） | 薪资（`hr_salary`） | 注册**不采集**薪资；须审批时提供（防"注册即定薪"注入，见 §5.4） |

### 2.4 唯一性与并发口径

- **不建 DB 唯一索引**（[db.md:42-50](db.md) 决策 D7）：`phone` 活跃唯一、`apply_no` 活跃唯一均由 Service 层查重 + 普通索引加速。
- 已知代价：并发下存在"双提交"窗口（既有无 DB 约束，管理端低并发可接受）。**注册是公开端点，并发高于后台** → 建议对本表**例外增设** `UNIQUE(apply_no)`（`apply_no` 无逻辑删除复用语义，加唯一索引不与 D7 的"已删账号名复用"矛盾）。**标为需裁定（§8-Q11）**。
- 审批联动的并发保护见 §4.5。

***

## 3. 接口契约草案

> 体例与编号严格对齐 [api.md](api.md)：路径 `/api/v1`、统一响应 `{code,message,data}`、分页 `pageNum/pageSize`、出参手机号脱敏（[api.md:59-65](api.md)）。**本节仅为草案**，定稿须由后端写入 `api.md`（P5 契约先行）。

### 3.1 端点清单（概览）

| # | 方法 | 路径 | 权限 | 是否新增 | 说明 |
| --- | --- | --- | --- | --- | --- |
| R-1 | POST | `/api/v1/auth/sms/send` | **公开** | **复用**（新增 `scene=REGISTER`） | 注册验证码下发 |
| R-2 | POST | `/api/v1/registration` | **公开** | 新增 | 提交注册申请 |
| R-3 | GET | `/api/v1/registration/{applyNo}` | **公开 + 申请凭据** | 新增 | 本人查询申请进度 |
| R-4 | POST | `/api/v1/registration/{applyNo}/cancel` | **公开 + 申请凭据** | 新增 | 本人撤回申请 |
| R-5 | GET | `/api/v1/registration/stations` | **公开** | 新增 | 意向驿站选项（**极简出参**） |
| R-6 | POST | `/api/v1/hr/onboarding/{id}/approve` | ADMIN | 新增 | **审批通过 + 三件事联动**（§4） |
| R-7 | GET | `/api/v1/hr/onboarding` | ADMIN | **复用** | 审批列表（既有） |
| R-8 | GET | `/api/v1/hr/onboarding/{id}` | ADMIN | **复用**（出参扩展 `registration` 子对象） | 审批详情 |
| R-9 | POST | `/api/v1/hr/onboarding/{id}/reject` | ADMIN | **复用** | 驳回（须同步回写 `registration`） |

> **~~新增公开端点 4 个（R-2~R-5）~~ 已被安全评估 M-6 推翻**：公开白名单**仅**放行「发码（R-1，复用既有 `/auth/sms/send`）」与「提交申请（R-2）」两条；**R-3/R-4 退出公开白名单**（改 ADMIN-only 或取消），**R-5 驿站选项列为待裁定 U-02**。详见 §11.2/M-6 与 §11.11。净新增白名单条目 = **1 条**（R-2）。全部扩大外部攻击面 → 公网暴露前须过 **P0.5 安全评估**（[PublicEndpoints.java:13-14](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)、[security-auth-review.md:281](security-auth-review.md) R9）。

### 3.2 逐端点（对齐 api.md 体例）

#### R-1 注册验证码下发（复用 `/auth/sms/send`）

`POST /api/v1/auth/sms/send`（公开）。入参新增取值：`scene = "REGISTER"`。

| 入参 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| scene | string | 否 | **`REGISTER`**（本方案新增；缺省仍按 `LOGIN`，见 [AuthServiceImpl.java:606-618](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L618)） |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| deviceId | string | 否 | 弱信号（参与同设备维度限频） |
| captchaTicket / captchaCode | string | 否 | `captcha-enabled=true` 时必填 |

行为：**与登录场景相反**——本场景**不要求手机号已存在**（注册者尚未成为员工）；发码前**不判定"是否已注册"**（防枚举，见 §3.4）。频控 4 维复用，`identifier = phone`（[SmsCodeStore.java:169-175](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L169-L175)）。

响应（复用 `SmsSendVO`，[SmsSendVO.java:11-24](../hrm-server/src/main/java/com/qiujie/vo/auth/SmsSendVO.java#L11-L24)）：`{ sent, expireIn, nextAllowedIn, requireCaptcha }`。**绝不包含验证码**。

错误码：400 / 1101 / 1105 / 1106。

> **副作用清单（须同步）**：`SmsScene` 增 `REGISTER`；`SmsProperties.Aliyun` 增 `template-code-register`；`application.yml` 增键；[multi-client-architecture.md:497](multi-client-architecture.md) 场景表与 [api.md:331-386](api.md) 端准入适用路径说明同步。

#### R-2 提交注册申请

`POST /api/v1/registration`（公开）

| 入参 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| realName | string | 是 | 2-20 字（对齐 [HrFlowServiceImpl.java:638-639](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L638-L639)） |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| smsCode | string | 是 | 6 位；走 `SmsScene.REGISTER` 校验（校验成功一次性作废） |
| stationId | long | 是 | 须存在、未删除且**启用**（否则 4001 / 4004） |
| position | string | 否 | ≤50 字（自由文本） |
| password | string | 是 | **8-20 位且含字母与数字**（对齐 [HrFlowServiceImpl.java:275-277](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L275-L277)） |
| agreementVersion | string | 是 | 服务条款版本（合规留痕） |

行为（**单事务**）：

1. 频控/短信校验：校验 `REGISTER` 场景验证码（1102/1103）；
2. **已注册判定**（须在步骤 1 之后）：`employee.phone` 活跃命中 → `9310`；
3. 重复提交判定：同 `phone` 存在 `SUBMITTED` → `9307`；
4. 建 `employee_registration`（`status=SUBMITTED`；`password_hash=BCrypt(password)`；生成 `apply_no`、`query_token`）；
5. 建 `hr_flow`（`flow_type=ONBOARDING`，`candidate_name/phone/station_id/position` 落值，`role=STAFF`，`status=IN_PROGRESS`，步骤全 `PENDING`；`operator_id/operator_name` 记**注册来源**（无员工上下文，记 `null` 或系统标识，**待裁定 §8-Q12**））；
6. 回填 `registration.flow_id`；
7. 返回 `{ applyNo, queryToken, status, createTime }`。

```json
// 请求
{ "realName": "李四", "phone": "13912345678", "smsCode": "123456",
  "stationId": 3, "position": "分拣员", "password": "Init1234", "agreementVersion": "v1.0" }

// 响应（HTTP 200）
{ "code": 200, "message": "success",
  "data": { "applyNo": "RG-20260926-0001", "queryToken": "<一次性明文>", "status": "SUBMITTED",
            "createTime": "2026-09-26 10:00:00" } }
```

错误码：400 / 9307 / 9310 / 4001 / 4004 / 1102 / 1103。

> **`queryToken` 明文只在本次响应出现一次**，服务端仅存 SHA-256；前端落地本地存储（对齐"设备令牌不进响应体"的既有红线思路，[AuthController.java:44-45](../hrm-server/src/main/java/com/qiujie/controller/auth/AuthController.java#L44-L45)）。

#### R-3 查询申请进度（本人）

`GET /api/v1/registration/{applyNo}?token={queryToken}`（公开 + 凭据）

行为：`SHA-256(token) == query_token_hash` **且** `applyNo` 命中，否则统一 `9308`（**不区分"编号不存在"与"凭据错误"**，防探测）。出参**脱敏**：`phone` 掩码；**不返回** `password_hash` / `query_token_hash`。

出参：`{ applyNo, realName, phone(脱敏), stationId, stationName, position, status, statusLabel, rejectReason, createTime, approveTime }`。

错误码：400 / 9308。

#### R-4 撤回申请（本人）

`POST /api/v1/registration/{applyNo}/cancel`（公开 + 凭据）

入参：`{ token }`。行为：凭据校验同上；仅 `status=SUBMITTED` 可撤（否则 `9309`）；**同事务**置 `registration.status=CANCELLED` + `hr_flow.status=REJECTED`（`reject_reason="申请人撤回"`）+ 清空 `password_hash/query_token_hash` + `cancel_time`。

错误码：400 / 9308 / 9309。

#### R-5 意向驿站选项（公开）

`GET /api/v1/registration/stations`（公开）

行为：仅返回**启用**驿站的 `[{ id, stationName }]`；**白名单出参**，绝不返回联系人/电话/地址/编码/状态等（对齐 [ClientLogSanitizer](../hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java) 的"白名单复制"既有先例，[security-auth-review.md:33](security-auth-review.md)）。

错误码：400（无）。

#### R-6 审批通过（ADMIN，聚合联动）

`POST /api/v1/hr/onboarding/{id}/approve`（ADMIN）

| 入参 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 否 | 登录账号（缺省按规则生成，见 §8-Q4） |
| deptId | long | 是 | 部门（注册不采集，**审批时必填**；不存在 → 3001） |
| stationId | long | 否 | 缺省取 `hr_flow.station_id`（意向驿站）；停用 → 4004 |
| role | string | 否 | 缺省 `STAFF`；仅 `STATION_ADMIN` / `STAFF`（[HrConstants.java:52](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L52)） |
| position | string | 否 | 缺省取 `hr_flow.position` |
| probationMonths | int | 否 | 缺省 `hrm.hr.default-probation-months`（[HrProperties.java:22](../hrm-server/src/main/java/com/qiujie/config/HrProperties.java#L22)） |
| contractType | string | 否 | 缺省 `FIXED_TERM` |
| basicSalary / postSalary / performanceBase / allowances | — | 否 | 定薪（缺省 0 → 建议前端必填，见 §8-Q10） |
| effectiveDate | string | 否 | 缺省取 `expected_entry_date` |
| remark | string | 否 | 0-200 字 |

行为：**单事务**，按既有顺序执行 `CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY → DONE` 四步副作用（复用 [HrFlowServiceImpl.java:264-398](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L398) 既有方法，**不重写**），并同步 `registration.status=APPROVED`。详见 §4。

错误码：400 / 404 / 3001 / 4001 / 4004 / 1003 / 9303 / 9305 / 9309。

#### R-7~R-9 复用既有端点

- **R-7** 列表：`GET /api/v1/hr/onboarding`（ADMIN），行为不变。
- **R-8** 详情：`GET /api/v1/hr/onboarding/{id}`（ADMIN），出参**新增** `registration` 子对象（`applyNo/position/agreementVersion/source/createTime`），供审批台展示注册来源。
- **R-9** 驳回：`POST /api/v1/hr/onboarding/{id}/reject`（ADMIN），行为**须扩展**：同事务同步 `registration.status=REJECTED` + `reject_reason` + 清空凭据列。

### 3.3 错误码草案（93xx 续号）

> 段位沿用 **93xx = 人事/入离职**（[api.md:88](api.md)、[ErrorCode.java:194-207](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L194-L207) 当前占用 9301~9306）。**新增从 9307 起**。

| code | 常量名（建议） | message | 说明 |
| --- | --- | --- | --- |
| 9307 | `REGISTRATION_DUPLICATE` | 该手机号已有进行中的入职申请，请勿重复提交 | 重复提交 |
| 9308 | `REGISTRATION_NOT_EXISTS` | 入职申请不存在或凭据无效 | 查询/撤回凭据不匹配（**不区分两种原因**） |
| 9309 | `REGISTRATION_STATUS_INVALID` | 申请状态不允许该操作 | 撤回/审批时状态非法 |
| 9310 | `REGISTRATION_PHONE_TAKEN` | 该手机号已注册，请直接登录 | 已存在活跃员工账号 |

短信类错误复用既有 **1101 / 1102 / 1103 / 1105 / 1106**，**不新增**。

> 联动项：`ErrorCode` 枚举、[api.md:95-142](api.md) §2.2 明细（现仅展开 10xx~50xx，93xx 尚未展开）、Mock `constants/errorCode.js` 文案同源（[ErrorCode.java:6](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L6)）。

### 3.4 关键判定口径（**必须写明**）

| 口径 | 定稿 |
| --- | --- |
| **手机号唯一性** | 以 `employee.phone` **活跃记录**为准（Service 查重，非 DB 唯一索引，[db.md:42-50](db.md)）。注册提交时命中 → `9310` |
| **"已注册"判定时机** | **必须在 `REGISTER` 验证码校验通过之后**执行。理由：验证码发往该手机号，通过校验即证明申请人**持有该手机号**，此时告知"已注册"不构成对他人手机号的枚举；若在发码环节提前判定，则 `send` 端点退化为"手机号是否已注册"的探测器（[security-auth-review.md:245](security-auth-review.md) §4.4③） |
| **发码环节一致性** | `scene=REGISTER` 发码**不因手机号是否已注册而改变响应**（已注册也发码），把拒绝推迟到提交。**此口径须经安全评估确认（§8-Q7）** |
| **重复提交** | 同 `phone` 且 `registration.status=SUBMITTED` → `9307`。已 `REJECTED`/`CANCELLED`/`EXPIRED` 的**不阻断**新提交（另起新单） |
| **越权口径** | R-3/R-4 无登录态，**以 `applyNo + queryToken` 双因子**收口；出参仅返回该单自身字段；**不得**返回他人申请、不得用手机号单独查询（防遍历） |
| **审批越权** | R-6/R-9 仅 `ADMIN`（与既有 hr 域 10 端点一致，[HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111)）；**站长（`STATION_ADMIN`）不可审**（§8-Q5） |

### 3.5 `api.md` 更新点清单（交后端定稿）

1. §2.1 分段表「已展开段位」说明：追加 93xx 注册续号（9307~9310）；
2. §2.2 错误码明细：追加 9307~9310；
3. §4.0 概览：追加 R-2~R-6 五行（**并修正该表"46 个"的历史计数与概览**，见 §0.2-④）；
4. 新增章节：注册侧（R-1~R-5）新章「员工自助注册」；审批侧（R-6）**须先补录既有 hr 域端点**——`api.md` §4 现仅到 §4.9 排班，**入离职/财务/KPI/工单等域尚未收录**（[api.md:182-1071](api.md)），故 R-6 应与既有 10 个 hr 端点同章补录（建议新增 `§4.10 人事/入离职`）；
5. §4.1.x 短信下发：补 `scene=REGISTER` 语义与端准入适用性说明；
6. §3.3 Redis Key 约定：追加 `hrm:sms:code:REGISTER:{phone}` 等（沿用 [SmsCodeStore.java:35-41](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L35-L41) 命名）。

***

## 4. 审批通过后的联动设计

### 4.1 「三件事」的数据落点（均为既有实体，零新增业务表）

| 用户口径 | 真实落点 | 既有实现 |
| --- | --- | --- |
| 生成员工档案 | `employee`（+ `hr_profile` + **0 值 `hr_salary`** 占位） | `createEmployeeForFlow`（[HrFlowServiceImpl.java:264-338](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L338)） |
| 设置岗位/站点/角色 | `employee.station_id` / `employee.role` / `employee.dept_id`；`hr_flow.position` / `hr_flow.role`（岗位无独立表） | `assignForFlow`（[:340-373](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L340-L373)） |
| 设置薪资 | `hr_salary`（当前定薪）+ `hr_salary_log`（`change_type=ENTRY` 留痕） | `HrSalaryWriter.save(...)`（[:375-386](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L375-L386)） |

### 4.2 触发方式：聚合 vs 分步（**需裁定，§8-Q1**）

| 方案 | 说明 | 评价 |
| --- | --- | --- |
| **A1 聚合一次通过（推荐）** | 新增 `POST /hr/onboarding/{id}/approve`，**单事务内按序复用** 4 步副作用；步骤条被一次调用推进至全 `DONE`，`hr_flow.status=COMPLETED` | 满足用户"三件事同时完成"；复用既有方法零重写；**代价**：绕过既有"逐步骤 `completeOnboardingStep`"的对外形态，属**既有契约的用法扩展**（不删旧端点，旧端点保留供 PC 手工办理） |
| A2 保持逐步骤 | 审批只完成 `SUBMIT_MATERIALS`+`HR_REVIEW`；三件事由 ADMIN 在入职页逐条办理 | **不满足**用户"同时完成"；落实最快 |
| A3 状态机改造 | 把 6 步压缩为 1 步 | 破坏既有步骤契约与 Mock 契约（[hrStore.js:54-60](../hrm-clients/packages/mock/src/hrStore.js#L54-L60)），**否决** |

> **A1 触及既有 `hr_flow` 步骤机对外形态**，属"设计判断与既有契约（`HrFlowController` 已定稿的 10 端点为现状契约）交互关系"范畴 → 依架构师红线，**不在本方案内单方拍板**，提交**技术评审 + 主智能体裁定**（§8-Q1）。本方案按 A1 展开，以便评审有可判定的具体设计。

### 4.3 事务边界

- **一个事务**：`@Transactional(rollbackFor = Exception.class)`（对齐既有全程用法，[HrFlowServiceImpl.java:107](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L107)）。
- 事务内顺序：`CREATE_ACCOUNT` → `ASSIGN_STATION` → `SET_SALARY` → ~~`DONE`~~（**已被安全评估 M-4 推翻，移出本事务**，见 §11.2/M-4）→ `registration.status=APPROVED` → 清空 `registration` 凭据列。
- **为什么必须同事务**：`SET_SALARY` 前置依赖 `CREATE_ACCOUNT` 写入的 0 值 `hr_salary` 行（[:326-337](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L326-L337)）；`ASSIGN_STATION` 依赖 `employeeId` 已回填。任一步失败若部分提交，将产生"有员工无薪资/有账号未在职"的半成品。

### 4.4 失败处理（部分成功）

| 失败点 | 行为 |
| --- | --- |
| 建档失败（账号重复 1003 / 部门、驿站非法 3001/4004） | 抛 `BusinessException` → **整单回滚**；`hr_flow` 保持 `IN_PROGRESS`，`registration.status` 保持 `SUBMITTED`；前端提示具体字段，审批人修正后重试 |
| 定薪非法（`HrSalaryValidator` 拒绝，[HrFlowServiceImpl.java:697-701](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L697-L701)） | 同上，整单回滚 |
| `registration` 回写失败 | 同事务，随主流程回滚 |

> **结论**：采用"**全成功或全回滚**"，**不存在**需人工补偿的部分成功态。响应不回滚的只是 HTTP 层提示。

### 4.5 幂等与并发

| 场景 | 设计 |
| --- | --- |
| 重复审批（同一单点两次） | 复用既有守卫：`hr_flow.status != IN_PROGRESS` → `9303`（[:161-164](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L161-L164)）；新建档时 `flow.employeeId != null` 亦拒（[:265-267](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L265-L267)）；再加 `registration.status != SUBMITTED` → `9309` |
| 并发审批（两请求同时进） | **既有实现未见行锁**（`requireFlow` 为普通 `selectById`）。建议在本端点对 `hr_flow` 行加 `SELECT ... FOR UPDATE`（或乐观锁 `update_time` 版本 + 影响行数断言）。**标为风险/必改（§8-R3）**；属后端实现细节，本方案只给约束 |
| 审批 vs 撤回并发 | 两路径均须以 `registration.status` 的状态守卫为准（其一必失败），并在同事务内先写 `registration` 状态再写 `hr_flow` |
| 「三件事」本身幂等 | 建档/定薪/分配均由上表守卫覆盖；`SET_SALARY` 走 `HrSalaryWriter`（需后端确认其是否幂等，见 §10 TODO） |

### 4.6 薪资与岗位信息由谁提供（**建议**）

**结论：申请时填「意向」，审批时由 ADMIN 定「事实」。**

| 项 | 申请时（申请人可控） | 审批时（ADMIN 定） | 理由 |
| --- | --- | --- | --- |
| 姓名 / 手机号 | ✅ 填 | 只读（不可静默改写） | 注册事实 |
| 意向驿站 | ✅ 填（`apply_station_id`） | 可改（`stationId`） | 意向 ≠ 定岗；亦防停用驿站 |
| 意向岗位 | ✅ 填（`apply_position`） | 可改（`position`） | 自由文本、无实体 |
| 部门 `deptId` | ❌ 不填 | ✅ **必填** | `CREATE_ACCOUNT` 强依赖 |
| 角色 `role` | ❌ **不可填** | ✅ 可授（默认 `STAFF`） | **安全红线**：防注册注入 `STATION_ADMIN`（§5.4） |
| 薪资 | ❌ **不可填** | ✅ 提供 | **安全红线**：防注册自定薪（§5.4） |
| 密码 | ✅ 自设 | 🔁 可用申请单散列，或审批人另置 | §8-Q3 |
| 登录账号 `username` | ❌ 不采集 | ✅ 提供或生成 | §8-Q4 |

***

## 5. 权限与安全边界

### 5.1 未鉴权入口的收敛口径

| 面 | 收敛措施 |
| --- | --- |
| **频控** | 复用 `hrm.sms.*` 四维限频（手机号/IP/设备/账号，[SmsCodeStore.java:93-115](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115)）+ 建议 Nginx `limit_req`（[security-auth-review.md:275](security-auth-review.md) R3）。`hrm.sms.ip-hourly-limit` 对公开注册尤为关键 |
| **验证码为提交前置** | R-2 必须先过 `REGISTER` 验证码；无有效码不得提交（阻断"无成本刷单"） |
| **字段白名单** | R-2 入参仅上表 7 字段；**禁止**接受 `role/deptId/stationId(事实)/salary/pwdChanged/status` 等；Spring 侧用独立 DTO（**不用** `HrStepCompleteRequest`，避免复用审批入参漏字段） |
| **出参白名单** | R-3 脱敏手机号、不回散列/凭据；R-5 仅 `{id, stationName}` |
| **幂等与防重放** | 验证码一次性作废（[SmsCodeStore.java:64-66](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L64-L66)）；重复提交 `9307` |
| **凭据形态** | `queryToken` 用 `SecureRandom` 生成、服务端只存 SHA-256、明文返回一次（对齐验证码"绝不回显"红线，[SmsSendVO.java:8](../hrm-server/src/main/java/com/qiujie/vo/auth/SmsSendVO.java#L8)） |
| **日志红线** | 注册请求/响应体**不入** `client_log`；验证码/密码/queryToken 不进任何日志；建议在 `ClientLogSanitizer` 的凭据正则应**补充** `code=` 形态（[security-auth-review.md:246](security-auth-review.md) §4.4⑦） |

### 5.2 申请数据的可见范围

| 角色 | 可见 |
| --- | --- |
| 申请人本人 | 仅凭 `applyNo + queryToken` 看**本单**（无登录态，不看列表） |
| ADMIN | 所有注册申请 + 审批单 |
| STATION_ADMIN / STAFF | **不可见**（无对应端点；R-5 除外，仅驿站名） |

> 申请数据含**未加密的姓名/手机号**（手机号出参脱敏），**不含**薪资（薪资仅审批时产生）。合规上应登记个人信息处理说明（§8-Q13）。

### 5.3 审批权限

**仅 `ADMIN`**。理由：既有 hr 域全部端点仅 ADMIN（[HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111)）；若开放站长审批须新设计两级审批（同 [leave_request](db.md) 的 `PENDING_STATION/PENDING_BOSS`），属**范围扩张**，须用户裁定（§8-Q5）。

### 5.4 可控字段与审批后赋值字段的**严格分离**（防注册注入）

| 类别 | 字段 | 谁能写 |
| --- | --- | --- |
| **申请人可控（注册时）** | `real_name`、`phone`、`apply_station_id`(意向)、`apply_position`(意向)、`password`(自设)、`agreement_version` | 注册接口（公开） |
| **仅审批人可赋（审批时）** | `employee.username`、`employee.dept_id`、`employee.station_id`(事实)、`employee.role`、`hr_salary.*`、`hr_profile.*`、`pwd_changed`、`status` | 审批接口（ADMIN） |

**硬约束**：

1. 注册 DTO **不得**包含任何"仅审批人可赋"字段（编译期即隔离）；
2. 建档时 `employee.role` **恒取** `"STAFF"`（对齐既有 `createOnboarding` 的 `flow.setRole("STAFF")`，[:119](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119)），`STATION_ADMIN` 只在 `ASSIGN_STATION` 由审批人显式授予；
3. 注册**不写** `hr_salary`（虽 `CREATE_ACCOUNT` 会落 0 值占位，但那是**审批时**产生，非注册时）；
4. `stationId` 在注册阶段仅为"意向"，落地员工前须**再审**存在性/启用状态（复用 `STATION_DISABLED` 4004）。

***

## 6. NFR

| 维度 | 结论 |
| --- | --- |
| **性能** | 影响面**极小**：注册为低频写（单人一次），`employee_registration` 与 `employee` 同量级（< 5000 行）；公开端点无列表/无聚合查询。唯一需关注的是 **`send` 端点的短信成本**（非性能，是成本面）——由 4 维频控 + Nginx 限流约束。BCrypt 每请求 1 次（cost=10 ≈ 数十 ms），可接受 |
| **可用性** | **短信通道不可用**：`provider=none`（降级）时 `LoggingSmsSender` 只记日志、验证码无人可取 → **注册事实上不可用**；生产 `provider=aliyun` 且凭据缺失则启动期 fail-fast（[SmsProperties.java:22-26](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L22-L26)）。投递失败回 `1105`（[AuthServiceImpl.java:308-312](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L308-L312)）。**降级必须 fail-closed**，不得弱兜底为固定码（[security-auth-review.md:246](security-auth-review.md) §4.4⑥）。生产 `dev-fixed-code`/`dev-universal-code` 均被 `SmsConfigGuard` 禁（[SmsProperties.java:74-99](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L74-L99)） |
| **可观测性** | 关键流转 `info`：注册提交（`apply_no` + 脱敏手机号）、审批通过（`flow_no` + `employee_id`）、驳回/撤回；`warn`：短信投递失败、频控命中。**验证码/密码/queryToken 一律不入日志**。审批留痕复用 `hr_flow_step.operator_id/operator_name/operate_time`（[db.md:794](db.md)） |
| **可维护性** | **复用最大化**：审批复用 `hr_flow` 步骤机与既有 10 端点；短信复用 `SmsSender`/`SmsCodeStore`（仅增 1 个 scene）；错误码复用段位。**零新增业务表**（仅 1 张注册事实表），零新增服务端口。参数（TTL/频控/超时天数/试用期）全部外置（[SmsProperties](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java)、[HrProperties](../hrm-server/src/main/java/com/qiujie/config/HrProperties.java)） |
| **一致性** | 审批联动**强一致（单事务）**；注册提交亦**单事务**（申请表 + 流程表） |

***

## 7. 兼容与迁移

| 项 | 结论 |
| --- | --- |
| **Flyway 迁移** | **需要**，版本规划经 §11.6/§11.11 调整（当前最大 [V15](../hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql)）：**V16__employee_phone_unique.sql**（M-5 前置，§11.6）→ **V17__employee_registration.sql**（新表，§2.2）→ **V18__hr_flow_source.sql**（M-9，§11.2）。**双库脚本**各一份。**严禁修改任何历史脚本**（[智能体调度规则 §5 L4](.trae/rules/智能体调度规则.md)） |
| **快照同步** | 同步更新 `sql/schema/mysql/init.sql` 与 `sql/schema/postgresql/init.sql`（[db.md:397-405](db.md) §5.3） |
| **迁移安全性** | **V17** 为纯 `CREATE TABLE`（新表，无既有表变更、无数据回填，低风险，回滚 = `DROP TABLE`）；**V16 / V18 为既有表结构变更**（`employee` 加活跃唯一索引、`hr_flow` 加列）→ 须**预检 + 备份**，回滚 = `DROP INDEX` / `DROP COLUMN`（脚本注释附回滚语句，[db.md:494](db.md) §8.0-5） |
| **`db.md` 更新** | 新增「`employee_registration`（V17）」小节（建议落 §8.5 人事域续号 8.5.6 或新章 §11）；`employee.phone` 活跃唯一（V16）与 `hr_flow.source`（V18）同步更新 |
| **`api.md` 更新** | 见 §3.5（6 处） |
| **`multi-client-architecture.md` 更新** | §4.3 场景表、§4.4.3 键规范补 `REGISTER`（[multi-client-architecture.md:497](multi-client-architecture.md)） |
| **`update-log.md`** | 按章节追加一行（[智能体调度规则 §12 M02]） |
| **Mock 同步** | **需要**，但**联调基线以真实后端为准**（既定纪律：Mock 只做前端脱机演示）。需同步：`packages/mock/src/routes/auth.js`（`scene=REGISTER`）、新增 `registration` 路由与 store、`constants/errorCode.js`（9307~9310）、`hrm-demo` 同名副本 |
| **前端** | 新增 staff-h5 注册页/路由 + 登录页入口（登录页仅 `sendSms` 已存在，[auth.js:15](../hrm-clients/apps/staff-h5/src/api/auth.js#L15)，需扩 api 层）；PC 审批页（`web/src/views/onboard/index.vue`）读 `registration` 子对象 + 新增「审批通过」表单 |
| **不兼容项** | 无破坏性变更（均为新增端点/新增列/新增表）。唯一**语义扩展**：`hr_flow` 的 `REJECTED` 被复用承载"撤回/超时"（§1.2），须在文档显式登记 |

***

## 8. 风险与假设登记

### 8.1 需用户裁定（口径类，**未裁定不得实现**）

> **v1.1 说明**：下表 Q1~Q13 为 v1.0 原表，**保留供追溯**；安全评估响应后已与新增项**去重合并为一张统一待裁定表（U-01~U-17）**，见 **§11.4**。未裁定项一律**不得实现**；标 ⛔ 者**阻塞公网放行**。

| # | 问题 | 建议 | 替代方案 |
| --- | --- | --- | --- |
| **Q1** | 审批通过采用「聚合一次通过」（A1）还是「保持逐步骤」（A2）？ | **A1**（满足"三件事同时完成"，复用既有方法） | A2（改动最小，但需 ADMIN 逐步办理，不满足口径） |
| **Q2** | 申请人自设密码后，是否仍需首登强制改密（`pwd_changed=0`）？ | `pwd_changed=1`（本人已设置，且非共享初始口令） | 置 0（与既有口径完全一致，安全性更高；多一步改密） |
| **Q3** | 初始密码来源：申请单散列 vs 审批人手输？ | **申请单散列**（尊重"本人设置的密码"） | 审批人另置初始密码（与既有 `CREATE_ACCOUNT` 完全一致） |
| **Q4** | 登录账号 `username` 口径（注册不采集） | 默认 `u` + 手机号（满足 `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`），审批人可覆盖 | 审批人必填；或引入拼音生成（复杂度高） |
| **Q5** | 审批权限是否仅 ADMIN？站长能否初审？ | **仅 ADMIN** | 两级审批（同 leave 模型，属范围扩张） |
| **Q6** | 申请人查询/撤销凭据形态 | `applyNo + queryToken`（服务端只存散列） | 手机号 + 短信验证码（多一个 scene，多一次发码成本） |
| **Q7** | `scene=REGISTER` 发码是否对"已注册手机号"一致响应（防枚举）？ | **一致响应**（不提前暴露），拒绝推迟到提交 | 发码即拒（简单，但构成枚举面）——**须安全评估结论** |
| **Q8** | 意向岗位是否收敛为字典？ | 一期**自由文本**（无岗位实体，[§0.2-⑥](#02-事实性纠正任务描述--既有材料-与实测出入)） | 引入岗位字典（新建实体，属范围扩张） |
| **Q9** | 部门 `deptId` 由谁提供？ | **审批时必填**（注册不采集；`CREATE_ACCOUNT` 强依赖） | 注册时采集（增加注册表单复杂度，且部门属内部结构） |
| **Q10** | 审批通过时薪资是否必填？ | **必填**（否则落 0 值，工资单会静默为 0） | 允许缺省 0，后续调薪补 |
| **Q11** | 是否对 `apply_no` 增设 DB 唯一索引（D7 例外）？ | **增设**（公开端点并发高于后台，且无逻辑删除复用语义） | 不加（完全遵守 D7，接受并发双单风险） |
| **Q12** | 注册创建 `hr_flow` 时 `operator_id/operator_name` 记什么？ | 记 `null`（无员工上下文） | 记系统哨兵（如 0/「自助注册」），需前端兼容 |
| **Q13** | 申请数据（姓名/手机号）个人信息处理说明与留存期限 | 沿用既有脱敏口径 + 终态清理凭据；留存期与合规声明须用户确认 | — |

### 8.2 安全风险（**须网络安全工程师评估，P0.5**）

| # | 风险 | 依据 | 处置 |
| --- | --- | --- | --- |
| S1 | **新增 4 个公开端点 + 1 个公开场景**，扩大攻击面（枚举/刷量/短信轰炸/成本放大） | [PublicEndpoints.java:13-14](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)、[security-auth-review.md:281](security-auth-review.md) R9 | **公网暴露前必须过 P0.5**，结论+授权表单归档；未过不得暴露 |
| S2 | **手机号枚举**（`9310` 暴露"该号已注册"） | [security-auth-review.md:245](security-auth-review.md) §4.4③ | 已用"验证码通过后才判定"缓解（§3.4）；**仍须评估确认 Q7 口径** |
| S3 | **短信轰炸/成本放大** | 同上② | 4 维频控 + Nginx `limit_req` + 建议 captcha 闸门 |
| S4 | **验证码明文红线** | [security-auth-review.md:246](security-auth-review.md) §4.4⑦ | 复用 `SmsSendVO`（无码）；建议补 `ClientLogSanitizer` 正则 |
| S5 | **密码/凭据残留** | 本方案 | 终态清空 `password_hash/query_token_hash`（§2.2） |
| S6 | **注册注入角色/薪资** | 本方案 | 字段白名单 + DTO 隔离（§5.4） |
| S7 | **公开驿站列表泄露** | 本方案 | R-5 白名单出参（仅 `id/stationName`） |
| S8 | **登录前无频控**（公开注册同样是"登录前"面） | [security-auth-review.md:115](security-auth-review.md) SEC-AUTH-03 | 同 R3：至少一层限流 |
| S9 | **未强制 HTTPS / 首登强制改密未服务端强制** | SEC-AUTH-02 / R6 | 属既有 R1/R2 硬阻断项与 R6，**本方案不改变其状态**，但注册进一步暴露这些面，须在放行前闭环 |

### 8.3 工程风险

| # | 风险 | 处置 |
| --- | --- | --- |
| E1 | 并发审批无行锁（§4.5） | 建议 `SELECT ... FOR UPDATE` 或乐观锁；列为**必改项**交后端 |
| E2 | 复用 `hr_flow` 承载审批，需回写 `registration`（驳回/撤回/超时三处） | 三处均须同事务回写，漏一处即状态漂移；测试须覆盖三态矩阵 |
| E3 | `HrSalaryWriter` 幂等性未验证 | 列为 §10 TODO；审批重复调用测试须覆盖 |
| E4 | `registration.flow_id` 与 `hr_flow.employee_id` 双向关联的一致性 | 建议在 §4.5 守卫中同时校验两表状态 |
| E5 | 既有 `hr_flow.status` 复用 `REJECTED` 承载撤回/超时 | 文档显式登记；若不接受，须扩枚举（契约变更，另评） |

***

## 9. 实施批次拆解

> 依赖顺序：**数据库 → 后端（含契约）→ 契约定稿 → UI/UX → 前端 → 测试 → 部署**。每批**可独立验证**。C 档动作（迁移执行、部署）须主智能体三步授权。

| 批次 | 内容 | 依赖 | 验收标准（可执行/可静态审查） |
| --- | --- | --- | --- |
| **B0 安全评估前置** | 网络安全工程师评估 §8.2（S1~S9） | 本方案（R25 评审后） | **已完成**：结论「**阻断（公网）**」（`security-registration-review.md`）。B0 由"待评估"转为"**已出结论 + 须闭环 M-1~M-9**"；B7 公网暴露前须 **S-6 复验** |
| **B1 契约定稿** | 后端按 §3 写入 `api.md`（§2.2 错误码 / §4.0 / 新章）；同步 `multi-client-architecture.md` 场景表 | B0 结论 | 范围经 §11.11 调整：① **前置必录** 入离职 10 + 人事档案 6 = **16 端点**（新建 `§4.10 人事/入离职`）；② 注册侧公开仅 R-1(场景扩展)+R-2(提交)；③ 错误码 9307/9309 保留、9310 视 **U-03**；④ `PublicEndpointsTest` 快照回归更新；其余域（财务/KPI/工单/同步/包裹/通知 共 67）**拆独立批** |
| **B2 数据库** | **V16** `employee_phone_unique`（M-5 前置）+ **V17** `employee_registration` + **V18** `hr_flow_source`（M-9）；快照同步 + `db.md` 新章；回滚语句 | B1 | 双库脚本齐；不改历史脚本；快照与迁移一致；含回滚语句；**V16 预检返回 0 行**方可执行（C 档授权） |
| **B3 后端-注册侧** | 新表实体/Mapper/Service/Controller；`SmsScene.REGISTER` + `resolveScene` 改造 + 配置键；`PublicEndpoints` **净增 1 条**（M-6）；`ErrorCode` 9307~9309（9310 视 U-03） | B2 | 单测：验证码前置、`9307` 重复、非法 scene 拒绝、R-5 出参白名单；`PublicEndpointsTest` 更新 |
| **B4 后端-审批联动** | R-6 聚合端点；R-9 驳回回写；R-8 出参扩展；事务/幂等/并发守卫 | B3 | 单测：三件事同事务（失败全回滚）、重复审批 `9303`/`9309`、并发守卫、终态清凭据 |
| **B5 UI/UX** | 注册页与"申请进度"页设计规范（Tokens 复用、四态、对比度） | B1 | 设计规范冻结（Tokens 明确）；四态（loading/empty/error/normal）齐 |
| **B6 前端** | staff-h5 注册页 + 登录页入口 + api 层；PC 审批页「审批通过」表单 + `registration` 展示 | B4, B5 | `build`/`lint` 通过；按 Tokens 还原；四态覆盖；`vitest` 通过 |
| **B7 测试与部署** | 接口/E2E + 门禁实跑；Mock 同步；部署（C 档） | B6, B0 | 用例覆盖 §3.4 全部口径；回归不弱化；**B0 结论为"可放行"且 R1/R2/R3/R6 闭环**方可公网暴露 |

> **批次可并行性**：B1 与 B2 可并行（B2 只依赖字段定稿，不依赖错误码）；B3/B4 串行；B5 与 B3/B4 可并行（无产物依赖，且写入路径不重叠）。**并发写入同一工作区仍须单一写者**（[智能体调度规则 §12 M04]）。

***

## 10. 遗留与 TODO(扩展)

| # | 项 | 说明 |
| --- | --- | --- |
| T1 | `HrSalaryWriter.save` 幂等性 | **~~未验证~~ 已核实（§11.7）**：`save()` = `updateById`（覆盖）+ `insert`（追加留痕），**无幂等键 → 非幂等**，每次调用追加一条 `hr_salary_log`；幂等责任在流程状态守卫，R-6 须保证单事务内仅调用一次 |
| T2 | 并发审批行锁 | **~~既有 `requireFlow` 无锁~~ 已核实（[:502-503]）并为 §11.7 设计**：R-6/R-9 事务首条对 `hr_flow` 行 `SELECT ... FOR UPDATE`，固定加锁顺序；验收见 §11.2/M-4 与 §11.7 |
| T3 | 超时失效执行方式 | 建议一期**惰性判定**（查询/审批前判 `expire_time`），自动任务 `TODO(扩展): 规模上升后引入定时任务` |
| T4 | 短信 `template-code-register` | 阿里云模板须新申请（属凭据/外部依赖，主智能体归口） |
| T5 | `ClientLogSanitizer` 凭据正则补 `code=` 形态 | 安全建议（[security-auth-review.md:246](security-auth-review.md) §4.4⑦），独立小改 |
| T6 | 通知联动 | 审批通过/驳回是否发站内通知（`notification.type` 需新增 7/8），本方案**未纳入**，`TODO(扩展): 按运营需要另批设计` |
| T7 | 注册来源扩展 | 一期仅 `STAFF_H5`；`TODO(扩展): 企微/安卓壳渠道`（三期） |
| T8 | `security-registration-review.md` 响应 | **~~当前缺失~~ 已完成（v1.1 §11）**：M-1~M-9 闭环对照（§11.2）、S/O 处置（§11.3）、统一待裁定（§11.4）已出 |
| T9 | 岗位字典化 | 见 §8-Q8，一期不做 |

***

## 11. 安全评估响应（v1.1 增量 · 逐条闭环）

> 响应对象：[security-registration-review.md](security-registration-review.md)（**331 行**，结论等级 **「阻断（公网）」**）。
> 本方案 v1.0 原 §0.2-③ 的"报告缺失、无法逐条响应"阻塞项**已解除**。
> 行文约定：对**已被安全评估推翻**的原表述一律用 `~~删除线~~` 并在正文标注改点；本方案修订后**仍属方案阶段产物**，须经**技术评审工程师**评估（L8），**不宣称已通过**。

### 11.1 总体处置

- 安全三条硬阻断：**REG-01/09**（短信轰炸 + 成本耗尽）、**REG-03/05**（提权 + 绕过人工审核）、**REG-02/04/06**（枚举 + 重复号 DoS + IDOR）。
- 降级路径（安全 §7.2）：**M-1~M-9 全部闭环 + S-6 复验** → 「有条件放行」；**M-3 / M-4 / M-5 任一未闭环 → 维持阻断**。
- 本方案对 **M-1~M-9 全部采纳**；其中 **M-4 / M-6 / M-9** 触发对 v1.0 设计的**实质性推翻**（见 11.2 各行，原文已加删除线）。S 项按 11.3 分档；O 项按 11.3 处置。

### 11.2 M-1 ~ M-9 闭环对照表

| 编号 | 安全要求（摘要） | 本方案设计落点（章节/字段/接口/事务） | 是否需改原设计（改了什么） | 验收对应（安全 §6.1 → 本方案） |
| --- | --- | --- | --- | --- |
| **M-1** | 独立 `SmsScene.REGISTER`；`resolveScene` 未知场景**拒绝**而非回落 LOGIN | §3.2 R-1 增 `scene=REGISTER`；**新增约束**：`SmsScene` 增 `REGISTER` 后，`resolveScene` 对**非空且非法** scene 抛 `BusinessException(BAD_REQUEST,"不支持的短信场景")`，**仅空/空白**保持回落 `LOGIN`（保既有前端契约，[AuthServiceImpl.java:606-619](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L619)） | **改**：原 §3.2 R-1 只写"缺省仍按 LOGIN"，**未处理非法值 fail-open** → 增补 `resolveScene` 改造约束（[SmsScene.java:18-29](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L18-L29)） | 单测：未知**非空** scene → 拒（非 LOGIN）；`REGISTER` 对未注册号可发码且**不**走 `sendLoginCode` 账号查询分支 |
| **M-2** | 发码/提交响应**完全恒定化**（含 1109/2003 差异消除） | §3.4「发码环节一致性」**采纳**（发码恒定，已注册/未注册都发码）；**提交段**原 `9310` 与 M-2 严格口径**冲突** → 两形态见 **U-03** | **部分推翻**：~~§3.4「验证码通过后告知已注册不构成枚举」~~ 需安全复验确认；默认按严格形态改造则废弃 `9310`，否则保留并报豁免 | 取严格形态时：已注册/未注册两组各 ≥50 次，响应 `{code,message,data}` 与 HTTP 状态逐字段一致 |
| **M-3** | 注册字段与 role/薪资/站点解耦；`role` 恒 `STAFF`；薪资仅 ADMIN 可写 | §5.4 + **新增 §11.5 完整字段分离表**；注册 DTO 字段更名 `intentStationId`/`intentPosition`（**废弃**顶层 `stationId` 语义）；增 `@Valid` + `FAIL_ON_UNKNOWN_PROPERTIES`（并入 S-3） | **改**：原 §3.2 R-2 入参 `stationId`（必填）→ `intentStationId`，并明确"仅意向/仅展示，建档时由 ADMIN 校验后写入" | 契约测试：载荷含 `role/stationId/basicSalary` → 400 或忽略不落库；建档后 `employee.role=='STAFF'`；定薪值来自 ADMIN 提交 |
| **M-4** | 审批通过**不自动激活**；保留 `status=0` + 首登强制改密；**不复用注册密码** | **新增 §11.8**；R-6 聚合**仅前 3 步**（CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY），停在 `SET_SALARY`，`hr_flow` 保持 `IN_PROGRESS`；`employee.status` 恒 0；`DONE`（激活）改由 ADMIN **二次显式确认** | **推翻**：~~§1.1 S4「…→DONE(转在职 status=1)」~~、~~§4.3 事务顺序含 DONE~~、~~§2.3「password_hash → employee.password」~~、~~§8-Q3「申请单散列作初始密码」~~（均加删除线）；初始口令由 ADMIN 在 `CREATE_ACCOUNT` 一次性设定（既有事实⑪） | 审批通过后 `employee.status==0`；首登强制改密生效；注册所填密码**不出现**在 `employee.password` 链 |
| **M-5** | 建档补手机号唯一校验 + `employee.phone` **DB 唯一约束**（含存量去重） | **新增 §11.6 既有缺陷修复**（Service 查重 + DB 唯一约束 + 预检 SQL + 迁移/回滚） | **改**：原 §2.4「不建 DB 唯一索引」（D7）对 `employee.phone` **增设例外**（M-5 硬项）；原 §4.1 `CREATE_ACCOUNT` 须加 phone 查重 | 存量重复号清单清零；并发同号仅 1 行落库；`selectOne(phone)` 恒命中 ≤1 行 |
| **M-6** | 申请接口 ADMIN-only；公开白名单**仅**发码与提交两条 | §3.1 端点清单**收敛**：公开白名单仅 R-1（发码，复用）+ R-2（提交）；**R-3/R-4 退出公开白名单**；R-5 见 **U-04** | **推翻**：~~§3.1「新增公开端点 4 个（R-2~R-5）」~~（加删除线）；R-3 改 ADMIN-only、R-4 取消或转 ADMIN，**净新增白名单 = 1 条**（R-2） | `PublicEndpoints.all()` 新增仅 1 条；申请管理端点非 ADMIN → 403 |
| **M-7** | Nginx `limit_req`/`limit_conn` + 应用层全局日上限 | §5.1 表增量：Nginx `limit_req zone`（按 `$binary_remote_addr`）+ 全局 `limit_conn`；应用层在既有五维外**增**「手机号×全局日上限」+「注册提交日上限」 | **改**：原 §5.1 仅"**建议** Nginx `limit_req`" → 升为**必做**并具体化；Nginx 配置执行归运维（B/C 档，主智能体授权） | 单 IP 高频 → 429/1101；换号轰炸命中全局上限；伪造 `X-Forwarded-For` 不影响（Nginx 以 `$remote_addr` 计） |
| **M-8** | 未过审数据保留期限 + 清理；最小必要；出参脱敏 | §2.2（`source`/`agreement_version`/终态清凭据）+ **新增** `retention` 配置项与清理任务（扩展 T3）；R-3/R-8 出参脱敏 | **改**：原 §2.2 **未定**保留期；原 T3 仅"超时惰性判定" → 增"已拒绝/超期"清理任务与配置项 | 清理任务 + 配置项存在（可外置）；超期数据被清理；列表手机号脱敏（`138****1234`） |
| **M-9** | `hr_flow` 增来源标识；记来源 IP（脱敏）/UA/提交时间；审批动作留办理人 | **新增 `hr_flow.source` 列**（迁移脚本，批次见 U-08）；`registration` 保留 `source`/`client_ip` 作注册侧审计补充；审批留痕复用 `hr_flow_step.operator_id/operator_name/operate_time` | **部分推翻**：~~§2.1 方案②"否决"理由之一为"避免动 `hr_flow` 加列"~~ → M-9 要求 `hr_flow` 加 `source`；两表 `source` **并存**（`hr_flow.source` 供既有审批台，`registration.source` 供注册审计） | 自助来源可区分；提交与逐审批动作可回溯 |

### 11.3 S-1 ~ S-6 与 O-1 ~ O-3 处置

| 项 | 处置 | 理由 |
| --- | --- | --- |
| **S-1** 启用真实图形验证码 | **后续做** | 现状为固定占位图 + 默认关闭（REG-10，[CaptchaStore.java:17-20](../hrm-server/src/main/java/com/qiujie/service/auth/support/CaptchaStore.java#L17-L20)），需独立改造；本批以 **M-7 双层限流**兜底，登记残余风险 R-3 |
| **S-2** 注册提交纳入频控 | **本批做** | 并入 M-7 应用层"注册提交日上限" |
| **S-3** 入参强校验 | **本批做** | DTO `@Valid` + `FAIL_ON_UNKNOWN_PROPERTIES` + 枚举白名单 + 长度上限 + 手机号正则（并入 M-3） |
| **S-4** 服务端 IP 取信改造 | **本批做（Nginx 侧）+ 后续做（应用层）** | M-7 验收要求"伪造 XFF 不影响限速"由 Nginx `$remote_addr` 满足（本批）；应用层 `IpUtil` 可信代理白名单改造**影响全局**，独立批（[IpUtil.java:17-31](../hrm-server/src/main/java/com/qiujie/util/IpUtil.java#L17-L31)） |
| **S-5** 埋点与告警 | **后续做** | 依赖监控体系，独立批；本批仅要求 `info` 级关键流转日志 |
| **S-6** 交接实现后安全复验 | **必做（流程项）** | 公网放行前由网络安全工程师复验并出具结论（L7） |
| **O-1** 短信通道日预算/多供应商降级 | **后续做** | 运维 + 主智能体 C 档协同 |
| **O-2** 行为风控（滑块/设备指纹） | **不做** | 依赖算法工程师 R09 选型，**超本方案范围** |
| **O-3** 高风险号段/IP 黑名单 | **后续做** | 运营策略位 |

### 11.4 统一待裁定表（Q1~Q13 去重合并 + 新增项）

> 编号连续 **U-01~U-17**；`⛔` = **阻塞公网放行**（M-3/M-4/M-5 相关或决定放行口径）。原 §8.1 Q1~Q13 已映射入此表（Q1→U-01；Q2/Q3→U-02；Q5→U-12；Q6→U-06；Q7→U-03；Q8→U-07；Q9→U-09；Q10→U-10；Q11→U-15；Q12→U-13；Q13→U-14；Q4→U-11）。

| 编号 | 裁定问题 | 建议 | 替代方案 | 影响面 |
| --- | --- | --- | --- | --- |
| **U-01** ⛔ | 审批激活时点：审批通过即 `status=1`，还是审批后 **DONE 二次人工确认**才激活？（原 Q1） | **DONE 二次确认**（满足 M-4；聚合仅前三步） | 审批即激活（**违反 M-4，维持阻断**） | §1.1/§4.1/§4.3；审批台 UI；用户口径"三件事同时完成"边界 |
| **U-02** ⛔ | 初始口令来源：是否**复用注册自设密码**？（原 Q2/Q3） | **不复用**：ADMIN 一次性口令 + `pwd_changed=0`（M-4） | 复用注册密码 + `pwd_changed=1`（违反 M-4） | §2.3/§3.2/§4.6/§11.8；注册页"密码"字段去留 |
| **U-03** ⛔ | 提交段是否保留 `9310`「该手机号已注册」提示？（原 Q7） | **保留**（验证码持有为前置闸门，M-1 已消除 fail-open）+ 报安全复验确认 | M-2 严格形态：提交恒返回受理外观，已注册由 ADMIN 审批时识别（无 `9310`） | §3.3 错误码/§3.4 口径/前端文案 |
| **U-04** ⛔ | 公开白名单是否**豁免 R-5 驿站选项**（第 3 条公开端点）？ | **申请豁免**（极简出参 `{id,stationName}` + Nginx 限流）**或**改前端静态配置 | 取消公开（注册页无法选意向驿站）/ 并入提交接口 | §3.1/§5.1；`PublicEndpoints.all()` |
| **U-05** ⛔ | `employee.phone` 唯一约束形态（M-5） | **活跃唯一**（MySQL 生成列 `phone_active` + `UNIQUE`；PG partial unique index `WHERE is_deleted=0`），保留已删号复用 | 直接 `UNIQUE(phone)`（破坏逻辑删除复用，不推荐） | `employee` DDL、迁移 V16、快照、`db.md` |
| **U-06** ⛔ | R-3（查询）/R-4（撤回）去向（原 Q6） | R-3 改 **ADMIN-only**（进度线下/短信告知）；R-4 **取消**（超时自动失效） | 保留凭据式公开（需安全豁免）/ 完全取消两者 | §1.1/§3.1/§3.3（`9308`/`9309`）、`registration.query_token_hash` 去留 |
| **U-07** | 「岗位」方案甲（仅 `hr_flow.position`）vs 乙（新增 `employee.position`）（原 Q8） | **方案甲**（零结构变更，一期） | 方案乙（C 档结构变更，见 §11.9） | `employee` DDL、VO、前端档案/列表/导出、Mock |
| **U-08** | `hr_flow.source` 迁移批次（M-9） | 与注册同批、**独立脚本 V18**（同批评审） | 延后至注册上线后 | 迁移版本规划、快照、`db.md` |
| **U-09** | 部门 `deptId` 由谁提供（原 Q9） | **审批时必填**（注册不采集） | 注册时采集（增表单复杂度，部门属内部结构） | §3.2 R-6、审批台表单 |
| **U-10** | 审批通过时薪资是否必填（原 Q10） | **必填**（否则落 0 值，工资单静默为 0） | 允许缺省 0，后续调薪补 | §3.2 R-6；`hr_salary` 0 值占位语义 |
| **U-11** | 登录账号 `username` 口径（原 Q4） | 默认 `u`+手机号（满足 `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`），ADMIN 可覆盖 | ADMIN 必填 / 拼音生成（复杂度高） | §3.2 R-6 |
| **U-12** | 审批权限是否仅 ADMIN（原 Q5） | **仅 ADMIN**（已被 M-6 定为 ADMIN-only） | 两级审批（同 leave 模型，范围扩张） | §5.3；`HrFlowController` 权限 |
| **U-13** | 注册创建 `hr_flow` 的 `operator_id/operator_name` 记什么（原 Q12/M-9） | `null` + `hr_flow.source=SELF_REGISTER` + 来源 IP（脱敏）/UA/提交时间 | 系统哨兵（如 0/"自助注册"），需前端兼容 | §2.3；`hr_flow` 列与审批台展示 |
| **U-14** | 申请数据保留期限与个人信息处理说明（原 Q13/M-8） | 已拒绝/超期保留 **N 天**（配置项，建议 30），终态清凭据 | — | §2.2/§5.2；清理任务 |
| **U-15** | 手机号查重命中返回码（新） | 复用 `PHONE_EXISTS(2003)`（与 `EmployeeServiceImpl` 同口径） | 新增 `9311 REGISTRATION_PHONE_TAKEN` | §3.3 错误码、安全 M-5 验收 |
| **U-16** | 非法短信场景返回码（M-1 新） | 复用 `400`（`BAD_REQUEST`，"不支持的短信场景"） | 新增 `1111 SMS_SCENE_INVALID` | `resolveScene` 改造、错误码表 |
| **U-17** | 是否对 `apply_no` 增设 DB 唯一索引（原 Q11） | **增设**（公开端点并发高于后台，且无逻辑删除复用语义，D7 例外） | 不加（接受并发双单风险） | `employee_registration` DDL/迁移 |

### 11.5 字段分离完整表（M-3 落地）

**表 A：注册 DTO 采集白名单（公开接口 R-2，注册方**仅能**影响本表字段）**

| 字段（DTO） | 来源 | 必填 | 校验 | 可否被注册方影响 | 落点 |
| --- | --- | --- | --- | --- | --- |
| `realName` | 申请人 | 是 | 2-20 字（[HrFlowServiceImpl.java:638-639](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L638-L639)） | 是（仅本单姓名） | `registration.real_name` → `flow.candidate_name` |
| `phone` | 申请人 | 是 | `^1[3-9]\d{9}$` | 是 | `registration.phone` → `flow.phone` |
| `smsCode` | 短信 | 是 | 6 位；`REGISTER` 场景一次性作废 | **否**（服务端校验） | 不落库 |
| `intentStationId` | 申请人 | 是 | 存在 + 启用（4001/4004） | 是（**仅意向**） | `registration.apply_station_id` → `flow.station_id`（审批可改） |
| `intentPosition` | 申请人 | 否 | ≤50 字 | 是（**仅意向**） | `registration.apply_position` → `flow.position`（审批可改） |
| `password` | 申请人 | 视 U-02 | 8-20 位含字母数字 | 是 | **M-4 后**：**不**作员工初始口令；散列存储与终态清空见 §11.8 |
| `agreementVersion` | 申请人 | 是 | 版本串 | 是 | `registration.agreement_version` |

> **硬约束**：表 B 字段**编译期不得出现在注册 DTO**（独立 DTO，**不复用** `HrStepCompleteRequest`）；Spring 侧 `FAIL_ON_UNKNOWN_PROPERTIES=true`，夹带未知字段 → 400（S-3）。

**表 B：审批时 ADMIN 赋值字段（注册方**不可**影响）**

| 字段 | 来源 | 必填 | 校验 | 落点 |
| --- | --- | --- | --- | --- |
| `username` | ADMIN / 按规则生成 | 是 | `^[a-zA-Z][a-zA-Z0-9_]{3,29}$` + 活跃唯一（1003） | `employee.username` |
| `initialPassword` | **ADMIN（M-4）** | 是 | 8-20 位含字母数字（[:275-277](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L275-L277)） | `employee.password`（BCrypt），`pwd_changed=0` |
| `deptId` | ADMIN | 是 | 存在（3001） | `employee.dept_id` |
| `stationId`（事实） | ADMIN（缺省取意向） | 是 | 存在 + 启用（4004） | `employee.station_id` |
| `role` | ADMIN | 否（默认 `STAFF`） | `ASSIGNABLE_ROLES` 白名单（[HrConstants.java:52](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L52)） | `employee.role` |
| `position`（事实） | ADMIN（缺省取意向） | 否 | ≤50 字 | `flow.position`（是否入 `employee` 见 U-07） |
| `basicSalary/postSalary/performanceBase/allowances` | **仅 ADMIN** | 视 U-10 | `HrSalaryValidator`（[:697-701](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L697-L701)） | `hr_salary` |
| `pwdChanged` | 系统 | — | **恒 0**（M-4） | `employee.pwd_changed` |
| `status` | 系统 | — | **恒 0**（M-4；激活由 DONE 步骤，见 U-01） | `employee.status` |

### 11.6 既有缺陷：手机号唯一（REG-04 / M-5）

**现状（实测，缺陷当前即存在，安全要求"先于注册上线修复"）**

- `createEmployeeForFlow` **仅校验 username**（[:272-274](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L272-L274)），**无 phone 查重**，直接 `employee.setPhone(flow.getPhone())`（[:296](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L296)）。
- 对照 `EmployeeServiceImpl` **有** `existsActivePhone`（[:131-133](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L131-L133)、[:547-556](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L547-L556)），返回 `2003`。
- `employee.phone` **无 DB 唯一索引**（[db.md:48](db.md) 决策 D7；[:184](db.md)/[:286](db.md) `idx_employee_phone` 为**普通索引**）。
- 登录按 phone `selectOne` → 命中多行抛异常 → 目标账号登录/短信登录 **DoS**（安全链 D）。

**① 修复设计**

- **Service 层**：`createEmployeeForFlow` 在 username 校验后**增** phone 活跃查重（复用 `existsActivePhone` 同口径），命中 → `PHONE_EXISTS(2003)`（或 U-15 的新码）；`assignForFlow` 若后续允许改 phone 亦须查重（当前不改 phone，登记为约束）。
- **DB 层**：`employee.phone` 增设**活跃唯一**约束（M-5 硬项）。两形态见 **U-05**，推荐**活跃唯一**：
  - MySQL：生成列 `phone_active VARCHAR(20) GENERATED ALWAYS AS (IF(is_deleted=0, phone, NULL)) STORED` + `UNIQUE INDEX uk_employee_phone_active(phone_active)`（NULL 可重复 → 已删号可复用）。
  - PostgreSQL：`CREATE UNIQUE INDEX uk_employee_phone_active ON employee(phone) WHERE is_deleted = 0;`
- **并发兜底**：DB 唯一约束即最终防线，Service 查重仅为友好报错（消除 TOCTOU 双提交窗口）。

**② 存量重复号预检 SQL（迁移前置，必须返回 0 行）**

```sql
-- MySQL：活跃员工中重复手机号
SELECT phone,
       COUNT(*)                       AS cnt,
       GROUP_CONCAT(id ORDER BY id)    AS ids,
       GROUP_CONCAT(real_name)         AS names
FROM employee
WHERE is_deleted = 0
GROUP BY phone
HAVING COUNT(*) > 1;

-- PostgreSQL：等价写法
-- SELECT phone, COUNT(*) cnt, string_agg(id::text, ',' ORDER BY id) ids, string_agg(real_name, ',') names
-- FROM employee WHERE is_deleted = 0 GROUP BY phone HAVING COUNT(*) > 1;
```

> 若返回非空：**先去重再迁移**；去重（保留哪条）属**数据变更**，须主智能体 C 档授权 + 人工确认。

**③ 迁移影响与回滚**

- 脚本：新版本号 **`V16__employee_phone_unique.sql`**（mysql + pg；当前最大 [V15](../hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql)），**禁止改历史脚本**；同步 `sql/schema/{mysql,postgresql}/init.sql` 快照与 `db.md`。
- 影响：**结构变更（C 档）**；执行前须**备份**；对既有数据无回填（仅加约束/生成列）。
- 回滚：`DROP INDEX`（脚本注释附回滚语句），**不改数据**；若已发生合法数据依赖，回滚前须评估。
- 风险：预检非 0 → 迁移失败（安全 **R-5**），须先人工去重。

**④ 档位标注**

**结构变更（C 档）**，且该缺口**当前即存在**、**先于注册公网上线修复**；授权须主智能体按 §10.3 三步表单，**无安全评估结论不得授权**（[智能体调度规则 §10.3]）。

### 11.7 事务与并发（行锁 / 加锁顺序 / 死锁规避 + `HrSalaryWriter` 幂等结论）

- **事务边界（按 M-4 修订）**：R-6 聚合事务 = `CREATE_ACCOUNT` → `ASSIGN_STATION` → `SET_SALARY` → `registration.status=APPROVED` → 清 `registration` 凭据列；**不含 `DONE`**（§4.3，原文已标注推翻）。
- **锁粒度与位置**：既有 `requireFlow` = `hrFlowMapper.selectById(id)`（[:502-503](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L502-L503)），**无行锁**。R-6/R-9 进入事务后**首条**执行 `SELECT ... FOR UPDATE` 锁 **`hr_flow` 单行**，再校验状态。
- **固定加锁顺序（防死锁）**：① `hr_flow`(FOR UPDATE) → ② `employee_registration`（按 `flow_id` FOR UPDATE 或先写状态）→ ③ `employee`（insert，无锁）→ ④ `hr_salary`（update）。**所有审批/驳回/撤回路径必须遵循同一顺序**。
- **死锁规避**：(a) 固定加锁顺序；(b) 事务内**不做**远程调用/短信；(c) 先锁后校验再操作，缩短持锁时间；(d) `hr_flow` 为**单行锁**，冲突面小；`innodb_lock_wait_timeout` 由运维配置化。
- **审批 vs 撤回 并发**：两路径均以 `registration.status=SUBMITTED` 守卫为准，同一行锁下**其一必失败**（`registration` 加锁顺序一致）。
- **`HrSalaryWriter` 幂等性核对结论（原 T1，已核）**：[HrSalaryWriter.java:57-105](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L105) 的 `save()` = `updateById`（覆盖当前值）+ `insert`（追加留痕），**无幂等键 → 非幂等**：每次调用追加一条 `hr_salary_log`（当前值不变）。**故幂等责任在流程状态守卫**（`hr_flow.status` / `registration.status` + FOR UPDATE），R-6 须保证事务内**仅调用一次**；可选加固：以 `(employee_id, change_type='ENTRY', effective_date)` 或流程 ID 作去重键。另注：`selectByEmployeeId` 取 `list.get(0)`（[:114-118](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L114-L118)），若 1:1 被破坏存在取错行风险 → 与 M-5 同批治理。

### 11.8 不激活口径（M-4 落地细则）

- **`status=0` 含义**：员工档案已建但**未生效**，**不可登录**、不可被引用为在职（既有口径 [:301](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301)；[Employee.java:47-48](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L47-L48)）。
- **首次登录路径**：审批通过后 `employee.status=0` → **无法登录**；须 ADMIN 在流程末步 `DONE` **二次显式确认**（复用 `completeOnboardingStep` DONE 或新端点）后方转 `status=1`，此后员工方可登录。
- **密码来源（M-4）**：由 **ADMIN 在 `CREATE_ACCOUNT` 一次性设定**（既有事实⑪ [:268-277](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L268-L277)），**不复用注册自设密码**；`pwd_changed=0` → 首登强制改密（R6 口径）。
- **与注册自设密码的关系**：注册采集的 password **不作为员工初始口令**；其去向二选一（**U-02**）：(A) 不采集；(B) 仅作合规留痕、终态清空散列。**为何不复用**：切断"注册可控数据 → 账号凭据"链路（安全链 C 的凭据面），且注册密码未经理工端二次强度确认不宜直通账号。
- **与用户口径的张力**：用户口径「审批通过后同时…生成档案 + 薪资 + 岗位/站点/角色 + **本人设置密码**」，其中「账号激活可登录」与「本人密码」两点**与 M-4 冲突** → 显式列入 **U-01 / U-02**。

**两形态对比**

| 维度 | 形态 A（M-4 严格，**推荐**） | 形态 B（用户口径字面） |
| --- | --- | --- |
| 账号激活 | 审批通过后 `status=0`，`DONE` 二次确认才激活 | 审批通过即 `status=1` |
| 初始口令 | ADMIN 一次性口令 + `pwd_changed=0` | 复用注册自设密码 + `pwd_changed=1` |
| 人工审核强度 | 两道（审批 + 激活确认） | 一道（审批即账号） |
| 安全结论 | 满足 M-4，可降级 | **不满足 M-4，维持阻断** |
| 体验 | 多一次 ADMIN 操作 + 首登改密 | 一步到位 |

### 11.9 「岗位」两方案对比与推荐

**背景（实测）**：`position` **仅**存 `hr_flow.position`（`VARCHAR(50)` 自由文本，[HrFlow.java:55-56](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L55-L56)）；`employee` 表**无 position 列**（[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66)）；全仓 VO 中仅 [HrFlowVO.java:44](../hrm-server/src/main/java/com/qiujie/vo/hr/HrFlowVO.java#L44) 有 `position`。

| 维度 | 方案甲（不入档案，**推荐一期**） | 方案乙（新增 `employee.position`） |
| --- | --- | --- |
| 结构变更 | **无**（零 DDL） | 有：加列 → **C 档** |
| 改动面 | 复用 `flow.position`；审批页/流程详情展示；无需动 `employee` | `Employee` 实体/VO/Mapper、前端员工档案与列表、`EmployeeCreateRequest`、导出模板、Mock |
| 迁移成本 | 0 | 迁移 + 快照 + `db.md` + Mock + 导出模板；历史员工 `position` 需回填策略 |
| 风险 | 岗位仅"流程属性"；历史多单时须约定取最新 ONBOARDING 单 | **双写一致性**（`flow.position` vs `employee.position` 谁权威）；历史数据空值 |
| 对既有报表/工资单/考勤影响 | **无**（工资单取 `hr_salary`，考勤取排班，均不读 `position`） | 员工列表/导出需增列；**不直接影响**工资单/考勤（其数据源不含 `position`） |
| 用户口径契合 | 「设置岗位」在流程单层面成立 | 岗位成为档案属性，口径最直观 |

**推荐**：一期取 **方案甲**（零结构变更、与既有实现一致、审批页可展示 `flow.position`）；若产品要求岗位进员工档案/报表 → 走 **方案乙**（C 档，须用户授权，可与 M-5 唯一约束同批迁移）。→ **待裁定 U-07**。

### 11.10 `api.md` 实况复核（v1.1 重核，不沿用 v1.0 读数）

复核方式：重新 `Grep` 全仓 controller 方法级 `@*Mapping`（**23 文件 / 151 个映射**）+ 读 `api.md` §4.0 概览与 §7。

| 结论 | 实测 |
| --- | --- |
| §4.0 概览计数「46」 | **已自洽**：认证 4 + 看板 1 + 员工 10 + 部门 4 + 驿站 5 + 考勤 10 + 补卡 4 + 班次 4 + 排班 4 = 46——考勤域 **22 端点**为**主智能体已补录**，**非**历史残留。~~原 §0.2-④「与代码不一致／历史残留」~~ 已被本次复核**推翻**（[api.md:184-234](api.md)） |
| 代码端点总量 | **151**（controller 方法级映射计数）；`api.md` §4.0 覆盖 **46**、§7 覆盖 **16**（请假 13 + 客户端日志 3），**合计 62 / 151** |
| **§4 未建章节的域（缺口 83）** | 入离职 `hr_flow` **10**、人事档案 `hr_profile` **6**、财务 `payroll` **10**、资薪规则 `payroll-rule` **5**、KPI **9**（score 4 + metric 5）、工单 **9**、同步 **22**（task 5 + config 4 + config-center 13）、包裹 **6**、通知 **6** |
| 认证增强未入 §4.0 概览表 | `sms/send`、`sms/login`、`device/verify`、`devices`、`devices/{id}`、`captcha` 共 **6** 端点（§4.1.5 为端准入契约说明，非端点表） |

→ 需**补录 83 端点（§4 章节）+ 概览表补 6 行**；其中与注册方案**直接相关**的是 **入离职 10 + 人事档案 6 = 16**（R-6 落点）。

### 11.11 契约批次（B0 / B1）调整

- **B0**：安全评估**已产出**（结论「阻断（公网）」）→ 由"待评估"转为"**已出结论 + 须闭环 M-1~M-9**"；B7 公网暴露前须 **S-6 复验**（§9 已同步）。
- **B1 范围调整**（验收标准随之改，§9 已同步）：
  1. **前置必录**：补录 **入离职 10 + 人事档案 6 = 16 端点**（新建 `§4.10 人事/入离职`），R-6 与之同章；
  2. **注册侧公开仅 R-1（场景扩展）+ R-2（提交）**；R-3/R-4 按 M-6 收敛（U-06）；
  3. **错误码**：`9307`/`9309` 保留；`9310` 视 **U-03** 保留或废弃；新增非法短信场景码（**U-16**）；
  4. **`PublicEndpoints` 净新增 1 条**（R-2；发码路径 `/auth/sms/send` 既已白名单，[PublicEndpoints.java:31](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L31)）；
  5. **验收**：与 §11.2 各 M 项验收逐条对齐；`PublicEndpointsTest` 快照回归断言更新。
  6. 其余域（财务/KPI/工单/同步/包裹/通知 共 **67** 端点）**拆为独立补录批次**，不阻塞注册方案。

### 11.12 本次修订新增的事实性纠正

| # | 旧认知 | 实测 | 出处 |
| --- | --- | --- | --- |
| ③′ | `security-registration-review.md` 不存在 | **已存在（331 行，结论阻断）** | [security-registration-review.md](security-registration-review.md) |
| ④′ | `api.md`「46」为历史残留、与代码不一致 | **§4.0 的 46 已自洽**（考勤域 22 已补录）；但仅覆盖代码 151 端点的 62/151（含 §7） | [api.md:184-234](api.md)、[api.md:1134](api.md) + controller 计数 151 |
| T1′ | `HrSalaryWriter` 幂等性"未验证" | **已核：非幂等**（`update` + `insert` 无幂等键） | [HrSalaryWriter.java:57-105](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L105) |
| 新⑥ | — | `resolveScene` 对未知**非空** scene **回落 LOGIN**（fail-open，放大枚举） | [AuthServiceImpl.java:606-619](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L619)、[SmsScene.java:18-29](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L18-L29) |

***

## 附：本方案自身检查（设计侧）

| □ | 检查项 | 结论 |
| --- | --- | --- |
| □ | 一切结论有出处（文件:行号 / 条款） | ✅ 全文标注；未标处为**设计建议**且显式标注 |
| □ | 无臆造实体/字段/接口 | ✅ 「岗位」明确声明**无实体**（§0.2-⑥）；字段/接口均标注"草案/建议/需评审" |
| □ | 未改任何代码 / 未改其它文档 / 未执行 git | ✅ 本次修订**仅改本文件**（`registration-design.md`）；未改代码、未改 `security-registration-review.md` 或其它文档、未执行 git |
| □ | 与 `api.md` / `db.md` 一致 | ✅ 体例、编号、命名、D7/D8 沿用；差异**显式登记**（api.md 计数经 §11.10 复核、hr_flow `REJECTED` 语义复用 §1.2/E5、`employee.phone` D7 例外 §11.6） |
| □ | 需求口径未被改动 | ✅ 原文照录 §0.3；冲突处（自设密码/激活 vs M-4）以"待裁定"呈现，未擅自取舍 |
| □ | 需用户裁定项齐备 | ✅ **统一待裁定表 §11.4 共 17 项（U-01~U-17）**，由原 §8.1 Q1~Q13 去重合并 + 新增，逐条附建议/替代/影响面，阻塞项标 ⛔ |
| □ | 安全评估逐条闭环 | ✅ M-1~M-9 对照（§11.2）、S-1~S-6/O-1~O-3 处置（§11.3）齐；被推翻表述已加删除线 |
| □ | 方案阶段产物 → 报到主智能体前须经技术评审 | ✅ 本文件**不宣称已通过**；状态标"待技术评审"；§11 修订后**仍须重评** |
| □ | 涉及 C 档动作（迁移执行/部署） | ✅ 已标注须主智能体三步授权，本方案不执行 |
