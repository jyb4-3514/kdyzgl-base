# 打卡时段真源统一 · 架构设计方案（班次为唯一时间真源）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.2（技术评审「有条件通过」定稿轮）** |
| 上一版本 | v1.1（修订版，2026-09-27，749 行）；技术评审（**复评**）结论**有条件通过**（报告：`tech-review-attendance-time-source.md`；必改 **N1~N7**，六维残留：防护有效性 / 防护可绕过 / 回滚完整性 / 门禁可判定性 / 穷尽性 / 行号精度 / 契约表述）。再上：v1.0（初稿，483 行，初评**打回**，必改 M1~M7） |
| 修订日期 | 2026-09-27 |
| 评估绑定声明 | 本方案为**方案阶段产物**，评审结论**绑定版本**。v1.1 复评结论 = **有条件通过**（必改 **N1~N7**）；v1.2 为按必改项修订的**定稿轮**，**须对 v1.2 重新评估**确认 N1~N7 闭环（结论「通过 / 有条件通过」方可报主智能体审批；「打回」不得报审）。本版仍**不构成任何 C 档动作授权** |
| 编写日期 | 2026-09-27 |
| 产出角色 | 架构师 `express-station-architect`（**只出方案**：不写业务实现代码、不写 Flyway 脚本正文、不改 `api.md`/`db.md` 正文、不执行 git/部署/MCP） |
| 状态 | **方案阶段产物（v1.2 定稿轮）**。依 `项目规则1.md` §8 第 8 条与调度规则 **P0.6 / R25 / L8**：**报审前须经技术评审工程师（`express-station-tech-reviewer`）评估通过（通过 / 有条件通过）**；v1.1 复评 = 有条件通过，v1.2 须复评确认 N1~N7 闭环。本方案**不构成任何 C 档动作授权**。 |
| 上游口径 | **用户已裁定**（2026-09-27，不得改写）：「打卡时段不再单独配时间，直接取该驿站的班次；班次成为唯一时间真源，排班改哪里、打卡就跟哪里，永远一致。」 |
| 关联文档（真源） | [db.md](db.md) §8.3.1/§8.3.2、[api.md](api.md) §4.6（考勤契约，本文只出「变更清单草案」，正文补录归后端）、[algo-payroll-shift.md](algo-payroll-shift.md) v2.0（班次制计薪口径）、[boss-management-architecture.md](boss-management-architecture.md)（D-6 决策、R10/R11 风险）、[update-log.md](update-log.md)（B7b 口径决议）、[algorithm-multi-shift-scheduling.md](algorithm-multi-shift-scheduling.md)（多班次排班四件套） |
| 本方案边界 | 只产出设计。**不含**业务实现代码、**不含**迁移脚本正文（`V24` 只给「承接内容 + 预检判据 + 回滚路径」）、**未连库、未编译、未运行任何测试**（本机无 JDK/MySQL）；契约以「变更清单草案」给出（见 §7） |

---

## 0.0 v1.2 变更摘要（对复评「有条件通过」的整改）

> 本版**仅修订本方案文档**：不改任何业务代码 / Flyway 脚本 / `api.md`/`db.md` 正文 / 其它文档；未执行 git / 部署 / MCP；未连库、未运行测试（本机无 JDK/MySQL）。
> 评估对象：v1.1；评估方：技术评审工程师 `express-station-tech-reviewer`；结论：**有条件通过**（必改 **N1~N7**，见 §0.0.1）。

**一句话摘要**：按复评「有条件通过」必改意见闭环 **N1~N7** 及评审「自述与实测不符」其余项；核心修订——① **补 M2 残余预检缺口**（新增 **P-6**：查 `attendance_shift.shift_name` = 保留名的**启用**班次；更正 P-4 的「兼作」表述）；② **禁用名校验归一化**（`trim` 后比对，堵前置/尾随空白绕过）；③ **回滚清单补 D5/D6/D7**（升为 **R-1~R-14**，与台账「需改 22 条」逐条可对）；④ **批次前置化**（P-1 预检与治理前移为独立前置批次 **B-1**，消除「B1 依赖 B4 产物」的先后矛盾）；⑤ **Mock 门禁资产登记**（`verify-mock.mjs`，并声明不得改断言迁就实现）；⑥ 行号与错误码精度统一（`ShiftPayrollPolicy.java:75`、`AttendanceScheduleServiceImpl.java:535`、U-5 = 通用 `400`）。**U-1~U-7 裁定不变**（含 **U-7 demo 不同步、mock 同步**）。

**版本差异一览（v1.1 → v1.2）**

| # | 项 | v1.1 | v1.2 |
| - | -- | ---- | ---- |
| 1 | §9.3 预检 P-4 | 标「**兼作** M2 残余预检」（**错**：P-4 只查 `record.period_name` 非哨兵非空，**结构上排除**哨兵名 → 查不到「全天班」命名班次） | **更正 P-4 表述**（删「兼作」，改指向 P-6）；**新增 P-6**：查 `attendance_shift.shift_name ∈ {legacyPeriodSentinel, DEFAULT_PERIOD_NAME}` 的启用班次（含 SQL 与判据） |
| 2 | §6.4/§8.3 禁用名校验 | 未声明归一化（`validateShift` 在 `setShiftName(trim())` **之前**调用 → 空白可绕过） | **明确归一化**：`validateShift` **首查**「`trim` 后 `shift_name` ∈ 保留名集合」，与落库值同源归一；对齐 `ShiftPayrollPolicy` 哨兵分支的**精确 `equals`** |
| 3 | §9.4/§10.5/§11 回滚 | R-1~R-11，**漏**台账需改的 D5/D6/D7 | **R-1~R-14**（补 R-12/R-13/R-14 ↔ D5/D6/D7），附「回滚 ↔ 台账」逐条对应表 |
| 4 | §11 批次序 | 线性序「B1&B2→B3→**B4**」与「B1 前置 = P-1=0（治理属 **B4**）」**矛盾** | **新增前置批次 B-1**（P-1~P-3/P-5/P-6 预检 + 治理）；线性序改为 **B0→B-1→B1(&B2)→B1′→B3→B4(回归)**；B1 入口门禁由 **B-1 出口门禁**提供 |
| 5 | §8.5 影响面清单 | 未登记 Mock 门禁资产 `hrm-clients/scripts/verify-mock.mjs` | **补登记**（第 7 行）+ §13 索引；声明 **B3 须核验其断言、不得改断言迁就实现** |
| 6 | §6.2/§6.3 行号 | `ShiftPayrollPolicy.java:74` / `AttendanceScheduleServiceImpl.java:534`（与实测差 1，自相不一致） | 统一为 **`:75`**（三元判定行，实测）/ **`:535`** |
| 7 | §7.1/§7.7 U-5 错误码 | 仅写「回 `400`」，未明示码 | 明示为**通用 `400`**（`ErrorCode.BAD_REQUEST`，不新增 91xx），对齐 `api.md:797` |
| 8 | §0.2 台账计数 | 「已覆盖 27」与首评「20 处」映射未显式标注 | 显式标注 **27 = 20（A 4 + B 读侧 4 + C 4 + D 逻辑 8，首评口径）+ 7（E 组）** |
| 9 | §4.5 补卡算例 | 「取 `endTime`（`ON` 取 `startTime`）」**未指定 `checkType`**，字面矛盾 | 明确**按 `checkType` 取值**（`ON`→`startTime`；否则→`endTime`） |

---

## 0.0.1 v1.2 对复评 N1~N7 的逐条响应表

> 评估对象：v1.1；评估方：技术评审工程师 `express-station-tech-reviewer`；结论：**有条件通过**。下表逐条闭环（结论均标依据 `文件:行号` 或 `[推导]`）。

| 编号 | 评审问题（引 `文件:行号`） | v1.2 整改结论 | 落位 |
| ---- | ------------------------ | ------------- | ---- |
| **N1**（中·防护有效性，M2 残留） | 方案 §9.3-P-4 标「**兼作** M2 残余预检」，但 P-4 定义为查 `attendance_record.period_name` **非哨兵且非空** → **查不到**「名为 `全天班` 的**班次**」（查询对象相反） | **已更正 + 新增**：① 删去 P-4 的「兼作」表述，改为「仅评估存量 `period_name` 错配（V-1，非阻塞登记）」；② **新增预检 P-6**「查 `attendance_shift.shift_name` = 哨兵 / `DEFAULT_PERIOD_NAME` 的启用班次清单」，给出 **SQL 与判据**；③ §6.4 残余风险与 §10.6-AS-3 的引用由 P-4 改指 **P-6** | §9.3（P-4/P-6）、§6.4、§10.6 |
| **N2**（中·防护可绕过，M2 残留） | 禁用名比对**未归一化**：`validateShift` 在 `setShiftName(...trim())` **之前**调用（`AttendanceShiftServiceImpl.java:71/92` vs `:74/93`），而 `AttendanceSupport.textLen` **内部 trim**（`:41`）→ 前置/尾随空白可绕过 | **已更正为「归一化后比对」**：`validateShift` **首查**（在长度/格式校验之前）取 **`String name = body.getShiftName() == null ? null : body.getShiftName().trim()`**，再判 `name` 是否 ∈ **保留名集合**（`legacyPeriodSentinel` / `DEFAULT_PERIOD_NAME`），命中回 `9114`；**与落库值同源归一**（落库 `setShiftName(trim())`）。**覆盖范围与计薪侧对齐**：`ShiftPayrollPolicy.java:168` 用**精确 `equals`** → 故本校验**仅 trim、不做全角折叠/大小写折叠**（若做折叠会与计薪侧判定不一致，产生「放行但计薪不认」的偏差）；实现须保证「校验用的归一化值 == 落库值」 | §6.4、§8.3 |
| **N3**（中·回滚完整性，M5 残留） | R-1~R-11 未覆盖台账需改的 **D5/D6/D7**（`AttendanceRuleServiceImpl.java:201-237` 时段校验 / `:296-320` 归一化 / `:374-379` `applyPayload` 时段写入）；§9.4 自述「与 B1 改动面一一对应」**不完全成立** | **已补 R-12/R-13/R-14**（分别 ↔ D5/D6/D7）；回滚清单升为 **R-1~R-14**；新增「**回滚 ↔ 台账 逐条对应表**」，证明覆盖台账「需改 **22 条**」（A1~A4 + B1~B5 + C1~C4 + D1~D8 + F2）无遗漏；§11-B1 改动面同步列出 D5/D6/D7 | §9.4、§10.5、§11 |
| **N4**（中·门禁可判定性，M6′ 残留） | §11 头部线性序「B1&B2 → B3 → **B4**」与「B1 前置 = **P-1=0**（而 P-1 治理属 **B4**）」**先后矛盾**（B1 需 B4 产物却排在 B4 之前） | **已前移为独立前置批次 B-1**：B-1 承接 **P-1~P-3/P-5/P-6 预检 + 治理**（补配启用班次 / 删减 >2 / 调整 `ordinal` 冲突 / 保留名改名）；**B-1 出口门禁 = B1 入口门禁**（**P-1=0 阻塞**；**P-2/P-3/P-6=0 阻塞**；P-4/P-5 报告并确认，**登记式非阻塞**）；线性序改为 **B0 → B-1 → B1(&B2) → B1′ → B3 → B4(回归)**；REL-2 由「B1 前置」改述为「**B-1 出口门禁**」（语义等价、先后自洽）。**取舍理由**：治理须先于后端上线才可判定（否则上线即「无班次站点全员禁打卡」）；若改为「非阻塞登记式」则违反 REL-2 上线前置，故选**前移批次**而非放宽门禁 | §11 |
| **N5**（低·穷尽性，M7 残留） | Mock 门禁资产 `hrm-clients/scripts/verify-mock.mjs` **未登记**（`api.md:999` 明确列为 Mock 侧，其内含以 `checkPeriods/checkFrequency/periodIndex` 旧语义编码的断言，如 `verify-mock.mjs:1934-1941,2106-2109`） | **已补登记**：§8.5 影响面增列 `verify-mock.mjs`（标注「Mock 同步时需适配」）；§13 依据索引新增条目；声明 **B3 须同步核验其断言，不得以改断言迁就实现**（违验收资产纪律：调度规则 **A06**）；确因契约变更需调整的，须**逐条说明理由并登记**（评审 N5 验收原话） | §8.5、§11-B3、§13 |
| **N6**（低·行号精度） | 两处引用行号与实测**差 1** 且**同文档内自相不一致**：§6.3 `ShiftPayrollPolicy.java:75`、§6.2 `AttendanceScheduleServiceImpl.java:534` | **已按实测校正并统一**（**注：与复评/实施指令所述方向相反，按「求证优先」取实测值**）：① 实测三元判定行 = **`ShiftPayrollPolicy.java:75`**（`return hour * 60 + minute < middayBoundaryMinute ? 0 : 1;`）→ **§6.2 的 `:74` 系笔误，改为 `:75`**（§6.3 原 `:75` **保持**）；② `AttendanceScheduleServiceImpl.java:534` → **`:535`**（内联 `minutes < boundary ? 0 : 1` 实测在 `:535`，方法体 `:527-541`）。**证据**：`ShiftPayrollPolicy.java` 内 `? 0 : 1` 仅 `:75` 一处（`:74` 实为 `int minute = Integer.parseInt(parts[1].trim());`）；`AttendanceScheduleServiceImpl.java:535` 为另一处内联（`:534` 实为异常抛出）。复评 N6 所述「`ShiftPayrollPolicy` 实测为 `:74`」与源码不符，本版以源码为准 | §6.2、§6.3 |
| **N7**（低·契约表述） | U-5 拒绝的**错误码未明示**：§7.1 只写「回 `400`」，未说明是否复用既有无业务码 `400` | **已明示为通用 `400`**（`ErrorCode.BAD_REQUEST`，`ErrorCode.java:20`，非业务码 91xx），**不新增 91xx**；与 `api.md:797`「字段校验类错误统一 `400`」一致；§7.7 补一句说明「U-5 拒绝**不**使用 `9107`/`9113`/`9114`」；§7.8 变更计数**不变**（不新增错误码） | §7.1、§7.7、§7.8 |

---

## 0.0.2 v1.1 变更摘要（对技术评审「打回」的整改）

> 本版**仅修订本方案文档**：不改任何业务代码 / Flyway 脚本 / `api.md`/`db.md` 正文 / 其它文档；未执行 git / 部署 / MCP；未连库、未运行测试（本机无 JDK/MySQL）。

**一句话摘要**：按评审打回意见闭环 **M1~M7** 及评审 §七「自述与实测不符」其余项；三处核心修订——① **更正 M1 取值方式错误**（`periods.get(periodIndex)` 下标取值）并立「按 `ordinal` 按值查找」硬性规则；② **补 M2 哨兵名冲突防护**（班次定义侧禁用保留名 `全天班`）；③ **重建唯一读取点台账**（M7：把「仅 3 处」更正为完整台账，含计薪侧与测试读取点）。同步把 U-1~U-7 全部落为**已裁定**并与主代理裁定逐条对齐（**U-7 demo 不同步**）。

**版本差异一览（v1.0 → v1.1）**

| # | 项 | v1.0 | v1.1 |
| - | -- | ---- | ---- |
| 1 | §4.2 取值方式 | 「前端/补卡均**以值为键**」（**错误**，见 §七-1） | 「**按 `ordinal` 按值查找，禁止 `List.get(periodIndex)`**」+ 硬性改造要求 + 三路径算例 |
| 2 | §6 哨兵名冲突 | 未提（评审高 H-2） | 新增 §6.4 防护：班次定义侧禁用保留名 `全天班`（= `legacyPeriodSentinel` / `DEFAULT_PERIOD_NAME`） |
| 3 | §0.1 读取点 | 「（`resolve(rule)` 生产者）**仅 3 处**」 | 完整台账（覆盖 27 + 遗漏 2 + 不适用 3 类；按字段×消费点登记 34 条） |
| 4 | §9.4/§10.5 回滚 | 「反向 diff **3 读取点** + 解析器 + `saveRule`」 | 补 `toVO` / `defaultShift` / `newRuleDefaults`，与 §11 B1 改动面**一一对应**（11 项） |
| 5 | §7 契约 | 路径 `/api/v1/attendance/shifts`、计数「**新增 1**」 | 路径 `/api/v1/shifts`；计数**新增 4 / 变更 6 组 / 复用 2**，附字段级清单 |
| 6 | §12 U-1~U-7 | 待裁定 + 架构建议 | **全部已裁定**并与主代理裁定逐条一致（U-7 = demo 不同步、mock 同步） |
| 7 | §6.2 R11 | 「天然消除**当且仅当** ordinal 互异」 | 精确表述：R11 **由构造消除**（充分，与 ordinal 互异无关）；ordinal 互异是**避免 R10 型冲突的必要条件**（另一风险域） |
| 8 | §0 摘要第 5 条 | 「存量读取**零变化**」 | 加限定「**仅哨兵 / 空名两类路径**」 |
| 9 | §0 摘要契约条 / §7.6 | 计数「新增 1」、「POST/PUT makeup*」 | 计数与 `api.md` 对齐；方法均 **POST** |
| 10 | NFR / 假设 / 遗留 | 无降级链、无显式假设、无 `TODO(扩展)` | 补**可用性降级链 + 可观测性声明**（§10.7）、**显式假设清单**（§10.6）、残余风险定级、`TODO(扩展)` 标注 |

---

## 0.0.3 v1.1 对技术评审 M1~M7 的逐条响应表

> 评估对象：v1.0；评估方：技术评审工程师 `express-station-tech-reviewer`；结论：**打回**。下表逐条闭环。

| 编号 | 评审问题（引 `文件:行号`） | v1.1 整改结论 | 落位 |
| ---- | ------------------------ | ------------- | ---- |
| **M1**（高·正确性） | `AttendanceRecordServiceImpl.java:320`、`AttendanceMakeupServiceImpl.java:123`、`:211-212` **均为 `periods.get(periodIndex)` 下标取值**；§4.2「均以值为键」**自述与实测不符**；方案自举的「单班次晚班站点」越界 → **9107**（打卡与补卡同时失效） | **已更正并立硬性规则**：删除错误断言；规定「`periodIndex` → 时段**按 `ordinal` 语义按值查找**，**禁止 `List.get(periodIndex)`**」；给出一致查找规则（含 `ordinal` 缺失兜底与 9107 提示）；登记 3 处调用点（打卡 1 + 补卡 2）与测试适配；给单班次晚班站点打卡 / 补卡 / status **三路径算例**；重新论证该站点**可达且正确** | §4.2、§4.5、§11 B1、§0.2-A |
| **M2**（高·正确性） | `AlgoProperties.java:123`（哨兵=「全天班」）、`ShiftPayrollPolicy.java:168-171`（命中 → 覆盖当日**全部**班次）、`AttendanceShiftServiceImpl.java:157-159`（班次名不禁止「全天班」） | **已选防护 = ①班次定义侧禁用语校验**：`shift_name` 不得等于 `legacyPeriodSentinel`（默认「全天班」）或 `AttendanceConstants.DEFAULT_PERIOD_NAME`；落点 `ShiftManager.vue` + 服务端 `validateShift`；三态逻辑**保持不动**（历史兼容）；给「班次名=`全天班`」反例算例与验收判据 | §6.4、§8.3、§7.7（错误码 `9114`） |
| **M3**（不一致） | §12.1 U-7 建议「同步 demo」；与主代理已裁定「`hrm-demo` **不同步**（登记）」「mock 同步」**相反** | **已改为与裁定一致**：U-7 = **demo 不同步（仅登记）、mock 同步**；§8.5 第 5/6 行与 §10.4 RK-3 同步修订 | §8.5、§10.4、§12.1 |
| **M4**（契约/路径） | §7.8 路径 `/api/v1/attendance/shifts`（实测 `/api/v1/shifts`，`ShiftController.java:28`）；§7.8「新增 1」漏 `checkPeriodsReadonly`/`shiftConfigured`；§7.6「`POST/PUT .../makeup*`」（实测均 **POST**，`api.md:1034,1049`） | **已更正**：路径统一 `/api/v1/shifts`；方法均 **POST**；**重算计数 = 新增 4 / 变更 6 组（字段级 11 项）/ 复用 2**，附完整字段级清单 | §7.1–§7.8 |
| **M5**（回滚完整性） | §9.4/§10.5「反向 diff 3 读取点」漏 `toVO`(`:143,146-147`)、`defaultShift`(`:140-152`)、`newRuleDefaults`(`:386-431`) | **已补全回滚清单（11 项）**，与 §11 B1 改动面**一一对应** | §9.4、§10.5 |
| **M6**（表述不严谨；评审发现项 M-5） | §6.2「R11 天然消除**当且仅当** ordinal 互异」表述不严谨 | **已改为精确表述**：R11 **由构造充分消除**（与 ordinal 是否互异**无关**）；`ordinal` 互异是**避免 R10 型取值冲突的必要条件**（另一风险域）；残余风险**定级：中高**（直至班次侧校验落地） | §6.2、§6.3 |
| **M7**（穷尽性，本次核心） | §0.1「读取点全集**仅 3 处**」不完整；遗漏 `PayrollContextProvider.java:92,121-122`（计薪侧）与 `AttendancePeriodResolverTest.java:44-57`（测试适配） | **已重建唯一台账**：覆盖 27 处（`resolve` 调用者 4 + 派生值 4 + `check_frequency` 4 + `check_periods` 8 + 其它消费点 7）+ 遗漏 2 处 + 不适用 3 类；漏①确认「**透传无需改，登记**」；漏②标「**B1 需适配**」 | §0.2、§13 |
| **M6′**（评审原 M6·发布/门禁） | 现网 web `RuleCard.vue:266`、boss-h5 `attendanceRule.vue:155` 仍提交 `checkPeriods`；B4（P-1 治理）排在 B1 之后 | **已新增发布约束**：B1 与 B2 **同批发布**（或过渡期取 U-5 兼容分支）；**B1 上线前置 = P-1 预检为 0**（**v1.2 N4 已改述为「B-1 出口门禁 = P-1=0」，见 §11**）；B2 前置含 U-3/U-4（已裁定） | §11 |

> 说明：用户任务书把「§6.2 表述不严谨」列为 M6；评审原文的必改 **M6 实为「发布/门禁」**。二者均属必改，故本表同时登记（**M6** 与 **M6′**），以免遗漏评审放行前置第 6 条。

---

## 0. 摘要（结论先行）

1. **真源方案选定 (a) 读时派生**：`attendance_rule.check_periods` **不再作为判定真源**（列保留、标废弃），打卡时段在**读取时由该驿站启用班次实时生成**。理由一句话：**（b）写入同步仍留副本与双写窗口，且无法覆盖「直接改班次 / 导入 / seed」等旁路；（a）是唯一能保证「排班改哪、打卡跟哪、永远一致」的方案**（详见 §2）。
2. **时段粒度**：派生源 = **该驿站启用班次（`status=1` 且未软删）按 `start_time` 升序**（**站点级**）。选此粒度而非「员工当日排班班次」的理由：`GET /attendance/rule` 无员工上下文、`GET /attendance/status.periods` 现为站点级语义、且改动面最小；**已裁定 U-1 = 站点级**（§12.1），员工级为 `TODO(扩展)`（不在本次范围）。
3. **打卡次数**：不再由用户配置，**由启用班次数派生**——1 班次 → 2 次（上下班各一次），2 班次 → 4 次；`check_frequency` 字段保留但**只读派生**（= 启用班次数 × 2）。边界见 §3。
4. **`period_index` 口径定稿 = `shiftOrdinal(start_time, middayBoundaryMinute)`（早=0 / 晚=1）**，**不是**数组下标。这是消除 R11 的关键：单班次晚班站点的唯一时段序号必须是 `1` 而非 `0`，否则记录 `period_index` 与计薪班次单元错位（§4、§6）。
5. **`period_name` 改取 `attendance_shift.shift_name` 快照**；**存量记录不改写**，读取侧继续走既有三态优先级（哨兵 `全天班` / 空名 / `period_index`）。**存量读取「零变化」仅限「哨兵名或空名」两类路径**（`ShiftPayrollPolicy.java:167-181`）；非哨兵非空的存量记录在纯晚班排班日可能经三态③错配 → 待核实 V-1 + P-4 预检（§4.3/§10.1）。**新增 M2 防护**：班次名禁用保留名 `全天班`，防新记录被误判哨兵（§6.4）。
6. **R11 由构造天然消除**（真源统一后 `period_index` 直接由班次 `ordinal` 计算，**与 `ordinal` 是否互异无关**）；`ordinal` 互异是**避免 R10 型取值冲突的必要条件**（另一风险域）。残余 R10 已由排班侧 `9112` 收口，但**班次定义侧**尚无该约束（**残余风险定级：中高**），需新增校验（§6）。
7. **契约变更（M4 重算）**：**新增 4**（错误码 `9113` + `9114`；出参 `checkPeriodsReadonly` + `shiftConfigured`）+ **变更 6 组**（字段级 11 项）+ **复用 2**（`/api/v1/shifts` CRUD、`GET /status.shift`；**不新增任何班次接口**）。清单见 §7。
8. **前端改动面 4 处页面**（PC `RuleCard.vue`、PC `ShiftManager.vue`、boss-h5 `attendanceRule.vue`、staff-h5 `attendance.vue` + `PeriodCard.vue`），详见 §8。
9. **迁移**：方案 (a) 下**无结构变更、`V24` 非功能必需**。若主智能体要求「显式废弃列注释」或「一次性数据治理」，`V24` 承接内容与预检判据见 §9；PG 侧目录冻结不动。
10. **口径 U-1~U-7 已全部裁定并落位**（§12.1，不再标「待裁定」）；**待核实 3 项**见 §12.2。其中 U-1（背景第 5 条错配登记出处）已复核订正，见 §0.1。

### 0.1 背景复核（用户给定事实逐条核对）

> 用户已声明「已核实，可直接采信；关键处请自行复核」。下表为**实际复核结论**，含 4 处订正/补充。

| # | 用户给定 | 复核结论 | 证据 |
| - | -------- | -------- | ---- |
| 1 | `attendance_shift` 字段：`station_id/shift_name/start_time/end_time/color/rest_minutes/status` | **正确** | `AttendanceShift.java:19-53`；`db.md:635` |
| 2 | `attendance_rule.check_periods` 为 JSON 列，内嵌 `CheckPeriod{name,startTime,endTime}` | **正确** | `AttendanceRule.java:66-68`；`CheckPeriod.java:13-23`；`db.md:621` |
| 3 | 二者无外键、无一致性校验、无派生关系 | **正确** | `V5__attendance.sql:22-65`（两表独立）；`AttendanceRuleServiceImpl.java` 全文无班次引用 |
| 4 | `period_index` 被当作班次 `ordinal`（`:183-186`），`ordinal` 由 `start_time` 与 `middayBoundaryMinute` 派生（`:64-79`） | **正确** | `ShiftPayrollPolicy.java:183-186`、`:64-79`；界值默认 `720`（`AlgoProperties.java:121`） |
| 5 | 该风险登记为 `docs/update-log.md:42` 的 **R11** | **订正**：`update-log.md:40-53` 为 **B7b 任务记录**，未见该错配登记；**R11 的定义在** `boss-management-architecture.md:457`，`update-log.md:42` 处仅为 B7b 引言 | `boss-management-architecture.md:456-457`；`update-log.md:40-53` |
| 6 | 打卡写入 `:316-393`：有 `periodIndex` 走 rule 时段；无则走班次时间；**但 `periodName` 恒取 rule 时段名** | **正确** | `AttendanceRecordServiceImpl.java:316-345`（时间基准二选一）、`:385-386`（`recordPeriodName` 恒取 rule 时段名） |
| 7 | 汇总 `AttendanceSummaryPolicy.java:62-129`：应到用班次时间、实到用 rule 的 `periodIndex` | **正确** | `AttendanceSummaryPolicy.java:62-129`；`ShiftPayrollPolicy.java:152-189` |
| 8 | D-6（不给班次补时段字段，避免双真源） | **正确** | `boss-management-architecture.md:104`；另 `db.md:1687` 已将「是否补时段字段」列为待确认（Q-DB-11），留给 `V24+` |
| 9 | 维护入口：班次 `ShiftManager.vue`；规则 PC `RuleCard.vue` + boss-h5 `attendanceRule.vue` | **正确，但补 2 点**：① **demo 工程有同名镜像**（`hrm-demo/src/pc/.../ShiftManager.vue`、`RuleCard.vue`、`hrm-demo/src/mobile/.../attendanceRule.vue`）；② **Mock 侧（`hrm-clients/packages/mock`）为独立真源**，与真实后端口径已有分叉先例（`api.md:997-1006`） | Glob 命中 2 组路径 |
| 10 | 「补卡」读取点 | **补充**：用户未列，但**补卡是第三读取点**——`AttendanceMakeupServiceImpl.java:121`、`:208` 亦调用 `AttendancePeriodResolver.resolve(rule)`，申请与审批两条路径都要改 | `AttendanceMakeupServiceImpl.java:121,208,225,231-232` |

### 0.2 读取点台账（唯一一份；M7 重建）

> v1.0 §0.1 原文「读取点全集（`resolve(rule)` 的生产者）**仅 3 处**」**表述误导**：该结论**仅在「`resolve(rule)` 调用者」这一狭义口径下成立**，不等于**影响面**。本版按评审核对口径重建**唯一台账**：**已覆盖 27 处**（`resolve` 调用者 4 + 派生值读取 4 + `check_frequency` 4 + `check_periods` 8 + `period_index/period_name` 其它消费点 7）/ **遗漏 2 处** / **不适用 3 类**；本台账另增列 1 处写侧重算点（`saveRule`）与 4 条字段/类型处理器登记，**按「字段 × 消费点」登记合计 34 条**。同一行号因字段不同可跨组出现（如 `AttendanceRuleServiceImpl.java:96` 同校 `checkPeriods` 与 `checkFrequency`），**非重复计数**。
> **【N5/§二残留·映射显式标注】** **首评口径**「已覆盖 **20 处** / 遗漏 2 处 / 不适用 3 类」= 本台账 **A 4 + B 读侧 4 + C 4 + D 逻辑 8 = 20**（首评**未计 E 组**）；本台账「已覆盖 **27**」= 首评 20 **+ E 组 7** → **27 = 20 + 7**。两口径**不冲突**，差额全在 E 组（`period_index/period_name` 其它消费点）。
> **澄清**：`resolve(rule)` 调用者严格说是 **4 处调用 / 3 个逻辑点**（补卡的「申请」与「审批」2 处调用同属「补卡」1 个逻辑点）。

**A. `AttendancePeriodResolver.resolve(rule)` 调用者（4 处调用 / 3 逻辑点）**

| # | 位置（文件:行号） | 读取 | 是否需改 | 依据 / 整改 |
| - | ---------------- | ---- | -------- | ----------- |
| A1 | `AttendanceRecordServiceImpl.java:316`（`checkIn`） | 时段列表 | **需改** | 解析来源改班次派生；`:320` 下标取值 → **按值查找**（M1） |
| A2 | `AttendanceRecordServiceImpl.java:447`（`todayStatus`） | 时段列表 | **需改** | 来源改班次派生；`periodIndex=ordinal`（§4） |
| A3 | `AttendanceMakeupServiceImpl.java:121`（`apply`） | 时段列表 | **需改** | 来源改班次派生；`:123` 下标 → **按值**（M1） |
| A4 | `AttendanceMakeupServiceImpl.java:208`（`canWriteRecord`） | 时段列表 | **需改** | 来源改班次派生；`:211-212` 下标 → **按值**（M1） |

**B. `work_start_time` / `work_end_time` 派生值读取点（读取 4 处 + 写侧 1）**

| # | 位置 | 读取 | 是否需改 | 依据 / 整改 |
| - | ---- | ---- | -------- | ----------- |
| B1 | `AttendancePeriodResolver.java:68-69` | 兜底段起止 | **需改** | 随解析器改造（兜底段改由班次派生） |
| B2 | `AttendanceRecordServiceImpl.java:344-345` | 单班次模型回退基准 | **需改** | 回退基准改班次派生；无启用班次 → `9113` |
| B3 | `AttendanceRuleServiceImpl.java:146-147`（`toVO`） | `GET /rule` 出参 | **需改** | 改为实时由班次派生（§5）；`toVO` 同被 `listRules` 复用 → 与 §7.3 的 N+1 属**同一性能面**（评审 L-3） |
| B4 | `AttendanceShiftServiceImpl.java:146-147`（`defaultShift`） | 兜底班次起止 | **需改** | 改为「该驿站首个启用班次」派生；无启用班次返回 `null` |
| B5 | `AttendanceRuleServiceImpl.java:100-103`（`saveRule` **写侧重算**） | 写入派生值 | **需改** | **删除**按 `check_periods` 重算的写逻辑 |

**C. `check_frequency` 读取点（4 处）**

| # | 位置 | 是否需改 | 依据 / 整改 |
| - | ---- | -------- | ----------- |
| C1 | `AttendanceRecordServiceImpl.java:469-472`（`todayStatus` 出参 + `requireSummary`） | **需改** | 改派生（启用班次数 × 2） |
| C2 | `AttendanceRuleServiceImpl.java:142`（`toVO`） | **需改** | 改派生 |
| C3 | `AttendanceRuleServiceImpl.java:421`（`newRuleDefaults`） | **需改** | 新驿站改「造默认班次」或提示先配班次（§10.3） |
| C4 | `AttendanceRuleServiceImpl.java:96,201-237,296-320,374-379`（`saveRule` 校验与写入） | **需改** | 去除频次写；入参只读（§7.1） |

**D. `check_periods` 写 / 读 / 校验（8 处逻辑点 + 4 条登记）**

| # | 位置 | 是否需改 | 依据 / 整改 |
| - | ---- | -------- | ----------- |
| D1 | `AttendancePeriodResolver.java:59`（读 `getCheckPeriods()`） | **需改** | 解析器改造（不再读该列判定） |
| D2 | `AttendanceRuleServiceImpl.java:96`（归一化后二次校验） | **需改** | 去除时段校验（U-5 拒绝入参） |
| D3 | `AttendanceRuleServiceImpl.java:100-102`（首末重算） | **需改** | 删除（见 B5） |
| D4 | `AttendanceRuleServiceImpl.java:143`（`toVO` 出参） | **需改** | 值改派生（§7.2） |
| D5 | `AttendanceRuleServiceImpl.java:201-237` | **需改** | 时段校验/归一化去除 |
| D6 | `AttendanceRuleServiceImpl.java:296-320` | **需改** | 同上 |
| D7 | `AttendanceRuleServiceImpl.java:374-379` | **需改** | 同上 |
| D8 | `AttendanceRuleServiceImpl.java:386-431`（`newRuleDefaults`） | **需改** | 默认时段改默认班次（见 C3） |
| D9 | `AttendanceRule.java:66-68`（实体字段） | 不需改 | 列保留（D-2） |
| D10 | `AttendanceRuleRequest.java:33` | 不需改 | 字段保留以兼容旧客户端（值被忽略/拒绝） |
| D11 | `AttendanceRuleVO.java:34` | 不需改 | 字段保留（值改派生） |
| D12 | `CheckPeriodListTypeHandler` | 不需改 | TypeHandler 保留 |

> D9~D12 为「字段 / 类型处理器」登记（非逻辑消费点），列此以证**穷尽**。

**E. `period_index` / `period_name` 其它消费点（7 处）**

| # | 位置 | 是否需改 | 依据 / 说明 |
| - | ---- | -------- | ----------- |
| E1 | `AttendanceRecordServiceImpl.java:126`（`export`） | 不需改 | 读值输出，值语义随写入侧改善 |
| E2 | `AttendanceRecordServiceImpl.java:178`（`summary`） | 不需改 | 同上 |
| E3 | `AttendanceRecordServiceImpl.java:655`（`buildDetailRow`） | 不需改 | 取代表卡快照，保持快照语义 |
| E4 | `AttendanceRecordServiceImpl.java:723-724`（`toVO`） | 不需改 | 同上 |
| E5 | `AttendanceRecordServiceImpl.java:581`（`validCard`） | 不需改 | **值比较**（非下标），不受「非连续」影响（M1 澄清） |
| E6 | `AttendanceSummaryPolicy.java:87-88`（`summarizeByShift` → `RecordSlot`） | 不需改 | 透传至 `ShiftPayrollPolicy` |
| E7 | `ShiftPayrollPolicy.java:168-186`（`attendedShiftSet` 三态） | 不需改 | 三态优先级保留（历史兼容）；**受 M2 影响**：哨兵分支依赖写入侧不再产生哨兵名 |

**F. 评审指出的遗漏项（2 处，登记类）**

| # | 位置 | 判定 | 说明 |
| - | ---- | ---- | ---- |
| F1 | `PayrollContextProvider.java:92,121-122` | **不需改（透传）** | 计薪侧读取 `period_index/period_name` 构造 `ShiftPayrollPolicy.RecordRow`，是三态映射与 R11/R10 风险的**真正消费方**；**透传无需改代码，语义随写入侧改善**。评审判断**确认成立**并登记 |
| F2 | `AttendancePeriodResolverTest.java:44-57` | **需改（B1 需适配）** | 构造无 `checkPeriods` 的 rule 调 `resolve(rule)`；解析器改造后测试须适配（断言来源改班次派生） |

**G. 不适用（3 类）**

| 类别 | 说明 |
| ---- | ---- |
| `hrm-clients/packages/mock` | 前端 Mock 为**独立真源**（`api.md:996-1007` 已登记分叉先例），非后端消费点 |
| `hrm-demo` 镜像 | **U-7 已裁定不同步**（登记） |
| 其它工程 / 文档正文 | 非消费点 |

> **台账统计（最终）**：消费点**合计 34 条**（A 4 + B 5 + C 4 + D 12 + E 7 + F 2）；其中 **需改 22 条**（A1~A4、B1~B5、C1~C4、D1~D8、F2）、**不需改 12 条**（D9~D12 登记 4 + E1~E7 + F1 计薪透传）。映射评审口径：**已覆盖 27 + 遗漏 2 + 不适用 3 类**；其中 **已覆盖 27 = 首评 20（A 4 + B 读侧 4 + C 4 + D 逻辑 8）+ E 组 7**（见本节首段 N5 映射说明）。本台账另增列写侧重算点 B5 与字段/处理器登记 D9~D12。
> `shiftOrdinal` 读取点另有 `AttendanceSummaryPolicy`（经 `ShiftPayrollPolicy`，E6/E7）与排班校验 `AttendanceScheduleServiceImpl.java:526-540`（**第二处 `ordinal` 实现**，见 §6.3），记录侧继续按 `period_index` 定位班次单元，**语义无需变更**。

---

## 1. 口径基线（用户已裁定，不得改写）

| # | 口径 | 原文 / 裁定 | 状态 |
| - | ---- | ---------- | ---- |
| U0 | 唯一时间真源 | 排班班次（`attendance_shift`）为打卡时间的**唯一真源** | **已裁定** |
| U0-1 | 打卡时段不单独配时间 | 打卡规则**不再单独配置时段与时间** | **已裁定** |
| U0-2 | 与排班一致 | 「排班改哪里、打卡就跟哪里，永远一致」 | **已裁定** |
| U0-3 | 存量事实不改写 | 已写入的 `period_index/period_name` 属历史事实，**不回改**（承接 `algo-payroll-shift.md:36`「历史不突变」） | **已裁定（承接既有）** |

---

## 2. 问题 1：真源与派生关系（决策 D-1）

### 2.1 三方案比较

| 维度 | (a) **读时派生**（选定） | (b) 写入时同步 | (c) 其它（如「规则只存时长偏移」） |
| ---- | ---------------------- | ------------- | ------------------------------- |
| 一致性强度 | **最强**：单真源，无副本，无窗口 | 弱：`check_periods` 仍是副本 | 中：引入第三种中间表示，更绕 |
| 双写不一致窗口 | **无** | **有**：班次保存成功但规则同步失败 / 并发 / **直接改班次（`PUT /shifts`）不经规则 Service** / 导入与 seed 旁路 | 有（需维护偏移与班次的换算） |
| 实现复杂度 | 中：改 **`resolve` 调用者 4 处（3 逻辑点）** + 解析器/派生/取值改造（集中，见 §0.2） | 中高：除保存点外还需**兜底补同步**（旁路无所不包） | 高 |
| 历史数据兼容 | **好**：列保留、历史不改写、**哨兵/空名路径**读取零变化 | 中：需一次性把存量 `check_periods` 按班次重写（不可逆） | 差 |
| 回滚难度 | **低**：列未删，回滚 = 恢复 `resolve(rule)` 签名 | 中：已重写的 `check_periods` 无法还原管理员原配置 | 中 |
| 与 D-6 原则一致性 | **一致**（消灭第二份时间数据） | **冲突**：D-6 是「不给班次补时段字段」，(b) 等于「给时段补班次时间」，同构双真源、方向相反 | — |

### 2.2 决策 D-1（ADR 内联）

| # | 决策 | 理由（依据） | 备选（否） |
| - | ---- | ------------ | ---------- |
| **D-1** | **选 (a) 读时派生**：`check_periods` 不再入库/不再参与判定；派生入口收敛为一个新解析方法（如「按驿站启用班次解析时段」），**`resolve` 调用者 4 处（3 逻辑点）** 全部改走它（并同步按值查找改造，§4.4） | ① 唯一真源、无窗口（§2.1）；② 覆盖全部旁路；③ 回滚成本最低；④ 与 D-6 同向 | (b) 写入同步（双真源 + 旁路漏洞 + 不可逆）；(c) 中间表示（过度设计） |
| **D-2** | `attendance_rule.check_periods` **列保留、语义标废弃**（不再写入、不再读取判定）；`work_start_time/work_end_time` 列保留、**改为读时派生**（§5） | 避免破坏性 DDL（删列不可逆）；契约字段可继续出参（值改派生） | 删列（`V24` 破坏性，不选） |
| **D-3** | 派生粒度 = **该驿站启用班次（`status=1` 且未软删）按 `start_time` 升序**（**站点级**） | ① 贴合用户原话「该驿站的班次」；② 与 `status.periods` 现语义一致、改动最小；③ 一个解析方法覆盖全部读取点 | **员工当日排班班次**（**已裁定 U-1 = 站点级**；员工级 `TODO(扩展)`，不在本次范围） |
| **D-4** | `period_index` 取值 = **`shiftOrdinal(start_time, middayBoundaryMinute)`（早 0 / 晚 1）**，非数组下标 | 与计薪班次单元 `epochDay×2+ordinal` 同构，直接消除 R11/R10 残余（§4、§6） | 数组下标 `0..n-1`（单班次晚班站点会错位） |

---

## 3. 问题 2：打卡次数（2 次 / 4 次）与「上下班卡」口径

### 3.1 规则定稿

| 项 | 口径 |
| -- | ---- |
| 打卡次数来源 | **由启用班次数派生**（不再由 `PUT /rule` 配置）：`次数 = 启用班次数 × 2` |
| 1 个启用班次 | 2 次（该班次 1 张上班卡 + 1 张下班卡） |
| 2 个启用班次 | 4 次（早班上下班 + 晚班上下班），正是用户实测期望（早班 08:00-16:00 / 晚班 16:00-24:00） |
| 「上下班卡」口径 | **不变**：每个班次各 1 张 `ON` + 1 张 `OFF`；`check_type` 语义、去重键、早退/迟到判定链**均不变** |

### 3.2 边界（0 班次 / >2 班次 / 停用）

| 边界 | 处置 | 依据 / 理由 |
| ---- | ---- | ----------- |
| **0 个启用班次** | **打卡拒绝**，回**新错误码 `9113`「该驿站未配置启用班次，无法打卡」**；`GET /rule`、`GET /status` 对应派生字段返回空数组 / `null` 并在前端给引导 | 无时间基准则无法判定；此前规则可独立存在，是**新边界**（§10） |
| **停用班次（`status=0`）** | **不计入**派生集合；已有停用班次的站点若全停用 → 等价 0 班次 | 与 `checkIn` 现逻辑一致（`AttendanceRecordServiceImpl.java:338` 要求 `status==1`）；注意 `AttendanceShiftServiceImpl.list():44-58` 现返回**全部**（含停用），派生集合须过滤 |
| **启用班次数 > 2** | **不在派生侧静默截断**；新增**班次定义侧校验**拒绝，回**新错误码 `9114`**（U-6 已裁定：新增班次定义侧校验错误码；不复用排班侧 `9111`） | 计薪单元编码 `epochDay×2+ordinal` 仅容纳 2（`AttendanceScheduleServiceImpl.java:65-70`）；静默截断会让管理员「配了 3 个班、打卡只认 2 个」 |
| **两班次 `ordinal` 相同**（均落午前 / 午后） | **拒绝**（**班次定义侧**校验，回 `9114`；排班侧另有 `9112` 兜底） | 否则 `period_index` 冲突、计薪 `R` 去重少算（R10，§6） |

> **`check_frequency` 字段去留（U-4 已裁定）**：**保留、只读派生**（= 启用班次数 × 2），不再接受 `PUT /rule` 写入；**不彻底移除列**（避免 DDL）。

---

## 4. 问题 3：`period_index` / `period_name` 的最终口径

### 4.1 定稿

| 字段 | 定稿口径 | 与现状关系 |
| ---- | -------- | ---------- |
| `attendance_record.period_index` | = **`shiftOrdinal(shift.start_time, middayBoundaryMinute)`**：早班 `0` / 晚班 `1`（`ShiftPayrollPolicy.java:64-79`、界值默认 `720`） | 2 班站点下与现「数组下标」**数值相同**；**单班次站点是关键差异**（下） |
| `attendance_makeup.period_index` | 同上（补卡与打卡同源） | 同 |
| `attendance_record.period_name` | = **`attendance_shift.shift_name`** 快照（替代原 rule 时段名） | **语义变更**（值域从「全天班/上午班…」变为「早班/晚班…」） |
| `period_index` 的取值语义 | **班次序号（早/晚）**，非「第几个时段」 | 与 `ShiftPayrollPolicy.attendedShiftSet` ③ 分支（`:183-186`）天然一致 |

### 4.2 关键论证：为什么必须是 `ordinal` 而非数组下标（M1 已更正）

[推导] 设站点仅有 1 个启用班次「晚班 16:00-24:00」（`ordinal = shiftOrdinal("16:00", 720) = 1`）：
- 若 `period_index` 取**数组下标** → 唯一时段序号 = `0`；
- 计薪侧 `record → 班次` 映射为 `shiftUnit(day, 0)`（早班单元），而该员工当日排班 `R = {shiftUnit(day, 1)}`（晚班单元）→ `A ∩ R = ∅` → **判旷工、折算少算**；
- 若 `period_index` 取 **`ordinal=1`** → 与 `R` 一致 → `A ∩ R` 正确。✓

故 D-4 定稿 `period_index = ordinal`。

> **【M1 更正】v1.0 此处原文写「前端 `slotKey`、`validCard`、补卡 `periodIndex` 均以值为键，不受非连续影响」——该结论与实测不符，予以更正。**
> 实测：`AttendanceRecordServiceImpl.java:320`、`AttendanceMakeupServiceImpl.java:123`、`:211-212` **均为 `periods.get(periodIndex)` 数组下标取值**（只有 `validCard:581` 是值比较）。因此在方案自举的「单班次晚班站点」（列表 `size=1`、`periodIndex=1`）下，`1 < 1` 为 false → `period=null` → **9107，打卡与补卡同时失效**。此即评审必改 **M1 / H-1**，取值方式**必须同步改造**（见 §4.4）。

**副作用（更正后）**：`status.periods[]` 的 `periodIndex` 不再是 `0..n-1` 连续值（单班次晚班站点唯一项 `periodIndex=1`）。**因此所有「以 `periodIndex` 取时段」的调用点都不得再用 `List.get(periodIndex)`**——这是 M1 的硬性要求，不能依赖「前端恰好按值传」。

### 4.3 存量记录兼容（历史事实不改写）

| 环节 | 口径 | 依据 |
| ---- | ---- | ---- |
| **不回改**存量 `period_index/period_name` | 历史事实保持原样 | U0-3；`algo-payroll-shift.md:36` |
| 存量读取 | 继续走既有**三态优先级**：① `period_name` 命中哨兵 `全天班` → 覆盖当日全部班次；② 空名 → 多班次计全、置 `PERIOD_NAME_MISSING` 告警（不克扣）；③ 其余按 `(workDate, period_index)` | `ShiftPayrollPolicy.java:152-189`；`algo-payroll-shift.md:92-98` |
| 现网存量形态 | `attendance_record` 386 条全为 `period_index=0` + `period_name='全天班'`（单班制）→ **命中哨兵①**，读取零变化 | `algo-payroll-shift.md:338` |
| **风险**（待核实） | 若存在**非哨兵** `period_name`（如「上午班」）且 `period_index=0` 的历史记录，落在**纯晚班**排班日 → ③ 分支映射到早班单元 → 与 `R` 交集为空。需核实存量 `period_name` 分布 | §12 V-1 |

### 4.4 【M1】取值方式硬性改造要求（`periodIndex` → 时段）

> 依据实际源码：`AttendanceRecordServiceImpl.java:320`、`AttendanceMakeupServiceImpl.java:123`、`:211-212` 均为 `periods.get(periodIndex)` **数组下标取值**。D-4 使 `periodIndex` 非连续（单班次晚班站点唯一项 = `1`），**下标取值必然越界**。故 **B1 必须同步改造取值方式**。

**统一取值规则（三处调用点一致）**：

1. **按 `ordinal` 语义按值查找**：`periodIndex` → 在「该驿站启用班次派生的时段列表」中，查找 `period.periodIndex() == requestedPeriodIndex` 的时段；**禁止 `List.get(periodIndex)`**。
2. **`ordinal` 缺失兜底**（`shiftOrdinal` 返回 `-1`，即 `start_time` 缺失/不可解析，`ShiftPayrollPolicy.java:64-78`）：该班次**不进入派生集合**，并记 `WARN_SCHEDULE_SHIFT_MISSING`（与计薪侧 §6.3 一致）；若因此导致无任何启用时段 → 按「0 启用班次」处理（`9113`）。
3. **查不到即报错**：请求的 `periodIndex` 在派生集合中无匹配 → **`9107`「打卡时段不存在」**（`ErrorCode.java:194`）；打卡与补卡路径同码。
4. **`periodIndex = null`**：仍走「单班次模型」（`usePeriod=false`），取排班班次时间，兜底基准改班次派生（§5 / §0.2-B2）。

**改造点与对应测试**（与 §0.2-A 一致）：

| 调用点 | 位置 | 现状 | 改造 | 对应测试 |
| ------ | ---- | ---- | ---- | -------- |
| 打卡 | `AttendanceRecordServiceImpl.java:320` | `periods.get(periodIndex)` | 改为按值查找；`recordPeriodName`（`:386`）取命中时段名 | 单班次晚班站点：`periodIndex=1` 命中；`periodIndex=0` → 9107 |
| 补卡-申请 | `AttendanceMakeupServiceImpl.java:123` | `periods.get(periodIndex)` | 改为按值查找 | 同上 |
| 补卡-审批 | `AttendanceMakeupServiceImpl.java:211-212` | `periods.get(makeup.getPeriodIndex())` | 改为按值查找 | 审批时 `periodIndex=1` 可写记录；错配 → 不可写（`9101`/拒绝） |
| 解析器自测 | `AttendancePeriodResolverTest.java:44-57`（F2） | 构造无 `checkPeriods` 的 rule | 断言来源改班次派生 | 保留兜底/序号断言并新增按值查找用例 |

> 建议把「按值查找」收敛为**解析器/工具类的单一方法**（如 `findByOrdinal(periods, ordinal)`），三处调用同一实现，避免第二/第三处漂移（对齐评审 M-5 对 `ordinal` 双实现的担忧）。

### 4.5 【M1】单班次晚班站点：三路径算例（证明改造后可达且正确）

> 站点配置：**唯一启用班次** = 「晚班 16:00–24:00」，`status=1`，`start_time="16:00"` → `ordinal = shiftOrdinal("16:00", 720) = 1`；派生时段列表 `periods = [{periodIndex:1, name:"晚班", startTime:"16:00", endTime:"24:00"}]`（`size=1`）。

| 路径 | 输入 | 旧逻辑（下标，M1） | v1.1 逻辑（按值） | 结论 |
| ---- | ---- | ----------------- | ---------------- | ---- |
| **status** | 无 | `periods` 逐项展开 → 输出 `[{periodIndex:1, name:"晚班", ...}]` | 同（展开不依赖下标） | 前端按 `periodIndex` 值渲染 ✓ |
| **打卡** | `checkIn(periodIndex=1)` | `320: 1 < 1` → false → `period=null` → **9107** | 按值命中 `periodIndex==1` 的晚班 → 时间窗/迟到早退按 16:00–24:00 判定 → 写 `period_index=1, period_name="晚班"` | **可达且正确 ✓** |
| **打卡（错值）** | `checkIn(periodIndex=0)` | `320: 0 < 1` → true → `periods.get(0)` → **错误取到晚班**（假命中） | 按值**查不到** `periodIndex==0` → **9107** | 正确拒绝（该站无早班）✓ |
| **补卡** | `makeup(periodIndex=1, checkType=OFF 下班卡)` | `123: 1 < 1` → false → `period=null` → **9107** | 按值命中晚班 → **按 `checkType` 取规定时间**：`ON`（上班卡）→ `startTime="16:00"`；**否则（`OFF` 等）→ `endTime="24:00"`**（`AttendanceMakeupServiceImpl.java:224-225`，`CHECK_TYPE_ON.equals(checkType) ? startTime : endTime`）→ 写记录 | **可达且正确 ✓** |

> 结论：改造前「单班次晚班站点」**不可达**（打卡/补卡均 9107）；改造后**可达且正确**，且对错值有明确拒绝。这是 M1「重新论证是否真正可达且正确」的正面答复。

---

## 5. 问题 4：`work_start_time` / `work_end_time`（派生字段）

| 项 | 定稿 |
| -- | ---- |
| 列 | **保留**（`attendance_rule.work_start_time/work_end_time`，`db.md:623`）；不改 DDL 结构（降低迁移面） |
| 语义 | 改为 = **该驿站启用班次按 `start_time` 升序的「首班开始 / 末班结束」**（原为「`check_periods` 首段开始 / 末段结束」） |
| 写侧 | **删除**「保存规则时按 `check_periods` 重算」的写逻辑（`AttendanceRuleServiceImpl.java:100-103`），不再由规则保存写入 |
| 读侧 | `toVO`（`AttendanceRuleServiceImpl.java:143-147`）改为**实时由班次派生**；无启用班次时返回 `null` |
| 契约 | `GET /rule` 出参字段**保留**（值改派生），避免破坏前端与旧客户端解析 |

> 备选（不选）：删除两列 → 破坏性 DDL，且旧客户端解析 `GET /rule` 会缺字段。

---

## 6. 问题 5：R11 风险收口论证

### 6.1 R11 定义（复核）

> R11：`period_index ↔ ordinal` 对齐依赖不变式 A2，当前无强制校验；触发 = 规则时段顺序与班次 `start_time` 升序不一致；影响 = 第二班次打卡时段错位。（`boss-management-architecture.md:457`）

### 6.2 论证（M6 已精确化）

[推导] R11 的**根因是存在两套相互独立的顺序**：① `check_periods` 的数组顺序；② 班次按 `start_time` 升序 + `middayBoundaryMinute` 派生的 `ordinal`。

- 真源统一后，`period_index` **直接由班次 `ordinal` 计算**（D-4），不再存在「规则顺序」这一独立来源 → **R11 根因消失**。
- **【M6 更正】消除的条件表述**（v1.0 原文「当且仅当站点启用班次 `ordinal` 互异」**不严谨**）：
  - **R11 消除是「由构造充分」的**：只要真源统一（`period_index` 源自班次），R11 即消除——**与启用班次 `ordinal` 是否互异无关**。故 `ordinal` 互异**不是** R11 的消除条件（既非充分也非必要）。
  - **`ordinal` 互异是「避免 R10 型取值冲突」的必要条件**（另一风险域）：若同一站点派生集合出现**两个相同 `periodIndex`**（如两个早班），则 `status.periods` 出现重复键 → 前端 `slotKey` 冲突、打卡去重与计薪 `R` 去重错乱。
  - **残余风险定级**：**中高**——直至**班次定义侧校验落地**为止（排班侧 `9112` 仅拦「排班时」，拦不住「只定义不排班」，见 §6.3）。
- **实现一致性要求（评审 M-5）**：`ordinal` 判定当前存在**两处实现**——`ShiftPayrollPolicy.java:75`（`return hour * 60 + minute < middayBoundaryMinute ? 0 : 1;`）与 `AttendanceScheduleServiceImpl.java:535`（后者为**内联** `minutes < boundary ? 0 : 1`，**未复用** `shiftOrdinal`）。**班次定义侧新增校验必须复用同一实现**（建议抽取共享方法），否则将出现**第三处**并漂移。**行号按实测校正（N6）**。

### 6.3 残余与防护

| 残余风险 | 现状 | 防护 | 定级 |
| -------- | ---- | ---- | ---- |
| **R10**：同日两班次同属早/晚半天 → `ordinal` 冲突 | **排班侧已收口**：`requireDistinctOrdinalPerDay`（默认 `true`，`AlgoProperties.java:191`）→ 冲突回 `9112`（`AttendanceScheduleServiceImpl.java:253-256,526-540`） | 排班侧保持；**新增班次定义侧同校验**（启用班次 `ordinal` 互异，回 `9114`），堵住「定义了 2 个同半天班次」的入口 | **中高**（校验落地前） |
| **班次定义数 > 2** | **无校验**（现网每驿站 3 班次含中班，算法 B5 已裁定删中班——`algo-payroll-shift.md:65`） | 新增启用班次数上限校验（≤2，回 `9114`，§3.2） | 中 |
| **`ordinal` 双实现漂移** | `ShiftPayrollPolicy.java:75` 与 `AttendanceScheduleServiceImpl.java:535` 两处（**行号按实测校正，N6**） | 新增校验**复用同一实现**（§6.2 末） | 中 |
| `middayBoundaryMinute` 界值边界（`start_time` 恰 = 720，即 `12:00`） | `minutes < boundary ? 0 : 1` → `12:00` 归**晚班**（`ShiftPayrollPolicy.java:75`）；算法文档亦备注 `12:00` 边界（`algo-payroll-shift.md:26` 建议项 S6） | 保持现状口径，不为本方案变更；`TODO(扩展)`：ShiftManager 是否加「12:00 起算晚班」提示待产品确认（V-3） | 低 |
| 跨零点班次（`end_time=24:00`） | 结构支持（`AttendanceShift.java:15` 注释、`V5:56`） | `period_index` 取**班次开始日**的 `ordinal`（承接 `algo-payroll-shift.md:135` 建议项 S3） | 低 |

### 6.4 【M2】哨兵名冲突防护（新增）

> **问题（评审 M2 / H-2）**：`period_name` 改取 `attendance_shift.shift_name` 后，若站点把班次命名为 **`全天班`**（= `AlgoProperties.java:123` 的 `legacyPeriodSentinel`，亦 = `AttendanceConstants.DEFAULT_PERIOD_NAME`），则新打卡记录 `period_name='全天班'` 会命中 `ShiftPayrollPolicy.java:168-171` 的**哨兵分支①** → **`attended.addAll(keys)` 覆盖当日全部班次** → **实到虚增、旷工罚款与折算基数被吞**。而 `AttendanceShiftServiceImpl.validateShift:157-159` 现仅校「1-20 字符」，**不禁止**该名。
> **【N2 补充·防护可绕过】**：更关键的是调用顺序——`validateShift(safe)` 在 `setShiftName(safe.getShiftName().trim())` **之前**执行（`AttendanceShiftServiceImpl.java:71`（create）/`:92`（update）vs 落库 `:74`/`:93`），而 `AttendanceSupport.textLen` **内部已 trim**（`AttendanceSupport.java:41`）。故若禁用名比对取**原始入参**，「 全天班」「全天班 」可**同时通过长度校验**、却与「全天班」**不相等** → **防护被前置/尾随空白绕过**。因此本防护**必须归一化后比对**（见下表「归一化」行）。

**选定防护方式 = ①班次定义侧禁用语校验（写入/定义侧拦截）**：

| 项 | 内容 |
| -- | ---- |
| 规则 | `shift_name` **归一化（`trim()`）后**不得等于 `algoProperties.getPayroll().getLegacyPeriodSentinel()`（默认「全天班」）或 `AttendanceConstants.DEFAULT_PERIOD_NAME`（`AttendanceConstants.java:64`，值同「全天班」）；违反回 **`9114`**（与 §3.2/§7.7 同一码） |
| **归一化（N2，必改）** | **落点 = `validateShift` 的「首查项」**（置于长度/格式校验**之前**）：`String name = body.getShiftName() == null ? null : body.getShiftName().trim();` → 再判 `name` 是否 ∈ 保留名集合。**必须与落库值同源归一**（`create:74` / `update:93` 均为 `safe.getShiftName().trim()`）→ 保证「校验用的值 == 落库值」。**覆盖范围与计薪侧对齐**：`ShiftPayrollPolicy.java:168` 用**精确 `equals`** → 本校验**仅 `trim`**，**不做**全角折叠 / 大小写折叠（若折叠会造成「校验放行、计薪不认」的偏差）；`null`/空白名由既有长度校验拦截（`textLen`，`AttendanceShiftServiceImpl.java:158`） |
| 落点 | ① 前端 `ShiftManager.vue` 先行提示（§8.3）；② **服务端 `validateShift` 强制（归一化后比对）**（`AttendanceShiftServiceImpl.java:157-177`）防直调 API 绕过（对齐 `api.md:895` 既有手法） |
| 校验时机 | 新增/编辑班次时；**不回溯改存量**（存量班次名不动，见 U0-3） |
| 影响面（`ShiftPayrollPolicy` 三态） | **三态逻辑保持不动**（哨兵分支仍存在，供历史哨兵记录兼容）；防护在**写入/定义侧**，使**新记录不再产生哨兵名** → 三态②③与普通分支不受影响 |
| 历史数据兼容 | 存量 `period_name='全天班'` 记录继续命中分支①→ **读取零变化**；`AttendanceConstants.DEFAULT_PERIOD_NAME` 仍用于**规则无时段时的兜底段**（`AttendancePeriodResolver.java:70`），本防护**不禁止**该常量，只禁止**班次命名撞名** |

**反例算例与验收判据**：

| 场景 | 输入 | 预期 |
| ---- | ---- | ---- |
| 禁止命名 | 新建班次 `shiftName="全天班"` | **拒绝**（`9114`）→ 该班次不存在 → 不会写入哨兵名 → 后续打卡**不产生**哨兵名 |
| **空白绕过（N2 反例）** | `shiftName=" 全天班"`（前置空格）/`"全天班 "`（尾随） | 归一化（`trim()`）后 == `全天班` → **拒绝**（`9114`）；旧实现按**原始串**比对会**放行**（缺陷），本版已修（`validateShift` 首查 + `trim`） |
| 历史兼容 | 班次名「早班/晚班」+ 存量记录 `period_name='全天班'` | 存量仍命中分支①（覆盖全班次），**不被本防护影响** |
| 等价防护 | 命名「上午班」等非保留名 | 允许；新记录 `period_name="上午班"` 走三态③按 `period_index` 精确定位 |

> 备选（**未选**）：② 把哨兵判定限定为「升级前写入的记录」（引入「升级时间点」判据）——**不选理由**：须新增时间/版本判据字段或账期比对，脆弱且改 `ShiftPayrollPolicy` 计薪域语义（须重评），成本高于禁用名。列为 `TODO(扩展)`，若将来哨兵配置频繁变更再评估。
> **残余风险**：若存在**历史遗留**或**直连 DB** 写入的「全天班」命名班次，仍可能误覆盖；此类数据须由 **P-6 预检**（§9.3，查 `attendance_shift.shift_name` = 保留名的**启用**班次）暴露，属**数据治理**范畴。
> **【N1 更正】** v1.1 原写「须由 **P-4** 预检暴露」**有误**：P-4 只查 `attendance_record.period_name` **非哨兵且非空**，**结构上排除**哨兵名，**查不到**「名为 `全天班` 的班次」→ 已改为 **P-6**（§9.3）。

---

## 7. 问题 6：契约变更清单草案（供后端补录 `api.md`，本文不改正文）

> 说明：以下为**草案**，契约正文由后端工程师按本清单补录 `api.md`；`api.md` 定稿权归后端、架构师评审、主智能体最终把关（调度规则 §9 冲突裁决表）。

### 7.1 `PUT /api/v1/attendance/rule`（现 `api.md:834-898`）

| 项 | 现状 | 变更草案 |
| -- | ---- | -------- |
| `checkPeriods` 入参 | 「时间判定唯一真源」，见约束表（`api.md:850,880-888`） | **拒绝（U-5 已裁定）**：字段保留以兼容旧客户端请求体，但**显式非空即回 `400`**「时段已由班次决定，请维护班次」；**不再写入、不再校验段数量/重叠**。**不采用「静默忽略」**（防管理员以为改了时段却无效）。**【N7 明示】错误码 = **通用 `400`**（`ErrorCode.BAD_REQUEST`，`ErrorCode.java:20`，**非**业务码 91xx），对齐 `api.md:797`「字段校验类错误统一 `400`」** |
| `workStartTime` / `workEndTime` 入参 | 旧客户端兼容映射到首/末段（`api.md:853-854`） | **废弃**：忽略入参；出参改派生 |
| `checkFrequency` 入参 | 仅 2/4（`api.md:849`） | **只读（U-4 已裁定）**：忽略入参；出参改派生（启用班次数 × 2） |
| 其余字段（`ruleName/enable*/matchMode/wifiList/围栏/阈值/status`） | — | **不变** |

> **发布顺序约束（评审 M6′）**：现网 web `RuleCard.vue:266`、boss-h5 `attendanceRule.vue:155` **仍提交 `checkPeriods`**。若 U-5「非空回 400」且 B1 先于 B2 上线 → **现网规则保存全面 400**。故 **B1 与 B2 必须同批发布**（或过渡期临时取「忽略」兼容分支，待 B2 上线后再收紧为 400）；见 §11。

### 7.2 `GET /api/v1/attendance/rule`（现 `api.md:800-826`）

| 出参字段 | 变更草案 |
| -------- | -------- |
| `checkPeriods` | **保留字段**，值改为**由该驿站启用班次派生**（只读）；**新增出参** `checkPeriodsReadonly: boolean = true`（已计入 §7.8「新增」），供前端渲染「由班次决定」 |
| `workStartTime` / `workEndTime` | 保留，值改为派生（§5）；无启用班次时为 `null` |
| `checkFrequency` | 保留，值 = 启用班次数 × 2；无启用班次时为 `0`（U-4 已裁定：**保留只读派生**，语义与「列去留」解耦） |
| 错误码 | `9101`（未配规则）**不变**；**不新增**「未配班次」错误（查询类不拦截，出空数组 + 前端引导） |

> **性能说明（评审 L-3 合并登记）**：`toVO`（`:142-147`）同被 `GET /rule/list`（`listRules`）复用，故本端点与 §7.3 的「N+1 派生」属**同一性能面**，统一走**批量预取班次**（见 §10.4 RK-1）。

### 7.3 `GET /api/v1/attendance/rule/list`（现 `api.md:828-832`）

| 项 | 变更草案 |
| -- | -------- |
| 出参 | 元素同 `4.6.1`；`checkPeriods` 逐条**按各自驿站班次派生**（**注意：这是 N+1 派生**，实现需批量预取班次；性能面登记见 §10.4） |

### 7.4 `GET /api/v1/attendance/status`（现 `api.md:900-918`）

| 出参字段 | 变更草案 |
| -------- | -------- |
| `periods[]` | 来源由「rule 时段」改为「该驿站启用班次派生」；`periodIndex` = `ordinal`（早 0/晚 1），`name` = `shift_name`，`startTime/endTime` = 班次起止，`windowStart/windowEnd` 计算**不变**（仍用 `allowEarlyMin/allowLateMin`） |
| `checkFrequency` | 派生（启用班次数 × 2） |
| `requireSummary` | 文案随 `periods[].name` 自动更新（`AttendanceRecordServiceImpl.java:470-472` 逻辑保留） |
| `shift` | **不变**（仍为今日排班班次；未排班时为兜底班次——兜底来源需从「规则派生」改为「班次派生」，见 §8.4 注） |
| 新增 | **新增出参** `shiftConfigured: boolean`（该驿站是否配置了启用班次），供前端区分「无班次 → 不可打卡」空态（已计入 §7.8「新增」） |

### 7.5 考勤记录 VO（现 `api.md:950`、`4.6.7` 导出、`4.6.9` 明细）

| 字段 | 变更草案 |
| ---- | -------- |
| `AttendanceRecordVO.periodName` | **语义变更**：快照值域 = 班次名（新记录）；历史记录保持原值（不改写），文档须注明「历史值为旧时段名，新值为班次名」 |
| `AttendanceDetailVO.shiftName` / `periodName` | 语义随班次名统一；明细 `periodName` 现取代表卡快照（`AttendanceRecordServiceImpl.java:655`），保持「快照」语义 |
| 导出「时段名称」列 | 文案建议改为「班次/时段」（列序与列数**不变**，13 列） |

### 7.6 补卡 `POST /api/v1/attendance/makeup` 与 `POST /api/v1/attendance/makeup/{id}/approve`（现 `api.md:1034,1049`）

> **【M4 更正】** v1.0 标题误写为「`POST/PUT .../makeup*`」；实测**两接口均为 `POST`**（提交 `POST /makeup`、审批 `POST /makeup/{id}/approve`，`api.md:1034,1049`），无 `PUT`。

| 项 | 变更草案 |
| -- | -------- |
| `periodIndex` | 语义 = 班次序号（早 0/晚 1）；校验基准由 rule 时段改为班次派生；取值改**按值查找**（§4.4，M1） |
| 「打卡时间取该时段规定时间」 | 改为「取该**班次**规定的上下班时间」（`AttendanceMakeupServiceImpl.java:224-225` 改用派生时段） |
| 错误码 | `9107`（时段不存在）语义扩展为「班次对应时段不存在」；**新增 `9113`** 用于站点无启用班次 |

### 7.7 新增错误码（U-6 已裁定）

| 码 | 文案（建议） | 触发 |
| -- | ------------ | ---- |
| **9113** | 「该驿站未配置启用班次，无法打卡」 | 打卡 / 补卡 时该驿站无 `status=1` 班次 |
| **9114** | 「班次定义非法：启用班次数超上限（2）或时段归属冲突（同落午前/午后）」 | **班次定义侧**校验（`validateShift`）：启用班次数 > 2 或启用班次 `ordinal` 冲突；亦用于**哨兵保留名冲突**（**`trim` 后**班次名 = `全天班`，§6.4，**N2**） |

> 说明：`9114` 为**班次定义侧**新码（U-6 裁定「新增」），**不复用**排班侧 `9111`（单日排班超限）/ `9112`（同日排班一早一晚）——三者语义域不同（定义侧 vs 排班侧），复用之会混淆排障。
> **【N7 明示】U-5（`PUT /rule` 收到非空 `checkPeriods`）的拒绝码**：使用**通用 `400`**（`ErrorCode.BAD_REQUEST`，`ErrorCode.java:20`），**不新增 91xx、不复用 `9107`/`9113`/`9114`**；依据 `api.md:797`「字段校验类错误统一 `400`」。故 §7.8 的「新增」计数**不含**此项（不增码）。

### 7.8 变更计数（M4 重算）

| 类别 | 数量 | 明细 |
| ---- | ---- | ---- |
| **新增** | **4** | 错误码 `9113`；错误码 `9114`；出参 `checkPeriodsReadonly`（§7.2）；出参 `shiftConfigured`（§7.4） |
| **变更** | **6 组 / 字段级 11 项** | ① `PUT /rule` 入参 `checkPeriods`→**拒绝(400)**、`workStartTime/workEndTime`→废弃、`checkFrequency`→只读（**3 项**）；② `GET /rule` 出参 `checkPeriods`→派生、`workStartTime/workEndTime`→派生、`checkFrequency`→派生（**3 项**）；③ `GET /rule/list` 逐站派生（**1 项**）；④ `GET /status` `periods[]`→派生且 `periodIndex=ordinal`、`checkFrequency`→派生（**2 项**）；⑤ 记录 VO / 明细 / 导出 `periodName` 语义 + 导出列文案（**1 项**）；⑥ 补卡 `periodIndex` 语义 + 规定时间基准 + `9107` 语义扩展（**1 项**） |
| **复用（不新增）** | **2** | ① `/api/v1/shifts` CRUD（班次维护入口，前端「去维护班次」直接跳转）；② `GET /attendance/status.shift`（今日班次，字段语义不变） |

> 结论：**不新增任何班次接口**（复用既有 `GET/POST/PUT/DELETE **/api/v1/shifts**`）。
> **【M4 更正】** v1.0 误写为 `/api/v1/attendance/shifts`；实测前缀为 **`/api/v1/shifts`**（`ShiftController.java:28`、`api.md:1062` §4.8）。

---

## 8. 问题 7：前端改动面

### 8.1 PC `RuleCard.vue`（`hrm-clients/apps/web/src/views/attendance/components/RuleCard.vue`）

| 改动点 | 现状 | 草案 |
| ------ | ---- | ---- |
| 时段编辑区（`:327-367` 频次 + 时段行 + `el-time-select`） | 可编辑时段名与起止 | **移除编辑**，改为**只读列表**展示「由该驿站班次决定」（显示班次名 + 起止），并给「**去维护班次**」按钮（跳转排班页班次管理区） |
| 「打卡频次」控件（`:329-335`、`handleFrequencyChange:220-223`） | 2/4 单选，联动增删时段 | **改只读展示**（显示「2 次（1 个班次）/ 4 次（2 个班次）」派生文案）——**U-4 已裁定：保留只读派生** |
| 派生校验 `validatePeriods`（`:164-180`） | 本地拦时段非法 | **删除**（无时段可编） |
| 「上下班时间」只读派生（`:439-442`） | 由 `checkPeriods` 派生 | 改为「由班次派生」，来源改 `workStartTime/workEndTime` 出参 |
| 提交体（`:251-277`） | 上报 `checkPeriods` | **不再上报** `checkPeriods/workStartTime/workEndTime/checkFrequency` |

### 8.2 boss-h5 `attendanceRule.vue`（`hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue`）

| 改动点 | 现状 | 草案 |
| ------ | ---- | ---- |
| 打卡频次 chips（`:478-492`、`setFrequency:315-332`） | 2/4 切换 | 移除/只读（同 8.1） |
| 打卡时段区（`:494-516` 时段名 + 时间选择器 `van-time-picker`、`openPicker/onPickerConfirm:340-350`） | 可编辑 | **移除编辑**，改只读展示 + 「去维护班次」引导——**U-3 已裁定：引导指向网页端维护班次**（boss-h5 无班次维护页，文案「请到网页端维护班次」） |
| `periodError`（`:177-191`）、`payload.checkPeriods`（`:155-159`） | 时段校验与上报 | 删除校验、不再上报 |
| 派生作息展示（`:82-83,510-516`） | 由 `checkPeriods` 派生 | 改由 `workStartTime/workEndTime` 出参 |

### 8.3 `ShiftManager.vue`（`hrm-clients/apps/web/src/views/schedule/components/ShiftManager.vue`）

| 改动点 | 现状 | 草案 |
| ------ | ---- | ---- |
| 校验 `formRules`（`:69-93`） | 名称/起止/结束晚于开始/休息时长 | **补充**：① **班次名禁用保留名**（**`trim()` 后**不得 = `全天班`，即 `legacyPeriodSentinel` / `DEFAULT_PERIOD_NAME`，回 `9114`，§6.4 M2/**N2**）；② 保存后**站点启用班次数 ≤ 2**（超限 `9114`，§3.2）；③ 启用班次 `ordinal` 互异（同落午前/午后拒绝，提示「需一早一晚」，回 `9114`）；④ **时间重叠**提示（对齐排班侧 `9110` 语义） |
| 说明文案 | 无 | 新增说明：**本页班次是打卡时段的唯一来源**；`TODO(扩展)`：「12:00 起算晚班」边界提示待产品确认（V-3） |
| 停用语义 | `status` 0/1 | 提示「停用班次不再作为打卡时段」 |

> 注：班次定义侧校验**必须服务端落**（`AttendanceShiftServiceImpl.validateShift:157-177`，前端仅先行提示），防直调 API 绕过（对齐 `api.md:895` 既有手法）；错误码 `9114`（U-6 已裁定新增）。**禁用名比对取 `trim()` 后值**（**N2 必改**）：服务端 `validateShift` **首查**、与落库值（`setShiftName(trim())`）**同源归一**；前端提示同理**先 `trim` 再判**，避免「 全天班」在前端通过、后端拒绝的体验落差。

### 8.4 staff-h5 打卡页（`hrm-clients/apps/staff-h5/src/views/staff/attendance.vue` 及 `components/PeriodCard.vue`）

| 改动点 | 现状 | 草案 |
| ------ | ---- | ---- |
| `PeriodCard.vue`（`:33-56`） | 展示 `period.name / startTime / endTime / windowText`，数据来自 `status.periods` | **出参形态不变**（仍是 `periodIndex/name/startTime/endTime/windowStart/windowEnd`）→ **组件可零改动**；仅「时段名」显示值从旧时段名变为班次名，**无需改代码** |
| 打卡页 | 依赖 `status.periods` | 需**新增空态**：`shiftConfigured=false`（无启用班次）时禁用打卡按钮 + 提示「该驿站未配置班次，请联系管理员」 |
| 补卡页（`MakeupPopup.vue` 等） | 依赖 `status.periods` 的 `periodIndex` | 出参形态不变，无需改；`periodIndex` 取值变（0/1）无需改前端 |

> **兜底班次（`AttendanceShiftServiceImpl.defaultShift:140-152`）**：现由 `rule.workStartTime/workEndTime` 合成；改后应改为「该驿站首个启用班次」派生，或（无启用班次时）返回 `null`。属**后端改动**，此处登记。

### 8.5 前端改动页面清单（汇总）

| # | 工程 | 文件 | 改动性质 |
| - | ---- | ---- | -------- |
| 1 | `hrm-clients/apps/web` | `views/attendance/components/RuleCard.vue` | 移除时段编辑 → 只读 + 引导 |
| 2 | `hrm-clients/apps/web` | `views/schedule/components/ShiftManager.vue` | 补校验 + 说明 |
| 3 | `hrm-clients/apps/boss-h5` | `modules/boss/views/attendanceRule.vue` | 移除时段编辑 → 只读 + 引导 |
| 4 | `hrm-clients/apps/staff-h5` | `views/staff/attendance.vue`（+ `PeriodCard.vue` 视空态需要） | 新增无班次空态 |
| 5 | `hrm-demo`（镜像） | `src/pc/.../RuleCard.vue`、`ShiftManager.vue`、`src/mobile/.../attendanceRule.vue` | **不同步（U-7 已裁定）**：仅**登记**为「镜像存在但不在本次改动范围」，不改 `hrm-demo` |
| 6 | `hrm-clients/packages/mock` | `attendanceStore.js` / `routes/attendance.js` | **同步（U-7 已裁定）**：Mock 为独立真源（`api.md:997-1006`），**同步**以保演示一致 |
| 7 | `hrm-clients/scripts` | **`verify-mock.mjs`**（Mock 侧门禁资产，`api.md:999` 明确属 Mock 侧） | **同步时需适配（N5 补登记）**：该脚本含以 `checkPeriods`/`checkFrequency`/`periodIndex` **旧语义**编码的断言（如 `verify-mock.mjs:1934-1941,2106-2109`）。B3 同步 mock 后须**核验**其断言；**不得以改断言迁就实现**（违验收资产纪律：调度规则 **A06**），确因契约变更需调整的须**逐条说明理由并登记** |

> **【N5 更正·登记纪律】** v1.1 §8.5 遗漏 Mock 门禁资产 `hrm-clients/scripts/verify-mock.mjs`。该脚本为 **Mock 侧门禁真源**，其「旧语义断言」在 U-7「mock 同步」后**可能失败**；不得被当作「误报」而删除或改断言迁就实现。

> **【M3 更正】** v1.0 §8.5 第 5 行把 `hrm-demo` 列为「同步镜像（是否同步见 U-7）」，与主代理已裁定「**`hrm-demo` 不同步**、mock 同步」**相反**；v1.1 已改为与裁定一致。

> **UI/UX 强制派发判定**：本改动为「移除编辑控件 + 只读展示 + 引导」属**既有组件结构调整**，命中 R01（新增交互/只读态）边缘 → **建议 UI/UX 设计师先行给出只读态与空态的视觉规范**，前端再落地（§11 批次门禁已含）。

---

## 9. 问题 8：迁移方案（接续 `V24`）+ 预检 + 回滚

### 9.1 结论：方案 (a) 下 `V24` **非功能必需**

[推导] 选 (a) 后：`check_periods` 列保留（D-2）、无新增列、无改列型、无唯一键 → **无结构变更**。故 `V24` 不是「让功能生效」的必要条件。

### 9.2 `V24` 的两种可选承接（择一；迁移选项，非口径裁定）

| 选项 | 内容 | 性质 |
| ---- | ---- | ---- |
| **V24-A（推荐，最小）** | **不改 DDL**；仅由数据库工程师同步 `db.md` 列注释（`check_periods` 标「废弃：时段由班次派生」）——**注释同步属文档，非 DDL**，故可**不出 V24** | 零 DDL |
| **V24-B（治理）** | 添加**预检/治理**：① 报告「有规则但无启用班次」的站点；② 报告「启用班次 > 2」的站点；③ 报告「`ordinal` 冲突」的站点。**仅报告，不回改 `check_periods`** | 数据治理，无 DDL |

> **不建议**：`V24` 清空/重写存量 `check_periods`（不可逆，且 (a) 下已不读该列，无收益）。若用户坚持「消除双份数据」，须走 C 档三步授权并明确回滚（§9.4）。

### 9.3 迁移前预检（判据，`V24-B` 使用）

> 以下为**预检判据**（描述 + 查询），非迁移脚本正文；执行属 C 档，须主智能体授权。

| # | 判据 | 目的 |
| - | ---- | ---- |
| P-1 | 存在 `attendance_rule`（`is_deleted=0`）但该 `station_id` 无 `attendance_shift`（`status=1 AND is_deleted=0`） | 触发新边界「无启用班次 → 打卡拒绝（9113）」的站点清单，需运营先补班次**再上线** |
| P-2 | 同一 `station_id` 启用班次数 > 2 | 超计薪编码容量的站点，需先删减 |
| P-3 | 同一 `station_id` 启用班次按 `start_time` 计算 `ordinal` 后**存在重复** | `ordinal` 冲突站点（R10），需先调整为「一早一晚」 |
| P-4 | 存量 `attendance_record` 中 `period_name` **非哨兵（≠'全天班'）且非空**的记录数按 `station_id` 分布 | 评估 §4.3/§10.2 的存量错配风险（V-1）。**【N1 更正】** 本项**只查记录侧 `period_name`**，其条件**结构上排除哨兵名** → **查不到**「名为 `全天班` 的**班次**」→ **不再兼作 M2 残余预检**（M2 残余改由 **P-6** 承担）；本项列为**非阻塞登记式** |
| P-5 | 启用班次 `start_time` 恰等于 `middayBoundaryMinute`（默认 `12:00`）的站点 | 边界站点，确认归属「晚班」是否符合运营预期（**非阻塞登记式**） |
| **P-6** | **班次名撞哨兵（N1 新增，M2 残余）**：检出 `attendance_shift` 中 **`TRIM(shift_name)` = 保留名**（`legacyPeriodSentinel`，默认「全天班」；亦 = `DEFAULT_PERIOD_NAME`）的**启用**班次（`status=1 AND is_deleted=0`） | 暴露「历史遗留 / 直连 DB 写入」的哨兵同名班次（§6.4 残余风险）：此类站点改造后仍会产出 `period_name='全天班'` 的新记录 → **命中哨兵分支①、实到虚增**。**须在 B1 上线前治理**（改名或以停用该撞名班次；**改名前须评估其历史记录影响**）或阻断 |

**P-6 查询（SQL 判据，N1 新增）**：

```sql
-- P-6：班次名撞哨兵（M2 残余）——检出会被 ShiftPayrollPolicy 哨兵分支①误判的「启用」班次
-- 哨兵值取运行时配置 hrm.algo.payroll.legacyPeriodSentinel（默认「全天班」，AlgoProperties.java:123）
--   与常量 AttendanceConstants.DEFAULT_PERIOD_NAME（AttendanceConstants.java:64，默认同值）
-- 用 TRIM(shift_name) 与「归一化后比对」口径一致（N2）
SELECT s.station_id, s.id AS shift_id, s.shift_name, s.status
FROM attendance_shift s
WHERE s.is_deleted = 0                 -- 未软删
  AND s.status = 1                     -- 启用
  AND TRIM(s.shift_name) IN ('全天班')  -- 保留名集合；配置非默认时替换为实际哨兵值
ORDER BY s.station_id, s.id;
```

**P-6 判据**：① 结果集**为空** → 通过（无 M2 残余）；② 结果集**非空** → **阻断式（B-1 出口门禁，见 §11）**：须在 B1 上线前完成治理（改名 / 停用撞名班次），治理后**复跑至空集**。备注：改名**不改写**其历史 `period_name='全天班'` 记录（历史事实保持哨兵分支①，读取零变化，U0-3）；但**不改名**则新记录持续虚增。

### 9.4 回滚路径（M5 已补全 + **N3 补 D5/D6/D7**，与 §11 B1 一一对应）

> **【M5 更正】** v1.0 仅列「反向 diff **3 读取点** + 解析器 + `saveRule` 重算」，**漏** `toVO` / `defaultShift` / `newRuleDefaults`。若这些已改而回滚不含，回滚后 `toVO`/兜底班次仍走派生路径，**与 `check_periods` 真源不一致**。
> **【N3 更正】** v1.1 的 R-1~R-11 仍**漏**台账需改的 **D5/D6/D7**（`saveRule` 时段侧去除点）→ 本版**补 R-12/R-13/R-14**，回滚清单升为 **R-1~R-14**，并新增下方「**回滚清单 ↔ §0.2 台账**」对应表，证明与台账「需改 **22 条**」**逐条可对**。下表**逐项与 B1 改动面一一对应**。

| # | 回滚项（反向 diff 目标） | 位置（文件:行号） | 对应 §0.2 |
| - | ------------------------ | ---------------- | --------- |
| R-1 | 恢复解析器 `resolve(rule)` 原语义（`check_periods` 优先 + 兜底段） | `AttendancePeriodResolver.java:54-72` | A/D1、B1 |
| R-2 | 恢复打卡取值链：`periods.get(periodIndex)` 下标取值、时间基准二选一、`recordPeriodName` 取 rule 名 | `AttendanceRecordServiceImpl.java:316-346,385-386` | A1、B2 |
| R-3 | 恢复 `todayStatus` 时段展开与 `checkFrequency` 出参来源 | `AttendanceRecordServiceImpl.java:447-472` | A2、C1 |
| R-4 | 恢复补卡申请取值（下标）与时段来源 | `AttendanceMakeupServiceImpl.java:121-126` | A3 |
| R-5 | 恢复补卡审批取值（下标）与规定时间来源 | `AttendanceMakeupServiceImpl.java:208-225` | A4 |
| R-6 | 恢复 `saveRule` 的 `workStartTime/workEndTime` 重算写逻辑 | `AttendanceRuleServiceImpl.java:96,100-103` | B5、D2、D3 |
| R-7 | 恢复 `toVO` 出参（`checkFrequency`/`checkPeriods`/`workStartTime/workEndTime` 原取值） | `AttendanceRuleServiceImpl.java:142-147` | B3、C2、D4 |
| R-8 | 恢复 `newRuleDefaults` 默认时段（`checkPeriods` + `checkFrequency` 写入） | `AttendanceRuleServiceImpl.java:386-431` | C3、D8 |
| R-9 | 恢复 `defaultShift` 由规则派生兜底班次 | `AttendanceShiftServiceImpl.java:140-152` | B4 |
| R-10 | 撤销班次定义侧新增校验（保留名 / ≤2 / `ordinal` 互异）与 `9113`/`9114` | `AttendanceShiftServiceImpl.java:157-177` + `ErrorCode.java` | §6.4、§7.7 |
| R-11 | 恢复解析器自测断言来源 | `AttendancePeriodResolverTest.java:44-57` | F2 |
| **R-12** | **【N3 新增】** 恢复 `saveRule` 时段校验 `validatePeriodRule`（频次档位 / 时段数量 / 单段起止 / 段间重叠与顺序）的**调用与实现** | `AttendanceRuleServiceImpl.java:200-236`（调用点 `:96`） | **D5**（C4 时段侧） |
| **R-13** | **【N3 新增】** 恢复 `normalize` 的**时段兼容映射 / 归一化**（未传时段沿用现值 + 旧客户端只发上下班时间时映射到首/末段） | `AttendanceRuleServiceImpl.java:293-320` | **D6**（C4 时段侧） |
| **R-14** | **【N3 新增】** 恢复 `applyPayload` 对 `checkFrequency` / `checkPeriods` 的写入 | `AttendanceRuleServiceImpl.java:374-379` | **D7**（C4 时段侧） |

**回滚清单 ↔ §0.2 台账（需改 22 条）逐条对应（N3 必改，证「一一对应」）**

| 台账「需改」组 | 条目 | 对应回滚项 |
| -------------- | ---- | ---------- |
| **A（4）** | A1 / A2 / A3 / A4 | R-2 / R-3 / R-4 / R-5 |
| **B（5）** | B1 / B2 / B3 / B4 / B5 | R-1 / R-2 / R-7 / R-9 / R-6 |
| **C（4）** | C1 / C2 / C3 / C4 | R-3 / R-7 / R-8 / **R-6 + R-12 + R-13 + R-14**（C4 = `:96,201-237,296-320,374-379` 的频次与时段侧） |
| **D（8）** | D1 / D2 / D3 / D4 / D5 / D6 / D7 / D8 | R-1 / R-6 / R-6 / R-7 / **R-12** / **R-13** / **R-14** / R-8 |
| **F（1）** | F2 | R-11 |
| 支撑（非台账消费点） | 班次定义侧校验与错误码 | R-10（§6.4、§7.7） |

> **对应对账**：A 4 + B 5 + C 4 + D 8 + F 1 = **22 条，全部命中**；其中 **D5/D6/D7 由 R-12/R-13/R-14 覆盖**（v1.1 缺此三项即 N3）。

| 层 | 回滚 |
| -- | ---- |
| 代码 | 按 **R-1~R-14** 反向 diff（与 §11 B1 改动面一一对应，映射见上表） |
| 数据 | 选 (a)/(V24-A)：**无数据变更** → 无需回滚；选 V24-B：仅报告，无变更 |
| 已知不可逆点 | **无**（方案 (a) 不改写 `check_periods`、不删列，回滚无损）；**前提**：上线前不删 `check_periods`、不清空其值（D-2 已保证） |

---

## 10. 问题 9：风险与兼容

### 10.1 存量打卡 / 考勤 / 计薪读取（是否零变化）

| 场景 | 结论 | 依据 |
| ---- | ---- | ---- |
| 存量记录全为 `period_name='全天班'` | **零变化**（哨兵覆盖全部班次） | `algo-payroll-shift.md:338`；`ShiftPayrollPolicy.java:168-171` |
| 存量记录 `period_name` 为空 | 零变化（三态②不克扣） | `ShiftPayrollPolicy.java:172-182` |
| 存量记录 `period_name` 非哨兵且 `period_index` 为旧下标 | **潜在变化**（§4.3） | 待核实 V-1 |
| 考勤汇总（应到/实到/缺卡） | 计算逻辑**不变**（仍用班次 + `period_index`），仅新写入记录的 `period_index` 语义更严格 | `AttendanceSummaryPolicy.java:62-129` |
| 计薪折算 | **不变**（`R/A/L` 集合运算不变） | `ShiftPayrollPolicy.java:242-288` |

> **【M6 澄清 · 评审 L-1】**：「存量读取**零变化**」**仅限「哨兵名或空名」两类路径**（上表前两行）；**第三行（非哨兵非空）不保证零变化**，须 P-4 预检 + V-1 核实。v1.0 §0 摘要与 §10.1 曾未加该限定，v1.1 已更正。

### 10.2 纯晚班站点历史 `period_index=0` 与 `ordinal=1` 错配

- 现网存量记录 `period_name='全天班'` → 命中哨兵①，**不受 `period_index` 错配影响**（`algo-payroll-shift.md:338`）；
- [推导] 若存在**非哨兵**记录（如「晚班」+ `period_index=0`），在纯晚班排班下会被三态③映射到**早班单元** → 与 `R`（晚班单元）交集为空 → 误判缺勤。此为**存量数据风险**，须 P-4 预检（§9.3）+ V-1 核实；**新口径下不再产生该类记录**（D-4 使 `period_index` = `ordinal`）。

### 10.3 「站点无班次」新边界

| 影响 | 处置 |
| ---- | ---- |
| 此前规则可独立存在（含默认单时段），**无班次也能打卡** | 改后**无启用班次 → 打卡拒绝**（`9113`） |
| 上线前缺口 | P-1 预检出「有规则无班次」站点，**上线前必须补齐班次**，否则该站全员无法打卡。**绑为 `B-1` 出口门禁：P-1 = 0**（§11，评审 M6′；**N4 修正**为前置批次出口门禁） |
| 前端体验 | `status` 新增 `shiftConfigured`；无班次时禁用打卡 + 引导（§8.4） |
| 新驿站 | `newRuleDefaults`（`AttendanceRuleServiceImpl.java:386-431`）现造默认时段 → 应改为「造默认班次」或提示先配班次（**后端改动**，登记） |

### 10.4 其它风险

| # | 风险 | 影响 | 缓解 |
| - | ---- | ---- | ---- |
| RK-1 | `GET /rule/list` 派生需逐站查班次（N+1） | 列表页耗时 | 批量预取全部班次（`in stationId`）后内存派生；驿站 <100（`boss-management-architecture.md:100`）→ 可接受 [推导]。**与 `toVO` 同一性能面（§7.2 L-3）** |
| RK-2 | 班次时间被改动 → 当日已打卡记录的 `period_name` 快照与新班次名不一致 | 展示轻微不一致 | 快照语义**不追改历史**（对齐既有快照口径）；如需一致由展示层以班次解释 |
| RK-3 | Mock 未同步 → 演示与真实后端分叉 | 演示失真 | **U-7 已裁定：mock 同步、demo 不同步** |
| RK-4 | 并行改造冲突（后端读取点 / 契约 / 前端多处） | 覆盖他人工作 | 按 §11 串行 + 各自分支 + 检查点（调度规则 A18/M04） |

### 10.5 打卡判定链路回滚

| 项 | 内容 |
| -- | ---- |
| 回滚触发 | 上线后出现「打卡大面积失败」或「计薪错位」 |
| 回滚动作 | 按 §9.4 **R-1~R-14** 反向 diff（含 `AttendancePeriodResolver`、全部读取/派生/校验点、`saveRule` 时段侧 D5/D6/D7）；恢复后 `resolve(rule)` 重新以 `check_periods` 为准（列未删，数据仍在） |
| 回滚前提 | **上线前不删 `check_periods`、不清空其值**（D-2 已保证） |
| 验证 | 恢复后抽查：打卡判定时间窗、`status.periods`、补卡、汇总数值与改造前逐位一致 |

### 10.6 显式假设清单（不成立后果，评审 M-5）

> v1.0 缺「显式假设」；本版逐条列出并给出**不成立后果**与**兜底**。

| # | 假设 | 不成立后果 | 兜底 / 判据 |
| - | ---- | ---------- | ----------- |
| AS-1 | 每站点**启用班次 ≤ 2** | 计薪单元 `epochDay×2+ordinal` 溢出/串号 | 班次定义侧校验（`9114`）；P-2 预检 |
| AS-2 | 每站点启用班次 `ordinal` **互异**（一早一晚） | `periodIndex` 重复 → `slotKey` 冲突、去重/计薪错乱 | 班次定义侧校验（`9114`）；P-3 预检；残余定级中高（§6.2） |
| AS-3 | 班次名**不与哨兵同名**（≠`全天班`） | 新记录被误判哨兵 → 实到虚增（M2） | 班次定义侧禁用名（`9114`，§6.4，**`trim` 后比对**）；**P-6 专查**（§9.3，**N1 更正**：原写「P-4 兼查」有误，P-4 查不到班次名） |
| AS-4 | 存量 `period_name` **全为哨兵或空** | 非哨兵非空记录在纯晚班日错配 → 误判缺勤 | P-4 预检 + V-1 核实（§4.3/§10.1） |
| AS-5 | 站点**有启用班次**（P-1=0） | 有规则无班次站点全员无法打卡 | **B-1 出口门禁：P-1=0**（§11，N4 修正）；`9113` 提示 |
| AS-6 | `start_time` 可解析 | `ordinal=-1` 该班次被排除，时段数减少 | 记 `WARN_SCHEDULE_SHIFT_MISSING`；不可解析班次由 `validateShift` 拦在定义侧 |

### 10.7 NFR：可用性降级链与可观测性声明（评审 M-7 补）

> v1.0 未声明降级链与可观测性，本版补齐。

**可用性 / 降级链**（真源统一后 `check_periods` 判定已移除，需明确各失败态的降级行为）：

| 场景 | 当前（v1.0 倾向） | v1.1 降级链 |
| ---- | ---------------- | ----------- |
| 站点 **0 启用班次** | 直接 `9113` 拒绝 | ① 前端用 `shiftConfigured=false` 置**空态**（禁用打卡 + 引导）；② 服务端 `9113` 明确提示；③ **不静默回退旧 `check_periods`**（避免真源回退、口径分裂） |
| **班次查询异常/超时** | 未定义 | 打卡/状态端点：**降级为「无时段」→ 9113/空态**，并记 `error` 日志；**不阻塞**其他站点。`GET /rule` 派生失败 → 对应派生字段返回 `null`/空数组（不抛 500） |
| **单班次回退基准**（`periodIndex=null`） | 取排班班次，无则 `rule.workStart/End` | 改为：取排班班次；无排班 → 取「首个启用班次」；无启用班次 → `9113` |
| 计薪侧派生失败 | 未定义 | `PayrollContextProvider` 现有「月份不可解析 → 返回 0」降级**保持**（`PayrollContextProvider.java:84-86`） |

**可观测性声明**（需在 B1 实现）：

| 指标 / 日志 | 内容 | 目的 |
| ----------- | ---- | ---- |
| `9113` 拒绝计数 | 按站点聚合「无启用班次打卡被拒」次数 | 上线后暴露未补齐班次的站点 |
| `9114` 拒绝计数 | 班次定义侧校验拒绝次数（保留名 / 超 2 / `ordinal` 冲突） | 暴露管理员误配 |
| `WARN_PERIOD_NAME_MISSING` | 沿用现有告警（`ShiftPayrollPolicy`） | 存量空名数据质量 |
| `WARN_SCHEDULE_SHIFT_MISSING` | 沿用现有告警 | 班次 `start_time` 缺失 |

> **安全面**：本维仅**声明**——本方案涉数据一致性为主，外部暴露面变化小；**`V24` 执行 / `hrm-server` 提交等 C 档动作的技术安全结论须由网络安全工程师出具**（P0.5，与 P0.6 并行不互替），本方案不作安全判定。

---

## 11. 问题 10：批次划分与门禁

> 依赖关系（**N4 修正后可判定**）：**B0（评审）→ B-1（预检 + 数据治理，前置）→ B1（后端 + 契约口径，与 B2 同批发布）→ B1′（数据层）→ B3（仅 mock 同步）→ B4（回归）**。C 档动作串行且须主智能体授权（调度规则 §6.1）。
> **改动说明（N4）**：① 原 v1.1 的 **B4（数据治理）前移为独立前置批次 `B-1`**，消除「B1 依赖 P-1=0（治理属 B4）却排在 B4 之前」的先后矛盾；② 原 **B5（回归）** 顺延为 **B4**（避免评测序号歧义）。

| 批次 | 内容 | 责任角色 | 入口门禁（依赖） | 出口门禁 |
| ---- | ---- | -------- | ---------------- | -------- |
| **B0** | **本方案技术评审**（L8 闸门） | 技术评审工程师 | — | 结论「通过 / 有条件通过」方可报主智能体；「打回」退回修订重评 |
| **B-1** | **前置：预检 + 数据治理**。预检 **P-1~P-3 / P-5 / P-6**；治理 **P-1** 补配启用班次、**P-2** 删减 >2、**P-3** 调整 `ordinal` 冲突、**P-6** 撞哨兵班次改名或停用 | 数据库工程师（预检，只读）+ 运维/运营（授权后治理） | B0 通过 | **出口门禁 = B1 入口门禁**：**P-1 = 0（阻塞）**；**P-2 = 0、P-3 = 0、P-6 = 0（阻塞）**；**P-4、P-5 报告并确认（非阻塞登记式）** |
| **B1** | 后端：`AttendancePeriodResolver` 派生改造 + **按值查找改造（M1）** + 读取点（打卡/状态/补卡）+ `toVO` + `defaultShift` + `newRuleDefaults` + `saveRule` 时段侧去除（**D5/D6/D7**；改动面见 §9.4 **R-1~R-14**） | 后端工程师 | B0 通过 + **U-1~U-7 已裁定**（§12.1）+ **B-1 出口门禁达成（P-1/P-2/P-3/P-6 = 0）** | 单测（本机无 JDK → 收敛静态 + 服务器验证命令）；**M1 三路径算例**（§4.5）通过；不改历史事实；`api.md` 正文由后端按 §7 补录 → **回本方案评审** |
| **B1′** | 数据层：`db.md` 列注释同步（标废弃）；如需 `V24`（V24-A/B 择一） | 数据库工程师 | B0 通过 | `V24` 执行属 **C 档**，须主智能体三步授权；PG 目录不动 |
| **B2** | 前端：`RuleCard.vue` / `attendanceRule.vue` 移除时段编辑 + 只读引导；`ShiftManager.vue` 补校验（含保留名禁用语，**`trim` 后比对**）；staff-h5 空态 | UI/UX 先行 → 前端工程师 | B1 契约定稿 + **U-3/U-4 已裁定** | UI/UX 只读态/空态规范冻结；`build/lint` 通过；四态覆盖（loading/empty/error/normal） |
| **B3** | **`packages/mock` 同步（U-7：mock 同步）** + **核验 `verify-mock.mjs` 断言**（N5）；`hrm-demo` **不同步** | 前端工程师 | B2 | `verify:mock` 基线不降；**`verify-mock.mjs` 断言变更须逐条说明理由并登记，不得改断言迁就实现**（N5） |
| **B4** | 回归 | 测试工程师 | B-1、B1、B1′、B2、B3 | 打卡（2 次/4 次/无班次/停用）、补卡、汇总（应到/实到/缺卡）、计薪折算、存量哨兵兼容 用例回归；门禁基线不弱化 |

> **B-1 的档位提示**：P-1~P-3/P-5/P-6 **预检为只读查询**；治理（补配/改班次）优先经**管理端业务操作**完成；若需**直接 SQL 批量改动生产数据**，属 **C 档**，须主智能体 §10.3 三步授权。

**发布顺序与上线前置约束（评审 M6′ 新增，逐条可判定）**：

| # | 约束 | 理由 |
| - | ---- | ---- |
| REL-1 | **B1 与 B2 同批发布**（或过渡期 B1 临时取「忽略 `checkPeriods`」兼容分支，待 B2 上线后再收紧为 U-5 的「400 拒绝」） | 现网 web `RuleCard.vue:266`、boss-h5 `attendanceRule.vue:155` 仍提交 `checkPeriods`；若 B1 先上且 U-5=400 → **现网规则保存全面 400** |
| REL-2 | **B-1 出口门禁 = P-1 预检 = 0**（有规则无启用班次的站点数须为 0；治理已在 B-1 完成，故 B1 无待办前置） | 否则该站全员无法打卡（§10.3）。**N4 修正**：由「B1 前置」改述为「B-1 出口门禁」，与批次先后一致 |
| REL-3 | **B1 前置含 U-1~U-7 全部裁定**（已满足，§12.1）；**B2 前置含 U-3/U-4**（已满足） | U-3/U-4 直接决定 B2 实现 |
| REL-4 | B3 只同步 `packages/mock`；**不改 `hrm-demo`** | U-7 裁定 |
| **REL-5** | **B-1 前置于 B1**；P-1/P-2/P-3/P-6 均为 **B-1 出口阻塞门禁** | **N4**：消除「B1 依赖 B4 产物」的先后矛盾；P-2/P-3 上线即触发计薪串号、P-6 上线即实到虚增，均属「上线即故障」类，故阻塞 |

**每批门禁要素（统一）**：① 是否涉 C 档（结构/数据/停服）→ 涉则先 §10.3 三步授权，**涉安全面先取网络安全工程师结论**（P0.5）[推导：本方案主要涉数据一致性，涉安全面低，但 `V24` 执行 / B-1 直连 SQL 治理仍走 C 档]；② 回归范围（打卡/补卡/汇总/计薪）；③ 回滚点（§9.4 **R-1~R-14** / §10.5）。

---

## 12. 口径裁定 / 待核实清单

### 12.1 口径裁定（**已裁定**，不得再标「待裁定」）

> 主代理已裁定，本方案须落位并据此实现（调度规则 P1 / §11.4）。

| # | 口径问题 | **裁定结论（已定）** | 落位 |
| - | -------- | ------------------- | ---- |
| **U-1** | 时段**粒度**：站点级 vs 员工级 | **站点级**（该驿站启用班次全集） | §0-2、§2 D-3、§3 |
| **U-2** | 员工当日**无排班**是否仍可打卡 | **可**（时段取站点启用班次，保持现状可用性） | §3.1、§7.4、§10.7 |
| **U-3** | boss-h5「去维护班次」引导落点 | **引导指向网页端维护班次**（文案「请到网页端维护班次」） | §8.2 |
| **U-4** | `check_frequency` 字段去留 | **保留、只读派生**（不删列） | §3.2、§7.2、§8.1 |
| **U-5** | `PUT /rule` 收到 `checkPeriods` 非空 | **拒绝**：回 **通用 `400`**（`ErrorCode.BAD_REQUEST`）「时段已由班次决定，请维护班次」；**非** 91xx 码（**N7 明示**，对齐 `api.md:797`） | §7.1、§7.7、§11 REL-1 |
| **U-6** | 班次定义侧校验落点与错误码 | **新增班次定义侧校验错误码 `9114`**（不复用排班侧 `9111`/`9112`） | §3.2、§6.4、§7.7、§8.3 |
| **U-7** | `hrm-demo` 镜像 / `packages/mock` 同步 | **`hrm-demo` 不同步（仅登记）；`packages/mock` 同步** | §8.5、§10.4 RK-3、§11 |

### 12.2 待核实（事实类，须实测/查库）

| # | 待核实 | 影响 | 核实方式 | 责任人 / 时限（评审 L-6） |
| - | ------ | ---- | -------- | ------------------------- |
| **V-1** | 存量 `attendance_record.period_name` 分布（是否存在非哨兵非空值） | §4.3/§10.2 存量错配风险 | P-4 预检（查库） | 数据库工程师；**B-1 出口**（与 REL-2 同批） |
| **V-2** | 现网每驿站**启用**班次数与 `ordinal` 分布 | §6.3 残余风险 | P-2/P-3 预检（查库） | 数据库工程师；**B-1 出口**（与 REL-5 同批） |
| **V-3** | `middayBoundaryMinute` 是否需 UI 提示「12:00 起算晚班」 | §6.3、§8.3 | 产品确认 | 主智能体转产品；**B2 动工前**（否则按「不加提示」实现，列为 `TODO(扩展)`） |

### 12.3 已复核订正（§0.1 摘要）

- **订正 1**：R11 登记出处应为 `boss-management-architecture.md:457`（非 `update-log.md:42`）。
- **补充 1**：**补卡为第三读取点**（`AttendanceMakeupServiceImpl.java:121,208`），用户背景未列。
- **补充 2**：`hrm-demo` 同名镜像与 `packages/mock` 独立真源需一并评估（§8.5、U-7）。
- **订正 2（v1.1 新增）**：v1.0「读取点全集仅 3 处」表述不完整 → 已重建为 §0.2 唯一台账。
- **订正 3（v1.1 新增）**：v1.0「`PUT` 入参 + `periods.get` 取值 + 哨兵名」三处与实测/口径不符 → 见 M1/M2/M4 响应（§0.0.3）。
- **订正 4（v1.2 新增）**：v1.1 的 P-4「兼作 M2 残余预检」表述有误（P-4 查不到哨兵名班次）→ 新增 **P-6** 并更正 P-4（N1，§9.3）。
- **订正 5（v1.2 新增）**：v1.1 禁用名比对未归一化（可被空白绕过）→ 明确 trim 后比对（N2，§6.4/§8.3）。
- **订正 6（v1.2 新增）**：v1.1 回滚清单漏 D5/D6/D7 → 补 R-12~R-14（N3，§9.4）。
- **订正 7（v1.2 新增）**：v1.1 批次序与 B1 前置门禁矛盾 → 前移治理为前置批次 B-1（N4，§11）。
- **订正 8（v1.2 新增）**：v1.1 未登记 Mock 门禁资产 `verify-mock.mjs` → 补登记（N5，§8.5）。
- **订正 9（v1.2 新增）**：v1.1 §6.2 行号 `ShiftPayrollPolicy.java:74` 与 §6.3 `:534` 系笔误 → 按实测统一为 `:75` 与 `:535`（N6，§6.2/§6.3）。
- **订正 10（v1.2 新增）**：v1.1 U-5 拒绝未明示错误码 → 明示通用 `400`（N7，§7.1/§7.7）。
- **订正 11（v1.2 新增·行号精度）**：`AttendanceConstants.java:65` → **`:64`**（`DEFAULT_PERIOD_NAME`，实测在 `:64`，`:65` 为空行），按源码校正（§6.4、§13）。
- **订正 12（v1.2 新增·自述口径）**：§0.2「已覆盖 27」与首评「20 处」映射**显式标注**（**27 = 20 + E 组 7**）；§4.5 补卡算例**补 `checkType`**（取值随打卡类型，见 §4.4/§4.5）。

---

## 13. 依据索引（可核）

| # | 依据 | 位置 |
| - | ---- | ---- |
| 1 | 班次实体字段 | `hrm-server/.../entity/AttendanceShift.java:19-53`；`db.md:635` |
| 2 | 规则实体与 `check_periods` | `entity/AttendanceRule.java:20-21,66-68`；`entity/CheckPeriod.java:13-23`；`db.md:607,621,623` |
| 3 | 建表（两表独立、无外键） | `db/migration/mysql/V5__attendance.sql:22-65,82-108` |
| 4 | `ordinal` 派生 | `service/finance/support/ShiftPayrollPolicy.java:64-79`（判定行 `:75`）；界值 `config/AlgoProperties.java:121` |
| 5 | 记录→班次三态优先级 | `ShiftPayrollPolicy.java:141-189`（`:183-186` 为 `period_index` 分支） |
| 6 | 打卡判定与写入 | `service/attendance/impl/AttendanceRecordServiceImpl.java:316-345,385-393` |
| 7 | 今日状态 | `AttendanceRecordServiceImpl.java:426-482`（`:447` 时段展开） |
| 8 | 补卡读取点 | `service/attendance/impl/AttendanceMakeupServiceImpl.java:121,208,225,231-232` |
| 9 | 汇总口径 | `service/attendance/support/AttendanceSummaryPolicy.java:62-129` |
| 10 | 时段解析（唯一解析器） | `service/attendance/support/AttendancePeriodResolver.java:54-72` |
| 11 | 规则保存重算派生值 | `service/attendance/impl/AttendanceRuleServiceImpl.java:100-103,143-147` |
| 12 | 班次列表/兜底班次 | `service/attendance/impl/AttendanceShiftServiceImpl.java:44-58,140-152` |
| 13 | 排班 `ordinal` 互异校验（9112）与上限（2） | `service/attendance/impl/AttendanceScheduleServiceImpl.java:65-70,244-256,476-482,526-540` |
| 14 | 错误码 9101-9112（含 9110/9111/9112） | `enums/ErrorCode.java:182-218`（`9113`/`9114` 为本方案**拟新增**） |
| 15 | D-6 决策（不补班次时段字段） | `docs/boss-management-architecture.md:104,201,427` |
| 16 | R10 / R11 风险 | `docs/boss-management-architecture.md:456-457` |
| 17 | 待确认 Q-DB-11（班次时段字段，留待 V24+） | `docs/db.md:1687,1744` |
| 18 | 契约（rule/status/records/makeup/**shifts**） | `docs/api.md:800-898,900-936,938-956,1034-1058,1060-1099`（班次前缀 `/api/v1/shifts`，`ShiftController.java:28`） |
| 19 | 班次/记帐口径既有决议 | `docs/algo-payroll-shift.md:36,65,92-98,135,338` |
| 20 | Mock 与真实后端口径分叉先例 | `docs/api.md:997-1006` |
| 21 | B7b 任务记录 | `docs/update-log.md:40-53` |
| 22 | 前端：规则卡 / boss-h5 规则页 / 班次管理 / 时段卡 | `hrm-clients/apps/web/src/views/attendance/components/RuleCard.vue:327-367`、`hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue:478-516`、`hrm-clients/apps/web/src/views/schedule/components/ShiftManager.vue:69-93`、`hrm-clients/apps/staff-h5/src/views/staff/attendance/components/PeriodCard.vue:33-56` |
| **23** | **计薪侧读取点（M7 遗漏①，透传）** | `service/finance/impl/PayrollContextProvider.java:92,121-122` |
| **24** | **解析器测试读取点（M7 遗漏②，B1 需适配）** | `src/test/.../support/AttendancePeriodResolverTest.java:44-57` |
| **25** | **哨兵常量与班次名校验（M2）** | `config/AlgoProperties.java:121-123`；`service/attendance/support/AttendanceConstants.java:62,64`（`DEFAULT_PERIOD_NAME` 在 `:64`，**N6/订正 11 按实测校正**）；`AttendanceShiftServiceImpl.java:157-159`（**N2**：`validateShift` 归一化后比对）；`ShiftPayrollPolicy.java:168-171` |
| **26** | **Mock 侧门禁资产（N5 补登记）** | `hrm-clients/scripts/verify-mock.mjs`（旧语义断言示例 `:1934-1941,2106-2109`）；Mock 侧口径分叉登记 `api.md:999` |
| **27** | **N1 预检 P-6 / 计薪哨兵配置** | `AlgoProperties.java:121-123`（`legacyPeriodSentinel`）；`AttendanceConstants.java:64`（`DEFAULT_PERIOD_NAME`）；`ShiftPayrollPolicy.java:167-171` |

---

## 14. 报审声明

1. 本方案（**v1.2**）为**方案阶段产物**，依调度规则 **P0.6 / R25 / L8**，**须先经技术评审工程师评估**（六维：依据充分性 / 边界合理性 / NFR 覆盖 / 复杂度论证 / 假设与风险登记 / 契约一致性），结论「通过 / 有条件通过」方可报主智能体审批。**评估沿革**：v1.0 = 初评「打回」（M1~M7）；v1.1 = 复评「**有条件通过**」（必改 **N1~N7**）；**v1.2 为按 N1~N7 修订的定稿轮，须复评确认闭环**（评估结论绑定版本）。
2. 本方案**未写实现代码、未写迁移脚本正文、未改 `api.md`/`db.md` 正文、未改其它设计文档、未执行 git/部署/MCP、未连库、未运行测试**（本机无 JDK/MySQL）。所有源码结论均以 `文件:行号` 为依据，推导处标 `[推导]`，未能实测处标「待核实」。**N6 说明**：复评所述「`ShiftPayrollPolicy` 内联三元在 `:74`」与源码不符（实测在 `:75`），本版按「求证优先」取实测值并保持 §6.2/§6.3 一致。
3. 本方案**不构成任何 C 档动作授权**；`V24` 执行、`hrm-server` 提交、B-1 直连 SQL 治理均须按权限分档由主智能体授权；**涉安全面的 C 档动作须先取网络安全工程师技术结论**（P0.5，与 P0.6 并行不互替）。
4. 口径 **U-1~U-7 已全部裁定并落位**（§12.1），不再有「待裁定」项；实现须与裁定一致（含 **U-7 demo 不同步、mock 同步**）。
5. **未改 `update-log.md`**（依任务硬性约束「只编辑本方案文档」）；变更记录建议由主智能体补记或由后续批次同步。
