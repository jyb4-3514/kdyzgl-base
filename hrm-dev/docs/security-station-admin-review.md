# 站长（STATION_ADMIN）账号创建放开 · 驿站精灵端写操作 · 技术安全评估报告

> 文档性质：**安全技术结论**（网络安全工程师 `express-station-security-engineer` 产出，**非授权结论**）
> 评估对象：① 放开 STATION_ADMIN 账号的创建/分配；② 驿站精灵端（boss-h5）新增「驿站管理」及站内员工/站长账号写操作；③ 站长权限限定本站
> 评估方式：**纯静态源码与配置只读审计**。**本机无 JDK / Maven / MySQL / Redis，未编译、未运行、未连库、未做渗透或端到端验证**；一切「运行时实际行为」按源码/配置推断，标注「待核实」或「服务器阶段实测」，不假定。
> 权限档位：A 档（只读检索 + 新建本报告）。**未改任何被评估代码/配置/契约，未调用 MCP，未执行任何 git 操作，未读取任何真实凭据明文。**
> 报告内**不含真实凭据明文**、不含服务器 IP/域名。
> 日期：2026-09-27｜依据基线：`项目规则1.md`、`智能体调度规则.md`、`security-full-review-20260926.md`、`security-client-admission-review.md`、`security-registration-review.md`
> 上游既有分级口径参照 `security-full-review-20260926.md`（严重 / 高 / 中 / 低），本文沿用。

---

## 一、评估范围与取证方式

### 1.1 范围

| 范围 | 覆盖 |
| --- | --- |
| 鉴权与会话 | `filter/JwtAuthFilter.java`、`config/RequireRolesInterceptor.java`、`annotation/RequireRoles.java`、`common/PublicEndpoints.java`、`enums/RoleEnum.java` |
| 端准入 | `service/auth/support/ClientAdmissionPolicy.java`、`resources/application.yml`（`hrm.auth.*`）、`docs/api.md`（端准入矩阵） |
| 定位与收敛 | `config/QueryDataScopeInterceptor.java`、`dto/support/StationScopedQuery.java`、`service/support/ResourceAccessChecker.java`、`annotation/DataScope.java`、`util/DataScopeContext` |
| 角色门槛分布 | 全量 `@RequireRoles` 检索（`hrm-server/src/main/java`，130 处） |
| 站点/账号写路径 | `controller/station/StationController.java`、`controller/employee/EmployeeController.java`、`service/station/impl/StationServiceImpl.java`、`service/employee/impl/EmployeeServiceImpl.java`、`dto/employee/EmployeeCreateRequest.java`、`dto/employee/EmployeeUpdateRequest.java` |
| 前端/端 | `hrm-clients/apps/boss-h5/src/router/index.js`、`hrm-clients/apps/boss-h5/src/modules/boss/views/station.vue`、`hrm-admin/src/views/employee/index.vue` |
| 审计能力 | 全量检索 `operation_log / 操作留痕 / AuditLog`；`docs/db.md`（`login_log` / `leave_log` / `payroll_log`） |

### 1.2 取证方式与限制

- **只读静态审计**：逐文件阅读源码、注解、DTO 校验、YAML 配置、DDL、契约文档。
- **未做**：编译、单测、E2E、接口抓包、越权实测、依赖 CVE 扫描、现网配置核对。存量数据是否已有 STATION_ADMIN 账号未连库确认（仅核到 `sql/seed/kdyzgl_test_seed.sql` 含演示站长号；生产迁移脚本 `V1..V21` **无** STATION_ADMIN 建号）。
- 依赖结论一律以「源码/配置描述为准 + 待实测」表述。

### 1.3 事实核实（含与任务描述的出入）

| # | 任务描述 | 实测 | 判定 |
| --- | --- | --- | --- |
| F1 | `employee` 表即账号表，含 `role`/`station_id`；角色注释「二期启用」 | 与 `docs/db.md:191,204-205` 一致 | 属实 |
| F2 | 一期接口挡死站长角色（`db.md:234`） | **真源在 DTO 校验**：`EmployeeCreateRequest.java:44-47` 与 `EmployeeUpdateRequest.java:37` 的 `@Pattern("^(ADMIN|STAFF)$")`；`db.md:234` 为文档记载 | 属实，**补充实现位置**（改点是 DTO，非文档） |
| F3 | boss-h5 路由 fail-closed 仅 ADMIN | 与 `boss-h5/src/router/index.js:9`（`BOSS_ROLES=[ROLE.ADMIN]`）一致 | 属实 |
| F4 | 既有接口类级 `@RequireRoles({"ADMIN"})` | 与 `StationController.java:27`、`EmployeeController.java:32` 一致 | 属实 |
| F5 | （描述未提）STATION_ADMIN 授权面规模 | 代码中**已声明约 45 个端点的 STATION_ADMIN 门槛**（工单/包裹/考勤/排班/KPI/同步/请假/通知/工资单等），当前因无人持该角色而为「休眠」 | **重要补充：放开创建 = 首次激活一整套既有授权面** |
| F6 | （描述未提）端准入现状 | `application.yml:119-121`：`pc-allowed-roles=ADMIN`、`boss-allowed-roles=ADMIN`、`staff-allowed-roles=STAFF,STATION_ADMIN`；`api.md:419-421` 同步记载 | **补充：站长开号后即可登「员工端 H5」；登不了 boss-h5 与 PC 端（1110）** |
| F7 | （描述未提）演示/二期口径 | `shared/constants/role.js:3` 注「STATION_ADMIN 一期未启用，Demo 提前启用」；Mock/PC/员工端 H5 均已实现站长分支 | **补充：「一期挡死」仅在员工写接口 DTO 白名单层，其余域已按二期实现** |

---

## 二、逐项评估结论

### 2.1 角色放开的风险面（核心）

**结论：谁能创建站长——只有 ADMIN（且应保持只有 ADMIN）；站长能做什么——已声明的约 45 个「本站/本人」受限端点；未发现「本不该由站长操作」的已声明端点，但该授权面此前从未在真实 STATION_ADMIN 身份下被运行时验证。**

- **创建者**：`EmployeeController`/`StationController` 均为类级 `@RequireRoles({"ADMIN"})`（`EmployeeController.java:32`、`StationController.java:27`），经 `RequireRolesInterceptor` 以 **Redis 会话 role** 为准、fail-closed（`RequireRolesInterceptor.java:40-58`）。**只有 ADMIN 能建号**；站长不能建号、不能建站、不能改他人密码（本次评估要求维持此约束，见 M-2）。
- **站长的自保护与最后管理员保护已存在**：`EmployeeServiceImpl.update:160-169`（禁止改自己角色）、`delete:198-204`、`changeStatus:215-223`（禁用自己/最后管理员受保护）。**不存在「站长自升 ADMIN」路径**（改角色需 ADMIN 权限 + 自身角色变更被禁 + 最后管理员保护）。
- **站长可触达端点（实测声明，按域归类）**：

| 域 | 端点（方法 路径） | 收敛机制 | 站点维度判定 |
| --- | --- | --- | --- |
| 工单 | GET/POST `/work-orders`、GET `/{id}`、PUT `/{id}/assign`、PUT `/{id}/status`、POST `/{id}/transfer` | `WorkOrderAccessPolicy`（`canAssign` 仅本站站长/`canManage` 本站/`canView` 同站 404） | 本站 ✓ |
| 包裹 | GET `/parcels`、`/summary`、`/trend`、`/ranking`、`/{id}`、PUT `/{id}/pickup` | `resolveReadScope()`（非 ADMIN=本人驿站）+ 详情/取件归属校验 | 本站 ✓ |
| 考勤 | GET `/attendance/rule`、`/status`、`/records`、`/export`、`/summary`、`/detail`、`/my`、POST `/attendance/check-in` | L1 收敛（`StationScopeQuery/StationScopedQuery`）+ 本人 | 本站 ✓ |
| 班次 | GET `/shifts` | L1 收敛 | 本站 ✓ |
| 排班 | GET `/schedules`、`/schedules/my` | L1 收敛 + 本人 | 本站 ✓ |
| 补卡 | GET `/attendance/makeup/my`、POST `/attendance/makeup` | 本人 | 本人 ✓ |
| KPI | GET `/kpi/scores/ranking`、`/kpi/scores`、`/kpi/scores/{employeeId}` | L1 收敛 + 详情策略（员工限本人/站长限本站 403） | 本站 ✓ |
| 同步任务 | GET `/sync-tasks`、`/{id}`、`/{id}/logs`、POST `/{id}/trigger`、`/{id}/retry` | L1 收敛 + 跨站 404/403 | 本站 ✓ |
| 采集配置 | GET `/sync/overview`、`/sync/configs`、`/sync/configs/{stationId}` | `scopedConfigs()` 收敛 | 本站 ✓（读，写仅 ADMIN：`SyncConfigController.java:58`） |
| 请假 | GET `/leave/list`、详情、`/my` 等、POST `/leave/{id}/station-approve` | `LeaveAccessPolicy`（本站初审、不能审自己） | 本站 ✓ |
| 通知 | GET `/notifications`、`/unread-count`、`/{id}`、PUT `/read-all`、`/{id}/read` | 本人 | 本人 ✓ |
| 工资单 | GET `/finance/payrolls/my`、`/{id}`、`/{id}/logs`、POST `/{id}/confirm`、`/{id}/objection` | 身份收口（非 ADMIN 仅本人且仅已发布态） | 本人 ✓ |
| 日志上报 | POST `/client-logs`（`ClientLogController.java:33`） | 任意登录角色 + `ClientLogSanitizer` 脱敏 | 无业务归属 ✓ |
| 人事/定薪 | GET `/hr/profiles/{employeeId}`、`/hr/salary-structures/{employeeId}` | 非本人非 ADMIN → 403 | 本人 ✓ |

- **本不该由站长操作、但已为 ADMIN-only 的端点（正确，无越权面）**：驿站增删改（`StationController` 全部）、员工增删改/重置密码/导入导出（`EmployeeController` 全部）、部门（`DepartmentController`）、注册审批（`RegistrationController`）、全局看板（`DashboardController`）、计薪规则/设置/运行（`PayrollRule/Setting/RunController`）、KPI 指标配置（`KpiMetricController`）、入离职审批（`HrFlowController`）、采集配置写（`SyncConfigController.java:58`）、通知发布（`NotificationController.java:75`）、派单规则（`WorkOrderController.java:65,72`）、考勤规则写/规则列表（`AttendanceController.java:64,71`）、补卡审批（`AttendanceMakeupController.java:43,57`）、班次增删改（`ShiftController.java:42,49,56`）、排班批量（`ScheduleController.java:50,57`）、工资单生成/审核/发布/发放/明细改（`PayrollController.java:67,74,81,88,95,109,119,127,152`）。
- **残余风险（本次核心）**：上述站长端点在**生产从未被运行时验证**——此前无人持 STATION_ADMIN（F5/F3），全部 STATION_ADMIN 分支仅在 Demo/Mock 与离线单测覆盖。放开创建即把「设计态」授权面投入「运行态」，存在**未验证授权分支被激活**的风险（见 REG-02）。

### 2.2 越权（横向）风险

**结论：既有「按 stationId 收敛」机制完整且方向正确（L1 静默收敛 + L3 资源归属 + 域策略纯函数），静态审查未发现站长跨站读写路径；但站长写/改类端点整体不足，且「未收敛最坏影响」须以运行时回归封堵。**

- **收敛机制（已存在，三层）**：
  - **L1 查询参数收敛**（`QueryDataScopeInterceptor:41-46,60-73`）：非 ADMIN 一律把 `stationId` 强置本人驿站；无归属收敛为哨兵「无数据」（安全失败，不放大范围）；`StationScopedQuery.getStationId()` 读取时叠加。
  - **L2 端点角色门槛**（`RequireRolesInterceptor`，fail-closed 403）。
  - **L3 资源归属校验**（`ResourceAccessChecker.check/isVisible`）：ADMIN 放行；非 ADMIN 归属不符 → 按端点策略 404/403（`DataScopePolicy`，ADR-07 逐端点，不统一）。
  - 域内另加纯函数策略：`WorkOrderAccessPolicy`、`LeaveAccessPolicy`、`ParcelServiceImpl.resolveReadScope()`。
- **不收敛时的最坏影响（假设推演）**：若某站长端点的收敛被漏配，站长 A 可读/改站长 B 站数据（工单指派、包裹取件、考勤导出、排班矩阵、KPI 排行、同步触发），即**跨站横向越权**。现有实现中 `detail/records/export/matrix/ranking` 等均走 L1/域策略，静态未见漏配；但**未运行验证**，此项以回归用例封堵（M-7）。
- **强制收敛建议与验收标准**：见 M-2 / M-7——① 新增任一 STATION_ADMIN 端点必须显式声明收敛源（L1 基类或 L3 标注），否则不允许合入；② 验收：以站长身份对**不同驿站**资源执行读/写，HTTP 404（详情/日志类）或业务码 403/8002/8003/7001（写类），且**不得**返回任何异地字段；无归属站长一律「无数据」。

### 2.3 账号生命周期风险

**结论：初始口令与重置后强制改密在「数据层」具备（`pwd_changed=0`），但「强制」依赖前端闸门，服务端未拦截；管理员/站长自提权路径已封闭（站长无账号管理权限）。**

- **初始口令**：`EmployeeServiceImpl.create:139,147` 以请求体密码 BCrypt 入库、`pwdChanged=0`；导入统一 `hrm.employee-init-password`（`EmployeeServiceImpl:331,343`；配置 `application.yml:79` 占位 `change_me_init_password`）。**新增站长沿用同一路径，策略一致（应保持）**。
- **重置**：`resetPassword:234-250` 仅 ADMIN 可调（类级门槛）、**禁止重置自己**（`SELF_OPERATION_FORBIDDEN`）、重置后 `pwdChanged=0` + `forceOffline`（删全部 Redis 会话 + 撤销受信设备）。**站长不能重置他人密码**（无端点权限）。
- **首登强制改密的实际强度（缺口）**：`pwdChanged` 仅**出参**给前端（`AuthServiceImpl:205`；`EmployeeVO` 同），全仓检索**未见服务端拦截**「`pwd_changed=0` 时限制其他接口」的逻辑（无 filter/interceptor 消费该字段）。∴ 「强制改密」为**前端体验闸门**，服务端未强约束——直连接口可绕过。这是**既有基线**，但站长属高权限账号，初始口令由 ADMIN 设定（可能弱/复用），风险被放大（REG-03）。
- **自提权路径**：站长**不能**给自己或他人改角色（无 `EmployeeController` 权限）；ADMIN 改自己角色被 `SELF_OPERATION_FORBIDDEN` 阻断；最后管理员降级/禁用/删除被 `LAST_ADMIN_PROTECTED` 保护。**未发现提权路径，此项目闭合**。
- **归属约束缺口（新增风险）**：`create` 仅在 `stationId != null` 时校验驿站存在且启用（`EmployeeServiceImpl:134,561-574`），**未强制「STATION_ADMIN 必须归属某启用驿站」**。放开角色后可能建出「无归属站长」→ L1 收敛为无数据（安全失败）但功能异常；需在放开时补强约束（M-2）。

### 2.4 boss-h5 端写操作的风险面

**结论：新增写操作本身可用既有 ADMIN-only 门槛安全承载；主要风险是「仅 UI 隐藏、服务端未拦」的假安全，与端准入口径张力。**

- **攻击面变化**：新增写端点（`POST /api/v1/stations`、`POST/PUT /api/v1/employees`）沿用既有控制器类级 `@RequireRoles({"ADMIN"})`，**服务端已硬拦截**，非仅 UI 隐藏。**前提：不得为其新增角色或加入 `PublicEndpoints`**（后者一旦误加，与 `SEC-FULL-01` 同类，属严重）。
- **枚举/批量**：`GET /stations`、`GET /employees` 已存在（幂等只读）；写接口当前**无限频、无创建上限**——在 ADMIN 权限内主要为误操作/滥用面（REG-06），非越权。
- **端准入张力（须裁定）**：boss-h5 端准入 `boss-allowed-roles=ADMIN`（`application.yml:120`、`api.md:419`）。**若本需求只由 ADMIN 在 boss-h5 维护驿站/账号，则无冲突**；**若意图让站长使用 boss-h5，则必须放宽 `boss-allowed-roles` 并同步 boss-h5 路由 `BOSS_ROLES`——这是一次端准入放宽，属独立安全面，须单独立项评估**（当前需求文本未明确，标「待核实」，见 REG-04）。
- **后端已正确收口**：新增账号的 role 取值须与 `RoleEnum` 一致（`RoleEnum:16-23,31-33`，未知角色按最小权限），前端角色下拉（`hrm-admin/.../employee/index.vue:180-185`）当前**只有 管理员/员工**，与后端白名单一致；放开后**必须同步前端选项**，否则出现「接口能建、UI 不能建」的隐蔽偏差（REG-07）。

### 2.5 审计与留痕

**结论：驿站/账号的增改当前无审计留痕，误建/恶意建号无法事后追溯——这是本次最高风险项。**

- **现状**：全量检索未发现通用操作日志表或审计能力。落地存在的仅 `login_log`（登录）、`leave_log`（请假）、`payroll_log`（工资单）、`wecom_audit_log`（企微，规划中）。**`StationServiceImpl`/`EmployeeServiceImpl` 的 `create/update/changeStatus/delete/resetPassword` 均无业务日志、无操作审计记录**（`StationServiceImpl` 无 logger；`EmployeeServiceImpl` 无写操作 logger 与审计落库）。
- **影响**：ADMIN 误建/恶意建站长号、改他人归属、重置口令等**不可追溯**；无法回答「谁在何时建了哪个站长、改了哪个字段、从旧值到新值」。属 **Repudiation（抵赖）** 面控制缺失（与 `security-auth-review.md:80` 已登记的口径一致）。
- **最小留痕要求（M-1）**：对 `employee`（create/update/status/delete/resetPassword）与 `station`（create/update/status/delete）落**追加型**审计记录：`operator_id`（取 `UserContext.getUserId()`）、`operator_role`、`target_type`、`target_id`、`action`、`changed_fields`（**脱敏**：口令只记「已重置」布尔，绝不记明文/散列）、`client_ip`、`result`、`create_time`；只增不改、应用层禁 UPDATE/DELETE（对齐 `payroll_log` 形态）。

### 2.6 与既有安全基线的冲突（逐条）

| 基线 | 本次变更 | 是否冲突 | 依据 |
| --- | --- | --- | --- |
| 端准入 `boss=ADMIN` | 若站长需用 boss-h5 则放宽 | **条件冲突**（依赖口径，待核实） | `application.yml:120`；`api.md:419`；`boss-h5/router/index.js:9` |
| 端准入 `staff=STAFF,STATION_ADMIN` | 站长开号后即可登员工端 H5 | **无冲突（既有已允许）**，但**激活未验证面** | `application.yml:121`；`api.md:420` |
| 公告白名单（`PUBLISH_TYPES` 1..6，独立于 `SYSTEM_TYPES`） | 无改动 | **无冲突**（发布仍仅 ADMIN） | `NotificationController.java:75`；`NotificationService.java:45-47` |
| fail-closed（未声明角色门槛 → 403；公开端点单一真源） | 新增端点须显式声明 `@RequireRoles`，不得入白名单 | **无冲突（须遵守）** | `RequireRolesInterceptor.java:40-50`；`PublicEndpoints.java:16,57-60` |
| 生产禁用万能验证码 `hrm.sms.dev-universal-code`（非空即启动失败） | 无改动 | **无冲突** | `application.yml:131,158` |
| SQL 参数化 / 写操作鉴权+越权校验 / 敏感字段脱敏 | 新增写沿用既有 Mapper 与脱敏 | **无冲突（须遵守）** | `DesensitizeUtil`；各 Mapper 无 `${}` |
| 「一期接口仅接受 ADMIN/STAFF」（`db.md:234`） | **本次正是要放开该白名单** | **直接冲突（属设计变更）**：须同步文档与契约，并对全部 STATION_ADMIN 端点重新验证 | `EmployeeCreateRequest.java:44-47`；`EmployeeUpdateRequest.java:37`；`db.md:234` |

---

## 三、必做项清单（M-x）

> 严重级：高 / 中。整改要求面向实现角色（不代改），验收标准为可判定项。

| 编号 | 问题 | 风险等级 | 依据（文件:行号） | 整改要求 | 验收标准 |
| --- | --- | --- | --- | --- | --- |
| **M-1** | 账号/驿站增改无审计留痕，不可追溯 | **高** | `service/employee/impl/EmployeeServiceImpl.java:126-152,154-250`；`service/station/impl/StationServiceImpl.java:77-144`；全仓无操作日志表（`docs/db.md:238` 仅 `login_log`） | 新增追加型操作审计（见 2.5 字段），覆盖 employee/station 的 create/update/status/delete/resetPassword；口令只记「已重置」布尔，禁止明文/散列入库；只增不改 | 任一增改产生一条审计记录，含 operator_id + target + action + 脱敏变更；对账可还原「谁在何时改了什么」；无口令明文/散列 |
| **M-2** | 角色放开缺约束：站长归属站未强制、账号管理权限边界未固化 | **高** | `EmployeeCreateRequest.java:44-47`；`EmployeeServiceImpl.java:134,561-574`；`EmployeeController.java:32`；`StationController.java:27` | ① 保持 `EmployeeController`/`StationController` 类级 `@RequireRoles({"ADMIN"})`，**不得**放宽给 STATION_ADMIN；② `STATION_ADMIN` 必须 `stationId != null` 且驿站存在/启用，否则 400；③ 角色白名单放开为 `^(ADMIN\|STATION_ADMIN\|STAFF)$` 且与 `RoleEnum` 一致 | 以 STATION_ADMIN 身份调任意员工/驿站写接口 → 403；建站长缺 `stationId`/停用驿站 → 400；`role` 非法值 → 400 |
| **M-3** | 首登/重置后强制改密仅前端闸门，服务端未强制 | **中** | `EmployeeServiceImpl.java:147,247`；`AuthServiceImpl.java:205`（仅出参）；全仓无 `pwdChanged` 服务端拦截 | 服务端对 `pwd_changed=0` 的账号限制除「改密/登出/读取本人」外的写操作（返回专用码引导改密）；或经用户裁定后明确登记为「已知边界」并加告警 | 新账号/重置后未改密直连写接口 → 被拒（非 200 业务成功）；改密后放行 |
| **M-4** | boss-h5 写操作须服务端硬拦截、端准入不得顺手放宽 | **中** | `boss-h5/router/index.js:9`；`application.yml:120`；`api.md:419`；`PublicEndpoints.java:57-60` | 新增端点显式声明 `@RequireRoles({"ADMIN"})`；**不得**加入 `PublicEndpoints`；boss 端准入维持 `ADMIN`（若确需站长用 boss-h5，另立 P0.5 评估） | 非 ADMIN 直连写端点 → 403；`PublicEndpoints.all()` 无新增路径；未产出端准入变更即视为未放开 |
| **M-5** | 归属校验与健壮性：可选字段清空/驿站停用边界 | **中** | `EmployeeServiceImpl.java:176-189`（`stationId` 可被置空）；`StationServiceImpl.java:124-129`（停用不影响存量归属） | 站长账号禁止被置为「无归属」（UPDATE 亦须校验）；停用驿站后的存量站长给出运营提示或强制重分配 | 将站长 `stationId` 置空 → 400；停用驿站后该站长读写收敛为无数据且无跨站可见 |
| **M-6** | 契约与文档同步（角色白名单放开） | **中** | `docs/db.md:234`；`docs/api.md`（4.3/4.5 及端准入矩阵 419-421）；`EmployeeCreateRequest.java:44-47` | 同步 `db.md`/`api.md` 角色白名单与端点角色列；登记「本次首次激活 STATION_ADMIN 授权面」及范围 | 文档与代码角色集合一致；`api.md` 端点角色列与 `@RequireRoles` 逐条一致 |
| **M-7** | 站长授权面缺少运行时越权回归 | **高** | 见 2.1 端点表；`ResourceAccessChecker.java:27-58`；`QueryDataScopeInterceptor.java:41-46,60-73` | 新增站长越权回归用例（跨站读写 → 404/403；本人端点 → 仅本人）；纳入放行门禁，未过不得放行 | 全套用例通过：站长读/改异地资源一律被拒；无归属站长一律无数据；ADMIN 不受影响 |

---

## 四、发现项清单（REG-xx）

| 编号 | 标题 | 分级 | 位置/依据 | 可利用性 | 影响面 |
| --- | --- | --- | --- | --- | --- |
| **REG-01** | 账号/驿站增改无审计留痕，操作不可追溯（本次最高风险） | **高** | `EmployeeServiceImpl.java:126-152,192-250`；`StationServiceImpl.java:77-144`；无操作日志表 | 非漏洞（控制缺失） | 误建/恶意建站长号、改归属、重置口令无法归因；抵赖风险；合规与取证缺口 |
| **REG-02** | STATION_ADMIN 整套授权面（约 45 端点）首次激活，缺运行时越权验证 | **高** | 2.1 端点表；`RequireRolesInterceptor.java`、`ResourceAccessChecker.java`、各域 `*AccessPolicy` | 理论（依赖漏配，静态未见漏配） | 一旦某端点收敛漏配，出现跨站横向越权；且此前从未在生产以该身份运行 |
| **REG-03** | 首登/重置后强制改密未在服务端强制（仅前端闸门） | **中** | `EmployeeServiceImpl.java:147,247`；`AuthServiceImpl.java:205` | 可利用（直连接口绕过改密） | 高权限站长账号在弱初始口令下长期可用 |
| **REG-04** | 端准入口径张力：站长用哪个端未裁定（boss-h5 仅 ADMIN） | **中** | `application.yml:119-121`；`api.md:419-421`；`boss-h5/router/index.js:9` | 非当前可利用（需求未定） | 若误放宽 boss 端准入，则激活未评估的端+角色组合 |
| **REG-05** | 未强制「STATION_ADMIN 必属启用驿站」 | **中** | `EmployeeServiceImpl.java:134,561-574`；`EmployeeUpdateRequest.java:37` | 非漏洞（配置/数据风险） | 产生无归属站长（安全失败=无数据，但功能异常、可被误判为放行） |
| **REG-06** | 写接口无限频、无创建上限 | **低** | `StationServiceImpl.java:77-89`；`EmployeeServiceImpl.java:126-152` | 权限内滥用/误操作 | 批量建站/建号，污染数据与统计 |
| **REG-07** | 前端角色选项与后端白名单须同步，防隐蔽偏差 | **低** | `hrm-admin/.../employee/index.vue:180-185`；`boss-h5` 站点页（只读骨架，`station.vue:19-23`） | 非漏洞（一致性） | 接口可建、UI 不可建（或反之），出现「假安全」或功能缺口 |
| REG-08 | 自提权/最后管理员保护已闭合（**正向**） | 低 | `EmployeeServiceImpl.java:160-169,198-204,215-223,592-600` | — | 未见站长自升 ADMIN、移除最后管理员路径 |

---

## 五、结论等级与放行前置条件

**结论等级：有条件放行（附 2 项高风险控制缺口，按审批纪律须由主智能体升级用户裁定后方可放行生产）。**

理由：

1. **技术方向正确**：鉴权以 Redis 会话 role 为唯一权威、端点门槛 fail-closed、站长数据范围经 L1/L3/域策略三层收敛，静态审查**未发现站长跨站横向越权、未发现提权路径**；账号/驿站写接口维持 ADMIN-only 即可安全承载 boss-h5 写操作。
2. **但存在 2 项高风险控制缺口（REG-01 审计缺失、REG-02 授权面首次激活无运行时验证）与 1 项中风险（REG-03 强制改密非服务端强制）**，在 M-1/M-2/M-7（及 M-3/M-4）闭环前**不得直接放行生产**。按 `项目规则1.md` 审批纪律，安全判定为高风险时主智能体不得直接放行，须升级用户裁定。
3. 本质：本次变更**不是引入新漏洞**，而是**首次把一条「设计态」授权路径投入运行态**，并叠加既有审计/口令控制短板。风险可管理，但必须前置闭环。

**放行前置条件（缺一不可）**：
- M-1 审计留痕落地并通过验收（覆盖 employee/station 增改，口令不落明文）；
- M-2 约束固化（写接口仍 ADMIN-only；站长强制归属启用驿站；角色白名单与 `RoleEnum` 一致）；
- M-4 服务端硬拦截确认（非 ADMIN 直连写端点 403；未新增公开端点；未改端准入）；
- M-7 站长越权回归用例全通过（跨站读/写被拒、无归属无数据）；
- M-6 契约与文档同步完成。
- M-3、M-5、REG-06/07 属中低项，须在放行前登记处置结论（修复或经用户裁定的已知边界）。

**若判定「阻断」的解除条件**（当前未判阻断，列出以便升级路径清晰）：当出现任一情况——① 需求被裁定为「站长可维护本站员工/驿站账号」，而 `EmployeeServiceImpl` 未加站点级收敛（当前 detail/update/delete/resetPassword 按裸 id 操作，无归属校验）；② boss-h5 写端点被误加入 `PublicEndpoints` 或被放宽给 STATION_ADMIN 而未经 P0.5 评估——则**应阻断**，解除条件为：补齐站点级收敛 + 服务端角色硬拦截 + 越权回归通过。

---

## 六、遗留与未验证项

| # | 事项 | 状态 |
| --- | --- | --- |
| U-1 | 是否已有生产 STATION_ADMIN 账号 | **未连库核实**；迁移脚本 `V1..V21` 无，`sql/seed/kdyzgl_test_seed.sql` 有演示号 |
| U-2 | 运行时越权行为（跨站读写实际返回码） | **未运行验证**，收敛到 M-7 测试阶段 |
| U-3 | boss-h5 是否需站长登录（端准入是否放宽） | **需求口径待确认**（REG-04） |
| U-4 | 依赖 CVE、现网 Nginx/HTTPS 等 | 不在本次范围（见 `security-full-review-20260926.md` 对应项） |

> 本报告仅出安全技术结论与修复建议，**不构成放行决定**；发现的缺口交对应实现角色按 M-x 修复，修复后由本角色复验。C 档授权由主智能体按 `项目规则1.md` §10.3 行使。
