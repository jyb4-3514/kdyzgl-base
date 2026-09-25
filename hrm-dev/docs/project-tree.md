# 项目目录树 · 快递驿站智汇系统

> 生成日期：2026-09-24
> 生成方式：工作区实际扫描（排除 `node_modules`/`dist`/`target`/`.git`/`.gradle`/`backups`/`__pycache__` 等噪音目录，深度 3 层）
> 安全说明：**本文不含任何凭据值**；`.secrets/` 仅列文件名。
> 维护要求：目录结构变更（新增工程/顶层目录）须同步本文。

---

## 0. 三端定位（2026-09-24 用户确认）

| 端 | 当前形态（初级定稿） | 终极形态 | 备注 |
| --- | --- | --- | --- |
| 员工端 | `hrm-dev/hrm-demo/src/mobile/views/staff/` | **安卓壳 + H5**，名称「**驿站助手**」 | 复用 `hrm-android-shell` |
| 老板端 | `hrm-dev/hrm-demo/src/mobile/modules/boss/` | **安卓壳 + H5** | 复用 `hrm-android-shell` |
| 网页端 | `hrm-dev/hrm-demo/src/pc/` | 纯网页端 | — |
| 生产 PC 管理端 | `hrm-dev/hrm-admin/` | 纯网页端 | 一期生产端，对 Demo 仅 `@admin` 只读引用 |
| 后端 | `hrm-dev/hrm-server/` | **三端共用一个后端服务** | 端口 8080，Nginx 反代 |

> **`TODO(扩展)` 三端独立代码**：终极形态要求三端**独立代码**（当前 `hrm-demo` 为单工程三入口 `index.html`/`pc.html`/`mobile.html`），拆分需单独 ADR（含仓库/工程边界、共享层策略、构建与发布流程）。
> **`TODO(扩展)` 短信验证码登录**：所有登录页新增短信验证码接口，使用**阿里云短信服务**（凭据由主智能体托管，不入库）。
> **`TODO(扩展)` 高德定位**：定位校验改用**高德 API 服务**（Key 待申请；申请前保留现有 Haversine 降级路径）。

---

## 1. 顶层结构

```
kdyzgl-base/
├── .github/
│   └── CONTRIBUTING.md              # 与 .trae/rules/项目规则1.md 同源同步
├── .secrets/                        # 本地凭据目录（.gitignore 忽略，主智能体独占读写）
│   ├── basic-auth-hash.txt
│   └── hrm-demo-basic-auth.txt
├── .trae/
│   ├── agents/                      # 10 个角色智能体配置（各自 SKILL.md）
│   ├── rules/                       # 项目规则1.md + 智能体调度规则.md（自动注入）
│   └── skills/                      # 14 个技能（token-optimizer / engineering-discipline / ...）
├── hrm-dev/                         # 全部工程与文档（见第 2 节）
├── .gitignore
├── AGENTS.md                        # 新会话入口（≤80 行，只放状态与指针）
├── SESSION-STATE.md                 # 跨会话检查点（顶部为状态摘要块）
├── env.example                      # 环境变量模板（仅参数名 + change_me_* 占位符）
├── 全局规则.md                       # 跨项目通用准则
└── 智能体配置.md                     # 团队编制说明
```

---

## 2. `hrm-dev/` 工程矩阵

```
hrm-dev/
├── collector/                       # 二期采集端（规划中，未开工）
│   ├── config/
│   └── scripts/
├── deploy/                          # 部署脚本与配置
│   ├── docker-demo/                 # 三端 Demo 静态站容器（含 nginx.conf）
│   ├── scripts/                     # hids-log-rotate.sh / zz-kdyzgl-storage.cnf 等
│   ├── deploy.sh
│   ├── hrm-server.service
│   └── nginx.conf.example
├── docs/                            # 全部文档（见第 3 节）
├── examples/
│   └── import/                      # Excel 导入样例
├── hrm-admin/                       # 生产 PC 管理端（Vue 3 + Vite 6 + Element Plus）
│   ├── public/
│   ├── src/
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── hrm-android-shell/               # 安卓 H5 壳（WebView + Gradle）
│   ├── app/
│   ├── build.gradle
│   ├── local.properties.example     # 真实 local.properties 绝不入库
│   └── settings.gradle
├── hrm-demo/                        # 三端演示 Demo（初级定稿；Mock 假数据零后端依赖）
│   ├── e2e/                         # Playwright
│   ├── scripts/                     # verify-mock.mjs / verify-mobile-t13-t16.mjs 等
│   ├── src/                         # 见第 4 节
│   ├── .env.demo                    # 演示态（VITE_MOCK_ENABLED=true）
│   ├── .env.production              # 生产态（剥离 Mock，走 /api/v1）
│   ├── index.html / pc.html / mobile.html   # 三入口
│   ├── vite.config.js
│   └── vitest.config.mjs
├── hrm-server/                      # 后端（Spring Boot 3.3.4 + Java 17 + MyBatis-Plus + MySQL 8 + Redis）
│   ├── src/
│   └── pom.xml
├── poc/
│   └── wecom/                       # 三期企微 POC 脚本
├── sql/
│   └── schema/
│       └── mysql/init.sql           # 全量结构快照（37 表 / 52 索引）
├── TASK.md                          # 一期原子任务清单
└── .dockerignore
```

### 2.1 多端 workspace：`hrm-clients/`（2026-09-25 回填，追加）

> 阶段：ADR-结构迁移 **B1–B5 已落地**（三端独立工程 + 共享包）。本小节为**追加**，不改动上文既有结构描述。
> §0 的 `TODO(扩展)` 三端独立代码**已由本节与 `hrm-dev/docs/adr-structure-migration.md` 承接**。

```
hrm-clients/                         # 多端 workspace（npm workspaces，根包 @kdyzgl/clients）
├── apps/
│   ├── web/                         # 网页端（PC 管理后台），dev :5191，base:'/web/'（history 路由）
│   ├── staff-h5/                    # 员工端「驿站助手」，dev :5189，base:'/staff/'（hash 路由）
│   └── boss-h5/                     # 管理端「驿站精灵」，dev :5190，base:'/boss/'（hash 路由）
├── packages/
│   ├── tokens/                      # Design Token 真源（tokens.base.scss + scripts/gen-element-tokens.mjs）
│   ├── shared/                      # 纯逻辑 + 中立共享 UI（constants / domain / ui / composables）
│   ├── api-client/                  # 请求层工厂 createHttp + 接口契约 contracts
│   └── mock/                        # Mock 引擎（routes / stores / engine / install；唯一行为规格）
├── e2e-utils/                       # 跨端 e2e 公共 harness（harness.js）
├── scripts/                         # 根级跨端门禁（verify-mock.mjs）
├── package.json                     # workspaces + 根脚本（verify:mock / verify:tokens / e2e:{staff,boss,web}）
├── README.md
└── .gitignore
```

| 端（工程） | dev 端口 | `vite.base` | 路由模式 | 现网入口 |
| --- | --- | --- | --- | --- |
| `apps/web` | 5191 | `/web/` | history（需 SPA 回退） | `/web/` |
| `apps/staff-h5` | 5189 | `/staff/` | hash | `/staff/` |
| `apps/boss-h5` | 5190 | `/boss/` | hash | `/boss/` |

> **边界与纪律：** `packages/*` 禁依赖任何端（含 `@admin`）与 Element Plus / Vant；根提供 `verify:mock` / `verify:tokens` 两个**跨端**门禁，各端自持 `build` / `build:prod` / `lint` / `test` / `e2e`。三端 `vite.base`、`createWebHashHistory`/`createWebHistory` 的 base、Nginx `location` 前缀**三者必须一致**（否则资源 404）。现网落点与回滚见 `hrm-dev/docs/deploy.md` §11。

---

## 3. `hrm-dev/docs/` 文档索引

```
docs/
├── algo-scripts/                    # 算法离线原型（Node，node run-all.mjs 一键复现）
├── screenshots/                     # 走查截图
├── 需求与规划
│   ├── requirement.md               # 需求
│   ├── plan.md                      # 迭代规划（一期/二期/三期）
│   └── TASK.md（上级目录）
├── 契约与设计
│   ├── api.md                       # 接口契约（一期 24 + M11 增量 16）
│   ├── db.md                        # 库表基准（v2.0，含 37 表）
│   ├── server-architecture.md       # 服务端架构（P0–P10 批次 + ADR）
│   └── algo-hrm-server.md           # 算法方案（8 场景四件套）
├── 部署与运维
│   ├── deploy.md                    # 部署手册（含 §0.5 存储策略）
│   └── demo-docker-deploy.md
├── 测试
│   ├── test-cases.md                # 用例
│   └── demo-functional-test-report.md
├── Demo 系列（demo-*.md）            # 设计/里程碑/UI/UX/员工端/老板端/同步配置/请假等
├── 二期采集端（collector-*.md）      # site-analysis / site-tech-audit / architecture-adr / project-plan
├── 三期企微（wecom-integration-adr.md）
├── 团队与规范
│   ├── agent-team-design.md
│   └── project-tree.md              # 本文
└── update-log.md                    # 变更日志
```

---

## 4. `hrm-demo/src/` 结构（三端入口 + Mock 层）

```
src/
├── portal/                          # 入口聚合页（index.html）
├── pc/                              # 网页端（PC 管理端演示）
├── mobile/                          # 移动端（员工端 + 老板端）
│   ├── modules/boss/                # 老板端模块
│   ├── views/staff/                 # 员工端（终极形态：「驿站助手」）
│   ├── components/
│   ├── router/
│   └── stores/
└── shared/
    ├── mock/                        # Mock 后端（axios 自定义 adapter）
    │   ├── routes/                  # 17 个路由模块，共 145 条 API
    │   ├── db.js                    # 种子实体
    │   ├── engine.js                # 匹配/鉴权/角色/分页/错误响应
    │   ├── *.Store.js               # 9 个领域数据层（parcel/attendance/kpi/hr/finance/leave/syncConfig/clientLog/session）
    │   └── install.js / overlay.js / persist.js
    ├── domain/                      # pagination / applyDataScope
    └── constants/                   # dict / errorCode / role
```

---

## 5. `hrm-server/src/main/` 结构（后端）

```
src/main/
├── java/com/qiujie/
│   ├── controller/{auth,dashboard,department,employee,station}   # P0 既有 24 接口
│   ├── controller/{systemlog,notification,attendance,kpi,hr,finance,leave}/  # P1~P7 新增
│   ├── service/{domain}/ + service/{domain}/impl/ + service/{domain}/support/  # 纯逻辑可单测
│   ├── service/{domain}/port/        # 跨域端口（依赖倒置，避免环）
│   ├── service/support/              # 跨域共用纯逻辑
│   ├── mapper/  entity/  handler/    # MyBatis-Plus + JSON TypeHandler
│   ├── dto/{domain}/  dto/support/   # 入参（PageQuery / StationScopedQuery）
│   ├── vo/{domain}/                  # 出参
│   ├── common/                       # Result / PageResult / SessionInfo / LoginUser / PublicEndpoints
│   ├── config/                       # WebConfig / 拦截器 / AlgoProperties / Redis / MyBatisPlus
│   ├── aspect/  annotation/  filter/  enum/  exception/  util/
│   └── HrmServerApplication.java
└── resources/
    ├── application.yml               # 含 hrm.algo.* / hrm.hr.* / hrm.storage.* 配置命名空间
    └── db/migration/mysql/           # Flyway V1~V13（V1/V2 已执行，永不修改）
```

> 数据库落位：生产为宿主 MySQL 8（端口 3307，`datadir=/data/mysql-host`，数据盘），系统盘只放系统文件。详见 `deploy.md` §0.5。

---

## 6. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-09-24 | 首次生成；补入三端定位、短信验证码与高德定位待办 |
| 2026-09-25 | 追加 §2.1 多端 workspace `hrm-clients/`（B1–B5 落地：`apps/{web,staff-h5,boss-h5}` + `packages/{tokens,shared,api-client,mock}` + `e2e-utils/`；dev 端口 5191/5189/5190，base `/web/` `/staff/` `/boss/`） |
