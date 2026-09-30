# algorithm-multi-shift-scheduling.md · 多班次排班（一名员工同一天可排多个班次）算法四件套

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.1（修订版：已按技术评审必改项 M-1/M-4/M-6 与主代理唯一性裁定整改；待技术评审重评）** |
| 编写日期 | 2026-09-27（v1.1 修订 2026-09-27） |
| 产出角色 | 算法工程师 `express-station-algorithm-engineer`（只出算法方案；**不写实现代码、不写迁移 SQL、不改任何代码/契约文档、不执行 git/部署/MCP、不连库/跑测试**） |
| 需求 | 允许**同一员工同一日期排多个班次**（如早班 09:00–13:00 + 晚班 17:00–21:00），等价「上一整天」 |
| 上游真源（只读） | `hrm-dev/docs/algo-payroll-shift.md` v2.0（**班次制算薪已存在，且已按「一天最多 2 个半天单元」建模**）、`db.md` §8.3.3/§8.3.4、`api.md` §4.9、`requirement.md`、`payroll-automation-design.md` |
| 关联实现（只读取证） | `AttendanceScheduleServiceImpl.java`、`AttendanceRecordServiceImpl.java`、`AttendanceSummaryPolicy.java`、`AttendanceDetailPolicy.java`、`ShiftPayrollPolicy.java`、`ProratedItemResolver.java`、`SchedulePlanner.java`、`V5__attendance.sql`、`AlgoProperties.java` |
| 状态 | **方案阶段产物（v1.1 修订版）**。v1.0 经技术评审结论 **打回**（`tech-review-boss-management.md`，2026-09-27）；本版已按必改项 M-1/M-4/M-6 与主代理唯一性裁定整改，**待技术评审重评**（结论绑定版本，任一变更须重评）。返回主智能体审批与交后端实施前，仍须**技术评审工程师（`express-station-tech-reviewer`）评估通过（通过 / 有条件通过）**。**本文不宣称已通过评审。** |
| 边界 | 只回答「怎么建模、选什么算法、复杂度多少、参数怎么配、边界怎么兜、指标是什么」；**不含 Java 代码、DDL、前端、部署** |
| 取证声明 | 本机**无 JDK / MySQL**，**未运行任何构建、测试或 SQL**；除注明出处外，全部数值为**按公式推导**，标 `[推导]`。结论均标 `文件:行号`。 |

> **四件套索引**：① 问题建模 = §1；② 算法选型与复杂度 = §4；③ 依据来源 = §5；④ 可验证指标与基准数据 = §6。
> **需上游确认的变更项**（含迁移版本号接续 `V22`）单列于 **§8**；**参数表**见 **§2**；**边界用例**见 **§3**；**失败降级**见 **§9**。

> **v1.1 修订约束**：本次**只编辑本文档**，未改任何代码 / 迁移脚本 / `db.md` / `api.md` / 架构方案 / UI / 安全报告；**未执行 git / 部署 / MCP，未连库、未跑测试**（本机无 JDK / MySQL）。修订依据 = 技术评审报告 `tech-review-boss-management.md`（结论 **打回**）+ 主代理裁定「排班「活跃唯一」采用**生成列式部分唯一**（沿用 `V16__employee_phone_unique.sql` 的 `phone_active` 生成列 + UNIQUE 手法）」。新增结论均标 `文件:行号` 或 `[推导]`。

---

## v1.1 变更摘要（相对 v1.0）

| # | 变更 | 落点 | 触发 |
| - | ---- | ---- | ---- |
| C1 | **删除语义由「物理删」更正为「逻辑删（`@TableLogic`）」**——v1.0 结论错误（无源） | §0.1-FC9、§1.2、§1.8、§4.1、§10-T3 | 评审 M-1 / F-01 |
| C2 | **唯一性口径由「裸三元 `UNIQUE(employee_id, work_date, shift_id)`」改为「生成列式活跃唯一」**（`active_shift_key = IF(is_deleted=0, CONCAT_WS(':',employee_id,work_date,shift_id), NULL)` STORED + `UNIQUE`） | §0-2、§1.2、§3-8、§6.1-N1/N9、§8-1 | 主代理裁定 + M-1 |
| C3 | **迁移预检口径显式化（三段式）**：区分「阻断性：仅活跃行」与「必须纳入输出：软删重复组」，并加 `is_deleted` 取值分布核对 | §8-1、§9 | M-6 / F-06 |
| C4 | **`shiftOrdinal` 前提化定义 + 新增「同日同员工班次 `ordinal` 必须互异」硬校验**（含冲突论证、防护、错误码建议、边界用例） | §1.3.1、§1.5、§2、§3-17/18、§7.1-A1、§7.2-R6、§9 | M-4 / F-03 |
| C5 | **兼容性前提更正**：存量单班次写的是**规则时段名快照**（`periods.get(0).name()`），**仅当 `check_periods` 为空**才兜底为哨兵 `全天班`；据此重述「零变化」论证 | §0.1-FC10、§1.5、§1.8、§7.1-A4 | 评审不符项 2 / F-08 |
| C6 | **§8 变更项按裁定重构**（9 条 → 10 条：新增「生成列 DDL + 预检三段」「ordinal 互异校验」；出勤口径条标「待用户裁定」） | §8、§11 | 本次裁定 + §8-4 |
| C7 | **FC8 由「待核实」升为「已核实」**：给出 `SchedulePlanner.java` 直接行号证据 | §0.1-FC8、§4.3 | 评审 §七-11 |
| C8 | 全文清除「物理删/物理删除」表述；`§8-4` 出勤口径补「待用户裁定，未裁定前不得据此实现」与两口径对照 | 全篇 | 评审不符项 2 / 用户口径红线 |

---

## 对技术评审必改项的整改响应（逐条）

| 必改项 | 评审要点 | 本文整改（v1.1） | 落点 |
| ------ | -------- | ---------------- | ---- |
| **M-1**（高） | §1.2/§1.8「物理删」为错误结论；裸三元 UNIQUE 与 `@TableLogic` 逻辑删互斥 | ① 更正为逻辑删（`AttendanceSchedule.java:39-40`、`AttendanceScheduleMapper.java:9`）；② 唯一性改用**生成列式活跃唯一**（对齐 `V16`）；③「清空→再排同班次」不再撞唯一键（§3-8）；④ 预检口径重写（见 M-6） | §1.2、§1.8、§3-8、§8-1、§10-T3 |
| **M-6**（中） | 迁移查重预检口径须显式含软删行 | 预检改**三段**：① 活跃行重复（阻断，须 0 行）② 软删行重复（**必须纳入输出**、不阻断，并说明理由）③ `is_deleted` 取值分布核对（生成列语义前提） | §8-1、§9 |
| **M-4**（高） | 同日两班「同半天」`ordinal` 冲突（计薪少算），未约束未登记 | 采纳评审**方案①**：新增「同日同员工班次 `ordinal` 必须互异」**硬校验**（默认拒绝，可配 `requireDistinctOrdinalPerDay`）；补 `shiftOrdinal` 唯一定义、冲突充要条件论证、边界用例、风险登记与计薪告警兜底 | §1.3.1、§1.5、§2、§3-17/18、§7.1-A1、§7.2-R6、§9、§10 |
| **评审不符项 2 / F-08**（中） | §1.8 前提「存量 `period_name='全天班'`」未证实 | 更正为「规则时段名快照」（`AttendanceRecordServiceImpl.java:354-355`、`AttendancePeriodResolver.java:58-71`、`AttendanceConstants.java:58`）；重述并**保留**「一天一行 ⇒ 零变化」论证 | §0.1-FC10、§1.5、§1.8、§7.1-A4 |
| **§8-4 出勤口径**（放行前置） | 应到/缺卡口径变更仍待用户裁定 | 条目**保留并标注「待用户裁定，未裁定前不得据此实现」**；补「人数去重 / 班次粒度」两口径对报表数字影响的对照表 | §1.5、§8-4、§10-T5 |
| **评审 §七-11 / FC8**（低） | `SchedulePlanner` 单值解空间「待核实」 | 升为**已核实**：`Solution(int[][] shift)`（`SchedulePlanner.java:50`）、维度约定 `shift[employee][day]`（`:22`） | §0.1-FC8、§4.3 |

> **仍未闭环（须上游 / 用户，非算法侧可单方闭合）**：① **§8-4 出勤口径待用户裁定**（未裁定前不得据此实现）；② 生成列 DDL、`V22` 迁移脚本与预检 SQL 由**数据库工程师**落地（本文只定语义口径，§8-1）；③ `ordinal` 冲突 / 重叠 / 超上限的**错误码由后端按 `api.md` 定稿**（算法不自定错误码，§8-10）；④ 计薪侧 `WARN_ORDINAL_COLLISION` 告警（兜底）须后端在 `ShiftPayrollPolicy` 侧实现（§9）。

---

## 0. 摘要（结论先行）

1. **核心结论（不推翻任何已裁定口径）**：本需求是**排班侧**从「一人一天一行（覆盖式）」升级为「一人一天一组班次（集合式）」。**计薪侧（S2b 班次制）已按「1 班次 = 半天单元、一天最多 2 单元」建模**（`ShiftPayrollPolicy.java:54-56`、`algo-payroll-shift.md` §1-B1），故**折算公式不新增、不改口径**，只需排班侧把「一行/天」补齐为「≤2 行/天」并与计薪的班次序号对齐。
2. **唯一性口径（v1.1 按主代理裁定更正）**：**生成列式活跃唯一** —— `attendance_schedule` 增生成列 `active_shift_key = IF(is_deleted=0, CONCAT_WS(':', employee_id, work_date, shift_id), NULL)`（STORED）+ `UNIQUE (active_shift_key)`；语义 = 「**同一员工同一天同一班次至多一条活跃排班；不同班次可并存（≤ `maxShiftsPerDay`）；软删行不占用唯一性**」。**取代 v1.0 的裸三元 `UNIQUE(employee_id, work_date, shift_id)`**（裸三元与 `@TableLogic` 逻辑删互斥，见 §1.2）。= `algo-payroll-shift.md` §0.1-C2「查重键改为三元键」的**活跃唯一**落地形态，本文给规范名与语义口径，DDL 由数据库工程师落 `V22`。
3. **班次重叠裁定建议**：**默认不允许时间重叠**（`hrm.algo.attendance.allowShiftOverlap=false`）；判定用**半开区间** `[start, end)`：`overlap ⟺ s₁ < e₂ ∧ s₂ < e₁`；**相邻不重叠**（`e₁ == s₂`）视为合法（早 09:00–13:00 + 晚 13:00–17:00 允许）。跨零点以 `end_time ≤ start_time` 归一为 `+1440`。
4. **「早+晚=全天」**：**不引入冗余字段**，全天为**派生概念**：`isFullDay(e,d) = |当日有效排班班次数| ≥ fullDayShiftCount`（默认 2）。
5. **唯一性键与计薪对齐点**：打卡记录 `period_index` **必须等于** `shiftOrdinal(shift.start_time, middayBoundaryMinute)`（早 0 / 晚 1），否则 `ShiftPayrollPolicy` 的 `A∩R` 映射错位（`ShiftPayrollPolicy.java:107-115` vs `:183-186`）。**且「同日同员工两班次的 `ordinal` 必须互异」（v1.1 新增硬校验，M-4）**：`shiftOrdinal` 仅早/晚二分，若同一天两班次**均落午前（或均落午后）** → `ordinal` 相同 → 计薪 `R` 去重后 `|R|=1` → **折算少算**（与「多班次对计薪影响=0」相悖）→ 排班保存侧拒绝（§1.3.1）。
6. **计薪折算（PRORATED）最终一句话定义**：多班次下 `PRORATED` 以**班次（=排班行，半天单元）为计数单位**，`折算后基本工资 = 定薪字段 × |A ∩ R| ÷ |R|`，其中 `R = 该员工当期排班行按 (work_date, 班次序号) 去重`（一天两班 = 2 个应出班次），`A = 有效上班卡按 (work_date, period_index↔班次序号) 映射后与 R 取交`；**一天两班即两个应出班次，不是「一天 = 一班次」**。
7. **发现 3 处口径/结构缺口**（§0.1 事实核查）：① `AttendanceSummaryPolicy` 应到/缺卡粒度自相矛盾（应到按排班行、实到按员工去重），多班次下 2 班全勤会误判 `absentCount=1`；② `SchedulePlanner` 解空间 `shift[employee][day]` 为**单班次模型**，多班次需扩展；③ `check_periods`（时间真源）与 `attendance_shift`（班次）无强制一致性约束。
8. **需上游确认的变更项 10 条**（§8）：含**迁移 `V22`（生成列 `active_shift_key` + `UNIQUE` + 预检三段，C 档）**；**出勤/考核口径变更 1 条须用户裁定（§11.4 口径红线，未裁定前不得据此实现）**。
9. **兼容性**：单班次存量数据（一人一天一行）在新模型下 **`R` 逐日退化为 1 单元**；存量 `period_name` 为**规则时段名快照**（仅 `check_periods` 为空才兜底为哨兵 `全天班`），三态优先级三种情形在「一天一行」下**均定位到该唯一班次**；**生成列式活跃唯一对存量行天然成立**（软删行生成列为 NULL、不占用唯一） → **零行为变化**（§1.8 论证）。

### 0.1 事实核查（上游材料 → 实测，含出入）

| # | 上游说法 | 实测（`文件:行号`） | 处置 |
| - | -------- | ------------------- | ---- |
| FC1 | 计薪折算「实出班次 ÷ 应出班次」需为多班次重定义 | **计薪侧已实现多班次（≤2/天）**：`shiftUnit = epochDay×2 + ordinal`、`requiredShiftSet` 按 `(workDate, ordinal)` 去重（`ShiftPayrollPolicy.java:54-56`、`:98-118`） | 折算公式**不改**；本任务 = 排班侧补齐「≤2 行/天」+ 序号对齐 |
| FC2 | 「打卡事实表已支持一天多时段打卡」 | 成立：`period_index`/`period_name` 显式列（`V5__attendance.sql:88-89`、`db.md:654-655`） | 无需改表结构 |
| FC3 | 排班唯一性 = `employeeId + workDate` | 成立：`AttendanceScheduleService.java:13`、`:24`；`findOne` 仅取首条 `AttendanceScheduleServiceImpl.java:370-379` | 改为**生成列式活跃唯一**（§8-1）；Service 查重键由 `(employeeId, workDate)` 扩展为**班次集合** |
| FC4 | `saveByStation` 覆盖式 | 成立，但逻辑分散在 `manualByStation`（`:262-273`）与 `autoByStation`（`:323-342`），**非单一行号段** | 仅行号精度更正 |
| FC5 | 表注释「员工+日期活跃唯一」 | 成立（`V5__attendance.sql:80`、`db.md:639`），且 DDL **仅普通索引无 UNIQUE**（`:78-79`） | 与 C2 一致 |
| **FC6（新）** | （上游未提）多班次下「缺卡/应到」可直接复用 | **`AttendanceSummaryPolicy.java:35-59` 应到按排班行数（`AttendanceRecordServiceImpl.java:151`）、实到按 employeeId 去重（`:39,56`）、缺卡 = `max(0, 应到 − 实到)`（`:57`）** → 2 班全勤（应到 2、实到 1）会得 `absentCount=1` **误判** | **必须改 per-shift 粒度**；属**出勤/考核口径**→ 须用户裁定（§8-4，**未裁定前不得据此实现**） |
| **FC7（新）** | （上游未提）打卡取排班 | `AttendanceRecordServiceImpl.findSchedule` 仅取首条（`:532-540`）；单班次模型（不传 `periodIndex`）以该首条班次为时间基准（`:303-316`） | 多班次需按 `period_index` 定位班次（§8-5） |
| **FC8（新）** | （上游未提）S3 排班算法 | **已核实（v1.1 补证）**：`SchedulePlanner.Solution` 定义为 `record Solution(int[][] shift)`（`SchedulePlanner.java:50`），维度约定 `shift[employee][day]` = 班次下标、`-1` = 轮休（`:22`）；`greedy`（`:118`）与 `simulatedAnnealing` 亦单值 → **单值解空间成立** | 多班次需扩展解空间（§4.3、§8-7） |
| **FC9（新 · 本文自身纠错）** | v1.0 §1.2/§1.8 称删除语义为「**物理删**（`deleteById`）」 | **不成立**：`AttendanceSchedule.isDeleted` 标注 `@TableLogic`（`AttendanceSchedule.java:39-40`），`AttendanceScheduleMapper` 为裸 `BaseMapper`（`AttendanceScheduleMapper.java:9`，无覆盖）⇒ `deleteById`（`AttendanceScheduleServiceImpl.java:185`、`:332`）实为**逻辑删（`UPDATE is_deleted=1`）** | **更正为逻辑删**；唯一性改用生成列式活跃唯一（M-1、§1.2、§1.8） |
| **FC10（新 · 本文自身纠错）** | v1.0 §1.8 称存量单班次「`period_name='全天班'` 哨兵」 | **不成立**：单班次模型写 `recordPeriodIndex=0`、`recordPeriodName = periods.get(0).name()`（`AttendanceRecordServiceImpl.java:354-355`），即**规则时段名快照**；`全天班` 仅在 `check_periods` 为空时由 `AttendancePeriodResolver.resolve` 兜底（`:58-71`，常量见 `AttendanceConstants.java:58`） | **更正前提**并重述「零变化」论证（§1.8、F-08） |

---

## 1. 问题建模（四件套 ①）

### 1.1 现状与问题

| 环节 | 现状（`文件:行号`） | 多班次冲突点 |
| ---- | ------------------- | ------------ |
| 表结构 | `attendance_schedule` 无唯一约束，仅 `(station_id, work_date)`、`(employee_id, work_date)` 两个普通索引（`V5__attendance.sql:78-79`） | 结构上**已允许**多行/天；缺**活跃唯一**约束与查重口径 |
| 保存 | 覆盖式：命中 `(employeeId, workDate)` 即改 `shiftId`（`AttendanceScheduleServiceImpl.java:195-197`）；`shiftId` 空即**逻辑删**（`deleteById` → `is_deleted=1`，`:185`、`:332`） | 一员工一天只能承载一个班次 |
| 矩阵/我的排班 | 索引 `key = employeeId@workDate`，`DayCell` 仅一个 `scheduleId/shiftId`（`:92-96`、`:122-135`；`ScheduleMatrixVO.java:39-45`、`MyScheduleVO.java:28-39`） | 出参形状为单班次 |
| 打卡取排班 | `findSchedule` 仅取首条（`:532-540`），单班次模型以首条班次为时间基准（`:303-316`） | 第二个班次无时间基准 |
| 出勤聚合 | 应到 = 排班**行数**（`AttendanceRecordServiceImpl.java:151`），实到 = 有效 ON 卡**员工去重**（`AttendanceSummaryPolicy.java:39,56`） | 粒度混用 → 缺卡误判（FC6） |
| 计薪折算 | 已课堂次制：`R` 按 `(workDate, ordinal)` 去重（`ShiftPayrollPolicy.java:98-118`） | 与排班行的「一天一行」不一致 |

### 1.2 数据模型口径：唯一性键与「活跃唯一」语义（v1.1 按主代理裁定更正）

**模型变换**：`(employee, work_date)` 由「**唯一**」改为「**一组班次**」。

| 项 | 现口径 | 新口径（v1.1 裁定） |
| - | - | - |
| 唯一性约束 | **无 DB 唯一约束**；Service 按 `(employee_id, work_date)` **活跃查重**（`AttendanceScheduleService.java:13`、`findOne` `AttendanceScheduleServiceImpl.java:370-379`） | **DB 层生成列式活跃唯一**：生成列 `active_shift_key = IF(is_deleted=0, CONCAT_WS(':', employee_id, work_date, shift_id), NULL)`（STORED）+ `UNIQUE (active_shift_key)` |
| 「活跃唯一」语义 | 一人一天至多一条**活跃**排班 | **同一员工同一天同一班次至多一条活跃排班；不同班次可并存（≤ `maxShiftsPerDay`）；软删行不占用唯一性** |
| 删除语义 | **逻辑删（`@TableLogic`）**：`deleteById` = `UPDATE is_deleted=1`（`AttendanceSchedule.java:39-40`、`AttendanceScheduleMapper.java:9`、`AttendanceScheduleServiceImpl.java:185`、`:332`） | **沿用逻辑删，不改 `@TableLogic`**；唯一性用**生成列**表达「仅活跃行」，软删行 `active_shift_key=NULL` 不参与唯一（与 `V16` 同范式） |

**为何不用裸三元 `UNIQUE(employee_id, work_date, shift_id)`（M-1 改正）**：实测删除为**逻辑删**，软删行仍物理存在。裸三元唯一键会把软删行计入唯一 → 「清空某班次（软删）→ 再排同班次」命中残留软删行 → **1062 冲突**，多班次「清空后再排」主流程失败。**生成列式活跃唯一**沿用本项目既有范式 `employee.phone_active`（`V16__employee_phone_unique.sql:35-39`；`db.md:215,226`）：`is_deleted=1` 行生成列取 `NULL`，MySQL `UNIQUE` 对 `NULL` 不去重（可重复）→ **软删行不占用唯一性、已清空班次可复用**（依据：MySQL 官方手册「Unique Indexes / NULL values are permitted」；项目先例 `db.md:1498`）。

**命名与索引取舍**：`uk_attendance_schedule_active_shift (active_shift_key)`。与既有普通索引 `idx_attendance_schedule_emp_date (employee_id, work_date)` **不构成最左前缀替代关系**（生成列 `active_shift_key` 是拼接值、非列序前缀），故该普通索引**保留**（多班次下它正是「按员工+日期取全部活跃班次」的取数路径，`AttendanceScheduleServiceImpl.scheduleIndex`/`findSchedule` 依赖之）——v1.0「评估是否删除冗余普通索引」的措辞**撤回**，改为**保留**。

**「改派 / 清空 / 重复排」与唯一性的交互**：
- **改派** = 事务内 `UPDATE shift_id`（现状 `AttendanceScheduleServiceImpl.java:195-197`）；同事务内，无瞬时冲突。
- **清空某班次 → 再排同班次**：清空 = 软删（生成列转 `NULL`，释放唯一槽位）→ 再排 = 插入新活跃行（生成列有值）→ **不冲突**（§3-8、§6.1-N9）。
- **重复排同一班次**：Service **先按活跃行查重**（不依赖 DB 报错），命中即按 `duplicateShiftPolicy=IDEMPOTENT` 幂等处理（§3-7），不冒泡唯一键异常。

**生成列语义的落地边界（交数据库 / 后端，本文只定口径）**：
1. 生成列表达式对**全量行**求值：`employee_id / work_date / shift_id` 均 `NOT NULL`（`V5__attendance.sql:70-73`）⇒ 无「NULL 拼串」歧义；`is_deleted=1`（或任何非 0 值）行取 `NULL`。
2. 实体 `AttendanceSchedule` **不声明** `active_shift_key` 字段（对齐既有做法：`Employee.java` 未声明 `phone_active`），避免 MyBatis-Plus 对生成列写入。
3. DDL、迁移与预检由**数据库工程师**落地（§8-1）；执行属 **C 档**，须主智能体三步授权（`项目规则1.md` §10.3）。

### 1.3 班次重叠判定

**输入**：同员工同一天的一组班次，每班次取 `attendance_shift.start_time / end_time`（`VARCHAR(5) HH:mm`，`end_time` 可 `24:00`，`V5__attendance.sql:55-56`）。

**归一化**（复用时段模型，`AttendancePeriodResolver.java:32-45`）：
```
s = minutesOfDay(start_time)          // 'HH:mm' → 当日分钟；不可解析 → NaN → 该班次判为脏数据（拒绝/留痕）
e = minutesOfDay(end_time)            // '24:00' → 1440
if e <= s:                            // 跨零点（如 22:00 → 06:00）
    if crossMidnightAsNextDay: e += 1440        // 归一到 [s, e+1440)
    else: 标记 INVALID_CROSS_MIDNIGHT → 拒绝该班次
```

**重叠判定（半开区间 `[s, e)`）**：
```
overlap(A, B) ⟺ s_A < e_B  ∧  s_B < e_A        // 加容差：s_A + tol < e_B ∧ s_B + tol < e_A
```
- **相邻不重叠**：`e_A == s_B` → `overlap=false`（合法）。**这是早班 09:00–13:00 与晚班 13:00–17:00 的场景**；
- **交叠**：`09:00–13:00` 与 `12:00–16:00` → `overlap=true`；
- **裁定建议**：`allowShiftOverlap` **默认 `false`（拒绝同员工同天时间重叠）**。理由：重叠意味着同一时刻双重在岗，与实际「一人不能在两地/两班同时在岗」矛盾，且会破坏出勤与折算的可解释性；如业务确需（如「值班+机动」叠班），单列配置开启，并在明细留痕。
- **跨零点尾段**：跨零点班次的尾段 `[1440, e')` 与**次日**早班理论上可叠。默认**不做跨日重叠检查**（`crossMidnightNextDayOverlapCheck=false`），仅登记告警；因 B5 已裁定晚班收班为 `16:00–24:00`（`end=1440`，无尾段），该分支为防御性。
- **依据**：半开区间相交判定为区间调度的标准定义（CLRS §14 区间树判定口径；区间调度以 `[start, end)` 避免「相邻即冲突」的端点歧义）。

#### 1.3.1 同日班次序号（`ordinal`）互异约束（v1.1 新增，M-4）

**`shiftOrdinal` 的计算定义（唯一定义处，防止漂移）**：

```
ordinal = shiftOrdinal(shift.start_time, middayBoundaryMinute)        // ShiftPayrollPolicy.java:64-79
  输入 ：attendance_shift.start_time（VARCHAR(5) 'HH:mm'，V5__attendance.sql:55）
  解析 ：start_time.trim().split(":") → hour*60 + minute；缺列/不可解析 → 返回 -1
  判据 ：hour*60 + minute  <  middayBoundaryMinute ?  0（早班） : 1（晚班）
  取值域：{0, 1}；-1 = 时间缺失/不可解析（调用方跳过该排班行并留痕，ShiftPayrollPolicy.java:107-114）
  依赖 ：仅 attendance_shift.start_time 与 middayBoundaryMinute（默认 720，AlgoProperties.java:121）
  不依赖：attendance_rule.check_periods（后者仅是打卡「时间窗」真源，AttendancePeriodResolver.java:58-72）
```
（注：`AttendanceScheduleServiceImpl.java:431-438` 的「按 start 升序」仅是**展示/铺排顺序**，**不是** `shiftOrdinal` 的判据；两者不得混用。）

**冲突论证：一天两班下 `shiftUnit = epochDay×2 + ordinal` 是否可能冲突？**

| 情形 | `ordinal` | `shiftUnit = epochDay×2 + ordinal` | 结论 |
| ---- | --------- | ---------------------------------- | ---- |
| 两班**跨午界**（一早一晚，如 09:00–13:00 + 17:00–21:00） | {0, 1} | `d×2+0`、`d×2+1` | **互异、无冲突** |
| 两班**同落午前**（如 06:00–09:00 + 09:00–11:00，相邻不重叠 → §1.3 放行） | {0, 0} | `d×2+0`、`d×2+0` | **冲突**：`requiredShiftSet` 去重后 `\|R\|=1`（`ShiftPayrollPolicy.java:98-118`） |
| 两班**同落午后**（如 13:00–15:00 + 15:00–17:00） | {1, 1} | `d×2+1`、`d×2+1` | **冲突**（同上） |
| 任一班 `start_time` 缺失/不可解析 | `-1` | 该行被跳过（不计入 `R`） | **不冲突但漏算**（`\|R\|` 少算，`ShiftPayrollPolicy.java:107-114`） |

**结论**：`shiftOrdinal` 仅早/晚二分，**「同一员工同一天各排班次 `ordinal` 互异」是 `shiftUnit` 不冲突的充要条件**。两班同半天 → `R` 去重后 `|R|=1` → 折算分母由 2 变 1 → 与「多班次对计薪影响 = 0」的结论**相悖**（评审 F-03 成立）。此风险 v1.0 未登记（假设 A1 仅覆盖「>2 班」与「=12:00 边界」）。

**防护（采纳评审 M-4 方案①：硬校验，默认开启）**：
1. **保存侧校验**：排班保存时对「同员工同天」的目标班次集合计算 `ordinal`；**若出现重复 → 拒绝整批**（`@Transactional` 原子，不部分落库，`AttendanceScheduleServiceImpl.java:145`），返回冲突明细（哪两个班次的 `ordinal` 相同、对应 `start_time`）。
2. **参数**：`hrm.algo.attendance.requireDistinctOrdinalPerDay`（默认 **`true`**；见 §2）。关闭即退化为评审**方案②**（登记为已知边界 + 前端阻止同半天双选 + 计薪侧显式告警）；**默认必须为 `true`**（关闭会导致计薪静默少算）。
3. **`start_time` 可解析性校验**：保存时对每个目标班次校验 `attendance_shift.start_time` 可解析（非 `-1`），否则拒绝（防 `|R|` 漏算）。
4. **错误码**：**由后端按 `api.md` 定稿**（建议归入参数校验失败类，`§8-10`；算法不自定错误码）。
5. **兜底（防校验被误关）**：计薪侧 `requiredShiftSet` 若检测到「当日排班行数 > 去重后 `R` 中当日单元数」→ 提示**显式告警** `WARN_ORDINAL_COLLISION`（登记 §9），**不静默少算**（该告警的常量与实现由后端在 `ShiftPayrollPolicy` 侧落，本文只定语义）。

### 1.4 「早+晚=全天」的产品语义

**建模建议：不引入冗余字段，全天 = 派生概念。**

| 方案 | 定义 | 取舍 |
| - | - | - |
| **A（推荐，默认）** | `isFullDay(e,d) = |当日有效排班班次数| ≥ fullDayShiftCount`（默认 2） | 与用户口径 B1「1 班次=半天、2 班次=1 天」**逐字一致**（`algo-payroll-shift.md` §1-B1）；零冗余 |
| B（可选增强） | `isFullDay(e,d) = 班次区间并集覆盖 [min(s), max(e)] 且最大间隙 ≤ fullDayMaxGapMinutes` | 更贴近「物理上覆盖一整天」，但需处理跨零点与休息间隙，复杂度与解释成本更高 |

**为何不落字段**：① 冗余字段会与排班行集合形成**双真源**，改派/清空/软删时须同步维护，必漂移（违反 `项目规则1.md` §2「精简」「权威不重复」）；② 全天是**读时派生量**，无独立事务语义；③ 若前端需展示「整天」标签，由后端读时计算后**在出参派生字段返回**即可（不改表）。

### 1.5 打卡应到/实到口径：`period_index` / `period_name` 与班次的对应

**对齐点（关键）**：`ShiftPayrollPolicy` 的 `R` 用 `shiftOrdinal(start_time)`（`ShiftPayrollPolicy.java:64-79`），`A` 直接把记录的 `period_index` 当序号（`:183-186`）。故：

```
班次序号 ordinal = shiftOrdinal(shift.start_time, middayBoundaryMinute)   // < 720 → 0(早)，否则 1(晚)；定义见 §1.3.1
打卡记录 period_index  := 该班次 ordinal
打卡记录 period_name   := 时序优先级判别（哨兵/空/序号，ShiftPayrollPolicy.java:152-189）；多班次新数据建议写班次名快照
```

**映射规则表**：

| 项 | 规则 | 依据 |
| - | ---- | ---- |
| 班次序号 | **由 `middayBoundaryMinute`（默认 720=12:00）判定早/晚**（< 720 → 0，否则 1；缺列 → -1）；「按 `start_time` 升序」仅是**展示顺序**、**不是序号判据** | `ShiftPayrollPolicy.java:64-79`、`AlgoProperties.java:121`；展示顺序见 `AttendanceScheduleServiceImpl.java:431-438` |
| `period_index` 取值 | **= 班次序号**（0=早、1=晚）；保证 `A∩R` 映射正确；**须满足 §1.3.1 互异约束**（同日两班序号不得相同） | `ShiftPayrollPolicy.java:183-186` |
| `period_name` | 记录 → 班次走**三态优先级**（`ShiftPayrollPolicy.java:152-189`）。**实测**：单班次模型写 `periods.get(0).name()`（`AttendanceRecordServiceImpl.java:354-355`），即**规则时段名快照**；**仅当 `check_periods` 为空**时 `AttendancePeriodResolver.resolve` 才兜底为常量 `全天班`（`AttendancePeriodResolver.java:58-71`、`AttendanceConstants.java:58`）。**v1.0「存量全为 `全天班` 哨兵」的前提不成立**，详见 §1.8 | `algo-payroll-shift.md` §2.1 三态优先级；本项目实测行号如上 |
| 时段（窗口）来源 | 打卡时间窗仍以 `attendance_rule.check_periods` 为**时间真源**（`AttendancePeriodResolver.java:58-72`） | `V5__attendance.sql:35`、`:6-9` |

> **一致性不变式（必须成立，见 §8-9）**：启用多班次时，`attendance_rule.check_periods`（按数组顺序）必须与 `attendance_shift`（按 `start_time` 升序）**一一对应且顺序一致**，其下标即班次序号。否则「规则时段」与「排班班次」两套序号漂移 → 打卡窗口与计薪序号各说各话。

**应到/实到粒度（FC6；⚠️ 候选新口径 = per-shift，须用户裁定，未裁定前不得据此实现）**：

| 指标 | 现口径（`AttendanceSummaryPolicy.java:35-59`） | 候选新口径（**待裁定**） |
| ---- | -------- | ------------ |
| 应到 | 当天有排班人数（`scheduleRows().size()` 实为**行数**） | 应到**班次数** = 有效排班行数 |
| 实到 | 有有效 ON 卡的**员工去重**（`:39,56`） | 实到**班次数** = `\|A ∩ R\|`（有效 ON 卡映射到班次后与 `R` 取交） |
| 缺卡 | `max(0, 应到 − 实到)`（`:57`） | `\|R \ (A ∪ L)\|`（与计薪 `absent` 同构） |
| 正常/迟到 | 有效 ON 卡**卡条数** | 不变（卡条数 = 班次粒度天然一致） |
| 早退 | 有效 OFF 卡条数 | 不变 |

> **状态：待用户裁定（§11.4 口径红线）。未裁定前不得据此实现。** 该改动**同时修正**「应到按行、实到按人去重」的粒度混用（FC6）；因改变出勤/考核呈现口径，须用户裁定后方可改 `AttendanceSummaryPolicy` / `AttendanceDetailPolicy`，并同步 `api.md` §4.7 概况/明细语义与前端。

**两种口径对报表数字的影响对照（同一「2 班全勤」样例，供用户裁定参考）**：

| 员工/日 | 排班（活跃行） | 有效 ON 卡 | 口径①「人数去重」（现） | 口径②「班次粒度」（候选） |
| ------- | -------------- | ---------- | ---------------------- | ------------------------- |
| E 一天两班全勤 | 2 行 | 2 条 | 应到 2 = **行数**、实到 **1**（按 employeeId 去重）、缺卡 = `max(0,2-1)=` **1**（**误判**） | 应到 2 = **班次数**、实到 `\|A∩R\|=` **2**、缺卡 = **0**（**正确**） |
| E 一天两班、晚班缺卡 | 2 行 | 1 条 | 应到 2、实到 1、缺卡 1（**数值巧合正确，但语义错**：把「晚班缺卡」误表达为「1 人缺勤」） | 应到 2、实到 1、缺卡 **1**（语义为「晚班 1 个班次缺卡」，可定位到班次） |
| E 一天一班全勤（存量） | 1 行 | 1 条 | 应到 1、实到 1、缺卡 0 | 应到 1、实到 1、缺卡 **0**（**与口径①数值一致** ⇒ 单班次存量零变化） |

> 结论（供裁定）：口径②在**多班次**下修正常见误判并给出**班次级可解释性**；在**单班次**下与口径①**逐位一致**（兼容）。故推荐口径②，但**须用户裁定**以变更出勤/考核呈现口径（`项目规则1.md` §11.4）。

### 1.6 迟到/早退/缺卡判定粒度

**裁定建议：按「每个班次各自判定」，不做「按天合并判定」。**

| 判据 | 说明 |
| - | ---- |
| 时间基准唯一 | 每班次有独立 `start_time/end_time`，迟到/早退阈值（`late_threshold_min`/`early_leave_threshold_min`）逐班次施加，语义无歧义（`AttendanceCheckPolicy.java:179-192`、`:206-224`） |
| 与计薪一致 | 计薪在**班次单元**上做集合运算（`ShiftPayrollPolicy.java:242-288`）；考勤若按天合并，会与计薪出现**双口径** |
| 迟到粒度既定 | 用户已裁定迟到 `PER_CARD`（按次，= 每张有效 ON 迟到卡计 1 次，`algo-payroll-shift.md` §1-T5），与 per-shift 天然一致 |
| 退化兼容 | 单班次下 per-shift ≡ per-day → 存量**零变化** |

**缺卡**：按班次判定；某班次无有效 ON 卡且无已批请假覆盖 → 该班次计缺卡（= 计薪 `absent` 单元）。**不再**用 `应到人数 − 实到人数`（FC6）。
**明细行粒度**：`AttendanceDetailPolicy` 现按员工取「最早 ON/OFF」代表（`:99-111`），多班次下需改为 **per `(employee, shift)`** 行；否则两班事实被折叠为一行、丢失可解释性（属契约变更，§8-4）。

### 1.7 计薪折算口径（PRORATED）——多班次下的可计算定义（关键）

**前置事实（不新增口径）**：`algo-payroll-shift.md` §1-B1/B2/T2 已由用户裁定：**每天 2 个班次（早+晚）、1 班次 = 半天、2 班次 = 1 天**；折算 `= basicSalary × (实出班次 ÷ 应出班次)`；只折算基本工资（`proratedFields=[basicSalary]`）。

**「班次」是计数单位还是时长单位？→ 计数单位（半天单元），且非时长。**
- `R = { shiftUnit(workDate, ordinal) | 排班行 }`，`shiftUnit = epochDay×2 + ordinal`（`ShiftPayrollPolicy.java:54-56`）；
- **一天两班 = 2 个应出班次**（`|R|` 计 2），**不是 1**；
- 一切迟到/缺卡/请假都在**单元集合**上运算，**与每班时长无关**。

**可计算定义（唯一真源引用）**：
```
R = 排班行按 (work_date, 班次序号) 去重                       // 应出班次集合，|R| 计班次个数
A = 有效上班卡（check_type=ON 且 status≠ABNORMAL）按 (work_date, period_index↔班次序号) 映射到单元
L = 已批请假单元 ∩ R
  = |A ∩ R|                                                  // 实出班次（取交，禁止直接用打卡条数）
absent = |R \ (A ∪ L)|                                       // 缺勤班次
basic_prorated = |R| > 0 ? basicSalary × |A∩R| / |R| : (zeroSchedulePolicy=FULL_BASIC ? basicSalary : 0)
```
（映射实现见 `ShiftPayrollPolicy.java:98-118`、`:152-189`、`:242-288`、`:297-306`；落点为 `ProratedItemResolver.java:38-71`。）

**算例**（basic=1500）：

| 场景 | 应出 `\|R\|` | 实出 `\|A∩R\|` | 折算 | 说明 |
| ---- | ---: | ---: | ---: | ---- |
| 单日两班全出勤 | 2 | 2 | 1500×2/2 = **1500** | 一天两班 = 满额 |
| **单日应出 2 班、实出 1 班** | 2 | 1 | 1500×1/2 = **750** | 缺 1 个半天单元 |
| **单日应出 2 班、实出 3 班（异常：多打卡）** | 2 | ≤2 | 1500×2/2 = **1500** | **`A∩R` 取交 ⇒ 多打卡不放大折算**（封顶 = 满额） |
| 整月 30 天×2 班=60，请半天假 | 60 | 59 | 1500×59/60 = **1475** | 与 `algo-payroll-shift.md` §2.4.1 逐位一致 |

> **异常「应出 2 实出 3」的处置**：多出的打卡落到 `R` 之外（如重复卡、非排班次序号）→ **被 `A∩R` 丢弃**，折算不超过满额；同时以 `PERIOD_NAME_MISSING`/重复卡留痕交人工复核（沿用 `algo-payroll-shift.md` §2.1 三态与 §8 降级）。**不新增判定逻辑**。

**结论一句话（供汇报）**：多班次下 `PRORATED` = 以**班次（半天单元）为计数单位**的 `basicSalary × |A∩R| ÷ |R|`，**一天两班即 2 个应出班次**，`A∩R` 取交使其**只减不增**（多打卡封顶为满额）。

### 1.8 兼容性论证：现有单班次数据行为零变化（v1.1 前提更正）

设存量数据满足「每 `(employee, work_date)` 至多 1 条**活跃**行」（现状 Service 查重 `AttendanceScheduleService.java:13` 所保证；**软删行可能多条但均非活跃**，不计入该前提）。

| 环节 | 新模型下的行为 | 是否零变化 |
| ---- | -------------- | :--------: |
| 生成列式活跃唯一 | 存量活跃行每 `(emp,date)` ≤1 ⇒ `(emp,date,shift)` 活跃键天然唯一；**软删行生成列 `NULL` 不参与唯一** ⇒ 加键**不拒绝任何存量行**（含历史软删行） | ✅ |
| `R` 计算 | 每天 1 条活跃行 ⇒ `\|R\|` 逐日退化为 1 单元（`ShiftPayrollPolicy.java:98-118`） | ✅ |
| 打卡 `period_index` | 存量单班次写 `period_index=0`（`AttendanceRecordServiceImpl.java:354`） | ✅ |
| 打卡 `period_name` | **更正（v1.1）**：存量写的是**规则时段名快照** `periods.get(0).name()`（`AttendanceRecordServiceImpl.java:355`），**不是** v1.0 所称「`全天班` 哨兵」。三态优先级下（`ShiftPayrollPolicy.java:152-189`）：① 命中哨兵 `全天班`（仅当 `check_periods` 为空才可能）→ 覆盖当日全部班次；② 为空 → 当日 ≤1 班次归属该唯一班次；③ 非空非哨兵（= 规则时段名）→ 按 `(work_date, period_index=0)` 精确定位。**三种情形在「一天一行」下均映射到该唯一班次**（优先级的差集不影响结果） | ✅ |
| `findSchedule` 首条语义 | 每天仅 1 条活跃行 ⇒ 首条即唯一，与现状逐位等价（`:532-540`） | ✅ |
| 重叠判定 | 单班次无重叠对 ⇒ 恒不触发 | ✅ |
| `ordinal` 互异校验（§1.3.1） | 单班次无第二班次 ⇒ 恒不触发 | ✅ |
| `isFullDay` | `fullDayShiftCount=2` ⇒ 单班次 `\|排班\|=1 < 2` ⇒ `false`（与「一天一班不是整天」一致） | ✅ |
| 应到/实到 per-shift 化（**待用户裁定**） | 单班次下 `\|R\|`=行数=人数、`\|A∩R\|`=实到人数 ⇒ **与现状数值一致**（§1.5 对照表末行） | ✅（数值） |
| 账期隔离 | `shiftModelFromMonth` 使历史账期走旧路径（`AlgoProperties.java:115`） | ✅ |

> **结论**：只要 `maxShiftsPerDay=2` 且存量满足「一天一行」，本方案对存量数据、历史工资单、既有接口数值**零变化**；多班次仅对**新产生**的「一天多行」数据生效。**生成列式活跃唯一比 v1.0 的裸三元键更兼容**：历史软删行不再构成加键障碍。
>
> **残留待核实项（`[推导]`，本机无 JDK/MySQL，未运行）**：若存量**单班次**的 `attendance_shift.start_time ≥ middayBoundaryMinute`（`ordinal=1`，纯晚班站）而其打卡记录 `period_index=0`（单班次模型固定写 0），则存在「`A` 落 unit 0 / `R` 落 unit 1 ⇒ `A∩R=∅`」的理论错配。此为 **S2b 既有行为**（本修订不引入、不改变），**不属本需求回归范围**；登记为**待核实项**交后端 / 财务核验存量是否存在此类站点（建议一并纳入 §8-10 的错误码/校验讨论）。

---

## 2. 参数表（`hrm.algo.attendance.*`，禁止内联阈值）

> 现状 `AlgoProperties.Attendance` 仅含 `anomaly`（`AlgoProperties.java:172-192`），本方案**新增 `schedule`/`multiShift` 段**；键名遵循既有前缀体例，默认值为本方案建议值，**实现侧必须逐键外置**（`项目规则1.md` §11.4、反模式 A03）。

| 键名 | 含义 | 默认值 | 取值范围 | 影响 |
| ---- | ---- | ------ | -------- | ---- |
| `hrm.algo.attendance.allowShiftOverlap` | 是否允许同员工同天班次时间重叠 | `false` | `true/false` | `false`：重叠提交被拒（§1.3）；`true`：允许，明细留痕 |
| `hrm.algo.attendance.maxShiftsPerDay` | 单日班次上限 | `2` | `1..N`（建议 ≤2） | 上限 2 与用户 B1「每天 2 班」一致；**>2 会与计薪序号编码冲突**（`shiftUnit=epochDay×2+ordinal` 仅 0/1）→ 本期封顶 2，>2 见 §10 |
| `hrm.algo.attendance.fullDayShiftCount` | 判「整天」的班次数阈值 | `2` | `1..maxShiftsPerDay` | 决定 `isFullDay`（§1.4） |
| `hrm.algo.attendance.overlapToleranceMinutes` | 重叠判定容差（分钟） | `0` | `0..60` | `0`=严格半开区间；>0 容忍端点轻微重叠 |
| `hrm.algo.attendance.crossMidnightAsNextDay` | `end_time ≤ start_time` 是否视为跨零点顺延 | `true` | `true/false` | `true`：`e += 1440` 归一；`false`：判脏数据拒绝 |
| `hrm.algo.attendance.crossMidnightNextDayOverlapCheck` | 跨零点尾段与次日班次是否查重叠 | `false` | `true/false` | 默认关闭（B5 晚班 `end=24:00` 无尾段），开启则登记告警 |
| `hrm.algo.attendance.duplicateShiftPolicy` | 同员工同天重复排同一班次的处置 | `IDEMPOTENT` | `IDEMPOTENT/REJECT` | `IDEMPOTENT`：视为 no-op（不新增、不报错，§3-7）；`REJECT`：报错 |
| `hrm.algo.attendance.requireDistinctOrdinalPerDay` | **同日同员工各排班次 `ordinal` 是否必须互异（v1.1 新增，M-4）** | **`true`** | `true/false` | `true`：同日两班同半天（`ordinal` 相同）→ **拒绝整批**（§1.3.1）；`false`：允许但**计薪静默少算风险**，须计薪侧告警兜底。**默认必须 `true`** |
| `hrm.algo.attendance.absentGranularity` | 应到/缺卡粒度 | `PER_SHIFT` | `PER_SHIFT/PER_DAY` | `PER_SHIFT`=班次粒度（候选）；`PER_DAY`=旧按天去重。**⚠️ 该项属出勤/考核口径变更，须用户裁定后方可生效（未裁定前不得启用，§8-4）** |
| `hrm.algo.payroll.middayBoundaryMinute` | 早/晚序号界值（**复用既有键，不新建**） | `720` | `0..1440` | 决定 `shiftOrdinal`；**排班序号必须与计薪同源**（`AlgoProperties.java:121`）。**恰为 12:00 起始的班次判为晚班（`AlgoProperties.java:119` 已警示）** |

> **不新增的键**：`proratedFields`/`absentFinePerShift`/`shiftModelFromMonth`/`legacyPeriodSentinel`/`lateGranularity` 等沿用 `algo-payroll-shift.md` §7（`AlgoProperties.java:109-137`），本方案不重复定义、不改默认。

---

## 3. 边界用例（逐条给期望结果，全部可判定）

> 记号：员工 `E`、日期 `D`；`R`=应出班次集合；`|A∩R|`=实出班次；`absent`=缺卡班次。除注明外，`allowShiftOverlap=false`、`maxShiftsPerDay=2`、`fullDayShiftCount=2`、**`requireDistinctOrdinalPerDay=true`**、`duplicateShiftPolicy=IDEMPOTENT`。

| # | 用例 | 期望结果 |
| - | ---- | -------- |
| 1 | 同天 **2 班**（早 09:00–13:00 + 晚 17:00–21:00） | 保存 2 行；`isFullDay=true`；`\|R\|=2`；两班均有有效 ON 卡 → `\|A∩R\|=2`、`absent=0` |
| 2 | 同天 **3 班** | 超过 `maxShiftsPerDay=2` → **拒绝**（错误码由后端定稿，建议 400 或经 `api.md` 新增 91xx）；**不落库**（整批原子） |
| 3 | 同天 **4 班** | 同 #2 → 拒绝；即使 `maxShiftsPerDay` 放开到 4，**计薪序号仅 0/1**（`ShiftPayrollPolicy.java:54-56`）→ 仍应拒绝或走 §10 扩展 |
| 4 | **相邻不重叠**（09:00–13:00 + 13:00–17:00） | `e₁==s₂` → 非重叠 → **允许** |
| 5 | **重叠**（09:00–13:00 + 12:00–16:00） | `overlap=true` 且 `allowShiftOverlap=false` → **拒绝**并给出冲突明细（如「与班次 X 时间重叠」） |
| 6 | **跨零点**（晚班 22:00–24:00） | +0 尾段，允许；记录 `end=1440`；`work_date` 取班次**开始日**（`algo-payroll-shift.md` §2.1 边界「晚班跨零点」） |
| 6' | 跨零点且 `end_time < start_time`（22:00–06:00） | `crossMidnightAsNextDay=true` → 归一 `[1320, 1800)`，允许并留痕；`false` → 判脏数据拒绝 |
| 7 | **同一班次重复排**（同天两次提交晚班） | `duplicateShiftPolicy=IDEMPOTENT` → Service **按活跃行查重**命中既有行**不新增**（`saved` 计数口径须由后端明确定义，见 §8-3）；`REJECT` → 报错。**注意**：查重只比活跃行（`is_deleted=0`），历史软删行不参与 |
| 8 | **排班后清空其中一个班次**（原早+晚，清晚班） | 晚班行**逻辑删**（`is_deleted=1`，`active_shift_key` 转 `NULL` 释放唯一槽位），早班行保留；`isFullDay` 由 `true` 变 `false`；`\|R\|` 由 2 → 1；**早班打卡记录不受影响**；**清空后再排同晚班不撞唯一键**（见 #18） |
| 9 | **排班变更后已产生的打卡记录** | **历史事实不改写**：`attendance_record` 只增不改（打卡事实表，`V5__attendance.sql:108` 注释）；删班次后该卡落在 `R` 之外 → 被 `A∩R` 自然丢弃，**不放大、不克扣**；折算分母 `\|R\|` 按**变更后**排班计算（走覆盖式，无快照）→ **已在草稿期外产生的工资单**由账期锁保护（`algo-payroll-shift.md` §8.2-N3） |
| 10 | **多班次下某班次缺卡**（早到、晚未打 ON） | `\|R\|=2`、`\|A∩R\|=1`、`absent=1`；折算 750（basic=1500）；**仅晚班计缺卡，早班正常**（per-shift） |
| 11 | **多班次与补卡（makeup）交互** | 补卡槽位键 = `(employee, work_date, period_index, check_type)`（`AttendanceMakeupServiceImpl.java:189-197`）；`period_index`=缺失班次序号 → 补的正是该班次；审批通过写 `source=MAKEUP` 记录（`:205-249`）→ 该班次由 absent 转 attended；**已存在同槽位有效卡则不再补录**（`:217-220`，防重复） |
| 12 | 多班次计薪：**应出 2 班、实出 1 班** | 1500×1/2 = **750**（§1.7） |
| 13 | 多班次计薪：**应出 2 班、实出 3 班（异常）** | `A∩R` 取交 → ≤2 → 1500×2/2 = **1500**（封顶）；多出卡留痕复核，**不放大** |
| 14 | 多班次 + 请假半天（晚班已批假） | `L` 含晚班单元；`absent = \|R\(A∪L)\| = 0`；折算 `= 1500×1/2 = 750`（请假只折算不罚款，`algo-payroll-shift.md` §1-B3） |
| 15 | 单班次存量数据（1 条活跃行/天，`period_name` = **规则时段名快照**，仅在 `check_periods` 为空时为 `全天班`） | `\|R\|` 逐日=1；三态优先级均定位到该唯一班次；折算与现状逐位一致（§1.8、FC10） |
| 16 | 员工跨驿站？ | 员工 `station_id` 唯一归属（`Employee.station_id`）；排班行的 `station_id` 由 Service 校验同驿站（`:191-194`）；活跃唯一键不加 `station_id`（生成列仅拼 `employee_id/work_date/shift_id`），避免同员工跨站可分排 → 语义漏洞 |
| **17（v1.1 新增，M-4）** | **同日两班同半天**（06:00–09:00 + 09:00–11:00，相邻不重叠）；`requireDistinctOrdinalPerDay=true` | 两班 `ordinal` 均为 **0**（`ShiftPayrollPolicy.java:75`）→ **拒绝整批**（`@Transactional` 原子，不落库），返回冲突明细（两班 `start_time` 与相同 `ordinal`）；错误码由后端按 `api.md` 定稿 |
| **17'** | 同 #17 但 `requireDistinctOrdinalPerDay=false` | 允许落库；计薪 `R` 去重后 `\|R\|=1` → **折算少算**；**必须**由计薪侧 `WARN_ORDINAL_COLLISION` 显式告警（不静默），并登记人工复核（§1.3.1、§9） |
| **18（v1.1 新增，M-1）** | **清空后再排同班次**（早+晚 → 清晚班（软删）→ 再排同晚班） | 清空 = 逻辑删（`active_shift_key=NULL`）→ 重排 = 新增活跃行；**不产生 1062 唯一键冲突**；断言「同 `(emp,date,shift)` 活跃行仅 1 条、软删行可有 0..n 条」（§6.1-N1/N9） |

---

## 4. 算法选型与复杂度（四件套 ②）

### 4.1 排班保存：覆盖式 → 集合差量

**问题**：把某员工某天的「当前班次集合 `C`」调整为「目标集合 `T`」（`|C|,|T| ≤ maxShiftsPerDay`）。

**增量算法（集合差量，推荐）**：
```
读入该 (station, date 区间) 全部「活跃」排班（is_deleted=0）→ 建索引 idx: (employee, workDate) → Set<shiftId>   // 一次预取，避免 N+1
对每个待保存项 (e, d, T)：                                  // |T| ≤ maxShiftsPerDay
    C = idx.getOrDefault((e,d), ∅)                        // C 只含活跃行（软删行不含）
    ins = T \ C        // 需 insert（含「活跃唯一」冲突防御：命中活跃行即视为已存在，不依赖 DB 报错）
    del = C \ T        // 需 delete（逻辑删：deleteById → is_deleted=1，AttendanceSchedule.java:39-40）
    // T ∩ C 保持不变（no-op）
```
**复杂度**：预取 `O(M log M)`（`M` = 区间活跃排班行数，排序/建索引）；逐项差量 `O(Σ(|C|+|T|)) ≤ O(2N·maxShiftsPerDay)`；写操作 `O(|ins|+|del|)`。**最坏情形**（每项都整组更换）：`O(N·maxShiftsPerDay)` 次 insert + 同量逻辑删，与现状「N 条 update」同阶（都不劣化数量级）。
**为何不用「先清后建」**：会产生瞬时空档、放大写放大（WAL/binlog）；差量最小化写。**注意**：逻辑删下 `del` 后软删行仍占用存储，行数随「改派/清空」操作单调增长（每次改派 = 1 次软删 + 1 次 insert）→ 长期需**归档/清理策略**（不在本期，登记 §10-T6）。

### 4.2 打卡判定（每日每员工）

| 环节 | 现状 | 多班次 | 复杂度 |
| ---- | ---- | ------ | ------ |
| 取排班 | `findSchedule` 取首条（`:532-540`） | 按 `(employee, work_date)` 取**全部行**并缓存为 `Map<emp, Map<shiftOrdinal, shift>>` | 一次索引查询 `O(log M + k)`，`k ≤ maxShiftsPerDay` |
| 班次选择 | 单班次模型无条件用首条（`:303-316`） | 有 `periodIndex` → 用 `periodIndex` 定位；无 → 单班次沿用首条，多班次**拒绝或要求显式 `periodIndex`** | `O(1)` |
| 去重 | 时段模型 `(emp,date,periodIndex,type)`；单班次 `(emp,date,type)`（`:346-350`） | 统一为 `(emp, date, shiftOrdinal, type)` | `O(1)`（走 `idx_attendance_record_emp_date`，`db.md:673`） |
| 迟到/早退 | 逐卡（`:179-192`） | 不变（逐班次各自判定） | `O(1)` |

**影响面**：每日每员工的查询从「1 次取首条」变「1 次取 ≤2 条」，**同一索引命中、同数量级**；无新增扫描。

### 4.3 S3 整站排班算法扩展（登记，非本轮实现）

**现状（v1.1 直接行号取证，评审 §七-11 由「待核实」升「已核实」）**：`SchedulePlanner.Solution` 为 `record Solution(int[][] shift)`（`SchedulePlanner.java:50`），维度约定 `shift[employee][day]` = 班次下标、`-1` = 轮休（`:22`）⇒ **单值解空间**；目标 `J` 见类注释（`:9-16`），贪心 `O(D·E·S)`、评估 `O(D·E)`、退火 `O(iter·D·E)`（`:23`）。

**多班次扩展（建议路径）**：
- **解空间**：`shift[employee][day] ⊆ {0..S-1}`（集合，`|·| ≤ maxShiftsPerDay`）或 `boolean[employee][day][shift]`；
- **规模**：完整多班次指派（每人每天可多班）属**集合划分/多指派**问题，含 `maxShiftsPerDay` 约束时**组合规模指数级**，普适最优需约束求解器（Timefold / OR-Tools CP-SAT，见 §5-D3）；
- **本期可行降级**：**两阶段贪心 + 局部搜索**——① 沿用现单班次算法产出主班次；② 第二遍在**未满 `maxShiftsPerDay`** 且**不违反连续工作/覆盖约束**的格子上，贪心补第 2 班（优先补缺口班次，用现 `GREEDY_DEFICIT_SCALE` 口径，`SchedulePlanner.java:35`）。评估仍 `O(D·E·S)`（逐格判定集合），退火 `O(iter·D·E·S)`。
- **失败降级不变**：返回贪心解 + 违规清单（`:96-109`），不抛异常；固定种子 `DEFAULT_SEED=0x1f2e3d4c`（`:28`）保证可复现。

> 该扩展列为 **`TODO(扩展)`（§10-T2）**，并作为**需上游确认项**（§8-7）：改 `Solution` 结构会波及 `AttendanceScheduleServiceImpl.buildFixedMatrix` 与出参。

### 4.4 计薪批量折算

| 环节 | 复杂度 | 说明 |
| ---- | ------ | ---- |
| `requiredShiftSet` | `O(S)` | `S`=排班行数；多班次满月 `S ≤ 62`（`ShiftPayrollPolicy.java:98-118`） |
| `attendedShiftSet` | `O(R)` | `R`=打卡条数；满月双班 `≤124`（`:152-189`） |
| `compute`（含差集） | `O(S + R + L·U)` | 单员工单月（`:242-288`） |
| **批量 N 员工** | `O(Σ(S_e + R_e + L_e·U_e))` | 逐员工独立、无跨员工状态 |
| 现状基准 | 500 员工 × 满月 **129.24 ms / 0.2585 ms·人** | `algo-payroll-shift.md` §2.4.3（既有实跑，含基准外纯算法段） |

> **多班次对计薪的影响 = 0**：折算逻辑**本就按班次单元**，排班「一天一行 → ≤2 行」只增加 `S`（≤+100%），复杂度同阶、无算法改造。

---

## 5. 依据来源（四件套 ③）

| # | 依据 | 来源（可查） |
| - | ---- | ------------ |
| D1 | 半开区间相交判定 `[s,e)`（相邻不算重叠） | Cormen, Leiserson, Rivest, Stein, *Introduction to Algorithms*, 3rd ed., ch.14（区间树/区间调度）；CLRS 区间调度以 `[start, finish)` 定义 |
| D2 | 集合差量最小化写（`T\C` 插、`C\T` 删、`T∩C` no-op） | 同上，集合基本性质；工程落点 = `Set<T>` 语义 |
| D3 | 多指派/排班的多约束优化（多班次完整解）选型 | TimefoldAI/timefold-solver（`https://github.com/TimefoldAI/timefold-solver`，约束求解器）；Google OR-Tools CP-SAT（`https://developers.google.com/optimization/cp/cp_solver`）；SalesforceAIResearch/agentforce-adlc（`https://github.com/SalesforceAIResearch/agentforce-adlc`）方法论 |
| D4 | 计薪「班次 = 半天单元、一天≤2 单元」编码 | 项目内 `ShiftPayrollPolicy.shiftUnit`（`ShiftPayrollPolicy.java:54-56`）；用户裁定 `algo-payroll-shift.md` §1-B1 |
| D5 | 记录→班次三态优先级（哨兵/空/序号） | 项目内 `algo-payroll-shift.md` §2.1；实现 `ShiftPayrollPolicy.java:152-189` |
| D6 | 打卡时间真源与分钟归一（`24:00`→1440） | 项目内 `AttendancePeriodResolver.java:32-45`、`:58-72`；DDL `V5__attendance.sql:35,39` |
| D7 | 唯一键/索引规范（含「生成列唯一不替代普通索引」的取舍判据） | 项目内 `db.md` §8.3.3（`:634-642`）；MySQL 官方索引手册（Unique / 索引设计）；`V16` 保留 `idx_employee_phone` 的先例（`V16__employee_phone_unique.sql:44`） |
| D8 | 金额精度与舍入（`BigDecimal` `HALF_UP` 到分） | Oracle Java SE API：`java.math.BigDecimal#setScale(int, RoundingMode)` |
| D9 | 纯函数 + 固定种子可复现 | 项目内 `SchedulePlanner.DEFAULT_SEED`（`:28`）、`Mulberry32`（`:390-409`）；原型 `algo-scripts/lib/rng.mjs` |
| **D10（v1.1）** | **MySQL 生成列 + `UNIQUE` 表达「仅活跃行唯一」**（`is_deleted≠0` 行生成列置 `NULL`；`UNIQUE` 对 `NULL` 不去重 ⇒ 软删行不占用唯一） | MySQL 官方手册：*CREATE TABLE … Generated Columns*、*Unique Indexes*（「A UNIQUE index permits multiple NULL values」）；项目先例 `V16__employee_phone_unique.sql:10-17,35-39`、`db.md:215,226,1498` |
| **D11（v1.1）** | **`@TableLogic` 逻辑删语义**（`deleteById` = `UPDATE is_deleted=1`） | MyBatis-Plus 官方文档（`@TableLogic` 逻辑删除）；项目实现 `AttendanceSchedule.java:39-40`、`AttendanceScheduleMapper.java:9`（裸 `BaseMapper`，无覆盖） |
| **D12（v1.1）** | **同日两班同半天 ⇒ `|R|` 去重少算**（充要条件 = `ordinal` 互异） | 项目实现 `ShiftPayrollPolicy.shiftOrdinal`（`:64-79`）、`shiftUnit`（`:54-56`）、`requiredShiftSet` 去重（`:98-118`） |

> **说明**：本项目**折算口径由用户裁定**（B1/B2），本文不改；D1/D3 仅支撑「重叠判定」与「多班次完整优化」的算法选型，不引入新口径。

---

## 6. 可验证指标与基准数据（四件套 ④）

> **取证声明（必读）**：本机**无 JDK / MySQL**，**未运行任何代码/SQL**。下列为**设计态验收指标与断言**（标 `[推导]`），Java/DB 实测**收敛到服务器阶段复核**。计薪折算的**既有实测基准**引 `algo-payroll-shift.md` §2.4（已实跑）。

### 6.1 可判定验收断言（实现后逐条可验）

| # | 断言 | 依据 | 如何验证 |
| - | ---- | ---- | -------- |
| N1 | 同 `(employee_id, work_date, shift_id)` 至多 **1 条活跃行**（`is_deleted=0`）；软删行不受限 | 生成列式活跃唯一（§1.2、`active_shift_key`） | 并发/重复提交同班次 → 断言活跃行不产生第 2 条；直连库断言 `active_shift_key` 唯一索引存在 |
| N2 | `allowShiftOverlap=false` 时重叠班次提交被拒且**整批不落库** | 半开区间判定（§1.3）+ `@Transactional`（`:145`） | 提交含重叠对 → 断言返回错误且库内**活跃行**数不变 |
| N3 | `\|A∩R\| ≤ \|R\|`；恒等式 `\|R\(A∪L)\| + \|A∪L\| = \|R\|` | 集合语义（`ShiftPayrollPolicy.java:252-266`） | 批量样本跑 `compute` → 断言无违例（既有基准 500 人 0 违例） |
| N4 | 单班次存量数据折算与现状逐位一致 | §1.8 | 对存量样本前后比对 `basic_prorated` |
| N5 | 应到/缺卡在 2 班全勤下 `absent=0`（修正 FC6） | §1.5 | 造 2 班全勤数据 → 断言 `absentCount=0`（现状会得 1）。**⚠️ 仅当用户裁定采纳「班次粒度」口径后方可断言** |
| N6 | 多班次出参包含全部班次（非仅首条） | §1.5/§8-3 | 2 班数据 → 断言 `shiftIds.length=2` |
| N7 | 相同输入重跑结果逐位一致（固定种子） | `DEFAULT_SEED`（`:28`） | S3 同 `(station, date 区间)` 重跑 → 断言解逐位相同 |
| **N8（v1.1 新增，M-4）** | `requireDistinctOrdinalPerDay=true` 时同日两班同半天（`ordinal` 相同）**被拒整批** | §1.3.1 | 提交 06:00–09:00 + 09:00–11:00 → 断言返回错误、库内活跃行不变；`false` 时断言产生 `WARN_ORDINAL_COLLISION` 告警且 `\|R\|=1` |
| **N9（v1.1 新增，M-1）** | **清空（软删）后再排同班次不产生 1062** | §1.2、§3-8 | 排晚班 → 清空 → 再排同晚班 → 断言成功且同 `(emp,date,shift)` 活跃行恰 1 条、软删行 ≥1 条 |

### 6.2 设计态复杂度与量级（`[推导]`）

| 环节 | 现状 | 多班次 | 量级（E=50, D=7, maxShiftsPerDay=2） |
| ---- | ---- | ------ | ------------------------------------ |
| 保存 N 项 | `O(N)` update | `O(N·maxShiftsPerDay)` 差量写 | N≤200 项 → 写 ≤400 次 `[推导]` |
| 每日取排班 | 1 行 | ≤2 行 / 同索引 | 查询次数不变 `[推导]` |
| 逐日考勤聚合 | `O(排班行+卡数)` | `O(班次数+卡数)` | 行数 ≤2× → 同阶 `[推导]` |
| 计薪批量 | `O(Σ(S+R))` | 同阶（`S` ≤62） | 既有 **129.24ms/500 人**（实测） |

### 6.3 复现路径（服务器阶段，本机未执行）

```
# 计薪折算既有基准（已实跑，作为多班次回归基线）
cd hrm-dev/docs/algo-scripts
node s2b-payroll-shift.mjs      # 输出：验收算例 / before-after / 边界与降级

# 排班多班次（待后端实现后）
#  - 单测：活跃唯一幂等(N1)、重叠拒绝(N2)、单班次兼容(N4)、2 班全勤缺卡(N5，须用户裁定后)、ordinal 互异拒绝(N8)、软删后再排不冲突(N9)
#  - 集成：/schedules 出参多班次(N6)、S3 固定种子可复现(N7)
#  - 迁移：V22 预检三段（§8-1）→ 加生成列与唯一索引 → 重跑 N1/N9

# 说明：N4/N5 需真实/演示排班数据，N1/N8/N9 需 DB 唯一约束生效后验证；本机无 JDK/MySQL，全部收敛到服务器阶段复核
```
基准数据**不含真实业务数据**（沿用账户/员工均为演示口径，`algo-payroll-shift.md` §2.4 固定种子派生）。

---

## 7. 假设与风险登记

### 7.1 显式假设（假设不成立 → 后果）

| # | 假设 | 不成立时的后果 |
| - | ---- | -------------- |
| A1 | 多班次上线后每员工每日**活跃**排班行 **≤2**，且**同日各班次 `ordinal` 互异**（一早一晚），`start_time` 非 12:00 | ① `>2` → 计薪序号 0/1 冲突；② **同日两班同半天 → `ordinal` 相同 → `\|R\|` 去重少算（§1.3.1，M-4）**；③ `=12:00` → 判晚班边界歧义（`AlgoProperties.java:119` 已警示） |
| A2 | `attendance_rule.check_periods` 与 `attendance_shift`（按 `start_time` 升序）**一一对应** | 打卡窗口与班次序号漂移 → `period_index` 映射错位（§1.5、F-07） |
| A3 | 存量**活跃**数据满足「一人一天一行」（**软删行不计入**） | 加活跃唯一索引可能拒绝存量**活跃**重复行（需先去重，数据变更）；**软删重复行不影响迁移**（生成列为 `NULL`） |
| A4 | `period_name` 三态：多班次新数据写**班次名快照**；**存量写规则时段名快照**（`AttendanceRecordServiceImpl.java:355`），**仅 `check_periods` 为空时**为哨兵 `全天班` | 若误按 v1.0「存量全为哨兵」前提实现 → 兼容性论证失效（§1.8、FC10）；三态优先级②（空名）依赖告警人工复核 |
| A5 | 前端/测试按新出参（`shiftIds[]`）改造 | 单字段出参下第二班次不可见 |
| **A6（v1.1 新增）** | `requireDistinctOrdinalPerDay` 保持默认 `true` | 被关闭 → 同日同半天双班**静默少算**（须由计薪侧 `WARN_ORDINAL_COLLISION` 兜底告警） |

### 7.2 风险登记

| # | 风险 | 触发 | 影响面 | 缓解 |
| - | ---- | ---- | ------ | ---- |
| R1 | **缺卡口径变更引客诉**（FC6 修正后 2 班全勤不再误判缺卡，反之单班缺勤显示更精确） | 存量月与新口径混用 | 考勤概况/明细数值 | **待用户裁定**；裁定后由账期开关 `shiftModelFromMonth` 隔离；上线前公告 |
| R2 | **`maxShiftsPerDay>2` 与计薪编码冲突** | 配置误开 >2 | 折算比例失真 | 参数校验：`maxShiftsPerDay ≤ 2` 硬校验（或强制走 §10-T1 扩展后才放开） |
| R3 | **活跃唯一索引迁移遇存量「活跃」重复行** | 存量存在活跃重复 `(emp,date,shift)` | `V22` 迁移失败（1062） | 迁移前**三段预检**（§8-1，含软删行统计）+ C 档授权 + 备份 |
| R4 | **改派历史打卡不回改**造成「卡在 R 外」 | 删班次后查历史 | 折算分母变化 | 事实表只增不改（正确语义）；工资单账期锁保护已生成单据 |
| R5 | **S3 planner 单班次模型未同步扩展** → 智能排班仍只产 1 班 | 未做 §4.3 | 智能排班无法产多班次 | 本期手动模式支持多班次；智能模式登记 `TODO(扩展)`（§10-T2） |
| **R6（v1.1 新增，M-4）** | **同日两班同半天 → `ordinal` 相同 → 计薪 `\|R\|` 去重少算** | 排班保存未校验 / 前端放行同半天双选 | 折算少算（危计薪正确性） | `requireDistinctOrdinalPerDay=true` 保存侧硬校验（§1.3.1）；计薪侧 `WARN_ORDINAL_COLLISION` 兜底告警；边界用例 #17/#17' |
| **R7（v1.1 新增）** | **STORED 生成列须重建表（锁表窗口）** | `V22` 迁移 | 迁移期间写入阻塞 | `attendance_schedule` 每员工每天 ≤2 行、非大表 ⇒ 锁表窗口小；仍须 C 档授权 + 低峰执行 + 备份；具体算法与锁策略由数据库工程师定 |
| **R8（v1.1 新增）** | **逻辑删行累积致表膨胀**（每次改派 = 1 软删 + 1 insert） | 频繁改派/清空 | 存储与查询 | 应用查询恒带 `is_deleted=0`（`@TableLogic` 自动）；长期归档/清理登记 §10-T6，不在本期 |
| **R9（v1.1 新增）** | **`period_name` 兼容性前提被误用**（按「存量全为哨兵」实现） | 实现方沿用 v1.0 错误前提 | 兼容性论证失效 | 以本版 §1.8/FC10 为准（存量 = 规则时段名快照）；三态优先级代码 `ShiftPayrollPolicy.java:152-189` 已实现，无需改 |

---

## 8. 需上游确认的变更项（含迁移版本号，接续 `V22`；v1.1 重构为 10 条）

> 本文**只登记，不落 DDL/代码**。执行属 C 档，须主智能体三步授权（`项目规则1.md` §10.3）；结构变更须数据库工程师实现（P4 结构先行）。
> **状态列语义（算法侧交付状态）**：`已定稿` = 语义/口径已定，可由责任角色直接实现；`待数据库落地` = DDL/预检待数据库工程师；`待用户裁定` = 口径红线未决、未裁定前不得实现。

| # | 变更项 | 类型 | 状态（v1.1） | 建议落位 / 影响面 | 责任角色 |
| - | ------ | ---- | ------------ | ----------------- | -------- |
| 1 | `attendance_schedule` 加**生成列** `active_shift_key = IF(is_deleted=0, CONCAT_WS(':', employee_id, work_date, shift_id), NULL)`（STORED）+ `UNIQUE (active_shift_key)`；**迁移预检三段**（见下）；**保留**普通索引 `idx_attendance_schedule_emp_date` | **结构（C 档）** | **待数据库落地** | `V22__attendance_schedule_multi_shift.sql` | 数据库工程师（+ 算法联评） |
| 2 | 唯一性语义：`(employeeId, workDate)` → 「`(employeeId, workDate, shiftId)` **活跃唯一**」；Service 查重扩展为班次集合；覆盖式改集合差量（§4.1） | 代码 | 已定稿（口径） | `AttendanceScheduleServiceImpl.java:182-205`、`:262-273`、`:323-342`、`:370-379` | 后端工程师 |
| 3 | 出参契约：`DayCell` 单 `shiftId` → `shiftIds[]`（矩阵）；`MyScheduleVO.Day` → 班次列表；`/schedules/batch` 的「清空整天」语义与「按班次增删」重定义（建议 item 增 `shiftIds[]`，保留 `shiftId` 兼容）；响应 `{saved, removed}` 语义重定义为「班次行数」**并入本条** | **契约（P5）** | 已定稿（形状）；`api.md` 定稿待后端 | `api.md` §4.9.1/§4.9.2/§4.9.3；前端/测试同步 | 后端定稿 + 架构师评审 |
| 4 | **出勤/考核口径变更**：应到/缺卡由「人数去重」改「**班次粒度**」（候选口径②）；明细行粒度改 per `(employee, shift)`（`AttendanceDetailPolicy.java:36-111`）；`todayStatus` 支持多班次（`AttendanceRecordServiceImpl.java:396-452`） | **口径（§11.4）** | **待用户裁定（未裁定前不得据此实现）** | `AttendanceSummaryPolicy.java:35-59`；考勤概况/明细/员工端 | **用户 → 算法 → 后端** |
| 5 | 打卡判定链：`findSchedule` 取全部班次并按 `period_index` 定位；单班次模型（`usePeriod=false`）在多班次下需显式 `periodIndex` 或拒绝 | 代码 | 已定稿（口径） | `AttendanceRecordServiceImpl.java:303-316`、`:532-540`、`:346-350` | 后端工程师 |
| 6 | **同日同员工班次 `ordinal` 互异硬校验**（含 `start_time` 可解析校验）+ 参数 `requireDistinctOrdinalPerDay`（默认 `true`）+ 计薪侧 `WARN_ORDINAL_COLLISION` 兜底告警（M-4） | 代码 | 已定稿（口径） | 排班保存路径 + `ShiftPayrollPolicy` | 后端工程师 |
| 7 | S3 排班解空间 `shift[employee][day]` → 多班次集合（§4.3） | 算法扩展 | 已定稿（`TODO(扩展)` 登记） | `SchedulePlanner.java:50`；`buildFixedMatrix` | 算法 → 后端 |
| 8 | `AlgoProperties.Attendance` 新增多班次参数段（§2，含 `requireDistinctOrdinalPerDay`） | 配置 | 已定稿 | `AlgoProperties.java:172-192` | 后端工程师 |
| 9 | `attendance_rule.check_periods` 与 `attendance_shift`（按 `start_time` 升序）顺序一致性约定（规则配置校验或文档） | 数据/规则约束 | 已定稿 | `V5__attendance.sql:35`；排班保存路径 | 后端 / 数据库 |
| 10 | 排班相关错误码（重叠拒绝 / 超上限拒绝 / **`ordinal` 冲突拒绝**）定稿 | 契约 | 已定稿（数值待后端按 `api.md`） | `api.md` §2.2「91xx」段 | 后端（**算法不自定错误码**） |

**状态分布**：已定稿 **8** 条（#2/#3/#5/#6/#7/#8/#9/#10）；待数据库落地 **1** 条（#1）；待用户裁定 **1** 条（#4）。

### 8-1 迁移预检口径（三段式，**显式含软删行**；M-6）

> **为何「软删行必须显式纳入」**：① **防误判**——若沿用旧口径的「全量 `GROUP BY … HAVING COUNT>1`」（不带 `is_deleted` 过滤）预检，会因**软删重复行**（生成列将转 `NULL`、**不阻断**迁移）而误报「不可迁移」；若只查活跃行又会**漏报存量脏数据**。故预检必须**显式声明软删行的处置**（纳入统计、排除于阻断），不能「默认忽略」。② **软删重复行是「曾清空过该班次」的历史痕迹**，其数量反映迁移前存量是否已发生「清空→再排」操作，须留痕供审计与回归（§3-8/#18）。③ 生成列语义成立的前提是 `is_deleted` 取值规范，须核对分布。

| 段 | 预检 SQL（示意，最终由数据库工程师定稿） | 期望结果 | 是否阻断迁移 |
| - | --------------------------------------- | -------- | ------------ |
| **① 活跃行重复（阻断性）** | `SELECT employee_id, work_date, shift_id, COUNT(*) cnt, GROUP_CONCAT(id) ids FROM attendance_schedule WHERE is_deleted = 0 GROUP BY employee_id, work_date, shift_id HAVING COUNT(*) > 1;` | **必须 0 行** | **是**（非 0 → 先人工去重（属数据变更，须 C 档授权）再迁移；否则 `UNIQUE(active_shift_key)` 建索引 1062 失败） |
| **② 软删行重复（纳入输出，不阻断）** | `SELECT employee_id, work_date, shift_id, COUNT(*) cnt, GROUP_CONCAT(id) ids FROM attendance_schedule WHERE is_deleted <> 0 GROUP BY employee_id, work_date, shift_id HAVING COUNT(*) > 1;` | 允许 >0 行 | **否**（生成列置 `NULL`，`UNIQUE` 不去重 ⇒ 不冲突）；须**记录并留痕** |
| **③ `is_deleted` 取值分布核对（生成列前提）** | `SELECT is_deleted, COUNT(*) FROM attendance_schedule GROUP BY is_deleted;` | 仅应出现 `0` 与 `1` | 若出现非 0/1（如 `NULL`/`2`）→ **先修正**（此类行生成列亦置 `NULL`，会被静默排除于唯一，须确认是否为脏值） |

> **执行前置与回滚**：预检 → 备份 → 主智能体三步授权（§10.3）→ 低峰执行 `V22`；回滚 = `DROP INDEX uk_attendance_schedule_active_shift` → `DROP COLUMN active_shift_key`（先索引后列，参照 `V16` 回滚体例）。**具体 SQL/锁策略/快照同步由数据库工程师定稿**（本文只定口径）。

> **迁移前缀说明**：现有最高迁移为 `V21__payroll_log_locator.sql`（`db/migration/mysql/` 目录实测），故新迁移编号从 **`V22`** 起；须同步刷新 `sql/schema/mysql/init.sql` 快照（`db.md:440-442`）。**`V22` 为 C 档，须备份 + 三步授权。**

---

## 9. 失败降级（不得阻塞主流程）

| 触发 | 兜底行为 |
| ---- | -------- |
| 班次时间缺失/不可解析（`NaN` / `ordinal=-1`） | 该班次**跳过重叠判定与 `ordinal` 校验**，登记告警，不抛异常（对齐 `ShiftPayrollPolicy.WARN_SCHEDULE_SHIFT_MISSING`）；**排班保存侧直接拒绝**（§1.3.1-3） |
| 存在重叠且不可自动裁决 | 拒绝保存并返回冲突明细（人工改派）；不静默落库 |
| **同日两班 `ordinal` 相同（同半天双班）** | `requireDistinctOrdinalPerDay=true` → **拒绝整批**（§1.3.1）；被误关时 → 计薪侧 `WARN_ORDINAL_COLLISION` 显式告警（**不静默少算**），登记人工复核 |
| 超出 `maxShiftsPerDay` | 拒绝该批（整批原子，不半成功） |
| `check_periods` 与班次顺序不一致 | 打卡按 `period_index` 定位失败 → 回退单班次首条路径 + **告警**（不克扣），登记 §8-9 |
| **活跃唯一**并发冲突 | Service 捕获唯一键异常 → 视为「已存在」幂等处理（`IDEMPOTENT`），不冒泡 |
| S3 智能排班异常 | 回落贪心解 + 违规清单（`SchedulePlanner.java:96-109`），不改 |
| 计薪脏数据（排班缺班次时间） | 跳过该行、不计入 `R`、留痕（`ShiftPayrollPolicy.java:107-114`），不抛异常 |

---

## 10. 遗留 `TODO(扩展)` 与待裁定项

| 编号 | 事项 | 类型 | 处置 |
| ---- | ---- | ---- | ---- |
| T1 | TODO(扩展): maxShiftsPerDay > 2 时的计薪序号编码扩展（现 `epochDay×2+ordinal` 仅支持 0/1）——需改为 `epochDay×k + ordinal` 或按班次全序编码，属计薪口径扩展，须用户裁定 | 计薪 | 登记（本期封顶 2） |
| T2 | TODO(扩展): S3 整站智能排班的多班次完整优化（Timefold/CP-SAT 或两阶段贪心+局部搜索，§4.3） | 算法 | 登记（本期手动模式支持） |
| ~~T3~~ | ~~TODO(扩展): 排班软删（is_deleted）与唯一键共存方案~~ | 结构 | **v1.1 已闭环**：删除语义实测为**逻辑删**（`@TableLogic`），唯一性改用**生成列式活跃唯一**（§1.2、§8-1），不再遗留 |
| T4 | TODO(扩展): 「全天」覆盖式判定（方案 B，含跨零点尾段与休息间隙）作为 `fullDayShiftCount` 的增强 | 算法 | 登记 |
| T5 | **待用户裁定**：出勤/考核口径由「人数去重」改「班次粒度」（§8-4，`§11.4 口径红线`） | 口径 | **须用户裁定后方可实现；未裁定前不得据此实现** |
| **T6（v1.1 新增）** | TODO(扩展): 逻辑删行归档/清理策略（每次改派 = 1 软删 + 1 insert，长期表膨胀，§4.1、§7.2-R8） | 结构 | 登记（不在本期） |

---

## 11. 交付与交接

| 字段 | 内容 |
| ---- | ---- |
| 产出 | 本文档（**v1.1**；**仅编辑本文档**；未改任何代码 / 迁移 / `db.md` / `api.md` / 方案 / UI / 安全文档；未执行 git / 部署 / MCP；未连库、未跑测试） |
| 证据 | 全部结论标 `文件:行号`；本文数值标 `[推导]`；计薪既有实测引 `algo-payroll-shift.md` §2.4；**本机未运行任何代码/SQL** |
| 四件套 | ① 问题建模 §1；② 算法选型与复杂度 §4；③ 依据来源 §5（含 **D10–D12** 新增）；④ 可验证指标与基准 §6（**N1–N9**）—— **齐备** |
| 计薪折算一句话定义 | `PRORATED` 以**班次（半天单元）为计数单位**：`定薪字段 × \|A∩R\| ÷ \|R\|`，**一天两班即 2 个应出班次**，`A∩R` 取交使其只减不增（多打卡封顶满额） |
| 唯一性口径（v1.1） | **生成列式活跃唯一**：`active_shift_key = IF(is_deleted=0, CONCAT_WS(':', employee_id, work_date, shift_id), NULL)`（STORED）+ `UNIQUE`；= 同员工同天同班次至多 **1 条活跃行**、不同班次可并存、**软删行不占用唯一性** |
| 删除语义（v1.1 更正） | **逻辑删（`@TableLogic`）**：`deleteById` = `UPDATE is_deleted=1`（`AttendanceSchedule.java:39-40`、`AttendanceScheduleMapper.java:9`） |
| `shiftOrdinal` 最终定义 | `shiftOrdinal(start_time, middayBoundaryMinute)`：解析 `HH:mm` → `hour*60+minute < middayBoundaryMinute(720) ? 0(早) : 1(晚)`；缺列/不可解析 → `-1`；**同日各排班次 `ordinal` 必须互异**（M-4 硬校验默认开启） |
| 班次重叠裁定建议 | **默认不允许重叠**（`allowShiftOverlap=false`）；半开区间 `[s,e)` 判定；相邻不重叠合法；跨零点按 `+1440` 归一 |
| 需上游确认项 | **10 条**（§8；状态分布：**已定稿 8 / 待数据库落地 1 / 待用户裁定 1**） |
| 仍待用户裁定 | **1 项**：§8-4 / T5 **出勤/考核口径**（「人数去重」→「班次粒度」）；**未裁定前不得据此实现** |
| 边界用例 | **20 条**（§3 #1–#16 + #6' + #17/#17'/#18；覆盖 2/3/4 班、相邻、重叠、跨零点、重复排、清空（软删）后再排、`ordinal` 同半天冲突、历史打卡不回改、缺卡、补卡交互、折算算例含应 2 实 1 / 应 2 实 3） |
| 如何回滚 | 删除本文档即可；未触及既有源码与契约文档 |
| 报审 | 本文为**方案阶段产物（v1.1）**；按 **P0.6 / R25 / L8**，报审前须先经**技术评审工程师**评估（本文不宣称已通过；v1.0 结论为「打回」，须修订后**重评**，结论绑定版本） |
