# ADR-结构迁移-01：Design Token 真源归口上移 + 移动端两端拆分独立

| 项目 | 内容 |
| ---- | ---- |
| 状态 | **v3（非主干修订：落地后事实回填 + 回滚点补全；决策与裁定结论不变）** —— 本方案已获主智能体审批并按 §3.7 分批落地（B2–B7、B8 部分）；v2 已按技术评审必改项 1–11 与自身错误 N1–N9 修订并**复评「通过」**；**v3 为非主干修订，按 §8 ④ 版本变更须重评后方可报审** |
| 日期 | 2026-09-25（v2 修订：2026-09-25；v3 非主干修订：2026-09-25） |
| 评审结论 | **有条件通过**（[tech-review-structure-migration.md](tech-review-structure-migration.md)，2026-09-25）；必改项 **1 / 6 / 10** 分别为 **B2 / B7 / B6** 开工前置；必改项 **2** 须报审前闭环并转交网络安全工程师形式核对。**〔v3 回填〕复评（2026-09-25）：结论「通过」，具备报审资格**（见同报告「[附：ADR v2 复评记录](tech-review-structure-migration.md)」）；**v3 属非主干修订，按 §8 ④ 须重评** |
| 作者 | 架构师 `express-station-architect` |
| 评估对象 | 本文件同时覆盖 **决策一 R-0（Token 真源归口）** 与 **决策二（移动端两端拆分独立）** —— 用户已批准「合并评估」 |
| 权限档位 | **C 档 · 结构变更**（须主智能体 §10.3 三步授权；涉现网条目另须网络安全工程师结论，见 §9） |
| 上游依据 | 用户本轮口径（R-0 与「两端未完全拆分独立」）；[ui-experience-optimization.md](ui-experience-optimization.md) §1.2 与 §7.1 R-0；[tech-review-ui-experience.md](tech-review-ui-experience.md) §3 R-0（判定「属结构变更，须走架构师 ADR」）；[multi-client-split-plan.md](multi-client-split-plan.md)（**本 ADR 复用并扩展，不另起炉灶**）；[project-tree.md](project-tree.md) §0 三端定位；[security-client-admission-review.md](security-client-admission-review.md)；[multi-client-architecture.md](multi-client-architecture.md) |
| 与既有方案关系 | 本 ADR 是 [multi-client-split-plan.md](multi-client-split-plan.md) 的**收敛版**：把其附录 A 的 ADR-SPLIT-01～05 与本文两决策**合并为同一次结构变更**（§4）。凡 split-plan 已详述的事实基线与对比表，本文只引章节号、不复制。 |
| 本轮边界 | **只产出 ADR 文档，不写任何实现代码**；不改 `hrm-dev/hrm-server/`、`hrm-dev/sql/`、`api.md`、`db.md`、`.trae/rules/`、`SESSION-STATE.md`、他人已产出方案原文、任何前端源码 |

> **凭据声明：** 本文不记录任何真实凭据、域名、IP、AppKey；示例一律 `change_me_*` / `example.invalid` 占位。**v2 已清除 v1 误入的真实域名（全文真实域名 / IP / 凭据命中 = 0）**，并须按 §9 SP7 转交网络安全工程师形式核对。
>
> **版本绑定：** 本 v2 对应评审报告的必改项清单；**若方案版本再变更，须重新提交技术评审（评审结论绑定版本）。** **〔v3 回填〕** v2 复评结论「**通过**」（2026-09-25）；**v3 为非主干修订（落地后事实回填 + 回滚点补全），按 `项目规则1.md` §8 ④ 版本变更须重评后方可报审。**
>
> **安全声明（P0.5）：** 本文涉**现网 Nginx location、外部暴露面、安卓壳加载地址、共享包供应链**的条目均为「设计输入」而非安全结论；落地前须由**网络安全工程师**按 L7 出具技术评估（清单见 §9），主智能体方可授权。
>
> **报审闸门（P0.6 / L8）：** 本 ADR 属**方案阶段产物**，须先经**技术评审工程师**六维评估，结论「通过 / 有条件通过」方可报主智能体审批；「打回」即退回修订后重评。**产出方（架构师）与评估方（技术评审）分离。** —— **本轮评估已完成：结论「有条件通过」**（[tech-review-structure-migration.md](tech-review-structure-migration.md)）；本 v2 按必改项修订后**须复评再报审**。

---

## 1. 背景与问题陈述

### 1.1 决策一要解决的问题（R-0）

现状事实（本轮实测，来源见「证据」列）：

| # | 事实 | 证据 |
| - | ---- | ---- |
| 1 | Design Token **真源**在演示工程：`hrm-demo/src/shared/styles/tokens.base.scss`（L1 原始色值 + L2 共用语义 + L3 通用基础，注释自称「跨端 Design Token 真源（P1-2）」） | [tokens.base.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss) |
| 2 | `hrm-admin` **未接入 Design Tokens**：`src/styles/` 下仅 `index.scss` 一个文件，全工程 `var(--` 命中 **0**；色值未收敛到 Token，实为 Element Plus 默认值 + 少量硬编码字面量（`hrm-admin/src` 实测：`#409eff` **4 处**、`#909399` **6 处**） | [index.scss:L59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/styles/index.scss#L59)；[layout/index.vue:L218](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/layout/index.vue#L218)；tech-review §1.1（`var(--` 0 命中，已复核） |
| 3 | 依赖方向为 **Demo → admin 单向**：`hrm-demo/src/pc/main.js` 先 `import '@admin/styles/index.scss'`、后 `import './styles/tokens.scss'`（后者压前者） | [pc/main.js:22-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/main.js#L21-L25) |
| 4 | 门禁 `verify:tokens` 的扫描目标写死在演示工程内：`TOKENS_FILE = ../src/pc/styles/tokens.scss`、`BASE_FILE = ../src/shared/styles/tokens.base.scss` | [gen-element-tokens.mjs:36-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/scripts/gen-element-tokens.mjs#L36-L38) |
| 5 | 「网页端」实现主体亦在演示工程：`hrm-demo/src/pc/`（split-plan §0.1 实测非测试源码 148 个文件） | split-plan §0.1 / project-tree §0 |

**问题的准确表述（见 §1.3 事实性纠正 1）：** 并非「删除 `hrm-demo` 会让 `hrm-admin` 构建失败」——依赖方向是单向的 Demo → admin。真实风险是：

> **拟作为生产交付的「网页端」实现主体、以及三端共用的 Design Token 唯一真源，物理寄居于一个按项目规则 §12 可被整体删除的演示工程内**；同时 `hrm-admin` 要达到对比度合规所需的取值口径**只存在于演示工程**（tech-review §3 R-0「要修 `hrm-admin` 对比度，必须有 admin 侧的落地载体」）。即：**生产资产 / 生产口径寄居在可整体剥离的演示工程里**，不满足生产工程应有的存续性保证，且与「单一逻辑不得重复实现」相冲突（若 admin 自建一份即形成两份同值）。

### 1.2 决策二要解决的问题（移动端两端拆分独立）

| # | 事实 | 证据 |
| - | ---- | ---- |
| 1 | 「驿站精灵（管理端）」与「驿站助手（员工端）」**共用同一个 HTML 入口** `mobile.html`，仅靠 `?as=` 参数区分 | [mobile.html:20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/mobile.html#L20)；[portal/main.js:31/38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/main.js#L31-L38) |
| 2 | 两端路由在同一 router 文件内聚合：`bossRoutes`（`modules/boss/router.js`，22 页）+ 员工端路由（`views/staff/**`，约 24 页）共处一份 `routes` 数组 | [mobile/router/index.js:27-223](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L27-L223) |
| 3 | 品牌差异靠**运行时改写** `<title>` 实现（`/boss` 前缀 → 「驿站精灵」，否则保持默认） —— 说明两品牌共用一个静态入口，无法各自定稿 | [mobile/router/index.js:250-252](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L246-L253) |
| 4 | 存在**跨域直引**：`/boss/kpi/:employeeId` 直接 `import('../views/staff/kpi.vue')`（全仓唯一一处，已登记 `TODO(扩展)`）；另有中立共享页 `MessagePage.vue` / `NoticeReader.vue` 位于域目录之外被两端共用 | [mobile/router/index.js:40-46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L39-L46) |
| 5 | 门禁现状：`verify:mock`（本轮口径 **942**）、`verify:mobile`（**48**）、`verify:tokens`、`build` / `build:prod` / `lint` / `lint:style` / `test` / `e2e` | `hrm-demo/package.json`；tech-review §1.5 必改项 5（基线须测试工程师实跑冻结） |
| 6 | 后端**三端共用同一套 API**，端类型仅作**产品/审计约束、不参与鉴权**；端准入已 fail-closed（PC=ADMIN｜`as=boss`=ADMIN｜`as=station`=STAFF+STATION_ADMIN｜缺省/未知拒） | [ClientAdmissionPolicy.java:13-33/64-94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L13-L33)；security-client-admission-review §1 |

**问题：** 两端在**代码、路由、构建产物、品牌、发布节奏**上未独立，一处改动即两端共担回归；壳内同一 H5 靠参数分流，使「两端各自独立发版 / 各自独立做视觉走查 / 危一端不伤另一端」在现状下不可达成。

### 1.3 事实性纠正（对既有材料）

1. **「删除 `hrm-demo`，生产 PC 端会一起崩」不成立。** 实测依赖方向为 **Demo → admin 单向**（`@admin` 别名只读引用，`hrm-admin` 全仓无任何指向 `hrm-demo` 的引用）。删除 `hrm-demo` 会消失的是：**① Token 真源；② 拟作为生产交付的网页端实现 `src/pc`；③ 演示三端入口** —— `hrm-admin` 自身仍可独立 `vite build`。问题性质应更正为「**生产资产/生产口径寄居于可整体删除的演示工程**」，而非「构建依赖」。此更正不改变 R-0 需走 ADR 的结论（tech-review §3 R-0 判定依然成立）。
2. **[multi-client-split-plan.md](multi-client-split-plan.md) §0.5-2 与 §4.3「红线」、其裁定项 D8 所述的生产 API 基址错误已不存在。** 实测 `hrm-demo/.env.production` 现值为 `VITE_API_BASE=/hrm-api/v1`，且带注释「不能写 `/api/v1`——那是现网 courier-app 的路径」。故 D8 由「阻塞 S6 的前置」**降级为「已闭环，仅需运维一次前缀确认」**。
3. **`verify:mock` 基线口径已漂移。** split-plan §1.2 C9 写「现状 919/919」，本轮用户口径为 **942**（用户口径，**待书面裁定归档**）。二者不一致，须由测试工程师实跑冻结为**唯一基线**后方可作为验收依据（本 ADR 一律以「≥ 冻结基线」表述，不写死数字）。
4. **split-plan 裁定项 D5 已被用户部分裁定。** 用户已明确「**两端终极形态都是 安卓壳 + H5**」（用户口径，**待书面裁定归档**；旁证：`project-tree.md` §0 三端定位）——即 D5 中「是否做壳」已定；仍待裁定的只剩「**两个 APK** 还是**单壳双入口（运行时切 URL）**」（本文 §3.4 建议前者）。
5. **〔v2 更正〕[ui-experience-optimization.md](ui-experience-optimization.md) §7.1 R-0 选项② 的定性无误，v1 的「纠正」不成立。** v1 曾称该处定性「需修正」，属**对象错位**：原文选项② 的对象是「`hrm-admin` **内**新增自有 `tokens.scss`（值复用、文件分离）」= **admin 自建副本**（与 Demo 真源并存 → 两份同值），原文判其「违反『同一逻辑不得重复实现』」**正确**。本 ADR §2.1 选项② 是「中立共享包 `packages/tokens`」，与原文选项② **不是同一形态（编号巧合）**，故**不构成对既有材料的修正**；本 ADR 只是新增一种原文未列形态。
6. **[v2 新增] 「用户已裁定」类陈述缺书面载体。** 本 ADR 引用「本轮用户口径」（R-0 归口、D5 两端终态 = 壳 + H5、`verify:mock` 942）在仓库内**无对应的裁定记录载体**（update-log / 会话记录未见）。本 v2 一律改标「用户口径（**待书面裁定归档**）」，并登记 §10.2 U-6 供核实。

---

## 2. 决策一：Design Token 真源归口上移（R-0）

### 2.1 选项对比与取舍

| 维度 | ① 上移到 `hrm-admin`（生产 PC） | **② 独立共享包 `packages/tokens`（推荐）** | ③ admin 内局部值覆盖（最小改动） |
| ---- | ---- | ---- | ---- |
| 真源位置 | `hrm-admin/src/styles/tokens.base.scss` | `hrm-dev/hrm-clients/packages/tokens/`（中立） | 无真源；`hrm-admin/src/styles/index.scss` 内写死字面量 |
| 端依赖方向 | **移动端（boss-h5 / staff-h5）→ 一期 PC 生产工程源码**（跨端反向耦合，新形态的坏味道） | 三端 / admin / demo **→ 中立包**（无端耦合，方向单一） | 无引用，但**两份同值→必然漂移** |
| 真源受门禁保护 | 否（`hrm-admin` 仅 `dev/build/preview`，无 lint / verify / tokens 校验） | **是**（`verify:tokens` 扫描目标落在包内，包可被门禁直接覆盖） | 否 |
| 与三端拆分方案的契合度 | 差：拆分终态是 `apps/web` 取代/并行 `hrm-admin`（D2），真源挂在 `hrm-admin` 会导致**二次迁移** | **好**：split-plan §3.1 第 8 项已把 `tokens.base.scss` 判定为共享资产、落点 `packages/shared`；本决策只是把它独立成 `packages/tokens` 以降低耦合 | 差：拆分后仍需迁移一次 |
| 删除 `hrm-demo` 后可用性 | 可用 | 可用（真源不在演示工程） | 可用，但口径来源已丢失 |
| 对 §12 的冲击 | 中：`hrm-admin` 成为全体依赖的枢纽，「一期工程」边界模糊 | 小：新增中立包，不改变任何既有硬约束语义 | 小 |
| 落地成本 | 低（搬文件 + 改引用） | 中（需 workspace 基建，与拆分同批落地更省） | 最低（但属技术债） |
| 结论 | 备选（不推荐） | **推荐** | 仅作临时兜底（**收敛期限已裁定：不得晚于 B2 完成**，见 §7.2 A-4） |

**取舍理由（为什么推荐 ②）：**
1. **真源必须中立且受门禁保护。** 把真源放进一个无 lint/verify 的生产工程（①）等于让「唯一真源」失去机器化守护，与「门禁不弱化」的方向相反。
2. **避免二次迁移。** 三端拆分终态下 `hrm-admin` 与 `apps/web` 的关系本身待裁定（D2）；真源挂在任一 PC 实现之内，D2 一旦收敛就要再搬一次。放进 `packages/tokens` 则与 D2 解耦。
3. **③ 只可作限定期兜底。** 采纳 tech-review §3 R-0 的建议路线：**③ 先兜住对比度合规红线（带 `TODO(扩展)` 指向本 ADR），① / ② 为目标态**；**收敛期限已由主智能体裁定为「不得晚于 B2 完成」**（本文 §7.2 A-4）——即 B2 完成时 ③ 必须退出、② 生效。
4. 与「精简 / 复用」原则一致：全仓 Token 定义**收敛到 1 处**，各端只保留**合法平台差异层**（Element / Vant 变量覆盖、字号阶梯、尺寸类 L3 —— `tokens.base.scss` 头注释已明确划分）。

### 2.2 决策（R-0 终态）

| 项 | 决策 |
| - | ---- |
| 真源 | `hrm-dev/hrm-clients/packages/tokens/`（`@kdyzgl/tokens`），持有 `tokens.base.scss`（L1+L2+L3 通用）与 Element 浅色阶契约数据 |
| 平台差异层 | 各端自带（`apps/*/src/styles/tokens.scss` 平台覆盖、`mobile.scss` 等），**不得再定义真源内已有语义** |
| 消费方 | `apps/web`、`apps/staff-h5`、`apps/boss-h5`、迁移期 `hrm-demo`、`hrm-admin` **只读消费同一真源** |
| `hrm-admin` 消费方式 | **主智能体已裁定取方案 ①**：以**相对路径 `@use`** 真源 + 新建 `hrm-admin/src/styles/tokens.scss` 承载 Element 变量覆盖；**不改 `hrm-admin/package.json`**、不改业务代码、不引入 npm 依赖（裁定依据见 §7.2 A-2）。备选 ②（镜像 + 跨工程一致性校验）**不采纳** |
| 依赖方向 | **单向**：`apps/*` / `hrm-admin` / `hrm-demo` → `packages/tokens`；真源**零反向依赖**（不含任何端包、不含 `@admin`、不含 Vue/Vant/Element） |
| 收敛期限 | ③（admin 局部覆盖）→ ②：**已裁定「不得晚于 B2 完成」**（§7.2 A-4）；期限内不得出现第三份同值 |

> **对 §12 的影响：** 本决策使「真源」移出演示工程，**方向与 §12.1「Demo 只读引用、可整体回滚」一致**；但需要在 §12 增补一句**抽象的**约束「**Token 真源唯一且不得位于演示工程；任何端不得再自定义 Token 真源**」（不写实现路径，避免实现细节漂移规则真源）——属**修订条款**，见 §8。

### 2.3 依赖方向如何保证「生产不依赖演示工程」（可判定断言）

> 全部为**可执行 / 可静态判定**项，不写「经验上满足」。

| # | 断言 | 判定方式 | 通过条件 |
| - | ---- | ---- | ---- |
| A1 | `hrm-admin` 不引用演示工程 | 在 `hrm-dev/hrm-admin/**` 检索 `hrm-demo` | 命中数 **= 0** |
| A2 | 共享包不反向依赖任何端 | 在 `hrm-clients/packages/**` 检索 `hrm-demo`、`hrm-admin`、`@admin` | 命中数 **= 0** |
| A3 | 真源唯一 | `Get-ChildItem -Recurse -Filter tokens.base.scss` 全仓计数 | **= 1**（仅 `packages/tokens/`） |
| A4 | **删除演练（关键断言，四步）** | **(a) 移出/删除**：把 `hrm-dev/hrm-demo` **移出工作区或删除**（**非仅重命名**）→ **(b) 清装**：三端 `apps/*` 与 `hrm-admin` 均执行 `npm ci`（或 `npm install` 且清 `node_modules`）**并清空各自 `dist`** 后跑 `build` / `build:prod` → **(c) 产物级断言**：各 `dist` 内检索字符串 `hrm-demo` **= 0**，且 `build:prod` 产物**无 Mock chunk**（静态检索）→ **(d) 边界声明**：见下方注 | **(a)–(c) 全部通过**；随后恢复目录（演练须在主智能体授权窗口内、先在非生产分支执行）|
| A5 | `hrm-admin` Token 无游离字面量 | 在 `hrm-admin/src` 检索十六进制色值字面量，与 `hrm-admin/src/styles/tokens.scss` 白名单比对 | 未登记字面量 **= 0** |
| A6 | 真源受门禁覆盖 | `verify:tokens` 报告须打印其扫描目标路径 | 目标路径指向 `packages/tokens` + 各端平台层；全绿 |

> **A4 的定位（v2 修订）：A4 是「生产不依赖演示工程」最接近充分的操作性证据**（v1 曾称「唯一直证」，经评审 §3.1 判定**严格不充分**故降级）；A1–A3/A5/A6 为其充分前置。
>
> **A4 的边界声明（(d)）：** A4 仅覆盖**构建期**依赖（编译期路径/别名引用、产物内容残留）；**运行期与部署期**依赖（现网 Nginx 指向演示站、`deploy/docker-demo/portal.html` 硬编码旧链接、旧壳 APK 加载地址）**不在 A4 覆盖范围**，分别由 §10.1 假设（H5）、§5.4 部署面与 §9 安全清单（SP1/SP3）承接。
>
> **A4 的执行纪律：** 涉**移出/删除目录**（本地、可逆），执行属 A 档本地构建范畴，但**因触及工程结构，须在主智能体授权窗口内进行**；**恢复前不得提交任何中间态**。

### 2.4 `verify:tokens` 扫描路径如何调整

| 项 | 现状 | 终态 |
| - | ---- | ---- |
| 脚本归属 | `hrm-demo/scripts/gen-element-tokens.mjs`（脚本在演示工程内） | 提升到 **workspace 根 `scripts/`**（或 `packages/tokens/scripts/`），由各端与 `hrm-admin` 共用 |
| 真源读取 | `BASE_FILE = ../src/shared/styles/tokens.base.scss` | 指向 `packages/tokens/tokens.base.scss` |
| 平台层读取 | `TOKENS_FILE = ../src/pc/styles/tokens.scss` | **多目标数组**：各端 `apps/*/src/styles/tokens.scss` + `hrm-admin/src/styles/tokens.scss`（若采纳镜像方案，另校验镜像与真源逐值一致） |
| 校验内容 | 30 个 `--el-color-*-light-{3,5,7,8,9}` / `-dark-2` 字面量（A 类算法复现 + B 类手写基线快照） | **不变**（沿用两段式设计，见脚本头注释）；**扩展**为多目标 + 跨工程一致性 |
| 纪律 | 「它不会拦截其它新增 Token，靠 CR 人工把关」 | 保留该事实陈述，并在本 ADR 下新增查询：**任何端内出现真源已有语义的重复定义 → 评审打回** |

> **`hrm-demo` 被删除时生产是否仍完整可用？** 是。真源在 `hrm-clients/packages/tokens`，`hrm-admin` 与三端 `apps/*` 均不引用 `hrm-demo`（A1/A2 断言）。`verify:tokens` 脚本已随包提升，不再寄居演示工程。**唯一残留**：演示态（Mock）能力须由构建模式 `VITE_MOCK_ENABLED` + `@kdyzgl/mock` 承接（split-plan §5.2 / D1）。

### 2.5 是否与「三端拆分（Workspace 多工程）」合并为同一次结构变更

**建议：合并为同一次结构变更（同一 workspace 落地），但分批执行、每批独立验收与回滚。** 理由：

1. **省一轮迁移：** 二者共用同一 workspace + 单 lockfile 基建。分开做要**两次**建包、两次改引用、两次全量回归、两次改 Nginx。
2. **依赖树只重构一次：** 真源上移与三端拆包同属「依赖方向收敛」，一次到位可同时消解 `resolve.dedupe` 白屏类风险（split-plan §2.3 理由 1）。
3. **风险可控：** 用批次 + 每批验收 + 每批回滚点把一次结构变更切为可独立回滚的单元（§3.7）；R-0 作为**第 2 批**先行落地，无需等三端全拆完。
4. **不违背任一硬约束：** 不触碰 `hrm-server`、不改 `api.md` / `db.md`、不改后端契约；`hrm-demo` 迁移期冻结保留、可整体删除。

### 2.6 权限档位与回滚（决策一）

- **档位：C 档 · 结构变更**（新增 `hrm-clients/` 顶层工程目录属结构变更；改 `hrm-admin/src/styles/**` 与 `vite.config.js` 属生产工程改动，须 Review）。须主智能体 §10.3 三步表单化授权。
- **回滚路径：** 删除新增 `hrm-clients/` 目录 + 对 `hrm-demo` / `hrm-admin` 的引用改动做**反向 diff**（或 `git checkout -- <指定路径>`，**禁 `reset --hard` / `clean -f`**，D 档）。**回滚前先 `git diff --cached --name-status` 全量核对暂存区**（反模式 A21：并发会话已暂存内容会被一并提交）。Nginx 未改，线上零影响。

---

## 3. 决策二：移动端两端拆分独立（驿站精灵 / 驿站助手）

### 3.1 目标终态（一句话）

在「**三端共用一个后端服务**」与「**Demo 可整体剥离**」两条硬约束下，把共用 `mobile.html` 的「驿站精灵（管理端）」与「驿站助手（员工端）」演进为 **两个独立工程、独立构建、独立发布、独立品牌入口**（共享中立包），**不改变后端 API 契约与端准入的产品语义**。

### 3.2 拆分粒度选项对比

| 维度 | ① 仅拆入口（两个 HTML + 两套路由，同一构建） | **② 两个 Vite app（`apps/boss-h5` / `apps/staff-h5`，共享 `packages/*`）（推荐）** | ③ 其他（子路径 + 单 app 双 router / 单 app 多实例） |
| ---- | ---- | ---- | ---- |
| 独立构建 | 否（同一 `vite.config`，同一次 `build` 出全部产物） | **是**（各自 `package.json` / `vite.config` / `dist`） | 否 |
| 独立发布 | 否（任一端改动须重出整包） | **是**（互不阻断，split-plan C2/C3） | 否 |
| 独立品牌 | 部分（HTML `<title>` 可各自定稿，但静态资源同包共享） | **是**（各自 `index.html`、`<title>`、应用名） | 部分 |
| 端隔离强度 | 弱（路由文件仍共享，跨域直引仍可能） | **强**（两端无跨工程相对引用，跨域直引在编译期即断） | 弱 |
| 依赖版本一致性 | 单 lockfile（强） | 单 lockfile（强，workspace） | 单 lockfile（强） |
| 迁移成本 | 低 | 中（内核拆分：router / stores/auth / http 需理清） | 低 |
| 是否满足用户目标 | **否**（仍非独立代码） | **是** | 否 |
| 结论 | 仅可作 B3 的**中间过渡步** | **推荐（终态）** | 否决 |

> ① 的价值：若主智能体希望**进一步缩小单批风险**，可把「两 HTML 两路由」作为 `apps/*-h5` 的**首批内嵌步骤**（先在同一 app 内物理拆出两端源码目录与两个入口，再抽离为两个 app）。本 ADR 允许该渐进路径，但**终态必须是 ②**。

### 3.3 URL 与路由终态（迁移期 / 终态两套方案 + 线上零中断）

| 阶段 | 驿站精灵（管理端） | 驿站助手（员工端） | 现网动作 | 中断风险 |
| ---- | ---- | ---- | ---- | ---- |
| **现状** | `mobile.html#/login?as=boss` | `mobile.html#/login?as=station` | 由演示静态站提供，`location = /` 与 `location /` **不动** | — |
| **迁移期** | **新增** `/boss/`（`apps/boss-h5` dist） | **新增** `/staff/`（`apps/staff-h5` dist） | **只增不改**：新增两条 location；既有 location 全部保留 | **零**（旧入口仍在，旧壳 APK 仍可用） |
| **终态** | `/boss/#/login`（hash 路由 + 子路径 base） | `/staff/#/login`（同左） | 旧 `mobile.html?as=` 保留 ≥ 1 个发布周期后按 D1 处置 | 零（退役是显式动作） |

**路由终态要点：**
- 采用 **hash 路由 + 子路径 base**（`/boss/`、`/staff/`）：壳内无服务端 rewrite 兜底，hash 是既有选型（`mobile/router/index.js:15-16` 注释已论证），沿用不引入新风险。
- **子路径 vs 独立子域：** 推荐**子路径**——**主智能体已裁定迁移期与终态统一用子路径** `/web/` `/staff/` `/boss/`（§7.1 D4 / §7.2 B-5），免 DNS A 记录依赖、单证书覆盖；子域方案**不采纳**（原待核实项 §10 U-1 随之关闭）。
- **`?as=` 参数处置：** 两端独立入口后，端类型应**由入口固定上报**（`clientType=BOSS` / `clientType=STAFF`，或 `X-Client-Type` 头），不再依赖 `as`。
  - **后端零改动依据：** `ClientAdmissionPolicy.parseEnd` 仅在取值等于旧值 `H5` 时才读取 `as` 派生（[L132-142](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L132-L142)）；新入口直接上报 `BOSS` / `STAFF` 即命中取值域，**`as` 无需后端配合**。
  - **兼容期：** 旧 APK / 旧书签仍可能带 `?as=`；迁移期新入口须**同时接受** `as` 与自身固定端类型（口径一致，冲突时以入口为准），避免已登录用户被登出或 1110 拒登。
  - **退役触发条件（主智能体已裁定：保留 ≥ 1 个发布周期，不做无限期保留，§7.2 B-4）：** 满足以下**全部**条件方可于 **B8** 提交退役（删除 `as` 兼容读与旧入口）——① B7 上线后新子路径入口连续 **1 个发布周期**（**计时起点 = B7 上线日 2026-09-25**）**无 P0 回滚**；② 旧入口（`mobile.html?as=`）访问量日志在**该周期末归零或在运维记录中低于约定阈值**（阈值由运维据现网日志定，**不落本文数字**）；③ 旧壳 APK 已按 D5 下发/提示升级并完成一个发布周期。**任一不满足即顺延一个周期，不得静默下线。**
- **基址一致性（易错点，split-plan §4.6）：** `vite.base`、`createWebHashHistory` 的 base、Nginx `alias` / `try_files` **三者必须一致**，否则资源 404。
- **缓存纪律：** `index.html` 必须 `no-store`/`no-cache`（引用带 hash 的 chunk，缓存住会「新页面引旧 chunk」）；`assets/*` 长缓存；**缺失资源必须 404，不得回退 HTML**（否则 HTML 被当 ES 模块解析 → 整页白屏）。

### 3.4 安卓壳适配

| 项 | 决策 / 建议 |
| - | ---- |
| 加载方式 | **远程 https 加载**（沿用 `BuildConfig.H5_URL`；`assets/h5/*` 继续为 gitignore 占位，**不做离线包** —— `base` 与 `file://` 硬冲突，split-plan §0.4 / §4.2 已论证） |
| 壳数量 | **建议：两个 APK**（「驿站精灵」指 `/boss/`；「驿站助手」指 `/staff/`），各自 `buildConfigField` 注入 `H5_URL`，应用名 / 包名区分 |
| 包名占位约定（v2 补） | **两个 APK 的 `applicationId` 暂用占位：`com.example.hrmwebview.boss` / `com.example.hrmwebview.staff`**（现状单值为 `com.example.hrmwebview`，见 `app/build.gradle:11`）；**真实包名由用户/主智能体裁定，不落本文敏感信息**。B6 的「包名区分」验收即检「两者不相同且均为占位」——**不校验真实包名**（避免凭据/品牌信息入库） |
| 备选 | 单壳双入口（壳内运行时切 URL）：**不推荐** —— 需壳内 UI 支持、品牌无法独立定稿、运行时切 URL 引入「同一 APK 内两端登录态混登」的审计复杂度（与端准入 fail-closed 的产品意图相左） |
| `H5_URL` 注入 | 落地 `app/build.gradle` 的既有 `TODO(扩展)`：改为构建参数注入（如 `-Ph5Url=...`），**真实域名不入库** |
| 桥接契约 | `HrmBridge` / `HrmShell` 方法名与入参**保持不变**（改名即打断通信）；`getWifiInfo` 浏览器下返回 `mock:true` 且 UI 明示 |
| 混合内容 | 壳必须加载 `https`（Android 9+ 默认拦明文）；仅 debug 经 `network_security_config.xml` 放行模拟器/局域网 |
| 迁移期 | 已装旧单壳 APK 的 `H5_URL` 指向 `.../mobile.html`（**不带 `?as=`**；实测 `app/build.gradle`：debug 段为**安卓模拟器专用地址（非真实 IP，不入本文）**、release 段为 `https://example.invalid/mobile.html` 占位），**继续可用**（现网静态站未动）；新壳指新子路径 |
| 验证状态 | **未编译验证**（本机无 Android SDK）。**v2 修订（去「收敛到 CI」）：** 全仓**无任何 CI 配置**（无 `.github/workflows` / `.gitlab-ci.yml` / `Jenkinsfile`），故 B6 验收 = **静态审查 + 标注未运行**；**运行期验收**（壳加载 / 桥接 / 混合内容 / HTTPS）**收敛到具备 Android SDK 的构建环境**另行执行，**本批次不声称可交付**（与 §12.8 一致） |

> **用户口径已明确「两端终极形态 = 安卓壳 + H5」**（project-tree §0 三端定位；**用户口径，待书面裁定归档**），故「是否做壳」不再是裁定项；待裁定的是「两 APK vs 单壳双入口」（split-plan D5 的剩余部分）。

### 3.5 共享什么、不共享什么

| # | 资产 | 判定 | 落点 | 理由 |
| - | ---- | ---- | ---- | ---- |
| 1 | 请求层（`createHttp` 工厂：baseURL / Bearer 注入 / `body.code` 分发 / 401 与 1108 幂等广播 / 超时策略） | **共享** | `packages/api-client` | 两端必须同源；这是拆分核心收益点（现状 PC 复用 `@admin/utils/request`、移动自有 `utils/http`，属两套） |
| 2 | 鉴权与登录态键规范 | **共享（工厂）** | `packages/shared/constants/storageKey`（键名按 `hrm:{client}:token` 生成） | 两端须**同浏览器可并存**，键名是共同契约；现值 `hrm_demo_*` 属演示命名，须去 demo 化并**双键兼容读取**（避免已登录用户被登出） |
| 3 | 错误码 / 角色 / 字典 / 权限判定 / 数据范围 / 分页 / 脱敏 / 时间 / 文本 | **共享** | `packages/shared`（`constants` + `domain`） | 纯函数、零 UI、两端同口径；多份必漂移 |
| 4 | 设备弱信号采集 / 客户端日志采集 | **共享** | `packages/shared`（`device` / `clientLog`） | 上报口径（白名单脱敏 / 环形缓冲 / 限流）必须单点 |
| 5 | **类型 / 契约**（接口入参出参 `JSDoc @typedef`） | **共享** | `packages/api-client/contracts` | 契约单一真源；**不引入 TS 构建链**（工程无 TS，沿用现状） |
| 6 | Design Token | **共享** | `packages/tokens`（见决策一） | 两端同一 Token 语汇（「同源不同语气」） |
| 7 | Mock（路由 + 引擎 + 数据层） | **共享（独立包）** | `packages/mock` | Mock 是**唯一行为规格**；按端复制将产生漂移，摧毁契约单一真源 |
| 8 | **路由表** | **必须独立** | 各端 `src/router` | 端内页面/角色/首页分流互不相同；共享即回到「一套路由两端跑」 |
| 9 | **页面 / 视图** | **必须独立** | `apps/staff-h5/src/views` / `apps/boss-h5/src/modules` | 用户诉求本体 |
| 10 | **样式入口（平台层）** | **必须独立** | 各端 `src/styles/*`（平台覆盖 + `mobile.scss`） | Element/Vant 变量覆盖与尺寸类 L3 属合法平台差异 |
| 11 | **构建产物 / `index.html` / 品牌常量** | **必须独立** | 各端 `dist` / `index.html` / `constants` | 独立发布、独立品牌的前置 |
| 12 | `stores/auth` | **必须独立** | 各端 `src/stores/auth.js` | `homePath` / 角色分流 / 端固定化后语义不同 |
| 13 | `api/*`（端点声明壳） | **各端自带（薄壳）** | 各端 `src/api` | 两端**可见范围不同**（端-接口矩阵），按端裁剪即天然各一份 |
| 14 | 中立共享页 `MessagePage.vue` / `NoticeReader.vue` | **共享（已裁定）** | `packages/shared/ui`，**B3 落地**（随首个拆出的端一并提升） | 现状位于域目录之外被两端共用；**主智能体已裁定取「提升为中立共享页」（§7.2 B-3）**，非各端各建 |
| 15 | `/boss/kpi/:employeeId` → `views/staff/kpi.vue` 跨域直引 | **必须消解（已裁定）** | `views/staff/kpi.vue` 提升为中立共享页（`packages/shared/ui`，**B3 落地**），`/boss/kpi` 改引之并以 **props 注入**；**B4 验收「跨域直引残留 = 0」** | 拆为两个工程后该相对引用**编译期即断**（split-plan 风险 R8）；**主智能体已裁定取提升方案（§7.2 B-3）** |
| 16 | 通用展示组件（`PageState` / `StatusTag` / `SlaTag` / `Badge` / `ListItemCard` / `MonthPicker` / `PageNav` 等） | **谨慎共享**（须 UI/UX 先冻结清单与 Tokens 归属） | 候选 `packages/shared/ui` | 入共享包**硬前提**：不得 import `stores/` / `api/` / mock，数据一律 props 注入（存量越界须先消） |

> 共享包**硬边界**：`packages/*` **禁**依赖任何端（含 `@admin`）、**禁**依赖 Element Plus / Vant（`api-client` 仅 `peerDependencies: axios`）。

### 3.6 Mock 与门禁如何保持不弱化

| 门禁 | 现状 | 拆分后 | 不弱化判定 |
| ---- | ---- | ---- | ---- |
| `verify:mock` | 直接 import `src/shared/mock/*`，**基线以实跑冻结值**（用户口径 942；split-plan 记 919，见 §1.3-3） | 随 `packages/mock` 提升到 workspace 根脚本，import 改为包名 | **断言数 ≥ 冻结基线**，且须打印基线来源 |
| `verify:mobile` | 48 | **按端各持一份同源断言子集**（员工路径 → `apps/staff-h5`；管理端路径 → `apps/boss-h5`；登录/分流等共用项两端各一份） | **每端 ≥ 其同源断言子集下限**（分端下限由「拆分前后断言清单对照表」逐项确定，**先出表后开工**）；且 **两端合计 ≥ 48**；「拆一份变两份」只许增加不许丢失 |
| `verify:tokens` | 扫 `hrm-demo` 两文件 | 扫 `packages/tokens` + 各端平台层（§2.4） | 目标数增加、校验项不减 |
| `build` / `build:prod` | 单工程 | **每端各一套** | 每端两条构建均须通过；`build:prod` 产物**无 Mock chunk**（静态检索） |
| `lint` / `lint:style` / `test` | 单工程 | 每端各跑 | 规则集不得少于现状（含既有 ESLint 边界规则 1–13） |
| `e2e`（Playwright） | 单 dev server（5188）双视角 | 各端一份 config + 各自 `baseURL`；`02-pc-nav` → web，`03-mobile-nav` / `04-forms` / `05-render` / `06-viewport` **拆为 staff / boss 两份**；`00-dev-server` / `07-network` 跨端契约**按端各持一份** | 断言**只可平移或增强**；`--config` 唯一入口纪律保留 |

> **新增端是否需各自门禁？** **是。** 两端各自持有 `build` / `build:prod` / `lint` / `lint:style` / `test` / `verify:mobile`（子集）/ `e2e` 脚本；workspace 根提供 `verify:mock` / `verify:tokens` 两个**跨端**门禁（单点，不复制）。
>
> **纪律（反模式 A06）：** 迁移验收资产时**严禁为通过而修改断言**；门禁若劣化须**归因并登记**，不得静默改基线。新增 e2e spec 一律「只新增、不改既有断言」。
>
> **NFR 量化（v2 补，必改项 9；由测试工程师实跑出具，本 ADR 不预填数字）：**
> - **构建耗时 / 产物体积对照表：** 拆分前后各跑一轮并登记对照值——① 多站点构建总耗时（拆分后构建次数 ≈ 3 端 × 2 模式 + `admin` + `demo`）；② 单端 `build:prod` 产物总体积与最大 chunk；③ 各端 e2e 执行时长。**判定口径：** 不作硬指标，**但不得出现单端产物体积显著劣化（如因重复打包共享依赖导致 token/mock 双份进入产物）**；若劣化须归因并登记（登记落 `update-log.md` 或测试报告）。
> - **`verify:mobile` 分端下限对照表：** 先出「拆分前后断言清单对照表」（逐条列出原 48 条 → 归属端 + 新计数），据表确定每端下限；**未出表不得开工 B3 / B4**。

### 3.7 拆分批次、唯一切换点、验收标准与回滚点

> **原则：** 先抽共享包 → 再上移真源 → 逐端拆出 → 最后壳与发布切换；每批结束线上仍可用。**本机 Node 可用，Java / Maven / Android SDK 不可用**，涉 Java / Android 的验收**标注「未运行」**；**运行期验收收敛到具备 Android SDK / JDK 的构建环境另行执行**（**全仓无 CI 配置，禁止写「收敛到 CI」**，必改项 10）。

| 批 | 名称 | 前置 | 验收标准（可判定） | 回滚点 |
| - | ---- | ---- | ---- | ---- |
| **B0** | **裁定冻结** | — | §7 的 D 系列与 A/B 系列裁定结论落 `update-log.md`；未裁定项对应批次**不得开工**（P1 口径先行 / A04）。**B0 冻结项须含：③→② 收敛期限「不得晚于 B2 完成」（A-4）、`hrm-admin` 消费方式取 ①（A-2）、`as` 退役触发条件（B-4）、迁移期与终态统一子路径（B-5）** | 不涉变更 |
| **B1** | **建 workspace + 抽共享包（行为不变）** | B0 的 D3（仓库粒度）/ D6（共享包边界） | ① workspace `npm ci` 通过；② `hrm-demo` 改 `file:` 引用后 `verify:mock` ≥ 冻结基线；③ `verify:mobile` ≥ 48；④ `verify:tokens` 绿；⑤ `build` / `build:prod` / `lint` / `lint:style` / `test` / `e2e` 全绿；⑥ `git diff -- hrm-dev/hrm-admin` 为**空** | 删除 `hrm-clients/` + 还原 `hrm-demo` import（纯新增 / 机械改动，反向 diff 即回滚） |
| **B2** | **R-0 真源上移（决策一落地）** | B1 | A1–A3、A5、A6 断言全绿；**A4 删除演练四步通过**（移出/删除 `hrm-demo` + `npm ci` 清装 + 清 `dist` + 产物级断言：各 dist 内 `hrm-demo` = 0、`build:prod` 无 Mock chunk）；`hrm-admin` 对比度取值来自真源；全仓 `tokens.base.scss` 计数 = 1；**③ 已退出、② 生效**（A-4「不得晚于 B2 完成」） | 还原 Token 归属（反向 diff）+ 删 `packages/tokens` + 恢复 `verify:tokens` 旧扫描路径 |
| **B3** | **拆员工端 `apps/staff-h5`（驿站助手）** | B2（**D9 已闭环**：后端多端会话 `hrm:session:{sid}` + 互踢粒度降为「端+设备」**已实现**，硬前置撤除）；B-3 共享页提升落地 | ① 独立 `build` / `build:prod` 通过；② 员工路径 e2e 平移通过（**含「拆分前后断言清单对照表」**）；③ `clientType=STAFF` 登录成、`as=station` 兼容读仍成；④ 无跨端源码复制（静态检索）；⑤ 两端并存不互踢（D9 已闭环，本批回归此行为） | **回滚（v3 补全，实测）**：① 删除 `apps/staff-h5/`；② 还原 `hrm-demo` **5 个文件**（`src/mobile/views/message/{MessagePage,NoticeReader}.vue`、`src/mobile/router/index.js`、`vite.config.js`、`vitest.config.mjs`），并**复原** `src/mobile/views/staff/kpi.vue`（原改为 `src/mobile/views/kpi/`）；③ 还原 `hrm-clients/e2e-utils/harness.js`；④ 撤销 `packages/shared/src/ui/` 中立页提升（`MessagePage` / `NoticeReader` / `KpiDetail`）。**Nginx 未改、`mobile.html` 旧入口未动、无发布动作 → 线上零影响**（由「提交 `apps/staff-h5`」一句补全为**逐项可执行反向 diff**） |
| **B4** | **拆管理端 `apps/boss-h5`（驿站精灵）** | B3；B-3 已裁定（共享页 **B3 已落地**） | ① 同 B3；② `clientType=BOSS` 登录成、`as=boss` 兼容读仍成；③ **跨域直引残留 = 0**（`/boss/kpi` 已改引 `packages/shared/ui` 中立页） | **回滚（v3 补全，实测）**：① 删除 `apps/boss-h5/`；② 还原 `hrm-clients/package.json`（去 `verify:tokens` 的 boss 目标与 `e2e:boss`）+ `hrm-clients/package-lock.json`。本批**未改** `hrm-demo` / `apps/staff-h5` / `e2e-utils`（与 B3 不同，**无 `hrm-demo` 反向 diff**）；**线上零影响** |
| **B5** | **拆网页端 `apps/web`** | B2；D2 裁定 | 同 split-plan §5.1 S2：独立构建；`build:prod` 产物无 Mock chunk；PC 用例平移；`hrm-demo` 未动 | **回滚（v3 补全，实测）**：① 删除 `apps/web/`；② 还原 `hrm-clients/package.json`（去 web 目标与 `e2e:web`）；③ 还原 `hrm-clients/package-lock.json`（**+44 包**）；④ 可选 `npm ci` 清理 workspace 依赖。**无需还原** `hrm-demo` / `hrm-admin` / Nginx（零改动、线上零影响） |
| **B6** | **安卓壳（两 APK）** | B3 + B4；D5 裁定 | **静态审查（可判定）**：① `H5_URL` 无硬编码真实域名；② 两壳应用名 / 包名区分为**占位**（`com.example.hrmwebview.boss` / `.staff`）；③ 桥接方法名未变。**运行期验收（壳加载 / 桥接 / HTTPS / 混合内容）标注未运行，收敛到具 Android SDK 环境，本批次不声称可交付**（必改项 10） | **回滚（v3 补全，实测）**：① 还原 `app/build.gradle`（恢复 `buildTypes` 内两处 `H5_URL`）；② **删除 `app/src/staff/`、`app/src/boss/`**（flavor 源集）；③ 还原 `app/src/main/res/values/strings.xml`、`local.properties.example`、`README.md`、`BUILD.md`。**完全可逆、未发布、无线上影响** |
| **B7** | **发布切换（★ 唯一切换点）** | B3 / B4 / B5 产物就绪；**P0.5 安全结论 → 主智能体三步授权 → 变更前备份**；**变更载体明确**（§5.4：载体为**现网 `courier-nginx`**，真源不在仓库、以运维记录为准；**演示容器 `deploy/docker-demo/nginx.conf` 不在本批变更范围**） | ① 新增 location 后三端入口 `curl` 200；② 深链刷新落自身入口；③ 缺失资源 404（不回退 HTML）；④ `.map` 不可公开下载；⑤ HTTPS 强制；⑥ **既有 location（`/`、`/api/`、`/admin/`、`/hrm-api/`）行为逐条不变**；⑦ 新旧入口**并存可达**；⑧ **新站点根与既有 root/alias 隔离**（新站点根独立，不共用 `location /` 的 root，防产物互相覆盖） | 删除新增 location + 还原 `nginx.conf.<时间戳>` + `nginx -t` + `reload` |
| **B8** | **演示站处置** | B7 通过；D1 裁定；**B-4 退役触发条件满足** | 三端全部可用；Mock 能力确认由 `VITE_MOCK_ENABLED` 构建模式承接；`as` 兼容读与旧入口**按 B-4 触发条件退役**；`hrm-demo` 若退役则 tag 保留；`portal/main.js` 与 `deploy/docker-demo/portal.html` 入口链接同步（见 §5.2） | 恢复 Nginx 指向 + 重新拉起演示容器（既有镜像 tag） |

**★ 唯一切换点 = B7。**
- B1–B6 全部**不触碰现网**（只新增工程 / 只本地构建 / 只静态审查），故可独立验收、独立回滚。
- B7 是**唯一**改现网 Nginx 的一步，且策略是「**只增不改**」：新增 `location /web/` `/staff/` `/boss/`，**不动** `location = /` 与 `location /` 及既有全部 location。
- 因此**迁移期线上零中断**：现网用户（**现网主域名正在使用的路径；域名值见运维记录，不落本文**）所依赖的既有 location 逐条不变；旧 `mobile.html?as=` 入口与旧壳 APK 继续可用；新入口仅追加。
- **零中断的前提（v2 显式化）：** 旧路径由**演示容器旧产物**继续提供 → 故迁移期须**冻结演示站发布**（见 §6 R新-1）；且新站点根须与既有 `location /` 的 root/alias **隔离**（B7 验收 ⑧）。
- **切换期观测判据（v2 补，必改项 9；任一命中即按 B7 回滚）：** ① 新入口 4xx/5xx 率超阈值（阈值由运维据现网基线定）；② 深链刷新失败（落不到自身入口）；③ `.map` 可公开下载探测命中；④ 新入口资源缺失回退 HTML 致白屏。观测数据源：现网 Nginx access/error log（**不引入新监控组件**）。
- **`as` 退役节奏（B-4，v2 补；v3 回填执行期事实）：** 计时起点 = **B7 上线日 2026-09-25**；B8 依 §3.3**三项触发条件**判定是否退役（不满足即顺延一个周期，**不得无限期保留**）。**〔v3 回填〕B8 批实际执行（2026-09-25）：** 依 D1 与 §3.3 三项条件，当前 **①③ 均未满足**（① 未满 1 个发布周期；③ 旧壳 APK 未完成一个周期）→ **旧入口（`mobile.html?as=`）与 `as` 兼容读保留**；本批只做**门户收敛 + 冻结发布 + 文档回填 + 判定期登记**（依据 `update-log.md` B8 条目）。

---

## 4. 合并评估：是否同一次结构变更（自查项）

**结论：是 —— 合并为同一次结构变更（同一 workspace 落地），分批执行。** 自查对照：

| 检查项 | 结论 |
| - | ---- |
| 是否复用了既有 [multi-client-split-plan.md](multi-client-split-plan.md)？ | **是**：本 ADR 复用其 §0 事实基线、§2 粒度选型（方案 A workspace）、§3 共享层判定、§5 迁移路线与 §6 风险 / §6.3 安全清单；**不另起炉灶**。其附录 A 的 ADR-SPLIT-01～05 由本文吸收为「同一变更内的子决策」，不再单独立项。 |
| 是否避免两轮迁移？ | **是**：真源上移（决策一）与三端拆包（决策二）共用同一 workspace / 单 lockfile / 同一次依赖树重构；R-0 作为 B2 提前独立落地，无需等三端全拆完。 |
| 是否引入新的重复建设？ | **否**：Token / Mock / 请求层 / 契约 / 领域纯函数均**收敛为单份**；各端只保留合法差异（路由 / 页面 / 平台样式 / 品牌 / 产物）。 |
| 文件落点 | 本文件（唯一 ADR 文件）；`multi-client-split-plan.md` 作为配套详设**保留不改**，本文以章节号引用之。 |

---

## 5. 影响面清单

### 5.1 工程

| 工程 | 影响 | 说明 |
| - | ---- | ---- |
| `hrm-dev/hrm-clients/`（**新增**） | 新增顶层工程目录 | workspace 根 + `packages/{tokens,shared,api-client,mock}` + `apps/{web,staff-h5,boss-h5}`；**结构变更（C 档）** |
| `hrm-dev/hrm-admin/` | 改：`src/styles/**`（新增 `tokens.scss`）、`vite.config.js`（消费真源 / 别名）；`package.json`（若补 lint 门禁） | **生产工程，须主智能体 Review**；不改业务逻辑、不改 `hrm-server`、不改契约 |
| `hrm-dev/hrm-demo/` | 改：Token 归属、import 路径、入口与 `verify:*` 脚本提升；迁移期**冻结功能**（仅 P0 修复） | §12.1 允许（Demo 只读引用 admin / 不反向污染） |
| `hrm-dev/hrm-android-shell/` | 改：`app/build.gradle`（`H5_URL` 构建参数注入、双壳 buildConfig）、`res/values/strings.xml`（应用名）、`AndroidManifest.xml`（如需）；`local.properties` **绝不入库** | 未编译验证 |
| `hrm-dev/deploy/` | **分两类，不得混写（v2 修订，必改项 6）：** **(a) 演示容器内配置** `docker-demo/nginx.conf`——其 root 服务**演示站自身**，**B7 不需要同步**（B7 不改演示容器）；**(b) 模板** `nginx.conf.example`（产线样例，非现网真源）；**(c) 现网变更载体** = 现网 `courier-nginx`，其**配置真源不在仓库**，B7 实施时**以运维记录为准**（须在 B7 前由运维核实并在 `update-log.md` 登记载体路径 / 管理脚本）。`deploy.sh` 视需要；**执行属 C 档** | 现网 Nginx 变更须安全评估 + 三步授权 |
| `hrm-dev/hrm-server/` | **零改动** | 端类型仅产品/审计约束，不参与鉴权；`as` 退役无需后端配合（§3.3） |
| `hrm-dev/sql/` | **零改动** | 无表结构变更 |

### 5.2 关键文件

| 文件 | 影响（**改 / 不改 + 归属批次**） |
| - | ---- |
| `hrm-demo/src/shared/styles/tokens.base.scss` | **改（迁出）**：真源上移；迁移期可保留为转发/引用，终态删除。归属 **B2** |
| `hrm-demo/scripts/gen-element-tokens.mjs` | **改（迁出）**到 workspace 根；扫描目标改多目标。归属 **B1 / B2** |
| `hrm-demo/src/pc/main.js` / `src/mobile/main.js` / `vite.config.js` | **改**：入口与别名、`dedupe`、`input` 随迁移调整（**`resolve.dedupe` 必须保留**）。归属 **B1–B5** |
| `hrm-demo/mobile.html` | **不改（迁移期保留）**；终态由 `apps/{staff,boss}-h5/index.html` 取代。归属 **B8** |
| `hrm-demo/index.html`（端选择页） / `hrm-demo/pc.html`（v2 补） | **迁移期不改**（旧入口继续可用）；**终态随 B8 演示站处置**（退役即删除，或改为指向新子路径的跳转页）。归属 **B8** |
| `hrm-demo/src/portal/main.js`（v2 补） | **改（终态）**：硬编码旧入口链接（`pc.html`、`mobile.html#/login?as=boss` / `as=station`，L24/31/38）须改指新子路径；**迁移期不改**（保旧入口可用；如需展示新入口，只许「新增卡片、不改旧卡片」）。归属 **B8**；**执行角色 = 前端工程师**。**〔v3 回填〕已执行（B8，2026-09-25）**：三张卡片实际链接已为 `/web/`（**L28**）、`/boss/`（**L35**）、`/staff/`（**L42**）——**行号自 L24/31/38 偏移**；同时剔除「已预填账号」失效 note 文案（`name` 字段逐字未变，**e2e 断言对象不受影响**） |
| `hrm-demo/src/mobile/router/index.js` | **改**：拆为两端各自 router；跨域直引与中立共享页**已裁定归位**（§3.5 第 14 / 15 项，**B3 落地**）。归属 **B3 / B4** |
| `hrm-admin/src/styles/index.scss` | **改**：接入 Token（字面量收敛）；`hrm-admin/src/styles/tokens.scss`（新增）。归属 **B2** |
| `hrm-dev/deploy/docker-demo/portal.html`（v2 补） | **改（终态）**：内含硬编码绝对链接 `/pc.html`、`/mobile.html?as=station`、`/mobile.html?as=boss`（L30–32），**是旧入口的真源之一**；须与 `portal/main.js` **同步改**（或随演示站退役）。归属 **B8**；**执行角色 = 运维**（演示容器部署资产）。**〔v3 回填〕已执行（B8，2026-09-25，提交 `04975fd`）**：现状已改为 `/web/` `/staff/` `/boss/`（**portal.html:31-33**，原记 L30–32 **行号随之回填**），与 `portal/main.js` 保持一致；线上宿主 `/data/www/hrm-demo/dist/index.html` 已**原地覆盖**（备份 `index.html.bak.<时间戳>`）并经 `docker cp` 进演示容器。**注意：演示容器无挂载、门户为镜像内置，容器重建会回退**（宿主 `dist` 已同步更新，重建即生效） |
| `hrm-dev/deploy/docker-demo/deploy-demo.sh` / `docker-compose.yml`（v2 补） | **迁移期不改**（演示容器 `hrm-demo-static` 保留）；**B8** 视演示站处置决定是否停容器 / 改 compose |
| 门禁资产：`hrm-demo/scripts/verify-*.mjs` / `e2e/**` / `playwright.config.js`、`.husky/pre-commit`、`lint-staged.config.js`、`eslint.config.js`、`stylelint.config.cjs`、`commitlint.config.cjs`、`vitest.config.mjs`（v2 补） | **改**：随 workspace 提升到根（**B1**），各端继承落地（**B3–B5**）；**规则集不得少于现状**（§3.6） |

### 5.3 门禁

见 §3.6；核心不变式：**`verify:mock` ≥ 冻结基线（用户口径 942，须实跑确认）、`verify:mobile` 每端 ≥ 其分端下限且两端合计 ≥ 48、`verify:tokens` 绿、每端 `build`/`build:prod`/`lint`/`lint:style`/`test`/`e2e` 全绿、`build:prod` 无 Mock chunk、断言只增不减。** 另：**「拆分前后断言清单对照表」与「构建耗时 / 产物体积对照值」由测试工程师实跑出具**（§3.6 NFR 量化）。

### 5.4 部署

| 项 | 影响 |
| - | ---- |
| Nginx（**v2 拆分载体，必改项 6**） | **变更载体 = 现网 `courier-nginx`**（配置真源**不在仓库**，以运维记录为准；B7 前由运维核实并登记）；**演示容器 `deploy/docker-demo/nginx.conf` 不在本批变更范围**。**变更内容：** **新增** 3 条 location（`/web/` `/staff/` `/boss/`）→ 各端**独立站点根**；**既有 location 逐条不变**；**新站点根与既有 `location /` 的 root/alias 隔离**（不共用 root，防产物互相覆盖）；`.map → 404`；`index.html` `no-store`；缺失资源 404 不回退 HTML。**〔v3 回填〕必改项 6 最终答案（执行期事实）：** 变更载体实为宿主 **`/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`**（`ro` 挂载进 `courier-nginx`）；**关键技术点：单文件 bind mount 必须原地改写（`cat >`）保 inode，用 `mv` 换文件容器看不到**；变更前备份 `nginx.conf.<时间戳>`，**未改动任何既有行** |
| 现网配置真源 | **不在仓库**（仓库仅有模板 `deploy/nginx.conf.example` 与演示容器配置 `deploy/docker-demo/nginx.conf`，二者均非现网真源）；B7 前须由运维核实现网配置文件路径 / 管理脚本并登记 `update-log.md`。**〔v3 回填〕已闭环：** 现网载体路径 = 宿主 **`/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`**（`ro` 挂载进 `courier-nginx`，容器内可见）；核实方法（`docker inspect` 查挂载 / `nginx -T` 看生效配置）与载体路径已登记 `deploy.md` §11 与 `update-log.md`（B7 条目） |
| 站点根目录 | 原方案示例：新增 3 个（如 `/www/wwwroot/hrm-web`、`hrm-staff`、`hrm-boss`），与现有目录**不重叠**、与既有 root **隔离**。**〔v3 回填〕实落偏差（正式确认）：** B7 实际落点为宿主 **`/data/www/download/hrm-clients/{web,staff,boss}`**（三端文件数 **167 / 80 / 97**，`map=0`）——**非**本示例，亦**非**手册规划的 `/data/www/hrm-clients`。**原因：** `courier-nginx` 仅挂载 `/data/photos`、`/data/www/apk`、`/data/www/download`，规划路径**容器内不可见**；为**不新建挂载 / 不重建容器**（避免中断 80/443），改用**已挂载且路径一致**的目录。**结论：** 该目录为 `ro` 挂载、只读服务静态资源无影响，且仅 `location = /download` 精确匹配会触达该目录 → **不新增暴露面**；偏差已登记 `deploy.md` §11 与 `update-log.md`，**本 ADR 予以确认** |
| 容器 / 进程 | 演示容器（`hrm-demo-static`）迁移期保留；不新增后端服务；`hrm-server` 不动 |
| 回滚 | 还原 `nginx.conf.<时间戳>` + `nginx -t` + `reload`；镜像 tag 保留 |

### 5.5 安卓壳

见 §3.4；要点：远程 https 加载不变、两 APK（包名占位 `.boss` / `.staff`）、`H5_URL` 构建参数注入、桥接名不变、`local.properties` 不入库、**未编译验证**；**运行期验收标注未运行、收敛到具 Android SDK 环境（全仓无 CI 通道），本批次不声称可交付**。

---

## 6. 回滚路径总表

| 批次 | 回滚动作 | 可逆性 | 线上影响 |
| - | ---- | ---- | ---- |
| B1 | 删除 `hrm-clients/` + 还原 `hrm-demo` import（反向 diff） | 完全可逆 | 无 |
| B2 | 还原 Token 归属 + 删 `packages/tokens` + 恢复 `verify:tokens` 旧路径 | 完全可逆 | 无 |
| B3 / B4 / B5 | 删除对应 `apps/*` 目录 **+ 还原 `hrm-clients/package.json` / `package-lock.json`**；**B3 另**还原 `hrm-demo` 5 文件 + `e2e-utils/harness.js` + 撤销 `packages/shared/src/ui`（**详 §3.7 各批回滚点**，v3 补全） | 完全可逆 | 无（Nginx 未改） |
| B6 | 还原 `build.gradle` / 壳配置（反向 diff）**+ 删除 `app/src/staff/`、`app/src/boss/` + 还原 `strings.xml` / `local.properties.example` / `README.md` / `BUILD.md`**（**详 §3.7 B6**，v3 补全） | 完全可逆 | 无（未发布 App） |
| B7 | 删除新增 location + 还原 `nginx.conf.<时间戳>` + `nginx -t` + `reload` | 可逆 | 仅新入口消失，旧入口未受影响 |
| B8 | 恢复 Nginx 指向 + 重新拉起演示容器 | 可逆（镜像 tag 保留） | 演示站恢复 |

> **回滚纪律：** 一律用**反向 diff / 删除新增件 / 还原备份文件**；**禁** `reset --hard` / `clean -f` / `push --force`（D 档，不接受授权）；`hrm-demo` 退役后如需回滚，靠**镜像 tag + 源码 tag**，不得依赖未提交工作区。

### 6.1 本 ADR 新增风险登记（v2 补，必改项 7）

| # | 风险 | 触发条件 | 影响 | 缓解 |
| - | ---- | ---- | ---- | ---- |
| **R新-1** | **演示站产物被覆盖 → 旧入口失效** | B7 之后**重新构建 / 发布 `hrm-demo` 并覆盖旧 `dist`**（或演示容器重建时拉取新产物） | 破坏「只增不改 → 零中断」前提：旧 `mobile.html?as=` 入口（`location /` 的 `try_files` 回退目标）失效 | **迁移期冻结 `hrm-demo` 发布**（仅允许 P0 修复，且须回归旧入口）；或对旧产物做**只读快照**（备份 `dist` + 冻结镜像 tag）；发布前核对 `git diff` 与备份点 |
| **R新-2** | **无 CI 通道，构建 / 壳验收无法机器化** | 需 Android / Java 构建验收时 | 「收敛到 CI」不可达，验收只能人工或异地构建 | v2 措辞已改为「静态审查 + 标注未运行；运行期验收收敛到具 SDK 环境，**本批不声称可交付**」（§3.4 / §3.7 B6）；如需机器化，属**新增 CI 基建**，须另立任务并经主智能体授权 |

### 6.2 既有缺陷登记（**非本方案引入**，v3 回填）

> 下列两处为 **B7 变更前即存在**的既有死链，**非本方案（B7 / B8）引入**；本轮属 **C 档显式修复**（载体 = 现网 `courier-nginx`，须 P0.5 安全结论 + 主智能体 §10.3 三步授权）。登记以备追溯，**不改变本方案任何结论**。

| # | 既有缺陷（非本方案引入） | 本轮处置（C 档显式修复） | 备注 |
| - | ---- | ---- | ---- |
| 旧-1 | `/admin/` 恒 **500**：`alias` 指向容器内**不存在**的目录 + `try_files` 形成**内部重定向环** | 改为 **`return 301 /web/`**（一期 PC 端已由 `apps/web` 承接） | **既有死链、非本方案引入** |
| 旧-2 | `/download` **404**：宿主 `/data/www/download/index.html` 不存在 | **只增宿主 `/data/www/download/index.html` 下载页**（**未改 Nginx**） | **既有死链、非本方案引入** |

---

## 7. 需主智能体 / 用户裁定事项

> 未裁定项对应的批次**不得开工**（P1 口径先行 / 反模式 A04）。延续 split-plan §7 的 D 编号，新增项以 A/B 前缀区分。

### 7.1 继承自 split-plan（编号不变）

| # | 议题 | 本 ADR 建议 | 影响批次 |
| - | ---- | ---- | ---- |
| D1 | `hrm-demo` 迁移后处置 | 退役归档（演示能力由构建模式承接） | B8 |
| D2 | `apps/web` 与一期 `hrm-admin` 关系 | 短期保留 `@admin` 只读引用，长期收敛另立 ADR | B5 |
| D3 | 仓库粒度 | 方案 A：workspace 单仓多工程 | B1 |
| D4 | 三端发布路径与最终归属 | 迁移期与终态**均用子路径**（`/web/` `/staff/` `/boss/`），避免二次切换（**主智能体已裁定，§7.2 B-5**） | B7 |
| D5 | 安卓壳产物形态 | **两个 APK**（「两端都是壳 + H5」为**用户口径，待书面裁定归档**；仅剩「两 APK vs 单壳双入口」待定） | B6 |
| D6 | 共享包边界 | `tokens` + `shared` + `api-client` + `mock` 四包；通用 UI 组件提升另议 | B1 |
| D8 | 生产 API 基址 | **已闭环**（现值 `/hrm-api/v1`，见 §1.3-2）；仅需运维核实 Nginx 前缀一致 | B7 |
| D9 | 拆分与后端会话多端化先后 | **已闭环**：后端多端会话（`hrm:session:{sid}` 多会话 + 互踢粒度降为「端 + 设备」）**已实现**，B3 硬前置**撤除**（B3 仍须回归「并存不互踢」行为） | — |
| D10 | e2e / verify 资产归属 | 按端各持一份 + 跨端门禁单点（workspace 根） | B1–B5 |

### 7.2 本 ADR 裁定项（**主智能体已裁定**，v2）

> 下列结论落 `update-log.md` 即 B0 冻结生效；未裁定项对应批次不得开工。

| # | 议题 | 选项 | **主智能体裁定结论** | 落地位置 |
| - | ---- | ---- | ---- | ---- |
| **A-2** | `hrm-admin` 如何消费真源 | ① 相对路径 `@use`（不改 `package.json`）；② admin 镜像 + 跨工程一致性校验 | **取 ①**（同意用相对路径 `@use`，**不改 `package.json`**） | §2.2、§3.7 B2 |
| **A-4** | ③（admin 局部覆盖）→ ② 的**收敛期限** | ① 与 B2 同批；② B2 后 N 迭代内 | **取 ① 并设期限：不得晚于 B2 完成** | §2.1、§2.2、§3.7 B0/B2 |
| **B-3** | 共享页与 `/boss/kpi` 跨域直引归属 | ① 提升为 `packages/shared/ui` 中立页；② 两端各建 | **取 ①**：`MessagePage` / `NoticeReader` 与 `views/staff/kpi.vue` 提升为中立页，**B3 落地**；`/boss/kpi` 改引中立页 + **props 注入**；**B4 验收「跨域直引残留 = 0」** | §3.5 第 14/15 项、§3.7 B3/B4 |
| **B-4** | `as` 参数退役节奏 | ① ≥ 1 个发布周期后退役；② 永久保留 | **取 ①，保留 ≥ 1 个发布周期**；**具体退役触发条件见 §3.3**（三项全满足方于 B8 退役；不满足顺延，**不得无限期保留**） | §3.3、§3.7 B8 |
| **B-5** | 迁移期与终态 URL 是否统一 | ① 统一子路径；② 临时路径后切 | **取 ①**：**统一为子路径 `/web/` `/staff/` `/boss/`** | §3.3、§3.7 B0/B7 |

### 7.3 需**用户**（非主智能体）裁定 —— **下列两项仍为「待用户裁定」**

> ⚠ **本 ADR 未擅自决定以下事项**；标注「待用户裁定」，未裁定前对应影响面**不得实施**。

| # | 议题 | 待裁定要点 | 状态 |
| - | ---- | ---- | ---- |
| U-A | 「驿站精灵」与「驿站助手」的**品牌与视觉是否各自独立定稿** | 「是否允许两端视觉分叉 / 分叉到什么程度」属产品/视觉方向；**含 `hrm-admin` 生产端主色是否变更**（该换色属**生产可见视觉变更**，tech-review 必改项 2）需 ≥2 方案对比 + 用户决策 | **待用户裁定** |
| U-B | 是否为 `hrm-admin` 补**最小门禁**（lint 等） | `hrm-admin` 当前无 lint / 无 verify 脚本；是否纳入本批次属工程投入决策 | **待用户裁定** |

---

## 8. §12「演示与隔离工程硬约束」需修订条款（单列，交主智能体裁定）

> 本节**只列需修订的条款与理由**，不代改规则文件（`.trae/rules/` 属禁改范围与规则唯一真源，M06）。

| 条款 | 现文（摘要） | 需修订点 | 理由 |
| - | ---- | ---- | ---- |
| §12.1 | 「`hrm-admin` 与 `hrm-server` 零改动：Demo 只经 `@admin` **只读**引用一期源码，不复制源码、不加开关；删除 `hrm-demo/` 即可完全回滚」 | **补限定语**：约束对象为 **Demo**（Demo 不得改 `hrm-admin`/`hrm-server`）；`hrm-admin` 为生产工程，**可改但须主智能体 Review**；并补**抽象约束**「**任何端不得再自定义 Token 真源；真源唯一且不得位于演示工程**」（**不写实现路径**，具体位置见本 ADR，避免实现细节漂移规则真源） | 现文易被误读为「`hrm-admin` 不得被改动」（tech-review §1.2 已裁定属**表述过宽**）；真源上移后须把「真源唯一」写进硬约束，防后人再在端内建 Token；**写实现路径会使规则真源随实现漂移**（评审 §5 建议措辞降耦） |
| §12.3 | 「Mock 层只允许由 `pc/main.js`、`mobile/main.js` **两个入口**以动态 `import()` 装配」 | 改为「由**各端入口**（当前为三端各自 `main.js`，拆分后为 `apps/*/src/main.js`）装配，装配必须先于 `mount`」 | 拆分后入口由 2 变 3（`apps/*`），条款文字须与结构一致 |
| §12.5 | 「双构建不可混用：`build` = 演示态（含 Mock，**三入口**）；`build:prod` = 生产态（剥离 Mock，**仅 pc/mobile**）」 | 入口枚举随拆分更新（三端各自构建），**语义不变**（演示态含 Mock、生产态剥离 Mock） | 结构变更后入口名与数量变化 |
| §12.7 | 「`base` 与 `file://` 离线包存在结构性冲突……登记 `TODO(扩展)`，未实施」 | **无需修订**，但补一句「移动端一律**远程 https 加载**；离线包未实施」以与安卓壳现状一致（现文 §12.8 有相反暗示） | 现状为远程加载（`BuildConfig.H5_URL`），与 split-plan §0.4 实测一致 |
| §12.8 | 「安卓壳：`local.properties` 含本机绝对路径，**绝不入库**；**`assets/h5/` 注入 H5 产物**；壳未编译验证前不得声称可交付」 | 「`assets/h5/` 注入 H5 产物」**与现状不符**，改为「壳以 **`BuildConfig.H5_URL` 远程 https 加载**；`assets/h5/*` 为 gitignore 占位，离线包未实施（`base` 与 `file://` 硬冲突）」 | 现文假设离线注入，实测是远程加载（`assets/h5/` 仅 `.gitkeep`）；条款与实现不一致会误导后续实现 |

> 上述修订共 **4 条修订 + 1 条补充**：**修订** = §12.1 / §12.3 / §12.5 / §12.8；**补充（无需修订本体）** = §12.7。**须主智能体裁定后由其执行**（规则文件不在本角色可写范围）。

---

## 9. 须经网络安全工程师评估清单（P0.5）

> 以下均为**安全面**，主智能体**不得凭经验放行**；须网络安全工程师按 L7 出结论（威胁建模 + 发现项 + 复现 + 修复建议），高风险项升级用户裁定。本 ADR 不含安全实质结论。

| # | 条目 | 触发原因 | 评估要点 |
| - | ---- | ---- | ---- |
| SP1 | 新增 Nginx location（`/web/` `/staff/` `/boss/`） | 生产外部暴露面变更 | 路径穿越 / 越权暴露、与既有 location 冲突、证书与 HTTPS 强制 |
| SP2 | `.map` 防护 | 源码泄露 | 三端 location 是否均拒 `.map`；sourcemap 是否 `hidden` |
| SP3 | 安卓壳 `H5_URL` 与构建参数注入（**两 APK**） | 外部暴露面 + 凭据 | 明文放行域最小化、真实域名不入库、混合内容拦截、包名/签名区分 |
| SP4 | 生产 API 基址与 CORS / 同源 | 攻击面 | 与现网 `courier-app` 的 `/api/` 是否串扰 |
| SP5 | **两端独立入口后的端准入与登录态并存** | 鉴权面（**须复核，非本 ADR 结论**） | 新入口上报 `clientType=BOSS`/`STAFF` 是否与 fail-closed 策略一致；`as` 兼容读是否引入绕过口；两端同浏览器并存是否削弱风控（**D9 已闭环**：后端多端会话已实现，复核点为「互踢粒度降为端+设备后的风控口径」） |
| SP6 | 共享包供应链 | 依赖 | `packages/*` 的 `peerDependencies` 与单一 lockfile 是否引入未知依赖 / 版本漂移；`packages/tokens` 零依赖是否成立 |
| SP7 | **真实域名入库（v1 遗留，v2 已修订）** | 规范 / 暴露面（**形式核对**） | 本文 v1 曾在 §3.7 / §10.1 落入真实域名，v2 已清除（全文无真实域名 / 凭据 / IP）；**请网络安全工程师形式核对**：① 全文再无真实域名/凭据/IP；② 判定该历史上库是否构成暴露面问题及是否需要历史清理（**本 ADR 不出安全结论**） |

---

## 10. 假设与待核实项

### 10.1 显式假设

| # | 假设 | 依据 | 不成立时的后果 | 最小验证方式 |
| - | ---- | ---- | ---- | ---- |
| H1 | 拆端后 `as` 参数可退役（后端零改动） | `parseEnd` 仅在 `clientType=H5` 时读 `as` | 若实现仍报 `H5`，则新入口须继续带 `as`，URL 终态须调整 | 读 `AuthServiceImpl` 登录三路径入参实际取用；实跑登录断言 |
| H2 | 子路径挂载对 hash 路由与壳加载均无副作用 | hash 路由对 base 不敏感（仅资源路径依赖 base） | 资源 404 / 白屏 | 各端 `build:prod` 产物在目标 base 下 `serve` + 壳/浏览器实跑 |
| H3 | 各端 `verify:mobile` 断言可拆分为互不重叠子集且合计不减 | split-plan §5.3 资产表 | 断言丢失即门禁弱化 | 测试工程师实跑并出具「拆分前后断言清单对照表」 |
| H4 | `hrm-admin` 以相对路径 `@use` workspace 包可被 Vite + sass 正常编译 | **sass 的 `@use` 按 URL（相对 / 绝对）解析成员，相对路径可用 `../` 跨目录 —— 属语言机制陈述，须以实跑为最终判据**。**〔v2 更正（N9）〕** v1 引用的「`hrm-demo` 跨工程 `@admin` 先例」**类比不成立**：该先例是 Vite `resolve.alias` + `server.fs.allow` 组合（[vite.config.js:103/125](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/vite.config.js#L101-L127)），与 sass 相对路径 `@use` **机制不同**（前者 Vite 解析层 + dev server 放行，后者 sass 编译期路径解析），**故该依据已删除** | 需改为镜像 + 校验方案（A-2 选项②，**但 A-2 已裁定取 ①，届时须回退 A-2 裁定并重评**） | 在非生产分支实跑 `hrm-admin` `vite build`（**唯一验证方式**） |
| H5 | **现网主域名（值见运维记录，不落本文）** 的现网 `location /` 由演示静态站提供、`/api/` 归 courier-app | split-plan §2.4 拓扑图 | 若不符，B7 的「只增不改」前提不成立 | **运维/主智能体核实现网 Nginx 当前配置**（本角色不读生产配置） |

### 10.2 待核实项（核实前不得写实现）

| # | 待核实 | 核实方法 |
| - | ---- | ---- |
| U-1 | ~~是否存在可用于独立子域的 DNS A 记录（若选子域方案）~~ | **已关闭**：主智能体已裁定迁移期与终态统一子路径（§7.2 B-5），子域方案不采纳，**不依赖此项** |
| U-2 | `verify:mock` / `verify:mobile` **冻结基线**的确切数字（含**分端下限**） | 测试工程师实跑 `npm run verify:mock` / `verify:mobile` 并登记；同时出具「拆分前后断言清单对照表」（§3.6） |
| U-3 | 安卓壳 WebView 的缩放 / 缓存配置（与 UI 方案 R-2、§3.4 缓存纪律相关） | 读 `hrm-android-shell` 侧 `MainActivity` / `AndroidManifest`（本机无 SDK，静态核对） |
| U-4 | 两端各自 dev server 端口（建议 5189 / 5190）是否空闲 | 本机端口探测（B3 / B4 开工前） |
| U-5 | 现网 `hrm-server` 反代前缀与版本段（D8 复核）；**现网 Nginx 配置载体路径 / 管理脚本**（B7 前置，必改项 6） | 运维核实；`hrm-demo/.env.production` 现值 `/hrm-api/v1` 为输入 |
| U-6 | **「用户口径」类陈述的书面载体**（R-0 归口、D5 两端终态、`verify:mock` 942） | 主智能体补裁定记录 / `update-log.md` 条目；未归档前相关陈述保持「待书面裁定归档」标注（§1.3-6） |

---

## 11. 回填指针（落地后须同步的文档）

| # | 文档 | 须同步内容 | 本轮 |
| - | ---- | ---- | ---- |
| 1 | `hrm-dev/docs/project-tree.md` | `TODO(扩展)` 三端独立代码 → 指向本 ADR 与 split-plan；补 `hrm-clients/` 目录树；更新 `hrm-demo` 定位 | **已完成（v3 回填）**：已补 **§2.1 `hrm-clients/` 多端 workspace 树**与端口 / base 表（5191 `/web/`、5189 `/staff/`、5190 `/boss/`） |
| 2 | `hrm-dev/docs/update-log.md` | 追加本 ADR 条目（**v1 新增文档**、**v2 按评审必改项修订**、**v3 非主干修订（落地后事实回填 + 回滚点补全）**，均无源码改动）；并登记 B0 冻结裁定结论与「用户口径书面载体」 | **须追加（由主智能体执行）** |
| 3 | `.trae/rules/项目规则1.md` §12 | §8 所列 **4 条修订 + 1 条补充**（修订：§12.1 / §12.3 / §12.5 / §12.8；补充：§12.7） | **主智能体裁定后执行** |
| 4 | `hrm-dev/docs/ui-experience-optimization.md` | 其 §7.1 R-0 已由本 ADR 收敛，补指针；**并声明其 §7.1 R-0 选项② 定性无误**（本 ADR **v2 已撤回** v1 的「需修正」主张，见 §1.3-5；不再要求改动该文定性） | 待原作者/主智能体 |
| 5 | `hrm-dev/docs/tech-review-ui-experience.md` | 必改项 1（R-0 兜底路径不闭合）与必改项 2（admin 换色决策）以本 ADR + A-2/A-4 + U-A 闭环为解；**注：**必改项 2 的「≥2 方案对比」在 `ui-experience-optimization.md` 侧仍缺，报审材料须提示 | 待技术评审 |
| 6 | `AGENTS.md` / `SESSION-STATE.md` | 每批完成按 M02 **即时追加**检查点（阶段 + 影响文件 + 回滚点） | 每批即时 |
| 7 | `hrm-dev/docs/deploy.md` | 回填现网实况与 B7 三端入口 | **已完成（v3 回填）**：已新增 **§11「现网实况与 B7 三端入口」**（三端入口与站点根、为何是 `/data/www/download/hrm-clients`、变更载体 + 载体核实方法（`docker inspect` / `nginx -T`）、单文件 bind mount 保 inode、全站 `.map` 404 与缓存头纪律、一键回滚、既有缺陷、B8 判定期），并在 §0.1 加**矛盾标注**（不改写历史结论）；**勿再重复要求** |

---

## 附录 A：子决策登记（吸收 split-plan 附录 A，状态均为「提议」）

| 编号 | 子决策 | 结论 | 备选与否决理由 |
| - | ---- | ---- | ---- |
| ADR-SM-01 | 仓库粒度 | workspace 单仓多工程（方案 A） | 完全独立仓 + 私服（基建先行、协作成本高）；不拆工程（不满足目标） |
| ADR-SM-02 | Token 真源归口（**决策一本体**） | 中立包 `packages/tokens`；各端与 `hrm-admin` 只读消费 | 上移 `hrm-admin`（移动端反向依赖 PC 生产工程、真源无门禁、D2 后二次迁移）；admin 局部覆盖（仅限定期兜底 ③） |
| ADR-SM-03 | 共享层边界 | `tokens` + `shared` + `api-client` + `mock` 四包；通用 UI 提升另议 | 单 `shared` 包（依赖不清）；各端自带请求封装（违反复用原则） |
| ADR-SM-04 | 端差异表达 | 保持角色驱动，不按端分区；端类型仅产品/审计约束；契约零改动 | 按端路径分区（改动面大、全量回归） |
| ADR-SM-05 | 移动端拆分粒度（**决策二本体**） | 两个 Vite app（`apps/boss-h5` / `apps/staff-h5`），共享 `packages/*` | 仅拆入口（不满足独立构建/发布）；单 app 双 router（同） |
| ADR-SM-06 | URL 与路由终态 | 迁移期与终态**统一**为子路径 `/web/` `/boss/` `/staff/`（hash 路由 + 子路径 base，**主智能体已裁定**）；`as` 兼容期 **≥ 1 个发布周期**，**退役触发条件见 §3.3**（三项全满足方退役） | 子域（DNS 依赖，**已否决**）；临时路径后切换到终态（二次切换） |
| ADR-SM-07 | 安卓壳形态 | 两 APK，`H5_URL` 构建参数注入，远程 https 加载；**包名占位** `com.example.hrmwebview.boss` / `.staff`（真实包名待裁定） | 单壳双入口（品牌难独立、运行时切 URL 引入混登审计复杂度）；离线包（与 history base 硬冲突） |
| ADR-SM-08 | 迁移策略 | 冻结源 + 复制式迁移 + 新路径并行 + **唯一切换点 B7（只增不改 Nginx）** | 原地重构单工程（破坏线上可用性） |
| ADR-SM-09 | 门禁策略 | 每端各持功能门禁 + 跨端门禁单点；断言只增不减；基线实跑冻结 | 拆分即减断言（门禁弱化，A06） |

## 附录 B：术语

| 术语 | 含义 |
| ---- | ---- |
| 端 | 网页端（`apps/web`）/ 驿站助手（`apps/staff-h5`）/ 驿站精灵（`apps/boss-h5`） |
| 真源 | Design Token 唯一定义处：`hrm-clients/packages/tokens` |
| 平台层 / 平台差异层 | 各端自带的 Element / Vant 变量覆盖与尺寸类 L3，属合法平台差异 |
| 唯一切换点 | 唯一触碰现网的步骤：B7（新增 Nginx location） |
| 迁移期 / 终态 | 迁移期 = 新旧入口并存；终态 = 三端独立工程 + 子路径 |
| C 档 | 需主智能体 §10.3 三步授权的操作（结构变更、现网 Nginx 等），不接受人工审批替代 |
| P0.5 / P0.6 | 安全评估先行 / 技术评审先行（两道闸门并行不互替） |

## 附录 C：检查点（M02）

- **2026-09-25**：产出 `hrm-dev/docs/adr-structure-migration.md`（**新增**），合并 R-0（Token 真源归口上移）与移动端两端拆分独立为同一次结构变更（ADR-SM-01～09）。**未改动任何源码、规则文件、契约文档、迁移脚本**；未触达生产；无回滚需求（纯新增文档）。
- 事实来源：`tokens.base.scss` / `gen-element-tokens.mjs` / `pc/main.js` / `mobile.html` / `mobile/router/index.js` / `portal/main.js` / `vite.config.js` / `hrm-admin/src/styles/index.scss` / `hrm-admin/package.json` / `ClientAdmissionPolicy.java` / `.env.production` / `.env.demo` / `git status`；既有文档 `ui-experience-optimization.md`、`tech-review-ui-experience.md`、`multi-client-split-plan.md`、`security-client-admission-review.md`、`project-tree.md`、`项目规则1.md` §12。
- **未运行**：`verify:mock` / `verify:mobile` / `verify:tokens` / `build` / `build:prod` / `lint` / `test` / `e2e` / Android 构建 —— 基线数字以 **U-2 实跑冻结**为准。
- **2026-09-25（v2 修订）**：按 [tech-review-structure-migration.md](tech-review-structure-migration.md)（结论「有条件通过」）**修订本 ADR**，闭环必改项 1–11 与自身错误 N1–N9：A4 升级为**四步可复核断言**并降级「唯一直证」→「**最接近充分的操作性证据**」；**清除真实域名**（全文无真实域名 / 凭据 / IP）并转交网络安全工程师形式核对（§9 SP7）；**撤回**对 `ui-experience-optimization.md` 选项② 的错误纠正（声明**原文定性无误**）；修正 `#409eff` / 旧壳 `H5_URL` 两处事实偏差；§8 / §11 数字统一为「**4 条修订 + 1 条补充**」；B7 **变更载体区分**（演示容器 `docker-demo/nginx.conf` ≠ 现网 `courier-nginx`，后者真源不在仓库、以运维记录为准）；补 **R新-1**（产物被覆盖）/ **R新-2**（无 CI）；影响面补 **4 类资产**（`index.html`/`pc.html`、`portal/main.js`、`docker-demo/portal.html`、`deploy-demo.sh`/`docker-compose.yml`/husky 等）；**NFR 补量化**与**切换期回滚判据**；`verify:mobile` 补**分端下限**；H4 删除不成立的 `@admin` 先例类比。**落地主智能体已裁定项**：A-2 / A-4 / B-3 / B-4 / B-5、D9 闭环、D4 统一子路径、§12.1 抽象表述、两 APK 包名占位；**U-A / U-B 保持「待用户裁定」**。**未改动任何源码、规则文件、契约文档**；未触达生产。
- **下一步（v2）**：技术评审**复评**（按评审报告 §7 复评触发条件）→ 主智能体审批 + **B0 冻结** → 用户裁定 **U-A / U-B** → 开工 B1；**B2 / B7 / B6 开工前分别闭环必改项 1 / 6 / 10**。
- **2026-09-25（v3 非主干修订）**：落地后**事实回填 + 回滚点补全**，逐条见下节「v3 修订记录」。**未新增决策、未改已裁定结论、未重新论证**；**未改任何其它文件**；未执行 git、未调用 MCP、未触达生产。

---

## v3 修订记录（2026-09-25）

> **修订性质：非主干（落地后事实回填 + 回滚点补全）。** 本 v3 **不新增决策、不改已裁定结论、不重新论证**；只把 B3–B8 落地后的**执行期事实**与**实测回滚点**回填进本 ADR。产出方（架构师）**只修本方方案，不做评估、不代授权**（反模式 A22 / A23）；v3 版本变更按 `项目规则1.md` §8 ④ **须重评后方可报审**。

| # | 改动位置 | 改了什么 | 依据 |
| - | ---- | ---- | ---- |
| 1 | 文首状态 / 日期 / 评审结论 / 版本绑定（L5–L7、L17） | 状态行 → **v3（非主干修订）**；日期补 v3；评审结论补「复评（2026-09-25）结论**通过**，具备报审资格」；版本绑定注「v3 须重评」 | `tech-review-structure-migration.md`「附：ADR v2 复评记录」§附.3；`update-log.md`「结构迁移 ADR v2 复评『通过』…」条目 |
| 2 | §3.3（L183）、§3.7 `as` 退役节奏（L267） | 明确 B8 三项退役触发条件的**计时起点 = B7 上线日 2026-09-25**；回填 B8 实际执行（**①③ 未满足 → 旧入口与 `as` 兼容读保留**；本批只做门户收敛 / 冻结发布 / 文档回填 / 判定期登记） | `update-log.md` **B8 条目**（范围裁定段） |
| 3 | §3.7 **B3** 回滚点（L254） | 由「删除 `apps/staff-h5`」补全为：删 `apps/staff-h5/` + 还原 `hrm-demo` **5 文件** + 复原 `src/mobile/views/staff/kpi.vue`（原改 `src/mobile/views/kpi/`）+ 还原 `hrm-clients/e2e-utils/harness.js` + 撤销 `packages/shared/src/ui/` 中立页提升 | `update-log.md` **B3 条目**「回滚点…实测不完整——已纠正」段 |
| 4 | §3.7 **B4** 回滚点（L255） | 由「删除 `apps/boss-h5`」补全为：删 `apps/boss-h5/` + 还原 `hrm-clients/package.json`（去 `verify:tokens` boss 目标与 `e2e:boss`）+ `package-lock.json`；注明本批未改 `hrm-demo` / `apps/staff-h5` / `e2e-utils` | `update-log.md` **B4 条目**「回滚点…已纠正」段 |
| 5 | §3.7 **B5** 回滚点（L256） | 由「删除 `apps/web`」补全为：删 `apps/web/` + 还原 `hrm-clients/package.json`（去 web 目标与 `e2e:web`）+ `package-lock.json`（**+44 包**）+ 可选 `npm ci` | `update-log.md` **B5 条目**「回滚点…已纠正」段 |
| 6 | §3.7 **B6** 回滚点（L257） | 由「还原 `build.gradle` 与壳配置」补全为：还原 `app/build.gradle`（恢复 `buildTypes` 两处 `H5_URL`）+ **删除 `app/src/staff/`、`app/src/boss/`** + 还原 `strings.xml` / `local.properties.example` / `README.md` / `BUILD.md` | `update-log.md` **B6 条目**「回滚点…已补全」段 |
| 7 | §6 回滚路径总表 B3–B6 行（L340–L341） | 与 §3.7 各批对齐并**指向 §3.7 详版**（补 `package.json` / `lock` / 壳 flavor 源集等）；**为一致性回填，可逆性与线上影响结论不变** | 依据本表 3–6 同源 |
| 8 | §5.4（L322–L324） | 回填**必改项 6 最终答案**：载体 = 宿主 `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`（`ro` 挂载）+ 单文件 bind mount **须原地改写保 inode**；站点根实落 = `/data/www/download/hrm-clients/{web,staff,boss}` 及**偏差原因与结论**；现网载体路径**已闭环** | `update-log.md` **B7 条目**（产物与落点 / Nginx 变更段） |
| 9 | §5.2（L307、L310） | 回填 B8 已执行：`portal/main.js` 链接实为 **L28/L35/L42**（原记 L24/31/38）；`portal.html:31-33` 已改 `/web/` `/staff/` `/boss/`（提交 `04975fd`），**两处行号随之更正** | `update-log.md` **B8 条目**（门户收敛段）+ **实测**（`portal/main.js:28/35/42`、`portal.html:31-33`） |
| 10 | §6.2（**新增小节**，L354–L361） | 登记两处**既有死链、非本方案引入**（均属 C 档显式修复）：`/admin/` 恒 500 → 改 `return 301 /web/`；`/download` 404 → 只增宿主 `/data/www/download/index.html` 下载页（未改 Nginx） | 任务提供的服务器事实；`update-log.md` B7 / B8 条目「既有缺陷登记」 |
| 11 | §11（L467、L473） | 标注 `project-tree.md`（已补 §2.1 多端 workspace 树）与 `deploy.md`（已补 §11 现网实况与 B7 三端入口，含矛盾标注）**回填完成**；**勿再重复要求** | `update-log.md` **B8 条目**（文档回填段） |

**未改动：** 任何决策 / 裁定结论、附录 A / B 既有内容、回滚纪律本体、凭据与安全声明；**未改任何其它文件**（含 `update-log.md` / `tech-review-*.md` / `deploy.md` / 源码 / 配置）；**未执行 git、未调用 MCP**；**未触达生产**。

**遗留（仍需回填 / 裁定，不属本轮）**：§9 安全清单 SP1–SP7 的复验结论（归网络安全工程师）；§7.3 **U-A / U-B 仍待用户裁定**；§7.2 A-2/A-4/B-3/B-4/B-5 与 §7.1 D1/D2/D5 的**书面裁定载体**（U-6）；§10.2 U-2/U-3/U-4/U-5 的实跑 / 核实冻结值；`hrm-demo` 与旧 `?as=` 入口的**终态退役**（B8 后续，依 §3.3 三项条件顺延，**不得静默下线**）。
