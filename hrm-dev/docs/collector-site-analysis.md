# 采集端 · 目标站点结构分析（多多买菜门店端）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 分析日期 | 2026-09-19 |
| 状态 | 待评审 |
| 分析对象 | 多多买菜门店端（`mcmd` 落地页 → `mdkd` 工作台） |
| 分析方式 | 内置浏览器 · 已登录会话 · **只读** |
| 关联文档 | [plan.md](plan.md)（二期驿站数据同步）、[db.md](db.md) |

> 本文只记录**实测观测到的结构与接口事实**，不含真实业务数据（运单号/收件人/手机号/机构码等一律掩码）。
> 未实测的部分统一标注「不确定」并列入第 9 章待验证清单，禁止据此推断。

***

## 1. 站点与登录链路

| 项 | 实测结果 |
| ---- | ---- |
| 入口地址 | `https://mcmd.pinduoduo.com/home` |
| 入口页性质 | **对外营销落地页**，`<title>` = 快递代收官网；技术栈 Next.js（React SSR，`__NEXT_DATA__` / `#__next`） |
| 登录后工作台 | `https://mdkd.pinduoduo.com/`（**域名与入口不同**） |
| 工作台技术栈 | React SPA（CRA/webpack 产物，`#root` 挂载，webpack 全局名 `webpackJsonplogistics-station-village-web`） |
| UI 框架 | 自研组件 + Rocket 系，class 前缀 `station-` / `rocket-`；**无 antd** |
| 路由 | history 路由，业务前缀 `/vw`，**无 hash、无 URL query 承载状态** |
| 登录态 | 会话保持正常，本轮全程未登出、未触发验证码 |

**采集端启示**：入口 `mcmd` 无采集价值，真实取数域是 `mdkd.pinduoduo.com`（页面）+ `mdkd-api.pinduoduo.com`（接口）。

***

## 2. 工作台功能地图（已登录账号可见）

左侧一级菜单为 `DIV[role=menuitem]`（无 href，点击展开），子项为 `<a href="/vw/...">`。
折叠态下子项不在 DOM 渲染，下面路由来自只读读取的 `$user.menuAuth.pageRouters`（**受账号权限限制，其他角色可能不同**）。

| 一级菜单 | 路由 | 二级 / 三级 |
| ---- | ---- | ---- |
| 组织管理 | `/organization` | 服务站管理 `/serviceStation`（服务站详情 `/stationDetails`、快递员映射 `/newDistributerMapping`、导入记录 `/staff/ImportRecord`）；映射管理 `/staffManagement`；审核管理 `/auditing` |
| 包裹管理 | `/package` | 退货暂存 `/send`；电子面单详情 `/waybill` |
| 运单中心 | `/waybillCenter` | 运单查询 `/waybillCenter/waybillQuery`；单号拦截 `/waybillCenter/waybillIntercept`；运营看板 `/waybillCenter/dashboard` |
| 作业中心 | `/operationCenter` | 运单导入 `/operationCenter/waybillImport`（导入记录 `/operationCenter/ImportRecord`）；下载任务 `/operationCenter/download` |
| 服务管理 | `/serviceManagement` | 工单数据 `/ticket`；云监控 `/cloudMonitor`；客户管理 `/client` |
| 个人中心 | `/business` | 我的钱包 `/bill`；账户管理 `/settingAccount` |
| 反馈与举报 | `/feedbackAndReport` | 廉正举报 `/incorruptReport` |
| （不入侧栏） | `/message` | 消息中心（`hideLeft=true`） |

**取数相关入口候选**：运单查询（主数据源）、下载任务（原生导出结果）、运单导入（反向写入）。
**负向观察**：菜单体系内**没有**独立的「入库 / 取件 / 到货 / 出库」入口，也**没有**独立「订单」入口。这类动作大概率在 App/移动端，或作为页面内按钮存在——仅为菜单层面结论，不足以判定其不存在。

***

## 3. 核心取数接口（本轮关键发现）

### 3.1 运单列表

| 项 | 实测值 |
| ---- | ---- |
| 接口 | `POST https://mdkd-api.pinduoduo.com/api/codelivery/data/query_waybill` |
| 类型 | XHR，JSON 请求 / JSON 响应（**非后端渲染**） |
| 分页模式 | **服务端分页**，响应仅返回当前页 |
| 入参（空条件时） | `siteOrgCode`(string) / `retentionDays`(number, 实测 0) / `pageSize`(number, 默认 50) / `pageNo`(number, 从 1 起) |
| 响应顶层 | `success`(bool) / `errorCode` / `errorMsg` / `showBackend`(bool) / `result`(object) |
| 响应 `result` | `resultList`(array, 长度 = pageSize) / `total`(number, 实测 133965) |
| 实测总量 | 133965 条 ÷ 50 = 2680 页 |

**单条运单字段（实测出现，仅列字段名）**：

`siteOrderSn`、`providerCode`、`providerName`、`siteCode`、`siteName`、`deliveryOrderSn`、`shippingCode`、`shippingName`、`trackingNumber`、`receiverName`、`receiverMobile`、`siteOrderStatus`、`deliveryOrderStatus`、`deliveryOrderStatusDesc`、`operatorCode`、`operatorName`、`gmtCreate`、`gmtModified`、`inCabinetTime`、`notifyStatusDesc`、`operationList`(string[])

另有大量可空字段：`orderType`、`courierCode`、`courierName`、`failTag`、`trackOrderType`、`returnReason`、`signerName`、`sendTime`、`signTime`、`returnTime`、`arriveTime`、`departTime`、`packageProperty`、`packagePropertyDesc`、`retentionDays`、`retentionTag`(num)、`notifyStatus`(num)、`printTag`(num)、`interceptTag`(num) 等。

### 3.2 请求头（风控事实，仅观测未逆向）

| 请求头 | 观测结论 |
| ---- | ---- |
| `Content-Type` | `application/json` |
| `Accept` | `application/json, text/plain, */*` |
| `anti-content` | **风控签名参数，位于请求头**，值为数百字符的不透明字符串 |
| `p-appname` | `DDStore-PC` |
| `etag` / `pdd-id` | 两个 32 位不透明 token，疑似会话 / 设备标识 |
| Cookie / UA / Referer / Origin | **未获取**（页面内 API 不可见） |

**结论**：接口**不是裸 HTTP 调用即可复用**。`anti-content` 是拼多多系风控签名，另有 `etag` / `pdd-id` 设备会话标识参与校验。

### 3.3 旁路接口（筛选下拉供数，未取请求体）

`/api/codelivery/emp/user_info`、`/api/codelivery/org/query_has_permission_org_list`、`/api/codelivery/emp/has_permission_emp_list`、`/api/codelivery/common/shipping/list`、`/api/codelivery/data/query_site_order_status_list`

***

## 4. 运单查询页 DOM 结构

### 4.1 骨架

```
div#root > div.app-content > div.page-container > div.waybill-center-waybill-query-container
  ├─ section.query-content          # 筛选区
  └─ section.table-section
       ├─ div.table-menu            # 按钮区（含「导出Excel」）
       ├─ div.rocket-table...       # 表格区
       └─ div.self-pagination       # 分页区
```

面包屑为纯文本节点（`运单中心 / 运单查询`），非独立组件。

### 4.2 表格

表头与表体为**分离的双 `table`**，根容器带 `rocket-table-fixed-header` / `rocket-table-fixed-column` / `rocket-table-scroll-horizontal` / `rocket-table-has-fix-right`。

| 用途 | 选择器 |
| ---- | ---- |
| 表格根 | `div.rocket-table` |
| 表头 | `div.rocket-table-header thead th.rocket-table-cell` |
| 数据行（**须排除测量行**） | `div.rocket-table-body tbody tr.rocket-table-row:not(.rocket-table-measure-row)` |
| 单行单元格 | `tr.rocket-table-row > td.rocket-table-cell` |
| 指定列 | `tr.rocket-table-row > td.rocket-table-cell:nth-child(N)` |
| 右固定操作列 | `tr.rocket-table-row > td.rocket-table-cell-fix-right` |

**坑位**：表体第 1 行是隐藏测量行 `tr.rocket-table-measure-row`，必须排除，否则行索引整体偏移 1。
**取值**：单元格文本用 `textContent`；视口外元素 `innerText` 可能为空。

### 4.3 列名（17 列，按显示顺序）

| # | 列名 | # | 列名 |
| ---- | ---- | ---- | ---- |
| 1 | 运单号 | 10 | 通知状态 |
| 2 | 快递公司 | 11 | 分拨入库时间 |
| 3 | 服务站 | 12 | 分拨出库时间 |
| 4 | 派件员 | 13 | 派件时间 |
| 5 | 操作员 | 14 | 代收入库时间 |
| 6 | 运单状态 | 15 | 签收时间 |
| 7 | 特殊件 | 16 | 运单最新状态 |
| 8 | 滞留天数 | 17 | 操作（右固定，含 出库 / 查看详情 / 退回） |
| 9 | 包裹属性 | | |

### 4.4 分页

| 用途 | 选择器 / 值 |
| ---- | ---- |
| 分页根 | `div.self-pagination > div.station-pagination.station-medium.station-normal.station-arrow-only` |
| 总条数 | `div.station-pagination-total`（文本「共 133965 条」） |
| 页码容器 | `div.station-pagination-pages > div.station-pagination-list > button.station-btn.station-pagination-item` |
| 当前页 | 页码按钮附加 `station-current` |
| 上一页 / 下一页 | `button.station-prev` / `button.station-next` |
| 每页条数 | `span.pagination-select`（当前 **50**） |
| 跳页输入框 | **不存在** |

***

## 5. 筛选区（16 个条件）

统一形态：`div.station-form-item`（标签 `.station-form-item-label label`，控件 `.station-form-item-control`）。

| 类别 | 条件（默认值） |
| ---- | ---- |
| 日期区间 ×5 | 分拨入库、分拨出库、派件时间、代收入库时间、签收时间（`span.rocket-calendar-picker`，两个只读 input，**默认全空**） |
| 文本输入 ×2 | 运单号（占位「请输入」）、收件人电话（占位「支持手机号后4位」） |
| 下拉 ×9 | 快递公司「全部」、组织（当前登录机构）、派件员（空）、操作人（空）、运单状态「全部」、是否拦截件「全部」、特殊件类型（空）、滞留天数「全部」、包裹属性「全部」 |

下拉为 `station-select` / `rocket-select`，**非原生 `<select>`**（Playwright 不能直接用 `select_option`，需点击展开后选节点）。
**默认无任何时间范围限制**，首次进入即展示全量 133965 条。

***

## 6. 交互逻辑（实测）

| 操作 | 结果 |
| ---- | ---- |
| 点击「查询」（筛选全空） | 触发 1 次 `POST query_waybill`，入参 `pageNo=1, pageSize=50, retentionDays=0, siteOrgCode=...`；**URL 不变**；回到第 1 页，`total` 不变 |
| 点击页码「2」 | 触发 1 次 `POST query_waybill`，**仅 `pageNo` 由 1 变 2**；**URL 不变**；返回 50 条新数据 |
| 筛选条件 → 请求字段映射 | **未实测**（未填任何筛选条件）；仅确认空条件只发 4 个基础字段 |

**结论**：状态全部由 SPA 内存维护，**不写入 URL**。采集端不能靠拼接 URL query 复现筛选，必须复现请求体。

***

## 7. 平台原生导出能力（重要）

| 环节 | 实测 |
| ---- | ---- |
| 触发入口 | 运单查询页 `div.table-menu` 内按钮 **「导出Excel」**（`button.station-btn.station-medium.station-btn-primary`） |
| 结果容器 | 作业中心 → 下载任务 `https://mdkd.pinduoduo.com/vw/operationCenter/download` |
| 列表列 | `任务名称` \| `状态` \| `创建时间` \| `失效时间` \| `操作人` \| `操作` |
| 实测行样本 | 任务名「包裹明细导出」，状态「已失效」，创建与失效相隔约 1 天 |
| 下载入口 | 行内 `button.rocket-btn.rocket-btn-link`（已失效行 disabled） |
| 创建入口位置 | **下载任务页本身无新建/申请入口**，导出任务只能从运单查询页的「导出Excel」发起 |
| 分页 | `ul.rocket-pagination.rocket-table-pagination` |
| 实测状态 | 本轮**未点击**「导出Excel」，其导出接口与文件格式**不确定** |

**价值**：这是平台自带的直出通道。若导出结果可解析，采集端可退化为「定期导出 + 解析文件」，**完全不需要触碰 `anti-content` 风控签名**。
**风险**：任务约 1 天失效，需在有效期内下载。

***

## 8. 风控观测记录

本轮全程**未触发熔断**，无验证码/滑块、无 403、无 429、无跳登录、无结构突变、无请求被拒。

观测到的站点风控设施（**只读记录，未触碰、未逆向**）：

| 设施 | 观测 |
| ---- | ---- |
| 请求签名 | `anti-content` 请求头（§3.2） |
| 设备标识 | `etag` / `pdd-id` 请求头 |
| 隐藏帧 | `BODY#watermark` 下 1 个 `x=-10000` 视口外、`src` 为空（`src=null`）的 iframe，疑似指纹/埋点帧，**业务页面无 iframe 嵌套** |
| 风控埋点 | `POST https://xg.pinduoduo.com/xg/pfb/a2` |
| 站点自身埋点报错 | `apm.pinduoduo.com/api/pmm/defined` 返回 `net::ERR_ABORTED`（站点原有行为，与本轮操作无关） |
| 一次「疑似 403」 | 经复核为**误报**——页面文本中的「403」来自某运单号片段，非 HTTP 403 |

**平台既有访问限制声明**：多多买菜服务站管理系统（`ddmc.pinduoduo.com/sms`）自 2022-03-31 起已禁用浏览器访问，强制改用「多多买菜工作台」Windows 客户端。说明平台对浏览器端自动化持明确的限制态度。

***

## 9. 待验证清单（下一阶段）

1. 「导出Excel」点击后的实际行为、导出接口、文件格式（Excel/CSV）、单次导出条数上限、是否有频次限制。
2. 各筛选条件到请求体字段的映射（运单号 / 收件人电话 / 快递公司 / 运单状态 / 五组日期区间 / 滞留天数 / 包裹属性等）。
3. `pageSize` 完整候选值（仅见 50）。
4. Cookie / UA / Referer / Origin 等浏览器自动头（页面内不可见）。
5. `anti-content` 签名生成机制（**仅记录存在，不逆向**）。
6. 「单号拦截」「运营看板」「运单导入」页面的接口与结构。
7. 不同角色账号可见菜单差异。
8. 表格首列是否左固定（DOM 中仅观察到末列 `fix-right`）。

***

## 10. 取数路线结论

| 路线 | 说明 | 对风控的依赖 |
| ---- | ---- | ---- |
| **A. 平台原生导出**（推荐先验证） | 运单查询 → 导出Excel → 下载任务 → 下载解析。采集端 = 定期导出 + 文件解析 | **无**（不触碰 `anti-content`） |
| **B. 官方开放平台 API** | 拼多多开放平台（`open.pinduoduo.com`）订单/物流类 API，需开发者认证 | 无（平台授权通道） |
| C. 复用会话调 `query_waybill` | 直连 `mdkd-api` 分页拉取 | **需 `anti-content` 签名 + `etag`/`pdd-id`**，不稳定 |

**结论**：优先验证 A；A 不可行再评估 B；C 仅在前两者均不可行时讨论，且**不做反检测伪装**。

**合规与边界声明（本采集端的硬约束）**：

1. 仅采集**自有账号、自有驿站**的数据，不得跨机构越权。
2. **限速 + 熔断**：串行请求、请求间隔下限、并发 = 1；命中验证码 / 403 / 429 / 跳登录 / 结构突变即**立即终止并出报告**。
3. **不做反检测伪装**：不轮换 User-Agent、不使用代理/IP 轮换、不注入脚本对抗指纹、不伪造或逆向 `anti-content` 签名。
4. 遵守 robots.txt 与站点爬取延迟声明。
5. 真实业务数据（运单号、收件人、手机号、地址）不落 Git、不外泄，按 [db.md](db.md) 规范脱敏存放。

***

## 11. 附：实测截图

| 区域 | 路径 |
| ---- | ---- |
| 入口落地页（未登录） | `%TEMP%\trae\screenshots\mcmd-home-not-logged-in.png` |
| 登录后工作台 | `%TEMP%\trae\screenshots\mdkd-console-logged-in.png` |
| 运单查询 · 筛选区 | `%TEMP%\trae\screenshots\waybill-query-filter.png` |
| 运单查询 · 表格区 | `%TEMP%\trae\screenshots\waybill-query-table.png` |
| 运单查询 · 分页区 | `%TEMP%\trae\screenshots\waybill-query-pagination.png` |
