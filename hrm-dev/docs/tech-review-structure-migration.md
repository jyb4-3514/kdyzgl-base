# 技术评审报告：结构迁移 ADR（Token 真源归口上移 + 移动端两端拆分独立）

- 评估对象：`hrm-dev/docs/adr-structure-migration.md`（**v2026-09-25，提议稿**；11 节 + 附录 A/B/C）
- 作者：架构师 `express-station-architect`
- 评估方：`express-station-tech-reviewer`（评估方与产出方分离，未参与本 ADR 任何内容产出）
- 评估方式：**静态文档级评估**（逐条核验 ADR 引证的文件:行号 + 独立重跑静态检索 + 交叉核对上游文档与规则条款）。本机未运行 `verify:mock` / `verify:mobile` / `verify:tokens` / `build` / `build:prod` / `lint` / `test` / `e2e` / Android 构建，相关门禁结论一律标注**未运行**。
- 结论等级：**有条件通过**
- 准入结论：**可报主智能体审批**，须随附本报告第 2 节必改项清单；其中必改项 **1、6、10 为对应批次开工前置**（不闭环则 B6 / B7 不可验收）。
- 评审结论绑定版本：ADR 本稿（2026-09-25）。ADR 版本变更须重评。

---

## 0. 结论先行

ADR 的两项主干决策**成立且有据**：① Design Token 真源必须移出「可整体删除的演示工程」并落到中立、受门禁保护的位置；② 两端从「同一 `mobile.html` 靠 `?as=` 分流」演进为两个独立 Vite app。ADR 对最危险的一条（迁移期线上可用性）给出了正确的结构性答案——**唯一切换点 B7 + 只增不改 Nginx**，且对 `api.md`/`db.md`/`hrm-server`/`sql` 的零改动声明与实际一致。未命中「打回」红线（无 `api.md`/`db.md` 冲突、无硬编码经验值、无编造依据）。

判「有条件通过」的核心理由（均为**非主干**问题）：

1. **1 条自报事实纠正不成立**（§1.3-5 对 `ui-experience-optimization.md` 选项② 的「定性修正」属对象错位）；
2. **ADR 自身违反自设凭据声明**：正文落入了真实域名 `kongzhen1.com`（§3.7 第 250 行、§10.1 H5 第 405 行），而文首声明「不记录任何真实凭据、域名、IP」——此项**登记并转交网络安全工程师**，本报告不出安全结论；
3. **「收敛到 CI 构建」不可判定**：`hrm-dev` 与仓库根**无任何 CI 配置**（无 `.github/workflows`、无 `.gitlab-ci.yml` / `Jenkinsfile`），B6 的验收表述须改写；
4. **A4 删除演练自称「唯一直证」但严格不充分**（重命名≠删除、未清装、未做产物级断言）；
5. **B7 的变更载体未指明**：`deploy/docker-demo/nginx.conf` 是演示**容器内**配置，与 B7 要改的**现网 courier-nginx** 不是同一份；仓库内无现网配置真源。

以上均可按必改项逐条核对闭环，**不须整体重评**。

---

## 1. 六维逐项结论

| 维度 | 结论 | 证据（引证） |
| --- | --- | --- |
| 1 依据充分性 | **基本满足**（1 条纠正不成立 + 3 处表述偏差 + 2 处「未验证」须显式标注） | 见 1.1 |
| 2 边界合理性 | **基本满足**（范围清晰；影响面清单漏 4 类资产） | 见 1.2 |
| 3 NFR 覆盖度 | **部分满足**（可回滚性充分；性能/兼容机制/构建耗时缺量化） | 见 1.3 |
| 4 复杂度与可行性 | **基本满足**（选项对比齐、无过早否决；缺工作量级） | 见 1.4 |
| 5 假设与风险登记 | **基本满足**（假设表 + 批次回滚点齐；A4 断言强度不足、B7 覆盖隐患未登记） | 见 1.5 |
| 6 与既有契约一致性 | **满足**（`api.md`/`db.md`/§12 无未声明冲突；§12 修订清单方向正确） | 见 1.6 |

### 1.1 维度 1 · 依据充分性

**独立复核（我重跑静态检索，不采信转述）：**

| ADR 引证 | 独立复核方式 | 结果 |
| --- | --- | --- |
| 依赖方向 Demo → admin 单向（§1.1 事实 3，引 `pc/main.js:21-25`） | `hrm-dev/hrm-admin/**` 全目录检索 `hrm-demo` | **0 命中** ✅；[pc/main.js:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/main.js#L22) 先 `import '@admin/styles/index.scss'`、L24 后 `import './styles/tokens.scss'`，顺序吻合 |
| `hrm-admin` 未接入 Token，`var(--` 命中 0（§1.1 事实 2） | Grep `hrm-admin/src` 计 `var(--` | **0 命中** ✅；`src/styles/` 下确仅 [index.scss](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/styles/index.scss) 一个文件 |
| `verify:tokens` 扫描目标写死（§1.1 事实 4，引 `gen-element-tokens.mjs:36-38`） | 读文件 | [L36-38](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/scripts/gen-element-tokens.mjs#L36-L38) 逐字吻合 ✅ |
| Token 真源在演示工程（§1.1 事实 1） | 读 [tokens.base.scss:2](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/shared/styles/tokens.base.scss#L2) | 注释自称「跨端 Design Token 真源（P1-2）」✅ |
| 两端共用一份 router、品牌靠运行时改写 title（§1.2 事实 2/3） | 读 [mobile/router/index.js:246-253](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L246-L253) | 逐行吻合 ✅ |
| 跨域直引 `/boss/kpi`→`views/staff/kpi.vue`（§1.2 事实 4） | 读 [mobile/router/index.js:41-46](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/router/index.js#L41-L46) | 逐行吻合 ✅ |
| `?as=` 由端选择页携带（§1.2 事实 1，引 `portal/main.js:31/38`） | 读 [portal/main.js:31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/portal/main.js#L31) / L38 | 吻合 ✅ |
| 后端零改动依据（§3.3，引 `ClientAdmissionPolicy.java:132-142`） | 读该段 + 类注释 | [parseEnd](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L132-L142) 确为「命中取值域直接返回；仅旧值 `H5` 读 `as` 派生」；类注释 [L27-30](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/ClientAdmissionPolicy.java#L27-L30) 明确「端类型来自客户端自称，是产品/审计约束而非鉴权边界」——ADR「不参与鉴权」表述**准确** ✅ |
| `hrm-admin` 无 lint/verify（§2.1） | 读 `hrm-admin/package.json` | scripts 仅 `dev`/`build`/`preview` ✅ |
| `.env.production` 现值 `/hrm-api/v1`（§1.3-2） | 读 [.env.production](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/.env.production) | `VITE_API_BASE=/hrm-api/v1` + 注释「不能写 /api/v1——那是现网 courier-app 的路径」✅ |

**问题项：**

- **P-1（精度）** §1.1 事实 2 写「仍是 `#409EFF` / `#909399` 等字面量」。独立检索：`#409EFF` 在 `hrm-admin/src` **无出现**（它是 Element Plus 的默认变量值，不是 `hrm-admin` 写的字面量）；`#909399` 见于 [index.scss:59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/styles/index.scss#L59)。表述应改为「无 `var(--)` 引用，色值沿用 Element Plus 默认值与少量字面量」。
- **P-2（无源/未标注）** ADR 反复以「本轮用户口径」为据（R-0 归口决策前提、D5「两端终极形态=安卓壳+H5」、`verify:mock` 942）。仓库内**无对应的用户裁定记录载体**（update-log / 会话记录均未见）。按分级规则「不确定项不得计入通过」，此类「用户已确认」须标注为**待用户书面裁定确认**，或指向可核验载体。
- **P-3（反幻觉抽查——通过）** 涉 Vite / sass / Playwright 的机制陈述均**未发现凭记忆臆断**：`resolve.dedupe` 的成因（`activePinia` 分裂 → `_s` 报错）与 [vite.config.js:88-100](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/vite.config.js#L88-L100) 注释一致；`server.fs.allow` 的「工作区根」默认值、MPA `input` 三/两入口、sourcemap `hidden` 均可核；e2e 各 spec 名与 §3.6 所列一致。**唯一存疑的机制论证**见 §1.4 S-1（H4 的「先例」类比不成立）。

### 1.2 维度 2 · 边界合理性

- **范围与「不做的事」清晰**：§1 界定两决策、§4 自查合并理由、§6 回滚总表、§7 裁定项、§9 安全清单、§10 假设/待核实、§11 回填指针。无范围夹带；`hrm-server`/`sql` 零改动已声明并核实。
- **B1–B8 批次边界不重叠、可独立验收**：B1（建 workspace + 抽包，验收含 `git diff -- hrm-dev/hrm-admin` 为空）、B2（真源上移）、B3/B4（逐端拆）、B5（拆 web）、B6（两 APK）、B7（发布切换）、B8（演示站处置）。B7 明示为**唯一**触碰现网步骤（§3.7 第 247-250 行），批次互不重叠 ✅。
- **遗漏的受影响面（4 类）**：
  1. `hrm-demo/index.html`（端选择页）与 `hrm-demo/pc.html`——§5.2 只列了 `mobile.html`；
  2. `hrm-demo/src/portal/main.js`——**硬编码旧入口链接**（`mobile.html#/login?as=boss` / `as=station`），拆端后终态须改写，未列入影响面；
  3. `hrm-dev/deploy/docker-demo/portal.html`——部署资产（注释说明「部署时须拷为 dist/index.html」），**内含硬编码 `/mobile.html?as=station`、`/mobile.html?as=boss`、`/pc.html` 绝对链接**（第 30-32 行），是旧入口的真源之一，未列入影响面；
  4. `hrm-dev/deploy/docker-demo/deploy-demo.sh`、`docker-compose.yml` 与门禁周边资产（`.husky` / `lint-staged` / `eslint.config.js` / `stylelint.config` / `commitlint`）——§5.2「门禁资产」只列了 `verify-*.mjs` / `e2e/**` / `playwright.config.js`。
- **发布面表述含糊（关键）**：§5.1 把「`nginx.conf.example` / `docker-demo/nginx.conf`」与「现网 Nginx 变更」混在**同一行**。实测 [docker-demo/nginx.conf](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/docker-demo/nginx.conf) 是**演示容器内**的静态站配置（其 `location = /`、`location /` 服务的是演示站自身），**与 B7 要改的现网 `courier-nginx` 不是同一份**；仓库内现网配置真源缺位（仅模板 `deploy/nginx.conf.example`）。→ B7 的「变更载体」未指明（见必改项 6）。

### 1.3 维度 3 · NFR 覆盖度

| 子项 | 覆盖 | 评价 |
| --- | --- | --- |
| 可维护性 | **满足** | 单 lockfile + 依赖方向单向（`apps/*`/`admin`/`demo` → `packages/tokens`，真源零反向依赖），§2.3 A1/A2 断言可判定；共享/独立边界（§3.5 十六项）逐项给判定与落点。 |
| **可回滚性** | **满足** | §6 总表逐批给回滚动作 + 可逆性 + 线上影响；B1–B6 明示「不触碰现网」；回滚纪律禁 `reset --hard`/`clean -f`（对齐 D 档）✅。 |
| 性能 | **缺** | 未给**多站点构建**的耗时/体积目标（拆分后构建次数：3 端 × 2 模式 + admin + demo），亦未评估单端产物体积是否劣化；「3.6 每端各一套门禁」的累计执行成本未量化。 |
| 兼容性 | **部分** | `as` 兼容读（§3.3）、旧壳 APK 迁移期可用（§3.4）**已声明**；但**未写清机制**——见 §3 独立判定（B7）；`hash` 路由对 base 不敏感已作 H2 假设（合理）。 |
| 安全（形式核对） | **满足** | §9 SP1–SP6 覆盖新增 location / `.map` / 壳 H5_URL / API 基址 / 端准入 / 共享包供应链；声明「均为设计输入、非安全结论」并归口网络安全工程师 ✅。**本维只作形式核对**（实质判定归安全工程师）。 |
| 可观测性 | **缺** | 迁移/切换期无回滚判据（触发阈值）与观测口径（如「新入口 5xx 率」「深链刷新失败率」），仅有人工 `curl` 断言。 |
| 数据一致性 | 不涉 | 无表结构变更 ✅。 |

### 1.4 维度 4 · 复杂度与可行性论证

- **选项对比齐、无过早否决** ✅：
  - R-0 三选项（上移 admin / 中立包 / admin 局部覆盖），最小方案③**未被否决**而是保留为**限定期兜底**并配收敛期限裁定（A-4）；
  - 拆端三选项（仅拆入口 / 两 Vite app / 单 app 双 router），最小方案①保留为 **B3 中间过渡步**（§3.2 注）；
  - 壳形态两 APK vs 单壳双入口、URL 子路径 vs 子域，均给对比与否决理由并登记裁定项。
- **「只增不改 Nginx」能否保证零中断——机制成立，但有未登记隐患**：成立的理由是**旧路径由旧产物继续提供**（`hrm-demo-static` 容器与 `location = /` / `location /` 不动），新 location 仅追加且前缀匹配天然优先于 `location /`。隐患见必改项 7（产物覆盖）与 §3 独立判定。
- **缺口（非阻塞）**：无工作量量级（人日/迭代归属），B1–B8 的风险档位（低/中/中-高）与工作量无对应；WBS 级排期缺失。属「复杂度论证」可补项。
- **S-1（机制论证不严谨）** H4 的「依据」写「`hrm-demo` 已有跨工程 `@admin` 别名先例」。实测该先例是 **Vite alias + `server.fs.allow`** 组合（[vite.config.js:103/125](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/vite.config.js#L101-L127) + 注释「不配置该项时访问 `/@fs/.../hrm-admin/...` 返回 403」）；而 A-2 选项① 明确「**不改 `package.json`**」，admin 侧走 sass `@use` **相对路径**——两者**机制不同**，先例不能直接类比。H4 已列「非生产分支实跑 `vite build`」为最小验证方式，**可接受，但「先例」这条依据应删除或改写**。

### 1.5 维度 5 · 假设与风险登记

- **显式假设表齐全** ✅：H1–H5 每条含「依据 / 不成立后果 / 最小验证方式」；H1（`as` 可退役）已由我独立验证为**成立**（见 1.1）；H2/H4 属待实跑验证。
- **待核实项** ✅：U-1～U-5 分开登记，且 §3.7 注「核实前不得写实现」。
- **风险登记**：ADR 依赖 split-plan §6 的 R1–R12（未复制，只引用）——**引用可接受**，但本 ADR **新增的两项风险未登记**：
  1. **R新-1 演示站产物被覆盖**（B7 后若重新构建/发布演示站 dist 覆盖旧产物，旧 `mobile.html?as=` 入口失效）；
  2. **R新-2 无 CI 通道**（§3.4/§3.7 的「CI 构建」不存在）。
- **门禁不变式**：§3.6 与 §5.3 列 `verify:mock ≥ 冻结基线` / `verify:mobile 两端合计 ≥ 48` / `verify:tokens 多目标` / 每端 `build`/`build:prod`/`lint`/`lint:style`/`test`/`e2e` + `build:prod 无 Mock chunk` + 「断言只增不减」。**较上一轮（ui-experience）的 §3.3 更完整**（补齐了 `lint:style`/`test`/`e2e`），并采纳了「基线实跑冻结」的处置 ✅。**不足**：A4 断言强度（见必改项 1）、`verify:mobile` 「两端合计 ≥48」未约束**分端下限**（理论上可单端 0 / 另端 48）。

### 1.6 维度 6 · 与既有契约一致性

- **`api.md` / `db.md`：无冲突** ✅。ADR 声明 `hrm-server`/`sql` 零改动，我核实 `ClientAdmissionPolicy` 未改（`as` 退役无需后端配合，H1 成立）。
- **`项目规则1.md` §12**：ADR §8 列出需修订条款（5 行），**未代改规则文件**（§8 末句「须主智能体裁定后由其执行」），符合 M06「权威不重复」与禁改范围 ✅。逐条核实见第 5 节。
- **与 `multi-client-split-plan.md` 的关系** ✅：ADR §4 自查「复用其 §0/§2/§3/§5/§6/§6.3」经核对**成立**，且确为「收敛版 + 扩展」（新增 R-0 的 B2、两 APK 的 B6、§8 规则修订清单）；**未发现两轮迁移风险**（R-0 落中立包后与 D2 解耦，避免真源随 `apps/web`↔`hrm-admin` 收敛再搬一次）——§2.5 第 2 点论证成立。
- **与 `tech-review-ui-experience.md` 的关系**：**部分**。tech-review 必改项 1（R-0 兜底路径不闭合）→ ADR 以 A-2 选项①（新建 `hrm-admin/src/styles/tokens.scss`）+ A5（白名单比对）**闭环，处理正确**；tech-review 必改项 2（生产端换色须 ≥2 方案对比 + 决策归用户）→ ADR 只把**决策归属**归入 U-A，**未补替代方案对比**（该对比在 `ui-experience-optimization.md` 侧仍缺，本 ADR 可辩称不属其范围，但报审材料须提示）。
- **与 `ui-experience-optimization.md` §7.1 R-0 的关系**：**存在不成立纠正**（§1.3-5），见第 4 节第 5 条。

---

## 2. 必改项清单

格式：问题 —— 依据；验收标准；复核方式；是否阻塞。

---

**必改项 1（阻塞 B2 验收）**
- 问题：A4「删除演练」自称「生产不依赖演示工程」的**唯一直证**（§2.3 表下注），但断言强度不足：①「临时重命名」≠「删除」，且未要求**清装**（残留 `dist` / `node_modules` 会产生假阳性）；② 未做**产物级断言**；③ 未界定「依赖」的边界（构建期 vs 运行期 vs 部署期）。
- 依据：§2.3 A4；§3.7 B2 验收「A4 删除演练通过」。
- 验收标准：A4 改写为可复核的四步断言并写入正文——(a) `hrm-demo` **移出工作区或删除**（非仅改名）；(b) 三端与 `hrm-admin` 均执行**清装** `npm ci` + 清 `dist` 后 `build` / `build:prod`；(c) 产物级断言：各 `dist` 内检索 `hrm-demo` 字符串 = 0、`build:prod` 产物无 Mock chunk；(d) 明确「本断言覆盖构建期依赖；运行期/部署期依赖另行由 §10 假设与 §9 安全清单覆盖」。措辞降级为「最接近充分的操作性证据」。
- 复核方式：读改写后的 §2.3 A4 与 §3.7 B2，核对四步是否齐备。
- 阻塞：**是**（B2 的验收依据）。

**必改项 2（阻塞报审前）**
- 问题：ADR 正文落入**真实域名** `kongzhen1.com`（§3.7 第 250 行、§10.1 H5 第 405 行），违反文首自设声明「本文不记录任何真实凭据、**域名**、IP、AppKey」；且与 `multi-client-split-plan.md` §2.4「域名/IP 以现网运维记录为准，**不落本文**」口径不一致。
- 依据：ADR 文首凭据声明；项目规则 §12.9 与全局规则 §4（真实凭据 / 服务器 IP 严禁入库）。**本角色只登记并转交，不做安全实质结论。**
- 验收标准：两处域名改为占位符（`example.invalid`）或改为「现网主域名（值见运维记录，不落本文）」；全文检索 `kongzhen1` 命中 = 0。**同时**将本条转交网络安全工程师，核查真实域名入库是否构成暴露面问题（形式核对即可）。
- 复核方式：Grep ADR 全文 `kongzhen1`；核对转交记录。
- 阻塞：**是**（报审前须修订；安全判定由网络安全工程师出具）。

**必改项 3（不阻塞，报审须附）**
- 问题：§1.3-5 对 `ui-experience-optimization.md` §7.1 R-0 **选项②** 的「定性修正」**不成立**（对象错位）。
- 依据：原文 [ui-experience-optimization.md:583](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/ui-experience-optimization.md#L583) 选项② 为「`hrm-admin` 内新增自有 `tokens.scss`（值复用、文件分离）」——其对象是 **admin 内自建副本**（与 Demo 真源并存 = 两份同值），定性「违反同一逻辑不得重复实现」**正确**；而 ADR §2.1 选项② 是「中立共享包」，二者**不是同一形态**（编号巧合）。
- 验收标准：删除或改写 §1.3-5：明确「ui-experience 选项② 的定性无误（对象为 admin 自建副本）」；若仍要说明本 ADR 的中立包形态不同，另立一句、不得声称「修正既有材料」。
- 复核方式：读 §1.3-5 与 §2.1 对照。
- 阻塞：否（但须更正，否则构成「纠正既有材料」的误判）。

**必改项 4（不阻塞）**
- 问题：`kdyzgl` 类型事实偏差 2 处：① §1.1 事实 2 把 Element Plus 默认值 `#409EFF` 说成 `hrm-admin` 的字面量（实测 `hrm-admin/src` 无 `#409EFF`）；② §3.4「已装旧单壳 APK 指 `mobile.html?as=`」与实测不符——[app/build.gradle:27/33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-android-shell/app/build.gradle#L27-L33) 的 `H5_URL` 为 `.../mobile.html`，**不带 `?as=`**。
- 依据：上述两文件行号。
- 验收标准：① 事实 2 改为「无 `var(--)` 引用，色值沿用 Element Plus 默认值与少量字面量（如 `#909399`）」；② §3.4 改为「旧壳 APK 指 `mobile.html`（端选择页/登录页，不带 `as`）」。
- 复核方式：读 §1.1 事实 2 与 §3.4。
- 阻塞：否。

**必改项 5（不阻塞）**
- 问题：§11 回填指针第 3 项写「`.trae/rules/项目规则1.md` §12｜**§8 所列 4 条修订**」，而 §8 表实为 **5 行**（§12.1 / §12.3 / §12.5 / §12.7 / §12.8），且 §12.7 自标「无需修订」。
- 依据：ADR §8 与 §11 第 3 行。
- 验收标准：统一为「§8 所列 **4 条修订 + 1 条补充**（§12.7 无需修订）」，两处数字一致。
- 复核方式：对照 §8 表行数与 §11 表述。
- 阻塞：否。

**必改项 6（阻塞 B7 开工）**
- 问题：B7 的**变更载体未指明**。§5.1 把「`nginx.conf.example` / `docker-demo/nginx.conf`」与「现网 Nginx 变更」并列，但实测 `deploy/docker-demo/nginx.conf` 是**演示容器内**配置，与 B7 要改的现网 `courier-nginx` 不是同一份；仓库内现网配置真源缺位（仅模板 `deploy/nginx.conf.example`）。
- 依据：`deploy/docker-demo/nginx.conf` 第 1-3 行（「三端演示 Demo · 容器内静态站」）；ADR §5.1 / §5.4 / §3.7 B7。
- 验收标准：§5.1 / §5.4 明确——(a) 演示容器内 `nginx.conf` 是否需要同步（说明「不需要」或给出理由）；(b) 现网 B7 的变更载体（现网配置文件路径 / 由哪个脚本管理）**由运维核实并写明**，或明确标注「现网配置真源不在仓库，B7 实施时以运维记录为准」；(c) 二者不得混写在同一行。
- 复核方式：读 §5.1 / §5.4 是否有区分与载体声明。
- 阻塞：**是**（B7 属 C 档现网变更，载体不明不可实施）。

**必改项 7（不阻塞，须登记风险）**
- 问题：B7「只增不改 → 迁移期线上零中断」的成立前提是「旧产物继续由演示容器提供」，但 ADR **未登记**「演示站产物被覆盖导致旧路径失效」这一风险。
- 依据：§3.7 第 250 行结论依赖「现网静态站未动」；§5.4「新增 3 个站点根目录…与现有目录不重叠」仅规避了**目录**冲突，未约束**产物重建/发布**。
- 验收标准：在 §6 风险表或 §3.7 补一条风险：触发条件「B7 之后重新构建/发布 `hrm-demo` 并覆盖旧 dist」、影响「旧 `mobile.html?as=` 入口失效」、缓解「迁移期 `hrm-demo` 冻结发布（仅 P0 修复且需回归旧入口），或旧产物做只读快照」。
- 复核方式：检索新增风险条目。
- 阻塞：否。

**必改项 8（不阻塞）**
- 问题：影响面清单漏 4 类资产（见 §1.2）。
- 依据：`hrm-demo/index.html` / `pc.html`、`hrm-demo/src/portal/main.js:31/38`、`deploy/docker-demo/portal.html:30-32`、`deploy/docker-demo/deploy-demo.sh` + `docker-compose.yml` + husky/lint-staged/eslint/stylelint/commitlint 配置。
- 验收标准：§5.1 / §5.2 补入上述资产与影响判定（改 / 不改 + 归属批次）；其中 `portal.html` 与 `portal/main.js` 须明确「终态入口链接由谁改、属哪一批」。
- 复核方式：对照 §5 清单与实际目录。
- 阻塞：否。

**必改项 9（不阻塞）**
- 问题：NFR 缺量化——多站点构建耗时/体积目标、切换期观测判据（哪些指标达阈值即判定异常并回滚）均未给。
- 依据：§3.6 门禁清单、§3.7 B7 验收（仅人工 `curl`）。
- 验收标准：§3.6 或 §5.3 补「构建耗时与产物体积不作硬指标，但须登记拆分前后实测对照值（由测试工程师实跑）」；§3.7 B7 补「切换期观测判据：新入口 4xx/5xx 率、深链刷新失败、`.map` 可下载探测，任一命中即执行回滚」。
- 复核方式：读 §3.6 / §3.7。
- 阻塞：否。

**必改项 10（阻塞 B6 验收）**
- 问题：「收敛到 CI 构建」**不可判定**——`hrm-dev` 与仓库根**无任何 CI 配置**（无 `.github/workflows`、无 `.gitlab-ci.yml` / `Jenkinsfile`）。
- 依据：全仓 Glob CI 配置 = 0 命中；ADR §3.4「验收收敛到静态审查 + CI/服务器构建」、§3.7 注「涉 Java / Android 的验收收敛到服务器 / CI」。
- 验收标准：改写为可判定表述——「B6 验收 = **静态审查**（`H5_URL` 无硬编码真实域名 / 两壳应用名与包名区分 / 桥接方法名未变）+ **标注未运行**（当前环境无 Android SDK，亦无 CI 通道）；**可交付性**验收须在具备 Android SDK 的环境另行执行，不在本批次声明」。
- 复核方式：读 §3.4 与 §3.7 B6。
- 阻塞：**是**（B6 验收标准须先可判定）。

**必改项 11（不阻塞）**
- 问题：`verify:mobile` 不变式只写「两端合计 ≥ 48」，未约束**分端下限**，理论上可「单端 0 / 另端 48」仍通过。
- 依据：§3.6 与 §5.3。
- 验收标准：补「每端 ≥ 其同源断言子集下限，且**两端合计 ≥ 48**；子集切分须先出「拆分前后断言清单对照表」（由测试工程师实跑出具）」。
- 复核方式：读 §3.6 / §5.3。
- 阻塞：否。

---

## 3. 特别事项独立判定

### 3.1 A4「删除演练」是否充分证据

**判定：方向正确，但作为「生产不依赖演示工程」的充分证据——不充分。**

- 它能捕获的：所有**编译期路径引用**（相对路径 / 别名指向 `hrm-dev/hrm-demo/**`）。因 `hrm-admin` 全目录检索 `hrm-demo` = 0 命中，且 `apps/*` 设计为不引用 demo，故通过概率高。
- 它不能捕获的：① 残留 `dist` / `node_modules` 造成的假阳性（改名后旧 `dist` 仍在）；② **运行期/部署期**依赖（Nginx 指向演示站、`portal.html` 硬编码旧链接、旧壳 APK 加载地址）；③ 产物内容是否仍引用 demo（须产物级 grep）。
- **更严谨的断言**：见必改项 1（删除而非改名 + `npm ci` 清装 + 清 dist + 产物级检索 + 边界声明）。

### 3.2 B7 切换点：「只增不改 Nginx」+ 旧路径继续可用是否成立

**判定：机制成立（有前提），但存在一处未登记的隐患。**

- 成立链：旧路径 `/mobile.html` 与 `/pc.html` 由**演示容器（`hrm-demo-static`）旧产物**继续提供；B7 只**新增** `/web/` `/staff/` `/boss/` 三条前缀 location，`location = /` 与 `location /` 不动 → 旧入口与新入口并存可达。前端产物改指新子路径**不影响**旧路径，因为新产物部署在**新增的站点根**（§5.4 声明「与现有目录不重叠」）。nginx 前缀匹配「最长者优先」，新 location 与既有 `/api/` `/admin/` `/hrm-api/` 无冲突。
- **隐患（须澄清「旧路径如何仍可用」的机制并登记风险）**：① 若演示站 dist 被**重新构建/发布覆盖**，旧入口失效 → 见必改项 7；② 若 `location /` 的 `try_files $uri /pc.html` 规则不变而新站点根被误挂到同一 root，会出现「新产物覆盖」→ 须在 §5.4 明示 **root/alias 隔离**（新站点根独立、不共享 root）；③ 「旧路径」成立完全依赖 H5 假设（现网 `location /` 确由演示静态站提供）——ADR 已登记 H5 与 U-5（**运维核实前不得实施 B7**），此项要求正确，须保留。
- 结论：**结论可接受，但须补「root/alias 隔离」显式声明与必改项 7 的风险登记后方可实施。**

### 3.3 两个 APK 决策

**判定：与项目规则 §12 及用户既定要求一致，`local.properties` 已覆盖。**

- §12.8（[项目规则1.md:407](file:///d:/Users/16626/Desktop/kdyzgl-base/.trae/rules/项目规则1.md#L407)）只约束「`local.properties` 绝不入库」「未编译验证前不得声称可交付」，**未限制壳数量**；两 APK 不违反任何条款 ✅。
- 用户既定要求「两端终极形态均为安卓壳 + H5」（§3.4 注）→ 两 APK 与之**一致**（`/boss/` 与 `/staff/` 各一壳）✅。
- `local.properties` 不入库：§3.4 / §5.5 均声明，且 `.gitignore` 核实覆盖 ✅。
- **补充要求（非阻塞）**：两 APK 的**包名区分取值**未给（现 `applicationId` 为 `com.example.hrmwebview`，见 [build.gradle:11](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-android-shell/app/build.gradle#L11)）。B6 验收「包名区分」目前只能判「不相同」，建议在 §3.4 补一句「两壳暂用 `com.example.hrmwebview.boss` / `.staff` 占位（真实包名由用户/主智能体裁定，不入库敏感信息）」。

### 3.4 安卓壳「未编译验证」是否使 B6 验收无法判定

**判定：三条验收项**均可静态判定**，故 B6 本身可判定；但「交付可验收性」不可判定，措辞须改写。**

- B6 ①「`H5_URL` 无硬编码真实域名」→ 静态可判（grep `build.gradle`）；实测现值 `https://example.invalid/mobile.html`，**已达标** ✅。
- B6 ②「两壳应用名 / 包名区分」→ 静态可判（读 `res/values/strings.xml` + `build.gradle`）。
- B6 ③「桥接方法名未变」→ 静态可判（读 `HrmJsBridge` / H5 侧 `bridge.js`）。
- **不可判定的**是「壳能加载、桥接通、HTTPS 通」——属**运行期**验收。因**无 CI 通道**（必改项 10），须写成：**「B6 验收 = 静态审查（①②③）+ 标注未运行；运行期验收（壳加载/桥接/混合内容）收敛到『具备 Android SDK 的构建环境』，本批次不声称可交付。」**
- 与 §12.8「壳未编译验证前不得声称可交付」**一致**，ADR 已有「标注未运行」，仅需把「CI」替换为可判定表述。

---

## 4. ADR 自报 5 条「对既有材料的事实性纠正」逐条核实

| # | ADR 声称 | 独立核实 | 结论 |
| - | ---- | ---- | ---- |
| 1 | 「删除 `hrm-demo`，生产 PC 端会一起崩」不成立；依赖方向实为 Demo → admin 单向；正确表述为「生产资产/口径寄居可删除的演示工程」 | `hrm-admin/**` 检索 `hrm-demo` = **0 命中**；[pc/main.js:22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/pc/main.js#L22) 单向 `@admin` 引用；`hrm-admin` 可独立 `vite build`（判据成立） | **成立** ✅（断言强度问题另见 §3.1，不影响本条定性） |
| 2 | split-plan §0.5-2 / §4.3 红线 / D8 所述「生产 API 基址错误」已不存在 | [.env.production](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/.env.production) 现值 `VITE_API_BASE=/hrm-api/v1` + 注释「不能写 /api/v1——那是现网 courier-app 的路径」；split-plan §0.5-2 / §4.3 / D8 确记 `/api/v1` 为错误 | **成立** ✅（D8 降级为「已闭环」合理） |
| 3 | `verify:mock` 基线口径漂移（split-plan 记 919，本轮口径 942），须实跑冻结为唯一基线 | split-plan §1.2 C9 确写「现状 919/919」；`hrm-demo/scripts/verify-mock.mjs` **无硬编码 919/942**（两者皆为运行时计数）→ 静态不可复现 | **成立** ✅（ADR 以「≥ 冻结基线」表述、不写死数字，处理**正确**） |
| 4 | split-plan 裁定项 D5 已被用户**部分裁定**（两端终极形态 = 安卓壳 + H5） | 仓库内**无该用户裁定的书面载体**（update-log / 会话记录未见）；仅有 `project-tree.md` §0 三端定位作旁证 | **部分成立 / 未验证** ⚠️：作为「待裁定项收敛」的陈述合理，但「已被用户裁定」**依据不可核验**，须补裁定记录或标注待确认 |
| 5 | `ui-experience-optimization.md` §7.1 R-0 **选项②** 的定性需修正（「独立文件承载 Token」不等于重复实现） | 原文 [L583](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/ui-experience-optimization.md#L583) 选项② 的对象是「`hrm-admin` **内**新增自有 `tokens.scss`（值复用、文件分离）」= admin 自建副本（两份同值），原文定性**正确**；ADR 代入的是自家 §2.1 选项②（**中立共享包**），非同一形态 | **不成立** ❌（对象错位，见必改项 3） |

---

## 5. §8 提出需修订的项目规则 §12 条款逐条核实

> 核实基准：[项目规则1.md:396-409](file:///d:/Users/16626/Desktop/kdyzgl-base/.trae/rules/项目规则1.md#L396-L409)。ADR §8 引文均标注「摘要」，实质与现文一致。

| 条款 | 现文核实 | ADR 修订理由是否必要 | 结论 |
| --- | --- | --- | --- |
| §12.1（L400） | 「`hrm-dev/hrm-admin` 与 `hrm-dev/hrm-server` 零改动：Demo 只经 Vite 别名 `@admin` 只读引用…」——首句确易被误读为「`hrm-admin` 不得被改动」 | **必要**（tech-review §1.2 已裁定「表述过宽」；`hrm-admin` 属生产工程可改须 Review） | **成立** ✅。**但**「补『Token 真源位于 `hrm-clients/packages/tokens`』」把**实现细节**写进规则唯一真源，易随实现漂移；建议改为「任何端不得再定义 Token 真源（真源位置见 ADR）」——**措辞须降耦** |
| §12.3（L402） | 「Mock 层只允许由 `pc/main.js`、`mobile/main.js` **两个入口**以动态 `import()` 装配，且装配必须先于 `mount`」 | **必要**（拆端后入口由 2 变 3+，文字与结构必须一致） | **成立** ✅ |
| §12.5（L404） | 「`build` = 演示态（含 Mock，**三入口**）；`build:prod` = 生产态（剥离 Mock，**仅 pc/mobile**）」 | **必要**（入口枚举随拆分变化，语义不变） | **成立** ✅ |
| §12.7（L406） | 「`base` 与 `file://` 离线包存在结构性冲突（history 需 `/`、`file://` 需 `./`），登记 `TODO(扩展)`，未实施」 | ADR 自标「**无需修订**，仅补一句『移动端一律远程 https 加载』」 | **成立** ✅（补充有理：可消解 §12.8 的相反暗示） |
| §12.8（L407） | 「安卓壳：`local.properties`…**绝不入库**；**`assets/h5/` 注入 H5 产物**；壳未编译验证前不得声称可交付」 | 实测 [MainActivity.java:64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-android-shell/app/src/main/java/com/example/hrmwebview/MainActivity.java#L64) `webView.loadUrl(BuildConfig.H5_URL)` **远程加载**；`.gitignore:16-17` 把 `assets/h5/*` 忽略仅留 `.gitkeep` → 现文「注入 H5 产物」**与现状不符** | **成立** ✅（修订必要且表述准确） |

---

## 6. ADR 自身新发现的问题

| # | 问题 | 位置 | 性质 |
| - | ---- | ---- | ---- |
| N1 | 正文落入真实域名 `kongzhen1.com`，违反自设凭据声明 + 与 split-plan 口径不一致 | §3.7 L250、§10.1 H5 L405 | **规范/安全面**（须修订 + 转交安全工程师）；见必改项 2 |
| N2 | §1.3-5「事实纠正」不成立（对象错位） | §1.3-5 | 事实性误判；见必改项 3 |
| N3 | 「旧单壳 APK 指 `mobile.html?as=`」与实测不符（`H5_URL` 无 `?as=`） | §3.4 | 事实偏差；见必改项 4 |
| N4 | §11 回填「§8 所列 4 条修订」与 §8 表 5 行不一致 | §11 第 3 行 vs §8 | 内部不一致；见必改项 5 |
| N5 | 把 Element Plus 默认值 `#409EFF` 说成 `hrm-admin` 的字面量 | §1.1 事实 2 | 精度问题；见必改项 4 |
| N6 | 「收敛到 CI 构建」不可判定（全仓无 CI 配置） | §3.4、§3.7 注 | 验收标准不可判定；见必改项 10 |
| N7 | 影响面漏 `index.html` / `pc.html` / `portal/main.js` / `deploy/docker-demo/portal.html` / `deploy-demo.sh` / `docker-compose.yml` / husky 等门禁配置 | §5.1 / §5.2 | 边界遗漏；见必改项 8 |
| N8 | B7 变更载体不明（演示容器 nginx ≠ 现网 courier-nginx；现网配置真源缺位） | §5.1 / §5.4 | 可行性缺项；见必改项 6 |
| N9 | H4「依据」的 `@admin` 先例类比不成立（alias + fs.allow ≠ sass 相对路径） | §10.1 H4 | 依据不严谨（已列实跑验证，可接受）；见 §1.4 S-1 |

---

## 7. 复评记录

- 复评触发条件：ADR 按必改项 **2、3、4、5、7、8、9、11** 修订后（**非主干，按必改项逐条核对即可，不须整体重评**）；必改项 **1、6、10** 为对应批次开工前置，须在 **B2 / B7 / B6** 开工前闭环。
- **不新增要求**（评估范围冻结于本报告）。
- 仍判「打回」的情形：修订后仍存在（a）A4 未升级为可复核断言即据此放行 B2；（b）真实域名未处置或未经安全工程师形式核对即报审；（c）B7 变更载体仍不明即实施现网变更；（d）ADR 版本变更未重评。
- 上一轮报告（`tech-review-ui-experience.md`）对 R-0 的「须走 ADR 并另行评估」约束**已满足**：本报告即该 ADR 的独立评估。

---

## 8. 是否建议报主智能体审批

**建议报审，须随附本报告第 2 节必改项清单。**

- 分区结论（避免误读）：**整体不打断——未命中「打回」红线**（无 `api.md`/`db.md` 冲突、无编造依据、无硬编码经验值）；次级项（1 条自报纠正不成立、1 处凭据声明违反、1 处验收不可判定、A4 断言强度、B7 载体与隐患、影响面遗漏）标为**须修订/须前置闭环**。
- 报审闸门：结论等级**「有条件通过」**，按 P0.6 / L8 **可进入报审流程**。
- 前置闭环（不闭环不得进入对应批次）：必改项 **1**（B2）、**6**（B7）、**10**（B6）；必改项 **2** 须在报审前修订并**转交网络安全工程师形式核对**。
- 交主智能体裁定的 ADR 既列项（本报告不代裁）：D1–D10 与 A-2 / A-4 / B-3 / B-4 / B-5 / U-A / U-B；其中 **D9（后端会话多端化先行）** 与 **H5/U-5（现网配置核实）** 是 B3 与 B7 的硬前置，须先决断。
- 本角色边界声明：本报告**不含安全实质结论**（安全面归网络安全工程师）、**不含可执行验证**（门禁实跑归测试工程师）、**不代改方案**、**不代授权**；仅出结论等级与必改项 + 验收标准。

---

## 附录 · 本次评估的独立取证命令/方式（供复核）

| 事项 | 取证方式 | 结果 |
| --- | --- | --- |
| 依赖方向 | Grep `hrm-demo` in `hrm-dev/hrm-admin/**` | 0 命中 |
| Token 未接入 | Grep `var(--` count in `hrm-admin/src` | 0 |
| 安卓壳加载方式 | Grep `H5_URL\|assets/h5\|loadUrl` in `hrm-android-shell` | `loadUrl(BuildConfig.H5_URL)`；`assets/h5/*` gitignore + `.gitkeep` |
| CI 通道 | Glob `**/{.gitlab-ci.yml,Jenkinsfile,.drone.yml,.travis.yml,*.yml,*.yaml}` under `hrm-dev` | 仅 `application.yml` / `docker-compose.yml`，**无 CI** |
| 现网配置载体 | LS `hrm-dev/deploy` | `docker-demo/{nginx.conf,Dockerfile,deploy-demo.sh,docker-compose.yml,portal.html}`、`deploy.sh`、`nginx.conf.example`、`hrm-server.service` |
| 端准入 | 读 `ClientAdmissionPolicy.java` L1-45 / L120-163 | `resolveEnd` 唯一归一；`parseEnd` 仅旧值 `H5` 读 `as` |
| 门禁基线 | Grep `919\|942` in `hrm-demo` | 无断言常量（**未运行**，须测试工程师实跑冻结） |
| 规则 §12 | 读 `项目规则1.md:396-409` | 与 ADR §8 引文一致（摘要级） |

---

## 附：ADR v2 复评记录（2026-09-25）

- 评估对象：`hrm-dev/docs/adr-structure-migration.md`（**v2**，2026-09-25 修订；状态栏 L5 自述「已按技术评审必改项 1–11 与自身错误 N1–N9 修订」）
- 评估方：`express-station-tech-reviewer`（与产出方架构师分离，未参与本 ADR 任何内容产出）
- 评审方式：**静态文档级评估**——按上一轮报告必改项的**验收标准**逐条核对 ADR v2 原文，并**独立重跑静态检索取证**（不采信 ADR 自述）。**本机无 JDK / Maven / MySQL / Redis / Android SDK**；未运行 `verify:mock` / `verify:mobile` / `verify:tokens` / `build` / `build:prod` / `lint` / `test` / `e2e` / Android 构建，相关结论一律标注**未运行**。
- 复评依据（**评估范围冻结，不新增要求**）：上一轮报告 §7 复评触发条件所列 **必改项 2、3、4、5、7、8、9、11**。必改项 **1、6、10** 为 **B2 / B7 / B6** 批次**开工前置**，按任务约定**不纳入本次「是否可报审」判定**，仅报告状态（见第 2 节）。

### 附.1 逐条核对表（必改项 2 / 3 / 4 / 5 / 7 / 8 / 9 / 11）

| 必改项 | 验收标准要点 | ADR v2 证据（行号 + 原文摘录） | 独立复核 | 判定 | 未闭环差项 |
| --- | --- | --- | --- | --- | --- |
| **2** 真实域名 | 域名改占位/改「见运维记录」；`kongzhen1` 命中 = 0；转交安全工程师 | L15「本文不记录任何真实凭据、域名、IP、AppKey…**v2 已清除 v1 误入的真实域名（全文真实域名 / IP / 凭据命中 = 0）**」；L264「现网用户（**现网主域名正在使用的路径；域名值见运维记录，不落本文**）」；L439「**现网主域名（值见运维记录，不落本文）** 的现网 `location /`…」；L424（§9 SP7）「本文 v1 曾在 §3.7 / §10.1 落入真实域名，v2 已清除…**请网络安全工程师形式核对**」 | Grep `kongzhen1\|\.com\|\.cn`（不区分大小写）→ **0 命中** | **已闭环**（方案侧） | 无；转交为报审随附动作（安全结论归网络安全工程师） |
| **3** ui-experience 选项② 定性 | 改写 §1.3-5：明确原文定性无误；不得声称「修正既有材料」 | L62「**〔v2 更正〕**…§7.1 R-0 选项② 的定性无误，v1 的「纠正」不成立…原文判其『违反「同一逻辑不得重复实现」』**正确**…本 ADR §2.1 选项② 是「中立共享包」，与原文选项② **不是同一形态（编号巧合）**，故**不构成对既有材料的修正**」；L460「并声明其 §7.1 R-0 选项② 定性无误（本 ADR **v2 已撤回** v1 的「需修正」主张）」 | 读 `ui-experience-optimization.md:583` → 选项② 原文 =「`hrm-admin` 内新增自有 `tokens.scss`（值复用、文件分离，违反"同一逻辑不得重复实现"）」→ 与 v2 陈述一致 | **已闭环** | 无 |
| **4①** 事实 2 精度 | 改为「无 `var(--)` 引用，色值沿用 Element Plus 默认值与少量字面量」 | L34「色值未收敛到 Token，**实为 Element Plus 默认值 + 少量硬编码字面量**（`hrm-admin/src` 实测：`#409eff` **4 处**、`#909399` **6 处**）」+ 同表「全工程 `var(--` 命中 **0**」 | Grep `#409eff\|#909399` in `hrm-admin/src` → **10 命中**（`#409eff` 4：layout/index.vue:6、:218、:232、login/index.vue:6；`#909399` 6：employee/index.vue:778、dashboard/index.vue:121、styles/index.scss:59、department/index.vue:212、login/index.vue:167、station/index.vue:295）→ **计数与 ADR 一致** | **已闭环**（比上一轮报告表述更精确） | 无 |
| **4②** 旧壳 H5_URL | §3.4 改为「旧壳 APK 指 `mobile.html`（不带 `as`）」 | L198「已装旧单壳 APK 的 `H5_URL` 指向 `.../mobile.html`（**不带 `?as=`**；实测 `app/build.gradle`：debug 段为安卓模拟器专用地址（非真实 IP，不入本文）、release 段为 `https://example.invalid/mobile.html` 占位）」 | 读 `app/build.gradle:27` = `http://10.0.2.2:5188/mobile.html`、`:33` = `https://example.invalid/mobile.html` → 均**无 `?as=`** | **已闭环** | 无 |
| **5** §8/§11 数字一致 | 统一「4 条修订 + 1 条补充」，两处一致 | L409「上述修订共 **4 条修订 + 1 条补充**：**修订** = §12.1 / §12.3 / §12.5 / §12.8；**补充（无需修订本体）** = §12.7」；L460「§8 所列 **4 条修订 + 1 条补充**（修订：§12.1 / §12.3 / §12.5 / §12.8；补充：§12.7）」 | §8 表 5 行（L402–406），§12.7 自标「无需修订」→ 两处表述一致 | **已闭环** | 无 |
| **7** 新增风险登记 | §6 风险表或 §3.7 补「产物被覆盖致旧路径失效」 | §6.1 L350「**R新-1** | **演示站产物被覆盖 → 旧入口失效** | 触发条件：B7 之后**重新构建 / 发布 `hrm-demo` 并覆盖旧 `dist`** | 影响：破坏「只增不改 → 零中断」前提… | 缓解：**迁移期冻结 `hrm-demo` 发布**…或对旧产物做**只读快照**」；L265 交叉引用「旧路径由**演示容器旧产物**继续提供 → 故迁移期须**冻结演示站发布**（见 §6 R新-1）」 | 检索 §6.1 表 → R新-1 条目齐备（触发条件 / 影响 / 缓解） | **已闭环** | 无 |
| **8** 影响面补漏 | §5.1/§5.2 补 4 类资产 + 影响判定；`portal.html`、`portal/main.js` 明确「谁改 + 属哪批」 | L305「`hrm-demo/index.html`（端选择页） / `hrm-demo/pc.html`（v2 补）| 迁移期不改…归属 **B8**」；L306「`hrm-demo/src/portal/main.js`（v2 补）| **改（终态）**：硬编码旧入口链接（`pc.html`、`mobile.html#/login?as=boss` / `as=station`，**L24/31/38**）…归属 **B8**；**执行角色 = 前端工程师**」；L309「`deploy/docker-demo/portal.html`（v2 补）…（**L30–32**）…归属 **B8**；**执行角色 = 运维**」；L310「`deploy-demo.sh` / `docker-compose.yml`（v2 补）…**迁移期不改**…**B8** 视处置定」；L311「门禁资产：…`.husky/pre-commit`、`lint-staged.config.js`、`eslint.config.js`、`stylelint.config.cjs`、`commitlint.config.cjs`、`vitest.config.mjs`（v2 补）」 | LS `hrm-demo/` → `index.html` / `pc.html` / `.husky/pre-commit` / `lint-staged.config.js` / `eslint.config.js` / `stylelint.config.cjs` / `commitlint.config.cjs` / `vitest.config.mjs` **均存在**；`portal/main.js` L24/31/38 与 `docker-demo/portal.html` L30–32 行号吻合 | **已闭环** | 无 |
| **9** NFR 量化 | §3.6/§5.3 补构建耗时与体积对照值（测试实跑、不设硬指标）；§3.7 B7 补切换期观测判据 | L241「**NFR 量化（v2 补，必改项 9；由测试工程师实跑出具，本 ADR 不预填数字）**」；L242「**构建耗时 / 产物体积对照表：**…**判定口径：** 不作硬指标，**但不得出现单端产物体积显著劣化**…（登记落 `update-log.md` 或测试报告）」；L315 §5.3 引用「由测试工程师实跑出具」；L266「**切换期观测判据（v2 补，必改项 9；任一命中即按 B7 回滚）：** ① 新入口 4xx/5xx 率超阈值 ② 深链刷新失败 ③ `.map` 可公开下载探测命中 ④ 新入口资源缺失回退 HTML 致白屏」 | 检索 §3.6 / §3.7 → 两项均落位，措辞可判定 | **已闭环** | 无 |
| **11** verify:mobile 分端下限 | 补「每端 ≥ 同源断言子集下限，且两端合计 ≥ 48；子集切分须先出对照表」 | L231「**每端 ≥ 其同源断言子集下限**（分端下限由「拆分前后断言清单对照表」逐项确定，**先出表后开工**）；且 **两端合计 ≥ 48**」；L243「**`verify:mobile` 分端下限对照表：**…**未出表不得开工 B3 / B4**」；L315 §5.3 同口径复述 | 三处（L231 / L243 / L315）表述一致，无「单端 0 / 另端 48」漏洞 | **已闭环** | 无 |

### 附.2 必改项 1 / 6 / 10 前置状态（批次开工前置，**不纳入本次报审判定**）

| 必改项 | 阻塞批次 | 当前是否可判定 | 证据（ADR v2 行号 + 原文摘录） |
| --- | --- | --- | --- |
| **1**（A4 删除演练） | **B2** | **是**（v2 已改为四步可复核断言 + 表述降级 + 边界声明） | L110 A4「**（a）移出/删除**：把 `hrm-dev/hrm-demo` **移出工作区或删除**（**非仅重命名**）→ **（b）清装**：三端 `apps/*` 与 `hrm-admin` 均执行 `npm ci`…**并清空各自 `dist`** 后跑 `build` / `build:prod` → **（c）产物级断言**：各 `dist` 内检索 `hrm-demo` **= 0**，且 `build:prod` 产物**无 Mock chunk**…」；L114「A4 是「生产不依赖演示工程」**最接近充分的操作性证据**（v1 曾称「唯一直证」…故降级）」；L116「**A4 的边界声明（(d)）：** A4 仅覆盖**构建期**依赖…**运行期与部署期**依赖…由 §10.1 假设（H5）、§5.4 部署面与 §9 安全清单承接」；L253 B2 验收已引四步 |
| **6**（B7 变更载体） | **B7** | **是**（载体三分且现网载体声明「真源不在仓库、以运维记录为准」） | L293「**(a) 演示容器内配置** `docker-demo/nginx.conf`——其 root 服务**演示站自身**，**B7 不需要同步**；**(b) 模板** `nginx.conf.example`（产线样例，非现网真源）；**(c) 现网变更载体** = 现网 `courier-nginx`，其**配置真源不在仓库**，B7 实施时**以运维记录为准**…」；L321–322 §5.4「**变更载体 = 现网 `courier-nginx`**（配置真源**不在仓库**，以运维记录为准；B7 前由运维核实并登记）；**演示容器 `deploy/docker-demo/nginx.conf` 不在本批变更范围**」；L258 B7 前置「**变更载体明确**（§5.4：载体为**现网 `courier-nginx`**…）」 | 独立复核 `deploy/docker-demo/nginx.conf:1` = 「三端演示 Demo · 容器内静态站」→ 确证**非**现网真源 |
| **10**（无 CI 通道） | **B6** | **是**（已去「收敛到 CI」，改为「静态审查 + 标注未运行 + 收敛到具 Android SDK 环境」） | L199「**v2 修订（去「收敛到 CI」）：** 全仓**无任何 CI 配置**（无 `.github/workflows` / `.gitlab-ci.yml` / `Jenkinsfile`），故 B6 验收 = **静态审查 + 标注未运行**…**本批次不声称可交付**（与 §12.8 一致）」；L247「**全仓无 CI 配置，禁止写「收敛到 CI」**，必改项 10」；L257 B6 验收「**静态审查（可判定）**：①…②…③…**运行期验收…标注未运行，收敛到具 Android SDK 环境，本批次不声称可交付**」；L352 R新-2 同口径 |

### 附.3 新结论等级

**结论等级：通过**（报审准入：**可报主智能体审批**）

判定理由：
1. **复评范围内 8/8 必改项全部闭环**（附.1 逐条取证，含独立重跑静态检索），未发现新的主干缺陷，未引入与 `api.md` / `db.md` 的未声明冲突。
2. **未命中打回红线**：上一轮报告 §7 所列仍判打回情形——(a) A4 未升级即可复核断言；(b) 真实域名未处置 / 未转交；(c) B7 变更载体仍不明；(d) 版本变更未重评——**逐条均不成立**（分见附.1 必改项 2、附.2 必改项 1/6、附.4 版本声明）。
3. **闸门合规**：按 `项目规则1.md` §8 第 8 条（L223–229）与调度规则 **P0.6 / L8 / R25**，本 ADR 属**方案阶段产物**，其独立评估（本报告）已完成且结论为「通过」，**具备报审资格**；产出方（架构师）与评估方（本角色）分离，未由同一主体兼任，符合 §8 第 8 条 ③ 与反模式 A22/A23。

> **说明（不阻断报审）：** 必改项 **1 / 6 / 10** 为 **B2 / B7 / B6** 批次**开工前置**（属批次执行阶段动作，非方案主干缺陷），其 ADR 层表述已在 v2 改为可判定；按本次任务约定**不纳入「是否可报审」判定**，随对应批次开工前闭环。

### 附.4 复评结论绑定版本

- **绑定对象：** ADR **v2**（`hrm-dev/docs/adr-structure-migration.md`，2026-09-25 修订版）。本结论**仅对 v2 有效**。
- **重评触发：** 方案版本再变更（含正文任一决策 / 批次 / 裁定结论 / 契约关系变化）→ **须重新提交技术评审**（`项目规则1.md` §8 第 8 条 ④、调度规则 L8「评估结论绑定方案版本」）。
- **随报审转交（安全面，非本角色结论）：** 必改项 2 的**真实域名历史入库形式核对**须转交**网络安全工程师**（ADR §9 SP7 已登记）；本报告**不含安全实质结论**，安全判定归网络安全工程师。

### 附.5 事实性纠正（本轮复评发现）

1. **上一轮报告自身 1 处取证错误（须更正）**：`tech-review-structure-migration.md:59`（§1.1 P-1）与**必改项 4①** 称「`#409EFF` 在 `hrm-admin/src` **无出现**（它是 Element Plus 的默认变量值，不是 `hrm-admin` 写的字面量）」。**实测不符**：`hrm-admin/src` 存在 `#409eff` **4 处**、`#909399` **6 处**（大小写不敏感共 10 处，见附.1 必改项 4① 复核）。ADR v2 L34 的计数**正确**，上一轮该处事实依据系误判——**须以本轮实测为准**。
2. **ADR v2 其余关键行号 / 引证经独立复核一致**（附.1、附.2 全部「独立复核」列），未发现新的无源结论或与实测不符项。

---

## 附：ADR v3 复评记录（2026-09-25）

- **评估对象：** `hrm-dev/docs/adr-structure-migration.md`（**v3**，2026-09-25 修订；状态栏 L5 自述「v3（非主干修订：落地后事实回填 + 回滚点补全；决策与裁定结论不变）」）
- **作者：** 架构师 `express-station-architect`；**评估方：** `express-station-tech-reviewer`（与产出方分离，未参与本 ADR 任何内容产出，符合 §8 第 8 条 ③ 与 A22/A23）
- **复评依据：** `项目规则1.md` §8 ④ 与调度规则 **P0.6 / L8 / R25**「评估结论绑定方案版本，方案版本变更须重评」——本任务即该重评。**评估范围冻结**于 v3 自述的三类改动（① §3.7 四处回滚点补全；② 执行期事实回填；③ 新增文末「v3 修订记录」），**不新增无关要求**（反模式「评估范围漂移」）。
- **评估方式：** **静态文档级评估**——逐条对照 ADR v3 原文行号 + 独立重读事实来源（`update-log.md` / `deploy.md` §11 / `project-tree.md` §2.1 / `portal.html` / `portal/main.js`），**不采信 ADR 自述**。本机 **无 JDK / Maven / MySQL / Redis / Android SDK**，未运行 `verify:*` / `build*` / `lint` / `test` / `e2e` / 现网核查，相关结论一律**未运行**；服务器事实以 `update-log.md` 与 `deploy.md` §11 记载为输入。

### 复1 · 四处回滚点逐条核对（必改项 1 遗留的 B3/B4/B5/B6）

| 批次 | 要求（`update-log.md` 实测回滚点） | ADR v3 证据（行号 + 原文摘录） | 独立复核 | 判定 |
| --- | --- | --- | --- | --- |
| **B3** | `update-log.md:91`：① 删 `apps/staff-h5/`；② **还原 `hrm-demo` 5 文件**（`src/mobile/views/message/{MessagePage,NoticeReader}.vue`、`src/mobile/router/index.js`、`vite.config.js`、`vitest.config.mjs`）+ **复原** `src/mobile/views/staff/kpi.vue`；③ 还原 `hrm-clients/e2e-utils/harness.js` | **L254**：「**回滚（v3 补全，实测）**：① 删除 `apps/staff-h5/`；② 还原 `hrm-demo` **5 个文件**（…四文件…），并**复原** `src/mobile/views/staff/kpi.vue`（原改为 `src/mobile/views/kpi/`）；③ 还原 `hrm-clients/e2e-utils/harness.js`；④ 撤销 `packages/shared/src/ui/` 中立页提升（`MessagePage`/`NoticeReader`/`KpiDetail`）。…线上零影响」 | 逐项与 `update-log.md:91` 逐字吻合；**⑤ 比对 `update-log.md:87` B3 产出**（「`packages/shared/src/ui/`（13 文件…）」）→ ADR 第 ④ 项（撤销中立页提升）为**合理超集**（update-log 回滚段未列，但 B3 产出确实新建该目录，回滚须撤销） | **完整**（ADR 4 项 ⊇ update-log 3 项，无缺项） |
| **B4** | `update-log.md:71`：① 删 `apps/boss-h5/`；② **还原 `hrm-clients/package.json`**（去 `verify:tokens` boss 目标与 `e2e:boss`）+ `package-lock.json`；注明本批未改 `hrm-demo`/`apps/staff-h5`/`e2e-utils` | **L255**：「**回滚（v3 补全，实测）**：① 删除 `apps/boss-h5/`；② 还原 `hrm-clients/package.json`（去 `verify:tokens` 的 boss 目标与 `e2e:boss`）+ `hrm-clients/package-lock.json`。本批**未改** `hrm-demo` / `apps/staff-h5` / `e2e-utils`（与 B3 不同，**无 `hrm-demo` 反向 diff**）；**线上零影响**」 | 逐字吻合 `update-log.md:71` | **完整** |
| **B5** | `update-log.md:61`：① 删 `apps/web/`；② **还原 `hrm-clients/package.json`**（去 web 目标与 `e2e:web`）；③ **还原 `package-lock.json`（+44 包）**；④ 可选 `npm ci`；无需还原 `hrm-demo`/`hrm-admin`/Nginx | **L256**：「**回滚（v3 补全，实测）**：① 删除 `apps/web/`；② 还原 `hrm-clients/package.json`（去 web 目标与 `e2e:web`）；③ 还原 `hrm-clients/package-lock.json`（**+44 包**）；④ 可选 `npm ci` 清理 workspace 依赖。**无需还原** `hrm-demo` / `hrm-admin` / Nginx（零改动、线上零影响）」 | 逐字吻合 `update-log.md:61`（含 `+44 包`） | **完整** |
| **B6** | `update-log.md:51`：还原 `app/build.gradle`（恢复 `buildTypes` 两处 `H5_URL`）→ **删除 `app/src/staff/`、`app/src/boss/`** → 还原 `app/src/main/res/values/strings.xml`、`local.properties.example`、`README.md`、`BUILD.md` | **L257**：「**回滚（v3 补全，实测）**：① 还原 `app/build.gradle`（恢复 `buildTypes` 内两处 `H5_URL`）；② **删除 `app/src/staff/`、`app/src/boss/`**（flavor 源集）；③ 还原 `app/src/main/res/values/strings.xml`、`local.properties.example`、`README.md`、`BUILD.md`。**完全可逆、未发布、无线上影响**」 | 逐项吻合 `update-log.md:51`；`app/src/{staff,boss}/`（flavor 源集）已含 | **完整** |
| **§6 回滚总表** | 与 §3.7 各批一致，可独立复核 | **L340**：「B3 / B4 / B5 \| 删除对应 `apps/*` 目录 **+ 还原 `hrm-clients/package.json` / `package-lock.json`**；**B3 另**还原 `hrm-demo` 5 文件 + `e2e-utils/harness.js` + 撤销 `packages/shared/src/ui`（**详 §3.7 各批回滚点**，v3 补全）」；**L341**：「B6 \| 还原 `build.gradle` / 壳配置…**+ 删除 `app/src/staff/`、`app/src/boss/` + 还原 `strings.xml` / `local.properties.example` / `README.md` / `BUILD.md`**（**详 §3.7 B6**，v3 补全）」 | 与 §3.7 L254–L257 方向一致、并显式指向详版；`update-log.md:37` B7 遗留项所列缺项（`hrm-clients/package.json`、`package-lock.json`、`hrm-demo` 5 文件、`e2e-utils`、flavor 源集、`local.properties.example`）**均已落位** | **完整** |

**复1 小结：** 四处回滚点**均已补全且与 `update-log.md` 实测记载一致**（B3 为合理超集），`update-log.md:17 / :37` 所列「B3/B4/B5/B6 回滚点不完整」**已闭环**。

### 复2 · 执行期事实回填核对

| 事项 | 要求（权威记录） | ADR v3 证据（行号 + 原文摘录） | 独立复核 | 判定 |
| --- | --- | --- | --- | --- |
| 站点根实落路径 | `update-log.md:23`：宿主 `/data/www/download/hrm-clients/{web,staff,boss}`（文件数 167/80/97，`map=0`） | **L324**：「**〔v3 回填〕实落偏差（正式确认）：** B7 实际落点为宿主 **`/data/www/download/hrm-clients/{web,staff,boss}`**（三端文件数 **167 / 80 / 97**，`map=0`）——**非**本示例…**原因：** `courier-nginx` 仅挂载 `/data/photos`、`/data/www/apk`、`/data/www/download`…」 | 路径与文件数与 `update-log.md:23` 一致；与 `deploy.md:1032–1037`（§11.1 站点根表 + 「为何是 `/data/www/download/hrm-clients/`」）一致 | **一致** |
| 变更载体路径 | `update-log.md:25`：宿主 `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`（`ro` 挂载进容器） | **L322**：「**〔v3 回填〕必改项 6 最终答案（执行期事实）：** 变更载体实为宿主 **`/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`**（`ro` 挂载进 `courier-nginx`）…」；**L323**：「**〔v3 回填〕已闭环：** 现网载体路径 = 宿主 **`/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`**（`ro` 挂载进 `courier-nginx`，容器内可见）；核实方法（`docker inspect` 查挂载 / `nginx -T` 看生效配置）…已登记 `deploy.md` §11 与 `update-log.md`」 | 路径与 `update-log.md:25` 一致；核实方法与 `deploy.md:1053/1056`（§11.2）一致；必改项 6（v1/v2 遗留）**已闭环** | **一致** |
| 单文件 bind mount 保 inode | `update-log.md:25`：「单文件 bind mount **必须原地改写（`cat >`）保 inode**，用 `mv` 换文件容器看不到」 | **L322**：「…**关键技术点：单文件 bind mount 必须原地改写（`cat >`）保 inode，用 `mv` 换文件容器看不到**；变更前备份 `nginx.conf.<时间戳>`，**未改动任何既有行**」 | 与 `update-log.md:25` 逐字一致；与 `deploy.md:1059`（§11.2「单文件 bind mount 必须「原地改写」保 inode（重要坑）」）一致 | **一致** |
| B8 判定期起点 | `update-log.md:5`：「**起点 = B7 上线日 2026-09-25**」 | **L183**：「…① B7 上线后新子路径入口连续 **1 个发布周期**（**计时起点 = B7 上线日 2026-09-25**）…」；**L267**：「**`as` 退役节奏（B-4，v2 补；v3 回填执行期事实）：** 计时起点 = **B7 上线日 2026-09-25**…**〔v3 回填〕B8 批实际执行（2026-09-25）：**…当前 **①③ 均未满足**…→ **旧入口（`mobile.html?as=`）与 `as` 兼容读保留**…」 | 与 `update-log.md:5` / `deploy.md:1104`（§11.6「起点 = B7 上线日 **2026-09-25**」）一致；与 `portal/main.js:21` 注「旧 `?as=` 入口未退役（ADR §3.3 退役三项条件未满足）」自洽 | **一致** |
| 两处既有死链处置 | `update-log.md:17`：「`/admin/` 500 与 `/download` 404 两处**既有死链未修**（载体为 `courier-nginx`，修复属 C 档需授权）」；`deploy.md:1097/1098`（§11.5 标题「非本次引入，**待修**」）：`/admin/`「待修（未变更载体）」、`/download`「待修」 | **L356**：「…本轮属 **C 档显式修复**（载体 = 现网 `courier-nginx`，须 P0.5 安全结论 + 主智能体 §10.3 三步授权）。登记以备追溯…」；**表头 L358**「本轮处置（C 档显式修复）」；**L360** 旧-1「**改为 `return 301 /web/`**」；**L361** 旧-2「**只增宿主 `/data/www/download/index.html` 下载页**（**未改 Nginx**）」 | 行号与原文**真实存在**；但 ADR 以「**本轮处置**」+ 完成式「改为 / 只增」登记，而权威执行记录（`deploy.md` §11.5、`update-log.md` B8）均记为**未修 / 待修**；v3 修订记录 **#10（L529）** 依据列自述为「**任务提供的服务器事实**」（非仓库执行记录） | **口径矛盾（见复4(b) / 复9-①）** |

### 复3 · 行号纠正独立复核（实测）

| 对象 | ADR v3 声称 | 实测行号（本次独立读取） | 判定 |
| --- | --- | --- | --- |
| `hrm-dev/deploy/docker-demo/portal.html` 三链接 | **L310**：「现状已改为 `/web/` `/staff/` `/boss/`（**portal.html:31-33**，原记 L30–32 **行号随之回填**）」 | **L31** `<a href="/web/">网页端…`、**L32** `<a class="alt" href="/staff/">驿站助手…`、**L33** `<a class="alt2" href="/boss/">驿站精灵…` | **成立** ✅（L31–33，顺序 `/web/` `/staff/` `/boss/` 吻合） |
| `hrm-dev/hrm-demo/src/portal/main.js` 三处 `link` | **L307**：「三张卡片实际链接已为 `/web/`（**L28**）、`/boss/`（**L35**）、`/staff/`（**L42**）——**行号自 L24/31/38 偏移**」 | **L28** `link: '/web/',`、**L35** `link: '/boss/',`、**L42** `link: '/staff/',` | **成立** ✅（三处 `link:` 行号逐一吻合）；「原记 L24/31/38」（与 `update-log.md:7` 同源、本报告附.1 必改项 8 亦记 L24/31/38）为**历史值，不执行 git 无法复核**，标注**未运行**；偏移量 +4 与现文 L18–22 新增注释块位置自洽 |

### 复4 · 是否引入新的主干缺陷（v3 逐项排查）

| 子项 | 排查方式 | 结论 |
| --- | --- | --- |
| **(a) 与 `api.md` / `db.md` 的未声明冲突** | 通读 v3 全部改动位置（L5–L7、L17、L183、L254–L257、L267、L307、L310、L322–L324、L340–L341、L354–L361、L467、L473、L510–L532）；检索新增内容是否涉接口 / 表结构 / 错误码 | **无** ✅。v3 改动仅涉 Nginx 现网事实、回滚点、文档指针与既有死链登记；`hrm-server` / `sql` 零改动声明未被 v3 触动 |
| **(b) 无源结论（「经验表明」类）** | Grep `经验表明｜一般来说｜业界普遍｜通常情况下｜众所周知｜应该是` 全文 | **0 命中** ✅。v3 新增事实均带「〔v3 回填〕」标注且指 `update-log.md` 条目为据（唯 §6.2 处置依据标「任务提供的服务器事实」，见复2 / 复9-①） |
| **(c) 对已裁定结论（D1–D10 / A-2 / A-4 / B-3 / B-4 / B-5）的擅自改动** | 逐行比对 §7.1（L373–L381）/ §7.2（L389–L393）/ §7.3（L401–L402）与 v2 记录；核对 §1.3-5（L62）撤回声明、§3.4（L192–L193）包名占位、§3.3（L183）B-4 触发条件 | **无擅自改动** ✅。v3 **未触碰 §7 任何一行**；A-2/A-4/B-3/B-4/B-5 结论逐字未变；§1.3-5 仍保持「原文定性无误、本 ADR v2 已撤回」 |
| **(d) 为「好看」删除既有风险登记** | 检索 §6.1（L347–L352）R新-1 / R新-2 是否仍在；核对 §6 回滚表 L338–L343 行数与 v2 | **无删除** ✅。`R新-1`（产物被覆盖）/ `R新-2`（无 CI）**完整保留**；v3 仅**新增** §6.2 小节（L354–L361），`deploy-demo.sh` 头部**新增**冻结发布 banner（`update-log.md:9`，`git diff` 仅新增 `+#` 行） |

**复4 小结：** v3 **未引入主干缺陷**（无契约冲突、无无源结论、无裁定篡改、无风险删除）。唯一新问题为 §6.2 的**状态口径表述**（复2/复9-①），属**表述级、不涉主干正确性**。

### 复5 · §11 回填指针完成状态标注核实（打开文件确认）

| ADR v3 声明 | 对象文件实测 | 判定 |
| --- | --- | --- |
| **L467**：「`hrm-dev/docs/project-tree.md` …**已完成（v3 回填）**：已补 **§2.1 `hrm-clients/` 多端 workspace 树**与端口 / base 表（5191 `/web/`、5189 `/staff/`、5190 `/boss/`）」 | `project-tree.md`：**L98 `### 2.1 多端 workspace：hrm-clients/（2026-09-25 回填，追加）`** 确实存在；L104 起为 `hrm-clients/` 目录树；L219 变更记录载「dev 端口 5191/5189/5190，base `/web/` `/staff/` `/boss/`」 | **属实** ✅ |
| **L473**：「`hrm-dev/docs/deploy.md` …**已完成（v3 回填）**：已新增 **§11「现网实况与 B7 三端入口」**（三端入口与站点根、为何是 `/data/www/download/hrm-clients`、变更载体 + 载体核实方法（`docker inspect` / `nginx -T`）、单文件 bind mount 保 inode、全站 `.map` 404 与缓存头纪律、一键回滚、既有缺陷、B8 判定期），并在 §0.1 加**矛盾标注**」 | `deploy.md`：**L1022 `## 11. 现网实况与 B7 三端入口（2026-09-25 回填）`** 存在，含 **§11.1–§11.6**（L1028/1040/1069/1075/1093/1103）；§11.1 站点根表 L1032–L1034、L1037「为何是 `/data/www/download/hrm-clients/`」；§11.2 L1053 `docker inspect` / L1056 `nginx -T` / L1059 保 inode；§11.3 `.map`；§11.4 一键回滚；§11.5 既有缺陷；§11.6 B8 判定期；**L39 §0.1 矛盾标注**存在 | **属实** ✅ |

### 复6 · 新结论等级与理由

**结论等级：有条件通过**（报审准入：**可报主智能体审批**，须随附本报告复7 必改项 R1）

理由：
1. **复评范围内 v3 三类改动全部核对完毕**：四处回滚点**完整且与实测一致**（复1）；执行期事实回填**四项一致、一项口径矛盾**（复2）；行号纠正**成立**（复3）；§11 指针**属实**（复5）。
2. **未命中打回红线**：无 `api.md`/`db.md` 未声明冲突、无无源结论、无硬编码经验值、无对既定裁定（D1–D10 / A-2 / A-4 / B-3 / B-4 / B-5）的擅自改动、无风险登记删除（复4）。故**不判「打回」**。
3. **存在 1 项不影响主干正确性的必改项**（§6.2 状态口径），符合「有条件通过」定义（存在**不影响主干正确性**的必改项：表述 / 补充依据 / 登记 TODO）。故**不判「通过」**。
4. **闸门合规**：按 `项目规则1.md` §8 第 8 条与调度规则 **P0.6 / L8 / R25**，ADR 属**方案阶段产物**，v3 版本变更**已重评**（即本记录），结论为「有条件通过」→ **具备报审资格**；评估结论**绑定 v3 版本**。

### 复7 · 剩余必改项（1 条，表述级，不阻塞主干）

```
必改项 R1：§6.2 既有死链的「执行状态」与权威记录口径不一致 —— 见复2 / 复9-①
- 问题：ADR v3 §6.2（L354–L361）表头写「本轮处置（C 档显式修复）」，行 旧-1/旧-2 以完成式
  「改为 `return 301 /web/`」「只增宿主下载页」登记，读作「已处置」；而权威执行记录
  `deploy.md` §11.5（L1097「待修（未变更载体）」、L1098「待修」）与 `update-log.md:17`
  （「两处既有死链**未修**…修复属 C 档需授权」）均记为**未执行**；v3 修订记录 #10（L529）
  依据列自述「任务提供的服务器事实」，非仓库执行记录。表述不可判定（易被读作已修复/已授权）。
- 依据：`项目规则1.md` §8 ④（结论绑定版本、方案应可判定）；本报告维度 1（依据充分性）与
  维度 5（风险/状态登记）；`deploy.md:1093–1098`；`update-log.md:17`。
- 验收标准：§6.2 引言 + 表头改为可判定状态表述——「以下为**拟处置方案**（C 档，待 P0.5 安全结论
  + §10.3 三步授权后执行）；**当前状态 = 待修**（与 `deploy.md` §11.5、`update-log.md` B8 一致）」；
  删去/限定「本轮处置」为「拟处置」；依据列改引 `deploy.md` §11.5 / `update-log.md` B8，删「任务提供的
  服务器事实」。**不得**以完成式表述登记未执行的现网变更。
- 复核方式：读 §6.2（L354–L361）+ v3 修订记录 #10（L529），比对 `deploy.md:1097–1098` 与 `update-log.md:17`。
- 阻塞：否（表述级；不影响 A/B 决策与裁定结论；报审须附本必改项）。
```

### 复8 · 复评结论绑定版本

- **绑定对象：** ADR **v3**（`hrm-dev/docs/adr-structure-migration.md`，2026-09-25 修订版）。本结论**仅对 v3 有效**。
- **重评触发：** 方案版本再变更（含正文任一决策 / 批次 / 裁定结论 / 契约关系变化）→ **须重新提交技术评审**（`项目规则1.md` §8 第 8 条 ④、调度规则 L8「评估结论绑定方案版本」）。**R1 为表述级修订，作者按验收标准改毕后，按复评触发条件「逐条核对即可、不须整体重评」**。
- **随报审转交（非本角色结论）：**
  1. **安全面**：v3 新增内容含服务器绝对路径（`/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`、`/data/www/download/hrm-clients/*`）与现网 bind mount 机制——虽**非**凭据声明列举的「凭据 / 域名 / IP / AppKey」，仍建议由**网络安全工程师**形式核对是否构成暴露面（§9 SP7 口径）；**本报告不含安全实质结论**。
  2. **既有死链修复**（`/admin/` `return 301 /web/`；`/download` 增下载页）属 **C 档现网变更**，其 P0.5 安全结论 + §10.3 三步授权归**网络安全工程师 / 主智能体**，本角色**不代授权**。

### 复9 · 事实性纠正（本轮复评发现）

1. **〔口径矛盾，v3 引入〕** ADR `adr-structure-migration.md:354–361`（§6.2 表头「本轮处置」+ L360/L361 完成式）↔ `deploy.md:1093–1098`（§11.5「非本次引入，**待修**」/「待修（未变更载体）」）↔ `update-log.md:17`（「两处既有死链**未修**…修复属 C 档需授权」）：**同一两处死链，ADR 记「本轮处置」、权威记录记「未修 / 待修」** → 见必改项 R1；**标注待运维复核**（本角色不推翻 ADR，只要求状态口径对齐）。
2. **〔上游一致性，非 v3 引入，待主智能体确认〕** ADR `:377`（§7.1 D5）仍写「…**仅剩「两 APK vs 单壳双入口」待定**」，而 `update-log.md:79`（2026-09-25「用户授权推进 ADR 全量 + 三项裁定补登」）已补登「**D5 安卓壳产物形态 = 两个 APK**」（并解锁 B6），§3.7 B6 前置（L257）亦写「D5 裁定」。**ADR §7.1 D5 状态措辞滞后于 `update-log.md`**——**非 v3 改动引入**（v3 未触碰 §7），**不计入本轮必改项**，登记供架构师后续对齐 / 主智能体确认。
3. **〔差异说明，非矛盾〕** §3.7 **B3 回滚点（L254）含 4 项**，较 `update-log.md:91` B3 回滚段（3 项）**多**「④ 撤销 `packages/shared/src/ui/` 中立页提升」。经比对 `update-log.md:87` B3 产出（「`packages/shared/src/ui/`（13 文件…）」）确为 B3 新建 → ADR 第 ④ 项为**合理补全**，**非缺项、非矛盾**，仅标注来源差异。
4. **〔未运行声明〕** `portal/main.js`「原记 L24/31/38」与「`name` 字段逐字未变」（L307）系**历史态**断言，本机不执行 git，**未运行、无法复核**；当前态行号 L28/L35/L42 已实测成立（复3）。

### 复10 · 本轮独立取证方式（供复核）

| 事项 | 取证方式 | 结果 |
| --- | --- | --- |
| 四处回滚点 | 读 ADR `L254–L257` + `L340–L341`，逐项比对 `update-log.md` B3:91 / B4:71 / B5:61 / B6:51 | 逐项吻合（B3 为超集） |
| 执行期事实 | 读 ADR `L183/L267/L322/L323/L324`，比对 `update-log.md:5/23/25` 与 `deploy.md:1032–1037/1044/1053/1056/1059/1104` | 四项一致、一项口径矛盾（§6.2） |
| 门户行号 | Read `hrm-dev/deploy/docker-demo/portal.html` / `hrm-dev/hrm-demo/src/portal/main.js` | `portal.html:31/32/33`；`main.js:28/35/42`（均成立） |
| §11 指针 | Grep/Read `deploy.md`（`## 11`→L1022，§11.1–§11.6）、`project-tree.md`（`### 2.1`→L98，变更记录 L219） | 均**存在** |
| 契约冲突 | 核对 ADR `hrm-server`/`sql` 零改动声明是否被 v3 触动 | 未触动 |
| 无源结论 | Grep 全文 `经验表明｜一般来说｜业界普遍｜通常情况下｜众所周知｜应该是` | 0 命中 |
| 真实域名/IP | Grep `kongzhen1｜\.com｜\.cn｜https?://[0-9]｜IPv4` | 0 命中（凭据声明成立） |
