# 快递驿站智汇系统 · 三端演示 Demo 界面重设计规范

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-17 |
| 作者 | UI/UX 设计师 |
| 适用范围 | `hrm-dev/hrm-demo`（网页端 pc.html / 老板端 + 员工端 mobile.html / 端选择页 index.html） |
| 交付对象 | 前端工程师（照本文「10 落地实施清单」逐项落地） |
| 关联文档 | [demo-design.md](demo-design.md)（信息架构 5 章、数据契约 7.4）、[requirement.md](requirement.md)（一期语义与角色） |

## 0. 本轮范围、取证方式与不确定项

### 0.1 本轮范围（硬约束）

| 项 | 约定 |
| ---- | ---- |
| 允许改动 | `hrm-demo/src/pc/**`、`hrm-demo/src/mobile/**`、`hrm-demo/index.html` |
| 禁止改动 | `hrm-admin/**`、`hrm-server/**`、`hrm-android-shell/**`、`hrm-demo/src/shared/**`（数据层已冻结） |
| 技术栈 | 冻结：Element Plus 2.9（PC）+ Vant 4.10（移动端），**不引入任何新 UI 库/CSS 框架/图表库** |
| 图表 | 继续手写 SVG（理由与代价见 6.7） |
| 本轮产出 | 仅本设计文档，不改任何源码 |

### 0.2 取证方式与局限（必须知情）

TRAE Chrome 扩展未响应（os error 10061），**本轮无法用浏览器查看真实渲染**。本文所有结论来自对 42 个文件（三端源码 + 设计依据）的完整静态阅读（清单见附录 B），因此：

- 结构性结论（色值、字号、间距、尺寸、交互可达性、状态缺失）证据充分，已逐项标注 `文件:行号`；
- 依赖运行时计算的结论（Vant 组件内部默认内边距实际像素、Tab 指示器宽度、`el-table` 行高实测值）**标注为待走查项**，实现后由测试工程师按 9.4 核对表复核。

查询三端硬编码色值规模的复核命令（撰稿时实测：命中 **111 行**、分布 **21 个文件**）：

```bash
grep -rn -E "#[0-9a-fA-F]{6}" hrm-dev/hrm-demo/src | wc -l
```

### 0.3 不确定项与验证方式（反幻觉声明）

| # | 不确定项 | 本文处理 | 验证方式 |
| ---- | ---- | ---- | ---- |
| U1 | Vant 4.10 **组件级** CSS 变量精确名称（如 `--van-cell-vertical-padding`） | 仅对根级变量（`--van-primary-color` 等）给确定结论；组件级变量集中列在 2.9 并标注「待核对」 | 读 `hrm-dev/hrm-demo/node_modules/vant/es/<component>/index.css` 或 Vant 官方《ConfigProvider 主题变量》页，逐项核对后再落地 |
| U2 | Element Plus 2.9 的 `--el-color-primary-light-3/5/7/8/9`、`dark-2` 生成算法 | 未凭记忆给值：用官方对 `#409EFF` 的**已知产物**反推出公式为 `mix(base, white, {30/50/70/80/90}%)` 与 `mix(base, black, 20%)`，再据此推导本文色阶 | 实现后用 `getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary-light-3')` 与 2.8 表比对；不一致则直接在 tokens 中写死推导值 |
| U3 | Element Plus `--el-button-hover-bg-color` 等按钮级变量是否存在 | 已出现在 2.8 的覆盖方案中，标注「待核对」 | 同上，读 `node_modules/element-plus/theme-chalk/el-button.css` |
| U4 | 对比度数值 | 全部按 WCAG 2.x 相对亮度公式手算，标注了计算值，误差量级 ±0.05 | 实现后用 DevTools 对比度检查器或 axe DevTools 复核 9.1 表 |
| U5 | `van-tabbar` 实际高度（现有 FAB 用 `calc(66px + safe-area)` 反推为 50px + 16px 偏移） | 按 50px 处理并改为变量 | 走查实测，若不符则只改 `--tabbar-h` 一处（这正是把魔数改成变量的目的） |
| U6 | 一期复用页（员工/部门/驿站/个人中心/登录）内部硬编码色值 | 记为已知偏差（9.3），不做重设计 | 由主智能体评审决定是否列入二期统一改造 |

---

## 1. 现状问题诊断

### 1.1 跨端问题（最关键）

| # | 文件 | 具体问题 | 影响 |
| ---- | ---- | ---- | ---- |
| P1 | [mobile.scss:6-19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss) vs [pc/layout/index.vue:6,14,211](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue) vs [index.html:45,69](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/index.html) | **同一系统存在三套互不相同的品牌色**：PC 用 Element 默认 `#409eff`，移动端用 `#1677ff`，入口页用 `#1677ff`；而项目基线与设计技能规定的主色是 `#1890FF`。语义色同样三分：成功 `#67c23a / #07c160 / #52c41a`，警告 `#e6a23c / #ff8f1f / (入口页无)`，危险 `#f56c6c / #ee0a24 / #ff4d4f` | 三端看起来像三个不同产品；任何一次品牌微调需要改 20+ 个文件；截图评审时无法判断"哪个蓝是对的" |
| P2 | 三端共 21 个文件、111 行 6 位十六进制色值（`grep` 实测） | 无任何 Design Token 层，色值散落在组件 `<style scoped>`、模板内联属性（`background-color="#001529"`、`color="#ee0a24"`）、SVG `stroke=` 里 | 无法做主题化；深浅色/对比度修正无法批量投入；新人无法判断某色值是否"合法" |
| P3 | [pc/views/dashboard/index.vue:345-359](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue) 与 [mobile/views/staff/home.vue:113](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/home.vue)、[MeSection.vue:70](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue) | 指标卡图标使用 4 组"AI 默认风"渐变，其中一组是**紫色 `#a18cd1 → #7c4dff`**（明确禁止的 AI 默认色）；移动端 Hero 渐变 `#1677ff → #3f8cff` 在两处重复硬编码 | 紫色与快递物流业务无关联，且与"企业内部管理系统"调性冲突；同一渐变写两遍，第三次出现必然漂移 |
| P4 | 移动端 [pickup.vue:140](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/pickup.vue) `color="#07c160"`、[boss/home.vue:78-82](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/home.vue) `color="#ee0a24" background="#fff1f0"` 等 | 色值直接写在模板属性里（不在 style 里），Token 化时无法被静态检查发现 | 后续审计"是否还有硬编码色"时容易漏；深浅模式下无法覆盖 |
| P5 | 状态标签：PC [StatusTag.vue:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/StatusTag.vue) 用 `effect="light"`（浅底实心），移动 [StatusTag.vue:23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatusTag.vue) 默认 `plain`（描边空心） | 同一状态（如"在库待取"）在 PC 是浅橙实心块、在移动是橙描边空心块 | 状态语义的视觉权重不一致，跨端对照时容易误读优先级 |

### 1.2 网页端（Element Plus）

| # | 文件 | 具体问题 | 影响 |
| ---- | ---- | ---- | ---- |
| P6 | [dashboard/index.vue:312-315](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue) 与 [parcel/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue)（无标题）与 [sync/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue)（无标题） | 页面标题只有看板有（16px/600），包裹、同步、工单、通知 4 个页面**完全没有页面标题**，仅靠头部面包屑定位 | 用户进入内容页后不知道"我在哪一页"；标题层级也没有进入统一样式表 |
| P7 | dashboard 单页内出现 **9 种字号**（11/12/13/14/16/20/22/26/28），其中同一类"指标数值"就有 4 种：`stat-value 28px`(:364)、`mini-value 22px`(MiniStats:39)、`health-value 26px`(:396)、`wo-value 20px`(:474) | 无字号阶梯，"重要"与"次要"仅靠随手调大 | 视觉层级失效——一屏 4 种数值字号，用户无法建立"哪个数更重要"的稳定预期 |
| P8 | [dashboard/index.vue:326](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue) `margin-bottom:16px` + `el-row :gutter="16"`(:21,40,86) | 竖向节奏靠"卡片自身 margin"堆叠，与 `el-row` 的 gutter 混用；`MiniStats.vue:24` 用 `shadow="hover"`，同页的 `block-card` 用 `shadow="never"`(:42,58,88,124) | 悬停阴影在同一页里语义不明（到底哪类卡片可点？）；间距无统一规则，改一处要翻全页 |
| P9 | [MiniStats.vue:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/MiniStats.vue) `:lg="Math.max(3, Math.round(24 / items.length))"` | 用"24 除以条数再四舍五入"算列宽：5 条 → `lg=5`（每行 4.8 个，必然换行错位）、6 条 → 4（正常）。是脆弱的隐式约定 | 指标条数一变（如包裹 6 条 → 5 条）栅格立刻错位；且 `Math.round` 的意图无法被后人还原 |
| P10 | [TrendChart.vue:14-15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue) `W=720 H=180` + `width:100%;height:auto`(:108-112) | 固定 viewBox 等比拉伸：容器 1200px 时图高约 300px、轴文字从 11px 被放大到约 18px | 图表文字尺寸脱离全局字号体系；宽屏下图形比例失真（横向被拉长） |
| P11 | [TrendChart.vue:41](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue) `label: Math.round(value)` | Y 轴刻度值取整后**会重复**：最大值 3 时五档刻度算出 `3,2,2,1,1,0` | 轴标签出现重复数字，数据可读性受损（这是可复现的确定性缺陷） |
| P12 | [TrendChart.vue:70](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue) 只画 `<polyline>` | 无双端一致的交互：无数据点、无 hover 十字线/读数、无图例开关（而移动端 [LineChart.vue:69-79](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) **有**图例开关） | 同一张"包裹趋势"在 PC 只能看形状不能读数，在移动端反而能切系列——能力倒挂 |
| P13 | [parcel/index.vue:293-297](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue) `.stat-hint { margin: -8px 0 16px }` | 用负 margin 抵消 `MiniStats` 的 `margin-bottom:16px` | 典型的"样式对抗"，任何一方调整都会破版 |
| P14 | [parcel/index.vue:180-185](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue)、[sync/index.vue:173-178](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue)、[workOrder/index.vue:300-305](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue)、[notification/index.vue:101-106](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/notification/index.vue) | `catch (e) { /* 拦截器已统一提示 */ }` 后列表保持 `[]`，表格渲染 `empty-text="没有符合条件的包裹"` 等 | **错误态被伪装成空态**：接口失败时用户看到"没有数据"，会误判为业务为空而非系统异常；这 4 个页面全部存在该问题 |
| P15 | [parcel/index.vue:54](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/parcel/index.vue) `border stripe` 与 [dashboard/index.vue:99](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue)(无 border) | 表格形态不统一：包裹/同步/工单用"边框+斑马纹"，看板排行用无边框 | 同一系统两种表格语言；斑马纹+边框+悬停高亮叠三层视觉噪声 |
| P16 | 抽屉三处宽度不一：`460px`(:89 包裹)、`520px`(:98 同步)、`560px`(:103 工单)；`el-descriptions` 列数 1/2/2、尺寸默认/`small`/`small` | 无抽屉规范 | 同一"详情"心智模型下三种宽度三种密度 |
| P17 | [workOrder/index.vue:448-450](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/workOrder/index.vue) `:deep(.over-sla-row > td) { background-color:#fef0f0 !important }` | 用 `!important` 覆盖斑马纹背景，越过 Element 的层级机制；`#fef0f0` 是 Element danger-light-9 的魔数拷贝 | 后续任何行样式调整都会与 `!important` 打架；色值无法被 Token 覆盖 |
| P18 | [dashboard/index.vue:434-447](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue) 排名徽标 `rank-1 红 #f56c6c`、`rank-2 橙 #e6a23c`、`rank-3 蓝 #409eff`，而移动端 [rank.vue:170-183](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue) 用 `金 #f5b301 / 银 #9aa4b2 / 铜 #c98a5b` | **同一"第 1 名"概念在两端语义冲突**：PC 用红色（危险语义）表示冠军，移动端用金色 | 颜色语义自相矛盾；PC 的"第 1 名"看起来像告警 |
| P19 | [dashboard/index.vue:461-484](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/dashboard/index.vue) `.wo-grid` 两列 5 项 | 5 个指标放 2 列 → 最后一项独占半行留白；且第 5 项"平均处理时长"是**时长**，与前 4 项**件数**同格混排、同字号 | 栅格出现孤儿项；单位混排让"20"到底是 20 条还是 20 分钟无法一眼区分 |
| P20 | [layout/index.vue:41](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue)（折叠图标 `el-icon` 直接绑 click）、[:49-55](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue)（`<span class="header-user">` 绑 `el-dropdown`）、[:208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue) `outline: none` | 折叠按钮与用户菜单是**不可聚焦的非按钮元素**，且显式移除了焦点轮廓 | 键盘用户无法展开/折叠侧栏、无法打开用户菜单（WCAG 2.4.7 / 2.1.1 失败） |
| P21 | [notification/index.vue:18-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/notification/index.vue) 行是 `<div @click>` | 通知行不可键盘聚焦/回车触发；`padding:14px 8px` 左右不对称 | 通知中心对键盘用户仅"标记已读"可用，点不到正文跳转 |
| P22 | [pc/layout/index.vue:14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue) `background-color="#001529"` | 侧栏色取自 Ant Design Pro 的深空蓝，与移动端品牌蓝、项目规定的辅助色 `#1F2937` 都不是一回事 | 三端品牌锚点缺失；模板属性形式导致无法在 Token 层统一 |

### 1.3 移动端（Vant 4）

| # | 文件 | 具体问题 | 影响 |
| ---- | ---- | ---- | ---- |
| P23 | [mobile.scss:6-19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss) 已定义 `--hrm-*`，但 `--van-primary-color` 只覆盖了 primary 一项 | 成功/警告/危险仍是 Vant 默认（`#07c160/#ff8f1f/#ee0a24`），与 `--hrm-*` 定义**并存但不一致**（`--hrm-success:#07c160` 恰好等于 Vant 默认，属巧合） | 组件内部（Button/Tag/Progress）与自绘样式两套取色路径，改一处不生效 |
| P24 | 移动端 11 个页面共出现 **10 种字号**（9/11/12/13/14/15/16/17/20/22），其中 `11px` 被大量用于承载信息的文案：[parcel.vue:161](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/parcel.vue)（筛选汇总）、[rank.vue:201](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue)（驿站三项指标）、[alerts.vue:253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue)（口径说明）、[workorder.vue:168,202](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue)、[pickup.vue:191](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/pickup.vue) 等 | 11px 中文在 375pt 屏上笔画粘连，可读性不达标；且"辅助文字"跨页有 11/12 两种，无规则 |
| P25 | 移动端 spacing 出现 10/14/18/22/26px：[mobile.scss:61,74,109](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss)（`card padding:14px`、`section-title margin:18px 2px 8px`、`list-item padding:12px 14px`）、[MeSection.vue:86](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/MeSection.vue)(`margin:22px 0 8px`)、[rank.vue:210](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue)(`padding:20px 0 24px`) | 4px 基准网格被破坏（14/18/22/26 均非 4 的倍数） | 稿面观感"说不清哪里不对"：纵向节奏不可预测，视觉走查无法用数值判定合格与否 |
| P26 | `card + card` 间距 12px（[mobile.scss:66-68](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss)），`list-item + list-item` 间距 10px（[:116](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss)，同时在 [parcel.vue:165-171](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/parcel.vue) 重复定义一遍） | 两种卡片间距（12/10）并存；`list-item` 的间距规则被页面级样式重复实现 | 同屏出现 10 与 12 两种间隙；第三次重复时必然漂移 |
| P27 | [IdentitySwitcher.vue:72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/IdentitySwitcher.vue) `background:#f0f7ff`、[login/index.vue:137-138](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue) `#f0f7ff` + `#cfe4ff`、[pickup.vue:167](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/pickup.vue) `#f0f7ff` | "主色浅底"在 3 处各写一遍，且都是手调值（非色阶） | 同一"浅蓝底"出现 3 个近似色；Token 化后应收敛为 1 个 `--color-primary-surface` |
| P28 | [mobile.scss:100-102](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss) `--hrm-text-3: #969799` | 该值被用作 `.muted/.list-item__meta/.section-title__extra/.tip` 的文字色（信息性文案，非装饰） | **对比度 2.93:1，远低于 WCAG AA 4.5:1**；12px 灰色元信息在户外/低亮屏上基本看不清（快递驿站一线场景常在户外） |
| P29 | PC 侧 [index.scss:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/styles/index.scss) `#909399` 与大量 PC 页面 `#909399` 文案色 | 同为"辅助文字"主力色，对比度 **3.08:1**，同样不达 AA | 两端辅助文字系统性不合格，这不是个别页面问题而是 Token 缺位导致的系统性缺陷 |
| P30 | [alerts.vue:91,119,140](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue) 用 `van-tag type="success"` 表达"0 个失败批次" | 把"中性好结果"用绿色标签，与"成功"语义混同；同时 [workorder.vue:143](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) 的优先级用实心 `plain=false`，而 [alerts.vue:155](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/alerts.vue) 同优先级用空心 | 标签的"实心/空心"没有承载任何语义，纯随机 |
| P31 | [workorderDetail.vue:156-173](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorderDetail.vue)、[parcelDetail.vue:95-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/parcelDetail.vue) | 关键操作按钮（接单/解决/关闭/取件核销）排在页面内容流末尾 | 工单详情内容长（信息 8 行 + 描述 + 时间线）时，**一线员工必须滑到底才能操作**，作业效率受损（这是本次重设计最高优先级的交互修复之一） |
| P32 | [workorder.vue:208-219](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) FAB `bottom: calc(66px + env(safe-area-inset-bottom))` | `66px` 是与 Tabbar 高度耦合的魔数（Tabbar 50px + 16px 间距），无变量 | 一旦调整 Tabbar 高度（如放大触控区），FAB 会被遮挡 |
| P33 | [workorder.vue:130-136](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) 列表行 `<div @click>`、[notification.vue:110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue)、[alerts.vue:97-102](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-demo/src/mobile/views/boss/alerts.vue)、[rank.vue:88](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue) | 可点击列表行为 `<div @click>`，无 `role`/`tabindex`/键盘事件；`notification.vue:105` 的"全部已读"是 `<span @click>` | 外接键盘/无障碍开关/读屏用户无法打开列表项；`aria-label` 全端缺失（含 [LineChart.vue:81](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) 的 `role="img"` 但没有 `aria-label`，而 PC 的 TrendChart 有） |
| P34 | 触控目标实测不足 44px（按 CSS 声明推算高度：padding×2 + 字号×1.2 + 边框）：[login/index.vue:133-140](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue) 一键体验按钮 ≈25px；[rank.vue:133-140](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue) 排序 chip ≈30px；[workorder.vue:171-181](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) `filter__chip` ≈23px；[notification.vue:138-141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue) "全部已读" ≈17px；[pickup.vue:162-169](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/pickup.vue) 演示运单号 ≈33px；[LineChart.vue:157-165](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) 图例 ≈17px | 驿站现场单手持机、戴手套操作，误触率显著上升；WCAG 2.5.5 (AAA 44px) 与 2.5.8 (AA 24px) 均不满足 |
| P35 | [boss/trend.vue:107-118](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/trend.vue) 自定义 `.metric` 卡 | 与 `StatCard` 同一"指标卡"概念**第二份实现**，且数值字号不同（20px vs [StatCard.vue:44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/StatCard.vue) 22px）、圆角与内边距不同 | 同屏"指标"两种字重；按项目"同一逻辑不得三次实现"红线，此处已是第二次，必须收口到 `StatCard` |
| P36 | [LineChart.vue:16](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) `H = compact ? 92 : 190` 且 `compact` 时 `PAD` 全为 8、隐藏全部轴与网格 | 老板端首页的迷你趋势图**没有任何量纲参照**（无轴、无末值标注），只有形状 | 数据被"形状化"：老板看到曲线上升但不知是 100 件还是 10 万件；首页最重要的图无法读数 |
| P37 | 移动端页面无过渡动画；[mobile.scss:136-148](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss) `highlight-fade 2.4s` 是唯一动效；全端无 `prefers-reduced-motion` 处理 | 页面切换生硬；且骨架→内容的切换无淡入，数据到达瞬间"跳变" | 观感廉价；对前庭敏感用户无法降级动效（WCAG 2.3.3） |
| P38 | [TabbarLayout.vue:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/layout/TabbarLayout.vue) `active-color="#1677ff" inactive-color="#969799"` | 内联色值绕过 Token；`inactive-color:#969799` 在 Tabbar 白底上对比度约 2.9:1 | 与 P28 同源问题；Tabbar 文字偏淡，老年员工辨识困难 |

### 1.4 图表专项（手写 SVG）

| # | 文件 | 具体问题 | 影响 |
| ---- | ---- | ---- | ---- |
| P39 | PC [TrendChart.vue:100,135](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue) `#409eff` / `#67c23a`；移动 [LineChart.vue:22-23,105-127](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) `#1677ff` / `#07c160` | 同一"入库/取件"双系列在两端 4 个色值，且取件绿线在白色背景上对比度 PC 2.24:1、移动 2.38:1 | 违反 WCAG 1.4.11「图形对象需 ≥3:1」；跨端看图无法建立"蓝色=入库"的条件反射 |
| P40 | PC 无数据点/无交互；移动 `compact` 无任何刻度 | 两端图表能力不对等（见 P12/P36） | 见 P12、P36 |
| P41 | [TrendChart.vue:16](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue) `PAD.bottom=28` 但 x 轴文字 `y=H-8`(:68)，与 [gridLines](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/TrendChart.vue#L38-L43) 最底刻度 `y=yAt(0)=PAD.top+plotH` 相距 20px | 无 0 轴基准线加粗，最低刻度线与横轴文字视觉上"漂浮" | 图表缺少"地面"，读图时缺少基准参照 |

### 1.5 无障碍汇总（不达标项）

| 项 | 现状 | 标准 | 判定 |
| ---- | ---- | ---- | ---- |
| 辅助文字对比度（移动 `#969799`） | 2.93:1 | WCAG 2.1 SC 1.4.3 ≥ 4.5:1 | 不通过 |
| 辅助文字对比度（PC `#909399`） | 3.08:1 | 同上 | 不通过 |
| 移动端危险色作文字（`#ee0a24`） | 4.46:1 | 同上 | 不通过（临界差 0.04） |
| 白色文字压主色实底（`#1890FF`/`#1677ff`/`#409eff` 按钮） | 3.24 / 4.10 / 3.23 | 同上 | 不通过 |
| Hero 渐变浅端白字（`#3f8cff`，12px） | 3.28:1 | 同上 | 不通过 |
| 图表数据线（取件系列） | 2.24 / 2.38 | SC 1.4.11 ≥ 3:1 | 不通过 |
| 键盘可达（侧栏折叠、用户菜单、通知行、移动端列表/FAB 以外的行点击） | 不可聚焦 | SC 2.1.1 | 不通过 |
| 焦点可见 | [layout/index.vue:208](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue) 显式 `outline: none` | SC 2.4.7 | 不通过 |
| 触控目标 | 6 处 < 44px，最小 17px | SC 2.5.5 (44) / 2.5.8 (24) | 不通过 |
| 动效降级 | 无 `prefers-reduced-motion` | SC 2.3.3 | 不通过 |
| 图表替代文本 | 移动端 `role="img"` 无 `aria-label` | SC 1.1.1 | 不通过 |

### 1.6 冻结清单（本轮不重设计，仅被动继承新变量）

以下页面复用 `hrm-admin` 一期视图，**禁止改动**，其外观仅被动继承 PC 端 Token 覆盖效果：

| 路由 | 组件 | 说明 |
| ---- | ---- | ---- |
| PC `/login` | `@admin/views/login/index.vue` | 一期登录页（含 1001/1002 文案分支） |
| PC `/employee`、`/department`、`/station`、`/profile` | `@admin/views/*` | 一期四大页面 |
| PC `/knowledge`、`/money`、`/performance`、`/permission`、`/system` | `@admin/views/*` | 仅 `<router-view/>` 空壳 |

> 已核实一期 [dashboard/index.vue:1-37](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/views/dashboard/index.vue) 无页面标题、指标卡为 28px/13px + 渐变图标——这正是 Demo 看板"照抄"的来源。Demo 看板是**新建文件**，可完全重设计。

---

## 2. 设计系统（Design Tokens）

### 2.1 三层结构与唯一真源

```text
L1 Primitive（基础层，纯数值，无语义）
   --c-blue-500 / --c-neutral-800 / --c-red-600 …
        ↓ 别名映射
L2 Semantic（语义层，组件唯一允许引用的层）
   --color-primary / --text-1 / --surface-card / --border-line …
        ↓ 组件别名
L3 Component（组件层，覆盖第三方库变量 + 组件私有尺寸）
   --el-color-primary / --van-primary-color / --tabbar-h / --drawer-w …
```

**落地形态（2 个文件，唯一真源）**

| 端 | 文件 | 内容 |
| ---- | ---- | ---- |
| PC | `hrm-demo/src/pc/styles/tokens.scss`（新建） | L1 + L2 + L3 + Element 变量覆盖 + 密度规格 |
| 移动 | `hrm-demo/src/mobile/styles/tokens.scss`（新建） | L1 + L2 + L3 + Vant 变量覆盖 + 安全区规格 |

**强制规则（进 Code Review 检查项）**

1. 业务组件 `<style>` 与模板中**不得出现十六进制色值**，只允许 `var(--…)`；
2. 数值（font-size / padding / margin / gap / radius）**必须取自 Token**，禁止 14/18/22/26 等非 4 倍数与 11px 正文；
3. 新增语义色必须先加 L2 变量，禁止就地写值；
4. SVG 图表内的 `stroke/fill` 使用 `currentColor` 或 `var(--…)`，禁止字面色值。

### 2.2 L1 Primitive 色板

**蓝（品牌主色族，基准 `#1890FF`）**

| Token | HEX | 用途 |
| ---- | ---- | ---- |
| `--c-blue-50` | `#E8F4FF` | 主色浅底（选中行、浅底 chip、图标底） |
| `--c-blue-100` | `#D1E9FF` | 主色浅底强化 / 浅色描边 |
| `--c-blue-200` | `#BADEFF` | 图表面积填充上限 |
| `--c-blue-300` | `#8CC8FF` | 弱化描边 |
| `--c-blue-400` | `#5DB1FF` | 深底上的次级强调 |
| `--c-blue-500` | `#1890FF` | **品牌标识色**：图标、图表线、进度条、强调描边 |
| `--c-blue-600` | `#1273E6` | 500 与 700 之间的过渡（禁用态实底） |
| `--c-blue-700` | `#0958D9` | **承白字实底**：主按钮、NavBar、Tabbar 选中、选中 chip |
| `--c-blue-800` | `#0745A8` | 主按钮 hover/active、Hero 渐变深端 |
| `--c-blue-900` | `#063A8A` | 深色装饰、Hero 渐变最深处 |

> **关键决策（D-1）**：`#1890FF` 与白色文字的对比度为 **3.24:1**，不满足 SC 1.4.3，因此**不得**作为承载 14px 白字的实底。品牌主色保留 `#1890FF` 用于"看着像品牌"的表面（图标/线/描边/浅底），而"要写字在上面"的表面统一降到 `#0958D9`（白字 **6.16:1**）。两者同属一条色阶，不产生第二品牌色。

> **易混淆口径（勿当作同一语义的两种写法）**：`--color-primary-strong` 与 `--color-primary-hover` 并存，但取值与语义都不同 —— 前者是 `--c-blue-700`(`#0958D9`) 的历史别名，与 `--color-primary` 同义，用于承白字的实底（收敛中）；后者是 `--c-blue-800`(`#0745A8`)，只用于 hover/active 加深态。改动其一不会影响另一，替换前先确认场景属于「实底」还是「交互态」。

**橙（物流/包裹强调，基准 `#FA8C16`）**

| Token | HEX | 用途 |
| ---- | ---- | ---- |
| `--c-orange-50` | `#FFF7E6` | 橙色浅底 |
| `--c-orange-100` | `#FFE7BA` | 橙色浅描边 |
| `--c-orange-300` | `#FFC069` | 图表第三系列（预留） |
| `--c-orange-500` | `#FA8C16` | **物流强调色**：包裹量图标、货架标识、趋势副轴 |
| `--c-orange-600` | `#D46B08` | 橙色图标/大字号（白底 3.55:1，仅限 ≥18.66px 粗体或图标） |
| `--c-orange-700` | `#B45309` | **橙色系文字色**（白底 5.02:1） |

**语义色族（每条族 5 档：浅底 / 描边 / 标识 / 文字 / 深底）**

| 语义 | 浅底 | 描边/图标 | 标识色(500) | 文字色(600/700) | 白字实底 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 成功 | `#F6FFED` | `#B7EB8F` | `#52C41A`（2.27:1，仅图标） | `#237804`（5.59:1） | `#237804` |
| 警告 | `#FFFBE6` | `#FFE58F` | `#FAAD14`（1.98:1，仅图标） | `#B45309`（5.02:1） | `#B45309` |
| 危险 | `#FFF1F0` | `#FFCCC7` | `#FF4D4F`（3.27:1，图标/大字号） | `#CF1322`（5.57:1） | `#CF1322` |
| 信息/中性 | `#F5F7FA` | `#E3E7ED` | `#6B7280`（4.83:1） | `#4B5563`（7.56:1） | `#4B5563` |
| 品牌 | `#E8F4FF` | `#BADEFF` | `#1890FF`（3.24:1，图标/线） | `#0958D9`（6.16:1） | `#0958D9` |

**中性灰阶（蓝灰族，与 `#1F2937` 同源）**

| Token | HEX | 白底对比度（手算） | 用途 |
| ---- | ---- | ---- | ---- |
| `--c-neutral-0` | `#FFFFFF` | — | 卡片、表格、NavBar 底 |
| `--c-neutral-25` | `#FAFBFC` | — | 表格斑马纹（如启用，仅偶数行） |
| `--c-neutral-50` | `#F5F7FA` | — | 页面底色、浅底标签 |
| `--c-neutral-100` | `#EDF0F4` | — | hover 底、中性标签底 |
| `--c-neutral-200` | `#E3E7ED` | 1.24:1 | **分隔线**（装饰性，豁免 1.4.11） |
| `--c-neutral-300` | `#CBD2DA` | 1.71:1 | 禁用文字（SC 1.4.3 豁免禁用态）、禁用描边 |
| `--c-neutral-400` | `#9AA4B2` | 2.52:1 | 占位符、禁用图标（见 9.3 已知偏差） |
| `--c-neutral-450` | `#8A93A0` | **3.10:1** | 可交互控件边界（`--border-control`，满足 SC 1.4.11） |
| `--c-neutral-500` | `#6B7280` | **4.83:1** | 三级文本（原 `#969799`/`#909399` 的替代） |
| `--c-neutral-600` | `#4B5563` | **7.56:1** | 二级文本（标签文字、元信息） |
| `--c-neutral-700` | `#374151` | 10.30:1 | 次级标题 |
| `--c-neutral-800` | `#1F2937` | **14.66:1** | 一级文本 / 深色表面（侧栏、Hero 深底） |
| `--c-neutral-900` | `#111827` | 16.9:1 | 强调文本（极少数） |
| `--c-neutral-950` | `#0B1220` | — | 图表暗底（预留，本轮不用） |

### 2.3 语义色三层用法（防误用的硬规则）

| 层 | 允许用在哪 | 禁止用在哪 |
| ---- | ---- | ---- |
| 500 档（`#1890FF`/`#52C41A`/`#FF4D4F`/`#FA8C16`/`#FAAD14`） | 图标、图表线、进度条、1px 描边、≥24px 大号数字、浅底上的大面积色块 | ❌ 14px 及以下文字；❌ 承载白字的实底 |
| 600/700 档 | 小号文字、标签文字、白字实底、hover/active | 大面积填充（视觉过重） |
| 50/100 档 | 标签底、选中行底、提示条底、图标底 | 大面积页面底（用 `--c-neutral-50`） |

### 2.4 字体层级（PC 与移动端共享阶梯，尺寸分端）

**字体栈（统一，两端一致）**

```scss
--font-sans: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB',
             'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
```

> 现状 PC 用 `'Helvetica Neue'` 打头、移动用 `-apple-system` 打头（[index.scss:15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/styles/index.scss) / [mobile.scss:34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/styles/mobile.scss)）→ 中文回退链虽然一致，但拉丁字形不同。统一为 `-apple-system` 打头（Windows 下回退 `Microsoft YaHei`，符合目标浏览器 Chrome/Edge 环境）。

**文本阶梯**

| 层级 | 语义 | PC 字号/字重/行高 | 移动字号/字重/行高 | 用途 |
| ---- | ---- | ---- | ---- | ---- |
| H1 | 页面大标题 | 20 / 600 / 28 | — | PC 页头标题；入口页主标题 |
| H1-m | 移动 Hero 标题 | — | 18 / 600 / 26 | 移动端 Hero 卡主文案 |
| H2 | 弹层/抽屉标题 | 16 / 600 / 24 | 16 / 600 / 24 | 抽屉、弹窗、底部弹层标题 |
| H3 | 区块/卡片标题 | 15 / 600 / 22 | 14 / 600 / 20 | 卡片头、`section-title` |
| Body | 正文 | 14 / 400 / 22 | 14 / 400 / 21 | 描述、表格正文 |
| Body-strong | 列表主文案 | 14 / 500 / 22 | 15 / 500 / 21 | 列表项标题（运单号、工单号） |
| Caption | 辅助文字 | 12 / 400 / 18 | 12 / 400 / 18 | 元信息、口径说明、时间 |
| Micro | 极小字（受限） | 11 / 400 / 16 | 11 / 400 / 16 | **仅限图表轴标签与徽标内计数**，其余一律用 Caption |
| Num-lg | 主指标数值 | 26 / 600 / 32 | 24 / 600 / 30 | 首屏 4 大指标 |
| Num-md | 次级数值 | 20 / 600 / 26 | 18 / 600 / 24 | 卡片内数值、排行数值 |
| Num-sm | 行内数值 | 15 / 600 / 20 | 15 / 600 / 20 | 表格内数字、列表右侧值 |

**硬规则**

1. 正文最小 12px（`Caption`）；`Micro` 仅限图表轴与徽标；
2. 数字一律加 `font-variant-numeric: tabular-nums`（表格、看板、排行必须，保证纵向对齐）——现状全端缺失；
3. 同一屏内数值字号最多 2 种（`Num-lg` + `Num-sm`，或 `Num-md` + `Num-sm`），**禁止 4 种并存**（修 P7）；
4. 中文字重只用 400 / 500 / 600 三档，不使用 700（PingFang 700 过黑，与 600 视觉差异小于预期）；
5. PC 页面标题取 H1 = 20px 而非项目基线的 24px：理由见 9.3 已知偏差 D-2（与冻结的一期页面共存）。

### 2.5 间距体系（4px 基准，8px 步进）

| Token | 值 | 典型用途 |
| ---- | ---- | ---- |
| `--sp-1` | 4 | 图标与文字间隙、标签内部 |
| `--sp-2` | 8 | 卡片内元素间距、标签组间隙、表格紧凑行 |
| `--sp-3` | 12 | 移动端卡片间距、表单行间距、列表项间距 |
| `--sp-4` | 16 | 卡片内边距（PC）、区块间距（PC）、页面左右内边距（PC） |
| `--sp-5` | 20 | 卡片内边距（PC 含图表的卡）、区块间距（PC 行间） |
| `--sp-6` | 24 | 移动端区块间距、页头与内容间距 |
| `--sp-8` | 32 | PC 页头下间距（含口径行时）、移动 Hero 与首卡间距 |
| `--sp-10` | 40 | 空态上下留白 |

**场景取值表（覆盖现状所有硬编码值）**

| 场景 | PC | 移动 | 现状值（需替换） |
| ---- | ---- | ---- | ---- |
| 页面左右内边距 | 16 | 12 | PC `16`（保留）；移动 `12`（保留） |
| 卡片内边距 | 16（普通）/ 20（含图表） | 16 | 移动 `14` → **16** |
| 卡片之间竖向间距 | 16 | 12 | 移动 `12`（保留）/ `10` → **12** |
| 区块标题上间距 | 24 | 18→**20** | 移动 `18` → **20** |
| 区块标题下间距 | 8 | 8 | 移动 `8`（保留） |
| 列表项内边距 | 12 / 16（垂直/水平） | 12 / 16 | 移动 `12px 14px` → **12px 16px** |
| 列表项之间 | 8（表格行）/ 12（卡片式） | 12 | 移动 `10` → **12** |
| 表单项之间 | 16 | 12 | PC `0`(inline)/`16` 混用 |
| 按钮组间隙 | 8 | 10 | PC `4`(toolbar `gap:4`) → **8**；移动 `10` → **12** |
| 弹层内边距 | 20 | 20 / 24（底部含安全区） | 移动 `20px 0 24px`（保留） |
| 顶部安全区 | — | `--safe-top`（壳注入，浏览器 0） | 保留机制，改为 Token 名 |
| 底部安全区 | — | `env(safe-area-inset-bottom, 0px)` | 保留机制，改为 Token |

### 2.6 圆角 / 阴影 / 描边

**圆角**

| Token | 值 | 用途 | 现状 |
| ---- | ---- | ---- | ---- |
| `--r-xs` | 4 | 标签、徽标、小 chip | PC `50%`/`6px` 混用；移动 `6px` |
| `--r-sm` | 6 | 输入框、小按钮、筛选 chip | — |
| `--r-md` | 8 | PC 卡片、面板、抽屉内卡 | PC `8`（保留）/ `6`(wo-item) → **8** |
| `--r-lg` | 12 | 移动卡片、底部弹层顶部 | 移动 `10` → **12** |
| `--r-full` | 999 | 胶囊按钮、头像、状态胶囊 | 保留 |

**阴影（elevation）**

| Token | 值 | 用途 |
| ---- | ---- | ---- |
| `--e0` | `none` | **PC 后台卡片默认**：改用 `1px` 描边，避免表格密集页的阴影噪声 |
| `--e1` | `0 1px 2px rgba(31,41,55,.06)` | 移动端卡片 |
| `--e2` | `0 4px 12px rgba(31,41,55,.08)` | 移动 Hero、悬浮 FAB(hover)、PC 可点卡片 hover |
| `--e3` | `0 8px 24px rgba(31,41,55,.12)` | 抽屉/弹层、移动固定操作栏（向上投影 `0 -1px 3px`） |
| `--e4` | `0 12px 32px rgba(31,41,55,.16)` | 模态框（保留 Element 默认则不复写） |

**描边（1px）**

| Token | 值 | 用途 |
| ---- | ---- | ---- |
| `--border-line` | `#E3E7ED` | 分隔线、卡片描边、表格网格（装饰性） |
| `--border-control` | `#8A93A0` | **可交互控件边界**（输入框、下拉、复选、分段控件）——白底 **3.10:1**，满足 SC 1.4.11 |

> ⚠ `--border-control: #8A93A0` 明显深于 Element/Vant 默认（`#dcdfe6`/`#ebedf0`，约 1.2–1.4:1），观感会更"实"。这是"合规优先"的取舍：**若设计评审认为观感不可接受**，则必须把输入框改为"浅底填充 + 深色下划线"等替代方案来满足 1.4.11，不能既不加深描边也不给替代。此点需评审明确拍板，不得默认放过。

### 2.7 状态色映射（三端必须一致）

色板语义见 2.2「语义色族」。**组件形态**：PC = 浅底实心标签（`border + 浅底 + 600/700 文字`）；移动 = 同一配色，`radius-full` 胶囊，尺寸 20px 高。两端形态统一（修 P5）。

**包裹状态（`PARCEL_STATUS`，字典值不可改）**

| 值 | 文案 | 语义 | 底 | 字 | 对比度 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 0 | 待入库 | 中性 | `#EDF0F4` | `#4B5563` | 6.50:1 |
| 1 | 在库待取 | 警告 | `#FFFBE6` | `#B45309` | 4.71:1 |
| 2 | 已取件 | 成功 | `#F6FFED` | `#237804` | 5.15:1 |
| 3 | 异常 | 危险 | `#FFF1F0` | `#CF1322` | 4.93:1 |
| 4 | 已退回 | 中性（描边式，与 0 区分） | `#FFFFFF` + 1px `#CBD2DA` | `#6B7280` | 4.83:1 |

**工单状态（`WORK_ORDER_STATUS`）**

| 值 | 文案 | 语义 | 配色 |
| ---- | ---- | ---- | ---- |
| 0 | 待处理 | 警告 | `#FFFBE6` / `#B45309` |
| 1 | 处理中 | 品牌 | `#E8F4FF` / `#0958D9`（5.27:1） |
| 2 | 已解决 | 成功 | `#F6FFED` / `#237804` |
| 3 | 已关闭 | 中性描边 | `#FFFFFF` + `#CBD2DA` / `#6B7280` |

**工单优先级（`WORK_ORDER_PRIORITY`）**

| 值 | 文案 | 形态 | 配色 |
| ---- | ---- | ---- | ---- |
| 0 | 低 | 描边 | `#FFFFFF` + `#CBD2DA` / `#6B7280` |
| 1 | 中 | 浅底 | `#FFFBE6` / `#B45309` |
| 2 | 高 | **实心（唯一实心优先级）** | `#CF1322` 底 + `#FFFFFF` 字（5.57:1） |

> 规则：**实心 = 需要立刻行动**（高优先级、超时）；浅底 = 状态描述；描边 = 无动作诉求。现状"实心/空心随机"（P30）由此收敛。

**同步任务状态（`SYNC_STATUS`）**

| 值 | 文案 | 配色 | 附加 |
| ---- | ---- | ---- | ---- |
| 0 | 待领取 | 中性描边 `#FFFFFF` + `#CBD2DA` / `#6B7280` | — |
| 1 | 执行中 | 品牌浅底 `#E8F4FF` / `#0958D9` | 图标可加 1.2s 脉冲（`prefers-reduced-motion` 下静止） |
| 2 | 成功 | 成功浅底 `#F6FFED` / `#237804` | — |
| 3 | 失败 | 危险浅底 `#FFF1F0` / `#CF1322` | — |

**同步日志级别（`SYNC_LOG_LEVEL`）**

| 值 | 文案 | 文字色 | 时间线轴点 |
| ---- | ---- | ---- | ---- |
| 0 | INFO | `#4B5563` | `#CBD2DA` |
| 1 | WARN | `#B45309` | `#FAAD14` |
| 2 | ERROR | `#CF1322` | `#FF4D4F` |

**SLA 超时（三端统一四态）**

| 态 | 判定 | 形态 | 配色 |
| ---- | ---- | ---- | ---- |
| 正常 | 剩余 > 25% 或 > 6h | 纯文本（不占标签位） | `#4B5563` |
| 临近 | 剩余 ≤ 25% 或 ≤ 6h | 浅底胶囊 | `#FFFBE6` / `#B45309` + 时钟图标 |
| 超时 | 剩余 < 0 | **实心胶囊** + 时钟图标 | `#CF1322` 底 + `#FFFFFF` 字（5.57:1） |
| 已终结 | 已解决/已关闭 | 不显示 | — |

> 阈值现状为"剩余 ≤1h"(PC [SlaCountdown.vue:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/components/SlaCountdown.vue))，移动端无临近态（[SlaTag.vue:27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/SlaTag.vue) 只有 over/非 over）→ 统一为"剩余 ≤ 该优先级 SLA 总时长的 25%"（低=12h / 中=6h / 高=2h），PC 与移动一致。

### 2.8 Element Plus 变量映射（PC）

```scss
/* ---------- L3：Element Plus 2.9 变量覆盖（不引主题包，不引新依赖） ---------- */
:root {
  /* 主色：承白字实底取 blue-700（白字 6.16:1）；浅色阶由 mix(base, #fff, {30,50,70,80,90}%) 推导 */
  --el-color-primary:              var(--c-blue-700);   /* #0958D9 */
  --el-color-primary-light-3:      #538AE4;  /* ⚠ 与 2.2 蓝族不通用，仅供 Element 内部浅色场景 */
  --el-color-primary-light-5:      #84ACEC;
  --el-color-primary-light-7:      #B5CDF4;
  --el-color-primary-light-8:      #CEDEF7;
  --el-color-primary-light-9:      #E6EEFB;
  --el-color-primary-dark-2:       #0746AE;

  /* 语义色：文字色档位（保证标签文字 ≥4.5:1） */
  --el-color-success:              #237804;
  --el-color-success-light-3:      #6AA84F;
  --el-color-success-light-5:      #9DC98A;
  --el-color-success-light-7:      #CDE5C1;
  --el-color-success-light-8:      #DEF0D6;
  --el-color-success-light-9:      #EEF9E8;   /* mix(#52C41A, #fff, 90%) */
  --el-color-success-dark-2:       #1C6003;

  --el-color-warning:              #B45309;
  --el-color-warning-light-3:      #D08A50;
  --el-color-warning-light-5:      #E0AD84;
  --el-color-warning-light-7:      #EFD2BC;
  --el-color-warning-light-8:      #F4E0D2;
  --el-color-warning-light-9:      #FEF7E8;   /* mix(#FAAD14, #fff, 90%) */
  --el-color-warning-dark-2:       #903F04;

  --el-color-danger:               #CF1322;
  --el-color-danger-light-3:       #DD5862;
  --el-color-danger-light-5:       #E78990;
  --el-color-danger-light-7:       #F1B7BC;
  --el-color-danger-light-8:       #F6CED1;
  --el-color-danger-light-9:       #FFEDED;   /* mix(#FF4D4F, #fff, 90%) */
  --el-color-danger-dark-2:        #A60F1B;

  --el-color-info:                 var(--c-neutral-600);   /* #4B5563，7.56:1 */
  --el-color-info-light-3:         #8B9199;
  --el-color-info-light-5:         #AEB2B8;
  --el-color-info-light-7:         #D1D3D7;
  --el-color-info-light-8:         #E3E4E7;
  --el-color-info-light-9:         #EDEEEF;
  --el-color-info-dark-2:          #3C444F;

  /* 文本 */
  --el-text-color-primary:         var(--c-neutral-800);   /* 替换 #303133 */
  --el-text-color-regular:         var(--c-neutral-600);   /* 替换 #606266 */
  --el-text-color-secondary:       var(--c-neutral-500);   /* 替换 #909399（3.08 → 4.83） */
  --el-text-color-placeholder:     var(--c-neutral-400);
  --el-text-color-disabled:        var(--c-neutral-300);

  /* 背景 / 描边 */
  --el-bg-color:                   var(--c-neutral-0);
  --el-bg-color-page:              var(--c-neutral-50);    /* 替换 #f0f2f5 */
  --el-bg-color-overlay:           var(--c-neutral-0);
  --el-border-color:               var(--c-neutral-200);
  --el-border-color-light:         var(--c-neutral-200);
  --el-border-color-lighter:       var(--c-neutral-100);
  --el-border-color-extra-light:   var(--c-neutral-100);
  --el-border-color-dark:          var(--c-neutral-300);
  --el-border-color-darker:        var(--border-control);   /* = --c-neutral-450 #8A93A0（3.10:1）；--c-neutral-400 已由 --text-placeholder 消费 */
  --el-fill-color-light:           var(--c-neutral-50);
  --el-fill-color-lighter:         var(--c-neutral-25);

  /* 尺寸 / 圆角 / 字号 */
  --el-border-radius-base:         var(--r-sm);   /* 6px */
  --el-border-radius-small:        var(--r-xs);   /* 4px */
  --el-border-radius-round:        var(--r-full);
  --el-font-size-base:             14px;
  --el-font-size-small:            12px;
  --el-font-size-extra-small:      12px;
  --el-font-size-large:            16px;
  --el-component-size:             32px;          /* 表单控件高度（PC 鼠标精度下 ≥24px 且舒适） */
  --el-component-size-small:       28px;
  --el-component-size-large:       36px;

  --el-box-shadow-light:           var(--e2);
  --el-box-shadow-lighter:         var(--e1);

  /* 侧栏（自定义变量，layout 使用） */
  --aside-w:                       210px;
  --aside-w-collapsed:             64px;
  --header-h:                      60px;
  --table-row-h:                   44px;
  --table-head-h:                  44px;
  --drawer-w:                      520px;

  /* 状态标签尺寸（PC 24 / 移动 20，两端取值不同属合法平台差异，故不进真源） */
  --tag-h:                         24px;
  --tag-pad-x:                     8px;
}
```

**与 Element 默认行为的差异（必须显式覆盖，理由随行）**

```scss
/* 主按钮 hover 由「变浅」改为「加深」：Element 默认 hover 用 light-3（#538AE4）配白字仅 3.43:1。
   加深到 blue-800（#0745A8，白字 8.66:1）后 hover 态同样满足 AA。 */
.el-button--primary {
  --el-button-bg-color: var(--c-blue-700);
  --el-button-border-color: var(--c-blue-700);
  --el-button-hover-bg-color: var(--c-blue-800);
  --el-button-hover-border-color: var(--c-blue-800);
  --el-button-active-bg-color: var(--c-blue-900);
  --el-button-active-border-color: var(--c-blue-900);
}

/* 卡片一律描边、不投影（PC 后台密度大，阴影叠表格会脏） */
.el-card { border-radius: var(--r-md); border-color: var(--border-line); }

/* 表格：行高与字号对齐 Token；表头不参与斑马纹 */
.el-table {
  --el-table-header-bg-color: var(--c-neutral-50);
  --el-table-row-hover-bg-color: var(--c-blue-50);
  --el-table-border-color: var(--border-line);
  font-variant-numeric: tabular-nums;
}
.el-table th.el-table__cell { height: var(--table-head-h); font-weight: 600; color: var(--c-neutral-600); }
.el-table td.el-table__cell { height: var(--table-row-h); }

/* 数字列右对齐：由页面通过 column 的 align="right" 控制，不放全局 */

/* 焦点可见（补齐 SC 2.4.7）：全端最小可用焦点环 */
:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--c-blue-500);
  outline-offset: 2px;
  border-radius: var(--r-xs);
}
```

> U2/U3 验证方式见 0.3；`--el-button-*` 与 `--el-table-*` 变量名需按实际 `theme-chalk` 产物核对，若名称不同则改为等价的类选择器写法，语义与取值不变。

### 2.9 Vant 4 变量映射（移动端）

```scss
:root {
  /* ---------- 根级品牌色（确定项） ---------- */
  --van-primary-color:  var(--c-blue-700);   /* #0958D9，白字 6.16:1 */
  --van-success-color:  #237804;             /* 5.59:1 */
  --van-danger-color:   #CF1322;             /* 5.57:1 */
  --van-warning-color:  #B45309;             /* 5.02:1 */

  --van-text-color:     var(--c-neutral-800);   /* 替换 #323233 */
  --van-text-color-2:   var(--c-neutral-600);   /* 7.56:1 */
  --van-text-color-3:   var(--c-neutral-500);   /* 4.83:1（替换 #969799 / 2.93:1） */
  --van-background:     var(--c-neutral-50);
  --van-background-2:   var(--c-neutral-0);
  --van-border-color:   var(--c-neutral-200);
  --van-radius-md:      var(--r-sm);   /* 8px → 6px，与 PC 输入控件一致 */
  --van-radius-lg:      var(--r-lg);   /* 12px */
  --van-font-size-md:   14px;
  --van-font-size-sm:   12px;

  /* ---------- 组件级（待按 v4.10 源码核对，见 U1） ---------- */
  --van-nav-bar-height:          44px;
  --van-nav-bar-background:      var(--c-neutral-0);
  --van-nav-bar-title-text-color: var(--c-neutral-800);
  --van-tabbar-height:           50px;
  --van-tabbar-item-active-color: var(--c-blue-700);
  --van-tabbar-item-text-color:   var(--c-neutral-500);
  --van-cell-font-size:          14px;
  --van-cell-vertical-padding:   var(--sp-3);   /* 12px */
  --van-cell-horizontal-padding: var(--sp-4);   /* 16px */
  --van-cell-value-color:        var(--c-neutral-600);
  --van-cell-group-inset-padding: 0;
  --van-button-primary-background: var(--c-blue-700);
  --van-button-primary-border-color: var(--c-blue-700);
  --van-button-border-radius:      var(--r-sm);
  --van-tag-primary-color:        var(--c-blue-700);
  --van-empty-description-color:  var(--c-neutral-500);
  --van-progress-color:           var(--c-blue-500);
  --van-field-placeholder-text-color: var(--c-neutral-400);
  --van-field-input-text-color:   var(--c-neutral-800);
}
```

**移动端自定义组件层（L3，Vant 之外）**

```scss
:root {
  --safe-top:  var(--status-bar-height, 0px);           /* 壳注入；浏览器为 0 */
  --safe-bottom: env(safe-area-inset-bottom, 0px);
  --tabbar-h: 50px;
  --navbar-h: 44px;
  --actionbar-h: 56px;                                   /* 详情页固定操作栏 */
  /* 页面底部内边距 = 操作栏 + 安全区 + 8px */
  --page-pad-bottom: calc(var(--actionbar-h) + var(--safe-bottom) + var(--sp-2));
  --page-pad-bottom-tab: calc(var(--tabbar-h) + var(--safe-bottom) + var(--sp-2));
  --grad-hero: linear-gradient(135deg, var(--c-blue-700) 0%, var(--c-blue-800) 100%);
  --grad-hero-deep: linear-gradient(135deg, var(--c-neutral-800) 0%, var(--c-neutral-900) 100%);
}
```

> **Hero 渐变决策（D-3）**：现状 `#1677ff → #3f8cff`，浅端配 12px 白字仅 3.28:1。新渐变两端取 `#0958D9`(6.16) 与 `#0745A8`(8.66)，**全段任何位置的白字都满足 AA**，不需要"文字只能压在暗部"这种脆弱约束。

### 2.10 动效 Token

```scss
:root {
  --dur-fast: 120ms;   /* hover / active / 按键反馈 */
  --dur-base: 200ms;   /* 展开折叠、Tab 指示器、骨架淡出 */
  --dur-slow: 300ms;   /* 抽屉、弹层、页面切换 */
  --ease-std: cubic-bezier(.4, 0, .2, 1);
  --ease-in:  cubic-bezier(0, 0, .2, 1);
  --ease-out: cubic-bezier(.4, 0, 1, 1);
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
}
```

---

## 3. 三端视觉定位与差异约定

### 3.1 定位一句话

| 端 | 用户 | 场景 | 视觉主张 |
| ---- | ---- | ---- | ---- |
| 网页端 | 管理员（总部） | 桌面、长时间批量操作（20 万包裹、批量工单） | **「数据密集 · 克制 · 可扫读」**：满屏信息、弱装饰、强列对齐、表头固定、一切为"快速定位与批量操作"服务 |
| 老板端（移动·ADMIN） | 经营决策者 | 碎片时间看全局（通勤/会议间隙） | **「少而醒目 · 先看趋势与异常」**：首屏 ≤3 个信息块，大字号数值，深色 Hero 报头建立"经营报告"心智 |
| 员工端（移动·站长/员工） | 一线作业者 | 站点现场、单手持机、可能戴手套 | **「待办优先 · 一步到位」**：首屏即"今天要做什么"，关键动作固定可达（不靠滚动），列表高密度、触控目标大 |

### 3.2 老板端 vs 员工端 差异表（同一套 Vant 组件下）

| 维度 | 老板端 | 员工端 | 共享（必须一致） |
| ---- | ---- | ---- | ---- |
| Hero 底色 | `--grad-hero-deep`（`#1F2937 → #111827` 深蓝灰）＋ 1px 主色顶边 | `--grad-hero`（`#0958D9 → #0745A8` 品牌蓝） | 高度 88px、圆角 `--r-lg`、白字阶梯、范围/角色 chip 形态 |
| Hero 内容 | 今日日期+星期 / 数据截止时间 / **口径 chip（全域｜本站）** | 驿站名 / 姓名+角色 chip / **今日待处理条数** | — |
| 主指标数值字号 | `Num-lg` 24 / 600 | `Num-lg` 22 / 600 | 指标卡结构（标签 12 / 数值 / 单位 12 / 环比 12） |
| 指标卡的"第一强调" | 今日入库 | 待取件 | 卡片圆角/描边/内边距一致 |
| 图标色语义 | 蓝=规模、橙=待处理、红=异常 | 蓝=入库、橙=待取、绿=已取、红=异常 | 语义色 Token 一致 |
| 首屏块数 | Hero + 4 指标 + 异常条 + **趋势图（96px 迷你，末值标注）** | Hero + 4 指标 + 异常条 + **4 宫格快捷入口** | 到 Tabbar 的距离 ≤ 1 屏 |
| 区块间距 | `--sp-4`(16) | `--sp-3`(12) | 间距 Token 一致 |
| 列表项信息行数 | ≤2 行（超 2 行改折叠） | 3 行（标题 / 元信息 / 警示） | 列表项 Token（内边距、圆角、行高） |
| 强调色主责 | 橙 `#FA8C16`（关注/待办） | 蓝 `#1890FF`（作业/进行中） | 危险/成功语义色一致 |
| 首页末块 | 组织规模（默认折叠） | 本站汇总一句话（12px Caption） | — |
| Tabbar | 总览/趋势/排行/预警/我的（5） | 工作台/包裹/工单/通知/我的（5） | Tabbar 高 50px、图标 22px、选中 `#0958D9`、未选中 `#6B7280` |

**品牌一致性锚点（三端共用，不可分叉）**：主色阶、语义色、状态色映射表、字号阶梯、间距 Token、圆角/阴影、图表规范、Tag 形态。

### 3.3 入口页（index.html）定位

保留"演示工具"属性（三卡片跳转 + 剧本自检 + 重置），但外观必须与系统同源：主色 `#1890FF`（描边/hover）、按钮实底 `#0958D9`、卡片描边 `#E3E7ED`、圆角 `--r-md(8)`、卡片 hover 位移 2px + `--e2`；三张端卡片各加 4px 顶部色条区分端（蓝/深蓝灰/蓝，其中移动两张用同色系深浅区分）；删除 24px 以上的自造字号（统一 H1 20 / H2 16 / Body 14 / Caption 12）。

---

## 4. 组件设计规范

> 通用要求：每个组件必须给出 **anatomy（构成）· variants（变体）· states（状态全覆盖）· token 映射 · 无障碍要求**。
> 状态必须覆盖：default / hover / active / focus-visible / disabled / loading / empty / error（不适用者显式写"不适用"）。

### 4.1 PC 组件

#### C-P1 `PageHeader`（新建）

- **Anatomy**：`① 标题(H1 20/600)` + `② 副信息行(Caption 12/400, `--c-neutral-500`)` + `③ 右侧操作区(按钮组, gap 8)`
- **Variants**：`with-sub`（带副信息，如"数据范围：全域 · 共 200,000 条 · 更新于 10:24"）/ `plain`（仅标题+操作）
- **States**：静态组件；`loading` 时副信息行替换为 12px 骨架条（宽 120px）
- **Token**：上下间距 `--sp-4`(下) / 无上间距（夹在 `el-main` 的 `--sp-4` 内边距里）
- **A11y**：标题用 `<h1>`；操作区按钮保持 Element 原生语义；不得用 `<div>` 承载标题
- **为什么建**：修 P6（4 个页面无标题）。一期冻结页无此结构，属**新增规范**，见 9.3 D-2

#### C-P2 `MetricCard`（新建，替换看板内联卡 + 收敛 MiniStats）

- **Anatomy**：`① 左侧图标容器 40×40（`--r-md`，语义 50 档浅底 + 500 档图标 20px）` + `② 标签 Caption 12/400 `--c-neutral-500`` + `③ 数值 Num-lg 26/600 tabular-nums` + `④ 单位/环比 Caption 12` + `⑤ 下沿 2px 语义色条（可选 variant）`
- **Variants**：`hero`（看板主指标，26px，含环比箭头）/ `plain`（次级指标条，20px，无图标）/ `inline`（表格页统计条，20px，无图标无环比）
- **States**：default / hover（`border-color: --c-blue-200`，`--e2`，`translateY(-1px)`，仅当 `clickable`）/ active（`translateY(0)`，`--e1`）/ focus-visible（2px 主色环）/ disabled（不适用）/ loading（数值处 28×16 骨架）/ empty（数值显示 `—`，标签保留）/ error（数值 `—` + 卡右上加 16px 危险图标 + `title` 提示）
- **Token**：卡片 `bg --c-neutral-0` / `border 1px --border-line` / `--r-md` / `--sp-4` / 无阴影（`--e0`）
- **A11y**：可点击时必须是 `<button>` 或带 `role="button" tabindex="0"` + 回车/空格触发；数值加 `aria-label="{标签} {数值}{单位}"`；环比箭头必须有文字方向（`↑12%` 而非仅箭头）
- **为什么建**：修 P7（4 种数值字号）、P8（shadow 混用）、P9（栅格算法）、P13（负 margin）

#### C-P3 `StatusTag`（改造）

- **Anatomy**：`1px 描边 + 浅底 + 600 档文字`，高 24px，内边距 `2px 8px`，`--r-xs`
- **Variants**：`soft`（默认，浅底）/ `outline`（中性描边式，用于"已关闭/已退回/低优先级"）/ `solid`（实心白字，仅"高优先级/超时"）
- **States**：default / hover（不改色，无交互，仅 `cursor: default`）/ 其余不适用；`value` 无匹配 → 渲染 `—`（`--c-neutral-300`）
- **Token**：取自 2.7 映射表；**实现方式**：在组件内建立 `type → { bg, fg, border }` 的 Token 映射（不改 `shared/constants/dict.js`），`info` 映射为中性；`dict` 传入保持现状
- **A11y**：色 + 文字双通道（本就含文案，合格）；`solid` 形态必须用 600 档底色（见 2.3）
- **修**：P5（两端形态统一）、P30（实心/空心语义化）

#### C-P4 `SlaCountdown`（改造）

- **Anatomy**：`时钟图标 14px + 文本 Caption 12/400`，胶囊 `--r-full`，高 22px
- **Variants**：`text`（正常态，无底无框，`--c-neutral-600`）/ `soft`（临近，`#FFFBE6`/`#B45309`）/ `solid`（超时，`#CF1322` 底白字）
- **States**：normal / warning / danger / muted（终态或空 deadline 显示 `—`）；倒计时随 30s 心跳更新（保留现实现）
- **Token**：阈值改为"剩余 ≤ SLA 总时长 25%"（低 12h / 中 6h / 高 2h），`deadline` 旁需传 `priority` 或在页面计算后传入 `thresholdMs`
- **A11y**：`aria-live="polite"` 不适用（30s 刷新会频繁打断读屏）→ 改为在超时**首次发生**时通过 `aria-live="polite"` 播报一次；文本必须含"已超时/剩余"，不能只有颜色

#### C-P5 `TrendChart`（改造）

见第 6 章图表规范。要点：改为 `ResizeObserver` 按容器宽度重绘（不再等比缩放文字）；Y 轴刻度改用 nice-number 算法消除重复；新增数据点、hover 十字线、图例开关（与移动端对齐）；`vector-effect="non-scaling-stroke"` 保证线宽恒定。

#### C-P6 `MiniStats`（改造为纯栅格容器）

- **Anatomy**：`el-row(gutter 16)` 包裹 N 个 `MetricCard variant="inline"`
- **Variants**：`cols=4`（`--sp` 基准：`:lg="6"`）/ `cols=3`（`:lg="8"`）；**列数由显式 prop 指定，不再用 `Math.round(24/n)` 计算**（修 P9）；`:md="8"`、`:sm="12"`、`:xs="12"`
- **States**：委托给 `MetricCard`；`loading` 时整排渲染 4 个骨架块；`error` 时整排改为 `StateBlock` 的错误态（修 P14）
- **A11y**：`el-row` 容器加 `role="list"`，每卡 `role="listitem"`

#### C-P7 `StateBlock`（新建，修 P14）

- **Anatomy**：`① 图标 48px（空=Box 描边 / 错误=WarningFilled 危险色）` + `② 主文案 14/400 --c-neutral-600` + `③ 次级文案 Caption 12 --c-neutral-500`（错误态给可操作建议）` + `④ 操作按钮（错误态必有"重试"）`
- **Variants**：`empty` / `error` / `denied`（无权限）
- **States**：三态互斥，`loading` 由 `el-skeleton` 承担（不在此组件内）
- **Token**：上下 `--sp-10`，按钮为 `type="primary" plain`（描边式，避免空态里出现强主色块）
- **A11y**：错误态容器 `role="alert"`；图标 `aria-hidden`
- **修**：包裹/同步/工单/通知 4 页"错误被当空态"

#### C-P8 列表页筛选栏（规范而非新组件）

- 结构：`el-card(border 1px, 无阴影, 内边距 16)` → `el-form inline`；控件高度 32（`--el-component-size`）；标签 `Caption`+`--c-neutral-600`；表单项之间 `--sp-4` 水平、`--sp-3` 垂直（换行时）
- 控件定宽规则（替换现状魔法值）：驿站/状态下拉 160px；时间范围 260px；关键字输入 200px；`重置` 用 `el-button`（default），`查询` 用 primary
- `States`：`loading` 时按钮 loading；筛选无结果 → 表格区 `StateBlock empty` 且文案含"当前筛选条件"

#### C-P9 抽屉（规范）

- 宽度统一 `--drawer-w: 520px`（< 1200px 视口时 `min(520px, 92vw)`）；标题用 `el-drawer` 默认（H2 16/600）；内容内边距 `--sp-5`；底部操作区 `el-drawer footer` + 上边框 `--border-line` + `--sp-3` 间隙；`el-descriptions` 统一 `:column="2" size="small" border`
- 修 P16（三处宽度/列数不一）

### 4.2 移动端组件

#### C-M1 `StatCard`（改造）

- **Anatomy**：`① 标签 Caption 12/400 --c-neutral-500` + `② 数值 Num-lg（老板 24 / 员工 22）/600 + 单位 12/400 --c-neutral-500` + `③ 环比行 Caption 12（`↑ 12.4%` 绿 / `↓ 3.1%` 红）`
- **Variants**：`primary`/`success`/`warning`/`danger`/`neutral`（决定数值色，取 600/700 档而非 500 档 → 保证 4.5:1）；`dense`（无环比，用于工作台）
- **States**：default / active（`transform: scale(.985)`，`--dur-fast`）/ focus-visible / disabled（`opacity .5` 不适用）/ loading（数值 60×24 骨架）/ empty（`—`）/ error（`—` + 12px 危险文案"加载失败"）
- **Token**：卡 `--c-neutral-0` / `--r-lg`(12) / `padding: var(--sp-4)` / `--e1`；数值与标签间距 `--sp-2`
- **A11y**：可点击时必须为 `<button type="button">` 或加 `role="button" tabindex="0"`；最小触控高度 **≥88px**（两列一格）
- **修**：P35（收口 trend 页 `.metric`）

#### C-M2 `LineChart`（改造）

见第 6 章。要点：补 `aria-label`；`compact` 增加"末值标注 + 极简刻度（仅最高/最低档）"；配色改 Token；图例触控 ≥44px 且加 `aria-pressed`。

#### C-M3 `StatusTag` / `C-M4 SlaTag`（改造）

- `StatusTag`：胶囊高 20px，内边距 `2px 6px`，`--r-full`，Caption 11/400（仅徽标级）；配色取 2.7 表；支持 `solid`（高优先级/超时）
- `SlaTag`：四态（正常不显示 / 临近 soft / 超时 solid / 终态隐藏）；阈值同 PC
- **A11y**：`van-tag` 文字必须可读（对比度按 2.7 表）；超时态加 `aria-label="已超 SLA"`

#### C-M5 `PageNav`（改造）

- **Anatomy**：左返回（44×44 热区，含 `aria-label="返回"`）/ 中标题 H2 16/600（长标题 `ellipsis` 且 `max-width: 60%`）/ 右插槽（内容最小 44×44）
- **States**：静态；`back=false` 时不渲染左按钮（Tab 页）
- **Token**：`--navbar-h: 44px`；`top: var(--safe-top)`；`placeholder` 保留

#### C-M6 `PageState`（改造）

- **Anatomy**：`loading → van-skeleton(row=N, 圆角 12)`；`error → 图标 40 + 文案 14 + 「重新加载」按钮（高 44，描边式）`；`empty → 图标 48 + 文案 14 --c-neutral-500 + 可选引导按钮`
- **States**：三态互斥；**新增 300ms 延迟显示规则**：加载 <300ms 不显示骨架（避免闪一下），实现为 `setTimeout 200ms` 后置 `loading=true`，数据到达则清定时器
- **A11y**：`error` 容器 `role="alert"`；空态/错误态图标 `aria-hidden="true"`；重试按钮 ≥44px
- **修**：P14 的移动端版本、P37 的跳变

#### C-M7 `IdentitySwitcher`（改造）

- 行高 ≥56px（现 ≈57px，达标）；选中态改 `--c-blue-50` 底 + `--c-blue-500` 1px 描边 + 右侧 16px 勾选图标（**不只靠颜色**）；描述文字 12px `--c-neutral-500`
- **A11y**：容器 `role="radiogroup"`，每项 `role="radio" aria-checked`；`switching` 时禁用全部并 `aria-busy="true"`
- **修**：P27（浅底色收敛）、P33

#### C-M8 `ActionBar`（新建，修 P31）

- **Anatomy**：固定底栏，`height: var(--actionbar-h)` + `padding-bottom: var(--safe-bottom)`；主按钮 `flex:1` 高 44（primary）+ 次按钮 44（outline/danger outline）；上沿 1px `--border-line` + `--e3` 反投影
- **Variants**：`single`（仅主操作，如取件核销）/ `dual`（主+次，如"取件核销 / 上报异常"）/ `multi`（≥3 操作时改为"主操作 + 更多"底部弹层，避免按钮堆叠）
- **States**：`disabled`（主按钮禁用时给出原因文案，如"当前状态不支持取件"）；`loading`（按钮内 loading）；承载空态时隐藏整栏
- **A11y**：容器 `role="toolbar"`；按钮 ≥44×44
- **配套规则**：使用 `ActionBar` 的页面根容器 `padding-bottom: var(--page-pad-bottom)`

---

## 5. 逐页重设计规范

### 5.1 PC 统一框架（`layout/index.vue`）

```text
┌──────────────┬──────────────────────────────────────────────────────────┐
│ Logo 64×60   │ [≡44] 组织管理 / 驿站管理                  [头像28 名 角色▾] │ ← header 60
├──────────────┼──────────────────────────────────────────────────────────┤
│ 数据看板      │  ← 当前项：左侧 3px 主色条 + 文字 #FFF + 底 rgba(255,255,255,.08)
│ 员工管理      │     · 图标 18px；菜单项高 44px；缩进 16px；字号 14/400
│ 包裹管理      │                                                          │
│ ▾ 组织管理    │  内容区（padding 16；底色 #F5F7FA）                       │
│    部门管理   │                                                          │
│    驿站管理   │                                                          │
│ 同步任务      │                                                          │
│ 工单管理      │                                                          │
│ 通知中心      │                                                          │
│ 个人中心      │                                                          │
└──────────────┴──────────────────────────────────────────────────────────┘
```

| 项 | 规格 |
| ---- | ---- |
| 侧栏底 | `--c-neutral-800 #1F2937`（替换 `#001529`，修 P22） |
| 侧栏宽 | 展开 210 / 折叠 64；`< 1200px` 视口**自动折叠为 64**（现状仅手动折叠，宽屏以下内容被压缩） |
| Logo 区 | 高 60px；图标 22px 主色 `#1890FF`；标题 15/600 `#FFFFFF`（仅展开态） |
| 菜单 | 底 `transparent`；默认色 `rgba(255,255,255,.72)`（对 `#1F2937` 约 10.4:1）；激活色 `#FFFFFF` + 3px 左主色条；hover 底 `rgba(255,255,255,.06)`；分组标题 `Caption 12/400 rgba(255,255,255,.45)` |
| 头部 | 高 60px；底 `#FFFFFF`；下边框 `--border-line`；左右内边距 `--sp-5`(20) |
| 折叠按钮 | **改 `<button>` 高 32×32 热区**（`aria-label="折叠/展开侧边栏"`，`aria-expanded`），`focus-visible` 主色环（修 P20） |
| 用户菜单 | **改 `<button>`**，高 36px，`aria-haspopup="menu"`；头像 28px 用 `#0958D9` 底白字；角色标签用 `StatusTag variant=outline`；去掉 `outline:none`（修 P20） |
| 面包屑 | 保留（`Separator "/"`，色 `--c-neutral-500`，最后一项 `--c-neutral-800`） |
| 内容区 | `padding: var(--sp-4)`；底 `--c-neutral-50`；`overflow-y: auto`；**表格页表头吸顶**（`el-table :height` 或 CSS `position: sticky` on `thead`，实现方式由前端按可行性选择） |

**三态**：框架级无 loading/empty；`401` 由 [pc/main.js:46-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/main.js) 订阅兜底（保留）。**新增**：路由切换时内容区顶部 2px 主色进度条（Element 无内置，可用 CSS 动画 300ms 两条横线，或省略——列为 P2）。

### 5.2 PC 数据看板（`views/dashboard/index.vue`）

```text
数据看板                                                          [刷新]
数据范围：全域 · 包裹 200,000 件 · 更新于 10:24                        ← PageHeader 副信息
┌────────────┬────────────┬────────────┬────────────┐
│ [icon] 今日入库  今日取件  在库待取  异常件   │ ← MetricCard hero（26/600）
│        1,286 ↑12%  1,102  3,410   37 ▲     │   卡片可点 → 下钻
└────────────┴────────────┴────────────┴────────────┘
┌──────────┬──────────┬──────────┬──────────┐
│ 包裹总量  │ 今日取件率│ 同步成功率│ 超SLA工单 │ ← MetricCard inline（20/600，4 列）
│ 200,000  │ 85.7%    │ 99.2%    │ 3        │
└──────────┴──────────┴──────────┴──────────┘
┌────────────────────────────────┬──────────────────┐
│ 包裹趋势         [近7天|近30天] │ 同步健康度        │
│ ┌────────────────────────────┐ │ ┌──────────────┐ │
│ │  折线 220px，双系列         │ │ │   99.2%      │ │ ← Num-lg，环或条 8px
│ │  图例可点 · hover 十字线读数 │ │ │ 最近批次成功率│ │
│ │  Y 轴 4 档（nice number）    │ │ └──────────────┘ │
│ └────────────────────────────┘ │ 同步失败驿站 2 个 │ ← Caption 行
│                                │ 最近批次 10:18    │
├────────────────────────────────┼──────────────────┤
│ 驿站排行 TOP5  [量|率|异常]     │ 工单 / SLA        │
│ ① 城东驿站  12,480 ▓▓▓▓▓▓ 85.6%│ 待处理 12 处理中 4 │ ← 2 列 4 项 + 全宽 1 项
│ ② 城西驿站  ...                 │ 今日新增 7          │
│ ③ ...                          │ 超 SLA 3  ← danger │
│                                │ 平均处理时长 3.4h   │ ← 全宽独占行
└────────────────────────────────┴──────────────────┘
┌──────────────────────────────────────────────────┐
│ 组织规模（一期口径）             员工 56 · 驿站 8  │ ← 默认折叠，点击展开
└──────────────────────────────────────────────────┘
```

**要点与修法**

1. **信息优先级重排**：包裹/同步/工单为一等公民（Row1–Row3），一期 4 指标降为可折叠末块（修"看板主题与业务重心错位"）。
2. 删除全部渐变图标：`MetricCard` 用 40×40 `--r-md` 浅底（`--c-blue-50` 等）+ 500 档图标（**删除紫色**，修 P3）。
3. 数值字号从 28/26/22/20 四种收敛为 `Num-lg 26` + `Num-sm 20` 两种（修 P7）。
4. 排行：**弃用 `el-table`，改为排行行组件**（行高 56，构成：徽标 24×24 + 站名 14/600 + 数值 `Num-sm` 右对齐 + 进度条 100%×6px + 副信息 Caption）；徽标 1/2/3 名用 `#B45309 / #6B7280 / #92400E` 实底白字，4+ 名用 `#F5F7FA` 底 + `#4B5563` 数字（修 P18 的"冠军红"）。进度条色随口径：包裹量 `--c-blue-500`、取件率 `--c-green-600`、异常率 `--c-red-500`（与移动端一致）。
5. 工单/SLA：`wo-grid` 改为 **4 项 2×2 + 时长项全宽独占**（修 P19）；超 SLA 项数值用 `#CF1322`（原 `#f56c6c`）。
6. 同步健康度：`el-progress` 的 `status` 逻辑保留，但 `stroke-width 8`、色取 `--c-green-600`（成功）/`--c-amber-500`（有失败）；百分比数值 26px。
7. 删除 `el-alert` 的复合失败提示？**保留**（多区块并行加载需局部降级能力）——但改为 `StateBlock error` 内联在失败区块位置，顶部不再堆一条全局 alert（更精确的定位，避免"全局红条但不知哪块挂了"）。

**三态**

| 态 | 设计 |
| ---- | ---- |
| loading | 各区块**独立骨架**（指标卡 4 个灰块；趋势区 220px 高灰块；排行 5 行灰条），不整页 `v-loading`（修现状整页遮罩 + 局部混合） |
| empty | 包裹趋势无数据 → 图表区 `StateBlock empty`（"近 7 天暂无入库数据"）；排行无数据 → 5 行占位条 + 文案 |
| error | 每个区块独立 `StateBlock error`（"包裹趋势加载失败" + 重试按钮，重试仅重拉该区块） |

### 5.3 PC 列表页模板（包裹 / 同步 / 工单 / 通知 共用）

```text
包裹管理                                                     [导出 CSV] [刷新]
数据范围：全域 · 共 200,000 件 · 更新于 10:24
┌─ 筛选卡（描边 1px，无阴影，内边距 16）───────────────────────────┐
│ 驿站[160▾] 状态[160▾] 入库时间[260 日期范围] 运单号[200____] [查询][重置] │
└──────────────────────────────────────────────────────────────┘
┌─ 内容卡 ─────────────────────────────────────────────────────┐
│ 共 200,000 条 · 已筛选 3 个条件                    [刷新 ⟳]     │ ← 工具行 Caption
│ 运单号▲       状态  驿站    收件人 手机号    货架  入库时间  操作 │ ← 表头吸顶 44px
│ SF1234…    [在库]  城东    张*   138****5678 A-12  09-17 10:12 详情│ ← 行高 44，hover #E8F4FF
│ ...                                                          │
│                                              [共 20000 条 分页] │
└──────────────────────────────────────────────────────────────┘
```

**规则**

| 项 | 规格 |
| ---- | ---- |
| 页头 | 必有（`PageHeader`，修 P6）；副信息含**数据范围 + 结果总数 + 更新时间**（数据范围对 STATION_ADMIN/STAFF 显示"本站：城东驿站"，与 Mock 收敛口径一致） |
| 统计条 | 仅包裹页保留（`MiniStats cols=6`），标题"今日指标"；`stat-hint` 的负 margin **删除**，改为在 `MiniStats` 内统一控制其上间距（修 P13） |
| 表格 | 统一 `border`(1px `--border-line`) + **不用斑马纹**（去掉 `stripe`，改 hover 高亮 `--c-blue-50`，修 P15）；数字列 `align="right"`；长文本列 `show-overflow-tooltip`；操作列 `fixed="right"` |
| 行高 | 44px（`--table-row-h`），表头 44px，表头底 `--c-neutral-50`、文字 600 |
| 操作按钮 | 表格内一律 `link` 型；危险操作（重试/关闭）用 `type="danger"`；**禁用态必须带 `title` 说明原因**（如"仅失败批次可重试"，修现状"禁用但不说为什么"） |
| 抽屉 | 统一 `--drawer-w` + `:column="2" size="small"`（修 P16）；抽屉底部为操作区（工单页保留） |
| 分页 | 右下对齐，上间距 `--sp-4`；默认 20/页（现状 10/页，包裹页 20 万数据下 10/页翻页成本过高）；可选 20/50/100 |
| 导出 | 按钮 default，右侧 Caption 提示"最多导出前 500 条"（保留，改字号 12） |
| 筛选回填 | 筛选条件与 URL query 同步（`stationId/status/startTime/endTime/waybillNo`）——**新增**，修复"刷新丢筛选"（现状无 URL 同步） |

**三态（4 页统一，修 P14）**

| 态 | 设计 |
| ---- | ---- |
| loading | 表格 `v-loading` + 保留表头（Element 默认行为）+ 行骨架 8 行（新增，优于纯遮罩） |
| empty | `StateBlock empty`：无筛选 → "暂无包裹数据"；有筛选 → "当前筛选条件下没有包裹" + 「清空筛选」按钮 |
| error | `StateBlock error`：文案"包裹列表加载失败" + 「重试」；**不得**显示"没有数据" |

### 5.4 PC 工单管理（`views/workOrder/index.vue`）

```text
工单管理                                                    [刷新]
数据范围：全域 · 待处理 12 · 超 SLA 3
┌─ Tab 条（独立于卡片，下边框 1px，选中项主色下划线 2px）────────────┐
│ 全部 12  待处理 3  处理中 4  已解决 5  已关闭 0  超SLA 3            │
└──────────────────────────────────────────────────────────────┘
┌─ 筛选卡 ─────────────────────────────────────────────────────┐
│ 驿站[160▾] 类型[160▾] 优先级[160▾] 关键字[200____] [查询][重置]   │
└──────────────────────────────────────────────────────────────┘
┌─ 内容卡 ─────────────────────────────────────────────────────┐
│ SLA 口径：低 48h / 中 24h / 高 8h · 超时仅高亮提醒              │ ← Caption
│ 工单号  类型  优先级 标题      驿站  上报人 处理人  SLA    状态 操作│
│ WO-…   [包裹] [高] 包裹破损…  城东  王强   李四  已超时[2h] [待] 详情│ ← 超时行：左侧 3px 红条
└──────────────────────────────────────────────────────────────┘
```

| 项 | 规格 |
| ---- | ---- |
| Tab 条 | 从卡片内移出到卡片外（现状 Tab + 筛选 + 表格挤在同一 `el-card` 内，层次不清）；Tab 高 40px，标签含计数（`全部 12`），超 SLA Tab 计数用 `#CF1322` |
| 超时行高亮 | 弃 `!important` 背景，改为**左侧 3px `#FF4D4F` 竖条 + 行底 `#FFF7F7`**（通过 `cell-class-name` 或行内 `box-shadow: inset 3px 0 0`，实现方式由前端选）；同时保留 SLA 单元格的红色胶囊（双通道，不依赖颜色单一） |
| SLA 列 | 宽 140px，含胶囊 + 截止时间（Caption） |
| 优先级列 | 宽 80px，按 2.7 表（高=实心红，中=浅底橙，低=描边灰） |
| 详情抽屉 | 宽 `--drawer-w`；区块顺序：基础信息（2 列 descriptions）→ 工单描述（正文 14/1.7）→ 流转记录（`el-timeline`，轴点色按 2.7 日志级别映射规则改为工作流动作色）→ 底部操作（指派 + 流转，`gap 8`，右侧无动作时显示 Caption 说明） |
| 指派弹窗 | 宽 420px；处理人下拉必填；确认按钮 loading；成功后 Toast 文案保留"已通知处理人" |
| 动作按钮 | 主操作（接单/解决）`primary`；关闭 `info`（**改**：现状 `action.target===3 ? 'info'`，关闭是终态但非危险操作，用 `info` 合理，保留）；驳回重开 `warning` plain（新增区分，避免与"接单"同款） |

**三态**：同 5.3；额外要求 Tab 切换时**保留上次滚动位置**（现状 `scrollBehavior` 全局 `top:0`，Tab 切换会跳到页顶；建议页内 Tab 切换不触发路由，故不受影响，但抽屉关闭后应回到原行——列为 P2）。

### 5.5 PC 通知中心（`views/notification/index.vue`）

| 项 | 规格 |
| ---- | ---- |
| 结构 | Tab（全部 / 未读 N）独立于卡片 → 工具行（共 N 条 · 未读 N 条 + 全部标记已读）→ 列表（卡片内，无表格，**保留自定义列表**，因为通知是"标题+正文"多行文本，表格不合适） |
| 列表项 | 内边距 `12px 16px`（修左右不对称的 `14px 8px`）；分隔线 1px `--border-line`；hover 底 `--c-neutral-50` |
| 未读 | 左侧 3px 主色竖条（`--c-blue-500`）+ 标题 14/600 `--c-neutral-800` + 右侧"未读"小标签（**新增文字标签**，不只靠圆点和字重，修颜色单一依赖） |
| 已读 | 无竖条，标题 14/400 `--c-neutral-600` |
| 类型标签 | `StatusTag variant="outline"`（现状 `el-tag effect="plain"`，统一到状态标签体系） |
| 时间 | 右对齐 Caption 12 `--c-neutral-500`；`>= 1 天` 显示相对时间（"2 天前"），当天显示 `HH:mm`（现状直出完整时间串，移动端已用相对时间 → 两端统一为相对时间） |
| 操作 | 「标记已读」「查看」为 link 型；hover 时才显性化（现状常驻，挤压标题）；**触屏/键盘下始终可见**（用 `@media (hover: hover)` 控制，避免触屏设备操作不可达） |
| 行点击 | 整行可点但必须**可键盘聚焦**：`role="button" tabindex="0"` + 回车触发 + `aria-label`（修 P21） |

**三态**：empty（"暂无通知"/"没有未读通知"）；error（`StateBlock error` + 重试，修 P14）；loading（5 行 68px 骨架）。

### 5.6 移动端通用容器（Tabbar 页）

```text
┌─────────────────────────┐
│ NavBar（44 + safe-top）  │ ← 标题 H2 16/600，居中
├─────────────────────────┤
│ 页面内容（padding 0 12）  │ ← 卡片间距 12，区块间距 20
│                         │
├─────────────────────────┤
│ Tabbar（50 + safe-bottom）│ ← 图标 22，文字 10/400（Vant 默认）
└─────────────────────────┘
```

| 项 | 规格 |
| ---- | ---- |
| Tabbar | 高 50px；选中 `#0958D9`（修 P38 的 `#1677ff`），未选中 `#6B7280`（4.83:1，修 P38 的 `#969799`）；角标（未读）底 `#CF1322` 白字 10px；图标 22px |
| 内容区 | `padding: 0 var(--sp-3) 8px`；末元素下方留 `--page-pad-bottom-tab` |
| 全宽元素 | `van-search` / `van-tabs` / `van-notice-bar` 使用**负边距贴边**（`margin: 0 calc(-1 * var(--sp-3))`）再补回自身内边距，使 Tab 下划线与搜索框底边真正通栏（现状固定在 12px 内，视觉收缩） |
| Tabs | 高 44px，标签 14/400（选中 14/600 + 主色），下划线 2px 主色、宽等于文字宽 |
| 搜索 | `van-search` 高 44，`--r-full`，底 `#FFFFFF`，placeholder `--c-neutral-400` |

### 5.7 移动端 · 老板端

#### B1 登录页（`views/login/index.vue`）

```text
┌─────────────────────────┐
│                         │  顶部留白 56 → 40
│  快递驿站智汇系统         │ ← H1 20/600（现状 22/600，归位）
│  移动端演示 · 纯 Mock 数据 │ ← Caption 12 --c-neutral-500
│ ┌─────────────────────┐ │
│ │ 一键体验：[老板][站长][员工]│ ← 胶囊，高 32（描边式，非必需触控目标）
│ │ 账号  [__________]   │ │ ← 控件高 44
│ │ 密码  [__________]   │ │
│ │      [    登录    ]  │ │ ← 高 44，实底 #0958D9
│ └─────────────────────┘ │
│ 演示密码统一为 demo1234   │ ← Caption 12
└─────────────────────────┘
```

- 背景：`linear-gradient(180deg, #E8F4FF 0%, #F5F7FA 42%)`（用 `--c-blue-50`，替换现 `#e8f2ff`）
- 一键体验按钮：高 32（修 P34 的 25px）；**主入口**仍是"登录"按钮，故 32 可接受（次要控件 ≥24px AA 达标）；若评审要求 AAA 则改 44
- 错误文案：`role="alert"`，色 `#CF1322`，位于表单与按钮之间（保留）
- 卡片：`--r-lg(12)` + `--e0` + 1px 描边（替换现状 `box-shadow: 0 6px 20px rgba(22,119,255,.08)` 的彩色阴影）

#### B2 经营总览（`views/boss/home.vue`）

```text
经营总览
╔═════════════════════════╗  ← --grad-hero-deep (#1F2937→#111827) + 顶部 2px #1890FF
║ 今日经营     [全域 ▾]    ║     高 88，圆角 12，白字
║ 09-17 周三 · 数据截止 10:24║  ← Caption rgba(255,255,255,.72)（对 #1F2937 约 8.2:1）
╚═════════════════════════╝
┌───────────┬───────────┐
│ 今日入库   │ 今日取件   │ ← StatCard Num-lg 24
│ 1,286 件  │ 1,102 件  │
│ ↑12.4%    │ ↑3.1%     │ ← Caption，涨绿跌红
├───────────┼───────────┤
│ 待取件     │ 异常件     │
│ 3,410 件  │ 37 件     │ ← 异常件数值 #CF1322
└───────────┴───────────┘
⚠ 3 条工单超 SLA · 2 个批次同步失败            ›  ← NoticeBar（#FFF1F0/#CF1322）
近 7 天包裹趋势                        查看详情 ›
┌─────────────────────────────────────┐
│ ▁▂▅▇▅▃▁  96px（末点标注 1,102）        │ ← compact：末值 + 最高/最低档刻度
│ 区间取件率 85.6%         日 均 184 件  │ ← Caption
└─────────────────────────────────────┘
同步健康度                    近 42 个批次
│ 失败批次 2 个（#CF1322） · 最近批次 10:18
驿站 TOP3                              全部 ›
│ ① 城东驿站 12,480 ▓▓▓▓▓▓▓▓
组织规模（一期）                           ›  ← 折叠
```

| 项 | 修法 |
| ---- | ---- |
| Hero | 新增（现状无 Hero，首屏直接从 4 卡开始）→ 建立"经营报告"心智；口径 chip 用 24px 高描边胶囊（白字 + `rgba(255,255,255,.24)` 底） |
| 环比 | **新增**（数据契约无同比字段，故用"近 7 天日均 vs 今日"本地推导，无数据时整行隐藏）；实现标注 `TODO(扩展): 待后端出环比字段后改为直接取值` |
| 趋势卡 | `compact` 模式补末值标注与最高/最低档刻度（修 P36）；点击整卡进入 B3 |
| 组织规模 | 移至末位并默认折叠（修"一期指标占据老板视线"） |
| 区块顺序 | Hero → 指标 → 异常条 → 趋势 → 同步健康度 → 排行 TOP3（新增）→ 组织规模 |
| 驿站 TOP3 | **新增**（老板首页需要"哪里好/哪里差"的一个抓手；数据来自既有 `getParcelRanking`，无需新接口） |
| 三态 | loading 用骨架（Hero 88 块 + 4 卡 + 96 图，修现状"整页 `PageState` 骨架行"导致 Hero 缺失）；error 保留 `PageState` 错误态；empty（无数据）在趋势卡内空态 |

#### B3 包裹趋势（`views/boss/trend.vue`）

```text
包裹趋势
┌ 全宽 Tab：[近 7 天] [近 30 天]（高 44，通栏）
2026-09-11 ~ 2026-09-17                      7 天
┌──────────────────────────────────────┐
│ ┌──────────────────────────────────┐ │ ← 200px 折线，双系列 + 图例开关（≥44 触控）
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
┌───────────┬───────────┐
│ 区间入库   │ 区间取件   │ ← 改用 StatCard（修 P35：删除自定义 .metric）
├───────────┼───────────┤
│ 区间取件率 │ 日均入库   │
└───────────┴───────────┘
口径：入库按入库时间分天聚合；取件含历史派生与实时核销   ← Caption 12（现状 11）
```

- 驿站筛选：数据契约未开放（`getParcelTrend` 只收 `days`）→ **不实现**，仅在文档保留 `TODO(扩展)`；**不画**禁用下拉（避免假控件）
- 下拉刷新保留；图例开关状态提升到页面级（现状在组件内，从 B2 进入时重置，属可接受，但同页切 7/30 天会重置 → 改为 prop 由页面持有，保证切范围不丢)

#### B4 驿站排行（`views/boss/rank.vue`）

| 项 | 修法 |
| ---- | ---- |
| 排序 chip | 高 30 → **44px**（修 P34）；选中态改"`--c-blue-50` 底 + `--c-blue-500` 1px 描边 + `#0958D9` 文字"（避免白字压 500 档主色的对比度问题，且比实底更精致） |
| 排名徽标 | 保留金/银/铜，取 `#B45309 / #6B7280 / #92400E`（白字 ≥5:1）；4+ 名用 `#F5F7FA` 底 `#4B5563` 字 |
| 进度条 | `stroke-width 6` → 8px；色按口径（包裹量 `#1890FF`、取件率 `#389E0D`、异常率 `#FF4D4F` = `--color-danger-icon`），与 PC 完全一致（修跨端不一致） |
| 列表项 | 内边距 `12px 16px`，间距 12；副信息 11 → **12px** |
| 弹层 | `round` = 顶部圆角 12（用 `--r-lg`）；标题 16/600；按钮高 44 |
| 三态 | 骨架 6 行；空态"暂无驿站数据"；错误态重试 |

#### B5 异常预警（`views/boss/alerts.vue`）

| 项 | 修法 |
| ---- | ---- |
| 分组头 | 高 48（现状 14px 内边距 ≈ 48，达标）；图标箭头改旋转动画 `--dur-base`；分组计数用 `StatusTag`（`danger` 当 >0，否则 `outline` 中性）——修 P30（0 个失败批次不用绿色） |
| 分组默认展开 | 仅"超 48h 未取件"默认展开（保留），其余折叠 |
| 列表项 | 底 `--c-neutral-50`（内嵌分组）→ 改 `#FFFFFF` + 1px 描边，避免"卡中卡"的双层底色；元信息 12px；危险文案色 `#CF1322` |
| 预警条 | 每组头右侧显示"最严重程度"色点（红/橙/灰），使折叠状态也能扫读严重度（**新增**） |
| 弹层（同步失败明细） | 标题 16/600；列表 `max-height: 56vh` 保留；错误原因用 `#CF1322` 12px |
| 三态 | 骨架 8 行；空态；错误态重试 |

#### B6 我的（`boss/me.vue` → `MeSection`）

见 C-M7 与 5.11（两端共用 MeSection，差异仅角色文案）。

### 5.8 移动端 · 员工端

#### S1 工作台（`views/staff/home.vue`）

```text
工作台
╔═════════════════════════╗  ← --grad-hero (#0958D9→#0745A8) + 顶部 2px #1890FF
║ 城东驿站        [站长]   ║
║ 王强，今日待处理 12 条    ║  ← Caption rgba(255,255,255,.82)（对 #0958D9 约 5.4:1）
╚═════════════════════════╝
⚠ 本站 3 条工单已超 SLA，请尽快处理            ›
┌───────────┬───────────┐
│ 今日入库   │ 待取件     │ ← Num-lg 22；待取件为主强调
│ 1,286 件  │ 3,410 件  │
├───────────┼───────────┤
│ 今日取件   │ 异常件     │
│ 1,102 件  │ 37 件     │
└───────────┴───────────┘
常用操作
┌─────┬─────┬─────┬─────┐  ← 宫格：图标 24px，文字 12px，单元高 ≥88
│本站 │取件 │工单 │通知 │
│包裹 │核销 │     │ ③   │  ← 角标底 #CF1322
└─────┴─────┴─────┴─────┘
本站包裹 12,480 件 · 取件率 85.6%          ← Caption 12（现状 11）
```

| 项 | 修法 |
| ---- | ---- |
| Hero | 渐变改 Token（修 P3/P4 硬编码）；站名 18/600 白字；角色 chip 20px 高 `rgba(255,255,255,.22)` 底 |
| 指标 | 修字阶（22/600）+ 语义：入库蓝、待取橙、已取绿、异常红（现状"今日取件"用 success 但无下钻，"异常件"下钻到包裹页——保留） |
| 宫格 | 图标 22 → 24px；文字 12px；单元最小高 88px（触控达标，修 P34 相关）；同步状态入口仅站长（保留） |
| 提示条 | `#ee0a24/#fff1f0` → `#CF1322/#FFF1F0`（修 P4 硬编码 + 对比度 4.46→5.57） |
| 底行 | 11 → 12px，用 `--c-neutral-500` |
| 三态 | 骨架 6 行（现状 `PageState rows=6`）；错误态重试；下拉刷新保留 |

#### S2 本站包裹（`views/staff/parcel.vue`）

```text
本站包裹
┌───────────────────────────────┐ ← 通栏搜索（高 44）
│ 🔍 输入运单号精确查询    [搜索] │
└───────────────────────────────┘
[全部][待取件][已取件][异常]        ← Tab 高 44，通栏，下划线
当前筛选共 1,240 件 · 已加载 20    ← Caption 12（现状 11）
┌───────────────────────────────┐
│ SF1234567890        [在库待取] │ ← 运单号 15/500
│ 货架 A-12 · 张* 138****5678     │ ← Caption 12 --c-neutral-500
│ · 2 小时前入库                  │
│ ⚠ 入库已超 48 小时未取件         │ ← Caption 12 #CF1322
└───────────────────────────────┘
（列表项：内边距 12/16，间距 12，整项触控 ≥72）
没有更多了
```

| 项 | 修法 |
| ---- | ---- |
| 搜索/ Tab | 通栏（负边距），修"整体缩进 12px 显得局促" |
| Tab 语义 | 保留"全部/待取件/已取件/异常"（`status=0/4` 不在 Tab 内，可接受；但**新增**"待入库 12 件"信息到筛选汇总行，避免"待入库包裹无处可查"——列为 P1） |
| 列表项 | 元信息拆行（现状一行塞"货架+收件人+电话+入库时间"，横向溢出风险高）；超出 48h 警示保留（色改 `#CF1322`，字号 11→12） |
| 行点击 | `<div @click>` → 加 `role="button" tabindex="0"` + 键盘触发（修 P33） |
| 分页 | 保留无限滚动（20/页）；`finished-text="没有更多了"`；搜索命中时不分页（保留） |
| 三态 | 首屏骨架 5 行；空态（区分"无数据"与"搜索无结果"，后者带"清除搜索"）；错误态（列表已有数据时不打断，仅 Toast——保留现状逻辑） |

#### S3 包裹详情（`views/staff/parcelDetail.vue`）

```text
‹ 包裹详情
┌───────────────────────────────┐
│ SF1234567890          [在库待取]│ ← 16/600
│ 城东驿站 · 货架 A-12           │
└───────────────────────────────┘
包裹信息（van-cell-group inset，行高 48，垂直内边距 12）
 收件人 / 入库时间 / 入库时长 / 取件时间 / 取件员工 / 同步批次 / 备注
╔═══════════════════════════════╗
║ [      取件核销      ]        ║ ← ActionBar 固定底部（修 P31）
║ [      上报异常      ]（描边） │
╚═══════════════════════════════╝
```

- 现状"作业操作"内联在内容末尾 → 改 `ActionBar`（`dual`），页面 `padding-bottom: var(--page-pad-bottom)`
- 主按钮禁用态文案保留（"当前状态不支持取件（已取件）"）→ 改为按钮禁用 + 下方 Caption 说明（禁用按钮内的长文案在 44px 高内会换行截断）
- ADMIN 进入时隐藏 ActionBar，改为底部 Caption 说明（保留现有语义）
- 三态：骨架 6 行；错误态（"包裹不存在或无权查看" + 返回）；空态不适用

#### S4 取件核销（`views/staff/pickup.vue`）

```text
‹ 取件核销
┌───────────────────────────────┐
│ 运单号  [__________________]  │ ← 输入高 44，`autofocus`，回车即查
│ ┌───────────┬───────────────┐ │
│ │  查询包裹  │  扫码（描边）  │ │ ← 主按钮 flex:2 / 扫码 flex:1，高 44
│ └───────────┴───────────────┘ │
│ 🎁 填入演示运单号 SF1234567890 │ ← 高 44 热区（现状 33）
└───────────────────────────────┘
包裹信息
┌───────────────────────────────┐
│ SF1234567890          [在库待取]│
│ 城东驿站 · 货架 A-12 · 2 小时前 │
│ 收件人 张* 138****5678          │
│ 入库时间 2026-09-17 08:12       │
└───────────────────────────────┘
╔═══════════════════════════════╗
║ [        确认取件         ]   ║ ← ActionBar single
╚═══════════════════════════════╝
本次演示已核销（3 件）             ← 会话内反馈，成功图标 #237804
```

| 项 | 修法 |
| ---- | ---- |
| 输入焦点 | 进入页面自动聚焦输入框（`van-field` + `autofocus`），减少一次点击（**新增**，一线效率关键） |
| 结果反馈 | 查询到"非在库待取"状态时，除 Toast 外**在结果卡顶部显示警示条**（"该包裹已取件" `#FFFBE6`/`#B45309`），因为 Toast 3s 后消失，用户回看时会误解（修"仅靠 Toast"） |
| 确认按钮 | 移入 `ActionBar`，仅在查到包裹时出现（现状：状态不符时按钮文案变长且禁用，观感差） |
| 演示运单号 | 热区撑到 44px（修 P34） |
| 已核销列表 | 成功图标用 `#237804`（替换 `#07c160`，修 P4） |
| 三态 | 查询中按钮 loading（保留）；未查询=空态引导（"输入运单号或扫码开始" + 演示运单号提示）；查询无结果=页内提示条（非 Toast 独占） |

#### S5 工单列表（`views/staff/workorder.vue`）

```text
工单
[全部][待处理][处理中][已解决][已关闭]      ← Tab 高 44 通栏
共 42 条                        [⚠ 仅看超 SLA] ← chip 高 44（现状 23）
┌───────────────────────────────┐
│ WO-20260916-0042      [包裹异常]│
│ 包裹破损需补发                  │ ← 15/500
│ [高] ⏱ 已超时 2 小时  2 小时前   │ ← 高优先级实心红 + 超时实心红
│ 处理人：李四 · 上报人 王强       │ ← Caption 12
└───────────────────────────────┘
…
                                        [＋ 新建工单] ← FAB，高 48，bottom: calc(var(--tabbar-h) + var(--safe-bottom) + 12px)
```

| 项 | 修法 |
| ---- | ---- |
| 超 SLA chip | 高 44px（修 P34）；选中态 `#FFF1F0` 底 + `#CF1322` 描边 + `#CF1322` 字 |
| 列表项 | 标签组行：优先级（高=实心红 / 中=浅底橙 / 低=描边灰）+ SLA 胶囊 + 相对时间；**行高 ≥76px** |
| FAB | 修 P32 魔数 → `calc(var(--tabbar-h) + var(--safe-bottom) + var(--sp-3))`；高度 44→48，`--e2`，文字 14/600 |
| 高亮新工单 | 保留 `is-highlight` 动画（2.4s → 1.6s，`--dur-slow`×n），并加 2px 主色描边（颜色 + 动效双通道） |
| 行点击 | 键盘可达（修 P33） |
| 三态 | 骨架 5 行；空态（区分"暂无工单"与"没有超 SLA 工单"）；错误态（有数据时仅 Toast，保留） |

#### S6 新建工单（`views/staff/workorderCreate.vue`）

| 项 | 修法 |
| ---- | ---- |
| 类型/优先级选择 | 现状 4 个 `van-radio` 横向排列（375pt 下"包裹异常/设备故障/客户投诉/其他"会挤成两行且极窄）→ 改 **2×2 卡片式单选**（每格高 48，`--r-sm`，选中=主色描边 + 浅蓝底 + 右上角勾），优先级 3 项一行（每格高 48） |
| 表单字段 | 标题必填（`maxlength 100`）；描述 textarea 4 行；运单号选填；错误提示改为**字段级**（现状仅一个全局 `errorMsg`，用户不知哪个字段错了） |
| 上传占位 | 虚线框保留，底色 `--c-neutral-50`，图标 + 文案 12px；点击 Toast 说明（保留） |
| 提交 | 固定底部 `ActionBar single`（"提交工单"）；防重复提交（保留 `submitting`）；提交成功 `router.replace` 回列表 + 高亮（保留） |
| 一致性提示 | 顶部显示"上报人 / 归属驿站"只读卡（保留）+ SLA 口径 Caption |

#### S7 工单详情（`views/staff/workorderDetail.vue`）

```text
‹ 工单详情
┌───────────────────────────────┐
│ WO-20260916-0042      [待处理] │
│ 包裹破损需补发                  │ ← 15/500
│ [包裹异常] [高] ⏱ 已超时 2 小时  │
└───────────────────────────────┘
工单信息（cell-group inset）
问题描述（card，14/1.7）
处理时间线（card，时间 Caption，动作 14/600 + 操作人 Caption）
可执行操作
╔═══════════════════════════════╗
║ [      接单处理      ]        ║ ← ActionBar multi（主操作+更多）
║ [      关闭工单      ]（描边红）│
╚═══════════════════════════════╝
```

| 项 | 修法 |
| ---- | ---- |
| 操作栏 | 内联 → `ActionBar`（修 P31，本页优先级最高）；≥3 操作时 `multi`（主操作 + "更多"弹层），现状 2 个操作可直接并列 |
| 时间线 | 现状用自绘 `timeline`：改为**最新在上**（保留现状 `.reverse()`）+ 每条前加动作色点（`--c-blue-500` 创建/指派、`#237804` 解决、`#CF1322` 关闭）+ 时间 Caption 12（现状 11） |
| 备注弹层 | 保留 `van-popup round position=bottom` + textarea 200 字 + 必填校验；确认按钮高 44 |
| 无权限提示 | 保留"当前身份不是处理人，仅可查看"+ 补充权限规则说明（Caption） |
| 三态 | 骨架 6 行；错误态重试 |

#### S8 通知（`views/staff/notification.vue`）

| 项 | 修法 |
| ---- | ---- |
| Tab | 高 44 通栏；未读 Tab 带计数 |
| 工具行 | "全部已读"改 `<button>` 高 44 热区（现状 `<span>` 17px，修 P33/P34）；计数 11→12px |
| 列表项 | 未读：左侧 3px 主色竖条 + 标题 15/600 + 12px 圆点；已读：无竖条 + 15/400 `--c-neutral-600`；类型标签用 `StatusTag variant=outline`（现状 `van-tag type=primary`，与状态体系不统一） |
| 时间 | 相对时间（保留）+ 右对齐 Caption |
| 行点击 | 键盘可达（修 P33）；跳转失败（如 STAFF 点同步通知）的 Toast 文案保留 |
| 分页 | 现状一次拉 50 条无分页 → 改 `van-list` 无限滚动（20/页），避免 50 条同时渲染（**新增**，性能与一致性） |

#### S9 同步状态（`views/staff/sync.vue`）

| 项 | 修法 |
| ---- | ---- |
| 最近批次卡 | 批次号 16/600（保留）；状态用 `StatusTag`；元信息改 `Caption 12`；错误原因 `#CF1322` 12px + 图标 |
| 历史批次 | 列表项间距 12（现状 10）；批次号 15/500；元信息 12px；失败行左侧 3px `#FF4D4F` 竖条 |
| 日志弹层 | 标题 16/600；日志行：级别 `StatusTag` + 消息 12px `--c-neutral-600` + 时间 11px `--c-neutral-500`；`max-height 56vh`（保留） |
| 只读提示 | 保留"重试在 PC 端操作"Caption |

#### S10 我的 / S11 修改密码（`MeSection` / `password.vue`）

| 项 | 修法 |
| ---- | ---- |
| 个人信息卡 | 渐变改 `--grad-hero`（修 P3 硬编码）；姓名 18/600；副信息 `rgba(255,255,255,.82)`（对 `#0958D9` ≈5.4:1，修现状 3.28:1 不达标） |
| 区块标题 | 字阶归位 H3 14/600；`section-title__extra` 用 `--c-neutral-500` 12px |
| 细胞行 | 行高 ≥48（`--van-cell-vertical-padding:12`）；`van-cell-group inset` 左右外边距 0（现状 Vant 默认 16px inset 与页面 12px 内边距叠加成 28px 缩进，视觉过窄）→ 统一为"无 inset 内缩、与页面同宽" |
| 退出按钮 | 高危操作：`type="danger" plain` 高 44，二次确认（保留） |
| 改密页 | 三个密码字段 + 字段级校验提示（现状全局 `errorMsg`）→ 改为字段 `:rules` + 全局兜底；规则文案保留（8-20 位含字母数字） |
| 运行环境块 | 保留（Demo 诊断用）但降级为 Caption 12 + `--c-neutral-500` |

### 5.9 入口页（`index.html`）

```text
           快递驿站智汇系统 · 三端演示 Demo            ← H1 20/600 居中
      三端演示 Demo · 纯前端 + Mock 假数据，不依赖后端   ← Caption 12 --c-neutral-500
┌─ 提示条（#FFF7E6 / #B45309 / 1px #FFE7BA / --r-sm）────┐
│ · 数据来源：全量 Mock…                                  │
└──────────────────────────────────────────────────────┘
┌────────────┬────────────┬────────────┐
│▔▔ 蓝       │▔▔ 深蓝灰    │▔▔ 蓝        │ ← 4px 顶部色条区分端
│PC/Desktop  │Mobile       │Mobile       │ ← 12px 主色 500 档
│网页端       │老板端        │员工端        │ ← 18/600
│角色说明…    │说明…        │说明…         │ ← 12px --c-neutral-500
│→ 进入网页端 │→ 进入移动端  │→ 进入移动端   │ ← 12px #0958D9
└────────────┴────────────┴────────────┘
演示剧本数据自检
┌─ 面板（描边 1px，--r-md）──────────────────────────────┐
│ ✅ 超 48h 未取件包裹   12 件（#237804）                  │
│ ❌ 失败同步批次         0 个（#CF1322）                  │
└──────────────────────────────────────────────────────┘
[重置演示数据] [重新自检]  提示文案…（Caption 12）
演示顺序（约 8-10 分钟）  ← H2 16/600
```

| 项 | 修法 |
| ---- | ---- |
| 色值 | `#1677ff` → 主色 `#1890FF`（描边/链接）与 `#0958D9`（按钮实底）；`#1f2329` → `#1F2937`；`#8c8c8c/#595959` → `#6B7280/#4B5563`；`#bfbfbf` → `#CBD2DA`；`#e8e8e8` → `#E3E7ED`；`#f0f2f5` → `#F5F7FA`；`#fffbe6/#ffe58f` → `#FFF7E6/#FFE7BA`；`#52c41a/#ff4d4f` → `#237804/#CF1322` |
| 圆角/间距 | 卡片 10 → `--r-md(8)`；面板 10 → 8；间距 20/26 → 16/24 |
| 焦点态 | 卡片 `<a>` 与按钮补 `:focus-visible` 2px 主色环（现状无） |
| 触控 | 按钮高 ≥36（桌面）；卡片整块可点（≥44 高） |
| 移动端（<768px） | 三卡单列（已有 `@media`），卡片间距 12；`portal__environment` 字号 12 不缩放 |
| 语义 | `check__ok/bad` 增加文字前缀（"达标/未达标"），不只靠颜色（修色单一依赖） |

---

## 6. 图表设计规范

### 6.1 统一参数表（PC `TrendChart` 与移动 `LineChart` 共用同一份视觉参数）

| 参数 | PC | 移动（full） | 移动（compact） |
| ---- | ---- | ---- | ---- |
| 渲染宽 | 容器实测宽（ResizeObserver） | 容器实测宽 | 容器实测宽 |
| 渲染高 | 220px | 200px | 96px |
| 内边距 | top 12 / right 16 / bottom 28 / left 44 | top 12 / right 12 / bottom 24 / left 32 | top 8 / right 8 / bottom 8 / left 8 |
| 线宽 | 2px（`vector-effect="non-scaling-stroke"`） | 同 | 同 |
| 拐点 | `stroke-linejoin/linecap: round` | 同 | 同 |
| 曲线形态 | **不做平滑**（直线连接） | 同 | 同 |
| 数据点 | 数据 ≤14 时逐点 `r=2.5`（白填充 2px 色描边）；>14 时仅末点 `r=3` + 外环 `r=5` 10% 透明 | 同 | 仅末点 `r=2.5` |
| 末值标注 | 末点旁 12px 数值（+ 单位），靠右时自动左移避让 | 同 | **必显示**（修 P36） |
| 横向网格 | 4 档（0、max、max/2 及其间两档），1px `--border-line` 实线 | 3 档 | 0（隐藏） |
| 0 轴基准线 | 1px `--c-neutral-300`（加粗区分，修 P41） | 同 | 无 |
| Y 轴刻度值 | nice-number 算法（`1/2/2.5/5 × 10ⁿ`，保证整数且不重复，修 P11）；12px `--c-neutral-500` | 11px `--c-neutral-500` | 仅"最高档/最低档"两个 11px 标注 |
| X 轴标签 | 最多 7 个，首尾必留，12px `--c-neutral-500` | 最多 6 个，11px | 无 |
| 系列色 | 入库 `--c-blue-500 #1890FF`；取件 `--c-green-600 #389E0D` | **同**（修 P39） | 同 |
| 面积填充 | 入库 `#1890FF` @8%（仅入库） | 同 | 同 |
| 图例 | 右侧，胶囊按钮，dot 8px + 12px 文字；关闭态文字 `--c-neutral-500` + 圆点 30% 透明；`aria-pressed` | 同，**触控热区 ≥44px** | 无图例 |
| 交互 | hover 显示十字线（1px 虚线 `--c-neutral-300`）+ 该点 `r=4` + 浮层（日期 + 两系列数值） | 触摸滑动同 hover | 无 |
| 浮层 | 白底 + 1px `--border-line` + `--e2` + `--r-sm`；标题 12/600；数值 12/400（色点 + 系列名 + 数值） | 同 | 无 |
| 空态 | 图表高度固定 + 居中（图标 32px `--c-neutral-300` + 12px `--c-neutral-500` 文案 + 可选引导按钮）；**不用** `el-empty` 大图 | 同 | 同 |
| 加载态 | 同高度灰块 + 3 条水平脉冲（`--c-neutral-100`） | 同 | 同 |
| 错误态 | 同高度 + 12px `#CF1322` 文案 + "重试" link 按钮 | 同 | 同 |

**为什么图表必须自己算宽度（关键实现决策）**：现状用固定 `viewBox` + `width:100%;height:auto` 等比缩放，导致文字随容器放大（PC 容器 1200px 时 11px 轴文字被渲染成约 18px，修 P10）。改为「按容器实测宽绘制 + `vector-effect="non-scaling-stroke"`」，文字保持 12px、线宽保持 2px，与全局字阶一致。实现成本：一个 `ResizeObserver` + 在 `computed` 中把 `W` 从常量改为 `ref`（约 10 行改动），无新依赖。

### 6.2 双系列语义与命名

| 系列 | 色 | 语义 | 备注 |
| ---- | ---- | ---- | ---- |
| 入库（inbound） | `#1890FF` | 规模/输入 | 带 8% 面积 |
| 取件（pickup） | `#389E0D` | 履约/输出 | 只画线（避免两条面积互相遮挡） |
| 异常（预留） | `#FA8C16` | 质量 | 本轮不画，Token 已预留 |

### 6.3 驿站排行（进度条 / 榜）

| 参数 | 规格 |
| ---- | ---- |
| 形态 | PC 行式（行高 56）/ 移动卡式（行高 ≥64） |
| 进度条高 | 8px（PC/移动一致），`--r-full`，底 `--c-neutral-100` |
| 进度条色 | 按口径：包裹量 `#1890FF`、取件率 `#389E0D`、异常率 `#FF4D4F`（= `--color-danger-icon`，三端一致，修跨端不一致）；**仅进度条**取 500 档，异常率的数值/文字仍走 `--color-danger`(`#CF1322`) |
| 归一化 | 分母 = 当前列表最大值（现状两端均如此，保留）；`aria-label="占最高值的 X%"` |
| 数值 | `Num-sm` 15/600 `tabular-nums`，右对齐；单位 12/400 |
| 排名徽标 | 24×24，`--r-xs`；1/2/3 名实底白字 `#B45309/#6B7280/#92400E`；4+ 名 `#F5F7FA` + `#4B5563` |
| 副信息 | Caption 12 `--c-neutral-500`：`包裹 12,480 · 取件率 85.6% · 异常率 0.4%`（**顺序三端固定**） |
| 口径说明 | 列表下方 Caption 一行，说明当前排序口径与"进度条为相对值" |

### 6.4 指标卡片（数值型）规范

| 项 | 规格 |
| ---- | ---- |
| 结构 | 标签（12/400 `--c-neutral-500`）→ 数值（`Num-lg/Num-md` + 12/400 单位）→ 环比/副信息（12/400） |
| 对齐 | 标签与数值左对齐（移动）/ 左对齐（PC，图标在左）；数值右对齐仅用于表格内 |
| 数字格式 | 千分位（`toLocaleString('zh-CN')`，保留）；百分比 1 位小数；`—`（非 `-`）表示缺失；`tabular-nums` 必加 |
| 单位 | 与数值同行，12/400 `--c-neutral-500`，`margin-left: 2px` |
| 语义色 | 数值色取 600/700 档（**不得**用 500 档，修 2.3 规则）；异常/超时类数值才用红色 |
| 涨跌 | 涨 = `#237804` + `↑`；跌 = `#CF1322` + `↓`；无变化 = `--c-neutral-500` + `—`；**箭头 + 数字**双通道 |
| 可点性 | 可下钻时整卡为按钮，`hover/active/focus` 三态齐备 + 右上角 12px 箭头图标提示可点 |

### 6.5 图表无障碍

1. 每个图 `role="img"` + `aria-label`（必须，修移动端缺失），格式：`"近 7 天包裹入库与取件趋势；入库合计 8,960 件，取件合计 7,672 件"`；
2. 图下方提供**数据表替代**（`<table>` + `visually-hidden` 类，仅读屏可读），两行三列：日期 / 入库 / 取件（P2，但建议做，成本低）；
3. 图例按钮 `aria-pressed`；
4. 不允许"仅颜色"区分系列 → 浮层与数据表均带系列名文字。

### 6.6 性能约束

| 项 | 规格 |
| ---- | ---- |
| 点数上限 | 趋势图最多 31 点（30 天）；若未来出 90 天视图，必须做采样聚合（按周），禁止全量绘制（无法交互且看不清） |
| 重绘 | `ResizeObserver` 需 `debounce 100ms`，避免拖拽窗口时高频重算路径 |
| 动画 | 折线首次进入做 `stroke-dashoffset` 描线动画 400ms（可选，P2）；`prefers-reduced-motion` 下禁用 |

### 6.7 为什么继续手写 SVG（不引 ECharts）

| 维度 | 手写 SVG（选它） | 引入 ECharts |
| ---- | ---- | ---- |
| 依赖体积 | 0 | ECharts 5 完整包 ≈ 1MB（min），按需仍需 `echarts/core` + line + tooltip + legend ≈ 300–400KB |
| 与项目约束冲突 | 无 | 违反"不引入新依赖"（demo-design.md 10.4 依赖策略 + 本轮硬约束） |
| 需求覆盖 | 1 条双系列折线 + 1 组进度条，参数已全部定义（6.1–6.4） | 覆盖远超需求，剩余能力成为维护负担 |
| 主题一致性 | 直接用 Token，与 CSS 变量同源 | 需另行维护 `theme.json`，与 Token 二次同步，易漂移 |
| 三端一致 | PC/移动共用同一套参数表 | 需两端两套配置 |
| 交互成本 | hover/十字线/图例各约 30 行 | 内置，但默认样式与 Token 冲突需大量覆写 |
| 可维护性 | 参数集中在 1 个组件，改动可预期 | 升级 ECharts 大版本需回归全部图表 |

> **结论**：维持手写 SVG。**触发重新评估的条件**：出现第 3 种图表类型（如堆叠柱、桑基图）或需要"缩放/框选/导出图片"等交互时再评审。

---

## 7. 移动端适配规范

### 7.1 安全区与视口

| 项 | 规格 |
| ---- | ---- |
| 视口 | `viewport-fit=cover`（已在 [mobile.html:8](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/mobile.html)，保留） |
| 顶部 | `#app { padding-top: var(--safe-top) }`；固定 NavBar `top: var(--safe-top)`；壳通过 `HrmShell.setStatusBarHeight` 注入，浏览器为 `0px`（保留现机制，`--status-bar-height` 改名为 `--safe-top` 并保留兼容别名） |
| 底部 | Tabbar / ActionBar 内 `padding-bottom: var(--safe-bottom)`；页面 `padding-bottom` 使用 `--page-pad-bottom(-tab)` |
| 弹层 | `van-popup position="bottom"` 必须带 `safe-area-inset-bottom`（现状已带，保留） |
| 横屏 | 本轮不做横屏专用布局（驿站现场以竖屏为主）；约束：横屏下用 1 列布局 + 内容 `max-width: 640px` 居中，避免拉伸变形（P2） |

### 7.2 底部 Tabbar

| 项 | 规格 |
| ---- | ---- |
| 高度 | 50px（`--tabbar-h`）+ `--safe-bottom` |
| 图标/文字 | 图标 22px；文字 10/400（Vant 默认，不放大以保 5 项不挤） |
| 色 | 选中 `#0958D9`；未选中 `#6B7280`；底色 `#FFFFFF`；上沿 1px `--border-line` |
| 角标 | 底 `#CF1322` 白字 10px，`--r-full`，右上偏移 2px；未读为 0 时不渲染（保留逻辑） |
| 可见项数 | 固定 5 项（老板端 5 / 员工端 5），不增减 |
| 触控 | 每项宽 = 屏宽/5（375pt 下 75px），高 50px → 达标 |

### 7.3 列表项密度

| 类型 | 结构 | 高 | 内边距 | 字号 |
| ---- | ---- | ---- | ---- | ---- |
| 单行（cell） | 标签 + 值 | ≥48 | 12 / 16 | 14 / 14 |
| 双行（列表项） | 标题行 + 元信息行 | ≥64 | 12 / 16 | 15 / 12 |
| 三行（列表项 + 警示） | 标题 + 元信息 + 警示 | ≥76 | 12 / 16 | 15 / 12 / 12 |
| 指标卡（2 列） | 标签 + 数值 + 环比 | ≥88 | 16 | 12 / 24 / 12 |
| 分组头（可折叠） | 标题 + 计数 + 箭头 | ≥48 | 14 / 16 | 14 / 12 |
| 宫格项（4 列） | 图标 + 文字 | ≥88 | 12 | 24 / 12 |
| 空态/错误态 | 图标 + 文案 + 按钮 | ≥160 | 40 上下 | 14 / 12 / 14 |

**间距**：同类列表项之间 12px；不同区块之间 20px；卡片内元素 8px。

### 7.4 触控目标

| 规则 | 值 | 依据 |
| ---- | ---- | ---- |
| 主要交互（按钮、Tab、chip、列表项、宫格、FAB） | **≥44×44** | WCAG 2.5.5 (AAA) — 驿站现场单手持机/戴手套场景按最高标准 |
| 次要控件（文本链接、图例、关闭图标、一键体验） | ≥24×24（推荐 32） | WCAG 2.5.8 (AA) |
| PC 控件 | ≥24×24；主要按钮 32 | WCAG 2.5.8 (AA)；鼠标精度下 32px 已舒适 |

**现有不达标项与目标值（实现后必须复核）**

| 位置 | 现状（推算） | 目标 |
| ---- | ---- | ---- |
| [login/index.vue:133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/login/index.vue) 一键体验 | ≈25px | 32px（次要） |
| [rank.vue:133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/boss/rank.vue) 排序 chip | ≈30px | 44px |
| [workorder.vue:171](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) 超 SLA chip | ≈23px | 44px |
| [notification.vue:138](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/notification.vue) 全部已读 | ≈17px | 44px |
| [pickup.vue:162](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/pickup.vue) 演示运单号 | ≈33px | 44px |
| [LineChart.vue:157](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/components/LineChart.vue) 图例 | ≈17px | 44px |
| [workorder.vue:204](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/workorder.vue) FAB | ≈40px（10px 内边距） | 48px |

### 7.5 无横向滚动约束

| 措施 | 说明 |
| ---- | ---- |
| 全局 | `html, body, #app { overflow-x: hidden }`（已有，保留） |
| 长运单号/单号 | 列表项标题 `min-width: 0` + `overflow: hidden; text-overflow: ellipsis; white-space: nowrap`；详情页允许换行（`word-break: break-all`） |
| 元信息行 | 禁止把 4 个字段塞一行（现状 [parcel.vue:141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/views/staff/parcel.vue) 一行含"货架+收件人+电话+时间"）→ 拆为 2 行 |
| 表格类内容 | 移动端**不使用表格**，一律卡片列表；若必须对比多列（如驿站三项指标），用"标签-值"对或换行堆叠 |
| 弹层 | 宽度 `100%`，内容 `padding: 0 16px`，长内容内部 `overflow-y: auto` 且 `max-height: 56vh` |
| 负边距通栏 | 仅用于 `search/tabs/notice-bar`，且必须 `box-sizing: border-box`（全局已设） |
| 验证 | 走查时用 320px 宽视口验证（最小支持宽度），确认 `document.documentElement.scrollWidth <= clientWidth` |

---

## 8. 动效规范

| 场景 | 时长/缓动 | 说明 |
| ---- | ---- | ---- |
| 按钮/卡片按下 | 120ms `--ease-std` | `scale(.985)` 或底色变化 |
| 卡片 hover（PC） | 120ms | `border-color` + `--e2` + `translateY(-1px)`；禁止缩放卡片（会引起表格行错位） |
| Tab 指示器切换 | 200ms `--ease-std` | 下划线位置/宽度过渡；Tab 内容不做横向滑动动画（容易与滚动冲突） |
| 分组展开/折叠 | 200ms `--ease-std` | 高度过渡 + 箭头旋转 180° |
| 抽屉/弹层 | 300ms `--ease-in`（进入）/ `--ease-out`（退出） | 保留 Element/Vant 默认时长则不复写 |
| 骨架 → 内容 | 200ms 淡出 | 骨架透明度 1→0，内容 0→1（消除跳变，修 P37） |
| 列表进入（首屏） | **不做**逐项错峰动画 | 20 万数据场景下错峰动画会拖慢可交互时间；仅"新建工单返回高亮"保留 |
| 高亮提示（新建工单返回） | 1600ms | 底色 `#E8F4FF` → `#FFFFFF` + 2px 主色描边（颜色 + 动效双通道） |
| 数值变化 | 不做数字滚动动画 | 数据准确优先；仅颜色变化（涨绿跌红）120ms |
| 执行中状态 | 1.2s 循环脉冲（图标透明度 .6↔1） | 仅同步任务"执行中" |
| 降级 | `prefers-reduced-motion: reduce` → 全部动效 ≤0.01ms（见 2.10） | WCAG 2.3.3 |

**禁止**：自动播放动画、视差滚动、超过 400ms 的装饰动画、列表逐项入场延迟、加载转圈（用骨架）。

---

## 9. 设计验证报告

### 9.1 对比度实测（手算，公式：WCAG 2.x 相对亮度；误差 ±0.05）

| 前景 | 背景 | 对比度 | 场景 | 要求 | 判定 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| `#1F2937` | `#FFFFFF` | 14.66 | 一级文本 | 4.5 | 通过 |
| `#4B5563` | `#FFFFFF` | 7.56 | 二级文本/标签字 | 4.5 | 通过 |
| `#6B7280` | `#FFFFFF` | 4.83 | 三级文本（替代 `#969799` 2.93 / `#909399` 3.08） | 4.5 | 通过 |
| `#0958D9` | `#FFFFFF` | 6.16 | 链接/可点文字 | 4.5 | 通过 |
| `#FFFFFF` | `#0958D9` | 6.16 | 主按钮/NavBar/Tabbar 选中 | 4.5 | 通过 |
| `#FFFFFF` | `#0745A8` | 8.66 | 主按钮 hover、Hero 深端 | 4.5 | 通过 |
| `#FFFFFF` | `#1F2937` | 14.66 | 侧栏菜单激活、老板端 Hero | 4.5 | 通过 |
| `rgba(255,255,255,.82)` 合成 `#D7E2F4` | `#0958D9` | ≈5.40 | Hero 副信息 | 4.5 | 通过 |
| `#237804` | `#F6FFED` | 5.15 | 成功标签 | 4.5 | 通过 |
| `#B45309` | `#FFFBE6` | 4.71 | 警告标签 | 4.5 | 通过 |
| `#CF1322` | `#FFF1F0` | 4.93 | 危险标签 | 4.5 | 通过 |
| `#0958D9` | `#E8F4FF` | 5.27 | 品牌标签 | 4.5 | 通过 |
| `#4B5563` | `#EDF0F4` | 6.50 | 中性标签 | 4.5 | 通过 |
| `#FFFFFF` | `#CF1322` | 5.57 | 实心危险标签（超时/高优先级） | 4.5 | 通过 |
| `#FFFFFF` | `#B45309` | 5.02 | 实心警告标签 | 4.5 | 通过 |
| `#FFFFFF` | `#237804` | 5.59 | 实心成功标签 | 4.5 | 通过 |
| `#1890FF` | `#FFFFFF` | 3.24 | 图表线/图标/大号数字 | 3.0（SC 1.4.11 图形） | 通过 |
| `#389E0D` | `#FFFFFF` | 3.46 | 取件折线（替代 PC 2.24 / 移动 2.38） | 3.0 | 通过 |
| `#FF4D4F` | `#FFFFFF` | 3.27 | 危险图标/强调描边 | 3.0 | 通过 |
| `#8A93A0` | `#FFFFFF` | 3.10 | 表单控件边界 | 3.0（SC 1.4.11） | 通过 |
| `#E3E7ED` | `#FFFFFF` | 1.24 | 分隔线（装饰性） | 豁免 | 通过（豁免） |
| `#9AA4B2` | `#FFFFFF` | 2.52 | 占位符 | 4.5 | **不通过** → 见 9.3 D-1 |
| `#CBD2DA` | `#FFFFFF` | 1.71 | 禁用文字 | 豁免（SC 1.4.3 禁用态） | 通过（豁免） |

### 9.2 验收核对表（实现后逐项打勾）

- [ ] **色彩**：全端 `grep -rn -E "#[0-9a-fA-F]{3,6}" src/pc src/mobile` 仅命中 `tokens.scss`（允许 `#fff` 的极少量情形，需在 CR 中说明）；三端主色/语义色完全一致
- [ ] **字阶**：PC 单页数值字号 ≤2 种；移动端正文 ≥12px，11px 仅出现在图表轴与徽标
- [ ] **间距**：所有 padding/margin/gap ∈ {4,8,12,16,20,24,32,40}；无 10/14/18/22/26
- [ ] **状态**：每个数据页三态齐备且**错误态与空态文案不同**（重点复核包裹/同步/工单/通知 4 页）
- [ ] **交互态**：可点元素具备 hover/active/focus-visible/disabled/loading 五态
- [ ] **对比度**：9.1 表中"通过"项用 DevTools 复核；对比度不达标处 0 例
- [ ] **触控**：7.4 表 7 处修复到位；用 DevTools 元素审查确认实际盒模型 ≥ 目标值
- [ ] **键盘**：PC 侧栏折叠/用户菜单/通知行/Tab 可 Tab 到达并回车触发；`focus-visible` 环可见
- [ ] **响应式**：PC 1200/1440/1600 三档无横向滚动、看板栅格 4/4/4→3/2 正确降列；移动 320/375/414 三档无横向滚动
- [ ] **安全区**：浏览器（`--safe-top=0`）不塌陷；模拟壳注入 44px 后 NavBar 与内容同步下移，无覆盖
- [ ] **动效**：开关 `prefers-reduced-motion: reduce` 后无位移/缩放动画残留
- [ ] **图表**：轴文字不随容器放大（1420px 视口下仍为 12px）；Y 轴刻度无重复值；折线取件系列为 `#389E0D`；移动 compact 显示末值
- [ ] **三端一致**：同一包裹/工单状态在 PC 与移动的配色、文案、形态完全一致

### 9.3 已知偏差（Known Deviations，需评审确认）

| # | 偏差 | 理由 | 处置 |
| ---- | ---- | ---- | ---- |
| D-1 | 占位符色 `#9AA4B2`（2.52:1）不达 SC 1.4.3 | 表单标签始终可见，占位符仅为格式示例（冗余信息，非唯一信息源）；若改深至 4.5:1 会与真实输入值难以区分 | 接受为已知偏差；若评审否决，则移除占位符示例、只保留标签（二选一，不得两者兼得） |
| D-2 | PC 页面标题取 20px（基线为 H1 24px） | 一期冻结页无页面标题结构且工具栏标题为 12–16px 级（[employee/index.vue:777](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/views/employee/index.vue)、[department/index.vue:211](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/views/department/index.vue)、[station/index.vue:293](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-admin/src/views/station/index.vue) 均为 12px）；24px 会与冻结页产生明显割裂 | 24px 保留给入口页与移动 Hero；`TODO(扩展): 二期统一改造一期页面时同步升级为 24px` |
| D-3 | 输入框描边改用 `#8A93A0`（3.10:1），明显深于 Element/Vant 默认 | SC 1.4.11 要求可交互控件边界 ≥3:1，默认 `#dcdfe6`（约 1.4:1）不达标 | 需评审二选一：① 接受加深（推荐，合规）；② 改为"浅底填充 + 深色下划线"替代方案 |
| D-4 | 一期冻结页（员工/部门/驿站/个人中心/登录）内部硬编码色值不修改 | 硬约束禁止改动 `hrm-admin` | 被动继承新主色变量；其余记为遗留项，`TODO(扩展): 二期统一改造` |
| D-5 | 移动端 tab 文字 10px（Vant 默认）低于 12px 正文下限 | 5 项 Tabbar 在 320px 宽下 10px 是唯一不换行的选择 | 接受；若评审否决需减少 Tab 数量（不推荐，会破坏信息架构） |
| D-6 | 环比数据由"近 7 天日均 vs 今日"本地推导 | 数据契约（demo-design.md 7.4.7）无同比/环比字段 | 标注 `TODO(扩展): 后端出环比字段后改为直接取值`；推导口径写入页面 Caption |

### 9.4 待走查项（无法静态确认，实现后由测试工程师复核）

1. Vant 组件默认内边距实测值（cell / tabs / tag / search）是否与 2.9 覆盖一致（U1）；
2. Element Plus 浅色阶变量实际值（U2）与按钮/表格变量名（U3）；
3. `el-table` 行高在 Token 覆盖后是否真正为 44px；
4. Tabbar 实际高度与 FAB `bottom` 计算是否吻合（U5）；
5. `van-tag` `plain` 与自定义 `solid` 形态的视觉重量是否与 PC `StatusTag` 匹配；
6. 320px 视口下老板端 Hero 内两行文字与 chip 是否溢出。

---

## 10. 落地实施清单（原子任务，文件级）

**通用约定**：每个任务 = 一个文件（或一个新建文件）；P0 = 必须完成才可交付演示；P1 = 建议完成；P2 = 可选/延后。
**执行顺序建议**：先做 T01–T05（两端 Token 基座），再按端推进组件（T06–T12、T20–T30），最后页面（T13–T18、T31–T45），P2 收尾。

**任务统计**：共 **50** 项（P0 = 34 项、P1 = 12 项、P2 = 4 项）；其中**新建文件 7 个**（T01/T02/T04/T06/T07/T08/T28）、**改造或重写现有文件 40 个**、**纯复核/走查 3 项**（T46/T48/T49）。

### 10.1 Token 基座

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T01 | P0 | `src/pc/styles/tokens.scss`（新建） | 落地 2.2–2.6、2.10 全部变量 + 2.8 Element 变量覆盖 |
| T02 | P0 | `src/pc/styles/element-overrides.scss`（新建） | 2.8 的按钮 hover 加深、卡片描边、表格行高/字色、全局 `focus-visible` |
| T03 | P0 | `src/pc/main.js` | 在 `element-plus/dist/index.css` 之后依次 `import './styles/tokens.scss'`、`./styles/element-overrides.scss`（顺序错则不生效） |
| T04 | P0 | `src/mobile/styles/tokens.scss`（新建） | 落地 2.2–2.6、2.9、2.10 全部变量 + Vant 根级/组件级覆盖 + `--grad-hero` 等自定义 L3 |
| T05 | P0 | `src/mobile/main.js` | `import './styles/tokens.scss'` 置于 `mobile.scss` **之前** |

### 10.2 PC 组件

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T06 | P0 | `src/pc/components/PageHeader.vue`（新建） | 实现 C-P1（H1 20/600 + 副信息 + 右侧操作） |
| T07 | P0 | `src/pc/components/StateBlock.vue`（新建） | 实现 C-P7（empty/error/denied，修 P14） |
| T08 | P0 | `src/pc/components/MetricCard.vue`（新建） | 实现 C-P2（variants: hero/plain/inline；8 态齐备；40×40 浅底图标，删紫色渐变） |
| T09 | P0 | `src/pc/components/MiniStats.vue` | 改为纯栅格容器（显式 cols prop），内部用 MetricCard，支持整体 loading/error（修 P9/P13） |
| T10 | P0 | `src/pc/components/StatusTag.vue` | 按 2.7 映射表实现 soft/outline/solid 三形态（Token 映射，不改 dict.js） |
| T11 | P0 | `src/pc/components/SlaCountdown.vue` | 四态 + 25% 阈值 + 胶囊形态 + 首次超时 aria 播报（修 P39 相关色值） |
| T12 | P0 | `src/pc/components/TrendChart.vue` | 按 6.1 重写绘制（ResizeObserver、nice-number 轴、数据点、hover 十字线、图例开关、non-scaling-stroke） |

### 10.3 PC 布局与页面

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T13 | P0 | `src/pc/layout/index.vue` | 侧栏 `#1F2937`、菜单激活左色条、Logo 主色、头部 Token 化、折叠按钮与用户菜单改 `<button>` + `aria`、删除 `outline:none`、`<1200px` 自动折叠、内容区 padding 16 + 底色 `--c-neutral-50`（修 P20/P22） |
| T14 | P0 | `src/pc/views/dashboard/index.vue` | 按 5.2 重排：PageHeader + MetricCard 两行 + 趋势/健康度 + 排行行组件/工单 SLA（4 项 2×2 + 时长全宽）+ 组织规模折叠；区块级三态；删紫色渐变与 `#f56c6c`（修 P3/P7/P8/P18/P19） |
| T15 | P0 | `src/pc/views/parcel/index.vue` | 按 5.3：PageHeader + 筛选栏规范 + 去 stripe/加 hover + 行高 44 + 分页 20 + 筛选同步 URL + StateBlock 三态 + 删负 margin（修 P6/P12/P13/P14/P15） |
| T16 | P0 | `src/pc/views/sync/index.vue` | 同 5.3 模板；日志时间线轴点色按 2.7；禁用按钮加 `title` 说明；抽屉统一宽度 |
| T17 | P0 | `src/pc/views/workOrder/index.vue` | 按 5.4：Tab 移出卡片、超时行改左竖条 + 去 `!important`、优先级/状态按 2.7、抽屉与指派弹窗规范（修 P17） |
| T18 | P1 | `src/pc/views/notification/index.vue` | 按 5.5：列表项 Token 化、未读加文字标签、行可键盘聚焦、操作按钮 hover 显隐、相对时间（修 P21） |
| T19 | P2 | `src/pc/styles/element-overrides.scss`（追加） | 表头吸顶（`position: sticky`）与路由切换进度条（若确定要做） |

### 10.4 移动端组件

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T20 | P0 | `src/mobile/styles/mobile.scss` | 重写：移除 `--hrm-*`（并入 tokens.scss 并对齐命名）、卡片 `--r-lg` + padding 16、`list-item` padding 12/16 + 间距 12、`section-title` 上下 20/8、`--c-neutral-500` 替换 `--hrm-text-3`、动效降级、`tabular-nums` 工具类 |
| T21 | P0 | `src/mobile/layout/TabbarLayout.vue` | Tabbar 色改 Token（`#0958D9`/`#6B7280`）、高 `--tabbar-h`、`--safe-bottom`、内容区底部内边距变量化（修 P38） |
| T22 | P0 | `src/mobile/components/StatCard.vue` | 实现 C-M1（tone 取 600/700 档、`dense` 变体、8 态、≥88px 高、可点语义化） |
| T23 | P0 | `src/mobile/components/LineChart.vue` | 按 6.1 重绘（ResizeObserver、末值标注、compact 极简刻度、Token 配色、图例 44px + `aria-pressed`、补 `aria-label`）（修 P36/P39/P40） |
| T24 | P0 | `src/mobile/components/StatusTag.vue` | 按 2.7 三形态 + 胶囊 20px（修 P5/P30） |
| T25 | P0 | `src/mobile/components/SlaTag.vue` | 四态 + 25% 阈值 + 图标（修 P39 相关） |
| T26 | P0 | `src/mobile/components/PageNav.vue` | 44px NavBar、返回热区 44、标题 H2 16/600 + 长标题 ellipsis |
| T27 | P0 | `src/mobile/components/PageState.vue` | 实现 C-M6（300ms 延迟骨架、错误态 / 空态统一、重试按钮 44px、`role="alert"`） |
| T28 | P0 | `src/mobile/components/ActionBar.vue`（新建） | 实现 C-M8（single/dual/multi + 安全区 + 反投影 + 页面底部内边距配套） |
| T29 | P0 | `src/mobile/components/MeSection.vue` | 渐变改 `--grad-hero`、cell-group 缩进对齐页面、区块标题字阶、退出按钮规范（修 P3/P27） |
| T30 | P1 | `src/mobile/components/IdentitySwitcher.vue` | `--c-blue-50` + 描边 + 勾选图标、`role="radiogroup"`（修 P27/P33） |

### 10.5 移动端页面

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T31 | P0 | `src/mobile/views/login/index.vue` | 按 5.7-B1：色值 Token 化、按钮高 32、卡片描边替代彩色阴影、字阶归位（修 P27/P34） |
| T32 | P0 | `src/mobile/views/staff/home.vue` | 按 5.8-S1：Hero Token 化、指标字阶、宫格 24px 图标/88px 单元、提示条色（修 P3/P4） |
| T33 | P0 | `src/mobile/views/staff/parcel.vue` | 按 5.8-S2：搜索/Tab 通栏、元信息拆行、警示 12px、行键盘可达、三态区分（修 P24/P26/P33） |
| T34 | P0 | `src/mobile/views/staff/pickup.vue` | 按 5.8-S4：输入自动聚焦、ActionBar、状态警示条、演示运单号 44px（修 P31/P34） |
| T35 | P0 | `src/mobile/views/staff/workorder.vue` | 按 5.8-S5：chip 44px、行高 ≥76、FAB 变量化 48px、行键盘可达（修 P32/P33/P34） |
| T36 | P0 | `src/mobile/views/staff/workorderDetail.vue` | 按 5.8-S7：ActionBar 固定底部（**最高优先级交互修复**）、时间线 Token 化、备注弹层 |
| T37 | P0 | `src/mobile/views/staff/parcelDetail.vue` | 按 5.8-S3：ActionBar 双按钮、禁用原因移到按钮外 |
| T38 | P1 | `src/mobile/views/staff/workorderCreate.vue` | 按 5.8-S6：类型/优先级改 2×2 卡片单选、字段级校验、ActionBar 提交 |
| T39 | P1 | `src/mobile/views/staff/notification.vue` | 按 5.8-S8：全部已读 44px、未读竖条、Tag 统一、`van-list` 分页、行键盘可达（修 P33/P34） |
| T40 | P1 | `src/mobile/views/staff/sync.vue` | 按 5.8-S9：胶囊化、失败行竖条、日志弹层字阶 |
| T41 | P1 | `src/mobile/views/staff/password.vue` | 字段级校验、按钮/间距 Token 化、字阶归位 |
| T42 | P1 | `src/mobile/views/boss/home.vue` | 按 5.7-B2：新增 Hero + 环比 + 趋势末值 + 同步健康度 + 排行 TOP3 + 组织规模折叠；文案与色值 Token 化（修 P24） |
| T43 | P1 | `src/mobile/views/boss/trend.vue` | 删除自定义 `.metric` 改用 StatCard（修 P35）、Tab 通栏、图例状态提升到页面、文案 12px |
| T44 | P1 | `src/mobile/views/boss/rank.vue` | 按 5.7-B4：chip 44px、进度条 8px + Token 色、徽标色阶、副信息 12px、弹层规范（修 P34） |
| T45 | P1 | `src/mobile/views/boss/alerts.vue` | 按 5.7-B5：分组计数 Tag 语义修正、列表项底色改白 + 描边、严重度色点、文案 12px（修 P24/P30） |
| T46 | P2 | `src/mobile/views/boss/me.vue`、`src/mobile/views/staff/me.vue` | 仅核对：确认随 T29 生效，无本文件改动（若需差异化文案再单独调整） |

### 10.6 入口页与收尾

| ID | 优先级 | 文件 | 改动要点 |
| ---- | ---- | ---- | ---- |
| T47 | P1 | `index.html` | 按 5.9：色值/圆角/间距 Token 对齐、卡片顶色条区分端、`:focus-visible`、达标文案前缀、`@media` 间距修正 |
| T48 | P1 | `src/pc/styles/tokens.scss`、`src/mobile/styles/tokens.scss`（复核） | 落地后按 9.4 走查项核对 U1/U2/U3，把核对结果写回注释（若 Vant/Element 变量名不符，改为等价类选择器实现） |
| T49 | P2 | 全局 | 视觉走查：按 9.2 核对表逐项打勾，输出走查记录（可附在实施 PR 描述中） |
| T50 | P2 | `src/mobile/styles/mobile.scss` | 横屏（`orientation: landscape`）单列 + `max-width: 640px` 居中 |

**任务统计**：共 **50** 项（P0 = 34 项、P1 = 12 项、P2 = 4 项）；其中新建文件 7 个（T01/T02/T04/T06/T07/T08/T28）、改造或重写现有文件 40 个、纯复核与走查 3 项（T46/T48/T49）。

---

## 附录 A：交付物自检（对照任务硬约束）

| 约束 | 状态 |
| ---- | ---- |
| 只产出设计文档，不改源码 | 满足（本文件为唯一产出） |
| 不改 `hrm-admin` / `hrm-server` / `hrm-android-shell` / `shared/` | 满足（1.6 冻结清单；所有任务仅落在 `src/pc`、`src/mobile`、`index.html`） |
| 基于现有技术栈，不引新 UI 库/图表库 | 满足（6.7 给出维持手写 SVG 的对比结论） |
| 中文输出；不确定处标注并给验证方式 | 满足（0.3 U1–U6、9.3 D-1–D-6、9.4 待走查项） |
| 配色符合企业内部管理系统专业调性 | 满足（蓝灰为骨、蓝为主、橙为物流强调；删除紫色 AI 默认风） |
| 具体到可直接改代码 | 满足（全部色值/字号/间距/行高/尺寸给出数值；10 章为文件级任务） |

## 附录 B：本轮完整阅读清单（42 个文件）

**设计依据（4）**：`docs/demo-design.md`（5 章、7.4 节）、`docs/requirement.md`、`hrm-admin/src/styles/index.scss`、`hrm-admin/src/views/dashboard/index.vue`（用于核对一期基线）

**网页端（13）**：`pc/App.vue`、`pc/main.js`、`pc/layout/index.vue`、`pc/config/menu.js`、`pc/router/index.js`、`pc/views/{dashboard,parcel,sync,workOrder,notification}/index.vue`、`pc/components/{MiniStats,StatusTag,SlaCountdown,TrendChart}.vue`

**移动端（20）**：`mobile/App.vue`、`mobile/main.js`、`mobile/vant.js`、`mobile/layout/TabbarLayout.vue`、`mobile/styles/mobile.scss`、`mobile/constants/tabs.js`、`mobile/router/index.js`、`mobile/utils/format.js`、`mobile/components/{StatCard,LineChart,StatusTag,SlaTag,PageNav,PageState,MeSection,IdentitySwitcher}.vue`、`mobile/views/login/index.vue`、`mobile/views/boss/{home,trend,rank,alerts,me}.vue`、`mobile/views/staff/{home,parcel,parcelDetail,pickup,workorder,workorderCreate,workorderDetail,notification,sync,me,password}.vue`

**入口与配置（5）**：`portal/main.js`、`index.html`、`pc.html`、`mobile.html`、`vite.config.js`
