# 快递驿站智慧管理系统 · PC 端「系统设置」页 UI/UX 规范 + 10 字全名显示规范

> 版本 v1.0 | 建立日期 2026-09-23 | 范围：**仅网页端（PC）/ `hrm-dev/hrm-demo/src/pc/**`**
> 口径来源（已冻结，不另起体系）：`demo-pc-refactor.md`（目录/组件契约/四态/行为等价红线）、`demo-ui-redesign.md`（Design Tokens / 逐页规范）、现有页面范式 `views/dashboard/index.vue`、`views/system/logs.vue`
> 本文档**只出规范，不含实现代码**；实现由前端工程师按本规范进行，测试工程师按 §1.6 / §4 判据验收。
> 硬约束：不改 `hrm-dev/hrm-admin/**`、`hrm-dev/hrm-server/**`、`src/mobile/**`、`scripts/**`；不执行 git 写操作。

---

## 0. 范围、取证方式与不确定项

### 0.1 本轮范围（硬约束）

1. 产品名由「快递驿站智汇系统」改为「**快递驿站智慧管理系统**」（10 个汉字），**仅网页端（PC）**。
2. 侧栏 logo 由 5 字简称改为**完整 10 字全名**；侧栏展开 210px / 折叠 64px。
3. 新增「系统设置」页：PC 侧栏「系统」分组，路由 `/system/settings`。
4. 移动端（`mobile.html` / `src/mobile/**`）、端选择入口页（`index.html` / `src/portal/**`）、`.env.demo` 的 `VITE_DEMO_TITLE` **不在本轮范围**（见 §3 风险点 6）。

### 0.2 取证方式

全部数值取自仓库内既有事实，非凭印象：

| 取值 | 来源（已读原文核对） |
| --- | --- |
| `--aside-w: 210px` / `--aside-w-collapsed: 64px` / `--header-h: 60px` | `src/pc/styles/tokens.scss` L44-46 |
| `--fs-h3: 15px` / `--fs-h1: 20px` / `--fs-body: 14px` / `--fw-semibold: 600` | `src/pc/styles/tokens.scss` L18-33、`tokens.base.scss` L208-210 |
| `--sp-2: 8px` / `--sp-4: 16px` / `--r-md: 8px` / `--color-primary-border` 等 | `src/shared/styles/tokens.base.scss` L213-226 |
| 侧栏 logo 结构（图标 `:size="22"`、`gap: var(--sp-2)`、标题 `--fs-h3`/`--fw-semibold`） | `src/pc/layout/index.vue` L5-8、L210-229 |
| 折叠阈值 `(max-width: 1199px)`、折叠态标题 `v-show="!isCollapse"` | `src/pc/layout/index.vue` L102、L7 |
| 浏览器标题拼接 `${title} - 快递驿站智汇系统` | `src/pc/router/index.js` L250-252 |
| 静态入口标题 `网页端 · 快递驿站智汇系统` | `pc.html` L11 |
| PC 登录页标题（22px/600，卡片 400px，冻结） | `hrm-admin/src/views/login/index.vue` L8、L141-159 |
| `VITE_MOCK_ENABLED` / `VITE_MOCK_PARCEL_COUNT=200000` / `VITE_API_BASE` | `.env.demo`、`.env.production`、`src/pc/main.js` L96 |
| `getParcelSummary()`（既有接口，`GET /parcels/summary`） | `src/pc/api/parcel.js` L19-22 |
| 角色标签 `ROLE_LABEL`、登录用户 `useAuthStore().user` | `src/shared/constants/role.js` L11-15、`src/pc/main.js` L27 |
| 侧栏分组 `sys: '系统'`、分组图标 `Setting`、卡片标题用 `<h2>` | `src/pc/config/menu.js` L15-28、`src/pc/components/MiniStats.vue` L37 |

**运行验证局限**（沿用 `demo-pc-refactor.md` §8 / `SESSION-STATE.md`）：本机 TRAE Chrome 扩展不可用、内置浏览器窗口固定 810×658，**宽屏（≥1200）与真机尺寸未覆盖**，相关判据标注为「静态审查 / 待宽屏复测」。

### 0.3 不确定项（反幻觉声明，必须知情）

1. 任务所述「T7 走查中已证伪的 8 项结论」**未在仓库中定位到原文**。已改用仓库内已归档的证伪/正常记录（`SESSION-STATE.md` L141-143/L262-267、`hrm-demo/README.md` L429-438、`demo-milestones.md` D-10/D-16）编制 §3.3；若确有其文，请提供路径以对齐。
2. **登录页标题落点与「hrm-admin 冻结」硬约束冲突**：PC 登录页复用一期冻结页 `@admin/views/login/index.vue`，标题写死在组件内、无插槽、无 CSS 可改文案的通道。本规范给出目标值，但**实现方案需主智能体裁决**（见 §1.5、§3 风险点 2）。
3. 375px 属 PC 不承诺区间（`demo-pc-refactor.md` §8）；「改名后不出现半截字」依赖折叠阈值 1200（<1200 时标题隐藏），理论安全，但**未在真机/窄窗实测**。
4. 「系统设置」可见角色本轮**收敛为仅 ADMIN**（与运行日志同组同口径）。若产品要求站长/员工可见只读，需同步改 `MENU_WHITELIST` 与路由 `meta.roles`。
5. 版本号注入方式（Vite `define` vs 前端常量）属工程实现选择，本规范给建议方案。
6. Firefox / WebKit 本机无自动化环境，相关检查项**未实测**，标注为静态审查。

---

## 1. 产品更名（10 字全名）显示规范

### 1.1 名称与字符核算基准

- 全名字符串：`快递驿站智慧管理系统`（10 个汉字，**无空格、无标点**）。
- 汉字宽度基准：CSS 中 CJK 表意文字为全角，**advance = 1em = 字号**（依据：W3C CSS Fonts/Text 中全角宽度定义；本工程字体栈 `--font-sans` 首选 `PingFang SC`(macOS) / `Microsoft YaHei`(Windows)，二者汉字均约 1em）。
- 因此：
  - 10 字 @ 15px = **150px**
  - 10 字 @ 22px = **220px**
  - 10 字 @ 18px = **180px**
  - 10 字 @ 14px = **140px**
- 工程容差：不同内核/字体对汉字 advance 可能有 <5% 偏差，宽度核算统一**预留 ≥10% 余量**后再判是否溢容。

### 1.2 落点清单与具体取值

| # | 落点 | 实现文件（现状） | 字号/字重/颜色 | 容器宽 | 10 字占宽 | 溢出策略 | 本轮动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R1 | PC 侧栏 logo（展开 210px） | `src/pc/layout/index.vue:7`（文案）、`:223-228`（样式） | `--fs-h3` 15px / `--fw-semibold` 600 / `--text-inverse` `#FFF` | 210px（`--aside-w`），`.app-logo` 无 padding | 150px | **`white-space: nowrap`（已存在）+ 新增 `flex-shrink: 0`**；不使用 `text-overflow: ellipsis` | 文案改为 10 字；补 `flex-shrink: 0` |
| R2 | PC 侧栏 logo（折叠 64px） | 同上（`v-show="!isCollapse"`） | —（隐藏） | 64px | —（不渲染） | `display: none`（`v-show` 已实现） | 保持现状 |
| R3 | 浏览器标题栏（运行时） | `src/pc/router/index.js:251` | 浏览器渲染，无字号要求 | 视口宽度 | — | 由浏览器自行处理标签截断 | 拼接串改为 `快递驿站智慧管理系统` |
| R4 | 浏览器标题栏（静态首屏） | `pc.html:11` | 同上 | — | — | 同上 | `<title>网页端 · 快递驿站智慧管理系统</title>` |
| R5 | PC 登录界面标题 | `hrm-admin/src/views/login/index.vue:8`（**冻结，不可改**） | 现状 22px / 600 | 卡片 `width:400px`，内容宽 ≈ 384px | 220px | 单行 `nowrap` | **阻塞**，见 §1.5 |
| R6 | 系统设置页「系统名称」字段 | 新建 `src/pc/views/system/settings.vue` | `--fs-body` 14px / `--fw-medium` 500 / `--text-1` | 卡片内自适应 | 140px | 单行，不截断 | 新增（只读，不复制，见 §2.6） |

> 说明：R5 若纳入并改名，10 字 @22px = 220px ≤ 384px，**单行不换行**；换行规则（仅当容器更窄时）为「优先 `white-space: nowrap`，容器确实不足时按**语义整字断行**，禁止拆字，`line-height` 与字号等比」。

### 1.3 侧栏 logo 宽度核算（R1 / R2）

**展开态（210px）**

```
容器（.app-aside 内容区）  = --aside-w           = 210px
内容 = 图标 22px + gap 8px(--sp-2) + 标题 150px = 180px
余量 = 210 − 180                                 = 30px（左右各 15px）
```

- 判定：30px 余量 ≥ 2×`--sp-2`(8px) 的视觉最小边距 → **通过，无需调 Token**。
- **最小可用宽度 = 180px**（图标 22 + 间距 8 + 标题 150）；规范保留 `--aside-w: 210px`，**不改**。
- 风险与对策：现状 6 字（120px）无压缩问题；改 10 字后若字体渲染略宽、或将来再加字，`flex` 默认 `flex-shrink:1` 会压缩 `.app-logo__title`，而 `.app-aside{overflow:hidden}` 会把末字裁成「半截字」。**必须补 `flex-shrink: 0`**（防御性，不改变当前视觉）。

**折叠态（64px）**

- `.app-logo__title` 由 `v-show="!isCollapse"` 控制 → `display:none`，仅图标 22px 居中于 64px，**无截断风险**。

### 1.4 浏览器标题栏拼接规则（R3 / R4）

- 运行时规则（`router.afterEach`）：`${to.meta.title} - 快递驿站智慧管理系统`；`meta.title` 缺失时回退 `快递驿站智慧管理系统`。
- 示例：`/dashboard` → `数据看板 - 快递驿站智慧管理系统`；`/login`（`meta.title='登录'`）→ `登录 - 快递驿站智慧管理系统`。
- 静态首屏（`pc.html`）：`网页端 · 快递驿站智慧管理系统`（router 未挂载前/刷新瞬间可见）。
- **两处必须同改**：只改其一会出现「刷新瞬间旧名、随后新名」的跳变（见 §3 风险点 3）。

### 1.5 登录界面标题（R5，含冻结冲突）

- 事实：Demo 的 `/login` 直接复用一期冻结页 `@admin/views/login/index.vue`（`src/pc/router/index.js:35`）。标题为组件内硬编码 `<h2 class="login-title">快递驿站智汇系统</h2>`，22px/600、`text-align:center`、无 `nowrap`，卡片固定 400px。
- 约束：硬约束「不得改动 `hrm-admin/**`」→ **无法在不修改冻结文件的前提下改登录页标题文案**；纯 CSS 只能改字号，改不了文字。
- 本规范结论（供主智能体裁决，三选一，均须显式登记）：

  | 方案 | 做法 | 代价 / 风险 |
  | --- | --- | --- |
  | A 接受偏差 | 登录页保留旧名，本文档登记为**已知偏差** | 网页端「登录页旧名 / 其余新名」不一致 |
  | B 解冻一期 | 允许改 `hrm-admin` 该行 | **违反硬约束**，需用户/主智能体明确授权 |
  | C Demo 侧包装 | Demo 另建 `/login` 包装组件承接（不改冻结文件） | 一期登录页无插槽，包装只能加外层、仍改不了内部文案；若复制实现则违反「不复制一期源码」（`demo-pc-refactor.md` §2/§6.3） |

- 目标值（仅方案 B/C 可行时适用）：字号 22px / 字重 600 / 单行不换行 / 容器 ≥ 384px；10 字 = 220px。
- 移动端登录页（`src/mobile/views/login/index.vue:64`）属范围外，若将来纳入：18px、容器 ≈335px、10 字 = 180px → 单行。

### 1.6 各落点验收判据（可脚本化）

> 统一前置：浏览器 DevTools 控制台或 e2e 中取 `document`，`aside = document.querySelector('.app-aside')`，`title = document.querySelector('.app-logo__title')`。

| 落点 | 验收判据（全部须为真） |
| --- | --- |
| R1 展开态 | ① 视口 ≥1200px；② `aside.offsetWidth === 210`；③ `title.textContent.trim() === '快递驿站智慧管理系统'`；④ **`title.scrollWidth <= title.clientWidth`**（无裁切）；⑤ `title.getBoundingClientRect().right <= aside.getBoundingClientRect().right`；⑥ `getComputedStyle(title).textOverflow !== 'ellipsis'` 或未产生省略号；⑦ `getComputedStyle(title).flexShrink === '0'` |
| R2 折叠态 | ① 视口 <1200px；② `aside.offsetWidth === 64`；③ `getComputedStyle(title).display === 'none'`；④ 页面无横向滚动：`document.documentElement.scrollWidth <= document.documentElement.clientWidth` |
| R3 运行时标题 | 在各路由断言 `document.title`：`/dashboard`→`'数据看板 - 快递驿站智慧管理系统'`；任意无 `meta.title` 场景→`'快递驿站智慧管理系统'` |
| R4 静态标题 | `pc.html` 源码 `<title>` 文本 **全等** `'网页端 · 快递驿站智慧管理系统'`（源码级断言，非渲染） |
| R5 登录标题 | 若方案 B/C 落地：`document.querySelector('.login-title').textContent.trim() === '快递驿站智慧管理系统'` 且 `scrollWidth <= clientWidth`（单行）；未落地则登记偏差，不设断言 |
| R6 设置页字段 | 系统名称值节点 `textContent.trim() === '快递驿站智慧管理系统'`；无 `text-overflow: ellipsis` 生效 |

**全页通用判据：** `document.documentElement.scrollWidth <= document.documentElement.clientWidth`（无横向滚动）。
**不纳入判据：** 浏览器标签页的可视截断（浏览器行为，非 CSS 缺陷，见 §3.3）。

---

## 2. 「系统设置」页设计

### 2.1 页面定位与可见角色

- 定位：**只读的「关于本系统」信息页**——回答「我现在跑的是什么系统、什么版本、什么模式、什么环境、数据是不是预置的、我是谁、能看到多少」。**不做任何写操作**。
- 目标用户：**仅 ADMIN**（与同组「运行日志」同口径：运行/环境信息属运维语义，对站长/员工无价值且涉及环境细节）。路由 `meta.roles: ['ADMIN']`，菜单白名单同步。
- 若产品要求站长/员工可见只读：需同步改 `MENU_WHITELIST` + `meta.roles`，并补数据范围收敛（属独立需求，见 §0.3-4）。

### 2.2 信息架构与字段清单（含取值来源）

分区共 4 个卡片 + 页头；**唯一异步块为 S3 的「已预置包裹总数」**（承载四态）。

| 分区 | 字段 | 展示形态 | 取值来源（**全部来自既有事实**） | 拿不到时的处置 |
| --- | --- | --- | --- | --- |
| **S1 系统信息** | 系统名称 | 只读文本（`--fs-body`/`--fw-medium`/`--text-1`） | 前端常量 `快递驿站智慧管理系统`（建议置于 `src/pc` 常量或 `shared/constants`，作唯一真源） | 不适用（常量） |
| | 版本号 | 只读文本 | **需前端注入**：`vite.config.js` 增加 `define: { __APP_VERSION__: JSON.stringify(pkg.version) }`（读 `package.json` 的 `1.0.0`）。运行时**当前无法**直接获取 | 未注入 → **不展示该行**（不要渲染 `undefined`） |
| | 运行模式 | `StatusTag`（soft）：演示态=橙 / 生产态=中性 | `import.meta.env.VITE_MOCK_ENABLED === 'true'`（构建期常量）→ `演示态（Mock 数据）` / `生产态（真实接口）` | 不适用（常量恒有值） |
| | 构建模式 | 只读文本（可选） | `import.meta.env.MODE`（`demo` / `production`） | 不展示 |
| **S2 运行环境** | 浏览器 | 只读文本 | `navigator.userAgent` 解析出「名称 主版本」（如 `Chrome 1xx`） | 解析失败 → `未知浏览器` |
| | 分辨率 | 只读文本 | `window.screen.width × window.screen.height` | 不适用（客户端必得） |
| | 视口 | 只读文本（可选） | `window.innerWidth × window.innerHeight`，监听 `resize` | 不适用 |
| | 时区 | 只读文本 | `Intl.DateTimeFormat().resolvedOptions().timeZone`（如 `Asia/Shanghai`） | 不支持 → 不展示该行 |
| | 语言 | 只读文本（可选） | `navigator.language` | 不展示 |
| | UA 全文 | **不展示**（噪声，可放 `title` tooltip 或省略） | `navigator.userAgent` | 不展示 |
| **S3 数据预置** | 预置规模（声明） | 只读文本 | 演示态：`import.meta.env.VITE_MOCK_PARCEL_COUNT`（= `200000`）；生产态：`不适用` | 未配置 → 不展示 |
| | **已预置包裹总数（实时）** | 数值 + 单位「件」 | **既有接口** `getParcelSummary()`（`src/pc/api/parcel.js` → `GET /parcels/summary` → `total`）。**不新造接口** | 失败 → `StateBlock error` + 重试；`total` 为空/0 → `StateBlock empty` |
| | Mock 装载状态 | `StatusTag` | 同「运行模式」 | 不适用 |
| **S4 账号与权限** | 当前账号 | 只读文本 | `useAuthStore().user.realName`（回退 `username`），`@admin/stores/auth` | 守卫已保证登录态；异常时不展示 |
| | 当前角色 | `StatusTag`（outline） | `ROLE_LABEL[user.role]`（`src/shared/constants/role.js`，既有） | — |
| | 数据范围 | 只读文本 | 派生自角色：`ADMIN`=全域 / `STATION_ADMIN`=本站 / `STAFF`=个人 | — |
| | 权限说明 | 静态说明文本 | 常量文案（**不查接口**）：说明三级角色的数据可见范围 | 不适用 |

页头（`PageHeader`）：`title="系统设置"`，`sub="仅 ADMIN 可见 · 只读信息 · 更新于 HH:mm"`（更新时间 = 页面加载时刻）。

### 2.3 布局与视觉语言

- **布局模式**：单列 + 卡片（与 `views/system/logs.vue` 同范式：`PageHeader` → `el-card shadow="never"` 逐块）。**不引入新的页面骨架**。
- **栅格**：字段区用 `el-descriptions`，列数 = `window.innerWidth >= 1200 ? 2 : 1`（窄窗单列防挤压，与 `demo-ux-improvement.md` A5-3 的抽屉列数策略同源）。
- **间距 / 圆角 / 阴影**：卡间距 `--sp-4`；卡内边距 `--sp-4`；卡描边 `1px --border-line` + `--r-md` + `--e0`（无阴影）；**不新增 Token**。
- **卡片标题**：`<h2>`，`--fs-h3` 15px / `--fw-semibold` 600 / `--text-1`（与 `MiniStats` 标题一致）。
- **视觉语言**：完全继承已冻结的 `demo-ui-redesign.md` 与 `tokens.base.scss` / `tokens.scss`；品牌蓝 `#1890FF` 仅用于图标/线/浅底，承白字实底用 700 档。**本轮不定义新设计方向**（避免另起体系）。
- 色彩分布遵循 60-30-10：中性底/文本为主体，品牌蓝仅用于页头图标与状态标签点缀，橙色仅用于「演示态」提示。

### 2.4 四态（loading / empty / error / normal）

复用 `StateBlock.vue` / `MetricCard.vue`，不自造第四套（`demo-pc-refactor.md` §3.4）。

| 态 | 触发条件 | 表现 | 组件 |
| --- | --- | --- | --- |
| loading | `onMounted` 发起 `getParcelSummary()` 后、响应返回前；或点击重试后 | S3「已预置包裹总数」卡显示骨架条（`MetricCard :loading` 或 `el-skeleton`），**静态分区不闪动** | `MetricCard` |
| normal | 请求成功且 `total` 非空 | 显示数值 + 单位「件」 | `MetricCard` |
| empty | 请求成功但 `total` 为 `0` / `null` | 「未检测到预置数据」+（演示态可提示"请确认 Mock 已装载"） | `StateBlock variant="empty"` |
| error | 请求抛错（Mock 适配器异常 / 生产态后端不可达） | 「数据读取失败」+「重试」（**仅重拉该块**） | `StateBlock variant="error"` |

- **静态分区（S1 / S2 / S4）恒为 normal**：取值范围为构建期常量与 `navigator`/`Store`，无异步语义。**必须在实现注释中显式写明**，避免验收时被误判为「四态没做全」。
- 若产品要求「整页四态」，**不应**把异步数据与静态信息放进同一卡片（否则 loading 骨架会带动静态内容闪动）；正确做法是保持异步块独立（本规范采用）。
- S3 若出现局部失败，其余区块仍正常渲染（局部降级，不整页报错）。

### 2.5 无障碍

| 项 | 要求 | 依据 |
| --- | --- | --- |
| 标题层级 | `h1` = `PageHeader`「系统设置」；每卡标题 `h2`；**不跳级、不出现多 h1** | WCAG 1.3.1 / 2.4.6 |
| 字段语义 | `el-descriptions`（渲染 table，标签列 = 字段名，值列仅放值）或原生 `<dl>/<dt>/<dd>`；标签与值须具备可读关联 | WCAG 1.3.1 |
| `aria-label` | `StatusTag` 提供可读名（如 `运行模式：演示态（Mock 数据）`）；纯装饰图标 `aria-hidden="true"` | 沿用 `layout/index.vue` L36/L56 既有做法 |
| 键盘可达 | Tab 顺序 = DOM 顺序；页面唯一可聚焦元素为 error 态「重试」按钮；**不得**出现 `outline:none` 且无替代焦点环 | WCAG 2.1.1 / 2.4.7 |
| 焦点样式 | 沿用 Element `focus-visible` 主色环（`--color-primary`），不得移除 | 同 `layout` 折叠按钮先例 |
| 点击/触控区 | PC 按钮高 `--el-component-size: 32px`，满足 **WCAG 2.5.8（≥24×24）**；44×44 为移动端要求，PC 沿用既有例外 | `demo-ux-improvement.md` A3-6（已登记例外） |
| 对比度 | 全部文本/标签使用既有 Token（`--text-1/-2/-3`、`StatusTag` 六态）——均已 ≥4.5:1（前序文档已核）；**本页不引入任何新色值** | WCAG 1.4.3 (AA) |
| 侧栏 logo 对比度 | 白字 `#FFF` 对侧栏底 `#1F2937`：底相对亮度 L=0.0215，对比度 = (1.0+0.05)/(0.0215+0.05) ≈ **14.7:1**（WCAG 2.x 相对亮度公式手算，误差 ±0.1） | WCAG 1.4.3 |
| 动效 | 仅沿用 `--dur-fast/base` 与 `--ease-std`；`prefers-reduced-motion` 已全局降级 | `tokens.base.scss` L244-252 |
| 文本换行 | 状态标签内不放长文案（44px 高内会换行截断，参 `demo-ui-redesign.md` §5.7） | — |

### 2.6 不做的项（可编辑项取舍）

- **不提供任何可编辑项**。理由：系统名称/版本/模式/环境均为**构建期常量或运行时事实**，本工程为纯前端 + Mock，**无写入通道**；提供"可编辑但保存无效"的控件会误导用户。
- 若产品要求在页面内改系统名称，**与「演示站」定位冲突**：① 系统名是构建期常量（`pc.html` 静态标题无法运行时改）；② 无持久化后端，改完刷新即复原；③ 与既有「写操作落 localStorage 覆盖层」契约不一致，且会造成三端名称分叉。→ **本轮不做**；若确需，须先定义持久化落点（localStorage key）与三端一致性，属独立需求。
- 「系统名称」**不提供复制按钮**。理由：系统名已常驻侧栏与标题栏，复制价值低；每增一个按钮需满足热区/键盘可达/焦点环，成本 > 收益。若产品坚持，复用既有复制实现模式（`WorkOrderCopyButton`）。
- **不重复**入口页的「重置演示数据」（该功能属 `index.html` 的 `#portal-reset`，本页不承接）。

### 2.7 实现落点索引（文件级，供前端工程师）

| 项 | 文件 | 说明 |
| --- | --- | --- |
| 页面（新建） | `src/pc/views/system/settings.vue` | 单文件 ≤300 行；壳 ≤150 行（`demo-pc-refactor.md` §3.5）；参照 `views/system/logs.vue` 结构 |
| 路由 | `src/pc/router/index.js` | 新增 `{ path: 'system/settings', name: 'SystemSettings', component: () => import('../views/system/settings.vue'), meta: { title: '系统设置', icon: 'Tools', group: 'sys', roles: ['ADMIN'] } }`（子路由，与既有 `system/logs`、一期占位 `system` 并行，**无路径冲突**） |
| 菜单 | `src/pc/config/menu.js` | `MENU_ITEMS` 加 `{ key: 'settings', path: '/system/settings', title: '系统设置', icon: 'Tools', group: 'sys' }`（须连续排列于 `sys` 组内） |
| 菜单白名单 | `src/shared/constants/role.js` | `MENU_WHITELIST.ADMIN` 增加 `'settings'`（真源，与 `logs` 同处）；**避免**同时写进 `EXTRA_MENU_KEYS` 造成双真源 |
| 复用组件 | `src/pc/components/{PageHeader,StateBlock,StatusTag,MetricCard}.vue` | 不新建组件 |
| 数据 | `src/pc/api/parcel.js#getParcelSummary`（既有） | 唯一异步数据源 |
| 状态 | `@admin/stores/auth`（既有） | 账号/角色 |
| 常量 | `src/shared/constants/role.js#ROLE_LABEL`（既有） | 角色标签 |

> 图标说明：`icon: 'Tools'` 为建议值，**须在 `@element-plus/icons-vue` 导出清单核对**（`demo-pc-refactor.md`/`menu.js` 要求图标不重复；`sys` 分组图标已占用 `Setting`，故子项改用 `Tools`）。

---

## 3. 断点与跨浏览器要求

### 3.1 宽度档与预期行为

侧栏折叠阈值 = `(max-width: 1199px)`（`layout/index.vue` L102），故 **≥1200px 才展开且显示 10 字**。

| 视口 | 侧栏 | logo 标题 | 页面内容 | 半截字风险 |
| --- | --- | --- | --- | --- |
| 375px（<768，PC 不承诺） | 折叠 64 | **隐藏** | 单列、卡片满宽；`el-descriptions` 单列 | 无（标题隐藏） |
| 820px（768–992） | 折叠 64 | 隐藏 | 单列 | 无 |
| 1024px（992–1200） | 折叠 64 | 隐藏 | 单列 | 无 |
| 1280px（1200–1440） | **展开 210** | **完整 10 字** | 两列 | 无（余量 30px） |
| 1440px | 展开 210 | 完整 | 达 `--content-max`，两列 | 无 |
| 1600px | 展开 210 | 完整 | 内容居中，两侧留白 | 无 |
| 1920px | 展开 210 | 完整 | 同上 | 无 |

结论：**<1200 时标题隐藏、≥1200 时 10 字占 180px ≤ 210px**，全档位不出现半截字。

### 3.2 内核验证矩阵

| 内核 | 覆盖 | 重点检查项 |
| --- | --- | --- |
| **Chromium**（Chrome/Edge） | 基准内核，唯一有 e2e（`npm run e2e`，Playwright 1.63） | R1-R6 全部脚本判据；折叠/展开在 1200px 边界切换；无横向滚动；`document.title` 拼接 |
| **Firefox** | 未实测（本机无自动化） | CJK 回退字体（Windows 无 PingFang → `Microsoft YaHei`，汉字仍 1em）；`Intl...timeZone` 返回；`el-descriptions` 表格边框与 `focus-visible` |
| **WebKit**（Safari） | 未实测（本机无自动化） | PingFang SC 下标题宽度；`document.title`；时区；`el-descriptions` 渲染。**均标注"静态审查，未实测"** |

### 3.3 属正常现象清单（避免把设计内行为当缺陷）

> 编号仅供参考；来源已逐条标注（任务所述「T7 8 项」原文未定位到，见 §0.3-1）。

**设计内行为（非缺陷）：**

1. 视口 <1200 侧栏自动折叠为纯图标、logo 标题消失 → `layout/index.vue` 的 `NARROW_QUERY` 设计内。
2. 浏览器标签页标题被浏览器截断显示省略号 → 浏览器行为，非 CSS 缺陷。
3. 1600/1920 下页面内容两侧留白 → `--content-max: 1440px` 设计内（`demo-pc-refactor.md` §8）。
4. 路由切换顶部 2px 主色进度条一次走完、与真实加载无关 → 设计内轻反馈（`demo-ux-improvement.md` A3-7）。
5. 生产构建（`--mode production`，`VITE_MOCK_ENABLED=false`）下「预置规模」显示「不适用」、运行模式显示「生产态」 → 设计内。
6. 时区显示 IANA 名（`Asia/Shanghai`）而非 `UTC+8` → 设计内。
7. Element dropdown/popper 展开时遮挡下方内容 → 正常遮罩行为（`SESSION-STATE.md` L141 已证伪）。
8. 侧栏 logo 图标用 500 档蓝、承白字实底用 700 档 → 项目硬规则（`tokens.base.scss` L84-90 注释）。

**已归档的「证伪/误报」记录（勿照单改）：**

| 走查/评审结论 | 复核结论 | 来源 |
| --- | --- | --- |
| 「我的」缺「我的工资单」入口 | ❌ 证伪（`MeSection.vue:56` 本就有） | `SESSION-STATE.md` L263 |
| 宫格「加载中」态不可达 | ❌ 证伪（运行时采样 `···` 可见） | `SESSION-STATE.md` L264 |
| `Cannot read ...'realName' @ preload-helper-*.js` | ❌ 证伪（旧产物残留；该行是 `@license` 注释） | `SESSION-STATE.md` L267 |
| 排班页头「计数被浮层遮挡」 | ❌ 误报（Element dropdown 正常遮挡，`position:absolute` 命中 0 处） | `SESSION-STATE.md` L141 |
| `--color-primary-strong` 与 `--color-primary-hover` 是同分叉 | ❌ 证伪（两个语义相异的 Token） | `SESSION-STATE.md` L142 |
| `--c-neutral-400` 与 `--c-neutral-450` 应合并 | ❌ 证伪（占位符/控件边界专用，必须并存） | `SESSION-STATE.md` L143 |

> 纪律：走查/评审是信号不是判决（`demo-milestones.md` D-10/D-16）；本页任何走查结论须**逐条复核后再改**。

---

## 4. 交付前自检清单

- [ ] R1 侧栏 logo 文案 = `快递驿站智慧管理系统`；已补 `flex-shrink: 0`；`title.scrollWidth <= title.clientWidth`（≥1200px）
- [ ] R2 折叠态标题 `display:none`，`aside.offsetWidth === 64`
- [ ] R3 `router.afterEach` 拼接串已改；`/dashboard` 断言 `document.title` 通过
- [ ] R4 `pc.html` 静态 `<title>` 已改
- [ ] R5 登录页标题：已按主智能体裁决方案执行或登记偏差（**不得擅改 hrm-admin**）
- [ ] R6 设置页「系统名称」字段全等 10 字
- [ ] 设置页四态：S3 异步块 loading/empty/error/normal 全覆盖；静态分区已注释说明无四态语义
- [ ] 无障碍：h1/h2 层级正确、StatusTag 有 `aria-label`、键盘可达、无 `outline:none`、对比度用既有 Token
- [ ] 未引入新色值 / 新 Token / 新依赖；文件 ≤300 行、`index.vue` ≤150 行
- [ ] 未改 `hrm-admin/**`、`hrm-server/**`、`src/mobile/**`、`scripts/**`
- [ ] 门禁：`verify:mock` / `test` / `lint` / `lint:style` / `build` 全绿，`e2e` 37 通过 / 0 失败
- [ ] 全页无横向滚动：`documentElement.scrollWidth <= clientWidth`

---

## 附录 A：不属本轮范围但仍含旧名的位置（勿越界修改）

| 位置 | 现状 | 处置 |
| --- | --- | --- |
| `mobile.html:16` | `移动端 · 快递驿站智汇系统` | 范围外（移动端） |
| `src/mobile/views/login/index.vue:64` | `快递驿站智汇系统` | 范围外 |
| `index.html:11` / `src/portal/main.js:7` / `.env.demo:3` | 入口页标题（`VITE_DEMO_TITLE`） | 范围外（端选择页） |
| `package.json:5` / `README.md:3` / `eslint.config.js:8` / `stylelint.config.cjs:2` / `启动预览.ps1` | 元数据/注释/脚本 | 非用户可见；如需统一另开一轮 |

> 若产品要求「全站统一改名」，须单独一轮并同步移动端/入口页；本轮**仅网页端**，不得越界。

---

## 附录 B：依据来源

- `hrm-dev/docs/demo-pc-refactor.md`（目录约定、组件契约、四态、行为等价红线、响应式断点基线）
- `hrm-dev/docs/demo-ui-redesign.md`（Design Tokens、字体层级 §2.4、组件规范 §4、PC 框架 §5.1、动效 §8、对比度 §9.1）
- `hrm-dev/docs/demo-ux-improvement.md`（A3 侧栏、A3-6 触控例外、A3-7 进度条、A5-3 抽屉列数）
- `SESSION-STATE.md`（L141-143 实测澄清、L262-267 M7 证伪、环境约束）
- `hrm-dev/hrm-demo/README.md`（L429-438 走查复核表）
- `hrm-dev/docs/demo-milestones.md`（D-10 / D-16「走查是信号不是判决」）
- 源文件：`src/pc/layout/index.vue`、`src/pc/router/index.js`、`src/pc/config/menu.js`、`src/shared/constants/role.js`、`src/pc/styles/tokens.scss`、`src/shared/styles/tokens.base.scss`、`src/pc/components/{PageHeader,StateBlock,StatusTag,MetricCard,MiniStats}.vue`、`pc.html`、`.env.demo`、`.env.production`、`hrm-admin/src/views/login/index.vue`
- 无障碍标准：WCAG 2.1（1.3.1 / 1.4.3 AA / 2.1.1 / 2.4.6 / 2.4.7 / 2.5.8）、W3C CSS 全角宽度定义
