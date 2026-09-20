# 采集端 · 目标页前端技术检查报告

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 检查日期 | 2026-09-19 |
| 状态 | 待评审 |
| 检查对象 | `https://mdkd.pinduoduo.com/vw/waybillCenter/waybillQuery`（多多买菜门店端 · 运单查询） |
| 检查方式 | 内置浏览器（Chromium）· 已登录会话 · **只读** |
| 关联文档 | [collector-site-analysis.md](collector-site-analysis.md)（结构分析）、[plan.md](plan.md)（二期驿站数据同步） |

> 全部数值来自本次实测（`browser_evaluate` 读取 DOM / Performance API、控制台消息接口、网络请求接口、截图）。
> **未能获取的维度一律标注「未能获取」并说明原因，不做估算、不做编造。**
> 认证与风控头（Cookie / Authorization / `anti-content` / `etag` / `pdd-id`）**只记录是否存在，不记录取值**；业务数据（运单号 / 收件人 / 手机号 / 地址 / 机构码）一律掩码。

***

## 0. 检查环境与合规

| 项 | 值 |
| ---- | ---- |
| 浏览器 | Trae 内置浏览器（Chromium） |
| 实际视口 | **810×658** CSS px，devicePixelRatio 1.25 |
| 登录态 | 有效（标题 `多多买菜`，`readyState=complete`，分页显示「共 133965 条」） |
| 页面加载次数 | 1 次（未刷新、未点击任何按钮） |
| 风控熔断 | **未触发**：无验证码/滑块、无 403/429、无跳登录、无结构突变；业务接口全部 200 |
| 只读合规 | 未提交表单、未发起写请求；`BODY#watermark` 隐藏帧与 `xg.pinduoduo.com` 埋点仅只读观察 |

***

## 1. Elements — DOM 结构与元素属性

### 1.1 DOM 层级路径

```
html
└ body
  └ div#root
    └ div.app-layout                 (min-width:1280px; width:1280px)
      └ div.auth-container           (width:1280px → div.app-left w=267 + div.app-content w=1013)
        └ div.app-content            (display:flex; width:1013px)
          └ div.page-container       (display:flex; width:1013px)
            └ div.waybill-center-waybill-query-container  (width:981px; display:block)
```

侧栏菜单根：`ul.station-menu.station-ver.app-sider-menu`（宽 252px）。

### 1.2 节点规模

| 指标 | 值 |
| ---- | ---- |
| 总 DOM 节点数 | **2481**（`#root` 子树 2318） |
| 表头列数 | 18 个 `thead th` |
| 表体数据行 | 50 行（另有隐藏测量行 `tr.rocket-table-measure-row` ×1） |
| 右固定单元格 | 50 个（每行 1 个） |

> 注：上一轮记录的可见列名为 17 列，本轮 `thead th` 计数为 18，差异疑为其中一列拆分了表头单元格，**不确定**，采集端取数应以列名文本匹配为准而非索引。

### 1.3 关键元素属性

| 元素 | 属性 |
| ---- | ---- |
| `div.rocket-table` | 仅 class：`rocket-table` `rocket-table-bordered` `rocket-table-ping-right` `rocket-table-fixed-header` `rocket-table-fixed-column` `rocket-table-scroll-horizontal` `rocket-table-has-fix-right`；**无 data-\*** |
| 外层 `div.rocket-spin-container` | 仅 class |
| `section.query-content` | 仅 class，**无 data-\***；含 2 个 `div.station-row.station-row-wrap` |
| `div.self-pagination` | 仅 class，**无 data-\*** |
| `tr.rocket-table-row` | `data-row-key="0"` + class `rocket-table-row rocket-table-row-level-0 rocket-table-row-striped` |
| `td.rocket-table-cell` | 仅 class |
| `td.rocket-table-cell-fix-right` | class `rocket-table-cell rocket-table-cell-fix-right rocket-table-cell-fix-right-first` + 内联 `position: sticky; right: 0px;` |

**结论**：业务标识类 `data-*` 属性**几乎不存在**（仅行级 `data-row-key`，且为序号非业务主键）。采集端定位元素只能依赖 **class + 列序 + 列名文本**。

### 1.4 CSS 样式溯源（`getComputedStyle` 实测）

| 元素 | 关键计算样式 |
| ---- | ---- |
| `div.rocket-table` | display:block；width:941px；max-width:none；overflow/overflow-x/overflow-y 均 **visible**；position:relative；font-size:14px；table-layout:auto；border-collapse:separate |
| `tr.rocket-table-row` | **height:74.8px**；font-size:14px；line-height:21px；display:table-row |
| `td.rocket-table-cell` | padding:**16px**（四边）；border-bottom:0.8px solid rgb(232,232,232)；border-right-width:0.8px；**white-space:normal**；text-overflow:clip；overflow:visible |
| `section.query-content` | display:block；flex 相关均为默认值（**本身不是 flex 容器**） |
| `.station-row.station-row-wrap` | display:flex；**flex-wrap:wrap**；子项宽约 312.3px，共 16 个子项 |
| `td.rocket-table-cell-fix-right` | **position:sticky；right:0px；z-index:2**；background-color:rgb(248,249,250)；box-shadow:none |

> 采集注意：单元格 `white-space:normal` + `overflow:visible`，长文本会**换行**而非省略号，故行高会随内容变化（实测 74.8px 为当前数据下的值，非固定值）。

### 1.5 样式表清单与来源判定

| 项 | 值 |
| ---- | ---- |
| 外链 `<link rel=stylesheet>` | 4 个 |
| `<style>` 标签 | 87 个（**全部在 `<head>`，`body` 内 0 个**），其中 23 个为本自动化环境注入，64 个为站点运行时注入 |
| 内联 style 属性元素 | 170 个 |

外链 CSS URL（跨域 `pfile.pddpic.com`）：

- `https://pfile.pddpic.com/mdkd/vw/static/css/main.6bb34e22.chunk.css`
- `https://pfile.pddpic.com/mdkd/vw/static/css/48.1019dab2.chunk.css`
- `https://pfile.pddpic.com/mdkd/vw/static/css/47.f7a4a93d.chunk.css`
- `https://pfile.pddpic.com/mdkd/vw/static/css/waybillCenter-waybillQuery.a3a62291.chunk.css`

**来源判定**：

- 4 个外链 CSS 均跨域，`cssRules` 不可读（CORS）—— Rocket/station 组件样式即在此。
- 站点运行时注入的 64 个 `<style>` 均为 **CSS Modules 哈希类名**（如 `.buttonsCard-bTXa5j`、`.actionButton-opZL8g`）。
- 未发现 `data-styled` / `data-emotion` / `data-goober` 标记 → **构建期 CSS Modules + 运行时注入**，非组件库 CSS-in-JS。
- 冗余迹象：4 段样式块各**重复出现 21 次**（文本长度 7758 / 1515 / 4567 / 1375）。

**UI 框架**：类前缀计数 `rocket-` 1008、`station-` 164、`self-` 1；`ant-` / `pdd-` / `mdkd-` 均为 0 → Rocket UI + 站点自研 `station` 组件，**无 antd**。

**CSS 变量**：站点引用 32 个，示例：`--spacer-4/8`、`--radius-4/8/sm`、`--spacing-xs/sm`、`--transition-fast`、`--font-family-default`、`--text-text-default/-tertiary/-disabled/-onaccent`、`--bg-bg-overlay-l1/l2/l3`、`--bg-bg-menu`、`--border-border-neutral-l1/l2/l3`、`--icon-icon-default/-secondary/-onaccent`、`--status-primary-default`、`--status-success/warning/error-default`。
**变量取值不可得**：外链 CSS 跨域不可读，且 `html`/`body` 上 `getPropertyValue` 为空。

***

## 2. Console — 控制台输出

总计 **17 条**：`info` 11、`error` 4、`warn` 2、`log` 0、`debug` 0。

### 2.1 error（4 条，各 1 次）

来源均为站点 main chunk `main.23ac7e68.chunk.js:1`，全部为 `net::ERR_ABORTED`（埋点/上报被中断，**非 CORS**）：

| 文本 |
| ---- |
| `net::ERR_ABORTED https://apm.pinduoduo.com/api/pmm/defined` |
| `net::ERR_ABORTED https://apm.pinduoduo.com/api/pmm/front_log` |
| `net::ERR_ABORTED https://apm.pinduoduo.com/api/pmm/front_err` |
| `net::ERR_ABORTED https://st.pinduoduo.com/st.gif` |

### 2.2 warning（2 条）

| 文本 | 来源 | 归属 |
| ---- | ---- | ---- |
| `single-spa minified message #1: See https://single-spa.js.org/error/?code=1` | `main.23ac7e68.chunk.js` | 站点（single-spa 微前端提示） |
| `MaxListenersExceededWarning: 11 vscode:icube:webview:browserUse listeners added` | `node:electron/js2c/sandbox_bundle` | **本次自动化环境自身，非站点代码** |

### 2.3 info（11 条）

版本横幅 `v20260913.16.22.43#20250325#info`（1）、`success!`（1）、`api ? https://tc.pinduoduo.com/ct.gif`（1）、`fallbackFetch prefetch <URL>`（8，预取 cvw 子应用资源 main.css / rocket-ui / lodash / axios / icons / moment / main.js 等）。

### 2.4 专项判定

| 项 | 结论 |
| ---- | ---- |
| 跨域 CORS 报错 | **未观测到** |
| 资源 404 | **未观测到** |
| 混合内容 Mixed Content | **无**（Resource Timing 中 `http://` 计 0） |
| 弃用警告 Deprecation | 未观测到站点侧弃用警告 |
| 内存/性能警告 | 仅环境自身的 `MaxListenersExceededWarning` |
| 未捕获异常 | **未能单独获取**（采集接口未区分 `window.onerror` / `unhandledrejection`，仅能给出上述 4 条错误记录；未观测到脚本级异常，但不能断言「绝无」） |

***

## 3. Network — 网络请求

### 3.1 总量与类型分布

请求总数：网络工具口径 **100** 条；Resource Timing 口径 **75** 条（差异为 OPTIONS 预检、中断请求与部分 beacon）。

| 类型 | 数量 | 解码体积 |
| ---- | ---- | ---- |
| Fetch（微前端子应用 JS/CSS） | 22–24 | 1,839,559 B（≈1.75 MB） |
| XHR | 27–31 | 0（跨域未暴露） |
| Script (JS) | 12 | **4,623,554 B（≈4.41 MB）** |
| Stylesheet (CSS) | 4 | **1,248,281 B（≈1.19 MB）** |
| Image | 8–13 | 20,379 B |
| Font | 1 | — |
| Document | 1 | 13,532 B（传输 6,004 B） |
| Other（OPTIONS 预检） | 14 | — |
| **合计** | **75** | **≈7,731,773 B（≈7.37 MB）** |

### 3.2 状态码分布

| 区间 | 数量 |
| ---- | ---- |
| 2xx | 全部可读请求均为 **200**（含全部业务接口） |
| 3xx / 4xx / 5xx | **0** |
| 失败 | 4 个 `net::ERR_ABORTED`（apm / 埋点上报类） |

> 状态码取自 Resource Timing `responseStatus`；部分跨域请求状态不可得（显示 `-`）。
> 工具不直接提供逐条状态码字段，也无法提供逐条真实传输体积（跨域资源缺 `Timing-Allow-Origin`，`transferSize`/`encodedBodySize` 被置 0）——**该维度未能获取**。

### 3.3 业务接口明细（`mdkd-api.pinduoduo.com`，共 15 个 XHR，全部 200）

| 接口 | 方法 | 状态 | 耗时 |
| ---- | ---- | ---- | ---- |
| `/api/codelivery/data/query_waybill` | POST | 200 | **644 ms** |
| `/api/codelivery/super_admin/provider_authority/query` | POST | 200 | 149 ms |
| `/api/orion/basic/self_post/setting` | POST | 200 | 138 ms |
| `/api/codelivery/data/query_site_order_status_list` | POST | 200 | 116 ms |
| `/api/codelivery/common/shipping/list` | POST | 200 | 111 ms |
| `/api/orion/ac/account/info` | POST | 200 | 109 ms |
| `/api/codelivery/emp/user_info` | POST | 200 | 106 / 81 / 68 ms（3 次） |
| `/api/codelivery/org/query_has_permission_org_list` | POST | 200 | 120 / 69 ms（2 次） |
| `/api/codelivery/emp/has_permission_emp_list` | POST | 200 | 121 / 112 / 100 / 81 ms（4 次） |

### 3.4 极值

| 项 | 内容 |
| ---- | ---- |
| 耗时最长 3 个 | ① `query_waybill` **644 ms**；② `file-link.pinduoduo.com/OwdljvkdgF`(img) 151 ms；③ `provider_authority/query` 149 ms |
| 体积最大 3 个 | ① `main.23ac7e68.chunk.js` **2,108,921 B**；② `main.6bb34e22.chunk.css` **1,219,336 B**；③ `waybillCenter-waybillQuery.63d2fac1.chunk.js` 773,863 B |

### 3.5 重复性心跳请求（非业务接口）

| 请求 | 次数 |
| ---- | ---- |
| `xg.pinduoduo.com/xg/pfb/a2`（风控埋点） | ≈11 |
| `apm.pinduoduo.com/api/pmm/defined` | ≈8 |
| `mdkd.pinduoduo.com/vw/api/version-check` | ≈5 |

### 3.6 脱敏声明

Cookie、Authorization、`anti-content`、`etag`、`pdd-id` 等认证/风控头**本轮未读取、未记录任何取值**；运单号、收件人、手机号、地址、机构码**未采集、未输出**。

***

## 4. 响应式布局

> **【重要】本节六档尺寸未能实测。**
> 本自动化环境**不提供任何视口/窗口尺寸控制能力**（已枚举全部工具，无 viewport / emulate / resize），页面内 `window.resizeTo(1920,1080)` 为无效操作（前后 `innerWidth/innerHeight` 均保持 810×658）。
> 下表「推断」列仅基于 CSS 判定，**不是实测结果**，不得作为验收依据。

| 视口 | 状态 | 横向滚动条 | 筛选区 | 表格区 | 分页区 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| **810×658** | **已实测** | **有**（`documentElement.scrollWidth=1280 > clientWidth=795`） | block 容器 + 2 行 `flex-wrap` 行，16 子项各约 312px，**自动换行** | 表格内部横向滚动：`div.rocket-table-body` scrollWidth **2410** / clientWidth 925 | `display:flex`，宽 941px，**未错位** |
| 1920×1080 / 1440×900 / 1366×768 | 未能测试（推断无横滚） | — | 推断不换行 | 推断仍内部横滚（内容宽约 2410px） | 推断正常 |
| 1024×768 | 未能测试（推断有横滚） | — | 推断换行 | 推断内部横滚 | 推断正常 |
| 768×1024 / 375×812 | 未能测试（推断有横滚） | — | 推断换行 | 推断内部横滚 | 推断可能挤压 |

### 4.1 布局定性（可判定）

**该页是固定宽度桌面布局，非自适应。**

依据（实测）：

1. `div.app-layout` 计算样式 `min-width:1280px; width:1280px`；在 810px 视口下文档 `scrollWidth` 仍为 1280px —— 存在硬性最小宽度。
2. 表格另有**独立的固定内容宽度约 2410px**，触发容器内横向滚动（`rocket-table-scroll-horizontal`）。
3. 查询条件不随视口缩放，靠 `flex-wrap` 换行容纳。
4. 媒体查询探测：`min-width:768px` 匹配、`max-width:1024px` 亦匹配（与 810px 视口一致）。

### 4.2 后续验证方法

若必须补齐六档实测，需在**具备视口控制能力的环境**（如本地 Playwright `page.set_viewport_size`，或 Chrome DevTools 设备模拟）中重跑；本轮工具链不具备该能力。

***

## 5. 性能与关键渲染路径

### 5.1 Navigation Timing（单次导航加载）

| 阶段 | 耗时 |
| ---- | ---- |
| redirect | 0 ms |
| dns | 0 ms |
| tcp | 0 ms |
| ttfb（responseStart − requestStart） | **31 ms** |
| responseEnd | 37 ms |
| domInteractive | 149 ms |
| domContentLoaded | 149 ms |
| **loadEventEnd** | **1,147 ms** |

### 5.2 Paint

| 指标 | 值 |
| ---- | ---- |
| first-paint | **688 ms** |
| first-contentful-paint | **688 ms** |
| LCP | **不可获取**（`getEntriesByType('largest-contentful-paint')` 返回 0 条；页面未注册对应 PerformanceObserver，API 不支持事后回溯） |
| CLS | **不可获取**（`layout-shift` 返回 0 条；同上） |
| 长任务 long task 数 | **不可获取**（未注册 `PerformanceObserver('longtask')`，无法回溯） |

### 5.3 资源体积

| 项 | 值 |
| ---- | ---- |
| JS 解码总量 | **≈4.62 MB**（另有微前端 fetch 载入 JS ≈1.75 MB） |
| CSS 解码总量 | **≈1.19 MB** |
| 图片解码总量 | ≈20 KB |
| 文档体积 | 13,532 B（传输 6,004 B） |
| DOM 节点总数 | 2481 |

### 5.4 关键渲染路径

| 项 | 结论 |
| ---- | ---- |
| 渲染阻塞 CSS | `<head>` 内 4 个无 `media`/`disabled` 的 `<link rel=stylesheet>`（即 §1.5 的 4 个 URL），均为阻塞；其中 `main.6bb34e22.chunk.css` 约 1.16 MB 解码，是首屏样式的主要成本 |
| 渲染阻塞脚本 | **无**。`<head>` 内 33 个脚本**全部带 `async`**（webpack 异步 chunk）；`blockingHeadScripts` 为空 |
| 主入口脚本 | `main.23ac7e68.chunk.js` 位于 `<body>` 末尾，为**非 async/defer 的经典脚本**（不阻塞首屏渲染，但阻塞 DOMContentLoaded 完成） |
| 预连接 | `<link rel=dns-prefetch>`：`pfile.pddpic.com`、`mdkd-api.pinduoduo.com` |

### 5.5 首屏渲染链路

**客户端渲染（CSR）：JS 加载后 XHR 取数再渲染，非 HTML 直出。**

依据：
1. HTML 仅为 `#root` 外壳，文档仅 13.5 KB，**不含任何业务表格 HTML**。
2. 表格与筛选内容由 React 挂载后发起 15 个 `mdkd-api` XHR（含 `query_waybill` 644 ms）再渲染。
3. 渲染进程入口为 **single-spa 微前端**（`cvw` 子应用通过 `fallbackFetch` 预取）。

### 5.6 采集端启示

- `query_waybill` 单次 644 ms（50 条）。全量 133,965 条约需 **2680 次请求**，按串行 + 限速估算不可忽视，**进一步佐证应优先走平台原生导出通道**（见 [collector-site-analysis.md](collector-site-analysis.md) 第 7、10 章）。
- 页面为重前端应用（首屏 JS+CSS ≈5.8 MB），采集端若走浏览器渲染路线，单页成本高；若走导出文件解析则完全规避。

***

## 6. 截图清单

| 截图 | 路径 | 对应内容 |
| ---- | ---- | ---- |
| 整页（fullPage） | `%TEMP%\trae\screenshots\waybill-fullpage-vp810x658.png` | 视口 810×658 @dpr1.25 |
| 表格区 | `%TEMP%\trae\screenshots\waybill-table-region.png` | 元素 `div.rocket-table` |
| 当前视口 | `%TEMP%\trae\screenshots\waybill-viewport.png` | 视口截图 |
| 筛选区 / 表格区 / 分页区（上轮） | `%TEMP%\trae\screenshots\waybill-query-filter.png` 等 | 见结构分析文档第 11 章 |

> 说明：本环境**无法打开浏览器原生 DevTools 面板 UI**，故「Elements 面板截图 / Network 面板截图 / Performance 面板截图」未产出；以上为页面级截图 + 等价 API 采集数据。另有两张早期截图文件名含 `1920`（`waybill-full-1920.png`、`waybill-table-area.png`），**实际视口仍为 810×658**，文件名仅命名，易误读，已标注。

***

## 7. 未能获取的维度（逐条说明原因）

| # | 维度 | 原因 |
| ---- | ---- | ---- |
| 1 | 六档响应式实测与逐档截图 | 自动化环境无任何视口控制 API，`window.resizeTo` 无效；表格内宽屏判定为基于 `min-width:1280px` 的**推断，非实测** |
| 2 | LCP / CLS | 无对应 PerformanceObserver 记录，API 不可事后回溯 |
| 3 | 长任务(long task)数量 | 同上 |
| 4 | 逐请求真实传输体积 | 跨域资源缺 `Timing-Allow-Origin`，`transferSize`/`encodedBodySize` 被置 0；仅文档自身可读 |
| 5 | 逐请求完整状态码 | 工具不直接提供；已用 Resource Timing `responseStatus` 部分补齐，仍有一批跨域请求不可得 |
| 6 | CSS 变量取值 / 外链 CSS 规则内容 | 4 个外链样式表跨域，`cssRules` 抛 CORS；变量在 `html`/`body` 上解析为空 |
| 7 | 未捕获异常明细 | 采集接口未区分 `window.onerror` / `unhandledrejection`；未观测到脚本级异常，但不能断言「绝无」 |
| 8 | `BODY#watermark` 元素 | 本轮 DOM 中未发现 `id` 含 `watermark` 的元素（上轮曾观测到），页面另有 1 个无 `src` 的 iframe（303×153，display:inline），仅只读观察 |
| 9 | 认证/风控头取值 | 按脱敏红线不读取、不记录；仅沿用上轮结论（存在 `anti-content` / `etag` / `pdd-id`），未做任何伪造、逆向或绕过 |

***

## 8. 结论

1. **页面为纯 CSR 重前端**：首屏 JS ≈4.62 MB + CSS ≈1.19 MB（解码），FCP 688 ms、load 1.15 s；无渲染阻塞脚本，但有 1.16 MB 渲染阻塞 CSS。作为采集目标，渲染成本高。
2. **无业务语义锚点**：`data-*` 几乎不存在，元素定位只能依赖 class + 列序/列名文本，页面改版即失效，抓取脆弱性高。
3. **业务接口单次 644 ms**，全量 2680 页；叠加 `anti-content` 风控签名要求，**直连接口路线成本与不稳定度均高**。
4. **六档响应式未实测**：仅能确认该页为**固定宽度（min-width 1280px）桌面布局**，表格另有约 2410px 内宽触发内部横滚；逐档实测需换具备视口控制能力的环境。
5. **Console 干净**：无 CORS、无 404、无混合内容、无站点侧弃用警告；4 条 error 均为埋点上报中断。
6. 全程**未触发风控熔断**，未做任何反检测伪装。
