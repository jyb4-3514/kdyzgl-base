# 薪资结算自动化 · 界面设计规范（三端）

> 版本 v1.0 ｜ 日期 2026-09-27 ｜ 角色：UI/UX 设计师（`express-station-ui-ux-designer`）
> 上游依据：`payroll-automation-design.md` **v1.5**（状态机 / §2.8 前端影响面 25 处 / §3.4 手工加扣款 / §4 接口）、`api.md` **v1.4 §4.12**（含 §4.12.16/17/18/22）、既有设计系统 `demo-boss-ui-spec.md`、字典单源 `packages/shared/src/constants/dict.js`。
> 本文**只出设计规范与交付清单，不含实现**；不改任何 `.vue` / `.js` / `.scss`，不改 `dict.js`、`financeStore.js`、方案、契约与 `db.md`。
> **Token 真源声明**：颜色 / 文本 / 表面 / 描边 / 状态族 / 间距 / 圆角 / 动效一律取自 `hrm-clients/packages/tokens/src/tokens.base.scss`（跨端真源）与各端 `styles/tokens.scss`（平台私有层：字号阶梯、`--step-*`、`--tag-h`、`--drawer-w-lg` 等）。**本文不新增任何色值**；凡页面出现十六进制即视为缺陷（`demo-boss-ui-spec.md §3.1`）。
> **验证声明**：本机无 Node/JDK，**未运行构建、未运行门禁、未做视觉走查**；文中对比度数值来自 `demo-boss-ui-spec.md 附录 A` 既有实算（标注来源），推导值实现后须以同法实测确认。

---

## §1 状态可视规范（8 态 × 三端）

### 1.1 状态集合与枚举序（不可演绎，真源 §2.1）

权威枚举顺序（`PayrollStatus.values()`，**末尾追加**）：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED / OBJECTED / PAID`。
`counts` 键序与之一致：既有 6 键相对顺序不变，尾部多 `OBJECTED`、`PAID` 两键。

### 1.2 状态标签文案与配色映射（沿用既有 `variant` 体系）

字典项结构 `{ label, type, variant }`，`label` 必须与后端 `PayrollStatus.label` **逐字一致**。

| 状态 | label（= 后端） | type（色族） | variant（形态） | 色族取值（既有 Token） | 变更 |
| --- | --- | --- | --- | --- | --- |
| `DRAFT` | 草稿 | `info` | `outline` | `--color-info-text` / `--surface-card` | 不变 |
| `PENDING_APPROVAL` | 待审核 | `warning` | `soft` | `--color-warning` / `--color-warning-surface` | 不变 |
| `APPROVED` | 已通过 | `success` | `soft` | `--color-success` / `--color-success-surface` | 不变 |
| `REJECTED` | 已驳回 | `danger` | `soft` | `--color-danger` / `--color-danger-surface` | 不变 |
| `PUBLISHED` | 已发布 | `primary` | `soft` | `--color-primary` / `--color-primary-surface` | 不变 |
| `CONFIRMED` | 已确认 | `success` | `soft` | `--color-success` / `--color-success-surface` | 不变 |
| **`OBJECTED`（新增）** | 异议退回 | `warning` | **`solid`** | **`--color-warning` 实底 + `--text-on-dark` 白字** | **新增映射** |
| **`PAID`（新增）** | 已发放 | `success` | **`outline`** | `--color-success` 文字 + `--surface-card` 底 + `--state-outline-border` 描边 | **新增映射** |

**新增两态的映射理由与被否方案**

- `OBJECTED` → warning 族 + **solid**：
  - 语义 = 「员工异议退回，待管理员重新核定」，属**需立刻处理**的例外态。既有 `solid` 变体定义即「实心：需要立刻行动（高优先级 / 超时）」。
  - 用 `solid` 与 `PENDING_APPROVAL`（warning + soft）在**同色族内区分**，避免与 `PENDING_APPROVAL` 混同；**不新增色值**，仅启用既有变体。
  - **被否方案 ①（warning + soft）**：与 `PENDING_APPROVAL` 标签完全同色同形，无法扫读区分 → 否。
  - **被否方案 ②（danger + soft）**：与 `REJECTED` 同色同形，违反 U-02「异议退回与管理员驳回必须可区分（谁退的）」 → 否。
  - **被否方案 ③（新增第 4 种语义色）**：违反「能复用的绝不再造」与跨端真源冻结（改 `tokens.base.scss` 影响 PC，超范围） → 否。
- `PAID` → success 族 + **outline**：
  - 语义 = 「已发放且归档冻结」终态，**无动作诉求**。既有规范「`outline` = 终态无动作诉求」（`dict.js` 顶部注释、`LEAVE_STATUS` 同口径）。
  - success 族表「发放完成」的正面收口，与 `CONFIRMED`（success + soft）**同族异形**，靠 variant 与标签文案区分（既有 `APPROVED` / `CONFIRMED` 同族先例）。
  - **被否方案（info + outline）**：中性色无法表达「钱已发」的完成语义，与草稿/已作废的描边色撞形 → 否。

**StatusTag 落地要求（两处实现都要改，见 §11 #3）**

- `packages/shared/src/ui/StatusTag.vue` 的 `DICT_COLORS[PAYROLL_STATUS]` 需补 `OBJECTED: { surface: 'var(--color-warning)', text: 'var(--text-on-dark)' }`；不补则 `solid` 变体回落淡底，与 soft 无异。
- `apps/web/src/components/StatusTag.vue` 为**独立实现**（web 详情/列表引用的是它，非 shared 版），**同款改动须两处同步**，否则三端标签不一致。
- `PAID` 只靠字典项即可（`outline` + success 文字），无需改 `DICT_COLORS`。

### 1.3 员工端可见性差异

| 状态 | 员工端（staff-h5）可见 | 说明 |
| --- | --- | --- |
| `PUBLISHED` | ✅ 可见（列表 + 详情） | 待本人确认 / 可提异议 |
| `CONFIRMED` | ✅ 可见 | 已确认 |
| **`PAID`** | ✅ **可见（新增）** | 归档态对本人可见；否则员工确认后单据从列表消失，反直觉（C-6 / U-08） |
| `DRAFT` / `PENDING_APPROVAL` / `APPROVED` / `REJECTED` / `OBJECTED` | ❌ 不可见 | **内部态，一律不展示**；员工端不渲染对应标签、不做「处理中」标签（避免暴露内部处理态） |

**`OBJECTED` 期间（异议退回）员工端文案处理**——后端 `my` 列表与详情均不含/拒绝 `OBJECTED`（详情回 `9403`），故员工端**不新增状态**，只处理「单据消失」的困惑：

1. staff-h5 详情 `9403` 拦截文案统一改为：**「工资单尚未发布或正在重新核定中，暂不可查看」**（替换现有「工资单尚未发布，暂不可查看」，覆盖「已提异议退回」场景）。
2. staff-h5 列表空态补充说明：**「如已提出异议，管理员重新核定并再次发布后即可查看」**。
3. 员工提交异议成功后：`showSuccessToast('已提交异议，等待管理员重新核定')` + **立即 `router.replace` 回列表**，避免停留在不可达详情页（现有行为保留，文案对齐）。
4. **不新增「异议处理中」列表项**（无数据来源，避免前端伪造状态）。

### 1.4 组件复用口径

- 标签一律用 `StatusTag`（移动端用 `@kdyzgl/shared/ui/StatusTag.vue`，web 用 `apps/web/src/components/StatusTag.vue`），**禁止**页面内自绘状态色块。
- 状态**不只靠颜色**（SC 1.4.1）：标签本身带 label 文案即为文字通道；列表/详情出现新增态时，不得仅以颜色变化表达。
- 计数徽标（`counts`）按枚举序渲染，`OBJECTED` / `PAID` 追加在尾部，不得插入中间。

---

## §2 驿站精灵算薪日设置页（I-1 / I-2 / I-3 / I-9）

端与准入：`boss-h5`（驿站精灵，fail-closed 仅 `ADMIN`），路由新增 `/boss/payroll-settings`（列表）与 `/boss/payroll-settings/:stationId`（编辑 + 历史）。先例参照 `leaveSettings.vue`（草稿 + 二次确认 + ActionBar 保存）。

### 2.1 页面结构（列表页）

- `PageNav` 标题：**算薪日设置**。
- 顶部提示条（`van-notice-bar`，可关闭）：**「启用后系统在每驿站各自的算薪日自动生成工资单草稿并提交审核；未启用的驿站不自动跑数。」**（`enabled` 默认 0 的安全语义必须前置说明）。
- 驿站列表：一驿一卡（`list-item--rich` 形态），卡内：
  - 主行：驿站名 + 右侧**启用状态胶囊**（启用=`success`/soft「已启用」；停用=`info`/outline「未启用」）。
  - 副行（`--text-3`，tabular-nums）：**「每月 {payrollDay} 日 {payrollTime}」**；未配置时显示 **「尚未配置」**（对应 `9406`）。
  - 右侧箭头进入编辑页。
- 筛选：驿站关键字（可空）；`enabled` 过滤（全部 / 已启用 / 未启用）。
- 分页：`van-list` 上拉加载，`pageSize=20`。
- 空态（`PageState` empty）：**「还没有可配置的驿站」** + 引导文案「请先在 PC 端维护驿站」。
- 加载态：`PageState` 骨架（`rows=3`），200ms 防闪。
- 错误态：`PageState` error + 「重新加载」（≥44px）+ `role="alert"`。

### 2.2 编辑页（I-2 读 / I-3 写）

页面结构（区块用 `section-title` + `card`）：

| 区块 | 字段 | 控件 | 默认 / 约束 |
| --- | --- | --- | --- |
| 自动算薪 | 启用自动算薪 | `van-switch` | 默认**关**（=0）；`0→1` 走二次确认 |
| 算薪设置 | 算薪日 | 数字输入 + `van-picker`（1–31） | 默认 1；**范围 1–31**（U-05） |
| | 执行时间 | `van-time-picker`（HH:mm） | 默认 `09:00`；墙钟 `Asia/Shanghai` |
| 通知 | 生成后推送管理员 | `van-switch` | 默认**开**（=1） |
| 备注 | 备注 | `van-field` textarea | 可空，≤255 字 |
| 变更历史 | 时间线（I-9） | 只读列表 | 见 2.4 |

- **月末钳位提示**（算薪日 > 当月天数时）：字段下方常驻辅助文案 **「当月无该日时，自动取当月最后一天（如 31 → 4 月 30 日）」**（`--text-3`）。
- 编辑交互（**沿用 leaveSettings 先例**）：
  1. 表单值写本地 draft，`dirty = draft !== saved`；ActionBar `note` 显示 **「有改动，点『保存设置』后生效」** / **「当前配置已保存」**。
  2. **只有「启用开关」的改动**先弹二次确认（`van-dialog`，动词标题「启用自动算薪」/「停用自动算薪」+ 影响说明 + 按钮「确认启用」/「再想想」），确认后落 draft — 理由：开关直接决定是否自动跑数（leaveSettings 同类先例）。
  3. 其余字段改动不弹确认，保存时统一提交。
  4. 底部 `ActionBar`：主按钮 **「保存设置」**（`disabled = !dirty || saving`），`note` 见上。
- **保存成功**：`showSuccessToast('算薪设置已保存')` → 就地刷新配置（不整页骨架）→ **刷新变更历史**（I-9）。
- 保存失败：按错误码就地提示（见 2.3）。

### 2.3 表单校验与错误文案（对应 9406 / 9407 / 9408）

| 触发 | 错误码 | 展示位置 | 文案（可判定） |
| --- | --- | --- | --- |
| 该驿站尚无配置（进入编辑页调 I-2） | `9406` | 页面级 | **「该驿站尚未配置算薪设置，保存一次即可创建」**；表单以**默认值**（启用=关、算薪日=1、时间=09:00、推送=开）呈现，**不判为错误态** |
| 算薪日非 1–31 整数 | `9407` | 算薪日字段下 `role="alert"` | **「算薪日须为 1–31 的整数，月末自动钳位到当月最后一天」** |
| 时间非 HH:mm | `9408` | 时间字段下 `role="alert"` | **「时间格式须为 HH:mm（如 09:00）」** |
| 备注超 255 | `400` | 备注字段下 | **「备注不可超过 255 字」** |

- 校验策略：**失焦即校验 + 提交前整表校验**；错误文案与后端 `message` 同义（文案以上表为准，不直接透传英文/堆栈）。
- 保存时后端返回 `9407`/`9408`：把字段滚动到可视区并聚焦该字段。

### 2.4 配置变更历史（I-9）

- 入口：编辑页「变更历史」区块，调 `GET /payroll-settings/{stationId}/logs`（ADMIN），按 `time` 倒序分页。
- 每条时间线行（复用纵向步骤条 / 列表形态）：

| 列 | 内容 |
| --- | --- |
| 动作 | `CREATE`=创建 / `UPDATE`=修改 / **`ENABLE`=启用** / `DISABLE`=停用 |
| 操作人 | `operatorName`（+ `operatorRole`） |
| 时间 | `time`（tabular-nums） |
| 变更内容 | 由 `before → after` 白名单键生成，逐字段一行：**`算薪日 1 → 31`**、**`执行时间 09:00 → 18:00`**、`启用 关 → 开`、`推送 开 → 关`、`备注 A → B`；`CREATE` 无 `before`，只列 `after` 初值 |
| 备注 | `remark` |

- **`ENABLE` 动作必须醒目标识（M-9 硬要求）**：动作标签用 `warning` / `solid` 胶囊 + 文案**「启用自动算薪」**，且该行上方无折叠（首屏可见）；不得仅靠文字颜色表达。
- 空态：**「暂无变更记录」**；加载态骨架；错误态 `PageState` + 重试。

---

## §3 自动算薪运行记录 + 手工触发（I-4 / I-5）

端：`web`（PC 管理端，ADMIN）为主承载；本轮不要求 boss-h5 承载运行记录。

### 3.1 列表字段与筛选（I-5）

Tab / 子页：**「自动算薪运行」**（挂 `web` 财务管理域）。

| 列 | 字段 | 展示 |
| --- | --- | --- |
| 驿站 | `stationId` → 站名 | 文本 |
| 账期 | `targetMonth` | `yyyy-MM` |
| 尝试日 | `attemptDate` | 日期（**日粒度**：每自然日一行） |
| 触发方式 | `triggerType` | `AUTO`=定时 / `CATCH_UP`=补跑 / `MANUAL`=手工 |
| 应执行时刻 | `dueAt` | 日期时间 |
| 结果 | `status` | 胶囊（见 3.3） |
| 跳过原因 | `skipCode` + `skipReason` | 机器码转中文 + 人类可读原因 |
| 生成数 | `generatedCount` | 数字，空显示 `—` |
| 起止时间 | `startTime` / `finishTime` | `finishTime` 为空 → **「进行中」**；tabular-nums |
| 失败原因 | `failReason` | 截断 tooltip；`STALE_RECLAIMED` 特殊渲染（见 3.3） |

- 筛选：驿站、账期（month）、结果（status）、触发方式（triggerType）、分页（`pageSize` 20/50/100）。
- 空态：**「当前条件下没有运行记录」**；错误态 `StateBlock` + 重试。

### 3.2 手工触发交互（I-4）

- 入口：运行记录页右上 **「手工触发算薪」** 主按钮（ADMIN）。
- 弹窗（`el-dialog` / `el-form`）：
  - 驿站（必选，`el-select`）
  - 账期（必选，`el-date-picker type=month`，`YYYY-MM`）
  - **无 `force` 开关**（v1.5 已删除该参数，界面不得出现「强制重跑」选项）
  - 底部：**「确认触发」**（loading 态）+「再想想」
- **前置提示（避免「点了才知道不行」）**：弹窗内常驻说明 **「同一驿站同一账期每自然日至多触发一次；已成功、已占位或今日已尝试过都会拒绝。」**
- 提交结果处置（**统一口径，无一静默**）：

| 后端返回 | 界面反馈 |
| --- | --- |
| `runId` + `status=SUCCESS` / 有 `generatedCount` | `ElMessage.success('已触发，生成 {generatedCount} 张工资单')` |
| `status=SKIPPED` + 原因 | `ElMessage.warning('已触发，但被业务拦截：{原因}')`（`9405` 映射为 SKIPPED，**不当错误**） |
| `9410`（正在运行 / 已占位 / **当日已尝试**） | `ElMessage.warning('该驿站该账期正在运行、已占位或今日已尝试过，不能重复触发；可查看运行记录，明日再试')` |
| `9415`（未启用自动算薪） | `ElMessage.warning('该驿站未启用自动算薪，请先在『算薪日设置』中启用')` |
| `4001`（驿站不存在） | `ElMessage.error('驿站不存在，请刷新驿站列表')` |

- 成功后**就地刷新**运行记录列表（新记录置顶，`status=RUNNING` 或终态）。

### 3.3 结果与「僵死回收」展示口径

结果胶囊（web 端新增展示字典 `PAYROLL_RUN_STATUS`，见 §11）：

| `status` | 文案 | 色族 / 形态 |
| --- | --- | --- |
| `RUNNING` | 进行中 | `primary` / `soft` |
| `SUCCESS` | 成功 | `success` / `soft` |
| `FAILED` | 失败 | `danger` / `soft` |
| `SKIPPED` | 已跳过 | `info` / `outline`（正常拦截、无动作诉求） |

`skipCode` 转中文（新增 `PAYROLL_SKIP_CODE`）：

| `skipCode` | 文案 |
| --- | --- |
| `BLOCKED_9405` | 该账期已存在非可覆盖工资单 |
| `CONFIG_INVALID` | 算薪配置非法 |
| `DRAFT_PROTECTED` | 存在草稿单，保护性跳过 |

**僵死回收（`failReason='STALE_RECLAIMED'`）展示口径**：

- 结果列显示 **失败**，并在失败原因处渲染 **「执行超时，已自动回收并释放占位」** 胶囊（`warning` / `outline`）+ tooltip **「回收后将于次日按日粒度闸门重试」**。
- **禁止**原样展示 `STALE_RECLAIMED` 机器码（须转人类可读）。
- 失败原因**不得**出现 SQL 原文 / 凭据 / 业务数据（服务端已截断；前端不拼接）。
- 连续失败告警（通知类型 10）不在本页展示，但记录行可给一条轻提示入口（可选）。

---

## §4 手工加款 / 扣款表单（I-6）

入口：**工资单详情页 / 审核页**（web `PayrollDetailDrawer`；boss-h5 `payrollDetail`）。

### 4.1 入口与可用性

- 入口按钮 **「加款 / 扣款」**（`primary` / plain），仅当单据状态 ∈ `isItemEditable`（`DRAFT` / `REJECTED` / **`PENDING_APPROVAL`** / **`OBJECTED`**）时可点。
- **不可用态（禁用 vs 隐藏，遵循 `demo-boss-ui-spec §6.5`）**：用户**有权限但状态不满足** → **禁用 + 原因**（不隐藏）：

| 当前状态 | 入口表现 | 原因文案（note / tooltip） |
| --- | --- | --- |
| `APPROVED` | 禁用 | 「已通过待发布，不可再调整金额」 |
| `PUBLISHED` | 禁用 | 「已发布给员工，不可再调整金额；如需调整请先由员工提异议退回」 |
| `CONFIRMED` | 禁用 | 「员工已确认，不可再调整金额」 |
| `PAID` | 禁用 | **「该工资单已发放归档，不可修改」**（9413 同义） |

- 服务端状态变更（如被他人审核）导致 `9403` → **刷新详情 + 提示「该单状态已变化，请刷新后重试」**，不静默。

### 4.2 表单字段与校验（I-6 入参）

| 字段 | 控件 | 约束 | 错误文案 |
| --- | --- | --- | --- |
| 方向 | 分段控件 / 单选：**加款（`ADDITION`）/ 扣款（`DEDUCTION`）** | 必选，默认加款 | — |
| 名称 | `van-field` / `el-input` | 必填，建议 2–20 字 | 「请填写名称（2–20 字）」 |
| 金额 | 数字输入（`inputmode=decimal`） | **必填且 > 0**，最多 2 位小数 | 「金额须为大于 0 的数字」 |
| **事由** | textarea，`maxlength=200` + `show-word-limit` | **必填 2–200 字** | 「加扣款事由必填（2–200 字）」（**`9412`**） |
| 补充说明（可选） | textarea | 可空（映射 `detail`，不覆盖事由全文） | — |

- **方向语义前置说明**（避免口径误解，对齐契约「实发 = 应发 − 扣项」）：表单底部常驻 **「加款计入应发合计，扣款计入扣项合计；保存后自动重算四项合计。」**
- `9411`（`item_key` 重复）：服务端生成 key，正常不出现；若返回 → `ElMessage.error('该明细已存在，请刷新后重试')`（不暴露 key）。

### 4.3 提交后反馈（合计重算如何呈现）

1. 提交成功：`ElMessage.success('已添加并重算合计')`（移动端 `showSuccessToast`）。
2. **就地面更新，不整页重刷**：以返回的 `PayrollVO` 更新四项合计（`grossAmount` / `deductionTotal` / `netAmount` / `additionTotal`）。
3. **金额变化对比呈现**（本次新增 vs 原值）：在合计区对**发生变化**的数字给一次性高亮（`--color-primary-surface` 底，≤1.2s 淡出）+ 变化角标 **`+120.00`** / **`-120.00`**（正负双通道：符号 + 颜色，SC 1.4.1）。
4. 明细表插入新行（`source=MANUAL`），并按 `sort_order` 保持稳定次序；新行可加一次性「新增」淡入。

---

## §5 审核时可修改金额 + 修改痕迹（C-3 + I-7）

### 5.1 可编辑区域（C-3）

- 审核页 / 详情抽屉中，**仅 `source=MANUAL` 的明细行**在 `isItemEditable` 状态下渲染为金额输入框；规则项（`FIXED`/`ATTENDANCE`/`KPI`）恒为只读文本。
- **可编辑状态扩展**：`DRAFT` / `REJECTED` / **`PENDING_APPROVAL`** / **`OBJECTED`**（新增后两态）。
- 保存按钮 **「保存人工项」** 仅在存在改动时可用；保存前**必须**弹「修改事由」输入（见 5.2）。
- 只读态提示（沿用既有文案，状态随之扩展）：**「当前状态（{statusLabel}）不允许修改金额」**。

### 5.2 必填事由（`9412`）交互

- 点「保存人工项」→ 弹层要求填写 **事由（必填 2–200）**（`textarea` + `show-word-limit=200`）。
- 事由为空或超界 → 字段下 `role="alert"` 报错 **「金额变更事由必填（2–200 字）」**（9412），阻止提交。
- 事由提交后由服务端写 `payroll_log.reason`；**`ITEM_UPDATE` 不得覆盖 `payroll_item.detail` 中的事由全文**（既有实现会覆盖为「人工填写」，属缺陷，见 §12 冲突 8）。

### 5.3 修改痕迹时间线（I-7）

入口：详情页「操作留痕」区块（web 抽屉内 / 移动端详情新增区块）。调 `GET /payrolls/{id}/logs`。

**字段按角色裁剪（以后端出参为准，前端不做二次裁剪）**：

| 字段 | ADMIN | 非 ADMIN（本人单 + 已发布及之后） |
| --- | --- | --- |
| `action` | ✅ | ✅ |
| `operatorName` / `operatorRole` | ✅ | ❌ |
| `time` | ✅ | ✅ |
| `fromStatus` / `toStatus` | ✅ | 仅 `toStatus` |
| `reason` | ✅ | ✅ |
| `before` / `after`（金额快照） | ✅ | ❌（后端不返回） |

- 时间线条目渲染（纵向时间线 / 步骤条复用）：

| 展示项 | ADMIN 渲染 | 员工端渲染 |
| --- | --- | --- |
| 动作 | `PAYROLL_LOG_ACTION` 转中文（新增映射，见 §11） | 同 |
| 操作人 | 姓名 + 角色胶囊 | **不渲染** |
| 时间 | `time` | 同 |
| 状态变化 | **「待审核 → 已通过」**（`fromStatus → toStatus`） | 仅 `toStatus` |
| 事由 | `reason` | 同 |
| **金额前后对比** | 见下 | **不渲染**（字段缺失） |

- **金额前后对比（仅 ADMIN）**：
  - 明细级：对 `before`/`after` 白名单 `{itemKey,itemType,itemName,amount}` 逐项配对，输出 **`{itemName}：{before.amount} → {after.amount}`**；仅金额变化的项高亮。
  - 合计级：输出 **`实发：{before.netAmount} → {after.netAmount}`**（另有 `additionTotal`/`deductionTotal`/`grossAmount` 变化时同列）。
  - `before` 或 `after` 为空 → **不渲染该快照区块**（不显示空值、不显示「—」，避免噪声）。
- **员工端（staff-h5）**：只显示 `action` / `time` / `reason` / `toStatus` 的文本叙述，**不显示任何人名、金额快照**（与服务端裁剪口径一致；前端隐藏而后端照返属信息泄漏，禁止）。
- 空态：**「暂无操作留痕」**；加载骨架；错误态 `PageState` + 重试。

---

## §6 发放归档（I-8）

### 6.1 `CONFIRMED` → 管理员「确认工资已发放」交互

- 入口：`CONFIRMED` 状态下，web 行内 + 详情抽屉、boss-h5 详情 `ActionBar` 显示 **「确认工资已发放」**（主按钮）。
- **二次确认必要性：必须**（不可逆终态）。确认弹窗四要素（沿用 `BossConfirm` / `el-messagebox` 口径）：

| 要素 | 文案 |
| --- | --- |
| 动词标题 | **确认工资已发放** |
| 对象 | **{员工姓名} · {账期} · 实发 {netAmount} 元** |
| 影响面 | **发放后单据进入「已发放」并归档冻结，员工可见该归档态** |
| 不可逆声明 | **本期不支持撤销 / 冲正，确认后不可修改** |
| 按钮 | **「确认已发放」** / 「再想想」（**禁止**「确定 / 取消」） |

- 成功：`showSuccessToast('已标记发放，工资单已归档')` / `ElMessage.success(...)`，状态就地变为 `PAID`。
- 失败：`9403`（来源非 `CONFIRMED`）→ **「该单当前状态不允许发放，请刷新后查看」**；`9413`（已发放）→ **「该工资单已发放归档，不可重复操作」**。

### 6.2 `PAID` 归档态的「冻结可视」

- 标签：`已发放`（success / outline，见 §1.2）。
- **列表**：该行保持可读，操作列不再出现任何写动作按钮；`PAID` 行不参与批量发布/批量提交（批量候选计数不含之）。
- **详情**：金额区顶部给一条常驻只读声明（`van-notice-bar` / `el-alert type=info`）：
  **「该工资单已发放并归档，不可修改」** + 归档信息（`paidTime` / `paidByName`）。
- **所有写入口在 `PAID` 下禁用**（改金额 / 加扣款 / 提交 / 审核 / 发布 / 异议），并由服务端 `9413` 兜底（§2.9 `assertMutable`）。前端禁用仅为体验拦截，不作为安全边界。

### 6.3 误操作防护

- 前述二次确认 + 「本期不支持撤销」声明（因 T5 无冲正出口，必须让用户在确认前知晓）。
- **文案区分**：`9413`（已发放归档，不可修改）与 `9403`（当前状态不允许该操作）**不得混用**；`9413` 一律说「已发放归档」，`9403` 一律说「当前状态不允许」。

---

## §7 异议与再发布（C-1 / C-2）

### 7.1 员工端异议入口与理由输入（C-1）

- 入口：`PUBLISHED` 详情 `ActionBar` 显示 **「确认无误」**（主）+ **「提异议」**（danger）。
- 异议弹窗（`van-popup` bottom）：
  - 标题 **「提交异议」**；说明 **「提交后单据退回管理员重新核定，重新发布前你将暂时看不到该单」**。
  - 理由 `textarea` **必填 2–200 字**（`maxlength=200` + word-limit）；越界 `role="alert"`：**「异议原因须为 2–200 字」**。
  - 主按钮 **「确认提交异议」**；成功 → `showSuccessToast('已提交异议，等待管理员重新核定')` + 回列表（§1.3-3）。
- 失败：`9403` → **「仅已发布的工资单可提异议，请刷新后查看」**；`9404` → **「只能对本人的工资单提异议」**。

### 7.2 管理员端 `OBJECTED` 待办呈现

- **boss-h5 待办组**：`payrolls` 组当前固定查 `status='PENDING_APPROVAL'`（`todoGroups.js:25`）；**新增一个异议待办组**（key 建议 `payrollObjections`），`title='待处理工资单异议'`，`to='/boss/payroll'`，`params:{ status:'OBJECTED', pageNum:1, pageSize:3 }`。**不得**把异议并进 `PENDING_APPROVAL`（否则 C-1 落 `OBJECTED` 后异议单不再进待办）。
- **web 异议面板**：`usePayrollObjections.js` 查询状态由 `PENDING_APPROVAL` 改 **`OBJECTED`**，并**去掉** `filter(row => !!row.objectionReason)` 的本地过滤（`OBJECTED` 本身即异议态）。
- 异议行展示：员工 / 账期 / **异议原因** / 提出时间 / 状态胶囊（`异议退回`，warning/solid）。操作列 **「处理」** → 复用工资单详情抽屉（不另造异议详情页）。

### 7.3 再发布（C-2）

- `OBJECTED` 详情显示 **「重新发布」**（主动作），允许**先改金额**（C-3，`isItemEditable` 含 `OBJECTED`）再发布。
- 发布弹窗：**「确认重新发布」**，说明 **「将重新发布给 {员工}（{账期}），发布后员工可见并需重新确认，不可撤回」**。
- 调用 `POST /payrolls/publish`，`ids` 路径**允许来源 `APPROVED`（首发）或 `OBJECTED`（再发布）**。
- 成功：状态 → `PUBLISHED`（记新 `publisher`/`publishTime`，清 `objection_*`），`showSuccessToast('已重新发布')`；列表/详情就地刷新。
- **状态提示**：`OBJECTED` 在管理员列表保持 warning/solid 标签 + 待办计数；发布后标签转 `已发布`。
- **`skipped`/错误区分**：`ids` 路径遇非允许来源（含 `PAID`/`CONFIRMED`）**必须显式报错**（不静默计入 `skipped`）；`month` 批量路径**仅 `APPROVED`**，界面须说明「批量发布不含异议退回单，请逐单重新发布」。

---

## §8 手工调整对账视图（I-10）

端：`web`（财务管理域新增 Tab / 子页 **「手工调整对账」**）。

### 8.1 页面结构

- 筛选（内联表单）：**账期 `month`（必填，`el-date-picker type=month`）**、**驿站 `stationId`（可选，留空=全部）**、「查询」。
  - `month` 缺失/格式非法 → `400`：**「请选择账期月份」**（前置禁用查询按钮 + 提示，避免「点了才知道」）。
- 汇总表（`el-table`）：

| 列 | 字段 | 对齐 | 说明 |
| --- | --- | --- | --- |
| 员工 | `employeeName` | 左 | tooltip 溢出 |
| 加款笔数 | `additionCount` | 右 | tabular-nums |
| 加款总额 | `additionTotal` | 右 | 金额，`+` 号 |
| 扣款笔数 | `deductionCount` | 右 | tabular-nums |
| 扣款总额 | `deductionTotal` | 右 | 金额，`-` 号 |
| **净影响** | `netImpact` | 右 | **正负双通道**：正 `+`（`--color-success`）/ 负 `-`（`--color-danger`）；**不得仅靠颜色** |
| 操作 | 下钻入口 | — | 见 8.2 |

- **合计行**：末行固定 `employeeName='合计'`（`employeeId=null`），加粗 + `--surface-sub` 底；合计行**不可下钻**。
- 空态（该站无员工 / 无数据）：**「{账期} 无手工加扣款记录」**；错误态 `StateBlock` + 重试。
- 金额为 0 时照常显示 `0.00`（0 是结论，不是缺失 —— 遵循 `demo-boss-ui-spec §5.3`）。

### 8.2 下钻到留痕明细

- 点某员工行的「明细」→ 打开该员工**该账期**的加扣款留痕（`ITEM_ADD` 条目）。
- **口径约束（登记 gap，见 §12 冲突 15）**：I-10 汇总真源是 `payroll_log` 的 `employee_id + month`；而现有 I-7 以 `payroll_id` 为键。`generate` 覆盖重建可能已物理删除旧单，按 `payroll_id` 下钻会漏「已删单」上的留痕。
  - **本轮实现建议（不改契约前提下）**：下钻先按 `employee + month` 取工资单列表拿 `id` → 调 I-7；若取不到（单据已被覆盖重建）→ 展示降级文案 **「该员工当月单据已被重建覆盖，历史留痕暂不可下钻」** 并登记 `TODO(扩展)`。
  - **建议契约补充（登记，不代改）**：新增「按 `employeeId + month` 查 `ITEM_ADD` 留痕」的查询出口，使对账与留痕口径一致。

### 8.3 导出 / 打印建议

- **本轮建议先提供「打印」（`window.print` 打印友好样式）与「复制表格」**，不硬做导出接口（契约无导出端点；`PayrollTabPanel.vue:135` 已有导出 `TODO(扩展)`）。
- 若后续补导出（CSV/Excel），列定义（与表一致，含合计行）：`员工 / 加款笔数 / 加款总额 / 扣款笔数 / 扣款总额 / 净影响`；表头追加 `账期 / 驿站` 上下文行；文件名 **`手工调整对账_{month}{_stationName?}.csv`**；导出须**仅 ADMIN**，且不得包含明细级事由之外的敏感字段。

---

## §9 响应式与可访问性

### 9.1 移动端（boss-h5 / staff-h5）

- **断点**：沿用 `demo-boss-ui-spec §2.2` 断点表（320 / 360 / 375 / 390 / 414 / 430 / 480 / 640 横屏），内容列 `--content-max = min(480px, 视口宽)`，横屏 `min(640px, 视口宽)`，横屏仍单列。**本规范不新增 max-width 档位。**
- **触控目标 ≥ 44×44px**：新增可点元素（设置项行、`fchip` 筛选、`ActionBar` 按钮、提异议/确认按钮、展开明细）一律 `min-height:44px`；小图标按钮须有 ≥44px 命中区（用 `padding` 或伪元素扩展）。
- **对比度 ≥ 4.5:1**：
  - 正文/辅助文字用 `--text-1/--text-2/--text-3`（对白卡 14.679 / 7.557 / 4.834:1）。
  - **浅底块内文字一律 `--text-2`**（`--text-3` 对浅灰底仅 4.23:1，禁用 —— 沿用 `leaveSettings.vue:171` 明文禁令）。
  - 新增标签：`OBJECTED` 白字于 `--color-warning`（5.022:1 ✓）、`PAID` `--color-success` 于白卡（5.585:1 ✓），见附录 A。
- **Picker 交互**：算薪日 `van-picker` / 时间 `van-time-picker` 用 `van-popup round position=bottom safe-area-inset-bottom`；确认/取消按钮 ≥44px；`aria-label` 必填；数值用 `.tabular-nums`。
- **状态不只靠颜色**：异议/归档等状态均带文字；金额正负带 `+/-` 与颜色双通道。
- **动效**：过渡 ≤200–300ms，全局 `prefers-reduced-motion` 降级已由真源承接。

### 9.2 桌面端（web）

- 布局：筛选卡 + 内容卡 + `el-table`；表格列宽固定关键列（金额右对齐 `tabular-nums`）。
- 断点行为（关键容器）：

| 容器 | 断点 | 行为 |
| --- | --- | --- |
| 设置表单 | ≥1200 | 双列栅格（字段两两一行） |
| | ≥992 ~ <1200 | 双列 |
| | <992 | 单列堆叠，标签在上 |
| 运行记录表 | <1200 | 保留关键列（驿站/账期/触发/结果/生成数），其余列 `show-overflow-tooltip` + 横向滚动 |
| 对账表 | <1200 | 保留员工/净影响，其余可横向滚动 |
| 留痕时间线 | ≥992 | 双栏（左动作/时间，右对比）；<992 单列堆叠 |
| 抽屉（详情） | — | `size="min(var(--drawer-w-lg), 92vw)"`（沿用既有） |

- 键盘可达：所有可点元素可用 `Tab` 聚焦，`Enter`/`Space` 触发；状态变化用 `aria-live="polite"`（合计重算、触发结果）。
- 图标承载语义时须 ≥3:1（SC 1.4.11）或配文字（`--color-warning-icon` 白底仅 1.900:1，禁止单独承载语义）。

### 9.3 三态与反馈（三端一致口径）

- **加载**：≤200ms 不显示骨架（防闪）；>200ms 显示等高骨架；行内操作用按钮 `loading`。
- **空态**：图标 + 文案 +（必要时）出口动作；**空态与错误态文案必须分开**。
- **错误态**：`role="alert"` + 原因 + 下一步；提供「重新加载」；**禁止静默失败**。
- **成功**：`showSuccessToast` / `ElMessage.success` + **就地更新**（不整页骨架重刷），文案含数量（「已发布 12 张工资单」）。

---

## §10 状态 — 动作 — 页面 对照表（8 态 × 3 端）

> 端口径：`boss-h5`=驿站精灵（ADMIN）；`web`=PC 管理端（ADMIN）；`staff-h5`=驿站助手（STAFF/STATION_ADMIN，本人）。
> 动作集以服务端 `actions[]`（§4.12.4 出参）为唯一真源，前端**不维护第二份状态机**。金额类动作（加款/改金额）以 `actions` 之外的可编辑标志/状态判断为准（`isItemEditable`）。
> 表共 **24 行**（8 态 × 3 端）。

| 状态 | 端 | 标签（variant） | 可见 | 可见动作按钮 | 点击→接口 | 成功后状态与 UI 变化 |
| --- | --- | --- | --- | --- | --- | --- |
| `DRAFT` | boss-h5 | 草稿(outline) | ✅ | 提交审核 / 加扣款 / 改金额 | `submit`(POST /payrolls/submit) · `I-6` · C-3 | → `PENDING_APPROVAL`；刷新列表/详情，标签变「待审核」 |
| `DRAFT` | web | 草稿(outline) | ✅ | 提交审核 / 加扣款 / 改金额 | 同上 | 同上 |
| `DRAFT` | staff-h5 | — | ❌ | 不展示 | — | 员工端不可见 |
| `PENDING_APPROVAL` | boss-h5 | 待审核(soft) | ✅ | 审核通过 / 驳回 / **加扣款 / 改金额(Q6)** | C-5 · `I-6` · C-3 | 通过→`APPROVED`；驳回→`REJECTED`；改金额→就地重算合计 |
| `PENDING_APPROVAL` | web | 待审核(soft) | ✅ | 同上（+ 批量提交/发布） | 同上 | 同上 |
| `PENDING_APPROVAL` | staff-h5 | — | ❌ | 不展示 | — | 员工端不可见 |
| `APPROVED` | boss-h5 | 已通过(soft) | ✅ | 发布给员工 | C-2(ids) | → `PUBLISHED`；员工立即可见（通知 8） |
| `APPROVED` | web | 已通过(soft) | ✅ | 发布 / 批量发布(month,仅 APPROVED) | C-2 | 同上；批量跳过数提示 |
| `APPROVED` | staff-h5 | — | ❌ | 不展示 | — | 员工端不可见 |
| `REJECTED` | boss-h5 | 已驳回(soft) | ✅ | 重新提交 / 加扣款 / 改金额 | `submit` · `I-6` · C-3 | → `PENDING_APPROVAL` |
| `REJECTED` | web | 已驳回(soft) | ✅ | 同上 | 同上 | 同上 |
| `REJECTED` | staff-h5 | — | ❌ | 不展示 | — | 员工端不可见 |
| `PUBLISHED` | boss-h5 | 已发布(soft) | ✅ | **无（只读等待）** | — | 等待员工确认或提异议 |
| `PUBLISHED` | web | 已发布(soft) | ✅ | **无（只读）** | — | 同上 |
| `PUBLISHED` | staff-h5 | 已发布(soft) | ✅ | **确认无误 / 提异议** | C-4 · C-1 | 确认→`CONFIRMED`；异议→`OBJECTED`（本人端单据消失，回列表） |
| `CONFIRMED` | boss-h5 | 已确认(soft) | ✅ | **确认工资已发放** | `I-8` | → `PAID`；标签变「已发放」；二次确认通过后归档 |
| `CONFIRMED` | web | 已确认(soft) | ✅ | 确认工资已发放 | `I-8` | 同上 |
| `CONFIRMED` | staff-h5 | 已确认(soft) | ✅ | **无（只读）** | — | 等待管理员确认发放 |
| `OBJECTED` | boss-h5 | 异议退回(solid) | ✅ | 重新发布 / 加扣款 / 改金额 /（可选）提交审核 | C-2 · `I-6` · C-3 · `submit` | 重新发布→`PUBLISHED`（清异议，记新发布人，通知 8）；提交→`PENDING_APPROVAL` |
| `OBJECTED` | web | 异议退回(solid) | ✅ | 同上（异议面板「处理」入口） | 同上 | 同上 |
| `OBJECTED` | staff-h5 | — | ❌ | 不展示（内部态） | — | 员工端不可见；详情 `9403` 文案见 §1.3 |
| `PAID` | boss-h5 | 已发放(outline) | ✅ | **无（终态冻结，全写入口禁用）** | — | 只读归档；声明「已发放归档，不可修改」 |
| `PAID` | web | 已发放(outline) | ✅ | 无（终态冻结） | — | 同上；任何写操作回 `9413` |
| `PAID` | staff-h5 | 已发放(outline) | ✅ | 无（只读） | — | 归档可见，不显示确认/异议入口 |

---

## §11 交付前端工程师落地清单

### 11.1 方案 §2.8 的 25 处影响点（逐条：改成什么）

| # | 类别 | 位置 | 改成什么（文案 / 分支条件 / 字典项） |
| --- | --- | --- | --- |
| 1 | 字典单源 | `dict.js:151-158` | `PAYROLL_STATUS` 追加 `OBJECTED:{label:'异议退回',type:'warning',variant:'solid'}`、`PAID:{label:'已发放',type:'success',variant:'outline'}`；**最早落地** |
| 2 | 筛选字典 | `dict.js:302-309` | `PAYROLL_FILTERS` 追加 `OBJECTED`、`PAID`，按处理动线定位（建议序：待审核 → **异议退回** → 已通过 → 已发布 → 已确认 → **已发放** → 已驳回 → 草稿） |
| 3 | 标签配色 | `shared/ui/StatusTag.vue:89-96` + `web/components/StatusTag.vue` | `DICT_COLORS[PAYROLL_STATUS]` 补 `OBJECTED:{surface:'var(--color-warning)',text:'var(--text-on-dark)'}`；`PAID` 不必覆盖（outline 回落即可） |
| 4 | NPE 现场① | `web/.../PayrollDetailDrawer.vue:116` | 改安全取值 `(PAYROLL_STATUS[detail.status] \|\| {}).variant`，防新态 NPE |
| 5 | NPE 现场② | `web/.../PayrollDetailTable.vue:99` | 同上安全取值 |
| 6 | NPE 现场③ | `web/.../PayrollObjectionsPanel.vue:40` | 同上安全取值 |
| 7 | NPE 现场④ | `web/.../employee/detail/index.vue:116` | 同上安全取值 |
| 8 | 可编辑集 | `web/.../PayrollDetailDrawer.vue:66` | 由 `['DRAFT','REJECTED']` 改为镜像 `isItemEditable`（含 `PENDING_APPROVAL`/`OBJECTED`） |
| 9 | 步骤条 | `web/components/PayrollStatusSteps.vue:20-26,69-72` | 增 `OBJECTED`/`PAID` 步（或明确不纳入并给出替代呈现）；异议分支由 `objectionReason` 改判 `status==='OBJECTED'`；时间线补 `PAID`（发放） |
| 10 | mock 字典/动作 | `mock/financeStore.js:37-59` | `PAYROLL_STATUS_LABEL` 追加两态；`PAYROLL_ACTIONS` 增 `CONFIRMED:['pay']`、`OBJECTED:['publish','submit']`；`PENDING_APPROVAL` 增明细动作标志（Q6） |
| 11 | mock 可见集 | `mock/financeStore.js:48` | `EMPLOYEE_VISIBLE_STATUS` **加 `PAID`**（U-08） |
| 12 | mock 可编辑集 | `mock/financeStore.js:50` | 拆为「明细可编辑 `isItemEditable`」与「可覆盖 `isOverwritable`」两集，镜像 §2.3 |
| 13 | mock 分支 | `mock/financeStore.js:276,503,605,632,719,764-774` | 逐点同步新判据；**异议落 `OBJECTED`**（不再落 `PENDING_APPROVAL`） |
| 14 | mock 路由 | `mock/routes/finance.js:131` | 状态白名单取 `PAYROLL_STATUS_LABEL` 键，字典补齐后自动生效，**须回归** |
| 15 | mock 路由 | `mock/routes/finance.js:152` | 员工状态限 **加 `PAID`**（`['PUBLISHED','CONFIRMED','PAID']`） |
| 16 | 门禁断言 | `scripts/verify-mock.mjs:5470` | 「恰好 6 态」改 8 态（或断言包含新态），否则**门禁失败** |
| 17 | 门禁断言 | `verify-mock.mjs:5331,5371` | `5371` 员工可见集加 `PAID`；`5331` 不受影响 |
| 18 | 待办字典 | `boss-h5/stores/todo.js:69,102`、`staff-h5/stores/todo.js:54` | 字典补齐后回落；确认新态文案（尤其 `PAID` 员工侧可读性） |
| 19 | 待办组 | `boss-h5/constants/todoGroups.js:25` | 异议待办须改查 `OBJECTED`（**建议新增独立异议组**，见 §7.2），否则异议单不进待办 |
| 20 | 异议面板 | `web/.../composables/usePayrollObjections.js:22`、`boss-h5/.../payroll.vue:29,52` | **必须改查 `OBJECTED`**，否则异议面板/待办空 |
| 21 | 端详情 | `boss-h5/.../payrollDetail.vue:38,45,75`、`staff-h5/.../payrollDetail.vue:41-65` | boss `objected = status==='OBJECTED'`；staff 增 `PAID` 只读文案与 `OBJECTED` 期 `9403` 文案（§1.3） |
| 22 | 动态/其他 | `MyPayrollCard.vue:49`、`GeneratePayrollDialog.vue:23`、`usePayrollActions.js:89`、`PayrollTabPanel.vue:84,109-116` | 字典补齐回落；可覆盖集/动作集与后端 `isOverwritable`/动作集**同口径** |
| 23 | staff 侧点位① | `staff-h5/.../payroll.vue:93` | `PUBLISHED` 待确认提示保留；确认 `PAID`/`OBJECTED` 不受影响（`OBJECTED` 员工端本就不可见） |
| 24 | staff 侧点位② | `staff-h5/components/MyPayrollCard.vue:49` | 字典补齐回落；确认 `PAID` 文案可读 |
| 25 | staff 侧待办组 | `staff-h5/constants/todoGroups.js:21` | `myPayrolls` 仍查 `PUBLISHED`；与 C-1 后口径一致性核对（异议单员工端不可见即为正确） |

### 11.2 共享字典单源与 mock 镜像改动要求

**`packages/shared/src/constants/dict.js`**

- 修改：`PAYROLL_STATUS`（+2 态）、`PAYROLL_FILTERS`（+2 项）。
- **新增导出**（供新页面消费，均为纯展示映射，不并入 `PAYROLL_STATUS`）：
  - `PAYROLL_RUN_STATUS`：`RUNNING/SUCCESS/FAILED/SKIPPED`（label + type/variant）。
  - `PAYROLL_RUN_TRIGGER`：`AUTO/CATCH_UP/MANUAL`。
  - `PAYROLL_SKIP_CODE`：`BLOCKED_9405/CONFIG_INVALID/DRAFT_PROTECTED`（中文文案）。
  - `PAYROLL_SETTING_LOG_ACTION`：`CREATE/UPDATE/ENABLE/DISABLE`（`ENABLE` 文案「启用自动算薪」）。
  - `PAYROLL_LOG_ACTION`：`GENERATE_AUTO/GENERATE_MANUAL/ITEM_ADD/ITEM_UPDATE/SUBMIT/APPROVE/REJECT/PUBLISH/REPUBLISH/CONFIRM/OBJECTION/PAY/NOTIFY/NOTIFY_SKIP`（中文文案）。
- **顺序约束**：字典 + mock 镜像 + 硬编码视图**先于/同批于**后端返回新状态落地（否则 `PAYROLL_STATUS[status].variant` 取 `undefined` → NPE）。

**`packages/mock/src/financeStore.js`**

- 上述 #10–#13；另需新增：
  - `pay(id, op)`（`CONFIRMED → PAID`，写 `paid_*`）。
  - 驿站算薪配置 + 变更日志（I-1/I-2/I-3/I-9）种子与动作。
  - 运行记录（I-4/I-5）种子与 `trigger` 动作；`payroll_run` 状态机。
  - 留痕（I-7）追加型数组；`before/after` 白名单。
  - 对账汇总（I-10）按 `employee_id + month` 聚合 `ITEM_ADD`。
  - `generatePayrolls` 的 9405 判定**按驿站收敛**；异议 `objectPayroll` **落 `OBJECTED`**；`publishPayrolls` 的 `ids` 路径**允许 `OBJECTED` 来源 + 非法来源显式报错**；`updatePayrollItems` 改 `isItemEditable` + **`reason` 必填 2–200** + **不覆盖 `detail`**。

**`packages/shared/src/constants/errorCode.js`**（补齐可判定分支）

- `FINANCE_CODE` 追加：`9406` / `9407` / `9408` / `9409`（保留）/ `9410` / `9411` / `9412` / `9413` / `9415`；`9414` / `9416` **不加**（已作废、号段不复用）。
- 文案表同步追加（与 `api.md §4.3` 逐字一致）。

**新增路由与页面（前端实现范围，非本设计文档改动）**

- boss-h5：`/boss/payroll-settings`、`/boss/payroll-settings/:stationId`（`meta.roles=[ROLE.ADMIN]`）。
- web：财务管理域新增 Tab「自动算薪运行」「手工调整对账」（或子路由），新增 `PayrollSettingsPanel` / `PayrollRunPanel` / `ManualAdjustmentPanel` 与对应 composables（具体组件划分交前端）。

### 11.3 验收口径（交测试工程师，可判定）

- 三端**详情 / 列表 / 步骤条 / 待办 / mock / 门禁**均无 NPE；8 态渲染与动作可用性正确。
- 新增态标签文案与后端 `PayrollStatus.label` 逐字一致；`counts` 8 键齐备、顺序正确。
- `OBJECTED`/`PAID` 在员工端可见性与 §1.3 一致（`PAID` 可见、`OBJECTED` 不可见）。
- `9413` 与 `9403` 文案不混用；所有写操作在 `PAID` 下禁用且服务端兜底。
- I-7 非 ADMIN 出参**不含** `before/after/operator_*`（前端**不**依赖本地裁剪）。
- 触控目标 ≥44px、对比度 ≥4.5:1、状态不只靠颜色（可抽查 §9 清单）。

---

## §12 冲突登记（既有页面/字典 与 方案 v1.5 / 契约 v1.4 —— 只登记，不改代码）

> 下列为本次精读实测发现，供主智能体分派修复；本设计文档**不修改任何代码**。

| # | 位置 | 现状 | 与方案/契约冲突点 | 影响 |
| --- | --- | --- | --- | --- |
| 1 | `mock/financeStore.js:37-44` | `PAYROLL_STATUS_LABEL` 仅 6 态 | 缺 `OBJECTED`/`PAID`（契约 8 态） | 新态渲染 `undefined` |
| 2 | `mock/financeStore.js:48` | `EMPLOYEE_VISIBLE_STATUS=['PUBLISHED','CONFIRMED']` | 缺 `PAID`（C-6 / U-08） | 员工确认后单据消失 |
| 3 | `mock/financeStore.js:50` | `EDITABLE_STATUS=['DRAFT','REJECTED']` | 未拆 `isItemEditable`/`isOverwritable`（§2.3） | 审核期/异议期不可改金额 |
| 4 | `mock/financeStore.js:52-59` | `PAYROLL_ACTIONS` 无 `pay`，`CONFIRMED:[]` | 缺 `CONFIRMED:['pay']`、`OBJECTED:['publish','submit']` | 发放不可达 |
| 5 | `mock/financeStore.js:762-784` | `objectPayroll` 落 `PENDING_APPROVAL`，注释明确「复用 PENDING_APPROVAL」 | **冲突 C-1**：须落 `OBJECTED` | 异议与待审核混同，无法区分 |
| 6 | `mock/financeStore.js:689-712` | `publishPayrolls` 对非 `APPROVED` **静默计入 `skipped`**，来源不含 `OBJECTED` | **冲突 C-2**：`ids` 路径须显式报错、允许 `OBJECTED` | 再发布不可达、静默失败 |
| 7 | `mock/financeStore.js:714-743` | `updatePayrollItems` 仅 `EDITABLE_STATUS`、**无 reason**、`target.detail='人工填写'`（覆盖事由） | **冲突 C-3 / §3.2**：须 `isItemEditable` + `reason` 必填 2–200 + 不覆盖 `detail` | 9412 不可达、审计事由被抹 |
| 8 | `mock/financeStore.js:502-510` | `generatePayrolls` 的 9405 判定为**账期全局级** | **冲突 C-7**：须按驿站收敛 | 多驿站自动算薪相互阻断 |
| 9 | `mock/routes/finance.js:149-157` | `myList` 白名单 `['PUBLISHED','CONFIRMED']` | 缺 `PAID`（C-6） | 归档态不可见 |
| 10 | `web/.../usePayrollObjections.js:22` + `PayrollObjectionsPanel.vue:24` | 查 `PENDING_APPROVAL` + 本地过滤 `objectionReason` | **冲突 C-1**：须改查 `OBJECTED` | 异议面板在 C-1 后为空 |
| 11 | `boss-h5/.../payroll.vue:29,52` | 默认筛选 `PENDING_APPROVAL`，无异议入口 | 需新增 `OBJECTED` 待办/筛选（§2.8 #20） | 异议单不可发现 |
| 12 | `boss-h5/constants/todoGroups.js:25` | `payrolls` 组固定查 `PENDING_APPROVAL` | 异议单落 `OBJECTED` 后不进待办（§2.8 #19） | 异议待办丢失 |
| 13 | `web/components/PayrollStatusSteps.vue:20-37,45-61` | 固定 5 步；异议靠 `objectionReason`；无 `PAID` | 需扩展 8 态呈现（§2.8 #9） | 步骤条无法表达新态 |
| 14 | `boss-h5/.../payrollDetail.vue:38` | `objected = objectionReason && status==='PENDING_APPROVAL'` | 须改判 `status==='OBJECTED'`（§2.8 #21） | 异议态判错 |
| 15 | I-10 对账下钻 | 现有 I-7 以 `payroll_id` 为键；I-10 真源为 `employee_id+month` | 覆盖重建后旧单已删，按 `payroll_id` 漏取留痕 | 下钻不完整（见 §8.2，建议契约补出口） |
| 16 | `staff-h5/.../payrollDetail.vue:75` | `9403` 文案「工资单尚未发布，暂不可查看」 | 异议退回期语义不符（§1.3） | 员工困惑 |
| 17 | `web/.../PayrollDetailDrawer.vue:116` / `PayrollDetailTable.vue:99` / `PayrollObjectionsPanel.vue:40` | `PAYROLL_STATUS[x].variant` 直接取 | 新态未入字典即 **NPE**（§2.8 #4-6） | 页面崩溃 |
| 18 | `web/.../GeneratePayrollDialog.vue:23` | 本地重复定义 `EDITABLE_STATUS=['DRAFT','REJECTED']` | 与字典/mock 双口径，易漂移（§2.8 #22） | 可覆盖集不一致 |
| 19 | `packages/verify-mock.mjs:5470` | 断言「6 态齐备」 | 8 态后**门禁失败**（§2.8 #16） | 门禁红 |
| 20 | `verify-mock.mjs:5371` | `my` 列表限 `['PUBLISHED','CONFIRMED']` | 缺 `PAID`（§2.8 #17） | 断言与契约不一致 |
| 21 | `packages/shared/src/constants/errorCode.js:147-153` | `FINANCE_CODE` 仅 `9401-9405`，无 `9406..9415` 文案 | 新增错误码未登记 | 前端无法分支/提示 |
| 22 | **设计系统路径** | `demo-boss-ui-spec.md` 引用 `hrm-demo/src/...` 与 `shared/styles/tokens.base.scss` | 当前仓库为 `hrm-clients`，真源实为 `hrm-clients/packages/tokens/src/tokens.base.scss` | 引用路径须以 `hrm-clients` 为准 |
| 23 | **字号 Token** | `demo-boss-ui-spec.md` 记 `--fs-num-lg-boss`(24) | `hrm-clients` 实为 `--fs-num-lg-staff`(22)（boss-h5 与 staff-h5 同名同值），无 `--fs-num-lg-boss` | 引用须用实际存在的变量名 |
| 24 | **StatusTag 双实现** | `shared/ui/StatusTag.vue` 与 `apps/web/src/components/StatusTag.vue` 并存，web 页面引用后者 | 状态配色改动需**两处同步**，否则三端不一致 | 标签色漂移 |
| 25 | **步骤条三实现** | `boss-h5` 与 `staff-h5` 的 `PayrollStatusSteps.vue` 内容逐字相同，`web` 另有 el-steps 版 | 三处状态映射须同批更新，`PAYROLL_STATUS` 相关分支易漏改 | 步骤条口径分裂 |

---

## 附录 A：新增/复用语义色的对比度依据（**本机未复算，引既有实算**）

**计算方式**（引用 `demo-boss-ui-spec.md 附录 A.1`，W3C WCAG 2.x 相对亮度）：

```
L = 0.2126·R + 0.7152·G + 0.0722·B (线性化后)
Contrast = (L_light + 0.05) / (L_dark + 0.05)
```

| 用途 | 前景 / 背景 | 实算 | 判定 | 来源 |
| --- | --- | --- | --- | --- |
| `OBJECTED` solid 白字 | `--text-on-dark`(#FFFFFF) / `--color-warning`(=amber-700 #B45309) | **5.022** | ✓ AA（普通文本 ≥4.5） | 附录 A #13（白/`--rank-1-bg`=amber-700，同值） |
| `PAID` outline 文字（白卡） | `--color-success`(#237804) / `--surface-card`(#FFFFFF) | **5.585** | ✓ AA | 附录 A #5 |
| `PAID` outline 文字（页面底） | `--color-success`(#237804) / `--surface-page`(#F5F7FA) | **≈5.2（推导）** | ✓ AA（**须实测确认**） | 按附录 A #2 同族衰减推导 |
| `OBJECTED`/`PAID` 标签底/描边 | `--color-warning-surface` / `--state-outline-border`(#CBD2DA) | 3.106（描边，SC 1.4.11） | ✓ 非文本 | 附录 A #19 |

**结论：本次新增两态映射**未引入任何新色值**，仅启用既有色族与既有 variant（`solid` / `outline`），故无新增色板；上表为落地所需的最小对比度核对，实现后须以同法实测「`--color-success` 对页面底」一项。

---

## 附录 B：Token 引用对照（以 `hrm-clients` 实际变量名为准）

| 用途 | 变量 |
| --- | --- |
| 主色（实底/文字） | `--color-primary` / `--color-primary-icon` / `--color-primary-surface` / `--color-primary-border` / `--color-primary-strong` |
| 语义色族 | `--color-success|warning|danger` + `--color-*-surface` / `--color-*-border` / `--color-*-icon` |
| 六态状态族 | `--state-success|warning|danger|primary|neutral` 的 `-bg/-fg/-border`；`--state-outline-bg|-border|-fg` |
| 文本 | `--text-1` / `--text-2` / `--text-3` / `--text-placeholder` / `--text-disabled` / `--text-on-dark` |
| 表面 | `--surface-card` / `--surface-page` / `--surface-sub` / `--surface-subtle` / `--surface-sunken` / `--surface-hover` |
| 描边 | `--border-line` / `--border-control` |
| 间距/圆角/动效 | `--sp-1..10` / `--r-xs..full` / `--dur-fast|base|slow` / `--ease-std` |
| 字号（移动私有） | `--fs-h1-m` / `--fs-h2` / `--fs-h3` / `--fs-body` / `--fs-body-strong` / `--fs-caption` / `--fs-micro` / `--fs-num-lg-staff` / `--fs-num-md` / `--fs-num-sm` |
| 组件尺寸（平台私有） | `--step-dot` / `--step-line` / `--step-gap` / `--tag-h` / `--tag-pad-x` / `--drawer-w-lg` |
| 渐变 | `--grad-hero`（brand） / `--grad-hero-deep`（管理端经营） |

> 引用约束：任何页面/组件出现十六进制色值即视为缺陷；500 档 `--color-primary-icon` 不得承载文字；浅底块内文字统一 `--text-2`。

---

## 附录 C：与既有文档的关系（避免规范冲突）

| 文档 | 关系 |
| --- | --- |
| `payroll-automation-design.md` v1.5 | **上游真源**：状态机、动作矩阵、接口、错误码；本文仅做界面落地，不重定义 |
| `api.md` v1.4 §4.12 | **契约真源**：出参字段、权限裁剪、错误码文案；本文文案与其同义 |
| `demo-boss-ui-spec.md` | **设计系统真源**：Token、断点、交互反馈、RBAC 展示、对比度实算；本文沿用，不另起 |
| `demo-staff-ui-redesign.md` | 员工端风格先例（本文移动端沿用同体系） |
| 本文（`payroll-ui-design.md`） | **界面落地规范 + 交付清单 + 冲突登记**；冲突项须由主智能体分派修复后回填 |

> **升级路径触发登记**：本设计涉及的运行记录/对账/设置页属**新增前端页面**，若需改动 `hrm-admin`/`hrm-server` 或需新增契约出口（如 §8.2 下钻、I-7 按员工+账期检索），**停下回报主智能体**，由主智能体按 §8 流程裁决。
