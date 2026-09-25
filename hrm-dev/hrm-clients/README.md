# hrm-clients · 多端 workspace

> B1 批次产物：按 [ADR-结构迁移-01](../docs/adr-structure-migration.md) §3.7 **B1「建 workspace + 抽共享包（行为不变）」** 建立。
> 本轮为**纯新增**：不删改 `hrm-demo` / `hrm-admin` 任何文件、不改其引用，故**不影响任何现有工程的构建与门禁**。

## 为什么建它

现状问题（ADR §1）：三端共用的 **Design Token 唯一真源**、拟作生产交付的**网页端实现主体**、以及 Mock 层，
都物理寄居在**按项目规则 §12 可被整体删除的演示工程 `hrm-demo`** 内。B1 先立中立、受门禁覆盖的共享包骨架。

## 目录

```
hrm-clients/
├─ package.json          # npm workspace（workspaces: packages/* + apps/*）+ 统一门禁入口
├─ .gitignore
├─ scripts/
│  └─ verify-mock.mjs    # 跨端 Mock 契约门禁（单点，不按端复制）
├─ packages/
│  ├─ tokens/            # @kdyzgl/tokens    Design Token 真源（中立包，零端依赖）
│  │  ├─ src/tokens.base.scss
│  │  └─ scripts/gen-element-tokens.mjs
│  ├─ shared/            # @kdyzgl/shared    常量 / 领域纯函数 / 上报工具（零 UI、零端依赖）
│  ├─ api-client/        # @kdyzgl/api-client  请求层工厂 + 登录态存储工厂 + 契约类型
│  └─ mock/              # @kdyzgl/mock      Mock 层（路由 + 引擎 + 数据层）
└─ apps/                 # 占位目录：B3/B4/B5 才落 apps/{web,staff-h5,boss-h5}
```

## 各包职责

| 包 | 职责 | 端依赖 | 关键约束 |
| - | - | - | - |
| `@kdyzgl/tokens` | Design Token **真源**（L1 原始色值 + L2 语义 + L3 通用基础）与 Element 浅色阶校验脚本 | 零 | 任何端不得再自定义真源；各端只保留合法平台差异层 |
| `@kdyzgl/shared` | 错误码/角色/字典/存储键常量、领域纯函数（时间/分页/CSV/脱敏/权限/数据范围）、设备弱信号与运行日志上报 | 零 | 纯函数、零 UI；不得 import 任何端 |
| `@kdyzgl/api-client` | `createHttp` 请求层工厂（baseURL / Bearer / `X-Client-Type` 上报 / `body.code` 分发 / 401·1108 幂等清态 / 超时策略 / GET 重试）、`createAuthStorage`、契约 JSDoc | 仅 `axios`（peer）+ shared | 端相关载体（token 读写/跳转/提示）一律注入，禁 import Element Plus·Vant |
| `@kdyzgl/mock` | Mock 引擎 + 路由 + 数据层：演示态**唯一行为规格** | 仅 axios + shared | 装配契约 `installMock(axiosInstance)` 保持不变 |

## 抽取来源对照（复制式抽取，不改原工程）

| 新位置 | 来源 | 改动 |
| - | - | - |
| `packages/tokens/src/tokens.base.scss` | `hrm-demo/src/shared/styles/tokens.base.scss` | **逐字一致**（未改一个 Token 值） |
| `packages/shared/src/{constants,domain,composables}`、`clientLog.js`、`device.js` | `hrm-demo/src/shared/` 同名目录/文件 | **逐字一致**（含 `.spec.js` 一并复制；B1 未接单测） |
| `packages/mock/src/**` | `hrm-demo/src/shared/mock/**` | 仅**跨包 import 改写**：`'../constants/*'`、`'../../constants/*'` → `'@kdyzgl/shared/constants/*'`；`'../domain/*'` → `'@kdyzgl/shared/domain/*'`（27 个文件） |
| `scripts/verify-mock.mjs` | `hrm-demo/scripts/verify-mock.mjs` | 仅 import 改写：`'../src/shared/mock/*'` → `'@kdyzgl/mock/src/*'`、常量 → `'@kdyzgl/shared/constants/*'` |
| `packages/tokens/scripts/gen-element-tokens.mjs` | `hrm-demo/scripts/gen-element-tokens.mjs` | 扫描目标由写死改为 `--targets` 注入；`BASE_FILE` 指向本包真源 |

## 门禁

```bash
npm install            # 建立 workspace 依赖树
npm run verify:mock    # 跨端 Mock 契约校验（基线须 ≥ 冻结值）
npm run verify:tokens  # Token 真源 + 平台层逐值校验
```

> B1 桥接说明：`verify:tokens` 的 `--targets` 当前仍指向 `hrm-demo/src/pc/styles/tokens.scss`（平台层迁移前的位置），
> 用于证明**抽出的真源与现网口径逐值一致**；B2 改为多目标（各端 + `hrm-admin`）并打印全部扫描路径。
> `verify:mobile` / `build` / `lint` 等**按端**门禁在 B3/B4/B5 随各 app 落地，B1 不预先创建（`TODO(扩展)`）。

## 回滚

删除本目录（`hrm-dev/hrm-clients/`）即完全回滚；未触碰任何既有工程与现网，故**线上零影响**。
一律用「删除新增件 / 反向 diff」，**禁** `reset --hard` / `clean -f` / `push --force`（D 档）。
