# 三端演示 Demo · 移动端「员工端」模块化拆分与精细化架构规范

> 版本 v1.0 | 建立日期 2026-09-22 | 分支 `feature/员工端拆分与精细化`
> 适用范围：`hrm-dev/hrm-demo/src/mobile/**`（重点 `views/staff/**` 22 页 + `components/**` 25 组件 + `api` / `stores` / `composables` / `utils` / `router` / `constants`）
> 本文档是员工端**结构与工程边界**的唯一口径来源（`demo-pc-refactor.md` 的移动端对偶）。视觉与交互口径见 `demo-staff-ui-redesign.md`，二者冲突时的裁决规则见 §13。
> 只出规范，不产出任何业务实现代码；文中出现的目录树 / 签名 / 伪代码仅用于说明边界。

---

## 0. 取证方式与结论速览

### 0.1 取证方式

- 静态通读本工程移动端源码（逐处标 `文件:行号`，可跳转）。
- **本机实跑**：`npm run build` 与 `npm run build:prod` 均 EXIT=0，产物体积为本次实测值（§7.2），非估算。
- 未做运行时验证的项一律标注「待真机/待实测」，不写成结论（§14）。

### 0.2 结论速览（先给结论）

| 项 | 结论 |
| --- | --- |
| 拆分批次 | **9 批（B0 基建 + B1–B7 七域 + B8 性能与门禁收口）** |
| api 分域 | 单文件 88 个导出 → **13 个按域文件**，**删除 `api/index.js`（不保留 barrel）** |
| 新增 store | **1 个**（`stores/attendance.js`）；其余跨页状态由既有 `auth` / `notify` / `todo` 承担 |
| 新增跨域组件 | 4 个原子（`Chip` / `Badge` / `MiniChip` / `ListItemCard`）+ 1 个员工端域共享（`ShiftCard`）+ 1 个通用 composable 组合（`useLatestRequest` / `useListPager`） |
| ESLint 新增/调整 | **6 项**（3 组依赖边界 + 2 条体积上限 + 1 处白名单路径修正） |
| 首屏体积（生产构建，实测） | **当前 gzip ≈ 157.6 KB**（JS ≈ 113.0 + CSS ≈ 44.2）；**目标 ≤ 150 KB**（主要靠 CSS 裁剪，见 §7.3） |
| 待用户拍板项 | **4 项**（§14 R1 / R2 / R5 / R9） |

---

## 1. 现状实测与问题清单

### 1.1 关键文件实测行数（本次实测，与任务描述有出入者以实测为准）

| 文件 | 实测 | 说明 |
| --- | --- | --- |
| `src/mobile/api/index.js` | **213 行 / 88 个 `export const`** | 任务描述称「60+」，实测 88；文件头 `:8` 已标 `TODO(扩展)` 待分域 |
| `src/mobile/router/index.js` | **374 行** | 任务描述称 361，实测 374；含 meta 约定与 `beforeEach` |
| `src/mobile/views/staff/*.vue` | **22 个** | 与描述一致 |
| `src/mobile/components/*.vue` | **25 个**（另有 6 个 `*.spec.js`） | 任务描述称 26，实测 25 |
| `src/mobile/stores/` | `auth.js` 103 / `notify.js` 55 / `todo.js` 72 | — |
| `src/mobile/composables/` | `useCheckIn.js` 99 / `useReselect.js` 19 | 仅 2 个 |
| `src/mobile/utils/http.js` | 58 行 | 单 axios 实例 + 2 个拦截器 |

### 1.2 问题清单（P0 = 阻断本轮目标；P1 = 违反既有范式；P2 = 可维护性）

| # | 问题 | 依据（文件:行号） | 判级 |
| --- | --- | --- | --- |
| G1 | **单文件装 88 个接口、无域边界**，任何页面改动都要读 213 行全文件；导出名与 PC 端 `api/*.js` 不同名（如 `workorder` vs PC `workOrder`），未来无法合并 | `src/mobile/api/index.js:1-213`、头注释 `:8`；对照 `src/pc/api/workOrder.js` | P0 |
| G2 | **无竞态守卫**：全部列表页只用 `let busy` 单飞标记，**缺「发起时自增、回来时比对」**；在途时切换筛选/Tab 会被 `if (busy) return` 直接丢弃新请求，新筛选永远加载不出来 | `views/staff/workorder.vue:41,48-49`、`views/staff/parcel.vue:41,47-48`；全 `src/mobile` 搜 `reqSeq` 零命中 | P0 |
| G3 | **`constants/` 反向依赖 `api/`**：`todoGroups.js` 直接 `import` 10 个接口并把 `load()` 写在配置里，配置层与取数层耦合 | `src/mobile/constants/todoGroups.js:3-14`、`:17-21`、`STAFF_TODO_GROUPS:137-221` | P0 |
| G4 | **超长「超级页」**：`attendance.vue` 972 行（DOM + 364 行样式 + 业务判定混于一处）；`workorderDetail.vue` 563；`leaveApply.vue` 423 | 对应文件行数实测 | P0 |
| G5 | **目录是扁平的**：22 页平铺在 `views/staff/`，看不出域归属；无 `views/<域>/components` 概念 | `src/mobile/views/staff/` 目录列表 | P1 |
| G6 | **tokens 无 `--touch-min` / `--row-h-*`**（UI 规范 §3 N1–N5 新增），当前 44/48/64/76/88 散落为字面量 | `demo-staff-ui-redesign.md:130-138` | P1 |
| G7 | **ESLint 依赖边界只拦了 `shared/mock`**，**未拦「展示组件直连 `api/`」与「组件直连 `stores/`」**；也没有文件体积上限规则 | `eslint.config.js:110-159` | P1 |
| G8 | **`eslint.config.js:114` 白名单写死了 `src/mobile/utils/workorder.js`**；拆分后该文件必然迁移，白名单会失效（Mock 关闭时的动态 import 兜底属设计允许） | `eslint.config.js:114`、`src/mobile/utils/workorder.js:41-49` | P1 |
| G9 | **无统一 HTTP 重试/超时判据**：`timeout: 30000` 单一值，GET 网络抖动无重试、写操作无「不重试」的显式约定 | `src/mobile/utils/http.js:18` | P2 |
| G10 | **401 并发去重缺失**：每个 401 都 `dispatchEvent`，`App.vue` 每次 `router.replace('/login')`；同屏多发请求时会重复触发（当前无可见故障，但属无谓开销且逻辑不收敛） | `utils/http.js:48-53`、`App.vue:21-26` | P2 |
| G11 | **Vant 组件全局注册 30 个**，其中 `Calendar` / `TimePicker` / `Stepper` 仅管理端 3 页使用，其样式却进了移动端全局 CSS | `vant.js:2-32,75-106`、`:42-73`；使用点见 §7.1 | P2 |
| G12 | **跨端复用页无明确落点约定**：`/boss/kpi/:employeeId` 复用 `views/staff/kpi.vue`（`router/index.js:110-114`），`MessagePage.vue` 两端复用（`router/index.js:44-49,174-179`），但一个在 `staff/` 一个在 `message/`，命名与落点无规则 | `router/index.js:44-49,110-114,174-179` | P2 |
| G13 | **`PageState` 无 `denied` 变体**，无权限页 `flow.vue` 手写说明卡，形成第三套降级块 | `demo-staff-ui-redesign.md:167,443`；`views/staff/flow.vue:92-96` | P2 |
| G14 | **列表页 `immediate-check="false"` + 独立 `busy` 已落地**（正例，勿动），但该范本只写在 `parcel.vue:41` 的注释里，未形成规范 | `views/staff/parcel.vue:41,177` | 记录（正例） |

> **不重复建设（已达标，勿动）**：`api/` 之外的三层边界（`shared/domain` 纯函数 + 98% 单测）、`stores` 的共享态收敛、`router` 全量 `() => import()` 懒加载、Vant 已按需注册（非全量 `index.css`）、`hrm-demo` 质量链（ESLint/Prettier/Stylelint/Vitest/commitlint/e2e/verify 脚本）。

---

## 2. 目标目录约定

### 2.1 目录演进（增量，不推倒重来）

> **v1.1 修正（2026-09-22，用户拍板）**：**本轮不改页面文件路径、不动 `router/index.js`**。
> 原因：`src/mobile/router/index.js` 被并发会话占用（管理端路由抽离，脏在工作区），任何改组件 import 路径的拆分都会把他人未完成改动带入提交。
> 因此页面壳**留在原位** `views/staff/<域>.vue`，域内的组件 / composable / model 放**同名兄弟目录** `views/staff/<域>/`（文件与目录同名可共存，Vue/Vite 解析无冲突）。
> v1.0 曾写「页面移入 `views/staff/<域>/` 并让壳叫 `index.vue`」——该写法**本轮作废**，`TODO(扩展): 并发占用解除后统一迁移目录并同步 router`。

```
src/mobile/
  api/                 # 按域一文件（13 个）【已删除 index.js，不保留 barrel】B0 已完成
  components/          # 移动端跨域原子/分子（既有 25 + 本轮新增跨域 4）
  composables/         # 跨域逻辑（既有 2 + useLatestRequest + useListPager）B0 已完成
  constants/           # 【约束】只放静态配置，禁止 import api / stores
  layout/              # TabbarLayout（保持）
  router/              # 【本轮零改动】index.js 保持；meta 约定不变
  stores/              # auth / notify / todo（既有）+ attendance（B0 已完成）
  styles/              # tokens.scss / mobile.scss（Token 落地见 UI 规范 §3）
  utils/               # 跨域纯函数（http.js 职责收敛 B0 已完成）
  views/
    staff/
      components/      # 【新】员工端跨域共享组件（如 ShiftCard）
      attendance.vue   # 【原位不动】页壳
      attendance/      # 【新】同名兄弟目录：components/ + composables/ + model/
      workorder.vue  workorderDetail.vue  workorderCreate.vue
      workorder/       # 【新】components/ + composables/
      parcel.vue  parcelDetail.vue  pickup.vue
      parcel/          # 【新】components/ + composables/
      me.vue  kpi.vue  payroll.vue  payrollDetail.vue  profile.vue  flow.vue  password.vue
      me/              # 【新】components/ + composables/
      leaveApply.vue  leaveList.vue  leaveReview.vue
      leave/           # 【新】components/ + composables/
      sync.vue         # 站长专属
      sync/            # 【新】components/ + composables/
      home.vue  schedule.vue  attendanceRecords.vue  makeupList.vue
      home/  schedule/ attendanceRecords/ makeupList/   # 【按需新，确有内容才建】
    message/           # MessagePage.vue（保持原位，见 §2.2）
    boss/              # 【本轮不动】且不得删除、不得迁移（并发会话在处理）
    login/ error/
```

**规则**

1. **新增目录只在确有内容时创建**，不预留空目录（沿用 PC 规范 §2）。
2. **页面壳文件名一律保持现名**（`attendance.vue`、`workorderDetail.vue` …），**不重命名为 `index.vue`**，也**不移动位置** —— 这是本轮「零 router 改动」的硬前提。
3. 域边界机器化：`views/staff/<域>/**` 不得 `import` 兄弟域的目录；跨域复用的组件必须上提到 `views/staff/components/` 或 `mobile/components/`（ESLint 落地见 §8）。
4. ESLint 体积规则的路径匹配随之调整：页壳规则（≤150 行）匹配 `views/staff/*.vue`，普通组件规则（≤300 行）匹配 `views/staff/**/*.vue`（B0 已按「未拆分目录为 warn」渐进落地，B1 起按域收严）。

### 2.2 boss / staff 共享边界裁决（G12）

现状事实：`/boss/kpi/:employeeId` → `views/staff/kpi.vue`（`router/index.js:110-114`）；`/boss/message` 与 `/staff/message` → 同一 `views/message/MessagePage.vue`（`router/index.js:44-49,174-179`）；`LeaveApprovalList.vue` 两端复用（`views/staff/leaveReview.vue` 挂 `actor="STATION"`）。

**裁决（本轮）**

| 场景 | 裁决 | 理由 |
| --- | --- | --- |
| 同一业务对象两端复用**同一页** | **保留复用，禁止两端各写一份**。差异只允许通过**显式 prop / route.meta** 表达 | 沿用 `demo-design.md` A12-7「同一业务对象两端优先复用」；与 UI 规范 §2.3「禁止组件内 `if (isStaff)` 隐式分支」一致 |
| `kpi.vue` 的两端差异 | 页面壳接收显式 `view`（`'self' | 'boss'`，由 `route.meta.view` 注入），字号档 `value-size` 同理由 `view` 派生（修 `demo-staff-ui-redesign.md:118` 的现存反例） | 修复 P1-2 字号越级，不新建 boss 副本 |
| 跨端复用页的**目录落点** | 本轮**不迁移**：`kpi.vue` **留在原位** `views/staff/kpi.vue`（按 §2.1 v1.1，页面一律不移动、不改 router），`MessagePage.vue` 保持 `views/message/`。登记 `TODO(扩展): 跨端复用页统一迁入 views/shared/` | 迁移 `MessagePage` 会牵动管理端路由与 e2e，超出「员工端拆分」范围 |
| 两端共享的**组件** | 全部落 `mobile/components/`（如 `LeaveApprovalList`），不放 `views/` 内 | 组件与页面分开管理，避免 `views/staff/**` 被 boss 反向 import |

---

## 3. 模块划分与批次计划

### 3.1 员工端 22 页按域归属（全量，无遗漏）

| 域 | 页面（现文件） | 页数 | 主要接口域 |
| --- | --- | --- | --- |
| 首页 | `home.vue` | 1 | dashboard + parcel + attendance + workorder + kpi（聚合读） |
| 考勤域 | `attendance.vue` / `attendanceRecords.vue` / `schedule.vue` / `makeupList.vue` | 4 | attendance + schedules |
| 工单域 | `workorder.vue` / `workorderDetail.vue` / `workorderCreate.vue` | 3 | workOrder + org（候选） |
| 包裹域 | `parcel.vue` / `parcelDetail.vue` / `pickup.vue` | 3 | parcel |
| 我的域 | `me.vue` / `kpi.vue` / `payroll.vue` / `payrollDetail.vue` / `profile.vue` / `flow.vue` / `password.vue` | 7 | kpi + finance + hr + auth |
| 请假域 | `leaveApply.vue` / `leaveList.vue` / `leaveReview.vue` | 3 | leave |
| 同步域 | `sync.vue`（仅 STATION_ADMIN） | 1 | syncTask |
| **合计** | | **22** | |

### 3.2 批次计划（9 批）

依赖顺序：`B0 → {B1,B2,B3,B4,B5,B7} → B6 → B8`。每批完成即跑门禁（§10.2），通过后写入 `SESSION-STATE.md` 检查点。

> **全局约定（v1.1，适用于 B1–B7 全部批次）**：页壳**保持原位路径**（`views/staff/<域>.vue` 等，见 §2.1 v1.1 修正），组件 / composable / model 落**同名兄弟目录** `views/staff/<域>/`。
> **严禁改 `src/mobile/router/index.js` 与 `router/index.spec.js`**（被并发会话占用）。若某批确实需要改路由，**停止并报告**，由主智能体协调。

#### B0 · 基建批次（无依赖，先行）

- **本批目标**：把「跨批共用的结构与机制」一次做对，避免 7 个域各建一套。
- **文件清单 / 产物**：
  1. 删除 `src/mobile/api/index.js`，新增 13 个域文件（见表 §5.1），88 个导出**逐个有归属**（迁移清单要在 commit message 附「原→新」对照）。
  2. 38 处调用点改 import（实测：`views/**` 共 38 处 `from '.../api/index.js'`）。
  3. 新增 `src/mobile/composables/useLatestRequest.js`、`src/mobile/composables/useListPager.js`（§5.3、§9）。
  4. 新增 `src/mobile/stores/attendance.js`（§4.3）。
  5. `src/mobile/constants/todoGroups.js` 的 `load()` 迁出到 `src/mobile/stores/todo.js`（配置只留静态描述：`key/title/to/roles/params`），消 G3。
  6. `eslint.config.js` 落地 6 项规则（§8.2）+ 修正 `:114` 白名单路径。
  7. `src/mobile/utils/http.js` 收敛拦截器职责（§5.2）：GET 网络错误重试 1 次、写操作不重试、401 幂等。
- **行数上限**：新增文件均 ≤300；`useLatestRequest.js` ≤80；`useListPager.js` ≤120。
- **验收标准**：`api/index.js` 不存在；`grep -rn "api/index.js" src/mobile` 零命中；`npm run lint` 0 error；`npm run verify:mock` 全过（断言数不减）；`npm run build` / `build:prod` EXIT=0；新增单测覆盖 `useLatestRequest` 乱序响应。

#### B1 · 考勤域（依赖 B0）

- **文件清单**：`attendance.vue`(972) / `attendanceRecords.vue`(247) / `schedule.vue`(190) / `makeupList.vue`(171)。
- **拆分产物**：`views/staff/attendance.vue`（**壳，原位不改路径**，≤150）+ `views/staff/attendance/components/{ClockHero, PeriodCard, CheckSlotRow, CheckResultPanel, VerifyCard, MakeupPopup}.vue` + `views/staff/attendance/composables/{useAttendanceStatus, useMakeupForm}.js` + `views/staff/attendance/model/attendanceUi.js`（展示常量与状态键）+ 共享组件上提 `views/staff/components/ShiftCard.vue`。
  其余三页：抽 `views/staff/components/ShiftCard.vue`、`views/staff/components/StepNavButton.vue`（`attendanceRecords.vue` 与 `schedule.vue` 重复的月/周导航按钮）；`attendanceRecords.vue` 与 `makeupList.vue` 改用 `ListItemCard` + `Chip`。
- **行数上限**：壳 ≤150；组件 ≤300；`useCheckIn` 收敛后打卡页**不得保留第二份提交实现**（`composables/useCheckIn.js:17-19` 的 `TODO(扩展)` 在本批销项）。
- **依赖**：B0；组件依赖 UI 规范 §4.2 的 C1/C3/C4/C5/C6/C7/C8/C9 与 §4.3 的 `PageState.denied`。
- **验收标准**：`attendance.vue` 全文不再出现「时段判定 + 三张自查卡样式块 + 补卡弹层」三块合一的写法；判定结果仍**就近渲染在所属 `PeriodCard` 内**（`demo-staff-ui-redesign.md:339`）；`useCheckIn` 单实现（`grep -c "checkIn(" src/mobile` 只应命中 composable 与 api 各 1 处）；门禁全过。

#### B2 · 工单域（依赖 B0）

- **文件清单**：`workorder.vue`(264) / `workorderDetail.vue`(563) / `workorderCreate.vue`(238)。
- **拆分产物**：`views/staff/workorder/index.vue`（列表壳）+ `components/{WorkOrderStatusTabs, WorkOrderRow, WorkOrderCreateForm, WorkOrderTimeline, WorkOrderAssigneeSheet, WorkOrderNoteSheet}.vue` + `composables/{useWorkOrderList, useWorkOrderDetail, useWorkOrderForm}.js` + `composables/useTransferTargets.js`（由 `utils/workorder.js` 迁入，销 G8）。
- **行数上限**：列表壳 ≤150；详情/新建壳 ≤150；组件 ≤300。
- **关键约束**：列表页接入 `useListPager`（修 G2）；5 个状态 Tab 按 `TabbarLayout.vue:62-67` 范本补 `Enter/Space`（UI 规范 P0-6）；行内复制仍是可点行的**兄弟节点**（`workorder.vue:178-179` 的既有约束固化为组件约束）。
- **验收标准**：① 竞态用例（先发慢返回不覆盖后发快返回）单测通过；② `transferErrorHint` 与 Mock 8002/8003/8004 判定同口径（`utils/workorder.js:57-70`）；③ 门禁全过。

#### B3 · 包裹域（依赖 B0）

- **文件清单**：`parcel.vue`(236) / `parcelDetail.vue`(152) / `pickup.vue`(289)。
- **拆分产物**：`views/staff/parcel/index.vue`（列表壳）+ `components/{ParcelSearchBar, ParcelStatusTabs, ParcelRow, PickupResultCard}.vue` + `composables/{useParcelList, usePickup}.js`。
- **行数上限**：壳 ≤150；组件 ≤300。
- **关键约束**：搜索框对比度（UI 规范 P0-4）、Tab 键盘（P0-6）、`pickup.vue` 查询失败**必须补 error 分支**（UI 规范 P0-7），`parcel.vue:41` 的单飞范本保留并推广；`getParcels` 的 `busy` 改为 `useListPager` 的 latest-wins（修 G2）。
- **验收标准**：`parcel.vue` / `pickup.vue` 四态齐（loading/empty/error/normal）；门禁全过。

#### B4 · 我的域（依赖 B0）

- **文件清单**：`me.vue`(9) / `kpi.vue`(130) / `payroll.vue`(121) / `payrollDetail.vue`(244) / `profile.vue`(163) / `flow.vue`(106) / `password.vue`(122)。
- **拆分产物**：`views/staff/me/` 下 7 页 + `components/{KpiHero, PayrollRow, PayrollBreakdown, ProfileSalaryCard, FlowDeniedCard}.vue` + `composables/{useMyKpi, useMyPayrolls, useProfile}.js`。
- **行数上限**：各页 ≤300（本域无超长页，不强制壳拆分）。
- **关键约束**：`kpi.vue` 按 §2.2 接收 `view` 显式 prop（修 P1-2 字号越级）；`flow.vue` 改用 `PageState variant="denied"`；`payroll.vue` 计数条不得用 `0` 冒充未知（UI 规范 P0-2）。
- **验收标准**：`/boss/kpi/:employeeId` 与 `/staff/kpi` 两端渲染差异**只由 `view` 驱动**；门禁全过。

#### B5 · 请假域（依赖 B0 + `LeaveApprovalList` 改造）

- **文件清单**：`leaveApply.vue`(423) / `leaveList.vue`(364) / `leaveReview.vue`(18)。
- **拆分产物**：`views/staff/leave/` 下 3 页 + `components/{LeaveFormFields, LeaveTypeSheet, LeaveDateSheet, LeaveRow}.vue` + `composables/{useLeaveForm, useLeaveList}.js` + `model/leaveOptions.js`。
- **行数上限**：列表壳与表单壳 ≤150；组件 ≤300。
- **关键约束**：**派生标志口径不变**——`canEdit` / `canCancel` / `canRevoke` 仍以 `GET /leave/:id` 下发为权威（`api/index.js:134`），前端不得自造同义判断；操作渲染按 `leaveList.vue:187` 的既有模式继续走服务端派生标志。
- **验收标准**：请假状态机与权限矩阵（`demo-leave-design.md:109-158`）逐条可静态核对；门禁全过。

#### B6 · 首页与消息域（依赖 B1–B5、B7 完成）

- **文件清单**：`home.vue`(206) / `views/message/MessagePage.vue` / `components/{NoticeList, TodoList, TodoGroup, HomeQuickGrid, QuickGridItem, IdentitySwitcher, MeSection}.vue` / `layout/TabbarLayout.vue`。
- **拆分产物**：`views/staff/home/index.vue`（壳，纯聚合与编排）+ `components/{HomeHero, HomeAlertBar}.vue` + `composables/useWorkbench.js`（首页多路取数编排，复用 B0 的 store 与 `useLatestRequest`）。
- **行数上限**：首页壳 ≤150；组件 ≤300。
- **关键约束**：首页宫格**独立于 `PageState`**（`home.vue:191-192` 的既有理由）不得改变；宫格/待办角标继续由 `stores/{notify,todo}` 兜底（`App.vue:37-43`），不得在首页再取一遍未读数。
- **验收标准**：首页首屏请求数**不增加**（改造前后用 devtools 网络面板对比并记录）；门禁全过。

#### B7 · 同步域（依赖 B0）

- **文件清单**：`sync.vue`(292)。
- **拆分产物**：`views/staff/sync/index.vue`（壳）+ `components/{SyncCollectCard, SyncBatchRow, SyncLogSheet}.vue` + `composables/useSyncStatus.js`。
- **行数上限**：壳 ≤150；组件 ≤300。
- **关键约束**：修复「日志取数失败被吞成空态」（UI 规范 P0-1）——**错误态与空态文案必须分离**；`sync.vue:101,142` 的 32px 按钮补 `--touch-min`（P0-3）；整页 `van-loading`（`sync.vue:179`）改块骨架。
- **验收标准**：`grep -n "van-loading" views/staff/sync/**` 零命中（除按钮内 `:loading`）；门禁全过。

#### B8 · 性能与门禁收口（依赖全部）

- **产物**：Vant 样式裁剪（§7.3）、弹层类重组件 `defineAsyncComponent`、长列表实测（2000 条取样）、视觉/无障碍走查（对齐 UI 规范 §9）、全量门禁、`SESSION-STATE.md` 收口。
- **验收标准**：§7.3 体积目标达成或如实登记未达成原因；§10.2 全门禁通过；`hrm-admin` / `hrm-server` 零改动。

---

## 4. 状态边界三层裁决

### 4.1 判据（唯一，沿用 PC 规范 §4）

| 层级 | 判据 | 落点 |
| --- | --- | --- |
| 跨页共享状态 | **≥2 个路由页面或 layout 消费**，或需跨组件订阅 | `src/mobile/stores/<域>.js`（setup store 写法） |
| 域内复用逻辑 | 仅该域 ≥2 个组件共用 | `views/staff/<域>/composables/useXxx.js` |
| 服务端数据获取 | 请求 + 四态 + 竞态守卫，仅本页消费 | `views/staff/<域>/composables/useXxxData.js`（内部调 `api/`） |

- **不引入** vue-query / TanStack Query / Pinia 持久化插件等新依赖（沿用 M10 决策精神：避免过度设计）。
- **仅被单个组件消费的状态不外提**，留在组件内 `ref`。

### 4.2 现有状态逐项裁决

| 状态 | 消费方（实测） | 裁决 | 依据 |
| --- | --- | --- | --- |
| `token` / `user` / `role` / `homePath` | router 守卫、App、全部页面 | **保持 store**（`stores/auth.js`） | `router/index.js:359-372`、`App.vue:5,22` |
| 未读通知数 | Tabbar 角标、MessagePage | **保持 store**（`stores/notify.js:11-14` 注释已述理由） | 两组件唯一共享态 |
| 待办分组快照 / `counts` / `total` / `known` | 首页宫格角标、消息 Tab、待办子视图 | **保持 store**（`stores/todo.js`），并把 `todoGroups.load()` 迁入本 store（消 G3） | `stores/todo.js:8-16` |
| 今日打卡状态快照（`/attendance/status` 的 `shift` / `rule` / `periods` / `hasSchedule`） | 首页（一键打卡）+ 打卡页 | **新增 store**（`stores/attendance.js`） | 两页同接口并发 → 口径漂移 + 请求翻倍（对照 `stores/todo.js:8-16` 同构理由） |
| 打卡页「演示辅助」开关、定位结果、WiFi 模拟值 | 仅打卡页 | **留页内 `ref`** | 单组件消费，且是演示手段（`attendance.vue:57-59`） |
| 补卡弹层表单态（`makeupReason` / `makeupError` / `makeupSubmitting`） | 仅打卡页弹层 | **留组件内 `ref`**（随 `MakeupPopup` 组件走） | 单组件消费 |
| 列表分页态（`list` / `pageNum` / `finished` / `loading` / `error`） | 各列表页 | **页内 `ref` + `useListPager`**，**不进 store** | 单页消费；不同页列表语义不同（筛选/排序） |
| 筛选态（`activeTab` / `onlyOverdue`） | 各列表页 | **页内 `ref`** | 单页消费 |
| 表单草稿（`leaveApply` / `workorderCreate`） | 各表单页 | **页内 `ref`**，不做持久化 | 无跨页需求，持久化属过度设计 |
| 转单/指派候选（`fetchTransferTargets`） | 工单详情弹层 | **域内 composable**，不缓存 | 每次打开重拉（`workorderDetail.vue:178-205` 既有语义） |
| 运行日志缓冲 | `shared/clientLog.js` | **保持模块级单例** | 已有唯一实现，勿动 |
| 员工端跨域共享的展示映射（如 `ShiftCard` 的契约色→Token 表） | 考勤域多组件 | **`views/staff/model/` 或组件内 `.js`**，不进 store | 纯映射，无响应式需求 |

### 4.3 新增 store 清单（1 个）

**`src/mobile/stores/attendance.js`**（setup store）

- **职责**：持有 `status` 快照（`shift` / `rule` / `periods` / `hasSchedule`）、`loading`、`error`，并提供 `refresh()`；`useCheckIn` 消费其 `rule` / `periods`，打卡成功后回写该槽位状态（就地对齐，不整页重拉）。
- **不持有**：演示辅助开关、定位结果、补卡弹层表单（属页面）。
- **边界**：不得把 `attendanceRecords` 的按月记录放进本 store（那是另一个接口 + 另一页消费）。
- **失败语义**：`error` 与「无排班/无规则」必须分开——`hasSchedule === false` 是业务空态，接口失败才是 `error`（对齐 UI 规范 §6.4 硬规则 1）。

---

## 5. 数据通信机制

### 5.1 api 按业务域拆分（13 文件，88 导出全覆盖）

**命名规则**：与 PC 端（`src/pc/api/*.js`）**同名同域**，为未来「移动端与 PC 端 api 合并」留路；移动端特有的域才用新名。**不保留 `api/index.js` barrel**（避免 tree-shaking 失效与循环依赖风险）。

| # | 文件 | 承载导出（现状名） | 数量 |
| --- | --- | --- | --- |
| 1 | `api/auth.js` | `login` / `logout` / `getMe` / `updatePassword` | 4 |
| 2 | `api/dashboard.js` | `getDashboardSummary` | 1 |
| 3 | `api/parcel.js` | `getParcelSummary` / `getParcelTrend` / `getParcelRanking` / `getParcels` / `getParcel` / `pickupParcel` | 6 |
| 4 | `api/syncTask.js` | `getSyncTasks` / `getSyncTask` / `getSyncLogs` / `getSyncOverview` | 4 |
| 5 | `api/workOrder.js` | `getWorkOrders` / `getWorkOrder` / `createWorkOrder` / `changeWorkOrderStatus` / `assignWorkOrder` / `transferWorkOrder` | 6 |
| 6 | `api/attendance.js` | 规则 3 + 打卡与记录 5 + 排班/班次 6 + 补卡 4（含 `getStationRoster`，实为 `/schedules` 的人员列） | 18 |
| 7 | `api/leave.js` | 申请/试算/我的/详情/改/撤/重提/列表/初审/终审/撤回/设置 2 | 13 |
| 8 | `api/kpi.js` | 指标 3 + 算分/排名/得分/明细/静默明细 | 8 |
| 9 | `api/hr.js` | 档案 2 / 定薪 2 / 入职流程 4 / 离职流程 4 | 12 |
| 10 | `api/finance.js` | 工资单 8（与 PC `api/finance.js` 同名） | 8 |
| 11 | `api/notification.js` | 列表/未读数/已读 ×3/发布 | 5 |
| 12 | `api/org.js` | `getEmployees` / `getStationList`（组织主数据，移动端只作候选与选择器） | 2 |
| 13 | `api/system.js` | `reportClientLogs` | 1 |
| | | **合计** | **≈88** |

- **迁移规则**：**导出名一个都不改**（避免 38 处调用点的语义同时变动）；只改 import 路径。
- **`getStationRoster` 的归属**：因其真实端点是 `/schedules`（`api/index.js:89-93`）且 `silent: true`，归 `attendance.js`，并在文件注释里写清「站长名册复用排班接口」的既有理由（`utils/workorder.js:19-23`）。
- **TODO(扩展)**：PC 端 `api/hr.js` / `api/attendance.js` 与本文件未来可合为一处共享层（需先解决 `@admin` 只读边界），本轮不做。

### 5.2 `http.js` 拦截器职责（收敛，不换实现）

| 职责 | 现状 | 本轮规范 |
| --- | --- | --- |
| 鉴权注入 | 请求拦截器读 `readToken()` 写 `Authorization`（`http.js:20-24`） | **保持**；仍不得 import `stores/auth.js`（循环引用，`authStorage.js:5-7` 已述） |
| `silent` 语义 | `config.silent === true` 时不弹全局 Toast（`http.js:26-30`） | **保持并写清契约**：`silent` 仅用于「调用方**必然**在页内/弹层内渲染错误」的场景；调用方必须真的渲染，否则等于吞错。现有 silent 端点清单以 `api/index.js` 注释为准，拆分时**逐条把 silent 理由随导出一起搬走** |
| 错误码 → 用户可读文案 | 优先用 `body.message`，缺省由 `codeMessage(code)` 兜底（`shared/constants/errorCode.js:253-255`） | **保持**；页面级「三步式」文案（现象 + 原因 + 下一步）由域 `utils` 提供，范本：`utils/attendance.js#checkErrorHint`、`utils/leave.js#leaveErrorHint`、`utils/workorder.js#transferErrorHint` |
| 401 处理 | 清 token + 广播 `UNAUTHORIZED_EVENT`（`http.js:48-53`），`App.vue:21-26` 清理并 `replace('/login')` | **保持事件机制**；补**幂等**：`App.handleUnauthorized` 在「已在登录页」时直接 return（消除 G10），并在 http 侧对同一 tick 内的多个 401 只广播一次（实现细节入 B0） |
| 超时 | `timeout: 30000`（`http.js:18`） | 规范：**读请求 15s / 写请求 30s**（移动端弱网 + 打卡等写操作需给足）；按 `method` 在拦截器内设定 |
| 重试判据 | 无 | 规范：**仅 GET 且「无 response 的网络错误」**重试 **1 次**，退避 500ms；**写操作一律不重试**（幂等性未保证）；业务码失败不重试；401/403/404 不重试 |
| 响应分发 | `code === 200` 取 `data`，否则 reject 并带 `code`/`data`（`http.js:32-43`） | **保持**（Mock 引擎结论 V3 已实测该链路生效，`shared/mock/engine.js:21-24`） |

### 5.3 竞态守卫统一方案（修 G2）

**问题定位（实测）**：移动端**没有任何**请求序号守卫；列表页用 `let busy` 单飞（`workorder.vue:41,48-49`、`parcel.vue:41,47-48`），在途期间的新请求被 `if (busy) return` 丢弃 → **切换筛选永远不刷新**。PC 端的正确实现可借鉴：`src/pc/views/workOrder/composables/useWorkOrderList.js:72-88`（发起时 `seq = ++listSeq`，回来 `if (seq !== listSeq) return`）。

**统一方案**：新增 `src/mobile/composables/useLatestRequest.js`（纯逻辑、可单测）

```js
// 伪代码：只表达契约，不落地实现
export function useLatestRequest() {
  // 返回 { run, isLatest, cancel }
  // run(task): 发起前自增序号；await 后比对，非最新则丢弃（含 data / error / loading 的丢弃）
  // 要求：自增必须发生在「发起时」，不得只在筛选变化时自增（否则漏掉首屏与筛选的竞争）
}
```

- **硬要求**：`loading` 的关闭也必须受序号保护（过期响应不得把新请求的 `loading` 关掉）。
- **与 `van-list` 单飞的关系**：`useListPager.js`（新）内部用 `busy` 做「触底回调单飞」（`van-list` 会自身置 `loading`，需独立标记，范本 `parcel.vue:41`），用 `useLatestRequest` 做「筛选/刷新整表替换」的 latest-wins。**二者不互相顶替**：单飞防重复请求，序号守卫防过期覆盖。
- **落地范围**：`workorder` / `parcel` / `payroll` / `leaveList` / `makeupList` / `sync` 历史批次 / 首页多路取数。

### 5.4 请求缓存的适用与不适用（禁止过度设计）

| 场景 | 是否缓存 | 理由 |
| --- | --- | --- |
| 跨页共享且同会话内基本不变（如「今日打卡状态」） | **缓存在 store**（§4.3），`refresh()` 显式失效 | 这是「共享态」不是「缓存」；两页消费必须同源 |
| 列表分页数据（工单/包裹/工资单） | **不缓存** | 分页 + 筛选组合爆炸；缓存收益低于一致性风险 |
| 详情数据（`workorder/:id` 等） | **不缓存** | 详情有状态流转，返回列表后必须取最新 |
| 字典（`dict.js`） | 无需缓存 | 已是编译期静态常量，不产生请求 |
| 驿站列表 / 员工候选 | **本轮不缓存**，登记 `TODO(扩展)` | 目前仅管理端多页使用，为它建缓存层属过度设计 |
| **统一结论** | 本轮**不引入任何请求缓存层**（除 store 持有的共享态） | 沿用 PC 规范 §4「不引入 vue-query 等新依赖」 |

### 5.5 与 Mock 层的契约对齐与扩展流程

**对齐方式（保持）**：`api/*.js` 的路径与入参**严格对齐** `src/shared/mock/routes/*`（即 `api.md` + `demo-design.md` 7.4），拆分时**只改文件归属，不改路径/入参/导出名**。

**扩展流程（本轮允许，须按序执行）**

1. 在对应 `src/shared/mock/routes/<域>.js` 增加 handler，并在 `routes/index.js:25-46` 注册（**注意顺序**：静态路径必须在 `:param` 之前，注释 `:33-34` 已述）。
2. 新错误码必须同步写入 `shared/constants/errorCode.js` 的对应段位 **与** `CODE_MESSAGE`（文件内 `:156-157` 已写「每码必须同时写入 CODE_MESSAGE」），否则出现「有码无文案」。
3. 新增/调整 `scripts/verify-mock.mjs` 断言；**既有断言的语义不得弱化或删除**（改写文案可以，放松判定不可以）。
4. 更新 `api/*.js` 与本文档 §5.1 归属表。

**错误码分段（现状，勿动；新段位顺延）**

| 段位 | 归属 | 出处 |
| --- | --- | --- |
| 10xx–50xx | 一期认证/员工/部门/驿站/导入导出 | `errorCode.js:17-52` |
| 60xx | 同步任务/采集 | `:59-61` |
| 70xx | 包裹 | `:62-64` |
| 80xx | 工单 | `:65-70` |
| 90xx | 通知 | `:71-72` |
| 91xx | 考勤与排班（**已从 90xx 顺延，理由见 `:75-83`**） | `:84-97` |
| 92xx / 93xx / 94xx / 95xx / 96xx | KPI / 人事 / 财务 / 同步配置 / 请假 | `:104-167` |
| **97xx 起** | **下一可用段位**（新增域从此顺延） | 本规范 |

### 5.6 弱网与壳内 WebView 降级策略

| 场景 | 规范 | 依据 / 现状 |
| --- | --- | --- |
| 弱网超时 | 读 15s / 写 30s（§5.2）；超时后页内 error + 重试，不自动重试写操作 | `http.js:18` |
| 页内错误 vs Toast | 首屏失败用页内 error；列表已有数据时的翻页失败用 Toast；弹层内失败在弹层内联 | UI 规范 §6.4 三形态判据 |
| 剪贴板不可用（`file://`） | 保持双通道降级（复制失败给可选中文本提示） | `components/WorkOrderCopyButton.vue:36-39` |
| 无壳环境（浏览器） | `bridge.js` 全部方法空实现兜底；`--status-bar-height` 写 0px，布局不塌陷 | `bridge.js:14-17,70-74,93-105` |
| WiFi / 定位在浏览器不可得 | 返回模拟值并**显式标注「模拟/演示辅助」**，不得伪装成真实能力 | `bridge.js:41-64`、`attendance.vue:41-44` |
| 壳返回键 | `HrmShell.onBackPressed` 复用 history，根页交原生处理 | `bridge.js:76-105` |
| 键盘弹起 | 底部固定栏与弹层滚动（`max-height + overflow-y`）保持；`windowSoftInputMode` 待真机（UI 规范 U6） | UI 规范 §7.3 |

---

## 6. 身份验证与权限管理

### 6.1 现状三重机制（实测）

| 层 | 实现 | 位置 |
| --- | --- | --- |
| 页面级 | `route.meta.roles` 白名单 + `beforeEach` 调 `canAccess` | `router/index.js:8-9,359-372` |
| 纯函数 | `canAccess(roles, user)`（`roles` 空 = 放行） | `shared/domain/permission.js:5-8` |
| 服务端（Mock） | `matched.roles` 命中即 403；数据范围由各 handler 自行覆盖 | `shared/mock/engine.js:170-174` |

### 6.2 现状缺口（实测）

| # | 缺口 | 依据 |
| --- | --- | --- |
| P1 | **Mock 无统一数据范围中间件**：`engine.js` 只做「路由级 roles」，`stationId` 收敛**散落在各 handler** 内自行覆盖（请假文档明示参照 `routes/parcel.js:13-17` 的写法） | `engine.js:164-182`；`demo-leave-design.md:154` |
| P2 | **路由级 roles 是「可选声明」**：未声明 `roles` 的路由默认放行，新增路由漏声明不会有任何提示 | `engine.js:170` 的 `matched.roles &&` 短路 |
| P3 | **派生标志口径不统一**：请假详情由服务端下发 `canEdit/canCancel/canRevoke`（`api/index.js:134`），而工单/补卡/工资单的「能否操作」在前端按状态+角色本地判断 | `api/index.js:134` vs `views/staff/workorderDetail.vue` / `boss/makeupApproval.vue:195-200` |
| P4 | **页面级判定与操作级判定未分档**：`STAFF_ROLES` / `ALL_ROLES` 只是路由白名单，不能表达「同页内不同角色可用操作不同」 | `router/index.js:8-9,226,238,251` |

### 6.3 收口方案：页面级 / 操作级 / 数据级三级

| 级别 | 谁来定 | 前端职责（只做体验拦截） | 服务端职责（唯一权威） |
| --- | --- | --- | --- |
| **页面级** | `route.meta.roles` + `canAccess` | 守卫拦截 + Toast 提示 + 回首页；菜单/宫格项按 `roles` 过滤（`quickEntries.js:44`、`todoGroups.js:209` 已用同口径） | 端点 `roles` → 403 |
| **操作级** | 派生标志（服务端优先） | 无标志时按「状态 + 角色」本地判定**集中到域 composable**，不散落模板；有标志时**只渲染、不推导** | 状态机非法流转 → 业务码（如 8001/9602/9403） |
| **数据级** | **服务端独有**；前端只传参、不做归属过滤 | 不得自行按 `stationId` 过滤列表（避免两处口径） | 非 ADMIN 强制覆盖 `stationId`；跨站/越权 → 403 或业务码 |

**必须由服务端权威（前端不做，做了也无效）**：可见范围（驿站/本人）、审批人归属（不能审自己）、状态机流转合法性、派生操作标志、ADMIN 提交请假（9605）。

**落地动作**

1. **数据级收口（消 P1）**：在 `shared/domain/` 新增纯函数 `applyDataScope(params, user)`（非 ADMIN 覆盖 `stationId` / `employeeId`），由 `engine.js` 在调用 handler 前统一执行；各 handler 里重复的覆盖逻辑删除。**此项改动触及 `shared/mock` 与 878 断言，须按 §5.5 流程同步断言**，标注 `TODO(扩展)` 若本轮不做。
2. **路由级显式化（消 P2）**：`engine.js` 对「需要鉴权且未声明 `roles`」的路由要求显式写 `roles: ALL`（或等价常量），未声明视为配置错误并在 `verify-mock.mjs` 加一条静态断言。
3. **派生标志补齐（消 P3）**：`workOrder` / `makeup` / `payroll` 的详情 VO 建议由服务端下发与请假同名的派生标志；契约未补齐前，本地判定**必须集中在** `views/staff/<域>/composables/`，并由单测钉住三种角色的组合。
4. **分档表**：在本文档 §6.4 固化「页 × 角色 × 可用操作」矩阵，作为 B1–B7 的验收依据。

### 6.4 员工端页面 × 角色权限矩阵（按实测路由 meta 整理）

| 页面 | ADMIN | STATION_ADMIN | STAFF | 关键操作级差异 |
| --- | --- | --- | --- | --- |
| `/staff/home` `/staff/message` `/staff/me` | ✗ | ✓ | ✓ | — |
| `/staff/attendance`（打卡） | ✗ | ✓ | ✓ | 补卡入口按槽位状态 |
| `/staff/schedule` / `records` / `makeup` | ✗ | ✓ | ✓ | — |
| `/staff/parcel`（列表） | ✗ | ✓ | ✓ | — |
| `/staff/parcel/:id` | ✓ | ✓ | ✓ | 取件核销 + 上报异常 |
| `/staff/workorder`（列表） | ✗ | ✓ | ✓ | 新建入口 |
| `/staff/workorder/:id` | ✓ | ✓ | ✓ | 接单/解决/关闭/指派/转单按角色与状态 |
| `/staff/sync` | ✗ | ✓ | ✗ | 只读（重试在 PC） |
| `/staff/leave/apply` `/staff/leave` | ✗ | ✓ | ✓ | 改/撤按派生标志 |
| `/staff/leave/review`（初审） | ✗ | ✓ | ✗ | 不能审自己（服务端 9605） |
| `/staff/kpi` `/staff/payroll` `/staff/profile` `/staff/flow` | ✗ | ✓ | ✓ | `flow` 为只读降级 |
| `/boss/kpi/:employeeId` | ✓ | ✗ | ✗ | 复用同页，只读 |

> 依据：`router/index.js` 各路由 `meta.roles`；`/boss/kpi/:employeeId` 见 `:110-114`，`/staff/sync` 见 `:240-246`。

> **本次实际收口结果（R5 子任务，2026-09-22，独立 commit）**：① **数据级（P1）已收口** —— 新增纯函数 `shared/domain/applyDataScope.js`，由 `engine.js` 在调 handler 前对查询参数统一执行，非 ADMIN 的 `stationId` 一律收敛为本人归属驿站；已删除 `parcel` / `workOrder` / `syncTask` / `attendance` / `kpi` / `leave` 六个路由文件中重复的**查询参数**覆盖逻辑（`employeeId` 不收敛：审计确认现存唯一被归属覆盖的参数就是 `stationId`，「只看本人」端点直接读 `user.id`，不臆造；body 的 `stationId` 覆盖保留在工单新建与打卡，属写操作防代提交，不属查询参数收敛）。② **路由级显式化（P2）已收口** —— `role.js` 新增 `ALL_ROLES`，engine 加载期对「鉴权但未声明 roles」直接判为配置错误抛错，`verify-mock.mjs` 新增 1 条静态断言（878 → 879）。③ **派生标志（P3）保留 `TODO(扩展)`、本轮不做** —— 工单/工资单详情补派生标志当前无消费方，且口径需按「状态机 × 角色」逐条定稿；补卡更无独立详情端点，Mock 单方面下发会与前端既有本地判定形成双口径，故留 TODO 于 `routes/workOrder.js`、`routes/finance.js`、`routes/attendance.js`，待契约定稿后补并同步断言与前端。④ **分档表（P4）以上矩阵即验收依据**，B1–B7 按本表逐条核对。

---

## 7. 性能策略

### 7.1 Vant 按需引入现状核查（结论：已按需，勿重做）

- `vant.js` 手写注册表：**30 个组件**（`:75-106`）+ **30 条样式按组件引入**（`:42-73`），**未引 `vant/lib/index.css`**；文件头 `:34-41` 已说明为何不用 `unplugin-vue-components`。
- 组件用量实测：`van-stepper`（`boss/attendanceRule.vue:351,363,383,395,451`、`boss/kpi.vue:414`）、`van-time-picker`（`boss/attendanceRule.vue:489`）、`van-calendar`（`staff/leaveApply.vue:311,319`）等**均有真实使用**，故「注册了但没用」的比例很低——**裁剪空间有限**（见 §7.3 的保守目标）。
- **路由懒加载已全量**：`router/index.js` 每个 `component` 均为 `() => import()`（含 `boss` 全部页）。

### 7.2 首屏体积基线（本次实测，生产构建 `npm run build:prod`）

| 文件 | 原始 | gzip | 说明 |
| --- | --- | --- | --- |
| `mobile-*.js`（入口） | 128.97 KB | **46.08 KB** | 入口 + Vant 组件逻辑 |
| `clientLog-*.js` | 171.36 KB | **65.67 KB** | `shared/clientLog.js` + 其依赖（axios 等被合并至此） |
| `dict-*.js` | 7.09 KB | 2.60 KB | 共享字典 |
| `role-*.js` | 0.38 KB | ≈0.3 KB | 角色常量 |
| `mobile-*.css` | 130.27 KB | **44.17 KB** | Vant 已注册组件样式 + 本项目移动样式 |
| **首屏 gzip 合计** | — | **≈ 157.6 KB** | JS ≈ 113.0 + CSS ≈ 44.2 |

> 依据：`dist/mobile.html:17-21` 的 `modulepreload` 列表（clientLog / role / dict）；CSS gzip 为本机 .NET GZipStream 实算。

**关键结论**：`clientLog` 占首屏 JS 的 58%，但 `mobile/main.js:34-46` 明确要求「必须在挂载前初始化」（装晚了首屏异常漏采），**属既定的设计取舍，本轮不动**。

### 7.3 性能目标与手段（可测）

| 目标项 | before（实测） | 目标 | 手段 | 风险 |
| --- | --- | --- | --- | --- |
| 移动端首屏 gzip 合计 | ≈ 157.6 KB | **≤ 150 KB** | 把**仅管理端/单页使用**的 Vant 组件样式（`Calendar` / `TimePicker` / `Stepper` / `Picker`）从 `vant.js` 全局注册中移出，改由使用页局部 `import 'vant/es/<c>/style/index'`（**样式仍加载，仅改加载时机**） | 估算收益 3–8 KB gzip，**收益偏低；若实测 <3 KB 则如实登记不强行凑数** |
| 首屏 JS gzip | ≈ 113.0 KB | **不设硬目标** | 仅登记；移除 `clientLog` 静态依赖违反 `:34-46` 的硬约束，不做 | — |
| 弹层类重组件 | 随页面 chunk | `defineAsyncComponent` | 补卡弹层、人员选择弹层、日志弹层、日历/时间选择弹层 | 首屏弹层可能多一次网络往返（同 chunk 已 preload 则无感） |
| 长列表（2000 条 mock 取样） | 未测 | 滚动无可感卡顿 | 保持 `van-list` 20/页；**本期不引入虚拟化**（UI 规范 U5 同结论） | 20 万级真实量未验证 |
| 路由懒加载 | 已全量 | 保持 | — | — |

**构建产物对比方法**：改动前 `git stash` → `npm run build:prod` → 记录 `dist/assets/mobile-*.js` 与 `mobile-*.css` 的 gzip（用 §7.2 同法实算）→ `git stash pop` → 改动后同法再测 → 差值写入本节表格。

### 7.4 长列表策略

- 分页 20/页，`immediate-check="false"`，独立 `busy` 单飞（范本 `parcel.vue:41,177`）；首屏由 `loadFirst` 负责，`loadingMore` 初值 `false`。
- 返回定位：全局 `scrollBehavior: { top: 0 }`（`router/index.js:356`）；per-Tab 记忆登记 `TODO(扩展)`（`TabbarLayout.vue:50`）。
- **不引入虚拟化**（无新依赖红线）。

---

## 8. 组件契约与分层禁令 + ESLint 规则清单

### 8.1 组件契约（沿用 PC 规范 §3，移动端差异已注明）

1. **命名**：组件文件与组件名 PascalCase 多词（`PageState.vue` → `PageState`）；既有单词组件（`PageNav`/`SlaTag`）不强制改名（`eslint.config.js:65-66` 已关闭 `multi-word-component-names`）。
2. **数据流**：`props` 进 / `emits` 出；禁止子组件改 props。
3. **分层禁令**：
   - **展示组件**（`mobile/components/**` 与 `views/**/components/**`）：禁止 `import` `api/**`、禁止 `import` **任何端**的 `stores/**`、禁止 `import` `shared/mock/**`；数据由容器或 composable 经 props 注入。
   - **容器**（`views/**/*.vue` 页面、`composables/**`、`stores/**`）：允许 import store / api / composable。
   - **`constants/**`**：禁止 import `api/**` 与 `stores/**`（消 G3）。
4. **四态覆盖**：所有承载数据的组件显式覆盖 `loading / empty / error / normal`，**只允许用 `PageState`**（UI 规范 §0.1），不得自造第二套；无权限走 `PageState variant="denied"`。
5. **体积约束**：普通组件 **≤300 行**；页面壳（拆为「壳 + 子组件」的页）**≤150 行**。移动端与 PC 的差异与理由：移动端每域多页、壳不强制命名 `index.vue`，故「壳」按**职责**判定（仅路由取参 + 数据编排 + 区块组合 + 四态，不含区块 DOM 细节），并以文件名 `index.vue` 作机器可识别的标记（§2.1 规则 2）。
6. **事件命名**：动词过去式或名词（`change` / `submit` / `select`），不用 `onXxx`。
7. **无障碍基线**：交互元素 ≥44×44（`--touch-min`）；图标 `aria-hidden="true"`；有数值的入口 `aria-label` 含数值；`van-tabs` / `van-switch` 必须补 Enter/Space（UI 规范 §8.3）。

### 8.2 ESLint 规则清单（新增/调整 6 项，机器可强制）

| # | 规则 | 目标文件 | 内容 | 解决 |
| --- | --- | --- | --- | --- |
| L1 | `no-restricted-imports`（新增第 4 组） | `src/mobile/components/**`、`src/mobile/views/**/components/**` | 禁 `**/api/**`、`@/mobile/api/**`；提示「展示组件不得直连接口，数据由容器/composable 注入」 | G7 |
| L2 | `no-restricted-imports`（新增第 5 组） | 同 L1 | 禁 `**/stores/**`、`@/mobile/stores/**`、`@/pc/stores/**`；提示「展示组件不得直接 useStore」 | G7 |
| L3 | `no-restricted-imports`（新增第 6 组） | `src/mobile/constants/**` | 禁 `**/api/**`、`**/stores/**`；提示「constants 只放静态配置」 | G3 |
| L4 | `max-lines` | `src/mobile/**/*.vue` | `{ max: 300, skipBlankLines: true, skipComments: true }`（普通组件） | G4 |
| L5 | `max-lines` | `src/mobile/views/**/index.vue` | `{ max: 150, skipBlankLines: true, skipComments: true }`（页面壳） | G4 |
| L6 | 修正既有 `ignores` 白名单 | `eslint.config.js:114` | `src/mobile/utils/workorder.js` → 拆分后的 `src/mobile/views/staff/workorder/composables/useTransferTargets.js`（Mock 关闭时动态 import 兜底的装配点） | G8 |

**保持不变（勿动）**：`eslint.config.js:110-128` 的 `shared/mock` 禁入规则、`:129-142` 的 `shared/domain` 禁反向依赖 mock、`:143-159` 的 `src/portal` / `src/demo` 规则、`:161-171` 的 a11y 降级策略（**不启用 `--max-warnings 0`**，理由 `:12-14` 已述）。

**实际落地方式（B0 实施记录）**：本表未规定严重级别，故按「拆分产物从严、存量从宽」渐进落地 ——

- **error**：`src/mobile/views/**/components/**`（L1/L2）、`src/mobile/views/staff/**/index.vue`（L5 页面壳）、`src/mobile/constants/**`（L3）。
- **warn**：`src/mobile/components/**`（L1/L2：既有 5 个文件直连 `stores`、`LeaveApprovalList.vue` 直连 `api`，待 B5/B6 上提取数后升 error）、`src/mobile/**/*.vue`（L4：存量整页尚未拆分）、`src/mobile/views/**/index.vue`（L5：`login/index.vue` 是既有整页而非壳）。
- 依据：存量若一律设 error，`npm run lint` 不可能 0 error，规范会被 `--no-verify` 绕过；随 B1–B8 逐批收敛后再升 error。
- L6 白名单：保留 `src/mobile/utils/workorder.js`（B2 迁走前仍需放行），并补入拆分后的落点 `src/mobile/views/staff/workorder/composables/useTransferTargets.js`。

---

## 9. 竞态守卫与错误处理统一方案

### 9.1 竞态守卫（同 §5.3，此处给落地契约）

- **公共实现**：`src/mobile/composables/useLatestRequest.js`（纯逻辑，无 DOM，Vitest 直测）。
- **分页组合**：`src/mobile/composables/useListPager.js`（`van-list` 单飞 + `useLatestRequest` latest-wins + reset）。
- **必测边界**：乱序响应（先发慢回不覆盖后发快回）、过期响应不得关闭新请求的 `loading`、reset 后旧响应不得写入、连续两次筛选、组件卸载后响应丢弃。
- **验收**：单测文件 `src/mobile/composables/useLatestRequest.spec.js`、`useListPager.spec.js`。

### 9.2 错误处理三形态与硬规则（对齐 UI 规范 §6.4，架构侧固化为门禁项）

| 形态 | 用在哪 | 禁止用在哪 |
| --- | --- | --- |
| 页内（`PageState error` / 页内提示条） | 首屏主数据失败；用户会「回看」的失败结论 | 一次性动作失败 |
| Toast | 动作已完成的瞬时确认；列表已有数据时的翻页失败 | 首屏失败 |
| 弹层内联 | 弹层内字段/提交失败 | 需跳转才能解决的错误 |

**硬规则**：① **绝不用 `0` 或空列表表达失败**；② 同一失败不得同时出页内 + Toast（`silent` 保证）；③ 错误文案三段式（现象 + 原因 + 下一步），集中在域 `utils` 的 `xxxErrorHint(code, ctx)`。

---

## 10. 行为红线与门禁

### 10.1 「业务口径不变」的等价性定义（本轮）

拆分与重设计**允许**：目录/组件/视觉/交互形态变化；移动端新增体验能力（键盘可达、四态补全、Token 落地）。
**不允许（违反即回滚该子任务）**：

1. 接口路径、入参、导出名变化（§5.1 只改 import 路径）。
2. 错误码值与 `CODE_MESSAGE` 语义变化（§5.5）。
3. 业务状态字典值变化（`shared/constants/dict.js`）；`PARCEL_STATUS` 值 1 的三端文案口径若统一，需按 UI 规范 P0-5 一次性改并同步断言。
4. 权限判定口径变化（§6）：谁可见、谁能操作、数据范围不得放宽。
5. 派生标志口径变化（请假 `canEdit/canCancel/canRevoke` 仍服务端权威）。
6. DOM 语义退化（按钮文本、`aria-label`、`role`、可聚焦顺序）——e2e 依赖它。
7. `VITE_MOCK_ENABLED` 只有显式 `true` 才启用 Mock；`build`（演示态，三入口 + Mock）与 `build:prod`（生产态，剥离 Mock，仅 pc/mobile）**不可混用**（`vite.config.js:16-27,104-116`、`mobile/main.js:53-60`）。

### 10.2 门禁命令与通过判据

| 命令 | 通过判据 | 现状基线 |
| --- | --- | --- |
| `npm run verify:mock` | 全过，失败 0；**既有断言语义不弱化**（新增可加） | 878 项（`SESSION-STATE.md:34`） |
| `npm run verify:mobile` | 全过，失败 0 | 48 项（同上） |
| `npm run test` | 全过；用例数只增不减 | 150（同上） |
| `npm run e2e` | 全过，失败 0 | 37（同上） |
| `npm run lint` | **0 error**（warn 允许，配置刻意不 `--max-warnings 0`） | 0 error |
| `npm run lint:style` | 0 error | — |
| `npm run build` / `npm run build:prod` | EXIT=0 | EXIT=0（本次实测） |
| `npm run verify:tokens` | 通过 | — |
| `git status` | `hrm-dev/hrm-admin/**`、`hrm-dev/hrm-server/**` **零改动** | — |

> 注：任务描述称脚本为 `scripts/verify-mobile.mjs`，**实测文件名为 `scripts/verify-mobile-t13-t16.mjs`**（`package.json:14`），引用时以此为准。

### 10.3 提交粒度

一个域一次提交，信息形如 `refactor: 拆分员工端打卡页为壳+组件+composable`（`refactor` / `perf` 按项目 commit 规范）。**拆分与缺陷修复不得混在同一次提交**（发现的既有缺陷另列一条 `fix`）。

---

## 11. 测试策略

### 11.1 覆盖对象与落点

| 对象 | 落点 | 命名 |
| --- | --- | --- |
| `stores/**` | 同目录 | `<name>.spec.js`（`auth` / `notify` / `todo` 已有先例：`notify.spec.js`、`todo.spec.js`） |
| `composables/**` | 同目录 | 重点 `useLatestRequest` / `useListPager` / `useCheckIn` / `useWorkOrderList` |
| `utils/**` 与 `views/**/model/**` | 同目录 | 纯函数直测（`format.spec.js` 已有先例） |
| 域内 composable | `views/staff/<域>/composables/` 同目录 | 权限判定组合、派生标志、四态切换 |
| 组件（需 DOM） | 同目录 + 文件首行 `// @vitest-environment jsdom` | 仅测「交互语义」不测快照（`vitest.config.mjs:11-12`） |
| 覆盖率门槛 | **不新增门槛**，仅 `shared/domain` 保持 90%（`vitest.config.mjs:28-33`） | 不为凑覆盖率写快照 |

### 11.2 必测边界（统一清单）

空集 / 单元素 / 极值 / 非法输入 / **竞态（乱序响应）** / 四态切换 / 权限三角色组合 / `0` 与 `null`（未知）不互相顶替 / `hasSchedule=false` 与 `error` 不互相顶替。

### 11.3 集成链路清单（端到端数据链路，`verify:mobile` 覆盖方向）

1. 登录（三角色）→ 首页聚合取数（打卡状态 + 指标 + 待办角标）。
2. 打卡：取 WiFi/定位 → 提交 → 按 9101–9109 分类提示 → 首页状态同步。
3. 补卡：提交 → 待审批 → 审批通过 → 打卡记录出现 `source=MAKEUP` 记录。
4. 工单：列表筛选 → 详情接单/解决/关闭 → 指派/转单候选（三角色三路径）→ 失败码 8002/8003/8004 提示。
5. 包裹：列表搜索 → 详情 → 取件核销 → 状态流转（7002/7003）。
6. 工资单：列表 → 详情 → 确认 / 提异议（9403 就地对齐）。
7. 请假：申请 → 试算 → 站长初审 → 管理员终审 → 撤回（9602/9603/9606）。
8. 通知：未读角标 → 标记已读/全部已读 → 跳转业务详情。

> 上述链路以 `scripts/verify-mobile-t13-t16.mjs` 与 `e2e/0{3,4,5,7}-*.spec.js` 为回归网，**拆分不得使任一链路退化**。

---

## 12. 交付节奏与检查点

- 每批完成即跑 §10.2 门禁；通过后在 `SESSION-STATE.md` 追加一行检查点（进度 + 遗留项）。
- 批次表（勾选为实施时使用）：

| 批次 | 范围 | 依赖 | 状态 |
| --- | --- | --- | --- |
| B0 | api 分域 + http 收敛 + 竞态 composable + store + ESLint + todoGroups 下沉 | — | **已完成**（commit `638fccb`；门禁 verify:mock 878/878、verify:mobile 48/0、test 209/0、lint 0 error、build+build:prod EXIT=0、e2e 37/0） |
| B1 | 考勤域（含打卡页 972 行拆分） | B0 | **已完成**（B1a `9d36310` 共用组件与 Token 基座；B1b `d47cadc` 打卡页 972→148 壳 + 6 组件 + 2 composable + 1 model、`useCheckIn` 销项双实现、`stores/attendance` 收口、另 3 页复用收口；门禁 lint 0 error、verify:mock 887/0、verify:mobile 48/0、test 52 文件 427 用例、verify:tokens EXIT=0、build+build:prod EXIT=0、e2e 38 passed/0 failed） |
| B2 | 工单域 | B0 | 待办 |
| B3 | 包裹域 | B0 | 待办 |
| B4 | 我的域（含 kpi 跨端 view prop） | B0 | 待办 |
| B5 | 请假域 | B0 | 待办 |
| B6 | 首页与消息域 | B1–B5、B7 | 待办 |
| B7 | 同步域 | B0 | 待办 |
| B8 | 性能（Vant 样式裁剪 + 异步组件 + 长列表实测）+ 全门禁 + 视觉/无障碍走查 | 全部 | 待办 |

---

## 13. 与 UI/UX 规范（`demo-staff-ui-redesign.md`）的接口约定

### 13.1 职责边界与冲突裁决

| 维度 | 以谁为准 |
| --- | --- |
| 视觉基调、Token 取值、组件 Anatomy/props、四态表现、触控/对比度/读屏文案、断点与安全区、动效时长 | **UI 规范**（`demo-staff-ui-redesign.md`） |
| 目录落点、依赖方向、状态分层、竞态与错误处理机制、体积上限、ESLint 规则、批次与门禁、测试策略 | **本文档** |
| UI 规范的 9 个新增组件**放哪个目录** | 本文档 §13.2（组件契约本身仍以 UI 规范为准） |

### 13.2 UI 新增组件的目录裁决（对齐 UI 规范 §4.2）

| UI 组件 | 落点 | 理由 |
| --- | --- | --- |
| `Chip`（C1）/ `Badge`（C2）/ `MiniChip`（C3）/ `ListItemCard`（C4） | `src/mobile/components/` | 移动端跨域原子/分子，管理端亦可用（`ListItemCard` 是 `mobile.scss` 全局类的组件化） |
| `ShiftCard`（C9） | `src/mobile/views/staff/components/` | 员工端考勤域与排班页共用的**域共享**组件，不下沉到跨域层（管理端不消费） |
| `ClockHero`（C5）/ `CheckSlotRow`（C6）/ `CheckResultPanel`（C7）/ `VerifyCard`（C8） | `src/mobile/views/staff/attendance/components/` | 打卡页专用；`PeriodCard` / `MakeupPopup`（UI 规范 §5.2.1 新增）同目录 |
| `FilterChips` / `PageState` / `TabbarLayout` 等既有组件改造 | 原位（`mobile/components/`、`mobile/layout/`） | 不搬迁，避免影响管理端 |

### 13.3 必须一致的三条

1. **状态组件唯一**：只允许 `PageState`（UI 规范 §0.1）；`PageState` 新增 `variant="denied"` 后，`flow.vue` 手写降级块必须删除（消 G13）。
2. **Token 落层一致**：UI 规范新增 9 条 Token 全部落 `mobile/styles/tokens.scss`，**不进 `shared/styles/tokens.base.scss` 真源**（UI 规范 附录 A 结论一致）。
3. **`--touch-min` / `--row-h-*` 是唯一来源**：组件与页面不得再出现 44/48/64/76/88 字面量（消 G6），骨架高度一律由 `--row-h-*` 派生。

### 13.4 UI 规范中登记为本轮「不做」的项（架构侧同意，登记 `TODO(扩展)`）

- `BottomSheetForm` 统一弹层骨架（UI 规范 §5.2.1-4）：本轮不做，登记；**但补卡弹层 / 日志弹层 / 备注弹层三者样式必须保持一致**。
- 长列表虚拟化（U5）：不做。
- per-Tab 滚动位置记忆（`TabbarLayout.vue:50`）：不做。

---

## 14. 未验证项与风险登记

| # | 项 | 类型 | 处理 |
| --- | --- | --- | --- |
| R1 | Vant 样式裁剪的实际 gzip 收益（估算 3–8 KB） | **已定（主智能体）**：留到 B8 实测后定 | 收益 <3 KB 时不强行凑数，如实登记未达成 |
| R2 | 首屏 gzip 目标 ≤150 KB 是否足够激进 | **已定（主智能体）**：保持 ≤150 KB | 不为此动 `clientLog` 静态依赖（违反 `mobile/main.js:34-46` 硬约束）；超目标则如实登记并给出原因 |
| R3 | `views/staff/components/` 与 `mobile/components/` 的边界在实现中可能反复 | 风险 | 由 review 把关；判定标准：**管理端是否消费** + **是否 ≥2 个员工端域消费** |
| R4 | 竞态守卫与 `van-list` 单飞叠加的乱序语义 | 待实测 | 单测必须覆盖「过期响应不得关闭新 `loading`」；e2e 不强依赖时序 |
| R5 | 数据级权限收口（`applyDataScope` + engine 改造）会触及 878 断言 | **已定（用户拍板 2026-09-22）：完整收口** | 纯函数 `applyDataScope` + engine 在调 handler 前统一执行 + 删除各 handler 重复的 `stationId` 覆盖；按 §5.5 流程同步维护断言（**只增不减**）；独立成子任务与独立 commit，便于单独回退 |
| R6 | 删除 `api/index.js` barrel 后，38 处 import 需一次性改完 | 风险（可控） | B0 一次性完成；`grep -rn "api/index.js" src/mobile` 作为验收 |
| R7 | **不保留** `api/index.js` 是否影响未来「移动端/PC api 合并」 | 存疑 | 同名同域后合并成本主要在「入参/响应差异」，与是否留 barrel 无关；本轮按 PC 范式执行 |
| R8 | 壳未编译：`--status-bar-height`、键盘弹起、返回键行为 | 待真机（沿用 UI 规范 U1/U3/U6） | 不在本文档下结论 |
| R9 | `/boss/kpi/:employeeId` 复用 `views/staff/me/kpi.vue` 的跨端耦合度 | **已定（用户拍板 2026-09-22）：保留复用 + `view` prop** | 不迁 `views/shared/`（会牵动管理端路由与 e2e）；两端渲染差异只由 `view` 驱动 |
| R10 | Mock 扩展后 `verify-mock.mjs` 新增断言计数 | 待登记 | 每次扩展在本文件 §5.5 同步登记「新增 N 项」，便于核对「只增不减」 |
| R11 | `constants/todoGroups.js` 的 `load()` 迁入 store 后，管理端 `BOSS_TODO_GROUPS` 同受影响 | 风险 | 两表一并迁移（同一文件），管理端回归靠 `verify:mock` + `e2e` 覆盖 |
| R12 | 移动端 25 组件中 `StationPicker` / `LineChart` 员工端不消费 | 记录 | 不删（管理端消费），不迁（跨端组件层） |

---

## 附录 A：本文档与既有规范的引用关系

| 文档 | 引用点 |
| --- | --- |
| `demo-pc-refactor.md` | §2 目录约定、§3 组件契约、§4 三层判据、§5 竞态守卫、§6 行为红线（对偶沿用） |
| `demo-staff-ui-redesign.md` | 组件清单与目录裁决（§13）、Token 落层、四态/触控/无障碍基线、U1–U6 |
| `demo-design.md` | §4 总体分层、§14 ADR A2/A4/A5/A6/A11/A12 |
| `demo-mobile-nav-redesign.md` | 三 Tab 职责、入口迁移映射（页面归属依据） |
| `demo-leave-design.md` | §2 权限矩阵与双保险、派生标志口径 |
| `api.md` / `demo-design.md` 7.4 | 接口契约与错误码分段 |
| `SESSION-STATE.md` | 门禁基线（878 / 48 / 150 / 37） |

> 本文档为架构规范产出，未写任何业务实现代码，未修改 `hrm-admin` / `hrm-server` 任何文件。所有「待实测」「待真机」「待拍板」项见 §7.3 与 §14。
