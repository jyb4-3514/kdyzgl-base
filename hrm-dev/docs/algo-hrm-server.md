# 快递驿站智汇系统 · 服务端算法方案（algo-hrm-server）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-24 |
| 作者 | 算法工程师 `express-station-algorithm-engineer` |
| 状态 | **待主智能体 Review + 待用户裁定 §2 口径清单** |
| 关联文档 | [api.md](api.md)、[db.md](db.md)、[requirement.md](requirement.md)、[plan.md](plan.md)、[demo-leave-design.md](demo-leave-design.md) |
| 行为规格来源 | `hrm-dev/hrm-demo/src/shared/mock/*`（145 条 Mock 契约中与算法相关的 store/route 实现） |
| 离线原型 | `hrm-dev/docs/algo-scripts/`（Node 24，`node run-all.mjs` 一键复现） |
| 变更范围 | **仅算法设计方案 + 离线原型 + 参数外置清单**；不含 Java 代码、不含 DDL、不改 `api.md`/`db.md`、不改前端 |

> **本方案不做的事（角色边界）**：不落地 Controller/Service/Mapper、不写 DDL/迁移、不改前端展示逻辑、不执行部署。
> 本方案只回答：**用什么算法、为什么、复杂度多少、参数怎么配、边界怎么兜、指标是多少**。

---

## 0. 摘要（结论先行）

| # | 场景 | 选型 | 关键复杂度 | 关键指标（离线原型实测） |
| - | ---- | ---- | ---------- | ------------------------ |
| S1 | KPI 评分与权重 | 阶梯参数化 + 分位映射（可选） | O(M) / 分位 O(M·n log n) | 等价性 1200 项 **0 不一致**；分位模式标准差 8.49→12.72，基尼 0.061→0.145 |
| S2 | 工资试算口径引擎 | 来源解析器注册表（表驱动） | O(I log I) | 幂等 **0 差异**；500 人 4.99ms；新增来源**不改核心代码** |
| S3 | 考勤排班生成 | 贪心构造 + 模拟退火 | O(D·E·S) + O(iter·D·E) | 覆盖率 0.7778→**1.0**；最少在岗违规 20→**0** 格；班次人次 80/80/40→66/66/68 |
| S4 | 考勤异常检测 | 稳健 z-score(median/MAD) + 连缺游程 | O(n log n) | F1 **0.889**（普通 z 仅 0.667）；连缺阈值 3 天 F1=**1.0** |
| S5 | 请假计薪天数与重叠 | 半天单元整数区间 + 二分 | O(log m) / O(log k) | 计薪天数 **18.5×**；重叠 k=2000 时 **6147×**；结果 **0 不一致** |
| S6 | 工单多目标派单 | SLA 紧迫度×技能×负载 加权标量化 | O(E) 每人 | 平均完成 9.447h→**1.504h**；SLA 78.3%→**100%**；技能匹配 89%→**100%** |
| S7 | 包裹预测/容量/性能 | Holt-Winters 加性季节 + IQR 离群 + 游标分页 | 预测 O(n)；查询 O(log N+pageSize) | MAPE **5.34%**（朴素 6.10%）；游标 vs 深分页扫描行 38 vs **20020** |
| S8 | 通知限频与日志聚合 | 令牌桶 + 窗口 Map 指纹聚合 | O(R log R) / O(rows) | 峰值 23→**14**/s；日志 100k 聚合 **114×**，压缩比 **199×** |

**必须由用户裁定的口径条目 6 条**（见 §2）：KPI 等级阈值与模式、分位模式排名口径、绩效 capRatio 上限与负净额、SLA 阈值、派单多目标权重与丢弃上限、请假扣款开关与连缺阈值。

**无阻塞冲突**：本方案与 `api.md`（不改任何请求/响应字段）与 `db.md`（不改表结构）**无契约冲突**；仅对 `db.md` §6.2 的 `parcel` 索引预告**追加一条建议索引**，属数据库侧联评事项（见 §13）。

---

## 1. 总体约定

### 1.1 四件套模板（每个场景必备）

每个场景固定按 ① **问题建模**（目标函数/约束/输入输出/边界）② **算法选型与复杂度**（时间/空间/最坏情况）③ **依据**（官方文档/论文/开源实现）④ **可验证指标与基准数据**（before/after + 复现脚本）四段书写，缺一不可。

### 1.2 参数外置（硬要求）

- 所有阈值、权重、窗口、阶梯系数、模式开关一律进配置，**禁止硬编码经验值**（项目规则 §11.4、调度规则反模式 A03）。
- 命名空间统一 `hrm.algo.*`，完整清单见 §11。Spring Boot 侧建议绑定 `@ConfigurationProperties(prefix = "hrm.algo")` 的强类型类；原型脚本中同名常量即为默认值。
- 沿用 `kpiStore` 已有的「配置快照」思路：算分/算薪结果**快照当时的配置**，使历史结果可解释（指标改配置后不回改历史）。

### 1.3 失败降级（硬要求）

算法不可用时**必须回落规则兜底，不得阻塞主流程**。各场景降级路径：

| 场景 | 降级触发 | 兜底行为 |
| ---- | -------- | -------- |
| S1 KPI | 无启用指标 / 全员无适用指标 | 返回 `NO_METRIC`，列表为空，不报错 |
| S1 分位模式 | 样本量 < `minSamplesForQuantile` | 回落 `LINEAR`/`TIERED` 绝对评分 |
| S2 工资 | 未知 `source` / 定薪档案缺失 | 该项按 0 计并写解释文案，不抛异常、不中断整批 |
| S3 排班 | 可行解不存在（人力不足） | 返回贪心解 + 违规清单（最少在岗不满足的天/班次），不失败 |
| S4 异常检测 | 样本量 < `minSamplesForDetect` 或 MAD=0 | 返回空集，回落单次静态阈值判定（`lateThresholdMin`） |
| S5 请假 | 区间非法（PM→AM） | 按基线口径返回 0 天并回 `DATE_INVALID`（9604） |
| S6 派单 | 无可用候选（全部超负载/停用） | 不指派，`assignee_id=null` 转人工，工单照常创建 |
| S7 预测 | 历史点 < 2×季节周期 | 回落 `MA(7)`；再不足回落「近 7 日均值」恒定预测 |
| S8 限频 | 队列超 `maxQueueWait` | 丢弃并记 `RATE_LIMITED` 计数（不重试、不阻塞发布主流程） |
| S8 日志 | 单条 > `textMax` | 截断；无指纹字段时按「单条独立」入库 |

### 1.4 离线可复现（本机无 JDK/Maven/MySQL/Redis 的替代验证）

- 脚本目录：`hrm-dev/docs/algo-scripts/`，`.mjs` 直接 `node` 运行，**无第三方依赖**。
- 固定随机种子（mulberry32，与 Mock `createRandom` 同实现），基准数据集由种子派生 → **同参数重跑结果逐位一致**（仅耗时列随机器波动）。
- 一键复现：`cd hrm-dev/docs/algo-scripts && node run-all.mjs`（8 个场景全部 OK，S5 因含基线的 O(k·L) 对照最慢，约 89s）。
- **原型与未来 Java 实现的等价性**：原型中 `sN-*.mjs` 的**纯函数**（`itemScore` / `resolveItem` / `evaluate` / `detectLateAnomalies` / `countedDaysByRange` / `scoreAssign` / `forecastHoltWinters` / `simulateBucket`）均按「无副作用、输入全显式、常量来自 config 参数」编写，可逐函数 1:1 译为 Java 静态方法；差异点仅在：
  1. 浮点求和顺序（原型的 `reduce` 顺序须在 Java 中保持，否则 `toFixed`/`Math.round` 末位可能差 1）→ 单测用**容差断言**（金额 ±0.01、分数 ±0.1）；
  2. 日期：原型用 UTC 毫秒序数，Java 侧须用 `LocalDate.toEpochDay()`（**且必须用本地时区语义**，避免 `new Date('YYYY-MM-DD')` 的 UTC 陷阱——Mock 已踩过，见 `attendanceStore.localDate`）；
  3. 本次未验证项：**MySQL 真实查询耗时、InnoDB 真实落盘字节、Redis 令牌桶时钟精度** → 均标注「收敛到服务器复核阶段」，见 §12.3。

---

## 2. 需用户确认的口径清单（口径红线 · 未确认不得实现）

> 依据：项目规则 §11.4 口径红线、调度规则组合优先级 **P1 口径先行**（R11/R22）。
> 以下条目**涉及计费/提成/SLA/考核口径**，算法侧只给「现状 / 建议 / 影响范围 / 备选方案」，**不自行拍板**。

| # | 口径条目 | 现状（Mock 基线） | 算法侧建议 | 影响范围 | 备选方案 |
| - | -------- | ----------------- | ---------- | -------- | -------- |
| Q1 | **KPI 等级阈值** | 硬编码 90/80/70，等级 EXCELLENT/GOOD/PASS/IMPROVE | 外置为 `hrm.algo.kpi.levels`（默认保持 90/80/70，**行为不变**） | 考核等级展示、绩效沟通、后续若接提成则影响金额 | A. 保持 90/80/70；B. 改为强制分布（如 20%/50%/25%/5%）；C. 按站长可配 |
| Q2 | **分位映射模式与等级的关系** | 无分位模式 | 新增可选 `scoreMode=QUANTILE`（相对排名归一）。实测：分位模式下全员均分≈50，**与 90/80/70 固定阈值严重不匹配**（200 人中 191 人落 IMPROVE） | 若启用分位，等级口径必须同步改，否则全员「待改进」 | A. 不启用分位（默认）；B. 启用分位 + 等级改「前 10%/25%/50%」相对分档；C. 启用分位 + 等级阈值重标定（如 75/60/45） |
| Q3 | **绩效 capRatio 上限与负净额** | `capRatio` 种子 1.2（即绩效最高 120% 基数）；扣项可超加项 → 实测**出现负净额 −360 元** | 建议：`capRatio` 上限保持可配；净额下限是否置 0 由用户定 | 工资单金额、员工确认、财务出账 | A. 允许负净额（现行为，需在单据上明示）；B. 净额下限置 0；C. capRatio 上限锁 1.0（不允许超额绩效） |
| Q4 | **工单 SLA 阈值** | 硬编码 `{低48h, 中24h, 高8h}`，且是**唯一**决定超时判定的口径 | 外置为 `hrm.algo.dispatch.slaHours`（默认保持 48/24/8，**行为不变**）；另建议区分「首次响应 SLA」与「解决 SLA」 | 超时统计、考核、派单紧迫度权重 | A. 保持单一口径 48/24/8；B. 拆响应/解决两段；C. 按工单类型分别配置 |
| Q5 | **多目标派单权重与丢弃策略** | 无派单算法（`default_assignee_id` 全空，即「只定类型优先级、不指派」） | 建议启用多目标加权（默认 `wUrgency=4 / wSkill=2.5 / wLoad=1.5 / wSpeed=2`）。实测该默认下 **SLA 100%** 但负载 Jain 从 1.000 降到 0.978（技能优先挤占均衡） | 派单公平性、员工投诉、考核工作量分布 | A. 重技能（工作质量优先，Jain 0.786）；B. 重负载均衡（Jain 0.9996）；C. 默认折中；**权重必须用户定** |
| Q6 | **请假扣款开关与异常判定阈值** | `leaveDeductEnabled` 默认 false（请假不扣）；连缺预警阈值不存在 | 建议 `leaveDeductEnabled` 保持默认 false；连缺阈值建议 **3 天**（实测 2 天 F1=0.455，3 天 F1=1.0） | 缺勤扣款金额、异常预警名单、站长沟通 | A. 保持不扣；B. 开启按缺勤扣；C. 按假别区分（事假扣、年假不扣） |

> **算法侧待办**：以上 6 条在用户裁定前，后端实现**必须使用与现状逐位等价的默认值**（Q1/Q4/Q6-A 即为现状），确保「上线即无行为变化」；新能力以开关形式默认关闭。

---

## 3. S1 · KPI 评分与权重

### 3.1 问题建模

**输入**：指标配置集 `M`（`metricKey, weight, targetValue, direction∈{UP,DOWN}, scoreRule.mode∈{LINEAR,TIERED,BINARY,QUANTILE}, fullScore, roleScope`）、员工集 `E`、月份 `month`、业绩实际值 `actual(e,m)`。

**输出**：每员工每指标单项分 `score`、加权分 `score×weight/100`、员工总分 `total`、达成率均值、等级 `level`、名次 `rank`。

**目标函数**（与 Mock 一致，保证可解释）：

```
achievement = UP  : min-cap(actual / target)          target<=0 时退化为 (actual>0 ? 1 : 0)
              DOWN: min(1, target / actual)           target 缺失时退化为 (actual>0 ? 0 : 1)
score(LINEAR)  = round(min(capRatio, achievement) × fullScore)
score(TIERED)  = round(tier(achievement) × fullScore)      tier 由配置阶梯给出
score(BINARY)  = achievement >= 1 ? fullScore : 0
score(QUANTILE)= round(percentile_rank(achievement) × fullScore)
total = Σ(score_m × weight_m) / Σ(weight_m)          （按员工**实际适用**指标权重归一）
```

**约束**：
1. 启用指标权重合计 = `weightSumTarget`（默认 100），仅在「新增/停用/删除/批量保存」时校验（与 Mock 的 `weightError` 时机一致）；
2. 快照不可回改：历史评分快照保存当时的 `scoreRule`，指标后续被改/删不影响历史解释；
3. 重算覆盖式：同月同员工同指标更新、已停用指标的旧行删除（避免幽灵权重）。

**边界条件**：空员工集、空指标集、全同达成率、单指标、`target=0`、`actual` 极端超额、`roleScope` 导致某员工无适用指标、权重合计 ≠ 100。

### 3.2 算法选型与复杂度

| 环节 | 算法 | 时间 | 空间 | 最坏情况 |
| ---- | ---- | ---- | ---- | -------- |
| 单项得分 | 查表/分支 | O(1)（阶梯可二分 → O(log T)，T=阶梯数） | O(1) | 常量 |
| 分位映射 | 排序 + 平均秩 | **O(n log n)**（n=同 scope 员工数） | O(n) | 全同值退化为一次扫描 |
| 单员工总分 | 加权归一 | O(M) | O(M) | M 为常数级 |
| 全员算分 | — | **O(n·M)**；分位模式额外 O(M·n log n) | O(n·M) | 分位模式一次排序即可复用 |
| 汇总/排名 | 排序 + 竞赛排名 | O(n log n) | O(n) | 同分同名次 |

**为什么不用更复杂的模型**：KPI 是**规则透明、可审计**的场景（员工会质疑「这分怎么来的」），ML 模型会破坏可解释性；分位映射是「相对排名归一」的最小改动，且完全可手算验证。

### 3.3 依据

- **加权归一（按实际适用权重）**：消除「缺指标被系统性压低」问题，属加权算术平均的标准做法；权重归一化定义见任何多准则决策教材（如 Saaty 的 AHP 权重归一思想）。
- **竞赛排名法（standard competition ranking）**：同分同名次、后续名次跳号，见 [Wikipedia: Ranking](https://en.wikipedia.org/wiki/Ranking)（1-2-2-4）。Mock `summarize` 已采用。
- **分位数 type-7 插值**：Hyndman, R.J. & Fan, Y. (1996). *Sample Quantiles in Statistical Packages*, The American Statistician 50(4):361-365（Excel `PERCENTILE.INC`、numpy 默认同法）。原型 `lib/stats.mjs#quantile` 采用此式。
- **平均秩处理并列**：非参数统计中处理 ties 的常规做法（用于 Spearman 秩相关），可避免并列值被系统性高估/低估。

### 3.4 可验证指标与基准数据

脚本：`algo-scripts/s1-kpi.mjs`（200 员工 × 6 指标 × 1 月，种子固定）

| 方案 | 均分 | 标准差 | 基尼 | 0 分 | 满分 | 等级分布（EXC/GOOD/PASS/IMPROVE） |
| ---- | ---- | ------ | ---- | ---- | ---- | --------------------------------- |
| 基线（硬编码阶梯） | 77.44 | 8.49 | 0.0611 | 0 | 0 | 21 / 42 / 93 / 44 |
| 参数化（外置阶梯） | 77.44 | 8.49 | 0.0611 | 0 | 0 | 21 / 42 / 93 / 44 |
| 分位映射 | 49.97 | 12.72 | 0.1448 | 0 | 0 | 0 / 2 / 7 / **191** |

- **等价性回归：1200 项比对（200 人 × 6 指标），不一致 0 项** → 阶梯参数化是**行为保持**的重构。
- 性能：参数化 **3.442 ms/轮**（200 人），分位模式 **7.075 ms/轮**（含一次排序上下文构建）。
- 边界实测：全同值分位 MID_RANK=0.5 / MIN=0 / MAX=1；`LINEAR capRatio=1` 时达成率 500% → 100 分，`capRatio=1.2` → 120 分；单指标（仅 SERVICE 适用）总分 94.6（权重归一后仍 100 分制）；`target=0` 四例全部符合基线口径。
- 权重守卫：`30/20/15/15/10/10` 通过；`30/20/15/15/10/15`（105）拒绝；无启用指标时不校验（回 `NO_METRIC`）。

**单测边界清单（必须覆盖）**：① 空员工集 ② 空指标集（无启用） ③ 全同达成率（分位三种策略） ④ 单指标 ⑤ `target=0`（UP/DOWN 各 2 例） ⑥ 极端超额（10× 目标） ⑦ 某员工无适用指标（roleScope 排除） ⑧ 权重合计 99/100/101 ⑨ 指标删除后历史快照仍可解释 ⑩ 同一员工同月重算两次结果一致。

---

## 4. S2 · 工资试算口径引擎

### 4.1 问题建模

**输入**：`payroll_rule.items[]`（`key, name, type∈{ADDITION,DEDUCTION}, source, params, enabled, sortOrder`）、账期 `month`、员工 `e`，以及三类取数上下文 `ctx = { salary, attendance, kpiScore }`。

**输出**：`items[]`（含金额与**解释文案**）、`grossAmount`、`deductionTotal`、`netAmount`。

**目标函数**（与 Mock 逐式一致）：

```
net = Σ(ADDITION.amount) − Σ(DEDUCTION.amount)
FIXED      : amount = salary[params.field] | allowancesTotal | 指定津贴项
ATTENDANCE : PER_COUNT : amount = min(cap, count × amount)     cap<=0 表示不封顶
             BONUS_IF_ZERO : amount = count===0 ? amount : 0
KPI        : amount = round(performanceBase × clamp(kpiScore/100, 0, capRatio))
MANUAL     : amount = params.defaultValue
```

**约束**：① 规则**纯配置驱动**，核心算薪代码不含任何计薪项专属公式；② 新增来源**不改核心代码**；③ `MANUAL` 项仅在 `DRAFT`/`REJECTED` 可改，算薪结果不可手改；④ 已出账（非 DRAFT/REJECTED）月份拒绝重复生成（9405）。

**边界**：无定薪档案、无绩效基数、该月无 KPI 记录、未知 `source`、`cap` 为 0/负/缺失、`count=0`、金额为 0、**扣项超加项导致负净额**（见 Q3）。

### 4.2 算法选型与复杂度

**选型**：**注册表（Registry / Strategy）+ 表驱动**，而非引入重量级规则引擎（Drools 等）。

| 环节 | 复杂 | 说明 |
| ---- | ---- | ---- |
| 规则项排序 | O(I log I) | I = 启用项数（种子 8，实测量级极小） |
| 单项解析 | O(1)（FIXED 指定津贴项为 O(A)，A=津贴项数） | 每次分发到注册表 |
| 单张工资单 | **O(I log I + I)** | 上下文三处取数各一次（非逐项查询） |
| 整批生成 | **O(E × I log I)** | E = 员工数 |

**最坏情况**：全部项 `enabled=1` 且 `source` 均为需查上下文的类型 → 上下文仍只取一次（`contextOf` 只在批次前段构造），复杂度不随项数恶化。

### 4.3 依据

- **表驱动法（Table-Driven Methods）**：McConnell, *Code Complete* 2nd ed., ch.18 —— 用查表替代长 if/else 链，是本场景从「四分支函数」升级为「注册表」的直接依据。
- **开闭原则（OCP）**：Martin, *Clean Architecture* (2017) —— 新增 `source` 只新增一个解析函数（对扩展开放），不修改 `buildPayroll` 与 `resolveItem`（对修改关闭）。
- **规则引擎的取舍**：Apache Drools / Easy Rules 提供决策表与 RETE 网络，但对本项目「8~20 个线性规则项」属**过度工程**（优化手册反模式「过度优化」），故采用轻量注册表；`TODO(扩展): 若规则项 > 200 或出现复杂条件组合，再评估引入决策表引擎`。
- 与 Mock 的**逐式对齐**：`financeStore.js#fixedAmount/attendanceAmount/kpiAmount` 四段公式与注释文本格式一并保留，保证员工端金额解释文案不变。

### 4.4 可验证指标与基准数据

脚本：`algo-scripts/s2-payroll.mjs`（500 员工 × 8 项，种子固定）

| 指标 | 数值 |
| ---- | ---- |
| 幂等性（两次生成净额不一致数） | **0 / 500** |
| 净额均值 / 标准差 | 6994.68 / 1563.18 |
| 净额最小 / 最大 | **−360** / 9681（最小值即 Q3 负净额案例） |
| 首次生成 500 人 | 11.839 ms |
| 第二次生成 500 人 | 4.990 ms（JIT 预热后） |
| 单人耗时 | 0.0237 ms |
| 来源注册表规模 | 注册前 4 → 注册后 5（新增 `TENURE`，核心零改动） |
| 新增来源实测 | 工龄 4 年 × 100 = **400 元** |

边界实测：KPI 满分（100）→ 2000 元；KPI 130 分、`capRatio=1.2` → **2400 元**（受 120% 上限约束）；无 KPI 记录 → 0 且文案「该月无 KPI 评分记录」；缺绩效基数 → 0；全勤奖缺勤 0 次 → 200、1 次 → 0；迟到 20 次 × 20 元 **封顶 300**；缺勤 10 次 × 150 元、`cap=0` → **1500**（不封顶）；未知来源 `GHOST` → 0（不抛错）。

**单测边界清单**：① 无定薪档案 ② 无绩效基数 ③ 无 KPI 记录 ④ 未知 source ⑤ `cap=0`/`cap` 负/`cap` 缺失 ⑥ `count=0` ⑦ `capRatio=0`/负 ⑧ 扣项合计 > 加项合计（负净额） ⑨ 规则项全部停用（items 为空 → net=0） ⑩ 同月重复生成幂等 ⑪ 已 `PUBLISHED` 月份拒绝生成（9405） ⑫ 非 MANUAL 项拒绝手改。

---

## 5. S3 · 考勤排班生成

### 5.1 问题建模

**输入**：员工集 `E`（|E|=n）、日期集 `D`（|D|=T 天）、班次集 `S`（|S|=s，早/中/晚）、约束参数。

**决策变量**：`x[e][d] ∈ S ∪ {REST}`。

**硬约束**（违反即不可行）：
1. **每日每班最少在岗** `minPerShift`；
2. **单员工连续工作天数** ≤ `maxConsecutiveWork`；
3. 每人每天至多一个班次（由变量定义天然满足）。

**软约束（目标）**：轮休天数均衡、班次分配均衡、员工偏好（可选，`TODO(扩展)`）。

**目标函数**（罚函数式，权重全部可配）：

```
J = wMinStaff · Σ_{d,s} max(0, minPerShift − headcount(d,s))
  + wConsecutive · (#连续工作超限次数)
  + wCoverageDeficit · Σ 缺口人数
  + wShiftBalance · Σ_e Var(该员工各班长度的方差)
  + wRestSpread · Std(各员工轮休天数)
```

**为什么用罚函数而非硬约束求解器**：规模极小（n≤50、T≤31、s=3），且**必需给出可解释结果**（站长要能看懂「今天谁休、谁上晚班」）；罚函数 + 局部搜索可在 300ms 内给出比朴素轮转显著更优且可打印的解，无需引入 CP 求解器依赖。

**边界**：人力不足（`n·(1−1/cycle) < s·minPerShift`）→ 无可行解，返回最优近似 + 违规清单；单人驿站；`minPerShift=0`；跨月排班窗口。

### 5.2 算法选型与复杂度

**选型**：**贪心构造（轮休铺排 + 班次最短缺优先） + 模拟退火局部搜索（同日班次互换 / 在岗↔轮休对调）**。

| 阶段 | 算法 | 时间 | 空间 |
| ---- | ---- | ---- | ---- |
| 轮休铺排 | 模周期错峰 | O(T·n) | O(T·n) |
| 班次贪心 | 最短缺优先 + 员工最均衡班次 | **O(T·n·s)** | O(T·n) |
| 目标评估 | 单遍统计 | **O(T·n)** | O(T·s) |
| 局部搜索 | 模拟退火（SA） | **O(iter · T·n)** | O(T·n)（每次复制候选解） |

**最坏情况**：`iter=6000`、n=50、T=31 → 约 9.3×10⁶ 次基本操作（实测 262 ms @ n=8, T=30, iter=6000）。若需要实时交互（站长点「一键排班」<1s），当前参数满足。

**终止条件**：固定迭代数（可复现，禁用「时间到即停」——那会破坏可复现性）。

### 5.3 依据

- **模拟退火**：Kirkpatrick, S., Gelatt, C.D., Vecchi, M.P. (1983). *Optimization by Simulated Annealing*. Science 220(4598):671-680。固定降温表使结果可复现。
- **约束满足/局部搜索在排班上的工业实现**：Timefold Solver（原 OptaPlanner，Apache-2.0）的 `@PlanningEntity`/`@PlanningVariable` + 软硬约束模型与本方案同构；本方案借鉴其「硬约束罚分远大于软约束」的建模思想，**不照搬其代码/框架依赖**（避免为一个 8 人排班引入完整求解器）。
- 若规模增长（多驿站联合排班、n>200）→ `TODO(扩展): 评估引入 Timefold/OR-Tools CP-SAT（约束规划）`。

### 5.4 可验证指标与基准数据

脚本：`algo-scripts/s3-schedule.mjs`（8 员工 × 30 天 × 3 班次，`minPerShift=2`，`restCycle=6`，`maxConsecutive=5`，SA 6000 次）

| 方案 | 目标 J | 覆盖率 | 最少在岗违规格数 | 单格最少在岗 | 班次总人次（早/中/晚） | 轮休标准差 | 耗时 |
| ---- | ------ | ------ | ---------------- | ------------ | ---------------------- | ---------- | ---- |
| **基线·朴素轮转** | 20266.667 | **0.7778** | **20 / 90** | 1 | **80 / 80 / 40** | 0 | 0.97 ms |
| 贪心构造 | 8.667 | 1.0000 | 0 / 90 | 2 | 68/68/64* | 0 | 1.20 ms |
| 贪心 + SA | **2.667** | **1.0000** | **0 / 90** | 2 | 66 / 66 / 68 | 0 | 262.9 ms |

\* 贪心构造的班次人次因贪心顺序产生偏斜，SA 进一步拉平。

**关键发现（对上游材料的补充，非错误）**：Mock 朴素轮转的轮休条件 `(e+d)%6==5` **在数学上蕴含** `(e+d)%3==2`，即**轮休恒落在第 3 班次**。这使晚班人次只有其余两班的一半（实测 80/80/40），并造成 20/90 个「每日每班最少 2 人」的覆盖缺口。这是「用同一模数派生两个语义」的典型耦合缺陷，也是本场景算法化的直接动因。

性能：n = 8/20/50 时，构造耗时 0.361 / 0.895 / 3.296 ms；评估耗时 0.163 / 0.367 / 0.661 ms。

**单测边界清单**：① 人力不足（不可行） ② 单人驿站 ③ `minPerShift=0` ④ `T=1` ⑤ `maxConsecutiveWork=1`（每人只能上 1 天） ⑥ `restCycleDays=1`（全休） ⑦ 同一员工同一天重复排班（唯一性 `employeeId+workDate` 覆盖） ⑧ 与已存在排班 `skipExisting` 交互 ⑨ SA 两次运行结果一致（固定种子） ⑩ 排班窗口跨月。

---

## 6. S4 · 考勤异常检测

### 6.1 问题建模

**输入**：员工集 `E`、观察窗口内每人每日打卡事实（迟到次数、缺卡标志序列）。

**输出**：异常员工集合 + 异常类型（`LATE_FREQUENT` / `ABSENT_RUN`）+ 严重度（`WARN` / `CRITICAL`）+ 触发证据（z 值 / 连缺天数）。

**判据**：

```
迟到频次:  robustZ(x_e) = (x_e − median(X)) / (1.4826 · MAD(X)) >= lateThresholdWarn
           （普通 z 备选： (x_e − mean) / std）
连续缺卡:  maxRun(e) >= consecutiveAbsentThreshold
```

**约束**：① 阈值可配；② 样本不足或 MAD=0 时**不出结论**（宁漏报不误报，避免给站长错误名单）；③ 检测结果只作「预警清单」，不直接产生扣款（扣款口径见 Q6）。

**边界**：全同值（MAD=0）、样本量 < 下限、全员零迟到、单一员工、极端离群（把 median/MAD 自身拉偏）。

### 6.2 算法选型与复杂度

**选型**：**稳健统计（median/MAD）+ 游程（run-length）**，不用 Isolation Forest / LOF。

| 环节 | 时间 | 空间 | 说明 |
| ---- | ---- | ---- | ---- |
| 稳健 z 检测 | **O(n log n)**（一次排序求 median/MAD）+ O(n) 打分 | O(n) | 中位数只算一次；⚠️ 若在循环内反复调 `robustZ` 会退化为 O(n² log n)（原型已修正：12.4ms vs 2217ms） |
| 连续缺卡 | O(n·T) | O(1) 滚动 | 单遍维护当前游程 |
| 游程 | — | — | 无需额外结构 |

**为什么不用 Isolation Forest/LOF**：n≈200、特征仅 1~2 维，树模型/密度模型在此规模下**精度无优势且不可解释**（站长需要「为什么是他」）；median/MAD 可直接手算复核。`TODO(扩展): 特征增至 ≥8 维（迟到/早退/缺卡/异常卡/时段分布）时再评估 Isolation Forest`。

### 6.3 依据

- **稳健 z-score / MAD**：Iglewicz, B. & Hoaglin, D.C. (1993). *How to Detect and Handle Outliers*. ASQC Quality Press —— 1.4826 为「MAD → 标准差」的正态一致性常数；右偏数据下 median/MAD 优于 mean/std。
- **箱线图与 1.5×IQR 离群准则**：Tukey, J.W. (1977). *Exploratory Data Analysis*. Addison-Wesley（S7 的驿站离群同源）。
- 工程实现参考：scikit-learn `robust_scale` 与 statsmodels `RobustScale` 的 MAD 归一思路（本方案只借鉴定义，未引依赖）。

### 6.4 可验证指标与基准数据

脚本：`algo-scripts/s4-anomaly.mjs`（200 员工 × 30 天；注入 10 例「迟到 λ×6」、5 例「连缺 4 天」）

| 检测器 | 预测数 | TP | FP | FN | 精度 | 召回 | F1 | 耗时 |
| ------ | ------ | -- | -- | -- | ---- | ---- | -- | ---- |
| **稳健 z（median/MAD, 阈值 3.5）** | 8 | 8 | 0 | 2 | 1.00 | 0.80 | **0.8889** | 12.4 ms |
| 普通 z（mean/std, 阈值 3.5） | 5 | 5 | 0 | 5 | 1.00 | 0.50 | 0.6667 | 0.4 ms |
| 连续缺卡（阈值 2 天） | 17 | 5 | 12 | 0 | 0.294 | 1.00 | 0.4545 | 0.2 ms |
| 连续缺卡（阈值 **3 天**） | 5 | 5 | 0 | 0 | **1.00** | **1.00** | **1.0000** | 0.2 ms |

- 数据分布：迟到次数 均值 8.155 / 中位 6 / 标准差 9.298 / MAD 3.5 → **右偏**，这是稳健统计优于普通 z 的直接原因（召回 0.5 → 0.8）。
- 阈值敏感性（稳健 z）：2.5→F1 0.842 / 3.0→0.889 / **3.5→0.889** / 4.0→0.824 / 5.0→0.750 → 佐证「阈值不可硬编码」。
- 失败降级实测：样本量 3（<5）→ 返回 0 条；全同值（MAD=0）→ 返回 0 条。

**单测边界清单**：① 样本量 < `minSamplesForDetect` ② MAD=0（全同值） ③ 全员零迟到 ④ 单员工 ⑤ 全部为异常（>50% 离群，median 被拉偏）⑥ 连缺阈值 1/2/3 ⑦ 跨月窗口 ⑧ 打卡记录含 `ABNORMAL`（不计入迟到/实到，口径与 `attendanceSummary` 一致）。

---

## 7. S5 · 请假计薪天数与重叠判定

### 7.1 问题建模

**输入**：请假区间 `(startDate, startPeriod, endDate, endPeriod)`、员工排班日集合、本人已有占用单集合、已出账月份集合。

**输出**：自然天数、计薪天数、是否重叠（及重叠单）、覆盖账期集合、账期锁命中月份。

**建模（核心变换）**：把「日期 + 上下午」映射为**半天单元整数**：

```
unitIndex(date, period) = ordinal(date) × 2 + (AM ? 0 : 1)      ordinal = 距基准日的天数
自然天数  = (endUnit − startUnit + 1) / 2          O(1)
计薪天数  = (upperBound(schedUnits, endUnit) − lowerBound(schedUnits, startUnit)) / 2   O(log m)
重叠      = 占用区间 [s_i, e_i] 与新区间 [s, e] 相交 ⇔ s_i ≤ e ∧ s ≤ e_i            O(log k)
账期跨度  = 单元区间连续 ⇒ 月份连续 ⇒ [monthOf(start), monthOf(end)]                  O(1)
非法判据  = endUnit < startUnit（同日 PM→AM）                                        O(1)
```

**约束**：① 单次跨度 ≤ `maxLeaveDays`（Mock 为 30）；② 占用状态仅 `PENDING_STATION / PENDING_BOSS / APPROVED`；③ 账期锁：撤回已批单时若任一覆盖月份存在非 DRAFT/REJECTED 工资单则拒绝（9606）；④ `SCHEDULED` 假别必须逐日查排班真源，`NATURAL` 假别与排班无关。

**边界**：同日 PM→AM（非法，0 单元）、半天单、跨月单、跨年单、30 天上限单、无排班（计薪 0）、`NATURAL` 假别、排班表为空。

### 7.2 算法选型与复杂度

**选型**：**整数区间 + 二分（lower_bound / upper_bound）**，等价于在有序集合上做区间查询（CLRS 区间树的退化形式——因只需「计数」与「是否相交」，二分足够，无需完整区间树）。

| 操作 | 基线（逐日） | 算法化 | 提升来源 |
| ---- | ------------ | ------ | -------- |
| 自然天数 | O(L) 逐日建 token | **O(1)** | 整数区间长度 |
| 计薪天数 | O(L + m)（每次重建排班 Set） | **O(log m)** | 排班单元数组预排序 + 2 次二分 |
| 重叠判定 | O(k·L)（对每条已有单重算 token） | **O(log k)** | 占用区间按 startUnit 预排序 + 二分 |
| 账期跨度 | O(L) 逐日推月 | **O(1)**（连续区间） | 单元连续性 |
| 账期锁 | O(L + 月数) | **O(月数)**（≤2） | 同上 |

**最坏情况**：二分恒为 O(log m)；排班单元数组预排序一次性 O(m log m)（可在员工排班变更时增量维护）。查询阶段**与区间长度 L 无关**——这是相对基线的本质改进（基线 30 天单要建 60 个 token）。

### 7.3 依据

- **二分查找**：CLRS, *Introduction to Algorithms* 3rd ed., ch.2.3 / ch.14（Interval Trees）——区间查询在有序结构上可降为对数级；实现口径与 Java `Arrays.binarySearch` 的插入点语义一致（`lower_bound` = 首个 ≥ x 的下标）。
- **「日粒度 → 半天单元整数」变换**：与 Mock `halfUnitsOf` 的语义**逐例等价**（本方案以 20000 次随机查询回归验证，0 不一致），属对既有口径的等价加速，不是口径变更。
- **账期锁的区间相交**：与 `db.md` §7.1 的 `idx_leave_request_date (start_date, end_date)` 索引设计一致——区间相交查询可走该索引范围扫描。

### 7.4 可验证指标与基准数据

脚本：`algo-scripts/s5-leave.mjs`（500 员工，每人 365 天排班 305 天 / 610 单元；20000 次区间查询）

| 指标 | 基线逐日法 | 区间法 | 加速比 | 结果一致性 |
| ---- | ---------- | ------ | ------ | ---------- |
| 计薪天数（20000 次） | 1253.53 ms | **67.66 ms** | **18.5×** | **0 不一致** |
| 重叠判定 k=10（5000 次） | 802.07 ms | 14.11 ms | 56.8× | 命中数一致 |
| 重叠判定 k=100 | 4185.91 ms | 13.24 ms | 316.1× | 一致 |
| 重叠判定 k=500 | 16188.49 ms | 14.79 ms | 1094.7× | 一致 |
| 重叠判定 k=2000 | 65468.89 ms | **10.65 ms** | **6147.3×** | 一致 |

- 关键性质：**区间法耗时与 k 无关**（10.65~14.79 ms 平坦），基线随 k 线性恶化 → 这是「区间 vs 逐日」的本质差异。
- 账期与非法用例：跨月单两法均得 `[2026-01, 2026-02]`；同日单自然天数 1；同日 PM→AM `valid=false`、自然天数 0；账期锁命中 `2026-02`、未命中返回 null；30 天单自然 30 / 计薪 25（排除 5 个轮休日）。
- 收益场景：站长月度审批高峰（同时校验本站数百条待审单）时，重叠判定从「秒级」降到「毫秒级」，且随历史单量增长**不劣化**。

**单测边界清单**：① 同日 PM→AM（非法） ② 同日 AM→PM（1 天） ③ 半天单（0.5 天） ④ 跨月单 ⑤ 跨年单 ⑥ 30 天上限 ⑦ 31 天（超限拒绝） ⑧ 无排班（计薪 0，`hasRestDayExcluded=true`） ⑨ `NATURAL` 假别 ⑩ 排班表为空 ⑪ 与多条已有单重叠（须返回最早/最相关的一条，口径与基线一致） ⑫ 账期锁跨两个月各命中一次。

---

## 8. S6 · 工单多目标派单

### 8.1 问题建模

**输入**：工单（`type, priority, createdTime`）、候选员工集（技能画像 `skillShare[type]`、当前负载、就绪时刻 `freeAt`）、SLA 阈值。

**输出**：处理人 + 派单理由（各分项分值与解释）。

**目标函数**（加权标量化，权重可配）：

```
score(e) = wUrgency · priorityUrgency · readiness(e)
         + wSkill   · skillShare(e, type)
         + wLoad    · (1 − handled(e) / maxHandled)
         + wSpeed   · readiness(e)
readiness(e) = 1 − min(1, max(0, freeAt(e) − now) / slaHours(priority))
priorityUrgency = (priority + 1) / 3          // 低=1/3, 中=2/3, 高=1
argmax_e score(e)  →  派单；若无候选 → 不指派（转人工）
```

**类型/优先级判定**（同一场景的第二处改造）：基线「关键词顺序命中」→ 改为**关键词特异度加权打分**（`weight × keyword.length`），消除「宽泛词先于具体词」的误判。

**约束**：① 候选人必须在职且启用；② 站长/处理人转单只能本站内（跨站仅 ADMIN）；③ 转单对象不能是操作人本人；④ 无候选时不指派（不阻塞建单）。

**边界**：全部员工超载、候选为 0、内容含多个关键词、无关键词命中（兜底类型 4 + 优先级 1）、`freeAt` 已过期、SLA 阈值缺失、单员工驿站。

### 8.2 算法选型与复杂度

**选型**：**多目标加权和（weighted-sum scalarization）+ 离散事件仿真验证**。不用 NSGA-II/多目标进化算法。

| 环节 | 时间 | 空间 |
| ---- | ---- | ---- |
| 类型判定（加权打分） | O(R · |content|) | O(1) |
| 候选人打分 | **O(E)** | O(1) |
| 单工单派单 | **O(E)** | O(1) |
| 整批仿真 | O(T · E + T log T)（含按创建时间排序） | O(T + E) |

**最坏情况**：T=5000、E=20 → 实测 18.6 ms。**规模不敏感**（E 为站点级常数），无需启发式搜索。

**为什么不用 NSGA-II/多目标进化**：决策变量是「每单选一个人」的在线贪心问题，无跨单耦合约束（除负载累积），加权和可在 O(E) 内给出确定解；进化算法仅适用于解空间耦合、需要 Pareto 前沿的离线规划问题。`TODO(扩展): 若引入「片区/时段/技能组合」等跨单耦合约束，再评估 CP-SAT 或 NSGA-II`。

### 8.3 依据

- **多目标加权和（scalarization）**：Marler, R.T. & Arora, J.S. (2004). *Survey of Multi-Objective Optimization Methods for Engineering*. Structural and Multidisciplinary Optimization 26:369-395 —— 加权和是最简单、可解释、可审计的标量化方法；其局限（无法得到非凸 Pareto 前沿）在本场景不构成问题。
- **Jain 公平指数**：Jain, R., Chiu, D., Hawe, W. (1984). *A Quantitative Measure of Fairness and Discrimination for Resource Allocation in Shared Systems*. DEC Technical Report DEC-TR-301 —— 负载均衡的标准度量，本方案用于量化「技能优先 vs 负载均衡」的取舍。
- **离散事件仿真**：Law, A.M. & Kelton, W.D., *Simulation Modeling and Analysis* —— 用 `freeAt` 累积实现「服务器队列」模型，用于在无生产环境时比较派单策略。
- **关键词特异度加权**：信息检索中的 TF-IDF 直觉（稀有/具体词权重更高）；本方案以「关键词长度 × 可配权重」近似，权重可由老板端配置。

### 8.4 可验证指标与基准数据

脚本：`algo-scripts/s6-dispatch.mjs`（20 员工，2000 工单/240h，离散事件仿真，种子固定）

| 方案 | 未派单率 | 技能匹配率 | 平均分配等待 | 平均完成时长 | SLA 达成率 | 负载 Jain | 处理量标准差 |
| ---- | -------- | ---------- | ------------ | ------------ | ---------- | --------- | ------------ |
| **基线·不派单（人工认领）** | **100%** | 89.8% | 7.095 h | **9.447 h** | **78.25%** | 0.9927 | 8.778 |
| 经验补丁·轮转指派 | 0% | 89.3% | 0.024 h | 1.693 h | 100% | **1.0000** | 0 |
| **算法·多目标加权** | 0% | **100%** | 0.034 h | **1.504 h** | **100%** | 0.9779 | 15.434 |

- 类型判定：12 条歧义样本，**顺序命中 0.8333 vs 加权打分 1.0000**；误判样本 `包裹破损且客户投诉`、`门禁故障引起客户投诉`（宽泛词 `破损`/`故障` 排在 `投诉` 之前被抢先命中）。
- 权重敏感性（说明权重必须由用户定，见 Q5）：重技能 → Jain 0.7857 / 完成 2.032 h；**重负载均衡 → Jain 0.9996** / 完成 1.504 h；默认 → Jain 0.9779。
- 规模：200 / 1000 / 5000 单 → 0.668 / 3.949 / 18.612 ms。
- **诚实结论**：多目标版相对「轮转补丁」的收益是**技能匹配 89.3%→100%** 与 **完成时长 1.693→1.504 h**，代价是 **负载 Jain 1.000→0.978**；相对「现状不派单」则是**质的改善**（SLA 78.25%→100%，完成时长 9.447→1.504 h）。是否接受公平性小幅下降，属 Q5 口径。

**单测边界清单**：① 候选为 0（全部停用/超载） ② 内容无关键词（兜底类型 4/优先级 1） ③ 内容含 2+ 关键词（加权取代顺序） ④ `freeAt` 已过期（等待 0） ⑤ SLA 阈值缺失 ⑥ 单员工驿站 ⑦ 全部工单同优先级 ⑧ 权重全 0（须兜底为「取第一个可用员工」） ⑨ 权重为负（须拒绝） ⑩ 与「转单」权限口径一致（本站/跨站）。

---

## 9. S7 · 包裹：趋势预测 / 驿站容量与热力 / 20 万级性能

### 9.1 问题建模

**(a) 趋势预测**
- 输入：日粒度入库量序列 `y[1..n]`（n≥14），预测步长 `h`（1~30）。
- 输出：`ŷ[n+1..n+h]` + 精度指标（MAE/MAPE）。
- 目标：最小化回测 MAE；**必须给出确定性、可解释的预测**（老板端展示「预计入库量」）。
- 边界：n < 2×季节周期、全零序列、突增/突降、节假日（`TODO(扩展): 引入节假日因子`）。

**(b) 驿站容量与热力**
- 输入：各驿站入库量、在库待取量、货架容量。
- 输出：利用率、预警等级（OK/WARN/CRITICAL）、离群驿站（IQR）。
- 判据：`utilization = pendingPickup / shelfCapacity`；`WARN ≥ utilWarn(0.8)`、`CRITICAL ≥ utilCritical(0.95)`；`IQR 离群` = 落在 `[Q1−1.5IQR, Q3+1.5IQR]` 之外。
- 边界：全站同一利用率（IQR=0）、单驿站、容量为 0、无在库包裹。

**(c) 20 万级查询性能与容量**
- 输入：`parcel` 表 20 万行、查询模式（按驿站/状态/时间倒序分页）。
- 输出：各量级耗时曲线、索引选择建议、落盘容量估算（与数据盘可用 46 G 对照）。
- 边界：深分页（第 1000 页）、无索引命中、运单号精确反查、全表聚合。

### 9.2 算法选型与复杂度

| 子问题 | 选型 | 时间 | 空间 | 最坏情况 |
| ------ | ---- | ---- | ---- | -------- |
| 趋势预测 | **Holt-Winters 加性季节**（备选 MA / Holt 线性 / 季节朴素） | 拟合 O(n)；预测 O(h) | O(m)（m=7） | n<2m 时降级 MA(7) |
| 驿站离群 | **IQR（Tukey 箱线图准则）** | O(S log S) | O(S) | S=7，常数级 |
| 分页 | **游标分页（keyset pagination）** | **O(log N + pageSize)** | O(pageSize) | 深分页 OFFSET 为 O(offset + pageSize) |
| 运单号反查 | 唯一键 `(station_id, waybill_no)` 等值查找 | O(log N) | O(1) | 与表长几乎无关 |
| 容量估算 | 行/索引字节模型 | O(1) | — | 精确值须服务器复核 |

**索引选择建议**（交数据库工程师实现，属 P4 结构先行 + 大表联评，见 §13）：

| 查询模式 | 建议索引 | 说明 |
| -------- | -------- | ---- |
| 按驿站 + 状态 + 入库时间倒序分页 | `idx_parcel_station_status_inbound (station_id, status, inbound_time DESC)` | 覆盖筛选 + 排序，替代 `db.md` 预告的 `(station_id, status)`（后者需回表排序） |
| 按驿站 + 入库时间倒序（游标） | `idx_parcel_station_inbound (station_id, inbound_time DESC)` | 游标分页的定位键 |
| 运单号精确反查 | `uk_parcel_station_waybill (station_id, waybill_no)` | 与 `db.md` §6.2 预告一致 |

> 说明：MySQL 8.0 支持降序索引（`DESC`）；若数据库工程师评估后选择升序索引，游标分页仍可正常使用（仅排序方向相反，改为 `ORDER BY inbound_time ASC` 需同步调整游标比较方向）。

### 9.3 依据

- **指数平滑族**：Hyndman, R.J. & Athanasopoulos, G., *Forecasting: Principles and Practice* 3rd ed. (OTexts, 2021, 开源电子书) ch.8 —— Holt 线性（§8.2）、Holt-Winters 季节法（§8.3）、季节朴素（§8.4）的定义与参数含义；Holt, C.C. (1957) 与 Winters, P.R. (1960) 为原始论文。
- **MAPE 的局限**：FPP3 §5.8 指出 MAPE 在实际值接近 0 时不可用 —— 本方案在 `lib/stats.mjs#mape` 中**跳过实际值 < 1e-9 的样本**（避免除零放大）。
- **工程实现对照**：statsmodels `ExponentialSmoothing(trend='add', seasonal='add')` 的公式与本方案一致（本方案只借鉴公式，未引依赖）。
- **IQR / 箱线图离群**：Tukey (1977) *Exploratory Data Analysis*（同 S4）。
- **深分页与延迟关联**：MySQL 8.0 官方手册 *LIMIT Query Optimization*（"Using an index to avoid a filesort" / "LIMIT with OFFSET requires scanning offset rows"）；`db.md` §6.2 已预告「避免 `SELECT *` 与深度分页（游标/延迟关联）」，本方案给出可量化的行数模型佐证。
- **InnoDB 容量估算**：MySQL 8.0 官方手册 *InnoDB Row Formats* / *InnoDB Page Structure*；精确值须在服务器执行 `SHOW TABLE STATUS LIKE 'parcel'`（看 `Data_length` / `Index_length`）。

### 9.4 可验证指标与基准数据

脚本：`algo-scripts/s7-parcel.mjs`（30 天序列，训练 23 / 回测 7；7 驿站；1k/1w/5w/20w 四量级）

**(a) 预测回测**（实际均值 889.1，标准差 55.3）

| 模型 | MAE | MAPE | 预测均值 |
| ---- | --- | ---- | -------- |
| **Holt-Winters(7)** | **46.90** | **5.34%** | 922.4 |
| Holt 线性 | 50.02 | 5.90% | 922.2 |
| MA(7) | 54.04 | 6.05% | 880.8 |
| 季节朴素（上周同日） | 55.29 | 6.10% | 872.7 |

→ Holt-Winters 相对朴素基线 MAE 改善 **15.2%**、MAPE 改善 **0.76 个百分点**；四个模型合计拟合+预测耗时 0.741 ms。

**(b) 驿站热力与容量**（货架位 900，WARN 0.8，CRITICAL 0.95）

| 驿站 | 入库 | 在库待取 | 利用率 | 预警 |
| ---- | ---- | -------- | ------ | ---- |
| 1（城东） | 1080 | 753 | **0.8367** | **WARN** |
| 2 | 840 | 648 | 0.7200 | OK |
| 6 | 723 | 571 | 0.6344 | OK |
| 7 | 592 | 476 | 0.5289 | OK |

- 入库量 IQR：Q1 709.5 / Q3 781.5 / 离群站点 **[1, 7]**；利用率 IQR 无离群。
- 结论：驿站 1 同时是「入库量离群高」与「唯一 WARN」，双重信号一致 → 可作为**扩容/增援优先级**依据。

**(c) 20 万级查询性能**

| 数据量 | 基线全扫单次 | 游标分页单次 | MySQL 模型：首页扫描行 | 游标扫描行 | 深分页扫描行 | 深分页放大 |
| ------ | ------------ | ------------ | ---------------------- | ---------- | ------------ | ---------- |
| 1 千 | 0.0178 ms | 0.0170 ms | 20 | 30 | 20020 | 1000× |
| 1 万 | 0.0845 ms | **0.0022 ms** | 20 | 34 | 20020 | 1000× |
| 5 万 | 0.3702 ms | 0.0140 ms | 20 | 36 | 20020 | 1000× |
| **20 万** | **2.3707 ms** | **0.0047 ms** | 20 | **38** | **20020** | **1000×** |

- 内存实现（Mock 索引层）与 MySQL 行数模型的对照：20 万量级基线全扫 2.37 ms vs 游标 0.0047 ms（**≈500×**）；MySQL 侧游标扫描 **38 行**（索引深度 18 + 一页 20）vs 深分页第 1000 页扫描 **20020 行**（**527×**）。
- ⚠️ 内存曲线在 1 千量级存在噪声（offset > N，深分页无意义）；**MySQL 绝对耗时必须在服务器实测**（§12.3）。

**(d) 落盘容量估算（20 万行）**

| 项 | 数值 |
| -- | ---- |
| 行字节估算 | **131 B**（含 InnoDB 行头 6 + 事务 id 6 + 回滚指针 7） |
| 数据字节 | 26.20 MB |
| 二级索引字节（uk + station/status + inbound_time） | 11.83 MB |
| 合计（含 B+ 树页填充开销 ×1.15） | 44.39 MB |
| **保守合计（含 binlog + undo/redo ≈ ×2.5）** | **105.83 MB** |
| **占 46 G 数据盘** | **0.2247%** |

→ 结论：20 万包裹的存储占用约 **0.11 GB**，相对数据盘可用 46 G **完全无压力**；`db.md` §6.1「不提前分表」的判断得到量化支持。真正的瓶颈是**查询模式与索引**，而非磁盘容量。

**单测边界清单**：① 序列长度 < 2×季节周期 ② 全零序列 ③ 突增突降 ④ 单驿站 ⑤ 全站同利用率（IQR=0） ⑥ 容量为 0 ⑦ 深分页第 1000 页 ⑧ 游标到底（无更多数据） ⑨ 运单号不存在 ⑩ 全表聚合（20 万行） ⑪ 状态值非法（不在 0~4） ⑫ 时间边界（`inbound_time` 为 null）。

---

## 10. S8 · 通知与日志限频

### 10.1 问题建模

**(a) 通知限频**
- 输入：按「机器人/员工」维度的发送请求流（到达时刻），配置 `bucketCapacity` / `refillPerSecond` / `maxQueueWaitSeconds`。
- 输出：实际发送时刻、排队等待、丢弃数、峰值每秒发送量。
- 约束：① 长期平均发送速率 ≤ `refillPerSecond`；② 瞬时突发 ≤ `bucketCapacity`；③ 排队超过 `maxQueueWaitSeconds` 即丢弃（不无限积压，避免内存爆）。
- 边界：空请求流、单请求、恒定满速到达、瞬时雪崩（≥10× 速率）、`capacity=0`（纯速率限制）、`refill=0`（仅突发）。

**(b) 客户端日志指纹去重与窗口聚合（服务端化）**
- 输入：三端上报日志流（`time, level, source, employeeId, route, message, stack, method, path, status, code, duration, ua`）。
- 输出：按 `指纹 = message|route|code` 聚合后的条目（`count, firstTime, lastTime`）。
- 约束：① 白名单脱敏（只复制允许字段）+ 凭据串正则擦除（`token=` / `password=` 等）；② 单条载荷 ≤ `textMax`；③ 窗口内同指纹累加 `count`。
- 边界：全同指纹、全不同指纹、时间乱序上报、跨窗口边界、超长 message/stack、含凭据串的 message。

### 10.2 算法选型与复杂度

| 子问题 | 选型 | 时间 | 空间 | 最坏情况 |
| ------ | ---- | ---- | ---- | -------- |
| 通知限频 | **令牌桶（token bucket）+ FIFO 队列 + 等待上限** | O(R log R)（事件排序）+ O(R) 队列 | **O(bucketCapacity + 队列上限)** | 队列受 `maxQueueWait` 约束，内存有界 |
| 日志聚合（基线） | 环形缓冲 + **线性指纹查找** | **O(rows × bufferCap)** | O(bufferCap) | 缓冲满即丢最旧（历史聚合信息丢失） |
| 日志聚合（算法化） | **Map 指纹索引 + 惰性窗口清扫** | **O(rows)**（均摊） | O(窗口内独立指纹数) | 窗口内指纹数上界可控 |

**为什么令牌桶而非漏桶/固定窗口计数**：令牌桶允许**可控突发**（对「一次发布推送给驿站全员」这类合法突发友好），且实现为 O(1) 状态（tokens + lastRefill），天然支持多维度（每机器人/每员工一个桶）。固定窗口计数存在「窗口边界双倍突发」缺陷。

**并发注意（交后端实现）**：多实例部署时桶状态须落在 **Redis**（`INCR` + 时间戳或用 Lua 脚本原子化），本地内存桶只能用于单实例；本方案给出的是**算法与参数**，实现属后端工程师（`TODO(扩展): 补充 Redis 令牌桶 Lua 脚本设计`）。

### 10.3 依据

- **令牌桶语义**：IETF RFC 2697（srTCM，单速率三色标记）、RFC 2698（trTCM，双速率三色标记）—— 「突发 ≤ 桶深、长期平均 ≤ 速率」的标准定义。
- **工程实现参考**：Google Guava `RateLimiter`（平滑预热令牌桶）、Bucket4j（Redis 分布式令牌桶）—— 借鉴其「桶深 + 补充速率」参数形态，**不照搬实现**（Guava 依赖在服务端可避免）。
- **日志指纹与降噪**：分布式系统日志去重（fingerprint/dedup）是常规做法，参见 Elastic 官方文档 *Log deduplication* / *Fingerprinting* 的思路（按模板聚合、累加计数）；本方案以「message|route|code」为指纹键，与 Mock `clientLogStore.fingerprintOf` 逐字一致。
- **白名单复制而非黑名单删除**：Mock `sanitizeLog` 注释已点明「黑名单在新增字段时必然漏脱敏」——本方案沿用并强化（叠加凭据正则擦除），与 `api.md` §7.5 的脱敏要求一致。

### 10.4 可验证指标与基准数据

脚本：`algo-scripts/s8-ratelimit.mjs`（3 机器人；日志 10 万条、2000 指纹池、窗口 10s，种子固定）

**(a) 令牌桶**（桶深 10、速率 5/s、等待上限 30s；到达约 960/机器人，含 20s 周期性高峰）

| 指标 | 限频后 | 无限制基线 |
| ---- | ------ | ---------- |
| 到达数 | 960 | 960 |
| 发送数 | 779 ~ 824 | 960 |
| 丢弃数（超等待上限） | 136 ~ 181 | 0 |
| **峰值每秒发送** | **12 ~ 14** | **23** |
| 平均等待 | 24.8 s | 0 |
| P95 等待 | 43.5 s | 0 |

→ 峰值被压到「桶深 + 速率」量级（23→14，**−39%**），代价是平均等待升至 ~25 s 且约 **17%** 请求因超过 30 s 等待上限被丢弃 —— **丢弃上限与等待上限属口径，见 Q5/Q6 范畴，须用户定**。

**(b) 日志指纹聚合**（10 万条原始日志）

| 指标 | 基线（线性查找 + 环缓冲） | 窗口 Map 聚合 |
| ---- | ------------------------- | ------------- |
| 聚合后条数 | 2000（受缓冲上限截断） | **720** |
| 总条数（校验） | 3372（**信息丢失**） | 6355（守恒） |
| 耗时 | 7087.6 ms | **69.8 ms** |
| 加速比 | — | **101 ~ 114×** |
| 降噪比 | — | **0.72%**（聚合后/原始） |
| 内存代理（字节） | 9.90 MB 原始 | **49.7 KB** 聚合 |
| 压缩比 | — | **199.3×** |

→ 基线除慢之外还有**正确性缺陷**：环形缓冲上限 200（服务端放大到 2000 测）**会丢失历史聚合计数**（总条数只剩 3372 / 应为 6355）；窗口 Map 保留了完整计数且内存有界。

**单测边界清单**：① 空请求流 ② 单请求 ③ `capacity=0` ④ `refill=0` ⑤ 雪崩（10× 速率） ⑥ 全同指纹 ⑦ 全不同指纹 ⑧ 时间乱序 ⑨ 跨窗口边界（`lastTime` 恰好 = 窗口长度） ⑩ 超长 message/stack（截断） ⑪ message 含 `?token=xxx`（须擦除） ⑫ 白名单外字段（须丢弃） ⑬ 多实例并发（桶状态在 Redis，须另行集成测试）。

---

## 11. 参数外置总表（`hrm.algo.*`）

> 所有键均为**默认值**，与离线原型中的常量一一对应；实现侧须逐键暴露为配置，禁止内联常量。

### 11.1 KPI

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.kpi.linearCapRatio` | `1.0` | LINEAR 模式下达成率封顶系数 | (0, 10] |
| `hrm.algo.kpi.tieredTiers` | `[{1.0,1.0},{0.9,0.9},{0.8,0.8},{0.6,0.6},{0,0}]` | TIERED 阶梯（minAchievement→ratio，降序） | 数组，ratio∈[0,2] |
| `hrm.algo.kpi.levels` | `[{90,EXCELLENT},{80,GOOD},{70,PASS},{0,IMPROVE}]` | 等级阈值（降序） | 数组，min∈[0,100] |
| `hrm.algo.kpi.quantile.enabled` | `false` | 是否启用分位映射 | true/false |
| `hrm.algo.kpi.quantile.tiePolicy` | `MID_RANK` | 并列处理策略 | MID_RANK / MIN / MAX |
| `hrm.algo.kpi.quantile.minSamples` | `20` | 启用分位的最小样本量（不足则降级） | [2, 10000] |
| `hrm.algo.kpi.weightSumTarget` | `100` | 启用权重合计目标 | 固定 100 |
| `hrm.algo.kpi.weightSumTolerance` | `0` | 权重合计容差 | [0, 5] |

### 11.2 工资

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.payroll.attendanceFieldMap` | `{LATE:lateCount,EARLY_LEAVE:earlyLeaveCount,ABSENT:absentCount,ABNORMAL:abnormalCount,LEAVE:leaveCount}` | 考勤指标 → 统计字段映射 | 枚举映射 |
| `hrm.algo.payroll.defaultCapRatio` | `1.0` | 未显式配 `capRatio` 时的默认上限 | (0, 5] |
| `hrm.algo.payroll.unknownSourcePolicy` | `ZERO` | 未知 source 处理 | ZERO / THROW |
| `hrm.algo.payroll.allowNegativeNet` | `false`（**待 Q3 确认**） | 是否允许负净额 | true/false |
| `hrm.algo.payroll.itemCapSemantics` | `ZERO_MEANS_NO_CAP` | `cap<=0` 语义（与 Mock 一致） | ZERO_MEANS_NO_CAP / ZERO_MEANS_ZERO |

### 11.3 排班

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.schedule.minPerShift` | `2` | 每班每日最少在岗 | [0, 50] |
| `hrm.algo.schedule.maxConsecutiveWork` | `5` | 连续工作上限（天） | [1, 14] |
| `hrm.algo.schedule.restCycleDays` | `6` | 轮休周期（天） | [2, 30] |
| `hrm.algo.schedule.weights.minStaff` | `1000` | 最少在岗违规罚权 | ≥0 |
| `hrm.algo.schedule.weights.consecutive` | `1000` | 连续工作违规罚权 | ≥0 |
| `hrm.algo.schedule.weights.coverageDeficit` | `10` | 覆盖缺口罚权 | ≥0 |
| `hrm.algo.schedule.weights.shiftBalance` | `1` | 班次均衡罚权 | ≥0 |
| `hrm.algo.schedule.weights.restSpread` | `0.1` | 轮休均衡罚权 | ≥0 |
| `hrm.algo.schedule.sa.iterations` | `6000` | 退火迭代数 | [0, 200000] |
| `hrm.algo.schedule.sa.initialTemp` | `8` | 初始温度 | >0 |
| `hrm.algo.schedule.sa.cooling` | `0.9995` | 降温系数 | (0,1) |

### 11.4 异常检测

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.attendance.anomaly.useRobust` | `true` | 用 median/MAD（true）或 mean/std（false） | true/false |
| `hrm.algo.attendance.anomaly.lateWarn` | `3.5` | 迟到频次 z 告警阈值 | [1, 10] |
| `hrm.algo.attendance.anomaly.lateCritical` | `5.0` | 迟到频次 z 严重阈值 | [warn, 20] |
| `hrm.algo.attendance.anomaly.consecutiveAbsent` | `3`（**待 Q6 确认**） | 连续缺卡预警阈值（天） | [1, 15] |
| `hrm.algo.attendance.anomaly.minSamples` | `5` | 最小样本量（不足则不出结论） | [1, 1000] |
| `hrm.algo.attendance.anomaly.windowDays` | `30` | 观察窗口（天） | [7, 180] |

### 11.5 请假

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.leave.maxLeaveDays` | `30` | 单次连续跨度上限（自然天） | [1, 365] |
| `hrm.algo.leave.occupiedStatus` | `[PENDING_STATION,PENDING_BOSS,APPROVED]` | 占用额度的状态集 | 枚举集合 |
| `hrm.algo.leave.lockStatusPolicy` | `NON_DRAFT_REJECTED` | 账期锁判定：非 DRAFT/REJECTED 即锁 | 枚举 |

### 11.6 派单

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.dispatch.slaHours` | `{0:48, 1:24, 2:8}` | 按优先级 SLA（小时） | 每项 >0 |
| `hrm.algo.dispatch.weights.urgency` | `4.0` | 紧迫度权重 | ≥0 |
| `hrm.algo.dispatch.weights.skill` | `2.5` | 技能匹配权重 | ≥0 |
| `hrm.algo.dispatch.weights.load` | `1.5` | 负载均衡权重 | ≥0 |
| `hrm.algo.dispatch.weights.speed` | `2.0` | 就绪度权重 | ≥0 |
| `hrm.algo.dispatch.keywordWeights` | `{破损:1.0, 丢失:3.0, 故障:1.0, 投诉:2.5}` | 关键词特异度权重 | 每项 >0 |
| `hrm.algo.dispatch.defaultType` | `4` | 无关键词兜底类型 | [1,4] |
| `hrm.algo.dispatch.defaultPriority` | `1` | 无关键词兜底优先级 | [0,2] |

### 11.7 包裹

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.parcel.forecast.model` | `HOLT_WINTERS` | 预测模型 | MA / HOLT / HOLT_WINTERS / SEASONAL_NAIVE |
| `hrm.algo.parcel.forecast.alpha` | `0.5` | 水平平滑系数 | (0,1) |
| `hrm.algo.parcel.forecast.beta` | `0.2` | 趋势平滑系数 | (0,1) |
| `hrm.algo.parcel.forecast.gamma` | `0.4` | 季节平滑系数 | (0,1) |
| `hrm.algo.parcel.forecast.seasonPeriod` | `7` | 季节周期（天） | [2, 30] |
| `hrm.algo.parcel.forecast.horizonDays` | `7` | 预测步长（天） | [1, 30] |
| `hrm.algo.parcel.capacity.shelfCapacity` | `900` | 站均货架位（件） | >0 |
| `hrm.algo.parcel.capacity.utilWarn` | `0.8` | 利用率告警阈值 | (0,1] |
| `hrm.algo.parcel.capacity.utilCritical` | `0.95` | 利用率严重阈值 | [warn, 1] |
| `hrm.algo.parcel.outlier.iqrK` | `1.5` | IQR 离群系数 | (0, 5] |
| `hrm.algo.parcel.page.pageSize` | `20` | 默认页大小 | [1, 100] |
| `hrm.algo.parcel.page.mode` | `CURSOR` | 分页模式 | CURSOR / OFFSET |

### 11.8 限频与日志

| 键名 | 默认值 | 含义 | 取值范围 |
| ---- | ------ | ---- | -------- |
| `hrm.algo.ratelimit.bucketCapacity` | `10` | 桶深（突发上限） | ≥1 |
| `hrm.algo.ratelimit.refillPerSecond` | `5` | 补充速率（个/秒） | >0 |
| `hrm.algo.ratelimit.maxQueueWaitSeconds` | `30` | 排队等待上限 | [0, 3600] |
| `hrm.algo.ratelimit.dimension` | `BOT_AND_EMPLOYEE` | 限频维度 | BOT / EMPLOYEE / BOT_AND_EMPLOYEE |
| `hrm.algo.log.dedupeWindowSeconds` | `10` | 指纹去重窗口（秒） | [1, 3600] |
| `hrm.algo.log.textMax` | `2000` | 单字段文本截断长度 | [100, 20000] |
| `hrm.algo.log.sweepEvery` | `2000` | 惰性窗口清扫间隔（条） | [100, 100000] |
| `hrm.algo.log.ringBufferCap` | `200` | 兜底环缓冲上限（仅降级路径） | [50, 100000] |

---

## 12. 性能关口与 TODO(扩展)

### 12.1 性能关口（1 千 / 1 万 / 5 万 / 20 万量级）

| 场景 | 1 千 | 1 万 | 5 万 | 20 万 | 关口判定 |
| ---- | ---- | ---- | ---- | ----- | -------- |
| S7 包裹分页（内存，游标） | 0.0170 ms | 0.0022 ms | 0.0140 ms | 0.0047 ms | **不随量级劣化** ✅ |
| S7 包裹分页（内存，基线全扫） | 0.0178 ms | 0.0845 ms | 0.3702 ms | 2.3707 ms | 线性劣化（O(N)）⚠️ 见 TODO-1 |
| S7 MySQL 模型（游标扫描行） | 30 | 34 | 36 | 38 | 对数级 ✅ |
| S7 MySQL 模型（深分页扫描行） | 20020 | 20020 | 20020 | 20020 | 与 offset 成正比 ⚠️ 见 TODO-1 |
| S3 排班（n=8/20/50） | — | — | 3.296 ms | — | 线性 ✅ |
| S6 派单（T=200/1000/5000） | 0.668 ms | 3.949 ms | 18.612 ms | — | 线性 ✅ |
| S8 日志聚合 | — | — | — | 100k→69.8 ms | 线性 ✅ |
| S5 区间算法 | — | — | — | 20000 次→67.7 ms | 对数级 ✅ |
| S5 逐日法（基线对照） | — | — | — | 20000 次→1253.5 ms | **明显劣化** ❌ 已由区间法替换 |

### 12.2 TODO(扩展) 清单（登记项）

| ID | 位置 | 内容 | 触发条件 |
| -- | ---- | ---- | -------- |
| TODO-1 | S7 分页 | 深分页（OFFSET）在 20 万量级扫描 20020 行，绝对耗时须服务器实测；若实测 > 100 ms 则**强制游标分页**并考虑「延迟关联」写法 | 服务器压测后 |
| TODO-2 | S3 排班 | n>200 或多驿站联合排班时，评估引入 Timefold Solver / OR-Tools CP-SAT（当前 SA 需 O(iter·T·n)） | 规模增长 |
| TODO-3 | S4 异常检测 | 特征维 ≥8（迟到/早退/缺卡/异常卡/时段分布）时，评估 Isolation Forest / LOF | 特征扩展 |
| TODO-4 | S2 规则引擎 | 规则项 >200 或出现复杂条件组合时，评估决策表引擎（Drools / Easy Rules） | 规则复杂度上升 |
| TODO-5 | S6 派单 | 引入片区/时段/技能组合等跨单耦合约束时，评估 CP-SAT / NSGA-II | 约束耦合 |
| TODO-6 | S8 限频 | 多实例部署时补 **Redis 令牌桶 Lua 脚本**设计（原子 `refill+take`） | 多实例上线前 |
| TODO-7 | S7 预测 | 引入节假日因子（春节/双 11 等）与促销事件回归量 | 业务确认节假日影响 |
| TODO-8 | S1 KPI | 分位模式的等级阈值重标定（见 Q2），须用户裁定后实现 | 用户裁定 |
| TODO-9 | S8 日志 | 日志指纹「模板化」（数字/ID 通配）以合并形近指纹 | 误报率观察 |
| TODO-10 | S3 排班 | 员工偏好（不排晚班/固定休周几）建模为软约束 | 用户确认需求 |

### 12.3 「收敛到服务器复核」清单（本机无 JDK/MySQL/Redis，以下未经实测）

| 项 | 复核方法 | 通过标准（建议） |
| -- | -------- | ---------------- |
| MySQL 真实分页耗时 | `GET /api/v1/parcel` 首页与第 1000 页，`EXPLAIN ANALYZE` | 游标首页 <20 ms；深分页若不达标签发 TODO-1 |
| `parcel` 真实落盘字节 | `SHOW TABLE STATUS LIKE 'parcel'` 取 `Data_length`/`Index_length` | 与估算 105.83 MB 同量级（±50%） |
| 索引命中 | `EXPLAIN` 确认走 `idx_parcel_station_status_inbound` | `type=range/ref`，无 `Using filesort` |
| Redis 令牌桶 | 单实例 + 多实例并发压测 | 峰值 ≤ `bucketCapacity`，无超发 |
| Flyway 迁移 | 服务器执行迁移并校验快照 | 迁移脚本新版本号、不改历史脚本 |
| 排班 500ms 交互目标 | 服务器侧 50 人 × 31 天实例化排班 | 端到端 <1 s |

---

## 13. 与 `api.md` / `db.md` 的一致性核对（守契约）

| 核对项 | `api.md` / `db.md` 现口径 | 本方案 | 结论 |
| ------ | ------------------------- | ------ | ---- |
| 接口契约 | `api.md` §4.0 一期 24 接口 + §7 M11 16 端点 | 不改任何请求/响应字段、不新增接口；算法全部在 Service 内部 | **一致，无冲突** |
| 错误码 | `api.md` §2 / §7.2（含 9604/9606 等） | S5 沿用 9604/9606；S6 沿用 8001~8006；S2 沿用 9405 | **一致，无冲突** |
| `parcel` 表结构 | `db.md` §6.2 预告：复合索引 `(station_id,status)`、唯一键 `(station_id,waybill_no)`、避免深分页 | 建议**追加** `idx_parcel_station_status_inbound (station_id,status,inbound_time DESC)` 与 `idx_parcel_station_inbound (station_id,inbound_time DESC)` | **不冲突，属追加建议**；大表加索引须数据库 + 算法联评（P4 + §6.8），执行属 C 档 |
| 20 万承载能力 | `db.md` §6.1「不提前分表」 | S7 估算保守占用 105.83 MB（0.22% / 46 G） | **一致，量化支持** |
| `leave_request` 表 | `db.md` §7.1（含 `natural_days` / `counted_days` / `counted_days_snapshot` / `idx_leave_request_date`） | S5 计薪天数与快照口径完全对齐；区间相交可走 `idx_leave_request_date` | **一致，无冲突** |
| `client_log` 表 | `db.md` §7.3（含 `count` / `first_time` / `last_time`）+ `api.md` §7.5 脱敏要求 | S8 聚合产出 `count/firstTime/lastTime`，白名单 + 正则擦除 | **一致，无冲突** |
| KPI / 工资 / 考勤表 | `db.md` 一期无这三类表（Demo 增量只在 Mock 持久化桶） | 本方案不改表结构；若需落库由数据库工程师按 P4 出 DDL | **无需变更** |

**结论：无阻塞冲突，可继续；无需停下回报主智能体。** 唯一需数据库侧联评的是 S7 的两条追加索引建议（非契约冲突，属实现层优化，纳入 §12 TODO 与数据库工程师协作）。

---

## 14. 复现方式

```bash
# 一键复现全部 8 个场景（约 100 s，S5 含基线对照故最慢）
cd hrm-dev/docs/algo-scripts
node run-all.mjs

# 单场景
node s1-kpi.mjs
node s2-payroll.mjs
node s3-schedule.mjs
node s4-anomaly.mjs
node s5-leave.mjs
node s6-dispatch.mjs
node s7-parcel.mjs
node s8-ratelimit.mjs
```

- 环境：Node ≥ 18（本机 Node 24.19.0），**无第三方依赖**。
- 复现性：所有随机源来自固定种子（`lib/rng.mjs`，mulberry32，与 Mock `createRandom` 同实现）；**重复运行除耗时列外结果逐位一致**。
- 目录：`lib/rng.mjs`（PRNG）、`lib/stats.mjs`（统计）、`s1..s8-*.mjs`（场景）、`run-all.mjs`（汇总）。
- 基准数据**不含任何真实业务数据**：全部由固定种子合成。

---

## 15. 交接说明（给主智能体）

- **产出**：本文件 + `hrm-dev/docs/algo-scripts/`（11 个文件：`lib/` 2 + 场景脚本 8 + `run-all.mjs` 1）。
- **证据**：8 个脚本均实跑通过（`run-all.mjs` 输出「总计 8 个场景，失败 0 个」），关键数字已回填各场景 §x.4。
- **未运行项**：MySQL 绝对耗时、InnoDB 真实字节、Redis 令牌桶并发 → 见 §12.3。
- **事实性纠正 / 补充**：见 §5.4（朴素轮休的模数蕴含缺陷，属上游材料未点明的补充发现）；任务描述中的「145 条接口」本方案未逐条清点，不作为算法结论依据。
- **待用户裁定**：§2 的 Q1~Q6 共 6 条口径。
- **如何回滚**：本方案纯文档 + 独立脚本目录，`git rm -r hrm-dev/docs/algo-scripts` 并删除本文件即可完全回滚，**不影响任何生产工程**。
