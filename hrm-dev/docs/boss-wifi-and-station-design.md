# 驿站精灵 · WiFi 白名单可编辑区 + 站点管理（只读骨架）设计规范

> 版本 v1.0 ｜ 日期 2026-09-26 ｜ 作者：UI/UX 设计师（`express-station-ui-ux-designer`）
> 定位：`apps/boss-h5`（驿站精灵 · 管理端 H5）两处交付物的**设计规范** —— ① 打卡规则页 WiFi 白名单增强为可编辑区；② 新增「站点管理」只读骨架页。
> 输入产物：`hrm-dev/docs/requirement.md`、`docs/demo-mobile-nav-redesign.md`、`docs/ui-experience-optimization.md`、`docs/demo-ui-redesign.md`、`docs/demo-boss-ui-spec.md`、`docs/update-log.md`（2026-09-26 MVP 裁剪）、`apps/boss-h5/src/**`、`packages/tokens/src/tokens.base.scss`、`apps/boss-h5/src/styles/tokens.scss`、`packages/shared/src/ui/**`、`packages/mock/src/**`。
> **产出物性质：方案阶段产物。** 按 `.trae/rules/智能体调度规则.md` P0.6 / L8，本规范须先经技术评审工程师评估（结论「通过 / 有条件通过」）方可报主智能体审批；不含实现代码。
> **本文件不写业务代码、不改任何 `.vue` / `.js`、不改后端、不执行 git。**

---

## 0. 取证方式与已核实事实

**取证方式：** 静态读文件 + 精确 Grep（读源码与 Mock/契约）。**未运行**任何构建、测试、门禁、部署命令；行号与字段名以本机磁盘为准。

| # | 已核实事实 | 证据 |
| --- | --- | --- |
| F1 | 打卡规则页 WiFi 区当前为**只读**：`<p class="rule-text">{{ wifiText }}</p>` + 固定 tip「白名单需现场抓取 SSID 后维护，移动端仅查看；改白名单请走 PC 端。」；第 20 行有 `TODO(扩展)` 标记 | [attendanceRule.vue:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L20)、[:78-81](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L78-L81)、[:440-444](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L440-L444) |
| F2 | 保存走 `ActionBar`（`saveAttendanceRule` = `PUT /attendance/rule`）；阻断错误经 `formError` 作为 `note`；成功 `showSuccessToast('打卡规则已保存')`；`dirty` 用快照比对 | [attendanceRule.vue:160-162](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L160-L162)、[:272-285](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L272-L285)、[attendance.js:12](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/api/attendance.js#L12) |
| F3 | 后端（Mock）`wifiList` 校验**只有两条**：须为数组、每条 `ssid` 非空。**无长度上限、无 MAC 格式校验、无重复校验**；写入时 `ssid` trim、`bssid` 可空 | [attendance.js:95-99](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/attendance.js#L95-L99)、[:159-163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/attendance.js#L159-L163) |
| F4 | 打卡判定**只按 SSID 精确比对**（`===`，区分大小写），`bssid` 不参与判定 | [attendanceStore.js:1090](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/attendanceStore.js#L1090) |
| F5 | `PUT /attendance/rule` 的写权限口径：规则页仅 ADMIN 可写（页面路由 `roles: BOSS_ROLES = [ADMIN]`） | [router/index.js:9](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L9)、[:162-166](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/router/index.js#L162-L166) |
| F6 | H5 侧 `getWifiInfo()` 已存在，返回 `{ssid, bssid, mock}`；`mock===true` 表示未拿到真实值。**安卓壳侧未实现 `HrmBridge.getWifiInfo`**（`hrm-android-shell` 全目录 Grep 命中 0）→ 当前恒返回 `mock:true` | [bridge.js:40-64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/utils/bridge.js#L40-L64) |
| F7 | 「我的」页「管理与配置」分组现有 5 项：人事管理 / 排班管理 / 打卡规则 / 打卡记录 / 请假扣款设置；**无「站点管理」** | [MeSection.vue:51-58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/MeSection.vue#L51-L58) |
| F8 | 移动端 L2 规范既定：「管理与配置」是**配置类二级页入口**的归属分组；宫格只放高频待办/概览；无权限/不可用一律**不渲染按钮**，不置灰 | [demo-mobile-nav-redesign.md:93-97、284、638、449](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-mobile-nav-redesign.md#L93-L97) |
| F9 | 可复用组件：`StationPicker`（含 loading 骨架 / error+重试 / empty 三态）、`PageState`（loading 延迟 200ms 骨架 / error 重试 / empty / `variant="denied"`）、`ActionBar`、`StatusTag`、`PageNav` | [StationPicker.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/StationPicker.vue)、[PageState.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/shared/src/ui/PageState.vue)、[ActionBar.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/shared/src/ui/ActionBar.vue) |
| F10 | Vant 注册表（`vant.js`）**不含 `Dialog` / `SwipeCell` / `Form` 校验器之外的组件**；但已引入 `vant/es/dialog/style/index`，项目统一用函数式 `showConfirmDialog` 做二次确认 | [vant.js:1-32、46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/vant.js#L1-L32)、[LogoutAction.vue:3、15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/LogoutAction.vue#L3) |
| F11 | `GET /employees` 支持 `keyword / deptId / stationId / status / pageNum / pageSize`，出参含 `realName / username / role / status / stationName / deptName / entryDate`；**仅 ADMIN** | [employee.js:33-62、309](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/employee.js#L33-L62)、[db.js:993-1012](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/db.js#L993-L1012) |
| F12 | `GET /hr/profiles` 支持 `stationId / keyword / deptId / pageNum / pageSize`，出参含 `employeeName / username / deptName / stationName / entryDate / leaveDate`，**无 `role`、无工号**；且只返回**已建人事档案**的员工 | [hr.js:51-62](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/hr.js#L51-L62)、[hrStore.js:372-402、414-425](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/hrStore.js#L372-L402) |
| F13 | `GET /stations` 出参含 `id / code / stationName / employeeCount / status`；**仅 ADMIN** | [station.js:35-48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/station.js#L35-L48) |
| F14 | 本次 MVP 裁剪下架清单（**页面不得出现其入口与字样**）：KPI 模块、包裹族、同步、`/boss/rank`、占位页 | [update-log.md:3-11](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L3-L11) |
| F15 | 移动端 Token 真源：`packages/tokens/src/tokens.base.scss`（L1/L2 + 间距/圆角/阴影/动效）+ `apps/boss-h5/src/styles/tokens.scss`（字号阶梯 / `--touch-min` / 行高族 / 安全区 / Vant 覆盖） | [tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/tokens/src/tokens.base.scss)、[tokens.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/styles/tokens.scss) |

---

## 1. 设计目标与范围

### 1.1 目标

| # | 目标 | 可判定的完成定义 |
| --- | --- | --- |
| G1 | 管理员在移动端**现场维护本站 WiFi 白名单**（增 / 删 / 改 SSID 与 BSSID） | 页面上能新增一条白名单并保存成功，且保存后回读一致 |
| G2 | 走查时**不会误以为已读取到真实 WiFi** | 界面上不存在任何"已读取"表象；点「读取当前 WiFi」得到的是明确的"暂不支持、请手动输入" |
| G3 | 走查时**不会误以为填了 BSSID 判定更严** | 白名单区常驻一行说明：判定只看 SSID，BSSID 仅留痕 |
| G4 | 管理员/站长可在移动端**只读浏览各驿站及其名下员工** | 选定驿站后能看到员工列表并点开既有员工详情页；无任何写操作控件 |

### 1.2 本批做 / 不做清单

| 分类 | 项 | 处置 |
| --- | --- | --- |
| **做** | 打卡规则页 WiFi 白名单：展示态 / 编辑态 / 新增行 / 删除确认 / 空态 / 校验提示 / 与保存按钮联动 | 本规范 §3.1–3.4 |
| **做** | 「站点管理」页：站点选择 → 员工名册（四态）→ 员工详情跳转 | 本规范 §3.5、§4.2 |
| **做** | 「我的 · 管理与配置」新增「站点管理」入口 | 本规范 §2.2 |
| **不做**（本批） | 「读取当前 WiFi」自动回填（壳侧能力未实现） | 预留态 + `TODO(扩展)`，见 §3.3 |
| **不做**（本批） | 员工**分配 / 调整 / 跨站调动 / 新增 / 停用**等写操作（口径未定） | 不渲染任何按钮，仅 `TODO(扩展)`，见 §3.5.4 |
| **不做**（本批） | 白名单**批量导入 / 从 PC 端同步 / 扫描周边 AP** | 不设计入口 |
| **不做**（本批） | 新造员工详情页 | 复用既有 `/boss/hr/:employeeId` |
| **不做**（本批） | 站点管理页的筛选维度（状态 / 部门 / 角色筛选） | 仅保留关键词搜索（§3.5.3） |
| **不做**（本批） | 后端 `wifiList` 的 32 长度 / MAC 格式 / 重复校验补齐 | 前端先拦，后端补齐列为待裁决项（§11.2） |

---

## 2. 信息架构与入口

### 2.1 现状骨架（管理端三 Tab：首页 / 消息 / 我的）

```
/boss/home   首页（经营总览 + 7 项快捷宫格 + 组织规模）
/boss/message 消息（通知 / 待办）
/boss/me     我的
  ├─ 管理与配置 ← 本次新增第 6 项「站点管理」
  ├─ 账号信息
  ├─ 演示身份 / 账号安全 / 运行环境 / 退出登录
```

### 2.2 入口决策与依据

| 页面 | 入口位置 | 依据 |
| --- | --- | --- |
| 打卡规则（WiFi 白名单增强） | **不改入口**：`/boss/attendance/rule`，现有「我的 · 管理与配置 › 打卡规则」+ 考勤概览宫格「打卡规则」两条入口保留 | 本次是**页内增强**，非新增页面；入口变更属无收益 diff（原则：精简优先） |
| 站点管理 | **「我的 · 管理与配置」分组，「人事管理」之后、「排班管理」之前** | ① 该分组即 F8 既定的「配置类二级页入口」归属；② 站点管理与人事管理同属**组织主数据只读浏览**，语义相邻，插在 HR 域之后符合"组织 → 人事 → 考勤"的既有分组顺序；③ 首页宫格按 F8 只放高频待办/概览，站点管理是低频查询，**不进宫格**（MVP 裁剪后宫格已收敛为 7 项待办型，不再增长） |

**「站点管理」与「人事管理」的边界（避免两入口混淆）：**

| | 人事管理 `/boss/hr` | 站点管理 `/boss/station`（新） |
| --- | --- | --- |
| 查询起点 | **员工**（全局列表 + 关键词） | **驿站**（先选站，再看人） |
| 回答的问题 | "某人的档案/合同/定薪是什么" | "某驿站有哪些人" |
| 是否改写 | 有（调薪） | **无**（纯只读浏览） |
| 共同落点 | 员工详情 `/boss/hr/:employeeId` | 同左（不新造详情页） |

> 二者是**同一对象的不同检索维度**，与 `demo-mobile-nav-redesign.md` B5-4「同一目标的不同路径不视为重复入口」同型判据，非重复建设。

### 2.3 跳转关系

```
我的(/boss/me) ──管理与配置──┬── 打卡规则(/boss/attendance/rule) ──[页内]── WiFi 白名单可编辑区
                             └── 站点管理(/boss/station) ──StationPicker 选站──► 员工名册 ──► 员工详情(/boss/hr/:employeeId)
```

---

## 3. 交互流程

### 3.1 打卡规则 · WiFi 白名单可编辑区（主流程，ADMIN）

```
进入打卡规则页
  ▼
PageState: loading(整页骨架) → error(整页重试) → normal
  ▼
选驿站（现有 .station-pick → 底部弹层）
  ▼
WiFi 白名单卡（本次增强）
  ├─ 卡标题右侧 extra：`N 条 · 可编辑`（ADMIN）
  ├─ [校验关闭时] 卡顶 warn 提示条（§3.4）
  ├─ 恒常说明：判定只比对 SSID，BSSID 仅留痕（§3.1.3）
  ├─ 列表
  │    ├─ 展示态行：SSID(主) / BSSID(次) + [编辑][删除]
  │    └─ 编辑态行：SSID 字段 + BSSID 字段 + [取消][完成]
  ├─ [+ 添加白名单]（列表底部，block 次要按钮）
  └─ [读取当前 WiFi（暂不支持）]（预留态，§3.3）
  ▼
底部 ActionBar：保存规则（disabled/可用/提交中/失败）
  ▼
成功：showSuccessToast('打卡规则已保存')；失败：由 http 层统一弹（页内不重复）
```

#### 3.1.1 展示态行

- 结构：`van-cell`（`title` = SSID，`label` = BSSID 文案），`#right-icon` 放「编辑」「删除」两个次要按钮。
- SSID 字号 `--fs-body-strong`（15px）/ 色 `--text-1`；BSSID 走 `van-cell__label`（`--fs-caption` / `--text-3`）。
- 每行两个按钮各自 **≥44×44**（`min-height: var(--touch-min)`，`min-width: var(--touch-min)`）。
- 行高沿用 `--van-cell-*`（`--van-cell-vertical-padding: --sp-3`，单行 ≥48px；含 label 的两行约 66px）。

#### 3.1.2 编辑态行

- 结构：同一行位置切换为两条 `van-field`（`SSID` / `BSSID`）+ 一行 `[取消] [完成]`。
- 进入编辑态：点该行「编辑」。**同一时刻只允许一条处于编辑态**（新点「编辑」时，若有未完成的编辑行，先拦一次：见 §3.1.5）。
- `SSID` 字段：`required`，`maxlength="32"`，`placeholder="如 ST001-Express"`，`error-message` 承载校验文案。
- `BSSID` 字段：选填，`placeholder="如 AC:84:C6:00:00:03（可留空）"`，`maxlength="17"`。
- 「完成」：本行校验通过才收起为展示态，并写入本地白名单数组（**不立即请求**，仍走页面统一保存）。
- 「取消」：丢弃本行改动回到原值；新增行取消即移除该行。
- 编辑态行不显示删除按钮（避免误删正在编辑的项）。

#### 3.1.3 「判定口径」常驻说明（硬约束 G3）

卡内固定一行 `.tip`：**「校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。」**（依据 F4）

#### 3.1.4 新增行

- `[+ 添加白名单]`：`van-button block plain`，`min-height: var(--touch-min)`，主色文字/描边（`--color-primary` / `--color-primary-icon`）。
- 点击后追加一条**编辑态空行**并把焦点移入 SSID（移动端软键盘弹出，见 §8.3）。
- 列表为空时，该按钮与空态文案一起呈现（§5）。

#### 3.1.5 删除确认

- 点「删除」→ `showConfirmDialog({ title: '删除白名单', message: '删除「{ssid}」后，该 WiFi 将不再通过校验。确定删除？' })`（依据 F10：项目统一用函数式 Dialog）。
- 确认后仅从本地数组移除，**保存按钮变可用**（`dirty` 命中），由页面统一保存。
- 若此时有未完成的编辑行：先收起该编辑行（等同「取消」），再执行删除；不叠加两层确认。

#### 3.1.6 保存按钮联动（复用 ActionBar）

| 状态 | 判定 | 呈现 |
| --- | --- | --- |
| 禁用 | 无改动（`!dirty`）**或** 有阻断校验错误（`formError` 非空）**或** 有未完成的编辑行 | `disabled`；`note` 显示原因（见 §7） |
| 可用 | 有改动 且 无阻断错误 且 无未完成编辑行 | 主色实底可点（`--color-primary`，白字 6.16:1） |
| 提交中 | `saving === true` | 按钮 `loading`；`submitting` 置位（ActionBar 全按钮禁用，防连点） |
| 失败 | http 层弹错（现状口径，页内不重复） | 表单保持原样，用户可改后重试 |

**白名单校验错误的分工（关键）：**

- **行内错误**（字段下方 `error-message`、就近）：`SSID 为空` / `SSID 超长` / `BSSID 格式错` / **与同站已配置项重复**。
- **ActionBar `note`（跨字段阻断汇总）**：如「有 2 条白名单未填写 WiFi 名称」——只给结论，不重复行内细节。

#### 3.1.7 校验规则与文案落点

| 场景 | 触发 | 文案 | 展示位置 |
| --- | --- | --- | --- |
| SSID 为空 | 失焦 或 点「完成」 | 请填写 WiFi 名称（SSID） | 行内 `error-message` |
| SSID 超长（>32 字符） | 输入时（`maxlength` 兜底 + 校验） | WiFi 名称最长 32 个字符 | 行内 `error-message` |
| BSSID 非法 MAC | 失焦 或 点「完成」 | MAC 地址格式应为 AA:BB:CC:DD:EE:FF | 行内 `error-message` |
| 同站 SSID 重复（区分大小写） | 失焦 或 点「完成」 | 该 WiFi 已存在，请勿重复添加 | 行内 `error-message` |
| 多条未填 SSID | 汇总 | 有 {n} 条白名单未填写 WiFi 名称 | ActionBar `note` |
| 白名单条数上限 | 达到 20 条时新增按钮禁用 | 单站白名单最多 20 条 | 按钮下方 `.tip` |

> **32 字符 / MAC 格式 / 20 条上限**这 3 项是**前端先行的新增约束**（后端现状只校验 ssid 非空，见 F3）→ 与后端不一致，须在 §11.2 裁决并由后端补齐，否则可被 PC 端或直调 API 绕过。

### 3.2 权限分支（ADMIN 可编辑 / 非 ADMIN 只读）

| 分支 | 判定 | 呈现 |
| --- | --- | --- |
| **可编辑** | `auth.isAdmin === true` | 卡 extra = `N 条 · 可编辑`；展示态行带 [编辑][删除]；底部 [+ 添加白名单] 与 [读取当前 WiFi] 可见；保存按钮可点 |
| **只读** | `auth.isAdmin !== true` | 卡 extra = `N 条 · 只读`；列表项**不渲染**任何按钮（不留空位）；不渲染新增按钮；保留原有说明「白名单需现场抓取 SSID 后维护；改白名单请走 PC 端。」；ActionBar 主操作禁用并 `note` 说明「当前身份只能查看打卡规则，保存需管理员权限」 |

**实现口径：** 只读分支的开关取 `useAuthStore().isAdmin`（[auth.js:35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/stores/auth.js#L35)），不在页内自行判 `role`（判据单点化）。

> ⚠️ **本分支当前不会渲染**：`apps/boss-h5`（驿站精灵）按 ADR 端固定化为 **ADMIN 专用**（端准入 fail-closed 拒登其它角色、`BOSS_ROLES = [ADMIN]`、`HOME_BY_ROLE` 仅映射 ADMIN），站长走 `apps/staff-h5`。故"站长只读"在本端属**防御性分支 / 前向兼容位**；若将来要向站长开放本端，属结构变更（须主智能体裁决，且安全面须先由网络安全工程师出结论）。详见 §11.1-①。

### 3.3 降级分支（「读取当前 WiFi」不可用）

**事实：** H5 侧 `getWifiInfo()` 存在，但安卓壳未实现 `HrmBridge.getWifiInfo` → 当前**恒返回 `mock:true`、`ssid` 为空**（F6）。硬约束：不得伪装成真实能力。

**设计（预留态，可被发现但不伪装）：**

| 项 | 规范 |
| --- | --- |
| 控件 | 次要按钮（plain，主色描边），文案固定为 **「读取当前 WiFi（暂不支持）」** |
| 触控 | `min-height: var(--touch-min)`，`≥44px` |
| 点击行为 | **不发起任何读取**，只弹 `showToast('当前版本需手动输入 WiFi 名称：安卓壳暂不提供自动读取')` |
| 常驻说明 | 按钮右侧/下方 `.tip`：「当前版本请手动输入 SSID 与 BSSID」 |
| 禁止 | ❌ 不得预填任何 SSID/BSSID 值；❌ 不得出现"已读取/模拟"字样；❌ 不得显示 loading 后回填 |
| 扩展位 | 壳侧实现 `HrmBridge.getWifiInfo` 后：**仅当 `getWifiInfo().mock === false` 且 `ssid` 非空**才回填，并在字段下方标注来源「来自当前连接（安卓壳）」；`mock===true` 一律走手动输入 |

> 为什么不做成 `disabled` 置灰按钮：置灰按钮在项目规范里用于"无权限"，且读屏用户不可聚焦、也发现不了该能力存在（F8 的另一面）。本处需要的是"**能力尚未开放**"而非"无权限"，故用"可点 + 明确拒绝 + 常驻说明"三件套表达，既不伪装也不误导。
> `TODO(扩展): 安卓壳实现 HrmBridge.getWifiInfo 后，本按钮改为自动回填 SSID/BSSID；前置条件：壳侧实现 + 真机验证 mock===false + 安全面复核（设备信息读取属敏感能力）。`

### 3.4 与上方「WiFi 校验」开关的联动

**原则：** 状态"不参与判定" ≠ "不可编辑"。关掉校验**不隐藏、不置灰、不锁编辑**。

| 校验开关 | 白名单区呈现 |
| --- | --- |
| **开**（`enableWifi === true`） | 卡 extra = `N 条 · 可编辑`；列表可增删改；ActionBar 正常 |
| **关**（`false`） | 卡 extra = `N 条 · 已停用`；**卡顶加一条 `van-notice-bar`**：`left-icon="info-o"`，`color="var(--color-warning)"`、`background="var(--color-warning-surface)"`，文案「WiFi 校验已关闭，白名单暂不参与打卡判定」；列表**仍可编辑**（理由见下） |
| **开 且 白名单为空** | 卡顶加一条 `van-notice-bar`（warning 语义）：文案「WiFi 校验已开启但白名单为空，将无人能通过 WiFi 校验」；**提示但不阻断保存**（分步配置的正当流程要能走通） |

**为什么关闭时仍可编辑：** 否则用户必须先开校验 → 改白名单 → 再关校验，来回三次；`enableWifi` 与 `wifiList` 同属一次 `PUT` 提交，允许"先备好名单、后开校验"的批处理顺序。

**为什么"开且为空"不阻断：** 阻断项（`formError` → 禁用保存）会卡死"分步配置"路径。此处用**警告提示**表达风险，与阻断错误分层（阻断错误仍在 ActionBar `note`）。

**请求口径：** `wifiList` 始终随 `PUT /attendance/rule` 提交（不因开关关闭而剔除），保证"开关只是判定开关、不是数据开关"，避免关一下再开名单就丢了。

### 3.5 站点管理（只读骨架）

#### 3.5.1 主流程

```
我的 › 站点管理(/boss/station)
  ▼
PageNav「站点管理」
  ▼
[驿站选择 cell] ——点击——► StationPicker 弹层(allowAll=false)
  │                          ├─ loading：3 行等高骨架
  │                          ├─ error：原因 + [重新加载]（区块级，不替换弹层）
  │                          └─ normal：驿站列表（单选）
  ▼ 选定驿站
员工名册区
  ├─ van-search（§3.5.3）
  ├─ .tool-row：共 {total} 名员工
  ├─ loading：3 行骨架（高度对齐 list-item--rich）
  ├─ error：PageState error + 重试
  ├─ empty：PageState empty「该站点暂无员工」
  └─ normal：van-list 触底加载（pageSize=20）→ 列表项 ——点击——► /boss/hr/:employeeId
  ▼
页脚 .tip：「本页仅查看；员工分配与调整暂未开放。」
```

#### 3.5.2 结构要点

- **驿站选择**：复用 `StationPicker`（`v-model:show` / `:stations` / `:model-value` / `:loading` / `:error` / `@retry` / `@select`），**`allowAll = false`**（本页必须选定一个驿站，"全部驿站"聚合无意义 —— 与打卡记录/排班页有"全部"选项的需求不同）。
  触发 cell 用 `van-cell is-link`（Vant 已注册，走 `--van-cell-*` 全局收敛样式），比 `attendanceRule.vue` 的自绘 `.station-pick` 更省样式。
- **驿站数据**：`getStationList()`（`GET /stations`，ADMIN）。末项可显示 `employeeCount`（F13）作为概览，但**列表的"共 N 名员工"以名册接口的 `page.total` 为准**（单一真源，不出现两个数字）。
- **员工列表源**：`GET /employees?stationId=&keyword=&pageNum=&pageSize=`（F11）。选择依据见 §11.2（这是本批唯一需要主智能体裁决的数据源问题）。
- **列表项（`button.list-item.list-item--rich`）信息层级：**

```
┌ 姓名（--fs-body-strong / --fw-medium / --text-1）        [状态标签] ┐
│ 登录账号 · 部门（--fs-caption / --text-3）                          │
│ 角色 · 入职 2024-03-01（--fs-caption / --text-3，tabular-nums）     │
└                                                   van-icon arrow ►  ┘
```

- **员工详情入口**：`router.push('/boss/hr/' + item.id)`，复用既有页；**不新造详情页**。

#### 3.5.3 搜索（需要，理由如下）

**结论：本批提供。** 依据：① 服务端 `GET /employees` 原生支持 `keyword`（F11），**零契约成本**；② 与 `/boss/hr` 的"员工列表可搜"形成一致心智；③ 中转站员工数可变（>20 时纯滚动不可用）。
形态：`van-search`，`placeholder="搜索姓名或登录账号"`，`shape="round"`，与 [hr.vue:102](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/hr.vue#L102) 同型；**仅在已选定驿站后才渲染**（未选站时不渲染，避免对空列表搜索）。
可裁剪项：若主智能体要求更薄，可延后至下一批，不影响骨架闭环（搜索不是四态闭环的必要条件）。

#### 3.5.4 不做写操作的视觉克制（硬约束）

- **不出现**：分配 / 调整 / 调动 / 新增员工 / 停用 / 批量选择等任何按钮或 checkbox —— **包括置灰不置灰都不出现**（F8：不渲染不可用按钮）。
- **不出现** `ActionBar`：本页无底部操作栏 → 根容器用 `.page.page--loose`（不是 `.page--bar`）。
- 页脚一行 `.tip` 说明克制原因（文案见 §7）。
- `TODO(扩展): 站点管理写操作（分配/调整/跨站调动）—— 前置条件：① 后端提供站点级员工写接口（当前 /employees 写操作仅 ADMIN 且无批量分配/跨站接口）；② 口径确认（谁能分配、跨站是否需审批）；③ 命中 R04/R05（分配规则/人员分配）须先经算法工程师出方案；④ 过 P0.6 技术评审闸门。`

#### 3.5.5 分页与滚动

- `van-list` + `v-model:loading` + `:finished` + `finished-text="没有更多了"`，`pageSize = 20`（沿用 [attendanceRecords.vue:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRecords.vue#L19)、[hr.vue:17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/hr.vue#L17) 口径）。
- **切站 / 换关键词**：重置 `pageNum=0 / finished=false / list=[]` 后重新首屏加载（与 attendanceRecords 的筛选切换同型）。
- 翻页失败：已有数据时不打断列表，`showFailToast('加载更多失败，请稍后重试')` 并 `finished = true`（沿用既有先例 [attendanceRecords.vue:110-113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRecords.vue#L110-L113)）。

---

## 4. 组件结构与 DOM 层级建议

### 4.1 打卡规则页 · WiFi 白名单区

**复用清单：** `van-cell-group inset` / `van-cell` / `van-field` / `van-button` / `van-notice-bar` / `showConfirmDialog` / `showToast` / `ActionBar` / `PageState` / 全局 `.card` · `.section-title` · `.section-title__extra` · `.tip` · `.chip`。
**新增组件：无**（全部为页内模板 + scoped 样式；若前端认为值得，可将"白名单一行"抽为 `WifiRow.vue`，本轮不作要求）。

```html
<div class="section-title">
  WiFi 白名单
  <span class="section-title__extra">{{ wifiList.length }} 条 · {{ isAdmin ? '可编辑' : '只读' }}</span>
</div>
<div class="card">
  <!-- 校验关闭 / 白名单为空 的 warning 提示条（互斥，最多一条；也可都显示，按优先级取一条） -->
  <van-notice-bar v-if="!form.enableWifi" left-icon="info-o"
    text="WiFi 校验已关闭，白名单暂不参与打卡判定"
    color="var(--color-warning)" background="var(--color-warning-surface)" />

  <!-- 恒常口径说明 -->
  <p class="tip">校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。</p>

  <!-- 列表：展示态行 -->
  <van-cell v-for="(w, i) in wifiList" :key="w.key" :title="w.ssid"
    :label="w.bssid ? `BSSID ${w.bssid}` : 'BSSID 未填'">
    <template v-if="isAdmin" #right-icon>
      <button type="button" class="wifi-act" @click="editRow(i)">编辑</button>
      <button type="button" class="wifi-act wifi-act--danger" @click="askRemove(i)">删除</button>
    </template>
  </van-cell>

  <!-- 列表：编辑态行（同一时刻至多一条） -->
  <van-field v-model="draft.ssid" label="SSID" required maxlength="32"
    placeholder="如 ST001-Express" :error-message="draft.ssidError" />
  <van-field v-model="draft.bssid" label="BSSID" maxlength="17"
    placeholder="如 AC:84:C6:00:00:03（可留空）" :error-message="draft.bssidError" />
  <div class="wifi-edit-actions">
    <van-button plain size="small" @click="cancelEdit">取消</van-button>
    <van-button type="primary" size="small" @click="commitEdit">完成</van-button>
  </div>

  <!-- 空态 -->
  <p v-if="!wifiList.length" class="wifi-empty">尚未配置白名单</p>

  <!-- 新增（仅可编辑） -->
  <van-button v-if="isAdmin" block plain class="wifi-add" @click="addRow">+ 添加白名单</van-button>

  <!-- 预留态：读取当前 WiFi（§3.3） -->
  <van-button v-if="isAdmin" plain size="small" class="wifi-read" @click="onReadWifi">
    读取当前 WiFi（暂不支持）
  </van-button>
  <p class="tip">当前版本请手动输入 SSID 与 BSSID。</p>
</div>
```

**样式取值要点（全部既有 Token）：**

| 选择器 | 取值 |
| --- | --- |
| `.card` | 全局类（`--sp-4` / `--surface-card` / `--r-lg` / `--e1`） |
| `.wifi-act` | `min-height: var(--touch-min)`；`min-width: var(--touch-min)`；`font-size: var(--fs-body)`；`color: var(--color-primary)`；`background: none`；`border: none` |
| `.wifi-act--danger` | `color: var(--color-danger)` |
| `.wifi-edit-actions` | `display:flex; gap: var(--sp-3); margin-top: var(--sp-3)` |
| `.wifi-empty` | `margin: var(--sp-5) 0`；`font-size: var(--fs-caption)`；`color: var(--text-3)`；`text-align:center` |
| `.wifi-add` | `min-height: var(--touch-min)`；`margin-top: var(--sp-4)`；`color: var(--color-primary)`；`border-color: var(--color-primary-icon)` |
| `.tip` | 全局类（`--fs-caption` / `--lh-caption` / `--text-3`） |

### 4.2 站点管理页

**复用清单：** `PageNav` / `PageState` / `StationPicker` / `StatusTag` / `van-cell-group` / `van-cell` / `van-search` / `van-list` / `van-icon` / 全局 `.page` · `.page--loose` · `.list-item` · `.list-item--rich` · `.list-item__title` · `.list-item__meta` · `.list-item__tags` · `.tool-row` · `.tip` · `.skeleton-block` · `.notice`。
**新增组件：无**（如前端认为需要，可抽 `StationPickCell.vue` 供本页与打卡规则页共用，本轮不作要求）。

```html
<div class="station-page">
  <PageNav title="站点管理" />
  <div class="page page--loose">

    <!-- 1. 驿站选择（复用 StationPicker；触发用 van-cell） -->
    <van-cell title="当前驿站" :value="currentStationName" is-link @click="showStation = true" />
    <StationPicker v-model:show="showStation" :stations="stations" :model-value="stationId"
      :loading="stationsLoading" :error="stationsError" :allow-all="false"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations" @select="selectStation" />

    <!-- 2. 搜索（仅选定驿站后渲染） -->
    <van-search v-if="stationId != null" v-model="keyword" placeholder="搜索姓名或登录账号"
      shape="round" @search="loadFirst" @clear="loadFirst" />

    <!-- 3. 名册 -->
    <p class="tool-row tabular-nums">共 {{ total }} 名员工</p>

    <template v-if="loading">
      <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
    </template>

    <PageState v-else :error="error" :empty="!list.length" empty-text="该站点暂无员工" @retry="loadFirst">
      <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
        <button v-for="item in list" :key="item.id" type="button"
          class="list-item list-item--rich emp-row" @click="openEmployee(item)">
          <div class="list-item__title">
            <span>{{ item.realName }}</span>
            <van-icon name="arrow" aria-hidden="true" />
          </div>
          <div class="list-item__meta">{{ item.username }} · {{ item.deptName || '未分配部门' }}</div>
          <div class="list-item__meta tabular-nums">
            {{ roleLabel(item.role) }} · 入职 {{ item.entryDate || '-' }}
          </div>
          <div class="list-item__tags">
            <StatusTag :dict="EMPLOYEE_STATUS" :value="String(item.status)" />
          </div>
        </button>
      </van-list>
    </PageState>

    <p class="tip">本页仅查看；员工分配与调整暂未开放。</p>
  </div>
</div>
```

**样式取值要点：**

| 选择器 | 取值 |
| --- | --- |
| `.sk-row` | `height: 100px`（对齐 `hr.vue` 的 `.sk-row`）或 `calc(var(--row-h-3) + var(--sp-4))`；`margin-top: var(--sp-3)` |
| `.emp-row` | `display:block; width:100%; text-align:left; border:none; margin-top: var(--sp-3)`（沿用 [hr.vue:159-165](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/hr.vue#L159-L165)） |

---

## 5. 状态矩阵（loading / empty / error / normal）

### 5.1 打卡规则页 · WiFi 白名单区

| 区块 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 整页（规则取数） | `PageState loading`（延迟 200ms 出骨架，`rows=8`） | 不适用 | `PageState error` + 「重新加载」→ 重取规则 | 表单 + 白名单区 |
| 白名单卡 | **不单独骨架**（随整页骨架；原因：数据与规则同一次请求返回） | 「尚未配置白名单」（可编辑）/ 「未配置」（只读，沿用现有文案） | 同上（规则失败即整卡不可用） | 列表行 |
| 单条行 | 不适用（本地表单态） | 不适用 | 行内 `error-message` | 展示态行 / 编辑态行 |
| 「读取当前 WiFi」 | 不适用（**明确不做 loading**，因为不发起读取） | 不适用 | 不适用 | 预留态按钮 + 拒绝提示 |
| 保存按钮 | 不适用 | 不适用 | 由 http 层弹错 | 禁用 / 可用 / 提交中 |
| 驿站选择弹层 | `StationPicker` 内置 3 行等高骨架 | `StationPicker` emptyText | `StationPicker` 区块级 error + 重试 | 驿站单选列表 |

### 5.2 站点管理页

| 区块 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 驿站选择弹层 | `StationPicker` 骨架 | 「暂无可选驿站，请先在 PC 端维护驿站」 | 区块级 error + 「重新加载」 | 驿站单选列表（无「全部驿站」项） |
| 名册区 | 3 行 `.skeleton-block.sk-row`（高度对齐 `list-item--rich`） | `PageState empty`「该站点暂无员工」 | `PageState error` + 「重新加载」→ 重取名册（驿站选择保持不丢） | `van-list` 列表 + 「共 N 名员工」 |
| 未选驿站 | 不适用 | 初始态：`stationId` 默认取 `stations[0].id`（与打卡规则页同口径），故一般不出现"未选" | 不适用 | 已选定驿站的名册 |
| 搜索无结果 | 不适用 | `PageState empty`「没有匹配的员工」（与"该站暂无员工"文案分离） | 不适用 | 匹配结果的列表 |

> **空态与错误态文案必须不同**（`ui-experience-optimization.md` R1-3）：空态禁出现"失败/错误/网络"；错误态禁出现"暂无/没有数据"。

---

## 6. Design Tokens 取值

**结论：零新增 Token。** 全部取自 `packages/tokens/src/tokens.base.scss`（L1/L2 + 通用基础）与 `apps/boss-h5/src/styles/tokens.scss`（移动端私有层）。**不新增任何颜色字面量**；下表的色值仅为"该 Token 解析后的实值"，供 §9 对比度核验引用。

| 用途 | Token | 解析实值 |
| --- | --- | --- |
| 卡片 / 弹层 / 列表项底 | `--surface-card` | `#FFFFFF` |
| 页面底 | `--surface-page` | `#F5F7FA` |
| 骨架块底 | `--surface-sunken` | `#EDF0F4` |
| 一级正文（SSID、姓名） | `--text-1` | `#1F2937` |
| 二级正文（cell 值） | `--text-2` | `#4B5563` |
| 辅助/元信息（BSSID、账号、部门） | `--text-3` | `#6B7280` |
| 输入占位 | `--text-placeholder` | `#9AA4B2` |
| 主色（实底承白字 / 主色文字） | `--color-primary` | `#0958D9` |
| 主色描边 / 焦点环 | `--color-primary-icon` | `#1890FF` |
| 主色浅底（chip 选中） | `--color-primary-surface` | `#E8F4FF` |
| 危险文字 / 删除按钮 | `--color-danger` | `#CF1322` |
| 危险浅底 | `--color-danger-surface` | `#FFF1F0` |
| 警告文字 / 提示条文字 | `--color-warning` | `#B45309` |
| 警告浅底（提示条） | `--color-warning-surface` | `#FFFBE6` |
| 成功标签 | `--color-success` | `#237804` |
| 分割线 / 边框 | `--border-line` | `#E3E7ED` |
| 触控下限 | `--touch-min` | `44px` |
| 行高族（骨架对齐） | `--row-h-1 / --row-h-3` | `48px / 76px` |
| 间距 | `--sp-1…--sp-6` | `4 / 8 / 12 / 16 / 20 / 24px` |
| 圆角 | `--r-sm / --r-lg / --r-full` | `6 / 12 / 999px` |
| 阴影 | `--e1` | `0 1px 2px rgba(31,41,55,.06)` |
| 动效 | `--dur-fast` / `--ease-std` | `120ms` / `cubic-bezier(.4,0,.2,1)` |
| 字号（移动端阶梯） | `--fs-body-strong` / `--fs-body` / `--fs-caption` / `--fs-h3` | `15 / 14 / 12 / 14px` |
| 字重 | `--fw-medium` / `--fw-semibold` | `500 / 600` |
| Vant 表格/字段/按钮 | `--van-cell-*` / `--van-field-*` / `--van-button-radius` | 已在 `tokens.scss` 收口 |
| 安全区 | `--safe-bottom` / `--page-pad-bottom` | `env(safe-area-inset-bottom,0)` / `calc(--actionbar-h + --safe-bottom + --sp-2)` |

**若确需新增的候选（均判定为不需要，列出以备复核）：**

| 候选 | 为何不需要 | 替代方案 |
| --- | --- | --- |
| 「预留态/不适用」专用色 | 用既有 `--color-warning` 语义已能表达"能力未开放"的提示；新增一组只会与 warning/simulate 互相牵连 | 复用 `--color-warning` + `--color-warning-surface`；文案承担语义 |
| 白名单行专用行高 | 行高由 `--van-cell-*` 与 `--row-h-*` 派生即可 | `--touch-min` + `--van-cell-vertical-padding` |
| 员工状态标签第 3 色 | 在职=success / 已停用=neutral，`--state-*` 已覆盖 | `StatusTag` + `--state-success-*` / `--state-neutral-*` |

---

## 7. 文案清单

> 原则：说清"是什么 / 为什么 / 怎么办"，不用"敬请期待""即将上线""赋能""一键"等模板话术。

### 7.1 打卡规则 · WiFi 白名单

| # | 场景 | 文案 |
| --- | --- | --- |
| T1 | 卡片标题 extra（可编辑） | `{n} 条 · 可编辑` |
| T2 | 卡片标题 extra（只读） | `{n} 条 · 只读` |
| T3 | 恒常口径说明 | 校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。 |
| T4 | 校验已关闭提示条 | WiFi 校验已关闭，白名单暂不参与打卡判定 |
| T5 | 校验开启但白名单为空 | WiFi 校验已开启但白名单为空，将无人能通过 WiFi 校验 |
| T6 | 展示态 BSSID 未填 | BSSID 未填 |
| T7 | 空态（可编辑） | 尚未配置白名单 |
| T8 | 空态（只读） | 未配置 |
| T9 | 只读态保留说明 | 白名单需现场抓取 SSID 后维护；改白名单请走 PC 端。 |
| T10 | 只读态 ActionBar note | 当前身份只能查看打卡规则，保存需管理员权限 |
| T11 | 新增按钮 | + 添加白名单 |
| T12 | 编辑态 SSID placeholder | 如 ST001-Express |
| T13 | 编辑态 BSSID placeholder | 如 AC:84:C6:00:00:03（可留空） |
| T14 | 行内错误：SSID 空 | 请填写 WiFi 名称（SSID） |
| T15 | 行内错误：SSID 超长 | WiFi 名称最长 32 个字符 |
| T16 | 行内错误：BSSID 非法 | MAC 地址格式应为 AA:BB:CC:DD:EE:FF |
| T17 | 行内错误：重复 | 该 WiFi 已存在，请勿重复添加 |
| T18 | ActionBar note：多条未填 | 有 {n} 条白名单未填写 WiFi 名称 |
| T19 | ActionBar note：有未完成编辑行 | 有 1 条白名单正在编辑，请先完成或取消 |
| T20 | 条数上限（按钮下方） | 单站白名单最多 20 条 |
| T21 | 删除确认标题 | 删除白名单 |
| T22 | 删除确认正文 | 删除「{ssid}」后，该 WiFi 将不再通过校验。确定删除？ |
| T23 | 删除确认按钮 | 删除（危险样式）/ 取消 |
| T24 | 读取当前 WiFi（预留态按钮） | 读取当前 WiFi（暂不支持） |
| T25 | 读取当前 WiFi 点击提示 | 当前版本需手动输入 WiFi 名称：安卓壳暂不提供自动读取 |
| T26 | 读取按钮下方说明 | 当前版本请手动输入 SSID 与 BSSID。 |
| T27 | **移除**的旧文案 | ~~白名单需现场抓取 SSID 后维护，移动端仅查看；改白名单请走 PC 端。~~（仅在只读态以 T9 形式保留前半句，删除"请走 PC 端"的默认口径） |
| T28 | 保存成功 | 打卡规则已保存（沿用现有） |
| T29 | 「完成」/「取消」（行内） | 完成 / 取消 |

### 7.2 站点管理

| # | 场景 | 文案 |
| --- | --- | --- |
| S1 | 页面标题 | 站点管理 |
| S2 | 「我的」入口 cell 标题 | 站点管理 |
| S3 | 「我的」入口 cell 副说明 | 按驿站查看名下员工 |
| S4 | 驿站选择 cell | 当前驿站 / 驿站名 |
| S5 | 驿站弹层标题 | 选择驿站（StationPicker 默认） |
| S6 | 驿站弹层空态 | 暂无可选驿站，请先在 PC 端维护驿站（沿用打卡记录页口径） |
| S7 | 搜索占位 | 搜索姓名或登录账号 |
| S8 | 计数行 | 共 {total} 名员工 |
| S9 | 空态（该站无人） | 该站点暂无员工 |
| S10 | 空态（搜索无命中） | 没有匹配的员工 |
| S11 | 错误态提示 | 员工名册加载失败 |
| S12 | 错误态次级说明 | 请检查网络后重试，若持续失败请联系管理员（PageState 默认） |
| S13 | 重试按钮 | 重新加载（PageState 默认） |
| S14 | 加载到底 | 没有更多了 |
| S15 | 翻页失败 | 加载更多失败，请稍后重试 |
| S16 | 页脚说明（视觉克制） | 本页仅查看；员工分配与调整暂未开放。 |
| S17 | 状态标签：在职 | 在职 |
| S18 | 状态标签：已停用 | 已停用 |
| S19 | 部门缺失 | 未分配部门 |
| S20 | 入职缺失 | 入职 - |

---

## 8. 响应式与安全区

| 项 | 规范 |
| --- | --- |
| 目标断点 | 手机竖屏 **320–430px**（与 `demo-mobile-nav-redesign.md` 同口径）；横屏沿用既有 `max-width: 640px` 居中 |
| 320px 下限 | 白名单编辑态两字段为整行 `van-field`（label 与输入同宽自适应），320px 下不溢出；`[取消][完成]` 两按钮各 `flex: 1` |
| 长文本 | SSID / BSSID / 姓名 / 账号 均按 `.list-item__title > span:first-child` 既有规则 `ellipsis` 不撑破；BSSID 全值 17 字符在 320px 可整行显示 |
| 底部安全区 | 打卡规则页由 `ActionBar` 承担（`padding-bottom: var(--safe-bottom)`，[ActionBar.vue:113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/shared/src/ui/ActionBar.vue#L113)），页根用 `.page--bar`；站点管理页无固定底栏，页根用 `.page--loose`，**不需要**安全区补偿 |
| 弹层安全区 | `StationPicker` 与删除确认弹层均带 `safe-area-inset-bottom`（StationPicker 已内置） |
| 顶部安全区 | 由 `--status-bar-height` / `--safe-top` 承载（壳注入，浏览器为 0） |

### 8.3 软键盘弹出处理（白名单编辑）

| 风险 | 处理 |
| --- | --- |
| 底部 `ActionBar` 被键盘顶起或遮挡输入框 | ActionBar 为 `position: fixed; bottom: 0`，**不随键盘上移**（移动端常见做法）；输入区在文档流内，由 `visualViewport` 滚动保证可见 |
| 焦点停留在编辑行、键盘挡住「完成」 | 「完成」按钮紧邻字段下方（同一卡片内），不放在页面底部；点「完成」前用户可见 |
| 新增行后键盘未弹出 | 追加行后对 SSID 输入框调用 `focus()`；若浏览器阻止（非用户直接手势），保持"不自动聚焦"，用户点一下即可 —— **不强制聚焦**（避免键盘突然弹出遮挡） |
| 收起键盘后布局跳变 | 编辑态行高（两字段 + 按钮行）固定，键盘收起不改变 DOM 结构 |
| iOS 输入框缩放 | 输入字号 ≥16px 可避免自动缩放；本页字段为 Vant `--fs-body`(14px) —— **接受现状**（页面已设 `viewport-fit=cover`，且项目全端同口径）；若走查发现 iOS 缩放影响体验，登记为 `TODO(扩展)` 由前端统一在移动层把 `van-field__control` 提到 16px（属全局改动，须单独评估，不在本批） |

---

## 9. 可访问性

### 9.1 对比度实测（关键色对）

> 标注「既有核验」的沿用 `demo-mobile-nav-redesign.md` E2 表；标注「手算」的按 WCAG 2.x 相对亮度公式本机复算。

| 元素 | 前景 / 背景 | 比值 | 结论 |
| --- | --- | --- | --- |
| 主按钮白字 | `#FFFFFF` / `--color-primary #0958D9` | **6.16:1** | ✅ 既有核验 |
| SSID / 姓名 | `--text-1 #1F2937` / `--surface-card #FFFFFF` | **14.68:1** | ✅ 手算 |
| cell 值（二级正文） | `--text-2 #4B5563` / `#FFFFFF` | **7.56:1** | ✅ 既有核验 |
| BSSID / 账号 / 部门 / 计数行 | `--text-3 #6B7280` / `#FFFFFF` | **4.83:1** | ✅ 既有核验 |
| 危险文字（删除按钮） | `--color-danger #CF1322` / `#FFFFFF` | **5.57:1** | ✅ 既有核验 |
| 危险文字在浅底上 | `#CF1322` / `--color-danger-surface #FFF1F0` | **5.08:1** | ✅ 手算 |
| 主色文字在选中浅底上 | `#0958D9` / `--color-primary-surface #E8F4FF` | **5.52:1** | ✅ 手算 |
| 警告提示条文字 | `--color-warning #B45309` / `--color-warning-surface #FFFBE6` | **4.83:1** | ✅ 手算 |
| 成功标签文字 | `--color-success #237804` / `#FFFFFF` | **7.53:1** | ✅ 既有体系（同族 700 档） |
| `--tip` 辅助文字（卡内白底） | `#6B7280` / `#FFFFFF` | 4.83:1 | ✅ ≥4.5 |
| `--tip` 辅助文字（页面底色上） | `#6B7280` / `--surface-page #F5F7FA` | **4.50:1** | ⚠️ 刚好过线、**无余量** → **关键信息不得用 `--text-3` 落在页面底色上**，一律放白底卡内（既有规范同口径） |
| 主色 500 档做文字 | `#1890FF` / `#FFFFFF` | 3.24:1 | ❌ **禁止**用于 ≤14px 文字；仅允许图标/线/描边（既有规范重申） |

### 9.2 触控与键盘

| 要求 | 落实 |
| --- | --- |
| 触控 ≥44×44 | 行内「编辑」「删除」`min-height/min-width: var(--touch-min)`；「+ 添加白名单」「读取当前 WiFi」「取消/完成」`min-height: var(--touch-min)`；`StationPicker` 列表项 48px；列表项整体 ≥100px |
| 键盘可达 | 白名单按钮为原生 `<button type="button">`；列表项为 `<button>`；`van-field` 原生可聚焦；Tab 顺序＝DOM 顺序 |
| 焦点可见 | 复用全局 2px 主色焦点环（`--color-primary-icon`），**不得** `outline: none` |
| 状态不只靠颜色 | 删除按钮 = 颜色 + 文案「删除」；选中驿站 = 颜色 + `van-icon passed`；在职/已停用 = 颜色 + 文字标签 |

### 9.3 表单标签与错误提示的可读方式

| 项 | 规范 |
| --- | --- |
| 字段标签 | `van-field` 的 `label`（可见文字），不用 placeholder 代替标签 |
| 必填标识 | `required` 属性（Vant 输出 `*`），并在 `aria-label` 或可见文案中体现"必填" |
| 错误提示 | **就近**：`error-message` 渲染在字段下方，随字段一起被读屏读到；错误出现时字段容器加 `aria-invalid="true"` |
| 汇总提示 | ActionBar `note` 为视觉汇总；`role="toolbar"` 容器已存在，不改 |
| 删除确认 | `showConfirmDialog` 自带 `role="dialog"` 语义；标题「删除白名单」+ 正文说明，聚焦落在确认按钮 |
| 加载/错误区 | `PageState` 错误态 `role="alert"`（组件已内置）；列表骨架 `aria-hidden="true"`（组件已内置） |

---

## 10. 视觉走查清单（供后续验收逐条打勾）

### 10.1 打卡规则 · WiFi 白名单

- [ ] 卡片标题 extra 在 ADMIN 下为 `{n} 条 · 可编辑`，非 ADMIN 下为 `{n} 条 · 只读`
- [ ] 非 ADMIN 时列表**不渲染**「编辑/删除」按钮（不是置灰），且不渲染「+ 添加白名单」
- [ ] 恒常说明存在且文案准确：**判定只比对 SSID（区分大小写），BSSID 仅留痕**（F4）
- [ ] 「读取当前 WiFi」为预留态：文案含「暂不支持」；点击**不发起读取**、**不预填任何值**、提示"需手动输入"
- [ ] 页面上**不存在**"已读取/模拟/当前 WiFi 为…"等任何伪装真实能力的字样
- [ ] 校验开关关闭时：卡顶出现提示条「WiFi 校验已关闭，白名单暂不参与打卡判定」，**列表仍可编辑**
- [ ] 校验开启且白名单为空时：出现 warning 提示条，**保存按钮未因此被禁用**
- [ ] 空态文案：可编辑=「尚未配置白名单」，只读=「未配置」；两者都不含"失败/错误/网络"
- [ ] SSID 必填、超长（>32）、BSSID 非法 MAC、同站 SSID 重复 四类行内错误均就近展示
- [ ] 多条未填 SSID 时 ActionBar `note` 给出汇总数
- [ ] 有未完成编辑行时保存按钮禁用且 `note` 说明原因
- [ ] 删除走 `showConfirmDialog`，正文含被删 SSID
- [ ] 保存成功 `showSuccessToast('打卡规则已保存')`；保存失败**不在页内叠加第二条提示**
- [ ] 提交中按钮 `loading` 且全按钮禁用（防连点）
- [ ] 所有按钮/字段触控区 ≥44×44
- [ ] 无十六进制色值：新增 `<style>` 与模板内联**不出现 `#` 色值**，全部走 Token
- [ ] 未误用品牌色：`#1890FF`（500 档）**未用于任何文字**；实底承白字一律 700 档

### 10.2 站点管理

- [ ] 入口位置正确：`MeSection.vue`「管理与配置」分组「人事管理」之后；**未加入首页宫格**
- [ ] 页面标题「站点管理」；路由 `/boss/station`（不进 Tabbar，二级页自带返回）
- [ ] 复用 `StationPicker` 且 `allowAll=false`（**不出现「全部驿站」项**）
- [ ] 四态齐备：loading 骨架 / empty「该站点暂无员工」/ error + 重置 / normal 列表
- [ ] 搜索无命中文案与"暂无员工"文案**不同**（「没有匹配的员工」）
- [ ] 列表项信息层级：姓名 → 账号·部门 → 角色·入职 → 状态标签
- [ ] 点列表项跳既有 `/boss/hr/:employeeId`，**未新造详情页**
- [ ] **不出现**任何写操作控件（分配/调整/调动/新增/停用/批量选择），**也不出现其置灰版本**
- [ ] **未渲染** `ActionBar`；页根为 `.page--loose`
- [ ] 页脚 `.tip` 文案为「本页仅查看；员工分配与调整暂未开放。」
- [ ] 分页：`pageSize=20`，触底追加，`finished-text="没有更多了"`；切站/换词重置
- [ ] 翻页失败不打断已有列表，仅 Toast 提示

### 10.3 全页通用（硬红线）

- [ ] **不得出现被本批下架模块的字样或入口**：包裹 / 同步 / KPI / 绩效 / 排行(markdown 排行) / 趋势（F14）
- [ ] 对比度全部 ≥4.5:1（§9.1 表逐项复核；`--text-3` 不落在页面底色上）
- [ ] 触控区全部 ≥44px
- [ ] 所有颜色/间距/圆角/字号来自 Token，**无新造字面量、无 Token 外色值、无紫色**
- [ ] 底部安全区正确：有 ActionBar 的页用 `.page--bar`，无固定栏的页用 `.page--loose`
- [ ] `prefers-reduced-motion` 下动效降级生效（骨架脉冲 / 过渡）

---

## 11. 事实性核对与待裁决项

### 11.1 事实性纠正（任务描述与实测出入）

| # | 任务描述 | 实测 | 影响与处置 |
| --- | --- | --- | --- |
| ① | 「STATION_ADMIN（站长）当前无写权限，因此设计必须区分 ADMIN 可编辑 / 站长只读两态」 | 后端口径正确（`PUT /attendance/rule` 仅 ADMIN 可写）；但 **`apps/boss-h5`（驿站精灵）本身就是 ADMIN 专用端**：`BOSS_ROLES = [ROLE.ADMIN]`，`auth.isAdmin` 注释明写"端准入 fail-closed 拒登其它角色"，`HOME_BY_ROLE` 仅映射 ADMIN，站长走 `apps/staff-h5` | **站长在驿站精灵里根本进不来**，"站长只读"是**防御性分支**，本批不会渲染。设计保留该分支（零成本、前向兼容），但**不得**据此宣称"站长已可在移动端查看打卡规则"。若确实要让站长进入本端，属**端准入结构变更** → 需主智能体裁决 + 安全面先出结论（P0.5/L7） |
| ② | 「真实读取当前 WiFi 依赖安卓壳 `HrmBridge.getWifiInfo()`，该能力当前未实现」 | 结论正确，但精确表述为：**H5 侧桥接包装已实现**（`bridge.js:51`，返回 `{ssid,bssid,mock}`），**安卓壳侧未实现 `getWifiInfo`**（`hrm-android-shell` 全目录零命中）→ 恒走 `mock:true` 分支 | 设计按"能力未开放"处理（§3.3）；实现时判据用 `mock === false` 而非"壳内/浏览器"（因为壳内也可能返回 mock） |
| ③ | 站点列表项需要「工号」 | **全仓无"工号"字段**（`grep employeeNo` 零命中）。最接近的是 `username`（登录账号），且 `GET /employees` 的 `keyword` 只匹配 姓名/账号/手机号 | 列表项改为**登录账号**；搜索占位改为「搜索姓名或登录账号」。附带发现：`hr.vue:102` 的占位「搜索姓名或工号」**与后端匹配口径不符**（无工号字段）→ 建议前端一并改为"账号"（属微调，R02 范畴） |
| ④ | 「一驿站可能配多个 AP（多条白名单）」+ 需要"重复/超长/非法 MAC"提示 | 后端（Mock）`wifiList` 校验**只有**"数组 + ssid 非空"，**无长度上限 / 无 MAC 格式 / 无重复校验**；写入 `ssid` trim、`bssid` 可空 | 前端新增的 32 字符 / MAC 格式 / 20 条 / 重复 四项属**前端先行约束**，与后端不对称 → 见 §11.2 裁决项 ② |
| ⑤ | 「判定只按 SSID，bssid 仅留痕」 | 正确，且更精确：比对是 `w.ssid === wifiSsid`（**区分大小写**） | §7 T3 文案已写明"区分大小写"，避免用户以为不敏感 |

### 11.2 需主智能体裁决的点

| # | 事项 | 选项与推荐 |
| --- | --- | --- |
| ① | **站点管理的员工名册数据源**（同一份设计有两种口径，字段不完全重叠） | **选项 A（推荐）** `GET /employees?stationId=`：名册**完整**（含 `status=0` 已停用者），有 `role`/`status` 可支撑"角色 + 在职状态"列；代价：点开 `/boss/hr/:employeeId` 可能落到该页既有 `9301`（无档案）错误态。<br>**选项 B** `GET /hr/profiles?stationId=`：与详情页 VO 同源、点开必不 404；代价：**名册不全**（只有已建人事档案的员工出现，无档案者被 `filter(Boolean)` 丢弃）、**无 `role`**。<br>**推荐 A**（"站点有哪些人"是组织事实，不该由建档进度决定），并把"无档案者点开落 9301"作为已知限制记录、`TODO(扩展)` 待后端补 `hasProfile` 后隐藏箭头。**请裁决。** |
| ② | **WiFi 白名单校验口径是否下沉到后端** | 现状仅校验 `ssid` 非空（F3）。本设计新增 4 项前端约束。若不补后端：PC 端与直调 API 可绕过（出现"移动端拦了、PC 端能存"的不一致）。**建议**：后端在 `PUT /attendance/rule` 补齐同口径校验（属后端实现范畴，须后端工程师评估 + 可能触及契约说明）。**请裁决是否纳入本批。** |
| ③ | **白名单条数上限（20）与 SSID 长度上限（32）的取值确认** | SSID 32 字符取自 IEEE 802.11 标准上限（客观依据）；20 条为设计取值（一驿站 AP 数量经验值，无后端依据）。**请确认 20 是否合理**，或改为"不设上限、仅前端性能软提示"。 |
| ④ | **「读取当前 WiFi」预留态的交互形态** | 本规范取"可点 + 明确拒绝"（不置灰）；备选为"置灰 disabled"。两者取舍见 §3.3 理由。若主智能体更倾向"任何不可用能力都不渲染按钮"，可改为纯 `.tip` 说明。**请确认。** |

---

## 附：本轮规范自身检查（设计侧）

- [x] 设计方向有业务依据：沿用既有「品牌蓝 + 物流橙 + 深蓝灰」体系，无新色族、无紫色、无 AI 默认三板斧
- [x] 三层 Design Token 结构完整：**零新增 Token**（§6），全部映射既有变量
- [x] 两处交付物的四态全覆盖，含"不适用"的显式声明（§5）
- [x] 无障碍达标：对比度逐项给出（§9.1，含手算值）、触控 ≥44px、表单标签与错误提示可读方式（§9.3）
- [x] 权限分支与能力降级均按"不伪装、不置灰误导"处理（§3.2 / §3.3）
- [x] 所有结论标明来源 `文件:行号`；不确定处标"待裁决"（§11.2）
- [x] 本文件为**方案阶段产物**，未写实现代码、未改任何源码、未执行 git
