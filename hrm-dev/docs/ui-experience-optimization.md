# 快递驿站智汇系统 · 三端前端体验优化专项设计方案

> 版本 v1.0 ｜ 日期 2026-09-25 ｜ 作者：UI/UX 设计师（`express-station-ui-ux-designer`）
> 定位：在不换组件库（PC = Element Plus 2.9.3 ／ 移动 = Vant 4.10.2）、不重做视觉风格、不改信息架构的前提下，把三端体验补齐到**可逐条判定**的规范水平。
> 输入：`hrm-dev/docs/requirement.md`、`demo-ui-redesign.md`、`demo-ux-improvement.md`、`demo-boss-ui-spec.md`、`demo-staff-ui-redesign.md`、现有 `hrm-dev/hrm-demo/src/**` 与 `hrm-dev/hrm-admin/src/**`
> 产出物性质：**方案阶段产物**。按 `.trae/rules/智能体调度规则.md` P0.6 / L8，本方案须先经技术评审工程师评估（结论「通过 / 有条件通过」）方可报主智能体审批；本方案不含实现代码。

---

## 0. 结论与边界

### 0.1 一句话结论

三端 Demo 的设计系统底座（Tokens、对比度、焦点环、四态组件、动效降级）**已基本建成**，本专项的价值不在"再设计一遍"，而在三件事：

1. **补齐真正缺失的**：生产 PC 端 `hrm-admin` 的四态与对比度（当前主按钮白字 ≈2.78:1，不达 AA）、移动端键盘遮挡与顶部安全区兜底、组件库非原生控件的焦点环覆盖。
2. **裁决既有规范之间的冲突**：`demo-ui-redesign.md` §8「禁止加载转圈（用骨架）」与 `demo-ux-improvement.md` B0.2「表格类用 `v-loading`」相互矛盾，导致 PC 端 40+ 处转圈与 6 处骨架并存、无判据。本方案给出「何时骨架 / 何时 loading」的可判定分界线。
3. **把规范变成可判定项**：每个维度给出测试工程师能直接断言的口径（断网看什么、双击提交数几次请求、Tab 到哪一步必须有环）。

**零新增 Token、零 Token 取值变更**（唯一涉及安全区的改动用 `max()` 在 `mobile.scss` 局部兜底，不动 `tokens.scss`）。理由见 §5。

### 0.2 本方案自我约束（硬边界）

| 约束 | 具体 |
| --- | --- |
| 不换组件库 | 继续用 Element Plus 2.9.3 / Vant 4.10.2；不完全重写任何页面 |
| 不重做视觉风格 | 色板、字阶、圆角、阴影沿用 `shared/styles/tokens.base.scss`，本方案不改取值 |
| 不改信息架构 | 不改路由结构、菜单层级、Tab 分组、字段定义 |
| 不改 Mock 与契约 | 不碰 `src/shared/mock/**`、不改任何断言基线（`verify:mock` 942 / `verify:mobile` 48 保持不动） |
| 不写实现代码 | 本文只出规范与验收口径；实现由前端工程师按规范执行 |
| 禁改清单 | `.trae/rules/**`、`hrm-dev/hrm-server/**`、`hrm-dev/sql/**`、`SESSION-STATE.md`、`api.md`、`db.md` 一律不碰 |

### 0.3 取证方式与已核实事实

本文结论全部来自仓库现有文件（路径 + 行号可核）。已实测确认的既有能力（**视为资产，不再重复建设**）：

| 能力 | 证据 | 状态 |
| --- | --- | --- |
| 跨端 Token 真源（L1/L2/通用基础 + 动效 + 降级） | [tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss) | 已建 |
| PC 四态组件（empty / error / denied + 重试出口） | [StateBlock.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/StateBlock.vue) | 已建，47 个文件接入 |
| 移动四态组件（loading + 200ms 延迟骨架 + error/empty/denied） | [PageState.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L27-L45) | 已建，42 个文件接入 |
| 全局焦点环（2px 主色 + offset 2px） | [element-overrides.scss:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/element-overrides.scss#L87-L92)、[mobile.scss:38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L37-L42) | 已建 |
| `prefers-reduced-motion` 全局降级 | [tokens.base.scss:250-259](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L248-L259) | 已建 |
| 对比度实测表（14 项全过 4.5:1） | [demo-ui-redesign.md:1510-1525](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ui-redesign.md#L1508-L1525) | 已建 |
| 触控下限 Token 44px + 宫格 e2e 断言 | [tokens.scss:64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L62-L64)、[06-viewport.spec.js:114-126](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/e2e/06-viewport.spec.js#L114-L126) | 已建 |
| `viewport-fit=cover` + `--safe-bottom` | [mobile.html:12-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/mobile.html#L12-L13)、[tokens.scss:179-181](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L177-L181) | 已建 |
| `html lang="zh-CN"`、路由同步 `document.title` | `index/pc/mobile.html:2`、[pc/router/index.js:262](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/router/index.js#L262)、[mobile/router/index.js:252](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L252) | 已建 |
| 三端 e2e 门禁（六档视口 / 网络降级 / 渲染 / 表单 / 导航） | `e2e/*.spec.js` | 已建 |

### 0.4 关键结论：`hrm-admin` 与 `hrm-demo/src/pc` 不是同一个体验基线

| 维度 | `hrm-demo/src/pc` | `hrm-admin`（生产 PC） |
| --- | --- | --- |
| Design Tokens | 有（`pc/styles/tokens.scss` + 共享真源） | **无**：`src/styles/index.scss` 全为字面量（`#303133`/`#f0f2f5`/`16px`），全工程 `grep var(--` 命中 0 \\
| 主按钮对比度 | 白字 / `#0958D9` = **6.16:1** ✅ | Element 默认 白字 / `#409EFF` = **≈2.78:1** ❌ |
| 四态 | `StateBlock` 47 文件接入 | 仅 `dashboard/index.vue:22` 用 `el-empty` 承载错误；其余页面 catch 后只弹 `ElMessage` |
| 焦点环 / 动效降级 | 有 | 无 |
| 质量门禁 | `verify:mock`/`verify:mobile`/`verify:tokens`/`build`/`lint` | 仅 `dev`/`build`/`preview`，**无 lint / 无 verify 脚本** |

且两者**样式单向耦合**：`hrm-demo/src/pc/main.js:22` 以 `@admin/styles/index.scss` 只读复用一期基座（`vite.config.js:16` 注明"只读复用，不改一期文件"）。因此：

- 改 `hrm-admin/src/styles/index.scss` → 会**同时影响 Demo PC 端**（必须同时回归两端截图）。
- 反过来 `hrm-admin` 无法引用 Demo 侧的 Token 真源（生产依赖演示工程不可接受）。

**这构成一项架构归口问题（见 §8 裁定项 R-0），不由本方案擅自决定；但"生产端对比度不合规"属硬红线，必须先修，修法给两套见 §1.5 与 §3。**

---

## 1. 六维体验规范

> 每维结构：现状取证 → 规范（可判定）→ **必做项** → 验收方式。
> 规范一律建立在既有 Token 与组件能力上，**不引入组件库不存在的 API**；凡拿不准的一律标「待核实」并给核实方法（见 §8）。

### 1.1 四态完整（loading / empty / error / normal）

#### 现状

- 移动端覆盖良好：42 个文件接入 `PageState`；未接入的仅表单页与 404（`staff/password.vue`、`workorderCreate.vue`、`boss/notificationPublish.vue`、`error/NotFound.vue`、`message/MessagePage.vue`），这些页面无列表态，**不算缺口**。
- PC（Demo）覆盖较广：47 个文件接入 `StateBlock`，但有 6 个"页面级"文件未接入：`dashboard/index.vue`、`sync/index.vue`、`schedule/index.vue`、`workOrder/index.vue`、`finance/index.vue`、`login/index.vue`。其中前三者的四态由子组件承载（可接受），但 **`schedule/index.vue` 用内联文案表达无权限**（见 `demo-ux-improvement.md` B0.2 指认的 `schedule/index.vue:490-494`），未走 `StateBlock` → 视觉与文案不统一。
- `hrm-admin` 近乎空白：错误被当作"空"渲染（`dashboard/index.vue:21-25` 的 `<el-empty :description="error">`），列表页异常只弹 toast（`utils/request.js:77`），用户无法区分"系统故障"与"业务为空"——这正是 `StateBlock.vue:6-8` 注释里已经修过一遍的老问题（P14），在生产端**尚未修**。
- 状态优先级不一致：`PageState` 的分支顺序是 `loading → denied → error → empty`（`PageState.vue:53-78`），即在"降级页同时拉取失败"时显示"无权限"，**故障被权限态掩盖**。

#### 规范

R1-1 **四态必须由组件承载，禁止页面自行 `catch` 后渲染空列表**
- PC：`StateBlock`（`variant = empty | error | denied`）；移动：`PageState`（`loading / error / empty / variant="denied"`）。
- 加载态不归这两个组件管（见 §1.2）。

R1-2 **状态判定优先级统一为：`error` → `denied` → `empty` → `normal`**
- 理由：故障必须可见，且 error 与 denied 语义不同不可互替；权限态是稳定态，等故障排除后再判权限。
- `PageState` 需把 `error` 分支前移到 `denied` 之前（当前顺序相反）。

R1-3 **文案三分，禁止混用**

| 态 | 主文案模板 | 次级说明 | 动作 |
| --- | --- | --- | --- |
| empty | `暂无{对象}`（如「暂无工单」） | 下一步引导（可选） | 可选引导按钮 |
| error | `{对象}加载失败` | `请检查网络后重试，若持续失败请联系管理员` | **必须有**「重试」 |
| denied | `暂无查看权限` | 必须写清「谁能办、去哪办」 | **不渲染任何不可用按钮** |

- **错误态文案中禁止出现"暂无""没有数据"**；空态文案中禁止出现"失败""错误""网络"。
- `error` 态容器必须带 `role="alert"`（PC `StateBlock.vue:35` 已实现；移动 `PageState.vue:65` 已实现），`denied` 用 `role="status"`。

R1-4 **边界态**（沿用 `demo-ux-improvement.md` B0.2）：超长文本省略且完整值可达（`show-overflow-tooltip` / 展开）；数量为 0 与极大值都要有明确渲染；空数组与 `null` 不得渲染成 `undefined` / `NaN`。

#### 必做项

- **【必做-1.1】** `hrm-admin` 全站接入四态（先 `dashboard`/`employee`/`station`/`department` 4 个高频页）：删除 `dashboard` 用 `el-empty` 承载错误的写法，改为 error 态 + 重试；列表页区分"请求失败（error）"与"无数据（empty）"。
- **【必做-1.2】** `PageState` 状态优先级改为 `error → denied → empty`（`PageState.vue:53-78`）。
- **【必做-1.3】** `schedule/index.vue` 内联无权限文案改走 `StateBlock variant="denied"`，与其余 47 个文件统一。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A1-1 | 断网后进入列表页（PC `/parcel`、`/sync`、`/work-order`；移动 `/staff/parcel`、`/boss/workorder`） | 出现 `.state-block--error` / `.page-state__block` 且含「重试」；**不得**出现 `.state-block--empty`、不得只弹 toast、不得空白 |
| A1-2 | 恢复网络后点「重试」 | 渲染 normal（表格/列表有数据），error 块消失 |
| A1-3 | 输入不存在的关键字查询 | 空态文案中**不含**「失败 / 错误 / 网络」；错误态文案中**不含**「暂无」 |
| A1-4 | 以 `STAFF` 账号访问同步页（预期 403） | 页面为只读降级态，且 DOM 中不存在 `button:not([disabled])` 的写操作按钮 |
| A1-5 | `hrm-admin`：断开后端（或让接口 500）后进入 dashboard / employee | 出现错误态与重试按钮，不出现"暂无数据"；`el-empty` 不再承载 error |
| A1-6 | 静态检查 | `grep -rn "el-empty.*error" hrm-admin/src` 命中 0；`schedule/index.vue` 不再出现内联无权限文案 |

---

### 1.2 骨架屏与加载反馈

#### 现状

- 移动端两套并存且各自有理由：`PageState` 用 `van-skeleton`（延迟 200ms，`PageState.vue:28`）；整块占位用 `.skeleton-block`（`mobile.scss:369-388`，1.2s 脉冲，注释明确"为什么不用 van-skeleton"）。
- PC 端两套并存且**无判据**：6 处 `el-skeleton`（dashboard 3 块 + `notification/index.vue:50` + `ConfigValueField.vue:61` + `SyncLogDrawer.vue:42`），其余 **40+ 处 `v-loading`** 转圈。
- 规范内部冲突：`demo-ui-redesign.md` §8 明写"**禁止**……加载转圈（用骨架）"，而 `demo-ux-improvement.md` B0.2 明写"表格类：表头保留 + 行区 `v-loading`"。两者并存 → 现状失控。
- 规范已有、实现缺失：§8 规定"骨架 → 内容 200ms 淡出"，`PageState.vue:53-78` 是直接切换，无过渡。
- `pc/views/parcel/index.vue:81` 自带 `TODO(扩展)` 承认"可补 8 行行骨架"。

#### 规范

R2-1 **「骨架 vs loading」分界线（本方案对既有规范冲突的裁决）**

| 场景 | 用哪种 | 理由 |
| --- | --- | --- |
| 首屏 / 整块内容替换（Hero、指标卡组、图表、看板区块、抽屉详情首屏） | **骨架屏** | 结构可预测，骨架能对齐真实高度、避免布局跳动 |
| 表格行数据刷新（表头已渲染，只有行在变） | **`v-loading`（表头保留，不整页遮罩）** | 表头是稳定信息，遮罩表头等于让人重新定位；行骨架在分页/筛选切换时会闪 |
| 单个字段 / 单元格异步（一个值、一个 tag） | **局部 loading 文本或占位** | 一块骨架换一个值，视觉重量失衡 |
| 用户主动触发的动作（提交、审核、导出、发布） | **按钮 `loading`** | 动作反馈必须绑定在触发点上 |
| 骨架延迟阈值 | 预计 **< 200ms** 不出骨架、不出 loading | 沿用 `PageState.vue:28` 的 `SKELETON_DELAY = 200`，PC 对齐同一阈值 |
| 骨架脉冲 | 1.2s 无限循环，`--ease-std` | 沿用 `mobile.scss:376` |

> 该裁决是对 `demo-ui-redesign.md` §8"禁止转圈"的**限定性修订**（转圈仅保留在"表格行刷新"与"按钮动作"两类）。请主智能体登记到 `update-log.md`，并在 `demo-ui-redesign.md` §8 加一行回指本文。

R2-2 **骨架形状规范**

| 端 | 场景 | 形状 |
| --- | --- | --- |
| PC | 列表页首屏 | 表头保留 + **8 行**行骨架；行高对齐 `--table-row-h`（44px） |
| PC | 看板区块 | 按块内真实行数取 `el-skeleton :rows`（现有范例：HeroCards=2、WorkOrderBlock=3、SyncHealthBlock=3、RankBlock=5） |
| PC | 抽屉详情 | `el-skeleton :rows="4"`（沿用 `SyncLogDrawer.vue:42` 口径） |
| 移动 | 列表 | `van-skeleton :row="4"`（`PageState` 默认 `rows=4`）；**不逐行堆骨架** |
| 移动 | 整块（Hero / 指标卡 / 图区） | `.skeleton-block`，高度由 `--row-h-2 / --row-h-3 / --row-h-tile` 派生 |

R2-3 **骨架高度必须等于真实内容高度**：高度一律由 `--row-h-*`（移动）或 `--table-row-h`（PC）派生，**禁止写死 96/108/120/148 等魔法数**（`mobile.scss:66-71` 已声明该规则，本轮把它作为验收项执行）。
R2-4 **骨架不参与读屏**：骨架容器加 `aria-hidden="true"`；加载中的容器加 `aria-busy="true"`；加载完成用现成 `role="status"` 区域播报（见 §1.5）。
R2-5 **骨架 → 内容 200ms 交叉淡出**（落地 `demo-ui-redesign.md` §8 已有规定）：透明度 1→0 / 0→1，禁止位移；`prefers-reduced-motion` 下自动降级为直接切换（全局规则已覆盖，无需额外代码）。

#### 必做项

- **【必做-2.1】** 按 R2-1 表逐页归类加载方式，产出"页面 × 加载方式"清单（覆盖 PC Demo 全部 `index.vue`、`hrm-admin` 全部 `views/**`、移动全部列表页），归类后消除同类页面两套写法。
- **【必做-2.2】** PC 列表页首屏补 8 行行骨架，兑现 `parcel/index.vue:81` 的既有 TODO；范围：`parcel`、`sync`、`workOrder`、`leave`、`attendance`、`system/logs`、`hr`（7 页）。
- **【必做-2.3】** 骨架 → 内容 200ms 交叉淡出（移动 `PageState` 优先，PC 骨架块随后）。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A2-1 | 用 CDP 限速（Fast 3G）打开 PC `/parcel` | 首屏出现**表头可见 + 行骨架**，无整块遮罩转圈；骨架高度与真实行高差 ≤2px |
| A2-2 | 同上，快速响应（Mock 延迟 120ms 内）场景 | 骨架不出现（200ms 阈值生效），直接出内容，无闪烁 |
| A2-3 | 表格内切分页 / 切筛选 | 表头始终保留，仅行区 `v-loading`；无整页遮罩 |
| A2-4 | 提交类动作（PC 新建工单 / 移动提交工单） | 按钮进入 `loading` 且 `disabled`，页面其他内容不进入骨架 |
| A2-5 | 开启 `prefers-reduced-motion: reduce` | 骨架无脉冲残留、无交叉淡出，仍然可读（块底色在） |
| A2-6 | 读屏（DevTools Accessibility 树） | 骨架节点不出现在可读节点中（`aria-hidden`）；加载容器 `aria-busy="true"` |
| A2-7 | 静态检查 | `grep -rn "height: *1[0-9][0-9]px\|height: *9[0-9]px" src/mobile src/pc` 中骨架块位置命中 0（不含图表内部尺寸） |

---

### 1.3 动效与微交互

#### 现状

- Token 齐备：`--dur-fast` 120ms / `--dur-base` 200ms / `--dur-slow` 300ms；`--ease-std/in/out`（`tokens.base.scss:240-245`）。
- 全局降级齐备：`@media (prefers-reduced-motion: reduce)` 把 `animation-duration` / `transition-duration` 压到 0.01ms（`tokens.base.scss:250-259`）。
- Token 消费率良好：`var(--dur-*|--ease-*)` 命中 57 处 / 21 文件；裸写 ms 仅 **9 处 / 7 文件**（清单见 §2 缺陷 14）。
- 已有动效：PC 路由进度条 300ms（`element-overrides.scss:106-127`，挂载于 `pc/layout/index.vue:81`）；列表返回高亮 1600ms（`mobile.scss:299-312`）；Tabbar 文字/底色 120ms（`TabbarLayout.vue:135-146`）。
- 缺失：骨架→内容淡出（§1.2 R2-5）；移动端路由切换无过渡（可接受，见 R3-2）。
- 局部重复：`FlowSteps.vue`、`BossShareBar.vue`、`PayrollStatusSteps.vue` 三处各自写了 `prefers-reduced-motion` 媒体查询，与全局规则重叠（是否冗余待核实，见 D-3）。

#### 规范（时长/缓动参数表）

| 场景 | 时长 | 缓动 | 实现载体 |
| --- | --- | --- | --- |
| 按钮 / 卡片按下 | 120ms | `--ease-std` | 底色或 `scale(.985)`（沿用 §8） |
| 卡片 hover（PC） | 120ms | `--ease-std` | `border-color` + `--e2`；**禁止缩放卡片**（会引起表格行错位） |
| Tab 指示器切换 | 200ms | `--ease-std` | 下划线位置/宽度 |
| 分组展开 / 折叠 | 200ms | `--ease-std` | 高度过渡 + 箭头旋转 180° |
| 抽屉 / 弹层 | 300ms | 进入 `--ease-in` / 退出 `--ease-out` | 保留 Element / Vant 默认，不覆写 |
| 骨架 → 内容 | 200ms | `--ease-std` | 透明度交叉淡出（无位移） |
| 骨架脉冲 | 1.2s 循环 | `--ease-std` | 透明度 1 ↔ 0.45 |
| 列表进入（首屏） | **不做** | — | 20 万数据场景下错峰动画拖慢可交互时间 |
| 数值变化 | **不做滚动** | 120ms 颜色 | 涨绿跌红仅颜色变化 |
| 高亮提示（新建返回） | 1600ms | `--ease-out` | 底色 + 2px 描边（颜色 + 动效双通道） |
| PC 路由切换 | 300ms | `--ease-out` | 顶部 2px 进度条；**不做页面位移过渡** |
| 移动路由切换 | **不做** | — | 保持 `scrollBehavior: top:0`；位移过渡易与手势返回冲突 |
| 下拉刷新 | Vant 内建 | — | 不覆写 |

R3-1 **Toast 时序**：成功 2000ms ／ 失败 3000ms ／ 纯提示 2000ms；同一时刻最多 1 条（新 Toast 顶替旧 Toast，不叠层）；错误 Toast 允许手动关闭。Vant 的具体选项名与默认值以 `node_modules/vant/es/toast/` 产物为准（**待核实 D-2**），核实前不得写入实现。

R3-2 **禁止清单**：自动播放动画、视差滚动、装饰动画 > 400ms、列表逐项入场延迟、为"等动画结束"而用 JS `setTimeout` 串联状态（reduced-motion 下会时序倒挂）。

#### 如何尊重 `prefers-reduced-motion`

1. **不新增任何降级代码**：全局规则已覆盖（`tokens.base.scss:250-259`）。
2. **新增动效一律消费 `--dur-*` / `--ease-*`**，不写裸 ms —— 这样自动被全局降级覆盖。这既是 §1.3 规范，也是 9 处裸 ms 的收敛依据。
3. **不用 JS 定时器承载动画时序**（同 R3-2）。
4. 降级后的语义完整性：降级只允许"去掉动效"，**不允许丢信息**。`mobile.scss:289-296` 的 `.list-item--marked` / `--failed` 用"颜色 + 位置双通道"表达状态，动效消失后仍可辨 —— 该模式作为规范：凡用动效表达状态处，必须有静态等价物。

#### 必做项

- **【必做-3.1】** 新增/修改的动效 100% 消费 `--dur-*` / `--ease-*`；顺手收敛 7 个文件里的 9 处裸 ms。
- **【必做-3.2】** Toast 时序三档口径落地（成功 2000 / 失败 3000 / 提示 2000），核实 Vant 选项名后统一到一处封装（禁止各页各写 `duration`）。
- **【必做-3.3】** 骨架 → 内容 200ms 淡出（与 §1.2 必做-2.3 同一项，此处为动效侧约束）。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A3-1 | DevTools 打开 Animations 面板，录制：展开分组 / 切换 Tab / hover 卡片 / 打开抽屉 | 实际时长落入上表 ±20ms；缓动为对应 cubic-bezier |
| A3-2 | 连续触发两次失败操作 | 同屏只有 1 条 Toast；失败 Toast 停留 ≈3000ms、成功 ≈2000ms（用 `performance.now()` 打点或录屏计时） |
| A3-3 | 勾选 `prefers-reduced-motion: reduce` 后重放 A3-1 全部动作 | 无位移/缩放动画（`getComputedStyle().transitionDuration` ≤ 0.01s）；**信息不丢**（状态仍可辨） |
| A3-4 | 静态检查 | UI 组件样式内 `\d+ms` 裸值命中 0（`tokens.scss` 除外）；`grep -rn "setTimeout.*animation\|transitionend"` 无"用 JS 等动画"的写法 |
| A3-5 | PC 路由切换 | 顶部进度条出现并在 ≤300ms 内走完，页面内容不位移 |

---

### 1.4 表单体验

#### 现状

- PC 校验时机已成惯例：37 处 `rules` 命中，文本用 `trigger: 'blur'`、选择类用 `trigger: 'change'`（如 `CreateWorkOrderDialog.vue:43-48`、`RuleCard.vue:86-93`、`login/index.vue:53-61`）——**基本符合规范，作为基线不动**。
- 移动端**未显式声明校验时机**：全工程 `grep validate-trigger` 命中 0，依赖 Vant Form 默认值（**待核实 D-4**）。
- 防重复提交已普遍实现：移动端 `submitting` 守卫 + `ActionBar :submitting`（`ActionBar.vue:33/55/68/88`）；PC 有 `useSilentSubmit` composable。→ 作为规范固化，不新建机制。
- 成功反馈：PC `ElMessage.success`；移动 `showSuccessToast`。文案不统一（有的「已保存」、有的「保存成功」）。
- 未确认的破坏性操作有二次确认（`demo-ux-improvement.md` B0.3 清单：发布通知、发布工资单、一键铺排覆盖、批量删除等）。

#### 规范

R4-1 **必填标记**：PC 用 `el-form-item required`（Element 内建星号）；移动用 Vant `Field` 的必填标记（**待核实 D-4**：Vant 4 是否内建 `required` 星号；若无，则用 label 后缀 `<span class="required-mark">*</span>` + `--color-danger`，并在表单顶部加一行"带 * 为必填"说明）。**禁止**只在 placeholder 里写"必填"。
R4-2 **校验时机（统一口径，覆盖三端）**

| 控件类型 | 首次触发 | 补充 |
| --- | --- | --- |
| 文本 / 数字输入 | **`blur`（失焦）** | 提交时全量校验 |
| 下拉 / 单选 / 多选 / 日期 / Picker | **`change`** | — |
| 全部 | **提交前 `validate()` 全量校验**，失败则聚焦并滚动到**第一个**错误项 | PC 用 Element `scrollToField` 能力（**待核实** API 名）；移动用 `scrollIntoView({ block:'center' })`（已有先例：`NoticeList.vue:164`） |
| **禁止** | `input` 实时校验 | 中文输入法组合期会误报；长表单输入中途报错干扰 |

R4-3 **错误文案规范**
- 必填：`请{动词}{对象}` → 「请选择归属驿站」「请填写工单标题」。
- 格式：`{对象}{约束}` → 「标题长度须为 1-100 字符」。
- 长度 ≤ 20 字、不以句号结尾、不出现字段英文名/字段名代码/技术术语。
- **不原样透传后端 message**（除已是用户可读文案）；后端技术性错误统一转成「操作失败，请稍后重试」+ 原始信息进客户端日志。
- **不做顶部错误汇总**（避免同一错误出现两处）。
- 校验失败时**禁止清空用户已填内容**。

R4-4 **提交中与防重复**
- 主按钮同屏只允许一个：`loading` + `disabled`（移动端由 `ActionBar :submitting` 承担）。
- 提交函数首行守卫：`if (submitting) return`（沿用现有惯例，固化为规范）。
- 网络失败：按钮恢复可点 + 失败反馈（PC `ElMessage.error` / 移动 `showFailToast`），**保留表单内容**，焦点回到出错字段（若可定位）。
- 超时/成功判定：不做前端乐观成功，等接口返回。

R4-5 **成功反馈**
- 就地 Toast / `ElMessage.success`，文案统一为「已{动词}」（「已保存」「已发布」「已提交」），**不出现"成功！""操作完成"这类无信息文案**。
- 列表页新建/编辑返回后，目标行高亮 1600ms（沿用 `.is-highlight`）。
- 不可逆操作二次确认：沿用 B0.3 清单与文案规范（标题用动词短语、正文说清影响范围与不可逆性、按钮用具体动词，不用"确定/取消"）。

#### 必做项

- **【必做-4.1】** 移动端显式声明校验时机（文本 `onBlur` / 选择类 `onChange`），与 PC 口径对齐；核实 Vant 配置项名后统一封装。
- **【必做-4.2】** 成功文案统一为「已{动词}」；失败文案统一为「{动作}失败，请稍后重试」；两处各出替换清单。
- **【必做-4.3】** 提交前全量校验 + 聚焦第一个错误项（PC / 移动各一套），补到所有含 ≥3 个字段的表单页。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A4-1 | 文本字段：输入非法值后**不离开**输入框 | 此时**不报错**；失焦后才报错；再次输入并失焦，错误提示实时更新 |
| A4-2 | 点提交且有多项为空 | 全量校验一次报全；焦点落在**第一个**错误项且该元素在视口内（`getBoundingClientRect()` 不越界） |
| A4-3 | 双击提交按钮（间隔 <100ms） | 后端只收到 **1 次**请求（用 Mock 请求计数或网络面板计数断言） |
| A4-4 | 提交中断网 | 按钮恢复可用、表单内容不变、出现失败反馈；不出现"已成功" |
| A4-5 | 静态检查 | 表单校验规则中不存在 `trigger: 'input'` / `validate-trigger="onInput"`；错误文案库中无句号结尾、无 20 字以上 |
| A4-6 | 成功反馈 | 文案匹配 `^已` 且不含"成功"/"操作完成"（`grep` 断言） |

---

### 1.5 无障碍与对比度

#### 现状

- **Demo 侧对比度已成体系**：14 项实测全过 4.5:1（`demo-ui-redesign.md:1508-1525`）；主色取 700 档因白字 6.16:1；`--border-control` 3.10:1 满足 SC 1.4.11；`--gauge-fill` 5.388:1；`--text-3` 在白底 4.83:1、在 `--surface-page` 上仅 4.504:1（**无余量**，`mobile.scss:142-143` 已注明）。
- **`hrm-admin` 对比度不合规**（实测手算，公式 = WCAG 2.x 相对亮度，误差 ±0.05）：

| 场景 | hrm-admin 现值 | 比值 | Demo 现值 | 比值 |
| --- | --- | --- | --- | --- |
| 主按钮（实底白字） | `#409EFF`（Element 默认） | **≈2.78:1** ❌ | `#0958D9` | 6.16:1 ✅ |
| 成功按钮 | `#67C23A` | **≈2.24:1** ❌ | `#237804` | 5.59:1 ✅ |
| 警告按钮 | `#E6A23C` | **≈2.19:1** ❌ | `#B45309` | 5.62:1 ✅ |
| 危险按钮 | `#F56C6C` | **≈2.90:1** ❌ | `#CF1322` | 5.57:1（实心标签实测） ✅ |
| 次级说明文字 `.form-tip`（12px） | `#909399`（`index.scss:59`） | **≈3.08:1** ❌ | `--text-3` `#6B7280` | 4.83:1 ✅ |

- **焦点环覆盖不全**：选择器为 `:where(a, button, input, select, textarea, [tabindex]):focus-visible`。组件库中**非原生可点控件**（Vant `.van-cell`、`.van-tabbar-item` 已含 tabindex；Element 的 `el-select__wrapper`、`el-checkbox`、`el-switch`、`el-radio` 等）若内部无 `tabindex`，键盘 Tab 到其上时**看不到焦点环**。
- **aria 覆盖偏稀疏**：`aria-label|aria-describedby|role="alert|status"` 全 `src` 命中约 56 处 / 30 文件（工程约 200 个 vue/js 文件）。已有较好先例：`pc/layout/index.vue:43-56`（`aria-expanded` / `aria-haspopup` / `aria-label`）、图表 `aria-label` + `.visually-hidden` 数据表（`TrendChart.vue` / `LineChart.vue`，e2e A5-1 已断言）。
- **已有的已知偏差（不重复提）**：占位符 `#9AA4B2` 2.52:1，`demo-ui-redesign.md` §9.3 D-1 已登记为接受项（理由：表单标签始终可见）。**但该接受有前提**——见 R5-6。
- 移动端 `viewport` 含 `maximum-scale=1.0, user-scalable=no`（`mobile.html:12-13`）→ **禁止缩放，违反 WCAG 2.1 SC 1.4.4（文本可放大 200%）**，属已存在的合规缺口。

#### 规范（检查清单）

R5-1 **对比度阈值**：正文与其背景 ≥ **4.5:1**；大字号（≥24px，或 ≥18.66px 且字重 ≥600）≥ **3:1**；非文本内容（描边、图标、图表线、焦点环）≥ **3:1**。
R5-2 **实底色规则**（项目硬规则，沿用）：500 档只做图标/线/描边；承白字的实底一律 700 档；hover/active 加深不浅化。
R5-3 **单点来源**：任何新增/修改颜色必须先算比值、后落地，比值写入本文档附录并在 CR 中说明；`src/**` 出现十六进制字面量仅允许在 `tokens.*.scss` 内（沿用 `demo-ui-redesign.md` §9.2 首条）。
R5-4 **`--text-3` 使用限制**：因在 `--surface-page` 上仅 4.504:1（无余量），禁止在 `--surface-page` 底上以 `--text-3` 承载关键信息，且禁止对其叠加透明度或再置于更浅底色上。
R5-5 **焦点可见**：所有可交互元素（含组件库非原生控件）在 `focus-visible` 下必须有 2px `--color-primary-icon` 环 + offset 2px。**禁止 `outline: none`**（PC 已有删除先例并留注释，见 `pc/layout/index.vue:55`）。
R5-6 **占位符偏差前提校验**：D-1 的接受前提是"每个输入都有始终可见的 label"。若出现**仅靠 placeholder 说明字段**的输入框，则该输入不合规，必须补 label（或改深占位色）。
R5-7 **aria / 语义清单**：

| # | 要求 | 判定 |
| --- | --- | --- |
| a | 仅有图标无文字的可点元素必须有 `aria-label` | `button` / `a` / `[role=button]` 内无文本节点 → 必须含 aria-label |
| b | 纯装饰图标与骨架 `aria-hidden="true"` | 骨架块不得出现在可读节点树 |
| c | 错误态 `role="alert"`；异步结果（提交成功/失败）在 `aria-live` 区域播报 | `StateBlock.vue:35`、`PageState.vue:65` 已达标；Toast 的读屏行为**待核实 D-2** |
| d | 表格有可读表头；纯图标列有 `aria-label` | Element 内建 `<th>`；演示端 `el-table` 需抽查 |
| e | 图表：SVG `aria-label` 摘要 + `.visually-hidden` 数据表 | 沿用现有做法，新增图表同样要求 |
| f | 表单 label 与输入关联（`for`/`id` 或包裹） | Element / Vant 内建需实测确认 |
| g | 页面标题随路由更新 | 已达标（`pc/router/index.js:262`、`mobile/router/index.js:252`） |
| h | `html lang` 正确 | 已达标（三入口均 `zh-CN`） |

R5-8 **键盘可用性**：登录 → 列表 → 详情 → 提交全流程可仅用键盘完成；Tab 可进出 Modal/Drawer（无键盘陷阱）；Esc 可关闭最上层弹层；关闭后焦点回到触发源。
R5-9 **触控热区（移动）**：所有可点元素 ≥ **44×44 CSS px**（`--touch-min`）。纳入范围：Tabbar 项、NavBar 返回键、chip、列表项内图标按钮、表单行内按钮、FAB、Picker 触发区。
R5-10 **缩放**：`user-scalable=no / maximum-scale=1.0` 与 SC 1.4.4 冲突，处置见 §8 裁定项 R-2（涉及 Android H5 壳手势行为，需主智能体裁定）。

#### 必做项

- **【必做-5.1】** `hrm-admin` 对比度修复：主按钮与四类功能色按钮（含 hover/active/disabled 态承白字）、`.form-tip` 次级文字，全部达到 ≥4.5:1。取值可直接对齐 Demo 已验证值（`pc/styles/tokens.scss:79-117` + `element-overrides.scss:8-20` 是已验证实现，可照此口径落地），但**归口问题见 R-0**。
- **【必做-5.2】** 焦点环覆盖补全：扩展选择器或给组件库非原生可点控件补 `tabindex`，确保 R5-5 达成；`grep -rn "outline: *none"` 必须为 0。
- **【必做-5.3】** 触控热区扫描：把 e2e B1-3 的宫格断言扩展到全部移动可点元素（清单见 R5-9）。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A5-1 | Playwright 取 `getComputedStyle` 的 color + 背景色，计算 WCAG 比值；抽样 ≥12 点（登录页、侧栏选中项、表头、六态标签、空/错误态文案、Toast、骨架块、禁用按钮、`hrm-admin` 四个功能色按钮） | 全部达标：正文 ≥4.5:1，大字号/非文本 ≥3:1；输出比值表存档 |
| A5-2 | 键盘 Tab 遍历：PC 侧栏 → 用户菜单 → 表格操作列 → 抽屉表单；移动 Tabbar → 列表项 → ActionBar | 每一步都有**可见**焦点环；`focus-visible` 环颜色 = `--color-primary-icon` |
| A5-3 | 用 `Keyboard` 打开 Modal / Drawer，按 Esc | 弹层关闭且焦点回到触发按钮；Tab 不逃逸到背景页 |
| A5-4 | 375 视口全量扫描可点元素 | 无 width 或 height < 44px 的元素（`getBoundingClientRect`） |
| A5-5 | 静态检查 | `grep -rn "outline: *none" src` = 0；`grep -rn "#[0-9a-fA-F]\{3,6\}" src/pc src/mobile` 仅命中 `tokens` 文件；`hrm-admin/src` 无未登记的字面量色值 |
| A5-6 | 占位符前提校验（R5-6） | 全站输入框均有始终可见 label；不存在"仅 placeholder 说明字段"的输入 |
| A5-7 | 可读节点抽查（Accessibility 树） | 图标按钮有可读名称；骨架/装饰图标不出现；错误态为 `alert` 角色 |

---

### 1.6 移动端适配与安全区

#### 现状

- `viewport-fit=cover` 已开（`mobile.html:12-13`）；`--safe-bottom: env(safe-area-inset-bottom, 0px)` 已定义（`tokens.scss:181`），`van-tabbar` 用 `safe-area-inset-bottom`（`TabbarLayout.vue:90`），固定底栏留白由 `--page-pad-bottom = actionbar 56 + safe-bottom + 8` 派生（`tokens.scss:187-188`）。→ **底部安全区已达标**。
- **顶部安全区未兜底**：`--safe-top: var(--status-bar-height, 0px)`，而 `--status-bar-height` 默认 `0px`（`tokens.scss:179-180`），只由 Android H5 壳经 `bridge.js` 注入。**非壳环境（手机浏览器 / PWA / 横屏刘海）无 `env(safe-area-inset-top)` 兜底**，NavBar 贴到 0 位置。
- **横屏左右安全区缺失**：全工程无 `env(safe-area-inset-left/right)`。横屏刘海机型上 `#app` 与固定栏可能被圆角/刘海裁切。
- 宽屏兜底已有：`#app max-width: 480px`，横屏放宽到 640px（`mobile.scss:31-35`、`446-453`），固定栏同步收口。
- **键盘遮挡无任何处理**：全 `src` 无 `visualViewport`、无 `scroll-padding-bottom`、无输入聚焦滚动兜底 → 表单页（`staff/leaveApply.vue`、`staff/workorderCreate.vue`、`staff/password.vue`、`MakeupPopup.vue`）键盘弹起时输入框可能被遮。

#### 规范

R6-1 **顶部安全区兜底（零 Token 变更）**：在 `mobile.scss` 对固定 NavBar 用 `max()` 取两侧较大值，不动 `tokens.scss` 取值。规范片段（示意，供前端实现）：

```scss
/* 壳内注入 status-bar-height>0 时取壳值；浏览器/PWA 下用 env 兜底，两者取大者 */
.van-nav-bar--fixed {
  top: max(var(--safe-top), env(safe-area-inset-top, 0px));
}
```

- 同时 `#app` 的 `padding-top` 需用同一表达式，保证内容与 NavBar 同步下移（否则出现覆盖）。
- 影响面：`--status-bar-height` 为 0 的浏览器环境才会变化（正是需要修复的群体）；Android 壳内取 `max(44, 0) = 44` 与现状一致 → **壳内零回归**。

R6-2 **横屏左右安全区**：`#app` 与三类固定栏（`.van-nav-bar--fixed` / `.van-tabbar--fixed` / `.actionbar`）增加
`padding-left: env(safe-area-inset-left, 0px)`、`padding-right: env(safe-area-inset-right, 0px)`（竖屏下 env 为 0，无副作用）。

R6-3 **键盘弹起不遮挡聚焦输入**（目标，实现路径由前端选择）：
- 判定目标：键盘弹起（视口高度缩小）后，**当前聚焦的输入框完整可见**，底部操作栏不遮挡输入框。
- 可选路径（按风险从低到高，前端择一并记录）：
  1. 依赖 Vant 内建的键盘适配（**待核实 D-5**：查 `node_modules/vant/es/field`、`es/popup` 是否含 `visualViewport` 逻辑）；
  2. 聚焦时 `scrollIntoView({ block: 'center' })`（已有先例 `NoticeList.vue:164`）；
  3. 监听 `window.visualViewport` 的 `resize`，把视口高度差作为底部占位（需自行接管，注意 reduced-motion 无关但需防抖）。
- **禁止**用固定大 `padding-bottom` 硬撑（会在无键盘时留下大片空白）。

R6-4 **横竖屏**：不重排布局，仅靠 480 / 640 兜底 + 安全区；横屏下表格/长列表仍不得出现横向滚动。

#### 必做项

- **【必做-6.1】** 顶部安全区 `max()` 兜底（R6-1），壳内行为不变。
- **【必做-6.2】** 键盘遮挡修复：至少覆盖 4 个表单页（`leaveApply`、`workorderCreate`、`password`、`MakeupPopup`）。
- **【必做-6.3】** 横屏左右安全区（R6-2）+ 横屏无横向滚动纳入 e2e 视口档。

#### 验收方式

| # | 判定动作 | 通过条件 |
| --- | --- | --- |
| A6-1 | 浏览器直开 `mobile.html`（无壳） | `getComputedStyle(.van-nav-bar--fixed).top` 不为负；内容区首元素不被 NavBar 覆盖（元素 `getBoundingClientRect().top ≥ NavBar 底边`） |
| A6-2 | 模拟壳注入 `--status-bar-height: 44px` | NavBar top = 44px，内容同步下移；与现状截图一致（零回归） |
| A6-3 | 812×375 横屏档 | 无横向滚动（`scrollWidth - clientWidth ≤ 1`）；固定栏左右不越界；左右 padding 生效 |
| A6-4 | 表单页：聚焦底部输入框后把 viewport 高度缩小 300px（模拟键盘） | 聚焦输入框 `getBoundingClientRect().bottom ≤ 视口高度`；底部操作栏不遮挡该输入框 |
| A6-5 | 收起"键盘"（恢复 viewport 高度） | 页面无大片空白残留（底部 padding 复原） |
| A6-6 | 375×812 全站抽查 | 无横向滚动；底部末元素不被 Tabbar/ActionBar 遮挡（`--page-pad-bottom` 生效） |
| A6-7 | 真机（iPhone 刘海机型 / Android 挖孔屏） | 顶部状态栏文字不压字、底部手势条不压按钮 —— **人工走查项**，写入视觉走查报告（无法自动化） |

---

## 2. 现有体验缺陷清单（按严重度排序）

> 严重度判据：**严重** = 合规红线或用户被误导/被阻断；**高** = 高频路径可用性受损或规范自相矛盾；**中** = 一致性与预期偏差；**低** = 打磨项。

| # | 严重度 | 缺陷 | 证据（文件:行） |
| --- | --- | --- | --- |
| 1 | **严重** | `hrm-admin` 生产 PC 无四态体系：错误被渲染成"空"，用户把系统故障误判为业务为空 | [dashboard/index.vue:21-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/dashboard/index.vue#L20-L25)（`<el-empty :description="error">`）；列表页仅 [request.js:77](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/utils/request.js#L77) 弹 toast |
| 2 | **严重** | `hrm-admin` 主色/功能色按钮白字对比度不达 AA（实测 ≈2.19–2.90:1），次级文字 `#909399` ≈3.08:1 | Element 默认色未覆写（`hrm-admin/src/styles/index.scss` 全文无颜色变量）；对照 Demo 已验证值 [tokens.scss:79-117](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L79-L117) |
| 3 | **严重** | `hrm-admin` 未接入 Design Tokens（全工程 `var(--` 命中 0），两套 PC 端视觉基线从此分叉 | `hrm-admin/src/styles/index.scss:1-60` 全字面量；`hrm-demo/src/pc/main.js:22` 反向只读引用一期基座 → 单向耦合 |
| 4 | **高** | 加载反馈两套并存且规范自相矛盾：§8 禁止转圈 vs B0.2 允许表格 `v-loading` | [demo-ui-redesign.md:1502](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ui-redesign.md#L1502) vs [demo-ux-improvement.md:236](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ux-improvement.md#L236)；现状 40+ `v-loading` / 6 `el-skeleton` |
| 5 | **高** | 移动端状态优先级把 `denied` 放在 `error` 之前，故障可能被"无权限"掩盖 | [PageState.vue:59-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L59-L72) |
| 6 | **高** | 键盘可达性缺口：组件库非原生可点控件不在焦点环选择器内，Tab 到其上无视觉落点 | [element-overrides.scss:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/element-overrides.scss#L87-L92)、[mobile.scss:38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L37-L42) |
| 7 | **高** | 移动端无键盘遮挡处理，4 个表单页存在输入框被遮风险 | 全 `src` 无 `visualViewport` / `scroll-padding-bottom`；受影响页 `staff/leaveApply.vue`、`workorderCreate.vue`、`password.vue`、`MakeupPopup.vue` |
| 8 | **高** | 顶部安全区未兜底（仅取壳注入值，默认 0px），非壳/横屏刘海环境 NavBar 贴顶 | [tokens.scss:179-181](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L177-L181) |
| 9 | **高** | 移动端 `user-scalable=no` 禁止缩放，违反 SC 1.4.4 | [mobile.html:12-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/mobile.html#L12-L13) |
| 10 | **中** | 骨架 → 内容 200ms 淡出规范已定但未实现 | 规范 [demo-ui-redesign.md:1495](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ui-redesign.md#L1495)；实现 [PageState.vue:53-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/PageState.vue#L53-L78) 为直接切换 |
| 11 | **中** | PC 页面级四态不统一：`schedule/index.vue` 用内联文案表达无权限 | `demo-ux-improvement.md` B0.2 指认 `schedule/index.vue:490-494`；该文件未 import `StateBlock` |
| 12 | **中** | 骨架预算未被约束：`.skeleton-block` 高度由调用方写死，行高族未强制消费 | [mobile.scss:369-388](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss#L369-L388)；`--row-h-*` 已定义于 [tokens.scss:66-71](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/tokens.scss#L66-L71) |
| 13 | **中** | 下拉刷新覆盖不全（仅 2 页），手势预期不一致 | [NoticeList.vue:210](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/NoticeList.vue#L210)、[staff/workorder.vue:133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue#L133)；其余列表页无 |
| 14 | **中** | 长列表返回不恢复滚动位置，一律回顶 | [mobile/router/index.js:228](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L228)（`scrollBehavior: () => ({ top: 0 })`） |
| 15 | **中** | Toast 时序无统一口径（50+ 调用点，无 duration 约定） | 全 `src` 命中 `showToast/...` 50+ 处（`boss/schedule.vue`、`payrollDetail.vue`、`notificationPublish.vue` 等） |
| 16 | **中** | aria 覆盖偏稀疏（≈56 处 / 30 文件 / 全工程约 200 文件） | 静态统计；对照 e2e A5-1 已断言的图表 `aria-label` 为良好先例 |
| 17 | **低** | 动效裸 ms 残留 9 处 / 7 文件，绕过 `--dur-*` 后不被 reduced-motion 覆盖 | `LineChart.vue`、`StationPicker.vue`、`PayrollStatusSteps.vue`、`BossShareBar.vue`(2)、`BossRankBar.vue`、`TrendChart.vue`、`mobile.scss`(2) |
| 18 | **低** | 3 处局部 `prefers-reduced-motion` 与全局重复 | `FlowSteps.vue`、`BossShareBar.vue`、`PayrollStatusSteps.vue`（是否必要待核实 D-3） |

---

## 3. 优先级与分批实施

### 3.1 优先级定义

| 级别 | 含义 | 判据 |
| --- | --- | --- |
| **P0** | 合规红线 / 用户被误导 / 移动端真机不可用 | 不修则违反 WCAG AA 或核心路径断裂 |
| **P1** | 一致性缺失 / 规范未落地 / 高频路径体验差 | 不修则同类页面两套行为，验收不可判定 |
| **P2** | 打磨与收敛 | 不影响正确性，影响精致度与长期可维护性 |

### 3.2 分批实施（每批可独立验收、可独立回滚）

#### 批次 A ——「合规与真机可用性闸门」（P0）

范围：
1. `hrm-admin` 四态接入（必做-1.1）+ 对比度修复（必做-5.1）—— **先做 `dashboard`，验证通过后再铺 `employee`/`station`/`department`**。
2. `PageState` 状态优先级（必做-1.2）。
3. 移动端顶部安全区兜底（必做-6.1）+ 键盘遮挡（必做-6.2）。
4. 焦点环覆盖补全（必做-5.2）。

验收：§1.1 A1-1/A1-5、§1.5 A5-1/A5-2、§1.6 A6-1/A6-2/A6-4。
回滚点：`hrm-admin/src/styles/**` 与 `hrm-admin/src/views/dashboard/**` 单点回滚；Demo 侧 `PageState.vue`、`mobile.scss`、两个 `styles/*.scss` 单点回滚。

#### 批次 B ——「加载与反馈一致性」（P1）

范围：骨架/loading 归类（必做-2.1/2.2）、骨架高度约束（R2-3）、骨架→内容淡出（必做-2.3）、Toast 时序（必做-3.2）、裸 ms 收敛（必做-3.1）。

验收：§1.2 A2-1～A2-7、§1.3 A3-1～A3-5。

#### 批次 C ——「表单与手势预期」（P1）

范围：移动端校验时机（必做-4.1）、成功/失败文案统一（必做-4.2）、提交前全量校验 + 聚焦（必做-4.3）、下拉刷新覆盖（缺陷 13）、返回滚动位置恢复（缺陷 14）。

验收：§1.4 A4-1～A4-6、缺陷 13/14 的定向断言（见 §4）。

#### 批次 D ——「可访问性补全与收敛」（P2）

范围：aria 清单落地（R5-7 全表）、触控热区全量扫描（必做-5.3）、局部 reduced-motion 清理（缺陷 18）、边界态复核（R1-4）、占位符前提校验（R5-6）。

验收：§1.5 A5-3～A5-7、§1.1 A1-6。

### 3.3 门禁不变式（每批都必须成立）

| 门禁 | 基线 | 本方案要求 |
| --- | --- | --- |
| `npm run verify:mock` | **942** | 本方案不碰 Mock/契约，数字**必须仍是 942** |
| `npm run verify:mobile` | **48** | 同上，**必须仍是 48** |
| `npm run verify:tokens` | 通过 | 本方案零 Token 新增、零取值变更 → 必须继续通过 |
| `npm run build` / `build:prod` | 通过 | 通过 |
| `npm run lint` | 通过 | 通过（含 `eslint-plugin-vuejs-accessibility`） |
| `hrm-admin` | `npm run build` | 通过（**该工程无 lint / 无 verify 脚本**，见 R-1） |

> **禁止**为让门禁变绿而修改断言或基线数字（反模式 A06）。新增验证只能**新增用例文件**（e2e 新增 spec 或新增独立脚本），不得改动既有断言。

---

## 4. 验收方式（测试工程师口径）

本节把 §1 的验收项汇总为可执行清单。统一断言口径：

1. **可见性**：`locator(...).toBeVisible()` + 元素在视口内（`getBoundingClientRect()` 不越界）。
2. **状态互斥**：同一时刻页面上 `.state-block--empty` / `--error` / `--denied`、`.page-state__block` 只允许出现 1 个。
3. **文案判定**：错误态文本 ∌ {暂无, 没有数据}；空态文本 ∌ {失败, 错误, 网络}。
4. **对比度**：取 `getComputedStyle` 的 `color` / `background-color`，按 WCAG 2.x 相对亮度公式计算，容差 ±0.05。
5. **热区**：`getBoundingClientRect()` 的 width/height 均 ≥ 44（移动端），≥ 24（PC 端可点行内控件，沿用项目 PC 最小语义）。
6. **请求计数**：用 Mock 适配器计数或 `page.on('request')` 统计，用于防重复提交断言。
7. **红色用例（必须失败的写法）**：断网后出现空态、错误态无重试按钮、双击发两次请求、Tab 到组件库非原生控件无焦点环、骨架块出现魔法高度 —— 任一条命中即判不通过。

**建议新增 e2e 用例（新增文件，不改既有）**：
- `08-state.spec.js`：四态互斥与文案三分（PC + 移动 + `hrm-admin`）。
- `09-a11y.spec.js`：对比度抽样、焦点环遍历、44px 热区扫描、可读节点抽查。
- `10-mobile-adapt.spec.js`：安全区、横屏、键盘遮挡（viewport 缩小法）。

---

## 5. Token 使用纪律（本方案零新增、零改值）

**结论：本方案不新增任何 Design Token，也不修改任何 Token 取值。**

| 本方案需要的东西 | 用什么承载 | 是否新增 Token |
| --- | --- | --- |
| 骨架高度 | `--row-h-1/2/3/tile`（移动）、`--table-row-h`（PC） | 否（复用） |
| 骨架脉冲 / 淡出时长 | `--ease-std` + `--dur-base` | 否 |
| 加载延迟阈值 200ms | JS 常量（沿用 `PageState.vue:28`） | 否 |
| Toast 时序 | JS 常量 / 一处封装 | 否 |
| 焦点环 | `--color-primary-icon`、`--r-xs` | 否 |
| 顶部安全区兜底 | `max()` 表达式写在 `mobile.scss`，不动 `tokens.scss` | 否 |
| `hrm-admin` 对比度修复 | 复用 Demo 已验证取值；归口见 R-0 | 否（但存在跨工程复制问题，见 R-0） |

**机械事实（避免误判门禁能力）**：`verify:tokens`（`scripts/gen-element-tokens.mjs --check`）只校验 `pc/styles/tokens.scss` 中的 `--el-color-{primary|success|warning|danger|info}-light-{3,5,7,8,9}` 与 `-dark-2` 共 30 个浅色阶字面量（A 类算法复现 + B 类手写基线）。**它不会拦截其它新增 Token**。因此"零新增 Token"这条约束靠**CR 人工把关**，不是靠门禁自动保证 —— 评审时请专项核对。

> 若后续确需新增 Token，必须单列一节说明「理由 + 影响面 + 与既有 Token 的关系」，交主智能体裁定后方可落地。

---

## 6. 风险与不做的事

### 6.1 本次**不做**（明确排除）

| # | 不做 | 原因 |
| --- | --- | --- |
| 1 | 换组件库 / 引第三方 UI 库 / 引 UI 框架 | 用户已裁定保留 Element Plus 2.9.3 与 Vant 4.10.2 |
| 2 | 重做视觉风格（改色板、换主色、改字阶、换圆角体系） | 现体系已过对比度实测；重做会作废 `demo-ui-redesign.md` 的验证结论 |
| 3 | 改信息架构（路由、菜单、Tab 分组、字段定义） | 属产品决策，不在体验优化范围 |
| 4 | 引入新的设计 Token | 用户硬性约束；本方案已证明不需要 |
| 5 | 改 Mock 数据结构 / 断言 / 门禁基线 | 硬约束；且会掩盖真实回归 |
| 6 | 重写页面（含"顺手重构"） | 增量修改，单点可回滚 |
| 7 | 加 ECharts 等图表库 | `demo-ui-redesign.md` §6.7 已论证继续手写 SVG |
| 8 | 首屏体积优化 / 拆包 / 懒加载重构 | 属性能专项，不在本 6 维范围 |
| 9 | 暗色主题 | 无需求，且当前 Token 未做主题映射层 |
| 10 | 国际化（i18n） | 无需求 |

### 6.2 风险登记

| # | 风险 | 触发条件 | 缓释 |
| --- | --- | --- | --- |
| R1 | **改 `hrm-admin/src/styles/**` 会同时影响 Demo PC 端**（`hrm-demo/src/pc/main.js:22` 只读引用） | 批次 A 动 `hrm-admin` 样式 | 每批验收必须**两端截图对比**（`/pc.html` + `hrm-admin` 本地）；Demo 侧 `element-overrides.scss` 的覆盖顺序需确认仍压得住 |
| R2 | `hrm-admin` 无 lint / 无 verify 门禁，改动只有 `build` 兜底 | 批次 A 铺设到多页时 | 只做"最小改动清单"，逐页小步提交；建议补门禁（R-1） |
| R3 | 骨架改造引起布局跳动（骨架高度 ≠ 真实高度） | 批次 B | 用 A2-1 的"骨架高度 vs 真实行高差 ≤2px"作为硬断言 |
| R4 | 键盘遮挡修复引入新的滚动抖动或双滚动条 | 批次 A | 优先选路径 1（Vant 内建）或 2（`scrollIntoView`），路径 3 需防抖；A6-4/A6-5 双向断言 |
| R5 | 焦点环扩展后"到处都是蓝框"，视觉噪声 | 批次 A | 只作用于 `:focus-visible`（键盘触发），鼠标点击不出现；逐页截图复核 |
| R6 | 移动端放开缩放（R-2 若采纳）导致固定布局被放大后错位 | 裁定后实施 | 必须逐页在 200% 缩放下回归；若无法保证则维持现状并登记为已知偏差 |
| R7 | 一次改太多页 → 回归面失控 | 任一批次 | 每批范围已限定，且**每批独立可回滚**；严格按批交付 |

---

## 7. 需主智能体 / 用户裁定的不确定项

### 7.1 裁定项（需决策后才能实施）

| # | 裁定项 | 背景与证据 | 选项 | 建议 |
| --- | --- | --- | --- | --- |
| **R-0** | **Token 真源归口** | Demo 持有 Token 真源（`shared/styles/tokens.base.scss`），生产 `hrm-admin` 反被 Demo 只读引用其 `index.scss`（`hrm-demo/src/pc/main.js:22`）；生产不能依赖演示工程 | ① 把 Token 真源上移到 `hrm-admin`，Demo 只读引用（架构变更，须架构师评估）；② `hrm-admin` 内新增自有 `tokens.scss`（值复用、文件分离，违反"同一逻辑不得重复实现"）；③ `hrm-admin` 仅在 `index.scss` 内做局部值覆盖（最小，但存在两份同值） | **倾向 ①**（长期唯一真源），但属结构变更 → **必须交架构师 + 主智能体裁定**；批次 A 先用 ③ 兜住合规红线，并在注释标 `TODO(扩展)` 指向本条 |
| **R-1** | 是否为 `hrm-admin` 补最小门禁 | `hrm-admin/package.json` 只有 `dev/build/preview`，无 lint / verify；批次 A 要改 4 个页面 | ① 补 `lint` + `build` 校验脚本；② 不补，靠人工走查 | **建议 ①**（否则本方案的验收项在 `hrm-admin` 无法自动化） |
| **R-2** | 是否放开移动端缩放 | `mobile.html:12-13` 的 `user-scalable=no` 违反 SC 1.4.4；但放开会影响 Android H5 壳手势与既有布局 | ① 移除 `maximum-scale=1.0, user-scalable=no`；② 维持现状并登记为已知偏差（需在 `demo-ui-redesign.md` §9.3 补一行） | 合规优先倾向 ①，但**须先确认 Android 壳 WebView 的缩放设置**（`hrm-android-shell` 侧），故需裁定 |
| **R-3** | §1.2 R2-1 对 `demo-ui-redesign.md` §8 的修订是否接受 | §8 原写"禁止加载转圈"，与 B0.2 冲突；本方案裁决为"表格行刷新 + 按钮动作"两类保留 loading | ① 接受修订并登记 `update-log.md`；② 改为全面骨架化（表格也改行骨架，成本高且分页/筛选会闪） | **建议 ①** |

### 7.2 待核实项（实施前须逐一核实，核实前不得写实现）

| # | 待核实 | 核实方法 |
| --- | --- | --- |
| D-1 | Element `scrollToField` 的确切 API 名与签名 | 查 `node_modules/element-plus/es/components/form/src/form*.mjs` 的 `expose` |
| D-2 | Vant 4 Toast 的时长选项名、默认值、可关闭选项，以及其读屏属性（role/aria-live） | 查 `node_modules/vant/es/toast/`（`Toast.mjs` props）与 `es/toast/index.mjs`；`duration` 默认值以产物为准 |
| D-3 | 3 处局部 `prefers-reduced-motion` 是否与全局规则重叠（是否有"全局覆盖不到"的部分，如 `transform` 残留） | 逐个读 `FlowSteps.vue`、`BossShareBar.vue`、`PayrollStatusSteps.vue` 的媒体查询内容，与 `tokens.base.scss:250-259` 对比 |
| D-4 | Vant 4 `Field` 是否内建必填星号（`required` prop 的渲染形态）；`Form` 的校验时机配置项名与默认值 | 查 `node_modules/vant/es/field/Field.mjs`、`es/form/Form.mjs` 的 props 与模板 |
| D-5 | Vant 4 是否内建键盘适配（`Field` / `Popup` 是否含 `visualViewport` 逻辑） | `grep -rn "visualViewport" node_modules/vant/es` |
| D-6 | `el-table` / `van-cell` 等控件的焦点环现状（内部是否存在可聚焦元素） | 用 Tab 键逐页实测 + DevTools 查看 `document.activeElement` |
| D-7 | 骨架组件的 `aria-hidden` / `aria-busy` 支持情况 | 查 `el-skeleton` / `van-skeleton` 产物模板，无则在外层容器补 |
| D-8 | `hrm-admin` 各列表页当前的空态实际表现（Element 默认 `暂无数据` 的确切文案与颜色） | 本地起 `hrm-admin`，断开接口后逐页截图 + 取 `getComputedStyle` |

---

## 附录 A：本方案复用的既有资产（实现时直接引用，不重复建设）

| 资产 | 路径 | 用途 |
| --- | --- | --- |
| PC 四态组件 | `hrm-demo/src/pc/components/StateBlock.vue` | empty / error / denied（含重试出口） |
| 移动四态组件 | `hrm-demo/src/mobile/components/PageState.vue` | loading（200ms 延迟骨架）/ error / empty / denied |
| Token 真源 | `hrm-demo/src/shared/styles/tokens.base.scss` | 色板、语义色、间距、圆角、阴影、**动效时长/缓动**、reduced-motion 降级 |
| PC Token 层 + Element 变量覆盖 | `hrm-demo/src/pc/styles/tokens.scss`、`element-overrides.scss` | 字阶、尺寸、焦点环、hover 加深、路由进度条 |
| 移动 Token 层 + 全局基座 | `hrm-demo/src/mobile/styles/tokens.scss`、`mobile.scss` | `--touch-min`、`--row-h-*`、安全区、`.skeleton-block`、`.is-highlight` |
| 移动 ActionBar | `hrm-demo/src/mobile/components/ActionBar.vue` | 提交中禁用/loading 的统一载体 |
| 请求层错误出口 | `hrm-demo/src/mobile/utils/http.js:37`、`hrm-admin/src/utils/request.js` | 统一失败文案落点 |
| e2e 断言工具 | `hrm-demo/e2e/utils/harness.js`（`horizontalOverflow` / `timeIt` / `attachCollector`） | 直接复用，不新写工具 |

## 附录 B：与既有规范、门禁的关系及事实性纠正

**本方案与既有文档的关系**

| 既有文档 | 关系 |
| --- | --- |
| `demo-ui-redesign.md` | 本方案**继承**其 Token 体系、对比度实测、动效规范（§8）、移动适配规范（§7）、验收核对表（§9.2）；**修订**其 §8"禁止加载转圈"（限定为两类场景，见 R2-1 / R-3） |
| `demo-ux-improvement.md` | 本方案**继承**其 B0.2 组件状态最小集与 B0.3 二次确认清单；**补充**其缺失的"何时骨架/何时 loading"判据与骨架高度约束 |
| `demo-staff-ui-redesign.md` / `demo-boss-ui-spec.md` | 本方案不改变两者的信息架构与视觉定位，只对其页面补齐四态、加载、表单、无障碍口径 |
| `test-cases.md` | 本方案 §4 的验收项可作为其新增用例来源（**只新增，不改既有**） |

**事实性纠正（1 项，按交接包要求登记）**

1. `demo-ui-redesign.md` §9.3 D-4 记载"**硬约束禁止改动 `hrm-admin`**"，与本次任务「`hrm-admin` 与 Demo 都要覆盖、禁改清单不含 `hrm-admin`」**冲突**。本方案以**本次任务指令为准**（`hrm-admin` 可改，但须主智能体 Review）。
   → 请主智能体在 `update-log.md` 登记该口径变更，并同步修订 `demo-ui-redesign.md` §9.3 D-4 的表述（保留"一期冻结页的内部硬编码色值"作为遗留项，但不再表述为"禁止改动 `hrm-admin`"）。

---

**待办与下一步**

1. 本方案提交 → 技术评审工程师按 P0.6 / L8 六维评估（依据充分性 / 边界合理性 / NFR 覆盖 / 复杂度论证 / 假设与风险登记 / 契约一致性）。
2. 评估结论「通过 / 有条件通过」后报主智能体审批；同时裁定 §7.1 的 R-0 ～ R-3。
3. 审批通过后，批次 A 交前端工程师实施，测试工程师按 §4 断言；视觉走查报告由本角色在批次完成后出具（`hrm-dev/docs/review-experience-optimization.md`）。
