# 请假申请模块（M11）设计规范

> 产出角色：UI/UX 设计师 ｜ 状态：待主智能体 Review ｜ 落地方：前端工程师
> 关联冻结决策：D1 两级审批 / D2 撤销双向 / D3 打通考勤算薪 / D4 假别固定枚举 / D5 扣款全局单开关 / D6 运行日志上报
> **路径约定：本文档所有 `src/...` 均相对 `hrm-dev/hrm-demo/`；所有 `docs/...` 均相对 `hrm-dev/docs/`。**
> **行号约定：全部为撰写时实测行号；标注「未验证」的项必须落地前复核。**

---

## 目录

- [第 0 章 · 设计方向与设计判断摘要](#第-0-章--设计方向与设计判断摘要)
- [第 1 章 · 状态机与流转矩阵](#第-1-章--状态机与流转矩阵)
- [第 2 章 · 权限矩阵](#第-2-章--权限矩阵)
- [第 3 章 · 页面与信息架构（三端全清单）](#第-3-章--页面与信息架构三端全清单)
- [第 4 章 · 请假申请表单设计](#第-4-章--请假申请表单设计)
- [第 5 章 · 组件与 Token 增量](#第-5-章--组件与-token-增量)
- [第 6 章 · 通知文案模板](#第-6-章--通知文案模板)
- [第 7 章 · 运行日志上报页（D6）](#第-7-章--运行日志上报页d6)
- [第 8 章 · 状态与空/错/边界态清单](#第-8-章--状态与空错边界态清单)
- [第 9 章 · 验收标准](#第-9-章--验收标准)
- [第 10 章 · 不建议做的事（防过度设计）](#第-10-章--不建议做的事防过度设计)
- [附录 A · 接口契约草案](#附录-a--接口契约草案)
- [附录 B · 错误码增量（96xx）](#附录-b--错误码增量96xx)
- [附录 C · 需主智能体决策的开放问题](#附录-c--需主智能体决策的开放问题)

---

## 第 0 章 · 设计方向与设计判断摘要

### 0.1 设计方向

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 风格 | **沿用既有企业级蓝色管理风**，不引入新视觉语言 | 项目已有完整 Token 体系（`src/shared/styles/tokens.base.scss` 143 条），请假是业务模块而非品牌页，另起风格会破坏三端一致性 |
| 主色使用 | 承白字实底走 `--color-primary`（700 档 `#0958D9`，白字 6.16:1）；图标/细线/描边走 `--color-primary-icon`（500 档 `#1890FF`，白底 3.24:1） | `tokens.base.scss:84-90` 的项目硬规则 |
| 复用优先 | **不新建跨端组件、不新增颜色 Token**，全部复用既有 `StatusTag` / `PageState` / `StateBlock` / `RejectDialog` / `ActionBar` / `MeSection` | 见第 5 章 |
| 反 AI 默认风格 | 无紫色、无 Inter 字体、无超大圆角；圆角一律走 `--r-sm/--r-md/--r-lg/--r-full`（`tokens.base.scss:222-226`） | 项目规则 |

### 0.2 设计判断摘要（六条）

1. **请假状态定 6 态**，不拆「初审驳回/终审驳回」为两个状态（用 `rejectStage` 字段区分），理由见 §1.1。
2. **站长初审入口放 `/staff/leave/review`（员工端域内）**，理由见 §3.3。
3. **时长双口径**：自然天数（日历）+ 计薪天数（按排班矩阵逐日查，自动排除轮休日），两者**同时展示**，理由与算法见 §4.3。
4. **撤回必须回滚考勤标记 + 算薪口径**，且被「账期工资单已生成」硬阻断（9606），理由见 §1.3。
5. **跨月/跨年请假单必须按账期切分**计入对应月份，理由见 §8.4。
6. **计薪天数在终审通过时快照落库**（`countedDaysSnapshot`），避免排班事后变更导致工资单与申请单口径漂移，理由见 §8.5。

---

## 第 1 章 · 状态机与流转矩阵

### 1.1 状态枚举（最终 6 态）

字典键**统一落 `src/shared/constants/dict.js`（三端唯一真源）**，不重复补卡那样「PC 与移动各留一份」（补卡踩坑记录：`src/mobile/constants/makeup.js:3-6` 与 `src/pc/views/attendance/components/MakeupApproval.vue:24-31` 各写一份）。

| 状态键 | 中文名 | Token 色族 | `variant` | 语义 | 是否终态 |
| --- | --- | --- | --- | --- | --- |
| `PENDING_STATION` | 待站长初审 | `--color-warning` / `--color-warning-surface` | `soft` | 员工已提交，等本站站长初审 | 否 |
| `PENDING_BOSS` | 待老板终审 | `--color-primary` / `--color-primary-surface` | `soft` | 站长已初审通过（或站长的单跳过初审直入） | 否 |
| `APPROVED` | 已通过 | `--color-success` / `--color-success-surface` | `soft` | 终审通过，已写考勤标记、已生效 | 是（可被撤回） |
| `REJECTED` | 已驳回 | `--color-danger` / `--color-danger-surface` | `soft` | 初审或终审驳回，靠 `rejectStage` 区分 | 是（可修改重提） |
| `CANCELLED` | 已撤销 | `--state-outline-*`（`--state-outline-border` 描边 + `--text-3` 类文字） | `outline` | 申请人自行撤销，从未写考勤标记 | 是 |
| `REVOKED` | 已撤回 | `--color-warning` / `--state-outline-border` | `outline` | 审批人撤回已通过的单，考勤标记已回滚 | 是 |

**变体语义对齐既有约定**（`dict.js:8-11`）：`soft` = 状态描述；`outline` = 终态无动作诉求；`solid` = 需立刻行动。请假**不使用 `solid`**——请假没有「超时未处理即事故」的紧迫语义（不像工单高优先级），全部终态用 `outline` 与待审态拉开层次。

> `StatusTag` 无需改配色表：字典项自带 `type` + `variant` 即可命中 `TYPE_FALLBACK`（`src/mobile/components/StatusTag.vue:101-107`；`:118` 优先级为「显式传入 > 字典 variant > type 兜底」），故**不需要往 `DICT_COLORS` 里登记**（`StatusTag.vue:32-98`）。
> **落地核对项**：`src/pc/components/StatusTag.vue` 的同名逻辑本文档未逐行核对，前端需确认两端回落规则一致（本文件只核对了移动端）。

### 1.2 「驳回」为何不拆两个状态

**结论：单 `REJECTED` + `rejectStage: 'STATION' | 'BOSS'` 字段。**

- 拆状态的代价：筛选字典要出现「初审驳回」「终审驳回」两个等价值项，员工在列表上要理解两级差异才能筛选；服务端算薪/统计也要两处判等。
- 保留字段的收益：展示层用「已驳回」+ 副信息「初审驳回 · 王站长」一句话讲清；重提逻辑对两级完全一致（都允许修改重提）。
- **两级驳回对申请人的差异只有两点**，均通过副信息表达，不升级为状态：
  1. **文案**：初审驳回 = 「站长未通过，可修改后重新提交」；终审驳回 = 「老板未通过，可修改后重新提交」。
  2. **是否需要再走初审**：两者重提**都必须回 `PENDING_STATION`**（终审驳回后重提若不重走初审，站长就失去了对本站数据的把关，D1 的两级设计被绕过）。

### 1.3 状态流转矩阵

| # | 动作 | 触发角色 | 前置状态 | 前置条件 | 结果状态 | 副作用 |
| --- | --- | --- | --- | --- | --- | --- |
| T1 | 提交 `SUBMIT` | 申请人本人（`STAFF` / `STATION_ADMIN`） | —（新建） | `startDate ≥ 今天`；与本人占用单无半日区间重叠；假别/事由合法 | `STAFF` → `PENDING_STATION`；`STATION_ADMIN` → `PENDING_BOSS`（**D1 跳过初审**） | 写单 + 留痕 `SUBMIT`；**发通知**（见 §6） |
| T2 | 初审通过 `STATION_APPROVE` | 本站 `STATION_ADMIN` | `PENDING_STATION` | 审批人 `station_id` === 单 `stationId`；审批人 ≠ 申请人 | `PENDING_BOSS` | 留痕；通知老板 |
| T3 | 初审驳回 `STATION_REJECT` | 本站 `STATION_ADMIN` | `PENDING_STATION` | 同上；**驳回原因必填 2–100 字** | `REJECTED`（`rejectStage='STATION'`） | 留痕（含原因）；通知申请人 |
| T4 | 终审通过 `FINAL_APPROVE` | `ADMIN` | `PENDING_BOSS` | 审批人 ≠ 申请人 | `APPROVED` | **写 `LEAVE` 考勤标记**（= 该单计薪天数并入「已批请假天数」）；**落 `countedDaysSnapshot`**；留痕；通知申请人 |
| T5 | 终审驳回 `FINAL_REJECT` | `ADMIN` | `PENDING_BOSS` | **驳回原因必填 2–100 字** | `REJECTED`（`rejectStage='BOSS'`） | 留痕（含原因）；通知申请人 |
| T6 | 撤销 `CANCEL` | **申请人本人** | `PENDING_STATION` / `PENDING_BOSS` | — | `CANCELLED` | **无考勤副作用**（从未写标记）；留痕；通知「当前待审的那一级审批人」 |
| T7 | 撤回 `REVOKE` | **`ADMIN`（终审人）** | `APPROVED` | 该单计薪天数所在**每一个**账期均无可计薪工资单（或工资单仍为 `DRAFT`/`REJECTED`） | `REVOKED` | **回滚考勤标记**（该单产生的已批请假天数全部失效）；留痕；通知申请人 |
| T8 | 编辑 `UPDATE` | 申请人本人 | `PENDING_STATION` | 字段合法；重叠校验排除本单 | `PENDING_STATION`（不变） | 留痕 `UPDATE`（记前后值）；**重算** `naturalDays`/`countedDays` |
| T9 | 修改重提 `RESUBMIT` | 申请人本人 | `REJECTED` | 字段合法 | **生成新单**（新 `id`）→ 按申请人角色回 `PENDING_STATION` / `PENDING_BOSS` | 原单保持 `REJECTED` 只读；新单带 `originId`；留痕 |

**非法流转统一返回 9602**（状态不允许该操作），文案「该申请当前状态不支持此操作，请刷新后查看最新状态」（对齐补卡 9109 的处置话术：`src/mobile/views/boss/makeupApproval.vue:129-135`）。

### 1.4 T7 撤回：考勤标记与扣款口径的回滚要求（**硬要求**）

D3 规定「批准后写 `LEAVE` 考勤标记，缺勤口径改为 `排班天数 − 出勤天数 − 已批请假天数`」。因此 `APPROVED` 是**有账务副作用的终态**，撤回必须完整回滚：

1. **标记失效**：撤回后该单不再参与 `approvedLeaveDays(employeeId, range)` 的任何计算（见 §4.5 的落地形态），等价于「删标记」。
2. **口径回滚**：该员工该账期的 `absentCount` 恢复为 `max(0, scheduledDays − attendedDays)`，全勤奖、缺勤扣款随之回到未请假状态。
3. **账期锁**：撤回前必须逐个检查该单覆盖到的账期（跨月单可能涉及 2 个月），只要任一月已存在 `PENDING_APPROVAL / APPROVED / PUBLISHED / CONFIRMED` 的工资单，**拒绝撤回并返回 9606**，文案：「{月份} 工资单已{dicLabel(PAYROLL_STATUS)}，撤回会导致工资数据不一致。请先在财务管理中作废该单据。」（工资单状态字典见 `dict.js:137-144`）
4. **撤回原因必填**（2–100 字），写入留痕与通知正文——撤回等于撤销一次已生效的公司决定，必须留因。
5. **撤回权归 `ADMIN`**：终审通过的决定由老板做出，撤销该决定也应由老板做出，符合最小权限。**（开放问题 Q1：是否给站长同等的撤回权，见附录 C）**

---

## 第 2 章 · 权限矩阵

### 2.1 三端 × 三角色逐格矩阵

| 能力 | ADMIN（老板） | STATION_ADMIN（站长） | STAFF（员工） |
| --- | --- | --- | --- |
| **看**：PC 请假管理页 | 全域全驿站 | **仅本站**（`station_id` 服务端强制覆盖） | 不可见（PC 无请假入口） |
| **看**：移动请假列表 | 不可用（ADMIN 无请假业务） | 本站全量（初审视角） | 仅本人 |
| **做**：提交申请 | ❌ 9605（老板无上级可审，见 §2.3） | ✅ | ✅ |
| **做**：编辑（`PENDING_STATION`） | — | ✅ 自己的单 | ✅ 自己的单 |
| **做**：撤销（`PENDING_*`） | — | ✅ 自己的单 | ✅ 自己的单 |
| **做**：初审通过/驳回 | ❌（初审是站长职责） | ✅ **仅本站**、**不能审自己** | ❌ |
| **做**：终审通过/驳回 | ✅ 全部（含站长的单） | ❌ | ❌ |
| **做**：撤回已通过单 | ✅ 全部 | ❌（开放问题 Q1） | ❌ |
| **做**：修改请假扣款开关 | ✅ | ❌ 读都不可见 | ❌ 读都不可见 |
| **看**：运行日志查看页 | ✅ 仅 PC | ❌ | ❌ |
| **看不到什么** | 无（全量） | 其他驿站员工姓名/单/工号；扣款开关；运行日志 | 他人任何请假数据（含本站同事）；扣款开关 |

**关键约束（必须由服务端强制，不依赖前端）**：

- 站长**只能审本站员工**的单：`leave.stationId === user.station_id`，否则 9605。
- 站长**不能审自己**的单：`leave.employeeId === user.id` → 9605。**实际上站长的单因 D1 直接进 `PENDING_BOSS`，本站不会出现「站长待初审自己的单」**，该拦截是纵深防御（防止构造请求或未来流程变更）。
- 老板**能审所有**（含站长的单）：终审端点 `roles: ['ADMIN']`（对齐补卡审批 `src/shared/mock/routes/attendance.js:561` 的写法）。

### 2.2 双保险清单（渲染层隐藏 + 服务端兜底）

**A. 渲染层就该不显示的入口**

| 位置 | 规则 | 依据 |
| --- | --- | --- |
| 老板端宫格「请假审批」 | 仅 `isAdmin` 渲染 | `src/mobile/views/boss/home.vue` 宫格按角色注入（现有模式） |
| 员工端宫格「请假申请」 | `STATION_ADMIN` / `STAFF` 均显示 | `STAFF_QUICK_ENTRIES`（`quickEntries.js:27-36`） |
| 员工端宫格「请假初审」 | **仅 `role === 'STATION_ADMIN'`** | 需给宫格项新增 `roles` 过滤能力（现有项无此字段） |
| 消息 Tab 待办分组「待初审请假」 | 仅 `STATION_ADMIN` | 需给 `STAFF_TODO_GROUPS` 新增 `roles` 字段支持 |
| 「我的」页 cell | 老板看「请假扣款设置」；员工/站长看「我的请假」；站长额外看「请假初审」 | `src/mobile/components/MeSection.vue:48-66` 分区渲染 |
| PC 侧边栏「请假管理」 | `MENU_WHITELIST` 白名单驱动 | `src/shared/constants/role.js:27-31` |
| PC 侧边栏「运行日志」 | 白名单仅 `ADMIN` | 同上 |
| 扣款设置卡片 | PC `/leave` 页内 `v-if="isAdmin"` | 移动 `/boss/leave/settings` 本身就是 ADMIN-only 路由 |
| 行内「通过/驳回」按钮 | 按 `status` + 角色决定渲染 | 对照 `makeupApproval.vue:195-200` 的 `v-if="item.status === 'PENDING'"` 模式 |

**B. 必须靠服务端 403 / 业务码兜底**

| 兜底点 | 实现位置 | 返回 |
| --- | --- | --- |
| 端点角色白名单 | Mock engine 统一判定：`if (matched.roles && !matched.roles.includes(employee.role))` → `CODE.FORBIDDEN` | 403 |
| 数据范围（驿站）| 参照 `src/shared/mock/routes/parcel.js:13-17` 的「非 ADMIN 强制覆盖」写法，请假用同一模式 | 静默收敛，不报错 |
| 自审拦截 | `leave.employeeId === user.id` | 9605 |
| 跨站越权 | 站长访问非本站单（含直接构造 `GET /leave/:id`） | 9605（详情） / 404 风格「不存在」 |
| 状态机非法流转 | 见 §1.3 | 9602 |
| ADMIN 提交申请 | `POST /leave` | 9605 |

### 2.3 ADMIN 为何不能提交请假

老板无上级可审，若允许提交则只能自审自批，会污染审批数据与审计链（D6 要求留痕可审计）。因此：**服务端 `POST /leave` 对 `ADMIN` 返回 9605**，文案「超级管理员无需提交请假申请」；渲染层所有请假入口对 ADMIN 不显示。演示态下用 `admin` 账号体验请假时，应切换到 `st001_admin`（`src/demo/accounts.js:10`）或 `st001_staff`（`:11`）。

---

## 第 3 章 · 页面与信息架构（三端全清单）

### 3.1 页面清单总表

| # | 端 | 路由 | 可见角色 | 页面职责 | 关键区块 | 主/次操作 |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | 移动·员工端 | `/staff/leave/apply` | `STATION_ADMIN`, `STAFF` | 新建请假申请 | 表单（假别/时段/事由）+ 天数摘要 + 底部固定操作栏 | 主：提交申请；次：取消返回 |
| P2 | 移动·员工端 | `/staff/leave` | `STATION_ADMIN`, `STAFF` | 我的请假列表 + 状态跟踪 + 撤销/编辑/重提 | 状态 chip 筛选 + 列表行 + 详情弹层（含审批链与操作留痕时间线） | 主：申请请假（空态）；行内：撤销 / 编辑 / 修改重提 |
| P3 | 移动·员工端 | `/staff/leave/review` | **`STATION_ADMIN`** | 本站请假初审（列表 + 通过/驳回） | 状态 chip（默认 `PENDING_STATION`）+ 计数条 + 列表行 + 审批底部弹层 | 行内：通过 / 驳回 |
| P4 | 移动·老板端 | `/boss/leave` | `ADMIN` | 终审（列表 + 通过/驳回 + 撤回） | 状态 chip（默认 `PENDING_BOSS`）+ 列表行 + 审批弹层 + 撤回弹层 | 行内：通过 / 驳回 / 撤回 |
| P5 | 移动·老板端 | `/boss/leave/settings` | `ADMIN` | 请假扣款设置（全局单开关） | 开关卡片 + 影响说明 + 底部保存栏 | 主：保存 |
| P6 | PC | `/leave` | `ADMIN`, `STATION_ADMIN` | 请假管理：审批 + 全量列表 + 筛选 + 扣款开关 | 顶部扣款设置卡片（仅 ADMIN）+ 待审计数 + 筛选栏 + 表格 + 审批弹窗 + 详情抽屉 | 行内：通过 / 驳回 / 撤回 / 详情 |
| P7 | PC | `/system/logs` | `ADMIN` | 运行日志查看 | 统计条 + 筛选栏 + 表格 + 详情抽屉 + 导出 | 主：刷新 / 导出；次：清空 |

**不新增移动端详情路由**：请假详情用底部弹层（`van-popup position="bottom"`）承载，与补卡审批的弹层形态一致（`src/mobile/views/boss/makeupApproval.vue:208-233`）。理由：移动端窄屏下详情字段 ≤10 行 + 时间线，弹层足够；新增路由会增加返回栈与通知深链的维护成本。

### 3.2 PC 路由与菜单

**路由表新增**（`src/pc/router/index.js`，挂在 `../layout/index.vue` 的 children 下）：

```js
{
  path: 'leave',
  name: 'Leave',
  component: () => import('../views/leave/index.vue'),
  meta: { title: '请假管理', icon: 'Notes', group: 'pay', roles: ['ADMIN', 'STATION_ADMIN'] }
},
{
  path: 'system/logs',
  name: 'SystemLogs',
  component: () => import('../views/system/logs.vue'),
  meta: { title: '运行日志', icon: 'Document', group: 'sys', roles: ['ADMIN'] }
}
```

- `roles` 写法对齐既有 `STATION_ROLES = ['ADMIN', 'STATION_ADMIN']`（`src/pc/router/index.js:19`）与 `roles: STATION_ROLES`（`:104`）。
- **落地核对项（未验证）**：菜单图标 `Notes` / `Document` 需在 `@element-plus/icons-vue` 中确认存在；若缺失，回退到项目已核实的 `Tickets`（`src/pc/config/menu.js:59` 已在用）。

**菜单真源**（`src/shared/constants/role.js:27-31`，**唯一真源，不用 `EXTRA_MENU_KEYS` 旁路**）：

```js
export const MENU_WHITELIST = {
  ADMIN: ['dashboard','employee','department','station','parcel','sync','workOrder','notification','leave','logs','profile'],
  STATION_ADMIN: ['parcel','sync','workOrder','notification','leave','profile'],
  STAFF: ['profile']
}
```

> 为什么不用 `src/pc/config/menu.js:38-41` 的 `EXTRA_MENU_KEYS`：那是上一轮「shared 层冻结」的临时旁路，其注释明确写了「解冻后并入 `MENU_WHITELIST`」（`menu.js:36`）。新增模块应直接进真源，避免权限口径分裂（项目规则第 6 条）。

**`MENU_ITEMS` 新增两项**（`src/pc/config/menu.js:43-63`，注意必须**连续排列在同组内**，否则 `buildMenus` 分组会错位，见 `menu.js:10-11` 注释）：

```js
{ key: 'leave', path: '/leave', title: '请假管理', icon: 'Notes', group: 'pay' },
// …（sys 组内，放在 profile 之前）
{ key: 'logs', path: '/system/logs', title: '运行日志', icon: 'Document', group: 'sys' },
```

### 3.3 站长初审入口放哪：`/staff/leave/review`（**关键判断**）

**结论：站长初审页放员工端域内 `/staff/leave/review`，不放 `/boss/*`。**

理由（四条硬依据）：

1. **`/boss/*` 全部是 `roles:[ADMIN]`**：`src/mobile/router/index.js:41-150` 逐条核对，`/boss/home`(:41)、`/boss/workorder`(:85)、`/boss/attendance/makeup`(:91)、`/boss/kpi`(:106)… 无一例外。站长进 `/boss/*` 会被守卫按 `meta.roles` 弹回（`router/index.js:4` 的 `canAccess`）。
2. **站长移动端已被定位在员工端**：`HOME_BY_ROLE.STATION_ADMIN = '/staff/home'`（`src/mobile/constants/accounts.js:11`），且与 STAFF **完全共用**宫格（`quickEntries.js:27-36`）、待办（`todoGroups.js:113-163`）、Tabbar（`tabs.js:15-19`，实际由 `TabbarLayout.vue:29` 的 `auth.isAdmin ? BOSS_TABS : STAFF_TABS` 决定 → 站长走 `STAFF_TABS`）。
3. **改造 `/boss/*` 的代价远大于收益**：需逐条改 ~110 行 route meta，并调整 Tabbar 分流逻辑（现在只按 `isAdmin` 二分，一旦 `/boss/*` 对站长部分开放，分流要变成三分支）；而项目对站长 vs 员工的差异目前**刻意只保留 2 处**（`mobile/router/index.js:232` 的 `/staff/sync` 与 `stores/auth.js:28` 的 `canSeeSync`），保持这个「差异最小化」约定比新增一整套老板端路由更稳。
4. **一致性**：PC 端站长已有 `/attendance`、`/schedule` 等域内页面（`src/pc/router/index.js:100-111`），移动端沿用「站长在员工端域内加站长专属页」的同一模式，三端心智一致。

**因此需要给两个配置文件增加「按角色过滤」能力**（这是本模块唯一的机制性改动）：

```js
// quickEntries.js 新增项（字段 roles 为新增支持）
{ key: 'leaveReview', text: '请假初审', icon: 'notes-o', to: '/staff/leave/review', type: 'count', roles: ['STATION_ADMIN'] }

// todoGroups.js（STAFF_TODO_GROUPS 内）新增项
{ key: 'leaveReview', title: '待初审请假', to: '/staff/leave/review', roles: ['STATION_ADMIN'], async load() { … } }
```

渲染侧按 `roles.includes(auth.role)` 过滤，未声明 `roles` 视为全角色可见（向后兼容既有 8/3 个项）。

> 图标名 `notes-o` **已核实存在**于 `node_modules/vant/es/icon/index.css`（vant@4.10.2）。同批已核实可用：`edit` / `passed` / `description` / `certificate`。

### 3.4 入口分配表

**A. 首页宫格**

| 端 | 文件 | 新增项 | 形态 | 说明 |
| --- | --- | --- | --- | --- |
| 老板端 | `src/mobile/constants/quickEntries.js:16-25` | `{ key:'leaves', text:'请假审批', icon:'notes-o', to:'/boss/leave', type:'count' }` | `count` | 插在「入离职审批」之后；计数 = `PENDING_BOSS` 的 `total` |
| 员工端 | `src/mobile/constants/quickEntries.js:27-36` | `{ key:'leave', text:'请假', icon:'notes-o', to:'/staff/leave/apply', type:'plain' }` | `plain` | 纯入口（发起请假无实时数据可显示）；排在「我的补卡申请」之后 |
| 员工端（站长） | 同上 | `{ key:'leaveReview', text:'请假初审', icon:'notes-o', to:'/staff/leave/review', type:'count', roles:['STATION_ADMIN'] }` | `count` | 站长专属，计数 = `PENDING_STATION` 的 `total` |

- `type` 三选一语义见 `quickEntries.js:8-12`；排序按「待办优先」（`quickEntries.js:13`）→ `count` 型排在 `plain/status` 型之前，故站长端「请假初审」应排在 `count` 段末尾、`plain` 段之前。
- 计数注入方式沿用既有：`boss/home.vue` 与 `staff/home.vue` 按 `key` 注入实时值（现有 `makeups`/`orders` 的同套机制）。
- 员工端「请假」用 `plain` 而非 `count`：`count` 的语义是「待办队列角标」（`quickEntries.js:9`），发起申请不属于待办队列；「我的待办请假数」由「我的请假申请」待办分组承担（见 B）。

**B. 消息 Tab 待办分组**

| 分组 | 归属文件 | 分组名 | 计数口径 | 落点 |
| --- | --- | --- | --- | --- |
| 老板 | `src/mobile/constants/todoGroups.js:41-111`（`BOSS_TODO_GROUPS`） | **待终审请假** | `GET /leave/list?status=PENDING_BOSS` 的 `total` | `/boss/leave` |
| 员工/站长 | `src/mobile/constants/todoGroups.js:113-163`（`STAFF_TODO_GROUPS`） | **我的请假申请** | `GET /leave/my?status=PENDING` 的 `total`（`PENDING` 为服务端展开的聚合值，见附录 A） | `/staff/leave` |
| 员工/站长（站长专属） | 同上 | **待初审请假** | `GET /leave/list?status=PENDING_STATION` 的 `total` | `/staff/leave/review` |

- 行文案模板（沿 `makeupRow` 的写法，`todoGroups.js:35-39`）：
  - 老板：`title = \`${item.employeeName} ${LEAVE_TYPE[item.leaveType].label}\``，`meta = \`${item.startDate} ~ ${item.endDate} · ${item.countedDays} 天\``
  - 员工：`title = \`${item.startDate} ~ ${item.endDate} ${类型}\``，`meta = '审批中，通过后生效'`
  - 站长：`title = \`${item.employeeName} ${item.startDate} ~ ${item.endDate}\``，`meta = \`${item.leaveType.label} · 待初审\``
- **Tabbar 角标自动纳入**：`messageBadge = badgeText(notify.unread + todo.total)`（`src/mobile/layout/TabbarLayout.vue:31`），`todo.total` 由待办分组求和，新增分组后自动计入，无需改角标逻辑。

**C. 「我的」Tab**（`src/mobile/components/MeSection.vue`）

| 分区 | 现状行 | 新增 |
| --- | --- | --- |
| 老板区（`v-if="auth.isAdmin"`，`:49-55`） | KPI / 人事 / 排班 / 打卡规则 / 打卡记录 | `<van-cell title="请假扣款设置" label="全局单开关：请假是否影响工资" is-link to="/boss/leave/settings" />` |
| 员工区（`v-else`，`:56-66`） | 我的 KPI / 工资单 / 档案 / 排班 / 打卡记录 / 我的补卡申请 / 我的入离职 /（站长）同步状态 | `<van-cell title="我的请假" label="申请记录与审批进度" is-link to="/staff/leave" />`；<br>`<van-cell v-if="auth.role === 'STATION_ADMIN'" title="请假初审" label="本站员工请假待初审" is-link to="/staff/leave/review" />` |

> 「我的」页顶部注释明确写了「待办队列不在这里：统一收进『消息』Tab」（`MeSection.vue:44-45`），故以上 cell **不带计数**，只做低频兜底入口 —— 与既有「我的补卡申请」一致（`MeSection.vue:62-63`）。

**D. 是否需要新增 Tab**

**结论：不需要。** 理由：

1. Tabbar 固定 3 项「首页 / 消息 / 我的」（`src/mobile/constants/tabs.js:9-19`），是上一轮导航重构（`docs/demo-mobile-nav-redesign.md`）刻意收敛的结果，注释说明「一级页只留最常用的三个意图」（`tabs.js:4-5`）。
2. 请假是**低频业务**（人均月均数次），不符合「最常用意图」标准；而它已经拥有三条完整触点：宫格入口 + 消息待办 + 通知跳转，覆盖率足够。
3. 移动端真机 375px 下，Tabbar 4 项会使单项宽度从 ~125px 降到 ~94px，12px 文字 + 22px 图标仍可容纳，但**新增 Tab 会稀释三个高频入口的点击效率**，收益为负。

### 3.5 移动端路由表增量（`src/mobile/router/index.js`）

```js
// 员工端域（STAFF_ROLES = [ROLE.STATION_ADMIN, ROLE.STAFF]，router/index.js:9）
{ path: '/staff/leave/apply', name: 'staffLeaveApply',
  component: () => import('../views/staff/leaveApply.vue'),
  meta: { roles: STAFF_ROLES, title: '请假申请' } },

{ path: '/staff/leave', name: 'staffLeaveList',
  component: () => import('../views/staff/leaveList.vue'),
  meta: { roles: STAFF_ROLES, title: '我的请假' } },

// 站长专属（放在 /staff/sync 附近，与「STAFF 不可见」的既有写法一致，router/index.js:227-233）
{ path: '/staff/leave/review', name: 'staffLeaveReview',
  component: () => import('../views/staff/leaveReview.vue'),
  meta: { roles: [ROLE.STATION_ADMIN], title: '请假初审' } },

// 老板端域（roles 一律 [ROLE.ADMIN]）
{ path: '/boss/leave', name: 'bossLeave',
  component: () => import('../views/boss/leaveApproval.vue'),
  meta: { roles: [ROLE.ADMIN], title: '请假审批' } },
{ path: '/boss/leave/settings', name: 'bossLeaveSettings',
  component: () => import('../views/boss/leaveSettings.vue'),
  meta: { roles: [ROLE.ADMIN], title: '请假扣款设置' } },
```

**路由顺序注意**：`/staff/leave/apply` 与 `/staff/leave` 是静态路径，无 `:id` 参数，**不存在遮蔽问题**；但两者必须写在 `/staff/leave/review` 之前或之后均可（hash 模式下 router 按精确匹配）。为与既有「静态路径在前」的约定一致（`routes/attendance.js:544-547` 注释），建议按 `apply` → `review` → `leave` 顺序声明。

---

## 第 4 章 · 请假申请表单设计

### 4.1 字段清单

| # | 字段 | 类型 | 必填 | 校验规则 | 错误文案 | PC 控件 | 移动控件 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 请假类型 `leaveType` | 枚举 `LEAVE_TYPE` | ✅ | ∈ `LEAVE_TYPE` 键集合 | 「请选择请假类型」 | `el-select`（宽度 100%） | `van-field` readonly + `van-popup` + `van-picker` |
| 2 | 开始日期 `startDate` | `YYYY-MM-DD` | ✅ | 合法日期；**≥ 今天**；≤ `endDate` | 「开始日期不能早于今天」/「开始日期不能晚于结束日期」 | `el-date-picker` `value-format="YYYY-MM-DD"`，`disabled-date` 禁过去 | `van-calendar`，`min-date = today` |
| 3 | 开始半天 `startPeriod` | `AM` / `PM` | ✅ | ∈ `HALF_DAY`；默认 `AM` | 「请选择开始时段」 | `el-radio-group` | `van-radio-group`（direction=horizontal） |
| 4 | 结束日期 `endDate` | `YYYY-MM-DD` | ✅ | 合法日期；≥ `startDate` | 「结束日期不能早于开始日期」 | 同 #2，`min = startDate` | 同 #2 |
| 5 | 结束半天 `endPeriod` | `AM` / `PM` | ✅ | ∈ `HALF_DAY`；`endDate === startDate` 时 `endPeriod` 不得早于 `startPeriod` | 「同一天内，结束时段不能早于开始时段」 | `el-radio-group` | `van-radio-group` |
| 6 | 请假事由 `reason` | 文本 | ✅ | 2–200 字（与补卡 `reason` 同口径，`routes/attendance.js:484`） | 「请假事由须为 2-200 字」 | `el-input type="textarea"` `:rows="3"` `maxlength="200"` `show-word-limit` | `van-field type="textarea"` `rows="3"` `maxlength="200"` `show-word-limit` |
| 7 | 天数摘要 `summary` | 只读派生 | — | — | — | 文本（见 §4.3） | 文本（见 §4.3） |

**提交成功文案**：
- 员工提交 → Toast「已提交，等待站长初审」
- 站长提交 → Toast「已提交，等待老板终审」

### 4.2 半天粒度定义

新增字典 `HALF_DAY`（落 `dict.js`）：

```js
/** 半天粒度：请假的最小时段单位。上午 = 当日 00:00–12:00 归属段，下午 = 12:00–24:00 */
export const HALF_DAY = {
  AM: { label: '上午' },
  PM: { label: '下午' }
}
```

**区间展示格式**（统一，三端一致）：`{startDate} {HALF_DAY[startPeriod].label} ~ {endDate} {HALF_DAY[endPeriod].label}`，同日时省略结束日期：`2026-10-01 上午 ~ 下午`。

### 4.3 时长计算规则（**关键**）

#### 4.3.1 半天单元模型

把区间展开成「半天单元」数 `halfUnits`（每天最多 2 个）：

```
d = 日期差(endDate, startDate)  // 同天 = 0
若 d === 0:  halfUnits = (startPeriod === 'AM' && endPeriod === 'PM') ? 2 : 1
若 d >= 1:   halfUnits = (startPeriod === 'AM' ? 2 : 1)   // 起始日
                      + (d - 1) * 2                        // 中间日
                      + (endPeriod  === 'PM' ? 2 : 1)      // 结束日
naturalDays = halfUnits / 2
```

校验：同日 `AM→AM` = 0.5 天；同日 `AM→PM` = 1 天；`10-01 PM → 10-03 AM` = 2 天；`10-01 AM → 10-03 PM` = 3 天。
**同日 `PM→AM` 为非法组合**，由 §4.1 #5 的表单校验与 §4.4 的服务端校验双重拦截。

#### 4.3.2 计薪天数：**必须逐日查排班矩阵，禁止按 `REST_CYCLE_DAYS` 推算**

> ⚠️ 反幻觉要点：`REST_CYCLE_DAYS = 6`（`src/shared/mock/attendanceStore.js:46`）只是**种子数据的生成规则**（`:269-275`：`(dayIndex + employeeIndex) % REST_CYCLE_DAYS === REST_CYCLE_DAYS - 1` 时跳过排班），**它不是业务规则**。真实轮休由 `schedules`（`employeeId + workDate + shiftId`，`attendanceStore.js:1268-1280`）说了算。计薪天数**必须逐日查排班真源**，按「每 6 天休 1 天」去推算一定会与工资单对不上。

**算法（`countMode === 'SCHEDULED'`，默认）**：

```
countedHalfUnits = 0
for each day in [startDate, endDate]:
    units = 该日的半天单元数（起始日/结束日按 §4.3.1 取 1 或 2；中间日取 2）
    hasSchedule = schedules 中存在 { employeeId: 本人, workDate: day }
    if hasSchedule: countedHalfUnits += units
    else:           countedHalfUnits += 0        // 轮休日不计薪
countedDays = countedHalfUnits / 2
```

**排班粒度说明（必须写进实现注释）**：`schedules` 是**日粒度**（只有 `workDate`，没有上午/下午，见 `attendanceStore.js:1268-1280`），因此半天单元只能表达「当天请了半天」，不能映射到具体班次时段。若某天有两个班次，请一天假仍按 1 天计。

**算法（`countMode === 'NATURAL'`）**：`countedDays = naturalDays`（婚假/产假等按自然日连续计算）。

#### 4.3.3 是否同时展示两个天数

**结论：必须同时展示。** 理由：申请人既要知道「我请了几天」（自然天数），也要知道「工资会按几天算」（计薪天数）。二者不一致是**常态**（轮休日被排除），若不展示计薪天数，员工会在收到工资单时质疑「我请 3 天为什么只扣 0.5 天」——这正是 D3 要避免的口径纠纷。

**展示文案（统一三端）**：

```
合计 3 天（自然日） · 计薪 2 天
```

副说明（`--text-3` 12px，**仅在两值不等时出现**）：

- `countMode === 'SCHEDULED'` 且不相等：`已自动排除排班休息日，计薪天数以排班为准`
- `countMode === 'NATURAL'`：`本假别按自然日连续计算，与排班无关`
- 扣款开关为「扣工资」且终审通过后：追加 `该假别按公司规定计算工资`

**前端取值来源**：`POST /leave/preview`（无副作用试算端点，入参 `{ leaveType, startDate, startPeriod, endDate, endPeriod }`，返回 `{ naturalDays, countedDays }`）。
> 为什么不纯前端算：排班真源在服务端（`GET /schedules` 是按周查询且数据量大，见 `src/pc/utils/payrollPreview.js:71-80` 的逐周取数），前端算会把一次请求变成 N 次；且算薪口径必须单点，避免出现第二个实现（`payrollPreview.js:10-12` 的保真约定明确要求「任何一侧改了算薪口径，另一侧必须同步」，这里宁可少一个镜像）。

### 4.4 时间段重叠校验

**占用状态集合**：`PENDING_STATION`、`PENDING_BOSS`、`APPROVED`。
**不占用**：`REJECTED`、`CANCELLED`、`REVOKED`（终态，不占额度）。

**判定粒度：半天**（不是整天）。把本人所有占用单与本次申请都展开为半天单元序列，**交集非空即重叠**。由此得出两条必须写进文档的推论：

1. **同一天的上半天与下半天可以分别申请**（AM 单与 PM 单不重叠，视为合法）。这是设计意图：上午事假 + 下午病假是真实场景。
2. **同一半天的重复提交必被拦截**（含连点重复提交）。

**错误码 9603**，文案：

```
该时间段与已有申请重叠：
{LEAVE_TYPE[type].label} {startDate} {上午/下午} ~ {endDate} {上午/下午}（{LEAVE_STATUS[status].label}）
请调整时间，或先撤销原申请再提交。
```

**编辑（T8）例外**：`PUT /leave/:id` 的重叠校验必须**排除本单自身**，否则修改事由会被自己的原区间挡下。

### 4.5 未来/过去日期护栏（与补卡**方向相反**）

| 项 | 补卡（既有） | 请假（本模块） |
| --- | --- | --- |
| 日期方向 | **只能过去或当天**（`routes/attendance.js:481`：`workDate > 今天` → 报错「补卡日期不能晚于今天」） | **只能今天或未来** |
| 服务端护栏 | `workDate > formatDate(new Date())` → 400 | `startDate < 今天` → 400 / 9604 |
| 边界：能否请当天 | — | ✅ **允许**（急事当天请假是刚需） |
| 边界：能否补请过去 | — | ❌ **不允许** |

**过去日期错误文案**：

```
请假不能选择过去的日期。已发生缺勤的情况请联系站长线下确认处理。
```

**当天请假的时段提示（软提示，不阻断提交）**：当 `startDate === 今天` 且 `startPeriod === 'AM'` 且当前时间已晚于本日上午班次开始时间时，在表单下方给 `--color-warning` 提示：

```
今天上午已开始，如需请假请从下午开始，或与站长说明情况。
```

**为什么只软提示不硬拦**：急事请假时需要「提交」这个动作先能落地，硬拦会让员工被迫先沟通再回系统提交，反而延长了信息滞后的窗口。审批人看到「今天上午已过」的单子可自行判断。

**上限护栏**：单次连续跨度 **≤ 30 个自然日**（超出返回 9604，文案「单次请假最长 30 天，如需更长请分次申请或联系老板」）。**该数值为建议值，列为开放问题 Q2。**

### 4.6 修改规则（对应需求「申请提交与修改功能」）

| 状态 | 可否修改 | 可改字段 | 交互 | 留痕 |
| --- | --- | --- | --- | --- |
| `PENDING_STATION` | ✅ **就地修改** | 全部（类型/日期/半天/事由） | 详情弹层「编辑」→ 复用 P1 表单（预填现值）→ 保存后状态不变，`updateTime` 刷新 | `UPDATE`，记前后值 |
| `PENDING_BOSS` | ❌ | — | 「编辑」按钮置灰，`note` 说明：`已通过站长初审，如需变更请先撤销后重新申请` | — |
| `REJECTED` | ✅「修改并重新提交」 | 全部 | 详情弹层「修改并重新提交」→ 表单预填原值 → 提交后**生成新单**（新 `id`，带 `originId`）→ 原单保持 `REJECTED` 只读 | 新单 `SUBMIT` + 原单 `RESUBMIT` |
| `APPROVED` / `CANCELLED` / `REVOKED` | ❌ | — | 只读，无操作按钮 | — |

**为什么不覆盖原单**：D6 要求审计可追溯。覆盖会让「被驳回的原因」与「修改后的内容」混在同一条记录里，审计时无法还原当时的审批依据。生成新单 + `originId` 关联，既保留历史又建立了追溯链。

**`PENDING_BOSS` 禁止编辑的原因**：该单已带站长的初审意见通过，若允许改日期/事由，等于用初审的背书跑终审，两级审批的制衡被绕过。

**编辑后必须重算 `naturalDays` / `countedDays` 并重新做重叠校验。**

### 4.7 驳回原因必填（与补卡的**刻意差异**）

| 项 | 补卡（既有） | 请假（本模块） | 差异理由 |
| --- | --- | --- | --- |
| 审批意见长度 | `textLen(body.approveRemark, 0, 200)`，空值传 `null`（`routes/attendance.js:533-534`） | **通过时**：选填 `0–100` 字；**驳回时**：必填 `2–100` 字 | — |
| 驳回原因 | 选填，UI 占位「选填，例如：情况属实，予以补卡」（`makeupApproval.vue:223`） | **必填**，UI 占位「请填写驳回原因（2-100 字）」 | 请假被驳回后当天即为缺勤（可能直接扣款），必须给员工一个可追溯的理由；补卡驳回不改变工资口径，理由可选 |
| 组件 | 页面自绘 `van-field` / `el-input` | PC **直接复用 `src/pc/components/RejectDialog.vue`**（组件内已强制 2–100 字必填，`RejectDialog.vue:31`、`:73-81`）；移动端在审批弹层内把「审批意见」字段标 `*` 并禁用提交按钮 | 复用优先 |

**移动端参数化弹层设计**（沿补卡「同一弹层、仅默认结论与按钮文案不同」的成熟形态，`makeupApproval.vue:97-104`、`:208-233`）：

| 弹层标题 | 意见字段 | 占位文案 | 主按钮 | 主按钮 type |
| --- | --- | --- | --- | --- |
| 通过请假申请 | 选填 | `选填，例如：情况属实，准假` | 确认通过 | `primary` |
| 驳回请假申请 | **必填**（`*`） | `必填，请说明驳回原因（2-100 字）` | 确认驳回 | `danger`（`:disabled="remark.trim().length < 2"`） |
| 撤回已批准的请假 | **必填**（`*`） | `必填，请说明撤回原因（2-100 字）` | 确认撤回 | `danger` |

---

## 第 5 章 · 组件与 Token 增量

### 5.1 Token 增量：**0 条新增**（全部复用）

这是刻意的设计结论：请假模块所需的所有视觉值在既有 Token 体系中均已存在，新增 Token 只会制造第二份真源。

**复用映射表 + 对比度核算**：

| 用途 | 复用 Token | 取值 | 对比度（WCAG 2.x 相对亮度公式） | 结论 |
| --- | --- | --- | --- | --- |
| 状态标签 待初审 | `--color-warning` / `--color-warning-surface` | `#B45309` on `#FFFBE6` | **4.71:1**（项目既有登记值，`src/mobile/components/SlaTag.vue:86`） | AA 正文 ✅ |
| 状态标签 待终审 | `--color-primary` / `--color-primary-surface` | `#0958D9` on `#E8F4FF` | **5.52:1**（本文档实算） | ✅ |
| 状态标签 已通过 | `--color-success` / `--color-success-surface` | `#237804` on `#F6FFED` | **5.44:1**（本文档实算） | ✅ |
| 状态标签 已驳回 | `--color-danger` / `--color-danger-surface` | `#CF1322` on `#FFF1F0` | **5.07:1**（本文档实算） | ✅ |
| 状态标签 已撤销 | `--state-outline-border` + `--text-2` | `#CBD2DA` 边框 / `#4B5563` 文字 on `#FFFFFF` | 文字 **7.56:1**（本文档实算） | ✅ |
| 状态标签 已撤回 | `--color-warning` + `--state-outline-border` | 同「待初审」文字色，描边形态 | 4.71:1 | ✅ |
| 主按钮白字 | `--color-primary` | `#FFFFFF` on `#0958D9` | **6.16:1**（项目既有登记值，`tokens.base.scss:84`） | ✅ |
| 危险实底白字 | `--color-danger` | `#FFFFFF` on `#CF1322` | **5.57:1**（项目既有登记值，`SlaTag.vue:92`） | ✅ |
| 图标 / 细线 / 描边 | `--color-primary-icon` | `#1890FF` on `#FFFFFF` | **3.24:1**（本文档实算） | SC 1.4.11 非文本 3:1 ✅ |
| 辅助说明文字 | `--text-3` | `#6B7280` on `#FFFFFF` | **4.83:1**（本文档实算） | ✅ |
| 辅助说明文字（页面底） | `--text-3` | `#6B7280` on `#F5F7FA` | **4.50:1**（本文档实算） | ⚠️ 临界，仅限 ≥12px 且不做正文 |
| **禁用**：说明文字放浅灰块 | ~~`--text-3`~~ → 改 `--text-2` | `#6B7280` on `#EDF0F4` | **4.23:1**（本文档实算） | ❌ 不达 AA，**必须改 `--text-2`（6.61:1）** |
| 表单标签 / 次要文本 | `--text-2` | `#4B5563` on `#FFFFFF` | **7.56:1**（本文档实算） | ✅ |
| 间距 | `--sp-1…--sp-10` | 4/8 网格 | — | ✅ |
| 圆角 | `--r-sm` / `--r-md` / `--r-lg` / `--r-full` | 6/8/12/999px | — | ✅ |
| 动效 | `--dur-fast/base/slow` + `--ease-std` | 120/200/300ms | — | 并与 `prefers-reduced-motion` 降级共用（`tokens.base.scss:244-253`） |

> **上表中最后一条⚠️级的对比度发现是本文档的新增结论，请在前端落地时严格遵守**：任何放在 `--surface-subtle`(`#F5F7FA`) 或 `--state-neutral-bg`/`--color-info-surface`(`#EDF0F4`) 之上的 12px 说明文字，一律用 `--text-2`，**不得用 `--text-3`**。既有页面存在同类风险，本模块不扩大它。

**移动端/PC 隐私尺寸 Token（两端差异，合法，无需新增）**：`--tag-h`（PC 24px / 移动 20px，`pc/styles/tokens.scss:56`、`mobile/styles/tokens.scss:70`）、`--drawer-w` 520px / `--drawer-w-lg` 720px（`pc/styles/tokens.scss:49-51`）、`--actionbar-h` 56px / `--navbar-h` 44px / `--tabbar-h` 50px（`mobile/styles/tokens.scss:163-165`）。

### 5.2 组件增量

#### 5.2.1 复用（不新建）

| 组件 | 路径 | 在请假模块中的用途 |
| --- | --- | --- |
| `StatusTag` | `src/mobile/components/StatusTag.vue` / `src/pc/components/StatusTag.vue` | 请假状态胶囊（字典驱动，无需改配色表，见 §1.1） |
| `PageState` | `src/mobile/components/PageState.vue` | 移动端列表三态（含 200ms 骨架延迟阈值，`:22-39`） |
| `StateBlock` | `src/pc/components/StateBlock.vue` | PC 空/错误/无权限三态（`empty` / `error` / `denied`，`:10-31`） |
| `RejectDialog` | `src/pc/components/RejectDialog.vue` | PC 驳回弹窗（已内置「原因必填 2-100 字」） |
| `ActionBar` | `src/mobile/components/ActionBar.vue` | P1 申请页底部固定操作栏（`single/dual/multi` 三形态） |
| `PageNav` | `src/mobile/components/PageNav.vue` | 移动端四个新页的顶栏返回 |
| `PageHeader` | `src/pc/components/PageHeader.vue` | PC 两个新页的页头 |
| `MeSection` | `src/mobile/components/MeSection.vue` | 入口 cell 落点 |
| `MiniStats` | `src/pc/components/MiniStats.vue` | P7 运行日志统计条 |
| `SlaTag` | `src/mobile/components/SlaTag.vue` | **不使用**（请假无 SLA 语义，见 §10） |

#### 5.2.2 需要新增的组件（仅 2 个）

| # | 组件 | 端 | 职责 | 复用关系 | 与既有组件的差异 |
| --- | --- | --- | --- | --- | --- |
| C1 | `FilterChips.vue` | 移动端（分子） | 横向可换行状态筛选 chip 组：`props: items[{value,label}]`、`active`；`emit('change', value)`；每个 chip `min-height: 44px`、`aria-pressed` | **抽取自既有三处重复**：`src/mobile/views/staff/makeupList.vue:84-96` + `:129-155`、`src/mobile/views/boss/makeupApproval.vue:148-160` + `:237-261`；请假的三页成为**第三、四、五处消费方**，触发项目规则「同一逻辑不得重复实现三次以上」→ 必须抽取 | 无功能差异，纯抽公共；抽后补卡两页应一并替换（见 §9 静态核查项） |
| C2 | `DayPeriodRange.vue` | 移动端（分子） | 「开始日期 + 上午/下午 + 结束日期 + 上午/下午」四控件组 + 天数摘要只读行 | 仅 P1 申请页使用 | 一次性使用，**本可不抽**；但它是表单中最易出错的区块（半天边界）且未来「编辑」「重提」两个入口都要复用同一表单，故抽为组件，避免三处各写一遍校验 |
| C3 | `LeaveManagement.vue` | PC（view 内区块） | 审批表格 + 筛选 + 待审计数 + 审批弹窗 + 详情抽屉 | 结构镜像 `src/pc/views/attendance/components/MakeupApproval.vue` | 差异：新增「终审通过后的撤回」入口；筛选默认值按角色不同（站长 `PENDING_STATION`，老板 `PENDING_BOSS`）；无页头驿站选择联动（审批是全域待办语义，同 `MakeupApproval.vue:14-15` 的理由） |
| C4 | `ClientLogViewer.vue` | PC（view 内区块） | 运行日志列表 + 筛选 + 抽屉详情 + 导出 | 无直接对标 | 新增（见 §7） |

> C3/C4 按项目习惯放在 `src/pc/views/leave/` 与 `src/pc/views/system/` 下（view 级，不进 `src/pc/components/`，因为它们承载页面语义而非原子能力）。

#### 5.2.3 需要给既有配置组件加的能力

| 文件 | 改动 | 影响面 |
| --- | --- | --- |
| `src/mobile/constants/quickEntries.js` | 宫格项新增可选字段 `roles: string[]`；消费侧（`boss/home.vue`、`staff/home.vue`）按 `auth.role` 过滤 | 向后兼容（未声明 `roles` = 全角色可见），既有 8 + 8 项无需改动 |
| `src/mobile/constants/todoGroups.js` | 待办分组新增可选字段 `roles: string[]`；消费侧（`stores/todo.js`）同样过滤 | 同上，既有 5 + 3 组无需改动 |

### 5.3 字典增量（**一律进 `src/shared/constants/dict.js`**）

```js
/* ==================== 请假（M11） ==================== */

/** 请假状态：6 态。PENDING_STATION→PENDING_BOSS→APPROVED；REJECTED 用 rejectStage 区分两级 */
export const LEAVE_STATUS = {
  PENDING_STATION: { label: '待站长初审', type: 'warning', variant: 'soft' },
  PENDING_BOSS:    { label: '待老板终审', type: 'primary', variant: 'soft' },
  APPROVED:        { label: '已通过',     type: 'success', variant: 'soft' },
  REJECTED:        { label: '已驳回',     type: 'danger',  variant: 'soft' },
  CANCELLED:       { label: '已撤销',     type: 'info',    variant: 'outline' },
  REVOKED:         { label: '已撤回',     type: 'warning', variant: 'outline' }
}

/** 驳回阶段（仅 REJECTED 有值），用于列表副信息「初审驳回 / 终审驳回」 */
export const LEAVE_REJECT_STAGE = {
  STATION: { label: '初审驳回' },
  BOSS:    { label: '终审驳回' }
}

/**
 * 假别（D4：固定枚举，不做额度）。
 * countMode 决定计薪天数口径：SCHEDULED = 按排班逐日计（排除轮休日）；NATURAL = 按自然日连续计。
 */
export const LEAVE_TYPE = {
  ANNUAL:       { label: '年假',   countMode: 'SCHEDULED' },
  PERSONAL:     { label: '事假',   countMode: 'SCHEDULED' },
  SICK:         { label: '病假',   countMode: 'SCHEDULED' },
  COMPENSATORY: { label: '调休',   countMode: 'SCHEDULED' },
  MARRIAGE:     { label: '婚假',   countMode: 'NATURAL' },
  MATERNITY:    { label: '产假',   countMode: 'NATURAL' },
  PATERNITY:    { label: '陪产假', countMode: 'NATURAL' },
  BEREAVEMENT:  { label: '丧假',   countMode: 'NATURAL' },
  OTHER:        { label: '其他',   countMode: 'SCHEDULED' }
}

/** 半天粒度 */
export const HALF_DAY = { AM: { label: '上午' }, PM: { label: '下午' } }

/**
 * 筛选器：'PENDING' 是服务端展开的聚合虚拟值（= PENDING_STATION + PENDING_BOSS），
 * 前端只传 'PENDING'，避免三端各自做两次请求求和。
 * 顺序即处理动线：待办优先 → 结果 → 终态 → 全部。
 */
export const LEAVE_FILTERS = [
  { value: 'PENDING',         label: '审批中' },
  { value: 'PENDING_STATION', label: '待初审' },
  { value: 'PENDING_BOSS',    label: '待终审' },
  { value: 'APPROVED',        label: '已通过' },
  { value: 'REJECTED',        label: '已驳回' },
  { value: 'CANCELLED',       label: '已撤销' },
  { value: 'REVOKED',         label: '已撤回' },
  { value: '',                label: '全部' }
]

/** 请假单操作留痕动作（审计，D6） */
export const LEAVE_LOG_ACTION = {
  SUBMIT:          { label: '提交申请' },
  UPDATE:          { label: '修改申请' },
  RESUBMIT:        { label: '修改后重新提交' },
  CANCEL:          { label: '申请人撤销' },
  STATION_APPROVE: { label: '站长初审通过' },
  STATION_REJECT:  { label: '站长初审驳回' },
  FINAL_APPROVE:   { label: '老板终审通过' },
  FINAL_REJECT:    { label: '老板终审驳回' },
  REVOKE:          { label: '审批人撤回' }
}

/** 按天考勤状态（考勤记录/汇总的「按天」展示层，非打卡记录级枚举） */
export const DAY_ATTENDANCE_STATE = {
  NORMAL:      { label: '正常', type: 'success', variant: 'soft' },
  LATE:        { label: '迟到', type: 'warning', variant: 'soft' },
  EARLY_LEAVE: { label: '早退', type: 'warning', variant: 'soft' },
  ABNORMAL:    { label: '异常', type: 'danger',  variant: 'soft' },
  LEAVE:       { label: '请假', type: 'primary', variant: 'soft' },
  MISS:        { label: '缺卡', type: 'info',    variant: 'outline' }
}
```

**需要修改的既有字典（三处镜像，必须同版）**：

| 常量 | 位置 1 | 位置 2 | 位置 3 | 改动 |
| --- | --- | --- | --- | --- |
| `ATTENDANCE_METRIC` | `dict.js:259-264` | `src/shared/mock/financeStore.js:62-68`（`ATTENDANCE_FIELD`）/ `:68`（`ATTENDANCE_LABEL`） | `src/pc/utils/payrollPreview.js:89-95`（`ATTENDANCE_FIELD` / `ATTENDANCE_LABEL`） | **新增 `LEAVE: { label: '请假' }`**（第 5 个指标），三处同增，`LEAVE → leaveCount` |
| `NOTIFICATION_TYPE` | `dict.js:80-85` | `src/shared/mock/routes/notification.js:80`（硬校验数组 `[1,2,3,4]`） | — | **新增 `5: { label: '请假申请' }`、`6: { label: '请假结果' }`**，同时把校验数组扩为 `[1,2,3,4,5,6]` |

**不改动的枚举（重要，避免误改）**：

- `ATTENDANCE_STATUS`（`dict.js:101-106`）**不新增 `LEAVE`**：它描述的是「打卡事实」（`ABNORMAL` 的注释明确写了「不计入出勤口径」），请假不是一种打卡行为。请假的「按天」展示改用新增的 `DAY_ATTENDANCE_STATE`。
- `RECORD_STATUS`（`src/shared/mock/routes/attendance.js:331`，打卡记录接口的入参校验枚举）**不新增 `LEAVE`**：请假不是可查询的打卡记录类型。
- `ATTENDANCE_STATUS` 的 Mock 侧同版（`routes/attendance.js:331`）**不动**。

**命名冲突提醒（必须留意）**：`LEAVE` 这个字符串在代码里已被占用为**离职流程的「离岗」步骤键**（`src/shared/mock/hrStore.js:70`）与 `FlowSteps` 的步骤映射（`src/pc/components/FlowSteps.vue:36`）。本模块的 `LEAVE` 只作为 `ATTENDANCE_METRIC` 的键与通知 `bizType`，**与离职步骤键不在同一命名空间**，实现时不要复用同一个常量对象。

### 5.4 「写 LEAVE 考勤标记」的落地形态（D3 实现约定）

**不往 `attendance_record`（`records`）表写伪造打卡记录**，理由：`records` 的 `source` 字段语义是打卡来源（`NORMAL` / `MAKEUP`，见 `routes/attendance.js:357`），塞入请假会污染「打卡事实」并让考勤记录条数虚增（补卡已经用了 `source='MAKEUP'`，见 `attendanceStore.js:1178`）。

**采用「派生标记」模型**：

1. 新建 `src/shared/mock/leaveStore.js`，导出：
   - 写：`applyLeave` / `updateLeave` / `cancelLeave` / `approveLeave` / `revokeLeave`
   - 读：`queryLeaves` / `findLeave` / `leaveDateSetOf(employeeId, startDate, endDate)` / `approvedLeaveDays(employeeId, startDate, endDate)`
   - 汇总：`leaveCountOf(employeeId, startDate, endDate)`
2. **口径变更点（D3 的落地位置，两处必须同版）**：

   | 位置 | 现有实现 | 改为 |
   | --- | --- | --- |
   | `src/shared/mock/attendanceStore.js:772-791`（`employeeAttendanceStat`，算薪内核取数） | `absentCount: Math.max(0, scheduledDates.size - attendedDates.size)`（`:789`） | 见下方 §5.4 公式；同时新增 `leaveCount` 字段 |
   | `src/pc/utils/payrollPreview.js:82`（前端试算镜像） | `stat.absentCount = Math.max(0, scheduledDates.size - attendedDates.size)` | 同上（`payrollPreview.js:10-12` 明文要求两侧同版） |

   公式（`leaveDeductEnabled` 为 D5 的全局开关）：

   ```
   leaveDays = approvedLeaveDays(employeeId, startDate, endDate)          // 已批请假天数（按账期切分后）
   absentCount = leaveDeductEnabled
       ? Math.max(0, scheduledDays - attendedDays)                        // 开关 ON：请假按缺勤计（扣款）
       : Math.max(0, scheduledDays - attendedDays - leaveDays)            // 开关 OFF（默认）：请假不扣
   ```

3. **考勤记录/汇总页的「按天」展示**：按天合并时（现有逻辑 `src/mobile/utils/attendance.js:160-165` 的 `dayStatusOf`），若该天落在某条 `APPROVED` 请假单的半天区间内且当天无有效上班卡，则该天状态显示为 `LEAVE`（`DAY_ATTENDANCE_STATE.LEAVE`，蓝色），替代「缺卡」文案。

---

## 第 6 章 · 通知文案模板

### 6.1 接收人如何确定（**唯一难点**）

`pushNotification` 只接受**单个 `employeeId`**（`src/shared/mock/db.js:1054-1084`，写入时自动 `is_read=0`），系统内**没有「按角色/按驿站广播」的模型**。批量发布也是先展开成员工列表再逐人写（`src/shared/mock/routes/notification.js:84-115`）。因此请假通知按下列规则解析接收人：

```
站长 id = employees.find(e => e.role === 'STATION_ADMIN'
                          && e.station_id === leave.stationId
                          && e.status === 1 && e.is_deleted === 0)?.id
老板 id = employees.find(e => e.role === 'ADMIN'
                          && e.status === 1 && e.is_deleted === 0)?.id
```

- 每站恰好一名站长：`buildEmployees` 为每个驿站生成一条 `role:'STATION_ADMIN'`（`src/shared/mock/db.js:326`）。
- 全局恰好一名老板：`list.push(createEmployee(..., { role: 'ADMIN', stationId: null, ... }))`（`src/shared/mock/db.js:330`）。
- **降级规则（必须有）**：若本站无可用站长（不存在或已停用），`STAFF` 提交的单**直接进入 `PENDING_BOSS`**，并给申请人发一条说明通知（场景 9）。这条降级必须与 D1 的「站长跳过初审」共用同一条代码路径，避免出现两条不同的跳级逻辑。

### 6.2 通知文案模板

| # | 场景 | 接收人 | `type` | `title` | `content` | `bizType` / `bizId` | 点击跳转 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 员工提交 | **本站站长** | 5 | `{employeeName} 提交了请假申请` | `{类型} {起} ~ {止} 共 {naturalDays} 天，待您初审` | `leave` / `{id}` | `/staff/leave/review` |
| 2 | 站长提交（跳过初审） | **老板** | 5 | `{employeeName}（站长）提交了请假申请` | `{类型} {起} ~ {止} 共 {naturalDays} 天，待您终审` | `leave` / `{id}` | `/boss/leave` |
| 3 | 初审通过 | **老板** | 5 | `请假申请待终审` | `{employeeName} 的{类型} {起} ~ {止} 已通过 {stationName} 站长初审` | `leave` / `{id}` | `/boss/leave` |
| 4 | 初审驳回 | **申请人** | 6 | `请假申请未通过初审` | `{类型} {起} ~ {止}，驳回原因：{remark}。可修改后重新提交` | `leave` / `{id}` | `/staff/leave` |
| 5 | 终审通过 | **申请人** | 6 | `请假申请已通过` | `{类型} {起} ~ {止}，计薪 {countedDays} 天，已生效` | `leave` / `{id}` | `/staff/leave` |
| 6 | 终审驳回 | **申请人** | 6 | `请假申请被驳回` | `{类型} {起} ~ {止}，驳回原因：{remark}。可修改后重新提交` | `leave` / `{id}` | `/staff/leave` |
| 7 | 申请人撤销 | **当前待审的那一级审批人**（`PENDING_STATION` → 站长；`PENDING_BOSS` → 老板） | 6 | `请假申请已撤销` | `{employeeName} 已撤销 {类型} {起} ~ {止} 的申请` | `leave` / `{id}` | 站长 → `/staff/leave/review`；老板 → `/boss/leave` |
| 8 | 审批人撤回 | **申请人** | 6 | `已批准的请假被撤回` | `{类型} {起} ~ {止} 已被 {revokeByName} 撤回，原因：{revokeReason}。如有疑问请联系站长` | `leave` / `{id}` | `/staff/leave` |
| 9 | 无可用站长降级 | **申请人** | 6 | `请假申请已直接提交终审` | `本站暂无在职站长，您的申请已直接提交老板终审` | `leave` / `{id}` | `/staff/leave` |
| 10 | 通知目标缺失（站长/老板账号不存在） | 无（不写通知） | — | — | — | — | 写一条 `leave_logs` 记录 `NOTIFY_SKIP`，便于排障 |

**文案规范**：
- `{起}` / `{止}` 格式：`2026-10-01 上午`（同日区间压缩为 `2026-10-01 上午 ~ 下午`）。
- `{类型}` 取 `LEAVE_TYPE[leaveType].label`。
- 天数一律展示**自然天数**（申请人关心的口径），计薪天数只在场景 5 展示（这是唯一需要让员工预知工资影响的位置）。
- `remark` 原样引用审批人填写的原因，**不做截断**（写入时已限 2–100 字）。
- 通知 `content` 长度上限 500 字（`routes/notification.js:78`），上述模板最长约 120 字，安全。

### 6.3 前端跳转分支（必须补）

`src/mobile/components/NoticeList.vue:113-133` 已预留 `work_order` / `sync_task` / `parcel` / `payroll` / `flow` 五类 `bizType` 分支，但 Mock 从未产出 `parcel` / `payroll` / `flow`。**请假补一个 `leave` 分支**，且**必须按角色分流**（同一 `NoticeList` 同时服务老板端与员工端，见 `NoticeList.vue` 所在 `MessagePage.vue` 被 `/boss/message`(`mobile/router/index.js:45-49`) 与 `/staff/message`(`:162-166`) 复用）：

```js
if (item.bizType === 'leave') {
  if (auth.isAdmin) router.push('/boss/leave')
  else if (auth.role === 'STATION_ADMIN') router.push('/staff/leave/review')
  else router.push('/staff/leave')
  return
}
```

不跳详情页、只用 `bizId` 做后续深链预留：三个落点页的默认筛选已能自动定位到相关单（老板 `PENDING_BOSS`、站长 `PENDING_STATION`、员工全部），且移动端不新增详情路由（见 §3.1）。

---

## 第 7 章 · 运行日志上报页（D6）

### 7.1 采集范围

**要采**（四类）：

| 来源 | 触发 | 级别 |
| --- | --- | --- |
| 未捕获异常 | `window.addEventListener('error')` + Vue `app.config.errorHandler` | `ERROR` |
| Promise 未处理 | `window.addEventListener('unhandledrejection')` | `ERROR` |
| 接口失败 | 前端 http 层（`src/mobile/utils/http.js` / 一期 `src/hrm-admin/utils/request.js` 的响应拦截器）中 `code >= 400` 或网络异常 | `code >= 500` → `ERROR`；其余 → `WARN` |
| 业务操作失败 | 已知业务错误码（96xx / 91xx / 94xx 等）被前端捕获后自行上报 | `WARN` |

**要过滤**（不采）：

1. `401`（登录态失效/被顶下线）—— 属正常流程，且会触发强制跳登录，上报会产生噪音。
2. 用户主动取消：`AbortError`、`CanceledError`、路由切换导致的中断请求。
3. `ResizeObserver loop limit exceeded` / `ResizeObserver loop completed with undelivered notifications` —— 浏览器已知无害告警。
4. **静态资源加载失败**（`img` / `script` / `link` 的 `error` 事件）—— 信噪比极低，本期不收。
5. **指纹去重**：相同 `(message + route + code)` 在 **10 秒**内重复出现，只记首条并把 `count` 累加，避免报错风暴写爆缓冲。
6. 通知幂等失败的二次重试（D6 规定上报失败不重试，见 §7.3）。

### 7.2 脱敏规则（**白名单复制，不是黑名单删除**）

实现必须用 `sanitizeLog()` 做**字段白名单复制**：只把允许的字段复制到待上报对象，其余全部丢弃。理由：黑名单删除在新增字段时必然漏脱敏，白名单是唯一可持续的方式。

**不得记录的字段（明确列出）**：

| 类别 | 字段 / 关键字 |
| --- | --- |
| 账号凭据 | `password`、`oldPassword`、`newPassword`、`confirmPassword`、`pwd` |
| 令牌 | `token`、`Authorization`、`accessToken`、`refreshToken`、`jti`、`Bearer *` |
| 身份信息 | 身份证号及其任何形态、`idCard`、`idNo` |
| 联系方式 | 手机号**全量**（如需展示必须掩码为 `138****8888`）、`phone`、`mobile`、`email` 全量 |
| 财务数据 | `salary`、`basicSalary`、`postSalary`、`performanceBase`、`allowances`、`grossAmount`、`netAmount`、`bankCard` |
| 请求/响应体原文 | 一律不记；只记 `method` / `path`（去 query） / `status` / 业务 `code` / 耗时 |
| 其他 | `Cookie`、`localStorage` / `sessionStorage` 快照、服务器 IP、Mock 密钥 |

**URL 处理**：`path` 必须去掉 query string 与 hash（避免 `?token=xxx` 之类泄漏）。

### 7.3 环形缓冲上限与超出策略

| 层级 | 上限 | 超出策略 |
| --- | --- | --- |
| 前端内存缓冲 | **200 条** | FIFO，丢最旧（环形缓冲） |
| 批量上报触发 | 满 **20 条** 或 **30 秒**（先到者） | — |
| 单条载荷 | ≤ **8 KB** | 超出的 `stack` 截断到 2000 字，其余丢弃 |
| 上报失败 | — | **不重试、不弹提示、不计数**（避免二次风暴）；丢弃该批 |
| Mock 侧 `db.clientLogs` | **最近 500 条** | FIFO，丢最旧（防止演示库无限增长） |

**端点**：`POST /system/client-logs`（不限角色 —— 任何端都可能出错）。入参 `{ logs: [...] }`，返回 `{ accepted: n }`。
> 为什么用 `POST` 批量而非逐条：单条上报会让告警风暴时产生 N 个请求，批量 + 缓冲天然限流。

### 7.4 PC 查看页信息架构（P7 `/system/logs`，**仅 ADMIN**）

| 区块 | 内容 |
| --- | --- |
| 页头 | 标题「运行日志」（`PageHeader`）+ 自动刷新开关（`el-switch`，默认**关**，开启后 30s 轮询）+ 手动刷新 + 清空（二次确认） |
| 统计条 | 近 24h 总数 / `ERROR` 数 / 涉及端数（复用 `MiniStats`） |
| 筛选栏 | 级别（复用 `SYNC_LOG_LEVEL` 字典 `dict.js:44-48`）/ 端（PC / H5 / 壳）/ 时间区间 / 关键字（`message` 模糊）/ 员工 |
| 表格列 | 时间（等宽数字）｜级别（`StatusTag`）｜端｜员工｜路由｜错误摘要（截断 1 行，`show-overflow-tooltip`）｜次数｜操作（详情） |
| 详情抽屉 | 宽 520px（`--drawer-w`，`pc/styles/tokens.scss:49`）：完整 `message` / `stack`（等宽字体 + 一键复制，参照 `src/mobile/components/WorkOrderCopyButton.vue` 的复制形态）/ 请求上下文（method/path/status/code/耗时）/ UA / 出现次数与首末时间 |
| 导出 | CSV（复用 `src/shared/domain/csv.js`；附件头写法参照 `src/shared/mock/routes/attendance.js:414-417`） |
| 空/错误态 | `StateBlock`（`empty` / `error` 变体，错误态给「重试」按钮） |

**可见性结论**：
- **三端都上报**（PC / 移动 H5 / 安卓壳）。
- **仅 ADMIN 可查看，且只在 PC 提供查看页**。移动端**不做**查看页：手机屏幕不适合阅读堆栈，且运行日志是运维/排障场景，属 PC 工作台职责。此为刻意的功能不对称，需在前端不要为移动端预留入口。
- 端点 `GET /system/client-logs` 与 `POST /system/client-logs/clear` 均 `roles: ['ADMIN']`；`POST /system/client-logs`（上报）**不限角色**。

---

## 第 8 章 · 状态与空/错/边界态清单

### 8.1 逐页三态

| 页面 | 加载态 | 空态 | 错误态 | 无权限态 |
| --- | --- | --- | --- | --- |
| P1 申请表单 | 无列表，提交按钮 `loading`（对照 `staff/attendance.vue:307-308` 的 `submitting` 守卫） | — | 提交失败在表单内 `role="alert"` 就地渲染，**不弹 Toast**（对照 `makeupErrorHint` 的处置思路，`mobile/utils/attendance.js:141-154`） | 路由守卫按 `meta.roles` 弹回落地页 |
| P2 我的请假 | 骨架 3 行（`v-for="i in 3"` + `skeleton-block`，对照 `makeupList.vue:98-100`）；`PageState` 骨架 **200ms** 延迟阈值（`PageState.vue:22-39`） | `PageState :empty`，空文案按筛选动态：默认「还没有请假申请」+ 引导「点右上角发起请假」；筛选态「没有{状态}的请假申请」（对照 `makeupList.vue:29-31`） | `PageState :error` + 「重新加载」（`PageState.vue:53-58`） | 未登录 → 登录页 |
| P3 请假初审 | 同 P2 | 默认 `PENDING_STATION`：「没有待初审的请假申请」；其他筛选：「当前筛选条件下没有请假申请」 | 同 P2 | `STAFF` 访问 → 守卫弹回 `/staff/home` |
| P4 请假审批 | 同 P2 | 默认 `PENDING_BOSS`：「没有待终审的请假申请」 | 同 P2 | 非 ADMIN → 守卫 + Mock 403 |
| P5 扣款设置 | 骨架 1 块 | — | 保存失败就地提示 | 非 ADMIN → 守卫 |
| P6 PC 请假管理 | `el-table v-loading`（对照 `MakeupApproval.vue:195` 的 `sticky-table`）；列表错误时**不显示 loading** | `StateBlock variant="empty"` + 「重置筛选条件」动作（对照 `MakeupApproval.vue:185-192`） | `StateBlock variant="error" title="请假列表加载失败" @action="refresh"`（对照 `MakeupApproval.vue:183`） | `StateBlock variant="denied"`（`StateBlock.vue:24` 已内置「无访问权限」） |
| P7 运行日志 | `v-loading` | 「暂未采集到运行日志」 + 说明「日志为客户端运行时上报，出现异常后自动产生」 | `StateBlock variant="error"` + 重试 | 非 ADMIN → 守卫（菜单也不可见） |

### 8.2 极值态

| 场景 | 表现 | 实现要点 |
| --- | --- | --- |
| 超长事由（200 字） | 列表行截断 2 行 + 省略号；详情弹层/抽屉展示全文 | `-webkit-line-clamp: 2`（列表）；详情不做截断 |
| 跨月请假 | 列表显示完整区间；计薪天数按账期切分（见 §8.4） | 后端 `approvedLeaveDays` 必须支持区间切分 |
| 跨年请假 | 区间显示带年份：`2026-12-30 ~ 2027-01-02`；提示「跨年申请将分别计入 12 月与 1 月账期」 | 同上 |
| 同日多单（不同半天） | 允许，列表同日出现两行 | §4.4 半天粒度重叠校验 |
| 同日多单（同一半天） | 拦截，9603 | 同上 |
| 单次超 30 天 | 拦截，9604 | §4.5（开放问题 Q2） |
| 排序 | 列表按 `applyTime` 倒序（对照 `attendanceStore.js:1079` 的 `sort((a,b) => a.applyTime < b.applyTime ? 1 : -1)`） | 待办分组取前 3 条 |

### 8.3 并发 / 竞态

| # | 场景 | 设计处置 |
| --- | --- | --- |
| R1 | **两人同时审同一单** | 先到者成功；后到者收到 **9602**。前端在审批弹层内就地提示「该申请已被处理，列表已刷新，请关闭弹层查看最新状态」，并**静默对齐列表**（对照 `makeupApproval.vue:82-95` 的 `syncList()` 静默对齐 + `:129-135` 的错误分支） |
| R2 | **申请人在审批人打开弹层后撤销** | 同 R1：提交审批 → 9602；弹层副标题必须显示「当前状态」快照，让审批人察觉状态已变 |
| R3 | **重复提交（连点）** | 三重防护：① 提交按钮 `:loading` + `submitting` 守卫；② 服务端半日区间重叠 → 9603；③ 同 `employeeId` + 相同半日区间 + `PENDING` 视为幂等重复，返回 9603 而非创建两条 |
| R4 | **撤回与工资单生成并发** | 服务端在撤回事务内重查账期工资单状态；若并发生成则以「撤回时校验结果」为准返回 9606，前端提示用户先去财务处理 |
| R5 | **审批期间排班被修改**（原本有排班的日子改成休息，或反之） | **`countedDaysSnapshot`**：终审通过（T4）时把 `naturalDays` / `countedDays` 写入快照字段，此后算薪与展示一律以快照为准，`countedDays` 只作为「申请时的预估」保留。撤回时按快照回滚（§1.4） |
| R6 | **PC 与移动同时审批** | 同 R1（同一端点、同一状态机） |
| R7 | **通知写入失败 / 目标缺失** | 不阻断主流程（审批照常成功）；写 `leave_logs` 的 `NOTIFY_SKIP` 记录（对照 `pushNotification` 无返回值的容错思路） |

### 8.4 跨账期切分（**必须实现**）

算薪是按月进行的：`financeStore.contextOf(employeeId, month)` 用 `monthRange(month)` 取数（`src/shared/mock/financeStore.js:183-188`），前端镜像同口径（`src/pc/utils/payrollPreview.js:16-20`）。

因此一条跨月的请假单**必须按账期切分**计入各自月份：

```
approvedLeaveDays(employeeId, monthStart, monthEnd)
  = Σ 对每条 APPROVED 单:
      overlap = 该单的半日区间 ∩ [monthStart, monthEnd] 的半日区间
      += overlap 的计薪天数（按 §4.3.2 逐日查排班；排班缺失则按自然日折算的保守值）
```

**不做切分的后果**：跨月请假会整单落在起始月（当月多扣）或整单落在结束月（当月少扣），且另一月完全漏算 —— 这是本模块最容易出错的算薪点，必须写进测试用例（见 §9）。

### 8.5 `countedDaysSnapshot` 的取值规则

- 写入时机：仅 **T4 终审通过**（`PENDING_STATION` 被撤销/驳回不写快照）。
- 取值：`{ naturalDays, countedDays, scheduleDigest }`，`scheduleDigest` 为该区间内排班日期的排序拼接串（用于审计时核对「当时的排班是什么样的」）。
- 撤回时：按快照回滚，不做重算（重算会因排班变化产生与当初不一致的结果，破坏可审计性）。

---

## 第 9 章 · 验收标准

> 使用方式：`[实测]` = 必须在浏览器（PC ≥1280px）+ 移动端 375px 竖屏真机/模拟器下实际点击验证；`[静态]` = 读代码/查文件即可核查。

### 9.1 状态机与权限

- [ ] `[实测]` 员工提交 → 状态变 `PENDING_STATION`；站长账号登录 → 宫格「请假初审」计数 +1，消息待办「待初审请假」+1，Tabbar「消息」角标 +1。
- [ ] `[实测]` 站长提交 → **直接** `PENDING_BOSS`（不出现「待初审」），老板端计数 +1。
- [ ] `[实测]` 站长初审通过 → 老板端「待终审请假」+1；站长端该单从 `PENDING_STATION` 视图消失。
- [ ] `[实测]` 老板终审通过 → 员工端状态「已通过」；考勤记录页对应日期显示「请假」；若扣款开关为「不扣」，该月缺勤天数**减少** `countedDays`。
- [ ] `[实测]` 初审驳回 / 终审驳回 → 状态「已驳回」，副信息分别为「初审驳回」「终审驳回」，且**驳回原因非空才能提交**。
- [ ] `[实测]` 申请人在两种待审态均可撤销 → `CANCELLED`，且**不产生任何考勤/工资影响**。
- [ ] `[实测]` 老板撤回已通过单 → `REVOKED`，考勤缺勤天数**恢复**（验证回滚），撤回原因为必填。
- [ ] `[实测]` 该账期工资单为 `PENDING_APPROVAL`/`APPROVED`/`PUBLISHED`/`CONFIRMED` 时撤回 → 报 9606，且状态不变。
- [ ] `[实测]` 站长无法看到其他驿站的单（切到 `st001_admin` 只出现城东数据）。
- [ ] `[实测]` 站长的单不出现在自己的初审列表里。
- [ ] `[实测]` `STAFF` 账号访问 `/staff/leave/review` → 被守卫弹回 `/staff/home`。
- [ ] `[实测]` `STATION_ADMIN` 账号访问 `/boss/leave` → 被守卫弹回 `/staff/home`。
- [ ] `[实测]` `admin` 账号提交请假 → 提示「超级管理员无需提交请假申请」（9605）。
- [ ] `[静态]` 所有请假端点的 `roles` 已声明；`roles: ['ADMIN']` 覆盖终审与撤回，`roles: [ROLE.STATION_ADMIN]` 覆盖初审。

### 9.2 表单与时长计算

- [ ] `[实测]` 同日 `AM→PM` 显示「合计 1 天」；同日 `AM→AM` 显示「合计 0.5 天」。
- [ ] `[实测]` `10-01 PM → 10-03 AM` 显示「合计 2 天（自然日）」。
- [ ] `[实测]` 起始日期选择早于今天 → 不可选（PC `disabled-date` / 移动 `min-date`）；绕过前端直接构造请求 → 9604。
- [ ] `[实测]` 今天可提交（允许当天请假）。
- [ ] `[实测]` 关闭覆盖：手工把某天排班删掉再提交该天的请假，验证「计薪天数」比「自然天数」小，且副说明出现「已自动排除排班休息日」。
- [ ] `[实测]` 婚假/产假 3 类 → 计薪天数 == 自然天数，副说明为「本假别按自然日连续计算，与排班无关」。
- [ ] `[实测]` 已有 `PENDING_STATION` 单占 10-01 上午，再提 10-01 下午 → **允许**；再提 10-01 上午 → **9603 拦截并给出重叠单信息**。
- [ ] `[实测]` `REJECTED` 单不占用区间（可在同区间重新提交）。
- [ ] `[实测]` `PENDING_STATION` 单可编辑，编辑后状态不变、`updateTime` 变化；`PENDING_BOSS` 单编辑按钮置灰并给出原因文案。
- [ ] `[实测]` `REJECTED` 单「修改并重新提交」生成**新单**（新 id），原单仍为 `REJECTED` 只读。
- [ ] `[实测]` 事由 1 字 → 不可提交（`disabled`）+ 提示「请假事由须为 2-200 字」。
- [ ] `[静态]` 计薪天数实现**逐日查 `schedules`**，代码中无「按 `REST_CYCLE_DAYS` 推算休息日」的痕迹。

### 9.3 通知与入口

- [ ] `[实测]` 9 个通知场景逐一触发，接收人正确、文案与 §6.2 模板一致、`bizType==='leave'`。
- [ ] `[实测]` 点击通知按角色跳到正确页面（老板 `/boss/leave`、站长 `/staff/leave/review`、员工 `/staff/leave`）。
- [ ] `[实测]` `POST /notifications/publish` 的类型校验已放行 5/6（构造 `type: 5` 发布成功）。
- [ ] `[实测]` 移动端宫格：「请假」对所有员工/站长可见；「请假初审」**仅站长可见**；「请假审批」仅老板可见。
- [ ] `[实测]` 消息待办三分组计数与列表 `total` 一致（含筛选无关性：切筛选后计数不变，对照 `MakeupApproval.vue:79-87` 的 `fetchPendingCount`）。
- [ ] `[实测]` 「我的」页三个新 cell 按角色正确出现/隐藏。
- [ ] `[实测]` Tabbar 仅 3 项，未新增第 4 项。

### 9.4 运行日志

- [ ] `[实测]` 制造一个未捕获异常 → 30s 内 PC `/system/logs` 出现一条 `ERROR`。
- [ ] `[实测]` 制造一次接口 500 → 出现 `ERROR`；制造一次 9103/9603 业务失败 → 出现 `WARN`。
- [ ] `[实测]` 触发 401（另一设备顶下线）→ **不产生**日志。
- [ ] `[实测]` 在表单提交一次后，运行的日志条目里**搜不到**密码/手机号全量/token 字符串。
- [ ] `[实测]` 连续触发同一错误 5 次 → 只有 1 条记录且 `count = 5`。
- [ ] `[实测]` 内存缓冲满 200 后继续触发 → 最旧记录被丢弃（数量稳定在 200）。
- [ ] `[实测]` 导出 CSV 内容与列表口径一致。
- [ ] `[静态]` `sanitizeLog()` 采用**白名单复制**而非黑名单删除。

### 9.5 跨账期与口径

- [ ] `[实测]` 提交 `2026-09-28 ~ 2026-10-03` 的请假并通过 → 9 月账期缺勤扣除额与 10 月账期分别正确（不做整单归属）。
- [ ] `[实测]` 切换 D5 开关 ON → 同一批已批假数据下，缺勤天数**回升**（请假按缺勤计）；切回 OFF → 恢复。
- [ ] `[静态]` `attendanceStore.employeeAttendanceStat` 与 `payrollPreview.loadAttendanceStat` 的 `absentCount` 公式**逐字同版**（`payrollPreview.js:10-12` 的硬约定）。
- [ ] `[静态]` `ATTENDANCE_METRIC` 三处镜像（`dict.js:259-264` / `financeStore.js:62-68` / `payrollPreview.js:89-95`）已同增 `LEAVE`。
- [ ] `[实测]` 撤回一条已通过单后，该员工该月缺勤天数回到撤回前值，且全勤奖恢复。

### 9.6 视觉与无障碍

- [ ] `[实测]` 所有新增枚举无硬编码 hex（全走 Token）。
- [ ] `[实测]` 状态胶囊 6 态颜色/形态与 §1.1 表一致，字号 `--fs-micro`（移动 11px / PC 11px，`pc/styles/tokens.scss:24`）。
- [ ] `[实测]` 所有可点元素最小热区 ≥ 44×44px（筛选 chip、行内按钮、日期控件、开关）。
- [ ] `[实测]` 键盘可完成：移动端加卡不加，只要求 Enter/Space；PC 全流程 Tab + Enter 可完成「筛选中单选 → 打开弹窗 → 填原因 → 提交」。
- [ ] `[实测]` 焦点环可见（不使用 `outline: none` 无替代）。
- [ ] `[实测]` 弹层打开后焦点进入弹层、关闭后回到触发按钮；错误提示容器有 `role="alert"`（对照 `PageState.vue:53`）。
- [ ] `[实测]` 系统开启「减弱动态效果」后无位移/缩放动画（`tokens.base.scss:244-253`）。
- [ ] `[实测]` 375px 竖屏无横向滚动；`#app` 480px 兜底容器正常（`src/mobile/styles/mobile.scss:33`）。
- [ ] `[实测]` PC 1280px 与 1920px 下内容区宽度受 `--content-max: 1440px` 约束，表格不散列（`pc/styles/tokens.scss:53`、`pc/layout/index.vue:379`）。
- [ ] `[实测]` 说明文字在浅灰底上使用 `--text-2`（§5.1 的对比度禁区）。

### 9.7 文件级静态核查

- [ ] `[静态]` `src/shared/constants/dict.js` 已新增 6 个请假相关字典，**且 `src/mobile/constants/` 下无第二份请假状态字典**（对比补卡的教训，`mobile/constants/makeup.js:3-6`）。
- [ ] `[静态]` `src/shared/mock/routes/index.js:23-41` 已注册 `leaveRoutes`，且注册顺序不遮蔽既有路径。
- [ ] `[静态]` 新增错误码 9601–9607 同时写入 `errorCode.js` 的常量与 `CODE_MESSAGE`（`errorCode.js:155-228` 的结构要求）。
- [ ] `[静态]` `MENU_WHITELIST`（`role.js:27-31`）包含 `leave`（ADMIN + STATION_ADMIN）与 `logs`（仅 ADMIN）。

---

## 第 10 章 · 不建议做的事（防过度设计）

| # | 不做的事 | 理由 |
| --- | --- | --- |
| 1 | **不做年假余额 / 额度扣减 / 额度结转** | D4 已冻结为固定枚举。额度体系需要期初额、结转规则、失效规则、按工龄分段等一整套模型，远超本期范围 |
| 2 | **不做按假别差异化扣款、不做按驿站覆写扣款开关** | D5 已冻结为全局单开关。按假别/按驿站覆写会让「同一员工同一假别在不同驿站扣不同钱」，工资单解释成本陡增 |
| 3 | **不做多级会签 / 加签 / 委托他人审批 / 审批人变更** | D1 已冻结为固定两级。会签需要"全部通过才算通过"的聚合状态机，与当前单审批人模型不兼容 |
| 4 | **不做附件上传（病假条/证明）** | 系统当前唯一的文件通道是 Excel 导入的 `FormData`（`engine.js:74-75`），**没有通用文件存储**。引入附件需要存储/鉴权/病毒扫描/生命周期管理，属独立基础设施议题 |
| 5 | **不做请假与排班的联动调整** | 请假批准后**不自动**把排班改成休息。自动改排班会污染排班真源（`schedules`），且站长对排班有最终话语权，应由站长在排班页手工处理（排班页复制功能已有类似人工确认的思路，见 `mobile/views/boss/schedule.vue:20` 注释） |
| 6 | **不做半天排班粒度** | `schedules` 是日粒度（`attendanceStore.js:1268-1280` 只有 `workDate`）。把排班改成上午/下午两段属于排班模块重构，影响打卡、迟到判定、考勤汇总全链路，不是请假模块能顺手做的 |
| 7 | **不做移动端运行日志查看页** | 见 §7.4：手机不适合阅读堆栈。三端上报 + PC 单点查看是更合理的职责划分 |
| 8 | **不做短信 / App 推送 / 企微通知** | 系统只有站内信通道（`db.js:1054-1084`）。短信需网关与费用预算，推送需壳端集成 FCM/厂商通道，均超出本期 |
| 9 | **不做审批 SLA / 超时自动升级 / 超时提醒** | 请假不像工单有"超时即事故"的强紧迫语义。引入 SLA 会带来倒计时组件、阈值配置、升级链，收益远小于复杂度 |
| 10 | **不做请假数据导出（CSV）** | 首期无需求方；PC 已有 csv 能力（`shared/domain/csv.js`），后续可低成本追加。运行日志的导出要做是因为它是排障工具，需要把现场带出去 |
| 11 | **不做「撤销后自动回填到新申请表单」** | 会让用户在一个"已有数据"的表单里操作，产生"这是新单还是旧单"的歧义。宁可让用户从空表单重新填，行为可预测 |
| 12 | **不做「请假与 KPI 出勤指标的联动」** | KPI 的 `ATTENDANCE` 指标（`dict.js:178`）口径另议。KPI 指标与算薪口径是两套体系，本期只打通算薪（D3 明确要求），不扩大到 KPI |
| 13 | **不做请假审批的批量操作（批量通过）** | 请假涉及每个人的具体时段与事由，逐单看是应有的审慎；批量通过会让"看错行"的代价直接变成工资错误 |
| 14 | **不做站长的撤回权** | 见 §1.4 第 5 条与开放问题 Q1（本期只给 ADMIN，若主智能体判断现场需要再开） |
| 15 | **不做「已通过请假」的再次编辑** | 已生效的单只能通过「撤回 → 重新申请」变更，保证每一次生效都经过完整两级审批 |
| 16 | **不做新的 Tab / 新的移动端详情路由** | 见 §3.4-D 与 §3.1 的说明 |

---

## 附录 A · 接口契约草案

> 端点命名沿用既有风格（补卡为 `/attendance/makeup`，见 `routes/attendance.js:548-571`）。`roles` 缺省 = 不限角色（仅需登录）。
> **落地核对项**：本附录是设计师提出的契约草案，需主智能体与后端工程师确认后写入 `docs/api.md`。

| # | 方法 | 路径 | roles | 入参 | 出参 |
| --- | --- | --- | --- | --- | --- |
| 1 | `POST` | `/leave` | — | `{ leaveType, startDate, startPeriod, endDate, endPeriod, reason }` | `LeaveVO` |
| 2 | `POST` | `/leave/preview` | — | 同上（只算不落库） | `{ naturalDays, countedDays, hasRestDayExcluded }` |
| 3 | `GET` | `/leave/my` | — | `{ status?, startDate?, endDate?, pageNum, pageSize }` | 分页 `LeaveVO[]` |
| 4 | `GET` | `/leave/:id` | — | — | `LeaveVO`（含 `logs[]`、`canEdit`、`canCancel`、`canRevoke` 派生标志） |
| 5 | `PUT` | `/leave/:id` | — | 同 #1 | `LeaveVO` |
| 6 | `POST` | `/leave/:id/cancel` | — | `{}` | `LeaveVO` |
| 7 | `POST` | `/leave/:id/resubmit` | — | 同 #1 | `LeaveVO`（新单） |
| 8 | `GET` | `/leave/list` | `['ADMIN','STATION_ADMIN']` | `{ status?, stationId?, employeeId?, leaveType?, startDate?, endDate?, pageNum, pageSize }`；`stationId` 对站长被强制覆盖 | 分页 `LeaveVO[]` |
| 9 | `POST` | `/leave/:id/station-approve` | `['STATION_ADMIN']` | `{ approved: boolean, remark?: string }` | `LeaveVO` |
| 10 | `POST` | `/leave/:id/final-approve` | `['ADMIN']` | `{ approved: boolean, remark?: string }` | `LeaveVO` |
| 11 | `POST` | `/leave/:id/revoke` | `['ADMIN']` | `{ reason: string }` | `LeaveVO` |
| 12 | `GET` | `/leave/settings` | — | — | `{ leaveDeductEnabled: boolean }` |
| 13 | `PUT` | `/leave/settings` | `['ADMIN']` | `{ leaveDeductEnabled: boolean }` | `{ leaveDeductEnabled: boolean }` |
| 14 | `POST` | `/system/client-logs` | — | `{ logs: ClientLogItem[] }` | `{ accepted: number }` |
| 15 | `GET` | `/system/client-logs` | `['ADMIN']` | `{ level?, source?, keyword?, startTime?, endTime?, employeeId?, pageNum, pageSize }` | 分页 `ClientLogVO[]` + `counts` |
| 16 | `POST` | `/system/client-logs/clear` | `['ADMIN']` | `{}` | `{ cleared: number }` |

**`status` 参数的聚合虚拟值**：`status=PENDING` 由服务端展开为 `[PENDING_STATION, PENDING_BOSS]`，前端只需传 `'PENDING'`（见 `LEAVE_FILTERS` 与待办计数口径）。单值 `status=PENDING_STATION` 等仍按精确匹配。非法 `status` → 400「status 取值非法」（对照 `routes/attendance.js:466-467`）。

**`LeaveVO` 字段**：

```
{
  id, employeeId, employeeName, stationId, stationName,     // stationName 由 VO 追加（对照 toMakeupVO，attendanceStore.js:1065-1067）
  leaveType,                                                // LEAVE_TYPE key
  startDate, startPeriod, endDate, endPeriod,
  reason,
  naturalDays, countedDays,                                 // 申请时预估
  countedDaysSnapshot,                                      // 终审通过时快照（终审前为 null）
  status, rejectStage,                                      // rejectStage: null | 'STATION' | 'BOSS'
  originId,                                                 // 驳回后重提时指向原单
  stationApproverId, stationApproverName, stationApproveTime, stationApproveRemark,
  finalApproverId,   finalApproverName,   finalApproveTime,   finalApproveRemark,
  cancelTime, cancelById,
  revokeTime,   revokeById,   revokeByName,   revokeReason,
  applyTime, updateTime
}
```

> **为什么不放通用 `approverName` / `approveTime`**：补卡是单级审批故只需一组通用字段（`attendanceStore.js:1113-1129`），请假是两级，通用字段会在终审时覆盖初审信息，导致「谁初审的」永久丢失。分阶段存放是必须的。

**`leave_logs` 留痕结构**（参照 `db.workOrderTransfers` 的完整度：有前后值 + 操作人 + 时间 + 原因，`db.js:1008-1034`）：

```
{ id, leaveId, action, operatorId, operatorName, operatorRole,
  time, fromStatus, toStatus, before, after, remark }
```

---

## 附录 B · 错误码增量（96xx）

> 段位依据：既有 91xx 考勤（`errorCode.js:84-97`）、92xx KPI（`:104-109`）、93xx 人事（`:115-122`）、94xx 财务（`:128-134`）、95xx 同步配置（`:141-152`），顺延取 **96xx 请假段**。
> 每码必须同时写入 `ATTENDANCE_CODE` 同级的常量对象与 `CODE_MESSAGE`（`errorCode.js:155-228` 为兜底文案表），否则会出现「有码无文案」。

| 码 | 常量名 | 含义 | 默认文案（`CODE_MESSAGE`） |
| --- | --- | --- | --- |
| 9601 | `LEAVE_NOT_EXISTS` | 请假申请不存在 | `请假申请不存在` |
| 9602 | `LEAVE_STATUS_INVALID` | 状态不允许该操作（已被他人处理 / 当前状态不可编辑） | `该申请当前状态不支持此操作` |
| 9603 | `LEAVE_OVERLAP` | 与已有申请的时间段重叠 | `该时间段与已有申请重叠` |
| 9604 | `LEAVE_DATE_INVALID` | 日期非法（早于今天 / 结束早于开始 / 超单次上限） | `请假日期不合法` |
| 9605 | `LEAVE_NO_PERMISSION` | 无权操作（跨站 / 审自己 / ADMIN 提交） | `无权操作该请假申请` |
| 9606 | `LEAVE_PAYROLL_LOCKED` | 账期工资单已生成，不可撤回 | `该账期工资单已生成，不可撤回` |
| 9607 | `LEAVE_EDIT_FORBIDDEN` | 当前状态不允许修改 | `该申请当前状态不允许修改` |

**前端处置约定**：
- 9601 / 9602 / 9603 / 9606 / 9607 走 `{ silent: true }`，在弹层/表单内**就地**说明并给下一步（对照补卡 `approveMakeup` 的 `silent` 用法与理由，`mobile/api/index.js:117-121`）。
- 9604 由表单前置校验拦截，服务端命中时用通用 Toast 即可（属绕过前端的异常路径）。
- 9605 由路由守卫与渲染层拦截，服务端命中时用 PC `StateBlock variant="denied"` / 移动路由弹回。

---

## 附录 C · 需主智能体决策的开放问题

| # | 问题 | 设计侧倾向 | 影响范围 |
| --- | --- | --- | --- |
| Q1 | **站长是否也需要「撤回已批准单」的权限？** | 本期**不给**（只 ADMIN），符合最小权限 | 权限矩阵 §2.1、端点 #11 的 `roles` |
| Q2 | **单次请假最长天数取多少？30 天是否合适？** | 30 天（超出走线下或分次） | §4.5 护栏、错误码 9604 文案、测试用例 |
| Q3 | **`countedDaysSnapshot` 是否本期就做？** 它会让撤回逻辑增加一个字段与一段回滚分支 | 建议做（否则排班变更会让工资单与申请单口径漂移，且撤回无法可审计地回滚） | §8.3-R5、§8.5、算薪联动 |
| Q4 | **无可用站长时的降级路径**：直接进终审（本文档方案）vs 直接拒绝提交并要求联系管理员 | 选「直接进终审 + 通知申请人」（不阻塞员工请假） | §6.1 降级规则、§6.2 场景 9 |
| Q5 | **`POST /leave/preview` 是否本期实现**，或改为前端本地算计薪天数（需逐周拉排班，请求放大） | 建议本期实现（口径单点，避免第二份算薪镜像） | §4.3.3、附录 A #2 |
| Q6 | **PC「请假扣款设置」放在 `/leave` 页内卡片，是否可接受？** 还是独立菜单项 | 放页内卡片（配置与受影响数据同屏） | §3.1-P6、`MENU_ITEMS` 增量 |
| Q7 | **运行日志的保留上限（前端 200 / Mock 500）是否需要持久化到文件或数据库** | 本期只在内存/Mock（与既有 Mock 持久化桶同层） | §7.3 |
| Q8 | **本模块错误码段位 96xx 与通知类型 5/6 是否需与后端 `error_code` 表对齐** | 需要，属跨端契约，应由主智能体统一登记到 `docs/api.md` | 附录 B、`dict.js:80-85`、`notification.js:80` |
| Q9 | **跨驿站借调场景**：员工在 A 站归属、到 B 站顶班期间请假，归哪站审批？ | 本期**按 `employee.station_id` 归属站**处理，不做借调模型 | §2.1 数据范围、§6.1 接收人 |
| Q10 | **`C1 FilterChips` 抽取后是否顺手重构补卡两页？** | 建议顺手重构（三处变一处，纯样式搬家，风险可控） | §5.2.2-C1、补卡两页 |

---

**文档结束。** 落地前请主智能体完成 Review；本文档不含任何源码改动，全部实现由前端工程师按上述规范执行。

---

## 附录 D · 落地回写（前端工程师实现对照，2026-09-20）

> 数据契约层与 Mock 数据层（`hrm-demo/src/shared/constants/{dict,errorCode}.js`、
> `src/shared/mock/{leaveStore,clientLogStore}.js`、`src/shared/mock/routes/{leave,systemLogs}.js`）已落地，
> 与本文档的差异逐条登记如下。文档正文未改动，视本附录为「实现口径补正」。

| # | 本文档原写法 | 实际落地 | 理由 |
| --- | --- | --- | --- |
| 1 | 附录 A：`GET /leave/:id` 出参含 `logs[]` | 统一命名 **`handleLog[]`**，不保留 `logs` 别名 | 同一数组不留两个名字；字段清单以 `handleLog` 为交付口径（api.md 7.4 已同步） |
| 2 | 附录 A：终审字段写作 `finalApproverId / finalApproverName / finalApproveTime / finalApproveRemark` | 终审槽位落 **`approverId / approverName / approveTime / approveRemark`**，初审仍为 `stationApprover*` | 与交付面字段清单一致；两级仍**分槽存放**，不会相互覆盖（§1.2 的核心诉求不变） |
| 3 | §5.3 字典名 `LEAVE_REJECT_STAGE` | 同名（未采用简写 `REJECT_STAGE`） | 与文档命名逐字一致，便于全仓检索 |
| 4 | §7.3：Mock 侧 `db.clientLogs` 保留最近 **500** 条 | Mock 侧同样为 **200** 条 | 主智能体裁决 Q7「环形缓冲 200 条」；Demo 为单进程内存，两套上限无收益 |
| 5 | §7.2：脱敏仅要求「白名单复制」 | 白名单复制 **+** `message` / `stack` 内凭据串二次擦除（`token=***`） | 白名单挡不住写在错误文案里的 `?token=xxx`；§9.4 要求「日志条目里搜不到 token 字符串」，故补擦除（`sanitizeLog` 的 `scrub`） |
| 6 | §5.4 公式：`leaveDeductEnabled === true` → 请假按缺勤计（扣款） | 一致：ON 时缺勤**不减**已批请假天数，OFF（默认）时减 | 与开关名字面语义、§9.5 验收、§1.4 回滚描述一致。**注**：批次任务书 §2.6 的括号说明（「仅在 true 时排除」）与本节相反，按「设计规范为唯一权威依据」执行，已单独上报主智能体 |
| 7 | 附录 A：`GET /leave/settings` 的 `roles` 缺省（不限角色） | 一致：仅需登录即可读 | 与批次任务书给定的角色清单一致。**与 §2.1「站长/员工读都不可见」冲突**，登记为待决策项（改动仅一行 `roles: ['ADMIN']`） |
| 8 | §5.4：「写 LEAVE 考勤标记」= 派生标记模型 | 一致：`approvedLeaveDays()` 派生，未向 `records` 写伪造打卡 | 保护 `source` 语义（`NORMAL` / `MAKEUP`）不被污染 |
| 9 | §8.4 / §8.5：跨账期切分 + `countedDaysSnapshot` | 快照按 §8.5 落库（含 `scheduleDigest`）；`approvedLeaveDays` 由调用方按月区间调用、逐月重算，撤回以「不再计入」实现回滚 | 快照只存总额、无逐日明细，无法把跨月单按天切到两个月；算薪侧本就逐月取数，逐月重算即天然切分（正是 §8.4 要防的「整单落在起始月」）。快照保留用于审计核对，见 `leaveStore.countedDaysSnapshot` |
| 10 | §6.2 场景 9「无可用站长 → 直接进终审 + 通知申请人」 | 已实现（与站长跳级共用同一状态判定路径），**但未进自动断言** | 每个启用驿站都有在职站长，构造该场景需停用站长账号，会牵连其它断言；留待移动端联调时人工验证 |
| 11 | 种子数据（§4/§9 隐含要求「含休息日样本」） | 非城东驿站的种子改用 **NATURAL 假别**（婚假/产假/陪产假/丧假）；1 号站演示账号（id 3 / 4）**不参与种子**；城东「含轮休日」样本按排班矩阵动态挑选 | 排班种子只铺城东，其他驿站用 SCHEDULED 假别会让计薪天数恒为 0（数据自相矛盾）；演示账号留空，现场演示「自己提一单」才不会撞已有区间 |
| 12 | §7.1-5 指纹去重（10s 内只记首条并累加 `count`） | 在**上报端点**（服务端）也做了一次同规则去重 | 前端缓冲去重是页面侧职责；服务端再兜一层，避免某端漏做去重时把缓冲写爆（两处规则一致，不产生口径分歧） |

