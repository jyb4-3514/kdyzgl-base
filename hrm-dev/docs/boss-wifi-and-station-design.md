# 驿站精灵 · WiFi 白名单可编辑区 + 站点管理（只读骨架）设计规范

> 版本 v1.1 ｜ 日期 2026-09-26 ｜ 作者：UI/UX 设计师（`express-station-ui-ux-designer`）
> 定位：`apps/boss-h5`（驿站精灵 · 管理端 H5）两处交付物的**设计规范** —— ① 打卡规则页 WiFi 白名单增强为可编辑区；② 新增「站点管理」只读骨架页。
> **v1.1 变更：** 按用户口径变更（已确认），WiFi 白名单由「可增删多条」修订为**「每个驿站仅一条」**。全文受影响条目已就地标注作废/修订，**完整取代条款见 §12**；「站点管理」功能本次不动。
> 输入产物：`hrm-dev/docs/requirement.md`、`docs/demo-mobile-nav-redesign.md`、`docs/ui-experience-optimization.md`、`docs/demo-ui-redesign.md`、`docs/demo-boss-ui-spec.md`、`docs/update-log.md`（2026-09-26 MVP 裁剪）、`apps/boss-h5/src/**`、`packages/tokens/src/tokens.base.scss`、`apps/boss-h5/src/styles/tokens.scss`、`packages/shared/src/ui/**`、`packages/mock/src/**`。
> **产出物性质：方案阶段产物。** 按 `.trae/rules/智能体调度规则.md` P0.6 / L8，本规范须先经技术评审工程师评估（结论「通过 / 有条件通过」）方可报主智能体审批；不含实现代码。
> **本文件不写业务代码、不改任何 `.vue` / `.js`、不改后端、不执行 git。**
> **v1.1 评审闭环修订（2026-09-26）：** 依据 `docs/tech-review-wifi-whitelist.md`（结论：**有条件通过**），本次就地修订：① §0 F1 引证按磁盘实况重取（该页**已实现多条可编辑**，非只读）；② §1.2 / §3.1.7 / §11.2 / §12.2 / §12.7 中「不动契约·后端」与「是否纳入本批」等**未决表述全部收敛为已落地**（后端下沉已随本批实现）；③ 新增 **§12.11–§12.14**（后端下沉落地对照 / 旧数据 >1 口径 / 风险登记 / NFR 与已知差异）。全部改动可追溯，**作废表述以删除线保留并注明原因**；Design Tokens 仍**零新增**。

---

## 0. 取证方式与已核实事实

**取证方式：** 静态读文件 + 精确 Grep（读源码与 Mock/契约）。**未运行**任何构建、测试、门禁、部署命令；行号与字段名以本机磁盘为准。

| # | 已核实事实 | 证据 |
| --- | --- | --- |
| F1 | 打卡规则页 WiFi 区**已达「多条可编辑」**（**非只读**）：卡标题 extra 显示条数（`{{ wifiExtra }}`，由 `wifiExtraLabel(wifiList.length, wifiEditable)` 生成）；列表 `v-for="w in wifiList"` **多行**；每行 `[编辑][删除]`；编辑态两字段（`maxlength=32` / `17`）+ `[取消][完成]`；空态 + 底部 `+ 添加白名单`；`读取当前 WiFi（暂不支持）` 预留态存在；提交体原样回传 **全部行**。→ **单条化须以此现存在实现为回落基线**（多条 → 单条）。<br>❌ **原 v1.1 表述「只读 `<p class="rule-text">{{ wifiText }}</p>`」已失效**：`wifiText` 在 `apps/boss-h5` 全仓 **0 命中**；该只读版实际存在于 **hrm-demo**（`hrm-demo/src/mobile/modules/boss/views/attendanceRule.vue:440-443`，`wifiText` computed 在 `:78`）——属**跨文件误引**（详见 §12.12 事实纠正） | [attendanceRule.vue:601-602](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L601-L602)、[:625-635](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L625-L635)、[:638-660](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L638-L660)、[:664-666](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L664-L666)、[:669-679](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L669-L679)、[:148-152](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L148-L152)、[:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L18) |
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
| G1 | 管理员在移动端**现场维护本站 WiFi 白名单**（设置 / 修改 / 清除 唯一的 SSID 与 BSSID） | 页面能设置**一条**白名单并保存成功，且保存后回读一致。**（v1.1 口径修订：白名单每站仅一条 → 见 §12）** |
| G2 | 走查时**不会误以为已读取到真实 WiFi** | 界面上不存在任何"已读取"表象；点「读取当前 WiFi」得到的是明确的"暂不支持、请手动输入" |
| G3 | 走查时**不会误以为填了 BSSID 判定更严** | 白名单区常驻一行说明：判定只看 SSID，BSSID 仅留痕 |
| G4 | 管理员/站长可在移动端**只读浏览各驿站及其名下员工** | 选定驿站后能看到员工列表并点开既有员工详情页；无任何写操作控件 |

### 1.2 本批做 / 不做清单

| 分类 | 项 | 处置 |
| --- | --- | --- |
| **做** | 打卡规则页 WiFi 白名单：展示态 / 编辑态 / **单条设置**（原「新增行」）/ **清除确认**（原「删除确认」）/ 空态 / 校验提示 / 与保存按钮联动 | 本规范 §3.1–3.4；**单条化口径修订见 §12** |
| **做** | 「站点管理」页：站点选择 → 员工名册（四态）→ 员工详情跳转 | 本规范 §3.5、§4.2 |
| **做** | 「我的 · 管理与配置」新增「站点管理」入口 | 本规范 §2.2 |
| **不做**（本批） | 「读取当前 WiFi」自动回填（壳侧能力未实现） | 预留态 + `TODO(扩展)`，见 §3.3 |
| **不做**（本批） | 员工**分配 / 调整 / 跨站调动 / 新增 / 停用**等写操作（口径未定） | 不渲染任何按钮，仅 `TODO(扩展)`，见 §3.5.4 |
| **不做**（本批） | 白名单**批量导入 / 从 PC 端同步 / 扫描周边 AP** | 不设计入口 |
| **不做**（本批） | 新造员工详情页 | 复用既有 `/boss/hr/:employeeId` |
| **不做**（本批） | 站点管理页的筛选维度（状态 / 部门 / 角色筛选） | 仅保留关键词搜索（§3.5.3） |
| ~~**不做**（本批）~~ → **已纳入本批（v1.1 评审闭环）** | 后端 `wifiList` 的 32 长度 / MAC 格式 / ~~重复~~校验补齐 | ~~前端先拦，后端补齐列为待裁决项（§11.2）~~ → **后端已落地同口径校验**（`AttendanceWifiValidator`：ssid 1–32 字符 / BSSID MAC / 去重区分大小写 / 条数 ≤1；契约固化于 `api.md` §8）→ 对照见 **§12.11**。重复校验因单条化作废；`length<=1` 已落地。 |

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

> ⚠️ **v1.1 口径修订（2026-09-26）：WiFi 白名单改为「每个驿站仅一条」。** 本节 §3.1.1 / §3.1.4 / §3.1.5 / §3.1.6 / §3.1.7 中与「多条」相关的表述**已就地标注作废**，其完整取代条款见 **§12**。标注为「保留」的条目（校验文案、删除/清除确认、权限分支）继续有效。**本节正文保留原貌以存演进痕迹，最终以 §12 为准。**

```
进入打卡规则页
  ▼
PageState: loading(整页骨架) → error(整页重试) → normal
  ▼
选驿站（现有 .station-pick → 底部弹层）
  ▼
WiFi 白名单卡（本次增强）
  ├─ 卡标题右侧 extra：`N 条 · 可编辑`（ADMIN）        ← 作废，单条化见 §12.3
  ├─ [校验关闭时] 卡顶 warn 提示条（§3.4）
  ├─ 恒常说明：判定只比对 SSID，BSSID 仅留痕（§3.1.3）
  ├─ 列表                                              ← 作废，改为「单条配置区」，见 §12.3
  │    ├─ 展示态行：SSID(主) / BSSID(次) + [编辑][删除]   ← 收敛为单条，动作改 [修改][清除]
  │    └─ 编辑态行：SSID 字段 + BSSID 字段 + [取消][完成]
  ├─ [+ 添加白名单]（列表底部，block 次要按钮）          ← 作废，改为 [设置 WiFi]，见 §12.3
  └─ [读取当前 WiFi（暂不支持）]（预留态，§3.3）          ← 落位修订见 §12.5
  ▼
底部 ActionBar：保存规则（disabled/可用/提交中/失败）
  ▼
成功：showSuccessToast('打卡规则已保存')；失败：由 http 层统一弹（页内不重复）
```

#### 3.1.1 展示态行 → **单条化修订见 §12.3**（cell 结构保留，动作按钮由 [编辑][删除] 改为 [修改][清除]）

- 结构：`van-cell`（`title` = SSID，`label` = BSSID 文案），`#right-icon` 放「编辑」「删除」两个次要按钮。
- SSID 字号 `--fs-body-strong`（15px）/ 色 `--text-1`；BSSID 走 `van-cell__label`（`--fs-caption` / `--text-3`）。
- 每行两个按钮各自 **≥44×44**（`min-height: var(--touch-min)`，`min-width: var(--touch-min)`）。
- 行高沿用 `--van-cell-*`（`--van-cell-vertical-padding: --sp-3`，单行 ≥48px；含 label 的两行约 66px）。

#### 3.1.2 编辑态行 → **单条化修订见 §12.5**（字段结构全部保留；「同时刻只允许一条」由约束变为事实）

- 结构：同一行位置切换为两条 `van-field`（`SSID` / `BSSID`）+ 一行 `[取消] [完成]`。
- 进入编辑态：点该行「编辑」。**同一时刻只允许一条处于编辑态**（新点「编辑」时，若有未完成的编辑行，先拦一次：见 §3.1.5）。→ 单条化后**物理上只可能有一条**，「同时刻一条」由约束降级为事实，§3.1.5 的拦截逻辑随之简化（见 §12.5）。
- `SSID` 字段：`required`，`maxlength="32"`，`placeholder="如 ST001-Express"`，`error-message` 承载校验文案。
- `BSSID` 字段：选填，`placeholder="如 AC:84:C6:00:00:03（可留空）"`，`maxlength="17"`。
- 「完成」：本行校验通过才收起为展示态，并写入本地白名单数组（**不立即请求**，仍走页面统一保存）。
- 「取消」：丢弃本行改动回到原值；**未配置态进入编辑后取消 → 回到未配置态（不产生空记录）**。
- 编辑态不显示「清除」按钮（避免误清正在编辑的项）。

#### 3.1.3 「判定口径」常驻说明（硬约束 G3）

卡内固定一行 `.tip`：**「校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。」**（依据 F4）

#### 3.1.4 新增行 ❌ **已作废（口径变更：每站仅一条）** → 取代条款见 §12.3 / §12.5

> 作废理由：单条化后不存在「追加多条」，故取消 `[+ 添加白名单]` 多行模式。取代为未配置态的单一动作 **「设置 WiFi」**（点开唯一的编辑表单）。**以下原文保留以存演进痕迹：**

- ~~`[+ 添加白名单]`：`van-button block plain`，`min-height: var(--touch-min)`，主色文字/描边（`--color-primary` / `--color-primary-icon`）。~~
- ~~点击后追加一条**编辑态空行**并把焦点移入 SSID（移动端软键盘弹出，见 §8.3）。~~
- ~~列表为空时，该按钮与空态文案一起呈现（§5）。~~

#### 3.1.5 删除确认 → **修订为「清除确认」，见 §12.5**

- 点「删除」→ `showConfirmDialog({ title: '删除白名单', message: '删除「{ssid}」后，该 WiFi 将不再通过校验。确定删除？' })`（依据 F10：项目统一用函数式 Dialog）。
- 确认后仅从本地数组移除，**保存按钮变可用**（`dirty` 命中），由页面统一保存。
- 若此时有未完成的编辑行：先收起该编辑行（等同「取消」），再执行删除；不叠加两层确认。

> **单条化修订：** 动作由「删除多行中的一行」变为「清除唯一一条（置空）」，文案与语义随之调整；**二次确认机制保留**。修订后条款 → **§12.5**。

#### 3.1.6 保存按钮联动（复用 ActionBar）

| 状态 | 判定 | 呈现 |
| --- | --- | --- |
| 禁用 | 无改动（`!dirty`）**或** 有阻断校验错误（`formError` 非空）**或** 有未完成的编辑行 | `disabled`；`note` 显示原因（见 §7） |
| 可用 | 有改动 且 无阻断错误 且 无未完成编辑行 | 主色实底可点（`--color-primary`，白字 6.16:1） |
| 提交中 | `saving === true` | 按钮 `loading`；`submitting` 置位（ActionBar 全按钮禁用，防连点） |
| 失败 | http 层弹错（现状口径，页内不重复） | 表单保持原样，用户可改后重试 |

**白名单校验错误的分工（关键）：**

- **行内错误**（字段下方 `error-message`、就近）：`SSID 为空` / `SSID 超长` / `BSSID 格式错` / ~~**与同站已配置项重复**~~（❌ **已作废**：单条化后同站不存在第二条，重复判定无适用场景 → 见 §12.4 / §12.6）。
- **ActionBar `note`（跨字段阻断汇总）**：~~如「有 2 条白名单未填写 WiFi 名称」~~（❌ **已作废**：单条化后至多一条，不存在"多条汇总"；未填提示改为单条行内 + `note` 仍保留"有 1 条白名单正在编辑"场景 → 见 §12.4）。

> 完整修订后分工 → **§12.6**。

#### 3.1.7 校验规则与文案落点 → **修订后清单见 §12.4**（沿用项：SSID 空/超长、BSSID 非法；作废项：重复、多条未填、20 条上限）

| 场景 | 触发 | 文案 | 展示位置 | 单条化处置 |
| --- | --- | --- | --- | --- |
| SSID 为空 | 失焦 或 点「完成」 | 请填写 WiFi 名称（SSID） | 行内 `error-message` | 保留 |
| SSID 超长（>32 字符） | 输入时（`maxlength` 兜底 + 校验） | WiFi 名称最长 32 个字符 | 行内 `error-message` | 保留 |
| BSSID 非法 MAC | 失焦 或 点「完成」 | MAC 地址格式应为 AA:BB:CC:DD:EE:FF | 行内 `error-message` | 保留 |
| ~~同站 SSID 重复（区分大小写）~~ | ~~失焦 或 点「完成」~~ | ~~该 WiFi 已存在，请勿重复添加~~ | ~~行内 `error-message`~~ | ❌ **已作废**（每站仅一条，无重复场景） |
| ~~多条未填 SSID~~ | ~~汇总~~ | ~~有 {n} 条白名单未填写 WiFi 名称~~ | ~~ActionBar `note`~~ | ❌ **已作废**（至多一条，无需汇总） |
| ~~白名单条数上限~~ | ~~达到 20 条时新增按钮禁用~~ | ~~单站白名单最多 20 条~~ | ~~按钮下方 `.tip`~~ | ❌ **已作废**（上限恒为 1，无计数语义） |

> **32 字符 / MAC 格式 / ~~20 条上限~~**这 ~~3~~ **2** 项原为**前端先行的新增约束**（后端曾只校验 ssid 非空，见 F3）；**（v1.1 评审闭环）后端已补齐同口径校验**（`AttendanceWifiValidator`，契约 `api.md` §8）→ **不再是「前端拦了、PC 端 / 直调 API 可绕过」的不对称状态**（对照见 §12.11）。
> **（v1.1）** 20 条上限**已作废**（每站仅一条）；32 字符 / MAC 格式两项**保留**且**后端已对齐**。原 §11.2 裁决项 ②③ 已**闭环（无待裁决）** → 见 §12.7。

### 3.2 权限分支（ADMIN 可编辑 / 非 ADMIN 只读）→ **权限分支本身不变**；仅 extra/按钮文案随单条化调整，见 §12.2

| 分支 | 判定 | 呈现 |
| --- | --- | --- |
| **可编辑** | `auth.isAdmin === true` | 卡 extra = ~~`N 条 · 可编辑`~~ **（作废 → 见 §12.3，单条化后不再显示条数）**；展示态行带 ~~[编辑][删除]~~ → **[修改][清除]**；底部 ~~[+ 添加白名单]~~ → **[设置 WiFi]** 与 [读取当前 WiFi] 可见；保存按钮可点 |
| **只读** | `auth.isAdmin !== true` | 卡 extra = ~~`N 条 · 只读`~~ **（作废 → 见 §12.3）**；配置项**不渲染**任何按钮（不留空位）；不渲染设置按钮；保留原有说明「白名单需现场抓取 SSID 后维护；改白名单请走 PC 端。」；ActionBar 主操作禁用并 `note` 说明「当前身份只能查看打卡规则，保存需管理员权限」 |

**实现口径：** 只读分支的开关取 `useAuthStore().isAdmin`（[auth.js:35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/stores/auth.js#L35)），不在页内自行判 `role`（判据单点化）。

> ⚠️ **本分支当前不会渲染**：`apps/boss-h5`（驿站精灵）按 ADR 端固定化为 **ADMIN 专用**（端准入 fail-closed 拒登其它角色、`BOSS_ROLES = [ADMIN]`、`HOME_BY_ROLE` 仅映射 ADMIN），站长走 `apps/staff-h5`。故"站长只读"在本端属**防御性分支 / 前向兼容位**；若将来要向站长开放本端，属结构变更（须主智能体裁决，且安全面须先由网络安全工程师出结论）。详见 §11.1-①。

### 3.3 降级分支（「读取当前 WiFi」不可用）→ **能力降级口径不变**；落位随单条化修订，见 §12.5

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

> **（v1.1）单条化后落位修订：** 该按钮**紧贴唯一那条输入行**（编辑态 BSSID 字段下方、`[取消][完成]` 之前）；**不再常驻展示态**——需先点「设置 WiFi / 修改」进入编辑态才可见。取代位置与理由 → **§12.5**。

> 为什么不做成 `disabled` 置灰按钮：置灰按钮在项目规范里用于"无权限"，且读屏用户不可聚焦、也发现不了该能力存在（F8 的另一面）。本处需要的是"**能力尚未开放**"而非"无权限"，故用"可点 + 明确拒绝 + 常驻说明"三件套表达，既不伪装也不误导。
> `TODO(扩展): 安卓壳实现 HrmBridge.getWifiInfo 后，本按钮改为自动回填 SSID/BSSID；前置条件：壳侧实现 + 真机验证 mock===false + 安全面复核（设备信息读取属敏感能力）。`

### 3.4 与上方「WiFi 校验」开关的联动

**原则：** 状态"不参与判定" ≠ "不可编辑"。关掉校验**不隐藏、不置灰、不锁编辑**。

| 校验开关 | 白名单区呈现 |
| --- | --- |
| **开**（`enableWifi === true`） | 卡 extra = ~~`N 条 · 可编辑`~~ **（作废 → 见 §12.3）**；配置区可设置/修改/清除；ActionBar 正常 |
| **关**（`false`） | 卡 extra = ~~`N 条 · 已停用`~~ **（作废 → 见 §12.3）**；**卡顶加一条 `van-notice-bar`**：`left-icon="info-o"`，`color="var(--color-warning)"`、`background="var(--color-warning-surface)"`，文案「WiFi 校验已关闭，白名单暂不参与打卡判定」；配置区**仍可编辑**（理由见下） |
| **开 且 白名单为空** | 卡顶加一条 `van-notice-bar`（warning 语义）：文案「WiFi 校验已开启但白名单为空，将无人能通过 WiFi 校验」；**提示但不阻断保存**（分步配置的正当流程要能走通）。**（v1.1）此态新增来源：单条化后「清除」置空亦落入本态 —— 见 §12.5** |

**为什么关闭时仍可编辑：** 否则用户必须先开校验 → 改白名单 → 再关校验，来回三次；`enableWifi` 与 `wifiList` 同属一次 `PUT` 提交，允许"先备好名单、后开校验"的批处理顺序。

**为什么"开且为空"不阻断：** 阻断项（`formError` → 禁用保存）会卡死"分步配置"路径。此处用**警告提示**表达风险，与阻断错误分层（阻断错误仍在 ActionBar `note`）。

**请求口径：** `wifiList` 始终随 `PUT /attendance/rule` 提交（不因开关关闭而剔除），保证"开关只是判定开关、不是数据开关"，避免关一下再开名单就丢了。

### 3.5 站点管理（只读骨架）

> **（v1.1）本节与「白名单每站仅一条」口径修订无关，本次全文不动。**

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

> ⚠️ **（v1.1）下方 DOM 为 v1.0「多条」版，保留以存演进痕迹。单条化修订后的 DOM（未配置 / 已配置 / 编辑中三态）见 §12.3。** 主要改动：`v-for` 列表 → 单条条件渲染；`+ 添加白名单` → `设置 WiFi`；`[编辑][删除]` → `[修改][清除]`；extra 去掉条数。

**复用清单：** `van-cell-group inset` / `van-cell` / `van-field` / `van-button` / `van-notice-bar` / `showConfirmDialog` / `showToast` / `ActionBar` / `PageState` / 全局 `.card` · `.section-title` · `.section-title__extra` · `.tip` · `.chip`。
**新增组件：无**（全部为页内模板 + scoped 样式；若前端认为值得，可将"白名单一行"抽为 `WifiRow.vue`，本轮不作要求）。

```html
<div class="section-title">
  WiFi 白名单
  <span class="section-title__extra">{{ wifiList.length }} 条 · {{ isAdmin ? '可编辑' : '只读' }}</span><!-- v1.1 作废：不再显示条数，见 §12.3 -->
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
  <van-button v-if="isAdmin" block plain class="wifi-add" @click="addRow">+ 添加白名单</van-button><!-- v1.1 作废：改为 [设置 WiFi]，见 §12.3 -->

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

### 5.1 打卡规则页 · WiFi 白名单区 → **单条化后完整状态矩阵见 §12.3**（本表按 v1.0 多条版保留）

| 区块 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 整页（规则取数） | `PageState loading`（延迟 200ms 出骨架，`rows=8`） | 不适用 | `PageState error` + 「重新加载」→ 重取规则 | 表单 + 白名单区 |
| 白名单卡 | **不单独骨架**（随整页骨架；原因：数据与规则同一次请求返回） | 「尚未配置白名单」（可编辑）/ 「未配置」（只读，沿用现有文案）→ **修订：可编辑态改为「尚未设置 WiFi 白名单」+ [设置 WiFi]，见 §12.3** | 同上（规则失败即整卡不可用） | ~~列表行~~ → 单条配置区（见 §12.3） |
| 单条行 | 不适用（本地表单态） | 不适用 | 行内 `error-message` | 展示态行 / 编辑态行（单条化后仅一条） |
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

**结论：零新增 Token。**（**v1.1 单条化后仍零新增**，见 §12.8）全部取自 `packages/tokens/src/tokens.base.scss`（L1/L2 + 通用基础）与 `apps/boss-h5/src/styles/tokens.scss`（移动端私有层）。**不新增任何颜色字面量**；下表的色值仅为"该 Token 解析后的实值"，供 §9 对比度核验引用。

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

> **（v1.1）单条化后文案清单见 §12.4。** 下列 T1/T2/T11/T17/T18/T20 **已作废**，T7 修订；标注「保留」者（T3–T6、T8–T10、T12–T16、T19、T21–T29）继续有效。

| # | 场景 | 文案 | 单条化处置 |
| --- | --- | --- | --- |
| T1 | 卡片标题 extra（可编辑） | ~~`{n} 条 · 可编辑`~~ | ❌ 作废（不显示条数 → §12.4 T30） |
| T2 | 卡片标题 extra（只读） | ~~`{n} 条 · 只读`~~ | ❌ 作废（不显示条数 → §12.4 T30） |
| T3 | 恒常口径说明 | 校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。 | 保留 |
| T4 | 校验已关闭提示条 | WiFi 校验已关闭，白名单暂不参与打卡判定 | 保留 |
| T5 | 校验开启但白名单为空 | WiFi 校验已开启但白名单为空，将无人能通过 WiFi 校验 | 保留（并成为「清除后」提示 → §12.5）；**（v1.1 评审闭环）该态显示升级为强警示 T40（§12.13.1），T5 保留为轻量版痕迹** |
| T6 | 展示态 BSSID 未填 | BSSID 未填 | 保留 |
| T7 | 空态（可编辑） | 尚未配置白名单 | **修订** → §12.4 T31（「尚未设置 WiFi 白名单」+ [设置 WiFi]） |
| T8 | 空态（只读） | 未配置 | 保留 |
| T9 | 只读态保留说明 | 白名单需现场抓取 SSID 后维护；改白名单请走 PC 端。 | 保留 |
| T10 | 只读态 ActionBar note | 当前身份只能查看打卡规则，保存需管理员权限 | 保留 |
| T11 | 新增按钮 | ~~+ 添加白名单~~ | ❌ 作废（改为 [设置 WiFi] → §12.4 T32） |
| T12 | 编辑态 SSID placeholder | 如 ST001-Express | 保留 |
| T13 | 编辑态 BSSID placeholder | 如 AC:84:C6:00:00:03（可留空） | 保留 |
| T14 | 行内错误：SSID 空 | 请填写 WiFi 名称（SSID） | 保留 |
| T15 | 行内错误：SSID 超长 | WiFi 名称最长 32 个字符 | 保留 |
| T16 | 行内错误：BSSID 非法 | MAC 地址格式应为 AA:BB:CC:DD:EE:FF | 保留 |
| T17 | 行内错误：重复 | ~~该 WiFi 已存在，请勿重复添加~~ | ❌ 作废（每站仅一条，无重复场景） |
| T18 | ActionBar note：多条未填 | ~~有 {n} 条白名单未填写 WiFi 名称~~ | ❌ 作废（至多一条，无需汇总） |
| T19 | ActionBar note：有未完成编辑行 | 有 1 条白名单正在编辑，请先完成或取消 | 保留（单条化后此为其唯一 `note` 场景） |
| T20 | 条数上限（按钮下方） | ~~单站白名单最多 20 条~~ | ❌ 作废（上限恒为 1） |
| T21 | 删除确认标题 | 删除白名单 | **修订** → §12.4 T35（「清除 WiFi 白名单」） |
| T22 | 删除确认正文 | 删除「{ssid}」后，该 WiFi 将不再通过校验。确定删除？ | **修订** → §12.4 T36 |
| T23 | 删除确认按钮 | 删除（危险样式）/ 取消 | **修订** → §12.4 T37（清除 / 取消） |
| T24 | 读取当前 WiFi（预留态按钮） | 读取当前 WiFi（暂不支持） | 保留（落位修订 → §12.5） |
| T25 | 读取当前 WiFi 点击提示 | 当前版本需手动输入 WiFi 名称：安卓壳暂不提供自动读取 | 保留 |
| T26 | 读取按钮下方说明 | 当前版本请手动输入 SSID 与 BSSID。 | 保留 |
| T27 | **移除**的旧文案 | ~~白名单需现场抓取 SSID 后维护，移动端仅查看；改白名单请走 PC 端。~~（仅在只读态以 T9 形式保留前半句，删除"请走 PC 端"的默认口径） | 保留（原判断不变） |
| T28 | 保存成功 | 打卡规则已保存（沿用现有） | 保留 |
| T29 | 「完成」/「取消」（行内） | 完成 / 取消 | 保留 |

### 7.2 站点管理

> **（v1.1）本组与白名单口径修订无关，本次不动。**

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

> **（v1.1）单条化后本表整体沿用**；仅「新增行后键盘未弹出」一行改为「进入编辑态（设置/修改）后键盘是否自动弹出」的判断，结论不变（不强制聚焦）。

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
| 触控 ≥44×44 | 行内 ~~「编辑」「删除」~~ → **「修改」「清除」** `min-height/min-width: var(--touch-min)`；~~「+ 添加白名单」~~ → **「设置 WiFi」**、**「读取当前 WiFi」**、「取消/完成」`min-height: var(--touch-min)`；`StationPicker` 列表项 48px；列表项整体 ≥100px（**（v1.1）单条化不改变触控下限，见 §12.8**） |
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

### 10.1 打卡规则 · WiFi 白名单 → **单条化后补项见 §12.9**（下列原作废项以 ~~删除线~~ 保留痕迹）

- [ ] ~~卡片标题 extra 在 ADMIN 下为 `{n} 条 · 可编辑`，非 ADMIN 下为 `{n} 条 · 只读`~~ ❌ 作废（不显示条数 → §12.9）
- [ ] 非 ADMIN 时列表**不渲染** ~~「编辑/删除」~~ → **「修改/清除」** 按钮（不是置灰），且不渲染 ~~「+ 添加白名单」~~ → **「设置 WiFi」**
- [ ] 恒常说明存在且文案准确：**判定只比对 SSID（区分大小写），BSSID 仅留痕**（F4）
- [ ] 「读取当前 WiFi」为预留态：文案含「暂不支持」；点击**不发起读取**、**不预填任何值**、提示"需手动输入"
- [ ] 页面上**不存在**"已读取/模拟/当前 WiFi 为…"等任何伪装真实能力的字样
- [ ] 校验开关关闭时：卡顶出现提示条「WiFi 校验已关闭，白名单暂不参与打卡判定」，**列表仍可编辑**
- [ ] 校验开启且白名单为空时：出现 warning 提示条，**保存按钮未因此被禁用**
- [ ] 空态文案：可编辑=「尚未设置 WiFi 白名单」（修订）+ [设置 WiFi]，只读=「未配置」；两者都不含"失败/错误/网络"
- [ ] SSID 必填、超长（>32）、BSSID 非法 MAC 三类行内错误均就近展示；~~同站 SSID 重复~~ ❌ 作废
- [ ] ~~多条未填 SSID 时 ActionBar `note` 给出汇总数~~ ❌ 作废（单条化后至多一条 → §12.9）
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
| ④ | 「一驿站可能配多个 AP（多条白名单）」+ 需要"重复/超长/非法 MAC"提示 | 后端（Mock）`wifiList` 校验**只有**"数组 + ssid 非空"，**无长度上限 / 无 MAC 格式 / 无重复校验**；写入 `ssid` trim、`bssid` 可空 | 前端新增的 32 字符 / MAC 格式 / 20 条 / 重复 四项属**前端先行约束**，与**当时的**后端不对称 → 见 §11.2 裁决项 ②。**（v1.1 更新）**「一驿站配多个 AP」的前提**已被用户口径变更推翻**：改为**每站仅一条** → 20 条上限与重复两项**作废**，前端先行约束收敛为 32 字符 / MAC 格式（见 §12.6 / §12.7）。**（v1.1 评审闭环）** 收敛后的 32 字符 / MAC 格式 + 条数 ≤1 **真实后端已同口径落地**（§12.11）；**Mock 仍未同步**（→ §12.13 R-Mock，已知差异，本批不改 Mock）。 |
| ⑤ | 「判定只按 SSID，bssid 仅留痕」 | 正确，且更精确：比对是 `w.ssid === wifiSsid`（**区分大小写**） | §7 T3 文案已写明"区分大小写"，避免用户以为不敏感 |

### 11.2 需主智能体裁决的点

| # | 事项 | 选项与推荐 |
| --- | --- | --- |
| ① | **站点管理的员工名册数据源**（同一份设计有两种口径，字段不完全重叠） | **选项 A（推荐）** `GET /employees?stationId=`：名册**完整**（含 `status=0` 已停用者），有 `role`/`status` 可支撑"角色 + 在职状态"列；代价：点开 `/boss/hr/:employeeId` 可能落到该页既有 `9301`（无档案）错误态。<br>**选项 B** `GET /hr/profiles?stationId=`：与详情页 VO 同源、点开必不 404；代价：**名册不全**（只有已建人事档案的员工出现，无档案者被 `filter(Boolean)` 丢弃）、**无 `role`**。<br>**推荐 A**（"站点有哪些人"是组织事实，不该由建档进度决定），并把"无档案者点开落 9301"作为已知限制记录、`TODO(扩展)` 待后端补 `hasProfile` 后隐藏箭头。**请裁决。** |
| ② | ~~**WiFi 白名单校验口径是否下沉到后端**~~ → **已落地（无待裁决，v1.1 评审闭环）** | ~~现状仅校验 `ssid` 非空；后端补齐列为未决~~。**实测已落地**：后端 `AttendanceWifiValidator` 已补齐 **ssid 1–32 字符 / BSSID MAC（大小写不敏感）/ 去重（区分大小写）/ 条数 ≤1** 四项，接入 `AttendanceRuleServiceImpl`（L186-189 校验、L258-261 归一），契约固化于 `api.md` §8（§8.1 约束 / §8.2 失败语义）→ **对照见 §12.11**。错误码全走 `ErrorCode.BAD_REQUEST(400)`（**未新增码值**）。**本项不再需要裁决。** |
| ③ | ~~**白名单条数上限（20）与 SSID 长度上限（32）的取值确认**~~ → **已收敛（v1.1）** | 20 条上限**已作废**（每站一条）；SSID 上限**保留为 32**，并**勘误为「32 个字符」**——IEEE 802.11 的客观上限是 **32 octets（字节）**，本实现按**字符数**校验（后端 `ssid.length() > 32`、前端 `maxlength=32` / `text.length > 32`），多字节 SSID 下与 802.11 不完全一致，属**已知取舍**（登记于 §12.13 R-Unit）。~~后端是否补 `wifiList.length <= 1` 约束~~ → **已落地**（§12.11）。**本项不再需要裁决。** |
| ④ | **「读取当前 WiFi」预留态的交互形态** | 本规范取"可点 + 明确拒绝"（不置灰）；备选为"置灰 disabled"。两者取舍见 §3.3 理由。若主智能体更倾向"任何不可用能力都不渲染按钮"，可改为纯 `.tip` 说明。**请确认。** |

---

## 12. 口径修订：白名单每站仅一条（v1.1）

> 版本 v1.1 ｜ 日期 2026-09-26 ｜ 作者：UI/UX 设计师（`express-station-ui-ux-designer`）
> 触发：**用户口径变更（已确认）** —— 「WiFi 白名单只有管理员可设置，且每个站点**指定一个**。」
> 性质：本节为**方案阶段产物**，按 P0.6 / L8 须先经技术评审工程师评估后方可报主智能体审批。**不写实现代码、不改 `.vue`/`.js`/后端、不执行 git。**
> 追溯：原文 §2–§10 受影响条目已就地标注作废/修订并指向本节。**本节为最终口径。**

### 12.1 变更摘要

| # | v1.0（多条） | v1.1（单条） | 依据 |
| --- | --- | --- | --- |
| C1 | 白名单可增删多条，上限 20 | **每站至多一条**（0 或 1） | 用户口径「每站指定一个」 |
| C2 | 卡 extra 显示 `N 条 · …` | **去掉条数**，仅显示 `可编辑` / `只读` | 单条后计数无信息量，且暗示"可多条" |
| C3 | `[+ 添加白名单]` 多行模式 | **`[设置 WiFi]`（未配置态唯一动作）** | 单条化 |
| C4 | `[编辑][删除]` 行 | **`[修改][清除]` 行** | 语义从"多行之一"变"唯一一条" |
| C5 | 删除确认（删除其中一行） | **清除确认（置空唯一一条）** | 二次确认机制保留 |
| C6 | 重复 / 多条未填 / 20 条上限 3 类校验 | **全部作废**（无适用场景） | 单条化 |
| C7 | 判定口径、权限分支、降级口径 | **不变**（仅落位/文案微调） | 见 §12.2 |

### 12.2 数据口径与红线确认

**数据模型（描述，非代码）：** 前端本地白名单仍表现为数组 `wifiList`，但**长度恒 ∈ {0, 1}**；`PUT /attendance/rule` 请求体结构**不变**（仍传 `wifiList` 数组，0 或 1 条）。~~**不动接口契约、不改 Mock、不改后端**。单条化是**纯前端 UI + 约束**收敛。~~ ❌ **此表述已作废（v1.1 评审闭环）**：**接口契约已固化**（`api.md` §8）、**后端已实现**（`AttendanceWifiValidator` + `AttendanceRuleServiceImpl` 接入校验与归一）——单条化**不止前端**，而是**契约 + 后端校验 + 前端 UI** 三层同批落地（**对照见 §12.11**）。

**三条不变声明（逐条对应任务口径）：**

1. **权限分支不变：** 本端（`apps/boss-h5` 驿站精灵）`BOSS_ROLES = [ROLE.ADMIN]`，登录者**恒为 ADMIN** → 编辑为主路径；只读分支作为**防御位**保留（零成本、前向兼容），**但不得据此宣称「站长可在移动端配置」**（用户口径：只有管理员可设置）。判据仍取 `useAuthStore().isAdmin`（判据单点化），不在页内自判 `role`。
2. **降级态不变：** 「读取当前 WiFi」仍为**可点 + 明确告知需手动输入 + 不预填**（安卓壳 `HrmBridge.getWifiInfo` 未实现，F6）；仅**落位**随单条化调整（§12.5）。
3. **站点管理不动：** 原 §2/§3.5/§4.2/§5.2/§7.2/§10.2 的「站点管理只读骨架」**本次全文不动**，与本修订无关。

> **（v1.1 评审闭环）本批实际改动范围（修正原「纯前端」表述）：** ① **前端 UI**（`attendanceRule.vue` / `wifiWhitelist.js` 由多条改单条，**待另派前端落地**，落地前存在「前端多行 ↔ 后端/契约单条」不一致窗口，见 §12.14 交付范围声明）；② **接口契约**（`api.md` §8，已固化）；③ **后端校验**（`AttendanceWifiValidator`，已实现并接入）；④ **Mock 未同步**（`routes/attendance.js:95-99` 仍只校验「数组 + ssid 非空」）→ 登记为已知差异（§12.13 R-Mock）。

### 12.3 修订后交互、DOM 与状态矩阵

**12.3.1 修订后主流程**

```
WiFi 白名单卡
  ├─ 卡标题 extra：`可编辑` / `只读`（ADMIN / 非 ADMIN，不再显示条数）
  ├─ warning 提示条（互斥取一条，优先级：校验关闭 > 校验开启但为空）
  ├─ 恒常口径说明（保留，T3）
  ├─ 三态之一（互斥）：
  │    ├─ 未配置态：说明「尚未设置 WiFi 白名单」 + [设置 WiFi]（仅可编辑）
  │    ├─ 已配置态：单条 cell（SSID 主 / BSSID 次） + [修改][清除]（仅可编辑）
  │    └─ 编辑中态：SSID 字段 + BSSID 字段 + [读取当前 WiFi（暂不支持）] + [取消][完成]
  ▼
底部 ActionBar：保存规则（disabled/可用/提交中/失败）  ← 复用不变
  ▼
成功：showSuccessToast('打卡规则已保存')；失败：由 http 层统一弹（页内不重复）
```

**12.3.2 修订后 DOM（替换 §4.1，Token 全沿用）**

```html
<div class="section-title">
  WiFi 白名单
  <span class="section-title__extra">{{ isAdmin ? '可编辑' : '只读' }}</span>
</div>
<div class="card">
  <!-- warning 提示条（互斥取一条；优先级：校验关闭 > 校验开启但为空 > 多条历史）
       v1.1 评审闭环：第 2 条在该态升级为强警示 T40（left-icon="warning-o"、不可关闭），见 §12.13.1；
       第 3 优先级 T39（多条历史）追加于此链尾，见 §12.12。 -->
  <van-notice-bar v-if="!form.enableWifi" left-icon="info-o"
    text="WiFi 校验已关闭，白名单暂不参与打卡判定"
    color="var(--color-warning)" background="var(--color-warning-surface)" />
  <van-notice-bar v-else-if="!hasWifi" left-icon="warning-o"
    text="WiFi 校验已开启但白名单为空：本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。请设置白名单，或关闭 WiFi 校验。"
    color="var(--color-warning)" background="var(--color-warning-surface)" />

  <!-- 恒常口径说明（保留） -->
  <p class="tip">校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。</p>

  <!-- A. 未配置态 -->
  <template v-if="!hasWifi && !editing">
    <p class="wifi-empty">尚未设置 WiFi 白名单</p>
    <van-button v-if="isAdmin" block plain class="wifi-set" @click="startEdit(emptyDraft)">
      设置 WiFi
    </van-button>
  </template>

  <!-- B. 已配置态（唯一一条） -->
  <van-cell v-else-if="hasWifi && !editing" :title="wifiList[0].ssid"
    :label="wifiList[0].bssid ? `BSSID ${wifiList[0].bssid}` : 'BSSID 未填'">
    <template v-if="isAdmin" #right-icon>
      <button type="button" class="wifi-act" @click="startEdit(existingDraft)">修改</button>
      <button type="button" class="wifi-act wifi-act--danger" @click="askClear">清除</button>
    </template>
  </van-cell>

  <!-- C. 编辑中态（未配置 / 已配置共用同一表单；物理上仅一条） -->
  <template v-else-if="editing">
    <van-field v-model="draft.ssid" label="SSID" required maxlength="32"
      placeholder="如 ST001-Express" :error-message="draft.ssidError" />
    <van-field v-model="draft.bssid" label="BSSID" maxlength="17"
      placeholder="如 AC:84:C6:00:00:03（可留空）" :error-message="draft.bssidError" />
    <!-- 降级态：紧贴唯一输入行（§3.3 / §12.5） -->
    <van-button v-if="isAdmin" plain size="small" class="wifi-read" @click="onReadWifi">
      读取当前 WiFi（暂不支持）
    </van-button>
    <p class="tip">当前版本请手动输入 SSID 与 BSSID。</p>
    <div class="wifi-edit-actions">
      <van-button plain size="small" @click="cancelEdit">取消</van-button>
      <van-button type="primary" size="small" @click="commitEdit">完成</van-button>
    </div>
  </template>
</div>
```

> `hasWifi = wifiList.length > 0`。样式取值：`.wifi-set` 沿用原 `.wifi-add`（`min-height: var(--touch-min)`、`color: var(--color-primary)`、`border-color: var(--color-primary-icon)`）；`.wifi-act` / `.wifi-act--danger` / `.wifi-edit-actions` / `.wifi-empty` / `.wifi-read` / `.tip` 取值**全部沿用 §4.1 原表**。

**12.3.3 修订后状态矩阵（八态，逐态给出展示与可用动作）**

| # | 状态 | 判定 | 展示 | 可用动作（ADMIN） | 可用动作（只读防御位） |
| --- | --- | --- | --- | --- | --- |
| 1 | **未配置** | `wifiList.length === 0` 且 `!editing` | 说明「尚未设置 WiFi 白名单」；无 cell；无编辑表单 | `[设置 WiFi]` → 进编辑中态 | **无**（不渲染按钮）；沿用 T8「未配置」 |
| 2 | **已配置** | `length === 1` 且 `!editing` | 单条 `van-cell`：SSID(主) / `BSSID …` 或「BSSID 未填」(次) | `[修改]` → 编辑中态；`[清除]` → 清除确认 | **无**（不渲染按钮，不留空位） |
| 3 | **编辑中** | `editing === true` | SSID 字段(必填,≤32) + BSSID 字段(选填,MAC) + `[读取当前 WiFi（暂不支持）]` + `[取消][完成]` | 字段可输；`[完成]`(校验通过才收起)；`[取消]`；`[读取…]`（仅提示） | 不适用（只读不进入编辑态） |
| 4 | **保存中** | `saving === true` | ActionBar 主按钮 `loading`；`submitting` 置位 | 全按钮禁用（防连点） | 不适用 |
| 5 | **失败** | http 层返回错误 | http 层统一弹错（页内不叠加） | 表单保持原样，可改后重试 | 不适用 |
| 6 | **校验错误** | 行内 `error-message` 非空 | 字段下方就近展示（T14/T15/T16 三类）；若仍有未完成编辑行 → ActionBar `note`(T19) | 修正字段；保存禁用直至无阻断错误 | 不适用 |
| 7 | **校验开关关闭** | `enableWifi === false` | 卡顶 `van-notice-bar`（T4）；extra 仍为 `可编辑` | **配置区仍可编辑**（不隐藏、不置灰、不锁编辑） | 同上（不渲染写按钮） |
| 8 | **只读防御位** | `auth.isAdmin !== true` | 卡 extra `只读`；未配置→T8；已配置→单条 cell 无按钮；保留 T9 说明 | 不适用 | 仅查看；ActionBar 主操作禁用 + `note`(T10) |

> 状态叠加规则：**7 与（1/2/3）叠加**（开关关闭不改变配置区的三态结构，仅加提示条）；**（4/5）为保存过程的瞬时态**，作用于 ActionBar；**6 与 3 叠加**（校验错误只可能出现在编辑中态）。**「校验开启且为空」为状态 1 的派生提示**（该态提示条 **T40**，原 T5 升级，见 §12.13.1；含"清除后置空"路径，见 §12.5），**不阻断保存**。**（评审闭环）另叠加「多条历史」派生态（T39，§12.12）**，提示条按 T4 > T40 > T39 互斥取一条。

### 12.4 修订后文案清单（新编号续 T30+）

| # | 场景 | 文案 | 备注 |
| --- | --- | --- | --- |
| T30 | 卡片标题 extra | `可编辑` / `只读` | 替代作废的 T1/T2（**去掉条数**） |
| T31 | 未配置态说明 | 尚未设置 WiFi 白名单 | 修订 T7（"配置"→"设置"） |
| T32 | 未配置态动作按钮 | 设置 WiFi | 替代作废的 T11（`+ 添加白名单`） |
| T33 | 已配置态动作 | 修改 | 替代 T1.x「编辑」语义 |
| T34 | 已配置态动作 | 清除 | 替代「删除」语义 |
| T35 | 清除确认标题 | 清除 WiFi 白名单 | 修订 T21（「删除白名单」） |
| T36 | 清除确认正文 | 清除后本站将不再配置 WiFi 白名单；若「WiFi 校验」已开启，将无人能通过 WiFi 校验。确定清除？ | 修订 T22 |
| T37 | 清除确认按钮 | 清除（危险样式）/ 取消 | 修订 T23 |
| T38 | 编辑中态上下文标识（可选） | 设置 WiFi / 修改 WiFi | 用于卡片内小标题，区分进入路径 |

**作废清单（保留痕迹，不静默删除）：**

| 原编号 | 原文案 | 作废理由 |
| --- | --- | --- |
| T1 / T2 | `{n} 条 · 可编辑` / `{n} 条 · 只读` | 单条化后无条数语义（→ T30） |
| T7（原文案） | 尚未配置白名单 | 修订为 T31 |
| T11 | + 添加白名单 | 多条模式取消（→ T32） |
| T17 | 该 WiFi 已存在，请勿重复添加 | 每站仅一条，无重复场景 |
| T18 | 有 {n} 条白名单未填写 WiFi 名称 | 至多一条，无需汇总 |
| T20 | 单站白名单最多 20 条 | 上限恒为 1，计数语义消失 |
| T21/T22/T23（原文案） | 删除白名单 / 删除「{ssid}」… | 语义由"删一行"变"清除唯一一条"（→ T35/T36/T37） |

### 12.5 关键交互细则（清除 / 编辑 / 降级落位）

**① 清除（置空）交互 —— 需要设计，本节给出：**

- 触发：已配置态 `[清除]`（次要按钮，`--color-danger`）。
- 二次确认（机制保留，采用 F10 既有函数式 Dialog）：
  `showConfirmDialog({ title: '清除 WiFi 白名单', message: '清除后本站将不再配置 WiFi 白名单；若「WiFi 校验」已开启，将无人能通过 WiFi 校验。确定清除？', confirmButtonText: '清除', confirmButtonColor: 'var(--color-danger)' })`（T35/T36/T37）。
- 确认后：本地 `wifiList = []`（**不立即请求**），`dirty` 命中 → 保存按钮变可用，由页面统一 `PUT`。
- **清除后与开关联动（硬约束）：** 若此时 `enableWifi === true` → 落回状态 1（未配置），并**展示强警示 T40**「WiFi 校验已开启但白名单为空：本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。请设置白名单，或关闭 WiFi 校验。」；**提示但不阻断保存**（沿用原 §3.4 口径；强提示方案见 §12.13.1）。
- 若 `enableWifi === false` → 仅提示 T4（校验已关闭）。

**② 编辑（单条）收敛：**

- 未配置态 `[设置 WiFi]` 与已配置态 `[修改]` **进入同一编辑表单**（T38 小标题区分）。
- 未配置态进入后 `[取消]` → **回到未配置态（不产生空记录）**；`[完成]` 校验通过才落为已配置。
- 已配置态进入后 `[取消]` → 回原值。
- **原 §3.1.5「若有未完成编辑行先收起」的拦截逻辑作废**：物理上只可能有一条编辑行，"同时刻一条"由约束降为事实，无需拦截。
- 编辑中态**不显示** `[清除]`（避免误清正在编辑项）。

**③ 「读取当前 WiFi」落位（降级态不变，落位调整）：**

- **新落位：紧贴唯一那条输入行** —— 位于编辑中态 **BSSID 字段下方、`[取消][完成]` 之前**；紧随其下为 `.tip`（T26）。
- **不再常驻展示态**：需先点 `[设置 WiFi]` / `[修改]` 进入编辑态才可见（避免在无输入行的态里出现无落点的按钮）。
- 其余不变：文案 T24、点击 T25、不预填、不 loading、不出现"已读取/模拟"字样（§3.3 表逐条保留）。

### 12.6 校验规则与错误分工（修订后）

| 类型 | 规则 | 文案 | 位置 | 处置 |
| --- | --- | --- | --- | --- |
| 行内 | SSID 为空 | T14 请填写 WiFi 名称（SSID） | `error-message` | 保留 |
| 行内 | SSID 超长（>32） | T15 WiFi 名称最长 32 个字符 | `error-message` | 保留 |
| 行内 | BSSID 非法 MAC | T16 MAC 地址格式应为 AA:BB:CC:DD:EE:FF | `error-message` | 保留 |
| 行内 | ~~同站 SSID 重复~~ | ~~T17~~ | — | ❌ **作废**（每站仅一条，无重复场景） |
| 汇总 | ~~多条未填~~ | ~~T18~~ | — | ❌ **作废**（至多一条） |
| 汇总 | 有未完成编辑行 | T19 有 1 条白名单正在编辑，请先完成或取消 | ActionBar `note` | 保留（唯一 `note` 场景） |
| 上限 | ~~20 条上限~~ | ~~T20~~ | — | ❌ **作废**（上限恒为 1） |

> **前端先行约束收敛：** 原 4 项（32 字符 / MAC 格式 / 20 条上限 / 重复）→ 修订后**仅剩 2 项**（32 字符 / MAC 格式），均**保留**；**（v1.1 评审闭环）后端已同口径落地**（§12.11），不再存在前后端不对称。

### 12.7 待裁决项收敛（对 §11.2 的修订）

> **（v1.1 评审闭环）本表 ②③ 两项均已闭环，不再是待裁决项**：后端下沉已随本批实现，契约固化于 `api.md` §8 → 落地对照见 **§12.11**。仅 ①④ 维持原裁决/确认请求（与单条化无关）。

| 原裁决项 | v1.1 收敛 |
| --- | --- |
| ② 后端是否补齐 `wifiList` 校验 | **已落地（无待裁决）**：~~仅剩 32 字符 + MAC 格式需后端对齐~~ → 后端已补齐 **ssid 1–32 字符 / BSSID MAC / 去重（区分大小写）/ 条数 ≤1** 四项（`AttendanceWifiValidator`），契约固化 `api.md` §8 → 对照 **§12.11**。~~新增候选：后端补 `wifiList.length <= 1`~~ → **已落地**（`MAX_ENTRIES=1`）。~~请裁决…是否纳入本批~~ ❌ 该未决表述**已作废**（已纳入本批并实现）。 |
| ③ 20 条上限与 32 字符确认 | **20 条上限作废**；**SSID 上限保留 32 并勘误为「32 个字符」**——IEEE 802.11 客观上限为 **32 octets（字节）**，实现按**字符数**校验（`String.length()`；多字节 SSID 下与 802.11 不完全一致，属已知取舍 → §12.13 R-Unit）。**本项无待裁决。** |
| ① 站点名册数据源 | 与本次修订无关，维持原裁决请求（§11.2①） |
| ④ 「读取当前 WiFi」交互形态 | 与本次修订无关（仅落位调整，形态维持"可点 + 明确拒绝"，§11.2④） |

### 12.8 Design Tokens：零新增（重申）

单条化**不引入任何新 Token**：三态复用的仍是 `--surface-card` / `--text-1` / `--text-2` / `--text-3` / `--color-primary` / `--color-primary-icon` / `--color-danger` / `--color-warning` / `--color-warning-surface` / `--touch-min` / `--sp-*` / `--r-*` / `--fs-*` 等既有变量（见 §6）。`[设置 WiFi]` 按钮直接复用原 `.wifi-add` 的 Token 取值。**无新造色值、无 Token 外字面量、无紫色。**

### 12.9 视觉走查清单补项（单条化专项，供 §10.1 叠加）

- [ ] 卡片标题 extra **不含条数**（不出现 `N 条` / `1 条` / `共 n 条` 等计数文案）
- [ ] 页面**不出现**任何"添加多条"暗示：无 `+ 添加白名单`、无「可添加多条 / 支持多个 AP / 最多 N 条」字样
- [ ] 白名单**至多渲染一条**记录；构造 `wifiList.length === 2` 的异常入参时，UI **只取首条**渲染（不出现两行），且**提交恒 0 或 1 条**（§12.12①②）
- [ ] 未配置态存在唯一动作 `[设置 WiFi]`，且点击进入编辑表单
- [ ] 已配置态动作为 `[修改]` + `[清除]`（**不出现** `[编辑][删除]` 旧字样）
- [ ] `[清除]` 走 `showConfirmDialog`，标题含「清除」，正文含"校验已开启将无人能通过"风险提示，确认按钮为危险样式
- [ ] 清除后若校验开启：**出现强警示 T40 且保存按钮未被禁用**（不阻断）
- [ ] 「读取当前 WiFi」在编辑中态**紧贴 BSSID 字段下方**出现；**展示态/未配置态不出现**该按钮
- [ ] 校验错误**只出现三类**（SSID 空 / 超长 / BSSID 非法）；**不出现**"重复"与"多条未填"文案
- [ ] 只读防御位：未配置/已配置均**不渲染**任何写按钮（含 `[设置 WiFi]` / `[修改]` / `[清除]`）
- [ ] 权限口径未被误解：文案**不出现**「站长可配置 / 站长可修改」等表述
- [ ] 触控区仍 ≥44×44；对比度仍 ≥4.5:1（§9.1 不变）
- [ ] **（评审闭环补项）** 加载到 `wifiList.length > 1` 历史数据：**只渲染首条** + 卡顶出现 **T39** 提示（§12.12）
- [ ] **（评审闭环补项）** 「校验开启且白名单为空」态：卡顶为**强警示 T40**（`left-icon="warning-o"`、不可关闭），**非**轻量 T5 文案（§12.13.1）
- [ ] **（评审闭环补项）** 该态下点保存且 `dirty` 命中：弹 **T41–T43** 二次确认；「仍要保存」放行、「返回设置」回表单；**保存按钮未被禁用**（不阻断，§12.13.1）
- [ ] **（评审闭环补项）** 校验失败 400 文案与前端行内文案**各自真源**（§12.14③）；页内**不叠加**第二条提示

### 12.10 修订对照表（原条目 → 新条目 → 原因）

| 原条目（v1.0） | 新条目（v1.1） | 处置 | 原因 |
| --- | --- | --- | --- |
| §1.1 G1「增/删/改、新增一条」 | G1（就地修订） | 修订 | 单条化 |
| §1.2 做清单「新增行 / 删除确认」 | 做清单（就地修订） | 修订 | 单条化 |
| §3.1 主流程「列表 / + 添加白名单 / N 条」 | §12.3.1 | 作废+取代 | 单条化 |
| §3.1.1 展示态行 | §12.3.2 B | 保留结构、改动作 | 动作改 [修改][清除] |
| §3.1.2 编辑态行 | §12.5② | 保留、简化 | 「同时刻一条」由约束降为事实 |
| §3.1.4 新增行 | §12.5① / §12.3.2 A | **作废** | 无"追加多条" |
| §3.1.5 删除确认 | §12.5① | 修订 | 改「清除确认」，机制保留 |
| §3.1.6 错误分工（重复 / 多条汇总） | §12.6 | 部分作废 | 重复、多条汇总无场景 |
| §3.1.7 校验表（重复 / 多条未填 / 20 条） | §12.6 | 部分作废 | 同上 |
| §3.2 权限分支 | §12.2 ①、§12.3.3 | **保留** | 权限口径不变 |
| §3.3 降级分支 | §12.5③ | 保留、改落位 | 能力口径不变 |
| §3.4 联动（extra / 开且为空） | §12.3.3、§12.5① | 修订 | extra 去条数；空态叠加清除路径 |
| §4.1 DOM | §12.3.2 | 取代 | 单条化 DOM |
| §5.1 状态矩阵 | §12.3.3 | 取代 | 八态矩阵 |
| §6 Tokens | §12.8 | **保留** | 零新增 |
| §7.1 文案 T1/T2/T7/T11/T17/T18/T20–T23 | §12.4 T30–T38 | 修订/作废 | 见 §12.4 |
| §7.2 站点文案 S1–S20 | — | **不动** | 与本修订无关 |
| §8.3 键盘处理 | 就地标注 | 保留 | 结论不变 |
| §9.2 触控（+ 添加白名单） | 就地标注、§12.9 | 保留 | 触控下限不变 |
| §10.1 走查清单 | §12.9 | 作废+补项 | 单条化专项 |
| §10.2 站点走查 | — | **不动** | 与本修订无关 |
| §11.2 裁决项 ②③ | §12.7 / §12.11 | 收敛→**闭环** | 后端下沉已落地，见 §12.11 |
| **§0 F1「只读 + `wifiText`」引证**（评审闭环） | F1（就地重写） | **勘误** | 磁盘实况为多条可编辑；`wifiText` 属 hrm-demo 跨文件误引 |
| **§12.2「不动契约/后端」表述**（评审闭环） | §12.2（就地作废） | **作废** | 契约已固化（`api.md` §8）、后端已实现（`AttendanceWifiValidator`） |
| **旧数据 `wifiList>1` 边界**（评审闭环） | §12.12 | **新增** | 加载 / 提交 / 与后端 400 关系三口径 |
| **NFR / 文案差异 / 32 字符勘误**（评审闭环） | §12.13、§12.14 | **新增** | 评审必改项 5 / 6 |

### 12.11 后端下沉落地对照（设计约束 ↔ 契约条款 ↔ 实现位置）

> **（v1.1 评审闭环新增）** 本小节逐条给出「设计约束 → 契约条款（`api.md` §8）→ 后端实现位置」的对应关系，**消除原「纯前端 / 不动契约·后端」与产物 2/3 的矛盾**（评审必改项 1）。行号为**当前磁盘版本**。

| # | 设计约束（本文档） | 契约条款（`api.md` §8） | 后端实现位置（`hrm-server`） | 状态 |
| --- | --- | --- | --- | --- |
| D1 | 每站**至多一条**（0 或 1，§12.1 C1） | §8.1 `wifiList` 至多 1 条（L797）；§8.2 条数 > 1 message（L815） | `AttendanceWifiValidator.MAX_ENTRIES = 1`（L29）、`validate` 条数判定（L69-71） | ✅ 已落地 |
| D2 | SSID 必填、trim 后 **1–32 字符**（§12.6 T14/T15） | §8.1 `wifiList[].ssid`（L798）；§8.2 空/超长 message（L810-811） | `validate`：`isBlank`（L53-55）+ `length() > SSID_MAX_LEN`（L56-59），`SSID_MAX_LEN=32`（L27） | ✅ 已落地 |
| D3 | BSSID 可空、非空须 **MAC（大小写不敏感）**（§12.6 T16） | §8.1 `wifiList[].bssid`（L799）；§8.2 message（L813） | `BSSID_PATTERN`（L31）、`validate`（L60-63） | ✅ 已落地 |
| D4 | 去重**区分大小写**（与打卡判定同源，F4） | §8.1 去重口径（L801） | `seen.add(ssid)` 不做小写化（L64-67） | ✅ 已落地 |
| D5 | 校验顺序：字段 → 去重 → 条数 | §8.1 校验顺序（L802） | `validate` 循环内先字段+去重、循环后判条数（L48-71） | ✅ 已落地 |
| D6 | 写入归一：`ssid` trim、`bssid` 空串/纯空白 → `null` | §8.1「保存前 trim」「空串/纯空白归一为 null」（L798-799） | `normalize`（L79-89）；接入 `AttendanceRuleServiceImpl.normalize`（L258-261） | ✅ 已落地 |
| D7 | 校验失败错误码：**全走 400**（裁定 B·不新增码值） | §8.2 `body.code = 400`（`ErrorCode.BAD_REQUEST`，L806） | `AttendanceRuleServiceImpl` **L187-189**：`throw new BusinessException(ErrorCode.BAD_REQUEST, wifiError)` | ✅ 已落地 |
| D8 | `enableWifi=true` 且白名单为空 → **fail-open（允许保存）**，前端 warning | §8.2（L817：「允许保存（不阻断）」） | `AttendanceRuleServiceImpl` **L191-193**：**刻意不做 fail-closed 阻断** | ✅ 已落地（口径由主智能体裁定维持） |
| D9 | 权限：仅 ADMIN 可写 | §8 写权限（L791） | `AttendanceController.saveRule` `@RequireRoles({"ADMIN"})` | ✅ 已落地（既有） |

**接入位置（后端）：** 校验入口 `AttendanceRuleServiceImpl` **L186-189** 调用 `AttendanceWifiValidator.validate(...)`；归一入口 `normalize` **L258-261** 调用 `AttendanceWifiValidator.normalize(...)`。

**结论：** 设计侧「单条 / 32 字符 / MAC」约束与契约（`api.md` §8）、后端实现**逐条一致**；**不再存在**「移动端拦了、PC 端 / 直调 API 可绕过」的不对称（该不对称曾由原 §11.2② 提出，**现已闭环**）。**遗留差异**：**Mock 未同步**（`packages/mock/src/routes/attendance.js:95-99` 仍只校验「数组 + ssid 非空」）→ §12.13 R-Mock。

### 12.12 旧数据 `wifiList.length > 1` 处置口径（加载 / 提交 / 与后端 400 的关系）

> **（v1.1 评审闭环新增）** 补齐原 §12.9 仅规定「只取首条渲染」而未规定**提交口径**的缺口（评审必改项 2）。本口径为**实现依据**，前端落地须照此执行。

**事实前提（主智能体实测，供实现与评审复核）：** 线上测试库 `kdyzgl_test.attendance_rule` 共 **8 条规则**，`json_length(wifi_list)` **全部 = 1**，`> 1` 的行 **0 条** → **现网无历史多条数据，该风险当前实际不存在**；本口径仍**固化**，用于防御未来（PC 端直调 / 历史遗留 / 数据迁移产生多条）。

| # | 环节 | 口径 |
| --- | --- | --- |
| ① | **加载** | 若服务端 `GET` 返回 `wifiList.length > 1`：UI **只渲染首条 `wifiList[0]`**（不出现两行），并在卡顶给出提示 **T39**（见下）。 |
| ② | **提交** | 前端**恒提交 0 或 1 条**：`payload.wifiList = form.wifiList.slice(0, 1).map(...)`（未配置 = `[]`）。**正常路径永不产生 `length > 1` 的请求体**。 |
| ③ | **与后端 400 的关系** | 后端对 `wifiList.length > 1` 返回 **400（`WiFi 白名单同一驿站仅允许配置 1 条`）属「兜底防线」**，面向 **PC 端 / 直调 API / 未升级的历史客户端**，**前端正常路径不应触发**。若前端触发了该 400，说明前端收敛（①/②）存在缺陷，**按缺陷处理**，不得以「后端兜底」为由放过。 |

**新增文案（续 §12.4 编号）：**

| # | 场景 | 文案 | 呈现 |
| --- | --- | --- | --- |
| **T39** | 加载到历史多条白名单（`length > 1`） | 检测到本站存在多条 WiFi 白名单（历史数据），当前仅显示第 1 条；保存后将收敛为 1 条。 | 卡顶 `van-notice-bar`（warning 语义，`--color-warning` / `--color-warning-surface` / `left-icon="info-o"`）；**优先级最低**：与 T4（校验关闭）/ T5-T40（开启且为空）冲突时按 **T4 > T40 > T39** 只显示一条 |

> 提示优先级与 §12.3.2 DOM 中 warning 提示条「互斥取一条」一致（现为 T4 > T40），T39 追加为其**第三优先级**；三者在同一时刻**只显示一条**。若走查要求「多条历史提示」必须与「开启且为空」同时可见，则升级为独立 `.notice` 行——**本批按互斥取一条从简**。

### 12.13 风险登记（v1.1 评审闭环补充）

| # | 风险 | 触发条件 | 影响面 | 缓解 / 处置 | 状态 |
| --- | --- | --- | --- | --- | --- |
| **R-A**（可用性·**强**） | `enableWifi=true` 且白名单为空 → **本站所有员工打卡因 WiFi 未命中失败**（错误码 9103） | ADMIN 先开开关后未配名单；或「清除」后置空 | 打卡可用性（全员） | **维持 fail-open**（口径由主智能体裁定保持：不阻断保存，满足「先开开关、后配 WiFi」分步配置）；**UI 强提示方案见 §12.13.1** | **已登记**（口径已裁定）。**实质安全影响（是否构成可用性攻击面）本设计不出结论** → 转网络安全工程师（R24 / P0.5） |
| **R-WifiLegacy**（旧数据 >1） | 历史规则含多条 + H5 加载/保存 | 打卡规则保存 | 见 §12.12（加载取首条 + T39；提交恒 0/1） | **已登记**（现网实测不存在，**防御性固化**） |
| **R-Mock**（Mock 分叉） | 演示（Mock）路径写入 2 条 / 非法 MAC **不报错**，真实后端报 400 | 认知差 / 验收误判 | 本批**不同步 Mock**；**显式声明「本批 Mock 不覆盖该校验，`verify:mock` 不校验该路径」** | **已知差异**（受本批范围约束；后续可另派同步） |
| **R-Copy**（文案两套） | 前端行内（T14/T15/T16）与后端 400 `message`（`api.md` §8.2）同时可见 | 用户困惑 | **主智能体裁定：不强制统一**；登记见 §12.14（前端行内优先，后端为兜底） | **已知差异**（已登记） |
| **R-Unit**（32 字符 ≠ 32 字节） | 非 ASCII / 多字节 SSID | 与 IEEE 802.11 的 32 octets 上限不符 | 勘误为「**32 个字符**」并说明实现按字符数校验（§11.2③ / §12.7） | **已勘误**（低危，已知取舍） |
| **R-Frontend**（前端收敛未交付） | 本批三产物不含前端单条化 | 「前端多行 ↔ 后端/契约单条」不一致窗口 | 另派前端按 §12.3 落地；报审须声明（§12.14④） | **待另派**（已知，范围声明） |

**12.13.1 R-A 强提示方案（评估与理由）**

> **问题：** 原设计在该态仅有一条**轻量** warning 提示条（T5）且不阻断保存；评审要求「必须显式登记可用性风险并给出**强提示**」，并评估「保存成功后再增一次确认 / 或常驻警示」。**评估结论如下。**

- **采纳方案（两层，互补）：**
  1. **常驻强警示（进入即知）—— T40：** 处于「`enableWifi=true` 且白名单为空」时，卡顶提示条文案**升级为 T40**、图标改 `warning-o`、**不可关闭**（不加 `mode="closeable"`），常驻直至该态解除。**T5 文案降级为"轻量版"保留痕迹**（编号不删，注明该态改用 T40）。
  2. **保存前二次确认（落地瞬间再确认一次）—— T41–T43：** 当用户点保存且**本次确有提交**（`dirty` 命中）且提交后仍为该态时，弹 `showConfirmDialog`（F10 既有函数式 Dialog）二次确认；用户可「仍要保存」（放行）或「返回设置」（回表单）。**不阻断**（不置 `formError`、不禁用保存），仅增加一次显式确认。
- **否决方案：** ❌ **「保存成功后再增一次确认」**。理由：确认时数据**已在服务端生效**，无法撤销，二次确认**无实际防护价值**，只会造成「已保存还要再弹一次」的挫败与语义混乱（用户会误以为可取消）；真正的危险点是**提交落地那一刻**，故采用**保存前**确认。
- **为何仍不阻断（维持 fail-open）：** 阻断会卡死合法的「先开开关、后配 WiFi」分步配置路径（§3.4），且口径已由主智能体裁定维持 fail-open。故以「**常驻强警示 + 保存前二次确认**」承担风险沟通，而非硬阻断。
- **Design Tokens：** T40 复用 `--color-warning` / `--color-warning-surface`；T41–T43 弹窗复用 `showConfirmDialog` 既有样式与 `--color-warning`/`--color-danger`（确认按钮可用警告/危险色）。**零新增 Token。**

**新增文案（续 §12.4 / §12.12 编号）：**

| # | 场景 | 文案 | 备注 |
| --- | --- | --- | --- |
| **T40** | 常驻强警示（`enableWifi=true` 且白名单为空） | WiFi 校验已开启但白名单为空：本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。请设置白名单，或关闭 WiFi 校验。 | **替代 T5 在该态的显示**（T5 保留为轻量版痕迹）；`left-icon="warning-o"`、不可关闭；对应 §12.3.2 DOM 中 `v-else-if="!hasWifi"` 的 `text` 与图标**就地按本行落地** |
| **T41** | 保存前二次确认标题（该态提交时） | 确认保存？白名单为空 | `showConfirmDialog` |
| **T42** | 保存前二次确认正文 | WiFi 校验已开启但白名单为空，保存后本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。确定仍要保存？ | 同上 |
| **T43** | 保存前二次确认按钮 | 仍要保存 / 返回设置 | 主操作「仍要保存」（放行）；次操作「返回设置」（回表单、默认焦点）；**不阻断** |

### 12.14 NFR 声明与已知差异登记（评审闭环）

**① 性能无关性（NFR）：** 本变更为 **O(1) 纯逻辑校验**（`wifiList` 长度 ≤ 1）+ **本地表单态**，**无性能目标**；校验为常数级，对页面加载与保存**无性能影响**。

**② 可观测性（NFR）：** 校验失败**仅返回 400 + 文案**（沿用统一响应 `{code,message,data}`），**不新增监控埋点**；错误由**既有 http 层统一承接**（页内不叠加第二条提示），与现状口径一致。

**③ 已知差异登记 —— 前后端两套校验文案：**

| 维度 | 前端行内文案（**优先**） | 后端 400 `message`（**兜底**） |
| --- | --- | --- |
| 真源 | `apps/boss-h5/src/utils/wifiWhitelist.js`（`SSID_EMPTY_TEXT` / `SSID_TOO_LONG_TEXT` / `BSSID_INVALID_TEXT`，L18-20） | `api.md` §8.2（`AttendanceWifiValidator` L50/54/58/62/66/70） |
| 受众 | 表单即时反馈（用户直面） | API 消费方 / 直调 |
| 裁定 | **主智能体裁定：不强制逐字统一**；用户可见优先**前端行内文案**，后端 `message` 作为**兜底**（非用户直读路径） | 同左 |

**④ 交付范围声明（报审须随附）：** 单条化的**前端收敛实现未随本批交付**（`attendanceRule.vue` / `wifiWhitelist.js` 当前仍为**多条版**）→ 须**另派前端工程师**按 §12.3 / §12.12 落地；落地前存在「前端多行 ↔ 后端/契约单条」**不一致窗口**。后端产物结论一律标「**静态审查 + 未编译/未运行，收敛到服务器 `mvn test` / 构建阶段**」。

**⑤ Design Tokens：** 本批复核仍 **零新增**（§12.8 重申）；T39 提示条、T40 强警示、T41–T43 二次确认弹窗均复用既有 `--color-warning` / `--color-warning-surface` / `--color-danger` / `--touch-min` 等 Token，**无新造色值、无 Token 外字面量**。

---

## 附：本轮规范自身检查（设计侧）

- [x] 设计方向有业务依据：沿用既有「品牌蓝 + 物流橙 + 深蓝灰」体系，无新色族、无紫色、无 AI 默认三板斧
- [x] 三层 Design Token 结构完整：**零新增 Token**（§6），全部映射既有变量
- [x] 两处交付物的四态全覆盖，含"不适用"的显式声明（§5）
- [x] 无障碍达标：对比度逐项给出（§9.1，含手算值）、触控 ≥44px、表单标签与错误提示可读方式（§9.3）
- [x] 权限分支与能力降级均按"不伪装、不置灰误导"处理（§3.2 / §3.3）
- [x] 所有结论标明来源 `文件:行号`；不确定处标"待裁决"（§11.2）
- [x] **（v1.1）** 口径变更已就地标注作废（不静默删除）、完整取代条款独立成节（§12）；修订后八态矩阵（§12.3.3）+ 文案清单 T30+（§12.4）+ 修订对照表（§12.10）齐备
- [x] **（v1.1）** 权限分支（§12.2-①）、降级态（§12.2-②）、站点管理（§12.2-③）三条不变声明已显式给出
- [x] **（v1.1 评审闭环）** F1 引证已按磁盘实况重取（该页**多条可编辑**，非只读；`wifiText` 系 hrm-demo 跨文件误引）；后端下沉状态与 `api.md` §8 / 实现对齐（§12.11）
- [x] **（v1.1 评审闭环）** 旧数据 `wifiList>1` 加载 / 提交 / 与后端 400 关系三口径明确（§12.12）；fail-open 可用性风险显式登记并给强提示方案（§12.13 / §12.13.1）
- [x] **（v1.1 评审闭环）** NFR（性能无关 / 可观测性）与前后端文案差异已声明（§12.14）；32 字符 vs 32 字节已勘误（§11.2③ / §12.7）；「是否纳入本批」等未决表述已全部收敛
- [x] 本文件为**方案阶段产物**，未写实现代码、未改任何源码、未执行 git
