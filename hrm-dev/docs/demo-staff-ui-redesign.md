# 三端演示 Demo · 移动端「员工端」UI/UX 精细化设计规范

> 版本 v1.0 | 建立日期 2026-09-22 | 分支 `feature/员工端UI精细化`
> 适用范围：`hrm-dev/hrm-demo/src/mobile/views/staff/**`（22 页）+ `src/mobile/components/**`（26 组件）+ `src/mobile/styles/**`
> 本文档是员工端视觉与交互的**唯一口径来源**。与 `demo-ui-redesign.md` 5.8 冲突处，以本文档 + `demo-mobile-nav-redesign.md` 为准（5.8 的多处描述已被三 Tab 改造推翻，见 §1.3 的文档冲突登记）。
> 只出规范，不含 `.vue` / `.scss` 实现代码。

---

## 0. 范围、取证方式与不确定项

### 0.1 范围（硬约束）

| 项 | 约定 |
| --- | --- |
| 组件库 | 仅 Vant 4（`node_modules/vant` 实测版本 4.10），不引入任何新 UI 库/设计依赖 |
| 基准宽度 | 375px；必测 320 / 375 / 414 / 640（横屏）/ 安全区 |
| Token | 落既有三层：`src/shared/styles/tokens.base.scss`（L1 + 跨端 L2）+ `src/mobile/styles/tokens.scss`（移动平台 L2/L3） |
| 媒体查询 | 组件内**禁止**魔法 px 断点（既有 `mobile.scss:431-437` 的 640 横屏档为唯一白名单） |
| 状态组件 | 只允许 `PageState.vue`，**不得自造第五套**；**绝不用 `0` 表示「加载失败/未知」** |
| 对比度 | 实算，不手算（§8.1 附可复现脚本）；正文 ≥4.5:1，非文本图形/控件边界 ≥3:1 |
| 触控 | 交互元素最小 44×44 CSS px |

### 0.2 取证方式

全部结论来自源码逐文件通读（行号精确到可跳转位置）+ node 实算对比度（§8.1）。已通读：22 个 staff 页、26 个移动组件、`mobile.scss`、`tokens.scss`、`tokens.base.scss`、`router/index.js`、`constants/{tabs,quickEntries,todoGroups}.js`、`stores/{todo,notify}.js`、`composables/useCheckIn.js`，以及 Vant 产物 `es/tab/TabTitle.mjs`、`es/tabs/Tabs.mjs`、`es/radio/Radio.mjs`、`es/switch/Switch.mjs`、`es/cell-group/index.css`。

**关键实测结论（先给结论）**：员工端 22 页在 2026-09 的 UI 重设计中已基本完成「Token 化」，`grep -rn "#[0-9a-fA-F]{3,8}" src/mobile/views/staff` **零命中**——即页面内已无十六进制色值。本轮的问题不在「有没有用 Token」，而在四类：**状态语义漏网、多份平行实现未收敛、契约色值绕过 Token、比对口径（触控/对比度/文案）在边角处失守**。

### 0.3 不确定项与验证方式（反幻觉声明）

| # | 不确定项 | 为什么不确定 | 验证方式 |
| --- | --- | --- | --- |
| U1 | 壳侧 `--status-bar-height` 实测值 | 安卓壳未编译（项目规则 §14.3），`MeSection.vue:100` 浏览器下恒显示 `0 px` | 真机（或壳编译后）读 `--status-bar-height` 实际注入值 |
| U2 | `van-radio` 是否可 Tab 聚焦 | Vant 4.10 `es/radio/Radio.mjs:34` 全文件仅输出 `role="radio"`，未输出 `tabindex`；`leaveApply.vue:245,259` 的半天选择依赖它 | 真机/桌面浏览器 Tab 键实测；不可聚焦则按 §8.4 改原生 button |
| U3 | 320/375/414/640 四档与安全区真实表现 | 内置浏览器窗口固定 810×658（项目规则 §4.1），无法覆盖真机尺寸 | DevTools 设备模拟 + 真机复核（§9 标注「待真机复核」） |
| U4 | Vant 半透明叠加后的最终对比度 | 本规范按 Token 字面值实算；若某节点叠加了 alpha 层（如 Hero 内再叠 chip），实际值会变 | DevTools 取渲染色后按 §8.1 脚本复算 |
| U5 | 长列表（>500 条）滚动性能 | `van-list` 20/页 无虚拟化，本机无法构造 20 万级真实数据 | 用 mock 造 2000 条做真机滚动取样，超阈值再决定是否引入虚拟化（本期不做） |
| U6 | 安卓 WebView 键盘弹起后的可视区行为 | 壳未编译 | 真机验证（§7.4） |

---

## 1. 现状实测盘点

分级判据：**P0 = 会导致错误结论/不可用/不合规**；**P1 = 违反本规范与既有设计系统的硬性条款**；**P2 = 一致性/可维护性优化**。

### 1.1 P0 · 缺陷级（8 条）

| # | 问题 | 依据（文件:行号） | 判据 | 修复方向 |
| --- | --- | --- | --- | --- |
| **P0-1** | **取数失败被吞成空态**：日志弹层 `catch` 里把 `logs` 置空，页面因此落到「该批次暂无日志」——用户看到的是「确实没有日志」，实际是接口挂了 | `staff/sync.vue:70-78`（catch）+ `staff/sync.vue:179-180`（渲染） | 违反「错误态与空态文案必须不同」+「绝不用空表示加载失败」 | 弹层内新增独立 error 分支（`role="alert"` + 重试），与空态文案分离 |
| **P0-2** | **`0` 冒充未知**：计数条在 `PageState` 之外，首屏/失败期间显示「共 0 张」「共 0 条」 | `staff/payroll.vue:71`（`共 {{ total }} 张已发布工资单`，`total` 初值 0，在 `:73` 的 `v-if="loading"` 之上）；`components/LeaveApprovalList.vue:196`（同型） | 违反 B4-2 硬规则 2（`0` 是结论，未知是另一件事） | 计数条移入数据态内，或未知显 `—` |
| **P0-3** | **触控热区不达标（32px）**：`van-button size="small"` 无 `min-height` 覆盖，Vant small 档为 32px | `staff/sync.vue:101`（重新加载）、`staff/sync.vue:142`（查看日志） | < 44×44（WCAG 2.5.5） | 统一走 `--touch-min`（§3 N1），或在按钮上补 `min-height` |
| **P0-4** | **对比度不达标**：搜索框**无 label**，placeholder 是唯一输入提示，而占位符色实算仅 **2.52:1** | `staff/parcel.vue:134-146`（`van-search` + `placeholder="输入运单号精确查询"`，无 `label`） | SC 1.4.3 要求 4.5:1；`demo-ui-redesign.md` 9.3 D-1 的豁免理由是「标签始终可见」，此处**理由不成立** | 二选一：给搜索框加可见标签文案（如左侧「运单号」文案）、或把该处占位符色加深到 ≥4.5:1（不得两者兼得，见 §8.2） |
| **P0-5** | **同一状态两端两种叫法**：`PARCEL_STATUS` 值 1 在字典与 PC 端叫「在库待取」，员工端叫「待取件」 | 字典/PC：`shared/constants/dict.js:29`、`pc/views/parcel/index.vue:203`（指标卡「在库待取」）；员工端：`staff/home.vue:166`（指标卡「待取件」）、`staff/parcel.vue:19`（Tab「待取件」）、`staff/pickup.vue:171` | 违反「三端文案口径一致」 | 员工端统一为「在库待取」，或经评审确认「待取件」为员工端口语化别名并全端登记 |
| **P0-6** | **键盘无法切换 Tab**：Vant 4.10 的 tab 标题是 `div[role="tab"]`，**无 keydown 处理**，Tab 键可聚焦但 Enter/Space 不触发 | 页面：`staff/workorder.vue:116-118`、`staff/parcel.vue:148-150`、`views/message/MessagePage.vue:50-61`、`components/NoticeList.vue:156-163`；根源：`node_modules/vant/es/tab/TabTitle.mjs:70-85`（仅 role/tabindex/aria-selected/aria-controls，无键盘事件） | SC 2.1.1 键盘可达 | 按 `TabbarLayout.vue:62-67` 的既有做法，在页面层补 `@keydown.enter/@keydown.space` 兜底 |
| **P0-7** | **四态不齐 + 失败静默**：查询包裹失败被吞掉，页内只剩引导态，无 error 分支 | `staff/pickup.vue:69-71`（catch 空实现）；页面无 `PageState`（`grep` 确认 pickup 未引入） | 「所有承载数据的区块必须显式覆盖 loading/empty/error/normal」 | 查询失败补页内 `role="alert"` 提示条 + 重试（与已有的 `alertText` 提示条同容器） |
| **P0-8** | **同页两套缩进**：同一页内 `van-cell` 左缩进 12+16+16=**44px**，`van-cell-group inset` 的 cell 为 12+0+16=**28px** | 44px：`staff/profile.vue:97-110`（`.card` 内直放 `van-cell`，未收敛 `--van-cell-horizontal-padding`）；28px：`staff/profile.vue:69-93`（`van-cell-group inset`） | 视觉层级/栅格一致性 | 统一为一种容器契约（§4 定为「卡片内 cell 一律收敛 `--van-cell-horizontal-padding: 0`」） |

### 1.2 P1 · 规范级（15 条）

| # | 问题 | 依据 | 修复方向 |
| --- | --- | --- | --- |
| P1-1 | **契约色值直出 inline style（体系外颜色通道）**：`shift.color` / `day.color` 由接口下发后直接写进 `background`，绕过 Token。当前取值恰好落在色板（`#0958D9`/`#FA8C16`/`#1F2937`），契约一改即静默脱离色板 | `staff/attendance.vue:361`、`staff/schedule.vue:87`；来源 `shared/mock/attendanceStore.js:63-65` | 经「契约色 → Token」映射表落色，映射失败回落 `--border-line`（§3 N4 + §4 C4） |
| P1-2 | **员工端主指标字号越级**：KPI 页与管理端共用一页，员工视角仍用管理员档 24px | `staff/kpi.vue:113`（`--fs-num-lg-boss`）vs `demo-ui-redesign.md` 3.2 差异表「员工端 22（`--fs-num-lg-staff`）」；对照 `staff/home.vue:162` 已正确用 `value-size="staff"` | 按 `isBossView` 切换字号档 |
| P1-3 | **chip/胶囊四套平行实现**（同一视觉，四处样式块） | `mobile.scss:311-334`（`.chip`/`.chip--active`）、`components/FilterChips.vue:48-64`、`staff/makeupList.vue:138-155`（`.fchip`）、`staff/attendance.vue:826-836` + `:881-891`（`.verify__badge` / `.mini-chip`） | 收敛为 `Chip`（交互）+ `MiniChip`（属性标记）两个原子，删各页私有块；`FilterChips.vue:8-9` 的 `TODO(扩展)` 同步销项 |
| P1-4 | **徽标三套实现**（同尺寸同配色各写一遍） | `components/QuickGridItem.vue:90-103`、`views/message/MessagePage.vue:69-81`、`components/NoticeList.vue:235-247` | 抽 `Badge` 原子（§4 C2），字号/高度/padding 收口到 §3 N3 |
| P1-5 | **骨架高度六处硬编码且互不相同**（96/108/120/120/148/156） | `staff/sync.vue:208`、`staff/makeupList.vue:158`、`staff/payroll.vue:104`、`components/MyPayrollCard.vue:90`、`components/LeaveApprovalList.vue:283`、`staff/leaveList.vue:272` | 骨架高度必须由 §3 N2 的行高 Token 派生，不写字面量 |
| P1-6 | **三页自绘骨架绕过 `PageState` 的 loading 防抖**：`PageState.vue:21-39` 有「200ms 内不显示骨架」的防抖，但这几页用 `v-if="loading"` 自绘，防抖失效、快请求下会闪一下 | `staff/leaveList.vue:155-159`、`staff/makeupList.vue:98-102`、`staff/payroll.vue:73-77`、`components/LeaveApprovalList.vue:198-202` | 一律把 `loading` 交给 `PageState`（`PageState.vue:47-51`） |
| P1-7 | **加载转圈**：违反「禁止加载转圈（用骨架）」 | `staff/sync.vue:179`（`van-loading`） | 改块骨架（`.skeleton-block`） |
| P1-8 | **`van-cell-group` 形态不统一**：5 页用 `inset`（12px 圆角 + margin 0，`node_modules/vant/es/cell-group/index.css:1`），3 页不用（直角、无圆角） | 用 inset：`staff/parcelDetail.vue:120`、`staff/flow.vue:74`、`staff/payrollDetail.vue:175`、`staff/profile.vue:69,80`、`staff/workorderDetail.vue:264`；不用：`staff/attendance.vue:573`、`staff/sync.vue:108`、`staff/workorderCreate.vue:82` | 定一种（§4 定为「表单/只读信息组一律 `inset`」） |
| P1-9 | **局部覆盖 token 的 cell 行高**：`10px`（行高 44，恰好达标）偏离基线 48 | `staff/pickup.vue:205`（`--van-cell-vertical-padding: 10px`）vs `mobile/styles/tokens.scss:105`（12px） | 回到 12px，或登记为局部偏差并同步 §9 验收 |
| P1-10 | **图表读数仅触摸可达**：只有 `touchstart/move`，无 `pointerdown`/键盘路径——桌面浏览器与键盘用户读不到数值 | `components/LineChart.vue:150-159`、`:247-250` | 补 `pointer*` 事件与键盘左右键读数（该组件为共享层，两端受益） |
| P1-11 | **Vant Switch 无键盘激活**，打卡页已手动补 keydown，但没有形成规范，后续新增开关会漏 | 已补：`staff/attendance.vue:523-529`；根源：`node_modules/vant/es/switch/Switch.mjs:66-74`（有 role/tabindex/aria-checked，无 keydown） | 写入规范：凡 `van-switch` 必须显式补 `@keydown.enter.prevent` + `@keydown.space.prevent` |
| P1-12 | **既有注释里的对比度数字与实算不符**（正是「不得手算」的教训复现） | `mobile/styles/mobile.scss:190` 声明 Hero 副信息「≈5.4:1」，实算 **4.66:1**；`staff/leaveApply.vue:396` 声明「`--text-3` 在浅灰底上只有 4.23:1」，实算 `--text-3` on `--surface-subtle`(#F5F7FA) 为 **4.5046:1**，4.23 实为 `--text-3` on `--surface-sunken`(#EDF0F4)；`components/SlaTag.vue:86` 声明警告标签「4.71:1」，实算 **4.83:1** | 按 §8.1 实算值改写注释与 `demo-ui-redesign.md` 9.1 表 |
| P1-13 | 「我的数据」分组已膨胀到 9~10 项，与 `demo-mobile-nav-redesign.md` A3 约定的 6 项不符，一屏放不下 | `components/MeSection.vue:57-75` | 按 §5.7 重排（分组内 ≥7 项时二次分群） |
| P1-14 | **宫格排序规则与文档表述不一致**：B1 写「计数型在前」，实际是「固定首位 + 业务频次混排」 | `constants/quickEntries.js:32-52`（第 1 项 `attendance` 为 status，末尾又混 plain 与 status）vs `demo-mobile-nav-redesign.md` B1「排序规则：按待办优先」 | 二选一：改文档表述为「固定首位 + 待办优先 + 频次降序」，或重排配置 |
| P1-15 | **SLA 超时可见文案三种并存**：标签正文「已超时 2小时」/ 筛选 chip 与提示条「超时未处理」/ 标签 `aria-label`「已超时未处理」 | `utils/format.js:59` 与 `utils/format.spec.js:31-34`（锁定「已超时」）、`components/SlaTag.vue:54`、`staff/workorder.vue:129`、`staff/home.vue:149` | 定义单一口径（建议可见文案统一「超时未处理」，相对时长放次级），并同步 `format.spec.js` |

### 1.3 P2 · 优化级（8 条）

| # | 问题 | 依据 |
| --- | --- | --- |
| P2-1 | 魔法数值：`font-size:12px`（`staff/workorderCreate.vue:221`）、`font-size:40px`/`18px`（`staff/pickup.vue:250,286`）、`height:32px`/`20px`（`staff/attendance.vue:638,829,884`）、`padding:0 6px`（`:830,885`）、`line-height:1.4`（`components/QuickGridItem.vue:109`）、`line-height:1.7`（`staff/workorderDetail.vue:414`）、`padding-right:52px`（`staff/workorder.vue:221`） |
| P2-2 | 非 4px 栅格：`border-left:3px`（`staff/attendance.vue:762`、`components/AttendanceStatusBar.vue:145`）、`7px` 圆点（`mobile.scss:392`）、`gap:3px`（`components/SlaTag.vue:68`，已有登记） |
| P2-3 | 键盘处理不一致：`staff/payroll.vue:88-90` 只有 Enter，无 Space，而 `parcel`/`workorder`/`sync`/`NoticeList` 都有 |
| P2-4 | 可点 `div` 无语义：`staff/attendance.vue:515`（`.assist` 整行可点，仅内部 switch 可聚焦） |
| P2-5 | 同功能不同样式：月份/账期 chip 走全局 `.chip`（`components/MonthPicker.vue:24`），补卡筛选走私有 `.fchip`（`staff/makeupList.vue:89`） |
| P2-6 | 页面级 `.card { margin-top }` 私有覆盖与 `mobile.scss:121-123` 的 `.card + .card` 规则并存（`workorderCreate.vue:176-178`、`password.vue:103-105`、`sync.vue:196-198` 等） |
| P2-7 | **文档冲突（需回写）**：`demo-ui-redesign.md` 5.8-S5 的 FAB `bottom` 仍含 `--tabbar-h`，与现状 `staff/workorder.vue:238`（工单已降为二级页、无 Tabbar）不符；7.2「Tabbar 固定 5 项」「文字 10px」已失效（现为 3 项 / 12px，见 `constants/tabs.js:15-19`、`mobile/styles/tokens.scss:88`）；7.4 表 7 处触控修复项已完成 5 处。**注**：`demo-mobile-nav-redesign.md` 中引用的 `tokens.scss:2xx` 等行号属 P1-2 重构（引入 `tokens.base.scss`）之前的旧版本——现 `tokens.scss` 仅 191 行、`mobile.scss` 438 行，凡行号引用一律以**章节名 + 语义**为准重新定位 |
| P2-8 | `.tip` / `.tool-row` 用 `--text-3` 落在页面底色 `--surface-page` 上，实算 **4.5046:1**，仅余 0.0046 余量（`mobile.scss:89-94`、`:301-308`）——任何对 `--surface-page` 的加深都会致其不合规 |

---

## 2. 设计方向与视觉基调

### 2.1 一句话定位（员工端）

> **员工端是「作业型界面」，不是「阅读型界面」**：一线员工在驿站现场、单手持机、可能戴手套，任务是「现在这一步做什么」，而不是「看懂一份报表」。

由此推出的 6 条视觉契约（与管理端/PC 端的分界）：

| 维度 | 员工端（本规范） | 管理端 / PC 端 | 是否共享 Token |
| --- | --- | --- | --- |
| 首屏首要目标 | **动作**（一键打卡 + 待办队列），动作不靠滚动可达 | 管理端：**结论**（趋势与异常）；PC：**批量** | 是 |
| 信息密度 | 列表项 3 行（标题/元信息/警示），行高 76；区块间距 12 | 管理端 2 行、间距 16；PC 表格 44 行高 | 部分（密度值走移动层） |
| 数值字号 | 主指标 22（`--fs-num-lg-staff`）；同屏数值字号 ≤2 种 | 管理端 24（`--fs-num-lg-boss`） | 名同值不同（合法平台差异） |
| 主色使用比例 | 蓝主色只出现在**图标、可点文字、主按钮实底、选中态**（≈10% 面积），页面由白卡 + 浅灰底构成 | 管理端 Hero 用深蓝灰建立「报告」心智 | 色值一致，比例不同 |
| 卡片与分割线 | 卡片 `--r-lg` + `--e1` 承载「一个可操作对象」；卡内分隔一律 1px `--border-line`，**不用阴影分层** | PC 后台卡片 `--e0` + 1px 描边（避免密集表格噪声） | Token 一致，策略不同 |
| 危险色语义 | 只给「需要立刻行动」（超时、异常件、未读），不做装饰 | 同 | 是 |

### 2.2 必须一致（不可分叉）的锚点

`--color-primary`(#0958D9 承载白字) / `--color-primary-icon`(#1890FF 仅图标线描边) / 语义四族色 / 状态色映射表（状态 → `--state-*`）/ 六态字典 `variant` 语义 / 字号阶梯名与行高 / 间距 `--sp-*` / 圆角 `--r-*` / 阴影 `--e0..e4` / 图表系列色（`--chart-*`）/ `StatusTag` 形态（soft/outline/solid）/ 动效时长与缓动。

### 2.3 员工端与管理端共用组件时的差异化边界

同一组件两端复用（`StatCard` / `StatusTag` / `SlaTag` / `PageState` / `ActionBar` / `MeSection` / `NoticeList` / `PayrollStatusSteps` / `MessagePage`）时，**只允许通过显式 prop 表达差异**（如 `StatCard` 的 `value-size`、`MeSection` 按 `auth.isAdmin` 分流分组），**禁止**在组件内写 `if (isStaff)` 式的隐式分支散落在样式里。`staff/kpi.vue` 当前用 `isBossView` 只切了标题（`:59`）没切字号（`:113`），是本条的现存反例（P1-2）。

---

## 3. Design Tokens 增补清单

落层规则（重申）：`shared/styles/tokens.base.scss` **只放两端含义与取值完全一致**的内容；移动端私有（字号阶梯、密度、Vant 变量、安全区）放 `mobile/styles/tokens.scss`。取值不同者不得进真源。

### 3.1 新增 Token（9 条）

| # | Token | 值 | 所在层 | 消费方（现状依据） | 为什么必须新增（不是「顺手加」） |
| --- | --- | --- | --- | --- | --- |
| **N1** | `--touch-min` | `44px` | `mobile/styles/tokens.scss` → L2 移动端特有 | 全部交互控件的最小高度：`components/PageState.vue:113-114`、`mobile.scss:311-315`、`staff/parcel.vue:212,222`、`staff/workorder.vue:243`、`staff/attendance.vue:736,750,870,904,938`、`components/ActionBar.vue:122` 等 20+ 处字面量 | 44 是项目的硬性触控下限，却是全项目出现频率最高、最无名的字面量；**P0-3 的漏网（`van-button size="small"` 32px）正是因为没有单一最小值可引**。放移动层而非真源：PC 的最小值语义不同（24/32），同一个名字两端取值不同，属合法平台差异 |
| **N2** | `--row-h-1` | `48px` | `mobile/styles/tokens.scss` → L2 移动密度族 | `mobile.scss:217-226`（`.list-item` 现为 64）、`--van-cell-vertical-padding` 派生行高、`components/TodoGroup.vue:117` | 7.3 密度表（48/64/76/88）是移动端独有的密度约定，**四个值当前全是字面量**，且骨架高度各自硬编码成 96/108/120/148/156（P1-5）。收口后骨架 = 行高 × 行数，删掉六处魔法值 |
| **N3** | `--row-h-2` | `64px` | 同上 | `mobile.scss:218`、`:229-231`（`.list-item--rich` 76） | 同上 |
| **N4** | `--row-h-3` | `76px` | 同上 | `mobile.scss:218`、`:229-231`（`.list-item--rich` 76） | 同上 |
| **N5** | `--row-h-tile` | `88px` | 同上 | `mobile.scss:350-352`（宫格项）、`components/StatCard.vue:80` | 同上（88 在宫格与指标卡两处复用，必须单点） |
| **N6** | `--fs-badge` | `10px` | `mobile/styles/tokens.scss` → L3 Component（与既有 `--tag-h`/`--tag-pad-x` 同族） | `components/QuickGridItem.vue:97`、`layout/TabbarLayout.vue:154`、`views/message/MessagePage.vue:75`、`components/NoticeList.vue:242` | 徽标字号 10px 出现 4 次，而字号阶梯最小档 `--fs-micro` 为 11px 且注释已被占用（`tokens.scss:40` 写「仅限图表轴标签与徽标计数」，一名两用）。**决策**：保留 10px（tabbar 50px 高度内的密度需要）但独立命名，并把 `--fs-micro` 的注释收窄为「仅图表轴标签」 |
| **N7** | `--badge-h` | `16px` | 同上 | `components/QuickGridItem.vue:94-95`、`views/message/MessagePage.vue:71-72`、`components/NoticeList.vue:237-238` | 与 N6 同组；三套徽标实现的高度/min-width 完全同值却各写一遍（P1-4） |
| **N8** | `--badge-pad-x` | `4px` | 同上 | `views/message/MessagePage.vue:73`、`components/NoticeList.vue:239`（均为 `padding: 0 4px`） | 同上 |
| **N9** | `--shift-bar-w` | `4px` | `mobile/styles/tokens.scss` → L3 Component | `staff/attendance.vue:635-640`、`staff/schedule.vue:174-178` | 「班次色条」是同一条视觉元素在打卡页与排班页各写一份（宽 4px、圆角 `--r-xs`），需单点收口；与 P1-1 的色值映射配套 |

### 3.2 语义纠正（不新增，改用法）

| # | Token | 现状误用 | 纠正 | 依据 |
| --- | --- | --- | --- | --- |
| **C-1** | `--fs-num-lg-staff` | `staff/kpi.vue:113` 员工视角误用 `--fs-num-lg-boss`（24px） | 员工视角必须用 `--fs-num-lg-staff`（22px）；两端差异只由 `value-size` 类 prop 表达 | `tokens.scss:41-42`、`demo-ui-redesign.md` 3.2 |
| **C-2** | `--text-3` | 被用在页面底色 `--surface-page` 上（`.tip`/`.tool-row`），实算 4.5046:1，余量 0.0046 | **收窄使用面**：`--text-3` 仅允许落在 `--surface-card`(#FFFFFF, 4.83) 与 `--surface-sub`(#FAFBFC, 4.67) 上；落在 `--surface-page`/`--surface-sunken`/`--color-danger-surface` 这三种底上时改用 `--text-2`（分别 7.04 / 6.61 / 6.12） | §8.1 实算 |

### 3.3 明确**不新增**的项（登记，防止实现时顺手加）

| 项 | 为什么不需要 |
| --- | --- |
| Hero 副信息色 | `rgba(255,255,255,.82)` 只在 `mobile.scss:191-196` 一处；无重复即无收敛收益（但需按 P1-12 修正注释数字） |
| 「1/2/3 行列表项」之外的密度档 | 7.3 表已覆盖全部现存场景，不加 `--row-h-4` |
| chip 高度 Token | 交互 chip 高度 = `--touch-min`（N1），不另设 |
| `--skeleton-*` 尺寸 Token | 骨架尺寸 = 行高 × 行数（N2-N5），尺寸本身不该有独立 Token |
| 班次色 Token（按班次名） | 班次名是自由文本，按名建 Token 会与契约耦合；改走「契约色 → Token 映射表」（§4 C4） |

---

## 4. 组件规范

### 4.1 既有 26 组件状态矩阵补全

约定：`✓` = 已覆盖且正确；`△` = 覆盖但需按本规范调整；`✗` = 缺失需补；`—` = 显式不适用（合法）。原子组件（无远程取数）的 loading/empty/error 由父级表达，属合法。

| 组件 | 分层 | 默认 | 加载 | 空 | 错误 | 禁用 | 无权限 | 边界 | 需处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `PageState.vue` | Organism | ✓ | ✓（200ms 防抖，`:21-39`） | ✓ | ✓（`role="alert"` + 重试，`:53-58`） | — | ✗（无 `denied` 变体，`staff/flow.vue:92-96` 手写 card 替代） | △ | **补 `variant="denied"`**，收口 `flow.vue` 的手写块 |
| `StatusTag.vue` | Atom | ✓ | — | ✓（无匹配渲染 `—`，`:133`） | — | — | — | ✓ | 无（映射表唯一真源，`:32-98`） |
| `SlaTag.vue` | Atom | △（可见文案口径 P1-15） | — | ✓（`:46-49` 正常态纯文本） | — | — | — | ✓（终态隐藏 `:51`） | 统一文案口径 + `gap:3px` 登记（P2-2） |
| `StatCard.vue` | Molecule | ✓ | ✓（数值位骨架 `:61`） | — | ✓（`—` + 原因 `:66-71`） | — | — | ✓ | 无（`clickable` 判定 `:29` 已防假按钮） |
| `PageNav.vue` | Organism | ✓ | — | — | — | — | — | ✓（标题 60% 省略 `:43-46`） | 无（返回 44×44 + `aria-label`，`:54-59`） |
| `ActionBar.vue` | Organism | ✓ | ✓ | ✓（`actions` 空则整栏不渲染 `:40`） | — | ✓（`note` 说明原因 `:46`） | ✓ | ✓（multi 收「更多」） | 按钮高度接 `--touch-min` |
| `AttendanceStatusBar.vue` | Organism | ✓ | ✓（`···` + 按钮禁用） | ✓（未配规则 `:54-55`） | ✓（`role=status` + 重试 `:52,69`） | ✓（降级为次操作 `:70-71`） | ✓（管理端不渲染） | ✓（2 段 4 卡） | `border-left:3px` → §3/P2-2 |
| `HomeQuickGrid.vue` | Organism | ✓ | ✓（项名先渲染 `:42-53`） | △（`nav` 内一行文案 `:55`） | ✓（整体不进错误态，逐项降级） | — | ✓（`canAccess` 过滤 `:32`） | ✓ | 无 |
| `QuickGridItem.vue` | Molecule | ✓ | ✓（`···` / 不渲染角标 `:33-37`） | ✓（业务零值由调用方给文案） | ✓（`—`，绝不用 0 `:36`） | — | — | ✓（`99+`、名称换行） | 角标改 `Badge`（P1-4）；`line-height:1.4` 收口 |
| `TodoList.vue` | Organism | ✓ | ✓（3 块骨架 `:27-29`） | ✓ | ✓（逐组降级 `:49-52`） | — | ✓（父级过滤） | ✓ | 空态用 `PageState` 但未传 `variant`，与 C1 一致 |
| `TodoGroup.vue` | Molecule | ✓ | ✓ | ✓（空组不渲染 `:22`） | ✓（组内重试 `:49-52`） | — | — | ✓（`aria-label` 带分组名 `:32`） | 骨架高度接 `--row-h-1` |
| `NoticeList.vue` | Organism | ✓ | ✓ | ✓（区分全部/未读 `:44`） | ✓ | ✓（未读为 0 时置灰 + `title` `:171-172`） | — | ✓ | **Tab 键盘（P0-6）**；徽标改 `Badge` |
| `MessagePage.vue` | Template | ✓ | 委托子组件 | 委托 | 委托 | — | — | — | 同上（Tab 键盘） |
| `MeSection.vue` | Organism | ✓ | — | — | — | — | ✓（按角色渲染分组 `:49-75`） | △（分组已 9~10 项，P1-13） | 分组内二次分群；`inset` 统一（P1-8） |
| `IdentitySwitcher.vue` | Organism | ✓ | ✓（`van-loading` 14px 内联） | — | ✓（Toast） | ✓（`switching` 时禁点 `:66`） | — | ✓（`aria-busy` `:56`） | 内联 spinner 属按钮内微型指示，保留（与 P1-7 的整页转圈不同） |
| `TabbarLayout.vue` | Organism | ✓ | — | — | ✓（角标静默降级 `:31`） | — | ✓（按角色换 tab 集 `:29`） | ✓（`99+`） | ✓ 已补 `aria-current` + Enter 兜底（`:98-100`）——**这是全项目 Tab 键盘可达的正确范本** |
| `FilterChips.vue` | Molecule | ✓ | — | — | — | — | — | ✓ | 合并 `makeupList` 的 `.fchip`（P1-3） |
| `MonthPicker.vue` | Molecule | ✓ | — | — | — | — | — | ✓（横向滚动不折行 `:36-43`） | 与 `FilterChips` 视觉对齐（P2-5） |
| `MyPayrollCard.vue` | Organism | ✓ | ✓（`:37`） | ✓（`payroll` 空兜底 `:38-40`） | ✓（委托 PageState） | — | — | ✓（折叠 `maxVisible` `:29`） | 骨架高度接 `--row-h-3` |
| `PayrollStatusSteps.vue` | Organism | ✓ | — | ✓（`steps` 空不渲染 `:31`） | 委托 | ✓（`muted` `:150-154`） | ✓（只读） | ✓ | `top:3px`/`2px` 收口（P2-2 同类） |
| `KpiGauge.vue` | Atom | ✓ | ✓（同尺寸骨架 `:35`） | ✓（灰环 + `—`） | 委托 | — | — | ✓（>100% 封顶 `:26`） | 无 |
| `KpiIndicatorCard.vue` | Organism | ✓ | ✓（`:43`） | ✓（`:44-46`） | ✓ | — | — | ✓（名省略、条封顶 `:91-99`） | 骨架高度接行高 Token |
| `LeaveAudit.vue` | Molecule | ✓ | — | ✓（未审批不渲染 `:23`） | — | — | — | ✓ | 无 |
| `LeaveApprovalList.vue` | Organism | ✓ | ✓（自绘，需并入 PageState，P1-6） | ✓ | ✓ | ✓（`canAct` `:134`） | ✓ | ✓ | 计数条 0 问题（P0-2） |
| `WorkOrderCopyButton.vue` | Atom | ✓ | ✓（`busy` 防连点 `:30-34`） | — | ✓（失败双通道提示 `:38-39`） | ✓ | — | ✓（44×44 `:63-68`） | 无 |
| `StationPicker.vue` | Organism | ✓ | — | ✓（`allowAll`） | — | — | — | ✓（48px `:67`） | 无（管理端专用，员工端不消费） |
| `LineChart.vue` | Organism | ✓ | ✓ | ✓ | ✓ | — | — | ✓ | **指针/键盘读数（P1-10）**；管理端/趋势页消费，员工端不消费 |

### 4.2 需新增的组件（9 个）

命名遵循 `demo-pc-refactor.md` §3 的契约范式：PascalCase 多词、props 进 / emits 出、展示组件不 import store/api、单文件 ≤300 行。

#### C1 `Chip`（Atom，`mobile/components/Chip.vue`）

- **职责**：可点筛选/切换胶囊，替代 `mobile.scss:311-334` 的 `.chip` 与 `staff/makeupList.vue:138-155` 的 `.fchip`。
- **Anatomy**：`文字` + `可选前置图标`。
- **props / emits**：`{ label: String, active: Boolean, tone: 'neutral'|'danger', icon: String }` / `emit('click')`。按下 `aria-pressed` 由组件内绑定。
- **四态**：默认 ✓ / 加载 —（父级处理）/ 空 —（不渲染零项组）/ 错误 — ；选中为**视觉变体**不是状态。
- **变体**：`neutral`（默认）/ `danger`（超时类筛选，`--color-danger` 系）。
- **尺寸**：`min-height: var(--touch-min)`，`padding: 0 var(--sp-4)`；选中态 = 品牌浅底 + 500 档描边 + 文字 700 档（现有 `chip--active` 取值即此）。
- **无障碍**：`role="group"` 由容器（`FilterChips`）承担；每项 `aria-pressed`；`--touch-min` 热区；键盘 Enter/Space 原生 `button` 已有。

#### C2 `Badge`（Atom，`mobile/components/Badge.vue`）

- **职责**：统一角标/计数徽标，替代 `QuickGridItem.vue:90-103`、`MessagePage.vue:69-81`、`NoticeList.vue:235-247` 三套实现。
- **Anatomy**：`数字`（`dot` 变体下为 `圆点`）。
- **props**：`{ value: [Number, String], dot: Boolean }`。内部复用 `utils/format.js:80-84` 的 `badgeText`（`0`/空/非数 → 不渲染；`>99` → `99+`）。
- **四态**：默认（有值）/ 加载（父级留空，**不显示骨架、不显示 0**）/ 空（`0` 不渲染）/ 错误（不渲染，不用 `!`）。
- **尺寸**：`min-width: var(--badge-h)`、`height: var(--badge-h)`、`padding: 0 var(--badge-pad-x)`、`font-size: var(--fs-badge)`、`--r-full`、底 `--color-danger`、字 `--text-on-dark`。
- **无障碍**：徽标本体 `aria-hidden="true"`（数值语义由宿主元素的 `aria-label` 承担，见 `QuickGridItem.vue:40-44` 的正确做法）。

#### C3 `MiniChip`（Atom，`mobile/components/MiniChip.vue`）

- **职责**：**非交互**的属性标记胶囊（规则启停、口径标记），替代 `staff/attendance.vue:826-836` 的 `.verify__badge` 与 `:881-891` 的 `.mini-chip`。
- **为什么不用 `StatusTag`**：`StatusTag` 的语义是「业务状态流转中的态」（有字典、有 `variant` 三档）；本组件表达「某项校验是否启用」这一静态属性。与 `--state-simulate-*` 拆出 `--state-warning-*` 的理由同构——语义不同不可共用一组变量。
- **props**：`{ text: String, on: Boolean, tone: 'neutral'|'success'|'warning'|'danger' }`。
- **尺寸**：`height: var(--tag-h)`、`padding: 0 var(--tag-pad-x)`、`font-size: var(--fs-micro)`——**复用既有 `--tag-*`，不新增 Token**。
- **状态**：`on=false` 用中性色 + 1px `--border-line`；`on=true` 用语义浅底。仅静态两态。

#### C4 `ListItemCard`（Molecule，`mobile/components/ListItemCard.vue`）

- **职责**：把 `mobile.scss:217-281` 的 `.list-item` BEM 类固化为组件，**并统一行点击的键盘语义**。
- **Anatomy**：`标题行`（标题 + 右侧标签槽）+ `元信息行 ×N` + `标签组` + `可选左侧标记条（--marked/--failed）`。
- **props / emits**：`{ clickable: Boolean, to: [String,Object], marked: Boolean, failed: Boolean, density: 2|3 }` / `emit('click')`。
- **为什么必须成组件**：目前「行点击 + `role="button"` + `tabindex` + Enter + Space」这四个动作在 `staff/parcel.vue:185-189`、`staff/workorder.vue:153-157`、`staff/sync.vue:154-158`、`NoticeList.vue:200-204`、`staff/payroll.vue:87-90`（**缺 Space**）五处重复书写，且已经出现不一致（P2-3）。
- **四态**：默认 / 加载（骨架，由父级按 `--row-h-*` 出）/ 空 — / 错误 —；`density` 决定最小高（`--row-h-2` / `--row-h-3`）。
- **无障碍**：可点时根元素为 `<router-link>`（首选）或 `role="button" tabindex="0"` + Enter/Space；行内独立操作（如复制）必须是兄弟节点而非子节点（`staff/workorder.vue:178-179` 的注释即此理，固化为组件约束）。

#### C5 `ClockHero`（Molecule，`mobile/components/ClockHero.vue`）

- **职责**：打卡页顶部时钟 + 日期 + 站点（`staff/attendance.vue:351-354` + `.clock` 样式 `:609-627`）。
- **props**：`{ time: String, dateText: String, stationName: String }`；秒级刷新由页面时钟驱动。
- **无障碍**：`role="timer"` + `aria-label="当前时间 HH:mm:ss"`（沿用 `staff/attendance.vue:352` 的写法）；**注意**：`role="timer"` ≠ `aria-live`，秒级不播报，符合现有取舍。
- **状态**：无远程取数；加载/空/错误不适用（时钟恒有值）。

#### C6 `CheckSlotRow`（Molecule，`mobile/components/CheckSlotRow.vue`）

- **职责**：打卡时段内的**单个打卡槽位**（上班卡/下班卡一行），从 `staff/attendance.vue:389-432` 抽出。
- **Anatomy**：`左侧文本区（卡类型 + 状态文案）` + `右侧操作区（主按钮 或 主按钮+次链接）`。
- **props / emits**：`{ periodName, checkType, state: {key,text}, submitting: Boolean, disabled: Boolean }` / `emit('check')`、`emit('makeup')`。
- **状态矩阵（业务 6 态，映射自 `staff/attendance.vue:125-143` 的 `itemState`）**：

| 态 | 判定 | 右侧操作 | 视觉 |
| --- | --- | --- | --- |
| `done` | 该槽位已打卡（优先于审批中，`:123`） | 无按钮 | 状态文案 `--color-success` |
| `pending` | 已提交补卡待审批 | 禁用按钮「审批中」 | 状态文案 `--color-primary` |
| `wait` | 未到开放时间 | 主按钮「上班/下班打卡」（禁用？见下） | 状态文案 `--text-3` |
| `todo` | 可打卡 | 主按钮「上班打卡」/描边「下班打卡」 | 状态文案 `--color-warning` |
| `overdue` | 已过规定时刻未打卡 | 主按钮（补打，表单 `:412-421`）+ 次链接「申请补卡」 | 状态文案 `--color-warning` |
| `missed` | 窗口已关 | 按钮换成「申请补卡」（`:398-405`） | 状态文案 `--color-danger` |

- **四态（数据维度）**：loading = 主按钮 `loading` 且全槽位 `disabled`（`:415-416`）；empty = 时段列表为空 → 由页面出引导卡（`:376-378`）；error = 由 `PageState` 承担；normal = 上表。
- **尺寸**：行 `min-height: 56px`（`staff/attendance.vue:685`）；主按钮 `min-width: 104px`、`min-height: var(--touch-min)`；次链接同样 44。
- **无障碍**：主按钮 `aria-label` 必须含「时段名 + 卡类型」（`:401,417,426` 的既有做法）；提交中 `aria-busy`。

#### C7 `CheckResultPanel`（Molecule，`mobile/components/CheckResultPanel.vue`）

- **职责**：就近渲染的打卡判定结果（`staff/attendance.vue:435-458`）。
- **props**：`{ result }`（`{ ok, periodName, checkType, status, checkTime, checkMode, distance, remark, hint, abnormalLogged }`）。
- **四态**：`ok` 成功（success 描边 + `StatusTag`）/ `fail` 失败（danger 描边 + 针对性 hint）/ 空（`result` 为 null 不渲染）/ 加载 —。
- **无障碍**：容器 `role` 按结果切换 `status` / `alert`（沿用 `:439`）；色点之外必须有文字（`:443-446` 已有）。
- **尺寸**：`border-left: 3px` → 调整见 P2-2。

#### C8 `VerifyCard`（Organism，`mobile/components/VerifyCard.vue`）

- **职责**：打卡前的「自查卡」通用骨架——**打卡页三张同构卡**（Wifi `:467-481`、定位 `:484-531`、规则摘要 `:534-555`）共用。
- **Anatomy**：`卡头（标题 + MiniChip 标记）` + `主值行` + `Caption 明细行 ×N` + `默认插槽（定位卡的按钮与演示辅助行）`。
- **props**：`{ title: String, value: String, badge: { text, tone } }`。
- **四态**：默认 / 加载（主值位 `···`，与 `AttendanceStatusBar` 同口径）/ 空（主值「未获取到」）/ 错误（`role="alert"` 的 danger 行 + 重试，沿用 `:509-512`）。
- **为什么抽**：三张卡的 DOM 结构与样式块（`.verify__head/__label/__badge/__value/__hint/__btn`，`:813-872`）完全同构，是页内最大的重复面。

#### C9 `ShiftCard`（Molecule，`mobile/components/ShiftCard.vue`）

- **职责**：班次卡（打卡页 `:360-369` 与排班页 `:79-101` 同一条「色条 + 班次名 + 时间窗」视觉）。
- **props**：`{ shiftName, startTime, endTime, restMinutes, colorKey }`。
- **关键约束**：`colorKey` 必须是**语义键**而非契约 hex——组件内按「契约色 → Token」映射表落色（`#0958D9 → --color-primary`、`#FA8C16 → --color-accent`、`#1F2937 → --c-neutral-800`），未命中映射的回落 `--border-line`。这是 P1-1 的落地方式（不新增 Token，只新增映射）。
- **尺寸**：色条宽 `var(--shift-bar-w)`，高 `32px`（`staff/attendance.vue:638` 现值，保留）。
- **无障碍**：色条 `aria-hidden="true"`（班次名与时间窗已承载语义，`schedule.vue:87` 已如此）。

### 4.3 组件改造要点（既有组件）

| 组件 | 改造 | 依据 |
| --- | --- | --- |
| `PageState` | ① 新增 `variant="denied"`（只读降级 + 一行说明，不渲染不可用按钮）；② 骨架高度由 `rows` × `--row-h-*` 派生 | `staff/flow.vue:92-96`、`B0.2` |
| `FilterChips` | 内部改用 `Chip`；销掉自身的 `TODO(扩展)` | `FilterChips.vue:8-9`、`staff/makeupList.vue:138-155` |
| `TabbarLayout` | 保持；把「Enter 兜底」提为全项目 Tab 键盘可达的**范本**（P0-6 照此补 `van-tabs` 页面） | `TabbarLayout.vue:62-67` |
| `AttendanceStatusBar` | 按钮接 `--touch-min`；`border-left` 值改 4px | `:145`、`:195-199` |
| `stat-card` / `list-item` 等全局类 | 高度改引 `--row-h-*`、`--touch-min` | `mobile.scss:155-352` |

---

## 5. 逐页视觉与交互规范

页面分组：① 首页 ② 考勤域 ③ 工单域 ④ 包裹域 ⑤ 我的数据域 ⑥ 请假域 ⑦ 我的与系统。每页给出：页面目标 / 信息层级 / 区块顺序 / 关键交互 / 组件对应 / 适配要点。

### 5.1 首域 · 员工端首页 `staff/home.vue`（206 行）

| 项 | 规范 |
| --- | --- |
| 页面目标 | 一屏内回答「今天要做什么、现在能不能打卡」；到 Tabbar 的距离 ≤1 屏（续 `demo-ui-redesign.md` 3.2） |
| 信息层级 | H1-m（站名）> 数值（22）> 区块标题（14/600）> 正文（14）> Caption（12） |
| 区块顺序（不可乱） | ① Hero（站名 + 角色 chip + 今日待处理 N 条 + 构成说明，`:131-140`）② 出勤状态条 + 一键打卡（`:143`）③ 工单超时提示条（有值才出现，`:145-154`）④ 4 指标卡（`:156-188`）⑤ 快捷宫格（`:193`，**独立于 PageState**，理由见 `:191-192`）⑥ 本站口径行（`:195-197`） |
| 关键交互 | 下拉刷新（`:128`）；一键打卡（`AttendanceStatusBar` → `useCheckIn`）；提示条点击进工单；指标卡点击进包裹/核销；宫格项按其 `type` 展示角标/数据行 |
| 组件对应 | `PageState` + `StatCard`(×4, `value-size="staff"`) + `AttendanceStatusBar` + `HomeQuickGrid`/`QuickGridItem` + `Badge`(新) |
| 适配 | Hero 内两行文字在 320px 下需不换行溢出（`flex-between` + `hero__title` 需 `min-width:0`）；宫格 4 列在 320px 下每格 ≈80px ≥44 |
| 待修 | 宫格 9~10 项已超 4×2，`HomeQuickGrid.vue:16-17` 的 `TODO(扩展)` 需与实际（自动换行左对齐）对齐；P1-14 排序表述 |

### 5.2 考勤域

#### 5.2.1 打卡 `staff/attendance.vue`（972 行，**拆分与重设计粒度最细**）

**现状**：一个文件同时承载时钟、班次、时段打卡、三张自查卡、补卡弹层，样式块 364 行，是员工端唯一的「超级页」。

**拆分后结构（页面壳 ≤150 行，符合 `demo-pc-refactor.md` §3.5）**：

```
views/staff/attendance.vue           页面壳：取数 + 四态 + 区块编排
  components/ClockHero.vue           时钟 + 日期 + 驿站
  components/ShiftCard.vue           今日班次（含契约色→Token 映射）
  components/PeriodCard.vue          【新 Organism】时段卡（表头 + 进度 + N 个槽位 + 就近结果）
  components/CheckSlotRow.vue        单个打卡槽位（6 态）
  components/CheckResultPanel.vue    判定结果（role status/alert）
  components/VerifyCard.vue          自查卡（Wifi / 定位 / 规则 三张共用）
  components/MiniChip.vue            规则启停标记
  components/MakeupPopup.vue         【新】补卡表单弹层（:570-604 + :947-971）
  constants/attendanceUi.js          【新】文案与状态键（periodWindowText 之外的展示常量）
```

**区块顺序**：时钟 → 今日班次 → 打卡时段（N 张 `PeriodCard`）→ 校验状态（Wifi / 定位 / 规则 三张 `VerifyCard`）→ 就近入口三按钮（`:558-566`）→ 补卡弹层。

**关键交互**：
- 每槽位主操作 = 补打（能落真实卡，迟到早退如实记录）；次入口 = 申请补卡（需审批，优先级低）——这是 `:410` 注释确立的优先关系，**拆分不得改变**。
- 判定结果**必须就近渲染在被操作的 `PeriodCard` 内**（`:434`），不得改到页顶或纯 Toast。
- 演示辅助开关：整行可点 + 开关可聚焦（`:515-530`）；开启时定位卡标记变为「演示辅助 · 围栏中心」且用 `verify__badge--ok` 而非 warn（`:487-489`）。
- 补卡弹层：理由 2–200 字，`disabled = reason.trim().length < 2`（`:597`），失败在弹层内 `role="alert"` 渲染（`:589`）。

**重设计要点**：
1. **`--fs-clock` 40px 为 L3 组件私有尺寸**（`tokens.scss:45-47`），保留不动；不得顺手把它提到通用阶梯。
2. `MiniChip` 覆盖 `.mini-chip` 与 `.verify__badge`（P1-3），语义色三态（`--badge-*`）收敛。
3. `border-left: 3px`（判定结果、`:762`）统一为 4px（P2-2），与 `--shift-bar-w` 同栅格。
4. 补卡弹层与 `sync.vue` 的日志弹层、`workorderDetail.vue` 的备注弹层结构同构（标题 + 表单 + 错误 + 底部按钮），**抽 `BottomSheetForm`** 作为这些弹层的统一骨架（若本轮不做，登记 `TODO(扩展)` 并保持四者样式一致）。
5. 页面底部三按钮（我的排班/打卡记录/补卡申请，`:558-566`）保留：它们与宫格/我的页是「就近入口」，按 `demo-mobile-nav-redesign.md` B5-4 不视为重复入口。

**适配**：时钟行在 320px 下 `--fs-clock` 40px 仍可容纳 `HH:mm:ss`（约 8 字符 × ~22px = 176px）；时段卡在 4 卡场景（2 段）行高不塌；演示辅助整行 ≥44。

#### 5.2.2 打卡记录 `staff/attendanceRecords.vue`（247 行）

- 目标：按月查「每一天哪几段、几点上下班、缺卡还是异常」。
- 层级：月份导航卡 → 统计行（出勤/迟到/早退/异常）→ 日期卡列表（3 行结构 + 时段分组）。
- 交互：上/下一月（44×44 + `aria-label`，`:102-113`）、回到本月（`:115`）、时段分组按 `periodIndex` 排序（`:42`）。
- 组件：`PageState` + `StatusTag` + `ListItemCard`(新) + `Chip`(新，替换月份导航的两个自绘按钮的样式对齐)。
- 适配：`month-nav__sub` 六个统计项在 320px 下会换行——允许换行（现状 `:198-203` 无 `nowrap`，正确）。
- 待修：`period__times` 长时在窄屏换行已由 `:229-232` 的 `flex-wrap` 处理，保持。

#### 5.2.3 我的排班 `staff/schedule.vue`（190 行）

- 目标：一周一屏看「哪天上班、什么班次」。
- 交互：整周平移（周首固定周一，`:36-40`）、回到本周（`:67`）、今日高亮走「主色描边 + 今天角标」双通道（`:169-172`）。
- 组件：`PageState` + `ShiftCard`(新，替换 `:79-101` 内联块) + `Chip`(新)。
- 待修：`day.color` 直出（P1-1）；`.week-nav__btn` 与 `attendanceRecords.vue:172-184` 的 `.month-nav__btn` 是同一样式两处写——抽 `StepNavButton`（或归入 `Chip` 的 outline 变体）。

#### 5.2.4 我的补卡申请 `staff/makeupList.vue`（171 行）

- 目标：查补卡进度与审批意见。
- 交互：状态筛选（`:84-96`）、无限滚动、审批中给「等管理员审批」的明确预期（`:121`）。
- 待修：① `.fchip` 改 `FilterChips`（P1-3）；② 自绘骨架并入 `PageState`（P1-6）；③ 筛选 `role="group"` 已正确（`:84`）。

### 5.3 工单域

#### 5.3.1 工单列表 `staff/workorder.vue`（264 行）

- 目标：扫一眼「哪条要处理、哪条已超时」。
- 区块顺序：NavBar → 状态 Tab（5 项，通栏）→ 工具行（共 N 条 + 仅看超时 chip）→ 列表 → FAB。
- 交互：Tab 切换、超时筛选（`:122-130`，`aria-pressed`）、下拉刷新、上拉加载、新建返回高亮 1.6s（`:25`）、行内复制（行外兄弟节点，`:178-179`）。
- 待修：**Tab 键盘（P0-6，必须补）**；`padding-right:52px`（P2-1）改由 `ListItemCard` 的 `trailing` 槽位表达。
- 适配：FAB `right: max(--sp-4, calc((100vw-480px)/2 + --sp-4))`（`:237`）为宽屏内推，**不得改成固定 16px**；横屏档 `:259-263` 同步为 640 口径。

#### 5.3.2 工单详情 `staff/workorderDetail.vue`（563 行）

- 目标：看清工单全貌并一步完成流转；管理端与员工端共页（`:16-24`）。
- 区块顺序：摘要卡 → 工单信息（cell-group）→ 工单描述（`line-height:1.7`，`:414`）→ 处理时间线（最新在上，`:110-112`）→ 转单留痕（有值才出现）→ 无权限说明。
- 交互：底部 `ActionBar`（`multi` 自动收「更多」）、备注弹层（必填校验）、指派/转单共用人员弹层（每次打开重拉候选，`:178-205`）。
- 待修：`line-height:1.7` → 用 `--lh-body` 或登记 L3；人员弹层改 `role="listbox"` 或保持 `aria-pressed` 按钮组（现状合法，登记口径）。

#### 5.3.3 新建工单 `staff/workorderCreate.vue`（238 行）

- 目标：30 秒内提交一条工单。
- 交互：2×2 卡片式类型单选（`:90-104`）、3 列优先级（`:110-124`）、字段级校验、底部 `ActionBar`、防重复提交。
- 待修：`font-size:12px`（`:221` 勾选图标）→ `--fs-caption`；`.choice` 的 48px 高与 `--touch-min` 统一。

### 5.4 包裹域

#### 5.4.1 本站包裹 `staff/parcel.vue`（236 行）

- 目标：找包裹（按运单号）或扫待取件队列。
- 交互：搜索（精确匹配）、状态 Tab、下拉刷新、无限滚动、48h 未取件警示。
- 待修：**P0-4（占位符对比度）必须处理**；**Tab 键盘（P0-6）**；`empty-reset` 空态按钮与 `PageState` 的 `empty-action` 槽配合正确（`:170-172`），保持。

#### 5.4.2 包裹详情 `staff/parcelDetail.vue`（152 行）

- 目标：核销前确认；管理端只读（`:130`）。
- 交互：`ActionBar dual`（取件核销 + 上报异常），禁用原因由 `note` 承载而非塞进按钮（`:31-36`）。
- 待修：`cell-group inset` 与 `attendance/sync` 不一致（P1-8）。

#### 5.4.3 取件核销 `staff/pickup.vue`（289 行）

- 目标：一手持机连续核销。
- 交互：进入即聚焦输入（`:117-119`）、回车即查（`:135`）、查询/扫码 2:1 布局、演示运单号热区 44（`:226-239`）、页内警示条替代 3 秒 Toast（`:39-44,153-155`）、成功后焦点回输入框（`:86-87`）。
- 待修：**P0-7（查询失败无错误态）**；`--van-cell-vertical-padding:10px`（P1-9）；`.`pickup__done` 18px（P2-1）→ `--fs-num-md`；`.pickup__guide-icon` 40px → 与 `PageState` 空态图标口径统一（40/48 两档需定一档）。

### 5.5 我的数据域

#### 5.5.1 我的 KPI `staff/kpi.vue`（130 行）

- 目标：看「这分怎么来的」。共享管理端（`/boss/kpi/:employeeId`）。
- 层级：Hero（员工/驿站/月份 + 大号总分 + 等级 + 排名）→ KpiGauge → 口径行 → 指标明细卡。
- 待修：**P1-2（字号越级）**；`MonthPicker` 顶部无「考核周期」标签时的读屏名依赖 `label` prop（`:61` 已传），保持。

#### 5.5.2 我的工资单 `staff/payroll.vue`（121 行）

- 交互：无限滚动；待确认单据行上方高亮（`:93`，文字 + 位置双通道）。
- 待修：**P0-2（计数条 0 冒充未知）**；自绘骨架并入 `PageState`（P1-6）；`keydown` 补 Space（P2-3）。

#### 5.5.3 工资单详情 `staff/payrollDetail.vue`（244 行）

- 区块顺序：Hero（月份 + 实发大号数）→ 流转步骤条 → 构成明细（`maxVisible=5` + 展开）→ 计算说明（cell-group）→ `ActionBar`（确认无误 / 提异议）。
- 交互：两个动作均二次确认且文案说清不可逆性（`:84-105`）；异议必填 2–200 字；成功后回列表（`:126`）。
- 待修：`blocked`（9403 未发布）当前并入 `empty`（`:144-150`），需与「错误态」明确区分（`error` 与 `blocked` 同时为空才走 empty，现状正确，登记口径）。

#### 5.5.4 我的档案 `staff/profile.vue`（163 行）

- 待修：**P0-8（同页 44 vs 28 缩进）**；薪资构成卡内 `van-cell` 需收敛水平内边距（`:97-110`）；调薪时间线复用全局 `.timeline__*`（`:121-127`），保持（但 `.timeline__dot` 7px 见 P2-2）。

#### 5.5.5 我的入离职 `staff/flow.vue`（106 行）

- 明确为「无权限只读降级」页（`:9-21`）：只展示在职信息 + 离职结算单 + 一段说明，**不渲染不可用按钮**。
- 待修：流程进度说明改用 `PageState variant="denied"`（P1-8/§4.3），避免第三处手写说明卡。

### 5.6 请假域

| 页 | 目标 | 关键交互 | 待修 |
| --- | --- | --- | --- |
| `leaveApply.vue`（423 行） | 三入口共用一份表单（新建/修改/重提） | 类型与日期弹层选择、半天选择（±44 触控）、300ms 防抖试算（`:25,205-211`）、天数摘要双值同显、跨年提示 | `van-radio` 键盘可达待复核（U2）；`:396` 注释对比度数字修正（P1-12）；弹层骨架与 `BottomSheetForm` 统一 |
| `leaveList.vue`（364 行） | 查进度 + 撤销/修改 | 状态筛选、行内操作按服务端派生标志渲染（`:187`）、就地更新（`:97-107`）、撤销二次确认（`:119-129`） | 骨架并入 `PageState`（P1-6）；骨架高度接行高 Token（P1-5） |
| `leaveReview.vue`（18 行） | 站长初审挂载点 | 复用 `LeaveApprovalList`（`actor="STATION"`） | 计数条 0 问题（P0-2）在 `LeaveApprovalList.vue:196` |

### 5.7 我的与系统域

#### 5.7.1 我的 `staff/me.vue`（9 行）+ `MeSection.vue`

- 结构：Hero → 「我的数据」（11 项，`:57-75`）→ 账号信息 → 演示身份（Demo 专用）→ 账号安全 → 运行环境 → 退出登录。
- 待修：**P1-13 重排**——建议「我的数据」拆为两群：**① 薪酬与考核**（我的 KPI / 我的工资单 / 我的档案）**② 考勤与流程**（我的排班 / 打卡记录 / 我的补卡申请 / 我的请假 / 我的入离职），站长附加项（请假初审 / 同步状态）置于对应群末。两群之间用 `--sp-6` 而非 `--sp-3`，以建立视觉分组。
- 待修：`inset` 统一（P1-8）；运行环境保持 Caption 降级（`:114-118`）。

#### 5.7.2 修改密码 `staff/password.vue`（122 行）

- 表单三字段 + 字段级校验 + 全局兜底（`:87`）；成功后强制重登（`:44`）。
- 无数据取数 → 四态中仅「normal/error（提交失败）」，属显式不适用，登记即可。
- 待修：`--van-cell-horizontal-padding: 0`（`:109`）的写法应与 `profile.vue` 收敛为同一约定（P0-8）。

#### 5.7.3 同步状态 `staff/sync.vue`（292 行）

- 目标：站长只看本站采集/同步是否正常，重试在 PC 端。
- 区块：采集状态卡（独立三态，`:25`）→ 最近批次卡 → 历史批次列表 → 日志弹层。
- 待修：**P0-1（错误吞成空态）**、**P0-3（32px 按钮 ×2）**、P1-7（转圈）、`cell-group inset` 统一。

---

## 6. 交互动效与反馈规范

### 6.1 时长与缓动上限（沿用既有 Token，不新增）

| 场景 | 时长 | 缓动 | 依据 |
| --- | --- | --- | --- |
| 按钮/卡片按下 | `--dur-fast`(120ms) | `--ease-std` | `StatCard.vue:90-95`（`transform: scale(.985)`）；列表行按下的延迟与高亮抑制见 `mobile.scss:217-226` |
| Tab 指示器 / 分组展开 | `--dur-base`(200ms) | `--ease-std` | — |
| 底部弹层 / 页面切换 | `--dur-slow`(300ms) | `--ease-in`（进）/`--ease-out`（出） | — |
| 骨架 → 内容 | 200ms 淡出 | `--ease-std` | 消除跳变 |
| 高亮（新建工单返回） | 1600ms | `--ease-out` | `mobile.scss:283-297` |
| 步骤条进行中脉冲 | 1.6s 循环 | `--ease-std` | `PayrollStatusSteps.vue:100-113` |

**硬上限**：装饰性动画 ≤300ms；任何 >400ms 的装饰动画禁止（`demo-ui-redesign.md` 8 章）。**禁止**列表逐项错峰入场、自动播放、视差。
**降级**：`prefers-reduced-motion` 已全局覆盖（`tokens.base.scss:244-253`），新增动效无需重复声明，但**不得**用内联 `animation-duration` 绕过它。

### 6.2 加载

- 骨架优于转圈：**唯一例外**是「按钮内微型指示」（`van-button :loading`、`IdentitySwitcher` 的 14px spinner），整页/整块一律块骨架。
- 快请求防抖：统一由 `PageState.vue:21-39` 的 200ms 实现，**不允许**页面自绘骨架绕过（P1-6）。
- 骨架尺寸 = 真实内容行高（§3 N2-N5），禁止魔法高度（P1-5）。

### 6.3 下拉刷新与乐观更新

- 下拉刷新：`van-pull-refresh`，只允许出现在「列表/看板」根节点（现 4 处：home/parcel/workorder/NoticeList）。
- **乐观更新**：仅在「撤销/审批」这类**幂等且可就地对齐**的场景使用（`leaveList.vue:97-107`、`LeaveApprovalList.vue:144-154`）；必须满足：① 有服务端派生标志兜底；② 失败时 `syncList`/`loadFirst` 就地对齐；③ 筛选与新状态不符时**移出列表**而非留在原地。
- **回滚**：不做「先改后撤」的动画回滚（会造成二次闪烁）；失败即重新取数覆盖。

### 6.4 错误反馈三形态的选用判据

| 形态 | 何时用 | 禁止用在哪 | 现例 |
| --- | --- | --- | --- |
| **页内（PageState error / 页内提示条）** | ① 首屏主数据取数失败；② 用户会「回看」的失败结论（状态不符、找不到单据） | 一次性的动作失败（如提交被拒）——会残留误导 | `PageState.vue:53-58`（正确）、`pickup.vue:153-155`（正确）、**`sync.vue:74-75`（反例，P0-1）** |
| **Toast** | 动作已完成/已提交的瞬时确认（成功）；或「列表已有数据时」的翻页失败 | 首屏失败（3 秒后消失，用户回来看到空页会误判） | `showSuccessToast`（多处正确）；`leaveList.vue:83`（正确） |
| **弹层内联** | 表单/弹层内的字段或提交失败——用户不关弹层就能改 | 需要跳转才能解决的错误 | `attendance.vue:589`、`payrollDetail.vue:199`、`workorderDetail.vue:366`（均正确） |

**硬规则**：
1. **绝不用 `0` 或「空列表」表达失败**（P0-1 / P0-2 的两处反例）。
2. 同一失败不得同时出页内 + Toast（重复噪音）；`http.js` 已统一 Toast 的接口，页面只在「需要给出可操作下一步」时补页内文案（如 `checkErrorHint`）。
3. 错误文案三段式：**现象**（发生了什么）+ **原因**（哪个校验/状态不符）+ **下一步**（重试 / 去哪改）。`checkErrorHint`（`utils/attendance.js`）与 `leaveErrorHint`（`utils/leave.js`）是正例范本。

### 6.5 空态引导文案规范

| 场景 | 文案要求 | 现例 |
| --- | --- | --- |
| 真无数据 | 陈述事实 + 可选引导 | 「暂无通知」「本站暂无包裹记录」 |
| 筛选后无结果 | 必须含**筛选条件** | `workorder.vue:45`「没有超时未处理的工单」（正确） |
| 未开始操作 | 明确下一步动作 | `pickup.vue:147-151`「输入运单号或扫码开始」（正确） |
| 业务未就绪 | 说明由谁配置 | `attendance.vue:344-347`「该驿站尚未配置打卡规则」+ 引导（正确） |
| 无权限 | 只读降级 + 一行说明，不给不可用按钮 | `flow.vue:92-96`（文案正确，容器待改 `PageState variant="denied"`） |

**禁止**：空态与错误态共用文案；空态只用插画无文字；空态文案超过 2 行。

### 6.6 手势

| 手势 | 允许 | 约束 |
| --- | --- | --- |
| 下拉刷新 | 列表/看板根节点 | 刷新中不叠加骨架 |
| 横向滚动 | `MonthPicker`（`:37-43`） | 必须隐藏滚动条且不引出整页横向滚动（`mobile.scss:17` 已兜底） |
| 上拉加载 | `van-list` 20/页 | `immediate-check="false"` 防挂载即触发；单飞保护用独立 `busy` 标记（`parcel.vue:41`） |
| 左滑删除/操作 | **不允许** | `role=button` 行 + 滑动删除与「键盘可达」冲突，且现场戴手套易误触 |
| 图表触摸读数 | `LineChart`（管理端） | 需补指针/键盘路径（P1-10） |

---

## 7. 移动端适配规范

### 7.1 断点与容器

| 档位 | 宽度 | 行为 | 依据 |
| --- | --- | --- | --- |
| xs 最小 | 320px | 4 列宫格每格 ≈80px；时段卡内按钮 `min-width:104px` 需允许换行 | 本规范 |
| 基准 | 375px | 设计基准 | 本规范 |
| 大屏竖屏 | 414px | 内容列 `max-width: 480px` 居中 | `mobile.scss:26-35` |
| 宽屏/折叠屏 | >480px | 固定栏（NavBar/Tabbar/ActionBar）与 `#app` 同宽收口，FAB 按同一口径内推 | `mobile.scss:418-428`、`workorder.vue:237` |
| 横屏 | orientation: landscape | 容器放宽到 640px（source order 覆盖 480 档），不重排布局 | `mobile.scss:430-438`、`workorder.vue:259-263` |

**禁止**在组件内新增 px 媒体查询；新增断点需求一律先加 Token 到移动差异层。

### 7.2 安全区

| 位置 | 机制 | 依据 |
| --- | --- | --- |
| 顶部 | `--safe-top`（= `--status-bar-height`，壳注入，浏览器 0）；`#app { padding-top }` + 固定 NavBar `top: var(--safe-top)` | `mobile.scss:26-29,44-47`、`PageNav.vue:38-41` |
| 底部 | `--safe-bottom: env(safe-area-inset-bottom, 0px)`；Tabbar 自带 + `--page-pad-bottom-tab` 一次给足（**不用 placeholder**） | `tokens.scss:161-168`、`TabbarLayout.vue:114-117` |
| 固定 ActionBar | 内 `padding-bottom: var(--safe-bottom)`；页面根 `.page--bar` 用 `--page-pad-bottom` | `ActionBar.vue:97-111`、`mobile.scss:107-110` |
| FAB | `bottom: calc(var(--safe-bottom) + var(--sp-3))`（二级页无 Tabbar） | `workorder.vue:238` |
| 弹层 | 所有 `van-popup position="bottom"` 必须带 `safe-area-inset-bottom` | 8 处均有（`attendance:570`、`sync:175`、`leaveList:214`、`workorderDetail:322,376`、`payrollDetail:186`、`LeaveApprovalList:243`、`StationPicker:27`） |

**待真机复核**：`--status-bar-height` 实测值（U1）；`env(safe-area-inset-*)` 实际值（U3）。

### 7.3 键盘弹起

| 场景 | 规范 |
| --- | --- |
| 输入框进入即聚焦 | `pickup.vue:117-119`（`nextTick` + 显式 `focus()`，壳内软键盘需显式调用）、`workorderCreate`/`leaveApply` 由 Vant 默认处理 |
| 底部固定栏与键盘 | 键盘弹起时 `ActionBar` 不得遮挡输入框：`van-field` 所在容器需可滚动到可视区；**壳侧 `windowSoftInputMode` 待真机验证**（U6） |
| 弹层内表单 | 弹层内容 `max-height: 56vh~80vh` + `overflow-y: auto`（`sync.vue:257-261`、`leaveList.vue:308-312`），键盘弹起后仍可滚动到底部按钮 |
| `enterkeyhint` | 搜索类输入必须 `enterkeyhint="search"`（`pickup.vue:134`）；表单提交类用 `done` |

### 7.4 长列表策略

| 项 | 规范 |
| --- | --- |
| 分页 | `van-list` 20/页（工单/包裹/通知/工资单/请假/补卡/同步批次一致） |
| 无虚拟化 | 本期不引入（新增依赖需评审）；不达标时的处置见 U5 |
| 单飞保护 | 触底回调用独立 `busy` 标记（`van-list` 会自行置 `loading`），`parcel.vue:41` 为范本 |
| 首屏 | `immediate-check="false"`；首屏由 `loadFirst` 负责，`loadingMore` 初值必须 `false`（`leaveList.vue:43` 注释已明确） |
| 大列表返回定位 | 全局 `scrollBehavior: { top: 0 }`（`router/index.js:356`），Tab 切换也回顶；per-Tab 记忆登记 `TODO(扩展)`（`TabbarLayout.vue:50`） |

### 7.5 iPhone Safari / 安卓 WebView 差异

| 差异 | 现状 | 规范 |
| --- | --- | --- |
| 剪贴板 | `file://` 下 Clipboard API 不可用是常态 | `WorkOrderCopyButton.vue:36-39` 已双通道（`copyText` + 降级提示），保持 |
| 定位 | 浏览器必在围栏外（Mock 坐标虚构） | 显式「演示辅助」开关 + 诚实标注（`attendance.vue:41-44,514-530`），**不得**做成隐形后门 |
| WiFi SSID | 浏览器无标准能力 | `attendance.vue:473-479` 标注「模拟」并说明壳内真实来源，保持 |
| 固定定位 | iOS 键盘弹起时 `position: fixed` 会漂移 | 待真机（U6） |
| 返回键 | 壳返回键复用 history | `PageNav.vue:6-9` + `bridge.js` 的 `onBackPressed`，保持 |
| 状态栏 | 壳注入 `--status-bar-height` | 待真机（U1） |

---

## 8. 无障碍规范

### 8.1 对比度实算（可复现）

**算法**：WCAG 2.x 相对亮度，脚本见下（node 实算，非手算）。半透明前景先按 `fg×α + bg×(1−α)` 合成再算。

```js
// 实算脚本（node -e 或存为 .mjs 执行）
const L = (c) => {
  const p = [0, 2, 4].map((i) => parseInt(c.slice(1 + i, 3 + i), 16) / 255)
    .map((x) => (x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4)))
  return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]
}
const R = (a, b) => ((Math.max(L(a), L(b)) + 0.05) / (Math.min(L(a), L(b)) + 0.05)).toFixed(4)
```

**实算结果（本轮实测，2 位小数）**：

| 组合 | 前景 / 背景 | 实算比 | 要求 | 判定 |
| --- | --- | --- | --- | --- |
| 一级文本 | `#1F2937` / `#FFFFFF` | 14.68 | 4.5 | 通过 |
| 二级文本（白卡） | `#4B5563` / `#FFFFFF` | 7.56 | 4.5 | 通过 |
| 二级文本（页面底） | `#4B5563` / `#F5F7FA` | 7.04 | 4.5 | 通过 |
| 三级文本（白卡） | `#6B7280` / `#FFFFFF` | 4.83 | 4.5 | 通过 |
| 三级文本（次级表面） | `#6B7280` / `#FAFBFC` | 4.67 | 4.5 | 通过 |
| **三级文本（页面底）** | `#6B7280` / `#F5F7FA` | **4.5046** | 4.5 | **通过但无余量**（⇒ §3 C-2 收窄使用面） |
| **三级文本（下沉面）** | `#6B7280` / `#EDF0F4` | **4.2293** | 4.5 | **不通过 → 禁用组合** |
| **三级文本（危险浅底）** | `#6B7280` / `#FFF1F0` | **4.3953** | 4.5 | **不通过 → 禁用组合** |
| 三级文本（警告浅底） | `#6B7280` / `#FFFBE6` | 4.65 | 4.5 | 通过 |
| 主色文字（白底） | `#0958D9` / `#FFFFFF` | 6.16 | 4.5 | 通过 |
| 白字主按钮 | `#FFFFFF` / `#0958D9` | 6.16 | 4.5 | 通过 |
| 主色文字（品牌浅底） | `#0958D9` / `#E8F4FF` | 5.52 | 4.5 | 通过 |
| 危险文字（白底） | `#CF1322` / `#FFFFFF` | 5.57 | 4.5 | 通过 |
| 危险文字（危险浅底） | `#CF1322` / `#FFF1F0` | 5.07 | 4.5 | 通过 |
| 成功文字（白底） | `#237804` / `#FFFFFF` | 5.59 | 4.5 | 通过 |
| 成功文字（成功浅底） | `#237804` / `#F6FFED` | 5.44 | 4.5 | 通过 |
| 警告文字（白底） | `#B45309` / `#FFFFFF` | 5.02 | 4.5 | 通过 |
| 警告文字（警告浅底） | `#B45309` / `#FFFBE6` | 4.83 | 4.5 | 通过 |
| 中性标签字（中性浅底） | `#4B5563` / `#EDF0F4` | 6.61 | 4.5 | 通过 |
| **占位符（白底）** | `#9AA4B2` / `#FFFFFF` | **2.52** | 4.5 | **不通过**（D-1 已知偏差；`parcel.vue` 处豁免理由不成立 ⇒ P0-4） |
| **Hero 副信息** | `rgba(255,255,255,.82)`→`#D3E1F8` / `#0958D9` | **4.66** | 4.5 | 通过（**既有注释写 5.4，需修正** ⇒ P1-12） |
| 图标/线/描边（非文本） | `#1890FF` / `#FFFFFF` | 3.24 | 3.0 | 通过 |
| 控件边界（非文本） | `#8A93A0` / `#FFFFFF` | 3.11 | 3.0 | 通过 |
| 禁用文字 | `#CBD2DA` / `#FFFFFF` | 1.53 | 豁免（SC 1.4.3 禁用态） | 通过（豁免） |
| 分隔线 | `#E3E7ED` / `#FFFFFF` | 1.24 | 豁免（装饰） | 通过（豁免） |

**禁用组合清单（写入实现约定）**：`--text-3` 不得落在 `--surface-page` / `--surface-sunken` / `--color-danger-surface`；白字不得落在 `#FF4D4F`(3.27) / `#FA8C16`(2.38) / `#52C41A`(需实算，未列入本轮) 等 500 档实底。

### 8.2 触控热区清单

| 目标 | 最小值 | 现状 |
| --- | --- | --- |
| 主操作（按钮、chip、列表行、宫格项、FAB、底部栏） | 44×44 | 除 **`sync.vue:101,142`（32px，P0-3）** 外均达标 |
| 次要控件（文本链接、图例、关闭） | ≥24×24（推荐 44） | `SlaTag` 非交互；`check-item__link` 44 ✓ |
| 表单输入行 | ≥44 | `pickup.vue:205` 恰好 44（P1-9 登记） |
| 弹层选项行 | ≥48 | `StationPicker.vue:67`、`ActionBar.vue:152`、`workorderDetail.vue:515` ✓ |

### 8.3 焦点管理与键盘

| 项 | 规范 | 现状 |
| --- | --- | --- |
| 焦点可见 | 全局 2px 主色环 + `outline-offset: 2px` | `mobile.scss:37-42` ✓（**不得** `outline: none`） |
| 可聚焦顺序 | = DOM 顺序；返回按钮 → 内容 → 固定栏 | `PageNav.vue:26-30` ✓ |
| `van-tabs` | **必须补 Enter/Space 切换** | ✗ P0-6 |
| `van-tabbar` | `aria-current` + Enter 兜底 | ✓ `TabbarLayout.vue:98-100`（范本） |
| `van-switch` | **必须补 Enter/Space** | 打卡页已补（`attendance.vue:527-528`），需成为规范（P1-11） |
| `van-radio` | 可聚焦性待复核（U2） | ✗/待复核 `leaveApply.vue:245,259` |
| 行点击 | 可聚焦 + Enter + Space | 4 处正确、`payroll.vue:88-90` 缺 Space（P2-3） |

### 8.4 读屏文案

| 场景 | 要求 | 现例 |
| --- | --- | --- |
| 图标 | 一律 `aria-hidden="true"`，语义由文字承担 | 全项目已遵守 |
| 有数值的入口 | `aria-label` 必须含数值与单位 | `QuickGridItem.vue:40-44`（范本）、`TabbarLayout.vue:41-44`（范本） |
| 打卡按钮 | 含「时段名 + 卡类型（+ 班次）」 | `attendance.vue:401,417`、`AttendanceStatusBar.vue:75-78` |
| 计时器 | `role="timer"` + `aria-label`，**不**用 `aria-live`（秒级会打断） | `attendance.vue:352` |
| 状态提示 | `role="status"`（不打断）/ `role="alert"`（打断，仅失败） | `attendance.vue:439`（按结果切换）、错误态统一 `role="alert"` |
| 进度/步骤 | `role="list"` + `listitem` + `aria-current="step"` | `PayrollStatusSteps.vue:31-38` ✓ |
| 分组筛选 | `role="group"` + `aria-label` | `FilterChips.vue:24`、`makeupList.vue:84` ✓ |
| 图表 | `role="img"` + `aria-label` + 视觉隐藏数据表 | `LineChart.vue:245-246,418-438` ✓（但读数交互 P1-10） |

### 8.5 `prefers-reduced-motion`

全局已覆盖（`tokens.base.scss:244-253`）：动画/过渡降至 0.01ms、`scroll-behavior: auto`。**新增组件不得**用内联 `animation` 时长绕过；步骤条脉冲、骨架脉冲在降级模式下自动静止，无需单独处理。

---

## 9. 验收清单（可静态审查）

判定方式：`grep` / 源码阅读 / node 实算 可**静态判定**的标「静态」；依赖真机或渲染态的标「**待真机复核**」。

### 9.1 结构与环境

- [ ] 静态：`grep -rn -E "#[0-9a-fA-F]{3,8}" src/mobile/views/staff src/mobile/components` **仅命中注释**（当前 3 处：`LineChart.vue:539`、`SlaTag.vue:86,92`），模板内联与 `<style>` 声明零命中
- [ ] 静态：`grep -rn "px" src/mobile/views/staff --include="*.vue"` 无新增魔法值（白名单：`--fs-clock` 相关、既有登记项）
- [ ] 静态：组件内无 px 媒体查询（除 `mobile.scss` 的 640 横屏档）
- [ ] 静态：新增 Token 全部落在 `mobile/styles/tokens.scss` 或 `shared/styles/tokens.base.scss`，且 §3 表格逐条可对应
- [ ] 静态：`npm run verify:mock` 全通过；`npm run e2e` 全通过（DOM 语义不得退化）
- [ ] **待真机复核**：`--status-bar-height` 注入值（U1）
- [ ] **待真机复核**：320 / 375 / 414 / 640 四档无横向滚动（`documentElement.scrollWidth <= clientWidth`）（U3）
- [ ] **待真机复核**：刘海屏安全区上下不塌陷/不遮挡（U3）

### 9.2 状态与语义

- [ ] 静态：所有承载数据的区块显式覆盖四态，且**全部经 `PageState`**（`grep -L "PageState" src/mobile/views/staff/*.vue` 剩余项逐个说明为何不适用）
- [ ] 静态：错误态与空态文案不同（现有 4 处需复核：`sync` 日志、`payroll`、`pickup`、`LeaveApprovalList`）
- [ ] 静态：`grep -rn "= 0$\|ref(0)" src/mobile/views/staff` 后逐个确认无「0 冒充未知」
- [ ] 静态：无整页 `van-loading` 转圈（仅允许按钮内 `:loading`）
- [ ] 静态：骨架高度全部由 `--row-h-*` 派生，无 96/108/120/148/156 字面量
- [ ] 静态：卡片内 `van-cell` 一律收敛水平内边距（消除 44px vs 28px 双缩进）
- [ ] 静态：`van-cell-group` 用法统一（表单/只读信息组一律 `inset`）

### 9.3 触控与对比度

- [ ] 静态：`grep -rn 'size="small"' src/mobile/views/staff` 结果逐条确认有 `min-height: var(--touch-min)` 或语义上非交互
- [ ] 静态：全部交互元素 `min-height` ≥ 44（对照 §8.2 清单）
- [ ] 静态：对比度按 §8.1 表逐项对照，**禁用组合清单零出现**
- [ ] 静态：`--text-3` 未出现在 `--surface-page` / `--surface-sunken` / `--color-danger-surface` 之上
- [ ] 静态：占位符作为唯一信息源处已消除或改深（`parcel.vue` 搜索框）
- [ ] **待真机复核**：DevTools 取渲染色后按 §8.1 脚本复算，确认无半透明叠加导致的不达标（U4）

### 9.4 键盘与读屏

- [ ] 静态：所有 `van-tabs` 页面已补 Enter/Space 切换（`workorder`/`parcel`/`MessagePage`/`NoticeList`）
- [ ] 静态：所有 `van-switch` 已补 Enter/Space
- [ ] 静态：所有行点击元素具备 `role`/`tabindex`/Enter/Space 四件套（现 5 处，`payroll` 缺 Space）
- [ ] 静态：有数值的入口 `aria-label` 含数值；图标 `aria-hidden`
- [ ] 静态：失败容器 `role="alert"`、非打断状态 `role="status"`
- [ ] **待真机复核**：`van-radio`（半天选择）Tab 可达性（U2）
- [ ] **待真机复核**：`prefers-reduced-motion: reduce` 下无位移/缩放残留

### 9.5 文案口径

- [ ] 静态：`PARCEL_STATUS` 值 1 在员工端与字典/PC 端**同文案**（P0-5）
- [ ] 静态：SLA 超时可见文案单一化（P1-15），`format.spec.js` 同步更新
- [ ] 静态：无「加载失败」与「暂无数据」混用
- [ ] 静态：错误文案三段式（现象 + 原因 + 下一步）

### 9.6 性能与适配

- [ ] 静态：路由懒加载全量 `() => import()`
- [ ] 静态：列表页均有单飞保护 + `immediate-check="false"`
- [ ] **待真机复核**：键盘弹起时底部固定栏不遮挡当前输入框（U6）
- [ ] **待真机复核**：2000 条 mock 数据下的滚动帧率（U5）

### 9.7 交付前自检（本规范自身）

- [x] 设计方向有业务依据（作业型界面），非 AI 默认风：沿用品牌蓝 + 物流橙 + 深蓝灰，无紫色、无新色族
- [x] 3 层 Token 结构完整：新增 9 条均注明层级与理由，明确不新增项 5 条
- [x] 组件状态全覆盖：26 个既有组件逐个给出 7 态（含显式「不适用」），9 个新增组件给出完整契约
- [x] 无障碍：对比度 **node 实算**（§8.1，附可复现脚本）、触控清单、焦点与键盘、读屏文案、动效降级逐项给出
- [x] 响应式：320/375/414/640 + 安全区 + 键盘弹起 + 长列表策略
- [x] 全部结论带 `文件:行号` 依据；无法核实的登记为 U1–U6
- [x] 未编造组件名/Token 名/行号/页面名：Vant 行为均取自 `node_modules/vant` 产物实测

---

## 附录 A：本轮新增 Token 一览（给前端直接抄）

```scss
/* mobile/styles/tokens.scss 追加（L2 移动端特有 + L3 Component） */
--touch-min: 44px;          /* N1 */
--row-h-1: 48px;            /* N2 */
--row-h-2: 64px;            /* N3 */
--row-h-3: 76px;            /* N4 */
--row-h-tile: 88px;         /* N5 */
--fs-badge: 10px;           /* N6 */
--badge-h: 16px;            /* N7 */
--badge-pad-x: 4px;         /* N8 */
--shift-bar-w: 4px;         /* N9 */
```

`Shared/tokens.base.scss` 本轮**零新增**（理由：这 9 条的取值或语义均为移动端私有，进真源会污染两端同名同义的约束）。

## 附录 B：与既有规范的冲突与处置

| 冲突点 | 既有规范 | 本轮结论 | 处置 |
| --- | --- | --- | --- |
| 工单 FAB 位置 | `demo-ui-redesign.md` 5.8-S5：`bottom: calc(--tabbar-h + --safe-bottom + 12px)` | 工单页已降为二级页、无 Tabbar，`workorder.vue:238` 为 `--safe-bottom + --sp-3` | 回写 5.8-S5 失效 |
| Tabbar 项数与字号 | `demo-ui-redesign.md` 7.2「固定 5 项」「文字 10px」 | 3 项（`constants/tabs.js:15-19`）、文字 12px（`mobile/styles/tokens.scss:88`，Vant 默认 `--van-font-size-sm`） | 已在 `demo-mobile-nav-redesign.md` 附页登记，本文档再次确认 |
| 宫格项数 | `demo-mobile-nav-redesign.md` B1「4×2 = 8 项，第 9 项下沉」 | 员工端 9~10 项，按 4 列自动换行 | 以 B1「M11 变更」为准；`HomeQuickGrid.vue:16-17` 的 TODO 需改写 |
| 员工端主指标字号 | `demo-ui-redesign.md` 3.2：员工端 22 | `staff/kpi.vue:113` 用 24 | 按 §3 C-1 纠正 |
| 「我的数据」分组项数 | `demo-mobile-nav-redesign.md` A3：6 项 | 实际 9~10 项 | 按 §5.7 二次分群 |
| Hero 副信息对比度 | `demo-ui-redesign.md` 9.1：≈5.40 | 实算 4.66 | 回写实算值 |
| `--text-3` 在浅灰底的比值 | `leaveApply.vue:396` 注释：4.23 | 在 `--surface-subtle` 为 4.5046；4.23 实为 `--surface-sunken` | 修正注释 |

## 附录 C：待真机复核项汇总（6 项）

U1 壳 `--status-bar-height` 实测值 / U2 `van-radio` Tab 可达性 / U3 四档宽度与安全区 / U4 渲染态半透明叠加后的对比度 / U5 2000 条长列表滚动帧率 / U6 键盘弹起与固定栏遮挡。

---

## 10. v1.2 修订（2026-09-23）· 员工端命名与「我的」页精简

> 追加节，不改动本文档既有条款。依据来自本轮逐文件读码（行号可跳转）。硬约束：不改 `router/index.js`（含 `index.spec.js`）、不新增路由/二级页、`hrm-admin`/`hrm-server` 零改动、壳保持 `views/staff/me.vue` 原位。

### 10.1 员工端应用命名命中矩阵（鉴权前无法判角色）

应用名真源：**员工端 = 「驿站助手」**；**管理端 = 「快递驿站智汇系统」**（保持）。判据优先级：登录后 `auth.role`（`stores/auth.js:32-33`） > 登录前 `route.query.as`（`views/login/index.vue:33`；取值仅 `boss|station|staff`，见 `src/demo/accounts.js:8-12`）。

| 载体 | as=staff / as=station（员工端） | as=boss（管理端） | 无参数 |
| --- | --- | --- | --- |
| `<h1>` `views/login/index.vue:64` | 驿站助手 | 快递驿站智汇系统 | 快递驿站智汇系统 |
| 副标题 `views/login/index.vue:65` | 员工端 · 移动端演示 · 纯 Mock 数据，无需后端 | 移动端演示 · 纯 Mock 数据，无需后端 | 移动端演示 · 纯 Mock 数据，无需后端 |
| 浏览器标题 | 驿站助手 | 快递驿站智汇系统 | 快递驿站智汇系统 |
| TabbarLayout 标题 | 「我的」等，**不含应用名 → 不改** | 同左 | 同左 |

- **登录页不动 `router`**：仅把 `views/login/index.vue:64-65` 的静态文案改为按 `route.query.as` 计算的 `computed`（`as` 已在 `:33` 读取，零新增数据源）。
- **浏览器标题运行时覆盖**：`mobile.html:16` 静态标题保留为首屏/无 JS 兜底；在 `src/mobile/App.vue` 加 `watchEffect`（`App.vue:14` 已有 `route`、`:16` 已有 `auth`），按上表优先级写 `document.title`。**不改 `router/index.js`**（标题不依赖 `meta`）。
- **确认点**：站内导航栏标题取 `route.meta.title`（`layout/TabbarLayout.vue:30` = `route.meta.title || ''`），值为「我的」「工作台」等，**不含应用名 → 本轮不动**；页内标题由各页显式传入（如 `staff/kpi.vue:59`），同样不含应用名。
- **本轮不改的共享命名点**：

| 位置 | 值 | 不改理由 | 若需改的改法 |
| --- | --- | --- | --- |
| `hrm-android-shell…/values/strings.xml:3` | `快递驿站` | 壳是三端移动端**共用载体**（管理员/员工同一 `mobile.html`），非员工端专属 | 不改（改则波及管理端） |
| `src/portal/main.js:19-41` 卡片名 | 员工端（作业视角） | 属**端选择入口**的「端」名，非「应用名」；范围仅员工端视图 | 仅改卡片③ `name` 为「员工端 · 驿站助手（作业视角）」，`link`/`role` 不动（单字段，零风险） |
| `package.json:5` description | 快递驿站智汇系统 · 三端演示 Demo | 三端共用描述 | 不改 |

### 10.2 「我的」页新信息架构（员工端）

区块顺序与保留/移除逐项对照 `components/MeSection.vue`：

| 序 | 区块 | 处置 | 现状依据 |
| --- | --- | --- | --- |
| 1 | 用户信息 Hero 卡 | **保留并吸收手机号/所属部门**（副信息改两行） | `MeSection.vue:56-62` |
| 2 | 「我的数据」两群 | **二次分群（P1-13）** | `MeSection.vue:67-95` |
| 3 | 「切换演示身份」（Demo 态） | 保留，行为与位置不变（数据区之后、账号安全之前） | `MeSection.vue:117-120` |
| 4 | 「账号安全」修改密码 | 保留 | `MeSection.vue:122-125` |
| 5 | 「关于」 | **原位替换「运行环境」** | 替换 `MeSection.vue:127-132` |
| 6 | 退出登录 | 保留 | `MeSection.vue:134-136` |
| — | 「账号信息」5 行整块 | **删除**（登录账号/所属驿站与 Hero `:61` 重复；最后登录低价值） | `MeSection.vue:97-115` |

群名与群内项序（**一级标题「我的数据」保留**；群为二级）：

| 群 | 项序 | 路由 |
| --- | --- | --- |
| ① 薪酬与考核 | 我的 KPI → 我的工资单 → 我的档案 | `/staff/kpi`、`/staff/payroll`、`/staff/profile` |
| ② 考勤与流程 | 我的排班 → 打卡记录 → 我的补卡申请 → 我的请假 → 我的入离职 → 请假初审（站长）→ 同步状态（`canSeeSync`） | `/staff/schedule`、`/staff/attendance/records`、`/staff/attendance/makeup`、`/staff/leave`、`/staff/flow`、`/staff/leave/review`、`/staff/sync` |

- 附加项条件原样保留：请假初审 `v-if="auth.role === 'STATION_ADMIN'"`、同步状态 `v-if="auth.canSeeSync"`（`MeSection.vue:86-94`）；二者置于群②**末**（严格按拍板）——若更重语义相邻，可把请假初审前移至「我的请假」之后（**可复判点**）。
- 层级（全用既有 Token）：一级标题沿用 `.section-title`（`mobile.scss:125`，`--fs-h3`/`--fw-semibold`，上距 `--sp-5`、下距 `--sp-2`）；新增二级群标题修饰类 `.section-title--sub`（落 `mobile.scss`）＝ `--fs-caption`/`--fw-medium`/`--text-2`，群①上距 `--sp-3`、**群②上距 `--sp-6`**（§5.7.1）。**注意**：`--text-3` 不得落在 `--surface-page`（§3 C-2，实算 4.5046 无余量），故群标题用 `--text-2`（7.04）。
- `inset` 已统一（`MeSection.vue:69,77,109,123,129` 均 `inset`，P1-8 已满足），新结构与「关于」沿用 `inset`。

### 10.3 拆分方案：**采纳方案 A**（员工端自组合壳）

**裁决：方案 A**。理由：① 员工端删「账号信息」块与「运行环境」、管理端二者全保留——差异是**整块级**而非字段级，组件边界是最显式的表达（§2.3 禁止隐式分支）；② 方案 B 需在 `MeSection.vue`（现约 195 行）内塞两套 IA 与多个 `v-if="view==='self'"`，必然破 §4.2「单文件 ≤300 行」，且**管理端将持续暴露在员工端 diff 中**，与「管理端零变化」硬约束相悖；③ 约束 4 已为 `views/staff/me/components/`、`views/staff/me/composables/` 预留落点。取舍：方案 A 需抽共享块并等价改写 `MeSection.vue`（内联块→引用共享组件，**DOM 与类名保持一致**），管理端零变化的验收方式是**逐项视觉/行为比对**——本方案唯一风险点。

新组件清单（共享块落 `src/mobile/components/`，员工端专属落 `views/staff/me/`）：

| 组件 | 分层 | 落点 | 职责 | 行数上限 |
| --- | --- | --- | --- | --- |
| `ProfileHero.vue` | Organism | `components/` | 用户信息 Hero 卡；props `name`/`role`/`lines: string[]`（每条一行 `.hero__sub`），输出与 `MeSection.vue:56-62` 等价 | ≤70 |
| `AccountSecurityGroup.vue` | Molecule | `components/` | 「账号安全」标题 + `inset` cell-group + 修改密码 cell；props `to`（默认 `/staff/me/password`） | ≤50 |
| `DemoIdentityGroup.vue` | Molecule | `components/` | 「切换演示身份」区（`VITE_MOCK_ENABLED==='true'` 才渲染 + 标题 + `IdentitySwitcher`） | ≤40 |
| `LogoutAction.vue` | Molecule | `components/` | 退出按钮 + 二次确认 + 登出跳转（与 `IdentitySwitcher.vue` 同型，可持 store/router） | ≤70 |
| `MyDataGroups.vue` | Organism | `views/staff/me/components/` | 员工端「我的数据」两群（群内项与站长附加项 `v-if`） | ≤90 |
| `AboutGroup.vue` | Molecule | `views/staff/me/components/` | 「关于」区（见 10.4） | ≤50 |

- composable：**需要 1 个** → `views/staff/me/composables/useMyProfile.js`，暴露 `{ state, retry }`（`state ∈ loading|error|ready`，`retry` 走 `auth.refreshMe()`，与 `MeSection.vue:26-39` 同口径）。**新增理由**：员工端删「账号信息」块后，Hero 成为唯一取数区块，三态必须由 Hero 承担（§0.1）；否则 `auth.userError` 在员工端**无处回显**。
- `views/staff/me.vue` 壳（≤120 行）：`<PageState :loading :error @retry>` 包 `ProfileHero`（仅 Hero 取数，群导航静态不包）→ `MyDataGroups` → `DemoIdentityGroup` → `AccountSecurityGroup` → `AboutGroup` → `LogoutAction`。
- `MeSection.vue` 改为**仅服务管理端**：保留「账号信息」5 行块与其 `meState` 骨架/错误（`:99-115`）、「运行环境」块（`:127-132`）；Hero/演示/账号安全/退出改引用上述共享组件（等价替换）。

### 10.4 「关于」区规格（替换原「运行环境」）

| 字段 | 值 / 文案 | 说明 |
| --- | --- | --- |
| 分组标题 | 关于 | `.section-title` |
| 应用名称 | 驿站助手 | 员工端应用名（10.1） |
| 版本 | v1.0.0（演示版） | **零风险替代**，见下 |
| 数据来源 | 全量 Mock，不发起真实请求 | 与 `portal/main.js:12` 口径一致 |

- **版本号取值（零风险）**：**不新增构建期变量**。固定文案 `v1.0.0`（与 `package.json:3` `version` 同值），加注释 `TODO(扩展): 后续由 vite define 注入 package.json version，消除硬编码`。
- **若改用 `import.meta.env.VITE_APP_VERSION` 的评估**：需在 `hrm-demo/.env.demo` 与 `.env.production` 各加一行（该两文件仅含演示参数、随仓库提交，无凭据风险），`build`(demo)/`build:prod`(production) 均可读到，`verify:mock`/`verify:mobile` 不受影响 —— **可行，但超出本轮「只改一个 md」范围**，故本轮给零风险替代。
- **视觉/inset 口径**：`van-cell-group inset`（P1-8）；沿用原「运行环境」的 Caption 降级 —— Vant 变量覆盖 `--van-cell-font-size: var(--fs-caption)` / `--van-cell-text-color: var(--text-3)` / `--van-cell-value-color: var(--text-3)`（现状 `MeSection.vue:186-190` `.env`）。**已核算**：`inset` cell-group 底为 `--surface-card`(#FFFFFF)，`--text-3` 实算 **4.83:1** 达 AA（§8.1），合规。

### 10.5 无障碍与响应式

| 项 | 规范 |
| --- | --- |
| 焦点顺序 | = DOM 顺序（Hero 不可聚焦 → 群① cell 链 → 群② cell 链 → 演示身份按钮 → 修改密码 → 关于（只读不可聚焦）→ 退出按钮）；**不加 `tabindex`**（§8.3） |
| `aria-*` | 群用 `<section role="group" :aria-label="群名">` 暴露分组；装饰图标 `aria-hidden="true"`；「关于」只读行不设 `tabindex` |
| 触控 | 全部 cell 行高 ≥48（`tokens.scss:125` 派生）；修改密码 cell 与退出按钮 ≥ `--touch-min`(44px)；`ProfileHero` 内 chip 非交互 |
| 320px | Hero 副信息两行、群标题与 cell label 允许换行；无横向滚动（`mobile.scss:17` 兜底）；「数据来源」≤2 行（§6.5） |
| 375px | 基准宽度 |
| 414px | 内容不超容器（页面左右 padding 固定 `--sp-3`，见 `tokens.scss:130`） |
| `prefers-reduced-motion` | 已全局覆盖（`tokens.base.scss:244-253`）；新区块无动效，无需重复声明 |
