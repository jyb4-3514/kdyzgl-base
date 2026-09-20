# 快递驿站智汇系统 · 三端 Demo 体验改进与新增功能设计规范

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-19 |
| 作者 | UI/UX 设计师 |
| 适用范围 | `hrm-dev/hrm-demo`（网页端 `pc.html` / 老板端 + 员工端 `mobile.html` / 端选择页 `index.html`） |
| 交付对象 | 主智能体（评审）→ 前端工程师（照 B 章逐项落地） |
| 依据文档 | [demo-design.md](demo-design.md)、[demo-ui-redesign.md](demo-ui-redesign.md)、[plan.md](plan.md)、[api.md](api.md) |
| 本轮产出 | 仅本设计文档，**不改任何源码** |

---

## 0. 取证方式、能力边界与不确定项

### 0.1 取证方式

本文所有结论来自**静态阅读**三端源码 + Mock 契约，逐条标注 `文件:行号`。未使用浏览器实测（TRAE Chrome 扩展在本机不可用，见 `SESSION-STATE.md` 未完成项 1），因此：

- **结构性结论**（信息架构、组件状态缺失、文案位置、契约能力边界、色值/尺寸 Token 取值）证据充分，标注了精确行号；
- **运行时结论**（Element Plus / Vant 组件内部实际像素、滚动容器行为）标注为「待走查」，由测试工程师在实现后复核。

### 0.2 Mock 契约的能力边界（决定哪些功能「只缺 UI」、哪些「还缺数据」）

这是本轮最关键的前置事实。经逐文件核对，**需求 1–6 的 Mock 契约已经就绪，但 PC 端 API 封装与界面一个都没有**；需求 7–10 连契约都没有。

| 需求 | Mock 契约 | PC 端 API 封装 | PC 端界面 | 移动端界面 |
| ---- | ---- | ---- | ---- | ---- |
| 1 同步任务模块化 + 驿站采集状态 | ✅ [syncConfig.js:142-146](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L142-L146) | ❌ 无 | ❌ 无 | ❌ 无 |
| 2 按驿站设置排班（一键铺排） | ✅ `/schedules/batch-by-station` [attendance.js:479](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L479) | ❌ 无 | ⚠️ 仅有逐格编辑 [schedule/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue) | ⚠️ 仅有逐人编辑 [boss/schedule.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue) |
| 3 新建工单 + 企微自动派单预留 | ✅ [workOrder.js:397-400](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L397-L400) | ❌ 无 | ❌ 无 | ✅ 员工端已有 [workorderCreate.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorderCreate.vue)、[api/index.js:39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/api/index.js#L39) |
| 4 发布通知 | ✅ [notification.js:121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L121) | ❌ 无 | ❌ 无 | ❌ 无 |
| 5 考勤导出 | ✅ `/attendance/export`（返回 Blob）[attendance.js:321-357](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L321-L357) | ❌ 无 | ❌ 无 | ❌ 无（移动端不做导出） |
| 6 文案改「超时未处理」 | ✅ 字段 `overdueUnhandled` 已就绪 [workOrder.js:23-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L23-L24) | — | ⚠️ 6 处旧文案 | ⚠️ 8 处旧文案 |
| 7 KPI 考核 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 |
| 8 人事管理 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 |
| 9 财务管理 / 工资单 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 |
| 10 入离职流程 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 |

**推论（对开发的硬约束）**：

1. 需求 1–6 的落地路径是「补 API 封装 → 补界面」，**不许前端自行发明字段**——契约字段名以 Mock handler 为准（如 `collectState`、`collectStateLabel`、`source`、`isPublished`、`publishScope`、`overdueUnhandled`）。
2. 需求 7–10 的落地路径是「先由后端/主智能体定契约 → 前端再按本规范实现」。本规范已给出**建议的数据结构与错误码段位**，标注为提案，供契约定稿时采用。
3. **共享层 `src/shared/**` 已冻结**（demo-ui-redesign.md 0.1）：新增菜单键必须走 [menu.js:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L19) 的 `EXTRA_MENU_KEYS` 既有模式，不得改 [role.js:27-31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/role.js#L27-L31)。若本规范需要新错误码/新字典，须由主智能体解冻共享层后统一变更。

### 0.3 不确定项与验证方式

| # | 不确定项 | 本文处理 | 验证方式 |
| ---- | ---- | ---- | ---- |
| U1 | PC 登录页是否有「演示账号一键填充」 | demo-design.md 5.1 第 1 行称「Demo 新增」，但实测 [login/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/login/index.vue) 内 grep「演示/一键」**零命中**，且该页属冻结清单 | 读取 `hrm-dev/hrm-admin/src/views/login/index.vue` 全文确认；结论以代码为准，见 A2-1 |
| U2 | `GET /sync-tasks` 是否支持时间范围查询 | 未读 `syncTask.js` 的 list 参数白名单，本文只提出「先核对再加控件」 | 读 `src/shared/mock/routes/syncTask.js` 的 `list()` 参数分支 |
| U3 | `GET /attendance/records` 是否支持 `checkType` | [attendance/index.vue:84-88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L84-L88) 页面自述「契约暂未支持」，实测 [attendance.js:292-307](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L292-L307) 确未解构 `checkType` → **已确认不支持** | 已确认 |
| U4 | `GET /attendance/summary` / `/attendance/rule` 的 `stationId` 是否可为空 | `scopedStationId(user, params.stationId)` 对 ADMIN 传空的行为未展开阅读 → 影响 A9-3「全部驿站」选项是否可行 | 读 `attendance.js` 的 `scopedStationId` 实现 |
| U5 | `POST /schedules/batch-by-station` 返回结构 | 只读到参数校验段 [attendance.js:262-286](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L262-L286)，`saveSchedulesByStation` 的返回体（saved/removed/skipped 字段名）未展开 | 读 `attendance.js` 的 `saveSchedulesByStation` 实现，据此写 toast 文案 |
| U6 | `/work-orders/dispatch-rules` 列表对非 ADMIN 是否放行 | 路由定义 [workOrder.js:398](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L398) 未加 `roles`，而 `updateDispatchRule` 加了 `roles:['ADMIN']` → 读权限不一致 | 读路由表确认，并据此决定规则表是否只对 ADMIN 渲染 |

---

# A. 现状走查与改进建议

口径：**P0 = 不修则本次新功能无法落地或产生错误预期；P1 = 明显损耗使用效率；P2 = 一致性/细节打磨**。每条给出「现存问题 → 改进建议 → 落地要点」。

## A1 端选择入口页（`index.html` + `src/portal/`）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A1-1 | **P0** | 入口页整个样式表用的是旧品牌色 `#1677ff`（[index.html:44,45,48,69,75](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/index.html#L44-L75)），与 Token 体系 `--c-blue-500: #1890FF`（[pc/tokens.scss:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L18)）不一致。入口页是三端第一屏，品牌锚点在这里就断了 | 把入口页纳入 Token 体系：新建 `src/portal/portal.scss`，`:root` 内复制 L1 蓝族 + 中性灰阶（入口页不引 Vant/Element，**不引 tokens.scss 全量**，只引需要的 12 个变量），把 `#1677ff` 全部替换为 `var(--color-primary)`（`#0958D9`，白字 6.16:1），`#f0f2f5`→`var(--surface-page)`，`#1f2329`→`var(--text-1)`，`#8c8c8c`→`var(--text-3)` | `index.html:7-84` 的 `<style>` 整块删除，改为 `<link rel="stylesheet" href="/src/portal/portal.scss">`；`src/portal/portal.scss` 新建 |
| A1-2 | **P0** | 入口页三张卡片的说明文字与「演示顺序」清单已与本次 10 项新功能脱节：演示顺序第 4 条仍写「切『超 SLA』Tab」（[index.html:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/index.html#L110)），角色说明也没提新增的人事/财务/KPI | 同步更新卡片 role 文案与演示顺序清单；演示顺序建议扩到 12 条（新增：采集配置、新建工单/模拟派单、发布通知、考勤导出、排班一键铺排、工资单闭环） | `index.html:105-117`（清单）、`src/portal/main.js:21-43`（cards 数组） |
| A1-3 | P1 | 「重置演示数据」只清覆盖层与重建索引（[main.js:92-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/main.js#L92-L103)），但本次新增的采集配置/派单规则/工单来源都写进了 `db.syncConfigs`、`db.dispatchRules`、`db.workOrders`（内存态，刷新即还原）——**用户看不出「重置」到底重置了什么**，且与 KPI/工资单这类持久化假设冲突 | 在「重置」按钮旁补一行状态说明：`内存种子（刷新自动还原）` + `本地覆盖层（需手动重置）` 两段，明确告知边界；重置成功提示里列出被重置的三类数据 | `src/portal/main.js:97` 的提示文案；`index.html:97-101` 的操作区 |
| A1-4 | P1 | 剧本自检项写死为 4 项业务断言（[scenario.js:60-75](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/scenario.js)），本次新增功能的种子（采集状态四态齐备、企微自动派发工单存在、手工公告 3 批）没有校验项。演示现场无法自查新功能是否有数据可演 | 自检项扩到 7 项：① 失败同步批次 ② 超时未处理工单 ③ 超 48h 未取件 ④ **采集状态四态齐备（≥1 站 ABNORMAL、≥1 站 UNCONFIGURED）** ⑤ **source=AUTO_WECHAT 工单 ≥1** ⑥ **isPublished=true 通知 ≥1** ⑦ 演示运单号归属 | `src/portal/scenario.js` 的 `checkScenario()`（第 9-20 行注释所示判定口径） |
| A1-5 | P2 | 卡片 hover 有动效（[index.html:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/index.html#L44)）但**没有 `:focus-visible`**；键盘 Tab 到卡片看不到落点。移动端已在 [mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38) 建立了「2px 主色焦点环」的统一规则，入口页缺失 | 补与移动端同款焦点环（`outline: 2px solid var(--color-primary-icon); outline-offset: 2px`）；同时把状态 emoji（`✅/❌/🎯`，[main.js:70,77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/main.js#L70-L79)）换成文字标签「通过/不通过」，避免 emoji 字形在 Windows 上不统一 | `index.html` 新增 `.card:focus-visible`；`portal.scss` 承载；`main.js:70` 的 `<span>${item.ok ? '✅' : '❌'}</span>` 改为状态标签 |

## A2 网页端登录页

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A2-1 | P1 | **文档与实现不符**：demo-design.md 5.1 第 1 行称 PC 登录有「演示账号一键填充」，但一期 [login/index.vue](file:///d:/Users/16626/Desktop/hrm-dev/hrm-admin/src/views/login/index.vue) 内无任何演示账号入口（grep「演示/一键」零命中），而该页在冻结清单内不可改 | 二选一，由主智能体决策：**(a)** 接受偏差——在 `demo-design.md` 5.1 该行标注「未实现（一期页面冻结）」，并把演示账号做成入口页可复制文本（[portal/main.js:16](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/main.js#L16) 已有，补一个「复制」按钮）；**(b)** 在 Demo 侧新增 `views/login/demo-banner.vue`，用**不侵入一期组件**的方式在 `<el-main>` 之外无解——**不推荐** | 首选 (a)：改文档 + `portal/main.js` 加复制按钮。禁止改动 `hrm-admin/**` |
| A2-2 | P2 | 登录页表单无「显示密码」开关、无大写锁定提示；现场演示手输 `demo1234` 时容易踩错 | 属一期冻结页，**本轮不动**。仅在 A2-1 的入口页文案里把密码做成可复制文本，降低手输频次 | `portal/main.js:17` 的 `<code>demo1234</code>` 加复制交互 |

## A3 网页端布局与侧边导航

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A3-1 | **P0** | 侧边栏是**平铺结构**：11 项平铺 + 仅 1 个「组织管理」分组（[menu.js:11,21-33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L11-L33)）。本次要新增「KPI 考核 / 人事管理 / 财务管理 / 入离职 / 采集配置」，平铺后将达 16+ 项，扫读成本翻倍 | 分组从 1 个扩到 4 个（**只改此文件，不动 shared**）：<br>· **组织人事**：员工管理 / KPI 考核 / 人事管理 / 入离职 / 部门管理 / 驿站管理<br>· **考勤薪酬**：考勤管理 / 排班管理 / 财务管理<br>· **包裹作业**：包裹管理 / 数据同步（批次流水+采集配置）/ 工单管理 / 通知中心<br>· **系统**：个人中心 | `pc/config/menu.js:11` 的 `MENU_GROUPS`、`:21-33` 的 `MENU_ITEMS`。**注意**：`buildMenus()` 靠「同 group 项首次出现时创建父节点」，重排 `MENU_ITEMS` 顺序即改变父节点位置（[menu.js:36-51](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L36-L51)），需按「父节点先于子节点」排列 |
| A3-2 | **P0** | 菜单可见性真源是 shared 层 `MENU_WHITELIST`（[role.js:27-31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/role.js#L27-L31)），新增的 `kpi / hr / finance / onboard / syncConfig` 键不在白名单里 → **新菜单一律不显示**。当前只靠 [menu.js:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L19) 的 `EXTRA_MENU_KEYS` 局部兜底 | 沿用既有兜底模式扩 `EXTRA_MENU_KEYS`：`ADMIN: ['attendance','schedule','kpi','hr','finance','onboard','syncConfig']`；`STATION_ADMIN: ['attendance','schedule','syncConfig']`（站长看采集配置但只读）。**须与路由 `meta.roles` 严格同口径**，否则出现「菜单可见但点进去被重定向」 | `pc/config/menu.js:19`；`pc/router/index.js:34-108` 各路由 `meta.roles` 同步 |
| A3-3 | P1 | 面包屑只支持两级：`meta.group` + `meta.title`（[layout/index.vue:131-137](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L131-L137)），且 `MENU_GROUPS` 只认 `org` 一个键。新增的三级页面（如「财务管理 → 工资单 → 详情」）无法表达 | 面包屑改为**显式声明**：路由 `meta.breadcrumb: ['财务管理', '工资单', '详情']`，有则直接渲染，无则回退现有两段逻辑。理由：分组名来自 `MENU_GROUPS` 常量，而详情页标题是动态的（含单号），靠常量拼不出来 | `pc/layout/index.vue:131-137`；`pc/router/index.js` 新页面 meta |
| A3-4 | P1 | 菜单项**无法承载待办角标**。本次新增大量「待老板处理」的队列（待审核工资单、待审批补卡、待审批入离职、待处理工单），老板必须逐个点开才知道有没有活 | `MENU_ITEMS` 增加可选字段 `badgeKey`（如 `'pendingPayroll'`），`layout/index.vue` 从新增的 `stores/counts.js`（Pinia）取数并渲染 `el-badge`；角标为 0 时不占位 | `pc/config/menu.js:21-33` 加 `badgeKey`；`pc/layout/index.vue:22-30` 渲染；新建 `pc/stores/counts.js` |
| A3-5 | P1 | `activeMenu` 直接用 `route.path`（[layout/index.vue:123](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L123)）。新增子路由（`/finance/payroll/:id`、`/employee/kpi/config`）时菜单高亮会**整个丢失**（路径不相等） | `activeMenu` 改为「取菜单 path 中与当前路径前缀匹配的最长项」；对详情页再用 `meta.activeMenu` 显式指定父路径（Element 官方支持 `el-menu` 的 `default-active` 传任意值，故只需计算正确） | `pc/layout/index.vue:123` 的 computed；`pc/router/index.js` 详情路由加 `meta.activeMenu` |
| A3-6 | P2 | 头部的折叠按钮是 32×32（[layout/index.vue:275-282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L275-L282)），低于项目自定的 44px 触控基线；但 PC 端以鼠标为主，WCAG 2.5.8 最小 24×24 已满足 | **保留 32×32**，并在本文件登记例外理由（桌面端鼠标精度高 + 头部空间受限）。若要严格达 44，仅需把 `.collapse-btn` 宽高改 `var(--sp-8)`（32）→ `44px`，代价是头部 60px 内留白变紧 | `pc/layout/index.vue:279-280`；登记于本文 A13 汇总表 |
| A3-7 | P2 | 路由进度条用 `key` 重放动画（[layout/index.vue:79-80,140-143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L79-L143)），**与真实加载无关**：接口慢时进度条早已走完，接口快时又显得多余。新增的「生成工资单」等重操作页若依赖它反馈，会误导 | 保留（作为路由切换的轻反馈），但明确规则：**任何 >1s 的操作必须用按钮级 `loading`**，禁止用进度条代替。写进前端实现约定 | 约定，无代码改动；示例见 [schedule/index.vue:17-19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L17-L19) 的按钮 loading 写法 |

## A4 首页看板（`pc/views/dashboard/index.vue`）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A4-1 | **P0** | 看板对本次新增的老板核心待办**零覆盖**：没有「待审核工资单」「采集异常驿站」「待审批入离职」入口。老板进来看到的还是包裹/工单，而新功能全是「等老板处理」的 | 次级指标条从 4 项扩到 **6 项**：包裹总量 / 今日取件率 / 同步成功率 / 超时未处理工单 / **采集异常驿站** / **待审核工资单**。后两项可点下钻（`/parcel/sync?tab=collect`、`/finance/payroll?status=pending`）| `dashboard/index.vue:284-325`（`inlineCards`）；`HERO_META` 归位；`loadAll()` [:490-495](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L490-L495) 增加两个 loader。**注意 6 项在 `:lg="6"` 会排成 4+2**，需同时把 [:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L34) 的 `:lg` 改为 `4`（一屏 3 项 × 2 行） |
| A4-2 | P1 | 首屏 4 个 Hero 卡在 `parcelLoading` 时**整块换 skeleton**（[dashboard/index.vue:13-26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L13-L26)），而次级 6 项是**逐项 loading**（同一份 `MetricCard` 组件）。同一页两种加载语义，会出现「上半骨架、下半真值」的错位观感 | 统一为逐项 `loading`：Hero 卡也用 `MetricCard` 的 `loading` 属性，删掉 `<el-skeleton>` 分支。理由：Hero 4 项同源（同一个 `/parcels/summary`），逐项 loading 会同时亮同时灭，视觉与整块骨架等价，但组件路径只剩一条 | 删 `dashboard/index.vue:26`；`MetricCard.vue` 的 `loading` 分支需先确认对 `variant="hero"` 与 `error` 共存的表现（待走查） |
| A4-3 | P1 | 「同步健康度」在本页由前端聚合 100 条批次算出来（[dashboard/index.vue:427-451](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L427-L451)），**且只看 `pageSize=100` 的样本**。本次新增「采集状态」后，同一件事会出现两个口径（前端样本聚合 vs `/sync/overview` 的权威计数） | 直接改用 `GET /sync/overview` 的 `counts`（[syncConfig.js:121-140](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L121-L140)），删掉前端聚合逻辑。「同步成功率」改为 `1 - abnormal/total` 的口径说明，文案显式标注口径来源 | `dashboard/index.vue:427-451`；新增 `pc/api/syncConfig.js` 的 `getSyncOverview()` |
| A4-4 | P2 | 需求 6 文案未同步：`label:'超 SLA 工单'`、`errorText:'超 SLA 工单加载失败'`、`{ label:'超 SLA' }`、`有 N 条工单已超 SLA`（[dashboard/index.vue:165,317,323,332](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L165-L332)） | 统一改为「超时未处理」 | 见 B6 全量清单 |
| A4-5 | P2 | `dash-caption` 用负 margin 抵消上一行 gutter（[dashboard/index.vue:510-515](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L510-L515)），与 demo-ui-redesign.md P13 记录的「样式对抗」同类 | 改为让口径行归属于上一行的容器（把 `<p class="dash-caption">` 移入第一个 `el-row` 之后、由该行统一 `margin-bottom`），删掉负 margin | `dashboard/index.vue:30,510-515` |
| A4-6 | P2 | 「组织规模」折叠块的箭头旋转用了 `transform: rotate(180deg)`（[dashboard/index.vue:770-777](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L770-L777)），而移动端同类折叠块用图标名切换（[boss/home.vue:210](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L210)）。两端折叠语义不同（旋转 vs 换图标） | 统一为「换图标 + 旋转过渡」二选一。建议 PC 保留旋转（PC 端箭头方向可读性更好），移动端也改为旋转（减少一次图标组件切换），并在 `mobile.scss` 登记 | `dashboard/index.vue:770-777` 保留；`boss/home.vue:210` 改为旋转 |

## A5 包裹管理（`pc/views/parcel/index.vue`）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A5-1 | P1 | 导出上限 500 条是**隐式约束**：页头常驻「最多导出前 500 条」（[parcel/index.vue:6](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L6)），但按钮 disabled 条件只有 `!total`（[:5](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L5)）。`total=3000` 时用户以为能全导，点完才知道只有 500 | 按钮文案随结果数变化：`total > 500` 时显示「导出前 500 条」，否则「导出 CSV」；上限说明从常驻文字改为按钮 `title`。导出成功提示已具备超额说明（[:374-376](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L374-L376)），保留 | `parcel/index.vue:5-7`；`:374-376` 文案保留。此规则同样约束 B5（考勤导出） |
| A5-2 | P1 | **只有包裹页做了筛选条件写回 URL**（[parcel/index.vue:241-262](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L241-L262)）；同步/工单/考勤三页刷新即丢筛选（`SESSION-STATE.md` 遗留项 9 已记录）。本次新增的 4 个列表页若不统一，会形成「一半页面能分享链接、一半不能」的分裂 | 抽出组合式函数 `pc/composables/useQuerySync.js`：入参为「字段白名单 + get/set 回调」，出参 `syncQuery()/restoreQuery()`。包裹页迁移过来，同步/工单/考勤/通知/工资单五页统一使用 | 新建 `pc/composables/useQuerySync.js`；`parcel/index.vue:241-262` 改为调用；`sync/index.vue`、`workOrder/index.vue`、`attendance/index.vue`、`notification/index.vue` 接入。**注意**：通知/工单页已有 `?orderId=` `?taskId=` `?parcelId=` 的业务参数，白名单必须排除它们，否则会污染筛选还原 |
| A5-3 | P2 | 详情抽屉固定 2 列 `el-descriptions`（[parcel/index.vue:121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L121)），窄视口（`min(--drawer-w,92vw)`，[:191](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L191)）下每列约 200px，长运单号会换行 | 列数随 `--drawer-w` 实际宽度响应：`column` 用 `computed` 按 `window.innerWidth < 1200 ? 1 : 2`。同样规则应用到工单/同步/考勤的 `el-descriptions` | `parcel/index.vue:121`；建议抽 `pc/composables/useDrawerColumns.js`，四处复用（工单 [workOrder/index.vue:134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L134)、同步 [sync/index.vue:122](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L122)） |
| A5-4 | P2 | 表格无「行骨架」，注释里自述首次加载是「表头 v-loading」（[parcel/index.vue:81](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L81) 的 TODO） | 本次不扩（20 万数据下加载 <11ms，表头骨架已足够），仅把该 TODO 保留不动 | 无 |

## A6 同步任务（`pc/views/sync/index.vue`）——需求 1 主战场

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A6-1 | **P0** | 页面只有「批次流水」一个视图（[sync/index.vue:57-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L57-L103)），完全没有需求 1 要的「按驿站区分采集状态」。契约 `GET /sync/overview`、`/sync/configs`、`PUT /sync/configs/:id` **已就绪但无人调用**（[syncConfig.js:142-146](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L142-L146)） | 页面改为**页内 Tab 双视图**：「批次流水」（现有内容）/「采集配置」（新增）。理由：不动路由与菜单权限键（`sync` 已在白名单），且两者本就是同一业务的两种视图。详见 B1 | `sync/index.vue` 顶部加 `el-tabs`；新建 `views/sync/components/CollectOverview.vue`、`CollectConfigTable.vue`、`CollectConfigDrawer.vue`；新建 `pc/api/syncConfig.js` |
| A6-2 | P1 | 同一行的「触发」与「重试」两个按钮**共用一个 `actingId`**（[sync/index.vue:85,95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L85-L95)）：点触发时重试按钮也会转圈（虽然同时 disabled，但 `loading` 是**两个按钮同时显示**） | `actingId` 拆为 `{ id, action }` 二元组，`loading` 判定加 action 条件；或直接拆成 `triggeringId` / `retryingId` 两个 ref | `sync/index.vue:186`（`actingId` 定义）、`:85,95`、`:277` |
| A6-3 | P1 | 「失败原因」列靠 `show-overflow-tooltip`（[sync/index.vue:74-76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L74-L76)），hover 延迟 + 无法复制；而失败原因恰恰是排查的第一信息 | 失败行支持**展开行**（`el-table` 的 `type="expand"`）：展开后显示完整 `errorMsg` + 失败明细 + 「查看日志」直达。仅对 `status===3` 的行渲染展开图标 | `sync/index.vue:57-103`；`row-key` 用 `id` |
| A6-4 | P2 | 筛选无时间范围（[sync/index.vue:200](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L200) 的 `query` 只有 stationId/status/keyword），240 条批次只能靠状态与批次号定位 | 先核对契约（U2）再决定：若 `/sync-tasks` list 支持 `startDate/endDate`，加日期区间控件；不支持则**不加**（避免出现又一次「控件无效」的 A9-2 同类问题） | `sync/index.vue:200`；验证方式见 U2 |
| A6-5 | P2 | 空态文案假设了「批次列表为空」，但采集配置视图的空态语义完全不同（8 个驿站都有配置行，永远不会空；真正的空态是「该驿站未配置」）。两个视图共用一个 StateBlock 会串味 | 采集配置视图**不使用空态**，改用「未配置」行内标签（`UNCONFIGURED` 已有字典，[syncConfig.js:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L18)）。只有接口失败才进 error 态 | `sync/index.vue:48-54`（仅保留给批次流水视图）；采集视图的 error 态独立 |

## A7 工单管理（`pc/views/workOrder/index.vue`）——需求 3、6 主战场

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A7-1 | **P0** | 无「新建工单」入口，`pc/api/workOrder.js` 也没有 `createWorkOrder`（全文件仅 5 个方法，[workOrder.js:1-33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/api/workOrder.js)）。契约 `POST /work-orders` **已就绪**（[workOrder.js:397](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L397)），移动端员工端也早已实现（[mobile/api/index.js:39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/api/index.js#L39)）——**PC 是唯一缺口** | 页头加主按钮「新建工单」→ 弹窗表单。字段与移动端对齐并补 PC 独有项（归属驿站、指派处理人）。详见 B3 | `workOrder/index.vue:3-7`（PageHeader actions）；`pc/api/workOrder.js` 加 `createWorkOrder`；新建 `views/workOrder/components/CreateWorkOrderDialog.vue` |
| A7-2 | **P0** | 需求 6 文案未改：Tab 名「超 SLA」（[workOrder/index.vue:333](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L333)）、口径提示 [:57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L57)、行样式注释 [:797](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L797)。**口径提示是老板判断「这条该不该催」的唯一说明**，不改则新语义对不上 | 全量替换见 B6；同时把 Tab 内部键 `overSla` → `overdueUnhandled`，请求参数改用新参数名（契约已兼容旧名，[workOrder.js:92](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L92) 明确「新参数为准」） | `workOrder/index.vue:333,57,797`；`:478`（`params.overSla='1'`）→ `params.overdueUnhandled='1'`；`:558,561` 的 `row.overSla` → `row.overdueUnhandled` |
| A7-3 | P1 | Tab 计数要**并发 6 个请求**（[workOrder/index.vue:504-511](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L504-L511)），每个 `pageSize=1`。需求 3 若再加「来源」筛选（手工/企微自动），组合数会继续膨胀 | 本次先**不加来源筛选**（改为在列表加「来源」列，见 A7-7）；计数逻辑改为「只统计当前筛选下的 5 个状态 + 1 个超时」，并把 6 次请求合并为一次 `Promise.all`（已是）→ 保持现状，登记为待后端提供 `GET /work-orders/stats` | `workOrder/index.vue:500-516`；新增需求登记于本文 A13 |
| A7-4 | P1 | 转单候选来源分裂且**不含在职状态**：ADMIN 走 `/employees`（`pageSize=100` 截断），站长走 `/schedules` 名册（[workOrder/index.vue:636-649](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L636-L649)），代码已自述该缺陷（[:622-623](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L622-L623) 的 TODO） | 需求 8（人事管理）落地时会产出「在职员工」的权威来源，届时统一改为「本站员工简表」接口；**本次不实现**，但在人事模块的接口清单里把该需求登记进去（见 B8） | `workOrder/index.vue:622-649`；登记到 B8 的接口清单 |
| A7-5 | P1 | 详情抽屉底部操作区**按钮数量随状态变化而跳动**：指派 + 转单 + 0~2 个流转按钮（[workOrder/index.vue:203-220](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L203-L220)），最多 4 个并排，最少 0 个（只剩一行灰字说明） | 主操作固定右侧（`margin-left:auto`），次操作（转单）收进「更多」下拉。理由：与移动端 `ActionBar` 的「主操作权重更大」规则一致（[ActionBar.vue:115-118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L115-L118)），且按钮位置稳定后不会误点 | `workOrder/index.vue:203-220`、`:905-914`（`.drawer-footer` 加 `justify-content: flex-end` 思路） |
| A7-6 | P1 | 列表无「来源」列，但契约已返回 `source`（[workOrder.js:37](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L37)）。需求 3 上线后，企微自动派发的工单与手工工单混在一起，老板无法区分 | 列表加一列「来源」（`MANUAL` → 手工 / `AUTO_WECHAT` → 企微自动，用 `StatusTag variant="outline"`），详情页在「类型」旁也显示；时间线已有 `auto_dispatch` 节点可直接渲染（[workOrder.js:369-372](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L369-L372)） | `workOrder/index.vue:74-108`（列定义）；`dict.js` 需新增 `WORK_ORDER_SOURCE` 字典 → **shared 层冻结**，须由主智能体解冻（见 0.2 推论 3）；`LOG_ACTION` 需补 `auto_dispatch: '企业微信自动派发'`（[workOrder/index.vue:308](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L308)） |
| A7-7 | P2 | 待处理工单「直关」需填原因（[workOrder/index.vue:547-559](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L547-L559)），但用的是 `ElMessageBox.prompt`——单行输入 + 无字数反馈，与转单理由（textarea + `show-word-limit`，[:260-269](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L260-L269)）不是一套形态 | 关闭原因改用与转单同款的 `el-dialog + el-form + textarea(maxlength=100, show-word-limit)`；处理说明（选填）保持 prompt 不动 | `workOrder/index.vue:543-576` |

## A8 通知中心（`pc/views/notification/index.vue`）——需求 4 主战场

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A8-1 | **P0** | 无「发布通知」入口，`pc/api/notification.js` 也无 `publishNotification`（全文件 4 个方法，[notification.js:1-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/api/notification.js)）。契约 `POST /notifications/publish` **已就绪**（[notification.js:121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L121)），且 Mock 已预置 3 批手工发布样本（[db.js:653-676](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L653-L676)）——**有数据没界面** | 页头加主按钮「发布通知」→ 抽屉表单（范围三选一 + 二级选择器）。详见 B4 | `notification/index.vue:3-7`；`pc/api/notification.js` 加 `publishNotification`；新建 `views/notification/components/PublishDrawer.vue` |
| A8-2 | P1 | 列表**不区分「系统联动」与「手工公告」**。契约已返回 `isPublished` / `publisherName` / `publishScope`（[notification.js:22-26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L22-L26)），PC 一个都没渲染（[notification/index.vue:42-69](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/notification/index.vue#L42-L69)） | 手工公告在标题前加一枚「公告」标识 + 行尾显示「由 {publisherName} 发布」；发布范围用 `caption` 级文字（如「范围：城东驿站」）。理由：公告是**别人下发的指令**，与「你被指派了一条工单」的处置优先级完全不同 | `notification/index.vue:55-62`；`NOTIFICATION_TYPE` 已有 `4=系统公告`（[dict.js:84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/dict.js#L84)）可直接用 `StatusTag` 渲染 |
| A8-3 | P1 | 只有「全部/未读」两个 Tab（[notification/index.vue:10-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/notification/index.vue#L10-L18)），无类型筛选。发布通知上线后公告会大量增长，工单类通知会被淹没 | 加类型下拉（`全部类型 / 工单指派 / 工单流转 / 同步失败 / 系统公告`）放在工具条左侧，与「全部标记已读」同排。**注意**：契约 `GET /notifications` 当前只认 `isRead`（[notification.js:33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L33)），需先补 `type` 参数 → 列入待后端清单 | `notification/index.vue:20-27`；契约变更需求登记于本文 A13 |
| A8-4 | P2 | 「全部标记已读」在未读为 0 时 disabled 但**不给原因**（[notification/index.vue:23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/notification/index.vue#L23)）；同步页已有 `:title` 说明 disabled 原因的良好先例（[sync/index.vue:84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L84)） | 补 `:title="unreadCount ? '将全部未读标记为已读' : '当前没有未读通知'"`。作为规则推广到全站 disabled 按钮 | `notification/index.vue:23` |

## A9 考勤管理（`pc/views/attendance/index.vue`）——需求 5 主战场

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A9-1 | **P0** | 无导出入口，`pc/api/attendance.js` 无 `exportAttendance`（[attendance.js:1-85](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/api/attendance.js) 仅 14 个方法）。契约 `GET /attendance/export` **已就绪且返回 Blob**（[attendance.js:321-357](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L321-L357)），Mock 适配器也已支持 `responseType:'blob'`（[engine.js:114-118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/engine.js#L114-L118)） | 页头加「导出」按钮 → 确认弹窗（回显筛选摘要 + 预计行数）→ 走 blob + `saveResponseFile`。详见 B5 | `attendance/index.vue:3-16`；`pc/api/attendance.js` 加 `exportAttendance`；复用 `@admin/utils/download.js` 的 `saveResponseFile`（[download.js:6-33](file:///d:/Users/16626/Desktop/hrm-dev/hrm-admin/src/utils/download.js#L6-L33)） |
| A9-2 | P1 | **明知无效仍给控件**：打卡类型下拉（[attendance/index.vue:61-65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L61-L65)）在契约层被忽略（`records()` 未解构 `checkType`，[attendance.js:292-307](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L292-L307)），页面只能用一段长文案解释（[:84-88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L84-L88)）。用户会以为筛选生效 | 二选一：**(a)** 后端补 `checkType` 参数（推荐，字段本就存在于记录里）；**(b)** 短期内把该控件 `disabled` 并给 `title` 说明。**禁止**保留「可用但无效」的第三态 | `attendance/index.vue:61-65,84-88`；契约变更需求登记于本文 A13 |
| A9-3 | P1 | 驿站切换**默认选中第一个驿站**（[attendance/index.vue:281-282](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L281-L282)），老板视角看到的永远是城东驿站的概况——「全域」这个数据范围在页面上**看不见**。页头 `sub` 虽写「数据范围：全域（可切换驿站）」（[:244-248](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L244-L248)），但三块数据实际都不是全域 | 驿站下拉加「全部驿站」选项（值 `null`）并设为 ADMIN 默认；`summary` 在同一次请求里返回全域口径。**前置**：需确认契约对 ADMIN 传空 `stationId` 的行为（U4）；若契约不支持，则退化为「默认不选 + 三块各自空态提示『请先选择驿站』」 | `attendance/index.vue:281-282`；`:236`（`effectiveStationId`）；验证方式见 U4 |
| A9-4 | P1 | 「今日打卡概况」6 卡用 `:cols="6"` 硬编码（[attendance/index.vue:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L21)），在 992–1200 断点每卡约 150px，「早退」「缺卡」的中文标签会被压 | `MiniStats` 的列数改为**按容器宽度自适应**：内部用 `ResizeObserver` 或 CSS Grid `repeat(auto-fit, minmax(140px, 1fr))`，`cols` prop 降级为「最大列数」。理由：`cols` 由调用方拍脑袋给（包裹页 6、考勤页 6、看板 4），是全站同类缺陷 | `pc/components/MiniStats.vue`；`attendance/index.vue:21`、`parcel/index.vue:14` 同步简化 |
| A9-5 | P2 | 「缺卡」只是概况里的一个数字，**没有下钻**；补卡审批入口挂在页面最底部（[attendance/index.vue:155](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L155)），老板要滚动整页才能看到审批区 | 「缺卡」卡加下钻 → 筛选到 `status=ABNORMAL`；补卡审批改为**页内 Tab 第三视图**（概况+记录 / 补卡审批）而不是页尾堆叠。理由：审批是高频动作，不该在页面末尾 | `attendance/index.vue:19-29`（MiniStats 加 `clickable`）、`:155` 移入 Tab |

## A10 排班管理（`pc/views/schedule/index.vue`）——需求 2 主战场

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A10-1 | **P0** | 只有「逐格 `el-select`」一种录入方式（[schedule/index.vue:83-104](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L83-L104)）。8 人 × 7 天 = **56 次下拉 × 2 次点击 = 112 次操作**铺一周，与需求 2「减少操作频次」正面冲突。契约 `POST /schedules/batch-by-station` **已就绪但无人调用**（[attendance.js:479](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L479)） | 引入 4 个批量能力：①「复制上一周」②「一键铺排（按驿站+日期区间+星期）」③「整行/整列批量设班」④「清空全周」。详见 B2 | `schedule/index.vue:15-22`（PageHeader actions 加「批量工具」下拉）；新建 `views/schedule/components/BatchToolsDialog.vue`；`pc/api/attendance.js` 加 `saveSchedulesByStation`、`copyWeekSchedules` |
| A10-2 | P1 | 单元格是下拉，**无法键盘录入**：表格无焦点管理，键盘用户要 Tab 过 56 个 select（每次 Tab 会打开下拉）。一线排班员实际是鼠标 + 键盘混用 | 支持「点击单元格选中 → 键盘 `1/2/3` 直接赋班次 → `Esc` 取消」，并支持「选中某天 → `Ctrl+C` → 选中另一天 → `Ctrl+V`」。理由：排班是**重复性极高的同构操作**，键盘录入比下拉快 5–10 倍 | `schedule/index.vue:78-106`（单元格改为可聚焦 `div[role=gridcell]` + `tabindex`）；`:194-196`（键盘映射挂在班次下标） |
| A10-3 | P1 | 未保存改动的提示只在**切周/切驿站/刷新**时出现（[schedule/index.vue:267-280](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L267-L280)），**离开页面/关闭标签页时无提示**（无 `beforeunload`、无路由守卫拦截） | 加 `onBeforeRouteLeave` 守卫：有 `dirtyCount` 时弹确认；`window.beforeunload` 兜底。理由：56 格编辑成本高，误丢一次就是几分钟白干 | `schedule/index.vue` 新增 `onBeforeRouteLeave`；`dirtyCount` 已有（[:180](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L180)） |
| A10-4 | P1 | 周视图**没有「本周已排/未排」进度**，老板看不出这周排完没有；`grid-toolbar` 只显示「共 N 名在岗员工」（[schedule/index.vue:48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L48)） | 在周标签旁补「已排 X/Y 天·格」统计（`Y = 员工数 × 7`，`X = 已排格数），并给未排满的日期列头加提示色。移动端 `boss/schedule.vue` 已有「已排 X/Y」的同类表达（[boss/schedule.vue:234](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L234)），两端口径统一 | `schedule/index.vue:33,48`；`:432-461`（`.day-head` 加未排满态） |
| A10-5 | P2 | 已排「停用班次」的单元格无法识别：下拉里停用项是 `disabled`（[schedule/index.vue:96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L96)），但**历史遗留的停用班次仍会显示在格子里且无色条**（`shiftColorOf` 找不到就返回 `transparent`，[:204-208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L204)） | 该格显示「已停用」标签 + 警告色左条；提交时该格会因 9106 被服务端拒绝（`handleSave` 已有 9106 分支，[:295-297](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L295-L297)），前端要**在提交前**就把这些格标出来 | `schedule/index.vue:78-106,204-208` |

## A11 员工管理（一期页面，需求 7 的挂载点）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A11-1 | **P0** | **需求 7 与既有约束冲突**：需求是「KPI 考核并入员工管理模块」，但 PC `/employee` 直接复用一期视图（[pc/router/index.js:40-45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/router/index.js#L40-L45)），而一期页面在**冻结清单内禁止改动**（demo-ui-redesign.md 0.1 / 1.6）。若前端直接改一期页，会污染一期交付基线 | **解法（推荐）**：不改一期视图，在 Demo 内新建 `/employee/kpi`（KPI 考核）与 `/employee/detail/:id`（员工档案聚合页：基本信息 + KPI + 入离职 + 工资单入口），侧边栏在「组织人事」分组下并列呈现。**员工管理** 与 **KPI 考核** 是同级菜单项，但从 KPI 表格点员工姓名可跳到档案聚合页——「并入员工管理模块」以**信息架构归属**实现，而非物理合并页面 | `pc/config/menu.js:21-33`（同 group 并列）；`pc/router/index.js:40-45` 之后新增两条；新建 `views/employee/kpi/index.vue`、`views/employee/detail/index.vue`。**禁止**改 `hrm-admin/**` |
| A11-2 | P1 | 一期员工页的员工列表无法携带 KPI 得分/排名（页面冻结）。老板看「谁该谈绩效」必须先开 KPI 页、再回员工页找人对齐 | 在 KPI 页的表格里直接给出**可跳转的员工档案入口**（点姓名 → `/employee/detail/:id`），并在档案页反向展示该员工的全部画像；一期员工页保持不动，仅在它旁边多一个菜单项 | 同 A11-1 |
| A11-3 | P2 | 一期员工页有「导入导出」（[employee.js mock](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js) 的 `IMPORT_TEMPLATE_HEADER`），但**没有入离职专用流程**，与需求 10 的「流程」不是一回事（导入是批量建档，流程是逐人状态推进） | 入离职流程独立成页（`/onboarding`），**不复用**员工导入/导出。理由：两者数据模型不同（流程有步骤、责任人、驳回记录），硬塞进导入会让「导入失败」与「流程驳回」两套错误提示串味 | `views/onboarding/**`（新建）；不在员工页加入口 |

## A12 移动端 · 老板端（`mobile/views/boss/**` + Tabbar）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A12-1 | **P0** | **Tabbar 已满，新功能无处可放**：老板端 6 项（[tabs.js:8-15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L8-L15)），代码内已注明「320px 下每项约 53px，已达上限」（[:5-6](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L5-L6)）；员工端同样 6 项（[:17-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L17-L24)）。本次新增 5 个模块（KPI/人事/财务/入离职/采集配置）**一个都进不了 Tabbar** | **明确约定（本轮所有移动端设计的硬前提）**：新功能只能走两条路径——**(a)** 首页宫格入口（`/boss/home` 的 `van-grid`）；**(b)** 「我的」页 `van-cell-group` 分组（[MeSection.vue:42-48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L42-L48)）。**禁止**为了塞新功能而把 Tabbar 加到 7 项（320px 下每项 <46px，触控不达标） | 本条为**约束**而非改动；B 章各项移动端入口均按此设计 |
| A12-2 | **P0** | 老板端**没有采集状态视图**（需求 1 要求「区分驿站同步采集状态」）。现有 `/boss/alerts` 三个分组只覆盖「超 48h 未取件 / 同步失败驿站 / 超时未处理工单」（[alerts.vue:86,114,135](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L86-L138)），缺 `UNCONFIGURED` / `DISABLED` 两态，老板看不到「有驿站压根没配采集」 | 老板端只做**只读状态卡**（不做配置）：在 `/boss/alerts` 增加「采集状态」分组，4 态计数 + 明细行；同时在 `/boss/home` 的「同步健康度」卡里补一行「未配置采集 N 站」。配置能力只留 PC | `boss/alerts.vue`；`boss/home.vue:182-187`（同步健康度卡）；数据源 `GET /sync/overview` |
| A12-3 | P1 | 首页「常用入口」只有 2 项（[boss/home.vue:162-166](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L162-L166)：工单管理 / 补卡审批），本次要加的「工资单审核 / 入离职审批 / 模拟派单 / KPI 考核」没有位置 | 扩到 **6 项（3 列 × 2 行）**，按「待办优先」排序：工单管理 / 补卡审批 / 工资单审核 / 入离职审批 / KPI 考核 / 模拟派单。每项按待办数给角标（复用 `van-grid-item` 的 `badge`，通知项已有先例 [staff/home.vue:38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L38)） | `boss/home.vue:163`（`:column-num` 2 → 3）；`:164-165` 扩项 |
| A12-4 | P1 | 「我的」页入口按角色分流但只有 2 条（[MeSection.vue:44-48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L44-L48)）。老板的身份是「审批者」，本次新增了 3 条审批线（工资单/入离职/补卡已有），入口会溢出到页面下方 | 「我的」页把 `常用入口` 拆成分组：**待办审批**（工单/补卡/工资单/入离职，带角标）与 **我的数据**（KPI/我的工资单/我的排班/打卡记录）。理由：老板与员工共用同一组件，分组标签让两端都能自解释 | `MeSection.vue:43-48` 改为两个 `van-cell-group` |
| A12-5 | P2 | 需求 6 文案未改，老板端 3 处（[boss/home.vue:65,155,161](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L65-L161)）+ 预警页 4 处（[alerts.vue:135,138,145,163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L135-L163)） | 统一改「超时未处理」 | 见 B6 全量清单 |
| A12-6 | P2 | 老板端 Hero 的「口径：全域」是不可点的 chip（[boss/home.vue:114-115](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L114-L115)），代码已注 TODO；用户会尝试点它 | 短期把它降级为纯文字（去掉 chip 的按钮隐喻），或加 `title`/`aria-label` 明确「当前不可切换」。理由：**不可交互的元素不应该长得像按钮** | `boss/home.vue:115` |
| A12-7 | P2 | 老板端详情页复用员工端（[boss/workorder.vue:112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L112) 直接跳 `/staff/workorder/:id`），当前没问题（同页多入口是有意设计）；但本次工资单/入离职也会出现「老板看审核态、员工看确认态」的同页两态 | 登记为**模式**：同一业务对象的两端视图**优先复用同页 + 按角色渲染不同操作栏**，不新写页面。仅在「信息结构真正不同」（如工资单老板看明细+审核，员工看汇总+确认）时才拆页。写进前端实现约定 | 约定，无代码改动 |

## A13 移动端 · 员工端（`mobile/views/staff/**` + Tabbar）

| # | 优先级 | 现存问题 | 改进建议 | 落地要点 |
| ---- | ---- | ---- | ---- | ---- |
| A13-1 | **P0** | 员工端缺 3 个自查入口：**我的 KPI**、**我的工资单**、**我的入离职进度**。Tabbar 已满（[tabs.js:17-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L17-L24)），必须走首页宫格 + 我的 | 首页宫格从 5 项扩到 **8 项（4 列 × 2 行）**，按频率排序：打卡 / 本站包裹 / 取件核销 / 工单 / 通知 / **我的排班** / **我的工资单** / **我的 KPI**；同步状态（仅站长）从宫格下沉到「我的」。理由：`staff/home.vue` 的 `entries` 已按角色动态 push（[staff/home.vue:32-42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L32-L42)），扩展成本低 | `staff/home.vue:32-42`（entries 数组）、`:149`（`:column-num` 4 保持）；`MeSection.vue:43-48` 同步 |
| A13-2 | P1 | 「我的」页只有账号信息 + 改密 + 身份切换（[MeSection.vue:42-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L42-L72)），员工的「个人业务数据」全靠首页宫格，一旦宫格排满就无处扩展 | 同 A12-4：拆「我的数据」分组，容纳 KPI / 工资单 / 排班 / 打卡记录 / 补卡申请。这样宫格只放**高频作业动作**，低频查询进「我的」 | `MeSection.vue:43-72` 重构为「待办 / 我的数据 / 账号信息 / 演示身份 / 账号安全 / 运行环境」六段 |
| A13-3 | P1 | 员工端**打卡页是 Tab 一级**，但「我的排班」「打卡记录」「补卡申请」都在二级（[mobile/router/index.js:65-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L65-L67)），打卡页内没有到这三页的入口聚集区；员工要「看今天排什么班」得先绕到首页宫格或「我的」 | 打卡页顶部时钟下方加一行「今日班次」直显（`getAttendanceStatus` 已返回 `shift`，[staff/home.vue:49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L49) 已在用），并在页面底部加三个快捷入口（我的排班 / 打卡记录 / 补卡申请） | `staff/attendance.vue`（未详读，需前端按此结构核对后落地）；数据源已有 |
| A13-4 | P2 | 需求 6 文案未改，员工端 2 处（[staff/home.vue:134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L134)、[staff/workorder.vue:44,121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue#L44-L121)） | 统一改「超时未处理」 | 见 B6 |
| A13-5 | P2 | 员工端缺少「待我确认」类聚合（工资单需确认、入离职需提交资料），与通知页强耦合（[staff/notification.vue:103-114](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue#L103-L114) 只按 `bizType` 跳三处）。新增「工资单」与「流程」两类 `bizType` 后跳转会落空 | 员工端通知跳转表需扩展 `payroll` → `/staff/payroll/:id`、`flow` → `/staff/flow/:id`；并在无 `bizType` 但需确认的通知上加「去处理」动作。理由：**通知的价值在于一跳到位**，跳不到就是死信 | `staff/notification.vue:103-114` |
| A13-6 | P2 | 员工端 Hero 显示「今日待处理 N 条」（[staff/home.vue:115](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L115)）只统计工单 `pendingOrders`，不含待确认工资单/待提交资料 | 口径改为「待我处理」= 待处理工单 + 待确认工资单 + 待提交流程步骤；Hero 文案改为「今日待处理 N 条」，并在下钻提示里说明构成 | `staff/home.vue:81-88,115` |

### A 章汇总

| 优先级 | 条数 | 说明 |
| ---- | ---- | ---- |
| **P0** | 15 | 需求 1–6 的界面缺失（5 条：A6-1/A7-1/A7-2/A8-1/A9-1）、需求 2 批量能力缺失（A10-1）、移动端入口容量与采集状态（A12-1/A12-2/A13-1）、菜单与路由容量（A3-1/A3-2）、KPI 挂载点冲突（A11-1）、入口页品牌与脱节（A1-1/A1-2） |
| **P1** | 29 | 效率与状态完整性 |
| **P2** | 22 | 一致性与细节 |
| **合计** | **66** | A1 5 / A2 2 / A3 7 / A4 6 / A5 4 / A6 5 / A7 7 / A8 4 / A9 5 / A10 5 / A11 3 / A12 7 / A13 6 |

**待后端/契约补齐的需求清单**（前端无法自行解决）：

| 编号 | 需求 | 涉及方 | 说明 |
| ---- | ---- | ---- | ---- |
| R-1 | `GET /work-orders/stats` 或按状态聚合计数 | 后端 | 替代 6 次 `pageSize=1` 请求（A7-3） |
| R-2 | `GET /notifications` 支持 `type` 参数 | 后端 | 通知类型筛选（A8-3） |
| R-3 | `GET /attendance/records` 支持 `checkType` 参数 | 后端 | 消除无效控件（A9-2） |
| R-4 | `GET /schedules` 或新增「本站员工简表」（含在职状态） | 后端 | 统一转单/指派候选来源（A7-4） |
| R-5 | shared 层解冻：`dict.js` 增 `WORK_ORDER_SOURCE`、`errorCode.js` 增 92xx/93xx/94xx 段 | 主智能体 | A7-6、B7、B8、B9、B10 |
| R-6 | 需求 7–10 的数据契约定稿 | 主智能体 + 后端 | B7–B10 全部依赖 |

---

# B. 10 项新增功能设计规范

## B0 通用规则（10 项全部适用，先行约定）

### B0.1 空间约束：三端各自的可扩展位

| 端 | 已满的地方 | 可扩展的地方 |
| ---- | ---- | ---- |
| PC 侧边栏 | 平铺 11 项（[menu.js:21-33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L21-L33)） | **分组化后 4 组**；每组 ≤6 项；新增项一律挂组内（A3-1） |
| 移动端 Tabbar | 两端各 6 项（[tabs.js:8-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L8-L24)） | **不可再加**。只能走首页宫格（≤8 项）与「我的」cell-group（A12-1） |
| PC 页内 Tab | 工单页 6 个状态 Tab（[workOrder/index.vue:327-334](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L327-L334)）、通知页 2 个 | 单页 Tab **上限 4 个**；超过就把次要视图放进**抽屉或页头按钮** |

### B0.2 组件状态最小集（本轮新增组件的验收标准）

新增组件必须实现以下 **7 态**，缺一不可（与 demo-ui-redesign.md 的状态规范一致，沿用现有 `StateBlock` 与 `PageState`）：

| 态 | 表现 | 复用 |
| ---- | ---- | ---- |
| 默认 | 有数据正常渲染 | — |
| 加载 | **表格类**：表头保留 + 行区 `v-loading`；**卡片类**：`MetricCard` 的 `loading`；**移动列表**：`.skeleton-block` 块骨架 | [StateBlock](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/StateBlock.vue)、[PageState](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue) |
| 空 | 独立空态 + 引导动作（**禁止**与错误态共用文案） | `StateBlock variant="empty"` [StateBlock.vue:21-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/StateBlock.vue#L21-L25) |
| 错误 | 独立错误态 + 「重试」 | `StateBlock variant="error"` |
| 禁用 | 必须给 `title` 说明原因 | 先例 [sync/index.vue:84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L84) |
| 无权限 | 只读降级 + 一行说明，不渲染不可用按钮 | 先例 [schedule/index.vue:490-494](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L490-L494)、[schedule/index.vue:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L110) |
| 边界 | 超长文本省略 + 完整值可达（tooltip/展开）；数量 0 / 极大值都要有明确渲染 | `show-overflow-tooltip`、`paginate` |

### B0.3 二次确认（危险/不可逆操作清单）

必须二次确认的操作（本轮新增）：**提交/发布类不可撤回**（发布通知、工资单发布、入离职审批通过）、**批量覆盖类**（一键铺排带 `skipExisting=false` 会覆盖已有排班、复制上一周覆盖当前周）、**权限/状态不可逆类**（工资单作废、离职归档）。

文案规范：确认框标题用**动词短语**（「发布工资单」），正文说清**影响范围与不可逆性**（「将发布给 12 名员工，发布后不可撤回」），按钮用**具体动词**（「确认发布」/「再想想」），不用「确定/取消」。

### B0.4 移动端分工总表（老板端做什么 / 员工端做什么）

| 模块 | 老板端（ADMIN 移动） | 员工端（STATION_ADMIN / STAFF 移动） |
| ---- | ---- | ---- |
| B1 采集状态 | 只读：4 态计数 + 异常驿站明细 | 站长只读本站采集状态；员工不可见 |
| B2 排班 | 可写：批量工具 + 逐人调整 | 只读：我的排班 |
| B3 工单 | 督办：不新建；可「模拟派单」（可选） | 站长/员工：新建工单（已实现） |
| B4 通知 | 可写：发布通知（≤2 步表单） | 只读：收通知 + 处理动作 |
| B5 考勤导出 | **不做**（手机上下载 CSV 无意义） | 不做 |
| B6 文案 | 改文案 | 改文案 |
| B7 KPI | 可写：查看全站结果、配置指标与权重 | 只读：我的 KPI 得分与排名 |
| B8 人事 | 可写：查看/维护薪资标准、调薪 | 只读：我的档案（合同/岗位/薪资条） |
| B9 财务 | **审核**：草稿审核 → 发布 | **确认**：查看工资条 → 确认 / 提异议 |
| B10 入离职 | **审批**：各步骤审批与驳回 | **执行**：提交资料、确认交接 |

---

## B1 同步任务模块化 + 驿站采集状态

### B1.1 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「包裹作业 → 数据同步」（键 `sync`，路径不变 `/parcel/sync`）。页内 Tab：**批次流水**（现有） / **采集配置**（新增）。Tab 状态写 URL：`?tab=collect` |
| 老板端 | `/boss/alerts` 增「采集状态」分组（只读）；`/boss/home` 同步健康度卡补一行「未配置采集 N 站」 |
| 员工端 | 站长：`/staff/sync`（现有页）顶部加只读采集状态卡；员工不可见 |

**为什么不新开路由**：`sync` 键已在白名单（[role.js:28-29](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/role.js#L28-L29)），新开路由要动 shared 层；且两者是同一业务的两种视图，页内 Tab 语义正确。

### B1.2 信息架构与区块划分（采集配置 Tab）

```text
① 采集状态总览（4 计数卡，异常优先）
   [正常 N]  [异常 N]  [未配置 N]  [已停用 N]
   —— 数据源 GET /sync/overview 的 counts（权威计数，不做前端聚合）
② 待处理提示条（仅当 abnormal + unconfigured > 0 时出现）
   「2 个驿站采集异常、1 个驿站未配置采集 → 查看列表」→ 表格自动筛选到异常
③ 采集配置表（8 行，按状态排序：异常 → 未配置 → 正常 → 已停用）
   列：驿站 | 采集状态 | 采集开关 | 采集频次 | 数据源 | 采集时段 | 最后采集 | 最近批次 | 操作
④ 编辑抽屉（点行打开）
   区块：基础配置（开关/数据源/频次）→ 采集时段（起止）→ 只读信息（最后采集、最近批次）
```

### B1.3 关键组件与状态

| 组件 | 形态 | 状态要求 |
| ---- | ---- | ---- |
| `CollectStateBoard`（新建） | 4 张计数卡，复用 `MetricCard variant="inline"` | 加载：逐卡 skeleton；错误：整块 `StateBlock variant="error"` + 重试；空：不适用（每站必有配置行，见 [db.js:393](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L393) 注释）；边界：某态为 0 时卡片显示 `0` 而非 `—` |
| `CollectConfigTable`（新建） | `el-table` + `CollectStateTag` | 加载：表头 + `v-loading`；错误：`StateBlock variant="error"`；无权限：站长视角隐藏「操作」列并给只读说明；边界：`dataSource` 为 null 时显示「—」，时段为 null 显示「未设置」 |
| `CollectStateTag` | **复用 `StatusTag`**，新增字典 `COLLECT_STATE` | 四态映射见 B1.4；`DISABLED` 用 `variant="outline"`（无动作诉求） |
| `CollectConfigDrawer`（新建） | `el-form`（label-width 100px） | 加载：打开时拉 `GET /sync/configs/:stationId`；错误：抽屉内 `StateBlock` + 重试；禁用：`status=0`（驿站停用）时全表单只读；边界：`collectEndTime` 允许 `24:00`（[syncConfig.js:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L21)） |

**需新增的字典**（shared 层，须主智能体解冻，见 R-5）：

```js
/** 采集状态：与 syncConfig.js 的 COLLECT_STATES 逐字一致（第 18 行） */
export const COLLECT_STATE = {
  NORMAL:       { label: '正常',   type: 'success' },
  ABNORMAL:     { label: '异常',   type: 'danger'  },
  UNCONFIGURED: { label: '未配置', type: 'warning' },
  DISABLED:     { label: '已停用', type: 'info'    }   // 渲染为 outline
}

/** 采集频次：与 syncConfig.js 的 FREQUENCIES 逐字一致（第 17 行） */
export const COLLECT_FREQUENCY = {
  HOURLY:     { label: '每小时' },
  EVERY_2H:   { label: '每 2 小时' },
  EVERY_4H:   { label: '每 4 小时' },
  DAILY:      { label: '每天' }
}
```

### B1.4 状态映射表（Tag 形态，三端统一）

| `collectState` | 文案 | 色族 | 形态 | 颜色 Token |
| ---- | ---- | ---- | ---- | ---- |
| `NORMAL` | 正常 | success | soft | `--state-success-bg/fg/border` |
| `ABNORMAL` | 异常 | danger | **solid**（需立刻行动） | `--state-danger-fg` 作底 + 白字 |
| `UNCONFIGURED` | 未配置 | warning | soft | `--state-warning-bg/fg/border` |
| `DISABLED` | 已停用 | neutral | outline | `--state-outline-*` |

### B1.5 交互流程

**正常流（配置采集）**：进入采集配置 Tab → 表格定位到 `UNCONFIGURED` 行 → 点行开抽屉 → 填数据源「多多买菜」+ 频次「每小时」+ 时段 08:00–22:00 → 开启采集开关 → 保存 → toast「采集配置已保存，下次采集周期生效」→ 表格行状态变为「正常」。

**异常流**：

| 场景 | 服务端 | 前端表现 |
| ---- | ---- | ---- |
| 未填数据源却开启采集 | `400 启用采集前须先选择数据源`（[syncConfig.js:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L98)） | **就地字段级报错**（数据源字段下方红字），不弹 toast |
| 结束时间早于开始 | `400 采集结束时间须晚于开始时间`（[:101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L101)） | 时段字段级报错 |
| 时段格式非法 | `400 采集开始时间格式须为 HH:mm`（[:89-90](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L89-L90)） | 用 `el-time-select` 或 `el-time-picker format="HH:mm"` 从源头避免 |
| 驿站不存在 | `4001`（[syncConfig.js:107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L107)） | 拦截器统一提示 |
| 未配置采集查询配置 | `6002`（[:82](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L82)） | 抽屉进入「未配置」空态 + 「立即配置」引导，**不能**与错误态混用 |
| 站长尝试保存 | `403`（路由 `roles:['ADMIN']`，[:146](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L146)） | 前端**不渲染**保存按钮，抽屉只读 + 一行说明（不做「点了才知道没权限」） |

**二次确认点**：关闭采集开关时确认——「关闭后该驿站将停止包裹采集，在途批次不受影响」。

### B1.6 PC 与移动端差异

| 项 | PC | 老板端 | 员工端（站长） |
| ---- | ---- | ---- | ---- |
| 视图 | 计数 + 表格 + 编辑抽屉 | 只读计数 + 明细行 | 只读本站状态卡（1 张卡 + 3 行 cell） |
| 可写 | ADMIN 可写 | 只读 | 只读 |
| 数据范围 | 全域（可切驿站） | 全域 | 本站（服务端强制） |

### B1.7 复用与新增 Token

- **复用**：`StatusTag`、`MetricCard`、`StateBlock`、`PageHeader`；`--state-*` 四族；`MiniStats` 的 grid 布局。
- **新增**：**无**（B1 只需 `COLLECT_STATE` / `COLLECT_FREQUENCY` 字典，不动色板）。

---

## B2 排班管理 · 按驿站设置与减少操作频次

### B2.1 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「考勤薪酬 → 排班管理」（路径不变 `/schedule`）。页头 `actions` 左侧新增 **「批量工具」下拉按钮** |
| 老板端 | `/boss/schedule` 页头新增「批量工具」，打开底部弹层 |
| 员工端 | `/staff/schedule`（我的排班）**只读**，不加任何批量入口 |

### B2.2 核心：把 112 次点击降到 1–3 次

需求 2 的「减少操作频次」不是一句体验口号，要落到 4 个可量化的能力。按**实现成本从低到高**排列：

| # | 能力 | 交互 | 操作次数（8 人 × 7 天铺满） | 契约 |
| ---- | ---- | ---- | ---- | ---- |
| ① | **一键铺排（推荐入口）** | 选「驿站 + 班次 + 日期区间 + 星期（可多选）+ 人员（默认全员）」→ 预览命中格数 → 提交 | **112 → 4** | ✅ `POST /schedules/batch-by-station`（[attendance.js:479](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L479)） |
| ② | **复制上一周** | 点「复制上一周」→ 预览（新增 X 格 / 覆盖 Y 格 / 清空 Z 格）→ 确认 → 本地生成 dirty → 保存 | **112 → 3** | ✅ 两次调用 `GET /schedules` + `POST /schedules/batch`（**无需新契约**） |
| ③ | **整行 / 整列批量** | 行首「全周」按钮 → 选班次 → 该员工 7 天全填；列头「全员」按钮 → 选班次 → 该天全员填 | **112 → 14**（8 行 + 7 列取其一） | ✅ 本地 dirty + `/schedules/batch`（单次上限 200 条，[schedule/index.vue:141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L141)） |
| ④ | **清空整周 / 清空某天** | 二次确认后置空 | **112 → 2** | ✅ 同上（`shiftId: null` 语义见 [boss/schedule.vue:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L59)） |

**实施顺序建议**：②（零契约成本）→ ①（契约已就绪）→ ③ → ④。

### B2.3 信息架构（PC 页头改造）

```text
PageHeader
├── actions 左：【批量工具 ▾】  （下拉菜单，4 项 + 分隔线 + 危险项）
│      · 一键铺排…            → BatchSpreadDialog
│      · 复制上一周             → 直接预览确认
│      · ─────────────
│      · 清空本周…（危险）      → 二次确认
├── actions 中：[撤销修改] [保存排班（N）]   ← 现有，不动
└── actions 右：[刷新]
```

表格新增两个批量锚点（不改变单元格编辑方式）：
- 行首列（「员工」列）hover 时出现「全周」小按钮（44×44 热区，图标 + `aria-label`）；
- 列头（日期）hover 时出现「全员」小按钮。

### B2.4 关键组件与状态

| 组件 | 状态要求 |
| ---- | ---- |
| `BatchToolsMenu`（新建，PC） | 禁用：`!canWrite` 时整个下拉禁用 + `title="仅超级管理员可执行批量排班"`；`dirtyCount>0` 时点击先触发 `confirmDiscard()`（复用 [schedule/index.vue:268-280](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L268-L280)） |
| `BatchSpreadDialog`（新建，PC） | 加载：提交时按钮 `loading` + 全表单禁用；错误：9106（班次已停用）→ 提示并刷新；400 参数 → 字段级；边界：`startDate > endDate` 时禁用提交并提示；`weekdays` 全不选时语义为「全周」需在 UI 显式说明 |
| `CopyWeekPreview`（新建，PC 抽屉） | 空：上一周没有任何排班 → 显示「上一周无排班记录，无可复制内容」+ 禁用「确认复制」；边界：当前周已有排班 → 预览必须分别列出 **新增 / 覆盖 / 清空** 三类计数 |
| 移动端 `BatchSheet` | 用 `van-popup position="bottom"`（复用 [boss/schedule.vue:270](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L270) 的 sheet 样式）；一键铺排简化为 3 个选择器（班次 / 日期区间 / 星期多选 chip） |

### B2.5 交互流程

**正常流 A（一键铺排）**：批量工具 → 一键铺排 → 驿站=城东（默认当前） / 班次=早班 / 日期=本周一~周日 / 星期=周一~周六 / 人员=全员 / **跳过已有排班=开** → 点「预览」→ 底部显示「将写入 48 个格子，跳过 8 个已有排班」→ 点「确认铺排」→ 提交 → toast「已铺排 48 处，跳过 8 处」→ 表格重载。

**正常流 B（复制上一周）**：批量工具 → 复制上一周 → 抽屉展示三类计数（新增 0 / 覆盖 40 / 清空 16）→ 点「确认复制」→ 本地生成 dirty（**不直接提交**）→ 用户仍可在表格上微调 → 点「保存排班（56）」→ 提交。

**为什么复制走「本地 dirty + 手动保存」而不是直接提交**：复制结果几乎一定需要微调（有人请假、有人换班），直接落库会让用户改完再存第二次，反而多一次请求。

**异常流**：

| 场景 | 表现 |
| ---- | ---- |
| 铺排日期区间跨月/跨年 | 允许，但预览需显示实际日期范围（`YYYY-MM-DD ~ YYYY-MM-DD`），避免用户选错 |
| 提交的班次已被停用（9106） | **不覆盖本地改动**：toast 警告 + 重新拉取矩阵（现有逻辑已实现，[schedule/index.vue:295-297](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L295-L297)），用户改完再存 |
| 单次提交超过 200 条 | 前端**在提交前**拦截：`items.length > 200` 时提示「单次最多提交 200 条，请分批保存」，并给出「提交前 200 条」按钮 |
| 无在岗员工 | 空态引导去员工管理（现有，[schedule/index.vue:55-60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/schedule/index.vue#L55-L60)） |
| 未保存改动时点批量工具 | 先 `confirmDiscard()`，取消则整体不执行 |

**二次确认点（3 个）**：① 一键铺排且「跳过已有排班」**关闭**时（会覆盖）；② 复制上一周（会覆盖当前周）；③ 清空本周。

### B2.6 PC 与移动端差异

| 能力 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 一键铺排 | ✅ 完整（含人员多选、星期多选） | ✅ 简化（班次 / 日期区间 / 星期 chip） | ❌ |
| 复制上一周 | ✅ | ✅ | ❌ |
| 整行/整列批量 | ✅ | ❌（矩阵在移动端不可用，[boss/schedule.vue:14-16](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/schedule.vue#L14-L16) 已说明按「单日 + 逐人」组织） | ❌ |
| 清空本周 | ✅ | ✅ | ❌ |
| 我的排班 | — | — | ✅ 只读 |

**移动端矩阵的降级策略（明确登记）**：移动端不做 7 列矩阵，保持「日期条 + 单人单日」的现有结构；批量能力只通过底部弹层的 3 个按钮进入。这是**有意的能力降级**，不是遗漏。

### B2.7 复用与新增 Token

- **复用**：`PageHeader`、`StateBlock`、`ActionBar`（移动端）、`el-table`、`el-select`、`el-date-picker`、`van-popup`；`--sp-*`、`--state-warning-*`、`--color-primary-*`。
- **新增**：**无**。

---

## B3 工单管理 · 新建工单 + 企业微信自动派单模拟入口

### B3.1 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「包裹作业 → 工单管理」。页头 `actions` 新增两个按钮：**[新建工单]（primary）** + **[自动派单]（default）** |
| 老板端 | `/boss/workorder` 页头可选加 [模拟派单]（**默认不加**，见 B3.6）；不提供新建 |
| 员工端 | `/staff/home` 宫格「工单」→ 列表页 FAB/按钮「新建工单」（**已实现**，[workorderCreate.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorderCreate.vue)） |

### B3.2 新建工单（PC）

**为什么用弹窗（`el-dialog`）而不是抽屉**：字段 7 个、无嵌套结构，抽屉的 520px 宽度里会空一半；且新建是「一次性提交」而非「边看边改」，弹窗更合适。**注意与移动端表单字段严格对齐**（[workorderCreate.vue:20-26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorderCreate.vue#L20-L26)），PC 多 3 项。

```text
CreateWorkOrderDialog（width 560px）
① 基础信息
   · 归属驿站 *  （ADMIN 必选，默认取当前筛选驿站；站长固定为本站，只读展示）
   · 工单类型 *  （四选一，复用 dict.js 的 WORK_ORDER_TYPE 1-4）
   · 优先级 *    （低/中/高，复用 WORK_ORDER_PRIORITY 0-2）
② 内容
   · 标题 *      （1-100，show-word-limit）
   · 描述        （0-500，textarea rows=4，show-word-limit）
   · 关联运单号  （选填，精确匹配；填了就送 waybillNo）
③ 指派（可跳过）
   · 指派处理人  （选填，filterable select，候选 = 归属驿站在职员工）
④ SLA 预览（只读，随优先级实时变化）
   「SLA 截止：2026-09-19 18:00（高优先级 8 小时）」
   —— 口径取 dict.js 的 WORK_ORDER_SLA_HOURS，禁止前端另写一份
```

**校验（前后端同口径）**：

| 字段 | 规则 | 服务端出处 |
| ---- | ---- | ---- |
| `type` | ∈ {1,2,3,4} | [workOrder.js:106](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L106) |
| `priority` | ∈ {0,1,2} | [:107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L107) |
| `title` | 1–100 字符 | [:108](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L108) |
| `content`/`description` | ≤500 字符 | [:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L110) |
| `assigneeId` | 必须是在职员工 | [:116](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L116) |
| `assigneeId` 跨站（非 ADMIN） | 拒（8004） | [:118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L118) |

**交互流程**：点「新建工单」→ 弹窗（驿站默认当前筛选值）→ 选类型/优先级（SLA 预览实时变）→ 填标题 → 选处理人（可选，选择器只列归属驿站在职员工）→ 提交 → 成功 toast「工单 WO-20260919-0121 已创建」+ **自动跳转并展开该工单详情**（列表接口返回 `{id, orderNo, source}`，[workOrder.js:161](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L161)）。

**二次确认点**：指派了非本站员工（仅 ADMIN 可跨站）时确认——「将工单指派给其他驿站的 {姓名}，确认？」。

### B3.3 企业微信自动派单（模拟演示入口）

**契约现状**：`GET /work-orders/dispatch-rules`（列表）+ `PUT /work-orders/dispatch-rules/:id`（ADMIN 编辑）+ `POST /work-orders/auto-dispatch`（`auth:false`）三个接口已就绪（[workOrder.js:398-400](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L398-L400)）。

**页面结构：`AutoDispatchDrawer`（宽 640px，两步）**

```text
Step 1 模拟群消息（醒目的「模拟演示」区）
  · 来源群名称    （选填，默认「城东驿站-异常件处理群」）
  · 发送人        （选填，默认「企微助手」）
  · 消息内容 *    （textarea 6 行，粘贴真实群消息文本）
  · 归属驿站 *    （默认当前筛选驿站）
  · [解析预览] 按钮
Step 2 解析结果预览（只读卡 + 可覆盖）
  · 命中规则      「含『破损』→ 包裹异常 / 高优先级」 / 未命中时提示「未命中任何规则，将使用默认：其他 / 中」
  · 解析类型      （可手动改，默认取命中规则）
  · 解析优先级    （可手动改）
  · 默认处理人    「未指定」或「{姓名}」
  · SLA 预览      「SLA 截止：…（{优先级} {N} 小时）」
  · [确认派单] / [返回修改]
```

**模拟与真实的视觉区分（硬要求）**：

| 区分手段 | 具体做法 |
| ---- | ---- |
| 区域底色 | 模拟输入区整块使用 `--state-simulate-bg/fg/border`（新增 Token，见 C 章），与真实表单的白底明确拉开 |
| 固定标识 | 抽屉标题右侧挂一枚 `StatusTag`：「**模拟演示**」（outline 形态） |
| 占位文案 | 消息内容 textarea 的 placeholder 说明是「粘贴一条企业微信采集群里真实出现过的消息文本，系统将按规则解析（**不会真的连接企业微信**）」 |
| 结果标注 | 生成后的工单在列表「来源」列显示「企微自动」标签（`AUTO_WECHAT`）；详情时间线出现「企业微信自动派发」节点 |
| 文档标注 | 代码与文档均标 `TODO(扩展)`（见下） |

**必须在代码里落的 `TODO(扩展)`**：

```js
// TODO(扩展): 接入企业微信机器人回调时替换为真实签名校验与消息解密
//   —— 校验 msg_signature / timestamp / nonce，用 EncodingAESKey 解密 Encrypt 字段后再解析消息体；
//   本接口当前只接受已解析的明文（依据 shared/mock/routes/workOrder.js:328-330）
```

**交互流程（正常）**：页头「自动派单」→ 抽屉 Step1 → 粘贴「A3 货架三件破损，客户拒收」→ 选驿站城东 → 「解析预览」→ Step2 显示命中「破损」规则 → 类型=包裹异常、优先级=高、处理人=张三 → 「确认派单」→ toast「已按规则生成工单 WO-…-0122，处理人 张三」→ 抽屉关闭 + 列表刷新并把新工单**高亮**（复用 `.is-highlight` 模式，[mobile.scss:280-293](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L280-L293)）。

**异常流**：

| 场景 | 服务端 | 前端 |
| ---- | ---- | ---- |
| 消息内容为空 | `8006`（[workOrder.js:336](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L336)） | Step1 必填校验 + 就地报错，**不进入** Step2 |
| 驿站不存在 | `4001`（[:338](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L338)） | 字段级报错 |
| 未命中规则 | 不报错，使用默认 其他/中（[:276](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L276)） | **必须显式提示**「未命中规则，已用默认类型/优先级」，否则用户以为规则生效了 |
| 无派单规则 | — | 规则表空态：「暂无派单规则，未命中时将使用默认类型与优先级」+ 引导编辑 |

**二次确认点**：确认派单（生成后不可撤销，只能关闭工单）。

### B3.4 派单规则表（同抽屉的第三个区块）

放在 `AutoDispatchDrawer` 的底部折叠区「派单规则（N 条）」：

| 列 | 内容 |
| ---- | ---- |
| 关键词 | `rule.keyword`（1-20 字符） |
| 工单类型 | `WORK_ORDER_TYPE` |
| 优先级 | `WORK_ORDER_PRIORITY` |
| 默认处理人 | 员工姓名 / 「未指定」 |
| 启用 | `el-switch` |
| 操作 | 编辑（行内弹层） |

**权限**：`PUT /work-orders/dispatch-rules/:id` 仅 ADMIN（[workOrder.js:399](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L399)），但 `GET .../dispatch-rules` 未加 `roles`（**不一致，U6**）。前端按更严的口径处理：**整个「自动派单」入口只对 ADMIN 渲染**，站长不显示。

### B3.5 PC 与移动端差异

| 项 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 新建工单 | ✅ 弹窗（含驿站/指派） | ❌（定位是督办，不新建，[boss/workorder.vue:17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L17) 已说明） | ✅ 已有（不含驿站/指派，由登录态决定） |
| 模拟派单 | ✅ 完整（规则表 + 模拟入口） | ⚠️ 可选：仅在确认演示需要时加一个「模拟派单」按钮，跳转到一个简化底部弹层（无规则表） | ❌ |
| 派单规则维护 | ✅ | ❌（PC 是配置场景） | ❌ |

**建议**：B3 的完整形态只做 PC；移动端**先不做**，等 PC 演示稳定后按需补简化版。理由：规则表在 375px 下必然退化成卡片堆叠，收益低于成本。

### B3.6 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`el-dialog`、`el-drawer`、`el-form`、`el-descriptions`、`el-timeline`；`WORK_ORDER_TYPE` / `WORK_ORDER_PRIORITY` / `WORK_ORDER_SLA_HOURS` 字典。
- **新增**：`--state-simulate-bg/fg/border`、字典 `WORK_ORDER_SOURCE`（`MANUAL` / `AUTO_WECHAT`）、`LOG_ACTION.auto_dispatch`。

---

## B4 通知中心 · 发布通知

### B4.1 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「包裹作业 → 通知中心」。页头 `actions` 新增 **[发布通知]（primary，仅 ADMIN 渲染）** |
| 老板端 | 「我的」→ 待办审批 分组 → **发布通知**（cell 入口，`/boss/notification/publish`）；或 `/boss/home` 宫格第 5 项 |
| 员工端 | ❌ 不可发布（契约 `roles:['ADMIN']`，[notification.js:121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L121)） |

### B4.2 信息架构：`PublishDrawer`（PC，宽 560px）

```text
① 通知类型（默认已选「系统公告」，其余三类也可选）
   el-radio-group：工单指派 / 工单流转 / 同步失败 / 系统公告
   —— 说明：默认 4（系统公告），因为手工发布场景就是公告；允许改是给「批量告知某类事务」留口
② 内容
   · 标题 *  （1-100，show-word-limit）
   · 正文 *  （1-500，textarea rows=5，show-word-limit）
③ 发布范围 *（三选一，分段控件）
   ○ 全员        → 显示「将发送给 56 名在职员工」
   ○ 指定驿站    → 驿站下拉 → 显示「将发送给 12 名在职员工」
   ○ 指定员工    → 多选（filterable，多选标签） → 显示「已选 3 人」
④ 发送预览（只读）
   接收人预览：前 3 名 + 「等共 N 人」；公告不落 bizType，点击不跳转（契约 bizType/bizId 为 null，[notification.js:106-107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L106-L107)）
Footer: [取消] [发布]
```

### B4.3 关键组件与状态

| 状态 | 表现 |
| ---- | ---- |
| 默认 | 范围默认「全员」，标题/正文空 |
| 加载 | 「发布」按钮 `loading` + 全表单 `disabled`；驿站下拉 `loading`（`GET /stations`）；员工多选 `loading`（`GET /employees?status=1`，**仅 ADMIN 可调**，[workOrder/index.vue:403-404](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L403-L404) 已说明该接口的 ADMIN 专属性质） |
| 空 | 员工多选搜不到人 → 下拉 footer「没有匹配的在职员工」；驿站列表为空 → 该选项禁用 + 说明 |
| 错误 | 抽屉内 `StateBlock` + 重试（驿站/员工列表加载失败时）；提交失败走 toast |
| 禁用 | 「发布」在 标题/正文为空 或 范围二级未选 时禁用，并给 `title` 说明缺哪一项 |
| 边界 | 全员 56 人时预览不展开列表，只给计数；正文 500 字上限有实时字数（`show-word-limit`） |

### B4.4 交互流程

**正常流**：页头「发布通知」→ 抽屉（类型=系统公告）→ 标题「9 月 20 日系统维护通知」→ 正文「…」→ 范围=指定驿站 → 城东驿站 → 预览显示「将发送给 12 名在职员工」→ 点「发布」→ 成功 toast「已发布给 12 名员工」→ 抽屉关闭 + 列表刷新。

**异常流**：

| 场景 | 服务端 | 前端 |
| ---- | ---- | ---- |
| 标题空/超 100 | `400 标题长度须为 1-100 字符`（[notification.js:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L76)） | 字段级校验 |
| 正文空/超 500 | `400 内容长度须为 1-500 字符`（[:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L77)） | 字段级校验 |
| 范围参数非法 / 指定员工为空数组 | `9002`（[:82,92,95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L82-L95)） | 提示「发布范围参数不合法：请选择至少一名在职员工」 |
| 指定员工全部为停用账号 | `9002`（[:95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L95)，收件人只取在职启用，[:83](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L83)） | 员工多选**只列在职启用员工**，从源头规避 |
| 指定驿站无在职员工 | `9002`（[:89](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L89) 只校验驿站存在；人数为 0 时服务端返回 `count:0`） | 发布前若预览显示 0 人 → 禁用「发布」并提示「该驿站暂无在职员工」 |

**二次确认点**（服务端返回 `count` 后）：**收件人 > 20 人时二次确认**——「本次将发布给 56 名员工，发布后不可撤回。确认发布？」。≤20 人直接执行（避免高频小范围通知每次都要确认）。

### B4.5 通知列表侧的配合改造

| 改造 | 说明 |
| ---- | ---- |
| 「公告」标识 | `isPublished === true` 的行，标题前加 `StatusTag`「公告」（outline） |
| 发布人 | 行尾补 `由 {publisherName} 发布`（caption） |
| 范围 | 详情/悬浮显示「范围：全员 / 城东驿站 / 指定 3 人」（`publishScope`） |
| 类型筛选 | 工具条加类型下拉（见 A8-3，依赖 R-2） |

### B4.6 PC 与移动端差异

| 项 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 发布 | ✅ 完整表单 | ✅ 简化：范围只留「全员 / 指定驿站」两项（去掉多选员工，移动端多选体验差），走全屏页面 `/boss/notification/publish`（**不用弹层**，表单在移动端全屏更稳） | ❌ |
| 收通知 | ✅ | ✅ | ✅（已有，需补「公告」标识与发布人，见 B4.5） |

### B4.7 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`el-drawer`、`el-form`；`NOTIFICATION_TYPE` 字典（[dict.js:80-85](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/dict.js#L80-L85)）。
- **新增**：字典 `PUBLISH_SCOPE`（`ALL` 全员 / `STATION` 指定驿站 / `EMPLOYEE` 指定员工），须与 [notification.js:68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L68) 的 `PUBLISH_SCOPES` 逐字一致。

---

## B5 考勤管理 · 导出

### B5.1 入口位置

PC 考勤页页头 `actions` 新增 **[导出]（default，图标 Download）**，位置在「刷新」左侧、「驿站选择」右侧。移动端不做（老板不会在手机上处理 CSV）。

### B5.2 交互：先确认范围，再导出

```text
ExportConfirmDialog（width 460px）
标题：导出考勤记录
① 导出范围（只读回显当前筛选，逐项列出）
   · 驿站：城东驿站
   · 日期：2026-08-21 ~ 2026-09-19
   · 员工：全部
   · 状态：全部
② 预计行数：「约 349 条」（取当前 total；若 total=0 则禁用导出）
③ 文件说明：「CSV（UTF-8 BOM，Excel 可直接打开）；字段 13 列，含 WiFi 与距离用于异常排查」
Footer: [取消] [导出 CSV]
```

**核心设计原则：导出结果必须与屏幕上的筛选完全一致**。因此导出按钮的实现必须是「把当前 `buildParams()` 原样传给 `/attendance/export`」，**禁止**导出时重新拼参数（历史上最容易出现的 bug 就是导出了全量而不是筛选结果）。

### B5.3 实现要点（技术对齐）

| 项 | 做法 | 依据 |
| ---- | ---- | ---- |
| 请求 | `request.get('/attendance/export', { params, responseType: 'blob' })` | Mock 适配器已支持 `responseType === 'blob'`（[engine.js:114-118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/engine.js#L114-L118)） |
| 落盘 | `saveResponseFile(response, '考勤记录_YYYYMMDD.csv')` | 一期工具 [download.js:6-33](file:///d:/Users/16626/Desktop/hrm-dev/hrm-admin/src/utils/download.js#L6-L33)，`@admin` 别名可直接引 |
| 文件名 | 优先用响应头 `content-disposition`（服务端已给 `考勤记录_{yyyyMMdd}.csv`，[attendance.js:355](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L355)），兜底用 `formatDateCompact()` | [download.js:35-41](file:///d:/Users/16626/Desktop/hrm-dev/hrm-admin/src/utils/download.js#L35-L41) |
| 列 | 13 列固定，由服务端生成（[attendance.js:332](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L332)）。**前端不要重新拼列**，否则与服务端漂移 | — |
| 错误 | blob 请求若返回 JSON 错误，`request.js` 已解析并统一报错（[request.js:83-95](file:///d:/Users/16626/Desktop/hrm-dev/hrm-admin/src/utils/request.js#L83-L95)）；`status` 非法时服务端回 400（[attendance.js:322](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L322)） | — |

### B5.4 组件状态

| 状态 | 表现 |
| ---- | ---- |
| 默认 | 按钮可用（`total > 0`） |
| 加载 | 按钮 `loading` + 文案「导出中…」；确认弹窗的「导出 CSV」按钮同样 `loading` |
| 空 | `total === 0` → 按钮 `disabled` + `title="当前筛选无数据可导出"`（与包裹页同款，[parcel/index.vue:347-350](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L347-L350)） |
| 错误 | 弹窗内提示 + toast；**不关闭弹窗**，允许重试 |
| 边界 | 行数极大（如 30 天全站 ≈ 数千条）：服务端一次性生成 CSV，前端无分页，需在弹窗里提示「数据量较大时生成可能需数秒」 |

### B5.5 需求 5 的落地顺序

1. `pc/api/attendance.js` 加 `exportAttendance(params)`（带 `responseType: 'blob'`，`silent: true` 以便弹窗内展示错误）。
2. 新建 `views/attendance/components/ExportConfirmDialog.vue`。
3. 页头加按钮 + 弹窗接线。
4. 核对 `GET /attendance/records` 与 `/attendance/export` 的**筛选参数一致性**（`records()` 有 `checkType` 未生效问题见 A9-2，导出接口同样未接 `checkType`，两边口径一致，可一起修）。

### B5.6 复用与新增 Token

- **复用**：`PageHeader`、`el-dialog`、`el-descriptions`；`@admin/utils/download.js`。
- **新增**：**无**。

---

## B6 工单文案统一为「超时未处理」

### B6.1 口径定义（必须先对齐，否则改了字还是旧逻辑）

> **超时未处理** = SLA 截止时间已过 **且** 工单仍处于「待处理(0)」或「处理中(1)」。
> 已解决(2) / 已关闭(3) **不计入**——终态工单继续计入会把「历史积压」误报成「待办超时」。
> 口径与旧字段 `overSla` 完全一致，本次只改**文案与字段名**，判定逻辑不变（[workOrder.js:18-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L18-L24)）。

### B6.2 全量替换清单（逐处列出，前端按表改）

**PC 端（6 处）**

| # | 文件:行 | 现文案 | 改为 | 备注 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | [workOrder/index.vue:333](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L333) | `{ name:'overSla', label:'超 SLA' }` | `{ name:'overdueUnhandled', label:'超时未处理' }` | Tab 内部键同步改；`:14` 的 `tab.name === 'overSla'` 判定同步改 |
| 2 | [workOrder/index.vue:57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L57) | `SLA 口径：低 48h / 中 24h / 高 8h · 超时仅高亮提醒，不自动改状态` | `SLA 口径：低 48h / 中 24h / 高 8h · 超时未处理 = 已过 SLA 且仍为待处理/处理中；仅高亮提醒，不自动改状态` | 文案已含「超时」，但缺「未处理」的判定说明；**这行是老板判断的唯一说明，必须补全** |
| 3 | [workOrder/index.vue:478](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L478) | `params.overSla = '1'` | `params.overdueUnhandled = '1'` | 契约已兼容旧名（[:92](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L92)），但新代码用新名 |
| 4 | [workOrder/index.vue:510](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L510) | `getWorkOrders({ ...base, overSla:'1', pageSize:1 })` | `overdueUnhandled: '1'` | Tab 计数 |
| 5 | [workOrder/index.vue:457-459,797-805](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue#L457-L459) | `row.overSla` / `is-oversla` 注释「超 SLA 行」 | `row.overdueUnhandled` / 注释改「超时未处理行」 | 类名 `is-oversla` 可保留（纯内部标识），但注释与字段名必须改；`:98` 的 `:finished` 判定不受影响 |
| 6 | [dashboard/index.vue:165,317,323,332](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue#L165-L332) | `超 SLA 工单` / `超 SLA 工单加载失败` / `超 SLA` / `有 N 条工单已超 SLA` | `超时未处理工单` / `超时未处理工单加载失败` / `超时未处理` / `有 N 条工单超时未处理` | `key:'overSlaCount'` 可保留（契约字段名），但 label 与 errorText 必改 |

**移动端（8 处）**

| # | 文件:行 | 现文案 | 改为 |
| ---- | ---- | ---- | ---- |
| 7 | [boss/home.vue:65](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L65) | `${overSla} 条工单超 SLA` | `${n} 条工单超时未处理` |
| 8 | [boss/home.vue:155](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L155) | `暂无超 SLA 工单与同步失败批次` | `暂无超时未处理工单与同步失败批次` |
| 9 | [boss/home.vue:161](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L161) | 注释「N 条工单超 SLA」 | 注释同步改 |
| 10 | [boss/alerts.vue:138](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L138) | 分组标题「超 SLA 工单」 | 「超时未处理工单」 |
| 11 | [boss/alerts.vue:145](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L145) | `暂无超 SLA 工单` | `暂无超时未处理工单` |
| 12 | [boss/alerts.vue:163](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L163) | `超 SLA 判定排除已解决与已关闭工单` | `超时未处理判定：已过 SLA 且仍为待处理/处理中` |
| 13 | [boss/alerts.vue:135](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue#L135) | 注释「分组三：超 SLA 工单」 | 注释同步改 |
| 14 | [boss/workorder.vue:26,53,184](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L26-L184) | Tab 名「超 SLA」/ 空态「没有超 SLA 的工单」/ tip「超 SLA 只统计待处理与处理中的工单…」 | 「超时未处理」/「没有超时未处理的工单」/「超时未处理只统计待处理与处理中的工单…」 |
| 15 | [staff/home.vue:134](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L134) | `本站有 N 条工单已超 SLA，请尽快处理` | `本站有 N 条工单超时未处理，请尽快处理` |
| 16 | [staff/workorder.vue:44,121](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue#L44-L121) | `没有超 SLA 的工单` / `仅看超 SLA` | `没有超时未处理的工单` / `仅看超时未处理` |

**其他（非界面但需同步，否则演示前后不一致）**

| # | 文件:行 | 处理 |
| ---- | ---- | ---- |
| 17 | [index.html:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/index.html#L110) | 演示顺序第 4 条「切『超 SLA』Tab」→「切『超时未处理』Tab」（A1-2 已提） |
| 18 | [SlaTag.vue:55,59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/SlaTag.vue#L55-L59) | `aria-label="已超 SLA"` / 读屏播报「工单已超 SLA」→ `已超时未处理` / `工单已超时未处理`。**无障碍文案也要一起改**，否则读屏用户听到的与视觉不符 |
| 19 | [SlaCountdown.vue:45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/SlaCountdown.vue#L45) | 播报「工单已超 SLA，…」→「工单已超时未处理，…」 |
| 20 | [pc/utils/format.js:62](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/utils/format.js#L62) | 注释「超 SLA 判定规则」→「超时未处理判定规则」 |
| 21 | [pc/styles/tokens.scss:112](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L112) | 注释「超 SLA 行整行底色」→ 同步改（变量名 `--state-danger-row-bg` 不变） |

> **Mock / 校验脚本 / 种子注释不改**：`verify-mock.mjs`、`db.js`、`scenario.js` 内的「超 SLA」属内部口径描述（[:429](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/scripts/verify-mock.mjs#L429) 已注明语义变更），本轮不动，避免触碰 shared 层。

### B6.3 验收口径

改完后 `grep -rn "超 ?SLA" src/ index.html` 应只剩：`shared/**`、`scripts/**`、`tokens.scss` 变量名相关注释（若保留）。**界面可见文案零命中**。

---

## B7 KPI 考核（并入员工管理模块）

### B7.1 契约缺口与建议数据结构

**契约不存在**（0.2 表），本节给出建议结构供后端/主智能体定稿。建议错误码段 **92xx**（90xx=通知、91xx=考勤，均已被占，依据 [errorCode.js:54-83](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/errorCode.js#L54-L83)）。

```
GET    /kpi/templates                 指标模板列表
GET    /kpi/templates/:id             模板详情（含指标项与权重）
POST   /kpi/templates                 新建模板
PUT    /kpi/templates/:id             编辑模板（含权重合计校验）
GET    /kpi/results                   考核结果（stationId/employeeId/period/month/pageNum/pageSize）
GET    /kpi/results/:id               结果详情（各指标得分明细）
POST   /kpi/results/generate          按周期生成结果（服务端按考勤+工单等已知数据自动算分）
GET    /kpi/my                        我的 KPI（移动端员工端）
```

建议错误码：

| 码 | 含义 |
| ---- | ---- |
| 9201 | 指标权重合计不为 100% |
| 9202 | 指标项数量不合法（1–10） |
| 9203 | 指标名称重复 |
| 9204 | 考核周期不存在或已锁定 |
| 9205 | 该周期已生成结果，不可重复生成 |
| 9206 | 指标模板被引用中，不可删除 |

### B7.2 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「组织人事 → **KPI 考核**」（新增菜单键 `kpi`），与「员工管理」同级。页内两个 Tab：**考核结果** / **指标模板** |
| PC（档案聚合） | 「组织人事 → 员工管理」旁新增 **员工档案** `/employee/detail/:id`（A11-1 的解法）；档案页的 KPI 区块内联展示 |
| 老板端 | `/boss/home` 宫格「KPI 考核」→ `/boss/kpi`（只读：全站结果 + 排名） |
| 员工端 | `/staff/home` 宫格「我的 KPI」→ `/staff/kpi`（只读：我的得分/达成率/排名/明细） |

### B7.3 信息架构（PC · 考核结果 Tab）

```text
① 周期与范围筛选
   考核周期（月度选择器） | 驿站（ADMIN 可切） | 员工搜索 | 排名方式（综合得分/单项指标）
② 汇总条（MiniStats，4 项）
   [参与考核 N 人] [平均得分 XX] [最高 XX] [待改进(<60) N 人]
③ 结果表（按得分降序）
   列：排名 | 员工 | 驿站 | 各指标得分（动态列，最多 5 个指标） | 综合得分 | 等级 | 操作(明细)
   —— 指标列数由模板决定，超过 5 个时表格横向滚动
④ 明细抽屉（点行打开）
   · 综合得分环形图（达成率）
   · 各指标：名称 | 权重 | 目标值 | 实际值 | 达成率 | 加权得分
   · 得分构成说明（口径来源，如「出勤率：考勤记录自动计算」）
```

### B7.4 指标模板 Tab · 权重配置与「合计不为 100%」的校验反馈

**这是需求 7 里最容易做砸的地方**，必须设计成「不可能存下错的权重」。

```text
指标模板编辑（PC 抽屉 width 640px）
① 模板信息：模板名称 | 适用角色（全站/站长/员工）| 考核周期（月/季）
② 指标项列表（可增删，1–10 项）
   每行：[拖拽手柄] 指标名称 | 数据来源(下拉) | 目标值 | 权重(%) | [删除]
   + 添加指标
③ 权重合计条（**常驻，不折叠**）
   ┌────────────────────────────────────────────┐
   │ 权重合计  95%   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░  [-5%] │  ← 不足：warning 色
   └────────────────────────────────────────────┘
   ┌────────────────────────────────────────────┐
   │ 权重合计 105%   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ [ +5%]│  ← 超出：danger 色
   └────────────────────────────────────────────┘
   ┌────────────────────────────────────────────┐
   │ 权重合计 100%   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ✓ 已平衡│ ← 达标：success 色
   └────────────────────────────────────────────┘
```

**校验反馈的设计规则（前端必须实现）**：

| 场景 | 反馈层级 | 具体表现 |
| ---- | ---- | ---- |
| 实时（每次改权重） | **进度条 + 数值 + 差额** | 三态配色见上；差额文案「还差 5%」/「超出 5%」，不给绝对值 |
| 输入非法（<0 或 >100） | 字段级 | 输入框下方红字「权重须为 0–100 的整数」 |
| 合计 ≠ 100% 时点保存 | **保存按钮禁用 + `title` 说明** | 按钮禁用并显示「权重合计需为 100%（当前 95%）」，**不允许提交**到服务端 |
| 服务端兜底 | toast | 若仍提交失败，`9201` → 「指标权重合计须为 100%，当前 95%」 |
| 权重全为 0 | 禁用 + 提示 | 「至少一项指标权重大于 0」 |
| 只有 1 项指标 | 自动 100% | 单指标时权重输入框置灰并固定 100%（避免用户去凑） |

**为什么不只是「提交时报错」**：考核模板是低频高风险配置（错了会导致一整月工资算错）。**必须让用户在改的时候就知道不平衡**，而不是等点保存才被拒。

**指标项可增删**：删除按钮带确认（「删除指标『客户满意度』后，已生成的历史结果不受影响，但未生成的周期将按新模板计算」）。拖拽排序用 `vuedraggable`？——**不引入新依赖**（demo-ui-redesign.md 0.1 冻结技术栈），改用「上移/下移」按钮。

### B7.5 自动算分的可视化（达成率 / 得分 / 排名）

| 可视化 | 形态 | Token |
| ---- | ---- | ---- |
| **达成率** | 手写 SVG 环形进度（两系列：达成/未达成），中心显示 `XX%` | 轨道 `--gauge-track`、进度 `--gauge-fill`（新增，见 C 章）；文字 `--fs-num-md` / `--fw-semibold` |
| **得分** | 大号数字 + 等级标签（优 ≥90 / 良 80–89 / 中 60–79 / 待改进 <60） | 数值 `--fs-num-lg`；等级标签复用 `StatusTag`（优=success soft / 良=primary soft / 中=warning soft / 待改进=danger soft） |
| **排名** | 徽标 + 分母（如 `#3 / 56`） | 前 3 名用 `--rank-1-bg` / `--rank-2-bg` / `--rank-3-bg`（**已有 Token**，[tokens.scss:121-126](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L121-L126)）；4+ 用 `--rank-rest-*` |
| **指标达成率条** | 横向进度条（宽度=达成率） | `--c-blue-500`；未达成（<100%）用 `--c-amber-500`；严重不足（<60%）用 `--c-red-500` |

**移动端降级**：员工端 `/staff/kpi` 不做表格，改为「Hero（我的综合得分 + 等级）+ 环形达成率 + 指标卡片列表（一项一卡：名称 / 权重 / 目标 / 实际 / 达成率条）」。老板端 `/boss/kpi` 为「汇总 4 卡 + 排名前 10 列表 + 点人进明细」。

### B7.6 关键组件与状态

| 组件 | 状态 |
| ---- | ---- |
| `KpiResultTable` | 加载：表头 + `v-loading`；空：「该周期暂无考核结果」+「生成本期考核」按钮（ADMIN）；错误：`StateBlock` + 重试；边界：指标列 >5 时横向滚动 + 首列固定（`fixed="left"`） |
| `KpiTemplateEditor` | 加载：抽屉打开时拉模板；错误：抽屉内错误态；禁用：模板已被引用且周期已锁定时只读；边界：10 项上限时「添加指标」禁用 + 说明 |
| `KpiGauge`（新建 SVG） | 空：无数据时显示灰环 + `—`；边界：>100% 时进度环封顶显示，但数字显示真实值（如 `112%`） |
| `KpiIndicatorCard`（移动端） | 加载：`.skeleton-block`；空：不适用（有结果必有指标）；错误：整页 `PageState` |

### B7.7 交互流程

**正常流（老板配置模板）**：KPI 考核 → 指标模板 Tab → 新建模板 → 填名称「一线员工月度考核」→ 添加 3 项指标（出勤率 40% / 工单及时率 40% / 客户满意度 20%）→ 权重合计条实时显示 100% 绿色 → 保存 → toast「模板已保存」→ 切到考核结果 Tab → 选周期 2026-08 → 「生成本期考核」→ 确认（`9205` 兜底防重复）→ 生成完成 → 表格按得分降序展示。

**异常流**：

| 场景 | 表现 |
| ---- | ---- |
| 权重合计 95% 点保存 | 保存按钮禁用 + `title`；若绕过前端，`9201` toast |
| 指标名重复 | 字段级「指标名称不可重复」（`9203`） |
| 该周期已生成结果再点生成 | 二次确认 → `9205` → toast「该周期已生成考核结果，如需重算请先作废」 |
| 员工无任何考勤记录 | 该员工不进入考核（结果表不出现），或出现但得分显示「无数据」；**必须二选一并写清**——建议：不出现 + 汇总条说明「N 人因无考勤数据未参与」 |
| 模板被引用时删除 | `9206` → 「该模板已被 2 期考核引用，不可删除，可停用」 |

**二次确认点**：生成本期考核、作废已生成结果、删除指标项、停用模板。

### B7.8 PC 与移动端差异

| 项 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 配置模板 | ✅ 完整（可增删指标、调权重） | ❌（配置场景留 PC） | ❌ |
| 查看全站结果 | ✅ 表格 + 明细抽屉 | ✅ 汇总 + 前 10 排名 | ❌ |
| 查看本人结果 | ✅ | ✅ | ✅ 我的 KPI（得分/等级/达成率/排名/指标明细） |
| 生成考核 | ✅ | ⚠️ 可选（月度一次，PC 更合适）→ 建议 ❌ | ❌ |

### B7.9 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`MetricCard`、`MiniStats`、`el-table`、`el-drawer`、`el-tabs`；`--rank-*`、`--state-*`、`--chart-inbound/pickup/abnormal`。
- **新增**：`--gauge-track` / `--gauge-fill` / `--gauge-size`（L3），见 C 章。

---

## B8 人事管理（员工工资设置等）

### B8.1 契约缺口与建议数据结构

契约不存在。建议错误码段 **93xx**。

```
GET  /hr/profiles                     员工档案列表（在职状态/岗位/职级/合同）
GET  /hr/profiles/:employeeId         档案详情
PUT  /hr/profiles/:employeeId         维护档案（岗位/职级/合同起止/转正日期）
GET  /hr/salary-standards             薪资标准列表（按岗位/职级）
POST /hr/salary-standards             新建薪资标准
PUT  /hr/salary-standards/:id         编辑
GET  /hr/salary/:employeeId           员工薪资档案（基本工资/绩效基数/补贴项/生效日期）
PUT  /hr/salary/:employeeId           调整薪资（生成调薪记录）
GET  /hr/salary/:employeeId/history   调薪记录
GET  /hr/positions                    岗位与职级字典
POST /hr/import                       批量导入（复用员工导入模板）
```

建议错误码：`9301` 岗位/职级不存在、`9302` 薪资标准重复（岗位+职级唯一）、`9303` 调薪生效日期早于当前生效日期、`9304` 档案不存在、`9305` 员工已离职不可调整薪资。

### B8.2 入口位置

| 端 | 位置 |
| ---- | ---- |
| PC | 侧边栏「组织人事 → **人事管理**」（菜单键 `hr`）。页内 4 个 Tab：**员工档案** / **薪资标准** / **调薪记录** / **岗位职级** |
| PC（交叉入口） | `/employee/detail/:id`（员工档案聚合页）内嵌「人事信息」与「当前薪资」两个区块 + 「调整薪资」按钮 |
| 老板端 | 「我的」→ 待办审批/我的数据 → **人事管理**（只读：员工档案查询） |
| 员工端 | 「我的」→ 我的数据 → **我的档案**（只读：岗位/职级/合同到期/当前薪资构成） |

### B8.3 信息架构（PC）

```text
Tab 1 员工档案
  筛选：在职状态 | 驿站 | 部门 | 岗位 | 合同到期（30 天内 / 已过期）
  表格：姓名 | 工号 | 驿站 | 部门 | 岗位 | 职级 | 入职日期 | 合同到期 | 状态 | 操作(详情)
  提示条：合同 30 天内到期 N 人 / 已过期 N 人  ← 人事最需要提前知道的事

Tab 2 薪资标准
  表格：岗位 | 职级 | 基本工资 | 绩效基数 | 生效日期 | 状态 | 操作
  —— 薪资标准是「模板」，员工实际薪资从模板派生后可单独调整

Tab 3 调薪记录
  表格：员工 | 调整前(基本/绩效) | 调整后 | 调整幅度 | 生效日期 | 操作人 | 原因 | 时间

Tab 4 岗位职级
  两列表格：岗位（名称/编码/所属序列）| 职级（名称/序号）
```

### B8.4 关键交互：薪资设置（需求 8 的核心）

**「员工薪资」不是一个输入框，而是「三要素」**：

```
员工薪资档案（PC 抽屉 width 640px）
① 基本信息（只读）：姓名 / 驿站 / 岗位 / 职级
② 薪资构成
   · 基本工资 *      （数字，元，≥0，整数）
   · 绩效基数 *      （数字，元，≥0，整数）  ← 与 B7 的 KPI 得分相乘得出绩效工资
   · 补贴项（可增删）：名称 | 金额 | 说明
   · 合计（实时计算，只读，加粗）：￥7,500
   —— 政策说明：此处的「合计」只是标准薪资，实发以财务模块的工资单为准（避免两处口径打架）
③ 生效设置
   · 生效日期 *  （默认下月 1 日；早于当前生效日期 → 9303）
   · 调整原因 *  （textarea 2-100 字，会写入调薪记录）
④ 历史（折叠，只读）：最近 3 条调薪记录
```

**薪资标准 vs 员工薪资的关系（必须画清楚，否则用户会两处都改）**：

```text
[薪资标准]  岗位=分拣员 + 职级=P2  →  基本 4500 / 绩效 800
        │  新员工入职时按此派生
        ▼
[员工薪资]  张三  基本 4500 / 绩效 800   ← 可单独调（如张三绩效基数调到 1000）
```

页面上必须有一行说明：「薪资标准是派生模板；修改标准**不会**自动改变已建档员工的薪资，需在员工薪资里单独调整」。这是人事系统最常见的误操作来源。

### B8.5 关键组件与状态

| 组件 | 状态 |
| ---- | ---- |
| `HrProfileTable` | 加载：表头 + `v-loading`；空：「暂无员工档案」+ 引导去员工管理建档；错误：`StateBlock`；边界：合同到期日为空（无固定期限合同）显示「无固定期限」 |
| `SalaryStandardTable` | 空：「暂无薪资标准，请先按岗位/职级建立标准」+ 「新建标准」按钮（**这是新系统的正确初始态**，必须有引导） |
| `SalaryEditorDrawer` | 加载：打开时拉当前薪资 + 历史；错误：抽屉内错误态；禁用：员工已离职 → 全表单只读 + 「已离职员工不可调整薪资」；边界：补贴项 0 项时合计 = 基本 + 绩效；金额输入限制为非负整数，超 6 位时提示 |
| `SalaryHistoryList` | 空：「暂无调薪记录，本次调整将是第一条」；时间线展示，节点色用 `--state-primary-*`（首次/普通调整）/ `--state-warning-*`（降薪）/ `--state-success-*`（涨薪） |

### B8.6 交互流程

**正常流（新员工薪资建档）**：人事管理 → 员工档案 → 找到新员工（状态=待入职，来自 B10 流程）→ 点「设置薪资」→ 抽屉默认按「岗位+职级」派生标准值 → 微调绩效基数 → 填生效日期（下月 1 日）+ 原因「新员工入职定薪」→ 保存 → toast「薪资已设置，2026-10-01 生效」+ 生成调薪记录。

**异常流**：

| 场景 | 表现 |
| ---- | ---- |
| 未选岗位/职级就设薪资 | 按钮禁用 + 「请先完善员工岗位与职级」 |
| 生效日期早于当前 | 字段级 + `9303` |
| 员工已离职 | 「调整薪资」按钮禁用 + `title`；绕过则 `9305` |
| 岗位+职级重复建标准 | 字段级 + `9302` |
| 合同 30 天内到期 | 档案列表行加 `StatusTag`「合同将至」（warning soft）+ 顶部提示条 |

**二次确认点**：降薪（调整后合计 < 调整前）必须二次确认——「本次为**降薪**调整（-￥500），确认提交？」；升薪不需确认。

### B8.7 PC 与移动端差异

| 项 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 员工档案 | ✅ 全量查询 + 维护 | ✅ 查询 + 详情（只读） | ✅ 仅本人（只读） |
| 薪资设置/调薪 | ✅ | ❌（涉及金额，PC 更稳；且老板在 B9 财务模块已能改规则） | ❌ |
| 薪资标准/岗位职级 | ✅ | ❌ | ❌ |
| 我的档案 | — | — | ✅ 岗位/职级/合同到期/**本人**薪资构成（只展示本人，接口必须按登录人收口） |

### B8.8 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`el-table`、`el-drawer`、`el-tabs`、`el-descriptions`、`el-timeline`；`--state-*` 四族。
- **新增**：`--drawer-w-lg: 720px`（薪资编辑表单字段较多，560 不够排两列），见 C 章。

---

## B9 财务管理（按考勤 + KPI 生成工资单，老板审核后发布，员工确认）

### B9.1 契约缺口与建议数据结构

契约不存在。建议错误码段 **94xx**。

```
# 计算规则（老板自行配置）
GET  /finance/payroll-rules                规则列表
GET  /finance/payroll-rules/:id            规则详情
POST /finance/payroll-rules                新建规则
PUT  /finance/payroll-rules/:id            编辑规则（含公式 JSON）
POST /finance/payroll-rules/:id/preview    按规则试算（入参：月份 + 驿站，返回每个员工的明细，不落库）

# 工资单
POST /finance/payrolls/generate            生成草稿（入参：month + stationIds）
GET  /finance/payrolls                     列表（status/month/stationId/keyword/pageNum/pageSize）
GET  /finance/payrolls/:id                 详情（含逐人明细与计算依据）
PUT  /finance/payrolls/:id/submit          提交审核（草稿 → 待审核）
PUT  /finance/payrolls/:id/approve         审核通过（待审核 → 已发布）→ 给员工推送通知
PUT  /finance/payrolls/:id/reject          驳回（待审核 → 草稿，需原因）
DELETE /finance/payrolls/:id               作废（仅草稿/待审核可作废）
GET  /finance/payrolls/my                  我的工资单（员工端）
PUT  /finance/payrolls/:id/confirm         员工确认
PUT  /finance/payrolls/:id/object          员工提异议（需原因）
PUT  /finance/payrolls/:id/resolve         老板处理异议（重新发布 or 驳回至草稿）
```

建议错误码：

| 码 | 含义 |
| ---- | ---- |
| 9401 | 该月份工资单已存在，不可重复生成 |
| 9402 | 计算规则不存在或未启用 |
| 9403 | 计算规则公式不合法 |
| 9404 | 工资单状态不允许该操作 |
| 9405 | 无该工资单的可见权限 |
| 9406 | 员工已确认，不可再修改 |
| 9407 | 工资单未发布，员工不可确认或提异议 |
| 9408 | 该月份存在未确认的考勤异常，无法生成 |

### B9.2 状态机（必须可视化的核心）

```text
                 ┌──────────── 作废（二次确认） ──────────────┐
                 ▼                                            │
  [草稿 DRAFT] ──提交审核──→ [待审核 PENDING] ──审核通过──→ [已发布 PUBLISHED]
       ▲                          │  ▲                            │
       │                          │  │                            ├──员工确认──→ [已确认 CONFIRMED]（终态）
       └──── 驳回（需原因）────────┘  │                            │
                                     │                            └──员工提异议─→ [有异议 OBJECTED]
                                     └── 重新生成 ───────────────────────┘  │
                                                                          ▼
                                                        [已确认] ←──重新发布/说明──┘
```

**状态标签映射（复用 `StatusTag`，不新增色族）**：

| 状态 | 文案 | 色族 | 形态 |
| ---- | ---- | ---- | ---- |
| `DRAFT` | 草稿 | neutral | outline |
| `PENDING` | 待审核 | warning | soft |
| `PUBLISHED` | 已发布 | primary | soft |
| `CONFIRMED` | 已确认 | success | soft |
| `OBJECTED` | 有异议 | danger | **solid**（需要立刻处理） |

### B9.3 页面结构（PC · 三个 Tab）

```text
Tab 1 工资单
  筛选：月份 | 驿站 | 状态 | 关键字（员工姓名）
  工具条：[生成工资单]（仅 ADMIN）  [批量导出]
  表格：单号 | 月份 | 驿站 | 人数 | 应发合计 | 实发合计 | 状态 | 生成时间 | 操作(详情/提交审核/审核)
  空态（首次）：「本月尚未生成工资单」+「生成工资单」引导 + 一行说明「生成前请确认当月考勤与 KPI 已完成」

Tab 2 计算规则（需求 9 的重点：老板自行配置）
  规则列表：规则名 | 适用岗位/驿站 | 关联数据源 | 状态 | 更新时间 | 操作
  [新建规则]

Tab 3 异议处理
  列表：员工 | 月份 | 异议原因 | 提出时间 | 状态 | 操作(处理)
```

### B9.4 计算规则的配置界面（需求 9 的核心，必须「老板自己会配」）

**设计原则：不写公式字符串，用「积木式」配置**。用户的真实心智是「基本工资 + 出勤天数折算 + 绩效工资 - 扣款 + 补贴」，而不是 `(base * attDays / shouldDays) + (perfBase * kpiScore / 100)`。

```text
PayrollRuleEditor（PC 抽屉 width 720px，用 --drawer-w-lg）

① 规则名称 *          「一线员工月度工资」
② 适用范围 *          岗位（多选）/ 驿站（多选，留空=全部）
③ 工资构成（可增删行，拖拽用上移/下移按钮）
   行 = 项目类型 + 数据来源 + 计算方式 + 预览值
   ┌────────────────────────────────────────────────────────────────────┐
   │ 1  基本工资     数据来源：员工薪资档案       固定值      ￥4,500      │
   │ 2  出勤折算     数据来源：考勤记录           按出勤比例  ￥4,500 × 22/22│
   │ 3  绩效工资     数据来源：KPI 得分           按得分比例  ￥800 × 92/100│
   │ 4  加班补贴     数据来源：考勤记录（加班时长）按小时计   12h × ￥30 = ￥360│
   │ 5  质量扣款     数据来源：工单超时未处理数    按次数扣   2 次 × -￥50 = -￥100│
   │ 6  其他补贴     手工录入（生成后可改）        固定值     ￥0          │
   └────────────────────────────────────────────────────────────────────┘
   + 添加项目（从「项目类型」下拉选）
④ 试算预览
   选择：月份 2026-08 | 驿站 城东
   [试算] 按钮 → 展示前 5 名员工的逐项拆解表（**不落库**）
   —— 这是本界面最重要的按钮：老板改完规则必须能立刻看到「张三会拿多少钱」
⑤ 汇总公式（只读）
   应发 = Σ(项目1..n 中正数项)；实发 = 应发 - Σ(扣款项)
   —— 展示为纯文本说明，不让用户编辑，避免出现无法解析的表达式
```

**「数据来源」的可选项必须与系统真实数据一一对应**（写死在 UI，不给自由输入）：

| 数据来源 | 取数口径 | 依赖 |
| ---- | ---- | ---- |
| 员工薪资档案 | B8 的 `基本工资` / `绩效基数` | B8 契约 |
| 考勤记录 | `应到 / 实到 / 迟到 / 早退 / 缺卡 / 加班时长` | 已有 `/attendance/summary`、`/attendance/records` |
| KPI 得分 | B7 的综合得分 | B7 契约 |
| 工单统计 | `超时未处理数` / `已解决数` | 已有 `/work-orders`（`overdueUnhandled`） |
| 手工录入 | 生成后逐人补 | 无需契约 |

**校验与反馈**：

| 场景 | 反馈 |
| ---- | ---- |
| 规则名为空 / 重名 | 字段级 |
| 未添加任何工资项目 | 提交按钮禁用 + 「至少添加一个工资项目」 |
| 添加了「绩效工资」但 B7 该月无考核结果 | **生成时**校验：`9408` 级别提示「2 名员工 2026-08 无 KPI 结果，绩效工资按 0 计算，确认继续？」 |
| 添加了「出勤折算」但该月考勤未闭环 | 生成前提示「该月存在 3 条考勤异常未处理，可能影响折算结果」+ 提供跳转 |
| 公式不合法（前端积木式结构不会产生，服务端兜底 `9403`） | toast |

### B9.5 工资单详情（生成结果 + 状态流转可视化）

```text
PayrollDetailDrawer（PC width 720px）

① 头部状态区（**状态流转可视化，需求 9 明确要求**）
   ┌──────────────────────────────────────────────────────────────┐
   │ ●───────●───────○───────○───────○                            │
   │ 草稿    待审核  已发布   已确认   完成                        │
   │ 09-19           (等待中)                                     │
   │ 09-18 10:00  已提交审核 · 王老板                              │
   └──────────────────────────────────────────────────────────────┘
   —— 用 el-steps（`active` 由状态决定）+ 已有时间线数据；每一步的时间与操作人写在下方时间线里

② 汇总（4 项）
   应发合计 / 扣款合计 / 实发合计 / 参与人数

③ 员工明细表
   列：员工 | 驿站 | 应发 | 扣款 | 实发 | KPI 得分 | 出勤 | 确认状态
   —— 「确认状态」列是需求 9 的闭环关键：已确认人数 / 未确认 / 有异议
   点员工行展开：逐项明细（项目名 | 计算式 | 金额）

④ 时间线（操作留痕）
   生成 → 提交审核 → 审核通过/驳回 → 员工确认/提异议 → 处理异议

⑤ Footer 操作（按状态动态）
   草稿：[作废] [提交审核]
   待审核：[驳回] [审核通过并发布]
   已发布：[查看确认进度] [催办未确认员工]
   有异议：[重新发布] [退回草稿]
```

### B9.6 关键组件与状态

| 组件 | 状态 |
| ---- | ---- |
| `PayrollTable` | 加载：表头 + `v-loading`；空：首次「本月尚未生成工资单」+ 生成引导；错误：`StateBlock`；边界：月份无在岗员工 → 生成时明确提示并禁用 |
| `PayrollRuleEditor` | 加载：打开时拉规则；错误：抽屉内；禁用：规则被引用的历史工资单存在时，「适用范围变更」需二次确认；边界：项目数量上限 20 |
| `PayrollStatusSteps`（新建） | 空：不适用；边界：出现「有异议」时第四步变 danger 色并显示「已提异议」；「作废」后整条置灰 |
| `PayrollDetailTable` | 空：不适用；边界：员工明细 >100 行时分页（`pageSize=50`） |
| 员工端 `MyPayrollCard` | 加载：骨架；空：「本月工资单尚未发布」；错误：`PageState`；边界：有异议时卡片顶部加 `van-notice-bar` 显示异议状态 |

### B9.7 交互流程

**正常流（完整闭环）**：

```text
老板：财务管理 → 工资单 → 生成工资单（选 2026-08 + 城东）→ 确认（提示「将按『一线员工月度工资』规则计算 12 人」）
  → 生成草稿（状态=草稿，可逐人手工调整「其他补贴」）
  → 提交审核（草稿 → 待审核）
  → 审核通过并发布（二次确认：「将发布给 12 名员工，发布后员工可见并需确认，不可撤回」）
  → 员工收到通知「您的 2026-08 工资单已发布，请确认」
员工：我的工资单 → 打开 → 看明细 → 点「确认无误」（二次确认）→ 状态=已确认
老板：工资单详情看到「已确认 10 / 12」，对未确认 2 人点「催办」
```

**异常流**：

| 场景 | 表现 |
| ---- | ---- |
| 同月重复生成 | `9401` → 「2026-08 的工资单已存在，请勿重复生成」+ 提供跳转现有单 |
| 规则未启用 | `9402` → 「未找到启用的计算规则，请先在『计算规则』里配置」 |
| 员工提异议 | 状态 → 有异议 + 员工端显示异议原因；老板端「异议处理」Tab 出现待处理项；老板可「重新发布」（回到已发布，需填说明）或「退回草稿」修改后重新走流程 |
| 未发布时员工访问 | `9407` → 员工端显示「工资单尚未发布」 |
| 已确认后再改 | `9406` → 「该员工已确认，如需调整请先退回草稿」 |
| 无该单权限（跨站） | `9405` → 「无权查看该工资单」 |
| KPI 数据缺失 | 生成时 `9408` 级提示（不阻断，给「继续生成（绩效按 0）」与「去补录」两个选择） |

**二次确认点（4 个）**：提交审核、审核通过并发布、作废工资单、员工确认/提异议。

### B9.8 PC 与移动端分工（需求 9 明确要求）

| 端 | 角色 | 做什么 |
| ---- | ---- | ---- |
| PC | ADMIN | 配置计算规则、生成草稿、批量调整、提交审核、审核发布、处理异议、导出 |
| 老板端 | ADMIN | **审核**：待审核列表 → 详情（含逐人明细）→ 通过/驳回（必须填原因）；查看确认进度、催办 |
| 员工端 | STAFF / STATION_ADMIN | **确认**：我的工资单列表 → 详情（逐项明细）→ 确认无误 / 提异议（必填原因）；查看历史工资单 |

**移动端的明细呈现**（375px 下的降级）：

```text
员工工资单详情（/staff/payroll/:id）
Hero：2026-08 工资单 · 实发 ￥5,860（大号数字）
      状态标签（已发布/已确认/有异议）
区块1 构成明细（cell 列表，一项一行）
  基本工资         ￥4,500
  出勤折算         ￥4,500
  绩效工资         ￥736       ← 点击展开「KPI 得分 92 × 绩效基数 800 ÷ 100」
  加班补贴         ￥360
  质量扣款        -￥100       ← 负数用 danger 色
  ───────────────
  实发合计         ￥9,996     ← 加粗 + 上边框
区块2 计算说明（折叠）：口径、数据来源、生成时间
ActionBar：[提异议]（次） [确认无误]（主）
```

### B9.9 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`MiniStats`、`ActionBar`（移动）、`el-table`、`el-drawer`、`el-steps`、`el-timeline`、`el-descriptions`；`--state-*` 四族。
- **新增**：`--drawer-w-lg: 720px`、`--step-*`（若需覆盖 `el-steps` 尺寸），见 C 章。

---

## B10 入离职流程

### B10.1 契约缺口与建议数据结构

契约不存在。建议错误码段 **95xx**。

```
# 流程模板（决定有哪些步骤、谁审批）
GET  /flows/templates                模板列表（ONBOARD / OFFBOARD）
POST /flows/templates                新建模板
PUT  /flows/templates/:id            编辑模板（步骤增删、责任人、是否必填）

# 流程实例
GET  /flows                           实例列表（type/status/stationId/keyword/pageNum/pageSize）
GET  /flows/:id                       实例详情（含步骤进度、每步经办人/时间/意见/附件）
POST /flows                           发起流程（入职/离职）
PUT  /flows/:id/steps/:stepNo/approve 步骤通过（可带意见）
PUT  /flows/:id/steps/:stepNo/reject  步骤驳回（必须原因）→ 回退到指定步骤
PUT  /flows/:id/cancel                撤销流程（仅发起人/ADMIN，且未完成）
GET  /flows/my                        我的流程（员工端）
```

建议错误码：`9501` 流程不存在、`9502` 当前步骤不允许该操作、`9503` 驳回原因为空、`9504` 流程已完成不可操作、`9505` 步骤责任人不在职、`9506` 存在未完成的离职流程，不可重复发起。

### B10.2 步骤设计（需求 10 的核心：步骤条 + 责任人 + 状态 + 驳回回退）

**入职流程（5 步）**

| 步 | 步骤名 | 责任人 | 产出/动作 | 是否可跳过 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | 提交入职资料 | 候选人 / 人事 | 身份证、学历、银行卡、紧急联系人 | 否 |
| 2 | 资料审核 | 人事（ADMIN） | 通过/驳回（驳回回步 1） | 否 |
| 3 | 开通账号与权限 | 人事（ADMIN） | 生成 `employee` 记录 + 角色 + 驿站归属 | 否 |
| 4 | 岗位与薪资确认 | 老板（ADMIN） | 关联 B8 的岗位/职级/薪资 | 否 |
| 5 | 入职培训与交接入岗 | 站长 | 确认已完成带教 | **可跳过**（小驿站无正式培训） |

**离职流程（5 步）**

| 步 | 步骤名 | 责任人 | 产出/动作 | 是否可跳过 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | 提出离职申请 | 员工本人 / 站长代提 | 离职原因、期望离职日 | 否 |
| 2 | 交接确认 | 站长 | 逐项确认交接（包裹/设备/钥匙/客户） | 否 |
| 3 | 财务结算 | 老板（ADMIN） | 关联 B9 生成末月工资单 | 否 |
| 4 | 账号与权限回收 | 人事（ADMIN） | 停用账号、关闭权限 | 否 |
| 5 | 离职归档 | 人事（ADMIN） | 归档档案、合同终止 | 否 |

### B10.3 步骤条视觉规范（Step）

```text
横向（PC）：
  ●━━━━━━●━━━━━━○━━━━━━○━━━━━━○
  1      2      3      4      5
 提交   资料   开通   薪资   培训
 资料   审核   账号   确认   入岗
 09-19  09-19  等待中
 10:00  11:30

  · 已完成：实心主色点 + 实线连接 + 主色文字
  · 进行中：实心主色点 + 右侧脉冲动画（120ms 缩放，尊重 prefers-reduced-motion）+ 加粗
  · 未开始：灰色空心点 + 灰线 + 三级文字
  · 已驳回：驳回步骤的点变 danger 实心 + 步骤名后挂「已驳回」标签；后续步骤全部置灰
  · 已跳过：灰色点 + 「已跳过」标签
```

```text
纵向（移动端）：复用 timeline__* 结构（mobile.scss:376-415 已有 .timeline 类）
  ● 提交入职资料        已完成 · 09-19 10:00
  │   经办：张三（本人）
  ● 资料审核            已完成 · 09-19 11:30
  │   经办：人事 · 意见「材料齐全」
  ● 开通账号与权限      进行中
  │   责任：人事
  ○ 岗位与薪资确认      等待中
  ○ 入职培训与交接入岗  等待中
```

**Step 组件的 Token 映射**：完成态 `--color-primary-icon`、驳回态 `--color-danger`、未开始 `--c-neutral-300`、连接线 `--border-line`（完成段用 `--color-primary-icon`）。新增尺寸 Token 见 C 章。

### B10.4 页面结构

```text
PC：组织人事 → 入离职（菜单键 onboard）
Tab 1 流程实例
  筛选：类型(入职/离职) | 状态(进行中/已完成/已驳回/已撤销) | 驿站 | 关键字
  表格：流程号 | 类型 | 员工 | 驿站 | 当前步骤 | 责任人 | 状态 | 发起时间 | 操作(详情)
  [发起入职流程] [发起离职流程]（仅 ADMIN）
Tab 2 流程模板
  两个模板卡片（入职/离职），点开编辑步骤（步骤名/责任人角色/是否必填/是否可跳过）

详情抽屉：
 ① 步骤条（横向）
 ② 员工信息卡（入职：候选人信息；离职：员工信息 + 在职时长）
 ③ 当前步骤待办区（**最重要**）：待办动作 + 意见输入 + [通过] [驳回]
 ④ 步骤记录时间线（经办人 / 时间 / 意见 / 附件）
 ⑤ Footer：[撤销流程]（未完成时）
```

### B10.5 驳回与回退的处理（明确规则，避免实现时歧义）

| 规则 | 约定 |
| ---- | ---- |
| 驳回目标步骤 | **默认退回上一已完成步骤**，但允许驳回人**下拉选择**退回到任意前置步骤（如「资料审核」可退到步骤 1；若步骤 3 才发现问题，也可一次退到步骤 1） |
| 驳回必填 | 驳回原因必填（2–100 字），写入步骤记录 |
| 驳回后的状态 | 流程状态仍为「进行中」，但被驳回的步骤打「已驳回」标记；**后续已完成步骤的记录保留**（不回滚数据，只回退进度） |
| 数据回滚 | **不做自动数据回滚**。如离职步骤 3（财务结算）已生成末月工资单，之后被驳回——工资单不自动作废，需人工在 B9 处理。界面上必须提示：「驳回不会撤销已产生的数据（如工资单），请手动核对」 |
| 重复发起 | 同一员工已存在未完成的同类流程时，`9506` 拦截 |
| 责任人离职 | `9505` → 提示「步骤责任人已离职，请联系管理员改派」+ 提供「改派」动作（ADMIN） |
| 撤销 | 仅发起人/ADMIN 且流程未完成；撤销后状态=已撤销，步骤条整体置灰 |

### B10.6 关键组件与状态

| 组件 | 状态 |
| ---- | ---- |
| `FlowSteps`（新建） | 空：不适用；边界：步骤 >6 个时横向滚动；移动端自动切纵向 |
| `FlowTable` | 加载：表头 + `v-loading`；空：「暂无进行中的入离职流程」+ 发起引导；错误：`StateBlock`；边界：「当前步骤」列在已驳回时显示「{步骤名}（已驳回）」 |
| `FlowActionPanel`（当前步骤待办） | 加载：通过/驳回按钮 `loading`；禁用：当前用户非责任人且非 ADMIN → 面板只读 + 「该步骤由 {责任人} 处理」；错误：`9502` → 「当前步骤不允许该操作，请刷新查看最新状态」 |
| `RejectDialog`（新建） | 驳回原因必填（textarea 2–100）+ 退回步骤下拉；空原因时禁用提交 |

### B10.7 交互流程

**正常流（离职）**：员工端「我的」→ 我的流程 → 发起离职 → 填原因与期望日期 → 提交 → 站长收到通知 → 站长在 PC/老板端打开流程 → 勾选交接项 → 通过 → 财务结算步骤给老板 → 老板确认末月工资单已生成 → 通过 → 人事回收账号 → 通过 → 归档 → 流程完成 → 员工端收到「离职流程已完成」通知，账号在下次登录时提示已停用。

**异常流**：

| 场景 | 表现 |
| ---- | ---- |
| 非责任人尝试处理 | 面板只读 + 一行说明（不渲染按钮） |
| 步骤 2 驳回退到步骤 1 | 步骤 1 重新变为待办，责任人收到通知；§B10.5 的「不回滚数据」提示出现 |
| 重复发起离职 | `9506` |
| 流程完成后再操作 | `9504` → 「流程已完成，不可再操作」 |
| 驳回原因为空 | 前端禁用提交；服务端 `9503` 兜底 |

**二次确认点（3 个）**：撤销流程、驳回（因涉及进度回退）、离职「账号与权限回收」步骤通过（**不可逆**——「通过后该账号将立即停用，员工将无法登录，确认？」）。

### B10.8 PC 与移动端差异

| 项 | PC | 老板端 | 员工端 |
| ---- | ---- | ---- | ---- |
| 查看流程列表 | ✅ 全量 | ✅ 待我审批（按责任人过滤） | ✅ 仅本人 |
| 发起流程 | ✅（代发起） | ⚠️ 可发起离职（口头提出场景常见）→ 建议 ✅ | ✅ 发起离职 |
| 步骤审批 | ✅ 全步骤 | ✅ **仅老板为责任人的步骤**（财务结算 / 薪资确认） | ✅ 仅员工为责任人的步骤（提交资料、确认交接） |
| 流程模板配置 | ✅ | ❌ | ❌ |

### B10.9 复用与新增 Token

- **复用**：`StatusTag`、`StateBlock`、`PageHeader`、`ActionBar`（移动）、`el-steps`、`el-timeline`、`el-drawer`、`el-table`；移动端 `.timeline__*` 类（[mobile.scss:376-415](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L376-L415)）。
- **新增**：`--step-dot` / `--step-line` / `--step-dot-done`（L3，覆盖 `el-steps` 尺寸），见 C 章。

---

# C. Token 与组件规范增量

## C1 品牌色使用边界（硬规则，违反即为缺陷）

沿用 demo-ui-redesign.md 2.3 的既有规则（本轮**重申并扩展适用面**，因为新增了 5 个模块的标签与图表）：

| 色 | 对比度（白底/白字） | **允许**用于 | **禁止**用于 |
| ---- | ---- | ---- | ---- |
| `#1890FF`（`--c-blue-500`） | 白底 3.24:1 | 图标、图表线、进度条、1px 强调描边、≥24px 大号数字、浅底上的大面积色块 | ❌ 14px 及以下文字；❌ **承载白字的实底** |
| `#0958D9`（`--c-blue-700` / `--color-primary`） | 白字 **6.16:1** | **承载白字的实底**：主按钮、NavBar、Tabbar 选中、选中 chip、Hero 渐变深端、步骤条完成态 | 大面积填充（视觉过重） |
| `#6B7280`（`--c-neutral-500` / `--text-3`） | 白底 **4.83:1** | 辅助文字：口径说明、时间、元信息、placeholder 之外的三级文本 | 一级/二级正文 |
| `#FA8C16`（`--c-orange-500`） | 白底 2.83:1 | 仅图标/线/图表系列（物流语义） | ❌ 任何文字；❌ 白字实底 |

**严禁**：
1. **紫色系**（AI 默认风）——本轮所有新增页面的 Token 取值不得出现 `--c-*` 色板之外的颜色，色板已定义于 [pc/tokens.scss:10-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L10-L67) 与 [mobile/tokens.scss:13-68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L13-L68)。
2. 新增页面出现**十六进制色值**（业务 `<style>` 与模板内联属性均不可）——沿用 9.2 验收项。
3. 引入 Token 色板外的第 5 种状态色——本轮 5 个新模块的**全部状态**都能映射到既有 4 族（见 C2），无需新色族。

## C2 新增业务状态 → 既有 Token 映射（本轮唯一需要的「组件规范」核心）

这是把新增模块接进既有设计系统的关键：**不新增色族，只做语义 → 变量的映射**，与 [StatusTag.vue:20-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/StatusTag.vue#L20-L22) 的既有做法一致。

| 业务 | 状态 | `type` 语义 | variant | 取用 Token |
| ---- | ---- | ---- | ---- | ---- |
| 采集状态 | `NORMAL` 正常 | success | soft | `--state-success-*` |
| 采集状态 | `ABNORMAL` 异常 | danger | **solid** | `--state-danger-fg` |
| 采集状态 | `UNCONFIGURED` 未配置 | warning | soft | `--state-warning-*` |
| 采集状态 | `DISABLED` 已停用 | info | outline | `--state-outline-*` |
| 工单来源 | `MANUAL` 手工 | info | outline | `--state-outline-*` |
| 工单来源 | `AUTO_WECHAT` 企微自动 | primary | soft | `--state-primary-*` |
| 工资单 | `DRAFT` 草稿 | info | outline | `--state-outline-*` |
| 工资单 | `PENDING` 待审核 | warning | soft | `--state-warning-*` |
| 工资单 | `PUBLISHED` 已发布 | primary | soft | `--state-primary-*` |
| 工资单 | `CONFIRMED` 已确认 | success | soft | `--state-success-*` |
| 工资单 | `OBJECTED` 有异议 | danger | **solid** | `--state-danger-fg` |
| KPI 等级 | 优 / 良 / 中 / 待改进 | success / primary / warning / danger | soft | 对应 `--state-*-bg/fg/border` |
| 入离职 | 进行中 | primary | soft | `--state-primary-*` |
| 入离职 | 已完成 | success | soft | `--state-success-*` |
| 入离职 | 已驳回 | danger | soft | `--state-danger-*` |
| 入离职 | 已撤销 / 已跳过 | info | outline | `--state-outline-*` |
| 合同预警 | 合同将至（≤30 天） | warning | soft | `--state-warning-*` |
| 合同预警 | 已过期 | danger | soft | `--state-danger-*` |
| 演示/模拟区 | 模拟演示标识 | warning（专用变量） | outline | `--state-simulate-*`（新增） |

## C3 新增 Token 清单（4 组，逐项给理由）

> 新增原则：**只增「既有体系无法表达」的项**。凡能靠 L2 既有变量组合表达的，一律不加。

### C3-1 `--state-simulate-*`（L2 Semantic，PC + 移动各加 3 条）

| Token | PC 取值 | 移动取值 | 理由 |
| ---- | ---- | ---- | ---- |
| `--state-simulate-bg` | `var(--c-amber-50)` | `var(--c-yellow-50)` | 企微派单模拟区、Demo 专用区需要**整片可识别**的底色，与「错误/危险」区分 |
| `--state-simulate-fg` | `var(--c-amber-700)` | `var(--c-yellow-700)` | 文字/图标色 |
| `--state-simulate-border` | `var(--c-amber-200)` | `var(--c-yellow-100)` | 描边色 |

**为什么必须新增而不是直接用 `--state-warning-*`**：两者语义不同——`warning` 表示「系统/业务有风险」，`simulate` 表示「这块是演示数据，不是真实功能」。若复用同一组变量，将来把 warning 调深（如为了突出超时工单）会连带把「模拟演示」区一起变重，两个语义被绑死。**注意**：两端 L1 的橙色系命名不同（PC 是 `--c-amber-*`，移动是 `--c-yellow-*`），但**取值逐档相同**（如 `#FFFBE6` 在 PC 为 `--c-amber-50`、移动为 `--c-yellow-50`），故 L2 映射表达式按端各写一份。

### C3-2 `--gauge-*`（L3 Component，KPI 达成率环形图）

| Token | PC 值 | 移动值 | 理由 |
| ---- | ---- | ---- | ---- |
| `--gauge-size` | `112px` | `80px` | 环形直径分端定义（PC 明细抽屉 vs 移动卡片） |
| `--gauge-stroke` | `10px` | `8px` | 环宽，8px 网格对齐 |
| `--gauge-track` | `var(--c-neutral-100)` | `var(--c-neutral-100)` | 轨道色（未达成部分） |
| `--gauge-fill` | `var(--color-primary)` | `var(--color-primary)` | 进度色（取 700 档：500 档对 `--gauge-track` 实测仅 **2.839:1**，不达 SC 1.4.11 的 3:1；700 档为 **5.388:1**。环形进度属非文本内容、不承载白字，按对比度取值而非 500/700 分工规则） |

**为什么新增**：既有 Token 无环形图相关尺寸；`TrendChart` / `LineChart` 的尺寸是各自组件内的私有值，KPI 环要复用到 PC 与移动两处，必须有共享 Token（避免出现第三份私有尺寸）。

### C3-3 `--step-*`（L3 Component，入离职步骤条）

| Token | PC 值 | 移动值 | 理由 |
| ---- | ---- | ---- | ---- |
| `--step-dot` | `24px` | `16px` | 步骤点直径；PC 横向 5 步需容纳副标题 |
| `--step-line` | `2px` | `2px` | 连接线宽 |
| `--step-gap` | `var(--sp-4)` | `var(--sp-3)` | 步骤间距 |

颜色不新增 Token：完成/进行中 → `--color-primary-icon`，驳回 → `--color-danger`，未开始 → `--c-neutral-300`，连接线 → `--border-line`。

### C3-4 `--drawer-w-lg`（L3 Component，PC）

| Token | 值 | 理由 |
| ---- | ---- | ---- |
| `--drawer-w-lg` | `720px` | 工资单规则配置（8 列积木行）、薪资编辑（基本+绩效+补贴多列）、派单规则表需要更宽；既有 `--drawer-w: 520px`（[pc/tokens.scss:197](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L197)）不足 |

**用量约束**：全站仅允许 B3 的 `AutoDispatchDrawer`、B8 的 `SalaryEditorDrawer`、B9 的 `PayrollRuleEditor` / `PayrollDetailDrawer` 四处使用；仍须按既有惯例叠加窄屏退化：`min(var(--drawer-w-lg), 92vw)`（[parcel/index.vue:191](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue#L191) 的写法）。

### C3-5 不需要新增 Token 的项（明确登记，防止实现时「顺手加一个」）

| 需求项 | 为什么不需要新 Token |
| ---- | ---- |
| B1 采集状态四态 | 全部映射到 `--state-*` 四族（C2） |
| B2 排班批量工具 | 纯交互，无新视觉元素 |
| B3 新建工单 | 全部复用 `el-dialog` / `el-form` / `StatusTag` |
| B4 发布通知 | 全部复用 `el-drawer` / `el-form` / `StatusTag` |
| B5 考勤导出 | 纯交互 |
| B6 文案 | 无视觉变更 |
| B7 KPI 得分/排名 | 排名复用 `--rank-*`（[pc/tokens.scss:121-126](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L121-L126)）；等级复用 `--state-*` |
| B8 薪资 | 纯表单 |
| B9 工资单状态 | 5 态全部映射到 `--state-*` 四族（C2） |
| B10 入离职状态 | 4 态全部映射到 `--state-*` 四族（C2）；步骤条颜色复用语义色，只有尺寸新增（C3-3） |

**结论：本轮新增 Token 共 11 个名称 —— PC 端 11 条，移动端 10 条（不含 `--drawer-w-lg`）。** 明细：`--state-simulate-*` 3 + `--gauge-*` 4 + `--step-*` 3 + `--drawer-w-lg` 1。

## C4 新增组件清单与规范（Atomic Design 定位）

| 层级 | 组件 | 端 | anatomy | variants | states（7 态） | Token 映射 | 无障碍要求 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| Atom | `CollectStateTag` | 共用（复用 StatusTag） | 单标签 | soft / solid / outline | 由 dict 驱动，本身无需状态 | `--state-*` | 文字表达语义（不靠颜色单一通道） |
| Atom | `KpiGauge` | PC + 移动 | 环 + 中心数值 | 达成 / 未达成 / 无数据 | 空（灰环 `—`）/ 边界（>100% 封顶） | `--gauge-*` | `role="img"` + `aria-label="达成率 XX%"`；中心数值同时可读 |
| Molecule | `CollectStateBoard` | PC | 4 × 计数卡 | — | 加载 / 错误 / 边界（0 值） | `--state-*` | 计数卡为纯展示，不需要键盘交互 |
| Molecule | `BatchToolsMenu` | PC | 下拉按钮 + 菜单项 | 默认 / 仅批量 / 含危险项 | 禁用（只读角色）/ 加载 | `--color-primary-*` | 键盘可达（`el-dropdown` 原生）；危险项用 `--color-danger` 文字 |
| Molecule | `CollectConfigDrawer` | PC | 表单抽屉 | 可写 / 只读 | 全 7 态 | `--state-*`、`--drawer-w` | 字段级错误用 `el-form-item` 原生 `role`；焦点自动落到首个字段 |
| Molecule | `CreateWorkOrderDialog` | PC | 弹窗表单 | — | 全 7 态 | 复用 | `el-dialog` 原生焦点陷阱；Esc 关闭 |
| Molecule | `PublishDrawer` | PC | 抽屉表单 | 范围三分支 | 全 7 态 | 复用 | 同 CollectConfigDrawer |
| Molecule | `ExportConfirmDialog` | PC | 确认弹窗 | — | 默认 / 加载 / 空（禁用）/ 错误 | 复用 | 「导出 CSV」按钮 `loading` 时禁点 |
| Molecule | `BatchSpreadDialog` | PC | 表单弹窗 | 一键铺排 / 复制上一周 | 全 7 态 | 复用 | 预览区用 `aria-live="polite"` 播报格数变化 |
| Molecule | `PayrollStatusSteps` / `FlowSteps` | PC | 步骤条 | 横向 / 纵向（移动） | 空 / 边界（>6 步滚动） | `--step-*`、`--color-primary-icon`、`--color-danger` | `role="list"`；当前步骤 `aria-current="step"`；驳回状态用文字标签表达（不只靠颜色） |
| Organism | `CollectConfigTable` | PC | 表格 | 可写 / 只读 | 全 7 态 | `--state-*` | 表头 `scope`；行操作有 `aria-label` |
| Organism | `KpiResultTable` | PC | 表格 | — | 全 7 态 | 复用 `--rank-*` | 得分列 `tabular-nums`；等级标签带文字 |
| Organism | `PayrollDetailTable` | PC | 表格 + 展开行 | — | 全 7 态 | 复用 | 展开行 `aria-expanded` |
| Organism | `MyPayrollCard` | 移动 | 卡片 + 明细 cell | — | 全 7 态 | `--state-*`、`--color-danger`（负数金额） | 金额 `tabular-nums`；负数不只靠红色（带 `-` 号） |
| Organism | `KpiIndicatorCard` | 移动 | 卡片 + 进度条 | — | 全 7 态 | `--gauge-*` | 达成率条附文字百分比 |

**通用要求**：所有新增组件的 `<style>` **不得出现十六进制色值**（9.2 验收项）；所有列表项/按钮热区 ≥44×44px；所有交互元素有 `:focus-visible` 焦点环（PC 已由 [element-overrides.scss:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/element-overrides.scss#L76) 统一提供，移动端由 [mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38) 提供）。

## C5 响应式适配（新增页面的断点行为）

沿用既有断点定义（xs <768 / sm 768–992 / md 992–1200 / lg 1200–1600），逐断点约定：

| 断点 | PC 新增页面行为 | 移动端新增页面行为 |
| ---- | ---- | ---- |
| lg ≥1200 | 侧边栏展开（210px）；抽屉 `--drawer-w-lg` 720px；表格全列展示 | — |
| md 992–1200 | 侧边栏**自动折叠**（[layout/index.vue:99-105](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L99-L105)）；抽屉退化为 `min(720px, 92vw)`；`el-descriptions` 由 2 列降 1 列（见 A5-3）；KPI 指标列改为横向滚动 | — |
| sm 768–992 | 同上 + 筛选卡控件换行（`el-form inline` 自动换行）；`MiniStats` 由 6 列降 3 列 | — |
| xs <768 | PC 页面不作为目标形态（MPA 已隔离，`pc.html` 在小屏可用但非验收项） | 新增移动页面的**唯一目标断点**：375–430px。单列卡片；列表不转表格；操作走 `ActionBar` 固定底栏；角度表（KPI/工资单/排班）不用矩阵，改用卡片列表 |

**横向兜底**：移动端 `#app` 在 `orientation: landscape` 下 `max-width: 640px` 居中（[mobile.scss:418-423](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L418-L423)）；新增的工资单明细、KPI 明细在本横屏下也须保持单列（不因变宽而变成两列，避免与手机纵向习惯冲突）。

## C6 无障碍验收清单（新增内容专项）

| 检查项 | 标准 | 本轮新增内容的验收点 |
| ---- | ---- | ---- |
| 文字对比度 | WCAG SC 1.4.3 ≥ 4.5:1 | 新增标签文字全部走 `--state-*-fg`（均 ≥4.5:1）；金额负数用 `--color-danger`（`#CF1322`，5.57:1，**不用** `#FF4D4F`） |
| 非文本对比度 | SC 1.4.11 ≥ 3:1 | 新增表单控件描边用 `--border-control`（`#8A93A0`，3.10:1）；KPI 进度条 `--gauge-fill` vs `--gauge-track` 已实测收口：500 档 `#1890FF` 为 **2.839:1**（原文记「约 3.1:1 临界」偏乐观，实际不达标），改 700 档 `#0958D9` 后为 **5.388:1**，达标（见 C3-2） |
| 状态不只靠颜色 | SC 1.4.1 | 所有新增状态标签**均带文字**；步骤条驳回态带「已驳回」文字；负数额外带 `-` 号；模拟区带「模拟演示」文字标签 |
| 键盘可达 | SC 2.1.1 | 新增表格的展开行、下拉、抽屉均用 Element 原生组件（自带键盘支持）；批量工具的行内/列头小按钮必须用 `<button>` 且 `tabindex` 可达 |
| 焦点可见 | SC 2.4.7 | 复用两端既有统一焦点环；新增表格内的内联按钮需确认未被 `overflow:hidden` 裁掉焦点环（**待走查**） |
| 触控目标 | SC 2.5.5 (44) / 2.5.8 (24) | 新增移动端元素全部 ≥44px（`ActionBar` 按钮 `min-height:44px` 已有，[ActionBar.vue:110-113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/ActionBar.vue#L110-L113)）；PC 端表格内联按钮沿用既有 `link` 形态，属于鼠标主场景，按 SC 2.5.8 的 24px 判定 |
| 动效降级 | SC 2.3.3 | 步骤条「进行中」脉冲动画必须被两端既有的 `prefers-reduced-motion` 媒体查询覆盖（[pc/tokens.scss:302-311](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L302-L311)、[mobile/tokens.scss:281-290](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L281-L290) 已全局处理，新动画只要用 `animation` 属性即自动生效） |
| 图表替代文本 | SC 1.1.1 | `KpiGauge` 必须补 `role="img"` + `aria-label`（移动端 `LineChart` 曾漏此属性，见 demo-ui-redesign.md P33，**新组件不得重犯**） |

## C7 交付前自检（本轮规范自身的检查结果）

| 检查项 | 结果 |
| ---- | ---- |
| 设计方向有业务依据、非 AI 默认风 | ✅ 全部新功能沿用既有蓝/橙物流语义 + 深蓝灰，无紫色，无新色族 |
| 3 层 Token 结构完整、组件正确映射到 Token | ✅ C2 给出 19 条业务状态 → 既有 Token 的映射；C3 仅新增 11 个 Token 名称，逐条给理由 |
| 全部组件状态无遗漏 | ✅ B0.2 定义 7 态最小集；B 章每个组件逐个列出状态要求 |
| 无障碍（对比度/触控/键盘）达标 | ✅ C6 逐项列出；KPI 进度条对比度已按实测关闭（改 700 档，5.388:1）；余 1 处待走查（表格内联按钮焦点环裁剪） |
| 断点覆盖 H5/平板/桌面 | ✅ C5 逐断点约定 |
| 可落地、无空话 | ✅ A 章 66 条每条带 `文件:行号`；B 章每项带阶段文件清单与契约出处 |
| 文档与实现一致性风险已登记 | ✅ A2-1（登录页一键填充的文档-实现偏差）、B6-2（文案全量清单）、U1–U6 六项不确定项 |


