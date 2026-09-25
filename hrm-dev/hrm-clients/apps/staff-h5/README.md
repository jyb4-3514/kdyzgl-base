# 员工端「驿站助手」H5（`@kdyzgl/staff-h5`）

> 快递驿站智汇系统 · 三端独立代码拆分 **B3** 产物（依据 `hrm-dev/docs/adr-structure-migration.md` §3）。
> 由 `hrm-dev/hrm-demo` 移动端**复制式迁移**并端固定化：自有入口 / 路由 / stores / api 薄壳 / 样式平台层 / 构建产物。

## 命令

| 命令 | 说明 |
| ---- | ---- |
| `npm run dev` | 开发服务，端口 **5189**（strictPort），应用 base `/staff/` |
| `npm run build` | 演示态构建（`--mode demo`，含 Mock），产物 `dist/` |
| `npm run build:prod` | 生产态构建（`--mode production`，剥离 Mock） |
| `npm run preview` | 预览演示态产物 |
| `npm run verify:mobile` | 员工路径数据链路实测（36 条断言，见 `scripts/verify-mobile.mjs`） |
| `npm run verify:tokens` | 本端平台层 Token 一致性校验 |
| `npm run lint` / `npm run lint:style` | ESLint（依赖边界规则）/ Stylelint |
| `npm run test` | Vitest 单测 |
| `npm run e2e` | Playwright（需先 `npm run dev`；`--config` 唯一入口） |

> 依赖安装统一在 workspace 根执行：`cd hrm-dev/hrm-clients && npm install`（单一 lockfile，禁止在本目录单独 install）。

## 约定

- **端固定**：登录恒上报 `clientType=STAFF`（后端 fail-closed：缺省/未知端 → 1110 拒登）；`?as=station` 仅作**兼容读**保留（ADR §3.3 / B-4，退役条件见 ADR）。
- **路由**：hash 模式（`/staff/#/staff/home`）；`/boss/*` 路径**不存在**于本工程（编译期即断）。
- **base**：`/staff/`；与 Nginx `location /staff/`、`createWebHashHistory` 三者一致（ADR §4.6 易错点）。
- **登录态存储**：`hrm:staff:token` / `hrm:staff:user`（独立命名空间，与 hrm-demo 移动端入口可同浏览器并存，支撑「两端并存不互踢」验收）。
- **共享消费**：`@kdyzgl/tokens`（Token 真源，相对路径 `@use`）、`@kdyzgl/shared`（常量/领域纯函数/中立 UI）、`@kdyzgl/api-client`（`createHttp` 请求工厂 + `createAuthStorage`）、`@kdyzgl/mock`（仅演示态动态装配）。**不得复制包内源码**。
- **环境变量**：`.env.demo`（`VITE_MOCK_ENABLED=true`）/ `.env.production`（`VITE_MOCK_ENABLED=false`、`VITE_API_BASE=/hrm-api/v1`）；真实域名/IP/密钥绝不入库。

## 门禁基线（B3 冻结）

- `verify:mobile`：**36**（分端下限；两端合计 ≥ 48，实测合计 56）
- `build` / `build:prod`：均须通过；`build:prod` 产物**无 Mock chunk**（静态检索）
- `lint` / `lint:style` / `test`：规则集不得少于 `hrm-demo` 现状（含既有 ESLint 边界规则）
- 反模式 A06：门禁不过**严禁**改断言/改基线，须归因并登记。
