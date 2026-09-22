# 会话状态 — 三端 Demo（网页端 / 老板端 / 员工端）

> 最后更新: 2026-09-19
> 状态：**需求 1–10 已全部实现并通过契约与构建验收，交付包已归档到桌面**
> 分支：`feature/三端演示Demo`
> 里程碑台账见 `hrm-dev/docs/demo-milestones.md`

## 交付物

| 交付物 | 路径 |
| ---- | ---- |
| 三端 Demo 工程 | `hrm-dev/hrm-demo/` |
| 安卓 H5 壳骨架 | `hrm-dev/hrm-android-shell/` |
| Demo 架构设计 | `hrm-dev/docs/demo-design.md` |
| UI 基础规范 | `hrm-dev/docs/demo-ui-redesign.md` |
| 体验改进与新功能规范 | `hrm-dev/docs/demo-ux-improvement.md` |
| 里程碑与跟踪机制 | `hrm-dev/docs/demo-milestones.md` |
| 本地启动器 | `hrm-dev/hrm-demo/启动预览.ps1` |
| 桌面预览入口 | `桌面/快递驿站三端Demo预览.lnk` |
| **交付归档包** | **`桌面/快递驿站三端Demo交付/`（程序包 + 文档）** |
| **前端改进建议书** | **`桌面/快递驿站前端改进建议书.md`（M10 依据：9 个开源项目参照 + 208 文件审计 + 24 条问题 + 7 条 ADR）** |

## 进度

- [x] M1 设计规范冻结 — `demo-ux-improvement.md`（66 条改进建议 P0 15/P1 29/P2 22 + 10 项功能规范 + 11 个新增 Token，全部带 `文件:行号` 依据）
- [x] M2 数据契约冻结 — 需求 1–10 契约 + 92/93/94xx 错误码段，断言 **347 → 668**（新增 321）
- [x] M3 网页端（PC）实现 — 需求 1–10 全部页面与组件
- [x] M4 移动端实现 — 老板端配置/审核 + 员工端查看/确认
- [x] M5 集成验证 — `verify:mock` 668 全过 + `build` EXIT=0（3977 modules）
- [x] M6 交付归档 — `桌面\快递驿站三端Demo交付\`（程序包 dist + 零依赖 `serve.mjs` + 启动器 + 使用说明；文档 5 份）。实测三入口 HTTP 200
- [x] M7 移动端三 Tab 导航重构 — 底部导航 6 项 → **首页 / 消息 / 我的**（两端都改）；95 条入口迁移映射全部落地；首页快捷功能宫格 8+8 项带实时数据；浏览器实测通过
- [x] M8 同步任务自定义配置 — 硬编码枚举全部开放为可维护配置项；全局默认 + 驿站覆盖；CSV 导入导出；配置校验机制；浏览器实测通过（断言 754）
- [x] M9 工单复制 + 登录密码明文切换 — 工单复制 4 处入口（PC 详情/PC 列表/移动端详情/移动端列表），三端文本一致；移动端登录页密码明文/密文切换。浏览器实测通过（断言 754）
- [x] **M11 请假申请模块** — 两级审批（站长初审 → 老板终审）+ 员工端申请/修改/重提/撤销 + 审批人撤回 + 扣款全局开关 + 考勤算薪口径联动 + 前端运行日志上报 + 操作留痕。门禁：**878 契约 + 48 移动链路 + 150 单测 + e2e 37 通过/0 失败** + 0 lint error + build EXIT=0
- [x] **M10 改进建议书落地（系统性修正与优化）** — 参照 9 个 GitHub/Gitee 同类开源项目产出 24 条问题清单（P0 5/P1 12/P2 7），全部落地；新增质量链（lint / format / stylelint / Vitest / commitlint / git hook）与 13 项「未验证」核实。门禁：754 契约 + 48 移动链路 + **150 单测** + 0 lint error + build EXIT=0

## 本批次新增能力（需求 1–10）

| # | 需求 | 落点 |
| ---- | ---- | ---- |
| 1 | 同步任务模块化 + 驿站采集状态 | PC 同步页页内双 Tab（批次流水/采集配置）+ 采集三组件；老板端只读 4 态 + 异常驿站明细；站长只读本站 |
| 2 | 排班按驿站批量设置 | PC 页头「批量工具」四件套 + 预览确认；老板端批量工具三动作。112 次点击 → 1–4 次 |
| 3 | 新建工单 + 企微自动派单预留 | PC `CreateWorkOrderDialog` + `AutoDispatchDrawer`（粘贴→解析预览→确认派单，`--state-simulate-*` 整片区分 + `TODO(扩展)`） |
| 4 | 通知中心发布通知 | PC `PublishDrawer`（范围三选一 + 实时人数 + >20 人二次确认）；老板端整页两步表单 |
| 5 | 考勤导出 | PC 导出确认弹窗 → CSV blob；导出参数与屏幕筛选同源。移动端不做（按 B0.4） |
| 6 | 「超SLA」改「超时未处理」 | 全端文案 + 无障碍播报文案替换；派生字段统一 `overdueUnhandled`（`overSla` 留作旧参数别名） |
| 7 | 员工 KPI 考核 | PC `/employee/kpi`（挂员工管理）+ 档案聚合页；老板端全站结果 + 指标权重配置；员工端我的得分与排名 |
| 8 | 人事管理 | PC `/hr` 4 Tab（档案/定薪/调薪留痕/合同预警）；老板端调薪；员工端只读档案（卡号脱敏） |
| 9 | 财务管理工资单 | PC `/finance` 3 Tab，**积木式规则配置 + 试算预览**（不写死公式）；老板端审核+批量发布；员工端确认/提异议 |
| 10 | 入离职流程 | PC `/onboard`（横向步骤条）；老板端各步审批与驳回；员工端执行类动作 |

## 移动端三 Tab 导航重构（M7）

| 能力 | 落点 |
| ---- | ---- |
| 底部导航 6 项 → 3 项（首页 / 消息 / 我的） | `mobile/constants/tabs.js`；老板端 `/boss/home|message|me`，员工端 `/staff/home|message|me` |
| 首页快捷功能宫格（图标 + 名称 + 实时数据） | `HomeQuickGrid` + `QuickGridItem`；老板端 8 项（6 计数 + 2 状态）、员工端 8 项（3 计数 + 3 状态 + 2 纯入口），4 列 × 2 行 |
| 首页顶部状态区 | 员工端 `AttendanceStatusBar`（今日出勤 + 一键打卡）；老板端经营概览 |
| 消息 Tab | 通知子视图（既有通知中心整体升格，读/未读/跳转逻辑零改动）+ 待办子视图（老板 5 类 / 员工 3 类） |
| 我的 Tab | 我的数据（员工）/ 管理与配置（老板）；**待办队列已移出**，统一收进消息 Tab |
| 角标 | 仅消息 Tab：`未读通知数 + 待办总数`；`0` 不渲染；`>99` 显示 `99+`；静默失败 |
| 设计规范 | `hrm-dev/docs/demo-mobile-nav-redesign.md`（723 行，95 条迁移映射逐条带 `文件:行号`） |

**关键设计**：三 Tab 按「用户意图」分层（要做的事 / 要知的事 / 要查的自己），不按业务模块分层；一个入口只在一个 Tab 有主落点；高频动作 ≤1 次点击。宫格实时数据三种形态互斥（计数型走角标 / 状态型走第二行小字 / 纯入口型无数据），**绝不用 `0` 表示「加载失败」**，单项失败独立降级不拖垮整页。

**实测补充（Vant 行为，反幻觉核对源码）**：`van-tabbar-item` 重复点击当前项**不触发 `change`**（`TabbarItem.mjs` 有 `if (!active.value)` 守卫），故改用 `@click` 比对路径；Vant **不输出 `aria-current`** 也不处理 Enter，均已显式补上；`van-grid-item` 会吞外部 attrs，故宫格改用默认插槽 + 原生 `router-link` + 自绘角标。

## 同步任务自定义配置（M8）

| 能力 | 落点 |
| ---- | ---- |
| 配置项与选项集管理 | PC 同步任务页新增第三个 Tab「配置管理」（`?tab=config`，仅 ADMIN 渲染）→ 分段控件「配置项与选项集 / 全局默认 / 驿站覆盖」 |
| 配置项定义 | 5 个：`data_source`（数据源，单选）、`collect_frequency`（采集频率，单选）、`time_template`（采集时段模板，单选）、`retry_times`（重试次数，数值）、`timeout_minutes`（超时时长，数值）；值类型 5 种：`SINGLE_SELECT / NUMBER / TEXT / TIME / TIME_RANGE` |
| 选项集 | 3 个：数据源 5 项（含 1 项 `source=MIGRATED` 历史自动纳管）、采集频率 5 档（含「每 30 分钟」）、时段模板 2 项 |
| 全局默认 + 驿站覆盖 | 驿站默认继承全局，可逐项覆盖；「继承 / 已覆盖」双通道视觉区分（实底软色 vs 描边中性 + 值弱化 + tooltip），支持「恢复继承」 |
| 既有点位动态化 | **「采集配置」抽屉的数据源 / 采集频率 / 时段模板从硬编码改为动态加载选项集**（这是本需求的核心） |
| 校验机制 | 前端即时（失焦）+ 提交时 + 服务端兜底三层；数值越界**不再静默改写**，给明确红字文案并阻止提交 |
| CSV 导入导出 | 16 列、4 类记录（`ITEM`/`OPTION`/`GLOBAL`/`STATION`）单表表达层级；UTF-8 BOM + CRLF；导出三档范围 + 下载模板；导入四步向导（上传 → dryRun 预览 → 冲突策略 → 落库） |
| 删除策略 | 先查影响面（`/impact`）→ 二次确认 → `confirm=true` 执行；受影响驿站该项**回退继承全局**；内置项只能停用 |
| 设计规范 | `hrm-dev/docs/demo-sync-config-design.md`（685 行）；错误码新增 **95xx 段**（9501–9510） |

**迁移（幂等、零业务影响）**：采集频率旧码 `HOURLY/EVERY_2H/EVERY_4H/DAILY` → `EVERY_60M/120M/240M/1440M`（登记 `legacyCodes` 兼容读取）；自由文本数据源 → `DUODUOCAI/CAINIAO/JD`，未命中的历史文本（如「丰巢智能柜」）**自动纳管为 `source=MIGRATED` 选项、不置空**；时段默认值 → 内建模板 `WORKDAY`。

**兼容硬要求**：读接口的 `frequency / dataSource` 等旧字段继续返回**生效值**且 `frequency` 优先回旧码，保证既有 PC 采集配置页与移动端在改造期间不破。

## 工单复制与登录密码切换（M9）

| 能力 | 落点 |
| ---- | ---- |
| 工单复制（整条详情 → 多行纯文本） | **4 处入口**：PC 工单详情抽屉底部「复制详情」、PC 工单列表行内（图标按钮 + tooltip）、移动端工单详情顶部（`PageNav` 右插槽）、移动端工单列表行右侧（员工端 + 老板端两处） |
| 文本拼装（唯一真源） | `src/shared/utils/workOrderText.js` —— 三端共用同一函数，字段顺序与标签完全一致；**空值行省略**，工单号与状态恒保留；排除 `id` / `stationId` / `parcelId` 等内部字段 |
| 复制实现（含降级） | `src/shared/utils/copyText.js`：`navigator.clipboard.writeText()`（需安全上下文）→ `document.execCommand('copy')` + 临时 textarea → 均失败给可见提示（**严禁静默失败**） |
| 移动端登录页密码明文/密文切换 | `mobile/views/login/index.vue`：图标 `closed-eye`（密文）/ `eye-o`（明文），`van-field` 的 `#right-icon` 插槽；`aria-label` 随状态切换 + `aria-pressed`；44×44 热区 |
| **PC 登录页零改动** | PC 登录页复用一期冻结页 `@admin/views/login/index.vue`，其第 43 行**已使用 Element Plus 的 `show-password`**（自带眼睛图标切换），无需改动 |

**实测结论（浏览器）**：4 处入口均可复制，成功提示统一为「已复制工单详情」；PC 端用 `navigator.clipboard.readText()` 读到真值，移动端因 `document.hasFocus()` 为 false 无法直读，改用**拦截 `writeText` / `execCommand` 抓取实际写入内容**（两条通道逐字节相同）交叉取证，未伪造读数。三端复制文本字段顺序与标签**完全一致**。移动端密码切换实测：`type` 在 `password`/`text` 间正确切换、密码未被清空、「一键体验」快捷填充未破坏、`aria-label` 实测为「显示密码」/「隐藏密码」、`aria-pressed` 存在。

**顺带修正**：移动端工单详情区块标题原为「问题描述」，与 PC 详情页及复制文本用的「工单描述」不一致 → 已统一为「工单描述」。

## 改进建议书落地（M10）

> 依据：`桌面\快递驿站前端改进建议书.md`（103 KB，参照 9 个开源项目 + 208 文件审计，24 条问题）

### P0（缺陷级 / 产物不可控级）

| # | 问题 | 落点与结果 |
| ---- | ---- | ---- |
| P0-1 | PC 守卫 `user === null` 裸解引用 → **白屏** | `pc/router/index.js` 守卫补「登录态自洽自愈」（有 token 无 user → `clearAuth()` + 回 `/login?redirect`）；第 1 步加 `user &&`、第 4 步加 `!user \|\|`。**三点一组，缺一即引入同址重定向环** |
| P0-2 | 展示层直连 `shared/mock`（**假后端被当工具库**，15 条 import） | 新建 `src/shared/domain/`（time / pagination / csv / mask / text / workOrderText / permission / sla / index），迁出 9 条纯工具；`shared/utils/` 并入 domain；`src/**` 内只剩 2 个装配点 + 1 处白名单兜底 |
| P0-3 | Mock 开关恒开 + 静态装配 → **产物不可剥离** | 三步法：① 开关语义反转为「显式 `true` 才开」+ 新增 `.env.production` + `build:prod`；② 装配点改条件动态 `import()`（因构建目标不支持 top-level await，入口用 `.then()` 链）；③ 演示资产移入 `src/demo/`。**独立 A/B 复验：demo 产物命中 5 处 → production 产物命中 0 处，无 `install-*`/`db-*`/`parcelStore-*`/`scenario-*`/`accounts-*` chunk** |
| P0-4 | 无 API base 可配置、无 dev proxy | `VITE_API_BASE` + `VITE_API_PROXY_TARGET`；PC 侧用 `request.defaults.baseURL` **赋值**（不改一期源码）；production 入口裁剪为 pc + mobile |
| P0-5 | KPI 环形图对比度**不达标** | `--gauge-fill` 500 档 → 700 档：实测 **2.839:1 → 5.388:1**（原文记「约 3.1:1 临界」偏乐观，已更正） |

### P1（质量与规范）

| # | 内容 | 结果 |
| ---- | ---- | ---- |
| P1-1 | 业务字典三方合一 | 41 个导出名并集 → 实保留 38 个；删 `pc/config/dict.js` + `mobile/constants/business.js`（**取字段并集，未丢 variant / hint / allowances**）；裁决 `PAYROLL_STATUS.APPROVED` = `success+soft`、`MAKEUP_STATUS.PENDING` 文案 = 「审批中」 |
| P1-2 | Token 收敛为真源 | 新建 `shared/styles/tokens.base.scss`（143 条），两端 `@use base` + 平台差异层；统一 8 组分叉；旧名留别名过渡；**合法平台差异白名单原样保留** |
| P1-3~P1-8 | 路由去重 / 404 / 定时器 / 分层破口 / 日期统一 / vconsole | 重复路由**实测完全等价**后删；两端新增 404 页（catch-all 由 redirect 改 `component`）；`SlaTag` 每实例 interval → 共享 `useNow`（20 行列表 20 个 → 1 个）；`NoticeList` 改走 notify store；`parseTime` 行为**实测逐字相同**故直接委托；vconsole 仅 DEV 动态引入（产物 0 命中） |
| P1-9 / P1-10 | statusMap / chart 语义色 | `DICT_STYLES` → `DICT_COLORS`（variant 归字典、配色留组件）；移动端补 `--chart-*` |
| P1-11 | **Vitest 接入** | 16 个 spec / **150 用例**；`shared/domain` 覆盖率 **98.23% 语句 / 94.25% 分支**；含**路由守卫 spec**（PC 6 条 + 移动 4 条），把「白屏缺陷」与「404 不再静默跳转」变成永久回归网 |
| P1-12 | **质量链** | Prettier（~187 文件，**一期零改动**）/ ESLint 9 flat config（**0 error**）/ Stylelint（**0 problem**）/ commitlint（实测放行 `feat: 中文`、拒绝无类型）/ husky + lint-staged |

### P2 与验收期新增发现

| 项 | 结果 |
| ---- | ---- |
| **Lint 抓出 3 个真实运行时缺陷** | ① `mobile/utils/attendance.js` 的 `monthShiftMap` 只 re-export 未 import → `staff/attendanceRecords.vue:74` **必崩 ReferenceError**；② `NoticeList.vue:47` 用 `PUBLISH_SCOPE` 未导入；③ `pc/views/notification/index.vue:166` computed 漏 `.value` → 骨架屏恒显 |
| **性能：无筛选全量查询（测试报告 Y1）** | 原 20 万条无筛选查询 median **103.21 ms**（设计目标 10–16 ms 的 6–9 倍，ADMIN 包裹页默认即命中）→ 新增「入库时间倒序 Int32Array 索引」+ 单遍按位次取页 → **3.91 ms（-96%）**；内存 `arrayBuffers` 1.28 → 2.04 MB |
| **性能：覆盖层线性劣化（Y2）** | `parcelSummary`(全站) 中位数：0 条 7.4ms / 1 千 12.6ms / 5 千 15.1ms / 1 万 21.1ms / **5 万 60.9ms**。**未改实现**（聚合口径与 754 断言强耦合，风险高于收益），已在 `overlay.js` 登记 `TODO(扩展)` + 劣化曲线 |
| 响应式 | PC `--content-max: 1440px` 居中；移动 `#app` 480px 兜底（横屏 640px 由末尾媒体查询覆盖，**实测不冲突**）；固定栏与 FAB 一并限宽居中 |
| L1 直引收口 | 业务侧 `var(--c-*)` **119 → 12**（保留项全部带注释说明理由） |
| 404 焦点管理 | 两页主标题 `tabindex="-1"` + 挂载 focus（SC 2.4.3） |
| favicon | 三个入口加内联 SVG data-URI（品牌 700 档），**消除 `/favicon.ico` 404**，不引二进制资源 |
| 文档治理 | 删 README 全部历史断言数字（改「以脚本输出为准」）、修 5 处漂移；同步修正 3 份设计文档的 12 处与代码不一致处；`serve.mjs` 收入仓库 + `使用说明.txt` 澄清「零依赖 ≠ 零 Node」 |
| Element 色阶脚本 | `scripts/gen-element-tokens.mjs` 改**两段式**：A 类（primary 6 个）严格对齐 `round` 混色算法、B 类（手写 24 个）比对基线快照 → `verify:tokens` 由**长期红灯**（30/30 报错，原脚本 hex 大小写敏感）转为 **EXIT=0 且能真实报警** |

### 实测澄清（3 项，避免误判）

1. **排班页头「计数被浮层遮挡」= 误报** —— `schedule/index.vue` 内 `position: absolute` 命中 **0 处**；唯一「浮层」是 Element dropdown popper（body 级），展开时遮挡下方属**正常遮罩行为**
2. **`--color-primary-strong` vs `--color-primary-hover` 不是同一语义**（前者 = 承白字实底，后者 = hover/active），**不应列入「8 组分叉统一」**；强行拉平会削弱 hover 反馈
3. **`--c-neutral-400`(#9AA4B2) 与 `--c-neutral-450`(#8A93A0) 必须并存** —— 前者被 `--text-placeholder` 消费、后者是 `--border-control` 专用（3.10:1），合并必破坏其一

### 上线就绪度（测试工程师判定）

**有条件可上线（限定为静态前端可部署），阻塞项：无。** 条件：① 生产侧必须复刻 `serve.mjs` 的深链回退规则（`/`→index.html、其余无扩展名→pc.html、带扩展名缺失必 404）；② `build:prod` 产物必须禁止公开访问 `dist/**/*.map`；③ 公网暴露前先做 Element Plus 按需引入（pc 首屏 gzip 451 KB → 约 250–290 KB）；④ 接生产量级数据前关注 Y2。

## 关键决策

1. Demo 纯前端 + Mock，零后端依赖（本机无 JDK/MySQL/Redis）
2. 独立工程 `hrm-demo`，一期 `hrm-admin`/`hrm-server` **全程零改动**，经 Vite 别名 `@admin` 只读复用
3. Mock 用 axios 自定义 adapter（Vite 中间件与 MSW 在打包/`file://` 下失效）
4. 20 万包裹用 TypedArray 索引层 + 按需水合，写操作落 localStorage 覆盖层
5. 设计 Token 三层体系；品牌色 `#1890FF` 仅图标/线/浅底，承白字实底用 `#0958D9`（6.16:1）
6. **工资单计算规则绝不写死**：由 `payroll_rule.items` 配置驱动（来源 `FIXED`/`ATTENDANCE`/`KPI`/`MANUAL`）
7. UI/UX 智能体**只出规范文档**，实现/测试全部由主智能体编排分派
8. **允许移动老板端配置 KPI 指标与权重**（与 B7.8「配置场景留 PC」表述相左，但与 B0.4 分工表「老板端可写：配置指标与权重」一致；保留移动端能力以便演示）
9. **新增契约端点 `PUT /kpi/metrics/batch`**：单指标接口每次校验「启用合计 = 100%」，调权重天生存在中间态，逐条提交必然失败 → 合并为原子校验
10. **演示态与生产态必须物理可分离**（M10）：`VITE_MOCK_ENABLED` 显式 `true` 才装 Mock（fail-safe 向生产倾斜），装配点改条件动态 `import()` 使 Rollup 可 DCE；`build` = 演示态、`build:prod` = 生产态。**已用 A/B grep 独立复验（demo 命中 5 处 / production 命中 0 处）**
11. **依赖方向机器强制**（M10）：`shared/mock` 只允许 `pc/main.js` 与 `mobile/main.js` 装配；`shared/domain` 禁止反向依赖 mock；`portal/**`、`demo/**` 禁止反向依赖具体端代码。用 ESLint `no-restricted-imports` 落地，封住 P0-2 复发路径
12. **`shared/` 层已解冻**（M10）：原「shared 层本轮冻结」是并行协作期的流程自律、非外部约束，导致字典被复制成 3 份且已出现字段分叉（PC 版 `COLLECT_STATE` 多 `variant`）→ 解冻后合并为单一真源
13. **不引入 Monorepo / TypeScript / Nitro-ApiFox Mock / 文件路由 / UnoCSS / px-to-viewport / Oxlint / Playwright**（M10 逐条裁决）：唯一复用对象 `hrm-admin` 是只读外部代码，无法成为 workspace 包；类型收益已被 754 项**行为**断言覆盖；在线 Mock 与 `file://` 冲突；其余均属过度设计
14. **Prettier 全量格式化后必须立即验证 `hrm-admin` 零改动**（M10）：`hrm-admin` 通过 `@admin` 别名只读复用，格式化极易误伤一期资产 → 三条 ignore（prettier / eslint / stylelint）为硬性要求
15. **对比度以 node 实算为准，不信手算**（M10）：`--gauge-fill` 500 档对 `--gauge-track` 原记「约 3.1:1 临界」，实算 **2.839:1 明确不达标**（低于 SC 1.4.11 的 3:1）→ 改 700 档得 5.388:1
16. **`--color-primary-strong` 与 `--color-primary-hover` 语义相异，不可互替**（M10）：前者 = 承白字实底（blue-700）、后者 = hover/active（blue-800）；曾误列入「分叉统一」清单，已修正
17. **`--c-neutral-400` 与 `--c-neutral-450` 必须并存**（M10）：`#9AA4B2` 被 `--text-placeholder` 消费、`#8A93A0` 是 `--border-control` 专用（3.10:1）；合并必破坏其一的语义或对比度
18. **性能瓶颈先测后改、设硬门槛**（M10）：20 万条无筛选查询由「全量 sort」改为「时间倒序 Int32Array 索引 + 单遍按位次取页」，median **103.21 → 3.91 ms**；改后 `verify:mock` 754 项断言**一字未改且全过**。覆盖层线性劣化（Y2）因与聚合口径强耦合，**未强改**，仅登记 `TODO(扩展)` + 实测劣化曲线

### M11 请假模块决策（用户已拍板，2026-09-20）

19. **审批链 = 两级**：员工提交 → **站长初审（本站）** → **老板终审**。站长的请假**跳过初审**直接进终审。现有补卡/入离职都是单级 ADMIN，两级属净新增，无先例可抄
20. **撤销范围 = 双向**：① 审批前（PENDING_* 任一态）**申请人可自行撤销**；② 终审通过后**可由审批人撤回**（撤回需回滚考勤标记与扣款口径）
21. **必须打通考勤与算薪口径**：批准后写入 `LEAVE` 考勤标记，并把缺勤口径从 `排班天数 − 出勤天数` 改为 `排班天数 − 出勤天数 − 已批请假天数`。**不联动会导致「请假当天即被算作缺勤并触发 ABSENT_FINE 扣款」**（口径镜像共 3 处：`dict.js` / `financeStore.js` / `payrollPreview.js`，必须同版）
22. **假别 = 固定枚举，不做额度**：年假/事假/病假/调休/婚假/产假等，定义在 `shared/constants/dict.js`（三端唯一真源）。**不做年假余额与扣减**（用户未要求，遵循精简优先）
23. **「请假是否扣工资」= 全局单开关**（非按假别、非按驿站），**网页端与老板端均可设置**（仅 ADMIN）
24. **「日志上传」= 前端运行日志上报**（捕获异常 / Promise 未处理 / 接口失败），落 Mock 端点 + PC 提供查看页；**必须脱敏**（不记 token / 密码 / 身份证）且设**环形缓冲上限**。另保留请假单自身的 `handleLog` 操作留痕（审计最低要求，照抄工单 `handle_log` 结构）
25. **复用优先**：请假的**结构照搬补卡审批范式**（4 端点拓扑 / 三态状态机骨架 / 待办分组 / 宫格计数 / PC 与移动审批页 / 断言分组结构），但**语义必须重写**——不绑定排班槽位、面向未来时段、时间段重叠校验、跨天与半天粒度、休息日排除、驳回原因**必填**（补卡为选填）

## 请假申请模块（M11）

> 依据：`hrm-dev/docs/demo-leave-design.md`（1078 行，6 态状态机 + 权限矩阵 + 7 页面 + 16 端点 + 96xx 错误码 + 10 项裁决 + 附录 D 落地偏差）
> 复用范式：**结构照搬补卡审批**（4 端点拓扑 / 状态机骨架 / 待办分组 / 宫格计数 / PC 与移动审批页 / 断言分组），**语义全部重写**

| 能力 | 落点 |
| ---- | ---- |
| 状态机 6 态 | `PENDING_STATION → PENDING_BOSS → APPROVED` + `REJECTED`（靠 `rejectStage` 区分初审/终审）+ `CANCELLED`（员工审批前撤销）+ `REVOKED`（审批人撤回）。驳回**不拆两态**，**两级驳回重提都回 PENDING_STATION** |
| 两级审批 | 员工提交 → 站长初审（本站） → 老板终审；**站长的单跳过初审**；**无可用站长时直接进终审**（Q4）；**不能审自己的单** |
| 撤销双向 | 审批前申请人可撤销；**终审通过后仅 ADMIN 可撤回**（Q1），撤回按 `countedDaysSnapshot` 回滚，被「存在非草稿工资单」硬阻断（9606） |
| 时长双口径 | 半天单元（AM/PM）；**自然天数** vs **计薪天数**（逐日查排班矩阵排除休息日，**未使用 `REST_CYCLE_DAYS`** 推算）；婚假/产假等走 `NATURAL` |
| 表单 | 7 字段校验 + **日期护栏（面向未来，与补卡方向相反）** + 时间段重叠校验（9603）+ 超 30 天上限 + **驳回原因必填（与补卡选填刻意不同）** + 实时调 `POST /leave/preview` 试算（**前端零本地时长实现**） |
| 假别 | 固定枚举进 `shared/constants/dict.js`（**不重复补卡「PC/移动各一份」的错**）；**不做额度余额** |
| 扣款全局开关 | `leaveDeductEnabled`：`true` = 请假按缺勤计（扣款）；`false`（默认）= 不扣。**PC 与移动老板端均可设**，两端界面都写明语义与公式；`GET /leave/settings` 收紧为仅 ADMIN |
| 考勤算薪联动 | 采用**派生模型**（不往 `records` 写伪造打卡记录，避免污染 `source` 语义）；缺勤口径 `排班天数 − 出勤天数 − 已批请假天数`；**两处同版**：`attendanceStore.employeeAttendanceStat` + `payrollPreview.js`；三处镜像（`dict` / `financeStore` / `payrollPreview`）同步增 `LEAVE` 指标 |
| 通知 | 7 场景接入 `pushNotification`（**补卡/入离职/调薪/工资单都没接，请假是第一个接的**）；`bizType:'leave'`；`NOTIFICATION_TYPE` 扩 5/6 并同步放行发布接口硬校验；`NoticeList` 补 `bizType==='leave'` 跳转分支（**按角色分流**） |
| 操作留痕 | 每单 `handleLog[]`：提交/修改/初审/终审/驳回/撤销/撤回 各一条（操作人 + 时间 + 前后状态 + 原因），结构照转单留痕 |
| 运行日志上报 | `src/shared/clientLog.js`：采集 `window.onerror` / `unhandledrejection` / axios 错误 / Vue `errorHandler`；**白名单复制式脱敏** + 二次凭据擦除；环形缓冲 200 + 20 条或 30s 批量上报 + 失败静默；PC `/system/logs` 查看页（仅 ADMIN，支持筛选/详情/清空） |
| 页面 | 移动：`/staff/leave/apply`、`/staff/leave`（我的）、`/staff/leave/review`（**站长初审放员工端域内**，理由见规范 §3.3）、`/boss/leave`（终审+撤回）、`/boss/leave/settings`；PC：`/leave`（管理+审批+扣款开关）、`/system/logs` |
| 权限收口 | Mock 层无统一越权中间件，请假 route 自写 `user.role === 'ADMIN' ? 入参 : user.station_id` 强制覆盖（范例 `parcel.js:13-17`）+ 自审拦截 + 派生标志 `canEdit`/`canCancel`/`canRevoke` 由服务端算，前端不重复推导 |

### M11 期 e2e 套件（新增能力）

工程内存在**真实的 Playwright 端到端套件** `hrm-demo/e2e/`（8 spec / **37 用例**），已接入 `npm run e2e`。
> ⚠️ **必须带 `--config e2e/playwright.config.js`** —— 不带会把 vitest 的 `src/**/*.spec.js` 一并吞进 Playwright 运行器并全部报错（脚本层已强制 + 配置与 README 双处注释）

**实测：37 通过 / 0 失败 / 0 flaky**（约 5.1 分钟）。它抓出并已修的真实缺陷：

| 缺陷 | 根因 | 影响 |
| ---- | ---- | ---- |
| **PC 考勤页指标条全为「—」** | `onMounted` 里 `await loadStations()` **串行阻塞**了 `fetchSummary()`，而 `summaryLoading` 初值 false → 首帧渲染成「失败语义」，约 538ms 后才出数（**从未通过过 A5-2**，属既有真缺陷） | 用户首屏看到 6 个「—」，误以为无数据 |
| **从 `/pc.html` 进入并登录后落 404** | 守卫把入口文件名 `pc.html` 当回跳地址写入 `redirect`（M10 的「404 替代静默 redirect」把该既有缺陷**暴露**出来） | 入口页登录直接 404 |
| **刷新后被踢回登录页**（PC + 移动端） | Mock 的 `db.sessions` 是**内存 Map**，整页刷新后会话消失 → 首个鉴权请求 401 | 与「token/user 持久化、刷新不丢」的既有设计意图冲突。已改 `PersistedSessionMap` 落盘 |
| **工单列表「后发先至」竞态** | 首屏未筛选请求晚于「查询」请求返回时，用 120 条覆盖 0 条（脚本复现 **6 次中 2 次失败**） | 列表数据错乱。已加请求序号守卫；同类竞态在考勤/包裹/员工列表页仍存在，登记 `TODO(扩展)` |
| **dev 模式入口白屏（M10 引入）** | M10 加的 dev history 回退中间件把 `/@vite/client`、`/@id/__x00__plugin-vue:export-helper` 也改写成 HTML → SFC 导入失败 | **dev 下入口白屏**；已加 `/@vite|@id|@fs|node_modules` 白名单 |

### M11 实测澄清

1. **`GET /leave/settings` 的 roles 曾被两份批次报告给出相反结论** —— 独立核实：`routes/leave.js:232` **确已收紧为 `['ADMIN']`**；「未收紧」的结论是读到旧状态
2. **算薪开关方向：任务书曾写错，实现正确** —— `leaveDeductEnabled = true` 字面语义即「启用请假扣款」，指向按缺勤计；已按设计规范实现并更正任务书措辞
3. **`A5-2` 与 M11 无关** —— 数据层 `employeeAttendanceStat` 返回正常（`scheduledDays:20/attendedDays:17/absentCount:3`），且 `evidence` 中从未产出过 A5-2 截图（从未通过）
4. **宫格超出 4×2 约定已实测可接受** —— 员工端 9 / 站长 10 / 老板端 9 项，375px 下横向溢出 **0px**、热区不达标 **0 个**，第 3 行不满；已在 `demo-mobile-nav-redesign.md` 登记裁决
5. **菜单图标** —— `@element-plus/icons-vue` 实测**无 `Notes`**；请假改用 `Notebook`（已核实存在且未被占用），与工单的 `Tickets` 不再重名

## 实测结论（M11 后为最新口径）

- `npm run verify:mock`：**878 项断言全部通过**（347 基线 + 321 新增 → M7/M8 期间 668 → 754 → **M11 +124**；M10/M11 全批次**既有断言一字未改**）
- `npm run verify:mobile`：**48 项通过**（T13–T16 数据链路）
- `npm test`（Vitest）：**16 个 spec / 150 用例全部通过**；`shared/domain` 覆盖率 **98.23% 语句 / 94.25% 分支 / 100% 函数**
- **`npm run e2e`（Playwright）**：**8 spec / 37 用例，37 通过 / 0 失败 / 0 flaky**（约 5.1 分钟）
- `npm run build`：EXIT=0，**2421 modules**（依赖去重前 3977 → 去重后 2318 → M10 后 2384 → M11 后 2421）
- `npm run lint`：**0 error**（24 warn 为既有 a11y 项）；`npm run lint:style`：**0 problem**；`npm run format:check`：通过；`npm run verify:tokens`：**EXIT=0**
- **Mock 剥离 A/B 独立复验**：`--mode demo` 产物命中特征串 5 处 → `--mode production` 产物命中 **0 处**，且无 `install-*`/`db-*`/`parcelStore-*`/`scenario-*`/`accounts-*` chunk
- **20 万包裹性能**：无筛选查询 median **3.91 ms**（改前 103.21 ms）；组合筛选 3.95 ms；分页 3 档 ≤4 ms；运单号精确 0.01 ms；索引层内存 2.04 MB

### 历史基线（保留供回溯）

- M5 时 `verify:mock` 668 项 / `build` 2318 modules（依赖去重前 3977）
- **规则驱动验证（需求 9 核心）**：规则项金额 200→500 → 净额 +300；关闭该开关 → 净额 −500
- 工资单状态机全链路：`DRAFT → PENDING_APPROVAL → APPROVED/REJECTED → PUBLISHED → CONFIRMED`；非法流转 9403、员工查他人 9404、重复生成 9405
- KPI 权重合计 ≠ 100% 保存被拒（9202）且**批量端点保证原子性**（失败后数据未部分写入）
- 入离职未走完全流程不启用账号（登录 1002 拦截）；离职结算单号与工资单三方自洽
- 人事档案脱敏：出参无 15 位以上连续数字，银行卡形如 `**** **** **** *** 2020`
- `POST /schedules/batch-by-station` 返回 `{ created, skipped, total }`（`skipExisting` 默认 `true`）
- 20 万包裹组合筛选单次查询 10–16 ms

**M8 浏览器实测（同步自定义配置）**：断言 **668 → 732 → 754**，`build` EXIT=0。
- ✅ **核心链路实测成立**：在「配置管理」新增数据源 → 切到「采集配置」抽屉，数据源下拉**立即出现该自定义项**（5 → 6 项）——证明配置区域确为动态加载、非硬编码
- ✅ 导入向导 4 步全部可走通，dryRun 预览给出「成功 3 / 失败 2 / 共 5 行」并逐行给出行号 + 级别 + 原因
- ✅ 数值校验：填 `99` 报「须在 0-10 次之间，当前为 99」、填 `3.5` 报「须为整数，当前为 3.5」，保存被拦下且未落库
- ✅ 继承/覆盖区分清晰；内置项删除按钮禁用态灰 `rgb(203,210,218)` vs 危险红 `rgb(207,19,34)` 可区分

**M8 修复链（走查驱动，均为真缺陷）**
1. **导入功能完全不可用**：向导 4 步但页脚只有「取消」，`goPreview()` 定义了却从未绑定到按钮 → 补全四步导航、步骤条、空/错误/边界态
2. **数值型静默改写**：`99` 被钳制成 `10`、`3.5` 被取整为 `4` 且无任何提示 → 改为「阻止提交 + 显式红字文案」（理由：静默改写会掩盖用户输入）
3. **大小写被静默归一化**：小写 `item` 被 `toUpperCase()` 后判定通过，与规范 `D.5` 第 5 点「要求大写枚举，写小写须行级失败并给出正确写法」不符 → 改为严格校验并给出纠正建议
4. **预览明细缺独立「级别」列**且出现 `通过 ：通过` 冗余 → 拆出级别列（复用 `StatusTag`，文字表达非仅颜色）
5. **失败行「列值与原因不一致」**：失败行的驿站 / 配置项 Key / 选项 Key 被清空，而原因里却带出该值 → 统一回填该行解析出的原始值
6. 契约层面：删除策略原实现为「被引用即阻断」，与规范 `B.6`「允许删除 + 前置影响面提示」冲突 → 补 `/impact` 影响面端点 + `confirm` 确认后删除，受影响驿站回退继承
7. **交付包静态服务的深链回退目标错了**：刷新 PC 深链（`/dashboard`、`/parcel/sync`）会跳到**端选择页**，看起来像登录态丢失。根因是 `serve.mjs` 把无扩展名路径统一回退到 `index.html`。三入口里只有网页端用 history 路由（移动端是 hash、入口页是静态页），故改为：`/` → `index.html`，其余无扩展名路径 → `pc.html`。同步给 `vite.config.js` 加了一个仅 dev 生效的中间件做同样的事。实测 `/dashboard`→pc.html ✅、`/assets/*.js` 缺失→404 ✅

**M7 浏览器实测（三 Tab 走查）**：员工端底部**恰好 3 项**且每项图标 + 文字齐备（`wap-home-o` / `chat-o` / `user-o`），选中态 `rgb(9,88,217)` + 字重 600、未选中 `rgb(107,114,128)` + 400；重复点击当前 Tab 生效（`scrollY` 817.6 → 0；消息子视图从「待办」回位「通知」）；宫格 8+8 项全部渲染、**无 `0` 冒充**（计数为 0 时角标隐藏）；「打卡」为员工端宫格第 1 项且**1 次点击可达**；消息 Tab 角标实测员工端 10 / 老板端 80；老板端消息待办 78 = 43+8+17+7+3（与角标同口径）。第二项走查对 6 个不确定项逐条给出源码级结论。

**M7 修复与证伪（走查报告 3 项，仅 1 项为真缺陷）**
1. ❌ **证伪**：「我的」缺「我的工资单」入口 → [MeSection.vue:56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L56) 本就有，走查误报
2. ❌ **证伪**：宫格「加载中」态不可达 → 宫格本就在 `PageState` 之外且已传 `:loading`，运行时采样实测 `···` 可见；`PageState` 未改动
3. ✅ **真缺陷已修**：老板端「今日取件」恒为 0、趋势 `↓100.0%`。根因是**双重的**：① 取件时间派生为「入库 + 2~72h」必然早于今日零点；② 口径分裂——`parcelSummary` 只认 localStorage 覆盖层、`parcelTrend` 认派生时间，即使补种子也会「趋势有、指标卡没有」。修法：新增 `pickupTs()` 统一取件时间口径（`hydrate`/`parcelSummary`/`parcelTrend` 三处收敛），并让约 2.6% 已取件包裹落在今日。结果：今日取件 **0 → 2,631**、环比 **↓100.0% → ↓7.6%**，**668 项断言一字未改且全过**

**M7 另一处证伪**：走查报告的 `Cannot read properties of null (reading 'realName') @ preload-helper-5aO-yyq7.js:12:660` **不是当前代码的缺陷**。依据三重：① 该 chunk 第 12 行是 `* @license MIT` 注释（15 字符），列 660 不存在；② 当前构建的 chunk 哈希已是 `preload-helper-Re4pOHOW.js`，`5aO-yyq7` 属旧产物；③ 清缓存后按原步骤复现，控制台为空、正常重定向到登录页。`auth.js` 的 `currentUser` 空安全兜底（P0-1）本就覆盖了该场景。

**浏览器实测（首轮真实走查，2026-09-19）**：Chrome 扩展仍不可用（`os error 10061`），改用**内置浏览器**完成。PC 登录页正常（非白屏）、`admin` 登录成功、控制台干净加载无错误无 404、侧边栏 4 组结构正确、**15 个 PC 路由页全部渲染无白屏**、排班「批量工具」与工单「新建/自动派单」交互正常；移动端 Tabbar 5 项、首页宫格、我的工资单（￥7,532）、我的 KPI（69.5 分 / 第 42 名）均正常。

**关键缺陷修正（会致 PC 端整体白屏）**：`@admin` 别名指向 `../hrm-admin/src`，而 hrm-admin 自带 `node_modules`，导致 `pinia` / `vue` / `axios` 各被解析成**两份物理副本**。`createPinia()` 设置在 A 副本的 `activePinia`，而 `@admin/stores/auth.js` 的 `defineStore()` 读 B 副本（恒 `undefined`）→ `useAuthStore()` 抛 `Cannot read properties of undefined (reading '_s')`。修法：`vite.config.js` 加 `resolve.dedupe`（证据：去重前 `_s.get(` 出现在两个 chunk，去重后仅 1 个；模块数 3977 → 2318）。**dev 与 build 下都会崩，编译通过≠能跑。**

**契约缺陷修正 5 处**：`GET /work-orders/dispatch-rules` 补 ADMIN 角色；`GET /kpi/scores/:employeeId` 误读查询串致恒 400（改读路径参数）；`GET /finance/payroll-rules/:id` 的 404 统一为 9401；入职 `CREATE_ACCOUNT` 未落定薪行致流程卡死；`kpiStore.js` 重复声明 `scoreOf` 致编译失败。

## 环境约束（硬约束）

| 依赖 | 状态 | 影响 |
| ---- | ---- | ---- |
| Node 24.19.0 / npm 11.17.0 | 可用 | H5 可本地跑 |
| JDK | 缺失 | 后端不可运行 |
| Android SDK / adb / gradle | 缺失 | 安卓壳仅骨架，**未编译验证** |
| MySQL / Redis | 缺失 | 必须 Mock |
| TRAE Chrome 扩展 | **不可用（os error 10061）** | 改用**内置浏览器**已完成首轮走查，但窗口固定 810×658，宽屏与真机尺寸未覆盖 |

## 未完成 / 遗留

1. **浏览器走查仅覆盖单分辨率**：内置浏览器窗口固定 810×658，未能在 ≥1280px PC 宽度与真实手机竖屏下验证布局与响应式。窄窗已观察到的现象（侧边栏自动折叠为纯图标、排班页头折 3 行且计数被浮层遮挡）需宽屏复测后判定是否为真实缺陷。待复核项：`--gauge-fill` vs `--gauge-track` 对比度约 3.1:1 属临界；表格内联按钮焦点环是否被 `overflow:hidden` 裁切
2. **未验证**：站长账号 `st001_admin` 的落地页与越权重定向；移动端其余 Tab（打卡/包裹/工单/通知）内容页
3. **安卓壳未编译验证**：AGP 8.7.0 为占位版本，补丁号需在 Google Maven 复核
4. 字典暂分两处维护：`pc/config/dict.js` 与 `mobile/constants/business.js`（`shared/constants/dict.js` 未解冻），存在两端口径分叉风险，已标 `TODO(扩展)`
5. 跨零点夜班时段不支持（`endTime = 24:00` 在时间选择器退回 `23:59`）
6. 驳回指定退点（`targetStepKey`）契约缺失，前端下拉置灰未伪造无效控件
7. 试算预览为前端镜像实现（`pc/utils/payrollPreview.js`），与 Mock 算薪内核已实测一致，头部注明迁移到服务端试算接口的路径
8. 移动端 KPI 弹层仅支持改权重/目标值/启用，不支持增删指标与评分规则（PC 才有）
9. 移动端权重 stepper 热区提到 44px（既有先例为 32px），视觉协调性待走查确认
10. 少量 P1/P2 标 `TODO(扩展)`：PC 菜单待办角标、工资单导出、流程模板配置、移动端催办等
11. **尚未提交 Git**：`hrm-demo/` 整目录仍未纳入版本管理。⚠️ **当前工作区分支为 `feature/二期采集端`**（非本文档预期的 `feature/三端演示Demo`）——本轮改动因目录未跟踪而不受分支影响，但**提交前必须先确认目标分支**
12. **M7 未覆盖**：真实手机竖屏（375px）宽度下的 4 列宫格换行与触控热区；真实打卡成功链路（走查时已过打卡时间窗，未制造真实记录）
13. **消息「待办」计数口径待产品确认**：现实现为「各待办分组 total 之和」（与 Tabbar 角标同口径），而设计稿示意图更像「有待办的分组数」；改口径只需动 `MessagePage` 一处 computed
14. `pickupRate` 既有口径为「今日取件 / 今日入库」（现 21.9%），语义偏怪，属既存设计，未在本次范围
15. **端口 5188 曾被两个服务同时监听**（vite dev 绑 IPv4 + 交付包 `serve.mjs` 绑 IPv6），表现为「产物更新了但页面还是旧的」。已清掉 dev 进程，并给 `serve.mjs` 加了 `EADDRINUSE` 显式报错。**排查此类问题先确认监听进程数与实际加载的 chunk 哈希**
16. **M8 已知限制（均已登记，属设计取舍）**：CSV 16 列不含 `scope` / `optionSetKey` / 选项排序，导入按约定兜底；「全局默认正在引用」的选项删除仍为硬阻断（引导改为停用），未走 confirm；导入预览的行号在「含换行的引号单元格」之后可能错位（契约 `rows` 未回带原始字段）；下载的模板示例行不保证「下载即导入成功」；**无「新建选项集」端点**，故新增单选型配置项必须复用已有选项集
17. 移动端只读采集状态卡仍读旧字段（`frequency`/`dataSource` 的旧码形式），未改为读 `values`；PC 采集配置抽屉已改为读选项集
18. **M9 已知限制**：`document.execCommand('copy')` 已被规范废弃，但**安卓壳 WebView 与非 HTTPS 场景下 `navigator.clipboard` 不可用，它是唯一兜底通道**（已标 `TODO(扩展)`：壳升级 + 全站 HTTPS 后可删）；移动端工单列表右侧为放复制按钮预留 52px，状态与元信息的换行位置略有变化，**需真机窄屏复核观感**；移动端登录切换按钮上下各被 `.van-cell { overflow:hidden }` 裁掉 1px（有效约 42×44），无实质影响但真机建议目视一次
19. 工单复制文本中的「SLA 截止」标签在 PC 详情页界面也叫「SLA 截止」，但该字段的语义是「超时未处理时限」；如需统一为「超时未处理时限」需同时复制文本与两端详情页文案，本次未动

### M10 新增登记（均属设计取舍或环境限制，非缺陷）

20. **husky git hook 未激活** —— `.git` 位于仓库根、`hrm-demo` 是子目录，husky 9 明确不支持子目录安装（`npx husky init` 输出 `.git can't be found`）。`.husky/pre-commit` 与 `lint-staged.config.js` 已就绪但**不会触发**。两方案待选：① 在仓库根执行 `git config core.hooksPath hrm-dev/hrm-demo/.husky/_`（**会改仓库级 git config，属需人工确认的操作，未擅自执行**）；② 把 husky 挪到仓库根，钩子内按路径过滤 `hrm-dev/hrm-demo/` 范围
21. **覆盖层（Y2）线性劣化未修** —— `parcelSummary`(全站) 中位数 0 条 7.4ms / 1 千 12.6ms / 5 千 15.1ms / 1 万 21.1ms / 5 万 60.9ms。当前演示量级（≤千条）不构成阻塞；已在 `overlay.js` 登记 `TODO(扩展)`（增量聚合或按 status·驿站分桶缓存，预期把 1 万条档的 +14ms 压回 <3ms），需专门一轮带等价性回归
22. **`file://` 下 ESM 能否在安卓 WebView 加载仍未验证**（本机无 Android SDK）→ 离线包方案**未实施**，仅在 `vite.config.js` 登记 `TODO(扩展)`。且 `base` 是全局配置不能按入口分（PC history 需 `/`、`file://` 需 `./`），属结构性冲突
23. **`build:prod` 的 hidden sourcemap 含源码文本** —— 必须与运维约定 `dist/**/*.map` 不可公开访问（本次交付的 demo dist 无 `.map`，仅影响生产交付）
24. **仍是「有条件可上线」**：① 生产侧需复刻 `serve.mjs` 的深链回退规则；② `.map` 禁公开；③ 公网暴露前做 Element Plus 按需引入（pc 首屏 gzip 451 KB → 约 250–290 KB）；④ 接生产量级数据前关注 Y2
25. **仍无法在本机验证的项**：真机 375px 竖屏（宫格换行/触控热区/工单行 52px 复制位）、≥1280px 宽屏 PC（侧栏折叠、PageHeader 折行、`.app-main` 居中后与顶栏左缘是否错位）、移动真机横屏 640 档、iOS Safari（`parseTime` / `execCommand` / `100vh`）、键盘导航全链路、壳内 `--status-bar-height` 实测值
26. **壳侧仍需补齐**：`getWifiInfo` 未实现（`bridge.js:52` 恒回落模拟值）；`assets/h5/` 仅 `.gitkeep`（H5 未注入壳）；debug 未开 `setWebContentsDebuggingEnabled`；`local.properties` 无 `sdk.dir`；`agpVersion=8.7.0` 为占位版本
27. **`MAKEUP_STATUS` 仍是两份**（`mobile/constants/makeup.js` 与 `MakeupAttendance.vue` 就地声明）。本轮只统一了文案（「审批中」），未迁入 `shared/constants/dict.js`；`makeup.js` 头部「本轮不得改共享层」的注释现已过时
28. **ESLint 剩余 24 条 warn**：21 条为 a11y（`label-has-for` / `static-element-interactions` / `click-events-have-key-events`），2 条在**冻结的 `verify-mock.mjs`**（不为 lint 改写 754 项验收资产），1 条 `vue/attributes-order`。按「渐进放行」策略保留，逐迭代收敛
29. **PC 404 为顶层独立路由**（不在 layout 内）→ 无侧栏导航上下文。UX 审核判定「可接受」，若需保留导航框架需把 catch-all 挪进 `/` 的 children（会改现有路由结构）
30. **favicon 为内联 SVG data-URI**（按红线不引二进制资源）；极老 WebView 下可能不渲染图标，但已消除 `/favicon.ico` 404
31. **`--step-gap`（PC 16 / 移动 12）** 属组件级 L3 尺寸差异，已在 `tokens.base.scss` 头部注释的白名单中，但**设计文档无「平台差异白名单」章节**（核实结论），未做文档改动
32. **`coverage/coverage-summary.json`** 为 `test:coverage` 副产物并被 prettier 规范化；若不希望入库需另加 ignore

## 注意事项

- **网页一律用系统默认浏览器打开**（本机默认 Edge），不使用内置预览面板：内置预览窗口固定 810×658，无法验证宽屏与真机布局。启动器走 `Start-Process <url>`（ShellExecute 语义），可用 `HRM_BROWSER` 环境变量强制指定已安装的浏览器
- 演示账号密码统一 `demo1234`，仅存在于 Mock 数据，非任何环境真实凭据
- `hrm-admin`、`hrm-server` 保持零改动，删除 `hrm-demo` 即可完全回滚
- 一期 D/E 阶段验收仍未执行（需服务器环境）

***

# 会话状态 — 二期采集端（驿站包裹数据采集）

> 最后更新: 2026-09-19
> 状态：**规划与设计阶段完成，未开工编码；P0 前提未闭环**

## 交付物

| 交付物 | 路径 |
| ---- | ---- |
| 站点结构分析 | `hrm-dev/docs/collector-site-analysis.md` |
| 前端技术检查 | `hrm-dev/docs/collector-site-tech-audit.md` |
| 架构选型 ADR（21 条决策 + §19 多源采集策略 + §20 凭据托管） | `hrm-dev/docs/collector-architecture-adr.md` |
| 项目规划 v1.3（P0–P5 + §12–§15 演进机制 + 多通道 + 凭据托管任务） | `hrm-dev/docs/collector-project-plan.md` |

## 进度

- [x] 目标站点结构分析（DOM / 接口 / 分页 / 筛选 / 导出入口）
- [x] 前端技术检查（Elements / Console / Network / 性能；响应式六档未实测）
- [x] 架构选型 ADR（路线 C 主力 / A 加速 / B 并行 / D 排除）
- [x] 三条 CDP 补丁规格（下载行为 / `Download is starting` / `contexts[0]`）
- [x] 项目规划：阶段划分、任务清单、角色分派、质量关口
- [x] 长期演进机制：质量属性保障、迭代优化机制、文档体系、团队协作流程
- [x] 多源（多通道）采集策略 → ADR §19（通道 C1/C2/C3/A/B、通道选择器、降级链、导出限频）
- [x] **V10 决议：放开凭据代管**（红线 C2/C3/C5 与 ADR-C02/C05/§10.2 已修订；不做清单见 ADR §20.9）
- [x] V11 MySQL 侧闭环（一次性隔离库验证后已 DROP）
- [ ] P0 前提闭环（未开工）
- [ ] P1 取数 PoC（未开工）

## 关键事实（实测）

- 入口 `mcmd.pinduoduo.com` **只是营销落地页**；真实工作台为 `mdkd.pinduoduo.com`，接口域 `mdkd-api.pinduoduo.com`
- 主数据接口 `POST /api/codelivery/data/query_waybill`；服务端分页 `pageNo`/`pageSize`(默认 50)；`total` ≈ 133965
- 接口请求头含 `anti-content` 风控签名 + `etag`/`pdd-id` 设备标识 → **不逆向、不伪装**；改走「人工已登录浏览器 + CDP 旁路读取」
- 页面纯 CSR（JS + CSS 解码 ≈5.8MB），FCP 688ms / load 1147ms；DOM 无业务 `data-*` 锚点，定位只能靠 class + 列名文本
- 布局为固定宽度桌面端（`min-width:1280px`），非自适应
- 平台原生导出入口存在：运单查询页「导出Excel」→ 作业中心「下载任务」（任务约 1 天失效）；**能力四项未验证（V2）**

## 阻塞项（未闭环，构成 P0）

| 编号 | 事项 | 影响 |
| ---- | ---- | ---- |
| ~~V10~~ | ~~集中部署下的登录人工介入通道~~ → **已决议**：放开凭据代管（ADR §20），转为实现任务 P2-09~P2-11 | 生效硬前置未满足前**不得启用**（授权书/加密验证/审计/用户确认） |
| V2 | 导出能力四项（上限/筛选/格式/频次） | 决定首灌是否走 2680 页分页；**需用户授权点一次** |
| V8 | robots / 条款的爬取延迟要求 | 限速间隔下限目前**无依据**，容量推算悬空 |
| V3 / V9 / V11 / V5 | 留存上限 / 容量核算 / 分区 SQL / 开放平台 API | 见规划 §2.2 |
| V12–V16 | 导出限频阈值 / HAR 开销 / CDP 长跑资源 / 取 body 失败率 / 通道健康度口径 | 见 ADR §19.12 |
| V17–V22 | 登录方式实测 / 会话有效期 / 自动登录风控表现 / DPAPI 可用性 / 自愈成功率 / 明文零落地可验证性 | 见 ADR §20.8 |
| K18–K21 | 凭据集中托管单点失效（高）/ ToS 可能禁止账号共享与自动登录（高）/ 自动登录触发风控（中）/ 授权撤回后凭据残留（中） | 见 ADR §20.7 |
| Q1–Q6 | 待用户拍板 | 见规划 §8 |

## 注意事项

- **分支未切换**：当前仍在 `feature/三端演示Demo` 且存在未提交改动；按规范应切 `feature/二期采集端`（从 `dev` 切出），**未擅自执行**，待确认
- 上述 4 份 collector 文档**尚未提交 Git**
- 全程只读、限速、无伪装；未触发风控熔断
- 桌面工作稿《二期采集端-开发项目规划.md》不入 Git，冲突时以仓库文档为准

***

# 会话状态 — 三期 · 企业微信接入（群工单 + 取件通知）

> 最后更新: 2026-09-20
> 状态：**架构设计完成（ADR v1.1），待用户评审；未开工编码**
> 分支：`feature/二期采集端`（本轮产出未提交 Git）

## 交付物

| 交付物 | 路径 | 状态 |
| ---- | ---- | ---- |
| 企业微信接入 架构设计 ADR | `hrm-dev/docs/wecom-integration-adr.md`（v1.1，≈930 行，24 章 + 2 附录） | 已评审通过 |
| **第一批 POC 套件** | `hrm-dev/poc/wecom/`（POC-01 调试端口探测 / POC-02 UIA 读群消息 / POC-05 限频与超长 + 执行手册 + 结果回填模板） | **已推 Gitee `3653915`，待采集机执行** |

## 进度

- [x] 需求拆解与平台能力边界核实（主智能体，查企业微信官方文档）
- [x] 架构设计 ADR（架构师 `parcel-station-architect`）
- [x] 主智能体 Review（修正 3 处，见下）
- [x] 用户评审：ADR 通过；Q7–Q12 按保守建议落定 —— Q7 条件触发不预先决策、Q8 接受 `[工单] 键=值` 约定模板、Q9 保持人工确认、Q10 群通知白名单内免确认、Q11 单机单实例、Q12 不启用凭据代管
- [x] **环境实测（2026-09-22）**：开发机 **无企业微信客户端 / JDK / Maven / Gradle / Docker / MySQL / Redis**；仅有 Python 3.13.12（**与 ADR/二期假设的 3.12 不一致**）、Node 24.19.0、Git 2.55.0
- [x] **上机通道探测**：SSH MCP 仅配 1 个连接 `root@156.225.23.154:22`（Linux，宝塔服务器），**无采集端 Windows 通道** → POC 改为「开发机出套件 → Gitee → 采集机执行 → 回推结果」模式
- [x] 双向同步通道就位：开发机 ↔ 采集机 `D:\yizhan`，分支 `feature/二期采集端`，upstream 已建
- [x] **第一批 POC 套件已交付并推 Gitee（`3653915`）**：零安装优先（POC-01 纯 PowerShell；POC-02 主方案 PowerShell + .NET UIAutomationClient，pywinauto 仅备选；POC-05 Python 标准库 urllib + PS 备选）
- [ ] **用户侧：在采集机执行第一批 POC 并回填 `结果回填模板.md`** ← **当前卡点**
- [ ] POC-03 外部群（**V24 顺延**，用户确认只有内部群可测）/ POC-04 仿人工发送（依赖 POC-02 结论）/ POC-06 虚拟列表增量（依赖 POC-02 通过）
- [ ] 编码（未开工）

### POC 套件本批纪律与已知限制

- **本批只做 POC-01 / POC-02 / POC-05**：POC-04 依赖 POC-02 的控件定位结论且属写动作（需走授权流程），POC-06 依赖 POC-02 通过，故均顺延
- **POC-05 是本批唯一写动作**：硬性护栏为 30 条总上限 / 3s 最小间隔 / 必须 `--confirm` / `STOP` 标志可中止 / webhook 仅从环境变量 `WECOM_POC_WEBHOOK` 读取（缺失即非 0 退出、无内置默认值）/ 输出只出现 `key=****`
- **已知设计取舍**：21 条 × 3s 的探测节奏**恰好卡在 20 条/分钟上限**，可能「未触发限频」—— 属安全优先的有意选择，**不得为凑结果缩短间隔**，如实回填「合规未触发」
- **开发机自检范围**：仅语法级（PowerShell `[Parser]::ParseFile` + Python `py_compile`）+ 护栏冒烟（缺变量退出码 2、缺 `--confirm` 退出码 3，均不发消息）。**企微是否真暴露 CDP 端口、UIA 能否真读到消息文本、真实限频边界与 40058 文案 —— 全部未验证，不得声称已通过**
- 子智能体修复的真实缺陷：`.ps1` 初版 UTF-8 无 BOM 致 Windows PowerShell 5.1 按 GBK 解码报「字符串缺少终止符」→ 已改 UTF-8 带 BOM
- **遗留一条**：`执行手册.md` 第 5 节的自检命令里含 `ReadProcessMemory` 等字面量（作为「不得使用」的说明文本）。将来若上 CI 关键词黑名单，**扫描范围须限定为代码文件**，否则会误报文档

## 关键事实（已核实来源，反幻觉核过）

| 通道 | 免费 | 读群消息 | 发消息 |
| ---- | ---- | ---- | ---- |
| 群机器人 /「消息推送」Webhook | ✅ | ❌ 官方：「暂不支持设置消息回调配置」（帮助中心 docid=14931） | ✅ 仅主动推送，**≤20 条/分钟/机器人**，markdown ≤4096 字节（超限 40058） |
| 自建应用「接收消息」回调 | ✅ | ❌ 官方：「发送消息到群聊会话……（**暂不支持接收群聊消息**）」 | ✅ 发给应用可见范围成员 |
| 会话内容存档 msgaudit | ❌ 付费（≈450–950 元/人/年 + 声纹验证 + 员工告知 + 私有化 SDK） | ✅ **唯一**官方合规读群消息通道 | ❌ |
| 腾讯官方开源 `WecomTeam/wecom-cli`（MIT） | ✅ | ❌ README 未声明群消息拉取（第三方转述的「消息记录拉取」与官方 README 不符，**不作依据**） | ✅ |
| 企微 PC 客户端旁路（CDP / UI Automation） | ✅ | ⚠️ **未验证** | ⚠️ 未验证 |
| DLL 注入 / 协议逆向类 | — | ✅ | ✅ | **红线 C3 + W-C1 直接排除** |

**核心结论**：在不付费、不逆向的双重约束下，官方通道**无读群消息能力**；用户选定的「桌面端旁路」是唯一可能路径，但**可行性属未验证假设** → 需求 1 存在根本性风险（K22）。

## 用户已拍板（2026-09-20）

1. 路线：Windows 10 官方 PC 客户端本地登录 + 桌面端旁路读取 + **UI 层**仿人工发送；不付费、不逆向、不注入。
2. 通知对象：驿站客户微信群（外部群）**与**客户个人微信（1 对 1）。
3. 技术栈：Python 采集端（沿用二期已验证的 Python 3.12 栈）+ Java 服务端（复用 `hrm-server`，部署 kongzhen1.com）。
4. 节奏：先出架构设计 + ADR，评审通过后再编码。

## 主智能体 Review 修正（v1.0 → v1.1）

1. **限频计数维度**：20 条/分钟是**每机器人**而非每群 → 限频桶键必须是 `robot`；否则「多工作群并发」（需求 1 明确要求）在 N≥2 时必然超限。新增 **V35**（同机器人跨 2 群并发验证）。
2. **新增红线 W-C10 + 决策 ADR-W24**：群机器人 webhook `key` 本身即凭据（持 key 即可向群发消息），原 W-C8 只覆盖登录态与账号凭据 → 补齐「仅存服务端加密存储、不下发采集端、不进 Git、日志与出参掩码、泄露即轮换」。
3. **反幻觉修正**：「外挂」定性的表述**转引自服务商公开解读**，非官方协议条款原文 → 已修正来源归属 + 新增 **V34**，并限定 K24 未闭环前不得对外声称「已确认合规」。

## 待用户拍板（Q7–Q12，ADR 第 21 章）

- **Q7** 读取通道 POC 全败时如何处置（付费存档 / 人工录入 / 暂停该需求）
- **Q8** 是否接受群内工单使用约定模板 `[工单] 键=值`（不接受则自动化程度大幅下降）
- **Q9** 个人微信 1 对 1 是否开启全自动（**建议保持人工确认**）
- **Q10** 客户群通知是否也纳入人工确认
- **Q11** 采集端部署形态（单机单实例 / 单机多实例 / 多机）
- **Q12** 是否启用凭据代管（自动登录）

## 未验证项（V23–V35，13 项）与风险（K22–K31，10 项）

**最可能导致方案整体失败的前 3 个前提**：
1. 企微 PC 端既不暴露 CDP 端口、UI Automation 也取不到消息文本 → G1/G2 无解（**K22，最高**）
2. 外部客户群不支持群机器人 Webhook → 取件通知全压到未验证的仿人工通道（K23）
3. 「本地官方客户端 + 仿人工」仍被官方判定为外挂 → 账号封禁 + 合规事故（**K24，最高**；**无技术解**，仅靠人工确认闸门 + 非批量 + 白名单 + 一键停用 + 全量审计缓解）

## 注意事项

- ⚠️ **分支陷阱（2026-09-22 实际踩到，务必先看）**：同步测试后工作区分支停在 **`dev`**，而 `dev` 跟踪的是 **`github/dev`（GitHub）**；采集机走的是 **Gitee `origin` 的 `feature/二期采集端`**。**在 `dev` 上提交再 push，采集机拉不到**。第一批 POC 套件首次即误提交到 `dev`（`0c4ca85`），已 cherry-pick 到 `feature/二期采集端`（`3653915`）并推 Gitee；**`dev` 上的 `0c4ca85` 未清理**（取非破坏性处理，待用户裁决是否回退 `dev` 至 `6fef352`）。**此后每次提交前必须先 `git branch --show-current` 确认在 `feature/二期采集端`**
- 两个远端并存：`origin` = Gitee `git@gitee.com:jia-yongbin/yizhan.git`（**采集机同步走这个**）；`github` = `github.com/jyb4-3514/kdyzgl-base.git`
- **可先行的范围**（不依赖未验证能力）：服务端编排 / 留痕 / 一键停用 / 群机器人 Webhook 通道骨架（对应 S3/S5/S6）—— 但开发机**无 JDK/Maven**，其可编译验证性同样受限，不宜盲写
- 开发机 Python 为 **3.13.12**，与 ADR / 二期采集端假设的 **3.12** 不一致 → 后续采集端选型需复核（登记为待验证项）
