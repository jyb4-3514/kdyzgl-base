# 快递驿站智汇系统 · 同步任务自定义配置 · UI/UX 设计规范

> 归属：`hrm-dev/docs/`（本次唯一新增交付物）
> 角色：UI/UX 设计师，**只出规范，不写业务代码、不改源码**
> 前置：`demo-ux-improvement.md` B0/B1/C 章、`demo-ui-redesign.md` 2.x/4.1 章、`demo-milestones.md` D-1~D-10、`src/pc/styles/tokens.scss`

---

## 0. 范围、取证方式与不确定项

### 0.1 范围与硬边界

| 项 | 内容 |
| ---- | ---- |
| 目标 | 把同步任务中**写死的枚举**全部开放为可维护配置：数据源、采集频率、采集时段模板、重试次数、超时时长；并提供配置项增删改查、全局默认 + 驿站覆盖、CSV 导入导出、校验兜底 |
| 生效层级 | 全局默认 + 驿站覆盖（驿站默认继承全局，可按项覆盖） |
| 导入导出格式 | CSV，UTF-8 带 BOM（Excel 可直接打开），CRLF 换行 |
| 端范围 | **仅 PC 端**。移动端采集状态为只读，本次不做配置界面（沿用 `demo-ux-improvement.md` B0.4 分工表） |
| 不做 | 不写 Vue 组件、不改 `syncConfig.js` / `db.js` / 任何源码、不改数据库结构（落位建议仅供后端/Mock 契约对齐，不在本设计实现范围） |

### 0.2 取证方式（结论均可回溯到源码）

| 现状对象 | 位置 | 关键事实 |
| ---- | ---- | ---- |
| 频率硬编码枚举 | [syncConfig.js:17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L17) | `FREQUENCIES = ['HOURLY','EVERY_2H','EVERY_4H','DAILY']` |
| 频率显示字典 | [dict.js:19-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/dict.js#L19-L24) | `COLLECT_FREQUENCY` 与 Mock 逐字一致，**同一份枚举两处维护** |
| 现有校验 | [syncConfig.js:87-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L87-L103) | 频次档位、时段格式、先后、数据源长度、布尔位、开关依赖数据源 |
| 数据源为自由文本 | [syncConfig.js:91](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L91)、[CollectConfigDrawer.vue:177-179](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L177-L179) | `data_source` 1-50 字符任意文本，无受管列表 |
| 时段格式判定 | [syncConfig.js:20-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L20-L25) | `isClock` / `isEndClock`（允许 `24:00`）/ `minutesOf` |
| 配置种子 | [db.js:395-404](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L395-L404) | 8 站各一行，频率取四档、数据源为中文自由文本或 `null` |
| 时段默认 | [db.js:413-414](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L413-L414) | 全站写死 `08:00`–`20:00` |
| 页内 Tab | [index.vue:10-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L10-L13) | 现有 2 个 Tab：批次流水 / 采集配置 |
| 写入角色 | [syncConfig.js:146](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L146) | `PUT` 仅 `ADMIN`；站长只读 |
| CSV 公共工具 | [util.js:175-192](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L175-L192) 、[csv.js:3-23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/utils/csv.js#L3-L23) | BOM + CRLF；转义规则：含 `,` `"` 换行时双引号包裹、内部引号翻倍 |
| 导入行级错误先例 | [employee.js:203-226](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js#L203-L226) | `5003 ROW_ERRORS` + `data.errors[{row,field,message}]` |
| 导入错误码 | [errorCode.js:47-52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/errorCode.js#L47-L52) | `FILE_INVALID 5001` / `ROW_LIMIT 5002` / `ROW_ERRORS 5003` |
| 空间约束 | `demo-ux-improvement.md` B0.1（[第 221-227 行](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ux-improvement.md#L221-L227)） | **单页 Tab 上限 4 个** |
| 组件 7 态 | `demo-ux-improvement.md` B0.2（[第 229-241 行](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ux-improvement.md#L229-L241)） | 默认/加载/空/错误/禁用/无权限/边界 |
| 品牌色边界 | `demo-ux-improvement.md` C1（[第 1420-1434 行](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/demo-ux-improvement.md#L1420-L1434)） | `#1890FF` 仅图标/线/浅底；承白字实底用 `#0958D9`；辅助文字 `#6B7280`；严禁紫色与色板外颜色 |
| Token 全集 | [tokens.scss:10-67](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L10-L67)（L1）、[69-135](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L69-L135)（L2）、[197-218](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss#L197-L218)（L3） | 本设计**零新增 Token**（见 §E.5） |

### 0.3 不确定项与验证方式（反幻觉声明）

| 不确定点 | 处理 |
| ---- | ---- |
| `el-input-number` 的 `precision` / `step-strictly` / `controls-position` 等属性名与行为 | **未在本仓库源码中找到使用先例**。实现前须查 Element Plus 2.9.3 官方文档「InputNumber 属性」表核对，不得凭记忆写属性 |
| `el-upload` 的拖拽上传（`drag`）与 `accept` 取值 | 本仓库仅见既有导入实现（`employee.js` 由页面侧发文件流），未见模板级用法。须核官方文档「Upload 属性」表 |
| `el-radio-group` 的按钮样式分段控件（`el-radio-button`）在 2.9.3 的尺寸/边框 Token 覆盖行为 | 须查官方文档并用实际渲染验证，尤其 `--el-component-size` 是否生效 |
| 「配置项管理」的 Mock 端点是**新增**还是扩展现有 `/sync/configs` | 源码中不存在配置项定义相关路由（[syncConfig.js:142-147](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L142-L147) 仅 4 条）。本文档只给**建议契约**，端点定稿由主智能体与后端对齐 |
| `retry_times` / `timeout_minutes` 的落库字段 | 源码中 `sync_task.retry_count`（[index.vue:74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L74)）是运行时计数而非配置，无对应配置字段。落位建议仅供契约对齐 |

验证方案：以上三条 UI 组件属性，实现前由前端工程师在本地 `hrm-demo` 起服后按官方文档核对一次再落地；不确定则回退到既有已用组件形态（`el-select` / `el-input`）。

---

## A. 配置项管理机制

### A.1 配置项定义模型（ConfigItem）

一个「配置项」= 一条元数据记录，描述「这个可配置项叫什么、值长什么样、怎么校验、谁能覆盖」。

| 字段 | 内部名 | 类型 | 必填 | 取值/约束 | 说明 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 项 Key | `itemKey` | string | 是 | `^[a-z][a-z0-9_]{1,39}$`，全局唯一 | 稳定标识，**不随显示名变化**；导入导出与引用均以此为准 |
| 显示名 | `name` | string | 是 | 1–20 字符 | 界面标签，可改；允许与他项重名 |
| 说明 | `description` | string | 否 | ≤100 字符 | 界面以 Caption 灰字展示 |
| 值类型 | `valueType` | enum | 是 | `SINGLE_SELECT` / `NUMBER` / `TEXT` / `TIME` / `TIME_RANGE` | 决定控件形态与校验器 |
| 是否必填 | `required` | boolean | 是 | — | 必填项在全局默认无值时，驿站进入「未配置」判定链 |
| 默认值 | `defaultValue` | 依类型 | 否 | 必须通过本项校验器 | `required=true` 时禁止为空 |
| 单位 | `unit` | string | 否 | ≤8 字符 | 仅 `NUMBER` 使用（次 / 分钟） |
| 取值约束 | `constraints` | object | 否 | 见 A.1.1 | 依值类型取不同键 |
| 关联选项集 | `optionSetKey` | string | 条件必填 | `SINGLE_SELECT` 必填 | 指向选项集 |
| 生效范围 | `scope` | enum | 是 | `GLOBAL` / `STATION` | `STATION` 才允许按驿站覆盖 |
| 排序 | `sort` | number | 是 | 0–9999 | 列表与表单渲染顺序 |
| 启用 | `enabled` | boolean | 是 | — | 停用项不出现在默认配置与驿站覆盖中，存量值保留可读 |
| 系统内置 | `builtin` | boolean | 是 | — | 内置项**不可删除**，只能停用（防止删掉采集引擎依赖的项） |
| 更新时间 | `updateTime` | string | 否 | — | 只读展示 |

#### A.1.1 值类型与取值约束方式

| 值类型 | 控件形态 | `constraints` 键 | 取值约束方式 |
| ---- | ---- | ---- | ---- |
| `SINGLE_SELECT` | 单选下拉（`el-select`） | 无（由关联选项集表达） | 值必须 **∈ 关联选项集中「启用」选项的 `optionKey` 集合**；选项集为空则该项不可保存 |
| `NUMBER` | 数字输入（`el-input-number`，属性待核） | `min` / `max` / `step` / `integerOnly` / `precision` | 数值落在 `[min,max]`，按 `step` 步进；`integerOnly=true` 时禁止小数；带 `unit` 时界面展示单位后缀 |
| `TEXT` | 文本输入（`el-input`） | `minLen` / `maxLen` / `pattern` / `patternHint` | 去除首尾空白后长度在 `[minLen,maxLen]`；有 `pattern` 时须匹配（`patternHint` 给出人话示例） |
| `TIME` | 时间下拉（自建选项，见 [CollectConfigDrawer.vue:28-34](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L28-L34) 先例） | `min` / `max`（可选） | 格式 `HH:mm`（复用 `isClock` 口径）；可选时界内 |
| `TIME_RANGE` | 起止双下拉 | `allowEnd2400`（默认 true） | 起止均 `HH:mm`，结束允许 `24:00`（复用 [syncConfig.js:21](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L21)）；**开始 < 结束**（复用 `minutesOf` 比较） |

#### A.1.2 本轮落地的 5 个配置项（覆盖用户点名范围）

| `itemKey` | 显示名 | `valueType` | `optionSetKey` | `constraints` | `required` | `scope` | 现有字段落位 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| `data_source` | 数据源 | `SINGLE_SELECT` | `data_source` | — | 是 | `STATION` | 复用 `sync_config.data_source`（改存 `optionKey`） |
| `collect_frequency` | 采集频率 | `SINGLE_SELECT` | `collect_frequency` | — | 是 | `STATION` | 复用 `sync_config.frequency` |
| `time_template` | 采集时段模板 | `SINGLE_SELECT` | `time_template` | — | 否 | `STATION` | 模板解析后落 `collect_start_time` / `collect_end_time` |
| `retry_times` | 重试次数 | `NUMBER` | — | `{min:0,max:10,step:1,integerOnly:true}`，`unit=次` | 是 | `STATION` | **无对应字段**，建议新增 `sync_config.retry_times` |
| `timeout_minutes` | 超时时长 | `NUMBER` | — | `{min:1,max:120,step:1,integerOnly:true}`，`unit=分钟` | 是 | `STATION` | **无对应字段**，建议新增 `sync_config.timeout_minutes` |

> 后两项的落库字段属后端/契约范围，本设计仅登记「需要新增」，不改变数据库结构。UI 侧按「若接口未返回该字段，则整列/整行降级为不可用并给一行说明」处理（见 §E 的边界态）。

### A.2 选项集模型（OptionSet / OptionItem）

单选项型配置项的候选项独立成「选项集」，一个选项集可被多个配置项引用。

**选项集 OptionSet**

| 字段 | 内部名 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| 选项集 Key | `setKey` | string | 是 | `^[a-z][a-z0-9_]{1,39}$`，全局唯一 |
| 显示名 | `name` | string | 是 | 1–20 字符，如「数据源」 |
| 说明 | `description` | string | 否 | ≤100 字符 |
| 系统内置 | `builtin` | boolean | 是 | 内置集不可删，只能停用/改名 |
| 启用 | `enabled` | boolean | 是 | 停用后引用它的配置项不可保存 |

**选项 OptionItem**

| 字段 | 内部名 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| 选项 Key | `optionKey` | string | 是 | **同一选项集内唯一**，稳定不随显示名变 |
| 显示名 | `label` | string | 是 | 1–20 字符，同一集内允许重名（靠 Key 区分） |
| 附加属性 | `extraAttrs` | object | 否 | 结构由所属选项集约定，见 A.2.1 |
| 排序 | `sort` | number | 是 | 0–9999 |
| 启用 | `enabled` | boolean | 是 | 停用选项：不可被新选择，存量值保留可读 |
| 系统内置 | `builtin` | boolean | 是 | 内置选项不可删，只能停用 |
| 来源 | `source` | enum | 是 | `BUILTIN` / `MANUAL` / `MIGRATED`（迁移自动创建，需人工确认） |
| 备注 | `remark` | string | 否 | ≤100 字符 |

#### A.2.1 各选项集的附加属性约定

| 选项集 | 附加属性键 | 类型 | 说明 |
| ---- | ---- | ---- | ---- |
| `collect_frequency` | `intervalMinutes` | number（分钟） | 采集间隔。`EVERY_30M→30`、`EVERY_60M→60`、`EVERY_120M→120`、`EVERY_240M→240`、`EVERY_1440M→1440`；**这是「每 30 分钟」这类自定义档位的落地方式** |
| `time_template` | `startTime` / `endTime` | `HH:mm` | 时段模板，选择后自动填充起止；`endTime` 允许 `24:00` |
| `data_source` | — | — | 无附加属性（纯标签） |

界面表现：附加属性在选项编辑表单中按选项集渲染对应字段（`collect_frequency` 显示「间隔（分钟）」数字输入；`time_template` 显示「开始时间 / 结束时间」两个时间下拉）——**前端按 `extraAttrs` 的键渲染通用表单，不写死业务字段**。

### A.3 机制上如何做到「不硬编码」

核心：**配置项定义、选项集、全局默认值、驿站覆盖值全部是数据（可增删改 + 可导入导出），代码只保留「值类型的校验器」与「渲染控件映射」两种逻辑。**

| 变更动作 | 改造前（现状） | 改造后（本设计） |
| ---- | ---- | ---- |
| **新增一个数据源**（如「中通快递」） | 无受管列表，用户只能手打自由文本；若要变成下拉需同时改 [syncConfig.js:17](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L17) 附近逻辑 + [dict.js:19-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/dict.js#L19-L24) + [CollectConfigDrawer.vue:177-179](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L177-L179) | 在「配置项与选项集」的 `data_source` 选项集新增一行 → **只改数据，不改代码** |
| **改一个频率档位**（新增「每 30 分钟」） | 需改 `FREQUENCIES` 数组 + `COLLECT_FREQUENCY` 字典 + 校验元数据（三处） | 在 `collect_frequency` 选项集新增 `EVERY_30M` 并填 `intervalMinutes=30` → **只改数据** |
| **改一个频率档位显示名**（「每小时」→「每 1 小时」） | 改 `COLLECT_FREQUENCY` 字典并回归所有引用 | 改选项 `label` → **只改数据** |
| **调整某项的取值范围**（重试次数 0-10 → 0-20） | 校验逻辑写死在 `validateConfig` | 改 `retry_times` 的 `constraints.max` → **只改数据** |
| **新增一个配置项**（如「并发批次数」） | 需改 DB 字段 + Mock 校验 + 前端表单 + 表格列（四处） | 在配置项列表新增一行（选值类型、填约束）→ **只改数据**；前端表单/表格按配置项动态渲染 |
| **改全局默认值** | 无「全局默认」概念，只能逐站改 | 「全局默认配置」区改一次，未覆盖的驿站自动生效 |

**诚实边界**：新增配置项后，若要让**采集引擎真正消费**该值（如 `timeout_minutes` 影响任务超时判定），仍需后端实现读取逻辑。本设计保证的是「**UI 与校验层不再硬编码**」，引擎侧消费属后端范围。

### A.4 兼容与迁移（必须的明确方案）

#### A.4.1 `frequency` 四档 → 选项集

**迁移策略：新码规范化 + 旧码兼容读取 + 一次性写回。** 映射关系为「值等价改写」，不改变任何驿站的实际采集间隔。

| 旧值（存量） | 新 `optionKey` | 显示名 | `intervalMinutes` |
| ---- | ---- | ---- | ---- |
| `HOURLY` | `EVERY_60M` | 每小时 | 60 |
| `EVERY_2H` | `EVERY_120M` | 每 2 小时 | 120 |
| `EVERY_4H` | `EVERY_240M` | 每 4 小时 | 240 |
| `DAILY` | `EVERY_1440M` | 每天 | 1440 |

实施三步（缺一不可）：

1. **兼容读取**：选项集内每个新选项登记 `legacyCodes`（如 `EVERY_60M.legacyCodes=['HOURLY']`）。Mock/前端读到旧值时先映射为新码再展示，**保证迁移期间页面不出「—」**。
2. **一次性写回**：迁移脚本把 `sync_config.frequency` 的旧码批量改写为新码（等价改写，幂等）。
3. **移除兼容层**：全量写回并回归通过后，删除 `legacyCodes` 与映射函数（用 `TODO(扩展)` 标注删除时机）。

**为什么不直接沿用旧码当 `optionKey`**：旧四档无法表达「每 30 分钟」这类新增档位；若沿用旧码会导致 `EVERY_30M` 与 `HOURLY` 命名不成体系，后续每加一档都要发明新命名。规范化后用 `EVERY_{n}M` 可无限扩展。

#### A.4.2 自由文本 `dataSource` → 选项集

**存量值**（[db.js:396-403](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L396-L403)）：`多多买菜` / `菜鸟裹裹` / `京东物流`，另有两站为 `null`（未配置）。

| 旧文本 | 新 `optionKey` | `label` | 处理 |
| ---- | ---- | ---- | ---- |
| `多多买菜` | `DUODUOCAI` | 多多买菜 | 内建选项，直接映射 |
| `菜鸟裹裹` | `CAINIAO` | 菜鸟裹裹 | 内建选项，直接映射 |
| `京东物流` | `JD` | 京东物流 | 内建选项，直接映射 |
| `null` | — | — | 保持 `null`（未配置态不变，见 [db.js:390](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L390) 的四态口径） |

**未命中既有选项的历史文本**（未来真实环境必然出现）：

1. 迁移时**自动创建选项**，`source=MIGRATED`、`enabled=true`、`builtin=false`、`remark='由历史配置自动创建'`；
2. 选项集管理页顶部给一条**待确认提示**：「有 N 个历史数据源已自动纳管，请确认显示名与启用状态」，并把这些选项行加 `--state-warning-*` 浅底标记；
3. 管理员确认（改显示名 / 停用 / 合并到既有选项）后标记清除。

**不建议**把未命中值直接置空——那会让存量驿站的采集来源凭空消失（`enabled=1` 且无数据源，与 [syncConfig.js:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L98) 的「启用采集前须先选择数据源」规则冲突，会凭空产生「异常」驿站）。

#### A.4.3 时段默认值

现状全站写死 `08:00`–`20:00`（[db.js:413-414](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/db.js#L413-L414)）。迁移为 `time_template` 内建选项 `WORKDAY`（`08:00`–`20:00`）并设为**全局默认**，存量驿站 `collect_start_time` / `collect_end_time` 原值不变（视为未覆盖、继承全局默认，值恰好相等）。**零风险迁移**。

---

## B. 界面设计

### B.1 入口与信息架构

#### B.1.1 入口位置

新增**第三个页内 Tab「配置管理」**，与现有「批次流水 / 采集配置」并列（[index.vue:10-13](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L10-L13)）。

```
同步任务页
┌ 批次流水 | 采集配置 | 配置管理 ┐  ← 3 个 Tab，满足 B0.1「单页 Tab 上限 4」
```

| 约束 | 落实 |
| ---- | ---- |
| B0.1 单页 Tab 上限 4 | 3 个，未越界；**不新开路由**（`sync` 键已在白名单，[menu.js:58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/config/menu.js#L58)） |
| 写操作仅 ADMIN（[syncConfig.js:146](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L146)） | Tab 用 `v-if="isAdmin"` **不渲染**给站长，避免「点了才知道没权限」（沿用 [CollectConfigTable.vue:101-108](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigTable.vue#L101-L108) 的既有做法） |
| Tab 状态可直达 | Tab 名 `config`，URL `?tab=config`，复用既有 `handleTabChange` 写法（[index.vue:503-514](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L503-L514)） |

#### B.1.2 「配置管理」Tab 内部结构

三个子区域**不用页内子 Tab 套 Tab**，改用**分段控件 + 单内容区**（避免三层导航）：

```
配置管理
├ 工具条
│   [ 配置项与选项集 | 全局默认 | 驿站覆盖 ]        [下载导入模板] [导入配置] [导出配置]
└ 内容区（随分段控件切换，三选一）
```

- 分段控件：`el-radio-group` + `el-radio-button`（属性待核，见 §0.3）；选中态用 `--color-primary-strong`（`#0958D9`）承载白字的实底，符合 C1。
- 工具条右侧三个按钮：`下载导入模板`（default）、`导入配置`（default）、`导出配置`（primary）——导入导出是全区域共用能力，放工具条而非某个子区。
- 三个子区各自独立维护 loading / error / 空态，互不阻塞。

### B.2 区域一：配置项与选项集管理

#### B.2.1 布局（主从）

```
┌──────────────────────────── 左：配置项表（约 62%） ────────────────────────────┐  ┌─ 右：选项集面板（约 38%）─┐
│ 排序 | 项Key | 显示名 | 值类型 | 必填 | 默认值 | 约束/单位 | 生效范围 | 启用 | 操作 │  │ 选中项：数据源(单选)      │
│ ...                                                                            │  │ 排序|选项Key|显示名|附加|启用|操作│
└────────────────────────────────────────────────────────────────────────────────┘  └──────────────────────────┘
```

- 点左侧任一行 → 右侧显示该项详情；**仅 `SINGLE_SELECT` 展示选项集表**，其它类型右栏显示空态提示「该值为数值/文本/时间型，无候选项」（不隐藏右栏，避免布局跳动）。
- 左表右上角「新增配置项」primary 按钮；右栏右上角「新增选项」按钮（仅单选型可见）。

#### B.2.2 配置项表列定义

| 列 | 宽度 | 说明 |
| ---- | ---- | ---- |
| 排序 | 70（居中） | 数字，`tabular-nums` |
| 项 Key | 170 | 等宽字体展示，`.visually-hidden` 不需要；长值 `show-overflow-tooltip` |
| 显示名 | 140 | |
| 值类型 | 120 | 中文标签：单选项 / 数值 / 文本 / 时间 / 时间段 |
| 必填 | 70（居中） | 是 → `StatusTag variant="soft" type="warning"`；否 → `—` |
| 默认值 | 140 | 单选型显示 `label`（非 Key），数值型带单位 |
| 约束/单位 | 160 | 如「0-10 次」「HH:mm」「1-50 字符」 |
| 生效范围 | 100 | `全局` / `可覆盖`（中文标签） |
| 启用 | 80（居中） | `el-switch`，停用时行文字变 `--text-3` |
| 操作 | 160（固定右） | `编辑` / `停用`(启用) / `删除` |

#### B.2.3 选项集面板列定义

| 列 | 说明 |
| ---- | ---- |
| 排序 | 数字 |
| 选项Key | 等宽字体 |
| 显示名 | label；`source=MIGRATED` 时后缀一枚 `StatusTag variant="soft" type="warning"`「待确认」 |
| 附加属性 | 按 A.2.1 渲染为可读文本：「间隔 30 分钟」「08:00 – 20:00」 |
| 启用 | `el-switch` |
| 操作 | `编辑` / `删除`（内置项 `builtin=true` 时删除按钮禁用并给 `title`） |

#### B.2.4 表单字段

- **配置项表单**（抽屉 `ConfigItemDrawer`，宽 `--drawer-w` 520px）：项 Key（新增时可填、编辑时只读，改 Key 视为新项）/ 显示名 / 说明 / 值类型（编辑时只读，改类型会破坏存量值）/ 是否必填 / 默认值（控件随值类型动态）/ 关联选项集（仅单选型）/ 约束（随值类型动态）/ 排序 / 生效范围 / 启用。
- **选项表单**（弹窗或同行内编辑）：选项 Key / 显示名 / 附加属性（随选项集动态）/ 排序 / 启用 / 备注。

### B.3 区域二：全局默认配置

- 表单（`el-form label-width=140px`），**按启用中的配置项动态渲染**，每项：`配置项显示名`(label) + 动态控件 + `必填`标记 + 说明(Caption 灰字) + 右侧「全局默认」徽标。
- 使用 `el-alert type="info"` 置顶说明：「这里的值是所有驿站的默认值；驿站若未单独覆盖，将自动继承此处的设置。」
- 底部：`保存全局默认`（primary，载白字实底用 `--color-primary-strong`）、`恢复为上次保存`（default）。
- 每项右侧给一行小字「当前被 N 个驿站继承 / M 个驿站覆盖」，点击数字可跳转到区域三并定位（帮助管理员理解改动的波及面）。

### B.4 区域三：驿站覆盖

两种视图配合，覆盖「扫读」与「精改」两种诉求：

**主视图：覆盖矩阵表**（`StationOverrideTable`）

| 列 | 说明 |
| ---- | ---- |
| 驿站 | `fixed="left"`，行首 |
| `<每个配置项一列>` | 单元格 = 值 + 来源标记（继承 / 已覆盖）；列头为配置项显示名 |
| 操作 | `配置` → 打开单站覆盖抽屉 |

- 8 站 × 5 配置项 = 40 格，规模可控，无需分页。
- 列头悬浮提示（`title`）：配置项说明 + 全局默认值。
- 数据未返回某配置项（如 `retry_times` 字段缺口）时，该列整体显示「暂不支持」并置灰，不渲染误导性的「继承」标记。

**编辑视图：单站覆盖抽屉**（`StationOverrideDrawer`，宽 `--drawer-w`）

- 抽屉内逐项一行：`配置项名` | 来源标记 | 当前值(可编辑控件) | 操作（`覆盖` / `恢复继承`）。
- 未覆盖项：控件**禁用**并展示全局默认值（灰字），右侧按钮为「覆盖」；点击后控件启用、按钮变「恢复继承」。
- 已覆盖项：控件启用，右侧「恢复继承」为 danger link。
- 底部：`保存`（primary）/ `关闭`。

### B.5 「继承 vs 覆盖」的视觉呈现（本设计重点）

统一到**三重冗余**（颜色 + 文字标签 + 图标），满足 WCAG SC 1.4.1「不只靠颜色」：

| 维度 | 继承（Inherited） | 已覆盖（Overridden） |
| ---- | ---- | ---- |
| 来源标签 | `StatusTag` `variant="outline"`，文案「继承」，色取 `--state-outline-*`（fg=`--c-neutral-500` `#6B7280`，白底 4.83:1） | `StatusTag` `variant="soft"` `type="primary"`，文案「已覆盖」，色取 `--state-primary-*` |
| 值文字颜色 | `--text-3`（`#6B7280`，辅助信息语义） | `--text-1`（`#1F2937`，主信息语义） |
| 单元格底 | `--surface-sub`（`#FAFBFC`） | `--surface-card`（`#FFFFFF`）+ 左侧 2px 竖线 `--color-primary-icon` |
| 值后缀 | 无（或灰字小注「(全局默认)」） | 无 |
| 图标 | 无（描边标签已足够轻） | 无（竖线承载） |

- **2px 竖线**用 `--color-primary-icon`（即 `#1890FF`），符合 C1「1px 强调描边允许用 500 档」的放宽适用（此处 2px 亦为纯装饰性强调，不承载白字）。
- 「恢复为全局默认」操作：抽屉内为 danger link 按钮；矩阵表内不直接提供（避免误触），进入抽屉再操作。
- 恢复时二次确认（仅当覆盖值 ≠ 全局默认值时）：「将「城东驿站」的「采集频率」恢复为全局默认「每小时」，本项当前覆盖值「每 30 分钟」将丢失。」按钮「确认恢复」/「再想想」（沿用 B0.3 文案规范：标题动词短语、正文说清影响与不可逆、按钮具体动词）。

**令牌映射表**

| 元素 | Token |
| ---- | ---- |
| 继承标签 | `--state-outline-bg/fg/border` |
| 覆盖标签 | `--state-primary-bg/fg/border` |
| 继承值文字 | `--text-3` |
| 覆盖值文字 | `--text-1` |
| 继承单元格底 | `--surface-sub` |
| 覆盖竖线 | `--color-primary-icon` |
| 单元格边框 | `--border-line` |
| 表格行高 | `--table-row-h`（44px） |

### B.6 删除的影响面提示（引用完整性前置检查）

删除前**必须先做引用检查**（前端调引用检查接口，或由 Mock 在删除接口内返回影响清单），再展示确认框。

| 删除对象 | 影响判定 | 确认框文案（可直接使用） | 按钮 |
| ---- | ---- | ---- | ---- |
| 配置项（有驿站覆盖值） | 受影响驿站 = 该项在 `sync_config` 存在覆盖值的站 | 标题「删除配置项」；正文「有 3 个驿站正在使用「采集频率」的覆盖值（城东驿站、城西驿站、高新驿站），删除后这些驿站将恢复为继承全局默认，覆盖值将丢失。」 | `确认删除` / `再想想` |
| 配置项（无覆盖值） | 仅全局默认引用 | 标题「删除配置项」；正文「该配置项没有任何驿站覆盖，删除后将从全局默认与所有驿站配置中移除。此操作不可撤销。」 | `确认删除` / `再想想` |
| 配置项（系统内置 `builtin`） | — | 禁用删除按钮，`title`「系统内置配置项不可删除，可停用」 | — |
| 单个选项（被驿站引用） | 受影响驿站 = 覆盖值指向该选项的站 | 标题「删除选项」；正文「有 3 个驿站正在使用「多多买菜」，删除后这些站点的数据源将回退为继承全局默认（当前为「菜鸟裹裹」）。」 | `确认删除` / `再想想` |
| 单个选项（仅被全局默认引用） | — | 标题「删除选项」；正文「该选项是全局默认值，删除后将导致「数据源」缺少全局默认值，未覆盖的驿站会变为「未配置」。建议改为停用而非删除。」（**主按钮改为「改为停用」**） | `改为停用` / `取消` |
| 选项集被多个配置项引用 | 列出引用方 | 正文追加「该选项集同时被「数据源」「备用数据源」2 个配置项引用，删除将一并影响。」 | `确认删除` / `再想想` |

- 提示中的「N 个驿站」超出 3 个时，正文列前 3 个 + 「等 N 个驿站」，并提供「查看受影响驿站」link 展开完整列表（在确认框内以 `el-alert` 或折叠列表呈现）。
- 删除按钮**永远**是 danger 形态；确认框 `type="warning"`（沿用既有 `ElMessageBox.confirm` 用法，[index.vue:474-481](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L474-L481)）。

### B.7 导入 / 导出交互

#### B.7.1 导出

1. 点「导出配置」→ 弹**导出范围对话框**（`ExportScopeDialog`）：

| 选项 | 含义 | 导出记录类型 |
| ---- | ---- | ---- |
| 仅配置项定义 | 配置项 + 选项集（不含任何值） | `ITEM` + `OPTION` |
| 含全局默认（默认选中） | 上一项 + 全局默认值 | `ITEM` + `OPTION` + `GLOBAL` |
| 全部（含驿站覆盖） | 上一项 + 8 个驿站的覆盖值 | 全部四类 |

2. 文件命名规范（沿用既有 `中文名_YYYYMMDD.csv` 惯例，[employee.js:267](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js#L267)、[attendance/index.vue:447](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/attendance/index.vue#L447)）：
   - 仅配置项：`同步配置_配置项_20260919.csv`
   - 含全局默认：`同步配置_含全局默认_20260919.csv`
   - 全部：`同步配置_全部_20260919.csv`
3. 导出实现复用既有工具：[util.js:184-187](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L184-L187) 的 `toCsvBlob`（BOM+CRLF）与 [employee.js:190-191](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js#L190-L191) 的 `csvDisposition`（RFC 5987 中文文件名）。**不新增 CSV 工具**。

#### B.7.2 导入（四步向导，抽屉 `ConfigImportDrawer`，宽 `--drawer-w-lg` 720px）

```
① 上传文件  →  ② 解析预览  →  ③ 选择冲突策略  →  ④ 确认导入  →  结果报告
```

**① 上传**：`el-upload`（`accept=".csv"`，属性待核 §0.3）+ 「下载导入模板」link。文件校验：非 `.csv` / 空文件 → 就地报错，不进入下一步。

**② 解析预览**（本步是全交互核心）：

- 顶部汇总条：`成功解析 N 条 · 失败 M 条 · 共 N+M 行`，用 `aria-live="polite"` 播报。
- 明细表（`el-table`，可滚动，行数上限提示）：列 = `行号` / `记录类型` / `配置项Key` / `选项Key` / `驿站` / `配置值` / `校验结果`。
- 失败行：整行底 `--state-danger-bg`，`校验结果` 列用 `--state-danger-fg` 文字说明原因（见 §C.3 文案），并给 `title` 完整原因。
- 成功行：`校验结果` 显示「通过」（`--state-success-fg`）。
- 四类记录分页签或按「记录类型」列过滤（超出 200 行时提示可用筛选）。
- 存在失败行时，**「下一步」仍可点**，但按钮为 default 而非 primary，并在按钮旁给说明「失败行将被跳过，不影响成功行导入」。

**③ 冲突策略**（三选一，`el-radio-group`）：

| 策略 | 语义 | 适用 |
| ---- | ---- | ---- |
| 覆盖（默认） | 同判定键的已存在记录，用导入值替换（含选项的显示名/附加属性/启用） | 从导出文件改完再导回 |
| 跳过 | 同判定键已存在则保留原值，不导入 | 增量补充，避免误伤 |
| 追加 | 仅对不存在的键新增；对已存在的键**报冲突错误**（不静默改） | 严格新增 |

- 策略区下方给一行预览：「按当前策略，将新增 N 条、更新 M 条、跳过 K 条、冲突 J 条」（随策略实时变化）。
- **追加策略下 J>0 时禁止继续**（按钮禁用 + 说明），因为「追加」的语义就是不改已有数据。

**④ 确认导入**：二次确认框，标题「导入同步配置」，正文「将新增 N 条、更新 M 条、跳过 K 条（失败行 X 条），导入后立即生效。此操作会修改配置项定义与驿站覆盖值。」按钮 `确认导入` / `再想想`。

**结果报告**：成功 → `ElMessage.success('已导入：新增 N 条，更新 M 条，跳过 K 条')`；含失败 → 结果面板列出失败行号与原因，并提供「下载失败明细」link（导出仅含失败行的 CSV，便于修正后再导入）。错误码沿用 [errorCode.js:47-52](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/constants/errorCode.js#L47-L52)：`5001` 文件无效、`5002` 超行数上限（1000 行）、`5003` 行级校验错误。

#### B.7.3 下载导入模板

导出**仅含表头 + 5 条示例行**（示例行为 `ITEM`/`OPTION` 各 1 条，明确标注「示例，可删除」于备注列）——沿用 [employee.js:185](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js#L185)「模板不含真实数据，避免示例被误导入」的先例。文件名 `同步配置_导入模板.csv`。

---

## C. 校验机制

### C.1 逐类型校验规则矩阵

值类型 × 校验点（✓ = 适用，— = 不适用）：

| 校验点 | SINGLE_SELECT | NUMBER | TEXT | TIME | TIME_RANGE |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 必填（`required=true` 时非空） | ✓ | ✓ | ✓ | ✓ | ✓（起止都要有） |
| 取值范围（min/max） | — | ✓ | — | ✓（可选时界） | — |
| 步进 / 精度 | — | ✓（`step`/`precision`） | — | — | — |
| 整数限定 | — | ✓（`integerOnly`） | — | — | — |
| 长度（min/max） | ✓（选项 label 1-20） | — | ✓ | — | — |
| 正则 | — | — | ✓（`pattern`） | — | — |
| 时间格式 | ✓（附加属性 `startTime`/`endTime` 用 `isClock`） | — | — | ✓（`HH:mm`） | ✓（起止 `HH:mm`） |
| 时间先后 | — | — | — | — | ✓（`start < end`，复用 `minutesOf`） |
| 允许 `24:00` | ✓（`endTime`） | — | — | — | ✓（`allowEnd2400`） |
| 选项必须存在且启用 | ✓ | — | — | — | — |
| 唯一性 | ✓（`optionKey` 集内唯一；`label` 允许重名） | — | ✓（`itemKey` 全局唯一） | — | — |
| 跨字段依赖 | ✓（开关开启且数据源必填时不可为空，沿用 [syncConfig.js:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L98) 口径） | — | — | — | — |

补充硬规则：

1. **单选型的值必须落在「启用」选项中**。禁用选项的存量值可只读展示，但保存时若仍指向禁用选项 → 阻断，提示「选项已停用，请重新选择」。
2. **`itemKey` / `optionKey` 不可在导入时静默改写**；Key 冲突走冲突策略处理（§D.5）。
3. **停用配置项**时其值不再参与表单，但**已有驿站值保留**（不清空），以便重新启用后恢复。

### C.2 校验发生的时机（三层分工）

| 层 | 负责 | 典型校验 | 反馈方式 |
| ---- | ---- | ---- | ---- |
| 前端即时 | 单字段格式类，输入过程中/失焦 | 长度、正则、数值范围、时间格式、时间先后、Key 命名规则 | 字段下方红字（`el-form-item` 原生 `error`），**不弹 toast** |
| 前端提交时 | 跨字段与集合级 | 必填、唯一性（同名/同 Key）、选项存在且启用、引用完整性、默认值必填、开关依赖 | 表单整体校验失败 + 滚动定位到首个错误项；引用冲突另行二次确认 |
| 服务端（Mock）兜底 | **全部重跑一遍**（前端不可信） | 与上两层同规则集，另加越权（`roles:['ADMIN']`）、资源存在性、行级导入错误 | 统一 `{code,message,data}`；`400` 带字段级 message；导入用 `5003` + `data.errors[]` |

**服务端必须兜底的原因**：前端校验可被绕过（改包、直接调接口），且「导入的 CSV」本身是外部输入——[employee.js:203-226](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/employee.js#L203-L226) 的导入接口正是在服务端做行级校验并返回 `data.errors[{row,field,message}]`，本设计沿用同一模式。前端在收到 `400`/`5003` 时**把服务端 message 原样落到对应字段**，不二次改写。

### C.3 错误提示文案规范

原则：**说清「哪个字段、什么要求、当前值是什么」**，禁用「输入有误」「格式不正确」这类无信息文案。逐条给出可直接使用的中文文案（`{字段}` 用配置项显示名替换，`{值}` 用当前值替换）：

| 场景 | 文案 |
| ---- | ---- |
| 必填为空 | `请填写「{字段}」` |
| 数值超范围 | `「{字段}」须在 {min}-{max} {unit} 之间，当前为 {值}` |
| 数值非整数 | `「{字段}」须为整数，当前为 {值}` |
| 步进不符 | `「{字段}」须为 {step} 的整数倍，当前为 {值}` |
| 文本过短/过长 | `「{字段}」长度须为 {minLen}-{maxLen} 字符，当前 {len} 字符` |
| 正则不匹配 | `「{字段}」格式须为 {patternHint}，当前为「{值}」` |
| 时间为空 | `请选择「{字段}」` |
| 时间格式非法 | `「{字段}」时间格式须为 HH:mm（如 08:00），当前为「{值}」` |
| 时段先后错误 | `「{字段}」的结束时间须晚于开始时间，当前为 {start} – {end}` |
| 选项不存在 | `「{字段}」选择的选项不存在，请重新选择` |
| 选项已停用 | `「{字段}」选择的「{label}」已停用，请重新选择` |
| 选项集为空 | `「{字段}」暂无可选项，请先在「配置项与选项集」中添加候选项` |
| 选项 Key 重复 | `选项 Key「{optionKey}」在选项集「{setName}」中已存在` |
| 项 Key 重复 | `配置项 Key「{itemKey}」已存在` |
| 项 Key 命名非法 | `配置项 Key 须以小写字母开头，仅含小写字母、数字与下划线，长度 2-40，当前为「{itemKey}」` |
| 单选项值不可保存 | `启用采集前须先选择「数据源」`（沿用 [syncConfig.js:98](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L98) 既有文案，保持口径一致） |
| 整体保存失败 | `同步配置保存失败，请重试`（兜底，前端未拿到 message 时用） |

### C.4 引用完整性

| 场景 | 处理 | 文案 |
| ---- | ---- | ---- |
| 删除**被驿站引用**的配置项 | 允许删除，但**前置影响面提示**（§B.6），删除后受影响驿站该项回退为「继承全局默认」；若全局默认也为空 → 判为「未配置」态 | 见 B.6 |
| 删除**被驿站引用**的选项 | 同上，受影响驿站覆盖值回退为继承全局默认；若全局默认正是该被删选项 → 同时阻断（见下） | 见 B.6 |
| 删除**全局默认正在引用**的选项 | **阻断删除**，引导改为「停用」 | 「该选项是全局默认值，删除后将导致「数据源」缺少全局默认值，未覆盖的驿站会变为「未配置」。建议改为停用而非删除。」 |
| 停用**被引用**的选项 | 允许停用；存量值**保留可读**（表格中该值后追加 `StatusTag variant="outline"`「已停用」）；新选择不可选 | 「该选项已停用，3 个驿站仍在使用，重新启用后这些驿站不受影响」 |
| 停用**被引用**的配置项 | 允许停用；该项从默认配置与覆盖表单中隐藏，存量值保留；表头/说明给出一条汇总提示 | 「「{字段}」已停用，其配置值将被保留但不参与采集」 |
| 导入时引用**不存在的选项** | 行级失败（`5003`），`errors[].message` = `「{字段}」选择的选项「{optionKey}」不存在，请先在选项集中添加` | — |
| 导入时引用**已停用的选项** | 覆盖/追加策略 → 行级失败；跳过策略 → 视为冲突跳过 | `「{字段}」选择的「{label}」已停用，无法导入` |
| 导入时 `optionSetKey` 不存在 | 行级失败 | `「{itemKey}」关联的选项集「{setKey}」不存在` |

---

## D. CSV 模板定义（导入导出契约）

### D.1 列定义表

一张 CSV 表用「记录类型」列区分四类记录，共 **16 列**。空单元格为「不适用该记录类型」。

| # | 列名 | 必填 | 取值示例 | 说明 | 内部字段 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | 记录类型 | 是 | `ITEM` / `OPTION` / `GLOBAL` / `STATION` | 决定本行语义（大写枚举） | — |
| 2 | 配置项Key | 是 | `collect_frequency` | 四类记录都必须填，作为挂靠主键 | `itemKey` |
| 3 | 配置项名称 | 仅 ITEM | `采集频率` | 显示名，1-20 字符 | `name` |
| 4 | 值类型 | 仅 ITEM | `SINGLE_SELECT` / `NUMBER` / `TEXT` / `TIME` / `TIME_RANGE` | 大写枚举 | `valueType` |
| 5 | 是否必填 | 仅 ITEM | `是` / `否` | 中文布尔 | `required` |
| 6 | 默认值 | 否（ITEM 用） | `EVERY_60M` / `3` | 单选型填 `optionKey`；数值填数字 | `defaultValue` |
| 7 | 单位 | 否（ITEM 用） | `次` / `分钟` | 仅数值型 | `unit` |
| 8 | 取值范围或正则 | 否（ITEM 用） | `0-10 的整数` / `1-50 字符` | 人可读的约束描述（见注 1） | `constraints` |
| 9 | 排序 | 仅 ITEM | `20` | 0-9999 整数 | `sort` |
| 10 | 是否启用 | 仅 ITEM/OPTION | `是` / `否` | 中文布尔 | `enabled` |
| 11 | 选项Key | 仅 OPTION | `EVERY_60M` | 选项稳定标识 | `optionKey` |
| 12 | 选项显示名 | 仅 OPTION | `每小时` | 1-20 字符 | `label` |
| 13 | 附加属性 | 否（OPTION 用） | `intervalMinutes=60` / `startTime=08:00;endTime=20:00` | `键=值` 用 `;` 分隔（见注 2） | `extraAttrs` |
| 14 | 驿站 | 仅 STATION | `城东驿站` | 按**驿站名称**精确匹配 | `stationName` |
| 15 | 配置值 | 仅 GLOBAL/STATION | `EVERY_30M` / `5` | 全局默认值 / 驿站覆盖值 | `value` |
| 16 | 备注 | 否 | `该站营业时间长` | ≤100 字符 | `remark` |

> 注 1：「取值范围或正则」是**面向人的可读描述**，导入时按值类型解析：
> - `NUMBER`：`{min}-{max} 的整数` 或 `{min}-{max}`；解析失败 → 行级失败
> - `TEXT`：`{n} 字符` / `{min}-{max} 字符`；正则另列（本期 CSV 不表达正则，`pattern` 仅支持界面维护）——**登记为已知限制**
> - `TIME` / `TIME_RANGE` / `SINGLE_SELECT`：留空
>
> 注 2：附加属性的键名由所属选项集约定（§A.2.1）。导入时**不认识的键一律忽略并给行级警告**，不阻断（向前兼容）。

### D.2 如何用一张表表达四类信息与层级关系

层级链路：`配置项(ITEM)` ← `选项(OPTION)` / `全局默认(GLOBAL)` / `驿站覆盖(STATION)`，全部通过第 2 列 `配置项Key` 挂靠；`STATION` 再叠加第 14 列 `驿站`。

| 记录类型 | 定位 | 复用列 |
| ---- | ---- | ---- |
| `ITEM` | 配置项定义（元数据） | 3-10、16 |
| `OPTION` | 某配置项的候选项 | 11-13、10、16 |
| `GLOBAL` | 某配置项的全局默认值 | 15、16 |
| `STATION` | 某驿站对某配置项的覆盖值 | 14、15、16 |

**结论：一张表足够，不需要多张表/多文件。** 代价是每行有较多空列，但换来「一个文件即一份完整配置快照」，可直接拿去二次编辑再导回。**导出即导入的闭环**：导出「全部」得到的文件，改完再导入即等价于一次批量配置更新。

### D.3 字符编码与转义规则

| 项 | 规则 | 依据 |
| ---- | ---- | ---- |
| 编码 | UTF-8，**带 BOM（`\ufeff`）** | [util.js:184-187](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L184-L187)、[csv.js:15](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/utils/csv.js#L15) |
| 换行 | `CRLF`（`\r\n`） | 同上 |
| 分隔符 | 逗号 `,` | 同上 |
| 引号转义 | 单元格含 `,`、`"`、换行时，用双引号包裹整个单元格；内部 `"` 翻倍为 `""` | [util.js:178-181](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L178-L181)、[csv.js:3-6](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/utils/csv.js#L3-L6) |
| MIME | `text/csv;charset=utf-8` | [util.js:175](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L175) |
| 文件名响应头 | `attachment; filename*=UTF-8''{encodeURIComponent(name)}` | [util.js:190-192](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/util.js#L190-L192) |

**导入端须兼容**：不带 BOM 的 UTF-8、单元格不必要地加了引号、`\n` 与 `\r\n` 混用。解析器读到 BOM 时忽略，不当作内容。

### D.4 完整示例 CSV（可粘贴使用）

> 16 列，与 D.1 列定义严格一一对应；`附加属性` 用 `;` 分隔，`备注` 已避免逗号故未加引号。

```csv
记录类型,配置项Key,配置项名称,值类型,是否必填,默认值,单位,取值范围或正则,排序,是否启用,选项Key,选项显示名,附加属性,驿站,配置值,备注
ITEM,data_source,数据源,SINGLE_SELECT,是,,,,10,是,,,,,,包裹采集的数据来源
ITEM,collect_frequency,采集频率,SINGLE_SELECT,是,EVERY_60M,,,20,是,,,,,,两次采集的最小间隔
ITEM,time_template,采集时段模板,SINGLE_SELECT,否,WORKDAY,,,30,是,,,,,,预置采集时段模板
ITEM,retry_times,重试次数,NUMBER,是,3,次,0-10 的整数,40,是,,,,,,批次失败后的最大自动重试次数
ITEM,timeout_minutes,超时时长,NUMBER,是,30,分钟,1-120 的整数,50,是,,,,,,单个批次超过该时长判定为超时
OPTION,data_source,,,,,,,,,DUODUOCAI,多多买菜,,,,
OPTION,data_source,,,,,,,,,CAINIAO,菜鸟裹裹,,,,
OPTION,data_source,,,,,,,,,JD,京东物流,,,,
OPTION,data_source,,,,,,,,,SF,顺丰速运,,,,
OPTION,collect_frequency,,,,,,,,,EVERY_30M,每 30 分钟,intervalMinutes=30,,,由管理员新增
OPTION,collect_frequency,,,,,,,,,EVERY_60M,每小时,intervalMinutes=60,,,
OPTION,collect_frequency,,,,,,,,,EVERY_120M,每 2 小时,intervalMinutes=120,,,
OPTION,collect_frequency,,,,,,,,,EVERY_240M,每 4 小时,intervalMinutes=240,,,
OPTION,collect_frequency,,,,,,,,,EVERY_1440M,每天,intervalMinutes=1440,,,
OPTION,time_template,,,,,,,,,WORKDAY,营业时段 08:00-20:00,startTime=08:00;endTime=20:00,,,
OPTION,time_template,,,,,,,,,ALLDAY,全天 08:00-24:00,startTime=08:00;endTime=24:00,,,
GLOBAL,data_source,,,,,,,,,,,,,DUODUOCAI,全局默认数据源
GLOBAL,collect_frequency,,,,,,,,,,,,,EVERY_60M,全局默认采集频率
GLOBAL,time_template,,,,,,,,,,,,,WORKDAY,全局默认时段模板
GLOBAL,retry_times,,,,,,,,,,,,,3,全局默认重试次数
GLOBAL,timeout_minutes,,,,,,,,,,,,,30,全局默认超时时长
STATION,collect_frequency,,,,,,,,,,,,城东驿站,EVERY_30M,该站营业时间长
STATION,retry_times,,,,,,,,,,,,城西驿站,5,该站网络不稳定
```

### D.5 冲突判定键

| 记录类型 | 判定键 | 说明 |
| ---- | ---- | ---- |
| `ITEM` | **配置项Key** | Key 相同即为同一配置项 |
| `OPTION` | **配置项Key + 选项Key**（复合） | 跨选项集不冲突；同一集内 Key 唯一 |
| `GLOBAL` | **配置项Key** | 每项全局默认唯一 |
| `STATION` | **驿站 + 配置项Key**（复合） | 驿站按**名称**精确匹配 |

重名与匹配规则：

1. **配置项名称可重名**，但 `配置项Key` 必须唯一；导入时若 Key 不存在但名称与既有项相同 → 允许新增（视为不同项），并在预览中给一条**警告**而非错误：「名称「数据源」与既有配置项重复，将作为新配置项导入」。
2. **选项显示名（label）在同一选项集内可重名**（靠 Key 区分）；若导入的 label 与既有选项相同但 Key 不同 → 允许，同样给警告。
3. **驿站名匹配不到** → 行级失败：`第 {row} 行：驿站「{name}」不存在，请核对名称`。
4. **同一文件内 Key 自冲突**（如两个 `ITEM` 行同为 `retry_times`）→ 后出现的行级失败：`第 {row} 行：配置项 Key「retry_times」在本文件中重复出现（第 {firstRow} 行已定义）`。
5. 大小写：Key 一律**区分大小写且要求大写枚举值**（`ITEM`/`OPTION`/`GLOBAL`/`STATION` 与值类型枚举），写成小写 → 行级失败并给出正确写法。

---

## E. 组件规范增量（Atomic Design）

> 通用要求（对以下全部组件生效）：`<style>` **不得出现十六进制色值**；交互元素热区 ≥44×44px（PC 表格内联按钮按 SC 2.5.8 的 24px 判定，沿用既有惯例）；焦点环复用 [element-overrides.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/element-overrides.scss) 既有统一实现。

### E.1 新增组件

| 层级 | 组件 | Anatomy | Variants | 7 态 | Token 映射 | 无障碍 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| Atom | `SourceBadge`（继承/覆盖徽标） | 复用一个 `StatusTag` | `inherit`（outline「继承」）/ `override`（soft primary「已覆盖」） | 由值决定，自身无独立态 | `--state-outline-*` / `--state-primary-*` | **文字表达语义**，不靠颜色单通道；`title` 补充「继承自全局默认」 |
| Atom | `ConfigValueField`（动态值控件） | label + 控件（下拉/数字/文本/时间/时间段）+ 单位后缀 + 错误位 | 按 5 种值类型各一形态；`readonly` | 默认 / 加载（骨架）/ 禁用（继承态灰显）/ 错误（红字）/ 边界（超长截断） | `--border-control`、`--text-3`、`--state-danger-fg` | 控件用 `el-form-item` 原生 `label`/`role`；时间下拉沿用既有自建选项的 `aria-label` 先例（[CollectConfigDrawer.vue:188-193](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L188-L193)） |
| Molecule | `ConfigItemTable` | `el-table` + 操作列 | 可写 / 只读 | 全 7 态 | `--table-row-h`、`--state-warning-*`（必填标记） | 表头 `scope`；行选择用 `row-class-name` 高亮而非仅颜色；键盘可达 |
| Molecule | `OptionSetPanel` | 标题 + `el-table` + 新增按钮 | 单选型 / 非单选型（空态提示） | 全 7 态 | `--surface-card`、`--border-line` | 空态用 `StateBlock variant="empty"`；新增按钮 `aria-label` |
| Molecule | `ConfigItemDrawer` | `el-drawer`(`--drawer-w`) + `el-form` | 新增 / 编辑（Key 与值类型锁） | 全 7 态 | `--drawer-w`、`--border-control` | `el-drawer` 原生焦点陷阱 + Esc 关闭；打开时焦点落首个字段；错误字段 `aria-invalid` |
| Molecule | `ExportScopeDialog` | `el-dialog` + `el-radio-group` | 三档范围 | 默认 / 加载（导出中）/ 禁用 / 错误 | 复用 | 单选组键盘可达；导出中按钮 `loading` 且禁点 |
| Molecule | `InheritToggleCell`（覆盖单元格） | 值 + `SourceBadge` + 竖线 | `inherit` / `override` | 默认 / 禁用（无写权限）/ 边界（值超长 tooltip） | `--surface-sub` / `--color-primary` / `--text-3` | 单元格若可点，须为 `<button>` 或有 `role="button" tabindex="0"` |
| Organism | `GlobalDefaultForm` | 顶 `el-alert` + 动态表单项 + 底部操作 | — | 全 7 态 | `--color-primary-strong`（主按钮实底）、`--text-3` | 每项 `label` 关联控件；保存后 `aria-live="polite"` 播报成功 |
| Organism | `StationOverrideTable` | 矩阵表（驿站 × 配置项） | 数据完整 / 部分配置项缺失（列置灰「暂不支持」） | 全 7 态 | `--table-row-h`、`--surface-sub` | 表头双层 `scope`（行/列）；单元格可读文本 `aria-label`（如「城东驿站 采集频率 已覆盖 每 30 分钟」） |
| Organism | `StationOverrideDrawer` | 逐项行 + 覆盖/恢复按钮 | 可写 / 只读 | 全 7 态 | `--drawer-w` | 恢复继承弹确认框；按钮文案明确「覆盖」「恢复继承」 |
| Organism | `ConfigImportDrawer` | 四步向导（步骤条 + 内容区 + 底部操作） | 四步各一形态；策略三分支 | 全 7 态 | `--drawer-w-lg`（720px，窄屏 `min(…,92vw)`）、`--state-danger-bg`（失败行） | 预览汇总 `aria-live="polite"`；失败行含文字原因；步骤条 `aria-current="step"` |

### E.2 改造组件

| 组件 | 改什么 | 保持不变 |
| ---- | ---- | ---- |
| [CollectConfigDrawer.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue) | ① 数据源：`el-input` 自由文本（[:177-179](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L177-L179)）→ `el-select`，选项来自 `data_source` 选项集；② 采集频次：从硬编码 `COLLECT_FREQUENCY`（[:180-184](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigDrawer.vue#L180-L184)）→ 动态选项集；③ 采集时段：加「时段模板」下拉（选模板自动填起止，仍允许逐格调整）；④ 新增「重试次数」「超时时长」两项（数值型）；⑤ 每项右侧显示来源标记（继承/已覆盖） | 抽屉宽度、`label-width=100px`、未配置 6002 空态、`status=0` 只读、关闭开关二次确认、字段级红字报错（[syncConfig.js:87-103](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/mock/routes/syncConfig.js#L87-L103) 的 message 原样落地） |
| [CollectConfigTable.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigTable.vue) | ① 新增「重试」「超时」两列（字段缺失时该列显示「—」并给 `title`「该配置项尚未启用」）；② 数据源/频次列改读选项集 label（旧值经 `legacyCodes` 映射后展示，不出现裸 `HOURLY`） | 排序权重、`v-loading` 表头保留、只读角色隐藏操作列（[:101-108](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/components/CollectConfigTable.vue#L101-L108)）、`show-overflow-tooltip` |
| [sync/index.vue](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue) | 新增第三个 `el-tab-pane name="config"`（`v-if="isAdmin"`）与 `?tab=config` URL 写回 | 现有两个 Tab、批次流水逻辑、`drawerSize` 窄屏退化（[:304](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/views/sync/index.vue#L304)） |

### E.3 复用不改造

`PageHeader`、`StateBlock`、`StatusTag`、`MetricCard`、`el-table` / `el-drawer` / `el-form` / `el-alert` / `el-descriptions` / `el-pagination` 全部复用，不新增样式分支。

### E.4 状态覆盖自查（对照 B0.2 表）

| 组件 | 加载 | 空 | 错误 | 禁用 | 无权限 | 边界 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| `ConfigItemTable` | 表头 + `v-loading` | 无配置项时「暂无配置项，点击右上角新增」 | `StateBlock error` + 重试 | 停用项行灰字 | 非 ADMIN 不渲染该 Tab | 项 Key/label 超长 tooltip |
| `StationOverrideTable` | 表格 `v-loading` | 无驿站时空态 | 错误态 + 重试 | 无写权限时单元格只读 | 站长不渲染 | 配置项字段缺失列置灰；值超长 tooltip |
| `ConfigImportDrawer` | 解析中骨架/进度 | 文件为空 → 「文件中没有可导入的数据行」 | 解析失败 → 错误态 + 重新上传 | 追加策略下冲突时按钮禁用 | — | >1000 行 → `5002` 提示；超长单元格 |
| `GlobalDefaultForm` | 表单骨架 | 无启用配置项时「暂无可配置项」 | 保存失败保留已填值 | 无写权限只读 | — | 数值极值按 min/max 夹取提示 |

### E.5 Token 增量

**新增 Token：无。**

理由：继承/覆盖标记复用 `--state-outline-*` 与 `--state-primary-*`；覆盖竖线与主按钮实底用 `--color-primary` / `--color-primary-strong`；禁用/辅助文字用 `--text-3`；单元格底用 `--surface-sub` / `--surface-card`；导入预览的成败行底用 `--state-danger-bg` / `--state-success-bg`；宽抽屉复用既有 `--drawer-w-lg`。全部落在 [tokens.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/styles/tokens.scss) 既有 L1/L2/L3 内，符合 C1「严禁引入色板外颜色」与 C3-5「能靠既有变量组合表达的一律不加」。

---

## F. 无障碍与自检

### F.1 表格类界面

| 要求 | 落实 |
| ---- | ---- |
| 语义化 | `el-table` 原生渲染 `<table>/<thead>/<th>`；表头列加 `scope="col"`；配置项表首列为行头时用行内 `<th scope="row">` 语义（`el-table-column` 默认 `td`，如需行头语义需实现时确认可行方案，**不确定项**） |
| 键盘可达 | 表格内操作按钮为原生 `<button>`（`el-button` 已是），`tabindex` 可达；覆盖单元格可点时必须 `role="button" tabindex="0"` + Enter/Space 触发 |
| 读屏播报 | 导入预览汇总 `aria-live="polite"`（「成功解析 18 条，失败 2 条，共 20 行」）；导入结果 `aria-live="polite"`；保存/删除成功用 `ElMessage`（Element 自带 `role="alert"`） |
| 状态不只靠颜色 | 继承/覆盖用「文字标签 + 值文字深浅」双通道；必填用「是」文字；失败行除底色外必带原因文字 |
| 焦点可见 | 复用 `element-overrides.scss` 既有统一焦点环；确认表格容器 `overflow` 未裁掉内联按钮焦点环（**实现后走查**） |
| 对比度 | 继承标签 fg=`--c-neutral-500` `#6B7280`，白底 **4.83:1** ≥ 4.5:1；覆盖标签 fg=`--c-blue-700` `#0958D9`，浅底 `--c-blue-50` 上对比度满足；正文用 `--text-1`/`--text-2`（既有已验收档位）；**不新引入任何临界色** |

### F.2 交付前自检清单

| 检查项 | 结果 |
| ---- | ---- |
| 设计方向有业务依据、非 AI 默认风（无紫色、无 Inter+紫渐变+大圆角套路） | ✅ 全部沿用蓝（信任/效率）/ 橙（物流）语义 + 深蓝灰，落点见 C1 与 tokens.scss |
| 3 层 Design Tokens 结构完整、组件正确映射 | ✅ §E 逐组件给出 Token 映射，全部指向既有 L1/L2/L3 |
| 全部组件 7 态无遗漏 | ✅ §E.4 逐组件对照 B0.2 七态表 |
| **无十六进制色值** | ✅ 本设计所有色值均以 `var(--…)` 形式给出，无裸 `#RRGGBB` 落入业务 `<style>` |
| **未误用品牌色**（`#1890FF` 不承载白字、不用于 ≤14px 文字） | ✅ 承白字实底统一用 `--color-primary-strong`(`#0958D9`)；`--color-primary-icon`(`#1890FF`) 仅用于 2px 装饰竖线与图标；辅助文字用 `--text-3`(`#6B7280`) |
| 触控目标 ≥44×44px | ✅ 表格行高 `--table-row-h`(44px)；PC 内联按钮按 SC 2.5.8 的 24px 判定（既有惯例） |
| 颜色对比度 ≥4.5:1（WCAG AA） | ✅ 见 F.1；无新引入色值，沿用已验收档位 |
| 响应式断点覆盖 H5 / 平板 / 桌面 | ✅ 见 F.3 |
| 键盘导航可用 | ✅ 全组件基于 Element 原生组件 + 明确的可点单元格语义要求 |
| 「先设计后实现」链路完整（设计 → 主智能体审核 → 前端实现 → 测试验收） | ⏳ 待主智能体 Review |

### F.3 响应式行为

| 断点 | 行为 |
| ---- | ---- |
| lg ≥1200 | 主从布局左右并排；矩阵表全列展示；导入抽屉 `--drawer-w-lg` 720px |
| md 992–1200 | 侧边栏自动折叠（[layout/index.vue:99-105](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/layout/index.vue#L99-L105)）；主从改为**上下堆叠**（配置项表在上、选项集面板在下）；抽屉 `min(720px, 92vw)`；矩阵表横向滚动 |
| sm 768–992 | 同上；分段控件换行；配置项表隐藏「项Key」「排序」列（进详情可见）；导入预览表横向滚动 |
| xs <768 | PC 页面非目标形态（沿用 C5），仅保证不横向溢出整页；**不做移动端配置界面**（本次只改 PC） |

---

## 附：本次交付范围声明

- 本文档仅为**设计规范**，未新增/修改任何源码文件。
- 建议契约（端点、字段）为设计输入，最终以主智能体与后端对齐为准。
- 待主智能体 Review 通过后，交前端工程师按 §B/§E 落地，测试工程师按 §C/§F 验收。
