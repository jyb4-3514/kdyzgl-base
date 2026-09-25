# 快递驿站智汇系统 · 三端独立代码拆分方案

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0（设计稿，待评审） |
| 编写日期 | 2026-09-24 |
| 作者 | 架构师 `express-station-architect` |
| 状态 | **设计稿待评审**；涉外部暴露面/现网变更的条目须先经网络安全工程师评估（调度规则 P0.5 / L7） |
| 范围 | ① 拆分目标与判定标准 ② 仓库/工程粒度选型 ③ 共享层策略 ④ 各端配置与构建 ⑤ 分阶段迁移路线 ⑥ 硬约束与风险 ⑦ 待用户裁定项 ⑧ 回填指针 |
| 关联文档 | [multi-client-architecture.md](multi-client-architecture.md)（API 角色驱动/服务器结论）、[project-tree.md](project-tree.md)（三端定位与 `TODO(扩展)`）、[demo-design.md](demo-design.md)（可替换数据源约束）、[demo-login-redesign.md](demo-login-redesign.md)（三端品牌分流）、[security-auth-review.md](security-auth-review.md)（HTTPS 强制/`.map` 禁公开）、[api.md](api.md) |
| 本轮边界 | **只做方案设计，不实施迁移**：不移动/删除/新建任何前端源码文件；不改 `hrm-demo`/`hrm-admin`/`hrm-server` 源码；不改 `api.md`/`db.md`/`plan.md`/`server-architecture.md`/`algo-hrm-server.md`/`multi-client-architecture.md`/`security-auth-review.md`/`demo-login-redesign.md`/`project-tree.md`/`SESSION-STATE.md`/迁移脚本/`.trae/rules/` |

> **安全声明（P0.5）：** 本文涉及**发布路径、Nginx location、外部暴露面、安卓壳加载地址**的条目均为「设计方案输入」而非安全结论。落地前须由**网络安全工程师**按 L7 出具技术评估，主智能体方可授权执行（清单见 [§6.3](#63-须经网络安全工程师评估清单p05)）。
>
> **凭据声明：** 本文不记录任何真实凭据、域名/IP、AppKey；示例一律 `change_me_*` 或 `example.invalid` 占位。

---

## 0. 事实基线（本轮实测，方案所有数字的来源）

> 实测时间 2026-09-24；方式：`Get-ChildItem -Recurse -File` 计数 + 源码逐文件阅读 + `git status`。**不确定项已标「需实测确认」**。

### 0.1 代码规模（`hrm-dev/hrm-demo/src`）

| 目录 | 文件总数 | 其中 `*.spec.js` | 非测试源码 |
| ---- | ---- | ---- | ---- |
| `src/mobile/` | 172 | 29 | 143 |
| `src/pc/` | 163 | 15 | **148** |
| `src/shared/` | 62 | 9 | 53 |
| `src/demo/` | 2 | 0 | 2 |
| `src/portal/` | 1 | 0 | 1 |
| **合计** | **400** | 53 | 347 |

**git 现状：** 分支 `feature/前端演示项目拆分与精细化`；工作区未提交 4 项；暂存区 0 项；`hrm-dev/hrm-demo` 下受版本控制文件 438 个。

### 0.2 共享层实际清单（任务列举之外的补充）

| 路径 | 内容 | 任务是否列举 |
| ---- | ---- | ---- |
| `src/shared/mock/` | `routes/`（17 个路由模块 + `index.js`，共 145 条 API）、`{attendance,clientLog,finance,hr,kpi,leave,parcel,syncConfig}Store.js`、`db.js`、`engine.js`、`install.js`、`overlay.js`、`persist.js`、`sessionStore.js`、`syncConfigCsv.js`、`util.js`、`validate.js` | 是 |
| `src/shared/domain/` | `pagination`、`applyDataScope`、`csv`、`mask`、`permission`、`sla`、`text`、`time`、`workOrderText`、`index`（+各 `.spec.js`） | 部分（漏 `csv/mask/permission/sla/text/time/workOrderText`） |
| `src/shared/constants/` | `dict`、`errorCode`、`role`、`storageKey` | 是 |
| `src/shared/device.js` | 设备弱信号采集（三端登录页共用） | 是 |
| `src/shared/clientLog.js` | 前端运行日志采集器（PC/H5/壳三端上报，白名单脱敏 + 环形缓冲） | **否（新增事实）** |
| `src/shared/composables/useNow.js` | 时间刷新 composable | **否（新增事实）** |
| `src/shared/styles/tokens.base.scss` | 设计系统 Token 基座 | 是 |

### 0.3 请求层与登录态（与任务描述有出入，见 [§0.5](#05-事实性纠正)）

| 端 | 请求封装 | baseURL 来源 | 登录态存储键 |
| ---- | ---- | ---- | ---- |
| 网页端（demo `src/pc`） | **复用** `@admin/utils/request`（`hrm-admin/src/utils/request.js`，**只读**）；`pc/main.js` 用实例属性覆盖 baseURL | `import.meta.env.VITE_API_BASE \|\| '/api/v1'` | `@admin/stores/auth` 的 `TOKEN_KEY`/`USER_KEY` |
| 移动端（demo `src/mobile`） | 自有 `src/mobile/utils/http.js`（axios 实例 + Vant Toast + `hrm:mobile-unauthorized` 事件广播，不 import router 以避循环引用） | 同上 | `hrm_demo_mobile_token` / `hrm_demo_mobile_user`（真源 `src/shared/constants/storageKey.js`） |
| 设备标识 | `src/shared/device.js` | — | `hrm_demo_device_id` |

### 0.4 构建与质量链（实测）

- **入口：** MPA 三入口 `index.html`（端选择，纯 DOM 不引框架）/ `pc.html`（history 路由）/ `mobile.html`（hash 路由）；`vite.config.js` 生产构建**只出 `pc`/`mobile`**，`index` 不进生产包。
- **脚本（`package.json`）：** `dev`(`--mode demo`)、`build`(`--mode demo`)、`build:prod`(`--mode production`)、`preview`、`serve:dist`、`verify:mock`、`verify:mobile`、`verify:tokens`、`format(:check)`、`lint(:fix)`、`lint:style(:fix)`、`test`、`test:watch`、`test:coverage`、`e2e`、`e2e:report`、`prepare`(husky)。
- **环境：** `.env.demo` = `VITE_DEMO_TITLE` + `VITE_MOCK_ENABLED=true` + `VITE_MOCK_PARCEL_COUNT=200000`；`.env.production` = `VITE_MOCK_ENABLED=false` + `VITE_API_BASE=/api/v1`。
- **vite 关键项（删改即事故）：**
  - `resolve.dedupe: ['vue','vue-router','pinia','axios','element-plus','@element-plus/icons-vue','vant']` —— 历史白屏根因（`@admin` 别名导致依赖双副本 → `activePinia` 分裂），**必须保留**（`vite.config.js` L88-100）。
  - 插件 `hrm-demo-mock-guard`：`--mode demo` 下 `VITE_MOCK_ENABLED !== 'true'` 直接抛错（**fail-safe 快速失败**）。
  - 插件 `hrm-demo-history-fallback`：dev 下把「非文件、非 Vite 内部模块」的 GET 路径改写到 `/pc.html`（否则 dev 刷新 PC 深链掉回端选择页）。
  - `define.__APP_VERSION__` 取自 `package.json.version`。
  - `build.sourcemap = mode === 'production' ? 'hidden' : false`（`.map` 生成但不在产物中引用，Nginx 侧禁公开下载）。
  - `server.fs.allow: ['..']`（放行 `hrm-admin` 目录，`@admin` 别名读取所需；根目录无 workspaces 时 Vite 探测不到）。
  - `build.rollupOptions.input`：演示态三入口 / 生产态两入口。
- **架构约束已机器化（`eslint.config.js`，规则 1–13）：** 展示层禁直连 `shared/mock`、禁直连 `api/`、禁直接 `useStore`；`shared/domain` 纯工具层禁反向依赖 mock；`portal`/`demo` 禁反向依赖 `mobile`/`pc`；`modules/boss` 为**叶子域**（禁被员工端反向依赖、禁依赖 `@admin` 与 `src/pc`）；PC 端禁依赖 `modules/boss`。
- **跨域复用先例（拆分必须处理）：**
  - 中立共享页：`src/mobile/views/message/MessagePage.vue`、`NoticeReader.vue` 位于域目录**之外**，`/boss/message` 与 `/staff/message` 共用。
  - 跨域直引（全仓唯一一处）：[router/index.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L41-L46) 的 `/boss/kpi/:employeeId` → `views/staff/kpi.vue`，已带 `TODO(扩展)` 登记。
  - 逐行等价重复：`boss/me.vue` 与 `staff/me.vue` 均仅渲染 `MeSection`（见 `demo-boss-module-plan.md` §P-06）。
- **工程现状补充：** 根仓库**无 lockfile/workspaces**（lockfile 仅在 `hrm-admin` 与 `hrm-demo` 内各一份）。
- **安卓壳（`hrm-android-shell`）：** `MainActivity` 用 `webView.loadUrl(BuildConfig.H5_URL)` **加载远程地址**（debug `http://10.0.2.2:5188/mobile.html`；release 占位 `https://example.invalid/mobile.html`，带 `TODO(扩展)` 构建参数注入）；`app/src/main/assets/h5/*` 被 `.gitignore` 忽略，仅 `.gitkeep` 占位（**不是离线包落地**）。桥接名 `HrmBridge`（H5→原生）与 `HrmShell`（原生→H5），见 `mobile/utils/bridge.js`。**全部未编译验证**。

### 0.5 事实性纠正

1. **「148 个前端文件」** —— 实测 `hrm-demo/src` 共 **400** 文件（`mobile` 172 / `pc` 163 / `shared` 62 / `demo` 2 / `portal` 1）。其中 **`src/pc` 非 spec 源码恰为 148**。若该数字指「网页端源码文件数」则吻合；若指「全量待迁移前端文件」则与实际不符，**本方案一律以 0.1 的实测计数为准**。
2. **生产 API 基址存在冲突（重要）** —— `.env.production` 的 `VITE_API_BASE=/api/v1`，而现网 `kongzhen1.com` 的 `/api/` 由 `courier-nginx` 反代到**现网 `courier-app`（快递系统）**，**不是 `hrm-server`**。`hrm-server` 按任务背景经 `/hrm-api/` 前缀暴露。故 **`build:prod` 产物现状不能直接对现网使用**；三端接 `hrm-server` 时基址须改为 `/hrm-api/...`（确切路径**需实测确认**）。同时 `multi-client-architecture.md` §3.2.3 规划的 `/api/ → 127.0.0.1:8081` 与现状 `/hrm-api/` 不一致，以现状为准。
3. **请求封装并非「PC/移动各一套」** —— 网页端**没有**自己的 `utils/request`，而是**只读复用** `@admin/utils/request`；只有移动端有 `mobile/utils/http.js`。故「各端一套封装」是拆分后**待建目标**，不是现状。
4. **安卓壳不是离线 `assets/h5/` 包** —— 现状是 `BuildConfig.H5_URL` **远程 https 加载**；`assets/h5/*` 为 gitignore 的占位目录。任务描述「`assets/h5/` 注入」与现状不符。
5. **`?as` 取值** —— 端选择页使用 `mobile.html#/login?as=boss`（管理端）与 `?as=station`（员工端）；`mobile/constants/appName.js` 的取值集合为 `{'staff','station'}`（员工端）与 `'boss'`（管理端）。任务所述「按 `?as` 分流」正确，取值补充如上。

---

## 1. 拆分目标与判定标准

### 1.1 目标（一句话）

在「**三端共用一个后端服务器**」与「**Demo 可整体剥离**」两条硬约束下，把 `hrm-demo` 单工程三入口演进为 **网页端 / 员工端「驿站助手」/ 管理端「驿站精灵」三个可独立构建、独立发布的工程**，共享层以 **workspace 包复用以消除复制**，迁移全程线上（`https://kongzhen1.com/`）可用。

### 1.2 「拆分完成」的可判定标准（DoD）

| # | 判定项 | 可执行判定方式 |
| - | ---- | ---- |
| C1 | **独立构建** | 三端各自 `npm ci && npm run build`（演示态）与 `npm run build:prod`（生产态）均在**本机 Node** 通过 |
| C2 | **独立发布** | 任一端重新构建并发布，**不需要**重建/重发另外两端；三端 `outDir` 与站点根互不重叠 |
| C3 | **互不影响** | 三端任一端的依赖升级/构建失败，不阻断其余两端的构建与发布（workspace 下各自的 `build` 脚本独立可跑） |
| C4 | **共用后端** | 三端请求路径仍为 `/api/v1/*`（**不按端分区**，沿用 `multi-client-architecture.md` ADR-MC-02）；端差异仅经可选 `X-Client-Type` 头承载 |
| C5 | **共享层不复制** | 全仓静态检索：`errorCode`/`role`/`dict`/`device`/`clientLog`/契约类型在**三端各仅 1 处消费来源**（workspace 包） |
| C6 | **契约单一真源** | Mock（145 路由）与真实后端行为规格同源，三端消费同一份 mock 包；Mock 与后端分叉须走契约变更流程 |
| C7 | **迁移期零中断** | 迁移全程 `https://kongzhen1.com/` 三入口可达；每次 Nginx 变更前有备份与回滚步骤 |
| C8 | **受保护工程零改动** | `git diff -- hrm-dev/hrm-admin`、`git diff -- hrm-dev/hrm-server` 均为空 |
| C9 | **门禁不弱化** | `verify:mock` 断言数**不得低于**迁移前基线（现状 919/919）；Playwright 用例断言只允许平移或增强（反模式 A06） |
| C10 | **Demo 可整体剥离** | 演示能力以**构建模式**（`VITE_MOCK_ENABLED`）承载；任一 `apps/*` 可单独删除而不破坏其余两端构建 |

---

## 2. 拆分粒度与仓库/工程结构（含取舍）

### 2.1 候选方案

#### 方案 A（推荐）：Workspace 单仓库多工程

```text
hrm-dev/
├── hrm-clients/                       # 新增：三端 workspace 根（含唯一 lockfile）
│   ├── package.json                   # private:true, workspaces:["packages/*","apps/*"]
│   ├── package-lock.json              # 单一 lockfile（版本一致性保障）
│   ├── packages/
│   │   ├── shared/                    # @kdyzgl/shared   零 UI 依赖（constants/domain/device/clientLog/composables/styles）
│   │   ├── api-client/                # @kdyzgl/api-client  peerDependencies:{axios}（http 工厂 + 错误码 + 契约类型）
│   │   └── mock/                      # @kdyzgl/mock     （145 路由 + 引擎 + 数据层；仅演示态动态引入）
│   └── apps/
│       ├── web/                       # 网页端        （history 路由 + Element Plus + @admin 只读别名）
│       ├── staff-h5/                  # 员工端「驿站助手」（hash 路由 + Vant）
│       └── boss-h5/                   # 管理端「驿站精灵」（hash 路由 + Vant）
└── hrm-demo/                          # 冻结保留：迁移期线上演示站 + 回滚锚点（不出新功能）
```

#### 方案 B：完全独立仓库（三份代码仓 + 共享包发 npm 私服）

```text
（三个独立 git 仓库）                 （一个私服包）
hrm-web.git        ─┐
hrm-staff-h5.git   ─┼─→ @kdyzgl/shared / api-client / mock（npm 私服，语义化版本）
hrm-boss-h5.git    ─┘
```

#### 方案 C（对照项）：不拆工程，仅逻辑分层

保持 `hrm-demo` 单工程三入口，只把内部目录按 `shared/apps` 规整，**不新增工程边界**。

### 2.2 量化对比

| 维度 | A Workspace 单仓多工程 | B 完全独立仓库 | C 不拆工程 |
| ---- | ---- | ---- | ---- |
| **迁移成本**（须搬动的源码量） | 中：一次性建 workspace + 逐端「复制-改引用-验证」，每端约 143（staff）/148（web）非测试文件量级；改动以 import 路径为主 | 高：需先建私服、发包、三仓各自 CI、跨仓版本发布流程；迁移量同 A 且叠加基建 | 低：几乎不动文件 |
| **共享层复用方式** | `workspaces` + `file:` 协议，本地即时生效、无需发包 | npm 私服 + 语义化版本，需发包即可用 | 单一 `src/shared/`（**当前即如此**） |
| **构建/发布复杂度** | 中：三套 `vite.config` + 各自 `dist`；一条命令 `npm run build -w apps/web` | 高：三仓各自流水线 + 私服发布流水线 | 低：一套构建（**但无法独立发布**） |
| **版本一致性保障** | **强**：单一 `package-lock.json`，`vue`/`pinia`/`axios` 与 Element Plus/Vant **物理同源**（直接消解 `resolve.dedupe` 类白屏风险） | 中：各仓独立 lockfile，需靠共享包版本约束，易漂移 | 强（现状即单 lockfile） |
| **CI 成本** | 中：一套 CI，按 `apps/*` 路径过滤触发 | 高：三套 CI + 私服 | 低 |
| **团队协作成本** | **低**：1 主 + 9 角色小体量，单仓便于跨端原子改动与统一 Review | 高：跨仓改动需多 PR、多流水线协调 | 低 |
| **与「Demo 可整体剥离」关系** | **好**：Demo 能力下沉为构建模式（`VITE_MOCK_ENABLED` + `@kdyzgl/mock`）；`hrm-demo` 冻结保留即可整体删除 | 差：演示能力散落三仓，剥离需三处处理 | 最好（现状即单工程） |
| **与「三端独立代码」关系** | **满足**：三端各自独立 `package.json`/`vite.config`/`dist` | 满足 | **不满足**（仍是单工程三入口） |
| **落地风险** | 中 | 高（基建先行） | 低（但目标不达成） |
| **是否推荐** | **推荐** | 备选（团队/仓库规模扩大后再考虑） | 否决（不满足目标） |

### 2.3 推荐结论

**采纳方案 A（Workspace 单仓库多工程）。** 核心理由：

1. **单一 lockfile = 版本一致性内生保障**：现状 `resolve.dedupe` 是为解决「`@admin` 自带 `node_modules` 导致 `vue`/`pinia` 双副本 → 白屏」而写的补丁；workspace 把三端与共享包的依赖收敛到**同一棵依赖树**，从根上降低该类事故概率（`dedupe` 仍保留作为纵深防御）。
2. **迁移成本可控**：无 npm 私服基建，`file:` 协议即时生效；改动以「复制 + 改 import 路径」为主，风险集中在 import 重写而非流程。
3. **团队规模贴合**：1 主 + 9 角色、无独立 DevOps 编制，跨端原子改动集中在一个仓库更易 Review 与回滚（对齐 `system-architecture` 「不过度设计」反模式）。
4. **不违背任一硬约束**：不触碰 `hrm-admin`/`hrm-server`（共享包为**中立新增**，禁反向依赖二者）；`hrm-demo` 冻结保留，可整体删除。

### 2.4 迁移期间线上可用性评估（必评项）

**结论：方案 A 下，迁移期线上零中断。** 机制：

| 机制 | 说明 |
| ---- | ---- |
| 冻结源 | 迁移期内 `hrm-demo` **不再新增功能**，仅接受 P0 缺陷修复；新需求一律进 `apps/*` |
| 双轨并行 | 新端先发布到**新路径**（`/web/`、`/staff/`、`/boss/`），现网 `location = /` 与 `location /` **继续指向 `hrm-demo-static`**，演示站行为完全不变 |
| 复制式迁移 | 从 `hrm-demo` **复制**代码进新工程（不移动、不删除源），源工程始终完整可构建、可发布 |
| 切换点唯一 | 只有 S6（发布切换）会触碰现网 Nginx；**改现网 Nginx 属 C 档**，须安全评估结论 + 主智能体三步授权 + 变更前备份 |
| 回滚锚点 | 现网 demo 镜像 tag `20260924-3`；Nginx 变更前备份 `nginx.conf.<时间戳>`，回滚 = 还原 + `nginx -t` + `reload` |
| 灰度 | 三端逐个切：先 `/web/` 验收 → `/staff/` → `/boss/`；每端切换后保留原 demo 入口至少一个发布周期 |

```text
迁移期拓扑（示意，域名/IP 以现网运维记录为准，不落本文）

[courier-nginx]
 ├─ location = /         → hrm-demo-static（演示站，不动）
 ├─ location /           → hrm-demo-static（演示站，不动）
 ├─ location /admin/     → 现网管理端（不动，禁改）
 ├─ location /api/       → courier-app（现网快递系统，不动，禁改）
 ├─ location /hrm-api/   → hrm-server（任务背景：现已暴露）
 ├─ location /web/       → 【新增】apps/web  dist        ← S6 接入
 ├─ location /staff/     → 【新增】apps/staff-h5 dist    ← S6 接入
 └─ location /boss/      → 【新增】apps/boss-h5 dist     ← S6 接入
```

> 最终态（S7 后）：`location = /` 与 `location /` 是否切到 `apps/web`（并退役演示站），**属待用户裁定项 D1/D4**。

---

## 3. 共享层策略（关键）

### 3.1 逐项判定表

> 判定原则：**纯逻辑、零 UI 依赖、三端同口径 → 共享；与端路由/角色/视觉载体绑定 → 各端自带。** 共享落点统一在 `hrm-clients/packages/*`。

| # | 资产 | 判定 | 落点 | 理由 |
| - | ---- | ---- | ---- | ---- |
| 1 | `shared/mock/**`（145 路由 + 引擎 + 8 领域 Store + db/overlay/persist/session） | **共享（独立包）** | `packages/mock`（`@kdyzgl/mock`） | Mock 是**唯一行为规格**；按端复制将产生 3 份漂移，直接摧毁契约单一真源（P5） |
| 2 | `shared/domain/**`（`pagination`/`applyDataScope`/`csv`/`mask`/`permission`/`sla`/`text`/`time`/`workOrderText`/`index`） | **共享** | `packages/shared`（`@kdyzgl/shared/domain`） | 纯函数、零 UI 依赖，三端同口径（分页/数据范围/脱敏/权限判定必须单源） |
| 3 | `shared/constants/{dict,errorCode,role}` | **共享** | `packages/shared/constants` | 与后端 `ErrorCode.java`/`RoleEnum.java`/字典同源；多份必漂移 |
| 4 | `shared/constants/storageKey.js` | **共享（需去 demo 化重构）** | `packages/shared/constants` | 键名是**三端共同存储契约**；但现值 `hrm_demo_*` 属演示命名，生产需按端命名空间化（见 §3.3） |
| 5 | `shared/device.js` | **共享** | `packages/shared` | 三端登录页共用同一设备弱信号采集（`security-auth-review.md` §4.2 定位：可伪造、仅降低误判） |
| 6 | `shared/clientLog.js` | **共享** | `packages/shared` | 三端上报口径（白名单脱敏/环形缓冲/限流）必须单点，否则端间长出第二份脱敏实现 |
| 7 | `shared/composables/useNow.js` | **共享** | `packages/shared` | 纯逻辑 composable |
| 8 | `shared/styles/tokens.base.scss` | **共享** | `packages/shared/styles` | 设计系统基座，三端同一 Token 语汇（P3「同源不同语气」） |
| 9 | `mobile/components/**`（40+ 组件） | **按类型二分**（见 §3.2） | 通用 → `packages/shared/ui`（`TODO(扩展)` 待 UI 评审）；端专属 → 各端 | 通用展示组件（`PageState`/`StatusTag`/`SlaTag`/`Badge`/`Chip`/`ListItemCard`/`MonthPicker`/`PageNav`/`KpiGauge` 等）三端可复用；端专属（`TodoGroup`/`HomeQuickGrid`/`IdentitySwitcher`/`DemoIdentityGroup`）留在端 |
| 10 | `mobile/stores/**` | **各端自带**（`auth` 三端各自一份） | `apps/*/stores` | Pinia store 与端路由/角色/端准入绑定；`auth` 的 `homePath`/角色分流/`?as` 语义三端不同（`mobile/stores/auth.js` 现按角色分流，拆分后各端固定） |
| 11 | `mobile/api/**` | **各端自带（薄壳）** | `apps/*/api` | 仅声明端点与入参；**各端可见范围不同**（`multi-client-architecture.md` §2.4 端-接口矩阵），按端裁剪即天然各端一份 |
| 12 | 请求封装（web 的 `@admin/utils/request` 与 mobile 的 `utils/http`） | **共享工厂 + 各端注入差异** | `packages/api-client`（`createHttp`） | **本次拆分核心收益点**：`baseURL`/拦截器/`body.code` 分发/错误码映射单源，各端仅注入 `baseURL`/`clientType`/`getToken`/`onUnauthorized`/`notifyError` |
| 13 | 登录态存储键 | **各端自带（同一命名规范）** | 各端 `src/utils/authStorage.js` + `@kdyzgl/shared/constants/storageKey` 工厂 | 三端须**同浏览器可并存**（沿用 `demo-design.md` §6.2 的 S13 双标签登录需求）；键名按 `hrm:{client}:token` 规范由共享工厂生成 |

### 3.2 `mobile/components/**` 二分（建议，待 UI/UX 评审冻结）

| 类别 | 组件（举例，来自实测目录） | 去向 |
| ---- | ---- | ---- |
| **通用展示（候选共享）** | `PageState`、`StatusTag`、`SlaTag`、`Badge`、`Chip`、`MiniChip`、`ListItemCard`、`MonthPicker`、`PageNav`、`ActionBar`、`KpiGauge`、`KpiIndicatorCard`、`StatCard`、`LineChart`、`PayrollStatusSteps`、`MyPayrollCard` | `packages/shared/ui`（**零业务依赖、零 store 依赖**为前提） |
| **业务通用（谨慎共享）** | `LeaveApprovalList`、`LeaveAudit`、`NoticeList`、`AttendanceStatusBar`、`FilterChips` | 先各端自带；若两处以上逐行等价再提升（复用原则：出现三次以上必须抽取） |
| **端专属（各端自带）** | `TodoGroup`、`TodoList`、`HomeQuickGrid`、`QuickGridItem`、`IdentitySwitcher`、`DemoIdentityGroup`、`AccountSecurityGroup`、`LogoutAction`、`ProfileHero`、`StationPicker`、`WorkOrderCopyButton` | 员工端 / 管理端各自持有 |

> **组件共享的硬前提（对齐既有 ESLint 边界）：** 入 `packages/shared/ui` 的组件**不得** import `stores/`、`api/`、`shared/mock`，数据一律 props 注入（现 `mobile/components/**` 尚有 5 处存量越界，见 `eslint.config.js` 规则「L1+L2（warn）」注释，须先消越界再提升）。

> `TODO(扩展): 通用组件提升为共享 UI 包的范围与命名，须由 UI/UX 设计师出「组件清单 + Tokens 归属」后冻结（P2 设计先行），本方案只给定性判据。`

### 3.3 API 客户端层设计（核心收益点，不复制）

**目标：三端共享「请求封装 + 错误码映射 + 契约类型」，但不复制、且不反向依赖 `hrm-admin`/`hrm-demo`。**

```text
packages/api-client/
├── src/
│   ├── http/
│   │   ├── createHttp.js      # axios 实例工厂：统一 baseURL、Bearer 注入、超时按方法（读 15s / 写 30s）
│   │   ├── response.js        # 统一 body.code 分发：code===200 → data；否则构造 Error{code,data,message}
│   │   ├── retry.js           # 仅「GET + 无 response 的网络错误」重试一次（退避 500ms），写操作不重试
│   │   └── unauthorized.js    # 401 / 1108 幂等广播（同批并发只触发一次）
│   ├── error-codes.js         # 与 shared/constants/errorCode.js 同源（单一真源，禁止第二份码表）
│   ├── contracts/             # 按域分文件的请求/响应契约（JSDoc typedef，见下「类型策略」）
│   └── index.js
└── package.json               # peerDependencies: { "axios": "^1.7.9" }；无 vue/element-plus/vant 依赖
```

**工厂签名（契约级，非实现代码）：**

```text
createHttp({
  baseURL,            // web: '/hrm-api/v1'（待实测确认）| staff/boss: 同源
  clientType,         // 'web' | 'staff' | 'boss'（作为 X-Client-Type 头，审计维度）
  getToken,           // 各端登录态读取（web: @admin auth key / h5: 自有 key）
  onUnauthorized,     // web: ElMessage + 路由跳转；h5: Vant Toast + 事件广播（不 import router）
  notifyError,        // 错误提示载体：web → ElMessage；h5 → showFailToast
  readTimeout, writeTimeout, retryDelay
}) → axiosInstance
```

**各端注入差异（保留现状语义，仅换载体）：**

| 注入项 | 网页端（apps/web） | 员工端 / 管理端（apps/*-h5） |
| ---- | ---- | ---- |
| `notifyError` | Element Plus `ElMessage` | Vant `showFailToast`（`silent` 时由调用方渲染） |
| `onUnauthorized` | 清 `@admin/stores/auth` + `router.replace('/login')`（含 `1108` 到期带 `redirect`/`expired`） | 清自有 storage + **广播自定义事件**（`hrm:mobile-unauthorized`），由 `App.vue` 统一清理与跳转（**保持不 import router 以避循环引用**） |
| `clientType` | `web` | `staff` / `boss` |

**类型策略（受现状约束）：** `hrm-demo` **无 TypeScript**（`package.json` 无 `typescript`；`vite.config.js` 注释明确「本工程无 TypeScript」）。故 `contracts/` **采用 JSDoc `@typedef` + 可选 `checkJs`**，**不引入 TS 构建链**，避免为拆分新增编译期基建。

> `TODO(扩展): 待后端引入 springdoc-openapi 后，contracts/ 可由 OpenAPI 自动生成以消除手写漂移（与 multi-client-architecture.md §2.2 TODO 同源）；届时以 api.md 为人工校验基线。`
>
> `TODO(扩展): 员工端/管理端登录态键名去 demo 化（现 hrm_demo_*），按 hrm:{client}:token 规范统一生成；迁移期需双键兼容读取，避免已登录用户被登出。`

---

## 4. 各端配置与构建

### 4.1 共性纪律（三端**必须**逐条保留）

| 纪律 | 要求 | 依据 |
| ---- | ---- | ---- |
| 演示态/生产态两条构建 | `build` = `--mode demo`（**含 Mock**）；`build:prod` = `--mode production`（**剥离 Mock**，Mock 走动态 `import()` 由 Rollup 整块剔除） | `demo-design.md` 核心约束「数据源可替换但不必须存在」 |
| Mock 装配点唯一 | `installMock(instance)` 仅在 `apps/*/src/main.js` 调用；展示层禁直连 `@kdyzgl/mock` | 既有 ESLint 规则 1 |
| fail-safe 语义 | 仅 `VITE_MOCK_ENABLED === 'true'` 才装 Mock；未配置/拼错一律视为关闭（向生产倾斜）；`--mode demo` 下未开 Mock 直接抛错 | `vite.config.js` `hrm-demo-mock-guard` + `main.js` |
| `resolve.dedupe` **必须保留** | `['vue','vue-router','pinia','axios','element-plus','@element-plus/icons-vue','vant']` | 历史白屏事故根因（`activePinia` 分裂） |
| sourcemap | 生产 `hidden`（生成 `.map`，不写 `sourceMappingURL`）；Nginx 侧**禁公开下载 `.map`** | `security-auth-review.md`、`deploy` 约定 |
| `__APP_VERSION__` | 由 `package.json.version` 注入，不硬编码 | `vite.config.js` `define` |
| 环境变量 | 仅 `VITE_` 前缀暴露；`.env.demo`/`.env.production` 各自持有；真实域名/IP/密钥**绝不入库** | 全局规则 §4 |

### 4.2 逐端配置要点

| 项 | 网页端 `apps/web` | 员工端 `apps/staff-h5` | 管理端 `apps/boss-h5` |
| ---- | ---- | ---- | ---- |
| 路由模式 | **history**（`/dashboard` 等真实路径） | **hash**（`createWebHashHistory`，壳内无服务端 rewrite 兜底） | **hash** |
| `base` | `/`（history 要求绝对 base；**禁用 `./`**）—— 若最终挂子路径 `/web/`，须 `base:'/web/'` 且 router `createWebHistory('/web/')` **同步** | `/staff/`（或子域根 `/`；hash 路由对 base 不敏感，但资源路径依赖 base） | `/boss/`（同左） |
| UI 库 | Element Plus（按需：`unplugin-auto-import` + `unplugin-vue-components` + `ElementPlusResolver`，`dts:false`） | Vant 4（`vant.js` 注册表 + 桌面触摸模拟仅 DEV） | Vant 4 |
| 别名 | `@` → `./src`；`@admin` → `../../hrm-admin/src`（**只读**，见 §4.4）；`@kdyzgl/*` → workspace 包 | `@` → `./src`；`@kdyzgl/*` → workspace 包（**禁引 `@admin`**） | 同员工端；**禁引 `@admin` 与 `src/pc`**（既有 ESLint 规则 4） |
| `VITE_API_BASE` | `/hrm-api/v1`（**待实测确认**，见 §0.5-2） | 同源 | 同源 |
| `VITE_CLIENT_TYPE` | `web` | `staff` | `boss` |
| MPA 入口 | 单入口 `index.html`（history 深链由 Nginx `try_files` 兜底） | 单入口 `index.html`（hash） | 单入口 `index.html`（hash） |
| dev server 端口（建议） | 5188 | 5189 | 5190（**需实测确认端口空闲**；避免与现有 5188 冲突） |
| `server.fs.allow` | 必须放行 workspace 根与 `hrm-admin`（`@admin` 别名读取；Vite 默认取「工作区根」，workspace 化后应自动生效，**需实测校验**） | 无需（不引 `@admin`） | 无需 |

> **`file://` 与 history 的既有冲突（`TODO(扩展)` 已登记于 `vite.config.js` L135-140）：** PC 入口 history 路由要求 `base:'/'`，而离线 `file://` 包要求 `base:'./'`，`base` 是全局配置无法按入口区分 —— **本方案不实施离线包**，移动端一律**远程 https 加载**（与现状 `BuildConfig.H5_URL` 一致）。若后续要离线包，须先验证 ESM 在 WebView 下能否加载。

### 4.3 环境变量与 fail-safe 保留清单

```bash
# apps/*/.env.demo        （演示态，可进仓库；无真实密钥）
VITE_MOCK_ENABLED=true
VITE_CLIENT_TYPE=<web|staff|boss>

# apps/*/.env.production  （生产态；API 基址以现网实测为准）
VITE_MOCK_ENABLED=false
VITE_API_BASE=/hrm-api/v1          # 待实测确认：hrm-server 经 courier-nginx 的暴露前缀
VITE_CLIENT_TYPE=<web|staff|boss>
```

> **红线：** `.env.production` 的 `VITE_API_BASE` 现状值 `/api/v1` 指向现网 `courier-app`（见 §0.5-2），**不得直接沿用**；落地前须实测 `hrm-server` 的 Nginx 反代前缀与版本段。

### 4.4 `@admin` 只读引用（网页端专属，影响评估见 §6.2-R2）

- **约束：** 网页端继续以 `@admin` 别名**只读引用** `hrm-admin/src`（登录页、`utils/request`、`stores/auth`、样式基座、部分一期页面），**零改动 `hrm-admin`**（对齐 §12.1 与反模式 A07）。
- **依赖代价（须登记）：** 网页端因此与一期 PC 端**物理耦合**；`hrm-admin` 的依赖树变化会影响网页端构建（`resolve.dedupe` 仍为必需）。
- **收敛路径（属裁定项 D2，见 §7）：** ① 长期保留只读引用；② 网页端逐步自建页面、最终摘除 `@admin`；③ 与 `hrm-admin` 合并为单一 PC 端实现。

### 4.5 安卓壳接入（员工端 / 管理端）

| 要点 | 方案 |
| ---- | ---- |
| 加载方式 | **远程 https 加载**（沿用 `BuildConfig.H5_URL`）；`assets/h5/*` 继续作为 gitignore 占位，**不做离线包** |
| `H5_URL` 注入 | 落地 `app/build.gradle` 的 `TODO(扩展)`：改为构建参数注入（如 `-Ph5Url=...`），真实域名**不入库** |
| 端分离 | 员工端与管理端**各自主张不同 H5 地址** → 二选一：**① 两个 APK**（各自 `buildConfigField`，应用名「驿站助手」/「驿站精灵」）或 **② 单壳双入口**（壳内参数切换 URL，需壳内 UI 支持）。**属裁定项 D5** |
| 缓存与热更新 | `WebView` 用默认缓存策略；**`index.html` 必须 `no-cache`**（引用带 hash 的 chunk，缓存住会「新页面引旧 chunk」）；业务迭代靠 Web 端发布，**免发 App** |
| 混合内容 | 壳必须加载 `https`（Android 9+ 默认拦明文）；仅 debug 经 `network_security_config.xml` 放行模拟器/局域网 |
| 桥接契约 | 保持 `HrmBridge`/`HrmShell` 名与入参约定不变（改名即打断通信）；`getWifiInfo` 在浏览器下返回 `mock:true` 且 UI 明示 |
| 验证状态 | **未编译验证**；本机无 Android SDK，验收收敛到「静态审查 + CI/服务器构建」 |

### 4.6 各端产物发布路径与 Nginx location 规划

> **硬约束：** `/hrm-api/` 已被 `hrm-server` 占用；现网 `/api/`、`/admin/` **不可动**（`demo-docker-deploy.md` §「明确不变」）。

| 端 | 产物 | 站点根（建议） | Nginx location（建议） | SPA 回退 |
| ---- | ---- | ---- | ---- | ---- |
| 网页端 | `apps/web/dist` | `/www/wwwroot/hrm-web` | `location /web/`（迁移期）；最终是否切 `/` 见 D1/D4 | `try_files $uri $uri/ /web/index.html`（history 必需） |
| 员工端 | `apps/staff-h5/dist` | `/www/wwwroot/hrm-staff-h5` | `location /staff/` | hash 路由**不需要**服务端回退，但仍建议 `try_files $uri $uri/ /staff/index.html`（防误配） |
| 管理端 | `apps/boss-h5/dist` | `/www/wwwroot/hrm-boss-h5` | `location /boss/` | 同左 |
| 演示站（迁移期） | `hrm-demo` 现有产物 | 现容器 `hrm-demo-static` | `location = /` + `location /`（**不动**） | 容器内规则不变 |

- **location 顺序：** nginx 前缀匹配「最长者优先」，故 `/web/`、`/staff/`、`/boss/` 会天然优先于 `location /`；但**`!= /` 与 `/` 仍指向演示站**，无冲突。
- **缓存：** `index.html` → `no-store`/`no-cache`；带 hash 的 `assets/*` → `max-age=31536000, immutable`；**缺失资源必须 404，不得回退 HTML**（否则 HTML 被当 ES 模块解析 → 整页白屏）。
- **子路径 base 一致性（易错点）：** 网页端 history 路由挂子路径时，`vite.base`、`router.createWebHistory(base)`、Nginx `alias/try_files` **三者必须一致**，否则刷新 404 或资源 404。
- **`.map` 防护：** 三端 location 均需拒绝 `*.map` 直接下载（`security-auth-review.md` 硬要求）。

---

## 5. 迁移路线（分阶段、可回滚）

> **原则：** 先抽共享包 → 再拆单端 → 最后拆剩余端；每阶段结束线上仍可用；**不得弱化门禁断言**。
> **本机环境：** 本机 **Node 可用**（`hrm-demo` 已实测 `verify:mock`/`build`/`build:prod` 通过）；**Java/Maven/Android SDK 不可用**，涉 Java/Android 的验收**收敛到服务器/CI**，标注「未运行」。

### 5.1 阶段清单

| 阶段 | 范围 | 前置 | 验收标准 | 回滚方式 | 风险 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| **S1 抽共享包（行为不变）** | 建 `hrm-clients/` workspace + `packages/{shared,api-client,mock}`；把 `shared/**` 迁入；`hrm-demo` 改为 `file:` 引用（**机械替换 import 路径**） | 无 | ① workspace `npm ci` 通过；② `hrm-demo` `verify:mock` **919/919 不降**；③ `build`/`build:prod`/`test`/`lint`/`lint:style` 全绿；④ `git diff -- hrm-dev/hrm-admin` 空；⑤ 线上 demo 未发布（无影响） | 删除新增 workspace 目录 + 还原 `hrm-demo` 的 import（纯新增/机械改动，反向 diff 即回滚） | **低** |
| **S2 拆网页端 `apps/web`** | 复制/重构 `src/pc` → `apps/web`；独立 `vite.config`（base `/`、dedupe、`@admin` 别名）；消费 `@kdyzgl/*` | S1 | ① `apps/web` `build`/`build:prod` 通过；② `build:prod` 产物**无 Mock chunk**（静态检索）；③ Playwright **PC 用例**平移通过；④ `hrm-demo` 不动、仍 919 | 删除 `apps/web`（Nginx 未改，线上零影响） | **中** |
| **S3 拆员工端 `apps/staff-h5`** | 从 `src/mobile` 抽出员工域（`views/staff/**`、员工专属 `api/stores/components`、`constants/{appName,tabs,quickEntries,todoGroups,makeup}`）→ hash 路由 + Vant + `@kdyzgl/*` | S1（可与 S2 并行**前提**：不共享可写文件、e2e harness 不重叠 —— 建议**串行**以避并发冲突） | ① 独立 `build`/`build:prod` 通过；② Playwright **移动端员工路径**用例平移通过；③ `?as=station` 登录分流正常；④ `verify:mobile` 对应断言**不得减少**；⑤ 无跨端源码复制（静态检索） | 删除 `apps/staff-h5` | **中–高**（内核拆分：`router`/`stores/auth`/`http` 需理清） |
| **S4 拆管理端 `apps/boss-h5`** | 抽出 `modules/boss/**` + 中立共享页（`MessagePage`/`NoticeReader`）+ boss 路由；**先裁定** `/boss/kpi` 跨域复用页归属 | S3（复用移动端内核拆分经验；跨域复用页须先定归属） | ① 独立 `build`/`build:prod` 通过；② Playwright **管理端路径**用例平移通过；③ `?as=boss` 登录分流正常；④ 中立共享页归属明确、无跨域直引残留（或按裁定保留并登记） | 删除 `apps/boss-h5` | **中–高** |
| **S5 安卓壳接入** | 按 D5 裁定产物（两 APK / 单壳双入口）；落地 `H5_URL` 构建参数注入；改 `network_security_config`（如需要） | S3、S4；**D5 已裁定** | ① 静态审查：`H5_URL` 无硬编码真实域名；② CI/服务器构建通过（**本机无 Android SDK，标注未运行**）；③ 壳加载 https 成功、桥接方法名未变（静态核对） | 还原 `build.gradle` 与壳配置（`git diff` 反向） | **中**（未编译验证） |
| **S6 发布切换（Nginx 新增 location）** | 新增 `location /web/`、`/staff/`、`/boss/` → 各自站点根；**不动** `location = /` 与 `location /` | S2–S4 产物就绪；**安全评估结论（P0.5）→ 主智能体三步授权** | ① 三端入口 `curl` 200；② 深链刷新正确落自身入口（不回退端选择页）；③ 缺失资源 404（不回退 HTML）；④ `.map` 不可公开；⑤ HTTPS 强制；⑥ 演示站原行为不变 | 还原 `nginx.conf.<时间戳>` + `nginx -t` + `reload` | **中–高**（改现网，C 档） |
| **S7 演示站处置** | 按 D1 裁定：保留演示工程 / 退役归档 / 切根到网页端 | S6 验收通过；**D1 已裁定** | ① 三端全部可用；② 「演示能力」确认可由 `VITE_MOCK_ENABLED` 构建模式承接（或在保留方案下确认 hrm-demo 仍在服务）；③ 退役时可回滚 | 恢复 Nginx 指向 + 重新拉起 `hrm-demo-static`（镜像 tag `20260924-3`） | **中** |

### 5.2 `hrm-demo` 单工程的保留与下线

| 时点 | 状态 |
| ---- | ---- |
| S1–S6 | **完整保留、冻结功能**（仅 P0 缺陷修复），继续服务线上演示与作为回滚锚点 |
| S6 之后 | 三端已上线，演示站与三端并存（`/` 仍为演示站） |
| S7 | 按 D1 裁定：**保留**（作独立演示工程）或 **退役归档**（tag 保留，不再发布）；**届时才可从发布链移除** |

### 5.3 e2e 与 verify 资产迁移（不得弱化断言）

| 资产 | 现状 | 迁移后归属 | 约束 |
| ---- | ---- | ---- | ---- |
| `scripts/verify-mock.mjs`（`verify:mock`，919/919） | 直接 import `src/shared/mock/*` | 随 `packages/mock` 迁到 workspace 根 `scripts/`（或 `packages/mock/scripts/`），import 路径改为包名 | **断言数不得低于 919** |
| `scripts/verify-mobile-t13-t16.mjs`（`verify:mobile`） | 校验移动端 T13–T16 | 归 `apps/staff-h5`（或保留在 `hrm-demo` 直至退役） | 断言只可平移/增强 |
| `scripts/gen-element-tokens.mjs`（`verify:tokens --check`） | 校验 Element Token 一致性 | 归 `apps/web` | 同上 |
| `e2e/00-dev-server.spec.js`、`07-network.spec.js`（跨端契约：Vite 内部模块不被改写、网络失败表现） | 单 dev server（5188） | **按端复制**：每端各持一份同源断言 | 断言只可平移/增强 |
| `e2e/02-pc-nav.spec.js` | PC 路由逐页 | → `apps/web/e2e` | 同上 |
| `e2e/03-mobile-nav.spec.js`、`04-forms`、`05-render`、`06-viewport` | 移动端（双视角） | → 拆为 staff / boss 两份 | 同上 |
| `e2e/playwright.config.js` | `baseURL: http://localhost:5188`、`workers:1` | 各端一份，`baseURL` 改各自 dev 端口 | `--config` 唯一入口纪律保留 |
| `e2e/utils/{harness,measure-ops,summarize}.mjs` | 共用工具 | 提升到 workspace 根 `e2e-utils/`（或各端复制） | — |

> **纪律：** 迁移 e2e/verify 时**严禁为通过而修改断言**（反模式 A06）。若迁移后门禁劣化，须**归因并登记**，不得静默改基线。

---

## 6. 硬约束与风险

### 6.1 不得触碰的范围（逐条）

| # | 禁改范围 | 依据 |
| - | ---- | ---- |
| 1 | `hrm-dev/hrm-admin/**`（一期生产 PC 端，仅 `@admin` 只读引用） | §12.1、反模式 A07 |
| 2 | `hrm-dev/hrm-server/**`（Java 源码；含 `db/migration/**` 迁移脚本） | 本轮边界、A05 |
| 3 | `.trae/rules/**`（规则唯一真源） | 全局规则、M06 |
| 4 | 既有契约与设计文档：`api.md`、`db.md`、`plan.md`、`server-architecture.md`、`algo-hrm-server.md`、`multi-client-architecture.md`、`security-auth-review.md`、`demo-login-redesign.md`、`project-tree.md`、`SESSION-STATE.md` | 本轮硬性约束 |
| 5 | 现网 Nginx 的 `location /api/`、`location /admin/` 及其余既有 location | `demo-docker-deploy.md`「明确不变」 |
| 6 | `/hrm-api/` 前缀（已被 `hrm-server` 占用） | 任务背景 |
| 7 | `hrm-android-shell/local.properties`（绝不入库）；壳内真实域名/IP | 全局规则 §4 |
| 8 | 演示态/生产态两条构建语义与 `resolve.dedupe` | §4.1 |

### 6.2 风险清单

| # | 风险 | 触发条件 | 影响 | 缓解 |
| ---- | ---- | ---- | ---- | ---- |
| R1 | **迁移期线上可用性** | 误删/误移 `hrm-demo` 源码，或提前改 `location /` | 演示站中断 | 冻结源 + 复制式迁移 + 新路径并行 + 切换点仅 S6 + 变更前备份 |
| R2 | **`@admin` 只读引用受影响** | `hrm-admin` 依赖树变动 / `resolve.dedupe` 被删 / `server.fs.allow` 未放行 | 网页端白屏或构建失败 | 保留 `dedupe`；`fs.allow` 放行 workspace 根与 `hrm-admin`；构建期校验（`build` 必须通过）；登记该跨工程依赖（D2） |
| R3 | **Mock 与真实后端分叉** | 三端各自维护 mock 或契约变更不同步 | 演示与生产行为不一致 | Mock 独立成包、三端同源；契约变更走 `api.md` 单一真源（P5） |
| R4 | **`resolve.dedupe` 类历史坑复现** | workspace 下出现多份 `vue`/`pinia`（如某 app 自带 `node_modules`） | `activePinia` 分裂 → 白屏 | **单 lockfile** + `dedupe` 保留 + 禁止 app 内独立 `npm install`（统一 workspace 安装）；构建后静态检索关键 chunk 内 `_s.get(` 是否唯一 |
| R5 | **fail-safe 语义失效** | 某端把「非空即开」或默认开 Mock | 生产误发假数据 / 演示静默走真网络 | 保留 `=== 'true'` 严格判定 + `--mode demo` 守卫插件 |
| R6 | **`.map` 公开** | Nginx 未拒 `.map` / sourcemap 改 `true` | 源码泄露 | 三端 location 均加 `.map → 404`；保持 `hidden` |
| R7 | **三端并存被后端单会话互踢** | 后端仍为 `hrm:session:{employeeId}` 单 key | 三端互相顶下线 | 依赖 `multi-client-architecture.md` §2.3 的**会话多端化（M1）**先落地；未落地前三端并存不可验收（见 D9） |
| R8 | **跨端复用页归属不清** | `/boss/kpi/:employeeId` 直引 `views/staff/kpi.vue` | 拆端后跨工程引用断裂 | 先裁定归属：提升为中立共享页（参照 `MessagePage.vue` 先例）或各端各建 |
| R9 | **子路径 base 与路由模式冲突** | history 端挂子路径而 `base`/`createWebHistory`/Nginx 三者不一致 | 刷新 404 / 资源 404 | §4.6「三者一致」检查项；history 端优先挂根路径或子域 |
| R10 | **迁移期双份代码漂移** | 源（`hrm-demo`）与新工程同时被改 | 行为分叉、回归失效 | 源冻结（仅 P0 修复）；单向迁移；同改动须在源与新端同步登记（优先只改新端） |
| R11 | **验证资产迁移失误** | e2e/verify 路径未随包迁移 / 断言被改 | 门禁失效、漏回归 | S1 即迁 `verify:mock` 并守 919 基线；断言只可平移/增强（A06） |
| R12 | **生产 API 基址误配** | 沿用 `/api/v1`（指向 `courier-app`） | 三端打到现网快递系统 | 落地前**实测** `hrm-server` 的 Nginx 前缀（`/hrm-api/...`）并写入 `.env.production`（D8） |

### 6.3 须经网络安全工程师评估清单（P0.5）

> 以下条目**均为安全面**，主智能体**不得凭经验放行**；须网络安全工程师按 L7 出结论（威胁建模 + 发现项 + 复现 + 修复建议），高风险项升级用户裁定。

| # | 条目 | 触发原因 | 建议评估要点 |
| - | ---- | ---- | ---- |
| SP1 | **新增 Nginx location（`/web/`、`/staff/`、`/boss/`）** | 生产外部暴露面变更 | 是否存在路径穿越/越权暴露、与既有 location 冲突、证书与 HTTPS 强制 |
| SP2 | **`.map` 防护** | 源码泄露 | 三端 location 是否均拒 `.map`；sourcemap 配置是否 `hidden` |
| SP3 | **安卓壳加载地址（`H5_URL`）与构建参数注入** | 外部暴露面 + 凭据 | 明文放行域最小化、真实域名不入库、混合内容拦截 |
| SP4 | **生产 API 基址（`/hrm-api/...`）** | 攻击面 | 与现网 `courier-app` 的 `/api/` 是否串扰、CORS/同源策略 |
| SP5 | **多端登录态与设备指纹（跨端并存）** | 鉴权 | 三端会话并存是否削弱风控（依赖后端 M1，见 `security-auth-review.md` §2.3） |
| SP6 | **共享包供应链** | 依赖 | `packages/*` 的 `peerDependencies` 与锁文件是否引入未知依赖/版本漂移 |

---

## 7. 需用户裁定项

| # | 议题 | 现状 | 建议 | 备选方案 | 影响 |
| - | ---- | ---- | ---- | ---- | ---- |
| **D1** | **`hrm-demo` 迁移后处置** | 单工程三入口，已上线（tag `20260924-3`） | **S7 后退役归档**（演示能力由 `VITE_MOCK_ENABLED` 构建模式承接） | ① 退役归档 ② 长期保留为独立演示工程 ③ 保留至二期再定 | 仓库结构、发布链长度、演示体验（现端选择页 + 剧本） |
| **D2** | **网页端 `apps/web` 与一期 `hrm-admin` 关系** | 网页端**只读复用** `@admin`（源码耦合，`hrm-admin` 零改动） | **短期保留 `@admin` 只读引用**（零改动一期、免复制），长期收敛另立 ADR | ① 保留只读引用 ② 网页端自建、逐步摘除 `@admin` ③ 与 `hrm-admin` 合并为单一 PC 端 | 一期改动面（涉 C 档）、双 PC 端维护成本、依赖风险（R2） |
| **D3** | **仓库粒度** | 单仓（根无 workspaces） | **方案 A：workspace 单仓多工程** | ① A workspace ② B 完全独立仓库 + npm 私服 ③ C 不拆工程仅分层 | 迁移成本、CI、版本一致性、协作成本（详见 §2.2） |
| **D4** | **三端发布路径与最终归属** | 演示站占 `/`；`/api/`、`/admin/` 不可动；`/hrm-api/` 已占 | **迁移期子路径**（`/web/`、`/staff/`、`/boss/`）；**最终态**是否切根路径另定 | ① 子路径 + 最终切根 ② 全程子路径 ③ 独立子域（需 DNS A 记录，现 `demo.`/`test.` 无 A 记录） | Nginx 配置（C 档）、`base` 与路由一致性、DNS |
| **D5** | **安卓壳产物形态** | 单壳加载 `mobile.html`（远程） | **两个 APK**（驿站助手 / 驿站精灵，各自 `H5_URL`） | ① 两壳两 APK ② 单壳双入口（运行时切 URL） ③ 暂缓（先纯 H5） | App 发布成本、壳内 UI 复杂度、品牌标识 |
| **D6** | **共享包边界** | 现 `src/shared/` 单目录 | **三包**：`shared` + `api-client` + `mock`；**通用 UI 组件提升另议** | ① 三包 ② 合并为单 `shared` 包 ③ 追加 `ui` 包（§3.2） | 依赖清晰度、包数量、UI 组件复用面 |
| **D7** | **三端品牌名冻结** | 网页端 `快递驿站智慧管理系统`；员工端 `驿站助手`；管理端 `驿站精灵`；系统名 `快递驿站智汇系统` | **维持现状**（三端品牌已定稿） | ① 维持 ② 网页端改名 | 静态 HTML `<title>` 与品牌常量需同改（`pc.html` 注释要求） |
| **D8** | **生产 API 基址** | `.env.production` = `/api/v1`（**指向 `courier-app`，错误**） | **实测** `hrm-server` 经 `courier-nginx` 的暴露前缀（任务称 `/hrm-api/`）后统一写入 | ① `/hrm-api/v1` ② `/hrm-api`（按后端实际版本段） | 三端能否连通真实后端 |
| **D9** | **拆分与后端会话多端化的先后** | 后端单会话互踢（`hrm:session:{employeeId}`） | **后端 M1（会话多端化）先行**，否则三端并存互相顶下线 | ① 后端先行 ② 前端先拆、后端后补（并存不可用） | 三端并存可用性、验收口径（R7） |
| **D10** | **e2e/verify 资产归属** | 集中于 `hrm-demo/e2e` 与 `hrm-demo/scripts` | **按端拆分各持一份**，公共工具提升到 workspace 根 | ① 各端持有 ② 集中于 workspace 根 ③ 混合 | 门禁完整性、维护成本、跨端契约测试覆盖 |

> 上述 D1–D10 未裁定前，**对应阶段不得开工**（P1 口径先行 / 反模式 A04）。D3/D4/D8/D9 为**阻塞 S1/S6 的前置**，建议优先裁定。

---

## 8. 回填指针（本方案落地后须同步的文档）

| # | 文档 | 须同步的内容 | 说明 |
| - | ---- | ---- | ---- |
| 1 | `hrm-dev/docs/project-tree.md` | 将 §0 的 `TODO(扩展) 三端独立代码` 指向本方案；补 `hrm-dev/hrm-clients/`（`packages/*` + `apps/*`）目录树；更新 `hrm-demo` 定位说明 | **本轮不改**，待拆分立项后回填 |
| 2 | `hrm-dev/hrm-demo/README.md` | 标注迁移关系（源 → 目标工程）、冻结声明、`hrm-demo` 退役时点 | 每端迁移完成后增量更新 |
| 3 | `hrm-dev/docs/demo-design.md` | 补「演示能力 = 构建模式（`VITE_MOCK_ENABLED`）」的演进说明；**不改其核心约束**（可替换数据源） | 只追加一致性说明 |
| 4 | `hrm-dev/docs/update-log.md` | 追加本文件条目（新增，无源码改动、无回滚需求） | **须追加** |
| 5 | （新增）`hrm-dev/docs/adr-01-三端拆分仓库与共享层.md` | 由本方案 §2.3 + 附录 A 落为独立 ADR | 拆分立项时创建 |
| 6 | （新增）各 `apps/*/README.md` | 各端构建/开发/发布说明、环境变量、`base` 约定 | 随各端建成创建 |
| 7 | `hrm-dev/docs/multi-client-architecture.md` | **不改**；仅在本方案内标注与本方案 §5 的 M1/M5 对齐关系（会话多端化为三端并存前置） | 交叉引用 |
| 8 | `AGENTS.md` / `SESSION-STATE.md` | 落地时按 M02 追加检查点（阶段完成一行 + 影响文件 + 回滚点） | 每阶段即时追加 |

---

## 附录 A：ADR 草案（拆分立项时落为独立文件）

- **ADR-SPLIT-01（仓库粒度）**：状态 `提议`。决策：**采纳方案 A（workspace 单仓多工程）**。后果：单一 lockfile（版本一致性内生保障）、本地 `file:` 复用无需发包、`hrm-demo` 可整体剥离；代价：需迁入 root lockfile 与 workspace 约定。备选：B 完全独立仓库 + 私服（否决，基建先行、协作成本高）；C 不拆工程（否决，不满足三端独立代码目标）。
- **ADR-SPLIT-02（共享层边界）**：状态 `提议`。决策：抽 `@kdyzgl/shared`（纯逻辑）+ `@kdyzgl/api-client`（请求工厂/错误码/契约）+ `@kdyzgl/mock`（Mock 引擎与 145 路由）三包；**通用 UI 组件提升另议**。后果：`errorCode`/`role`/`dict`/`device`/`clientLog` 单源；三端不复制请求封装。备选：单 `shared` 包（依赖不清晰）；各端自带封装（违反复用原则）。
- **ADR-SPLIT-03（端差异表达）**：状态 `提议`。决策：**沿用 `multi-client-architecture.md` ADR-MC-02——API 保持角色驱动，不按端分区**；端维度用可选 `X-Client-Type` 头。后果：145 契约零改动、三端共用 `/api/v1/*`。备选：按端路径分区（改动 300+ 处、全量回归，否决）。
- **ADR-SPLIT-04（迁移策略）**：状态 `提议`。决策：**冻结源 + 复制式迁移 + 新路径并行 + 单一切换点（S6）**。后果：迁移期线上零中断、可逐端回滚；代价：临时双份代码（有界、完成后退役源）。备选：原地重构单工程（破坏线上可用性，否决）。
- **ADR-SPLIT-05（安卓壳加载）**：状态 `提议`（依赖 D5）。决策：**远程 https 加载**（不做 `file://` 离线包）；`H5_URL` 改为构建参数注入。后果：业务迭代免发版；壳能力变更才发 App。备选：离线包（`base` 与 history 硬冲突，且 ESM 在 `file://` 未验证，否决）。

## 附录 B：术语

| 术语 | 含义 |
| ---- | ---- |
| 端 | 网页端（`apps/web`）/ 员工端「驿站助手」（`apps/staff-h5`）/ 管理端「驿站精灵」（`apps/boss-h5`） |
| 演示态 / 生产态 | 构建模式：`build`（含 Mock，供演示/评审）与 `build:prod`（剥离 Mock，接真实后端） |
| 共享包 | `hrm-clients/packages/*`（`shared` / `api-client` / `mock`），三端消费、禁反向依赖 `hrm-admin`/`hrm-demo` |
| 冻结源 | 迁移期 `hrm-demo` 停止加功能，仅 P0 修复，作为演示站与回滚锚点 |
| C 档 | 需主智能体三步授权的操作（改现网 Nginx、DNS、端口放行等） |
| P0.5 | 安全评估先行：安全面操作须先取网络安全工程师技术结论，再授权 |

---

## 附录 C：本轮检查点（M02）

- **2026-09-24**：完成「三端独立代码拆分方案」设计稿，产出 `hrm-dev/docs/multi-client-split-plan.md`（**新增**）。**未改动任何源码、契约文档、规则文件、迁移脚本**；未触达生产；无回滚需求（纯新增文档）。
- 事实基线（文件计数 / 请求层 / 构建纪律 / 线上拓扑）均来自本轮实测，来源见 §0。
