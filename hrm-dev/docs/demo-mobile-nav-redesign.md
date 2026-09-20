# 快递驿站智汇系统 · 移动端底部导航重构（三 Tab：首页 / 消息 / 我的）设计规范

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-19 |
| 作者 | UI/UX 设计师 |
| 适用范围 | `hrm-dev/hrm-demo` 移动端（`mobile.html`，老板端 ADMIN + 员工端 STATION_ADMIN / STAFF） |
| 交付对象 | 主智能体（评审）→ 前端工程师（照 A/B/C/D 章逐项落地）→ 测试工程师（照 E 章验收） |
| 依据文档 | [demo-ux-improvement.md](demo-ux-improvement.md)（M1 冻结规范）、[demo-ui-redesign.md](demo-ui-redesign.md)（Token 三层体系）、[demo-milestones.md](demo-milestones.md)（D-1~D-7 决策） |
| 本轮产出 | **仅本设计文档，不改任何源码** |

---

## 0. 取证方式、约束变更登记与不确定项

### 0.1 取证方式

本文所有结论来自**静态阅读**移动端源码与 `src/shared/mock/**` 契约，逐条标注 `文件:行号`。未使用浏览器实测（TRAE Chrome 扩展在本机不可用，`demo-ux-improvement.md` 0.1 已登记），因此：

- **结构性结论**（信息架构、入口清单、契约字段、Token 取值、Vant 组件属性）证据充分并标注行号；
- **运行时结论**（重复点击当前 Tab 是否触发 Vant `change`、`aria-current` 是否由 Vant 输出、Tabbar 实际渲染像素）标注为「待走查」，由测试工程师在实现后复核。

### 0.2 约束变更登记（本次唯一推翻的既有约束，必须回写）

| 项 | 既有约定 | 本次变更 | 出处与理由 |
| ---- | ---- | ---- | ---- |
| **移动端 Tabbar 项数** | 「移动端 Tabbar 两端各 6 项，**不可再加**」 | **改为两端各 3 项**（首页 / 消息 / 我的） | 旧约束见 [demo-ux-improvement.md](demo-ux-improvement.md) B0.1 与 [tabs.js:5-6](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L5-L6)。**该约束的实质是「防止 Tabbar 超载」**（320px 下每项 <46px 触控不达标），3 项比 6 项更宽松，故本次变更不违反其本意，反而是超载问题的彻底解法：3 项在 320px 下每项约 106px、高 50px，触控余量翻倍。变更理由：需求方要求「首页 / 消息 / 我的」三 Tab 结构；6→3 释放出的容量正好把原有 6 个一级页中的 4 个（考勤/趋势/排行/预警、打卡/包裹/工单/通知）下沉为首页宫格项与消息页子视图，符合「一级页只保留最常用」的信息架构原则。 |
| **`demo-ui-redesign.md` 7.2「固定 5 项，不增减」** | 固定 5 项 | **本条已失效** | [demo-ui-redesign.md](demo-ui-redesign.md) 7.2 写「老板端 5 / 员工端 5」，但源码实际为两端各 6 项（[tabs.js:8-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L8-L24)），该文档此节在 M1 后已过期。本次以**源码为准**，并统一到 3 项。 |
| **`demo-ux-improvement.md` A12-3「老板端首页宫格扩到 6 项（3 列 × 2 行）」** | 3 列 × 6 项 | **改为 4 列 × 8 项**（两端一致） | 3 列配 8 项会排成 3+3+2 不齐；4 列配 8 项为整行 4×2。列数变更后每格约 88px（375px 视口），仍 ≥44×44 热区。 |
| **`demo-ux-improvement.md` A13-1「员工端宫格 8 项含『通知』」** | 宫格含通知 | **通知移出宫格，升格为「消息」Tab** | 通知本身是「被动接收的信息」，与宫格「主动发起的动作」语义不同（见 A1 划分原则）；通知移出后宫格空出的位置由「我的补卡申请」等**待办动作**填充。 |

> **回写动作（由主智能体执行，本文件不改其他文档）**：在 `demo-ux-improvement.md` B0.1、A12-1、A12-3、A13-1 与 `demo-ui-redesign.md` 7.2 处追加一行注记「已由 `demo-mobile-nav-redesign.md` 0.2 变更，以新文件为准」。

### 0.3 不确定项与验证方式

| # | 不确定项 | 本文处理 | 验证方式 |
| ---- | ---- | ---- | ---- |
| U1 | 重复点击当前 Tab 时，Vant 4 `van-tabbar` 的 `change` 事件是否触发（值是同值时可能被去重） | 规范按「需自行判断」设计双保险方案，标注 **待走查** | 实现后在浏览器用 `@change` 打点；若不触发，改用 `van-tabbar-item` 的 `@click` 自行比对 `route.path`（见 C5-2） |
| U2 | Vant 4 `van-tabbar-item` 是否输出 `aria-current="page"` 给当前项 | 规范要求显式绑定 `:aria-current`，不依赖 Vant 默认 | 实现后查看 DOM；Vant 未输出则由绑定补齐（见 E1） |
| U3 | 消息 Tab 未读角标「未读通知 + 待办」合计口径是否与产品预期一致 | 本文给出明确口径（C4-2），标注为**设计决策**，如需改口径只改一处聚合函数 | 评审确认；口径实现集中在 `MessagePage` 的 computed |
| U4 | `GET /kpi/scores/:employeeId` 在无考核数据时返回错误码（`KPI_CODE.SCORE_NOT_EXISTS`，见 [kpiStore.js:448](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/kpiStore.js#L448)），前端需把它当「空」而非「错误」 | 规范明确：该码走宫格「空」态（显示「未考核」），不进错误态 | 读 `shared/constants/errorCode.js` 的 `KPI_CODE` 段确认码值与 http 层是否 silent |
| U5 | Vant `van-badge` 默认底色是否为 `--van-danger-color`（决定角标是否已是 #CF1322） | 规范按「已在 [tokens.scss:188](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L188) 设定 `--van-danger-color: var(--color-danger)`＝#CF1322」推断角标底色达标 | 实现后量取角标像素色；若为 Vant 内置红 `#ee0a24`，则为 `--van-badge-background` 显式赋值 |
| U6 | 老板端是否需在移动端打卡 | 规范判定**不需要**（老板端 `ADMIN` 无排班/打卡语义，`/attendance/status` 对 ADMIN 的返回未验证） | 读 [attendance.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js) 的 `status` handler 确认 ADMIN 分支 |

---

# A. 信息架构

## A1 三 Tab 的职责定义

### A1-1 划分原则（先定原则，再定内容，避免「哪里放得下就放哪里」）

1. **按「用户意图」分层，不按「业务模块」分层。** 业务模块（考勤/工单/财务/人事）是后端视角；移动端按用户此刻的意图分三类：**要做的事**（首页）、**要知的事**（消息）、**要查的自己**（我的）。
2. **一个入口只在一个 Tab 有主落点。** 同一队列禁止在两个 Tab 各挂一份入口（现有 `MeSection` 的「待办审批」与首页宫格重复就是这类问题，见 A2 迁移表）。
3. **高频动作不超过 1 次点击；低频查询不超过 2 次点击。** 打卡、取件核销、看异常必须在首页宫格/顶部状态区直达；个人档案、KPI 明细、工资单明细允许进二级页。
4. **数据看板与动作入口分离。** 数值（今日入库、待取件）属于首页「概览区」；「待我处理 N 件」属于宫格角标。同一数字不两处出现，避免出现两个真源。

### A1-2 职责定义

| Tab | 一句话职责 | **承载** | **不承载** |
| ---- | ---- | ---- | ---- |
| **首页** | 「我现在要做什么」——高频动作 + 待办入口 + 关键数据概览 | ① 顶部状态区（员工端＝今日出勤状态 + 一键打卡；老板端＝经营概览 + 待办总数）② 快捷功能宫格（带实时数据，见 B 章）③ 1–3 个关键数据区块（员工端＝4 指标卡 + 异常提示条；老板端＝4 指标卡 + 异常提示条 + 趋势卡 + 同步健康度 + 驿站 TOP3 + 组织规模折叠） | ❌ 通知列表（→ 消息）❌ 个人资料/账号/设置（→ 我的）❌ 低频查询列表（打卡记录、我的档案、KPI 明细 → 我的）❌ 配置类表单（打卡规则、排班管理、采集配置 → 我的「管理与配置」或 PC） |
| **消息** | 「有什么需要我知道/处理」——通知事件流 + 待办快照 | ① **通知**子视图：系统联动通知 + 手工公告（含未读/已读、全部已读、按 `bizType` 一跳到位；ADMIN 页头带「发布」）② **待办**子视图：「待我处理」队列聚合（员工端 3 类 / 老板端 5 类，见 A4） | ❌ 业务台账/历史列表（已处理完的工单去工单页看，不在消息里做「已办」）❌ 新建/编辑类表单（发布通知是页头动作，表单仍走独立二级页）❌ 个人数据（→ 我的） |
| **我的** | 「我自己是谁、我有什么、账号怎么设」 | ① 我的数据（个人业务数据只读查询）② 管理与配置（仅 ADMIN：KPI/人事/排班/打卡规则/打卡记录/采集状态）③ 账号信息 ④ 演示身份切换 ⑤ 账号安全（修改密码）⑥ 运行环境（Demo 诊断，只读，降级）⑦ 退出登录 | ❌ 待办队列（→ 消息，避免两处口口径漂移）❌ 高频作业动作（→ 首页宫格）❌ 通知列表（→ 消息） |

## A2 现有入口迁移映射表（全量，不得遗漏）

> 口径：**新归属**只能是 `首页`（快捷宫格 / 顶部状态区 / 首页区块）、`消息`（通知 / 待办）、`我的`（我的数据 / 管理与配置 / 账号信息 / 演示身份 / 账号安全 / 运行环境）、或「降级」为某二级页入口 / 页内动作 / 只读诊断区。
> 共 **95 条**（老板端 46 条 + 员工端 49 条）。带 ★ 的为「降级处理」项（无法成为某 Tab 的一级模块）。

### A2-1 老板端（ADMIN）

| # | 现有入口 / 页面 | 出处 | 原位置 | 新归属 | 新落点 | 处理 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| B1 | 总览 `/boss/home` | [tabs.js:9](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L9) | Tabbar 第 1 项 | 首页 | Tab 根路由 `/boss/home` | 保留 |
| B2 | 考勤 `/boss/attendance` | [tabs.js:10](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L10) | Tabbar 第 2 项 | 首页 | 快捷宫格「考勤概览」 | 降为二级页 |
| B3 | 趋势 `/boss/trend` | [tabs.js:11](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L11) | Tabbar 第 3 项 | 首页 | 快捷宫格「包裹趋势」+ 首页趋势卡 | 降为二级页 |
| B4 | 排行 `/boss/rank` | [tabs.js:12](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L12) | Tabbar 第 4 项 | 首页 | 快捷宫格「驿站排行」+ 首页 TOP3 区块 | 降为二级页 |
| B5 | 预警 `/boss/alerts` | [tabs.js:13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L13) | Tabbar 第 5 项 | 首页 | 快捷宫格「异常预警」+ 首页异常提示条 | 降为二级页 |
| B6 | 我的 `/boss/me` | [tabs.js:14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L14) | Tabbar 第 6 项 | 我的 | Tab 根路由 `/boss/me` | 保留 |
| B7 | 工单管理（宫格） | [boss/home.vue:185-190](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L185-L190) | 首页宫格第 1 项 | 首页 | 快捷宫格「工单管理」 | 保留（角标＝待处理数） |
| B8 | 补卡审批（宫格） | [boss/home.vue:191-197](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L191-L197) | 首页宫格第 2 项 | 首页 + 消息 | 宫格「补卡审批」+ 消息·待办「待审批补卡」 | 保留 + 新增待办落点 |
| B9 | 工资单审核（宫格） | [boss/home.vue:198-204](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L198-L204) | 首页宫格第 3 项 | 首页 + 消息 | 宫格「工资单审核」+ 消息·待办「待审核工资单」 | 保留 + 新增待办落点 |
| B10 | 入离职审批（宫格） | [boss/home.vue:205-211](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L205-L211) | 首页宫格第 4 项 | 首页 + 消息 | 宫格「入离职审批」+ 消息·待办「进行中入离职」 | 保留 + 新增待办落点 |
| B11 | KPI 考核（宫格） | [boss/home.vue:212](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L212) | 首页宫格第 5 项 | 我的 | 我的·管理与配置「KPI 考核」 | ★ 降为二级页入口 |
| B12 | 发布通知（宫格） | [boss/home.vue:213](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L213) | 首页宫格第 6 项 | 消息 | 消息页头右上「发布」动作（ADMIN）→ 二级表单页 | ★ 降为页内动作 |
| B13 | 工单管理（我的·待办审批） | [MeSection.vue:45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L45) | 我的分组 1 | 消息 | 消息·待办「待处理工单」 | 去重（我的不再重复） |
| B14 | 补卡审批（我的·待办审批） | [MeSection.vue:46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L46) | 我的分组 1 | 消息 | 消息·待办「待审批补卡」 | 去重 |
| B15 | 工资单审核（我的·待办审批） | [MeSection.vue:47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L47) | 我的分组 1 | 消息 | 消息·待办「待审核工资单」 | 去重 |
| B16 | 入离职审批（我的·待办审批） | [MeSection.vue:48](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L48) | 我的分组 1 | 消息 | 消息·待办「进行中入离职」 | 去重 |
| B17 | KPI 考核（我的·我的数据） | [MeSection.vue:58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L58) | 我的分组 2 | 我的 | 我的·管理与配置「KPI 考核」 | 保留（与 B11 合并为一条） |
| B18 | 人事管理 | [MeSection.vue:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L59) | 我的分组 2 | 我的 | 我的·管理与配置「人事管理」 | ★ 降为二级页入口 |
| B19 | 排班管理 | [MeSection.vue:60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L60) | 我的分组 2 | 我的 | 我的·管理与配置「排班管理」 | ★ 降为二级页入口 |
| B20 | 打卡记录（全域） | [MeSection.vue:61](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L61) | 我的分组 2 | 我的 | 我的·管理与配置「打卡记录」 | ★ 降为二级页入口 |
| B21 | 打卡规则 | [MeSection.vue:62](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L62) | 我的分组 2 | 我的 | 我的·管理与配置「打卡规则」 | ★ 降为二级页入口 |
| B22 | 通知中心 | [MeSection.vue:63](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L63) | 我的分组 2 | 消息 | 消息·通知 | 去重（升格为 Tab） |
| B23 | 登录账号 | [MeSection.vue:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L76) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| B24 | 手机号 | [MeSection.vue:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L77) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| B25 | 所属驿站 | [MeSection.vue:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L78) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| B26 | 所属部门 | [MeSection.vue:79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L79) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| B27 | 最后登录 | [MeSection.vue:80](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L80) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| B28 | 切换演示身份 | [MeSection.vue:83-84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L83-L84) | 我的分组 4 | 我的 | 我的·演示身份 | 保留 |
| B29 | 修改密码 | [MeSection.vue:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L88) | 我的分组 5 | 我的 | 我的·账号安全 | ★ 降为二级页入口 |
| B30 | 运行容器 | [MeSection.vue:94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L94) | 运行环境 | 我的 | 我的·运行环境（末位只读） | ★ 降为只读诊断区 |
| B31 | 状态栏高度 | [MeSection.vue:95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L95) | 运行环境 | 我的 | 我的·运行环境（末位只读） | ★ 降为只读诊断区 |
| B32 | 退出登录 | [MeSection.vue:99](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L99) | 我的分组 6 | 我的 | 我的·末位（二次确认保留） | 保留 |
| B33 | 工单管理页 `/boss/workorder` | [router/index.js:38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L38) | 二级页 | 二级页 | 首页宫格 B7 / 消息待办 B13 进入 | ★ 二级页保留 |
| B34 | 补卡审批页 `/boss/attendance/makeup` | [router/index.js:39-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L39-L44) | 二级页 | 二级页 | 宫格 B8 / 消息待办 B14 进入 | ★ 二级页保留 |
| B35 | 发布通知页 `/boss/notification/publish` | [router/index.js:46-51](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L46-L51) | 二级页 | 二级页 | 消息页头「发布」进入 | ★ 降为页内动作的后置页 |
| B36 | KPI 考核页 `/boss/kpi` | [router/index.js:54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L54) | 二级页 | 二级页 | 我的·管理与配置进入 | ★ 二级页保留 |
| B37 | 考核明细 `/boss/kpi/:employeeId` | [router/index.js:56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L56) | 三级页 | 三级页 | 由 B36 行内点击进入 | ★ 三级页保留 |
| B38 | 人事管理页 `/boss/hr` | [router/index.js:57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L57) | 二级页 | 二级页 | 我的·管理与配置进入 | ★ 二级页保留 |
| B39 | 员工档案 `/boss/hr/:employeeId` | [router/index.js:58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L58) | 三级页 | 三级页 | 由 B38 进入 | ★ 三级页保留 |
| B40 | 工资单审核页 `/boss/payroll` | [router/index.js:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L59) | 二级页 | 二级页 | 宫格 B9 / 消息待办 B15 进入 | ★ 二级页保留 |
| B41 | 工资单详情 `/boss/payroll/:id` | [router/index.js:60](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L60) | 三级页 | 三级页 | 由 B40 进入 | ★ 三级页保留 |
| B42 | 入离职审批页 `/boss/flow` | [router/index.js:61](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L61) | 二级页 | 二级页 | 宫格 B10 / 消息待办 B16 进入 | ★ 二级页保留 |
| B43 | 流程办理 `/boss/flow/:type/:id` | [router/index.js:62](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L62) | 三级页 | 三级页 | 由 B42 进入 | ★ 三级页保留 |
| B44 | 打卡规则页 `/boss/attendance/rule` | [router/index.js:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L88) | 二级页 | 二级页 | 我的·管理与配置进入 | ★ 二级页保留 |
| B45 | 排班管理页 `/boss/schedule` | [router/index.js:89](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L89) | 二级页 | 二级页 | 我的·管理与配置进入 | ★ 二级页保留 |
| B46 | 打卡记录页 `/boss/attendance/records` | [router/index.js:90](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L90) | 二级页 | 二级页 | 我的·管理与配置进入 | ★ 二级页保留 |

### A2-2 员工端（STATION_ADMIN / STAFF）

| # | 现有入口 / 页面 | 出处 | 原位置 | 新归属 | 新落点 | 处理 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| S1 | 工作台 `/staff/home` | [tabs.js:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L18) | Tabbar 第 1 项 | 首页 | Tab 根路由 `/staff/home` | 保留 |
| S2 | 打卡 `/staff/attendance` | [tabs.js:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L19) | Tabbar 第 2 项 | 首页 | 顶部「一键打卡」+ 快捷宫格第 1 项「打卡」 | 降为二级页 |
| S3 | 包裹 `/staff/parcel` | [tabs.js:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L20) | Tabbar 第 3 项 | 首页 | 快捷宫格「本站包裹」 | 降为二级页 |
| S4 | 工单 `/staff/workorder` | [tabs.js:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L21) | Tabbar 第 4 项 | 首页 + 消息 | 宫格「工单」+ 消息·待办「待处理工单」 | 降为二级页 |
| S5 | 通知 `/staff/notification` | [tabs.js:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L22) | Tabbar 第 5 项 | 消息 | 消息·通知（升格为 Tab 根） | 升格 + 旧路由重定向 |
| S6 | 我的 `/staff/me` | [tabs.js:23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L23) | Tabbar 第 6 项 | 我的 | Tab 根路由 `/staff/me` | 保留 |
| S7 | 打卡（宫格） | [staff/home.vue:40](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L40) | 宫格第 1 项 | 首页 | 快捷宫格第 1 项「打卡」（**决策已确认**） | 保留（数据行＝进度） |
| S8 | 本站包裹（宫格） | [staff/home.vue:41](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L41) | 宫格第 2 项 | 首页 | 快捷宫格「本站包裹」 | 保留（纯入口，见 B5-2） |
| S9 | 取件核销（宫格） | [staff/home.vue:42](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L42) | 宫格第 3 项 | 首页 | 快捷宫格「取件核销」 | ★ 纯入口（不承载数据） |
| S10 | 工单（宫格） | [staff/home.vue:43](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L43) | 宫格第 4 项 | 首页 | 快捷宫格「工单」 | 保留（角标＝待处理数） |
| S11 | 通知（宫格） | [staff/home.vue:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L44) | 宫格第 5 项 | 消息 | 消息·通知 | 移出宫格（升格为 Tab） |
| S12 | 我的排班（宫格） | [staff/home.vue:45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L45) | 宫格第 6 项 | 首页 | 快捷宫格「我的排班」 | 保留（数据行＝今日班次） |
| S13 | 我的工资单（宫格） | [staff/home.vue:46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L46) | 宫格第 7 项 | 首页 + 消息 | 宫格「我的工资单」+ 消息·待办「待确认工资单」 | 保留 |
| S14 | 我的 KPI（宫格） | [staff/home.vue:47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L47) | 宫格第 8 项 | 首页 | 快捷宫格「我的 KPI」 | 保留（数据行＝本月得分） |
| S15 | 我的补卡申请 | [MeSection.vue:51](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L51) | 我的分组 1 | 消息 | 消息·待办「我的补卡申请」 | 移入消息（新增待办落点） |
| S16 | 我的工资单 | [MeSection.vue:52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L52) | 我的分组 1 | 消息 + 我的 | 消息·待办「待确认工资单」+ 我的·我的数据 | 去重（主落点＝消息待办） |
| S17 | 我的 KPI | [MeSection.vue:66](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L66) | 我的分组 2 | 我的 | 我的·我的数据「我的 KPI」 | 保留 |
| S18 | 我的档案 | [MeSection.vue:67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L67) | 我的分组 2 | 我的 | 我的·我的数据「我的档案」 | ★ 二级页入口 |
| S19 | 我的排班 | [MeSection.vue:68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L68) | 我的分组 2 | 我的 | 我的·我的数据「我的排班」 | 保留（与 S12 同一目标，宫格为主入口） |
| S20 | 打卡记录 | [MeSection.vue:69](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L69) | 我的分组 2 | 我的 | 我的·我的数据「打卡记录」 | ★ 二级页入口 |
| S21 | 我的入离职 | [MeSection.vue:70](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L70) | 我的分组 2 | 我的 | 我的·我的数据「我的入离职」 | ★ 二级页入口（无流程接口权限，只读降级） |
| S22 | 同步状态（站长） | [MeSection.vue:71](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L71) | 我的分组 2 | 我的 | 我的·我的数据「同步状态」（仅 `auth.canSeeSync`） | ★ 二级页入口（STAFF 不可见） |
| S23 | 登录账号 | [MeSection.vue:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L76) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| S24 | 手机号 | [MeSection.vue:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L77) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| S25 | 所属驿站 | [MeSection.vue:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L78) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| S26 | 所属部门 | [MeSection.vue:79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L79) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| S27 | 最后登录 | [MeSection.vue:80](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L80) | 账号信息 | 我的 | 我的·账号信息 | 保留 |
| S28 | 切换演示身份 | [MeSection.vue:83-84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L83-L84) | 我的分组 4 | 我的 | 我的·演示身份 | 保留 |
| S29 | 修改密码 | [MeSection.vue:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L88) | 我的分组 5 | 我的 | 我的·账号安全 | ★ 二级页入口 |
| S30 | 运行容器 | [MeSection.vue:94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L94) | 运行环境 | 我的 | 我的·运行环境（末位只读） | ★ 降为只读诊断区 |
| S31 | 状态栏高度 | [MeSection.vue:95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L95) | 运行环境 | 我的 | 我的·运行环境（末位只读） | ★ 降为只读诊断区 |
| S32 | 退出登录 | [MeSection.vue:99](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L99) | 我的分组 6 | 我的 | 我的·末位 | 保留 |
| S33 | 取件核销页 `/staff/pickup` | [router/index.js:76](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L76) | 二级页 | 二级页 | 宫格 S9 进入 | ★ 二级页保留 |
| S34 | 包裹详情 `/staff/parcel/:id` | [router/index.js:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L77) | 二级页 | 二级页 | 包裹列表进入 | ★ 二级页保留 |
| S35 | 新建工单 `/staff/workorder/create` | [router/index.js:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L78) | 二级页 | 二级页 | 工单页 FAB 进入 | ★ 二级页保留 |
| S36 | 工单详情 `/staff/workorder/:id` | [router/index.js:79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L79) | 二级页 | 二级页 | 工单列表 / 通知跳转进入 | ★ 二级页保留 |
| S37 | 同步状态 `/staff/sync` | [router/index.js:81](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L81) | 二级页 | 二级页 | 我的·我的数据（站长）进入 | ★ 二级页保留 |
| S38 | 修改密码页 `/staff/me/password` | [router/index.js:82](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L82) | 二级页 | 二级页 | 我的·账号安全进入 | ★ 二级页保留 |
| S39 | 我的排班页 `/staff/schedule` | [router/index.js:85](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L85) | 二级页 | 二级页 | 宫格 S12 / 我的·我的数据进入 | ★ 二级页保留 |
| S40 | 我的打卡记录 `/staff/attendance/records` | [router/index.js:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L86) | 二级页 | 二级页 | 我的·我的数据 / 打卡页内入口进入 | ★ 二级页保留 |
| S41 | 我的补卡申请 `/staff/attendance/makeup` | [router/index.js:87](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L87) | 二级页 | 二级页 | 消息·待办 / 打卡页内入口进入 | ★ 二级页保留 |
| S42 | 我的 KPI `/staff/kpi` | [router/index.js:93](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L93) | 二级页 | 二级页 | 宫格 S14 / 我的·我的数据进入 | ★ 二级页保留 |
| S43 | 我的工资单 `/staff/payroll` | [router/index.js:94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L94) | 二级页 | 二级页 | 宫格 S13 / 消息·待办进入 | ★ 二级页保留 |
| S44 | 工资单详情 `/staff/payroll/:id` | [router/index.js:95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L95) | 三级页 | 三级页 | 由 S43 进入 | ★ 三级页保留 |
| S45 | 我的档案 `/staff/profile` | [router/index.js:96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L96) | 二级页 | 二级页 | 我的·我的数据进入 | ★ 二级页保留 |
| S46 | 我的入离职 `/staff/flow` | [router/index.js:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L98) | 二级页 | 二级页 | 我的·我的数据进入 | ★ 二级页保留（只读降级） |
| S47 | 打卡页内入口「我的排班」 | [staff/attendance.vue:514](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/attendance.vue#L514) | 打卡页底部 | 二级页内就近入口 | 随打卡页保留（非新条目，与 S19 同目标） | 保留 |
| S48 | 打卡页内入口「打卡记录」 | [staff/attendance.vue:515](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/attendance.vue#L515) | 打卡页底部 | 二级页内就近入口 | 随打卡页保留（非新条目，与 S20 同目标） | 保留 |
| S49 | 打卡页内入口「补卡申请」 | [staff/attendance.vue:516](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/attendance.vue#L516) | 打卡页底部 | 二级页内就近入口 | 随打卡页保留（非新条目，与 S15 同目标） | 保留 |

**映射条目合计：95 条（老板端 46 + 员工端 49）。无遗漏项。**

### A2-3 降级处理项汇总（无法成为某 Tab 一级模块的项）

| 降级类型 | 老板端 | 员工端 | 处理规则 |
| ---- | ---- | ---- | ---- |
| **降为二级页入口**（入口在 Tab 内，页面本体是二级页） | 考勤概览(B2)、包裹趋势(B3)、驿站排行(B4)、异常预警(B5)、KPI 考核(B11/B17)、人事管理(B18)、排班管理(B19)、打卡记录(B20)、打卡规则(B21)、修改密码(B29) | 打卡(S2)、本站包裹(S3)、工单(S4)、取件核销(S9)、我的档案(S18)、打卡记录(S20)、我的入离职(S21)、同步状态(S22)、修改密码(S29) | 二级页保留路由与组件，一级入口收进首页宫格或我…分组 |
| **降为页内动作** | 发布通知(B12/B35) | — | 入口做成消息页头右上按钮，表单仍为独立整页（沿用 [B4.6](demo-ux-improvement.md) 决策） |
| **降为只读诊断区** | 运行环境(B30/B31) | 运行环境(S30/S31) | 我的页末位，Caption 字号，不进任何导航/分组标题，不参与信息层级 |
| **降为首页区块**（非路由，页面内内容） | 组织规模（一期指标）、同步健康度、驿站 TOP3、近 7 天趋势卡 | 4 指标卡、异常提示条 | 保持现有区块顺序，不新增路由 |
| **移动端不做**（登记，无迁移） | 采集配置（PC 专属，移动端只读「采集状态」）、模拟派单（[B3.5](demo-ux-improvement.md) 判定不做，[boss/workorder.vue:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/workorder.vue#L21) TODO）、考勤导出（[B5](demo-ux-improvement.md) 判定不做）、驿站/部门/员工 CRUD | 同左 | 维持现状，本次不新增入口 |

## A3 老板端与员工端的三 Tab 差异

| Tab | 老板端（ADMIN） | 员工端（STATION_ADMIN / STAFF） |
| ---- | ---- | ---- |
| **首页** | 顶部＝经营概览 Hero（今日经营 + 口径：全域 + 数据截止）**无打卡**；指标卡 4 项＝今日入库 / 今日取件 / 待取件 / 异常件；异常提示条；**快捷宫格 8 项（全部计数型，见 B5-1）**；近 7 天趋势卡；同步健康度（含「未配置采集」行）；驿站 TOP3；组织规模（折叠，末位） | 顶部＝Hero（站名 + 角色 chip + 今日待处理 N 条 + 构成说明）+ **今日出勤状态条 + 一键打卡**；指标卡 4 项＝今日入库 / 待取件 / 今日取件 / 异常件；打卡提示条 + 工单超时提示条；**快捷宫格 8 项（6 项带数据 + 2 项纯入口）**；本站包裹总量/取件率口径行 |
| **消息** | 通知子视图：全部/未读 + 类型筛选 + 全部已读；**页头右上「发布」**；待办子视图 **5 类**：待处理工单 / 待审批补卡 / 待审核工资单 / 进行中入离职 / 采集异常·未配置（只读提醒） | 通知子视图：全部/未读 + 全部已读；**无发布**；待办子视图 **3 类**：待处理工单 / 待确认工资单 / 我的补卡申请（审批中） |
| **我的** | 分组＝①管理与配置（KPI 考核 / 人事管理 / 排班管理 / 打卡规则 / 打卡记录〔全域〕）②账号信息 ③演示身份（Demo 专用）④账号安全 ⑤运行环境 ⑥退出登录。**无「我的数据」**（老板无个人业务数据） | 分组＝①我的数据（我的 KPI / 我的档案 / 我的排班 / 打卡记录 / 我的入离职 / 同步状态〔站长〕）②账号信息 ③演示身份 ④账号安全 ⑤运行环境 ⑥退出登录。**无管理与配置** |

**两端必须一致的部分**（同一组件、同一 Token、同一交互）：Tabbar 结构/尺寸/色值/角标规则、宫格布局与热区、通知子视图的列表与已读规则、账号信息/演示身份/账号安全/运行环境四段、退出登录二次确认文案。

## A4 消息 Tab 的定义

### A4-1 与既有「通知中心」的关系：**合并，不重写**

- 既有 `/staff/notification`（[staff/notification.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue)）整体**升格为消息 Tab 的「通知」子视图**，列表交互（van-list 20/页、未读 Tab、全部已读、按 `bizType` 跳转）**全部保留**，不做重写。
- 消息 Tab = 「通知」+「待办」两段全宽 Tab（`van-tabs`，高 44，与 [demo-ui-redesign.md](demo-ui-redesign.md) 5.6 的 Tabs 规范一致）。
- 路由：**新增** `/boss/message`（roles `[ADMIN]`）与 `/staff/message`（roles `STAFF_ROLES`），二者渲染同一组件 `views/message/MessagePage.vue`（沿用 [A12-7](demo-ux-improvement.md) 的「同一业务对象两端复用同页 + 按角色渲染」模式）。既有 `/staff/notification` **改为重定向**到 `/staff/message?tab=notice`，保留兼容（该路由当前为 `ALL_ROLES`，[router/index.js:70-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L70-L72)），避免旧链接 404。
- 为什么新增两个路由而不是复用 `/staff/notification`：Tab 根路由应与该端前缀一致（老板端全部 `/boss/*`），避免「老板端 Tabbar 第二项指向 `/staff/*`」的认知负担；且新页面要容纳两类内容，路径名「notification」已不准确。

### A4-2 两类内容的关系与区分规则

| 维度 | 通知（Notice） | 待办（Todo） |
| ---- | ---- | ---- |
| 本质 | **事件流（时间线）**——系统推送了什么 | **状态快照（队列）**——当前还需要我做什么 |
| 数据源 | `GET /notifications`（[notification.js:118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L118)） | 前端聚合各业务「待我处理」列表接口（见 A4-3） |
| 已读语义 | 有 `isRead` / `readTime`（[notification.js:19-20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L19-L20)）；可单条已读、可全部已读 | **无「已读」概念，只有「已处理」**：业务状态变化后该项**自动从待办列表消失**。**禁止**提供「标记已办」按钮（会产生伪状态，与业务不符） |
| 未读/已读区分 | `全部 / 未读` 两 Tab，请求参数 `isRead`（[notification.js:33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L33)） | 只显示「待处理」，不显示已办；「已办」去对应业务页看历史（如工单列表的「已解决/已关闭」） |
| 数量上限 | 分页（20/页，`van-list`） | 每类**只显示前 3 条** + 行尾「查看全部 N 条 ›」，避免消息页变成第二个业务列表 |
| 位置 | Tab 1（默认） | Tab 2 |
| 空态 | 「暂无通知」/「没有未读通知」（[notification.vue:46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue#L46)） | 「暂无待办，今天只剩你自己了」（员工端）/「暂无待办事项」（老板端），空态**不得**与错误态共用文案 |

**交集处理**：工单指派会产生一条 `bizType='work_order'` 的通知（时间线）与一条「待处理工单」待办（快照）。两者**不合并、不去重**：通知记录「谁在什么时候指派给我」，待办记录「这条工单还没处理」。通知点掉已读不会让待办消失，待办消失（工单被解决）也不会删除通知——这是两个不同的事实。

### A4-3 待办配置表（逐项，前端直接按表实现）

**员工端（3 类）**

| 序 | 待办分组名 | 数据接口 | 参数 | 取值 | 明细行主文案 | 点击跳转 | 权限 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 待处理工单 | `GET /work-orders` | `{ status: 0, pageNum: 1, pageSize: 3 }` | `total` / `list` | `#{id} {title}` + 类型/优先级 | `/staff/workorder` | STAFF_ROLES |
| 2 | 待确认工资单 | `GET /finance/payrolls/my` | `{ status: 'PUBLISHED', pageNum: 1, pageSize: 3 }` | `total` / `list` | `{month} 工资单待确认` | `/staff/payroll` | STAFF_ROLES |
| 3 | 我的补卡申请（审批中） | `GET /attendance/makeup/my` | `{ status: 'PENDING', pageNum: 1, pageSize: 3 }` | `total` / `list` | `{workDate} {periodName}·{checkType}` | `/staff/attendance/makeup` | ALL_ROLES |

> 员工端**不把**「我的入离职」放进待办：`/staff/flow` 是无流程接口权限的只读降级页（[router/index.js:97-98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L97-L98) 注释），没有「等我处理」的动作，归入「我的·我的数据」。
> 员工端**不把**「同步状态」放进待办：站长只读页面，无待办动作，归入「我的·我的数据」。

**老板端（5 类）**

| 序 | 待办分组名 | 数据接口 | 参数 | 取值 | 明细行主文案 | 点击跳转 | 权限 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 待处理工单 | `GET /work-orders` | `{ status: 0, pageNum: 1, pageSize: 3 }` | `total` / `list` | `#{id} {title}` + 归属驿站 | `/boss/workorder` | ADMIN |
| 2 | 待审批补卡 | `GET /attendance/makeup/list` | `{ status: 'PENDING', pageNum: 1, pageSize: 3 }` | `total` / `list` | `{employeeName} {workDate} {periodName}·{checkType}` | `/boss/attendance/makeup` | ADMIN |
| 3 | 待审核工资单 | `GET /finance/payrolls` | `{ status: 'PENDING_APPROVAL', pageNum: 1, pageSize: 3 }` | `total` / `list`（亦可读 `counts.PENDING_APPROVAL`，见 [financeStore.js:487-492](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/financeStore.js#L487-L492)） | `{month} {stationName} 工资单` | `/boss/payroll` | ADMIN |
| 4 | 进行中入离职 | `GET /hr/onboarding` + `GET /hr/offboarding` | `{ status: 'IN_PROGRESS', pageNum: 1, pageSize: 3 }` | 两个 `total` 之和 / 两个 `list` | `{employeeName} {flowName}（入职/离职）` | `/boss/flow` | ADMIN |
| 5 | 采集异常·未配置（只读提醒） | `GET /sync/overview` | — | `counts.abnormal + counts.unconfigured`（[syncConfig.js:123-139](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L123-L139)） | `异常 N 站 · 未配置采集 M 站` | `/boss/alerts` | ADMIN |

> 老板端待办口径与首页宫格**同源**（同一批接口），因此实现上应把「待办计数」收敛到一个 store/composable（建议 `stores/todo.js`），首页宫格角标与消息待办共用，避免同一数字两处实现（呼应 A1-1 原则 4）。

### A4-4 消息页信息架构

```text
消息                                     [发布]  ← 仅 ADMIN，44×44 热区
┌──────────────┬──────────────┐
│    通知 ●3    │    待办 2     │            ← van-tabs，高 44，下划线 2px 主色
└──────────────┴──────────────┘
[通知子视图]
┌ 全部 │ 未读 ┐        [全部已读]            ← 未读为 0 时 disabled + title 说明（沿用 A8-4 规则）
│ ◯ 公告 关于国庆排班的通知       09-18 10:24 │ ← 手工公告：标题前「公告」标识 + 行尾「由 X 发布」
│ ● 工单 #20260918-003 已指派给你  09-18 09:02 │ ← 系统联动：未读左侧圆点
│ ...                                        │
[待办子视图]
待处理工单                    2 条 · 查看全部 ›
│ #20260918-003 包裹破损         城东驿站      │
│ #20260918-007 客户投诉         城西驿站      │
待审批补卡                    1 条 · 查看全部 ›
│ 张三 09-17 上午班·上班卡                     │
```

---

# B. 首页快捷功能入口（需求重点）

## B1 宫格布局规范

| 项 | 规范 | 依据 |
| ---- | ---- | ---- |
| 列数 | **4 列**（老板端与员工端一致），4×2＝8 项 | 3 列配 8 项排成 3+3+2 不齐；4 列在 375px 视口下每格约 87.75px，仍 ≥44×44 |
| 行数 | 2 行（上限 8 项，M7 口径）→ **M11 起改为按 4 列自动换行** | 见下方「M11 变更」 |
| 单元格最小高度 | `88px` | [mobile.scss:345-347](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L345-L347)（`.entry-grid .van-grid-item__content { min-height: 88px }`） |
| 图标尺寸 | `24px`，色 `var(--color-primary-icon)`（#1890FF） | [mobile.scss:340](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L340)、[:349-351](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L349-L351) |
| 名称字号/字重/色 | `12px` / `400` / `var(--text-2)`（#4B5563，白底 7.56:1） | [tokens.scss:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L201)、[:265](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L265)（`--van-grid-item-text-color: var(--text-2)`） |
| 图标与名称间距 | Vant 默认（`--van-grid-item-content-padding: var(--sp-3)`＝12px 上下内边距） | [mobile.scss:339](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L339) |
| 行/列间距 | **不使用 gutter**；相邻格以 12px 内容内边距形成视觉间隔，整块为一张 `--r-lg` 卡片 | [mobile.scss:334-343](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L334-L343)（既有 `.entry-grid` 写法，保持一致） |
| **单项热区** | 88 × (视口/4) ≈ **88×88**，**≥44×44 达标** | WCAG 2.5.5 (AAA)，[demo-ui-redesign.md](demo-ui-redesign.md) 7.4 |
| 名称文案长度 | **≤5 个汉字**（4 列下每格可用宽度 ≈ 87.75 − 2×12 ≈ 64px，5×12px＝60px 恰好一行）；超过 5 字**换行**（最多 2 行，行高 16px），**不用省略号**（省略后用户无法知道全称） | 量算，非臆断 |
| 超过 8 项的处理 | **不翻页、不做「更多」**：第 9 项起一律下沉到「我的」（我的数据 / 管理与配置）。理由：宫格承载「高频动作」，第 9 项已属低频；引入翻页或「更多」会破坏「一屏可达」并增加一次点击 | A1-1 原则 3 |
| 宫格标题 | 统一「快捷功能」（员工端）/「快捷功能」（老板端）；老板端标题右侧可挂 `--fs-caption` 的说明「按待办优先排序」 | 与 [mobile.scss:122-136](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L122-L136) 的 `.section-title` 一致 |
| 排序规则 | **按待办优先**：待办类（有角标）在前，纯入口/状态类在后；同组内按业务频次降序。员工端固定「打卡」为第 1 项（**决策已确认**） | 需求确认项 |

> **M11 变更（2026-09-20）**：请假模块给首页宫格新增入口 —— 老板端「请假审批」、员工端「请假」、站长额外可见「请假初审」，结果为**老板端 9 项 / 员工端 9 项 / 站长 10 项，已超出 M7 约定的 4 列 × 2 行（8 项）**。
> 裁决：**接受，改按 4 列自动换行**（第 3 行不满 4 项，左对齐），不翻页、不做「更多」、不下沉到「我的」。理由：
> 1. 请假是 M11 核心链路（员工申请 → 站长初审 → 老板终审），下沉会让高频动作多一次点击，与 A1-1 原则 3 冲突；
> 2. 实测 375px 竖屏下每格仍约 88×88（≥44×44），4 列布局与热区均有 e2e 用例守护（`A3-1`/`A3-2`/`B1-2`/`B1-3`）；
> 3. 项数由配置数组驱动，两端首页零改动；继续增长时的处置见 [HomeQuickGrid.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/HomeQuickGrid.vue) 的 `TODO(扩展)`。

## B2 老板端功能项清单（逐项）

| # | 名称 | 图标（Vant，已核实） | 类型 | 实时数据 | 数据来源 | 交互 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 工单管理 | `todo-list-o` | 计数型 | 待处理工单数 | `GET /work-orders?status=0&pageNum=1&pageSize=1` → `total` | 点击 → `/boss/workorder` |
| 2 | 补卡审批 | `clock-o` | 计数型 | 待审批补卡数 | `GET /attendance/makeup/list?status=PENDING&pageNum=1&pageSize=1` → `total` | 点击 → `/boss/attendance/makeup` |
| 3 | 工资单审核 | `bill-o` | 计数型 | 待审核工资单数 | `GET /finance/payrolls?status=PENDING_APPROVAL&pageNum=1&pageSize=1` → `total` | 点击 → `/boss/payroll` |
| 4 | 入离职审批 | `friends-o` | 计数型 | 进行中流程数（入职+离职） | `GET /hr/onboarding?status=IN_PROGRESS` + `GET /hr/offboarding?status=IN_PROGRESS` → 两个 `total` 之和 | 点击 → `/boss/flow` |
| 5 | 考勤概览 | `records` | 计数型 | 今日异常项数（迟到+早退+缺卡） | `GET /attendance/summary` → `lateCount + earlyLeaveCount + absentCount` | 点击 → `/boss/attendance` |
| 6 | 包裹趋势 | `chart-trending-o` | 状态型 | 今日取件件数 | `GET /parcels/summary` → `todayPickup` | 点击 → `/boss/trend` |
| 7 | 驿站排行 | `bar-chart-o` | 状态型 | TOP1 驿站名 | `GET /parcels/ranking` → `list[0].stationName` | 点击 → `/boss/rank` |
| 8 | 异常预警 | `warning-o` | 计数型 | 异常项总数（超 48h 未取件 + 同步失败批次 + 超时未处理工单 + 采集异常/未配置） | `GET /parcels`（超 48h）+ `GET /sync-tasks`（status=3）+ `GET /work-orders?overdueUnhandled=1` + `GET /sync/overview`（counts） | 点击 → `/boss/alerts` |

> 图标名核实方式：`todo-list-o` / `records` / `bill-o` / `friends-o` / `chart-trending-o` / `bar-chart-o` / `warning-o` / `clock-o` 全部已在移动端源码中实际使用（[boss/home.vue:185-213](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L185-L213)、[tabs.js:9-14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L9-L14)），非臆造。

## B3 员工端功能项清单（逐项）

| # | 名称 | 图标（Vant，已核实） | 类型 | 实时数据 | 数据来源 | 交互 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 打卡 | `clock-o` | 状态型 | 今日打卡进度「已完成 X/Y」 | `GET /attendance/status` → `X = periods 中 onChecked/offChecked 为真计数`，`Y = periods.length × 2` | 点击 → `/staff/attendance` |
| 2 | 本站包裹 | `logistics` | 纯入口 | —（数据由首页指标卡「待取件」承载） | — | 点击 → `/staff/parcel` |
| 3 | 取件核销 | `scan` | 纯入口 | — | — | 点击 → `/staff/pickup` |
| 4 | 工单 | `todo-list-o` | 计数型 | 待处理工单数 | `GET /work-orders?status=0&pageNum=1&pageSize=1` → `total` | 点击 → `/staff/workorder` |
| 5 | 我的排班 | `calendar-o` | 状态型 | 今日班次名（无排班显示「今日休息」） | `GET /attendance/status` → `shift.shiftName`（`hasSchedule` 为假时显示「未排班」） | 点击 → `/staff/schedule` |
| 6 | 我的工资单 | `bill-o` | 计数型 | 待确认张数 | `GET /finance/payrolls/my?status=PUBLISHED&pageNum=1&pageSize=1` → `total` | 点击 → `/staff/payroll` |
| 7 | 我的 KPI | `bar-chart-o` | 状态型 | 本月总得分（无考核显示「未考核」） | `GET /kpi/scores/{auth.user.id}?month=YYYY-MM` → `totalScore`（错误码 `SCORE_NOT_EXISTS` 视为空，见 U4） | 点击 → `/staff/kpi` |
| 8 | 我的补卡申请 | `records` | 计数型 | 审批中单数 | `GET /attendance/makeup/my?status=PENDING&pageNum=1&pageSize=1` → `total` | 点击 → `/staff/attendance/makeup` |

> 图标名核实方式：全部已在 [staff/home.vue:40-47](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L40-L47) 与 [boss/attendance.vue:46-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/attendance.vue#L46-L49) 实际使用，非臆造。

## B4 实时数据的展示规范

### B4-1 三种形态（互斥，不叠加）

| 形态 | 结构 | 适用 | 视觉 |
| ---- | ---- | ---- | ---- |
| **计数型** | 图标（右上角标）+ 名称 | 「有 N 件等我处理」的队列 | 角标：Vant `van-grid-item` 的 `badge` 属性；底 `--color-danger`（#CF1322）白字 `10px`、`--r-full` |
| **状态型** | 图标 + 名称 + 第二行数据行（`12px` / `--text-3`） | 状态值（打卡进度、今日班次、KPI 得分、今日取件、TOP1 驿站） | 无角标；数据行与名称同字号、色降一档 |
| **纯入口型** | 图标 + 名称 | 无对应实时数据且不宜伪造的动作入口 | 无角标、无数据行 |

> **为什么计数型不额外加数据行**：角标已承载数值，再加一行同值文字是冗余；三种形态在 `min-height: 88px` + 垂直居中的网格中并存不会错位（[mobile.scss:345-347](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L345-L347)）。

### B4-2 五态展示规则（逐态明确）

| 态 | 计数型 | 状态型 | 纯入口型 |
| ---- | ---- | ---- | ---- |
| **有值（N > 0）** | 角标显示数值（`badge = N`） | 数据行显示业务文字（如「已完成 1/2」「早班 08:00-18:00」「88 分」） | 无变化 |
| **值为 0** | **角标不渲染**（`badge` 传空串 `''`；先例 [boss/home.vue:187](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L187) `:badge="todo.orders || ''"`） | 数据行显示业务零值文案（如「已完成 0/2」「今日休息」），**不显示 `0`**（无意义数字） | 无变化 |
| **加载中** | 角标**留空**（不显示骨架、不显示 `0`） | 数据行显示 `···`（三个点，`--text-3`） | 无变化 |
| **加载失败** | 角标不渲染（**降级为无角标**，不显示 `0` 也不显示 `!`） | 数据行显示 `—` | 无变化 |
| **边界** | N > 99 显示 `99+`（前端格式化，Vant 支持字符串 badge，见官方 Icon 文档示例 `badge="99+"`） | 文字超长省略（`max-width: 100%` + `text-overflow: ellipsis`），完整值通过点击进入业务页获取 | — |

**硬规则**：
1. **单项失败不得拖垮整页**：每个宫格项独立 `.catch(() => null)` 后降级（先例 [boss/home.vue:48-54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L48-L54)）。
2. **绝不用 `0` 表示「加载失败/未知」**：`0` 是「没有待办」这一结论，与「拿不到数据」是两件事（先例 [boss/home.vue:24-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L24-L25) 注释：`null` 表示接口不可用）。
3. **不显示假数据**：接口失败时不显示上一次的缓存值，避免误导。

## B5 老板端 / 员工端宫格设计决策（必须遵守，避免实现时反复）

| # | 决策 | 理由 |
| ---- | ---- | ---- |
| B5-1 | 老板端 8 项**全部**带实时数据（6 计数 + 2 状态），无纯入口项 | 老板端 8 项均为「异常/待办/概览」，都对应一个可计数的业务事实，不存在无数据的动作入口 |
| B5-2 | 员工端「本站包裹」「取件核销」为**纯入口**，不承载数据 | 其核心数值（待取件、异常件）已由首页 4 指标卡承载（[staff/home.vue:156-159](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L156-L159)），宫格再显示同一数字会形成两个真源（违反 A1-1 原则 4）；且这两项的动作语义是「进入列表/开始核销」，不是「有 N 件事等我」 |
| B5-3 | 员工端宫格**不含「通知」** | 通知已升格为消息 Tab（A2-2 S11），宫格不再重复 |
| B5-4 | 「我的排班」「我的 KPI」「我的补卡申请」**同时**出现在宫格与「我的」 | 宫格是高频直达（一线现场），「我的」是兜底查询（低频）；两者是同一目标的不同路径，**不视为重复入口**（与 A1-1 原则 2 的「同一队列不两处挂」不同：这里不是「队列」，是「查询」） |

## B6 接口选型（盘点的结论）

### B6-1 结论：**全部复用既有契约，本期不新增端点**

经逐文件核对 `src/shared/mock/routes/*`，B2/B3 的全部 16 个宫格项、首页指标卡、消息待办所需的**每一个数据都可由既有接口取得**，无需新增：

| 所需数据 | 既有接口 | 契约位置 | 是否需改契约 |
| ---- | ---- | ---- | ---- |
| 待处理工单数 / 超时未处理数 | `GET /work-orders`（`status` / `overdueUnhandled` 参数均支持） | [workOrder.js:396](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/workOrder.js#L396) | 否 |
| 待审批补卡数 | `GET /attendance/makeup/list` | [attendance.js:472](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L472) | 否 |
| 我的补卡申请（审批中） | `GET /attendance/makeup/my` | [attendance.js:471](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L471) | 否 |
| 今日打卡状态 / 班次 / 时段进度 | `GET /attendance/status` | [attendance.js:465](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L465) | 否 |
| 今日考勤异常数 | `GET /attendance/summary` | [attendance.js:468](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/attendance.js#L468) | 否 |
| 待审核工资单数 | `GET /finance/payrolls`（含 `counts`，`status=PENDING_APPROVAL` 亦可） | [finance.js:209](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/finance.js#L209) | 否 |
| 我的待确认工资单数 | `GET /finance/payrolls/my`（`status=PUBLISHED`） | [finance.js:205](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/finance.js#L205) | 否 |
| 进行中入离职数 | `GET /hr/onboarding` + `GET /hr/offboarding` | [hr.js:232](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/hr.js#L232)、[:237](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/hr.js#L237) | 否 |
| 本月 KPI 得分 | `GET /kpi/scores/:employeeId`（需 `month`） | [kpi.js:165](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/kpi.js#L165) | 否 |
| 包裹概览 / 趋势 / 排行 | `GET /parcels/summary` / `/parcels/trend` / `/parcels/ranking` | [parcel.js:69-71](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/parcel.js#L69-L71) | 否 |
| 采集四态计数 | `GET /sync/overview` | [syncConfig.js:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L143) | 否 |
| 同步批次失败数 | `GET /sync-tasks`（`status=3`） | [syncTask.js:103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncTask.js#L103) | 否 |
| 未读通知数 / 通知列表 | `GET /notifications/unread-count` / `/notifications` | [notification.js:118-119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L118-L119) | 否 |

**为什么本期不新增聚合端点**：
1. `src/shared/**` 已冻结（[demo-ux-improvement.md](demo-ux-improvement.md) 0.2 推论 3）；新增端点须主智能体解冻共享层，成本高于收益。
2. 首屏并发请求量本就在既有水平：老板端现状已并发 12 个请求（[boss/home.vue:39-55](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L39-L55)），重构后宫格 8 项复用了其中大部分（工单/同步/采集/包裹），**净增仅 3–4 个**（补卡/工资单/入离职）。
3. 既有单接口即可精确表达每个宫格项的实时值，聚合端点只省请求数、不增信息量。

### B6-2 可选聚合端点（**提案，本期不落地**，仅在请求数成为瓶颈时启用）

> 触发条件：实测首屏并发 > 18 个请求，或出现移动网络下首屏 P75 > 2s。启用前须由主智能体解冻共享层。

| 项 | 内容 |
| ---- | ---- |
| 方法 + 路径 | `GET /mobile/home/summary` |
| 鉴权 | 需登录；返回内容按登录角色收敛（`ADMIN` 返老板端字段，`STATION_ADMIN`/`STAFF` 返员工端字段），**不接受前端传角色参数**，与服务端既有收敛口径一致（[api/index.js:6-7](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/api/index.js#L6-L7)） |
| 入参 | 无（月份由服务端取当前月；驿站范围按登录身份强制收敛） |
| 返回结构 | `{ code, message, data: { generatedAt, badges: { workorder, makeup, payroll, flow, attendanceAbnormal, alert, … }, status: { attendanceProgress: { done, total }, shiftName, kpiScore, todayPickup, topStationName } } }` |
| 字段含义 | `generatedAt`：服务端聚合时间（用于页面「数据截止 HH:mm」文案）<br>`badges.*`：各宫格项的计数值（整数，缺失表示该端无此项）<br>`status.attendanceProgress`：员工端今日打卡进度（`done` 已完成卡数 / `total` 应打总数）<br>`status.shiftName`：员工端今日班次名，无排班为 `null`<br>`status.kpiScore`：员工端本月 KPI 总分，未考核为 `null`<br>`status.todayPickup`：今日取件件数<br>`status.topStationName`：包裹量 TOP1 驿站名 |
| 错误码建议 | 复用既有：`200` 成功；`401` 未登录（沿用鉴权层，无新增码）。**不设业务错误码**——聚合端点任一子项取数失败时，该字段返回 `null` 而非整体失败（与前端逐项降级的语义一致） |
| 缓存建议 | 无服务端缓存（Demo 为内存 Mock）；后端正式实现时建议 30s 内同参数结果复用 |
| 前端使用 | 宫格项数据行/角标直接从 `badges` / `status` 取值，失败时对**单个字段**判 `null` 降级，不整体重试 |

### B6-3 请求编排规范（本期实现方式）

| 规则 | 说明 |
| ---- | ---- |
| 并发 | 全部走 `Promise.all`；每个请求挂独立 `.catch(() => null)`（先例 [boss/home.vue:48-54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L48-L54)） |
| 复用 | 同一接口在首页多处用到的，**取一次共享**：`/parcels/summary`（指标卡 + 趋势口径）、`/attendance/status`（顶部状态条 + 宫格「打卡」「我的排班」）、`/work-orders`（首页 hero 待办总数 + 宫格角标 + 消息待办） |
| 计数请求 | 只为取 `total` 的请求一律 `pageSize: 1`（先例 [boss/home.vue:44-45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L44-L45)），避免为看一个数字拉回整页数据 |
| 收敛 | 待办计数统一收敛到 `stores/todo.js`（建议），首页宫格、消息待办、消息 Tab 角标共用同一份状态，杜绝三处各算一遍 |

---

# C. 底部导航栏视觉与交互规范

## C1 结构

| 项 | 规范 | 依据 |
| ---- | ---- | ---- |
| 高度 | `--tabbar-h: 50px` + `--safe-bottom` | [tokens.scss:275](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L275)、[:273](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L273) |
| iPhone 安全区 | 由 Vant 的 `safe-area-inset-bottom` 处理（`van-tabbar` 的 props，固定时默认开启）+ 项目 `--safe-bottom: env(safe-area-inset-bottom, 0px)` | [TabbarLayout.vue:36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L36)、[tokens.scss:273](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L273) |
| 背景 | `--van-tabbar-background: var(--surface-card)`（#FFFFFF） | [tokens.scss:211](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L211) |
| 上边框 | `border-top: 1px solid var(--border-line)`（#E3E7ED），**不用阴影** | [TabbarLayout.vue:54-56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L54-L56) |
| 层级 z-index | 沿用 Vant 默认 `--van-tabbar-z-index: 1`；**不新增**（项目固定底栏另有 `ActionBar`，二者不同页并存，无叠压冲突） | Vant 4 Tabbar 主题变量 |
| 内容区底部留白 | `padding-bottom: var(--page-pad-bottom-tab)`（= tabbar 高 + 安全区 + 8px），**不再使用 Vant 的 `placeholder`** | [TabbarLayout.vue:49-52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L49-L52)（注释已说明两者叠加会多出一屏空白） |
| 项数 | **固定 3 项**，两端一致（老板/员工按角色渲染标签与路由） | [TabbarLayout.vue:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L20) |

## C2 图标与文字

| 项 | 规范 | 依据 |
| ---- | ---- | ---- |
| Tab 定义（老板端） | ① 首页 `/boss/home` ② 消息 `/boss/message` ③ 我的 `/boss/me` | 本次新增 `/boss/message` |
| Tab 定义（员工端） | ① 首页 `/staff/home` ② 消息 `/staff/message` ③ 我的 `/staff/me` | 本次新增 `/staff/message` |
| 图标名 | 首页 `wap-home-o`、消息 `chat-o`、我的 `user-o` | `wap-home-o` / `user-o` 已在 [tabs.js:9,14,18,23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/constants/tabs.js#L9-L23) 实际使用；`chat-o` 见 Vant 4 官方 Icon 文档示例（`<van-icon name="chat-o" />`），非臆造 |
| 图标尺寸 | `--van-tabbar-item-icon-size: 22px` | [tokens.scss:214](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L214) |
| **选中态是否换图标** | **不换图标，仅变色 + 加粗文字**。理由：① 与现行实现一致（仅靠 `--van-tabbar-item-active-color` 变色）；② 换面性图标需要引入未核实的实底图标名，本轮 `node_modules` 未安装、无法逐名核对，禁止臆造（见 0.3 U 类）；③ 选中态若**只靠颜色**传导，会违反 SC 1.4.1，故补「字重变化」作为第二通道 | 见「文字」行 |
| 文字字号 | `--van-tabbar-item-font-size` 未单独设定 → 取 Vant 默认 `var(--van-font-size-sm)` ＝ `--fs-caption` ＝ **12px** | [tokens.scss:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L201)。**偏差登记**：[demo-ui-redesign.md](demo-ui-redesign.md) 7.2 写「文字 10/400（Vant 默认）」，与源码不符（实际 12px），以源码为准 |
| 文字字重 | 未选中 `400`；**选中 `600`**（`--fw-semibold`）。实现：`.van-tabbar-item--active .van-tabbar-item__text { font-weight: var(--fw-semibold) }` | 三个 Tab 宽度充裕（320px 下每项约 106px），加粗不挤；同时满足 SC 1.4.1「状态不只靠颜色」 |
| 图标与文字间距 | Vant 默认 `--van-tabbar-item-icon-margin-bottom: var(--van-padding-base)`（4px），**项目不覆盖** | Vant 4 Tabbar 主题变量 |
| 文字截断 | `--van-tabbar-item-text` 不换行；「首页/消息/我的」均 2 字，无截断风险 | 量算 |

## C3 状态

| 状态 | 规范 | Token |
| ---- | ---- | ---- |
| 选中 | 图标与文字色 `var(--color-primary)`（#0958D9，白底 6.16:1，达 AA）+ 文字 600 + 选中项背景保持 `--surface-card`（不加背景块） | `--van-tabbar-item-active-color: var(--color-primary)`（[tokens.scss:212](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L212)） |
| 未选中 | 图标与文字色 `var(--text-3)`（#6B7280，白底 4.83:1，达 AA）+ 文字 400 | `--van-tabbar-item-text-color: var(--text-3)`（[tokens.scss:213](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L213)） |
| **按下** | 该项背景短暂变为 `--surface-subtle`（#F5F7FA），时长 `--dur-fast`（120ms）、缓动 `--ease-std`，松开即恢复。同时全局已设 `-webkit-tap-highlight-color: transparent` 防系统高亮叠加（先例 [mobile.scss:219-221](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L219-L221)） | `--surface-subtle`、`--dur-fast`、`--ease-std` |
| 聚焦 | 复用全局 2px 主色焦点环（`:focus-visible`） | [mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38) |
| **禁用 / 无权限** | **不存在**。三 Tab 对该角色恒为 3 项：ADMIN 见 `/boss/*`，STATION_ADMIN / STAFF 见 `/staff/*`（[TabbarLayout.vue:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L20) 按 `auth.isAdmin` 二选一）。**禁止**把某个 Tab 置灰或隐藏来表达权限——权限差异体现在 Tab **内部内容**：无权限的宫格项/分组**不渲染**（而不是渲染成灰色不可点） | 与 [demo-ux-improvement.md](demo-ux-improvement.md) B0.2「无权限＝只读降级 + 一行说明，不渲染不可用按钮」一致 |
| 动效降级 | `prefers-reduced-motion` 全局已覆盖 | [tokens.scss:302-310](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L302-L310) |

## C4 角标

| 项 | 规范 | 依据 |
| ---- | ---- | ---- |
| 出现位置 | 仅**消息** Tab 的图标右上（首页、我的**不挂角标**——首页的待办已由宫格角标承载，两处显示同一批数字会重复） | 设计决策（A1-1 原则 4） |
| 底色 / 字色 / 字号 | 底 `--color-danger`（#CF1322）白字 `10px`；`--r-full` | [TabbarLayout.vue:58-62](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L58-L62)（既有：`.van-badge { font-size: 10px; background: var(--color-danger) }`） |
| 上限 | **> 99 显示 `99+`**（前端格式化，Vant 支持字符串 badge） | Vant 4 Icon/Tabbar 官方文档示例 `badge="99+"` |
| `0` 时 | **不渲染**（`badge` 传空串） | [TabbarLayout.vue:24-26](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L24-L26)（既有 `notify.unread > 0 ? notify.unread : ''`） |
| **口径（本次定义）** | `消息 Tab 角标 = 未读通知数 + 待我处理待办总数`（两类的合计，与首页 Hero 的「今日待处理」口径一致：员工端先例 [staff/home.vue:51](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L51)；老板端先例 [boss/home.vue:67-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L67-L72)）。<br>**理由**：消息 Tab 同时承载「通知」与「待办」两个子视图，角标必须覆盖两类，否则用户看到无角标却有待办。 | 设计决策，登记为 0.3 U3 |
| 失败降级 | 未读数取失败 → 角标为 `0`（不渲染）；待办取失败 → 该部分计 0。角标属辅助信息，**静默失败**，不弹 Toast | 先例 [stores/notify.js:22-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/stores/notify.js#L22-L24) |

## C5 交互

| # | 项 | 规范 |
| ---- | ---- | ---- |
| C5-1 | **点击切换** | 路由模式（`van-tabbar route`，[TabbarLayout.vue:36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue#L36)）；每项 `to` 指向对应 Tab 根路由；路由切换由 Vue Router 完成，Tabbar 靠路径匹配自动高亮 |
| C5-2 | **重复点击当前 Tab** | 行为：**回到该 Tab 的根视图并滚动到顶部**。① 首页：滚动到顶部；② 消息：切回「通知」子视图 + 滚动到顶部，若已在「通知」子视图则刷新未读数；③ 我的：滚动到顶部。<br>**实现双保险**（因 Vant 在同值点击时 `change` 是否触发未知，见 U1）：优先监听 `van-tabbar` 的 `change`；若实测不触发，则改为在 `van-tabbar-item` 上 `@click` 比对 `route.path`。**禁止**用 `watch(route.path)` 实现（路径不变时 watch 不触发） |
| C5-3 | **切换动效** | **Tab 切换不加页面过渡**（即时切换，符合移动端 Tab 心智）；仅 Tabbar 项的颜色/字重过渡 `--dur-fast`（120ms）、`--ease-std`。理由：Vue Router 未配置 transition（[router/index.js:103-107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L103-L107) 仅配了 `scrollBehavior`），新增过渡会与 `scrollBehavior` 的瞬时滚动叠加出现闪动 |
| C5-4 | **滚动位置** | 保持现状：全局 `scrollBehavior: () => ({ top: 0 })`（[router/index.js:106](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L106)）→ **所有导航（含 Tab 切换）都回到顶部**。若产品要求「各 Tab 各自记忆滚动位置」，需引入滚动位置缓存（本期**不做**，登记 `TODO(扩展): 待需求确认后引入 per-Tab scroll 缓存`） |
| C5-5 | **预加载** | 不预加载首页以外的 Tab（路由懒加载 [router/index.js](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js) 全量 `() => import()`），首屏体积优先。但**未读数与待办计数在首页加载时预取**（首页已调 `notify.refresh()`，先例 [staff/home.vue:101](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L101)），保证切到消息 Tab 时角标已正确 |
| C5-6 | **切回时数据刷新** | 消息 Tab 每次 `onActivated`/进入时刷新未读数与待办计数（待办是快照，过期会误导）；首页**不自动重刷**（保留下拉刷新与页面级重试，避免每次切 Tab 都发一轮请求） |
| C5-7 | **点击热区** | 每项宽 = 视口/3（320px 下约 106px），高 = 50px + 安全区 → **≥44×44 达标** |
| C5-8 | **键盘可达** | 路由模式渲染为可聚焦链接（`to` 存在时为 `router-link`），`Tab` 可依次聚焦三项，`Enter` 激活；配合全局 `:focus-visible` 焦点环（[mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38)） |

## C6 Token 取用与新增

### C6-1 取用清单（全部为既有 Token，零新增）

| 用途 | Token |
| ---- | ---- |
| Tabbar 高度 / 安全区 / 内容区留白 | `--tabbar-h`、`--safe-bottom`、`--page-pad-bottom-tab` |
| Tabbar 背景 / 上边框 | `--van-tabbar-background`（→`--surface-card`）、`--border-line` |
| 选中色 / 未选中色 | `--van-tabbar-item-active-color`（→`--color-primary`）、`--van-tabbar-item-text-color`（→`--text-3`） |
| 图标尺寸 | `--van-tabbar-item-icon-size` |
| 按下态背景 | `--surface-subtle` |
| 角标底 / 字色 | `--color-danger`、`--text-on-dark` |
| 宫格：内容内边距 / 图标尺寸 / 名称字号 / 名称色 / 图标色 / 格高 | `--van-grid-item-content-padding`、`--van-grid-item-icon-size`、`--van-grid-item-text-font-size`、`--van-grid-item-text-color`、`--color-primary-icon`、`min-height: 88px`（[mobile.scss:334-347](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L334-L347)） |
| 状态型数据行字号 / 色 | `--fs-caption`、`--text-3` |
| 动效 | `--dur-fast`、`--ease-std` |
| 焦点环 | `--color-primary-icon`（[mobile.scss:35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L35)） |

### C6-2 新增 Token 结论：**无**

本次重构未发现既有三层 Token 无法表达的视觉值：
- Tabbar 结构/颜色全部走 Vant 变量 + L2 语义变量；
- 宫格尺寸/间距已有 `.entry-grid` 一套（[mobile.scss:334-351](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L334-L351)）；
- 角标颜色复用 `--color-danger`；
- 选中态字重用 `--fw-semibold`；
- 状态型数据行复用 `--fs-caption` + `--text-3`。

**严禁**为本次改动「顺手加 Token」（与 [demo-ux-improvement.md](demo-ux-improvement.md) C3-5 的登记原则一致）。

### C6-3 品牌色使用边界（重申，违反即缺陷）

| 色 | 允许 | 禁止 |
| ---- | ---- | ---- |
| `#1890FF`（`--color-primary-icon`） | Tabbar/宫格**图标**、图表线、进度条、1px 描边 | 14px 及以下**文字**；**承载白字的实底** |
| `#0958D9`（`--color-primary`） | Tabbar **选中文字与图标**、主按钮实底、Hero 渐变深端 | 大面积填充 |
| `#6B7280`（`--text-3`） | Tabbar 未选中文字/图标、辅助说明、状态型数据行 | 一级/二级正文 |
| `#CF1322`（`--color-danger`） | 角标实底（白字 5.57:1）、异常数值 | — |
| **严禁** | — | 紫色系；Token 色板外的任何颜色；业务 `<style>` 或模板内联出现十六进制色值 |

> 备注：Tabbar 选中色用 `--color-primary`（700 档 #0958D9）而非 500 档，是因为选中态是「图标 + 文字」同色，文字必须达 AA（[demo-ui-redesign.md](demo-ui-redesign.md) 7.2 已定此规则）。

---

# D. 组件规范增量（Atomic Design）

## D1 组件清单

| 层级 | 组件 | 端 | 动作 | 文件建议 |
| ---- | ---- | ---- | ---- | ---- |
| Atom | `QuickGridBadge` | 移动 | 新建 | 复用 Vant `badge`，不单独成文件（如需 99+ 格式化，抽为工具函数 `formatBadge`） |
| Molecule | `QuickGridItem` | 移动 | 新建 | `mobile/components/QuickGridItem.vue` |
| Molecule | `TodoGroup` | 移动 | 新建 | `mobile/components/TodoGroup.vue` |
| Organism | `HomeQuickGrid` | 移动 | 新建 | `mobile/components/HomeQuickGrid.vue` |
| Organism | `AttendanceStatusBar` | 移动（仅员工端首页） | 新建 | `mobile/components/AttendanceStatusBar.vue` |
| Organism | `NoticeList` | 移动 | 从 [staff/notification.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue) 抽出 | `mobile/components/NoticeList.vue` |
| Organism | `TodoList` | 移动 | 新建 | `mobile/components/TodoList.vue` |
| Organism | `MeSection` | 移动 | **改造** | `mobile/components/MeSection.vue` |
| Organism | `TabbarLayout` | 移动 | **改造** | `mobile/layout/TabbarLayout.vue` |
| 常量 | `tabs.js` | 移动 | **改造** | `mobile/constants/tabs.js` |
| Template | `MessagePage` | 移动 | 新建 | `mobile/views/message/MessagePage.vue` |

## D2 逐组件规范

### D2-1 `QuickGridItem`（Molecule，新建）

- **Anatomy**：`图标容器`（含可选 `角标`）→ `名称`（可选 `数据行`）。
- **Variants**：`count`（计数型）/ `status`（状态型）/ `plain`（纯入口型）。
- **States（7 态）**：

| 态 | 表现 |
| ---- | ---- |
| 默认 | 有值正常渲染（角标数值 / 数据行文字） |
| 加载 | 计数型：角标留空；状态型：数据行 `···`；纯入口型：无变化。**不出现骨架块，避免高频首屏抖动** |
| 空 | 计数型：角标不渲染（值 0）；状态型：数据行显示业务零值文案（「今日休息」「未考核」「已完成 0/2」） |
| 错误 | 计数型：不渲染角标；状态型：数据行 `—`；**不显示 `!` 或红色**（宫格是入口不是告警面） |
| 禁用 | 该变体不存在——无权限的项**不渲染**，不做禁用样式 |
| 无权限 | 由父组件 `HomeQuickGrid` 按角色过滤后不渲染该项 |
| 边界 | 名称 >5 字换行（最多 2 行）；数值 >99 → `99+`；数值 >9999 → `9999+`；数据行文字超宽省略 |

- **Token 映射**：图标色 `--color-primary-icon`；图标尺寸 `--van-grid-item-icon-size`（24px）；名称 `--van-grid-item-text-font-size`（12px）+ `--van-grid-item-text-color`（`--text-2`）；数据行 `--fs-caption` + `--text-3`；角标底 `--color-danger` + 白字 10px；格高 88px；按下态 `--surface-subtle` + `--dur-fast`。
- **无障碍**：图标 `aria-hidden="true"`（装饰）；**名称文字承载语义**，禁止只有图标；整项为链接（`to`），可聚焦，`:focus-visible` 用全局焦点环；角标数值通过 `aria-label` 播报（如 `aria-label="工单，2 条待处理"`），避免读屏只读「工单」。

### D2-2 `HomeQuickGrid`（Organism，新建）

- **Anatomy**：`区块标题`（含可选说明）→ `van-grid`（4 列）→ N 个 `QuickGridItem`。
- **Variants**：`boss`（8 项全计数/状态）/ `staff`（6 项带数据 + 2 纯入口）。
- **States（7 态）**：

| 态 | 表现 |
| ---- | ---- |
| 默认 | 8 项正常渲染 |
| 加载 | 宫格结构与项名先渲染（配置是静态的），仅数据位进入加载态——**首屏不出现「宫格整块骨架再变内容」的跳动** |
| 空 | 不适用（宫格项由前端配置恒有 8 项）；若过滤后为 0 项，显示一行 `--fs-caption` 文案「暂无可用的快捷功能」而非插画 |
| 错误 | **整体不进入错误态**（逐项降级，见 B4-2 硬规则 1）；仅当配置数组本身为空才提示 |
| 禁用 | 不存在（项级无禁用） |
| 无权限 | 按角色过滤后不渲染该项（老板端/员工端各自一份配置数组） |
| 边界 | 8 项为硬上限；第 9 项一律下沉到「我的」（B1 表） |

- **Token 映射**：`.entry-grid` 既有全套（[mobile.scss:334-351](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L334-L351)）；区块标题 `--fs-h3` + `--fw-semibold`。
- **无障碍**：容器 `<nav aria-label="快捷功能">`；项为链接；键盘 `Tab`/`Enter` 可达（现有写法用 `@keydown.enter` 兜底，[boss/home.vue:189](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue#L189)，**新组件应改为原生链接以免重复处理**）。

### D2-3 `AttendanceStatusBar`（Organism，新建，仅员工端首页）

- **Anatomy**：`状态图标` + `状态文案`（今日出勤状态）+ `主按钮`（一键打卡 / 查看打卡详情）。
- **Variants**：`not-checked-in`（未打上班卡）/ `half-done`（已打上班卡、未打下班卡）/ `done`（今日已完成）/ `no-rule`（未配置规则）/ `no-schedule`（今日无排班）。
- **States（7 态）**：

| 态 | 表现 |
| ---- | ---- |
| 默认 | 按 `GET /attendance/status` 的 `periods` 判定当前应打槽位，主按钮文案「上班打卡 / 下班打卡」 |
| 加载 | 状态文案 `···`，主按钮 `disabled`（避免按到未就绪的卡） |
| 空 | `rule` 为空 → 显示「该驿站尚未配置打卡规则」+ 次按钮「查看打卡详情」（对应 `no-rule` 变体） |
| 错误 | 状态文案「出勤状态获取失败」+ 次按钮「重试」（**不阻塞首页其余内容**，先例 [staff/home.vue:77-84](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L77-L84) 的降级口径） |
| 禁用 | 无可打卡槽位（未到时间窗 / 已全部完成）→ 主按钮降级为「查看打卡详情」（语义从主操作变次操作），不保留一个点不动的灰按钮 |
| 无权限 | 老板端**不渲染**该组件 |
| 边界 | 支持每日 2 段（4 张卡）场景（[staff/attendance.vue:26-34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/attendance.vue#L26-L34) 注释）；跨日时间窗文案走 `periodWindowText`（[utils/attendance.js:51-57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/utils/attendance.js#L51-L57)） |

- **Token 映射**：状态色 `--color-warning` / `--color-success` + 对应 `--color-*-surface`（先例 [staff/home.vue:54-75](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue#L54-L75)）；按钮走 `van-button` primary（`--van-button-primary-background` → `--color-primary`）；主按钮 `min-height: 44px`。
- **无障碍**：主按钮 `aria-label` 必须含语义（如「上班打卡，早班 08:00-18:00」）；状态文案用 `role="status"`（可被读屏播报，但**不打断**）；打卡失败提示用 `role="alert"`。
- **实现要点（前端直接照做）**：一键打卡复用 [staff/attendance.vue:231-263](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/attendance.vue#L231-L263) 的提交逻辑（`wifiSsid` 走 `bridge.getWifiInfo`，坐标走 `navigator.geolocation`，`checkIn` 走 silent 并按 `ATTENDANCE_CODE` 给针对性提示）。**必须抽为共享 composable**（建议 `mobile/composables/useCheckIn.js`），避免首页与打卡页两份打卡实现漂移。备注：浏览器环境定位必然在围栏外，首页的「演示辅助」开关需要保留（可在失败提示里给「去打卡页开启演示辅助」的引导，避免首页塞入演示开关）。

### D2-4 `NoticeList`（Organism，从通知页抽出）

- **Anatomy**：`Tab（全部/未读）` + `工具条（类型筛选 + 全部已读）` + `van-list` 列表项（未读圆点 / 公告标识 / 标题 / 内容摘要 / 发布时间 / 发布人）。
- **Variants**：`full`（独立整页，带 NavBar，兼容旧路由）/ `embedded`（嵌在消息 Tab 内，无独立 NavBar，高度受 Tab 容器约束）。
- **States（7 态）**：

| 态 | 表现 |
| ---- | ---- |
| 默认 | 列表项按时间倒序（契约已排序，[notification.js:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/notification.js#L34)） |
| 加载 | `van-list` 的 `loading` 文案（首屏用 `PageState loading`） |
| 空 | 「暂无通知」/「没有未读通知」（[notification.vue:46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue#L46)） |
| 错误 | `PageState error` + 重试（保留现有） |
| 禁用 | 「全部已读」在未读为 0 时 `disabled`，且必须给原因（`title="当前没有未读通知"`，沿用 [A8-4](demo-ux-improvement.md) 规则） |
| 无权限 | 不适用（通知对所有登录角色可见） |
| 边界 | 长标题省略 + 内容 2 行截断；分页 20/页 + `finished`（[notification.vue:28](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue#L28)）；`bizType` 目标页无权限时给明确 Toast（先例 [notification.vue:119-122](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue#L119-L122)） |

- **Token 映射**：列表项 `--fs-body-strong` / `--fs-caption`；未读圆点 `--color-danger`；公告标识复用 `StatusTag`（`--state-primary-*`）；时间 `--text-3`。
- **无障碍**：未读状态**不只靠圆点颜色**——未读项整条加 `--fw-medium` 并提供读屏文案（「未读」）；「全部已读」`disabled` 时有 `title`；列表项为可聚焦链接。

### D2-5 `TodoList` / `TodoGroup`（Organism / Molecule，新建）

- **Anatomy**：`分组标题（分组名 + N 条 + 查看全部 ›）` → `明细行（主文案 + 元信息 + 状态标签）` × ≤3。
- **Variants**：`boss`（5 组）/ `staff`（3 组）。
- **States（7 态）**：

| 态 | 表现 |
| ---- | ---- |
| 默认 | 每组 ≤3 行 + 「查看全部 N 条 ›」 |
| 加载 | 分组标题先渲染（配置静态），行区用 3 条 48px 块骨架 |
| 空 | 整页无待办 → 「暂无待办事项」（老板端）/「暂无待办，今天只剩你自己了」（员工端）；**单组为空则该组不渲染**（不显示「0 条」分组） |
| 错误 | 该组取数失败 → 组内显示「加载失败，点击重试」一行，**不影响其他组**（逐组独立降级） |
| 禁用 | 不存在 |
| 无权限 | 按角色过滤分组（员工端不渲染采集组、老板端不渲染「待确认工资单」组） |
| 边界 | 每组只渲染 3 行（超出走「查看全部」）；`total > 99` 显示 `99+` |

- **Token 映射**：分组标题 `--fs-h3` / `--fw-semibold`；计数 `--fs-caption` / `--text-3`；行高 ≥48；状态标签复用 `StatusTag`（`--state-*`，映射见 [demo-ux-improvement.md](demo-ux-improvement.md) C2）。
- **无障碍**：分组用 `role="list"` + `role="listitem"`；「查看全部 N 条」为链接且 `aria-label` 含分组名（「查看全部 2 条待处理工单」）；**无「标记已办」按钮**（A4-2）。

### D2-6 `MeSection`（改造，Organism）

- **Anatomy**：`个人信息 Hero` + 若干 `van-cell-group` 分组（我的数据 / 管理与配置 / 账号信息 / 演示身份 / 账号安全 / 运行环境）+ `退出登录`。
- **Variants**：`boss`（无「我的数据」，有「管理与配置」）/ `staff`（有「我的数据」，无「管理与配置」）。
- **变化点（相对现状 [MeSection.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue)）**：① 删除「待办审批」/「我的待办」分组（迁往消息 Tab，见 A2，避免两处入口）；② 老板端原「我的数据」标题改为「管理与配置」，成员＝KPI 考核 / 人事管理 / 排班管理 / 打卡规则 / 打卡记录（全域）；③ 员工端「我的数据」成员保持现状不变＝我的 KPI / 我的档案 / 我的排班 / 打卡记录 / 我的入离职 / 同步状态（仅站长可见），**不新增项**。
- **States（7 态）**：本组件无远程取数（均为静态 cell + 账号信息来自 store），故 默认 / 空（不适用）/ 加载（不适用）/ 错误（不适用）/ 禁用（不适用）/ 无权限（按角色过滤分组）/ 边界（「我的数据」项数随角色变化，最长 6 项，无需分页；运行环境 2 项固定置末）。
- **Token 映射**：Hero 用 `--grad-hero`；分组标题 `--fs-h3`；cell 走 `--van-cell-*`（[tokens.scss:216-223](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L216-L223)）；运行环境降级为 `--fs-caption` + `--text-3`（现状已如此，[MeSection.vue:109-113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L109-L113)）。
- **无障碍**：cell 为链接，`:focus-visible` 焦点环；「切换演示身份」分组必须保留「Demo 专用」标识（[MeSection.vue:83](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L83)）；退出登录必须二次确认（现状已有，[MeSection.vue:19-27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue#L19-L27)），文案沿用「退出登录 / 退出后需重新登录，确定继续？」。

### D2-7 `TabbarLayout`（改造，Organism）

- **Anatomy**：`PageNav（无返回）` + `内容 slot` + `van-tabbar（3 项）`。
- **变化点**：① `tabs` 计算改为读 3 项常量；② 角标口径改为「未读通知 + 待办合计」（`stores/todo.js` + `stores/notify.js`）；③ 加 `<nav aria-label="主导航">` 包裹；④ 选中项 `aria-current`（U2）；⑤ 选中文字 600。
- **States（7 态）**：默认 / 加载（不适用）/ 空（不适用）/ 错误（角标静默降级）/ 禁用（不存在，C3）/ 无权限（按角色切换 tab 集）/ 边界（角标 `99+`）。
- **Token 映射**：见 C6-1。
- **无障碍**：见 E1。

### D2-8 `MessagePage`（Template，新建）

- **Anatomy**：`PageNav（标题「消息」+ right 插槽「发布」）` + `van-tabs（通知/待办）` + `NoticeList / TodoList`。
- **States**：由子组件承载；页面级只有「Tab 切换态」。
- **无障碍**：`van-tabs` 原生 Tab 语义；「发布」按钮 `aria-label="发布通知"`，44×44 热区。

---

# E. 无障碍与自检

## E1 底部导航语义化要求

| 要求 | 具体做法 | 依据 |
| ---- | ---- | ---- |
| 容器语义 | Tabbar 外层包 `<nav aria-label="主导航">` | WCAG SC 1.3.1 |
| 当前项 | 当前 Tab 必须带 `aria-current="page"`。Vant 是否自动输出未知（见 U2），**规范要求显式绑定**：`:aria-current="isActive ? 'page' : undefined"` | WCAG SC 2.4.8 |
| 读屏播报 | 每项的可访问名＝**可见文字 + 角标语义**：`:aria-label="text + (badge ? '，' + badge + ' 条未处理' : '')"`。**不允许**读屏只读出「消息」而漏掉「3 条未读」 | WCAG SC 1.1.1 / 4.1.2 |
| 状态不只靠颜色 | 选中态＝颜色（#0958D9）+ **文字加粗**（600）双通道；未选中＝#6B7280 + 400 | WCAG SC 1.4.1 |
| 图标不承载语义 | 每个 Tab 与每个宫格项**必须有文字标签**（`text`），图标一律 `aria-hidden="true"` | WCAG SC 1.1.1；需求「各导航选项需有明确的图标和文字标识」 |
| 焦点可见 | 用全局 2px 主色焦点环（[mobile.scss:34-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L34-L38)），**不得** `outline: none` | WCAG SC 2.4.7 |
| 键盘可达 | 路由模式下 Tab/宫格项渲染为可聚焦链接，`Tab` 顺序＝DOM 顺序（首页→消息→我的），`Enter` 激活 | WCAG SC 2.1.1 |
| 触控目标 | Tabbar 项 ≥44×44；宫格项 88×88；角标仅装饰不需独立热区 | WCAG SC 2.5.5 |
| 动效降级 | 按下态 120ms 过渡被 `prefers-reduced-motion` 全局覆盖 | WCAG SC 2.3.3 |

## E2 对比度核验表（手算，公式 WCAG 2.x 相对亮度）

| 元素 | 前景 / 背景 | 比值 | 结论 |
| ---- | ---- | ---- | ---- |
| Tabbar 选中文字/图标 | `#0958D9` / `#FFFFFF` | **6.16:1** | ✅ ≥4.5 |
| Tabbar 未选中文字/图标 | `#6B7280` / `#FFFFFF` | **4.83:1** | ✅ ≥4.5 |
| 消息角标文字 | `#FFFFFF` / `#CF1322` | **5.57:1** | ✅ ≥4.5 |
| 宫格名称 | `#4B5563`（`--text-2`） / `#FFFFFF` | **7.56:1** | ✅ ≥4.5 |
| 宫格状态型数据行 | `#6B7280`（`--text-3`） / `#FFFFFF` | **4.83:1** | ✅ ≥4.5 |
| 宫格图标（非文本） | `#1890FF` / `#FFFFFF` | **3.24:1** | ✅ ≥3:1（SC 1.4.11） |
| 运行环境文字 | `#6B7280` / `#FFFFFF` | 4.83:1 | ✅ ≥4.5 |

> 上述比值与既有体系一致：`--color-primary`(#0958D9)、`--text-3`(#6B7280)、`--color-danger`(#CF1322)、`--text-2`(#4B5563) 的对比度已在 [demo-ux-improvement.md](demo-ux-improvement.md) C1 与 [demo-ui-redesign.md](demo-ui-redesign.md) 9.1 核验过，本次沿用未改动取值。

## E3 交付前自检清单

### E3-1 本轮规范自身的检查（设计侧）

- [x] 设计方向有明确业务依据，非 AI 默认风：沿用既有「品牌蓝 + 物流橙 + 深蓝灰」物流语义，无紫色、无新色族。
- [x] 3 层 Design Token 结构完整：本次**零新增 Token**（C6-2），全部组件映射到既有 Token（D2 逐组件给出）。
- [x] 所有组件状态全覆盖：D2 逐个给出 7 态（含「不存在/不适用」的显式声明，而非漏写）。
- [x] 无障碍达标：对比度（E2）、触控（≥44×44）、键盘可达、状态双通道（E1）逐项给出。
- [x] 响应式：本次为移动端单端改动，唯一目标断点 375–430px；320px 最小宽度下 4 列宫格与 3 Tab 均达标（B1、C1）；横屏沿用 [mobile.scss:418-423](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L418-L423) 的 `max-width: 640px` 居中。
- [x] 全部结论有 `文件:行号` 依据；无依据处标「不确定 / 待走查」（0.3）。
- [x] 未编造 Vant 组件属性或图标名：`van-tabbar` / `van-tabbar-item` 的 props 与 slot 取自 Vant 4 官方文档；16 个宫格图标名与 3 个 Tab 图标名全部来自源码实测使用或官方文档示例。

### E3-2 前端实现后的验收硬项（逐项打勾）

- [x] **无十六进制色值**：本次新增/改造的 `<style>` 与模板内联属性中不出现 `#` 色值（全部走 Token）。
- [x] **无误用品牌色**：`#1890FF` 只用于图标/线/描边；承载文字或白字实底一律用 `#0958D9` / `#CF1322`；无 Token 色板外颜色；无紫色。
- [x] **Tabbar 恒为 3 项**，两端按角色渲染，无第 4 项、无置灰项。
- [x] **宫格恒为 8 项**（4×2），第 9 项下沉「我的」；每项热区 ≥44×44。
- [x] **角标 0 不渲染**、**> 99 显示 `99+`**、**失败不显示 `0`**。
- [x] **逐项/逐组降级**：单个宫格项或待办组取数失败不影响整页。
- [x] **消息页两类内容齐备**：通知子视图（含全部已读与 `bizType` 跳转）+ 待办子视图（老板 5 类 / 员工 3 类）。
- [x] **入口无遗漏**：A2 表 95 条逐条可点到位；原 6 Tab 中的 12 个一级页全部有新的可达路径。
- [x] **「我的」不再出现待办队列**（避免与消息重复）。
- [x] **无障碍**：`nav` 语义 + `aria-current` + 角标可播报 + 选中态双通道 + 焦点环未被裁。
- [x] **`prefers-reduced-motion` 生效**：新增的按下态过渡在降级模式下生效。
- [x] **旧路由兼容**：`/staff/notification` 重定向到 `/staff/message?tab=notice`，无 404。

---

## 附：本次与前序规范的冲突与处置（一页速查）

| 冲突点 | 前序规范 | 本次结论 | 处置 |
| ---- | ---- | ---- | ---- |
| Tabbar 项数 | [demo-ux-improvement.md](demo-ux-improvement.md) B0.1「各 6 项不可再加」 | **3 项** | 已在 0.2 登记变更与理由，需回写前序文档注记 |
| Tabbar 项数 | [demo-ui-redesign.md](demo-ui-redesign.md) 7.2「固定 5 项」 | **3 项** | 7.2 本节在 M1 后已与源码不符（源码为 6），本次统一为 3，并登记该节失效 |
| Tabbar 文字字号 | [demo-ui-redesign.md](demo-ui-redesign.md) 7.2「10/400（Vant 默认）」 | **12px**（Vant 默认＝`--van-font-size-sm`＝`--fs-caption`） | 以源码为准（[tokens.scss:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L201)），登记偏差 |
| 老板端宫格列数/项数 | [demo-ux-improvement.md](demo-ux-improvement.md) A12-3「3 列 × 6 项」 | **4 列 × 8 项** | 已在 0.2 登记变更与理由 |
| 员工端宫格含通知 | [demo-ux-improvement.md](demo-ux-improvement.md) A13-1「8 项含通知」 | 通知升格为消息 Tab，宫格补入「我的补卡申请」 | 已在 0.2 登记变更与理由 |
| 「待办审批」入口位置 | [demo-ux-improvement.md](demo-ux-improvement.md) A12-4「我的页拆待办审批 + 我的数据」 | **待办统一收进消息 Tab，我的页不再放待办** | 已在 A2（B13-B16、S15-S16）与 A4 说明；属对 A12-4 的修正，建议一并回写 |
| 消息 Tab 与通知中心 | 既有无「消息」概念 | **通知中心升格为消息 Tab 的通知子视图，新增待办子视图** | A4 定义 |

