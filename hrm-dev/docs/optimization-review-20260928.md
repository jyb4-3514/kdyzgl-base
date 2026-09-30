# 快递驿站智汇系统 · 架构与代码质量优化建议（只读审查）

| 项目 | 内容 |
| ---- | ---- |
| 审查对象 | `hrm-dev/` 全量（后端 `hrm-server`、多端 `hrm-clients`、演示工程 `hrm-demo`、一期前端 `hrm-admin`、安卓壳 `hrm-android-shell`、部署 `deploy`、文档 `docs`、迁移 `db/migration`） |
| 审查方式 | **纯只读静态审查**：读取源码 / 配置 / 迁移脚本 / 文档，并辅以只读 git 状态查询。**未编译、未运行、未连库、未跑测试、未执行任何写操作** |
| 审查日期 | 2026-09-28 |
| 分支 | `feature/前端演示项目拆分与精细化` |
| 工作树状态 | 约 **135 个已修改文件，+10023 / -2063** 行，另有较多未跟踪文件（含 `V20`–`V23` 迁移） |
| 本机环境 | Node 可用；**无 JDK / Maven / MySQL / Redis / Android SDK** → 后端与安卓壳结论一律为静态判定 |
| **总体结论** | **有条件继续开发；当前版本不宜直接合并或上线** |

> 凭据声明：本文不含任何真实口令 / 密钥 / 凭据 / 服务器地址；涉及域名与 IP 一律不落具体值。

---

## 一、系统结构概览

| 层 | 组成 | 说明 |
| - | ---- | ---- |
| 多端入口 | `apps/web`（PC 管理后台）、`apps/boss-h5`（驿站精灵 / 管理端）、`apps/staff-h5`（驿站助手 / 员工端）、`hrm-android-shell`（安卓 H5 壳，双 flavor） | 三端已独立工程化，各有入口 / 路由 / stores / api / 样式 / 产物 |
| 共享层 | `packages/tokens`、`packages/shared`、`packages/api-client`、`packages/mock` | npm workspace 单 lockfile；Token、常量、请求层、Mock 收敛为单份 |
| 业务核心 | `hrm-server`（Spring Boot 3.3.4 / Java 17 / MyBatis-Plus / JWT / EasyExcel / Flyway） | 覆盖认证、员工、组织、考勤、排班、薪资、工单、包裹、同步、通知、KPI、入离职 |
| 数据 / 部署 | MySQL 8、Redis、Flyway（`db/migration/mysql` V1–V23）、Nginx、宝塔 / systemd / nohup | 迁移只增不改历史版本；生产走 Git pull + 外置配置 |

真实规模（静态计数）：Controller 方法级 Mapping 约 **164** 个；后端 `main/java` 53 个文件含 `TODO(扩展)` 标注；前端三端各配 `lint / lint:style / test / e2e / build:prod`。

---

## 二、做得好的点（应保持）

1. **共享层与端边界清晰**：`packages/*` 不反向依赖任何端（`hrm-clients/README.md:31-36`），Token 真源中立且受 `verify:tokens` 门禁覆盖（ADR-结构迁移-01 §2.2）。
2. **权限与数据范围服务端收敛**：`RequireRolesInterceptor` fail-closed + `QueryDataScopeInterceptor` 静默收敛 + 域策略（`WorkOrderAccessPolicy` 等）三层防护；端类型只作审计、不参与鉴权。
3. **复杂业务有状态机与策略类**：薪资 `PayrollStateMachine` / `PayrollGenerateGuard`、工单 `WorkOrderStateMachine`、排班 `ShiftPayrollPolicy`、考勤 `AttendanceSummaryPolicy`，并用端口（`PayrollSettlementPort`、`ApprovedLeaveDaysPort`）解跨域依赖、避免循环。
4. **迁移与快照纪律较好**：`db.md` / `init.sql` / 迁移三方对读；`V22` 用「生成列 + 唯一键」处理逻辑删除后的活跃排班唯一性（`V22__attendance_schedule_multi_shift.sql:71-76`），并保留预检与回滚段。
5. **前端工程质量基线已建立**：三端均有 ESLint（含可访问性插件）、Stylelint、Vitest、Playwright、生产/演示双构建；生产构建 `sourcemap: 'hidden'`（`web/vite.config.js:187` 等）。
6. **评审与未验证项记录意识强**：`docs/` 下已有技术评审与安全评审多份报告，并显式区分「已验证 / 待核实 / 未运行」。

---

## 三、发现清单

> 分级：**P0 = 建议立即阻断**；**P1 = 近期必须闭环**；**P2 = 架构与维护性优化**。每条含证据、影响、建议与可静态判定的验收标准。

### P0-1 · 未认证公开写端点 `auto-dispatch` 仍在白名单（阻断项）

- **证据**：`PublicEndpoints.java:26` 定义 `WORK_ORDER_AUTO_DISPATCH`；`:57-60` `PATHS = Set.of(AUTH_LOGIN, WORK_ORDER_AUTO_DISPATCH, ...)`；单测 `PublicEndpointsTest.java:16-18` 明确断言 `isPublic("/api/v1/work-orders/auto-dispatch") == true`。
- **影响**：代码层允许无凭据建单并触发通知；一旦 Nginx 漏配、换环境或复制部署，攻击面即恢复。现网 403 属外围补偿，不改变代码层公开性。
- **建议**：
  1. 企微回调**签名验签 + 解密**（`msg_signature` / `timestamp` / `nonce` + `EncodingAESKey`）落地前，从 `PublicEndpoints` 移除该路径；
  2. 未配置验签密钥时端点默认 404/403（fail-closed）；
  3. 过渡期在 Nginx 侧默认拒绝公网并加 `limit_req`。
- **验收**：无签名 / 伪造签名 / 过期时间戳 / 重复 nonce 一律非 200；`PublicEndpoints.all()` 不含该路径（同步更新单测断言，不得只改实现不改断言）。

### P1-2 · 强制改密存在 fail-open 路径

- **证据**：`PwdChangedInterceptor.java:55-57` 对 `pwdChanged == null` 放行；`JwtAuthFilter.java:188-207` 旧会话补齐失败时 `catch` 后 `return true`（按「已改密」放行）。
- **影响**：高权限站长 / 管理员在弱初始口令下，可借异常路径绕过改密闸门直连业务接口。
- **建议**：新会话必须携带明确布尔值；旧会话补齐失败返回系统错误或强制重登；补「Redis 旧会话 / 员工不存在 / 数据库异常」三组测试。
- **验收**：`pwd_changed=0` 会话访问非白名单接口 → 被拒（非 200 业务成功）；上述三组异常路径不得静默放行。

### P1-3 · 密码登录无失败限流，图形验证码默认关闭

- **证据**：`AuthServiceImpl.java:118-129` 密码错误仅记日志后抛 1001，无计数 / 退避 / 锁定；`application.yml:104` `captcha-enabled: false`。短信通道有 `smsCodeStore.isSendBlocked` 频控，密码通道没有。
- **影响**：在线口令爆破；初始口令为全局共享配置（`application.yml:79` 占位），一旦偏弱可批量尝试。
- **建议**：按「账号 + IP」双维度失败计数 + 指数退避 / 锁定；高失败率触发验证码；校验可信代理 IP 取值，防伪造转发头绕过。
- **验收**：连续 N 次错误触发限速 / 锁定并返回对应码；伪造 `X-Forwarded-For` 不改变限速归属。

### P1-4 · 双库支持「配置上支持、结构上不可运行」

- **证据**：MySQL 迁移已到 `V23`，PostgreSQL 仅 `V1/V2`；但 `pom.xml:61-85` 同时含 MySQL 与 PostgreSQL 驱动，`application.yml:34-37` Flyway 按 `{vendor}` 自动选目录。
- **影响**：任何一次 PG 数据源切换都会因缺失 V3–V23 而失败，属「隐性不可用能力」。
- **建议**：二选一 —— 一期明确仅支持 MySQL（移除 PG 驱动与切换说明），或补齐 PG V3–V23 并纳入双库迁移测试。
- **验收**：`README` / `db.md` 声明的数据库支持范围与迁移目录实际版本集合一致。

### P1-5 · 接口契约无自动对账，文档自相矛盾

- **证据**：`api.md:10` 写「接口总数 90」，`api.md:217` 又写「接口概览（89 个）」；而 Controller 方法级 Mapping 静态计数约 **164**。
- **影响**：契约真源实际漂移，前端 / 测试 / 新人无单一权威依据；Mock 与后端分属两工程，无门禁捕获漂移。
- **建议**：以代码或 OpenAPI 为机器真源自动生成端点清单，CI 比对路径 / 方法 / 角色 / 模型 / 错误码，并检查 Mock 路由与 Controller 差集。
- **验收**：生成器输出与 `api.md` 差集为空（或差集在白名单内）；文档内同类计数唯一。

### P1-6 · 缺少统一 CI 与根级质量门禁

- **证据**：全仓无 `.github/workflows`（无任何 CI 配置）；`hrm-clients/package.json:8-13` 仅有 `verify:mock` / `verify:tokens` / `e2e:*`，**没有聚合的 lint / unit test / build:prod 入口**。
- **影响**：三端各有脚本但无统一入口，本地与 CI 易漏跑；后端测试多为纯单测或 standalone MockMvc，覆盖不到拦截器链、事务、Redis 会话与真实 SQL。
- **建议**：
  1. 根级单一入口：`contract → lint → unit test → build:prod → critical e2e`；
  2. 后端补真实 Spring 容器 + MySQL + Redis + Flyway 集成测试（可用 Testcontainers）；
  3. 预提交钩子至少跑格式化与受影响测试。
- **验收**：一条命令可在干净环境复现全部门禁；关键链路（登录 / 越权 / 算薪并发）有集成测试覆盖。

### P1-7 · 部署基线需硬化

| 项 | 证据 | 建议 |
| - | ---- | ---- |
| HTTPS 仍为「可选」 | `nginx.conf.example:22-24` 仅 `listen 80`；HTTPS 段 `:80-94` 为注释 | 生产模板强制 443 + 80→301，补 HSTS / nosniff / Referrer-Policy |
| 模板无 `.map` 拦截 | 全 `nginx.conf.example` 无 `\.map$` 规则（正确先例在 `docker-demo/nginx.conf:55-57`） | 三端站点内先于通用规则加 `location ~* \.map$ { return 404; }` |
| 后端未限制监听地址 | `application.yml:13-16` 仅 `port: 8080`，无 `server.address` | 显式绑定回环 / 指定内网地址，配合防火墙最小化暴露 |
| nohup 回退以 root 运行 | `deploy.sh:17-19,52` auto 模式无 systemd 单元即回退 nohup；`:132` 强制 root 执行；而 `hrm-server.service:38-39` 正确为 `User=www` | nohup 分支显式降权（`runuser -u www` / `setpriv`），或书面禁用 nohup 模式 |
| 健康检查污染登录日志 | `deploy.sh:57` `HEALTH_URL` 指向 `/api/v1/auth/login`；`:304-330` 通过请求登录接口判活 | 提供受限健康端点（`/actuator/health` 或自建 `/health`），仅内网可达 |

- **验收**：`curl -I https://…/` 含 HSTS；`.map` 探测 403/404；`ps -o user=` 非 root；健康检查不再产生登录失败记录。

### P2-8 · 超大文件已成维护热点

| 文件 | 行数 |
| - | --: |
| `hrm-server/.../service/finance/impl/PayrollServiceImpl.java` | 1606 |
| `hrm-clients/packages/mock/src/financeStore.js` | 1740 |
| `hrm-clients/packages/mock/src/attendanceStore.js` | 1685 |
| `hrm-clients/scripts/verify-mock.mjs` | 7870 |

- **建议**：Service 按「用例编排 / 状态迁移 / 审计 / 查询 / 持久化 / 通知」拆分；Mock 按业务域拆分并由统一 runner 汇总断言（保持断言只增不减）。
- **验收**：单文件控制在可维护区间；`verify:mock` 断言数不下降且打印基线来源。

### P2-9 · 前端迁移未真正完成：仍耦合 `@admin`，且 `hrm-demo` 保留镜像

- **证据**：`apps/web/src/router/index.js:59,92,98,157` 直接 `import('@admin/views/...')`；`apps/web/src/main.js:22,27` 引入 `@admin/styles/index.scss` 与 `@admin/utils/request`；`apps/web/vite.config.js:151-155` 配置 `@admin` 别名指向 `../../../hrm-admin/src`。同时 `hrm-demo/src` 仍保留大量与 `hrm-clients` 镜像的实现。
- **影响**：「生产 Web 端」仍依赖一期工程源码；`hrm-demo` 与 `hrm-clients` 双份实现并存，双改 / 漏改概率高（此点既有评审 M6 已登记，但缺截止时间）。
- **建议**：给 `@admin` 依赖与 `hrm-demo` 退役设定**明确截止版本与责任人**；新页面一律在 `apps/web` 自建。
- **验收**：`apps/web/src` 检索 `@admin` 命中数为 0（或仅剩已登记的过渡白名单）；`hrm-demo` 退出门禁 / 构建链路。

### P2-10 · 项目状态与代码事实不一致

- **证据**：`docs/plan.md:16-18` 仍定义「二期 = 驿站数据同步、三期 = 工单系统」，而 `AGENTS.md:9` 定义为「二期 = 包裹数据采集、三期 = 企业微信接入」；同时代码中已有包裹、同步、工单、薪资自动化及 `V10`–`V23` 迁移。
- **影响**：路线规划与协调依据冲突；「写完代码」易被误认为「完成验收」。
- **建议**：只保留一个路线真源，并把状态拆为「规划 / 已实现 / 已静态验证 / 已运行验证 / 已上线」。
- **验收**：`plan.md` 与 `AGENTS.md` 路线表逐行一致；各模块状态可判定。

---

## 四、30 / 60 / 90 天优化路线

**0–30 天（阻断与安全）**
1. 移除 `auto-dispatch` 公开白名单（P0-1），补签名验签方案。
2. 修复强制改密 fail-open（P1-2）并补异常路径测试。
3. 加登录限流与验证码（P1-3），校可信代理 IP。
4. 冻结当前大批未提交改动，按模块拆为可独立评审 / 回滚的提交。

**30–60 天（质量门禁）**
5. 建统一 CI 与根级门禁（P1-6），补后端真实集成测试。
6. 接口契约自动生成与对账（P1-5），统一 `api.md` 计数。
7. 明确数据库支持范围，消除 PG 隐性不可用（P1-4）。
8. 部署硬化：健康端点、HTTPS、安全头、`.map` 拦截、非 root（P1-7）。

**60–90 天（架构收敛）**
9. 拆分超大 Service / Mock 文件（P2-8）。
10. 摘除 `@admin` 依赖，设定 `hrm-demo` 退役截止（P2-9）。
11. 统一路线与状态真源（P2-10）。
12. 在具备 JDK / MySQL / Redis / Android SDK 的环境完成：编译、Flyway 空库升级、鉴权越权回归、并发算薪、安卓真机验证。

---

## 五、未验证项与边界（不得视为已确认）

1. **后端未编译、未启动、Flyway 未执行**：本机无 JDK / Maven / MySQL / Redis → 「可编译 / 可启动 / 迁移可执行」均未验证。
2. **接口连通、鉴权越权、数据范围实际行为**：未连库、未做端到端 / 渗透验证。
3. **后端单测未执行**：测试文件存在但本机无 Maven → 覆盖率与绿否未知。
4. **前端三端 `test` / `build` / `e2e`**：本次未重跑（保持只读、避免生成产物）。
5. **安卓壳未编译验证**：无 Android SDK；壳加载、桥接、混合内容、HTTPS 均待真机。
6. **真实部署配置未核**：现网 Nginx / 证书 / 防火墙 / Redis 绑定与口令强度属服务器阶段实测项。
7. **旧版评审报告可能过期**：`tech-review-full-20260926.md` 等绑定当时版本（V1–V15 / 当时工作树），当前工作树已大幅变更，结论需重评。

---

## 六、一句话建议

**先关掉公开写端点、补上强制改密的 fail-closed 与登录限流，再把「统一 CI + 契约对账 + 真实集成测试」这条验证链建起来；架构拆分与文档收敛随后按版本推进。当前版本可作为「继续开发的基线」，不应作为「可上线版本」。**
