# 驿站精灵（boss-h5）管理能力扩展 · 界面设计规范

> 版本 v1.3 ｜ 日期 2026-09-28 ｜ 角色：UI/UX 设计师（`express-station-ui-ux-designer`）；v1.3 由前端工程师按实现回填（见下行）
> 覆盖 6 项需求：① 首页宫格新增「财务管理」（含「员工工资设置」，并入算薪日设置）② 首页宫格新增「驿站管理」（驿站 CRUD + 站内员工/站长账号维护）③ 驿站助手工资单详情去流转状态步骤条 ④ 驿站精灵员工页补「历史工资单」⑤ 排班支持一天多班次 ⑥ 新建「审批中心」
> **v1.1 修订（本文档内）**：新增第 ⑦ 项需求 —— **首页宫格收敛**（10 → 6 项：移除补卡审批 / 工资单审核 / 入离职审批 / 请假审批，统一由「审批中心」承接）+ **审批中心体验优化**。修订落点：**② 章**（宫格改 6 项，最终态）、**⑧ 章**（审批中心最终态）、**⑪.7**（落地清单）、**⑫**（新增冲突登记）、**附录 B**（自检）；新增章节 **⑬** 承载 v1.1 变更说明、决策取舍、落地清单与冲突汇总。v1.0 的其余章节（③–⑦、⑨、⑩）**未改动**。
> **v1.2 修订（本文档内）**：新增第 ⑧ 项需求 —— **驿站精灵班次管理**（移动端可维护班次，入口在「考勤概览 → 考勤管理」）；**显式覆盖旧裁定 U-3**（原「引导到网页端维护班次」改为「移动端可维护班次」，见 ⑭.0）。修订落点：**①**（目标 / 范围 / 准入）、**②**（宫格不变声明）、**⑩**（触控 / 对比度 / 无障碍）、**⑪**（落地清单）、**⑫**（新增冲突 27–35）、**附录 B**（自检）；新增章节 **⑭** 承载班次管理设计全量（入口 / 列表 / 表单 / 因果表达 / 校验 / 四态 / 冲突 / 落地 / 取舍）。v1.0 / v1.1 的其余章节**未改动**。
> **v1.3 修订（本文档内 · 前端实现回填）**：**撤销 ⑭.0 的「不变项：打卡时段只读、不可在打卡规则页编辑」**——`/boss/attendance/rule` 的时段改为「**只读来源 + 可就地改起止（底层写班次 `PUT /shifts/{id}`）**」，见新增 **⑭.10**。修订落点：**⑭.0**（不变项改写）、**⑭.1-(3)**（两页关系与引导文案）、**⑭.4**（因果表达表）、**⑪.8**（清单 #38 补记）、**⑫**（新增冲突 36）、**附录 B**（自检新增 v1.3 行）。**唯一时间真源不变**（仍为班次；未给规则页新增第二条时段写入通道，`PUT /rule` 传 `checkPeriods` 仍 400）。
> 上游依据：设计系统真源 `demo-boss-ui-spec.md`、`payroll-ui-design.md`、`registration-ui-design.md`、`boss-wifi-and-station-design.md`；契约真源 `api.md`（§4.3 员工 / §4.5 驿站 / §4.11 注册 / §4.12 薪资）；字典单源 `hrm-clients/packages/shared/src/constants/dict.js`；角色常量 `.../constants/role.js`。
> **Token 真源声明**：颜色 / 表面 / 描边 / 状态族 / 间距 / 圆角 / 动效取自 `hrm-clients/packages/tokens/src/tokens.base.scss`；移动端私有层（字号阶梯、`--touch-min`、`--row-h-*`、`--tag-h`、`--step-*`、安全区、容器尺寸）取自 `hrm-clients/apps/boss-h5/src/styles/tokens.scss`（staff-h5 同名同值）。**本规范零新增 Token、零新增色值**；页面出现十六进制字面量即视为缺陷（`demo-boss-ui-spec.md §3.1`）。
> **验证声明**：本机**未运行**构建 / lint / 测试 / 门禁，**未做视觉走查**；文中对比度数值取自 `demo-boss-ui-spec.md 附录 A`、`registration-ui-design.md §11.1` 的既有实算（已标注来源），新增组合按同法推导，落地后须实测复核。
> **凭据红线**：本文**不含任何真实口令 / 密钥 / 凭据**。口令相关一律只描述交互（「由管理员设置 / 重置」），示例一律 `******`。
> **产出物性质**：本规范为**方案阶段产物**。依 `.trae/rules/智能体调度规则.md` **P0.6 / L8 技术评审闸门**，须主智能体 Review（并经技术评审）通过后前端方可按 Tokens 实现；本文**不改任何 `.vue` / `.js` / `.scss`、不改 `dict.js`、不改方案 / 契约 / `db.md`、不执行 git**。

---

## ① 设计目标与范围

### 1.1 目标（可判定）

| # | 目标 | 完成定义 |
| --- | --- | --- |
| G1 | 首页宫格给出 3 个新入口且**仍满足触控与不溢出** | 宫格 10 项、4 列 3 行；375px 下横向溢出 0px、每格可点区 ≥44×44；既有 7 项相对顺序不变。**v1.1 修订**：宫格收敛为 **6 项、4 列 2 行**（②.2），触控 / 不溢出要求不变 |
| G2 | 工资相关设置**收口到一处**，不再散在「我的」 | 「我的 → 管理与配置」中不再出现「算薪日设置」独立行；财务管理页含「员工工资设置」区块 |
| G3 | 驿站管理**可增可改**，员工与站长账号**在驿站内维护** | 驿站列表可新增/进入详情；详情内分「员工账号」「站长账号」两个区块，各自可增改 |
| G4 | 工资单详情**去步骤条后仍信息自洽** | 驿站助手详情仅「金额摘要 → 明细项 → 操作区」，无 `PayrollStatusSteps`；确认/提异议入口保留 |
| G5 | 员工页**可下钻历史工资单** | 员工档案页新增「历史工资单」区块，列表含月份/单号/应发/实发/状态，点行进入 `/boss/payroll/:id` |
| G6 | 排班**一天多班次可达** | 单日可多选班次，显示为累加标签；提供「全天」与「清空」快捷动作；冲突与上限有前置提示 |
| G7 | 审批**按类聚合、单一入口** | 审批中心含补卡/请假/入离职(含注册)/工资单审核/工资单异议 5 类，不含工单；与消息页待办同源 |
| G8 | 可访问性达标 | 关键色对 ≥4.5:1；触控目标 ≥44px；错误 `role=alert`；四态（loading/empty/error/normal）逐页齐备 |
| **G9** | **（v1.2）班次可在移动端维护，且「班次即打卡时段」的因果显式可见** | 「考勤概览 → 考勤管理」含「**班次管理**」入口；`/boss/shifts` 可增 / 改 / 停用启用 / 删除；列表顶部与表单各有一句因果说明；四态齐备（⑭） |

### 1.2 本批做 / 不做

| 分类 | 项 | 处置 |
| --- | --- | --- |
| **做** | 首页宫格新增 3 项 + 角标规则 + 顺序 | ② |
| **做** | 财务管理页（含员工工资设置）+ 算薪日设置并入 | ③ |
| **做** | 驿站管理列表 / 详情 / 新增编辑 / 员工账号 / 站长账号 | ④ |
| **做** | 驿站助手工资单详情骨架调整 | ⑤ |
| **做** | 员工档案页「历史工资单」区块 | ⑥ |
| **做** | 排班多班次交互（多选、全天快捷、冲突提示、触达） | ⑦ |
| **做** | 审批中心（含分类承载决策、注册标识、降级） | ⑧ |
| **做** | 状态—动作—页面对照、响应式与可访问性、交付清单、冲突登记 | ⑨–⑫ |
| **做（v1.2）** | **班次管理**：入口（考勤概览 → 考勤管理）+ 列表 + 新增 / 编辑 + 站点级前置校验 + 因果表达 + 四态 | ⑭ |
| **不做** | 计薪规则 / 排班算法口径 / 班次时段划分规则 | 交算法工程师；本文只定呈现与交互（登记见 ⑫-4） |
| **不做** | 任何接口契约与数据结构变更、Mock 变更、断言调整 | 交后端 / 数据库 / 前端按契约实现 |
| **不做** | 员工注册的申请侧页面（staff-h5 已有 `/register`）与注册审批流实现 | 本批只做审批中心的**标识与承载**；数据来源缺口见 ⑫-3 |
| **不做** | PC 端（web / hrm-admin）改动 | 超范围；如需实现须停下回报主智能体 |
| **不做（v1.2）** | 班次「是否被排班引用」的列表标识 | 契约 `GET /shifts` 出参无该字段（⑫-30）；删除时以服务端 400 拦截并给文案 |
| **不做（v1.2）** | 自由取色 / 任意颜色选择器 | 移动端取舍：仅提供设计 Token 内 **3 个受控配色**（⑭.3 / ⑭.9）；不引入色值字面量 |

### 1.3 端与准入（不新开口子）

| 端 | 前缀 | 准入 | 依据 |
| --- | --- | --- | --- |
| 驿站精灵 boss-h5 | `/boss/*` | **fail-closed 仅 ADMIN**（`BOSS_ROLES = [ROLE.ADMIN]`） | [router/index.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L9-L9)、[:236-L239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L236-L239) |
| 驿站助手 staff-h5 | `/staff/*` | STAFF / STATION_ADMIN | 既有守卫 |

本批新增页面**全部 `meta.roles = [ROLE.ADMIN]`**，不新开角色可见面；权限判定一律取服务端派生标志，前端不重复推导（`demo-boss-ui-spec §6.6`）。

> **v1.2 补充**：新增 `/boss/shifts`、`/boss/shifts/create`、`/boss/shifts/:id/edit` 三条路由，同样 `meta.roles = [ROLE.ADMIN]`。班次写操作（`POST/PUT/DELETE /shifts`）服务端**仅放行 ADMIN**；非 ADMIN 属防御位——只读列表、不渲染任何写按钮（与 PC `ShiftManager.vue` 的 `canWrite` 口径一致）。

---

## ② 首页宫格（v1.1：收敛为 6 项）

> **v1.1 变更**：依用户口径「审批中心有了以后，应把补卡审批、入离职审批、请假审批、工资单审核在首页去除，并入审批中心」，宫格由 v1.0 的 10 项（4 列 3 行）**移除 4 项** —— `makeups` 补卡审批 / `payrolls` 工资单审核 / `flows` 入离职审批 / `leaves` 请假审批，收敛为 **6 项（4 列 2 行）**。被移除的 4 类审批**统一由「审批中心」承接**：其数量并入「审批中心」角标与审批中心的页头汇总 / 分类计数，**信息不丢失**（见 2.3 末段）。

### 2.1 现状与约束

- v1.0 的 10 项（4 列 3 行）为本批历史版本；v1.1 收敛为 6 项。源文件：[quickEntries.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/constants/quickEntries.js)。
- 被移除的 4 类审批的**业务页与路由全部保留**（`/boss/attendance/makeup`、`/boss/payroll`、`/boss/flow`、`/boss/leave`）——它们是审批中心组头 / 明细行的跳转目标，仅**首页不再直挂入口**。
- `HomeQuickGrid` 仍为 4 列（`:column-num="4"`），对项数无硬编码；6 项自然落 2 行。
- 变体三选一互斥不变（`count` / `status` / `plain`）；**本版不新增任何 icon**——6 项 icon 均为既有实存值（v1.0 已核实）。

### 2.2 最终项序（6 项，4 列 2 行）

第 1 行 = **待办与概览**（count 型）；第 2 行 = **管理与配置**（非 count 型）。

| 位置 | key | 名称 | icon | type | 角标 / 副行规则 | to |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `approvals` | 审批中心 | `orders-o` | count | 见 2.3 | `/boss/approval` |
| 2 | `orders` | 工单管理 | `todo-list-o` | count | `todo.counts.orders`（null 无角标） | `/boss/workorder` |
| 3 | `attendance` | 考勤概览 | `records` | count | `attendanceAbnormal`（null 无角标） | `/boss/attendance` |
| 4 | `alerts` | 异常预警 | `warning-o` | count | `alertTotal` | `/boss/alerts` |
| 5 | `finance` | 财务管理 | `balance-o` | plain | 无角标 | `/boss/finance` |
| 6 | `stations` | 驿站管理 | `shop-o` | status | 副行 `共 {n} 个` | `/boss/station` |

**排序依据（可判定）：**
① **审批中心前移第 1**：移除 4 项后它是**审批的唯一入口**，也是管理者最高频的「清待办」入口；首格是视觉与单手触达的最优点位。
② **工单管理落第 2**：原第 1 位锚点仅后移一格、与审批中心同行相邻，可发现性不受损。
③ **存活项相对顺序一格不动**：`orders → attendance → alerts → finance → stations` 保持 v1.0 相对次序（`orders < attendance < alerts < finance < stations`），仅 `approvals` 由第 8 前移至第 1，避免大面积心智重置。
④ **行序语义**：第 1 行 = 待办与概览；第 2 行 = 管理与配置（与「我的 → 管理与配置」同域呼应）。
⑤ **宫格提示文案**：`home.vue` 传给 `HomeQuickGrid` 的 `hint` 由「按待办优先排序」改为 **「待办与概览在前」**（6 项下「待办优先」已不足以描述，且第 2 行为管理入口）。

> **取舍（登记）**：曾考虑把「异常预警」提到「考勤概览」之前（紧迫度更高），**本轮否决** —— 为保住第 ③ 条的顺序稳定性；如用户明确要求调整，按路由 R03 走算法 / 需求口径后再改。

### 2.2.1 10 → 6 项的过渡处理（布局取舍）

**结论：保持 4 列，6 项落 2 行（第 1 行 4 格 + 第 2 行 2 格，末 2 格自然留白）；不改为 2 行 3 列。**

| 维度 | 保持 4 列（取） | 改 2 行 3 列（否） |
| --- | --- | --- |
| 组件改动 | **零改动**（`HomeQuickGrid` 仍 `:column-num="4"`，对项数无硬编码） | 需给组件加列数变体或改硬编码 —— 该组件跨端共用（staff 端 9~10 项仍按 4 列），会引出第二套网格 |
| 一致性 | 与项目既有移动网格基准（宫格 / 指标卡同宽 `--row-h-tile`）一致 | 单元格由 ≈74px 变 ≈117px（375px 下），图标—文字间距与密度须重调 |
| 视觉完成度 | 末行留白 2 格（可接受；且第 2 行恰好承载「管理与配置」两条，语义自洽） | 满格无留白，但以「多改一处组件 + 打破跨端一致」为代价 |

**过渡动作**：① `quickEntries.js` 删去 4 项（`makeups`/`payrolls`/`flows`/`leaves`），其余 6 项字段结构不变；② `home.vue` 的 `quickData` 删去这 4 个 key（`makeups`/`payrolls`/`flows`/`leaves`）；③ `hint` 改文案（见 2.2 第 ⑤）。**不新增空态文案**（6 项恒非空，宫格既有「暂无可用的快捷功能」分支保留以兜底）。

### 2.3 角标规则（逐项可判定）

| 项 | 规则 |
| --- | --- |
| `approvals` 审批中心 | `approvalTotal = makeups + leaves + flows + payrolls + payrollObjections`（**不含 `orders` 工单**）。**未知传播**：5 组中**任一** `total === null` → 整值取 `null` → 不渲染角标（不用「部分和」冒充合计）。合计 0 → 不渲染角标（`badgeText` 口径）。>99 → `99+` |
| `orders` 工单管理 | `todo.counts.orders ?? null`（null 不渲染角标） |
| `attendance` 考勤概览 | `attendanceAbnormal = lateCount + earlyLeaveCount + absentCount`（`getAttendanceSummary` 既有出参）；取数失败 `null` → 不渲染角标 |
| `alerts` 异常预警 | `alertTotal = overdueUnhandled`（超时未处理工单） |
| `finance` 财务管理 | `plain`，**不渲染任何角标 / 副行**（设置类入口无实时待办语义） |
| `stations` 驿站管理 | `status`，副行 `共 {n} 个`，`n = dashboard.stationTotal`（`getDashboardSummary` 既有出参，**不新增接口**）；`loading` 时副行 `···`，取数失败时 `—`（`QuickGridItem` 既有三态，绝不用 0 冒充） |

**信息不丢失（对用户口径的交代）**：被移除的 4 类审批的待办数**未消失**——它们并入 `approvals` 角标（`approvalTotal`）与审批中心的页头汇总 / 分类计数（⑧.2、⑧.5）。用户在首页第 1 格看到「待我审批总数」，进审批中心看分类明细，**入口收敛但信息不减**。

**文案口径（避免与消息页计数打架）**：审批中心角标**不含工单**，消息 Tab 角标 `todo.total` **含工单**。二值不等属**预期**，须在审批中心页脚以一行 `.tip` 说明（见 ⑧.6），避免用户把「两个数字不一致」判为 bug。

### 2.4 「我的 → 管理与配置」的同步调整

| 行 | 现状 | 处置 |
| --- | --- | --- |
| 算薪日设置（`/boss/payroll-settings`） | [MeSection.vue:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.vue#L59-L59) | **移除该行**（已并入财务管理；路由保留以兼容深链，见 ③.5） |
| 自动算薪运行（`/boss/payroll-runs`） | [MeSection.vue:60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.vue#L60-L60) | **保留**（同一路由；财务管理页内亦给入口，见 ③.2，两处指向同一页**非第二套实现**） |
| 站点管理（`/boss/station`） | [MeSection.vue:54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.vue#L54-L54) | **改名为「驿站管理」**（同一路由），指向改造后的驿站管理页；不再保留「只读骨架」的 label 文案 |

> `MeSection.spec.js` 只断言「管理与配置 / 运行环境 / 账号信息 5 行 / 修改密码」，**未断言**「算薪日设置」「站点管理」文案 → 上述调整**不破坏既有护栏用例**（已核对 [MeSection.spec.js:50-70](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.spec.js#L50-L70)）。

### 2.5 v1.2 声明：班次管理**不进入**首页宫格

宫格保持 **6 项（②.2）不变**；「班次管理」入口落在「**考勤概览 → 考勤管理**」分组内（⑭.1），**不进首页宫格**。

理由：① 班次是考勤域的时间真源，与「打卡规则 / 排班管理 / 打卡记录」同域，放在考勤概览内与使用者动线一致；② 首页宫格第 2 行已承载「管理与配置」，再插入会破坏 ②.2.1 的「4 列 2 行」稳定布局；③ 若不进考勤概览而进首页，则「考勤概览 → 考勤管理」分组会缺失时间真源入口，用户仍会困惑「打卡时间从哪来」。

---

## ③ 财务管理 → 员工工资设置

### 3.1 信息架构（决策：设置聚合 hub，非二级套娃）

**决策：`payrollSettings.vue` 原地升级为「财务管理」页**（不新建页面、不新建组件），页内分三区块：

| 区块 | 内容 | 形态 |
| --- | --- | --- |
| A 员工工资设置 | 算薪日设置列表（一驿一卡）+ 筛选 + 上拉加载 | 主区块（沿用既有 `set-row` 行结构与 `getPayrollSettings`） |
| B 自动算薪运行 | 一行 `van-cell is-link` → `/boss/payroll-runs` | 入口行 |
| C 相关设置 | 请假扣款设置 → `/boss/leave/settings` | 入口行 |

**被否方案**：① 新建独立 `finance.vue` hub + 把算薪日列表抽成共享组件 —— 增加 1 文件 + 1 组件，收益仅是「同一列表两份 markup」的抽象，属过度设计；② 宫格「财务管理」直接进 `/boss/payroll-settings` 并仅改标题 —— 满足「算薪日并入」，但**无法承载**「自动算薪运行 / 请假扣款设置」的收口，仍留两个散落在「我的」的入口。**取原地升级**：0 新文件、0 新组件、入口收口。

### 3.2 页面骨架（财务管理页）

```
PageNav「财务管理」（back=true，二级页）
└─ .page.page--loose
   ├─ van-notice-bar ——「启用后系统在每驿站各自的算薪日自动生成工资单草稿并提交审核；未启用的驿站不自动跑数。」
   ├─ .section-title「员工工资设置」 + .section-title__extra「共 {n} 个驿站」
   │    ├─ van-field（label=驿站，placeholder=输入驿站名筛选，clearable，aria-label=按驿站名筛选）
   │    ├─ .filter-row[role=group]：fchip × （全部 / 已启用 / 未启用），aria-pressed
   │    ├─ PageState（loading rows=3 / error+retry / empty「还没有可配置的驿站」+ 出路「请先在 PC 端维护驿站」）
   │    └─ button.set-row × N（一驿一卡）
   │         主行：{stationName} + 状态胶囊（已启用=success/soft「已启用」；未启用=info/outline「未启用」）
   │         副行（tabular-nums）：`每月 {payrollDay} 日 {payrollTime}`；未配置 → 「尚未配置」（--color-danger 文字）
   │         右侧 van-icon arrow
   ├─ .section-title「自动算薪运行」
   │    └─ van-cell-group inset → van-cell「自动算薪运行」label=「查看运行记录与手工触发算薪」is-link → /boss/payroll-runs
   ├─ .section-title「相关设置」
   │    └─ van-cell-group inset → van-cell「请假扣款设置」label=「全局单开关：请假是否影响工资」is-link → /boss/leave/settings
   └─ .tip「工资单由财务端生成草稿后进入审核流；算薪日设置仅决定是否自动跑数与跑数时间。」
```

- 「员工工资设置」区块**点卡片 → 既有编辑页** `/boss/payroll-settings/:stationId`（无二次跳转中间页）。
- 三区块**同页单滚动**，不做 Tab（区块数 3、内容浅，Tab 会把 2 个入口藏起来）。

### 3.3 编辑页（既有 `payrollSettingEdit.vue`，仅改标题）

| 项 | 改为 |
| --- | --- |
| `PageNav :title` | `notConfigured ? '配置员工工资设置' : '员工工资设置'` |
| 校验 / 草稿 / 二次确认 / 变更历史 | **全部不动**（沿用 `payroll-ui-design.md §2.2–§2.4` 与 9406/9407/9408 文案口径） |

### 3.4 四态与错误文案（财务管理页）

| 态 | 表现 |
| --- | --- |
| loading | `PageState :rows="3"`（≤200ms 不显示骨架，防闪，组件内已实现）；区块标题与筛选控件**照常渲染**（不被整块骨架替换） |
| empty | `PageState empty`「还没有可配置的驿站」+ `#empty-action`：「请先在 PC 端维护驿站」（空态**不得**出现「失败/错误/网络」字样） |
| error | `PageState error` + 「重新加载」（≥44px）+ `role="alert"` |
| normal | 列表可点、筛选可切、状态胶囊与「尚未配置」正确 |
| 筛后空 | 过滤后为空时沿用 empty 文案（不新增第二套） |

**错误文案（与既有契约同义，不直接透传英文/堆栈）**

| 触发 | 文案 |
| --- | --- |
| 进入编辑页 `9406`（尚未配置） | 「该驿站尚未配置算薪设置，保存一次即可创建」（**非错误态**，表默认值呈现） |
| 算薪日非 1–31 | 「算薪日须为 1–31 的整数，月末自动钳位到当月最后一天」（`9407`） |
| 时间非 HH:mm | 「时间格式须为 HH:mm（如 09:00）」（`9408`） |
| 保存失败（其他） | 页面级 `role="alert"` + `e.message`；**禁止静默** |

### 3.5 路由与衔接

| 路由 | 处置 |
| --- | --- |
| `/boss/payroll-settings` | **保留为规范路径**，component 仍为 `payrollSettings.vue`；`meta.title` → 「财务管理」 |
| `/boss/finance` | 作为上一条的 **`alias`**（宫格 `to` 用它，命名对齐「财务管理」）；**不新增独立路由记录**，避免两条记录维护两份 meta |
| `/boss/payroll-settings/:stationId` | 保留；`meta.title` → 「员工工资设置」 |
| `/boss/payroll-runs`、`/boss/leave/settings` | 保留不动 |

> **为什么用 alias 而不是改路径**：既有深链、待办 `to`、通知跳转、`payroll-ui-design.md` 与 `MeSection` 均引用 `/boss/payroll-settings`；改路径会造断链。alias 使新旧路径同时可达，零迁移成本。

---

## ④ 驿站管理（列表 / 详情 / 站内账号维护）

> **口径说明（用户澄清，2026-09-27）**：**站长（STATION_ADMIN）只是员工账号上的一个身份，不是独立账号体系**。账号只有两类——**管理员账号**（登录驿站精灵 `/boss/`）与**员工账号**（登录驿站助手 `/staff/`）。故驿站详情以**一个统一账号列表**承载站内员工账号（含站长身份），**身份（员工 / 站长）是列表的筛选与标识维度**，不再拆「员工账号 / 站长账号」两个区块；新增时在表单内选择身份，两者共用同一套账号增改删接口（同一张 `employee` 表）。

### 4.1 信息架构（决策：列表 → 详情两页，账号在详情内）

```
/boss/station            驿站管理（列表）   ← 宫格「驿站管理」入口
   └─ /boss/station/create            新增驿站（表单页）
   └─ /boss/station/:id               驿站详情
        ├─ /boss/station/:id/edit         编辑驿站（表单页，复用新增表单）
        ├─ /boss/station/:id/account/create          新增账号（身份由入口预置：员工 / 站长）
        └─ /boss/station/:id/account/:employeeId     账号编辑（改资料 / 身份 / 状态 / 重置口令）
```

**路由注册顺序**：`/boss/station/create` 必须早于 `/boss/station/:id`（静态路径先于路径参数，沿用 `employee.js` 既有注释所强调的 engine「首个命中」规则）。

**为什么账号不放列表页**：① 列表页只见驿站，账号是驿站内实体，跨站混排会让「同名员工属哪个站」不可判；② 账号数量随驿站增长，列表页塞账号必然要做「两段展开」，不如直进详情；③ 与服务端数据范围一致（员工以 `stationId` 归属）。

**与既有 `station.vue` 的关系**：现 `station.vue` = 「选站 → 看该站员工名册（只读）」([station.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/station.vue))。本批**重构为详情页的「员工账号」区块**（名册语义被账号列表取代），原「先选站再看人」的入口级选站弹层**移除**（驿站已由详情页上下文确定）。`/boss/hr/:employeeId` 的复用关系不变（点账号行仍进既有员工档案页或账号编辑页，见 4.4）。

### 4.2 驿站管理列表页

| 项 | 规范 |
| --- | --- |
| 顶部 | `PageNav title="驿站管理"`（back=true） |
| 主操作 | 底部 `ActionBar`：`[{ key:'create', label:'新增驿站' }]`（单主操作，≥44px）。**不随状态变化**，不隐藏 |
| 筛选 | `van-search`（`placeholder="搜索驿站名或编号"`，shape=round）+ `.filter-row` fchip（全部 / 启用 / 停用，`aria-pressed`） |
| 计数行 | `.tool-row tabular-nums`「共 {n} 个驿站」 |
| 行字段 | 主行：`{stationName}` + `StatusTag(STATION_STATUS, status)`（0=停用/1=启用）<br>副行 1（tabular-nums）：`编号 {code}`<br>副行 2：`{contactPerson || '—'} · {contactPhone || '—'}`（电话**出参已脱敏**，前端不做二次脱敏也不解掩码）<br>副行 3（tabular-nums）：`员工 {employeeCount} 人`<br>右侧 arrow |
| 行高 | `list-item--rich` 四行 → ≥76px（`--row-h-3` 派生），整行可点 |
| 数据源 | `GET /stations`（ADMIN，**全量不分页**）。驿站基数 <100 → 关键字 + 状态筛选**在前端做**，**不新增分页请求**；列表按 `id` 升序（服务端既有排序） |
| 分页 | **不做上拉加载**（全量返回；<100 条无需分页）。若未来驿站规模≥100，再平级新增分页（`api.md §4.5.1` 已留出口） |

**四态**

| 态 | 表现 |
| --- | --- |
| loading | 3 行等高骨架（高度对齐 `list-item--rich`）；`ActionBar` 的「新增驿站」在 loading 期间**保持可点**（新增不依赖列表数据） |
| empty（无驿站） | `PageState empty`「还没有驿站」+ `#empty-action`「点下方『新增驿站』创建第一个驿站」 |
| empty（筛后无命中） | 「没有匹配的驿站」+ 说明「换个关键字或清除筛选」 |
| error | `PageState error` + 「重新加载」+ `role="alert"`；主操作仍可用 |
| normal | 行可点、状态胶囊正确、计数与列表一致 |

**错误文案**

| 触发 | 文案 |
| --- | --- |
| `400` 编码非法 | 「驿站编号须为 2–50 位字母/数字/下划线/中划线」 |
| `4002` 编码已存在 | 「该驿站编号已被使用，请更换」 |
| `400` 名称长度 | 「驿站名称须为 1–50 字符」 |
| `400` 电话格式 | 「联系人电话格式不正确」 |
| `404` | 「驿站不存在，请刷新后重试」 |
| `4003` 删除有员工 | 「该驿站下仍有员工，无法删除；请先调整员工归属」 |
| 网络 | 「网络异常，请检查网络后重试」 |

### 4.3 新增 / 编辑驿站表单

| 字段 | 契约字段 | 控件 | 必填 | 校验 | 错误文案 |
| --- | --- | --- | --- | --- | --- |
| 驿站编号 | `code` | 文本 | 是 | `^[A-Za-z0-9_-]{2,50}$`，活跃唯一 | 「驿站编号须为 2–50 位字母/数字/下划线/中划线」/「该驿站编号已被使用，请更换」 |
| 驿站名称 | `stationName` | 文本 | 是 | 1–50 字符 | 「驿站名称须为 1–50 字符」 |
| 联系人 | `contactPerson` | 文本 | 否 | ≤50 字符 | 「联系人不可超过 50 字符」 |
| 联系电话 | `contactPhone` | `type=tel` + `inputmode=numeric` | 否 | `^1[3-9]\d{9}$`（可空） | 「联系人电话格式不正确」 |
| 地址 | `address` | `textarea rows=2 maxlength=255` | 否 | ≤255 | 「地址不可超过 255 字符」 |
| 备注 | `remark` | `textarea rows=2 maxlength=255` | 否 | ≤255 | 「备注不可超过 255 字符」 |
| 状态 | `status` | `van-switch` | 编辑态才有 | 0/1 | — |

- **新增**：`POST /stations`（**不传 `status`**，服务端固定 `status=1`；表单**不出现**启用开关，避免"建了就是停用"的错觉）。成功后 `showSuccessToast('驿站已创建')` → `router.replace('/boss/station/:新id')`（直接进详情继续建账号）。
- **编辑**：`PUT /stations/{id}`（`code` 一期可改，二期爬虫上线后冻结，`api.md §4.5.3`）。成功 toast「驿站信息已保存」+ 就地刷新详情。
- **启用/停用**：`PUT /stations/{id}/status`。切「停用」须**二次确认**（影响面：停用后新增/编辑员工不可再归属该驿站，存量员工与登录不受影响）—— 用 `bossConfirm` 四要素：标题「停用驿站」/对象「{站名}」/影响「新增与编辑员工时不可再选择该驿站，存量员工归属与登录不受影响」/按钮「确认停用」。
- **删除**：`DELETE /stations/{id}`。**入口仅在详情页**，`bossConfirm`（`irreversible: true`）+ 前置校验「有员工则 4003」；`employeeCount>0` 时按钮**禁用 + 原因**「该驿站下仍有 {n} 名员工，无法删除」（遵循 `demo-boss-ui-spec §6.5` 禁用+原因，不隐藏）。

### 4.4 驿站详情页

```
PageNav「驿站详情」
└─ .page.page--bar（底部 ActionBar）
   ├─ .hero.hero--deep
   │    {stationName}  +  StatusTag(STATION_STATUS)
   │    编号 {code} · 员工 {employeeCount} 人
   ├─ .section-title「基本信息」
   │    van-cell-group inset：联系人 / 联系电话 / 地址 / 备注 / 创建时间
   ├─ van-search（单一搜索入口，同时过滤姓名与登录账号）
   ├─ FilterChips「按身份筛选」（全部 / 员工 / 站长；`aria-pressed`）
   ├─ .section-title「账号」 + extra「共 {n} 人」（n 为过滤后计数）
   │    ├─ 行列表（见 4.4.1，统一账号列表，行内展示身份）
   │    └─ van-button plain block「新增账号」→ /boss/station/:id/account/create?role=STAFF
   │         （当前身份筛选为「站长」时预置 role=STATION_ADMIN，具体身份仍可在表单内改）
   └─ .tip（账号作用域说明）
ActionBar：[{key:'edit',label:'编辑驿站'}, {key:'status',label: 启用?'停用驿站':'启用驿站', type:'danger'}]（删除入口放编辑页尾部或 ActionBar「更多」）
```

**行动作（底部）**：`编辑驿站`（主） / `停用·启用`（次，danger）；`删除驿站`（danger，仅 `employeeCount===0` 时可用，放 ActionBar 「更多」或编辑页底部）。

#### 4.4.1 账号列表行（统一账号列表，身份为一项维度）

| 行内容 | 说明 |
| --- | --- |
| 主行 | `{realName}` + 身份胶囊 `MiniChip(roleLabel(role))`（站长高亮、员工中性）+ `StatusTag(EMPLOYEE_STATUS, status)`（1=在职 / 0=已停用） |
| 副行 1 | `{username}`（登录账号） |
| 副行 2 | `{phone}`（出参已脱敏） |
| 副行 3 | `入职 {entryDate \|\| '—'}`（tabular-nums） |
| 行高 | ≥76px，整行可点 → 账号编辑页 |
| 空态 | 无筛选「该驿站还没有账号；点下方按钮新增」；筛后无命中「没有匹配的账号」 |
| 区块错误 | 区块级 error + 「重新加载」（不替换整页） |
| 区块加载 | 3 行等高骨架 |

- **账号列表** = `GET /employees?stationId={id}&status=&keyword=&pageNum=&pageSize=20` 中 `role ∈ {STAFF, STATION_ADMIN}`（员工账号；站长只是其身份之一，`ADMIN` 属系统管理员账号，不在站点账号维护范围）。
- 一次 `pageSize=100` 拉全站在内存过滤（避免为身份维度打多次分页请求）；**口径**：计数以过滤后的账号数为准，不用 `employeeCount` 冒充——`employeeCount` 含全角色。
- **筛选/搜索**：详情页顶部**一个** `van-search`（`placeholder="搜索姓名或登录账号"`）+ 一组身份 `FilterChips`（全部 / 员工 / 站长），两者**联动**同时过滤统一列表（单一搜索入口，避免"两个搜索框"）。

#### 4.4.2 站长的身份约束（已放开，须归属启用驿站）

站长只是员工账号上的一个身份，与员工共用同一套账号管理（`POST/PUT /employees`）；`role` 白名单已放开 `STATION_ADMIN`（架构 ARCH-C-1）。故：

- 身份在**新增/编辑表单内选择**（员工 / 站长），不再是独立的「站长账号」区块；
- 选「站长」时 `stationId` **必填**（本页已在驿站上下文内，自动锁定当前驿站）；该驿站须为**启用**态；
- **不得**用 `STAFF` 冒充站长（会产生"看着像站长、其实是员工"的错误数据）；

### 4.5 账号新增 / 编辑表单

| 字段 | 契约字段 | 新增 | 编辑 | 控件 / 校验 | 错误文案 |
| --- | --- | --- | --- | --- | --- |
| 姓名 | `realName` | 必填 | 必填 | 1–50 字符 | 「请输入 1–50 字真实姓名」 |
| 手机号 | `phone` | 必填 | 必填 | `^1[3-9]\d{9}$`，活跃唯一 | 「请输入正确的 11 位手机号」/「该手机号已被使用」(`2003`) |
| 登录账号 | `username` | 必填 | **不可改**（编辑态只读展示） | `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`，活跃唯一 | 「登录账号须为 4–30 位，以字母开头，仅含字母/数字/下划线」/「该账号已被使用」(`1003`) |
| 身份 | `role` | 由入口预置（员工=STAFF / 站长=STATION_ADMIN），可在表单内改 | 可改（受 `2001`/`2002`） | 单选（员工 / 站长）：站长只是员工账号上的身份，非独立账号体系 | —（选「站长」须归属启用驿站，本页已锁定当前驿站） |
| 驿站归属 | `stationId` | **锁定当前驿站**（只读展示站名，不给选择器） | 同 | — | 「该驿站已停用，无法归属员工」(`4004`) |
| 入职日期 | `entryDate` | 否 | 否 | `yyyy-MM-dd` | 「入职日期格式须为 yyyy-MM-dd」 |
| 状态 | `status` | 新增固定 1（不出现） | 可切（`PUT /employees/:id/status`） | switch | 「该驿站已停用…」（停用驿站归属拦截） |
| **初始口令** | `password` | **必填**（8–20 位，含字母与数字） | **不出现** | 密码输入（可明/密切换） | 「初始口令须为 8–20 位且同时包含字母和数字」 |
| 备注 | `remark` | 否 | 否 | ≤255 | 「备注不可超过 255 字符」 |

**口令交互口径（本规范不含任何真实口令）**

1. **新增**：管理员在表单内**设置**初始口令。字段下方常驻辅助说明（`--text-3`）：
   > 该口令仅用于账号首次登录，**首次登录后系统强制要求修改**；请通过线下安全渠道告知本人，不要写进备注或在聊天中发送。
2. **提交后**：`showSuccessToast('账号已创建，首次登录须修改口令')`。**不回显、不提供「复制口令」按钮、不写入任何日志/备注/页面**。
3. **编辑态无口令字段**：改口令走独立动作「**重置口令**」（`PUT /employees/{id}/password/reset`）。
4. **重置口令**交互：
   - 入口：账号编辑页底部 `ActionBar` 次操作「重置口令」。
   - 二次确认（`bossConfirm`，`irreversible: true`）：标题「重置登录口令」/对象「{realName}（{username}）」/影响「该账号现有登录会话将立即失效，需用新口令重新登录，且首次登录后仍强制改密」/按钮「确认重置」。
   - 确认后弹层输入新口令（`maxlength=20`，可明/密切换），字段下 `role="alert"` 校验；**不回显明文**。
   - 成功 toast「口令已重置，该账号需重新登录」。
   - 自我保护：`2001` → 「不能重置自己的口令，请走『我的 → 修改密码』」。
5. **口令强度**：8–20 位且**同时含字母与数字**（契约 `isStrongPassword`）。**不引入**复杂度评分、强度条等新增视觉（无 Token 依据）。

**编辑态不可变项与保护提示**

| 情形 | 表现 |
| --- | --- |
| 「登录账号」 | 只读文本 + `.tip`「登录账号创建后不可修改」 |
| 编辑的是**自己**（`2001`） | 「角色」「状态」「重置口令」**禁用 + 原因**「不能在此修改自己的角色/状态；如需变更请联系其他管理员」（不隐藏） |
| 目标是**最后一个管理员**（`2002`） | 停用 / 降级 → 禁用 + 原因「系统需保留至少一名管理员」 |
| 驿站已停用 | 新增账号时 `stationId` 归属校验会 `4004` → 表单级 `role="alert"`「该驿站已停用，无法归属员工」（**前置**：进入新增页时若驿站为停用，页面顶部先给 `van-notice-bar` 警告） |

---

## ⑤ 驿站助手工资单详情：去掉流转状态步骤条

端：`staff-h5`（`/staff/payroll/:id`，[payrollDetail.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/payrollDetail.vue)）。

### 5.1 页面骨架（移除步骤条后）

```
PageNav「我的工资单」
└─ .page（有操作时 .page--bar，否则 .page--loose）
   ① 金额摘要 .hero.hero--brand
        {month} 工资单            [{statusLabel} 胶囊]
        {employeeName} · {stationName|总部} · {payrollNo}
        实发大字（--fs-num-lg-staff / tabular-nums）
        实发合计（应发 {grossAmount} · 扣款 {deductionTotal}）
   ② 明细项列表（MyPayrollCard 复用，showItems=true, max-visible=5）
        逐项：{name} + {detail}（label）+ 金额（右，tabular-nums；扣项 `-` 号 + --color-danger 双通道）
        超过 5 项 → 「展开剩余 {n} 项 / 收起明细」（44px 热区）
        尾部合计行：应发 / 扣款
        审核意见 / 员工异议（有则显示）
   ③ 时间信息（可选，收为明细区尾部一行 caption）
        「生成于 {createTime} · 发布于 {publishTime} · 确认于 {confirmTime || '待确认'}」
ActionBar：[{key:'confirm',label:'确认无误'}, {key:'object',label:'提异议', type:'danger'}]（仅 status=PUBLISHED）
```

**移除项（硬约束）**：
- 「流转状态」`section-title` + `<div class="card"><PayrollStatusSteps …/></div>` **整块删除**；
- 与之配套的 `steps` computed **删除**（[payrollDetail.vue:37-57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/payrollDetail.vue#L37-L57)）；
- 「计算说明」区块（4 行 `van-cell-group` 含「计薪规则」）→ **收为 ③ 的一行 caption**（避免"步骤条走了、又堆一屏字段"的割裂）；`ruleName` 若产品确需保留，并入该行末尾「计薪规则 {ruleName}」。

**保留项（不得回归）**：金额摘要、明细展开、`ActionBar` 的确认/提异议、`9403` 阻断说明、异议弹层与校验。

### 5.2 明细项行结构与空态

| 项 | 规范 |
| --- | --- |
| 行结构 | `van-cell`：`title={name}`，`#label={detail}`（`--text-3`），`#value=金额`（`tabular-nums`，右对齐） |
| 扣项 | `-￥{金额}`（负号 + `--color-danger`，**双通道**，不只靠颜色） |
| 应发/扣款合计 | 明细尾部一行：`应发 {grossAmount}` / `扣款 {deductionTotal}`（`--text-2`，tabular-nums） |
| **空态（items 为空）** | 明细区渲染 `.tip`「暂无明细项；如对金额有疑问请提异议或联系人事」（**不整页空态**——金额摘要仍在，页面不空） |
| 明细过长 | 默认折叠 5 项 + 展开（既有）|

### 5.3 确认 / 提异议 交互与错误提示（沿用既有口径，不新增文案）

| 动作 | 交互 | 错误码 → 文案 |
| --- | --- | --- |
| 确认无误 | 二次确认：标题「确认工资单」/正文「确认 {month} 工资单（实发 {netAmount} 元）无误？确认后不可撤销，如有疑问请先提异议。」/按钮「确认无误」「再想想」 | 失败 → `showFailToast(e.message \|\| '确认失败，请稍后重试')` |
| 提异议 | 底部弹层：标题「提交异议」/说明「提交后单据会退回管理员重新核定，重新发布前你将暂时看不到该单」/`textarea` 理由必填 2–200（`maxlength=200` + word-limit）/主按钮「确认提交异议」 | 理由越界 → 行下 `role="alert"`「异议原因须为 2–200 字，会同步给管理员」<br>`9403` → **「仅已发布的工资单可提异议，请刷新后查看」**<br>`9404` → 「只能对本人的工资单提异议」 |
| 异议成功 | `showSuccessToast('已提交异议，等待管理员重新核定')` + **`router.replace('/staff/payroll')`**（回列表，避免停在不可达详情） | — |
| 详情不可达 | `9403` → `PageState empty` 文案**「工资单尚未发布或正在重新核定中，暂不可查看」** + `#empty-action`「工资单由管理员发布后才可见；重新核定期间会暂时不可查看」 | — |

### 5.4 四态

| 态 | 表现 |
| --- | --- |
| loading | `PageState loading`（≤200ms 防闪）；不渲染 `hero`（字段访问会空指针，组件已保证） |
| empty | 仅用于 `9403` / 单据不存在 → 文案见 5.3（**不是**"暂无数据"） |
| error | `PageState error` + 「重新加载」+ `role="alert"` |
| normal | 摘要 + 明细 + （`PUBLISHED` 时）操作栏 |

---

## ⑥ 驿站精灵员工页补「历史工资单」区块

端：`boss-h5`，页：`/boss/hr/:employeeId`（[hrDetail.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/hrDetail.vue)）。

### 6.1 区块位置（决策：页面最末，紧随「调薪留痕」之后）

**理由**：① 本页主任务是「调薪（写）」，「历史工资单」是查询辅助，插在写表单之前会打断动线；② 与「调薪留痕」同属"只读历史"，相邻放置形成「标准薪资历史 ↔ 实发历史」对照；③ 避免首屏同时出现两个金额列表造成误读。

> **被否方案**：放在「银行卡片」与「调薪调整」之间 —— 会把管理员每次进页都要看的调薪表单推到第二屏。

### 6.2 区块结构

```
.section-title「历史工资单」  .section-title__extra「共 {total} 张」
└─ .card
   ├─ van-list（v-model:loading / :finished / finished-text="没有更多了" @load）
   │    button.list-item 行 × N（两行，min-height ≥64px = --row-h-2）
   │      主行：{month} · {payrollNo}        [StatusTag(PAYROLL_STATUS, status)]
   │      副行（tabular-nums）：应发 {grossAmount} · 实发 {netAmount}（实发加粗 --fw-medium）
   ├─ 空态：「暂无工资单记录」+ 说明「工资单由财务端生成、管理员发布后在此可见」
   └─ 错误态：区块级 error + 「重新加载」（role=alert，≥44px；不替换整页）
```

### 6.3 列表密度 / 分页 / 四态

| 项 | 规范 |
| --- | --- |
| 密度 | **两行/条**，`min-height: var(--row-h-2)`（64px），行间距 `--sp-3`；金额用 `tabular-nums`；月份 `yyyy-MM` 不缩写 |
| 分页 | `pageSize = 10`（区块位于长页末，比列表页 20 更小以减少长页末的等待）；`van-list` 触底加载，`finished-text="没有更多了"` |
| 首屏 | 与页面主体**同批**加载（`Promise.all` 与 profile/salary 并发），避免滚动到页末再触发一次请求的"空一下" |
| loading | 区块级 2 行等高骨架（`--row-h-2` × 2） |
| empty | 见 6.2 文案（**不得**与错误态同文案） |
| error | 区块级 error + 重新加载；**不影响**上方档案与调薪表单可用 |
| normal | 行可点 → `/boss/payroll/:id`（既有详情页，不新造详情） |

**数据源**：`GET /payrolls?employeeId={employeeId}&pageNum=&pageSize=`（ADMIN；契约与 Mock 均支持 `employeeId`，见 `api.md §4.12` 列表出参 与 `packages/mock/src/routes/finance.js` 的 `payrollList`）。**不新增接口**。

**下钻口径**：点行进入 `/boss/payroll/:id`，**不新增员工维度的工资单详情页**。

---

## ⑦ 排班支持一天多个班次

端：`boss-h5`，页：`/boss/schedule`（[schedule.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/schedule.vue)）。
**边界声明**：本节点只设计**交互与呈现**；「什么算一整天」「班次是否需配对」「重叠如何判定」等**计薪/排班算法口径一律由算法工程师产出**（不在此定义）。

### 7.1 数据结构变更（前端本地草稿）

| 项 | 现状 | 本批 |
| --- | --- | --- |
| 草稿值 | `draft['{employeeId}|{date}'] = shiftId`（单值，`null`=休息） | `draft['{employeeId}|{date}'] = [shiftId, …]`（**数组**，空数组 `[]` = 休息） |
| 脏检查 | `(serverMap[key] \|\| null) !== (draft[key] \|\| null)` | 集合比较：`sortedJoin(server) !== sortedJoin(draft)`（排序后 `join(',')`，规避顺序差异产生的假脏） |
| 待保存条数 | 按格计数 | **仍按格计数**（`/schedules/batch` 的 `items` 一条对应一格；多班次写在一条 item 内，见 ⑫-4 契约扩展） |
| 提交载荷 | `{ employeeId, workDate, shiftId \| null }` | `{ employeeId, workDate, shiftIds: [] }`（**须契约扩展**，见 ⑫-4；扩展前保持单值 → 本需求不可达） |

### 7.2 呈现：一格内多班次的表达（决策：累加标签 + 二次弹层多选）

**当日排班行（逐人列表，非矩阵）**：

```
┌ {employeeName}                                        [待保存] ┐
│ [早班 08:00-12:00] [晚班 16:00-20:00]                        │   ← 累加标签，可换行
└                                                              ┘
未排班：休息（未排班）  ← --text-3
```

| 项 | 规范 |
| --- | --- |
| 单班次 | 一个班次标签：`{shiftName} {startTime}-{endTime}`，左侧 `--shift-bar-w` 色条取自 `shift.color`（沿用既有班次色条 Token） |
| 多班次 | **多个标签横向排列、超出换行**（`flex-wrap: wrap`，`gap: --sp-2`）；**不做横向滚动** |
| 行高 | 单班次 ≥48px；多班次随标签换行自然增高（不设行高上限，标签最多 **2** 个，见 7.4 上限） |
| 色条 | 每标签各带自身色条；**班次名 + 时间始终以文字呈现**，色条仅为辅助（状态不只靠颜色，SC 1.4.1） |
| 空 | 「休息（未排班）」（`--text-3`），沿用既有 |

**二次弹层（点行打开）→ 多选**：

```
sheet 标题：{employeeName} · {MM-DD 周X}
┌ 快捷动作 ────────────────────────────┐
│ [早班+晚班（全天）]      [清空（休息）] │   各 ≥44px
├──────────────────────────────────────┤
│ ☑ 早班 08:00-12:00            ●      │   ← 整行 ≥48px 可点，aria-pressed
│ ☑ 晚班 16:00-20:00            ●      │
│ ☐ 中班 10:00-14:00            ●      │
└──────────────────────────────────────┘
[ 完成 ]（≥44px，落草稿，不落库）
```

| 项 | 规范 |
| --- | --- |
| 选择模型 | **多选**：点整行切换勾选（`aria-pressed`）；勾选态用 `van-icon passed` + `--color-primary` 文字/底色（复用 `StationPicker` 的 `sheet__item[aria-pressed=true]` 既有样式口径） |
| 弹层关闭 | 「完成」按钮落草稿并关弹层；点遮罩关闭 = **放弃本次选择**（保持弹层打开前的值）——在标题下加一行 `.tip`「关闭不保存，点『完成』才应用到待保存区」 |
| 触控 | 每行 `min-height: 48px`；快捷按钮 `min-height: 44px`；「完成」≥44px |
| 无障碍 | 弹层容器 `role="group"` + `aria-label="{员工} {日期} 班次选择"`；每行 `aria-pressed`；勾选变化用 `aria-live="polite"` 播报「已选 2 个班次」 |

### 7.3 「早班+晚班=一整天」的快捷操作

**定义与依赖（关键）**：现有 `shift` 项仅有 `{ id, shiftName, startTime, endTime, color }`（见 `schedule.vue` 对 `matrix.shifts` 的使用），**没有「时段类型 / 配对关系」字段** → 纯前端**无法判定**"哪两个班次构成一整天"。

| 情形 | 交互 |
| --- | --- |
| 班次数据**提供**时段/配对信息（如 `periodType: AM/PM` 或 `pairId`） | 快捷按钮「早班+晚班（全天）」**可用**：一键把「上午段 + 下午段」两个班次置为勾选（覆盖当前勾选） |
| 班次数据**未提供**（当前现状） | 按钮 **禁用 + 原因**：按钮下方 `.tip`「需先在 PC 端维护班次时段（上午/下午）后才能一键选全天」（遵循 `§6.5` 禁用 + 原因，**不隐藏**） |

- 「清空（休息）」快捷动作**恒可用**（清空勾选 → 落草稿 `[]`）。
- **被否方案**：用「班次数 ≥2 即视为全天」或「名称含『早/晚』字符串匹配」来自动配对 —— 名称是自由文本，字符串匹配必然误判（如「早班」与「早班-临时」），会静默写错数据。**否**。
- 需算法/契约补的字段登记见 ⑫-4。

### 7.4 冲突与校验提示（呈现层；判定规则待算法确认）

| 场景 | 呈现位置 | 文案（可判定） |
| --- | --- | --- |
| 所选班次**时间重叠** | 弹层内 `role="alert"`（勾选后即时） | 「所选班次时间有重叠：{A}({a1}-{a2}) 与 {B}({b1}-{b2})，请调整」 |
| 单日**班次上限**（= **2**，主代理已裁定） | 弹层内 `role="alert"` + 阻止继续勾选 | 「单日最多选择 2 个班次」 |
| **重复**选同一班次 | —（**静默去重**，不提示） | 去重后集合 |
| 保存时班次已停用 `9106` | 既有 http 层 Toast | 「{班次名} 已停用，请重新选择」（沿用既有错误提示，不新增） |
| 切换驿站/周/批量工具时有未保存改动 | 既有 `showToast` | 「请先保存或撤销当前排班调整，再…」（沿用既有） |

**重叠判定边界（须算法工程师确认后落文案，勿自行定义）**：含首尾相接（12:00 结束 + 12:00 开始是否算重叠）、跨夜班次（`endTime < startTime`）、休息时段扣除。设计侧只保证**有结论就能显示**——接口/规则未定前，**该行提示不渲染**（宁可不说，不可说错）。

### 7.5 批量工具的多班次口径（建议，待确认）

| 工具 | 多班次下的建议口径 |
| --- | --- |
| 一键铺排 | 增加「模式」二选一：**覆盖**（默认，该格 = [所选班次]）/ **追加**（该格 = 原集合 ∪ {所选班次}，去重） |
| 复制上一周 | 按格**整体复制**班次集合（非单值） |
| 清空本周 | 该格置 `[]`（不变） |
| 预览计数 | 沿用「格子数」口径（不按班次数），并补一行「其中 {k} 格为多班次」 |

> 铺排/复制是否允许"追加多班次"属**排班规则口径**，须与算法工程师确认（⑫-4）。确认前，铺排默认**覆盖**模式上线即可（不阻塞单日多班次的核心诉求）。

### 7.6 移动端可触达性（硬约束）

- **仍不做「员工 × 7 天」矩阵**：375px 下矩阵必然触控不达标（既有裁决，见 `schedule.vue` 顶部注释）。本批保持「日期条 → 逐人列表 → 点行弹层」动线。
- 日期条 `.day-chip` 7 等分、每格 ≥44×44（现行已达标，**不改**）；周切换按钮 44×44（现行）。
- 弹层每行 ≥48px；快捷按钮 ≥44px；「完成」≥44px。
- **禁止**为"放下更多班次标签"而缩字号：标签最小 `--fs-caption`(12px)。
- 日期条与班次标签**均不得产生横向滚动**（`overflow-x: hidden` 兜底 + 换行布局）。

### 7.7 四态

| 态 | 表现 |
| --- | --- |
| loading | `PageState :rows="6"`（既有）；弹层内班次列表加载 → 3 行骨架 |
| empty | 「该驿站暂无可用班次」（既有，`shifts` 为空）；当日无可排班员工 → 「该驿站当天无可排班员工」（既有） |
| error | `PageState error` + 重试（既有）；驿站列表失败 → `StationPicker` 区块级错误 + 重试（既有） |
| normal | 行显示 0..N 个班次标签；「待保存」标记随脏检查出现 |

---

## ⑧ 审批中心（v1.1：体验优化后的最终形态）

端：`boss-h5`，路由：`/boss/approval`（`meta.roles=[ROLE.ADMIN]`，`title='审批中心'`）。

### 8.1 承载方案（决策：**分组列表（单滚动页）**，不做 Tab）

| 维度 | 分组列表（取） | 分类 Tab（否） |
| --- | --- | --- |
| 「今天要处理什么」的可发现性 | 首屏即可见 5 类的待办数（未处理一目了然） | 4 类藏在 Tab 后，需逐一点开才知道有没有 |
| 行字段差异 | 每组**各自定义行结构**（补卡/请假有日期、工资单有账期+实发） | 一套列表要按类分叉渲染（移动端列表的经典难题） |
| 复用度 | **直接复用 `TodoGroup`**（title/to/total/rows/loading/error 全对应），**零新组件** | 需 5 套列表取数/分页/筛选页面级实现 ≈ 把 5 个业务页重抄一遍 |
| 深度 | 每组只需前 3 行 + 「查看全部」，明细归各自业务页 | 每个 Tab 内要么再分页（重复实现）要么只放 3 行（Tab 就无意义） |

**结论**：`MessagePage` 的待办子视图已是「分组列表」，审批中心是**同一形态的"排除工单"视图**，做成 Tab 会引入第二套列表实现。**取分组列表**。

> 既有先例支持：`flow.vue` 用 2 个 Tab 是因为「入职 / 离职」是同类实体的两种，字段高度同构；本页 5 类是**异类聚合**，与 `MessagePage` 待办分组同构，故分组列表更贴合。

### 8.2 页面信息架构（v1.1）

```
PageNav「审批中心」（back=true）+ #right「刷新」（≥44px 命中区）
└─ .page.page--loose
   ├─ 汇总筛选条（v1.1 新增；替代 v1.0 的 van-notice-bar 纯文案）
   │    · 总数行：「共 {approvalTotal} 项待处理」；未知 → 「待处理项获取中」；节点带 aria-live="polite"
   │    · 分类 chips（单选，复用全局 .fchip）：全部 {N} · 补卡 {n} · 请假 {n} · 入离职 {n} · 工资单审核 {n} · 工资单异议 {n}
   │    · 更新时间行：「更新于 {HH:mm}」（页面本地记录本次 refresh 完成时刻，不进 store）
   ├─ van-pull-refresh 包裹分组区（下拉 → todo.refresh()）
   ├─ 分组（固定顺序 = 处理紧迫度，**不做动态排序**，见 8.9）：补卡 → 请假 → 入离职 → 工资单审核 → 工资单异议
   │    每组 = TodoGroup（title/to/total/rows/loading/error）
   │      · 组头：{组名} + 「{n} 条 · 查看全部 ›」（n 未知 → `···`）
   │      · 行 ≤3（沿用 stores/todo.js 行文案，见 8.3）
   │      · 组内错误 → 「点击重试」（组级，不影响其他组）
   │      · 空组 → **不渲染**（沿用 TodoGroup 既有口径，不显示「0 条」）
   ├─ 筛选态空（选中某类且该类 total === 0）→ 「暂无待处理的{类名}」+ 动作「查看全部待办」（回「全部」）
   ├─ 全空（5 组全空）→ PageState empty「当前没有待审批事项」+ 说明「有新申请时会出现在这里，也会推送通知」
   └─ .tip「审批中心不含工单；工单待办请见首页『工单管理』。消息页『待办』含工单，两者数字不同属正常。」
```

**取舍 A：是否引入顶部汇总区 → 引入（即上「汇总筛选条」）**
理由：① 4 类审批入口从首页移除后，审批中心成为**唯一审批入口**，进页需「一眼看清各类型分布」；② v1.0 仅一行 `共 N 项` 文案，回答不了「哪类多、先处理哪类」；③ **汇总与筛选合并为同一组件**，其分类计数同时承担「筛选入口 + 数值概览」双重职责，不产生「只读装饰数字」；④ 总数仍是页内唯一总数（**替代** v1.0 的 notice-bar 文案，**不是**第二个徽标，沿用 8.5「不明文重复总数」）。

**取舍 B：分组顺序（固定业务优先级 vs 按数量动态排序）→ 固定业务优先级，不做动态排序**
理由：① 动态排序使分组位置随数据跳动，每次进入都要重新定位；② 与消息页待办组顺序（同一 store）保持一致心智；③ 「按数量排序」属算法口径（路由 R03 / R10），不由设计拍板；④ 数量信息已由分类 chips 与组头 count 表达，无需靠位置承载。

### 8.3 分组字段（行结构）与跳转目标（v1.1）

**统一口径**：行文案仍由 [stores/todo.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/stores/todo.js) 的 `LOADERS` 单点产出，**消息页与审批中心共享同一份**，不新增第二套（沿用 8.6）。v1.1 对行**字段**做增强，凡标注「出参已含」的字段仅需在 LOADER 行映射里多取用，**不改契约**。

| 分组 | key | 行主文案 | 行元信息 | 次要行 `note`（可选） | 标签 | 移动端优先展示（1~2） | 字段可用性 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 补卡 | `makeups` | `{employeeName} {workDate}` | `{periodName} · {checkType}`（日期 + 时段） | `{reason}`（事由，1 行省略） | — | **姓名、补卡日期** | `reason/periodName/checkType/workDate` **出参已含**（`getMakeupList`）；LOADER 补取 `reason` |
| 请假 | `leaves` | `{employeeName} {假别}` | `{startDate} ~ {endDate} · {countedDays} 天`（起止 + 天数 + 类型） | — | — | **姓名、起止** | 出参已含，**无需改** |
| 入离职 | `flows` | `{employeeName} 入职/离职` | `{stationName\|总部} · {currentStepName\|待办理}` | — | 「注册」微胶囊（见 8.4；来源字段已具备，字段缺失时不渲染为兜底） | **姓名、类型** | **来源字段已具备**（⑧.4）；其余出参已含 |
| 工资单审核 | `payrolls` | `{month} {stationName} 工资单` | `{employeeName} · 实发 {moneyText(netAmount)}` | — | `StatusTag(PAYROLL_STATUS, status)` | **员工、账期** | `netAmount` **出参已含**（`toPayrollVO` 展开 payroll），LOADER 补取；**「人数」无此字段**（单据按员工一行，见 ⑫-21） |
| 工资单异议 | `payrollObjections` | `{month} {employeeName} 工资单` | `{objectionReason \|\| '员工提出异议，待重新核定'}`（事由） | — | `StatusTag(PAYROLL_STATUS, 'OBJECTED')` **（全页唯一 solid 实心标签，见 8.6）** | **员工、账期** | 出参已含，**无需改** |

**行字段缺口登记**：`makeups.reason`、`payrolls.netAmount` 属「**出参已含、LOADER 未取**」→ 前端改动（⑬-冲突表 C1）；`payrolls.人数` 属「**出参无此字段**」→ 不造假、按降级处理（⑫-21）；`flows.source` **已具备**（⑧.4，2026-09-27 已实现并渲染）。

**跳转目标（`to`）——已实现（核实 2026-09-27）**：各组应落**各自业务页的「该组状态」筛选视图**（补卡 `PENDING` / 请假 `PENDING_BOSS` / 入离职 `IN_PROGRESS` / 工资单审核 `PENDING_APPROVAL` / 异议 `OBJECTED`）。`toTarget` 已按 `config.to` **附加字符串枚举 `status` 的 query**（[stores/todo.js:173-176](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/stores/todo.js#L173-L176)），异议组正确落 `?status=OBJECTED` 并能看到异议单——缺口 ⑫-6 **已闭环**。

### 8.4 「员工注册」的标识方式（现无独立组）

| 项 | 规范 |
| --- | --- |
| 归类 | 员工注册**不独立成组**，归入「入离职」分组（注册是入职流程的来源之一，落 `employee_registration` + 入职流程） |
| 标识 | 该行主文案右侧加一个 **micro 胶囊「注册」**：尺寸复用既有 `--tag-h` / `--tag-pad-x` / `--fs-micro` / `--r-full`；配色 = 文字 `--color-info-text`（#4b5563）/ 浅底 `--color-info-surface`（#edf0f4），**实算对比度 6.61:1 ✓**（见 ⑬.4 对比度补算）。与「入职」文案并列；**不得**只靠颜色区分（有文字「注册」即文字通道）。**实现取舍**：`MiniChip` 的 `tone` 走 `--state-${tone}-*`，而六态族**只有 success/warning/danger/primary/neutral，无 `--state-info-*`**，故本胶囊**自绘**（`approval.vue` 页内 scoped 类），尺寸 Token 与 `MiniChip` 对齐；**不改 `MiniChip` 组件**（避免动共享组件与引入新语义族）。 |
| 数据来源 | 入离职**列表**出参提供注册来源字段 **`source`**（取值 `ADMIN`=后台建档 / `SELF_REGISTER`=员工自助注册）。**已实现**（2026-09-27 核实：`HrFlowVO.source` 直出，列表与详情同源） |
| 降级（字段缺失时，兜底） | **不显示「注册」胶囊**（不臆造来源）。注：字段已具备且前端已渲染（`source === 'SELF_REGISTER'`），此降级仅作旧数据/异常兜底 |

### 8.5 计数角标（复用 `todo.counts` 口径）

| 位置 | 规则 |
| --- | --- |
| 首页宫格「审批中心」 | `approvalTotal = makeups+leaves+flows+payrolls+payrollObjections`；任一为 `null` → 整值 `null`（不渲染）；见 ②.3 |
| 审批中心·汇总条总数行 | 条数说明「共 {approvalTotal} 项待处理」；`approvalTotal` 未知 → 显示「待处理项获取中」而不是 `0`；节点 `aria-live="polite"` |
| 审批中心·汇总条分类 chips | 每类计数 = 该组自身 `total`（`todo.groups` 同源）；未知 → 显示 `—`（**不用 0 冒充**）；chip 的 `aria-label` 带计数（如「补卡，3 条待处理」） |
| 审批中心·更新时间行 | 「更新于 {HH:mm}」= 页面本地记录的本次 `todo.refresh()` 完成时刻（页面 `ref`，**不进 store**）；首帧显示「更新中…」 |
| 组头 | 各组自身 `total`（未知 → `···`，`TodoGroup` 既有） |
| **不明文重复显示** | 汇总条总数**替代** v1.0 的 notice-bar 文案，仍是页内**唯一**总数；不再额外渲染第二个"总数徽标"（避免与宫格、消息 Tab 三处口径并列造成困扰） |

### 8.6 与既有 `MessagePage`（待办组）的关系（避免第二套口径）

| 约束 | 落地 |
| --- | --- |
| **单一数据源** | 审批中心**只消费 `useTodoStore`**（`groups` / `counts` / `loading` / `known` / `refresh`），**不新增接口调用**、**不新增 `constants/todoGroups.js` 分组**、**不新增 `LOADERS`** |
| **差异只在"视图过滤 + 展示增强"** | 审批中心 = 该快照**按 key 白名单**筛出的子集（`[makeups, leaves, flows, payrolls, payrollObjections]`，**排除 `orders`**）；消息页待办子视图 = 全量 5~6 组（含 `orders`）。**v1.1 起审批中心另加展示增强**（汇总筛选条 / 下拉刷新 / `note` 行），但**取数、分组、行文案、计数口径全部同源**，不构成第二套 |
| **刷新时机** | 进入页面即 `todo.refresh()`（`onMounted`）；页头 `PageNav #right`「刷新」按钮（≥44px 命中区，文案「刷新」）。**v1.1 新增下拉刷新**（`van-pull-refresh` → `todo.refresh()`，与右上刷新**同源同效**）。**不做**定时轮询；**不做**分页 / 加载更多（各组仅前 3 行，明细归业务页） |
| **禁止** | 在审批中心内自成一套分页/筛选后再"组装"总数（会产生「审批中心说 3、消息页说 5」的漂移） |
| **复用组件** | `TodoGroup`（title/to/total/rows/loading/error + `retry`；**v1.1 行模型加可选 `note`**，见 8.3）、`PageState`（整页四态）、全局 `.fchip`（汇总条分类 chips）、`van-pull-refresh`；「注册」胶囊自绘（见 8.4）。汇总条在 `approval.vue` **页内实现**，**不新增具名组件** |

**页内 role 白名单实现口径**：`configs` 已按 `canAccess(item.roles, auth.user)` 过滤，本页只需在渲染前按 key 白名单再筛一次（**不复制权限逻辑**）。

### 8.7 四态与逐类降级

| 层 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 汇总条（v1.1） | 总数「…」+ 分类 chips 计数 `—` + 「更新中…」；单行等高骨架 | — | 单类失败 → 该类 chip 计数 `—`（其余照常）；全失败 → 总数「待处理项获取中」 | 总数 + 分类计数 + 「更新于 HH:mm」 |
| 整页 | 5 组骨架（每组 3 行等高） | 5 组全空 → `PageState empty`（见 8.2） | 5 组**全部**失败 → `PageState error`「待办加载失败」+ 「重新加载」`role=alert` | 分组渲染 |
| 筛选态（v1.1，选中某类） | 同整页骨架 | 该类 `total === 0` → 「暂无待处理的{类名}」+ 动作「查看全部待办」（回「全部」） | 该类失败 → 沿用组内 `role=alert` + 「点击重试」 | 仅渲染该类分组 |
| 单组 | `TodoGroup loading`（3 行骨架） | **空组不渲染**（既有口径） | 组内 `role=alert` 原因 + 「点击重试」（重试 = 重新 `todo.refresh()`，成功即恢复） | 组头 + ≤3 行 |
| 部分失败 | — | — | **逐类降级**：一组失败只影响该组，其余组照常展示（`todo.refresh` 已逐组 `try/catch`） | 失败组头显示 `···` 条数 |

**「整页错误」的触发条件（可判定）**：`todo.known === false`（`groups.some(total !== null)` 为 false）且 `groups.length > 0` → 整页 error；若 `groups.length === 0`（未登录 / token 缺失）→ 不渲染错误态，沿用既有「未登录不取数」行为。

### 8.8 错误文案（可判定）

| 触发 | 文案 |
| --- | --- |
| 整页加载失败 | 「待办加载失败」+ 「请检查网络后重试，若持续失败请联系管理员」+ 「重新加载」 |
| 组内加载失败 | `e.message \|\| '加载失败'`（store 既有）+ 「点击重试」 |
| 全空 | 「当前没有待审批事项」+ 「有新申请时会出现在这里，也会推送通知」 |
| 角标未知 | 组头 `···`；页头「待处理项获取中」 |

### 8.9 交互与效率（v1.1，逐条可判定）

| # | 项 | 规范 |
| --- | --- | --- |
| E1 | **筛选** | 汇总条分类 chips **单选**（全部 / 补卡 / 请假 / 入离职 / 工资单审核 / 工资单异议）；选中即**本地过滤** `visibleGroups`（**不新增请求**）。选中态复用全局 `.fchip--active`（文字 `--color-primary` / 底 `--color-primary-surface` / 边 `--color-primary-icon`）；`role="group"` + `aria-label="按审批类型筛选"`，各 chip `aria-pressed` |
| E2 | **排序** | **不提供排序控件**（见 8.2 取舍 B）；分组顺序固定为业务紧迫度 |
| E3 | **下拉刷新** | `van-pull-refresh` 包裹分组区，触发 `todo.refresh()`（与右上刷新**同源同效**）；刷新期间分组区**不闪骨架**（`pageLoading = loading && !groups.length`，快照在则维持渲染） |
| E4 | **加载更多** | **不做**（无分页语义，每组仅前 3 行）。列表底部 `.tip`：「每组仅显示前 3 条，查看全部进对应业务页」 |
| E5 | **快捷入口** | 「待我处理」计数即**分类 chips**：点某类 chip → 直达该分类视图（等价于「计数直达对应分类」）；点「全部」还原。**不**为每类单独做行内动作 |
| E6 | **命中区** | 页头「刷新」≥44px（现状 `nav-action` 已满足）；下拉手势区为整个分组区 |

### 8.10 行内直达详情 与 一键审批红线（v1.1）

| 项 | 规范 | 理由 |
| --- | --- | --- |
| **整行点击（保持）** | 组头与明细行均为 `router-link`，跳**该组业务页的该状态筛选视图**（`TodoGroup` 既有行为），**不是**单条详情 | 审批中心是「快照分发台」，明细与动作归业务页；避免为 5 类各做一套详情路由 |
| **卡片内直达单条详情** | **一期不做**（登记为 ⑬ 遗留项）。若要做须逐类接入详情路由，而补卡 / 请假仅「列表 + 弹层」、无独立详情页 → 一致性差、成本高 | 见 ⑬ 遗留项 |
| **一键通过 / 驳回** | **硬红线：禁止**。审批的「通过 / 驳回」**必须进详情**后完成（补卡 / 请假 = 业务页行内弹层；入离职 / 工资单 = 详情页）；快照行内**只展示、不落动作** | 防误批：审批为**有连带影响**动作（如补卡通过即补录打卡记录），快照场景不足以承载确认信息与审批意见填写 |
| **可判定验收** | 审批中心页内**不存在**任何「通过 / 驳回 / 同意」按钮或开关；可点元素仅「导航 / 刷新 / 重试」 | 静态审查可查：页面模板无审批动作绑定 |

### 8.11 返回后的列表状态与滚动位置（v1.1）

| 项 | 规范 | 依据 / 缺口 |
| --- | --- | --- |
| **列表数据状态** | **天然保持**：`todo` store 为应用级（Pinia），从业务页返回时快照仍在；且刷新时 `pageLoading` 为 false（有快照不闪骨架），内容与顺序**不跳变** | [approval.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/approval.vue) 既有 `pageLoading` 判定 |
| **筛选状态** | 选中 chip 值写入 `sessionStorage`（键如 `boss.approval.filter`），返回时作 `activeType` 初值 → **筛选不丢** | 需页面级实现（`sessionStorage` 与 `route.query` 二选一，取舍见 ⑬） |
| **滚动位置** | **一期不做恢复**。框架现状：`scrollBehavior: () => ({ top: 0 })`（[router/index.js:267](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L267-L267)）**固定归顶**，且移动端**未启用 keep-alive**（[NoticeList.vue:142](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/NoticeList.vue#L142-L142) 注释已记载） | **超范围**：恢复滚动需改路由 `scrollBehavior`（影响全端）或给本页加 keep-alive（需布局改造）→ **登记回报主智能体**（⑬ 遗留项） |
| **不做的理由** | 审批中心为单滚动页，返回归顶后仍可凭「固定分组顺序 + 筛选 chips」快速回到目标分类；成本收益比不支持下探路由改造 | — |

### 8.12 可访问性（v1.1 增补，并入 ⑩）

| 项 | 规范 |
| --- | --- |
| 触控目标 | 汇总条 chips ≥44px（全局 `.fchip` 已 `min-height: 44px`）；分组行 ≥48px（`TodoGroup__link` 既有）；页头刷新 44×44（既有） |
| 对比度 | 汇总条与 chips 全部使用既有色对，**实算均 ≥4.5:1**（推导见 ⑬.4）：总数文字 `--color-primary`/`--surface-card` 6.16:1；chip 选中 `--color-primary`/`--color-primary-surface` 5.52:1；chip 未选 `--text-2`/`--surface-card` 7.56:1；计数未知态 `—` 用 `--text-3`/`--surface-card` 4.83:1 |
| `aria-live` | **仅总数节点** `aria-live="polite"`：刷新后计数变化被播报（只挂一处，避免 chips 计数同时播报造成噪声） |
| 读屏命名 | 分类 chip `aria-label` 带计数（「补卡，3 条待处理」）；「查看全部」沿用 `TodoGroup` 既有 `moreLabel`（带分组名与条数） |
| 键盘 | chips 为原生 `button`（`Tab` 可达、`Space`/`Enter` 触发、`aria-pressed` 报选中态）；行内为 `router-link`（可聚焦） |
| 在途态 | 刷新中页头刷新按钮 `aria-busy="true"`（可选）；下拉刷新由 `van-pull-refresh` 承载 |
| 状态非颜色单通道 | 异议单 `OBJECTED` 为 solid 实心标签且**带文字**（「已提异议」）；「注册」胶囊带文字 |
| 空/错态区分 | 空态与错误态文案不同（8.7 / 8.8）；错误态必给重试 |

---

## ⑨ 状态 — 动作 — 页面 对照表（新增页面维度）

> 口径：状态取各实体**既有字典**（`STATION_STATUS` / `EMPLOYEE_STATUS`（页内常量）/ `PAYROLL_STATUS` / `FLOW_STATUS`），不新增状态机；动作以服务端为准，前端不维护第二份。

### 9.1 财务管理（员工工资设置）

| 状态（算薪配置） | 页面 | 可见动作 | 点击 → 接口 | 成功后 UI |
| --- | --- | --- | --- | --- |
| 已配置 · 启用 | 列表卡 / 编辑页 | 进入编辑；改字段；保存 | `PUT /payroll-settings/{id}` | toast「算薪设置已保存」→ 就地刷新 + 刷新变更历史 |
| 已配置 · 未启用 | 同上 | 同上（启用开关 0→1 先二次确认） | 同上 | 开关落草稿 → 保存后「已启用」 |
| **尚未配置**（`9406`） | 编辑页 | 直接保存（以默认值创建） | `PUT /payroll-settings/{id}` | toast 成功 → 变「已配置」 |
| 校验失败（`9407`/`9408`） | 编辑页 | — | — | 字段下 `role=alert`；滚动聚焦 |

### 9.2 驿站管理

| 状态 | 页面 | 可见动作 | 点击 → 接口 | 成功后 UI |
| --- | --- | --- | --- | --- |
| 启用 | 列表行 / 详情 | 编辑 / 停用 / 删除（仅 `employeeCount=0`）/ 维护账号 | `PUT /stations/{id}` · `/status` · `DELETE` · `/employees` | 就地更新；停用/删除走 `bossConfirm` |
| 停用 | 列表行 / 详情 | 编辑 / 启用 / 删除 | `PUT /stations/{id}/status` | 启用后新账号可归属该站 |
| 有员工 | 详情 | 删除**禁用** + 原因 | — | — |
| 驿站不存在 | 详情 | — | — | 404 → 页面级 error |
| 账号 · 在职(1) | 账号行 | 编辑 / 停用 / 重置口令 | `PUT /employees/{id}` · `/status` · `/password/reset` | 停用/重置后该账号强制下线 |
| 账号 · 已停用(0) | 账号行 | 编辑 / 启用 | 同上 | 启用后可登录 |
| 账号 · 是自己 | 账号行 | 角色/状态/重置口令 **禁用** + 原因 | — | — |
| 账号 · 最后管理员 | 账号行 | 停用/降级 **禁用** + 原因 | — | — |
| 新增账号（角色受限） | 站长账号区块 | 「新增站长账号」**禁用** + 原因 | — | 见 ⑫-2 |

### 9.3 驿站助手工资单详情

| 状态 | 端 | 可见内容 | 可见动作 | 点击 → 接口 |
| --- | --- | --- | --- | --- |
| `PUBLISHED` | staff-h5 | 摘要 + 明细 + 时间行 | 确认无误 / 提异议 | `confirmPayroll` / `objectPayroll` |
| `CONFIRMED` | staff-h5 | 摘要 + 明细 | 无（note「本单已确认」） | — |
| `PAID` | staff-h5 | 摘要 + 明细 + 归档只读 | 无（note「已发放并归档」） | — |
| 内部态 / `9403` | staff-h5 | `PageState empty`（文案见 5.3） | 无 | — |

### 9.4 员工页（历史工资单）

| 状态 | 可见内容 | 可见动作 |
| --- | --- | --- |
| 有记录 | 行（月份/单号/应发/实发/状态） | 点行 → `/boss/payroll/:id` |
| 无记录 | 空态文案 | 无 |
| 区块失败 | 区块错误 + 重试 | 重新加载 |
| 全状态（`DRAFT`..`PAID`） | 均可见（ADMIN 全域视角） | 均可下钻（详情页按状态给动作） |

### 9.5 排班（多班次）

| 状态（某员工某日） | 呈现 | 可见动作 |
| --- | --- | --- |
| 0 班次 | 「休息（未排班）」 | 点行 → 弹层多选 |
| 1 班次 | 1 个班次标签 | 点行 → 弹层多选 / 清空 |
| 2 班次（上限） | 多个标签（换行） | 同上 |
| 超上限（>2） | 弹层内阻止 + `role=alert` | 取消勾选 |
| 时间重叠 | 弹层内 `role=alert` | 调整选择 |
| 有未保存改动 | 行标「待保存」+ ActionBar「保存排班」可用 | 保存 / 撤销（切站/切周/批量工具被阻止） |

### 9.6 审批中心

| 状态 | 呈现 | 可见动作 |
| --- | --- | --- |
| 组有数据 | 组头（`{n} 条 · 查看全部`）+ ≤3 行 | 点组头/行 → 业务页该状态筛选视图（**非单条详情、非一键审批**，见 8.10） |
| 组为空 | **不渲染该组** | — |
| 筛选态·该分类为空（v1.1） | 「暂无待处理的{类名}」+ 动作「查看全部待办」 | 回「全部」 |
| 组失败 | 组内错误 + 点击重试 | 重试 |
| 5 组全空 | 整页空态 | 无 |
| 5 组全失败 | 整页错误 + 重新加载 | 重试 |
| 汇总条·刷新中（v1.1） | 总数「…」+ 计数 `—` + 「更新中…」 | 下拉 / 点右上刷新 |
| 注册来源（字段可用时） | 入离职行 + 「注册」胶囊 | 同组跳转 |
| 审批落动作 | **审批中心内不可见任何通过 / 驳回动作**（红线） | 进业务页详情后再操作 |

---

## ⑩ 响应式与可访问性（移动端为主）

### 10.1 断点与容器（沿用，不新增档位）

| 视口 | 行为 | 约束 |
| --- | --- | --- |
| 320 | 单列；宫格 4 列每格 ≈74×88 | ≥44×44；新页面**禁止**固定 `min-width > 320` 的控件 |
| 360 / 375（基准）/ 390 / 414 / 430 | 单列 | 宫格 10 项 → 4 列 3 行；标签换行不产生横向滚动 |
| 480 | `#app` 触达 `max-width` 上限 | 不再增宽 |
| 640（横屏） | `max-width: 640px`，**仍单列** | 不重排；不得出现横向滚动条 |

- 内容列 `--content-max = min(480px, 视口宽)`（横屏 `min(640px, …)`）；固定栏（NavBar / Tabbar / ActionBar）同步限宽（沿用 `mobile.scss`）。
- 底部安全区三选一（不混用）：Tab 页 `--page-pad-bottom-tab`；带固定栏 `--page-pad-bottom`；普通详情 `.page--loose`。**禁止**页面内直接写 `env(safe-area-inset-*)`。
- 表单页（新增/编辑驿站、账号）用 `.page--bar`（底部 `ActionBar`）+ `--page-pad-bottom`，末字段不被固定栏遮挡。

### 10.2 触控目标 ≥44×44（逐项清单）

| 元素 | 尺寸 |
| --- | --- |
| 宫格单元 | 375px 下每格 ≥74×88（4 列；10 项分 3 行） |
| `fchip` 筛选、`van-search` | `min-height: 44px`（`--van-search-input-height: 44px`） |
| 列表行（驿站/账号/工资单/排班/审批组） | ≥48px（`list-item` / `TodoGroup__link`）；多班次行随标签增高 |
| 弹层行（`StationPicker` / 班次多选 / 口令弹层） | ≥48px |
| `ActionBar` 按钮 | `min-height: 44px`（`--actionbar-h: 56px`） |
| `PageNav` 返回 / `#right` | 44×44（组件内已保证） |
| 行内次要动作（重置口令、展开明细、查看全部、点击重试） | ≥44px（含负边距回填） |
| 班次色条 `4px` 宽 | 仅装饰（`--shift-bar-w`），**不承载点击**；点击目标为整行 |
| **v1.2** 班次列表行（`/boss/shifts`） | ≥76px（`--row-h-3`：名称+状态 / 时间·休息 / 行内动作三行） |
| **v1.2** 班次行内动作（停用·启用 / 删除） | ≥44×44（含负边距回填，按钮不挤行文字） |
| **v1.2** 班次配色 chip（3 个受控配色） | ≥44px（复用全局 `.chip`）；色块 10×10 仅装饰 |
| **v1.2** 时间选择 `van-cell` / `van-time-picker` 确认·取消 | ≥48px / ≥44px（Vant 默认 ≥44） |

### 10.3 对比度 ≥4.5:1（引用既有实算 + 本批新增组合）

| 用途 | 前景 / 背景 | 对比度 | 判定 | 来源 |
| --- | --- | --- | --- | --- |
| 正文/标题 | `--text-1` / `--surface-card` | 14.66 | ✓ | 附录 A（本项目既有实测口径） |
| 字段标签/副行 | `--text-2` / `#fff` | 7.56 | ✓ | 同 |
| 辅助说明 `.tip` | `--text-3` / `#fff` | 4.83 | ✓ | 同 |
| 主色按钮/链接 | `--color-primary` / `#fff` | 6.16 | ✓ | 同 |
| 错误文案 | `--color-danger` / `#fff` | 5.57 | ✓ | 同 |
| 「尚未配置」警示文字 | `--color-danger` / `--surface-card` | 5.57 | ✓ | 同上（同色对） |
| 状态胶囊（`StatusTag`）| 字典 type 族 + `--text-*`（soft/outline/solid） | 见 `demo-boss-ui-spec 附录 A` | ✓ | 既有 |
| 「注册」胶囊 | `--color-info-text` / `--color-info-surface` | ≥4.5（既有 info 浅底族） | ✓（落地后实测） | 同族既有 |
| 停用驿站胶囊 | `info` + `outline` → `--text-3` / `--surface-card` + `--state-outline-border` | 4.83（文字） | ✓ | 同 |
| 禁用按钮文字 | `--text-disabled` | 1.525 | **豁免**（WCAG 1.4.3 对失效控件） | `demo-boss-ui-spec §3.1` |
| **v1.2** 配色 chip · 选中 | `--color-primary`(#0958d9) / `--color-primary-surface`(#e8f4ff) | 5.52 | ✓（按 ⑬.4 同法推导，落地后实测） | 同法 |
| **v1.2** 配色 chip · 未选 | `--text-2`(#4b5563) / `--surface-card`(#ffffff) | 7.56 | ✓ | 同法 |
| **v1.2** 班次色块（`--c-blue-700` / `--c-orange-500` / `--c-neutral-800`） | **仅装饰**，必配文字标签，**不单独承载语义** | — | ✓（SC 1.4.1） | — |

**硬规则（沿用）**：① 浅底块内文字**一律 `--text-2`**，禁用 `--text-3`（对 `--surface-subtle` 实算 4.23:1）；② 500 档 `--color-primary-icon` **不承载文字**（仅图标/线/描边/焦点环）；③ **状态不只靠颜色**：班次色条、状态胶囊、金额正负、注册标识**均须带文字**。

**本批零新增色值结论**：⑥ 项需求所需表达（状态、警示、选中、禁用）**全部可由既有语义色族 + 既有 variant（soft/outline/solid）表达**，`StatusTag` 的 `TYPE_FALLBACK` 与 `DICT_COLORS` 已覆盖所用字典（含 `OBJECTED` solid 白字 5.022:1）。**未新增任何颜色字面量或 Token**。若实现中发现缺口，须先在本规范登记「理由 + 替代方案」并回评审，**不得在前端就地造色值**。

### 10.4 无障碍与反馈

| 场景 | 要求 |
| --- | --- |
| 区块错误 | `role="alert"`（`PageState` / `TodoGroup` / 区块级错误块） |
| 表单校验失败 | 字段下 `role="alert"`；提交失败时把焦点移向首个错误字段 |
| 在途控件 | `aria-busy="true"`（`ActionBar` 按钮 / `TodoGroup` 骨架 / 弹层列表） |
| 动态计数 | 班次多选「已选 N 个班次」、铺排预览 → `aria-live="polite"` |
| 图标语义 | 承载语义的图标须 ≥3:1 或配文字（`--color-warning-icon` 白底 1.900:1，**禁止单独承载语义**） |
| 键盘 | 所有可点元素可 `Tab` 聚焦，`Enter`/`Space` 触发；弹层 `Esc` 关闭（Vant 默认） |
| 长文本 | 列表标题单行省略（`min-width:0` + ellipsis）；宫格项名允许换行 `word-break: break-all` |
| 超长数字 | 一律 `.tabular-nums`；≥1 万走 `numberText`（万）；金额走 `moneyText` |
| 动效 | ≤200–300ms；`prefers-reduced-motion` 降级由 Token 层全局承接 |
| 空/错态区分 | 空态与错误态**文案必须不同**；错误态必须给下一步与重试 |
| **v1.2** 配色 chip 选中态 | `aria-pressed`（或 `role=radio` + `aria-checked`）**与视觉双表达**，**不只靠颜色**；色块 `aria-hidden`，名称由文字标签承载 |
| **v1.2** 时间选择 | `van-cell` 带 `aria-label`（如「开始时间，当前 08:00」）；`van-time-picker` 确认 / 取消 ≥44px |
| **v1.2** 班次保存中 / 失败 | `ActionBar` `loading` + `submitting` 防连点；保存失败文案**就地** `role="alert"`（`ActionBar.note`），**不清空表单** |

---

## ⑪ 交付给前端工程师的落地清单

> 目标工程：`hrm-dev/hrm-clients/apps/boss-h5/`，除 ⑤ 外；⑤ 在 `apps/staff-h5/`。**本文不改代码**，下列为前端实现范围。

### 11.1 首页宫格与「我的」

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `src/constants/quickEntries.js` | **v1.1**：`BOSS_QUICK_ENTRIES` **删去 4 项**（`makeups` 补卡审批 / `payrolls` 工资单审核 / `flows` 入离职审批 / `leaves` 请假审批），保留 **6 项**（`approvals` 前移至第 1，`orders/attendance/alerts/finance/stations` 相对顺序不变，见 ②.2）；v1.0 已加入的 `approvals`/`finance`/`stations` 三项**保留**；**本次不新增 icon** |
| 2 | `src/modules/boss/views/home.vue` | **v1.1**：`quickData` **删去 4 个 key**（`makeups`/`payrolls`/`flows`/`leaves`）；`approvals = todo.approvalTotal`、`stations = team?.stationTotal ?? null`、`attendance/alerts/orders` 保留；`hint` 文案改 **「待办与概览在前」**（②.2 第 ⑤） |
| 3 | `src/stores/todo.js` | 新增派生（**纯前端，不改契约**）：`approvalKeys = ['makeups','leaves','flows','payrolls','payrollObjections']`；`approvalTotal = 全部命中键非 null ? 求和 : null`；`approvalKnown = 命中键全部非 null`。**不得**把工单 `orders` 计入 |
| 4 | `src/components/MeSection.vue` | 删除「算薪日设置」`van-cell`；「站点管理」label → 「驿站管理」（`to` 不变）；「自动算薪运行」保留 |
| 5 | `src/components/HomeQuickGrid.vue` | **不改**（仅注释中「8 项上限」表述可回填；组件对条目数无硬编码） |
| 6 | 文档回填（主智能体执行） | `demo-mobile-nav-redesign.md` B1 的「4×2=8 项上限」需追加「管理端扩至 4×3=10 项，v1.1 收敛为 6 项」的修订注记 |

> **v1.1 说明**：4 项删除后其**业务页 / 路由保留不动**（它们是审批中心组头 / 行的跳转目标，②.1）；`HomeQuickGrid` **不改**（对项数无硬编码）。

### 11.2 财务管理 / 员工工资设置

| # | 文件 | 改动 |
| --- | --- | --- |
| 7 | `src/modules/boss/views/payrollSettings.vue` | `PageNav title` → 「财务管理」；新增「自动算薪运行」「相关设置」两个 `section-title` + `van-cell` 入口行；「员工工资设置」区块标题与 extra；**列表行结构、筛选、PageState、错误分支不动** |
| 8 | `src/modules/boss/views/payrollSettingEdit.vue` | `PageNav :title` → `notConfigured ? '配置员工工资设置' : '员工工资设置'`（仅此一处文案） |
| 9 | `src/router/index.js` | `/boss/payroll-settings` 记录：`meta.title` → 「财务管理」，新增 `alias: '/boss/finance'`；`/boss/payroll-settings/:stationId` 的 `meta.title` → 「员工工资设置」 |
| 10 | 复用 | `PageState` / `PageNav` / `fchip`（页内 scoped 或抽具名样式，见 ⑫-15）；**不新增组件** |

### 11.3 驿站管理

| # | 文件 | 改动 |
| --- | --- | --- |
| 11 | `src/modules/boss/views/station.vue` | **重构为驿站管理列表页**：`PageNav`/搜索/fchip/计数行/列表行（四行结构）/`ActionBar[{create}]`；原「选站 + 名册」逻辑移除（迁入 #13） |
| 12 | `src/modules/boss/views/stationDetail.vue`（新增） | 详情页：hero + 基本信息 + **统一账号列表**（含站长身份，行内 `MiniChip` 展示身份）+ `ActionBar`；顶部同一搜索框 + 身份 `FilterChips`（全部 / 员工 / 站长）联动过滤；**不拆「员工账号 / 站长账号」两个区块**（用户口径：站长是员工账号的身份） |
| 13 | `src/modules/boss/views/stationForm.vue`（新增） | 新增/编辑驿站共用（按 `route.params.id` 判新增/编辑）；字段与校验见 4.3 |
| 14 | `src/modules/boss/views/accountForm.vue`（新增） | 新增/编辑账号共用；**身份**由 `?role=` 预置（员工=STAFF / 站长=STATION_ADMIN）；新增含初始口令字段，编辑态无口令字段 + 「重置口令」动作 |
| 15 | `src/api/org.js` | 新增封装：`createStation` `POST /stations`、`updateStation` `PUT /stations/:id`、`updateStationStatus` `PUT /stations/:id/status`、`deleteStation` `DELETE /stations/:id`、`createEmployee` `POST /employees`、`updateEmployee` `PUT /employees/:id`、`updateEmployeeStatus` `PUT /employees/:id/status`、`resetEmployeePassword` `PUT /employees/:id/password/reset`（均 `{ silent: true }` 以支持就地错误展示） |
| 16 | `src/router/index.js` | 新增 `/boss/station/create`、`/boss/station/:id`、`/boss/station/:id/edit`、`/boss/station/:id/account/create`、`/boss/station/:id/account/:employeeId`（**静态路径先于 `:id`**）；均 `meta.roles=[ROLE.ADMIN]` |
| 17 | 复用 | `PageNav` / `PageState` / `StationPicker`（仅列表页若需"跳到某站"时用，详情页内不用）/ `StatusTag` / `FilterChips`（身份筛选）/ `MiniChip`（身份胶囊）/ `van-cell` / `van-field` / `van-search` / `van-list` / `ActionBar` / `bossConfirm`（二次确认）/ `EMPLOYEE_STATUS`（**由 `station.vue` 页内常量上提到 `constants/`**，因新增账号列表与账号表单两个消费点，满足"三次抽取"前置） |

### 11.4 驿站助手工资单详情

| # | 文件 | 改动 |
| --- | --- | --- |
| 18 | `apps/staff-h5/src/views/staff/payrollDetail.vue` | 删除「流转状态」区块与 `PayrollStatusSteps` 引入、删除 `steps` computed；「计算说明」4 行并入明细区尾部一行 caption；其余（摘要/明细/`ActionBar`/异议弹层/`9403` 分支）**不动** |
| 19 | 死代码登记 | `apps/staff-h5/src/components/PayrollStatusSteps.vue` 移除引用后可能无消费者 → **本批不删**，登记（⑫-12）；若确认无引用，由主智能体决定删除 |

### 11.5 员工页历史工资单

| # | 文件 | 改动 |
| --- | --- | --- |
| 20 | `src/modules/boss/views/hrDetail.vue` | 在「调薪留痕」区块之后新增「历史工资单」区块：`van-list`（`pageSize=10`）+ 行（两行）+ 区块级四态；与 `load()` 的 `Promise.all` 同批取 `GET /payrolls?employeeId=` |
| 21 | `src/api/finance.js` | `getPayrolls` 已支持任意 query 透传 → **无需改签名**；确认传入 `employeeId` |
| 22 | 复用 | `PageNav` / `StatusTag` / `van-list` / `moneyText` / `.list-item` 系列；**不新增组件** |

### 11.6 排班多班次

| # | 文件 | 改动 |
| --- | --- | --- |
| 23 | `src/modules/boss/views/schedule.vue` | ① 草稿值改数组 + 集合脏检查；② 当日行改多班次标签（`flex-wrap`）；③ 弹层改多选（整行 `aria-pressed` + `aria-live` 计数）；④ 快捷动作「早班+晚班（全天）」（无时段字段时禁用 + 原因）与「清空（休息）」；⑤ 弹层内冲突/上限 `role="alert"`；⑥ 铺排增加「覆盖/追加」模式（追加默认关） |
| 24 | 契约依赖 | 提交载荷 `shiftIds: []` 与 `shift.periodType/pairId`（用于"全天"）**须先由后端/算法/契约落定**（⑫-4）；未落定前：①②③⑤⑥（覆盖模式）可先做，**④ 的自动配对按钮保持禁用** |
| 25 | 复用 | `ActionBar` / `PageState` / `StationPicker` / `bossConfirm` / `.sheet` 弹层与 `.chip--sm` 既有样式；**不新增组件** |

### 11.7 审批中心

| # | 文件 | 改动 |
| --- | --- | --- |
| 26 | `src/modules/boss/views/approval.vue`（新增） | 页壳：`PageNav title="审批中心"` + `#right`「刷新」；**v1.1：汇总筛选条**（总数 + 分类 chips + 「更新于 HH:mm」，页内实现，**替代** v1.0 的 notice-bar）→ `van-pull-refresh` 包裹分组区；按白名单筛 `todo.groups` 渲染 `TodoGroup`；整页四态用 `PageState`；页脚 `.tip`（工单口径说明）。**红线：页内不得出现通过 / 驳回动作**（8.10） |
| 27 | `src/router/index.js` | 新增 `/boss/approval`（`meta.roles=[ROLE.ADMIN]`, `title='审批中心'`）。**（可选）** 筛选态持久化用 `sessionStorage`（见 8.11），route 侧无需新增 query 定义 |
| 28 | `src/constants/todoGroups.js` | **不改**（分组定义与取数保持单一真源）；审批中心的白名单常量放**页内**（属视图过滤，不是数据定义） |
| 29 | `src/stores/todo.js` | 复用 `groups/counts/known/loading/refresh`；`approvalKeys` 与 `approvalTotal` 见 #3（宫格与页头共用同一派生）。**v1.1 另需在 `LOADERS` 补两处字段**（出参已含，**不改契约**）：`makeups` 行取 `reason`、`payrolls` 行取 `netAmount`（⑬-冲突 C1） |
| 30 | `packages/shared/src/ui/TodoGroup.vue` | **v1.1 微调（跨端共享，需回归 `MessagePage`）**：行模型加**可选 `note`**（1 行省略，`--text-2`，未传不渲染）；组头「计数」与「查看全部」**分色**（计数 `--text-2`、动作 `--color-primary`）。**若不改共享组件** → 维持现状，把 `note` 列为遗留（登记 ⑬-冲突 C2） |
| 31 | 复用 | `TodoGroup`（含 `retry`）/ `PageState` / 全局 `.fchip`（分类 chips）/ `van-pull-refresh`；「注册」胶囊**自绘**（见 8.4）。汇总条**页内实现**，**不新增具名组件、不新增接口** |
| 32 | 已闭环 | 「注册」胶囊的来源字段 **已具备**（`HrFlowVO.source`，⑧.4）；`to` 带 query 的组级筛选**已实现**（⑫-6）；`payrolls.人数` 无此字段（⑫-21） |

### 11.8 班次管理（v1.2）

> 端：`boss-h5`。**本文不改代码**；完整字段 / 文案 / 校验见 **⑭**，此处只给「文件 — 动作 — 复用」清单（与 ⑭.8 同源）。

| # | 文件 | 改动 |
| --- | --- | --- |
| 33 | `src/router/index.js` | 新增 3 条记录：`/boss/shifts`（`title='班次管理'`）、`/boss/shifts/create`（`title='新增班次'`）、`/boss/shifts/:id/edit`（`title='编辑班次'`）；均 `meta.roles=[ROLE.ADMIN]`；**静态 `create` 先于 `:id/edit`**（同 `station` 的注册顺序约束） |
| 34 | `src/modules/boss/views/attendance.vue` | `entries` 由 4 项改 **5 项**：**「班次管理」置首**（`icon: 'clock-o'`，`to: '/boss/shifts'`），其余 4 项**相对顺序不变**（⑭.1）；`:column-num="4"` 不改（5 项落 4+1） |
| 35 | `src/modules/boss/views/shift.vue`（**新增**） | 班次列表页：站点选择 + 计数 + 站点级提示条 + 列表行 + `ActionBar[{create}]` + 四态（⑭.2） |
| 36 | `src/modules/boss/views/shiftForm.vue`（**新增**） | 新增 / 编辑共用（按 `route.params.id` 判模式）：字段 + 时间选择 + 配色 chip + 站点级前置校验 + 保存反馈（⑭.3） |
| 37 | `src/api/attendance.js` | 新增写封装：`createShift`（`POST /shifts`）、`updateShift`（`PUT /shifts/:id`）、`deleteShift`（`DELETE /shifts/:id`），均 `{ silent: true }` 以把 400 / 9114 就地展示在 `ActionBar.note`（既有 `getShifts` 保留） |
| 38 | `src/modules/boss/views/attendanceRule.vue` | **（v1.2）** 空态 cell `label` 与底部 `.tip` 文案指向本端 `/boss/shifts`（⑭.1 / ⑭.4）+「去班次管理」按钮（≥44px）。**（v1.3）** 时段区由「只读」改**可就地改时间**：行内「修改时间」→ 编辑态（`van-cell` + `van-popup` + `van-time-picker`，30 分钟粒度、结束可 `24:00`）→ `PUT /shifts/{id}`（整对象，只变起止）→ 重拉 `GET /attendance/rule`；失败文案原样落 `role="alert"`（⑭.10） |
| 39 | `src/modules/boss/views/schedule.vue`（**登记遗留**，可选） | 两处 `请先在 PC 端维护班次`（`:603`、`:679`）改为「请先在『班次管理』维护班次」 |
| 40 | 复用 | `PageNav` / `PageState` / `StationPicker`（`allow-all=false`）/ `StatusTag` / `ActionBar` / `bossConfirm` / `van-field` / `van-cell` / `van-stepper` / `van-switch` / `van-popup` / `van-time-picker` / 全局 `.card` `.list-item` `.tip` `.section-title` `.chip` `.muted` `.skeleton-block` / `STATION_STATUS`（**不新增具名组件、不新增 Token、不新增色值**） |

### 11.9 门禁与回归核查点（交测试 / 主智能体）

- **v1.1** 宫格 **6 项**在 **320 / 375 / 430** 三档无横向溢出、热区全部 ≥44px；末行 2 格留白不产生空交互（可脚本度量，沿用既有 `verify-mobile-t13-t16.mjs` 思路）。
- `MeSection` 不再出现「算薪日设置」；`/boss/finance` 与 `/boss/payroll-settings` **均可达**同一页。
- 驿站 CRUD 全链：新增 → 详情 → 建账号 → 停用 → 删除拦截（4003）。
- 驿站助手详情**不存在** `PayrollStatusSteps` 节点；`PUBLISHED` 下确认/提异议仍可用。
- 员工页「历史工资单」可下钻到 `/boss/payroll/:id`。
- 排班多班次：0/1/2 班次三种呈现、上限（=2）与重叠提示可复现。
- 审批中心：5 类齐备、**不含工单**、逐组降级、全空/全失败两态可复现；与消息页待办同源（同一 store）。
- **v1.1** 审批中心：汇总条总数 = 5 类合计（排除工单）、分类 chips 计数 = 各组 `total`；chips 筛选后仅渲染该类；下拉刷新与右上刷新**同效**；汇总条总数 `aria-live="polite"`。
- **v1.1** 审批中心回归：**筛选态空**文案（「暂无待处理的{类名}」）与**整页空**文案**不同**；汇总条在某类失败时该类计数为 `—`（其余照常）。
- **v1.1** 红线：审批中心页内**无**「通过 / 驳回 / 同意」类按钮（静态审查页面模板可查）。
- **v1.1** 首页回归：6 项齐备（审批中心 / 工单管理 / 考勤概览 / 异常预警 / 财务管理 / 驿站管理），且**不再出现**「补卡审批 / 工资单审核 / 入离职审批 / 请假审批」4 项；`approvals` 角标 = 不含工单的 5 类合计。
- **v1.2** 班次管理入口：「考勤概览 → 考勤管理」含 **5 项**（班次管理置首）；点「班次管理」可达 `/boss/shifts`；驿站可切换（`StationPicker`）。
- **v1.2** 班次管理全链：新增（名称 / 起止 / 配色 / 休息 / 状态）→ 列表可见；编辑改起止 → 列表与「打卡规则」页时段同步（因果关系成立）；停用 / 启用 / 删除可用；删除被排班引用班次回 **400「该班次已被排班引用，不能删除」**。
- **v1.2** 班次校验：保留名「全天班」（含前后空白）保存被**阻断**且文案用服务端口径；启用数 >2 / 一早一晚冲突为前端**阻断**、同站时间重叠为**仅提示不阻断**；最终拦截以后端 **9114** 为准（演示态 Mock 未实现该站点级校验，见 ⑫-31）。
- **v1.2** 班次四态：loading 骨架 / empty「该驿站暂无班次」/ error + 重新加载 / normal；配色 chip 选中态**不只靠颜色**（`aria-pressed` + 文字标签）。
- **v1.2** 回归：`/boss/schedule` 与 `/boss/attendance/rule` 不再出现「请在网页端维护班次」类文案（改指向 `/boss/shifts`）。

---

## ⑫ 与既有实现 / 契约的冲突登记（**只登记，不改代码**）

> 下列为本次精读实测发现，供主智能体分派与裁决；本设计文档**不修改任何代码 / 契约 / Mock / 字典**。

| # | 位置 | 现状 | 冲突 / 缺口 | 影响与建议 |
| --- | --- | --- | --- | --- |
| 1 | [HomeQuickGrid.vue:10-17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/HomeQuickGrid.vue#L10-L17) | 规则「4×2 共 8 项；第 9 项起下沉『我的』」 | 本批 10 项（+3）突破该上限 | 需把 B1 规则改为「4×3=12 格上限」；`hrm-demo` 员工端 9~10 项已有先例（组件 TODO 注释） |
| 2 | [api.md:560](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L560-L560) + `mock/routes/employee.js:31` | `ASSIGNABLE_ROLES=['ADMIN','STAFF']`（一期不开放 `STATION_ADMIN`） | **需求 ④「站长账号新增/改角色」不可达** | 「新增站长账号」须禁用 + 原因；**升级路径**：契约放开 `STATION_ADMIN` 分配 + 口径确认（谁能授站长、越权边界）→ 由主智能体裁决（P0.6 前先定口径） |
| 3 | `mock/routes/*`（**无 registration 路由**）+ boss-h5 全域**无 registration 引用** + 入离职列表出参无 `source` | 注册为契约先行（`api.md §4.11` R-2/R-3/R-8），Mock 与 boss-h5 均未落地 | **审批中心「注册」标识无数据来源**；Mock 下 `POST /registration` 不可达 | 设计侧已给降级（不显示胶囊、详情页可见）；**升级路径**：Mock 补 `POST /registration` 种子 + 入离职列表补 `source`/`registration` 字段 |
| 4 | `schedule.vue` 的 `matrix.shifts` 仅 `{id,shiftName,startTime,endTime,color}`；提交 `{employeeId,workDate,shiftId}` | 无「时段/配对」字段；载荷为**单值** | 需求 ⑤ 的两处不可达：①「早班+晚班=全天」无判定依据；② 多班次无法提交 | 须**契约扩展**：`shiftIds: []`（或等价）+ `shift.periodType`/`pairId`；**重叠判定口径须算法工程师确认**。未定前 ④ 按钮禁用、载荷维持单值 |
| 5 | `stores/todo.js` 仅暴露 `total`（含 `orders`） | 无「排除工单的审批合计」派生 | 审批中心宫格角标无现成值 | **纯前端新增派生**（`approvalKeys`/`approvalTotal`/`approvalKnown`），不改契约；见 #3 |
| 6 | [stores/todo.js:173-176](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/stores/todo.js#L173-L176) `toTarget` | **已实现**（核实 2026-09-27）：`params.status` 为字符串枚举时附加 query（`{ path, query: { status } }`），异议组落到 `?status=OBJECTED`；工单组 `status` 为数字 0 不附加 | **无缺口（已闭环）**——原文「未进入跳转目标」系文档滞后 | 无需再改；口径由前端定，与本节实现一致 |
| 7 | [MeSection.vue:54-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.vue#L54-L60) | 「算薪日设置」「站点管理（只读）」独立入口 | 与新 IA（财务管理收口 / 驿站管理可写）不一致 | 见 ⑪ #4（属改造清单；不破坏 `MeSection.spec.js`） |
| 8 | [station.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/station.vue) | 「只读骨架」：选站 → 名册；页脚「本页仅查看；员工分配与调整暂未开放」 | 与需求 ④（列表 + 新增 + 详情 + 账号维护）**IA 冲突** | 需重构该页与路由（`/boss/station` 语义变更）；其 TODO(扩展) 中「站点级员工写接口」前提已部分由 `POST/PUT /employees` 满足，但**跨站调动**仍缺 |
| 9 | [api.md:729](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L729-L729) | `GET /stations` **全量不分页**（<100） | 驿站管理列表**不做上拉分页** | 属契约事实，非缺陷；新增后前端**就地插入或重取**，不要造分页请求 |
| 10 | [api.md:581](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L581-L581) | 编辑员工**无** `username` / `password` | 编辑表单不得出现这两项 | 见 4.5；改口令走 `/password/reset` |
| 11 | `mock/routes/employee.js:133,141,174,188` | 自我保护 `2001` + 最后管理员保护 `2002` | 驿站账号维护中易触发 | 前置**禁用 + 原因**（不隐藏），见 4.5 |
| 12 | `apps/staff-h5/src/components/PayrollStatusSteps.vue` | 移除步骤条后可能**无消费者** | 死代码风险 | **本批不删**；由主智能体确认后决定删除或保留 |
| 13 | `payroll-ui-design.md §12 #23` | 记「boss-h5 无 `--fs-num-lg-boss`」 | **与实测不符**：`apps/boss-h5/src/styles/tokens.scss:43` **确有** `--fs-num-lg-boss: 24px`（同文件 `:44` 另有 `--fs-num-lg-staff: 22px`） | 事实性纠正，供主智能体回填该文档 |
| 14 | `demo-boss-ui-spec.md` 全文 | 引用路径为 `hrm-demo/src/...` | 实际工程为 `hrm-clients/apps/boss-h5/...`（`payroll-ui-design.md §12 #22` 已登记） | 本规范一律以 `hrm-clients` 路径为准 |
| 15 | `mobile.scss` | **无全局 `.fchip`**（各页 scoped 各写一份，见 `payrollSettings.vue` / `makeupApproval.vue` / `flow.vue` / `payroll.vue`） | 新增页面若用 fchip 需**自带样式**，且**第四次重复** | 建议上提为全局工具类或抽 `FilterChips`（既有 `staff-h5/components/FilterChips.vue` 先例）；本轮**不作强制**，但新增 3 页若各写一份 → 达 7 份，建议随本批收口 |
| 16 | `ROLE_LABEL` vs `ASSIGNABLE_ROLES` | 列表可显示 `role='STATION_ADMIN'`（「站长」），但新增/编辑**不可选**该角色 | 「看得到、改不了」的口径差 | 与 #2 同源；账号列表须能**显示**站长，编辑时角色下拉**不含**站长（或禁用站长项 + 原因） |
| 17 | `dict.js` `STATION_STATUS` | 仅 `{label, type}`，**无 `variant`** | `StatusTag` 走 `TYPE_FALLBACK`：`0=info→outline`、`1=success→soft` | 结果与设计意图一致（停用=终态描边、启用=浅底），**无需改字典**；登记以备核查 |
| 18 | `apps/boss-h5/src/api/org.js` | 只有 `getEmployees` / `getStationList` 两个只读封装 | 驿站 CRUD / 账号 CRUD 的写封装**不存在** | 需求 ④ 需新增 8 个封装（见 ⑪ #15） |
| 19 | `apps/boss-h5/src/api/hr.js` / `api/finance.js` | 无「按员工查工资单」的专用封装 | 需求 ⑥ 需 `getPayrolls({employeeId})` | `getPayrolls` 已支持任意 query 透传（Mock `payrollList` 亦支持 `employeeId`）→ **无需改契约**，仅调用侧传参（利好项） |
| 20 | `apps/boss-h5/src/modules/boss/views/approval.vue` | **不存在** | 需求 ⑥ 为新建页 + 新路由 | 见 ⑪ #26/#27 |
| 21 | [financeStore.js:752-766](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/financeStore.js#L752-L766) `toPayrollVO` 展开 `payroll` | 工资单列表**每行 = 一名员工的一张单**，**无「人数」字段** | v1.1 需求拟列的「工资单：账期/实发/**人数**」中「人数」**不可达**（无批量单据行） | 设计改展示 **账期 + 员工 + 实发（`netAmount`，出参已含）**；**「人数」不引入**。若确需按批次人数，须另立「算薪批次」视图（超出本批） |
| 22 | `stores/todo.js` `LOADERS`（`makeups`/`payrolls` 行映射） | 行文案**未取** `reason`（补卡事由）与 `netAmount`（工资单实发） | 二者 **出参已含**，仅映射层未取 → **不构成契约缺口** | **前端补字段**（⑪ #29），不改契约；行文案为两页共享（消息页同受益） |
| 23 | [TodoGroup.vue:30-43](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/shared/src/ui/TodoGroup.vue#L30-L43) | 行模型仅 `{key,title,meta,tag}`；组头计数与「查看全部」**同色**（均 `--color-primary`） | 审批中心需**可选 `note` 行**（补卡事由）与**计数/动作分色** | **共享组件微调**（⑪ #30），**影响 `MessagePage` 与 staff 端** → 须回归；若不改则把 `note` 列为遗留 |
| 24 | [mobile.scss:351-370](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/styles/mobile.scss#L351-L370) | **已有全局 `.fchip` / `.fchip--active`** | 与 ⑫-15「`mobile.scss` **无**全局 `.fchip`」记载**不符**（该轮已上提，注释注明「管理能力扩展批上提为全局工具类」） | **事实性纠正**：新页面（含审批中心汇总条）**直接复用全局 `.fchip`**，无需自带样式 |
| 25 | [router/index.js:267](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L267-L267) `scrollBehavior: () => ({ top: 0 })` + 移动端**无 keep-alive**（[NoticeList.vue:142](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/NoticeList.vue#L142-L142)） | 返回一律归顶、组件重挂 | v1.1「返回后保持**滚动位置**」**不可达** | **超范围**：需改全局 `scrollBehavior` 或给本页加 keep-alive（布局改造）→ **登记回报主智能体**；一期以「固定分组顺序 + 筛选 chips」补偿（8.11） |
| 26 | [approval.vue:58-65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/approval.vue#L58-L65) | `van-notice-bar` 仅 `wrapable`，**无 `closeable`** | ⑧.2（v1.0）描述为「可关闭」的 notice-bar，**与实现不符** | **事实性纠正**；v1.1 该 notice-bar 被**汇总筛选条替代**，此项随之消解 |
| **27** | [api/attendance.js:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/api/attendance.js#L44-L44) | boss-h5 **只有** `getShifts`（只读） | 移动端班次维护缺 `createShift` / `updateShift` / `deleteShift` 写封装 | 需求 ⑧ 需新增 3 个封装（⑪-37 / ⑭.8）；**不改契约**（PC 侧 [api/attendance.js:64-76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/src/api/attendance.js#L64-L76) 已有同路径实现） |
| **28** | `boss-h5/src/router/index.js` | **无** `/boss/shift*` 任何记录 | 需求 ⑧ 无路由可达 | 新增 3 条记录（⑪-33 / ⑭.1）；**静态 `create` 先于 `:id/edit`** |
| **29** | [attendance.vue:73-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendance.vue#L73-L78) | 考勤管理分组 `entries` **恒 4 项**、`van-grid :column-num="4"` | 新增第 5 项后落 **4+1**（末格留白） | 设计**接受 4+1**（与 ②「6 项落 4+2」同构）；**不改列数**；已核实 `attendance.vue` **无**对应 spec 断言 entries，重排/新增不破坏护栏 |
| **30** | `GET /shifts` 出参（`toShiftVO`，`attendanceStore.js:469-479`） | 仅 `{id,stationId,stationName,shiftName,startTime,endTime,color,restMinutes,status}` | **无**「是否被排班引用」字段 → 列表**无法**展示引用状态 | 设计**不展示**引用状态（**宁可不说，不可说错**，不得按名称/颜色推测）；删除时以服务端 400 为准；**升级路径**：后端在 `GET /shifts` 补 `referenced`（boolean）或 `scheduleCount` |
| **31** | Mock [routes/attendance.js:176-196](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/attendance.js#L176-L196) | `createShiftHandler` / `updateShiftHandler` **仅校验单条**，**未实现**站点级「启用数 ≤2 / ordinal 一早一晚」 | 演示态与 `hrm-server` [AttendanceShiftServiceImpl:280-306](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceShiftServiceImpl.java#L280-L306)（**已实现 9114**）**不一致** | 前端**前置校验**在演示态承担主要提示；真实拦截以后端为准。**升级路径**：Mock 补 `validateShiftSet`（启用集合校验），使演示与真实一致 |
| **32** | 保留名文案双版本 | Mock「班次名称**不可使用**保留名「全天班」」vs `hrm-server`「班次名**不得使用**保留名「全天班」」 | 两处文案不一致 | 前端前置校验**统一采用服务端文案**（⑭.3）；建议 Mock 侧对齐（登记，非本设计可改） |
| **33** | [attendanceRule.vue:26,41,411,419](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L411-L419) | 文案「移动端不提供班次维护，请在网页端「排班管理 → 班次管理」维护班次」（**U-3 旧裁定**） | 与本次用户要求「移动端也能维护班次」**直接冲突** | 本设计**显式覆盖 U-3**（⑭.0）；文案改为**指向本端 `/boss/shifts`**；代码改动交前端（⑪-38）。**回填 `attendance-time-source-design.md` U-3 行**（主智能体执行） |
| **34** | [schedule.vue:603,679](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/schedule.vue#L679-L679) | 两处「请先在 **PC 端**维护班次」 | 与「移动端可维护」口径不一致 | **登记为遗留**（非本页必需）；建议改为「请先在『班次管理』维护班次」（⑪-39） |
| **35** | `demo-boss-ui-spec.md` 全文 | 引用路径为 `hrm-demo/src/...` | 实际工程为 `hrm-clients/apps/boss-h5/...` | 与本规范 ⑫-14 同源；**本设计一律以 `hrm-clients` 路径为准** |
| **36** | ⑭.0「不变项」原写「打卡时段仍是**只读派生**（不可在打卡规则页编辑）」 | 与本次用户要求「打卡规则页可改打卡时间」**冲突** | **v1.3 改写不变项**：规则页时段 = **只读来源 + 可就地改起止（底层写班次）**（⑭.10）。**唯一时间真源仍为班次**，未新增时段写入通道（规则页仍不上报 `checkPeriods`）；⑫-33 的旧文案落点（`attendanceRule.vue`）已在本轮实现中一并改完 |

---

## ⑬ 首页宫格收敛与审批中心优化（v1.1）

> 本章为 v1.1 的**变更说明 + 决策取舍 + 落地清单 + 冲突汇总**；**最终态**以 ②（宫格）与 ⑧（审批中心）为准，本章不重复其表格。

### 13.1 变更摘要（对用户口径）

| 用户口径 | 落地 |
| --- | --- |
| 「补卡审批、入离职审批、请假审批、工资单审核在首页去除，并入审批中心」 | 宫格 10 → **6 项**（②.2）；4 类业务页 / 路由**保留**，仅首页不再直挂入口（②.1） |
| 「审批中心的页面优化一下，提高用户体验」 | 审批中心 **12 条优化**（⑬.3），落地于 ⑧.2–⑧.12 |

### 13.2 首页宫格（最终态速览）

最终 6 项（4 列 2 行）：**① 审批中心（count，前移第 1）② 工单管理 ③ 考勤概览 ④ 异常预警 ⑤ 财务管理（plain）⑥ 驿站管理（status）**。
角标：`approvals = approvalTotal`（5 类合计，**不含工单**）；其余沿用 `home.vue` 既有口径。**排序依据与过渡处理见 ②.2 / ②.2.1；角标逐项规则见 ②.3。**

### 13.3 审批中心优化（12 条，可判定）

| # | 优化条 | 落点 |
| --- | --- | --- |
| O1 | **顶部汇总筛选条**（总数 + 分类计数 + 「更新于 HH:mm」），替代 v1.0 的 notice-bar | 8.2 |
| O2 | **分类 chips 单选筛选**（全部/补卡/请假/入离职/工资单审核/工资单异议），本地过滤 | 8.2 / 8.9-E1 |
| O3 | **分组顺序固定**（业务紧迫度），**不做**按数量动态排序 | 8.2 取舍 B |
| O4 | **新增下拉刷新**（`van-pull-refresh` → `todo.refresh()`，与右上刷新同效） | 8.9-E3 |
| O5 | **不做加载更多**；底部 tip 说明「每组仅前 3 条，查看全部进业务页」 | 8.9-E4 |
| O6 | **行字段增强**：补卡加 `note`（事由）、工资单元信息加「实发 `netAmount`」 | 8.3 |
| O7 | **组头计数与动作分色** + `note` 行样式（1 行省略，`--text-2`） | 8.3 / ⑪#30 |
| O8 | **筛选态差异化空态**：「暂无待处理的{类名}」+「查看全部待办」（区别于整页空态） | 8.7 |
| O9 | **逐类错误降级**：单类失败只影响该类；汇总条该类计数 `—`（其余照常） | 8.7 |
| O10 | **状态与标识规范**：`StatusTag` 用既有色不新增；异议单为**全页唯一 solid 实心标签**；「注册」胶囊自绘（`--color-info-*`） | 8.4 / 8.6 |
| O11 | **可访问性**：`aria-live="polite"` 仅挂总数；chips `aria-pressed` + `aria-label` 带计数；触控 ≥44px、对比 ≥4.5:1 | 8.12 |
| O12 | **交互红线与状态保持**：**禁止一键通过/驳回**（须进详情）；**不**做快照内直达单条详情；返回保持**列表/筛选**、**不**恢复滚动位置 | 8.10 / 8.11 |

**最影响体验的 3 条：**
1. **O1 顶部汇总筛选条** —— 4 类入口从首页移除后，审批中心成为唯一审批入口；汇总条让「今天有几类、各几条、先处理哪类」在首屏一眼可得（v1.0 只有一句 `共 N 项`）。
2. **O2 分类 chips 计数直达** —— 「待我处理」的每类计数本身就是筛选入口，一次点击直达该分类，省掉「滚动找组」。
3. **O6 行字段增强（事由 / 实发）** —— 把快照从「知道有待办」提升到「知道**要不要批、批给谁、多少钱**」，是**决策前置**，直接减少「点进详情才发现不是这单」的往返。

### 13.4 对比度补算（v1.1 新增组合，按 L1 色值 + WCAG 2.x 相对亮度公式推导）

> 与文档既有口径同法；**落地后须实测复核**（本机未运行构建 / 走查，见头部验证声明）。色值来源：`tokens.base.scss`（L1）。

| 用途 | 前景 / 背景 | 对比度 | 判定 |
| --- | --- | --- | --- |
| 汇总条总数文字 | `--color-primary`(#0958d9) / `--surface-card`(#ffffff) | 6.16 | ✓ |
| 筛选 chip · 选中 | `--color-primary`(#0958d9) / `--color-primary-surface`(#e8f4ff) | **5.52** | ✓ |
| 筛选 chip · 未选 | `--text-2`(#4b5563) / `--surface-card`(#ffffff) | 7.56 | ✓ |
| 汇总条计数未知 `—` / 更新时间 `.tip` | `--text-3`(#6b7280) / `--surface-card`(#ffffff) | 4.83 | ✓ |
| 「注册」胶囊 | `--color-info-text`(#4b5563) / `--color-info-surface`(#edf0f4) | **6.61** | ✓ |
| 异议单 solid 标签文字 | `--text-on-dark`(#ffffff) / `--color-warning`(#b45309) | 5.02 | ✓ |

**本版零新增色值**：全部组合落在既有语义资源内（连「5.52」「6.61」两条也为既有色对的实算），**无新色、无新 Token**。

### 13.5 与 `MessagePage`（待办消息页）的分工与差异（避免两套口径）

| 维度 | 消息页「待办」子视图 | 审批中心 |
| --- | --- | --- |
| 定位 | Tab 根页的「通知 + 待办」快照 | **审批专用工作台**（唯一审批入口） |
| 分组范围 | **全量**（含工单 `orders`） | **工单排除**的审批 5 类 |
| 计数口径 | Tab 角标 = `todo.total`（**含工单**） | 汇总总数 = `approvalTotal`（**不含工单**）；与首页宫格 `approvals` 同源 |
| 交互增强 | 无（快照 + ≤3 行） | 汇总筛选条 / 下拉刷新 / `note` 行（v1.1） |
| 数据源 | `useTodoStore`（**同一份**） | `useTodoStore`（**同一份**，仅视图过滤） |

**结论（取舍）：不合并两页**。消息页是「通知 + 待办」的混合入口（受众 = 日常巡检），审批中心是「审批工作台」（受众 = 集中清待办）；两者受众与主任务不同，合并会稀释两者。**一致性硬保障**：行文案与取数**同源共享**（⑧.6），不一致仅在「是否含工单」一项，且已在审批中心页脚 `.tip` 说明。

### 13.6 落地清单（v1.1）

| 文件 | 改动 | 依据 |
| --- | --- | --- |
| `apps/boss-h5/src/constants/quickEntries.js` | 删 4 项（`makeups`/`payrolls`/`flows`/`leaves`）；`approvals` 前移至第 1；保留 6 项 | ②.2 / ⑪#1 |
| `apps/boss-h5/src/modules/boss/views/home.vue` | `quickData` 删 4 个 key；`hint` 改「待办与概览在前」 | ②.2 / ⑪#2 |
| `apps/boss-h5/src/modules/boss/views/approval.vue` | 新增**汇总筛选条**（页内）+ **下拉刷新** + **筛选态** + **`note` 行 / 「注册」胶囊自绘** | ⑧.2 / 8.9 / 8.10 / 8.11 / ⑪#26 |
| `apps/boss-h5/src/stores/todo.js` | `LOADERS` 补 `makeups.reason`、`payrolls.netAmount`（**不改契约**） | ⑧.3 / ⑪#29 |
| `packages/shared/src/ui/TodoGroup.vue` | 行模型加**可选 `note`**；组头**计数 / 动作分色**（**跨端，需回归 `MessagePage` 与 staff 端**） | ⑧.3 / ⑪#30 |
| `apps/boss-h5/src/router/index.js` | `/boss/approval`（v1.0 已列）；筛选持久化用 `sessionStorage`，**无需改路由** | ⑪#27 |
| `apps/boss-h5/src/constants/todoGroups.js` | **不改** | ⑪#28 |

**复用（不新增具名组件）**：`PageState` / `TodoGroup` / 全局 `.fchip` / `van-pull-refresh` / `StatusTag` / `moneyText` / `MiniChip`（仅尺寸参考）。
**新增 `note` 行不新增 Token**：高度随内容（1 行），字号 `--fs-caption`、色 `--text-2`、上边距 `--sp-1`。

### 13.7 冲突与遗留登记（v1.1）

| 编号 | 事项 | 性质 | 处置 |
| --- | --- | --- | --- |
| **C1** | `LOADERS` 未取 `makeups.reason` / `payrolls.netAmount`（**出参已含**） | 前端补字段，**非契约缺口** | 前端实现（⑪#29） |
| **C2** | `TodoGroup` 行模型无 `note`、组头计数与动作**同色** | 共享组件能力不足 | 微调共享组件（⑪#30），**须回归两端**；不改则列遗留 |
| **C3** | ~~入离职列表出参**无 `source`**~~ **已闭环**（2026-09-27 核实：`HrFlowVO.source` 已返回 `ADMIN`/`SELF_REGISTER`，前端已按实际值渲染「注册」胶囊） | 契约已具备 | 完成（降级分支保留为兜底） |
| **C4** | 工资单**无「人数」字段**（按员工一行） | 契约事实 | 不引入「人数」（⑫-21） |
| **C5** | 组 `to` **已携带 `params.status`**（`toTarget` 实现，核实 2026-09-27）→ 异议组正确落到 `?status=OBJECTED` | **已闭环**（前端） | 见 ⑫-6（原文「未携带」系文档滞后，已更正） |
| **C6** | `scrollBehavior` 固定归顶 + 无 keep-alive → 返回**不恢复滚动位置** | **超范围**（涉全局路由 / 布局） | 登记回报主智能体（⑫-25）；一期不做 |

**遗留（一期明确不做，非缺陷）**：**L1** 快照行内直达**单条详情**（8.10）；**L2** 返回后**滚动位置恢复**（8.11 / C6）。

### 13.8 取舍记录（决策一览，便于复核）

| 决策点 | 结论 | 一句话理由 |
| --- | --- | --- |
| 宫格 10 → 6 的布局 | **保持 4 列**（2 行 4+2，末 2 格留白） | 零组件改动、跨端一致；末行恰承载「管理与配置」 |
| 审批中心位置 | **前移第 1** | 它是审批唯一入口、最高频清待办入口 |
| 分组顺序 | **固定业务优先级** | 避免位置跳动；排序属算法口径 |
| 顶部汇总区 | **引入（与筛选条合并）** | 唯一入口下需首屏看清分类分布，且不产生装饰数字 |
| 下拉刷新 | **提供** | 单手可达、移动端惯例；与右上刷新同源 |
| 排序控件 | **不提供** | 见上；避免过度交互 |
| 加载更多 | **不提供** | 快照仅前 3 行，无分页语义 |
| 一键通过 / 驳回 | **禁止（红线）** | 防误批；审批有连带影响 |
| 行内直达单条详情 | **一期不做** | 补卡/请假无独立详情页，一致性差 |
| 返回保持滚动位置 | **一期不做** | 框架 `scrollBehavior` 归顶 + 无 keep-alive，改造超范围 |

---

## ⑭ 驿站精灵班次管理（v1.2）

> 端：`boss-h5`。本章为 v1.2 新增需求「**班次维护同步到驿站精灵端，入口在考勤概览-考勤管理里**」的**最终态**（入口 / 列表 / 表单 / 因果表达 / 校验 / 四态 / 冲突 / 落地 / 取舍），**逐条可判定**。
> 参考 PC 同能力页 [ShiftManager.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/src/views/schedule/components/ShiftManager.vue)：移动端**能力对齐、交互适配**（不做 1:1 搬运）。

### 14.0 变更摘要与 U-3 覆盖声明

| 项 | 内容 |
| --- | --- |
| 用户需求 | 「班次维护同步到驿站精灵端，入口在考勤概览-考勤管理里」 |
| 落地 | 「考勤概览 → 考勤管理」新增「班次管理」入口；新增 `/boss/shifts`（列表）与 `/boss/shifts/create`・`/boss/shifts/:id/edit`（表单）；能力与 PC `ShiftManager.vue` 对齐（增 / 改 / 停用启用 / 删除 / 站点级前置校验） |
| **覆盖旧裁定 U-3** | `attendance-time-source-design.md §12.1` **U-3** 原裁定「boss-h5『去维护班次』引导**指向网页端**（文案『请到网页端维护班次』）」——本次用户要求改为**移动端可维护**，**U-3 被本设计覆盖**。旧文案「移动端不提供班次维护 / 请在网页端维护」在本端**一律作废**（⑫-33 / ⑫-34），改指向本端 `/boss/shifts` |
| 不变项 | 班次仍是**打卡时段与上下班时间的唯一时间真源**。**（v1.3 改写）** 由 v1.2 的「打卡时段仍是只读派生、不可在打卡规则页编辑」改为「**只读来源 + 可就地改起止（底层写班次）**」：规则页改时间 = 写 `PUT /shifts/{id}`，改完重拉规则让派生时段跟随；**不新增第二条时段写入通道**（`PUT /rule` 传 `checkPeriods` 仍 400）。详见 ⑭.7 |
| 不做 | 见 ①.1.2「不做（v1.2）」两行；算法 / 契约口径仍归算法工程师 / 后端（①.1.2 首行） |

> **U-3 覆盖的理由（供主智能体归档）**：U-3 的原始理由为「避免出现『在移动端改了时段却其实不生效』的**假入口**」——该风险由**时段定义侧**决定。本次移动端承载的是**班次定义**（真正的写入口，改完即生效），而**非**在规则页直接改派生时段，故该风险**不再成立**；覆盖属**能力下沉 / 口径演进**，非推翻原判断。

### 14.1 入口与信息架构

**（1）考勤概览「考勤管理」分组最终项序（5 项，4 列，落 4+1）**

页：`attendance.vue` 的 `entries`（既有 4 项，[attendance.vue:73-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendance.vue#L73-L78)）。

| 位置 | 文案 | icon | to |
| --- | --- | --- | --- |
| **1（新增）** | **班次管理** | `clock-o` | `/boss/shifts` |
| 2 | 打卡规则 | `setting-o` | `/boss/attendance/rule` |
| 3 | 排班管理 | `calendar-o` | `/boss/schedule` |
| 4 | 打卡记录 | `records` | `/boss/attendance/records` |
| 5 | 异常明细 | `warning-o` | `/boss/attendance/records?status=ABNORMAL` |

- **排序规则**：**新增项置首，既有 4 项相对顺序一格不动**（与 ②.2「审批中心前移第 1、存活项相对顺序不变」同一原则）。既有相对序 `打卡规则 < 排班管理 < 打卡记录 < 异常明细` 保持。
- **置首理由**：班次是考勤域的时间真源，置首与紧随其后的「打卡规则」共同表达「先定班次 → 再定规则」；且它正是本次用户困惑的根因入口。
- **图标**：`clock-o` 为 Vant 实存图标（项目内 `staff-h5` 已在用），**不新增图标资源**。
- **布局取舍（登记）**：`van-grid :column-num="4"` **不改**，5 项落 **4 + 1**（末项「异常明细」单独占第 2 行首格），**接受**该留白（与 ②「6 项落 4+2」同构、零组件改动、跨端一致）。**否决**「改 3 列 / 加第 6 项凑满格」（前者改组件、后者塞无关入口）。
- **备选方案（否决）**：把「班次管理」插在「排班管理」之前（保 班次 → 排班 相邻）。**否**——为保 ② 的「相对顺序不变」稳定性，且 5 项下末格仍留白，收益不足以牺牲顺序规则。

**（2）路由命名（建议）**

| 路由 | name | title | 说明 |
| --- | --- | --- | --- |
| `/boss/shifts` | `bossShifts` | 班次管理 | 列表 |
| `/boss/shifts/create` | `bossShiftCreate` | 新增班次 | **静态段先于 `:id`** |
| `/boss/shifts/:id/edit` | `bossShiftEdit` | 编辑班次 | 与 `/boss/station/:id/edit` 同构 |

- **同一资源段（复数 `shifts`）贯串三条路由**（`/boss/shifts/...`），**不混用** `/boss/shift/:id/edit` 式单复数切换；`create` / `:id/edit` 子路径与既有 `/boss/station` 系列同构，注册顺序沿用「静态先于参数」（[router/index.js:125-126](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L125-L126)）。
- 独立成段（不挂 `/boss/attendance/*` 下），避免与 `/boss/attendance/rule`・`/boss/attendance/records` 前缀歧义。
- 均 `meta.roles = [ROLE.ADMIN]`（①.3 v1.2 补充）。

**（3）与既有 `/boss/attendanceRule`（打卡规则）的关系**

| 页 | 职责 | 关系 |
| --- | --- | --- |
| `/boss/shifts`（本页） | **班次全生命周期**：新增 / 编辑（含时间）/ 启用停用 / 删除 | 时间真源的定义面（唯一） |
| `/boss/attendance/rule`（既有） | **展示**由班次派生的时段 / 作息（只读来源），并**就地改时段起止**（底层写班次）；定义 WiFi / 围栏 / 时间窗 / 阈值 | **不重叠、不产生第二真源（v1.3）**：规则页的时段行「**只读来源 + 可就地改起止**」，改时间实际是 `PUT /shifts/{id}`，改完重拉规则让派生时段跟随；规则页**始终不上报** `checkPeriods`（`PUT /rule` 传非空即 400）。班次的增删停用、配色、休息时长仍只在 `/boss/shifts` |

- **打卡规则页文案（最终版 · v1.3）**：
  - **空态 cell**：`title`「该驿站尚未配置启用班次」，`label`「去『班次管理』新增班次后，本站打卡时间自动生效」，并附「**去班次管理**」按钮（跳 `/boss/shifts`，≥44px）。**无启用班次时本页不凭空造班次**。
  - **时段区标题 extra**：`{n} 个时段 · 可改时间`（v1.2 为 `· 只读`）。
  - **时段行（展示态）**：`时段 {i} · {班次名}` + `{起} - {止}` + 行内「**修改时间**」按钮（≥44×44，仅管理员且该行配到启用班次时渲染）。
  - **时段行（编辑态）**：就地展开「开始时间 / 结束时间」两行（`van-cell is-link` → `van-popup` + `van-time-picker`，30 分钟粒度、结束可 `24:00`，与 `shiftForm.vue` 同口径）+ 说明 `label`「此处修改的即本站班次时间，保存后打卡时段随之生效」+「取消 / 保存时间」。
  - **底部 `.tip`**：「打卡时段与上下班时间由该驿站班次决定（唯一时间真源）：本页改的即班次时间，保存后打卡时段随之生效；班次的增删停用仍在『考勤概览 → 班次管理』。」
- **是否需把引导按钮改为跳移动端班次管理**：**是**（原「请在网页端维护班次」文案作废）。落点：空态 cell、底部 `.tip`、班次未取到时的降级说明。

### 14.2 班次列表页（`/boss/shifts`）

**（1）站点上下文**
- 沿用 `StationPicker`（`allow-all=false`）+ 页顶 `.station-pick card` 按钮（与 `/boss/schedule`・`/boss/attendance/rule` 同模式，[schedule.vue:497-500](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/schedule.vue#L497-L500)）。
- 默认取 `getStationList()` 首个驿站；**切换驿站 → 重拉班次列表**。
- 站点列表 loading / error / retry 由 `StationPicker` 的 `loading` / `error` / `retry` 承载（**区块级**，不把整个弹层或整页替换成错误页）。

**（2）列表行结构（三行，`--row-h-3` = 76px）**

```
┌──────────────────────────────────────────────┐
│ ● 早班                              [ 启用 ] │  ← 行1：色条+名称 + StatusTag
│ 08:00 - 16:00 · 休息 60 分钟                 │  ← 行2：时间 · 休息
│                          [停用]    [删除]    │  ← 行3：行内动作（各 ≥44×44）
└──────────────────────────────────────────────┘
```

| 字段 | 呈现 | 依据 |
| --- | --- | --- |
| 班次名 | 左侧 `--shift-bar-w` 色条（取 `row.color`，**仅装饰**）+ 名称文字 | 复用 ⑦.2 班次标签口径；**色条不承载语义**，名称文字必在 |
| 起止 | `{startTime} - {endTime}`，`.tabular-nums` | 与 PC `ShiftManager.vue:310-312` 同口径 |
| 休息时长 | `休息 {restMinutes} 分钟` | PC 同口径 |
| 状态 | `StatusTag`（`STATION_STATUS`）：启用 `1` → success soft；停用 `0` → info outline | 与 PC `ShiftManager.vue:316-319` 同源；`dict.js` 该项无 `variant`，走 `TYPE_FALLBACK`（⑫-17） |
| 是否被排班引用 | **不展示**（契约无该字段，⑫-30） | 「**宁可不说，不可说错**」，不得按名称 / 颜色推测 |
| 行点击 | 进入 `/boss/shifts/:id/edit` | 整行可点；行内动作 `@click.stop` 阻止冒泡 |

**（3）列表操作**

| 操作 | 位置 | 行为 | 确认 |
| --- | --- | --- | --- |
| 编辑 | 行点击 | 跳 `/boss/shifts/:id/edit` | 否 |
| 停用 / 启用 | 行内按钮（按当前状态二选一显示） | `PUT /shifts/:id`（携带该行全字段 + 翻转后的 `status`）；成功后局部刷新 | 否（**可逆**）；**启用**可能触发站点级 9114 → 失败时就地提示并刷新 |
| 删除 | 行内按钮 | `bossConfirm` → `DELETE /shifts/:id` | **是**（`bossConfirm`） |

- **删除前被引用时的拦截**：契约 `GET /shifts` **无**引用标志 → 前端**不做**引用前置判断；由 `bossConfirm` 的 `impact` 明确约束，删除失败时**原样展示服务端文案**：
  - `bossConfirm({ action: '删除班次', target: '{驿站} · {班次名} {start}-{end}', impact: '已被排班引用的班次不能删除；删除后不可恢复', irreversible: true, confirmText: '确认删除' })`。
  - 服务端 400：**「该班次已被排班引用，不能删除」**（`hrm-server` `AttendanceShiftServiceImpl:168`；Mock 同文案）——失败 Toast 提示，**列表不变**。
  - **升级路径**：后端在 `GET /shifts` 出参补 `referenced` 后，可改为「引用中」胶囊 + 删除按钮**禁用 + 原因**（⑫-30；禁用而非隐藏，遵循既有口径）。

**（4）计数与站点级提示**
- 计数行（`.section-title__extra`）：`共 {n} 个班次 · 启用 {m}`。
- **站点级提示条**（`van-notice-bar` 或 `.card` 内 warning 段，`role="alert"`），**仅提示不阻断列表**，与 PC `ShiftManager.vue:154-164` 的 `stationHint` 同口径；二者**互斥取一条**（超限优先）：
  - 启用数 > 2：**「本站启用班次 {m} 个，超过 2 个上限：打卡时段只取其中互不重叠的 2 个，建议停用多余班次。」**
  - 启用班次一早一晚冲突：**「本站启用班次存在同落上午 / 下午的班次：打卡时段需一早一晚才可区分，请调整班次起止或停用其一。」**

**（5）空态 / 工具栏**
- 列表空：`PageState` empty——`emptyText` **「该驿站暂无班次，点下方『新增班次』开始配置」**（与错误态文案**必须不同**，⑩.4）。
- `ActionBar[{ key:'create', label:'新增班次', plain:false }]` **恒显示**（空态亦显示）；空态文案指向它。

### 14.3 班次新增 / 编辑页（`/boss/shifts/create` · `/boss/shifts/:id/edit`）

共用组件按 `route.params.id` 判模式（`create` / `edit`）；`PageNav :title` 随之切换（「新增班次」/「编辑班次」）。

**（1）站点上下文**
- **新增**：页顶 `.station-pick` 可选（沿用 `StationPicker`），提交体带 `stationId`。
- **编辑**：站点**只读展示**（驿站名文字，不可切换）——契约 `PUT /shifts/:id` **不含** `stationId`，班次不跨站迁移。

**（2）字段与控件（与 PC `ShiftManager.vue:335-379` 能力对齐）**

| 字段 | 控件 | 默认（新增） | 说明 |
| --- | --- | --- | --- |
| 班次名称 | `van-field`（`maxlength=20`、`show-word-limit`） | `''` | 必填；长度 1-20 |
| 开始时间 | `van-cell is-link`（只读展示）→ `van-popup` + `van-time-picker` | `08:00` | 分钟粒度 **30**（与 PC `step=00:30` 一致） |
| 结束时间 | 同上 | `16:00` | 允许 `24:00`（当日收班） |
| 班次配色 | 3 个受控 `.chip`（色块 + 文字标签） | 早班蓝 | **受控 3 色**，见（4） |
| 休息时长（分钟） | `van-stepper`（`min=0 max=480 step=15`、`button-size=32px`） | `60` | ≥0 |
| 启用状态 | `van-switch`（`active-value=1 inactive-value=0`） | 开（`1`） | 配底部 `.tip`：「停用后该班次不再被排班引用，也不再作为打卡时段」 |

**（3）移动端时间选择交互（与既有页一致）**
- 复用既有模式（[payrollSettingEdit.vue:413-421](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/payrollSettingEdit.vue#L413-L421)）：`van-cell` 触发 → `van-popup round position="bottom" safe-area-inset-bottom"` → `van-time-picker v-model :columns-type="['hour','minute']" :title @confirm @cancel`；`@confirm` 取 `{ selectedValues }` 拼接 `HH:mm`。
- **分钟粒度 30**：用 Vant TimePicker 的 `filter(type, options)` 把 `minute` 列限为 `00 / 30`；**实现前须按 Vant 4.10 实存 API 核对 `filter` 签名**（求证优先，全局规则 §1）。
- **`24:00` 边界表达（结论）**：结束时间 picker **允许 `hour = 24`**，且 `hour === '24'` 时 `minute` 列**仅 `00`**；界面统一显示 **「24:00」**（**不写「00:00」**，避免与次日零点混淆）。后端 `isEndClock` 已接受 `24:00`（[routes/attendance.js:157](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/attendance.js#L157-L157)）。展示形如「16:00 - 24:00（当日收班）」。
- **降级**：若 Vant `van-time-picker` 无法直接支持「小时含 24」或「分钟过滤」，**降级**为自定义 `van-picker`（时列 `00–24`、分列 `00/30`），**交互与校验不变**；降级须在实现交付时标注理由。**不采用**原生 `<input type="time">`（无法表达 `24:00`）。

**（4）配色选择（颜色选择器的移动端取舍）**
- **受控 3 色**（与 PC `SHIFT_COLORS` 一致，取自设计 Token，**不新增色值**；`value` 为接口 `#RRGGBB` 字面量，`token` 为渲染色块变量，**模板不写死色值**）：

| 文字标签（必在） | token 渲染色块 | 提交值（接口 `#RRGGBB`） |
| --- | --- | --- |
| 早班蓝 | `--c-blue-700` | `#0958D9` |
| 中班橙 | `--c-orange-500` | `#FA8C16` |
| 晚班深灰 | `--c-neutral-800` | `#1F2937` |

- **取舍**：**不做**自由取色器（移动端不适合：色轮 / 取色板触控精度差、易产出不在 Token 色板内的颜色、与 ⑩.3「零新增色值」冲突）。**只提供 3 个受控配色**（与 PC 一致）。
- **可访问性**：色块 `aria-hidden`；**名称文字标签始终显示**（状态**不只靠颜色**，SC 1.4.1）；选中态用 `role=radio` + `aria-checked`（或 `aria-pressed`）**与视觉双表达**。

**（5）校验与提示文案 —— 前置阻断 vs 仅提示**

**A. 字段级 · 前置阻断**（保存时校验，字段下 `role="alert"`；命中即不提交）：

| 场景 | 触发条件 | 文案（**采用服务端口径**，⑫-32） |
| --- | --- | --- |
| 名称必填 | `trim` 后为空 | 请输入班次名称 |
| 名称长度 | 1-20 | 班次名称长度须为 1-20 |
| **保留名** | `trim` 后 === 「全天班」 | **班次名不得使用保留名「全天班」** |
| 开始时间必填 | 空 | 请选择开始时间 |
| 结束时间必填 | 空 | 请选择结束时间 |
| 起止有效性 | `minutesOfDay(start) >= minutesOfDay(end)`（`24:00` 计 1440） | 结束时间须晚于开始时间 |
| 休息时长 | `< 0` | 休息时长须不小于 0 |

**B. 站点级 · 前置阻断**（保存前对「保存后启用集合」预算；命中则不提交并 `role="alert"`）：

| 场景 | 触发条件 | 文案 |
| --- | --- | --- |
| 启用班次数超限 | 保存后启用数 > 2 | 启用班次最多 2 个（当前将达 {n} 个），请先停用多余班次 |
| 一早一晚冲突 | 保存后启用班次 ordinal（早 / 晚）重复 | 启用班次须一早一晚：新班次与已有班次时段归属相同（请调整开始时间，或停用其一） |

**C. 站点级 · 仅提示不阻断**：

| 场景 | 触发条件 | 文案 / 呈现 |
| --- | --- | --- |
| 同站启用班次**时间重叠** | 半开区间 `[s,e)` 相交 | 表单底部 `.tip`（warning 色）：**「存在启用班次时间重叠，可能影响打卡时段与排班判定，请确认后再保存」**——**仍可保存** |

- **口径依据**：与 PC `ShiftManager.vue:216-226` **逐条一致**——超限 / 一早一晚**阻断**；重叠**只提示**（重叠属**排班侧 9110** 语义，**定义侧不额外收紧**）。
- **真源声明**：以上均为**前置体验**；**真实拦截以后端 9114 / 400 为准**。当前 Mock **未实现**站点级校验（⑫-31），故**演示态**下 B 组前置校验承担主要提示责任。

**（6）12:00 早晚分界 —— 是否需在界面显式说明（结论）**

> **结论：界面常驻文案【不写】「12:00」字面量；规则用「一早一晚」语义表达；仅在冲突发生时以「触发式提示」给出后果。**

理由（可判定）：
1. `middayBoundaryMinute` 是**可配置超参**（默认 `720` = 12:00；`AlgoProperties.java:121`、`application.yml:218`）。把「12:00」写进常驻界面 → 与配置漂移（`attendance-time-source-design.md` **V-3 未闭环**，产品尚未裁定是否提示）。
2. 用户**不需要**理解 ordinal 编码；只需知道「两个班次必须一个偏上午开始、一个偏下午开始」——**「一早一晚」**即准确，且不含超参。
3. 只在**冲突那一刻**给出针对性提示（B 组「须一早一晚」文案），比常驻说明更有效，也不误导。
4. **可平滑升级**：若 V-3 产品最终裁定「需显式提示」，只需在 B 组冲突文案后追加常量句「（下午班以 12:00 起算）」即可，**不返工**。
5. **与本页其他文案的关系**：列表页站点级提示条与表单 B 组文案**同用「一早一晚」语义**，全篇**不出现恒定「12:00」**。**登记**：本结论属**设计侧裁决**，与 V-3（待产品确认）不冲突——V-3 若裁定「需提示」，按第 4 条落地。

**（7）操作栏与保存反馈**
- `ActionBar[{ key:'save', label:'保存班次', plain:false, loading, disabled }]`；`note` 承载 A / B 组错误或 C 组提示；`submitting` 防连点（复用 `payrollSettingEdit.vue` 的 `saveError` **就地反馈**模式）。
- 保存成功：`showSuccessToast('班次已新增' / '班次已更新')` → **返回列表并刷新**（保证时间立刻反映）。
- 保存失败：400 / 9114 文案**就地**落 `note`（`role="alert"`），**不清空表单**；网络错误走通用 Toast。

### 14.4 因果关系表达（「班次决定打卡时段与上下班时间」）

| 位置 | 文案（最终） |
| --- | --- |
| 列表页顶部 `.tip` | 「班次决定打卡时段与上下班时间：本页新增 / 调整班次后，本站打卡时间**即时跟随**，无需在『打卡规则』另行配置。」 |
| 表单页 `.tip`（状态字段下方） | 「停用后该班次不再被排班引用，也不再作为打卡时段。」 |
| 打卡规则页底部 `.tip`（⑭.1-(3)） | 「打卡时段与上下班时间由该驿站班次决定（唯一时间真源）：本页改的即班次时间，保存后打卡时段随之生效；班次的增删停用仍在『考勤概览 → 班次管理』。」 |
| **打卡规则页时段编辑态说明（v1.3）** | 编辑行 `label`：「**此处修改的即本站班次时间，保存后打卡时段随之生效**」——把「规则页改时间 = 改班次」这一步因果写在动作就地，避免用户以为又出现了一套独立时段配置 |

- 四处**同一因果**、**不搬运 PC 长句**（移动端精简）；与 PC `ShiftManager.vue:285-287` 的 `shift-card__note` **同义**。

### 14.5 响应式与可访问性

| 项 | 规范 |
| --- | --- |
| 视口 | 320 / 360 / 375(基准) / 390 / 414 / 430 / 640(横屏)：单列；**无横向滚动**（⑩.1） |
| 触控 | 站点按钮 52；列表行 ≥76；行内动作 ≥44×44；配色 chip ≥44；时间 cell ≥48；`ActionBar` ≥44（⑩.2 v1.2 行） |
| 对比度 | 配色 chip 选中 `--color-primary` / `--color-primary-surface` = 5.52 ✓；未选 `--text-2` / `--surface-card` = 7.56 ✓（⑩.3 v1.2 行，**落地后实测**） |
| **颜色语义** | 色块**仅装饰**，**必配文字标签**（早班蓝 / 中班橙 / 晚班深灰）；**不得仅靠颜色**传达状态（SC 1.4.1） |
| 无障碍 | 配色 chip `role=radio` + `aria-checked`（或 `aria-pressed`）；色块 `aria-hidden`；时间 cell `aria-label`（如「开始时间，当前 08:00」）；校验字段下 `role="alert"`；保存中 `ActionBar` `loading`（⑩.4 v1.2 行） |
| 长文本 | 班次名单行省略（`min-width:0` + ellipsis）；时间 `.tabular-nums` |
| 动效 | 弹层 / 骨架 ≤200–300ms，`prefers-reduced-motion` 由 Token 层承接 |

### 14.6 四态与保存反馈

| 页面 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 列表 | `PageState :rows="4"`（骨架与行同高） | 「该驿站暂无班次，点下方『新增班次』开始配置」 | `PageState error` + **重新加载**（`@retry`）；站点列表失败 → `StationPicker` **区块级**错误 + 重试 | 列表行 + 计数 + 站点级提示条 |
| 表单（编辑态） | `PageState :rows="6"` | —（表单无空态） | `PageState error` + 重新加载 / 返回列表 | 表单可编辑 |
| 表单（新增态） | 无（无取数） | — | — | 表单可编辑 |

- **保存中**：`ActionBar` 主按钮 `loading` + `submitting`（禁用其余动作，防连点）。
- **保存失败**：见 14.3-(7)——就地 `role="alert"`，**保留输入**。
- **删除中**：行内删除按钮进入 loading；失败 Toast + 列表不变。
- **空 / 错态文案必须不同**（⑩.4 硬规则）。

### 14.7 与既有实现 / 契约的冲突登记（**只登记，不改代码**）

> 本次新增冲突沿用 ⑫ 编号，追加 **27–36**（详见 **⑫** 表）。摘要：

| 编号 | 事项 | 性质 | 处置 |
| --- | --- | --- | --- |
| ⑫-27 | boss-h5 只有 `getShifts`，缺 3 个写封装 | 实现缺口（**非**契约缺口） | 新增封装（⑪-37） |
| ⑫-28 | 无 `/boss/shift*` 路由 | 实现缺口 | 新增 3 条（⑪-33） |
| ⑫-29 | 考勤管理分组 5 项落 4+1 | 布局事实 | **接受**；不改 grid |
| ⑫-30 | 出参无「被排班引用」字段 | **契约缺口** | 不展示引用状态；升级路径：补 `referenced` |
| ⑫-31 | Mock **未实现**站点级 9114（服务端已实现） | **演示与真实不一致** | 前端前置校验兜底；建议 Mock 补齐 |
| ⑫-32 | 保留名文案双版本（Mock vs Server） | 文案不一致 | 前端统一用服务端口径 |
| ⑫-33 | `attendanceRule.vue` 旧文案 = **U-3 旧裁定** | **口径冲突** | 本设计覆盖 U-3（⑭.0）；回填 U-3 裁定行 |
| ⑫-34 | `schedule.vue` 两处「PC 端维护班次」 | 口径不一致 | 登记遗留（⑪-39） |
| ⑫-35 | `demo-boss-ui-spec.md` 路径滞后 | 文档事实 | 以 `hrm-clients` 为准 |
| **⑫-36** | v1.2「⑭.0 不变项：打卡时段只读」与 v1.3 需求（规则页可改时间）冲突 | **口径演进** | **v1.3 改写不变项**（⑭.10）；真源仍为班次、未新增写入通道 |

- **超范围 / 需升级的项**：**无**——本需求全部落在 `boss-h5` 内，**不触 `hrm-admin` / `hrm-server`**；唯一需主智能体**归档**的是「**U-3 覆盖**」（⑭.0）与「**V-3 提示结论**」（14.3-(6)）。

### 14.8 落地清单（交前端工程师；与 ⑪.8 同源）

> 复用优先，**不新增具名组件 / Token / 色值**。完整清单见 **⑪.8（#33–#40）**，要点：

| 类型 | 内容 |
| --- | --- |
| 新增文件 | `views/shift.vue`（列表）、`views/shiftForm.vue`（表单） |
| 改既有文件 | `router/index.js`（3 条路由）、`attendance.vue`（entries 5 项）、`api/attendance.js`（3 个写封装）、`attendanceRule.vue`（引导文案）、`schedule.vue`（遗留文案） |
| 复用 | `PageNav` / `PageState` / `StationPicker` / `StatusTag` / `ActionBar` / `bossConfirm` / `van-field` / `van-cell` / `van-stepper` / `van-switch` / `van-popup` / `van-time-picker` / 全局 `.card` `.list-item` `.tip` `.section-title` `.chip` `.muted` `.skeleton-block` / `STATION_STATUS` |
| 禁止 | 新增语义色 / 十六进制字面量；自造第二套确认弹窗；页面内重算服务端口径之外的规则 |

### 14.9 取舍记录（决策一览，便于复核）

| 决策点 | 结论 | 一句话理由 |
| --- | --- | --- |
| 入口位置 | 考勤概览 →「考勤管理」分组，**置首** | 时间真源与考勤同域；与后续「打卡规则」表达「先定班次」 |
| 分组项数 | **5 项**（落 4+1），不改列数 | 零组件改动、跨端一致 |
| 路由命名 | `/boss/shifts` + `/shifts/create` + `/shifts/:id/edit`（**同一复数段**） | 与契约资源同名、与 `station` 子路径同构，不混用单复数 |
| 站点选择 | 列表可切换；**编辑页站点只读** | `PUT` 不含 `stationId`，班次不跨站 |
| 颜色选择器 | **受控 3 色**，不做自由取色 | 移动端取舍 + 零新增色值（⑭.3-(4)） |
| 时间选择 | `van-time-picker`、**30 分钟粒度**、允许 `24:00` | 与 PC 一致；不开放分钟自由输入 |
| 引用状态展示 | **不展示** | 契约无字段，「不可说错」（⑫-30） |
| 超限 / 一早一晚 / 重叠 | **阻断 / 阻断 / 仅提示** | 与 PC 逐条一致；重叠属排班侧语义 |
| 12:00 分界 | **常驻不写**，改「一早一晚」语义 + 触发式提示 | 超参可配、V-3 未闭环（14.3-(6)） |
| 列表内 3 动作同置 | **接受**（编辑 / 停用启用 / 删除） | 满足「列表即维护」诉求；删除有 `bossConfirm` 兜底 |

### 14.10 打卡规则页「就地改时间」（v1.3）

**（1）定位**：`/boss/attendance/rule` 的「打卡时段」由 v1.2 的**纯只读**改为「**只读来源 + 可就地改起止**」。改的**不是**规则字段，而是该时段对应的**班次记录**（`PUT /api/v1/shifts/{id}`，只变 `startTime` / `endTime`）。

**（2）为什么不是「在规则页直接改时段」**：时间真源仍是班次（`GET /attendance/rule` 出参 `checkPeriods` 为派生值、`checkPeriodsReadonly=true`；`PUT /rule` 传非空 `checkPeriods` 回 `400`）。若规则页另存一份时段，会出现「班次 08:00-16:00」与「规则时段 08:00-12:00」并存的**时间分裂**。故本能力**只改班次**，且改完**重拉规则**回读派生结果——**不引入第二套时间**。

**（3）页面交互（可判定）**

| 元素 | 规范 |
| --- | --- |
| 时段区标题 extra | `{n} 个时段 · 可改时间`（原 `· 只读`） |
| 展示态行 | `van-cell`：`时段 {i} · {班次名}` / `{起} - {止}`；`right-icon` 为原生「修改时间」按钮（≥44×44，`--color-primary`，不新增组件/色值） |
| 编辑态行 | 就地展开（同一 `van-cell-group inset` 内，不换弹层页）：`时段 {i} · {班次名}`（`label` 写清因果）+「开始时间」+「结束时间」（`van-cell is-link` → `van-popup` + `van-time-picker`）+「取消 / 保存时间」 |
| 时间粒度 | **30 分钟**；结束时间可 **`24:00`**（与 ⑭.3-(5) / `shiftForm.vue` 同一 `filter` 口径，不写第二套） |
| 保存 | `PUT /shifts/{id}`，整对象提交（`shiftName` / `startTime` / `endTime` / `color` / `restMinutes` / `status`，**其余字段原样带回不丢**，api.md §4.8.3）；成功后 `showSuccessToast('打卡时间已更新')` + **重拉 `GET /attendance/rule`**（派生时段跟随刷新） |
| 失败 | 服务端文案（如 `9114` 启用数超 2 / 一早一晚冲突）**原样**落行内 `role="alert"`，**不自行改写、不清空草稿** |
| 无启用班次 | 维持既有空态 +「去班次管理」引导，**本页不凭空造班次** |
| 两班站点 | 两行各自可改；改完任一行即重拉，另一行由服务端派生结果决定（不受影响） |
| 与 WiFi 白名单编辑 | **互斥**：任一处编辑中，另一处入口禁用，`ActionBar` note 提示「有 1 个打卡时段正在编辑，请先完成或取消」 |
| 权限 | 沿用 `isWifiEditable(auth.isAdmin)` 口径；非 ADMIN 只读、不渲染「修改时间」 |
| 降级 | 班次拉取失败（silent）→ 时段仍展示，但不渲染「修改时间」，给一行 `.tip` 说明并指路 `/boss/shifts` |

**（4）配对口径（为什么需要一次配对）**：契约的派生时段 `CheckPeriod[]` 只有 `{ name, startTime, endTime }`、**不含班次 id**（api.md §4.6.1），而写入口只有班次。故按 `stationId` 取 `GET /shifts`（api.md §4.8.1，升序），把每一行时段配到**该驿站启用班次**：先按 (班次名, 起止) 三元组精确命中，命中不到且两侧数量一致时按升序位置兜底，仍配不到则该行**只读**（宁可不给入口，也不拿相邻行 / 别站班次顶替）。纯逻辑单测见 `apps/boss-h5/src/utils/shiftPeriods.spec.js`。

**（5）与「单一真源」原则的一致性**：本能力**未新增时段写入通道**——规则页提交体仍不含 `checkPeriods`（⑭.1-(3)），页面显示的时间仍全部来自 `checkPeriods` 回读。因此「班次 = 唯一时间真源」不变；U-3 所担心的「假入口」（改了不生效）也不成立：改的就是真源本身，保存即生效。

---

## 附录 A：Token 引用对照（以 `hrm-clients` 实际变量名为准）

| 用途 | 变量 |
| --- | --- |
| 主色 | `--color-primary` / `--color-primary-icon`（**不承载文字**）/ `--color-primary-surface` / `--color-primary-border` |
| 语义结构族 | `--color-success|warning|danger|info` + `--color-*-surface` / `--color-*-border` / `--color-*-icon` |
| 六态状态族 | `--state-success|warning|danger|primary|neutral`（`-bg/-fg/-border`）+ `--state-outline-bg|-border|-fg` |
| 文本 | `--text-1` / `--text-2` / `--text-3` / `--text-placeholder` / `--text-disabled` / `--text-on-dark` / `--text-inverse` |
| 表面 | `--surface-card` / `--surface-page` / `--surface-sub` / `--surface-subtle` / `--surface-sunken` / `--surface-hover` |
| 描边 | `--border-line` / `--border-control` |
| 间距 / 圆角 / 阴影 / 动效 | `--sp-1..10` / `--r-xs..full` / `--e0..e3`（含 `--e3-up`）/ `--dur-fast|base|slow` / `--ease-std` |
| 字号（移动私有） | `--fs-h1-m` / `--fs-h2` / `--fs-h3` / `--fs-body` / `--fs-body-strong` / `--fs-caption` / `--fs-micro` / `--fs-num-lg-boss`(24) / `--fs-num-md`(18) / `--fs-num-sm`(15) |
| 尺寸 / 密度（移动私有） | `--touch-min`(44) / `--row-h-1..tile`(48/64/76/88) / `--tag-h` / `--tag-pad-x` / `--step-dot` / `--step-line` / `--shift-bar-w` / `--navbar-h` / `--actionbar-h` / `--tabbar-h` |
| 安全区 | `--status-bar-height` / `--safe-top` / `--safe-bottom` / `--page-pad-bottom` / `--page-pad-bottom-tab` |
| 渐变 | `--grad-hero`（staff） / `--grad-hero-deep`（boss 经营） |
| 排行 / 图表 / 仪表 | `--rank-1..3-bg` / `--chart-*` / `--gauge-*` |

**引用约束**：① 任何页面/组件出现十六进制色值即视为缺陷；② 浅底块内文字统一 `--text-2`；③ 500 档不承载文字。

## 附录 B：本规范自身检查（设计侧）

- [x] **只改本文件**（v1.0 为新增，v1.1 在同文件内修订）；未改任何 `.vue` / `.js` / `.scss`、未改 `dict.js` / 方案 / 契约 / `db.md`、未执行 git / 部署 / MCP
- [x] **零新增 Token / 零新增色值**；新增状态表达全部由既有语义色族 + 既有 variant 表达
- [x] 未出现任何真实口令 / 密钥；口令一律「由管理员设置 / 重置」交互描述
- [x] 每条规范可判定（文件 / 路由 / 字段 / 文案 / 尺寸 / 状态分支逐条给出）
- [x] 移动端为主：触控 ≥44px、对比度 ≥4.5:1、避免横向滚动
- [x] 未新增角色可见面（新增页面全部 `[ROLE.ADMIN]`）
- [x] 未写入「跑过构建 / 门禁 / 视觉走查」的不实声明（已显式声明未运行）
- [x] 与既有实现/契约的出入**逐条登记**于 ⑫（26 条），只登记不改代码
- [x] **v1.1**：**只改本文件**（`boss-management-ui-design.md`）；未改任何 `.vue` / `.js` / `.scss`、未改 `dict.js` / 方案 / 契约 / `db.md`、未执行 git / 部署 / MCP
- [x] **v1.1**：宫格最终 6 项**项序 + 角标逐项**可判定（②.2 / ②.3）；10 → 6 的**布局取舍**成文（②.2.1）
- [x] **v1.1**：审批中心 **12 条优化**逐条可判定（⑬.3，落点 ⑧.2–⑧.12）；四态含**筛选态空**与**逐类降级**（8.7）
- [x] **v1.1**：**一键通过 / 驳回红线**成文（8.10，含可判定验收：页内无审批动作按钮）
- [x] **v1.1**：新增交互的组合**对比度全部实算 ≥4.5:1**（⑬.4），**零新增色值 / Token**
- [x] **v1.1**：`MessagePage` 与审批中心**分工与差异**成文（⑬.5），计数口径差异（含 / 不含工单）有页脚 `.tip` 交代
- [x] **v1.1**：新发现冲突与超范围项**逐条登记**（C1–C6 / 遗留 L1–L2，⑬.7），**不擅自改代码**
- [x] **v1.2**：**只改本文件**（`boss-management-ui-design.md`，新增章节 ⑭ 并回填 ① / ② / ⑩ / ⑪ / ⑫ / 附录 B）；未改任何 `.vue` / `.js` / `.scss`、未改 `dict.js` / 方案 / 契约 / `db.md`、未执行 git / 部署 / MCP
- [x] **v1.2**：班次管理**入口项序、路由、列表 / 表单字段、控件、校验「阻断 vs 仅提示」、四态**逐条可判定（⑭.1–⑭.6）
- [x] **v1.2**：**显式声明覆盖旧裁定 U-3**（网页端维护 → 移动端可维护），并给出覆盖理由（⑭.0），冲突登记于 ⑫-33
- [x] **v1.2**：**12:00 早晚分界结论成文**（常驻不写「12:00」字面量；改「一早一晚」语义 + 触发式提示；与 `attendance-time-source-design.md` V-3 的关系已交代，⑭.3-(6)）
- [x] **v1.2**：移动端能力**取舍成文**（受控 3 色配色 / 不展示引用状态 / 不自由取色 / 时间 30 分钟粒度，⑭.3、⑭.9）
- [x] **v1.2**：**零新增 Token / 零新增色值 / 零新增具名组件**；新表达全部落在既有语义色族、既有组件与全局工具类（⑭.5、⑪-40）
- [x] **v1.2**：未写入「跑过构建 / 门禁 / 视觉走查」的不实声明（本机未运行，头部验证声明有效）
- [x] **v1.3**：打卡规则页时段由「只读」改「**只读来源 + 可就地改起止**」成文（⑭.10），并与「**班次 = 唯一时间真源**」的一致性论证齐备（未新增时段写入通道；`PUT /rule` 传 `checkPeriods` 仍 400）
- [x] **v1.3**：触发条件「纯只读被推翻 / 新增可写入口」→ 已由 **⑭.0 不变项改写**承接，冲突登记 ⑫-36；`⑪.8 #38` 执行项同步更新
- [x] **v1.3**：交互**零新增具名组件 / Token / 色值**（`van-cell` + `van-popup` + `van-time-picker` 与既有 `ActionBar` / 全局工具类），粒度与 `24:00` 口径与班次表单**同源**
- [x] **v1.3**：时段 ↔ 班次**配对口径**成文并落到**可回归的纯函数**（`apps/boss-h5/src/utils/shiftPeriods.js` + `.spec.js`）：只认启用班次、一行一配、配不到即只读
- [x] **v1.3**：**唯一时间真源不变**；未改 `hrm-admin` / `hrm-server` / 迁移脚本 / `api.md` / `db.md`，未执行 git / 部署 / MCP
