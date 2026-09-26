# 员工注册页 + 岗位展示 · 设计规范（B5 批次）

> 版本 v1.0 ｜ 日期 2026-09-26 ｜ 作者：UI/UX 设计师（`express-station-ui-ux-designer`）
> 定位：`apps/staff-h5`（驿站助手 · 员工端）新增「员工注册」入口与注册页的**设计规范**，以及「岗位」（方案乙，`employee.position`）在管理端的**展示规范**。仅出设计与走查口径，**不写业务代码**。
> 上游依据：`docs/registration-design.md`（v1.3，技术评审复评「通过」）§1 流程 / §3 接口契约 / §11.4 裁定结果表 / §11.5 字段分离表 / §11.8 不激活口径 / §11.9 岗位方案乙；`docs/security-registration-review.md`（结论**阻断**，必做 M-1~M-9）§6.1 / §7。
> 体例参照：`docs/boss-wifi-and-station-design.md`（§0 取证 → §1 目标 → §2 信息架构 → §3 页面 → … → 可访问性 → 走查清单）。
> **产出物性质：方案阶段产物。** 依 `.trae/rules/智能体调度规则.md` **P0.6 / L8 技术评审闸门**，本规范**须主智能体 Review（并经技术评审评估）通过后前端方可实现**；**本文不宣称已冻结、不含实现代码、不改任何 `.vue`/`.js`/后端/`sql`、不执行 git。**
> Design Tokens：**零新增**，一律引用 `hrm-clients/packages/tokens/src/tokens.base.scss` + `apps/staff-h5/src/styles/tokens.scss` 既有变量（§10）。

---

## 0. 取证方式与已核实事实

**取证方式：** 静态读文件 + 精确 Grep（源码 / Mock / 上游文档）。**未运行**任何构建、测试、部署命令；行号以本机磁盘为准。

| # | 已核实事实 | 证据 |
| --- | --- | --- |
| F1 | 登录页现结构：`header`(标题+副标题) → `expiredTip` → `login__card`(演示快捷 / 提交级错误 / **两 Tab**：`password`=密码登录、`sms`=验证码登录) → `login__aux`(`忘记密码？`) → `login__compliance`；**当前无任何注册入口** | [index.vue:305-471](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/login/index.vue#L305-L471) |
| F2 | 既有可复用机制：`createCountdown(60)`（发码倒计时，`onUnmounted` 停止）、`isPhone`(`^1[3-9]\d{9}$`)、`maskPhone`（`@kdyzgl/shared/domain/mask.js`）、发码按钮 44px 热区（负边距回填、disabled 不 `display:none`）、`sendSms({phone,scene,clientType,deviceId})` | [index.vue:74-102](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/login/index.vue#L74-L102)、[:32](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/login/index.vue#L32)、[:599-616](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/login/index.vue#L599-L616)、[auth.js:15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/api/auth.js#L15) |
| F3 | 路由 `meta` 约定：`public`（免登录）/`roles`/`tabbar`/`title`；守卫 `to.meta.public` 已登录 → 回 `auth.homePath`；未登录访问非 public → 跳 `/login?redirect=` | [router/index.js:28-35、183-196](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/router/index.js#L28-L35) |
| F4 | 上游定稿：**公开端点仅 2 个**（R-1 发码 `scene=REGISTER` 复用 `/auth/sms/send`；R-2 提交 `/registration`）；R-3 转 ADMIN-only、R-4 取消、R-5 取消公开；**一期无申请人自助端点、无 `queryToken`**，进度告知走线下/短信 | [registration-design.md:243-259](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L243-L259)、[:333](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L333) |
| F5 | 注册 DTO 字段白名单（表 A，**仅此 7 字段可被注册方影响**）：`realName`(2-20 必填)、`phone`(必填)、`smsCode`(6 位必填)、`intentStationId`(必填，存在+启用)、`intentPosition`(≤50，**选填**)、`password`(**选填**，U-02 仅留痕)、`agreementVersion`(必填) | [registration-design.md:809-821](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L809-L821) |
| F6 | **硬约束**：DTO 编译期**不含** `role/deptId/stationId(事实)/basicSalary/postSalary/performanceBase/allowances/status/pwdChanged`；`FAIL_ON_UNKNOWN_PROPERTIES=true`，夹带未知字段 → 400 | [registration-design.md:821、297](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L821) |
| F7 | 结果口径：成功返回 `{ applyNo, status:'SUBMITTED', createTime }`（`applyNo` 形如 `RG-YYYYMMDD-0001`）；**审批通过不自动激活**（`status=0`），初始口令由 ADMIN 一次性设定、`pwd_changed=0` 首登强制改密 | [registration-design.md:309-319、897-914](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L309-L319) |
| F8 | 错误码：`9307`(重复提交)、`400`、`4001/4004`(驿站)、`1101`(频控)/`1102`(码错或过期)/`1103`(尝试超限)/`1105`(通道失败)/`1106`；**提交段不返回 `9310`**；手机号查重命中复用 `2003`（在审批建档环节识别） | [registration-design.md:379-392、396-408](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L379-L408) |
| F9 | 安全硬约束（用户可见面）：**M-2 发码/提交响应与文案恒定化**（不得出现 1109/2003 存在性差异）；**M-4 不自动激活**；**M-6 申请管理 ADMIN-only**；**M-7** Nginx 限速（超限 429/1101）；图形码（`captcha`）生产**不可用**（固定占位图、默认关闭，不计入缓解） | [security-registration-review.md:227-233、176、241](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-registration-review.md#L227-L233) |
| F10 | `StationPicker`（`.vue`，弹层 `van-popup` + 48px 行 + loading 骨架 / error+重试 / empty 三态 + `allowAll` 开关）现存于 **`apps/boss-h5/src/components/`**；**不在 `packages/shared/src/ui`**，`apps/staff-h5/src/components/` **无同名组件** | [StationPicker.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/components/StationPicker.vue)、`packages/shared/src/ui/`（目录清单无该组件） |
| F11 | 岗位现状：`position` 仅存 `hr_flow.position`(VARCHAR(50) 自由文本，**无岗位实体**、**不字典化**)；方案乙新增 `employee.position`，**双写点唯一** = `assignForFlow`，**禁止他处单独写**；R-6 审批定岗**建议必填**（后端允许缺省取意向） | [registration-design.md:916-938](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/registration-design.md#L916-L938) |
| F12 | 管理端现状：审批台「岗位名称」为自由文本输入（`el-input`，placeholder `如：快递员`），描述区「拟任岗位」空值显示 `—`；员工管理列表（`hrm-admin`）现有列无「岗位」，工具栏含**导入/导出**；`hr/index.vue` 的「岗位职级」Tab 是**未开放占位**（契约无字典接口） | [FlowDetailDrawer.vue:219、266-268](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/src/views/onboard/components/FlowDetailDrawer.vue#L189-L268)、[employee/index.vue:58-106](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/employee/index.vue#L58-L106)、[hr/index.vue:245-250](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/src/views/hr/index.vue#L245-L250) |
| F13 | MVP 裁剪下架清单（**页面/入口不得出现其字样**）：KPI 考核、包裹族、同步、`/boss/rank`、**趋势 `/boss/trend`**、绩效、占位页 | [update-log.md:112-134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/update-log.md#L112-L134) |
| F14 | 移动端 Token 真源：`packages/tokens/src/tokens.base.scss`（L1/L2 + 间距/圆角/阴影/动效）+ `apps/staff-h5/src/styles/tokens.scss`（字号阶梯 / `--touch-min:44px` / 行高族 / 安全区 / Vant 覆盖） | [tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/tokens/src/tokens.base.scss)、[tokens.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/styles/tokens.scss) |

---

## 1. 设计目标与范围

### 1.1 目标（可判定）

| # | 目标 | 完成定义 |
| --- | --- | --- |
| G1 | 员工在驿站助手登录页能找到注册入口，且**不干扰登录主路径** | 登录两 Tab 与「登录」按钮位置/层级不变；注册为**独立页面**，入口是卡片外的次级动作 |
| G2 | 注册表单**只出现字段白名单**（F5），不出现角色/薪资/站点事实等敏感字段 | 逐字段对照 §4 表；任何 `role/薪资/dept` 输入控件 = 不合格 |
| G3 | 发码与提交的**文案恒定**，**不泄露手机号存在性** | 全页无「已注册/已存在/该手机号已被使用」及 `1109/2003/9310` 字样 |
| G4 | 结果页**不承诺即时可用**，如实说明人工审批 → 管理员发初始口令 → 首登改密 | 结果页含三步说明 + 申请编号；**无「查看进度」按钮**（一期无自助查询端点） |
| G5 | 岗位在管理端**展示规范统一**（位置/层级/空值/可编辑性） | 列表/档案/导出/审批台四处口径一致，空值统一 `—`（导出空单元格） |
| G6 | 可访问性达标 | 关键色对 ≥4.5:1（§11）；触控热区 ≥44px；错误提示 `role=alert` |

### 1.2 本批做 / 不做

| 分类 | 项 | 处置 |
| --- | --- | --- |
| **做** | 登录页「员工注册」入口（位置/形态/文案） | §2 |
| **做** | 注册页（新路由 `/register`）：字段白名单表单 + 意向驿站 + 意向岗位 + 条款同意 | §3、§4 |
| **做** | 发码按钮状态机 + 恒定化提示 | §5 |
| **做** | 提交中/成功/失败三态 + 结果页 | §6 |
| **做** | 异常与边界文案 | §7 |
| **做** | 岗位展示规范（列表/档案/导出/审批台） | §8 |
| **做** | 四态矩阵 / Tokens 引用 / 可访问性 / 文案清单 / 走查清单 | §9–§13 |
| **不做**（本批） | 申请人自助撤回 / 进度查询 / 凭据式公开查询（R-3/R-4/`queryToken` 一期均无） | 不设计入口；`TODO(扩展)` |
| **不做**（本批） | 图形验证码控件（生产不可用，F9） | 不发码前图形码区块；`TODO(扩展)` |
| **不做**（本批） | 岗位字典/职级（`hr/index.vue`「岗位职级」Tab 保持现状占位，**不因本批开放**） | 不在本批激活；`TODO(扩展): 岗位字典（T9）` |
| **不做**（本批） | 档案内**编辑**岗位的写入口（与「双写点唯一」冲突，F11） | 档案侧岗位默认**只读**；见 §8.4 与 §14 |
| **不改** | `hrm-demo`、`hrm-admin` 对 Demo 的只读引用边界、任何后端/`sql` | 本产物仅设计与走查口径 |

---

## 2. 入口设计（登录页「员工注册」）

### 2.1 与既有两 Tab 的关系（决策）

**决策：注册入口 `不` 做成第三个 Tab，而是卡片外的独立次级入口。** 依据：① 既有两 Tab 是**同一动作（登录）的两种通道**，第三 Tab 会把「注册」混进「登录方式」语义；② 注册与登录在卡片内争同一焦点，破坏既有「切换通道不清空已输入」（`watch(activeChannel)` 仅清提交级错误）的交互模型（[index.vue:128-131](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/login/index.vue#L128-L131)）；③ 登录主路径（Tab + 主按钮）保持零改动，风险最小。

### 2.2 位置与形态

```
[ header：驿站助手 / 员工端 ]
[ expiredTip（如到期） ]        ← 不变
[ login__card ]                ← 不变：演示快捷 / 提交级错误 / 两 Tab / 登录按钮
──────────── aux ────────────
                        忘记密码？   ← 不变（右对齐）
[ 新：login__register ]         ← 独立块，位于 aux 之下
   还没有账号？                 ← caption，--text-3
   [ 员工注册 ]                ← 描边次级按钮，44px，居中
[ login__compliance ]           ← 不变
```

| 项 | 规范 |
| --- | --- |
| 位置 | `login__aux` **之后**、`login__compliance` **之前**；与卡片留 `--sp-5` 间距，与合规段留 `--sp-4` |
| 形态 | 一行 caption（`--text-3`，`--fs-caption`）+ 一个**描边次级按钮**（`--color-primary` 文字、`--color-primary-icon` 1px 描边、`min-height: var(--touch-min)`、`--r-sm`、内边距 `0 var(--sp-4)`）。**不用主按钮实底**，避免与登录按钮抢主视觉 |
| 层级 | 视觉权重**低于**登录主按钮：无阴影、无实底、字号用 caption；不得使用 `--grad-hero` 或实底主色 |
| 文案 | 引导语「还没有账号？」+ 按钮「员工注册」 |
| 行为 | 点击 → `router.push('/register')`（public 路由） |
| 登录态 | 已登录用户访问 `/register` 由既有守卫（F3）重定向回 `auth.homePath`，**不额外实现** |

> 说明：`expiredTip`（到期强制重登）与 `forgotTip`（联系管理员重置密码）保持原样，不因本批改变。

### 2.3 入口不干扰登录主路径的判据（走查用）

- 登录按钮仍是卡片内**唯一实底主色**控件；注册按钮为描边次级。
- 注册入口**不进入** `login__card`、不改变 Tab 数量与顺序。
- 键盘 Tab 序：账号 → 密码/短信字段 → 登录按钮 →（卡片外）忘记密码 → 员工注册 → 合规段。

---

## 3. 注册页信息架构

### 3.1 路由与骨架

| 项 | 规范 |
| --- | --- |
| 路由 | `/register`，`meta: { public: true, title: '员工注册' }`（对齐 F3 既有约定；**不由本人实现**，交前端） |
| 顶部 | 二级页自带返回（`PageNav` 语义：`← 返回` + 标题「员工注册」）；返回即回 `/login`，**已填内容丢失前给二次确认**（§7 幂等/边界） |
| 骨架 | 单列居中（登录/表单页布局，UI/UX 技能 §布局模式） |
| 区块顺序 | ① 前置说明 → ② 基本信息（姓名/手机号/验证码）→ ③ 意向信息（意向驿站/意向岗位）→ ④ 账号密码（选填，留痕）→ ⑤ 条款同意 → ⑥ 底部固定 `ActionBar`「提交注册」 |
| 底部 | `ActionBar`（`--actionbar-h:56px`）+ `--safe-bottom`；表单容器底留 `--page-pad-bottom`，末字段不被固定栏遮挡 |

### 3.2 前置说明（防「即时可用」预期）

页面顶部常驻一段说明（`--text-2`，`--fs-caption`）：
> 提交后需管理员人工审核。审核通过后，账号由管理员发放初始口令，首次登录需修改密码。

---

## 4. 表单字段设计（逐字段 = §11.5 表 A 白名单）

> **标签一律常驻**（`--van-field-label-color = --text-2`，7.56:1），占位符仅作示例、**不承载任何必填/校验信息**（§11）。校验为**失焦校验 + 提交前整体校验**，错误就地渲染在字段下方（`role="alert"`）。

| # | 字段(DTO) | 标签 | 类型/控件 | 占位/示例 | 必填 | 校验 | 错误文案 | 键盘 | 最大长度 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `realName` | 姓名 | 文本输入 | 请输入真实姓名 | 是 | 2–20 字（对齐 `validateOnboardingCreate`） | 请输入 2–20 字真实姓名 | `text` | 20 |
| 2 | `phone` | 手机号 | 电话输入 | 请输入 11 位手机号 | 是 | `^1[3-9]\d{9}$`（复用 `isPhone`） | 请输入正确的 11 位手机号 | `tel` + `inputmode=numeric` + `autocomplete=tel` | 11 |
| 3 | `smsCode` | 验证码 | 一次性验证码 | 请输入 6 位验证码 | 是 | 6 位数字 | 请输入 6 位数字验证码 | `numeric` + `autocomplete=one-time-code` | 6 |
| 4 | `intentStationId` | 意向驿站 | 弹层选择（`StationPicker`，`allowAll=false`） | 请选择意向驿站 | 是 | 必须选中一项（服务端再校验存在+启用） | 请选择意向驿站 | 只读触发 | — |
| 5 | `intentPosition` | 意向岗位 | 文本输入 | （示例移入字段下方辅助说明，见下） | **否** | ≤50 字，自由文本 | 意向岗位不超过 50 字 | `text` | 50 |
| 6 | `password` | 密码（选填） | 密码输入（可明/密切换） | 请输入 8–20 位密码 | **否**（U-02：仅留痕，不作员工口令） | 8–20 位且含字母与数字 | 密码需 8–20 位且含字母和数字 | `text`（可切换） | 20 |
| 7 | `agreementVersion` | 服务条款 | 勾选框 + 链接 | — | 是（勾选） | 必须勾选 | 请先阅读并同意《服务条款》与《隐私与安全说明》 | — | — |

### 4.1 明确「不出现」的字段（不得作为输入控件出现在本页）

`role`（角色）、`deptId`（部门）、`stationId`（**站点事实**，与「意向驿站」严格区分）、`basicSalary/postSalary/performanceBase/allowances`（薪资四项）、`status`、`pwdChanged`、`username`（登录账号，由审批人提供/按规则生成）、`expected_entry_date`、`education`。依据：F5/F6（M-3 字段分离）。

### 4.2 意向驿站：复用 `StationPicker`

| 项 | 规范 |
| --- | --- |
| 复用组件 | `StationPicker`（F10）：底部弹层、48px 行、`loading` 骨架 / `error`+重试 / `empty` 三态、选中即关、`aria-pressed` |
| 参数 | `allowAll=false`（必须选具体驿站）、`title="选择意向驿站"`、`emptyText="暂无可选驿站，请联系管理员"` |
| 数据源 | 依 U-04 定稿：**前端构建期静态配置**（不新增公开驿站端点）。⇒ 一期 `loading` 恒 `false`（同步可得）；`error` 仅用于配置读取异常（降级为空态 + 提示）。若后续切 API 再启用 loading/error（`TODO(扩展)`） |
| 已选回显 | 触发区显示 `stationName`；未选显示 placeholder，`--text-placeholder` |
| 漂移兜底 | 静态配置与 `station` 表可能漂移 → 服务端 R-2 校验存在+启用（4001/4004）→ 提交失败按 §7 文案 |

> **跨端复用缺口（须主智能体/前端处理）：** `StationPicker` 现存于 `apps/boss-h5/src/components/`，**未下沉到 `packages/shared/src/ui`**，`apps/staff-h5` 无同名组件。在 staff-h5 复用它需**先把组件下沉到共享包**（属前端工程结构与跨端依赖决策）。见 §14「需主智能体裁定」。

### 4.3 意向岗位：自由文本（含理由）

- **形态**：单一文本输入，≤50 字，可选。**不做下拉/字典**。
- **理由**（对齐 §0.2-⑥ / §11.9）：① 全仓**无岗位实体**，`hr_flow.position`/`employee.position` 均为 `VARCHAR(50)` 自由文本；② 若此处字典化，将与审批台的自由文本形成**两套口径**；③ 引入岗位字典属范围扩张、需用户口径确认（`TODO(扩展): 岗位字典（T9）`）。
- **示例呈现**：不用 placeholder 承载示例（占位对比度不足，§11），改为字段下方**辅助说明**（`--text-3`，4.83:1）：`如：分拣员、快递员、客服`。
- **与审批定岗的关系**：本字段仅意向；审批定岗写 `employee.position`（权威），审批人可改。

### 4.4 密码字段（选填）：必须显式去误导

- 标签用**「密码（选填）」**，字段下方辅助说明（`--text-3`）：
  > 此密码仅用于本次申请留痕，**不是登录密码**。登录密码将在审核通过后由管理员发放。
- **不得**使用「设置登录密码」「登录密码」等误导性标签；**不得**承诺该密码可用于登录。
- 依据：U-02/§11.8（注册密码不作员工初始口令，仅留痕、终态清散列）。
- 见 §14：本字段 UX 上**建议整体移除**（上游 `TODO(扩展)` 已留有该出口），或保留但必须带上述说明——**须主智能体裁定**。

---

## 5. 验证码交互

### 5.1 发码按钮状态机

| 状态 | 触发 | 按钮文案 | 可点 | 视觉 | A11y |
| --- | --- | --- | --- | --- | --- |
| `idle` | 初始 / 手机号合法且未发送 | 获取验证码 | 是 | `--color-primary` 文字 + `--color-primary-icon` 描边 | 可聚焦 |
| `idle-invalid` | 手机号非空但格式非法，点击后 | 获取验证码 | 是（点击给字段错误，**不禁用沉默**） | 同 `idle` | 字段 `role=alert` 报错 |
| `sending` | 请求在途 | 发送中… | 否 | 同 idle + 文案变化 | `aria-busy=true` |
| `countdown` | 发码成功 | 重新获取（{n}s） | 否 | 文字 `--text-3`（4.83:1）、描边 `--border-line` | `aria-disabled=true` |
| `failed` | 1101/1105/1106/网络 | 重新获取 | 是（可重试） | 同 idle | 字段 `role=alert` 报错 |

- **热区恒定 ≥44×44**：禁用/倒计时态**不 `display:none`**，沿用登录页负边距回填模式（F2）。
- **倒计时文字用 `--text-3` 而非 `--text-disabled`**：剩余秒数是有效信息，不属「不可用控件」豁免范围（§11）。
- 倒计时来源：服务端 `nextAllowedIn`（缺省回落 60s），频控命中时按服务端建议间隔覆盖本地倒计时（对齐登录页做法）。

### 5.2 响应恒定化对 UI 的影响（M-2 硬约束）

| 场景 | 前端表现 |
| --- | --- |
| 发码成功（**含未注册号静默成功**） | 统一提示「验证码已发送至 138\*\*\*\*0000」（脱敏用 `maskPhone`）。**不得**按「是否已注册」分支文案 |
| 已注册号 / 未注册号 | **逐字段一致**（响应体、状态码、文案、耗时量级）→ 前端**无差异分支** |
| 禁止出现 | 「该手机号未注册」「该手机号已注册」「该手机号已被使用」及码值 `1109`/`2003`/`9310` 的任何用户可见文案 |
| 图形码 | 一期**不呈现**图形码区块（F9：生产不可用）；`TODO(扩展)` |

### 5.3 频控 / 超限文案

| 触发 | 文案 | 处理 |
| --- | --- | --- |
| `1101` 频控 | 验证码发送过于频繁，请稍后再试 | 字段级 `role=alert`；按服务端 `nextAllowedIn` 覆盖倒计时 |
| `429`（Nginx `limit_req`，M-7） | 操作过于频繁，请稍后再试 | 表单级内联提示，不清空已填内容 |
| `1105/1106` 通道失败 | 短信服务暂不可用，请稍后重试 | 按钮回 `failed` 态可重试 |

---

## 6. 提交与结果页

### 6.1 三态

| 态 | 表现 |
| --- | --- |
| **提交中** | `ActionBar` 主按钮 `loading` +「提交中…」；按钮禁用，**防重复提交**；表单只读但不卸载（保留输入） |
| **成功** | 切换到**结果页视图**（同路由内替换，非新 Tab）；清除表单状态；不可回退到已提交表单（返回键 → 登录页） |
| **失败** | 停留在表单；表单顶部内联错误（`role=alert`，`--color-danger`）+ 字段级错误；**已填内容全部保留**；焦点移至首个错误字段 |

### 6.2 结果页内容（不承诺即时可用）

```
[ 成功图标（--color-success-icon，线稿，非实底） ]
申请已提交
申请编号：RG-20260926-0001         [ 复制 ]
──────────────────────────────
后续流程（三步，步骤说明，--text-2）
① 管理员审核 —— 人工审批，请耐心等待
② 审核通过后，由管理员发放初始口令
③ 首次登录需修改密码，之后即可正常使用
──────────────────────────────
[ 返回登录 ]（次级描边）
```

| 项 | 规范 |
| --- | --- |
| 申请编号 | 主信息，等宽感呈现（用户级字号 `--fs-num-sm`/`--fs-body-strong`）+ 复制按钮（复用既有复制交互模式，`apps/staff-h5/src/components/WorkOrderCopyButton.vue` 的交互语义，**不新增组件规范**） |
| 后续说明 | **必须**含「人工审批 → 管理员发放初始口令 → 首登改密」三步（对齐 §11.8 不激活口径） |
| 禁止 | **不得**出现「立即登录/已可使用/账号已开通」等即时可用表述；**不得**出现「查看进度 / 进度查询」按钮（一期无申请人自助端点，F4） |
| 进度告知 | 文案注明：审核进度由管理员线下告知（对齐 F4 副作用） |
| 返回 | 主操作「返回登录」→ `/login` |

---

## 7. 异常与边界

| 场景 | 触发码 | 文案 | 位置 |
| --- | --- | --- | --- |
| 手机号格式错误 | 前端 | 请输入正确的 11 位手机号 | 字段级 |
| 验证码错误/过期 | `1102` | 验证码不正确或已过期，请重新获取 | 字段级（验证码） |
| 验证码尝试超限 | `1103` | 验证码错误次数过多，请重新获取验证码 | 字段级 |
| 重复提交（同号有进行中申请） | `9307` | 该手机号已有进行中的入职申请，请勿重复提交 | 表单级 |
| **手机号已存在（中性化，不泄露存在性）** | `2003` | 提交未成功，请稍后重试；如多次失败请联系管理员 | 表单级；**严禁**出现「已注册/已存在/已被使用」 |
| 站点漂移（不存在/停用） | `4001/4004` | 所选意向驿站暂不可选，请重新选择 | 字段级（意向驿站） |
| 限速 | `429` | 操作过于频繁，请稍后再试 | 表单级 |
| 网络失败 | — | 网络异常，请检查网络后重试 | 表单级 + 可重试（提交按钮恢复可点） |
| 未勾选条款 | 前端 | 请先阅读并同意《服务条款》与《隐私与安全说明》 | 条款区 |
| 返回导致内容丢失 | — | 返回前二次确认：已填写的内容将不会保存，确定返回？ | 返回拦截 |

> **幂等提示统一口径**：任何可能揭示「手机号是否存在」的服务端错误，一律映射为 §7 的**中性文案**，前端只维护一张 `中性兜底` 映射（`2003`/未知存在性码 → 同一句），避免文案分支泄露存在性。

---

## 8. 岗位展示规范（方案乙 · `employee.position`）

> 权威口径：以 `employee.position` 为**权威事实**，`hr_flow.position` 为该次流程的过程值与留痕；**双写点唯一**，禁止他处单独写（F11）。

### 8.1 展示位置与层级总表

| # | 界面 | 位置 | 层级 | 空值 | 可编辑 |
| --- | --- | --- | --- | --- | --- |
| 1 | 员工管理列表（`hrm-admin/src/views/employee/index.vue`） | 新增「岗位」列，置于「驿站」列**之后、「角色」列之前** | 扁平文本，**非可点击、非排序** | `—` | 否 |
| 2 | 员工导出（同页「导出」） | 新增「岗位」列，置于「驿站」列之后 | 数据单元格 | **空单元格**（不写 `—`，避免污染数据） | 否 |
| 3 | 人事档案详情（`apps/web/src/views/hr`） | 详情描述项「岗位」 | 只读描述项 | `—` | **否**（见 8.4） |
| 4 | 审批台（`apps/web/src/views/onboard/components/FlowDetailDrawer.vue`） | ① 描述区「拟任岗位」= `flow.position`（既有，空 `—`）；② `ASSIGN_STATION` 步骤「岗位名称」输入；③ 注册来源区展示 `registration.intentPosition`（意向） | 输入控件（步骤内） | 输入为空显示 placeholder | 是（审批人） |

### 8.2 审批定岗表单（填写位置与校验）

- 位置：`ASSIGN_STATION` 步骤内，与「角色」同栅格行（`flow-detail__grid`），沿用既有 `el-form label-position="top"`（F12）。
- 控件：单行文本（`el-input`），`maxlength=50` + `show-word-limit`（对齐字段长度 50）。
- 预填：打开步骤时以 `registration.intentPosition`（或 `flow.position`）预填，审批人可改。
- 校验（设计建议，见 §14 裁定）：**前端必填**，为空时提示「请填写岗位名称」（对齐 §11.9「建议必填」，后端仍允许缺省取意向）。
- 值落点：提交 `position` → 后端双写 `employee.position` + `flow.position`。
- 空值展示：未办理时描述区沿用 `—`（不改既有）。

### 8.3 列表/导出口径一致性

- 列表列宽 `min-width:110`，`show-overflow-tooltip`（对齐既有列风格，F12），不参与排序。
- 展示空值 `—`，导出空值**空单元格**；两者**分离**（Excel 数据侧不引入占位符）。
- 批量导入模板新增「岗位」列（可空）；导入空 = 不写（保持 NULL = 未登记），**不以空串覆盖已有值**。

### 8.4 可编辑性（关键约束）

- 档案/列表/导出侧岗位**默认只读**。**不在**档案编辑抽屉（`ProfileEditDrawer.vue`）内新增岗位输入——否则将产生**第二个写点**，破坏 §11.9「双写点唯一」不变式。
- 若产品要求档案内可改岗，须：后端先提供受控写入口（含校验/留痕）→ 重过技术评审（P0.6）→ 本规范再补编辑态设计。见 §14。

---

## 9. 状态矩阵（四态）

| 区块 | loading | empty | error | normal |
| --- | --- | --- | --- | --- |
| 注册页表单 | 不进页面级 loading（表单为即刻可填）；品牌/说明静态 | 不适用 | 不适用（不整页替换） | 字段可填、按钮可用 |
| 意向驿站选择器 | 一期 `loading=false`（静态配置）；切 API 后启用 3 行骨架（48px 等高，F10） | 「暂无可选驿站，请联系管理员」 | 「驿站列表加载失败」+「重新加载」（弹层内区块级，弹层不整层替换） | 列表可选，选中即关 |
| 验证码按钮 | `sending`「发送中…」 | 不适用 | `failed`「重新获取」可重试 | `idle`「获取验证码」/ `countdown`「重新获取（{n}s）」 |
| 提交按钮 | actionbar `loading`「提交中…」禁用 | 不适用 | 失败后恢复可点 + 表单级错误 | 「提交注册」可点 |
| 结果页 | 不适用（同步切换） | 不适用 | 不适用 | 申请编号 + 三步说明 + 返回登录 |
| 岗位列（列表/导出） | 随列表 `v-loading`（既有） | 列值 `—`（导出空单元格） | 随列表既有错误态 | 显示岗位文本 |
| 审批定岗「岗位名称」 | 随抽屉既有 `v-loading` | 空 → placeholder | 随抽屉既有 `StateBlock error` | 预填/可改/校验通过 |
| 档案「岗位」描述项 | 随页面既有 loading | `—` | 随页面既有错误态 | 只读显示 |

> 依据：UI/UX 技能「无 loading/empty/error 即反模式」；四态文案必须互不相同（空 ≠ 取不到）。

---

## 10. Design Tokens（零新增）

| 用途 | Token（引用既有变量） |
| --- | --- |
| 主色/次级控件描边、文字 | `--color-primary`、`--color-primary-icon`、`--color-primary-surface` |
| 危险/错误 | `--color-danger`、`--color-danger-surface` |
| 成功（结果页图标） | `--color-success-icon` |
| 文本 | `--text-1`、`--text-2`、`--text-3`、`--text-placeholder`、`--text-inverse` |
| 表面/描边 | `--surface-page`、`--surface-card`、`--surface-sub`、`--surface-sunken`、`--border-line`、`--border-control` |
| 状态（如到期/演示标识沿用） | `--state-warning-*`、`--state-simulate-*` |
| 间距/圆角/阴影/动效 | `--sp-1..8`、`--r-sm/md/lg/full`、`--e0..e3`、`--dur-fast/base`、`--ease-std` |
| 字号/行高 | `--fs-h1/h2/h3/body/body-strong/caption/num-sm`、`--lh-*`（移动端列） |
| 尺寸/安全区 | `--touch-min`(44px)、`--actionbar-h`、`--navbar-h`、`--safe-top`、`--safe-bottom`、`--page-pad-bottom`、`--row-h-*` |
| Vant 覆盖（既有） | `--van-field-*`、`--van-button-radius`、`--van-popup-round-radius`、`--van-cell-*` 等 |

**零新增结论**：本规范所需颜色/间距/字号**全部可由既有变量表达**。**未新增任何颜色字面量或 Token**。若实现中发现缺口，须先在本规范登记「理由 + 替代方案」并回评审，**不得在前端就地造色值**。

---

## 11. 可访问性

### 11.1 关键色对（对比度，WCAG AA 正文 ≥4.5:1）

| 前景 | 背景 | 对比度 | 用途 | 判定 |
| --- | --- | --- | --- | --- |
| `--text-1` `#1f2937` | `--surface-card` `#ffffff` | **14.66:1** | 字段值/标题 | 通过 |
| `--text-2` `#4b5563` | `#ffffff` | **7.56:1** | 字段标签/前置说明 | 通过 |
| `--text-3` `#6b7280` | `#ffffff` | **4.83:1**（项目既有实测口径） | 辅助说明/倒计时文字 | 通过 |
| `--color-primary` `#0958d9` | `#ffffff` | **6.16:1**（项目既有实测口径） | 次级按钮/链接文字 | 通过 |
| `--color-danger` `#cf1322` | `#ffffff` | **5.57:1** | 错误文案 | 通过 |
| `--state-warning-fg` `#b45309` | `--state-warning-bg` `#fffbe6` | ≈**5.2:1** | 到期提示条 | 通过 |
| `--text-placeholder` `#9aa4b2` | `#ffffff` | **2.52:1** | 占位符 | **低于 4.5** → 见 11.2 缓解 |

> 上表除标注「项目既有实测口径」外，均为 sRGB 相对亮度计算值；**实现后须由 UI/UX 走查复核实测**。

### 11.2 占位符低对比度的处理（红线缓解）

- 占位符**不承载任何必要信息**：所有字段标签常驻（`--text-2`，7.56:1），必填/格式要求由标签 + 校验文案表达。
- 「意向岗位」的示例**移入字段下方辅助说明**（`--text-3`，4.83:1），不依赖占位符。
- 结论：占位符仅装饰性示例，**不作为唯一信息来源**，不违反「信息对比度 ≥4.5:1」的红线本意。

### 11.3 触控与安全区

- 发码按钮 **44×44**（含负边距回填）；返回/条款链接/复制按钮 **≥44px**；`StationPicker` 行 **48px**；`ActionBar` **56px**。
- 底部固定栏 + 表单容器用 `--page-pad-bottom`（含 `--safe-bottom`），末字段不被遮挡。
- 顶部用 `--safe-top`（H5 壳 `--status-bar-height`），浏览器下为 0 不塌陷。

### 11.4 表单可读性与键盘

- 手机号 `type=tel`+`inputmode=numeric`；验证码 `autocomplete=one-time-code`（唤起一次性验证码键盘）。
- 错误文案 12px/18px（`--fs-caption`/`--lh-caption`）、`--color-danger`、紧贴字段下方、`role="alert"`。
- 提交失败 → 焦点移至首个错误字段；`aria-busy` 标注在途控件；`prefers-reduced-motion` 降级已由 Token 层全局处理（`tokens.base.scss`）。

---

## 12. 文案清单（中文，去 AI 味）

**入口**
- 还没有账号？
- 员工注册

**注册页**
- （标题）员工注册
- （前置说明）提交后需管理员人工审核。审核通过后，账号由管理员发放初始口令，首次登录需修改密码。
- （区块）基本信息 / 意向信息 / 账号密码
- （姓名）请输入 2–20 字真实姓名
- （手机号）请输入正确的 11 位手机号
- （验证码）请输入 6 位数字验证码
- （意向驿站）请选择意向驿站 / 暂无可选驿站，请联系管理员 / 驿站列表加载失败 · 重新加载
- （意向岗位）意向岗位不超过 50 字 /（辅助说明）如：分拣员、快递员、客服
- （密码，选填）密码需 8–20 位且含字母和数字 /（辅助说明）此密码仅用于本次申请留痕，不是登录密码。登录密码将在审核通过后由管理员发放。
- （条款）请先阅读并同意《服务条款》与《隐私与安全说明》
- （按钮）提交注册 / 提交中…

**发码/验证码**
- 获取验证码 / 重新获取 / 发送中…
- 重新获取（{n}s）
- 验证码已发送至 138\*\*\*\*0000
- 验证码发送过于频繁，请稍后再试
- 验证码不正确或已过期，请重新获取
- 验证码错误次数过多，请重新获取验证码
- 短信服务暂不可用，请稍后重试
- 操作过于频繁，请稍后再试

**结果页**
- 申请已提交
- 申请编号：{applyNo}
- 复制 / 已复制
- ① 管理员审核：人工审批，请耐心等待
- ② 审核通过后，由管理员发放初始口令
- ③ 首次登录需修改密码，之后即可正常使用
- 审核进度由管理员线下告知
- 返回登录

**异常/边界**
- 该手机号已有进行中的入职申请，请勿重复提交
- 提交未成功，请稍后重试；如多次失败请联系管理员
- 所选意向驿站暂不可选，请重新选择
- 网络异常，请检查网络后重试
- 已填写的内容将不会保存，确定返回？

**岗位展示**
- （列/描述项标签）岗位 / 拟任岗位 / 岗位名称
- （空值）—
- （审批校验）请填写岗位名称

---

## 13. 视觉走查清单（后续验收逐条打勾）

**A. 入口与结构**
- [ ] 登录页 Tab 仍为 **2 个**（密码登录 / 验证码登录），顺序与文案未变
- [ ] 「员工注册」为卡片**外**的描边次级入口，不进入 `login__card`
- [ ] 登录按钮仍是卡片内**唯一实底主色**控件
- [ ] 注册为独立路由页面，含返回；已登录访问被守卫重定向

**B. 字段白名单**
- [ ] 表单仅出现 §4 表 7 字段；**无**角色/部门/站点事实/薪资四项/状态/账号 等控件
- [ ] 每个字段：标签常驻、占位示例、校验与错误文案与 §4 一致
- [ ] 「意向岗位」为自由文本，无字典下拉；示例在辅助说明而非 placeholder
- [ ] 密码字段标签为「密码（选填）」且带「不是登录密码」说明

**C. 存在性不泄露（红线）**
- [ ] 全页（注册页 + 结果页 + 所有错误态）**无**「已注册 / 已存在 / 已被使用 / 该手机号未注册」字样
- [ ] 全页**无** `1109 / 2003 / 9310` 码值或等价存在性提示
- [ ] 发码成功文案对「已注册/未注册」**完全一致**（无分支文案）
- [ ] 已注册手机号提交的响应与未注册**外观一致**（受理外观恒定）

**D. 不激活口径**
- [ ] 结果页含「人工审批 → 管理员发放初始口令 → 首登改密」三步
- [ ] **无**「立即登录 / 已可使用 / 账号已开通」等即时可用表述
- [ ] **无**「查看进度 / 进度查询」按钮
- [ ] 申请编号可复制且与 `applyNo` 格式一致（`RG-YYYYMMDD-####`）

**E. 四态**
- [ ] `StationPicker` 具 loading/empty/error 三态且文案互不相同
- [ ] 发码按钮五态（idle/idle-invalid/sending/countdown/failed）可复现
- [ ] 提交三态（提交中/成功/失败）可复现，失败保留已填内容
- [ ] 岗位列/描述项空值显示 `—`，导出为空单元格

**F. 可访问性**
- [ ] 关键色对实测 ≥4.5:1（§11.1）；占位符未承载必要信息
- [ ] 发码/返回/复制/条款热区 ≥44×44；`StationPicker` 行 ≥48
- [ ] 错误提示 `role="alert"`；在途控件 `aria-busy`
- [ ] 软键盘类型正确（tel/numeric/one-time-code）；末字段不被固定栏遮挡

**G. 纪律与边界**
- [ ] **零新增 Token**：无新色值字面量（颜色/字面量 grep = 0）
- [ ] 全页**无**被下架模块字样：包裹 / 同步 / KPI / 绩效 / 排行 / 趋势 / 占位页
- [ ] 未激活 `hr/index.vue`「岗位职级」Tab（仍为占位）
- [ ] 档案/列表侧岗位为**只读**，未新增第二写点
- [ ] `hrm-demo`、后端、`sql` 零改动；未执行 git

---

## 14. 遗留与 TODO(扩展)

### 14.1 需主智能体裁定 / 协调的点

| # | 事项 | 为何需裁定 |
| --- | --- | --- |
| Q1 | **`StationPicker` 下沉到 `packages/shared/src/ui`**（或另建共享组件），供 `staff-h5` 与 `boss-h5` 共用 | 跨端工程结构与依赖边界变更；不改共享包则 staff-h5 无法「复用既有组件」（F10） |
| Q2 | **注册密码字段是否保留** | 上游 U-02 定为「仅留痕」，且留有「可整体移除」的 `TODO`；保留则须带 §4.4 去误导说明。UX 建议移除，**须主智能体/用户口径确认** |
| Q3 | **审批定岗「岗位名称」前端是否必填** | §11.9 为「建议必填」，后端允许缺省取意向；前端必填会改变审批人操作路径，需确认 |
| Q4 | **档案内岗位是否需可编辑** | 与「双写点唯一」冲突（F11）；若要可编辑，需后端先提供受控写入口并重过技术评审 |

### 14.2 `TODO(扩展)` 登记

- `TODO(扩展): 申请人自助进度查询 —— 需新增公开端点并重过 P0.5 安全评估（R-3/R-4/queryToken 一期均无）`
- `TODO(扩展): 意向驿站选项由构建期静态配置切 API —— 届时启用 StationPicker 的 loading/error 态`
- `TODO(扩展): 注册发码图形验证码 —— 生产图形码可用后再设计控件（当前不可用，不计入缓解）`
- `TODO(扩展): 岗位字典（T9）—— 字典化将影响注册页与审批台两处口径，须统一并重评`
- `TODO(扩展): 若产品接受，可整体移除注册密码字段（对齐上游 U-02 备注）`

### 14.3 本规范未覆盖 / 依赖上游闭环

- 本规范为**视觉与交互规范**：接口契约以 `api.md` 定稿（后端）为准；数据/结构以 `registration-design.md` v1.3 为准；安全闭环（M-1~M-9）与放行由**网络安全工程师 + 主智能体**按 P0.5/L7 决定。
- 本产物**属方案阶段产物**，须主智能体 Review（含技术评审）后前端方可按 Tokens 实现；**未经批准不得据此开工实现**。

---

## 附：本方案自身检查（设计侧）

- [x] 仅新建本文件；未改任何 `.vue`/`.js`/后端/`sql`，未改其它文档，未执行 git
- [x] Design Tokens 零新增；无新造颜色字面量
- [x] 未出现被下架模块（包裹/同步/KPI/绩效/排行/趋势）入口或字样
- [x] 入口不干扰登录主路径；字段严格取自 §11.5 表 A 白名单
- [x] 发码/提交文案恒定、不泄露手机号存在性；结果页不承诺即时可用
- [x] 岗位展示四处口径统一，档案侧只读（不引入第二写点）
