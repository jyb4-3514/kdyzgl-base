# 技术评审报告：员工自助注册方案（registration-design v1.2）

| 项目 | 内容 |
| ---- | ---- |
| 评估对象 | [registration-design.md](registration-design.md) · **v1.2（已定稿，980 行）** |
| 关联依据 | [security-registration-review.md](security-registration-review.md)（阻断，M-1~M-9）、[security-full-review-20260926.md](security-full-review-20260926.md)（SEC-FULL-08/09/14/18）、[tech-review-full-20260926.md](tech-review-full-20260926.md)（有条件通过，TR-M1/M5） |
| 作者（产出方） | 架构师 `express-station-architect` |
| 评估方 | `express-station-tech-reviewer`（**未参与本方案产出**，评估方与产出方分离） |
| 日期 | 2026-09-26 |
| 评审方式 | **纯静态文档级 + 独立读盘核验**（源码/契约/迁移目录逐条取证）。**本机无 JDK/Maven/MySQL/Redis → 后端类结论一律「未运行」，收敛到服务器 `mvn -q clean package` / `mvn test` / Flyway 空库迁移** |
| **结论等级** | **打回**（存在 1 处与 `db.md` 未声明冲突 + 1 处事实错误出处 + 1 处激活路径功能缺口） |
| 准入结论 | **不可报主智能体审批**；须按第 4 节必改项修订后**重评**（P0.6/L8 硬性闸门） |

> 本报告为**工程面技术质量结论**；安全实质结论归网络安全工程师（`security-registration-review.md` 结论「阻断（公网）」+ SEC-FULL-08/09）。本报告**不出安全结论、不代改方案、不代授权、不执行 git**。

---

## 1. 受理检查

| 检查项 | 结论 |
| --- | --- |
| 版本/日期明确 | ✅ v1.2 / 2026-09-26（结论绑定此版本，变更须重评） |
| 验收标准可判定 | ✅ §9 批次表逐批含验收标准 |
| 契约引用关系已声明 | ✅ §3.5 / §7 / §11.10 声明对 `api.md`/`db.md` 的改动与差异 |
| 不含真实凭据/业务数据 | ✅ 无明文凭据（占位符） |

**受理成立**，进入逐项评估。

---

## 2. 六维逐项评估

| 维度 | 结论 | 关键证据（行号） |
| --- | --- | --- |
| 1 依据充分性 | **不满足（1 处错源）** | 见 2.1 |
| 2 边界与模块合理性 | **基本满足（1 处缺口）** | 见 2.2 |
| 3 NFR 覆盖度 | **基本满足** | 见 2.3 |
| 4 复杂度与可行性 | **满足** | 见 2.4 |
| 5 假设与风险登记 | **基本满足（遗漏 4 项）** | 见 2.5 |
| 6 既有契约一致性 | **不满足（1 处未声明冲突）** | 见 2.6 |

### 2.1 维度 1 · 依据充分性

**抽样核验（独立读盘，均命中）：** `assignForFlow` 现仅写 `flow.position`（[HrFlowServiceImpl.java:361-363](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L361-L363)）；`createEmployeeForFlow` 只校验 username、不校验 phone，直接 `setPhone(flow.getPhone())`（[:264-305](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L305)）；`resolveScene` 未知场景回落 `LOGIN`（[AuthServiceImpl.java:605-619](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L605-L619)）；`employee` 无 `position` 列（[Employee.java:19-66](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66)）；`hr_flow.position` 存在、无 `source` 列（[HrFlow.java:55-57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L55-L57)）；`api.md` 计数「46」自洽（[api.md:184-234](api.md)）；93xx 现占 `9301~9306`（[ErrorCode.java:194-207](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L194-L207)）；`HrFlowController` 10 端点、`HrProfileController` 6 端点（B1-C1=16 成立）。**§3.5/§11.10 端点算式自洽：62(§4.0+§7) + 83(§4 缺口) + 6(概览补录) = 151**。

**不满足项（错源结论）：** 方案称「现最大版本 V15（`mysql/` 与 `postgresql/` **双目录各至 V15**）」（§0.4:61）与「双目录」（§7:564、§7:584）。**实测**：`db/migration/postgresql/` **仅 `V1__init_schema.sql`、`V2__init_data.sql`**（无 V3~V15）→ 该结论**事实错误**。

### 2.2 维度 2 · 边界与模块合理性

**满足项：** 状态机（`SUBMITTED/APPROVED/REJECTED/EXPIRED`，`CANCELLED` 保留不用）清晰（§1.2）；申请侧与流程侧职责分离论证充分（§1.2 说明、§2.1 三选一）；并发以 `SELECT ... FOR UPDATE` 锁 `hr_flow` 单行 + 固定加锁顺序（§4.5、§11.7:851）；事务全成功/全回滚（§4.3:464、§4.4）；幂等由「状态守卫 + 行锁 + 非幂等 `HrSalaryWriter` 单次调用」收口（§4.5、§11.7:855，读盘核实 [HrSalaryWriter.java:57-118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L118) 确为非幂等）；双写点唯一在 `assignForFlow`（§11.9:891）。

**缺口（须修）：** R-6 聚合事务「按序复用 `CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY` 三步」（§4.3:464），但**未推进 `hr_flow_step`**：既有 `completeOnboardingStep` 才调 `markStepDone`（[HrFlowServiceImpl.java:132-154](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L132-L154)）。而 U-01 定稿激活「复用既有 `completeOnboardingStep` 的 `DONE` 步骤」（§11.8:860），`HrFlowStepGuard.checkOrder` 要求目标步必须为**首个 PENDING 步**（[HrFlowStepGuard.java:36-52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/support/HrFlowStepGuard.java#L36-L52)；步骤顺序见 [HrConstants.java:59-65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L59-L65)）。⇒ **按方案字面，R-6 后步骤全 `PENDING`，`DONE` 激活会被守卫拒（9303），且审批页步骤条与已产生的副作用（建档/定薪）不一致**。方案 §4.2:459 将实现细节留给后端，但未约束「步骤推进」，造成与 U-01 的自相矛盾。

### 2.3 维度 3 · NFR 覆盖度

- **安全面（只核「是否声明」）：** M-1~M-9 **逐条映射入批**（§11.2、§9 末行映射），SEC-FULL-08/18 与 M-2/M-1 合并（§11.13），SEC-FULL-09 登记为**公网放行前必闭环关联项**（§11.14）。**声明齐备**；实质安全结论归安全工程师。
- **可行性质疑已核，结论：技术可行**——MySQL 生成列 `phone_active ... STORED` + `UNIQUE`、PG 部分唯一索引、发码「未注册号静默成功」均可实现。
- **未覆盖缺口（须补）：** ① **V16 存量预检 SQL**（§11.6②:819-833）在 `is_deleted=0 且 phone IS NULL` 多行时，MySQL `GROUP BY phone` 会把 NULL 归一组使 `COUNT(*)>1` → **误报「重复号」**，可能无谓阻断迁移（生成列对 NULL 不去重）。② R-6 step 不一致（见 2.2）。
- 性能（§6：低频写、<5000 行）、可用性（短信 `provider=none` 时注册不可用，已声明为 fail-closed）、可观测性（无码不入日志）、可维护性（仅 1 新表 + 3 结构变更、参数外置）——**声明合理**。
- 后端类结论：**未运行**（本机无 JDK）。

### 2.4 维度 4 · 复杂度与可行性

- **新表 `employee_registration` 必要性：论证充分**（§2.1 三选一，逐条驳回「只复用 `hr_flow`」方案②的三项缺口：密码散列、查询凭据、注册审计）。**结论：必要**。
- **双写岗位必要性：** 依用户裁定①；引入一致性维护面（E6）但已限定权威与单点写入（§11.9）→ 可接受。
- **`api.md` 一次性补录 83 端点：范围与风险合理**——拆 C1~C6、分 P0~P2、验收「Controller 映射差集=0」；但 C2~C5 端点数（15/18/22/12）**本轮未逐一核验**，收敛到 B1 验收。
- 与迭代阶段匹配（一期不引入微服务/分布式事务），无超前设计。

### 2.5 维度 5 · 假设与风险登记

风险表齐（E1~E9、S1~S9、R-1~R-6、T1~T12），新增 E6（双写一致性）/E7（存量空值）/E8（前端改造面）与 SEC-FULL-08/09 已登记。**遗漏 4 项（须补登）：**

| # | 遗漏风险 | 依据 |
| --- | --- | --- |
| G1 | R-6 未推进 `hr_flow_step` → 激活路径被守卫阻断 / 步骤条与副作用不一致 | 2.2 |
| G2 | PG 目录冻结与「双库脚本」要求冲突（未登记） | 2.6 |
| G3 | 提交段 `9307` 仍是「该手机号存在进行中注册」的存在性 oracle（低危：需有效短信码，即须持有该号） | §3.2:303、§3.3:385 |
| G4 | V16 预检 SQL 对 `NULL phone` 误报 | 2.3 |

### 2.6 维度 6 · 既有契约一致性

| 契约 | 结论 | 证据 |
| --- | --- | --- |
| `api.md` | **一致** | 体例/路径前缀/93xx 续号（`9307~9309`，`9310` 废弃）一致；§2.1 段位 `93xx=人事/入离职` 与 [api.md:87](api.md) 一致；§2.2 明细 93xx 尚未展开（[api.md:92](api.md)）与方案 §3.5-A 一致 |
| `db.md` | **不一致（未声明冲突）** | `db.md:8`「**MySQL 8.0 单库**（`postgresql/` 目录**冻结不再维护**）」、`db.md:402`「`postgresql/init.sql` 冻结…`spring.flyway.locations={vendor}` 只会选中 `mysql/`」⇒ 方案 §0.4:61 / §7:564,568,584 / §9:669 强制 **pg 脚本 + pg 快照同步 + 「双目录各至 V15」**，**既与 `db.md` 冲突又未声明处置**。其余偏差（D7 例外「`employee.phone` 活跃唯一 / `apply_no` 唯一」、`hr_flow.status=REJECTED` 复用、新增列）**已显式登记**，可接受 |
| `PublicEndpoints` | **一致** | 净新增 1 条（R-2）、R-1 复用、R-3 ADMIN、R-4/R-5 取消，与现白名单 6 条（[PublicEndpoints.java:45-47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L45-L47)）无冲突 |
| 错误码段位 | **一致** | 93xx 现 `9301~9306`，新增 `9307~9309` 续号不重叠 |
| 既有 `hr_flow`/`employee` 语义 | **一致** | 沿用；`employee.position` 为新增列（用户授权） |
| `tech-review-full-20260926.md` 必改项 | **无冲突** | TR-M1 并入 §3.5/B1；TR-M5 落为批次验收；TR-M2 归安全、不纳入 |

---

## 3. 特别核查结论（独立读盘）

1. **迁移 V16~V19（顺序/双库/回滚/改历史/快照）**：顺序 `V16→V17→V18→V19` 合理、依赖成立；**未改历史脚本**；每脚本含回滚语句；快照与 `db.md` 同步点列明。**但「双库(pg)」前提错误**——PG 目录实测仅 `V1,V2` 且 `db.md:8` 明确 PG 冻结 ⇒ **不成立，须修正**（必改项 1）。
2. **手机号活跃唯一（MySQL/PG）**：实现**技术可行**（生成列 + `UNIQUE` / PG 部分唯一索引）；存量预检 SQL **正确但对 NULL 误报**（须加 `phone IS NOT NULL`）；迁移失败路径（预检非 0 → 先人工去重、C 档授权）**明确**。
3. **M-2 与 U-03 是否矛盾**：**无矛盾**。v1.2 已定稿 **U-03 = 废弃 `9310`**、提交段亦恒定（M-2 严格形态，§11.4:752、§3.3:388、§3.4:402）。任务描述「U-03 保留 9310 查重码」为**旧版口径**，与 v1.2 不符（事实性纠正）。
4. **批次表可独立验收 / 依赖 / C 档**：B0~B7 逐批含可判定验收标准；依赖顺序（安全基线→契约→DB→后端→UI/UX→前端→测试/部署）成立；C 档项（B2 迁移、B7 去重/Nginx/部署）已标注。**唯一缺口**：B4 未含「`hr_flow_step` 推进 / `DONE` 激活可验证」（与 2.2 同源）。

---

## 4. 必改项清单（可判定）

**必改项 1｜PG 前提错误 + 与 `db.md` 冲突未声明 —— 阻塞（打回主因）**
- 依据：`db.md:8`（MySQL 单库、`postgresql/` 冻结）、`db.md:402`（pg init.sql 冻结、`{vendor}` 只选 mysql）；实测 `db/migration/postgresql/` 仅 `V1,V2`。方案 §0.4:61 / §7:564,568,584 / §9:669。
- 验收标准：删除或更正「`postgresql/` 双目录各至 V15」，并**二选一并写明**：(i) 本方案**仅出 mysql 脚本**（对齐 `db.md` 的 PG 冻结决策），`§7` 的 PG 列、`§3.5` 的 pg 快照同步、`§9 B2` 的「双库脚本齐」一并删改；或 (ii) **显式声明「解冻 PG」**并给出授权依据、补齐 V3~V15 pg 处置说明、登记 `db.md` 变更。
- 复核方式：读 §0.4/§7/§9 对应行；`LS db/migration/postgresql/` 与 `db.md:8/402` 比对。
- 阻塞：**是**。

**必改项 2｜R-6 未推进 `hr_flow_step`，与 U-01 激活路径矛盾 —— 阻塞**
- 依据：`markStepDone` 仅在 `completeOnboardingStep` 调用（[HrFlowServiceImpl.java:152](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L152)）；`HrFlowStepGuard.checkOrder` 要求目标步=首个 PENDING（[HrFlowStepGuard.java:47-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/support/HrFlowStepGuard.java#L47-L50)）；方案 §4.3:464、§11.8:860。
- 验收标准：明确 R-6 是否/如何将 `SUBMIT_MATERIALS/HR_REVIEW/CREATE_ACCOUNT/ASSIGN_STATION/SET_SALARY` 置 `DONE`，并保证 U-01 激活路径**可执行**（`DONE` 不被 step guard 拒）；审批页步骤条与副作用一致。`§9 B4` 增验收：审批后 `hr_flow_step` 状态可核对、`DONE` 激活成功。
- 复核方式：读 §4.2/§4.3/§11.8 与 B4 验收表，与 `HrFlowServiceImpl`/`HrFlowStepGuard` 比对。
- 阻塞：**是**。

**必改项 3｜V16 存量预检 SQL 对 `NULL phone` 误报 —— 非阻塞（同上批）**
- 依据：§11.6②:819-833；`is_deleted=0 且 phone IS NULL` 多行会因 `GROUP BY phone` 归一组而 `HAVING COUNT(*)>1`。
- 验收标准：预检条件补 `phone IS NOT NULL`（或等价），避免误报阻断迁移。
- 复核方式：读 §11.6② SQL。
- 阻塞：否。

**必改项 4｜风险登记补漏 —— 非阻塞**
- 依据：§8.3 / §11 风险表。
- 验收标准：补登 G1（step 一致性）、G2（PG 冻结冲突）、G3（`9307` 差分，低危）、G4（NULL 预检）。
- 复核方式：读 §8.3/E 系列与 §11 风险表条目。
- 阻塞：否。

---

## 5. 报审须声明事项

1. 本结论绑定 `registration-design.md` **v1.2**；方案版本变更须重评。
2. **安全面结论归网络安全工程师**（`security-registration-review.md`「阻断（公网）」+ SEC-FULL-08/09），本报告只评工程面，**不出安全结论、不代放行**。
3. 后端类结论均属**静态判定**：本机无 JDK/Maven/MySQL/Redis → 编译/单测/迁移**未运行**，收敛到服务器 `mvn -q clean package` + `mvn test` + Flyway 空库迁移并归档。
4. 结论「打回」→ **不得报主智能体审批**；修订后**必须重评**（评估范围冻结，仅核必改项，不新增无关要求）。
5. 授权与放行归主智能体（§10.3）；涉 C 档（迁移/去重/Nginx/部署）须其授权且先取安全结论（P0.5/L7）。

## 6. 复评记录

（本轮为初评，结论**打回**。复评时按第 4 节必改项逐条核对；必改项 1、2 未闭环则维持打回。）

---

## 复评（v1.3）

| 项目 | 内容 |
| ---- | ---- |
| 复评对象 | [registration-design.md](registration-design.md) · **v1.3（1039 行；新增 §4.2.1、§8.4、§12）** |
| 复评方 | `express-station-tech-reviewer`（**未参与本方案产出**，评估方与产出方分离） |
| 日期 | 2026-09-26 |
| 复评范围 | **冻结，仅核 M1~M4 是否真闭环**；**独立读盘取证，不采信自述** |
| 评审方式 | 静态文档级 + 独立对码/对盘。**本机无 JDK/Maven/MySQL/Redis → 后端类结论一律「未运行」** |
| **复评结论等级** | **通过（工程面）** —— M1~M4 全部闭环、无剩余必改项 |
| 准入结论 | **可报主智能体审批**（P0.6/L8；报审须附第 7 节声明事项） |

> 本结论为**工程面技术质量**判定；安全实质结论归网络安全工程师，本报告**不出安全结论、不代改方案、不代授权、不执行 git**。

### 5. M1~M4 闭环核对（独立取证）

| 必改项 | 结论 | 证据（独立读盘 / 对码） |
| --- | --- | --- |
| **M1［阻塞］PG 前提 → 仅 MySQL** | **已闭环** | ①[§0.4:61](registration-design.md) 已改「快照唯一同步点 = `sql/schema/mysql/init.sql`」+ **显式声明**「PostgreSQL 迁移目录自 V2 起冻结，本期不产出 pg 脚本与 pg 快照」（引 `db.md:8`/`db.md:401`）；②[§7:588](registration-design.md) 表头/各行全「仅 mysql」+ 冻结声明；③残留 PG 字样均在**删除线或「冻结/留档」语境**（[§2.2:181](registration-design.md)、[§11.4 U-05:791](registration-design.md)、[§11.6①:851](registration-design.md)、[§11.9:924](registration-design.md)、[§11.12:977](registration-design.md)）；④快照同步点仅 `sql/schema/mysql/`（[§7:608](registration-design.md)）、[§9 B2:705](registration-design.md)「仅 mysql 脚本齐（pg 冻结…）」；⑤**独立 `LS` 实测**：`db/migration/postgresql/` 仅 `V1__init_schema.sql`、`V2__init_data.sql`；`mysql/` 为 `V1~V15`；`db.md:8`「MySQL 8.0 单库…`postgresql/` 目录冻结」、`db.md:401`「`postgresql/init.sql` 冻结…`{vendor}` 只会选中 `mysql/`」——**方案与 `db.md` 一致** |
| **M2［阻塞］R-6 推进 step** | **已闭环（口径在代码上可执行）** | [§4.2.1:460-482](registration-design.md) 定稿「单事务按序推进 **5 步** `SUBMIT_MATERIALS→HR_REVIEW→CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY`，逐 step 复用 `completeOnboardingStep`、**不含 `DONE`**」+ 4 项不变式。**独立对码**：[completeOnboardingStep:143-153](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L143-L153) switch `default`（提交资料/人事审核）仅走 `markStepDone`；[markStepDone:474-499](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L474-L499) 每步置 `DONE` 并重算 `currentStepKey=firstPending`、仅当无 PENDING 且 `IN_PROGRESS` 才置 `COMPLETED`；[HrFlowStepGuard.checkOrder:47-48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/support/HrFlowStepGuard.java#L47-L48) 要求**目标步 = 首个 PENDING**。⇒ 推 5 步后 `DONE` 为唯一 PENDING、`current_step_key=='DONE'`、`status=='IN_PROGRESS'`，激活**可执行**，口径自洽。**B4 验收**（[§9:707](registration-design.md)）已补**可判定断言**：`5 步 == DONE` + `current_step_key=='DONE'` + `status=='IN_PROGRESS'` + `DONE` 仍 `PENDING` + `completeOnboardingStep(DONE)` 无 9303 → `employee.status==1` |
| **M3［非阻塞］预检 SQL NULL 误报** | **已闭环** | [§11.6②:857-866](registration-design.md) 预检条件已补 **`AND phone IS NOT NULL`**（PG 留档写法同步）；修因（`GROUP BY phone` 对 NULL 归一组致 `COUNT(*)>1`）已列 |
| **M4［非阻塞］G1~G4 补录** | **已闭环** | 新增 [§8.4:674-683](registration-design.md)：**G1 已闭环(M2)、G2 已闭环(M1)、G3 登记为残余（低危）、G4 已闭环(M3)**；并声明 G 系列与 E/S/R/T 编号不冲突 |

### 6. 另需确认 —— U-03 旧口径未被回改

**未被回改，正文保持「`9310` 废弃」**：[§11.4 U-03:789](registration-design.md)「废弃 `9310`」、[§3.3:388](registration-design.md) 删除线标「废弃（M-2/U-03）」、[§3.2:320](registration-design.md) 错误码列「**无 9310**」、[§3.4:304](registration-design.md)「不返回 `9310`」、[§8.1 Q7:632](registration-design.md)、[§7 Mock 同步:613](registration-design.md) 均一致；[§12:1037](registration-design.md) 已把「上轮口述『U-03 保留 9310』」登记为**旧口径/事实性纠正**。**确认：本方案未因上轮口述被回改**。

### 7. 报审须声明事项（随报告出具，供主智能体决定是否受理）

1. 本结论绑定 `registration-design.md` **v1.3**；**方案版本变更须重评**（P0.6/L8）。
2. 本结论为**工程面**判定；**安全面结论归网络安全工程师**（`security-registration-review.md`「阻断（公网）」+ SEC-FULL-08/18）。**SEC-FULL-09 仍待闭环**（首登强制改密仅前端实现，[§11.14:988](registration-design.md)）——属**公网放行前必闭环的放行前置**，不在本方案实现范围，**不属本复评工程必改项**；本报告不代安全结论、不代放行。
3. 后端类结论**未运行**：本机无 JDK/Maven/MySQL/Redis → 编译/单测/迁移收敛到服务器 `mvn -q clean package` + `mvn test` + Flyway 空库迁移，结果归档 `docs/`（tech-review-full TR-M5）。
4. **Flyway 迁移未执行**：V16 预检须返回 0 行方可执行；迁移执行属 **C 档**（须主智能体 §10.3 三步授权，且先取安全结论 P0.5/L7）。
5. **报审通过 ≠ 放行**：C 档项（B2 迁移、B7 存量去重/Nginx/部署）仍须主智能体授权；授权与放行归主智能体。

### 8. 新发现的不一致

1. **[轻微·引用] 行号偏移（不影响方案）**：上轮报告/派发引 `db.md:402`，而「`postgresql/init.sql` 冻结」实为 `db.md:401`（`:402` 为快照一致性核对）。被评对象 [§0.4:61](registration-design.md)/[§7:588](registration-design.md) 引 `db.md:401` **正确**，**无缺陷**，仅作留痕。
2. **[无] 工程面**：被评对象 v1.3 未发现新的工程面不一致；§4.2.1/§8.4/§12 与 §4.3/§11.7/§11.8/§9-B4 口径一致，无自相矛盾。

---

> **复评范围冻结声明**：本节仅核 M1~M4 是否闭环，**未新增无关要求**。M1~M4 全部闭环、无剩余必改项 ⇒ **复评结论：通过（工程面）**，可进入报审流程（须附第 7 节声明事项）。
