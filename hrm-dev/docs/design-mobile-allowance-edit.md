# 移动端「调薪调整」津贴区 · 交互与视觉规范

> 版本 v1.0 | 2026-09-25 | 作者：UI/UX 设计师 `express-station-ui-ux-designer`
> 交付物类型：**可实施的设计规范**（Token 冻结 → 交前端工程师实现；本文件不含实现代码）
> 落点：移动端 `hrm-dev/hrm-demo/src/mobile/modules/boss/views/hrDetail.vue` 的「津贴」区域
> 设计依据：`shared/styles/tokens.base.scss`、`mobile/styles/tokens.scss`、`mobile/styles/mobile.scss`、`demo-ui-redesign.md`、`demo-boss-ui-spec.md`（§3 一致性 / §5.1 状态 / §5.2 静默失败 / §6.3 只读态 / §6.5 禁用 vs 隐藏 / §7.4 触控）
> PC 参照：`pc/views/hr/components/SalaryEditorDrawer.vue`（同能力，数据口径与校验的唯一基准）

## 0. 结论摘要

| 项 | 结论 |
| --- | --- |
| 组件选型 | `van-field`（双列并排）+ `van-button`（图标删除 / 添加），**不新增自定义组件** |
| 每行结构 | 3 列网格：名称 `minmax(0,3fr)` / 金额 `minmax(0,2fr)` / 删除 `var(--touch-min)` |
| 行数上限 | **不设上限**（与 PC 及契约一致，契约无条数约束）；明细区**不内滚**，随页面纵向滚动 |
| 删除确认 | **需要 1 次确认**；`name` 空且金额空的行**豁免**（无数据丢失，直接删） |
| 合计联动 | **不 debounce**，`computed` 同步求和；按钮文案随 `diff` 实时切换但不改尺寸 |
| 新增 Token | **0 条**（全部复用既有 Token；占位符对比度属全局既有问题，见 §10） |
| 契约 | **无变更**；沿用 `PUT /hr/salary-structures/{employeeId}`，payload 增加 `allowances`（PC 已在用） |

---

## 1. 布局方案（窄屏 320–480）

### 1.1 位置与分组（不挤压既有字段层级）

沿用现有区块顺序，**原位替换**第 212 行的 `van-cell 津贴合计`：

```
.card（既有卡片，不新开卡）
├── van-field  基本工资
├── van-field  岗位工资
├── van-field  绩效基数
├── 津贴子区块 ← 本规范
│   ├── van-cell  title=津贴合计  value=明细实时合计   ← 行头（保留原位与原 label）
│   ├── .allowance-rows  role=group   ← 明细行（0..N）
│   └── van-button 添加津贴
├── van-field  生效日期
├── van-field  调整原因
├── 调整后合计 + BossMetricDelta
└── 提交按钮
```

**禁止**：为津贴另开一张 `.card`（`demo-boss-ui-spec.md` §3.2 明文禁区「卡片套卡片」，内层一律改浅底或纯行）。津贴与三项工资**同在一个卡片内、同一左边界**，靠行分隔线区分行，不靠缩进或底色另立层级。

**明细区不做折叠**：折叠会把「参与合计的金额」藏起来，与页面既有设计理由（第 14–15 行注释：当前构成 / 调整后合计 / 留痕三件事同时可见）冲突。

### 1.2 行结构（组件级）

| 列 | 组件 | 宽度 | 关键属性 |
| --- | --- | --- | --- |
| 1 名称 | `van-field` | `minmax(0, 3fr)` | 无 `label`、`:border="false"`、`maxlength="20"`、`placeholder="津贴名称"` |
| 2 金额 | `van-field` | `minmax(0, 2fr)` | 无 `label`、`:border="false"`、`type="number"`、`inputmode="numeric"`、`input-align="right"`、`placeholder="金额"` |
| 3 删除 | `van-button` | `var(--touch-min)` = 44px | `size="small"`、`icon="cross"`、`plain`、`type="danger"`、`aria-label`（见 §5.4） |

- 容器：`.allowance-row { display:grid; grid-template-columns: minmax(0,3fr) minmax(0,2fr) var(--touch-min); align-items:center; gap: var(--sp-2); min-height: var(--row-h-1) }`（`--row-h-1` = 48px）。
- 行分隔：`.allowance-row + .allowance-row { border-top: 1px solid var(--border-line) }`；**首行不画线**（行头 `van-cell` 自带底线，避免双线）。
- 列内 `van-field` 需**收掉自身水平内边距**（`.allowance-row .van-field { padding: 0; min-height: var(--touch-min) }`）——否则两列各带 16px 内边距，在 320px 下两列实际可输区不足 44px。这是本方案唯一的 Vant 覆盖点，必须写明理由（`demo-boss-ui-spec.md` §3.1 允许覆盖 `--van-*`，禁止的是十六进制硬编码）。

### 1.3 溢出与宽度核算（不横向滚动）

`html/body/#app` 已 `overflow-x:hidden`（`mobile.scss:17,30`），任何超宽都会**裁切**而不是出滚动条——因此必须事前算清：

| 视口 | 内容宽 = 视口 − 页面 12×2 − 卡片 16×2 | 扣除 删除 44 + 间隙 8×2 | 名称列 (3fr) | 金额列 (2fr) |
| --- | --- | --- | --- | --- |
| 375px | 319px | 259px | ≈155px | ≈104px |
| 320px | 264px | 204px | ≈122px | ≈82px |

结论：320px 下两列仍 ≥82px（可容 5 位数字 + 千分位不换行），**最窄档亦不横向溢出**。名称列 `min-width:0` + 单行省略，超 20 字由 `maxlength` 从源头挡住。

### 1.4 行高与间距 Token 表（全部为既有 Token）

| 位置 | Token | 值 |
| --- | --- | --- |
| 明细行最小高 | `--row-h-1` | 48px |
| 单列输入最小高 / 删除按钮边长 | `--touch-min` | 44px |
| 列间距、行内间距 | `--sp-2` | 8px |
| 明细区与行头 / 添加按钮间距 | `--sp-2` | 8px |
| 空态提示与添加按钮间距 | `--sp-3`（`.tip` 既有 margin-top） | 12px |
| 分隔线 | `--border-line` | neutral-200 |
| 圆角（按钮） | `--r-sm` | 6px |
| 数字对齐 | `.tabular-nums` 工具类 | — |

---

## 2. 交互设计

### 2.1 添加

- 入口：明细行之下「添加津贴」`van-button`（`size="small"`、`icon="plus"`、`plain`、`type="primary"`、`min-height: var(--touch-min)`）。
- 行为：在数组末尾 `push` 一行，**该行自动聚焦名称输入**（一次点击即可开始输入，避免二次点击），页面滚动到该行（`scrollIntoView({ block:'nearest' })`，与 `prefers-reduced-motion` 无冲突）。
- 新增行初值：`{ key:'', name:'', amount:'' }` —— 金额用**空串**而非 PC 的 `0`，以 `placeholder="金额"` 引导输入。**此项登记为「允许差异 · 交互细节」**；提交映射 `amount: Number(item.amount || 0)`，落库口径与 PC 完全一致（`Number('') === 0`）。
- 添加动作本身**不弹确认**（新增无数据丢失）。

### 2.2 编辑

- 行内直接编辑，**不弹层**：窄屏下为单字段弹层会遮住上下文（用户需同时看到合计变化）。
- 名称：`maxlength="20"`（与 PC 及契约 `textLen(name, 1, 20)` 一致），超出无法继续输入，不产生错误态。
- 金额：`type="number"` + `inputmode="numeric"`。

### 2.3 删除

**结论：需要 1 次确认，但空行豁免。**

| 行内容 | 处理 | 判定理由 |
| --- | --- | --- |
| `name` 非空 或 金额非空/非 0 | 弹 `bossConfirm` 确认 | 命中 `demo-boss-ui-spec.md` §5.1 危险操作判据「**覆盖已有数据**」：删除即丢弃已输入的项名与金额；若该项来自当前档案（带 `key`），误删后须重填才能还原 |
| `name` 为空 且金额为空/0 | 直接删除，不弹 | 无数据丢失，弹窗即「高频小范围操作也弹」的反模式（§5.1 禁止） |

- 实现：`bossConfirm({ action:'移除津贴项', target:`「${item.name}」`, impact:'该项不再计入调整后合计', confirmText:'删除该津贴' })`（`bossConfirm` 在 `modules/boss/components/bossConfirm.js`，本页同目录 `../components/bossConfirm.js`）。
- **不置 `irreversible: true`**：本轮改动在提交前仍是本地态、可撤销；写入不可撤回的是「提交」这个动作（已由底部降薪确认覆盖）。虚张声势的不可撤销声明会稀释真正不可逆提示的可信度。
- 确认通过后 `splice(index, 1)`；不弹成功 Toast（行消失即反馈，`§5.2` 只禁写操作无反馈，本地行编辑不在其列）。

### 2.4 空态

- 条件：数据已加载且 `form.allowances.length === 0`。
- 表现：明细区位置显示一行 `.tip` caption（不占空卡、不放插图），「添加津贴」按钮保持可用。

### 2.5 合计联动（实时，不防抖）

| 项 | 规范 |
| --- | --- |
| 行头「津贴合计」 | 改为由 `form.allowances` 求和派生（`sum(Number(amount \|\| 0))`），**不再取** `salary.current.allowancesTotal`（否则编辑不联动） |
| 「调整后合计」`nextTotal` | 三项工资 + **派生津贴合计** |
| 底部差额 `diff` | `nextTotal − currentTotal`，沿用 `BossMetricDelta mode="money"`；`diff === 0` 渲染「与当前持平」（既有逻辑保留） |
| 是否 debounce | **不防抖**。求和为本地 `computed`、O(n)、n≤10，键盘每击一次即更新；加延迟会让「边输边看合计」失去意义 |
| 抖动规避 | ① 合计与差额行加 `.tabular-nums`（数字等宽，宽度不跳）；② 徽标沿用 `v-if="diff"`，零差额不渲染空白徽标（既有）；③ 提交按钮文案随 `diff` 切换（「保存薪资」↔「提交降薪调整」）时**不改按钮尺寸**（`block` + `min-height:44px` 固定），文案长度变化不引起布局位移 |
| 中间态 | 允许输入过程中合计跳变（如输入 `12` 时先按 `1` 计）——这是正确反馈，不做"输入停顿后再更新"的抑制 |
| 提交前不因中间态阻断 | 见 §4「呈现时机」 |

### 2.6 `resigned`（已离职）只读态

- 全明细行 `:readonly="resigned"`（与三项工资字段、生效日期、调整原因同一处理方式）。
- 删除按钮与添加按钮 **`:disabled="resigned"`**（不隐藏——隐藏会让「有津贴但看不出为什么不能改」；禁用 + §6.3 要求的 `van-notice-bar` 原因说明，构成本页既有的唯一正确只读范式）。
- 行头「津贴合计」在 `resigned` 时直接取 `salary.current.allowancesTotal`（与明细和等价，避免两处取值分叉）。

---

## 3. 文案规范（精确中文，结论先行，无感叹号）

| 位置 | 文案 | 来源 |
| --- | --- | --- |
| 行头 | `津贴合计` | 现状保留 |
| 名称占位符 | `津贴名称` | 与 PC 逐字一致 |
| 金额占位符 | `金额` | 与 PC 逐字一致 |
| 添加按钮 | `添加津贴` | 与 PC 逐字一致 |
| 空态 | `暂无津贴项，合计 = 基本工资 + 岗位工资 + 绩效基数` | 与 PC 逐字一致 |
| 名称校验 | `津贴项名称不可为空` | 与 PC 逐字一致 |
| 金额非数字/负数 | `津贴「{名称}」须为不小于 0 的数字` | 与 PC 金额校验 prefix 一致 |
| 金额非整数 | `津贴「{名称}」须为整数金额` | 与 PC 逐字一致 |
| 金额过大 | `津贴「{名称}」数额过大，请核对` | 与 PC 逐字一致 |
| 删除确认标题 | `移除津贴项`（`action`） | `bossConfirm` 动词短语标题 |
| 删除确认正文 | `作用对象：「{名称}」。影响：该项不再计入调整后合计。` | `bossConfirm` 四要素自动拼装 |
| 删除确认按钮 | 主 `删除该津贴` / 次 `再想想` | `confirmText` + `bossConfirm` 固定次按钮 |
| 只读原因（既有，不改） | `已离职员工不可调整薪资，以下表单只读` | `hrDetail.vue:186` |

未列出的文案（如金额为 0 的兜底、加载中）一律沿用既有页面文案，不新增。

---

## 4. 校验与错误提示

### 4.1 规则（与 PC 同口径 = 客户端加固口径）

| 字段 | 规则 | 权威 |
| --- | --- | --- |
| `name` | `trim` 后非空，且 ≤20 字 | 契约 `textLen(name,1,20)`；PC 同 |
| `amount` | 为**不小于 0 的整数**，且整数部分 ≤6 位 | PC 客户端加固（`amountError`）；契约只要求「不小于 0 的数字」 |
| 提交整体 | 任一行非法 → **阻止提交**，页内给出第一条错误 | PC `canSubmit`；移动端同口径 |
| `key` | 空串提交时映射为 `null`（与 PC 一致），移动端不提供 `key` 编辑入口 | PC `map` |

### 4.2 呈现位置与时机

| 优先级 | 时机 | 位置 | 形态 |
| --- | --- | --- | --- |
| 字段级（金额非法、非整数、过大） | **失焦（blur）** | 该行**下方**（占一行 caption） | `--fs-caption` / `--lh-caption` / `--color-danger`，`role="alert"` |
| 字段级（名称超长） | — | 不提示 | 由 `maxlength="20"` 输入层阻断 |
| 表单级（名称空、或任一字段级错误未消解） | **提交时（点击保存）** | 既有 `.form-error`（`.card` 内、按钮上方，`role="alert"`） | 沿用 `hrDetail.vue:285-290` 既有样式，不新增 |

**为什么名称空不在失焦时报**：用户常见顺序是「先填金额再补名称」，失焦即报红会误伤正常操作；名称是唯一无法靠输入层兜底的必填项，放在提交时统一收口。

**为什么不禁用提交按钮**：现有 `hrDetail` 的口径是「按钮可点，点击后写明哪一项不对」（`validate()` → `formError`）。窄屏上灰按钮不携带「错在哪」的信息，定位成本更高。**此项与 PC（`:disabled="!canSubmit"`）不同，属允许差异**，见 §8。

### 4.3 与提交的耦合

- `submit()` 的 `validate()` 需在既有三项工资后**追加**津贴逐行校验，返回第一条错误即止（与 PC `formError` computed 的短路顺序一致）。
- 降薪二次确认（`diff < 0`）逻辑不变，仍在津贴校验通过之后触发。
- 后端 `9302 / 9305` 的页内说明路径不变（`formError` 已承载）。

---

## 5. 可访问性

### 5.1 触控目标

| 元素 | 最小尺寸 | 依据 |
| --- | --- | --- |
| 删除按钮 | 44×44（`var(--touch-min)`） | 项目硬红线；`demo-ui-redesign.md` §7.4 |
| 添加津贴按钮 | 高 44（`size="small"` 需 `min-height: var(--touch-min)` 覆盖，Vant small 默认 32 不达标） | 同上 |
| 名称 / 金额输入 | 高 ≥44、整行高 48 | 同上 |

### 5.2 数字键盘

- 金额：`type="number"` + **显式 `inputmode="numeric"`**。Vant 的 `type="number"` 不保证下发 `inputmode`，仓库已有显式 `inputmode` 先例（`views/login/index.vue:375,385,424`），本区块照做。
- 选 `numeric` 而非 `decimal`：PC 客户端要求整数金额，键盘不给小数点可减少一次校验失败。

### 5.3 对比度（全部取自既有 Token，均已核算）

| 用途 | Token | 实测对比度 |
| --- | --- | --- |
| 输入值文字 | `--text-1` | 14.68:1 ✓ |
| 行头 value / 辅助 | `--text-2` | 7.56:1 ✓ |
| 空态提示 `.tip` | `--text-3` | 4.83:1 ✓（>4.5） |
| 错误文案 | `--color-danger` | 5.57:1 ✓ |
| 分隔线 | `--border-line` | 非文本装饰，不承载信息 |
| 删除图标 | `--color-danger` | 5.57:1 ✓（非文本需 ≥3:1） |

> 占位符色 `--text-placeholder` 实测约 2.5:1，**低于 4.5:1**；这是全站 `van-field` 的既有全局口径，不由本区块引入、也不在本区块单点修改（单点改会造成同一页面两种占位符色）。已登记为设计系统层遗留，见 §10。

### 5.4 删除按钮可达性

- 图标必须配可读标签：`van-button` 加 `:aria-label="`删除津贴项 ${item.name || '第 ' + (index+1) + ' 项'}`"`；`van-icon` 加 `aria-hidden="true"`（仓库既有约定，见 `Chip.vue:30`）。
- 图标 `cross` 为 Vant 内置（**禁止引入未经确认的二进制素材**；如前端评估后改用 `delete-o` 亦为内置图标，二者均可，需在实现时二选一并保持唯一）。
- 键盘可达：`van-button` 渲染为原生 `<button>`，`focus-visible` 主色环由 `mobile.scss:38-42` 全局提供，无需重复实现。

### 5.5 屏幕阅读器行语义

- 明细容器 `<div role="group" aria-label="津贴明细">`。
- 每行 `<div role="group" :aria-label="`津贴项 ${index+1}：${item.name || '未命名'}`">`。
- 两个输入的可访问名：给 `van-field` 传 `id`，并在行内放 `<label class="visually-hidden" :for="id">`（`.visually-hidden` 为 `mobile.scss:57-67` 既有工具类）。
  **求证项**：`van-field` 的 `id` 是否落到内部 `<input>`，须前端按已安装 `vant@4.10` 产物核对；若不支持，改用 `#input` 插槽自绘 `<input>` 并绑定 `id`，样式取 `--fs-body` / `--text-1` / `--text-placeholder`（不得留成无名的裸输入框）。
- **不对实时合计加 `aria-live`**：逐键更新会持续播报「合计 x 元」，形成噪音污染；合计的权威播报落在降薪二次确认弹窗（已含当前值与调整后值）与提交成功 Toast。

---

## 6. 四态覆盖

| 态 | 触发 | 表现 | 禁止 |
| --- | --- | --- | --- |
| **normal** | 有津贴项、在职 | 行头合计实时更新；明细行可编辑；删除按钮可点；添加按钮可用 | — |
| **empty** | `allowances.length === 0` | 明细区显示空态 caption；合计 = 三项之和；添加按钮可用 | ❌ 空态与错误态同文案 ❌ 用 `van-empty` 大图占位（列表页才用大空态） |
| **error** | 金额非法（失焦）/ 名称空（提交） | 字段级：行下 caption `role="alert"`；表单级：按钮上方 `.form-error`；**阻止提交** | ❌ 仅标红输入框不给文字原因 ❌ 提交被拦但无提示 |
| **disabled** | `resigned === true` | 行头取当前值；明细行 `readonly` 显示既有项；删除/添加 `disabled`；顶部 `van-notice-bar` 说明原因 | ❌ 隐藏明细（用户需看到津贴构成）❌ 只改灰边框让它"像文字"（`demo-boss-ui-spec.md` §6.3 明文禁止） |

---

## 7. 验收标准（UI/UX 视觉走查清单，逐条可判定）

**布局**
1. 375px 视口下 3 行津贴**不出现横向滚动**，且 `document.documentElement.scrollWidth === clientWidth`。
2. 320px 视口下同第 1 条；名称列与金额列可输区宽度均 ≥72px。
3. 津贴明细与「基本工资/岗位工资/绩效基数」三项**同在一个 `.card` 内**，左边界对齐，无卡片套卡片。
4. 明细行最小高度 = 48px（`--row-h-1`），行内含输入与删除按钮时不换行、不裁切。
5. 行分隔线只出现在行与行之间，行头与首行之间**无双线**。

**交互**
6. 点击「添加津贴」后新增一行并**自动聚焦名称输入**，无需二次点击。
7. 删除一个有内容的行**恰好弹 1 次确认**；删除空行（名称空、金额空/0）**不弹**。
8. 金额输入后，「调整后合计」与差额徽标在 **≤100ms 内**更新（本地 computed，无网络请求）。
9. 合计与差额数字使用等宽数字（`.tabular-nums`），输入过程中数字不左右跳动。
10. `diff` 在输入过程中由正转负时，提交按钮文案切换但**按钮高度与宽度不变**。
11. `diff === 0` 时差额徽标不渲染，显示「与当前持平」。
12. 删除/添加按钮点击反馈立即出现（`:active` 背景 `--surface-subtle`，`touch-action: manipulation`）。

**校验**
13. 名称留空点保存 → 页内出现 `津贴项名称不可为空` 且**未发起请求**。
14. 金额输入 `-1` 后失焦 → 该行下方出现 `津贴「{名称}」须为不小于 0 的数字`，`role="alert"` 可被读屏捕获。
15. 名称输入到 20 字后**无法继续输入**，无红字报错。

**可访问性**
16. 删除按钮实测热区 ≥44×44px；添加津贴按钮实测高度 ≥44px。
17. 金额输入聚焦时唤起**数字键盘**（`inputmode="numeric"` 生效）。
18. 删除按钮在无障碍树中有可读名称（含项名），徽标与图标 `aria-hidden` 不污染朗读。
19. 键盘 Tab 可依次到达名称 → 金额 → 删除，焦点环可见（2px 主色环）。
20. 走查工具（对比度检查）在本区块扫描**无 <4.5:1 的正文断言失败项**（占位符按 §10 豁免并附说明）。

**只读态**
21. `resigned` 时：明细仍可见、输入 `readonly`、删除与添加 `disabled`，`van-notice-bar` 原因说明存在。
22. `resigned` 时点击删除/添加**无任何请求发出**。

---

## 8. 与 PC 端一致性声明

### 8.1 必须一致（不可差异化）

1. **数据口径**：`allowances` 为 `[{ key, name, amount }]`；`key` 空 → `null`；`allowancesTotal` = 明细求和；合计 = 三项 + 明细和。
2. **提交契约**：`PUT /hr/salary-structures/{employeeId}`，payload 必含 `allowances`；**移动端现有调用未传 `allowances`**（`mobile/api/hr.js:9-10` 仅透传），后端在字段缺省时保留原值（`hrStore.js:543`）——因此**必须补传，否则编辑不落库**。契约本身无需变更，PC 已在用同一形状。
3. **校验规则**：名称非空且 ≤20 字；金额为不小于 0 的整数且 ≤6 位（PC 客户端加固口径）。
4. **文案**：名称/金额占位符、空态、四条金额/名称错误文案、`添加津贴` 按钮，全部逐字一致。
5. **降薪语义**：`diff < 0` 二次确认、按钮文案变「提交降薪调整」、成功 Toast「薪资已保存，{date} 生效」。
6. **只读态原则**：已离职整表单只读 + 原因说明。

### 8.2 允许移动端差异化

| 项 | PC | 移动端 | 理由 |
| --- | --- | --- | --- |
| 布局 | 抽屉内 3 列网格（`2fr 1fr 56px`） | 卡片内 3 列网格（`3fr 2fr 44px`） | 窄屏单列流，与三项工资同卡片同层级 |
| 密度 | `el-input` 默认高 | 行高 48 / 触控 44 | 单手持机 |
| 删除入口 | `el-button link 删除` 文字链 | 44×44 图标按钮 + `aria-label` | 文字链在窄屏横向占位过大；图标需补可读名 |
| 删除确认 | **无**确认（直接删） | **有**1 次确认（空行豁免） | 移动端误触率高；已登记为建议 PC 后续对齐（§10） |
| 新增行金额初值 | `0` | 空串 + 占位符 | 提交映射后落库值相同，不影响口径 |
| 校验反馈时机 | 按钮 `disabled` + 点击 `ElMessage` | 不禁用按钮，点击后页内 `form-error` | 窄屏灰按钮不携带「错在哪」信息 |
| 添加按钮位置 | 区块标题行右侧 | 明细行下方整行 | 窄屏右对齐小按钮触控区不足 44px |

---

## 9. 新增 Token 清单

**无。** 本区块全部取值来自既有 Token：`--touch-min`、`--row-h-1`、`--sp-1..3`、`--r-sm`、`--fs-caption`、`--lh-caption`、`--border-line`、`--text-1/2/3`、`--text-placeholder`、`--color-danger`、`--color-primary`、`--surface-subtle`。

宽度列用 `fr` 比例（`3fr / 2fr`）而非固定 px，正是为了**避免新增 `--allowance-*-col` 类尺寸 Token**；行分隔复用 `--border-line`，不新增色值。

---

## 10. 事实性纠正与 `TODO(扩展)`

### 事实性纠正

1. **`demo-boss-ui-spec.md` §6.3 引用的路径已失效**：文中写 `mobile/views/boss/hrDetail.vue`，实际文件在 `mobile/**modules**/boss/views/hrDetail.vue`。不影响其结论，但引用需更正。
2. **PC 与契约的校验强度不一致（原文未点明）**：问题描述把 PC 口径概括为「金额为非负数」，实测 PC `amountError` 更严——要求**整数**且**整数部分 ≤6 位**（`SalaryEditorDrawer.vue:105-112`）；契约/Mock 只校验「不小于 0 的数字」（`mock/routes/hr.js:145`）。本规范按 **PC 客户端口径**（更严者）执行，并已在 §4.1 标注权威来源。
3. **移动端 `updateHrSalary` 未传 `allowances`，当前必然静默不改津贴**：这不是新发现的缺陷（原设计就是不给移动端编辑），但改为可编辑后它成为**必须同时修的对接点**，否则 UI 有反馈、数据无变化（属 §5.2 静默失败）。已在 §8.1 第 2 条列为硬性一致项。
4. **「津贴合计」`van-cell` 的 value 现取 `salary.current`，编辑态会不联动**：第 33 行 `allowancesTotal` computed 直接读 `salary.value.current.allowancesTotal`；改为可编辑后须切到 `form.allowances` 求和派生，否则用户改金额而合计纹丝不动。

### TODO(扩展)

- `TODO(扩展): 占位符色 --text-placeholder（neutral-400）对白底约 2.5:1，低于 WCAG AA 4.5:1；属全站 van-field 既有口径，需在设计系统层统一裁决（是否提升到 neutral-500 / --text-3），不在本区块单点修改。`
- `TODO(扩展): PC 端 SalaryEditorDrawer 删除津贴项无二次确认，与移动端不一致；建议由 PC 侧后续对齐（或在 PC 复核后确认差异为有意）。`
- `TODO(扩展): 明细行数 >10 时是否折叠/收起，待真实调薪数据出现后再评估（本轮不设上限，与契约一致）。`
- `TODO(扩展): 员工端（staff）是否需只读展示津贴明细，需求未涉及，待确认后再设计。`

---

## 附：前端实现对接点清单（只列位置与改动性质，不含实现）

| 文件 | 位置 | 改动性质 |
| --- | --- | --- |
| `mobile/modules/boss/views/hrDetail.vue` | `:212` `van-cell 津贴合计` | 替换为行头 + 明细区 + 添加按钮（§1.1） |
| 同上 | `form`（`:28`） | 增加 `allowances` 字段，`load()` 时从 `salaryData.current.allowances` 深拷贝预填 |
| 同上 | `:33` `allowancesTotal` computed | 改为由 `form.allowances` 求和派生（§2.5） |
| 同上 | `validate()`（`:66-79`） | 追加津贴逐行校验（§4.1） |
| 同上 | `submit()` payload（`:102-108`） | 增加 `allowances`（`key \|\| null`、`name` trim、`amount: Number(... \|\| 0)`） |
| 同上 | `<style scoped>` | 新增 `.allowance-row` / `.allowance-rows` / `.allowance-add` 三组规则，全部消费既有 Token（§1.2、§1.4） |
| `mobile/modules/boss/views/hrDetail.vue` 引用 | — | 新增 `bossConfirm` 引用（`../components/bossConfirm.js`） |

> 禁改范围（本任务硬约束）：`hrm-admin/**`、`hrm-server/**`；`demo` 的 Mock 装配与既有断言（`shared/mock/**`、`*.spec.js`）一律不动。
