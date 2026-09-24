# 快递驿站智汇系统 · 三端服务拆分与登录体系改造设计

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0（设计稿，待评审） |
| 编写日期 | 2026-09-24 |
| 作者 | 架构师 `express-station-architect` |
| 状态 | **设计定稿待评审**；涉安全面条目须先经网络安全工程师评估（调度规则 P0.5 / L7） |
| 范围 | ① 三端服务请求拆分 ② 服务器拆分 ③ 登录体系改造（契约级）④ 分批实施计划 ⑤ 待裁定清单 |
| 关联文档 | [api.md](api.md)、[db.md](db.md)、[server-architecture.md](server-architecture.md)、[plan.md](plan.md)、[deploy.md](deploy.md)、[wecom-integration-adr.md](wecom-integration-adr.md) |
| 本轮边界 | **只做设计，不写业务实现代码**；不改 `hrm-server`/`hrm-demo`/`hrm-admin` 源码；不改既有契约文档与迁移脚本；**不执行任何迁移（C 档）** |

> **安全声明（P0.5）：** 本文涉及鉴权、设备信任、短信通道、会话、外部暴露面的设计，**均为「设计方案输入」而非安全结论**。落地前须由**网络安全工程师**按 L7 出具技术评估（威胁建模 + 发现项 + 复现 + 修复建议），主智能体方可授权实现。清单见 [§7](#7-须经网络安全工程师评估清单p05)。
>
> **凭据声明：** 本文不记录任何真实凭据、服务器 IP、AppKey/Secret；所有配置仅写**键名**与 `change_me_*` 占位符。现网域名/IP/口令见服务器外置配置与运维记录，不进本文。

---

## 0. 摘要（结论先行）

| # | 议题 | 结论 |
| - | ---- | ---- |
| 1 | **API 命名空间** | **保持角色驱动**（同一路径 + `RoleEnum` + 数据范围收敛），**不做按端路径分区**；仅新增**可选** `X-Client-Type` 头用于审计/限流/会话维度。按端分区将牵动 **约 300+ 处路径引用、跨 3 工程 5 文档、回归面=全量**，收益不抵成本（详见 §1.1）。 |
| 2 | **客户端共享层** | 新增中立 workspace 包 `hrm-dev/shared/api-client`（零 UI 依赖，`peerDependencies: axios`），三端以 `file:` 引用；**禁止反向依赖 `hrm-admin`/`hrm-demo`**，满足 §12.1「Demo 不得改 `hrm-admin`」。 |
| 3 | **会话多端化（关键障碍）** | 由「`hrm:session:{employeeId}` 单 key 全局互踢」改为「**`hrm:session:{sid}` 多会话 + `hrm:session:idx:{employeeId}` 员工索引**」；互踢粒度由「账号」降为「**端 + 设备**」（同设备重登覆盖、跨端/跨设备并存）。改动集中 `SessionUtil`/`JwtAuthFilter`/`SessionInfo`。 |
| 4 | **部署拓扑** | **模块化单体（沿用现单体）+ 单后端进程**；**否决微服务**（团队规模/3.8G 单机/运维成本）。 |
| 5 | **与现网 `courier-server` 共存** | 现网容器已占 **127.0.0.1:8080**（`courier-app`）、**80/443**（`courier-nginx`）、**6379**（`courier-redis`）。推荐 **候选 A（独立子域 + 独立端口，隔离度最高）**，回退 **候选 B（同 Nginx 不同路径前缀）**（详见 §2.2）。**`hrm-server` 不得再计划占用 8080**。 |
| 6 | **登录体系** | 双通道（密码 / 短信）；密码登录携带**设备指纹**，新设备触发短信二次验证；会话 **3 天双控**（JWT `exp` + Redis TTL）；到期路径（强制重登 vs 短信续期）**待用户裁定**。 |
| 7 | **外部适配** | `SmsSender` / `GeoService` **端口 + 生产实现 + 降级实现**三件套；未配置 Key 时降级（**验证码绝不入日志**）；Key 由主智能体托管，落服务器外置配置。 |

---

## 1. 现状事实核对（设计前置）

| # | 事实 | 来源（已核对） | 结论 |
| - | ---- | ---- | ---- |
| F1 | 单体应用，端口 8080，包名 `com.qiujie` | `hrm-server/pom.xml`、`application.yml`（server-architecture §5.1） | 确认 |
| F2 | 145 接口（M0 既有 24 + P1~P10 121），路径全部 `/api/v1/*` | `server-architecture.md` §6.3 全量映射（第 592–781 行） | 确认；**路径已冻结**，Mock 为唯一行为规格 |
| F3 | 三角色 `ADMIN/STATION_ADMIN/STAFF`，`@RequireRoles` fail-closed | [RoleEnum.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/RoleEnum.java)、[RequireRolesInterceptor](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java) | 确认 |
| F4 | **单会话互踢**：`hrm:session:{employeeId}` 单 key，`save()` 覆盖旧值 | [SessionUtil.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/SessionUtil.java) | 确认；**三端并存关键障碍** |
| F5 | `JwtAuthFilter` 用 `userId` 查会话 + 比对 `jti` | [JwtAuthFilter.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java) L86–96 | 确认 |
| F6 | `SessionInfo`/`LoginUser` 全字段 String，含 `stationId`（三态语义） | [SessionInfo.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/SessionInfo.java)、[LoginUser.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/LoginUser.java) | 确认 |
| F7 | 公开端点仅 2 条：`POST /api/v1/auth/login`、`POST /api/v1/work-orders/auto-dispatch` | [PublicEndpoints.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java) | 确认 |
| F8 | JWT 有效期 `/jwt.expire` 默认 86400s（**1 天**，与「3 天」不符需改） | [JwtUtil.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/JwtUtil.java) | 确认；**改造点** |
| F9 | 37 表 / 迁移 `V1`–`V13`（V1/V2 永不修改） | `db/migration/mysql/*.sql` 实测 `CREATE TABLE` 37 个、`db.md` §9.3 | 确认 |
| F10 | 错误码段：通用 / 10xx 认证 / 20xx 员工 / 30xx 部门 / 40xx 驿站 / 50xx 导入 / 60xx~96xx 领域 | [ErrorCode.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java) | 确认；**10xx 仅用到 1004**，短信/设备码建议新开 **11xx** |
| F11 | `hrm-demo` 单工程三入口（`index/ppc/mobile.html`），`base` 全局不可按入口区分，`file://` 与 history 路由硬冲突 | [vite.config.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/vite.config.js) L135–154 | 确认；**H5 壳须走远程 https 而非离线包** |
| F12 | 现网 `courier-server` 为独立 Docker 栈；`courier-app` 占 `127.0.0.1:8080`；`courier-mysql` 已停用（改连宿主 MySQL 3307）；`courier-redis` 占 `127.0.0.1:6379` | [demo-docker-deploy.md](demo-docker-deploy.md)、[deploy.md](deploy.md) §0.2/0.3 | 确认；**端口与现网硬冲突，见 §2.2/§2.4** |
| F13 | 宿主 MySQL 3307（`datadir=/data/mysql-host`），`/data` 50G 可用约 46G | server-architecture §1.4.8、[deploy.md](deploy.md) | 确认 |
| F14 | 现有围栏用 Haversine；定位校验码 `9104` | `HaversineCalculatorTest`、[ErrorCode.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java) L128 | 确认；**Amap 降级到 Haversine 有现成落点** |

**事实性纠正（对任务背景材料的补充）：**
1. 任务称「现有 145 接口」，而 `api.md` 头部仍写「接口总数 24」——**api.md 未随 P1~P10 更新**；145 条的唯一权威映射是 `server-architecture.md` §6.3。本文引用接口号一律以 §6.3 为准。
2. 任务称「后端端口 8080」——**现网 8080 已被 `courier-app` 占用**；`hrm-server` 生产端口须重新分配（见 §2.2），本文按 `8081` 占位。
3. 任务称「Redis 单实例」，未提现网 `courier-redis` 已占 6379——**直连 6379 会与现网串库**（见 §2.4）。

---

## 2. 第 1 部分：三端「服务请求拆分」

### 2.1 API 命名空间策略

#### 2.1.1 候选与对比

| 维度 | 方案甲（推荐）**角色驱动**，路径不变 | 方案乙 **按端分区**（`/api/v1/staff|boss|web/*`） | 方案丙 **版本并行**（`/api/v2/{端}/*` 新路径、旧路径冻结保留） |
| ---- | ---- | ---- | ---- |
| 形态 | 同一 `/{domain}` 路径，服务端按 `RoleEnum` + `@RequireRoles` + 数据范围三层收敛 | 端前缀分区，各端独立路径 | 旧 145 冻结点，新端走 v2 |
| 已实现 145 接口改动量 | **0** | **高（见量化）** | 中（旧 145 不动，但新增分支与双份维护） |
| 契约一致性 | 单份契约（§6.3） | 需重排契约表 | 双份契约长期漂移 |
| 缓存/限流粒度 | 按端靠 `X-Client-Type` 头，不靠路径 | 天然按端 | 混合 |
| 多端并存可解释性 | 中（需看角色+头） | 高（看路径） | 中 |
| 后续扩展成本 | 低（新增端零契约改动） | **每加一端复制一套路径** | 高（两套长期并存） |
| 与 `api.md` 决策 D9（同源反代） | 兼容 | 兼容 | 兼容 |

#### 2.1.2 改动成本量化（方案乙）

| 层 | 受影响物 | 数量级 | 风险 |
| - | ---- | ---- | ---- |
| 后端 | 145 个 Controller 方法的 `@RequestMapping` 路径 | **145 处** | 高 |
| 后端 | `PublicEndpoints` 白名单常量、`JwtAuthFilter` 路径匹配 | 3 处 | 高 |
| 后端 | 路由顺序回归 + 白名单测试（`RouteOrderRegressionTest`、`PublicEndpointsTest`） | 2 类，断言全量重写 | **高（回归面=全量）** |
| 前端 | `hrm-demo` Mock 145 路由定义 + 请求路径常量 | ~150 处 | 高 |
| 前端 | `hrm-admin` 24 接口封装 + dev proxy | ~25 处 | 中 |
| 文档 | `api.md`、`server-architecture.md` §6.3（145 行映射逐行） | 2 文档，**145+ 行** | 中 |
| 测试 | `test-cases.md` 全量路径断言 | 全量 | 高 |
| **合计** | — | **约 300+ 处路径引用，跨 3 工程 5 文档，回归=全量** | **高** |

#### 2.1.3 结论

**采纳方案甲（角色驱动）**，理由：
1. 可见范围问题**已在 P0 地基解决**（`@RequireRoles` fail-closed + 数据范围三层 + `ResourceAccessChecker`），端差异属「同一资源、不同角色可见子集」，**不应编码进路径**；
2. 145 路径被 Mock 定为唯一行为规格，按端分区会把「行为规格」与「端」耦合，违反契约单一真源（P5 契约先行）；
3. 需要端维度时，用**可选请求头 `X-Client-Type: staff|boss|web|admin`**（缺失默认 `web`）承载审计/限流/会话/埋点，**不改任何路径**。

> `TODO(扩展): 若后续某端出现「服务端可判定的独占端点」（如老板端专属报表），用角色而非路径表达（新增 `@RequireRoles("BOSS")`），仍不动 URL。`

### 2.2 各端 API 客户端层组织

#### 2.2.1 约束

- §12.1：Demo 工程**不得改动** `hrm-admin`/`hrm-server`。
- 三端**独立代码**，共用一个后端。
- 共享层要「复用不复制」，但**不得反向依赖被保护工程**。

#### 2.2.2 候选

| 方案 | 形态 | 优点 | 缺点 |
| ---- | ---- | ---- | ---- |
| A（推荐）**中立 workspace 包** | 新增 `hrm-dev/shared/api-client`，纯 TS，`peerDependencies: axios`；三端 `file:` 引用 | 单一真源、可版本化、零 UI 依赖、不触 `hrm-admin` | 需引入 workspace 约定（仓库根目前无 lockfile，见 F11 注） |
| B Git subtree/submodule | 把共享目录嵌入各端 | 无包管理依赖 | 同步易漂移、子模块运维成本高 |
| C 各端自带薄封装 | 每端复制 | 最简单 | 违反「复用」原则；4 端 = 4 份漂移 |

#### 2.2.3 推荐目录结构

```text
hrm-dev/
├── shared/
│   └── api-client/                 # @kdyzgl/api-client（新增，中立）
│       ├── src/
│       │   ├── http/               # axios 实例工厂、拦截器（401/403/错误码映射）
│       │   ├── contracts/          # 从 api.md 手写的请求/响应 TS 类型（按域分文件）
│       │   ├── error-codes.ts      # 与 ErrorCode.java 文案同源（对照生成/人工同步）
│       │   └── index.ts
│       └── package.json            # 无 vue/element-plus/vant 依赖
├── hrm-admin/                      # 引用 @kdyzgl/api-client（后续增量接入，不强制）
├── hrm-demo/                       # 演示工程，Mock/真实切换；只只读引用 @admin
├── hrm-app-staff/                  # 员工端「驿站助手」（规划新增）
├── hrm-app-boss/                   # 老板端（规划新增）
└── hrm-web/                        # 网页端（规划新增）
```

- **复用策略**：`request()` 工厂 + 拦截器 + 错误码映射 + 类型定义共享；各端仅注入自己的 `baseURL`、`X-Client-Type`、token 存取实现。
- **不反向依赖**：`shared/api-client` **不得** import `hrm-admin`/`hrm-demo` 任何代码（保持中立，满足 §12.1）。
- `hrm-admin` 现状 `dev proxy → 127.0.0.1:8080`（见任务背景），端口调整后需同步（见 §2.2/§3.5）。

> `TODO(扩展): 待后端引入 springdoc-openapi 后，contracts/ 可由 OpenAPI 自动生成，消除手写漂移；届时以 api.md 为人工校验基线。`

### 2.3 认证多端共存

#### 2.3.1 问题：单会话互踢

现状 `SessionUtil.save()` 对 `hrm:session:{employeeId}` **覆盖写**，`JwtAuthFilter` 比对单 `jti` → 同一账号任一时刻**只有一个有效会话**。三端并存会互相顶下线。

#### 2.3.2 方案：会话按「端 + 设备」维度持有

**Redis 键设计（新增，旧键保留兼容）：**

| 键 | 类型 | 值 | TTL | 用途 |
| - | - | - | - | - |
| `hrm:session:{sid}` | String(JSON) | `SessionInfo`（**新增** `employeeId`/`clientType`/`deviceId`/`sid`） | 3 天 | 会话主体（主键改为 `sid`） |
| `hrm:session:idx:{employeeId}` | Set | 该员工所有活跃 `sid` | 3 天（每次续期刷新） | 设备管理 / 强制下线 / 登出全部 |
| `hrm:session:{employeeId}`（旧） | String(JSON) | 旧结构 | 旧 TTL | **兼容期只读**，新登不再写 |

- `sid` 取值 = `jti`（沿用，Token 内已有 `jti`，零新增 claim 即可落地第一版）。
- **互踢粒度变更**：新登录只覆盖「**同 `employeeId` + 同 `clientType` + 同 `deviceId`**」的旧 `sid`；不同端 / 不同设备**并存**。
- **强制下线**（禁用 / 删除 / 重置密码 / 改密）→ 遍历 `hrm:session:idx:{employeeId}` 删除全部 `sid`（跨端全失效），并删除索引。

**Token 设计：** 沿用 `jti` 作为 `sid`；`JwtAuthFilter` 改为：
1. 解析 `jti` → 先查 `hrm:session:{jti}`；
2. 命中则校验（可选）会话内 `jti`/`sid` 一致 → 注入 `LoginUser`（新增 `clientType`/`deviceId`）；
3. 未命中 → **回退**旧逻辑 `hrm:session:{userId}`（兼容期旧 Token，不改前端即可平滑）；
4. 若后续要防「jti 被重放」，改为按 `(employeeId, clientType, deviceId)` 组合键，并允许 `TODO(扩展): 双 Token（access + refresh）`。

**同一账号三端同时登录：** **允许**。员工端 1 设备 1 会话、老板端 1 设备 1 会话、网页端 1 浏览器 1 会话，互不顶替。

**token 传递 / 刷新 / 吊销：**

| 动作 | 策略 |
| - | - |
| 传递 | `Authorization: Bearer {token}`（不变） |
| 刷新 | **默认不滑动续期**：`exp` 固定，到期须重认证（与「每 3 天短信验证一次」口径一致）。是否允许滑动续期 → 待裁定（§6） |
| 吊销（单会话） | 登出：删 `hrm:session:{sid}` + 从索引移除 |
| 吊销（全会话） | 改密/禁用/删除/重置密码：按索引删全部 |
| 令牌过期 | Redis TTL 与 JWT `exp` 双控（见 §3.3） |

#### 2.3.3 对现有类的改动点

| 类 | 改动 | 风险 |
| - | - | - |
| `util/SessionUtil` | 新增 `saveBySid/getBySid/deleteBySid/listSids/deleteAllOfEmployee`；旧方法保留兼容 | 中 |
| `filter/JwtAuthFilter` | 解析顺序改为「先 sid 后 userId 回退」；注入新增字段 | 高（认证主链路） |
| `common/SessionInfo` | 新增 `employeeId`/`clientType`/`deviceId`/`sid`（**全 String**，沿用类头约定，避免 JSON 序号兼容问题） | 中 |
| `common/LoginUser` | 新增 `clientType`/`deviceId`（供限流/审计/设备管理使用） | 低 |
| `config/RedisConfig` | 无需改（序列化不变）；仅键规范新增 | 低 |
| `service/auth/impl/AuthServiceImpl` | `login()` 写会话改为按端+设备；`logout()` 按 sid 删 | 中 |

> **兼容期风险：** 旧 Token 无 sid，靠 `userId` 回退；上线后旧 Token 自然过期即退出回退分支。**灰度/回滚**：保留旧键读写路径，回滚只需还原 `JwtAuthFilter` 分支。

### 2.4 各端接口可见范围（逐端清单）

> 接口号引用 `server-architecture.md` §6.3（1–145）。roles 列沿用 §6.3。端清单是**产品可见范围建议**，最终以「角色 + 数据范围」收敛为准（服务端权威）。

#### 2.4.1 员工端「驿站助手」（安卓壳 + H5，主力 STAFF）

| 接口号 | 方法 路径 | 说明 |
| - | ---- | ---- |
| 1–4 | `/auth/*` | 登录/登出/me/改密（改造后含设备与会话） |
| 25 | `POST /system/client-logs` | 前端日志上报（本人） |
| 28、29、30、31、33 | `/notifications*` | 本人通知；**不含 32 发布** |
| 34、37、42、43、45、47、49、52 | `/attendance/*`、`/schedules/my`、`/shifts` | 本人考勤：规则、状态、我的记录、我的补卡、补卡申请、打卡、我的排班、班次（静默读） |
| 64 | `GET /kpi/scores/{employeeId}` | 本人 KPI（403 收敛他人） |
| 66、69 | `GET /hr/profiles|salary-structures/{employeeId}` | 本人档案/薪资（403 收敛） |
| 86、94、95 | `/finance/payrolls/my`、`{id}/confirm`、`{id}/objection` | 我的工资单、确认、异议 |
| 96–98、102–105 | `/leave*` | 提交/预览/我的/详情/改/撤回/重提（**不含审批类 106/107/108**） |
| 109、110、114、115、116、117 | `/work-orders*` | 我的工单：列表/创建/详情/指派/改状态/转单（**不含 111/112 派单规则**） |
| 140–145 | `/parcels*` | 包裹查询/概览/趋势/排行/详情/取件 |
| 101/100 | — | **不含**请假设置（ADMIN） |

#### 2.4.2 老板端（安卓壳 + H5，ADMIN / STATION_ADMIN 经营视角）

| 接口号 | 方法 路径 | 说明 |
| - | ---- | ---- |
| 1–4 | `/auth/*` | 同上（改造后） |
| 5 | `/dashboard/summary` | 看板（ADMIN） |
| 56–63 | `/kpi/*` | 指标配置 + 评分 + 排名（管理侧；64 亦可） |
| 65–80 | `/hr/*` | 人事档案/定薪/入离职流程（ADMIN） |
| 81–93 | `/finance/*` | 计薪规则/工资单生成·提交·发布·审批（86/94/95 本人亦可） |
| 109–117 | `/work-orders*` | 工单全量（含 111/112 派单规则） |
| 118–139 | `/sync*` | 同步任务与配置中心（含配置中心增删改，ADMIN） |
| 34–55 | `/attendance/*`、`/schedules*`、`/shifts*` | 考勤管理侧（38–41、44、46、48、50、51、53–55） |
| 96–108 | `/leave*` | 请假审批链（含 106 站审/107 终审/108 撤回） |
| 140–145 | `/parcels*` | 经营侧包裹视图 |
| 6–24 | `/employees|departments|stations` | 仅当老板端承担组织管理时（ADMIN）；否则移入网页端/PC 端 |

#### 2.4.3 网页端（纯网页）

- **全量可览**（等同 `hrm-admin` 能力面）：24、全部领域接口；按登录角色收敛。
- 定位：桌面办公场景的「管理总台」，覆盖 §2.4.2 + 组织管理（6–24）+ 通知发布（32）+ 日志查询/清理（26/27）+ 请假设置（100/101）。

#### 2.4.4 PC 管理端（`hrm-admin`，现状）

- **维持现状**：当前消费既有 24 接口，新增认证契约落地后**按需增量接入**（改造不改其现有页面契约）。**本轮零改动**。

> **端 → 接口矩阵汇总（勾选表）**

| 域 | 员工端 | 老板端 | 网页端 | PC 端 |
| - | - | - | - | - |
| auth 1–4 | ✅ | ✅ | ✅ | ✅ |
| employee/dept/station 6–24 | ❌ | 视需要 | ✅ | ✅ |
| dashboard 5 | ❌ | ✅ | ✅ | ✅ |
| systemlog 25–27 | 仅 25 | ❌ | ✅ | ✅ |
| notification 28–33 | 28–31,33 | 28–31,33 | 全量 | 全量 |
| attendance 34–55 | 本人子集 | 管理侧 | 全量 | 全量 |
| kpi 56–64 | 64 | 56–63 | 全量 | 全量 |
| hr 65–80 | 66,69 | 65–80 | 全量 | 全量 |
| finance 81–95 | 86,94,95 | 81–95 | 全量 | 全量 |
| leave 96–108 | 96–98,102–105 | 96–108 | 全量 | 全量 |
| workorder 109–117 | 109,110,114–117 | 109–117 | 全量 | 全量 |
| sync 118–139 | ❌ | 全量 | 全量 | 全量 |
| parcel 140–145 | ✅ | ✅ | ✅ | ✅ |

> 说明：以上为**产品可见范围建议**，服务端最终以 `@RequireRoles` + 数据范围三层为准；员工端不存在独立端点，故**无需改后端路径**（呼应 §2.1 结论）。

---

## 3. 第 2 部分：服务器拆分方案

### 3.1 部署拓扑：单体 vs 模块化单体 vs 微服务

| 维度 | 单体（现） | **模块化单体（推荐）** | 微服务 |
| - | - | - | - |
| 团队规模适配 | 够用 | **贴合（1 主 + 9 角色，小体量）** | 过重 |
| 迭代频率 | 中 | 中 | 高才划算 |
| 运维成本 | 低 | **低（仍单进程单库）** | 高（注册/网关/链路追踪） |
| 3.8G 单机内存 | 可 | **可（单 JVM + 单 MySQL + Redis）** | 不可（多 JVM 竞争） |
| 隔离/独立扩缩 | 弱 | 弱（可后续按模块拆） | 强 |
| 结论 | 保留 | **采纳：保留单进程，按域分模块（已在 P0~P10 按域分包）** | **否决（过度设计，反模式）** |

**结论：** 维持**单后端进程 + 单 MySQL（3307）+ Redis**；域边界已由 `controller/{domain}`、`service/{domain}` 与 `server-architecture` §2.2 DAG 表达。**三端共用同一后端服务器**（用户口径），无需为端拆服务。真正的「拆分」诉求在**前端工程**与**静态资源发布**，非后端进程。

> ADR：见 §8 ADR-MC-01（架构风格）、ADR-MC-02（命名空间策略）。

### 3.2 Nginx 路由与域名/端口划分（含与 courier-server 共存）

#### 3.2.1 现网占用（F12，硬约束）

| 资源 | 现网占用 | 与 hrm-server 冲突 |
| - | ---- | - |
| `127.0.0.1:8080` | `courier-app`（Docker） | **冲突**：hrm-server 不能再计划 8080 |
| `:80 / :443` | `courier-nginx`（Docker） | 需复用或另起入口 |
| `127.0.0.1:6379` | `courier-redis`（Docker） | **冲突**：直连会串库 |
| 宿主 MySQL 3307 | 现网生产库 `courier_station` | **并存**：hrm 用新库 `kdyzgl`（server-architecture §1.4.8 已定） |
| 现网主域 | 由 `courier-nginx` 承载（证书） | 需子域或路径区分 |

#### 3.2.2 共存候选方案

| 候选 | 形态 | 优点 | 缺点 | 风险 |
| - | ---- | ---- | ---- | ---- |
| **A（推荐）** 独立子域 + 独立端口 | `hrm-server` 起在 `127.0.0.1:8081`；新子域（如 `hrm.<现网主域>`）A 记录指向本机；由 Nginx（任一）承载该 `server_name` 块，`/api/` 反代 `127.0.0.1:8081` | 隔离度最高；不触碰现网 `location`；回滚=摘子域 | 需加 DNS A 记录；若 80/443 仍由 `courier-nginx` 承载，需在**其**加 `server` 块（触碰现网配置）或另起监听端口的 Nginx | 中（改现网 Nginx 属 C 档） |
| **B** 同 Nginx 不同路径前缀 | 在现有 `courier-nginx` 加 `location /hrm/` 与 `location /hrm-api/` 反代 8081 | 无需 DNS；复用现有证书 | 改现网 Nginx；路径前缀与 `hrm-web` 的 SPA `base` 耦合；与其他应用共享 `server_name` 易踩 `location` 顺序 | 中高（触碰现网配置 + SPA base 约束） |
| C 独立 Nginx（新端口） | 另起 Nginx 监听非标端口（如 8080+8081 组合） | 完全不碰现网 | 非标端口对外不友好；需放行端口（安全面） | 高（安全组变更 C 档） |

**推荐：候选 A**（子域隔离）；若短期内无法加 DNS A 记录，**回退候选 B**。**候选选定属待裁定项（§6）**，且「改现网 Nginx」属 **C 档**，须主智能体按 §10.3 三步授权 + 先取安全结论。

#### 3.2.3 端口与路由规划（推荐态）

| 项 | 建议值 | 说明 |
| - | ---- | ---- |
| hrm-server 端口 | `8081`（占位，**不得用 8080**） | 仅 `127.0.0.1` 监听，**禁止公网放行**（对齐 deploy.md §0.3） |
| API 反代 | `/api/ → http://127.0.0.1:8081` | 保留 `X-Real-IP`/`X-Forwarded-For` 透传（登录日志 IP；deploy.md §5.3） |
| 员工端 | `https://<子域>/staff/` | SPA，`try_files … /staff/index.html` |
| 老板端 | `https://<子域>/boss/` | 同上 |
| 网页端 | `https://<子域>/` | 或独立子域 |
| PC 管理端 | `https://<子域>/admin/` | 现状 `hrm-admin` 站点根，增量迁移 |
| MySQL | `127.0.0.1:3307`（宿主） | 库 `kdyzgl`；账号 `hrm_app` 仅授权 `kdyzgl`、不授 DROP |
| Redis | **独立实例端口（占位 `6380`）** 或复用 6379 的独立 `database` | 见 §3.4 建议 |

> 上述路径为**规划占位**，最终以候选裁定 + 运维手册为准。**本文不落真实域名/IP**（见 deploy.md）。

### 3.3 静态资源与 H5 壳

#### 3.3.1 发布与缓存

| 端 | 产物 | 站点根 | 缓存策略 |
| - | ---- | - | - |
| 员工端 | `hrm-app-staff/dist` | `/www/wwwroot/hrm-app-staff` | `index.html`：`no-cache`；`assets/*`（带 hash）：`max-age=31536000, immutable` |
| 老板端 | `hrm-app-boss/dist` | `/www/wwwroot/hrm-app-boss` | 同上 |
| 网页端 | `hrm-web/dist` | `/www/wwwroot/hrm-web` | 同上 |
| PC 端 | `hrm-admin/dist` | `/www/wwwroot/hrm-admin` | 同上；`.map` **禁止公开下载**（deploy/demo 已约定） |
| 后端 | jar | `/www/wwwroot/hrm-server`（**非站点目录**） | 不对外 |

- SPA 回退：`try_files $uri $uri/ /{端前缀}/index.html`（对齐 deploy.md §5.3）。
- 多端并存时用**各自子路径/子域**隔离 `base`，避免 `assets` 互相覆盖。

#### 3.3.2 安卓 H5 壳注意点

| 要点 | 结论 | 依据 |
| - | ---- | ---- |
| `base` | 用**子路径 base**（如 `/staff/`）或子域根 `/`；**禁用 `./`（相对 base）**，否则 history 深链刷新 404 | F11；history 路由要求绝对 base |
| `file://` 与 history 冲突 | **走远程 https 加载**，不做离线 `file://` 包；`file://` 下 ESM + history 均不可靠，且 `base` 是全局配置无法按入口区分 | F11（vite.config 注释） |
| WebView 缓存 | `LOAD_DEFAULT`（尊重 http 缓存）；`index.html` no-cache 保证版本更新即时；必要时以版本查询参数强制刷新 | 工程约定 |
| 热更新 | **靠 Web 端发布**（H5 加载远程），壳内不打包业务前端 → 业务迭代免发版；只有壳能力变更才发 App | 用户口径「安卓壳 + H5」 |
| 混合内容 | WebView 必须加载 `https`，禁用 `http`（Android 9+ 默认拦截明文） | 安全基线 |
| 离线兜底 | `TODO(扩展): 断网/弱网提示页（不缓存业务数据）` | — |

### 3.4 数据与外部依赖

#### 3.4.1 MySQL（单库 `kdyzgl`，宿主 3307）

| 项 | 评估 |
| - | ---- |
| 容量 | 37 表；`parcel` 20 万行 ≈ 106MB（server-architecture §1.4.8）；`/data` 可用约 46G → **充裕（<0.5%）** |
| 连接数 | 单实例 HikariCP 默认池 10；三端并发为「员工端打卡 + 查询」量级，**单实例足够**；上限受 `max_connections`（默认 151）约束，前端无需多池 |
| 与现网 | 库隔离（`kdyzgl` vs `courier_station`）+ 账号隔离（`hrm_app` 仅授权 `kdyzgl`）；**同实例不同库**，互不影响（server-architecture §1.4.8） |
| 新增表 | 设备信任（§4.2）、短信审计（可选）将新增 1–2 表 → 仍属容量无关紧要 |

#### 3.4.2 Redis

| 项 | 评估 |
| - | ---- |
| 单实例是否够用 | **够用**（会话 + 短信验证码 + 频控，键数量级 = 在线用户数 × 端数 + 手机号维度，内存 MB 级） |
| 与现网冲突 | **6379 已被 `courier-redis` 占用**；直连会与现网共用实例 |
| **建议** | **独立 Redis 实例（占位 `6380`）**，物理隔离现网；次选：复用 6379 但使用**独立 `database`（如 `database: 1`）** + `hrm:` 键前缀（隔离性弱，不推荐用于会话） |
| 键前缀 | 统一 `hrm:`（现有），短信用 `hrm:sms:*`，会话用 `hrm:session:*` |
| 持久化 | 会话可容忍丢失（丢失=用户重登）；是否开 AOF/RDB 由运维定，非本文裁定 |

> **风险提示：** 若复用现网 `courier-redis`，须经安全评估（跨应用数据隔离、`FLUSHDB` 误操作面）。

### 3.5 环境划分与凭据落点

| 环境 | 配置载体 | 凭据落点 | 说明 |
| - | ---- | ---- | ---- |
| 本地 | `application-local.yml`（可入库，仅占位） | `.secrets/`（**不入库**，`.gitignore`） | 本机无 JDK/Maven（plan.md §2.3），联调靠 Mock 或远端 |
| 联调 | `application-dev.yml`（外置） | 服务器外置 `/www/wwwroot/hrm-server/.secrets/` | Nginx 反代同源 |
| 生产 | `application-prod.yml`（**服务器外置，600 权限，绝不入仓库**） | 同上 | 对齐 server-architecture §5.2 |

**新增配置命名空间（键名，值占位）：**

```yaml
hrm:
  sms:
    provider: aliyun            # aliyun | none(降级)
    aliyun:
      access-key-id: change_me_sms_access_key_id
      access-key-secret: change_me_sms_access_key_secret
      sign-name: change_me_sms_sign_name
      template-code-login: change_me_sms_tpl_login
      template-code-device: change_me_sms_tpl_device
      template-code-reauth: change_me_sms_tpl_reauth
    code-ttl-seconds: 300
    send-interval-seconds: 60
    daily-limit-per-phone: 10
    max-verify-attempts: 5
  geo:
    provider: amap               # amap | haversine(降级)
    amap:
      key: change_me_geo_amap_key
      security-code: change_me_geo_amap_security_code
    fence-radius-meters: 200     # 兜底默认，实际以 attendance_rule 为准
  auth:
    session-ttl-seconds: 259200  # 3 天
    device-trust-enabled: true
    periodic-reauth-seconds: 259200
    captcha-enabled: false       # 待裁定（§6）
jwt:
  expire: 259200                 # 由 86400 改为 3 天（F8）
```

- **凭据归主智能体托管**：`change_me_*` 仅为键位占位，真实值经主智能体按最小必要下发，落服务器外置配置，**永不入库/进日志/进对话**。
- 阿里云短信 / 高德 Key 均落 `hrm.sms.aliyun.*` / `hrm.geo.amap.*`；未配置时走降级实现（§4.4/§4.5）。

---

## 4. 第 3 部分：登录体系改造设计（契约级）

> 本章为**契约级设计**（接口/表/状态机/端口），**不含实现代码**。所有安全面条目须经网络安全工程师评估（§7）。

### 4.1 接口清单（新增 / 改造）

#### 4.1.1 错误码段建议（新增 **11xx 认证增强**，与 10xx 不重叠）

| code | message（建议） | 语义 |
| - | ---- | ---- |
| 1101 | 短信发送过于频繁，请稍后再试 | 频控触发 |
| 1102 | 验证码错误或已过期 | 校验失败/超时 |
| 1103 | 验证码尝试次数过多，请重新获取 | 尝试上限 |
| 1104 | 该设备为未信任设备，需短信验证 | **业务分流码**（非错误，前端据此进入二次验证） |
| 1105 | 短信服务暂不可用，请稍后重试 | 通道异常/降级失败 |
| 1106 | 图形验证码错误或已失效 | 防刷校验失败 |
| 1107 | 设备已被撤销，请重新登录 | 已撤销设备 |
| 1108 | 登录态已到期，需重新验证 | 周期重认证到期 |
| 1109 | 该账号未绑定手机号，无法短信验证 | 无手机号 |

> `TODO(扩展): 1104 亦可设计为 HTTP 200 + data.needDeviceVerify=true 的**正常响应**（不带错误码），由契约评审在 api.md 定稿时二选一。本文倾向「正常响应 + 字段」，错误码保留给真失败。`

#### 4.1.2 接口表

| # | 方法 路径 | 端点性质 | 入参（要点） | 出参（要点） | 错误码段 |
| - | ---- | ---- | ---- | ---- | ---- |
| A1 | `POST /api/v1/auth/sms/send` | **公开**（新增白名单） | `phone`、`scene`(LOGIN/DEVICE_VERIFY/PERIODIC_REAUTH)、`deviceId`、`captchaTicket`+`captchaCode`(captcha 开启时必填) | `{ sent, expireIn, nextAllowedIn, requireCaptcha }` | 1101/1105/1106/1109 |
| A2 | `POST /api/v1/auth/sms/login` | **公开**（新增白名单） | `phone`、`code`、`device{...}` | `LoginVO` + `{ sessionExpireAt, deviceTrusted:true }` | 1001/1002/1102/1103/1107 |
| B1 | `POST /api/v1/auth/login`（**改造**） | 公开（已有） | 原 `username/password` **+** `device{...}` | 原 `LoginVO` **+** `{ deviceTrusted, needDeviceVerify, twoFactorTicket? , sessionExpireAt }` | 1001/1002/1102/1104 |
| B2 | `POST /api/v1/auth/device/verify` | **公开**（新增白名单） | `twoFactorTicket`、`phone`、`code` | `LoginVO`（同 A2） | 1102/1103/1107 |
| B3 | `POST /api/v1/auth/session/renew` | 需登录（可选公开+短信） | `phone`、`code`、`device{...}` | 新 `token` + `{ expiresIn, sessionExpireAt }` | 1102/1103/1108 |
| B4 | `POST /api/v1/auth/logout`（**改造**） | 需登录 | `all`(可选，默认 false) | `{}` | 401 |
| C1 | `GET /api/v1/auth/devices` | 需登录 | — | `[{ deviceId, platform, model, lastIp(脱敏), lastSeenTime, current }]` | 401 |
| C2 | `DELETE /api/v1/auth/devices/{deviceId}` | 需登录 | — | `{}` | 401/1107 |
| D1 | `GET /api/v1/auth/captcha`（**可选**） | **公开**（captcha 开启时） | — | `{ ticket, imageBase64, expireIn }` | 1106 |

**公开端点白名单新增**（`PublicEndpoints`）：A1、A2、B2、D1（B1 已在）。**每条新增公开端点均须经安全评估**（暴力/刷量/枚举攻击面）。

#### 4.1.3 设备信息采集字段（前端采集项清单）

| 字段 | 来源 | 说明 |
| - | ---- | ---- |
| `deviceId` | 前端生成 UUID，持久化本地存储（App 用原生 UUID / H5 用 `localStorage`） | 设备稳定标识（**可被伪造，不能单独作安全依据**） |
| `platform` | 枚举 `ANDROID/IOS/H5/WEB` | 端判定 |
| `model` | 设备型号 | 指纹组成项 |
| `osVersion` | 系统版本 | 指纹组成项 |
| `appVersion` | 壳版本（H5 无则空） | 指纹组成项 |
| `screen` | `WxH`（可选） | 辅助 |
| `timezone` | 时区（可选） | 辅助 |
| `userAgent` | **服务端**从请求头取 | 不信任前端传值 |

**服务端设备指纹：**

```text
fingerprint = HMAC-SHA256(key = 服务端盐(外置配置), msg = deviceId + "|" + platform + "|" + model + "|" + osVersion + "|" + uaCanonical)
```

- `uaCanonical`：UA 归一化（去版本号噪声，保留浏览器/内核/OS 大类），降低 UA 微变导致指纹漂移。
- 服务端盐（`hrm.auth.device-fingerprint-salt`）**外置、不入库**。
- **安全定位**：指纹是「降低误判」的辅助，**不是强认证**；真正的二次验证靠短信（须经安全评估确认抗伪造强度）。

### 4.2 设备信任模型

#### 4.2.1 新增表（**只设计，不迁移**；迁移属 C 档）

**表 1 `auth_trusted_device`（受信设备）**

| 字段 | 类型 | 约束 | 说明 |
| - | ---- | ---- | ---- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `employee_id` | BIGINT | NOT NULL | 归属员工（逻辑外键 → `employee.id`，D6） |
| `device_fingerprint` | CHAR(64) | NOT NULL | 服务端指纹 hex |
| `device_id` | VARCHAR(64) | NULL | 前端上报 deviceId（仅展示/排障） |
| `platform` | VARCHAR(16) | NOT NULL | ANDROID/IOS/H5/WEB |
| `model` | VARCHAR(64) | NULL | 型号 |
| `os_version` | VARCHAR(32) | NULL | 系统版本 |
| `app_version` | VARCHAR(32) | NULL | 壳版本 |
| `last_ip` | VARCHAR(45) | NULL | 最近 IP（IPv6 兼容；**出参脱敏**） |
| `first_seen_time` | DATETIME | NOT NULL | 首次受信时间 |
| `last_seen_time` | DATETIME | NOT NULL | 最近活跃时间 |
| `trusted` | TINYINT | NOT NULL DEFAULT 1 | 1 受信 / 0 未受信 |
| `revoked` | TINYINT | NOT NULL DEFAULT 0 | 1 已撤销（软删，保留审计） |
| `create_time` | DATETIME | NOT NULL | D8 应用层填充 |
| `update_time` | DATETIME | NOT NULL | D8 |

**索引：** `uk_auth_device_emp_fp (employee_id, device_fingerprint)`（唯一，幂等 upsert）；`idx_auth_device_fp (device_fingerprint)`。

**表 2 `auth_sms_log`（短信发送审计，可选但建议）**

| 字段 | 类型 | 说明 |
| - | ---- | ---- |
| `id` | BIGINT PK | |
| `phone_masked` | VARCHAR(20) | **仅存脱敏手机号**（如 `138****5678`），不存明文 |
| `scene` | VARCHAR(24) | LOGIN/DEVICE_VERIFY/PERIODIC_REAUTH |
| `send_ip` | VARCHAR(45) | 请求 IP |
| `device_id` | VARCHAR(64) | 设备 |
| `result` | TINYINT | 成功/失败 |
| `fail_reason` | VARCHAR(128) | 失败原因（**不含验证码**） |
| `create_time` | DATETIME | |

**索引：** `idx_auth_sms_phone_time (phone_masked, create_time)`、`idx_auth_sms_ip_time (send_ip, create_time)`。

#### 4.2.2 与现有 37 表的关系

- **不新增 `employee` 字段**：设备信任独立成表，保持一期表结构冻结（V1 永不改）。
- 新增表**逻辑外键**指向 `employee.id`（D6，不建物理外键）。
- 命名/索引遵循 db.md §8.0 通用约定（`snake_case`、主键 `id`、`idx_表名_字段`）。
- 迁移脚本号段：**建议 `V14`**（现状至 V13），且须与 db.md 快照同步（L4 门禁）。**执行属 C 档。**

### 4.3 会话与时效

#### 4.3.1 3 天有效期实现（双控）

| 控制点 | 实现 | 说明 |
| - | ---- | ---- |
| JWT `exp` | `jwt.expire=259200`（3 天） | **由 86400 改**（F8）；到期 JWT 解析失败 → 401 |
| Redis TTL | `hrm:session:{sid}` TTL = 259200 | 会话权威；`save` 时设置 |
| 双控语义 | **两者取「较短者」生效** | 防「JWT 未过期但会话已删」的越权窗口 |

#### 4.3.2 「每 3 天短信验证一次」状态机

```text
[未登录]
   │ ① 密码登录(B1) / 短信登录(A2)
   ▼
[已登录·active]  ──── sid 会话 TTL=3天, JWT exp=3天
   │   ├─ 请求通过（JwtAuthFilter 校验 sid）
   │   ├─ 首次登录若为新设备 → 分流到 [待二次验证]
   │   └─ 到期判断：now ≥ loginTime + 3天 或 sid 不存在
   ▼
[待周期重认证]（会话已失效）
   │ ② 短信续期(B3, PERIODIC_REAUTH)  ── 保留 deviceId，签发新 sid
   │ ③ 或 重新登录(A2/B1)             ── 从零登录
   ▼
[已登录·active]（新 3 天周期）

[待二次验证]（仅密码登录遇新设备）
   │ ④ 设备短信验证(B2, DEVICE_VERIFY) → [已登录·active] 且写入 auth_trusted_device
```

- 新设备判定：登录时 `fingerprint` 不在 `auth_trusted_device`（`trusted=1 AND revoked=0`）→ 返回 `needDeviceVerify=true` + `twoFactorTicket`（短 TTL，如 5 分钟，Redis 暂存）。
- 到期路径（**待裁定**，§6）：**选项 1 强制重新登录**（更安全、体验差）vs **选项 2 短信验证后续期**（体验好、需评估短信滥用面）。本文**建议选项 2**（贴合「每 3 天短信验证一次」的用户原话），但须安全评估。

#### 4.3.3 token 刷新与吊销（汇总）

| 场景 | 处理 |
| - | ---- |
| 正常使用（<3 天） | 不续期，`exp` 固定 |
| 即将到期 | 前端在到期前引导「周期重认证」（B3） |
| 登出（单端） | 删 `hrm:session:{sid}` + 索引移除 |
| 登出（全部端） | 按 `hrm:session:idx:{employeeId}` 删全部 |
| 禁用/删除/重置密码/改密 | 删全部会话（安全事件） |
| 撤销单设备 | 删该设备对应 sid + 标记 `auth_trusted_device.revoked=1` |

### 4.4 短信通道设计

#### 4.4.1 端口与实现（三件套）

```text
port:    SmsSender（接口）           send(phone, scene, code) → SmsSendResult
impl:    AliyunSmsSender            @ConditionalOnProperty(hrm.sms.provider=aliyun)
impl:    LoggingSmsSender（降级）    @ConditionalOnProperty(hrm.sms.provider=none, matchIfMissing=true)
```

- **降级实现（未配置 Key）**：`LoggingSmsSender` **不真正发短信**，生成**固定开发码**（如 `000000` 或按配置 `hrm.sms.dev-fixed-code`）写入 Redis；日志只记录「**已发送至 138****5678（降级模式）**」，**绝不记录 code 明文**。
- **生产禁用降级必须显式**：生产启动时若 `provider=none` 且非 `local/dev` profile → **启动期 fail-fast**（`@Profile` 守卫），避免生产误用固定码。
- `AliyunSmsSender`：调用阿里云短信 SDK（`dysmsapi`）；失败归类 1105（不暴露上游错误细节）。

#### 4.4.2 频控（三维度）

| 维度 | 规则（建议默认） | Redis 键 |
| - | ---- | - |
| 同手机号 | 60s 间隔、10 次/天 | `hrm:sms:limit:phone:{phone}` |
| 同 IP | 20 次/时 | `hrm:sms:limit:ip:{ip}` |
| 同设备 | 10 次/时 | `hrm:sms:limit:device:{deviceId}` |

- 计数用 Redis `INCR` + `EXPIRE`；命中→1101。
- **令牌桶/窗口算法**：属算法工程师选型范畴（路由 R09），本文只定维度与默认值；上线前须算法出「四件套」并在 `hrm.algo.ratelimit`/`hrm.sms.*` 参数外置（**禁止硬编码** A03）。

#### 4.4.3 验证码存储

| 键 | 值 | TTL | 说明 |
| - | ---- | ---- | ---- |
| `hrm:sms:code:{scene}:{phone}` | 验证码（6 位） | 300s | 校验成功即删（一次性） |
| `hrm:sms:attempt:{scene}:{phone}` | 失败次数 | 300s | 达 `max-verify-attempts`(5) → 1103 并删码 |

- **日志红线**：验证码**绝不入日志**、不出现在异常栈、不出现在审计表（`auth_sms_log.fail_reason` 只写「校验失败」，不写内容）。
- 明文仅在「服务端 → 短信通道」一次流转；存储即用即删。

### 4.5 高德定位适配

#### 4.5.1 端口与实现

```text
port:    GeoService（接口）          regeo(lng,lat) → GeoAddress; distance(a,b) → meters; inFence(center,radius,point) → boolean
impl:    AmapGeoService              @ConditionalOnProperty(hrm.geo.provider=amap)
impl:    HaversineGeoService（降级）  @ConditionalOnProperty(hrm.geo.provider=haversine, matchIfMissing=true)
```

- **降级**：未配 Key 时用现有 [HaversineCalculator](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/HaversineCalculator.java) 直算球面距离，**跳过逆地理编码**（仅做围栏校验）；不阻断打卡。
- 现有围栏校验码 `9104` 复用（`ATTENDANCE_LOCATION_MISMATCH`）。

#### 4.5.2 链路

```text
前端获取经纬度（WebView/H5 Geolocation）
  → POST /attendance/check-in 携带 { lng, lat, ... }
  → 服务端：① 校验经纬度合法范围  ② (Amap) 逆地理编码取地址/城市（审计展示）
           ③ 围栏校验：distance(中心, 打卡点) ≤ radius?  ④ 落 attendance_record
```

- 逆地理编码结果**仅用于展示/审计**，围栏判定用距离（不依赖逆编码，降级无影响）。
- **Key 落点**：`hrm.geo.amap.key` + `security-code`（服务器外置，主智能体托管）。
- `TODO(扩展): 定位防作弊（虚拟定位识别）属安全范畴，须安全评估后定方案。`

### 4.6 对已实现代码的影响面（文件级 + 风险等级）

| 文件 / 配置 | 改动内容 | 改动量 | 风险 |
| - | ---- | ---- | ---- |
| `util/SessionUtil` | 多会话 API（按 sid）；保留旧方法 | ~60 行 | **中** |
| `filter/JwtAuthFilter` | 会话查找改 sid 优先 + userId 回退；注入 clientType/deviceId | ~30 行 | **高（认证主链路）** |
| `common/SessionInfo` | 增 `employeeId/clientType/deviceId/sid` | ~8 行 | 中 |
| `common/LoginUser` | 增 `clientType/deviceId` | ~4 行 | 低 |
| `common/PublicEndpoints` | 新增 A1/A2/B2/(D1)；改为「方法+路径」匹配 | ~10 行 | **中高（外部暴露面）** |
| `service/auth/AuthService` + `impl/AuthServiceImpl` | 新增 smsLogin/deviceVerify/sessionRenew/listDevices/revokeDevice；改 login/logout | ~200 行 | **高** |
| `controller/auth/AuthController` | 新增 6–8 端点 | ~80 行 | 中 |
| `enums/ErrorCode` | 新增 11xx 段 9 码 | ~20 行 | 低 |
| **新增** `config/SmsProperties`(hrm.sms.*) | 强类型配置 | 新文件 | 低 |
| **新增** `config/GeoProperties`(hrm.geo.*) | 强类型配置 | 新文件 | 低 |
| **新增** `config/AuthProperties`(hrm.auth.*) | 强类型配置 | 新文件 | 低 |
| **新增** `service/auth/port/SmsSender` + `impl/AliyunSmsSender` + `LoggingSmsSender` | 端口 + 双实现 | 新文件 ×3 | **高（外发通道/凭据）** |
| **新增** `service/geo/port/GeoService` + `impl/AmapGeoService` + `HaversineGeoService` | 端口 + 双实现 | 新文件 ×3 | 中 |
| **新增** `service/auth/support/DeviceFingerprint` | 指纹算法 | 新文件 | 中 |
| **新增** `entity/AuthTrustedDevice` + `mapper/AuthTrustedDeviceMapper` | 表映射（依赖 V14） | 新文件 ×2 | 中 |
| `config/RedisConfig` | 无改动（键规范新增） | 0 | 低 |
| `application.yml` / 外置 prod | `jwt.expire=259200` + `hrm.sms.*`/`hrm.geo.*`/`hrm.auth.*` | ~30 行 | 中 |
| `server-architecture.md` §5.1 配置表 | 补三个命名空间（**须同步，属文档变更**） | — | 低 |
| `deploy/nginx.conf.example`、`hrm-admin` dev proxy | 端口 8080→8081、端路径 | 少量 | **中高（涉现网/部署，C 档）** |

> **注意：** 上表为**改动面预估**，非本文施工内容。实际改动须按批次（§5）拆派对应角色，且**过主智能体 Review**。

---

## 5. 第 4 部分：分批实施计划

> **原则：** 先做「**不改契约的地基**」（会话多端化 / 配置命名空间 / 适配器端口），**再做登录契约改造**，**最后做三端拆分**。
> **依赖链：** M1 → M2 → M3 → M4 → M5（M3 可与 M2 并行前提：不共享可写文件且无产物依赖）。
> **验收基座：** 本机无 JDK/Maven/MySQL/Redis，所有验收写成**可静态审查条款**，运行类收敛到服务器阶段并标注。

| 批次 | 范围 | 依赖 | 验收标准（静态可审查） | 风险 |
| - | ---- | ---- | ---- | ---- |
| **M1 会话多端化地基**（不改契约） | `SessionUtil` 多会话 API + `JwtAuthFilter` sid 优先/回退 + `SessionInfo`/`LoginUser` 扩字段 | 无 | ① 新增方法签名与键规范齐备；② `JwtAuthFilter` 保持「旧 token 回退」分支存在（可静态检索）；③ 未改任何 URL/出参结构；④ 会话键 `hrm:session:{sid}` 与索引 `hrm:session:idx:{employeeId}` 定义写入本文与 `server-architecture.md`（同步） | **中** |
| **M2 配置命名空间 + 适配器端口**（不改契约） | `hrm.sms.*`/`hrm.geo.*`/`hrm.auth.*` 三配置类 + `SmsSender`/`GeoService` 端口 + 降级实现 | M1（无强依赖，可先行；建议 M1 后） | ① 三配置类 `@ConfigurationProperties` 键名与 §3.5 逐键一致；② 端口 interface + 两实现（生产/降级）齐备；③ **降级实现日志不含验证码**（可静态检索日志语句）；④ 生产 profile 下 `provider=none` fail-fast 守卫存在 | 低–中 |
| **M3 设备信任表 + 指纹**（DB 设计 → 迁移 C 档） | `auth_trusted_device`/`auth_sms_log` DDL 设计 + 实体/Mapper + `DeviceFingerprint` | M2（配置盐） | ① DDL 与 db.md §8.0 约定一致、表名/索引唯一性自检通过；② 迁移脚本**新版本号 V14**、**不改历史脚本**（db.md §9.3 门禁）；③ 快照与迁移同步；④ **执行迁移属 C 档，本批只出脚本** | 中 |
| **M4 登录契约改造** | A1/A2/B1–B4/C1/C2/D1 端点 + 11xx 错误码 + `PublicEndpoints` 扩展 + 周期重认证状态机 | M1、M2、M3 | ① 接口清单与 `api.md` 修订稿逐条一致（**须先定契约**，P5）；② `PublicEndpointsTest`/错误码段测试更新；③ 频控/验证码键与 §4.4 一致；④ 到期路径按**用户裁定**实现（§6） | **高（鉴权/外发）** |
| **M5 三端拆分** | `shared/api-client` 包 + `hrm-app-staff`/`hrm-app-boss`/`hrm-web` 新工程骨架 + 发布配置 | M4（认证契约稳定后） | ① 三端各自可独立 build（`npm run build` 通过）；② 无跨端源码复制（可静态检索重复封装）；③ `hrm-admin` **零改动**（`git diff` 空）；④ 部署脚本 + Nginx 片段（**执行属 C 档**） | 中–高 |

**排序理由：** M1/M2 是**地基**，不改任何对外契约（145 接口路径/出入参不动），可先行验证且可独立回滚；M3 结构先行（P4）；M4 才动契约（P5 契约先行，须先修 `api.md`）；M5 依赖稳定的登录契约，避免三端反复改认证。

> **待用户裁定阻塞项：** M4 的「到期路径」与「captcha 开关」、M5 的「先拆 vs 功能稳定后拆」未裁定前**不得进入实现**（P1 口径先行）。

---

## 6. 第 5 部分：待用户裁定清单

| # | 议题 | 现状 | 建议 | 备选方案 | 影响范围 |
| - | ---- | ---- | ---- | ---- | ---- |
| Q1 | **3 天到期后路径** | 无（新设计） | **短信验证后续期**（贴合「每 3 天短信验证一次」原话，体验好） | ① 强制重新登录（更安全）② 短信续期（体验好） | 登录契约（B3）、前端引导流程、短信量、安全评估面 |
| Q2 | **是否允许查看/撤销已信任设备** | 无 | **允许**（员工自助管理，误报自救） | ① 允许（C1/C2）② 不允许（仅后台） | 新增 2 端点、`auth_trusted_device` 表、越权校验 |
| Q3 | **短信是否配图形验证码** | 无 | **默认关闭，可配置开启**（`hrm.auth.captcha-enabled`）；命中风控时**按需弹出** | ① 常开 ② 常关 ③ 风控触发（推荐） | A1/D1 端点、防刷成本、用户体验 |
| Q4 | **三端先拆 vs 功能稳定后拆** | Demo 单工程三入口 | **功能稳定后再拆**（先 M1–M4 打地基，M5 最后） | ① 先拆工程再补功能 ② 稳定后拆 | 前端工期、回归面、发布节奏 |
| Q5 | **API 是否按端分区** | 角色驱动（145 已冻结） | **不分区**（保持角色驱动 + `X-Client-Type` 头） | ① 保持（推荐）② 按端分区（300+ 处改动）③ v2 并行 | **145 契约、3 工程 5 文档、全量回归** |
| Q6 | **与 courier-server 共存方案** | 现网占 8080/80/443/6379 | **候选 A（子域 + 独立端口 8081，隔离度最高）** | ① A 子域 ② B 同 Nginx 路径前缀 ③ C 独立 Nginx 端口 | Nginx 配置（C 档）、DNS、端口放行、安全组（安全面） |
| Q7 | **Redis 归属** | 现网 courier-redis 占 6379 | **独立 Redis 实例（占位 6380）** | ① 独立实例 ② 复用 6379 独立 database | 会话/验证码隔离、跨应用误操作面（安全评估） |
| Q8 | **滑动续期是否允许** | 无 | **不允许**（`exp` 固定 3 天，到期重认证） | ① 固定（推荐）② 活跃即续期 | 会话安全、长期占用风险 |
| Q9 | **设备指纹盐与强度** | 无 | 外置盐 + UA 归一化；**须安全评估确认抗伪造** | ① 现方案 ② 增强（结合原生设备 ID/证书） | 免密体验、越权风险 |
| Q10 | **`1104` 表达方式** | 无 | 「HTTP 200 + `needDeviceVerify` 字段」 | ① 正常字段 ② 业务错误码 1104 | 前端分流逻辑、契约一致性 |

> 上述 Q1–Q10 未裁定前，**M4 相关实现不得开工**（P1 口径先行 / 反模式 A04）。

---

## 7. 须经网络安全工程师评估清单（P0.5）

> 以下条目**均为安全面**，主智能体**不得凭经验放行**；须网络安全工程师按 L7 出结论（威胁建模 + 发现项 + 复现 + 修复建议），高风险项升级用户裁定。

| # | 条目 | 触发原因 | 建议评估要点 |
| - | ---- | ---- | ---- |
| S1 | **新增公开端点白名单**（A1/A2/B2/D1） | 外部暴露面扩大 | 未授权访问、枚举、刷量、DoS |
| S2 | **短信验证码机制** | 凭据通道 + 暴力破解面 | 验证码熵/尝试上限/频控绕过、短信轰炸、越权发码 |
| S3 | **设备信任模型** | 鉴权关键判定 | 指纹伪造、设备伪造绕过二次验证、越权信任他人设备 |
| S4 | **会话多端化**（sid 索引/回退分支） | 认证主链路 | 会话固定、越权复用、回退分支弱化、强制下线失效 |
| S5 | **3 天/周期重认证时效** | 会话生命期 | 长期未验证窗口、续期滥用 |
| S6 | **高德 Key + 定位链路** | 第三方凭据 + 位置数据 | Key 泄露、位置数据隐私、虚拟定位绕过 |
| S7 | **Redis 与现网共存/独立** | 跨应用隔离 | 键空间串扰、`FLUSHDB` 误操作面 |
| S8 | **Nginx/端口/子域变更（候选 A/B/C）** | 生产外部暴露面变更 | 配置错误致越权暴露、证书、端口放行最小化 |
| S9 | **设备指纹盐与算法** | 抗伪造强度 | 盐泄露影响面、HMAC 参数、UA 归一化引入的碰撞 |
| S10 | **生产禁用降级（固定码）守卫** | 降级误用 | 生产环境误用固定码登录 |

> **候选 A/B/C、Redis 独立实例、端口放行均属 C 档**，须「安全评估结论 → 主智能体三步授权 → 运维执行」顺序（P0.5 跨切面）。

---

## 8. 附录

### 8.1 ADR 决策记录（本设计新增）

- **ADR-MC-01（架构风格）**：状态 `提议`。决策：维持**模块化单体单进程 + 单 MySQL + Redis**，三端共用后端。理由：团队规模/单机资源/运维成本；备选：微服务（否决，过度设计）。
- **ADR-MC-02（API 命名空间）**：状态 `提议`。决策：**保持角色驱动，不按端分区**；端维度用 `X-Client-Type` 头。后果：145 契约零改动；端可解释性略降。备选：按端分区（改动 300+ 处，否决）。
- **ADR-MC-03（会话多端化）**：状态 `提议`。决策：会话主键改 `sid`，互踢粒度降为「端 + 设备」，加员工索引键。后果：`SessionUtil`/`JwtAuthFilter` 改造；兼容期保留旧键回退。备选：维持全局互踢（否决，不满足三端并存）。
- **ADR-MC-04（外部适配三件套）**：状态 `提议`。决策：短信/定位一律「端口 + 生产实现 + 降级实现」，Key 外置托管。后果：未配 Key 可开发；生产 fail-fast 防降级误用。
- **ADR-MC-05（客户端共享层）**：状态 `提议`。决策：新增中立 `shared/api-client`，三端 `file:` 引用，禁止反向依赖受保护工程。备选：复制封装（违反复用）、submodule（运维重）。

### 8.2 术语

| 术语 | 含义 |
| - | ---- |
| `sid` | 会话唯一标识（取值 = JWT `jti`），`hrm:session:{sid}` 的主键 |
| 设备指纹 | 服务端基于 deviceId + 设备属性 + UA 计算的 HMAC，用于设备信任判定 |
| 周期重认证 | 每 3 天用短信验证延续/重建会话的机制 |
| 降级实现 | 未配置外部 Key 时的本地替代（固定码/球面距离），仅限非生产 |
| C 档 | 需主智能体三步授权的操作（迁移执行/Nginx 变更/端口放行等） |

### 8.3 同步与登记（本轮不改被保护文档）

- 本文为**新增文档**，未改动 `api.md`/`db.md`/`plan.md`/`server-architecture.md`/迁移脚本/`.trae/rules/`。
- **后续须同步（待主智能体排期，非本轮）**：① `server-architecture.md` §5.1 补 `hrm.sms.*/hrm.geo.*/hrm.auth.*`；② `api.md` 补 11xx 段与新增端点；③ `db.md` 补 2 张新表与 V14；④ `update-log.md` 追加本文件条目。
- **检查点（M02 即时追加）**：本轮完成「三端拆分 + 服务器拆分 + 登录改造」设计稿，产出 `hrm-dev/docs/multi-client-architecture.md`（新增，无回滚需求）；未触达任何生产。
