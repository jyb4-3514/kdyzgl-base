# 技术评审报告：S2b 班次制算薪（缺勤/请假粒度 天 → 班次）

- 评估对象：
  - 方案 `hrm-dev/docs/algo-payroll-shift.md` **v1.0（2026-09-25）**
  - 原型 `hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（同日版本，`SHIFT_CONFIG` 默认值见方案 §7）
- 作者：算法工程师 `express-station-algorithm-engineer`
- 评估方：`express-station-tech-reviewer`（评估方与产出方分离）
- 评估方式：静态文档级评估 + **离线原型实跑**（Node，本机执行）；Java 侧静态审查（本机无 JDK/Maven/MySQL，标注「未运行」）
- **结论等级：打回**
- 准入结论：**不得报主智能体审批**；须由原算法工程师按第 3 节必改项修订后**重评**（结论绑定版本，见第 7 节）

---

## 1. 六维逐维结论

| 维度 | 结论 | 证据（引用原文/文件行号） |
| --- | --- | --- |
| 1 依据充分性 | **部分满足（存在 1 处影响口径判断的事实错误）** | 法规引用**正确**：方案 §0.1 C1、§2.3 D5 引「人社部发〔2025〕2号（2025-01-01）」并称其废止劳社部发〔2008〕3号 —— 经查人社部原文（文号、生效日、月工作日 20.67、月计薪天数 21.75、第三条「同时废止」）**逐条吻合**，且明确声明「本项目班次折算口径由用户裁定，D5 不作口径变更」（§2.3 末段），未以法规冒充项目口径。复杂度/集合运算依据可查（§2.3 D1–D4、D7、D8）。**但**：§3 问题 7 / §5 / §7 称「`LATE_FINE` 现状按天计一次」，与源码不符（见 4.FC1），且由该错误推出了「默认 `DAY` = 行为不变」的错误结论 |
| 2 边界合理性 | **基本满足，1 处兜底歧义** | §2.1 边界表 9 项 + §2.4.4 原型 9 用例，全部有实跑输出；「宁可保守不克扣」方向明确（空排班 `FULL_BASIC`、请假 `L∩R` 取交不多扣、`absent≥0`、日 PM→AM 非法区间计 0 班次）。**但**：「一天两班 + `period_name` 为空」的记录侧判定在 §0.1 C3 与 §8 表述不唯一，按原型实现会退化为只算早班 → **可能克扣**（见 3.B3） |
| 3 NFR 覆盖度 | **部分满足** | 性能：§2.2 复杂度表 + §2.4.3 500 人基准（实跑复现，耗时列随机器波动已预先声明）；失败降级：§8 七条兜底齐备（逐员工 try/catch、未知 source ZERO、不阻塞整批）；可观测性：`payroll_item.detail` 解释文案 + `warn` 留痕；参数外置：§7 十键清单齐备（`absentFineCapRatio`/`fullAttendMetric`/`lateGranularity` 标「待裁定」）。**缺口**：① 幂等/试算路径未覆盖（见 3.B7）；② 安全面「是否声明」缺失（无「不新增暴露面/鉴权不变」一句话，本维只作形式核对，实质判定归网络安全工程师）；③ 风险登记缺失（见 3.B4） |
| 4 复杂度论证 | **满足** | §2.2 逐环节 `O(S)/O(S+R)/O(L·U)`，单员工 `O(S log S + R + L·U)`、批量 `O(Σ…)`，与原型实现一致（`requiredShiftSet`/`attendedShiftSet`/`leaveShiftSet`/差集逐行）；规模上界（`S≤62, R≤124, U≤62`）与实现自洽；「不存在随规则项数恶化的路径」与 `PayrollResolverRegistry` 一次分发一致（`PayrollResolverRegistry.java:60-70`）。**未论证的隐含假设**：`middayBoundaryMinute=720` 对 `start_time` 恰为 `12:00` 的班次判为晚班，与晚班键冲突（B5 删中班后无现存数据，属遗留风险，见第 4 节建议项 S6） |
| 5 假设与风险登记 | **不满足** | 全文无「显式假设清单（假设不成立后果）」，亦无风险登记（触发条件/影响面/缓解）。§10 仅为待裁定项与 `TODO(扩展)`；§8 为被动降级。**未登记的已知风险**至少四类：负实发（原型 §2.4.4「无打卡」= **−6000**）、旷工罚款超基本工资、双班制同事实扣款由 150/天 → 200/天（§2.4.2 已算出但未登记为客诉风险）、空排班 `FULL_BASIC` 的**多发**风险 |
| 6 与既有契约一致性 | **不满足（影响评估不完整，且「无冲突」结论不成立）** | §9 表仅登记 2 项（数据变更、`ApprovedLeaveDaysPort`），遗漏对既有代码契约的多处改动（见 3.B2）；§9「`api.md`/`db.md` 冲突 无」不成立 —— `db.md:812/860` 将 `source` 取值枚举为 `FIXED/ATTENDANCE/KPI/MANUAL`，新增 `PRORATED` 属取值集合扩展**未声明处置**；§9 引 `api.md` 作工资单出参契约来源属**引用错位**（`api.md` 一期 24 接口与 §7 请假增量**均不含工资单**，见 4.FC2）。**正向确认**：`source VARCHAR(16)` 与 `PRORATED`(8 字符) 相容，C5 成立；`db.md` 表结构（`attendance_schedule.shift_id`、`attendance_record.period_index/period_name VARCHAR(20)`）确已具备，§9「无 DDL 变更」成立 |

**分级依据：** 命中三级红线的两条 —— ①「与 `api.md`/`db.md` 冲突且未声明处置」（`source` 取值集合）；②「缺关键论证 / 影响正确性的事实错误」（`LATE_FINE` 现状粒度判断错误，可能导致未经用户裁定的口径变更）。故判 **打回**，非「有条件通过」。

---

## 2. 本次评审实际执行的验证（证据）

### 2.1 原型实跑（本机 Node，实跑结果）

```
cd hrm-dev/docs/algo-scripts
node s2b-payroll-shift.mjs
```

| 核对项 | 方案声称 | 实跑结果 | 一致 |
| --- | --- | --- | :--: |
| 5 个验收数字 | 1500/1475/1375/1450/1250 | **1500/1475/1375/1450/1250**（逐位，`验收全部通过=true`） | ✅ |
| 平均应出班次（500 人） | 49.87 | 49.87 | ✅ |
| 实得均值/标准差 | 3708.08 / 219.25 | 3708.08 / 219.25 | ✅ |
| 实得最小/最大 | 2920 / 4000 | 2920 / 4000 | ✅ |
| 恒等式违例 | 0 / 500 | 0 | ✅ |
| 耗时（总/单人） | 135.11 ms / 0.2702 ms | 75.73 ms / 0.1515 ms | 环境相关（方案已预声明「仅耗时列随机器波动」） |
| 边界与降级 9 用例 | §2.4.4 表 | 逐项一致（含「无打卡」**−6000**、单班制历史 ratio=1 → 1500、跨日单 1400） | ✅ |

**结论：5 个验收数字为真实运行结果，非手写常量；固定种子（`0x5d1f2a7c` + `fnv1a('shift#<id>')`）可独立复现（本报告已复现）。** 原型文件亦未发现硬编码回填期望值（`acceptance()` 与 `expect` 独立于 `calcShiftPayroll`）。

### 2.2 契约与源码静态核对（未运行 Java）

| 核对点 | 方案声称 | 实测 | 判定 |
| --- | --- | --- | :--: |
| `leave_setting.leave_deduct_enabled` 播种值 | 0（C3） | `sql/seed/kdyzgl_test_seed.sql:918-919` = **0**；`V9__leave.sql:77` 默认 0 | ✅ C3 成立 |
| `ABSENT_FINE` 规则项参数 | `amount=150`（按天）、`cap=0`（C4） | seed:1364 `{"metric":"ABSENT","mode":"PER_COUNT","amount":150,"cap":0}` | ✅ C4 成立 |
| 中班记录数 | 8 条（B5） | seed:238/241/244/247/250/253/256/259 = **8 条**「中班 12:00–20:00」 | ✅ B5 成立 |
| `payroll_rule_item.source` 列宽 | `VARCHAR(16)`（C5） | `db.md:812` `VARCHAR(16)` | ✅ C5 成立 |
| `AttendanceSchedule` 查重键 | `employeeId+workDate`（C2） | `AttendanceSchedule.java:17` 注释「唯一性 = employeeId + workDate（活跃唯一），由 Service 查重保证」 | ✅ C2 成立 |
| 服务端现状缺勤/出勤口径 | §2.4.2 复刻 | `PayrollContextProvider.java:84-123`（`Set<LocalDate>` 去重、`excludeLeaveDays`、`absentCount` 下限 0） | ✅ 复刻正确 |
| 现状迟到计数口径 | 「按天计一次」（§3 问题 7） | `PayrollContextProvider.java:94-98` 每张有效 ON 卡 `lateCount++`；`hrm-demo/src/shared/mock/attendanceStore.js:893` 同口径（`.filter(...).length`） | ❌ **不成立，见 4.FC1** |
| 幂等/账期锁 | §4「已出账（非 DRAFT/REJECTED）拒绝重复生成」 | `PayrollServiceImpl.java:153-176`：非 DRAFT/REJECTED → 9405 整批拒绝；DRAFT/REJECTED → `deleteExisting` 覆盖重建 | ⚠ 成立但未在 NFR 层声明（见 3.B7） |

---

## 3. 必改项清单（7 条，每条含验收标准与复核方式）

### B1 `LATE_FINE` 现状粒度判断错误，且由此推出的「默认 `DAY` = 行为不变」不成立

- **问题**：方案 §3 问题 7 称「`LATE_FINE` 现状按天计一次」，§5/§7 据此设 `lateGranularity` 默认 `DAY` 并声明「行为不变」。实测现状为**按有效 ON 卡计数**（`PayrollContextProvider.java:94-98`、`attendanceStore.js:893`），在单班制历史数据下才恰好等价「一天一次」；在本次要落地的**双班制**数据下，代码现状即班次粒度。
- **为什么必须改**：该错误使方案把一个**实际口径变更**（同一人同一天两次迟到，按天只扣 20 → 现状扣 40）当作「行为不变」，从而**未按 §11.4 口径红线交给用户裁定**，也未被 §10 待裁定项覆盖；一旦默认 `DAY` 落地，迟到扣款将静默下降，属反模式 A04 同源风险。
- **验收标准**：§3 问题 7 与 §7 的 `lateGranularity` 条目改为二者之一并自洽：① 默认 `SHIFT`（维持现状行为），把「切 `DAY`」列入待用户裁定项并注明属口径变更；或 ② 默认 `DAY`，但明确标注「本项为默认行为变更，须用户裁定后方可启用」「现状默认等价 `SHIFT`」。两种写法均须引用 `PayrollContextProvider.java:94-98` 与 `attendanceStore.js:893` 作为现状证据。
- **复核方式**：读修订后 §3 问题 7、§5、§7、§10，确认默认值与「行为不变 / 属口径变更」表述自洽，且现状证据的行号正确。

### B2 §9 契约影响评估不完整（新增 `PRORATED` 的完整改动面未登记）

- **问题**：§9 仅登记「数据变更」与 `ApprovedLeaveDaysPort` 两个端口项，并称「新 source 只是注册一个解析器」「前端零字段改动」。实测新增 `PRORATED` 至少还牵连下列既有契约/代码：
  1. `PayrollSource.java:12-21` 枚举（缺则 `PayrollSource.isValid` 为 false）；
  2. `PayrollRuleValidator.java:23,77-79` 的 `ITEM_SOURCES = "FIXED / ATTENDANCE / KPI / MANUAL"` 白名单与错误文案 → **不改则无法保存 `PRORATED` 规则项（400）**；
  3. `AttendanceStat.java:15-19`（record 仅 `lateCount/earlyLeaveCount/absentCount/abnormalCount/leaveCount`）**缺少 `|R|`/`|A|` 班次字段**，`PayrollCalcContext.java:18` 随之；
  4. `PayrollContextProvider.java:76-79`（`select(workDate, checkType, status)`，未取 `period_index/period_name`）与 `:106-110`（排班 `select(workDate)`，未取 `shift_id` → 需 join `attendance_shift.start_time`）；
  5. `PayrollItemVO.java:23` / `PayrollRuleItemVO.java:26` 的 `sourceLabel` 取值（`PayrollServiceImpl.java:785`、`PayrollRuleServiceImpl.java:232` 走 `PayrollSource.labelOf`）；
  6. 演示端字典 `hrm-demo/src/shared/mock/financeStore.js:31-36`（`PAYROLL_ITEM_SOURCE_LABEL`）、`hrm-demo/src/shared/constants/dict.js:257`（`PAYROLL_ITEM_SOURCE`）与规则项编辑器 `PayrollRuleEditor.vue:47,358`（`item.source === 'FIXED'` 专属「取数字段」参数编辑器）。
- **为什么必须改**：§9 是评审与报审判断「改动面是否可控」的唯一依据；漏登第 2 项会导致按方案落地时**规则项保存即失败**，漏登第 3/4 项会导致「折算解析器取不到 `|A|/|R|`」而静默走未知来源降级为 0（`PayrollResolverRegistry.java:65-69`），漏登第 6 项会导致工资单/规则项界面出现原始英文 `PRORATED` 或参数不可编辑。§9 结论「前端零字段改动」亦只对**工资单明细字段形状**成立，对**字典与规则项编辑器分支**不成立。
- **验收标准**：§9 增行逐条列出上述 6 项（含文件:行号）、处置方式（改 / 不改及理由）与责任角色；并明确写出「候选 A 非『零改动』，工资单**明细项数不变**、`gross_amount` 值随口径变化、来源字典与规则项编辑器需同步」。
- **复核方式**：按修订后 §9 清单逐文件到仓库比对（本报告已给出行号），并确认 `PayrollSource`/`PayrollRuleValidator` 两项被标为「必须改」。

### B3 `db.md` 的 `source` 取值集合扩展未声明处置（契约一致性）

- **问题**：`db.md:812`（`payroll_rule_item.source`）与 `db.md:860`（`payroll_item.source`）将取值枚举为 `FIXED / ATTENDANCE / KPI / MANUAL`；方案新增 `PRORATED`（§3 问题 5、§4）后，§9 仍写「`api.md`/`db.md` 冲突 **无**」。
- **为什么必须改**：`api.md`/`db.md` 是本项目**契约基线**，其字段取值枚举属契约内容；新增枚举值虽非 DDL，但**必须在契约文档登记同步**，否则实现与契约漂移，且下一轮评审/新人按 db.md 无法确认 `PRORATED` 合法。方案自评「无冲突」会使该偏差被报审环节漏过。
- **验收标准**：§9 增行「`db.md` §8.6 两处 `source` 取值集合需追加 `PRORATED`（文档同步，非 DDL；同一真源为 `PayrollSource` 枚举）」，并撤下或改写「`api.md`/`db.md` 冲突 无」为「无实质冲突，但需 1 处契约文档取值集合同步（已登记）」。同时把工资单接口契约的引用载体改为真实来源（见 4.FC2），不得继续以 `api.md` 作为工资单出参依据。
- **复核方式**：读修订后 §9，确认取值集合同步行存在、责任角色明确、且不再出现「与 api.md 工资单出参」的引用。

### B4 「一天两班 + `period_name` 为空」的兼容判定歧义（可致克扣）

- **问题**：§0.1 C3 与 §8 对空/`NULL` `period_name` 的处置表述不唯一（§8 写「记录侧按哨兵/0 处理」）。原型实现为「非哨兵即按 `period_index`」（`s2b-payroll-shift.mjs:103-107`），因此当日有两班排班而记录 `period_name` 为空、`period_index=0` 时，**仅计早班出勤**，晚班被算作旷工 → 折算与罚款同时不利，方向是**克扣**。
- **为什么必须改**：与方案自身「宁可保守不可克扣」原则（§2.1 空排班兜底、§8 不克扣）冲突；且 `attendance_record.period_name` 为 `DEFAULT NULL`（`V5__attendance.sql:89`），非哨兵空值是可出现状态，不能靠「数据里恰好都是`全天班`」当前提。
- **验收标准**：§2.1/§8 给出**唯一**判定优先级并覆盖三态：① `period_name` 命中哨兵 → 覆盖当日全部排班班次；② `period_name` 为空/`NULL` 且当日排班班次 > 1 → **不得判为亏勤**（按当日应出班次数计入出勤，或计入出勤并置告警标志交人工复核）；③ 其余按 `period_index`。原型须补 1 个对应用例（「空 `period_name` + 双班排班」），输出 `ratio=1`（或输出明确的告警字段）。
- **复核方式**：运行原型，确认新增用例输出与 §2.1/§8 表述逐字对应；确认三态优先级在文档中出现且仅出现一处定义。

### B5 缺「假设与风险登记」（含负实发、罚款超基本工资、客诉、多发）

- **问题**：维度 5 要求逐条列显式假设与风险（触发条件/影响面/缓解），方案无此节。已算出但未登记的风险：① 负实发（§2.4.4「无打卡」= **−6000**；既有 `AlgoProperties.java:99-103` 注释载明 Mock 现状最小仅 **−360**，量级放大约 16 倍）；② 旷工罚款 > 折算后基本工资；③ 双班制同事实扣款 150/天 → 200/天（§2.4.2 已算，未登记为员工争议/客诉风险）；④ 空排班 `FULL_BASIC` 在**漏排班**时按全额发放（多发放风险，与「不克扣」是同一枚硬币两面）。
- **为什么必须改**：涉及员工实发金额，风险未登记则无法在报审时评估影响面与缓解；且 T3 属待用户裁定项，裁定前必须有「已识别风险 + 临时缓解」而非空白。
- **验收标准**：新增「假设与风险登记」一节，含：① 显式假设 ≥4 条（含「双班制上线后 `attendance_schedule` 每驿站每员工每日 ≤2 行」「`period_name` 语义随班次制切换」「上线月由发布计划确定」），每条写「假设不成立时的后果」；② 上列 4 类风险各一行，含触发条件、影响面（影响哪些员工的实发/哪些单据）、缓解动作；③ 明确与 T3 的关系（未裁定期间的默认缓解是什么）。
- **复核方式**：读新增节，核对 4 类风险是否各有触发条件与缓解；核对 −6000 与 −360 两个量级值均有出处。

### B6 `absentFineCapRatio` 与既有 cap 机制/负净额开关的优先级未定

- **问题**：§7 新增 `absentFineCapRatio`（罚款上限 = 折算基本 × 系数，0=不封顶），但既有 `ABSENT_FINE.params.cap`（绝对额封顶，`AttendanceItemResolver.java:55-59`）、`hrm.algo.payroll.itemCapSemantics`（`AlgoProperties.java:104-105`，`ZERO_MEANS_NO_CAP`）、以及 `hrm.algo.payroll.allowNegativeNet`（`AlgoProperties.java:96-103`，由 `PayrollTotalsPolicy.java:45-46` 消费，`false` 即把净额下限置 0）三者**语义重叠**，方案未给优先级；§7 又把 `allowNegativeNet` 标为「沿用 Q3 待裁定」而未与 T3（负实发）关联。
- **为什么必须改**：三处口径并存而无优先级，实现时会出现「规则项 `cap` 封顶了、配置 `capRatio` 又算一次」或「封顶后仍负实发」的歧义；且项目已有现成「实发不低于 0」开关（`allowNegativeNet=false`），方案却未指出，导致看起来需要新增键。
- **验收标准**：§7 给出优先级表并写明冲突生效规则，至少覆盖：配置 `absentFineCapRatio` 与规则项 `params.cap` 的优先关系；`cap===0` 时按 `itemCapSemantics` 解释；`allowNegativeNet=false` 作为「实发不低于 0」的可配兜底（明示既有键，无需新增）。同时 §10 T3 条目补「未裁定期间默认缓解 = `allowNegativeNet` 现状 true（允许为负）+ 明细文案标注罚款构成」。
- **复核方式**：比对 `AlgoProperties.java:92-105`、`AttendanceItemResolver.java:35-60`、`PayrollTotalsPolicy.java:29-46`，确认修订后的优先级表述与代码实际执行顺序一致。

### B7 幂等/试算路径未覆盖（NFR）

- **问题**：任务口径要求「试算与结算一致性」，方案未声明：① 试算载体（本项目无独立试算端点，`PayrollController.java:61` 仅 `POST /generate` 造草稿）；② 同月 DRAFT/REJECTED 重复生成的语义（`PayrollServiceImpl.java:171-176` `deleteExisting` + 覆盖重建 → 同输入同结果）；③ 与折算的交互（折算为纯函数，随账期开关确定，无跨次状态）。
- **为什么必须改**：折旧率算薪属关键财务路径，幂等语义不写在方案里，验收阶段无法判定「重复生成结果一致」；也容易被误当作需要额外加锁。
- **验收标准**：新增（或并入 §8）一段，含三条可判定断言：① 「试算 = `POST /generate` 造草稿」；② 「同月已存在 DRAFT/REJECTED 时按 `deleteExisting` 覆盖重建，同一输入（同一账期开关 + 同一源数据 + 同一规则快照）重复生成结果逐位一致」；③ 「同月存在非 DRAFT/REJECTED 单据 → 9405 整批拒绝」。并声明与账期锁（`PayrollLockQueryService`，9606 用于请假撤回）无交互变更。
- **复核方式**：比对 `PayrollServiceImpl.java:153-176`、`PayrollGenerateGuard`、`PayrollLockQueryService.java:27`，确认三条断言与代码一致。

---

## 4. 建议项（不阻塞通过）

| # | 建议 | 可验收动作 |
| --- | --- | --- |
| S1 | §0.2「主公式」直接写 `|A|/|R|`，需靠 §2.1 约束 2 才能补出「A 已与 R 取交」 | 在 §0.2 公式处写 `|A∩R|/|R|`（或加脚注），与原型 `calcShiftPayroll` 的 `attended = attendedRaw ∩ required` 一致 |
| S2 | `AttendanceItemResolver.java:53` 明细文案为「… 次 × … 元」，新口径单位为「班次」 | 方案 §9 加一行：`detail` 单位由「次」改「班次」（文案变更，非字段变更） |
| S3 | 晚班 `16:00–24:00` 的跨零点下班卡（如次日 00:30）归属未定义 | 在 §2.1 假设处明确：「`work_date` 取班次开始日，`period_index=1`；跨零点 OFF 卡归属同 `work_date`」 |
| S4 | 安全面「是否声明」缺失（本维只作形式核对） | 在 §9 加一行：「本变更不新增外部暴露面、不改鉴权/越权口径；如涉工资数据脱敏与越权展示，转网络安全工程师判定」 |
| S5 | §11.2 已正确把「索引实现」交数据库工程师，但未提复合索引建议方向 | 保持不改结论，仅建议在 §11.2 注明候选 `(employee_id, work_date, shift_id)` 供数据库工程师评估（不自行建索引） |
| S6 | `middayBoundaryMinute=720` 使 `start_time='12:00'` 判为晚班，与晚班键冲突（当前无 12:00 班次，属遗留风险） | 在 §7 该键备注「若存在 12:00 起始班次将与之冲突，删中班后须校验无此类数据」；或改为经 `attendance_shift` 配置取序号 |

---

## 5. 六个技术点核验结论

| # | 技术点 | 结论 | 理由（证据） |
| --- | --- | --- | --- |
| 1 | 折算落点选型（§4 候选 A）是否前端零字段改动 | **存疑** | 字段形状确不变（`payroll_item` 仍一条加项；旧单按 `rule_snapshot` 内 `source=FIXED` 分发，历史单据 `amount` 已落库 → 回读不变，§4 末段成立）。但：① `gross_amount` 值由 1500 变 1475（新单，口径变更需在 §9 声明）；② 「前端零改动」不成立 —— 需同步 `PayrollSource` label、演示端 `PAYROLL_ITEM_SOURCE_LABEL`/`PAYROLL_ITEM_SOURCE` 字典、`PayrollRuleEditor.vue:47,358` 的 `FIXED` 分支（见 3.B2） |
| 2 | 试算与结算一致性 / 幂等 / 账期锁交互 | **存疑** | 幂等实质成立（9405 整批拒绝 + DRAFT/REJECTED 覆盖重建，`PayrollServiceImpl.java:153-176`），账期锁（9606）与折算无交互（折算不改变锁语义）。但方案未在 NFR 层声明试算载体与「同输入同结果」断言（见 3.B7） |
| 3 | 历史兼容双保险是否真能保证升级前月份零语义突变；哨兵对「一天两班但 `period_name` 为空」是否失效 | **存疑** | 账期开关对 `month < shiftModelFromMonth` 走旧路径，**能**保证已出账月份零突变（旧单 `amount` 已落库 + 9405 拒绝重算，双保险成立）；但**哨兵对「一天两班 + `period_name` 为空」失效**：原型 `s2b-payroll-shift.mjs:103-107` 非哨兵即按 `period_index`，会只计早班 → 亏勤（可能克扣），见 3.B4 |
| 4 | `absentFineCapRatio=0`（不封顶）风险，−6000 是否可接受 | **不通过（须补登记）** | −6000 由原型实跑复现（§2.4.4）。允许负净额本身与现状一致（`allowNegativeNet=true`），但最坏值由 −360 放大到 −6000，属**未登记风险**；且方案未指出「实发不低于 0」可直接用既有 `allowNegativeNet=false` 表达（`PayrollTotalsPolicy.java:45-46`），亦未定 `capRatio` 与 `params.cap` 优先级 → 见 3.B5/B6。取值应由用户裁定（不在本角色职权） |
| 5 | 5 个验收数字复现可信度 | **通过** | 本机实跑逐位一致（1500/1475/1375/1450/1250），并核对了 `acceptance()` 的 `expect` 与计算函数相互独立，非硬编码回填；固定种子可复现 |
| 6 | `leave_setting.leave_deduct_enabled` 去留 | **不通过** | 新口径下 `absent = \|R\(A∪L)\|` 已把请假排除在罚款外，该开关（true=请假按缺勤计）在新路径**语义失效**；方案 §0.1 C3 写「去留见 §3 问题 3/§7」，但 §3 问题 3 与 §7 **均未讨论**该开关 → 引用悬空 + 决策缺失，见 3.B6 同批修正（并入 B6 之外的独立条目 **B8** 亦可，本报告将其并入 B6 的验收一并处理，故不计入必改项总数） |

> 说明：上表第 6 点与第 4 点的「须补登记」已分别落入必改项 B6；为避免必改项重复计数，最终必改项为 **B1–B7 共 7 条**（`leave_deduct_enabled` 作为 B6 验收内容之一）。

---

## 6. 对 4 项待用户裁定口径的影响评估（是否引发架构变更）

| 待裁定项 | 取值变化落在何处 | 是否引发**架构变更** | 说明（对方案的处置建议） |
| --- | --- | --- | --- |
| **T1** 全勤奖是否含请假（`fullAttendMetric`） | §7 参数键取值 | **否**（架构不变），但需**局部实现扩展** | 默认 `ABSENT` 为纯参数路径（复用 `AttendanceItemResolver` + `attendanceFieldMap` 的 `ABSENT→absentCount`）。严格模式 `ABSENT_OR_LEAVE=（旷工+请假班次）` 是**组合指标**：`AttendanceStat.byField` 现为单字段映射（`AttendanceStat.java:27-39`）、`attendanceFieldMap` 现为「指标→单字段」（`AlgoProperties.java:89-91`），故需在统计层新增一个组合字段/组合键。属**局部实现点**，方案 §7/§9 须登记该落点，但**不改变集合运算主干与四件套**，不构成阻塞 |
| **T2** 岗位工资/津贴是否同样折算（`proratedFields`） | §7 参数列表 + 规则项 source 列表 | **否**（**纯参数**） | PRORATED 解析器对 `proratedFields` 内字段逐个折算；扩充为 `[basicSalary, postSalary, allowances]` 只改配置与规则项数据，架构不变 |
| **T3** 罚款是否封顶 / 是否允许负实发 | §7 `absentFineCapRatio`、既有 `allowNegativeNet` | **否**（架构不变），**优先级需定** | `capRatio` 为参数；「实发不低于 0」用既有键即可表达（`PayrollTotalsPolicy.java:45-46`）。需先定与 `params.cap`/`itemCapSemantics` 的优先级（见 3.B6），但不改架构 |
| **T5** 迟到粒度（天 / 班次）（`lateGranularity`） | §7 参数键取值 | **否**（架构不变），但需**局部实现扩展 + 默认值修正** | `SHIFT` 与现状实现一致（`PayrollContextProvider.java:94-98`）。切 `DAY` 需对 `lateCount` 增加「按日去重」统计（现为按卡 `int` 计数），属 provider 层局部扩展；同时**默认值必须修正**（见 3.B1），否则默认即口径变更 |

**结论：4 项待裁定口径均不引发架构变更**（不会改变「以排班行为计量单位 + 集合运算 + 表驱动注册表」的整体设计，也不改变四件套结构），其中 **T1/T5 涉及局部统计字段/组合指标的实现扩展、T3 涉及优先级定义**，须在方案 §7/§9 登记落点；**本次「打回」与这 4 项待裁定口径无关**，请勿将二者混同（避免误判阻塞）。

---

## 7. 方案版本绑定声明

- 本次评审结论**仅绑定**：`hrm-dev/docs/algo-payroll-shift.md` **v1.0 / 2026-09-25** 与 `hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（同日版本，`SHIFT_CONFIG` 默认值见方案 §7）。
- 下列任一变更均**使本结论失效，须重评**：① 方案正文任一节的结论或表述修改；② §7 参数键的默认值修改（含 `lateGranularity`/`absentFineCapRatio`/`zeroSchedulePolicy`）；③ 原型脚本中口径相关逻辑或 `SHIFT_CONFIG` 修改；④ 待裁定项 T1/T2/T3/T5 取值落地后。
- 复评范围**冻结**在必改项 B1–B7 与建议项涉及的原文范围，不新增无关要求（评估范围漂移禁止）。
- 本结论为「打回」：**不得报主智能体审批**；修订后由本角色复评。

---

## 8. 事实性纠正（本次评审发现被评审材料的错误）

| # | 被评审材料表述 | 实测/核对结果 | 影响 |
| --- | --- | --- | --- |
| **FC1** | §3 问题 7 / §5 / §7：「`LATE_FINE` 现状按天计一次」「默认 `DAY` = 行为不变」 | 现状按**有效 ON 卡计数**（`PayrollContextProvider.java:94-98`、`hrm-demo/src/shared/mock/attendanceStore.js:893`）；单班制历史数据下才恰好等价「一天一次」，双班制即班次粒度 | **高**：使一个实际口径变更被误判为「行为不变」，未走 §11.4 口径裁定 → 必改项 B1 |
| **FC2** | §9：「`api.md` 出参字段 否」「`api.md` 错误码 否，复用 9405 / 9604」 | `api.md` 一期 24 接口（§4.0，L152-179）与 §7 请假增量（L646-708）**均不含工资单接口**；`9405` 仅存于 `enums/ErrorCode.java:217`，未登记于 `api.md` §2.2（L82-109，止于 5003）；`9604` 在 `api.md` §7.2 L683 有登记 | **中**：工资单契约的真实载体不是 `api.md`（实际端点为 `PayrollController.java:42` `/api/v1/finance/payrolls`，契约应在项目既有架构/接口文档），引用错位 → 必改项 B3 |
| **FC3** | §0.1 C3：「该开关去留见 §3 问题 3/§7」 | §3 问题 3（请假班次）与 §7（参数清单）**均未讨论** `leave_setting.leave_deduct_enabled` 的去留 → 指针悬空 | **中**：决策缺失 + 悬空引用 → 必改项 B6 验收内容 |
| **FC4** | §4：「新 source 只是注册一个解析器，符合 S2 开闭原则」「前端零字段改动」 | `PayrollSource` 枚举与 `PayrollRuleValidator.java:23,77-79` 白名单/文案、`AttendanceStat` 字段、`PayrollContextProvider` 查询字段、`sourceLabel`、演示端字典与 `PayrollRuleEditor.vue` 分支均需同步 | **中**：改动面被低估 → 必改项 B2 |
| **FC5（正向核对，非错误）** | §0.1 C1 / §2.3 D5：劳社部发〔2008〕3号 已被人社部发〔2025〕2号 废止；月工作日 20.67、月计薪天数 21.75 | 与人社部原文逐条吻合（文号、2025-01-01、20.67、21.75、「三、废止文件」） | **无**：引用正确，写作方未以法规替换项目裁定口径（§2.3 末段已声明） |

---

## 9. 遗留与假设（本评审自身的边界声明）

- Java 侧（`BigDecimal` 舍入到分 vs 原型 `toFixed(2)` 末位差异、真实查询耗时与索引命中、账期开关在真实生成链路上的回归）**本次未运行**，均标注「未运行」，收敛到服务器阶段复核（与方案 §11.2 一致）。
- 本报告仅做**工程面技术质量**评估：未做漏洞/攻击面/鉴权与越权判定（归网络安全工程师），未做门禁实跑与单测/E2E（归测试工程师），未做 UI/UX 视觉走查，未对计费/考核口径取值做裁定（归用户）。
- 本次评审**未修改**被评审方案与原型文件、未改 `hrm-server`/`hrm-admin`/`hrm-demo` 源码与迁移脚本，仅新增本报告与 `update-log.md` 一行追加。

---

# 重评（v2.0）

- 复评对象：
  - 方案 `hrm-dev/docs/algo-payroll-shift.md` **v2.0（2026-09-25，修订版）**
  - 原型 `hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（同版）
- 作者：算法工程师 `express-station-algorithm-engineer`；评估方：`express-station-tech-reviewer`（产出方与评估方分离）
- 复评触发：上一轮结论「打回」（必改项 B1–B7）；作者按必改项修订后报重评
- 复评方式：**逐条取证核验（不看修订记录表放行）** + **原型实跑（Node，本机执行）** + 源码/契约静态比对；Java 侧仍**未运行**（本机无 JDK/Maven/MySQL）
- **结论等级：有条件通过**（附必改项 1 条，见第 3 节）
- 准入结论：**可报主智能体审批**——报审时须附本重评结论与第 3 节必改项清单（必改项属「表述/依据」类，作者修订后按条核对，**无须再次重评**）

---

## 1. B1–B7 逐条核验（「已落实 / 部分落实 / 未落实」+ 依据）

| # | 上一轮必改项 | 结论 | 核验依据（文件:行号 / 实跑） |
| - | ------------ | :--: | ---------------------------- |
| **B1** | `LATE_FINE` 现状事实错误 | **已落实** | ① 事实已更正：`§3 问题 7`（L288）、`§0.1 C6`（L50）均改为「按有效 ON 迟到卡逐张计数」；② 「默认 `DAY` = 行为不变」**已删除**——全文检索仅存修订说明与 `PER_DAY` 口径变更表述，无该错误结论；③ 新论证与源码**自洽**：`PayrollContextProvider.java:94-98` 在 `CHECK_TYPE_ON` 分支内对 `STATUS_LATE` 执行 `lateCount++`（每卡一次），`isValidCard = !ABNORMAL`（`AttendanceConstants.java:75-76`），即方案所述「有效 ON 迟到卡按次」；演示端 `attendanceStore.js:893` 同口径；④ 双班差异算例已给（`§2.4.5`，L239-250）：单班 20/20 一致；双班 `PER_CARD` **20** vs `PER_DAY` **10**，差 **−200**（实跑一致）。默认已改 `PER_CARD`（`§7` L362、原型 `SHIFT_CONFIG.lateGranularity`） |
| **B2** | `PRORATED` 改动面漏登 | **已落实** | `§9.2`（L432-447）列全 6 项 + 1 项自动项 + 1 项文档项，逐项标注「代码改动 / 文档改动」与责任角色；**逐文件比对全部核对准确**：`PayrollSource.java:12-21`（枚举）、`PayrollSource.codes()`（`:33-40`，随枚举自动）、`PayrollRuleValidator.java:23,77-79`（`ITEM_SOURCES` 白名单 + `isValid`，不改则 400，已标「必须改」）、`AttendanceStat.java:15-19`(5 字段)、`PayrollCalcContext.java:18`、`PayrollContextProvider.java:76-79,106-110`（未取 `period_index/period_name` 与 `shift_id`）、`PayrollItemVO.java:23`/`PayrollRuleItemVO.java:26` + `PayrollServiceImpl.java:785`/`PayrollRuleServiceImpl.java:232`、演示端 `financeStore.js:31`、`dict.js:257`、`PayrollRuleEditor.vue:47,358`。「前端零字段改动」表述**已撤回**（`§4` L308、`§9.1` L430、`§9.2` 前言） |
| **B3** | `db.md` 取值集合未声明处置 | **已落实** | ① 性质**已查证**并给依据（`§0.1 C8` L52、`§9.4` L460-461）：`V8__payroll.sql:41`/`:97` 均为 `VARCHAR(16) NOT NULL COMMENT '来源：FIXED/ATTENDANCE/KPI/MANUAL'`，**无 CHECK / ENUM**——经本报告实测核对**逐字吻合**；② 「文档枚举 + `VARCHAR(16)` + 列 COMMENT」结论**成立**（写入 `PRORATED` 不被 DB 拒绝，真源为 `PayrollSource` 枚举）；③ 同步改法具体到位置（`§9.4` 表：`db.md` §8.6.2 L812 / §8.6.4 L860，及 DDL 注释属 C 档）；④ 已撤回「与 `api.md`/`db.md` 冲突 无」（`§9.4` 结论），工资单契约载体改为 `PayrollController.java:42` + `server-architecture.md`（C9，`§9.5` L477）——`api.md` 全文仅 `9606` 一处 payroll 引用、`server-architecture.md` 确含工资单 10 接口清单（L559/L695+），**引用更正成立** |
| **B4** | 空 `period_name` 可能克扣 | **已落实** | ① **唯一三态优先级**已定死且**仅一处定义**（`§2.1` L92-98：①哨兵 `全天班`→覆盖当日全部班次；②空/NULL/空白→双班按当日全部班次计出勤 + `PERIOD_NAME_MISSING` 告警、单班归唯一班次、无排班不产生；③其余按 `period_index`）；原型 `attendedShiftSet`（`s2b-payroll-shift.mjs:116-146`）与文档**逐字对应**；② **新增用例实跑**（边界表 ⑨）：空 `period_name` + 双班 → 应出 **60** / 实出 **60** / 旷工 **0** / 折算 **1500** / 实得 **1500** / 告警 **30**，与必改项要求的「不克扣 + 告警」**逐位吻合**；另补单班用例（应出 30 / 实出 30 / 告警 0） |
| **B5** | 缺假设与风险登记 | **已落实** | 新增独立章节 `§2.5`（L252-274）：① 显式假设 **5 条**（A1-A5），每条含「假设不成立时的后果」；② 风险 **5 类**（R1 负实发 / R2 罚款超基本工资 / R3 双班扣款 150→200 客诉 / R4 空排班 `FULL_BASIC` 多发 / R5 历史 `period_name` 空数据质量），每条含**触发条件 / 影响面 / 缓解措施 / 是否待裁定**；③ −6000 与 −360 两个量级值均给出处（−360 引 `AlgoProperties.java:99-103`，核对无误） |
| **B6** | 封顶优先级 + 开关去留 | **已落实** | ① **逐级 min 封顶链**与判定顺序（`§7.1` L365-377）：级1 规则项 `params.cap`（`AttendanceItemResolver.java:55-59`）→ 级2 配置 `absentFineCapRatio` → 级3 合计层 `allowNegativeNet`（`PayrollTotalsPolicy.java:44-46`）；与代码**实际执行顺序一致**（resolver 内封规则项 cap → 合计层钳净额），「逐级取更严、不叠加相减」与原型 `capAbsentFine` 一致；② 明示既有键 `allowNegativeNet=false` 即可表达「实发不低于 0」，**无需新增键**；③ `leave_deduct_enabled` **正面裁定**（`§7.2` L379-388）：班次制新路径 **no-op**、旧按天路径沿用 → **终结 C3/FC3 悬空引用**（`§0.1 C7` L51 已登记） |
| **B7** | 幂等/试算未覆盖 | **已落实** | 新增 `§8.2`（L407-416）N1–N4，每条含**断言 + 依据 + 如何验证**：N1 试算载体=`POST /generate` 造 DRAFT（`PayrollController.java:61`，**核对成立**）；N2 DRAFT/REJECTED 按 `deleteExisting` 覆盖重建、同输入逐位一致（`PayrollServiceImpl.java:171-176`，核对成立）；N3 非 DRAFT/REJECTED → **9405** 整批拒绝、无部分成功（`PayrollServiceImpl.java:154-163` + `ErrorCode.java:217` 9405，核对成立）；N4 与账期锁 `PayrollLockQueryService.java:27` 无交互变更（核对成立） |

**结论：B1–B7 七条全部「已落实」，无「部分落实 / 未落实」。** 建议项 S1–S6 亦逐项采纳并核对到位（S1 `§0.2`/`§2.1` 公式写 `|A∩R|/|R|`；S2 `§9.3` L456 `detail` 单位「次→班次」；S3 `§2.1` L135 跨零点归属；S4 `§9.5` 安全面声明；S5 `§11.2` L509 复合索引建议；S6 `§7` L353 `12:00` 边界备注）。

---

## 2. 本报告实际执行的验证（证据）

### 2.1 原型实跑（本机 Node，两次运行）

```
cd hrm-dev/docs/algo-scripts
node s2b-payroll-shift.mjs
```

| # | 核对项（任务第三节） | 方案声称 | 实跑结果 | 一致 |
| - | -------------------- | -------- | -------- | :--: |
| 1 | 5 个验收数字 | 1500/1475/1375/1450/1250 | **1500/1475/1375/1450/1250**（逐位，`验收全部通过=true`） | ✅ |
| 2 | T3 封顶（整月无打卡） | 实得 0（原 −6000）、`负净额已钳制=true` | 应出 60 / 实出 0 / 旷工 60 / 折算 0 / 罚款 6000 / **实得 0** / **`负净额已钳制=true`** | ✅ |
| 3 | T1 全勤奖（请半天假） | 新 0（旧 200） | **新 0 / 旧 200** | ✅ |
| 4 | B4 不克扣（空 `period_name`+双班） | 实出 60 / 旷工 0 / 实得 1500 | **应出 60 / 实出 60 / 旷工 0 / 折算 1500 / 实得 1500 / 告警 30** | ✅ |
| 5 | B1 迟到差异（双班 10 天×2 班） | `PER_CARD` 20 vs `PER_DAY` 10 | **20 vs 10**（差 −200 元） | ✅ |
| 6 | 批量基准（500 人） | 恒等式违例 0、负净额钳制数 0 | **恒等式违例数 0 / 负净额被钳制数 0**；平均应出 49.87、均值 3708.08、标准差 219.25、最小 2920、最大 4000 | ✅ |
| 7 | 固定种子可复现 | 同命令重跑逐位一致 | **第二次运行逐位一致**（49.87 / 3708.08 / 219.25 / 2920 / 4000 / 0 / 0；验收数字同上） | ✅ |

- 耗时列（本次 113.441 ms / 0.2269 ms；方案记 129.24 ms / 0.2585 ms）**属环境相关，方案已预声明**，不计入不符。
- 其余边界用例（空排班 `FULL_BASIC`、部分缺勤封顶触发、`ABNORMAL` 不计出勤、整月全假、请假与出勤重叠不双扣、跨日单、非法区间、单班制历史不突变）**逐项与 §2.4.4 表一致**。

### 2.2 契约与源码静态核对（补充，未运行 Java）

| 核对点 | 方案声称 | 实测 | 判定 |
| ------ | -------- | ---- | :--: |
| `V8__payroll.sql` source 列 | `VARCHAR(16)` + COMMENT、无 CHECK/ENUM | `:41`/`:97` 均为 `VARCHAR(16) NOT NULL COMMENT '来源：FIXED/ATTENDANCE/KPI/MANUAL'`，无约束 | ✅ |
| `db.md` source 枚举位置 | §8.6.2（L812）/ §8.6.4（L860） | L809 标题 `#### 8.6.2 payroll_rule_item`、L856 标题 `#### 8.6.4 payroll_item`，枚举文本一致 | ✅ |
| `leave_deduct_enabled` 播种值 | 0（C3） | `sql/seed/kdyzgl_test_seed.sql:918-919` = **0**；`V9__leave.sql:77` DEFAULT 0 | ✅ |
| `ABSENT_FINE` 参数 | `amount=150`（按天）、`cap=0`（C4） | `seed:1364` `{"metric":"ABSENT","mode":"PER_COUNT","amount":150,"cap":0}` | ✅ |
| 中班记录数 | 8 条（B5 删中班） | `seed:238/241/.../259` = **8 条**「中班 12:00–20:00」 | ✅ |
| 现状迟到计数口径 | 「按有效 ON 卡计数」 | `PayrollContextProvider.java:94-98` `lateCount++`；`isValidCard=!ABNORMAL`（`AttendanceConstants.java:75-76`） | ✅（B1 论证自洽） |
| 试算/幂等端点 | `PayrollController.java:61` `/generate` 造 DRAFT | `:61` `@PostMapping("/generate")`；`:42` `/api/v1/finance/payrolls` | ✅ |
| 9405 / 9606 | `ErrorCode.java:217` 9405；`api.md` §7.2 9606 | `ErrorCode.java:217` = `FINANCE_PAYROLL_GENERATED(9405)`；`api.md:685` = 9606 | ✅（9606 行号见第 5 节，仅行号偏差） |

---

## 3. 必改项（1 条）

> 判定为「有条件通过」而非「通过」的原因：存在 **1 处 v2.0 自身新引入的内部矛盾**（不影响主干口径与 5 个验收数字，属「表述/依据」类）。

### B8 `§2.4.2` 「现状」基线开关值与 `§0.1 C3` 互相矛盾（v2.0 新引入）

- **问题**：`§2.4.2`（L193）写「取**实测库**开关值 `=1`」，且其 before/after 表（L195-201）与差异要点（L205）按 `leave_deduct_enabled=1` 复刻现状（故「请全天假」现状 1350、差 **+100**）；但同一文档 `§0.1 C3`（L47）已更正为「`sql/seed/kdyzgl_test_seed.sql:918-919` 播种值为 **0**；默认 false」，处置为「以实测库为准、默认 false」。**同一文件对「实测库开关值」给出两个互斥取值**。
- **为什么是问题**：`§2.4.2` 的 before/after 差异清单是测试工程师验收对照的基线；基线值错误会使「请全天假」等情形的差异方向与幅度失真（实际应为现状 1500、差 **−50**，非 +100），与 `§0.1 C3` 已核对的事实（播种值 0）不符，属「以未核实的取值冒充实测」（对照 §4.1）。
- **验收标准**：`§2.4.2` 与 `§0.1 C3` 取值一致，二者之一：① 「现状」列按**实测库值 `=0`** 重算（「请全天假」现状=1500、差 **−50**），并相应更新 L205 差异要点与 `§2.5 R3` 的引用；**或** ② 保留 `=1` 场景但**显式改写为假设场景**（如「假设 `leave_deduct_enabled=1`」），并在同表并列给出 `=0`（实测）列。**禁止**再出现「取实测库开关值 `=1`」这一与 C3 互斥的表述。
- **复核方式**：读 `§2.4.2` 标题/脚注与 `§0.1 C3`，确认二者取值一致；若选 ①，比对「现状(天) 实得」列「请全天假」= 1500、差 = −50；若选 ②，确认「=1」已标注为假设场景且给出 =0 列。**本条为「表述/依据」类，作者修订后按条核对即可，无须再次重评。**

---

## 4. 新引入/未闭环问题与事实性纠正

| # | 类型 | 内容 | 依据 |
| - | ---- | ---- | ---- |
| N-1 | **新引入（内部矛盾）** | `§2.4.2`（L193）「取实测库开关值 `=1`」与 `§0.1 C3`（L47）/`§2.4.2` 脚注（L205）「实测库开关为 0」互斥 → **必改项 B8** | 见第 3 节 |
| FC-a | 事实性纠正（轻微） | `§9.5`（L477）称 9606「`api.md` §7.2 L683 有登记」；实测 9606 在 `api.md:685`（条款存在，**行号偏差 2 行**）。不影响结论，可随 B8 一并更正 | `api.md:685` |
| FC-b | 正向核对（非错误） | `§0.1 C8` 对 `V8__payroll.sql:41,97`「`VARCHAR(16)`、无 CHECK/ENUM」之判断、`§0.1 C3` 对 seed 播种值 0 之判断、`§9.2` 对 6 项改动面文件:行号之列举，**经本报告逐条实测均准确** | 第 2.2 节 |
| 遗留 | 未运行项 | Java 侧 `BigDecimal` `HALF_UP` 到分与原型 `toFixed(2)` 末位差异、真实查询耗时与索引命中、账期开关在真实生成链路的回归、N2/N3 真实链路验证——**本次仍未运行**，收敛到服务器阶段复核（与方案 `§11.2` 一致） | `§11.2` |

---

## 5. 复评结论与版本绑定

- **结论等级：有条件通过。**
- 准入：**可报主智能体审批**；报审时须附本重评结论与必改项 B8 清单（1 条，属「表述/依据」类，作者修订后按条核对，无须再次重评）。
- 版本绑定：本重评结论**仅绑定** `hrm-dev/docs/algo-payroll-shift.md` **v2.0 / 2026-09-25** 与 `hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（同版）。下列任一变更**使本结论失效，须重评**：① 方案正文任一口径/结论修改；② `§7` 参数键默认值修改（含 `lateGranularity`/`fullAttendMetric`/`allowNegativeNet`/`absentFineCapRatio`）；③ 原型口径逻辑或 `SHIFT_CONFIG` 修改；④ 本次「打回」遗留已闭环但**若修订超出 B8 范围**（评估范围冻结，禁止漂移）。
- 本报告的边界：仅做**工程面技术质量**评估——未做漏洞/攻击面/鉴权与越权判定（归网络安全工程师），未做门禁实跑与单测/E2E（归测试工程师），未做 UI/UX 视觉走查，未对计费/考核口径取值做裁定（归用户）；**未修改**被评审方案与原型、未改任何业务源码与迁移脚本，仅追加本重评章节与 `update-log.md` 一行。
