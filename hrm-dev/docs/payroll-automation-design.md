# 薪资结算自动化与全链路留痕 · 架构设计方案（含 ADR / 状态机扩展 / 表设计 / 接口契约草案 / 批次）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.5（v1.4 基础 + 复评新增必改 复-1~复-4 闭环：僵死回收默认开启并完整定义 / 删除 `force` / 补跑触发时刻统一 / 清单一致性）** |
| 上一版本 | v1.4（2026-09-27，1034 行，主代理裁定：日粒度防线统一为 DB 唯一键 `uk_attempt`）；v1.3（2026-09-27，995 行，用户裁定 U-05/U-06 落地 + 主代理 M-9 裁定 + 技术评审整改收口）；v1.2（2026-09-27，879 行，技术评审「打回」+ 安全「有条件放行」整改稿）；v1.1（2026-09-27，679 行，技术评审被打回）；v1.0（2026-09-27 初稿，536 行） |
| 编写日期 | 2026-09-27 |
| 状态 | **方案阶段产物（v1.5 整改稿，技术评审复评「有条件通过」）**。依 `项目规则1.md` §8 第 8 条与调度规则 **P0.6 / R25 / L8**，报审前须经技术评审工程师（`express-station-tech-reviewer`）评估通过。**首评结论为「打回」**（`tech-review-payroll-automation.md`，必改 1~10）；**安全评估结论为「有条件放行」**（`security-payroll-automation-review.md`，M-1~M-10）；**复评结论为「有条件通过」**（同报告「复评（方案 v1.4 ｜ 2026-09-27）」章节：首评必改 1~10 已闭环 9 / 部分 1，**新增必改 复-1~复-4**，放行前置 **P1~P6**）。v1.2/v1.3/v1.4 为逐轮整改稿；**本 v1.5 逐条闭环复评新增必改 复-1~复-4（响应表见 §14）**：僵死回收默认开启并完整定义（复-1）、删除 `force` 参数（复-2）、补跑触发时刻口径统一（复-3）、前端与参数清单一致性（复-4）。**本版只改本文档**；算法侧由算法工程师同步为 `[算法 v1.3]`（参数单一真源）。**放行前置 P1~P6 的方案层落点见 §14.2；本方案仍不宣称已通过评审，亦不构成任何 C 档动作授权。** |
| 产出角色 | 架构师 `express-station-architect`（只出设计；不写实现代码、不写迁移 SQL、不执行 git/部署/MCP） |
| 修订输入（v1.1） | ① [algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md) v1.0（四件套，§7 修正建议 12 条）；② `V20__payroll_automation.sql`（数据库工程师按 v1.0 §3 产出）；③ [db.md](db.md) §8.6.5~§8.6.7 与 §9.1 存疑项 Q-DB-7/8/9；④ 主代理预裁定 U-01~U-15 |
| 修订输入（v1.2） | ① [tech-review-payroll-automation.md](tech-review-payroll-automation.md)（结论「打回」，必改 1~10）；② [security-payroll-automation-review.md](security-payroll-automation-review.md)（结论「有条件放行」，M-1~M-10、REG-01~REG-18）；③ [algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md) v1.1 §11（W1/W2、D1~D6）；④ 数据库工程师已完成的 v1.1 数据层同步（`V20`/`db.md`/`init.sql`） |
| 修订输入（v1.3） | ① **用户裁定 U-05（算薪日 1..31，月末钳位）/ U-06（日粒度重试：次日补跑、失败次日再补跑、每日至多一次）**；② **主代理对安全 M-9 的裁定**（新增配置变更审计表 `station_payroll_setting_log`，并入 `V21`）；③ 主代理对「加扣款金额方向」的解释（待用户最终确认）。**本版未改算法文档 / 迁移脚本 / `db.md` / 代码**；算法侧参数口径以 `[算法 v1.2]` 为准（**v1.5：算法侧已按复评 P1 同步为 `[算法 v1.3]`，第 12 行「`[算法 v1.2]`」为当期历史记录**） |
| 修订输入（v1.4） | **主代理裁定**：统一本文档与算法四件套 v1.2 对「同一驿站同一账期每自然日至多尝试一次」的防线表述——采纳算法的 **DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`**（与既有 `uk_payroll_run_claim` **正交共存**）；应用层 `existsSuccess`/「当日已尝试」短路**仅作查询优化**、不作为唯一防线。**本版只改本文档**，不改算法文档 / 迁移脚本 / `db.md` / 代码；数据层 `V21`（含 `uk_attempt`）由数据库工程师按同一裁定**并行**编写 |
| 修订输入（v1.5） | ① **技术评审复评报告**（`tech-review-payroll-automation.md`「复评（方案 v1.4 ｜ 2026-09-27）」：§七 新发现必改 **复-1~复-4**、§三 独立验算（3.2 卡死场景 / 3.3 `force` 无差异 / 3.4 补跑时刻分歧）、§7.2 放行前置 **P1~P6**）；② **主代理统一裁定**（复-1 回收默认 `true` + 回收动作完整定义 + 单实例安全说明并登记场景；复-2 删除 `force`、同日重复一律 `9410`；复-3 `catch-up-time-of-day` 为当日触发时刻、`target_month` 恒为 `dueAt` 所在月；复-4 前端补 3 处 staff 侧点位、参数清单以 `[算法 v1.3]` 为单一真源）。**本版只改本文档**，不改算法文档 / 迁移脚本 / `db.md` / 任何代码文件；**接口数量不变**（既有 10 + 新增 9 + 变更 7） |
| 上游需求口径 | **用户已裁定**（需求口径 Q1~Q9 见 §0.3；**本次新增业务口径裁定 U-05/U-06 见 §13**）；本方案只做设计，不改口径 |
| 关联文档（真源） | [db.md](db.md)（表结构规范真源，§8.6 计薪段）、[api.md](api.md)（接口契约真源，§4.0 概览 / 第 2 章错误码）、[registration-design.md](registration-design.md)（本文体例参照）、[plan.md](plan.md)、[server-architecture.md](server-architecture.md)（§6.2 P6） |
| 本方案边界 | 只产出设计；**不含**业务实现代码、**不含** Flyway 脚本正文、不执行部署/MCP/git。**v1.4 未修改任何代码文件与迁移脚本**；**v1.5 同（只改本文档，未改算法/迁移/`db.md`/代码）**。数据层（`V20__payroll_automation.sql` / `db.md` / `init.sql`）的 **v1.1 同步已由数据库工程师完成**（见 §6「数据层状态」）；本版新增的 `payroll_log` 冗余定位列、`payroll_run.attempt_date` 列 + **唯一键 `uk_attempt`（v1.4，日粒度硬防线）**、配置审计表 `station_payroll_setting_log` 均以**新迁移 `V21`** 承接（M-2② + U-06 + 主代理 v1.4 裁定 + M-9 裁定，见 §3.2/§3.3/§3.6） |

### v1.5 变更摘要

> 来源：**技术评审复评**（`tech-review-payroll-automation.md`「复评（方案 v1.4 ｜ 2026-09-27）」）**新增必改 复-1~复-4** + **主代理统一裁定**。**本版只改本文档**；算法侧由算法工程师按同一复评同步为 `[算法 v1.3]`（参数单一真源），数据层三方对读（P2）由数据库工程师保证。逐条响应表见 **§14**。

| # | 来源 | 变更点 | 落点 |
| --- | --- | --- | --- |
| 1 | 复评 复-1 + 主代理裁定 | `stale-reclaim-enabled` 默认值定 **`true`**（纠正 v1.4 的 `false`，与算法一致）；`running-timeout-minutes` 默认 **30**；**回收动作完整定义**（置 `claim_key=NULL`、`status=FAILED`、`fail_reason=STALE_RECLAIMED`、`finish_time`，**释放占位、允许次日重试**）并**必须触发失败告警**；补**单实例下回收安全论证**（`fixedDelay` 不重叠 → 无并发执行 → 超时即真僵死）；**登记该场景与处置** | §3.3、§5、§7 T4 |
| 2 | 复评 复-2 + 主代理裁定 | **删除 `force` 参数**：I-4 入参移除；**同日重复触发一律回 `9410`**（由 `uk_attempt` 硬拒绝）；清除全文 `force` 表述并标注作废（`9414` 已于 v1.3 作废） | §1 ADR-01、§3.3、§4.1 I-4、§7 T5、§10、§13.2/§13.5 |
| 3 | 复评 复-3 + 主代理裁定 | 补跑**触发时刻口径统一**为 **`catch-up-time-of-day`（默认继承该站 `payroll_time`）**；判定 = `now ≥ 当日 catch-up-time-of-day` **且** `(station_id, target_month)` **当日尚未尝试**；`target_month` 恒为 `dueAt` 所在月（纠正 v1.4「`now ≥ dueAt`」会在次日 00:0x 触发的分歧） | §3.3、§5、§13.2 |
| 4 | 复评 复-4 + 主代理裁定 | §2.8 **补入 3 处 staff 侧点位**（`staff-h5` 的 `staff/payroll.vue:93`、`MyPayrollCard.vue:49`、`constants/todoGroups.js:21`，均非 NPE）；参数清单**改以 `[算法 v1.3]` 为单一真源**、正文只保留架构相关项 | §2.8、§5 |
| 5 | 复评 P1~P6 + 主代理 | 新增 **§14 对复评新必改项 复-1~复-4 的逐条响应表**；复核放行前置 P1~P6 的方案层落点（P2/P4/P5/P6 归口非架构师） | §14 |
| 6 | 连带同步 | 头部版本 → v1.5；**接口总数口径复核**（**删 `force` 不改接口数量**，仍为「既有 10 + 新增 9 + 变更 7」）；§9 结论摘要、§10 必改 3 前端计数同步 | 全文、§9、§10 |

### v1.4 变更摘要

> 来源：**主代理裁定**（统一本文档与算法四件套 v1.2 对同一机制的表述，采纳算法的 DB 唯一键方案）。**本版只改本文档**，不改算法文档 / 迁移脚本 / `db.md` / 任何代码文件；数据层 `V21`（含 `uk_attempt`）由数据库工程师按同一裁定**并行**编写。

| # | 来源 | 变更点 | 落点 |
| --- | --- | --- | --- |
| 1 | 主代理裁定 | 「同一驿站同一账期**每自然日至多尝试一次**」的**硬防线**由「应用层判定」升格为 **DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`**（跨实例兜底）；应用层 `existsSuccess`/「当日已尝试」短路**降级为查询优化**，不再作为唯一防线 | §3.3、§5、§6、§8、§9、§13 |
| 2 | 主代理裁定 | `attempt_date DATE NOT NULL` 为**每行恒有值、永不置 NULL**；`uk_attempt`（管「日内一次」）与 `uk_payroll_run_claim`（管「跨日终态占位」）**正交共存**、互不冲突（各管什么 / 何时写值 / 为何不冲突，详见 §3.3） | §3.3、§6、§13 |
| 3 | 主代理裁定 | **索引取舍（最小冗余）**：`uk_attempt` 与 `idx_payroll_run_station_month` 列完全相同 → **删除冗余的 `idx_payroll_run_station_month`**，由 `uk_attempt` 覆盖其查询前缀（运行历史 / 连续失败天数统计）；`V21` 由「扩索引」改为 **`DROP INDEX` + `ADD UNIQUE KEY uk_attempt`** | §3.3、§6、§13 |
| 4 | 连带同步 | 头部版本 → v1.4；**全文不再出现「日粒度闸门为应用层判定」旧表述**；接口清单 / U-05 / U-06 / M-9 表设计 / 批次划分**均不变**（既有 10 + 新增 9 + 变更 7） | 全文 |

### v1.3 变更摘要

> 来源：① **用户裁定 U-05/U-06**（业务口径，`项目规则1.md` §11.4）；② **主代理对安全 M-9 的裁定**（新增算薪配置变更审计表）；③ 主代理对「加扣款金额方向」的解释（待用户最终确认）。**本版只改本文档**，不改算法文档 / 迁移脚本 / `db.md` / 任何代码文件。旧口径作废清单见 **§13**。

| # | 来源 | 变更点 | 落点 |
| --- | --- | --- | --- |
| 1 | 用户裁定 **U-05** | `payroll_day` 定稿 **1..31**，当月无该日**钳位到当月最后一天**；「待用户裁定」改「**已裁定（裁定方=用户）**」 | §3.1、§4.1 I-3、§5、§7、§9、§13 |
| 2 | 用户裁定 **U-06** | **替换原 72h 补跑窗口**：算薪日当天未成功 → **次日补跑**，失败 → **次日继续补跑**，自然日粒度持续重试至成功；**同一驿站同一账期每自然日至多尝试一次**；`target_month` 恒为 `dueAt` 所在月；允许**告警/可配熔断补偿**但**不得加硬上限** | §3.3（重写）、§4.1 I-4、§5、§6、§7、§9、§13 |
| 3 | U-06 派生（数据支撑） | `payroll_run` 新增 **`attempt_date DATE NOT NULL`**（日粒度闸门依据）+ 索引末位追加；`skip_code` **移除 `EXHAUSTED`**；`9414`/`9416` 作废；`force` 语义改为「跳过日粒度闸门」。**（v1.4：闸门升格为 DB 唯一键 `uk_attempt`，并删除冗余索引 `idx_payroll_run_station_month`；`force` 收敛为「仅跳过应用层短路、不可绕过该唯一键」——见 v1.4 摘要与 §13.5）** | §3.3、§4.3、§5、§6 |
| 4 | 主代理 **M-9 裁定** | 新增追加型审计表 **`station_payroll_setting_log`**（与 `payroll_log` 同构、无 `is_deleted`/`update_time`）；I-3 **每次保存必写一条**；**启用 0→1 可追溯**；新增 **I-9** 查询接口；并入 **`V21`** | §3.6、§3.1、§4.1 I-9、§6、§8、§9、§11 |
| 5 | 主代理解释（加扣款方向） | 加款计入应发、扣款计入扣项、实发=应发−扣项（沿用既有 `PayrollTotalsPolicy`，不改公式）；**自动参与金额计算并重算四项合计**；待用户最终确认 | §3.4、§4.1 I-6、§7 U-16、§13 |
| 6 | 接口计数口径更新 | **新增 9 个**（I-1~I-9，新增 `I-9` 配置变更历史）+ **变更 7 项**（C-1~C-7） | §6 B0、§9、§10 |
| 7 | 新增章节 | **§13 对用户 U-05/U-06 裁定的落地响应**（含旧口径作废清单） | §13 |

### v1.2 变更摘要

> 整改来源：① [tech-review-payroll-automation.md](tech-review-payroll-automation.md)（结论「打回」，必改 **1/3/5/6/7/9/10** 退回本架构师）；② [security-payroll-automation-review.md](security-payroll-automation-review.md)（结论「有条件放行」，M-1~M-10 中属方案层部分）；③ [algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md) v1.1 §11 衍生风险 **D1~D6**。**本版只改本文档**，不改算法文档 / 迁移脚本 / `db.md` / 任何代码文件。

| # | 来源 | 变更点 | 落点 |
| --- | --- | --- | --- |
| 1 | 必改 1（高） | **补 `pay` 端点** `POST /api/v1/finance/payrolls/{id}/pay`（ADMIN；`CONFIRMED→PAID`）；来源非 `CONFIRMED` 回 **9403**、`PAID` 冻结回 **9413**；动作矩阵逐个动作 ↔ 端点闭合 | §4.1 I-8、§2.2、§6、§9、§10 |
| 2 | 必改 3（高） | **消除无源结论**：撤销「规则项 key 不使用 `MANUAL_` 前缀（`V8:38`）」的伪既有事实表述，改为「**本方案新增的建设性约定**」并给理由；全文同类自查 | §3.4、§10 |
| 3 | 必改 5（中） | §6 数据层提示行改为「**已完成项 + 仍需回改项**」准确状态；不再声称数据层为 v1.0 | §6、§8、§9 |
| 4 | 必改 6（中） | 动作矩阵与接口闭合核对；「**新增 8 + 变更 7**」口径修正与逐条可数 | §4.1、§4.2、§6、§9、§10 |
| 5 | 必改 7（中） | **收敛可观测性过度声明**：`payroll_run` 只可复现「**已进入执行**」的归因；「未启用/未到点/超窗口/已占位」不落表，列**已知观测盲区** | §5、§10 |
| 6 | 必改 9（中） | **U-05 / U-06 撤回「主代理预裁定」，改标「待用户裁定」**，各给 2 候选口径与影响，不预设结论。**（v1.3 更新：已获用户裁定，落点见 §13）** | §3.1、§5、§7、§9、§10 |
| 7 | 必改 10（中） | **前端影响面清单补全**（NPE 点 / mock 镜像 / 门禁断言 / 待办字典），含独立搜索新发现 | §2.8、§0.2⑨、§10 |
| 8 | 安全 M-1/REG-01（高） | claim 协议在**方案 / 数据层（已完成）/ U-13 验收断言**三处口径一致；**全部生成路径（含手工 `generate`）必须占用同一 claim** 定为「生产启用自动算薪」硬前置 | §3.3、§6、§11 |
| 9 | 安全 M-2（高） | ① I-7 出参**服务端**按角色裁剪并复用 `detail()` 越权单一真源；②**裁定 `payroll_log` 加冗余定位列**（`employee_id`+`month`，另立 `V21`）；③ `fail_reason`/`before`/`after` 脱敏与字段白名单可执行化 | §3.2、§3.3、§4.1 I-7、§11 |
| 10 | 安全 M-3（中） | **统一终态写守卫 `assertMutable(payroll)`**（`PAID`→9413）覆盖全部写入口（含 `pay`、`generate` 覆盖重建、`deleteExisting`） | §2.9、§11 |
| 11 | 安全通知隔离（中） | `sendSystem` 独立 `SYSTEM_TYPES` 放行 7/8/9，**公告白名单维持 1..6**；type 8 仅状态落 `PUBLISHED` 后触发；管理员接收人走**单一真源** | §4.5、§11 |
| 12 | 算法 D1（结构裁定） | **裁定不加 `owner_instance_id` / `heartbeat_at` 列**（单实例 + `fixedDelay` 不重叠 + 含 `DRAFT` 保护性 `SKIPPED` + `stale-reclaim-enabled=false` 兜底）；多实例 / 僵死误判登记架构债。**（v1.5 更正：该兜底默认值改为 `true`（回收安全、次日自愈），见 §3.3/§14 复-1）** | §3.3、§7、§10 |
| 13 | 新增两节 | ①**技术评审 10 条必改逐条响应表**；②**安全评估 M-1~M-10 落地表** | §10、§11 |

### v1.1 变更摘要

> 逐条响应下游产物（算法四件套 12 条修正 + 数据库 3 条存疑项）与主代理预裁定；标「V20 修订项」者须数据库工程师回改脚本。

| # | 来源 | 变更点 | 落点章节 |
| --- | --- | --- | --- |
| 1 | 算法 R1（必改） | 撤销 v1.0「单据级幂等靠 `idx_payroll_emp_month_bill`」断言（该索引为**普通 KEY**）；**不加 DB 唯一约束**，明确降级为「应用层 9405 + `payroll_run` claim」双层，单据级幂等缺口写入风险登记 | §0.2⑪、§3.3、§5、§8 |
| 2 | 算法 R2（必改） | `payroll_run` 由「仅 SUCCESS 写哨兵」改为 **claim 槽位**：`success_key` → `claim_key`，唯一键改名 `uk_payroll_run_claim`；`RUNNING`/`SUCCESS`/`SKIPPED` 占位、`FAILED` 置 NULL 释放（**V20 修订项**） | §3.3、§5、§6 |
| 3 | 算法 R3（必改） | 重申 v1.0 已认 M1：9405 阻断判定**按驿站收敛**（多驿站自动算薪可用性前提） | §0.2⑥⑦、§4.2 C-7 |
| 4 | 算法 R4（必改） | `payroll_day` 定稿 **1..31**，`dueAt` 于月末**钳位到当月最后一天**（不采 1..28）（**V20 修订项**：注释文案）。**（v1.3：该口径经用户 U-05 裁定确认，见 §13）** | §3.1、§5、§4.3 |
| 5 | 算法 R5（必改） | 消解「窗口 `dueAt+W`」与「不跨月」矛盾：**窗口裁剪到 `min(dueAt+W, 次月首日 00:00)`**，自动路径只评估 `now` 所在月。**（v1.3 作废：窗口口径整体被 U-06 日粒度重试模型取代，见 §13）** | §3.3、§5 |
| 6 | 算法 R11（必改） | 采纳**甲**：`generate` 覆盖重建时**保留既有 `source=MANUAL` 明细**（迁移到新单、重算合计）；撤销 v1.0「手工项须在 `DRAFT/REJECTED` 之外录入」约束 | §0.2⑫、§2.2、§3.4、§4.2 |
| 7 | 算法 R6（建议） | 采纳：新增失败重试上限与**僵死 `RUNNING` 回收**（超时置 `FAILED` 释放槽位）（**V20 修订项**：新增 `skip_code`）。**（v1.3：重试上限部分作废——U-06 定「持续重试不设硬上限」，仅保留僵死回收；见 §13）** | §3.3、§5、§4.3 |
| 8 | 算法 R7（建议） | 采纳：调度链路落库时间统一 `ZonedDateTime.now(zone).toLocalDateTime()`；人工链路沿用 `LocalDateTime.now()` 并以部署前置 JVM TZ 兜底 | §1 ADR-01、§5 |
| 9 | 算法 R8（建议） | 采纳：`trigger_type` 判定基准定死为 `(now - dueAt) ≤ tickInterval ? AUTO : CATCH_UP` | §3.3、§5 |
| 10 | 算法 R9（建议） | 采纳：「未启用 / 自动路径超窗口」**不产生运行记录**；`SKIPPED` 仅用于「已决定执行但被业务正常拦截」 | §5 |
| 11 | 算法 R10（建议） | **部分采纳**：扫描加 `station.status=1`（采纳）；**不新增 `enabled` 索引**（0/1 低选择性，与「索引克制」一致） | §3.1、§5 |
| 12 | 算法 R12（待裁定） | **部分采纳**：本期维持「全局第一个启用规则」，按驿站差异化规则属 schema 变更，登记 `TODO(扩展)` | §5、§7 |
| 13 | 数据库 Q-DB-7 | 裁定：`uk_payroll_run_claim` 为 D7 的**显式例外成立**（多实例 DB 硬兜底），与 Q-DB-5 同类 | §3.3、§8、§0.7 |
| 14 | 数据库 Q-DB-8 | 裁定：统一为**末尾追加顺序** `…/CONFIRMED/OBJECTED/PAID`；§2.1 表仅为生命周期概念分组，非枚举序 | §2.1、§2.7、§0.7 |
| 15 | 数据库 Q-DB-9 | 裁定：维持「运行记录只增不删、无删除入口」前提；唯一索引不过滤 `is_deleted` 属**有意为之**（占位不可复用） | §3.3、§0.7 |
| 16 | 主代理预裁定 U-01~U-15 | 15 项全部落定（裁定方：主代理预裁定）。**（v1.2 更正：U-05/U-06 已撤回预裁定、改标「待用户裁定」，见 §7）** | §7 |
| 17 | 主代理实测（R1/R11 证据） | 新增两条事实性纠正：`idx_payroll_emp_month_bill` 为非唯一 KEY；`generate` 物理删除重建致 DRAFT 期 MANUAL 项丢失 | §0.2⑪⑫ |

***

## 0. 取证方式与已核实事实

### 0.1 取证方式

只读代码、迁移脚本与文档，未运行任何构建/测试（本机无 JDK/MySQL/Redis，见 `TASK.md` 纪律 2）。全部结论标注 `文件:行号` 出处；口径项一律引 §7（**v1.3：U-05/U-06 已由用户裁定，其余落定**），未落实的架构债引 §7 T1~T5。

### 0.2 事实性纠正（任务描述 / 既有材料 与实测出入）

| # | 任务描述 / 既有表述 | 实测事实 | 出处 |
| --- | --- | --- | --- |
| ① | 任务称「payroll 六态状态机」 | **成立**：枚举值 `DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED`，label 为「草稿 / 待审核 / 已通过 / 已驳回 / 已发布 / 已确认」 | [PayrollStatus.java:12-19](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStatus.java#L12-L19) |
| ② | 任务称「`PUT /{id}/items` 只能改已有 MANUAL 项，无法新增一笔」 | **成立**：`updateItems` 对 `item_key` 未命中直接抛 400「工资单项不存在」；命中但 `source≠MANUAL` 抛 400「由规则计算，不可手工修改」 | [PayrollServiceImpl.java:349-364](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L349-L364) |
| ③ | 任务称「`objection` 回 `PENDING_APPROVAL`，但该状态 `isEditable=false`，与『退回后可改再发布』相矛盾」 | **成立且为必须解决的设计缺口**：`isEditable` 仅 `DRAFT / REJECTED`；`objection` 落 `PENDING_APPROVAL`，该状态不在可编辑集 | [PayrollStateMachine.java:60-62](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStateMachine.java#L60-L62)、[PayrollServiceImpl.java:443-455](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L443-L455) |
| ④ | 任务称「完全没有任何定时调度能力」 | **成立**：全仓 `@EnableScheduling` / `@Scheduled` / quartz / xxl-job 零命中；启动类仅 `@SpringBootApplication` | [HrmServerApplication.java:9](../hrm-server/src/main/java/com/qiujie/HrmServerApplication.java#L9) |
| ⑤ | 任务称「通知类型放行 1~6」 | **成立**：`PUBLISH_TYPES = Set.of(1,2,3,4,5,6)`，`sendSystem` 亦走该白名单校验 | [NotificationServiceImpl.java:44](../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)、[:184](../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184) |
| ⑥ | 任务称「索引含 `idx_payroll_emp_month_bill`（生成幂等）」 | **成立，但生成幂等当前是「账期级」而非「驿站级」**：`generate` 的阻断判定查全月 `MONTHLY` 单（**不带 stationId**），任一非可编辑单即整批 9405。**多驿站独立算薪会被此拦截**（见 §0.2 ⑦） | [PayrollServiceImpl.java:154-163](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L154-L163)、[PayrollGenerateGuard.java:33-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L33-L52) |
| ⑦ | **（本次新增发现，必改项）** 逐驿站自动算薪（Q4）与现有生成幂等冲突 | 现有 9405 判定为**全局账期级**：先跑驿站 A 并提交/发布后，驿站 B 同月 `generate` 会被 A 的既有单阻断。逐驿站调度要求幂等判定**按驿站收敛**，否则多驿站场景下自动算薪只能成功第一个站点 | 同 ⑥；[PayrollGenerateGuard.java:14-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L14-L52) |
| ⑧ | 任务称「前端仅 ADMIN 端（boss-h5）承载算薪日设置」 | **成立**：boss-h5 端准入 fail-closed，仅 `ADMIN`；boss 端已有 `finance.js`（工资单只读同名域）、`modules/boss/views/` 已有 `payroll.vue`、`leaveSettings.vue` 等设置页先例 | [boss-h5/src/api/finance.js](../hrm-clients/apps/boss-h5/src/api/finance.js)、[boss-h5/src/modules/boss/views/](../hrm-clients/apps/boss-h5/src/modules/boss/views/) |
| ⑨ | **（本次新增发现）** 前端 `PAYROLL_STATUS` 新增状态须同步**共享字典单源 + mock 镜像**，且**多处视图硬编码状态集合/分支**（不改则新状态详情/步骤条错乱、`PAYROLL_STATUS[status].variant` 取 `undefined.variant` 抛错） | 单源：`shared/src/constants/dict.js`（label/type/variant，被 18 个文件引用）；mock 镜像：`mock/src/financeStore.js`（`PAYROLL_STATUS_LABEL` + `PAYROLL_ACTIONS`）；硬编码点：`web/components/PayrollStatusSteps.vue`（步骤条固定 5 步）、`boss-h5/modules/boss/views/payrollDetail.vue`（`objected = objectionReason && status==='PENDING_APPROVAL'`）、`staff-h5/views/staff/payrollDetail.vue`、`PayrollDetailDrawer.vue:66`（可编辑集 `['DRAFT','REJECTED']`）、`GeneratePayrollDialog.vue:23`、`usePayrollActions.js:89`。**注**：`dict.js` 注释里的「三处镜像」指的是 **LEAVE 计薪天数常量**，**非** `PAYROLL_STATUS`，勿混用。**v1.2 补全：本行原清单不完整**（经技术评审实测与独立搜索，遗漏 7 类点位及 5 处独立新发现），补全后的完整清单见 **§2.8** | [dict.js:151-158](../hrm-clients/packages/shared/src/constants/dict.js#L151-L158)、[dict.js:274](../hrm-clients/packages/shared/src/constants/dict.js#L274)、[financeStore.js:37-59](../hrm-clients/packages/mock/src/financeStore.js#L37-L59)、[PayrollStatusSteps.vue:21-34](../hrm-clients/apps/web/src/components/PayrollStatusSteps.vue#L21-L34)、[boss payrollDetail.vue:36-38](../hrm-clients/apps/boss-h5/src/modules/boss/views/payrollDetail.vue#L36-L38)、[PayrollDetailDrawer.vue:66](../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue#L66) |
| ⑩ | **（本次新增发现）** 前端已预留 `PAID` 计数键 | `usePayrollList.spec.js` 用例已构造 `counts: { DRAFT: 3, REJECTED: 2, APPROVED: 4, PAID: 1 }`，说明终态命名 `PAID` 与既有前端预期一致 | [usePayrollList.spec.js:43](../hrm-clients/apps/web/src/views/finance/composables/usePayrollList.spec.js#L43) |
| ⑪ | **（v1.1 新增发现，实测）** v1.0 §3.3/§5 称「单据级幂等靠 `idx_payroll_emp_month_bill`」 | **不成立**：该索引声明为普通 `KEY idx_payroll_emp_month_bill (employee_id, month, bill_type)`，**非 `UNIQUE KEY`**，不阻止重复插入 → 「单据级 DB 幂等」在当前 schema 下不存在（响应算法 R1，见 §3.3 风险登记） | [V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85) |
| ⑫ | **（v1.1 新增发现，实测）** `generate` 对同员工同月同类型旧单**物理删除再重建** | **成立**：`deleteExisting(...)` → `createPayroll(...)` 逐员工「先删后建」（[PayrollServiceImpl.java:173-175](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L173-L175)）；`deleteExisting` 仅清 `DRAFT/REJECTED` 范围（[PayrollServiceImpl.java:601-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621)）。**后果**：`DRAFT/REJECTED` 期录入的 `source=MANUAL` 手工项会被下次 `generate` **静默清除**（响应算法 R11，见 §3.4） | 同左 |

### 0.3 用户已裁定的需求口径（不改，只映射）

| 口径 | 内容 | 本方案落点 |
| --- | --- | --- |
| Q1 | 手工加款/扣款**一次性**，只记当月工资单（不做员工级长期项） | §3.4（复用 `payroll_item`，`source=MANUAL`，不建长期项表） |
| Q2 | 管理员操作**无需审批**，直接落库归档 | §2.2（`item-add` 无审批前置）、§4.1 I-6 |
| Q3 | 每笔手工加扣款**必填事由**并**记录操作人** | §3.2（`payroll_log.reason` + `operator_*`）、§4.1 I-6（`reason` 必填） |
| Q4 | 算薪日**每个驿站各自配置** | §3.1（新增 `station_payroll_setting`，一驿一条） |
| Q5 | 设置在**驿站精灵（boss-h5，仅 ADMIN）** | §4.4（新接口全 ADMIN，与端准入一致）、§6 批次 B3 |
| Q6 | 自动算薪生成后**先推管理员审核**，**审核时可改并留痕**，改后可审核发布 | **§2.10（自动生成即自动提交落 `PENDING_APPROVAL`）**、§2.3（拆分「可编辑」判据）、§2.2、§4.5 |
| Q7 | 需**错过补跑** | §3.3（`payroll_run` + **日粒度重试模型（U-06 已裁定）**）、§5、§13 |
| Q8 | 只推**管理员**；管理员审核通过并**发布后**才推员工 | §4.5（通知接收人单一真源 / type 8 落定后触发）、§4.1、§7（已裁定 U-07） |
| Q9 | 发布后**不可删除**；仅员工异议可退回处理并**再次发布**；员工确认后管理员确认「工资已发放」**归档**；**每一步留痕可追溯** | §2.2、§2.5、§2.6、§2.9、§3.2、**§4.1 I-8（`pay` 归档端点）** |

### 0.4 既有可复用资产（本方案落点，均为实测）

| 资产 | 关键事实 | 出处 |
| --- | --- | --- |
| 状态机集中真源 | 动作矩阵收在 `PayrollStateMachine.ACTIONS`；`isEditable` 单一判据 | [PayrollStateMachine.java:30-62](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStateMachine.java#L30-L62) |
| 追加型留痕先例 | `leave_log`（只增不改：无 `is_deleted`/`update_time`，`before/after` JSON，`action/operator_*/from_status/to_status/remark`），含 `NOTIFY_SKIP` 排障留痕 | [V9__leave.sql:57-72](../hrm-server/src/main/resources/db/migration/mysql/V9__leave.sql#L57-L72)、[LeaveServiceImpl.java:658-690](../hrm-server/src/main/java/com/qiujie/service/leave/impl/LeaveServiceImpl.java#L658-L690) |
| 单行全局开关先例 | `leave_setting`（单行、无 `is_deleted`） | [V9__leave.sql:74-81](../hrm-server/src/main/resources/db/migration/mysql/V9__leave.sql#L74-L81) |
| **一驿一条配置先例** | `attendance_rule`：`station_id` NOT NULL + **活跃唯一由 Service 查重**（不建 DB 唯一索引，决策 D7）+ 普通索引 `idx_attendance_rule_station` | [V5__attendance.sql:10-47](../hrm-server/src/main/resources/db/migration/mysql/V5__attendance.sql#L10-L47) |
| 一对一系统通知出口 | `NotificationService.sendSystem(employeeId, type, title, content, bizType, bizId)`：`is_published=0`、无发布人、带业务跳转 | [NotificationService.java:50](../hrm-server/src/main/java/com/qiujie/service/notification/NotificationService.java#L50) |
| 通知开关先例 | 请假域以 `algoProperties.getLeave().isNotifyEnabled()` 控制投递 + 目标缺失写 `NOTIFY_SKIP` | [LeaveServiceImpl.java:658-668](../hrm-server/src/main/java/com/qiujie/service/leave/impl/LeaveServiceImpl.java#L658-L668) |
| 算薪规则驱动内核 | 金额由 `PayrollResolverRegistry` 按来源解析，`PayrollTotalsPolicy` 统一合计；核心不含计薪项专属公式 | [PayrollServiceImpl.java:69-82](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L69-L82) |
| 物理删除仅限可覆盖态 | `deleteExisting` 仅清 `DRAFT/REJECTED`（`PayrollGenerateGuard.editableStatuses()`），已提交/已发布不取不删 | [PayrollServiceImpl.java:601-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621) |
| 账期锁策略 | `PayrollLockPolicy.isLocked = !isEditable`（非 `DRAFT/REJECTED` 即「已出账」） | [PayrollLockPolicy.java:28-30](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollLockPolicy.java#L28-L30) |
| 时区事实 | 数据源 URL 已固定 `serverTimezone=Asia/Shanghai`；无 JVM 时区强制项 | [application.yml:25](../hrm-server/src/main/resources/application.yml#L25) |
| 错误码段位 | 94xx 财务段，已用到 `9405`；段位不重叠 | [ErrorCode.java:222-233](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L222-L233) |
| 迁移现状 | MySQL 目录实测至 **`V19`**；PostgreSQL 目录自 `V2` 起冻结；快照唯一同步点 `sql/schema/mysql/init.sql` | [migration/mysql/](../hrm-server/src/main/resources/db/migration/mysql/)、[registration-design.md:61](registration-design.md) |
| 端准入 | boss-h5 仅 `ADMIN`（fail-closed）；`@RequireRoles` 取值须编译期字面量 | [RoleEnum.java:12-28](../hrm-server/src/main/java/com/qiujie/enums/RoleEnum.java#L12-L28)、[PayrollController.java:41-51](../hrm-server/src/main/java/com/qiujie/controller/finance/PayrollController.java#L41-L51) |

### 0.5 本方案边界与升级路径

1. **只设计不实现**：不写 Controller/Service/Mapper/Vue、不写迁移 SQL 正文；表设计以文档形式给出（§3）。
2. **P0.6 / L8 技术评审闸门**：本方案为方案阶段产物，须先过技术评审工程师评估，结论「通过 / 有条件通过」方可报主智能体审批。
3. **P0.5 安全面并行**：本方案**不涉及任何权限放宽**（新接口均为 ADMIN 或员工本人，见 §4.4）；且**新引入进程内定时调度与自动写库**（§1），属生产变更面，故同步登记为需网络安全工程师评估项（见 §8）。
4. **口径类裁定状态（v1.3 更新）**：**U-05（算薪日范围）与 U-06（补跑模型）已由用户裁定**（属业务口径，`项目规则1.md` §11.4）：U-05 = `payroll_day` **1..31** + 月末钳位；U-06 = **日粒度重试模型**（次日补跑、失败次日再补跑、每自然日至多一次）。落点与旧口径作废清单见 **§13**；U-01~U-04、U-07~U-15 维持落定；**U-16（加扣款金额方向）为主代理解释、待用户最终确认**（§3.4/§7）。**新增**的按驿站差异化计薪规则（算法 R12）属 schema 面，登记 `TODO(扩展)` 待架构评估 + 用户裁定，不在本方案内拍板。
5. **只设计不落改**：本方案对既有行为（状态机 / `objection` 语义 / `generate` 幂等 / `generate` 覆盖重建）的判断，均**不自行落改代码或迁移脚本**；数据层**已完成**的 v1.1 同步与**仍需回改**项分别见 §6「数据层状态」①②。

### 0.6 对算法四件套 12 条修正的逐条响应表

> 输入：[algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md) v1.0 §7「对上游方案的修正建议」（必改 6：R1/R2/R3/R4/R5/R11；建议 5：R6/R7/R8/R9/R10；待裁定 1：R12）。逐条给结论、落点与依据，**不留悬空**。

| 修正项 | 级别 | 结论 | 落点 | 依据 |
| --- | --- | --- | --- | --- |
| **R1** 单据级幂等断言不成立 | 必改 | **采纳** | §0.2⑪、§3.3、§8（风险登记） | 实测 `idx_payroll_emp_month_bill` 为普通 `KEY`（[V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85)）。采纳「明示单据级无 DB 幂等」主张；**是否另立唯一键**由本方案裁定为**不加**（理由见 §3.3 风险登记：存量查重前置 + 与 Q9「发布后不可删除」潜在冲突 + claim 唯一键已提供 DB 硬线） |
| **R2** `RUNNING` 无并发防线 | 必改 | **采纳** | §3.3、§5、§6 | claim 槽位协议在 **INSERT 一条 SQL** 上以 InnoDB 唯一约束原子拒绝并发，最简最可靠（算法 §1.5）。最终列设计见 §3.3；`V20` 需同步修订 |
| **R3** 9405 账期全局级 | 必改 | **采纳** | §0.2⑥⑦、§4.2 C-7 | 与 v1.0 自认 M1 同源，重申：**不改则逐驿站自动算薪只能成功第一个站点**（[PayrollServiceImpl.java:154-163](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L154-L163)、[PayrollGenerateGuard.java:33-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L33-L52)） |
| **R4** `dueAt` 月末无定义 | 必改 | **采纳（钳位口径 A）；v1.3 经用户 U-05 确认** | §3.1、§5、§4.3（9407） | 钳位保证「每月恒定有且仅有一个 `dueAt`」，无静默漏跑；1..28 会使「月末结算」（`payroll_day=31`）不可配，不符 Q4「每驿站自定义」。**裁定方（v1.3）：用户（U-05）** |
| **R5** 窗口与不跨月自相矛盾 | 必改 | **原采纳（选项 b）；v1.3 作废** | §3.3、§5、§13 | v1.3 经用户 U-06 裁定，**窗口口径整体作废**，改为**日粒度重试模型**（次日补跑、失败次日再补跑；账期恒为 `dueAt` 所在月，仅执行日推后）。原「跨月一律手工触发」不再适用 |
| **R11** DRAFT 期 MANUAL 项被静默清除 | 必改 | **采纳（甲）** | §0.2⑫、§2.2、§3.4、§4.2 | 采纳主代理倾向：`generate` 覆盖重建**保留既有 `source=MANUAL` 明细**。「记到当月工资里」的语义不应被规则重算抹掉；实现约束（识别 / 排序 / key 冲突 / 留痕）见 §3.4 |
| **R6** 无重试上限 / 无僵死回收 | 建议 | **部分采纳（v1.3 修订）** | §3.3、§5 | **僵死 `RUNNING` 回收：采纳**（`running-timeout-minutes` 外置）；**「重试上限」：作废** —— 用户 U-06 定「以自然日为粒度持续重试直到成功、不得加硬上限」，故移除 `max-retry-per-run`，改为**每日一次 + 连续失败告警 / 可配熔断补偿**（默认仅告警）。`9416` 作废 |
| **R7** 落库时间与判定时区错位 | 建议 | **采纳** | §1 ADR-01、§5 | 调度链路（系统身份）落库统一 `ZonedDateTime.now(zone).toLocalDateTime()`；人工链路沿用 `LocalDateTime.now()`，以部署前置 `-Duser.timezone=Asia/Shanghai` 兜底，保证全链路单一时区源 |
| **R8** `trigger_type` 判定基准未定义 | 建议 | **采纳** | §3.3、§5 | 定死 `(now - dueAt) ≤ tickInterval ? AUTO : CATCH_UP`；仅影响可观测性标签，不影响执行语义 |
| **R9** 「未启用 / 超窗口」误列 `SKIPPED` | 建议 | **采纳** | §5 | 「未进入执行」不产生运行记录，避免污染运行历史（I-5 查询）；`SKIPPED` 仅用于「已决定执行但被业务正常拦截」。**（v1.3：`SKIPPED` 集合移除 `EXHAUSTED`）** |
| **R10** 扫描未含 `station.status` / `enabled` 无索引 | 建议 | **部分采纳** | §3.1、§5 | **采纳**扫描条件加 `station.status=1`（停用驿站不自动算薪，修正语义）；**不采纳**新增 `KEY idx_station_payroll_setting_enabled(enabled)`——`enabled` 仅 0/1，基数极低，MySQL 大概率仍全表扫描；表规模 = 驿站数（数十~数百），全表扫描可接受，且与「索引克制」原则一致 |
| **R12** 自动调度未定义 `ruleId` 来源 | 待裁定 | **部分采纳** | §5、§7 | 本期维持现状口径：不传 `ruleId` 时取「第一个启用规则」（[PayrollServiceImpl.java:556-563](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L556-L563)），不改 schema；「按驿站差异化规则」属结构变更（`station_payroll_setting` 增 `rule_id` 或规则加驿站维），登记 `TODO(扩展)` 交架构评估 + 用户裁定 |

**采纳统计（v1.3 更新）**：R1、R2、R3、R4、R7、R8、R9、R11 **采纳 8 条**；R6、R10、R12 **部分采纳 3 条**；**R5 经用户 U-06 裁定作废 1 条**；不采纳 0 条。

### 0.7 对数据库 3 条存疑项的裁定

> 输入：[db.md](db.md) §9.1 Q-DB-7/8/9 与 `V20__payroll_automation.sql`。逐条裁定并给最终口径。

| # | 存疑项 | 裁定 | 理由与约束 | 落点 |
| --- | --- | --- | --- | --- |
| **Q-DB-7** | `payroll_run` 建唯一键是对决策 D7（不建 DB 唯一索引）的例外 | **例外成立，予以确认** | ① 「重叠 tick / 未来多实例」的互斥属**跨请求原子性**，应用层 `check-then-act` 有竞态（[PayrollGenerateGuard.java:33-52](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L33-L52) 为应用层判定），须 DB 硬约束兜底；② 与 Q-DB-5 `auth_trusted_device` 唯一键同类先例；③ 与 D7 关切不冲突——D7 反对的是「逻辑删除后唯一键阻止复用」，而 `payroll_run` 无删除入口（见 Q-DB-9）。**保留唯一键**（v1.1 改名为 `uk_payroll_run_claim`） | §3.3、§8 |
| **Q-DB-8** | `payroll.status` 八态枚举顺序（§2.1 表 vs §2.7.2） | **统一为「末尾追加顺序」** | 最终枚举顺序：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED / OBJECTED / PAID`。§2.1 表按**生命周期概念分组**展示（`OBJECTED` 置于 `PUBLISHED` 与 `CONFIRMED` 之间），**非**枚举序，须标注；`PayrollStatus.values()` 即此序（[PayrollStatus.java:12-19](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStatus.java#L12-L19)），`labels()`/`counts` 键序随之（[PayrollStatus.java:31-38](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStatus.java#L31-L38)、[PayrollServiceImpl.java:293-298](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L293-L298)）。`V20` 的 `status` COMMENT 已是此序，**无需修订** | §2.1、§2.7 |
| **Q-DB-9** | `payroll_run` 唯一索引不过滤 `is_deleted` | **维持「只增不删、无删除入口」前提** | 约束：① 不提供任何 `payroll_run` 删除端点；② `is_deleted` 仅为结构一致性兜底，业务永不置位；③ 被逻辑删除的 `SUCCESS` 行仍占键位、阻止再次写 `SUCCESS`，在本业务下**属有意为之**（防止重复算薪）。**不改**生成列式部分唯一（无产品需求 + 增复杂度）。若未来确需「删除后重跑」，须改生成列式部分唯一并另立版本，登记 `TODO(扩展)` | §3.3、§6 |

***

## 1. ADR：调度选型

### ADR-01 · 定时调度的落地方式

**状态**：提议（待技术评审 + 主智能体审批）
**日期**：2026-09-27

**上下文**

- 需求 Q4/Q6/Q7/Q8 要求：按**每驿站各自配置**的算薪日与时间，自动生成工资单草稿、推管理员审核、错过可补跑。
- 部署形态（实测）：后端为宿主**裸 `java -jar` 进程**，**无 systemd、无部署脚本、非 git 仓库**，日志 `/data/log/hrm/app.log`，当前**单实例**。
- 代码现状（实测）：全仓**零调度能力**；启动类无 `@EnableScheduling`。
- 硬约束：服务器**禁止编辑业务源码**（D 档）；进程重启/宕机后必须能自愈补跑。

**决策**

采用 **方案 A：进程内 Spring `@Scheduled` 定时轮询 + 手工触发端点兜底**，不引入宿主 crontab，不引入 Quartz/xxl-job。

具体形态：

1. 启动类新增 `@EnableScheduling`（仅此一处基础设施开关）。
2. 新增 `PayrollScheduleTicker`：**固定间隔轮询**（`@Scheduled(fixedDelayString = "${hrm.payroll.schedule.tick-interval-ms:600000}")`，默认 10 分钟），每轮执行「到点判定 → 逐驿站触发 → 记录运行结果」。
3. **用轮询而非「每天算薪日一个 cron」**：因为算薪日/时间**逐驿站不同**，无法用单条 cron 表达；且轮询天然实现「宕机恢复后 ≤1 个 tick 内补上当日错过的执行」，无需额外补跑机制即覆盖 Q7 的轻度场景（跨日补跑见 §3.3 **日粒度重试模型（U-06 定稿）**）。
4. **手工触发端点** `POST /api/v1/finance/payroll-runs/trigger`（ADMIN）为**运维兜底**：多驿站集中补跑时由管理员显式触发。**（v1.5 复-2：删除 `force` 参数）** 触发受**日粒度闸门**与**占位**双重约束——`(s,m)` 当日已尝试由 `uk_attempt` 唯一键硬拒绝、已占位由 `uk_payroll_run_claim` 硬拒绝，**同日重复触发一律回 `9410`**；本端点**不提供**「同日再试 / 强制重跑已成功账期」能力。

**理由（为何 A 优于 B / C）**

| 维度 | A 进程内 `@Scheduled`（采纳） | B 宿主 crontab（否决） | C Quartz / xxl-job（否决） |
| --- | --- | --- | --- |
| 新增基础设施 | 无（仅一个注解 + 一个 Bean） | 需在服务器手工维护 crontab 条目 | 需调度组件（Quartz 表 11 张 / xxl-job 需独立调度中心） |
| 代码版本化 | 调度逻辑随代码入库、可 Review、可单测 | 服务器状态**不可版本化**，与「服务器禁写源码」纪律冲突 | 入库但引入重组件 |
| 可观测性 | 运行结果落 `payroll_run`（§3.3）+ 应用日志 | 只能靠 `cron` 邮件/系统日志，不可查 | 自带面板，但成本高 |
| 事务/通知耦合 | 与业务同进程，直接注入 `PayrollService` / `NotificationService`，事务边界可控 | 需经 HTTP 或 CLI 回调，引入网络与鉴权复杂度 | 同 A 但组件更重 |
| 单实例前提 | 完全适配 | 适配 | 过度设计 |
| 失败模式 | 见下表 | 进程未启时 cron 仍触发但无进程可调 → 静默失败 | 同 A |

**失败模式与取舍（单实例前提）**

| 失败模式 | 现象 | 本方案处置 |
| --- | --- | --- |
| 进程重启 | 错过重启窗口内的 tick | 轮询自愈：重启后首个 tick 判定 `now ≥ 当日 catch-up-time-of-day`（算薪日当天即 `dueAt`，补跑日为该站当日钟点，v1.5 复-3）且无成功记录即补跑 |
| 进程宕机 >1 个 tick | 错过执行 | 同「重启」；按**日粒度重试模型**（§3.3）自愈补跑（每个自然日至多一次，直至成功），并在连续失败达阈值时告警管理员 |
| **多实例**（当前不成立，但须防守） | 两个实例同 tick 双双触发 → 重复算薪 | **双重幂等**：① `payroll_run` **claim 槽位唯一键**（`uk_payroll_run_claim`，§3.3）——`RUNNING` 即占位，第二个执行者 INSERT 即 DuplicateKey 短路，DB 层天然跨实例互斥；② 生成侧 9405 幂等（需按驿站收敛，见 §0.2 ⑦）。**任一实例重复触发都不会产生重复单据**。前置声明：本方案**不实现分布式锁**，以 claim 唯一键兜底「不产生重复单据」；若后续水平扩容，须先补「实例选主 / 分布式锁」解决「同刻双跑的资源浪费」并重评（登记为架构债，见 §7 U-12） |
| 单驿站算薪抛异常 | 可能中断整轮 | **逐驿站 try/catch 隔离**：失败仅写该驿站 `payroll_run=FAILED`，不影响其它驿站（§2.2、§5） |
| 该账期已生成 | 重复触发 | 生成侧 9405 → 运行记录记为 `SKIPPED`（非失败），跳过原因回填单据号 |

**时区口径（关键，必须钉死）**

- **权威时区：`Asia/Shanghai`（UTC+8）**，与数据源连接串 `serverTimezone=Asia/Shanghai` 一致（[application.yml:25](../hrm-server/src/main/resources/application.yml#L25)）。
- 判定**不依赖 JVM 默认时区**：新增配置 `hrm.payroll.schedule.zone`（默认 `Asia/Shanghai`），到点判定一律用 `ZonedDateTime.now(ZoneId.of(zone))`。**落库时间（v1.1 修订，响应算法 R7）**：调度链路（系统身份，`payroll_run.due_at/start_time/finish_time`、`payroll_log.time`）统一写 `ZonedDateTime.now(ZoneId.of(zone)).toLocalDateTime()`，保证判定与落库**同一时区源**；人工链路（`approve_time`/`publish_time` 等）沿用既有 `LocalDateTime.now()`，以**部署前置 `-Duser.timezone=Asia/Shanghai`** 保证两者一致（若该前置缺失，人工链路时间会与调度链路时区错位，部署须校验）。
- **「算薪日几点」定义**：以 `Asia/Shanghai` 墙钟时间计的「当月 `payroll_day` 日的 `payroll_time`（HH:mm）」为应执行时刻 `dueAt`。例：`payroll_day=5, payroll_time=09:00` → `dueAt = 当月 5 日 09:00 (UTC+8)`。
- 前置校验项（部署运维）：确认宿主 OS 与 JVM 时区；如需显式固定，启动参数加 `-Duser.timezone=Asia/Shanghai`（**属部署操作，C/B 档，由主智能体按 §10 分档授权**）。

**与业务代码事务、通知能力的耦合方式**

- **不跨驿站共享事务**：ticker 本身**不加事务**；每个驿站一个**独立事务**边界（`PayrollRunService.execute(stationId, month)` 标注 `@Transactional(rollbackFor = Exception.class)`），保证单驿站要么整体成功、要么整体回滚，互不污染。
- **复用既有事务方法**：`PayrollRunService` 内部直接调用现有 `PayrollService.generate(request)`（已在 `@Transactional` 内，[PayrollServiceImpl.java:142-186](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L142-L186)）；**不新建第二套算薪逻辑**。
- **通知在事务提交后触发**：写 `payroll_run=SUCCESS` 与调用 `NotificationService.sendSystem(...)`。为避免「单据回滚但通知已发」，通知调用放在**驿站事务提交之后**（`TransactionSynchronization.afterCommit` 或 ticker 循环内、事务方法返回后），并沿用请假域「通知目标缺失写留痕不阻断主流程」的口径（[LeaveServiceImpl.java:658-668](../hrm-server/src/main/java/com/qiujie/service/leave/impl/LeaveServiceImpl.java#L658-L668)）。
- **调度开关默认关闭**：`hrm.payroll.schedule.enabled`（默认 `false`）。部署后由管理员在 boss-h5 配置算薪日并显式启用，避免上线即自动跑数。
- **系统身份**：调度以 `SYSTEM` 身份执行（无 `UserContext`），故自动算薪生成的草稿 `approver_*`/`publisher_*` 为空、`payroll_log.operator_type=SYSTEM`；**不冒用任何管理员身份**。

**后果**

- 正面：零新基础设施；逻辑版本化、可单测；补跑内建；与既有事务/通知天然同进程耦合。
- 负面 / 风险：进程内调度**只在进程存活时有效**（进程整体宕机期间不触发，靠重启自愈 + 手工兜底）；单实例前提一旦打破需补分布式锁；轮询间隔内精度为 tick 粒度（默认 10 分钟，可配）。
- 备选方案：B 宿主 crontab、C Quartz/xxl-job、D 外部消息队列延迟任务 —— 均因「新增不可版本化的服务器状态 / 过度设计 / 无 MQ 基础设施」否决。

***

## 2. 状态机扩展设计（Q6/Q8/Q9）

### 2.1 目标状态集合

在既有六态基础上**新增两态**（枚举追加，既有六态名称与语义不变）：

| 状态 | label | 含义 | 是否终态 | 明细可编辑 | 可否被生成覆盖 | 可否删除 |
| --- | --- | --- | --- | --- | --- | --- |
| `DRAFT` | 草稿 | 生成/覆盖重建期 | 否 | 是 | 是 | 是（物理重建范围） |
| `PENDING_APPROVAL` | 待审核 | 已提交，审批中 | 否 | **是（Q6 新增）** | 否 | 否 |
| `APPROVED` | 已通过 | 审批通过待发布 | 否 | 否 | 否 | 否 |
| `REJECTED` | 已驳回 | 管理员审批驳回 | 否 | 是 | 是 | 是（物理重建范围） |
| `PUBLISHED` | 已发布 | 已发给员工，待本人确认 | 否 | 否 | 否 | **否（Q9 铁律）** |
| `OBJECTED` | **异议退回**（新增） | 员工提异议，退回管理员处理 | 否 | **是** | 否 | **否** |
| `CONFIRMED` | 已确认 | 员工已确认 | 否 | 否 | 否 | **否** |
| `PAID` | **已发放**（新增） | 管理员确认工资已发放，**归档冻结** | **是（终态）** | 否 | 否 | **否** |

> **枚举顺序定稿（v1.1，Q-DB-8）**：本表为**生命周期概念分组**（把 `OBJECTED` 置于 `PUBLISHED` 与 `CONFIRMED` 之间有助理解），**非**枚举序。**权威枚举顺序 = 末尾追加**：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED / OBJECTED / PAID`，以 `PayrollStatus.values()` 为准（[PayrollStatus.java:12-19](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStatus.java#L12-L19)），`labels()` 与 `counts` 键序随之（§2.7.2）。
> 命名与 label 已定稿（U-01）：`PAID` / 「已发放」（与前端既有测试键一致，见 §0.2 ⑩），**不另设 `ARCHIVED`**；`OBJECTED` / 「异议退回」。

### 2.2 完整动作矩阵（每个状态允许哪些动作 / 前置条件 / 副作用）

**动作清单**：`submit`（提交审核）、`approve`/`reject`（审批）、`publish`（发布/再发布）、`confirm`（员工确认）、`objection`（员工异议）、`pay`（管理员确认发放）、`item-add`（新增一笔手工加/扣款）、`item-update`（改已有 MANUAL 项金额）。

| 当前状态 | 允许动作 | 前置条件 | 副作用（含留痕） |
| --- | --- | --- | --- |
| `DRAFT` | `submit`、`item-add`、`item-update`、（被 `generate` 覆盖） | item-add/update 需 `reason` 非空（Q3）；**被覆盖时既有 `source=MANUAL` 明细保留（R11）** | submit → `PENDING_APPROVAL`，清 `approve_remark`；每次改动写 `payroll_log`（action/operator/before/after/reason）；`generate` 覆盖重建时迁移保留 MANUAL 项（§3.4） |
| `PENDING_APPROVAL` | `approve`、`reject`、**`item-add`、`item-update`（Q6 新增）** | item-add/update 需 `reason` 非空 | approve → `APPROVED`（记 `approver_*`/`approve_time`）；reject → `REJECTED`（记 `approve_remark`）；改动金额后**重算合计**并写 `payroll_log`（`before={netAmount,...}`、`after={netAmount,...}`） |
| `APPROVED` | `publish` | — | → `PUBLISHED`，记 `publisher_*`/`publish_time`；写 `payroll_log`（action=PUBLISH） |
| `REJECTED` | `submit`、`item-add`、`item-update`、（被 `generate` 覆盖） | 同 DRAFT；**被覆盖时既有 `source=MANUAL` 明细保留（R11）** | 同 DRAFT 的 submit；改动写 `payroll_log`；`generate` 覆盖重建时迁移保留 MANUAL 项（§3.4） |
| `PUBLISHED` | `confirm`（员工本人）、`objection`（员工本人） | `confirm`/`objection` 均须本人且已发布 | `confirm` → `CONFIRMED`（记 `confirm_time`）；`objection`（`reason` 必填 2-200）→ **`OBJECTED`**（记 `objection_reason`/`objection_time`，清 `confirm_time`/`publish_time`/`publisher_*`）；两者均写 `payroll_log` |
| `OBJECTED` | `item-add`、`item-update`、`publish`（**再发布**）、`submit`（可选走二次审批） | 再发布前须 `reason` 非空（处理说明） | `publish` → `PUBLISHED`（记**新的** `publisher_*`/`publish_time`，清 `objection_reason`/`objection_time`）；异议历史永久留在 `payroll_log`；写 `payroll_log`（action=REPUBLISH） |
| `CONFIRMED` | `pay`（**端点 I-8**，§4.1） | 仅 ADMIN（Q9）；前置 `status=CONFIRMED`（来源非法回 `9403`） | → `PAID`（记 `paid_by_id`/`paid_by_name`/`paid_time`）；写 `payroll_log`（action=PAY）；进入 `PAID` 后受 §2.9 统一守卫冻结 |
| `PAID` | **无（终态冻结）** | — | 任何改动/删除一律拒绝（**9413**，统一由 **§2.9 `assertMutable`** 收口） |

> **再发布是否需二次审批（已裁定 U-04）**：退回粒度 = **单员工单据级**；`OBJECTED → PUBLISHED` **直发（再发布）为默认路径**，同时**保留 `submit` 作为可选路径**（供需要二次审批的组织口径）。裁定方：主代理预裁定（§7）。
> **自动路径的状态落点（v1.2，响应技术评审必改 5）**：自动算薪 `generate` 后**自动 `submit`**、落 `PENDING_APPROVAL`（**非** `DRAFT`），通知 7 与之一致；详见 **§2.10**。

### 2.3 核心解法：拆分「可编辑」判据（同时解 Q6 与 Q9 的异议矛盾）

**问题**：现有单一判据 `PayrollStateMachine.isEditable` 同时承担三件事——① 能否改 MANUAL 项金额；② 能否被 `generate` 覆盖重建；③ 账期是否被锁（`PayrollLockPolicy.isLocked = !isEditable`）。用户 Q6 要求「**审核时可改**」、Q9 要求「**异议退回后可改**」，而这两态（`PENDING_APPROVAL`、`OBJECTED`）若直接塞进 `isEditable`，会让 **`generate` 覆盖重建范围**一并扩大，从而「把老板正在审的工资单悄悄改掉」——正是 `PayrollGenerateGuard` 注释明令避免的（[PayrollGenerateGuard.java:10-12](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L10-L12)）。

**解法（本方案采纳）**：把「可编辑」**一分为三**，各自独立真源：

| 判据 | 语义 | 状态集合 | 消费方 |
| --- | --- | --- | --- |
| `isItemEditable(status)` | 能否改/加明细金额 | `DRAFT`、`REJECTED`、**`PENDING_APPROVAL`**、**`OBJECTED`** | `updateItems`、`item-add` |
| `isOverwritable(status)` | 能否被 `generate` 物理覆盖重建 | `DRAFT`、`REJECTED`（**不变**） | `PayrollGenerateGuard.editableStatuses()`、`deleteExisting` |
| （账期锁）`isMonthLocked` | 该账期是否已出账 | `!isOverwritable(status)` | `PayrollLockPolicy`（**改绑 `isOverwritable`，行为零突变**） |

- 原 `isEditable` 保留并**重定义为 `isOverwritable`**（或保留 `isEditable` 作 `isOverwritable` 的别名），以**兼容既有调用点**、保证 `deleteExisting`（[PayrollServiceImpl.java:612-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621)）与 `PayrollLockPolicy` 行为**零突变**。
- 新增 `isItemEditable`，供 `updateItems` 与 `item-add` 使用（[PayrollServiceImpl.java:340-343](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L340-L343) 处改用新判据）。
- 该拆分是纯逻辑改动，**无状态数据迁移**，且使「审核中修改」「异议退回修改」两处矛盾一并消解。

### 2.4 「员工异议」与「员工审核不通过」是否同一口径

用户原文：**「仅当员工审核不通过（异议）才可退回管理员处理」** —— 括号已把「员工审核不通过」等同「异议」，故本方案**认定二者为同一业务含义**，统一落 `OBJECTED`。

辨析（避免与既有两个易混概念混淆）：

| 概念 | 触发者 | 结果状态 | 说明 |
| --- | --- | --- | --- |
| **员工异议**（=用户所称「员工审核不通过」） | **员工本人** | → `OBJECTED` | Q9 唯一允许的退回路径 |
| 管理员驳回 | 管理员 | → `REJECTED` | 审批阶段内部驳回，员工从未见过该单 |
| 员工未确认 | — | 停留在 `PUBLISHED` | 不构成退回，不阻断归档前置（但未 `CONFIRMED` 不能 `pay`） |

> 是否要把 `REJECTED`（管理员驳回）也并入 `OBJECTED` 单一退回态（已裁定 U-02）：**不合并**（触发动作者与语义不同，合并会让「谁退的」不可区分）。裁定方：主代理预裁定（§7）。

### 2.5 「已发放/已归档」终态（Q9）

- **名称（已裁定 U-01）**：`PAID`，label「已发放」；**语义 = 归档**（终态即归档态，**不另设 `ARCHIVED`**；理由：`PAID` 已唯一标识「已发放且不可再改」，再加一层状态只会让计数键与前端字典膨胀）。裁定方：主代理预裁定（§7）。
- **冻结程度（已裁定 U-03：绝对冻结）**：`PAID` 下的任何写操作（改明细 / 审批 / 发布 / 再异议 / **`is_deleted` 置位** / 金额与状态变更）一律拒绝 9413；**`is_deleted` 禁止置位**。
- **落库字段**：`payroll` 新增 `paid_by_id`（BIGINT NULL）、`paid_by_name`（VARCHAR(50) NULL）、`paid_time`（DATETIME NULL）——当前态展示用；事件详情入 `payroll_log`。
- **进入条件**：仅 `CONFIRMED` 可 `pay`；前置要求员工已确认（`confirm_time` 非空）；端点 **I-8**（§4.1）。
- **误发放冲正（v1.2）**：`PAID` 绝对冻结下**本期无冲正出口**，登记为受限项 `TODO(扩展)`（§7 **T5**），不沉默（响应技术评审必改 6）；冻结的强制收口见 §2.9 `assertMutable`。

### 2.6 发布后不可删除（Q9）

| 删除形态 | 现状（实测） | 本方案口径 |
| --- | --- | --- |
| 逻辑删除（`is_deleted=1`） | 无对外删除端点；`is_deleted` 由 MyBatis-Plus `@TableLogic` 全局管理 | **禁止**对 `PUBLISHED`/`OBJECTED`/`CONFIRMED`/`PAID` 置位；新增守卫（状态检查）显式拦截 |
| 物理删除 | 仅 `deleteExisting` 清 `DRAFT`/`REJECTED`（[PayrollServiceImpl.java:612-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L612-L621)） | **不变**：仅 `DRAFT`/`REJECTED` 可被重建覆盖；发布及之后一律不取不删 |
| 生成覆盖 | `PayrollGenerateGuard` 阻断非可覆盖态整批 9405 | 保留，并按驿站收敛（§0.2 ⑦ 必改项） |

> **不删除 ≠ 不可编辑**：`OBJECTED` 允许改明细（Q9「处理后可再次发布」的前提），但**不允许删除**。二者是不同维度，勿混淆。

### 2.7 存量六态兼容映射 与 计数键序影响

1. **存量数据零迁移（已裁定 U-13：不回填）**：既有六态名称与语义全部保留；历史上发生过异议、现处 `PENDING_APPROVAL` 且 `objection_reason` 非空的单据，**保持原状**（仍可经 `approve`/`reject` 处理并最终发布），**不回填** `OBJECTED`（零风险，老单走老路径）。裁定方：主代理预裁定（§7）。
2. **`PayrollStatus.labels()` 键序**：该方法按 `values()` 顺序生成 `LinkedHashMap`（[PayrollStatus.java:31-38](../hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollStatus.java#L31-L38)），且 `list()` 的 `counts` 亦按 `values()` 顺序构造（[PayrollServiceImpl.java:293-298](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L293-L298)）。**新增 `OBJECTED`、`PAID` 追加在枚举末尾** → 既有 6 个键的**相对顺序与名称不变**，`counts` 仅在尾部多 2 个键。**最终枚举顺序（Q-DB-8 定稿）**：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED / OBJECTED / PAID`；§2.1 表仅为概念分组，`V20` 的 `status` COMMENT 已与此序一致（[V20__payroll_automation.sql:96](../hrm-server/src/main/resources/db/migration/mysql/V20__payroll_automation.sql#L96)）。
3. **前端消费方式（已实测）**：web 端 `PayrollTabPanel.vue` 以 `v-for key in PAYROLL_STATUS` **动态渲染**状态筛选与计数徽标（[PayrollTabPanel.vue:84,109-116](../hrm-clients/apps/web/src/views/finance/components/PayrollTabPanel.vue#L84-L116)）；但 `PayrollDetailDrawer.vue:116` 直接取 `PAYROLL_STATUS[status].variant`，**若新状态未加入前端字典则 NPE**；且 `PayrollStatusSteps.vue:21-25` 步骤条为**固定 5 步**、`boss-h5`/`staff-h5` 的 `payrollDetail.vue` 存在**硬编码状态分支**（如 boss 端 `objected = objectionReason && status==='PENDING_APPROVAL'`）。→ **强制**：新增两态必须**同步共享字典单源 + mock 镜像**并**逐处更新硬编码视图**（§0.2 ⑨），且**前端字典须先于/同批于后端返回新状态**（批次排序见 §6）。
4. **`EMPLOYEE_VISIBLE_STATUS`**：员工可见集现为 `PUBLISHED/CONFIRMED`（[PayrollServiceImpl.java:86-87](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L86-L87)）。`PAID` 为员工已确认后的归档态，**应加入可见集**（否则员工确认后单据从列表消失，反直觉）；`OBJECTED` 为内部处理态，**不加入**员工可见集（员工在退回期间看到的是「处理中」，由前端另行文案）。**已裁定 U-08**：`PAID` 加入、`OBJECTED` 不加入（裁定方：主代理预裁定，§7）。

### 2.8 前端影响面清单（v1.2 补全 / v1.5 增补 staff 侧；响应必改 10 + 复评 复-4）

> v1.1 清单（§0.2⑨）**不完整**：技术评审实测 + 复评独立搜索（关键词 `PENDING_APPROVAL|PUBLISHED|CONFIRMED|PAYROLL_STATUS|editableStatus`）共补齐 **25 处**点位（v1.2 补 22 处；**v1.5 复-4 再补 3 处 staff 侧，见第 23~25 行**）。**共性风险**：字典未先行时 `PAYROLL_STATUS[status].variant` 取 `undefined.variant` 抛 **NPE**；mock 可见/可编辑集与后端漂移；门禁断言失败。**强制顺序**：字典 + mock 镜像 + 硬编码视图**先于/同批于**后端返回新状态。

| # | 类别 | 文件:行号 | 现状 | 改动要求 |
| --- | --- | --- | --- | --- |
| 1 | 字典单源 | [dict.js:151-158](../hrm-clients/packages/shared/src/constants/dict.js#L151-L158) | `PAYROLL_STATUS` 6 态（label/type/variant），被 18 文件引用 | 追加 `OBJECTED`/`PAID`（含 label/type/variant）；**须最早落地** |
| 2 | 筛选字典 | [dict.js:302-309](../hrm-clients/packages/shared/src/constants/dict.js#L302-L309) | `PAYROLL_FILTERS` 固定 6 项（处理动线） | 追加 `OBJECTED`/`PAID` 筛选项，按处理动线定位 |
| 3 | 标签配色 | [StatusTag.vue:89-96](../hrm-clients/packages/shared/src/ui/StatusTag.vue#L89-L96) | `PAYROLL_STATUS` 专用配色表仅 6 态 | 新态可回落 `TYPE_FALLBACK`（**无 NPE**）；建议补配色以保持一致 |
| 4 | **NPE 现场①** | [PayrollDetailDrawer.vue:116](../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue#L116) | `PAYROLL_STATUS[detail.status].variant` 直接取 `.variant` | 字典未含新态即 NPE；须同批改 |
| 5 | **NPE 现场②** | [PayrollDetailTable.vue:99](../hrm-clients/apps/web/src/views/finance/components/PayrollDetailTable.vue#L99) | `PAYROLL_STATUS[row.status].variant`（**技术评审实测新增，v1.1 遗漏**） | 同上 |
| 6 | **NPE 现场③** | [PayrollObjectionsPanel.vue:40](../hrm-clients/apps/web/src/views/finance/components/PayrollObjectionsPanel.vue#L40) | 同款 NPE（**v1.1 遗漏**） | 同上 |
| 7 | **NPE 现场④** | [employee/detail/index.vue:116](../hrm-clients/apps/web/src/views/employee/detail/index.vue#L116) | 同款 NPE（**v1.1 遗漏**） | 同上 |
| 8 | 可编辑集 | [PayrollDetailDrawer.vue:66](../hrm-clients/apps/web/src/views/finance/components/PayrollDetailDrawer.vue#L66) | 可编辑集 `['DRAFT','REJECTED']` | 改镜像新 `isItemEditable`（含 `PENDING_APPROVAL`/`OBJECTED`） |
| 9 | 步骤条 | [PayrollStatusSteps.vue:20-26,69-72](../hrm-clients/apps/web/src/components/PayrollStatusSteps.vue#L20-L26) | 固定 5 步；`REJECTED`/`hasObjection` 分支 | 增 `OBJECTED`/`PAID` 步骤或明确不纳入；异议分支改判 `status==='OBJECTED'` |
| 10 | mock 字典/动作 | [financeStore.js:37-59](../hrm-clients/packages/mock/src/financeStore.js#L37-L59) | `PAYROLL_STATUS_LABEL` 6 态 + `PAYROLL_ACTIONS` | 追加两态与动作；`PENDING_APPROVAL` 动作集含 `item-add/update`（Q6） |
| 11 | mock 可见集 | [financeStore.js:48](../hrm-clients/packages/mock/src/financeStore.js#L48) | `EMPLOYEE_VISIBLE_STATUS=['PUBLISHED','CONFIRMED']` | **加 `PAID`**（U-08；**v1.1 遗漏**） |
| 12 | mock 可编辑集 | [financeStore.js:50](../hrm-clients/packages/mock/src/financeStore.js#L50) | `EDITABLE_STATUS=['DRAFT','REJECTED']` | 拆为「明细可编辑」与「可覆盖」两集，镜像 §2.3 判据拆分 |
| 13 | mock 分支 | [financeStore.js:276](../hrm-clients/packages/mock/src/financeStore.js#L276)、[503](../hrm-clients/packages/mock/src/financeStore.js#L503)、[605](../hrm-clients/packages/mock/src/financeStore.js#L605)、[632](../hrm-clients/packages/mock/src/financeStore.js#L632)、[719](../hrm-clients/packages/mock/src/financeStore.js#L719)、[764-774](../hrm-clients/packages/mock/src/financeStore.js#L764-L774) | 多处依 `EDITABLE_STATUS`/状态判定 | 逐点同步新判据；**异议落 `OBJECTED`**（不再落 `PENDING_APPROVAL`） |
| 14 | mock 路由 | [finance.js:131](../hrm-clients/packages/mock/src/routes/finance.js#L131) | 状态白名单取 `PAYROLL_STATUS_LABEL` 键 | 字典补齐后自动生效，**需回归** |
| 15 | mock 路由 | [finance.js:152](../hrm-clients/packages/mock/src/routes/finance.js#L152) | 员工状态限 `['PUBLISHED','CONFIRMED']`（**v1.1 遗漏**） | **加 `PAID`** |
| 16 | 门禁断言 | [verify-mock.mjs:5470](../hrm-clients/scripts/verify-mock.mjs#L5470) | 断言「恰好 6 态」（**v1.1 遗漏**） | 改为 8 态（或断言包含新态），否则**门禁失败** |
| 17 | 门禁断言 | [verify-mock.mjs:5331](../hrm-clients/scripts/verify-mock.mjs#L5331)、[5371](../hrm-clients/scripts/verify-mock.mjs#L5371) | 提交后状态断言 / `my` 列表限 `PUBLISHED/CONFIRMED` | `5371` 加 `PAID`；`5331` 不受影响 |
| 18 | 待办字典 | [boss-h5/stores/todo.js:69,102](../hrm-clients/apps/boss-h5/src/stores/todo.js#L69)、[staff-h5/stores/todo.js:54](../hrm-clients/apps/staff-h5/src/stores/todo.js#L54) | `PAYROLL_STATUS` 渲染待办标签（**v1.1 遗漏**） | 字典补齐后回落；确认新态文案 |
| 19 | 待办组 | [todoGroups.js:25](../hrm-clients/apps/boss-h5/src/constants/todoGroups.js#L25) | 待办组固定 `status='PENDING_APPROVAL'`（**独立新发现**） | 异议改 `OBJECTED` 后，异议待办组须改查 `OBJECTED`（否则异议单不再进待办） |
| 20 | 异议面板 | [usePayrollObjections.js:22](../hrm-clients/apps/web/src/views/finance/composables/usePayrollObjections.js#L22)、[boss payroll.vue:29,52](../hrm-clients/apps/boss-h5/src/modules/boss/views/payroll.vue#L29) | 异议列表/默认筛选查 `status='PENDING_APPROVAL'`（**独立新发现**） | **必须改查 `OBJECTED`**，否则异议面板/待办空 |
| 21 | 端详情 | [boss payrollDetail.vue:38,45,75](../hrm-clients/apps/boss-h5/src/modules/boss/views/payrollDetail.vue#L38)、[staff payrollDetail.vue:41-65](../hrm-clients/apps/staff-h5/src/views/staff/payrollDetail.vue#L41-L65) | boss `objected = objectionReason && status==='PENDING_APPROVAL'`；staff 硬编码分支 | boss 改判 `status==='OBJECTED'`；staff 增 `OBJECTED`/`PAID` 文案与按钮 |
| 22 | 动态/其他 | [MyPayrollCard.vue:49](../hrm-clients/apps/boss-h5/src/components/MyPayrollCard.vue#L49)、[GeneratePayrollDialog.vue:23](../hrm-clients/apps/web/src/views/finance/components/GeneratePayrollDialog.vue#L23)、[usePayrollActions.js:89](../hrm-clients/apps/web/src/views/finance/composables/usePayrollActions.js#L89)、[PayrollTabPanel.vue:84,109-116](../hrm-clients/apps/web/src/views/finance/components/PayrollTabPanel.vue#L84) | `StatusTag` 动态取值（无 NPE）；可覆盖集/动作集；`v-for` 动态渲染 | 字典补齐即可回落；可覆盖集与动作集须与后端 `isOverwritable`/动作集**同口径** |
| 23 | **staff 侧点位①**（**v1.5 复-4 补，复评独立发现**） | [staff payroll.vue:93](../hrm-clients/apps/staff-h5/src/views/staff/payroll.vue#L93) | 硬编码 `v-if="item.status === 'PUBLISHED'"`（卡片上方「待确认」提示），未列 | **非 NPE**（分支回退）；须与新状态一致：`PAID` 为归档态、`OBJECTED` 为内部态，均不应再显示「待确认 · 点开核对明细后确认」提示 |
| 24 | **staff 侧点位②**（**v1.5 复-4 补，复评独立发现**） | [MyPayrollCard.vue:49](../hrm-clients/apps/staff-h5/src/components/MyPayrollCard.vue#L49) | `StatusTag :dict="PAYROLL_STATUS"`（boss 侧 counterpart 已列于第 22 行） | **非 NPE**（`StatusTag` 有 `TYPE_FALLBACK` 回退）；字典补齐后回落，须确认新态文案（尤其 `PAID` 在员工侧的可读性） |
| 25 | **staff 侧待办组**（**v1.5 复-4 补，复评独立发现**） | [todoGroups.js:21](../hrm-clients/apps/staff-h5/src/constants/todoGroups.js#L21) | staff 待办组固定 `params:{status:'PUBLISHED'}`（boss 侧 counterpart 已列于第 19 行） | **非 NPE**；须与 `C-1`（异议改 `OBJECTED`）后的一致性核对——staff 侧待办/入口取值与后端新状态口径一致 |

> **验收口径（必改 10）**：新增任一状态后，三端**详情 / 列表 / 步骤条 / 待办 / mock / 门禁**均无 NPE、状态渲染与动作可用性正确；`verify-mock.mjs` 断言随字典同批更新。

### 2.9 统一终态写守卫 `assertMutable(payroll, action)`（v1.2 新增，响应安全 M-3/REG-11）

**问题**：`PAID` 冻结现仅以「状态集合 + 9413」表达，实际守卫**分散 5 处**（`submit`/`approve` 走 `requireAction`；`updateItems` 内联；`confirm`/`objection` 内联；`publish` 内联；`generate` 走 9405），**无单一收口**；扩展期（新增 `pay`、扩展 `isItemEditable`）任一入口漏加即绕过（安全 REG-11）。

**设计**：所有工资单写入口在进入业务分支前，**统一调用** `PayrollStateMachine.assertMutable(payroll, action)`（或等价地**全部**改走 `requireAction` 单一收口）：

- `payroll.status == PAID` → 一律 `9413`（`FINANCE_PAYROLL_ARCHIVED`），**不区分动作**；
- `isItemEditable` / `isOverwritable` / `actionsOf` 对 `PAID` **显式取值**（`false` / `false` / 空集），不依赖「集合不含即拒绝」的隐式行为；
- `publish(ids)` 对**非允许来源**（含 `PAID`/`CONFIRMED`）**返回显式错误码**，不得静默计入 `skipped`（安全 REG-14）。

**覆盖范围（必须全部收口，逐条可验）**：改明细（`updateItems`）、加扣款（`items/add` I-6）、`submit`、`approve`、`reject`、`publish`（含再发布）、`objection`、**`pay`（I-8）**、`generate` 覆盖重建（`deleteExisting`），以及未来任何新增写入口。

**验收标准（交测试）**：对 `PAID` 单调用上述**全部**写入口 → 均返回 `9413`；`publish` 传入 `PAID` 单返回错误码而非仅 `skipped+1`。

### 2.10 自动算薪生成后的状态与通知语义（v1.2 新增，响应技术评审必改 5）

**问题**：v1.1 中自动路径 `generate` 落 `DRAFT`，而通知类型 7 文案为「工资单待审核」，且 `DRAFT` **不在** `approve` 允许集（需先 `submit`，§2.2）——「`DRAFT → PENDING_APPROVAL` 由谁触发」未定义，与 Q6「生成后先推管理员审核」存在语义偏差。

**裁定（选项 a：自动生成即自动提交）**：自动算薪在 `generate` 成功后，**对本次新生成的 `DRAFT` 单在同一驿站事务内调用既有 `submit`**，落 **`PENDING_APPROVAL`**，随后（事务提交后）发通知类型 7「工资单待审核」。理由：

1. **吻合 Q6/Q8**：管理员收到「待审核」即对应 `PENDING_APPROVAL`，可直接 `approve`/`reject`，无需先手工 `submit`；发布后（`PUBLISHED`）才推员工（type 8），链路与 Q6/Q8 一致。
2. **与 D1/D4 保护逻辑自洽**：自动路径不产出长期 `DRAFT`，「含 `DRAFT` 保护性 `SKIPPED`」（§3.3 D1 裁定）只可能命中**手工**产生的 `DRAFT`；自动单落 `PENDING_APPROVAL`（非可覆盖态）→ 天然不被 `generate` 覆盖重建，消除「正在审的单被悄悄改掉」。
3. **管理员仍可在审核期改**：`PENDING_APPROVAL` 属 `isItemEditable`（Q6 新增可改），`item-add`/`item-update` 可用且必填事由、全留痕。

**同步点**：§2.2 动作矩阵（`DRAFT→submit` 由自动路径触发）、§4.5 类型 7 触发点文案、§6 B2/B4 验收口径、§10。
**备选（选项 b，未采纳）**：保持落 `DRAFT`，把通知文案改为「待处理草稿」——不采纳，因其与 Q6「推管理员审核」的语义偏差未消除，且管理员需两步（先 `submit` 再审核）。

***

## 3. 表设计（只设计，不写迁移脚本）

> 迁移编号：**接续 `V20` 起**（MySQL；PostgreSQL 冻结，本次不产出 pg 脚本）。`V20__payroll_automation.sql` 承载下述 3 新表 + `payroll` 加列（**已由数据库工程师完成 v1.1 同步**）；**v1.2/v1.4 追加 `V21`**（① `payroll_log` 冗余定位列，M-2②，见 §3.2；② `payroll_run.attempt_date` + 唯一键 `uk_attempt`（并删除冗余索引 `idx_payroll_run_station_month`），U-06 / v1.4 主代理裁定，见 §3.3；③ 新表 `station_payroll_setting_log`，M-9，见 §3.6）；快照同步点 `sql/schema/mysql/init.sql` 同批更新（[registration-design.md:61](registration-design.md)）。
> 通用约定：`is_deleted` 逻辑删除、`create_time`/`update_time`（应用层维护）、UTF-8/utf8mb4（[db.md](db.md) 通用规范）。

### 3.1 驿站级算薪配置（Q4/Q5）

**决策（ADR-02）：新增独立表 `station_payroll_setting`，不在 `station` 表加列。**

| 备选 | 说明 | 结论 |
| --- | --- | --- |
| 甲：`station` 表加列 | 改动一期核心字典表 | **否决**：污染 `station` 的「组织属性」语义；配置有 `enabled`/时间/开关等生命周期，混入字典表后难扩展 |
| 乙：独立表（**采纳**） | 一驿一条，与 `attendance_rule` 同构 | 布局清晰、可扩展、与既有「一驿一条配置」先例一致（[V5__attendance.sql:21-47](../hrm-server/src/main/resources/db/migration/mysql/V5__attendance.sql#L21-L47)） |
| 丙：复用 `leave_setting` 式全局单行 | 全局一个算薪日 | **否决**：违反 Q4（每驿站各自配置） |

**字段设计 `station_payroll_setting`**

| 字段 | 类型 | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT AI | 否 | — | 主键 |
| `station_id` | BIGINT | 否 | — | 驿站（逻辑外键 `station.id`，一驿一条，活跃唯一由 Service 查重） |
| `enabled` | TINYINT | 否 | 0 | 是否启用自动算薪：0=停用（默认，安全），1=启用 |
| `payroll_day` | INT | 否 | 1 | 算薪日=每月第几天。**范围已裁定（v1.3，U-05，裁定方=用户）：1..31**；当月无该日时 `dueAt` **钳位到当月最后一天**（例 `31→4/30`、`30→2026/2/28`、`29→2024/2/29`）。`V20` 注释「1-31（月末钳位）」与之一致（§13） |
| `payroll_time` | VARCHAR(5) | 否 | '09:00' | 执行时间 `HH:mm`（`Asia/Shanghai` 墙钟） |
| `notify_enabled` | TINYINT | 否 | 1 | 生成后是否推送管理员（Q8） |
| `remark` | VARCHAR(255) | 是 | NULL | 备注 |
| `is_deleted` | TINYINT | 否 | 0 | 逻辑删除 |
| `create_time` | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间（应用层维护） |

**索引 / 唯一性**

- `PRIMARY KEY (id)`。
- `KEY idx_station_payroll_setting_station (station_id)`。
- **唯一性不做 DB 唯一索引**：一驿一条由 Service 活跃查重保证（对齐 `attendance_rule` 决策 D7 与 `employee.phone` 口径，[V5__attendance.sql:10](../hrm-server/src/main/resources/db/migration/mysql/V5__attendance.sql#L10)）。
- **不新增 `enabled` 索引（v1.1 裁定，响应算法 R10）**：轮询以 `enabled=1 ∧ station.status=1` 过滤，但 `enabled` 仅 0/1、基数极低，单列索引选择性差、MySQL 大概率仍全表扫描；表规模 = 驿站数（数十~数百），全表扫描成本可忽略，且与「索引克制」原则一致。**采纳**的是扫描条件本身须含 `station.status=1`（见 §5）。

**说明**：`enabled` 默认 0（**默认不自动跑数**）；`notify_enabled` 默认 1（生成即推管理员）。

### 3.2 操作留痕 / 审计表（Q3/Q6/Q9 全链路追溯）

**决策（ADR-03）：新增追加型审计表 `payroll_log`，形态对齐 `leave_log`（[V9__leave.sql:57-72](../hrm-server/src/main/resources/db/migration/mysql/V9__leave.sql#L57-L72)）。**

**字段设计 `payroll_log`**

| 字段 | 类型 | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT AI | 否 | — | 主键 |
| `payroll_id` | BIGINT | 否 | — | 工资单（逻辑外键 `payroll.id`）；**注意**：`generate` 覆盖重建会物理删除 `DRAFT/REJECTED` 单，本列可能指向**已删单**（孤儿），故须配合下方冗余定位列检索 |
| `employee_id` | BIGINT | 是 | NULL | **冗余定位列（v1.2 新增，M-2②/M-4）**：留痕所属员工（逻辑外键 `employee.id`），使留痕可**脱离已删 `payroll_id`** 独立按「员工 + 账期」检索 |
| `month` | CHAR(7) | 是 | NULL | **冗余定位列（v1.2 新增）**：账期 `yyyy-MM` |
| `action` | VARCHAR(32) | 否 | — | 动作：`GENERATE_AUTO`/`GENERATE_MANUAL`/`ITEM_ADD`/`ITEM_UPDATE`/`SUBMIT`/`APPROVE`/`REJECT`/`PUBLISH`/`REPUBLISH`/`CONFIRM`/`OBJECTION`/`PAY`/`NOTIFY`/`NOTIFY_SKIP` |
| `operator_id` | BIGINT | 是 | NULL | 操作人（逻辑外键 `employee.id`；SYSTEM 为空） |
| `operator_name` | VARCHAR(50) | 是 | NULL | 操作人姓名快照 |
| `operator_role` | VARCHAR(20) | 是 | NULL | 操作人角色快照 |
| `operator_type` | VARCHAR(16) | 否 | 'USER' | 操作主体：`USER`=人工，`SYSTEM`=自动调度 |
| `time` | DATETIME | 否 | CURRENT_TIMESTAMP | 操作时间（**只插不改，无 `update_time`**） |
| `from_status` | VARCHAR(20) | 是 | NULL | 变更前状态 |
| `to_status` | VARCHAR(20) | 是 | NULL | 变更后状态 |
| `reason` | VARCHAR(200) | 是 | NULL | 事由：**手工加扣款必填（Q3）** / 异议原因 / 驳回意见 / 再发布处理说明 |
| `before` | JSON | 是 | NULL | 变更前快照（金额/合计等，低频读取） |
| `after` | JSON | 是 | NULL | 变更后快照 |
| `remark` | VARCHAR(200) | 是 | NULL | 备注 / 排障说明 |

**索引**

- `PRIMARY KEY (id)`。
- `KEY idx_payroll_log_payroll (payroll_id, time)` —— 详情页时间线查询主路径。
- `KEY idx_payroll_log_action_time (action, time)` —— 审计查询（按动作类型/时段）。
- **`KEY idx_payroll_log_emp_month (employee_id, month, time)`（v1.2 新增，M-2②/M-4）** —— 按「员工 + 账期」独立检索留痕，覆盖重建后仍可达（不经由已删 `payroll_id`）。

> **配置变更不在本表（v1.3，M-9 裁定）**：`payroll_id` 为 `NOT NULL`，结构上无法承载「无工资单」的配置事件；驿站算薪配置变更另立独立追加型审计表 `station_payroll_setting_log`（**§3.6**）。

> **迁移安排（M-2② 裁定；v1.4 范围扩展）**：`employee_id` / `month` 两列 + `idx_payroll_log_emp_month` **另立新迁移 `V21`**（`ALTER TABLE payroll_log ADD COLUMN employee_id BIGINT NULL, ADD COLUMN month CHAR(7) NULL, ADD KEY idx_payroll_log_emp_month (employee_id, month, time)`）。**v1.4 追加同批另两段**：`payroll_run.attempt_date` + 唯一键 `uk_attempt`（并删除冗余索引 `idx_payroll_run_station_month`）（§3.3，U-06 / 主代理裁定）、新表 `station_payroll_setting_log`（§3.6，M-9）。`V20` 保持「v1.1 已同步态」不动，以保版本可追溯；`payroll_log` 由 `V20` 新建、**尚无存量**，无需回填。（等效更简做法：若 `V20` 在本次修订前尚未进入执行流程，可并入 `V20` 建表语句；两者择一，以**版本可追溯**为原则。）

**与 `payroll` 既有审批字段的关系（是否冗余 / 以谁为真源）**

| 维度 | `payroll` 上的状态字段（`approve_remark`/`approver_*`/`approve_time`/`publisher_*`/`publish_time`/`confirm_time`/`objection_*`/新增 `paid_*`） | `payroll_log` |
| --- | --- | --- |
| 定位 | **当前态快照（latest）** —— 列表/详情直读，免 join | **全量事件流（append-only）** |
| 真源声明 | 当前态展示的**真源** | 历史追溯的**真源** |
| 是否冗余 | **非简单冗余**：一张单可被**多次**审批/发布/异议/再发布/发放，`payroll` 的列只保留**最后一次**；`payroll_log` 保留**每一次**。二者是「当前值 vs 事件流」关系 | 同左 |
| 写入规则 | 每次动作同步更新当前态列 | 每次动作追加一行，**永不 UPDATE/DELETE** |

**手工加扣款的事由与操作人落点**：事由全文写 `payroll_log.reason`（审计真源）；同时写 `payroll_item.detail`（展示文案，如「事由：xxx」），操作人由 `operator_id/operator_name` 记录（**不在 `payroll_item` 加列**，避免改动 V8 表结构；事由存储已裁定 U-10：复用 `detail` + `reason`，见 §7）。

**`before` / `after` 字段白名单与脱敏（v1.2 新增，响应安全 M-2③/M-3/REG-05）**

- **白名单（仅允许写这些键）**：明细级 `{itemKey, itemType, itemName, amount}` + 合计级 `{additionTotal, deductionTotal, grossAmount, netAmount}`。
- **禁止**：序列化实体 / 请求上下文 / **`rule_snapshot`**（计薪规则参数＝经营信息）/ 操作人证件信息 / 口令令牌；禁止「dump 实体」式实现。
- **可判定性要求**：由 `before`→`after` 必须能**唯一重建**本次 `netAmount` 差值与变动项（只记合计在连续/并发调整下不可反推）。
- **脱敏兜底**：`ClientLogSanitizer.scrub` 仅作**二次兜底**、**非唯一口径**（其正则仅覆盖 `token=/accessToken=/refreshToken=/password=/pwd=` 等凭据形态，**不覆盖**身份证/银行卡/金额/姓名，[ClientLogSanitizer.java:48-49](../hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L48-L49)）。
- **事由必填一致性（REG-03）**：任何**金额变更**（`items/add` I-6 与 `items` 更新 C-3）**强制 `reason` 2-200**（复用 `9412`）；`ITEM_UPDATE` 的实现**不得覆盖** `payroll_item.detail` 中的事由全文（既有实现会把 `detail` 覆盖为「人工填写」，[PayrollServiceImpl.java:362](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L362)，须修正）。

### 3.3 自动算薪运行记录与补跑幂等（Q7）

**新表 `payroll_run`**

| 字段 | 类型 | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT AI | 否 | — | 主键 |
| `station_id` | BIGINT | 否 | — | 驿站（逻辑外键 `station.id`） |
| `target_month` | CHAR(7) | 否 | — | 目标账期 `yyyy-MM` |
| `attempt_date` | DATE | 否 | — | **本次尝试的自然日（v1.4 升格为 DB 唯一键列）**：写 `ZonedDateTime.now(ZoneId.of(zone)).toLocalDate()`，**每行恒有值、永不置 NULL**；与 `station_id`/`target_month` 组成 **唯一键 `uk_attempt`**，作为「同一驿站同一账期每自然日至多尝试一次」的 **DB 硬防线**，并供**连续失败天数**统计 |
| `trigger_type` | VARCHAR(16) | 否 | — | 触发方式：`AUTO`=定时到点 / `CATCH_UP`=补跑 / `MANUAL`=手工触发 |
| `due_at` | DATETIME | 否 | — | 本次应执行时刻（判定「错过」的基准） |
| `status` | VARCHAR(16) | 否 | 'RUNNING' | 结果：`RUNNING`/`SUCCESS`/`FAILED`/`SKIPPED` |
| `skip_code` | VARCHAR(24) | 是 | NULL | **跳过码（机器可读）**：`BLOCKED_9405`/`CONFIG_INVALID`/`DRAFT_PROTECTED`（**v1.3 移除 `EXHAUSTED`**：无硬上限，不存在「重试耗尽即放弃」；连续失败改为**告警**而非放行 `SKIPPED`）；配合 `skip_reason` 供指标统计与「是否重试」判定 |
| `skip_reason` | VARCHAR(200) | 是 | NULL | 跳过原因（人类可读，如「该账期已生成 9405（单号 …）」） |
| `generated_count` | INT | 是 | NULL | 生成单据数 |
| `fail_reason` | VARCHAR(500) | 是 | NULL | 失败原因（截断，不落敏感信息） |
| `claim_key` | CHAR(7) | 是 | NULL | **认领槽位（v1.1：由 `success_key` 改名并升级语义）** = `target_month`。`RUNNING`/`SUCCESS`/`SKIPPED` 写值（占位）；`FAILED` 置 NULL（释放，允许重试）。配合下方唯一键 |
| `operator_id` | BIGINT | 是 | NULL | 手工触发人（`MANUAL` 时） |
| `start_time` | DATETIME | 否 | CURRENT_TIMESTAMP | 开始时间（**调度链路**用 `ZonedDateTime.now(zone).toLocalDateTime()`，R7） |
| `finish_time` | DATETIME | 是 | NULL | 结束时间（仅 `RUNNING` 为 NULL） |
| `is_deleted` | TINYINT | 否 | 0 | 逻辑删除（**只增不删、无删除入口**，Q-DB-9；业务永不置位） |
| `create_time` | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间（应用层维护） |

> **重试计数不另设列（v1.4 修订）**：claim 模型下每次尝试 = 一行（`RUNNING` → 终态），故「重试次数」= 该 `(station_id, target_month)` 下 `status=FAILED` 的行数；**「连续失败天数」= 连续 `attempt_date` 上均无 `SUCCESS`/占位**，均由唯一键 **`uk_attempt (station_id, target_month, attempt_date)`** 查询（唯一索引亦为其最左前缀 `(station_id, target_month)` 的普通查询路径）；每次尝试留一行可审计，**且每自然日至多一行——由 `uk_attempt` DB 唯一键硬保障**（见下）。

**索引 / 幂等硬约束（v1.1：claim 槽位）**

- `PRIMARY KEY (id)`。
- **`UNIQUE KEY uk_payroll_run_claim (station_id, claim_key)`** —— **claim 槽位（v1.1 由 `uk_payroll_run_success` 改名升级）**：唯一索引允许多个 NULL，故 `FAILED`（`claim_key=NULL`）可多次存在；而**同一 (驿站, 账期) 至多一条占位行**（`RUNNING`/`SUCCESS`/`SKIPPED` 互斥）。这是重叠 tick / 多实例重复触发的最后一道 DB 硬防线（§1 ADR-01、Q-DB-7）。
- `KEY idx_payroll_run_status_time (status, start_time)` —— 查询在跑/失败列表、僵死 `RUNNING` 扫描。
- **~~`KEY idx_payroll_run_station_month`~~ 已删除（v1.4）**：原索引列 `(station_id, target_month, attempt_date)` 与 `uk_attempt` **完全相同**，按**最小冗余**原则**删除**（避免两个同列索引）；其查询用途（本驿站本账期运行历史 / 连续失败天数统计）由 **`uk_attempt` 的最左前缀**覆盖。`V21` 以 `DROP INDEX idx_payroll_run_station_month` + `ADD UNIQUE KEY uk_attempt …` 收敛为**单一索引**（§6）。

**两个唯一键的正交关系（v1.4，主代理裁定）**

| 唯一键 | 列 | 管什么 | 写值 / 置 NULL 规则 | 是否冲突 |
| --- | --- | --- | --- | --- |
| `uk_payroll_run_claim`（v1.1） | `(station_id, claim_key)` | **跨日终态占位**：同一 (驿站, 账期) 至多一条**占位行** | 占位行（`RUNNING`/`SUCCESS`/`SKIPPED`）写 `claim_key=target_month`；`FAILED` 置 **NULL**（释放，允许其他日重试） | 与 `uk_attempt` **不同列 → 不冲突** |
| **`uk_attempt`（v1.4）** | `(station_id, target_month, attempt_date)` | **日内一次**：同一 (驿站, 账期, **自然日**) 至多一行尝试 | `attempt_date` **每行恒写值（NOT NULL）**，无「置 NULL」语义 | 与 `uk_payroll_run_claim` **不同列 → 不冲突** |

- **为何不冲突（正交性）**：二者约束**维度不同**——`uk_payroll_run_claim` 约束「**终态占位**」维度（`claim_key` 允许多 NULL，故多条 `FAILED` 行可并存），`uk_attempt` 约束「**自然日**」维度（`attempt_date` 恒有值）。同一 `(s,m)` 的**多行 `FAILED`（跨日）**在 `uk_attempt` 下靠**不同 `attempt_date`** 天然区分、在 `uk_payroll_run_claim` 下靠**多 NULL 并存**天然放行；**占位行至多一条**由 `uk_payroll_run_claim` 保证，其 `attempt_date` 唯一，且与任何 `FAILED` 行**不同日**（同日至多一行，见「错过」判据第 4 步），故两键同时成立、互不触发 DuplicateKey。
- **应用层短路降级（v1.4）**：应用层 `existsSuccess`/「当日已尝试」`EXISTS` 检查**仅作查询优化**（提前短路、省一次 INSERT），**不再作为唯一防线**；「每自然日至多一次」的不变式由上述 **`uk_attempt` DB 唯一键**承载（跨并发、跨实例均成立）。

**Claim 槽位协议（响应算法 R2；v1.0 原「仅 SUCCESS 写哨兵」已废止）**

| 运行状态 | `claim_key` 取值 | 效果 |
| --- | --- | --- |
| `RUNNING` | `target_month` | **占位**：第二个并发执行者 INSERT 即 DuplicateKey（1062）→ 短路，**零重复算薪** |
| `SUCCESS` | `target_month` | 占位（永久）：不再重复生成 |
| `SKIPPED`（`BLOCKED_9405`/`CONFIG_INVALID`/`DRAFT_PROTECTED`） | `target_month` | 占位（永久）：不重试 |
| `FAILED` | **NULL** | **释放**：允许下一 tick 重试 |

- **为什么 `RUNNING` 也必须占位**：v1.0 仅 `SUCCESS` 写哨兵时，两个重叠 tick（或未来两实例）可**同时插入 `RUNNING` 并同时进入 `generate`**；因下游未提交、彼此不可见，两者各自通过 9405 检查，最终可能各插一行同员工同月单据（单据层无唯一约束，见下）。让 `RUNNING` 占位后，冲突在 **INSERT 一条 SQL** 上被 InnoDB 唯一约束原子拒绝。
- **事务切分（交后端实现）**：`INSERT RUNNING`（commit 独立短事务 Tx1）→ `generate`（业务事务 Tx2）→ `UPDATE` 终态（Tx3）。Tx1 必须先提交，否则未提交占位对其他执行者不可见，且 `generate` 回滚会连带抹掉占位。
- **僵死 `RUNNING` 回收（响应算法 R6；v1.5 完整定义，复评 复-1）**：tick 对 `status=RUNNING ∧ start_time < now - running-timeout-minutes` 的记录判为僵死。**回收动作（完整定义，同一 `UPDATE` + 告警，四条同时执行）**：① `claim_key = NULL`（**释放占位**）；② `status = 'FAILED'`；③ `fail_reason = 'STALE_RECLAIMED'`（机器可辨识标记，供 I-5 查询与巡检）；④ `finish_time = now`。**回收后该 `(s,m)` 不再被占位拦住，允许按日粒度闸门在次日重试**（同日至多一次仍由 `uk_attempt` 硬保障）。**并必须触发失败告警**（复用连续失败告警通道 `notify-on-fail` / `alert-after-consecutive-fail-days`，§5），**消除复评指出的「静默失效、告警不触发」**。
  - **阈值与守卫**：`running-timeout-minutes` 外置（默认 **30**，**值以 `[算法 v1.3]` 为准**）；回收 `UPDATE` 必须带 `WHERE status='RUNNING' AND start_time < …` 守卫（防误改已终态行）；`running-timeout-minutes` 必须 **> 最坏批次耗时**，否则误杀正常长任务（安全 REG-10）。
  - **总开关（v1.5 更正默认值，复评 复-1）**：受 `stale-reclaim-enabled` 控制，**单实例默认 `true`（开启）**——与 `[算法 v1.3]` 一致（纠正 v1.4 的 `false`）。理由：开启后僵死行可**自动回收 → 释放占位 → 次日自愈**，从根上消除复评 §三 3.2 独立发现的「卡死」场景。
  - **单实例下回收为何安全（复评 复-1 要求论证）**：单实例 `@Scheduled(fixedDelay)` **不自我并发**（上一轮未结束不启动下一轮），**回收者与执行者同线程、串行**，故**不存在「回收与另一执行者并发」**——某 `RUNNING` 行 `start_time` 超过 `running-timeout-minutes` 即证明其**所属执行已不再推进（真僵死）**，而非「与回收并发」，回收为**安全**。该论证的**前提是「单实例 + `fixedDelay` 不重叠」**；**打破该前提（水平扩容）即回收不再安全**，须先补选主 / 分布式锁或加 owner/心跳列（架构债 **T4**，§7）——与 D1 裁定的四条前提一致。
  - **场景登记（复评 §三 3.2 独立发现，v1.5 显式登记）**：**触发条件** = 某日 `RUNNING` 行僵死且**未被回收**（关闭回收或回收失效）；**影响** = 该日行 `uk_attempt` 拦、次日行 `uk_payroll_run_claim` 被永久占位拦住 → `(s,m)` 一直回 `9410`、**自动补跑中止**，且**因无 `FAILED` 行，连续失败告警不触发 → 静默失效**；**处置/缓解** = ① **默认开启回收**（`stale-reclaim-enabled=true`）→ 超时即回收、置 `FAILED`、释放占位、次日自愈，**并触发失败告警**；② `running-timeout-minutes` > 最坏批次耗时，避免误杀；③ 若运维显式关闭回收，则须对超期 `RUNNING` 增设巡检告警 + 最小人工恢复步骤（C 档）。**故关闭回收不再是默认态，静默失效仅在「显式关闭且无巡检」下残留。**

**claim 协议三处口径一致 + 全部生成路径占用同一 claim（v1.2 新增，响应安全 M-1/REG-01，为「生产启用自动算薪」硬前置）**

- **三处一致**：① 本方案 §3.3（claim 槽位：`RUNNING`/`SUCCESS`/`SKIPPED` 占位、`FAILED` 置 NULL）；② 数据层 `V20__payroll_automation.sql` + `db.md` §8.6.7 + `init.sql`（**已由数据库工程师完成 v1.1 同步**：`claim_key` / `uk_payroll_run_claim` / `skip_code` / `payroll_day` 1-31 / 枚举末尾追加序）；③ 验收断言 **U-13**（已重写为「同一 `(station_id, target_month)` 连续写两条 `RUNNING` 应触发 **1062**」）。三者口径**逐字一致**，由数据库工程师在 B1 前出具**三方对读记录**（安全 §5.3 报审前置）。
- **全部生成路径占用同一 claim（硬前置）**：**手工 `generate`（C-7）、调度 `tick`、手工触发 `trigger`（I-4）必须复用同一 `(station_id, target_month)` claim**——`INSERT RUNNING` 占位成功方可进入 `generate`；占位失败（1062）统一回 `9410`（`FINANCE_PAYROLL_RUN_IN_PROGRESS`）。这是把「唯一 DB 硬防线（Layer 0）」覆盖到**非调度**路径的必要条件；**该路径闭环前禁止启用自动算薪**（安全 M-2 硬阻断）。此消解手工与自动同刻争抢（原 R1-A 残余风险），后置巡检仅作兜底、不再是唯一手段。

**D1 裁定：`payroll_run` 不加 `owner_instance_id` / `heartbeat_at` 列（v1.2）**

算法四件套 §1.5.1 的 W1 处置 P1/P2 需给 `payroll_run` 增列（衍生风险 D1）。**架构裁定：本期不加列**，理由：

1. **W1 的成立前提在本期不满足**：W1 需存在「回收者 B」。单实例 `@Scheduled(fixedDelay)` **不自我并发**、回收与执行同线程 → **B 不存在 → W1 不发生**（算法 §1.5.1 前提声明）。
2. **不加列时的单实例正确性保障**（四条同时成立，任一被打破即须重评）：① 自动路径对**含 `DRAFT` 单的 `(站点, 账期)` 一律保护性 `SKIPPED`**（算法 §1.5.2 (a)，**本方案确认采纳**），把「自动覆盖重建」从自动路径移除，消除 W2（`item-add` 与自动重建不互斥）；② **单实例 `fixedDelay` 不重叠 → 无并发回收者 B → 回收安全**（§3.3 论证），`stale-reclaim-enabled` **默认 `true`**（v1.5 更正；与 `[算法 v1.3]` 一致），仅运维显式关闭时才走人工处置（C 档）；③ 手工与自动**共用同一 claim**（上条）；④ `running-timeout-minutes` > 最坏批次耗时。
3. **结论**：在「单实例 + `fixedDelay` 不重叠（无并发回收者 B）+ 含 `DRAFT` 保护性 `SKIPPED` + `stale-reclaim-enabled=true`（且回收阈值 > 最坏批次耗时）」四前提下，**不加列亦不产生重复工资单**；无「不加列即出错」的反例，故采最简方案。
- **登记为架构债 `TODO(扩展)`**（并入 §7 **T4**）：**多实例**与**僵死回收误判**下的并发重复风险，须在**水平扩容前**补「实例选主 / 分布式锁」或加 owner/心跳列（算法 P1/P2），并重评。
- **D2~D6 回应**：**D2**（终态并入业务事务的跨层耦合）在不加列下不触发；**D3**（跨月扫描）**已由 U-06 裁定收口**：改为**日粒度重试模型**，账期恒为 `dueAt` 所在月、仅执行日可落次月，无需「口径 A 跨月扫描」特殊分支（§3.3/§13）；**D4**（含 `DRAFT` 保护性 `SKIPPED` 改变既有行为）**本方案确认采纳**，登记为行为变更（边界用例 #10）；**D5**（心跳续期 tick 成本）不加列即不存在；**D6**（两账期同 tick 的 9405 交互）依赖「9405 按驿站收敛」（C-7），已登记。

**`fail_reason` 脱敏可执行化（v1.2 新增，响应安全 M-2③/M-5）**：`fail_reason` 由**服务端构造**（异常类名 + 错误码 + 白名单文案），**禁止**拼接 SQL 异常原文 / 参数值 / 业务数据 / 凭据 / 完整证件号；超长按**字符**安全截断（≤500，不截半 JSON）；完整堆栈只进应用 `error` 日志。`ClientLogSanitizer.scrub` 仅作**二次兜底**、非唯一口径（同上）。

**「错过」判据（v1.3：日粒度重试模型，U-06 定稿）**

> **口径变更（v1.3）**：原 v1.1/v1.2 的「补跑窗口 `[dueAt, min(dueAt + W, 次月首日 00:00))`（`W` 默认 72h）」**整体作废**，替换为用户裁定 U-06 的**日粒度重试模型**（裁定原文与落点见 §13）。配置项 `catch-up-window-hours` / `catch-up-window-min-hours` 随之下线（§5）。**不得**再加硬上限推翻该口径（可加告警 / 可配熔断补偿，默认持续重试）。

对每个 `enabled=1 ∧ station.status=1` 的驿站配置（`target_month` **恒为 `dueAt` 所在月**）：
1. `dueAt = 当月 min(payroll_day, 当月天数) 的 payroll_time (Asia/Shanghai)`（**钳位，U-05 已裁定**，§5）；
2. **当日触发时刻（v1.5 复-3 统一）**：`当日 = 该次尝试的自然日`；**尝试时刻 = 当日 `catch-up-time-of-day`（默认继承该站 `payroll_time`，`[算法 v1.3]` §2 为参数单一真源）**——算薪日当天 = 当期 `payroll_time`，补跑日 = 当日同钟点（默认同值）；
3. **到点**：`now ≥ 当日 catch-up-time-of-day`。**（v1.5 复-3 纠正 v1.4 的「`now ≥ dueAt`」**——`dueAt` 固定为当期算薪日，跨入次日 00:00 后该条件恒真，会使补跑在凌晨任意 tick 触发、早于上游数据日结；改为**与正常到点对称的当日钟点**后方一致）；
4. **未占位**：`(station_id, claim_key=target_month)` 无占位行（claim 短路即视为已有 `SUCCESS`/`SKIPPED`）；
5. **当日未尝试**：`(station_id, target_month, attempt_date=today)` 无行（**日粒度闸门**；此判定为**应用层优化短路**，其硬防线为 DB 唯一键 `uk_attempt`，见下）；
6. 全部满足 → 判定「需执行 / 需补跑」，`trigger_type` 按 §5 R8 基准标记（`AUTO` / `CATCH_UP`）后执行；
7. **不再存在「超窗口即永久中止」**：算薪日当天未成功 → **次日等到该站当日钟点后**继续尝试，每自然日至多一次，直至成功或被占位。

**日粒度重试模型（v1.4，替代原「补跑窗口」，U-06 定稿 + 主代理裁定防线上移）**

用户裁定原文：「**补跑窗口只允许次日补跑，补跑失败则次日再次补跑**」→ 最终口径：

- **算薪日当天未成功执行** → **次日补跑**；**补跑失败 → 次日继续补跑**，以**自然日为粒度持续重试直到成功**（**无硬性天数上限**）。
- **补跑触发时刻（v1.5 复-3 统一）**：每个自然日的尝试时刻均取 **`catch-up-time-of-day`（默认继承该站 `payroll_time`）**，**须 `now ≥ 当日 catch-up-time-of-day`**；**不在凌晨 00:0x 触发**（避免早于上游数据日结，算法 R-D2）。`target_month` 恒为 `dueAt` 所在月（下条）。
- **同一驿站同一账期，每个自然日至多尝试一次**：由 **DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)` 硬保障**（跨实例兜底），应用层 `existsSuccess`/「当日已尝试」短路（第 5 步）**仅作查询优化、不再作为唯一防线**。**杜绝 10 分钟 tick 在同一天反复重试**（同日第二次 INSERT 撞唯一键 → `9410`，不得因 `fixedDelay` 连续 tick 而同日多次落行）。
- **`target_month` 恒为 `dueAt` 所在月**：补跑的是**该账期单据**，仅**执行日推后**；账期归属**不因执行日跨月而改变**（原「自动路径只评估 `now` 所在月 / 跨月一律手工触发」的**自动中止口径作废**）。
- **补偿（允许，非推翻；参数以 `[算法 v1.3]` §2 为单一真源）**：新增**告警 / 可选补偿** —— 连续失败天数达 `alert-after-consecutive-fail-days`（默认 3；**值以 `[算法 v1.3]` 为准**）时**推送管理员**，重复节奏由 `alert-repeat-interval-days`（默认 0 = 仅首次）控制。**告警只提醒、不停止重试**（`[算法 v1.3]` 无「暂停自动重试」类参数；如后续确需熔断补偿，须由算法先出四件套，本方案不预设）。**不得构成「默认不再重试」的硬上限。**
- **手工触发（I-4，v1.5 复-2 删除 `force`）**：仍受**占位**约束（已 `SUCCESS`/`SKIPPED`/`RUNNING` → `9410`）；对**无占位**账期（前次 `FAILED` 或从未执行），受同一**日粒度闸门**约束——`now ≥ 当日 catch-up-time-of-day` 且当日未尝试方可执行（**应用层短路为优化、`uk_attempt` 为硬防线**）；**同日重复触发一律回 `9410`**（由 `uk_attempt` 硬拒绝）。**不再提供 `force` 参数**，故无「同日再试 / 强制重跑已成功账期」能力。
- **幂等与并发**：日粒度闸门为 **DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)` 硬保障**（跨实例），**应用层 `existsSuccess`/「当日已尝试」短路仅作查询优化**；`(s,m)` 的**跨日终态占位**另由 `uk_payroll_run_claim`（claim 槽位）兜底（同日并发触发时第二个执行者 INSERT 撞唯一键 → `9410`）。二者**正交共存**（§3.3上「两个唯一键的正交关系」），故日粒度不引入新的重复算薪风险（幂等分层见下）。

**同日重复触发一律 `9410`（v1.5：删除 `force`，复评 复-2 裁定）**

> **v1.5 裁定（复评 复-2）**：v1.4 收敛后，`force=false/true` 在**全部情形下无可观测行为差异**（复评 §三 3.3 独立验算：无占位未尝试 → 均执行；无占位当日已尝试 → 均 `9410`，仅路径不同；已占位 → 均 `9410`；`RUNNING` 未回收 → 均 `9410`）→ `force` **失去存在意义**。**故删除 `force` 参数**：`trigger`（I-4）端点仍作人工补跑入口，但**同日重复触发一律回 `9410`**，由 DB 唯一键硬拒绝。

| 情形 | 结果（v1.5 无 `force`，统一口径） |
| --- | --- |
| `(s,m)` 无占位、**当日未尝试**且 `now ≥ 当日 catch-up-time-of-day` | 正常执行 |
| `(s,m)` 无占位、**当日已尝试** | 拒绝并回 **`9410`**（应用层短路仅优化；硬防线 = DB 唯一键 `uk_attempt`：同日第二次 INSERT 撞唯一键 → `9410`） |
| `(s,m)` 已占位（`SUCCESS`/`SKIPPED`，`claim_key=target_month`） | `INSERT RUNNING` 撞 `uk_payroll_run_claim` 唯一键 → 回 **`9410`**（占位是「不重复算薪」不变式，**不释放**） |
| `(s,m)` 有 `RUNNING` 未回收 | 回 `9410`；待回收（**默认开启**，`stale-reclaim-enabled=true`）置 `FAILED` 释放占位后，**次日照常重试**（§3.3 回收动作 + 告警） |

- **不再提供的能力（登记，不沉默）**：**释放占位 / 强制重跑已成功账期**（占位永久性是 Layer 0 硬防线前提，与安全 M-1 冲突）；**配置修正后重算已 `SKIPPED(CONFIG_INVALID)` 的账期**（须走**新账期**或由数据库工程师/主智能体按 C 档人工处置，登记 §7 **T5**）。原 `force` 不承担这些职能，**删除后能力面无回退**。
- **`9414` 处置（v1.3）**：日粒度模型下**不再存在「超窗口」**，`FINANCE_PAYROLL_RUN_WINDOW_EXPIRED (9414)` **已于 v1.3 作废、保留号段不复用**（§4.3）。
- **`9410` 文案扩展（v1.3）**：`FINANCE_PAYROLL_RUN_IN_PROGRESS` → 「该驿站该账期**正在运行、已占位或当日已尝试**，不可重复触发」。

**误发放冲正（v1.2 裁定，响应必改 6 · 维度 2 问题 2）**：`PAID` 为绝对冻结终态（U-03），**本期不提供「误发放冲正 / 反归档」路径**；该能力属**受限项**，登记为 `TODO(扩展)`（§7 **T5**）并附风险说明，**不得沉默**。运维需要时由主智能体评估是否另立迁移/补偿机制（C 档），不在本方案内拍板。

**幂等分层与「单据级缺口」风险登记（响应算法 R1）**

| 层 | 机制 | 粒度 | 强度 |
| --- | --- | --- | --- |
| Layer 0 · 调度层 | `payroll_run` **claim 槽位**唯一键（本节；**v1.2：全部生成路径含手工 `generate` 均占用**） | 一驿站一账期 | **DB 硬约束**（唯一硬防线） |
| Layer 1 · 生成层 | `generate` **9405**（**须按驿站收敛**，§0.2 ⑦ / C-7） | 一驿站一账期 | 应用层（同事务 `check`） |
| Layer 2 · 单据层 | ~~`idx_payroll_emp_month_bill`~~ **无 DB 唯一约束** | 一员工一月度单 | **不存在**（v1.1 更正：该索引为普通 `KEY`，[V8__payroll.sql:85](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L85)） |

- **裁定：`V20` 不新增 `payroll` 单据级唯一约束**（不采纳算法 R1 的「另立 UNIQUE KEY」备选）。理由：
  1. **加键须存量查重前置（C 档）**：`(employee_id, month, bill_type)` 上若已有重复行（历史 `generate` 重复调用曾产生物理重复，见 [PayrollServiceImpl.java:601-621](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L601-L621) 注释「物理行 63 → 126」），必须先物理去重；而**存量若存在重复的 `PUBLISHED`/`CONFIRMED` 行，去重即违反 Q9「发布后不可删除」铁律**——即唯一键在极端存量下**无法在不违约的前提下建立**。
  2. **与既有 V8 口径冲突**：`payroll` 现行「(employee_id, month, bill_type) 由 Service 活跃查重」（[V8__payroll.sql:9-10](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L9-L10)），改 DB 唯一属结构变更，超出本方案设计范围。
  3. **claim 唯一键已提供 DB 硬线**：自动算薪路径下，「一驿站一账期一次成功」由 Layer 0 硬约束保证；叠加 Layer 1 按驿站收敛的 9405，已覆盖 99% 重复风险。
  4. **索引克制**：与 `V20` 既有「高频筛选字段显式列 + 索引，不滥用唯一键」取向一致。
- **风险登记（交评审与安全面知悉）**：
  - **风险 R1-A（v1.2 更新）**：原「手工 `generate` 与 tick 在同一 `(employee, month)` 极端并发、各自通过 Layer 1」的理论窗口，v1.2 已由「**全部生成路径（含手工 `generate`）占用同一 `(station_id, target_month)` claim**」（本节上条 + C-7）在 **INSERT 一条 SQL** 上原子消解——手工与自动不再各自 check-then-act，而是**先争抢同一 claim**（占位失败→`9410`）。`generate` 内部 `deleteExisting` + `createPayroll` 仍在同一事务内先删后建（[PayrollServiceImpl.java:142-186](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L142-L186)）。
  - **残余**：单据层仍**无 DB 唯一约束**（Layer 2 空缺），故「同一 `(employee, month, bill_type)` 重复插入」**不由 DB 阻止**，仅由 claim + 9405 + 事务保证；`COUNT(*)` 与 `COUNT(DISTINCT employee_id)` 的日巡检仅作**兜底检测（非阻断）**。
  - **处置**：登记为**已知残余风险**，交技术评审评估是否需在实施期补「`(employee_id, month, bill_type)` 唯一键 + 存量查重 C 档动作」；本方案**不自行加键**。
- **9405 的语义映射**：调度触发生成时若命中 9405，`payroll_run` 记 `status=SKIPPED`、`skip_code=BLOCKED_9405`、`skip_reason` 回填阻断单据号，**不计为失败、不重试**。

> **与 `uk_attempt` 的分工（v1.4）**：本「幂等分层」针对**「同一员工同月单据不重复」**（防**重复算薪**）；`uk_attempt` 针对**「同一驿站同一账期每自然日至多一次」**（**频率闸门**，防**同日反复重试**）。二者目的不同、互不替代；`uk_attempt` 不改变 Layer 0~2 的结论。

### 3.4 手工加扣款（Q1/Q2/Q3）的数据支撑

- **不需要新业务表**：手工一笔加/扣款 = 在目标工资单下新增一行 `payroll_item`：
  - `source=MANUAL`、`item_type=ADDITION|DEDUCTION`、`amount>0`（正数，增/扣由 `item_type` 承载，[V8__payroll.sql:96-98](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L96-L98)）；
  - `item_key` 由**服务端生成（已裁定 U-09）**，且**强制 `MANUAL_` 前缀**（建议 `MANUAL_<账期>_<自增序号>` 或 `MANUAL_<uuid 短码>`）——该前缀既是生成契约，也是 R11 迁移时「识别 + 隔离 key 冲突」的依据；
  - `item_name` 为事由摘要/自定义名，`detail` 存事由全文（展示）；
  - 明细无 `payroll_item` 级唯一约束，`(payroll_id, item_key)` 唯一性由 Service 校验（重复 → 9411）。
- **可录入状态（v1.1 修订，R11）**：任何 `isItemEditable` 状态（`DRAFT`/`REJECTED`/`PENDING_APPROVAL`/`OBJECTED`）均可录入。**撤销 v1.0 的「手工项须在 `DRAFT/REJECTED` 之外录入」约束**——因为 v1.1 采甲（覆盖重建保留 MANUAL），`DRAFT` 期录入手工项不再丢失（§4.1 I-6 前置条件同步更新）。
- **R11 甲：`generate` 覆盖重建保留既有 `source=MANUAL` 明细（v1.1 新增，必改）**：
  - **识别**：在 `deleteExisting` **之前、同一事务内**，按 `(employee_id, month, bill_type=MONTHLY, status ∈ {DRAFT, REJECTED})` 查出目标旧单下 `source=MANUAL ∧ is_deleted=0` 的明细 → 内存快照（`item_key`/`item_type`/`item_name`/`amount`/`detail`）。
  - **排序**：保留项按原 `id` 升序保持相对次序，**接续**在新单规则项之后（`sort_order = R+1 …`），保证明细展示稳定（[V8__payroll.sql:105](../hrm-server/src/main/resources/db/migration/mysql/V8__payroll.sql#L105) 按 `(payroll_id, sort_order)` 取）。
  - **迁移与合计**：新单规则项 insert 完成后逐条 re-insert 保留项（新 `payroll_id`）；随后以「**规则项 + 保留项**」重算 `addition_total/deduction_total/gross_amount/net_amount`（复用 `PayrollTotalsPolicy`，[PayrollServiceImpl.java:366-378](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L366-L378) 同口径）。**不得**先落规则项合计、再另行叠加（避免合计口径分裂）。
  - **`item_key` 冲突处理**：**由本方案新增的建设性约定**——手工项 key 强制 `MANUAL_` 前缀，规则项 key 沿用 `payroll_rule_item.item_key`（`V8__payroll.sql:38` 仅声明该列为 `VARCHAR(40) NOT NULL COMMENT '规则项键'`，**未规定任何前缀**；「规则项不使用 `MANUAL_` 前缀」是本方案确立的**约定**，非既有事实）。**理由**：`MANUAL_` 语义明确、与规则项 key 命名空间天然可分离，且无需改 V8 表结构。**正常路径不冲突**。若遇历史脏数据冲突：**规则项 key 保持不变（规则为真源），保留项由服务端重新生成 key**，并在 `payroll_log.remark` 注明「重建迁移改键」。
  - **留痕**：每次覆盖重建写 **1 条** `payroll_log(action=GENERATE_AUTO|GENERATE_MANUAL)`，`before/after` 快照记录 `manualKept` 数量与保留项 `[{key,itemType,amount}]` 及合计变化；**不**为每条保留项单独写 log（防噪声）。
  - **边界**：仅 `isOverwritable`（`DRAFT`/`REJECTED`）被覆盖；`PENDING_APPROVAL`/`OBJECTED` 不在覆盖范围（§2.3），其 MANUAL 项天然安全。
  - **代价**：`createPayroll` 增加「一次保留项查询 + 若干 re-insert」，量级 = 单员工 MANUAL 项数（通常 0–数条），可忽略。
- **留痕**：每次新增写 `payroll_log(action=ITEM_ADD, reason=事由必填, before/after=合计前后, operator_*)`；随后**重算并更新** `payroll` 的 `addition_total/deduction_total/gross_amount/net_amount`（复用 `PayrollTotalsPolicy`，同口径）。
- **一次性语义（Q1）**：不新增员工级长期项表；该明细仅存于当月该工资单。`generate` 对 `DRAFT/REJECTED` 覆盖重建时，**MANUAL 项随单迁移保留（R11 甲）**；对 `PENDING_APPROVAL`/`OBJECTED` 不覆盖、不删除。
- **加扣款金额方向（v1.3 验收口径；主代理解释、待用户最终确认）**：`item_type=ADDITION` **计入应发合计**（`addition_total` / `gross_amount` 增大）、`item_type=DEDUCTION` **计入扣项合计**（`deduction_total` 增大）；**实发 = 应发 − 扣项**（即 `net_amount = gross_amount − deduction_total`，沿用既有 `PayrollTotalsPolicy` 模型，[PayrollServiceImpl.java:366-378](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L366-L378)，**不改公式**）。每笔**必填事由**（`9412`）并**记录操作人**（`payroll_log.operator_*`），且**自动参与金额计算**（**不是**只写留痕不改金额）——保存后**重算并回写** `payroll` 的 `addition_total/deduction_total/gross_amount/net_amount` **四项合计**。
- **预留（不预设）**：若用户后续要求「扣款亦减少应发（从 `gross_amount` 直接扣除）」，则须改 `PayrollTotalsPolicy` 的合计口径；**本 v1.3 按上一条执行，不作预设**（口径变更须用户确认后另行评估，见 §7 U-16 / §13.4）。

### 3.5 `payroll` 表加列（当前态展示）

| 新列 | 类型 | 默认 | 注释 |
| --- | --- | --- | --- |
| `paid_by_id` | BIGINT NULL | NULL | 确认发放人（逻辑外键 `employee.id`） |
| `paid_by_name` | VARCHAR(50) NULL | NULL | 确认发放人姓名快照 |
| `paid_time` | DATETIME NULL | NULL | 确认发放时间 |

> 现有 `status` 列 `VARCHAR(20)` 足以容纳 `OBJECTED`（7）/`PAID`（4），**无需改列宽**；仅需更新列 COMMENT 枚举说明。

### 3.6 驿站算薪配置变更审计表 `station_payroll_setting_log`（v1.3 新增，主代理 M-9 裁定）

**决策（ADR-04）：新增追加型审计表 `station_payroll_setting_log`，形态与 `payroll_log` 同构（§3.2），仅记录「驿站算薪配置」的变更历史。**

**背景（M-9 缺口闭环）**：安全评估 M-9 要求「算薪配置变更留痕、**启用 0→1 必须可追溯**」。`payroll_log.payroll_id` 为 `NOT NULL`（§3.2 字段表），结构上无法承载「无工资单」的配置事件；复用 `leave_log` 等既有系统日志会混淆审计归属。故新增专表，**与 `payroll_log` 同构但独立**。

**字段设计 `station_payroll_setting_log`（共 10 列）**

| 字段 | 类型 | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT AI | 否 | — | 主键 |
| `station_id` | BIGINT | 否 | — | 驿站（逻辑外键 `station.id`，**非空**，与 `payroll_log.payroll_id NOT NULL` 等价定位） |
| `action` | VARCHAR(16) | 否 | — | 动作：`CREATE`/`UPDATE`/`ENABLE`/`DISABLE`（每次保存必写一条） |
| `operator_id` | BIGINT | 是 | NULL | 操作人（逻辑外键 `employee.id`） |
| `operator_name` | VARCHAR(50) | 是 | NULL | 操作人姓名快照 |
| `operator_role` | VARCHAR(20) | 是 | NULL | 操作人角色快照 |
| `before` | JSON | 是 | NULL | 变更前快照（白名单键：`{enabled,payrollDay,payrollTime,notifyEnabled,remark}`；`CREATE` 时为 NULL） |
| `after` | JSON | 是 | NULL | 变更后快照（同键，白名单，**不含凭据/个人信息**） |
| `time` | DATETIME | 否 | CURRENT_TIMESTAMP | 操作时间（**只插不改，无 `update_time`**） |
| `remark` | VARCHAR(200) | 是 | NULL | 备注 |

> **无 `is_deleted` / `update_time`**：追加型审计表纪律（同 `payroll_log` / `leave_log`，[V9__leave.sql:57-72](../hrm-server/src/main/resources/db/migration/mysql/V9__leave.sql#L57-L72)）。**只增不改、无删除入口**。

**索引**
- `PRIMARY KEY (id)`。
- `KEY idx_station_payroll_setting_log_station_time (station_id, time)` —— 按驿站查变更历史主路径（对应 I-9）。

**与 I-3 的写入关系（每次保存必写，M-9 硬要求）**
- `PUT /payroll-settings/{stationId}`（I-3）**每次保存成功即在同一事务内追加一条**：首次创建 → `action=CREATE`；`enabled` 由 `0→1` → `action=ENABLE`；`1→0` → `action=DISABLE`；其余字段变更 → `action=UPDATE`。**「启用 0→1」由 `action=ENABLE` 行承载、可追溯**。
- `before`/`after` 只写上述**白名单键**；`station_id` 与操作人已单列，不重复进 JSON。

**历史暴露结论（M-9 第三问）**：**新增查询接口 I-9**（`GET /api/v1/finance/payroll-settings/{stationId}/logs`，ADMIN），**不在 I-1/I-2 出参内嵌历史**。理由：① 留痕可查才闭环，M-9 验收需**可判定**；② 列表接口不应背负时间线（与 I-7 单据留痕查询同构）。该结论导致「新增接口」计数由 8 改 **9**（§4.1、§9、§10 末）。

**迁移安排**：本表并入 **`V21`**（与 `payroll_log` 冗余定位列、`payroll_run.attempt_date` 同批；见 §6「数据层状态」②与 §6 B1）。

***

## 4. 接口清单

> 财务域前缀 `/api/v1/finance`，错误码 94xx（[ErrorCode.java:222-233](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L222-L233)）。`api.md` 现存财务契约缺口（[api.md:191-262](api.md) §4.0 概览 65 端点未含财务段）**本方案不解决**，仅声明新增/变更端点须在 B0 批次补录进 `api.md`（见 §6）。

### 4.1 新增接口

| # | 方法 + 路径 | 角色（`@RequireRoles`） | 入参要点 | 出参要点 | 错误码 |
| --- | --- | --- | --- | --- | --- |
| I-1 | `GET /api/v1/finance/payroll-settings` | `{"ADMIN"}` | 无（可带 `stationId`/`enabled` 过滤） | 驿站列表 + 各站算薪配置（驿站名、`enabled`、`payrollDay`、`payrollTime`、`notifyEnabled`） | — |
| I-2 | `GET /api/v1/finance/payroll-settings/{stationId}` | `{"ADMIN"}` | `stationId` 路径 | 单驿站算薪配置 | `4001` 驿站不存在 |
| I-3 | `PUT /api/v1/finance/payroll-settings/{stationId}` | `{"ADMIN"}` | `enabled`、`payrollDay`（**1-31，U-05 已裁定；月末钳位**）、`payrollTime`(HH:mm)、`notifyEnabled`、`remark` | 保存后的配置。**副作用（v1.3，M-9）**：每次保存**必写一条** `station_payroll_setting_log`（`CREATE`/`UPDATE`/`ENABLE`/`DISABLE`，§3.6） | `4001`；`9407` 算薪日非法；`9408` 时间格式非法 |
| I-4 | `POST /api/v1/finance/payroll-runs/trigger` | `{"ADMIN"}` | `stationId`、`month`(yyyy-MM)（**v1.5 复-2：删除 `force` 参数**） | 本次运行结果（`runId`/`status`/`generatedCount`；或 `SKIPPED`+原因） | `4001`；`9405`→映射 `SKIPPED`；`9410` 正在运行 / 已占位 / **当日已尝试**（同日重复触发一律 `9410`） |
| I-5 | `GET /api/v1/finance/payroll-runs` | `{"ADMIN"}` | `stationId`、`month`、`status`、`triggerType`、分页 | 运行记录分页（含 `dueAt`/`status`/`skipReason`/`generatedCount`/起止时间） | — |
| I-6 | `POST /api/v1/finance/payrolls/{id}/items/add` | `{"ADMIN"}` | `itemType`(ADDITION/DEDUCTION)、`itemName`、`amount`(>0)、**`reason`（必填 2-200）** | 更新后的工资单详情（含重算合计） | `9402`；`9403` 状态不允许；`9411` item_key 重复；`9412` 事由必填 |
| I-7 | `GET /api/v1/finance/payrolls/{id}/logs` | `{"ADMIN", "STATION_ADMIN", "STAFF"}`（非 ADMIN **仅本人单** + 仅已发布及之后） | `id` 路径 | 留痕时间线。**服务端按角色裁剪**：ADMIN 返回 `action`/`operatorName`/`operatorRole`/`time`/`fromStatus`/`toStatus`/`reason`/`before`/`after`；**非 ADMIN 仅返回 `action`/`time`/`reason`/`toStatus`**（**不返回** `before`/`after`/`operator_id`/`operator_role`） | `9402`；`9404` 越权；`9403` 不可见状态 |
| **I-8** | `POST /api/v1/finance/payrolls/{id}/pay` | `{"ADMIN"}`（Q9） | `id` 路径（可选 `remark`） | 归档后的工资单详情（`status=PAID`、`paidTime`/`paidByName`） | `9402`；`9403` **状态不允许（来源非 `CONFIRMED`）**；`9413` 已发放归档 |
| **I-9** | `GET /api/v1/finance/payroll-settings/{stationId}/logs` | `{"ADMIN"}` | `stationId` 路径；可选时间范围、分页 | 该驿站算薪配置**变更历史时间线**（`action`/`operatorName`/`operatorRole`/`time`/`before`/`after`/`remark`），按 `time` 倒序 | `4001` 驿站不存在 |

> **I-6 前置条件（v1.1 修订，R11）**：允许状态 = `isItemEditable`（`DRAFT`/`REJECTED`/`PENDING_APPROVAL`/`OBJECTED`）；`itemKey` 由服务端生成、强制 `MANUAL_` 前缀（U-09）。`DRAFT`/`REJECTED` 期的 MANUAL 项在 `generate` 覆盖重建时**保留迁移**（§3.4），故**不再禁止**在 `DRAFT` 录入。**金额方向（v1.3 验收口径）**：加款计入应发、扣款计入扣项，**实发 = 应发 − 扣项**（`PayrollTotalsPolicy` 既有模型，**不改公式**）；保存后**自动重算四项合计**（§3.4；主代理解释、待用户最终确认，§7 U-16）。

> **I-7 权限说明（v1.2 修订，响应安全 M-6/REG-06）**：越权判定**必须复用** `detail()` 的**单一真源**（`employeeId == userId` + 状态 ∈ `{PUBLISHED, CONFIRMED, PAID}`，[PayrollServiceImpl.java:320-327](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L320-L327)），违规返回 `9404`/`9403`；**不得**仅校验角色而未逐单校验「本人 + 状态可见」（否则任一 STAFF 可按 id 遍历任意单据留痕 → **IDOR**）。**字段裁剪由服务端强制**（U-14）：非 ADMIN **不出参** `before`/`after`/`operator_id`/`operator_role`——**废止** v1.1「由端（前端）裁剪」表述（前端隐藏而后端照返＝越权信息泄漏）。
>
> **I-8 说明（v1.2 新增，响应技术评审必改 1）**：`pay`（`CONFIRMED→PAID`）为 Q9「管理员确认工资已发放→归档」的落地端点。前置 `status=CONFIRMED`（来源非法回既有 **`9403`** `FINANCE_PAYROLL_STATUS_INVALID`）；副作用写 `paid_by_id`/`paid_by_name`/`paid_time` + `payroll_log(action=PAY)`；进入 `PAID` 后受 §2.9 `assertMutable` 全面冻结（任何写入口回 `9413`）。`pay` 与既有 `publish` 的**静默 skip** 语义不同——**必须显式报错**，不得静默。
>
> **I-9 说明（v1.3 新增，响应安全 M-9 裁定）**：受理由 `stationId` 指定，**仅 ADMIN**（与端准入一致，§4.4）；数据源 `station_payroll_setting_log`（§3.6）。**不在 I-1/I-2 出参内嵌历史**（列表接口不背负时间线，与 I-7 单据留痕查询同构）。承载「**启用 0→1 可追溯**」与配置变更审计。

### 4.2 需变更的既有接口

| # | 端点 | 变更点 |
| --- | --- | --- |
| C-1 | `POST /api/v1/finance/payrolls/{id}/objection`（员工） | 目标状态 `PENDING_APPROVAL` → **`OBJECTED`**（[PayrollServiceImpl.java:443-455](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L443-L455) 改）。副作用：清 `confirm_time`/`publish_time`/`publisher_*`（保留现有清空口径），写 `payroll_log(OBJECTION)` |
| C-2 | `POST /api/v1/finance/payrolls/publish` | 允许来源扩展：`APPROVED`（首发）**或 `OBJECTED`（再发布）**；不限 ids 的 `month` 批量路径维持仅 `APPROVED`（避免误批再发布） |
| C-3 | `PUT /api/v1/finance/payrolls/{id}/items` | 可编辑判据由 `isEditable` 改为 **`isItemEditable`**（新增 `PENDING_APPROVAL`/`OBJECTED`）；仍仅允许改 `source=MANUAL` 项；每次改动写 `payroll_log(ITEM_UPDATE)`，**`reason` 必填 2-200（v1.2 修订，M-3/REG-03；原「可选」与 Q3 冲突）**；`before`/`after` 限 §3.2 白名单 |
| C-4 | `POST /api/v1/finance/payrolls/{id}/confirm`（员工） | 无行为变更；补写 `payroll_log(CONFIRM)` |
| C-5 | `POST /api/v1/finance/payrolls/{id}/approve` / `/submit` | 无状态变更；补写 `payroll_log(APPROVE/REJECT/SUBMIT)` |
| C-6 | `GET /api/v1/finance/payrolls`、`GET /{id}`、`GET /my` | 出参：`statusLabel` 新增两态中文；`counts` 键新增 `OBJECTED`/`PAID`；详情补 `paidTime`/`paidByName`、`actions` 随状态机扩展；`my` 的员工可见集加入 `PAID` |
| C-7 | `POST /api/v1/finance/payrolls/generate` | **必改 1（R3）**：9405 阻断判定按**驿站收敛**（新增 `stationId` 维度的可覆盖性判定），否则多驿站自动算薪相互阻断（§0.2 ⑦）。**必改 2（R11 甲）**：覆盖重建（`deleteExisting` → `createPayroll`，[PayrollServiceImpl.java:173-175](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L173-L175)）须**保留既有 `source=MANUAL` 明细**并重算合计（§3.4）。写 `payroll_log(GENERATE_AUTO/GENERATE_MANUAL)`，`after` 含 `manualKept`。**必改 3（v1.2，M-1/M-2/REG-09）**：手工 `generate` **须占用与调度相同的 `(station_id, target_month)` claim**（占位失败回 `9410`），使全部生成路径共用 Layer 0 硬防线 |

### 4.3 错误码（94xx 续号，接 `9405`）

| 码 | 枚举名（建议） | 文案 |
| --- | --- | --- |
| 9406 | `FINANCE_PAYROLL_SETTING_NOT_EXISTS` | 该驿站尚未配置算薪设置 |
| 9407 | `FINANCE_PAYROLL_SETTING_DAY_INVALID` | 算薪日取值非法（须为 1-31，月末自动钳位到当月最后一天） |
| 9408 | `FINANCE_PAYROLL_SETTING_TIME_INVALID` | 算薪时间格式非法（须为 HH:mm） |
| 9409 | `FINANCE_PAYROLL_RUN_NOT_EXISTS` | 运行记录不存在 |
| 9410 | `FINANCE_PAYROLL_RUN_IN_PROGRESS` | 该驿站该账期正在运行、已占位或当日已尝试，不可重复触发 |
| 9411 | `FINANCE_PAYROLL_ITEM_EXISTS` | 工资单项键已存在 |
| 9412 | `FINANCE_PAYROLL_REASON_REQUIRED` | 加扣款事由必填（2-200 字） |
| 9413 | `FINANCE_PAYROLL_ARCHIVED` | 工资单已发放归档，不可修改 |
| 9414 | ~~`FINANCE_PAYROLL_RUN_WINDOW_EXPIRED`~~ | **v1.3 作废（保留号段不复用）**：日粒度重试模型下无「超窗口」概念 |
| 9415 | `FINANCE_PAYROLL_RUN_DISABLED` | 该驿站未启用自动算薪 |
| 9416 | ~~`FINANCE_PAYROLL_RUN_EXHAUSTED`~~ | **v1.3 作废（保留号段不复用）**：不得设重试硬上限；连续失败改为告警（§3.3） |

> 9407/9408/9415 可选（亦可用通用 `BAD_REQUEST` + 具体文案）；但 9411/9412/9413 为**可判定业务分支**，建议独立码。错误码文案须与前端字典/`api.md` §2 同步。**9414 / 9416 于 v1.3 作废**（日粒度重试模型无「超窗口」「重试耗尽」；见 §3.3/§13）；`payroll_run.skip_code` 已移除 `EXHAUSTED`，与错误码语义同步。**（v1.5 复-2）**：`force` 参数删除后，`9414`（原「忽略补跑窗口」）**保持作废、号段不复用**；同日重复触发统一走 `9410`，**不新增错误码、不改错误码段位**。
>
> **`pay`（I-8）不新增错误码（v1.2 裁定，响应必改 1）**：来源非 `CONFIRMED` 复用既有 **`9403`**（`FINANCE_PAYROLL_STATUS_INVALID`，[ErrorCode.java:229](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L229)、[errorCode.js:150](../hrm-clients/packages/shared/src/constants/errorCode.js#L150)）；`PAID` 冻结用 `9413`。**9413 语义保持「已发放归档不可修改」不变**，不与「状态不允许（9403）」混用。

### 4.4 权限与端准入一致性

| 项 | 结论 |
| --- | --- |
| 角色口径 | 所有**新增**接口（I-1~I-6、**I-8**、**I-9**）均 `{"ADMIN"}`；I-7 为 ADMIN + 本人（既有口径） |
| boss-h5 端准入 | Q5 要求设置在 boss-h5；boss-h5 fail-closed 仅 `ADMIN`，与 I-1~I-3 的 `ADMIN` 口径一致 ✓ |
| web（PC 管理端） | 仅 `ADMIN`，可承载 I-3~I-7 的管理操作 ✓ |
| staff-h5 | 仅涉及 C-1/C-4（员工异议/确认）与 I-7（本人留痕），均为既有「本人」口径 ✓ |
| **权限放宽** | **无**。本方案不引入任何跨角色越权；`@RequireRoles` 全部沿用既有角色常量。**无「待安全评估的权限放宽项」** |
| 间接安全面（非权限放宽，仍需评估） | ① 新增**进程内定时调度 + 自动写库**（生产变更面）；② 新增 `payroll_run`/`payroll_log` 两个可含个人薪资信息的表（数据面）。二者登记为需网络安全工程师评估项（§8） |

### 4.5 通知类型扩展（Q6/Q8）

| 类型值 | 用途 | 触发点 | 接收人 |
| --- | --- | --- | --- |
| 7 | 工资单待审核 | 自动算薪生成**并自动提交**（落 `PENDING_APPROVAL`，§2.10）后（`notify_enabled=1`） | **管理员**（Q8，单一真源 `findAdminEmployeeIds()`） |
| 8 | 工资单已发布 | `publish` 成功后 | **员工本人**（Q8：发布后才推员工） |
| 9 | 工资单异议退回 | `objection` 后 | **管理员**（待处理） |

- `biz_type='payroll'`、`biz_id=payroll.id`（对齐 `sendSystem` 契约，[NotificationService.java:50](../hrm-server/src/main/java/com/qiujie/service/notification/NotificationService.java#L50)）。
- **实现约束（v1.2 修订，响应安全 M-7/REG-07/REG-08）**：**不得**把 `7/8/9` 并入公告白名单 `PUBLISH_TYPES`（现 `1..6`，[NotificationServiceImpl.java:44](../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L44)）。改为 **`sendSystem` 使用独立白名单 `SYSTEM_TYPES = {7,8,9}`** 放行；**公告发布路径 `publish()` 的白名单维持 `1..6` 不变**——否则 ADMIN 可经 `POST /notifications/publish`（`ALL/STATION/EMPLOYEE` 扇出、`title/content` 任意）**仿冒**薪资通知（应用内钓鱼）。**属代码改动，不改表结构**（`type` 为 TINYINT，无需迁移）；`notification.type` 列 COMMENT 与 `db.md` 说明同批更新。
- **接收人单一真源（v1.2）**：新增 `findAdminEmployeeIds()`（`employee.role=ADMIN AND status=1`）作为「管理员集合」**唯一真源**；**禁止**复用 `resolveTargets("ALL"/"STATION")`（会误推站长/员工，[NotificationServiceImpl.java:245-246](../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L245-L246)）。
- **type 8 触发前置（v1.2）**：**仅当 `publish`/`republish` 成功且状态已落 `PUBLISHED` 后**触发；**禁止**在 `generate`/`approve`/`submit` 分支触发（防「草稿生成即推员工」）。断言：**未发布状态下 type 8 通知数为 0**。
- **通知异常不阻断（v1.2）**：`sendSystem` 对非法 `type`/超长文本会抛 `BusinessException`（[NotificationServiceImpl.java:184-192](../hrm-server/src/main/java/com/qiujie/service/notification/impl/NotificationServiceImpl.java#L184-L192)）；调用侧**一律 `try/catch`**，失败只写 `NOTIFY_SKIP` 留痕、不阻断主流程（防 `afterCommit` 阶段异常外溢）。
- **接收人解析（已裁定 U-07）**：「管理员」= **全部在职 `employee.role=ADMIN AND status=1`**（全局管理员，无驿站归属语义）；**不推站长、不推员工**（裁定方：主代理预裁定，§7）。
- **通知类型定稿（已裁定 U-15）**：7=工资单待审核（→管理员）、8=工资单已发布（→员工本人）、9=工资单异议退回（→管理员）。

***

## 5. 算法口径要点（骨架，四件套交算法工程师细化）

> 骨架口径；细节（含边界用例、复杂度、基准数据、**日粒度重试的参数与算法复杂度**）以 **`[算法 v1.3]`**（[algorithm-payroll-scheduling.md](algorithm-payroll-scheduling.md)，算法工程师按复评 P1 同步后的版本）为准；本节**只钉 v1.5 最终口径**（U-05/U-06 已由用户裁定，落点见 §13）。**凡涉参数处不代为定值**，一律引用 `[算法 v1.3]`（**参数名称 / 默认值 / 范围以算法 §2 为单一真源，本方案不另立名称集合**，复评 复-4）；若算法结论与本方案冲突，以「**日粒度重试**」这一用户口径为准。

| 项 | 口径（v1.5 定稿） |
| --- | --- |
| **触发判定（R4 · U-05 已裁定）** | `dueAt = ZoneId(Asia/Shanghai) 下「当月 min(payroll_day, 当月天数) 日 payroll_time」`。**`payroll_day` 范围 = 1..31，当月无该日则钳位到当月最后一天（裁定方=用户，U-05）**：例 `31→4/30`、`30→2026/2/28`、`29→2024/2/29`；保证「每月恒定有且仅有一个 `dueAt`」、支持月末结算。每 tick 对 `enabled=1 ∧ station.status=1` 驿站判定 **`now ≥ 当日 catch-up-time-of-day`（v1.5 复-3：当日触发时刻，默认继承该站 `payroll_time`；纠正 v1.4 的 `now ≥ dueAt`）** `∧ 未占位 ∧ 当日未尝试` → 触发（判据见 §3.3；**「当日未尝试」的硬防线为 DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`，应用层短路仅作优化**）。**原「候选甲/乙」二分已裁定落为甲，不再保留乙。** 月末 `dueAt` 在旧窗口模型下的窗口塌缩问题，随**日粒度重试模型（U-06）**一并消除。 |
| **幂等（R1/R2）** | ① 调度层 `payroll_run` **claim 槽位唯一键 `(station_id, claim_key)`**（§3.3，**唯一 DB 硬防线**）；② 生成层 9405（**须按驿站收敛**，§0.2 ⑦）；③ 单据层**无 DB 唯一约束**（`idx_payroll_emp_month_bill` 为普通 `KEY`，见 §3.3 风险登记 R1-A） |
| **补跑（R5 作废 / R6 部分，U-06 已裁定）** | **原「名义窗口 `[dueAt, min(dueAt+W, 次月首日))`、`W` 默认 72h」整体作废**，替换为**日粒度重试模型**：算薪日当天未成功 → **次日补跑**，失败 → **次日继续补跑**，以自然日为粒度持续重试至成功；**补跑日触发时刻 = 当日 `catch-up-time-of-day`（默认继承 `payroll_time`，v1.5 复-3），须 `now ≥` 该钟点**；**每（驿站, 账期）每自然日至多一次**（由 **DB 唯一键 `uk_attempt`** 硬保障，应用层短路仅作优化）；**`target_month` 恒为 `dueAt` 所在月**（仅执行日推后）；**不得加硬上限**，允许「连续失败 `alert-after-consecutive-fail-days` 天告警（重复节奏 `alert-repeat-interval-days`）」，**参数以 `[算法 v1.3]` §2 为准**（本方案不另立名称集合）。逐驿站串行，可设 `max-stations-per-tick` 顺延。详见 §3.3、§13。 |
| **触发类型（R8）** | `trigger_type = (now - dueAt) ≤ tickInterval ? AUTO : CATCH_UP`；仅影响可观测性标签，不影响执行语义 |
| **失败重试与僵死回收（R6，v1.5 复-1 修订）** | **自动重试不设次数上限**（U-06：持续重试至成功）；相邻两次尝试间隔 = **次一自然日**（同日不再重试；该日粒度闸门由 **DB 唯一键 `uk_attempt`** 硬保障）。僵死 `RUNNING`（`start_time < now - running-timeout-minutes`）→ **回收：`claim_key=NULL`（释放占位）+ `status=FAILED` + `fail_reason=STALE_RECLAIMED` + `finish_time`，并触发失败告警** → 次日按日粒度闸门再试（§3.3 回收动作定义）；`stale-reclaim-enabled` **默认 `true`**（v1.5 更正，与算法一致）；阈值 `running-timeout-minutes` 外置（默认 30，**值以 `[算法 v1.3]` 为准**）。**`max-retry-per-run` / `EXHAUSTED` / `9416` 于 v1.3 作废。** |
| **异常处理（隔离）** | 逐驿站 try/catch：单驿站失败写 `payroll_run=FAILED` + `fail_reason` + 应用 error 日志，**不阻断其它驿站** |
| **`SKIPPED` 归类（R9）** | **仅**「已决定执行但被业务正常拦截」记 `SKIPPED`（9405→`BLOCKED_9405`、配置非法→`CONFIG_INVALID`、DRAFT 保护→`DRAFT_PROTECTED`），**不重试**（**v1.3 移除 `EXHAUSTED`**）；**「未启用 / 未到点 / 当日已尝试 / 已占位」不产生运行记录**（属「未进入执行」，避免污染运行历史） |
| **时区与落库（R7）** | 判定与落库统一 `Asia/Shanghai`：调度链路写 `ZonedDateTime.now(zone).toLocalDateTime()`；人工链路 `LocalDateTime.now()` 以部署前置 `-Duser.timezone=Asia/Shanghai` 兜底（§1 ADR-01） |
| **规则来源（R12）** | 自动算薪不传 `ruleId` → 取**第一个启用规则**（[PayrollServiceImpl.java:556-563](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L556-L563)）。按驿站差异化规则属 schema 变更，登记 `TODO(扩展)`，本期不做 |
| **数据丢失保护（R11）** | `generate` 覆盖重建**保留既有 `source=MANUAL` 明细**（迁移 + 重算合计），详见 §3.4 |
| **性能量级估算** | 设驿站数 `S`、每驿站在职员工数 `E`、规则项数 `R`。单驿站单次生成 ≈ `E` 次工资单 insert + `E×R` 次明细 insert（当前 `createPayroll` 逐行 insert，[PayrollServiceImpl.java:526-538](../hrm-server/src/main/java/com/qiujie/service/finance/impl/PayrollServiceImpl.java#L526-L538)）。例：`S=50, E=20, R=8` → 单驿站 ~180 行、全量 ~9000 行；若 `E` 达数百、`S` 达数十，则单次全量可达十万级 insert，**需评估批量插入优化**（`TODO(扩展)`，交后端 + 算法联评）。tick 自身成本 = 每轮 `S` 次配置查询（小表，走 `idx_station_payroll_setting_station`），可忽略 |
| **算薪日集中风险** | 多驿站同日同时刻会造成瞬时负载；建议**默认时间分散 + 逐驿站串行**，超阈值时给出「错峰建议」（交算法工程师评估是否需排队/限速，涉及 §11.1 R09 频控，若引入则须算法先出四件套） |
| **参数外置（硬约束，v1.5 修订）** | **参数名称 / 默认值 / 范围一律以 `[算法 v1.3]` §2 为单一真源**（复评 复-4；本方案**不另立名称集合、不代定值**）。**本方案只登记架构/部署相关项**（引用算法名）：`zone`（判定与落库时区，§1 ADR-01）、`enabled`（调度总开关，默认关，§1）、`tick-interval-ms`（轮询精度）、`stale-reclaim-enabled`（僵死回收总开关，**默认 `true`**，v1.5 复-1）、`running-timeout-minutes`（回收阈值，默认 30）、`catch-up-time-of-day`（补跑当日钟点，默认继承 `payroll_time`，复-3）、`max-stations-per-tick` / `inter-station-delay-ms`（逐驿站串行节流）、`notify-on-fail` 与告警项。全部映射 `hrm.payroll.schedule.*`；**禁止内联阈值**（反模式 A03）。v1.3 前表述的 `catch-up-window-hours` / `catch-up-window-min-hours` / `max-retry-per-run` **已随 U-06 下线**，不得复现 |
| **可解释性（v1.2 收敛，响应必改 7）** | `payroll_run` 覆盖**已进入执行**的每次尝试：`dueAt`/`triggerType`/`status`/`skip_code`/`skip_reason`/`generated_count`，**可复现「已进入执行的跳过 / 失败归因」**（为何 `SKIPPED`、为何 `FAILED`）。**边界（不宣称的能力）**：对**未进入执行**的四种情形——`enabled=0` / 驿站停用、未到点、**当日已尝试（日粒度闸门）**、`(s,m)` 已被占位（claim 短路）——**不产生运行记录**，其「为何没跑」**无法由 `payroll_run` 复现**，属**已知观测盲区**；如需可见只能由**查询层按配置 + 当前时间派生展示**（非落表），**不得**据此宣称可完整复现「为何没跑」 |

***

## 6. 批次划分（B0..B5，每批可独立验收）

| 批次 | 内容 | 依赖 | 影响端 | 需迁移 | 验收口径（可判定） |
| --- | --- | --- | --- | --- | --- |
| **B0** | 契约定稿：`api.md` 补录财务段（**既有 10 + 新增 9 + 变更 7**，新增含 v1.2 `pay` I-8 与 v1.3 `logs` I-9）；`db.md` 补 §8.6.x **四表**（含 `station_payroll_setting_log`）与 `payroll` 加列、**落档 Q-DB-7/8/9 裁定（§0.7）**；错误码 9406-9416 登记（**9414/9416 v1.3 作废**） | 无 | 无 | 否 | `api.md`/`db.md` 与本方案字段/编号逐条一致；P0.6 技术评审通过 |
| **B1** | 数据层：`V20__payroll_automation.sql`（`station_payroll_setting` / `payroll_log` / `payroll_run` + `payroll.paid_*`；**v1.1 同步已完成**）+ **`V21`（v1.2/v1.4 新增，范围见下）**：① `payroll_log` 冗余定位列（M-2②）；② **`payroll_run.attempt_date DATE NOT NULL` + 唯一键 `uk_attempt (station_id, target_month, attempt_date)` + 删除冗余索引 `idx_payroll_run_station_month`（U-06 / v1.4 主代理裁定）**；③ **新表 `station_payroll_setting_log`（M-9）**；快照 `init.sql` 同步 | B0 | 无 | **是（C 档执行）** | 脚本版本号 `V20`/`V21`（仅新增，不改历史）；快照与迁移同步；回滚脚本注释齐（对齐既有体例）；`V21` 在 `V20` 之后按序执行；`attempt_date` 为 `NOT NULL` 且无默认，**`payroll_run` 建表后尚无存量（同批）故可直加**；若届时已有存量，须由数据库工程师按 C 档先给默认值/回填再收紧 |
| **B2** | 状态机与留痕：拆分判据（§2.3）、新增 `OBJECTED`/`PAID`、`isItemEditable`、`payroll_log` 全链路写入、手工加扣款 I-6、异议改 `OBJECTED`（C-1） | B1 | **web / boss-h5 / staff-h5**（共享字典单源 + mock 镜像 + 步骤条/详情硬编码分支 + 按钮）；后端 | 否 | 状态机单测覆盖 8 态动作矩阵；每次动作产生 1 条 `payroll_log`；I-6 事由必填校验命中 9412；`PAID` 下全部写入口回 9413（§2.9） |
| **B3** | 配置与调度：算薪配置 I-1~I-3（**含 I-3 写 `station_payroll_setting_log`**）、配置变更历史 **I-9**、`PayrollScheduleTicker`（`@EnableScheduling` + 开关默认关）、`payroll_run` I-4/I-5、claim 槽位、**日粒度重试模型（U-06）** | B1 | **boss-h5**（设置页）、**web**（运行记录/手工触发/配置历史）；后端 | 否 | 到点触发幂等（并发/重复触发仅 1 条占位行 `RUNNING`→`SUCCESS`）；单驿站失败不影响其它；**同一 (s,m) 每自然日至多 1 条尝试行**；失败次日自动补跑；**补跑须 `now ≥ 当日 catch-up-time-of-day`（不在凌晨 00:0x 触发，v1.5 复-3）**；**僵死 `RUNNING` 超时按 §3.3 回收（`claim_key=NULL`+`status=FAILED`+`fail_reason=STALE_RECLAIMED`+释放占位+触发告警，v1.5 复-1）**；**同日重复触发（含 I-4 手工路径）一律 `9410`（v1.5 复-2）**；I-3 每次保存产生 1 条配置留痕、`enabled 0→1` 可追溯 |
| **B4** | 通知联动：类型 7/8/9 放行（**独立 `SYSTEM_TYPES`，公告白名单不动**）；生成→推管理员（Q6/Q8）；发布→推员工；异议→推管理员；`CONFIRMED`→`pay` 归档（**I-8**） | B2 + B3 | **staff-h5**（状态展示/异议/确认）、**boss-h5**、**web**；后端 | 否 | 各节点各产生一条站内信；类型 7 收件人 ⊆ `{role=ADMIN ∧ status=1}`、未发布状态**零** type 8；`biz_type=payroll` 跳转可用；`notify_enabled=0` 时不投递且留 `NOTIFY_SKIP`；`CONFIRMED→pay` 归档留 `PAY` 记录 |
| **B5** | 联调验收与文档同步：`update-log.md`、`api.md`/`db.md` 终稿、门禁实跑、E2E（四态覆盖） | B4 | 三端 | 否 | 门禁基线不弱化；核心分支 E2E 通过（含异议再发布、发放归档） |

> **排序硬约束**：B2/B3 的**前端字典单源 + mock 镜像 + 硬编码视图**（§0.2 ⑨）必须与后端状态返回**同批或先行**，否则 `PAYROLL_STATUS[status].variant` NPE；建议 B2 先合并前端字典改动，再放后端新状态返回。

> **数据层状态（v1.2 更新为「已完成 + 仍需回改」准确态，修正技术评审必改 5）**
>
> **① 已完成（数据库工程师已按 v1.1 同步；本方案不再声称数据层为 v1.0）**
> - `V20__payroll_automation.sql`：`payroll_run.success_key` → **`claim_key`**（`CHAR(7)` 不变）+ 列 COMMENT「认领槽位 = target_month：`RUNNING`/`SUCCESS`/`SKIPPED` 写值、`FAILED` 置 NULL」；唯一键 → **`uk_payroll_run_claim (station_id, claim_key)`**；新增 **`skip_code VARCHAR(24) DEFAULT NULL`**；`station_payroll_setting.payroll_day` 注释 → **「1-31（月末钳位）」**；表 COMMENT → 「占位行（RUNNING/SUCCESS/SKIPPED）每驿站每账期至多一行」；`payroll_run` 说明同步 `is_deleted`「只增不删、无删除入口」（Q-DB-9）；回滚注释随列名/索引名更新。
> - `db.md`：§8.6.5 `payroll_day` 注释「1-31（月末钳位）」；§8.6.7 与 §9.1 Q-DB-7/**U-13** 改为 **claim 槽位语义**；§9.4 U-13 反向断言已重写为「同一 `(station_id, target_month)` 连续写两条 `RUNNING` 应触发 **1062**」。
> - `init.sql`：与 `V20` 逐字一致（`claim_key`/`skip_code`/注释）。
> - `payroll.status` 8 态 COMMENT（枚举末尾追加序）与 `paid_*` 三列**不变**（Q-DB-8 确认无需修订）。
>
> **② 仍需回改（v1.2 交数据库工程师、v1.4 追加唯一键要求，随 B1/重评；此前不得声称数据层与本版一致）**
> 1. **`V21`**（脚本名沿用 `V21__payroll_log_locator.sql`，范围扩展为三段；M-2② + U-06 + **v1.4 主代理裁定** + M-9 裁定）：① `payroll_log` 增 `employee_id BIGINT NULL` + `month CHAR(7) NULL` + `KEY idx_payroll_log_emp_month (employee_id, month, time)`；② `payroll_run.attempt_date` + **唯一键 `uk_attempt` + 删除冗余索引 `idx_payroll_run_station_month`**（见下 6）；③ 新表 `station_payroll_setting_log`（见下 7）。`init.sql` 同步；`payroll_log`/`payroll_run` 无存量、无需回填。
> 2. `db.md` §8.6.3：`idx_payroll_emp_month_bill` 用途措辞「生成幂等」→「生成幂等（**应用层**，非 DB 唯一）」，避免与「该索引为普通 KEY」事实混淆（技术评审 L1）。
> 3. `V20` 头部引用「§3（表设计，v1.0 初稿，待技术评审）」→ 随 v1.2 版本号更新（技术评审 L3）。
> 4. `db.md` §9.1 Q-DB-9：落档技术评审对「只增不删」前提的确认结论（技术评审 L2，可与必改 6 的救急出口一并落定）。
> 5. `db.md`/`init.sql`：`payroll_log` 三列变更随 `V21` 同步；`notification.type` COMMENT 随通知白名单拆分（§4.5）同步。
> 6. **`V21` 增 `payroll_run.attempt_date DATE NOT NULL`**（U-06）并**新增 DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`**（v1.4 主代理裁定：日粒度硬防线），**同时 `DROP INDEX idx_payroll_run_station_month`**（列完全相同、保留即冗余；其运行历史 / 连续失败天数统计由 `uk_attempt` 的最左前缀覆盖）——即 `V21` 以 `DROP INDEX idx_payroll_run_station_month` + `ADD UNIQUE KEY uk_attempt …` 收敛为**单一索引**（§3.3）。
> 7. **`V21` 新增表 `station_payroll_setting_log`**（M-9，§3.6）+ `init.sql` 同步；与 `payroll_log` 同构、无 `is_deleted`/`update_time`；`db.md` 补该表结构段。
>
> **`V21` 范围（v1.4 汇总）**：① `payroll_log` 冗余定位列 + `idx_payroll_log_emp_month`；② `payroll_run.attempt_date` + 唯一键 `uk_attempt` + **删除冗余索引 `idx_payroll_run_station_month`**；③ 新表 `station_payroll_setting_log` + `idx_station_payroll_setting_log_station_time`。三项同批，脚本版本号不变（仍 `V21`）；②的列变更与③的新表按 v1.4 裁定由数据库工程师**并行**编写、内部分段注释区分。

***

## 7. 口径裁定项清单（v1.3：U-05 / U-06 已由用户裁定；其余落定）

> 裁定来源：**用户裁定**（需求口径 Q1~Q9 见 §0.3；**业务口径 U-05/U-06 于 v1.3 由用户裁定**）；**主代理预裁定**（U-01~U-04、U-07~U-15）；**主代理解释、待用户最终确认**（U-16）。U-05/U-06 属业务口径（`项目规则1.md` §11.4、调度规则 P1），**已获裁定、实现可开工**；U-16 为解释性口径，正式生效前以用户确认为准。落点与旧口径作废清单见 §13。

| # | 事项 | 最终裁定 | 裁定方 | 落点 |
| --- | --- | --- | --- | --- |
| U-01 | 终态命名 | `PAID`（label「已发放」），即归档态，**不另设 `ARCHIVED`** | 主代理预裁定 | §2.1、§2.5 |
| U-02 | 异议（`OBJECTED`）与驳回（`REJECTED`）是否合并 | **不合并**（动作主体不同，「谁退的」须可区分） | 主代理预裁定 | §2.4 |
| U-03 | 归档冻结程度 | `PAID` **绝对冻结**（禁止 `is_deleted` 置位、禁止任何金额与状态变更，统一 9413） | 主代理预裁定 | §2.5 |
| U-04 | 退回粒度与再发布 | **单员工单据级**；再发布**直发**为默认路径，保留 `submit` 二次审批为可选 | 主代理预裁定 | §2.2 |
| U-05 | 算薪日范围 | **`payroll_day` 允许 1..31；当月无该日时钳位到当月最后一天执行**（用户原话「算薪日按 1-31」） | **用户（v1.3）** | §3.1、§4.1 I-3、§5、§13 |
| U-06 | 补跑模型 | **日粒度重试模型**：算薪日当天未成功 → 次日补跑；补跑失败 → 次日继续补跑，以自然日为粒度持续重试直到成功；**同一驿站同一账期每自然日至多尝试一次**；`target_month` 恒为 `dueAt` 所在月；允许告警/可配熔断补偿，**不得加硬上限**（用户原话「补跑窗口只允许次日补跑，补跑失败则次日再次补跑」）。**（v1.5 复-4：落地参数按 `[算法 v1.3]`——仅告警项落地，熔断补偿不预设参数）** | **用户（v1.3）** | §3.3、§4.1 I-4、§5、§13 |
| U-07 | 通知接收人 | **全部在职 ADMIN**（`role=ADMIN AND status=1`）；不推站长、不推员工 | 主代理预裁定 | §4.5 |
| U-08 | 员工可见性 | `PAID` **加入**员工可见集；`OBJECTED` **不加入**（内部处理态） | 主代理预裁定 | §2.7 |
| U-09 | `item_key` 生成方 | **服务端生成**（防客户端伪造/冲突），强制 `MANUAL_` 前缀 | 主代理预裁定 | §3.4 |
| U-10 | 事由存储 | 复用 `payroll_item.detail` + `payroll_log.reason`，**不改 V8 表结构** | 主代理预裁定 | §3.2、§3.4 |
| U-11 | crontab 二级兜底 | **不引入**（保持单一调度真源） | 主代理预裁定 | §1 ADR-01 |
| U-12 | 多实例应对 | 登记为 `TODO(扩展)` 架构债；本期以 claim 幂等兜底 | 主代理预裁定 | §1、§3.3、§9 |
| U-13 | 存量回填 | **不回填**（零风险，老单走老路径） | 主代理预裁定 | §2.7 |
| U-14 | 员工可见留痕字段 | 员工仅见 `action`/`time`/`reason`/`toStatus`；`before`/`after` 内部快照仅 ADMIN 可见 | 主代理预裁定 | §4.1 I-7 |
| U-15 | 通知类型 | 7=工资单待审核（→管理员）、8=工资单已发布（→员工本人）、9=工资单异议退回（→管理员） | 主代理预裁定 | §4.5 |
| U-16 | 加扣款金额方向（v1.3） | 加款计入应发（`addition_total`/`gross_amount` 增大）、扣款计入扣项（`deduction_total` 增大），**实发 = 应发 − 扣项**（既有 `PayrollTotalsPolicy`，**不改公式**）；每笔**必填事由 + 记录操作人 + 自动参与金额计算** | **主代理解释，待用户最终确认** | §3.4、§4.1 I-6、§13 |

**v1.1 新增待评估项（登记 `TODO(扩展)`，非 U 编号）**：

| 编号 | 事项 | 现状 | 归属 |
| --- | --- | --- | --- |
| T1 | **按驿站差异化计薪规则**（算法 R12） | 本期维持「全局第一个启用规则」；差异化属 schema 变更 | 架构评估 + 用户裁定 |
| T2 | **`payroll` 单据级唯一键 + 存量查重**（算法 R1） | 本期**不加**（§3.3 风险登记 R1-A）；是否实施期补，交技术评审决定 | 技术评审 + 数据库 |
| T3 | **`payroll_run` 逻辑删除后重跑**（Q-DB-9） | 本期无删除入口；若未来需要，须改生成列式部分唯一并另立版本 | 架构 + 数据库 |
| T4 | **多实例 / 僵死回收误判的并发重复风险**（算法 D1，v1.2 裁定不加列） | 本期不加 `owner_instance_id`/`heartbeat_at`；以单实例 + `fixedDelay` 不重叠（无并发回收者 B）+ 含 `DRAFT` 保护性 `SKIPPED` + `stale-reclaim-enabled=true`（回收安全，§3.3）兜底（§3.3 D1 裁定）；**水平扩容前**须补选主/分布式锁或加列并重评 | 架构 + 数据库（扩容前） |
| T5 | **受限项：配置修正后重跑已占位账期 / `PAID` 误发放冲正**（v1.2 新增，响应必改 6） | 本期均**不支持**（占位永久性 + `PAID` 绝对冻结）；**同日重复触发一律 `9410`（v1.5 删除 `force`，复-2，无绕过入口）**；须重跑/冲正时由主智能体评估另立迁移或补偿机制（C 档） | 架构 + 数据库 + 主智能体 |

***

## 8. 与既有契约的冲突、评审与安全评估路径

1. **契约冲突**：本方案对 `payroll` 状态机、`objection` 语义、`generate` 幂等判定均**修改既有行为**（C-1/C-2/C-7），并对 `payroll_run` 唯一键语义（R2）、`payroll_day` 范围（R4）作出裁定。据调度规则「设计判断与 `api.md` / `db.md` 冲突时停下回报主智能体」，本方案**不自行落改**：B0 批次须先完成 `api.md`/`db.md` 补录（含 §0.7 的 Q-DB-7/8/9 裁定落档）并经技术评审，再进入 B1。**另**：v1.1 明确「`payroll` 单据级**不加**唯一键」（§3.3 风险登记 R1-A），属**已知残余风险**，须评审确认是否接受。**（v1.4 更新）** 数据层三方（`V20` / `db.md` / `init.sql`）的 **v1.1 同步已完成**；`V21` 为 v1.2/v1.4 新增，**范围三段**（`payroll_log` 冗余定位列 + `payroll_run.attempt_date` + 唯一键 `uk_attempt`（并删除冗余索引 `idx_payroll_run_station_month`）+ 新表 `station_payroll_setting_log`，§6「数据层状态」②）。**U-05/U-06 已由用户裁定**，本方案据此定稿：`payroll_run` 需增 `attempt_date`（V21）属结构变更，随 B0/B1 落档；**v1.4 追加**：`attempt_date` 与 `(station_id, target_month)` 组成唯一键 `uk_attempt`（日粒度硬防线，主代理裁定，§3.3）；`9414`/`9416` 作废。
2. **P0.6 / L8 技术评审闸门（前置）**：本方案为方案阶段产物，**须经技术评审工程师六维评估**（依据充分性 / 边界合理性 / NFR 覆盖 / 复杂度论证 / 假设与风险登记 / 契约一致性），结论「通过 / 有条件通过」方可报主智能体审批；「打回」退回本架构师修订后重评。
3. **P0.5 安全评估（并行，非替代）**：两项间接安全面登记为需网络安全工程师出结论项——
   - ① 新增**进程内定时调度与自动写库**（生产变更面；涉自动执行、失败隔离、幂等失效风险）；
   - ② 新增 `payroll_run`/`payroll_log` 含个人薪资信息（数据面；涉访问收敛、`fail_reason`/`before/after` 是否含敏感信息、日志脱敏）。
   安全结论优先于进度；判高风险时升级用户裁定，主智能体不得直接放行（P0.5 / L7）。
4. **必改项（本方案已自认，交评审复核）**：
   - **M1**：`generate` 9405 判定按驿站收敛（§0.2 ⑦ / C-7）——**不改则多驿站自动算薪不可用**；
   - **M2**：拆分「可编辑」判据（§2.3）——**不改则 Q6「审核时可改」与 Q9「异议后可改」无法成立**；
   - **M3**：前端 `PAYROLL_STATUS` 共享字典单源 + mock 镜像 + 硬编码视图（步骤条/详情）同步（§0.2 ⑨）——**不改则新状态页面 NPE 或步骤条错乱**；
   - **M4（v1.1）**：`payroll_run` 唯一键语义由「仅 `SUCCESS` 写哨兵」升级为 **claim 槽位**（`success_key`→`claim_key`，§3.3 / 算法 R2）——**不改则重叠 tick / 多实例可双执行 → 重复工资单**；
   - **M5（v1.1）**：`generate` 覆盖重建须**保留既有 `source=MANUAL` 明细**（§3.4 / 算法 R11 甲）——**不改则 `DRAFT` 期手工加扣款被静默物理清除**；
   - **M6（v1.1，v1.2/v1.4 状态更新）**：`V20`/`db.md`/`init.sql` 的 v1.1 同步**已由数据库工程师完成**；仍需回改项见 §6「数据层状态」②，并**新增 `V21`**（`payroll_log` 冗余定位列，M-2②；`payroll_run.attempt_date` + **唯一键 `uk_attempt`** + 删除冗余索引 `idx_payroll_run_station_month`，v1.4）——**不改则 `V21` 缺失、留痕孤儿问题不闭环、日粒度硬防线缺位**；
   - **M7（v1.2，响应技术评审必改 1）**：补 `pay` 端点（§4.1 I-8）——**不改则 Q9「确认发放→归档」无落地路径**；
   - **M8（v1.2，响应安全 M-1/M-3/通知隔离）**：全部生成路径占用同一 claim（§3.3）、统一终态写守卫 `assertMutable`（§2.9）、通知白名单拆分与 type 8 前置（§4.5）——**不改则 Layer 0 未覆盖手工路径 / `PAID` 冻结可被新入口绕过 / 薪资通知可被公告端点仿冒**；
   - **M9（v1.3，响应安全 M-9 裁定）**：新增 `station_payroll_setting_log` 配置变更审计表（§3.6）、I-3 每次保存必写、新增 I-9 查询（§4.1）、并入 `V21`——**不改则「算薪配置变更 / 启用 0→1」不可追溯，生产启用受阻**。
5. **升级路径**：若评审/安全结论与本方案设计判断冲突，或涉 C 档动作（B1 迁移执行属 C 档），**停下回报主智能体**，不自行推进。

***

## 9. 结论摘要

| 项 | 结论 |
| --- | --- |
| ADR-01 调度 | **进程内 Spring `@Scheduled` 轮询（默认 10 min，默认关闭）+ 手工触发端点兜底**；不引入 crontab/Quartz；时区权威 `Asia/Shanghai`（判定与落库同源，R7）；逐驿站独立事务、通知提交后触发；多实例以 **claim 唯一键**兜底（选主/分布式锁登记架构债） |
| 状态机目标态 | 既有 6 态 + **`OBJECTED`（异议退回）** + **`PAID`（已发放/归档终态）**；**拆分「明细可编辑」与「可被生成覆盖」两判据**消解 Q6/Q9 矛盾；发布后不可删除；**枚举序定稿为末尾追加**（Q-DB-8） |
| 新增表 | `station_payroll_setting`（驿站算薪配置）、`payroll_log`（追加型审计留痕，**v1.2 增 `employee_id`/`month` 冗余定位列**）、`payroll_run`（运行记录与 **claim 槽位**批次幂等，**v1.3 增 `attempt_date`，v1.4 增唯一键 `uk_attempt`（日粒度硬防线）**）、**`station_payroll_setting_log`（v1.3 新增，配置变更审计，M-9）**；另 `payroll` 加 3 列 |
| 幂等（v1.2 定稿） | ① 调度层 **claim 槽位唯一键 `(station_id, claim_key)`**（唯一 DB 硬线）；② 生成层 9405（按驿站收敛）；③ 单据层**无 DB 唯一约束**（R1 降级，风险登记 R1-A）；**④ 全部生成路径（含手工 `generate`）须占用同一 claim（v1.2，M-1/REG-01，生产启用自动算薪硬前置）** |
| `dueAt`（**U-05 已裁定（用户）**） | `payroll_day` = **1..31**；`dueAt = 当月 min(payroll_day, 当月天数) 的 payroll_time (Asia/Shanghai)`（**当月无该日即钳位到当月最后一天**）。**可开工**（§3.1/§5/§7/§13） |
| 补跑（**U-06 已裁定（用户）**） | **日粒度重试模型**：算薪日当天未成功 → 次日补跑，失败 → 次日继续补跑，自然日粒度持续重试至成功；**补跑日触发时刻 = 当日 `catch-up-time-of-day`（默认继承 `payroll_time`，v1.5 复-3；纠正 v1.4 的 `now ≥ dueAt`）**；**每自然日至多一次**（**DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)` 硬保障，应用层短路仅作优化**，v1.4）；`target_month` 恒为 `dueAt` 所在月；**不设硬上限**，允许连续失败告警（`alert-after-consecutive-fail-days` 等，**以 `[算法 v1.3]` 为准**）。原 72h 窗口口径**作废**（§3.3/§5/§13） |
| 手工项保护（v1.2 定稿） | `generate` 覆盖重建**保留 `source=MANUAL` 明细**（迁移 + 重算合计，R11 甲）；**自动路径对含 `DRAFT` 的 `(s,m)` 一律保护性 `SKIPPED`**（v1.2 采纳算法 D4），`DRAFT` 覆盖重建仅走手工 `generate` |
| 接口 | **新增 9 个**（I-1~I-9，**I-8=`pay`**、**I-9=配置变更历史（M-9）**）+ **变更 7 项**（C-1~C-7）；错误码续号 `9406`~`9416`（**9414/9416 v1.3 作废**）；`pay` 复用既有 `9403`、冻结 `9413`。**（v1.5 复-2）删除 `force` 参数不改接口数量**（I-4 仍为新增接口，仅入参收敛）；**总数口径 = 既有 10 + 新增 9 + 变更 7，逐条可数（§10 末）** |
| 批次 | **B0~B5 共 6 批**（B1 需迁移 `V20`+`V21`，C 档执行；`V20` v1.1 同步**已完成**，`V21` v1.2/v1.4 新增，含 `payroll_log` 定位列 + `payroll_run.attempt_date` + 唯一键 `uk_attempt` + 配置审计表） |
| 裁定项 | **U-05/U-06 已由用户裁定**（业务口径，§7/§13）；U-01~U-15 其余落定；**U-16（加扣款金额方向）主代理解释、待用户最终确认**；新增 5 项 `TODO(扩展)`（T1 差异化规则 / T2 单据级唯一键 / T3 逻辑删除后重跑 / **T4 多实例与僵死回收误判** / **T5 配置修正后重跑已占位账期与 `PAID` 冲正受限项**） |
| 安全面 | **无权限放宽**；登记 2 项间接安全评估（调度生产变更面 / 新表数据面） |
| 终态守卫（v1.2） | 统一 `assertMutable(payroll)`：`PAID`→9413，覆盖**全部写入口**（含 `pay`、`generate` 覆盖重建、`deleteExisting`）（§2.9，M-3） |
| 留痕可检索（v1.2） | `payroll_log` 增冗余定位列 `employee_id`+`month`（另立 `V21`），防覆盖重建后留痕成孤儿（M-2②/M-4） |
| 通知隔离（v1.2） | `sendSystem` 独立 `SYSTEM_TYPES{7,8,9}`；公告白名单维持 `1..6`；type 8 仅 `PUBLISHED` 后触发；管理员接收人单一真源（§4.5，M-7） |
| 观测边界（v1.3） | `payroll_run` 只复现「已进入执行」的归因；「未启用 / 未到点 / **当日已尝试** / 已占位」为**已知观测盲区**（§5，必改 7） |
| 重试模型（v1.5） | 日粒度重试（U-06）：次日补跑、失败次日再补跑、**每自然日至多一次（DB 唯一键 `uk_attempt` 硬保障，应用层短路仅作优化）**；**补跑日须 `now ≥ 当日 catch-up-time-of-day`（复-3）**；无硬上限，连续失败告警（以 `[算法 v1.3]` 为准）；**僵死 `RUNNING` 超时回收（`stale-reclaim-enabled` 默认 `true`，置 `FAILED`+`STALE_RECLAIMED`+释放占位+告警，复-1）**；**同日重复触发一律 `9410`（删 `force`，复-2）**（§3.3） |
| 配置留痕（v1.3） | 新增 `station_payroll_setting_log`（追加型、无 `is_deleted`/`update_time`）；I-3 每次保存必写、`enabled 0→1` 可追溯；新增 I-9 查询（§3.6，M-9） |
| 复评闭环（v1.5） | 复评新增必改 **复-1~复-4** 逐条闭环（§14）：回收默认 `true` + 完整动作（复-1）、删 `force`（复-2）、补跑触发时刻统一（复-3）、前端补 25 处 + 参数单一真源（复-4）；**放行前置 P1~P6 的方案层落点见 §14.2** |

***

## 10. 对技术评审 10 条必改的逐条响应表（v1.2 新增；v1.3 更新）

> 来源：[tech-review-payroll-automation.md](tech-review-payroll-automation.md) §四「必改项清单（1~10）」与 §六「退回对象」。逐条给 **v1.3 结论 / 落点 / 验收标准满足方式**，不留悬空。级别取报告原文。

| 必改号 | 级别 | 退回对象（报告） | v1.3 结论 | 落点 | 验收标准满足方式 |
| --- | --- | --- | --- | --- | --- |
| **1** | 高 | 架构师 | **已闭环** | §4.1 **I-8**、§2.2、§2.5、§2.9、§6 B0/B4、§9 | §4.1 增列 `POST /api/v1/finance/payrolls/{id}/pay`（`{"ADMIN"}`、前置 `CONFIRMED`、副作用 `paid_*` + `PAY`、非 `CONFIRMED`→`9403`、`PAID`→`9413`）并补入 B0 `api.md` 补录清单；文中 `pay` 动作与端点**一一对应**（§6 动作矩阵闭合核对见下必改 6） |
| **2** | 高 | 数据库 | **已闭环（数据层已完成 + 仍需回改清单）** | §6「数据层状态」①②、§8-1 | ① `V20`/`db.md`（§8.6.5/§8.6.7/§9.1 Q-DB-7/U-13）/`init.sql` 的 v1.1 同步**已完成**（本方案不再称数据层为 v1.0）；② 列出**仍需回改项**：新增 `V21`、`db.md` §8.6.3 措辞、`V20` 头部引用、`db.md` Q-DB-9 落档、`notification.type` 注释 |
| **3** | 高 | 架构师 | **已闭环（v1.5 计数更新为 25 处）** | §2.8、§0.2⑨、§6「排序硬约束」 | 补全前端影响面清单（**25 处**：v1.2 补 22 处，含 4 个 NPE 现场 `PayrollDetailTable.vue:99`/`PayrollObjectionsPanel.vue:40`/`employee/detail/index.vue:116`/`PayrollDetailDrawer.vue:116`、mock `financeStore.js:48/50` 等、`routes/finance.js:131/152`、`verify-mock.mjs:5470/5371`、`todo.js`；**v1.5 复-4 再补 3 处 staff 侧** `staff/payroll.vue:93`/`MyPayrollCard.vue:49`/`todoGroups.js:21`）；沿用「字典 + mock 镜像先行、后端新状态后返回」顺序；验收口径写入 §2.8 末 |
| **4** | 高 | 算法 | **已闭环（算法整改 + 本方案收口）** | §3.3（claim 三处一致 / D1 裁定 / D4 采纳）、§2.8、§9 | 算法已在 `algorithm-payroll-scheduling.md` §1.5.1/§1.5.2 补 W1/W2 时序与处置；本方案：**确认采纳 D4**（含 `DRAFT` 保护性 `SKIPPED`）、**裁定 D1 不加列**、表述加限定「至多一个 RUNNING/SUCCESS **在无僵死回收误判且手工路径串行前提下**」（§3.3） |
| **5** | 中 | 架构师 | **已闭环** | §2.10、§2.2、§4.5、§6 B2/B4 | 明确定义**自动算薪生成后自动 `submit`、落 `PENDING_APPROVAL`**（选项 a）；通知 7 文案「工资单待审核」与状态一致；同步 §2.2 动作矩阵、§4.5 触发点、B2/B4 验收口径 |
| **6** | 中 | 架构师 | **已闭环 + 受限项登记（v1.5：`force` 已删除，复-2）** | §3.3（同日重复触发表）、§2.5、§7 **T5**、§13.2/§13.5 | ① **v1.5 删除 `force` 参数**：I-4 入参移除、**同日重复触发一律 `9410`**（`uk_attempt` 硬拒绝）；`force` 的 v1.3「跳过日粒度闸门」/v1.4「仅跳过应用层短路」两代语义**整体作废**（复评 §三 3.3 证明其无观测差异）；② `PAID` **误发放冲正本期不支持**，明确登记为受限项 `TODO(扩展)`（T5），不沉默 |
| **7** | 中 | 架构师 | **已闭环** | §3.4、§10 | `V8:38` 伪既有事实表述已改写为「**本方案新增的建设性约定**」（该行仅声明 `item_key VARCHAR(40)`，无前缀约定）；全文同类无源引用自查通过 |
| **8** | 中 | 算法 | **已闭环（算法整改 + U-06 已裁定）** | §5（触发/补跑两行）、§3.3、§13 | 算法已补 §1.4.1 量化验算与风险登记 R-W2；本方案 §5 据 **U-06 用户裁定**改为**日粒度重试模型**（v1.5 复-3 补触发时刻 `catch-up-time-of-day`），「窗口塌缩」问题随之消除；参数值引用 `[算法 v1.3]`，不代定 |
| **9** | 中 | 架构师 | **已闭环** | §5「可解释性（v1.2 收敛；v1.3 盲区项随 U-06 调整）」、§9「观测边界」 | 收敛为「可复现**已进入执行**的跳过/失败归因」；对「未进入执行」（未启用 / 未到点 / **当日已尝试** / 已占位）明确为**已知观测盲区**，给出查询层派生展示建议 |
| **10** | 中 | 架构师 | **已闭环（v1.3 已获用户裁定）** | §7（抬头 + U-05/U-06 行）、§3.1、§5、§9、§0.5、§13 | **U-05/U-06 已由用户裁定**（裁定原文与落点见 §13）：U-05 = `payroll_day` 1..31 + 月末钳位；U-06 = 日粒度重试模型。原「待用户裁定」标注**全部改为「已裁定（裁定方=用户）」**，无残留；其余 U 项（含 U-03/U-07）落定状态见 §7 |

**动作矩阵 ↔ 接口闭合核对（响应必改 6）**：`submit`→C-5、`approve`/`reject`→C-5、`publish`（含再发布）→C-2、`confirm`→C-4、`objection`→C-1、**`pay`→I-8**、`item-add`→I-6、`item-update`→C-3，**8 个动作全部有端点**，无悬空动作。
**接口计数最终口径（v1.5 复核）**：**新增 9 个**（I-1 `GET payroll-settings`、I-2 `GET payroll-settings/{id}`、I-3 `PUT payroll-settings/{id}`、I-4 `POST payroll-runs/trigger`、I-5 `GET payroll-runs`、I-6 `POST payrolls/{id}/items/add`、I-7 `GET payrolls/{id}/logs`、I-8 `POST payrolls/{id}/pay`、**I-9 `GET payroll-settings/{id}/logs`（M-9 新增）**）+ **变更 7 项**（C-1~C-7），逐条可数；§6 B0 与 §9 已同步为「既有 10 + **新增 9** + 变更 7」。**接口清单因 M-9 而变：新增 1 个（I-9），计数由 v1.2 的 8 改 9。** **（v1.5 复-2）删除 `force` 参数不改接口数量**——I-4 仍属「新增接口」，仅入参收敛（去掉可选 `force`）；**接口总数（既有 10 + 新增 9 + 变更 7）与错误码段位（9406~9416，9414/9416 作废）均不变，逐条可数。**

***

## 11. 对安全评估 10 条必做项的落地表（v1.2 新增；v1.3 更新 M-9）

> 来源：[security-payroll-automation-review.md](security-payroll-automation-review.md) §3「必做项清单（M-1~M-10）」与 §2「通知隔离」。逐条给 **方案层落点 / 是否属实现层待编码**。安全结论「有条件放行」，其硬阻断（M-1/M-2 未闭环前禁止 B1 迁移执行与生产启用自动算薪）在本方案相应落点已标明。

| M 号 | 级别 | 方案层落点（v1.2） | 是否属实现层待编码 |
| --- | --- | --- | --- |
| **M-1**（claim 三处一致） | 高 | §3.3「claim 协议三处口径一致 + 全部生成路径占用同一 claim」：方案 ↔ 数据层（已完成）↔ U-13 断言（1062）三处一致，DB 工程师出三方对读记录 | **部分实现层**：三处产物已齐（数据层完成）；U-13 断言脚本、并发验证须实现/测试；**生产启用自动算薪硬前置** |
| **M-2**（全路径入 claim / 服务端裁剪 / 留痕内容） | 高 | ① 全路径入 claim → §3.3 + §4.2 C-7；② I-7 服务端裁剪 + `detail()` 单一真源 → §4.1 I-7 说明；③ `payroll_log` 冗余定位列裁定**加**（`employee_id`+`month`，另立 `V21`）→ §3.2；④ 脱敏 → §3.2/§3.3 | **部分是**：②③④ 契约/表设计在方案层定稿；DL 编码（DTO 裁剪、claim INSERT、`V21` 落地）属实现层 |
| **M-3**（统一终态写守卫） | 中 | §2.9 `assertMutable(payroll, action)`：`PAID`→9413，覆盖全部写入口（含 `pay`/覆盖重建/`deleteExisting`）；`publish` 非允许来源不静默 | **实现层待编码**：守卫抽取 + 单测（方案已定义收口与验收标准） |
| **M-4**（审计链不断裂） | 中 | §3.2 冗余定位列 `employee_id`+`month` + 索引（另立 `V21`），使留痕脱离已删 `payroll_id`；`payroll_id` 注释标孤儿风险 | **部分是**：列/索引设计定稿（V21）；写入侧（记录 `employee_id`/`month`）属实现层 |
| **M-5**（`fail_reason` 脱敏） | 中 | §3.3「`fail_reason` 脱敏可执行化」：服务端构造（异常类名 + 错误码 + 白名单文案），禁拼参数/业务数据/凭据，字符安全截断；`scrub` 仅兜底 | **实现层待编码**：构造器 + 截断 + 单测 |
| **M-6**（I-7 裁剪 + 单一真源） | 中 | §4.1 I-7「服务端按角色裁剪」+ I-7 说明（复用 `detail()` 单一真源；非 ADMIN 不返 `before`/`after`/`operator_*`；废止「由端裁剪」） | **部分实现层**：契约已定稿；DTO 裁剪与越权判定实现属编码 |
| **M-7**（通知隔离 + 最小接收人 + 落定后触发） | 中 | §4.5：`SYSTEM_TYPES{7,8,9}` 独立、公告白名单维持 `1..6`；`findAdminEmployeeIds()` 单一真源；type 8 仅 `PUBLISHED` 后触发；`try/catch` 不阻断 | **实现层待编码**：白名单拆分、接收人解析、触发前置、异常兜底 |
| **M-8**（`PAID` 单一写守卫 + 非预期态拒绝） | 中 | §2.9（同 M-3）；`isItemEditable`/`isOverwritable`/`actionsOf` 对 `PAID` 显式取值；`publish` 非允许来源返回错误码 | **实现层待编码 + 单测** |
| **M-9**（算薪配置变更留痕） | 中 | ✅ **已裁定 + 落点（v1.3，主代理 M-9 裁定）**：**新增追加型审计表 `station_payroll_setting_log`**（§3.6，与 `payroll_log` 同构、无 `is_deleted`/`update_time`，字段 `station_id`/`action`/`operator_*`/`before`/`after`/`time`/`remark`，索引 `(station_id, time)`）；**I-3 每次保存必写一条**（`CREATE`/`UPDATE`/`ENABLE`/`DISABLE`），**「启用 0→1」可追溯**；新增 **I-9** 查询接口（`GET payroll-settings/{stationId}/logs`，ADMIN），**不在 I-1/I-2 内嵌历史**；并入 **`V21`**（§6） | **部分是**：表/索引/接口契约在方案层定稿；写入侧（每次保存落痕）与 I-9 编码属实现层 |
| **M-10**（迁移执行显式化） | 低 | §6 B1 验收口径（仅新增、快照同步、回滚注释）；迁移属 C 档须三步授权 | **运维/数据库实现层**：显式 `ALGORITHM=INPLACE, LOCK=NONE`、前后 DDL 比对、备份（**待核实项 R-1/R-2**） |

> **M-9 说明（v1.3 已闭环）**：M-9 未被用户本次整改清单点名，但属安全评估「生产启用自动算薪前须闭环」项。v1.2 曾如实登记为**方案层缺口**；**v1.3 经主代理裁定闭环**：新增 `station_payroll_setting_log`（§3.6）、I-3 每次保存必写、新增 I-9 查询、并入 `V21`。**「启用 0→1 必须可追溯」由该表 `action=ENABLE` 行承载。** 其余 M 项方案层落点见对应章节。

***

## 12. 自检与边界声明（v1.5）

- **只改本文档**：本 v1.5 **未修改** `algorithm-payroll-scheduling.md`、`V20`/`V21` 迁移脚本、`db.md`、`init.sql`、任何代码文件；未执行 `git`/部署/MCP；**未运行任何构建/测试/SQL**（本机无 JDK/MySQL/Redis）。
- **v1.5 变更范围（最小，复评 复-1~复-4）**：① 僵死回收默认值 **`true`** + 回收动作完整定义 + 单实例安全论证 + 场景登记（复-1）；② 删除 `force` 参数、同日重复一律 `9410`（复-2）；③ 补跑触发时刻统一为**当日 `catch-up-time-of-day`**（复-3）；④ §2.8 补 **3 处 staff 侧点位**、参数清单改以 **`[算法 v1.3]` 为单一真源**（复-4）。连带同步 §1/§3.3/§4.1/§4.3/§5/§6/§7/§9/§10/§13/§14；**U-05/U-06 落定内容、M-9 表设计、接口清单（既有 10 + 新增 9 + 变更 7）、批次划分（B0~B5）、错误码段位均不变**。
- **结论有源**：全部新增/改写结论均标 `文件:行号` 或指向本方案既有段落；**复-1~复-4 逐条对上复评原文条目**（§14）；涉算法参数处一律引 `[算法 v1.3]`（算法工程师按复评 P1 同步后的版本），不代定值；`[算法 v1.3]` 为**参数单一真源**，本方案不另立名称集合。
- **不越权**：U-05/U-06 系**用户裁定**（§13）；M-9 系**主代理裁定**；**v1.5 复-1~复-4 系复评结论 + 主代理统一裁定**——本方案只落地不改写。**U-16（加扣款金额方向）保持「主代理解释、待用户最终确认」**（§13.4，不预设公式变更）；涉及 C 档（B1 迁移执行）、安全面（claim 收敛、生产启用、僵死回收）**均标注须主智能体授权 / 安全复验**，本方案不构成任何授权。
- **待复评**：本 v1.5 须交技术评审工程师**复评/重评**（放行前置 P1~P6 的方案层落点见 §14.2）；结论「打回」不得报审，须修订后重评；**版本再变更须重评**。

***

## 13. 对用户 U-05/U-06 裁定的落地响应（v1.3 新增；v1.4 追加 §13.5）

> 本节逐条说明用户裁定的落点，并**注销** v1.1/v1.2 中已作废的旧口径表述，确保全文无矛盾残留。裁定原文由主智能体转达，本方案**只落地不改写**。

### 13.1 U-05（算薪日范围：1..31，月末钳位）

| 项 | 内容 |
| --- | --- |
| 裁定原文 | 「算薪日按 1-31」 |
| 最终口径 | `payroll_day` 允许 **1..31**；当月**无该日**时 `dueAt` **钳位到当月最后一天**执行 |
| 裁定方 | **用户**（v1.3） |
| 落点 | §3.1（字段范围）、§4.1 I-3（入参）、§5（触发判定）、§7（U-05 行）、§9（`dueAt` 行）；`9407` 文案维持「1-31，月末自动钳位」（§4.3） |
| 作废表述 | v1.2 §3.1/§5/§7/§9 中「范围**待用户裁定**」「**候选甲/乙**（1..28）」「**未裁定前不得实现**」等；**候选乙（仅 1..28）正式废弃** |
| 数据层 | **无新增迁移**；`V20` 注释「1-31（月末钳位）」已与裁定一致（仅注释，非结构变更） |

### 13.2 U-06（补跑模型：日粒度重试，替代 72h 窗口）

| 项 | 内容 |
| --- | --- |
| 裁定原文 | 「补跑窗口只允许次日补跑，补跑失败则次日再次补跑」 |
| 最终口径 | **① 算薪日当天未成功执行 → 次日补跑；② 补跑失败 → 次日继续补跑，以自然日为粒度持续重试直到成功；③ 同一驿站同一账期每自然日至多尝试一次（不得因 10 分钟 tick 同日反复重试）；④ `target_month` 恒为 `dueAt` 所在月（补跑的是该账期单据，仅执行日推后）；⑤ 允许告警 / 可配熔断补偿，但不得加硬上限推翻本口径。** |
| 裁定方 | **用户**（v1.3） |
| 落点 | §3.3（**重写**：「错过」判据 + 日粒度重试模型 + **触发时刻 `catch-up-time-of-day`（v1.5 复-3）** + **同日重复触发一律 `9410`**）、§4.1 I-4（**v1.5 删 `force`**）、§4.3（`9410` 文案、`9414`/`9416` 作废）、§5（触发 / 补跑 / 重试 / `SKIPPED` / 参数外置）、§6（B1 `V21` 范围、B3 验收）、§7（U-06 行）、§9（补跑行）、§1 ADR-01（失败模式两行） |
| 数据支撑 | `payroll_run` **新增 `attempt_date DATE NOT NULL`**（`ZonedDateTime.now(zone).toLocalDate()`）；**v1.4（主代理裁定）**：`attempt_date` 与 `station_id`/`target_month` 组成 **DB 唯一键 `uk_attempt`** 作为**日粒度硬防线**（应用层短路仅作优化），并**删除冗余索引 `idx_payroll_run_station_month`**（列相同，由 `uk_attempt` 覆盖其查询前缀）；并入 `V21`（§6）。跨日终态占位另由 `uk_payroll_run_claim` 兜底，二者**正交共存**（§3.3） |
| 告警/补偿 | `alert-after-consecutive-fail-days`（默认 3，**值以 `[算法 v1.3]` 为准** → 连续失败达阈值即推送管理员）+ `alert-repeat-interval-days`（重复节奏，默认 0 = 仅首次）；**仅告警、不停止重试，不构成硬上限**。**（v1.5 复-4：与算法参数名对齐，删除 v1.3 自拟的 `alert-after-fail-days` / `auto-retry-suspend-after-fail-days`——算法无「暂停自动重试」项。）** |
| 作废表述 | v1.1/v1.2 全部「**72h 窗口**」「`[dueAt, min(dueAt+W, 次月首日 00:00))`」「**窗口裁剪到当月**」「**自动路径只评估 `now` 所在月**」「**跨月一律手工触发**」「**候选甲需 `W_min` 下限 + 有限跨月顺延**」「候选甲/乙」「**未裁定前不得实现**」「**重试上限 `max-retry-per-run`（默认 3）**」「**重试耗尽 → `SKIPPED(EXHAUSTED)` / `9416`**」「`force` **忽略补跑窗口（`9414`）**」；**v1.5 追废**：v1.4 的「**触发判据 `now ≥ dueAt`**」及全部 `force` 表述（v1.3「跳过日粒度闸门」/v1.4「仅跳过应用层短路」）——**`force` 参数已删除**（复-2）——**上述表述全部作废，以本节口径为准** |
| 与算法文档关系 | 算法工程师按同一复评同步四件套为 `[算法 v1.3]`；本方案涉参数处（`catch-up-time-of-day`、`alert-after-consecutive-fail-days`、`running-timeout-minutes`、复杂度 / 边界用例）**只引用 `[算法 v1.3]`，不代为定值**；若算法结论与本方案冲突，**以「日粒度重试」这一用户口径为准** |

### 13.3 M-9（算薪配置变更留痕）与接口清单最终口径

- **M-9（主代理裁定）落点**：新增表 `station_payroll_setting_log`（§3.6，10 列）、I-3 每次保存必写、新增 I-9 查询、并入 `V21`；「启用 0→1」由 `action=ENABLE` 可追溯（§11 M-9 行）。
- **接口清单最终口径**：**既有 10 + 新增 9（I-1~I-9）+ 变更 7（C-1~C-7）**。因 M-9 新增 1 个查询接口（**I-9**），「新增」由 v1.2 的 8 改为 **9**，逐条可数（§4.1、§9、§10 末）。

### 13.4 待用户最终确认项

| 项 | 内容 | 当前口径 | 归属 |
| --- | --- | --- | --- |
| **U-16** | 加扣款金额方向 | 加款计入应发、扣款计入扣项，**实发 = 应发 − 扣项**（不改公式）；每笔必填事由 + 记录操作人 + **自动参与金额计算** | **主代理解释，待用户最终确认**（§3.4、§7）。若用户要求「扣款亦减少应发」，须另评估 `PayrollTotalsPolicy` 公式变更（§3.4 预留说明） |

### 13.5 v1.4 补强：日粒度防线由「应用层判定」上移为「DB 唯一键」（主代理裁定）

> 本节**注销** v1.3 中「日粒度闸门为应用层判定」的旧表述，确保全文无矛盾残留；**裁定来源为主代理**（非用户），用于统一本文档与算法四件套 v1.2 对同一机制的表述。

| 项 | 内容 |
| --- | --- |
| 裁定来源 | **主代理裁定**（采纳算法四件套 v1.2 的 DB 唯一键方案，更硬、可跨实例兜底） |
| 最终口径 | 「同一驿站同一账期**每自然日至多尝试一次**」的**硬防线 = DB 唯一键 `uk_attempt (station_id, target_month, attempt_date)`**（跨实例兜底）；应用层 `existsSuccess`/「当日已尝试」短路**仅作查询优化、不作为唯一防线**；`attempt_date` **每行恒有值（NOT NULL）、永不置 NULL**，与 `uk_payroll_run_claim`（跨日终态占位，`FAILED` 置 NULL）**正交共存**、互不冲突（§3.3） |
| 索引取舍 | `uk_attempt` 与 `idx_payroll_run_station_month` **列完全相同** → 按**最小冗余**原则**删除** `idx_payroll_run_station_month`，由 `uk_attempt` 覆盖其 `(station_id, target_month)` 查询前缀（运行历史 / 连续失败天数统计）；`V21` 由「扩索引」改为 **`DROP INDEX idx_payroll_run_station_month` + `ADD UNIQUE KEY uk_attempt …`** |
| 连带影响（`force`，**已闭环**） | v1.4 曾登记「`force` 作用域收敛为主代理待确认项」；**v1.5 复-2 主代理裁定：删除 `force` 参数**——复评 §三 3.3 证明其无可观测差异；**同日重复触发一律 `9410`**（`uk_attempt` 硬拒绝）。该待确认项**已闭环、无残留**（§3.3「同日重复触发」表、§4.1 I-4） |
| 落点 | §3.3（防线表述 + 正交关系表 + **同日重复触发表** + 索引）、§5（触发/补跑/重试）、§6（B1、数据层状态②、`V21` 范围）、§8（契约冲突 1、M6）、§9（新增表/补跑/重试模型行）、§13.2（数据支撑行） |
| 作废表述 | v1.3 §3.3「**日粒度闸门为应用层判定**」「**跨实例互斥仍由 `uk_payroll_run_claim` 兜底**（隐含日粒度仅应用层）」「`idx_payroll_run_station_month` **扩为** `(station_id, target_month, attempt_date)`」（改为**唯一键 + 删冗余索引**）——**上述表述全部作废，以本节口径为准** |
| 不变项 | U-05/U-06 落定内容、M-9 表设计、接口清单（**既有 10 + 新增 9 + 变更 7**）、批次划分（B0~B5）**均不变**；**v1.5 删除 `force` 亦不改接口数量** |

> **其余待办**：`TODO(扩展)` T1~T5 见 §7；U-05/U-06 已裁定，**无口径留有「待用户裁定」字样**；**v1.4 的 `force` 作用域收敛（§13.5）已由 v1.5 删除 `force` 闭环（复-2），无残留待确认项**；复评 复-1~复-4 的逐条响应见 **§14**。

***

## 14. 对复评新必改项（复-1~复-4）的逐条响应表（v1.5 新增）

> 来源：[tech-review-payroll-automation.md](tech-review-payroll-automation.md)「复评（方案 v1.4 ｜ 2026-09-27）」§七「新发现必改项」与 §7.2「放行前置条件 P1~P6」。逐条给 **v1.5 结论 / 落点 / 验收标准满足方式**，不留悬空。**本方案不构成任何 C 档授权**；放行前置的判断与授权归主智能体（安全面归网络安全工程师），本表只声明方案层落点。

### 14.1 复-1~复-4 逐条响应

| 必改号 | 级别 | 退回对象（复评） | v1.5 结论 | 落点 | 验收标准满足方式 |
| --- | --- | --- | --- | --- | --- |
| **复-1** | 中（倾向高） | 架构师（方案侧） | **已闭环** | §3.3（回收动作 + 默认值 + 单实例安全论证 + 场景登记）、§5「失败重试与僵死回收」、§7 **T4** | ① **默认值与理由一致**：`stale-reclaim-enabled` 单实例默认 **`true`**（§3.3/§5/§7，纠正 v1.4 的 `false`，与 `[算法 v1.3]` 一致）；② **风险登记条目**：§3.3「场景登记」给出**触发条件**（`RUNNING` 僵死且未回收）、**影响**（`(s,m)` 自动补跑中止、告警不触发→静默失效）、**缓解**（默认开启回收 + `running-timeout-minutes`>最坏批次耗时 + 显式关闭时加巡检告警与最小人工恢复步骤）；③ **回收动作完整定义**：`claim_key=NULL` + `status=FAILED` + `fail_reason=STALE_RECLAIMED` + `finish_time` + **触发失败告警**（消除「静默失效、告警不触发」）；④ **单实例安全论证**：`fixedDelay` 不重叠 → 无并发回收者 B → 超时即真僵死 |
| **复-2** | 中 | 架构师 | **已闭环（删除 `force`）** | §3.3（同日重复触发表）、§4.1 I-4、§1 ADR-01、§4.3、§7 **T5**、§10 必改 6、§13.2/§13.5 | **采纳选项 A（删除）**：I-4 入参移除 `force`；**同日重复触发一律 `9410`**（由 `uk_attempt` 硬拒绝）；核对三处一致——**入参（§4.1 I-4 无 `force`）/ 交互表（§3.3 已无 `force=false/true` 列）/ §13.5（登记删除）**；`9414`（已于 v1.3 作废）不复用、不因删除新增错误码 |
| **复-3** | 中 | 架构师（方案侧） | **已闭环** | §3.3「错过」判据 + 日粒度重试模型、§5（触发判定/补跑两行）、§13.2 | **统一为算法口径**：补跑当日触发时刻 = **`catch-up-time-of-day`（默认继承该站 `payroll_time`）**；判定 = **`now ≥ 当日 catch-up-time-of-day`** **且该 `(station_id, target_month)` 当日尚未尝试**；`target_month` 恒为 `dueAt` 所在月；**删除 v1.4 的「`now ≥ dueAt`」**（其在跨入次日 00:00 后恒真）；参数以 **`[算法 v1.3]`** 为单一真源（§5 不代定值） |
| **复-4** | 低 | 架构师 | **已闭环** | §2.8（第 23~25 行）、§5「参数外置」、§13.2 | ① **前端补 3 处 staff 侧点位**：`staff/payroll.vue:93`、`MyPayrollCard.vue:49`、`todoGroups.js:21`（§2.8 第 23~25 行，均非 NPE、已注明改动要求）；② **参数清单同一份**：§5 明确「**名称/默认值/范围一律以 `[算法 v1.3]` §2 为单一真源**，本方案不另立名称集合」，删除 v1.3 自拟的 `alert-after-fail-days`/`auto-retry-suspend-after-fail-days`，改引算法名 `alert-after-consecutive-fail-days`/`alert-repeat-interval-days`/`catch-up-time-of-day` |

### 14.2 放行前置 P1~P6 的方案层落点复核

| # | 前置条件（复评 §7.2） | 承接口 | 方案层落点 / 状态 |
| --- | --- | --- | --- |
| **P1** | 必改 复-1/2/3/4 闭环（两文件默认值/时刻/参数名/`force` 处理一致，卡死场景已登记） | **架构师（本轮）** | **本轮完成**（§14.1）：复-1 默认 `true`+动作+场景；复-2 删 `force`；复-3 时刻统一；复-4 前端+参数名。**算法侧同口径同步由算法工程师闭环（`[算法 v1.3]`）**；「两文件一致」须对读确认 |
| **P2** | 数据层三方对照（`V20`+`V21` / `db.md` / `init.sql`）逐列一致，由数据库工程师出对读记录 | **数据库工程师** | **非架构师归口**；本方案 §6「数据层状态」②已列 `V21` 范围（`payroll_log` 定位列 / `attempt_date`+`uk_attempt`+删冗余索引 / `station_payroll_setting_log`），**对读记录由数据库工程师保证** |
| **P3** | U-16（加扣款金额方向）由用户最终确认并存档；未确认前不得固化为实现 | **用户（主智能体转达）** | **保持标注「主代理解释、待用户最终确认」**（§3.4、§7 U-16、§13.4），**本版不改口径、不预设公式变更**；待用户裁定后更新 §13.4 |
| **P4** | 安全复验（L7）：M-1/M-2 实现层须经网络安全工程师复验 | **网络安全工程师** | **非架构师归口**；本方案 §8-3、§11 已声明安全面（claim 收敛 / 脱敏 / 通知隔离 / 回收自动写库为生产变更面）；**复验结论由网络安全工程师出**，本方案不构成安全实质判定 |
| **P5** | B1 迁移执行属 C 档 → 须主智能体按 §10.3 三步授权，执行前备份库 | **主智能体** | **非架构师归口**；本方案 §6 B1、§8-5 已标注「C 档执行、须主智能体三步授权」；**本报告/本方案不构成任何执行授权** |
| **P6** | `attempt_date` 三段式 `DEFAULT (CURRENT_DATE)` 与 `DROP DEFAULT` 的 MySQL 次版本（≥8.0.13）与「表空/非空」保护路径，服务器阶段实证 | **实现/部署阶段（数据库 + 运维）** | **标注为实现/部署阶段验证项**：本方案 §6 B1 验收口径已含「`attempt_date` NOT NULL 无默认、无存量直加；有存量须 C 档先默认/回填再收紧」；**次版本实证（`SELECT VERSION()`）非方案层可验，随 B1 执行前完成** |
