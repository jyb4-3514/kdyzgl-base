# algo-payroll-shift.md · S2b 班次制算薪（缺勤/请假粒度 天 → 班次）

> 版本 **v2.0（修订版）** | 日期 2026-09-25 | 作者：算法工程师 `express-station-algorithm-engineer`
> 任务：把算薪的缺勤/请假粒度从「天」改为「班次」，按用户 2026-09-25 已裁定口径重新设计「S2 算薪引擎」。
> 范围：**只出方案与离线原型，不改业务代码**（`hrm-dev/hrm-server`、`hrm-admin`、`hrm-demo` 一律只读）。
> 关联：`algo-hrm-server.md`（S2 §4、S5 §7、参数表 §11）、`db.md` §8.3/§8.6、`requirement.md`；工资单接口契约的真实载体为 `PayrollController.java`（`/api/v1/finance/payrolls`）+ `server-architecture.md`（**非** `api.md`，见 §9）。
> 原型：`hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（`node` 直接运行，无第三方依赖）。

---

## 修订记录（v1.0 → v2.0）

- 修订触发：技术评审报告 `hrm-dev/docs/tech-review-payroll-shift.md`（结论**打回**，7 条必改项 B1–B7，6 个技术点核验，事实性纠正 FC1–FC5）。
- 本版同样落实**用户新裁定的 4 项口径**（T1 / T2 / T3 / T5）。
- **本版未声称通过评审**：评审结论由评审方出具；本版仅为按必改项修订后的待重评稿。

| 必改项 | 落实位置 | 一句话说明 |
| --- | --- | --- |
| **B1** | §0.1 C6、§3 问题 7、§5、§7、§10 T5 | 更正「现状按天计一次」的事实错误：现状为**按有效 ON 迟到卡计数**（`PayrollContextProvider.java:94-98`、`attendanceStore.js:893`）；据此把默认改为**按次（`PER_CARD`）= 现状行为不变**，`PER_DAY` 才属口径变更（未采纳）；并给出双班切换前后 `lateCount` 差异算例（§2.4.5） |
| **B2** | §9.2、§9.3 | 补齐新增 `PRORATED` 的**完整改动面** 6 项（枚举/白名单/统计字段/取数查询/label/演示端字典与规则项编辑器），逐项标注「代码改动 / 文档改动」与责任角色；撤回「零改动」的过度表述 |
| **B3** | §0.1 C8、§9.1、§9.4 | 查证 `db.md:812/860` 的 `source` 取值集合**性质=文档枚举**（`VARCHAR(16)` + 列 COMMENT，**无 CHECK/ENUM 约束**）；给出 `db.md` 同步位置与改法，并声明 DDL 注释同步属 C 档 |
| **B4** | §2.1（边界表）、§8（降级表）、§2.4.4 | 定死「记录→班次」**唯一三态优先级**（哨兵 / `period_name` 空 / `period_index`）；空 `period_name` + 双班排班**不得判为亏勤**；补用例并给出「不克扣」实跑证据（应出 60、实出 60、实得 1500、告警 30） |
| **B5** | §2.5（新增「假设与风险登记」） | 新增显式假设 5 条（各含「不成立后果」）+ 风险 5 类（触发条件/影响面/缓解/是否待裁定），含 −6000 与 −360 两个量级值出处 |
| **B6** | §7.1（优先级链）、§7.2（`leave_deduct_enabled` 去留）、§10 T3 | 给出**逐级封顶优先级链**（规则项 `params.cap` → 配置 `absentFineCapRatio` → 合计层 `allowNegativeNet`）；正面裁定 `leave_deduct_enabled` **冻结保留、新路径下 no-op**；拒绝悬空指针 |
| **B7** | §8.2（新增幂等/试算 NFR） | 补 3 条可判定断言（试算载体 / DRAFT 覆盖重建同结果 / 9405 整批拒绝）+ 账期锁交互，各附「如何验证」 |
| 建议项 S1–S6 | §2.1、§9.2、§9.5、§7、§11.2 | 采纳：公式写 `|A∩R|/|R|`；`detail` 单位「次→班次」；跨零点卡归属；安全面声明；复合索引建议；`12:00` 边界备注 |

---

## 0. 摘要（结论先行）

1. **核心变换**：把「排班行」作为唯一计量单位 —— `attendance_schedule` 一行 = 一个应出勤班次；出勤/请假/旷工都在**班次单元**（半天单元 `epochDay×2 + (AM?0:1)`，1 单元 = 1 班次）上做**集合运算**。
2. **主公式**：`折算后基本工资 = basicSalary × |A∩R| / |R|`；`旷工班次 = |R \ (A ∪ L)|`；`罚款 = 逐级封顶(旷工班次 × 单价)`（封顶链见 §7.1）。
3. **5 个验收算例逐位复现通过**（实际输出，非推算）：`1500 / 1475 / 1375 / 1450 / 1250`。
4. **推荐候选 A**（`BASIC` 改由新 source `PRORATED` 按出勤折算），否决候选 B（另加负项扣款）——理由见 §4。
5. **历史不突变**靠双保险：① 账期开关 `shiftModelFromMonth`（更早账期走旧「按天」路径）；② 记录级哨兵 `全天班` 兼容（单班制历史记录覆盖当日全部排班班次）。
6. **契约影响**：**无需改 `db.md` 表结构**；`payroll_rule_item.source` 列宽 `VARCHAR(16)` 决定新 source 命名必须 ≤16 字符 → 取 `PRORATED`（勿用 `ATTENDANCE_PRORATED`）；但**取值集合枚举需 1 处文档同步**（§9.4）。
7. **用户 4 项新裁定已落实**：T1 请假算缺勤（`fullAttendMetric=ABSENT_OR_LEAVE`）、T2 只折算基本工资（`proratedFields=[basicSalary]`）、T3 实发不低于 0（`allowNegativeNet=false`）、T5 迟到保持按次（`lateGranularity=PER_CARD`）。
8. **本轮不再有未裁定项**（原 T1/T2/T3/T5 均已裁定）；仅保留 4 项 `TODO(扩展)`（T4/T6/T7/T8，§10）。

### 0.1 事实性纠正（上游材料与实测出入）

| # | 上游说法 | 实测/官方 | 处置 |
| - | -------- | --------- | ---- |
| C1 | 依据可引「劳社部发〔2008〕3号（月计薪 21.75 天）」 | 该文件已被 **人社部发〔2025〕2号**（2025-01-01）**同时废止**；现行口径：月工作日 20.67 天、月计薪天数 21.75 天 | 本文依据引**现行**文件（见 §2.3） |
| C2 | `attendance_schedule`「结构天然支持一天两班次」 | DDL 无唯一索引，结构确实支持多行；但 `AttendanceSchedule.java:17` 注释与 Service 查重键仍为 **`employeeId + workDate`（一天一行）** | 需后端把查重键改为 `(employeeId, workDate, shiftId)`；**属代码改动，本文仅登记**（§9.2） |
| C3 | `leave_setting.leave_deduct_enabled`「当前测试库 = 1」 | `sql/seed/kdyzgl_test_seed.sql:918-919` 播种值为 **0**；`AlgoProperties.Leave.deductEnabledDefault = false` | 以「实测库为准、默认 false」；该开关去留**本版正面裁定**（§7.2），不再悬空 |
| C4 | 现状 `ABSENT_FINE` 单价 | 规则项实参为 `amount=150`（**按天**）、`cap=0`（不封顶），`seed:1364` | 新口径「100 元/班次」需**改规则项 params**（数据变更，非 DDL），见 §5/§9.3 |
| C5 | 新 source 名可叫 `ATTENDANCE_PRORATED` | `payroll_rule_item.source VARCHAR(16)`、`PayrollSource` 枚举名直接入库 | 命名 `PRORATED`（8 字符），避免改列宽（DDL） |
| **C6（新增，B1）** | v1.0 §3 问题 7 称「`LATE_FINE` 现状按天计一次」 | **错误**。现状按**有效 ON 迟到卡逐张计数**：`PayrollContextProvider.java:94-98`（`check_type=ON` 且 `status=LATE` 时 `lateCount++`），演示端同口径 `hrm-demo/src/shared/mock/attendanceStore.js:893`（`.filter(...).length`）。单班制历史（1 卡/天）下才**恰好**等价「一天一次」；双班制下现状即**班次粒度** | 修正事实；默认改 `PER_CARD`（按次 = 现状逐位一致）→ **「行为不变」成立**；`PER_DAY`（按天去重）才是**口径变更**，本轮未采纳（§3 问题 7 / §7 / §10 T5） |
| **C7（新增，B6）** | v1.0 §0.1 C3 称「该开关去留见 §3 问题 3/§7」 | `§3 问题 3` 与 `§7` **均未讨论**该开关 → **指针悬空** | 本版 §7.2 正面裁定：**冻结保留（FROZEN）**、班次制新路径下 no-op、旧按天路径继续沿用 |
| **C8（新增，B3）** | v1.0 §9 称「与 `api.md`/`db.md` 冲突 无」 | `db.md:812/860` 将 `source` 取值枚举为 `FIXED/ATTENDANCE/KPI/MANUAL`；`V8__payroll.sql:41,97` 对应列 COMMENT 同枚举。经查 **列为 `VARCHAR(16)`、无 CHECK/ENUM 约束** → 该集合是**文档枚举**（db.md 文字 + DDL 列 COMMENT），非 DB 约束 | 撤回「无冲突」；登记 1 处**契约文档同步**（§9.4）；DDL 注释同步属 C 档且**非功能必需** |
| **C9（新增，FC2）** | v1.0 §9 以 `api.md` 作工资单出参/错误码契约来源 | `api.md` 一期 24 接口与 §7 请假增量**均不含工资单接口**（仅 `9606` 一处引用）；真实端点为 `PayrollController.java:42`（`/api/v1/finance/payrolls`，10 接口） | 工资单契约载体改为 **`PayrollController.java` + `server-architecture.md`**；不再以 `api.md` 作工资单依据（§9.5） |

---

## 1. 口径基线（用户已裁定，不得更改）

| # | 口径 | 原文 | 状态 |
| - | ---- | ---- | ---- |
| B1 | 班次制 | 每天 2 个班次（早 + 晚），1 班次 = 半天，2 班次 = 1 天 | 已裁定 |
| B2 | 基本工资折算 | `折算后基本工资 = basicSalary × (实际出勤班次数 ÷ 应出勤班次数)` | 已裁定 |
| B3 | 已批请假 | 只参与折算，**不罚款** | 已裁定 |
| B4 | 旷工 | 应到未到且无已批请假覆盖 → 折算 + 罚款 **100 元/班次** | 已裁定 |
| B5 | 班次时间 | 早班 `08:00–16:00`、晚班 `16:00–24:00`；**删除中班**（现每驿站 3 班次，共 8 条中班记录） | 已裁定 |
| B6 | 验收算例 | basic=1500、应出 30 天=60 班次 → 全勤 1500 / 请半天 1475 / 旷半天 1375 / 请全天 1450 / 旷全天 1250 | 已裁定 |
| **T1** | 全勤奖口径 | **请假算缺勤 → 请假者不发全勤奖**（`fullAttendMetric=ABSENT_OR_LEAVE`） | **已裁定（新）** |
| **T2** | 折算范围 | **只折算基本工资**（`proratedFields=[basicSalary]`），岗位工资/津贴不折算 | **已裁定（新）** |
| **T3** | 罚款封顶 | **实发不低于 0**（用既有配置 `allowNegativeNet=false` 承担） | **已裁定（新）** |
| **T5** | 迟到粒度 | **保持按次**（`lateGranularity=PER_CARD`，= 现状源码口径，见 C6） | **已裁定（新）** |

---

## 2. 交付四件套

### 2.1 问题建模

**输入**（单员工单账期 `month`）：
- 定薪档案 `salary`（`basicSalary` 等，来源 `HrSalaryWriter#selectByEmployeeId`）；
- 排班行集 `schedules = {workDate, shiftId(start_time)}`（`attendance_schedule`，一行 = 一人一天一班次）；
- 打卡记录集 `records = {workDate, periodIndex, periodName, checkType∈{ON,OFF}, status}`（`attendance_record`）；
- 已批请假单集 `leaveRows = {startDate, startPeriod∈{AM,PM}, endDate, endPeriod, status=APPROVED}`（`leave_request`）；
- 配置 `cfg`（§7，命名空间 `hrm.algo.payroll.*`）。

**中间量（集合）**：
```
R = { (workDate, ordinal(shift.startTime)) | 排班行 }               // 应出勤班次
A = { 记录→班次(三态优先级，见下) | checkType=ON 且 status≠ABNORMAL }  // 实际出勤班次
L = { 班次单元 ∈ [unitOf(startDate,startPeriod), unitOf(endDate,endPeriod)] } ∩ R  // 已批请假班次
```

**记录 → 班次的唯一判定优先级（B4，三态，本节为唯一定义处；原型 `attendedShiftSet` 逐字对应）**：

| 序 | `period_name` 取值 | 处置 | 目的 |
| - | ------------------ | ---- | ---- |
| ① | 命中哨兵 `legacyPeriodSentinel`（默认 `全天班`） | 覆盖**当日全部排班班次** | 单班制历史兼容，`ratio` 不突变 |
| ② | **空 / `NULL` / 空白串** | 当日排班班次 **> 1** → 按当日**全部**排班班次计入出勤，并置 `PERIOD_NAME_MISSING` **告警**（交人工复核）；当日排班班次 **= 1** → 归属该唯一班次；当日**无排班** → 不产生班次（与 `R` 取交自然丢弃） | **不得判为亏勤**（防克扣）；数据质量暴露为告警而非扣款 |
| ③ | 其余（非空且非哨兵） | 按 `(workDate, period_index)` 精确定位班次 | 双班制正常路径 |

**目标函数（基本工资部分）**：
```
折算后基本工资 basic_prorated = |R| > 0 ? basicSalary × |A ∩ R| / |R|
                                          : (zeroSchedulePolicy = FULL_BASIC ? basicSalary : 0)
旷工班次       absent         = |R \ (A ∪ L)|
旷工罚款       fine           = 逐级封顶( absent × absentFinePerShift )      // 默认：100 元/班次，链见 §7.1
基本工资部分实得 net_basic    = max(0, basic_prorated − fine)   // 保留 2 位小数（HALF_UP）；T3 → allowNegativeNet=false
```
> 折算只取 `|A ∩ R|`（**不含请假**）：请假班次不计出勤（B3）；同时请假也不进 `absent`（B3 不罚款）。
> `|A|` 需先与 `R` 取交（`A ∩ R`），不可直接用打卡条数。

**约束**：
1. `absent ≥ 0`（集合差集天然非负）；
2. `|A ∩ R| ≤ |R|`；
3. `|R \ (A ∪ L)| + |A ∪ L| = |R|`（恒等式，原型 500 人校验 **0 违例**）；
4. `L` 先与 `R` 取交 ⇒ 请假超出应到不会反向放大缺勤；
5. `net_basic ≥ 0`（T3 封顶后，`allowNegativeNet=false`）；
6. 全程 **BigDecimal/整数**，金额四舍五入 `HALF_UP` 到分（Java 侧），原型用 `toFixed(2)` 近似。

**输出**：`{ requiredShifts, attendedShifts, leaveShifts, absentShifts, basicProrated, absentFine, fineGross, fineCappedBy, basicPartNet, netFloored, warnings }` + 明细解释文案（`payroll_item.detail`）。

**边界条件**（全部有原型用例，见 §2.4.4）：

| 边界 | 处置 |
| ---- | ---- |
| 空排班（`|R|=0`） | 按 `zeroSchedulePolicy` 兜底，**默认 `FULL_BASIC`**：不因数据缺失克扣基本工资（避劳动纠纷/阻塞）；挡除零 |
| 无打卡 | `A=∅` → 折算 0，`absent=|R\L|`；罚款经 §7.1 链封顶 + T3 净额下限 0 |
| 请假覆盖整天 | 同日 AM+PM 两个单元都计入 `L`（2 班次） |
| 请假超出应到 | `L∩R` 取交 ⇒ 不多扣；整月全假 → 折算 0、`absent=0` |
| 跨月请假 | 调用方按月区间切分（沿用 `ApprovedLeaveDaysPortImpl` 现状），本方法只按传入区间计算；跨月单 ≤ 2 单元区间连续 |
| `ABNORMAL` 卡 | `isValidCard=false` → 不计出勤 → 若应出则记旷工（与现状一致） |
| 一天仅一班制历史 | `period_name='全天班'` 哨兵 → 覆盖当日全部排班班次 → `ratio` 不突变 |
| **`period_name` 空 + 当日多班次** | **优先级②**：按当日全部班次计出勤（**不克扣**）+ 告警（B4） |
| 非法区间（同日 PM→AM） | `endUnit < startUnit` → 0 班次（对齐 S5 降级 `DATE_INVALID`） |
| 打卡与已批假重叠 | 集合 `∪` 去重 ⇒ **不双扣**（`A∩L≠∅` 时结果仍正确） |
| 晚班跨零点下班卡（建议项 S3） | `work_date` 取**班次开始日**，`period_index=1`；跨零点 OFF 卡归属同 `work_date`（本口径仅用 ON 卡计出勤，OFF 卡只走早退链） |

### 2.2 算法选型与复杂度

**选型**：**集合运算 + 半天单元整数编码**（沿用 S5 `LeaveIntervalPolicy` 的单元变换，把「排班日→2 单元」替换为「排班班次→1 单元」）＋ **表驱动注册表**（沿用 S2 `PayrollResolverRegistry`，新 source 只注册一个解析器）。不引入求解器/规则引擎——本问题是线性确定性计算，无组合优化成分。

| 环节 | 时间复杂度 | 空间 | 说明 |
| ---- | ---------- | ---- | ---- |
| `requiredShiftSet` | `O(S)` | `O(S)` | `S` = 排班行数（满月双班 ≤ 62） |
| `attendedShiftSet` | `O(S + R)` | `O(S + R)` | `R` = 打卡条数（满月双班 ≤ 124）；三态判定为常数分支 |
| `leaveShiftSet` | `O(L·U)` | `O(U)` | `U` = 单月单元数 ≤ 62；如需 `O(log S)` 计数可复用 S5 的 `lowerBound/upperBound` 二分 |
| `lateCountOf` | `O(R)` | `O(R)` | `PER_DAY` 去重亦为 `O(R)`（哈希集） |
| `capAbsentFine` | `O(1)` | `O(1)` | 逐级 min，级数固定 2（+合计层 1） |
| 集合差 `R\(A∪L)` | `O(S)` | `O(S)` | 逐 `R` 判定成员 |
| **单员工单月** | **`O(S log S + R + L·U)`** | `O(S + R)` | 排序仅用于稳定输出（原型按 key 排序） |
| **批量 N 员工** | **`O(Σ(S_e log S_e + R_e + L_e·U_e))`** | `O(max_e)` | 逐员工独立，无跨员工状态 |

**最坏情况**：满月双班、每天全勤且每班重复打卡、每员工多张跨日假单 → `S=62, R=124, L·U≈62L`。规模恒在**万级操作以内/月·人**，实测 500 人 × 满月 **129.24 ms**（单人 0.2585 ms，含基准数据构建在外的纯算法段）。**不存在随规则项数恶化的路径**（上下文在批次前段构造一次，与 S2 §4.2 一致）。

### 2.3 依据

| # | 依据 | 来源（可查） |
| - | ---- | ------------ |
| D1 | 区间整数化 → 两次二分计数（lower/upper bound） | Cormen, Leiserson, Rivest, Stein, *Introduction to Algorithms*, 3rd ed., §2.3（二分）与 ch.14（区间树）。项目内已用于 S5 |
| D2 | 集合差/并去重（不双扣、顺序无关） | 同上，集合基本性质；工程落点为 `Set` 语义 |
| D3 | 表驱动 + 注册表（新增 source 不改核心） | McConnell, *Code Complete* 2nd ed., ch.18；Martin, *Clean Architecture* (2017) 开闭原则。对齐 S2 §4.3 |
| D4 | 金额精度与舍入（`BigDecimal`、`HALF_UP` 到分） | Oracle Java SE API：`java.math.BigDecimal`（`setScale(int, RoundingMode)`）`https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html` |
| D5 | 工资按比例折算的法定惯例（月计薪天数 21.75） | 人力资源社会保障部，《关于职工全年月平均工作时间和工资折算问题的通知》**人社部发〔2025〕2号**（2025-01-01），`https://www.mohrss.gov.cn/xxgk2020/fdzdgknr/zcfg/gfxwj/ldgx/202501/t20250101_533693.html`。**注：该文件同时废止劳社部发〔2008〕3号** |
| D6 | 「有效卡判定」复用现有唯一真源 | 项目内 `AttendanceConstants.isValidCard`（ABNORMAL 不计出勤）、`db.md` §8.3.4 |
| D7 | 「半天单元」编码与区间法 | 项目内 `LeaveIntervalPolicy`（S5，`algo-hrm-server.md` §7），本方案仅改「单元粒度映射」 |
| D8 | 纯函数 + 固定种子可复现 | `algo-scripts/lib/rng.mjs`（mulberry32，`https://github.com/bryc/code/blob/master/jshash/PRNGs.md`） |
| D9 | 逐级封顶（min 链）语义 | 项目内既有实现：`AttendanceItemResolver.java:55-59`（规则项 `cap`）、`AlgoProperties.java:104-105`（`itemCapSemantics`）、`PayrollTotalsPolicy.java:44-46`（`allowNegativeNet` 净额下限） |

> 说明：本项目**班次折算口径由用户裁定**（B2），未采用 21.75 天折算公式；D5 仅作为「按实际出勤比例支付工资」这一做法的合规惯例支撑，不改变本项目口径。

### 2.4 可验证指标与基准数据

**运行命令**（本机有 Node v24，无 JDK/Maven）：
```
cd hrm-dev/docs/algo-scripts
node s2b-payroll-shift.mjs
```
基准数据集由**固定种子**派生（`0x5d1f2a7c` + `fnv1a('shift#<id>')`），同参数重跑**逐位一致**（仅耗时列随机器波动）。

#### 2.4.1 五个验收算例（实际输出，逐位）

| 情形 | 应出 | 实出 | 请假 | 旷工 | 折算后基本 | 罚款 | **实得** | 期望 | 一致 |
| ---- | ---: | ---: | ---: | ---: | ---------: | ---: | -------: | ---: | :--: |
| 全勤 | 60 | 60 | 0 | 0 | 1500 | 0 | **1500** | 1500 | ✅ |
| 请半天假(已批) | 60 | 59 | 1 | 0 | 1475 | 0 | **1475** | 1475 | ✅ |
| 旷工半天 | 60 | 59 | 0 | 1 | 1475 | 100 | **1375** | 1375 | ✅ |
| 请全天假(已批) | 60 | 58 | 2 | 0 | 1450 | 0 | **1450** | 1450 | ✅ |
| 旷工全天 | 60 | 58 | 0 | 2 | 1450 | 200 | **1250** | 1250 | ✅ |

`[验收] 全勤=1500 / 请半天假(已批)=1475 / 旷工半天=1375 / 请全天假(已批)=1450 / 旷工全天=1250 → 全部逐位一致`（实跑输出）。

#### 2.4.2 before / after 差异清单（现状按天 vs 新按班次，同组班次事实）

> 现状口径复刻：`BASIC=FIXED` 不折算；`excludeLeaveDays = leave_deduct_enabled ? 0 : leaveDays`；按天 `absent×150`。
>
> **开关取值说明（消除与 §0.1 C3 的互斥）**：`leave_setting.leave_deduct_enabled` 的**真源为 seed 值 `0`**（`sql/seed/kdyzgl_test_seed.sql:918-919`），与 `AlgoProperties.Leave.deductEnabledDefault=false` 一致。
> 下表「现状」列按**对照场景 `=1`（请假按缺勤计）**计算——取该值意在展示"请假被当缺勤罚"的**最不利差异**，便于对照新旧口径；**`=0`（seed 真源）情形**的差异见下方「差异要点」第 2 条（请全天假实得 1500、差 −50）。
> 测试库当前该行恰为 `1`，属**早于本次 seed 修订灌入的存量数据**，重灌 seed 即归零；不影响算法结论（两种取值均已列出）。

| 情形（班次事实） | 现状(天) 实得 | 现状明细 | 新(班次) 实得 | 新明细 | 差(新−现状) |
| ---------------- | ------------: | -------- | ------------: | ------ | ----------: |
| 全勤（60 班有卡） | 1500 | 缺勤 0 天×150 | 1500 | 折算 1500 − 0 | **0** |
| 请半天假（59 班出勤，1 班已批假） | 1500 | 缺勤 0 天×150 | 1475 | 折算 1475 − 0 | **−25** |
| 旷工半天（59 班出勤，1 班未到无假） | 1500 | 缺勤 0 天×150 | 1375 | 折算 1475 − 100 | **−125** |
| 请全天假（58 班出勤，2 班已批假） | 1350 | 缺勤 1 天×150 | 1450 | 折算 1450 − 0 | **+100** |
| 旷工全天（58 班出勤，1 天两班未到无假） | 1350 | 缺勤 1 天×150 | 1250 | 折算 1450 − 200 | **−100** |

**差异要点（供验收对照）**：
- 现状**无半天粒度**：请半天假/旷工半天只要「当天有一张有效 ON 卡」就**整天不扣**（粒度丢失）；新口径按班次精确到 1 班次 = 25 元折算。
- 现状 `leave_deduct_enabled=1` 时**请假按缺勤计**（请全天假被罚 150）；新口径 **B3 请假只折算不罚款**（反而多 100 元）。若实测库开关为 `0`，则现状「请全天假」= 1500（不罚），差异变为 **−50**。
- 现状「旷工全天」= 扣整天 150；新口径 = 100×2 = 200，**同事实扣款更重**（口径变更，用户已裁定 100/班次）→ **客诉风险登记见 §2.5 R3**。

#### 2.4.3 批量基准（500 员工 × 2026-10 满月，固定种子）

| 指标 | 数值 |
| ---- | ---- |
| 员工数 / 账期 | 500 / 2026-10 |
| 平均应出班次 | 49.87 |
| 实得均值 / 标准差 | 3708.08 / 219.25 |
| 实得最小 / 最大 | 2920 / 4000 |
| **恒等式违例数**（`\|R\(A∪L)\|+\|A∪L\|=\|R\|` 等） | **0 / 500** |
| 负净额被钳制数（T3 生效次数） | 0 / 500 |
| 总耗时 / 单人耗时 | 129.24 ms / 0.2585 ms |

> 耗时为含对象分配的 JS 实现；Java 侧为静态设计，**收敛到服务器阶段复核**（§11.2）。基准集内罚款未触发封顶、净额全为正 → T3 未改变基准数值（仅「无打卡」等极端用例受影响）。

#### 2.4.4 边界与降级（实际输出）

| 用例 | 应出 | 实出 | 请假 | 旷工 | 折算基本 | 罚款 | 实得 | 负净额已钳制 | 封顶级 | 告警数 | 说明 |
| ---- | ---: | ---: | ---: | ---: | -------: | ---: | ---: | :--: | :--: | ---: | ---- |
| 空排班（FULL_BASIC） | 0 | 0 | 0 | 0 | 1500 | 0 | 1500 | 否 | NONE | 0 | 不因数据缺失克扣 |
| **整月无打卡（T3）** | 60 | 0 | 0 | 60 | 0 | 6000 | **0** | **是** | NONE | 0 | **v1.0 的 −6000 已消除** |
| **部分缺勤（T3）** | 60 | 10 | 0 | 50 | 250 | 5000 | **0** | **是** | NONE | 0 | 罚款超折算、净额钳到 0 |
| ABNORMAL 卡不计出勤 | 60 | 59 | 0 | 1 | 1475 | 100 | 1375 | 否 | NONE | 0 | 与现状一致 |
| 整月全假 | 60 | 0 | 60 | 0 | 0 | 0 | 0 | 否 | NONE | 0 | 请假不罚款 |
| 请假与出勤重叠 | 60 | 60 | 1 | 0 | 1500 | 0 | 1500 | 否 | NONE | 0 | **不双扣** |
| 跨日单 PM→AM | 60 | 56 | 4 | 0 | 1400 | 0 | 1400 | 否 | NONE | 0 | 连续单元覆盖中间整日 |
| 非法区间 PM→AM 同日 | 60 | 59 | 0 | 1 | 1475 | 100 | 1375 | 否 | NONE | 0 | 降级为 0 班次 |
| **空 period_name + 双班（B4）** | 60 | **60** | 0 | **0** | **1500** | 0 | **1500** | 否 | NONE | **30** | **不克扣**：按当日全部班次计出勤 + 告警 |
| 空 period_name + 单班（B4） | 30 | 30 | 0 | 0 | 1500 | 0 | 1500 | 否 | NONE | 0 | 归属唯一班次 |
| 单班制历史_全勤 | 20 | 20 | 0 | 0 | 1500 | 0 | 1500 | 否 | NONE | 0 | 哨兵兼容，ratio=1 无突变 |
| 单班制历史_缺一天 | 20 | 19 | 0 | 1 | 1425 | 100 | 1325 | 否 | NONE | 0 | 哨兵兼容生效 |

#### 2.4.5 B1 迟到粒度切换前后 `lateCount` 差异算例（实际输出）

> 现状口径 = `PER_CARD`（每张「`check_type=ON` 且 `status=LATE`」的卡计 1 次）；`PER_DAY` = 按 `work_date` 去重，为**口径变更**。迟到单价按 20 元/次示例。

| 场景 | 现状 `PER_CARD` 次数 | `PER_DAY` 次数 | 现状扣款 | `PER_DAY` 扣款 | 差（`PER_DAY`−现状） | 结论 |
| ---- | ---: | ---: | ---: | ---: | ---: | ---- |
| 单班制历史（20 天 × 1 张迟到卡） | 20 | 20 | 400 | 400 | **0** | 单班制下「按次」与「按天」**等价** |
| 双班制（10 天 × 2 班均迟到） | **20** | 10 | **400** | 200 | **−200** | 默认 `PER_CARD` = **现状行为不变**；改 `PER_DAY` 则**少扣 200 元 = 口径变更** |

**结论（对 B1 的正面回应）**：现状代码**本就是「按次」**（每张有效 ON 迟到卡一次），不是「按天一次」。因此：
- 将默认设为 `PER_CARD` **不产生口径变更**（`lateCount` 与源码逐位一致，单/双班制皆然）；
- `PER_DAY`（按天去重）**才是**口径变更（双班制下每人每天最多少扣一次迟到），**本轮按 T5 未采纳**；若未来启用须**用户再裁定**（§10）。

### 2.5 假设与风险登记（B5 新增）

#### 2.5.1 显式假设（假设不成立 → 后果）

| # | 假设 | 不成立时的后果 |
| - | ---- | -------------- |
| A1 | 双班制上线后 `attendance_schedule` 每员工每日 **≤ 2 行**，且 `shift_id` 指向早/晚班（`start_time` 非 12:00） | `|R|` 被夸大或班次序号冲突 → 折算比例与罚款基数同时失真 |
| A2 | `period_name` 语义随班次制切换：新数据写 `早班`/`晚班`，历史数据写 `全天班` | 三态优先级①/②被频繁触发 → 依赖告警人工复核，自动化程度下降 |
| A3 | 上线月（`shiftModelFromMonth`）由发布计划确定，历史月一律 `month <` 该值 | 历史月误走新路径 → 语义突变、历史单据重算口径不一致 |
| A4 | 定薪档案 `basicSalary` 非空且 ≥ 0 | `null` 当 0 计 → 折算为 0（克扣）；负数 → 折算放大（多发） |
| A5 | 罚款单价经规则项 `params.amount`（100 元/班次）承载并已数据变更生效 | 仍为旧的 150/天 → 罚款口径与方案不符（数值偏大） |

#### 2.5.2 风险登记（触发条件 / 影响面 / 缓解措施 / 是否待裁定）

| # | 风险 | 触发条件 | 影响面 | 缓解措施 | 待裁定？ |
| - | ---- | -------- | ------ | -------- | :------: |
| **R1** | **负实发**（v1.0 原型「无打卡」实得 **−6000**；既有 Mock 现状最小仅 **−360**，出处 `AlgoProperties.java:99-103`；量级放大 ~16 倍） | 旷工罚款 > 折算后基本工资（如整月无卡、长期缺勤） | 涉该员工当月实发金额（单据为负） | **T3 已裁定**：`allowNegativeNet=false` 生效 → 净额下限置 0（实证：整月无打卡实得 **0**，§2.4.4） | 否（T3 已裁定） |
| **R2** | **罚款超基本工资**（罚款额本身 > 折算基本，明细上「扣 6000」） | 同上 | 工资单明细可解释性；员工感知罚不当罪 | 可选开 `absentFineCapRatio`（§7.1 级 2）；默认保留全额明细 + 文案拆分（`detail` 单位「班次」），净额由 R1 兜底 | 否（可配项，默认关闭） |
| **R3** | **双班制扣款由 150/天 → 200/天，员工客诉** | 同一天两班均旷工 | 同期同事实员工实发下降 ~50 元/天；集中申诉 | `detail` 按班次拆分解释（「旷工 2 班次 × 100 元」）；上线前公告口径；预留人工调整（`MANUAL` 项） | 否（B4 已裁定 100/班次） |
| **R4** | **空排班 `FULL_BASIC` 多发** | 漏排班 / 排班数据未生成 | 该员工按全额发基本工资（多发） | 生成工资单前跑「排班完整性巡检」（应出班次=0 的员工清单）；本期账期对账；降级策略可切 `zeroSchedulePolicy=ZERO` | 否（降级策略，默认不克扣优先） |
| **R5** | **历史 `period_name` 为空的数据质量** | 升级前存在 `period_name=NULL` 的打卡记录（`V5__attendance.sql:89` 允许 NULL） | 走 B4 优先级② → 按当日全部班次计出勤（可能多发）+ 告警 | ① 依赖 B4 的 `PERIOD_NAME_MISSING` 告警清单人工复核；② `shiftModelFromMonth` 使历史月走旧路径，不触发新逻辑；③ 数据侧回填 `period_name`（登记为数据变更） | 否（B4 已定优先级） |

> **与 T3 的关系**：T3 裁定前，默认缓解为 `allowNegativeNet=true`（允许为负）+ 明细文案标注罚款构成（`fineGross` 与封顶级）；T3 裁定后，默认缓解升级为 `allowNegativeNet=false`（实发不低于 0）。

---

## 3. 八个设计问题的结论表

| # | 问题 | 结论 | 依据 |
| - | ---- | ---- | ---- |
| 1 | **应出勤班次数** | `attendance_schedule` 行数，按 **`(work_date, shift_id)`** 去重（`Set`）。满月双班 = 30×2 = 60 | D2；DDL 无唯一约束，Service 现状按 `(employee_id, work_date)` 查重需改为三元键（C2） |
| 2 | **实际出勤班次数** | **schedule-driven**：逐排班行判定「是否存在匹配的有效上班卡」→ 实出 = 有卡的排班行数。判定口径 = **`check_type=ON` 且 `status≠ABNORMAL` 即算一个班次出勤，不要求 ON+OFF 成对**；记录→班次按**三态优先级**（§2.1）定位 | **取舍**：(a) 与现状「有 ON 且状态有效即出勤」逐位一致（`PayrollContextProvider` L91–98）；(b) 双班制下 2 张 ON 卡天然表达「上午出勤下午旷工」；(c) OFF 缺失属早退/补卡另一条链。`requireBothCards` 列为 `TODO(扩展)`（§10） |
| 3 | **请假班次** | 把「日期+AM/PM」映射为**班次单元**：`AM→早班(0)`、`PM→晚班(1)`，区间 `[unitOf(start,startPeriod), unitOf(end,endPeriod)]` 连续；与 `R` 取交得 `L`。**跨日单（PM→AM）天然覆盖中间整日**；非法区间（同日 PM→AM）→ 0 班次 | D7（`unitOf = epochDay×2 + (AM?0:1)`）。**边界**：只有落在应出排班上的请假单元才抵扣缺勤（`L∩R`）；NATURAL/SCHEDULED 在本口径下**结果等价** |
| 4 | **旷工班次数与扣减顺序** | `absent = \|R \\ (A ∪ L)\|`，下限 0。**采用集合语义 ⇒ 先扣请假还是先扣缺勤对结果无影响**；仅在「标量相减」且 `A∩L≠∅` 时会双扣/顺序敏感 | D2；恒等式 500 人 **0 违例** |
| 5 | **折算项落点** | **推荐候选 A**：`BASIC` 由 `FIXED` 改为新 source **`PRORATED`**（`params:{baseField:'basicSalary'}`），`amount = basic × \|A∩R\|/\|R\|`。否决候选 B（另加负项） | 见 §4；命名 ≤16 字符受 `source VARCHAR(16)` 约束（C5） |
| 6 | **全勤奖关系** | **T1 已裁定：请假算缺勤 → 请假者不发全勤奖**。`FULL_ATTEND` 指标改为 **`ABSENT_OR_LEAVE`**（旷工+请假班次须为 0），默认值即此；旧 `ABSENT`（仅旷工）保留为可配项但**不再是默认** | §7 键 `fullAttendMetric`；组合指标落点见 §9.2 第 3 项（`AttendanceStat` 需新增组合字段） |
| 7 | **与 LATE_FINE/FULL_ATTEND 叠加** | 三者**独立可加**：折算改 `BASIC`（加项），罚款为扣项，全勤奖为加项，无相互作用。`LATE_FINE` 现状**按有效 ON 迟到卡（按次）计数**（`PayrollContextProvider.java:94-98`、`attendanceStore.js:893`）；新口径新增可配 `lateGranularity`，**默认 `PER_CARD`（按次，= 现状，行为不变）**；`PER_DAY` 为口径变更（T5 未采纳） | **B1 更正**（原称「按天计一次」为事实错误）；叠加算例见 §5、差异算例见 §2.4.5 |
| 8 | **历史数据兼容** | **双保险**：① **账期开关** `shiftModelFromMonth`：`month < 该值` 走旧「按天」统计路径（旧快照/未出账历史月零突变）；② **记录级哨兵** `全天班`：单班制历史记录覆盖当日全部排班班次 ⇒ `ratio` 不突变。**三态优先级**（§2.1）补齐「`period_name` 空 + 双班」缺口 | 原型用例「单班制历史_全勤」ratio=1、实得 1500；「空 period_name + 双班」实得 1500 + 告警 30。DDL **无需变更** |

---

## 4. 折算项实现选择：候选 A vs B

| 维度 | 候选 A：`BASIC` → 新 source `PRORATED` | 候选 B：`BASIC` 保持 `FIXED` + 负项「折算扣款」 |
| ---- | ---------------------------------------- | ---------------------------------------------- |
| 公式 | `amount = basic × \|A∩R\|/\|R\|`（一条加项） | `BASIC=basic`（加项）+ `PRORATE_DEDUCT = basic×(1−\|A∩R\|/\|R\|)`（扣项） |
| 净额 | 同（1475） | 同（1500−25=1475） |
| `gross_amount`（应发=Σ加项） | 1475（**随口径变化**） | 1500（虚高） |
| 明细可读性 | 一条「基本工资 1500 × 59/60 = 1475」 | 「基本工资 1500」+「出勤不足折算 −25」两条，**与真实罚款并列易混淆** |
| 语义纯度 | 折算 = 计薪方式（正确） | 折算被伪装成「扣款」（易被误读为处罚） |
| 前端影响 | **明细项数不变**；但来源字典与规则项编辑器需同步（§9.2 第 6 项） | 多一条扣款项，需确认前端分组渲染 |
| 历史快照兼容 | 旧 `rule_snapshot.BASIC.source=FIXED` 按快照 source 分发，回读旧单不变 | 同样兼容 |
| 除零 | `\|R\|=0` 走 `zeroSchedulePolicy` 兜底 | `1−\|A∩R\|/\|R\|` 需单独挡除零 |
| 复杂度 | `O(1)` | `O(1)` |

**推荐：候选 A**。理由：① 语义单一（折算就是折算，不是罚款），员工端可解释性最好；② **工资单明细项数不变**；③ 复杂度同；④ 新 source 只是注册一个解析器，符合 S2 开闭原则（D3）。
> **更正 v1.0 表述**：候选 A **非「零改动」**——工资单**明细字段形状不变、项数不变**，但 `gross_amount` 值随口径变化（1500→1475），且**来源字典与规则项编辑器需同步**（完整改动面见 §9.2，B2）。

> **岗位工资 / 津贴是否同样折算？** **T2 已裁定：只折算基本工资**（`proratedFields=[basicSalary]` 为其默认值，非「待裁定」）；若需扩展为岗位/津贴 → **须用户再裁定**（口径红线）。

**对历史工资单快照的影响**：`payroll.rule_snapshot` 保存算薪当场规则，回读旧单按**快照里的 source** 分发（旧为 `FIXED`）→ 金额不因新代码变化；仅**新生成**的单据使用 `PRORATED`。已出账（非 `DRAFT/REJECTED`）月份本就拒绝重复生成（§8.2），双重保险。

---

## 5. 叠加口径（FULL_ATTEND / LATE_FINE）与算例

以 basic=1500、应出 60、全勤奖 200、迟到 20 元/次（cap 300）、旷工 100 元/班次、T1 口径（请假算缺勤）为例：

| 场景（班次事实） | 折算基本 | 全勤奖(ABSENT_OR_LEAVE) | 迟到扣款(PER_CARD) | 旷工罚款 | 基本工资部分实得 |
| ---------------- | -------: | ----------------------: | -----------------: | -------: | ---------------: |
| 全勤、迟到 1 班次 | 1500 | 200 | −20 | 0 | 1500（未含奖与迟到）|
| 请半天假 | 1475 | **0（请假算缺勤）** | 0 | 0 | 1475 |
| 旷工半天 | 1475 | 0（有旷工） | 0 | −100 | 1375 |
| 整月全假 | 0 | **0（请假算缺勤）** | 0 | 0 | 0 |

> 「基本工资部分实得」仅含折算与旷工罚款；全勤奖/迟到扣款为独立规则项，验证算例（B6）不覆盖。
> 全勤奖新旧对比（实跑，§2.4 输出 `T1_全勤奖_请假算缺勤`）：请半天假 **新 0 / 旧 200**；整月全假 **新 0 / 旧 200**；全勤与旷工半天两态新旧一致（200 / 0）。

---

## 6. 历史数据兼容路径

现状：`attendance_record` 386 条全为 `period_index=0` + `period_name='全天班'`（单班制）；`attendance_schedule` 一天一行（Service 按 `employeeId+workDate` 查重）。

**兼容设计（三档，均不阻塞）**：
1. **账期级（主保险）**：配置 `hrm.algo.payroll.shiftModelFromMonth`（默认设为**上线当月**，如 `2026-10`）。`month < 该值` 的账期完全走旧「按天」统计路径 → 旧账期**零语义突变**、历史工资单重算结果逐位不变。
2. **记录级（次保险，三态）**：按 §2.1 唯一优先级处理：哨兵 `全天班` → 覆盖当日全部班次；`period_name` 空 → 不克扣（多班次时全班次计出勤 + 告警）；其余按 `period_index`。用于混入升级前数据的过渡月。
3. **数据侧（由数据库/后端执行，本文只登记）**：
   - 删除 8 条中班 `attendance_shift` 行（B5）——**数据变更**（新 Flyway 版本 + seed 同步）；旧账期若引用中班 `shift_id`，需先 `UPDATE` 迁到早/晚班或保留 `is_deleted` 逻辑删除。
   - 更新 `payroll_rule_item`：`ABSENT_FINE.params.amount` `150 → 100`、语义「元/天 → 元/班次」（B4，数据变更）。
   - **无 DDL 变更**（不新增列、不改列型；`source` 取值集合为文档枚举，见 §9.4）。

---

## 7. 参数外置清单（`hrm.algo.payroll.*`）

> 原型中 `SHIFT_CONFIG` 同名常量即默认值；实现侧必须逐键外置，禁止内联（规则 §11.4 / 反模式 A03）。

| 键名 | 默认值 | 语义 | 待用户再裁定？ |
| ---- | ------ | ---- | :------------: |
| `shiftModelFromMonth` | `2026-10` | 班次制生效账期；`month <` 该值走旧按天路径 | 否（上线月份由发布计划定） |
| `middayBoundaryMinute` | `720` | 班次序号判定界值：`start_time` 分钟数 `<720` = 早班(0)，否则晚班(1)。**若存在 12:00 起始班次将与之冲突**，删中班后须校验无此类数据（建议项 S6） | 否 |
| `legacyPeriodSentinel` | `全天班` | 单班制历史记录哨兵，命中则覆盖当日全部排班班次 | 否 |
| `proratedFields` | `[basicSalary]` | 需按出勤班次折算的定薪字段 | **否（T2 已裁定仅基本工资）** |
| `absentFinePerShift` | `100` | 旷工罚款单价（元/班次）；实际以规则项 `params.amount` 为准 | 否（B4 已裁定） |
| `absentFineCap` | `0` | 规则项级绝对额封顶（对应 `ABSENT_FINE.params.cap`）；`<=0` 按 `itemCapSemantics` 解读 | 否（沿用规则项机制） |
| `absentFineCapRatio` | `0`（不封顶） | 配置级比例封顶 = 折算后基本工资 × 该系数；`<=0` = 本级不封顶 | 否（可配项，默认关闭） |
| `allowNegativeNet` | **`false`** | 是否允许负净额；`false` = 实发不低于 0 | **否（T3 已裁定）** |
| `zeroSchedulePolicy` | `FULL_BASIC` | `\|R\|=0` 兜底：`FULL_BASIC`（全额）/ `ZERO` | 否（降级策略） |
| `fullAttendMetric` | **`ABSENT_OR_LEAVE`** | 全勤奖指标：`ABSENT_OR_LEAVE`（旷工+请假，请假算缺勤）/ `ABSENT`（仅旷工，旧口径） | **否（T1 已裁定）** |
| `lateGranularity` | **`PER_CARD`** | 迟到计数粒度：`PER_CARD`（按次，每张有效 ON 迟到卡计 1 次，= 现状）/ `PER_DAY`（按日去重，口径变更） | **否（T5 已裁定保持按次）** |
| `itemCapSemantics` | `ZERO_MEANS_NO_CAP` | `cap<=0` 的语义（既有键，`AlgoProperties.java:104-105`） | 否（沿用既有） |

### 7.1 罚款封顶优先级链（B6 定死）

**判定顺序（自上而下，命中即定；每级「取值 ≤ 0 视为本级不封顶」；逐级取更严（min），不叠加相减）**：

| 级 | 来源 | 表达式 | 生效规则 |
| - | ---- | ------ | -------- |
| 级 1 | **规则项** `ABSENT_FINE.params.cap`（`AttendanceItemResolver.java:55-59`） | `amount₁ = cap>0 ? min(amount₀, cap) : amount₀` | **最具体，先施加**；`cap<=0` 由 `itemCapSemantics` 解读（默认 `ZERO_MEANS_NO_CAP` = 不封顶） |
| 级 2 | **配置级** `hrm.algo.payroll.absentFineCapRatio` | `amount₂ = ratio>0 ? min(amount₁, basic_prorated×ratio) : amount₁` | 在级 1 结果上再取更严；`<=0` = 本级不封顶 |
| 级 3 | **合计层** `allowNegativeNet`（`PayrollTotalsPolicy.java:44-46`） | `net = (allowNegativeNet>0) ? gross−deduct : max(0, gross−deduct)` | **不改变罚款额**，只钳制**实发净额**（T3 使用的正是本级） |

其中 `amount₀ = 旷工班次 × absentFinePerShift`。**冲突生效规则**：若两级都配了封顶，取**更严（更小）者**；因是逐级 `min`，**不会出现「规则项 cap 封顶后又按 capRatio 算一次」的重复施加**。原型 `capAbsentFine` 输出 `fineCappedBy ∈ {NONE, ITEM_CAP, CONFIG_CAP_RATIO}` 便于明细与排障（实跑默认全为 `NONE`，基准集未触发封顶）。

> **「实发不低于 0」无需新增键**：既有 `hrm.algo.payroll.allowNegativeNet=false` 即可表达（`PayrollTotalsPolicy.java:45-46`），T3 直接复用之。

### 7.2 `leave_setting.leave_deduct_enabled` 去留裁定（B6，终结 C3 悬空）

| 路径 | 处置 | 理由 |
| ---- | ---- | ---- |
| 班次制新路径（`month ≥ shiftModelFromMonth`） | **不读取（no-op）**：`absent = \|R\(A∪L)\|` 已把请假排除在罚款外，开关语义（true=请假按缺勤计）无法表达，**忽略之** | 新口径 B3 已裁定「请假只折算不罚款」，与开关无关 |
| 旧按天路径（`month < shiftModelFromMonth`） | **继续沿用现有语义**（`PayrollContextProvider.java:117` 消费），保证历史月零突变 | 兼容优先 |

**结论：冻结保留（FROZEN）——不删除键与库表，不新增引用。**
- **不删除**的理由：删除需 DDL + 代码 + 演示端 Mock + 文档多点同步（且破坏历史按天路径），成本高收益低；冻结后可随时在旧路径复用。
- **对既有配置的影响**：`leave_setting` 表与 `AlgoProperties.Leave.deductEnabledDefault` **均不改动**；仅新增「新路径不读取」的说明（文档改动），并在实现侧以 `month` 分支隔离（代码改动，责任=后端工程师）。

---

## 8. 失败降级与幂等（不得阻塞整批算薪）

### 8.1 失败降级

| 触发 | 兜底行为 |
| ---- | -------- |
| `month` 无法解析（非法账期） | 返回 `AttendanceStat.empty()`，整批继续（沿用 `PayrollContextProvider` 现状） |
| `|R| = 0`（空排班/无排班数据） | 按 `zeroSchedulePolicy`，默认 `FULL_BASIC`（**不克扣**），挡除零 |
| 打卡记录缺失/全 ABNORMAL | `A=∅` → 折算 0；罚款经 §7.1 链封顶 + 净额下限 0；不抛异常，写明细文案 |
| 请假区间非法（同日 PM→AM） | 计 0 班次（对齐 S5 `DATE_INVALID`），不抛异常 |
| **`period_name` 空 + 当日多班次（B4）** | 按当日**全部**排班班次计出勤（**不克扣**），推入 `PERIOD_NAME_MISSING` 告警（交人工复核），不抛异常 |
| 记录字段缺失（`period_index`/`shift_id` 为 null） | 记录侧按三态优先级处理（`period_name` 空走②）；排班侧跳过该行（不计入 `R`），并 `warn` 留痕 |
| 新 source `PRORATED` 未注册（灰度期） | 按 `unknownSourcePolicy=ZERO`，该项按 0 计并写文案，**不中断批**（沿用 S2 降级表） |
| 单条脏数据导致异常 | 逐员工 try/catch：该员工按「基本工资全额 + 无罚款」降级，其余员工不受影响 |

### 8.2 幂等 / 试算路径（B7 新增 NFR）

| # | 断言 | 依据 | 如何验证 |
| - | ---- | ---- | -------- |
| N1 | **试算载体**：本项目**无独立试算端点**；试算 = `POST /api/v1/finance/payrolls/generate` 造 `DRAFT` 草稿（`PayrollController.java:61`） | `PayrollController` 仅 `generate` 造草稿，无 dry-run 接口 | 调用 `generate` 后查库/列表确认单据 `status=DRAFT`；无规则改动时草稿明细与规则项一致 |
| N2 | **同月 DRAFT/REJECTED 覆盖重建结果一致**：同月已存在 `DRAFT`/`REJECTED` 时按 `deleteExisting` 覆盖重建；**同一输入（同账期开关 + 同源数据 + 同规则快照）重复生成，结果逐位一致** | `PayrollServiceImpl.java:171-176`（`deleteExisting` + 覆盖重建，`DRAFT`）| 对同一 `month` 连续调用 `generate` 两次 → 比对两次 `payroll_item`（`item_key/amount/detail`）逐位一致；断言条数相同、无残留旧单 |
| N3 | **同月存在非 DRAFT/REJECTED 单据 → 9405 整批拒绝** | `PayrollServiceImpl.java:154-163`（`PayrollGenerateGuard.findBlockingMonthly` → `FINANCE_PAYROLL_GENERATED` 9405） | 将某单置为 `PENDING_APPROVAL` 后调用 `generate` → 断言抛 9405、**无任何部分成功**（同月单据数与状态不变） |
| N4 | **与账期锁无交互变更**：账期锁 `PayrollLockQueryService`（`isMonthLocked`，`9606` 用于请假撤回）与折算无交互——折算为纯函数，不改变锁语义；`allowNegativeNet`/封顶亦不写锁 | `PayrollLockQueryService.java:27`；`PayrollServiceImpl.java:153-176` | 折算逻辑上线前后，对同一 (employeeId, month) 调 `isMonthLocked` 结果一致；撤回已批请假时锁行为不变（9606 触发条件不变） |

> 说明：折算为**纯函数**（输入 = 账期 + 源数据 + 规则快照，无跨次状态），故幂等无需额外加锁；三角色（试算/生成/锁）行为由账期开关确定，不含随机性。

---

## 9. 对既有契约的影响评估（B2/B3 全面修订）

### 9.1 契约面总览

| 契约面 | 是否改动 | 说明 |
| ------ | :------: | ---- |
| `db.md` 表结构 | **否** | `attendance_schedule(shift_id)`、`attendance_record(period_index/period_name)`、`payroll_item.detail` 均已具备；**无新增列/改列型** |
| `db.md` §8.6 取值集合（非结构） | **是（文档）** | 两处 `source` 枚举追加 `PRORATED`（§9.4；文档同步，非 DDL） |
| `db.md` 数据（非结构） | **是（数据）** | ① 删 8 条中班 `attendance_shift`（B5）；② `payroll_rule_item.ABSENT_FINE.params.amount: 150→100`（B4）。均走**新 Flyway 版本 + seed 同步**，由数据库/后端执行（**C 档授权**） |
| 工资单接口契约 | **否（写 API 层）** | 端点为 `PayrollController.java:42`（`/api/v1/finance/payrolls`，10 接口），契约载体为 `server-architecture.md`；出参 `items[]` 形状不变，仅 `detail` 文案变。**`api.md` 一期与 §7 增量均不含工资单接口**（C9），v1.0 引用错位已更正 |
| 前端展示逻辑 | **低风险** | 工资单明细**项数不变**；若前端**仅渲染** `detail` 文本 → 零改动；若前端**解析** `detail` 文本（反模式）→ 需前端同步。建议渲染结构化字段 |

### 9.2 新增 `PRORATED` 的完整改动面（B2 逐项登记）

> 更正 v1.0「新 source 只是注册一个解析器 / 前端零字段改动」的过度表述：**新增 `PRORATED` 至少牵连以下 6 项既有契约/代码**。

| # | 文件 / 位置 | 改动内容 | 性质 | 责任角色 |
| - | ---------- | -------- | ---- | -------- |
| 1 | `PayrollSource.java:12-21` | 枚举追加 `PRORATED("出勤折算")` | **代码改动** | 后端工程师 |
| 1' | `PayrollSource.codes()`（`:33-40`） | 自动包含新增值（无额外改动）→ 使 `PayrollSource.isValid("PRORATED")` 为 true；**`labelOf` 同步可用** | 代码（随枚举自动） | 后端工程师 |
| 2 | `PayrollRuleValidator.java:23,77-79` | `ITEM_SOURCES` 常量须加 `PRORATED`（**不改则保存 `PRORATED` 规则项返回 400**）；错误文案同步 | **代码改动（必须改）** | 后端工程师 |
| 3 | `AttendanceStat.java:15-19` + `PayrollCalcContext.java:18` | 记录仅 `lateCount/earlyLeaveCount/absentCount/abnormalCount/leaveCount`，**缺 `|R|`/`|A∩R|` 班次字段** → 需新增 `requiredShifts`/`attendedShifts`（供 `PRORATED` 取值）；T1 组合指标另需 `absentOrLeaveCount` | **代码改动** | 后端工程师 |
| 4 | `PayrollContextProvider.java:76-79`、`:106-110` | 取数查询未取 `period_index/period_name`（记录）与 `shift_id`（排班）→ 需补列并 join `attendance_shift.start_time` 以构造 `R/A` | **代码改动** | 后端工程师 |
| 5 | `PayrollItemVO.java:23` / `PayrollRuleItemVO.java:26` 的 `sourceLabel` | 走 `PayrollSource.labelOf`（`PayrollServiceImpl.java:785`、`PayrollRuleServiceImpl.java:232`）→ 随枚举自动产出「出勤折算」 | 代码（随枚举自动） | 后端工程师 |
| 6 | 演示端字典与编辑器（`hrm-demo`） | ① `financeStore.js:31-36` `PAYROLL_ITEM_SOURCE_LABEL` 加 `PRORATED: '出勤折算'`；② `constants/dict.js:257` `PAYROLL_ITEM_SOURCE` 加键与 hint；③ `PayrollRuleEditor.vue:47,358` 的 `FIXED` 专属「取数字段」分支需为 `PRORATED` 增设参数编辑器（否则界面出现原始英文或参数不可编辑） | **代码改动（演示工程）** | 前端工程师 |
| 7 | `docs/db.md` §8.6.2 / §8.6.4 | `source` 取值集合追加 `PRORATED`（§9.4） | **文档改动** | 数据库工程师（文档同步） |

> **必须改**：#1/#2（否则规则项无法保存或被判非法）；#3/#4（否则 `PRORATED` 解析器取不到 `|A∩R|/|R|`，静默降级为 0，`PayrollResolverRegistry.java:65-69`）；#6（否则界面出现原始英文/参数不可编辑）。

### 9.3 内部端口与数据变更（登记）

| 项 | 处置 | 性质 | 责任 |
| -- | ---- | ---- | ---- |
| `ApprovedLeaveDaysPort` | 需增补「请假班次单元」能力（或新增只读方法 `approvedLeaveShiftUnits(...)`），供 `PayrollContextProvider` 计算 `L` | **代码改动** | 后端工程师（本文只给签名建议） |
| `ABSENT_FINE.params` | `amount 150→100`、语义「元/天→元/班次」 | **数据变更**（非 DDL） | 数据库/后端 + C 档授权 |
| 中班 `attendance_shift` 8 行 | 删除或逻辑删除 | **数据变更** | 数据库 + C 档授权 |
| `detail` 文案单位（建议项 S2） | 「… 次 × … 元」→「… **班次** × … 元」（`AttendanceItemResolver.java:53`） | **代码改动（文案，非字段）** | 后端工程师 |

### 9.4 `db.md` 的 `source` 取值集合扩展处置（B3）

**性质查证结论：该取值集合是「文档枚举」，非 DB 约束。**
- 依据：`V8__payroll.sql:41` 与 `:97` 的 `source` 列类型均为 **`VARCHAR(16) NOT NULL COMMENT '来源：FIXED/ATTENDANCE/KPI/MANUAL'`**，**无 `CHECK` 约束、无 `ENUM` 类型**；`db.md:812/860` 的文字枚举与列 COMMENT 一致。→ **写入 `PRORATED` 不会被数据库拒绝**。
- 唯一运行时真源：`PayrollSource` 枚举（`payroll_rule_item.source` 写入其 `name()`）。

**需同步位置与改法（文档改动）**：

| 位置 | 现文 | 改为 |
| ---- | ---- | ---- |
| `db.md` §8.6.2（`payroll_rule_item.source`，L812） | 取值集合 = FIXED / ATTENDANCE / KPI / MANUAL | 追加 PRORATED（出勤折算），并注明「来源真源为 `PayrollSource` 枚举」 |
| `db.md` §8.6.4（`payroll_item.source`，L860） | 取值集合同 §8.6.2 | 同上 |
| `V8__payroll.sql:41,97` 列 COMMENT（可选） | `'来源：FIXED/ATTENDANCE/KPI/MANUAL'` | 追加 `/PRORATED`。**属 DDL（`ALTER TABLE … MODIFY COLUMN … COMMENT`）→ C 档授权，且非功能必需**（不影响写入），本文仅登记 |

**结论**：撤回 v1.0「与 `api.md`/`db.md` 冲突 无」；改为「**无实质冲突，但需 1 处契约文档取值集合同步（已登记，见上表）**」。

### 9.5 安全面与引用更正（建议项 S4 / C9）

- **安全面声明（S4）**：本变更**不新增外部暴露面**、**不改鉴权/越权口径**（工资单接口的越权过滤逻辑不变）；如涉工资数据脱敏与越权展示，**转网络安全工程师判定**。
- **引用更正（C9）**：v1.0 以 `api.md` 作工资单出参/错误码契约来源属**引用错位**（`api.md` 无工资单接口）。本版工资单契约载体改为 **`PayrollController.java`（`/api/v1/finance/payrolls`）+ `server-architecture.md`**；错误码 `9405`（`enums/ErrorCode.java:217`）与 `9606`（`api.md` §7.2 L683 有登记）沿用不动。

---

## 10. 遗留 `TODO(扩展)` 与待裁定项

| 编号 | 事项 | 类型 | 处置 |
| ---- | ---- | ---- | ---- |
| **T1** | 全勤奖是否含请假 | 口径（考核） | **已裁定：请假算缺勤**（`fullAttendMetric=ABSENT_OR_LEAVE`），本版落实 |
| **T2** | 岗位工资/津贴是否折算 | 口径（计薪） | **已裁定：只折算基本工资**（`proratedFields=[basicSalary]`） |
| **T3** | 罚款是否封顶 / 是否允许负实发 | 口径（计费） | **已裁定：实发不低于 0**（`allowNegativeNet=false`）；`absentFineCapRatio` 为可选加严项，默认关闭 |
| **T5** | 迟到粒度（按次 / 按天） | 口径（扣款） | **已裁定：保持按次**（`lateGranularity=PER_CARD`，= 现状）；`PER_DAY` 未采纳，若启用属**口径变更，须再裁定** |
| T4 | `TODO(扩展): 新增 `requireBothCards`（要求 ON+OFF 成对才算一个班次出勤）开关；当前口径为单 ON 有效卡 | 算法可选 | 登记待评估 |
| T6 | `TODO(扩展): 请假「计薪天数」NATURAL/SCHEDULED 差异在带薪假计发场景需保留；当前仅用于缺勤抵扣故等价 | 算法说明 | 登记 |
| T7 | `TODO(扩展): 单员工月排班行数 > 1000 或跨年批量时，`leaveShiftSet` 可退回 S5 的二分计数（`O(log S)`）替代物化 | 性能 | 登记 |
| T8 | `TODO(扩展): `PERIOD_NAME_MISSING` 告警的落库/展示载体（现为原型内存数组）；实现侧需定义落库表或日志字段 | 实现 | 登记（交后端，非本轮必改） |

> 原 v1.0 的 T1/T2/T3/T5 已由用户裁定并落地（§1）；本版**无未裁定口径**，仅余 `TODO(扩展)` 4 项。

---

## 11. 复现方式与交接

### 11.1 复现
```
cd hrm-dev/docs/algo-scripts
node s2b-payroll-shift.mjs   # 输出：验收算例 / before-after / B1_T5迟到粒度 / T1全勤奖 / 批量基准 / 边界与降级
```
固定种子：批量集 `0x5d1f2a7c` + `fnv1a('shift#<id>')`；同参数重跑**逐位一致**（仅耗时列波动）。

### 11.2 「收敛到服务器复核」清单（本机无 JDK/Maven/MySQL）
- Java 版 `PRORATED` 解析器的 `BigDecimal` 舍入（`HALF_UP` 到分）与原型 `toFixed(2)` 的末位差异 → 单测用**容差断言**（金额 ±0.01）。
- 真实 `attendance_schedule/record` 查询耗时；**候选复合索引 `(employee_id, work_date, shift_id)` 供数据库工程师评估（本文不建索引，建议项 S5）**。
- 账期开关在真实 `payroll` 生成链路上的回归（历史月零突变）；幂等断言 N2/N3 的真实链路验证（§8.2）。
- 均标注：**收敛到服务器阶段复核**。

### 11.3 给主智能体的交接
| 字段 | 内容 |
| ---- | ---- |
| 产出 | 本文档（v2.0）+ `algo-scripts/s2b-payroll-shift.mjs`（修订；未改任何既有源码/文档正文） |
| 证据 | `node s2b-payroll-shift.mjs` 实跑输出；5 验收数字逐位一致；B4/T1/T3 新用例实际值见 §2.4.4/§2.4.5/§5 |
| 影响范围 | 无业务代码改动（只读）；`db.md` 表结构不变；登记**数据变更**（删中班、改罚款 params）+ **代码改动**（§9.2 全部 6 项 + 端口）+ **文档改动**（`db.md` §8.6 取值集合） |
| 如何验证 | 运行上述命令；比照 §2.4.1 五个数字与 §2.4.4 边界表 |
| 如何回滚 | 删除本原型与本文档即可；未触及既有源码 |
| 遗留与 TODO | §10（T4/T6/T7/T8 登记；无未裁定口径） |
| 事实性纠正 | §0.1（C1 引文号废止、C2 查重键、C3 开关播种值、C4 罚款单价、C5 source 列宽、**C6 迟到粒度事实错误**、**C7 C3 悬空引用**、**C8 source 取值集合性质**、**C9 工资单契约引用错位**） |
| 报审 | 本文为**方案阶段产物**；按 P0.6/L8，报审前须先经**技术评审工程师**重评（本版为待重评稿，不自评通过） |
