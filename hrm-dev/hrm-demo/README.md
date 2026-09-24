# hrm-demo · 三端演示 Demo 工程

> 快递驿站智汇系统 · 三端演示 Demo（网页端 PC + 管理端移动 + 员工端移动）
> 纯前端 + Mock 假数据，**零后端依赖**（本机无 JDK / MySQL / Redis，且不启动 `hrm-server`）
> 设计方案：`hrm-dev/docs/demo-design.md`（本工程按第 6/7/10/12 章落地）
> 已交付范围：T01–T19 + 需求 1–10 批次 + 移动端三 Tab 重构 / 同步自定义配置 / 工单复制 / 构建优化与文档治理

## 启动方式

```bash
cd hrm-dev/hrm-demo
npm install            # 本机 Node v24.19.0 / npm 11.17.0 实测通过；Node 18+ 即可
npm run dev            # vite --mode demo，端口 5188
npm run verify:mock    # Mock 契约自动校验（纯 node）
npm run verify:mobile  # 移动端页面数据链路校验（按各页面实际发出的请求参数逐个打 Mock）
npm run verify:tokens  # Element 浅色阶与官方混色算法比对（见文末「本轮实测结论」）
npm run build          # 演示态产物（含 Mock），三入口
npm run build:prod     # 生产态产物（剥离 Mock），只出 pc / mobile
npm run serve:dist     # 静态预览 dist/ 产物（双击「启动预览.ps1」走的则是本地 Node dev server）
```

> 校验脚本输出的**断言条数以实际运行结果为准**，文档不写易变数字，只写取数方法。

三个入口（同一端口，物理隔离两套 UI 库）：

| 入口     | 地址                              | 内容                                           |
| -------- | --------------------------------- | ---------------------------------------------- |
| 端选择页 | http://localhost:5188/            | 三张卡片 + 环境说明（无后端/Mock 数据提示）    |
| 网页端   | http://localhost:5188/pc.html     | Element Plus，一期资产经 `@admin` 别名只读复用 |
| 移动端   | http://localhost:5188/mobile.html | Vant 4，管理端与员工端按登录角色分流           |

演示账号统一密码 `demo1234`（**仅存在于 Mock 数据**，不是任何环境的真实凭据）：

| 账号          | 角色          | 归属驿站                            |
| ------------- | ------------- | ----------------------------------- |
| `admin`       | ADMIN         | — （网页端 + 管理端主演示账号）     |
| `admin_pwd0`  | ADMIN         | — （首登强制改密，`pwd_changed=0`） |
| `st001_admin` | STATION_ADMIN | 城东驿站（id=1）                    |
| `st001_staff` | STAFF         | 城东驿站（id=1）                    |

## 演示态 / 生产态双构建

同一套代码、两个构建目标，差别只在环境变量与 Mock 是否进包：

| 命令                 | mode       | 入口                | Mock     | 用途                          |
| -------------------- | ---------- | ------------------- | -------- | ----------------------------- |
| `npm run build`      | demo       | index + pc + mobile | 打进产物 | 交付演示（本机无后端）        |
| `npm run build:prod` | production | pc + mobile         | 整块剥离 | 接真实后端（宝塔 Nginx 反代） |

| 环境变量                | 作用                                                                                                                                                                                            | 取值                                              |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `VITE_MOCK_ENABLED`     | 是否装配 Mock 适配器。**只有显式 `true` 才装**，未配置 / 拼错一律按关闭处理（fail-safe 向生产倾斜）；`--mode demo` 下若不为 `true` 直接构建失败（快速失败优于静默走真实网络、页面空状态难排查） | `.env.demo` = `true`；`.env.production` = `false` |
| `VITE_API_BASE`         | axios 实例 baseURL。一期 `request.js` 把 baseURL 硬编码为 `/api/v1` 且不可改，Demo 在入口处用实例属性覆盖                                                                                       | 留空即相对路径 `/api/v1`；生产填 `/api/v1`        |
| `VITE_API_PROXY_TARGET` | **仅 dev 生效**：设置后才把 `/api/v1` 反代到本机后端；不设即不代理，避免与 Mock 适配器抢流量                                                                                                    | 默认不设置                                        |

演示态另有两个参数见 `.env.demo`：`VITE_DEMO_TITLE`、`VITE_MOCK_PARCEL_COUNT`（包裹索引层规模）。

## 依赖边界规则（违反即失去「可整体剥离」能力）

1. `shared/mock/**` **只允许**由 `pc/main.js` 与 `mobile/main.js` 两个入口以动态 `import()` 装配（`installMock(axiosInstance)`）；装配必须先于 `mount`，晚装首屏请求会漏到真实网络。
2. 展示层（views / components / stores / composables）**禁止直连假后端** —— 一律经 `api/*` 走 axios 实例，否则生产态会打到不存在的 Mock 通道。
3. 之所以能整块剥离，正是因为入口处用的是动态 `import().then()`：Rollup 在 `--mode production`（`VITE_MOCK_ENABLED !== 'true'`）下静态判定该分支不可达，连带整个 mock 数据层剔除。

## 404 兜底与真机调试

- 两端路由都有 `/:pathMatch(.*)*` 兜底页（`views/error/NotFound.vue`），命中未知路径不再白屏。
- `vconsole` 只在 `import.meta.env.DEV` 下动态引入，演示 / 生产产物零体积（安卓壳真机排查依赖它）。

## 硬约束（违反即回滚）

1. **`hrm-dev/hrm-admin` 与 `hrm-dev/hrm-server` 零改动**：一期未通过 D/E 验收，Demo 只通过 Vite 别名 `@admin` 只读引用，不复制源码、不加开关。删除本目录即可完全回滚。
2. 不删除 `hrm-admin/src/views/{knowledge,money,performance,permission,system}` 占位页。
3. 仓库内不落任何真实凭据 / 服务器 IP / 业务数据；`.env.demo` 只有演示参数（已在 `hrm-demo/.gitignore` 中显式放行，需随仓库提交）。

## 对 hrm-admin 的依赖清单（一期重构时的检查项）

Demo 直接编译下列一期源码文件，改动其路径会导致 Demo 编译失败：

| 一期文件                                                          | 用途                                                                         |
| ----------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `src/styles/index.scss`                                           | 全局样式基座（PC 入口引入）                                                  |
| `src/utils/request.js`                                            | HTTP 封装：Bearer 注入、`code` 分发、401 去重跳转、blob 解析                 |
| `src/utils/download.js`                                           | 文件流保存（T11 导出使用）                                                   |
| `src/stores/auth.js`                                              | 登录态持久化（`hrm_admin_token` / `hrm_admin_user`）                         |
| `src/api/{auth,dashboard,employee,department,station}.js`         | 一期 24 接口调用签名                                                         |
| `src/views/{login,employee,department,station,profile}/index.vue` | T10 复用的一期页面                                                           |
| `src/router/index.js`                                             | 仅复用「4 条守卫规则」的设计思路，Demo 自建扩展版（新增 STATION_ADMIN 分支） |

## 目录结构

```text
hrm-demo/
├── index.html / pc.html / mobile.html     # MPA 三入口
├── vite.config.js                         # 三入口 + @admin/@ 别名 + port 5188 + fs.allow + resolve.dedupe
├── .env.demo / .env.production            # 演示参数 / 生产参数（均无真实凭据）
├── 启动预览.ps1                           # 双击启动（本地 Node dev server）
├── scripts/
│   ├── verify-mock.mjs                    # Mock 契约校验（断言真源）
│   ├── verify-mobile-t13-t16.mjs          # 移动端页面数据链路校验（按页面实际请求参数打 Mock）
│   ├── gen-element-tokens.mjs             # Element 浅色阶离线生成 / 校验（只读不写）
│   └── serve.mjs                          # dist 静态预览服务（零第三方依赖，与交付包同一份）
└── src/
    ├── portal/main.js                     # 端选择页（纯 DOM，不引框架）
    ├── pc/main.js                         # 网页端入口（完整 App：Element Plus + Pinia + 路由 + Mock 装配）
    ├── mobile/main.js                     # 移动端入口（Vant 4 + TabbarLayout + 路由 + 桥接 + Mock 装配）
    ├── demo/{accounts,scenario}.js        # 演示账号与剧本（仅演示态进包）
    ├── shared/
    │   ├── styles/tokens.base.scss        # 跨端 Token 真源：L1 原始色值 + L2 共用语义 + 字体/间距/圆角/动效
    │   ├── constants/{role,dict,errorCode}.js
    │   ├── composables/useNow.js          # 「当前时间」响应式封装（两端共用）
    │   ├── domain/                        # 两端共用纯逻辑（无 UI、无副作用）
    │   │   ├── index.js                   # 统一出口
    │   │   ├── csv.js / mask.js / pagination.js / sla.js / time.js
    │   │   ├── permission.js              # 角色可见性判定
    │   │   ├── text.js                    # 复制文本（clipboard → execCommand 降级）
    │   │   └── workOrderText.js           # 工单详情 → 多行纯文本（三端共用唯一实现）
    │   └── mock/                          # 假后端：仅两个入口动态装配，生产态整块剥离
    │       ├── engine.js                  # axios 适配器（路由匹配 + 统一响应 + 401/403/404 包装）
    │       ├── install.js                 # installMock(axiosInstance) / uninstall
    │       ├── util.js                    # 确定性 PRNG、脱敏、分页、时间
    │       ├── validate.js                # 入参校验（与 api.md 字段规则逐条对齐）
    │       ├── db.js                      # 内存库：驿站/部门/员工/登录日志 + 同步任务/工单/通知 + 会话
    │       ├── persist.js / overlay.js    # 写操作持久化（localStorage，Node 回退进程内 Map）
    │       ├── parcelStore.js             # 20 万包裹 TypedArray 索引层 + 按需水合 + Top-K 分页
    │       ├── attendanceStore.js / financeStore.js / hrStore.js / kpiStore.js
    │       ├── syncConfigStore.js / syncConfigCsv.js
    │       └── routes/{index,auth,employee,department,station,dashboard,parcel,syncTask,syncConfig,syncConfigCenter,workOrder,notification,attendance,kpi,hr,finance}.js
    ├── pc/
    │   ├── api/                           # 10 个业务域接口封装（统一走一期 request 实例）
    │   ├── components/                    # 通用展示组件（StateBlock / StatusTag / SlaCountdown / 图表等）
    │   ├── composables/useLogout.js
    │   ├── config/menu.js                 # 侧边栏菜单（按角色过滤）
    │   ├── layout/index.vue               # 后台外壳（侧边栏 + 头部 + 内容区）
    │   ├── router/index.js                # history 路由 + 守卫 + 404 兜底
    │   ├── styles/{tokens,element-overrides}.scss   # 平台差异层：--el-* 覆盖 + PC 字号阶梯
    │   ├── utils/{csv,format,payrollPreview}.js
    │   └── views/                         # attendance / dashboard / employee / finance / hr / notification / onboard / parcel / schedule / sync / workOrder / error
    └── mobile/
        ├── api/index.js                   # 移动端接口封装
        ├── components/                    # Vant 封装组件（宫格 / 图表 / 状态条 / 列表 / 选择器等）
        ├── composables/{useCheckIn,useReselect}.js
        ├── constants/{tabs,accounts,quickEntries,todoGroups,makeup}.js
        ├── layout/TabbarLayout.vue        # 三 Tab 外壳（首页 / 消息 / 我的）
        ├── router/index.js                # hash 路由 + 角色分流 + 404 兜底
        ├── stores/{auth,notify,todo}.js
        ├── styles/{tokens,mobile}.scss    # 平台差异层：--van-* 覆盖 + 移动端字号阶梯
        ├── utils/{http,bridge,authStorage,attendance,workorder,format}.js
        └── views/{login,boss,staff,message,error}/   # 管理端与员工端页面
```

全仓扩展点用 `rg "TODO\(扩展\)"` 检索；新增预留代码必须带该前缀，便于定期清理。

Token 分层：`shared/styles/tokens.base.scss` 是**跨端真源**（L1 原始色值 + 两端含义一致的 L2 语义 + 字体/间距/圆角/动效等基础度量）；两端各自的 `styles/tokens.scss` 是**平台差异层**（`--el-*` / `--van-*` 覆盖、字号阶梯、组件私有尺寸），经 `@use` 引用真源，编译时真源 CSS 先输出、平台层后覆盖。

## 实测结论（本机 Node v24.19.0 / npm 11.17.0 / Vite 6.4.3 / axios 1.13.x）

### 1. 设计文档 7.3 三处待验证点（原始标注为【存疑】）

| #   | 结论                                                                                                                                                                                                                                                                                                                                                                                                               | 实测证据                                                                                                               |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| V1  | **`config.url` 不含 `baseURL`，与设计稿预期相反**。axios 1.x 的 `dispatchRequest` 不合并 baseURL，`buildFullPath` 在具体 adapter 内部执行，自定义 adapter 收到的是调用方传入的相对路径（实测 `POST /auth/login` → `url="/auth/login"`，`baseURL="/api/v1"`）。引擎因此做「去 origin → 有 baseURL/`/api/v1` 前缀则剥离 → 原样使用」的兼容归一化；**禁止无条件拼接 baseURL**（会得到 `/api/v1/api/v1/...` 全量 404） | `npm run verify:mock` 打印 `[V1]` 观测值；另断言「绝对地址 + `/api/v1` 前缀」仍能被归一化命中                          |
| V2  | **`config.data` 为 JSON 字符串**（普通对象经默认 `transformRequest` 序列化）；GET 请求为 `undefined`；**FormData 保持实例**（Excel 导入），不可无脑 `JSON.parse`。引擎按「字符串 / FormData / 对象」三路分支处理                                                                                                                                                                                                   | `[V2]` 三行观测值：`string` / `undefined` / `FormData instanceof = true`                                               |
| V3  | **adapter 返回值正常经过响应拦截器链**，`request.js` 的 `code` 分发、401 跳登录、blob 解析按原样生效。注意：自定义 adapter 需自行判定「非 2xx 是否 reject」（内置 xhr/http 由 `settle` 完成），引擎对 401/403/404 主动 reject，使 `error.response` 与 `body.code` 两条分支都被覆盖                                                                                                                                 | 校验脚本用与 `request.js` 同构的拦截器跑通：`code=200` 取 `data`、`code!=200` 进错误分支、401/403/404 同步 HTTP 状态码 |

### 2. 设计文档 6.1 的 `server.fs.allow`（Q3，A/B 实测）

- **不配置 `fs.allow`**：请求 `/@fs/D:/.../hrm-admin/src/styles/index.scss` 返回 **403**，Vite 日志原文
  `The request url "D:\...\hrm-dev\hrm-admin\src\styles\index.scss" is outside of Vite serving allow list.`
- **配置 `fs: { allow: ['..'] }`**：同一 URL 返回 **200**（1722 bytes SCSS 编译成功）。
- 原因：Vite 默认 `fs.allow` 取「工作区根」，而本仓库根无 lockfile/workspaces（lockfile 只在 `hrm-admin/` 内），探测不到跨目录的工作区根，故必须显式放行 `hrm-dev`。依据 Vite 官方《开发服务器选项》`server.fs.allow`。
- 结论：**`fs.allow` 必须显式配置**，设计文档中「很可能已覆盖」的推测不成立。

### 3. 工程与接口实测

- `npm install` 成功：100 个包，0 vulnerabilities（Vant 版本以 npm 实测为准：`vant@4.10.2`、`@vant/touch-emulator@1.5.0`）。
- `npm run dev` 启动成功（2409 ms）；`/`、`/pc.html`、`/mobile.html` 均 200；三入口模块图的静态依赖（含 `@admin/*` 与 Element Plus / Vant 预构建依赖）**全部解析成功，0 失败**。
- `@admin` 别名可用：`@admin/styles/index.scss`、`@admin/api/employee.js` 均 200 编译。
- `npm run verify:mock`：全部通过（断言条数以脚本输出为准，下同），覆盖一期 24 接口的成功路径与错误码关键分支（1001/1002/1003/1004/2001/2003/3001/3002/3003/3004/4001/4002/4003/4004/5001/5002/5003 + 401/403/404），另含「同参数多次查询结果稳定」「重置后员工数 56」两项数据一致性断言。

### 4. 二三期数据层实测（T05–T09）

- `npm run verify:mock` 全部通过，覆盖 T05–T09 验收标准；`npm run build` 同步通过（EXIT=0，无新增报错）。
- **T05 包裹索引层**：总数 200000；同参数多次查询结果稳定；组合筛选 + 分页与总数口径一致；写操作覆盖层（overlay）落 localStorage、刷新后数据不变（Node 校验脚本无 localStorage 时回退进程内 Map，语义等价）。**单次组合筛选查询耗时实测约 10–11 ms**（`performance.now()`，城东驿站 + 在库待取 + 第 3 页，20 条/页），远低于 demo-design.md 7.5.1 预设的 300 ms 阈值，默认 20 万规模无需下调（`VITE_MOCK_PARCEL_COUNT` 保留可配置）。
- **T06 包裹接口**：非 ADMIN 的 `station_id` 强制覆盖为本站（前端传别的驿站无效）；取件后 `status/pickup_employee_id/pickup_time` 变更且 `todayPickup` 同步 +1；对已取件再取件返回 7002、他人取件返回 7003、不存在返回 7001；`summary/trend/ranking` 指标齐全。
- **T07 同步任务**：240 条种子；仅「失败」可重试（`retry_count+1` 且追加日志），对「成功」任务重试返回 6001；触发（待领取 → 执行中 → 成功）状态机可用；STAFF 访问返回 403。
- **T08 工单**：120 条种子，超 SLA 恰好 6 条（判定排除已解决/已关闭）、已关闭 20 条；非法流转 0→2 返回 8001、非归属人操作返回 8002；`sla_deadline` 按优先级（低 48h / 中 24h / 高 8h）正确计算。
- **T09 通知**：60 条种子；工单指派后被指派人未读 +1 且可见 `biz_type=work_order / biz_id` 的跳转通知；解决后上报人未读 +1；已读 / 全部已读 / 未读计数可用；标记他人通知返回 9001。

### 5. 考勤与排班数据层实测（T17）

> 本轮只交付数据层（Mock 实体 + 路由 + 校验脚本），页面 UI 为后续任务。`npm run verify:mock` 全部通过；`npm run build` 同步通过（EXIT=0）。

**数据规模（本机实测，确定性种子，同一天内刷新稳定）**

| 实体                | 规模                                                                    |
| ------------------- | ----------------------------------------------------------------------- |
| `attendance_rule`   | 8 条（8 驿站各 1 条，围栏半径 300m、迟到/早退阈值各 30min、ALL 组合）   |
| `shift`             | 24 条（8 驿站 × 早/中/晚 3 班，配色 `#0958D9` / `#FA8C16` / `#722ED1`） |
| `schedule`          | 城东驿站 7 人 × 近 30 天 + 本周剩余日期，轮休错峰（每天约 1/6 人休息）  |
| `attendance_record` | **348 条**：正常 227 / 迟到 47 / 早退 55 / 校验异常 19（四种状态齐备）  |
| 今日概况（城东）    | 应到 6 / 实到 4 / 正常 1 / 迟到 3 / 早退 2 / 缺卡 2                     |

**打卡判定实测**（`attendanceStore.checkIn`，服务端位置执行）

| 验收点       | 实测结果                                                                                                                                                                                          |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 定位校验     | **Haversine 大圆距离**：纬度偏移 0.001° 实测 `distance = 111.2m`，与理论值 111.19m 一致（非经纬度差值 0.001）                                                                                     |
| 时间窗       | 上班卡 `[班次开始-30min, 班次结束]`、下班卡 `[班次开始, 班次结束+60min]`；窗外返回 `9102`                                                                                                         |
| 迟到 / 早退  | 以**班次时间**为基准：上班卡晚于「开始+阈值」→ `LATE`；下班卡早于「结束-阈值」→ `EARLY_LEAVE`；断言用脚本侧独立推算交叉验证，不依赖运行时刻；「晚班 24:00 收班 + 阈值 0」用例保证早退分支必然命中 |
| ALL / ANY    | 同一入参（WiFi 命中 + 超出 50m 围栏）在 `ALL` 下返回 `9104`、在 `ANY` 下放行且 `checkMode='WIFI'`、`locationMatched=false`                                                                        |
| 重复打卡     | `9105`；**异常卡不参与去重**（一次 WiFi 未命中的失败尝试不会把员工整天挡在门外，已断言校验失败后仍可正常打卡）                                                                                    |
| 校验未通过   | 返回 `9103`/`9104` 的同时**落一条 `ABNORMAL` 异常卡留痕**（便于 Boss 端异常预警），异常卡不计入「实到 / 正常 / 迟到 / 早退」口径                                                                  |
| 越权防护     | 非 ADMIN 的 `stationId` 一律用其 `employee.station_id` 覆盖（规则 / 班次 / 排班 / 记录 / 概况 / 打卡全线生效，与二期 parcel 同口径）；规则写接口白名单过滤 `id`/`stationId`/`updateTime`          |
| 种子一致性   | 抽查员工全部打卡记录逐条回查所在周排班矩阵，**无「无排班却有打卡记录」的矛盾数据**（记录只在有排班的日子生成，且今日只生成「时刻已到」的卡）                                                      |
| 演示入口预留 | 固定演示账号（`st001_admin`/`st001_staff`）今日**不预置打卡**，且今日班次取「上班卡时间窗覆盖当前时刻」的班次，移动端任何时段进入都能演示一次完整打卡                                             |

**与契约的两处偏差（均已标注为待确认项，未擅自改设计规范）**

1. **考勤错误码由 9001-9006 顺延至 9101-9106**。`demo-design.md` 7.4.8 已将 90xx 划给「通知」，且 `DEMO_CODE.NOTIFICATION_NOT_EXISTS = 9001` 已被已交付的通知页与校验断言依赖；同一码值无法承载两句提示（`CODE_MESSAGE` 只能保留一条），故考勤顺延到 **91xx 段**，语义与契约逐条一致。若三期专项设计确认 90xx 归考勤，需同步迁移通知码并全量回归。
2. **晚班配色 `#722ED1` 未见于 `demo-ui-redesign.md` 2.2**，且该文档明确要求「删除紫色 AI 默认风」。本轮按契约取值不改，但**需 UI/UX 设计师确认晚班配色后再定稿**（`attendanceStore.js` 中已用 `【待确认】` 标注）。

**本轮未做（已标注 `TODO(扩展)`）**

- 考勤写操作（打卡 / 排班 / 规则 / 班次）目前**只存活于当前会话**，刷新即回到种子态；若演示需要「三端刷新后写操作仍在」，参照 `overlay.js` 增加考勤覆盖层（`attendanceStore.js` 顶部已注明）。
- 跨零点班次的排班表展示、请假/加班/补卡等考勤单类型不在本轮契约内。
- T16「重置演示数据」需同时调用 `resetDb()` + `resetParcelStore()` + `resetAttendanceStore()`（考勤 store 与 db 相互独立，`db.resetDb` 不反向依赖领域 store）。

### 6. 自定义上下班时间与打卡频次（T18）

> 本轮只改数据层（Mock 实体 + 路由 + 校验脚本），页面 UI 为后续任务。`npm run verify:mock` 全部通过；`npm run build` 同步通过（EXIT=0）。

**契约落地（时段模型）**

- `attendance_rule` 新增 `checkFrequency`（仅 2 / 4）、`checkPeriods`（长度 = 频次 / 2，元素 `{ name, startTime, endTime }`）、`allowEarlyMin`、`allowLateMin`；`workStartTime` / `workEndTime` 退化为**派生值**（首段开始 / 末段结束），时段是唯一真源。
- `attendance_record` 新增 `periodIndex` / `periodName`；`checkType = ON/OFF` 语义变为「**该时段内**的上/下班卡」。
- `POST /attendance/check-in` 新增 `periodIndex`：时间窗 = `[时段开始 - allowEarlyMin, 时段结束 + allowLateMin]`，迟到 = `ON` 且晚于「时段开始 + lateThresholdMin」，早退 = `OFF` 且早于「时段结束 - earlyLeaveThresholdMin」，去重键含 `periodIndex`；索引越界 / 非整数 / 负值统一回 `9107`。
- `PUT /attendance/rule` 白名单新增这 4 个字段；`checkFrequency` 非 2/4、时段数与频次不匹配、单段起止倒置、段间重叠或乱序均回 `9107`（`allowEarlyMin/allowLateMin` 为负仍回 `400`，与既有阈值字段同口径）。
- `GET /attendance/status` 按时段展开：新增 `checkFrequency`、`requireSummary`（含频次）、`periods: [{ periodIndex, name, startTime, endTime, windowStart, windowEnd, onChecked, offChecked, onTime, offTime }]`；`GET /attendance/records` 记录含 `periodName`。

**种子数据**（确定性 PRNG，同一天内刷新稳定）

| 项           | 实测                                                                                                                                           |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 规则频次     | 城西 / 城南 = **4**（上午班 08:00-12:00 + 下午班 14:00-18:00），其余 6 站 = **2**（全天班 08:00-18:00）；每条规则时段数 = `checkFrequency / 2` |
| 城东打卡记录 | **349 条**（正常 232 / 迟到 45 / 早退 53 / 校验异常 19，四种状态齐备；条数随运行时刻「今日时刻已到的卡」略有浮动，历史部分与 T17 同口径）      |
| 记录时段字段 | 城东规则频次为 2，记录统一 `periodIndex = 0`、`periodName = 全天班`，与规则声明一致                                                            |
| 时段切分口径 | 按**排班班次**均分（频次 2 → 整班 1 段，4 → 上下半段各一对卡），避免出现「排的是晚班、卡却打在上下午」的矛盾数据                               |

**兼容性设计与偏差（均已在代码内显式标注）**

1. `periodIndex` **可缺省**：缺省走历史「单班次模型」（以排班班次为时间基准），传入才走时段模型。原因：已交付的移动端打卡页尚未接入时段配置，而本轮明确不改页面，若强制要求 `periodIndex` 会让现有打卡页当场打不了卡。`attendanceStore.checkIn` 已标 `TODO(扩展)`，页面接入 `periods` 后删除旧路径。
2. `9107` **一码两语义**：打卡索引越界（默认文案「打卡时段不存在」）与保存规则时时段配置非法（handler 显式传 message）同码——两者都是「时段」维度问题，前端按调用接口即可区分，不再额外占码。
3. **旧客户端兼容**：管理端规则页当前只发 `workStartTime` / `workEndTime`，保存时会映射到首 / 末时段，保证「改了时间就生效」且两个口径不打架；页面支持多时段编辑后删除该映射（代码内 `TODO(扩展)`）。
4. 时段校验额外要求「**按开始时间升序**」（契约只写了不重叠）：`periodIndex` 是记录归属与打卡去重的定位键，乱序会让「第 1 段」指向下午；跨零点夜班时段（如 20:00-24:00 + 00:00-04:00）需后续放宽本约束并同步改造时段定位（已标 `TODO(扩展)`）。

**T18 新增断言覆盖**

- `checkFrequency` 2 / 4 两种配置、时段数与频次一致性、种子 ≥ 2 个双时段驿站。
- `checkPeriods` 长度 / 起止倒置 / 重叠 / 乱序 / 空名称 / 非数组 → `9107`；`allowEarlyMin` 为负 → `400`。
- `workStartTime` / `workEndTime` 保存后与时段首尾同步（派生值不打架）。
- 按时段的时间窗 `9102`（排班班次时间窗覆盖当前时刻时，仍按规则时段判定）、`periodIndex` 越界 / 非整数 / 负值 → `9107`。
- 按时段的迟到 / 早退（脚本侧独立推算交叉验证，不依赖运行时刻）、同段同类型重复 → `9105`、不同时段同类型可各打一次。
- `/attendance/status` 按时段展开（频次 4 → 2 个时段，字段齐全、索引 0 起递增、时间窗按余量推导、按段标记 `onChecked`）。
- 打卡记录与历史种子的 `periodIndex` / `periodName` 校验、旧客户端上下班时间兼容路径、用例后恢复种子规则。

### 7. 补卡申请与审批 + 工单转单（T19）

> 本轮只做数据层（Mock 实体 + 路由 + 校验脚本），**未写任何页面 UI**。`npm run verify:mock` 全部通过；`npm run build` 同步通过（EXIT=0）。

**补卡申请（`makeup_apply`）**

| 项               | 落地结果                                                                                                                                                                                                                                                     |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 接口             | `POST /attendance/makeup`（员工本人）、`GET /attendance/makeup/my`（本人，分页 + 状态筛选）、`GET /attendance/makeup/list`（**仅 ADMIN**，可筛状态 / 驿站 / 日期）、`POST /attendance/makeup/:id/approve`（**仅 ADMIN**，`approved` 布尔 + `approveRemark`） |
| 审批权           | 仅 `ADMIN`（管理员）：`STATION_ADMIN`（站长）访问列表与审批**均返回 403**（沿用 engine 的角色校验，与既有越权口径一致）                                                                                                                                        |
| 错误码           | 新增 `9108`（该时段当日已有补卡申请或已正常打卡）、`9109`（补卡申请状态不允许该操作）；时段不存在仍回 `9107`、无规则回 `9101`、申请不存在回 `404`                                                                                                            |
| 重复校验         | 同一 `employeeId + workDate + periodIndex + checkType` 已有 `PENDING` / `APPROVED` 申请 → `9108`；该槽位当日**已有正常卡** → `9108`（同码两语义：都不该再补）；被驳回的记录可重新提交                                                                        |
| 审批通过         | 写入一条 `attendance_record`：`checkType` / `periodIndex` / `periodName` 与申请一致、`status='NORMAL'`，**打卡时间取该时段规定时间**（上班卡取时段开始、下班卡取时段结束，`24:00` 收班自动进位到次日 00:00）                                                 |
| 打卡记录新增字段 | `source`：`'NORMAL'` 正常打卡 / `'MAKEUP'` 补卡补录（列表与详情均下发，前端可直接区分展示）                                                                                                                                                                  |
| 幂等与一致性     | 申请到审批期间本人又正常打了卡 → 同槽位不再重复补录（避免一个槽位两条正常卡）；时段配置变更导致原时段消失时审批直接回 `9101`，不落下「审批通过却无打卡记录」                                                                                                 |

**补卡记录为什么不写校验项**：补卡不是设备打卡产生的，`checkMode` / `wifiSsid` / `wifiMatched` / `longitude` / `latitude` / `distance` / `locationMatched` 统一置 `null`（不伪造「命中」值），`remark = '补卡通过（系统补录）'`，前端按 `source='MAKEUP'` 分支渲染即可。

**补卡种子（确定性 PRNG，同一天内刷新稳定）**：16 条 = 待审批 **8** / 已通过 **5** / 已驳回 **3**，分布在 **城东、城西、城南、城北、高新** 5 个驿站，上班卡与下班卡兼具，时段按各站规则取值（双时段驿站会出现 `periodIndex = 1`）。

- 已通过的 5 条**逐条**回查得到 `source='MAKEUP'`、`status='NORMAL'` 的打卡记录（脚本逐条断言，不放行「审批通过却无打卡记录」）。
- 补卡样本只在「当日有排班 + 槽位既无有效卡、也无在途申请」的地方生成；城东逐条回查排班矩阵，**无「无排班却有补卡」**。
- 演示账号 `st001_admin` / `st001_staff` 不参与补卡种子，今日打卡演示入口不受历史申请干扰。

**工单转单**

| 项                          | 落地结果                                                                                                                                                                                        |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 接口                        | `POST /work-orders/:id/transfer`，入参 `{ toEmployeeId, reason }`；工单不存在 → `404`                                                                                                           |
| 新增留痕结构                | `work_order_transfer`：`id, workOrderId, fromEmployeeId, fromEmployeeName, toEmployeeId, toEmployeeName, reason, operatorId, operatorName, transferTime`                                        |
| 权限矩阵（不满足 → `8003`） | `ADMIN` 任意工单；`STATION_ADMIN` 仅本站工单；`STAFF` 仅「当前处理人 = 自己」的工单                                                                                                             |
| 转单对象（不满足 → `8004`） | 必须存在、未删除、`status = 1`；站长与处理人只能转**本站**员工（`ADMIN` 可跨站）；不能转给操作人自己                                                                                            |
| 转单结果                    | **工单状态不变**，只改处理人；追加 `work_order_transfer` 留痕 + 时间线 `action='transfer'` 事件（含转出人 / 转入人 / 理由 / 操作人）；向**新处理人**推送一条 `biz_type='work_order'` 的未读通知 |
| 详情结构                    | `GET /work-orders/:id` 额外返回 `transfers`（按 `transferTime` 倒序）；列表接口不带（避免 120 条列表多一份嵌套数据）                                                                            |

**转单种子**：**8 条工单**含转单留痕（跨站 1 条：城西 → 城南，由管理员发起；其余 7 条为站内转单，其中 2 条为「处理人本人转单」、5 条为站长转单）。种子里工单的**当前处理人 = 最后一次转单的转入人**，时间线含 `transfer` 事件，杜绝「转给了 A 却挂在 B 名下」。

**与契约的偏差 / 待确认（均已写在代码注释内）**

1. **路径沿用 `/work-orders/{id}/transfer`**：契约原文写作 `POST /workorders/{id}/transfer`，但本工程既有 5 条工单路由统一为 `/work-orders`，PC / 移动端 api 封装也按此前缀调用；同一资源不宜出现两套命名，故转单继续用连字符写法（`workOrder.js` 头部已注明）。若三期正式接口定为 `workorders`，需同步改路由与两端 api 封装。
2. **补卡日期不得晚于今天**、**补卡理由 2–200 字**、**转单理由 2–100 字**：契约只写了「必填 / 长度校验」，具体边界是本轮按业务常识加的护栏；正式契约给出不同阈值时只改路由层校验即可（store 不重复校验）。
3. **其他驿站的排班补位**：T17 排班种子只铺城东，为保证其他驿站的补卡样本同样满足「当日有排班」，生成样本时就地补一条排班（`ensureScheduleFor`，已标 `TODO(扩展)`：排班种子覆盖全部驿站后删除）。
4. **转单通知复用 `type = 1`（工单指派语义）+ `title = '工单转单'`**：通知类型字典（1 指派 / 2 流转 / 3 同步 / 4 公告）未定义「转单」，暂借指派类；若三期新增类型码需同步迁移。
5. **转单写操作只存活于当前会话**，刷新回到种子态（与考勤写操作同口径，`TODO(扩展)` 指向 `overlay.js` 覆盖层）。

**T19 新增断言覆盖**

- 补卡：种子三类状态 / 多驿站分布 / 字段齐全 / 状态与驿站筛选 / 参数越界 `400` 与时段越界 `9107`；员工端「我的」只含本人；提交成功、重复申请 `9108`、已有正常打卡 `9108`；非 ADMIN 列表与审批 `403`；审批通过（状态 / 审批人 / 备注）并生成 `source='MAKEUP'` 记录、打卡时间取规定时间、不伪造校验项；重复审批 `9109`、申请不存在 `404`；驳回分支（不生成记录 + 驳回后可重新提交）；已通过补卡逐条回查打卡记录、城东补卡逐条回查排班。
- 转单：种子 ≥8 条工单留痕 / 含跨站 / 字段齐全 / 理由非空；详情 `transfers` 倒序、与当前处理人自洽、时间线含 `transfer`；ADMIN 跨站转单成功且**状态不变**；站长转本站成功、转外站 `8004`、转他站工单 `8003`；STAFF 转非本人处理工单 `8003`、转本人处理工单成功；转给自己 `8004`、对象不存在 `8004`、对象已停用 `8004`；转单理由缺失 `400`、工单不存在 `404`；新处理人收到未读通知；运行时留痕持久化到详情。

## Mock 实现约定（与真实后端的差异，均已显式标注）

| 项                       | Demo 做法                                                                                                   | 原因 / TODO                                                                                                             |
| ------------------------ | ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 密码                     | 明文占位 `demo1234`，不做 BCrypt                                                                            | 前端无法复现后端散列成本，演示无需安全性；真实环境散列在后端                                                            |
| 会话                     | `db.sessions: Map<employeeId, jti>` + `mock.{id}.{jti}` 令牌，会话快照落 localStorage（`sessionStore.js`）  | 用于演示互踢与「禁用/删除/重置密码/改密即强制下线」的 401；落盘是为了让「刷新不丢登录态」与 token/user 的持久化口径一致 |
| 导入模板 / 导出          | 返回 **CSV** 文件流（列定义与 api.md 第 5/6 章一致，手机号完整输出）                                        | Mock 无法生成真实 xlsx；`TODO(扩展): 需要真实 xlsx 时引入 SheetJS 写入/解析`                                            |
| Excel 导入校验           | 非 `.xlsx`/空 → 5001；>10MB → 5002；文件名含「错误/error」→ 5003（附 2 条示例明细）；其余按体积推导 `total` | 无法解析 xlsx 二进制，行级规则用可复现的代理规则覆盖；真实行校验在后端                                                  |
| `2002` 最后管理员保护    | 已实现并按 api.md 语义判定（变更后活跃管理员为 0 时拒绝）                                                   | 单会话流程下调用方自身即活跃管理员，该分支实为并发场景防御；代码内已注明可复现路径                                      |
| 二三期错误码             | 60xx/70xx 为 plan.md 已定段位；80xx/90xx 为 Demo 提案                                                       | 80xx/90xx 待三期专项设计确认（demo-design.md 7.4.8）                                                                    |
| 文件体积上限外的字段校验 | `parcel` 等二三期实体字段取 demo-design.md 7.4 的 Demo 契约草案                                             | 正式 DDL 由二期/三期专项设计评审定稿                                                                                    |

## 与设计文档的偏差记录

1. **`build.rollupOptions` 写法保留**：实测使用 `vite@6.4.3`（`^6.0.5` 同版本线），`rollupOptions` 有效，无需 `rolldownOptions`。
2. **`.env.demo` 的 git 可见性**：仓库根 `.gitignore` 的 `.env.*` 规则会吞掉该文件，已在 `hrm-demo/.gitignore` 中用 `!.env.demo` 显式放行，未改动根 `.gitignore`。
3. **T04 的 PC 全流程验收**：接口层与 Mock 契约已完成并由校验脚本证明；PC 基座（T10）建好后的页面全流程已由浏览器走查覆盖（15 个 PC 路由页全部渲染，见「浏览器实测」一节）。T04 当时 `pc.html` 还是 T01 骨架自检页，已用 `@admin` 的真实 `request.js` + `api/*` 跑通登录、看板、员工分页、部门树、驿站列表与 1001/404 错误分支。

## 需求 1–10 批次实测结论

> 范围：同步任务模块化 / 按驿站排班 / 新建工单+企微预留 / 发布通知 / 考勤导出 / 超时未处理 / KPI 考核 / 人事管理 / 财务管理工资单 / 入离职流程。

- `npm run verify:mock`：全部通过（断言条数以脚本输出为准），覆盖需求 1–10 的正常流 + 错误码 + STAFF / STATION_ADMIN / ADMIN 三类越权交叉验证
- `npm run build`：EXIT=0，三入口产物齐全
- 错误码新增分段：**92xx KPI / 93xx 人事（含入离职）/ 94xx 财务**（`errorCode.js` 为单一真源）
- 「超 SLA」文案全量替换为「**超时未处理**」（含无障碍播报文案）；工单派生字段统一 `overdueUnhandled`，`overSla` 仅作旧参数别名保留（标 `TODO(扩展)` 待删除）
- **规则驱动验证（需求 9 核心）**：改规则项金额 200→500 后重新生成，净额 +300；关闭该项开关，净额 −500。金额随配置变化，未写死任何薪资公式
- 工资单状态机全链路：`DRAFT → PENDING_APPROVAL → APPROVED/REJECTED → PUBLISHED → CONFIRMED`，非法流转 9403、员工查他人 9404、重复生成已发布月份 9405
- KPI 权重合计 ≠ 100% 时保存被拒（9202）；入离职未走完全流程不启用账号（登录 1002 拦截），离职结算单号与工资单三方自洽
- 人事档案脱敏：出参无 15 位以上连续数字，银行卡形如 `**** **** **** *** 2020`
- `POST /schedules/batch-by-station` 实际返回 `{ created, skipped, total }`（`total = created + skipped`；`skipExisting` 默认 `true`）

**契约缺陷修正 4 处（本轮发现并修复）**

| #   | 问题                                                                                    | 修法                                                                    |
| --- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1   | `GET /work-orders/dispatch-rules` 未加 `roles`，而 `PUT` 加了 → 读写口径不一致          | 读也限定 `ADMIN`（规则含关键词/类型/优先级/默认处理人，属企微接入配置） |
| 2   | `GET /kpi/scores/:employeeId` 读的是查询串而非路径参数 → 恒回 400，KPI 明细页不可用     | 改读路径参数并兼容查询串                                                |
| 3   | `GET /finance/payroll-rules/:id` 不存在时回 404，而 `PUT/DELETE` 回 9401 → 同资源两套码 | 统一为 9401                                                             |
| 4   | 入职 `CREATE_ACCOUNT` 只建档案不建定薪行 → 「定薪」步骤恒回 9305，流程卡死不可达        | 建档时同步落一条 0 值定薪行                                             |

**新增契约端点 1 个**：`PUT /kpi/metrics/batch`。原因：单指标 `PUT /kpi/metrics/:id` 每次校验「启用指标合计 = 100%」，而调权重/切换启用天生存在中间态（如 30/20 → 40/10），任何中间态都不等于 100%，逐条提交必然失败，需求 7 的「权重合计条 + 保存」在该契约下无法实现。批量端点把整组变更合成一次原子校验。已标 `TODO(扩展): 后端正式定稿时并入 KPI 指标服务或改为模板整体提交`。

## 构建配置关键陷阱：`@admin` 复用导致依赖双副本（曾致 PC 端整体白屏）

**症状**：打开 `pc.html` 白屏，控制台报 `TypeError: Cannot read properties of undefined (reading '_s')`，堆栈落在 `main.js` 的 `useAuthStore().$subscribe(...)` 调用处。

**根因**：`@admin` 别名指向 `../hrm-admin/src`，而 `hrm-admin` 自带 `node_modules`，`pinia` / `vue` / `axios` 各有一份物理副本（pinia 版本同为 2.3.1，但是两个独立模块实例）。Rollup 按 importer 所在目录解析，于是：

- `hrm-demo/src/pc/main.js` 的 `createPinia()` + `app.use(pinia)` 设置的是 **A 副本**的 `activePinia`
- `@admin/stores/auth.js` 的 `defineStore()` 读的是 **B 副本**的 `activePinia`（恒为 `undefined`）→ `pinia._s.has(id)` 抛错

**实测证据**：去重前 `dist/assets` 中 pinia 内部的 `_s.get(` **同时出现在 `pc-*.js` 与 `preload-helper-*.js` 两个 chunk**；去重后仅剩 1 个文件，构建模块数由 **3977 降至 2318**（减少 1659 个重复模块）。

**修法**：`vite.config.js` 的 `resolve.dedupe` 强制统一从本项目 `node_modules` 解析唯一副本：

```js
dedupe: ['vue', 'vue-router', 'pinia', 'axios', 'element-plus', '@element-plus/icons-vue', 'vant']
```

**为什么必须记下来**：这是「跨项目源码别名复用」的固有陷阱，且 **dev 与 build 下都会崩**。此前只跑 `npm run build` 与契约断言，编译通过但页面根本起不来 —— 编译绿灯不等于能跑。任何新增 `@admin` 复用面的改动都要先确认该项仍在。

---

## 浏览器实测（首轮真实走查）

Chrome 扩展不可用（`os error 10061`），改用**内置浏览器**完成首轮真实走查（此前 UI 从未被人打开过）：

| 项                           | 结果                                                                                   |
| ---------------------------- | -------------------------------------------------------------------------------------- |
| PC 登录页                    | ✅ 正常渲染（非白屏），`admin` / `demo1234` 登录成功 → `/dashboard`                    |
| 控制台错误 / 404             | ✅ 干净加载下 console 为空，无 404                                                     |
| 侧边栏                       | ✅ 4 组 + 1 顶层，与设计一致（组织人事 6 / 考勤薪酬 3 / 包裹作业 4 / 系统 1）          |
| 15 个 PC 路由页              | ✅ 全部渲染，无白屏、无报错                                                            |
| 排班「批量工具」             | ✅ 下拉含一键铺排 / 复制上一周 / 整行 / 整列 / 清空本周                                |
| 工单「新建工单」「自动派单」 | ✅ 新建为 `el-dialog`；自动派单为 `el-drawer`，含整片浅黄「模拟演示」区与 5 条派单规则 |
| 移动端                       | ✅ Tabbar 5 项、首页宫格、我的工资单（￥7,532）、我的 KPI（69.5 分 / 第 42 名）均正常  |

**未覆盖（受工具限制）**：内置浏览器窗口固定 **810×658**，无法在 ≥1280px 的典型 PC 宽度与真实手机竖屏宽度下验证布局与响应式；未验证站长账号落地页；移动端未逐一点开其余 Tab 内容页。

**窄窗下观察到的现象（需宽屏复测后判定是否为真实缺陷）**：810px 时 PC 侧边栏自动折叠为纯图标；排班页头信息折成 3 行且「已排 43 / 49 格」被下拉浮层遮挡。

---

## 移动端底部导航重构：6 项 → 3 项（首页 / 消息 / 我的）

设计规范见 [`docs/demo-mobile-nav-redesign.md`](../docs/demo-mobile-nav-redesign.md)（723 行，95 条入口迁移映射逐条带 `文件:行号`）。

| 能力             | 落点                                                                                                                      |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 底部导航三 Tab   | `mobile/constants/tabs.js`；管理端 `/boss/home\|message\|me`，员工端 `/staff/home\|message\|me`                           |
| 首页快捷功能宫格 | `HomeQuickGrid` + `QuickGridItem`，4 列 × 2 行；管理端 8 项（6 计数 + 2 状态）、员工端 8 项（3 计数 + 3 状态 + 2 纯入口） |
| 首页顶部状态区   | 员工端 `AttendanceStatusBar`（今日出勤 + 一键打卡）；管理端经营概览                                                       |
| 消息 Tab         | 通知子视图（既有通知中心整体升格，读/未读/跳转逻辑零改动）+ 待办子视图（管理员 5 类 / 员工 3 类）                           |
| 我的 Tab         | 我的数据（员工）/ 管理与配置（管理员）；**待办队列已移出**，统一收进消息 Tab                                                |

**宫格实时数据三形态（互斥）**：计数型走角标、状态型走名称下方第二行小字、纯入口型无数据。硬规则：**绝不用 `0` 表示「加载失败/未知」**（`0` 是「没有待办」这一结论）；单项失败独立降级不拖垮整页；不显示缓存的旧值。

**Vant 行为实测结论（逐一核对了 Vant 源码，非凭记忆）**

- `van-tabbar-item` 重复点击当前项**不触发 `change`**（`TabbarItem.mjs` 有 `if (!active.value)` 守卫）→ 改用 `@click` 比对 `route.path`
- Vant **不输出 `aria-current`**，也不处理 Enter 键 → 均显式补上
- `van-grid-item` 会吞掉外部 attrs（`aria-label` 落到不可聚焦的外层）→ 宫格改用默认插槽 + 原生 `router-link` + 自绘角标

**本轮修掉的真缺陷**：管理端「今日取件」恒为 `0`、趋势 `↓100.0%`。根因是**双重的**——① 取件时间派生为「入库 + 2~72h」，必然早于今日零点；② **口径分裂**：`parcelSummary` 只认 localStorage 覆盖层、`parcelTrend` 认派生时间，即使补了种子也会「趋势有、指标卡没有」。修法：新增 `pickupTs()` 统一取件时间口径（`hydrate` / `parcelSummary` / `parcelTrend` 三处收敛），并让约 2.6% 的已取件包裹落在今日。结果 **0 → 2,631**、环比 **↓100.0% → ↓7.6%**，断言脚本一字未改且全过。

## 工程踩坑：同一端口被两个服务同时监听

现象：产物明明重新构建并同步了，浏览器里却一直是旧版本。

原因：**Node 的 `server.listen(port)` 不带 host 时绑定 IPv6（`::`），而 Vite dev server 绑 IPv4（`127.0.0.1`）——两者可以同时监听 5188 而不报 `EADDRINUSE`**。浏览器解析 `localhost` 时可能落到其中任意一个。

排查手法（记录备用）：先 `Get-NetTCPConnection -LocalPort 5188 -State Listen` 看**监听进程数**，再比对**服务端 HTML 引用的 chunk 哈希与磁盘 dist 是否一致**；两者对不上就是这个问题。`scripts/serve.mjs`（交付包同一份）已补 `EADDRINUSE` 显式报错。

## 走查结论必须复核：本批次 3 项报错中 2 项被证伪

| 走查报告                                                                                  | 复核结论                                                                                                                                            |
| ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| 「我的」缺「我的工资单」入口                                                              | ❌ 证伪 —— `MeSection.vue:56` 本就有                                                                                                                |
| 宫格「加载中」态不可达                                                                    | ❌ 证伪 —— 宫格本就在 `PageState` 之外且已传 `:loading`，运行时采样实测 `···` 可见                                                                  |
| 管理端「今日取件 0 件」                                                                   | ✅ 真缺陷，已修（见上节）                                                                                                                           |
| `Cannot read properties of null (reading 'realName') @ preload-helper-5aO-yyq7.js:12:660` | ❌ 证伪 —— 该文件第 12 行是 `* @license MIT` 注释（15 字符），列 660 不存在；且当时 chunk 哈希已变为 `preload-helper-Re4pOHOW.js`，属**旧产物残留** |

**教训**：走查是信号不是判决，照单修复会引入无谓改动；同时控制台报错要先用「文件行号是否存在 + chunk 哈希是否当前」验伪，否则会去追一个不存在的 bug。

---

## 同步任务自定义配置：把硬编码枚举变成可维护配置

设计规范见 [`docs/demo-sync-config-design.md`](../docs/demo-sync-config-design.md)（685 行）。**目标：新增一个数据源 / 一个频率档位，只改数据、不改代码。**

### 改造前 vs 改造后

| 场景         | 改造前                                                                                                                    | 改造后                                                                              |
| ------------ | ------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| 新增数据源   | `dataSource` 是自由文本输入框，各驿站各写各的、拼写无人管；要变下拉需同时改 Mock 枚举 + `dict.js` + 抽屉三处              | 在 `data_source` 选项集加一行即可，**代码零改动**                                   |
| 改频率档位   | `FREQUENCIES = ['HOURLY','EVERY_2H','EVERY_4H','DAILY']` 硬编码在 `routes/syncConfig.js:17`，且旧码无法表达「每 30 分钟」 | 选项集加一行（`optionKey` + `intervalMinutes`），命名规范为 `EVERY_{n}M` 可无限扩展 |
| 加一个配置项 | 需要改表结构 + 表单 + 校验三处                                                                                            | 在「配置管理」建项（含值类型与约束），表单项自动出现                                |

### 分层模型

`配置项定义（ConfigItem）` → `选项集（OptionSet/OptionItem）` → `全局默认` → `驿站覆盖`

- 值类型 5 种：`SINGLE_SELECT` / `NUMBER` / `TEXT` / `TIME` / `TIME_RANGE`，每种有独立的约束方式（范围、长度、正则、时间格式与先后）
- 驿站**默认继承全局**，可逐项覆盖；「继承 / 已覆盖」有双通道视觉区分（实底软色 vs 描边中性 + 值弱化 + tooltip）并支持「恢复继承」
- 删除被引用的配置项/选项：先查影响面 → 二次确认 → 受影响驿站该项**回退为继承全局**（不留悬空引用）；内置项只能停用

### 迁移（幂等，零业务影响）

`HOURLY/EVERY_2H/EVERY_4H/DAILY` → `EVERY_60M/120M/240M/1440M`（选项登记 `legacyCodes` 做兼容读取）；自由文本数据源 → `DUODUOCAI/CAINIAO/JD`；**未命中的历史文本自动纳管为 `source=MIGRATED` 选项，不置空**（置空会让「启用采集却无数据源」凭空产生异常驿站）。时段默认值 → 内建模板 `WORKDAY`。

读接口的 `frequency` / `dataSource` 继续返回**生效值**且优先回旧码，保证改造期间既有 PC 采集配置页与移动端不破。

### CSV 导入导出

16 列，用「记录类型」列（`ITEM`/`OPTION`/`GLOBAL`/`STATION`）**一张表表达四层结构**，`导出即导入`闭环。UTF-8 BOM + CRLF（Excel 可直接打开编辑）。导入为四步向导：上传 → `dryRun` 预览（汇总 + 逐行「行号 / 级别 / 原因」）→ 冲突策略（覆盖/跳过/追加）→ 落库。预览与落库**复用同一套解析逻辑**。

### 走查驱动修掉的 6 个真缺陷

| #   | 问题                                                                    | 性质                                      |
| --- | ----------------------------------------------------------------------- | ----------------------------------------- |
| 1   | 导入向导 4 步但页脚只有「取消」，`goPreview()` 定义了却从未绑定到按钮   | **功能完全不可用**                        |
| 2   | 数值越界被静默改写（`99` → `10`、`3.5` → `4`），无任何提示              | 校验反馈缺失，掩盖用户输入                |
| 3   | 小写记录类型被 `toUpperCase()` 归一化后判定通过                         | 与规范 `D.5` 冲突，导入边界不应静默猜意图 |
| 4   | 预览明细缺独立「级别」列，出现 `通过 ：通过` 冗余                       | 与规范 `B.7`「行号+级别+原因」不符        |
| 5   | 失败行的驿站 / 配置项 Key / 选项 Key 被清空，而原因里却带出该值         | 列值与原因不一致，用户困惑                |
| 6   | 删除被引用项原为「直接阻断」，与规范 `B.6`「允许删除 + 影响面提示」冲突 | 定稿契约时未回查规范，由走查发现          |

**教训**：`D-11` —— 定稿接口契约前必须回查设计规范对应章节；这次契约与规范冲突，靠浏览器走查才暴露出来。

---

## 工单复制 + 登录密码明文切换

### 工单复制（4 处入口，三端文本一致）

| 入口           | 位置                          | 形态                                    |
| -------------- | ----------------------------- | --------------------------------------- |
| 网页端列表行内 | 操作列「详情」右侧            | 图标按钮 + tooltip（操作列 80 → 120px） |
| 网页端详情     | 抽屉底部次操作区              | 「复制详情」文字按钮                    |
| 移动端详情     | `PageNav` 右插槽              | 44×44 图标按钮                          |
| 移动端列表     | 行右侧（员工端 + 管理端两处） | 44×44 图标按钮                          |

- 拼装逻辑收口在 [`src/shared/domain/workOrderText.js`](src/shared/domain/workOrderText.js)（**三端共用同一函数**，字段顺序与标签不可能漂移）
- 空值行省略，工单号与状态恒保留；排除 `id` / `stationId` / `parcelId` 等内部字段
- 文本样例：

```
【工单详情】
工单号：WO-20260920-0112
类型：设备故障
优先级：中
状态：已解决
归属驿站：城西驿站
处理人：谢洋
上报人：田平
创建时间：2026-09-20 00:05:08
SLA 截止：2026-09-21 00:05:08
解决时间：2026-09-20 00:35:08
工单描述：演示工单内容：用于验证四态流转、SLA 倒计时与通知联动
```

### 剪贴板必须降级（否则安卓壳里必然失败）

[`src/shared/domain/text.js`](src/shared/domain/text.js) 里 `copyText()` 的链路：

1. `navigator.clipboard.writeText()` —— **只在安全上下文（https / localhost）可用**
2. 失败或不可用 → 临时 `textarea` + `document.execCommand('copy')`（`execCommand` 已被规范废弃，但**在安卓壳 WebView 与非 HTTPS 场景下它是唯一通道**，已标 `TODO(扩展)`）
3. 两者都失败 → **给出可见提示，严禁静默失败**

### 移动端登录页密码明文切换

- 图标 `closed-eye`（密文）/ `eye-o`（明文），`van-field` 的 `#right-icon` 插槽；`aria-label` 随状态在「显示密码」/「隐藏密码」间切换 + `aria-pressed`
- 切换只改 `type`，不清空密码，也不影响页面上「一键体验」快捷身份填充

### 一个反幻觉核查的收益

用户要求「**三端**登录页加密码明文切换」。核查后发现：**PC 登录页复用一期冻结页 `@admin/views/login/index.vue`，其第 43 行早已使用 Element Plus 的 `show-password`**（自带眼睛图标切换）。实际只需改移动端登录页——**避免了在冻结文件上做无谓改动**。记为决策 `D-14`。

---

## 构建优化 + 交付形态 + 色阶校验（本轮实测结论）

### 1. 体积告警阈值复原（P2-5）

`build.chunkSizeWarningLimit` 原为 `2048`（等效关闭体积告警，Vite 默认 500），**已删除**。删除后 `npx vite build --mode demo` 的实际告警原文：

```
(!) Some chunks are larger than 500 kB after minification. Consider:
- Using dynamic import() to code-split the application
- Use build.rollupOptions.output.manualChunks to improve chunking: https://rollupjs.org/configuration-options/#output-manualchunks
- Adjust chunk size limit for this warning via build.chunkSizeWarningLimit.
```

唯一超限的是 **pc 入口块** `pc-*.js`（约 1.10 MB / gzip 348 kB，Element Plus 单入口全量引入所致）；其余：Mock 装配块 `install-*.js` 174 kB、移动端入口 `mobile-*.js` 114 kB、最大页面块 165 kB，全部低于 500 kB。

**不引入 `manualChunks`**（先量后拆）：

- 超限的只有 pc 入口块，它是**单入口专用**，不存在「某 vendor chunk 过大且被多入口复用」的情形；
- `element-plus` 只被 pc 入口用、`vant` 只被 mobile 入口用，按库拆只会把 1 个文件变成 2 个，总字节不变；
- 分包策略变更属 pinia 双副本风险的**相邻改动面**，收益（缓存命中）不足以抵复验成本。

→ 交给 Rollup 默认行为，该告警为已知且可接受的信号，**不靠调阈值掩盖**。

### 2. sourcemap 策略（P2-5）

`--mode production` 出 `hidden`（生成 `.map` 但不写 `sourceMappingURL` 注释，浏览器不自动加载）；`--mode demo` 关闭。实测：生产产物 `.js` 中 `sourceMappingURL` 命中 **0** 处，`.map` 已随构建生成。与运维约定：`dist` 下 `.map` 不可公开访问。

### 3. `base` 与 `file://` 离线包（P2-5，只登记不实施）

PC 是 history 路由，构建基址必须是 `/`；安卓 WebView 若以 `file://` 加载离线包则需 `./`。`base` 是全局配置、无法按入口区分，二者硬冲突。本轮**不实施**离线包方案（`file://` 下 ESM 能否加载本机无法验证，无 Android SDK），已在 `vite.config.js` 的 `build` 段用 `TODO(扩展)` 登记待验证项。

### 4. 交付形态澄清（P1-12）

`serve.mjs` 此前只存在于交付包、仓库无此文件，形成「源码无法回溯」状态。现已收入 `scripts/serve.mjs`（**与交付包同一份**，靠「脚本同级 `dist/` 或上级 `dist/`」双候选定位，两种放置都能直接跑），并新增 `npm run serve:dist`。

| 启动方式             | 走什么                                            | 前置条件                                      |
| -------------------- | ------------------------------------------------- | --------------------------------------------- |
| 双击 `启动预览.ps1`  | 本地 Node dev server（Vite，端口 5188，含热更新） | 本机有 Node 18+；首次运行会自动 `npm install` |
| `npm run serve:dist` | 仓库内 Node 静态服务，托管 `dist/` 产物           | 本机有 Node 18+，且已 `npm run build`         |

**两种方式都需要本机有 Node，都不需要后端**——「零依赖」指的是零后端 / 零数据库 / 零 JDK，不是零 Node。交付包内不含 `package.json`，其启动方式是等价的 `node serve.mjs`。

### 5. Element 浅色阶生成与校验（ADR-A26）

`scripts/gen-element-tokens.mjs` 按官方 `set-color-mix-level` 的算法（`light-N = mix(white, base, N*10%)`、`dark-2 = mix(black, base, 20%)`，逐通道线性插值）离线复算，与 `pc/styles/tokens.scss` 的 30 个字面量（light-3/5/7/8/9 共 25 个 + dark-2 共 5 个）逐值比对：

- **取整方式 = `round`**：round 命中 10/30、floor 命中 5/30；`primary` 族 6 个值（light-3/5/7/8/9 + dark-2）**全中 round 且精确复现**，故算法口径确认为「逐通道混色 + round」，`floor` 已排除。
- **20 个值无法复现**（success/warning/danger/info 的 19 个 light 值 + warning dark-2）：依据 `demo-ui-redesign.md` 2.8 / U2，这几族的浅色阶当年就是**按文档给定的字面量写死**，并非算法推导值 —— 属预期内，不是脚本缺陷。
- `npm run verify:tokens` 当前**报不一致并按设计非零退出**（如实报警，不是脚本 bug）。要让校验转绿须产品/UI 决策：按 round 重算替换（会改视觉），或把校验范围收敛到可复现的族。
- 脚本**只读不写**，输出仅作人工采纳；不引 `element-plus/theme-chalk` 的 SCSS 生成链（会把 Element 的 SCSS 纳入本项目编译图，触碰 `resolve.dedupe` 白屏护城河）。

### 6. Mock 剥离与 pinia 去重的 A/B 证据（实测）

| 证据                   | 命令                                     | 演示态产物                                                   | 生产态产物 `.js`                                              |
| ---------------------- | ---------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------- |
| Mock 是否进包          | `rg -c "installMock\|createMockAdapter"` | 命中 **3 个文件 / 8 次**（pc 入口、mobile 入口、install 块） | **0 文件 / 0 次**（仅 `.map` 的 `sourcesContent` 里留有源码） |
| pinia 模块实例是否唯一 | `rg -c "_s\.get\("`                      | **只命中 1 个文件**                                          | 同左                                                          |

pinia 去重的前后对比（`resolve.dedupe` 必须原样保留）：去重前 `_s.get(` 同时出现在 `pc-*.js` 与 `preload-helper-*.js` **两个 chunk**、模块数 **3977**；去重后仅 **1 个**、模块数 **2318**。任何改动构建输入 / 分包策略的提交都必须复验这一项。
