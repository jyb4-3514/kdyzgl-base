# 移动端「老板端」模块化拆分方案（结构与依赖边界）

> 文档类型：架构方案（先方案后实现，本轮不落代码）
> 范围：`hrm-dev/hrm-demo/src/mobile/` 内的**老板端域**（`src/mobile/views/boss/`，21 页）
> 编写方式：全部结论基于**实际读码**，逐条给 `文件:行号`；未取证项显式标注「存疑」
> 配套文档：`demo-boss-ui-spec.md`（UI/UX 设计师 · 视觉/交互/组件规范）。本文负责**结构与依赖**，两者互补。
> 硬约束来源：项目规则 §12「演示与隔离工程硬约束」

---

## 0. 结论先行

1. 老板端当前**没有独立的物理边界**：21 个页面平铺在 `src/mobile/views/boss/`，166 条 import 里有 **107 条是跨层的深层相对路径直引**（`../../components|x`），模块边界全靠目录名口头表达。
2. 老板端**当前 0 个专属组件、0 个专属 composable、0 个专属常量、0 个专属 util**——所有依赖都落在移动端共享内核里。真正"跨域"的只有一处：路由把 `/boss/kpi/:employeeId` 指向了 `views/staff/kpi.vue`（`router/index.js:109-114`）。
3. 因此本次拆分的实质是三件事：① 页面迁入模块目录；② 把路由从单文件拆成「模块子表 + 聚合点」；③ 用 ESLint `no-restricted-imports` 把边界变成 lint error，并给 UI 规范提出的 6 个新组件一个**有主的落点**。
4. 目标形态（单工程内按域拆模块，可整体 `rm -rf hrm-demo/` 回滚）：

```
src/mobile/
├── modules/
│   └── boss/
│       ├── router.js        # 老板端路由子表（导出数组，由 ../router/index.js 聚合）
│       ├── views/           # 21 个页面（views/boss/*.vue 整体迁入）
│       └── components/      # 老板端专属组件（UI 规范 N-01~N-06 的落地位置）
└── （内核原地不动）components/ composables/ constants/ layout/ stores/ styles/ utils/ api/
```

5. **与 UI 规范的一处冲突需裁决**：UI 规范 §1.2 建议新组件放 `src/mobile/components/boss/`，本方案落 `src/mobile/modules/boss/components/`。裁决建议取后者，理由见 §3.4。

---

## 1. 现状审计与问题清单

### P-01 页面平铺、无物理边界

`src/mobile/views/boss/` 下 21 个 `.vue`（实测行数）：

| 页面 | 行数 | 页面 | 行数 | 页面 | 行数 |
|----|----|----|----|----|----|
| alerts.vue | 421 | flowDetail.vue | 481 | payroll.vue | 280 |
| attendance.vue | 142 | home.vue | 402 | payrollDetail.vue | 230 |
| attendanceRecords.vue | 262 | hr.vue | 150 | rank.vue | 200 |
| attendanceRule.vue | 536 | hrDetail.vue | 282 | schedule.vue | 941 |
| flow.vue | 183 | kpi.vue | 555 | trend.vue | 107 |
| leaveApproval.vue | 13 | leaveSettings.vue | 161 | workorder.vue | 223 |
| makeupApproval.vue | 296 | me.vue | 7 | notificationPublish.vue | 337 |

共 21 个文件。目录前缀 `boss/` 是唯一的结构信号，无模块清单、无入口、无归属声明。

### P-02 深层相对路径直引（量化）

对 21 个文件的 `^import` 逐行统计（命令见下表脚注）：

| 目标层 | 写法 | 条数 | 例（文件:行号） |
|----|----|----|----|
| 移动端内核组件 | `../../components/*` | **74** | `alerts.vue:4-7`、`home.vue:4-7` |
| 移动端 API | `../../api/index.js` | **16** | `alerts.vue:8`、`workorder.vue:11` |
| 移动端 utils | `../../utils/*` | **14** | `home.vue:21`、`schedule.vue:9` |
| 移动端 stores | `../../stores/todo.js` | **1** | `home.vue:20` |
| 移动端 constants | `../../constants/*` | **2** | `home.vue:19`、`makeupApproval.vue:9` |
| 三端共享层 | `@/shared/*` | 12 | 已走别名，合规 |
| import 行总数 | — | 166 | — |

> 统计命令（PowerShell，可复现）：
> `Get-ChildItem src\mobile\views\boss -Filter *.vue | Select-String "^import" | ...`
> 结果：`文件数=21 / import 行总数=166 / ../../components=74 / ../../api=16 / ../../utils=14 / ../../stores=1 / ../../constants=2 / @shared=12`

**107/166 = 64% 的 import 是跨层深层相对路径**。这类路径的层级深度与物理位置强耦合——一旦目录下移一层，全部要改（本次迁移正是如此，见 §4/§6 的风险）。

### P-03 跨域直引：仅路由层一处

逐条核实 `src/mobile/router/index.js` 中指向非 `views/boss/` 的 `/boss/*` 路由：

| 路由 | 行号 | 组件指向 | 判定 |
|----|----|----|----|
| `/boss/message` | `router/index.js:44-49` | `../views/message/MessagePage.vue` | **中立共享页**（`/staff/message` 亦复用，`router/index.js:174-179`）。合法，见 P-07 |
| `/boss/kpi/:employeeId` | `router/index.js:109-114` | `../views/staff/kpi.vue` | **跨域直引**：老板端路由挂载了员工端域页面。全仓唯一一处 |

反向核实：`/staff/kpi`（`router/index.js:313-317`）指向同一文件 `views/staff/kpi.vue`。该页内部以 `auth.isAdmin` 分流标题与空态文案（`staff/kpi.vue:27,59,66-68`），即"同一业务对象两端共页"的既有约定（`staff/kpi.vue:16-22` 注释）。

**重要事实**：21 个 `views/boss/*.vue` 内部**没有任何一条** import 指向 `views/staff/**` 或 `src/pc/**`（grep 全仓仅命中 router 与 router 单测）。即页面之间无跨域耦合，耦合全部发生在路由层。

### P-04 重复实现盘查（逐域比对）

| 域 | 老板端 | 员工端 | 判定 |
|----|----|----|----|
| 我的 | `boss/me.vue`（7 行） | `staff/me.vue`（7 行） | **逐行等价重复**（除注释）：两者均只渲染 `MeSection`（`boss/me.vue:2,8` / `staff/me.vue:2,8`）。可合并为中立共享页 |
| 请假审批 | `boss/leaveApproval.vue`（13 行） | `staff/leaveReview.vue`（16 行） | 同一组件 `LeaveApprovalList` 的两个 props 变体（`title/default-status/actor` 不同，`boss:14` / `staff:17`）。**合法端差异**，不合并 |
| 工资单详情 | `boss/payrollDetail.vue`（230 行） | `staff/payrollDetail.vue`（221 行） | 共享同一套 UI 骨架（`PageNav+PageState+PayrollStatusSteps+MyPayrollCard+ActionBar`）与同一"理由弹层"模式（2–200 字校验、`role="alert"`），但业务动作与端点完全不同（`approvePayroll/publishPayrolls` vs `confirmPayroll/objectPayroll`）。**业务不合并；「理由录入弹层」属可抽的 UI 模式**（与 UI 规范 N-06 同族） |
| KPI | `boss/kpi.vue`（555 行） | `staff/kpi.vue`（114 行） | **非重复**：前者是考核列表 + 生成本期考核，后者是得分明细（且被老板端路由复用，见 P-03） |
| 考勤记录 | `boss/attendanceRecords.vue`（262 行） | `staff/attendanceRecords.vue`（224 行） | 端差异：老板端带 `StationPicker`（`boss:7`）、员工端用 `monthShiftMap`（`staff:8`）。不合并 |
| 考勤概览/打卡 | `boss/attendance.vue`（142 行） | `staff/attendance.vue`（876 行） | 端差异：`getAttendanceSummary` vs `checkIn`。不合并 |
| 工单 | `boss/workorder.vue`（223 行） | `staff/workorder.vue`（238 行） | 端差异：老板全域筛选 vs 本站。不合并 |
| 排班 | `boss/schedule.vue`（941 行） | `staff/schedule.vue`（170 行） | 端差异：排班管理 vs 看自己。不合并 |
| 入离职 | `boss/flow.vue`（183 行） | `staff/flow.vue`（96 行） | 端差异：审批 vs 只读降级（`staff/flow.vue:2` 头部注释已说明契约未开放） |
| 工资单列表 | `boss/payroll.vue`（280 行） | `staff/payroll.vue`（109 行） | 端差异：`getPayrolls+publishPayrolls` vs `getMyPayrolls` |
| 人事档案 | `boss/hr.vue`(150)/`hrDetail.vue`(282) | `staff/profile.vue`（147 行） | 端差异 |
| 跨 **PC** 端 | `mobile/components/{StatusTag,PayrollStatusSteps,KpiGauge,WorkOrderCopyButton}.vue` | `pc/components/` 同名文件 | **合法端差异**：Vant 与 Element Plus 物理隔离（`vite.config.js:5-8`），**禁止跨 UI 库合并** |

**结论**：可抽的唯一真重复是 `boss/me.vue` ⇄ `staff/me.vue`；其余均为合法端差异或不同职责，强行合并会破坏两端行为（违反精简原则中"同一逻辑不得重复实现"的边界——端差异不算重复）。

### P-05 老板端专属资产盘查：现状为 0

| 类型 | 结论 | 证据 |
|----|----|----|
| 组件 | **0 个专属** | 25 个 `mobile/components/*` 中，`AttendanceStatusBar` 仅 `/staff/home` 消费（`staff/home.vue:4`）；其余 24 个均双端可达（与 UI 规范 §1.1 结论一致） |
| composable | 0 个专属 | `useReselect` 由 `TabbarLayout.vue:9`、`MessagePage.vue:9` 消费；`useCheckIn` 由 `AttendanceStatusBar.vue:5` 消费 |
| util | 0 个专属 | `utils/workorder.js` 仅 `staff/workorderDetail.vue:14` 消费（员工端专属） |
| constant | 0 个专属 | `quickEntries`（`boss/home.vue:19` + `staff/home.vue:11`）、`makeup`（`boss/makeupApproval.vue:9` + `staff/makeupList.vue:9`）、`accounts`（`login/index.vue:5`+`MeSection.vue:6`+`staff/home.vue:10`）、`tabs`（`TabbarLayout.vue:5`）、`todoGroups`（`stores/todo.js:3`）——全部共享 |

**唯一"仅老板端消费"的共享目录资产**：`LineChart`，仅 `boss/home.vue:5`、`boss/trend.vue:3` 引用，员工端 0 引用。是否随迁见 §3.5 裁决（建议不迁）。

### P-06 共享内核里硬编码了老板端路由字符串（本次禁改）

| 位置 | 内容 |
|----|----|
| `mobile/layout/TabbarLayout.vue:33` | `auth.isAdmin && route.path === '/boss/message'` |
| `mobile/layout/TabbarLayout.vue:79` | `router.push('/boss/notification/publish')` |

共享内核知道 `/boss/*` 的存在。这是历史债（`TODO(扩展)` 级），但**本次必须冻结 `/boss/*` URL**，否则会同时打断共享内核与 e2e（见 §6）。清理它需要改共享内核，超出本轮范围。

### P-07 已有"中立共享页"先例（用于裁决 P-03）

`views/message/MessagePage.vue` 同时被 `/boss/message` 与 `/staff/message` 复用，位置在**域目录之外**（`views/message/`）。即"跨端同对象共页"在本工程已有落地形态：**页面不放进任何一端域目录**。这是裁决 `/boss/kpi/:employeeId` 的参照（见 §8 开放问题 Q2）。

---

## 2. 模块边界定义

### 2.1 三层依赖模型

```
┌──────────────────────────────────────────────────────────┐
│ 老板端模块  src/mobile/modules/boss/                      │
│   自有：views/（21 页）· components/（Boss* 专属组件）      │
│         router.js（老板端路由子表）                        │
│         （composables/ constants/ api/ 本轮不建，§3.3）    │
└───────────────┬──────────────────────────────────────────┘
                │ 只允许向下依赖，禁止横向依赖其他域
┌───────────────▼──────────────────────────────────────────┐
│ 移动端共享内核（原地不动）                                  │
│   layout/TabbarLayout  components/*(25)  composables/*(2) │
│   constants/*(5)  stores/*(auth/notify/todo)              │
│   utils/*(http/authStorage/bridge/format/attendance/leave) │
│   api/index.js（薄适配层）  styles/*(mobile/tokens)         │
└───────────────┬──────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────┐
│ 三端共享层  src/shared/                                    │
│   constants/(dict/role/storageKey/errorCode)              │
│   domain/(纯函数)  styles/tokens.base.scss  clientLog.js  │
└──────────────────────────────────────────────────────────┘
```

### 2.2 包含 / 依赖 / 禁止

**老板端模块自有（本次迁入并归属模块）**
- `modules/boss/views/`：21 个页面
- `modules/boss/components/`：老板端专属组件，命名前缀 `Boss*`（沿用 UI 规范 §1.2 要求），本轮承载 UI 规范 N-01~N-06
- `modules/boss/router.js`：老板端路由子表（导出数组）

**依赖移动端共享内核（保持原地，只读消费）**
- `layout/TabbarLayout.vue`（Tab 页外壳）
- `components/*`（PageNav / PageState / StatusTag / StatCard / ActionBar / StationPicker / MonthPicker / SlaTag / HomeQuickGrid / LeaveApprovalList / MeSection / MyPayrollCard / PayrollStatusSteps / LineChart / WorkOrderCopyButton …）
- `constants/{tabs,quickEntries,makeup,accounts,todoGroups}.js`
- `stores/{auth,notify,todo}.js`
- `utils/{http,authStorage,bridge,format,attendance,leave}.js`
- `api/index.js`
- `styles/{mobile,tokens}.scss`

**依赖三端共享层 `src/shared/`**
- `constants/{dict,role,errorCode,storageKey}.js`、`domain/*`、`styles/tokens.base.scss`、`clientLog.js`

**明确禁止**
1. `modules/boss/**` 不得 import `views/staff/**` 或未来的 `modules/staff/**`（跨域依赖）。
2. `modules/boss/**` 不得 import `src/pc/**` 或 `@admin/**`（一期只读资产）。
3. `modules/boss/**` 不得 import `shared/mock/**`（沿用 `eslint.config.js:112-128` 既有禁令）。
4. 反向：`views/staff`、`modules/staff`、`src/pc` 不得 import `modules/boss/**`（叶子域单向）。
5. 跨域共享页不放进任何域目录（沿用 `views/message/MessagePage.vue` 先例）。

以上 5 条由 §5 的 lint 规则机器强制。

---

## 3. 目标目录结构

### 3.1 落地形态

```
src/mobile/
├── modules/
│   └── boss/
│       ├── router.js               # 新增：export const bossRoutes = [...]（23 条：/boss 重定向 + 22 个页面路由）
│       ├── views/                  # 21 个页面由 views/boss/ 整体迁入（文件名不变）
│       │   ├── home.vue  me.vue  attendance.vue  trend.vue  rank.vue  alerts.vue
│       │   ├── workorder.vue  makeupApproval.vue  notificationPublish.vue
│       │   ├── kpi.vue  hr.vue  hrDetail.vue  payroll.vue  payrollDetail.vue
│       │   ├── flow.vue  flowDetail.vue  leaveApproval.vue  leaveSettings.vue
│       │   ├── attendanceRule.vue  schedule.vue  attendanceRecords.vue
│       │   └── （共 21 个）
│       └── components/             # 新增：老板端专属组件（Boss*），本轮承接 UI 规范 N-01~N-06
├── router/
│   ├── index.js                    # 改为聚合点：登录/员工端/404 + 展开 bossRoutes + 唯一跨域复用路由
│   └── index.spec.js               # 随 mock 路径同步（§4 第 24 行）
└── （内核全部原地不动）
```

### 3.2 路由聚合方式

`src/mobile/modules/boss/router.js`：
```js
import { ROLE } from '@/shared/constants/role.js'

/**
 * 老板端路由子表（按域拆分后由 ../router/index.js 单点聚合）
 * 为什么用数组导出而非独立 createRouter：整端共用一个 router 实例，
 * 子表只提供定义，实例与守卫（登录态 / 角色白名单）仍在聚合点，避免出现多实例与守卫漏挂。
 */
export const bossRoutes = [
  { path: '/boss', redirect: '/boss/home' },
  { path: '/boss/home', name: 'bossHome', component: () => import('./views/home.vue'), meta: { tabbar: 'boss', roles: [ROLE.ADMIN], title: '经营总览' } },
  // …其余 21 条，逐条照搬 views/boss 现有 meta（tabbar / roles / title 三者原样保留，禁止改动）
]
```

`src/mobile/router/index.js`（聚合点）：
```js
import { bossRoutes } from '../modules/boss/router.js'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', /* … 不变 … */ },

  ...bossRoutes,                        // 老板端域（含 /boss 重定向与 22 页）

  /* 跨域复用路由：唯一一处，故意留在聚合点（见 §8 Q2） */
  // TODO(扩展): 该页为「同一业务对象两端共页」，若后续要彻底去耦，应提升为中立共享页（参照 views/message/MessagePage.vue 先例）
  { path: '/boss/kpi/:employeeId', name: 'bossKpiDetail', component: () => import('../views/staff/kpi.vue'), meta: { roles: [ROLE.ADMIN], title: '考核明细' } },

  /* …员工端、404 不变… */
]
```

**为什么把跨域复用路由留在聚合点而不是塞进 `boss/router.js`**：让全仓唯一一处"跨域复用"集中在聚合点可见，避免在模块内部埋 `eslint-disable` 例外；这也让 §5 的模块内禁区规则可以保持零例外。

### 3.3 为什么本轮不建 `composables/` `constants/` `api/`

| 目录 | 现状 | 裁决 |
|----|----|----|
| `composables/` | 老板端 0 个专属（P-05） | **不建**。空目录是噪音；首个专属 composable 出现时再建，登记 `TODO(扩展)` |
| `constants/` | 老板端 0 个专属常量（P-05） | **不建**，理由同上 |
| `api/` | `src/mobile/api/index.js` 是 **212 行扁平薄适配层**，全端共用，无端特异性分支（`api/index.js:11-212` 全部是 `http.xxx(url)` 一行式） | **不拆**。拆它必须动共享文件、且员工端未同期改造会造成"半迁移"状态；按域拆 api 的前提是两端模块化同时推进，登记 `TODO(扩展)` |

> 反过度设计：任务建议的骨架含 `api/`，但本工程的实际形态不支持——强行建一个只 re-export 的 `modules/boss/api/index.js` 会增加一层无收益转发，违反「精简优先」。

### 3.4 与 UI 规范的冲突裁决：新组件落点

| 方案 | 出处 | 评价 |
|----|----|----|
| **A**（本方案）`src/mobile/modules/boss/components/` | 本文 | **采纳** |
| B `src/mobile/components/boss/` | UI 规范 §1.2 / Q1 | 否决 |

**采纳 A 的理由**（三条，逐条可验证）：
1. **所有权清晰**：`components/boss/` 位于内核目录内，归属含糊——它既不是内核（员工端不可用），又不在模块内；`modules/boss/components/` 由 §5 的 `files: ['src/mobile/modules/boss/**']` 规则自然覆盖。
2. **lint 覆盖零额外成本**：现有 `VIEW_FILES` 含 `'src/**/components/**'`（`eslint.config.js:25`），`src/mobile/modules/boss/components/**` 已被 `**` 命中，无需改规则。
3. **与员工端同构**：员工端未来按同一约定得 `modules/staff/components/`；若走 B，则会变成"内核目录下按端开子目录"，两端拆完后 `components/` 下混杂内核与各端专属，边界再次模糊。

**同时保留 UI 规范的实质要求**：新组件命名前缀 `Boss*`（UI 规范 §1.2 第 118 行）；老板端专属样式**禁止**写进 `mobile.scss` 全局工具类。

**兼容处理**：UI 规范 §1.2 与附录 C 提到的 `components/boss/` 需在实现阶段同步改写为 `modules/boss/components/`（列入 §9 任务 A9，由主智能体回写）。

### 3.5 `LineChart` 是否随迁：裁决为**不迁**

- 事实：仅 `boss/home.vue:5`、`boss/trend.vue:3` 引用，员工端 0 引用（P-05）。
- 裁决：**保留在内核 `mobile/components/`**。理由：它是通用纯展示图表（无老板端业务语义），UI 规范 §8.1 已把它列为共享内核并规划接口扩展（§8.8、附录 C 的"LineChart 接口扩展"）。若迁入 `modules/boss/components/`，未来员工端要用就得反向依赖老板端模块，违反 §2.2 第 4 条依赖方向。
- 登记：`TODO(扩展): 若确认员工端永不使用 LineChart，再评估下沉到 module`（不属本次）。

### 3.6 与其他模块（staff / pc）的目录约定（供其他负责人对齐）

| 约定 | 内容 |
|----|----|
| 模块根 | `src/mobile/modules/<domain>/`（本条针对移动端域）；PC 端若同理为 `src/pc/modules/<domain>/`（本轮不动 pc） |
| 模块内固定三件套 | `router.js`（导出 `<domain>Routes` 数组）+ `views/` + `components/`；`composables/`、`constants/`、`api/` 按需增建，不预置空目录 |
| 内核 | `src/mobile/{components,composables,constants,layout,stores,styles,utils,api}` 与 `src/shared/**` 是唯一允许被多方依赖的层，**只增不改语义**；跨域共享页放内核的 `views/`（如 `views/message/`） |
| 路由 | 每模块只导出定义，`src/mobile/router/index.js` 单点聚合；**跨域复用路由只允许写在聚合点** |
| 命名 | 模块自有组件统一 `<Domain>*` 前缀（老板端 = `Boss*`） |
| 依赖方向 | `modules/* → 内核 → shared`；禁止 `modules/A ⇄ modules/B`；禁止内核 `→ modules/*`（当前 `TabbarLayout.vue:33,79` 的 `/boss/*` 字符串是历史债，登记 `TODO(扩展)`） |
| import 写法 | 跨层一律走既有 `@` 别名（`@/mobile/**`、`@/shared/**`），模块内互引用用相对路径（`./`、`../components/`）。**不新增 `@boss`/`@mobile` 别名**——既有 `@`（`vite.config.js:65`、`vitest.config.mjs:21`）已够用，新增别名要多改两处配置文件 |

### 3.7 备选方案取舍

| 方案 | 内容 | 裁决 |
|----|----|----|
| A（采纳） | 页面迁入 `modules/boss/`，路由拆子表，新组件落模块内 | 见 §3.4 |
| B | 保留 `views/boss/`，只加 lint 边界 + 新组件放 `components/boss/` | 否决：域边界仍靠文件前缀表达，员工端无法同构，且 `components/boss/` 所有权不清 |
| C | 拆成多个独立 `package.json` 子工程（Monorepo） | 否决：违反用户决策 2（需保持可整体 `rm -rf hrm-demo/` 回滚）；且 `resolve.dedupe` 与 `@admin` 别名的历史白屏坑（`vite.config.js:52-62`）在 workspace 下会重新引入，成本远超收益 |

---

## 4. 迁移映射表

> 处置列：**移动** = 文件位置变更；**保留原地** = 不动；**改** = 内容修改。
> 所有「移动」项都是 `git mv` 语义（保留历史），禁止删除重建。

### 4.1 页面（21 条）

| # | 现有路径 | 目标路径 | 处置 |
|----|----|----|----|
| 1 | `src/mobile/views/boss/alerts.vue` | `src/mobile/modules/boss/views/alerts.vue` | 移动 |
| 2 | `src/mobile/views/boss/attendance.vue` | `src/mobile/modules/boss/views/attendance.vue` | 移动 |
| 3 | `src/mobile/views/boss/attendanceRecords.vue` | `src/mobile/modules/boss/views/attendanceRecords.vue` | 移动 |
| 4 | `src/mobile/views/boss/attendanceRule.vue` | `src/mobile/modules/boss/views/attendanceRule.vue` | 移动 |
| 5 | `src/mobile/views/boss/flow.vue` | `src/mobile/modules/boss/views/flow.vue` | 移动 |
| 6 | `src/mobile/views/boss/flowDetail.vue` | `src/mobile/modules/boss/views/flowDetail.vue` | 移动 |
| 7 | `src/mobile/views/boss/home.vue` | `src/mobile/modules/boss/views/home.vue` | 移动 |
| 8 | `src/mobile/views/boss/hr.vue` | `src/mobile/modules/boss/views/hr.vue` | 移动 |
| 9 | `src/mobile/views/boss/hrDetail.vue` | `src/mobile/modules/boss/views/hrDetail.vue` | 移动 |
| 10 | `src/mobile/views/boss/kpi.vue` | `src/mobile/modules/boss/views/kpi.vue` | 移动 |
| 11 | `src/mobile/views/boss/leaveApproval.vue` | `src/mobile/modules/boss/views/leaveApproval.vue` | 移动 |
| 12 | `src/mobile/views/boss/leaveSettings.vue` | `src/mobile/modules/boss/views/leaveSettings.vue` | 移动 |
| 13 | `src/mobile/views/boss/makeupApproval.vue` | `src/mobile/modules/boss/views/makeupApproval.vue` | 移动 |
| 14 | `src/mobile/views/boss/me.vue` | `src/mobile/modules/boss/views/me.vue` | 移动 |
| 15 | `src/mobile/views/boss/notificationPublish.vue` | `src/mobile/modules/boss/views/notificationPublish.vue` | 移动 |
| 16 | `src/mobile/views/boss/payroll.vue` | `src/mobile/modules/boss/views/payroll.vue` | 移动 |
| 17 | `src/mobile/views/boss/payrollDetail.vue` | `src/mobile/modules/boss/views/payrollDetail.vue` | 移动 |
| 18 | `src/mobile/views/boss/rank.vue` | `src/mobile/modules/boss/views/rank.vue` | 移动 |
| 19 | `src/mobile/views/boss/schedule.vue` | `src/mobile/modules/boss/views/schedule.vue` | 移动 |
| 20 | `src/mobile/views/boss/trend.vue` | `src/mobile/modules/boss/views/trend.vue` | 移动 |
| 21 | `src/mobile/views/boss/workorder.vue` | `src/mobile/modules/boss/views/workorder.vue` | 移动 |

### 4.2 路由与配置（9 条）

| # | 现有位置 | 目标位置 / 改动内容 | 处置 |
|----|----|----|----|
| 22 | `src/mobile/router/index.js:36`（`/boss` 重定向）+ `:37-99`（11 条路由）+ `:101-163`（12 条路由）+ `:293-309`（3 条路由），共 23 条老板端路由 | 抽到新文件 `src/mobile/modules/boss/router.js`，导出 `bossRoutes` 数组 | 改 |
| 23 | `src/mobile/router/index.js` 顶部 import 区（`:1-9`） | 新增 `import { bossRoutes } from '../modules/boss/router.js'`；`routes` 数组内 `...bossRoutes` 展开 | 改 |
| 24 | `src/mobile/router/index.spec.js:48` `vi.mock('../views/boss/home.vue', …)` | 改为 `vi.mock('../modules/boss/views/home.vue', …)`（路由改了 import 路径，mock 路径必须同步，否则 mock 不命中 → 导航挂起；该风险 `index.spec.js:44` 注释已警示） | 改 |
| 25 | `src/mobile/router/index.spec.js:47` `vi.mock('../views/staff/home.vue', …)` | **保留**（员工端页面未移动） | 保留原地 |
| 26 | `hrm-dev/hrm-demo/eslint.config.js:109-159`（规则 1-3）+ `eslint.config.js:22-28`（`VIEW_FILES`） | 在规则 3 之后新增规则 4-6（§5.3 片段）并增补 `VIEW_FILES` | 改 |
| 27 | `src/mobile/views/boss/`（迁移后空目录） | 删除空目录 | 改 |
| 28 | `modules/boss/` 内 107 条跨层 import（P-02 统计） | 由 `../../X` 改写为 `@/mobile/X`（跨层走 `@` 别名）；模块内互引用与 `modules/boss/components` 引用用相对路径 | 改 |
| 29 | `hrm-dev/docs/demo-mobile-nav-redesign.md`、`SESSION-STATE.md`、`demo-boss-ui-spec.md`（§1.2 / 附录 C 的 `components/boss/` 路径） | 追加/改写为 `modules/boss/` 路径注记 | 改（主智能体执行） |
| 30 | `vite.config.js` | **不动**（无 manualChunks，`vite.config.js:104-117`；别名不新增） | 保留原地 |

> `router/index.js` 需删除/改动的行号清单（23 条路由 = 24 个条目，减去留在聚合点的 `/boss/kpi/:employeeId`）：
> `:36`、`:37-42`、`:44-49`、`:50-55`、`:57-62`、`:63-68`、`:69-74`、`:75-80`、`:81-86`、`:87-92`、`:93-99`、`:102-107`、`:115-120`、`:121-126`、`:127-132`、`:133-138`、`:139-144`、`:145-150`、`:151-157`、`:158-163`、`:293-297`、`:298-303`、`:304-309`
> **保留在聚合点**：`:109-114`（`/boss/kpi/:employeeId` → `views/staff/kpi.vue`）。

### 4.3 保留原地（共享内核与共享页，逐条列出）

| 资产 | 依据 |
|----|----|
| `mobile/layout/TabbarLayout.vue` | P-06，双端共用外壳 |
| `mobile/components/*`（25 个，含 `LineChart`，见 §3.5） | P-05，双端可达 |
| `mobile/composables/{useCheckIn,useReselect}.js` | P-05，双端消费 |
| `mobile/constants/{accounts,makeup,quickEntries,tabs,todoGroups}.js` | P-05；**`quickEntries.js` 必须原地**（`e2e/03-mobile-nav.spec.js:2` 以源码路径直连它） |
| `mobile/stores/{auth,notify,todo}.js` | 内核 |
| `mobile/utils/{http,authStorage,bridge,format,attendance,leave,workorder}.js` | 内核 |
| `mobile/api/index.js` | §3.3，薄适配层 |
| `mobile/styles/{mobile.scss,tokens.scss}` | 内核 |
| `src/mobile/views/{login,error,message}/**`、`views/staff/**` | 非老板端域（本轮范围外） |
| `src/shared/**` | 三端共享层，禁止改导出 |
| `src/pc/**`、`hrm-admin/**`、`hrm-server/**` | §12 硬约束，零改动 |

---

## 5. 依赖方向机器强制（ESLint 9 flat config）

### 5.1 现状确认

`hrm-demo/eslint.config.js` 已有三块 `no-restricted-imports`：
- 规则 1（`:112-128`）：`src/pc/**`+`src/mobile/**` 禁 `shared/mock/**`，白名单 `pc/main.js`、`mobile/main.js`、`mobile/utils/workorder.js`。
- 规则 2（`:130-142`）：`shared/domain/**`、`shared/composables/**` 禁 mock。
- 规则 3（`:143-159`）：`src/portal/**`、`src/demo/**` 禁 `mobile/**`、`pc/**`。

`no-restricted-imports` 的 `patterns[].group` 是对**原始 import 字符串**做 minimatch。因此 `../../../views/staff/kpi.vue` 能命中 `**/views/staff/**`（`**/` 可匹配 `..` 段）；这决定了规则可以直接按路径片段写，不必依赖解析器。

### 5.2 关键坑（已在官方文档核实）

**flat config 中同名 rule「后者整体覆盖前者」，不做选项合并。** 新增的 `no-restricted-imports` 块会把它覆盖文件**原本生效的 patterns 全部替换掉**——若新块不复述 mock 禁令，`shared/mock/**` 的封禁会被静默抹掉。

> 依据：ESLint《Configuration Files · Cascading Configuration Objects》——"the configuration objects are merged with later objects overriding previous objects when there is a conflict"（https://eslint.org/docs/head/use/configure/configuration-files ）。
> 社区配置包的实测提醒同样印证：定义自己的 `no-restricted-imports` 会让组织级封禁"静默消失"（npm `@leaflink/eslint-config` 说明）。
> 因此：**新块必须复述其覆盖文件原本已生效的 patterns，且必须置于规则 1-3 之后**。

### 5.3 可直接落地的配置片段（本轮不实际改 `eslint.config.js`）

追加在 `hrm-demo/eslint.config.js` 的规则 3 之后、a11y 块之前：

```js
  // ==========================================================================
  // 规则 4-6：按域拆模块后的老板端边界（本次新增）
  // --------------------------------------------------------------------------
  // 必须放在规则 1-3 之后：flat config 中同一 rule 名"后者整体覆盖前者"，
  // 故每个新块都要把它覆盖文件原本生效的 patterns 复述一遍（尤其 mock 禁区），
  // 否则会把规则 1 的「展示层禁止直连假后端」静默抹掉。
  // 依据：ESLint《Configuration Files · Cascading Configuration Objects》。
  // ==========================================================================

  // 规则 4：老板端模块内部 —— 禁止跨域 / 禁止触达一期只读资产 / 禁止直连 mock
  {
    files: ['src/mobile/modules/boss/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '老板端模块禁止直连假后端；Mock 装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/views/staff/**', '**/modules/staff/**'],
              message: '老板端模块禁止直引其他业务域；跨端复用请走内核或中立共享页（路由层例外只允许写在聚合点 router/index.js）'
            },
            {
              group: ['@admin/**', '@/pc/**', '**/src/pc/**'],
              message: '老板端模块禁止依赖 PC 端与一期只读资产 @admin'
            }
          ]
        }
      ]
    }
  },

  // 规则 5：员工端域 —— 禁止反向依赖老板端模块（复述 mock 禁区）
  {
    files: ['src/mobile/views/staff/**', 'src/mobile/modules/staff/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；Mock 装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/modules/boss/**'],
              message: '老板端模块是叶子域，禁止被员工端反向依赖'
            }
          ]
        }
      ]
    }
  },

  // 规则 6：PC 端 —— 禁止反向依赖移动端老板模块（复述 mock 禁区，防御性）
  {
    files: ['src/pc/**'],
    ignores: ['src/pc/main.js'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；Mock 装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/modules/boss/**'],
              message: '老板端模块属移动端叶子域，PC 端禁止反向依赖'
            }
          ]
        }
      ]
    }
  },
```

**规则 3 无需改动**：`src/portal/**`、`src/demo/**` 已封 `**/mobile/**`，而 `src/mobile/modules/boss/**` 被 `**/mobile/**` 命中，天然覆盖。

**未覆盖项（有意）**：规则不拦 `modules/boss/**` 引 `views/message/MessagePage.vue`（中立共享页，合法）、不拦 `@/mobile/**` 与 `@/shared/**`（内核与共享层，合法）。

### 5.4 负向验证（可静态执行，不产文件）

```bash
# 在 hrm-demo/ 下执行；--stdin-filename 让 ESLint 按老板端模块的 files 规则匹配
echo "import '@/shared/mock/install.js'" | npx eslint --stdin --stdin-filename src/mobile/modules/boss/views/__probe.js
# 期望：1 个 no-restricted-imports error（禁止直连假后端）

echo "import '@/mobile/views/staff/kpi.vue'" | npx eslint --stdin --stdin-filename src/mobile/modules/boss/views/__probe.js
# 期望：1 个 no-restricted-imports error（禁止跨域）

echo "import '@/mobile/modules/boss/views/home.vue'" | npx eslint --stdin --stdin-filename src/mobile/views/staff/__probe.js
# 期望：1 个 no-restricted-imports error（员工端禁止反向依赖）
```

> `--stdin` + `--stdin-filename` 为 ESLint 9 内置能力；若本机 ESLint 版本行为不符，改用临时文件 + 立即删除（存疑点：仅在未实测该 CLI 组合时需回退）。

---

## 6. 增量迁移策略

### 6.1 门禁资产影响评估（逐项实测）

| 资产 | 是否受影响 | 依据 |
|----|----|----|
| `e2e/` 8 个 spec | **不受影响**（前提：URL 冻结 + `constants/quickEntries.js` 不移动） | 断言的是**路由 URL**：`01-load.spec.js:126`（`/#\/boss\/home/`）、`03-mobile-nav.spec.js:128,134`（`/#\/boss\/home/`、`/#\/boss\/me/`）、`05-render.spec.js:114`（`/mobile.html#/boss/trend`）；选择器为 DOM class，与文件路径无关 |
| `e2e/03-mobile-nav.spec.js:2` | **高风险直连** | 以源码相对路径 `import { BOSS_QUICK_ENTRIES, STAFF_QUICK_ENTRIES } from '../src/mobile/constants/quickEntries.js'`。→ `quickEntries.js` **必须原地**（§4.3） |
| `scripts/verify-mobile-t13-t16.mjs`（48 项） | **不受影响** | 仅 import `src/shared/mock/{engine,db,parcelStore,overlay}.js`（`:8-11`），走 Mock adapter 直接调接口，不涉及页面文件 |
| `scripts/verify-mock.mjs`（878 项） | **不受影响** | 仅 import `src/shared/constants/{dict,errorCode}.js`（`:16-17`），共享层不动 |
| `src/mobile/router/index.spec.js` | **受影响** | `:48` 的 `vi.mock('../views/boss/home.vue')` 必须随路由 import 路径同步改为 `'../modules/boss/views/home.vue'`；`:47`（staff/home）不变 |
| `vite.config.js` chunk 命名 | **不受影响（但产物名会变）** | 无 `manualChunks`（`vite.config.js:104-117` 只设 `input`）；Rollup 默认按模块文件名派生 chunk 名，迁移后路径前缀与内容 hash 变化属正常。全仓**无任何断言 chunk 名的脚本/用例**（对 `e2e/**`、`scripts/**` grep `chunk|dist/|assets/` 未命中文件名断言） |
| `lint` / `format:check` / `lint:style` | 受影响面可控 | 迁移会改 import 行；`modules/boss/**` 已落在既有 `VIEW_FILES`（`eslint.config.js:22-28` 的 `**/components/**` 与 `src/mobile/views/**` 需确认覆盖新路径，见下） |
| `eslint.config.js` 的 `VIEW_FILES` | **需核对** | 现含 `'src/mobile/views/**'`——`modules/boss/views/**` **不匹配**该条。`'src/**/components/**'` 可覆盖 module 内组件。故需在 `VIEW_FILES` 增补 `'src/mobile/modules/**'`，否则新页面的"展示层降为 warn"策略失效（不影响 error 门禁，但会改变告警口径） |

**必做的一处配置增补**：`eslint.config.js:22-28` 的 `VIEW_FILES` 数组增加 `'src/mobile/modules/**'`（理由：保持"展示层降 warn"的既有口径；不加也不会让 `lint` 失败，因为默认 js/vue 规则仍是 error，但会与既有策略不一致）。

### 6.2 分步计划（每步独立可验收，门禁全程不红）

| 步 | 内容 | 完成标志 |
|----|----|----|
| **M0** | 只加 §5.3 的 lint 规则 4-6 + `VIEW_FILES` 增补。此时 `modules/boss/**` 尚不存在 | `npm run lint` 0 error（证明规则不误伤现状）；`--stdin` 三条负向探针均按预期报错 |
| **M1** | 建 `modules/boss/` 骨架；**只迁 1 个样板页 `me.vue`**（7 行、仅依赖内核）+ 建 `modules/boss/router.js`（先只含 `/boss/me` 与 `/boss` 重定向）+ 改聚合点 + 改 `index.spec.js:48` | `lint`/`build`/`test`/`e2e` 全绿；URL `/boss/me` 不变 |
| **M2** | 迁「首页与看板组」：home、trend、rank、alerts、attendance | 同上 |
| **M3** | 迁「人力财务流程组」：hr、hrDetail、kpi、payroll、payrollDetail、flow、flowDetail | 同上 |
| **M4** | 迁「考勤排班组」：attendanceRule、attendanceRecords、schedule、makeupApproval | 同上 |
| **M5** | 迁「请假/通知/工单组」：leaveApproval、leaveSettings、notificationPublish、workorder | 同上 |
| **M6** | 删除空目录 `views/boss/`；全量门禁 + 文档回写（`demo-mobile-nav-redesign.md`、`SESSION-STATE.md`、UI 规范路径） | 全部门禁绿 + `views/boss` 不存在 |
| **M7**（并行线） | UI 规范 N-01~N-06 组件落 `modules/boss/components/`，并在页面内替换手写实现 | 视觉走查 + e2e 选择器不回归 |

**为什么按"页面组"而不是"逐个目录"分批**：路由与页面必须同改（路由 import 指向文件），按业务组分批可让每个 commit 有独立语义，且 `e2e` 可在每个批次末跑一次（`/boss/*` 相关 URL 断言全绿即证明 URL 冻结有效；相关断言位置：`01-load.spec.js:126`、`03-mobile-nav.spec.js:128,134`、`05-render.spec.js:114`）。

**回滚粒度**：任一批次出问题，`git revert` 该批次 commit 即可恢复；整个工程仍可 `rm -rf hrm-demo/` 整体回滚（用户决策 2 的底线）。

---

## 7. 验收门禁

### 7.1 可静态审查（本机可执行）

| # | 命令 | 期望 | 说明 |
|----|----|----|----|
| G1 | `npm run lint` | 0 error（warn 允许存在） | `eslint.config.js:13-14` 明确不开 `--max-warnings 0` |
| G2 | `npm run lint:style` | 0 error | 样式块迁移后 BEM 命名不变 |
| G3 | `npm run format:check` | 通过 | 迁移后需 prettier 通过 |
| G4 | `npm run build` | EXIT=0（演示态三入口） | MPA 三入口不变 |
| G5 | `npm run build:prod` | EXIT=0（仅 pc/mobile） | 验证 `modules/boss` 不引 mock（生产剥离线不破） |
| G6 | `npm run verify:mock` | 878/878 | 契约层，不受影响 |
| G7 | `npm run verify:mobile` | 48/48 | 数据链路，不受影响 |
| G8 | `npm run test` | 150/150（含 `router/index.spec.js`） | mock 路径必须已同步 |
| G9 | `npm run e2e` | 37/37 | 需 dev server + Playwright 浏览器 |
| G10 | 结构断言：`Test-Path src/mobile/views/boss` → `False`；`(Get-ChildItem src/mobile/modules/boss/views -Filter *.vue).Count` → `21` | 断言通过 | 迁移完整性 |
| G11 | §5.4 三条 `--stdin` 负向探针 | 各 1 个 error | 依赖边界真实生效 |
| G12 | URL 冻结核对：`grep -n "'/boss" src/mobile/modules/boss/router.js` 与 §4.2 保留的 `/boss/kpi/:employeeId` | 前缀全部为 `/boss`，无路径改写 | 保证 e2e 与共享内核不破 |
| G13 | `npm run verify:tokens` | 通过 | 若 M7 引入 Token 消费需复核 |

### 7.2 本机无法验证（须声明，不得当作已通过）

| 项 | 原因 | 处置 |
|----|----|----|
| 安卓壳加载行为 | 本机无 Android SDK / adb / gradle（项目规则 §4.1） | 不改壳；壳能力结论仅静态审查 |
| `npm run e2e` 实际执行 | Playwright 依赖已装（`package.json:43`），但浏览器二进制是否已 `npx playwright install` **存疑** | 执行前先跑 `npx playwright install --dry-run` 或直接运行并按缺浏览器报错处置 |
| 真机 / 宽屏视觉走查 | TRAE Chrome 扩展不可用（os error 10061）；内置浏览器固定 810×658 | 走查改用**系统默认浏览器**；375/430/640 真机档与平板档**不可覆盖**，只能静态审查；结论须标注"未在真机复测" |
| 后端相关 | 本机无 JDK/MySQL/Redis | 本次为纯前端结构迁移，不涉后端；但不得据此声称任何后端联通结论 |

---

## 8. 风险与不做清单

### 8.1 高风险点（3 个）

1. **路由 import 路径与 `router/index.spec.js` 的 `vi.mock` 不同步** → 单测导航挂起（假通过/超时）。证据：`index.spec.js:44` 注释明确"路径与 `router/index.js` 内的写法一致，否则 mock 不命中、真实视图会让导航挂起"。必须同一 commit 内改 `:48`。
2. **`e2e/03-mobile-nav.spec.js:2` 以源码路径直连 `constants/quickEntries.js`** → 任何"把常量按域拆进 modules"的动作都会直接打断该 spec（48 项之一）。故 `quickEntries.js` 冻结原地。
3. **`/boss/*` URL 与路由 meta 必须冻结** → 共享内核硬编码了 `/boss/message`（`TabbarLayout.vue:33`）与 `/boss/notification/publish`（`:79`），e2e 有 3 处 URL 断言（`01-load.spec.js:126`、`03-mobile-nav.spec.js:128,134`）。任何"顺手规范化路径"会同时打断两者。

### 8.2 中等风险

4. **107 条跨层 import 改写**（P-02）机械且量大：漏改会被 `build` 捕获（静态与动态 import 均在构建期解析），但可能表现为"构建通过、运行时 chunk 404"（动态 import 场景，参见 `07-network.spec.js:109-111` 注释）——必须靠 `e2e` 兜底，不能只看 `build` EXIT=0。
5. **`VIEW_FILES` 未覆盖 `src/mobile/modules/**`** → 新页面"展示层降 warn"策略失效，告警口径漂移（不阻断门禁，但需 M0 一并增补）。

### 8.3 本轮不做（含理由）

| 不做项 | 理由 |
|----|----|
| 不引入 Monorepo / 独立 `package.json` 子工程 | 违反用户决策 2（需可整体 `rm -rf` 回滚）；`resolve.dedupe`+`@admin` 白屏坑（`vite.config.js:52-62`）会被重新引入 |
| 不引入 TypeScript | 现工程纯 JS，改造 + 门禁重写成本远超结构收益；非本次目标 |
| 不改 `hrm-admin` / `hrm-server` | 项目规则 §12 硬约束 |
| 不删除一期占位页（`hrm-admin/src/views/{knowledge,money,performance,permission,system}`） | 项目规则 §12.2 |
| 不动 `src/shared/**` 既有导出 | 会波及 PC 端与员工端，超范围 |
| 不拆 `src/mobile/api/index.js` | §3.3：薄适配层，拆分须两端模块化同期推进 |
| 不把 `views/message/MessagePage.vue` 迁入 boss | 双端复用，属中立共享页（P-07） |
| 不改 `/boss/*` URL 与路由 meta（`tabbar`/`roles`/`title`） | §8.1 风险 3；`App.vue:60` 依赖 `meta.tabbar` |
| 不迁 `LineChart` | §3.5：通用图表，迁入会造成员工端反向依赖 |
| 不建空 `composables/` `constants/` `api/` 目录 | §3.3，反过度设计 |
| 不新增 `@boss`/`@mobile` 别名 | §3.6：既有 `@` 已够用，新增要多改 `vite.config.js` + `vitest.config.mjs` 两处 |
| 不清理 `TabbarLayout.vue:33,79` 的 `/boss/*` 硬编码 | 需改共享内核，超出本轮；登记 `TODO(扩展)` |
| 不用 `--no-verify` 绕过 pre-commit | 违反工程纪律 |

---

## 9. 实施任务拆解

> 供前端工程师按序执行。每项均可独立验收；A0 先行，A1 完成后 A2-A6 可分派并行但建议顺序推进（共享 `router/index.js` 与 `router.js` 会冲突）。

| # | 描述 | 涉及文件 | 验收方式 |
|----|----|----|----|
| **A0** | 落 lint 边界规则 4-6 + `VIEW_FILES` 增补 `'src/mobile/modules/**'` | `hrm-demo/eslint.config.js:22-28`、`:159` 之后 | G1 全绿；G11 三条负向探针各报 1 error |
| **A1** | 建 `modules/boss/` 骨架；迁样板页 `me.vue`；新建 `modules/boss/router.js`（先含 `/boss` 重定向 + `/boss/me`）；聚合点接入；同步 `index.spec.js:48` | `src/mobile/modules/boss/{router.js,views/me.vue}`、`src/mobile/router/index.js`、`index.spec.js:48` | G1/G4/G8/G9 绿；`/boss/me` URL 与 Tabbar 不变 |
| **A2** | 迁「首页与看板组」5 页并同步路由 | `modules/boss/views/{home,trend,rank,alerts,attendance}.vue`、`modules/boss/router.js` | G1/G4/G6/G9 绿（`05-render.spec.js:111-123` 趋势图用例必过） |
| **A3** | 迁「人力财务流程组」7 页并同步路由 | `modules/boss/views/{hr,hrDetail,kpi,payroll,payrollDetail,flow,flowDetail}.vue`、`modules/boss/router.js` | G1/G4/G6/G7 绿 |
| **A4** | 迁「考勤排班组」4 页并同步路由 | `modules/boss/views/{attendanceRule,attendanceRecords,schedule,makeupApproval}.vue`、`modules/boss/router.js` | G1/G4/G7 绿 |
| **A5** | 迁「请假/通知/工单组」4 页并同步路由 | `modules/boss/views/{leaveApproval,leaveSettings,notificationPublish,workorder}.vue`、`modules/boss/router.js` | G1/G4/G6/G7/G9 绿 |
| **A6** | 收尾：删空目录 `views/boss/`；107 条跨层 import 全部改走 `@/mobile/*`；全量门禁 | `src/mobile/views/boss/`（删）、`modules/boss/views/*.vue` | G4/G5/G6/G7/G8/G9/G10/G12 全绿 |
| **A7** | 负向验证落档 + 结构断言脚本化（可选，登记为 CI 检查项） | 新增校验脚本（若采纳） | G11 可一条命令复跑 |
| **A8** | 文档回写：`demo-mobile-nav-redesign.md`、`SESSION-STATE.md` 追加路径变更注记 | 两份文档 | 与 §4.2 第 29 行一致 |
| **A9** | 将 UI 规范中的 `components/boss/` 统一改写为 `modules/boss/components/`（§1.2、附录 C） | `demo-boss-ui-spec.md` | 两处路径一致 |
| **A10** | UI 规范 N-01~N-06 组件落 `modules/boss/components/`（`Boss*` 前缀）并在页面替换手写实现 | `modules/boss/components/*`、相关页面 | G1/G4/G9 绿 + 视觉走查（系统默认浏览器，标注未真机复测） |

---

## 附：开放问题（≤5，交主智能体裁决）

| # | 问题 | 影响面 | 本文建议 |
|----|----|----|----|
| Q1 | 老板端专属组件落点：`modules/boss/components/`（本文）vs UI 规范 §1.2 的 `components/boss/` | 组件库结构、lint 覆盖、与其他域同构性 | 取 `modules/boss/components/`（§3.4） |
| Q2 | `/boss/kpi/:employeeId` 复用 `views/staff/kpi.vue`（`router/index.js:109-114`）：本轮保留在聚合点 + 零例外 lint，还是提升为中立共享页（如 `views/shared/kpiDetail.vue`，参照 `views/message/MessagePage.vue` 先例）？ | 需 staff 负责人配合，跨本轮范围 | 本轮保留在聚合点并登记 `TODO(扩展)`（§3.2） |
| Q3 | `LineChart` 仅老板端消费，是否迁入 boss 模块？ | 依赖方向 | 不迁（§3.5） |
| Q4 | 是否新增 `@boss` / `@mobile` 别名？ | 需同步改 `vite.config.js` + `vitest.config.mjs` | 不新增，复用既有 `@`（§3.6） |
| Q5 | `src/mobile/api/index.js`（212 行）是否按域拆分？ | 需两端模块化同期推进 | 本轮不拆，登记 `TODO(扩展)`（§3.3） |

---

## 10. 主智能体裁决（Review 结论 · 2026-09-22）

> 主智能体已对本文全部结构性断言做独立复核（非采信转述），复核通过的证据见下表「复核」列。以下裁决为最终口径，实现阶段以本节为准。

### 10.1 五项开放问题裁决

| # | 裁决 | 依据 |
|----|----|----|
| Q1 | **采纳 `src/mobile/modules/boss/components/`**（否决 UI 规范的 `components/boss/`） | 复核通过：`eslint.config.js:25` 实测为 `'src/**/components/**'`，模块内组件已被覆盖，lint 零额外成本；所有权与「与 staff 同构」两条理由成立 |
| Q2 | **本轮保留在聚合点 `router/index.js`，登记 `TODO(扩展)`** | 提升为中立共享页需 staff 负责人同期配合，超出本轮范围；`views/message/MessagePage.vue` 先例已指明最终形态 |
| Q3 | **`LineChart` 不迁** | 通用纯展示图表，迁入将迫使员工端未来反向依赖老板模块，违反 §2.2 第 4 条 |
| Q4 | **不新增 `@boss` / `@mobile` 别名** | 既有 `@` 已覆盖；新增须同改 `vite.config.js` 与 `vitest.config.mjs` 两处 |
| Q5 | **`api/index.js` 本轮不拆** | 212 行薄适配层，拆分须两端模块化同期推进，否则产生「半迁移」状态 |

### 10.2 复核结论（主智能体独立验证，逐条通过）

| 断言 | 复核方式 | 结果 |
|----|----|----|
| `VIEW_FILES` 含 `'src/mobile/views/**'` 但不含 `modules/**` | 读 `eslint.config.js:22-28` | ✅ 属实，`M0` 增补 `'src/mobile/modules/**'` 为**必做** |
| `index.spec.js:48` 为 `vi.mock('../views/boss/home.vue')`，`:44` 有同步警示注释 | 读 `src/mobile/router/index.spec.js:44,48` | ✅ 属实，`M1` 必须同 commit 改 |
| `router/index.js` 老板端相关条目共 **24** 条（23 条随迁 + 1 条 `/boss/kpi/:employeeId` 留守） | 逐条清点 `:36`–`:163`、`:293`–`:309` | ✅ 属实，§4.2 第 22 行与第 345 行的「23 条」口径正确（1 条重定向 + 22 条页面路由，其中含中立共享页 `/boss/message`） |
| `boss/router.js` 引 `views/message/MessagePage.vue` 不被规则 4 拦截 | 比对规则 4 的 `group` 与 §5.4 未覆盖项声明 | ✅ 属实（`**/views/staff/**` 不匹配 `views/message/**`） |

> **实现阶段新增硬要求（Review 补入）**：`--stdin --stdin-filename` 组合若在本机 ESLint 上行为不符，**不得**直接判定边界规则生效——改用临时探针文件后立即删除，并保留删除记录。禁止以「未实测」为由跳过 G11。

### 10.3 必修缺陷（Review 追加进本轮实现范围）

| # | 缺陷 | 位置 | 处置 |
|----|----|----|----|
| **D-1** | 铜牌徽标对比度 **3.556:1** 不达 AA | `src/mobile/views/boss/home.vue:411-415` 用 `--c-orange-600`（`#D46B08`） | 改用 `--rank-3-bg`（`--c-bronze-700` `#92400E`，**7.090:1**）；原注释「保留原值待决策（P2-4）」解除 |
| **D-2** | 3 处可点 `<div>` 键盘不可达 | `src/mobile/views/boss/alerts.vue:152-157`、`:181`、`:202-207` | 与 `rank.vue` / `workorder.vue` / `home.vue` 同形态的正确写法对齐（`role="button"` + `tabindex="0"` + Enter/Space） |
| **D-3** | `StationPicker` 静默吞取数失败（7 页） | 消费方如 `boss/attendanceRecords.vue:116-123` 静默降级为空数组 | 随 UI 规范 G-01 补 `loading`/`error`/`emptyText` 三 prop + `retry` emit；**不得**让用户把「加载失败」读成「没有驿站」 |

> D-1 与 D-2 为**独立实算 / 独立读码复核确认的真实缺陷**，不是规范建议；D-1 的两个对比度数值由主智能体用同一 WCAG 公式复算，与 UI 规范附录 A 完全一致。

### 10.4 对 UI 规范的路径回写（本文负责，实现前完成）

`demo-boss-ui-spec.md` 中 `src/mobile/components/boss/` 一律回写为 `src/mobile/modules/boss/components/`（涉及 §1.2、§11 Q1、附录 C），其余实质要求（`Boss*` 命名前缀、专属样式禁入 `mobile.scss` 全局工具类）**原样保留**。
