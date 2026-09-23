# 会话状态 — 三端 Demo（网页端 / 老板端 / 员工端）

> 最后更新: 2026-09-23（本次追加「并行工作流 · 服务器存储与登录收敛」）
> 状态：**需求 1–10 已全部实现并通过契约与构建验收，交付包已归档到桌面**
> 分支：`feature/三端演示Demo`
> 里程碑台账见 `hrm-dev/docs/demo-milestones.md`

## 并行工作流 · 服务器存储与登录收敛（2026-09-23）

> 与三端 Demo 主线并行，改动集中在部署态与服务器磁盘，**未触碰 Demo 源码主线**。
> 分支：`feature/前端演示项目拆分与精细化`（工作区改动，**尚未 commit**）

### A. Demo 登录收敛（删弹窗，只留站内登录）

| 项 | 内容 |
| ---- | ---- |
| 需求 | 移除全部额外验证步骤（验证码/双因素/安全问题），登录仅「账号 + 密码」；预留账号 `16626369983` |
| 根因 | 生产站点弹窗来自 `hrm-demo-static` 容器内的 Basic Auth 网关（非 `courier-nginx` 现网栈） |
| 改动 | 删 `auth_basic`（nginx.conf / Dockerfile / docker-compose.yml / deploy-demo.sh / .env.example），`docker-entrypoint.d/10-basic-auth.sh` 已删除；`hrm-demo/src/shared/mock/db.js` 新增预留账号（ADMIN、`pwdChanged=1`） |
| 验证 | 线上 `https://kongzhen1.com/` HTTP/2 **200 且无 `WWW-Authenticate`**；Mock 内核直跑：正确口令 200+token、错误 1001、短账号/空密码 400、auth 路由仅 4 个且无 captcha/otp/mfa；`verify:mock` 879/879、单测 300/300 |
| 代价（已知悉） | 站点完全公开；口令 `Aa16626369983..` 已进公开 JS 产物，**若与其他真实系统同口令须轮换** |
| 文档 | `hrm-dev/docs/demo-docker-deploy.md` 已同步 5 处 |

### B. 服务器存储策略落地（清理 → 装库 → 定策 → 验证）

| 项 | 内容 |
| ---- | ---- |
| s1 清理 HIDS 日志 | 宝塔入侵检测日志 `/www/server/panel/data/hids_data/log` **4.7G → 1.8G**；先归档 `/data/backup/hids-log-backup-20260923144522.tgz`（185M，24 条目校验通过） |
| s2 清理系统盘 | journal vacuum 释放 176M；`/www/backup/{panel,php56.Bak,php-fpm56.Bak}` 224M **移动**（非删）到 `/data/backup/www-server-backup-20260923144948/`；`docker builder prune -af` + 删无用镜像 `nginx:1.27-alpine`。**保留回滚镜像 `hrm-demo-static:20260922-1`**（78M，是既有回滚路径） |
| s3 增长治理 | 新增 `hrm-dev/deploy/scripts/hids-log-rotate.{sh,cron}` → 服务器 `/usr/local/bin/hids-log-rotate.sh` + `/etc/cron.d/hids-log-rotate`，**每周日 03:30 轮转、只留近 7 日**；日志写 `/data/log/hids-log-rotate.log` |
| s4 装宿主 MySQL | 先补 2G swap（`/data/swapfile`，`vm.swappiness=10`）→ apt 装 `mysql-server 8.0.46`；`rsync` 迁移初始 datadir → `/data/mysql-host`（180M）；配置文件 `hrm-dev/deploy/scripts/zz-kdyzgl-storage.cnf` → `/etc/mysql/mysql.conf.d/`；apparmor local 放行 `/data` 并重载；`systemctl enable --now mysql` |
| s4 关键取舍 | **端口取 3307**（3306 被现网容器 `courier-mysql` 占用），**不停现网容器**；是否把 `courier-server` 切到宿主实例留作独立决策 |
| s5 验证 | `is-active=active`/`is-enabled=enabled`；`@@datadir=/data/mysql-host/`、`@@port=3307`、`@@log_error=/data/log/mysql/error.log`、`@@tmpdir=/data/mysql-tmp`；探针库建表读写成功且 `t_probe.ibd` 落在 **`/dev/vdb1`**；`/var/lib/mysql` 4.0K / 0 文件、全盘无 `.ibd/.frm`；3307/33060 仅监听 127.0.0.1；探针库已 DROP；5 容器全 healthy；`kongzhen1.com → 200` |
| 磁盘结果 | 系统盘 `13G/43% → 8.7G/31%`；数据盘 `/data` `2.8G/49G = 7%` |
| 文档 | `hrm-dev/docs/deploy.md` 新增 **§0.5 存储策略（系统盘/数据盘分工）** + §0.3 端口表补 3307 行 |

### 遗留待决策（勿擅自推进）

1. 是否把 `courier-server` 从容器 MySQL(3306) 切到宿主实例(3307) —— **切换会中断现网**，需独立评估。
2. 是否创建生产库 `kdyzgl` 与 `hrm_app` 账号（涉及凭据，须按项目规则 §7 由主智能体授权）。
3. 上一轮 HTTPS 修复在服务器侧产生 commit `691c794`，因 DeployKey 只读未 push，需从服务器拉回本地再推 Gitee。
4. 口令 `Aa16626369983..` 已进公开 JS 产物，若与其他系统同口令须轮换。
5. `hrm-dev/deploy/docker-demo/.env.example` 被根 `.gitignore` 的 `.env.*` 规则挡住未跟踪，与规则 §4 要求不符。

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

***

# 会话状态 — 二期采集端 · 登录模块实装（Windows 10 采集机）

> 最后更新: 2026-09-22
> 状态：**登录链路已全线打通至「人工介入点」；唯一剩余阻塞 = 需人在采集机桌面完成滑块验证**
> 分支：`feature/二期采集端`（远端 Gitee `origin`）；作业隔离在 git worktree `C:\Users\16626\AppData\Local\Temp\yz-wt`

## 交付物

| 交付物 | 路径 |
| ---- | ---- |
| 采集端 Python 工程 | `hrm-dev/collector/`（`src/collector/` + `scripts/` + `tests/` + `config/`） |
| 环境自检与安装（幂等） | `hrm-dev/collector/scripts/env-setup.ps1` |
| 多多账号密码写入（DPAPI） | `hrm-dev/collector/scripts/init-local-secrets.ps1` |
| 登录页结构只读诊断 | `hrm-dev/collector/scripts/dump-login-page.py` |
| 使用与故障处理 | `hrm-dev/collector/README.md` |

## 采集机环境（实测，均为本次安装）

| 项 | 值 |
| ---- | ---- |
| 主机 | `PC-20260112CXLA`，Windows 10 19045.4291 |
| SSH 通道 | `mcp_ssh-collector` → `jyb4351469@192.168.112.192`（**管理员，High Mandatory Level**） |
| 仓库 | `D:\yizhan` |
| Python | **3.13.15** → `C:\Program Files\Python313`（**非 3.12**：3.12 自 3.12.11 起为「仅源码」安全发布，**无 Windows 安装器**；最后一个带安装器的 3.12.10 停在 2025-04） |
| MySQL | **8.0.46** 本地服务 `MySQL80`，**仅监听 127.0.0.1**；库 `yizhan_collector`，应用账号 `yizhan@127.0.0.1`（最小权限） |
| 依赖 | playwright 1.63.0 / PyMySQL 1.2.3（含 rsa）/ pywin32 312；pytest **234 单测** |
| 浏览器 | Chrome for Testing 经 **npmmirror** 安装成功 |

## 关键实测事实（含踩坑，均已闭环）

1. **站点地址之前给错**：`mcmd.pinduoduo.com/home` 是**营销落地页**（`input 数=0`，标题「快递代收官网」）；真实登录页为 **`https://mdkd.pinduoduo.com/login`**（`/` 会 302 过去），标题「代收点」。**印证二期 ADR 早已核实的结论**。
2. 登录页三种方式（短信/密码/微信），**默认停在短信登录**，必须点「密码登录」页签；其 DOM 中 `#mobile` 与 `button.login-btn` **各存在两份**（两个 tabpane 同时挂载），靠可见性与作用域区分。活动面板锚点：`div[role="tabpanel"][aria-hidden="false"]`。
3. **下载源实测**：python.org **655 B/s**（等同不可用）→ npmmirror **2640 KB/s**；MySQL 国内高校/云镜像全 403/404，仅 **cdn.mysql.com** 可用（236MB / 42 秒）。
4. **分离进程会被回收**：`Start-Process` / `start /b` 拉起的子进程随 SSH 会话结束被杀（实测安装器只下了 12KB 就断）→ 必须用 **WMI `Win32_Process.Create`** 才能存活。
5. **PowerShell 编码**：`.ps1` 必须 **UTF-8 带 BOM**，否则 PS 5.1 在中文系统按 GBK 解码、中文注释变乱码 →「字符串缺少终止符」。**本项目已因此踩坑两次**。
6. **`mysqld --install` 参数顺序**：`--install` 必须排在 `--defaults-file` **之前**；反了报 `unknown option '--install'` 且**只写 mysql-error.log、不落控制台**（静默失败，靠服务不存在反推）。
7. **SSH 进程在 Session 0，交互桌面在 Session 2**（`quser`：`administrator rdp-tcp#0 ID=2`）→ **SSH 拉起的浏览器窗口用户在桌面上看不见**。

## 登录链路实测结果（逐段确认）

| 环节 | 结果 |
| ---- | ---- |
| 打开真实登录页 | ✅ |
| 切到「密码登录」页签 | ✅ |
| 仿人工逐字符填账号 + 密码 | ✅ 自检日志「账号 11 字符、密码 13 字符（仅记录长度）」 |
| 提交（四条降级链：回车 → 真实点击 → 强制点击 → 合成点击） | ✅ 回车未生效 → 自动降级 → **真实点击生效** |
| 识别滑块并立即停止自动化 | ✅ `MANUAL_REQUIRED`，退出码 2（**设计行为，红线 C2/C8 要求**） |
| MySQL 登录态持久化 | ✅ `session list` 有记录，账号脱敏 `166****9983`，时间 UTC |

## 本次修复的真实缺陷（全部由实跑暴露，非推测）

1. **`获取验证码` 被误判为图形验证码挑战** → 未提交表单即 `MANUAL_REQUIRED`（退出码 2），登录永远走不通。修法：匹配前剔除登录页自身控件文案（**不删通用模式**，避免真挑战漏检）。回归网 `tests/test_challenge_false_positive.py`。
2. **提交按钮被遮挡**：`button.login-btn` 被活动面板内 `input#mobile` 与 `div.rocket-tabs` 拦截 `pointer events`，30 秒硬等不自愈。修法：四条降级链 + 生效信号判定 + 填表自检（密码只记长度不记值）。
3. **检出挑战后立即关浏览器**，提示却说"请在浏览器窗口完成验证" → **人工无从介入**。修法：新增 `login --manual`（保持浏览器打开、只读轮询登录态、交人工完成挑战）。

## 阻塞项（当前唯一）

**需人在采集机桌面完成滑块验证**，且**账户必须一致**：
- DPAPI 密钥由 **`jyb4351469`** 生成（**CurrentUser 作用域**）
- 交互桌面当前登录的是 **`administrator`**
- → **在 `administrator` 下运行必然解密失败**（「密文与当前 Windows 用户/机器不匹配」）
- 建议：**以 `jyb4351469` 做 RDP/桌面登录**，让「运行账户」与「能看见桌面的账户」统一，再执行 `python -m collector login --manual`

## 注意事项

- 本轮全部提交都在 `feature/二期采集端` 并已推 Gitee；**主工作区（同事的前端分支）全程未被触碰** —— 因分支被并发切换，改用 `git worktree` 隔离作业
- 首笔提交曾误落到 `dev`（`0c4ca85`，该分支跟踪 **GitHub** 而非 Gitee），已 cherry-pick 到 `feature/二期采集端`；**`dev` 上那笔未清理**，待用户裁决
- `init-local-secrets.ps1` 调用时密码经**命令行**传入（且明文早已在会话中）→ 建议本轮跑通后**轮换多多账号密码**
- `settings.toml` 为本地文件（未入库）；配置模板变更后需在采集机从 `settings.example.toml` 重新复制
- 凭据**绝不进 Git**：`config/local/*` 已被 `hrm-dev/collector/.gitignore` 排除，密钥文件为 DPAPI 密文
- **ADR 待回填**：客户端版本登记（V31）、登录态有效期（V30 对应项）、`WORKBENCH_MARKERS` 仍待登录成功后实测

***

# 会话状态 — 员工端模块化拆分与精细化

> 最后更新: 2026-09-22
> 状态：**B0 基建批次已完成并提交（`638fccb`）；B1 考勤域待开工**
> 分支：**`feature/前端演示项目拆分与精细化`** ⚠️ 见下「并发冲突」——原建分支名 `feature/员工端拆分与精细化` 已被另一会话改名
> 改造深度（用户拍板）：**拆分 + UI/UX 精细化重设计**；**允许扩展 Mock 端点/字段并同步断言**

## ⚠️ 并发冲突记录（2026-09-22，必读）

同一工作区**有另一会话并发作业**，本任务的分支与工作区均被其占用：

1. `21:50` 主智能体建 `feature/员工端拆分与精细化`；`22:29:25` **另一会话将分支改名为 `feature/前端演示项目拆分与精细化`**（reflog 可证），随后提交 `2dc680d`（拆分 PC 工单/排班页）、`93f8abd`（采集端脚本）。
2. 另一会话正在做**老板端模块化**：`src/mobile/views/boss/**` 21 文件删除 → `src/mobile/modules/boss/**`（**至今未提交**），并新增 `docs/demo-boss-module-plan.md`、`docs/demo-boss-ui-spec.md`；另改了 `components/{ActionBar,MonthPicker,StationPicker}.vue`（加 `loading/error/retry` 四态）与 `router/index(.spec).js`（老板端路由抽离，`import { bossRoutes } from '../modules/boss/router.js'`）。
3. **处置（用户拍板）**：接受并发，主智能体严格控制提交范围。B0 提交 `638fccb` **只含员工端文件（57 项）**，上述并发改动一律未纳入。
4. **后续每次提交前必须**：`git branch --show-current` + `git status --short`，并逐个核对候选文件的 `--numstat` 与 diff 归属，**只 add 员工端路径**。
5. **门禁可信度提示**：`lint` 的 warn 数与 `e2e` 的页面覆盖受老板端半成品状态影响；B0 门禁是在该状态下实跑通过的（结论偏保守，非偏乐观）。
6. **e2e 前置**：`npm run e2e` **必须先 `npm run dev`**（`e2e/global-setup.js` 要连 `localhost:5188`），否则立即 `TypeError: fetch failed` 退出——这是环境前置，不是代码缺陷。

## 交付物

| 交付物 | 路径 | 状态 |
| ---- | ---- | ---- |
| 员工端拆分与精细化**架构**规范 | `hrm-dev/docs/demo-staff-refactor.md` | 已产出（v1.0，§1–§14 + 附录 A） |
| 员工端 UI/UX **精细化设计**规范 | `hrm-dev/docs/demo-staff-ui-redesign.md` | 已产出（≈596 行，9 章 + 3 附录） |

## 进度

- [x] 前置材料通读：`demo-pc-refactor.md`、`demo-design.md`、`demo-mobile-nav-redesign.md`、`demo-leave-design.md`、`demo-ux-improvement.md`、`api.md`
- [x] 源码实测：`mobile/api/index.js`(88 导出)、`utils/http.js`、`router/index.js`、`stores/*`、`constants/todoGroups.js`、`mock/engine.js`、`eslint.config.js`、`vant.js`
- [x] 首屏体积实测：`build` / `build:prod` 均 EXIT=0；移动端首屏 gzip ≈157.6 KB（JS≈113 + CSS≈44.2）；目标 ≤150 KB
- [x] 架构规范产出（架构师 `parcel-station-architect`）
- [x] UI/UX 规范产出（UI/UX 设计师 `express-station-ui-designer`）
- [x] 主智能体 Review：独立核实 8 项关键论断全部为真（`reqSeq` 在 `src/mobile` **0 命中**；`api/index.js` 88 导出；`PARCEL_STATUS[1]` 字典「在库待取」vs `parcel.vue:19`「待取件」；`sync.vue:74-75` 错误吞成空态；`sync.vue:101,142` 32px 按钮；`sync.vue:179` 整页 loading；`payroll.vue:71` 加载期显「共 0 张」；`verify:mobile` 脚本名 `verify-mobile-t13-t16.mjs`）
- [x] §14 四项待拍板已裁定：**R1** 留 B8 实测后定 / **R2** 保持 ≤150 KB 不动 `clientLog` 静态依赖 / **R5 完整收口**（用户拍板）/ **R9 保留复用 + `view` prop**（用户拍板）
- [x] **B0 基建批次已完成并提交 `638fccb`**（api 分域 13 文件 + 删 barrel + `useLatestRequest`/`useListPager` + `stores/attendance.js` + todoGroups 下沉 + http 收敛 + ESLint 6 项）
  - 门禁实测：`verify:mock` **878/878**、`verify:mobile` **48/0**、`test` **209/0**（基线 150，只增不减；新增 `useLatestRequest` 7 + `useListPager` 7 + `attendance` 6）、`lint` **0 error**（41 warn）、`build` 与 `build:prod` **EXIT=0**、`e2e` **37/0**、`hrm-admin`+`hrm-server` **零改动**
- [x] **R5 数据级权限收口已完成并提交 `5800521`**（用户拍板「完整收口」，独立 commit）
  - 新增纯函数 `shared/domain/applyDataScope(params,user)`（8 条单测）；`mock/engine.js` 统一执行；删除 6 个路由文件共 **12 处**重复 `stationId` 覆盖；保留写操作 body 收敛与资源级/本人级判定
  - `role.js` 新增 `ALL_ROLES`；engine 对「鉴权但未声明 roles」的路由**加载期抛配置错误**（消除隐性放行），**42 条**受保护路由补显式声明；`verify-mock.mjs` 新增 1 条静态断言
  - 门禁（主智能体独立复跑）：`verify:mock` **878→879/0**（既有语义一字未改）、`verify:mobile` 48/0、`test` **248/0**、`lint` **0 error**、`build` EXIT=0
  - 未做：派生标志补齐（P3）——无消费方且补卡无详情端点，留 `TODO(扩展)` 待 B2/B4 契约定稿
- [x] **B1a 子批次已完成并提交 `9d36310`**（B1 考勤域前置：共用组件与 Token 基座）
  - 新增跨域组件 `Chip(C1)/Badge(C2)/MiniChip(C3)/ListItemCard(C4)` + 员工端域共享组件 `ShiftCard(C9)`（`views/staff/components/`）；`PageState` 新增 `variant="denied"` 并替换 `views/staff/flow.vue` 手写降级块（消 G13）
  - `tokens.scss` 新增 **9 条** Token（`--touch-min`/`--row-h-1..3`/`--row-h-tile`/`--fs-badge`/`--badge-h`/`--badge-pad-x`/`--shift-bar-w`）；`tokens.base.scss` **零改动**；§3.3 禁项零出现
  - 单测 **+42 例**（Chip 8 / Badge 7 / MiniChip 6 / ListItemCard 8 / ShiftCard 8 / PageState +5）
  - 门禁（主智能体独立复跑）：`lint` **0 error**、`verify:mock` **879/879**、`verify:mobile` **48/0**、`verify:tokens` **EXIT=0**、`test` **37 文件/300 用例全过**、`build` **EXIT=0**
  - ⚠️ **e2e 36/37**：见下方「待复验项」
- [x] **附加批次（非 B1b）已完成并提交 `5181c20`**：员工端更名「驿站助手」+「我的」页标准精简
  - 命名真源 `constants/appName.js` → `resolveAppName({ as, role })`（**登录后 role 优先于 as**）；消费方：`mobile.html` 静态 title（首屏兜底）、`App.vue`（**仅员工端**写 `document.title`，老板端交还并发会话的 `router/index.js` `afterEach`，`flush:'post'` 保证不被默认标题覆盖）、登录页 h1/副标题、门户入口卡片③。安卓壳 `app_name` **刻意不改**（三端共用载体）
  - 「我的」页按 **方案 A** 拆分：员工端自组合壳 `views/staff/me.vue` + 跨端共享件 `components/{ProfileHero,AccountSecurityGroup,DemoIdentityGroup,LogoutAction}`；`MeSection.vue` 收为**仅服务老板端**（195→143 行），以 `MeSection.spec.js` 3 条护栏锁住老板端零变化
  - IA（标准精简）：用户信息区吸收手机号/所属部门（删重复的「登录账号/所属驿站/最后登录」整块）→「我的数据」二次分群（薪酬与考核 / 考勤与流程）→ 演示身份（仅 Demo 态）→ 账号安全 →「关于」**原位替换**「运行环境」（零 router 改动）
  - 门禁（主智能体独立复跑 7 项）：`lint` **0 error**（41 warn）/ `verify:tokens` EXIT=0 / `verify:mock` **887/0** / `verify:mobile` **48/0** / `test` **42 文件 336 用例全过** / `build` **EXIT=0**（首次 EPERM 失败系 `dist/index.html` 被占用的环境态，单跑即通过）/ `build:prod` EXIT=0；`hrm-admin`+`hrm-server` **零改动**
  - 提交范围控制：`views/login/index.vue`、`src/portal/main.js` 与并发会话**同文件混改**，采用「备份 → 摘出他人 hunk → `git add` → 还原工作区」的**行级剥离**；提交后已还原并发会话残余 hunk（两文件工作区仍为 `M`，归属他人）。`SESSION-STATE.md` 因多方写入，本批次**未纳入提交**
  - ⚠️ **待浏览器复核**：员工端 `document.title` 依赖 vue-router 路由对象引用变化触发 `watchEffect`，本机无可用浏览器走查 → 收敛到浏览器复核后才能声称已生效
- [ ] B1b 考勤域页面拆分（`attendance.vue` 972 行 → 壳 + `ClockHero/PeriodCard/CheckSlotRow/CheckResultPanel/VerifyCard/MakeupPopup` + composables + model；另 3 页）
- [ ] B2 工单域 / B3 包裹域 / B4 我的域 / B5 请假域 / B6 首页与消息域 / B7 同步域
- [ ] B8 性能与门禁收口 + 视觉/无障碍走查

## ⚠️ 待复验项：e2e B2-2（PC 登录页 Slow 3G 首屏）

**结论：非员工端改动引入，归属并发会话的 PC 拆分；但未闭环，不得声称 e2e 全绿。**

| 项 | 数据 |
| --- | --- |
| 本次实测（2026-09-22） | Slow 3G **383,641 ms 仍未渲染**（`locator.waitFor: Test ended`），整测 420s 预算耗尽 → 失败 |
| 2026-09-20 基线（`e2e/evidence/metrics-full-run.json`） | Fast 3G **34,606 ms** 通过 / Slow 3G **137,647 ms** 通过（预算 240s） |
| 劣化倍数 | **≈2.8×** |
| 被测对象 | `http://localhost:5188/pc.html` 的 `.login-card` —— **PC 侧，本任务全程未改动 `src/pc/**`** |
| 归因依据 | ① B2-1 端选择页在同档限速下 **610ms / 1695ms 通过**，限速链路本身正常；② 09-20 之后 PC 侧发生大拆分：`2dc680d`（工单/排班）、`69aabaa`（看板/同步/财务）、`fd695d8`（org store），dev 模式未打包 → 模块请求数激增，正是 Slow 3G 的瓶颈；③ 期间本机同时跑着并发会话的 dev/build，负载叠加 |
| 复验条件 | 并发会话提交完毕、本机无其他构建进程时重跑 `npm run e2e`（须先 `npm run dev`） |
| 附带说明 | 该用例度量的是 **dev（未打包）** 首屏，不代表生产构建表现；如需生产口径应改测 `dist` 产物 |

## ✅ 已解除：`router/index.js` 占用问题（2026-09-22 用户裁定）

B1–B7 原本都要把页面改为 `views/staff/<域>/index.vue` 并同步改 `src/mobile/router/index.js`，而该文件脏着并发会话的老板端路由抽离改动。

**裁定结果**：采用「**不改目录、不动 router**」方案 —— 页壳**保持原位路径**（`views/staff/<域>.vue`），组件 / composable / model 落**同名兄弟目录** `views/staff/<域>/`；**B1–B7 全程零 router 改动**。已落入 `demo-staff-refactor.md` **§2.1 v1.1 修正** 与 **§3.2 全局约定**（commit `0306078`）。

## 批次门禁基线（随批次上调，不得弱化）

`verify:mock` **887**（附加批次后） / `verify:mobile` **48** / `test` **336**（42 文件，附加批次后） / `e2e` **36 通过 + 1 待复验（B2-2，PC 侧）** / `lint` **0 error** / `verify:tokens` EXIT=0 / `build`+`build:prod` EXIT=0 / `hrm-admin`+`hrm-server` 零改动

## 关键结论

- 批次 **9 批**（B0 基建 + B1–B7 七域 + B8 收口），依赖 `B0 → {B1..B5,B7} → B6 → B8`
- api `index.js`（88 导出）拆为 **13 个域文件**并**删除 barrel**；38 处调用点改 import，导出名一律不改
- 新增 store **1 个**（`stores/attendance.js`）；新增跨域组件 4 + 域共享组件 1；新增 composable 2
- ESLint 新增/调整 **6 项**（3 组依赖边界 + 2 条 `max-lines` + 修正 `eslint.config.js:114` 白名单路径）
- UI 侧：P0 **8** / P1 **15** / P2 **8**；新增 Token **9** 条（全部落 `mobile/styles/tokens.scss`，真源零新增）；新增组件 **9** 个
- **PC 拆分已部分落地**（`demo-pc-refactor.md` §1 现状表对 workOrder/schedule 已过期）：`src/pc/views/workOrder/{components,composables,model}/`、`src/pc/views/schedule/composables/useScheduleMatrix.js` 均已在库，含 `useWorkOrderList.spec.js` 可作单测样板
- 本轮三个待修的结构问题：**G2** 移动端无竞态守卫（`busy` 单飞会丢弃在途筛选请求）/**G3** `constants` 反向依赖 `api`/**G8** `eslint.config.js:114` 白名单路径在拆分后失效

## 注意事项

- 任务描述与实测有 3 处出入，文档内已以实测为准：`api/index.js` 88 导出（非 60+）、`router/index.js` 374 行（非 361）、移动端 `components` 25 个（非 26）
- 本轮**未修改** `hrm-admin` / `hrm-server`；两次构建的 `dist/` 为副产物（已被 `.gitignore` 忽略）
- 本机无真机 / 内置浏览器固定 810×658 → 触控热区、安全区、横屏 640、键盘弹起等项**只能收敛到「待真机复核」**，不得声称已验证

***

# 会话状态 — 移动端老板端模块化（单工程内按域拆模块）

> 最后更新: 2026-09-22
> 状态：**A0–A6 + A9/A10 + N-02 全部落地并通过全量门禁；老板端模块交付完成**
> 分支：`feature/前端演示项目拆分与精细化`（多会话共用的拆分主分支；**已实测核对**，本任务与之对齐，无需切换）
> 方案与规范：`hrm-dev/docs/demo-boss-module-plan.md`（结构与依赖边界）、`hrm-dev/docs/demo-boss-ui-spec.md`（UI/UX 与组件规范）

## 交付物

| 交付物 | 路径 |
| ---- | ---- |
| 模块拆分方案（含主智能体裁决 §10） | `hrm-dev/docs/demo-boss-module-plan.md` |
| 老板端 UI/UX 精细化规范（含主智能体裁决 §12） | `hrm-dev/docs/demo-boss-ui-spec.md` |
| 老板端模块 | `hrm-dev/hrm-demo/src/mobile/modules/boss/`（`router.js` + `views/` 21 页 + `components/`） |

## 进度

- [x] **A0** 依赖边界 lint（规则 4–6 + `VIEW_FILES` 增补 `'src/mobile/modules/**'`）+ 三条 `--stdin` 负向探针各报 1 error
- [x] **A1–A6** 21 个页面迁入 `modules/boss/views/`；`src/mobile/views/boss/` 已删除；`bossRoutes` 23 条；`index.spec.js:48` mock 路径已同步
- [x] **A9** UI 规范路径回写（`components/boss/` → `modules/boss/components/`）
- [x] **A10（部分）** `BossRankBar` / `BossMetricDelta` / `BossScopeNote` / `BossInlineEmpty` / `bossConfirm` 落地并替换手写实现
- [x] **D-1** 铜牌徽标对比度 **3.556:1 → 7.090:1**；名次配色单点收口到 `BossRankBar`（取 `--rank-1/2/3-bg`）
- [x] **D-2** `alerts.vue` 三处可点 `<div>` 补 `role="button"` + `tabindex="0"` + 键盘触发
- [x] **D-3 / G-01** `StationPicker` 补 `loading`/`error`/`emptyText` + `retry`（默认行为不变，员工端零回归）
- [x] **G-02** `ActionBar` 按钮级 loading；**G-03** `MonthPicker` 补 `disabled`
- [x] **N-02 `BossShareBar`（占比堆叠条）** —— 用户裁决：**用 100% 堆叠条 + 保留 `alerts` 四态计数按钮的下钻能力**（**否决环图方向**）。已接入 `alerts.vue`（采集四态）与 `attendance.vue`（考勤构成），均为**纯新增**、零新增接口请求；百分比采**最大余数法**取整，保证各段合计恒为 100%（不会出现「四段加起来 101%」）
- [x] **A11（Review 追加）横向溢出缺陷修复** —— `LineChart.vue` 的读屏等价表 `class="visually-hidden"` 直接加在 `<table>` 上，被表格 min-content 击败（**实测渲染宽 444px**），375×812 下 `boss/home` 横向溢出 **96px → 0px**；修法为外包一层块级 `.visually-hidden` 容器
- [x] **G-04** `NoticeList` 行级 pending（写入期间行内忙碌指示 + `aria-busy` + 防重复触发与重复跳转；失败给可见提示，**不静默**）
- [x] **G-05** `MeSection` 账号信息三态（`stores/auth.js` 暴露 `userLoaded`/`userLoading`/`userError`，组件**零新增接口请求**，仅做渲染分流）
- [x] **A10（收口）** 老板端组件库共 **6 项**：`BossRankBar` / `BossMetricDelta` / `BossScopeNote` / `BossInlineEmpty` / `BossShareBar` / `bossConfirm`（函数式），全部零十六进制色值
- [ ] **A7** 负向验证脚本化（未采纳，仍为手工探针）

## 目标结构（供其他域负责人对齐）

`src/mobile/modules/<domain>/` = `router.js`（导出 `<domain>Routes` 数组）+ `views/` + `components/`（组件统一 `<Domain>*` 前缀）。

- 内核 `src/mobile/{components,composables,constants,layout,stores,styles,utils,api}` 与 `src/shared/**` 只增不改语义；
- 跨层引用一律走既有 `@` 别名（不新增 `@boss`/`@mobile`）；
- 每模块只导出路由定义，`src/mobile/router/index.js` 单点聚合；**跨域复用路由只允许写在聚合点**（`/boss/kpi/:employeeId` 即此类，指向 `views/staff/kpi.vue`，登记 `TODO(扩展)`）；
- 依赖方向 `modules/* → 内核 → shared`；禁止 `modules/A ⇄ modules/B`，禁止内核 `→ modules/*`。

## 门禁实测（2026-09-22）

| 项 | 结果 |
| ---- | ---- |
| `npm run lint` | **我的范围 0 error / 10 warn**（`src/mobile/modules/boss` + `LineChart.vue` 单独跑）。**全仓 5 error 属他人**：全部落在 `e2e/evidence/deployed-check.mjs`（`no-undef: process`）——该文件位于 gitignore 的产物目录，为并行负责人在本会话期间新增的临时脚本，**未擅自动** |
| `npm run lint:style` | 0 error |
| `npm run build` / `npm run build:prod` | EXIT=0（prod 产物 mock 命中 0 处） |
| `npm run verify:mock` | **879 / 879**（计数因并行改动 +1，非本模块引入） |
| `npm run verify:mobile` | 48 / 48 |
| `npm test` | **300 / 300**（37 files）；先前员工端 `staff/components/ShiftCard.spec.js` 的失败已由其负责人修复，本模块未新增失败 |
| `npm run e2e` | **37 passed**（4.8m；此前 PC 侧 `A2-1`/`A5-2` 两项失败已随并行改造自行恢复） |
| 横向溢出实测（375×812，独立脚本取证后已删除脚本） | `boss/home` **96px → 0px**；`boss/trend` / `boss/alerts` / `boss/attendance` 均 **0px**；包装容器实测宽 **1px**（444px 表格被正确裁剪） |
| `npm run verify:tokens` | 通过 |
| 结构断言 | `views/boss` 不存在；`modules/boss/views` 21 个 `.vue`；模块内深层相对路径 **0 条**（改写后别名数 110 与迁移前 109+1 守恒） |
| URL 冻结核对 | `bossRoutes` 23 条全部 `/boss/*`；`path`/`name`/`meta` 与迁移前**逐字一致**，仅 `component` 路径变更 |
| 色值自查 | `modules/boss/components/**` 命中十六进制色值 **0**；`--c-orange-600` 在老板端命中 **0** |

## 环境阻塞（不得当作已完成）

1. **`npm run format:check` 全仓红灯（290 文件）**：全仓文件为 CRLF，而 `.prettierrc` 为 `endOfLine: "lf"`；未参与本任务的 `src/shared/constants/dict.js`（实测 418 处 CRLF）同样报错 → **环境级预存在问题**。本轮**未**做全量 `prettier --write`（会覆盖并行负责人的改动），仅保证本次改动文件不新增格式违规。需专项裁决是否补 `.gitattributes` 或调整 `endOfLine`。
2. **分支（已实测核对；本节此前记录有误，已更正）**：当前分支为 `feature/前端演示项目拆分与精细化` —— 与多会话拍板的共用分支一致（见本文件后文「并发冲突」节：`feature/PC端拆分与精细化` 已作废，PC / 员工端 / 老板端拆分统一汇入该分支）。**本任务无需切分支**。⚠️ 原写「工作区当前为 `feature/二期采集端`」系沿用本文件旧记录、未经 `git` 核实即转述，**已更正**；`feature/二期采集端` 是另一条并行工作流（作业隔离在 git worktree `C:\Users\16626\AppData\Local\Temp\yz-wt`），与本工作区无关。
   - **该共用分支尚无上游（未推送）**：`git rev-parse @{u}` 报 `no upstream configured`；`git branch -r` 中无同名远端分支。
   - **工作区混入非本工作流的未提交改动**（提交时必须按范围分离，禁止 `git add -A`）：`hrm-dev/collector/scripts/env-setup.ps1`、`hrm-dev/poc/wecom/POC-0{1,2,5}*.ps1`（二期采集端资产）、`hrm-demo/src/mobile/utils/workorder.js` 与 `hrm-demo/src/mobile/views/staff/home.vue`（员工端并行改动）。
3. **工作区并发**：`src/pc/**`（PC 模块）与员工端模块正由其他负责人并行改造，同一工作区存在未提交改动；`SESSION-STATE.md` 亦被多方写入，本节点为**追加**而非重写。
4. 本机无 Android SDK / 真机，且 TRAE Chrome 扩展不可用（os error 10061）→ **375px 真机档、≥1280px 宽屏、iOS Safari、企业微信内置浏览器、壳内 `--status-bar-height` 实测值均为「未验证」**，不得在交付中改述为已验证。
5. 内联 `--stdin --stdin-filename` 探针在本机 ESLint 9.39.5 上**行为正常**（已实测），无需回退临时文件方案。

***

# 会话状态 — PC 端拆分与精细化 + 三端 Demo Docker 测试环境部署

> 最后更新: 2026-09-22
> 分支：`feature/前端演示项目拆分与精细化`（多会话共用；原 `feature/员工端拆分与精细化` 按用户拍板合并为该共分支）
> 状态：**PC 精细化 B1–B5 完成并提交；Docker 测试环境部署完成并通过端到端验收**

## 进度

| 批次 | 内容 | commit |
| --- | --- | --- |
| 规范 | `hrm-dev/docs/demo-pc-refactor.md`（目录约定 / 组件契约 / 状态三层边界 / 竞态守卫 / 行为等价红线） | `2dc680d` |
| B1 | 工单 1057→129 行、排班 651→135 行 | `2dc680d` |
| B2 | 看板 823→121、同步 720→132、财务 671→128 行 | `69aabaa` |
| B3 | 状态三层梳理 + `src/pc/stores/org.js`（驿站 11 页、部门 3 页重复取数收敛） | `fd695d8` |
| B4 | Element Plus 按需引入：首屏 gzip **465218→151354（-67.5%）** | `adfcd9d` |
| B5 | 响应式：15 路由 × 7 宽度 = 105 格实测，修 2 类真缺陷 | `06e587d` |
| B6 | 部署产物与手册（Dockerfile / nginx / compose / 入口脚本 / C 档手册） | `5134821` |
| 待办 | 补测 PC `utils` / `config` / `router`；UI/UX 走查 | — |

## 门禁（实测）

`verify:mock` **879/879** · `test` **300 用例 / 37 文件** · `eslint src/pc` **0 error** · `stylelint src/pc` **0 problem** · `build` 与 `build:prod` **EXIT=0** · **`e2e` 37 passed / 0 failed**

## 部署（已完成，端到端验收 22/22）

- 服务器 `root@156.225.23.154`，Docker 29.7.2；本服务 `hrm-demo-static` **仅绑 `127.0.0.1:8090`**，经现网 `courier-nginx` 反代到 `kongzhen1.com` 根路径
- 现网**仅改 2 处 location**（`= /` 改为反代 + 新增 catch-all）；`/admin/`、`/api/`、`/photos/`、`/apk/`、`/download`、`/health` 逐条未动
- 备份：`/data/backup/nginx.conf.20260922-165202`；回滚与验证清单见 `hrm-dev/docs/demo-docker-deploy.md`
- 验收含：15 个 PC 路由无白屏、深链 `/dashboard` 正确回退 pc.html、刷新保登录、移动端入口、缺失资源 404、未鉴权全站 401、公网直连 8090 超时
- 凭据：Basic Auth 用户 `16626369983`，口令**仅存**本地 `.secrets/hrm-demo-basic-auth.txt`（已 gitignore）与服务器 `/data/www/hrm-demo/.env`（600）

## 关键决策

1. **多会话共用一个分支**（用户拍板）：`feature/PC端拆分与精细化` 作废，统一到 `feature/前端演示项目拆分与精细化`
2. **状态边界不凑指标**：三域拆分后按「≥2 路由页消费」判据无跨页共享状态，故未建 store；驿站/部门因 11 页与 3 页重复取数才建 `org.js`
3. **Element Plus 按需引入保留图标全局注册**：`@admin` 一期页面依赖全局图标名，移除必白屏
4. **静态站无法隐藏口令**：站内账号会打进公开 bundle，故访问控制放 Basic Auth 网关层；测试手机号**仅作网关账号**，站内仍用演示账号
5. **单文件 bind mount 绑定的是 inode**：`mv` 换 inode 后容器读不到新配置，必须重启容器让 Docker 重新解析路径

## 实测踩坑（登记，避免复发）

1. nginx `location ~*` 正则含 `{8,}` **必须加引号**，否则报 `unknown directive`
2. `.htpasswd` 设 600 → worker（nginx 用户）读不到 → 症状是「无凭据 401、带凭据反而 500」，需 `root:nginx` + 640
3. `{SHA}` 格式哈希可安全放 `.env`（**不含 `$`**，规避 Compose 插值吃掉 `$apr1$…`）
4. 部署前必须对 nginx.conf 先 `nginx -t` 干跑再落盘，否则现网容器进入重启循环
5. 并行会话会停掉 5188 dev server → e2e 大面积假失败；判定归属前先 curl 探活（本轮 28 失败即由此证伪）

## 未完成 / 遗留

1. **`/admin/` 现网返回 500**：`courier-nginx` 镜像内 `/usr/share/nginx/html/` 只有 nginx 自带文件，**无 `admin/` 目录** → 属改动前既有的部署缺口，非本次引入
2. `/api/` 403、`/photos/` 403、`/download` 404 均为未改动 location 的既有行为
3. 375px 及以下 PC 布局外溢**刻意未修**（规范 §8：<768px 由移动端入口承接）
4. `npm run format:check` 全仓红灯（既有 CRLF 与 `.prettierrc` `endOfLine: lf` 冲突，需专项裁决）
5. 测试手机号仅在网关层生效；如需**站内**以该手机号登录并带管理权限，须改 `src/demo/accounts.js` 与 Mock 权限映射（口令将公开于 bundle，**不建议**）
6. 文档漂移待修：项目规则写「生产 = 宝塔 + systemd `hrm-server.jar`、库名 `kdyzgl`」，实测为 Docker 栈 `courier-server` + 库名 `courier_station` + 另一仓库 `kdyzzhxt`

***

# 会话状态 — 网页端改名「快递驿站智慧管理系统」+ 新增系统设置页

> 最后更新: 2026-09-23
> 分支：`feature/前端演示项目拆分与精细化`
> 状态：**改名完成并已同步线上；跨浏览器 × 多尺寸验证通过**

## 进度

| 项 | 结果 | commit |
| --- | --- | --- |
| 改名（仅网页端） | 名称收敛到 `src/pc/constants/brand.js` 单一真源；落点＝浏览器标题栏（`pc.html` + `router.afterEach`）、侧栏 logo、登录界面标题 | `3d85cd0` |
| 侧栏 10 字适配 | 补 `flex-shrink: 0`（不补会被 flex 压缩 + `.app-aside{overflow:hidden}` 裁掉末字） | `3d85cd0` |
| 「系统设置」页 | `/system/settings`，仅 ADMIN；四分区；唯一异步块承载四态；版本号经 `vite define` 注入 | `3d85cd0` |
| 线上同步 | 重建 dist → 上传 → `docker compose up -d --build`；线上 `pc.html` title 已为新名 | — |

## 门禁与验证（实测）

`verify:mock` **879/879** · `test` **322 用例 / 39 文件** · `lint` **0 error** · `stylelint src/pc` **0 problem** · `build` **EXIT=0** · `e2e` **36 passed + 1 failed**

- **e2e 唯一失败 = `B2-2` 负载抖动，非改名回归**：改名只改一个字符串、不改变登录页模块图；该用例 240s 预算在机器被并行任务拖慢（本轮 e2e 9.7m vs 基线 5.1m）时超时；**单独复跑 `07-network.spec.js` → 3 passed**。该用例此前已被登记为「待复验」（见 `0b12b4d`）。
- **跨浏览器 × 多尺寸名称显示**：Chromium / Firefox / WebKit 三内核 ×（1280 / 1440 / 1920 / 1024 折叠 / 375），**本地与线上域名各跑一轮，`problems = []`、`EXIT=0`**。判据全为脚本实测数值：侧栏 `scrollWidth 150 === clientWidth 150`（不截断）、`flexShrink === '0'`、`aside 210`（折叠 64 + `display:none`）、`document.title` 全等、设置页展示系统名称、无横向滚动、无页面 JS 错误。
- 脚本：`e2e/evidence/brand-crossbrowser.js`（gitignored，支持 `BASE_URL` + Basic Auth 环境变量）

## 关键决策

1. **改名范围＝仅网页端**（用户拍板）：移动端与端选择页仍为「快递驿站智汇系统」，属**已知且用户接受**的一系统两名
2. **破例改一期源码**（用户明确授权）：`hrm-admin/src/views/login/index.vue:8` 标题文案，**仅 1 行**。影响——污染一期资产，删除 `hrm-demo` 不再能完全回滚该改名；且 `hrm-admin` 自身入口其余位置仍是旧名，一期独立运行时会出现「登录页新名 / 其余页旧名」
3. **未新增 token、未新增组件**：系统设置页复用 `PageHeader / StateBlock / StatusTag / MetricCard`
4. **权限收口走既有 `EXTRA_MENU_KEYS.ADMIN`**（与 `logs` 同口径），未改 `src/shared/constants/role.js`（本轮该目录禁改），已标 `TODO(扩展)` 待 shared 解冻后并入真源
5. 设计规范把包裹总数契约字段写作 `total`，实测契约是 **`parcelTotal`** → 按真实契约取值，未按文档字面猜

## ⚠️ 线上部署与仓库产物已不可信（他方改动，非本人）

**另一会话/他人在 Sep 22 23:50–23:55 于服务器上直接改动了本服务，且未提交仓库：**

| 服务端文件 | 变化 | mtime |
| --- | --- | --- |
| `nginx.conf` | **移除全部 `auth_basic`**，并写入理由「认证已收敛到站内登录页，避免两层验身」 | Sep 22 23:50 |
| `docker-compose.yml` | 移除 `env_file` 与 `BASIC_AUTH_*` 环境变量 | Sep 22 23:50 |
| `.env` | 只剩 `DEMO_TAG` / `HOST_PORT`（认证两行被删） | Sep 22 23:55 |
| `docker-entrypoint.d/10-basic-auth.sh` | **已删除** | — |

后果：
1. **演示站自此对公网完全开放**（无网关鉴权）
2. **仓库侧也已同步为同一无鉴权形态（未提交）**：`nginx.conf` / `docker-compose.yml` / `Dockerfile` / `deploy-demo.sh` 均已改、`10-basic-auth.sh` 已删 → 服务端与仓库**当前一致**，按仓库重新部署**不会**重新引入网关
3. **测试手机号 `16626369983` 现在在任何地方都不存在**：网关已删除，且全仓 `grep` 无该账号（`src/demo/accounts.js` 未落）→ 原始需求「预先配置测试用账号」当前**未被满足**
4. 此事属「直接在服务器改配置 + 仓库改动未提交」，与规则「服务器产物只来源于仓库」冲突，需登记为流程问题
5. 已在本会话 `939ded2` 中把该状态登记进 `hrm-dev/docs/demo-docker-deploy.md`（含首版误判「仓库仍保留网关」的更正）

## 未完成 / 遗留

1. **`hrm-admin` 自身入口仍是旧名**：`layout/index.vue:7`（侧栏）、`router/index.js:105-106`（标题）未改（用户仅授权登录页那一行）。其中 layout 会经 `@admin/utils/request.js` 的 401 动态 import 被打进 demo 包，但演示里**永不渲染**（三内核侧栏实测为新名可证）。是否一并改需拍板。
2. `B2-2` 在本机并行负载下会超 240s 预算 → 属环境敏感用例，建议单独复跑或放宽预算（已有他人登记）
3. 系统设置页 S3 的 `empty` / `error` 两态**未做运行时触发**：Mock 在 axios adapter 层拦截、演示态恒返回 `parcelTotal=200000`，无自然失败路径；当前仅静态审查 + 纯逻辑单测覆盖
4. 375 / 820 档「设置页」与折叠态以外布局未逐格实测（PC 不承诺区）
5. `/admin/` 现网 500、`/api/` 403、`/photos/` 403、`/download` 404 仍为改动前既有行为（未见处置）

***

# 会话状态 — 智能体团队编制与调度规则（规范层，非代码）

> 最后更新: 2026-09-23
> 分支：`feature/前端演示项目拆分与精细化`（本轮全部改动**未提交**）
> 状态：**设计 + 落盘完成；项目规则升 v2.2；9 角色编制；调度规则常驻生效**
> 参照基线：MetaGPT（arXiv:2308.00352，ICLR 2024 Oral，`Code = SOP(Team)` + 带类型产物交接）

## 交付物（全部未提交）

| 交付物 | 路径 | 规模 |
| --- | --- | --- |
| **智能体调度规则（常驻生效）** | `.trae/rules/智能体调度规则.md` | 新建 265 行 |
| 项目规则 **v2.2** | `.trae/rules/项目规则1.md` | 465→472 行 |
| 第 9 角色 | `.trae/agents/express-station-security-engineer/SKILL.md` | 新建 104 行 |
| 角色边界修订 | `.trae/agents/{test-engineer,ui-ux-designer,ops-engineer}/SKILL.md` | 3 个文件 |
| 角色创建版 | `智能体配置.md` | 173→192 行（9 角色） |
| 详设（论证与契约卡） | `hrm-dev/docs/agent-team-design.md` | v1.0→v1.1，540 行 |
| 同源镜像 | `.github/CONTRIBUTING.md` | 187→474 行（= 项目规则 v2.2 全文镜像） |
| 全局准则对齐 | `全局规则.md` | 96→102 行（§6 改四档、§4 凭据托管） |
| 工程放行 | `.gitignore` | `.trae/{rules,agents,skills}` 解除忽略 |

## 关键决策（用户拍板）

1. **新增第 9 角色「网络安全工程师」**：拆分「安全技术评估」与「安全审批」——评估方＝网络安全工程师，决策与授权方＝主智能体。
2. **主智能体不得无脑审批**：C 档授权必须以独立安全技术评估结论为依据；无结论不得授权；高风险升级用户裁定。
3. **「视觉走查」正式划归 UI/UX 设计师**；测试工程师收敛为功能/接口/E2E/门禁实跑 + 响应式与可访问性可执行验收。
4. **调度逻辑独立成文常驻加载**（不并入项目规则，避免每轮 token 膨胀）；详设论证留 `hrm-dev/docs/agent-team-design.md`。
5. **「安全评估」两类口径消歧**：操作安全评估（影响范围/可逆/回滚）→ 主智能体 §10.3；技术安全评估（漏洞/攻击面/依赖/合规）→ 网络安全工程师；运维不承担任一类。
6. `.trae/` 放行 rules/agents/skills 入仓库；**技能领域技能无安全类**，网络安全工程师暂挂 `code-review`（安全维度），已标 `TODO(扩展)`。

## 实测澄清（子智能体纠错，避免误判）

1. 8 个角色 SKILL.md 的父规则引用**原本已是 v2.1 编号**（非任务书假设的"全部失效"）；全仓仅运维文件存在失效引用「项目规则 12.3 节」，已修正。
2. `CONTRIBUTING.md` 虽声明与项目规则同源，**正文实际停留在旧版**（PG 双库 / Vuex / 无 §10–§14）→ 本轮重写为 v2.2 镜像。
3. 项目规则 §10.5 曾自称「与 `全局规则.md` §6 已对齐」，**该自述不成立**（全局 §6 仍写「高危操作必须人工确认」）→ 本轮已双向对齐。

## 未完成 / 遗留

1. **全部未提交**：`.trae/` 现为未跟踪（`?? .trae/`），`智能体配置.md`、`CONTRIBUTING.md` 为已修改。**提交前须先 `git branch --show-current` 确认分支并核对范围**（工作区另有并发会话改动）。
2. **`.trae/skills/` 是否随仓库分发**：已放行，但 13 个技能中部分可能来自插件市场安装，**首次入库需人工确认无本机私有内容**。
3. **协作方仍需补齐**：新增角色与调度规则会影响历史会话的既有习惯；`.trae/skills/{engineering-discipline,devops-pipeline}` 中「安全评估」仍用裸词，建议后续统一为「操作安全评估」（`TODO(扩展)`）。
4. **文档漂移（历史遗留，本轮未处理）**：项目规则 §5 写「宝塔 + systemd `hrm-server.jar`、库名 `kdyzgl`」，实测生产为 Docker 栈 `courier-server` + 库名 `courier_station`（见本文件 PC 端小节遗留 6）。
5. **算法/UI 两处落盘路径仍 `TODO(扩展)`**：Design Tokens 生产端路径、算法基准数据独立目录（详见 `agent-team-design.md` §10）。
