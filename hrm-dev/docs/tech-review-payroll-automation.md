# 技术评审报告：薪资结算自动化与全链路留痕

| 项目 | 内容 |
| ---- | ---- |
| 评估对象 | ① [payroll-automation-design.md](payroll-automation-design.md)（架构方案 v1.1，679 行，2026-09-27）② [algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md)（算法四件套 v1.0，447 行）③ [V20__payroll_automation.sql](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql)（数据层）④ [db.md](db.md) §8.6.3/§8.6.5/§8.6.6/§8.6.7 与 §9.1 Q-DB-7/8/9 ⑤ [init.sql](../../sql/schema/mysql/init.sql) 新增部分 |
| 产出角色 | 架构师 `express-station-architect` / 算法工程师 `express-station-algorithm-engineer` / 数据库工程师 `express-station-database-engineer` |
| 评估方 | 技术评审工程师 `express-station-tech-reviewer`（未参与上述任一方案产出，独立性成立） |
| 评估日期 | 2026-09-27 |
| 取证方式 | **静态文档级评估**。本机无 JDK / MySQL / Redis，**未运行任何构建、测试、SQL 或门禁**；全部结论以「文件:行号」下探到真实代码/脚本/文档后作出，不采信方案自述 |
| 依据闸门 | `项目规则1.md` §8 第 8 条、§11.4；调度规则 **P0.6 / R25 / L8**、冲突裁决表、反模式 A22/A23 |
| **结论等级** | **打回** |

---

## 一、评估范围与取证方式

1. **评估范围**：以上游「用户已裁定的需求口径 Q1~Q9」（任务书 §二）为基准，评估方案是否**忠实落地**；对方案六维（依据充分性 / 边界合理性 / NFR 覆盖 / 复杂度论证 / 假设与风险登记 / 既有契约一致性）逐维取证。
2. **取证方式**：只读检索 + 逐条下探。凡方案自述的 `文件:行号`，一律回读原文件核对；对无自述行的关键事实（前端硬编码点、`pay` 端点、db.md 存量条款）**独立搜索**。
3. **不在本报告范围**：安全实质判定（漏洞 / 越权 / 供应链，归网络安全工程师）；可执行验证（门禁 / 单测 / E2E，归测试工程师）；操作安全评估与 C 档授权（归主智能体）；口径最终裁定（归用户）。
4. **独立性声明**：评估方未产出、未修改任何被评对象；本报告只出结论等级与必改项，不改方案原文、不代放行。

---

## 二、独立核验结果（逐条对应任务书 §三 的 9 项）

### 核验 1 · `idx_payroll_emp_month_bill` 是否 `UNIQUE`

- 实测：`KEY idx_payroll_emp_month_bill (employee_id, month, bill_type)` —— **普通 KEY，非 UNIQUE**。
- 出处：[V8__payroll.sql:85](../../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85)。
- 结论：**算法 R1 成立，方案 v1.1 §0.2⑪/§3.3 的更正成立**（v1.0「单据级幂等靠该索引」为无源断言）。当前 schema 下**单据层不存在 DB 幂等**。db.md §8.6.3 仍将该索引用途写作「生成幂等」([db.md:883](db.md))，措辞与「非唯一」事实易误导，属存量待同步项。

### 核验 2 · `generate` 是否物理删除同员工同月旧单再重建

- 实测：`generate` 在 `@Transactional` 内逐员工先 `deleteExisting(...)` 后 `createPayroll(...)`；`deleteExisting` 走**物理删除**（自定义 SQL，绕 `@TableLogic`），范围仅 `DRAFT/REJECTED`。
- 出处：[PayrollServiceImpl.java:142-186](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L142-L186)、[:173-175](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L173-L175)、[:601-621](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621)、`PayrollGenerateGuard.editableStatuses()` = `[DRAFT, REJECTED]`（[PayrollGenerateGuard.java:20-22](../../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L20-L22)）。
- 结论：**成立**。算法 R11「DRAFT 期 MANUAL 项被静默物理清除」的事实基础成立；方案 v1.1 采纳「保留 MANUAL 明细」（§3.4）在既有 `deleteExisting + createPayroll` 结构上**可实施**（识别 → 重建后 re-insert → 复用 `PayrollTotalsPolicy` 重算）。

### 核验 3 · `PayrollStateMachine.isEditable` 的现有消费点，拆分后是否遗漏

- 全仓检索消费点（生产代码仅 3 处）：
  - [PayrollServiceImpl.java:340](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L340)（`updateItems` 状态守卫）；
  - [PayrollStateMachine.java:60](../../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStateMachine.java#L60) 定义体；[PayrollGenerateGuard.java:25-26](../../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L25-L26)（转调）、[:47](../../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L47)（`findBlockingMonthly` 9405 判定）；
  - [PayrollLockPolicy.java:29](../../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollLockPolicy.java#L29)（`isLocked = !isEditable`）。
  - 测试消费点：`PayrollStateMachineTest`、`PayrollGenerateLockPolicyTest`。
- 结论：方案 §2.3 的「三分」映射（`isItemEditable` ← `updateItems`/item-add；`isOverwritable` ← `PayrollGenerateGuard.editableStatuses()`/`deleteExisting`；`isLocked = !isOverwritable` ← `PayrollLockPolicy`）对**生产消费点覆盖完整，无遗漏调用点**；将 `isEditable` 重定义为 `isOverwritable` 后 `PayrollLockPolicy` 行为零突变（DRAFT/REJECTED 外仍锁定）。
- **但**：消费点清单**未纳入前端/mock 镜像**（`financeStore.js` 的 `EDITABLE_STATUS`、`EMPLOYEE_VISIBLE_STATUS` 与 `updateItems` 分支），详见核验 8。

### 核验 4 · `NotificationServiceImpl.PUBLISH_TYPES` 是否 `1..6`

- 实测：`PUBLISH_TYPES = Set.of(1, 2, 3, 4, 5, 6)`；`sendSystem` 与发布路径同走白名单校验。
- 出处：[NotificationServiceImpl.java:44](../../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)、[:143](../../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L143)、[:184](../../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184)。
- 结论：**成立**。方案新增 7/8/9（§4.5）**必须同步放行白名单**，否则 `sendSystem` 抛错；方案已声明该点，正确。

### 核验 5 · `payroll.status VARCHAR(20)` 是否足够 & V20 是否仅改 COMMENT

- 实测：`status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'`（[V8__payroll.sql:67](../../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L67)）；V20 `MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '...8 态'`（[V20:96](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L96)）。
- 结论：**成立**。`OBJECTED`(7) / `PAID`(4) 均在 `VARCHAR(20)` 内；V20 的 `MODIFY COLUMN` **仅改 COMMENT**，类型 / 长度 / 默认值 / 空性不变，**无数据转换**，与 db.md §8.6.3 注一致（[db.md:873-874](db.md)）。**无无谓改列型**。

### 核验 6 · `payroll_run` claim 槽位在 MySQL 下能否保证「同驿站同账期至多一个 RUNNING/SUCCESS」

- 机制成立性：`UNIQUE(station_id, claim_key)` + MySQL 唯一索引允许多 `NULL`（算法 §5② 已核）→ `RUNNING/SUCCESS/SKIPPED` 写 `claim_key=target_month` 互斥、`FAILED=NULL` 可多行释放，**方向正确**，是重叠 tick 的有效硬防线。
- **但仍存竞态窗口（方案表述过强，未加限定）**：
  - **W1 僵死回收误判**：`running-timeout-minutes`（默认 30）在下游变慢时可能 < 实际单驿站耗时；另一执行者（多实例 / 手工触发）将其判为僵死 → `UPDATE claim_key=NULL` 释放 → `INSERT RUNNING` 抢占 → **并发 `generate`**。因 Layer 2 单据层无唯一约束（核验 1），理论上**可双写重复工资单**。单实例 `fixedDelay` 不重叠时风险低，**多实例或手工并发时真实存在**。
  - **W2 手工路径不受 claim 保护**：`item-add`（I-6）不经 `claim`；与自动 `generate` 的「事务内 `deleteExisting`」并发时，可能删掉刚录入的 MANUAL 项或产生孤儿。
  - **W3 `force` 与占位行交互未定义**：若 `(s,m)` 已被 `SKIPPED`（`CONFIG_INVALID`/`BLOCKED_9405`/`DRAFT_PROTECTED`）**永久占位**，I-4 手工触发 `INSERT RUNNING` 将 DuplicateKey；`force` 如何绕过占位**未定义** → 等同「无法重跑」。
- 结论：方案 §1.5/§3.3「至多一个 RUNNING/SUCCESS」**在该前提之外不绝对成立**；§3.3 风险登记 R1-A 只覆盖「手工 `generate` 与 tick 并发」，**未覆盖 W1/W2/W3**。详见必改项 4/6。

### 核验 7 · 「有效窗口 `[dueAt, min(dueAt+W, 次月首日 00:00))`」与「`payroll_day` 1..31 钳位」是否自洽

- 结论：**逻辑自洽（无矛盾），但联合效果使「月末 d0」的有效窗口急剧塌缩**，与算法 §2「72h 覆盖周末停机」的依据**不符**。
- 验算：
  - `d0=31, time=09:00, 2026-08` → `dueAt=2026-08-31 09:00`；`min(dueAt+72h, 2026-09-01 00:00) = 2026-09-01 00:00` → 有效窗口 = **15 小时**。
  - `d0=31, time=23:59, 2026-08` → 有效窗口 = `[08-31 23:59, 09-01 00:00)` = **1 分钟** → 自动补跑形同关闭。
  - `d0=30, 2026-02`（钳位 28 日）→ `dueAt=2026-02-28`；`min(dueAt+72h, 2026-03-01 00:00)` = **15 小时**。
  - `d0=1` 时 `min` 由 `dueAt+72h` 取胜（第 4 日）→ W 生效。
- 附加约束：自动路径**只评估 `now` 所在月**，故一旦跨入次月，上一账期自动补跑即中止——与「窗口截到月末」是**同向叠加**，非矛盾。
- 结论细化：两条规则**不冲突**，但「窗口 = W」的语义在月末场景**失真**，方案未登记该后果。详见发现项 M-2。

### 核验 8 · 前端影响面清单是否完整（含独立搜索）

- 方案已列（核验属实）：`shared/src/constants/dict.js` 的 `PAYROLL_STATUS`（[dict.js:150-157](../../hrm-clients/packages/shared/src/constants/dict.js#L150-L157)）；`mock/src/financeStore.js` 的 `PAYROLL_STATUS_LABEL` + `PAYROLL_ACTIONS`（[financeStore.js:37-59](../../hrm-clients/packages/mock/src/financeStore.js#L37-L59)）；`PayrollStatusSteps.vue` 固定 5 步（[PayrollStatusSteps.vue:20-26](../../hrm-clients/apps/web/src/components/PayrollStatusSteps.vue#L20-L26)）；boss `payrollDetail.vue` 硬编码分支（[boss payrollDetail.vue:36-38](../../hrm-clients/apps/boss-h5/src/modules/boss/views/payrollDetail.vue#L36-L38)）；staff `payrollDetail.vue`（[staff payrollDetail.vue:41-65](../../hrm-clients/apps/staff-h5/src/views/staff/payrollDetail.vue#L41-L65)）；`PayrollDetailDrawer.vue:66` 可编辑集与 `:116` NPE（[PayrollDetailDrawer.vue:66](../../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue#L66) / [:116](../../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue#L116)）；`GeneratePayrollDialog.vue:23`、`usePayrollActions.js:89`。
- **独立搜索发现的遗漏点（方案未列）**：
  1. `PayrollDetailTable.vue:99` —— `PAYROLL_STATUS[row.status].variant` **同款 NPE 风险点**（[PayrollDetailTable.vue:99](../../hrm-clients/apps/web/src/views/finance/components/PayrollDetailTable.vue#L99)）。
  2. `PayrollObjectionsPanel.vue:40` —— 同款 NPE（[PayrollObjectionsPanel.vue:40](../../hrm-clients/apps/web/src/views/finance/components/PayrollObjectionsPanel.vue#L40)）。
  3. `employee/detail/index.vue:116` —— 同款 NPE（[employee/detail/index.vue:116](../../hrm-clients/apps/web/src/views/employee/detail/index.vue#L116)）。
  4. `mock/src/financeStore.js:48` `EMPLOYEE_VISIBLE_STATUS=['PUBLISHED','CONFIRMED']`（U-08 要求 `PAID` 加入，**mock 镜像漏列**）、`:50` `EDITABLE_STATUS=['DRAFT','REJECTED']`（应镜像 `isItemEditable`）、`:276/:644` 动作与可编辑分支。
  5. `mock/src/routes/finance.js:131`（状态白名单取 `PAYROLL_STATUS_LABEL` 键）、`:152`（员工状态限 `['PUBLISHED','CONFIRMED']`）。
  6. `scripts/verify-mock.mjs:5470`（断言恰好 6 态）、`:5371`（`my` 列表限 `PUBLISHED/CONFIRMED`）。
  7. `boss-h5/stores/todo.js:69,102`、`staff-h5/stores/todo.js:54`（`PAYROLL_STATUS` 字典渲染待办标签）。
- 结论：**清单不完整**。M3「前端字典单源 + mock 镜像 + 硬编码视图同步」若按现清单执行，仍会残留 NPE / mock 断裂 / 门禁脚本失败风险。详见必改项 3。

### 核验 9 · 与既有契约（`api.md` / `db.md`）的冲突面是否登记且给出补录计划

- `api.md`：§4.0 概览「65 个」端点**不含任何财务/payroll 段**（[api.md:190-261](api.md)；全文件检索 `payroll|finance|财务` 仅命中错误码段 [api.md:90](api.md)）。
- 结论：
  - C-1（`objection` 目标态）、C-2（`publish` 来源）、C-7（`generate` 9405 范围）**与 `api.md` 现有条文无直接文本冲突**（因该段整体缺位），冲突面主要在**既有代码行为 / Mock 契约 / 前端**（[PayrollServiceImpl.java:440-455](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L440-L455)、[financeStore.js:52-59](../../hrm-clients/packages/mock/src/financeStore.js#L52-L59)）。
  - 方案已在 §4/§6 B0/§8.1 声明「新增/变更端点须在 B0 补录 `api.md`」，**补录计划存在**。
  - **但 `db.md` 侧冲突未列入同步清单**：§8.6.5 `payroll_day` 注释仍「建议 1-28」（[db.md:911](db.md)）、§8.6.7 与 §9.1 Q-DB-7/U-13 仍为 v1.0 `success_key` 语义（[db.md:966-1000](db.md)、[db.md:1230](db.md)、[db.md:1282](db.md)）、`init.sql` 仍为 v1.0（[init.sql:945,953](../../sql/schema/mysql/init.sql#L945)）。方案 §6「V20 提示行」只列了 **V20 脚本**回改项，**未显式列 db.md / init.sql 的回改项**。详见必改项 2。

---

## 三、六维评估

### 维度 1 · 依据充分性 —— **基本满足，存在 2 处依据不实/过度声明**

- 满足面：绝大多数结论可下探到真实 `文件:行号`（核验 1~5、8、9 均属实）；算法 §5 对 MySQL `UNIQUE` 多 `NULL`、`@Scheduled.fixedDelay` 语义、`Asia/Shanghai` 无 DST 均给官方来源。
- 问题：
  1. **依据不实（无源结论）**：方案 §3.4 称「规则项 key……约定不使用该前缀（[V8__payroll.sql:38]）」，但 [V8:38](../../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L38) 仅声明 `item_key VARCHAR(40)` 列，**无任何「前缀约定」**。该论断支撑「`MANUAL_` 前缀不冲突」的核心假设，属**建设性约定被冒充为既有事实**。
  2. **过度声明**：方案 §5 与 §9 称 `payroll_run`「可复现『为何跑了 / 为何没跑』」，与 §1.6/§5「未启用 / 自动路径超窗口**不产生运行记录**」自相矛盾——「为何没跑」的场景（未到点 / 未启用 / 超窗口）**无记录可查**。
- 结论：**不满足（有条件）**。

### 维度 2 · 架构与模块边界合理性 —— **基本合理，但「绝对冻结 + 无救急出口」与「pay 缺接口」破坏闭环**

- 状态机 8 态动作矩阵：可达性检查——`DRAFT→PENDING_APPROVAL→APPROVED→PUBLISHED→CONFIRMED→PAID` 与 `PUBLISHED→OBJECTED→PUBLISHED` 均可达；**无不可达态、无死锁、无越界跃迁**（§2.2:230-239 矩阵与 §2.1:212-221 一致）。
- 判据拆分（§2.3）职责单一、依赖单向，且对既有 3 个生产消费点行为零突变（核验 3）。
- 问题：
  1. **`pay`（`CONFIRMED→PAID`）无接口定义**：§2.2/§2.5 定义了 `pay` 动作与 `paid_*` 落库，§6 B4 也有「`CONFIRMED`→`pay` 归档」验收，但 §4.1（I-1~I-7）与 §4.2（C-1~C-7）**均无 `POST /payrolls/{id}/pay`**；现有 [PayrollController](../../hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java) 亦无该端点。**Q9「管理员确认工资已发放→归档」无落地路径**。
  2. **`PAID` 绝对冻结（U-03）+ claim 永久占位（SKIPPED）+ 无冲正/无删除**，三者在「误发放需冲正」「配置修正后需重跑」等运维救急场景下**无出口**（§2.5:276 明确禁止任何写与 `is_deleted`；§3.3:417 `SKIPPED` 永久占位）。这是设计上的**已知代价但未给应急路径**。
- 结论：**不满足（有条件）**。

### 维度 3 · NFR 覆盖度 —— **部分覆盖，存在未登记缺口**

| 子项 | 结论 | 证据 / 缺口 |
| --- | --- | --- |
| 并发 | 部分满足 | claim 槽位方向正确并有 MySQL 依据（算法 §5②）；但有 **W1 僵死回收误判双跑**、**W2 手工路径不受保护**、**W3 `force` 与占位未定义** 未登记（核验 6）。多实例资源浪费已登记债（§1、U-12） |
| 性能 | 满足 | 逐行 insert 在 `S=50,E=20,R=8` → 9000 行 / `[推导]≈5.2s` / 利用率 <1%，并给批量阈值 `>5×10⁴` 行（算法 §4.2）；量级结论成立 |
| 可观测性 | **不满足** | `payroll_run` 覆盖 dueAt/trigger_type/status/skip_code/skip_reason/generated_count；但「未启用 / 未到点 / 超窗口」不落表（R9），「为何没跑」不可复现（核验 9 · 维度 1 问题 2） |
| 幂等 | 部分满足 | 三层防线清晰，Layer 2 缺失已登记（R1-A）；但 Layer 0 不覆盖手工 `item-add` 与自动重建的并发（核验 6 · W2） |
| 时区 | 部分满足 | 判定与调度落库统一 `Asia/Shanghai`（ADR-01:186）；**人工链路沿用 `LocalDateTime.now()`，依赖部署前置 `-Duser.timezone=Asia/Shanghai`**，该前置**未纳入风险/T 登记**，宿主 TZ 非 +8 时人工链路与调度链路错位 |
| 安全面（形式核对） | 已声明 | §4.4 声明「无权限放宽」并登记 2 项间接安全面（调度生产变更面 / 新表数据面），交网络安全工程师；**实质判定不在本报告** |

- 结论：**不满足（有条件）**。

### 维度 4 · 复杂度与可行性论证 —— **ADR 选型成立；stale 回收引入的复杂度收益未论证充分**

- **ADR-01 取舍成立**：进程内 `@Scheduled(fixedDelay)` vs 宿主 crontab vs Quartz/xxl-job 的三方对比（§1:164-171）与「当前单实例、零调度基础设施、服务器禁写源码」现状匹配；「逐驿站算薪日无法用单条 cron 表达」是决定性理由，成立。否决 B（不可版本化）成立；否决 C（过度设计）与一期定位一致。
- 未过度设计：claim 槽位属**必要**复杂度（正确性防线）；补跑窗口、钳位规则属**必要**口径。
- 论证不足处：**僵死 `RUNNING` 回收**（算法 R6 / 方案 §3.3:422）本身引入新状态机分支与**W1 竞态**，方案只给阈值（30min）与「>3×tick 避免误杀」，**未论证在 DB 变慢 / 大批量场景下阈值为何充分**，也未与「批量插入阈值」联动。属「引入了不必要的复杂度风险」。
- 结论：**基本满足（有条件）**。

### 维度 5 · 假设与风险登记 —— **已分级但不完整**

- 已登记且分级良好：R1-A（单据级 DB 幂等缺口）、多实例架构债（U-12）、逐行 insert 批量优化 TODO（算法 TODO-2）、T1/T2/T3（§7:636-642）、算法 §7 十二条修正。
- **未登记的高/中风险假设**：
  1. 时区单源依赖部署前置（维度 3）。
  2. 僵死回收误判 → 并发双跑（核验 6 · W1）。
  3. 月末 d0 有效补跑窗口塌缩（核验 7）。
  4. `force` 与占位行交互未定义（核验 6 · W3）。
  5. 自动生成落 `DRAFT` 与通知「待审核」语义偏差（见下维度 6 · 问题 3）。
  6. `U-05/U-06` 业务口径以「主代理预裁定」定稿（见维度 6 · 问题 4）。
- 结论：**不满足（有条件）**。

### 维度 6 · 与既有契约的一致性 —— **存在未闭环差异（有处置计划）**

- 一致：`db.md` §8.6.3 八态枚举序与方案 Q-DB-8 定稿一致（[db.md:863](db.md)）；`payroll_run` 唯一键作为 D7 例外已论证（§0.7 Q-DB-7、`db.md` §9.1 Q-DB-7/§8.12）；迁移体例对齐 V5/V8/V9（V20 含回滚注释）。
- **不一致（须闭环）**：
  1. `db.md` §8.6.5 `payroll_day` 注释「建议 1-28」（[db.md:911](db.md)）vs 方案 U-05「1..31 钳位」。
  2. `db.md` §8.6.7、§9.1 Q-DB-7、§9.1 U-13 仍为 v1.0 `uk_payroll_run_success/success_key`「仅 SUCCESS 写值、RUNNING/SKIPPED 为 NULL 可多行」（[db.md:966-1000](db.md)、[db.md:1230](db.md)、[db.md:1282](db.md)）vs v1.1 claim 槽位（RUNNING/SKIPPED 占位）。
  3. `init.sql` 仍为 v1.0（[init.sql:895,945,953](../../sql/schema/mysql/init.sql#L945)）。
  4. `V20` 仍为 v1.0（`uk_payroll_run_success`、无 `skip_code`、`payroll_day` 注释「建议 1-28」）——方案 §6 已自认 M6 并给出 7 条回改项，**但回改项未覆盖 db.md / init.sql**。
- **需求口径忠实度**（Q1~Q9）：Q1/Q2/Q3/Q4/Q5/Q7 落地充分；Q6 有语义缺口（见问题 3）；Q8 通知 7/8 落地充分；**Q9 因 `pay` 端点缺失而无法完整落地**。
  - 问题 3（Q6 语义缺口）：自动路径生成落 `DRAFT`（§1.3:122-123 `generate` 落 DRAFT；§3.5），而通知类型 7 为「工资单待审核」，且 `DRAFT` 不在 `approve` 允许集（需先 `submit`，§2.2:232）。方案**未定义 DRAFT→PENDING_APPROVAL 由谁触发**（自动提交？管理员提交？），与 Q6「生成后先推管理员审核」存在偏差。
  - 问题 4（口径来源，超越「是否声明」的形式核对）：`U-05`（算薪日 1..31 钳位）、`U-06`（不自动跨月）属**业务口径**，方案 §7 标注裁定方为「主代理预裁定」；而算法 §1.2/§7 R4 明确「该选择属业务口径，**须用户裁定**」。按 §11.4 口径红线，此类须用户确认。**本角色只登记该差异并转交主智能体，不替用户裁定**。
- 结论：**不满足（有条件）**。

---

## 四、必改项清单

> 格式：问题 / 依据 / 影响 / 验收标准（作者可执行、评估方可复核）。

**必改项 1（高）· 缺失 `pay` 端点，Q9 无法落地**
- 问题：`pay`（`CONFIRMED→PAID`）动作在 §2.2/§2.5 定义、B4 有验收，但 §4.1/§4.2 无对应接口。
- 依据：`payroll-automation-design.md` §2.2:228,238、§2.5:277-278、§6 B4:597；`PayrollController.java` 无 `/pay`（检索 [PayrollController.java](../../hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java) 全部 Mapping）。
- 影响：用户已裁定 Q9「管理员确认工资已发放→归档」**无接口承接**，功能不可实现。
- 验收标准：§4.1 或 §4.2 增列 `POST /api/v1/finance/payrolls/{id}/pay`（`{"ADMIN"}`，前置 `status=CONFIRMED`，副作用写 `paid_*` + `payroll_log(PAY)`，命中 `9413`）并补入 §6 B0 `api.md` 补录清单；文中 `pay` 动作与端点一一对应。

**必改项 2（高）· 数据层产物与 v1.1 不一致，且回改清单不完整**
- 问题：`V20`/`db.md`/`init.sql` 仍为 v1.0；方案 §6 提示行仅列 V20 回改项，未显式列 db.md（§8.6.5/§8.6.7/§9.1 U-13）与 `init.sql`。
- 依据：[V20:78,86,96](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L78)；[db.md:911](db.md)、[db.md:966-1000](db.md)、[db.md:1282](db.md)；[init.sql:953](../../sql/schema/mysql/init.sql#L953)。
- 影响：B1 执行后 `V20` 与本方案语义不符（`RUNNING` 不占位 → 并发失去硬防线）；文档/快照与迁移三方漂移。
- 验收标准：① `V20` 落实 §6 七条回改（`claim_key`/`uk_payroll_run_claim`/`skip_code`/`payroll_day` 注释/表 COMMENT/回滚注释）；② `db.md` §8.6.5 注释改「1-31（月末钳位）」、§8.6.7 与 §9.1 Q-DB-7/U-13 改为 claim 槽位语义；③ `init.sql` 与 V20 逐字一致。复核方式：三方对读 `success_key`→`claim_key`、`skip_code`、注释三处。

**必改项 3（高）· 前端影响面清单不完整（M3 完备性不足）**
- 问题：遗漏同类 NPE 点与 mock 逻辑点（核验 8 的 1~7）。
- 依据：`PayrollDetailTable.vue:99`、`PayrollObjectionsPanel.vue:40`、`employee/detail/index.vue:116`；`financeStore.js:48,50,276,644`；`mock/routes/finance.js:131,152`；`verify-mock.mjs:5371,5470`；`boss-h5/stores/todo.js:69,102`、`staff-h5/stores/todo.js:54`。
- 影响：字典未先行的窗口内 `PAYROLL_STATUS[status].variant` 取 `undefined.variant` 抛错；mock 可见集/可编辑集与后端漂移；门禁脚本断言失败。
- 验收标准：§0.2⑨/§2.7.3 清单补齐上述全部点位，并沿用「字典 + mock 镜像先行、后端新状态后返回」的同批顺序；验收口径写明「新增任一状态后，三端详情/列表/步骤条/mock 无 NPE、状态渲染正确」。

**必改项 4（高）· 并发残余竞态窗口未登记（W1/W2）**
- 问题：claim 槽位「至多一个 RUNNING/SUCCESS」的表述未加限定；僵死回收误判与手工路径并发未覆盖。
- 依据：核验 6（W1/W2）；`payroll-automation-design.md` §1.5:181-190、§3.3:420-422、R1-A:454-456。
- 影响：极端场景（多实例 / 慢任务 / 手工并发）可产生重复工资单，与方案核心承诺不符。
- 验收标准：§3.3 风险登记新增 W1（僵死回收误判 → 并发）与 W2（手工 `item-add` 与自动重建不互斥），各给触发条件、影响面、缓解（如回收前复核、手工路径加单员工锁/版本号）；并把「至多一个 RUNNING/SUCCESS」改为「在无僵死回收误判且手工路径串行前提下」。

**必改项 5（中）· 自动生成状态与通知语义闭环（Q6 忠实度）**
- 问题：自动生成落 `DRAFT`，通知类型 7 文案「待审核」，但 `DRAFT` 不在 `approve` 允许集，DRAFT→PENDING_APPROVAL 触发方未定义。
- 依据：§1.3:122-123、§2.2:232、§4.5:555、§6 B4:597；`PayrollServiceImpl.generate` 落 `DRAFT`（[:175](../../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L175)）。
- 影响：管理员收到「待审核」但单据为草稿，需两步（提交→审批），与 Q6「生成后推管理员审核」语义偏差。
- 验收标准：明确定义自动算薪后的状态目标（自动 `submit` 落 `PENDING_APPROVAL`，或保持 `DRAFT` 并将通知文案改为「待处理草稿」），并同步 §2.2 动作矩阵、§4.5 文案、B2/B4 验收口径。

**必改项 6（中）· `force` / 手工触发与 claim 占位行交互定义**
- 问题：`(s,m)` 已被 `SKIPPED`（永久占位）时，I-4 触发将 DuplicateKey；`force` 如何绕过未定义；`PAID` 误发放无冲正出口。
- 依据：§3.3:417、建议 I-4:501；核验 6 · W3、维度 2 问题 2。
- 影响：运维救急（配置修正后重跑 / 误发放处理）无路径。
- 验收标准：① 定义 `force` 对已占位行（`SKIPPED`/`SUCCESS`）的处置（拒绝 + 明确错误码，或允许释放后重跑并留痕）；② 明确「误发放冲正」是否属本期范围，若非则登记为**受限项**（`TODO(扩展)` + 风险），不得沉默。

**必改项 7（中）· 依据修正：`V8__payroll.sql:38` 误引**
- 问题：§3.4 称 `V8:38` 约定规则项 `item_key` 不使用 `MANUAL_` 前缀，原文无此约定。
- 依据：[V8:38](../../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L38) 仅声明列。
- 影响：支撑「前缀不冲突」的核心假设无源。
- 验收标准：改写为「本方案**新约定**：手工项强制 `MANUAL_` 前缀，规则项沿用 `payroll_rule_item.item_key`（V8:38 列为真源，约定由本方案确立）」，或补真实出处。

**必改项 8（中）· 月末有效补跑窗口塌缩的依据修正与登记**
- 问题：`min(dueAt+W, 次月首日 00:00)` 使月末 d0 的有效窗口可塌缩至分钟级，与「72h 覆盖周末停机」依据矛盾，且未登记。
- 依据：核验 7；算法 §1.4/§2、方案 §3.3:436、§5:574。
- 影响：月末算薪的自动补跑实际不可用，运维依赖手工触发。
- 验收标准：§3.3/§5 明确「有效窗口 = `min(dueAt+W, 次月首日00:00)`，**月末 d0 下窗口可短至分钟级**」，并纳入风险登记；或调整口径（如把 `W` 的起点改为「次月首日起算」）——**口径调整须用户裁定**，不得由方案自定。

**必改项 9（中）· 可观测性过度声明修正**
- 问题：§5/§9 称 `payroll_run`「可复现为何跑了/为何没跑」，与 R9「未启用/超窗口不落记录」矛盾。
- 依据：§1.6:218、§5:578,585。
- 影响：排障预期与实际能力不符。
- 验收标准：将表述收敛为「可复现**已进入执行**的跳过/失败归因」；对「未进入执行」给出查询层派生展示（如按配置 + 当前时间推算）或登记为**已知观测盲区**。

**必改项 10（中）· 业务口径来源须用户确认（§11.4）**
- 问题：`U-05`（算薪日 1..31 钳位）、`U-06`（不自动跨月）属业务口径，现以「主代理预裁定」定稿。
- 依据：§7:616,624-625；算法 §1.2:89、§7 R4:397；`项目规则1.md` §11.4 / 调度规则 P1。
- 影响：口径红线——未经用户确认即进入实现。
- 验收标准：由主智能体向用户确认 `U-05`/`U-06`（及 `U-03` 绝对冻结程度、`U-07` 通知接收人）并归档裁定；未确认前相应实现项标注「待用户裁定，不得实现」。**本角色不代为裁定。**

---

## 五、发现项清单（分级）

| 级别 | # | 发现项 | 证据 |
| --- | --- | --- | --- |
| **高** | H1 | `pay` 端点缺失，Q9 无落地路径 | 必改项 1 |
| **高** | H2 | 数据层（V20/db.md/init.sql）与 v1.1 不一致且回改清单不全 | 必改项 2 |
| **高** | H3 | 前端影响面清单不完整（NPE 点 / mock 逻辑 / 门禁脚本） | 必改项 3 |
| **高** | H4 | claim 槽位竞态窗口 W1/W2 未登记，核心承诺表述过强 | 必改项 4 |
| 中 | M1 | `pay`/DRAFT 生成与「推管理员审核」语义偏差（Q6） | 必改项 5 |
| 中 | M2 | `force` 与占位行交互未定义；`PAID` 无冲正出口 | 必改项 6、维度 2 |
| 中 | M3 | `V8__payroll.sql:38` 依据不实 | 必改项 7 |
| 中 | M4 | 月末有效补跑窗口塌缩，「72h 覆盖周末」依据失真 | 必改项 8、核验 7 |
| 中 | M5 | 可观测性「为何没跑」不可复现（过度声明） | 必改项 9 |
| 中 | M6 | 业务口径 `U-05/U-06` 以主代理预裁定定稿，待用户确认 | 必改项 10 |
| 中 | M7 | 时区单源依赖部署前置 `-Duser.timezone`，未纳入风险登记 | 维度 3 时区行 |
| 低 | L1 | `db.md` §8.6.3 将 `idx_payroll_emp_month_bill` 用途写作「生成幂等」，与「非唯一」事实易混 | [db.md:883](db.md) |
| 低 | L2 | `db.md` §9.1 Q-DB-9 明示「请技术评审确认」——本次确认「只增不删」前提可接受，但须与必改项 6 的救急出口一并落定 | [db.md:1232](db.md) |
| 低 | L3 | `V20` 头部引用「§3（表设计，v1.0 初稿，待技术评审）」，评审通过后须随版本号更新 | [V20:3](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L3) |

---

## 六、结论等级、判定理由与放行前置

- **结论等级：打回**（不得报主智能体审批）。

**判定理由（按 `tech-evaluation` 分级红线）：**
1. **缺关键设计 / 影响正确性**：`pay` 端点缺失使**用户已裁定口径 Q9 无法落地**（必改项 1）——属「缺关键论证/关键设计」。
2. **影响正确性**：`payroll_run` claim 槽位的「至多一个 RUNNING/SUCCESS」承诺**未加限定且未登记 W1/W2 竞态**（必改项 4）；极端场景可产生重复工资单，与方案核心卖点冲突。
3. **无源结论**：`V8__payroll.sql:38` 误引（必改项 7）——按分级红线「出现无源结论 → 最低打回」。
4. **覆盖完备性不足**：前端影响面清单不完整（必改项 3），M3 无法保证闭环。
5. **契约一致性未闭环**：数据层三方与 v1.1 不一致且回改清单不全（必改项 2）。
6. **口径红线**：`U-05/U-06` 业务口径以主代理预裁定定稿，未获用户确认（必改项 10）。

> 说明：必改项 2/6 的「自认未同步」不代表方案质量差——方案对 v1.1 变更的自述与响应表（§0.6/§0.7）质量较高、证据链完整；但**评估对象集合当前内部不自洽**，且存在上述影响正确性的缺口，故不满足「通过」与「有条件通过」的判据（「有条件通过」仅限**不影响主干正确性**的必改项）。

**退回对象：**
- **架构师**（主）：修订 `payroll-automation-design.md` → v1.2，闭环必改项 **1、3、5、6、7、9、10**（设计侧）。
- **数据库工程师**：按必改项 **2** 同步 `V20__payroll_automation.sql`、`db.md`（§8.6.3/§8.6.5/§8.6.7/§9.1 Q-DB-7/U-13）、`init.sql`；出具同步后的三方对照。
- **算法工程师**：按必改项 **4、8** 补僵死回收竞态（W1）与月末窗口塌缩的论证/限定（可增补 `algorithm-payroll-scheduling.md`）。

**重评前置（重评不新增无关要求，评估范围冻结）：**
1. 必改项 1~10 逐条闭环，每条附「改后状态」与可复核位置；
2. 数据层三方对照（V20 / db.md / init.sql）与 v1.2 逐项一致；
3. 竞态窗口 W1/W2 与 `force`/占位交互给出明确处置（含错误码或登记）；
4. `U-05/U-06/U-03/U-07` 口径由**用户**确认的归档记录（或方案显式标注「待用户裁定，不得实现」）；
5. 重评结论须绑定 v1.2 版本号与日期；版本再变更须重评。

**放行前置（重评「通过 / 有条件通过」后）**：本报告**不构成对任何 C 档动作的授权**；B1 迁移执行仍须主智能体按 §10.3 三步授权，安全面仍须网络安全工程师结论（P0.5 / L7），二者**并行不互替**。

---

*评估方声明：本报告为静态文档级评估，未运行任何构建/测试/SQL；未产出、未修改被评方案；未做安全实质判定；未做口径裁定；不含真实凭据或业务数据。*

---

# 复评（方案 v1.4 ｜ 2026-09-27）

| 项目 | 内容 |
| ---- | ---- |
| 复评对象 | ① [payroll-automation-design.md](payroll-automation-design.md) **v1.4（1034 行）** ② [algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md) **v1.2（792 行）** ③ [V21__payroll_log_locator.sql](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql) ④ [init.sql](../../sql/schema/mysql/init.sql) / [db.md](db.md) **v2.5（43 表）** ⑤ 首评 [tech-review-payroll-automation.md](tech-review-payroll-automation.md) ⑥ 安全 [security-payroll-automation-review.md](security-payroll-automation-review.md)（「有条件放行」） |
| 首评结论 | 打回（必改 1~10） |
| 评估方 | 技术评审工程师 `express-station-tech-reviewer`（未参与任一方案产出，独立性成立） |
| 取证方式 | 静态文档级：逐条下探 `文件:行号` + 独立关键词搜索 + 手工验算。**本机无 JDK/MySQL，未运行任何构建/测试/SQL**；不确定处标「**待核实**」 |
| **复评结论等级** | **有条件通过**（附放行前置条件；**不构成任何 C 档动作授权**） |

## 一、首评 10 条必改逐条闭环核对

> 说明：任务书括注的编号映射与首评原文略有偏移（任务书「5/6/7/9/10」实际对应首评「2 尾（回改清单）/ 6（动作矩阵闭合）+1（接口计数）/ 9（过度声明）/ 10（口径来源）/ 7（无源结论）」）。本表以**首评原文**为准逐条核对，并在「备注」列标明任务书关心点的落点。

| 必改 | 首评问题 | 复评判定 | 证据（文件:行号） |
| ---- | ---- | ---- | ---- |
| **1** 高 | 缺 `pay` 端点，Q9 无法落地 | **已闭环** | `I-8 POST /payrolls/{id}/pay`（ADMIN、前置 `CONFIRMED`、副作用 `paid_*`+`PAY`、非 `CONFIRMED`→`9403`、`PAID`→`9413`）见 [design:718](payroll-automation-design.md)；动作矩阵→I-8 见 [design:286](payroll-automation-design.md)；`pay`↔端点逐条闭合见 [design:945](payroll-automation-design.md)；B0 `api.md` 补录见 [design:817](payroll-automation-design.md)。实测 `PayrollController` **无** `/pay`（[PayrollController.java:42-121](../../hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java)），与首评一致，方案已补。 |
| **2** 高 | 数据层三方与方案不一致 + 回改清单不全 | **已闭环** | `V20`：`claim_key` [V20:80](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L80)、`uk_payroll_run_claim` [V20:88](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L88)、`skip_code` [V20:76](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L76)、`payroll_day` 注释「1-31」[V20:36](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L36)、`status` 八态末尾追加序 [V20:98](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L98)；`db.md` `payroll_day` [db.md:932](db.md)、`payroll_run` claim 与 `uk_attempt` [db.md:1012](db.md)、[db.md:1023-1024](db.md)、表数 43 [db.md:1251](db.md)；`init.sql` 与 `V20+V21` 逐列一致 [init.sql:945-968](../../sql/schema/mysql/init.sql)。**回改清单**已列（[design:834-843](payroll-automation-design.md)）。任务书所列 8 项（`claim_key`/`uk_payroll_run_claim`/`attempt_date`/`uk_attempt`/`skip_code`/`station_payroll_setting_log`(10 列)/`payroll_day 1-31`/枚举序）**逐项核到**。 |
| **3** 高 | 前端影响面清单不完整（M3 完备性） | **部分闭环（低残余）** | `§2.8` 已补至 **22 处**（[design:347-376](payroll-automation-design.md)），首评 4 个 NPE 现场全部纳入（[PayrollDetailDrawer.vue:116](../../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue)、[PayrollDetailTable.vue:99](../../hrm-clients/apps/web/src/views/finance/components/PayrollDetailTable.vue)、[PayrollObjectionsPanel.vue:40](../../hrm-clients/apps/web/src/views/finance/components/PayrollObjectionsPanel.vue)、[employee/detail/index.vue:116](../../hrm-clients/apps/web/src/views/employee/detail/index.vue)）。**本次独立搜索另发现 3 处 staff 侧未列**（见 §五），均非 NPE（`StatusTag` 有 `TYPE_FALLBACK`），列低风险待办。 |
| **4** 高 | 并发残余竞态 W1/W2 未登记、承诺表述过强 | **已闭环** | 算法补 W1 完整时序 [algorithm:273-305](algorithm-payroll-scheduling.md)、W2 [algorithm:307-315](algorithm-payroll-scheduling.md)；方案采纳 D4、裁定 D1 不加列并加限定 [design:563-571](payroll-automation-design.md)；「至多一个 RUNNING/SUCCESS」限定为「无僵死回收误判且手工路径串行」前提 [algorithm:270](algorithm-payroll-scheduling.md)。 |
| **5** 中 | 自动生成状态与通知 7 语义偏差（Q6） | **已闭环** | 裁定「自动生成→自动 `submit`→`PENDING_APPROVAL`」[design:392-403](payroll-automation-design.md)；同步动作矩阵 [design:290](payroll-automation-design.md)、通知 7 文案 [design:776](payroll-automation-design.md)、B2/B4 [design:819-821](payroll-automation-design.md)。 |
| **6** 中 | `force`/手工触发与占位交互定义；`PAID` 冲正出口 | **已闭环（定义层面）** | `force` 交互表 [design:598-610](payroll-automation-design.md)；`PAID` 无冲正登记受限项 T5 [design:878](payroll-automation-design.md)；「动作矩阵↔接口闭合」[design:945](payroll-automation-design.md)。**接口计数口径**「既有 10 + 新增 9（I-1~I-9）+ 变更 7（C-1~C-7）」成立：实测既有端点 **10** 个（[PayrollController.java:52-121](../../hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java)）；新增 I-1~I-9 [design:709-719](payroll-automation-design.md)；变更 C-1~C-7 [design:731-739](payroll-automation-design.md)。**但新发现 `force` 收敛后失去存在意义，见 §七 必改 复-2。** |
| **7** 中 | `V8__payroll.sql:38` 依据不实（无源结论） | **已闭环** | 已改写为「**本方案新增的建设性约定**」，明示 `V8:38` 仅声明 `item_key` 列、无前缀约定 [design:647](payroll-automation-design.md)。 |
| **8** 中 | 月末有效补跑窗口塌缩依据不实 | **已闭环** | 原 72h 窗口口径**整体作废**，替换为 U-06 日粒度重试 [design:577](payroll-automation-design.md)、[design:996-1007](payroll-automation-design.md)；算法 §1.4.1 改写为钳位/跨月验算 [algorithm:182-214](algorithm-payroll-scheduling.md)。「窗口塌缩」问题随窗口作废而消失。 |
| **9** 中 | 可观测性过度声明（为何没跑不可复现） | **已闭环** | 收敛为「仅可复现**已进入执行**的归因」，列「未启用/未到点/**当日已尝试**/已占位」为**已知观测盲区** [design:809](payroll-automation-design.md)、[design:922](payroll-automation-design.md)。 |
| **10** 中 | `U-05/U-06` 业务口径须用户确认（§11.4） | **已闭环** | 用户裁定原件与落点 [design:857-858](payroll-automation-design.md)、§13.1/13.2 [design:985-1007](payroll-automation-design.md)；旧口径作废清单 [design:1006](payroll-automation-design.md)、[algorithm:754-769](algorithm-payroll-scheduling.md)。 |

**闭环统计：已闭环 9 条（1/2/4/5/6/7/8/9/10）；部分闭环 1 条（3，低残余）；未闭环 0 条。**

> 补注：首评 **H1~H4** 对应必改 1~4 均已闭环；**M1~M7** 对应必改 5~10 及「时区单源」已闭环。首评 L1/L2/L3 低风险项：L1（`db.md` §8.6.3 措辞）仍待回改（[design:836](payroll-automation-design.md)）；L2（Q-DB-9 落档确认）与 L3（`V20` 头部引用）已随数据层同步处理（`V20` 头部现为「v1.1，claim 槽位语义」[V20:3](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L3)，已不残留「待技术评审」）。

## 二、安全 M-1~M-10 方案层闭环核对

> 本角色**只核「安全要求是否已在方案/数据层声明并落点」**，**不作安全实质判定**（归网络安全工程师）。

| M | 要求 | 方案层复评 | 证据 |
| ---- | ---- | ---- | ---- |
| M-1 高 | claim 三处一致（方案/数据层/U-13） | **已落**（三方产物齐；U-13 断言脚本属实现层） | [design:558-561](payroll-automation-design.md)；`V20`/`db.md`/`init.sql` 三方 `claim_key` 一致（§一 必改 2 证据）；U-13 重写见 [design:830](payroll-automation-design.md)、[db.md:995](db.md)（1062 断言） |
| M-2 高 | 全路径入 claim / I-7 服务端裁剪 / 留痕内容 | **已落** | 全路径入 claim [design:561](payroll-automation-design.md)、C-7 [design:739](payroll-automation-design.md)；I-7 服务端裁剪且废止「由端裁剪」[design:717](payroll-automation-design.md)、[design:723](payroll-automation-design.md)；冗余定位列 [design:456-457](payroll-automation-design.md)、[V21:52-57](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql) |
| M-3 中 | 统一终态写守卫 | **已落** | `assertMutable(payroll,action)` 定义与覆盖范围 [design:378-390](payroll-automation-design.md) |
| M-4 中 | 审计链不断裂 | **已落** | `payroll_log` 增 `employee_id`/`month` + 索引 [design:456-457](payroll-automation-design.md)、[design:476](payroll-automation-design.md)、[V21:52-57](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql)（写入侧属实现层） |
| M-5 中 | `fail_reason` 脱敏可执行化 | **已落** | 服务端构造 + 字符截断 + 白名单 [design:573](payroll-automation-design.md) |
| M-6 中 | I-7 裁剪 + 越权单一真源 | **已落** | 复用 `detail()` 单一真源、非 ADMIN 不返 `before/after/operator_*` [design:717](payroll-automation-design.md)、[design:723](payroll-automation-design.md) |
| M-7 中 | 通知白名单拆分 + 最小接收人 + 落定后触发 | **已落** | `SYSTEM_TYPES{7,8,9}` 独立、公告 `1..6` 不变、type 8 仅 `PUBLISHED` 后触发 [design:781-786](payroll-automation-design.md) |
| M-8 中 | `PAID` 单一写守卫 + 非预期态显式拒绝 | **已落** | 同 M-3；`publish` 非允许来源返回显式错误码 [design:386](payroll-automation-design.md)、[design:390](payroll-automation-design.md) |
| M-9 中 | 算薪配置变更留痕（启用 0→1 可追溯） | **已落** | 新表 `station_payroll_setting_log`（10 列、追加型）[design:666-699](payroll-automation-design.md)、[V21:81-94](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql)、[init.sql:970-983](../../sql/schema/mysql/init.sql)；I-3 每次必写、I-9 查询 [design:713](payroll-automation-design.md)、[design:719](payroll-automation-design.md) |
| M-10 低 | 迁移执行显式化 | **部分落**（`ALGORITHM/LOCK` 显式化属运维/数据库实现层，安全报告 R-1/R-2 待核实） | [design:818](payroll-automation-design.md)、[design:965](payroll-automation-design.md) |

**结论：M-1~M-9 方案层均已落点，M-10 属实现/运维层待落地。** 安全「硬阻断」（M-1/M-2 未闭环前禁 B1 执行与生产启用）中各**方案层**要件已具备；**安全复验（L7 闭环）仍待网络安全工程师**。

## 三、独立验算（本次自行推导，未照抄方案）

### 3.1 U-05 钳位验算（独立结论：与口径一致，无「某月无 dueAt / 同月双 dueAt」）

`d_eff = clamp(d0, 1, L)`，`L = 当月天数`；`dueAt = 当月 d_eff 日 hh:mm`。

| d0 | 账期 | L | d_eff | dueAt | target_month |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 31 | 2026-02 | 28 | 28 | 2026-02-28 09:00 | 2026-02 |
| 31 | 2024-02（闰） | 29 | 29 | 2024-02-29 09:00 | 2024-02 |
| 31 | 2026-04 | 30 | 30 | 2026-04-30 09:00 | 2026-04 |
| 29 | 2026-02（非闰） | 28 | 28 | 2026-02-28 09:00 | 2026-02 |
| 30 | 2026-02（非闰） | 28 | 28 | 2026-02-28 09:00 | 2026-02 |
| 31 | 2026-08 | 31 | 31 | 2026-08-31 09:00 | 2026-08 |

- `d_eff ∈ [1, L]` **恒成立** → 每月**恰有且仅有 1 个** `dueAt`，**不存在**「某月无 `dueAt`」或「同月两个 `dueAt`」。✅ 与 [algorithm:186-210](algorithm-payroll-scheduling.md)、[design:796](payroll-automation-design.md) 一致。
- 附注（非缺陷）：不同 `d0`（如 31 与 28）在 2 月均映射到 28 日，属**跨站配置巧合**，因每驿一条 `payroll_day`，不产生**同站**冲突。

### 3.2 U-06 日粒度重试验算（独立结论：跨月归属正确、同日去重正确；**发现「卡死」场景**）

| 序列 | 独立推导 | 结论 |
| ---- | ---- | ---- |
| `dueAt=2026-08-31` 当天失败 → 09-01 补跑 | 08-31 行 (`attempt=08-31`,`claim=NULL`,`FAILED`)；09-01 `INSERT` 新 `attempt_date` 过 `uk_attempt`、`claim_key=NULL` 不撞 `uk_claim` → 执行，`target_month=2026-08` | **不生成 9 月单** ✅ |
| 09-01 再失败 → 09-02 | 09-01 行 `FAILED`/`claim=NULL`；09-02 同理放行 | 持续归 2026-08 ✅ |
| 同日重复 tick | 同日第 2 条 `INSERT` 撞 `uk_attempt` → 1062 → `9410` | 零重复 ✅ |
| `FAILED` 行次日能否再插 | 新 `attempt_date` 过 `uk_attempt`；`FAILED` 的 `claim_key=NULL` 多 NULL 并存不撞 `uk_claim` | **可插** ✅ |

**两唯一键正交性（四序列）**：

| 序列 | `uk_attempt` | `uk_payroll_run_claim` | 结果 |
| ---- | ---- | ---- | ---- |
| 同日重复 | 拦（同日期 1062） | — | 9410 ✅ |
| 跨日重试 | 放行（新日期） | 放行（多 NULL） | 执行 ✅ |
| 连续失败多行 | 放行（不同日期） | 放行（多 NULL 并存） | 允许 ✅ |
| 成功后再次触发 | （当日）拦 | **拦**（`SUCCESS` 占 `claim=m`） | 9410 ✅ |

- **组合卡死（本次独立发现，方案未显式登记）**：当某日 `RUNNING` 行**僵死且未被回收**（回收开关关闭/失效）时——**当日的行不能插入**（`uk_attempt` 拦）；**次日的行也不能插入**（`uk_attempt` 放行，但 `uk_claim` 被该 `RUNNING` 行**永久占位**拦住）→ 该 `(s,m)` 一直回 `9410`，**自动补跑中止**；且因无 `FAILED` 行，`alert-after-*-fail-days` **不触发**→ **静默失效**。方案仅含糊写「真僵死走人工处置（C 档）」（[design:568](payroll-automation-design.md)），未把该场景作为风险显式登记、未给最小恢复步骤。**该卡死的严重度还取决于 `stale-reclaim-enabled` 默认值——而两文件默认值冲突（见 §七 必改 复-1）。**

### 3.3 `force` 语义收敛的独立判定

按 [design:598-610](payroll-automation-design.md) 的交互表逐行推导 `force=false` vs `force=true` 的**可观测结果**：

| 情形 | `force=false` | `force=true` | 是否产生差异 |
| ---- | ---- | ---- | ---- |
| 无占位、当日未尝试 | 执行 | 执行 | **无差异** |
| 无占位、当日已尝试 | 9410（应用层短路） | 9410（改由 `uk_attempt` 1062 拦） | **无差异**（同为 9410，仅路径不同） |
| 已占位 | 9410 | 9410 | **无差异** |
| `RUNNING` 未回收 | 9410 | 9410 | **无差异** |

**独立结论：v1.4 收敛后，`force` 在方案自列的全部四种情形下均无可观测行为差异，`force` 参数已失去存在意义**（其唯一作用退化为「省一次应用层查询」，但无论是否省，`INSERT` 都会被 DB 唯一键拒绝）。详见 §七 必改 复-2。

### 3.4 补跑时刻口径独立核对（发现分歧）

- 方案「错过」判据第 2 步 = **`now ≥ dueAt`**（[design:581](payroll-automation-design.md)）；因 `dueAt` 固定为「当月算薪日」，**跨入次日（乃至更晚）后该条件恒真** → 方案语义下补跑可在**次日 00:00 的任意 tick**触发。
- 算法 §1.4 明确定义补跑**须 `now ≥ atTimeOf(当日, hh, mm)`**（当日 `payroll_time`，可配 `catch-up-time-of-day` 覆盖，默认继承 `payroll_time`）（[algorithm:163](algorithm-payroll-scheduling.md)、[algorithm:372](algorithm-payroll-scheduling.md)）→ 补跑须**等到当日钟点**。
- **独立结论：两文件对「补跑何时触发」存在实质分歧**（方案的「`now ≥ dueAt`」比算法的「同钟点」更早，最早可在 00:0x）；且**方案 §5 参数清单未列 `catch-up-time-of-day`**（[design:808](payroll-automation-design.md) 的「保留/新增/下线」三段均无此参数），而算法已定义该项 → 「补跑时刻如何取」在方案侧**缺口径**。详见 §七 必改 复-3。

## 四、数据层 DDL 静态审查（本机无 MySQL，均为静态判断；标注「待核实」）

1. **`attempt_date` 三段式加列**（[V21:60-70](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql)）：① 加可空列 → ② `MODIFY … DATE NOT NULL DEFAULT (CURRENT_DATE)` → ③ `ALTER COLUMN … DROP DEFAULT`。**最终与「NOT NULL 且无默认」目标一致** ✅；`DEFAULT (表达式)` 与 `ALTER COLUMN … DROP DEFAULT` 需 **MySQL 8.0.13+**（脚本已声明本项目 MySQL 8 [V21:42](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql)），**可行性成立（待核实：具体次版本 ≥8.0.13）**。**「②以执行当日回填存量 NULL 行」的技术断言未验证**（本机无 MySQL），属**防御性**陈述；因 `payroll_run` 与 `V20` 同批新建、**预期空表**，**无实质影响**。
2. **`DROP INDEX idx_payroll_run_station_month` 前提** ✅：该索引**确实存在**于 [V20:89](../../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L89)；**全仓 Java/XML 检索 `idx_payroll_run_station_month` 与 `payroll_run` 均 0 命中**（新表，尚无 Mapper/语句引用），且**不建物理外键（D6）** → **无外键/查询语句依赖，DROP 安全**。
3. **`station_payroll_setting_log` 列构成** ✅：10 列、**无 `is_deleted`/`update_time`**（[V21:81-94](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql)、[init.sql:970-983](../../sql/schema/mysql/init.sql)），符合**追加型审计表**约定（与 `payroll_log`/`leave_log` 同构）。
4. **回滚段完整性** ✅：[V21:96-111](../../hrm-server/src/main/resources/db/migration/mysql/V21__payroll_log_locator.sql) 逆序还原（先 DROP 新表 → `payroll_run` 还原索引/注释/删列 → `payroll_log` 还原注释/索引/列），**逻辑可逆**；已声明「`attempt_date` 回滚会丢日粒度数据」的前提。
5. **快照/迁移一致性**：`init.sql` 已是**终态**（含 `attempt_date`+`uk_attempt`、**无** `idx_payroll_run_station_month`）（[init.sql:945-968](../../sql/schema/mysql/init.sql)）✅；**唯 `notification.type` COMMENT**：`init.sql:148` 已含「7/8/9」，但 **`V21` 未含对应 `MODIFY`** → 快照先于迁移，属**仅注释、低风险待办**（按任务口径**不作为打回理由**）。

## 五、前端影响面独立搜索复核

以 `PAYROLL_STATUS|PENDING_APPROVAL|PUBLISHED|CONFIRMED|editableStatus|EDITABLE_STATUS|EMPLOYEE_VISIBLE_STATUS|objected` 独立检索 `hrm-clients`，命中 22 文件，与 §2.8 清单**高度吻合**，NPE 类风险点**全覆盖**。**独立新发现 3 处 staff 侧未列点位**（均**非 NPE**，`StatusTag` 有 `TYPE_FALLBACK` 回退，属低风险）：
1. `staff-h5/src/views/staff/payroll.vue:93` —— 硬编码 `v-if="item.status === 'PUBLISHED'"`（待确认提示），未列；
2. `staff-h5/src/components/MyPayrollCard.vue:49` —— `StatusTag :dict="PAYROLL_STATUS"` 同 boss 侧点位，未列（boss 侧 counterpart 已列于 §2.8 第 22 行）；
3. `staff-h5/src/constants/todoGroups.js:21` —— staff 待办组 `status:'PUBLISHED'`，未列（boss 侧 counterpart 已列于 §2.8 第 19 行）。

> 结论：**不影响「新增状态不 NPE」的核心完备性**（4 个 NPE 现场已在清单内），但按「逐处硬编码同步」的验收口径仍应补齐，列**低风险必改/待办**（§七 复-4）。

## 六、六维评估（简版 · 仅评本轮修订引入的新问题）

| 维度 | 结论 | 增量说明（已闭环项不复述） |
| ---- | ---- | ---- |
| 1 依据充分性 | **基本满足** | 首评两处（`V8:38` 无源、可观测性过度声明）已修；新增项均为**跨文档一致性**问题（非无源结论）；DDL 技术断言标「待核实」（§四） |
| 2 架构与模块边界 | **满足（但有参数未清理）** | `pay` 补全、判据三分、claim 三处一致均成立；**但 `force` 参数在 v1.4 后失去语义（复-2）** |
| 3 NFR 覆盖度 | **部分满足** | 可观测性收敛已闭环；**新增「`RUNNING` 僵死卡死」场景未登记（复-1）** |
| 4 复杂度与可行性 | **满足** | `uk_attempt` 覆盖并删除冗余 `idx_payroll_run_station_month`，索引最少冗余，方向正确；无过度设计 |
| 5 假设与风险登记 | **部分满足** | 卡死场景未显式登记（复-1）；补跑时刻口径未统一（复-3）；参数命名未统一（复-4） |
| 6 与既有契约一致性 | **与 api.md/db.md 满足；方案内部不一致** | 数据层三方（`V20`/`db.md`/`init.sql`）逐列一致 ✅；**但 design v1.4 ↔ algorithm v1.2 在 `stale-reclaim-enabled` 默认值、补跑时刻、参数命名上不一致**；算法 §1.5 残留已删索引引用 |

## 七、新发现必改项与放行前置条件

### 7.1 新发现必改项（均为可判定；不影响核心「不重复算薪」不变式）

**必改 复-1（中，倾向高）· `stale-reclaim-enabled` 默认值冲突 + 「`RUNNING` 僵死卡死」未登记**
- 问题：方案 `stale-reclaim-enabled` **单实例默认 `false`**（[design:568](payroll-automation-design.md)、[design:808](payroll-automation-design.md)），算法同参数 **默认 `true`**（[algorithm:376](algorithm-payroll-scheduling.md)）→ **同参数默认值直接冲突**，后果相反（`false`→卡死需人工 C 档；`true`→次日自愈）。且方案**未显式登记**「`RUNNING` 僵死 + 回收关闭 → `(s,m)` 被 `uk_claim` 永久占位 → 自动补跑中止、且无 `FAILED` 行致告警**不触发** → **静默失效**」这一卡死场景（§三 3.2 独立验算）。
- 影响：Q7 用户裁定「错过补跑」在「进程在 `RUNNING` 期间被杀」下**静默失效**，与方案「持续重试至成功」承诺不符。
- 验收标准：① 两文件该参数**默认值与理由一致**；② §3.3/§7 增加**风险登记条目**（触发条件 = `RUNNING` 未回收；影响 = 该 `(s,m)` 自动补跑中止、无告警；缓解 = 开启回收且阈值 > 最坏批次耗时，或对超期 `RUNNING` 增设告警 + 最小人工恢复步骤）。
- 复核方式：对读 [design:568](payroll-automation-design.md)/[algorithm:376](algorithm-payroll-scheduling.md) 与新增风险段。

**必改 复-2（中）· `force` 参数在 v1.4 收敛后失去存在意义**
- 问题：§三 3.3 验算表明 `force=false/true` 在方案的**全部四种情形**下结果相同 → **无可观测行为差异**。
- 影响：保留一个「看着像能救急、实则无效」的参数，易误导运维（误以为可「同日再试」）。
- 验收标准（二选一，须可判定）：**(A) 删除** `force` 入参（`trigger` 端点本身仍作人工补跑入口）；或 **(B) 保留**但**明确其唯一作用为「跳过应用层查询优化、无可观测行为差异」**，并删除任何「同日再试/强制重跑」暗示。
- 复核方式：核对 [design:714](payroll-automation-design.md) 入参、[design:598-610](payroll-automation-design.md) 交互表、[design:1029](payroll-automation-design.md) §13.5 表述三者一致。

**必改 复-3（中）· 补跑触发时刻口径分歧 + 缺 `catch-up-time-of-day`**
- 问题：方案判据「`now ≥ dueAt`」[design:581](payroll-automation-design.md) vs 算法「`now ≥ atTimeOf(当日,hh,mm)`」[algorithm:163](algorithm-payroll-scheduling.md)（§三 3.4）；方案 §5 参数清单**缺** `catch-up-time-of-day`（[design:808](payroll-automation-design.md)）。
- 影响：补跑最早可在次日 00:0x 触发，可能早于上游数据日结（算法 R-D2 已警示），取数不全。
- 验收标准：两文件统一为「补跑日须 `now ≥ atTimeOf(当日, payroll_time)`」（采纳算法口径），并在方案 §5 参数清单补 `catch-up-time-of-day`（默认继承 `payroll_time`）。
- 复核方式：对读 [design:575-596](payroll-automation-design.md) 与 [algorithm:155-178](algorithm-payroll-scheduling.md)。

**必改 复-4（低）· 参数命名/清单与前端残余点位对齐**
- 参数：方案（`alert-after-fail-days`、`auto-retry-suspend-after-fail-days`）[design:808](payroll-automation-design.md) vs 算法（`alert-after-consecutive-fail-days`、`alert-repeat-interval-days`）[algorithm:377-378](algorithm-payroll-scheduling.md)；方案有 `auto-retry-suspend-after-fail-days` 而算法**无**，算法有 `catch-up-time-of-day`/`alert-repeat-interval-days` 而方案**无**。方案称「参数值以 `[算法 v1.2]` 为准」但**名称集合不同**。
- 前端：补 §五 的 3 处 staff 侧点位。
- 验收标准：产出**同一份参数清单**（名称/默认值/范围逐项一致）；§2.8 清单补 3 处。
- 复核方式：对读两份参数表与 §2.8。

**低风险待办（不阻断，随 B0/B1）**：① 算法 §1.5 仍以已删除的 `idx_payroll_run_station_month` 作辅助查询路径（[algorithm:248](algorithm-payroll-scheduling.md)），应改指 `uk_attempt` 最左前缀；② `notification.type` COMMENT 迁移（可并入 `V21`，[init.sql:148](../../sql/schema/mysql/init.sql) 快照已含 7/8/9）；③ `db.md` §8.6.3 `idx_payroll_emp_month_bill` 措辞「生成幂等（应用层，非 DB 唯一）」（首评 L1，[design:836](payroll-automation-design.md)）。

### 7.2 放行前置条件清单（可判定、逐条可验）

| # | 前置条件 | 验收方式 |
| ---- | ---- | ---- |
| **P1** | **必改 复-1/2/3/4 闭环**（两文件默认值/时刻/参数名/`force` 处理一致，卡死场景已登记） | 逐条对读对应章节，附「改后状态」 |
| **P2** | **数据层三方对照**（`V20`+`V21` / `db.md` / `init.sql`）与 v1.4 **逐列一致**，由数据库工程师出具对读记录（安全 §5.3 报审前置） | 对读 `claim_key`/`uk_attempt`/`attempt_date`/`skip_code`/`station_payroll_setting_log` 五处 |
| **P3** | **U-16（加扣款金额方向）由用户最终确认**并存档（现为「主代理解释、待用户确认」[design:868](payroll-automation-design.md)）；未确认前不得固化为实现 | 用户裁定归档 + §13.4 更新 |
| **P4** | **安全复验（L7）**：M-1/M-2 方案层要件已具备，实现层（U-13 断言脚本、并发 1062 复现、DTO 服务端裁剪）须经**网络安全工程师复验**出结论 | 安全复验报告 |
| **P5** | **B1 迁移执行**仍属 C 档 → **须主智能体按 §10.3 三步授权**（影响范围/是否可逆/回滚步骤），且**执行前备份库**；本报告**不构成任何执行授权** | C 档授权表单 + 备份记录 |
| **P6** | `attempt_date` 三段式 `DEFAULT (CURRENT_DATE)` 与 `DROP DEFAULT` 的 **MySQL 次版本（≥8.0.13）** 与「表空/非空」保护路径，**服务器阶段实证** | 执行前 `SELECT VERSION()` + 空表核验 |

## 八、复评结论

- **结论等级：有条件通过**（**可进入报审流程**，报主智能体审批时**须附本复评的放行前置条件清单**）。

**判定理由（按 `tech-evaluation` 分级）：**
1. 首评 10 条必改**已闭环 9 条、部分闭环 1 条（低残余）、未闭环 0 条**；无「影响主干正确性/与 `api.md`/`db.md` 冲突/无源结论」的新问题；核心不变式「**不重复算薪**」（`uk_payroll_run_claim` + `uk_attempt` 正交）经独立验算成立，无重复单据路径。
2. 本轮新发现 4 条必改（复-1~4）均为**口径统一/参数清理/风险登记**类，**不影响主干正确性**——符合「有条件通过」定义（首评曾判「打回」的理由：缺 `pay` 端点、数据层不自洽、无源结论，**均已消除**）。
3. 数据层三方（`V20`+`V21`/`db.md`/`init.sql`）**逐列一致**；`DROP INDEX` 前提经全仓检索确认无依赖；回滚段完整；审计表列构成正确。
4. 仍存**跨文档一致性**缺口（`stale-reclaim` 默认值冲突、补跑时刻分歧、参数命名不齐）与**卡死场景未登记**，须按 P1 收敛后方可放行实施；`U-16` 未决项须按 P3 由用户确认。

**退回对象（复评修订，不新增无关要求，评估范围冻结）：**
- **架构师**（主）：修订 `payroll-automation-design.md` → v1.5，闭环 **复-1（方案侧）、复-2、复-3、复-4（参数/前端清单）**；
- **算法工程师**：同步 `algorithm-payroll-scheduling.md` 的 `stale-reclaim-enabled` 默认值与补跑时刻/参数命名（闭环 **复-1（算法侧）、复-3、复-4**），并清理 §1.5 已删索引残留引用；
- **数据库工程师**：`V21` 补 `notification.type` COMMENT（低风险待办）与三方对读记录（P2）。

**放行前置（本报告不构成任何 C 档授权）**：见 §7.2 P1~P6；B1 迁移执行须主智能体三步授权（§10.3），安全面仍须网络安全工程师复验（P0.5 / L7），二者**并行不互替**。版本再变更须重评。

---

*复评声明：本报告为静态文档级评估，未运行任何构建/测试/SQL（本机无 JDK/MySQL，未连接数据库）；未产出、未修改任何被评方案；未做安全实质判定；未做口径裁定；不含真实凭据或业务数据。*
