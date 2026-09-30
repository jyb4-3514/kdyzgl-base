# algorithm-payroll-scheduling.md · 薪资自动算薪调度与补跑（算法四件套）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.3（依技术评审复评 复-1/复-3/复-4 + 主代理统一裁定修订，待技术评审重评）** |
| 编写日期 | 2026-09-27 |
| 修订摘要 | **v1.3**：依 [tech-review-payroll-automation.md](tech-review-payroll-automation.md) **复评（方案 v1.4 ｜ 2026-09-27，结论「有条件通过」）** 之**新发现必改 复-1/复-2/复-3/复-4** 与**主代理统一裁定**修订——① **复-1**：`stale-reclaim-enabled` 默认 **`true`** 定稿（纠正方案 v1.4 的 `false`，与本文 v1.2 一致，现已统一）；**僵死回收动作完整定义**：僵死行 `claim_key=NULL` + `status=FAILED` + `fail_reason=STALE_RECLAIMED` → **释放占位、允许次日重试**，且**必须触发失败告警**；补「回收 → 次日可插入」与 `uk_claim`/`uk_attempt` 交互时序（§1.5.3 / §1.4 / §3 / §13）；论证**单实例回收安全**（`fixedDelay` 不重叠 → 无并发执行 → 超时即真僵死），保留 W1/W2（多实例仍登记架构债）。② **复-3**：补跑触发时刻**统一为 `catch-up-time-of-day`（默认继承该站 `payroll_time`）**，判据 =「`now` ≥ **当日** `catch-up-time-of-day` **且**该 `(station_id, target_month)` **当日尚未尝试**」；修正 v1.2「次日同钟点」表述。③ **复-2**：`force` 参数**已由主代理裁定删除**（同日重复一律由 `uk_attempt` 硬拒绝回 `9410`），本文件清除 `force` 语义引用。④ **复-4**：**复核并最终确认参数清单**（11 项单真源），显式列相对 v1.2 的增删改，清除残留已删项（`catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run` / `force`）；另清理 §1.3/§1.5 对已删索引 `idx_payroll_run_station_month` 的引用（改指 `uk_attempt` 最左前缀）。落点：**§14（整改响应）**、§0、§1.3、§1.4 / §1.4.1、§1.5 / §1.5.3、§2、§3、§4.6、§6、§10、§13。**本文版本变更须重评（L8）。** **v1.2**：依**用户裁定**修订业务口径——① **U-05**：算薪日 `payroll_day` 取整范围 **1..31**，当月无该日时**钳位到当月最后一天**正式确认（原 §7 R4 口径甲定稿）；② **U-06（重大变更）**：补跑模型由「`[dueAt, dueAt+72h]` 小时窗 + `max-retry-per-run=3` 上限」改为「**日粒度重试**」——算薪日当天未成功即**次日补跑**，补跑再失败则**次日继续补跑**，以自然日为粒度持续至成功；**同一驿站同一账期每自然日至多尝试一次**；`target_month` 恒为 `dueAt` 所在月。落点：§0 摘要、§1.1 约束、§1.3 判定链、§1.4 / §1.4.1、§1.5（claim 与「每日至多一次」关系）、§2 参数表、§3 边界用例、§4 负载、§5 依据、§6 指标、§7 R4/R5/R6、§8 契约、§9、§10 摘要；**落地逐条响应 = §12**，**风险与补偿建议 = §13**（只建议不推翻用户口径）。**v1.1**：依 [tech-review-payroll-automation.md](tech-review-payroll-automation.md) **必改 4 / 必改 8** 修订（僵死回收竞态 W1、手工路径竞态 W2、窗口塌缩量化），说明见 §11；**其中「窗口塌缩」相关结论在 v1.2 日粒度口径下作废并改写（§1.4.1 / §12.3）。** **本文版本变更须重评（L8）。** |

| 产出角色 | 算法工程师 `express-station-algorithm-engineer`（只出算法方案；不写业务实现代码、不写迁移 SQL、不改现有代码、不执行 git/部署/MCP） |
| 上游方案（真源） | [payroll-automation-design.md](payroll-automation-design.md) v1.0（架构师）；重点 §1 ADR-01、§3.3、§5 |
| 关联契约 | [api.md](api.md)（财务段，架构方案 §4 声明待 B0 补录）、[db.md](db.md)、[algo-hrm-server.md](algo-hrm-server.md)（§11 参数体例） |
| 状态 | **方案阶段产物**。依 `项目规则1.md` §8 第 8 条与调度规则 **P0.6 / R25 / L8**，本文报主智能体审批与实施前**必须先经技术评审工程师（`express-station-tech-reviewer`）评估通过（通过 / 有条件通过）**；结论「打回」退回本算法工程师按必改项修订后重评。**本文不宣称已通过评审。** |
| 边界 | 只回答「用什么算法、为什么、复杂度多少、参数怎么配、边界怎么兜、指标是多少」；不含 Java 代码、DDL、前端、部署 |

### v1.2 变更摘要

| 项 | 内容 |
| ---- | ---- |
| 来源 | **用户裁定**（口径红线 §11.4 / P1；由主智能体转达） |
| 变更点 1（U-05） | 算薪日范围 **1..31**；当月无该日（如 31 日遇 2 月）→ **钳位到当月最后一天**同日同一时刻执行。原文 §1.2 口径甲由「建议」升为「定稿」。 |
| 变更点 2（U-06） | 补跑 = **自然日粒度重试**：当天未成功 → 次日补；再次失败 → 次日再补；**每自然日至多一次**（禁用同日 tick 反复重试）；**无窗口上限、持续至成功**；补跑的是原账期工资单（`target_month = dueAt 所在月`），仅执行日推后。 |
| 落点章节 | §1.4（触发与补跑模型，重写）、§1.4.1（月末/钳位/跨月验算，改写）、§1.5 / §1.5.1 / §1.5.2（claim 与「每日至多一次」关系）、§2（参数集重列：删 3 / 新增 3 / 语义变更 3）、§3（边界用例）、§4（补跑负载）、§6（指标）、§12（落地响应）、§13（风险与补偿建议） |
| 结构影响 | **需 `payroll_run` 新增 `attempt_date DATE` 列 + 新增 `UNIQUE (station_id, target_month, attempt_date)`**（「每日至多一次」硬防线）；交架构师 / 数据库工程师（本文件不落 DDL，见 §1.5 / §12.2） |
| 作废 | 见 **§12.3 因口径变更而失效的旧结论清单**（12 条） |

### v1.3 变更摘要

| 项 | 内容 |
| ---- | ---- |
| 来源 | 技术评审**复评**（[tech-review-payroll-automation.md](tech-review-payroll-automation.md) §七，结论「有条件通过」）**复-1/复-2/复-3/复-4** + **主代理统一裁定**（§11.4 / P1，与架构师 v1.5 口径一致） |
| 变更点 1（复-1） | `stale-reclaim-enabled` 默认 **`true`** 定稿；**僵死回收动作完整定义**（`claim_key=NULL` + `status=FAILED` + `fail_reason=STALE_RECLAIMED`，释放占位 + 允许次日重试 + **必须触发失败告警**）；补回收与 `uk_claim`/`uk_attempt` 交互时序；论证单实例回收安全；保留 W1/W2 多实例债。 |
| 变更点 2（复-3） | 补跑触发时刻**统一为 `catch-up-time-of-day`（默认继承该站 `payroll_time`）**，判据 =「`now` ≥ **当日** `catch-up-time-of-day` **且** 该 `(station_id,target_month)` **当日尚未尝试**」；**修正 v1.2「次日同钟点」表述**。 |
| 变更点 3（复-2） | `force` 参数**由主代理裁定删除**；同日重复一律由 `uk_attempt` 硬拒绝回 `9410`；本文件清除 `force` 语义引用。 |
| 变更点 4（复-4） | **最终确认参数清单**（11 项，单真源），逐项给「名 / 含义 / 默认值 / 范围 / 影响」，显式列相对 v1.2 的增删改，清除残留已删项。另清理已删索引 `idx_payroll_run_station_month` 的引用。 |
| 落点章节 | §0（摘要）、§1.3（判定链）、§1.4 / §1.4.1（时刻口径）、§1.5 / §1.5.3（回收时序与唯一键交互）、§2（参数表）、§3（边界用例）、§4.6（回收扫描成本）、§6（指标）、§10（结论摘要）、§13（风险）、**§14（整改响应）** |
| 结构影响 | 无新增结构需求（回收动作仅**更新既有 `payroll_run` 行**的 `claim_key`/`status`/`fail_reason`；`fail_reason` 为既有列；`STALE_RECLAIMED` 为**取值**，非新列） |

> **四件套索引**：① 问题建模与流程 = §1；② 算法选型与复杂度 = §1.7 + §4；③ 依据来源 = §5；④ 可验证指标与基准数据 = §6。
> **对上游方案的修正建议**单列于 **§7**（12 条）。
> **技术评审整改响应（必改 4/8）**单列于 **§11**（含参数复核结论与衍生风险）。
> **用户裁定 U-05 / U-06 落地响应**单列于 **§12**（含「因口径变更而失效的旧结论清单」）；**风险与补偿建议**单列于 **§13**（只建议、不推翻用户口径）。
> **技术评审复评整改响应（复-1/复-2/复-3/复-4）**单列于 **§14**（含「因本次修订而作废的旧表述」）。
> **取证声明**：本机无 JDK / MySQL / Redis，**未运行任何构建、测试或 SQL**；全部数值除注明出处外均为**按公式推导**，标 `[推导]`。结论均标 `文件:行号`。

---

## 0. 摘要（结论先行）

1. **调度模型**：无状态「到期判定 + 抢占式认领（claim）+ 逐驿站串行 + **日粒度补跑（每自然日至多一次）**」。选型沿用上游 ADR-01 的进程内 `@Scheduled` 固定间隔轮询（`fixedDelay`）。
2. **`dueAt` 计算**：`dueAt = ZonedDateTime.of(Y, M, min(payroll_day, lengthOfMonth), hh, mm, 0, 0, Asia/Shanghai)`；**月末缺日采用「钳位到当月最后一天」**（**U-05 已裁定，定稿**；上下游原未定义，见 §7 R4）。
3. **幂等**：把上游 `payroll_run` 的「仅 SUCCESS 写哨兵」升级为 **claim 槽位**——`RUNNING` 与所有**终态未成功/已成功**（`SUCCESS`/`SKIPPED`）均占同一唯一键槽位，仅 `FAILED` 释放以允许重试。这是重叠 tick 与未来多实例下**唯一的硬防线**（**前提：无僵死回收误判且手工路径串行**，否则见 §1.5.1/§1.5.2；见 §1.5、§7 R2）。**W1 残余（回收误判）不可由该唯一键消解**——关键是唯一键的拦截滞后于重复落库（§1.5.1）。
4. **关键事实纠正**：`idx_payroll_emp_month_bill` 是**普通索引（`KEY`）而非唯一索引（`UNIQUE KEY`）**（[V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85)）→ 「单据级幂等靠该索引」**不成立**（见 §0.2 ①、§7 R1）。
5. **补跑（U-06 定稿，日粒度重试）**：算薪日当天未成功 → **次日补跑**；补跑再失败 → **次日再补**，以自然日为粒度**持续至成功**；**同一驿站同一账期每自然日至多尝试一次**（禁用同日 tick 反复重试）；**无窗口上限**——原「`[dueAt, dueAt+72h]` 小时窗、窗口内自动 / 窗口外手工」口径**作废**（见 §12.3）；补跑的是原账期工资单（`target_month` 恒为 `dueAt` 所在月），仅执行日推后；**不回溯更早账期**。**默认不启用限速桶**（10min tick 本身即节流），仅提供「驿站间串行间隔」节流参数（与 `hrm.algo` 风格对齐）。
6. **发现上游算法口径缺陷 12 条**（必改 6 / 建议 5 / 待裁定 1），见 §7。**其中 R4（算薪日范围/钳位）、R5（补跑窗口与跨月）已由用户 U-05 / U-06 裁定，见 §12.1；R5 在新口径下由「窗口」概念消解。**
7. **量级**：`S=50, E=20, R=8` → 单驿站 180 行单行 insert，全量 9000 行；串行单轮 `[推导]` ≈ 5–6s，远小于 10min tick。批量插入优化在 `S×E×R > 5×10⁴` 量级才必需（§4）。
8. **v1.1 修订（响应技术评审必改 4/8）**：① **W1**——`claim` 唯一键只在**无僵死回收误判**前提下保证「至多一个 `RUNNING/SUCCESS`」；给出 7 步时序证明「回收误判 → 重复工资单」可发生，处置口径 = **owner 标识 + 终态回写并入业务事务 + 行锁复核**，`stale-reclaim-enabled=false` 仅在**多实例前 / 主动放弃自动回收**时作兜底（**v1.3 起单实例默认 `true`**，见 §1.5.3）。（§1.5.1）② **W2**——手工 `item-add` 不经 claim，与自动重建并发可删 MANUAL 项/产生孤儿，默认「自动路径对存在 `DRAFT` 的 `(s,m)` 一律保护性 `SKIPPED`」（§1.5.2）。③ **窗口塌缩**——`min(dueAt+W, 次月首日00:00)` 使月末 `d0` 有效窗口可短至 **1 分钟**（恒 > 0，非空），引入 `catch-up-window-min-hours` 下限保障（§1.4.1）。**（注：该③的窗口塌缩分析与 `W` / `W_min` 在 v1.2 日粒度口径下整体作废，见 §12.3。）**
9. **v1.2 修订（依用户 U-05 / U-06 裁定）**：① **U-05** 钳位口径定稿（§1.2）；② **U-06** 补跑改为**日粒度重试**——次日补、失败次日再补、**每自然日至多一次**、无窗口上限、持续至成功，`target_month` 恒为 `dueAt` 月（§1.4 重写）；③ 「每日至多一次」落地 = `payroll_run` **新增 `attempt_date` + `UNIQUE(station_id, target_month, attempt_date)`**，与既有 `uk_payroll_run_claim` **正交协同**（§1.5 / §12.2）；④ 参数集调整：**删 3 / 新增 3 / 语义变更 3**（§2）；⑤ 新增告警/熔断建议（§13，只建议不推翻用户口径）。
10. **v1.3 修订（响应技术评审复评 复-1/复-3/复-4 + 主代理裁定）**：① **僵死回收完整定义**——回收动作 = `claim_key=NULL` + `status=FAILED` + `fail_reason=STALE_RECLAIMED`，**释放 `uk_claim` 占位、允许次日（新 `attempt_date`）重试、并触发失败告警**（消除复评 §三 3.2「RUNNING 僵死未回收 → `uk_claim` 永久占位 → 自动补跑中止且无 `FAILED` 行 → 告警不触发 → 静默失效」，§1.5.3 / §3 #25~#27 / §13 R-C1）；② **补跑触发时刻统一**（`catch-up-time-of-day`，默认继承 `payroll_time`，判据含「当日尚未尝试」，§1.4）；③ **`force` 参数删除**（主代理裁定，同日重复由 `uk_attempt` 硬拒绝回 `9410`，§1.4 / §8）；④ **参数清单为单真源**（11 项，§2）。

### 0.1 与本机实测的核对（上游材料 → 实测）

| # | 上游说法 | 实测 | 出处 |
| - | -------- | ---- | ---- |
| ① | 任务/上游称「单据级幂等靠 `idx_payroll_emp_month_bill`」 | **不成立**：该列声明为 `KEY idx_payroll_emp_month_bill (employee_id, month, bill_type)`，**非 `UNIQUE KEY`**，无唯一约束，不阻止重复插入 | [V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85) |
| ② | 算薪入口 `PayrollService.generate(request)` 逐行 insert | **成立**：每员工 1 次主单 insert + R 次明细 insert，均单行 | [PayrollServiceImpl.java:524](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L524)、[:527-538](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L527-L538) |
| ③ | 9405 阻断判定为「账期全局级、不带 stationId」 | **成立**：`selectList` 仅按 `month + billType=MONTHLY` 查全月单，再整批判定 | [PayrollServiceImpl.java:154-162](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L154-L162)、[PayrollGenerateGuard.java:33-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L33-L52) |
| ④ | `station` 有启停位 | **成立**：`status TINYINT NOT NULL DEFAULT 1`，含 `idx_station_status` | [V1__init_schema.sql:34,41](../hrm-server/src/main/resources/db/migration/mysql/V1__init_schema.sql#L34) |
| ⑤ | 每员工算薪上下文取数次数 | **实测约 5–6 次 SELECT/员工**：定薪 1 + 考勤记录 1 + 排班 1 + 班次 1 + KPI 1 + 已批请假班次 ≈1 | [PayrollContextProvider.java:69-74](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollContextProvider.java#L69-L74)、[:91-101](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollContextProvider.java#L91-L101) |
| ⑥ | `station_payroll_setting.enabled` 可被轮询过滤 | **成立但无索引**：架构方案 §3.1 只给 `KEY idx_station_payroll_setting_station`，`enabled` 无索引（小表可接受，S 大时需补） | [payroll-automation-design.md:269-272](payroll-automation-design.md) |
| ⑦ | 算薪规则来源 | 未定义：`generate` 未传 `ruleId` 时取**第一个启用规则**（全局，非按驿站） | [PayrollServiceImpl.java:149,556-563](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L149) |

---

## 1. 算法方案（问题定义 + 模型 + 流程）

### 1.1 问题定义与符号

**目标**：对每个启用自动算薪的驿站，在其「算薪日 + 时间」到点后，**至多一次成功**生成该账期工资单草稿并推管理员审核；算薪日当天未成功（进程停机 / 未启动 / 执行失败）时，**自次日起按自然日粒度补跑——每自然日至多尝试一次、持续至成功**；单驿站故障不波及其它驿站。

| 符号 | 含义 | 出处/取值 |
| ---- | ---- | ---- |
| `Z` | 判定时区 | 默认 `Asia/Shanghai`（`hrm.payroll.schedule.zone`） |
| `now` | `ZonedDateTime.now(Z)` | 每 tick 取一次，全轮复用 |
| `m` | 待评估账期 `yyyy-MM` | `YearMonth.from(now)` |
| `s` | 一个启用自动算薪的驿站 | `station_payroll_setting.enabled=1 ∧ station.status=1` |
| `d0` | 配置算薪日 | `station_payroll_setting.payroll_day` |
| `hh:mm` | 配置算薪时间 | `station_payroll_setting.payroll_time` |
| `dueAt(s,m)` | 应执行时刻 | 见 §1.2 |
| `D` | 尝试日 `attempt_date`（自然日，由 `Z` 墙钟派生） | **v1.2 新增**；「每日至多一次」的唯一键维度（§1.5） |
| ~~`W`~~ | ~~补跑窗口~~ | **v1.2 删除**：日粒度重试**无窗口上限**（§1.4） |
| `E_s` | 驿站 `s` 的在职员工数 | `employee.status=1`（[PayrollServiceImpl.java:584-598](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L584-L598)） |
| `R` | 启用规则项数 | `enabled=1` 的 `payroll_rule_item` 数 |
| `Q` | 每员工上下文查询数 | 实测 ≈5–6（§0.1 ⑤） |

**约束**：
1. 全部判定**不依赖 JVM 默认时区**，只用 `Z`；落库时间亦须用 `Z` 派生（§7 R7）。
2. 同一 `(s, m)` **至多一次成功生成**，且达成 `SUCCESS` 后**不再产生新尝试行（跨日亦不重试）**——**成立前提**：**无僵死 `RUNNING` 回收误判**且**手工写路径与自动路径串行**（前提不成立时的竞态 W1/W2 见 §1.5.1 / §1.5.2；兜底见 §11.1）；
3. 生成失败可重试，但**同一自然日至多一次**、跨日不限次数（持续至成功）；**无总次数硬上限**（U-06 裁定；补偿性告警阈值见 §13）；
4. 单驿站异常**不得**中断整轮；
5. 所有阈值外置（`hrm.payroll.schedule.*`），代码内零内联常量（反模式 A03）。

### 1.2 触发判定模型（`dueAt`）

**计算式**（`m = (Y, M)`，`L = YearMonth.lengthOfMonth() `）：

```
d_eff = clamp(d0, 1, L)                                  // 口径甲：月末钳位（U-05 定稿）
dueAt = ZonedDateTime.of(Y, M, d_eff, hh, mm, 00, 000000000, Z)
```

| 边界 | 规则 | 例 |
| ---- | ---- | ---- |
| `d0 > L`（如 31 日遇 30 天月、30 日遇 2 月） | **钳位到 `L`**（当月最后一天同一时刻） | `d0=31, 2026-04` → `2026-04-30 hh:mm`；`d0=30, 2026-02` → `2026-02-28` |
| 2 月 29 日（闰年） | `L=29`，`d0=29` 正常命中 | `d0=29, 2024-02` → `2024-02-29` |
| 2 月 29 日（非闰年） | `L=28`，钳位 | `d0=29, 2026-02` → `2026-02-28` |
| 跨月 | `Y/M` 由 `now` 的 `m` 决定，无跨月累计状态 | 每月独立计算，天然滚动 |
| 跨年 | `m` 含年，`M=1` 自动切年 | `2027-01` |
| 夏令时 | **`Asia/Shanghai` 无 DST**（自 1991 年起全年固定 UTC+8），无跳变 | 见 §5 依据 |
| `payroll_time` | `HH:mm`，24 小时制，`00:00–23:59`；非法 → 该驿站配置非法，记 `SKIPPED`（不重试） | `"9:00"` 非法 |

**为什么用钳位（口径甲）而非「当月无该日则跳过」（口径乙）**：钳位保证「每月恒定有且仅有一个 `dueAt`」，调度器无状态、可解释、无「某月静默不计薪」的隐性缺口；口径乙会让 31 日配置在 4/6/9/11 月**永久漏跑**，属高风险默认。**该口径已由用户 U-05 裁定为「钳位」，本文采纳、定稿生效**（上游原 U-05 未定，见 §7 R4 / §12.1）。

### 1.3 轮询扫描模型（伪代码）

```
tick(now):                                   // 每 tick 由 @Scheduled(fixedDelay=interval) 触发
  if (!cfg.enabled) return                   // 总开短路（默认 false）
  if (cfg.staleReclaimEnabled) reclaimStale(now)   // v1.3：先做僵死回收扫描，动作定义见 §1.5.3
                                             //   UPDATE payroll_run SET status=FAILED, claim_key=NULL,
                                             //     fail_reason='STALE_RECLAIMED' WHERE status='RUNNING'
                                             //     AND start_time < now - runningTimeout → 释放 uk_claim → 允许次日重试
                                             //   回收命中即触发失败告警（受 notify-on-fail 管控，§13 R-C1）
  m = YearMonth.from(now)
  list = SELECT * FROM station_payroll_setting sts
         JOIN station st ON st.id = sts.station_id
         WHERE sts.enabled = 1 AND st.status = 1 AND sts.is_deleted = 0
  list.sort(by: (dueAt(s,m) asc, s.id asc))  // 稳定全序 → 结果可复现
  processed = 0
  for (s in list):
     if (cfg.maxStationsPerTick > 0 && processed >= cfg.maxStationsPerTick):
        log("tick 达单轮上限，剩余顺延"); break        // 顺延，不丢弃（下 tick 续跑）
     r = executeIfDue(s, m, now)
     if (r != null) processed++                       // 真正执行（含失败）才占配额
     if (cfg.interStationDelayMs > 0 && 还有后续) sleep(cfg.interStationDelayMs)

executeIfDue(s, m, now):                     // 单驿站判定链，短路优先（v1.3：日粒度重试 + catch-up-time-of-day，U-06/复-3）
  dueAt = computeDueAt(s, m)                              // O(1)，含月末钳位（§1.2）
  day   = now.toLocalDate()                               // 尝试日 attempt_date（§1.5）
  tAt   = attemptTimeOf(day, s)                           // 当日尝试钟点（§1.4，复-3 统一口径）
                                                          //   day == dueAt 日 → payroll_time；day > dueAt 日 → catch-up-time-of-day（默认继承 payroll_time）
  if (day < dueAt.toLocalDate()) return null              // 短路① 未到算薪日
  if (now < tAt) return null                              // 短路② 当日尝试时刻未到（复-3：统一 catch-up-time-of-day）
  if (existsSuccess(s.id, m)) return SKIPPED(已成功)      // 短路③ 该账期已成功 → 跨日永久停
  if (existsTerminalSkip(s.id, m)) return SKIPPED          // 短路④ 业务已终止（9405/CONFIG_INVALID/DRAFT 保护）→ 永久停
  claim = tryClaim(s.id, m, day, dueAt, now)              // INSERT RUNNING；硬防线 = uk_attempt(s,m,day) ∧ uk_claim(s,claim_key)（§1.5）
  if (claim.duplicate) return null                        // 短路⑤ 今日已尝试过（uk_attempt）/ 已有占位（uk_claim）→ 拒绝（复-3「当日尚未尝试」由 uk_attempt 落实）
  cfgValid = validate(s)                                  // time 格式 / payroll_day ∈ [1,31]
  if (!cfgValid) { claim.finish(SKIPPED, CONFIG_INVALID); return SKIPPED }   // 业务终止，不重试
  triggerType = (day > dueAt.toLocalDate()) ? CATCH_UP : AUTO                // 日粒度定义（§1.4）
  try:
     vo = payrollRunService.execute(s.id, m)              // 独立事务：内部调 generate(request)
     claim.finish(SUCCESS, generatedCount = vo.created)   // 占 uk_claim（claim_key=m）→ 跨日永久不再尝试
     afterCommitNotify(s, m, vo)                          // 提交后推管理员（失败不阻断）
     return SUCCESS
  catch e:
     if (e.code == 9405) { claim.finish(SKIPPED, BLOCKED_9405); return SKIPPED }  // 业务终止，不重试
     claim.finish(FAILED, e)                              // 占 uk_attempt(今日)；claim_key 置 NULL 释放跨日槽位
     return FAILED                                        // 次日（新 attempt_date）自动再次尝试 → 持续至成功
```

**判定顺序（短路链）**：总开关 → **僵死回收扫描（v1.3）** → 未到算薪日 → 当日时刻未到 → 已成功 → 业务终止 → 今日已尝试/占位 → 配置校验 → 执行 → 结果分类。短路优先可最大限度减少 DB 写（例：未到算薪日的驿站只做 1 次内存比较，不触碰 `payroll_run`；「已成功/已终止」在 `uk_attempt (station_id, target_month, …)` 的**最左前缀** `(station_id, target_month)` 上 1 次索引查询即短路）。

**串行 vs 并行**：**逐驿站串行**。理由：① 单实例、单事务边界，避免并发写放大与死锁；② 与既有 `createPayroll` 单线程逻辑一致，无需改业务代码并发性；③ 频率低（10min/tick），串行耗时 `[推导]` 远小于 tick（§4）。

### 1.4 补跑（catch-up）模型（v1.3：日粒度重试 + `catch-up-time-of-day`，U-06 / 复-3 定稿）

**模型一句话**：算薪日 `dueAt` 当天未成功 → **次日**在**当日 `catch-up-time-of-day`（默认继承该站 `payroll_time`）之后的首个 tick** 再试；再失败 → 次日再试；以**自然日为粒度持续重试直至成功**；**每个自然日至多尝试一次**；补的仍是**原账期**工资单（`target_month` 恒 = `dueAt` 月），仅执行日推后。（**v1.3 复-3**：删除 v1.2「次日同一钟点」的歧义表述，补跑钟点**统一由 `catch-up-time-of-day` 决定**。）

| 项 | 口径 |
| ---- | ---- |
| **可尝试判定（日粒度，复-3 统一）** | `day ≥ dueAt.toLocalDate()` ∧ `now ≥ 当日尝试钟点` ∧ **该 `(station_id, target_month)` 当日尚未尝试**（`uk_attempt` 未占） ∧ `(s,m)` 既无 `SUCCESS` 也无**终止类 `SKIPPED`** ∧ 配置合法。其中 **当日尝试钟点** = `day == dueAt 日 ? payroll_time : catch-up-time-of-day`（后者空值则继承 `payroll_time`）；`day = now.toLocalDate()`（`Z` 墙钟）。「当日尚未尝试」为**硬防线**（`UNIQUE (station_id, target_month, attempt_date)`），非仅应用层检查 |
| **每日一次（硬约束）** | 同一 `(s, m)` 的同一 `day` 至多产生 **1** 条尝试记录，由 `UNIQUE (station_id, target_month, attempt_date)` **原子拒绝**（§1.5）。10min tick 在同一自然日内多次评估，**第 2 次起即被拒（短路⑤）**，不产生重复尝试 |
| **尝试时刻（复-3 定稿）** | `attemptTimeOf(day, s) = ZonedDateTime.of(day, H, M, Z)`，其中 `(H, M) = (day == dueAt.toLocalDate()) ? payroll_time : catchUpTimeOfDay(s)`；`catch-up-time-of-day` **为空则继承该站 `payroll_time`**（默认值 = `""`）。**当天 = `payroll_time`**；**补跑日 = `catch-up-time-of-day`**（默认与正常到点同钟点，故**默认行为与 v1.2 一致**；差异仅在该参数被**显式配置**时体现）。判定式 =「`now ≥ attemptTimeOf(day, s)` **且**当日尚未尝试」（§2 参数表） |
| **终止条件** | ① 成功（`SUCCESS`，`claim_key=m` 永久占位）→ 跨日不再尝试；② **终止类 `SKIPPED`**（`BLOCKED_9405` / `CONFIG_INVALID` / `DRAFT_PROTECTED`）→ 跨日不再尝试（**业务性终止，不属「可重试失败」**）；③ **无其他终止**——`FAILED` 一律次日继续 |
| **无窗口上限** | **v1.2 删除 `W` / `W_min`**：不存在「窗口外不补」；自动路径按日粒度**持续重试至成功或业务终止**。原「窗口外只走手工端点」口径**作废**（见 §12.3 第 2 条） |
| **是否补历史多期** | **否**。每日仅评估 `now` 所属 `dueAt` 账期；补跑只推后**执行日**，不改变账期 |
| **是否跨月** | `target_month = dueAt 所属月` **恒定不变**；补跑可跨越自然月边界（如 8-31 账期在 9-02 补跑），`target_month` 仍为 `2026-08`，**不回溯更早账期**（月末/跨月验算见 §1.4.1） |
| **优先级与去重** | 补跑与正常触发**共用同一条判定链**，仅在 `trigger_type` 标记上区分（`AUTO` / `CATCH_UP`）；去重由 §1.5 的**两个**唯一键（`uk_attempt` 管日内、`uk_claim` 管终态）统一负责 → **不存在「正常与补跑各跑一次」，也不存在「同日两次」** |

**`trigger_type` 确定性定义（v1.2 改为日粒度）**：

```
trigger_type = (now.toLocalDate() > dueAt.toLocalDate()) ? CATCH_UP : AUTO
```

即「与 `dueAt` 同日」= 正常到点 `AUTO`；「晚于 `dueAt` 所在日」= 补跑 `CATCH_UP`。该定义仅影响可观测性标签，不影响执行语义。

> **手工触发端点** `POST /api/v1/finance/payroll-runs/trigger` 仍保留（用于人工干预）；但自动路径**不再以「窗口」界定是否补跑**。**v1.3（复-2，主代理裁定）**：`force` 参数**已删除**——同日重复触发一律由 `uk_attempt` 硬拒绝回 `9410`，不存在「跳过应用层短路后同日再试」的路径；端点入参仅保留 `stationId` / `month`（§8）。**本节不涉及 `force` 语义。**

**「跨月矛盾」在新口径下消解（原 §7 R5）**：v1.1 的 R5 矛盾源于「窗口 `dueAt+W` 可跨月」与「不补历史账期」并存。新口径**取消窗口概念**——补跑是「同一 `(s,m)` 的日粒度重试」，`target_month` 恒为 `dueAt` 月，既不依赖窗口是否跨月，也不回溯更早账期，**矛盾自然消失**（作废见 §12.3 第 3 条）。

#### 1.4.1 月末钳位与跨月补跑验算（v1.3：日粒度口径不变；补跑钟点见 §1.4 / 复-3）

> **v1.1 → v1.2 说明（必读）**：本节 v1.1 内容为「`min(dueAt+W, 次月首日00:00)` **有效窗口塌缩**量化」并讨论口径 A/B（`W_min` 下限保障）。在 v1.2 **日粒度重试**下令牌 `W` / `W_min` 已**删除**，**「小时窗口」概念不再存在**，故原塌缩分析、边界证明 `[1min, W]`、风险登记 R-W2、口径 A/B 与组合矩阵**整体作废**（见 §12.3 第 1、2 条）。本节改写为「**钳位后的 `dueAt` 与跨月补跑的 `target_month` 归属验算**」，保留月末边界与跨月验算要求。

**（1）`dueAt` 钳位验算（U-05 定稿）**：`d_eff = clamp(d0, 1, L)`，`dueAt = ZonedDateTime.of(Y, M, d_eff, hh, mm, 0, 0, Z)`。钳位只改 `dueAt` 的**日**，不改 `target_month`。

| # | `d0` | 账期 m | `L` | `d_eff` | `dueAt` | `target_month` | 备注 |
| - | - | ---- | - | - | ---- | ---- | ---- |
| 1 | **31** | **2026-02** | 28 | 28 | 02-28 09:00 | `2026-02` | **31 日遇 2 月 → 钳位到月末** |
| 2 | 30 | 2026-02 | 28 | 28 | 02-28 09:00 | `2026-02` | 30 日遇 2 月 → 钳位 |
| 3 | 29 | 2026-02 | 28 | 28 | 02-28 09:00 | `2026-02` | 非闰年 29 → 钳位 |
| 4 | 29 | 2024-02 | 29 | 29 | 02-29 09:00 | `2024-02` | 闰年 29 → 命中 |
| 5 | 31 | 2026-04 | 30 | 30 | 04-30 09:00 | `2026-04` | 31 日遇 30 天月 → 钳位 |
| 6 | 31 | 2026-08 | 31 | 31 | 08-31 09:00 | `2026-08` | 无需钳位 |

**（2）跨月补跑的 `target_month` 归属验算（新口径核心）**：

| # | `dueAt` | 当天 | 补跑日 | `attempt_date` | `target_month` | 期望 |
| - | - | ---- | ---- | ---- | ---- | ---- |
| 1 | 2026-08-31 09:00 | 08-31 失败 | 09-01（次日） | `2026-09-01` | **`2026-08`** | 跨月补跑，账期仍为 8 月；**不生成 9 月单** |
| 2 | 2026-08-31 09:00 | 08-31 失败 | 09-01 失败 → 09-02 | `2026-09-02` | **`2026-08`** | 持续重试仍归 8 月账期 |
| 3 | 2026-02-28（钳位自 31） | 02-28 失败 | 03-01 | `2026-03-01` | **`2026-02`** | 钳位日跨月补跑，仍归 2 月 |
| 4 | 2026-09-01 09:00 | 09-01 成功 | — | `2026-09-01` | `2026-09` | 新账期正常算薪 |
| 5 | 同站两账期（`m=08` 补跑 + `m=09` 正常） | — | 09-01 同一天 | 两条 `attempt_date=2026-09-01`（`m` 不同） | 各为 `2026-08` / `2026-09` | **`uk_attempt` 含 `target_month` → 同日两账期互不误拦**（§1.5） |

**（3）关键性质 `[推导]`**：
- **钳位确定性**：`d_eff ∈ [1, L]` 恒成立 → `dueAt` 恒落在当月内，**不存在「某月无 `dueAt`」**（U-05 定稿的直接效果）。
- **补跑与账期解耦**：执行日 `attempt_date` 可跨月递增（`08-31 → 09-01 → 09-02 …`），`target_month` 恒 = `dueAt` 月 → **「执行日推后」不污染账期归属**（验算表第 1–3 行）。
- **同日两账期不冲突**：`uk_attempt` 键含 `target_month`，故「8 月账期补跑」与「9 月账期正常」可在同一自然日各占 1 槽（验算表第 5 行）。

**（4）边界风险（登记，非阻断）**：
- **R-D1 补跑钟点固定**：补跑在「当日 `catch-up-time-of-day`（默认继承 `payroll_time`）后首个 tick」触发；若该钟点极晚（如 23:59）且该分钟恰逢停机，则当天错过、**次日同钟点再试**——因**无窗口上限**，仅表现为**延迟一天**，不构成永久漏跑（与 v1.1「窗口塌缩 → 自动补跑形同关闭」有本质区别）。**v1.3（复-3）：补跑钟点口径单一真源 = `catch-up-time-of-day`。**
- **R-D2 `catch-up-time-of-day` 前移限制**：若运营将补跑钟点前移到凌晨（如 00:30），可能早于上游数据日结时点 → 建议保持默认继承 `payroll_time`（如 09:00）；该参数仅提供运营弹性，默认不改变业务节奏。

### 1.5 幂等模型（层级图）

```
Layer 0  调度层  payroll_run 双唯一键（v1.2 新增第 1 个）
   ▲ 最强，DB 强约束  · uk_attempt (station_id, target_month, attempt_date) → 同账期同日至多一次
                    · uk_claim   (station_id, claim_key)                  → 跨日终态占位（RUNNING/SUCCESS/终止SKIPPED）
Layer 1  生成层  generate 9405（按驿站收敛）  应用层 check（同一事务）
Layer 2  单据层  idx_payroll_emp_month_bill  ✗ 普通索引，无唯一约束（§0.2 ①）
```

**Claim 槽位协议（对上游的唯一键语义修正，§7 R2；v1.2 补「尝试日」维度）**：

v1.2 起由**两个唯一键正交协同**，各负责一条不变式：

| 唯一键 | 负责的不变式 | 生效条件 | 释放条件 |
| ---- | ---- | ---- | ---- |
| **`uk_attempt (station_id, target_month, attempt_date)`**（**v1.2 新增**） | **同一账期同一自然日至多一次尝试** | 每次尝试行必写 `attempt_date`（`NOT NULL`） | **不释放**：该日槽位永久占用；次日为新 `attempt_date`，天然放行 |
| `uk_claim (station_id, claim_key)`（v1.1） | **跨日终态互斥**：已成功 / 已终止 / 正在跑 → 不再尝试 | `RUNNING` / `SUCCESS` / 终止类 `SKIPPED` 写 `claim_key = target_month` | 仅 `FAILED` 置 `NULL` 释放（允许次日重试；MySQL 唯一索引允许多个 `NULL`，见 §5②） |

**`claim_key` 取值表（v1.2 语义微调）**：

| 运行状态 | `claim_key` | `attempt_date` | 效果 |
| ---- | ---- | ---- | ---- |
| `RUNNING` | `target_month` | 当日 | **占位**：第二个并发执行者 INSERT 撞 `uk_claim` 即 DuplicateKey → 短路 |
| `SUCCESS` | `target_month` | 当日 | 占位（永久）：跨日不再尝试（前置 `existsSuccess` + `uk_claim` 双保险） |
| `SKIPPED`（`BLOCKED_9405` / `CONFIG_INVALID` / `DRAFT_PROTECTED`，**终止类**） | `target_month` | 当日 | 占位（永久）：业务终止，跨日不再尝试 |
| `FAILED` | `NULL` | 当日 | **释放跨日槽位**（`uk_claim`），但 **占用当日槽位**（`uk_attempt`）→ 同日不再重试、次日允许 |
| ~~`SKIPPED(EXHAUSTED)`~~ | — | — | **v1.2 删除**：无「总次数上限」，故不存在 `EXHAUSTED`（见 §12.3 第 4 条） |

**「每日至多一次」的判定机制（v1.2 新增；最终选定方案）**：

- **主判据（硬防线，充分条件）**：`UNIQUE (station_id, target_month, attempt_date)`。同日第 2 次 `INSERT` 命中 1062 → 短路，**零重复尝试**。DB 原子，无需应用层锁。
- **辅助判据（可选，减少无效 INSERT 与日志噪音）**：`existsSuccess` / `existsTerminalSkip` **前置查询**（短路③④），使「已成功 / 已终止」账期根本不发起 INSERT；查询走 `uk_attempt (station_id, target_month, attempt_date)` 的**最左前缀** `(station_id, target_month)`（**v1.3 清理**：`idx_payroll_run_station_month` 已在 `V21` 删除，改指该前缀），**已判定的终态短路无新增索引成本**。
- **为什么不用「仅查询当日记录」作唯一判据**：`EXISTS(… WHERE DATE(start_time)=今天)` 属 **check-then-act**，并发写者可同时通过检查；且 `DATE(start_time)` 使既有索引失效（除非建函数索引）。**故以唯一键为硬防线，查询仅作优化**（依据：MySQL 唯一索引原子性 §5②③）。
- **表 / 索引需求**：`payroll_run` **新增 `attempt_date DATE NOT NULL`**（由 `Z` 墙钟派生）+ **新增 `UNIQUE KEY uk_attempt (station_id, target_month, attempt_date)`**。因 `target_month` 已是 `CHAR(7) NOT NULL`（[V20__payroll_automation.sql:72](../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L72)），键列全 `NOT NULL`，无 NULL 逃逸。**属结构变更，交架构师 / 数据库工程师，本文件不落 DDL。**
- **为什么不能把日期编进 `claim_key`（该降级被否决）**：`claim_key` 为 **`CHAR(7)`**（[V20__payroll_automation.sql:80](../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L80)），装不下 `yyyy-MM#yyyy-MM-dd`（≥17 字符），且会破坏「`claim_key = target_month`」既有语义与 `db.md` 契约。**故必须新增列**（事实核验：列类型长度约束）。
- **降级方案（仅当架构拒绝新增列时登记）**：不新增列，改以「同日 `EXISTS` 查询」作日判据 + 保留 `uk_claim` 作并发互斥 → **单实例下可用**，但多实例 / 手工并发下存在 check-then-act 残余竞态，须登记 `TODO(扩展)`（§9 TODO-8）。

**为什么 `RUNNING` 也必须占位**：上游仅让 `SUCCESS` 写哨兵，`RUNNING` 阶段 `success_key=NULL` 无约束 → 两个重叠 tick（或未来两实例）可**同时插入 RUNNING 并同时进入 `generate`**，各自通过 9405 检查（下游未提交、彼此不可见），最终可能各插一行同员工同月单据（Layer 2 无唯一约束，见 §0.2 ①）→ **重复工资单**。让 `RUNNING` 占位后，冲突在 **INSERT 一条 SQL** 上被 InnoDB 唯一约束原子拒绝，这是最简、最可靠的防线。**v1.2 补充**：`RUNNING` 行同时占用 `uk_claim`（`claim_key=m`，跨日互斥）与 `uk_attempt`（当日，日内互斥），故「重叠 tick」与「**同日多次 tick**」两类并发均被**一条 INSERT** 原子拦截。

**事务切分要求（交后端实现，本文只提口径）**：claim 的 `INSERT RUNNING` 必须**先于** `generate` 并在**独立短事务中提交**（否则未提交的占位对其他执行者不可见，且 `generate` 回滚会连带抹掉占位）。推荐三段：`Tx1 = INSERT RUNNING（commit）` → `Tx2 = generate（业务事务）` → `Tx3 = UPDATE 终态`。**v1.2**：`Tx1` 须同时写入 `attempt_date = day`（`Z` 墙钟）与 `claim_key = target_month`，使 `uk_attempt` / `uk_claim` 两道防线在插入即生效。

**僵死 RUNNING 回收（§7 R6；v1.1 补竞态；**v1.3 完整定义动作与告警**，见 §1.5.3）**：进程在 `RUNNING` 后被杀，占位将永久阻塞该 `(s,m)`。tick 对 `status=RUNNING ∧ start_time < now - running-timeout-minutes` 的行判定为僵死，执行**完整回收动作**（v1.3，复-1）：`claim_key=NULL` + `status=FAILED` + `fail_reason='STALE_RECLAIMED'` → **释放 `uk_claim` 跨日占位**；**不释放当日 `uk_attempt` 槽位** → **僵死当天不再重试、次日新 `attempt_date` 放行重试**；**回收命中即触发失败告警**（受 `notify-on-fail` 管控，§13 R-C1）。完整时序与唯一键交互见 **§1.5.3**。**该回收本身仍引入竞态 W1**（**仅当存在第二执行者 B 时**：多实例或手工路径也执行回收）：阈值 `running-timeout-minutes` 可能 `< 实际单驿站耗时 D`（DB 变慢 / 大批量），导致在跑记录被误判为僵死 → 释放 → 被第二执行者抢占 → **并发 `generate` → 重复工资单**（完整时序见 §1.5.1）。**限定**：`claim` 唯一键的「至多一个 `RUNNING/SUCCESS`」**仅在无僵死回收误判且手工路径串行时成立**。**单实例下回收安全**（`fixedDelay` 不重叠 → 回收与执行同线程 → 无并发 → 超时即真僵死，§1.5.3）。

**极端并发最坏结果（防线表）**：

| 场景 | 未修 R2（仅 SUCCESS 占位） | 采用本方案 claim 槽位后 |
| ---- | ---- | ---- |
| 两个 tick 重叠 | 可能双执行 → 重复单据 | 第二个 INSERT 失败 → 短路，**零重复** |
| **同一自然日多次 tick**（v1.2） | 每 tick 均可重试（无日内约束）→ 同日最多 144 次 | `uk_attempt` 拦截：当日仅**首个** tick 的 INSERT 成功，其余全部短路 → **同日零重复**；次日新 `attempt_date` 自动放行 |
| 手工触发与 tick 并发 | 可能双执行 | 同上，且可回 9410 |
| 未来多实例 | 双实例双执行 | DB 唯一键天然跨实例互斥（**在无僵死回收误判时**保证「至多一次成功」）；残留问题：**僵死回收误判 → 并发**（W1，§1.5.1） |
| 进程崩溃（RUNNING 未提交） | 占位随事务回滚消失 | 同左（Tx1 未 commit 则无残留；已 commit 则靠超时回收） |

> **本表全部「零重复」结论的成立前提**：**无僵死 `RUNNING` 回收误判**且**手工写路径与自动路径串行**（否则见 §1.5.1 W1 / §1.5.2 W2）。
> **多实例选主/分布式锁**仍登记为架构债（上游 U-12），本方案以 claim 唯一键兜底「不产生重复单据」，但不解决「同一时刻两实例都在跑」的资源浪费。

#### 1.5.1 僵死回收竞态 W1：完整时序与处置口径（响应技术评审必改 4）

**前提**：W1 的成立需存在**回收者 B**（执行僵死回收的第二执行者）。**单实例 `fixedDelay` 下 B 不存在**（同实例不自我并发、回收与执行同线程）→ **W1 不发生**。**W1 仅在**：① 多实例（上游 U-12）；② 手工触发路径也执行僵死回收 时成立。`[推导]`
**v1.2 说明**：日粒度重试**不改变** W1 前提（回收逻辑未变）；新增的 `uk_attempt` 仅约束**同一自然日**，对「回收误判 → 第二执行者**同日**抢占」**不构成额外防线**，故 **P1（owner + 终态并入业务事务 + 行锁复核）仍是 W1 的唯一对策**。

记号：`T = running-timeout-minutes`（默认 30min）；`D = A 的 generate 墙钟耗时`；`tick=10min`。窗口最大处即 **`D > T`**（正常量级 `D` <1min，`D>T` 仅见于 DB 变慢 / 大批量 / 阈值误配）。

| 步 | 时刻 | A（原持有者） | B（回收者） | `payroll_run`（id / 状态 / claim_key） |
| - | ---- | ---- | ---- | ---- |
| 1 | `T0` | `INSERT(id=1, RUNNING, claim_key=m)` Tx1 commit | — | id1 RUNNING m |
| 2 | `T0..T0+D` | `generate`（Tx2）执行中 | — | 同左 |
| 3 | `Tk`，`T0+T ≤ Tk < T0+D` | 仍在 Tx2 | 扫到 id1 RUNNING 且 `start_time < Tk−T` → 判僵死 → `UPDATE id1 SET FAILED, claim_key=NULL` commit | id1 FAILED **NULL** |
| 4 | `Tk+ε` | 仍在 Tx2 | `INSERT(id=2, RUNNING, claim_key=m)` commit | id1 FAILED NULL；**id2 RUNNING m** |
| 5 | `T0+D` | Tx2 **提交**（工资行落库） | Tx2′ 执行中 | **两套工资行并存**（Layer 2 无唯一约束）→ **重复工资单** |
| 6 | `T0+D+ε` | 终态回写 `UPDATE id1 SET SUCCESS, claim_key=m` → 与 id2 冲突 → **DuplicateKey，回写失败** | — | id1 仍 FAILED NULL；id2 RUNNING m |
| 7 | 稍后 | A 报错退出 | Tx2′ 提交；`UPDATE id2 SET SUCCESS` | id2 SUCCESS m |

**关键结论 `[推导]`**：
- **最大危害在步 5**（重复工资单），此时唯一键**尚未拦截**；唯一键仅在**步 6 的终态回写**处二次报错，**滞后于危害**——故不能以「有唯一键」为由宣称无风险。
- **变体**：若 A 的终态回写不回填 `claim_key`（自认「已是 m」），则 id1 → `SUCCESS` 且 `claim_key=NULL`，**破坏「`SUCCESS` 占位」不变式**，且仍为双份工资行。
- 步 3 回收与步 6 回写**无锁竞态**：谁先提交决定终态，行为不确定。

**处置口径候选**：

| 方案 | 做法 | 成本 | 结论 |
| ---- | ---- | ---- | ---- |
| **P1 owner + 终态并入业务事务 + 行锁复核（默认）** | ①claim 写 `owner_instance_id`；②`generate` 事务内 `SELECT … FOR UPDATE` 该 run 行，校验 `status=RUNNING ∧ owner=自己`，算薪后**同事务**回写终态；③回收用 CAS `UPDATE … SET FAILED, claim_key=NULL WHERE id=? AND status='RUNNING'`（可加心跳条件） | 1 列 + 1 行锁 + 1 次 CAS | **推荐**：行锁使「复核 + 落库 + 终态」与「回收」串行，**把重复挡在提交前**；无需分布式锁 |
| P2 心跳续期 | RUNNING 行每 tick 续 `heartbeat_at`；回收条件加 `heartbeat_at < now−T` | 1 列 + 每 tick 1 UPDATE | P1 的加固（多实例下降低误判率） |
| P3 fencing token | 每次 claim 分配单调 `fence`，工资写带 `fence` 上限校验 | 改表 + 改写路径 | **过度设计**（无 MQ / 协调组件，一期否决） |
| P4 明确接受风险（**限多实例前 / 主动放弃自动回收**） | **多实例前**：置 `stale-reclaim-enabled=false`（或仅人工回收）→ 消除 B 前提；真僵死人工处置（C 档）。**v1.3：单实例默认 `true`，无需关闭**（§1.5.3） | 0 结构成本 | **多实例前的兜底开关**（非单实例默认） |

**最终处置口径（本方案建议）**：默认 **P1**（+**P2** 可选加固）；**P4** 以 `stale-reclaim-enabled=false` 作**多实例前**兜底（**v1.3：单实例默认 `true`，自动回收安全可行，不关闭**，§1.5.3）。
**边界声明**：P1/P2 需 `payroll_run` **加列**，属**结构变更**，交**架构师 / 数据库工程师**评估并在 B0 批次同步；**本文件不落 DDL**。若实现未采纳 P1 且无 owner，则**登记残余风险**：极端多实例 / 慢任务下可重复工资单，兜底 = 单据去重核对脚本 + 告警 + `TODO(扩展)`（§9）。

#### 1.5.2 手工路径竞态 W2：`item-add` 与自动重建不互斥（响应技术评审必改 4）

- **触发条件**：自动路径对 `(s,m)` 的 `DRAFT` 单执行「事务内 `deleteExisting` + 重建」（[PayrollServiceImpl.java:601-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621)）的同时，管理员对**同员工同月** `item-add`（I-6，**不经 claim**、独立事务）。
- **影响面 `[推导]`**：① 并发录入的 `MANUAL` 项被 `deleteExisting`（物理删除）清除 → **数据丢失**；② 或产生**孤儿 item**（主单被删重建、明细指向旧主单 id）→ 单据汇总口径失真。
- **缓解（按成本升序）**：
  - **(a) 口径层（默认建议）**：自动路径对**存在 `DRAFT` 单**的 `(s,m)` **一律保护性 `SKIPPED`**（不区分是否已含 `MANUAL`），把「`DRAFT` 覆盖重建」从自动路径移除；待人工处理 `DRAFT` 后重跑。**零 schema 成本**；较上游 R11「仅含 `MANUAL` 才保护」更严，**改变边界用例 #10 行为，须架构确认**。
  - **(b) 入口加固（可选）**：`item-add` 前置校验 `(s,m)` 是否存在 `RUNNING` 的 `payroll_run`（按员工所属驿站映射）→ 在跑则拒绝 / 请稍后（错误码沿用 `941x`）。
  - **(c) schema 方案（登记 TODO）**：单据明细层加唯一键 / 乐观版本号，使并发写可检测（交数据库 + 后端联评，P4）。
- **最终处置口径**：默认 **(a)**，**(b)** 可选加固，**(c)** 登记 `TODO(扩展)`（§9 TODO-7）。**残余风险**：若 (a) 未被采纳，须落 (b)/(c) 或接受风险并登记。

#### 1.5.3 僵死回收动作定义与时序（v1.3，响应复评 复-1）

> **定位**：复评 §三 3.2 独立验算发现「`RUNNING` 僵死**未被回收** → 次日行也无法插入（`uk_claim` 被该 `RUNNING` 行**永久占位**）→ 自动补跑中止；且**无 `FAILED` 行** → `alert-after-consecutive-fail-days` **不触发** → **静默失效**」。本节把**回收动作完整定义**并给出「回收 → 次日可插入」时序，作为该卡死场景的**正面消解**。

**（1）回收动作定义（v1.3 定稿，可配）**：回收由 tick 的 `reclaimStale(now)` 承担，**仅当 `stale-reclaim-enabled=true`（默认）** 时执行。

| 项 | 定义 |
| ---- | ---- |
| 触发条件（僵死判定） | `status='RUNNING'` ∧ `start_time < now − running-timeout-minutes`（`now`、`start_time` 同用 `Z` 墙钟口径，§7 R7） |
| 更新语句（CAS） | `UPDATE payroll_run SET status='FAILED', claim_key=NULL, fail_reason='STALE_RECLAIMED' WHERE id=? AND status='RUNNING'` |
| 字段效果 | `status`：`RUNNING` → `FAILED`；`claim_key`：`m` → **`NULL`**（**释放 `uk_claim` 跨日槽位**）；`fail_reason`：置 **`STALE_RECLAIMED`**（白名单常量，非敏感信息，§1.6） |
| 唯一键效果 | **释放 `uk_claim`**（`claim_key=NULL`，多 `NULL` 不冲突，§5②）；**仍占 `uk_attempt (s,m,原 attempt_date)`**（当日槽位不因回收释放） |
| 告警 | **回收更新命中（affected ≥ 1）即触发失败告警**——复用失败告警通道，受 `notify-on-fail`（默认 `true`）管控；文案含 `stationId` / `target_month` / `attempt_date` / `STALE_RECLAIMED`；该条 `FAILED` **计入** `alert-after-consecutive-fail-days` 的连续失败计数（§13 R-C1） |
| 幂等性 | CAS 条件含 `status='RUNNING'`：行已是 `FAILED`/`SUCCESS` 则不匹配 → `affected=0` → **不重复回收、不重复告警** |
| 安全前提（并发） | 回收**不覆盖**并发完成的 `SUCCESS`（CAS 保证）；但**在存在第二执行者 B 时**仍受 W1 约束（§1.5.1），单实例无 B（见本小节 (4)） |

**（2）时序表：`RUNNING` 僵死 → 回收 → 当日不重复 → 次日自愈 `[推导]`**

记：`T = running-timeout-minutes`；某站 `s`、账期 `m`、僵死行 `id=1`、`attempt_date=D0`、`start_time=T0`。

| 步 | 时刻 | 动作 | `payroll_run`（id / status / claim_key / attempt_date） | `uk_claim(s,m)` | `uk_attempt(s,m,D0)` |
| - | ---- | ---- | ---- | ---- | ---- |
| 1 | `T0` | `Tx1 INSERT id=1 RUNNING, claim_key=m, attempt_date=D0`（commit） | id1 RUNNING m D0 | **占** | **占** |
| 2 | `T0+ε` | 进程被杀，无终态回写 | id1 RUNNING m D0（滞留） | 占 | 占 |
| 3 | `now ≥ T0+T` 的 tick | `reclaimStale`：CAS `→ FAILED, claim_key=NULL, fail_reason=STALE_RECLAIMED`（affected=1）→ **触发告警** | id1 **FAILED  NULL  D0** | **释放** | **仍占** |
| 4 | 回收后**同一日 `D0`** 的后续 tick | `tryClaim`：`INSERT (s,m,D0,claim_key=m)` | 不变（新增被拒） | 可插 | **撞 1062 → 拒绝** |
| 5 | **次日 `D1`（>D0）** 首个满足 `catch-up-time-of-day` 的 tick | `tryClaim`：`INSERT (s,m,D1,claim_key=m)` → 成功 → `generate` → `SUCCESS` | 新增 id2 SUCCESS m D1 | 占（id2） | 新日期槽位占 |
| 6 | 之后 | 短路③ `existsSuccess` / `uk_claim` → 跨日不再尝试 | — | — | — |

**（3）两唯一键交互——为何「回收后次日能插入、当日不重复」**：

- **为何回收后次日能插入新行**：`uk_attempt` 键含 `attempt_date`，回收**不改**僵死行的 `attempt_date`（仍为 `D0`）→ 次日 `D1` 是**新键值**，`uk_attempt` **放行**；僵死行 `claim_key` 已置 `NULL` → `uk_claim (station_id, claim_key)` 上该站该账期**不再有非 `NULL` 值**，多 `NULL` 并存不冲突（§5②）→ `uk_claim` **放行**。**两键同时放行 → 次日 INSERT 成功**。
- **为何回收当日不重复**：回收**不释放** `uk_attempt (s,m,D0)` → 当日任一后续 tick 的 `INSERT` 仍撞该键（1062）→ 短路⑤ → **当日零新增**。故「每自然日至多一次」**在回收场景下依然成立**。
- **为何告警必触发（消除静默失效）**：回收**写入一条 `FAILED` 行**（`fail_reason=STALE_RECLAIMED`）并在更新命中时**主动告警**——不再依赖「等某天出现 `FAILED` 行才计数」，从**根因**上消除了复评指出的「无 `FAILED` 行 → 告警不触发 → 静默失效」。**即使回收开关被置 `false`**，也必须保留**超期 `RUNNING` 告警**（§13 R-C1②）作为兜底观测。

**（4）单实例下回收安全性论证（`[推导]`）**：

- **前提**：一级调度使用 `@Scheduled(fixedDelay=interval)`（§5①）——**上一次执行结束到下一次执行开始**才计时，**单实例内两次 tick 不重叠**，回收 `reclaimStale` 与执行 `executeIfDue` **在同一调度线程串行**。
- **推论**：当回收在时刻 `now` 看到某个 `RUNNING` 且 `start_time < now−T` 时，**持有该行的执行体在同一线程内必已结束**（否则 `now` 时刻不可能同时运行回收）→ **该行确为真僵死，非误判** → 单实例下**回收不产生 W1 重复工资单**。
- **边界**：`T` 需 **≥ 最坏单驿站耗时**（默认 `30min ≫ [推导] <1min`，§4.2）→ 进一步压低了「单实例内误判」的可能（虽然按上推论单实例本无 B 前提）。
- **结论**：**`stale-reclaim-enabled=true`（默认）在单实例部署下安全且必要**（否则僵死占位依赖人工 C 档处置，复评即判为静默失效风险）。**多实例**下 B 存在 → W1 成立 → 仍登记架构债（TODO-3），并保留 W1 处置口径 P1（§1.5.1）。

**（5）衍生风险（登记，非阻断）**：回收扫描的频率与成本（§4.6）、误回收判定在多实例下的残余（§13 R-C2）、`fail_reason` 取值白名单需后端加 `STALE_RECLAIMED`（§13 R-C3）。

### 1.6 异常与失败隔离

| 项 | 口径 |
| ---- | ---- |
| **隔离粒度** | 逐驿站 `try/catch`；单驿站异常不外溢，其它驿站照常执行 |
| **事务边界** | 每驿站独立事务（`generate` 已在 `@Transactional` 内，[PayrollServiceImpl.java:142-186](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L142-L186)）；ticker 本身不加事务 |
| **通知** | 事务**提交后**触发（`afterCommit` 或事务方法返回后），失败只记 `NOTIFY_SKIP` 留痕、不阻断主流程（沿用请假域口径） |
| **重试（v1.3）** | 仅 `FAILED`（可重试类，**含回收产生的 `STALE_RECLAIMED`**）**次日**自动重试；**同一自然日至多一次**；**无总次数上限**（持续至成功）；连续失败达 `alert-after-consecutive-fail-days`（默认 3）→ 告警（**不停止**，§13） |
| **不重试类** | 9405、配置非法、DRAFT 保护 → 立即 `SKIPPED`，不重试 |
| **失败留痕** | `fail_reason` 截断（≤500）且**不含敏感信息**（金额/身份证等），完整堆栈只进应用 error 日志；**取值白名单含 `STALE_RECLAIMED`**（v1.3，§13 R-C3） |

**`SKIPPED` 与 `FAILED` 的归类边界（对上游的细化，§7 R9）**：

| 情形 | 是否产生运行记录 | status | 是否重试 |
| ---- | ---- | ---- | ---- |
| `enabled=0` 或驿站 `status=0` | **否**（不在扫描集合） | — | — |
| 未到点 | 否 | — | — |
| `(s,m)` 已有 `SUCCESS` | 否（claim 短路） | — | — |
| 命中 9405（该驿站该月已有非可覆盖单） | **是** | `SKIPPED`（记阻断单号） | 否 |
| 存在人工 DRAFT 保护（§7 R11；**v1.1 加固**：存在 `DRAFT` 单一律保护，见 §1.5.2 (a)） | 是 | `SKIPPED` | 否 |
| ~~重试耗尽~~（**v1.2 删除**） | — | — | 无 `EXHAUSTED`：不设总次数上限（§12.3 第 4 条） |
| 配置非法（`time` 格式 / 日越界） | 是 | `SKIPPED`（`CONFIG_INVALID`） | 否 |
| ~~自动路径超窗口~~（**v1.2 删除**） | — | — | 无窗口概念；`FAILED` 一律次日继续（§1.4） |
| 同日非首个 tick（当日已尝试） | **否**（`uk_attempt` 短路，不新增行） | — | — |
| 手工触发（`MANUAL`）与当日自动尝试冲突 | 是 | `SKIPPED`（claim 占位冲突） | 否（回 9410；次日自动路径继续） |
| 算薪抛业务/系统异常 | 是 | `FAILED` | 是（**次日重试**，同日不重试） |
| **僵死 `RUNNING` 被回收（v1.3，复-1）** | **是**（**更新**既有 `RUNNING` 行为 `FAILED`，非新增行） | `FAILED`（`STALE_RECLAIMED`） | 是（**次日重试**；**同日不重试**——`uk_attempt` 当日槽位不释放，§1.5.3） |

> 「未启用」「未到算薪日/时刻」「同日非首个 tick（当日已尝试）」「已成功 / 已终止」**均不产生运行记录**：它们属「未进入执行」，写 `SKIPPED` 会污染运行历史并误导排障（I-5 查询）。如产品要求可见，应在查询层以**派生状态**展示，而非落表。此为对上游 §5「未启用 / 超出窗口记 SKIPPED」清单的细化（v1.2 以「同日已尝试」替代原「超窗口」）。

### 1.7 算法选型与复杂度小结

| 子问题 | 选型 | 时间 | 空间 | 依据/理由 |
| ---- | ---- | ---- | ---- | ---- |
| 到期判定 | 无状态闭式 `dueAt(m)` + 钳位 | `O(1)`/驿站 | `O(1)` | 无持久化定时队列，进程重启即自愈（上游 ADR-01） |
| 扫描调度 | 固定间隔轮询 + 稳定排序串行 | `O(S)`/tick（DB 查询） | `O(S)` | 逐驿站配置无法用单条 cron 表达；轮询天然实现补跑 |
| 幂等（跨日终态） | DB 唯一键 `uk_claim (station_id, claim_key)`（Insert-claim） | `O(1)` 每次尝试 | `O(1)` | InnoDB 唯一约束原子性，无需分布式锁（单实例） |
| 幂等（日内一次，v1.2） | DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)` | `O(1)` 每次尝试 | `O(1)` | 同日重复 tick 原子拒绝；次日新 `attempt_date` 自动放行 |
| 失败隔离 / 补跑 | 逐驿站事务 + **日粒度重试（每自然日至多一次，无上限）** | `O(1)`/站/天 | `O(1)` | 单点故障不扩散；次日自动续跑（U-06） |
| **僵死回收（v1.3，复-1）** | 按 `RUNNING ∧ start_time < now−T` 扫描 + **CAS 单行更新**（`FAILED`/`claim_key=NULL`/`STALE_RECLAIMED`） | `O(R_run)`/tick（`R_run` = 运行中行数，通常 ≤ 并发站数） | `O(1)` | CAS 幂等；单实例 `fixedDelay` 不重叠 → 超时即真僵死；回收后次日自愈、告警必触发（§1.5.3） |
| 节流（可选） | 串行间隔；需要精确速率时用令牌桶 | 桶 `O(1)` 状态 | `O(1)` | 与 `hrm.algo.ratelimit` 令牌桶口径同源（§5） |

**为什么不用「持久化延迟队列 / 每条 dueAt 一个 cron / 优先队列」**：① 逐驿站算薪日与时间皆可配，无法穷举 cron；② 引入外部队列属过度设计（无 MQ 基础设施，上游 ADR-01 已否决 B/C/D 备选）；③ 无状态判定让「错过即自愈」成为**零额外机制**的副产品，而队列需要「积压处理 + 死信 + 补偿」三套组件。

---

## 2. 参数表（全部外置，禁止内联；v1.3 最终确认，单一真源）

命名空间 `hrm.payroll.schedule.*`（沿用上游 ADR-01）。组织方式对齐 [AlgoProperties.java](../hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java) 的强类型 `@ConfigurationProperties` 风格（嵌套静态类 + 字段默认值），限速子项命名与 `hrm.algo.ratelimit` 同构。

> **单一真源声明（复-4）**：本清单为 **架构方案（`payroll-automation-design.md` v1.5）与 [algo-hrm-server.md](algo-hrm-server.md) 参数引用之唯一真源**；名称 / 默认值 / 取值范围**以本表为准**。架构侧旧名（`alert-after-fail-days`）与旧项（`auto-retry-suspend-after-fail-days`）的处置见 **§14.4**。

**v1.2 变更概览（历史）**：**删除 3 项**（`catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run`）、**新增 3 项**（`catch-up-time-of-day` / `alert-after-consecutive-fail-days` / `alert-repeat-interval-days`）、**语义变更 3 项**（`tick-interval-ms` / `running-timeout-minutes` / `notify-on-fail`）。最终 **11 项**。

**v1.3 变更概览（相对 v1.2，最终确认）**：

| 类别 | 计数 | 明细 |
| ---- | ---- | ---- |
| **新增（yaml 参数）** | **0** | 无 |
| **删除（yaml 参数）** | **0** | 无（v1.2 的 11 项全部保留） |
| **默认值变更** | **0** | 无（`stale-reclaim-enabled` v1.2 即为 `true`，v1.3 定稿沿用；**承接主代理裁定，纠正方案 v1.4 的 `false`**） |
| **语义 / 依据更新** | **4** | ① `catch-up-time-of-day`（补跑钟点判定口径定稿，复-3）；② `running-timeout-minutes`（补回收动作与单实例安全依据，复-1）；③ `stale-reclaim-enabled`（默认依据改写 + 单实例安全论证，复-1）；④ `notify-on-fail`（明确覆盖僵死回收告警，复-1） |
| **删除（接口参数，非 yaml）** | **1** | `force`（主代理裁定删除，复-2；见下方「已删除的接口参数」） |
| **最终项数** | **11** | 与 v1.2 一致 |

| 参数名 | 类型 | 默认值 | 取值范围 | 含义 | 调大影响 | 调小影响 | 默认值依据 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| `hrm.payroll.schedule.enabled` | boolean | `false` | — | 自动算薪总开关 | — | — | 上游 ADR-01「默认关闭，避免上线即跑数」 |
| `hrm.payroll.schedule.zone` | String | `Asia/Shanghai` | 合法 `ZoneId` | 判定时区（同时派生 `attempt_date`） | 与数据源不一致会错点 | 同左 | 与 `serverTimezone=Asia/Shanghai` 一致（[application.yml:25](../hrm-server/src/main/resources/application.yml#L25)） |
| `hrm.payroll.schedule.tick-interval-ms` | long | `600000`（10min） | `[60000, 3600000]` | 轮询间隔（`fixedDelay`）；= 到点 / 补跑钟点 / **回收扫描**的**检测精度**（非重试间隔） | 到点/补跑延迟↑、DB 压力↓ | 精度↑、DB 压力↑ | 算薪为日粒度，10min 精度充分；单轮 `[推导]` ≈5s → 留 10× 余量 |
| `hrm.payroll.schedule.catch-up-time-of-day` | String | `""`（继承 `payroll_time`） | `""` 或 `HH:mm` | **补跑日的每日尝试钟点**（空 = 沿用该站 `payroll_time`）。**复-3 定稿判据** =「`now ≥ 当日 该钟点` **且**该 `(station_id, target_month)` **当日尚未尝试**」 | 补跑更晚 | 补跑更早（须晚于上游数据日结） | 用户未指定补跑钟点；与正常到点对称，故默认继承 `payroll_time`（§1.4 / §1.4.1 R-D2） |
| `hrm.payroll.schedule.max-stations-per-tick` | int | `0`（不限） | `≥ 0` | 单 tick 串行处理驿站上限 | 单轮更长、更易跨 tick | 单轮短、需多轮完成 | 0=不限：默认 S 小且 tick 充裕；仅当单轮逼近 tick 才设限（§4.3） |
| `hrm.payroll.schedule.inter-station-delay-ms` | long | `200` | `[0, 10000]` | 驿站间串行间隔（错峰节流） | 单轮更慢、DB 压力↓ | 更快、DB 压力↑ | 200ms → 稳态速率 ≈5 站/s，与 `hrm.algo.ratelimit.refillPerSecond=5` 同量级 |
| `hrm.payroll.schedule.running-timeout-minutes` | int | `30` | `≥ tick-interval` | 僵死 `RUNNING` **回收阈值 `T`**（需 ≥ 最坏单驿站耗时）；命中即执行**完整回收动作**（`status=FAILED` / `claim_key=NULL` / `fail_reason=STALE_RECLAIMED` + 告警），**回收后当日不再重试、次日补跑**（v1.3 复-1，§1.5 / §1.5.3） | 更容忍慢算薪；僵死占位更久；误杀概率↓ | 误杀慢任务概率↑（多实例下 W1） | 30min ≫ 单驿站量级（`[推导]` <1min）→ 单实例下「超时即真僵死」；§1.5.3 (4) |
| `hrm.payroll.schedule.stale-reclaim-enabled` | boolean | **`true`（v1.3 定稿）** | — | 僵死回收总开关。开启 = 僵死行**自动回收**（释放 `uk_claim` + 告警）→ **次日自愈**；关闭 = 不回收（须人工 C 档处置，**仍保留超期 `RUNNING` 告警兜底**，§13 R-C1②） | — | — | **单实例 `fixedDelay` 不重叠 → 回收安全（超时即真僵死）**，且为消除复评「静默失效」所必需（§1.5.3 (4)）；**承接主代理裁定默认 `true`（纠正方案 v1.4 的 `false`）**；多实例前须先补选主（U-12）或置 `false` 走人工（§1.5.1 P4 / 架构债 TODO-3） |
| `hrm.payroll.schedule.alert-after-consecutive-fail-days` | int | `3` | `[1, 365]` | **连续失败天数告警阈值**（达阈值 → 告警/转人工；**不停止重试**；**回收产生的 `STALE_RECLAIMED` 计入本计数**） | 更晚暴露系统性失败 | 更早告警、噪音↑ | 3 天 = 覆盖「周末 + 一次瞬时故障」仍不成功，视为需人工介入（**仅告警，不推翻 U-06 持续重试**） |
| `hrm.payroll.schedule.alert-repeat-interval-days` | int | `0`（仅首次达阈值时告警） | `≥ 0` | 告警**重复间隔**（>0 = 每 N 天重复一次；0 = 仅首次） | 提醒更少 | 提醒更频繁 | 防告警风暴：50 站全失败 × 每天 = 50 条/天；0 时仅阈值跨越当天各 1 条 |
| `hrm.payroll.schedule.notify-on-fail` | boolean | `true` | — | **失败 / 连续失败 / 僵死回收告警总开关**（v1.3 明确覆盖 `STALE_RECLAIMED` 回收告警，§1.5.3 / §13 R-C1） | — | — | 失败须可见，避免静默（Q9 留痕精神；复评点名的「静默失效」由此开关兜底） |

**已删除参数（v1.2，保留为历史说明）**：

| 参数名 | 原默认 | 删除理由 |
| ---- | ---- | ---- |
| `hrm.payroll.schedule.catch-up-window-hours` | `72` | 日粒度重试**无窗口上限**，`W` 概念不适用（§1.4 / §12.3 第 2 条） |
| `hrm.payroll.schedule.catch-up-window-min-hours` | `24` | 同上；`W_min` 下限保障随窗口取消（§12.3 第 2 条） |
| `hrm.payroll.schedule.max-retry-per-run` | `3` | 「总次数上限」与 U-06「持续重试至成功」冲突；其「防无限重试」职责改由 `alert-after-consecutive-fail-days` 以**告警（不阻断）**承担（§12.3 第 4 条） |

**已删除的接口参数（非 yaml，v1.3，复-2 / 主代理裁定）**：

| 参数名 | 原语义 | 删除理由 | 删除后行为 |
| ---- | ---- | ---- | ---- |
| `force`（`POST /api/v1/finance/payroll-runs/trigger` 入参） | 「跳过应用层日粒度短路、强制同日再试」 | 收敛后 `force=false/true` 在方案自列**全部四种情形**下**无可观测行为差异**（复评 §三 3.3）——同日重复终被 `uk_attempt` 硬拒绝，参数退化且易误导运维 | 端点入参**仅保留 `stationId` / `month`**；同日重复触发一律由 `uk_attempt` 硬拒绝回 **`9410`**（§1.4 / §8） |

> **残留项自检（复-4 必验）**：以下名称在本文件中**除上述两张历史说明表外，不得再作为参数出现**——`catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run`（v1.2 删除）与 `force`（v1.3 删除）。§2 现行参数清单仅含**上表 11 项**。

> **仓储层参数（表格数据，非超参）**：`station_payroll_setting.enabled / payroll_day / payroll_time / notify_enabled` 属业务配置，走数据库热改，不进 `application.yml`（对齐 [AlgoProperties.java:19](../hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L19) 的「业务口径走 DB」约定）。
> **`payroll_run` 结构变更（非 yaml 参数）**：v1.2 需新增 `attempt_date DATE NOT NULL` + `UNIQUE KEY uk_attempt (station_id, target_month, attempt_date)`（「每日至多一次」硬防线，§1.5）；§1.5.1 处置口径 P1/P2 另需 `owner_instance_id`（+可选 `heartbeat_at`）。均属**表结构**，交架构师 / 数据库工程师（本文件不落 DDL），不进 `application.yml`。
> **可选限速桶**（仅当引入精确速率控制时）：`bucket-capacity` / `refill-per-second` / `max-queue-wait-seconds`，命名与语义对齐 `hrm.algo.ratelimit.*`（RFC 2697，见 §5）。**默认不启用**——10min tick 本身即节流。

---

## 3. 边界用例（逐条给期望结果）

时区一律 `Asia/Shanghai`；**v1.3 日粒度重试（无窗口）+ 补跑钟点 `catch-up-time-of-day`**；`tick=10min`；`E=20, R=8`（除注明）。「当日」= `attempt_date = now` 的 `Z` 墙钟日。

| # | 场景 | 输入 | 期望结果 |
| - | ---- | ---- | ---- |
| 1 | 算薪日 = 31，当月 30 天 | `d0=31, m=2026-04` | `dueAt=2026-04-30 hh:mm`（钳位）；到点触发，`target_month=2026-04` |
| 2 | 算薪日 = 30，2 月非闰年 | `d0=30, m=2026-02` | `dueAt=2026-02-28 hh:mm` |
| 3 | 算薪日 = 29，闰年 / 非闰年 | `d0=29, m=2024-02` / `2026-02` | `2024-02-29` / `2026-02-28` |
| 4 | 算薪日 = 1，时间 00:00 | `d0=1, time=00:00` | `dueAt=当月 1 日 00:00`；判定正确 |
| 5 | **当天失败 → 次日补跑成功（U-06 核心）** | `d0=15, time=09:00`；08-15 09:05 尝试 `FAILED`；08-16 09:05 已恢复 | 08-15 仅 1 条 `FAILED`（`attempt_date=2026-08-15`）；08-16 恰 1 次 `CATCH_UP` → `SUCCESS`（`attempt_date=2026-08-16`）；`target_month=2026-08` |
| 6 | **同一天重复 tick 不重复执行（U-06 硬约束）** | 08-15 09:05 起连续 6 个 tick（至 10:05）均满足时刻条件 | 仅**首个** tick 产生 1 条尝试行；其余 tick 撞 `uk_attempt(s,m,2026-08-15)` → 短路，**零新增行、零重复算薪** |
| 7 | 同一 tick 两驿站同时到点 | 两站 `dueAt` 相同 | 按 `(dueAt, station_id)` 串行；各自独立事务，均 `SUCCESS`；单轮耗时 ≈ 2× 单站 |
| 8 | 某驿站算薪抛异常 | 站点 B 抛错 | B 记 `FAILED`（当日不重试），A/C 照常 `SUCCESS`；B **次日**补跑 |
| 9 | 该账期已存在已提交/已发布单据 | `(s,m)` 有 `PENDING_APPROVAL` 单 | 命中驿站级 9405 → `SKIPPED(BLOCKED_9405)`，**终止类，跨日不重试** |
| 10 | 管理员自动算薪前手工 `generate`（仍 `DRAFT`） | `(s,m)` 有 `DRAFT` 单 | **v1.1 默认口径 (a)**（§1.5.2）：自动路径**保护性 `SKIPPED(DRAFT_PROTECTED)`**（终止类），待人工处理 `DRAFT` 后运维手工重跑 |
| 11 | 同上但 `DRAFT` 已含 `source=MANUAL` 项 | 手工加扣款在 `DRAFT` 单内 | **数据丢失风险** → 保护性 `SKIPPED`（§7 R11、§1.5.2 (a)） |
| 12 | 管理员已 `submit`（`PENDING_APPROVAL`） | 非可覆盖态 | 9405 → `SKIPPED`（同 #9） |
| 13 | 时区边界：宿主 TZ=UTC | `now(UTC)=2026-04-05T01:00` → `now(Z)=2026-04-05T09:00` | 判定用 `Z`；`attempt_date=2026-04-05` 亦由 `Z` 派生（§7 R7） |
| 14 | 补跑期间管理员同时手工触发 | tick 与手工并发同 `(s,m)` | 两个唯一键：仅一方 INSERT 成功；另一方短路（回 `9410` 或 `SKIPPED`），**不重复生成** |
| 15 | 两 tick 理论重叠 | 同 `(s,m)` 同日并发 | 第二个 claim `INSERT` DuplicateKey → 短路；**零重复单据** |
| 16 | **连续失败 3 天 → 告警（U-06 补偿，不停止）** | `d0=1`；08-01/02/03 连续 `FAILED` | 08-03 后（连续 3 天）触发告警 1 次（`alert-after-consecutive-fail-days=3`）；**08-04 仍继续尝试**（用户要求持续重试）；`alert-repeat-interval-days=0` → 不重复刷屏 |
| 17 | 进程死在 `RUNNING` 后（**v1.3 完善：回收动作完整定义**） | `(s,m,D0)` 存在 `RUNNING` 且 `start_time < now−30min` | **回收**：`status→FAILED`、`claim_key→NULL`、`fail_reason=STALE_RECLAIMED`（§1.5.3）→ 释放 `uk_claim`；**当日 `uk_attempt(s,m,D0)` 仍占** → 当日不再试；**告警触发**（见 #26） |
| 18 | **算薪日 31 日遇 2 月（U-05 钳位）** | `d0=31, time=09:00, m=2026-02` | `dueAt=2026-02-28 09:00`（钳位），`target_month=2026-02`；到点触发 |
| 19 | **29 / 30 日缺日钳位** | `d0=29/30, time=09:00, m=2026-02` | 均钳位到 `2026-02-28 09:00`，`target_month=2026-02` |
| 20 | **跨月补跑：`target_month` 归属（U-06）** | `d0=31, time=09:00`；`dueAt=2026-08-31 09:00`；08-31 失败 → 09-01、09-02 补跑成功 | 所有尝试 `target_month=2026-08`（**不生成 9 月单**）；`attempt_date` 递增 `08-31→09-01→09-02` |
| 21 | W1 僵死回收误判（多实例 / 手工回收 + `D>T`） | A 持 `RUNNING`，B 判僵死并抢占 | 采纳 **P1**：A 的事务内行锁复核失权 → Tx2 回滚，**无重复工资单**；未采纳 P1：按 §1.5.1 时序 **可重复工资单**（登记残余风险） |
| 22 | W2 手工 `item-add` 与自动重建并发 | `(s,m)` 有 `DRAFT` 单 + 自动路径重建中 + 管理员 `item-add` | 口径 (a)：自动路径对 `DRAFT` **一律保护性 `SKIPPED`** → 不与手工并发；若 (a) 未采纳，则按 (b) `item-add` 前置校验 `RUNNING` 拒绝 |
| 23 | **同日两账期（补跑 + 新账期）** | 同站 `m=08` 补跑与 `m=09` 正常同日 | `uk_attempt` 含 `target_month` → 各占 1 槽、互不误拦（§1.4.1 表 5） |
| 24 | **次日仍未成功 → 继续补跑（不设上限）** | `d0=1`；08-01 起连续失败 10 天 | 每天恰 1 次尝试（10 行 `FAILED`），**无 `EXHAUSTED`**；第 11 天仍尝试 |
| 25 | **僵死回收后「次日可重试」（复-1 核心）** | 步骤 #17 回收完成；次日 `D1` 首个满足 `catch-up-time-of-day` 的 tick | `INSERT (s,m,D1, claim_key=m)` **成功**——`uk_attempt` 新日期放行 + 僵死行 `claim_key=NULL` 多 `NULL` 不撞 `uk_claim` → `generate` → `SUCCESS`；**次日自愈**（§1.5.3 时序步 5） |
| 26 | **僵死回收「告警必触发」（消除静默失效）** | 回收 CAS `affected=1` 且 `notify-on-fail=true` | **必推送 1 条失败告警**（含 `stationId` / `target_month` / `attempt_date` / `STALE_RECLAIMED`）；该 `FAILED` **计入** `alert-after-consecutive-fail-days` 连续失败计数（§1.5.3 / §13 R-C1） |
| 27 | **僵死回收「当日不重复」** | 回收后**同一日 `D0`** 再触发连续 6 个 tick | 每个 tick 的 `INSERT` 均撞 `uk_attempt (s,m,D0)` → 短路⑤ → **当日零新增行、零重复算薪**（回收只释放 `uk_claim`，不释放 `uk_attempt`，§1.5.3 (3)） |
| 28 | **补跑钟点由 `catch-up-time-of-day` 决定（复-3）** | `payroll_time=09:00`、`catch-up-time-of-day=10:30`；补跑日 `10:00` 的 tick | `10:00` tick **不触发**（`now < 10:30`）；`10:30` 后首个 tick 触发 `CATCH_UP`；若该参数为 `""` 则按 `09:00`（继承 `payroll_time`） |
| 29 | **回收开关关闭时的兜底告警** | `stale-reclaim-enabled=false` + 存在超期 `RUNNING` | 不执行回收（占位保留，须人工 C 档处置）；**仍须触发「超期 `RUNNING`」告警**（§13 R-C1②）——**不得因关闭回收而静默** |

---

## 4. 复杂度与性能分析

### 4.1 时间复杂度

| 环节 | 成本 | 说明 |
| ---- | ---- | ---- |
| 单 tick 扫描 | `O(S)` 次查询（配置表）+ `O(S)` 次内存判定 | `dueAt` 计算 `O(1)`；仅到点且认领成功的驿站进入算薪 |
| 单驿站算薪 | `O(E × (Q + R))` 读 + `O(E × (1 + R))` 单行写 | `Q≈5–6`（§0.1 ⑤）；写 = 每员工 1 主单 + R 明细 insert |
| 全量 `S` 驿站 | `O(Σ_s E_s × (Q + R))`（串行） | 逐驿站串行，无并发 |

### 4.2 量级估算示例（`S=50, E=20, R=8`）

| 指标 | 公式 | 推导值 `[推导]` |
| ---- | ---- | ---- |
| 单驿站写行数 | `E×(1+R)` | `20×9 = 180` 单行 insert |
| 单驿站读次数 | `1 + E×Q + R_items` | `1 + 20×5 + 8 ≈ 109` 次 SELECT |
| 全量写行数 | `S×E×(1+R)` | `9000` 单行 insert |
| 单驿站耗时 | `E×(1+R)×t_ins + E×Q×t_sel` | 取 `t_ins=0.3ms, t_sel=0.5ms` → `54 + 50 ≈ 104ms` |
| 全量（串行）单轮 | `S×单驿站` | `≈ 5.2s` |
| tick 利用率 | `单轮 / tick` | `5.2s / 600s ≈ 0.9%` |

**结论**：
- 当前**逐行 insert 在 `S=50, E=20, R=8` 量级完全可接受**（利用率 <1%）。
- **必须批量插入的阈值**：单次全量写行数 `> 5×10⁴`，或单轮耗时 `> 0.2×tick`（即 >2min）→ 评估 MyBatis `<foreach>` 批量插入（batch 500–1000）。反例：`E=200, R=10, S=50` → 全量 `50×200×11 = 1.1×10⁵` 行 → `[推导]` 单轮 `≈ 60–180s`，逼近 tick 上限，需批量 + 可能需延长 tick。登记 `TODO(扩展)` 交后端 + 数据库联评（P4）。
- **tick 固定成本极低**：每轮 1 次配置查询（`S` 行小表）。**注**：`enabled` 无索引（§0.1 ⑥），`S` 大时建议补 `KEY idx_station_payroll_setting_enabled(enabled)`（§7 R10）。

### 4.3 算薪日集中风险与错峰 / 限速建议

**风险**：`K` 个驿站被配成同一天同一时刻 → `dueAt` 相同 → 该 tick 全部成为候选，串行处理时间 `≈ K × 单驿站耗时`。

| 场景 | `K` | 单驿站 `[推导]` | 单轮串行 `[推导]` | 相对 tick=10min |
| ---- | ---- | ---- | ---- | ---- |
| 典型 | 50 | 104ms | 5.2s | 0.9%（安全） |
| 大站 | 50（E=200,R=10） | 760ms | 38s | 6.3%（安全） |
| 大站 + DB 慢 3× | 50 | 2.3s | 114s | 19%（仍安全，但余量收窄） |
| 大站 + tick 缩至 1min | 50 | 2.3s | 114s | **>100%（积压）** |

**建议（按优先级）**：
1. **错峰（首选，零算法成本）**：运营侧把驿站 `payroll_time` 分散到窗口（如 06:00–09:00，每站错开 ≥1min），最坏并发从 `K` 降到 `K×(tick/tick)` 即每 tick 命中 ≤ 若干站。
2. **逐驿站串行**（本方案默认，已内建）。
3. **单 tick 上限 + 顺延**：`maxStationsPerTick`（默认 0=不限）；仅在「单轮 > tick」时设值，超出部分下 tick 续跑（因 `now` 仍在窗口内，不会漏）。
4. **驿站间节流**：`interStationDelayMs`（默认 200ms）→ 稳态 ≤5 站/s，与 `hrm.algo.ratelimit.refillPerSecond=5` 同量级，避免瞬时打满 DB 连接池。
5. **精确限速（可选，默认关闭）**：若需速率保证，在 tick 内对「启动算薪」动作套令牌桶（`bucketCapacity` / `refillPerSecond` / `maxQueueWaitSeconds`），语义与 S8 一致（RFC 2697：突发 ≤ 桶深、长期平均 ≤ 速率）；超等待上限则顺延到下 tick（**丢弃而非无限积压**）。参数命名对齐 `hrm.algo.ratelimit.*`。

### 4.4 空间 / 存储增量（`S=50, E=20`）

| 表 | 行数公式 | 年增量 `[推导]` | 单行尺寸估计 | 年字节 `[推导]` |
| ---- | ---- | ---- | ---- | ---- |
| `payroll_run` | `S × 12 × (1 + 重试率×上限)` | ≈ 600–2000 行/年 | ~200B | < 0.5 MB |
| `payroll_log` | `S × E × 12 × (自动 1 + 人工 ≈5)` | ≈ `50×20×12×6 = 72,000` 行/年 | 300–800B（含 `before/after` JSON） | ≈ 22–58 MB |

**结论**：两者均**量级可控**。优化建议：`payroll_log.before/after` **仅在金额确实变更时写入**，并只存最小差异，避免 JSON 膨胀。

### 4.5 日粒度补跑对负载的影响（U-06 专属分析）

> **口径**：每驿站每账期每自然日至多 1 次尝试（§1.4）。故补跑负载与「失败持续天数 `K`」**线性**增长，而与 tick 数（144/天）**无关**。

| 场景 | 每日尝试次数 | 每日额外写行数 | 每日额外耗时 `[推导]` | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| 正常（全部当天成功） | `0` | 0 | 0 | 无补跑 |
| 少量失败（`N=5` 站） | 5 | ≤ 10（`RUNNING`+终态，每次 2 行） | `5×104ms ≈ 0.5s` | 可忽略 |
| **最坏（`N=50` 站连续失败）** | 50 | ≤ 100 | `50×104ms ≈ 5.2s/天` | 约 1 个 tick 的量级（§4.2），利用率 <1%/天 |
| 持续失败 `K` 天 | `50×K` 累计 | `100×K` 累计 | `5.2s×K` 累计 | 每次尝试 = 独立短事务 |

**关键结论 `[推导]`**：
- **负载可控的前提是「失败快速返回」**：单次尝试耗时 ≈ 单驿站成功耗时（104ms，§4.2）。此时「50 站连续失败」每天约 **5.2s**，**远低于 1 天**，不构成积压。
- **风险情形（登记 R-F1，见 §13）**：若失败模式为**长时间挂起**（DB 连接 timeout / 死锁等待）而非快速抛错，单次尝试可能拖到 `running-timeout-minutes`（30min）→ 50 站串行 = **25h/天 > 24h** → 当日无法完成、次日叠加。这是**执行超时**问题而非「重试次数」问题：即便 v1.1 的 `max-retry=3`，同 tick 内 50 站各挂 30min 同样积压。补偿建议见 §13（单次尝试执行超时 / 熔断 / 告警）。
- **无界重试的存储影响**：最坏「站点永久失败」→ 每站每年每账期 365×2 行，`S=50` → ≤ **3.65 万行/年**（≈7MB，与 §4.4 同量级）→ 可忽略；但 `payroll_run` 增长模式由 `S×12`（成功路径）变为 `S×失败天数`（失败路径），须靠告警后人工介入终止（§13）。
- **与 v1.1「每 tick 重试」的对比**：v1.1 若不做日内限次，50 站失败会在 72h 窗口内每 10min 重试（≈432 次/站）；**U-06 的「每日一次」使补跑负载从 432 次降至 `K` 次，是负载侧的净收益**。

### 4.6 僵死回收扫描成本对 tick 固定成本的影响（v1.3，复-1 专属分析）

> **口径**：回收扫描随**每个 tick** 执行（§1.3 伪代码），其频率 = tick 频率；扫描成本与 `payroll_run` 行数 `N_run` 及运行中行数 `R_run` 相关。

| 环节 | 成本 | `[推导]`（`S=50`） |
| ---- | ---- | ---- |
| 候选筛选 | 1 次 `SELECT … WHERE status='RUNNING' AND start_time < ?` | 全扫 `N_run`（≈600–2000 行/年，§4.4）或走索引 `(status, start_time)` 时 `O(R_run)`；`R_run` 通常 ≤ 并发站点数，稳态 ≈ 0–5 |
| 逐行回收 | 每命中 1 行 1 次 CAS `UPDATE`（主键定位） | 命中数回退率极低，稳态 0；**回退时才写** |
| 告警 | 每命中 1 行 1 次告警构造（受 `notify-on-fail` 管控） | 与命中数同阶 |
| 每 tick 增量 | 1 次候选查询 + （命中数）次 CAS | 无僵死时 ≈1 次轻查询；相对 §4.2 单轮 5.2s 可忽略 |

**结论 `[推导]`**：
- **回收扫描不改变 tick 固定成本量级**：无僵死时仅 1 次查询（返回 0 行）；有僵死时才产生 `O(命中数)` 次 CAS 与告警。
- **索引建议（登记，非阻断）**：`payroll_run` 在**成功路径**下规模小（§4.4），全扫可接受；但**持续失败路径**下 `N_run` 随失败天数线性增长（§4.5），当 `N_run` 增至 `> 10⁴` 量级时，建议补 `KEY idx_payroll_run_status_start (status, start_time)` 以将回收扫描降为 `O(R_run)`。属结构变更，交架构 / 数据库评估（登记 §9 `TODO(扩展)`）。
- **频率关系**：回收扫描频率与 `tick-interval-ms` **同源**——缩短 tick 会**同时**提高「补跑钟点检测精度」与「回收时效」（僵死占位更早被释放），但也会提高扫描频率；因单次成本极低（上表），10min 默认下无压力（§13 R-C2）。

---

## 5. 依据来源

| # | 依据 | 用途 | 来源 |
| - | ---- | ---- | ---- |
| ① | `@Scheduled.fixedDelay` = 「上一次执行**结束**到下一次执行**开始**的固定间隔」；`fixedRate` = 「两次调用之间固定周期」 | 选 `fixedDelay` 避免任务堆积时的重叠；说明轮询间隔为「执行后计时」 | Spring Framework `@Scheduled` Javadoc（[docs.spring.io](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)）；[Task Execution and Scheduling](https://docs.spring.io/spring-framework/docs/3.2.1.RELEASE/spring-framework-reference/html/scheduling.html) |
| ② | MySQL `UNIQUE` 索引**允许**含 `NULL` 的列存在多个 `NULL` 值（InnoDB/MyISAM 一致） | 支撑「`FAILED` 释放槽位（多 NULL）、`RUNNING/SUCCESS/SKIPPED` 占位（非 NULL 唯一）」 | MySQL 8.0 Reference Manual, [CREATE TABLE](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) / [CREATE INDEX](https://dev.mysql.com/doc/refman/8.4/en/create-index.html) |
| ③ | InnoDB 唯一约束冲突在**插入语句**处即报错回滚 | 支撑 claim「INSERT 即抢占」的原子互斥 | 同 ②；InnoDB 唯一索引语义 |
| ④ | `java.time`：`YearMonth.lengthOfMonth()`、`ZonedDateTime.of(..., ZoneId)` | `dueAt` 计算与月末钳位 | Java SE `java.time` API（Oracle docs） |
| ⑤ | `Asia/Shanghai` 自 1991 年起无夏令时，全年固定 UTC+8 | 支撑「无 DST 跳变」结论 | IANA Time Zone Database（[iana.org/time-zones](https://www.iana.org/time-zones)） |
| ⑥ | 令牌桶语义：突发 ≤ 桶深、长期平均 ≤ 补充速率 | 可选精确限速口径 | IETF [RFC 2697](https://www.rfc-editor.org/rfc/rfc2697) / [RFC 2698](https://www.rfc-editor.org/rfc/rfc2698)；与 [algo-hrm-server.md](algo-hrm-server.md) §11.8 同源 |
| ⑦ | 幂等 = 同一操作重复执行结果不变；`check-then-act` 在无唯一约束时存在竞态 | 支撑幂等分层与「普通索引不构成幂等」的断言 | 通用并发/幂等设计（无单一权威文档，属公认工程结论） |
| ⑧ | InnoDB 行级锁：`SELECT … FOR UPDATE` 对索引行加排他锁；`UPDATE … WHERE id=? AND status=?` 具 **CAS（compare-and-set）** 语义；不同事务对同一行的写被行锁串行化 | 支撑 §1.5.1 处置口径 P1「终态并入业务事务 + 行锁复核」与回收 CAS 的串行化 | MySQL 8.0 Reference Manual, [InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html) / [Locks Set by Different SQL Statements in InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-locks-set.html) |
| ⑨ | 分布式锁/租约（lease）中「持有者被误判过期」为经典问题；须以 **fencing token（单调递增栅栏）** 或受锁保护的状态校验防止过期持有者写入 | 支撑 W1 竞态分析与 P1/P2/P3 取舍（P3 唯一真正防过期写，但在此规模属过度设计） | M. Kleppmann, *How to do distributed locking*（2016，kleppmann.com）；M. Burrows, *The Chubby lock service for loosely-coupled distributed systems*, OSDI 2006（租约与序列号语义） |
| ⑩ | MySQL 8.0.13+ 支持**函数索引**（functional key parts）；未建函数索引时，对列施加函数（如 `DATE(col)=?`）会使该列索引无法用于过滤条件 | 支撑 §1.5「不以 `DATE(start_time)` 查询作硬防线」及降级方案的成本说明 | MySQL 8.0 Reference Manual, [CREATE INDEX](https://dev.mysql.com/doc/refman/8.0/en/create-index.html)（Functional Key Parts） |

> ②③⑧⑩ 已按官方文档核实（本轮检索命中 MySQL 官方手册原文；⑧ 为 InnoDB 行锁与 CAS 语义；⑩ 为函数索引语义）；① ③ 语义已核实。④⑤ 为 JDK/IANA 标准行为，未逐条开页核验，标注为「标准库/标准数据」。⑨ 为分布式系统经典文献（Kleppmann / Chubby），用于支撑 W1 分析与方案取舍，未逐段引用原文。

---

## 6. 可验证指标与基准数据（离线可复现设计）

> **未运行声明**：本机无 JDK / MySQL，**本轮未执行任何测试或 SQL**。下表「期望值」除注明出处外均为**按 §1–§4 公式推导**，标 `[推导]`；未来实现须以固定种子离线原型 + 服务器实测**逐位复现**后方可采信。

### 6.1 可判定指标（断言）

| # | 指标 | 断言（可判定） | 期望值 |
| - | ---- | ---- | ---- |
| A1 | 幂等（并发） | 对同 `(s,m)` 并发 2 次触发 | 恰 **1** 条 `SUCCESS`；`payroll` 行数 = `E_s`（无重复） |
| A2 | 幂等（跨日重复） | 已成功 `(s,m)` 在后续 N 个自然日再次评估 | 不新增 `SUCCESS`；短路③ / `uk_claim` 拦截 |
| A3 | **次日补跑成功（U-06 核心）** | 当天 `FAILED`，次日已恢复 | 次日恰 **1** 次 `CATCH_UP` → `SUCCESS`；累计 2 个 `attempt_date` |
| A4 | **持续补跑（无窗口、无上限）** | 连续失败 `K` 天 | 每天恰 1 次尝试；**无 `SKIPPED(EXHAUSTED)`**；第 `K+1` 天仍尝试 |
| A5 | 失败隔离 | 注入 1 站抛异常 | 其余站成功率 **100%**；该站 `FAILED`（当日不重试）、**次日**续跑 |
| A6 | **连续失败告警（不停止）** | 连续 3 天 `FAILED` | 达阈值告警 1 次；**第 4 天仍产生尝试**（尊重 U-06） |
| A7 | 到期精度 | 到点后首个 tick 执行 | 延迟 ∈ `[0, tickInterval + 单轮耗时]` |
| A8 | `dueAt` 钳位正确性 | `d0 > L` 的月份 | `dueAt` 落在当月最后一天，不跳月、不漏月 |
| A9 | 时区无关 | 宿主 TZ ≠ `Asia/Shanghai` | `dueAt` 与 `attempt_date` 判定**不随宿主 TZ 变化** |
| A10 | 复杂度 | 单轮 DB 写次数 | `= Σ_s E_s × (1 + R)`（单轮成功量级） |
| A11 | **W1 无重复（采纳 P1）** | 模拟 `D>T`：并发注入 B 的僵死回收 + A 的终态回写（§1.5.1 时序） | **恰 1 套**工资行；A 事务内行锁复核失权 → Tx2 回滚 *或* B 回收被行锁阻塞后读到 `SUCCESS` 不回收 |
| A12 | **每日至多一次（硬约束）** | 同日对同 `(s,m)` 触发 `M` 个 tick | 仅 **1** 条尝试行；其余撞 `uk_attempt` 短路，零新增 |
| A13 | **钳位确定性** | 穷举 `d0∈[1,31]` × 月长 `L∈{28,29,30,31}` | `d_eff ∈ [1, L]`；`dueAt` 恒在当月；**无「某月无 `dueAt`」**（§1.4.1） |
| A14 | **W2 保护** | `(s,m)` 存在 `DRAFT` + 并发 `item-add` | 口径 (a)：自动路径 `SKIPPED(DRAFT_PROTECTED)`，`item` 行数不因自动路径减少 |
| A15 | **同日两账期互不误拦** | 同站 `m=08` 补跑 + `m=09` 正常同日 | 2 条尝试行（`target_month` 不同）；`uk_attempt` 各自独立 |
| A16 | **跨月 `target_month` 归属** | `dueAt=2026-08-31`，09-01 / 09-02 补跑 | 所有行 `target_month=2026-08`；**不生成 9 月单** |
| A17 | **补跑日负载** | 50 站连续失败 1 天 | 尝试 ≤ 50 次、写 ≤ 100 行、耗时 `[推导]` ≈5.2s < 1 tick 量级（§4.5） |
| A18 | **僵死回收动作正确性（复-1）** | 对超期 `RUNNING` 行执行 `reclaimStale` | 该行 → `status=FAILED`、`claim_key=NULL`、`fail_reason=STALE_RECLAIMED`；`uk_claim` 槽位释放；`uk_attempt(s,m,D0)` 仍占（§1.5.3 (1)） |
| A19 | **回收后次日可重试（复-1）** | A18 完成后，次日 `D1` 触发 | 次日 `INSERT` **成功** → `generate` → `SUCCESS`；**不因僵死永久占位**（§1.5.3 步 5） |
| A20 | **回收当日不重复（复-1）** | A18 完成后，同日再触发 `M` 个 tick | **0** 新增尝试行（全部撞 `uk_attempt(s,m,D0)`）；零重复算薪（§1.5.3 步 4） |
| A21 | **回收告警必触发（消除静默失效）** | 回收 `affected=1` 且 `notify-on-fail=true` | 恰 **1** 条失败告警（含 `STALE_RECLAIMED`）；**记录既有告警通道**，不复现复评 §三 3.2 的「无 `FAILED` 行 → 告警不触发」 |
| A22 | **回收幂等（CAS）** | 同一僵死行在连续 `M` 个 tick 被扫描 | 仅**首个** tick `affected=1` + 告警 1 条；其余 `affected=0`（`status` 已非 `RUNNING`） |
| A23 | **补跑钟点（复-3）** | 显式 `catch-up-time-of-day=10:30`；补跑日 `10:00`/`10:35` 各 1 tick | `10:00` **不触发**；`10:35` 触发 `CATCH_UP`（`now ≥ 10:30` 且当日尚未尝试） |

### 6.2 基准数据集与推导期望值

- **合成集**（无真实业务数据）：`S=50, E=20, R=8`，固定种子派生员工/规则项（种子值待原型确定）；**最坏集中场景**：50 站同 `dueAt`；**补跑场景**：注入「前 `K` 天 50 站全失败」，验证日粒度重试、跨月归属与连续失败告警。
- **推导基准**（`[推导]`，供未来原型/实测比对）：

| 量 | 期望 |
| ---- | ---- |
| 单轮写行数 | `50×20×9 = 9000` |
| 单轮读次数 | `50×(1+20×5+8) ≈ 5450` |
| 单轮耗时 | `≈ 5.2s`（`t_ins=0.3ms, t_sel=0.5ms`） |
| 单轮产出 `SUCCESS` | 50 条 |
| 重复触发产出增量 | 0 |
| **补跑日写行数（50 站全失败）** | ≤ `100`/天（每次尝试 2 行：`RUNNING`→`FAILED`） |
| **补跑日耗时（50 站全失败）** | `[推导]` ≈ `5.2s`（§4.5） |

- **复现设计**：后续在 `hrm-dev/docs/algo-scripts/` 新增 **`s9-payroll-schedule.mjs`**（纯函数：`computeDueAt` / `clampDay` / `attemptDate(now)` / `attemptTimeOf(day, cfg)`（**复-3**：`payroll_time` vs `catch-up-time-of-day`）/ `shouldAttemptOnDay`（日粒度判定）/ `claimSemantics`（**双唯一键**仿真）/ `staleReclaimTimeline`（W1 7 步时序仿真）/ `reclaimAction`（**复-1**：回收动作 + 两键交互 + 次日放行 + 触发告警）/ `consecutiveFailAlert`（连续失败告警）），固定种子，`node` 无第三方依赖，纳入 `run-all.mjs`。**本轮受「只出算法文档」硬约束未创建**，登记 `TODO(扩展)`（§9 TODO-1）。

---

## 7. 对上游方案的修正建议

> 上游 = [payroll-automation-design.md](payroll-automation-design.md) v1.0。逐条给「问题 / 依据 / 建议」。**R1–R5、R11 为必改**（不改则自动算薪在多驿站 / 边界 / 并发下不可用或产生重复数据）；**R6–R10 为建议**；**R12 待裁定**。

| # | 级别 | 问题 | 依据 | 建议 |
| - | ---- | ---- | ---- | ---- |
| **R1** | **必改** | 上游称「单据级幂等靠 `idx_payroll_emp_month_bill`」，但该索引是**普通 `KEY`**，无唯一约束，「幂等」断言不成立 | [V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85) | 明示**单据级无 DB 幂等**；实际幂等由 §1.5 claim 唯一键承担。若要 DB 级单据幂等，须另立 `UNIQUE KEY`（DDL，C 档），且需先清理历史逻辑删除重复行 |
| **R2** | **必改** | `uk_payroll_run_success (station_id, success_key)` **仅 `SUCCESS` 写哨兵**，`RUNNING` 无约束 → 重叠 tick / 多实例可双执行 | §1.5；[MySQL UNIQUE 多 NULL](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) | 改 **claim 槽位**：`RUNNING`/`SUCCESS`/`SKIPPED` 均写 `claim_key=target_month`，仅 `FAILED` 置 NULL；`INSERT RUNNING` 独立短事务先提交 |
| **R3** | **必改** | 9405 判定账期全局级、不带 `stationId`，多驿站相互阻断 | [PayrollServiceImpl.java:154-162](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L154-L162)、[PayrollGenerateGuard.java:33-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L33-L52) | 重申上游 M1：9405 必须**按驿站收敛**，否则本方案「逐驿站自动算薪」只能成功第一个站点 |
| **R4** | **必改** | `dueAt` 在「`payroll_day > 当月天数`」时**无定义**（上游仅建议 1–28、DDL 无约束，U-05 未定） | §1.2；上游 U-05 | **U-05 已裁定：`payroll_day` 取 1..31，月末缺日钳位 `min(d0, L)`，定稿生效**（§1.2 / §12.1） |
| **R5** | **必改（已被 U-06 消解）** | §3.3「`now ≤ dueAt+W` 即错过」与「不自动跨月补历史账期」**互相矛盾**：`d0` 靠月末且 `W` 较大时窗口跨月，两种实现都「合规」 | §1.4、§1.4.1；上游 §3.3 | **U-06 裁定补跑改为「日粒度重试」后，窗口概念取消 → 矛盾自然消失**：`target_month` 恒 = `dueAt` 月，补跑只推后执行日、不回溯更早账期（§1.4 / §12.1）。原口径 A/B 讨论**作废**（§12.3 第 3 条） |
| **R6** | 建议（**v1.2 修订**） | 无**僵死 `RUNNING` 回收**：进程死在 RUNNING 会永久占位；**且僵死回收本身引入竞态 W1** | §1.5、§1.5.1、§1.6 | 保留 `running-timeout-minutes=30`、`stale-reclaim-enabled`；僵死记录置 `FAILED` 释放 `uk_claim` 槽位（当日 `uk_attempt` 仍占 → 次日补跑）；回收须配 §1.5.1 P1 避免误判并发。**v1.2 删 `max-retry-per-run`**——U-06 要求持续重试，改以 `alert-after-consecutive-fail-days` 告警（**不阻断**）（§13） |
| **R7** | 建议 | ADR-01 称「落库时间沿用 `LocalDateTime.now()`」，与判定时区 `Asia/Shanghai` 不一致；宿主 TZ≠+8 时 `due_at`/`start_time` 与判定口径错位，可观测性失真 | §1.1 约束①；上游 ADR-01「时区口径」 | 落库统一 `ZonedDateTime.now(zone).toLocalDateTime()`，全链路单一时区源 |
| **R8** | 建议 | `trigger_type` 的 `AUTO` / `CATCH_UP` 判定基准**未定义** | §1.4 | 采用 `(now - dueAt) ≤ tickInterval ? AUTO : CATCH_UP` |
| **R9** | 建议 | §5 把「未启用 / 超出窗口」也列为 `SKIPPED`，语义混淆（二者并未进入执行） | §1.6 归类表 | 「未启用 / 超窗口（自动）」**不产生运行记录**；`SKIPPED` 仅用于「已决定执行但被业务正常拦截」 |
| **R10** | 建议 | 轮询按 `enabled` 过滤，但 `station_payroll_setting` **无 `enabled` 索引**，且扫描未纳入 `station.status` | §0.1 ⑥；[V1__init_schema.sql:34](../hrm-server/src/main/resources/db/migration/mysql/V1__init_schema.sql#L34) | 补 `KEY idx_station_payroll_setting_enabled (enabled)`；扫描条件加 `station.status=1`（停用驿站不自动算薪） |
| **R11** | **必改** | 自动算薪对同 `(s,m)` 的 `DRAFT` 单**覆盖重建**，若管理员已在 `DRAFT` 录入手工加扣款（`source=MANUAL`），将被**静默物理删除** | [PayrollServiceImpl.java:601-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621)；上游 §3.4 自身备注「手工加扣款应在 DRAFT/REJECTED 之外录入」 | 自动路径在检测到「同 `(s,m)` 存在 `DRAFT` 且含 `MANUAL` 项」时**保护性 `SKIPPED`**（可配）+ 告警；或强制界面禁止在 `DRAFT` 录手工项 |
| **R12** | 待裁定 | 自动调度**未定义 `ruleId` 来源**：`generate` 不传 `ruleId` 时取**第一个启用规则**（全局，非按驿站），多驿站差异化规则超出现有 schema | [PayrollServiceImpl.java:149,556-563](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L149) | 明确自动算薪使用的规则（全局默认？按驿站配？）；若需按驿站差异化，属 schema 变更，须架构师评估 |

**汇总：12 条（必改 6：R1/R2/R3/R4/R5/R11；建议 5：R6/R7/R8/R9/R10；待裁定 1：R12）。** **v1.2 进度**：**R4 / R5 已由用户 U-05 / U-06 裁定**（R5 由新口径消解）；**R6 按 U-06 修订**（删 `max-retry-per-run`、补连续失败告警）；R1 / R2 / R3 / R11 仍为必改；R12 待裁定。

---

## 8. 与既有契约的一致性 / 冲突

1. **与 `db.md`**：本文不改表结构。`payroll_run` / `station_payroll_setting` 由上游 §3 承载；本文对 `payroll_run` 有**两项结构修正建议**——① 唯一键语义修正（R2，v1.1）；② **v1.2 新增 `attempt_date` 列 + `uk_attempt (station_id, target_month, attempt_date)`**（「每日至多一次」硬防线，§1.5）。均须回架构师 / 数据库工程师并在 B0 批次同步 `db.md`（属**方案内部修正**，非本文自行落改）。
2. **与 `api.md`**：财务段 `api.md` 尚未收录（上游 §4 已声明 B0 补录）；本文不改 `api.md`。**v1.3（复-2）**：手工触发端点 `POST /finance/payroll-runs/trigger` 的入参 `force` **已由主代理裁定删除**，入参仅 `stationId` / `month`；同日重复触发一律由 `uk_attempt` 硬拒绝回 `9410`（§1.4 / §2「已删除的接口参数」），B0 补录 `api.md` 时须同步该契约。
3. **与口径红线（P1 / R11 / R22）**：算薪日范围（U-05）与补跑模型（U-06）**已由用户裁定**，本文据此修订（钳位 / 日粒度重试；§12）。该裁定内容**非算法可自行变更**，后续如需调整须再经用户确认。
4. **与既有算法契约**：本文不改 `algo-hrm-server.md` 既有场景；新增参数段与 §11 体例一致，如需登记入 `algo-hrm-server.md` 须由主智能体回填（登记见 §9）。
5. **无自行落改**：本文全部为设计口径，未改任何代码 / SQL / 契约文件。

---

## 9. TODO(扩展) 与待裁定项

| # | 项 | 说明 | 归属 |
| - | ---- | ---- | ---- |
| TODO-1 | 离线原型 `s9-payroll-schedule.mjs` | 纯函数 `computeDueAt` / `clampDay` / `attemptDate(now)` / `attemptTimeOf(day, cfg)` / `shouldAttemptOnDay`（日粒度）/ **双唯一键 claim 仿真** / `staleReclaimTimeline`（W1 时序）/ `reclaimAction`（**复-1 回收动作 + 次日放行**）/ `consecutiveFailAlert`（连续失败告警）+ 固定种子；本轮受「只出文档」约束未创建（v1.3 已同步 §6.2） | 算法 |
| TODO-2 | 批量插入优化 | 当 `Σ E_s×(1+R) > 5×10⁴` 或单轮 > 0.2×tick 时评估 MyBatis `<foreach>`；须后端 + 数据库联评（P4） | 后端 + 数据库 |
| TODO-3 | 多实例选主 / 分布式锁 | 上游 U-12 架构债；本方案以 claim 唯一键兜底「不重复单据」；**多实例前须先补选主或置 `stale-reclaim-enabled=false`**（否则 W1 成立，§1.5.1） | 架构 |
| TODO-4 | 失败/耗尽告警通知类型 | 复用通知白名单扩展（上游 §4.5） | 后端 |
| TODO-5 | 按驿站差异化计薪规则 | 超出现有 schema（R12） | 架构 + 用户裁定 |
| **TODO-6** | `payroll_run` 增 owner 列 | W1 处置口径 P1/P2 需 `owner_instance_id`（+可选 `heartbeat_at`）；属结构变更，交架构 + 数据库评估并 B0 同步 | 架构 + 数据库 |
| **TODO-7** | 手工项并发保护（schema） | W2 缓解 (c)：单据明细层唯一键 / 乐观版本号；交数据库 + 后端联评（P4） | 数据库 + 后端 |
| ~~待裁定 U-05~~ | **已裁定（用户）**：`payroll_day` 取 **1..31**，月末缺日**钳位到当月最后一天** | §1.2 / §12.1 | 用户（已闭环） |
| ~~待裁定 U-06~~ | **已裁定（用户）**：补跑 = **日粒度重试**（次日补、失败次日再补、**每自然日至多一次**、无窗口上限、持续至成功） | §1.4 / §12.1；原「窗口 `W` / `W_min`」讨论作废（§12.3） | 用户（已闭环） |
| **TODO-8** | 「每日至多一次」降级方案 | 若架构拒绝新增 `attempt_date` 列：以「同日 `EXISTS` 查询」作日判据 + 保留 `uk_claim` 作并发互斥，单实例可用但多实例有 check-then-act 残余竞态（§1.5） | 架构 + 数据库 |
| **TODO-9** | 回收扫描索引（可选，v1.3） | 当失败路径使 `payroll_run` 行数 `> 10⁴` 时，补 `KEY idx_payroll_run_status_start (status, start_time)` 将僵死回收扫描降为 `O(R_run)`；属结构变更，交架构 + 数据库评估（§4.6） | 架构 + 数据库 |

---

## 10. 结论摘要

| 项 | 结论 |
| ---- | ---- |
| 调度模型 | 无状态到期判定 + 双唯一键 claim 抢占 + 逐驿站串行 + **日粒度补跑 + 每 tick 僵死回收扫描（v1.3）**；沿用上游 `@Scheduled(fixedDelay)` |
| `dueAt` | `min(payroll_day, 当月天数)` 钳位（**U-05 定稿**，`payroll_day ∈ 1..31`）；时区 `Asia/Shanghai`（无 DST）；落库须同源时区 |
| 幂等 | **双唯一键**：`uk_attempt (station_id, target_month, attempt_date)` 管「每日至多一次」、`uk_claim (station_id, claim_key)` 管跨日终态（成功/终止/在跑）；单据索引非唯一（R1） |
| **僵死回收（v1.3 复-1）** | 动作 = `status=FAILED` / `claim_key=NULL` / `fail_reason=STALE_RECLAIMED`（CAS）→ **释放 `uk_claim`、允许次日重试、回收命中即告警**；`stale-reclaim-enabled` 默认 **`true`**；单实例 `fixedDelay` 不重叠 → 超时即真僵死（§1.5.3） |
| **并发竞态 W1** | 僵死回收**误判** → 可重复工资单（§1.5.1 7 步时序）；**仅多实例 / 手工回收者 B 存在时成立**；处置 = **owner + 终态并入业务事务 + 行锁复核（P1）**；多实例前须补选主或置 `stale-reclaim-enabled=false`；需加列（TODO-6） |
| **手工竞态 W2** | `item-add` 与自动重建不互斥；默认「自动路径对含 `DRAFT` 的 `(s,m)` 一律保护性 `SKIPPED`」（§1.5.2） |
| **补跑（U-06 + 复-3 定稿）** | **日粒度重试**：当天未成功 → 次日补、失败次日再补，**每自然日至多一次**、**无窗口上限**、持续至成功；补跑钟点 = **`catch-up-time-of-day`（默认继承 `payroll_time`）**，判据含「**当日尚未尝试**」；`target_month` 恒 = `dueAt` 月；**无 `EXHAUSTED` 硬上限**（告警见 §13） |
| 异常隔离 | 逐驿站独立事务 + try/catch + **日粒度重试** + 僵死回收（**回收动作完整定义 + 必告警**；回收后当日不再试、次日补跑，§1.5.3） |
| 参数 | **11 项** `hrm.payroll.schedule.*` 全外置，**单一真源**（§2）。相对 v1.2：**增 0 / 删 0（yaml）**、**语义或依据更新 4**、**删除接口参数 `force` 1 项**（复-2）；历史已删 yaml 3 项（`catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run`） |
| 结构变更 | `payroll_run` **新增 `attempt_date` + `uk_attempt`**（每日至多一次，§1.5）；P1/P2 另需 `owner_instance_id`（TODO-6）；均交架构 + 数据库，本文件不落 DDL |
| 量级 | `S=50,E=20,R=8` → 9000 行/轮，`[推导]` ≈5.2s，利用率 <1%；补跑日最坏 ≤100 行/天、≈5.2s（§4.5）；`>5×10⁴` 行才需批量插入 |
| 上游缺陷 | **12 条**（必改 6 / 建议 5 / 待裁定 1）；**R4/R5 已由 U-05/U-06 裁定，R6 按 U-06 修订**（§7 汇总） |
| 衍生风险 | D1–D6（§11.4）+ **§13 风险与补偿建议**（R-F1 单次尝试挂起 / R-F2 无界重试资源 / R-D1·R-D2 补跑钟点 / **R-C1 僵死回收静默失效 · R-C2 回收扫描频率 · R-C3 误回收与 `fail_reason` 白名单**）；D1/D4 涉结构/行为口径，须架构 + 用户确认 |
| 状态 | 方案阶段产物 **v1.3**（依技术评审复评 复-1/复-3/复-4 + 主代理裁定修订），**待技术评审工程师（L8）重评**后方可报主智能体审批 |

---

## 11. 对技术评审必改 4/8 的整改响应

> 依据：[tech-review-payroll-automation.md](tech-review-payroll-automation.md)（结论等级「打回」）之 **必改项 4、8**——报告「退回对象」中列明「算法工程师：按必改项 4、8 补僵死回收竞态（W1）与月末窗口塌缩的论证 / 限定（可增补 `algorithm-payroll-scheduling.md`）」。
> 本节逐条给「评审原话要点 → 整改内容 → 落点章节 → 验收标准满足情况」。**本节只做已有文档的修订说明，不新增算法能力、不改上游方案文件、不落 DDL。**

### 11.1 必改 4（高）· 并发残余竞态（W1 僵死回收误判 / W2 手工路径）

**评审原话要点**：claim 槽位「至多一个 RUNNING/SUCCESS」的表述**未加限定**；僵死回收误判（W1，`running-timeout-minutes` 可能 < 实际耗时 → 被误判僵死 → 释放 → 抢占 → 并发 `generate`）与手工 `item-add` 不经 claim、与自动重建 `deleteExisting` 不互斥（W2）**未覆盖**。验收标准：风险登记新增 W1/W2（各给触发条件、影响面、缓解），并把「至多一个 RUNNING/SUCCESS」改为「**在无僵死回收误判且手工路径串行前提下**」。

**整改内容**：
1. **表述限定**：§1.1 约束 2、§1.5 防线表注、§10 摘要的「至多一次成功生成 / 跨实例互斥」一律补限定「**在无僵死 `RUNNING` 回收误判且手工写路径与自动路径串行的前提下**」。
2. **W1 完整时序**（新增 §1.5.1）：7 步交错时序（`T0` 起 claim → A 长事务 → B 判僵死释放 → B 抢占 → A 提交 → A 终态回写 DuplicateKey → B 成功），证明「回收误判 → 重复工资单」在 Layer 2 无唯一约束下**可发生**；并指出**唯一键的拦截滞后于危害**（步 5 已落重复，步 6 才报错），故不能以「有唯一键」宣称无风险。
3. **W1 处置口径**：候选 P1（owner 标识 + 终态并入业务事务 + 行锁复核，**默认**）/ P2（心跳续期，加固）/ P3（fencing token，过度设计）/ P4（明确接受风险 + 单实例开关，兜底）；残余风险与兜底（去重核对 + 告警 + TODO）一并登记。
4. **W2**（新增 §1.5.2）：触发条件、影响面（MANUAL 项被删 / 孤儿 item）、三级缓解 (a) 口径层「含 `DRAFT` 一律保护性 `SKIPPED`」（默认）/ (b) 入口加固校验 `RUNNING` / (c) schema 唯一键或版本号（登记 TODO-7）。
5. **参数复核联动**：细化 `running-timeout-minutes` 语义（`start_time` 与心跳**双超时**）、新增 `stale-reclaim-enabled`（§2、§11.3）。

**落点章节**：§1.1 约束 2、§1.4、§1.5（新增 §1.5.1 / §1.5.2）、§1.6 归类表、§2 参数表、§3 边界用例 #14/#15/#17/#21/#22、§5 依据 ⑧⑨、§6 指标 A11/A14、§7 R6、§9 TODO-3/6/7、§10 摘要。

**验收标准满足情况**：
- 「新增 W1/W2 触发条件 / 影响面 / 缓解」→ §1.5.1 时序表 + 处置表、§1.5.2 清单（**满足**）。
- 「表述加限定」→ §1.1 约束 2、§1.5 表注、§10（**满足**）。
- 「缓解为回收前复核 / 手工路径加锁或版本号」→ §1.5.1 P1（行锁复核）、§1.5.2 (b)/(c)（**满足**；其中 (c) 涉 schema，登记 TODO 不越界落 DDL）。

### 11.2 必改 8（中）· 月末有效补跑窗口塌缩

**评审原话要点**：`min(dueAt+W, 次月首日00:00)` 使月末 `d0` 的有效窗口可塌缩至分钟级，与「72h 覆盖周末停机」依据矛盾且**未登记**。验收标准：明确「有效窗口 = `min(dueAt+W, 次月首日00:00)`，月末 `d0` 下可短至分钟级」并纳入风险登记；或调整口径（如 `W` 起点改为次月首日起算）——**口径调整须用户裁定**，不得由方案自定。

**整改内容**：
1. **量化验算表**（新增 §1.4.1）：12 行样例（`d0∈{1,28,29,30,31}` × 月长 × `time`），给闭式 `有效窗口 = min(W, (L − d_eff)×24h + (24h − hh:mm))` 与逐行数值。
2. **边界证明**：**窗口恒 > 0，最小 1min**——钳位保证 `dueAt ≤ 当月最后一日 23:59 < 次月首日 00:00`；故「空窗口」**不可能**（对任务书「甚至为空」作了核验性更正）。
3. **风险登记 R-W2**：触发条件（`d0` 逼近月末 + `time` 偏晚）、影响面（自动补跑形同关闭、依赖手工）、量化证据（行 2/5 = 1min）。
4. **口径候选**（§1.4.1）：口径 A（下限保障 `W_min` + 有限顺延，**默认建议**）/ 口径 B（接受塌缩 + 运营兜底）；**明确「是否跨月顺延」属业务口径，须用户裁定 U-06，本方案不拍板**。
5. **组合边界矩阵**：`d0` × 月长 → 是否钳位、自然窗口、是否触发下限顺延。
6. **参数复核联动**：新增 `catch-up-window-min-hours`（`W_min`，默认 24h），`catch-up-window-hours` 语义改为「名义上限」。

**落点章节**：§1.2、§1.4（新增 §1.4.1）、§2 参数表、§3 边界用例 #18/#19/#20、§6 指标 A12/A13、§7 R5、§9 待裁定 U-06、§10 摘要。

**验收标准满足情况**：
- 「明确窗口可短至分钟级 + 纳入风险登记」→ §1.4.1 表 + 风险登记 R-W2（**满足**）。
- 「口径调整须用户裁定，不得由方案自定」→ §1.4.1 明确标注 U-06 待裁定，口径 A 为**建议值**非定稿（**满足**）。

> **v1.2 更新**：本节（必改 8 · 窗口塌缩）的全部结论**已被用户 U-06 裁定整体作废**——补跑改为**日粒度重试**后**无「小时窗口」**，塌缩问题不复存在（§1.4.1 / §12.3 第 1、2、12 条）。本节仅作 v1.1 历史整改记录保留。

### 11.3 参数复核结论

| 参数 | 原默认 | 复核结论 | 变更 |
| ---- | ---- | ---- | ---- |
| `enabled` | `false` | 保留 | 无 |
| `zone` | `Asia/Shanghai` | 保留 | 无 |
| `tick-interval-ms` | `600000` | 保留（W1 缓解不依赖缩小 tick；fixedDelay 单向） | 无 |
| `catch-up-window-hours` | `72` | 保留数值；**语义细化**为「名义上限」，受 `W_min` 下限保障 | 语义 |
| `max-stations-per-tick` | `0` | 保留 | 无 |
| `inter-station-delay-ms` | `200` | 保留 | 无 |
| `max-retry-per-run` | `3` | 保留 | 无 |
| `running-timeout-minutes` | `30` | 保留数值；**语义细化**：回收需 `start_time` 与心跳**双超时** | 语义 |
| `notify-on-fail` | `true` | 保留 | 无 |
| `catch-up-window-min-hours`（新） | — | 新增 `W_min=24`（口径 A 下限，`0` = 关闭） | **新增** |
| `stale-reclaim-enabled`（新） | — | 新增 `true`（单实例兜底开关） | **新增** |

**结论**：原 9 项默认值**均保留**（无因 W1/W2 或窗口塌缩而必须调默认值的硬理由）；仅 2 项**语义细化** + 2 项**新增**。

> **v1.2 更新**：上表为 **v1.1** 的复核结论；**v1.2 依 U-05 / U-06 重新置定参数集**（**删 3 / 新增 3 / 语义变更 3，最终 11 项**），**一律以 §2 为准**：`catch-up-window-hours`、`catch-up-window-min-hours`、`max-retry-per-run` **已删除**；新增 `catch-up-time-of-day` / `alert-after-consecutive-fail-days` / `alert-repeat-interval-days`（见 §2 / §12.1）。

### 11.4 衍生风险（本次整改新发现）

| # | 衍生风险 | 说明 | 归属 |
| - | ---- | ---- | ---- |
| D1 | owner/心跳列需结构变更 | W1 处置 P1/P2 需 `payroll_run` 增 `owner_instance_id`（+可选 `heartbeat_at`），属结构变更 | 架构 + 数据库（不落 DDL） |
| D2 | 终态回写并入业务事务的跨层耦合 | P1 需在 `generate` 事务内对 `payroll_run` 加行锁复核，业务层与调度表耦合；若架构不允许，退回 P4 兜底 | 后端 + 架构 |
| ~~D3~~ | ~~口径 A 的跨月扫描与「单月评估」实现冲突~~ | **v1.2 作废**：窗口 / 下限顺延取消，不存在跨月扫描（§12.3 第 2 条） | 架构（已消解） |
| D4 | 「含 `DRAFT` 一律保护性 `SKIPPED`」改变既有行为 | 与上游 §3.4「仅含 MANUAL 才保护」不同，影响边界用例 #10，须架构 / 用户确认 | 架构 + 用户 |
| D5 | 心跳续期的 tick 固定成本 | RUNNING 行每 tick 续期增 1 次 UPDATE；量大时叠加 tick 固定成本（量级仍 <1%，§4） | 后端 |
| D6 | 两账期同 tick 评估的 9405 交互（**v1.2 保留并强化**） | 无窗口概念下，补跑账期（如 `m=08`）与新月账期（`m=09`）**可能同日并存**（§1.4.1 表 5）；`uk_attempt` 含 `target_month` 故互不冲突；但 9405 按月**全局**判定须先按驿站收敛（R3），否则 `m=08` 补跑会误伤 `m=09` | 后端 |

**自检声明**：本节所列整改**仅修改本文件 `algorithm-payroll-scheduling.md`**，未改上游方案、迁移脚本、代码或契约文件；未执行 git / 部署 / MCP；未连接数据库或运行测试。所有新结论均标 `[推导]` 或给官方来源（§5 ⑧⑨），无编造。

---

## 12. 对用户 U-05 / U-06 裁定的落地响应

> 来源：**用户裁定**（经主智能体转达，口径红线 §11.4 / P1）。本节**逐条说明模型如何满足**，并列出**因口径变更而失效的旧结论**（§12.3）。**本节不新增算法能力、不改上游文件、不落 DDL。**

### 12.1 逐条落地响应

**U-05（算薪日范围与月末语义）**：

| 序 | 裁定内容 | 落地设计 | 落点 |
| - | ---- | ---- | ---- |
| ① | `payroll_day` 允许 **1..31** | 不再限制 1–28；`validate(s)` 校验范围 `[1, 31]`，越界记 `SKIPPED(CONFIG_INVALID)` | §1.2 表、§1.3 |
| ② | 当月无该日 → **钳位到当月最后一天** | `d_eff = min(d0, YearMonth.lengthOfMonth())`；`dueAt` 用 `d_eff` 构造，钳位只改「日」不改账期 | §1.2、§1.4.1（1） |
| ③ | 钳位日**同一时刻**执行 | `dueAt = ZonedDateTime.of(Y, M, d_eff, hh, mm, …, Z)` | §1.2、§1.4.1（1） |

**U-06（补跑模型改为日粒度重试）**：

| 序 | 裁定内容 | 落地设计 | 落点 |
| - | ---- | ---- | ---- |
| ① | 算薪日当天未成功 → **次日补跑** | 判定链以「`day ≥ dueAt 日` ∧ `now ≥ atTimeOf(day,hh,mm)`」触发；当天未成功则该 `(s,m)` 无 `SUCCESS` | §1.3、§1.4 |
| ② | 补跑失败 → **次日再次补跑** | `FAILED` 仅释放 `uk_claim`（`claim_key=NULL`），**不释放**当日 `uk_attempt`；**次日新 `attempt_date`** 使 INSERT 成功 → 自动续跑 | §1.4、§1.5 |
| ③ | **同一驿站同一账期每自然日至多一次** | **硬防线 = `UNIQUE (station_id, target_month, attempt_date)`**；同日重复 tick 撞 1062 短路；`FAILED` 不释放当日槽位 | §1.5（判定机制）、§2 |
| ④ | **`target_month` 恒为 `dueAt` 所在月** | `target_month = YearMonth.from(dueAt)`，与 `attempt_date` 解耦；跨月补跑不改变账期 | §1.4、§1.4.1（2） |
| ⑤ | **无窗口上限、持续至成功** | 删除 `W` / `W_min`；`FAILED` 不产生终止；仅 `SUCCESS` 与终止类 `SKIPPED` 停止 | §1.4、§2（已删除参数） |
| ⑥ | 持续失败需**告警/可配补偿**（不推翻口径） | 新增 `alert-after-consecutive-fail-days`（默认 3）+ `alert-repeat-interval-days`；**只告警、不停止** | §2、§13 |

**替换 / 调整的参数**（详见 §2）：
- **语义移除**：`catch-up-window-hours`（72h）、`catch-up-window-min-hours`（24h）→「窗口」概念消失。
- **删除**：`max-retry-per-run`（3）→ 总次数上限与 U-06 冲突；其「防无限重试」职责改由 `alert-after-consecutive-fail-days` 以**告警（不阻断）**承担。
- **新增**：`catch-up-time-of-day`、`alert-after-consecutive-fail-days`、`alert-repeat-interval-days`。
- **语义细化**：`tick-interval-ms`（改为检测精度）、`running-timeout-minutes`（回收后当日不重试）、`notify-on-fail`（含连续失败告警）。

### 12.2 「每日至多一次」判定机制与结构需求（结论）

| 项 | 结论 |
| ---- | ---- |
| **最终选定方案** | **唯一键硬防线** `UNIQUE (station_id, target_month, attempt_date)` + 前置 `existsSuccess` / `existsTerminalSkip` 查询优化 |
| **是否需要新增表列** | **需要**：`payroll_run.attempt_date DATE NOT NULL`（`Z` 墙钟派生） |
| **是否需要新增索引** | **需要**：`UNIQUE KEY uk_attempt (station_id, target_month, attempt_date)`（与既有 `uk_claim` **正交共存**） |
| **为什么不用 `claim_key` 复合** | `claim_key` 为 `CHAR(7)`，装不下日期（≥17 字符），且破坏既有契约（事实核验，§1.5） |
| **降级方案** | 架构拒绝新增列时：同日 `EXISTS` 查询 + 保留 `uk_claim`；单实例可用，多实例有残余竞态（TODO-8） |
| **归属** | 结构变更交**架构师 / 数据库工程师**（B0 批次同步 `db.md`）；**本文件不落 DDL**（P4 结构先行） |

### 12.3 因口径变更而失效的旧结论清单（**明确作废，不得沿用**）

| # | 旧结论（v1.0 / v1.1） | 出处（旧） | 作废理由 | 新口径 |
| - | ---- | ---- | ---- | ---- |
| 1 | 补跑窗口 `[dueAt, dueAt+W]`，`W` 默认 72h | §0 条 5、§1.4、§2 | U-06：无窗口上限 | §1.4（无窗口） |
| 2 | 有效窗口 = `min(dueAt+W, 次月首日00:00)`；月末可短至 1min；口径 A/B（`W_min` 下限保障 + 跨月顺延） | §1.4.1、§3 #18/#19/#20、§6 A12/A13、§2 `catch-up-window-*` | 小时窗口概念取消 | §1.4.1（改写为钳位 / 跨月验算） |
| 3 | R5「窗口跨月 vs 不补历史账期」矛盾 | §7 R5 | 新口径无窗口 → 矛盾消解 | §7 R5（已消解） |
| 4 | `max-retry-per-run=3` 总次数上限；`SKIPPED(EXHAUSTED)` | §2、§1.6、§7 R6 | U-06：持续重试，无硬上限 | §1.6（删）、§2（删）、§13（告警替代） |
| 5 | 失败重试「间隔 = 一个 tick」（10min） | §1.6「重试」行 | 改为日粒度 | §1.6 新行 |
| 6 | 「窗口外自动路径不动作、不产生运行记录」 | §1.4、§1.6、§3 #6 | 无窗口 | §1.4 |
| 7 | `trigger_type = (now−dueAt) ≤ tickInterval ? AUTO : CATCH_UP` | §1.4 | 改为日粒度 | §1.4 新定义 |
| 8 | 指标 A3「窗内补跑」/ A4「窗外零动作」/ A6「重试上限」 | §6.1 | 窗口 / 上限取消 | §6.1 A3/A4/A6 重写 |
| 9 | 边界用例 #6「重启落窗口外无动作」、#18/#19/#20「窗口塌缩 / 下限保障」 | §3 | 窗口取消 | §3 #5/#6/#18~#24 重写 |
| 10 | 待裁定 U-05（1–28 vs 1–31 vs 月末） | §9 | **已裁定** | §9（已闭环）、§12.1 |
| 11 | 待裁定 U-06（`W` / `W_min` 与跨月顺延） | §9 | **已裁定并被新口径替换** | §9（已闭环）、§1.4 |
| 12 | 「月末窗口塌缩 → 自动补跑形同关闭」（风险 R-W2） | §1.4.1 风险登记 | 无窗口 → 不存在塌缩；最坏仅延迟一天 | §1.4.1（4）R-D1 |

> **保留不变**（不因 U-05 / U-06 作废）：claim 槽位协议（§1.5，`uk_claim` 语义与 W1 / W2 分析不变）、僵死回收与 P1 处置（§1.5.1）、W2 手工路径口径（§1.5.2）、时区口径（R7）、逐驿站串行与异常隔离（§1.6）、参数外置原则、`TODO-1~7`。

---

## 13. 风险与补偿建议（**只建议、不推翻用户口径**）

> 定位：U-06 要求「持续重试至成功、无窗口上限」。本节**尊重该裁定**，仅登记其**副作用**并给**可配补偿**（告警 / 超时 / 熔断），**不引入硬停止上限**。

| # | 风险 | 描述 | 补偿建议（可配，默认不阻断） | 归属 |
| - | ---- | ---- | ---- | ---- |
| **R-F1** | **单次尝试长时间挂起 → 当日积压** | 若失败模式为 DB / 网络长时间挂起而非快速抛错，单次尝试可能拖至 `running-timeout-minutes`（30min）；`N=50` 站串行 = 25h/天 > 24h → 当日无法完成、次日叠加（§4.5） | ① 为单次 `generate` 设**执行超时**（事务 / 连接超时，如 `attempt-timeout-seconds`，建议值由后端定，登记 `TODO(扩展)`）；② 超时即转 `FAILED`，纳入次日重试；③ 与 `max-stations-per-tick` 联用限制单轮 | 后端 + 架构 |
| **R-F2** | **无界重试的资源与告警风暴** | 「持续重试」下永久失败站每天尝试；50 站全失败 → 每天 50 条告警（若逐条发），`payroll_run` 行数按失败天数线性增长（§4.5） | ① `alert-after-consecutive-fail-days`（默认 3）：仅**连续失败**达阈值才告警；② `alert-repeat-interval-days`（默认 0）：**同一 `(s,m)` 仅首次达阈值告警一次**，避免刷屏；③ 运维按告警**人工介入**（修数据 / 停该站自动算薪），而非算法硬停 | 后端 + 运维 |
| **R-F3** | **告警阈值误配 → 漏报或噪音** | 阈值过小 → 瞬时抖动即告警；过大 → 系统性故障迟报 | 阈值**全外置可配**；默认 3 天（覆盖周末 + 一次瞬时故障）；上线后按实际抖动率调参 | 运维 + 算法 |
| **R-D1** | **补跑钟点固定 → 最坏延迟一天** | `payroll_time` 极晚（如 23:59）且该分钟停机 → 当天错过、次日同钟点再试 | 无永久漏跑（仅延迟一天）；如需更快，可前移 `payroll_time` 或配置 `catch-up-time-of-day`（须晚于数据日结） | 运营 |
| **R-D2** | **`catch-up-time-of-day` 前移过早 → 数据未就绪** | 若补跑钟点早于上游考勤 / 排班日结，算薪取数不全 | 默认继承 `payroll_time`（不改变业务节奏）；仅在有数据日结保证时才前移 | 运营 + 后端 |
| **R-D3** | **`uk_attempt` 需结构变更 → 实施排期依赖** | 「每日至多一次」硬防线依赖新增列与唯一键（§12.2） | 交架构 / 数据库工程师 B0 批次；若排期受限，用降级方案（TODO-8）并在单实例下上线，**多实例前必须补硬防线** | 架构 + 数据库 |
| **R-C1** | **僵死回收关闭 / 未命中 → 静默失效（复评 §三 3.2 点名的卡死场景）** | 若 `stale-reclaim-enabled=false`，或回收因阈值误配 / 扫描缺失而未命中，`RUNNING` 僵死将**永久占位** `uk_claim` → 该 `(s,m)` **自动补跑中止**；且**无 `FAILED` 行** → 连续失败告警**不触发** → **静默失效** | ① **默认开启回收**（`true`），回收命中即写 `FAILED`（`STALE_RECLAIMED`）并**主动告警**（§1.5.3）；② **无论开关状态**，保留「**超期 `RUNNING`**」独立告警（受 `notify-on-fail` 管控）作兜底观测——**即使不回收也不得静默**（§3 #29）；③ `running-timeout-minutes` 全外置可配 | 后端 + 运维 |
| **R-C2** | **回收扫描频率与 tick 的关系（v1.3 衍生）** | 回收扫描频率 = `tick-interval-ms` 频率；缩短 tick 提升回收时效，但提高扫描次数（§4.6） | 单次扫描成本极低（无僵死时 1 次轻查询，§4.6）；失败路径使 `payroll_run` 增至 `>10⁴` 时补 `KEY idx_payroll_run_status_start`（TODO-9） | 后端 + 数据库 |
| **R-C3** | **误回收判定在多实例下的残余 + `fail_reason` 白名单（v1.3 衍生）** | 单实例 `fixedDelay` 不重叠 → 超时即真僵死（无第二执行者 B）；但**多实例 / 手工回收路径也执行回收**时，`T <` 实际耗时仍可误判 → W1（§1.5.1） | 多实例前补选主 / 置 `stale-reclaim-enabled=false`（TODO-3）；`fail_reason` 白名单须含 `STALE_RECLAIMED`，便于审计**区分真僵死与误回收** | 架构 + 后端 |

**明确声明**：以上均为**补偿性建议**，**不构成对 U-06「持续重试至成功」的修改**；是否采纳由主智能体 / 用户裁定。

---

## 14. 对复评复-1/复-3/复-4 的整改响应（v1.3）

> 依据：[tech-review-payroll-automation.md](tech-review-payroll-automation.md)「**复评（方案 v1.4 ｜ 2026-09-27）**」§七 7.1 **新发现必改项 复-1~复-4** 与 §三 **独立验算**；口径裁定来源 = **主代理统一裁定**（口径红线 §11.4 / P1，与架构师 v1.5 口径一致）。
> 本节逐条给「复评原话要点 → 整改内容 → 落点章节 → 结论」，并列出**因本次修订而作废的旧表述**（§14.5）。**本节只修订本文件，不新增算法能力、不改上游方案 / 迁移脚本 / 契约，不落 DDL。**

### 14.1 复-1（中，倾向高）· `stale-reclaim-enabled` 默认值 + 「`RUNNING` 僵死卡死」未登记

**复评原话要点**：方案单实例默认 `false` vs 算法默认 `true` **冲突**；「`RUNNING` 僵死 + 回收关闭 → `uk_claim` 永久占位 → 自动补跑中止、且无 `FAILED` 行致告警不触发 → **静默失效**」未显式登记。

**结论：已整改（算法侧闭环）。**

| 复评验收要点 | 整改内容（v1.3） | 落点 |
| ---- | ---- | ---- |
| ① 该参数默认值与理由，两文件一致 | 默认 **`true`** 定稿（承接主代理裁定，纠正方案 v1.4 的 `false`）；理由 = **单实例 `fixedDelay` 不重叠 → 回收安全**且为消除静默失效所必需 | §2 参数表 `stale-reclaim-enabled` |
| ② 卡死场景风险登记 | 新增 **R-C1**（触发=回收关闭/未命中；影响=该 `(s,m)` 自动补跑中止、无告警；缓解=默认开启回收 + 命中必告警 + 无论开关均保留「超期 `RUNNING`」兜底告警） | §13 R-C1、§3 #29 |
| 回收动作**完整定义** | `status=FAILED` / `claim_key=NULL` / `fail_reason=STALE_RECLAIMED`（CAS）→ **释放 `uk_claim`、允许次日重试、命中即告警** | §1.5.3 (1)、§1.5、§1.6 归类表、§2 `running-timeout-minutes` |
| 「回收 → 次日可插、当日不重复」时序 | 6 步时序表 + **两唯一键交互**论证（次日 `attempt_date` 新键值放行 `uk_attempt`；`claim_key=NULL` 多 NULL 放行 `uk_claim`；当日 `uk_attempt` 仍占 → 零新增） | §1.5.3 (2)(3)、§3 #25/#27、§6.1 A18~A20 |
| 单实例回收安全立论 | `fixedDelay` 不重叠 → 回收与执行同线程 → **超时即真僵死（无 B）** | §1.5.3 (4)、§1.7、§10 |
| 保留 v1.1 的 W1/W2 | W1/W2 时序**原样保留**；明确 W1 **仅多实例 / 手工回收者 B 存在时成立**，多实例仍登记架构债（TODO-3） | §1.5.1、§1.5.2、§10、§13 R-C3 |
| 告警**必触发** | 回收 CAS `affected≥1` 即触发失败告警（受 `notify-on-fail` 管控），并计入连续失败计数 | §1.5.3 (1)、§2 `notify-on-fail`、§3 #26、§6.1 A21 |
| 衍生风险（新发现，只建议） | R-C2 回收扫描频率与 tick 关系；R-C3 误回收判定残余 + `fail_reason` 白名单 | §4.6、§13 R-C2/R-C3、§9 TODO-9 |

### 14.2 复-3（中）· 补跑触发时刻口径分歧 + 缺 `catch-up-time-of-day`

**复评原话要点**：方案「`now ≥ dueAt`」可在次日 00:0x 触发，早于上游日结；算法「当日同钟点」更晚；两文件分歧且方案缺该参数。**主代理裁定统一**为 `catch-up-time-of-day`（默认继承 `payroll_time`），判据含「当日尚未尝试」。

**结论：已整改（算法侧闭环）。**

| 项 | v1.3 定稿 |
| ---- | ---- |
| 时刻口径 | **当日尝试钟点** = `day == dueAt 日 ? payroll_time : catch-up-time-of-day`（后者 `""` 继承 `payroll_time`）；**单一真源 = `catch-up-time-of-day`** |
| 判定式 | `day ≥ dueAt 日` ∧ `now ≥ 当日尝试钟点` ∧ **该 `(station_id, target_month)` 当日尚未尝试**（`uk_attempt` 硬防线） ∧ 无 `SUCCESS`/终止类 `SKIPPED` ∧ 配置合法 |
| 落点 | §1.4（模型 + 判定表 + 尝试时刻行）、§1.3 伪代码（`attemptTimeOf`）、§1.4.1 R-D1、§2 参数表、§3 #28、§6.1 A23 |
| 修正的 v1.2 表述 | 「次日**同一钟点**再试」→ 改为「次日**在当日 `catch-up-time-of-day` 之后的首个 tick**」（§14.5 第 1 条） |

### 14.3 复-2（中）· `force` 参数删除（主代理裁定）

**复评原话要点**：`force=true/false` 在方案全部四种情形下**无可观测差异**，退化为无效参数，易误导运维。**主代理裁定：删除 `force`。**

**结论：已整改（算法侧清除 + 契约同步声明）。**

- **删除理由**：同日重复触发已由 `uk_attempt (station_id, target_month, attempt_date)` 唯一键**硬拒绝回 `9410`**；应用层短路与否不改变可观测结果（复评 §三 3.3）。
- **落点**：§1.4 手工端点说明（明确「本节不涉及 `force` 语义」）、§2「**已删除的接口参数**」表、§8 契约一致性（`api.md` B0 补录须同步入参仅 `stationId` / `month`）。
- **残留自检**：本文除 §2 历史说明表外，**不再出现** `force` 作为参数或流程要素。

### 14.4 复-4（低）· 参数命名 / 清单一致性（**最终清单**）

**复评原话要点**：方案与算法**参数名集合不同**（`alert-after-fail-days` vs `alert-after-consecutive-fail-days`；方案有 `auto-retry-suspend-after-fail-days`、算法有 `catch-up-time-of-day` / `alert-repeat-interval-days`）。**主代理裁定：以算法参数清单为单一真源。**

**结论：已整改。** 命名统一与旧项处置：

| 架构方案 v1.4 旧名/旧项 | 归并处置（v1.3） |
| ---- | ---- |
| `alert-after-fail-days` | **改名**为算法名 `alert-after-consecutive-fail-days`（默认 `3`） |
| `auto-retry-suspend-after-fail-days` | **删除**：其「暂停自动重试」语义与用户 U-06「持续重试至成功」冲突；「防无限重试」职责由 `alert-after-consecutive-fail-days` 以**告警（不阻断）**承担。**如需保留「熔断暂停」能力须用户裁定，算法侧不定义** |
| `catch-up-time-of-day` / `alert-repeat-interval-days` | 架构方案**补入**（采算法默认：`""` / `0`） |

**最终参数清单（`hrm.payroll.schedule.*`，**11 项**，逐项名 / 类型 / 默认值 / 范围，详见 §2）**：

| # | 参数名 | 类型 | 默认值 | 取值范围 |
| - | ---- | ---- | ---- | ---- |
| 1 | `enabled` | boolean | `false` | — |
| 2 | `zone` | String | `Asia/Shanghai` | 合法 `ZoneId` |
| 3 | `tick-interval-ms` | long | `600000` | `[60000, 3600000]` |
| 4 | `catch-up-time-of-day` | String | `""`（继承 `payroll_time`） | `""` 或 `HH:mm` |
| 5 | `max-stations-per-tick` | int | `0`（不限） | `≥ 0` |
| 6 | `inter-station-delay-ms` | long | `200` | `[0, 10000]` |
| 7 | `running-timeout-minutes` | int | `30` | `≥ tick-interval` |
| 8 | `stale-reclaim-enabled` | boolean | `true` | — |
| 9 | `alert-after-consecutive-fail-days` | int | `3` | `[1, 365]` |
| 10 | `alert-repeat-interval-days` | int | `0` | `≥ 0` |
| 11 | `notify-on-fail` | boolean | `true` | — |

**相对 v1.2 的增删改统计**：**增 0 / 删 0（yaml）/ 默认值变更 0 / 语义或依据更新 4**（§2）；另**删除接口参数 `force` 1 项**（非 yaml，复-2）。**残留项自检**：`catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run` / `force` **均不再作为现行参数**（仅存于 §2 两张历史说明表）。
**低风险待办 ①（算法侧）**：已把 §1.3 / §1.5 对**已删除索引** `idx_payroll_run_station_month` 的引用，改指 `uk_attempt` 最左前缀 `(station_id, target_month)`。
**前端 3 处 staff 侧残余点位**（复评 §五）属前端影响面，**非算法职责**，交前端 / 架构补齐。

### 14.5 因本次修订而作废的旧表述（**明确作废，不得沿用**）

| # | 旧表述（v1.0~v1.2） | 处（旧） | 作废理由 | 新表述（v1.3） |
| - | ---- | ---- | ---- | ---- |
| 1 | 补跑「次日**在同一钟点**再试」 | §0 条 5、§1.4 模型一句话 | 复-3：钟点口径未统一 | 「次日**在当日 `catch-up-time-of-day`（默认继承 `payroll_time`）之后的首个 tick**」§1.4 |
| 2 | 补跑判据仅 `now ≥ atTimeOf(day, hh, mm)` | §1.3 伪代码、§1.4 判定表 | 复-3：未含 `catch-up-time-of-day` 与「当日尚未尝试」 | `now ≥ attemptTimeOf(day)` ∧ `(s,m)` 当日尚未尝试（`uk_attempt`）§1.3 / §1.4 |
| 3 | `stale-reclaim-enabled=false` 作**单实例**兜底 | §0 条 8、§1.5.1 P4 行 / 结论 | 复-1 + 主代理裁定：单实例默认 `true` | `false` 仅作**多实例前 / 主动放弃自动回收**的兜底；单实例默认 `true`（§1.5.1、§1.5.3、§2） |
| 4 | 僵死回收仅「置 `FAILED` 释放 `uk_claim`」，未定义 `fail_reason` 与告警 | §1.5 回收段落（v1.2） | 复-1：动作不完整 → 静默失效 | 回收动作 = `FAILED` + `claim_key=NULL` + `fail_reason=STALE_RECLAIMED` + **主动告警**（§1.5.3） |
| 5 | `force` 参数 / `force` 语义引用 | §1.4 手工端点注（v1.2） | 复-2 主代理裁定删除 | 端点入参仅 `stationId` / `month`；同日重复回 `9410`（§1.4、§2、§8） |
| 6 | §1.3 / §1.5 以已删索引 `idx_payroll_run_station_month` 作辅助查询路径 | §1.3 判定顺序注、§1.5 辅助判据 | 复评低风险待办 ①：索引已在 `V21` 删除 | 改指 `uk_attempt` 最左前缀 `(station_id, target_month)` |

> **保留不变**（不因本次修订作废）：U-05 钳位、U-06 日粒度重试与「每自然日至多一次」、双唯一键协议、W1/W2 时序与 P1~P4 取舍、时区口径、逐驿站串行与异常隔离、参数外置原则、`TODO-1~9`。

---

**v1.3 自检声明**：本次修订**仅修改本文件 `algorithm-payroll-scheduling.md`**，未改上游方案（`payroll-automation-design.md`）、迁移脚本（`V20__payroll_automation.sql` / `V21__payroll_log_locator.sql`）、`db.md`、`api.md`、任何代码文件；未执行 git / 部署 / MCP；**本机无 JDK / MySQL，未运行任何构建、测试或 SQL**，未连接数据库。所有新结论均标 `[推导]` 或给官方来源（§5②③⑧）；`STALE_RECLAIMED` 为 `fail_reason` **取值**（非新列），回收动作仅为**设计口径**，未见 DDL 落库。**v1.3 变更须重评（L8）。**
