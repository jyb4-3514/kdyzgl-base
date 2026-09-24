# 快递驿站智汇系统 · 三端登录页设计规范（双通道 + 设备信任 + 会话 3 天）

> 版本 v1.0 | 日期 2026-09-24 | 作者：UI/UX 设计师 `express-station-ui-ux-designer`
> 交付物性质：**设计规范**（区块 / 状态机 / 文案 / 尺寸 / Token / 交接契约），**不含实现代码**。
> 上游依据：`multi-client-architecture.md` §4（登录契约）、`security-auth-review.md` §4（安全约束）、`demo-ux-improvement.md` §C（Token 增量与无障碍）、`demo-ui-redesign.md` §2/§3/§7（设计系统）。
> 落地链路：L2（设计 → 前端 → 测试）；P2 设计先行。
> 状态：**待主智能体 Review + 用户裁定**（阻塞项见 §9）。

---

## 0. 前置：取证、事实核对与边界

### 0.1 取证方式

本轮**只读检索现状代码与既有文档**，未修改任何源码、Mock、契约或规则文件。已读文件：

| 类别 | 文件 |
| --- | --- |
| 移动端登录页 | [login/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue) |
| PC 端登录页（一期，只读） | [login/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/login/index.vue) |
| 登录 Mock | [auth.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js) |
| 错误码真源 | [errorCode.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/errorCode.js) |
| Token 真源 | [tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss)、[mobile/tokens.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss)、[pc/tokens.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss) |
| 端品牌真源 | [appName.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/appName.js)、[brand.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/constants/brand.js) |
| 路由与守卫 | [mobile/router/index.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js)、[pc/router/index.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/router/index.js) |
| Vant 注册表 | [vant.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/vant.js) |
| 既有 B 端引用 | [multi-client-architecture.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md) §4、[security-auth-review.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md) §4 |

### 0.2 事实核对（含与任务描述的 3 处出入）

> 依调度规则 §7「事实性纠正」：上游材料与实测不符须列出。

| # | 任务描述 / 既定认知 | 实测事实 | 影响 |
| --- | --- | --- | --- |
| F1 | 「网页端登录页在 `hrm-demo/src/pc/`」 | ❌ **不在**。PC 路由 `/login` 复用 `@admin/views/login/index.vue`（[pc/router/index.js:31-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/router/index.js#L31-L38)），`@admin` 别名指向 `hrm-admin/src`（**只读、禁改**） | PC 端登录页改造**无法直接改一期文件**，须在 Demo 侧新建覆盖页（见 §3.2、§9 待裁定 A1） |
| F2 | 三端「独立代码」 | 移动端「员工端」与「管理端」**当前共用同一个登录页** `mobile/views/login/index.vue`，靠 `?as=boss|staff|station` 与登录后 `role` 分流品牌（[appName.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/appName.js)）。二者物理同源，仅视角不同 | 本节差异化设计按「同一实现、三套视角口径」编写，避免制造第二份登录页 |
| F3 | 错误码 **1110** | ✅ 任务提及的 1110 与既有 11xx 段（1101–1109，[multi-client §4.1.1](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L410-L424)）**不重叠，属新增码**；且真源 [errorCode.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/errorCode.js) 目前**无任何 11xx 段** | 11xx 全段 + 1110 均须登记为契约增补（§7.4），Mock 需新增 |

**另需登记的两处口径冲突（须裁定，见 §9）：**

- **C1**：`multi-client §4.3.2` **建议**到期走「选项 2 短信续期（B3）」，而**用户已裁定**为「**选项 1 到期后强制重新登录**」。本设计**以用户裁定为准**：B3 `/auth/session/renew` **不在本设计启用范围**（可保留端点但登录页不调用）。
- **C2**：任务置顶约束「现有 4 个 auth 端点的**入参/出参不得变更**」与契约 B1「`/auth/login` 出参**新增** `needDeviceVerify` 等字段」表面冲突。本设计的判定口径见 §7.3：**新增可选字段 = 向后兼容扩展，不视为「变更」**；删除/改名/改类型既有字段 = 变更（禁止）。此口径须裁准确认。

### 0.3 能力边界与不确定项

| # | 不确定项 | 验证方式 | 收敛阶段 |
| --- | --- | --- | --- |
| U1 | 短信 Mock 是否需真实倒计时服务端 `nextAllowedIn` 校准 | Mock 返回固定值，前端以本地倒计时兜底 | 联调环境 |
| U2 | 图形验证码（D1）是否在演示态开启 | 暂定演示态**不开启** captcha（`requireCaptcha:false`），生产按风控触发 | 契约定稿 |
| U3 | 移动端「员工端是否拒绝 ADMIN 登录 / 管理端是否拒绝非 ADMIN」 | 现状是共用页按角色分流；端准入矩阵见 §1.3，须裁定（§9-A3） | 用户裁定 |
| U4 | 一期 `hrm-admin` dev proxy 会话字段（`pwdChanged` 等）在 3 天会话下的行为 | 静态推证 + 联调实测 | 联调环境 |

### 0.4 权威顺序

冲突时：`项目规则1.md` > `security-auth-review.md`（安全结论优先）> **用户裁定** > `multi-client-architecture.md`（契约）> 本文件（视觉/交互）> 既有 demo 设计规范。**设计方向与需求冲突、或须改 `hrm-admin`/`hrm-server` 才可实现时，升级主智能体**（本轮已登记 §9-A1）。

---

## 1. 设计目标与原则

### 1.1 设计主张（一句话）

**「让『安全』可被理解，而不是被恐惧」** —— 登录页用低噪声的品牌区与单列、逐步披露的表单，把「双通道登录 + 新设备短信二次验证 + 3 天到期重登」这套安全能力，做成用户一眼看懂、三步走完、失败可恢复的常规操作。

**为什么不是 AI 默认三板斧**：沿用项目既有蓝/橙物流语义与深蓝灰中性色（[demo-ux-improvement.md §C1](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ux-improvement.md)），无紫色系、无玻璃态、无通用圆角卡片堆叠；登录卡为**单卡纵向流程**，非「大标题 + 副标题 + 两列按钮」模板。

### 1.2 五条原则

| # | 原则 | 可判定要求 |
| --- | --- | --- |
| P1 | **单一任务 · 逐步披露** | 一屏只完成一件事：首屏只出「通道切换 + 账号/密码（或手机号/验证码）+ 主按钮」；新设备验证作为**第二步卡片**，不塞进首屏 |
| P2 | **安全可理解** | 涉及二次验证/到期处，必须用**中性说明文案**解释「为什么」（如「为保障账号安全」），**禁止**任何诱导泄露密码/验证码的措辞；验证码类字符**不出现在页面可见区域与公开产物** |
| P3 | **同源不同语气** | 三端共用同一套 Token 与组件语汇；仅品牌名、称谓、密度、断点分端（§3），**不新造色彩/字号体系** |
| P4 | **无障碍内建** | 文本对比度 ≥ 4.5:1、大字 ≥ 3:1、非文本 ≥ 3:1；触控区 ≥ 44×44px；全键盘可达、焦点可见；状态**不只靠颜色**（带文字/图标） |
| P5 | **优雅失败** | 四态齐全（loading/empty/error/normal）；错误**内联可恢复**，不遮挡表单；网络异常给明确重试路径 |

### 1.3 端-角色准入矩阵（1110 触发面）

> 依据：用户已确认「PC 端仅 ADMIN 可登录」；移动端现状为**共用页按角色分流**。1110 文案统一为「该账号无权登录此端」。

| 端 | 允许角色（准入） | 拒绝 → 表现 | 依据 |
| --- | --- | --- | --- |
| **网页端**（`pc.html`） | `ADMIN` | 非 ADMIN → **1110**，停留登录页，不签发会话 | 用户裁定 + 任务 §一.5 |
| **员工端**（`mobile.html`，`?as=staff\|station`） | `STAFF`、`STATION_ADMIN` | `ADMIN` → **待裁定**（§9-A3）：现状允许并分流到管理端首页 | 现状 `HOME_BY_ROLE` |
| **管理端**（`mobile.html`，`?as=boss`） | `ADMIN` | 非 ADMIN → **1110**（建议） | 任务 §二 + 品牌绑定「仅 ADMIN 视角」([demo-boss-ui-spec.md §13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-boss-ui-spec.md)) |

> **端准入必须由服务端判定**（前端拦截仅为体验）：见 §7.1 入参携带端标识 `clientType`（`WEB`/`H5`）+ 角色约束，服务端不满足即 1110。

---

## 2. 三端登录页信息架构与页面状态机

### 2.1 五档区块模型（三端共用）

登录页按 **5 个纵向区块**构成，顺序固定（视觉层级自上而下）：

```
┌─────────────────────────────────┐
│  ① 品牌区  Brand                 │  应用名 + 视角副标题（+演示标识）
├─────────────────────────────────┤
│  ② 通道切换区  ChannelSwitch      │  密码登录 | 验证码登录
├─────────────────────────────────┤
│  ③ 表单区  Form                   │  输入 + 内联错误 + 主按钮（+ 验证码行）
├─────────────────────────────────┤
│  ④ 辅助链接区  AuxLinks           │  忘记密码 / 设备管理 / 换账号（按端）
├─────────────────────────────────┤
│  ⑤ 合规区  Compliance             │  演示标识 / 隐私与安全说明（小字）
└─────────────────────────────────┘
```

| 区块 | 必备内容 | 无障碍角色 |
| --- | --- | --- |
| ① 品牌区 | 应用名（`h1`）、视角副标题、演示态标识条 | `<h1>` 唯一 |
| ② 切换区 | 双通道 Tab（`tablist`） | `role="tablist"` + `aria-selected` |
| ③ 表单区 | 字段、内联错误区、主按钮 | `<form>` + `aria-live` 错误播报 |
| ④ 辅助链接区 | 忘记密码、设备管理（若提供）、返回登录（二次验证步） | `<a>`/`<button>` |
| ⑤ 合规区 | 「演示环境」标识 / 「登录即表示同意…」说明 | 小字 ≥12px |

### 2.2 三端构成（逐端区块填充）

| 区块 | 网页端（PC） | 员工端（H5） | 管理端（H5） |
| --- | --- | --- | --- |
| ① 品牌区 | 应用名「**快递驿站智慧管理系统**」+ 副标题「管理员登录」 | 应用名「**驿站助手**」+ 副标题「员工端」 | 应用名「**驿站精灵**」+ 副标题「管理员经营视角」 |
| ② 切换区 | `el-tabs`：密码登录 / 验证码登录 | `van-tabs`（line）：密码登录 / 验证码登录 | 同员工端 |
| ③ 表单区 | 账号 + 密码 + [主按钮] | 手机号 + 验证码 + [验证码行+主按钮] | 同员工端 |
| ④ 辅助链接区 | 忘记密码（→ 提示联系管理员）、设备管理（→ 我的页/独立页，见 §9-A5） | 忘记密码 → 提示联系管理员；**设备管理**入口在「我的」页内 | 同员工端 |
| ⑤ 合规区 | 「演示环境 · 数据为 Mock」标识（演示态） | 演示标识条（`van-notice-bar`） | 同员工端 |
| **二次验证步** | 密码通过且新设备 → 卡片内切换为「设备验证」步骤 | 同左（卡片内切步） | 同左 |

> **管理端与员工端的品牌/称谓差异**：应用名严格走 `resolveAppName()` 真源（`ADMIN`→驿站精灵 / `STAFF|STATION_ADMIN`→驿站助手）；**登录页标题在未认证态不可泄漏另一端品牌**——沿用既有「`?as=boss` 才渲染管理员品牌副标题」的判据（[login/index.vue:23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue#L23)）。文案统一用「**管理员 / 管理端 / 员工**」，**不使用「老板」**。

### 2.3 页面状态机

```text
                 ┌──────────────┐
                 │ S0 初始       │ 双通道 Tab；默认「密码登录」；空表单
                 └──────┬───────┘
        ┌───────────────┴────────────────┐
        ▼（密码通道）                      ▼（验证码通道）
   ┌─────────┐ 输入中               ┌──────────┐ 输入手机号
   │ S1 填表  │◄──────┐              │ S10 填表  │
   └────┬────┘       │              └────┬─────┘
        │ 提交        │                  │ 点「获取验证码」
        ▼            │                  ▼
   ┌─────────┐       │              ┌──────────────┐
   │ S2 提交中│       │              │ S11 已发码     │ 60s 倒计时
   └────┬────┘       │              └──────┬───────┘
        │            │                  │ 填码 + 提交
        │ 密码校验通过但新设备             ▼
        ▼            │              ┌──────────┐
   ┌──────────────────┐             │ S12 提交中│
   │ S3 设备二次验证   │◄── 验证码错/过期 ─┐  └────┬───┘
   │ （卡片内第 2 步） │  ── 重新获取 ──┘        │
   └────┬───────────┬─┘                        │
        │ 提交       │「这不是我的设备」返回 S0    │
        ▼            ▼                        │
   ┌─────────┐    ┌────────┐                  │
   │ S7 校验中│    │ S0 初始│◄─────────────────┘
   └────┬────┘    └────────┘
        │ 通过
        ▼
   ┌────────────────────────────┐
   │ S8 成功  Toast + 跳转(redirect)   │
   └────────────────────────────┘

   [S9 到期强制重登]  由 401/1108 触发，或路由守卫发现会话过期 → 进入 S0 并显示到期说明
```

**分支要点：**

- **S3 只能由「密码通道」进入**（新设备）；**验证码通道登录不触发二次验证**（短信本身即二次因子，`deviceTrusted:true`）。
- **S11 倒计时**与 **S3→S7** 是两条独立计时语义，不得混用同一枚倒计时（发码倒计时 60s；验证码有效期 300s 由服务端控制，前端不重复倒计时）。
- **S9 到期**：用户已裁定**强制重新登录**（不做短信续期），故 S9 直达 S0（保留原路径 `redirect`）。

### 2.4 状态表（状态 → 触发 → 界面表现 → 可行动作 → 错误码）

| 状态 | 触发条件 | 界面表现 | 用户可行动作 | 错误码 |
| --- | --- | --- | --- | --- |
| **S0 初始** | 进入登录页 | 品牌区 + 双通道 Tab（默认密码）+ 空表单 + 主按钮（可用） | 切换通道、输入、提交 | — |
| **S1 填表（密码）** | 用户在密码通道输入 | 字段聚焦环可见；主按钮在必填项为空时**保持可点**（点击后内联校验），不置灰误导 | 输入、清晰/密码可见切换、提交 | — |
| **S2 提交中（密码）** | 点「登录」 | 主按钮进入 `loading`；字段 `readonly`（不 disabled 以免焦点丢失）；不可重复提交 | 等待 | — |
| **S3 设备二次验证** | 密码正确且服务端返回 `needDeviceVerify=true` + `twoFactorTicket` | **卡片切为第 2 步**：说明文案 + 手机号（脱敏只读）+ 验证码输入 + 「获取验证码」（点击后才开始 60s 倒计时）+ 主按钮「验证并登录」+ 「这不是我的设备」次要链接 | 发码、填码、提交、返回 | 1104（分流码，非错误） |
| **S7 校验中（设备验证）** | 提交验证码 | 按钮 `loading`；验证码框 `readonly` | 等待 | — |
| **S10 填表（验证码）** | 切到验证码通道并输入手机号 | 手机号 + 验证码行（「获取验证码」可用；未填合法手机号时点击给内联提示） | 输入、发码、提交 | — |
| **S11 已发码** | 发码成功 | 「获取验证码」→「重新获取（60s）」**禁用**；提示「验证码已发送至 138****5678」（脱敏） | 填码、等待后重发 | 1101（频控时提示文案改） |
| **S12 提交中（验证码登录）** | 提交 | 按钮 `loading` | 等待 | — |
| **S8 成功** | 服务端签发会话 | Toast「欢迎，{姓名}（{角色}）」（成功提示，非错误）；随后按角色/`redirect` 跳转 | — | — |
| **S9 到期强制重登** | 路由守卫/接口返回「需重认证」 | 登录页顶部提示条「登录已到期，请重新登录」（`--state-warning-*`）；表单正常 | 重新登录 | 1108 |
| **E1 口令错** | 账号或密码错误 | 表单顶部内联错误（`role="alert"`） | 修改后重试 | 1001 |
| **E2 账号禁用** | 账号 `status≠1` | 同上 | 联系管理员 | 1002 |
| **E3 端权限不足** | 账号角色不满足端准入 | 同上，文案「该账号无权登录此端」 | 换账号 / 换端 | **1110** |
| **E4 验证码错/过期** | 码不符或 >300s | 验证码字段下方内联错误；「重新获取」恢复可用 | 重发、重填 | 1102 |
| **E5 尝试过多** | 失败 ≥5 次 | 内联错误 + 验证码作废，需重新获取 | 重新发码 | 1103 |
| **E6 频控触发** | 命中手机号/IP/设备频控 | 「获取验证码」禁用 + 提示「发送过于频繁，请稍后再试（{n}s）」 | 等待 | 1101 |
| **E7 图形验证码错** | captcha 开启且校验失败 | 图形验证码区刷新 + 内联错误 | 重填 | 1106 |
| **E8 设备已撤销** | 受信设备被撤销 | 内联错误，回到 S0 | 重新登录 | 1107 |
| **E9 未绑手机号** | 账号无手机号 | 内联错误，提示联系管理员 | 联系管理员 | 1109 |
| **E10 短信服务不可用** | 通道异常/降级失败 | 内联错误 + 「重试」 | 重试 | 1105 |
| **E11 网络异常** | 请求超时/断网 | 内联错误 + 「重试」；保留已填内容 | 重试 | 无码（本地） |
| **E12 参数校验** | 前端/后端字段校验失败 | 字段级内联错误 | 修正 | 400 |

### 2.5 错误码 → 文案 → 呈现位置总表

| 错误码 | 中文文案（逐条定稿） | 呈现位置 | 类型 |
| --- | --- | --- | --- |
| 1001 | `账号或密码错误，请重新输入` | 表单顶部内联 | 提交级 |
| 1002 | `该账号已被停用，请联系管理员` | 表单顶部内联 | 提交级 |
| **1110** | `该账号无权登录此端` | 表单顶部内联 | 提交级 |
| 1101 | `验证码发送过于频繁，请稍后再试` | 验证码行下方内联 + 按钮禁用 | 字段级 |
| 1102 | `验证码错误或已过期，请重新获取` | 验证码字段下方内联 | 字段级 |
| 1103 | `验证码尝试次数过多，请重新获取` | 验证码字段下方内联 | 字段级 |
| 1104 | （非错误）`检测到新设备，需短信验证` | 第 2 步说明区 | 分流提示 |
| 1105 | `短信服务暂不可用，请稍后重试` | 验证码行下方内联 + 重试 | 提交级 |
| 1106 | `图形验证码错误或已失效，请重新输入` | 图形码下方内联 | 字段级 |
| 1107 | `该设备已被撤销，请重新登录` | 表单顶部内联 | 提交级 |
| 1108 | `登录已到期，请重新登录` | 顶部提示条（warning 样式） | 全局提示 |
| 1109 | `该账号未绑定手机号，无法短信验证` | 表单顶部内联 | 提交级 |
| 400 | `请检查填写内容后重试` | 字段级内联 | 字段级 |
| 网络 | `网络异常，请检查网络后重试` | 表单顶部内联 + 重试 | 全局 |

---

## 3. 三端差异化设计

### 3.1 差异总表

| 维度 | 网页端（PC） | 员工端（H5） | 管理端（H5） |
| --- | --- | --- | --- |
| 布局约束 | 宽屏居中单卡（卡片 420px），卡片外为品牌渐变 | 窄屏单列，卡片**通栏**（左右各 `--sp-5`=20px 内边距） | 同员工端 |
| 主目标宽度 | lg ≥1200（md/sm 自适应居中） | 375–430px（主）/ 320–640 兜底 | 同员工端 |
| 输入方式 | 物理键盘；Tab 顺序；Enter 提交 | 虚拟键盘：手机号 `type=tel inputmode=numeric`、验证码 `type=tel autocomplete=one-time-code` | 同员工端 |
| 自动聚焦 | 进入页面**不**抢焦；进入二次验证步**聚焦验证码框** | 进入二次验证步**聚焦验证码框**（并唤起数字键盘） | 同员工端 |
| 交互密度 | 中等（字段高 40px，见 §5.2） | 高（字段高 44px、行高 48px） | 同员工端 |
| 安全提示强度 | 中（`el-alert` 内联） | 中（卡片内说明行，`--fs-caption`） | 同员工端 |
| 品牌名 | 快递驿站智慧管理系统 | 驿站助手 | 驿站精灵 |
| 主色用法 | 背景 `--grad-hero`（深）承白字；卡片白底 | 页面浅渐变（`--color-primary-surface`→`--surface-page`）；主按钮实底 | 同员工端 |
| 端准入 | 仅 ADMIN | STAFF/STATION_ADMIN（ADMIN 待裁定） | 仅 ADMIN |

### 3.2 网页端（PC）

- **落地约束（关键）**：现有 `/login` 复用 `@admin/views/login/index.vue`（**`hrm-admin` 禁改**）。落地路径二选一（§9-A1 待裁定）：
  - **(a) 推荐**：在 Demo 侧新建 [src/pc/views/login/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/login/index.vue)，`pc/router` 的 `/login` 改指向 Demo 页；**保留一期页零改动**（`git diff` 对 `hrm-admin` 为空）。
  - (b) 若坚持继续复用一期页，则 PC 端**无法承载双通道/设备信任**（一期页写死了单通道、裸十六进制背景），需求不成立。**建议 (a)**。
- **布局**：视口等比居中；卡片宽 `--login-card-w`（420px，见 §5.2），卡片圆角 `--r-lg`，阴影 `--e3`；背景 `--grad-hero`（blue-700→blue-800，承白字 ≥6:1）。
- **字号**：标题 `--fs-h1`(20) / 副标题 `--fs-caption`(12) / 字段 `--fs-body`(14) / 按钮 `--fs-body`(14)。**不得沿用一期页的 `22px/13px` 裸值**。
- **辅助链接区**：忘记密码（点击 → 内联说明「请联系管理员重置密码」，不跳外链）；设备管理（PC 无「我的」页 → 见 §9-A5 裁定是否在 PC 提供设备列表页）。

### 3.3 员工端（H5）

- **文件**：复用 [mobile/views/login/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue)（同一页承载两者），品牌按 `?as` / `role` 分流。
- **布局**：`min-height:100vh`；顶部安全区 `padding-top: calc(var(--safe-top) + var(--sp-10))`；底部 `padding-bottom: var(--safe-bottom)`；左右 `--sp-5`。
- **键盘避让**：H5 壳内 WebView，聚焦验证码框时页面须可滚动到按钮（表单区 `scroll-margin-bottom` 保证；不依赖 `visualViewport` 原生支持）。
- **主按钮**：`van-button block type=primary`，高 ≥44px（`--touch-min`）。
- **品牌副标题**：员工端固定「员工端」；`--as=boss` 时不渲染管理员品牌（防泄漏）。

### 3.4 管理端（H5）

- 与员工端**同一实现**；差异仅在：
  - 品牌：`?as=boss`（或登录后 role=ADMIN）→「驿站精灵」+ 副标题「管理员经营视角」；
  - 称谓：统一「管理员」，禁止出现「老板」；
  - 端准入：仅 ADMIN（§1.3）。
- **演示门户口径**：`?as=boss` 的直达链接预填管理员演示账号（沿用现有 `demo/accounts.js` 逻辑，**仅 Mock 态加载**）。

### 3.5 断点与安全区

| 断点 | 定义 | 登录页行为 |
| --- | --- | --- |
| mobile 主 | 375–430px | H5 唯一主目标：单列、卡片通栏、字段 44px |
| mobile 兜底 | 320–640px | 单列不破版；`orientation:landscape` 下 `#app max-width:640px` 居中（[mobile.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss)） |
| sm | 768–992 | PC 卡居中断点（PC 非该断点主目标） |
| md | 992–1200 | PC 卡居中断点 |
| lg | ≥1200 | PC 卡居中，卡片外背景铺满 |

**安全区（H5 壳 WebView）**：`--safe-top: var(--status-bar-height, 0px)`、`--safe-bottom: env(safe-area-inset-bottom, 0px)`（既有 Token）。登录页顶部与底部必须留出，避免被状态栏/Home 指示条遮挡。**不新增 safe-area Token**。

---

## 4. 交互细节规范

### 4.1 双通道切换

- **控件形态**：移动端 `van-tabs`（type `line`，底部条 `--van-tabs-bottom-bar-color`=主色）；PC 端 `el-tabs`。Tab 文案：`密码登录` / `验证码登录`。
- **默认通道**：**密码登录**（存量用户习惯；也避免首屏即要求手机号）。
- **状态保持（可验收）**：切换 Tab **不清空**任何已输入内容——密码通道维护 `{username, password}`，验证码通道维护 `{phone, code}`，两份独立状态；切回原通道时原值仍在。**已提交级错误在切换时清除**，字段级错误保留在各自通道。
- **键盘**：Tab 焦点可达两个 tab；`←/→` 切换（原生 tablist 行为）。

### 4.2 验证码与倒计时

| 项 | 规范 |
| --- | --- |
| 倒计时时长 | **60s**（与后端同手机号 60s 间隔一致，[multi-client §4.4.2](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L572-L581)） |
| 按钮文案 | 初始 `获取验证码`；倒计时中 `重新获取（59s）`…；结束后恢复 `获取验证码` |
| 禁用态 | 倒计时中与提交中禁用；禁用态字色 `--text-disabled`，`aria-disabled="true"`，**保持 44px 热区**（不可用 `display:none`） |
| 重发路径 | 倒计时结束 → 可重发；E6 频控时按服务端 `nextAllowedIn` 覆盖本地倒计时 |
| 频控提示 | `验证码发送过于频繁，请稍后再试`（E6，1101） |
| 验证码有效期 | 300s（服务端权威，**前端不重复倒计时**）；过期后错误提示引导重发（1102） |
| 脱敏 | 发送成功提示中的手机号**脱敏**（`138****5678`）；脱敏规则由服务端/工具函数统一，前端不自行拼接明文 |
| 安全红线 | 验证码**绝不**回显在页面、Toast、日志或响应体（[security-auth-review §4.4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md#L241-L247)） |

### 4.3 新设备二次验证

- **触发**：密码通道提交后服务端返回 `needDeviceVerify=true` + `twoFactorTicket`（1104 为分流码，非错误，**不显示为红色错误**）。
- **提示文案（定稿）**：
  - 标题：`设备验证`
  - 说明：`检测到这是一台新设备。为保障账号安全，请完成短信验证。`
  - 手机号行：`验证手机号：138****5678`（脱敏只读）
- **退路**：次要链接 `这不是我的设备` → 返回 S0 并**清空密码、保留账号**；若用户反复遇到，附一行提示 `如非本人操作，请立即联系管理员`。
- **成功**：服务端下发 `device_token`（**服务端签发**，前端不生成、不存储可复制的信任凭据），并写入 `auth_trusted_device`。前端此步**不持久化任何「设备受信」标志**（防伪造放行——[security-auth-review §4.2](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md#L225-L231)）。
- **设备指纹定位**：前端只采集 `deviceId/platform/model/osVersion/appVersion/screen/timezone`（[multi-client §4.1.3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L442-L463)）作为**弱信号**上报；**设计上不得以「前端记得这台设备」作为跳过验证的依据**。

### 4.4 「记住我」：**不设**（明确决策）

> 结论：登录页**不提供**「记住我 / 自动登录」勾选框。

| 理由 | 说明 |
| --- | --- |
| 语义冲突 | 会话固定 3 天**由服务端权威控制**且到期强制重登；「记住我」会让用户误以为能延长登录，属**误导性交互** |
| 安全面 | 勾选框易被理解为「免验证」，与设备信任需服务端签发矛盾 |
| 替代 | 维持 3 天会话本身即「免频繁登录」；到期重登是用户已裁定的安全口径 |

> `TODO(扩展): 若未来引入「仅记住账号（不记住登录态）」的需求，可作为纯前端便利项单列评估，但与本次会话口径无关。`

### 4.5 3 天到期强制重登

- **告知时机**：
  - **提前**：会话剩余 < 1 天时，在应用内（非登录页）以 `Warning` 提示条提示 `登录即将到期，到期后需重新登录`（不打断当前操作）；
  - **到期时**：跳登录页，顶部提示条 `登录已到期，请重新登录`（`--state-warning-*` 样式，1108）。
- **跳转带 `redirect`**：是。守卫在重定向登录页时带 `redirect=<原路径>`，登录成功后回原页（复用既有 `redirectQuery` 逻辑，[pc/router/index.js:29](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/router/index.js#L29) / [mobile/router/index.js:237](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L237)）。
- **不做短信续期**：用户裁定到期强制重登；B3 端点不在登录页调用（见 §0.2-C1）。

### 4.6 错误提示呈现

| 类型 | 呈现方式 | 组件 |
| --- | --- | --- |
| 字段级（验证码错、频控、图形码错） | **内联**于字段下方 | 移动：`p.login__error`；PC：`el-form-item` 的 `error` |
| 提交级（1001/1002/1110/1105/1107/1109/网络） | **内联**于表单顶部 `role="alert"` 区 | 移动：`p.login__error`；PC：`el-alert type=error` |
| 全局提示（1108 到期） | 页面顶部**提示条** | 移动：`van-notice-bar`（warning）；PC：`el-alert type=warning` |
| 成功（登录成功） | **Toast** | `showSuccessToast` / `ElMessage.success` |

**规则**：**可恢复的表单错误一律内联**，不用 Toast（Toast 会遮挡表单且自动消失，用户来不及读）；Toast 仅用于成功与不可恢复的全局错误。错误区渲染时用 `aria-live="assertive"` 播报。

### 4.7 四态

| 态 | 表现 |
| --- | --- |
| **normal** | 表单可用；主按钮可点（必填为空时点击触发内联校验，**不置灰**） |
| **loading** | 主按钮 `loading`；字段 `readonly`（保留焦点与内容）；按钮禁点防重复提交 |
| **empty** | 初始空表单（占位符在，无错误）；**不显示错误区** |
| **error** | 见 §4.6；错误可恢复，输入即清除该错误 |

### 4.8 键盘与触控可达性

| 项 | 规范 |
| --- | --- |
| Enter 提交 | PC：密码框/验证码框 `@keyup.enter` 提交；移动：`van-form` 提交 |
| Tab 顺序 | 品牌 → Tab → 账号/手机号 → 密码/验证码 → 可见性按钮 → 主按钮 → 辅助链接 → 合规区；**逻辑顺序与 DOM 顺序一致** |
| 焦点可见 | 复用既有统一焦点环（PC [element-overrides.scss:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/element-overrides.scss#L76)、移动 [mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38)）；**不得被 `overflow:hidden` 裁剪** |
| 触控区 | 所有可点元素 ≥44×44px（`--touch-min`）；密码可见性按钮沿用既有 44×44 负边距写法 |
| 密码可见性 | 保留「显示/隐藏密码」按钮，`aria-pressed` + `aria-label`（沿用 [login/index.vue:118-126](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue#L118-L126)） |
| 验证码粘贴 | 支持粘贴 6 位数字；填满后**不自动提交**（防误触），由用户点按钮 |
| 输入属性 | 手机号 `type="tel" inputmode="numeric" autocomplete="tel"`；验证码 `type="tel" inputmode="numeric" autocomplete="one-time-code"`；账号 `autocomplete="username"`；密码 `autocomplete="current-password"` |
| 减少动效 | 登录页动效（卡片切步）须被既有 `prefers-reduced-motion` 覆盖 |

### 4.9 文案清单（逐条定稿，可直接复制）

| 位置 | 文案 |
| --- | --- |
| 密码通道 Tab | `密码登录` |
| 验证码通道 Tab | `验证码登录` |
| 账号占位符 | `请输入登录账号` |
| 密码占位符 | `请输入密码` |
| 手机号占位符 | `请输入手机号` |
| 验证码占位符 | `请输入 6 位验证码` |
| 获取验证码 | `获取验证码` / `重新获取（{n}s）` |
| 主按钮（密码） | `登录` |
| 主按钮（验证码） | `登录` |
| 主按钮（设备验证） | `验证并登录` |
| 空账号校验 | `请输入登录账号` |
| 空密码校验 | `请输入密码` |
| 空手机号校验 | `请输入手机号` |
| 手机号格式校验 | `请输入正确的 11 位手机号` |
| 空验证码校验 | `请输入验证码` |
| 发码成功提示 | `验证码已发送至 {脱敏手机号}` |
| 设备验证标题 | `设备验证` |
| 设备验证说明 | `检测到这是一台新设备。为保障账号安全，请完成短信验证。` |
| 验证手机号行 | `验证手机号：{脱敏手机号}` |
| 返回链接 | `这不是我的设备` |
| 安全提示（设备步） | `如非本人操作，请立即联系管理员` |
| 到期提示条 | `登录已到期，请重新登录` |
| 即将到期提示 | `登录即将到期，到期后需重新登录` |
| 忘记密码 | `忘记密码？` → `请联系管理员重置密码` |
| 演示标识（合规区） | `演示环境 · 短信不会真实发送` |
| 成功 Toast | `欢迎，{姓名}（{角色}）` |

---

## 5. 视觉规范

> **零自创**：以下全部使用既有 Token（[tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss)），不新造色彩/字号。Token 增量见 §5.2 末（仅 1 条尺寸，逐条论证，可裁）。

### 5.1 线框描述（三端）

**移动端（H5）线框**

```
┌──────────────────────────────────────┐  padding-top: safe-top + --sp-10
│  [品牌区]                             │
│   驿站助手                    ← --fs-h1 / --fw-semibold / --text-1
│   员工端 · 移动端演示          ← --fs-caption / --text-2
├──────────────────────────────────────┤  margin-top --sp-6
│  [卡 片]  --surface-card / 1px --border-line / --r-lg / --e0
│  ┌──────────────────────────────┐    │  padding --sp-4
│  │ [切换区] 密码登录 │ 验证码登录  │    │  van-tabs line
│  │                              │    │
│  │ [表单区]                      │    │
│  │  账号  [________________]     │    │  字段高 44
│  │  密码  [________________] 👁  │    │
│  │  ⚠ 内联错误区（role=alert）    │    │  --color-danger / --fs-caption
│  │  [        登录        ]       │    │  --color-primary / 白字
│  └──────────────────────────────┘    │
│                                      │
│  [辅助链接]  忘记密码？                │  --fs-caption / --color-primary
│  [合规区]    演示环境 · 短信不真实发送  │  --state-simulate-* / --fs-caption
└──────────────────────────────────────┘
```

**设备验证步（替换卡片内 ③）：**

```
┌──────────────────────────────────────┐
│  设备验证                    ← --fs-h2 / --fs-h3
│  检测到这是一台新设备。为保障账号安全， │  --fs-caption / --text-2
│  请完成短信验证。                     │
│  验证手机号：138****5678     ← 只读 / --text-2
│  验证码 [________] 获取验证码 │  字段 44 + 右侧按钮（60s 倒计时）
│  [      验证并登录      ]    │
│  这不是我的设备               ← 次要链接 / --color-primary
│  如非本人操作，请立即联系管理员 │  --fs-caption / --text-3（纯白卡上 4.83:1 ✓）
└──────────────────────────────────────┘
```

**PC 端线框**

```
┌──────────────────────────────────────────────────────────┐
│  背景：--grad-hero（blue-700 → blue-800，承白字 ≥6:1）      │
│                                                          │
│              ┌────────────────────────┐                  │
│              │ [卡 片] 宽 --login-card-w(420)              │
│              │   快递驿站智慧管理系统   ← --fs-h1 / --text-1│
│              │   管理员登录             ← --fs-caption      │
│              │  ────────────────                        │
│              │  密码登录 | 验证码登录   ← el-tabs          │
│              │  账号  [____________]   ← 字段高 40          │
│              │  密码  [____________] 👁                    │
│              │  ⚠ 内联错误（el-alert error）               │
│              │  [        登 录        ]                    │
│              │  忘记密码？                               │
│              └────────────────────────┘（--e3 阴影）      │
└──────────────────────────────────────────────────────────┘
```

### 5.2 尺寸与间距

| 元素 | 移动端 | PC | Token |
| --- | --- | --- | --- |
| 页面左右内边距 | 20px | — | `--sp-5` |
| 品牌区上边距 | 40px（+安全区） | 卡片内 24px | `--sp-10` / `--sp-6` |
| 标题字号/行高 | 20 / 28 | 20 / 28 | `--fs-h1` / `--lh-h1` |
| 副标题 | 12 / 18 | 12 / 18 | `--fs-caption` / `--lh-caption` |
| 卡片内边距 | 16px | 24px | `--sp-4` / `--sp-6` |
| 卡片圆角/阴影/描边 | `--r-lg` / `--e0` / `--border-line` | `--r-lg` / `--e3` / `--border-line` | 同名 |
| 字段高度 | 44px | 40px | `--touch-min` / Element `--el-component-size-large`(36)+间距 |
| 主按钮高度 | 44px | 40px | `--touch-min` |
| 字段间距 | 12px | 16px | `--sp-3` / `--sp-4` |
| 主按钮上边距 | 20px | 24px | `--sp-5` / `--sp-6` |
| 错误/辅助文字 | 12 / 18 | 12 / 18 | `--fs-caption` / `--lh-caption` |
| 密码可见性热区 | 44×44 | 40×40 | `--touch-min` |
| 状态标签高（若用） | 20 | 24 | `--tag-h`（分端） |

**Token 增量（仅 1 条，逐条论证）：**

| Token | 端 | 取值 | 理由 | 可否不加 |
| --- | --- | --- | --- | --- |
| `--login-card-w`（L3 Component） | PC | `420px` | 登录卡宽度是三端登录页唯一需要的**新尺寸**；一期页写死 `400px`（裸值），新页若再写裸值会形成第三份魔数。收口为 Token 便于将来改密锁定卡复用 | **可裁**：若判定登录卡仅此一处，可用 `--sp-*` 组合表达而不加（见 §9-A2） |

> 色彩、字号、间距、圆角、阴影**零新增**；演示提示条复用既有 `--state-simulate-*`（[mobile/tokens.scss:76-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L76-L78)），**不新增**。

### 5.3 色彩与对比度

| 用途 | Token | 实测对比度 | 判定 |
| --- | --- | --- | --- |
| 页面主标题 | `--text-1`（#1F2937）对浅底 | ≥ 12:1 | ✓ |
| 品牌副标题（渐变近顶区） | `--text-2`（#4B5563） | ≥ 7:1 | ✓ 必须用 `--text-2`（**不可用 `--text-3`**：其对合成渐变底约 4.33:1，不达 AA，沿用既有 [#login 注释](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue#L162-L169)） |
| 副标题（纯白卡片上） | `--text-3`（#6B7280）对白 | 4.83:1 | ✓ |
| 主按钮文字 | `--text-inverse` 对 `--color-primary`（#0958D9） | 6.16:1 | ✓ |
| 错误文字 | `--color-danger`（#CF1322）对白 | 5.57:1 | ✓ |
| 字段描边（非文本） | `--border-control`（#8A93A0）对白 | 3.10:1 | ✓ ≥3:1 |
| 占位符 | `--text-placeholder`（#9AA4B2）对白 | 2.52:1 | ⚠ 既有登记反例；**仅用于占位符**，不得承载必读信息 |
| PC 背景承白字 | `--grad-hero`（blue-700→800） | ≥ 6.16:1 | ✓ |
| 演示提示条 | `--state-simulate-bg/fg/border` | fg/bg ≥ 4.5:1 | ✓ |

**硬规则**：登录页所有正文与按钮文字 ≥4.5:1；大字（≥18.66px bold 或 ≥24px）≥3:1；聚焦环与非文本控件 ≥3:1；**状态不只靠颜色**（错误带文字、演示带文字标签）。

### 5.4 焦点态

- 复用两端既有统一焦点环（见 §4.8）；登录页内**新增的所有可交互元素**（Tab、验证码按钮、返回链接）必须继承焦点环，不得 `outline:none`。
- 二次验证步切换后，焦点**程序化移到验证码输入框**（同时满足「键盘用户不迷失」与「移动端唤起数字键盘」）。

### 5.5 响应式与 safe-area

- 移动端：`min-height:100vh`，上/下安全区内边距；横屏 `#app max-width:640px` 居中，登录卡保持单列不裂为两列。
- PC：卡片 `--login-card-w` 居中；视口高度不足时允许纵向滚动（不裁切按钮）。
- WebView 内：`--status-bar-height` 由壳 `bridge.js` 注入；浏览器下为 0，布局不塌陷（既有机制）。

---

## 6. 演示态 vs 生产态差异

> 最高原则：**验证码/凭据不得出现在公开 JS 产物或页面可见区域**（[安全基线 §4](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-auth-review.md)、[项目规则 §10.4](file:///d:/Users/16626/Desktop/kdyzgl-base/.trae/rules/项目规则1.md)）。

| 维度 | 演示态（Mock，`VITE_MOCK_ENABLED=true`） | 生产态 |
| --- | --- | --- |
| 短信发送 | **不真实发送**；Mock 返回 `{ sent:true, expireIn:300, nextAllowedIn:60, requireCaptcha:false }` | 阿里云短信 SDK 真实下发（`AliyunSmsSender`） |
| 验证码取值 | **固定码**（建议 `000000`），来源与后端 `LoggingSmsSender` 的 `hrm.sms.dev-fixed-code` 对齐 | `SecureRandom` 生成 6 位，一次性、TTL 300s、尝试上限 5 |
| 固定码落点（合规） | ① 只写在**演示剧本资产**（`src/demo/` 下，仅 `VITE_MOCK_ENABLED` 分支动态 import，**生产构建剔除**）；② 后端降级实现 `LoggingSmsSender`（`hrm.sms.provider=none`）；③ 演示文档/口播。**均不在登录页 DOM、不在公开产物** | 不存在固定码 |
| 页面提示 | 合规区/提示条显示 `演示环境 · 短信不会真实发送`（`--state-simulate-*` 样式，**不含具体验证码**） | 无演示标识 |
| 频控 | 可选模拟（默认首次发码即成功，便于演示） | 同手机号 60s/10 次·天、同 IP 20 次·时、同设备 10 次·时（三维度） |
| 图形验证码 | **不开启**（`requireCaptcha:false`） | 风控触发时开启（D1 端点，`requireCaptcha:true` 前置） |
| 降级 | Mock 直连，无外部依赖 | Key 未配置时 **fail-closed**（禁用短信通道并明确提示，**绝不弱兜底**） |
| 验证码日志 | 不记录 | 绝不入日志/响应/异常（`ClientLogSanitizer` 白名单补充 `code=`/`smsCode=` 形态） |
| 设备信任 | Mock 服务端签发 `device_token`（可简化为内存态），前端仍不持久化信任标志 | 服务端签发，`auth_trusted_device` 表 + 有效期（如 30 天）+ 单账号最大设备数 |

> **演示者如何取码**：翻看演示剧本/门户说明（演示资产），而非从页面读取。**禁止**把固定码做成页面彩蛋、控制台输出或 `window.__DEMO_CODE__` 之类可在生产复用的形式。
> `TODO(扩展): 演示剧本中固定码的展示形态，待与主智能体确认是否统一由 `demo/` 目录下的说明页承载。`

---

## 7. 与前端的交接契约

> 依据 [multi-client-architecture.md §4.1](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/multi-client-architecture.md#L408-L440)。**本节只汇总登录页消费的端点**，不新增契约（除 §7.4 登记的 1110 与可选字段口径）。路径统一前缀 `/api/v1`。

### 7.1 逐端接口清单

**网页端（PC，仅 ADMIN）**

| # | 方法 路径 | 入参（登录页相关） | 出参（登录页相关） | 错误码 |
| --- | --- | --- | --- | --- |
| B1 | `POST /auth/login` | `username`、`password`、`clientType:"WEB"`、`device{deviceId,platform:"WEB",model,osVersion,screen,timezone}` | `token`、`expiresIn`(259200)、`employee`、`deviceTrusted`、`needDeviceVerify`、`twoFactorTicket?`、`sessionExpireAt` | 1001/1002/**1110**/1104 |
| A1 | `POST /auth/sms/send` | `phone`、`scene:"DEVICE_VERIFY"`、`deviceId`、`captchaTicket?`、`captchaCode?` | `sent`、`expireIn`、`nextAllowedIn`、`requireCaptcha` | 1101/1105/1106/1109 |
| B2 | `POST /auth/device/verify` | `twoFactorTicket`、`phone`、`code` | `LoginVO` + `sessionExpireAt` + `deviceTrusted:true` | 1102/1103/1107/1110 |
| C1 | `GET /auth/devices` | — | `[{deviceId,platform,model,lastIp(脱敏),lastSeenTime,current}]` | 401 |
| C2 | `DELETE /auth/devices/{deviceId}` | — | `{}` | 401/1107 |
| D1 | `GET /auth/captcha`（可选） | — | `{ticket,imageBase64,expireIn}` | 1106 |
| B4 | `POST /auth/logout`（既有） | `all?`(默认 false) | `{}` | 401 |
| — | `GET /auth/me`、`PUT /auth/password`（既有） | 不变 | 不变 | 既有 |

**员工端（H5，允许 STAFF/STATION_ADMIN）**

- 端点集合同 PC，但：`clientType:"H5"`、`device.platform:"H5"`；**主推验证码通道 A2**。
- `POST /auth/sms/login`（A2）：`phone`、`code`、`clientType:"H5"`、`device{...}` → `LoginVO` + `sessionExpireAt` + `deviceTrusted:true`；错误码 1001/1002/1102/1103/1107/**1110**。
- 密码通道 B1 同样可用（`needDeviceVerify` 分支同上）。

**管理端（H5，仅 ADMIN）**

- 端点集合与员工端一致；差异仅在**服务端端准入**（非 ADMIN → 1110）。
- 品牌/称谓由前端按 `role` 分流（`ADMIN`→驿站精灵）。

**未启用端点声明**：`B3 POST /auth/session/renew` **本设计不调用**（用户裁定到期强制重登，§0.2-C1）。

### 7.2 Mock 需新增的路由（`shared/mock/routes/auth.js` 扩展点）

**保留现状**（[auth.js:88-93](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/auth.js#L88-L93)）：`POST /auth/login`、`POST /auth/logout`、`GET /auth/me`、`PUT /auth/password` —— **入参/出参不得变更**（§7.3）。

**新增路由（本轮只登记契约，不实现）**：

| 方法 路径 | auth | roles | 说明 |
| --- | --- | --- | --- |
| `POST /auth/sms/send` | `false`（白名单） | — | 发码；演示恒成功，返回 `nextAllowedIn:60` |
| `POST /auth/sms/login` | `false` | — | 短信登录；固定码校验 |
| `POST /auth/device/verify` | `false` | — | 新设备二次验证；校验 `twoFactorTicket` |
| `GET /auth/devices` | 需登录 | `ALL_ROLES` | 本人设备列表（脱敏 IP） |
| `DELETE /auth/devices/:deviceId` | 需登录 | `ALL_ROLES` | 撤销设备 |
| `GET /auth/captcha`（可选） | `false` | — | 图形验证码；演示态默认不启用 |

**Mock 数据/状态扩展（登记，不实现）**：

- 新增集合：`trustedDevices`（对齐 `auth_trusted_device`）、`smsLogs`（对齐 `auth_sms_log`，仅存脱敏手机号）；
- 会话扩展：为会话记录补 `deviceId` / `expireAt`（3 天），`expiresIn` 由 `86400` 改 `259200`（与契约一致）；
- 固定码：仅 `VITE_MOCK_ENABLED` 分支内定义（§6）。

### 7.3 不得改动的既有契约（硬约束）

| 契约 | 约束 |
| --- | --- |
| `POST /auth/login` 既有字段 | `token` / `expiresIn` / `employee` 的名称、类型、语义**不得删除或改名** |
| `POST /auth/logout` / `GET /auth/me` / `PUT /auth/password` | 入参/出参**不得变更** |
| 错误码 1001 / 1002 | 码值与文案语义**不变** |
| Mock 既有断言 | **不得**为通过而修改既有验收资产（反模式 A06） |

**「新增可选字段」口径（须裁准确认，§0.2-C2）**：本设计判定 `/auth/login` **追加可选出参**（`deviceTrusted`/`needDeviceVerify`/`twoFactorTicket`/`sessionExpireAt`）与**可选入参**（`clientType`/`device`）属**向后兼容扩展**，不构成「变更既有契约」；前提是既有字段零改动、老前端忽略新字段仍可正常工作。**若主智能体判定这也算「变更」，则须为设备判定增设独立预检端点，本设计的 B1 改造方案需相应调整。**

### 7.4 新增登记（须写入 api.md 定稿）

| # | 项 | 现状 | 建议 |
| --- | --- | --- | --- |
| N1 | **错误码 1110**「该账号无权登录此端」 | `multi-client` 11xx 段为 1101–1109，无 1110；真源 `errorCode.js` 无 11xx | 新增 1110，并同步 `CODE_MESSAGE` |
| N2 | 11xx 段 1101–1109 | 真源 `errorCode.js` **完全缺失** 11xx | 全段补入 `errorCode.js` + `CODE_MESSAGE`（每码必须有文案） |
| N3 | `clientType`（`WEB`/`H5`）入参 | 契约未显式列出 | 由后端在契约修订稿中确认字段名与取值 |

---

## 8. 验收清单（可静态审查）

> 供测试工程师 / UI/UX 走查逐条勾选。**本轮无实现**，故为「设计符合性」检查项；实现后由走查复核。

### A. 结构与状态机

- [ ] 登录页含 5 档区块（品牌/切换/表单/辅助链接/合规），顺序正确（§2.1）。
- [ ] 双通道可切换，默认「密码登录」（§4.1）。
- [ ] 状态机覆盖：S0/S1/S2/S3/S7/S10/S11/S12/S8/S9 + E1–E12（§2.4）。
- [ ] 新设备二次验证为**卡片内第 2 步**，非新页面/非弹窗（§4.3）。
- [ ] 到期重登直达 S0 且带 `redirect`（§4.5）。

### B. 交互细节

- [ ] 切换通道**不清空**已输入内容（§4.1）。
- [ ] 验证码倒计时 60s；禁用态保持 44px 热区；频控按 `nextAllowedIn` 覆盖（§4.2）。
- [ ] 发码成功提示手机号**脱敏**（§4.2）。
- [ ] **无**「记住我」控件（§4.4）。
- [ ] 可恢复错误**内联**呈现，`role="alert"`；成功用 Toast（§4.6）。
- [ ] 四态齐全：loading/empty/error/normal（§4.7）。
- [ ] Enter 提交、Tab 顺序与 DOM 一致、焦点可见、触控 ≥44px（§4.8）。

### C. 视觉与 Token

- [ ] 无裸十六进制；无 Token 色板外颜色；无紫色系（§5.3）。
- [ ] 登录页文本对比度 ≥4.5:1，大字 ≥3:1，非文本 ≥3:1（§5.3、附录 A）。
- [ ] 品牌副标题**未**使用 `--text-3` 于渐变近顶区（§5.3）。
- [ ] Token 增量 ≤1（`--login-card-w`）且已论证（§5.2）。
- [ ] 演示标识复用 `--state-simulate-*`，未新增（§5.2、§6）。

### D. 三端差异化

- [ ] 品牌名走 `resolveAppName()` 真源；文案无「老板」（§3.4）。
- [ ] 未认证态**不泄漏**另一端品牌（§2.2）。
- [ ] 端-角色准入与 1110 一致（§1.3）。
- [ ] 断点：移动 375–430 单列；PC 居中断点；safe-area 生效（§3.5）。

### E. 安全与演示态

- [ ] 验证码不出现在页面可见区域、Toast、公开产物（§6）。
- [ ] 固定码仅存在于演示资产/降级实现，生产构建剔除（§6）。
- [ ] 前端**不持久化**「设备受信」标志；设备信任由服务端签发（§4.3）。
- [ ] 无诱导泄露密码/验证码的文案（§1.2-P2）。

### F. 契约

- [ ] 4 个既有 auth 端点入参/出参未变（§7.3）。
- [ ] 新增端点与 §7.1 对齐；1110/11xx 已登记（§7.4）。
- [ ] B3 未被登录页调用（§0.2-C1）。

---

## 9. 待用户裁定项

| # | 事项 | 现状 | 建议 | 备选 | 影响 |
| --- | --- | --- | --- | --- | --- |
| **A1** | PC 登录页落地路径 | `/login` 复用 `@admin/views/login/index.vue`（hrm-admin **禁改**） | **在 Demo 侧新建 `pc/views/login/index.vue`，路由改指向**（保留一期页零改动） | ① 维持复用 → 需求（双通道/设备信任）不成立；② 改 hrm-admin → **违反硬约束，升级主智能体** | PC 端登录改造能否落地 |
| **A2** | 是否新增 `--login-card-w`(PC 420px) | 无该 Token；一期页写死 400px 裸值 | 新增 1 条并论证（§5.2） | 不加，用 `--sp-*` 组合表达 | Token 增量 |
| **A3** | 员工端/管理端的端准入 | 移动端共用页按角色分流；PC 仅 ADMIN 已定 | 员工端允许 STAFF/STATION_ADMIN，管理端仅 ADMIN；两端对**异端角色**返回 1110 | 移动端继续按角色分流、不做端拒绝 | 1110 触发面、Mock 校验口径 |
| **A4** | 「新增可选字段」是否算变更既有契约 | 任务约束「4 端点出入参不得变更」 vs 契约 B1 需新增字段 | 认定为**向后兼容扩展**，不算变更（§7.3） | 另设独立预检端点 | 契约与 Mock 形态 |
| **A5** | PC 端「设备管理」入口 | PC 无「我的」页 | PC 登录页**只给文字指引**（联系管理员/移动端自助）；不在 PC 做设备列表页 | 在 PC 单独做设备管理页 | 页面范围 |
| **A6** | 演示固定码承载位置 | 未定 | 由 `src/demo/` 演示说明承载，页面只显示「演示环境」标识（§6） | 门户页承载 | 演示体验 |
| **A7** | 1110 与 11xx 全段入 `errorCode.js` | 真源无 11xx | 全段补入并同步 `CODE_MESSAGE`（§7.4） | 由前端硬编码兜底（**不推荐**，违反单一真源） | 错误码真源 |

> 依调度规则：**A1 属「须改 hrm-admin 才可实现」类升级项**；其余为口径确认（P1 口径先行，未确认不得实现）。

---

## 附录 A：对比度实算

计算式（WCAG 2.x 相对亮度）：`L = 0.2126R + 0.7152G + 0.0722B`（各通道线性化），对比度 `=(L1+0.05)/(L2+0.05)`。

| 前景 / 背景 | 比值 | 标准 | 判定 |
| --- | --- | --- | --- |
| `--text-1` #1F2937 / `--surface-card` #FFFFFF | 12.63:1 | ≥4.5 | ✓ |
| `--text-2` #4B5563 / #FFFFFF | 7.03:1 | ≥4.5 | ✓ |
| `--text-3` #6B7280 / #FFFFFF | 4.83:1 | ≥4.5 | ✓ |
| `--text-3` #6B7280 / 渐变近顶（≈#E8F4FF 合成底） | ≈4.33:1 | ≥4.5 | ✗ **→ 登录页该区改用 `--text-2`** |
| `--text-inverse` #FFFFFF / `--color-primary` #0958D9 | 6.16:1 | ≥4.5 | ✓ |
| `--color-danger` #CF1322 / #FFFFFF | 5.57:1 | ≥4.5 | ✓ |
| `--border-control` #8A93A0 / #FFFFFF | 3.10:1 | ≥3（非文本） | ✓ |
| `--text-placeholder` #9AA4B2 / #FFFFFF | 2.52:1 | ≥4.5（严格） | ⚠ 仅占位符（既有登记反例） |
| `--text-inverse` #FFFFFF / `--c-blue-800` #0745A8 | ≈8.9:1 | ≥4.5 | ✓ |

> 数值为手算（误差 ±0.05）；实现后由测试工程师用工具复核。

## 附录 B：术语与引用

| 术语 | 含义 |
| --- | --- |
| 双通道登录 | 密码登录 + 短信验证码登录，可在登录页切换 |
| 设备信任 | 服务端签发的 `device_token` / `auth_trusted_device` 记录；前端指纹仅为弱信号 |
| 1110 | 「该账号无权登录此端」，本设计登记的**新增**错误码（§7.4-N1） |
| 到期强制重登 | 会话 3 天到期后重新登录（用户裁定，替代契约建议的短信续期） |
| 驿站助手 / 驿站精灵 | 员工端 / 管理端应用名（[appName.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/appName.js) 真源） |

---

**交付边界声明**：本文件为**设计规范**，未修改任何 `hrm-demo`/`hrm-admin`/`hrm-server` 源码、Mock、契约文档、迁移脚本或 `.trae/rules/`。实现由前端工程师按本规范落地（L2），视觉走查由本角色执行。
