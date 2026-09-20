# 快递驿站智汇系统 · 三端演示 Demo 本地设计方案

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-17 |
| 状态 | 待评审 |
| 交付形态 | 本地可运行的三端演示 Demo（纯前端 + Mock 假数据，不依赖后端与数据库） |
| 覆盖范围 | 一期已交付功能演示 + 二期（包裹同步/站长视角/看板扩展）+ 三期（工单/通知/移动端） |
| 关联文档 | [plan.md](plan.md)、[api.md](api.md)、[db.md](db.md)、[requirement.md](requirement.md)、[deploy.md](deploy.md)、[test-cases.md](test-cases.md)、[../../SESSION-STATE.md](../../SESSION-STATE.md) |
| 本机环境实测 | Node v24.19.0 / npm 11.17.0 可用；**无 JDK、无 Android SDK、无 adb、无 gradle、无 MySQL、无 Redis** |

> 阅读约定：
> - 每条决策都写成「结论 + 理由 + 代价」，文末第 14 章汇总为 ADR 表，便于追溯。
> - 标 **【存疑】** 的位置表示依据不足或未实测，必须按给出的验证方式确认后才能落地，禁止凭记忆断言。
> - 为后期预留的代码统一用 `TODO(扩展): 说明` 标注。

---

## 1. 目标与边界

### 1.1 本轮目标

1. 交付一套**本地一条命令启动**的三端演示 Demo：网页端（PC 管理后台）、老板端（移动）、员工端（移动）。
2. Demo 覆盖二三期规划的全部关键业务流：包裹数据、同步任务状态机、站长视角、工单流转、通知、看板趋势与排行。
3. 移动端以**安卓 H5 壳**为目标形态：本机交付 H5 应用 + 安卓壳工程骨架，演示时用浏览器设备模拟替代真机。

### 1.2 明确不做（避免范围失控）

| 不做的事 | 原因 |
| ---- | ---- |
| 不启动 `hrm-server`、不连 MySQL/Redis | 本机无 JDK/数据库/缓存，环境不具备（硬约束） |
| 不编译安卓 APK、不做签名打包 | 本机无 Android SDK/adb/gradle（硬约束） |
| 不实现 `/api/v1/crawler/*`（爬虫侧接口） | 该段接口是微主机专用接口（见 plan.md 3.3），面向机器而非人，Demo 只需演示**管理侧**的同步任务可见性与重试 |
| 不修改 `hrm-admin` / `hrm-server` 任何文件 | 一期代码尚未通过 D/E 验收，生产以 `main` 为准，不得污染交付基线 |
| 不删除 `hrm-admin/src/views/{knowledge,money,performance,permission,system}` | 一期预留占位页，Demo 仅挂路由可达 |
| 不在 Demo 中落真实业务数据、真实 IP、真实密钥 | 仓库安全红线（项目规则 6） |

### 1.3 前置动作（阻塞项，必须先做）

当前仓库分支为 `main`。按项目规则「禁止直接向 main 提交」，实现开工前必须先执行：

```bash
git switch -c feature/三端演示Demo
```

**理由**：Demo 是新增交付物而非生产修复，走 `feature/*` → `dev` 合并；同时避免在 `main` 上误提交。**【存疑】** 是否需要在 `dev` 之外单开 Demo 分支由主智能体决策，本设计不越权决定。

---

## 2. 硬约束与环境实测结论

| 约束 | 实测结论 | 对设计的强制影响 |
| ---- | ---- | ---- |
| Node / npm | v24.19.0 / 11.17.0 可用 | H5 与 PC 演示版可在本机跑 |
| JDK | 缺失 | 后端不可编译运行 → Demo 必须**零后端依赖** |
| Android SDK / adb / gradle | 缺失 | 安卓壳只交付骨架 + 构建说明，**不承诺编译通过** |
| MySQL / Redis | 缺失 | Mock 层必须是唯一数据源，代码路径中不得存在「无后端就白屏」的分支 |
| 磁盘仓库红线 | 禁提交 IP/密钥/密码 | 所有地址、密钥、包名一律占位；壳的 H5 地址走 Gradle 配置，真实值不入库 |

**架构级推论（本设计的核心约束）**：Demo 的**数据来源必须可替换但不必须存在**。即三端页面代码只依赖「HTTP 契约」，Mock 层只是契约的一个实现；未来接真实后端时，关闭 Mock 即可，页面代码零改动。这条推论直接决定了第 7 章选「axios 适配器拦截」而不是「页面内硬编码假数据」。

---

## 3. 三端定位与角色映射

### 3.1 决策：端与角色正交

**结论**：**「端」决定信息架构（有哪些页面、入口在哪），「角色」决定数据可见范围与操作白名单。二者正交，不做「一端一角色」的硬绑定。**

**理由**：
- 同一个 H5 包在安卓壳里只能是一个应用，老板与员工共用同一份安装包更符合真实交付形态（少一个包、少一套发布流程）；用登录身份区分视图，避免「两个 App」这种与三期规划（plan.md 4：员工移动端）不符的设计。
- 一期已定义 `ADMIN / STAFF / STATION_ADMIN` 三个角色且 `employee.role` 字段已预留（db.md 3.3），**不为 Demo 新增 `BOSS` 角色**：新增角色会与一期生产角色模型分裂，二期开放 STATION_ADMIN 时还要再改一遍权限矩阵。
- 老板视角的本质是「权限最大的那个人的移动端视图」。一期已把全局看板定为 `ADMIN` 专属（api.md 4.2.1 `GET /dashboard/summary` 权限 = ADMIN），因此**老板端 = ADMIN 身份的移动端视图**，语义自洽、零改角色模型。

**代价**：老板端与网页端同为 ADMIN，需要在演示话术上说明「端不同 ≠ 权限不同」，避免评审误解。

### 3.2 角色与端映射矩阵

| 端 | 承载角色 | 数据可见范围 | 可执行操作（Demo 演示范围） |
| ---- | ---- | ---- | ---- |
| 老板端（移动 H5） | `ADMIN` | 全部驿站汇总 | 只读：经营总览、包裹趋势、驿站排行、异常预警；**不暴露** HR CRUD 与系统配置（那些属网页端场景） |
| 员工端（移动 H5） | `STATION_ADMIN`（站长） | 本站（`employee.station_id`） | 取件核销、新建/处理本站工单、查看本站同步状态、查看本站包裹 |
| 员工端（移动 H5） | `STAFF`（员工） | 本站包裹（只读）+ 本人相关工单 | 取件核销、上报工单、处理指派给自己的工单、查看通知 |
| 网页端（PC） | `ADMIN` | 全局 | 全部：HR 管理（一期）+ 包裹/同步/工单/通知（二三期） |
| 网页端（PC） | `STATION_ADMIN` | 本站 | 包裹查询、同步查看、工单处理；**无** HR CRUD |
| 网页端（PC） | `STAFF` | 本人 | 仅个人中心（沿用一期路由守卫规则 4 的语义） |

**数据可见范围实现口径（三端统一，一处收口）**：
- `ADMIN` → 不附加任何 `station_id` 过滤条件；
- `STATION_ADMIN` / `STAFF` → 强制注入 `station_id = 当前登录员工的 station_id`，**服务端（此处即 Mock 层）强制覆盖前端传入值**，前端传什么都不生效。

**理由**：这是越权防护的唯一可靠位置。plan.md 3.4 已明确「`employee.station_id` 归属关系是二期数据可见范围基础」，Mock 层按同一口径实现，未来接真实后端时行为一致，不会出现「Demo 能看全站、上线只能看本站」的演示事故。

### 3.3 演示账号（仅存在于 Mock 层）

| 账号 | 角色 | 归属驿站 | 用途 |
| ---- | ---- | ---- | ---- |
| `admin` | ADMIN | — | 网页端 + 老板端主演示账号 |
| `admin_pwd0` | ADMIN | — | 演示首登强制改密（`pwd_changed=0`） |
| `st001_admin` | STATION_ADMIN | 城东驿站 | 员工端站长视角 |
| `st001_staff` | STAFF | 城东驿站 | 员工端员工视角 |

统一演示密码 `demo1234`。**说明**：该密码只存在于 Mock 数据中，不是任何环境（本地/测试/生产）的真实凭据，不违反安全红线；但文档与代码中不得出现任何真实环境口令。

---

## 4. 总体分层架构

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                        演示宿主：桌面浏览器 / 安卓 WebView 壳                    │
├──────────────────────┬────────────────────────┬───────────────────────────────┤
│ 端选择入口页          │ 网页端（PC）            │ 移动端 H5                      │
│ index.html           │ pc.html                 │ mobile.html                   │
│ /                    │ /pc.html                │ /mobile.html                  │
├──────────────────────┴────────────────────────┴───────────────────────────────┤
│ ① 表现层                                                                        │
│   PC：Element Plus（复用一期主题） + Demo 扩展版 layout + 5 个一期页面（复用）     │
│   H5：Vant 4 + Tabbar/NavBar 布局（老板端视图 / 员工端视图，按角色渲染）           │
├───────────────────────────────────────────────────────────────────────────────┤
│ ② 应用层（各端独立，互不 import）                                                │
│   PC：router（Demo 自建，扩展二三期路由与守卫）                                   │
│       复用 @admin/stores/auth.js（storage key: hrm_admin_token）                │
│   H5：router（自建） + mobile/stores/auth.js（storage key: hrm_demo_mobile_*）   │
│       mobile/utils/http.js（Vant Toast 版 HTTP 封装）                            │
├───────────────────────────────────────────────────────────────────────────────┤
│ ③ 数据接入层（契约一致，实例各自持有）                                             │
│   PC：@admin/utils/request.js ── 零改动复用 ──┐                                 │
│   H5：mobile/utils/http.js ──────────────────┤ baseURL 均为 /api/v1             │
│                                              ↓                                  │
│                       两者都挂载同一个 Mock 适配器（axios adapter）               │
├───────────────────────────────────────────────────────────────────────────────┤
│ ④ Mock 层（shared/mock）— 纯前端，无网络、无后端                                 │
│   ① engine.js    axios 自定义适配器：按 method + url 匹配路由，返回统一响应结构    │
│   ② routes/*     路由注册表：认证/员工/部门/驿站/看板/包裹/同步任务/工单/通知       │
│   ③ db.js        内存数据库：种子实体（驿站8/部门6/员工56）                        │
│   ④ parcelIndex  包裹索引层：TypedArray（默认 20 万条虚拟包裹）+ 按需水合          │
│   ⑤ overlay.js   写操作覆盖层：落 localStorage（取件/工单/通知/同步任务）           │
│   ⑥ scenario.js  演示剧本：预置失败批次、超 SLA 工单、超 48h 未取件等剧情数据       │
└───────────────────────────────────────────────────────────────────────────────┘
```

**关键点说明**：Mock 层位于**适配器**这一层，而不是替换 `request.js`、也不是在各页面里写假数据。因此一期页面的业务代码（含其 API 调用与错误处理）在 Demo 中原封不动地跑起来，这就是「可替换数据源」的落地方式。

---

## 5. 信息架构与页面清单

优先级口径：**P0 = 演示主线必须可用**；**P1 = 完善体验，建议交付**；**P2 = 可选/占位**。

### 5.1 网页端（PC 管理后台演示版）

| # | 页面 | 路由 | 优先级 | 核心区块 | 关键交互 | 来源 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 登录 | `/login` | P0 | 账号密码表单 | 演示账号一键填充（Demo 新增，不改进程内逻辑） | 复用一期视图 |
| 2 | 数据看板 | `/dashboard` | P0 | 一期 4 指标卡 + 包裹趋势折线 + 驿站排行 TOP5 + 同步健康度 + 工单/SLA 卡片 | 时间范围切换、点击卡片下钻到包裹/工单页 | **Demo 扩展版** |
| 3 | 员工管理 | `/employee` | P0 | 筛选栏 + 表格 + 增删改/启停/重置密码/导入导出 | 同 一期 api.md 4.3 | 复用一期视图 |
| 4 | 部门管理 | `/department` | P0 | 部门树 + CRUD | 同 api.md 4.4 | 复用一期视图 |
| 5 | 驿站管理 | `/station` | P0 | 列表 + CRUD + 启停 | 同 api.md 4.5 | 复用一期视图 |
| 6 | 个人中心 | `/profile` | P0 | 个人信息 + 改密 | 同 api.md 4.1.3/4.1.4 | 复用一期视图 |
| 7 | 包裹管理 | `/parcel` | P0 | 筛选栏（驿站/状态/时间范围/运单号）+ 表格 + 详情抽屉 + 统计条 | 分页、组合筛选、详情查看、导出 CSV | Demo 新建 |
| 8 | 同步任务 | `/parcel/sync` | P0 | 任务列表（驿站/批次/状态/包裹数/耗时）+ 状态标签 + 批次日志抽屉 | 手动触发、失败重试、查看日志明细 | Demo 新建 |
| 9 | 工单管理 | `/work-order` | P0 | 状态 Tab（待处理/处理中/已解决/已关闭/超 SLA）+ 列表 + 详情时间线 | 指派、状态流转、SLA 倒计时、超时高亮 | Demo 新建 |
| 10 | 通知中心 | `/notification` | P1 | 通知列表 + 未读筛选 + 未读角标 | 标记已读、全部已读、跳转业务详情 | Demo 新建 |
| 11 | 知识库 | `/knowledge` | P2 | 空壳占位 | 仅路由可达 | **保留不删** |
| 12 | 资金 | `/money` | P2 | 空壳占位 | 仅路由可达 | **保留不删** |
| 13 | 绩效 | `/performance` | P2 | 空壳占位 | 仅路由可达 | **保留不删** |
| 14 | 权限 | `/permission` | P2 | 空壳占位 | 仅路由可达 | **保留不删** |
| 15 | 系统 | `/system` | P2 | 空壳占位 | 仅路由可达 | **保留不删** |

> 第 11–15 项：`hrm-admin` 中这 5 个页面是仅含 `<router-view/>` 的空壳，Demo 沿用同一形态，**不填业务内容**，目的是保留一期已冻结的导航结构。**理由**：填了内容就变成「顺手造功能」，属超范围。

### 5.2 移动端 · 老板端（`ADMIN` 身份）

| # | 页面 | 路由 | 优先级 | 核心区块 | 关键交互 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| B1 | 登录 | `/login` | P0 | 表单 + 3 个「一键体验」身份按钮 | 一键登录（老板/站长/员工）、手输账号密码 |
| B2 | 经营总览 | `/boss/home` | P0 | 4 指标卡（今日入库/今日取件/待取件/异常件）+ 近 7 天迷你趋势 + 异常提示条 | 下拉刷新、点击卡片下钻 |
| B3 | 包裹趋势 | `/boss/trend` | P0 | 近 7/30 天入库与取件折线（可切驿站） | 时间范围切换、驿站筛选、图例开关 |
| B4 | 驿站排行 | `/boss/rank` | P0 | 8 个驿站列表 + 进度条 + 排名徽标 | 按包裹量 / 取件率 / 异常率切换排序 |
| B5 | 异常预警 | `/boss/alerts` | P0 | 三组预警：超 48h 未取件 TOP10、同步失败驿站、超 SLA 工单 | 分组折叠、点击跳对应明细 |
| B6 | 我的 | `/boss/me` | P1 | 账号信息、切换演示身份、退出登录 | 身份切换（Demo 专用） |

### 5.3 移动端 · 员工端（`STATION_ADMIN` / `STAFF` 身份）

| # | 页面 | 路由 | 优先级 | 核心区块 | 关键交互 | 可见角色 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| S1 | 工作台 | `/staff/home` | P0 | 本站 4 指标 + 4 宫格快捷入口（包裹/取件/工单/通知） | 下拉刷新、角标提示 | 两者 |
| S2 | 本站包裹 | `/staff/parcel` | P0 | 状态 Tab（全部/待取件/已取件/异常）+ 列表（无限滚动） | 运单号搜索、下拉刷新、加载更多 | 两者（STAFF 只读） |
| S3 | 包裹详情 | `/staff/parcel/:id` | P1 | 包裹信息卡 + 底部操作栏 | 取件核销入口、上报异常 | 两者 |
| S4 | 取件核销 | `/staff/pickup` | P0 | 运单号输入框 + 扫码占位按钮 + 结果反馈卡 | 校验运单号 → 展示包裹信息 → 确认取件 → Toast 反馈 | 两者 |
| S5 | 工单列表 | `/staff/workorder` | P0 | 状态 Tab + 列表（含 SLA 倒计时标签） | 筛选、进入详情 | 两者（STAFF 仅本人相关） |
| S6 | 新建工单 | `/staff/workorder/create` | P0 | 类型选择（包裹异常/设备故障/客户投诉/其他）+ 关联运单号 + 描述 + 图片上传占位 | 提交 → 返回列表并高亮新工单 | 两者 |
| S7 | 工单详情 | `/staff/workorder/:id` | P0 | 时间线（创建/指派/处理/解决/关闭）+ SLA 倒计时 + 操作栏 | 接单、填写处理记录、标记已解决、关闭 | 两者 |
| S8 | 通知 | `/staff/notification` | P0 | 通知列表 + 未读红点/Tabbar 角标 | 标记已读、点击跳转业务详情 | 两者 |
| S9 | 同步状态 | `/staff/sync` | P1 | 本站最近同步批次卡片（批次号/状态/包裹数/失败原因） | 查看历史批次（只读） | 仅 STATION_ADMIN |
| S10 | 我的 | `/staff/me` | P1 | 个人信息、所属驿站、改密入口、退出 | 切换演示身份 | 两者 |
| S11 | 修改密码 | `/staff/me/password` | P2 | 原密码/新密码/确认密码表单 | 提交后强制重新登录（对齐 api.md 4.1.4 语义） | 两者 |

> **与一期页面的关系**：一期 `hrm-admin` 已有 `/profile`（个人信息 + 改密），移动端 S10/S11 是**移动端形态的重新实现**，不复用 PC 视图（DOM 结构与交互模型差异过大，强行复用需大量断点样式，代价高于重写）。这属于「同一逻辑的第二次实现」，未超过「三次以上必须抽取」的红线（项目规则 核心原则 2）。

### 5.4 页面流转图（演示主线）

```text
端选择入口页 (/)
   ├─→ pc.html ──→ 登录 ──→ 看板 ──┬─→ 包裹管理 ──→ 详情
   │                               ├─→ 同步任务 ──→ 日志/重试
   │                               ├─→ 工单管理 ──→ 详情/指派/流转
   │                               ─→ 通知中心
   └─→ mobile.html ──→ 登录（一键选身份）
          ├─ 老板(ADMIN)：经营总览 → 趋势 → 排行 → 预警 → 我的
          └─ 站长/员工：工作台 → 本站包裹 → 取件核销
                        └→ 工单列表 → 新建/详情(流转)
                        └→ 通知
                        └→ 同步状态(仅站长) → 我的
```

### 5.5 与二三期规划的功能覆盖对照（自检用）

| plan.md 规划项 | Demo 覆盖位置 |
| ---- | ---- |
| 同步任务管理 + 状态机（待领取/执行中/成功/失败） | PC `/parcel/sync`；移动端 S9 |
| 爬虫微主机轮询 | **不覆盖**（机器侧接口，见 1.2 不做清单） |
| 包裹按驿站/状态/时间筛选查询、分页 | PC `/parcel`；移动端 S2/S3 |
| 看板扩展：包裹量趋势、驿站维度排行 | PC `/dashboard`；移动端 B2/B3/B4 |
| 开放 STATION_ADMIN（本站可见） | 3.2 角色矩阵 + Mock 强制过滤 + 移动端员工端 |
| 工单类型 / 四态流转 / SLA 提醒 | PC `/work-order`；移动端 S5/S6/S7 |
| 员工移动端 | mobile.html（老板端 + 员工端） |
| 权限细化 | 3.2 矩阵 + 路由守卫 + Mock 越权拦截（演示 403） |
| 通知（站内信） | PC `/notification`；移动端 S8（微信通知**不覆盖**，无资质与后端） |

---

## 6. Demo 工程目录方案（重点决策）

### 6.1 决策：新建独立 Demo 工程 + 别名复用一期源码

**结论**：在 `hrm-dev/` 下**新建独立工程 `hrm-dev/hrm-demo/`**（单个 Vite 工程、MPA 三入口），**不在 `hrm-admin` 上加 Mock 开关**；一期已交付的 PC 资产通过 **Vite 别名 `@admin` → `../hrm-admin/src` 零拷贝复用**；安卓壳另起独立目录 `hrm-dev/hrm-android-shell/`。

**理由（为什么不在 hrm-admin 上加开关）**：

1. **不污染验收基线**：一期 `hrm-admin` 尚未通过 D/E 验收、生产以 `main` 为准。加 Mock 开关需要改动 `main.js`（按环境决定是否挂 Mock）、`vite.config.js`（可能加代理或 fs 配置）、`package.json`（加 mock 依赖），且 Mock 相关代码要长期留在交付物里。这会扩大 D/E 验收的审查面，并让「一期交付物」与「演示物」在同一个包里互相牵制。
2. **可整体删除**：独立工程的最大价值是回滚成本为零——删掉一个目录即可，不需要在已验收代码上做「反向拆开关」。
3. **依赖可自由引入**：移动端要引入 Vant 4，PC 演示要引入 CSV 导出等能力。加到 `hrm-admin` 会让一期 PC 包的依赖清单里出现移动端 UI 库，语义混乱。
4. **别名复用而非拷贝复制**：若把一期 6 个页面拷进 Demo，会形成第二份实现与漂移风险（一期改 bug，Demo 不同步）。用别名让 Demo **直接编译一期源码文件**，保证同一份实现，同时 `hrm-admin` 的文件一个字节都不改。

**代价与风险**：

- Demo 编译依赖 `hrm-admin/src` 的目录结构与文件路径；若一期后续重命名/移动文件，Demo 会编译失败。**缓解**：Demo 只依赖 6 个视图 + 布局相关 4 个模块 + api 层，路径稳定；在 Demo 的 `README` 中登记依赖清单，作为一期重构时的检查项。
- 跨目录引入需要 Vite 允许访问根目录外的文件。**【存疑】** Vite 开发服务器的 `server.fs.allow` 默认值是「工作区根目录」，本项目仓库根存在 `.git`，因此默认很可能已覆盖 `hrm-dev/hrm-admin`。**验证方式**：启动 `npm run dev` 打开一个复用页面，若控制台报 `The request url ... is outside of Vite serving allow list` 则显式配置 `server.fs.allow: ['..']` 并复测。官方配置项见 Vite《开发服务器选项》`server.fs.allow`。

**考虑过但未选**：在 `hrm-admin` 内加 `VITE_USE_MOCK` 开关（改动最小但污染一期基线）；把一期页面复制进 Demo（零耦合但产生双份实现）。两者的取舍理由如上。

### 6.2 目录结构

```text
hrm-dev/
├── hrm-admin/                      # 一期 PC 管理端（本轮零改动，仅被 @admin 只读引用）
│   └── src/                        #   ├─ utils/request.js  ├─ stores/auth.js
│                                   #   ├─ api/*             ├─ layout/index.vue
│                                   #   ├─ styles/index.scss └─ views/{login,dashboard,
│                                   #        employee,department,station,profile}/*
├── hrm-server/                     # 一期后端（Demo 完全不使用）
│
├── hrm-demo/                       # 【新增】三端 Demo 工程（单 Vite 工程 / MPA 三入口）
│   ├── index.html                  #   端选择入口页
│   ├── pc.html                     #   网页端入口
│   ├── mobile.html                 #   移动端入口
│   ├── vite.config.js              #   MPA 多入口 + @admin/@ 别名 + port 5188 + fs.allow
│   ├── package.json
│   ├── README.md                   #   启动方式 + 对 hrm-admin 的依赖清单
│   ├── .env.demo                   #   演示参数（VITE_MOCK_PARCEL_COUNT 等，非密钥）
│   │
│   ├── src/
│   │   ├── shared/                 # 三端共享（只被 Demo 内部引用）
│   │   │   ├── mock/
│   │   │   │   ├── engine.js       #   axios 适配器 + 路由匹配 + 统一响应包装
│   │   │   │   ├── install.js      #   installMock([axiosInstance...]) 挂载入口
│   │   │   │   ├── db.js           #   内存数据库（种子实体 + 查询/SLA 计算工具）
│   │   │   │   ├── parcelStore.js  #   包裹索引层（TypedArray 20 万）+ 水合 + 覆盖层
│   │   │   │   ├── overlay.js      #   写操作持久化（localStorage）
│   │   │   │   ├── scenario.js     #   演示剧本预置数据
│   │   │   │   ├── util.js         #   确定性 PRNG、脱敏、分页、时间工具
│   │   │   │   └── routes/         #   按业务域拆分的路由注册表
│   │   │   │       ├── index.js
│   │   │   │       ├── auth.js         # 一期 4 条
│   │   │   │       ├── employee.js     # 一期 9 条
│   │   │   │       ├── department.js   # 一期 4 条
│   │   │   │       ├── station.js      # 一期 5 条
│   │   │   │       ├── dashboard.js    # 一期 1 条 + 二三期扩展 2 条
│   │   │   │       ├── parcel.js       # 二期新增
│   │   │   │       ├── syncTask.js     # 二期新增
│   │   │   │       ├── workOrder.js    # 三期新增
│   │   │   │       └── notification.js # 三期新增
│   │   │   ├── constants/
│   │   │   │   ├── role.js         #   角色枚举 + 可访问路由白名单（与 api.md 对齐）
│   │   │   │   ├── dict.js         #   状态/类型字典（包裹/同步任务/工单/通知）
│   │   │   │   └── errorCode.js    #   错误码（一期 10xx-50xx + Demo 提案段）
│   │   │   └── ui/
│   │   │       ├── charts/         #   轻量图表（纯 SVG/Canvas 手写，不用图表库）
│   │   │       └── states/         #   空态/骨架屏/错误态（各端各自包装）
│   │   │
│   │   ├── pc/                     # 网页端
│   │   │   ├── main.js             #   应用引导（Element Plus + 全量图标注册 + Mock 挂载）
│   │   │   ├── App.vue
│   │   │   ├── router/index.js     #   Demo 自建路由表 + 扩展守卫（含 STATION_ADMIN）
│   │   │   ├── layout/index.vue    #   Demo 扩展版布局（基于一期布局结构，扩展菜单）
│   │   │   ├── api/                #   二期/三期接口封装（一期接口直接用 @admin/api/*）
│   │   │   │   ├── parcel.js  syncTask.js  workOrder.js  notification.js
│   │   │   └── views/
│   │   │       ├── dashboard/      #   扩展版看板
│   │   │       ├── parcel/         #   包裹管理
│   │   │       ├── sync/           #   同步任务
│   │   │       ├── workOrder/      #   工单管理
│   │   │       └── notification/   #   通知中心
│   │   │
│   │   ├── mobile/                 # 移动端 H5
│   │   │   ├── main.js
│   │   │   ├── App.vue
│   │   │   ├── router/index.js     #   按角色分流：/boss/* 与 /staff/*
│   │   │   ├── stores/auth.js      #   独立 storage key：hrm_demo_mobile_token / _user
│   │   │   ├── utils/http.js       #   精简 HTTP 封装（Vant Toast/dialog 版）
│   │   │   ├── utils/bridge.js     #   安卓壳桥接（HrmBridge/HrmShell，浏览器下空实现）
│   │   │   ├── layout/TabbarLayout.vue
│   │   │   ├── components/         #   指标卡 / 状态标签 / SLA 倒计时 / 列表项
│   │   │   └── views/
│   │   │       ├── login/index.vue
│   │   │       ├── boss/           #   home / trend / rank / alerts / me
│   │   │       └── staff/          #   home / parcel(+detail) / pickup / workorder
│   │   │                           #   (+create/detail) / notification / sync / me
│   │   ├── views/                  #   （无）
│   │   └── styles/demo.scss        #   Demo 自有样式基座（CSS 变量、安全区）
│   │
│   └── public/                     #   静态占位资源（图标、无真实数据）
│
└── hrm-android-shell/              # 【新增】安卓 H5 壳工程骨架（Gradle，本机不构建）
    ├── README.md  BUILD.md
    ├── settings.gradle  build.gradle  gradle.properties
    ├── gradle/wrapper/gradle-wrapper.properties
    ├── local.properties.example    #   sdk.dir 占位（真实 local.properties 不入库）
    └── app/
        ├── build.gradle  proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/example/hrmwebview/MainActivity.java
            ├── assets/h5/          #   离线包放置位（构建产物拷贝，gitignore）
            ── res/{layout,xml,values}/
```

### 6.3 为什么用 MPA（多入口）而不是单入口 + 路由前缀

**结论**：三个 `.html` 入口，而不是一个 `index.html` 里用 `/pc/*`、`/mobile/*` 前缀分区。

**理由**：
1. **样式隔离**：Element Plus 与 Vant 4 各自带全局样式与 CSS 变量，同页混装存在互相覆盖风险。MPA 每个入口是独立 HTML + 独立打包产物，物理隔离，风险归零。
2. **加载成本**：移动端不必加载 Element Plus（桌面库，约百 KB 级 CSS+JS），反之亦然。
3. **跟真实形态一致**：上线时 `hrm-admin` 与移动端 H5 本来就是两个独立产物、两个部署路径，Demo 结构与生产结构同形，减少认知迁移成本。
4. **成本可控**：Vite 官方支持 MPA，只需在 `build.rollupOptions.input` 配置多个 `.html` 入口。**注意**：`hrm-admin` 用的是 `vite ^6.0.5`，在 Vite 6 中该配置项名称为 `build.rollupOptions`。**【存疑】** 更高版本 Vite 已把该选项更名为 `build.rolldownOptions`（`rollupOptions` 变为别名/弃用）。**缓解**：Demo 的 `vite` 版本与 `hrm-admin` 保持一致（`^6.0.5`），锁定 `rollupOptions` 写法，不追新。

---

## 7. Mock 数据方案

### 7.1 决策：axios 自定义适配器（adapter）拦截

**结论**：Mock 层实现为一个 **axios 自定义 adapter 函数**，通过 `httpInstance.defaults.adapter = mockAdapter` 挂载到 PC 与移动端的 axios 实例上。**不采用** Vite 中间件（`configureServer`）、**不采用** MSW（Mock Service Worker）。

**理由（为什么可行）**：
- axios 官方支持自定义 adapter：写一个接收 `config` 的函数，返回 Promise 并 resolve 一个**标准 axios 响应对象** `{ data, status, statusText, headers, config, request }`（依据：axios 官方文档《Adapters》/《响应结构》）。响应拦截器在该对象返回后照常运行，因此 `hrm-admin` 的 `request.js` 里的 `res.data` 拆包、`code` 分发、401 跳登录、blob 处理等逻辑**全部按原样生效**。
- 挂载点在 axios 实例上，`request.js` 零改动即可被拦截，满足「不污染一期代码」的硬约束。
- 纯前端、无网络请求，在 `file://`（安卓壳离线包）与任意静态托管下同样工作。

**为什么排除 Vite 中间件**：`server.proxy` / `configureServer` 只在 `vite dev` 阶段有效，`vite build` 后的产物与 Android WebView 离线包中完全不生效，会导致「开发能跑、打包后白屏」。**理由**：Demo 明确要求覆盖安卓壳形态，中间件方案在最关键的交付形态上直接失效。

**为什么排除 MSW**：MSW 的浏览器模式依赖 Service Worker。**【存疑】** Service Worker 需要安全上下文（HTTPS 或 localhost），`file://` 加载的 Android WebView 资产不满足；此外 Android WebView 对 Service Worker 的支持与 `ServiceWorkerController` 配置相关，本机无 SDK 无法验证。**理由**：核心交付形态存在未验证的失效风险，而自定义 adapter 零依赖、零风险，故选后者。

**代价**：
- 需要自己实现 URL 匹配与参数解析（约 60 行，含路径参数 `:id` 匹配）；不享受 MSW 的 handler 语法糖与网络层可视化。
- 适配器内需注意两处数据形态：`config.url` 在 axios 内部已与 `baseURL` 合并（需在实现时打日志确认，见 7.3 验证点）；`config.data` 已被默认 `transformRequest` 序列化为 JSON 字符串，需反序列化后再用。

### 7.2 Mock 引擎工作流

```text
页面调用 @admin/api/xxx 或 pc/mobile/api/xxx
        ↓
axios 实例（baseURL=/api/v1，请求拦截器注入 Bearer token）
        ↓  dispatchRequest 合并 baseURL → 调用 adapter
Mock Adapter（shared/mock/engine.js）
        ├─ 1. 解析 method / url / params / body
        ├─ 2. 路由匹配（精确路径 + `:param` 路径参数）
        ├─ 3. 无命中 → 返回 { code: 404, message: '接口未实现(演示)' }
        ├─ 4. 命中 → 构造 ctx { db, params, body, pathParams, currentUser }
        │       ├─ 权限校验：角色白名单（不一致 → code 403）
        │       ├─ 登录态校验：无 token（→ code 401）
        │       └─ 数据范围：非 ADMIN 强制注入 station_id
        ├─ 5. 执行业务 handler（读写 db / overlay）
        ├─ 6. 随机延迟 120~350ms（让 loading 态可见）
        └─ 7. 包装为统一响应 { code, message, data } 并 resolve
        ↓
响应拦截器（request.js / http.js）按 body.code 分发 → 页面
```

**统一响应结构**严格对齐 api.md 1.2：`{ code, message, data }`；分页 `data` 为 `{ total, pageNum, pageSize, list }`。**401/403/404 同时同步 HTTP 状态码**（adapter 的 `status` 字段），以覆盖 `request.js` 中「HTTP 状态码路径」与「body.code 路径」两条分支。

### 7.3 实现约定与验证点（必须实测的三处）

| # | 约定 | 验证方式 |
| ---- | ---- | ---- |
| V1 | adapter 内 `config.url` 是否已包含 `baseURL`（预期为 `/api/v1/parcel`） | 首次实现时 `console.log(config)` 确认；若未合并，则在适配器内先做 `baseURL + url` 拼接 |
| V2 | `config.data`（POST/PUT）是否为 JSON 字符串 | 打日志确认；是则 `JSON.parse`，并处理 `undefined`/`FormData` 情况 |
| V3 | adapter 返回的对象能否触发响应拦截器 | 挂载后跑通一次登录请求，确认 `code!=200` 时页面能弹出错误提示 |

**【存疑】** V1/V2 属 axios 内部行为细节，本设计按官方《Adapters》文档的示例（示例中直接使用 `config.url`、`config.data`）推导，未经本地实测，按上表在实现首日一次性确认。

### 7.4 数据契约

> 说明：一期 4 张表字段**完全依据 [db.md](db.md) 第 3 章**，不做任何改动；二三期实体在 db.md/plan.md 中仅有「设计要点」、**无完整 DDL**，故以下字段为 **Demo 契约草案**，正式 DDL 须由二期/三期专项设计评审定稿。草案字段命名已对齐一期命名规范（snake_case / `id` / `create_time` / `update_time` / `is_deleted`）。

#### 7.4.1 一期实体（与 db.md 一致，仅列关键字段）

| 实体 | 字段（与 db.md 逐项一致） |
| ---- | ---- |
| `department` | `id, parent_id, dept_name, sort_order, is_deleted, create_time, update_time` |
| `station` | `id, code, station_name, contact_person, contact_phone, address, status, remark, is_deleted, create_time, update_time` |
| `employee` | `id, username, password, real_name, phone, gender, dept_id, station_id, role, status, pwd_changed, entry_date, last_login_time, remark, is_deleted, create_time, update_time` |
| `login_log` | `id, username, employee_id, login_ip, login_result, fail_reason, user_agent, login_time` |

**出参 VO 差异（遵循 api.md）**：`phone`/`contactPhone` 一律按 api.md 1.4 脱敏（`138****5678`）；`password` 永不出参；列表项附加值字段 `deptName`/`stationName`。

#### 7.4.2 `parcel` 包裹（二期 · Demo 草案）

| 字段 | 类型 | 允许空 | 说明 |
| ---- | ---- | ---- | ---- |
| `id` | BIGINT | 否 | 主键，与一期主键策略一致（自增） |
| `station_id` | BIGINT | 否 | 归属驿站（逻辑外键 `station.id`），二期数据可见范围核心维度 |
| `waybill_no` | VARCHAR(50) | 否 | 运单号 |
| `status` | TINYINT | 否 | 0=待入库，1=在库待取，2=已取件，3=异常，4=已退回 |
| `receiver_name` | VARCHAR(50) | 是 | 收件人姓名（出参只显示「姓+*」） |
| `receiver_phone` | VARCHAR(20) | 是 | 收件人手机号（出参脱敏，规则同 api.md 1.4） |
| `shelf_code` | VARCHAR(20) | 是 | 货架/库位码 |
| `inbound_time` | DATETIME | 是 | 入库时间（趋势与超时预警的时间基准） |
| `pickup_employee_id` | BIGINT | 是 | 取件核销员工（逻辑外键 `employee.id`，历史留痕不校验） |
| `pickup_time` | DATETIME | 是 | 取件时间 |
| `sync_batch_no` | VARCHAR(40) | 是 | 同步批次号（plan.md 3.3 要求留痕） |
| `remark` | VARCHAR(255) | 是 | 备注 |
| `is_deleted` | TINYINT | 否 | 逻辑删除：0=否，1=是 |
| `create_time` / `update_time` | DATETIME | 否 | 创建/更新时间（应用层维护） |

**索引规划（对齐 plan.md 3.3 要点）**：

| 索引 | 字段 | 用途 |
| ---- | ---- | ---- |
| `idx_parcel_station_status` | (`station_id`, `status`) | 本站按状态筛选（最高频查询） |
| `idx_parcel_station_waybill` | (`station_id`, `waybill_no`) | 防重复入库 + 取件核销按运单号定位 |
| `idx_parcel_inbound_time` | (`inbound_time`) | 时间范围筛选、趋势统计 |

**【存疑】唯一键与逻辑删除的语义冲突（需二期专项决策）**：plan.md 3.3 要求 `station_id + waybill_no` 唯一键防重复入库，但 db.md 决策 D7 明确「不建数据库唯一索引」，理由是逻辑删除后行仍物理存在、MySQL 无部分索引、双库行为不一致。若 `parcel` 沿用 `is_deleted` 逻辑删除，`UNIQUE(station_id, waybill_no)` 会在「包裹删除后同一运单号再次入库」时直接冲突。**可选出路（待评审）**：① parcel 改为物理删除（包裹是流水型数据，删除语义弱）；② 删除时置 `is_deleted = id`（db.md 3.1.3 提到的演进方案）；③ 维持 Service 层查重 + 普通索引。**Demo 处理方式**：Mock 层按「站内运单号不重复」实现业务规则，不体现数据库约束差异，并在文档中标注该冲突待二期评审。

#### 7.4.3 `sync_task` 同步任务（二期 · Demo 草案）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | BIGINT | 主键 |
| `station_id` | BIGINT | 归属驿站 |
| `batch_no` | VARCHAR(40) | 同步批次号（唯一） |
| `status` | TINYINT | 0=待领取，1=执行中，2=成功，3=失败 |
| `parcel_total` | INT | 本批次抓取包裹数 |
| `success_count` / `fail_count` | INT | 成功/失败明细数 |
| `retry_count` | INT | 重试次数 |
| `error_msg` | VARCHAR(255) | 失败原因（失败态出参） |
| `assign_time` / `start_time` / `finish_time` | DATETIME | 状态机各节点时间（用于耗时展示） |
| `create_time` / `update_time` | DATETIME | 创建/更新时间 |

**状态机（严格按 plan.md 3.2 的四态，Demo 不引入第五态）**：

```text
待领取(0) ──领取──→ 执行中(1) ──┬──成功──→ 成功(2)  [终态]
                       ↑          └──异常──→ 失败(3) ──重试──
                       └──────────────────────────────────
```

**约束**：仅「失败」可重试（回到「待领取」并 `retry_count+1`）；「执行中」「成功」不可重试；每次流转追加一条 `sync_log`。**理由**：状态机是二期唯一有并发风险的逻辑（plan.md 提到 Redis 分布式锁防并发领取），Demo 虽无并发，但流转规则必须与规划一致，否则演示会给出错误的业务预期。

#### 7.4.4 `sync_log` 同步日志（二期 · Demo 草案）

`id, task_id, batch_no, level(0=info,1=warn,2=error), message, log_time`

#### 7.4.5 `work_order` 工单（三期 · Demo 草案）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` / `order_no` | BIGINT / VARCHAR(32) | 主键 / 工单号（如 `WO-20260916-0042`） |
| `type` | TINYINT | 1=包裹异常，2=设备故障，3=客户投诉，4=其他 |
| `status` | TINYINT | 0=待处理，1=处理中，2=已解决，3=已关闭 |
| `priority` | TINYINT | 0=低，1=中，2=高 |
| `title` / `content` | VARCHAR(100) / VARCHAR(500) | 标题 / 描述 |
| `station_id` | BIGINT | 归属驿站（数据可见范围维度） |
| `parcel_id` / `waybill_no` | BIGINT / VARCHAR(50) | 关联包裹（可空；三期依赖二期包裹数据） |
| `reporter_id` | BIGINT | 上报人（逻辑外键 `employee.id`） |
| `assignee_id` | BIGINT | 处理人（可空，未指派时为空） |
| `sla_deadline` | DATETIME | SLA 截止时间（按优先级规则计算） |
| `resolved_time` / `closed_time` | DATETIME | 解决/关闭时间 |
| `handle_log` | TEXT(JSON) | 处理记录时间线（Demo 存 JSON；**存疑**：正式设计应独立 `work_order_log` 表，见下） |
| `is_deleted` / `create_time` / `update_time` | — | 同公共约定 |

**状态机与流转合法性**：

```text
待处理(0) ──接单──→ 处理中(1) ──解决──→ 已解决(2) ──关闭──→ 已关闭(3)
   │                    │
   └──────关闭───────────┘（未处理直接关闭，需填写关闭原因）
                       已解决(2) ──驳回/重开──→ 处理中(1)   [反向流转]
```

**约束**：只有 `assignee_id = 当前用户` 或 `ADMIN` / 本站 `STATION_ADMIN` 可操作流转；`SLA` 超时仅做**展示层高亮与提醒**，不自动改状态。

**【存疑】** 工单流转记录用 JSON 字段还是独立 `work_order_log` 表：db.md 6.3 只提到「工单表（类型/流转状态机/SLA）、通知记录表」，没有日志表。Demo 用 JSON 字段是为了省钱省事；正式设计应倾向独立表（可索引、可分页、可审计）。**待三期专项设计定稿**。

#### 7.4.6 `notification` 站内信（三期 · Demo 草案）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | BIGINT | 主键 |
| `employee_id` | BIGINT | 接收人（逻辑外键 `employee.id`） |
| `type` | TINYINT | 1=工单指派，2=工单流转，3=同步失败，4=系统公告 |
| `title` / `content` | VARCHAR(100) / VARCHAR(500) | 标题 / 内容 |
| `biz_type` / `biz_id` | VARCHAR(20) / BIGINT | 业务类型与 ID（点击跳转用：work_order / sync_task / parcel） |
| `is_read` / `read_time` | TINYINT / DATETIME | 是否已读 / 已读时间 |
| `create_time` | DATETIME | 创建时间 |

#### 7.4.7 看板指标契约

| 指标组 | 字段 | 口径 |
| ---- | ---- | ---- |
| 一期沿用 | `employeeTotal, stationTotal, departmentTotal, todayLoginCount` | 严格对齐 api.md 4.2.1（`todayLoginCount` 按去重员工数） |
| 包裹 | `parcelTotal, todayInbound, todayPickup, pendingPickup, abnormalCount, pickupRate` | `pickupRate = todayPickup / todayInbound`（今日入库为 0 时返回 0，不返回 `NaN`） |
| 趋势 | `trend[{ date, inbound, pickup }]` | 近 N 天按天聚合，默认 7 天，支持切 30 天 |
| 排行 | `ranking[{ stationId, stationName, parcelTotal, pickupRate, abnormalRate }]` | 按包裹量降序，支持按取件率/异常率重排 |
| 同步 | `syncHealth{ latestBatchSuccessRate, failedStationCount, lastBatchTime }` | 取各驿站最近 1 个批次汇总 |
| 工单 | `workOrder{ pendingCount, processingCount, overSlaCount, todayNewCount, avgHandleMinutes }` | `overSlaCount` 排除已解决/已关闭 |

#### 7.4.8 错误码

一期 10xx–50xx **严格沿用 api.md 第 2 章**，Mock 层必须按同一码值返回（例如登录失败必须返回 `1001`，否则登录页的 `silent` 文案分支会走错）。

二三期扩展段：

| 段 | 含义 | 依据 |
| ---- | ---- | ---- |
| 60xx | 同步任务 / 爬虫 | plan.md 3.4 已明确「二期扩展 60xx 爬虫段」 |
| 70xx | 包裹 | plan.md 3.4 已明确「70xx 包裹段」 |
| 80xx | 工单 | **【存疑】Demo 提案，plan.md 未定义，待三期专项设计确认** |
| 90xx | 通知 | **【存疑】Demo 提案，同上** |
| 91xx | 考勤 / 排班 | Demo 提案（打卡规则与排班功能新增）。**顺延为 91xx 而非 90xx 的原因**：90xx 已被通知段占用（`9001` = 通知不存在），若考勤也用 90xx 会产生码值冲突 |

Demo 用到的具体码：`6001` 任务状态不允许重试、`7001` 运单号不存在、`7002` 包裹状态不允许取件、`7003` 该包裹已被他人取件、`8001` 工单状态流转非法、`8002` 无权操作该工单、`9001` 通知不存在、`9101` 该驿站尚未配置打卡规则、`9102` 不在打卡时间窗内、`9103` WiFi 校验未通过、`9104` 定位校验未通过（超出围栏）、`9105` 今日该类型打卡已完成、`9106` 班次不存在或已停用。

### 7.5 演示数据规模

| 实体 | Demo 规模 | 与生产的对齐情况 |
| ---- | ---- | ---- |
| 驿站 | 8 | 与 db.md 看板示例 `stationTotal=8` 一致 |
| 部门 | 6 | 与 `departmentTotal=6` 一致 |
| 员工 | 56 | 与 `employeeTotal=56` 一致 |
| 包裹 | **200,000（索引层虚拟 + 按需水合）** | 与二期生产目标量级一致 |
| 同步任务 | 240（8 驿站 × 近 30 天） | — |
| 同步日志 | 每任务 3–8 条，约 1,300 条 | — |
| 工单 | 120（含 6 条超 SLA、20 条已关闭） | — |
| 通知 | 60 | — |

**为什么一期三个数字刻意对齐 db.md/api.md 的示例值**：让评审在 Demo 里看到的看板数字与文档示例一致，避免「文档写 8 个驿站、演示显示 12 个」这种低级疑问，减少沟通噪音。

#### 7.5.1 20 万包裹的内存策略：轻量索引层 + 按需水合

**结论**：**不实例化 20 万个 JS 对象**，而是构建 4 个 `TypedArray` 索引（约 2 MB），查询时只对命中页的数据「水合」成完整对象。

```text
索引层（启动时一次性构建，确定性 PRNG 固定种子，约 2MB）
  stationIdx : Int8Array(200000)    // 归属驿站在驿站数组中的下标
  status     : Int8Array(200000)    // 包裹状态
  inboundTs  : Int32Array(200000)   // 入库时间（相对基准日的秒偏移）
  waybillSeq : Int32Array(200000)   // 运单号序号（拼接运单号用）

查询流程（例：城东驿站 + 在库待取 + 近 7 天，第 3 页，每页 20）
  1) 遍历索引层做条件计数 → total        （20 万次整数比较 + 时间比较）
  2) 二次遍历收集命中下标 → 按 inboundTs 取前 N 个（Top-K）
  3) 仅对当前页的 20 个下标「水合」完整对象（拼运单号、从姓名池取收件人、手机号脱敏）
  4) 叠加覆盖层（overlay）：取件/异常等写操作的状态与时间
  → 内存恒定，不随数据量增长
```

**理由**：
1. **内存**：20 万条完整对象（含字符串）约 40–60 MB，浏览器内存与 GC 压力明显，且每次刷新重建会造成可感知的启动卡顿；索引层约 2 MB，代价可忽略。
2. **真实性**：聚合指标（趋势、排行、超时预警）在 20 万条上真算，而不是「假装有 20 万、实际算 2 千」，避免演示数据与看板数字自相矛盾。
3. **验证价值**：能真实暴露「深度分页」「组合筛选」的交互体验问题（正是 plan.md 3.3 关心的大表查询模式）。

**代价与风险**：
- 实现复杂度高于直接造对象（约 +120 行）；查询是 O(N) 全表扫描，无索引加速。
- **【存疑】性能未实测**：20 万次遍历预计单次查询 < 50 ms（含两次遍历），但未在本机 Node v24 上实测。**验证方式**：T05 任务中记录 `performance.now()` 前后差值，写入验收记录；若 > 300 ms 则将默认规模下调为 `VITE_MOCK_PARCEL_COUNT=50000` 并把结论记回本文档。
- **【存疑】localStorage 容量**：只把**写操作覆盖层**（取件/异常，预估算千条）落 localStorage，不落索引层与包裹明细。覆盖层体积需实测，**若超过 2 MB 则改为仅内存**（跨标签一致性降级为「同一标签内一致」）。

### 7.6 演示场景脚本（按此顺序点，效果最完整）

> 演示时长约 8–10 分钟。**括号内为要强调的演示点**。

| 步骤 | 操作 | 展示效果 |
| ---- | ---- | ---- |
| S1 | 打开 `http://localhost:5188/` | 端选择入口页：三张卡片 + 环境说明（无后端/Mock 数据提示） |
| S2 | 进入「网页端」→ 用 `admin` 一键填充登录 | （复用一期登录页与 `request.js`，零改动跑通） |
| S3 | 看板页 | 一期 4 指标（56 员工/8 驿站/6 部门）→ 二期扩展：包裹趋势折线、驿站排行 TOP5、同步健康度、工单/SLA 卡片 |
| S4 | 包裹管理 → 驿站「城东驿站」+ 状态「在库待取」+ 近 7 天 → 翻到第 3 页 | （20 万量级下组合筛选 + 分页的响应表现）；打开详情抽屉看脱敏后的收件人信息 |
| S5 | 同步任务 → 找到 1 条「失败」任务（城东驿站 09-16 批次）→ 看日志 → 点「重试」 | （状态机演示：失败 → 待领取 → 执行中 → 成功；`retry_count+1`；日志追加） |
| S6 | 工单管理 → 切「超 SLA」Tab → 打开 `WO-20260916-0042`（客户投诉·城东驿站）→ 指派给站长 → 状态转「处理中」 | （四态流转演示 + SLA 倒计时高亮） |
| S7 | 通知中心 | 看到 S6 自动生成的「工单指派」通知（**跨页面联动**，证明是同一份数据而非静态假数据） |
| S8 | 新开标签 → `mobile.html` → DevTools 切 iPhone 14 Pro → 一键登录「老板」 | （端选择 + 设备模拟；说明真实场景为安卓 WebView 加载同一 `mobile.html`） |
| S9 | 老板端：经营总览 → 包裹趋势（切 30 天）→ 驿站排行（切换排序）→ 异常预警 | 超 48h 未取件 TOP10、同步失败驿站、超 SLA 工单三组预警 |
| S10 | 「我的」→ 切换身份为「城东驿站站长」 | （角色切换：数据范围从全局收敛为本站；`station_id` 强制过滤生效） |
| S11 | 工作台 → 本站包裹 → 取件核销 → 输入演示运单号 → 确认取件 | （写操作：状态 在库待取 → 已取件，`pickup_employee_id`/`pickup_time` 落库） |
| S12 | 工单列表 → 看到 S6 指派给自己的工单 → 处理 → 填处理记录 → 标记「已解决」→ 通知页出现新通知 | （工单闭环 + 通知二次联动） |
| S13 | 回到网页端，刷新看板与工单页 | （**三端数据一致性**：今日取件 +1、工单状态为「已解决」）——本 Demo 的核心说服点 |

**S13 的一致性原理**：三端同源（同一 `localhost:5188`），写操作经覆盖层落 `localStorage`，因此**刷新后必然一致**（P0 保证）。**【存疑】** 「不刷新即实时一致」需监听 `storage` 事件（Web Storage 官方机制，同源标签页间派发），属 P1 增强，未实测；若不实现，演示时按 S13「刷新」话术执行即可，不影响交付。

### 7.7 图表实现决策

**结论**：趋势折线/柱状图、进度条、迷你 sparkline **手写 SVG 组件**，不引入 ECharts 等图表库。

**理由**：Demo 只需折线、柱状、进度条三种图形，ECharts 会带来数百 KB 依赖与主题适配工作量；`shared/ui/charts` 三个手写 SVG 组件约 150 行即可，且天然响应式、无依赖。**代价**：未来若需要复杂图表（多轴、地图、下钻），需替换为图表库。**`TODO(扩展): 若看板需复杂图表（地图/多轴/联动），替换 shared/ui/charts 为 ECharts 适配层，调用方接口保持不变。`**

---

## 8. 安卓 H5 壳方案

### 8.1 决策：WebView 壳为「薄壳」，业务全在 H5

**结论**：安卓侧只承担三件事：**加载 H5、提供原生能力桥接、处理返回键与状态栏**。不做原生页面、不做原生缓存、不做离线包管理（Demo 阶段）。

**理由**：
- 三期规划（plan.md 4）移动端选型尚未定稿，Demo 阶段的壳只验证「H5 跑在 WebView 里体验是否可用」这一核心问题；把壳做厚会把未定稿的选型提前固化。
- 薄壳意味着 H5 迭代不需要重新发版（Demo 阶段直接改 H5 刷新即可），迭代速度优先。

### 8.2 桥接通信约定

| 方向 | 全局对象 | 方法 / 约定 | 说明 |
| ---- | ---- | ---- | ---- |
| H5 → 原生 | `window.HrmBridge`（原生通过 `addJavascriptInterface` 注入） | `getDeviceInfo(): string` | 返回 JSON 字符串 `{platform, appVersion, screenWidth, screenHeight, statusBarHeight}` |
| | | `setStatusBarStyle(json: string)` | `{"dark":true}` 控制状态栏图标深浅 |
| | | `toast(text: string)` | 原生 Toast（H5 弹层被 WebView 遮挡时兜底） |
| | | `close()` | 关闭当前 Activity |
| 原生 → H5 | `window.HrmShell`（H5 在 App 挂载时注册） | `onBackPressed(): boolean` | 返回 `true` 表示 H5 已消费返回；`false` 表示允许原生退出 |
| | | `onResume()` | 页面恢复（刷新未读计数等） |
| | | `setStatusBarHeight(px: number)` | 注入状态栏高度，H5 写入 CSS 变量 `--status-bar-height` |

**命名理由**：
- 用 `HrmBridge` / `HrmShell` 而非通用的 `Android` / `JSBridge`：避免与第三方 SDK 或系统注入对象重名（`addJavascriptInterface` 的注入名是全局的，重名会直接覆盖）。
- 两个对象名对称（`Bridge` = 原生能力的桥；`Shell` = 壳回调的入口），一眼能分辨调用方向。

**调用细节约定（Android 侧）**：
- 所有 H5 → 原生的方法**入参一律用字符串**（`addJavascriptInterface` 只可靠支持基本类型），复杂对象走 JSON 字符串。**理由**：`addJavascriptInterface` 传对象/数组在部分版本行为不稳定，字符串是唯一稳妥通道。
- 所有原生 → H5 的调用统一走 `webView.evaluateJavascript(...)`（异步），**不使用** `loadUrl("javascript:...")`。**理由**：`loadUrl` 方式无返回值、无法拿到 `onBackPressed` 的布尔结果，且会污染历史栈。
- 安全：`addJavascriptInterface` 只注入**必要且无副作用**的方法；绝不在桥上暴露文件系统读写、任意 URL 加载等能力。**理由**：WebView 的 JS 桥是安卓侧最大攻击面，一旦 H5 被注入脚本即可越权调用原生。

**返回键链路**：

```text
用户按返回键
  → MainActivity 的 OnBackPressedCallback 拦截（不调用 super）
  → webView.evaluateJavascript("window.HrmShell && window.HrmShell.onBackPressed()")
  → H5 判断：有可返回路由 → history.back() 并回调 true
             已在首页    → 回调 false
  → 原生 ValueCallback 收到 true  → 什么都不做（H5 已处理）
                    收到 false → 再按一次退出：首次提示 Toast「再按一次退出应用」，
                                1.5s 内二次按下才 finish()
```

**状态栏链路**：`WindowCompat.setDecorFitsSystemWindows(window, false)` → 通过 `ViewCompat.setOnApplyWindowInsetsListener` 取 `WindowInsetsCompat` 的 `systemBars().top` 与 `displayCutout()` → 计算安全区顶部高度 → `evaluateJavascript("window.HrmShell.setStatusBarHeight(<px>)")`。H5 侧把该值写入 CSS 变量 `--status-bar-height`，供 NavBar 与页面顶部内边距使用。

**【存疑】** 上述 WindowInsets API 的具体调用方式与 `displayCutout` 在刘海屏上的取值为安卓平台细节，本机无 SDK 无法验证。**验证方式**：在具备 Android SDK 的环境中，用一台带刘海的真机或模拟器运行，确认状态栏高度注入值合理（大于 0 且与实测一致）。

### 8.3 工程骨架目录

```text
hrm-dev/hrm-android-shell/
├── README.md                          # 工程说明 + 桥接约定速查
├── BUILD.md                           # 构建步骤（本机无法执行，待环境补齐后验证）
├── settings.gradle                    # include ':app'
├── build.gradle                       # 顶层构建脚本（AGP 版本占位）
├── gradle.properties                  # JVM 参数、AndroidX 开关
├── gradle/wrapper/gradle-wrapper.properties   # Gradle 版本占位
├── local.properties.example           # sdk.dir 占位（真实 local.properties 必须 gitignore）
├── .gitignore                         # local.properties / build/ / *.jks / assets/h5/*
└── app/
    ├── build.gradle                   # compileSdk/minSdk/targetSdk、BuildConfig.H5_URL
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml        # INTERNET 权限 + networkSecurityConfig + Activity 声明
        ├── java/com/example/hrmwebview/
        │   ├── MainActivity.java      # WebView 初始化、返回键、状态栏、JS 桥注入
        │   └── HrmJsBridge.java       # @JavascriptInterface 方法集合（与 H5 约定一一对应）
        ├── assets/h5/                 # 离线包放置位（构建产物拷贝，gitignore）
        └── res/
            ├── layout/activity_main.xml      # WebView 容器
            ├── xml/network_security_config.xml # 仅调试域名允许明文，其余强制 HTTPS
            └── values/{strings.xml, themes.xml}
```

**占位约定**：包名统一用 `com.example.hrmwebview`（`com.example` 为安卓官方保留的示例域，不含真实公司域名）；`H5_URL` 通过 `buildConfigField` 注入，调试值形如 `http://10.0.2.2:5188/mobile.html`（`10.0.2.2` 是安卓模拟器访问宿主机的约定地址，非真实 IP），生产值由构建参数传入、不入库。

### 8.4 无 Android SDK 环境下的交付边界

| 类别 | 内容 | 状态 |
| ---- | ---- | ---- |
| **交付** | 完整工程骨架目录、`MainActivity` / `HrmJsBridge` 逻辑代码、清单/布局/网络安全配置、`README.md` + `BUILD.md`、`.gitignore` | 交付（代码按约定编写） |
| **不交付** | APK、签名文件（`*.jks`）、真实域名/IP、`local.properties` | 明确不做 |
| **未验证（必须声明）** | ① 工程能否编译通过；② WebView 加载与 JS 桥是否按约定通信；③ 返回键链路与状态栏注入实际效果；④ AGP/Gradle/JDK 版本组合是否匹配 | **全部未验证**，需具备环境后验证 |

**BUILD.md 中记录的构建步骤（待验证）**：

1. 安装 JDK 17——AGP 8.x 要求 JDK 17。**【存疑】** 具体 AGP/Gradle/JDK 三元组版本必须查 Android 官方「Android Gradle Plugin 版本说明 / 与 Gradle 的兼容性表」确认，**禁止凭记忆写死版本号**；本设计只在骨架中留占位并注明「以官方兼容性表为准」。
2. 安装 Android SDK：Platform（对应 `compileSdk`）+ Build-Tools + Platform-Tools（含 adb）。
3. 生成 `local.properties` 写入 `sdk.dir=<本机 SDK 路径>`（不入库）。
4. `./gradlew assembleDebug` 产出调试 APK。
5. 模拟器/真机安装后，在应用内把 H5 地址指向本机开发服务器（模拟器用 `10.0.2.2`，真机用局域网址，需 `npm run dev -- --host`）。
6. **验证清单**：页面加载无白屏 → H5 调 `HrmBridge.getDeviceInfo()` 有返回 → 按返回键在二级页返回、首页二次确认退出 → 状态栏不被内容遮挡。

---

## 9. 本地运行方式

### 9.1 端口分配

| 服务 | 端口 | 说明 |
| ---- | ---- | ---- |
| 一期 PC 管理端（`hrm-admin`） | 5173 | 现有，**不动** |
| 一期后端（`hrm-server`） | 8080 | 现有，Demo 不使用 |
| **三端 Demo（`hrm-demo`）** | **5188** | 新增，单 dev server 承载 index.html / pc.html / mobile.html |
| 安卓模拟器访问宿主机 | — | 用约定地址 `10.0.2.2:5188`，不占端口 |

**为什么不把 PC 与移动端拆成两个端口**：单端口 = 同源 = 一次启动即可在同一个浏览器里同时演示三端（两个标签页），且写操作走同一个 `localStorage`，S13 的跨端一致性才成立。**代价**：PC 与移动端共享同源存储，必须避免 storage key 冲突——PC 沿用一期的 `hrm_admin_token`（不冲突），移动端使用 `hrm_demo_mobile_token` / `hrm_demo_mobile_user`。

**【存疑】** 5188 是否被本机其他进程占用未知。**验证方式**：启动后看控制台输出的实际端口；Vite 在端口被占用时会自动尝试下一个可用端口（官方文档 `server.port` 已说明），因此不会启动失败，但演示时须以控制台输出为准。若要强制固定，可加 `server.strictPort: true` 让冲突直接报错。

### 9.2 启动命令

```bash
# 1) 安装依赖（只需一次）
cd hrm-dev/hrm-demo
npm install

# 2) 启动（单条命令，默认 5188）
npm run dev
```

`package.json` 的 scripts 约定：

| 命令 | 作用 |
| ---- | ---- |
| `npm run dev` | 启动开发服务器（默认 5188，MPA 三入口） |
| `npm run dev:host` | 同上并 `--host`，供安卓真机/模拟器访问 |
| `npm run build` | 构建三端静态产物到 `dist/`（MPA 多入口产物） |
| `npm run preview` | 本地预览构建产物（验证「打包后仍无后端依赖」） |

### 9.3 一个浏览器里同时演示三端

**需要端选择入口页**——`http://localhost:5188/`（`index.html`）就是它：

```text
┌──────────────────────────────────────────────┐
│   快递驿站智汇系统 · 三端演示                    │
│   提示：全部数据为本地 Mock 演示数据，无后端依赖   │
├──────────────────────────────────────────────┤
│  ┌──────────── ┌────────────┐ ────────────┐ │
│  │ 网页端      │ │ 老板端      │ │ 员工端      │ │
│  │ PC 管理后台 │ │ 移动 · 经营 │ │ 移动 · 作业 │ │
│  │ → /pc.html │ │ → mobile.html│ │ → mobile.html││
│  └────────────┘ └────────────┘ └────────────┘ │
│  推荐演示顺序 / 演示账号表 / 重置演示数据按钮      │
└──────────────────────────────────────────────┘
```

**演示方式（两种，都可）**：

| 方式 | 操作 | 适用 |
| ---- | ---- | ---- |
| A. 双标签页 | 标签 1 开 `pc.html`，标签 2 开 `mobile.html` 并在 DevTools 里切换设备工具条 | 电脑上完整演示三端联动（S13 一致性） |
| B. 设备模拟为主 | 只开 `mobile.html`，用 DevTools 的「Toggle device toolbar」选 iPhone 14 Pro / Pixel 7 预设 | 移动端细节验收（安全区、Tabbar、无限滚动） |

**移动端在桌面浏览器调试的必要配置**：Vant 官方生态提供了 `@vant/touch-emulator`（官方 npm README 明确其用途为「Using vant in desktop browsers」），需在**开发环境**引入，用于把鼠标事件模拟为触摸事件。**理由**：Vant 的部分组件（如 SwipeCell、下拉刷新）依赖触摸事件，桌面鼠标下无响应，不加这个会导致演示时「点了没反应」。生产构建不引入。

**端选择页的附加能力（P1）**：提供「重置演示数据」按钮（清空 `localStorage` 覆盖层 + 重新构建索引层），保证演示可重复进行，避免上一位演示者的取件操作污染下一位的演示。

---

## 10. 与现有代码的复用边界

### 10.1 直接复用（别名引入，`hrm-admin` 零改动）

| 资产 | 路径 | 复用理由 |
| ---- | ---- | ---- |
| HTTP 封装 | `hrm-admin/src/utils/request.js` | 已实现 Bearer 注入、`code` 分发、401 并发去重跳转、blob 错误解析——这些都是踩过坑的逻辑，重写等于重新踩坑 |
| 下载工具 | `hrm-admin/src/utils/download.js` | 文件流保存逻辑 |
| 登录态 Store | `hrm-admin/src/stores/auth.js` | token/user 持久化 + `needChangePwd` 判定，Demo 的 PC 端与一期行为完全一致 |
| 接口封装 | `hrm-admin/src/api/{auth,dashboard,employee,department,station}.js` | 一期 24 接口的调用签名，Demo 直接复用以保证契约一致 |
| 一期页面 | `hrm-admin/src/views/{login,dashboard,employee,department,station,profile}/index.vue` | 5 个可原样复用；`dashboard` 因需扩展二三期指标，Demo 另建扩展版（见 10.2） |
| 样式基座 | `hrm-admin/src/styles/index.scss` | 一期主题与全局重置 |
| 路由守卫**思路** | `hrm-admin/src/router/index.js` 的 4 条规则 | 复用「白名单 / 未登录跳转带 redirect / 强制改密锁定 / 越权重定向」这一设计，Demo 在自己工程内实现扩展版（新增 STATION_ADMIN 分支） |

> 注意：「路由守卫思路」复用的是**设计**而非文件本身。**理由**：一期守卫把非 ADMIN 一律重定向到 `/profile`，而 Demo 需要 STATION_ADMIN 能进包裹/工单页，守卫逻辑必然分叉；强行改一期守卫文件会污染一期基线。

### 10.2 必须新建

| 资产 | 位置 | 新建理由 |
| ---- | ---- | ---- |
| Demo 工程与 MPA 配置 | `hrm-demo/` | 见第 6 章 |
| Mock 引擎 + 契约 + 剧本 | `hrm-demo/src/shared/mock/**` | 全新能力 |
| PC 扩展布局 | `hrm-demo/src/pc/layout/index.vue` | 一期 `layout/index.vue` 的菜单是硬编码的（仅首页/员工/组织管理/个人中心），无法承载包裹/同步/工单/通知菜单。Demo 复刻其结构与视觉（侧边栏 #001529、菜单项样式、折叠逻辑），扩展菜单与面包屑。**代价**：Demo 后续不再自动继承一期布局的修改（属可接受的第二次实现） |
| PC 扩展看板 | `hrm-demo/src/pc/views/dashboard/index.vue` | 一期看板只有 4 个数字卡，需扩展趋势/排行/同步/工单区块 |
| PC 二三期页面与 API | `hrm-demo/src/pc/views/{parcel,sync,workOrder,notification}` + `api/*` | 全新功能 |
| PC 路由表 | `hrm-demo/src/pc/router/index.js` | 需新增路由与扩展守卫 |
| 移动端全部 | `hrm-demo/src/mobile/**` | 一期无移动端 |
| 移动端 HTTP 封装 | `hrm-demo/src/mobile/utils/http.js` | 一期 `request.js` 依赖 `Element Plus` 的 `ElMessage`，移动端不能引入桌面 UI 库。该文件与 `request.js` 同构（Bearer 注入 + `code` 分发 + 401 处理），仅把提示组件换为 Vant `showToast`/`showDialog`。**这是同一逻辑的第 2 次实现**，未触及「三次以上必须抽取」红线 |
| 安卓壳工程 | `hrm-android-shell/` | 一期无安卓工程 |

### 10.3 移动端 UI 库选型决策

**结论**：移动端使用 **Vant 4**（`vant` 最新为 4.10.2，Vue 3 版本，底层零第三方依赖，浏览器支持 Chrome ≥ 51 / iOS ≥ 10，与 Vue 3 一致）；**不使用 Element Plus**。

**Element Plus 是否适配移动端 → 不适配**。理由：

1. **官方定位就是桌面端**：Element Plus 官方描述为「基于 Vue 3，面向设计师和开发者的**桌面端**组件库」，其设计目标是中后台管理系统。
2. **缺少移动端必需组件**：没有 Tabbar（底部导航）、PullRefresh（下拉刷新）、List（无限滚动）、ActionSheet、SwipeCell（滑动操作）、NavBar（含安全区）等移动端标配；而这些正是第 5.3 节员工端页面的骨架。
3. **交互模型不匹配**：组件交互按鼠标设计（hover 态、表格式排版）。`el-table` 在窄屏不可用，只能换成自定义列表——那等于放弃 Element Plus 的价值。
4. **响应式工具不解决问题**：`el-row/el-col` 的断点与 `el-show-xs-only` 工具类只能做布局适配，无法补出上表的组件缺口。
5. **浏览器基线更宽**：Element Plus 2.5.0+ 要求 Chrome ≥ 85 / Safari ≥ 14.1，Vant 4 支持 Chrome ≥ 51 / iOS ≥ 10。移动端（尤其旧安卓机）用 Vant 的兼容面更大。

**代价**：Demo 工程同时依赖 Element Plus 与 Vant（MPA 分入口，互不影响），依赖体积上升；移动端需引入 `@vant/touch-emulator`（仅开发环境）。

**【存疑】** 社区有「Element Plus Mobile」处于预览阶段的消息（来源为技术博客，非官方文档），本设计**不作为选型依据**；如后续有官方正式版，可重新评估。**验证方式**：查 Element Plus 官方文档站与官方组织下的仓库列表，确认是否存在正式发布的移动端库。

### 10.4 依赖版本策略

**结论**：Demo 的依赖版本**与 `hrm-admin` 保持同版本线，不追新**。

| 依赖 | 版本 | 依据 |
| ---- | ---- | ---- |
| `vue` | `^3.5.13` | 与 hrm-admin 一致 |
| `vue-router` | `^4.5.0` | 与 hrm-admin 一致 |
| `pinia` | `^2.3.0` | 与 hrm-admin 一致 |
| `axios` | `^1.7.9` | 与 hrm-admin 一致（Mock 适配器依赖其 adapter 契约） |
| `element-plus` | `^2.9.3` | 与 hrm-admin 一致（PC 端复用其样式与组件） |
| `@element-plus/icons-vue` | `^2.3.1` | 与 hrm-admin 一致（一期页面使用全局注册的图标组件） |
| `vite` | `^6.0.5` | 与 hrm-admin 一致（锁定 `build.rollupOptions` 写法） |
| `@vitejs/plugin-vue` | `^5.2.1` | 与 hrm-admin 一致 |
| `sass` | `^1.83.0` | 与 hrm-admin 一致（一期页面与样式用 SCSS） |
| `vant` | 新增，取 4.x 最新稳定版 | 依据 Vant 官方文档（Vue 3 版） |
| `@vant/touch-emulator` | 新增，devDependency | 桌面浏览器调试 Vant 的触摸事件（Vant 官方生态） |

**理由**：同一仓库内两个 Vite 工程共用同版本线，避免 `node_modules` 双份大版本共存导致的行为差异（尤其 Vite 6→8 的配置项改名问题），也便于未来把 Demo 的产物直接搬进生产线时零适配。

---

## 11. 安全与规范约束（Demo 特有）

| 约束 | 落地方式 |
| ---- | ---- |
| 不提交真实凭据 | Demo 无任何真实密钥；演示密码 `demo1234` 仅存在于 Mock 数据 |
| 不提交真实 IP/域名 | 安卓壳的 H5 地址走 `buildConfigField`，仓库内只出现 `10.0.2.2` 这类约定占位地址 |
| 不提交真实业务数据 | `shared/mock` 全部数据由确定性 PRNG 生成；收件人/员工姓名取自构造的假名池，手机号形如 `138****0001` 且为分段构造，不与真实号段库或真实名单对应 |
| 敏感字段脱敏 | Mock 出参层统一走脱敏工具（手机号按 api.md 1.4），`password` 永不出参；前端拿到的就是脱敏值，与生产行为一致 |
| 越权防护 | Mock 层做**服务端式**强制校验：非 ADMIN 一律覆盖 `station_id`；工单流转校验归属；角色白名单不一致返回 403 |
| 中文注释 | 所有注释中文，写「为什么」；预留代码统一 `TODO(扩展): 说明` |
| 精简优先 | 图表手写不引库；Mock 路由按业务域拆分，不写巨型单文件；复用优先于新建 |

---

## 12. 后续开发原子任务拆解

依赖关系：`T01 → T02 → (T03/T04) → (T05/T06/T07/T08/T09) → (T10/T11/T12/T13) → (T14/T15) → T16 → T17 → T18`

| 编号 | 任务 | 依赖 | 验收标准 | 建议执行者 |
| ---- | ---- | ---- | ---- | ---- |
| T01 | 搭建 `hrm-demo` 工程骨架：`package.json`（依赖版本按 10.4）、`vite.config.js`（MPA 三入口 + `@admin`/`@` 别名 + port 5188 + `server.fs.allow` 兜底）、三个 `.html`、`.env.demo`、`README.md`（含对 hrm-admin 的依赖清单） | 无 | `npm install && npm run dev` 成功；三个入口均可访问且无控制台报错；`@admin` 别名能成功引入一期 `styles/index.scss` | 前端工程师 |
| T02 | Mock 引擎与常量：`engine.js`（adapter + 路由匹配 + 统一响应 + 延迟 + 401/403 包装）、`install.js`、`util.js`（PRNG/脱敏/分页/时间）、`constants/{role,dict,errorCode}.js`；**完成 7.3 的 V1/V2/V3 三处验证并记录结论** | T01 | 挂载到一个临时 axios 实例后：命中路由返回 `{code,message,data}`；未命中返回 404；401/403 能触发调用方错误分支；三处验证结论写入代码注释 | 前端工程师 |
| T03 | 种子数据 `db.js`：驿站 8 / 部门 6 / 员工 56（角色分布含 ADMIN、STATION_ADMIN、STAFF，`station_id` 归属正确），`login_log` 若干 | T02 | 数据量与 db.md/api.md 示例一致；`st001_admin`、`st001_staff` 归属城东驿站 | 前端工程师 |
| T04 | 一期 24 接口 Mock（`routes/{auth,employee,department,station,dashboard}.js`），含错误码 1001/1002/1003/1004/2001/2002/2003/3001-3004/4001-4004/5001-5003 的关键分支 | T02, T03 | PC 端复用页面全流程可用：登录（含 1001 错误文案）→ 看板 → 员工增删改/启停/重置密码 → 部门树 CRUD 与 3002/3003 校验 → 驿站 CRUD 与 4003 校验 → 个人中心改密；全程无真实 HTTP 请求（Network 面板无 `/api` 真实请求） | 前端工程师 |
| T05 | 包裹索引层 `parcelStore.js`：TypedArray 构建（默认 20 万，可配）、确定性生成、组合筛选 + Top-K 分页、按需水合、`overlay.js` 读写 | T02 | 20 万条下：总数为 200000；同参数多次查询结果稳定；筛选 + 分页结果与总数口径一致；**记录单次查询耗时（`performance.now()`）并写入验收记录**；刷新后数据不变 | 前端工程师 |
| T06 | 包裹接口 Mock：`GET /parcels`、`GET /parcels/{id}`、`PUT /parcels/{id}/pickup`、`GET /parcels/summary`、`GET /parcels/trend`、`GET /parcels/ranking` | T05 | 非 ADMIN 请求时 `station_id` 被强制覆盖（前端传别的驿站也无效）；取件成功后 `status`/`pickup_employee_id`/`pickup_time` 变更且统计同步变化；对已在库外的状态取件返回 `7002` | 前端工程师 |
| T07 | 同步任务 Mock：列表/详情/手动触发/重试/日志；状态机四态与流转约束 | T02 | 仅「失败」可重试；重试后 `retry_count+1` 并追加日志；对「成功」任务重试返回 `6001` | 前端工程师 |
| T08 | 工单 Mock：列表/详情/新建/指派/流转；SLA 截止时间计算与超时判定 | T02 | 非法状态跳转返回 `8001`；非归属人操作返回 `8002`；`sla_deadline` 按优先级规则正确计算；超 SLA 判定排除已解决/已关闭 | 前端工程师 |
| T09 | 通知 Mock：列表/已读/全部已读/未读计数；工单指派与解决时**自动向相关人写入通知** | T08 | 指派工单后，被指派人的未读数 +1 且能看到对应通知；点击通知可跳转 `biz_type`/`biz_id` 指向的业务 | 前端工程师 |
| T10 | PC 端基座：`pc/main.js`（Element Plus + 全量图标注册 + Mock 挂载）、`App.vue`、`layout/index.vue`（扩展菜单+面包屑）、`router/index.js`（扩展守卫）、扩展版 `dashboard` | T04 | 菜单按角色渲染：ADMIN 全量；STAFF 仅个人中心；STATION_ADMIN 可见包裹/同步/工单但不可见员工/部门/驿站；5 个占位页路由可达；看板数字与 Mock 一致 | 前端工程师 |
| T11 | PC 包裹管理页 + 同步任务页（含详情抽屉、日志抽屉、CSV 导出） | T06, T07, T10 | 组合筛选/分页/详情/导出可用；同步任务可触发与重试并看到状态流转与日志追加 | 前端工程师 |
| T12 | PC 工单管理页 + 通知中心页 | T08, T09, T10 | 状态 Tab 与超 SLA 筛选正确；指派与流转可用；通知未读筛选与跳转可用 | 前端工程师 |
| T13 | 移动端基座：`mobile/main.js`、`router`（按角色分流）、`stores/auth.js`（独立 storage key）、`utils/http.js`、`utils/bridge.js`、`TabbarLayout.vue`、Vant 按需引入 + `@vant/touch-emulator`（dev） | T04 | DevTools 设备模拟下无横向滚动；底部 Tabbar 与 NavBar 正常；安全区（`--status-bar-height`）在无壳时取 0 且布局不塌陷；浏览器下 `bridge.js` 空实现不报错 | 前端工程师 |
| T14 | 移动端老板端 5 个页面（home/trend/rank/alerts/me） | T06, T13 | 全局数据（8 驿站汇总）正确；趋势可切 7/30 天；排行可切三种排序；预警三组数据非空且可下钻 | 前端工程师 |
| T15 | 移动端员工端 7 个页面（home/parcel(+detail)/pickup/workorder(+create/detail)/notification/sync/me/password） | T06, T07, T08, T09, T13 | 数据范围收敛为本站；取件核销闭环（校验→确认→状态变更）；工单四态流转可用；通知未读角标与点击跳转可用；STAFF 看不到同步状态页 | 前端工程师 |
| T16 | 端选择入口页 `index.html` + `scenario.js` 演示剧本预置 + 「重置演示数据」 | T10, T14, T15 | 三卡片跳转正确；剧本数据齐备（1 条失败同步任务、6 条超 SLA 工单、10 条超 48h 未取件、1 条指定演示运单号）；重置后数据回到初始态 | 前端工程师 |
| T17 | 安卓壳工程骨架 + `README.md` + `BUILD.md`（含构建步骤与验证清单） | 无（可与 T13 并行） | 目录结构与 8.3 一致；`MainActivity`/`HrmJsBridge` 方法名与 8.2 约定逐项对应；`.gitignore` 覆盖 `local.properties`/`build/`/`*.jks`/`assets/h5/*`；文档明确标注「未编译验证」 | 前端工程师 / 运维工程师 |
| T18 | 集成自检 + 文档回填 | T16, T17 | 按 7.6 演示脚本走通 S1–S13；`npm run build` 与 `npm run preview` 下同样可用（证明无后端依赖）；回填 `update-log.md`、`SESSION-STATE.md`；把 T05 的实测耗时与 T02 的三处验证结论写回本文档 | 测试工程师 |

**任务执行纪律**（项目规则 9.1）：编码类任务在实现前加载 `engineering-discipline`；Mock 路由与业务 handler 视为「接口/服务层」实现，按 `tdd-development` 先写断言（可用轻量断言函数，不引入测试框架）；每个任务完成后按 `code-review` 四维自检。

---

## 13. 待确认项与风险登记

### 13.1 存疑清单（必须验证，禁止当作已知事实）

| # | 存疑内容 | 影响 | 验证方式 | 归属任务 |
| ---- | ---- | ---- | ---- | ---- |
| Q1 | axios adapter 内 `config.url` 是否已合并 `baseURL` | 路由匹配错位 → 全站 404 | 首次实现打日志确认 `config` | T02 |
| Q2 | `config.data` 是否已序列化为 JSON 字符串 | POST/PUT 入参解析失败 | 同上 | T02 |
| Q3 | Vite `server.fs.allow` 默认是否覆盖 `hrm-admin` | 复用页面 403 | 启动后访问复用页面；必要时显式配置 `fs.allow` | T01 |
| Q4 | 20 万条 O(N) 查询的实际耗时 | 体验卡顿 | `performance.now()` 实测并记录 | T05 |
| Q5 | `localStorage` 覆盖层体积是否超限（约 5 MB） | 写操作丢失 | 实测序列化字节数；超限则降级为纯内存 | T05 |
| Q6 | `storage` 事件跨标签实时同步的实际效果 | S13 需刷新才一致 | 双标签实测；不通过则 S13 改为「刷新后一致」 | T16 |
| Q7 | `parcel` 唯一键与逻辑删除的语义冲突 | 二期 DDL 返工 | 二期专项设计评审决策（见 7.4.2） | 二期 |
| Q8 | 工单流转记录用 JSON 字段还是独立表 | 三期表结构返工 | 三期专项设计评审决策（见 7.4.5） | 三期 |
| Q9 | 错误码 80xx/90xx 段号 | 与三期正式定义冲突 | 三期专项设计确认（见 7.4.8） | 三期 |
| Q10 | AGP / Gradle / JDK 具体版本组合 | 安卓工程无法构建 | 查 Android 官方 AGP 与 Gradle 兼容性表；禁止凭记忆写死 | T17 |
| Q11 | WindowInsets / 刘海屏安全区取值 | 移动端顶部遮挡 | 具备 SDK 环境后用真机验证 | 后续 |
| Q12 | Service Worker 在 Android WebView `file://` 下的可用性 | 影响 Mock 方案选型 | 本设计已规避（不选 MSW）；如未来改用 MSW 需先验证 | — |
| Q13 | 5188 端口是否被占用 | 演示端口变动 | 以启动日志实际端口为准 | T01 |
| Q14 | 「Element Plus Mobile」是否存在正式版 | 移动端选型重估 | 查官方文档站与官方仓库 | 后续 |

### 13.2 风险登记

| 风险 | 等级 | 影响 | 缓解措施 |
| ---- | ---- | ---- | ---- |
| 一期代码重构导致 Demo 编译失败 | 中 | Demo 失效 | Demo 的 `README` 登记依赖清单（6 视图 + 4 模块 + api 层）；一期重构时同步检查 |
| 演示时 Demo 被当成「已实现功能」 | 高 | 需求预期错位 | 端选择页、各端顶部固定标注「演示数据」；`README` 与文档首行写明纯前端 Mock；演示开场先说明 |
| Mock 契约与未来真实后端不一致 | 中 | 接后端时页面返工 | 一期接口严格照 api.md；二三期契约标注为草案并集中登记（7.4），二期开工时先对齐契约再动 Demo |
| 20 万索引层实现的复杂度超预期 | 中 | 拖慢交付 | 以 `VITE_MOCK_PARCEL_COUNT` 为调优旋钮，必要时先以 5 万交付主流程，再补足规模 |
| 安卓壳长期处于「未验证」状态 | 中 | 移动端形态无法证实 | 交付物中显式声明未验证项；在具备环境后按 8.4 的验证清单一次性闭环 |
| 在 `main` 分支上开发 | 高 | 违反分支纪律 | 开工第一步切 `feature/三端演示Demo`（见 1.3） |

---

## 14. 架构决策记录（ADR）汇总

| # | 决策 | 结论 | 主要理由 | 代价 |
| ---- | ---- | ---- | ---- | ---- |
| A1 | 端与角色关系 | 端 = 视图，角色 = 数据范围，二者正交；老板端 = ADMIN 的移动端视图，不新增角色 | 避免移动端拆成两个 App；避免与一期已冻结的角色模型分裂 | 需在演示话术上澄清「端 ≠ 权限」 |
| A2 | 数据可见范围实现位置 | 统一在 Mock 层（未来的服务端）强制覆盖 `station_id`，前端传值不生效 | 越权防护只有服务端一处可靠；与 plan.md 3.4「`employee.station_id` 是二期可见范围基础」一致 | Demo 需多写一层校验逻辑 |
| A3 | Demo 工程形态 | 新建独立工程 `hrm-dev/hrm-demo/`，**不改** `hrm-admin` | 不污染未验收的一期基线；可整体删除、回滚成本为零；依赖可自由引入 | Demo 编译依赖一期文件路径 |
| A4 | 一期资产复用方式 | Vite 别名 `@admin` → `../hrm-admin/src`，**零拷贝零改动** | 单一实现、无漂移；`request.js`/`auth`/`api`/5 视图/样式全部原样生效 | 跨目录引入需确认 `fs.allow`（Q3） |
| A5 | 多端工程组织 | 单 Vite 工程 + MPA 三入口（index/pc/mobile） | Element Plus 与 Vant 物理隔离、按需加载；与生产两个独立产物同形 | 需配置 `build.rollupOptions.input`；锁定 Vite 6 写法 |
| A6 | Mock 实现方式 | axios 自定义 adapter，挂在 axios 实例上 | `request.js` 零改动即被拦截；`vite build` 与 `file://`（安卓壳）下同样生效 | 需自写 URL/参数解析；三处行为待验证（Q1–Q3） |
| A7 | 排除 Vite 中间件 | 不采用 | 只在 `vite dev` 生效，打包产物与 WebView 离线包中完全失效 | — |
| A8 | 排除 MSW | 不采用 | 依赖 Service Worker 与安全上下文，`file://` WebView 下未验证存在失效风险（Q12） | 失去 MSW 的语法糖与网络可视化 |
| A9 | 包裹数据规模策略 | 20 万条虚拟数据 = TypedArray 索引层（约 2 MB）+ 按需水合 + 写操作覆盖层 | 内存恒定；聚合指标真算 20 万；能暴露深度分页与组合筛选问题 | 实现复杂度 +120 行；查询 O(N) 未实测（Q4） |
| A10 | 跨端数据一致性 | 写操作经覆盖层落 `localStorage`，同源共享 → 刷新后必然一致；实时一致（`storage` 事件）为 P1 | 演示核心说服点（三端数据一致）的 P0 保证不依赖未验证能力 | 覆盖层体积受限（Q5） |
| A11 | 移动端 UI 库 | Vant 4；**不用** Element Plus | Element Plus 官方定位桌面端，缺 Tabbar/PullRefresh/List/ActionSheet/SwipeCell 等移动端骨架组件；Vant 浏览器基线更宽 | 工程同时依赖两套 UI 库 |
| A12 | 移动端 HTTP 封装 | 新建 `mobile/utils/http.js`（Vant 提示版，与 `request.js` 同构） | 移动端不能引入 Element Plus；属第 2 次实现，未越红线 | 两处 HTTP 逻辑需同步维护 |
| A13 | 移动端布局与个人中心 | 全部重写，不复用一期 PC 视图 | DOM 与交互模型差异过大，强行复用需大量断点 hack，代价高于重写 | 二期移动端也要独立维护 |
| A14 | 图表实现 | 手写 SVG（折线/柱状/进度条），不引 ECharts | 只需三种图形；省数百 KB 依赖与主题适配 | 复杂图表需替换（已留 `TODO(扩展)`） |
| A15 | 安卓壳厚度 | 薄壳：只做加载 H5 + JS 桥 + 返回键 + 状态栏 | 三期移动端选型未定稿，壳做厚会把未定决策提前固化；H5 迭代免发版 | 原生体验（启动速度、离线）不做 |
| A16 | JS 桥命名与通道 | `HrmBridge`（H5→原生，`addJavascriptInterface` 注入）/ `HrmShell`（原生→H5，`evaluateJavascript` 调用）；入参一律字符串 | 避免与第三方注入对象重名；`evaluateJavascript` 可拿到返回值，是返回键链路的必要条件 | — |
| A17 | 端口与演示入口 | 单 dev server 端口 5188 + 端选择入口页 `index.html`；PC 与移动端同源 | 一次启动演示三端；同源才能共享 `localStorage` 实现 A10 | 需避免 storage key 冲突（已用前缀隔离） |
| A18 | 依赖版本 | 与 `hrm-admin` 保持同版本线，不追新 | 避免双份大版本共存的行为差异（尤其 Vite 配置项改名） | 无法使用新版特性 |
| A19 | 不入库的接口段 | 不实现 `/api/v1/crawler/*` | 机器侧接口，非人机交互场景；Demo 只演示管理侧可见性 | 演示无法覆盖爬虫侧逻辑（已登记） |
| A20 | 名字/手机号数据构造 | 假名池 + 分段构造手机号，不引用真实号段名单 | 仓库红线：不提交真实业务数据 | 数据「看起来」不够逼真（可接受） |

---

## 15. 与仓库规范的对齐自检

| 规范项 | 本设计的落实 |
| ---- | ---- |
| 分支纪律（禁提交 main） | 1.3 前置动作：开工先切 `feature/三端演示Demo` |
| 不提交敏感信息 | 11 章约束 + 7.4/8.3 的占位约定 + `.gitignore`（`local.properties`/`*.jks`） |
| 中文注释、`TODO(扩展)` | 11 章 + 7.7 已给出 `TODO(扩展)` 示例 |
| 精简优先、同一逻辑不重复三次 | 复用优先（10.1）；仅两处按第 2 次实现处理（10.2 的 http.js 与 layout），并说明理由 |
| 数据库脚本入 `sql/schema` | 本轮无 DDL 变更；7.4 的二三期字段为**契约草案**，正式 DDL 由其专项设计产出后再入 `sql/schema/` 与 Flyway |
| 文档同步更新 | T18 要求回填 `update-log.md`、`SESSION-STATE.md` 与本文档的实测结论 |
| 智能体分派 | 12 章标注各任务「建议执行者」，实现阶段由主智能体派发并 Review |

---

> **本文档为设计阶段产出，未写任何业务代码实现，未修改 `hrm-admin` / `hrm-server` 任何文件。**
> 所有标注 **【存疑】** 的事项必须在对应任务中实测或查官方源确认后方可落地。