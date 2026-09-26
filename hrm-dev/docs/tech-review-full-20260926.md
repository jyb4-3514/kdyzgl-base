# 技术评审报告：hrm-server 服务端实现 + hrm-clients 结构迁移 · 项目级技术质量评审

- 评估对象：`hrm-dev/hrm-server`（方案 B · 145 接口实现，实际方法级映射 **151** 个）、`hrm-dev/hrm-clients`（三端拆分）、相关文档 `server-architecture.md` / `api.md` / `db.md` / `algo-hrm-server.md` / `project-tree.md`
- 作者：后端/前端/数据库/算法工程师等（产出方）
- 评估方：`express-station-tech-reviewer`（评估方与产出方、决策方分离；未参与上述任何产物产出）
- 日期：2026-09-26
- 分支：`feature/前端演示项目拆分与精细化`
- 结论等级：**有条件通过**（须完成第 4 节必改项后报主智能体审批）
- 准入结论：可报主智能体审批，须随附本报告第 4 节必改项清单；其中 **M1、M5、M6 为「可运行验证/契约闭环」前置项，未闭环不得进入验收**
- 评审方式：**静态审查，收敛到静态阶段**。本机无 JDK / Maven / MySQL / Redis（Node 可用）。

---

## 1. 评审范围与未覆盖项

### 1.1 已覆盖（本轮实测）

| 项 | 方式 | 结果摘要 |
| --- | --- | --- |
| 后端源码规模 | 目录扫描 | main `*.java` = **533**、test = **95**、Controller = **23**、`*ServiceImpl` = **26**、`*Mapper` = **39**、`service/**/support` = **118** |
| 方法级端点 | 注解计数 | GET 67 / POST 48 / PUT 27 / DELETE 9 = **151** |
| 分层与分包 | 目录 + import 扫描 | Controller 域 = 15、Service 域 = 16；Controller **零** `import com.qiujie.mapper.*` |
| 跨域依赖 | import 扫描 | 域间调用全部走接口/端口，**未发现循环依赖**（见 2.1） |
| 数据库 | init.sql 与 V1..V15 解析比对 | **38 表**（两侧一致）、**70 个索引名**（两侧**完全一致**） |
| 算法离线基准 | **实跑** `node run-all.mjs` | **8/8 OK，失败 0**（详见 2.4） |
| 异常处理/日志 | 代码扫描 | `printStackTrace` = 0；`System.out.print` = 2（仅在 `PasswordUtil` CLI）；`GlobalExceptionHandler` 齐全 |
| 契约覆盖 | 文档 vs 代码 | `api.md` 覆盖 **62/151**（缺口已由 `update-log.md` 自报） |

### 1.2 未覆盖项（一律标注「未验证」）

- **后端编译 / 启动 / Flyway 执行**：本机无 JDK/Maven，**未编译、未运行** → 「可编译、可启动、迁移可执行」类结论**未验证**。
- **接口连通、鉴权/越权、数据范围三层（L1/L2/L3）实际行为**：无 MySQL/Redis，**未验证**。
- **单测/集成测试实跑**：95 个测试文件存在，但**未执行**（无 Maven）→ 覆盖率、绿否**未验证**。
- **前端三端** `test` / `build` / `e2e`：本轮未重跑（节点可用，主张见会话记录），**未验证**。
- **大表真实查询耗时 / InnoDB 落盘字节 / Redis 令牌桶时钟精度**：无环境，**未验证**（与 `algo-hrm-server.md` §12.3 收敛口径一致）。
- **真实凭据/域名安全定性**：归网络安全工程师；本报告只做「是否入库」的形式核对，**不出安全结论**。

---

## 2. 六维逐项结论

| 维度 | 结论 | 证据 |
| --- | --- | --- |
| 1 架构与分层一致性 | **基本满足**（1 处文档与实现不符；1 处双份实现风险） | 见 2.1 |
| 2 接口契约一致性 | **部分满足**（`api.md` 覆盖 62/151，缺口 83+；响应结构/错误码规范合规） | 见 2.2 |
| 3 数据库设计与迁移 | **满足**（表/索引快照与迁移逐一对齐；命名规范；迁移只增不改） | 见 2.3 |
| 4 算法落地一致性 | **满足**（阈值/权重全外置；降级链齐全；离线 8/8 可复现） | 见 2.4 |
| 5 工程规范与非功能需求 | **基本满足**（异常/日志/分页规范；缺可观测性；1 处事实陈旧） | 见 2.5 |
| 6 文档与事实一致性 | **部分满足**（表数/迁移版本/路线/ADR 落地描述陈旧） | 见 2.6 |

### 2.1 维度 1 · 架构与分层一致性

**结论：基本满足。**

1. **分包与分层与 `server-architecture.md` §1.2 基本一致**：实际 `controller/{domain}`（15 域）、`service/{domain}/{domain}/impl/support`、`dto/{domain}`、`vo/{domain}` 与 ADR-01 声明一致；`mapper/`、`entity/` 保持扁平（与声明一致，[server-architecture.md:58-59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/server-architecture.md#L58-L59)）。
2. **无跨域直连、无循环依赖**：`controller/**` 中 `import com.qiujie.mapper` 命中 **0**（Controller 不直连 Mapper）。域间调用全部经接口/端口，最典型的是离职结算：`hr` 侧声明端口 `PayrollSettlementPort`（含 `UnavailablePayrollSettlementPort` 默认实现），`finance` 侧提供 `PayrollSettlementAdapter` 适配，**方向单向、无环**；请假↔财务经 `ApprovedLeaveDaysPort` / `PayrollLockQueryService` 两个只读端口互通，其中 `ApprovedLeaveDaysPortImpl` 类注释显式声明「依赖方向恒为 `leave → finance`，不产生环」（[ApprovedLeaveDaysPortImpl.java:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/leave/port/impl/ApprovedLeaveDaysPortImpl.java#L34)），独立核对成立。
3. **上帝类（规模）可控但偏大**：Top3 为 `LeaveServiceImpl` **998** 行、`WorkOrderServiceImpl` **884** 行、`PayrollServiceImpl` **827** 行。均未超千行，且已按 `support/` 抽策略类（如 `ShiftPayrollPolicy` / `WorkOrderDispatchScorer` / `PayrollGenerateGuard`），**不构成上帝类**，但已接近维护阈值。

**问题：**

- **P1-1（文档与实现不符）**：`server-architecture.md` §1.2 声明含 `domain/event/` 包（ADR-03 领域事件）与「`aspect/` 现为空 package-info」。实测：`com.qiujie.domain` **不存在**（全仓 `domain.event` 引用 0 命中）；ADR-03 所要求的 hr↔finance 断环**以端口实现**（`PayrollSettlementPort`）而非领域事件；`aspect/` 已有 `DataScopeAspect.java`。影响：架构文档对「断环机制」的描述与代码不一致，后续改动人易按错误模型施工。建议：回填 §1.2 与 ADR-03，二选一并写清（若维持端口方案，应显式记录「ADR-03 以端口替代领域事件」的偏离理由）。**优先级 P2**（不影响运行正确性，取「有条件通过」必改项）。
- **P2-1（双份实现风险）**：`hrm-demo`（受控文件 **436**）与 `hrm-clients`（受控文件 **571**）**并存**，且 `apps/staff-h5` 与 `hrm-demo/src/mobile` 存在**逐字节重复**：`api/attendance.js`、`api/workOrder.js`、`components/StatCard.vue` 经 MD5 比对**完全一致**；`utils/http.js`、`stores/auth.js` 已分叉。三端 `api/*.js` 命名一一对应。影响：同一逻辑维护两处（接近规则「出现三次以上必须抽取」边界），双改/漏改概率高。该状态由 ADR 结构迁移 B8「退役判定期」承接，属**已知过渡态**，但**缺截止时间与责任人**。建议：在 `adr-structure-migration.md` 补 B8 退役判定期起止与判据。**优先级 P2**。
- **P3-1（残留空目录）**：`com/qiujie` 下 `attendance/`、`leaveapproval/`、`overtime/`、`salarycalculation/`、`selfservice/`、`knowledge/`、`assistant/`、`filetask/`、`spi/`、`storage/`、`listener/`、`service/impl/` **为空目录**（`git ls-files` 0 命中，非受控）。影响：命名体系误导（如 `storage` 空但配置 `hrm.storage.*` 存在）。建议：清理或登记。**优先级 P2**。

### 2.2 维度 2 · 接口契约一致性

**结论：部分满足（结构规范合规，但契约文档覆盖严重不足）。**

**抽查结论（代码侧合规）：**

- **统一响应 `{code,message,data}` 全量遵守**：[Result.java:12-17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/Result.java#L12-L17) 与 api.md 1.2 逐字一致；23 个 Controller 全部返回 `Result<T>`，**唯一例外**为文件导出 `AttendanceController.export` 返回 `ResponseEntity<byte[]>`（导出语义正当，属合理例外）。
- **分页 `pageNum/pageSize` 全量遵守且有校验**：[PageQuery.java:22-27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/dto/support/PageQuery.java#L22-L27) `@Min(1)/@Max(100)`，越界 → `code 400`，对齐架构 §1.4.3（**不再静默钳制**，修正了 `EmployeeServiceImpl` 旧行为）。
- **错误码段位规范**：[ErrorCode.java:10-12](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L10-L12) 段位不重叠，且**显式登记了历史冲突修正**（91xx 考勤「原契约 9001~9006 与通知冲突，顺延至 91xx」，[ErrorCode.java:162](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L162)）；HTTP 状态码映射规则与 api.md 1.3 一致（[GlobalExceptionHandler.java:37-48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/handler/GlobalExceptionHandler.java#L37-L48)）。
- **鉴权显式声明**：`auth` 等控制器逐方法标注 `@RequireRoles`，注释说明「避免漏声明即静默放行（拦截器 fail-closed）」（[AuthController.java:41-42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/auth/AuthController.java#L41-L42)）。
- **端点数对账**：代码 151 = 既有 24 + 方案 B 增量 121 + M4 登录改造 6（`/auth/sms/send`、`/auth/sms/login`、`/auth/device/verify`、`/auth/devices`、`/auth/devices/{id}`、`/auth/captcha`），与 `server-architecture.md` §6.3 合计 145 的口径**可解释、无矛盾**。

**问题：**

- **P1-2（契约文档缺口 · 最重要）**：`api.md` 头部自述「接口总数 46」、§4.0「接口概览（46 个）」（[api.md:9](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L9)、[api.md:184](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L184)），加 §7 的 16 个（请假 + 前端日志）共 **62**；而代码方法级映射 **151**，**缺口 83+ 端点**（入离职 10、人事档案 6、财务 10、资薪规则 5、KPI 9、工单 9、同步 22、包裹 6、通知 6 —— 数字与 `update-log.md` 自报一致，见 [update-log.md:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L21)）。同时 `server-architecture.md` 明确「其余 145 接口契约无需改动（**以 Mock 为准**）」（[server-architecture.md:934](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/server-architecture.md#L934)）。影响：**契约真源实际落在前端 Mock 路由**（`hrm-demo/src/shared/mock/routes/` 18 文件）而非 `api.md`，导致前端/测试/新人无单一权威接口文档；且 Mock 与后端分属两个工程，二者一旦漂移无门禁捕获。建议：① 明确「契约唯一真源」并写入门禁；② 按已登记批次补齐 `api.md`（优先入离职 10 + 人事档案 6）。**优先级 P1**。
- **P2-2（契约真源与文件位置错配）**：`api.md` 服务前缀声明 `/api/v1`（[api.md:8](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L8)）与 Controller 一致（如 `@RequestMapping("/api/v1/auth")`），但生产前端基址为 `/hrm-api/v1`（经 Nginx 改写）——属部署层映射，**非冲突**，但文档未登记该改写，建议补一行说明以免误判。**优先级 P2**。

### 2.3 维度 3 · 数据库设计与迁移

**结论：满足。**

1. **三方一致（db.md / init.sql / 迁移）**：解析 `V1~V15` 全部 `CREATE TABLE` 得 **38** 张表，与 `init.sql` 的 38 张**完全一致**（集合差集为空、无重复建表）；**索引名集合 70 = 70 完全一致**（迁移侧「仅迁移、无快照」与快照侧无缺口）。与 `db.md` v2.1 自述「表总数 37 → **38**」（[db.md:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L22)）一致。
2. **迁移只增不改历史**：`V14__hr_flow_operator_columns.sql`（补 2 列）与 `V15__auth_trusted_device.sql`（1 表）均为**新版本号增量**，`V1~V13` 未改；`db.md` v2.1 亦声明「V1~V13 未改动」（[db.md:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L21)），核实成立。
3. **命名与时间字段规范**：`snake_case`、主键 `id`、`is_deleted` 逻辑删除一致。对「无 `create_time`/`update_time`」的 8 张表逐张核对，**全部为已声明例外**：日志表用业务时间 `time`（`login_log`/`client_log`/`leave_log`/`sync_task_log`）、追加型留痕（`work_order_timeline`/`work_order_transfer`）、业务时间 `apply_time`（`leave_request`）；`db.md` §1.1 与 §例外段落已登记（[db.md:490-492](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/db.md#L490-L492)）。
4. **大表策略与索引合理**：`parcel`（20 万级）**建表只建主键、索引独立 `CREATE INDEX`** 便于逐条回滚，4 条索引覆盖「查重/状态筛选/列表排序/趋势聚合」，与算法 §13 建议对齐，并附回滚脚本与 MySQL 8 在线 DDL 适用条件（[V13__parcel.sql:47-68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V13__parcel.sql#L47-L68)）。
5. **日志表索引克制**：`client_log` 仅 2 索引、只插不改、脱敏 + 截断前置（[V3__client_log.sql:20-41](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/db/migration/mysql/V3__client_log.sql#L20-L41)）。

**问题：**

- **P3-2（口径漂移 · 非代码问题）**：`SESSION-STATE.md` 记「迁移 `V3`~`V13`（11 文件 / 33 新表）+ `init.sql`（**37 表 / 52 索引**）」（[SESSION-STATE.md:56](file:///d:/Users/16626/Desktop/kdyzgl-base/SESSION-STATE.md#L56)），与实测 **V1~V15 / 38 表 / 70 索引** 不符（该条位于历史分节，未回填 v2.1 增量）。建议：补注或更正。**优先级 P2**（详见 2.6）。
- **P3-3（未验证）**：`CREATE INDEX ... (inbound_time DESC)` 的降序索引在目标 MySQL 8 小版本的实际生效、以及 `db.md` §6.2 预告的 EXPLAIN 断言（`type=range/ref`、无 `Using filesort`）**未验证**（无库）。建议在服务器复核阶段补 EXPLAIN 证据。**优先级 P2**。

### 2.4 维度 4 · 算法落地一致性

**结论：满足。**

1. **阈值/权重全外置、无硬编码经验值**：`hrm.algo.*` 由强类型 [AlgoProperties.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L15-L21) 承载（类注释明确「代码内严禁内联常量（规则 §11.4、反模式 A03）」），配置实体见 [application.yml:180-266](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L180-L266)。对 `service/**` 扫描「`0.5`/`0.9`/`1.5` 等可疑字面量」11 处，**逐处核对均为算法结构性常量而非可调阈值**：中位数 `quantile(...,0.5)`、半日粒度 `unitCount()/2.0`、优先级归一 `(priority+1)/3.0`、模拟退火接收 `< 0.5`。**未发现 A03 型硬编码经验值**。
2. **四件套与实现对应**：`algo-hrm-server.md` 按「问题建模 / 算法选型与复杂度 / 依据 / 可验证指标与基准」四段书写 S1~S8（[algo-hrm-server.md:40-42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/algo-hrm-server.md#L40-L42)），实现侧有同名 `support/*`（`SchedulePlanner` / `AttendanceAnomalyDetector` / `WorkOrderDispatchScorer` / `ParcelForecaster` / `KpiQuantileMapper` / `ShiftPayrollPolicy` 等）一一对应。
3. **降级链齐全**：`algo-hrm-server.md` §1.3 逐场景列出兜底（如 S7「历史点 < 2×季节周期 → 回落 `MA(7)`，再不足 → 近 7 日均值」，S6「无候选 → `assignee_id=null` 转人工」），代码侧有对应开关（`AlgoProperties.Forecast.minSamples/maWindow`）。
4. **离线基准可复现（本轮实跑）**：`cd hrm-dev/docs/algo-scripts && node run-all.mjs` → **总计 8 个场景，失败 0 个**；耗时 S5 87.6s / S4 10.3s / S8 7.3s，其余 < 1s。固定种子（mulberry32）保证除耗时列外结果逐位一致。
5. **口径红线遵守**：`algo-hrm-server.md` §2 列 Q1~Q6 口径清单，明确「未裁定前必须使用与现状逐位等价的默认值」，且 `application.yml` 中各开关默认即现状（如 `hrm.algo.kpi.quantile.enabled: false`、`dispatch.keywordWeighted: false`、`leave.deductEnabledDefault: false`）——**符合 P1 口径先行**。

**问题：**

- **P3-4（文档回填欠账 · 部分代码自有 TODO 承认）**：`AlgoProperties` 内多处以 `TODO(扩展)` 记录「新增键尚未登记进 `algo-hrm-server.md` §11 / `server-architecture.md` §5.1」，`AlgoProperties.java` 单文件即有 **6** 处（如 [AlgoProperties.java:217-218](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L217-L218)、[L298-300](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L298-L300)、[L425-427](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L425-L427)、[L463-464](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AlgoProperties.java#L463-L464)）。影响：算法方案 §11 与实现存在**参数登记缺口**（不影响运行，但破坏「逐键一致」可审计性）。建议：主智能体按 TODO 回填文档。**优先级 P2**。

### 2.5 维度 5 · 工程规范与非功能需求

**结论：基本满足（异常/日志/分页规范扎实；可观测性缺口）。**

1. **异常处理规范**：[GlobalExceptionHandler.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/handler/GlobalExceptionHandler.java#L36-L130) 分类处理（业务/参数绑定/校验/请求体/上传/方法/404/兜底），**兜底只记堆栈、对外通用提示**（[L126-130](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/handler/GlobalExceptionHandler.java#L126-L130)），符合「不吞、不裸抛、敏感不外泄」。全仓 `printStackTrace` = **0**。
2. **日志规范与脱敏**：`LoggingSmsSender` 为范例——「只打印位数与脱敏手机号，**绝不打印验证码明文**」，并把文案抽为静态方法供单测断言（[LoggingSmsSender.java:17-43](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/sms/impl/LoggingSmsSender.java#L17-L43)）。全仓扫描 `log.(info|debug|warn|error)` 含 `password/secret/token/phone/idCard/bankCard/accessKey` 仅命中该处（且已脱敏）。`System.out.print` 仅 2 处，位于 `PasswordUtil` 的命令行工具 `main`，**可接受**。
3. **幂等与并发**：薪资生成幂等经 `PayrollGenerateGuard`（可编辑态覆盖重建 / 非可编辑态整批拒绝 9405，[PayrollGenerateGuard.java:8-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/finance/support/PayrollGenerateGuard.java#L8-L13)），并有 `PayrollServiceImplGenerateIdempotencyTest`；会话淘汰经 `SessionEvictionPolicy`（纯逻辑可单测）+ `max-sessions-per-employee`（[SessionEvictionPolicy.java:44-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/SessionEvictionPolicy.java#L44-L64)）。**注意**：薪资生成幂等为「状态守卫 + 覆盖重建」的**应用级**方案，**未使用分布式锁/DB 行锁**，高并发下同一月并发生成是否存在竞态**未验证**（`update-log.md` 亦自述 `HrSalaryWriter` 非幂等、依赖流程状态守卫，[update-log.md:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L22)）。
4. **`TODO(扩展)` 标注**：main 侧 **53 处 / 43 文件**，符合统一标注要求（未发现散落非规范 TODO）。
5. **性能**：`parcel` 走游标分页并限制 OFFSET 最大页深（`AlgoProperties.ParcelPage.maxOffsetDepth`），`ParcelServiceImpl` 有相关 TODO 说明与 Mock 的差异。**N+1 查询未做系统性核查（未验证）**。

**问题：**

- **P1-3（可观测性缺口）**：`hrm-server` **未引入 spring-boot-actuator**（全仓 `actuator/management/endpoints` 命中 0），亦无自建 `/health` 端点。影响：无健康检查/指标端点，上线的存活/就绪探针、监控告警缺抓手（`SESSION-STATE` 中线上 `/health` 200 系前端 Nginx 静态路径，与后端无关）。建议：至少提供 `/actuator/health`（或自建轻量 `/health`）并显式限制暴露面。**优先级 P1**（涉外部暴露面，实施须先取网络安全工程师结论）。
- **P2-3（并发未验证）**：薪资并发生成竞态（见上）——建议补「同月并发调用」的竞态验证方案或明确以单实例串行调用约束。**优先级 P2**。
- **P2-4（超参隐式耦合）**：`application.yml` 存在跨键隐式约束（如 `middayBoundaryMinute: 720` 要求「删中班后须校验无 12:00 起始班次」，[application.yml:205](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/resources/application.yml#L205)），但**无启动期校验**。建议：加配置一致性校验或明确运维检查项。**优先级 P2**。

### 2.6 维度 6 · 文档与事实一致性

**结论：部分满足。**

1. **`update-log.md` 及时且诚实**：2026-09-26 两条记录在案，且**主动自报**关键事实纠正（如「`api.md` 覆盖仅 62/151」[update-log.md:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L21)、「被 `v-if` 守卫不必然剔除字面量」[update-log.md:31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L31)），符合「下游纠正上游」纪律。
2. **`project-tree.md` 采用追加式回填**：新增 §2.1「多端 workspace `hrm-clients/`（2026-09-25 回填）」，并声明「§0 的 TODO(扩展) 三端独立代码已由本节与 ADR 承接」（[project-tree.md:98-108](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/project-tree.md#L98-L108)）；`.trae/skills` = 14、`.trae/agents` = 10 与文档一致。**结论：基本一致**（仅 §0 表格仍指向 `hrm-demo` 路径，属未改的既有描述）。

**问题：**

- **P2-5（路线口径不一致）**：`plan.md` §1 定义「二期 = 驿站数据同步、**三期 = 工单系统**」（[plan.md:16-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/plan.md#L16-L18)），而 `AGENTS.md` / 调度规则定义「二期 = 驿站包裹数据采集、**三期 = 企业微信接入**」（[AGENTS.md:9](file:///d:/Users/16626/Desktop/kdyzgl-base/AGENTS.md#L9)）。影响：路线规划与协调依据冲突。建议：明确唯一真源并回填。**优先级 P2**。
- **P2-6（表数/迁移版本陈旧）**：见 P3-2（`SESSION-STATE.md:56`）。
- **P1-4（真实域名入库 · 转交安全）**：`kongzhen1.com` 在 `hrm-dev/docs/**` 命中 **15 处**，且 `AGENTS.md`、`SESSION-STATE.md` 亦含该域名。**本报告不判定其安全性质**（归网络安全工程师），但按项目「凭据/域名/IP 不入仓库文档」的自定红线，**需安全结论后处置**（前一轮 `tech-review-structure-migration.md` 已就同一问题登记转交）。**优先级 P1（待安全结论）**。
- **P3-5（被砍模块残留登记）**：MVP 裁剪后前端隐藏但磁盘保留、零引用（`SESSION-STATE.md:33-35` 有登记）。**建议补一份「被砍文件清单」**（路径级），便于后续回收审计。**优先级 P2**。

---

## 3. 总体结论等级

**有条件通过。**

**判据说明：**

- **未命中「打回」红线**：① 与 `db.md` **无冲突**（38 表 / 70 索引 / 命名规范三方对齐）；② 与 `api.md` 的差异属**覆盖缺口且已声明处置**（`update-log.md` 自报 + `server-architecture.md` 明确契约真源与后续批次），非「冲突未声明」；③ **未发现硬编码经验值**（算法阈值全外置）；④ **未发现无源结论**（本轮实跑离线基准 8/8、静态证据可复现）；⑤ Java/yml 中**无真实 IP、无明文凭据**。
- **不可判「通过」**：关键技术结论**均为静态判定**，后端**未编译、未运行**、测试**未执行**、性能**未验证**；且存在 P1 级必改项（契约文档缺口、可观测性缺口、真实域名待安全结论）。
- **判「有条件通过」**：上述必改项**不影响主干正确性**，可按清单逐条闭环，**不必整体重评**（复评按第 4 节逐条核对）。

**准入：可报主智能体审批**，须随附第 4 节必改项清单；**M1（契约）/ M5（可运行验证）/ M6（双份实现退役计划）为报审前置**，未闭环不得进入验收环节。**本报告不下授权结论**（授权归主智能体；涉安全面须先取网络安全工程师结论）。

---

## 4. 必改项清单

格式：问题 —— 依据；验收标准；复核方式；是否阻塞。

**M1｜`api.md` 契约缺口 83+ 端点，契约真源与文档错位 —— P1**

- 依据：`api.md:9/184`（自述 46 + §7 16 = 62）；代码方法级 151；`server-architecture.md:934`（「以 Mock 为准」）；`update-log.md:21`（自报缺口）。
- 验收标准：`api.md`（或新指定的唯一契约文件）**覆盖全部 151 个方法级端点**，或**显式声明**「契约真源 = 某文件 + 未收录端点白名单与补齐计划（含批次与截止）」；文中 `TODO`/「追补」类表述 0 命中。
- 复核方式：以控制器注解与 `@RequestMapping` 前缀生成方法级清单，与契约文件逐条比对，差集为空（或在白名单内）。
- 阻塞：**是**（报审前置）。

**M2｜真实域名 `kongzhen1.com` 入库 —— P1（待安全结论）**

- 依据：`hrm-dev/docs/**` 命中 15 处 + `AGENTS.md` / `SESSION-STATE.md`；项目自定红线「凭据/域名/IP 不入仓库文档」。
- 验收标准：由**网络安全工程师**出具结论；若判为须脱敏，则仓库文档内 `kongzhen1.com` 命中 = 0（改占位符），并在 `update-log.md` 登记。
- 复核方式：全仓 `grep -r kongzhen1.com`（含 `.md`）；附安全结论文件路径。
- 阻塞：**是**（**不得由本报告或架构师判定**；评估方与决策方分离）。

**M3｜后端可观测性缺失（无 actuator / 无健康端点）—— P1**

- 依据：`hrm-server` 全仓 `actuator|management:|endpoints` 命中 0；无 `/health`。
- 验收标准：提供健康检查端点（`/actuator/health` 或自建 `/health`）与最小指标面，且**暴露面显式受限**（仅内网/仅白名单路径）；附变更后 Nginx/防火墙口径说明。
- 复核方式：读 `pom.xml` 依赖 + `application.yml` `management.*` 配置 + 端点 Controller；涉暴露面须附网络安全工程师结论。
- 阻塞：**是**（涉外部暴露面，实施前先取安全结论）。

**M4｜架构文档与实现不符（ADR-03 断环机制 / `domain/event/` / aspect 描述）—— P2**

- 依据：`server-architecture.md:36/60/62/214` 声明领域事件与空 aspect；实测无 `com.qiujie.domain`、以 `PayrollSettlementPort` 端口断环、`DataScopeAspect` 已存在。
- 验收标准：`server-architecture.md` §1.2 与 ADR-03 的包结构与断环机制描述**与代码一致**；若维持端口方案，显式记录「以端口替代领域事件」的偏离与理由。
- 复核方式：核对 §1.2 代码块与实际目录树；核对 ADR-03 段落与 `service/hr/port` + `service/finance/port` 实现。
- 阻塞：否。

**M5｜后端零可运行验证 —— P1**

- 依据：本机无 JDK/Maven/MySQL/Redis；`AGENTS.md:40-41` 明确「后端不可编译运行」。
- 验收标准：在具备环境处完成 ① `mvn -q clean package` 编译通过；② Flyway 迁移 `V1~V15` 在空库首次执行成功且表/索引数与 `init.sql` 一致；③ 冒烟：登录成功 + 一个分页查询 + 一个写操作（含鉴权）+ 一个 400 越界分页；结果归档到 `docs/`。
- 复核方式：附构建日志、`flyway_schema_history` 摘要、冒烟请求/响应记录；核对表索引实数 = 38 表 / 70 索引。
- 阻塞：**是**（报审/验收前置）。

**M6｜`hrm-demo` 与 `hrm-clients` 双份实现并存且逐字节重复 —— P2**

- 依据：`hrm-demo` 受控 436 文件 / `hrm-clients` 受控 571 文件；`api/attendance.js`、`api/workOrder.js`、`components/StatCard.vue` MD5 完全一致。
- 验收标准：在 `adr-structure-migration.md` 明确 B8 退役判定期的**起止时间、判据、责任人**，或明确「真源唯一 + 另一侧为只读快照」的约束与门禁。
- 复核方式：读 ADR B8 段落；抽查 `hrm-demo` 是否仍在门禁/构建链路中。
- 阻塞：否。

**M7｜文档事实陈旧（表数/迁移版本、路线三期定义）—— P2**

- 依据：`SESSION-STATE.md:56`（37 表 / 52 索引 / V3~V13）；`plan.md:16-18`（三期 = 工单系统）vs `AGENTS.md:9`（三期 = 企微接入）。
- 验收标准：`SESSION-STATE.md` 表数/索引数/迁移区间与实测一致（38 表 / 70 索引 / V1~V15，或标「历史快照」）；二期/三期定义在 `plan.md` 与 `AGENTS.md` 唯一一致。
- 复核方式：与 `init.sql`、迁移目录实数比对；两份文档路线表逐行比对。
- 阻塞：否。

**M8｜`AlgoProperties` 参数登记缺口回填 —— P2**

- 依据：`AlgoProperties.java` 6 处 `TODO(扩展)` 自述「§11 / §5.1 尚未登记」。
- 验收标准：`algo-hrm-server.md` §11 与 `server-architecture.md` §5.1 逐键覆盖 `AlgoProperties` 现有全部键与默认值；`TODO(扩展)` 相应条目清零。
- 复核方式：以 `AlgoProperties` 字段名为清单，在 §11 中逐键核验存在且默认值一致。
- 阻塞：否。

**M9｜被砍模块残留路径级登记 —— P2**

- 依据：`SESSION-STATE.md:33-35` 仅定性登记「保留磁盘、零引用」。
- 验收标准：提供路径级清单（被砍 `.vue` / `api` 文件全路径），并标注回收条件。
- 复核方式：抽查清单与磁盘文件存在性、路由引用数（应为 0）。
- 阻塞：否。

---

## 5. 假设与风险登记

| # | 假设 / 风险 | 触发条件 | 影响面 | 缓解 / 现状 |
| --- | --- | --- | --- | --- |
| A1 | 假设：后端代码可编译（本机无法验证） | Maven 编译在服务器暴露编译错误 | 全部后端交付 | 收敛到 M5；`AGENTS.md` 已声明本机无 JDK |
| A2 | 假设：Flyway `V1~V15` 在目标 MySQL 8 可全量执行 | 版本/排序/DDL 语法差异 | 库结构、启动 | 静态比对已过（表/索引一致）；执行仍**未验证** |
| A3 | 假设：`hrm-demo/src/shared/mock` 与后端逐位等价 | Mock 与后端语义漂移 | 前端联调、验收用例 | 契约真源在 Mock（P1-2）；**无漂移门禁** |
| R1 | 契约文档缺口长期存在 | `api.md` 不补齐 | 新人理解、测试用例、回归 | 必改项 M1；`update-log.md` 有批次计划 |
| R2 | 双份前端实现分叉 | 只改一侧 | 三端行为不一致 | 必改项 M6；B8 退役判定期 |
| R3 | 薪资并发生成竞态 | 同月并发触发生成 | 工资单重复/错账 | 应用级幂等守卫；**无分布式锁**（未验证） |
| R4 | 无健康检查致故障不可观测 | 进程假死/依赖不可用 | 线上可用性 | 必改项 M3 |
| R5 | 真实域名入库 | 仓库公开/外泄 | 合规、攻击面 | 必改项 M2；**归网络安全工程师判定** |
| R6 | 超参隐式耦合（`middayBoundaryMinute` 等） | 运维改配置 | 排班/算薪异常 | 无启动期校验（P2-4） |
| R7 | `AlgoProperties` 文档登记缺口 | 参数审计 | 算法可解释性 | 必改项 M8 |

---

## 6. 开放问题

1. **契约唯一真源归属**：继续以「前端 Mock 路由」为真源（现状），还是收敛回 `api.md`？若收敛，谁负责门禁？（架构决策，须主智能体裁定）
2. **`/health` 暴露策略**：内网可达 / 仅本机 / 经 Nginx 白名单？——须网络安全工程师结论（外部暴露面）。
3. **薪资生成并发模型**：单实例串行是否足以约束？若未来多实例，是否引入 DB 唯一键或分布式锁？
4. **`hrm-demo` 退役时间点**：B8 判定期（≥1 发布周期）的起点与判据由谁裁定并写入 ADR？
5. **被砍模块回收策略**：永久保留（防回流）还是设回收期限？
6. **`api.md` 补齐优先级**：是否按 `update-log.md` 建议先补「入离职 10 + 人事档案 6」？与注册审批方案是否合并批次？
7. **未验证项复现责任**：M5（编译/迁移/冒烟）与 EXPLAIN 断言（P3-3）由谁在何环境执行并归档？

---

## 7. 复评记录

（本轮为初评；「有条件通过」复评时按第 4 节 M1~M9 逐条核对，不新增无关要求。评估范围冻结。）

- 评审结论绑定版本：本报告对应代码/文档状态截至 **2026-09-26**，工作区分支 `feature/前端演示项目拆分与精细化`；上述产物变更后须重评。
- 评估方声明：本报告为**独立评估**，未参与被评产物产出；**不代改代码、不代下授权结论**；安全实质判定归网络安全工程师。
