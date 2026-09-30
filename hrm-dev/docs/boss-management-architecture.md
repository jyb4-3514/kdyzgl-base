# 驿站精灵管理能力扩展 · 架构与契约设计方案

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.1（技术评审打回后修订；待重评）** |
| 编写日期 | 2026-09-27 |
| 产出角色 | 架构师 `express-station-architect`（只出**分层 / 模块边界 / 接口契约草案 / 数据库顶层设计 / ADR 决策**；**不写业务实现代码、不写迁移脚本、不改 `api.md`/`db.md` 正文、不改 UI/算法文档、不执行 git/部署/MCP、不连库/不跑测试**） |
| 覆盖需求 | ① 财务管理（并入算薪日设置）② 驿站管理（驿站 CRUD + 站内账号维护，含站长）③ 驿站助手工资单详情去流转状态步骤条 ④ 驿站精灵员工页补「历史工资单」⑤ 排班支持一天多班次 ⑥ 新建「审批中心」 |
| 上游真源（只读精读） | `boss-management-ui-design.md`（921 行，UI/UX）、`algorithm-multi-shift-scheduling.md`（449 行，算法四件套）、`security-station-admin-review.md`（192 行，安全技术结论，**有条件放行**）、`payroll-automation-design.md` v1.5（薪资自动化既有口径）、`api.md` v1.4、`db.md` v2.5（43 表）、`plan.md` v1.0 |
| 关联实现（只读取证） | `controller/employee/EmployeeController`、`controller/station/StationController`、`controller/attendance/ScheduleController`、`controller/finance/PayrollController`、`controller/hr/HrFlowController`、`dto/employee/*`、`service/employee/impl/EmployeeServiceImpl`、`service/station/impl/StationServiceImpl`、`service/hr/impl/HrFlowServiceImpl`、`vo/attendance/*`、`vo/hr/HrFlowVO`、`db/migration/mysql/V1..V21`、`apps/boss-h5/src/stores/todo.js`、`apps/boss-h5/src/constants/todoGroups.js` |
| 状态 | **方案阶段产物**（v1.1，修订自 v1.0 技术评审「打回」）。依 `项目规则1.md` §8 第 8 条与 `智能体调度规则.md` **P0.6 / R25 / L8**，报主智能体审批与交实现角色前**必须先经技术评审工程师（`express-station-tech-reviewer`）评估通过（通过 / 有条件通过）**；结论「打回」退回本架构师按必改项修订后重评。**本文不宣称已通过评审。** |
| 边界 | 只回答「模块边界 / 端承载 / 契约草案 / 表结构顶层设计 / 批次与门禁 / 冲突裁定」；**不含 Java/Vue 代码、不含可执行 SQL、不重定义计费·提成·SLA·考核口径**（须用户确认） |
| 取证声明 | 本机**无 JDK / Maven / MySQL / Redis**，**未编译、未运行、未连库、未做端到端验证**；全部结论标 `文件:行号`，**不确定处标「待裁定 / 待核实」** |
| 凭据红线 | 本文**不含任何真实口令 / 密钥 / 凭据 / 服务器地址**；口令一律只描述「交互 + 接口 + 落点」，示例一律 `******` |

---

## v1.1 修订说明（对技术评审「打回」的闭环）

> 评估绑定 v1.0（2026-09-27，436 行）；本 v1.1 修订依据 `tech-review-boss-management.md`（结论「打回」，8 条必改 M-1~M-8、发现项 F-01~F-16、放行前置 5 条）与主代理裁定 A-②~A-⑥。**归口声明**：M-1/M-4/M-6 归算法工程师与数据库工程师、M-5 归 UI/UX；本文只做**架构侧契合度改写与落点登记**，不代其产出、不重定义其口径。

### 0.1 变更摘要（v1.0 → v1.1）

| # | 变更 | 依据 | 落位 |
| --- | --- | --- | --- |
| V1 | 审计留痕写入点 **9 → 12**：补「员工批量导入（`EmployeeServiceImpl.importEmployees`）」+ 复核发现的间接写入路径「入职流程建档（`HrFlowServiceImpl.createEmployeeForFlow`）」 | 评审 M-3/F-04；本次复核 | §0 摘要、§3.3 |
| V2 | **A-⑤ 首登强制改密 = 服务端拦截** 落位（拦截层 / 白名单 / 错误码口径 / 批次 / 验收） | 评审 M-2/F-02；主代理裁定 A-⑤ | §2.9、§5 B1、§6.1 |
| V3 | 排班唯一性口径改写：**逻辑删（`@TableLogic`）+ 生成列式部分唯一**（沿用 `V16` `phone_active` 手法），删除「物理删」表述 | 评审 M-1；本次实测 | §2.4、§2.8、§6.2/§6.3 |
| V4 | `{saved, removed}` 语义重定义**单列契约条目 `ARCH-C-2b`** | 评审 M-8/F-10 | §2.4.1、§2.8 |
| V5 | 审计表业务时间列命名**引用数据库工程师核对结论**（既有留痕 `payroll_log.time`/`leave_log.time` 均为 `time`，`db.md:535` 例外集许可 `time`），不再自行假设 | 评审 M-7/F-09 | §3.2 |
| V6 | 批次表**入口门禁 / 出口验收分离**；`B7` 拆为「排班结构落地（B7a，可先做）」与「出勤口径呈现（B7b，A-① 裁定后）」 | 评审 F-14/F-16、放行前置 5 | §5 |
| V7 | 主代理裁定落位：**A-②=2**、A-③ 不引入「全天」配对字段、A-④ 覆盖式语义、A-⑤ 服务端强制、A-⑥ 审计范围 | 主代理裁定 | §2.4、§2.9、§6.1 |
| V8 | 行号精度修正：station `create` 由「`:77-144` 段内」改为 **`:77-89`**；全文同类引用自查 | 评审不符项 6 | §3.3 等 |
| V9 | 契约清单 **6 → 8 条变更**（新增 `ARCH-C-2b`、`ARCH-C-7`）；新增端点仍 0；复用仍 8 组 | 本次整改 | §2.8、§8 |

### 0.2 对技术评审 M-1~M-8 的逐条响应表

| 必改项 | 一句话响应 | 本次落位 |
| --- | --- | --- |
| **M-1**（高，算法+数据库） | 架构侧删除「物理删」错误表述；§2.4 唯一性改写为「逻辑删 + 生成列式部分唯一」；键形态与预检最终由数据库工程师 `V22` 定稿 | §2.4、§2.8、§6.2/§6.3 |
| **M-2**（高，架构+后端） | A-⑤ 由「待裁定」改「主代理已裁定 → 已落位」：新增 §2.9 服务端拦截设计（拦截层/白名单/错误码口径），纳入 **B1** 批次并附 4 条验收标准 | §2.9、§5 B1、§6.1 |
| **M-3**（中-高，架构+后端） | 审计写入点补「员工批量导入」，并复核发现「入职流程建档」间接路径；计数 9 → 12 | §3.3、§0 摘要 |
| **M-4**（高，算法） | 归算法「四件套」定稿；架构 §2.4 校验清单登记「同日两班次 `ordinal` 须互异」落点，风险入 R10（不代定码/文案） | §2.4、§6.2 |
| **M-5**（中，UI/UX） | 归 UI/UX「单日上限=2」文案修订（UI 7.4/9.5/⑪#23）；架构 A-② 已按裁定固化 =2 | §2.4、§6.1 |
| **M-6**（中，数据库+算法） | 归数据库工程师；架构 §2.4 预检前置改为「口径与最终唯一键自洽（生成列式仅约束活跃行）」，引用其结论 | §2.4 |
| **M-7**（低-中，架构+数据库） | 审计表业务时间列命名**引用**数据库工程师结论（参照既有 `time` 口径），不自设 | §3.2 |
| **M-8**（低，架构+后端） | `{saved, removed}` 语义重定义单列 `ARCH-C-2b`，标注 `api.md §4.9.3` 同步点与旧客户端兼容影响 | §2.4.1、§2.8 |

---

> **与 UI / 算法文档的关系**：本文**不重复**其界面规范与算法建模，只做**系统契合度评审 + 契约/结构裁决 + 冲突处置**。UI 的 20 条冲突登记逐条处置见 **§7**；算法 §8 的 9 条变更项由本文**确认落位与批次**（见 **§2.4 / §3 / §5**）。

---

## 0. 摘要（结论先行）

1. **口径先行（不改写用户 6 条口径）**：本文严格按用户已确认的 6 项口径设计，**未改动任何一条**；项 5 的「出勤/考核口径变更」按用户要求**单列待裁定**（§6 A-①），**不预设结论**。
2. **端承载收敛**：6 项中 **4 项为纯前端**（③④⑥ + ①），**2 项需后端**（② 角色白名单放开 + ⑥ 的 1 个出参变更），**1 项需迁移**（⑤ `V22` 唯一键）。**新增后端端点 = 0**；**新增/变更契约 = 8 条**（§2.8 汇总；v1.1 增 `ARCH-C-2b`、`ARCH-C-7`）；**迁移 = 2 个**（`V22` 排班唯一键、`V23` 操作审计表）。
3. **站长口径（安全评估阻断条件不成立）**：驿站精灵 boss-h5 仍 **fail-closed 仅 ADMIN**（`boss-h5/router/index.js:9`、`api.md:419`）；账号/驿站写接口**保持类级 `@RequireRoles({"ADMIN"})`**（`EmployeeController.java:32`、`StationController.java:27`）→ **站长不得管理账号**，与用户口径 2 一致；安全评估 `security-station-admin-review.md:179` 的「阻断条件」**不成立**。
4. **安全必做项 M-1 / REG-01（审计留痕）**：账号/驿站增改当前**零留痕**（`security-station-admin-review.md:112`）→ 裁定**新增独立追加型表 `operation_audit_log`（`V23`）**，覆盖 employee/station 的 create/update/status/delete/resetPassword **12 个写入点**（原 9 + 员工批量导入 + 入职流程建档，§3.3）；**口令只记布尔、绝不落明文/散列**（§3）。
5. **排班多班次**：入参 `shiftId` 单值 → **`shiftIds[]` 集合**（保留 `shiftId` 兼容旧客户端），校验「重叠拒绝（半开区间 `[s,e)`）+ 上限 2 + 同日两班 `ordinal` 互异 + 去重幂等 + 整批原子」；保存为**按当天班次集合整体覆盖**（A-④）；`V22` 加「**生成列式部分唯一**」（活跃唯一：`is_deleted=1` 不占唯一性，沿用 `V16` `phone_active` 手法，删除语义为 **逻辑删 `@TableLogic`**）；**`attendance_shift` 本批不补时段字段**（「早/晚」由 `start_time`+`middayBoundaryMinute` 派生、「全天」由班次数派生），UI 的自动配对按钮**维持禁用降级**（§2.4）。
6. **审批中心不新增后端聚合接口**：复用 `stores/todo.js` 6 类并行取数（`todo.js:37-153`）；**唯一后端变更点** = 入离职**列表**出参补 `source`（`hr_flow.source` 列已存在 `V18`，但 `HrFlowVO` 未出参，且 `registration` 子对象仅详情附 `HrFlowServiceImpl.java:112-113`）（§2.7）。
7. **批次 B0–B8（10 批，v1.1 由 B7 拆分）**：每批可独立验收，逐批标注依赖 / 影响端 / 是否迁移 / **入口门禁（开工前必须闭合）** / **出口验收（完成定义）**；消除「未过门禁即可开工」漏洞（§5）。
8. **待用户裁定 1 条**（A-① 出勤/考核口径）；其余 **A-②~A-⑥ 主代理已裁定并落位**（§6.1）；**UI 20 条冲突裁定：执行 14 / 调整 6 / 不在本批 0**（§7）。

---

## 1. 总体设计（模块边界与端承载）

### 1.1 端与准入（不新开口子）

| 端 | 前缀 | 准入（不变） | 依据 |
| --- | --- | --- | --- |
| 驿站精灵 boss-h5 | `/boss/*` | **fail-closed 仅 `ADMIN`** | `boss-h5/src/router/index.js:9`；`api.md:419` |
| 驿站助手 staff-h5 | `/staff/*` | `STAFF` + `STATION_ADMIN` | `api.md:420` |
| PC 管理端 | `/api/v1/*` | 仅 `ADMIN` | `api.md:417` |

> **本方案零端准入变更**：不触碰 `hrm.auth.boss-allowed-roles`（`application.yml:120`）与 `BOSS_ROLES`；不新增公开端点（`PublicEndpoints` 净增 0）。符合安全 M-4（`security-station-admin-review.md:139`）。

### 1.2 6 项需求 → 端承载 / 改造类型矩阵

| # | 需求 | 端承载 | 纯前端 | 需后端 | 需迁移 |
| --- | --- | --- | :-: | :-: | :-: |
| ① | 财务管理（并入算薪日设置） | boss-h5 | ✔ `payrollSettings.vue` / `payrollSettingEdit.vue` / `MeSection.vue` / `router` | — | — |
| ②a | 驿站管理（驿站 CRUD） | boss-h5 | ✔ `station.vue` 重构 + `stationDetail.vue` / `stationForm.vue` + `api/org.js` 封装 | 复用 `StationController`（`StationController.java:27`） | — |
| ②b | 站内账号维护（含站长） | boss-h5 | ✔ `accountForm.vue` + `api/org.js` 封装 | 改 DTO `role` 白名单 + 站长归属校验（**契约 C-1**） | — |
| ③ | 工资单详情去流转状态步骤条 | staff-h5 | ✔ `payrollDetail.vue` | — | — |
| ④ | 员工页补「历史工资单」 | boss-h5 | ✔ `hrDetail.vue` | 复用 `GET /finance/payrolls?employeeId=` | — |
| ⑤ | 排班一天多班次 | boss-h5 + hrm-server | ✔ `schedule.vue` | ✔ `AttendanceScheduleService`（batch/矩阵/我的排班/出参） | ✔ **`V22`**（活跃唯一） |
| ⑥ | 审批中心 | boss-h5 | ✔ `approval.vue` + `router` + `stores/todo.js` 派生 | 1 出参变更（**契约 C-6**：`hr_flow` 列表补 `source`） | — |
| — | 审计留痕（安全 M-1/REG-01，横切必做） | hrm-server | — | ✔ employee/station 写路径 12 写入点 | ✔ **`V23`**（新表） |

**分层与依赖方向（不变）**：`controller → service → mapper → entity`；前端 `views → api → shared`。本方案**不新增模块**、不改变依赖方向；契约变更全部为**只增不改**（新增字段/放宽白名单），保持向后兼容。

### 1.3 设计决策（ADR 内联）

| # | 决策 | 理由（依据） | 备选（否） |
| --- | --- | --- | --- |
| D-1 | 财务管理**原地升级** `payrollSettings.vue`，不新建 hub 页 | 0 新文件/组件、入口收口（`boss-management-ui-design.md:118`） | 新建 `finance.vue` hub（过度设计） |
| D-2 | boss-h5 驿站详情**复用 `GET /stations` 全量**，不新增 `GET /stations/{id}` | 驿站 <100、列表已含详情全字段（`api.md:723-752`）；避免双真源 | 平级加 `GET /stations/{id}`（登记为可选增强 `TODO`，非必需） |
| D-3 | 站内账号列表**复用 `GET /employees?stationId=`**，不新增「按站查账号」出口 | 契约已支持 `stationId`（`api.md:504`），前端一次 `pageSize=100` 内存分角色（UI 4.4.1:291） | 新增专用端点（重复） |
| D-4 | 审批中心**不新增后端聚合接口**，复用 `stores/todo.js` | 单 store = 单一数据源，避免「审批中心说 3、消息页说 5」漂移（`todo.js:15-25`） | 新增 `/approvals/summary`（第二套口径） |
| D-5 | 排班多班次入参**以 `shiftIds[]` 为准、保留 `shiftId` 兼容** | 旧客户端行为（单值覆盖）与新语义（集合差量）严格等价于单元素（`ScheduleBatchRequest.java:27`） | 直接改 `shiftId` 为数组（破坏旧客户端） |
| D-6 | **`attendance_shift` 不补时段字段** | 「早/晚」由 `start_time`+`middayBoundaryMinute` 派生（`algorithm-multi-shift-scheduling.md:113`）；「全天」=班次数派生（`:99-106`）；补字段 = 双真源 + 迁移扩大 | 补 `period_type`/`pair_id`（登记 `TODO`） |
| D-7 | 审计留痕**新立独立表**，不并入 `payroll_log` | `payroll_log.payroll_id NOT NULL`，结构上无法承载无工资单事件（`db.md:1038-1039`）；对齐 `station_payroll_setting_log` 先例 | 复用 `payroll_log` / 复用系统日志（混淆归属） |
| D-8 | 审计表**独立迁移 `V23`**，与 `V22`（排班唯一键）解耦 | 两变更不同域、不同批次（B1 / B7），解耦便于分别授权与回滚 | 合并 `V22`（批次耦合、回滚粒度粗） |
| D-9 | 迁移编号**接续 `V22`**（当前最高 `V21__payroll_log_locator.sql`） | 算法 §8 已声明 `V22` 起（`algorithm-multi-shift-scheduling.md:406`）；**PG 目录冻结、不改历史脚本** | — |
| D-10 | 首登强制改密**在服务端拦截**（`pwd_changed=0` 未改密前拒访业务接口），非仅前端提示 | 主代理裁定 A-⑤；站长属高权限账号，前端闸门可被直连接口绕过（`AuthServiceImpl.java:779` 注释「pwd_changed=0 时前端强制进入改密流程」= 仅出参/前端） | 仅前端提示（可绕过，安全 REG-03，`security-station-admin-review.md:95,138`） |

### 1.4 非功能需求（NFR）与本次影响

| 维度 | 结论 |
| --- | --- |
| 性能 | 排班写路径「单值 update」→「集合差量」（算法 §4.1，同阶）；取排班「1 行 → ≤2 行、同索引」；计薪批量**不变**（本就按班次单元，`ShiftPayrollPolicy.java:54-56`） |
| 安全 | 角色放开**首次激活** `STATION_ADMIN` 审批面（`security-station-admin-review.md:41` F5，约 45 端点"休眠"）；**写接口保持 ADMIN-only**，风险面被隔离在「读/本人」侧；**审计留痕**（M-1）与**越权回归**（M-7）为本方案放行前置 |
| 可用性 | 无新增外部依赖、无新增定时任务；审批中心逐组独立降级（`todo.js:181-197`） |
| 一致性 | `V22` 「活跃唯一」DB 防线以**生成列式部分唯一**承载（`is_deleted=1` 置 NULL 不占唯一性，与既有 `@TableLogic` 逻辑删自洽；沿用 `V16` `phone_active` 手法）；`V23` 追加型（只增不改，对齐 `payroll_log`，`db.md:987`） |
| 可维护性 | 零新增 Token/色值；零新增后端聚合接口；契约只增不减 |

---

## 2. 接口变更清单草案（供后端补录 `api.md`）

> 编号 `ARCH-C-x`（契约）/ `ARCH-S-x`（结构）。**本文不改 `api.md`/`db.md` 正文**，仅给可转录的草案条目。分类统计见 **§2.8**。

### 2.1 角色白名单放开（ARCH-C-1）— 对应 UI 冲突 #2 / #16

**真源在 DTO 校验，非文档**：现 `role` 仅 `^(ADMIN|STAFF)$`（`EmployeeCreateRequest.java:44-47`、`EmployeeUpdateRequest.java:37`；文档记载见 `db.md:204`、`db.md:233`）。

| 项 | 草案 |
| --- | --- |
| 变更点 | `role` 白名单 `^(ADMIN|STAFF)$` → **`^(ADMIN|STATION_ADMIN|STAFF)$`**（新增/编辑两处 DTO 同步），取值须与 `RoleEnum` 一致（安全 M-2③） |
| 必填字段 | 同既有：`username` / `password`(仅新增) / `realName` / `phone` / `role` 必填（`api.md:551-563`） |
| `stationId` 条件必填 | **`role=STATION_ADMIN` → `stationId` 必填**，且驿站存在、未删除、启用（否则 `4001`/`4004`/`400` 文案「站长必须归属启用驿站」）；`ADMIN`/`STAFF` 维持可选（`api.md:559`） |
| 校验规则 | 自我保护 `2001` / 最后管理员保护 `2002` **不变**；「最后管理员」计数口径仍为 `role='ADMIN' AND status=1`，**不把 `STATION_ADMIN` 计入**；`UPDATE` 亦禁止把 `STATION_ADMIN` 的 `stationId` 置空（安全 M-5，`EmployeeServiceImpl.java:176-189`） |
| 错误码 | 建议复用 `400` + 文案；或新增 `2004`「站长必须归属启用驿站」（**由后端按 `api.md` 定稿，架构不定码**） |
| 契约同步点 | `api.md §4.3.3 / §4.3.4`（role 行）；`api.md §4.1.5` 端准入矩阵**不变**（boss 仍 `ADMIN`、staff 已含 `STATION_ADMIN`，`api.md:419-420`）；`db.md:204`/`:233` 角色注释同步（安全 M-6） |
| 影响 | **首次激活** `STATION_ADMIN` 整套授权面 → 必须配套越权回归（M-7，§4.3） |

> **不做的**：**不**放宽 `EmployeeController`/`StationController` 类级角色（保持 `{"ADMIN"}`，`EmployeeController.java:32`、`StationController.java:27`）；**不**改端准入。

### 2.2 按驿站维护账号（复用为主）

| 能力 | 结论 | 依据 |
| --- | --- | --- |
| 按 `stationId` 查员工列表 | **复用** `GET /employees?stationId=`（**不新增**） | `api.md:504`（`stationId` 已支持）；`EmployeeController.java:41-44` |
| 列表分角色（员工/站长区块） | **前端内存分组**（`role=STAFF` / `role=STATION_ADMIN`），单次 `pageSize=100` 拉全站 | UI 4.4.1（`boss-management-ui-design.md:289-292`） |
| 账号新增（含初始口令） | **复用** `POST /employees`（`password` 必填 8–20 位含字母数字，`EmployeeServiceImpl.java:139`、`api.md:554`） | — |
| 账号编辑 | **复用** `PUT /employees/{id}`（**无 `username`/`password`**，`api.md:581`；`EmployeeUpdateRequest.java`） | — |
| 启停 | **复用** `PUT /employees/{id}/status`（`api.md:595`；停用即强制下线 `EmployeeServiceImpl.java:228-230`） | — |
| 重置口令 | **复用** `PUT /employees/{id}/password/reset`（`newPassword` 8–20 位；`pwd_changed=0` + 强制下线；`api.md:605-611`） | 重置后 `pwd_changed=0`，**服务端强制改密拦截见 §2.9**（A-⑤） |

**口令交互与接口口径（两页面统一，全程不落明文）**

| 场景 | 接口 | 口径 |
| --- | --- | --- |
| 新增账号·设置初始口令 | `POST /employees`（`password`） | 管理员表单内设置；提交 toast「账号已创建，首次登录须修改口令」；**不回显、不提供复制、不写日志/备注**（UI 4.5:317-321） |
| 编辑账号 | `PUT /employees/{id}` | **无口令字段**；改口令走「重置口令」 |
| 重置口令 | `PUT /employees/{id}/password/reset` | 二次确认（`irreversible`）+ 弹层输新口令（`maxlength=20`，**不回显明文**）；`2001`（自己）→ 引导走「我的 → 修改密码」（`api.md:611`） |
| 服务端行为 | 复用既有 | BCrypt 入库（`EmployeeServiceImpl.java:139,246`）；`pwd_changed=0` 首登强制改密（**服务端拦截落位见 §2.9 `ARCH-C-7`**）；重置后 `forceOffline`（`EmployeeServiceImpl.java:247-249`） |

> **凭据红线**：架构层**不定义**初始口令值、**不下发**任何凭据；口令由主智能体按最小必要托管，角色不得查找/猜测/从文档收集（`项目规则1.md` §7.1）。

### 2.3 驿站管理（复用 `StationController`，核对缺口）

| 能力 | 结论 | 依据 |
| --- | --- | --- |
| 驿站列表 | **复用** `GET /stations`（ADMIN，全量不分页） | `StationController.java:36-39`、`api.md:723-729` |
| 新增驿站 | **复用** `POST /stations`（`code`/`stationName` 必填；**不传 `status`**，服务端固定 `1`） | `StationController.java:42-45`、`api.md:756-769` |
| 编辑驿站 | **复用** `PUT /stations/{id}` | `StationController.java:48-52`、`api.md:775-777` |
| 启停 | **复用** `PUT /stations/{id}/status` | `StationController.java:55-59`、`api.md:781-787` |
| 删除 | **复用** `DELETE /stations/{id}`（有员工 `4003`） | `StationController.java:62-66`、`api.md:791` |
| **按 ID 取驿站详情** | **缺口（已核实）**：`StationService` 仅有 `list(Integer)`，**无 `detail`/`GET /{id}`**（`StationService.java:15`） | **裁定 D-2：boss-h5 详情页复用 `GET /stations` 内存按 id 定位，不新增端点**；如需便捷可平级加 `GET /stations/{id}`，登记 `TODO(扩展)`，非本批必需 |

### 2.4 排班多班次（ARCH-C-2/3/4/5 + ARCH-S-1）

**入参变更（`POST /api/v1/schedules/batch`，`ScheduleBatchRequest.Item`）**

| 项 | 现（`api.md:1130-1138`） | 草案 |
| --- | --- | --- |
| 字段 | `shiftId`（单值；缺省/空=清空该天） | **新增 `shiftIds: Long[]`**；`shiftId` **保留但标注弃用**（兼容旧客户端） |
| 语义优先级 | — | `shiftIds` **存在即为准**（含空数组）；`shiftIds=[]` → 清空该天；仅传 `shiftId` → 单元素集合；两者皆缺 → 清空（沿用现行）。**该天目标集合整体覆盖当前集合**（非累加，主代理裁定 **A-④**） |
| 保存算法 | 单值覆盖 | **集合差量实现「覆盖式」语义**（`ins = T\C`、`del = C\T`、`T∩C` no-op；语义 = 按「当天班次集合」整体覆盖），整批 `@Transactional` 原子（算法 §4.1） |
| 校验（违反即 `400`/`91xx`） | `shiftId` 存在、同驿站、`status=1`（`9106`）；`items ≤ 200` | 逐班次：存在/同驿站/启用（`9106`）；**时间重叠拒绝**（`allowShiftOverlap=false`，半开区间 `[s,e)`）；**单日上限 `maxShiftsPerDay = 2`**（主代理裁定 **A-②**）；**同日两班次 `ordinal` 须互异**（评审 **M-4**：避免同半天双班致计薪序号去重少算；具体约束与错误码由算法四件套定稿，架构仅登记落点）；**重复班次去重幂等**（`IDEMPOTENT`）；**整批原子不落半成功** |
| 员工归属 | `employeeId` 须属该驿站 | 不变（`api.md:1134`） |
| 响应 | `{ saved, removed }` | **语义重定义见 §2.4.1 `ARCH-C-2b`**：覆盖式语义下 `saved`/`removed` = **班次行数**（`algorithm-multi-shift-scheduling.md:240` §8-3），非旧「新增/改派条数、清空条数」（`api.md:1138`）；**最终值由后端定稿** |

**出参变更（只增不减，向后兼容）**

| 端点 | 现 | 草案 |
| --- | --- | --- |
| `GET /schedules`（`api.md:1114`） | `DayCell{workDate, scheduleId, shiftId}`（单值） | 新增 `shiftIds: Long[]`；`shiftId` 保留 = 首条（兼容）；`scheduleId` 保留 = 首条 |
| `GET /schedules/my`（`api.md:1124`） | 单日 `{scheduleId, shiftId, shiftName, startTime, endTime, color, restMinutes}` | 新增 `shifts: [{scheduleId, shiftId, shiftName, startTime, endTime, color, restMinutes}]`；旧字段保留 = 首条 |

**迁移 `V22`（ARCH-S-1）必要性论证**

| 项 | 结论 |
| --- | --- |
| 唯一键 | **必须加「活跃唯一」约束**，形态为**生成列式部分唯一**（沿用 `V16` `phone_active` 手法：`V16__employee_phone_unique.sql:35-39`、`db.md:215,226`）：以 `is_deleted` 派生的生成列承载 `(employee_id, work_date, shift_id)` 唯一，`is_deleted=1` 行置 NULL、**不占唯一性**。现状 `attendance_schedule` **仅两个普通索引、无唯一约束**（`db.md:634-642`、`V5__attendance.sql:78-79`），Service 查重键为 `(employeeId, workDate)`（会**覆盖式吞掉第二班次**）→ 多班次下必须改键，DB 约束为「活跃唯一」最终防线 + 并发防线。**删除语义 = 逻辑删（`@TableLogic`）**：`AttendanceSchedule.java:39-40` 标 `@TableLogic`、`AttendanceScheduleMapper.java:9` 为裸 `BaseMapper` ⇒ `deleteById` 实为 `UPDATE is_deleted=1`，**非物理删**（v1.0 表述错误，v1.1 更正）；故**裸三元 `UNIQUE` 与逻辑删互斥**（残留软删行会致 1062），必须用生成列式（或 `is_deleted` 入键）自洽。**生成列表达式 / 单列或多列形态由数据库工程师在 `V22` 定稿**（评审 M-1） |
| 是否删冗余普通索引 | `idx_attendance_schedule_emp_date (employee_id, work_date)` 是新键的**最左前缀** → **建议评估删除**（交数据库工程师，算法 §1.2:70） |
| `attendance_shift` 补时段字段 | **否（本批）**：见决策 D-6。理由：①「早/晚」序号由 `start_time`+`middayBoundaryMinute` 派生（`algorithm-multi-shift-scheduling.md:113`）；②「全天」由班次数派生（`:99-106`）；③ 补 `period_type`/`pair_id` 会造双真源 + 扩大迁移面 |
| 迁移前置 | **存量重复行查重预检（须返回 0 行）+ 备份 + C 档三步授权**（算法 §8-1 / R3，`algorithm-multi-shift-scheduling.md:384,396`）。**预检口径须与最终唯一键形态自洽**：采用生成列式部分唯一（仅活跃行参与）时，预检按 **`is_deleted=0` 活跃行** 分组查重即可；若数据库工程师定稿为含 `is_deleted` 的其它形态，预检须相应含软删行（评审 M-6）。**以数据库工程师 `V22` 定稿与预检结论为准**。同步刷新 `sql/schema/mysql/init.sql` 快照；**PG 目录冻结、不改历史脚本** |
| 编号 | `V22__attendance_schedule_multi_shift.sql`（当前最高 `V21`，`algorithm-multi-shift-scheduling.md:406`） |

**错误码（ARCH-C-5）**：重叠拒绝 / 超上限拒绝的码由**后端按 `api.md §2.2`「91xx」段定稿**（建议 `400`+文案 或 新增 `9110`/`9111`；`api.md:89,141`）。**架构与算法均不自定码**（算法 §8-9:404）。

#### 2.4.1 `{saved, removed}` 语义重定义（ARCH-C-2b，评审 M-8 单列）

> v1.0 仅在本节内「顺带」改写响应语义，未单列契约条目（评审 F-10）→ v1.1 单列 `ARCH-C-2b`，防 `api.md` 更新遗漏。

| 项 | 内容 |
| --- | --- |
| 现状真源 | `api.md:1138`：`saved` = 新增/改派条数、`removed` = 清空条数 |
| v1.1 新语义 | 保存为「当天班次集合整体覆盖」（A-④）后：`saved` = 覆盖后该天/该批**班次行数**（`\|T\|` 口径，含未变化的 `T∩C`）；`removed` = 本次被差量移除的**班次行数**（`\|C\T\|`）。**定义域由「条数」升级为「班次行数」**，与 `shiftIds[]` 集合语义同构（`algorithm-multi-shift-scheduling.md:240` §8-3 建议值） |
| 旧客户端兼容影响 | 旧客户端仅传单值 `shiftId`（单元素集合）：`saved∈{0,1}`、`removed∈{0,1}`，与旧「新增 1 / 清空 1」**可观察等价**；多班次下旧客户端不产生多值。**无字段增删，属语义收窄，不破坏结构兼容** |
| 返回口径约束 | 计数单位为**班次行数**（非「天」）；整批原子，`removed` 仅统计被差量逻辑删的行；**`saved + removed` 不等于差量总数**（`T∩C` no-op 计入 `saved` 但不计入 `removed`）——**该口径须在 `api.md` 显式写明**，避免前后端对账歧义 |
| 同步点 | `api.md §4.9.3`（`POST /schedules/batch` 响应说明）；**不新增字段**；值口径最终由后端定稿 |

**出勤/考核口径变更（**不在本批实现，待用户裁定**）**：应到/缺卡由「人数去重」改「班次粒度」（`AttendanceSummaryPolicy.java:35-59`，算法 §8-4:399）→ 见 **§6 A-①**。

### 2.5 驿站助手工资单明细（③：**后端无需变更**）

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 去流转状态步骤条 | **纯前端**（staff-h5 `payrollDetail.vue`），删除 `PayrollStatusSteps` 区块与 `steps` computed | UI ⑤（`boss-management-ui-design.md:342-371`） |
| 保留确认/提异议 | 复用 `POST /finance/payrolls/{id}/confirm`、`/objection`（**不改**） | `PayrollController.java:46`（越权口径）；`api.md:1596-1622` |
| 「计算说明」收敛 | 纯前端展示层 | UI ⑤.1:369 |
| 后端契约 | **零变更** | — |

### 2.6 历史工资单（④：**复用，不新增**）

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 按员工查工资单 | **复用** `GET /finance/payrolls?employeeId=&pageNum=&pageSize=`（ADMIN） | `api.md:1513-1517`；`PayrollQuery.java:22`（`employeeId` 已存在）；`PayrollController`（ADMIN 列表） |
| 下钻详情 | **复用** `GET /finance/payrolls/{id}`，进既有的 `/boss/payroll/:id` | `api.md:1525-1538`；UI ⑥.3:436 |
| 前端封装 | `getPayrolls` 已支持任意 query 透传 → **无需改签名**，仅调用侧传 `employeeId` | UI ⑪#21:830 |
| 后端契约 | **零变更** | — |

### 2.7 审批中心（⑥：**不新增聚合接口 + 1 出参变更 ARCH-C-6**）

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 取数 | **不新增后端聚合接口**；复用 `stores/todo.js` 的 6 类并行取数（5 类白名单 + `orders` 排除） | `todo.js:37-153`；UI ⑧.6:620-626 |
| 记账单异议 | 复用 `getPayrolls(status='OBJECTED')`（**已实现**） | `todoGroups.js:29-33` |
| **注册标识数据源（ARCH-C-6，唯一后端变更点）** | `hr_flow.source` 列**已存在**（`V18__hr_flow_source.sql`；取值 `ADMIN` / `SELF_REGISTER`，`HrConstants.java:35,37`），但 **`HrFlowVO` 未出参 `source`**（`vo/hr/HrFlowVO.java` 无该字段），且 `registration` 子对象**仅详情附、列表不附**（`HrFlowServiceImpl.java:112-113`，注释「避免列表 N+1」）→ **需在列表出参补 `source`** |
| 草案 | `HrFlowVO` 新增出参 **`source`**（枚举 `ADMIN`/`SELF_REGISTER`），`toFlowVO` 直出；**列表与详情共用 VO**。契约同步点：`api.md §4.11.5 R-7~R-9`（既有端点扩展）/ §4.8 入离职章节。**不新增端点、不改 `registration` 子对象的「仅详情」策略** |
| 降级 | 字段未落地前：审批中心**不显示「注册」胶囊**（不臆造来源），UI 已给降级（`boss-management-ui-design.md:604-605`） | — |
| Mock 缺口 | Mock 无 registration 路由/种子，`POST /registration` 在 Mock 不可达（UI ⑫#3:872）→ **登记为实现批（测试）项，非契约** | — |

### 2.8 契约变更清单汇总（统计）

| 类型 | 条数 | 明细 |
| --- | :-: | --- |
| **新增端点** | **0** | —（审批中心不聚合；驿站/账号/历史工资单/口令全部复用） |
| **变更（需补录 `api.md`）** | **8** | `ARCH-C-1` 角色白名单放开+站长归属；`ARCH-C-2` 排班入参 `shiftIds[]`；**`ARCH-C-2b` 排班响应 `{saved,removed}` 语义重定义（v1.1 新增 / M-8）**；`ARCH-C-3` 矩阵出参 `shiftIds[]`；`ARCH-C-4` 我的排班出参 `shifts[]`；`ARCH-C-5` 排班错误码定稿；`ARCH-C-6` `hr_flow` 列表补 `source`；**`ARCH-C-7` 首登未改密拒访业务接口（错误码 / 全局行为，v1.1 新增 / A-⑤ / M-2，§2.9）** |
| **复用（仅登记，不改契约）** | **8 组** | 员工列表 `stationId` / 员工 CRUD+重置口令 / 驿站 CRUD+状态 / 工资单列表 `employeeId` / 工资单详情 / 确认·异议 / `payroll-settings` 组 / `stores/todo.js` 6 类取数 |
| **结构变更（`db.md` / 迁移）** | **2** | `ARCH-S-1` `V22` 排班「活跃唯一」（**生成列式部分唯一**；+评估删冗余索引）；`ARCH-S-2` `V23` `operation_audit_log` 新表（§3） |

> **v1.1 变更**：变更条目 **6 → 8**（+`ARCH-C-2b`、+`ARCH-C-7`）；新增端点仍 **0**；复用仍 **8 组**；结构变更仍 **2**。`ARCH-C-7` 不新增端点，但改变既有端点的**失败语义**（未改密前一律拒访），故计入「变更」。

> **文档同步项（M-6）**：`db.md:204/:233` 角色注释、`api.md §4.3.x / §4.9.x / §4.11.x`、端准入矩阵说明。由实现角色按本草案转录，**本文不代改**。

### 2.9 首登强制改密 = 服务端拦截（ARCH-C-7，主代理裁定 A-⑤ / 评审 M-2）

> **现状**：`pwd_changed` 仅作出参，前端据此进改密流程（`AuthServiceImpl.java:779` 注释「pwd_changed=0 时前端强制进入改密流程」）；**服务端无任何拦截**，高权限站长账号在弱初始口令下可直连接口绕过（安全 REG-03，`security-station-admin-review.md:95,138`）。主代理裁定 **A-⑤ = 服务端强制**。

| 项 | 设计（架构层，不含代码） |
| --- | --- |
| 判定依据 | 登录会话中的 `pwd_changed`（`employee.pwd_changed`，`Employee.java:59`；新增 / 导入 / 重置均置 0：`EmployeeServiceImpl.java:147,247,343`）。**取值口径由后端定稿**（建议随 `SessionInfo` 在登录时写入 Redis 会话，避免每请求回表；`JwtAuthFilter` 已持会话并注入 `UserContext`，`JwtAuthFilter.java:123-134`） |
| 拦截层 | **新增 HandlerInterceptor**（如 `PwdChangedInterceptor`），在 `WebConfig.addInterceptors` 注册（`WebConfig.java:29-32`），**须晚于 `JwtAuthFilter` 注入 `UserContext`**；与 `RequireRolesInterceptor` / `QueryDataScopeInterceptor` 同链，**先于业务处理**（`WebConfig.java:11-14` 链序说明） |
| 放行白名单（改密 / 登出 / 读本人 + 公开端点） | ① `PublicEndpoints` 全量（`PublicEndpoints.java:57-60`：登录 / 短信 / 设备验证 / 验证码 / 注册 / 预留派单）；② `PUT /api/v1/auth/password`（改本人密码，`AuthController.java:85`）；③ `POST /api/v1/auth/logout`（`AuthController.java:70`）；④ `GET /api/v1/auth/me`（读本人，供前端识别 `pwdChanged` 并跳改密，`AuthController.java:78`）。**除白名单外的一切业务接口，`pwd_changed=0` 一律拒访**（fail-closed） |
| 错误码口径 | 建议**复用 `403`** 或按 `api.md §2.2` 在**认证段（11xx，`ErrorCode` 既有 `1108` 同段）**新增专用码（如「首次登录须先修改口令」）——**架构不自定码，由后端按 `api.md` 定稿**（同 §2.4 错误码口径原则） |
| 改密后行为 | `PUT /auth/password` 成功后 `pwd_changed=1`；若沿用会话内标记，须**同步刷新会话**（或强制重登一次），避免旧标记残留导致「已改密仍被拒」。**交后端定稿**，登记 §6.3 `T10` |
| 与审计关系 | `RESET_PASSWORD`（管理员重置）已入审计（§3.3）；**用户自助首登改密不在 A-⑥ 范围**（A-⑥ 覆盖管理员发起的「驿站与账号的增/改/启停/删/重置口令」） |
| 批次 | **B1**（与账号角色放开、`V23` 同批；面向 B1 首次激活的高权限 `STATION_ADMIN`） |
| 验收标准 | ① 以 `pwd_changed=0` 账号持有效 Token 直连**任一非白名单业务接口**（如 `GET /employees`）→ 被服务端拒绝（403 或专用码），**不返回业务数据**；② 白名单四类可正常访问；③ 改密成功后同一会话可正常访问业务接口；④ 前端提示不再作为唯一闸门（`AuthServiceImpl.java:779` 注释须同步更新为「服务端强制」） |

---

## 3. 审计留痕设计（安全必做项 M-1 / REG-01）

> 依据：`security-station-admin-review.md:112-114`（现状零留痕）、`:150` REG-01（**高**）、`:136` M-1。形态对齐既有追加型留痕 `payroll_log`（`db.md:950-988`）与 `station_payroll_setting_log`（`db.md:1038-1039`）。

### 3.1 方案选择（决策 D-7）

| 方案 | 取舍 |
| --- | --- |
| **A（取）新增通用追加型表 `operation_audit_log`** | 可覆盖 employee/station 的 **5 + 2 + 4 = 11 写动作**（employee 5 + 导入/建档 2 + station 4，§3.3）；与既有表解耦；只增不改；口令可「只记布尔」 |
| B 复用 `payroll_log` | **否**：`payroll_id NOT NULL`，结构上无法承载无工资单事件（`db.md:1038-1039`） |
| C 复用系统日志 | **否**：混淆审计归属、不落库不可查询 |

### 3.2 表设计顶层（`ARCH-S-2` / `V23__operation_audit_log.sql`）

> **顶层设计，非 DDL**；字段类型与命名交数据库工程师定稿（P4 结构先行）。

| 字段 | 含义 | 备注 |
| --- | --- | --- |
| `id` | 主键 | BIGINT AI |
| `operator_id` | 操作人 | 取 `UserContext.getUserId()`（逻辑外键 `employee.id`） |
| `operator_name` / `operator_role` | 操作人快照 | — |
| `operator_type` | 操作主体 | `USER` / `SYSTEM`（对齐 `payroll_log.operator_type`） |
| `target_type` | 目标类型 | `EMPLOYEE` / `STATION` |
| `target_id` | 目标 ID | 逻辑外键 |
| `target_name` | 目标名称快照 | 员工姓名 / 驿站名（评审 **N-3** 增补，与 `V23` DDL 对齐） |
| `action` | 动作 | `CREATE` / `UPDATE` / **`CHANGE_STATUS`** / `DELETE` / `RESET_PASSWORD`（**与 `V23` DDL 与 `db.md` 统一定名 `CHANGE_STATUS`**，评审 **N-2**） |
| `before` / `after` | 变更快照（JSON） | 低频读取；**口令只落布尔标记**，见 §3.4 |
| `changed_fields` | 变更字段白名单（JSON） | 只列发生变化的字段名 |
| `client_ip` | 客户端 IP | Nginx 透传 `X-Forwarded-For` 首个 |
| `result` | 结果 | `SUCCESS` / `FAIL`（+ `fail_reason` 可选） |
| `time` | 操作时间 | **业务时间列命名对齐既有追加型留痕口径「`time`」**（评审 **M-7**）：`payroll_log.time`（`db.md:969`）、`leave_log.time`（`db.md:506`）、`client_log.time`（`db.md:511`）均为 `time`，且 `db.md:535` 例外集许可 `time`；**只插不改；无 `update_time` / `is_deleted`**（对齐 `db.md:987`）。**最终命名以数据库工程师核对 `leave_log`/`payroll_log` 的结论定稿**（v1.0 用 `create_time`，v1.1 改为引用其结论，不再自行假设） |

**索引（顶层建议）**：

| 索引名（建议） | 字段 | 用途 |
| --- | --- | --- |
| `idx_operation_audit_target` | `(target_type, target_id, time)` | 按对象取留痕（「谁在何时改了哪个站长」） |
| `idx_operation_audit_operator` | `(operator_id, time)` | 按操作人检索 |
| `idx_operation_audit_action_time` | `(action, time)` | 按动作类型/时段审计 |

### 3.3 写入点（12 处，覆盖 M-1 / M-3 要求）

| # | 目标 | 写入点（实现位置） | 动作 |
| :-: | --- | --- | --- |
| 1 | employee | `EmployeeServiceImpl.create`（`:126-152`） | `CREATE` |
| 2 | employee | `EmployeeServiceImpl.update`（`:154-190`） | `UPDATE` |
| 3 | employee | `EmployeeServiceImpl.changeStatus`（`:209-231`） | `CHANGE_STATUS` |
| 4 | employee | `EmployeeServiceImpl.delete`（`:192-207`） | `DELETE`（逻辑删） |
| 5 | employee | `EmployeeServiceImpl.resetPassword`（`:233-250`） | `RESET_PASSWORD` |
| 6 | employee（**批量**） | `EmployeeServiceImpl.importEmployees`（`:285-354`，插入在 `:346`）：**批量 CREATE** | `CREATE`（**口径：每批记 1 条汇总**，`after` 记 `{"count":N,"role":"STAFF"}`；是否按行记明细由后端定稿） |
| 7 | employee（**间接**） | `HrFlowServiceImpl.createEmployeeForFlow`（`:364-409`，插入在 `:409`）：入职流程建档 | `CREATE`（`target_id` = 新建员工 id；`after` 可带 `flowNo`） |
| 7b | employee（**间接**） | `HrFlowServiceImpl.assignForFlow`（`:484`）：审批通过时回填/变更账号的 `stationId` / `position` / `role` | `UPDATE`（评审 **N-1** 补入；属 A-⑥「账号的改」范围） |
| 8 | station | `StationServiceImpl.create`（`:77-89`） | `CREATE` |
| 9 | station | `StationServiceImpl.update`（`:92-113`） | `UPDATE` |
| 10 | station | `StationServiceImpl.changeStatus`（`:116-129`） | `CHANGE_STATUS` |
| 11 | station | `StationServiceImpl.delete`（`:132-144`） | `DELETE`（逻辑删） |

> **v1.1 变更（9 → 12）**：① 新增「员工批量导入」（评审 **M-3**/F-04，`EmployeeServiceImpl.java:346`，与 `create` 同类但独立入口，原清单漏列）；② **本次复核新发现**的间接写入路径「入职流程建档」（`HrFlowServiceImpl.java:409`）——该路径**开户即账号创建**，属 A-⑥「账号的增」范围，原清单与该路径同为 employee 侧写操作，纳入后 employee 侧 = 8 项（含 `assignForFlow`，评审 **N-1**）。**写入方式**（AOP 切面 / Service 内显式）由后端定稿；架构只约束**落点、字段、只增不改、事务一致**（与业务写同事务或失败可补偿，二选一由后端定稿，登记 `T9`）。**要求：任一增改产生一条审计记录，可还原「谁在何时改了什么」。**
>
> **复核残留（低，登记）**：`HrFlowServiceImpl.updateEmployeeStatus`（`:948-959`）在 `changeStatus`（已覆盖）之外对 `remark` 做二次 `updateById`（`:954-957`）——属非状态变更；若切面仅埋点于 `changeStatus`，该 `remark` 更新**无独立留痕**。建议随 `changeStatus` 审计合并说明或单独留痕，**交后端定稿**（登记 `T11`）。
>
> **未纳入（说明边界）**：`RegistrationServiceImpl`（注册申请）不直接写 employee（`employeeMapper.insert` 全仓仅 3 处：`EmployeeServiceImpl.java:150/346`、`HrFlowServiceImpl.java:409`，本次已全部覆盖）；注册 → 审批 → 建档经由 `HrFlowServiceImpl.createEmployeeForFlow`，故第 7 项即覆盖之。`StationServiceImpl.list` 的 `countEmployeesByStation` 为只读，不入清单。

### 3.4 口令脱敏硬约束（M-1 验收）

- `RESET_PASSWORD` / `CREATE`（含初始口令）的 `before`/`after`/`changed_fields` **不得**出现明文或 BCrypt 散列；
- 口径：`{"password":"SET"}`（新增设置）/ `{"password":"RESET"}`（重置）**布尔标记**；
- 与既有 `payroll_log` 的 `before/after` 脱敏同源口径（`api.md:1774` I-7 服务端裁剪）。**验收：全表检索无口令明文/散列。**

### 3.5 保留策略

| 项 | 结论 |
| --- | --- |
| 归档 | **本期不做**（写频低、行数可控）；登记二期评估，对齐 `login_log` 归档口径（`plan.md:111`） |
| 不可变 | 应用层禁用 `UPDATE`/`DELETE`（对齐 `payroll_log`，`db.md:987`） |
| 扩展 | 若后续需覆盖更多敏感写操作（如部门、角色、薪资规则），**只增 `target_type`/`action` 枚举**，不改表结构 → `TODO(扩展)` |

---

## 4. 站长数据范围收敛（安全必做项）

### 4.1 既有收敛机制（三层 + 域策略，核实存在）

| 层 | 机制 | 位置 |
| --- | --- | --- |
| L1 | 查询参数收敛：非 ADMIN 一律把 `stationId` 强置本人驿站；无归属收敛为「无数据」（安全失败，不放大） | `QueryDataScopeInterceptor:41-46,60-73`（`security-station-admin-review.md:82`） |
| L2 | 端点角色门槛：`@RequireRoles` fail-closed 403 | `RequireRolesInterceptor`（`:83`） |
| L3 | 资源归属校验：非 ADMIN 归属不符 → 按端点策略 404/403（`DataScopePolicy`，ADR-07 逐端点） | `ResourceAccessChecker`（`:84`） |
| 域策略 | `WorkOrderAccessPolicy` / `LeaveAccessPolicy` / `ParcelServiceImpl.resolveReadScope()` | `:85` |

### 4.2 站长登录后可见/可操作端点边界（引用安全评估 2.1 表）

| 域 | 边界 | 站点维度判定 |
| --- | --- | --- |
| 工单 / 包裹 / 考勤 / 班次 / 排班 / KPI / 同步任务 / 采集配置(读) / 请假 / 通知 / 工资单(本人) / 日志上报 / 人事(本人) | 见 `security-station-admin-review.md:55-73` 端点表 | **本站 ✓ / 本人 ✓** |
| 驿站增删改、员工增删改/重置口令/导入导出、部门、注册审批、全局看板、计薪规则/设置/运行、KPI 配置、入离职审批、采集配置写、通知发布、派单规则、考勤规则写、补卡审批、班次增删改、排班批量、工资单生成/审核/发布/发放 | **已为 ADMIN-only（正确，无越权面）** | —（`:74`） |

### 4.3 本次需求下的边界结论与验证口径

| 项 | 结论 |
| --- | --- |
| boss-h5 是否放宽给站长 | **否**（用户口径 2 已确认：驿站精灵仅 ADMIN 登录）→ 安全评估 REG-04 的端准入张力**不适用**（`security-station-admin-review.md:153`） |
| 站长能否管理账号/驿站 | **否**：写接口保持类级 `{"ADMIN"}`（`EmployeeController.java:32`、`StationController.java:27`）→ 安全评估 `:179` 阻断条件①②**均不成立** |
| 站长落地后果 | 站长可登 **员工端 H5**（`staff-h5`，`api.md:420` 既有允许），触及上述 45 端点（**首次激活**）→ 必须过 **M-7 越权回归** |
| **验证口径（M-7，可判定）** | 以站长身份对**不同驿站**资源读/写 → 详情/日志类 `404`、写类 `403`/`8002`/`8003`/`7001`，且**不返回任何异地字段**；无归属站长一律「无数据」；ADMIN 不受影响（`:142`） |
| 前置闭环 | M-7 用例**纳入放行门禁，未过不得放行**（`:142`）；M-2 约束固化（`:137`）；M-4 服务端硬拦截确认（`:139`）；M-6 契约同步（`:141`） |

---

## 5. 批次划分（B0–B8，10 批）

> 每批**可独立验收**。**门禁分两栏**以消除 v1.0「前置/出口歧义」（评审 F-14）：`入口门禁` = **开工前必须闭合**的闸门（未闭合不得开工）；`出口验收` = **本批完成定义**（未达成不得进入下游批）。`迁移` 列标注是否含 C 档结构变更。

| 批次 | 内容 | 依赖 | 影响端 | 迁移 | 入口门禁（开工前必须闭合） | 出口验收（完成定义） |
| --- | --- | --- | --- | :-: | --- | --- |
| **B0 方案冻结与裁定** | 本方案定稿（v1.1 重评）；算法 §8 变更项落位确认；契约草案分发 | — | — | — | —（方案自检） | ① **P0.6 技术评审（L8）** 结论「通过 / 有条件通过」；② A-②~A-⑥ 裁定落位确认（已满足）；③ 安全方案确认（M-3/M-6/M-7 落位核对） |
| **B1 账号角色放开 + 审计留痕 + 首登强制改密** | DTO 白名单 `STATION_ADMIN` + 站长归属校验（ARCH-C-1）；`V23` 新表 + **12 写入点**（ARCH-S-2）；**A-⑤ 服务端拦截**（ARCH-C-7） | B0 出口 | hrm-server（+ `hrm-admin` 员工角色下拉同步） | ✔ `V23` | B0 出口 + **C 档三步授权**（`V23` 迁移：影响面 / 可逆性 / 回滚步骤齐备） | ① `V23` 迁移执行（备份 + 预检）；② 12 写入点落地，可还原「谁在何时改了什么」；③ 口令脱敏（全表无明文/散列）；④ **安全复验**（M-1 审计 / M-2 约束固化 / M-4 服务端硬拦截 / A-⑤ 拦截生效，含「未改密直连业务接口被拒」）；⑤ M-6 契约同步 |
| **B2 驿站管理端（boss-h5）** | 列表/详情/表单/账号页 + `api/org.js` 8 封装 + 路由（静态段先于 `:id`） | B1 出口 | boss-h5 | — | B1 出口；前端门禁（build/lint） | 前端 build/lint 通过；越权回归（M-7）抽样通过 |
| **B3 财务管理（boss-h5）** | `payrollSettings.vue` 升级 + `alias /boss/finance` + `MeSection` 移除「算薪日设置」 | 无 | boss-h5 | — | 前端门禁 | 前端 build/lint 通过；`MeSection.spec.js` 不破 |
| **B4 员工页历史工资单（boss-h5）** | `hrDetail.vue` 新增区块（复用 `GET /finance/payrolls?employeeId=`） | 无 | boss-h5 | — | 前端门禁 | 前端 build/lint 通过 |
| **B5 驿站助手工资单明细（staff-h5）** | 去步骤条 + 计算说明收行（保留确认/异议） | 无 | staff-h5 | — | 前端门禁 | 前端 build/lint 通过；`PayrollStatusSteps` 无消费断言 |
| **B6 审批中心（boss-h5 + 1 出参）** | `approval.vue` + 路由 + `todo` 派生；`HrFlowVO.source` 出参（ARCH-C-6） | 契约 C-6 落定 | boss-h5 + hrm-server | — | 契约 C-6 定稿（`api.md` 更新草案评审） | 契约 C-6 评审通过 + 前端门禁通过 |
| **B7a 排班结构落地（可先做）** | `V22`「活跃唯一」（生成列式部分唯一）；保存**覆盖式**语义 + 出参 `shiftIds[]/shifts[]`（ARCH-C-2/2b/3/4/5）；`schedule.vue` 多班次交互（上限 2） | B0 出口 | hrm-server + boss-h5 | ✔ `V22` | B0 出口 + **算法多班次四件套经技术评审（L8）通过** + **C 档授权**（`V22` 迁移） | ① `V22` 迁移执行（备份 + 查重预检通过）；② 覆盖式保存 / 出参落地；③ 边界用例（上限 2 / 重叠拒绝 / 同日 `ordinal` 互异 / 整批原子 / 逻辑删后再排不冲突）通过 |
| **B7b 出勤口径呈现（A-① 裁定后）** | 应到/缺卡由「人数去重」→「班次粒度」呈现 | B7a 出口 | hrm-server（+ boss-h5） | — | **用户裁定 A-①**（口径未定不实现） | 按 A-① 裁定口径实现；`AttendanceSummaryPolicy` 断言回归通过 |
| **B8 集成回归与门禁** | 越权回归 M-7 全通过；排班多班次边界用例；UI 门禁（宫格 10 项无溢出、热区 ≥44px）；双库脚本同步；文档同步（M-6） | B1–B7b 出口 | 全域 | — | B1–B7b 出口齐备 | ① M-7 越权回归全通过；② 双库脚本同步；③ 文档同步（M-6）完成；④ 主智能体 `code-review` 合流 + 安全复验；⑤ `dev → main`（C 档授权） |

**门禁语义澄清（评审 F-14/F-16）**：① v1.0「B1 前置 = 安全复验」次序不通（安全复验针对的是 B1 **自身实现项**）→ v1.1 改为「安全方案确认」入 **B0 出口**、「安全复验」作 **B1 出口**；② v1.0「B7 整批被 A-① 阻塞」→ v1.1 拆 **B7a（结构落地，A-① 不阻塞）/ B7b（口径呈现，须 A-① 裁定）**；③ 每批**入口门禁未闭合不得开工、出口验收未达成不得进入下游批**，无「未过门禁即可开工」漏洞。

**并行约束（§6.1）**：B2/B3/B4/B5/B6 **同改 `boss-h5/src/router/index.js`** 与 `constants/quickEntries.js` → **不满足「不共享可写文件」判据**，须**串行**或**各自分支 + 各自检查点**（`智能体调度规则.md` §6.1/§6.2、A18）；B7a 触及 `hrm-server` + `boss-h5` 两域，**独占串行**。

---

## 6. 待裁定与风险登记

### 6.1 裁定项（A-x）

#### 6.1.1 待用户裁定（未裁定前不得据此实现）

| # | 事项 | 类型 | 影响 | 处置 |
| --- | --- | --- | --- | --- |
| **A-①** | **出勤/考核口径变更**：应到/缺卡由「人数去重」→「**班次粒度**」（`AttendanceSummaryPolicy.java:35-59`，算法 §8-4:399 / T5:432） | **口径（须用户拍板，§11.4）** | 见下方「两种口径对报表数字的影响」 | **待用户裁定；裁定前不得实现**（**B7b** 入口门禁；**不阻塞 B7a 结构落地**，评审 F-16） |

#### 6.1.2 主代理已裁定（v1.1 落位，不再标「待裁定」）

| # | 事项 | 裁定 | 落位 / 批次 |
| --- | --- | --- | --- |
| **A-②** | 单日班次上限 `maxShiftsPerDay` | **= 2**（与计薪序号编码 `epochDay×2+ordinal` 绑定，`ShiftPayrollPolicy.java:54-56`；>2 须先做计薪编码扩展 T7，本批不做） | §2.4 校验清单；**UI 文案（7.4/9.5/⑪#23）由 UI/UX 改为「单日最多 2 个班次」**（评审 M-5）；批次 **B7a** |
| **A-③** | 「全天」自动配对 | **不引入 `periodType`/`pairId` 配对字段**（决策 D-6 维持）：两个独立班次即可表达「全天」，无需额外字段 | §1.3 D-6、§2.4；UI「早+晚」快捷按钮维持禁用降级；批次 **B7a** |
| **A-④** | 批量排班保存语义 | **按「当天班次集合」整体覆盖**（非累加）；**不开放「追加」模式**（未实现） | §2.4 语义优先级/保存算法、§2.4.1 `ARCH-C-2b`；批次 **B7a** |
| **A-⑤** | 首登/重置后强制改密 | **服务端强制**（未改密前拒访业务接口，非仅前端提示） | **§2.9 `ARCH-C-7`**（拦截层/白名单/错误码口径/验收）；批次 **B1** |
| **A-⑥** | 审计留痕范围 | **新建 `operation_audit_log`**，覆盖**驿站与账号**的增/改/启停/删/重置口令；**口令只记布尔标记** | §3（含 **12 写入点**：employee 5 + 导入/建档 2 + station 4）；批次 **B1** |

**A-① 两种口径对报表数字的影响（必须让用户看到差异）**

| 场景（单员工单日 2 班全勤） | 现口径（人数去重） | 班次粒度口径 | 差异 |
| --- | --- | --- | --- |
| 应到 | 1（有排班人数） | **2**（有效排班行数） | +1 |
| 实到 | 1（有效 ON 卡员工去重） | **2**（`\|A∩R\|`） | +1 |
| 缺卡 | `max(0, 应到−实到)=0` | `\|R\(A∪L)\|=0` | 同 |
| **单班缺勤（应 2 班，实际只打 1 班）** | 应到 1、实到 1、缺卡 **0（漏报）** | 应到 2、实到 1、缺卡 **1（正确）** | **现口径漏报缺卡** |

> **结论**：现口径在「一天多班」下会**漏报缺卡**且「应到/实到」语义混乱；班次粒度口径使「应到/实到/缺卡」三者同构且与计薪 `absent` 一致（`algorithm-multi-shift-scheduling.md:129-139,152`）。**但这是考核呈现口径变更，须用户拍板，架构不代裁。**

### 6.2 风险登记

| # | 风险 | 触发 | 影响 | 缓解 |
| --- | --- | --- | --- | --- |
| R1 | 出勤口径变更引客诉 | 存量月与新口径混用 | 考勤概况/明细数值 | 账期开关 `shiftModelFromMonth` 隔离；上线前公告（算法 R1:382） |
| R2 | `maxShiftsPerDay>2` 与计薪编码冲突 | 配置误开 | 折算比例失真 | 参数硬校验 `≤2`（算法 R2:383） |
| R3 | `V22` 迁移遇存量重复行 | 存量存在重复 `(emp,date,shift)` | 迁移失败（1062） | 迁移前查重预检（0 行，**口径与最终唯一键自洽**：生成列式仅约束活跃行）+ 备份 + C 档授权（算法 R3:384、评审 M-6） |
| R4 | **角色放开首次激活 `STATION_ADMIN` 授权面，无运行时验证** | 放开后站长登 staff-h5 | 若某端点收敛漏配 → 跨站横向越权 | **M-7 越权回归全通过**（`security-station-admin-review.md:142`）；静态未见漏配（`:86`） |
| R5 | 审计表写放大 | 每次增改写一行 | 写延迟 | 低频后台操作、单行 append；可接受 |
| R6 | 审批中心与消息页计数不一致 | 双入口角标 | 用户误判 bug | UI 已在页脚 `.tip` 说明（`boss-management-ui-design.md:583`）；**预期差异** |
| R7 | 强制改密未落地期间仍非服务端强制（REG-03） | 实现前直连接口绕过 | 高权限弱口令长期可用 | **A-⑤ 已裁定 = 服务端强制**，B1 落位 §2.9（拦截层 + 白名单 + 出口验收 4 条）；**B1 出口未达成不得进入下游批** |
| R8 | 前端多批并行改 `router/index.js`/`quickEntries.js` | 并行 fan-out | 提交冲突/覆盖（A18） | 串行或各自分支 + 检查点（§5 并行约束） |
| R9 | `GET /stations` 全量在详情页需客户端定位 | 列表增长 | 详情页取数 | 现状 <100；≥100 时平级加分页（`api.md:729` 已留出口） |
| R10 | **同日两班次同属早/晚半天 → `ordinal` 冲突致计薪少算** | 两班次均落午前（`ShiftPayrollPolicy.shiftOrdinal` 均返回 0） | 计薪 `R` 去重后 `\|R\|=1`，折算少算 | **B7a 校验登记「同日 `ordinal` 须互异」**（§2.4）；具体约束/错误码由算法四件套定稿（评审 M-4）；`ordinal` 口径由算法定（架构不代裁） |
| R11 | `period_index ↔ ordinal` 对齐依赖不变式 A2，当前无强制校验 | 规则时段顺序与班次 `start_time` 升序不一致 | 第二班次打卡时段错位 | 交实现落**校验**（算法 §7.1/§8-8，评审 F-07）；架构 §2.4 引用之 |

### 6.3 遗留 `TODO(扩展)`

| # | 事项 |
| --- | --- |
| T1 | `POST /employees` 校验 `STATION_ADMIN` 归属启用驿站（M-2②）——若后端以 `400`+文案替代专用码，须回改 `api.md` |
| T2 | 可选 `GET /stations/{id}` 便捷端点（非必需，D-2 备选） |
| T3 | `attendance_shift` 补 `period_type`/`pair_id`（「全天」配对，A-③） |
| T4 | S3 整站智能排班多班次扩展（算法 §4.3，本期登记） |
| T5 | ~~排班软删与唯一键共存方案~~ **（v1.1 已关闭）**：定稿为「生成列式部分唯一」（M-1），具体表达式/单列或多列形态交数据库工程师 `V22` |
| T6 | 审计表归档/保留策略（§3.5） |
| T7 | `maxShiftsPerDay>2` 的计薪序号编码扩展（算法 T1:428） |
| T8 | Mock 补 `POST /registration` 种子 + 入离职列表 `source` 种子（UI ⑫#3） |
| T9 | 审计写入方式（AOP vs Service 显式）与事务一致性口径，交后端定稿 |
| T10 | A-⑤ 会话内 `pwd_changed` 标记的刷新/重登口径（改密后避免误拒），交后端定稿（§2.9） |
| T11 | `HrFlowServiceImpl.updateEmployeeStatus` 中 `remark` 二次更新的留痕口径（§3.3 复核残留），交后端定稿 |
| T12 | 同日两班次 `ordinal` 互异约束的错误码/文案（M-4），交算法四件套定稿后纳入 `api.md` |

---

## 7. 对 UI 设计文档 20 条冲突的处置（逐条裁定）

> 依据 `boss-management-ui-design.md:864-889`（⑫ 表）。裁定档：**按 UI 执行** / **需调整** / **不在本批**。

| # | 冲突摘要 | 裁定 | 依据 |
| :-: | --- | --- | --- |
| 1 | `HomeQuickGrid` 8 项上限，本批 10 项突破 | **按 UI 执行** | 组件对条目数无硬编码（`HomeQuickGrid.vue:10-17`）；需回填 `demo-mobile-nav-redesign.md` B1 注释（文档同步） |
| 2 | `ASSIGNABLE_ROLES=['ADMIN','STAFF']`，「新增站长账号」不可达 | **需调整** | 本方案 **ARCH-C-1** 放开 `STATION_ADMIN`（`api.md:560`、`EmployeeCreateRequest.java:44-47`）；配套安全 M-2/M-7；UI 的「禁用+原因」降级随之启用 |
| 3 | 注册无独立组；入离职列表无 `source` 出参 | **需调整** | **ARCH-C-6**：`HrFlowVO` 列表补 `source`（列已在 `V18`，仅未出参；`HrFlowServiceImpl.java:112-113`）；Mock 种子登记 T8 |
| 4 | `matrix.shifts` 无时段/配对字段；载荷单值 | **需调整（分两半）** | 多班次载荷 `shiftIds[]` 纳入本批（ARCH-C-2）；`periodType/pairId` **不在本批**（A-③ 裁定：不引入配对字段，维持按钮禁用）；重叠拒绝口径由算法定（与 A-① 无关；上限 A-②=2） |
| 5 | `stores/todo.js` 无「排除工单的审批合计」派生 | **按 UI 执行** | 纯前端派生 `approvalKeys/approvalTotal`，不改契约（`todo.js:170,207`） |
| 6 | `todo.js:186` 只回传 `config.to`，`params` 未进跳转 | **需调整** | 前端修：`to` 支持 query（如 `{path:'/boss/payroll', query:{status:'OBJECTED'}}`）；否则异议组跳默认 `PENDING_APPROVAL` 看不到单（`todoGroups.js:29-33`） |
| 7 | `MeSection` 入口与新 IA 不一致 | **按 UI 执行** | ⑪#4（`boss-management-ui-design.md:793`） |
| 8 | `station.vue` 只读骨架与需求 ② 冲突 | **按 UI 执行** | 重构为列表页（⑪#11:810）；跨站调动仍缺 → 登记（非本批） |
| 9 | `GET /stations` 全量不分页 | **按 UI 执行** | 属契约事实（`api.md:729`）；前端就地插入/重取，不造分页 |
| 10 | 编辑员工无 `username`/`password` | **按 UI 执行** | `api.md:581`；改口令走 `/password/reset` |
| 11 | 自我保护 `2001` + 最后管理员 `2002` | **按 UI 执行** | 前置禁止 + 原因（`EmployeeServiceImpl.java:160-169,215-223`） |
| 12 | `PayrollStatusSteps.vue` 死代码风险 | **按 UI 执行** | 本批不删、登记；由主智能体确认后决定（UI ⑫#12:881） |
| 13 | `payroll-ui-design §12#23` 记「无 `--fs-num-lg-boss`」与实测不符 | **按 UI 执行** | 实测确有（`boss-h5/src/styles/tokens.scss:43`）→ 回填该文档（文档同步） |
| 14 | `demo-boss-ui-spec` 路径引用 `hrm-demo` | **按 UI 执行** | 一律以 `hrm-clients` 为准（UI ⑫#14:883） |
| 15 | `mobile.scss` 无全局 `.fchip`，新增 3 页将达第 7 份 | **需调整** | 建议随本批把 `.fchip` 上提为具名工具类/组件收口（第四次重复，`项目规则1.md` §2「三次抽取」）；**收口范围由前端定稿** |
| 16 | `ROLE_LABEL` 可显示 `STATION_ADMIN` 但新增/编辑不可选 | **需调整** | 与 #2 同源；ARCH-C-1 放开后消除「看得到改不了」 |
| 17 | `dict.js STATION_STATUS` 无 `variant` | **按 UI 执行** | 走 `TypeFallback`，结果与设计意图一致，无需改字典（`dict.js`） |
| 18 | `api/org.js` 只有只读封装 | **按 UI 执行** | 新增 8 个写封装（⑪#15:814） |
| 19 | `api/hr.js`/`finance.js` 无按员工查工资单封装 | **按 UI 执行** | `getPayrolls` 任意 query 透传 → 无需改签名（⑪#21:830） |
| 20 | `approval.vue` 不存在 | **按 UI 执行** | 新建页 + 路由（⑪#26/#27:845-846） |

**裁定统计**：**按 UI 执行 = 14**（#1,5,7,8,9,10,11,12,13,14,17,18,19,20）；**需调整 = 6**（#2,3,4,6,15,16）；**不在本批 = 0**。

---

## 8. 交付与交接

| 字段 | 内容 |
| --- | --- |
| 产出 | 本文档（v1.1，修订自 v1.0；**仅改本文件**；未改任何代码 / 迁移脚本 / `api.md` / `db.md` 正文 / UI / 算法文档） |
| 证据 | 全部结论标 `文件:行号`；不确定处标「待裁定 / 待核实」；**本机未编译、未运行、未连库**；v1.1 修订依据见 §0.1/§0.2 |
| 需求设计落点 | ① 纯前端（boss-h5）；② 需后端（DTO 白名单）+ 纯前端；③ 纯前端（staff-h5）；④ 纯前端（boss-h5）；⑤ 需后端 + 需迁移（`V22`）+ 前端；⑥ 需 1 出参变更 + 纯前端；横切：审计留痕需迁移（`V23`）+ 首登强制改密需后端拦截（`ARCH-C-7`） |
| 接口变更清单 | **8 变更 / 0 新增 / 8 组复用**（§2.8；v1.1 增 `ARCH-C-2b`、`ARCH-C-7`）；**结构变更 2**（`V22` `V23`） |
| 审计留痕落点 | `V23` 新表 `operation_audit_log`（顶层设计 §3.2，业务时间列命名引用数据库工程师结论）+ **12 写入点**（§3.3）；口令只记布尔（§3.4） |
| 批次划分 | **10 批 B0–B8**（v1.1 由 B7 拆 B7a/B7b）；每批含**入口门禁 + 出口验收**（§5）：B0 出口=技术评审+裁定落位+安全确认 / B1 出口=安全复验（含 A-⑤ 拦截）/ B7a 入口=算法四件套评审通过+C 档、出口=迁移+边界用例 / B7b 入口=A-① 用户裁定 / B8 出口=M-7 全通过+合流 |
| 待用户裁定项 | **1 条**（A-① 出勤/考核口径；裁定前不得实现，仅阻塞 B7b）；**A-②~A-⑥ 主代理已裁定并落位**（§6.1） |
| UI 冲突裁定 | **执行 14 / 调整 6 / 不在本批 0**（§7） |
| 已知风险 | **11 条**（§6.2，v1.1 增 R10 `ordinal` 冲突、R11 不变式 A2 校验）；`TODO(扩展)` **12 条**（§6.3） |
| 如何回滚 | 删除/回退本文档即可；**未触及任何既有源码、契约与迁移脚本** |
| 下一步 | ① 交**技术评审工程师**（P0.6/L8）**重评 v1.1**；② 交主智能体发起**用户裁定 A-①**（其余已裁定）；③ 重评通过后按批次分派实现角色 |
| 报审声明 | 本文为**方案阶段产物**（v1.1）；按 **P0.6 / R25 / L8**，报审前须先经技术评审工程师评估，**本文不宣称已通过**；结论「打回」退回本架构师修订后重评 |
