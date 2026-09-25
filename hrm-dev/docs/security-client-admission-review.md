# 端准入规则收紧 · 技术安全评估报告

> 评估方：网络安全工程师（`express-station-security-engineer`）｜日期：2026-09-25
> 审计性质：**静态代码与配置审计**（本机无 JDK/Maven/Redis，**未编译、未运行、未端到端验证**；运行时与存量数据项见「遗留与未验证项」）
> 评估对象（只读）：`ClientType` / `ClientRolePolicy` / `EndAdmissionPolicy` / `AuthServiceImpl` / `JwtAuthFilter` / `RequireRolesInterceptor` / `application.yml` / `AuthProperties` / `api.md`
> 评估基线：**工作区当前状态**（被评估文件处于未提交态，分支 `feature/前端演示项目拆分与精细化`）
> 声明：本报告只出结论与建议，**未改任何被评估代码/配置/契约**；不含任何真实凭据明文与服务器 IP。

---

## 一、结论摘要（可判定）

| 项 | 结论 |
| --- | --- |
| 伪造 `X-Client-Type` 能否获得**额外权限** | **不能**。权限恒由会话 `role` + `@RequireRoles` + 数据范围决定，端类型不进鉴权链路 |
| 收紧后的端准入是否为 **fail-closed** | **否**。缺省/未知端**静默放行**；`WEB` 端在回退策略下**不受约束** → 存在策略绕过口子 |
| 收紧对**存量会话**是否立即生效 | **否**。`JwtAuthFilter` 不校验端类型，已签发会话最长 3 天窗口内不失效；**端准入仅约束登录** |
| STAFF/STATION_ADMIN 声明 BOSS/省略端类型进管理端后能否**触达管理端接口** | **不能触达 ADMIN-only 接口**（403）；仅"页面可见性"被绕过，**无数据越权** |
| 管理员被禁登员工端是否有**安全副作用** | **无**（无"仅员工端可用"的接口）；存在**入口/UX 影响**，属产品口径 |
| 是否触及 §7.2 绝对禁止项 | **否**（无逆向签名/注入进程/绕过风控/凭据/暴露面/供应链变更） |
| **风险分级** | **中**（产品/审计约束可被静默绕过，需求收敛目标不成立；非数据越权） |
| C 档技术输入结论 | **暂不放行**；须先按必改建议改为 fail-closed 口径并统一端类型契约 |

**一句话：** 本次收紧若沿用"客户端自称 + 缺省放行"的现有模型，则"账号不能混登"**不是安全边界、可被静默绕过**；它不会造成跨角色数据越权（`role` 权威），但会使产品目标落空并产生虚假安全感。

---

## 二、事实核实（含对任务描述的出入）

| # | 任务描述 | 实测 | 判定 |
| --- | --- | --- | --- |
| F1 | `ClientType` 枚举 `ADMIN/BOSS/STAFF/WEB`；声明"不可信、不参与鉴权、未知回落 `WEB`" | 与 [ClientType.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ClientType.java#L16-L46) 一致（`:8-11` 安全定位、`:13-14` `TODO(扩展)`） | **属实** |
| F2 | `ClientRolePolicy.isRoleConstrainedClient()` 只约束 `ADMIN` 端；`isLoginAllowed()` 对不受约束端直接 `true` | 与 [ClientRolePolicy.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientRolePolicy.java#L45-L58) 一致 | **属实（但不完整）** |
| F3 | `application.yml` `pc-allowed-roles: ADMIN`；注释称"未上报端类型时端回落 WEB 且不做角色约束" | 与 [application.yml:108-112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L108-L112) 一致 | **属实** |
| F4 | 端准入调用点与判定顺序（在密码/验证码之后、会话建立之前）；`X-Client-Type` 读取位置 | 顺序属实（`AuthServiceImpl.java:140-143` 密码后、`:169` 建会话前；`AuthController.java:61-62` 读头）。**但**：实际优先走 `EndAdmissionPolicy`（请求体 `clientType`），仅回退才用 `ClientRolePolicy`（`AuthServiceImpl.java:563-569`） | **不完整（漏关键类）** |
| F5 | `api.md` 有 `X-Client-Type`/端类型/`1110` 的现行约定 | **`api.md` 全文无任何相关记载**（检索 `X-Client-Type`/`clientType`/`1110`/`端准入` 均无匹配）。1110 与端准入仅记载于 [demo-login-redesign.md §1.3/§7.4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-login-redesign.md#L77-L87)（标注"须登记为契约增补"） | **不成立** |

### 任务描述外的关键事实（评估要点所依赖）

- **F6 双口径并存（核心）：** 系统存在**两套互不相同的端类型口径**并存：
  - `ClientType`：`ADMIN/BOSS/STAFF/WEB`（"ADMIN"=PC 管理端）；
  - `EndAdmissionPolicy`：只认 `WEB/H5`（"WEB"=PC 网页端，`H5`=移动端；[EndAdmissionPolicy.java:70-73](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/EndAdmissionPolicy.java#L70-L73)）；
  - 架构文档第三套：[multi-client-architecture.md §2.1.3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L88-L95) 写 `X-Client-Type: staff|boss|web|admin`。
  三者取值域不一致，且由 `isEndAllowed` 按"请求体非空则走 `EndAdmissionPolicy`，否则回退 `ClientRolePolicy`"分流。
- **F7 现状生效口径（由 `EndAdmissionPolicy.isAllowed` 决定）：** `clientType=WEB` → 仅 `pc-allowed-roles`（默认 `ADMIN`）；`H5 + as=boss` → `ADMIN/STATION_ADMIN`；`H5 无 as`（员工端）→ **不限角色**；**缺省/未知 → 不校验**（[EndAdmissionPolicy.java:46-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/EndAdmissionPolicy.java#L46-L64)）。
- **F8 `RoleEnum` 无 `BOSS` 角色：** 角色全集仅 `ADMIN/STATION_ADMIN/STAFF`（[RoleEnum.java:16-23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/RoleEnum.java#L16-L23)）。"BOSS"是**端类型/入口视角**（`?as=boss`），**不是角色**。
- **F9 三端实际上报：** PC=`WEB`（[pc/views/login/index.vue:19-20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/login/index.vue#L19-L20)）；移动端=`H5`，`?as=boss` 为管理端（"驿站精灵"）、无 `as` 为员工端（"驿站助手"）（[mobile/views/login/index.vue:24-25,38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue#L24-L38)）。**但登录请求体的 `clientType` 仅由前端 UI 携带**，服务端对未携带者放行。
- **F10 端准入被三处调用：** 密码登录（`:140`）、短信登录（`:362`）、设备二次验证（`:385`）及发码复判（`:287`），口径一致。

---

## 三、七项评估（逐条结论 + 依据）

### 1. 绕过可能性：伪造 `X-Client-Type` 能否越过收紧获得额外权限？

**结论：不能获得额外权限；但可绕过"端准入策略本身"。**

- **权限层面：安全。** 授权链全程以服务端会话角色为唯一权威——`JwtAuthFilter` 从 Redis 会话取 `role` 注入 `UserContext`（[JwtAuthFilter.java:130-134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java#L130-L134)），端点级由 `RequireRolesInterceptor` 比对会话 `role`（fail-closed，[RequireRolesInterceptor.java:40-58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L40-L58)）。全仓检索未发现 `clientType` 参与任何鉴权/数据范围判定。任务描述的"不参与鉴权"论断**成立**。
- **策略层面：可绕过。** 端准入本身以"客户端自称"为输入，攻击者/普通用户**省略或伪造**端标识即可跳过 1110（详见第 2 条）。这属"约束失效"，**不等于权限提升**——绕过者拿到的仍是其 `role` 决定的权限集。

### 2. 收紧是否真 fail-closed？`WEB` 缺省端约束应取什么值？

**结论：现状不是 fail-closed；`WEB` 端必须纳入受约束端，否则即为绕过口子。**

- **现状为 fail-open：** `isEndAllowed` 在请求体 `clientType` 为空时回退 `ClientRolePolicy.isLoginAllowed(headerClientType, …)`；`ClientRolePolicy` 只在端为 `ADMIN` 时约束，`WEB`/`null`/任意非法值（`ClientType.normalize` 一律回落 `WEB`）**均不受约束 → 直接 `true`**（[ClientRolePolicy.java:46-58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientRolePolicy.java#L46-L58)）。故：**请求体 `clientType` 缺失 + 请求头 `X-Client-Type` 缺失/非法/为 `WEB` → 任意角色登录成功**。
- **对本次收紧的直接后果：** 若新规则定义为"PC 端仅 `ADMIN`"，攻击者只需**不传** `clientType`（或传任意非法值）即可让 `STAFF` 登录"PC 端"。**这即是"绕过口子"。**
- **明确建议：** 缺失/非法/未知端类型**不得回落为"不约束"**。`WEB`（及缺省端）**必须取受约束集合 `hrm.auth.pc-allowed-roles`（默认 `ADMIN`）**；缺省端建议按最严口径处理（拒绝或按目标端约束），**不得默认放行**。
- **口径张力提示：** `H5` 分支（员工端/管理端）**不经** `ClientRolePolicy`，故若只约束 `WEB`，则"H5 省略请求体 `clientType`"仍走回退 → 不受约束。**必须同时消除 `ClientRolePolicy` 与 `EndAdmissionPolicy` 的双口径（见必改建议 1）。**

### 3. 对存量会话与在途请求的影响

**结论：存量会话不失效；"端类型仅约束登录"的语义缺口确认存在。**

- **存量会话不失效：** `JwtAuthFilter` 仅解析 JWT + 校验 Redis 会话 + 重认证窗口，**不读取也不校验端类型**（[:62-134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java#L62-L134)），仅将 `session.getClientType()` 注入上下文供审计/限流。故收紧仅对新登录生效；已签发的越权端会话在 `session-ttl-seconds`（默认 3 天）内继续有效。
- **语义缺口：** 端准入只在登录三条路径校验（`AuthServiceImpl.java:140/362/385`），**请求级不校验** → 除"重新登录"外无第二道端校验。
- **是否需要 `JwtAuthFilter` 补校验：** **不建议**把端类型提升为请求级鉴权边界。理由：端类型是客户端自称，请求级与会话级**同为攻击者可控**（伪造请求端 vs 伪造登录端成本相同），补校验只会增加误判与认证主链路（高风险改动），**不增加实质安全**（[multi-client-architecture.md:158-162](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L158-L162) 亦明确端类型仅作审计维度）。**可行且建议的是**：把"存量会话窗口"作为**已知运营口径**由用户确认，或提供清会话的 C 档运营动作；若产品确需强约束，应改用**服务端可判定的信号**（而非端自称）。

### 4. 越权面：声明 BOSS/省略端类型进管理端后能否触达管理端接口？

**结论：不能触达 ADMIN-only 接口（403）；无新增数据越权。**

- **判据：** 登录不改变会话 `role`（声明 `BOSS` 仅改 `clientType` 元数据，[AuthServiceImpl.java:477-478](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L477-L478)）；所有非公开端点由 `RequireRolesInterceptor` 按会话 `role` fail-closed 把关。
- **具体接口举例（`STAFF` 登录管理端后调用）：**
  - **403（ADMIN-only）：** `GET /api/v1/dashboard`（`DashboardController` 类级 `{"ADMIN"}`）、`/api/v1/employees*`（`EmployeeController` 类级）、`/api/v1/stations*`、`/api/v1/departments*`、`/api/v1/kpi/metrics*`（`KpiMetricController`）、`/api/v1/hr/flows*`（`HrFlowController`）、`/api/v1/payroll/rules*`（`PayrollRuleController`）、`/api/v1/sync/config-center*`（`SyncConfigCenterController`）。
  - **403（ADMIN/STATION_ADMIN，对 STAFF）：** `/api/v1/kpi/scores*`、`/api/v1/schedules`、`/api/v1/attendance/*`（`AttendanceController.java:96/103/114/121`）、`/api/v1/sync/tasks*`。
  - **放行（三端共享、门槛含 `STAFF`）：** `/api/v1/parcels*`、`/api/v1/work-orders*`、`/api/v1/leave*`、`/api/v1/notifications*` 等——**这些本就是 `STAFF` 的合法权限，非越权**。
- **对 `STATION_ADMIN`：** 声明 `as=boss` 登录管理端后，对 `{"ADMIN","STATION_ADMIN"}` 接口**可访问**（数据范围限本人驿站）——这是**当前设计口径**（`EndAdmissionPolicy.BOSS_VIEW_ROLES`），**不是漏洞**；但若新规则"管理端仅 `ADMIN`"，则属"混登"应被拒，**现状与新需求冲突**。
- **越权面定级：** 端类型相关的"越权"仅止于**页面/入口可见性**，**不产生跨角色数据访问**。

### 5. 管理员被禁登员工端的业务副作用

**结论：安全/权限层面无副作用；存在入口/UX 影响（产品口径）。**

- 所有含 `STAFF` 的门槛**均含 `ADMIN`**（如 `AttendanceController.java:57/80/87/128`、`LeaveController.java:48-111`、`HrProfileController.java:44/66`、`PayrollController.java:51/88/113/120`、`NotificationController`、`ParcelController`）。**不存在"仅员工端可用"的接口**，故禁登员工端**不剥夺 ADMIN 任何接口能力**。
- `demo-login-redesign.md` 原标记"员工端是否拒绝 ADMIN 登录"为**待用户裁定**项（U3 / §9-A3，[:50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-login-redesign.md#L50)）。本次需求即裁定为"拒绝"。**若管理员需要在员工端自查考勤/本人薪资**，需确认管理端有等价"本人"入口，否则为功能缺口（非安全项）。

### 6. 合规/风险红线核查

**结论：不触及 §7.2 绝对禁止项。**

- 不涉及逆向接口签名、注入进程、伪装设备标识、绕过风控限频；不涉及凭据处理、外部暴露面变更（未新增公开端点）、依赖供应链变更（无依赖改动）。
- **唯一需守的红线（表述纪律）：** 不得在文档/评审中把"客户端自称的端类型"称为"端维度鉴权/安全边界"，并在未验证情况下声称"已实现端维度鉴权/已合规"（本报告明确其为**产品/审计约束**，与 [ClientType.java:8-11](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ClientType.java#L8-L11)、[security-auth-review.md §4.5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md#L249-L254) 的既有论断一致）。

### 7. 风险分级

**结论：中。**

- **实际越权数据访问：无。** 权限恒由 `role` + `@RequireRoles` + 数据范围三家决定（[security-auth-review.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md#L30) 既有结论亦成立）；端类型不进鉴权。
- **端准入收敛目标（"不能混登"）：中风险。** 现状 fail-open；**若不改口径**：① 需求目标不成立（可静默绕过）；② 管理端现状仍允许 `STATION_ADMIN`；③ 员工端现状不限角色（`ADMIN` 可登）——与目标准入矩阵不符；④ 存在"以为是安全边界实则非"的虚假安全感。
- **"若不改是否造成实际越权数据访问"：不会。** 但会造成端准入形同虚设与需求落空。

---

## 四、必改建议（7 条，各附验收标准）

> 均交对应实现角色落地；本评估方不改代码。定稿后须复验（L7）。

1. **统一端类型单一真源。** 收敛 `ClientType`（`ADMIN/BOSS/STAFF/WEB`）与 `EndAdmissionPolicy`（`WEB/H5`）两套口径为一套，并与 `multi-client-architecture.md §2.1.3` 的取值域对齐，删除或改造 `ClientRolePolicy` 的"仅 `ADMIN` 受约束"语义。
   验收：全仓仅一处端类型归一函数；`grep` 无第二套取值域定义。
2. **端准入缺省/未知一律 fail-closed。** 缺失/非法/未知端类型**不得放行**；缺省端按最严口径（拒绝或按目标端约束）。
   验收：登录三路径均断言"不传 `clientType` → 1110"；原"缺省放行"用例须显式变更并给出变更理由。
3. **`WEB`/缺省端纳入受约束端。** `WEB` 取 `hrm.auth.pc-allowed-roles`（默认 `ADMIN`）；明确"`WEB` 不约束 = 绕过口子"。
   验收：单测断言 `WEB`+`STAFF` → 拒、`WEB`+`ADMIN` → 允。
4. **管理端按需求收紧为仅 `ADMIN`。** 若采纳"驿站精灵仅 ADMIN"，须移除 `EndAdmissionPolicy.BOSS_VIEW_ROLES` 中的 `STATION_ADMIN`（或由用户裁定保留并登记例外）。
   验收：`H5 + as=boss + STATION_ADMIN` → 1110；`H5 + as=boss + ADMIN` → 允。
5. **员工端收紧为 `STAFF + STATION_ADMIN`（拒绝 `ADMIN`）。**
   验收：`H5 + 无 as + ADMIN` → 1110；`H5 + 无 as + STAFF/STATION_ADMIN` → 允。
6. **三条登录路径与票据复判共用同一策略对象。** 密码/短信/设备二次验证（含发码复判）策略与单测矩阵一致（避免漏判即静默放行）。
   验收：单测对三路径 + 票据复判跑同一参数矩阵，结果一致。
7. **契约登记（P5 契约先行）。** 在 `api.md` 正式增补端准入条款：`clientType`/`entryAs` 入参、`X-Client-Type` 头、`1110` 错误码与其触发面（当前 `api.md` **零记载**，属事实缺陷）。
   验收：`api.md` 含 1110 与端准入章节，且与实现逐字一致；`demo-login-redesign.md` 的"须登记为契约增补"闭环。

**补充（非必改，建议）：** 存量会话窗口口径（第 3 条）须由用户明确接受"最长 3 天"或提供清会话运营动作；若接受，须写入文档，不得默认沉默。

---

## 五、遗留与未验证项

| # | 项 | 状态 |
| --- | --- | --- |
| U1 | 编译/单测/E2E 实跑 | **未运行**（本机无 JDK/Maven/Redis/Docker）；本报告为**静态审计** |
| U2 | `ClientRolePolicyTest`/`EndAdmissionPolicyTest` 现存断言是否与收紧冲突 | **未逐条跑**；须在实现后由测试工程师回归 |
| U3 | `hrm-admin`（PC 一期工程，只读）实际上报的端标识 | **未核**（本次未读该工程）；Demo PC 上报 `WEB` 已核实 |
| U4 | 是否存在依赖 `X-Client-Type` 头回退行为的其它调用方（脚本/采集端） | **未全量核**；回退分支移除前须全仓检索（`AuthServiceImpl.java:100-102` 同类 `TODO(扩展)`） |
| U5 | 生产环境任何验证 | **未做且禁止做**（不得在生产库执行破坏性验证） |
| U6 | 存量越权端会话的实际数量 | **未知**（只读，未查 Redis） |

**复验结论：待定。** 上述必改项闭环（含实现落地与回归）后，由本评估方出复验结论；**在此之前不得声称"端准入已合规 / 已实现端维度鉴权"**。

---

## 附：本报告依据的条款

- 项目规则 §7.2（安全红线）、§10.3（C 档三步授权与审批纪律）、§8（智能体调用）、§9.2（技能强制）。
- 调度规则 P0.5（安全评估先行）、L7（安全评估 → 授权 → 实现 → 复验）、R24（安全评估路由）。
- 既有安全结论：[security-auth-review.md §4.5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md#L249-L254)（三端角色口径不得共用一套，否则可能越权）。
- 契约与设计：[multi-client-architecture.md §2.1.3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L88-L95)、[demo-login-redesign.md §1.3/U3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-login-redesign.md#L77-L87)。

---

## 复验（实现完成后）

> 复验方：网络安全工程师（`express-station-security-engineer`）｜日期：2026-09-25
> 复验性质：**静态代码与配置复验**（本机无 JDK/Maven/Redis；服务器实跑数据由主智能体提供，**本报告不采信为结论依据**，均以仓库源码逐条核对为准）
> 复验基线：工作区当前状态（`hrm-server` + `hrm-demo` + 顺带核 `hrm-admin`/`deploy`）
> 本轮声明：**只读 + 只追加本报告与 `update-log.md`**，未改任何被复验代码 / 配置 / 契约。

### 一、复验结论（可判定）

| 项 | 结论 |
| --- | --- |
| 7 条必改是否全部落实 | **7/7 已落实**（逐条取证见 §二） |
| 是否引入新问题 | **有 1 项交付阻断级兼容回归**（`hrm-admin` 登录未上报端 → fail-closed 被 1110 拒登），另有 4 项低风险观察（见 §三） |
| 安全属性是否被本次改动破坏 | **否**。仍无权限提升 / 越权 / 数据泄露面；端类型“非鉴权边界”的既有判定不变 |
| 复验后残余风险评级 | **低**（较上一轮「中」降级，依据见 §四） |
| 能否进入部署上线（本步门禁） | **有条件放行**：安全维度**通过**；但须先闭环 §三 `hrm-admin` 兼容项。**若本次部署范围包含 `hrm-admin`（生产 PC 站点），则该条件未闭环前不放行**；若明确不含，则可放行。 |

**一句话：** 端准入已从「客户端自称 + 缺省放行」改为「严格归一 + 缺省/未知/非法一律 1110 + 三路径共用同一策略」，上一轮认定的绕过口子（缺省静默放行、`WEB` 不受约束、双口径分流）**已从实现上消除**；残余风险降到「端自称固有、无权限影响」。但同一次 fail-closed 变更会把**不报端类型的既有客户端一并拒登**，已定位到 `hrm-admin` 登录链路，属放行前必须确认/闭环项。

### 二、7 条必改逐条核验

| # | 必改项 | 结论 | 依据（文件:行） |
| --- | --- | --- | --- |
| 1 | 统一端类型单一真源 | **已落实** | 旧类 `ClientRolePolicy`/`EndAdmissionPolicy` 及其测试**已删除**（Glob 无文件；全仓 `grep ClientRolePolicy\|EndAdmissionPolicy\|ClientType.DEFAULT\|isRoleConstrainedClient\|BOSS_VIEW_ROLES` **源码零残留**，仅命中历史文档与旧报告）；唯一取值域 [ClientType.java:26-55](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ClientType.java#L26-L55)；唯一归一入口 [ClientAdmissionPolicy.resolveEnd:84-94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L84-L94)；`ClientType.normalize()`/`DEFAULT` 在 `hrm-server/src` 内 `.normalize(` **零命中** |
| 2 | 缺省/未知/非法端 fail-closed | **已落实** | [ClientAdmissionPolicy.isLoginAllowed:64-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L64-L72)（`end==null → false`）；`resolveEnd` 不再回落 WEB（[:82-93](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L82-L93)）；[ClientType.parse:44-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ClientType.java#L44-L50) 缺失/未知返回 `null`；测试 `missingUnknownIllegalEndIsFailClosed`、`parseReturnsNullForMissingOrBlank` |
| 3 | `WEB`/缺省端纳入受约束端 | **已落实** | [roleSetOf:149-153](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L149-L153)（`case ADMIN, WEB -> pc`）；`hrm.auth.pc-allowed-roles=ADMIN`（[application.yml:119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L119)、[AuthProperties.java:112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AuthProperties.java#L112)）；测试 `assertEnd("WEB", true, false, false)` |
| 4 | 管理端（BOSS）仅 ADMIN | **已落实** | `hrm.auth.boss-allowed-roles=ADMIN`（[application.yml:120](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L120)、[AuthProperties.java:120](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AuthProperties.java#L120)）；`bossAvailableRoles` 走 `allowed.boss()`；测试 `legacyH5DerivesEndFromEntryAs`（`H5+as=boss+STATION_ADMIN` 拒、`+ADMIN` 允） |
| 5 | 员工端拒 `ADMIN` | **已落实** | `hrm.auth.staff-allowed-roles=STAFF,STATION_ADMIN`（[application.yml:121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L121)）；测试 `assertEnd("STAFF", false, true, true)` 与 `H5 无 as + ADMIN` 拒 |
| 6 | 三条登录路径 + 票据复判共用同一策略 | **已落实** | 单点 [isEndAllowed:567-575](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L567-L575) 供密码[:139](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L139)/短信[:362](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L362)复用；票据复判（发码 [:286-287](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L286-L287)、验证 [:385-386](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L385-L386)）直接调 `ClientAdmissionPolicy.isLoginAllowed`；Mock 侧同一 `resolveEnd` + `END_ALLOWED_ROLES` 表驱动三路径（[auth.js:66-100](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L66-L100)、[:252](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L252)/[:336](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L336)/[:385](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L385)/[:413](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L413)） |
| 7 | `api.md` 契约登记 | **已落实** | 4.1.5 端准入契约 + 目标准入矩阵 + 配置键 + 失败行为（[api.md:286-338](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L286-L338)）；1110 明细与入参登记（[api.md:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L77)、[:106](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L106)、[:202-206](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L202-L206)）；`demo-login-redesign.md` 的「须登记为契约增补」闭环 |

**必改 1 的补充裁定：** `multi-client-architecture.md §2.1.3` 的取值域 `staff|boss|web|admin` 与 `ClientType` 的 `{ADMIN,BOSS,STAFF,WEB}` **集合一致**（仅大小写差异，`parse` 大小写不敏感），判定「对齐」。但该文档同句仍写「缺失默认 `web`」，与本次 fail-closed 相冲突 → 列为文档待同步项（低风险，见 §三-⑤），非代码缺陷，**不阻断放行**。

### 三、新引入问题

#### ① 【中 · 交付阻断，非安全漏洞】`hrm-admin` 登录未上报端类型，将被 fail-closed 一并拒登（1110）

- **事实：** [hrm-admin/src/views/login/index.vue:101-104](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/login/index.vue#L101-L104) 仅提交 `{ username, password }`；请求拦截器 [hrm-admin/src/utils/request.js:23-32](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/utils/request.js#L23-L32) 仅附加 `Authorization`，**不写 `X-Client-Type`、不写 `clientType`**（全仓 `X-Client-Type` 命中列表不含任何 `hrm-admin` 文件）。
- **影响：** 该为 deploy 目标前端（[deploy.sh:68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh#L68) `SITE_DIR=/www/wwwroot/hrm-admin`、[:74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh#L74)；[nginx.conf.example:28](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L28) `root /www/wwwroot/hrm-admin`）。部署新后端后，`ADMIN` 从该站点登录将命中 `resolveEnd→null→false` → **HTTP 200 + 1110，无法登录**。
- **可利用性 / 安全属性：** **不可被攻击者利用为越权**——方向是 fail-closed（拒），无权限提升、无数据外泄。**严重级按「可用性/交付回归」计为中**，且是「安全收紧的副作用」，符合上一轮 U3/U4「既有调用方是否依赖旧回退行为」的未验证风险，现被证实。
- **为何部署门禁不会自动拦下：** 健康检查 [deploy.sh:313](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh#L313) 仅取 `%{http_code}`，而 1110 仍是 HTTP 200 → 健康检查**照常通过**，回归会被静默放过。
- **复现步骤（可在服务器执行、只读断言）：**
  1. `curl -s -X POST http://127.0.0.1:<port>/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"<ADMIN账号>","password":"<密码>"}'`
  2. **预期（当前实现）：** `{"code":1110,"message":"该账号无权登录此端"}` —— 即 `hrm-admin` 现状登录结果。
- **修复建议（交对应实现角色，安全方不改代码）：** 二选一——
  - a) `hrm-admin` 登录补端：请求体加 `clientType: 'WEB'` 或请求拦截器统一加 `X-Client-Type: ADMIN`；
  - b) 若本次**明确不部署 `hrm-admin`/生产 PC 站点**，则在放行记录中显式声明该工程不在本次范围。
- **验收标准：** 用 `hrm-admin` 打 `POST /auth/login` 带 `ADMIN` 凭据返回 `code=200`；带 `STAFF` 凭据（若该站点存在 STAFF 入口）返回 `1110`；或给出书面「`hrm-admin` 不在本次部署范围」的范围声明。

#### ② 【低】会话/票据内 `clientType` 由旧 `WEB/H5` 改为规范端名（跨端互踢粒度 / 登录日志 / Redis 兼容）

- **跨端互踢粒度：** [SessionEvictionPolicy.sameClientAndDevice:78-82](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/SessionEvictionPolicy.java#L78-L82) 以 `clientType+deviceId` 判「同端同设备覆盖」。旧值下 `BOSS` 与 `STAFF` 两移动端同为 `"H5"`，现在为 `BOSS`/`STAFF` 两值 → 粒度由 2 组细化为最多 4 组。**评估：无实际安全影响**——同一员工角色唯一，不可能同时取得 BOSS（需 ADMIN）与 STAFF（需 STAFF/STATION_ADMIN）会话；并发总量仍受 `max-sessions-per-employee`（默认 5）封顶。
- **登录日志：** `recordLoginLog(...)` 入参不含 `clientType`，登录日志**不记录端类型**，故本次记录值变更**不影响登录日志**。
- **既有 Redis 会话兼容：** 存量会话仍存 `"WEB"/"H5"`；`JwtAuthFilter` 仅将其注入上下文供审计/限流（[JwtAuthFilter.java:134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java#L134)），**不参与鉴权**；仅副作用是「遗留 `H5` 会话与新 `BOSS/STAFF` 会话在同一设备上不再互相覆盖」，由并发上限兜底，且随 3 天 TTL 自然消解。**评级低**。

#### ③ 【低 · 既有行为，非本次引入】伪造 `X-Client-Type` 头仍可“自选目标端”，但不获得额外权限

- **结论不变：** 端类型来自客户端自称，**不是鉴权边界**；`header` 优先只是读取顺序，伪造头/体成本相同，拿到的是其会话 `role` 决定的最小权限集。全仓仍无 `clientType` 参与授权判定。头非空非法即拒（[ClientAdmissionPolicyTest.headerTakesPrecedenceOverBody](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/test/java/com/qiujie/service/auth/support/ClientAdmissionPolicyTest.java#L90-L101)），**未新增绕过口子**。
- **派生观察（可选优化，非放行项）：** 短信路径端准入位于验证码校验之前（[:362](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L362) 早于 [:367](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L367)）；该顺序在改造前后一致（本次仅替换策略实现），故**不计入本次新引入问题**，仅记录：未持码者或可据 `1001/1002/1110` 与 `1102` 的差异推断账号存在性——建议后续统一错误码或调整判定顺序。

#### ④ 【低 · 文档一致性】`multi-client-architecture.md §2.1.3` 未同步 fail-closed

- [multi-client-architecture.md:93](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L88-L95) 仍写「缺失默认 `web`」，与 4.1.5「缺省即 1110」冲突。**仅文档，不改行为**，建议随下次文档批同步（实现角色/架构师）。

#### ⑤ 【低 · 契约表述】`api.md` 写“前端须上报 `X-Client-Type`”，实际 Demo 走请求体 `clientType`

- [api.md:337-338](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L337-L338) 的前端契约描述与实际前端不符：Demo 三端上报的是请求体 `clientType`（PC=`WEB`、移动=`H5`+`as`），不发 `X-Client-Type` 头（全仓 `X-Client-Type` 无前端命中）。因请求体回退存在，**行为正确、无功能影响**；建议文档改为“体 `clientType` 为主、头 `X-Client-Type` 为可选新契约”，以免后续误判。**不阻断放行**。

### 四、残余风险评级与变化依据

**复验后残余风险：低。**

- **自「中」降级的依据：** 上一轮判「中」的根因是「缺省/未知端静默放行、`WEB` 不受约束、双口径分流 → 产品约束可被静默绕过」。本轮实现：① 缺省/未知/非法一律 `null → false`（必改 2，[ClientAdmissionPolicy.java:64-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L64-L72)）；② `WEB` 纳入 `pc-allowed-roles`（必改 3，[:149-153](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L149-L153)）；③ 双口径收敛为单一 `ClientAdmissionPolicy`（必改 1）。三者叠加后，**“不报端即放行”的绕过路径在实现层已不存在**；三路径共用同一策略（必改 6）消除“漏判即静默放行”的历史根因。故该项风险项消解。
- **仍存在的残余（为何不为「无」）：** 端类型本身仍是客户端自称 → 对**自定义/伪造客户端**，“账号不能混登”仍非强制边界；但该路径**不产生任何权限提升**（授权恒为 `role + @RequireRoles + 数据范围`，端类型不进鉴权链路），故按「低」而非「中/高」。
- **“不因自评降级”说明：** 声明为「低」不改变「端类型非鉴权边界、不得对外称端维度鉴权」的表述红线，仍适用。

### 五、存量会话遗留风险与处置建议（只给建议，不执行）

- **事实：** 端准入**仅约束新登录**；`JwtAuthFilter` 不校验端类型，已签发会话在 `session-ttl-seconds`（默认 3 天）内继续有效（[api.md:338](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L337-L338) 已登记为“已知运营口径”）。
- **风险定级：低。** 存量会话的端类型不改变其 `role`，故不存在“旧会话带来越权”的问题；风险面仅止于“在旧口径下混登成功的会话，3 天内不会因收紧被强制下线”。
- **建议（按需，非放行前提）：** 若产品要求“收紧即刻全量生效”，由**主智能体**按 **C 档三步授权**决定是否执行“清会话/强制重认证”运营动作（影响范围：当前在线会话；可逆性：用户重新登录即可；回滚：无数据写入、无破坏、可回滚至不清）；否则接受“最长 3 天自然到期”即可。**安全方不获取凭据、不在生产执行任何改状态动作。**

### 六、事实性纠正（对任务描述 / 上游材料）

| # | 描述来源 | 描述 | 实测 | 判定 |
| --- | --- | --- | --- | --- |
| R1 | 任务描述 | 被复验实现含 `hrm-server` + `hrm-demo` | 属实，但**同源后端还有 `hrm-admin`（生产 PC 站点）**未在本次范围内同步端上报，构成兼容缺口 | **需补**（§三-①） |
| R2 | 任务描述 | 单测 17 项全过 | 本机无 JDK/Maven，**未复跑**；本报告结论不依赖该结果，全部以源码取证 | **未独立验证（U1）** |
| R3 | 上一轮报告 U3 | `hrm-admin` 实际上报端标识「未核」 | **本轮已核：不上报任何端**（见 §三-①） | **已闭环为“缺口”** |
| R4 | `api.md:337` | 「前端契约：三端分别上报 `X-Client-Type`」 | 实际 Demo 上报请求体 `clientType`，不发该头 | **文档表述与实现不符**（§三-⑤） |
| R5 | `multi-client-architecture.md:93` | 「缺失默认 `web`」 | 与 4.1.5 fail-closed 冲突 | **文档未同步**（§三-④） |

### 七、遗留与未验证项（复验后）

| # | 项 | 状态 |
| --- | --- | --- |
| V1 | 服务器单测/编译/E2E 实跑 | **未由安全方独立复跑**（本机无 JDK/Maven/Redis）；结论基于源码逐条取证 |
| V2 | `hrm-admin` 是否在本次部署范围内 | **需主智能体与用户确认**（决定 §三-① 是否构成放行阻断） |
| V3 | 生产环境任何验证 | **未做且禁止做**（不在生产库/生产环境执行改状态验证） |
| V4 | 存量越权端会话实际数量 | **未知**（只读，未查 Redis；且安全方不接触生产凭据） |

**复验结论：** 7 条必改 **7/7 已落实**；安全维度 **可放行**；**部署门禁 = 有条件放行**——须先闭环 §三-①（`hrm-admin` 端上报，或书面声明其不在本次部署范围）。其余为低风险观察项，不阻断放行。**在 §三-① 闭环前，不得声称「端准入已全量生效 / 已合规」。**
