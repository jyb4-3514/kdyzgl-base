# 安全技术评估报告：薪资结算自动化与全链路留痕

| 项 | 内容 |
| --- | --- |
| 评估对象 | ① `hrm-dev/docs/payroll-automation-design.md`（架构方案 **v1.1**，679 行）；② `hrm-dev/docs/algorithm-payroll-scheduling.md`（算法四件套，447 行）；③ `hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql`（194 行）；④ `hrm-dev/docs/db.md` §8.6.5~§8.6.7 与 §9.4 U-13 |
| 关联既有实现 | `PayrollServiceImpl.java` / `PayrollController.java` / `NotificationServiceImpl.java` / `ClientLogSanitizer.java` / `PayrollStateMachine.java` / `PayrollGenerateGuard.java` / `PayrollLockPolicy.java`（**只读**） |
| 任务来源 | 主智能体派发（安全面技术结论）；依据 `项目规则1.md` §7.2 / §8 审批纪律、调度规则 **P0.5 安全评估先行**、**L7 安全评估 → 授权 → 实现**、**R24** |
| 评估方 | 网络安全工程师 `express-station-security-engineer`（**只出技术结论 + 风险分级 + 修复建议与验收标准；不写业务方案、不改代码、不执行 git/部署/MCP、不代授权**） |
| 评估方式 | **纯静态只读审计**（源码 / 迁移脚本 / 文档）。**未运行任何构建、测试、脚本，未连接任何数据库**；本机无 JDK / Maven / MySQL / Redis。一切「数据库实际行为」按 **待核实** 处理 |
| 权限档位 | A 档（只读检索 + 新建本报告）|
| 凭据声明 | 报告内**不含任何真实凭据 / 域名 / IP / 令牌**；未读取凭据明文 |
| 报告日期 | 2026-09-27 |
| **结论等级** | **有条件放行**（放行范围与两条硬性阻断条件见 §5）|

---

## 1. 评估范围与取证方式

### 1.1 取证方式

1. 通读方案 v1.1 全文（679 行）与算法四件套、`V20` 全文、`db.md` §8.6.5~§8.6.7 + §9.4 U-13。
2. 反向核对**既有实现的全部工资单写入口**：`PayrollController` 的 10 个端点逐一比对 `PayrollServiceImpl` 的目标状态、守卫与副作用；核对 `NotificationServiceImpl` 的类型白名单与接收人解析；核对 `ClientLogSanitizer` 的脱敏口径与适用范围。
3. 核对端点鉴权基础设施（`RequireRolesInterceptor` fail-closed 语义）与公开端点白名单。
4. 只读检索确认「全仓无调度能力」「无 payroll 删除端点」等前提。
5. 每条结论标注 `文件:行号`；无法从仓库证实的写「**待核实**」。

### 1.2 已核实事实（本次审计新增或复核）

| # | 事实 | 出处 |
| --- | --- | --- |
| F1 | 端点角色门槛 **fail-closed**：非公开端点未声明 `@RequireRoles` 一律 403，杜绝「漏声明即静默放行」 | [RequireRolesInterceptor.java:41-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L41-L50) |
| F2 | 财务域**不在公开白名单**（公开面仅登录/短信/验证码/注册提交/企微预留） | [PublicEndpoints.java:57-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L57-L60) |
| F3 | 现有 `generate` 的 9405 阻断判定为 **全局账期级**：查全月 `MONTHLY` 单，**不带 `stationId`** | [PayrollServiceImpl.java:155-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L155-L163) |
| F4 | `generate` 对同员工同月旧单 **物理删除再重建**（`deleteExisting` → `createPayroll`） | [PayrollServiceImpl.java:173-175](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L173-L175)、[PayrollServiceImpl.java:612-621](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621) |
| F5 | `deleteExisting` 的删除范围由 `editableStatuses()` = `{DRAFT, REJECTED}` 限定 → **已提交/已发布/已确认不取不删** | [PayrollGenerateGuard.java:20-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L20-L22)、[PayrollServiceImpl.java:613-614](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L613-L614) |
| F6 | 工资单**无任何删除端点**（`PayrollController` 全 10 接口无 delete / 无 `is_deleted` 置位入口） | [PayrollController.java:48-125](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L48-L125) |
| F7 | `updateItems` 允许改 `MANUAL` 项金额，并把 `detail` **覆盖写为「人工填写」** | [PayrollServiceImpl.java:357-363](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L357-L363) |
| F8 | 非 ADMIN 访问工资单详情的**单一真源**：`employeeId == userId` + 状态 ∈ `{PUBLISHED, CONFIRMED}` | [PayrollServiceImpl.java:320-327](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L320-L327)、[PayrollServiceImpl.java:86-87](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L86-L87) |
| F9 | `publish(ids)` 对**非 `APPROVED`** 单据 **静默 `skipped++`**，不报错 | [PayrollServiceImpl.java:252-256](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L252-L256) |
| F10 | `PUBLISH_TYPES = Set.of(1..6)`，且**同一白名单被手工公告发布与系统联动 `sendSystem` 共用** | [NotificationServiceImpl.java:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)、[:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L143)、[:184](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184) |
| F11 | 手工公告发布端点 `POST /api/v1/notifications/publish` 为 **ADMIN-only**，可按 `ALL/STATION/EMPLOYEE` 扇出 | [NotificationController.java:74-79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/notification/NotificationController.java#L74-L79)、[NotificationServiceImpl.java:216-247](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L216-L247) |
| F12 | 通知服务内**不存在**「管理员集合」解析；`sendSystem` 只接受单一 `employeeId` | [NotificationServiceImpl.java:179-208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L179-L208) |
| F13 | **`db.md` §8.6.7 与 §9.4 U-13 仍为 v1.0 语义**：`success_key` 仅 `SUCCESS` 写值，并断言 `RUNNING/FAILED/SKIPPED` 为 NULL、可多行 | [db.md:966-968](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L966-L968)、[db.md:982](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L982)、[db.md:993](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L993)、[db.md:1282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L1282) |
| F14 | `V20` 手工公告口径同样为 v1.0：`success_key` + `uk_payroll_run_success`，且「`V20` 为静态产出（本机无 MySQL，未实跑）」 | [V20__payroll_automation.sql:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L78)、[:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L86)、[:27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L27) |
| F15 | `db.md` §8.6.5 中 `payroll_day` 注释仍为「建议 1-28」，与方案 U-05 定稿「1-31 并钳位」不一致 | [db.md:911](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L911)、[design:321](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L321) |
| F16 | `PAID` 冻结在方案中仅以「状态集合 + 9413」表达，未指定**检查收口的落点层级**；现有守卫分散在 5 处 | [design:239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L239)、[PayrollServiceImpl.java:340](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L340)、[:415-420](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L415-L420)、[:440-441](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L440-L441)、[:634-641](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L634-L641) |

### 1.3 边界声明

- **不代改代码**（反模式 A17）：本报告一字未改任何源码 / 配置 / 迁移脚本 / 既有报告；未执行 `git`；未调用 MCP；未读取凭据明文。
- **不代授权**：仅出技术结论、风险等级与是否可放行；是否放行由主智能体按 §10.3 决策；判高风险不得直接放行。
- 不做功能正确性测试与门禁实跑（只做安全维度）。
- **未验证项未闭环前，本报告不声称「已确认合规」。**

---

## 2. 逐项评估结论

### 2.1 「管理员手工加扣款无需审批、直接落库」的内部舞弊面

**结论：结构上可事后审计，但存在 3 处可判定缺口；定性为「职责分离缺失」，风险等级 中。**

**(1) 是否构成「单人可无审批修改实发工资」的权限过度集中 —— 是。**

新增接口 I-6 `items/add`、C-3 `items` 更新、C-5 审批、C-2 发布、以及新增 `pay`（发放归档）**全部 `{"ADMIN"}`**（[design:498-504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L498-L504)、[design:513-520](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L513-L520)）。既有实现亦为 ADMIN 全权（[PayrollController.java:59-110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L59-L110)）。即：**单一 ADMIN 可独立完成「改金额 → 提交 → 审批 → 发布 → 确认发放 → 归档」全链路，无第二人复核。**

该结果是用户口径 Q2 的**直接后果**（[design:69](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L69)），属**口径红线**（`项目规则1.md` §11.4）：安全侧**不得**以「加审批」覆盖用户裁定。安全侧的判定与建议只能是**补偿性控制**（见 M-3 与本节 (4)）。

**(2) 现有留痕是否足以事后审计 —— 结构足够，内容口径不足。**

`payroll_log` 设计的字段（`action` / `operator_id` / `operator_name` / `operator_role` / `time` / `from_status` / `to_status` / `reason` / `before` / `after`）**在结构上能够**回答「谁在何时对谁的工资做了什么、理由是什么」（[design:342-359](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L342-L359)、[db.md:935-950](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L935-L950)）。但三处内容口径不足，导致关键问句「**净额差多少、差在哪一笔**」不能稳定回答：

- **缺口 a（before/after 无字段白名单）**：方案仅写「金额 / 合计等」（[design:357-358](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L357-L358)），§3.4 更只说「`before/after=合计前后`」（[design:475](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L475)）。若只记合计（`netAmount`），在**连续多次调整**或**并发调整**下无法唯一反推「哪一笔、多少」；若实现改为 dump 实体，则可能带入 `rule_snapshot`（计薪规则参数＝经营信息）等超范围内容。**必须显式定义白名单**（见 M-3）。
- **缺口 b（ITEM_UPDATE 事由可选 + item 事由被覆盖）**：C-3 明确 `item-update` 的 `reason` 为「**可选**」（[design:516](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L516)），与 Q3「每笔手工加扣款**必填事由**」冲突——**修改一笔已有加/扣款的金额同样是「一笔手工调整实发工资」**。更严重的是既有实现会把该明细的 `detail` **覆盖为「人工填写」**（[PayrollServiceImpl.java:362](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L362)），而方案把事由全文存于 `payroll_item.detail`（[design:376](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L376)、[design:464](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L464)）→ **若 `reason` 又不填，则该次调整的事由在两侧同时丢失。**
- **缺口 c（孤儿留痕，追溯可达性断裂）**：`generate` 对 `DRAFT/REJECTED` 单据**物理删除**（主单 + 明细）（[PayrollServiceImpl.java:612-621](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621)）；而 `payroll_log.payroll_id` 为 `NOT NULL` 且**无外键、无级联**（[V20:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L49)）。方案 R11 只**迁移 `payroll_item`**（保留项，且可能重生成 `item_key`）（[design:467-476](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L467-L476)），**未迁移 `payroll_log`**。由于「操作人」只落在 `payroll_log.operator_*`、`payroll_item` 不加列（[design:376](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L376)），**DRAFT 期录入的手工项在下次 `generate` 重建后，其 `ITEM_ADD/ITEM_UPDATE` 留痕行的 `payroll_id` 指向已物理删除的单据** → 经 `GET /{id}/logs` 不可达、`operator` 追溯断裂。（发布后时点的追溯不受影响，故定级 中。）

**(3) 是否存在绕开留痕的路径 —— 逐入口核对结果。**

| # | 写入口（含设计新增） | 留痕设计 | 核对结论 |
| --- | --- | --- | --- |
| 1 | `POST /payrolls/{id}/items/add`（I-6） | `ITEM_ADD` + `reason` 必填（9412） | ✓ 覆盖 |
| 2 | `PUT /payrolls/{id}/items`（C-3） | `ITEM_UPDATE`，**`reason` 可选** | ✗ 见缺口 b |
| 3 | `POST /payrolls/generate`（C-7） | 每单 1 条 `GENERATE_*`（含 `manualKept`） | △ 粒度粗（不含被重建项的逐项 delta）；且产生缺口 c 的孤儿 |
| 4 | `POST /payrolls/submit` / `{id}/approve`（C-5） | 补写 `SUBMIT` / `APPROVE` / `REJECT` | ✓ |
| 5 | `POST /payrolls/publish`（C-2） | 写 `PUBLISH` / `REPUBLISH` | ✓（批量路径逐单写，[PayrollServiceImpl.java:238-245](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L238-L245)，单次调用可改变整月全部员工状态，无二次确认） |
| 6 | `POST /payrolls/{id}/confirm` / `objection`（C-1/C-4） | 写 `CONFIRM` / `OBJECTION` | ✓ |
| 7 | `POST /finance/payrolls/{id}/pay`（设计新增） | 写 `PAY` | ✓ |
| 8 | 直接改库 / SQL（应用外） | 无留痕 | △ 需 DB 最小权限 + 变更审计兜底（建议项，非本次可判定） |
| 9 | 删除工资单 / 置 `is_deleted` | — | ✓ 实测**无任何删除端点**（F6），无外部置位路径 |
| 10 | `createSettlement`（P5 内部端口） | 方案未定义留痕 | △ 只产 `DRAFT`，非手工加扣款；风险 低 |

**结论：不存在「完全无留痕的对外写入口」；主要绕行/弱化点是 #2（事由可选）、#3（粒度与孤儿），以及 #8（应用外改库，非本方案可覆盖）。**

**(4) 金额阈值二次确认 / 独立对账视图 —— 建议采纳（补偿性控制）。**

- **需要**。理由：SoD 缺失下，「修改实发工资」这一高敏感动作目前**零事中摩擦**；补偿控制的性价比最高。
- 具体建议（**不引入「他方审批」以免与 Q2 冲突**）：
  1. **阈值即时告警 + 二次确认（同一操作人）**：单笔金额 ≥ `X` 或单月累计 ≥ `Y` 时，服务端强制记录二次确认（同弹窗确认语义）并即时推送给**管理员群体 + 主智能体可见的审计视图**；阈值一律外置可配（`项目规则1.md` 反模式 A03）。
  2. **独立对账视图**：按月输出「手工调整**笔数 / 加总额 / 扣总额 / 操作人分布 / 单笔 TopN**」，与 `payroll_item(source=MANUAL)` 对账（数据源可直接用 `idx_payroll_log_action_time`，[V20:64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L64)）。
  3. **事由必填扩展到 ITEM_UPDATE**（见 M-3）。
- **若组织要求「他方复核 / 双人操作」**：属**口径变更**，须由用户裁定（§11.4）。安全侧不代拍板，仅登记为升级事项。

---

### 2.2 新增进程内定时调度 + 自动写财务数据（生产变更面）

**结论：调度本体不引入外部暴露面；但幂等硬防线（Layer 0）在**当前下游产物**中与设计 v1.1 不一致，且手工生成未纳入硬防线。风险等级 高（REG-01）/ 中（其余）。**

**(1) 是否可能被外部触发 —— 否（设计态）。** 调度仅为进程内 `@Scheduled` 固定间隔轮询（[design:153-160](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L153-L160)），唯一新增外部触发点为 `POST /api/v1/finance/payroll-runs/trigger`（[design:160](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L160)、[design:501](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L501)）。**鉴权收敛为 `{"ADMIN"}` ✓**，且与 fail-closed 拦截器一致（F1）——若实现漏标 `@RequireRoles`，端点会被 403 拒绝（安全兜底成立）。
**待核实**：实现阶段须确认未引入 actuator / 其他管理端点的调度触发面（本机无法验证运行态）。

**(2) 误跑 / 重复跑 —— 财务后果最坏为「同一员工同账期出现两条已发布/已确认工资单」，即**重复发放**与对账失真，并直接违反 Q9。**
幂等三层防线现状核对（[design:441-457](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L441-L457)）：

| 层 | 设计 v1.1 期望 | **实测下游产物** | 结论 |
| --- | --- | --- | --- |
| Layer 0 调度层 | `uk_payroll_run_claim (station_id, claim_key)`；`RUNNING`/`SUCCESS`/`SKIPPED` 均占位 | `V20` = `uk_payroll_run_success (station_id, success_key)`，**仅 `SUCCESS` 写值**（[V20:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L78)、[:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L86)）；`db.md` §8.6.7 同（F13）；**§9.4 U-13 验收断言「`RUNNING`/`FAILED`/`SKIPPED` 可多行」**（[db.md:1282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L1282)） | **✗ 不一致**：`RUNNING` **不占位** → 重叠 tick / 未来多实例可**双执行** → 重复工资单；且 U-13 会把此缺陷**判为通过** |
| Layer 1 生成层 | 9405 **按驿站收敛** | 当前**全局账期级、不带 `stationId`**（F3） | ✗ 方案已列为 M1 必改（[design:655](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L655)） |
| Layer 2 单据层 | 已自认**不存在** | `idx_payroll_emp_month_bill` 为普通 `KEY`（[V8__payroll.sql:85](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85)） | ✗ 无 DB 唯一约束（方案已登记，[design:447](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L447)） |

**方案 M6 只列了 `V20` 的修订（[design:660](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L660)），未列入 `db.md` §8.6.7 与 §9.4 U-13** —— 这是本次审计判定的**最高风险项（REG-01）**：Layer 0 是**唯一 DB 硬防线**，其下游产物若按现状执行 `V20`，防线对「并发/多实例」实际失效。

此外，**手工 `generate` 不走 `payroll_run` claim**（[PayrollController.java:60-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L60-L64)），与 tick 并发时仅剩 Layer 1 的 `check-then-act`（存在竞态，[PayrollServiceImpl.java:155-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L155-L163)）→ 方案已自认残余风险 R1-A（[design:454-456](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L454-L456)），但其「缓解」仅为后置巡检（`COUNT(*) == COUNT(DISTINCT employee_id)`），**属检测而非阻断**。

**(3) 调度开关默认值 —— 安全默认成立 ✓。** `enabled` 默认 0（[V20:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L34)、[design:195](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L195)、[algorithm:240](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L240)）。**上线后由 ADMIN 经 I-3 启用**（[design:500](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L500)）。
**但「启用是否留痕」—— 未留痕。** I-3 的设计未声明任何审计写入；而 `payroll_log.payroll_id` 为 `NOT NULL`（[V20:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L49)），**结构上无法承载配置变更事件** → 无法回答「谁在何时启用了自动算薪 / 改了某驿站算薪日」。风险 中（见 M-9）。

**(4) 失败隔离与僵死 `RUNNING` 回收 —— 隔离设计合格，回收机制有「误判 + 重复执行」风险。**
- 逐驿站 `try/catch` + 独立事务 ✓（[design:192](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L192)、[design:577](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L577)）。
- 回收：「`start_time < now - running-timeout-minutes` → 置 `FAILED`（释放槽位）→ 允许重试」（[design:422](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L422)、[algorithm:576](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L576)）。风险：
  - **误判**：方案的量级估算自认单次全量可达**十万级 insert**（[design:582](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L582)）。若 `running-timeout-minutes` 小于最坏批次耗时，**正常运行中的任务会被判 `FAILED` 并释放槽位** → 下一 tick 重复执行 → 重复单（与 REG-01 叠加放大）。要求：超时阈值必须 > 最坏批次耗时（含批插入优化后的实测），且回收 UPDATE 必须带 `WHERE status='RUNNING' AND start_time < ...` 守卫，并**不允许与「进行中 task 的串行保证」冲突**。风险 中（REG-10）。
  - **占位/资源**：逐驿站串行 + 单站长事务（十万级 insert）会长时间持有连接/锁；属可用性面（本报告按安全红线不展开 DoS 判定），但仍建议 `max-stations-per-tick` + 批插入（方案已列 TODO）。

---

### 2.3 新表数据面（含个人薪资敏感信息）

**结论：访问收敛方向正确（新接口全 ADMIN / 本人），但两条**内容级脱敏口径缺失**、一条**服务端裁剪未强制**。风险等级 中。**

**(1) `payroll_log.before/after` 是否会写入敏感信息 —— 存在可能，缺可执行约束。**
字段类型为 `JSON`、注释仅「金额 / 合计等」（[V20:59-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L59-L60)、[db.md:948-949](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L948-L949)）。**未定义字段白名单、未声明禁止 dump 实体**。薪资（金额）本身就是**个人敏感信息**；若实现将实体/请求上下文整体序列化，还会带入 `rule_snapshot`、经办人信息等。→ 见 M-3。

**(2) `payroll_run.fail_reason` 是否已给出可执行脱敏约束 —— 否。**
方案与算法四件套均只写「**截断（≤500）且不落敏感信息**」（[V20:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L77)、[algorithm:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L201)），**这是目标陈述，不是可执行约束**。
关于「对照 `ClientLogSanitizer` 的脱敏口径」的核对结论：**不能直接背书**——
- 该类是**客户端运行日志上报**的清洗器，采用「**白名单复制**」（只绑定 `ClientLogItem` 显式字段入 `ClientLog`），未列字段天然丢弃（[ClientLogSanitizer.java:62-86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L62-L86)）；
- 其凭据擦除正则为 `token=|accessToken=|refreshToken=|password=|pwd=` 五种**凭据形态**（[ClientLogSanitizer.java:48-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L48-L49)、[:99-101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L99-L101)），**不覆盖身份证 / 银行卡 / 金额 / 姓名**；
- 业务异常文本（SQL 异常、参数校验异常）常携带**参数值**，恰好是上述正则的盲区。
→ 见 M-5。风险 中（REG-05）。

**(3) 新接口读取权限收敛核对。**

| 接口 | 设计角色 | 越权防护核对 | 结论 |
| --- | --- | --- | --- |
| I-1 `GET /payroll-settings`（全站列表） | `{"ADMIN"}`（[design:498](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L498)） | 与 boss-h5 端准入（仅 ADMIN）一致；非预期角色不可读跨驿站配置 | ✓ 风险 低（实现只需落死 ADMIN；**将来若开放给 `STATION_ADMIN`，必须按 `stationId` 收口**） |
| I-2 `GET /payroll-settings/{stationId}` | `{"ADMIN"}` | 同上 | ✓ |
| I-7 `GET /payrolls/{id}/logs` | `{"ADMIN","STATION_ADMIN","STAFF"}`（非 ADMIN **仅本人单 + 仅已发布及之后**）（[design:504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L504)） | 契约已写明 9404/9403 语义，与既有 `detail()` 的单一真源一致（F8）；**但方案未指定「必须复用该单一真源」** | △ 风险 中：若实现仅校验角色而未逐单校验「本人 + 状态可见」，任一 STAFF 可按 id 遍历**任意**工资单的完整留痕（含操作人姓名、before/after）→ **水平越权（IDOR）**。要求见 M-6 |

**关键冲突（越权信息暴露）：** I-7 的**出参要点把 `before`/`after` 列给全部角色**（[design:504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L504)），而 U-14 裁定「员工仅见 `action`/`time`/`reason`/`toStatus`；`before`/`after` 仅 ADMIN 可见」，落点写的是「**由端裁剪字段实现**」（[design:508](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L508)、[design:633](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L633)）。「端」若指**前端**，即为「前端隐藏而后端照返」——**抓包 / 直连接口即可获得，属越权信息泄漏**（同时违反 U-14 与最小必要）。→ 见 M-6。风险 中（若 `before/after` 严格限于该员工本人金额，机密性外溢有限；但**违反既定裁定**且可能带出规则快照/经办信息，故仍须服务端强制裁剪）。

**(4) 追加型表的不可变性。** `payroll_log` 无 `is_deleted` / `update_time` ✓（[V20:47-65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L47-L65)、[db.md:961](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L961)），语义为 append-only；但**仅有应用层约定**，DB 层未限制（建议：应用 DB 账号对本表仅 `INSERT`/`SELECT`，属运维/数据库 C 档建议，见 REG-15）。

---

### 2.4 通知外泄面（Q8）

**结论：接收人解析在通知服务内无落点，且类型白名单被公告发布共用 → 存在**误推**与**仿冒**两个可得路径。风险等级 中。**

**(1) 类型 7/8/9 的放行方式 —— 方案的做法会打开「公告端点仿冒薪资通知」的口子。**
方案要求把 `PUBLISH_TYPES`（现 `1..6`）**同步放行 7/8/9**（[design:560](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L560)）。但实测该白名单**同时**服务于：
- 手工公告发布 `publish()`（[NotificationServiceImpl.java:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L143)）——端点为 **ADMIN-only**，可 `ALL/STATION/EMPLOYEE` 扇出、`biz_id=null`、`title/content` 任意（F10/F11）；
- 系统联动 `sendSystem()`（[NotificationServiceImpl.java:184](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184)）。

若按方案直接扩白名单，则 **ADMIN 可经公告端点以 type=7/8/9 向任意员工群发伪造的「工资单待审核 / 已发布 / 异议退回」通知**（`is_published=1`、无业务跳转）。影响：应用内**社工/钓鱼**（员工难以区分真伪的薪资提示），并破坏「类型 8 只可能来自真实发布」的语义保证。→ 见 M-7。风险 中（触发者需 ADMIN 权限，故非越权提权，但属**可信度滥用 / 仿冒**）。

**(2) 接收人解析是否正确收敛 —— 无落点，存在误推风险。**
方案裁定「管理员 = 全部在职 `role=ADMIN AND status=1`」（[design:561](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L561)），但该解析在 `NotificationServiceImpl` 中**不存在**：`sendSystem` 只接受**单一** `employeeId`（F12），且**缺失即静默 return**（[NotificationServiceImpl.java:181-183](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L181-L183)）。
风险：实现方若**复用现成的 `resolveTargets("ALL")`（返回全部在职员工，[NotificationServiceImpl.java:245-246](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L245-L246)）或 `"STATION"`** 来解析「管理员」，就会把类型 7/9 **推给站长/员工**。→ 见 M-7。风险 中。

**(3) 「发布后才推员工」是否存在未发布即推送的路径 —— 设计口径正确，但缺强制落点。**
方案要求 type 8 仅在 `publish` 成功后、事务提交后触发（[design:556](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L556)、[design:194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L194)）。风险点：若实现把「推员工」写在 `generate`/`approve`/`submit` 分支，或未把「员工可见状态」作为发送前置，就会出现**草稿生成即推员工**。→ 见 M-7，要求以「状态已落 `PUBLISHED` 及以上」作为硬前置，并断言未发布状态**零** type 8 通知。

**(4) 附带项（低）。** `sendSystem` 对非法 `type` / 超长 title/content 会**抛 `BusinessException`**（[NotificationServiceImpl.java:184-192](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184-L192)）；而方案要求「通知失败只写 `NOTIFY_SKIP`、不阻断主流程」。两者需在调用侧以 `try/catch` 对齐，否则 `afterCommit` 阶段异常可能外溢（REG-17）。

---

### 2.5 归档终态（`PAID`）的可绕过性（Q9）

**结论：在「当前已知写入口」下冻结成立；但**缺少单一强制收口**，属设计鲁棒性缺口。风险等级 中。**

**(1) 拦截落点核对（哪些层、哪些入口）。** 现有守卫**分散在 5 处**，无单一收口：

| 写入口 | 现守卫 | 依据 |
| --- | --- | --- |
| `submit` / `approve` | `requireAction` → `PayrollStateMachine.ACTIONS` | [PayrollServiceImpl.java:634-641](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L634-L641)、[PayrollStateMachine.java:35-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStateMachine.java#L35-L44) |
| `updateItems` | 内联 `isEditable(status)` | [PayrollServiceImpl.java:340](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L340) |
| `confirm` | 内联「本人 + `PUBLISHED`」 | [PayrollServiceImpl.java:411-420](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L411-L420) |
| `objection` | 内联「本人 + `PUBLISHED`」 | [PayrollServiceImpl.java:436-441](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L436-L441) |
| `publish` | 内联 `APPROVED.equals(status)`，否则**静默 skip** | [PayrollServiceImpl.java:252-256](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L252-L256) |
| `generate` | 9405（`PayrollGenerateGuard`） | [PayrollServiceImpl.java:159-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L159-L163) |

逐入口推演 `PAID` 行为：`submit`（`ACTIONS` 无 `PAID` 键 → `actionsOf` 返回空 → 拒绝 ✓）、`updateItems`（`PAID ∉ isItemEditable` → 拒绝 ✓）、`confirm`/`objection`（`PAID ≠ PUBLISHED` → 拒绝 ✓）、`publish`（非 `APPROVED` → **静默 skip**，无错误码）、`generate`（`PAID` 属非可覆盖 → 9405 阻断 ✓）。
**风险点**：方案 §2.2 只写「`PAID` 下任何动作一律 9413」（[design:239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L239)），却**未指定该判定落在哪一层**。守卫已分散 5 处，新增 `pay` 与扩展 `isItemEditable` 后若不收口，**任一入口漏加即构成绕过**（例如未来新增「批量调整」「明细删除」）。→ 见 M-8。风险 中（REG-11）。

**(2) `generate` 的覆盖重建 / `deleteExisting` 是否会误删已发布/已发放单 —— 不会 ✓。**
删除范围严格限定为 `{DRAFT, REJECTED}`（F5），方案 §2.3 亦明确 `isOverwritable` **保持 `{DRAFT, REJECTED}` 不变**（[design:252](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L252)）→ `PENDING_APPROVAL`/`OBJECTED`（新增可编辑态）**不进入覆盖范围**，`PUBLISHED`/`CONFIRMED`/`PAID` 不取不删。✓
**但**：R11 的覆盖重建会产生 §2.1 缺口 c 的孤儿留痕（不涉及「删除已发布单」，但影响 DRAFT 期留痕可达性）。

**(3) `is_deleted` 置位 —— 当前无可达路径 ✓。**
实测**无任何工资单删除端点**（F6）；`deleteExisting` 为自定义物理删除且带状态白名单，不走 `@TableLogic`（[PayrollServiceImpl.java:604-606](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L604-L606)）。风险 低；**约束**：未来新增任何 payroll 删除/批量端点时必须保持终态守卫（登记 REG-13）。

---

### 2.6 迁移与上线（C 档）

**结论：`V20` 不触碰存量业务数据 ✓；`MODIFY COLUMN status` 在标准写法下为元数据变更、**低风险**，但脚本未显式声明算法/锁且 MySQL 版本未举证（**待核实**）。**

**(1) 是否触碰存量 `payroll` 数据 —— 否。**
`V20` 对既有 `payroll` 仅：`ADD COLUMN paid_by_id / paid_by_name / paid_time`（列尾追加、可空、无默认）+ `MODIFY COLUMN status`（**仅更新 COMMENT**）（[V20:92-96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L92-L96)）。**无 `UPDATE`/`DELETE`/回填/数据转换**（注释亦声明「不产生数据转换」[V20:100-101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L100-L101)），与 U-13「不回填」一致（[design:292](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L292)）。3 张新表为 `CREATE TABLE` ✓。

**(2) `ALTER TABLE payroll MODIFY COLUMN status` 是否可能触发表重建 / 长锁表 —— 按当前脚本写法：不会；但存在写法退化风险。**

- 当前脚本**重复了完整列定义**（`VARCHAR(20) NOT NULL DEFAULT 'DRAFT'`），仅 `COMMENT` 变（[V20:96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L96)）→ 在 MySQL 8.0 / 5.7 上属**仅元数据（COMMENT）变更**，可 `INPLACE`、**不重建表、不长时间锁表**；`ADD COLUMN`（可空、列尾、无默认）在 8.0 为 `INSTANT`。
- **风险与建议**：
  1. 脚本**未显式声明 `ALGORITHM=INPLACE, LOCK=NONE`**，算法由优化器按版本择定；**若后续有人重写 `MODIFY` 时丢失 `NOT NULL`/`DEFAULT`，则可能触发 `COPY`（重建 + 长锁）**。→ 要求显式声明并附前后 DDL 比对（M-10）。
  2. **`payroll` 表实际行数未举证**（本机无 MySQL，未实跑）→ 表规模为「员工数 × 账期数」，非 20 万级大表（[V20:102-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L102-L103)）；即便如此，仍建议**低峰窗口**执行。
  3. **版本待核实**：`V20` / `db.md` 未举证目标 MySQL 版本（`utf8mb4_0900_ai_ci` 暗示 8.0，但非证据）。→ 待核实项 R-2。
- **窗口/方式建议**：备份 → `SHOW CREATE TABLE payroll` 存档 → 低峰执行（显式 `ALGORITHM=INPLACE, LOCK=NONE`）→ 执行后 DDL 差异比对 → 校验 3 张新表索引与 8 态 COMMENT。属 **C 档**（结构变更，[V20:26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L26)），须主智能体三步授权、先备份。

**(3) 回滚 —— 不可逆点已识别 ✓。** `DROP TABLE payroll_run / payroll_log / station_payroll_setting` 将**不可逆丢失审计与运行记录**；脚本已注明「回滚前须确认无 OBJECTED/PAID 存量」（[V20:106-116](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L106-L116)）。属 C 档，须先备份。

**(4) 批次顺序（安全侧补充）。** 方案已要求「前端字典须先于/同批于后端返回新状态」（[design:600](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L600)）。安全侧**追加硬约束**：**「`PAID` 冻结守卫」「I-7 服务端字段裁剪」「通知白名单拆分」必须与状态枚举（`OBJECTED`/`PAID`）同批上线**——否则会出现「状态可流转但守卫未上」的中间态（冻结失效 / 越权信息暴露窗口）。见 §5 放行前置。

---

## 3. 必做项清单（M-x）

> 分级：**高** = 未闭环不得放行对应范围；**中** = 上线前须闭环；**低** = 建议随批落地。
> 「验收标准」均可在测试环境以命令 / 断言 / 抓包级验证判定；本机无环境，故均为**待验证要求**。

### M-1【高】Layer 0（claim）三处产物一致性对齐
- **问题**：`V20` 与 `db.md` §8.6.7、§9.4 U-13 仍为 v1.0 `success_key` 语义，与设计 v1.1 claim 槽位冲突；U-13 反向断言「`RUNNING`/`FAILED`/`SKIPPED` 可多行」，会把「`RUNNING` 不占位」判为通过。
- **依据**：[design:411-422](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L411-L422)、[design:602-610](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L602-L610)（M6 未含 db.md/U-13）、[V20:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L78)、[V20:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L86)、[db.md:966-968](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L966-L968)、[db.md:1282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L1282)
- **整改要求**：`V20` 按 v1.1 改为 `claim_key` + `uk_payroll_run_claim (station_id, claim_key)` + `skip_code`，`RUNNING`/`SUCCESS`/`SKIPPED` 均写 `claim_key`、`FAILED` 置 NULL；`db.md` §8.6.5~§8.6.7 说明同步；**§9.4 U-13 断言重写**为「同一 `(station_id, target_month)` 连续写两条 `RUNNING` 应触发 1062」。
- **验收标准**：`SHOW CREATE TABLE payroll_run` 含 `uk_payroll_run_claim(station_id, claim_key)` 与 `skip_code`；并发两 tick 仅 1 条占位行；第二条 `RUNNING` 的 `INSERT` 报 1062；`U-13` 测试脚本已含该断言。

### M-2【高】全部生成路径纳入 Layer 0（或落可判定巡检）
- **问题**：手工 `generate` 不占 claim，与 tick 并发仅靠 Layer 1 的 `check-then-act`，理论上可产生重复工资单；方案「缓解」仅为后置巡检，属检测非阻断。
- **依据**：[PayrollController.java:60-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L60-L64)、[PayrollServiceImpl.java:155-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L155-L163)、[design:454-456](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L454-L456)
- **整改要求**：① 9405 判定按驿站收敛（方案 M1）；② 手工 `generate` 在目标 `(station_id, month)` 复用同一 claim 占位，使手工与自动共用 Layer 0；③ 实施日巡检 SQL（`COUNT(*) == COUNT(DISTINCT employee_id)` 按 `(month, bill_type)`）。
- **验收标准**：并发生成（手工 `generate` × tick）后，`payroll` 无 `(employee_id, month, bill_type)` 重复；巡检 SQL 返回一致。

### M-3【中】手工调整留痕内容口径收紧（事由必填 + before/after 白名单）
- **问题**：C-3 的 `ITEM_UPDATE` 事由为「可选」，且既有实现会把 `detail` 覆盖为「人工填写」；`before/after` 无字段白名单。
- **依据**：[design:516](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L516)、[PayrollServiceImpl.java:362](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L362)、[design:357-358](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L357-L358)、[design:475](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L475)
- **整改要求**：① 任何**金额变更**（add / update）强制 `reason` 2-200（复用 9412）；② `payroll_log.before/after` 定义白名单：`{itemKey,itemType,itemName,amount}` 变更明细 + `{additionTotal,deductionTotal,grossAmount,netAmount}` 合计；禁止序列化实体/请求上下文/`rule_snapshot`；③ `ITEM_UPDATE` 不得覆盖 `payroll_item.detail` 中的事由全文（或同时保留）。
- **验收标准**：无 `reason` 的 items 更新返回 9412；单测断言 log 的 `before/after` 仅含白名单键，且可由 `before`→`after` 唯一重建本次 `netAmount` 差值与变动项。

### M-4【中】审计链不断裂：覆盖重建期的留痕可追溯
- **问题**：`generate` 物理删除 `DRAFT/REJECTED` 单，R11 只迁移 `payroll_item` 不迁移 `payroll_log` → DRAFT 期 `ITEM_ADD/ITEM_UPDATE` 留痕成为孤儿（`payroll_id` 指向已删单），且操作人只存于 `payroll_log`。
- **依据**：[PayrollServiceImpl.java:612-621](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621)、[V20:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L49)、[design:376](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L376)、[design:467-476](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L467-L476)
- **整改要求**（择一，方案须定稿）：① `payroll_log` 增冗余定位列（`employee_id` + `month`，或 `payroll_no` 快照），使留痕可脱离已删 `payroll_id` 独立检索；② 覆盖重建时把旧单留痕以新 `payroll_id` 续接（含「重建迁移」事件）；③ 或（代价最大）禁止在 `DRAFT` 期录入手工项——与 R11 冲突，需用户/技术评审重新裁定。
- **验收标准**：DRAFT 期录入一笔 `ITEM_ADD` → 执行 `generate` 重建后，仍能按「员工 + 账期」检索到该条留痕及其 `operator_id/reason`，且不经由已删 `payroll_id`。

### M-5【中】`fail_reason` 脱敏可执行化
- **问题**：仅有「截断 ≤500、不落敏感信息」的目标陈述，无可执行口径；`ClientLogSanitizer` 的白名单/凭据正则**不覆盖**身份证/银行卡/金额，不能直接背书。
- **依据**：[V20:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L77)、[algorithm:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L201)、[ClientLogSanitizer.java:48-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L48-L49)、[:62-86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L62-L86)
- **整改要求**：`fail_reason` 由服务端**构造**（异常类名 + 错误码 + 白名单文案），禁止拼接 SQL 参数值 / 业务数据 / 凭据；超长按**字符**安全截断（不截半 JSON）；把 `ClientLogSanitizer.scrub` 作为二次兜底而**非**唯一口径。
- **验收标准**：构造失败场景（异常文本含金额 / 姓名 / 卡号形态），落库 `fail_reason` 不含上述值，且长度 ≤500；单测覆盖截断边界。

### M-6【中】`GET /{id}/logs` 服务端强制裁剪 + 越权判定单一真源
- **问题**：I-7 出参对全角色列出 `before/after`，而 U-14 要求「仅 ADMIN 可见」且落点写「由端裁剪」；「端」若指前端即为越权信息暴露。同时契约未指定越权判定复用既有单一真源，存在 IDOR 风险。
- **依据**：[design:504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L504)、[design:508](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L508)、[design:633](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L633)、[PayrollServiceImpl.java:320-327](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L320-L327)
- **整改要求**：① 越权判定**复用** `detail()` 的单一真源（本人 + 状态 ∈ `{PUBLISHED, CONFIRMED, PAID}`），违规返回 9404 / 9403；② **服务端按角色裁剪 DTO**：非 ADMIN 仅返回 `action/time/reason/toStatus`，**不返回** `before/after/operator_id/operator_role`；③ 契约文字明确「服务端裁剪」，删除「由端裁剪」歧义表述。
- **验收标准**：STAFF 请求**他人** `/{id}/logs` → 9404；STAFF 请求**本人未发布**单 → 9403；以**接口原始响应**（非前端渲染）断言非 ADMIN 响应体**不含** `before/after` 字段。

### M-7【中】通知白名单拆分 + 最小接收人 + 状态落定后触发
- **问题**：`PUBLISH_TYPES` 被公告发布与系统联动共用；把 7/8/9 并入后 ADMIN 可经公告端点仿冒薪资通知。通知服务内无「管理员集合」解析，易误推。
- **依据**：[NotificationServiceImpl.java:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)、[:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L143)、[:245-246](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L245-L246)、[NotificationController.java:74-79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/notification/NotificationController.java#L74-L79)、[design:560-561](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L560-L561)
- **整改要求**：① `sendSystem` 使用**独立** `SYSTEM_TYPES` 白名单放行 7/8/9，**公告 `PUBLISH_TYPES` 保持 1..6 不变**；② 新增 `findAdminEmployeeIds()` 单一真源（`role=ADMIN AND status=1`），类型 7/9 仅推该集合，禁止复用 `resolveTargets`；③ 类型 8 仅在 `publish/republish` 成功且状态已落 `PUBLISHED` 后触发，禁止在 `generate/approve/submit` 分支触发；④ 通知调用一律 `try/catch`，失败只记 `NOTIFY_SKIP` 不阻断。
- **验收标准**：公告端点传 `type=7/8/9` → 拒绝；类型 7/9 收件人集合 ⊆ `{role=ADMIN ∧ status=1}`；未发布状态下**零** type 8 通知；`sendSystem` 抛错时主流程完成且留有 `NOTIFY_SKIP`。

### M-8【中】`PAID` 冻结单一写守卫 + 非预期态显式拒绝
- **问题**：冻结判定分散 5 处，无单一收口；`publish(ids)` 对非 `APPROVED` 静默 skip，掩盖 `PAID` 等非预期态。
- **依据**：[design:239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L239)、[PayrollServiceImpl.java:252-256](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L252-L256)、[:634-641](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L634-L641)、[PayrollStateMachine.java:35-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStateMachine.java#L35-L44)
- **整改要求**：① 新增统一 `assertMutable(payroll, action)`（或把所有写动作统一走 `requireAction`），`PAID` 一律 9413；② `isItemEditable` / `isOverwritable` / `actionsOf` 对 `PAID` 显式取值（`false` / `false` / 空）；③ `publish` 对传入的 `PAID`/`CONFIRMED` 等**非允许来源**返回显式错误码，不得静默计入 `skipped`。
- **验收标准**：单测覆盖「`PAID` 下全部写入口（含新增 `pay`）→ 9413」；`publish` 传入 `PAID` 单返回错误而非仅 `skipped+1`。

### M-9【中】算薪配置变更留痕
- **问题**：I-3（`payroll_day` / `payroll_time` / `enabled` / `notify_enabled`）变更无审计；`payroll_log.payroll_id NOT NULL` 结构上无法承载配置事件。
- **依据**：[design:500](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L500)、[V20:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L49)、[design:195](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L195)
- **整改要求**：I-3 任一变更写入审计（可复用既有系统/操作日志能力，或新增配置审计记录），记录 `(station_id, 字段, 旧值, 新值, operator_id, time)`；**尤其「自动算薪从停用到启用」必须可追溯**。
- **验收标准**：执行一次 I-3（含 `enabled: 0→1`）后，可查询到对应审计记录（含旧值/新值与操作人）。

### M-10【低】迁移执行方式显式化与可核验
- **问题**：`MODIFY COLUMN` / `ADD COLUMN` 未声明算法与锁；写法退化时可能触发表重建；MySQL 版本未举证。
- **依据**：[V20:92-96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L92-L96)、[V20:26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L26)
- **整改要求**：迁移语句显式 `ALGORITHM=INPLACE, LOCK=NONE`（版本支持时）；执行前后 `SHOW CREATE TABLE payroll` 存档并比对；低峰窗口；执行前备份；迁移属 C 档，须三步授权。
- **验收标准**：迁移记录附前后 DDL 差异；执行无长锁告警；回滚脚本可执行且已确认无 `OBJECTED`/`PAID` 存量。

---

## 4. 发现项清单（REG-xx）

| 编号 | 标题 | 分级 | 依据（文件:行号）/ 证据 |
| --- | --- | --- | --- |
| **REG-01** | claim 协议在 `V20` / `db.md` §8.6.7 / §9.4 U-13 三处仍为 v1.0 语义，与设计 v1.1 冲突 → 唯一 DB 硬防线对 `RUNNING` 并发失效，重叠 tick / 多实例可**双执行产生重复工资单**，且 U-13 会把缺陷**判为通过** | **高** | [V20:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L78)、[:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L86)；[db.md:966-968](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L966-L968)；[db.md:1282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L1282)；[design:411-422](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L411-L422) |
| REG-02 | 单一 ADMIN 可无审批完成「改金额→提交→审批→发布→发放」全链路（职责分离缺失；口径已由用户裁定，靠补偿控制收敛） | 中 | [design:498-504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L498-L504)、[design:513-520](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L513-L520)、[PayrollController.java:59-110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L59-L110) |
| REG-03 | `ITEM_UPDATE` 事由可选 + 既有实现把 `detail` 覆盖为「人工填写」→ Q3「每笔必填事由」不可保证 | 中 | [design:516](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L516)、[PayrollServiceImpl.java:362](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L362) |
| REG-04 | `generate` 物理删除 `DRAFT/REJECTED` 单，`payroll_log` 无外键且 R11 不迁移留痕 → DRAFT 期手工项留痕成孤儿，操作人追溯断裂 | 中 | [PayrollServiceImpl.java:612-621](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621)、[V20:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L49)、[design:376](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L376) |
| REG-05 | `payroll_log.before/after` 与 `payroll_run.fail_reason` 缺可执行脱敏口径（无字段白名单；`ClientLogSanitizer` 正则不覆盖身份证/银行卡/金额，不能背书） | 中 | [V20:59-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L59-L60)、[V20:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L77)、[algorithm:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L201)、[ClientLogSanitizer.java:48-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L48-L49) |
| REG-06 | I-7 契约出参对全角色列出 `before/after`，U-14 却要求「仅 ADMIN 可见 / 由端裁剪」→ 若前端隐藏而后端照返即越权信息暴露 | 中 | [design:504](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L504)、[design:508](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L508)、[design:633](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L633) |
| REG-07 | 若把 7/8/9 并入公告 `PUBLISH_TYPES`，ADMIN 可经 `POST /notifications/publish` 伪造「工资单待审核/已发布/异议退回」通知（应用内仿冒/钓鱼） | 中 | [NotificationServiceImpl.java:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)、[:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L143)、[NotificationController.java:74-79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/notification/NotificationController.java#L74-L79) |
| REG-08 | 通知服务内无「管理员集合」解析落点；若复用 `resolveTargets("ALL"/"STATION")` 会误推站长/员工；type 8 无「已发布」硬前置则可能未发布即推员工 | 中 | [NotificationServiceImpl.java:179-208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L179-L208)、[:245-246](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L245-L246)、[design:556-561](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L556-L561) |
| REG-09 | 手工 `generate` 不占 claim，与 tick 并发存在重复工资单理论窗口；缓解仅为后置巡检（检测非阻断） | 中 | [PayrollController.java:60-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L60-L64)、[design:454-456](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L454-L456) |
| REG-10 | 僵死 `RUNNING` 回收仅依据时间；`running-timeout-minutes` 小于最坏批次耗时（方案自认可达十万级 insert）时，正常长任务被判 `FAILED` 释放槽位 → 下一 tick 重复执行 | 中 | [design:422](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L422)、[design:582](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L582)、[algorithm:576](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algorithm-payroll-scheduling.md#L576) |
| REG-11 | `PAID` 冻结守卫分散 5 处、无单一收口；扩展期任一入口漏加即绕过（新增 `pay`、扩展 `isItemEditable` 均在改动面内） | 中 | [PayrollServiceImpl.java:340](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L340)、[:415-420](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L415-L420)、[:440-441](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L440-L441)、[:634-641](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L634-L641)、[design:239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L239) |
| REG-12 | 算薪配置（`enabled`/`payroll_day`/`payroll_time`/`notify_enabled`）变更无留痕，无法追溯「谁启用/改了自动算薪」 | 中 | [design:500](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L500)、[V20:31-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L31-L44) |
| REG-13 | 工资单无删除端点、`is_deleted` 无外部置位路径（**当前成立**；扩展期须保持终态守卫） | 低 | [PayrollController.java:48-125](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L48-L125)、[PayrollServiceImpl.java:604-606](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L604-L606) |
| REG-14 | `publish(ids)` 对非 `APPROVED` 静默 `skipped++`，掩盖 `PAID`/`CONFIRMED` 等非预期态，排障与审计信号弱化 | 低 | [PayrollServiceImpl.java:252-256](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L252-L256) |
| REG-15 | `payroll_log` 追加型仅靠应用层约定，DB 层未限制 `UPDATE`/`DELETE`（建议改历史或被篡改后无技术阻止） | 低 | [V20:47-65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L47-L65)、[db.md:961](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L961) |
| REG-16 | `MODIFY COLUMN` / `ADD COLUMN` 未显式声明算法与锁；MySQL 版本未举证；写法退化可能触发表重建 | 低 | [V20:92-96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L92-L96)、[V20:27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L27) |
| REG-17 | `db.md` §8.6.5 `payroll_day` 注释仍为「建议 1-28」，与方案 U-05 定稿「1-31 并钳位」不一致（契约漂移，可致实现被误约束） | 低 | [db.md:911](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L911)、[design:321](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L321)、[design:624](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L624) |
| REG-18 | `sendSystem` 对非法类型/超长 text 抛异常，与「通知失败只记 `NOTIFY_SKIP`、不阻断」口径需在调用侧兜住，否则 `afterCommit` 阶段异常可能外溢 | 低 | [NotificationServiceImpl.java:184-192](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184-L192)、[design:194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L194) |

**分级统计：高 1 项；中 11 项；低 6 项。** 无「严重」级（无 RCE / 鉴权绕过 / 大规模数据泄露 / 垂直越权）。

**已确认为「合格 / 正向」的控制（须被本需求复用，不得另起口径）**
1. 端点角色门槛 **fail-closed**（[RequireRolesInterceptor.java:41-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L41-L50)）——新增接口即便漏标亦被 403 兜底。
2. 财务域**不在公开白名单**（[PublicEndpoints.java:57-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L57-L60)）。
3. 非 ADMIN 详情访问的**单一真源**已存在（本人 + 可见状态），I-7 只需复用（[PayrollServiceImpl.java:320-327](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L320-L327)）。
4. `deleteExisting` 的**状态白名单**使已提交/已发布/已确认单不被覆盖删除（[PayrollGenerateGuard.java:20-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L20-L22)）。
5. 调度开关**默认关闭**（[V20:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L34)）、调度**仅进程内**、无对外暴露触发面（除 ADMIN 端点）。
6. 交易链路**逐驿站隔离事务** + 通知**提交后触发**（[design:192-194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L192-L194)）。

---

## 5. 结论等级与放行条件

### 5.1 结论等级：**有条件放行**

**理由（一句话）**：本需求**不引入任何权限放宽**（新增接口全 `ADMIN` / 员工本人，[design:540-549](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/payroll-automation-design.md#L540-L549)），既有鉴权基础设施 fail-closed 且财务域非公开；但存在**一处高危**（REG-01：幂等硬防线的三处产物与设计 v1.1 不一致）与**多项中危口径缺口**（留痕内容、服务端裁剪、通知隔离、终态收口、配置留痕），均可在实现前以契约与验收标准收敛，**不构成整体阻断**。

### 5.2 放行范围（明确）

- **允许**：进入 B0 契约补录与 B2/B3/B4 的**开发与测试环境验证**（`enabled=false`，不触生产财务数据）。
- **阻断（不得执行 / 不得启用）**：以下两处**在 M-1 / M-2 闭环前**为硬阻断——
  1. **B1 迁移执行（C 档）**：不得以现版 `V20` 作为执行依据（其 claim 语义与设计 v1.1 不符，会固化失效防线）；
  2. **生产启用自动算薪（`enabled: 0→1`）**：不得在 M-1/M-2/M-8 闭环前启用定时自动写库。

### 5.3 放行前置条件（必须完成）

| 范围 | 前置必做项 |
| --- | --- |
| 报主智能体审批（B0） | **M-1**（claim 三处一致，含 `db.md` 与 U-13）、**M-4**（审计链不断裂方案定稿）、**M-6**（服务端裁剪口径写入契约） |
| B1 迁移执行（C 档授权） | M-1 已完成并经数据库工程师回改 `V20`；M-10（算法/锁显式化 + 前后 DDL 比对 + 备份）；由主智能体按 §10.3 三步授权 |
| 生产启用自动算薪 | M-2、M-8、M-9、M-10；M-3、M-5、M-7 随同批 |
| 上线前（B5 合流） | 全部 M-x 闭环并经**安全复验**（L7 闭环：网络安全工程师复验出结论） |

### 5.4 解除条件（若判定为阻断时适用）

本报告不整体判「阻断」。**若 M-1 与 M-4 未闭环而方案被直接报审或 `V20` 被直接执行，则安全侧改判为「阻断」**，解除条件为：① 上述 §5.3 对应范围前置必做项全部完成；② 提供 `V20` 回改后的 `SHOW CREATE TABLE` 证据（服务器阶段）；③ 复验确认 U-13 断言已重写且并发 `RUNNING` 触发 1062。

---

## 6. 我认为必须在实现前强制落地的控制（不超过 3 条）

1. **幂等硬防线收口**：claim 协议在 `V20` / `db.md` / U-13 三处对齐（`RUNNING` 亦占位），并让**全部生成路径（含手工 `generate`）统一占用同一 claim**；未闭环则**禁止启用自动算薪**。
2. **留痕不可断 + 服务端裁剪**：`ITEM_UPDATE` 事由必填、`before/after` 白名单化到 item 级 delta、`payroll_log` 增冗余定位列避免孤儿；I-7 越权判定复用 `detail()` 单一真源且**服务端**按角色裁剪（非 ADMIN 不返 `before/after`）。
3. **终态单一写守卫 + 通知隔离**：新增统一 `assertMutable`（`PAID` → 9413）覆盖全部写入口；`sendSystem` 用独立 `SYSTEM_TYPES`（7/8/9）且公告白名单维持 1..6；管理员接收人走单一真源，type 8 仅在状态落 `PUBLISHED` 后触发。

---

## 7. 待核实项（本机无 JDK / MySQL，未运行、未连接）

| # | 待核实项 | 影响 | 核实方式（服务器阶段） |
| --- | --- | --- | --- |
| R-1 | `V20` 脚本正确性与索引真实生效 | 决定 Layer 0 是否成立 | 执行后 `SHOW CREATE TABLE station_payroll_setting / payroll_log / payroll_run / payroll` |
| R-2 | 目标 MySQL 版本与 `MODIFY COLUMN` 实际算法 | 决定是否长锁 | `SELECT VERSION()`；`SHOW CREATE TABLE payroll` 前后比对；执行计划确认 `ALGORITHM=INPLACE` |
| R-3 | `payroll` 实际行数 / 单次全量 insert 最坏耗时 | 决定 `running-timeout-minutes` 下限（REG-10） | 生产/预发统计；批插入实测 |
| R-4 | 是否有 actuator 或其他管理端点暴露调度触发面 | 外部触发面（§2.2(1)） | 部署后端点枚举 + 鉴权配置核查 |
| R-5 | 既有 `payroll_item` 是否存在 `source=MANUAL` 存量（影响 R11 迁移与 item_key 冲突） | 覆盖重建正确性与留痕连续性 | `SELECT COUNT(*) FROM payroll_item WHERE source='MANUAL'` |
| R-6 | 客户端上报白名单 `ClientLogItem` 字段集 | 仅用于评估「能否复用 `ClientLogSanitizer` 口径」，本报告已按不适用处理 | 只读源码确认 |
| R-7 | 单管理员实际人数与运营授权矩阵（SoD 补偿控制设计输入） | §2.1(4) 告警对象与对账视图受众 | 运营确认（非技术） |

---

## 8. 升级与复验

- **升级路径（触发即停下回报主智能体）**：① `V20` 未经 M-1 修订被执行；② 生产启用自动算薪前未闭环 M-2/M-8；③ 发现幂等失效导致重复工资单的实例；④ 实现与安全红线冲突（如要求以 root 运行、要求凭据入文档、要求绕过风控）。
- **复验责任**：上述 M-x 闭环后，由网络安全工程师按本报告「验收标准」逐条复验并出具**复验结论**；复验未闭环前不得声称已确认合规。
- **本报告不影响** P0.6 技术评审闸门（工程面归技术评审工程师）；两道闸门**并行不互替**。

---

## 9. 证据索引（本报告引用文件）

- 方案：`d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\docs\payroll-automation-design.md`（v1.1）
- 算法：`d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\docs\algorithm-payroll-scheduling.md`
- 迁移：`d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\resources\db\migration\mysql\V20__payroll_automation.sql`
- 数据规范：`d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\docs\db.md`（§8.6.5~§8.6.7、§9.4 U-13）
- 既有实现：`PayrollServiceImpl.java`、`PayrollController.java`、`NotificationServiceImpl.java`、`NotificationController.java`、`ClientLogSanitizer.java`、`PayrollStateMachine.java`、`PayrollGenerateGuard.java`、`PayrollLockPolicy.java`、`RequireRolesInterceptor.java`、`PublicEndpoints.java`、`V8__payroll.sql`
