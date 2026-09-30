# 技术评审报告：打卡时间真源统一（班次为唯一时间真源）

| 项目 | 内容 |
| ---- | ---- |
| 评估对象 | `hrm-dev/docs/attendance-time-source-design.md`（**v1.0 初稿**，2026-09-27，483 行；真源方案选定 **(a) 读时派生**） |
| 产出方（作者） | 架构师 `express-station-architect` |
| 评估方 | 技术评审工程师 `express-station-tech-reviewer`（**独立于产出方**，只出结论与必改项，不产出方案、不代改、不代授权） |
| 评估日期 | 2026-09-27 |
| 取证方式 | **静态文档级 + 源码逐条取证**：全量检索 `check_periods`/`checkPeriods`/`periodIndex`/`period_name`/`periodName`/`work_start_time`/`workStartTime` 的消费点（`hrm-server/src/main` + `src/test`）并逐条读源码；对照 `algorithm-multi-shift-scheduling.md` v1.1、`boss-management-architecture.md` v1.1、`boss-management-ui-design.md` v1.1、`api.md` v1.4、`db.md` v2.6、`update-log.md`、`algo-payroll-shift.md` v2.0、seed/DDL |
| 本机能力声明 | **无 JDK / 无 MySQL / 无 Maven**：**未编译、未运行任何单测/接口/E2E、未连库、未执行 SQL**。所有结论为静态源码与文档取证，**未跑过任何构建或测试**；涉数据分布的判据标「待核实（需 P-4/P-2/P-3 实跑）」 |
| 评估边界 | 只评**工程面技术质量**（六维）。**不做安全实质判定**（漏洞/攻击面/鉴权归网络安全工程师，本报告仅核对「安全面是否声明」）；**不裁定计费/提成/SLA/口径**；**不评视觉**；评估结论**绑定本文版本**，方案版本变更须重评 |
| 结论等级 | **打回**（详见 §六） |

---

## 一、评估范围与取证方式

1. 被评对象：`hrm-dev/docs/attendance-time-source-design.md`（全文 483 行，已逐行精读）。
2. 独立核验（不看方案自述、只认磁盘现行版本）：`AttendancePeriodResolver`、`AttendanceRecordServiceImpl`、`AttendanceSummaryPolicy`、`ShiftPayrollPolicy`、`AttendanceMakeupServiceImpl`、`AttendanceRuleServiceImpl`、`AttendanceShiftServiceImpl`、`AttendanceScheduleServiceImpl`、`AttendanceCheckPolicy`、`AttendanceConstants`、`AlgoProperties`、`ErrorCode`、实体 `AttendanceRule/AttendanceShift/CheckPeriod`、DDL `V5__attendance.sql`、seed `kdyzgl_test_seed.sql`、控制器前缀。
3. 契约对照：`api.md` §4.6 / §4.7 / §4.8（行 795–1099）、§4.6.12（行 996–1007）；`db.md` 行 619–624、1687；`boss-management-architecture.md` 行 102–107、450–457；`algorithm-multi-shift-scheduling.md` §1.3.1/§1.8/A4/FC10；`algo-payroll-shift.md` 行 26–70、92–98、129–135、336–360；`update-log.md` 行 40–56、629–635。
4. 前端对照：`web/.../RuleCard.vue`、`boss-h5/.../attendanceRule.vue`、`staff-h5/.../PeriodCard.vue`、`packages/mock`。

---

## 二、八项核对结果（逐项，含 `文件:行号`）

### 核对 1 · 真源方案 (a) 的读取点穷尽性（本次核心）

**范围**：全量检索后端 `main` + `test` 的全部消费点，逐条判定「已覆盖 / 遗漏 / 不适用」。**结果：已覆盖 20 处 / 遗漏 2 处 / 不适用 3 类。**

**（1）`AttendancePeriodResolver.resolve(rule)` 调用者（方案称「3 读取点」）——已覆盖 4 处调用 / 3 个逻辑点**

| # | 位置 | 用途 | 判定 |
| - | ---- | ---- | ---- |
| 1 | `AttendanceRecordServiceImpl.java:316`（`checkIn`） | 取时段起止做时间窗/迟到早退、写 `period_index/period_name`（`:385-386`） | 已覆盖（§0.1-6、§4） |
| 2 | `AttendanceRecordServiceImpl.java:447`（`todayStatus`） | `status.periods` 逐时段展开 | 已覆盖（§0.1） |
| 3 | `AttendanceMakeupServiceImpl.java:121`（`apply`） | 校验时段存在、写 `periodIndex/periodName`（`:138-139`） | 已覆盖（§0.1 补充、§7.6） |
| 4 | `AttendanceMakeupServiceImpl.java:208`（`canWriteRecord`） | 审批回写：取规定时间（`:224-225`）、写记录（`:231-232`） | 已覆盖（§0.1 补充、§7.6） |

**（2）`work_start_time`/`work_end_time` 派生值读取点——已覆盖 4 处**

| # | 位置 | 用途 | 判定 |
| - | ---- | ---- | ---- |
| 5 | `AttendancePeriodResolver.java:68-69` | 规则无时段时兜底段的起止 | 已覆盖（随解析器改造） |
| 6 | `AttendanceRecordServiceImpl.java:344-345` | 单班次模型无排班时的回退时间基准 | 已覆盖（属读取点①内部） |
| 7 | `AttendanceRuleServiceImpl.java:146-147`（`toVO`） | `GET /rule` 出参 | 已覆盖（§5） |
| 8 | `AttendanceShiftServiceImpl.java:146-147`（`defaultShift`） | 兜底班次起止 | 已覆盖（§8.4 注）——**但未计入「3 读取点」，§9.4 回滚亦未列**（见必改 M5） |

**（3）`check_frequency` 读取点——已覆盖 4 处**

`AttendanceRecordServiceImpl.java:469-472`（todayStatus 出参 + requireSummary）、`AttendanceRuleServiceImpl.java:142`（toVO）、`:421`（newRuleDefaults）、`:96/201-237/296-320/374-379`（saveRule 校验与写入）。判定：已覆盖（§3.2、§5、§7.1、§10.3）。

**（4）`check_periods` 写/读/校验——已覆盖 8 处**

`AttendancePeriodResolver.java:59`、`AttendanceRuleServiceImpl.java:96/100-102/143/201-237/296-320/374-379/386-431`、`AttendanceRule.java:66-68`、`AttendanceRuleRequest.java:33`、`AttendanceRuleVO.java:34`、`CheckPeriodListTypeHandler`。判定：已覆盖（§5、§7.1、§7.2、§10.3）。

**（5）`period_index`/`period_name` 的其它消费点——已覆盖 7 处，遗漏 1 处**

已覆盖：`AttendanceRecordServiceImpl` 的 `export:126`、`summary:178`、`buildDetailRow:655`、`toVO:723-724`、`validCard:581`（§4.2 已引 `:573-585`）；`AttendanceSummaryPolicy.summarizeByShift`；`ShiftPayrollPolicy.attendedShiftSet:168-186`。

**遗漏 1（登记类，非必改代码，但属「穷尽性」缺口）**：`PayrollContextProvider.java:92,121-122`——**计薪侧**读取 `period_index/period_name` 并构造 `ShiftPayrollPolicy.RecordRow`，是三态映射与 R11/R10 风险的**真正消费方**。方案 §4/§10 讨论了 `ShiftPayrollPolicy`，却**未在读取点/影响面清单登记其调用方** `PayrollContextProvider`（§13 依据索引亦无）。该处**无需改代码**（透传），但穷尽性核对必须显式列为「不适用（透传，语义随写入侧改善）」——否则无法证明「读取点已穷尽」。

**遗漏 2（登记类）**：`src/test/.../AttendancePeriodResolverTest.java:44-57` 构造无 `checkPeriods` 的 rule 调 `resolve(rule)`。B1 若改签名/语义，**测试须适配**；方案未登记测试改动面。

**（6）不适用 3 类**：`hrm-clients/packages/mock`（前端 Mock 为**独立真源**，`api.md:996-1007` 已登记分叉先例）、`hrm-demo` 镜像（U-7 裁定范围）、其它工程/文档正文（非消费点）。

**结论**：方案「读取点全集仅 3 处」的表述**仅在「`resolve(rule)` 调用者」这一狭义口径下成立**；作为**影响面**它**不完整**——另有 4 处派生值读取点、1 处计薪侧读取点未纳入同一清单。§9.4/§10.5「回滚 = 反向 diff 3 读取点」因此**低估改动面**（必改 M5）。

### 核对 2 · `period_index` 语义（= `shiftOrdinal`）核验

- **方案结论成立**：单班次晚班站点若 `period_index` 取数组下标 `0`，则 `shiftUnit(day,0)`（早班单元）与员工当日 `R={shiftUnit(day,1)}` 交集为空 → 判旷工、折算少算。依据 `ShiftPayrollPolicy.shiftUnit:54-56`、`shiftOrdinal:64-79`、`attendedShiftSet:183-186`、`compute:242-288`（`prorated = base×|A∩R|÷|R|:297-306`）。`period_index = ordinal` **语义正确**。
- **但方案举证不完整（高危，见核对 3/M1）**：`period_index = ordinal` 会使 `status.periods[]` 的 `periodIndex` **非连续**（单晚班站点唯一项 = 1）。方案 §4.2（行 134）主张「前端/补卡均**以值为键**、不受非连续影响」，**与实测不符**：`AttendanceRecordServiceImpl.java:320` 与 `AttendanceMakeupServiceImpl.java:122-123、210-212` 均为 **`periods.get(periodIndex)` 数组下标取值**。在方案自己举证的「单班次晚班站点」下（列表 size=1、`periodIndex=1`），`1 < 1` 为 false → `period=null` → **9107**，**打卡与补卡同时失效**。这是**方案自述与实测不符**，且**影响正确性**。
- **存量纯晚班站点错配的「读取零变化」**：`update-log.md:54`② 已登记「写入侧 `period_index` 单班次恒写 0，纯晚班站存在 `period_index(0)` 与 `shiftOrdinal(1)` 错配，读取侧由三态映射兼容」。方案 §4.3/§10.2 的结论是**有条件的**：仅当存量 `period_name` 命中哨兵 `全天班` 或为空时「读取零变化」；**非哨兵非空**情形（如「上午班」+`period_index=0`）在纯晚班排班日会经三态③映射到早班单元 → 交集为空。方案已把它列为 **V-1 待核实**（`§12.2`）并配 P-4 预检，**处置正确**；**但 §0 摘要第 5 条「存量读取零变化」未加此限定**，表述**过度**（见发现项 L2）。seed 实测：`attendance_record` 仅投城东驿站（`kdyzgl_test_seed.sql:224-225`），其规则时段名为 `全天班`（`:227`）→ 命中哨兵，故 seed 样本下读取零变化成立。

### 核对 3 · `period_name` 改取班次名的影响面（潜在高危点）

`period_name` 消费点：`ShiftPayrollPolicy.attendedShiftSet:160-187`（三态优先级）、`AttendanceSummaryPolicy.summarizeByShift:87-88`（→ RecordSlot）、`AttendanceRecordServiceImpl` 的 `export:126`、`buildDetailRow:655`、`toVO:724`、`summary:178`。

**高危结论：哨兵名冲突会使「实到」虚增，方案未防护。**
- `legacyPeriodSentinel` 默认值 = **`全天班`**（`AlgoProperties.java:123`），且 `AttendanceConstants.DEFAULT_PERIOD_NAME` 亦为 `全天班`（`:65`）。
- `ShiftPayrollPolicy.attendedShiftSet:168-171` 分支①：`period_name` 命中哨兵 → **`attended.addAll(keys)`（覆盖当日全部排班班次）**。
- 方案把 `period_name` 改为 `attendance_shift.shift_name` 快照后，`attendance_shift.shift_name` 仅校验「1-20 字符」（`AttendanceShiftServiceImpl.java:157-159`），**不禁止命名「全天班」**。若管理员把某班次命名为「全天班」，则该班次的新打卡记录 `period_name='全天班'` → **被误判为历史哨兵** → 覆盖当日**全部**班次 → **实到虚增、旷工罚款与折算基数被吞**（本应只计 1 个班次）。
- 方案**全文未提及**该冲突（§4.3 只讨论存量记录；§6.3 只讨论 `12:00` 边界与跨零点）。此为**正确性缺陷**（必改 M2）。

其余影响面：导出「时段名称」列值域变化（§7.5 已提文案建议，列数 13 不变，`AttendanceRecordServiceImpl.java:75,117-118`）；明细 `periodName` 取代表卡快照（`:655`）保持快照语义（§7.5 正确）。`period_name VARCHAR(20)` 与 `shift_name VARCHAR(20)`（`V5__attendance.sql:89,54`）等宽，**无截断风险**。

### 核对 4 · 打卡次数派生与边界

- **「0 启用班次」现状行为**：受评现状下，规则可**独立于班次**存在——`AttendancePeriodResolver.resolve:58-72` 在 `checkPeriods` 为空时回退为单段「全天班」，起止取 `work_start_time/work_end_time`；`checkIn` 单班次模型亦回退到 `rule.getWorkStartTime/End()`（`:344-345`）。故**现状「无启用班次」仍可打卡**。方案改为 **9113 拒绝**，确属**行为变更**（§10.3 已自认「新边界」）。影响存量站点数**未量化**：seed 中 8 站均有 2 个启用班次（`kdyzgl_test_seed.sql:237-253`，早/晚 `status=1`），故 seed 样本下 **P-1=0**；真实库分布须走 **P-1 预检**（方案未给数，属 §12.2 V-2）。**判定：方案已登记为行为变更，但缺「影响站点数」与「上线前置」绑定**（见必改 M6）。
- **与 U-2 自洽性**：U-2 裁定「员工当天无排班仍可打卡（时段取站点班次）」。方案 D-3 派生粒度 = **站点级启用班次**（与员工排班无关），故「站点有班次、员工无排班」时：`status.periods` 仍展开站点班次、`period_index=ordinal`（早 0/晚 1）、打卡走时段模型。**与 U-2 自洽 ✓**。`status.shift` 仍取「今日排班班次，无则兜底」（`:474-478`），兜底改由「首个启用班次」派生（§8.4 注）亦自洽。
- **>2 班次**：`shiftUnit = epochDay×2+ordinal` 仅容 2（`AttendanceScheduleServiceImpl.java:64-70`、`AlgoProperties.java:183`）。方案「不静默截断、班次定义侧拒绝」**方向正确**；但落点（U-6）未定，且现有 `AttendanceShiftServiceImpl.validateShift:157-177` **确无条数/ordinal 校验**（实测确认方案 §6.3「班次定义数>2 无校验」成立）。
- **停用不计入**：`AttendanceShiftServiceImpl.list:44-58` 经 `@TableLogic` 排除软删，但**不过滤 `status=0`**（实测确认方案 §3.2 描述准确）；派生集合须自行过滤 `status==1`。

### 核对 5 · 契约变更清单核验（新增 1 + 变更 6 + 复用 2）

- **计数自相矛盾（必改 M4）**：§7.8 记「新增 1」，但 §7.2 拟新增 `checkPeriodsReadonly`、§7.4 拟新增 `shiftConfigured`——**两处新增字段未计入计数**。
- **路径错误（必改 M4）**：§7.8/§7.7 写「复用 `GET/POST/PUT/DELETE /api/v1/attendance/shifts`」，**实测路径为 `/api/v1/shifts`**（`ShiftController.java:28`，`api.md` §4.8）。方案 §7.8 另一处写 `/shifts` 正确，**两处不一致**。
- **方法误记（必改 M4，低）**：§7.6 标题「`POST/PUT /api/v1/attendance/makeup*`」——实测两接口均为 **POST**（提交 `POST /makeup`、审批 `POST /makeup/{id}/approve`，`api.md:1034,1049`）。
- **入参废弃致旧客户端不兼容（必改 M6，中）**：实测 **web** `RuleCard.vue:266` 与 **boss-h5** `attendanceRule.vue:155` 仍在提交 `checkPeriods`；**staff-h5 不调 `PUT /rule`**（无提交体）。若 U-5 取 (ii)「收到 `checkPeriods` 非空 → 400」，且 B1 先于 B2 上线，则**现网规则保存全面 400**。方案未有发布顺序约束。
- **出参核对**：`GET /rule` 现状 `checkPeriods` 为「唯一真源」（`api.md:819,838,880-888`）、`workStartTime/workEndTime` 为派生（`:821,853-854`）；方案 §7.1/§7.2 对其**语义改派生**的描述与实测方向一致。`GET /status.periods` 字段形态（`api.md:915`）与 §7.4「出参形态不变、值改派生」一致。

### 核对 6 · R11 是否真的消除

- **出处核准（架构师订正成立 ✓）**：**R11 定义在 `boss-management-architecture.md:457`**（`period_index ↔ ordinal` 对齐依赖不变式 A2、当前无强制校验），非 `update-log.md:42`（`:40-53` 为 B7b 记录）。架构师本次订正**正确**。
- **消除论证**：R11 根因 = 存在两套独立顺序（`check_periods` 数组序 vs 班次 `start_time`+界值派生的 `ordinal`）。D-4 使 `period_index` **直接由班次计算** → 「规则顺序」这一独立来源消失 → **根因消除，成立 ✓**。
- **充要条件表述不严谨（中）**：方案称「当且仅当站点启用班次 `ordinal` 互异」。严格说，`ordinal` 互异是**避免 `period_index` 值冲突（R10 型）**的条件，**不是 R11 消除的条件**（R11 由构造消除，与 ordinal 是否互异无关）；二者被混为一谈。
- **残余风险等级未定（中）**：方案自认「班次定义侧尚无该约束，需新增」。实测**缺失约束时的残余风险为真**：站点若定义 2 个同半天班次（如两个早班），派生集合会产生**两个 `periodIndex=0` 的时段** → `status.periods` 出现重复键 → 前端 `slotKey`（`attendanceUi.js:32`）冲突、打卡/去重错乱；且 `9112` 只在**排班**侧生效（`AttendanceScheduleServiceImpl.java:253-256,526-540`），**拦不住「只定义不排班」**。方案未对此残余风险**定级**（应为「中高，直至班次侧校验落地」）。另：`ordinal` 判定存在**两处实现**（`ShiftPayrollPolicy.java:75` 与 `AttendanceScheduleServiceImpl.java:535`），班次侧新增校验须复用同一实现以防第三处漂移——方案未声明。

### 核对 7 · 迁移与回滚

- **「(a) 下 `V24` 非功能必需」成立 ✓**：实测 `check_periods` 为 `JSON DEFAULT NULL`（`V5__attendance.sql:35`），**无 NOT NULL、无默认值、无索引/唯一键牵连**；`period_index INT NOT NULL DEFAULT 0`、`period_name VARCHAR(20)`（`:88-89`）均不因「保留列」受阻。**无隐藏结构依赖**。与 D-6（`boss-management-architecture.md:104`）、Q-DB-11（`db.md:1687`）一致。
- **预检判据 P-1~P-5 可执行性**：P-1（有规则无启用班次）、P-2（启用班次>2）、P-3（同站 `ordinal` 重复）、P-4（`period_name` 非哨兵非空分布）、P-5（`start_time=720` 边界）均为**可判定的集合查询**，判据明确 ✓。但均为**「描述 + 查询」**，未给可粘贴 SQL（属方案边界，可接受）；P-4/P-2/P-3 结论**须实跑**（本报告未跑，标待核实）。
- **回滚路径「3 读取点」不完整（必改 M5）**：`§9.4/§10.5` 只反向 diff「3 读取点 + `AttendancePeriodResolver` + `saveRule` 重算」，**遗漏同批 B1 的** `AttendanceRuleServiceImpl.toVO:143,146-147`、`AttendanceShiftServiceImpl.defaultShift:140-152`、`newRuleDefaults:386-431`。若这些已改而回滚不含，回滚后 `toVO`/兜底班次仍走派生路径，**与 `check_periods` 真源不一致**。
- **不改历史事实 ✓**：`attendance_record` 只增不改（U0-3、`V5` 注释 `:108`）；(a) 不改写 `check_periods`。

### 核对 8 · 批次与门禁

- **依赖与门禁基本可判定 ✓**：B0（本评审闸门）→ B1（后端 + 契约）→ B2（前端）→ B3（demo/mock，U-7）→ B4（数据治理，C 档）→ B5（回归）；B1′（`db.md` 注释，C 档）。B1 依赖「B0 通过 + U-1~U-7 裁定」明确。
- **存在「未过门禁即可开工/上线」的缝隙（必改 M6）**：
  1. **B1 上线前置未绑定 P-1**：B4（治理，含 P-1）排在 B1 之后。设计 §10.3 已言「上线前必须补齐班次，否则该站全员无法打卡」，但**门禁未把「P-1=0」列为 B1 的上线前置** → 存在「B1 先上线 → 有规则无班次站点全员禁打卡」的窗口。
  2. **B1/B2 发布顺序未约束**：见核对 5（U-5=400 与现网客户端冲突）。
  3. **B2 前置未锁定 U-3/U-4**：B2 门禁只写「B1 契约定稿」，但 U-3（引导落点）、U-4（频次去留）直接决定 B2 实现，**未列为 B2 前置**。

---

## 三、六维评估

| 维度 | 结论 | 证据与说明 |
| --- | --- | --- |
| **1 依据充分性** | **部分不满足** | 优点：引用普遍带 `文件:行号`，抽检 **R11→`boss-management-architecture.md:457`、D-6→`:104`、`db.md:621/623/1687`、`AttendanceShift.java:19-53`、`AttendanceRecordServiceImpl.java:316-345/385-386`、`ShiftPayrollPolicy.java:64-79/183-186`、`algo-payroll-shift.md:338`** 均**准确**；§0.1 对上游 10 条复核基本可靠、订正 1（R11 出处）成立。缺陷：§4.2（行 134）「periodIndex 均以值为键」为**证据不足导致的错误结论**（只引 `validCard` 值比较，漏引 `checkIn:320`、`makeup:122/210` 的下标取值）→ 见必改 **M1**。 |
| **2 架构与模块边界** | **部分满足** | 优点：单真源收敛（读时派生）方向正确、依赖单向、解析器集中、无循环依赖；三层（解析器/规则服务/展示）边界清晰。缺陷：① 影响面登记**漏计薪侧消费方** `PayrollContextProvider`（见核对 1）；② **班次 `ordinal` 语义属计薪域**，方案建议校验落 `ShiftManager`（attendance 域），未说明复用 `shiftOrdinal` 以防第二/第三处实现漂移（实测已有两处：`ShiftPayrollPolicy.java:75`、`AttendanceScheduleServiceImpl.java:535`）。 |
| **3 NFR 覆盖度** | **部分满足** | 性能：识别 `GET /rule/list` N+1 并给批量预取、驿站<100 可接受（§7.3/§10.4）✓。可用性：**缺降级链**——删除 `check_periods` 判定后，0 班次**直接 9113 拒绝**，无「派生失败/班次查询异常」兜底（§10.3 只登记不兜底）。可观测性：**未声明**（9113 拒绝率、派生失败日志/告警均未提）。安全：**未作声明**（§11 仅一句「涉安全面低」）——本维**只核「是否声明」**，实质判定转网络安全工程师。数据一致性：单真源无窗口 ✓，事务边界未提。 |
| **4 复杂度与可行性** | **部分满足** | 优点：三方案 (a)/(b)/(c) 对比有取舍理由、否决 (b) 的旁路论证（`PUT /shifts`、导入/seed）成立、规模与一期匹配、无过度设计。缺陷：**无 before/after 量化基准**（性能/耗时仅定性）；**可行性论证遗漏关键改造点**（值查找，M1），导致「改动最小」的结论**不成立**。 |
| **5 假设与风险登记** | **部分满足** | 优点：§10 风险表 5 类、§12 待核实 3 项、残余 R10/R11 有登记。缺陷：① **无「显式假设」清单**（如「班次名不与哨兵同名」「每站启用≤2」「存量 `period_name` 全为哨兵或空」等未逐条列出并给不成立后果）；② **哨兵名冲突（高风险）未登记**（M2）；③ 班次侧约束缺失的**残余风险未定级**（核对 6）；④ 全篇**未使用 `TODO(扩展)`** 统一标注遗留（如员工级粒度、`12:00` UI 提示）。 |
| **6 与既有契约一致性** | **部分满足** | 与 `db.md` 无结构冲突（保留 `check_periods` 与 D-6/Q-DB-11 一致）✓；与 `api.md` 语义方向一致。缺陷：① 契约变更清单**计数/路径/方法错误**（M4）；② **U-7 建议与主代理已裁定相反**（M3）；③ 与 `algorithm-multi-shift-scheduling.md` v1.1 的「存量 = 规则时段名快照（仅 `check_periods` 空才兜底哨兵）」表述，与方案 §4.3 的「现网全为哨兵」**口径需并轨说明**（方案引 `algo-payroll-shift.md:338` 的实测数据，但未与 `algorithm-multi-shift-scheduling.md` FC10 的**一般性结论**对齐，易被实现方误用）。 |

---

## 四、必改项清单（问题 / 依据 / 影响 / 验收标准）

> 格式：**问题**（引用条款/行号）→ **影响** → **验收标准**（可判定）→ **复核方式**。

**M1（高 · 正确性）** —— §4.2（行 134）「`periodIndex` 均以值为键、不受非连续影响」**与实测不符**。
- 依据：`AttendanceRecordServiceImpl.java:320`（`periodIndex < periods.size() ? periods.get(periodIndex) : null`）、`AttendanceMakeupServiceImpl.java:122-123`、`:210-212` **均为数组下标取值**；方案只引了值比较的 `validCard`（`:573-585`）与前端 `PeriodCard.vue:48`。
- 影响：在方案自己举证的「单班次晚班站点」（唯一时段 `periodIndex=1`、列表 size=1）下，打卡与补卡**均取不到时段 → 9107**，功能失效。
- **验收标准**：方案须**删除/更正**该断言，并在「3 读取点改造要求」中**显式规定「`periodIndex` → 时段按值查找（禁止 `List.get(periodIndex)`）」**；附「单班次晚班站点」的**打卡 + 补卡 + `status` 三条路径**取值算例（输入 periodIndex=1 → 命中晚班时段）。
- 复核方式：读方案对应章节 + 逐行核对 `AttendanceRecordServiceImpl.java:316-323`、`AttendanceMakeupServiceImpl.java:121-126,208-225`。

**M2（高 · 正确性）** —— 哨兵名冲突**未防护**。
- 依据：`AlgoProperties.java:123`（`legacyPeriodSentinel=全天班`）、`ShiftPayrollPolicy.java:168-171`（命中哨兵 → 覆盖**当日全部**班次）、`AttendanceShiftServiceImpl.java:157-159`（`shiftName` 仅校 1-20 字，**不禁止**「全天班」）。
- 影响：班次若命名「全天班」，新打卡记录 `period_name` 被误判为历史哨兵 → **实到虚增、旷工与折算基数被吞**。
- **验收标准**：方案须新增**防护项**（二选一或并用）：① 班次定义侧**拒绝保留名**（= `legacyPeriodSentinel`，默认 `全天班`）并写入 §8.3 `ShiftManager` 校验与 §6.3 风险表；② 或把哨兵判定限定为「升级前写入的记录」（给出可判定判据）。须附「班次名 = `全天班`」场景算例，证明**不被**覆盖为全部班次。
- 复核方式：读方案 §6.3/§8.3 新增段 + 核对 `ShiftPayrollPolicy.java:168-171`。

**M3（中 · 与已裁定口径不一致）** —— U-7 未落实。
- 依据：方案 §12.1 U-7 架构建议为「(i) 同步 demo 镜像」，§8.5 第 5 行列 `hrm-demo` 为待定同步项；与主代理已裁定「只改生产三端 + mock，**`hrm-demo` 不同步**（登记）」**相反**。
- 影响：报审口径冲突，实施方可能误改 `hrm-demo`。
- **验收标准**：方案把 **U-1~U-7 全部改为「已裁定」**并逐条对齐裁定（U-7 明确「demo 不同步、仅登记；mock 同步」），**删除与裁定相反的「建议」**；§8.5 同步修订。
- 复核方式：读方案 §8.5/§12.1，逐条比对 7 条裁定。

**M4（中 · 契约一致性）** —— 契约变更清单计数/路径/方法错误。
- 依据：§7.8「新增 1」vs §7.2/§7.4 拟新增 `checkPeriodsReadonly`/`shiftConfigured`；§7.8/§7.7 「`/api/v1/attendance/shifts`」vs 实测 `/api/v1/shifts`（`ShiftController.java:28`、`api.md` §4.8）；§7.6「`POST/PUT .../makeup*`」vs 实测均 `POST`（`api.md:1034,1049`）。
- 影响：后端按清单补录 `api.md` 会写错字段计数与路径。
- **验收标准**：§7 逐条与 `api.md` §4.6/§4.7/§4.8 + 控制器前缀一致；**变更计数重算**（新增字段单列）；路径统一为 `/api/v1/shifts`。
- 复核方式：读方案 §7.1–§7.8 对照 `api.md:800-1099`。

**M5（中 · 回滚完整性）** —— 回滚路径「3 读取点」低估改动面。
- 依据：`§9.4/§10.5` 只列「3 读取点 + 解析器 + `saveRule` 重算」，漏 `AttendanceRuleServiceImpl.java:143,146-147`（`toVO`）、`AttendanceShiftServiceImpl.java:140-152`（`defaultShift`）、`:386-431`（`newRuleDefaults`）。
- 影响：回滚后 `toVO`/兜底班次仍走派生路径，与 `check_periods` 真源不一致，出现口径分裂。
- **验收标准**：回滚清单**补全**上述 `文件:行号`，并与 B1 改动面清单**一一对应**。
- 复核方式：读方案 §9.4/§10.5 与 §11 B1 改动面比对。

**M6（中 · 发布/门禁）** —— 发布顺序与上线前置缺失。
- 依据：现网 web `RuleCard.vue:266` 与 boss-h5 `attendanceRule.vue:155` 仍提交 `checkPeriods`；B4（P-1 治理）排在 B1 之后。
- 影响：① U-5=400 且 B1 先上线 → 现网规则保存**全面 400**；② P-1 未清零即上线 → **有规则无班次站点全员无法打卡**。
- **验收标准**：方案新增「发布顺序约束」：B1 与 B2 **同批发布**（或过渡期取 U-5(i) 忽略）；**B1 上线前置 = P-1 预检为 0**；并声明 B2 前置含 U-3/U-4 裁定。
- 复核方式：读方案 §11 批次表新增约束行。

**M7（低 · 穷尽性）** —— 读取点/影响面登记不完整。
- 依据：核对 1——遗漏 `PayrollContextProvider.java:92,121-122`（计薪侧读取点）与 `AttendancePeriodResolverTest.java:44-57`（测试读取点）；§0.1「读取点全集仅 3 处」表述**误导**（不等于影响面 3 处）。
- 影响：影响面与测试改动面被低估；下游实现方可能漏改测试或误判改动范围。
- **验收标准**：§0.1/§13 补入 `PayrollContextProvider`（标「不适用：透传，语义随写入侧改善」）与测试读取点（标「B1 需适配」），并把「3 处」改为「`resolve(rule)` 调用者 3 个逻辑点 / 4 处调用」。
- 复核方式：读方案 §0.1/§13 补入项。

---

## 五、发现项清单（高 / 中 / 低）

**高（2）**
- **H-1**：单班次晚班站点因 `periods.get(periodIndex)` 下标取值 → 打卡/补卡 9107（= M1）。
- **H-2**：班次名 = 哨兵 `全天班` → 覆盖当日全部班次、实到虚增（= M2）。

**中（7）**
- **M-1**：U-7 建议与已裁定相反（= M3）。
- **M-2**：契约变更计数/路径/方法错误（= M4）。
- **M-3**：回滚清单漏 `toVO`/`defaultShift`/`newRuleDefaults`（= M5）。
- **M-4**：发布顺序与 B1 上线前置缺失（= M6）。
- **M-5**：R11「充要条件」表述不严谨；班次侧约束缺失的**残余风险未定级**（应为中高）；`ordinal` 判定三处漂移风险未声明（核对 6）。
- **M-6**：§0 摘要「读取点全集仅 3 处」表述误导影响面（= M7 主项）。
- **M-7**：NFR 缺**可用性降级链**（0 班次直接拒绝、无派生失败兜底）与**可观测性声明**（维度 3）。

**低（6）**
- **L-1**：§0 摘要第 5 条「存量读取零变化」未加「仅限哨兵/空名」限定（§4.3/§10.2 已限定）。
- **L-2**：§7.2 把「`checkFrequency` 无启用班次时 `0` 或 `null`」挂在 U-4（U-4 实为「列去留」），语义混淆。
- **L-3**：§5「读侧 `toVO` 改为实时派生」未说明 `AttendanceRuleServiceImpl.toVO` 被 `listRules`（`GET /rule/list`）复用 → 与 §7.3 的 N+1 派生是同一性能面，应合并登记。
- **L-4**：无显式假设清单、全篇未用 `TODO(扩展)` 标注遗留（维度 5）。
- **L-5**：U-3/U-4 未定却已进入 B2 门禁（= M6 子项）。
- **L-6**：§12.2 V-3（`12:00` UI 提示）标注「[待核实]」但未给核实责任人与时限。

---

## 六、结论等级与理由

### 结论等级：**打回**

**理由（触发出打回的分级红线）**：
1. **存在影响正确性的必改项**：**H-1**（单班次晚班站点打卡/补卡 9107，M1）与 **H-2**（哨兵名冲突致实到虚增，M2）均为**功能正确性缺陷**；按 `tech-evaluation` 分级红线，影响正确性 → **打回**。
2. **存在与已裁定口径/契约不一致且未处置**：**M3**（U-7 与主代理裁定相反）、**M4**（契约计数/路径错误）。
3. **§4.2 出现「证据不足导致的错误结论」**（等同无源结论的变体），且该结论会直接误导实现方漏改读取点 → 符合「无源结论/缺关键论证 → 打回」。

> 注：方案整体方向（**(a) 读时派生、单真源、D-2 保留列、D-4 `period_index=ordinal`**）**本身成立**，§0.1 复核与多数 `文件:行号` 证据**准确**；打回**非否定方向**，而是要求修订上述高/中风险项后**重评**。

**退回对象**：架构师 `express-station-architect`（方案原作者；评估方不代改）。

**重评前置（修订须逐条闭环，重评时按条核对、评估范围冻结）**：
1. 闭环 **M1、M2**（正确性两项，必改必重评）。
2. 闭环 **M3、M4、M5、M6、M7**（一致性/回滚/门禁/穷尽性）。
3. 同步修订 §0 摘要（L-1/L-2）、补显式假设与残余风险定级（M-5）、补 NFR 降级与可观测性声明（M-7）。
4. 版本号递进（v1.0 → v1.1）并在文首声明「评估结论绑定版本、版本变更须重评」。

**不得报审**：本结论为「打回」，依 `项目规则1.md` §8 第 8 条与调度规则 **P0.6 / L8**，**不得报主智能体审批**；主智能体不得受理未重评版本。

**放行前置清单（重评通过后、报审须同时满足，逐条可判定）**：
1. M1 验收标准达成：方案明文「按值查找」+ 单班次晚班站点三路径算例齐备。
2. M2 验收标准达成：哨兵名防护项入 §6.3/§8.3 且有反例算例。
3. M3 达成：U-1~U-7 全部标「已裁定」且与 7 条裁定逐条一致。
4. M4 达成：§7 计数/路径/方法与 `api.md` + 控制器一致。
5. M5 达成：回滚清单与 B1 改动面一一对应。
6. M6 达成：发布顺序 + B1 上线前置（P-1=0）+ B2 前置（U-3/U-4）写入 §11。
7. M7 达成：`PayrollContextProvider` 与测试读取点登记入 §0.1/§13。
8. **口径锁定**：U-1~U-7 的最终取值以用户/主代理裁定为准并落文（评估方不代裁）。
9. **安全面**：`V24` 执行/`hrm-server` 提交等 C 档动作，报审时须由**网络安全工程师**出技术结论（本报告不做安全判定，P0.5 与 P0.6 **并行不互替**）。

---

## 七、方案自述与实测不符（逐条）

| # | 方案自述（位置） | 实测 | 性质 |
| - | ---- | ---- | ---- |
| 1 | §4.2（行 134）「前端 `slotKey`、`validCard`、**补卡 `periodIndex`** 均以**值为键**，不受『非连续』影响」 | `AttendanceMakeupServiceImpl.java:122-123`、`:210-212` 与 `AttendanceRecordServiceImpl.java:320` **均为 `periods.get(periodIndex)` 数组下标取值**；仅 `validCard:581` 为值比较 | **不符（影响正确性，M1/H-1）** |
| 2 | §0.1（行 45）「**读取点全集**（`resolve(rule)` 生产者）仅 3 处」 | 作为「影响面」不完整：另有 `checkIn:344-345`、`toVO:146-147`、`defaultShift:146-147` 派生值读取点，及 `PayrollContextProvider:92,121-122` 计薪侧读取点未纳入 | 表述误导（M7/M-6） |
| 3 | §9.4/§10.5 回滚「反向 diff 3 读取点 + `AttendancePeriodResolver` + `saveRule` 重算」 | B1 另含 `toVO:143,146-147`、`defaultShift:140-152`、`newRuleDefaults:386-431` 未列入回滚 | 不符（M5/M-3） |
| 4 | §7.8「复用 `GET/POST/PUT/DELETE /api/v1/attendance/shifts`」 | 实测前缀为 `/api/v1/shifts`（`ShiftController.java:28`、`api.md` §4.8） | 不符（M4/M-2） |
| 5 | §7.8 变更计数「新增 1」 | §7.2/§7.4 另拟新增 `checkPeriodsReadonly`、`shiftConfigured` 两字段未计入 | 不符（M4/M-2） |
| 6 | §7.6 标题「`POST/PUT /api/v1/attendance/makeup*`」 | 实测两接口均 `POST`（`api.md:1034,1049`） | 不符（低，M4） |
| 7 | §12.1 U-7 架构建议「(i) 同步 demo 镜像」 | 主代理已裁定「`hrm-demo` **不同步**、仅登记」 | 与已裁定相反（M3/M-1） |
| 8 | §0 摘要第 5 条「存量读取**零变化**」 | 仅在存量 `period_name` 命中哨兵或为空时成立；§4.3/§10.2 已限定，摘要未同步 | 摘要过度（L-1） |
| 9 | §6.2「R11 天然消除（**当且仅当**站点启用班次 `ordinal` 互异）」 | R11 由构造消除，与 `ordinal` 是否互异无关；`ordinal` 互异是**防 R10 型冲突**的条件 | 表述不严谨（M-5） |
| 10 | §6.3「**算法 B5 已裁定删中班** —— `algo-payroll-shift.md:65`」 | `algo-payroll-shift.md:65` 为「B5 班次时间 + 删除中班」，**引用成立**；seed 已删中班（`kdyzgl_test_seed.sql:236`）——**此条核对为「相符」**，列此以说明非全部不符 | **相符（保留澄清）** |

**核对为「相符」的关键自述（抽检通过，供报审留痕）**：§0.1-1~4、6~10 的字段/行号复核、订正 1（R11 出处 `:457`）、D-6(`:104`)、`db.md:621/623/1687`、`AttendanceShiftServiceImpl.list:44-58 含停用`、`AttendanceShiftServiceImpl.validateShift` **无条数/ordinal 校验**、`check_periods` 为可空 JSON 无索引牵连（→ `V24` 非必需成立）。

---

## 附：本报告的边界与未验证项

- **未运行**：未编译、未跑单测/接口/E2E、未连库、未执行 SQL（本机无 JDK/Maven/MySQL）。
- **待核实（须实跑，非本报告结论）**：P-1（有规则无启用班次站点数）、P-2（启用班次>2 站点）、P-3（`ordinal` 冲突站点）、P-4（`period_name` 非哨兵非空分布）= §12.2 V-1/V-2。
- **转交网络安全工程师**：`V24` 执行、`hrm-server` 提交等 C 档动作的**技术安全结论**（本维仅核对「安全面是否声明」，方案基本未声明）。
- **口径最终裁定权**：U-1~U-7 取值归**用户/主代理**；评估方只评「方案是否一致落实」，不代裁。

---

# 复评（方案 v1.1 ｜ 2026-09-27）

> **本节性质：上游方案修订后的复评（非生产变更前复评）。** 仅对「v1.0 打回 → v1.1 修订」做**闭环核对 + 新增问题排查 + 可实施性复核**；**不重写首评六维全文**，与「生产变更前复评」是两类不同性质的评估，**不得互相替代**。

| 项目 | 内容 |
| ---- | ---- |
| 复评对象 | `hrm-dev/docs/attendance-time-source-design.md`（**v1.1 修订版**，749 行；v1.0 = 483 行） |
| 上一结论 | **打回**（首评，本文件 §六；必改 M1~M7、发现项高 2 / 中 7 / 低 6、放行前置 9 条） |
| 复评基准 | 主代理已裁定 **U-1~U-7**（站点级 / 员工无排班仍可打卡 / boss-h5 引导网页端 / `check_frequency` 保留只读派生 / `PUT /rule` 收 `checkPeriods` 拒绝 / 新增班次定义侧校验错误码 / `hrm-demo` 不同步·`mock` 同步） |
| 评估方 | 技术评审工程师 `express-station-tech-reviewer`（**独立于产出方**；只出结论与必改项，不产出方案、不代改、不代授权） |
| 取证方式 | 静态文档级 + 源码逐条取证：全量复核下标取值点、`ErrorCode` 现状、`ShiftPayrollPolicy` 哨兵比对、`AttendanceRuleServiceImpl` saveRule 管线、`api.md`/控制器路径、前端提交体、mock 侧 |
| 本机能力声明 | **无 JDK / Maven / MySQL**：**未编译、未跑单测/接口/E2E、未连库、未执行 SQL**；结论均为静态取证 |
| 评估边界 | 只评**工程面技术质量**；**不做安全实质判定**（归网络安全工程师）；**不裁定口径**；结论**绑定 v1.1 版本**，版本再变须重评 |
| **结论等级** | **有条件通过**（详见 §六；附可判定放行前置清单） |

---

## 一、M1~M7（含 M6′）逐条闭环核对

> 判定口径：**已闭环** = 验收标准达成且有独立证据；**部分闭环** = 主项达成、仍有影响一致性/防护有效性的残留；**未闭环** = 主项未达成。

| 编号 | 首评问题（等级） | v1.1 整改落位 | 独立核验证据（`文件:行号`） | 闭环判定 |
| ---- | -------------- | ------------- | ------------------------- | -------- |
| **M1** | 下标取值致单班次晚班站点打卡/补卡 **9107**（高·正确性） | §4.2 更正 + §4.4 硬规则「按 `ordinal` 按值查找，禁止 `List.get(periodIndex)`」+ §4.5 三路径算例 | ① 硬规则落文（方案 §4.4 行 271-276、§4.5 行 289-300）；② **全量复核** `hrm-server/src/main` 内 `periods.get(periodIndex)` **仅 3 处**：`AttendanceRecordServiceImpl.java:320`、`AttendanceMakeupServiceImpl.java:123`、`:211-212`（与方案一致）；`AttendanceRuleServiceImpl.java:425-426` 的 `get(0)/get(size-1)` 属**非 `periodIndex`** 取值（已登记 §0.2-D8）；**确认无第三处**；③ 算例自洽：`size=1`/`periodIndex=1` 下旧逻辑 `1<1=false→null→9107`、新逻辑按值命中 → 打卡与补卡均**可达且正确** | **已闭环** |
| **M2** | 班次名 = 哨兵 `全天班` → 覆盖当日全部班次、实到虚增（高·正确性） | 新增 §6.4「班次定义侧禁用保留名 `全天班`（回 9114）」；§8.3/§7.7 落点 | ① 哨兵分支证据成立：`ShiftPayrollPolicy.java:167-170`（`legacyPeriodSentinel.equals(row.periodName())` → `attended.addAll(keys)`）；② `AttendanceShiftServiceImpl.validateShift:157-177` 现无保留名校验（仅 1-20 字，`textLen` 内部 trim 见 `AttendanceSupport.java:41`）—— 方案描述准确；③ **残留 1（防护可绕过）**：`validateShift` 在 `setShiftName(safe.getShiftName().trim())` **之前**调用（`AttendanceShiftServiceImpl.java:71/92` vs `:74/93`），若禁用名比对取**原始入参**，则「 全天班」「全天班 」等前置/尾随空白可绕过（`textLen` 因 trim 而放行）；④ **残留 2（残余预检失效）**：§0.2-P-4 定义仅查 `attendance_record.period_name` **非哨兵（≠全天班）且非空**，**结构性排除**哨兵名 → 无法检出「已存在的『全天班』命名班次」，与方案「P-4 兼作 M2 残余风险预检」自述**矛盾** | **部分闭环**（主项已立，残留见 N1/N2） |
| **M3** | U-7 建议与已裁定相反（中） | §8.5 第 5/6 行、§10.4 RK-3、§12.1 U-7 | 方案 §8.5 行 503（demo **不同步**·仅登记）/行 504（mock **同步**）、§10.4 行 599、§12.1 行 691 —— 与主代理裁定**逐条一致** | **已闭环** |
| **M4** | 契约计数/路径/方法错误（中） | §7 全章重算 + §7.8 计数 | ① 路径 `/api/v1/shifts` 正确（`ShiftController.java:28`、`api.md:1062/1066`）；② 补卡两接口均 `POST`（`api.md:1034/1049`）；③ 计数「新增 4（9113/9114/`checkPeriodsReadonly`/`shiftConfigured`）+ 变更 6 组·字段级 11 项 + 复用 2」与 §7.1–§7.7 逐条可对上（方案 §7.8 行 443-450） | **已闭环** |
| **M5** | 回滚「3 读取点」低估改动面（中） | §9.4/§10.5 补 `toVO`/`defaultShift`/`newRuleDefaults` → R-1~R-11 | 首评点名的三点已全覆盖：R-7↔`toVO`（`AttendanceRuleServiceImpl.java:142-147`）、R-8↔`newRuleDefaults`（`:386-431`）、R-9↔`defaultShift`（`AttendanceShiftServiceImpl.java:140-152`）✓；**但** §0.2「需改 22 条」中的 **D5/D6/D7**（`AttendanceRuleServiceImpl.java:201-237` 时段校验、`:296-320` 归一化、`:374-379` `applyPayload`，即 C4 的时段侧去除）**无对应 R 项** → 「R-1~R-11 与 B1 改动面一一对应」的表述与 §0.2 台账**不完全对应**（见 N3） | **部分闭环** |
| **M6** | §6.2「R11 当且仅当 ordinal 互异」不严谨（中） | §6.2 精确化 + 残余定级中高 | 方案 §6.2 行 324-333 已改为「R11 **由构造充分消除**（与 `ordinal` 互异**无关**）；`ordinal` 互异是**避免 R10 型取值冲突的必要条件**（另一风险域）」；并指出 `ordinal` **两处实现**（`ShiftPayrollPolicy.java:74` 与 `AttendanceScheduleServiceImpl.java:535` 内联，**未复用** `shiftOrdinal`）须复用同一实现 | **已闭环**（行号精度见 N6） |
| **M6′** | 发布顺序 / B1 上线前置缺失（中） | §11 REL-1~REL-4 + 批次表 | REL-1（B1/B2 同批）、REL-2（B1 前置 = P-1=0）、REL-3（B1 前置 U-1~U-7、B2 前置 U-3/U-4）、REL-4（B3 仅 mock）均落文（方案 §11 行 664-671）✓；**但** §11 头部线性序「B1&B2 → B3 → B4」与「B1 依赖 P-1=0（而 P-1 治理属 B4）」**矛盾**（见 N4） | **部分闭环** |
| **M7** | 读取点/影响面登记不完整（低·穷尽性） | §0.2 唯一台账（34 条）+ §13 索引 23/24 条 | 台账抽检**全部相符**（见 §二）；首评两处遗漏已登记（F1 `PayrollContextProvider.java:92,121-122` 透传、F2 `AttendancePeriodResolverTest.java:44-57` 需适配）✓；**残留**：与首评「20 处」的映射未显式标注、Mock 门禁资产 `verify-mock.mjs` 未登记（见 N5） | **部分闭环** |

**闭环统计：已闭环 4（M1、M3、M4、M6）；部分闭环 4（M2、M5、M6′、M7）；未闭环 0。**

---

## 二、§0.2 读取点台账抽检（独立核对）

**抽检范围**：A~F 六组共 34 条**逐条核对**（重点抽检 A/B/C/D/E/F 各 ≥1 组，实为全组覆盖，**抽检 34 条**），逐条比对「行号 + 需改/不需改」判定。

| 组 | 条目 | 独立核验结果 |
| -- | ---- | ------------ |
| A（`resolve` 调用者 4） | A1 `AttendanceRecordServiceImpl.java:316`、A2 `:447`、A3 `AttendanceMakeupServiceImpl.java:121`、A4 `:208` | **4/4 相符**（均实为 `resolve(rule)` 调用点，判定「需改」正确） |
| B（派生值 4+写侧1） | B1 `AttendancePeriodResolver.java:68-69`、B2 `AttendanceRecordServiceImpl.java:344-345`、B3 `AttendanceRuleServiceImpl.java:146-147`、B4 `AttendanceShiftServiceImpl.java:146-147`、B5 `AttendanceRuleServiceImpl.java:100-103` | **5/5 相符**（B5 为「写侧重算」，方案已单列并计入需改） |
| C（`check_frequency` 4） | C1 `AttendanceRecordServiceImpl.java:469-472`、C2 `AttendanceRuleServiceImpl.java:142`、C3 `:421`、C4 `:96,201-237,296-320,374-379` | **4/4 相符**（`:421` 实为 `newRuleDefaults` 内 `setCheckFrequency`） |
| D（`check_periods` 8+登记4） | D1 `AttendancePeriodResolver.java:59`、D2/D3/D4/D5/D6/D7/D8（`AttendanceRuleServiceImpl` 各段）、D9 `AttendanceRule.java:66-68`、D10 `AttendanceRuleRequest.java:33`、D11 `AttendanceRuleVO.java:34`、D12 `CheckPeriodListTypeHandler` | **12/12 相符**（D9~D12 实为字段/TypeHandler，判定「不需改」正确） |
| E（其它消费点 7） | E1 `:126` 导出、E2 `:178`、E3 `:655`、E4 `:723-724`、E5 `:581`、E6 `AttendanceSummaryPolicy.java:87-88`、E7 `ShiftPayrollPolicy.java:168-186` | **7/7 相符**（E5 为 `wrapper.eq(...)` 值比较，判定准确） |
| F（遗漏 2） | F1 `PayrollContextProvider.java:92,121-122`、F2 `AttendancePeriodResolverTest.java:44-57` | **2/2 相符**（F1 透传不需改、F2 需适配，判定正确） |

**抽检结论**：抽检 **34 条**，**行号不符 0 处、判定不符 0 处**。台账计数自洽（4+5+4+12+7+2 = 34；需改 22 = A1-A4+B1-B5+C1-C4+D1-D8+F2；不需改 12 = D9-D12+E1-E7+F1）。
**残留 2 项（非行号错）**：① 与首评「20 处」的**映射关系未显式标注**（27 = 20 + E 组 7，可由分解式推出，但未点明）；② Mock 门禁资产 `hrm-clients/scripts/verify-mock.mjs` **未登记**（`api.md:998` 明确把该文件列为 Mock 侧）。

---

## 三、新引入问题排查（v1.1 是否带来新矛盾）

| # | 排查项 | 结论 | 依据 |
| - | ------ | ---- | ---- |
| NP-A | §4.4「按值查找」与 §6.4「禁用 `全天班`」是否冲突/重复 | **不冲突、不重复**（**正交**）：§4.4 治「取时段的方式」（下标→按值），§6.4 治「命名撞哨兵」（写入侧禁用保留名），作用面与失败态均不同 | 方案 §4.4 行 267-287 / §6.4 行 345-368 |
| NP-B | `9113`/`9114` 是否落在既有编号段且不冲突 | **不冲突**：`ErrorCode` 91xx 段现止于 `9112`（`ErrorCode.java:218`），其后直接跳 `92xx`（`:220`）→ `9113/9114` **为该段下一个可用号**；全仓检索 `9113/9114` **仅本方案文档出现**，无既有占用 | `ErrorCode.java:179-220`；仓库 `Grep 9113\|9114` |
| NP-C | U-2「员工无排班仍可打卡」是否仍有明确时段来源 | **有**：派生粒度 = **站点级启用班次**（D-3），与员工排班无关；`periodIndex` 非空即走时段模型（`AttendanceRecordServiceImpl.java:317-331`），**不查排班** → 无排班员工仍有 `ordinal` 可查；`periodIndex` 为空时兜底由「首个启用班次」派生（方案 §10.7） | 方案 §10.7 行 634；`AttendanceRecordServiceImpl.java:316-331` |
| NP-D | U-5「`PUT /rule` 拒绝 `checkPeriods`」与「读时派生」是否完全自洽 | **判定依据自洽**（显式非空即拒，`check_periods` 已非真源）；**错误码归属未明示**：方案 §7.1 只写「回 `400`」，未说明是否复用既有无码 `400`（`api.md:798` 已定「字段校验类统一 400」）—— 建议明示为**通用 `400`** 以消除歧义（见 N7） | 方案 §7.1 行 380；`api.md:798` |

**结论：四项排查均未发现「新引入的硬矛盾」**；NP-D 属「未明示」而非矛盾。

---

## 四、必改项（复评新增；问题 / 影响 / 验收标准 / 复核方式）

> 仅列本轮修订引入或仍存的**可判定**项；不改写方案原文（修订归原作者）。

**N1（中 · 防护有效性，M2 残留）** —— §0.2-P-4 的查询定义**无法检出**「已存在名为保留名的班次」。
- 依据：P-4 定义为「`attendance_record.period_name` **非哨兵且非空**」（方案 §9.3 行 536），而 M2 残留要求检出的是**班次表**中 `shift_name ∈ {legacyPeriodSentinel, DEFAULT_PERIOD_NAME}` 的启用班次（`is_deleted=0`）——二者查询对象相反。
- 影响：「历史遗留 / 直连 DB 写入」的「全天班」命名班次**不会被任何预检暴露**，仍持续产生哨兵记录 → 实到虚增（M2 原缺陷在存量数据上未闭环）。
- **验收标准**：方案新增一条预检（如 **P-6**）：「查 `attendance_shift.shift_name` = 哨兵 / `DEFAULT_PERIOD_NAME` 的启用班次清单」，并给出**治理或阻断口径**（改名前须评估其历史记录影响）；同步修正 §0.2-P-4 对其的「兼作」表述（删或改为指向 P-6）。
- 复核方式：读方案 §9.3/§6.4 新增项；比对 `ShiftPayrollPolicy.java:167`、`AlgoProperties.java:123`、`AttendanceConstants.java:65`。

**N2（中 · 防护可绕过，M2 残留）** —— 禁用名防护的**比对值未归一化**，可被前置/尾随空白绕过。
- 依据：`validateShift` 在 `setShiftName(...trim())` **之前**调用（`AttendanceShiftServiceImpl.java:71/92` vs `:74/93`）；`AttendanceSupport.textLen` **内部 trim**（`AttendanceSupport.java:41`）→ 「 全天班」既通过长度校验、又可能与原始串比对不相等。
- 影响：若实现按字面「`shift_name` 不得等于 `全天班`」以**原始入参**比较，则该班次被放行、落库为 `全天班`（trim 后）→ 新记录命中哨兵分支 → 实到虚增，**防护失效**。
- **验收标准**：方案在 §6.4 明确：禁用名比对取 **`trim()` 后值**、且与落库值**同源归一**（建议先 `String name = body.getShiftName().trim()` 再比对，或把校验移到 trim 之后）；并在 §8.3 服务端校验条目中写明「trim 后比对」。
- 复核方式：读方案 §6.4/§8.3；核对 `AttendanceShiftServiceImpl.java:71-74` 调用顺序与 `AttendanceSupport.java:41`。

**N3（中 · 回滚完整性，M5 残留）** —— R-1~R-11 未覆盖 §0.2 需改的 **D5/D6/D7**。
- 依据：§0.2 记「需改 22 条」，其中 D5（`AttendanceRuleServiceImpl.java:201-237` 时段校验）、D6（`:296-320` 归一化）、D7（`:374-379` `applyPayload`）为 `saveRule` 时段侧去除点；§9.4 R-6 仅覆盖 `:96,100-103`（B5/D2/D3），**无 D5/D6/D7**。
- 影响：若 B1 已去除上述时段校验/归一化/写入而回滚清单不含，回滚后 `saveRule` 仍为「读时派生」语义、`check_periods` 被忽略 → 与「回滚后以 `check_periods` 为准」的目标**不一致**，出现口径分裂。
- **验收标准**：§9.4 增补 R 项（或在 R-6 明确包含 `:201-237/296-320/374-379`），使「需改 22 条」与回滚清单**逐条可对**；并在 §11 B1 改动面同步列出。
- 复核方式：读方案 §9.4 与 §0.2-D5/D6/D7 交叉比对。

**N4（中 · 门禁可判定性，M6′ 残留）** —— §11 批次线性序与「B1 前置 = P-1=0」矛盾。
- 依据：§11 头部写「B1（与 B2 同批）→ B3 → **B4**（迁移/治理）」（行 652），而 B1 依赖「**P-1 预检 = 0**」（行 657），P-1 治理（补班次）又被列在 **B4** 内容中（行 661）→ **B1 需要 B4 的产物，却排在 B4 之前**。
- 影响：批次门禁不可判定——「B1 是否可以开工」取决于尚未执行的 B4，存在「先开工 B1 → 有规则无班次站点全员禁打卡」的窗口，与 REL-2 自相矛盾。
- **验收标准**：方案明确 **P-1 预检与治理（补班次）须在 B1 之前完成**（或将其前移为独立前置批次 B-1），并修正 §11 头部线性序；B1 门禁条「P-1=0」与其依赖项（治理批次）**先后一致**。
- 复核方式：读方案 §11 头部 + B1/B4 行 + REL-2。

**N5（低 · 影响面穷尽性，M7 残留）** —— Mock 门禁资产 `verify-mock.mjs` 未登记。
- 依据：`api.md:998` 明确把 `hrm-clients/scripts/verify-mock.mjs` 列为 Mock 侧；该脚本含大量以 `checkPeriods`/`checkFrequency`/`periodIndex` 编码**旧语义**的断言（如 `verify-mock.mjs:1934-1941, 2106-2109`）。
- 影响：U-7 裁定「mock 同步」后，mock 语义变更将使该门禁断言可能失败；若未预先登记，易被当作「误报」或以「改断言」迁就（违 §13/A06 验收资产纪律）。
- **验收标准**：§8.5 影响面补登 `verify-mock.mjs`，并声明「B3 须同步核验其断言，**不得以改断言迁就实现**；断言确因契约变更需调整的，须逐条说明理由并登记」。
- 复核方式：读方案 §8.5/§11-B3；抽查 `hrm-clients/scripts/verify-mock.mjs` 相关断言。

**N6（低 · 行号精度）** —— 两处引用行号与实测差 1，且**同文档内自相不一致**。
- 依据：§6.3 写 `ShiftPayrollPolicy.java:75`（实际内联 `minutes < boundary ? 0 : 1` 在 **`:74`**，同文档 §6.2 已用 `:74`）；§6.2 写 `AttendanceScheduleServiceImpl.java:534`（实际内联在 **`:535`**，方法体 `:527-541`）。
- 影响：实现方按错行号定位，增加沟通成本。
- **验收标准**：§6.2/§6.3 行号校正为 `:74` 与 `:535`，并保持同文档一致。
- 复核方式：核对 `ShiftPayrollPolicy.java:74`、`AttendanceScheduleServiceImpl.java:535`。

**N7（低 · 契约表述）** —— U-5 拒绝的**错误码未明示**。
- 依据：§7.1 只写「回 `400`」，未说明是通用 `ErrorCode.BAD_REQUEST`（无业务码）；而 `api.md:798` 已定「字段校验类错误统一 `400`」。
- 影响：后端补录 `api.md` 时可能自造码或复用 `9107`，造成口径不一。
- **验收标准**：§7.1/§7.7 明示 U-5 拒绝使用**通用 `400`**（不新增 91xx 码），与 `api.md:798` 一致。
- 复核方式：读方案 §7.1/§7.7；对照 `api.md:798`、`ErrorCode.java` 91xx 段。

---

## 五、六维简版（仅评本轮修订引入或仍存的问题）

| 维度 | 结论 | 本轮证据 |
| --- | --- | --- |
| 1 依据充分性 | **满足（残留行号精度）** | M1 取证已由「只引值比较」更正为「三处下标取值 + 全量复核无第三处」；残留 N6 两处行号差 1 |
| 2 架构与模块边界 | **满足** | 单真源/单向依赖不变；`ordinal` 双实现风险已在 §6.2 声明并建议抽取共享方法（未见列为 B1 交付物，属建议级） |
| 3 NFR 覆盖度 | **满足** | §10.7 已补**可用性降级链 + 可观测性声明**（9113/9114 拒绝计数等）；轻微：降级链「班次查询异常→9113/空态」与「0 班次→9113」**同码**，运营侧无法区分「配置缺失」与「查询故障」（建议日志/告警标签区分，非必改） |
| 4 复杂度与可行性 | **部分满足（残留 N3）** | M1 改造点、批次、门禁已明确；`saveRule` 时段侧的 D5/D6/D7 未入回滚/改动面 → 可行性清单不完整 |
| 5 假设与风险登记 | **部分满足（残留 N1/N2）** | §10.6 显式假设 6 条（含不成立后果）齐备；AS-3（班次名不撞哨兵）的**防护与残余预检不完整** |
| 6 与既有契约一致性 | **满足（残留 N5/N7）** | M4 已修（路径/方法/计数）；残留 `verify-mock.mjs` 未登记、U-5 错误码未明示 |

---

## 六、结论等级与放行前置

### 结论等级：**有条件通过**

**理由**：
1. 首评**两项高危正确性缺陷（M1、M2 主项）已实质整改**：M1 已立「按值查找」硬规则、三路径算例自洽且**全量复核无第三处下标取值**；M2 已立「写入侧禁用保留名」并给反例算例；两项均**非仅表述修补**。
2. 与已裁定口径/契约不一致项（M3、M4）**已闭环**；M6（R11 表述）、M6′（发布约束）主项**已落文**。
3. 剩余为**一致性/防护有效性/门禁可判定性**问题（N1~N7），**不推翻主干方向**（读时派生 / `period_index=ordinal` / 按值查找）→ 按 `tech-evaluation` 分级，属「**有条件通过**」（须附必改项清单）。
4. **无安全实质结论**（归网络安全工程师）、**无口径代裁**、**未运行任何构建/测试**（本机无 JDK/MySQL）。

### 报审准入：**可报审**（结论为「有条件通过」，依 P0.6 / L8 可进入报主智能体审批流程），**但须附本清单**。

### 放行前置清单（报审须同时满足，逐条可判定）

1. **N1 达成**：新增「查 `attendance_shift.shift_name` = 哨兵/`DEFAULT_PERIOD_NAME` 的启用班次」预检，并修正 §0.2-P-4 的「兼作」表述。
2. **N2 达成**：§6.4/§8.3 明确禁用名比对取 **`trim` 后值**、与落库值同源归一。
3. **N3 达成**：§9.4 回滚清单与 §0.2「需改 22 条」**逐条可对**（补 D5/D6/D7 或明确 B1 不含该三处）。
4. **N4 达成**：§11 明确 **P-1 预检与治理须先于 B1**，修正「B1&B2 → B3 → B4」线性序与 REL-2 的先后一致。
5. **N5 达成**：§8.5 补登 `verify-mock.mjs`，并声明 B3 断言调整不得迁就实现。
6. **N6/N7 达成**：行号校正（`:74`、`:535`）；U-5 拒绝明示为通用 `400`。
7. **口径锁定**：U-1~U-7 取值以用户/主代理裁定为准（已落位；评估方**不代裁**）。
8. **安全面并行**：`V24` 执行 / `hrm-server` 提交等 C 档动作的技术安全结论归**网络安全工程师**（P0.5，与本闸门 P0.6 **并行不互替**）。

> 说明：N1/N2 影响 M2 防护的**有效性**，**建议在 B1 动工前闭环**；N3/N4 影响改动面与门禁一致性；N5~N7 为登记/精度类。

---

## 七、复评中「方案自述与实测不符」逐条

| # | 方案自述（位置） | 实测 | 性质 |
| - | --------------- | ---- | ---- |
| 1 | §6.3「`12:00` 归晚班（`ShiftPayrollPolicy.java:75`）」 | 实为 **`:74`**（同文档 §6.2 已用 `:74`，**自相不一致**） | 行号差 1（N6） |
| 2 | §6.2「`AttendanceScheduleServiceImpl.java:534`（内联 ordinal，未复用 `shiftOrdinal`）」 | 内联在 **`:535`**（方法体 `:527-541`）；「未复用」判断正确 | 行号差 1（N6） |
| 3 | §0.2-P-4「兼作 M2 残余风险预检（是否有历史/直连 DB 写入的『全天班』命名班次）」 | P-4 只查 `attendance_record.period_name` **非哨兵且非空**，**排除**哨兵名 → **查不到**名为「全天班」的班次 | **逻辑不符**（N1） |
| 4 | §9.4「R-1~R-11 与 §11 B1 改动面**一一对应**」 | §0.2 需改 22 条中的 **D5/D6/D7** 无对应 R 项 | **不完全对应**（N3） |
| 5 | §11 头部「B1（与 B2 同批）→ B3 → **B4**」 | 与 B1 依赖「P-1=0（治理属 B4）」**先后矛盾** | **自相矛盾**（N4） |
| 6 | §0.2 台账「已覆盖 27」 | 与首评「20 处」的**映射未显式标注**（27 = 20 + E 组 7，可由分解式推得）；`verify-mock.mjs` 未登记 | 表述未闭环（N5 / §二残留） |
| 7 | §4.5 补卡算例「取规定时间 `endTime="24:00"`（`CHECK_TYPE_ON` 取 `startTime`，`:224-225`）」 | 未指定 `checkType`，字面（取 `endTime`）与括号（`ON` 取 `startTime`）**自相矛盾** | 表述不清（低，并入 §四表述类，不单列必改） |

**核对为「相符」的关键自述（抽检通过，供报审留痕）**：M1 三处下标取值（`AttendanceRecordServiceImpl.java:320`、`AttendanceMakeupServiceImpl.java:123`、`:211-212`）**全部相符**且**无第三处**；M2 哨兵分支（`ShiftPayrollPolicy.java:167-170`）、`validateShift` 无保留名校验（`:157-177`）、`AttendanceShift` `@TableLogic` 但不滤 `status=0`（`AttendanceShift.java:43-46` + `list():44-58`）**相符**；M4 路径 `/api/v1/shifts`（`ShiftController.java:28`）、补卡均 `POST`（`api.md:1034/1049`）**相符**；`9113/9114` 为 91xx 段**下一可用号**（`ErrorCode.java:218→220`）**相符**；§0.2 台账 34 条行号与判定**抽检全相符**；`AttendanceRuleServiceImpl` **无班次引用**（§0.1-3）**相符**。

---

## 附二：本次复评的边界与未验证项

- **未运行**：未编译、未跑单测/接口/E2E、未连库、未执行 SQL（本机无 JDK/Maven/MySQL）。
- **待核实（须实跑/查库，非本报告结论）**：P-1（有规则无启用班次站点数）、P-2（启用班次>2）、P-3（`ordinal` 冲突）、P-4（`period_name` 非哨兵非空分布）、**N1 建议新增的 P-6（班次名撞哨兵）**。
- **转交网络安全工程师**：`V24` 执行、`hrm-server` 提交等 C 档动作的**技术安全结论**（本报告不做安全判定）。
- **口径最终裁定权**：U-1~U-7 归**用户/主代理**；评估方只评「方案是否一致落实」，不代裁、不代改、不代放行。
