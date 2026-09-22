# 快递驿站智汇系统 · 移动端「老板端」精细化开发设计规范

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-22 |
| 作者 | UI/UX 设计师 |
| 适用范围 | `hrm-dev/hrm-demo` 移动端老板端（`mobile.html`，`roles: ['ADMIN']` 的 21 个页面 + 其消费的组件与共享内核） |
| 交付对象 | 主智能体（评审）→ 前端工程师（照本文件逐项落地）→ 测试工程师（照第 10 章验收） |
| 本轮产出 | **仅本设计规范，不改任何源码** |
| 依据文档 | [demo-ui-redesign.md](demo-ui-redesign.md)（Token 三层体系）、[demo-ux-improvement.md](demo-ux-improvement.md)（M1 冻结规范）、[demo-mobile-nav-redesign.md](demo-mobile-nav-redesign.md)（三 Tab 信息架构 + 95 条迁移映射）、[demo-leave-design.md](demo-leave-design.md)、[demo-sync-config-design.md](demo-sync-config-design.md) |
| 上位约束 | 项目规则 §12（`hrm-admin`/`hrm-server` 零改动）、§8.1（先设计后实现）、§5.1（测试工程师视觉/响应式验收） |

---

## 0. 取证方式、范围与不确定项

### 0.1 范围界定（不可扩张）

- **本次范围**：仅移动老板端。PC 管理台（`src/pc/**`）与移动员工端（`src/mobile/views/staff/**`）**不作为设计对象**；但员工端与老板端**共用的共享内核**（`src/mobile/components/**`、`src/mobile/composables/**`、`src/shared/**`）在老板端消费面上必须一并纳入，并逐项标注「改动需评审」。
- **不计入范围**：`hrm-admin/**`、`hrm-server/**`、`hrm-android-shell/**` 一律零改动；一期占位页不删除。
- **本轮产出形态**：只出规范。主智能体评审通过后由前端工程师实现，测试工程师按第 10 章逐条验证。

### 0.2 取证方式

1. **静态阅读**：完整阅读老板端 21 个页面、25 个组件、`mobile.scss` / `tokens.scss` / `shared/styles/tokens.base.scss`、`router/index.js`、`shared/domain/{permission,mask}.js`、`mobile/utils/bridge.js`、`mobile/api/index.js`，逐条标注 `文件:行号`。
2. **对比度实算**：全部色值对比度用 node 按 WCAG 2.x 相对亮度公式实算，**不手算、不估数**（工程既有教训：`--gauge-fill` 曾记「约 3.1:1」实算为 2.839:1 不达标，见 `SESSION-STATE.md` M10 决策 15）。计算方式与结果见附录 A。
3. **未使用浏览器实测**：TRAE Chrome 扩展在本机不可用（`os error 10061`，`SESSION-STATE.md` 环境约束表）。因此本文中**一切依赖运行时渲染的结论**（Vant 组件内部像素、真实手机竖屏、iOS Safari、壳内状态栏）一律标注「未验证」，**不得当作已验证**。

### 0.3 不确定项与验证方式（反幻觉声明）

| # | 不确定项 | 本文处理 | 验证方式 | 状态 |
| ---- | ---- | ---- | ---- | ---- |
| U1 | Vant 4.10 组件内部实际渲染像素（`van-progress` 高度、`van-cell` 真实行高、`van-popup` 圆角） | 只引用工程已按 v4.10 产物核对的 CSS 变量（`tokens.scss:91-155`），不臆测像素 | 实现后用 DevTools 量取；不符只改 `tokens.scss` 一处 | 未验证 |
| U2 | 安卓 WebView 壳内 `--status-bar-height` 实测值 | 按设计口径「壳注入，浏览器为 0」处理（`tokens.scss:158-160`、`bridge.js:71-74`）；**实测值未知** | 壳内 `getDeviceInfo().statusBarHeight` 回读（`hrm-android-shell/BUILD.md:104,109` 亦登记未验证） | 未验证 |
| U3 | iOS Safari 下 `parseTime` / `document.execCommand('copy')` / `100vh` 的实际行为 | 一律标「未验证」（`SESSION-STATE.md` 遗留 25） | 真机 iOS Safari 走查 | 未验证 |
| U4 | 真实手机 375px 竖屏下的 4 列宫格换行、工单行 52px 复制位遮挡 | 保留既有实测（浏览器窗口下 375px 宫格横向溢出 0px、热区不达标 0 个，`SESSION-STATE.md` M11 澄清 4），**真机未验证** | 真机 375px 复测 | 部分验证 |
| U5 | ≥1280px 宽屏下移动壳行为（本项对移动端意义有限，但影响桌面浏览器窄窗/宽窗一致性） | 标「未验证」 | 桌面浏览器拉伸复测 | 未验证 |
| U6 | 企业微信内置浏览器（X5 / wkwebview）对 `env(safe-area-inset-*)`、`backdrop-filter`、`position: fixed` 的表现 | **无工程内证据**，全部标「未验证」，只给降级方案不给结论 | 企微内打开 `mobile.html` 走查 | 未验证 |
| U7 | 键盘导航全链路（Tab 顺序、焦点环可见性、Enter/Space 触发行操作） | 静态审出明确缺陷（见 1.2 G-04、10 章），其余标「未验证」（`SESSION-STATE.md` 遗留 25） | 桌面浏览器 + 外接键盘全链路走查 | 未验证 |

**引用纪律**：本文凡写「已满足」，必有 `文件:行号`；凡写「未验证」，不得在实现阶段被表述为「已验证」。

---

## 1. 组件库清单与缺口

### 1.1 老板端消费面总表（21 页 / 25 组件）

**直接 import 统计**（源：`views/boss/*.vue` 的 import 行）：

| 组件 | 直接消费页数 | 消费页面（老板端） | 状态覆盖自评 |
| ---- | ---- | ---- | ---- |
| `PageNav` | 18 | attendance, alerts, attendanceRecords, attendanceRule, flow, flowDetail, hr, hrDetail, kpi, leaveSettings, makeupApproval, notificationPublish, payroll, payrollDetail, rank, schedule, trend, workorder | ① 已满足 |
| `PageState` | 18 | 同上 + home | ① 已满足 |
| `StatusTag` | 8 | alerts, attendanceRecords, flow, hr, kpi, makeupApproval, payroll, workorder | ① 已满足 |
| `StationPicker` | 7 | attendanceRecords, flowDetail, kpi, notificationPublish, payroll, schedule, workorder | ② 缺状态 |
| `ActionBar` | 6 | attendanceRule, flowDetail, leaveSettings, notificationPublish, payrollDetail, schedule | ② 缺状态 |
| `StatCard` | 4 | attendance, home, kpi, trend | ① 已满足 |
| `MonthPicker` | 2 | kpi, payroll | ② 缺状态 |
| `LineChart` | 2 | home, trend | ① 已满足（能力边界见 8.1） |
| `SlaTag` | 2 | alerts, workorder | ① 已满足 |
| `PayrollStatusSteps`（`flowDetail` 以 `FlowSteps` 别名复用同一实现） | 2 | payrollDetail, flowDetail | ① 已满足 |
| `HomeQuickGrid` | 1 | home | ① 已满足（有意不进错误态） |
| `LeaveApprovalList` | 1 | leaveApproval | ① 已满足 |
| `MeSection` | 1 | me | ② 缺状态 |
| `MyPayrollCard` | 1 | payrollDetail | ① 已满足 |
| `WorkOrderCopyButton` | 1 | workorder | ① 已满足 |

**传递消费（经 MessagePage / 子组件 / 员工端复用页间接进入老板端）**：

| 组件 | 进入路径 | 证据 |
| ---- | ---- | ---- |
| `NoticeList` | `/boss/message` → `MessagePage` | [MessagePage.vue:4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/message/MessagePage.vue#L4) |
| `TodoList` → `TodoGroup` | 同上 | [MessagePage.vue:5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/message/MessagePage.vue#L5)、[TodoList.vue:4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/TodoList.vue#L4) |
| `QuickGridItem` | `HomeQuickGrid` | [HomeQuickGrid.vue:3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/HomeQuickGrid.vue#L3) |
| `FilterChips` | `LeaveApprovalList` | [LeaveApprovalList.vue:4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LeaveApprovalList.vue#L4) |
| `LeaveAudit` | `LeaveApprovalList` | [LeaveApprovalList.vue:5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LeaveApprovalList.vue#L5) |
| `IdentitySwitcher` | `MeSection` | [MeSection.vue:5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L5) |
| `KpiGauge`、`KpiIndicatorCard` | `/boss/kpi/:employeeId` 复用 `staff/kpi.vue` | [router/index.js:109-114](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L109-L114)、[staff/kpi.vue:4-5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/kpi.vue#L4-L5) |

**老板端不可达（本次不动）**：`AttendanceStatusBar`（仅 `/staff/home` 消费，[staff/home.vue:4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L4)）。

> **结论**：25 个组件中 **24 个在老板端可达**（唯一不可达为 `AttendanceStatusBar`），且这 24 个**全部位于共享内核 `src/mobile/components/`**（员工端也在消费）→ 任何改动均需主智能体评审；**0 个是老板端专属组件**——这是本次要补的最大结构性缺口（见 1.3）。

### 1.2 缺口分类

#### ① 已满足（保持不动）

`PageState`（loading / error + retry / empty 三态齐备，且带 200ms 骨架延迟防闪，[PageState.vue:9-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L9-L22)）、`StatCard`（loading / error / 空值渲染 `—` / 环比三态，[StatCard.vue:9-23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatCard.vue#L9-L23)）、`LineChart`（loading/error/empty/ready 四态 + 图例 + 读屏数据表，[LineChart.vue:197-202](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L197-L202)）、`StatusTag`（未匹配值渲染 `—`，[StatusTag.vue:110-111](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatusTag.vue#L110-L111)）、`SlaTag`（正常/临近/超时/隐藏四态 + 首次超时读屏播报，[SlaTag.vue:36-59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/SlaTag.vue#L36-L59)）、`TodoGroup`（loading / error + 重试 / 空组不渲染，[TodoGroup.vue:20-52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/TodoGroup.vue#L20-L52)）、`WorkOrderCopyButton`（默认/忙/禁用，`copyText` 双通道失败必给可见提示，[WorkOrderCopyButton.vue:30-43](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/WorkOrderCopyButton.vue#L30-L43)）。

#### ② 缺状态（在既有组件上补，属共享内核改动）

| # | 组件 | 缺什么 | 证据 | 建议 |
| ---- | ---- | ---- | ---- | ---- |
| G-01 | `StationPicker` | **无 loading / error / empty**。props 只有 `show/stations/modelValue/allowAll/title`，列表为空时直接渲染仅含标题的空弹层 | [StationPicker.vue:8-15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StationPicker.vue#L8-L15)、[StationPicker.vue:26-47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StationPicker.vue#L26-L47) | 补 `loading`/`error`/`emptyText` 三 prop + `retry` emit；消费方（7 页）现已各自吞掉取数失败（如 [attendanceRecords.vue:116-123](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue#L116-L123) 静默降级为空数组）→ 用户看到空弹层会误判「没有驿站」 |
| G-02 | `ActionBar` | **只有全局 `submitting`，无按钮级 `loading`**（`mainAction.loading` 是唯一例外） | [ActionBar.vue:16-17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L16-L17)、[ActionBar.vue:60-70](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L60-L70) | 次操作按钮也支持 `action.loading`，与主操作同口径（对齐 PC 侧「`actingId` 拆 action」的既有修法思路，[demo-ux-improvement.md A6-2]） |
| G-03 | `MonthPicker` | 无 `disabled` / `loading`；账期切换触发整页重载时控件仍可连点 | [MonthPicker.vue:9-12](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MonthPicker.vue#L9-L12) | 补 `disabled` prop；`kpi.vue` / `payroll.vue` 在 `loading` 期间禁用 |
| G-04 | `NoticeList` 行 | 有全局 loading/error/empty，但**行级「标记已读写入中」无视觉反馈**，点击后先 await 再跳转 | [NoticeList.vue:103-112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/NoticeList.vue#L103-L112) | 补行级 `pending` 态（行内骨架/`aria-busy`），见 5.1 |
| G-05 | `MeSection` | **无 loading / error**，直接渲染 `auth.user.*`，取数失败静默 | [MeSection.vue:36-84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L36-L84) | 账号信息区抽成带三态的子块；或在 store 层暴露 `userLoaded/userError` |
| G-06 | 组内局部空态 | 老板端 4 处手写 `…class="empty muted">…`，未走 `PageState`，样式与文案各行其是 | [alerts.vue:151](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L151)、[:180](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L180)、[:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L201)、[:259](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L259) | 抽 `InlineEmpty`（见 1.3 N-05），避免第四次、第五次重复 |

#### ③ 缺能力需新增

| # | 能力 | 现任实现（重复次数） | 证据 |
| ---- | ---- | ---- | ---- |
| G-07 | **排行榜条形**（相对值横条 + 名次徽标 + 数值右对齐） | 手写 2 份：`van-progress` + 自绘名次块 | [rank.vue:102-132](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L102-L132)、[home.vue:297-314](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L297-L314)；名次配色两处还分叉（见 9 章 AP-06） |
| G-08 | **占比展示**（采集四态 / 考勤构成的构成比） | 手写「四态计数按钮」代替任何占比表达 | [alerts.vue:243-257](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L243-L257)、[attendance.vue:88-107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendance.vue#L88-L107) |
| G-09 | **环比/差值徽标**（±% 或 ±金额，三态配色） | 内嵌在 `StatCard` 内，其他场景（排行、明细、调薪差额）各自手写 | [StatCard.vue:35-46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatCard.vue#L35-L46)、[hrDetail.vue:234-237](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/hrDetail.vue#L234-L237) |
| G-10 | **口径说明条**（「口径：…」统一容器） | 手写 `.tip` 多处，且出现「同一页两种写法」 | [alerts.vue:277-280](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L277-L280)、[trend.vue:103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/trend.vue#L103)、[rank.vue:133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L133) |
| G-11 | **统一二次确认** | `showConfirmDialog` 在老板端被直接调用 12 个文件、63 处 Vant 反馈调用，文案模板各写 | [payroll.vue:136-141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payroll.vue#L136-L141)、[schedule.vue:293-305](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L293-L305)、[flowDetail.vue:33-59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/flowDetail.vue#L33-L59) |

#### ④ 共享内核改动 / 老板端专属（改动归属裁决）

| 分类 | 组件 | 落地要求 |
| ---- | ---- | ---- |
| **共享内核（改动需评审）** | 24 个（1.1 表全部） | 改动必须保持员工端行为不回归；`AttendanceStatusBar` 不在本次范围 |
| **老板端专属（可新增，不动内核）** | 本规范提案的 `RankBar` / `DonutChart` / `MetricDelta` / `ScopeNote` / `InlineEmpty` / `BossActionBar`（可选） | 统一放 `src/mobile/modules/boss/components/`，命名前缀 `Boss`，**禁止**把老板端专属样式写进 `mobile.scss` 全局工具类（避免污染员工端） |

> 裁决理由：25 个组件 0 专属 → 老板端所有视觉差异只能靠页面级 `<style scoped>` 硬写（现状 `home.vue` 已累积 7 个自有 class）。按域拆模块的既定形态（用户已拍板）应落成一个**老板端专属组件目录**，而不是继续在共享组件上加 `isBoss` 分支。

### 1.3 新增组件接口草案（props / emits / 状态矩阵 / 无障碍）

> 全部落 `src/mobile/modules/boss/components/`。尺寸与色值一律走既有 Token，**禁止新增十六进制**。

#### N-01 `BossRankBar`（排行榜条形，Molecule）

```text
props:  items: Array<{ key, name, value, subText?, tone? }>
        metric: 'count' | 'rate'        // 决定数值格式化（件数 / 百分比）
        maxValue?: Number               // 缺省取 items 最大值的 1/0.0 保护
        loading: Boolean
        error: String
        emptyText: String
        maxVisible?: Number = 8         // 超出下沉到「全部」
emits:  retry, select(key)
slots:  #extra（行右侧附加标签位，如 SlaTag）
```

| 状态 | 表现 |
| ---- | ---- |
| default | 名次徽标（1/2/3 实底白字，4+ 中性浅底）+ 名称 + 数值（`tabular-nums`）+ 相对值条 |
| loading | 每行等高骨架，高度对齐真实行（≥ 56px），数据到达不跳版 |
| empty | `InlineEmpty`（图标 + 文案 + 可选动作） |
| error | 行内错误条 + 「重新加载」，**不整页替换**（排行是区块之一） |
| disabled / 无权限 | 不适用——由父级过滤后不渲染（与 `QuickGridItem` 同口径，[QuickGridItem.vue:13-15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/QuickGridItem.vue#L13-L15)） |
| focus | 整行可聚焦 `role="button"` + `tabindex="0"`，`Enter`/`Space` 等价触发 |

无障碍：条形容器 `role="img"` + `aria-label="驿站名，包裹量 N 件，占最高值的 P%"`（现状 [rank.vue:125](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L125) 已有同构文案，收口到组件）；名次不能只靠颜色（已有数字，天然双通道）。

**名次配色裁决（必须执行）**：统一取 `--rank-1-bg` / `--rank-2-bg` / `--rank-3-bg`（[tokens.base.scss:195-197](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L195-L197)）。**禁止**继续用 `--c-orange-600`（[home.vue:411-415](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L411-L415)）——其实算对比度 3.556:1 不达 AA（附录 A #14），而 `--rank-3-bg` 为 7.090:1。

#### N-02 `BossDonutChart`（占比环图，Atom）

```text
props:  slices: Array<{ key, label, value, tone }>   // tone 映射到既有语义色族
        total?: Number                              // 缺省按 slices 求和；为 0 时进空态
        size?: 'sm' | 'md' = 'md'                   // 80 / 120，均为 8px 网格倍数
        thickness?: Number = 12
        loading: Boolean
        error: String
emits:  retry, select(key)
```

限制（刻意）：**最多 4 段**（第 5 段起合并为「其他」），避免窄屏图例折行；不使用动画扫入（`prefers-reduced-motion` 下必须静态，[tokens.base.scss:244-253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L244-L253)）。
无障碍：`role="img"` + `aria-label="采集状态构成：正常 N 站（P%）、异常 N 站（P%）…"`，并配 `<table class="visually-hidden">` 等价表（复用 `LineChart` 既有模式，[LineChart.vue:418-438](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L418-L438)）。**禁止**仅用颜色区分扇区：每段必须带文字标签或直接标值。

#### N-03 `BossMetricDelta`（环比/差值徽标，Atom）

```text
props:  value: Number | null        // null = 无数据（不渲染整标）
        mode: 'percent' | 'money' | 'number'
        intent?: 'inverse'           // 逆向指标（异常率上升 = 坏）→ 翻转配色语义
        size?: 'sm' | 'md'
```

- 空值：`value === null` 时整标不渲染（不显示 `0.0%`，与 [StatCard.vue:37](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatCard.vue#L37) 现行为一致）。
- 三通道：符号（`↑/↓/—`）+ 文字 + 颜色，**不以颜色为唯一区分**。
- 无障碍：`aria-label="较基准上升 12.4%"`（读屏念得出方向，不依赖箭头字形）。

#### N-04 `BossScopeNote`（口径说明条，Atom）

```text
props:  text: String                // 「口径：…」
        tone?: 'default' | 'warning'
        ariaLive?: 'off' | 'polite'
```

替换老板端全部手写 `.tip` / 口径段落。**为什么必须统一**：口径文案是「数据可信度」的唯一表达，散写必然出现「同一数字两个口径」的表述冲突（工程内已有先例：同步成功率前端样本聚合 vs `/sync/overview` 权威计数，[demo-ux-improvement.md A4-3]）。

#### N-05 `BossInlineEmpty`（组内空态，Atom）

```text
props:  text: String
        icon?: String = 'logistics'
        actionText?: String
emits:  action
```

替换 [alerts.vue:151/180/201/259](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L151) 等处的手写空态。

#### N-06 `BossConfirm`（危险/不可逆操作二次确认封装）

```text
函数式：  bossConfirm({ action, target, impact, irreversible, confirmText })
                    → Promise<boolean>
```

强制四要素：`action`（动词短语标题）、`target`（作用对象）、`impact`（影响面/数量）、`irreversible`（不可逆时正文必须显式声明）。收敛老板端 12 个文件、63 处直接调用（附录 B 清单）。

---

## 2. 响应式与多设备规范

### 2.1 现状核实（先读代码，再定规范）

| 现状 | 实际写法 | 证据 |
| ---- | ---- | ---- |
| 内容列兜底宽度 | `#app { max-width: 480px; margin: 0 auto }` | [mobile.scss:26-35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L26-L35) |
| 横屏放宽 | `@media (orientation: landscape) { #app, .van-nav-bar--fixed, .van-tabbar--fixed, .actionbar { max-width: 640px } }`，靠 **source order 在同文件末尾**覆盖 480 档 | [mobile.scss:431-438](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L431-L438) |
| 固定栏同步收口 | NavBar / Tabbar / ActionBar 三者也限宽 480 并 `margin: auto`（否则横跨视口与内容错位） | [mobile.scss:422-428](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L422-L428) |
| 横向溢出 | `html, body { overflow-x: hidden }` + `#app { overflow-x: hidden }` | [mobile.scss:12-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L12-L24)、[:30](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L30) |
| 顶部安全区 | `#app { padding-top: var(--safe-top) }` + `.van-nav-bar--fixed { top: var(--safe-top) }` | [mobile.scss:29](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L29)、[:45-47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L45-L47) |
| 底部安全区 | `--safe-bottom: env(safe-area-inset-bottom, 0px)`，被 ActionBar / Tabbar / 页面留白共同消费 | [tokens.scss:161](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L161)、[:167-168](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L167-L168) |
| 状态栏高度 | `--status-bar-height: 0px`（默认），`--safe-top: var(--status-bar-height, 0px)`；壳经 `bridge.js` 注入 | [tokens.scss:158-160](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L158-L160)、[bridge.js:71-74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/utils/bridge.js#L71-L74) |
| viewport | `mobile.html` 已是 `width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover` | [mobile.html:12-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/mobile.html#L12-L13) |

**关键结论（对既有生效规则不予推翻）**：480 为顶层兜底值，640 由末尾横屏媒体查询覆盖，**两者不冲突**，与 `SESSION-STATE.md` 的既有实测一致。本规范**不引入新的 max-width 档位**，只补断点内的行为细则。

### 2.2 断点表（移动端单列，覆盖 320–640）

> 坐标口径：`--content-max = min(480px, 视口宽)`（横屏为 `min(640px, 视口宽)`）。

| 视口宽 | 代表设备 | 布局行为 | 关键约束与判定 |
| ---- | ---- | ---- | ---- |
| **320** | iPhone SE(1) / 极低端安卓 | 单列；宫格 4 列，每格 ≈ 74×88；chip 行最多 3 项/行 | 每格可点区 ≥44×44 ✓（74>44）。**禁止**任何固定 `min-width > 320` 的控件（如 `fchip--wide` 的 140px 在 320 下仍可行，但不得再加宽） |
| **360** | 主流安卓 | 单列；宫格每格 ≈ 84×88 | 4 列宫格均为整行 4×2 或 4×3；第 3 行不满属可接受（既有实测：员工端 9–10 项在 375px 下横向溢出 0px，[`SESSION-STATE.md` M11 澄清 4]） |
| **375** | iPhone 8/SE2/12 mini | 基准档；`#app` 已 375 | 既有实测基线：宫格横向溢出 0px、热区不达标 0 个（同上） |
| **390** | iPhone 12/13/14 | 单列 | 同上；`fs-num-lg-boss` 24px 不放大（字号不随屏宽缩放） |
| **414** | iPhone Plus/Max | 单列 | 同上 |
| **430** | iPhone 14/15 Pro Max | 单列，内容两侧留白 0（`#app` 宽 430 < 480，铺满） | 长文本行长上限由 `#app` 决定；正文 14px 单行 ≤ 30 汉字，无需额外 clamp |
| **480** | 折叠屏外屏 / 小尺寸竖屏平板 | `#app` 首次触达 max-width 上限 | 此处起**内容列不再增宽**；横屏同样收口，避免被拉扁 |
| **640（横屏）** | 手机横屏 / 折叠屏展开 | `max-width: 640px`（不重排布局） | **明确不做双列**：横屏仍单列，仅放宽容器（与 [mobile.scss:430](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L430) 注释理由一致：避免被拉扁） |
| **768–992（平板）** | iPad 竖屏 | `#app` 居中 480 单列 | 见 2.5 待裁决项 |

### 2.3 安全区与状态栏

1. **顶部**：页面首个元素不得自行 `padding-top`；一律依赖 `#app` 的 `--safe-top` 与固定 NavBar 的 `top: var(--safe-top)`（[mobile.scss:29](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L29)）。
2. **底部**：三种页面留白**只能三选一**，不得混用：
   - Tab 页 → `--page-pad-bottom-tab`；
   - 带 ActionBar → `--page-pad-bottom`；
   - 普通详情页 → `.page--loose`（[mobile.scss:98-110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L98-L110)）。
3. **禁止**在页面内直接写 `env(safe-area-inset-bottom)`（现有 ActionBar / popup 已由组件承担，[ActionBar.vue:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L110)）。
4. **壳内**：`--status-bar-height` 由壳注入（[bridge.js:71-74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/utils/bridge.js#L71-L74)）；**实测值未验证**（U2），因此实现时不得对 0 或非 0 做任何特判分支，布局必须在两者下均不塌陷。
5. **视口高度**：`#app { min-height: 100vh }`（[mobile.scss:27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L27)）。iOS Safari 动态工具栏下 `100vh` 的行为**未验证**（U3）；**本轮不改**，仅登记：若真机出现底部被裁，改为 `min-height: 100dvh` 并保留 `100vh` 兜底（须先实测再改）。

### 2.4 长文本与超长数字

| 场景 | 规则 | 现有实现（可直接复用） |
| ---- | ---- | ---- |
| 列表标题（运单号/工单号） | 单行省略，`min-width: 0` + `text-overflow: ellipsis` | [mobile.scss:248-253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L248-L253) |
| 宫格项名 | 允许换行但 `word-break: break-all` | [QuickGridItem.vue:105-112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/QuickGridItem.vue#L105-L112) |
| 宫格状态型数据行 | 单行省略 | [QuickGridItem.vue:115-123](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/QuickGridItem.vue#L115-L123) |
| 指标长文本（员工名/驿站名+指标） | 左对齐省略，数值右侧不压缩（`flex: none`） | [rank.vue:199-208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L199-L208) |
| **超长数字** | 一律加 `.tabular-nums`；≥ 1 万走 `numberText` 缩写（`万`）；金额走 `moneyText` | [mobile.scss:51-54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L51-L54) |
| 阈值 | 单行数字宽度不得超过容器 60%；超过则降一档字号（`--fs-num-lg-boss` → `--fs-num-md` → `--fs-num-sm`），**不换行、不截断** | 新增规则（现无实现） |

### 2.5 横竖屏切换与平板档

- **横竖屏**：切换后不重排（`orientation: landscape` 只放宽容器到 640）。强制要求：**切换后不得出现横向滚动条**（`overflow-x: hidden` 已兜底，但固定栏 `max-width` 必须随之更新——三处固定栏已在媒体查询内一并覆盖，[mobile.scss:431-437](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L431-L437)）。
- **图表在横屏**：`LineChart` 用 `ResizeObserver` + 100ms 防抖重绘（[LineChart.vue:44-61](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L44-L61)），横竖屏切换会自动适配；**新增图表组件必须复用同一机制**（禁止一次性 `clientWidth` 读取）。
- **平板档（768–992）**：现状是 480 居中单列。**待裁决**（见 11 章 Q2）——两选项：① 保持 480 居中（信息密度稳定，零改动）；② 引入双列（左导航/右内容或双栏卡片）。**本规范默认 ①**，若要 ② 须单独出方案并评估对员工端的影响。

### 2.6 与既有实测结论不冲突的声明

| 既有实测结论 | 本规范关系 |
| ---- | ---- |
| 375px 下宫格横向溢出 **0px**、热区不达标 **0 个**（`SESSION-STATE.md` M11 澄清 4） | **不冲突**：本规范未改宫格列数（仍 4 列）、未改 `--van-grid-item-*` 尺寸，只补 320/430 档判定 |
| `#app` 480 兜底 + 末尾横屏 640 覆盖，**实测不冲突** | **不冲突**：本规范显式承认该规则为生效真源，不新增档位 |
| 固定栏与 FAB 一并限宽居中 | **不冲突**：本规范将该写法列为强制项并指出 FAB 例外（FAB 只锚 right，需用 `right: max()` 内推，[mobile.scss:418-421](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L418-L421)） |

---

## 3. UI/UX 一致性规范

### 3.1 老板端 Token 子集（可直接消费，不得重定义）

> 真源：`shared/styles/tokens.base.scss`（143 条）+ `mobile/styles/tokens.scss`（移动端私有）。**任何页面/组件出现十六进制色值即视为缺陷**（[mobile.scss:4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L4) 已有此验收口径）。

| 用途 | Token | 取值来源 |
| ---- | ---- | ---- |
| 主色（承白字实底/文字） | `--color-primary` | blue-700，白字 6.159:1 ✓ |
| 主色（图标/线/描边/焦点环） | `--color-primary-icon` | blue-500，白底 3.245:1 ✓（SC 1.4.11） |
| 主色浅底 | `--color-primary-surface` | blue-50 |
| 正文一/二/三 | `--text-1` / `--text-2` / `--text-3` | 14.679 / 7.557 / 4.834:1 ✓ |
| 禁用文字 | `--text-disabled` | 1.525:1（**WCAG 1.4.3 对失效控件豁免**，仅用于真禁用态） |
| 卡片/页面/下沉底 | `--surface-card` / `--surface-page` / `--surface-sunken` | neutral-0 / 50 / 100 |
| 描边 | `--border-line` / `--border-control` | 后者 3.106:1 ✓（SC 1.4.11 控件边界） |
| 语义文字色 | `--color-success` / `--color-warning` / `--color-danger` | 5.585 / 5.022 / 5.571:1 ✓ |
| 语义浅底 | `--color-*-surface` | 与上配对比值 5.438 / 4.829 / 5.065:1 ✓ |
| 排行徽标 | `--rank-1-bg` / `--rank-2-bg` / `--rank-3-bg` / `--rank-rest-*` | 白字 5.022 / 4.834 / 7.090:1 ✓ |
| 图表系列 | `--chart-inbound` / `--chart-pickup` / `--chart-abnormal` / `--chart-area` / `--chart-axis` / `--chart-skeleton` | [tokens.base.scss:183-190](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L183-L190) |
| 环形仪表 | `--gauge-fill` / `--gauge-track` / `--gauge-size` / `--gauge-stroke` | 5.388:1 ✓（[tokens.scss:175-180](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L175-L180)） |
| 字号阶梯（移动端） | `--fs-h1-m` / `--fs-h2` / `--fs-h3` / `--fs-body` / `--fs-body-strong` / `--fs-caption` / `--fs-micro` | [tokens.scss:33-47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L33-L47) |
| 数值字号 | `--fs-num-lg-boss`(24) / `--fs-num-md`(18) / `--fs-num-sm`(15) | 老板端主指标专用 24px |

**硬规则**：
1. 500 档（`--color-primary-icon`）**不得承载文字**，只做图标/线/描边/进度条（`tokens.base.scss:84-90` 已固化）。
2. 浅底块内文字**一律 `--text-2`**，禁用 `--text-3`（`--text-3` 对 `--surface-subtle` 实算仅 4.23:1，工程内已有明文禁令，见 [leaveSettings.vue:171](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/leaveSettings.vue#L171) 注释）。
3. 字号层级**不超过 3 级同屏**（H3 区块标题 / Body 正文 / Caption 辅助），数值另计一列不得混入正文阶梯。

### 3.2 老板端「经营数据密度」表达（不破坏既有 Token 体系）

老板端与员工端的核心差异是**信息密度**，不是视觉风格。落地手段只有三条，全部基于既有 Token：

| 手段 | 具体做法 | 依据 |
| ---- | ---- | ---- |
| **A. 区块间距加档** | 老板端 `.stat-grid` 用 `--roomy` 变体（16px）而非员工端 12px；区块标题上 20 / 下 8 | [mobile.scss:149-152](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L149-L152)、[:125-134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L125-L134) |
| **B. Hero 底色区分角色** | 老板端 `hero--deep`（neutral-800→900 深蓝灰）表「经营报告」；员工端 `hero--brand`（blue-700→800） | [mobile.scss:176-182](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L176-L182)；两段渐变**全段任意位置承白字 ≥6:1**，副文本 `rgba(255,255,255,.82)` 合成后对 blue-700 实算 4.715:1 ✓ |
| **C. 数值字号分档** | 主指标 24（`--fs-num-lg-boss`）、次级 18、行内 15；**不新增第 4 档** | [tokens.scss:41-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L41-L44) |

**密度表达的禁区**：
- ❌ 不新增老板端专属色板（现状已有 3 处自发名次色，见 9 章 AP-06）；
- ❌ 不引入「卡片套卡片」（`alerts.vue` 的 `.group__body .list-item` 把内层卡改成浅底是**正确**做法，[alerts.vue:351-355](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L351-L355)，须作为范式推广）；
- ❌ 不为「更密」把字号压到 12px 以下（`--fs-micro` 11px 已限定为图表轴标签与徽标计数，[tokens.scss:40](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L40)）。

### 3.3 专有 Token 需求清单（与既有 143 条不冲突的裁决）

| 需求 | 建议 Token | 与既有体系的关系 | 裁决 |
| ---- | ---- | ---- | ---- |
| 老板端 Hero 渐变 | 已有 `--grad-hero-deep` | 无需新增 | 复用 |
| 环形仪表尺寸 | 已有 `--gauge-size: 80` / `--gauge-stroke: 8` | 无需新增 | 复用 |
| **占比环图尺寸/厚度** | `--donut-size: 120px` / `--donut-stroke: 12px` | 属移动端私有 L3 尺寸（与 `--gauge-*` 同类，不进真源） | **新增到 `mobile/styles/tokens.scss`**，非真源 |
| **排行条形高度/圆角** | `--rank-bar-h: 8px` | 与 `--van-progress` 现有 `stroke-width="8"` 一致 | **复用既有值，不新增** |
| 图表迷你档高度 | `LineChart` 内 `compact ? 96 : 200` | 硬编码在组件内 | 建议提升为 `--chart-h-compact` / `--chart-h-full`（**待评审**，属共享内核改动） |
| 展示**占比百分比**的徽标底色 | 复用 `--state-*` 六态族 | 无需新增 | 复用 |

> **裁决原则**：能复用的绝不再造；确需新增的只允许进 `mobile/styles/tokens.scss`（平台私有层），**不得改 `shared/styles/tokens.base.scss` 的跨端语义**（改真源会影响 PC 端，超出本轮范围）。若确需动真源（如新增图表色），必须单独列出影响面并走评审。

---

## 4. 加载性能规范

### 4.1 首屏预算（老板端）

> 现状基线：`npm run build` EXIT=0，**2421 modules**（`SESSION-STATE.md` 实测结论）。移动端为 hash 路由 MPA，首屏即 `mobile.html` 入口。

| 指标 | 目标 | 依据与说明 |
| ---- | ---- | ---- |
| 首屏 HTML+JS+CSS（gzip） | ≤ 200 KB | 移动端不含 Element Plus；Vant 4 按需引入（[vant.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/vant.js) 集中注册）。**当前实测值未知**（未做体积采样）→ 实现前先跑一次 `build` 并记录各 chunk 体积 |
| 首屏接口数（`/boss/home`） | ≤ 9（现状即 9，**不再增**） | [home.vue:66-79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L66-L79) 一次 `Promise.all` 并发 9 个请求 |
| 首屏可交互（模拟 4G） | ≤ 2.5s | 目标值，未验证 |
| 骨架出现延迟 | 200ms | `PageState` 既有设计：`SKELETON_DELAY = 200`（[PageState.vue:21-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L21-L22)）——**快请求不闪骨架**，该口径不得改 |

### 4.2 分层加载策略（老板端 home 为经营总览，聚合多接口）

现状已实现三级降级，规范将其固化为**强制模式**：

| 层级 | 内容 | 失败处理 | 证据 |
| ---- | ---- | ---- | ---- |
| **L1 骨架层** | `hero` + 4 指标卡 | 整页 `PageState` error + 重试 | [home.vue:169-176](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L169-L176) |
| **L2 独立降级层** | 采集状态、考勤异常、超 48h 未取件 | 各自 `.catch(() => null)`，对应数字显示 `……` 或 `—`，**不带崩整页** | [home.vue:74-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L74-L78) |
| **L3 锦上添花层** | 快捷宫格 9 项数据 | 项级降级：计数型无角标、状态型显示 `—`、纯入口型无数据行 | [HomeQuickGrid.vue:7-17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/HomeQuickGrid.vue#L7-L17)、[QuickGridItem.vue:32-37](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/QuickGridItem.vue#L32-L37) |
| **聚合口径保护** | 四项求和类数字（如 `alertTotal`）**任一项取不到则整体不显示角标**，不用「部分已知」的数冒充完整口径 | [home.vue:113-122](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L113-L122) |

**新增页面必须遵守**：聚合型页面（≥3 个数据源）必须显式划分三级，并在代码注释中标出层级归属。

### 4.3 骨架屏与 `PageState` 使用口径

> `PageState` 现有 API（实读确认）：`loading` / `error` / `empty` / `emptyText` / `errorHint` / `rows`，`emits: retry`（[PageState.vue:9-19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L9-L19)）。

| 口径 | 规则 |
| ---- | ---- |
| **优先 `PageState`** | 整页级三态一律走 `PageState`（18 页已用），**禁止**页面自行写错误块 |
| **例外一：`van-list` 首屏** | `van-list` 必须真实挂载才会触发触底加载，故首屏骨架不能藏在 `PageState` 分支里 → 使用**页面级骨架块**（`skeleton-block`）在前，`PageState` 只承接 error/empty | [attendanceRecords.vue:197-206](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue#L197-L206) |
| **例外二：区块级** | 页面内单个区块失败**不得**让整页进错误态 → 区块自渲染 error + 重试（`alerts` 采集组即此模式，[alerts.vue:236-239](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L236-L239)） |
| **骨架必须等高** | 骨架块高度 = 真实内容高度（如 `sk-hero: 88px`、`sk-card: 88px`、`sk-chart: 96px`，[home.vue:332-344](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L332-L344)），数据到达时**不得跳动** |
| **禁止** | ❌ `van-skeleton` 用于整块占位（行结构对不上真实高度，[mobile.scss:355-358](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L355-L358) 已说明理由）❌ 用骨架代替错误态 ❌ 骨架动画超过 1.2s 周期（既有 `skeleton-pulse 1.2s`，[:361-373](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L361-L373)） |

### 4.4 图表按需加载

- 现状：`LineChart` 为**同步静态 import**，被 `home.vue` / `trend.vue` 直接引入（[home.vue:5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L5)、[trend.vue:3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/trend.vue#L3)），无额外图表库（**不引入 ECharts**，`demo-ui-redesign.md` 0.1 已冻结）。
- 规范：`LineChart` / `KpiGauge` 体积小（自绘 SVG，无依赖）且首页必用 → **保持同步引入**；**新增 `BossDonutChart` 若体积 > 5KB 或仅在单页使用，改为 `defineAsyncComponent` 懒加载**，并给 `loading` 骨架（异步组件默认 `suspense` 空白会导致跳动）。
- `ResizeObserver` 已是唯一重绘机制（[LineChart.vue:47-56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L47-L56)）；新增图表**必须**复用，禁止在 `onMounted` 里只读一次宽度。

### 4.5 列表虚拟化评估（核实结论）

**核实结果：老板端存在 7 条真实长列表（无上限），现状全部为 `van-list` 无限追加，未做虚拟化。**

| 页面 | 数据规模 | 分页方式 | 证据 |
| ---- | ---- | ---- | ---- |
| 打卡记录 | **无上限**（全站全时段） | `van-list`，`PAGE_SIZE=20` 触底追加 | [attendanceRecords.vue:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue#L18)、[:206](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue#L206) |
| 工单管理 | **无上限**（5 个 Tab 各自） | `van-list` 20/页 | [workorder.vue:35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L35)、[:170](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L170) |
| 工资单审核 | 按「月 × 驿站」收敛，单次通常 ≤ 数百 | `van-list` 20/页 | [payroll.vue:23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payroll.vue#L23)、[:202](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payroll.vue#L202) |
| KPI 考核结果 | 全站员工数 | `van-list` 20/页（`PAGE_SIZE=20`） | [kpi.vue:46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/kpi.vue#L46) |
| 请假审批 | 全站请假单 | `van-list` 20/页 | [LeaveApprovalList.vue:33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LeaveApprovalList.vue#L33) |
| 人事管理 | 全站员工 | 列表（分页） | [hr.vue:124-127](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/hr.vue#L124-L127) |
| 通知列表 | 全站通知 | `van-list` 20/页 | [NoticeList.vue:26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/NoticeList.vue#L26) |

**判定**：
- **排行榜页不需要虚拟化**：`getParcelRanking` 一次返回 8 个驿站，无分页（[rank.vue:54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L54)）。
- **异常预警页不需要虚拟化**：超时包裹 `slice(0,10)` 封顶（[alerts.vue:78-81](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L78-L81)），同步失败按驿站聚合，工单取 50 条不翻页（[:74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L74)）。
- **打卡记录 / 工单 / 通知需要评估虚拟化**：`van-list` 无限追加 DOM，遇到二期 20 万级包裹与全站工单时，长距离滚动会累积数百上千个节点。**给出分级处置**：
  - **本轮（必须做）**：给这三页加**软上限**——已加载 > 500 条时，`finished` 置 true 并提示「已加载 500 条，请用筛选条件缩小范围」。低成本、零重构、可控。
  - **下一轮（登记 `TODO(扩展)`）**：引入列表虚拟化（Vant 4 无官方 `van-virtual-list`；需自研窗口化或改用「本地窗口 + `IntersectionObserver`」）。评估要点：行高固定（`list-item` 64 / `list-item--rich` 76，[mobile.scss:217-231](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L217-L231)）→ 可行；但行内含可变高度文本（`alerts` 的三行结构）→ 需先统一行高。
  - **禁止**：以「分页 20 条够用」为由不做任何约束（工单/打卡记录是长期累积数据，筛选条件为「全部」时必然触顶）。

---

## 5. 交互反馈规范

### 5.1 状态逐项定义（老板端强制口径）

| 状态 | 触发 | 表现 | 时限 | 禁止 |
| ---- | ---- | ---- | ---- | ---- |
| **点击态/按下态** | `:active` | 背景 `--surface-subtle` 或 `scale(0.985)`；统一 `touch-action: manipulation` + `-webkit-tap-highlight-color: transparent` | 立即（0ms 延迟） | ❌ 无按下反馈的可点元素（现状仅 `StatCard` / `QuickGridItem` / Tabbar 有，需推广到所有可点行） |
| **加载态** | 请求发出 | ≤200ms 不显示任何骨架（防闪）；>200ms 显示等高骨架；行内操作用按钮 `loading` | 骨架 200ms 起 | ❌ 全屏遮罩 ❌ 用进度条代替按钮 loading（PC 侧已有同禁令，[demo-ux-improvement.md A3-7]） |
| **空态** | 请求成功且结果为空 | 图标 + 文案 + **必要时给出路动作**（如 `payroll.vue` 空态补「工资单由财务端生成」说明，[payroll.vue:198-200](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payroll.vue#L198-L200)） | — | ❌ 空态与错误态同文案（`PageState` 二者已分离，[PageState.vue:53-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L53-L64)） |
| **错误态** | 请求失败 | `role="alert"` + 原因 + **次要有可操作建议** + 「重新加载」按钮（≥44px） | — | ❌ 只显示「加载失败」不给下一步 ❌ 把错误吞成空态（**工程红线**） |
| **失败重试** | 用户点重试 | 重置到 loading，`PageState` 的 `retry` 语义为「重跑首个请求」 | — | ❌ 重试后仍停留错误态而无可感知变化 |
| **危险操作二次确认** | 不可逆 / 影响面 > 20 人 / 覆盖已有数据 / 降薪 | `BossConfirm` 四要素（动词标题 + 对象 + 影响面 + 不可逆声明）；按钮文案用具体动词（「确认发布」/「确认铺排」） | — | ❌ 「确定/取消」 ❌ 高频小范围操作也弹（现状 `notificationPublish` 仅 >20 人弹，[notificationPublish.vue:127-139](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/notificationPublish.vue#L127-L139)，此为正确粒度） |
| **成功反馈** | 写操作成功 | `showSuccessToast` + **就地更新数据**（不整页重拉）；文案含数量（「已发布 12 张工资单」） | 2s | ❌ 成功后无任何反馈 ❌ 成功后又整页骨架重刷（闪掉用户位置） |
| **静默失败** | — | **绝对禁止**（见 5.2） | — | — |
| **无障碍播报** | 状态变化 | 错误 `role="alert"`；动态计数 `aria-live="polite"`；超时首次播报 `role="status"` | — | ❌ 30s 心跳每轮都播报（`SlaTag` 已做「只播一次」，[SlaTag.vue:34-42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/SlaTag.vue#L34-L42)） |

### 5.2 静默失败禁区（工程既有硬要求，逐条可检查）

**定义**：操作已发生但用户无法从界面上得知结果（既无成功也无失败提示，或失败被伪装为空态）。

现存**允许**的静默（白名单，须逐条登记理由）：

| 位置 | 理由 |
| ---- | ---- |
| Tabbar 角标取数失败 | 角标是辅助信息，失败按 0 计，不弹 Toast（[TabbarLayout.vue:20-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L20-L22)） |
| 首页宫格项级降级 | 单项失败显示 `—` 或无角标，不弹 Toast（[HomeQuickGrid.vue:11-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/HomeQuickGrid.vue#L11-L13)） |
| 站点筛选项取数失败 | 降级为「全部驿站」，主列表不受影响（[attendanceRecords.vue:118-122](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue#L118-L122)） |
| 客户端日志上报失败 | 有意丢弃，不重试不提示（[api/index.js:149-150](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/api/index.js#L149-L150)） |
| 审批弹层「就地对齐」失败 | 弹层内已提示手动刷新，不叠加第二个错误态（[LeaveApprovalList.vue:125-127](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LeaveApprovalList.vue#L125-L127)） |

**禁止的静默**（任一出现即判定缺陷）：
- ❌ 「标记已读」「发布」「保存」「审批」类**写操作**无任何反馈；
- ❌ 复制/导出类操作失败无提示（`WorkOrderCopyButton` 已做正确示范：两条剪贴板通道均失败时给「请长按选中文本手动复制」，[WorkOrderCopyButton.vue:37-39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/WorkOrderCopyButton.vue#L37-L39)）；
- ❌ `catch (e) {}` 后列表保持空数组（移动端已由 `PageState` 修复的 P14 类问题，禁止回归）。

### 5.3 「绝不用 0 表示加载失败」（宫格与计数类数字）

| 场景 | 正确表达 | 反例 |
| ---- | ---- | ---- |
| 计数型角标取数失败 | 不渲染角标（`null` → 无角标） | 渲染 `0` |
| 状态型数据行取数失败 | 显示 `—` | 显示 `0 件` / 空白 |
| 加载中 | `……` | 显示 `0` |
| **业务确认的零** | 照常显示 `0`（0 是结论） | 隐藏 |

证据：`QuickGridItem` 三态实现（[QuickGridItem.vue:30-37](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/QuickGridItem.vue#L30-L37)）；首页 Hero 待办总数「全失败显示 `···`」([home.vue:185-186](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L185-L186))；采集四态「某态为 0 也照常显示 0」([alerts.vue:48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L48))。三者语义相反且**都对**——判定标准是「这个 0 是结论还是缺失」。

### 5.4 重复点击当前 Tab 的行为

**既有实测（已核对 Vant 源码）**：`van-tabbar-item` 重复点击当前项**不触发 `change`**（`TabbarItem.mjs` 有 `if (!active.value)` 守卫），故工程改用 `@click` 自行比对路径（[TabbarLayout.vue:46-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L46-L60)、[useReselect.js:1-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/composables/useReselect.js#L1-L18)）。

规范动作（逐 Tab 定义，不得留空）：| Tab | 重复点击行为 |
| 首页 | 滚回顶部（`window.scrollTo(0, 0)`，已有） |
| 消息 | 若不在「通知」子视图 → 回「通知」；已在「通知」→ 刷新未读（[MessagePage.vue:39-43](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/message/MessagePage.vue#L39-L43)） |
| 我的 | 滚回顶部 |

**新增要求**：`Enter` 键兜底必须保留（Vant 的 TabbarItem 渲染为 `div[role=tab]`，原生不把回车转成 click，[TabbarLayout.vue:62-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L62-L67)）；`aria-current="page"` 必须显式绑定（Vant 不输出，[:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L98)）。

### 5.5 无障碍播报（`aria-live`）落地口径

| 场景 | ARIA | 现状 |
| ---- | ---- | ---- |
| 区块错误 | `role="alert"`（隐式 assertive） | 已广泛使用（`PageState` / `TodoGroup` / `LineChart` error 等） |
| 表单校验失败 | `role="alert"` | 已用（`hrDetail.vue:238`、`flowDetail.vue:408`、`payrollDetail.vue:212` 等） |
| 动态计数（如铺排预览） | `aria-live="polite"` | 仅 1 处（[schedule.vue:588](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L588)）→ **推广**：发布通知「接收人预览」([notificationPublish.vue:260-268](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/notificationPublish.vue#L260-L268))、KPI 权重合计条等 |
| 超时告警 | `role="status"` + 仅首次播报 | 已有（[SlaTag.vue:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/SlaTag.vue#L59)） |
| 图表 | `role="img"` + `aria-label` + `visually-hidden` 等价表 | `LineChart`/`KpiGauge` 已有；**新增图表必须同等** |
| 行操作 pending | `aria-busy="true"` | 缺（见 G-04） |

---

## 6. RBAC 展示口径

### 6.1 角色与路由真源

- `/boss/*` 全部页面 `roles: [ROLE.ADMIN]`（[router/index.js:35-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L35-L163)）。**老板端不存在第二角色可见的页面**。
- 例外：`/boss/kpi/:employeeId`、`/boss/hr/:employeeId` 等复用员工端页面的路由，`meta.roles` **仍为 ADMIN**（复用只发生在路由层面，权限不收窄，[router/index.js:109-114](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L109-L114)）。
- 判定单点化：`canAccess(roles, user)`（[permission.js:5-8](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/domain/permission.js#L5-L8)）——路由守卫与宫格项级白名单共用同一函数（[HomeQuickGrid.vue:32](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/HomeQuickGrid.vue#L32)）。

### 6.2 无权限时的降级视觉与文案（现状 + 规范）

| 层级 | 现状 | 规范 |
| ---- | ---- | ---- |
| 路由级 | 守卫 `showFailToast('当前演示身份无权访问该页面')` + 重定向到 `auth.homePath` | **保留**；补两点：① Toast 文案补「你是谁」与「去哪」→「当前身份（站长）无权访问该页面，已返回工作台」；② 重定向前记录一次埋点（用于统计误点） |
| 页面级 | **无独立 403 页**（`/boss/*` 与 `/staff/*` 分域，跨域访问一律重定向） | 保持不新增 403 页（避免第 3 个错误页形态）；若需展示原因，在 `homePath` 顶部用一条可关闭的 `van-notice-bar` 承接 |
| 入口级 | 宫格项按 `roles` 过滤后**不渲染**（不做置灰） | **保留**（无权限的入口留着会制造「点了没反应」） |
| 动作级 | 由契约下发派生标志（如 `canRevoke`/`canEdit`），前端不重复推导（[LeaveApprovalList.vue:216](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LeaveApprovalList.vue#L216)） | **保留**并写入规范：**权限判定不由前端推导，一律取服务端派生标志** |

### 6.3 只读态与可写态的视觉区分（现状缺失，需补）

**现状**：可写页与只读页**没有任何统一视觉标识**，仅靠「页面底部有没有 ActionBar」隐式区分。反例：`hrDetail.vue` 用「已离职 → 字段 `readonly` + `van-notice-bar` 说明」（[hrDetail.vue:184-232](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/hrDetail.vue#L184-L232)）是**唯一**把只读态讲清楚的地方。

**规范**：
1. **只读态必须显式声明**：页面若整页只读（如告警、排名、趋势）无需标识；若**同一页内存在可写/不可写切换**（离职员工、已关闭工单、已终审请假），必须在可写区域顶部给一条 `van-notice-bar`，文案格式：`<原因>，以下仅可查看`（沿用 `hrDetail` 的写法）。
2. **可写控件只读化**：用 `readonly`（`van-field`）或 `disabled`（按钮）+ 原因说明，**禁止**仅去掉边框让它「看起来像文字」。
3. **ActionBar 不可用时必须给 `note`**：`ActionBar` 已支持 `note` 显示在按钮上方（[ActionBar.vue:14-15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L14-L15)），全部 6 个消费页**必须传 note**（现状：`leaveSettings.vue:27-30`、`notificationPublish.vue:62-68`、`schedule.vue:109` 已传；`payrollDetail.vue` 的 `actionNote` 也存在，[payrollDetail.vue:85-92](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payrollDetail.vue#L85-L92)；`flowDetail` / `attendanceRule` 需核查补齐）。

### 6.4 敏感字段脱敏展示

**现状核实**：
- 脱敏**全部发生在 Mock 层**：`db.js`（[db.js:954](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L954)）、`hrStore.js`（[:379,:395,:398](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/hrStore.js#L379)）、`station.js`（[station.js:41](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/station.js#L41)）、`parcelStore.js`（[:233-234](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/parcelStore.js#L233-L234)）。
- `shared/domain/mask.js` 提供 `maskPhone` / `maskName` / `maskBankAccount`（[mask.js:9-33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/domain/mask.js#L9-L33)），头部注明「后端脱敏生效后本文件整体可删」。
- **老板端页面自身不做二次脱敏**：`hrDetail.vue` 仅注释说明依赖契约出参（[hrDetail.vue:175](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/hrDetail.vue#L175)）；`MeSection.vue` 直接渲染 `auth.user.phone`（[MeSection.vue:80](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L80)）。
- 唯一的兜底例外是员工端 `staff/profile.vue`（[staff/profile.vue:15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/profile.vue#L15) 注释），**老板端没有对应兜底**。

**规范**：
1. **脱敏以服务端为权威**（对齐 `api.md` 1.4 口径），前端不重复实现。
2. **但老板端必须补一层展示兜底**（与员工端 `profile.vue` 同策略）：手机号、银行卡、身份证类字段在 `van-cell` 的 `:value` 上经过 `maskPhone` / `maskBankAccount` 兜底——理由：`/auth/me` 与 `/hr/profiles/:id` 的出参是否已脱敏**未逐字段核实**（见 11 章 Q5），兜底是幂等操作（对已脱敏值再 `maskPhone` 会得到不同串，需用「值中包含 `*` 则原样透传」的判定短路）。
3. **禁止**在老板端页面新增「查看完整手机号」类解掩码交互（演示态无鉴权审计，解掩码无意义）。
4. 脱敏字段的读屏播报口径：播报脱敏后的值（不额外提示「已脱敏」）。

### 6.5 禁用 vs 隐藏（取舍规则，可直接判定）

| 条件 | 处理 | 依据 |
| ---- | ---- | ---- |
| 用户**有权限**但当前**状态不满足** | **禁用** + 给出原因（`:title` 或 `ActionBar` 的 note） | 现状正例：`payroll.vue:231-232`（无已通过单时禁用 + title 说明）、`NoticeList.vue:168-174`（无未读时禁用 + 「当前没有未读通知」）、`notificationPublish.vue:230-233`（驿站列表空时禁用「指定驿站」+ title） |
| 用户**无权限** | **不渲染**（入口级过滤） | `HomeQuickGrid.vue:32`；`QuickGridItem.vue:13-15` 明确「无权限不适用——过滤后不渲染，不做置灰」 |
| 动作在当前状态下**语义不成立**（如「标记已办」在待办快照里） | **不提供**（不是禁用） | [MessagePage.vue:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/message/MessagePage.vue#L19) 明确理由：会产生伪状态 |
| 危险操作且**批量** | **禁用**（未选目标）+ 复选框计数 | PC 侧既有范例；移动端现状无批量入口 |

**红线**：**不得隐藏关键操作**（发布/审核/审批/保存）——隐藏会让用户以为功能不存在，禁用 + 原因才可诊断。

### 6.6 前端拦截 vs 真正拦截

**既有原则（必须逐字写入实现规范）**：**前端只做体验拦截，真正拦截在 Mock 层**（[router/index.js:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L19)）。落地含义：
1. 前端 `canAccess` 只用于「不给入口 / 提前拦一道」，**不得**作为安全边界；
2. 数据可见范围由服务端强制收敛（`station_id` 覆盖等，[api/index.js:5-7](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/api/index.js#L5-L7)）；
3. 前端**不重复推导**越权判定（取服务端派生标志）；
4. 任何「前端已拦所以后端可省」的表述视为违规。

---

## 7. 跨浏览器兼容矩阵

> **状态列口径**：「已验证」= 工程内有浏览器实测记录；「未验证」= 无证据，禁止在交付时改述。

| 环境 | 已知风险 | 降级方案 | 状态 |
| ---- | ---- | ---- | ---- |
| **iOS Safari（iPhone）** | ① `100vh` 受动态工具栏影响（未验证）；② `document.execCommand('copy')` 已废弃但在非 HTTPS 下仍是唯一兜底（[WorkOrderCopyButton.vue:36-39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/WorkOrderCopyButton.vue#L36-L39) 已有失败兜底文案）；③ `parseTime` 行为未验证（M10 曾「实测逐字相同」后直接委托，但 iOS 未覆盖） | ① 真机若底部裁切 → `min-height: 100dvh` + `100vh` 双写；② 复制失败给「长按选中手动复制」（已实现）；③ 另行真机验证 | **未验证**（`SESSION-STATE.md` 遗留 25） |
| **Android Chrome** | ① `env(safe-area-inset-*)` 在无刘海设备为 0（正常）；② 底部手势条遮挡（已由 `--safe-bottom` 处理） | 无额外降级；验收点为「无横向滚动 + 底栏不被遮挡」 | **未验证** |
| **安卓 WebView 壳**（`hrm-android-shell`） | ① `--status-bar-height` 实测值未知；② `file://` 下 ESM 能否加载**未验证** → 离线包方案未实施；③ 壳未编译验证（AGP 为占位版本）；④ `getWifiInfo` 未实现（[bridge.js:51-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/utils/bridge.js#L51-L64) 回落模拟值） | ① 布局必须对 0 / 非 0 均不塌陷（见 2.3）；② 壳内统一走 HTTP 服务而非 `file://`（`base` 与 `file://` 结构性冲突已登记 `TODO(扩展)`）；③ 状态栏高度在「我的 → 运行环境」可见（[MeSection.vue:96-101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L96-L101)）便于现场诊断 | **未验证**（`hrm-android-shell/BUILD.md` 第 3/8 项同为「未验证」） |
| **企业微信内置浏览器** | ① 内核差异（Android 走 X5/系统 WebView、iOS 走 wkwebview）导致 `position: fixed`、`env()`、`100vh` 表现不一；② **工程内无任何证据** | ① `mobile.scss` 已用 `overflow-x: hidden` 兜底；② 固定栏一律 `position: fixed` + 底部 `--safe-bottom`；③ 若企微内出现固定栏错位 → 提供「内嵌模式」开关（`position: static` 降级，登记 `TODO(扩展)`） | **未验证（无工程证据）** |
| **桌面浏览器窄窗** | ① `#app` 480 居中 → 窄窗（<480）铺满、宽窗（>480）居中；② 固定栏限宽依赖媒体查询 source order，改动顺序会破版 | ① 已由 `mobile.scss` 末尾媒体查询 + 三处固定栏同步覆盖处理；② 实现新固定栏时必须加入 `mobile.scss:422-428` 的选择器列表 | **部分验证**（固定栏限宽实测不冲突，宽屏 ≥1280 未验证） |
| **桌面浏览器宽窗（≥1280）** | 未验证（`SESSION-STATE.md` 遗留 25） | 无降级；只需确认「居中列 + 固定栏对齐」 | **未验证** |
| **键盘导航全链路** | ① Vant Tabbar 不处理 Enter（已自补，[TabbarLayout.vue:62-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L62-L67)）；② Vant 不输出 `aria-current`（已自补）；③ **老板端仍有 3 处可点 `div` 无 `role`/`tabindex`**（见下） | ① 修 alerts 三处（`role="button"` + `tabindex="0"` + `keydown.enter/space`）；② 焦点环已有全局规则（[mobile.scss:38-42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L38-L42)），需实测未被 `overflow:hidden` 裁切 | **静态审出缺陷；全链路未验证** |
| **`prefers-reduced-motion`** | 全局已降级（[tokens.base.scss:244-253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L244-L253)） | 新增动效不得绕过该媒体查询 | 代码级已验证 |

**老板端跨浏览器必修项**（清点）：
1. [alerts.vue:152-157](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L152-L157)（超 48h 未取件行）；
2. [alerts.vue:181](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L181)（同步失败驿站行）；
3. [alerts.vue:202-207](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L202-L207)（超时未处理工单行）。
> 对照：`rank.vue` / `workorder.vue` / `home.vue` 的同一形态已正确使用 `role="button" + tabindex="0" + @keydown.enter/space`（[rank.vue:102-111](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L102-L111)），说明这是**遗漏而非模式**。

---

## 8. 数据可视化规范（重点）

### 8.1 现状核实：`LineChart` 与 `KpiGauge` 的实现方式与限制

**`LineChart.vue`（自绘 SVG，240 行核心逻辑）**

实现方式（关键）：

| 机制 | 实现 | 行号 |
| ---- | ---- | ---- |
| 尺寸 | 按**容器实测宽**绘制，非固定 viewBox 拉伸；`H = compact ? 96 : 200`；`PAD` 两档 | [LineChart.vue:33-36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L33-L36) |
| 重绘 | `ResizeObserver` + **100ms 防抖**；`width || 340` 兜底 | [:44-63](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L44-L63) |
| 量纲 | `1/2/5 × 10ⁿ` 的 `niceMax`，保证 3 档刻度取整不重复（修 PC 侧 P11 缺陷） | [:75-98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L75-L98) |
| 轴/基线 | 网格只画非 0 档，0 档由**加粗基准线**承担 | [:100-101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L100-L101)、[:252-263](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L252-L263) |
| X 轴标签 | 最多 6 个，首尾必留 | [:123-132](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L123-L132) |
| 数据点 | ≤14 点逐点；>14 点仅末点 + 光晕 | [:136-139](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L136-L139) |
| 触摸读数 | 十字线 + 浮层（白底 + 描边 + **系列名文字**，不靠颜色区分） | [:146-172](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L146-L172)、[:406-414](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L406-L414) |
| 图例开关 | 状态由**页面持有**（切 7/30 天不丢）；关闭态用文字 + 半透明圆点双通道；热区 44px | [:22-23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L22-L23)、[:457-496](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L457-L496) |
| 无障碍 | `role="img"` + `aria-label`（含合计）+ `visually-hidden` 数据表 | [:186-191](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L186-L191)、[:417-438](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L417-L438) |
| 三态 | loading（3 条骨架）/ error（+44px 重试）/ empty；**绘图区高度固定不跳动** | [:224-237](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L224-L237)、[:624-678](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L624-L678) |

**已确认的能力边界（写规范必须承认）**：

| # | 限制 | 证据 | 影响 |
| ---- | ---- | ---- | ---- |
| L-1 | **仅支持 2 个固定系列**（`inbound` / `pickup`），系列名与颜色硬编码 | [:27-30](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L27-L30)、[:535-542](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L535-L542) | 第三指标（异常件）不能直接加；需改组件接口 |
| L-2 | **不支持缺失/断点**：`points` 必须连续，缺日期只能补 0（补 0 与「当天真的是 0」视觉不可区分） | `stepX` 按索引等距 [:65-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L65-L67) | 断网日会被画成「业务归零」 |
| L-3 | **不支持负值**：`maxValue` 取 `Math.max(1, ...values)`，负值会被画到基准线以下且轴不变 | [:70-73](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L70-L73)、[:107-109](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L107-L109) | 环比等双向数据不能进本组件 |
| L-4 | X 轴标签无年份；`DATE_SLICE=5` 取 `MM-DD` | [:31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L31) | 跨年区间歧义（>365 天不可用） |
| L-5 | `compact` 档**无 X 轴、无网格**，仅保留末值标注 | [:33-36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L33-L36)、[:253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L253)、[:373-374](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L373-L374) | 迷你图只有形状，量级靠末值锚点（已修 P36） |
| L-6 | **无降采样**：所有点都进 `polyline` | [:111-113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L111-L113) | 30 天无问题；>180 点时点密度与性能需评估 |
| L-7 | 面积仅画入库（`inbound`），取件只画线 | [:115-120](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L115-L120) | 两条面积会互相遮挡，属有意设计 |

**`KpiGauge.vue`（环形仪表）**：`viewBox="0 0 80 80"` 固定、半径 36、描边 8px（与 `--gauge-size: 80` 1:1 不缩放，[:37-39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/KpiGauge.vue#L37-L39)）；达成率 **>100% 时环封顶但中心数字显示真实值**（[:26-28](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/KpiGauge.vue#L26-L28)）；空/错误态渲染灰环 + `—`（[:36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/KpiGauge.vue#L36)）；`role="img"` + `aria-label`（[:29-31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/KpiGauge.vue#L29-L31)）。限制：**仅支持单值达成率**，不能表达分段进度。

### 8.2 图表类型选择（老板端）

| 场景 | 图表类型 | 现用组件 | 决定 |
| ---- | ---- | ---- | ---- |
| 包裹趋势（7/30 天，入库 vs 取件） | 双系列折线 + 面积 | `LineChart`（full） | 保持 |
| 首页迷你走势 | 迷你折线（无轴，末值标注） | `LineChart`（compact） | 保持 |
| 驿站排行（相对量） | **横向条形（非全宽）** | 手写 `van-progress` ×2 | 收口到 `BossRankBar`（N-01） |
| KPI 达成率 | 环形仪表 | `KpiGauge` | 保持 |
| **构成占比**（采集四态、考勤构成） | **环图或 100% 堆叠条** | 无 | 新增 `BossDonutChart`（N-02）；**四态建议用 100% 堆叠条**（宽 ≤480 时环图图例易折行） |
| 费用构成（工资单明细） | **不用图表**（明细表更可读） | `MyPayrollCard` 明细 | 保持 |
| 数量对比（少于 5 项） | 不用图表，用指标卡 | `StatCard` | 保持（避免「4 个数画成饼图」） |

**判定规则（防止滥用）**：
- 数据点 ≤ 4 且总量确定 → 用指标卡/占比条，**不画环图**；
- 时间序列 ≤ 3 点 → 不画折线（画折线会误导出趋势，点太少）；
- 相对值（非绝对值）**禁止**用面积图（面积暗示绝对量）。

### 8.3 尺寸、栅格、坐标轴与刻度

| 项 | 规范 | 依据 |
| ---- | ---- | ---- |
| 图表容器 | `.card` 内（`padding: var(--sp-4)`，圆角 `--r-lg`） | [mobile.scss:114-119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L114-L119) |
| 高度 | full = 200px，compact = 96px，环形 = 80px；**固定高度**（状态切换不跳动） | `LineChart.vue:33`、`KpiGauge.vue:58-60` |
| 内边距（绘图区） | full: top 12 / right 12 / bottom 24 / left 32；compact: 8 全边 | `LineChart.vue:34-36` |
| 刻度数 | full 3 档（含 0），compact 2 档 | `LineChart.vue:87-98` |
| 刻度上限算法 | `1/2/5 × 10ⁿ`，**必须整除且取整后不重复**（3 档去重逻辑已内置） | `LineChart.vue:75-98` |
| 基准线 | 0 轴加粗（`--chart-axis`），网格不重复画 0 档 | `LineChart.vue:100-101` |
| 轴文字 | 11px（`--fs-micro`），fill `--text-3`（对白卡实算 4.834:1 ✓） | `LineChart.vue:509-512` |
| 折线 | `stroke-width: 2`，`vector-effect="non-scaling-stroke"`（防缩放变粗） | `LineChart.vue:529-542` |
| 数据点 | 半径 2.5 / 3，`fill: --surface-card` + 系列色描边 | `LineChart.vue:544-555` |
| 8px 栅格 | 所有间距取 `--sp-*`；图表高度 96/200/80 均为 8 的倍数 | 全局 |
| 新增图表尺寸 | 占比环 120px / 描边 12px（见 3.3，进移动端私有层） | 新提案 |

### 8.4 数据缺失与负值处理

| 情况 | 强制规则 | 说明 |
| ---- | ---- | ---- |
| 某日无数据（接口缺口） | **不得静默补 0 当作真值**。优先要求契约补齐该日期并回 `null`；若只能给 0，用 `BossScopeNote` 显式说明「无数据日按 0 计入合计」 | 现状 `home.vue` 的环形/趋势均按 0 处理（`trend` 直接 `reduce` 求和，[home.vue:139-150](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L139-L150)） |
| 全部为 0 / 空数组 | 走 **empty 态**（`LineChart` 已实现：`pointsCount === 0` → empty，[LineChart.vue:200](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L200)），**不画一条贴 0 的直线** | — |
| 负值 | **禁止传入 `LineChart`**（L-3）。负值场景（环比下降、扣款）一律用 `BossMetricDelta` 或明细文本表达 | — |
| 极大值（量级跳变） | `niceMax` 自动升档，但须检查 X 轴标签不被压缩至重叠（6 标签上限已保护） | — |
| 达成率 > 100% | 环封顶、数字显示真实值（`KpiGauge` 已实现） | `KpiGauge.vue:26` |
| 单点数据 | 折线退化为点，须保证末值标注可见（`showEndDot` 已覆盖） | `LineChart.vue:137` |

### 8.5 色彩语义（须与 `--chart-*` 系列 Token 一致）

**定义位置（已核实）**：[tokens.base.scss:180-190](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L180-L190)

| Token | 值 | 语义 | 对白卡实算 |
| ---- | ---- | ---- | ---- |
| `--chart-inbound` | blue-500 | 入库 | 3.245:1 ✓（SC 1.4.11 ≥3:1） |
| `--chart-pickup` | green-600 | 取件 | 3.463:1 ✓ |
| `--chart-abnormal` | orange-500 | 异常 | 未单独实算（与 inbound 同族量级）→ 须补算 |
| `--chart-area` | rgba(24,144,255,.08) | 入库面积 | 装饰，不承载信息 |
| `--chart-axis` | neutral-300 | 轴线/准线/空态图标 | 非文本 |
| `--chart-skeleton` | neutral-100 | 图表骨架 | 装饰 |

**规范**：
1. 图表系列色**只能用 `--chart-*`**，不得直接用 L1（`--c-blue-500` 等）；现状 `rank.vue:16-18` 已正确使用（[rank.vue:16-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue#L16-L18)）。
2. 系列色**不能是唯一区分手段**（图表必须带图例文字 + 浮层系列名，`LineChart` 已满足）。
3. 新增图表若需第 4 个系列色 → 先评估 `--color-accent`（orange-500）是否与异常色语义冲突；**冲突则宁可减系列**（禁止新增第 4 个色板）。
4. 图表内**语义色**用法：红=异常/超时、橙=临近/警告、绿=达成/正常、蓝=主口径（与 `StatusTag` / `SlaTag` 同口径，[StatusTag.vue:101-107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatusTag.vue#L101-L107)）。

### 8.6 无障碍替代文本（图表必须有文字等价）

| 要求 | 实现 |
| ---- | ---- |
| 容器 | `role="img"` + `aria-label` 概述（含量纲与合计） |
| 明细等价 | `visually-hidden` 的 `<table>`（caption + th scope），逐点/逐项给出数值 |
| 交互控件 | 图例 `button` + `aria-pressed`（[LineChart.vue:209-220](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L209-L220)） |
| 空/错误 | 错误 `role="alert"` + 可聚焦重试（[LineChart.vue:229-233](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue#L229-L233)） |
| 环形/排行 | `KpiGauge` 已有 `aria-label`；`BossRankBar` 每行给 `aria-label`（含名次与占比） |
| 红线 | **无文字等价表达的图表即缺陷**；`role="img"` 但不给 `aria-label` 同样算缺陷（工程内 P33 已记录该历史问题） |

### 8.7 大数据量降采样

| 数据量 | 处理 |
| ---- | ---- |
| ≤ 30 点 | 不降采样，全量绘制（现状） |
| 31–180 点 | **保留全量**，但必须缩短 X 标签到 6 个（已实现）并关闭逐点圆点（已实现，>14 点仅末点） |
| > 180 点 | **窗口聚合**：按 `ceil(n / 90)` 合并相邻点为均值（需在图表外做，保持组件纯粹）；或改用「周/月聚合」契约字段。**登记 `TODO(扩展)`**，本轮不实现 |
| 排行类 | 相对值条最多渲染 8 行，其余下沉到独立页 |

### 8.8 图表组件接口草案（可复用）

#### C-01 `BossLineChart`（`LineChart` 的老板端超集，建议**原地扩展**而非新写）

```text
props:
  points: Array<{ date, [seriesKey]: Number|null }>
  series: Array<{ key, label, color?: Token, type: 'line'|'area' }>   // 取代硬编码 inbound/pickup
  unit: String = '件'
  height?: 'compact'|'full' = 'full'
  loading / error / emptyText
  downsample?: { maxPoints: Number = 180 }                            // 超限按均值聚合
model:
  visible: { [seriesKey]: Boolean }                                   // 图例开关由页面持有（保持既有约定）
emits:
  retry, point-click({ index, date })
a11y:
  ariaLabel 自动拼「近 N 天 <label> 趋势；<label> 合计 X 件」
  visually-hidden 表由 series 动态生成列
```

> **改造归属**：`LineChart` 属共享内核（员工端 `staff/kpi`、`boss/home`、`boss/trend` 三处消费）→ 接口扩展需保持**向后兼容**（`points.inbound/pickup` 缺省映射为系列 `入库/取件`）；扩展方案须过主智能体评审。

#### C-02 `BossDonutChart`：见 N-02。
#### C-03 `BossRankBar`：见 N-01。
#### C-04 `BossMiniTrend`：**不新建**——用 `BossLineChart` 的 `height="compact"`，避免第三份折线实现（现存 P10/P36 已因「第二份实现」返工过一次）。

---

## 9. 反模式清单（老板端禁止项 + 可检查判定标准）

| # | 反模式 | 判定标准（可机械检查） | 现状是否已违反 |
| ---- | ---- | ---- | ---- |
| **AP-01** | 用颜色单独传达状态 | 颜色是唯一差异通道 → 违规。检查：把页面截图转灰度后，状态仍可区分（字重/图标/文字/位置任一保留即可） | 未违反（`SlaTag` 超时带 `aria-label` 与文字、`StatusTag` 带文字、Tabbar 选中态色 + 字重） |
| **AP-02** | 用 0 冒充加载失败 | 搜索 `== null` / `?? 0` / `|| 0` 落在「展示数值」的路径上 → 违规；应渲染 `—` / 不渲染 | 未违反（`QuickGridItem` / `home.vue` 已正确） |
| **AP-03** | 卡片套卡片 | 卡片内出现第二层带 `box-shadow` + `border-radius` 的卡片 → 违规；内层应为 `--surface-subtle` 浅底无阴影 | 未违反（`alerts.vue:351-355` 是正例） |
| **AP-04** | 无文字等价图表 | 图表容器无 `aria-label` 或无 `visually-hidden` 数据表 → 违规 | 未违反（`LineChart` / `KpiGauge` 已合规） |
| **AP-05** | 隐藏而非禁用关键操作 | 关键动作（发布/审核/审批/保存）在「状态不满足」时直接 `v-if` 移除 → 违规；应禁用 + `title`/`note` 说明 | 未违反（`payroll` / `NoticeList` / `ActionBar` 均为禁用 + 原因） |
| **AP-06** | **同一语义两套配色** | 同一「第 3 名」在两处取不同 Token → 违规 | **已违反**：`home.vue:411-415` 用 `--c-orange-600`，`rank.vue:189-192` 用 `--rank-3-bg`（实算 3.556:1 vs 7.090:1）→ 必修 |
| **AP-07** | 无确认的批量/不可逆操作 | `publishAll` / `batch-by-station` / `clear` 类动作无 `BossConfirm` → 违规 | 未违反（`schedule` / `payroll` 均已二次确认） |
| **AP-08** | 静默失败 | 写操作 `catch` 后无任何用户可见反馈，或失败被渲染成空态 → 违规 | 未违反（但见 5.2 白名单必须登记理由） |
| **AP-09** | 点击热区 < 44×44 | 计算 `(padding × 2 + 行高 × 2 + 边框)` < 44 → 违规 | 未违反（既有实测 375px 下不达标 0 个） |
| **AP-10** | 硬编码色值 / 字号 / 间距 | `grep -E "#[0-9a-fA-F]{6}|[0-9]+px"` 命中 `views/boss/**` 的 `style` → 违规（除 0/1px 边框与图标尺寸白名单） | 未违反（`alerts.vue:306-308` 注释记录的旧 `--hrm-*` 引用已清理） |
| **AP-11** | 用 emoji 承载状态 | 模板出现 emoji → 违规（字形跨平台不一致） | 未违反 |
| **AP-12** | 数字不用等宽 | 数值未加 `.tabular-nums` → 违规（看板/排行/表格内必须加） | 未违反（现状普遍已用） |
| **AP-13** | 无权限入口置灰 | 无权限项渲染为 disabled → 违规（应过滤不渲染） | 未违反（`HomeQuickGrid:32` + `QuickGridItem:13-15`） |
| **AP-14** | 长列表无边界 | 列表页面无软上限、无虚拟化、且筛选默认「全部」→ 违规 | **待办**：`attendanceRecords` / `workorder` / `NoticeList` 需按 4.5 加软上限 |
| **AP-15** | 骨架与真实内容不等高 | 骨架块高度 ≠ 内容高度 → 数据到达时跳动即违规 | 未违反（`home.vue` / `attendance.vue` 均按真实版面给高） |
| **AP-16** | 图表内系列色唯一定义 | 图表里出现非 `--chart-*` 的系列色 → 违规 | 未违反 |
| **AP-17** | 员工端样式写入全局工具类 | 老板端专属样式写进 `mobile.scss` 通用类 → 违规 | 未违反（本次新增须放 `components/boss/`） |
| **AP-18** | 前端推导权限 | 页面内写 `if (role === 'ADMIN')` 自行判权（而非取服务端派生标志 / `canAccess`）→ 违规 | 未违反 |

---

## 10. 设计走查检查表（供测试工程师逐项勾选）

### A. 响应式与多设备

- [ ] 320 / 360 / 375 / 390 / 414 / 430 / 480 px 七档下**无横向滚动条**（`document.documentElement.scrollWidth === clientWidth`）
- [ ] 横屏（640 档）下固定栏（NavBar / Tabbar / ActionBar）与内容列**左缘对齐**，不横跨视口
- [ ] 宫格在 320 / 375 px 下每格**可点区 ≥ 44×44**，横向溢出 0px（真机复测，非仅窗口）
- [ ] 有刘海/手势条的设备上，顶部内容不被遮挡、底部固定栏与最后一项不重叠（`--safe-top` / `--safe-bottom` 生效）
- [ ] 长文本（运单号 20+ 字符）、超长数字（≥ 10 万）不撑破容器、不截断关键位
- [ ] 横竖屏切换后图表自动重绘且无横向溢出

### B. 组件状态完整性

- [ ] 18 个使用 `PageState` 的页面，在**断网**下显示错误态 + 可点重试（不是空态、不是白屏）
- [ ] 空数据下显示空态文案（与错误态文案必须不同）
- [ ] `StationPicker` / `MonthPicker` / `ActionBar` / `MeSection` 的缺口状态（G-01 ~ G-05）落地后逐项验证
- [ ] 骨架出现延迟 ≈200ms（快速接口不闪骨架），骨架高度与真实内容一致（无跳动）
- [ ] 所有可点元素有按下态（`:active`）视觉反馈

### C. 交互反馈

- [ ] 写操作（发布/审核/审批/保存/铺排）成功后**必有**成功提示 + 就地更新（不整页重刷）
- [ ] 失败必有提示，且含下一步建议；**无一处静默失败**（对照 5.2 白名单逐条核对）
- [ ] 计数型角标/状态型数据行取数失败时显示 `—` 或不渲染，**无一例显示 0**
- [ ] 危险操作二次确认具备四要素（动词标题 / 对象 / 影响面 / 不可逆声明）
- [ ] 重复点击当前 Tab 行为符合 5.4 表（首页滚顶、消息回通知子视图并刷新）
- [ ] 表单校验失败就地显示（`role="alert"`），不叠全局 Toast

### D. RBAC

- [ ] 站长/员工身份访问任一 `/boss/*` 路由，被拦并提示，**不发生白屏**
- [ ] 无权限的宫格入口**不渲染**（非置灰）
- [ ] 只读态页面（含离职员工、已终审单据）有显式只读说明；可写控件为 `readonly`/`disabled` + 原因
- [ ] 服务端派生标志（`canRevoke` 等）变更后界面同步（不出现「按钮可点但接口 403」）
- [ ] 敏感字段（手机号/银行卡）在老板端各页均以脱敏形态出现

### E. 无障碍

- [ ] 对比度：全部文本 ≥ 4.5:1，非文本图形 ≥ 3:1（按附录 A 逐项复核；`--text-disabled` 仅用于真禁用）
- [ ] 键盘可达：Tab 顺序合理，焦点环可见（未被 `overflow:hidden` 裁切）；`alerts` 三处行操作可 `Enter`/`Space` 触发
- [ ] 图表有 `aria-label` 与 `visually-hidden` 等价表
- [ ] 超时告警只播报一次（不随 30s 心跳重复）
- [ ] `prefers-reduced-motion` 下无位移/缩放动画

### F. 数据可视化

- [ ] 图表状态四态齐备（loading/error/empty/ready），切换时**高度不变**
- [ ] 0 基准线可见；3 档刻度取整后不重复
- [ ] 系列色取自 `--chart-*`；图例可开关且状态在切范围后保留
- [ ] 负值未传入折线图；缺失日未被静默画成 0 趋势
- [ ] 排行/环形百分比有文字等价表达

### G. 兼容性

- [ ] Android Chrome 与 iOS Safari 各走一遍首页 + 3 个列表页 + 1 个写操作
- [ ] 安卓壳内：内容不被状态栏遮挡，布局不塌陷，`--status-bar-height` 与「我的 → 运行环境」显示一致
- [ ] 企业微信内置浏览器内打开：无白屏、无固定栏错位（**未验证项，本轮必须实测**）
- [ ] 桌面浏览器窄窗（<480）与宽窗（>1280）内容列居中、固定栏对齐

### H. 反模式核对

- [ ] 第 9 章 AP-01 ~ AP-18 逐条扫描，**AP-06（同一名次两套配色）必修**、**AP-14（长列表无边界）必修**

---

## 11. 需主智能体裁决的开放问题（5 条）

| # | 问题 | 影响面 | 我的建议 |
| ---- | ---- | ---- | ---- |
| Q1 | 是否采纳 `src/mobile/modules/boss/components/` **老板端专属组件目录**？（现状 25 组件 0 专属，老板端差异只能靠页面级样式硬写） | 组件库结构、评审边界、后续维护成本 | 采纳。专属组件不改共享内核，符合「单工程内按域拆模块」的既定形态 |
| Q2 | 平板档（768–992）保持 **480 居中单列**，还是引入双列？ | 响应式方案、可能波及员工端共用样式 | 保持单列（信息密度稳定、零改动、与既有实测不冲突）。若业务要求双列，须单独出方案 |
| Q3 | 趋势数据契约是否支持**缺失日（null）**与**负值**？ | 决定图表是否需支持断点与双向轴（现 `LineChart` 均不支持） | 先核实契约；契约不支持则用 `BossScopeNote` 显式声明「缺失日按 0」，并把双向轴登记 `TODO(扩展)` |
| Q4 | `attendanceRecords` / `workorder` / `NoticeList` 的**长列表软上限（500 条）**是否接受？虚拟化是否排入下一轮？ | 二期 20 万级数据下的 DOM 与滚动性能 | 本轮加软上限（低成本），虚拟化排下一轮并登记 `TODO(扩展)` |
| Q5 | `/auth/me` 与 `/hr/profiles/:id` 的出参**是否已对手机号脱敏**？老板端是否补前端兜底？ | 敏感字段展示合规性、脱敏双份实现的取舍 | 先核实契约；未脱敏则补前端兜底（幂等短路，参考 `staff/profile.vue`），并保留「后端接管后删除」的 `TODO(扩展)` |

---

## 附录 A：对比度实算表与计算方式

### A.1 计算方式（W3C WCAG 2.x 相对亮度公式）

$$
L = 0.2126 \cdot R + 0.7152 \cdot G + 0.0722 \cdot B, \quad
C_{lin} = \begin{cases} C_{srgb}/12.92 & C_{srgb} \le 0.03928 \\ ((C_{srgb}+0.055)/1.055)^{2.4} & \text{otherwise}\end{cases}
$$
$$
\text{Contrast} = \frac{L_{light} + 0.05}{L_{dark} + 0.05}
$$

**实算命令（可复现，node 24 实测）**：

```bash
node -e "function L(h){h=h.replace('#','');const c=[0,2,4].map(i=>parseInt(h.substr(i,2),16)/255).map(v=>v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4));return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]} function R(a,b){const x=L(a),y=L(b);return ((Math.max(x,y)+0.05)/(Math.min(x,y)+0.05)).toFixed(3)} console.log(R('#6B7280','#FFFFFF'))"
```

> 本文所有数值均由该函数实算，**未使用手算估值**（工程既有教训：`--gauge-fill` 手算记「约 3.1:1」，实算 2.839:1 不达标）。

### A.2 实算结果（老板端消费面）

| # | 前景 / 背景 | 用途 | 实算 | 判定 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | `--text-3` `#6B7280` / 白卡 | 元信息、口径说明 | **4.834** | ✓ AA |
| 2 | `--text-3` / `--surface-page` `#F5F7FA` | 页面底上的辅助文字 | **4.505** | ✓ AA（余量极小，**不得再降档**） |
| 3 | `--text-2` `#4B5563` / 白卡 | 次级正文 | **7.557** | ✓ |
| 4 | `--color-primary` `#0958D9` / 白卡 | 主色文字、ActionBar 文字 | **6.159** | ✓ |
| 5 | `--color-success` `#237804` / 白卡 | 成功文字、环比上升 | **5.585** | ✓ |
| 6 | `--color-warning` `#B45309` / 白卡 | 警告文字 | **5.022** | ✓ |
| 7 | `--color-danger` `#CF1322` / 白卡 | 危险文字、环比下降 | **5.571** | ✓ |
| 8 | `--chart-pickup` `#389E0D` / 白卡 | 取件折线 | **3.463** | ✓ SC 1.4.11（非文本 ≥3） |
| 9 | `--chart-inbound` `#1890FF` / 白卡 | 入库折线 | **3.245** | ✓ SC 1.4.11 |
| 10 | `--gauge-fill` `#0958D9` / `--gauge-track` `#EDF0F4` | 环形进度 | **5.388** | ✓（修 P0-5 前为 2.839） |
| 11 | `#1890FF` / `#EDF0F4`（**旧值对照**） | — | **2.839** | ✗ 已废弃 |
| 12 | 白 / `--rank-2-bg` `#6B7280` | 第 2 名徽标 | **4.834** | ✓ AA |
| 13 | 白 / `--rank-1-bg` `#B45309` | 第 1 名徽标 | **5.022** | ✓ AA |
| 14 | 白 / `--c-orange-600` `#D46B08` | **`home.vue` 第 3 名徽标（现状）** | **3.556** | ✗ **不达 AA**（见 AP-06） |
| 15 | 白 / `--rank-3-bg` `#92400E` | 第 3 名徽标（正确值） | **7.090** | ✓ AA |
| 16 | `--color-primary` / `--color-primary-surface` `#E8F4FF` | chip 选中态文字 | **5.519** | ✓ |
| 17 | `--text-disabled` `#CBD2DA` / 白卡 | 禁用文字 | **1.525** | 豁免（WCAG 1.4.3 失效控件） |
| 18 | `--text-placeholder` `#9AA4B2` / 白卡 | 输入占位符 | **2.522** | ⚠️ 不达 4.5；占位符属提示性文本，**本轮保留但登记**（若视为正文须改档） |
| 19 | `--border-control` `#8A93A0` / 白卡 | 控件描边 | **3.106** | ✓ SC 1.4.11 |
| 20 | `--color-warning-icon` `#FAAD14` / 白卡 | 警告图标（若作唯一指示） | **1.900** | ⚠️ 图标若承载语义须 ≥3:1 → **须放在浅底上或配文字** |
| 21 | `--color-primary-icon` `#1890FF` / 白卡 | 图标、焦点环 | **3.245** | ✓ SC 1.4.11 |
| 22 | `--color-warning` / `--color-warning-surface` `#FFFBE6` | 临近超时标签 | **4.829** | ✓ |
| 23 | `--color-danger` / `--color-danger-surface` `#FFF1F0` | 危险浅底标签 | **5.065** | ✓ |
| 24 | `--color-success` / `--color-success-surface` `#F6FFED` | 成功浅底标签 | **5.438** | ✓ |
| 25 | 白 / `--color-danger` | 超时实心标签、角标 | **5.571** | ✓ |
| 26 | `#D7E2F4`（白 82% 合成）/ `#0958D9` | Hero 副文本 | **4.715** | ✓ AA（余量小，**不得改透明度**） |
| 27 | 白 / `--c-neutral-800` `#1F2937` | Hero 深蓝灰底文字 | **14.679** | ✓ |

**结论**：老板端现存**唯一硬性超标项**为 #14（`home.vue:411-415` 第 3 名徽标配色分叉），另有 2 项需登记监控（#18 占位符、#20 警告图标）。

---

## 附录 B：老板端 Vant 反馈调用分布（用于 `BossConfirm` 收敛范围）

`showConfirmDialog|showSuccessToast|showFailToast|showToast` 命中分布（`grep -c` 实测，共 63 处 / 12 文件）：

| 文件 | 命中数 |
| ---- | ---- |
| [schedule.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue) | 13 |
| [payrollDetail.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payrollDetail.vue) | 10 |
| [notificationPublish.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/notificationPublish.vue) | 6 |
| [payroll.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/payroll.vue) | 6 |
| [flowDetail.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/flowDetail.vue) | 5 |
| [kpi.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/kpi.vue) | 5 |
| [hrDetail.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/hrDetail.vue) | 4 |
| [leaveSettings.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/leaveSettings.vue) | 4 |
| [makeupApproval.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/makeupApproval.vue) | 4 |
| [attendanceRecords.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRecords.vue) | 2 |
| [attendanceRule.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendanceRule.vue) | 2 |
| [workorder.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue) | 2 |

**无 `showConfirmDialog` 的 9 页**（核对是否为纯只读，若后续加写操作须补确认）：`alerts`、`attendance`、`flow`、`hr`、`home`、`me`、`leaveApproval`(→ 由 `LeaveApprovalList` 内的审批弹层承担确认)、`rank`、`trend`。

---

## 附录 C：与既有文档的关系（避免规范冲突）

| 既有文档 | 本规范的处理 |
| ---- | ---- |
| `demo-ui-redesign.md` | 全部 Token / 尺寸 / 对比度结论**继承不推翻**；仅补充「老板端消费面」视角与实算复核（附录 A） |
| `demo-ux-improvement.md` | M1 冻结规范；本规范未推翻任何 A/B 章条目，仅将其中与老板端相关的准入、降级、文案口径**固化** |
| `demo-mobile-nav-redesign.md` | 三 Tab 信息架构与 95 条迁移映射**为生效真源**；本规范不新增/移动任何入口，只对该架构下的页面做精细化 |
| `demo-leave-design.md` / `demo-sync-config-design.md` | 业务语义（两级审批、采集四态）**为真源**；本规范只定义其展示口径与状态呈现 |
| 待回写项 | 若本规范的 ①`modules/boss/components/` 目录（**已采纳**，回写口径见 §12） ②长列表软上限 ③`LineChart` 接口扩展 被采纳，需在 `demo-mobile-nav-redesign.md` 与 `SESSION-STATE.md` 追加注记（由主智能体执行） |

---

## 12. 主智能体裁决（Review 结论 · 2026-09-22）

> 最终口径以本节为准；与本章之前的表述冲突时，一律以本节覆盖。主智能体已独立复核本文关键数值与缺陷，复核方式见「复核」列。

### 12.1 五项开放问题裁决

| # | 裁决 | 复核与依据 |
|----|----|----|
| Q1 | **采纳老板端专属组件目录，但落点改为 `src/mobile/modules/boss/components/`**（非 `components/boss/`） | 复核：`eslint.config.js:25` 的 `'src/**/components/**'` 已覆盖该路径，lint 零额外成本；归属清晰、与 staff 域同构。**实质要求不变**：命名前缀 `Boss*`；专属样式禁止写入 `mobile.scss` 全局工具类 |
| Q2 | **平板档（768–992）保持 480 居中单列**，不引入双列 | 移动端为手机形态壳，双列会波及员工端共用样式；既有实测（375px 横向溢出 0px）不受影响。双列若确有业务需要，须单独出方案 |
| Q3 | **本轮不改趋势契约**，改由组件层防御 | 契约扩展会波及 Mock 与 878 项冻结断言（`verify-mock.mjs` 不可改写）。要求：非有限值（`null`/`NaN`/负值）**不参与绘制**，并在图例区显示「数据缺失 / 负值不展示」的可见提示 + `BossScopeNote` 声明口径。**禁止**把缺失日按 `0` 绘制——用 0 冒充缺失会把「无数据」画成「量为 0」，与工程「绝不用 0 表示加载失败」同源红线。双向轴登记 `TODO(扩展)` |
| Q4 | **本轮加 500 条软上限；虚拟化登记 `TODO(扩展)`** | Vant 4 无官方虚拟列表组件，需自研窗口化，成本超本轮范围；软上限为低成本止血 |
| Q5 | **老板端不补前端兜底脱敏**（推翻本文建议） | **复核证明 Mock 层（服务端语义）已统一脱敏**：`shared/mock/hrStore.js:379,395,585`、`shared/mock/parcelStore.js:234`（`receiverPhone`）、`shared/mock/db.js:954`、`shared/mock/routes/station.js:41` 均已调用 `maskPhone`/`maskBankAccount`。前端再打码会造成**双重脱敏**（`138****5678` → `138****`）。§6.4 口径改为：**展示层不得自行脱敏，也不得从其他字段还原明文**；`shared/domain/mask.js:1` 的 `TODO(扩展): 后端脱敏生效后删除前端兜底` 保持不变（那是给真实后端的） |

### 12.2 关键数值与缺陷复核（主智能体独立验证）

| 断言 | 复核方式 | 结果 |
|----|----|----|
| `--c-orange-600`（`#D46B08`）承白字 **3.556:1** 不达 AA | 独立按 WCAG 2.x 相对亮度公式实算 | ✅ 数值一致，确为 **3.5555** |
| `--rank-3-bg`（`--c-bronze-700` `#92400E`）**7.090:1** | 同上 | ✅ 数值一致，确为 **7.0888** |
| `alerts.vue` 3 处可点 `<div>` 无 `role`/`tabindex` | 独立读 `alerts.vue:152-157`、`:181`、`:202-207` | ✅ 属实，键盘不可达，判为遗漏（同形态在 `rank`/`workorder`/`home` 已正确） |
| 25 个 `mobile/components/*` 中 24 个老板端可达、0 个专属 | 独立读 import 分布 + 架构师 P-05 交叉 | ✅ 属实 |

**据此，D-1 / D-2 / D-3 三项已由主智能体追加进本轮实现范围**（详见 `demo-boss-module-plan.md` §10.3），不再停留在「待决策」。

### 12.3 表达口径澄清（保留项 / 已证伪项）

- **保留**：`AP-06`（同一语义两套色）在复审后**降级为 D-1 单一缺陷**——问题只出在 `home.vue:411-415` 一处，`rank.vue` 的 `--rank-3-bg` 本身正确，不构成「系统性双色板」。
- **保留**：`--text-3` 对 `--surface-page` 的 4.505:1「余量极小，不得再降档」——该约束具有实际防护价值，实现阶段必须遵守。
- 附录 A 全部数值采信（已抽查 2 项一致），后续如需新增对比度结论，必须沿用附录 A 的 `node` 实算命令，禁止手算估值。
