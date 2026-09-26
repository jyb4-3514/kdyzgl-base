# 技术评审报告：打卡规则 WiFi 白名单「每站仅一条」口径落地

- 评估对象（三份，同一主题）：
  1. **设计规范 v1.1** `hrm-dev/docs/boss-wifi-and-station-design.md`（968 行；作者：UI/UX 设计师）
  2. **接口契约增量** `hrm-dev/docs/api.md` §8（L788–817；作者：后端工程师）
  3. **后端实现** `AttendanceWifiValidator.java`（新增 89 行）、`AttendanceRuleServiceImpl.java`（改，接校验+normalize）、`AttendanceWifiValidatorTest.java`（新增 113 行 / 8 用例；作者：后端工程师）
- 评估方：`express-station-tech-reviewer`（**评估方与产出方分离，未参与任何被评对象产出**）
- 评估方式：**静态文档/代码级评估**——逐条核验引证的 `文件:行号` + 契约基线 + 规则条款。
  **本机无 JDK / Maven / MySQL / Redis（§4.1）**：产物 3 **未编译、未运行、未执行任何单测或门禁**；凡涉「可运行/已验证」的结论一律降级为「**静态审查通过 + 收敛到服务器构建阶段**」，**不视为已验证**。
- 评估日期：2026-09-26
- **结论等级：有条件通过**
- 准入结论：**可报主智能体审批**，但须随附本报告第 5 节必改项清单；其中 **必改项 1（设计↔契约状态同步）、必改项 2（旧数据 >1 条边界）为报审前须闭环项**。报审批时须一并声明「前端收敛未随本批交付」（见必改项 6）。
- 评审结论绑定版本：设计规范 v1.1 / api.md §8 / 后端实现当前磁盘版本。**任一版本变更须重评。**

---

## 0. 结论先行

三份产物在**技术主干上自洽**：`ssid 1–32 / bssid 可空须 MAC / ssid 去重区分大小写 / 至多 1 条` 四条约束在 **api.md §8.1（L793–802）↔ 后端 `AttendanceWifiValidator` L43–73 ↔ 前端 `wifiWhitelist.js` L9–38** 三方**逐条一致**；错误码全走 `400 / ErrorCode.BAD_REQUEST`（**未新增码值**），与既有 Mock 对 `wifiList` 参数错的处理同源（`routes/attendance.js:99`），未污染 91xx 业务码段。设计侧的单条化状态矩阵（八态）、文案清单（T30–T38）、作废痕迹保留（不静默删除）、Tokens 零新增（§12.8）齐备，质量高于既往方案均值。

**未触发「打回」红线**：三份产物均无「经验表明」式无源结论；两处常量均有出处（SSID 32 → IEEE 802.11；条数 1 → 用户口径「每站指定一个」），非硬编码经验值。存在 6 条**非主干**必改项（依证同步 / 边界登记 / 文案登记 / NFR 声明 / 注释勘误 / 交付范围声明），修订后按清单逐条核对即可报审，**不须整体重评**。故判 **有条件通过**。

**须立即上报主智能体的两处事实**（详见第 7 节）：
- 设计规范 §0 **F1（L18）声称「当前打卡规则页 WiFi 区为只读 `<p class="rule-text">{{ wifiText }}</p>`」与当前磁盘不符**——当前 `attendanceRule.vue` **已实现多条可编辑**（L626 `v-for` 多行、L664 `+ 添加白名单`、L632–633 `[编辑][删除]`、`wifiExtraLabel` 带条数），`wifiText` 全仓 0 命中。**依据条目已过期，属引用失真。**
- **单条化的前端收敛未随本批三产物交付**：本批只评审到「设计 + 契约 + 后端」，而真正收敛 UI 的前端（`attendanceRule.vue` / `wifiWhitelist.js`）仍为**多条版**。落地前系统处于「前端多行 ↔ 后端/契约单条」的不一致窗口。

---

## 1. 受理与前置检查

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 有明确版本/日期 | ✅ | 设计 v1.1 / 2026-09-26（`boss-wifi-and-station-design.md:3`） |
| 有可判定验收标准 | ⚠️ 部分 | 设计有走查清单（§12.9）与状态矩阵；但「旧数据 >1 条」等边界无验收项（→ 必改项 2） |
| 与既有契约引用关系已声明 | ⚠️ 部分 | 设计声明「不动接口契约/不改后端」（§12.2 L737），**与产物 2/3 实际改契约、改后端相矛盾**（→ 必改项 1） |
| 不含真实凭据 / 业务数据 | ✅ | 全篇示例 SSID 为 `ST001-Express`、BSSID 为 `AC:84:...`；**未出现真实凭据**（§7.2.1 / §10.4 未触发） |
| 独立性 | ✅ | 评估方未参与三份对象产出；产出方为 UI/UX 设计师 + 后端工程师 |

---

## 2. 六维逐项结论

| 维度 | 结论 | 关键证据 |
| --- | --- | --- |
| 1 依据充分性 | **基本满足**（1 处核心引证失真） | 见 2.1；F1 过期 |
| 2 边界合理性 | **基本满足**（2 处边界未覆盖） | 见 2.2 |
| 3 NFR 覆盖度 | **部分满足**（性能/可观测性未声明；安全面已声明） | 见 2.3 |
| 4 复杂度与可行性 | **满足**（无过度设计；1 处结构风险需登记） | 见 2.4 |
| 5 假设与风险登记 | **部分满足**（关键风险缺登记） | 见 2.5 |
| 6 与既有契约一致性 | **基本满足**（设计↔契约状态不同步、Mock 分叉） | 见 2.6 |

### 2.1 维度 1 · 依据充分性

- **常量均有出处**：SSID 32 → IEEE 802.11（设计 §12.7 L906；`AttendanceWifiValidator.java:26`）；条数 1 → 用户口径「每个站点指定一个」（设计 §12 L719）。**无无源结论、无硬编码经验值**。
- **契约↔实现逐条吻合**（抽查全部命中，未发现伪造行号）：

| 约束 | api.md §8.1 | 后端实现 | 判定 |
| --- | --- | --- | --- |
| ssid 必填、trim 后 1–32 | L798 | `isBlank` + `length()>32`（L52–59） | ✅ 一致 |
| bssid 可空、非空须 MAC（大小写不敏感） | L799 | `BSSID_PATTERN`（L31、L61） | ✅ 一致 |
| ssid 去重**区分大小写** | L801 | `seen.add(ssid)` 不做小写化（L64–67） | ✅ 一致，且与判定 `w.ssid===wifiSsid`（`attendanceStore.js:1090`）同源 |
| 至多 1 条 | L797 | `MAX_ENTRIES=1`（L29、L69–71） | ✅ 一致 |
| 错误文案 6 条 | L810–815 | L50/54/58/62/66/70 | ✅ **逐字一致** |
| 校验顺序 字段→去重→条数 | L802 | 循环字段+去重后再判条数（L48–71） | ✅ 一致 |

- **失真项（阻塞级）**：设计 §0 **F1（L18）** 称当前页为只读并引 `attendanceRule.vue:20 / :78-81 / :440-444`，当前该文件 L20 为 `import { useAuthStore }`、正文为多条可编辑（L626 / L632–633 / L664），**`wifiText` 全仓 0 命中** → 引证与磁盘不符（→ 必改项 1-b）。
- **单位口径瑕疵**：设计 §12.7（L906）与后端注释（L26）述「SSID 32 字符（IEEE 802.11 上限）」，但 802.11 的上限是 **32 字节（octet）**；实现用 `String.length()`（字符/UTF-16 单元）。`wifiWhitelist.js:9` 甚至写「32 字节」。**来源与实现单位不统一**（→ 必改项 5 的低危项）。

### 2.2 维度 2 · 边界合理性

**已覆盖且互斥性成立**：八态矩阵（设计 §12.3.3 L823–834）中 1/2/3 由 `length` 与 `editing` 互斥；4/5 为 ActionBar 瞬时态；6 叠加于 3；7 与（1/2/3）叠加；8 由 `auth.isAdmin` 叠加。DOM 分支（L784–816）与矩阵一致（`!hasWifi`/`hasWifi`/`editing` 三态 + notice-bar 优先级 `校验关闭 > 开启但为空`）。

**覆盖充分的三条关键边界**：①「清除」后本地 `wifiList=[]` 不立即请求、由 `dirty` 触发统一 PUT，且 `enableWifi=true` 时落回 T5 提示**但不阻断**（L869–870、L922）；②「开关关 + 白名单空」仅 T4；③「开关开 + 白名单空」T5 且保存未被禁用（L655、L922）。

**未覆盖的边界（阻塞级）**：
- **旧数据 `wifiList.length > 1`**：设计 §12.9（L918）仅规定「UI 只取首条渲染」，**未规定提交口径**（只提交首条？还是原样回传？）。而前端现状会**原样回传全部行**（`attendanceRule.vue:148-149`），后端却对 `>1` 直接 `400`（`Validator L69-71`）。→ 一旦读到历史多条数据并保存，**必然 400 或静默裁剪**（→ 必改项 2）。
- **「未配置态进入编辑中 + `enableWifi=true`」**：此时 `hasWifi=false` → T5 提示条与编辑表单同屏（DOM L776-778 vs L801-815），矩阵未列该叠加态。属可接受（提示 + 表单并存），但须显式声明，避免走查判为「提示位错位」。

### 2.3 维度 3 · NFR 覆盖度

- **性能**：本变更主链路为 O(1) 纯逻辑校验 + 本地表单态，**实际无性能目标需求**，但设计**未显式声明「性能无关」**（→ 必改项 4）。
- **安全（只做「是否声明」的形式核对，实质判定归网络安全工程师）**：写入权限声明充分——`PUT /api/v1/attendance/rule` 仅 **ADMIN**（`AttendanceController.java:71-72`，与设计 F5 L22、api.md §8 L791 一致）；前端只读分支被明确定位为**防御位/前向兼容**，并**明令不得据此宣称「站长可配置」**（设计 §12.2-1 L741、§12.9 L926）。降级态「不伪装能力」声明充分（§3.3 L219–227、§12.5③）。**实质安全结论（越权/暴露面）须由网络安全工程师出具，本报告不代判。**
- **可用性**：降级态「可点 + 明确拒绝 + 不预填」三件套齐（设计 §3.3）；失败态由 http 层统一弹错、页内不叠加（§12.3.3 态 5）——降级不伪装能力，✅。
- **可维护性**：校验单一真源——后端收口为纯函数 `AttendanceWifiValidator`（Javadoc L12–23 说明抽取理由），`validate` 与 `normalize` 同源，`ServiceImpl` 只调用不另写判据（L186-189 / L258-261），✅；前端同为纯函数 `wifiWhitelist.js`，文案集中在 `SSID_*_TEXT` 常量（L18–21），✅。
- **扩展性**：`TODO(扩展)` 齐（设计 §3.3 L227；后端注释亦标后续）。
- **可观测性**：**未声明**——`400` 校验失败未说明是否需日志/埋点（→ 必改项 4，一并声明即可）。

### 2.4 维度 4 · 复杂度与可行性

- **必要性充分、无过度设计**：单条化本身是**删约束**（`§12.6` 将前端 4 项校验收敛为 2 项）；后端新增的是**一个 89 行纯静态工具类 + 8 条单测**，`O(n)` 且 `n ≤ 1`，**复用既有 `ErrorCode.BAD_REQUEST`、未新增错误码、未新增表/字段/接口**（设计 §12.2 L737 声明「请求体结构不变」成立）。以「纯函数 + 单测」而非「散落 if」实现，符合「复用/单一真源」（通用准则 §2）。
- **结构风险（须登记）**：`api.md` §8 是**文末追补段**（L788-790 自述「一期 api.md 原未收录考勤域端点；本节为追补，只固化…不动其它段落」），但 §4.0 接口概览仍为「24 个」（L163）且未含考勤端点、§2.1 分段规则（L73-81）**未收录 9xxx 段**。**未来考勤域正式收录时会与 §8 编号/概览重叠**（→ 必改项 3）。

### 2.5 维度 5 · 假设与风险登记

设计 §11.2（L709-712）、§12.7（L903-908）保留了待裁决叙事与「作废不删除」痕迹（§12.4 L850-860、§12.10 L931-954），**追溯性良好**。但以下**已知风险未登记**：

| 未登记风险 | 触发条件 | 影响 |
| --- | --- | --- |
| 旧数据 `wifiList>1` 保存即 400 / 或静默裁剪 | 历史规则含多条且用户在 H5 保存 | 保存失败或数据被裁剪（→ 必改项 2） |
| 前后端文案两套并存 | 行内校验（T14/T15/T16）与 400 message（api.md §8.2）同时可见 | 用户困惑（→ 必改项 4/待裁决 C） |
| Mock 与真实后端校验分叉 | 演示（Mock）下 2 条 / 非法 MAC **不报错**，真实后端报 400 | 「演示能存、线上 400」认知差（→ 必改项 3） |
| 32 字符 vs 32 字节 | 非 ASCII / 多字节 SSID | 与 802.11 客观上限不符（→ 必改项 5） |

### 2.6 维度 6 · 与既有契约一致性

- **与 `attendanceStore.js:1090`**：判定为 `rule.wifiList.some((w) => w.ssid === wifiSsid)`（**数组 any + 精确大小写**）。单条化不破坏该契约；去重口径（区分大小写）与之一致，✅。**注意**：任务背景所述「`w.ssid === wifiSsid` 精确比对」省略了外层 `.some`（多条 any 语义），详见第 7 节。
- **与 `verify-mock.mjs`**：L969-970 断言 `rule1.result.wifiList.length === 1`、L981 取 `wifiList[0].ssid` —— 默认种子本就 1 条（`attendanceStore.js:196-198`），**单条化不使该断言失败**，✅。
- **与 `routes/attendance.js:95-99`（Mock 校验）**：Mock 仍只校验「数组 + ssid 非空」，**未补 32/MAC/条数**；而 api.md §8 + 后端已补 → **Mock 与真实后端口径分叉**（设计 §12.2 声明「不改 Mock」，但后端已改）（→ 必改项 3）。
- **与错误码段**：91xx 现用 9101–9109（`errorCode.js:104-115`；`server-architecture.md:920` 记「91xx=考勤/排班/补卡 9101~9109」）。本批**未新增码值**、全走 `400`（`ErrorCode.java:20`），与 Mock 对 `wifiList` 参数错的既有处理同源（`routes/attendance.js:99`），**未冲突**，✅。
- **与 `db.md`**：`wifi_list` 为 JSON 内嵌、无表结构变更（`WifiEntry.java:6-8`），**未触达 db.md**，✅（未逐字读 db.md，标「未核」）。
- **设计↔契约状态不同步（阻塞级）**：设计 §12.2 L737「不动接口契约、不改 Mock、不改后端」、§12.7 L905「**请裁决是否纳入本批**」、§1.2 L59「后端校验补齐列为待裁决项」、§11.2② L710「请裁决」——**均与「api.md §8 已固化 + 后端已实现」矛盾**（→ 必改项 1）。

---

## 3. 待裁决点结论建议（本角色只给建议，不裁定口径，最终由用户/主智能体决定）

- **A · `enableWifi=true` 且白名单为空：fail-open vs fail-closed**
  **建议维持 fail-open（允许保存）**。理由：三份产物口径一致（设计 §3.4 L237、api.md §8 L817、实现注释 `ServiceImpl:191-193`），且缓解面已声明（前端 warning + 仅 ADMIN 可写）。**但须登记**：此属**产品口径**，来源仅为设计 §3.4，**未经用户确认**，按 P1 / §11.4「口径先行」建议主智能体转用户确认；若用户判「全员打卡失败风险不可接受」，则改 fail-closed（触发条件：`enableWifi=true` 且合并当前值后白名单为空）。
- **B · 是否新增字段级错误码（9110/9111/9112）**
  **建议不新增，维持全走 `400`**。理由：① 与既有 Mock 对同类参数错的选用同源（`routes/attendance.js:99`）；② 复用 `ErrorCode.BAD_REQUEST` 无需改动 api.md §2.1 分段规则 / §2.2 明细 / `errorCode.js` **三处**，避免新增漂移面；③ 客户端可按 `message` 分流字段。若将来确要字段级可编程分流，再单独走契约变更评估。
- **C · 前后端文案是否统一**
  **建议「登记为已知差异、不强制逐字统一」**。理由：前端行内文案（T14/T15/T16）面向表单即时反馈、后端 message 面向 API 消费方，受众不同；强制统一属无收益 diff（通用准则 §2）。**但须在设计 §12.6 显式登记「两套文案并存 + 各自真源」**，避免后续误判为口径漂移（→ 必改项 4）。

---

## 4. 结论等级

| 等级 | 判定 | 依据 |
| --- | --- | --- |
| ~~通过~~ | ✗ | 存在 6 条非主干必改项（依证同步 / 边界登记 / NFR 声明等） |
| **有条件通过** | ✅ **本次结论** | 无源结论/硬编码经验值**均未触发**（不达打回红线）；必改项均属**表述同步、边界/风险登记、注释勘误**，不影响单条化主干正确性 |
| ~~打回~~ | ✗ | 未发现影响主干正确性的无源结论、未声明契约冲突或错误码污染 |

**报审准入**：**可报主智能体审批**，随附第 5 节必改项清单；**必改项 1、2 为报审前须闭环项**；报审时须声明「前端收敛未随本批交付」（必改项 6）。

---

## 5. 必改项清单（交原作者修订，评估方逐条复核）

> 格式：问题 → 为什么是问题（条款号）→ 验收标准（可判定）→ 复核方式。

**必改项 1（阻塞）· 设计规范对「契约/后端是否变更」的状态与产物 2/3 自相矛盾**
- 问题：设计 §12.2（L737）称「不动接口契约、不改 Mock、不改后端」；§12.7（L905）与 §11.2②（L710）仍将后端下沉校验列为「**请裁决是否纳入本批**」；而 api.md §8（L788-817）已固化、后端已实现（`AttendanceWifiValidator` L69-71 + `ServiceImpl` L186-189）。
- 为什么是问题：同属一个方案阶段产物集合却状态互斥，违反契约一致性（tech-evaluation 维度 6）；报审时会口径混乱。
- 验收标准：设计新增「后端下沉落地对照」小节，逐条给出 §12.6 / §12.7 / §1.2 / §11.2 的**最终状态**（已纳入本批）并**指向 api.md §8 与实现类/方法**；删除全部「请裁决是否纳入本批」的未决表述。
- 复核方式：读设计 §12.7 与 §1.2，状态应与 api.md §8 一致；Grep 设计全文「请裁决是否纳入本批」应 0 命中。

**必改项 1-b（阻塞）· 设计 §0 F1 引证失真**
- 问题：F1（L18）称当前页只读并引 `attendanceRule.vue:20 / :78-81 / :440-444`；实测 L20 为 import、正文为多条可编辑（L602/626/632-633/664），`wifiText` 全仓 0 命中。
- 为什么是问题：依据充分性要求引证可查（维度 1）；失真的「已核实事实」会误导实现。
- 验收标准：F1 更新为当前磁盘事实（当前已达**多条可编辑**：`wifiExtraLabel` 计数、`+ 添加白名单`、`[编辑][删除]`），并据此说明「单条化需回落既有前端实现」；行号重取。
- 复核方式：按新行号逐一 Read `attendanceRule.vue`，内容须吻合。

**必改项 2（阻塞）· 「旧数据 `wifiList.length > 1`」边界处置未覆盖**
- 问题：设计 §12.9（L918）只规定「UI 只取首条渲染」，未规定**提交口径**；前端现状原样回传全部行（`attendanceRule.vue:148-149`），后端对 `>1` 直接 `400`（`Validator L69-71`）。
- 为什么是问题：读到历史多条数据的用户保存时，将 400 失败或静默裁剪（维度 2 + 维度 5）。
- 验收标准：设计 + api.md §8 明确：① 读入 `>1` 条时前端**加载策略**（取首条 / 提示）；② **提交口径**（只提交首条 or 全量）；③ 与后端 400 的关系（正常路径不产生 400）。三者写入 §12 与 api.md §8，并进风险登记表。
- 复核方式：读设计 §12 + api.md §8，三点齐备且互不矛盾；前端提交分支与之对应。

**必改项 3（阻塞）· Mock 与真实后端校验口径分叉未声明**
- 问题：设计 §12.2 声明「不改 Mock」，但 api.md §8 + 后端已补 32/MAC/条数；`routes/attendance.js:95-99` 与 `attendanceStore.js` 均未同步。另 api.md §8 追补段与 §4.0 概览（24 个，L163）/ §2.1 分段规则（L73-81）未建立归属。
- 为什么是问题：演示（Mock）能存多条/非法 MAC、真实后端 400，产生认知差（维度 5 + 维度 6）；追补段未来会被考勤域正式章节覆盖（维度 4）。
- 验收标准：二者选一并落实——(a) **同步更新 Mock** 补同口径校验；或 (b) 设计/api.md **显式声明「本批 Matches 不覆盖该校验，`verify:mock` 不校验该路径」**。另 api.md §8 顶注补 `TODO(扩展): 考勤域正式收录时并入 §4.0 概览与 §2.1 分段规则，本节降为引用`。
- 复核方式：读设计 §12.2 + api.md §8 顶注；或运行 `verify:mock` 观察是否有对应断言（**本次未运行，标「未运行」**）。

**必改项 4（非阻塞）· NFR 与文案差异未声明**
- 问题：设计未声明「性能无关 / 可观测性」，亦未登记前后端两套文案并存。
- 验收标准：设计补一句「本变更为 O(1) 纯逻辑 + 本地表单态，**无性能目标**；不新增监控埋点，400 由既有 http 层承接」；§12.6 登记「前端行内文案（T14/T15/T16）与后端 400 message（api.md §8.2）为两套并存、各自真源」，并写出是否统一的结论（按本报告 §3-C 建议）。
- 复核方式：读设计相应小节，两点齐备。

**必改项 5（非阻塞）· 单位口径与注释勘误**
- 5-1：SSID 上限「32（IEEE 802.11 上限）」实为 **32 字节**，实现按**字符**计数（`wifiWhitelist.js:9` 写「字节」、`Validator:26` 写「字符」、api.md §8.1 写「字符」）。验收：统一表述为「32 字符（与前端 `maxlength=32` 对齐）」，并注明与 802.11 的 32 字节差异及取舍。
- 5-2：`AttendanceRuleServiceImpl.java:192` 注释称「服务端**无法区分**『未提交』与『提交为空』」，与 api.md §8 L791「`null`=沿用现值、`[]`=清空」的差量语义矛盾（服务端可区分）。验收：注释改为与设计 §3.4 一致的**取舍说明**（技术可行但刻意不 fail-closed）。
- 复核方式：读上述三处文件对应行。

**必改项 6（非阻塞，报审时须声明）· 交付范围与验证降级声明**
- 问题：单条化的**前端收敛实现未随本批三产物交付**（前端仍多条）；产物 3**未编译未运行**。
- 验收标准：报审批材料中显式声明 ①「前端收敛待另派前端落地，落地前存在前后端不一致窗口」；② 产物 3 结论一律标「**静态审查 + 未编译/未运行，收敛到服务器 `mvn test` / 构建阶段**」。
- 复核方式：读报审材料，两点齐备即达标。

---

## 6. 风险登记表

| # | 风险 | 触发条件 | 影响面 | 缓解 / 处置 | 状态 |
| --- | --- | --- | --- | --- | --- |
| R1 | 旧数据 `wifiList>1` 保存失败/静默裁剪 | 历史规则多条 + H5 保存 | 打卡规则保存 | 必改项 2 | **待闭环** |
| R2 | `enableWifi=true` 且白名单空 → 全员 WiFi 校验失败（9103） | ADMIN 先开开关后未配名单 | 打卡可用性 | 前端 warning + 仅 ADMIN 可写；**口径来源仅设计 §3.4，未经用户确认** | 待用户裁定（待裁决 A） |
| R3 | Mock 与真实后端校验分叉 | 演示路径写入 2 条/非法 MAC | 认知差 / 验收误判 | 必改项 3 | **待闭环** |
| R4 | 前后端文案不一致 | 行内 + 400 同时可见 | 用户困惑 | 必改项 4（登记为已知差异） | 待修订 |
| R5 | 32 字符 ≠ 32 字节 | 非 ASCII / 多字节 SSID | 与 802.11 上限不符 | 必改项 5-1 | 待修订（低危） |
| R6 | 产物 3 未编译/未运行 | 本机无 JDK/Maven | 编译或单测失败风险 | 服务器阶段 `mvn test` 验证；本报告标「未验证」 | 未验证 |
| R7 | 设计↔契约状态不同步 | 报审时 | 口径混乱 / 受理争议 | 必改项 1 | **待闭环** |
| R8 | 前端收敛未交付 | 报审后 | 前端多行 ↔ 后端单条不一致窗口 | 必改项 6 + 另派前端 | 待处置 |

> **【须转交网络安全工程师】**：fail-open 的实质安全影响（配置导致全员无法打卡 / 是否构成可用性攻击面）、`bssid` 采集与留痕的设备信息敏感性评估——**均未在本报告出具结论，请转网络安全工程师（R24 / P0.5）**。

---

## 7. 上游材料与实测不符（逐条）

| # | 任务描述 / 上游材料 | 实测 | 处置 |
| --- | --- | --- | --- |
| 1 | 设计 §0 F1（L18）：「打卡规则页 WiFi 区当前为**只读** `<p class="rule-text">{{ wifiText }}</p>`，第 20 行有 `TODO(扩展)`」 | `attendanceRule.vue` **已实现多条可编辑**（L602 `wifiExtraLabel` 计数 / L626 `v-for` 多行 / L632-633 `[编辑][删除]` / L664 `+ 添加白名单`）；L20 为 `import { useAuthStore }`；`wifiText` 全仓 0 命中 | 设计 F1 失真 → 必改项 1-b |
| 2 | 任务：「Mock `packages/mock/src/attendanceStore.js` 的 `w.ssid === wifiSsid` 精确比对」 | 实为 `rule.wifiList.some((w) => w.ssid === wifiSsid)`（L1090），即**数组 any + 精确大小写** | 单条化后语义等价、不冲突；但描述省略了 `.some`（多条语义），已记入维度 6 |
| 3 | 设计 §12.2：「单条化是纯前端 UI + 约束收敛 …… **不动接口契约、不改 Mock、不改后端**」 | api.md 已新增 §8（契约变更）、后端已新增 `AttendanceWifiValidator` 并接入（后端变更） | 与产物 2/3 矛盾 → 必改项 1 |
| 4 | 任务：「产物 3 … 未编译、未运行」 | `AttendanceWifiValidatorTest.java:16` 自述「本测试未执行，收敛到服务器阶段」；本机无 JDK/Maven（静态判断） | 核实一致；本报告全程标「未运行」 |
| 5 | 任务：「既有错误码段（91xx 已用至 9109）」 | `errorCode.js:104-115` 确认 9101–9109；`server-architecture.md:920` 记「91xx = 考勤/排班/补卡 9101~9109」 | 核实一致 |
| 6 | 任务：「设计规范 v1.1（原 702 行 → 968 行）」「api.md 文末新增段（约 L788–817）」 | 设计文件 968 行；api.md §8 位于 L788–817 | 核实一致 |

---

## 8. 复评记录

- 复评状态：**待作者按第 5 节修订后复评**。
- 复评范围：**仅核对必改项 1、1-b、2、3、4、5、6**，不新增无关要求（评估范围冻结）。
- 复评判定：必改项 1/1-b/2/3 闭环且 4/5/6 完成后 → **通过（报审）**；任一阻塞项未闭环 → **仍为有条件通过 / 视情打回**。
- 复评触发：被评三份产物**任一版本变更**（含设计行号重取、api.md §8 改动、后端实现改动）→ **重评**。

---

> **本报告为评估方结论，不含方案设计、不含修复代码、未修改任何被评产物、未执行任何 git 命令。** 结论等级与准入结论绑定上述版本；安全实质结论与操作安全评估分别归网络安全工程师与主智能体，本报告不代判、不放行。

---

## 9. 复评（v1.1 修订后）

- 复评对象：设计规范 **v1.1 评审闭环修订版** `hrm-dev/docs/boss-wifi-and-station-design.md`（1075 行）、`hrm-dev/docs/api.md` §8（L787–851）、后端 `AttendanceWifiValidator.java` / `AttendanceRuleServiceImpl.java`（**仅注释级改动，无逻辑/签名/常量值变更**）。
- 复评方式：**独立读盘核对**（Grep + 逐行 Read），**不采信作者自述**；对前次报告 §5 六条必改项 + 三项主智能体裁定逐条取证。
- 复核基线：以**当前磁盘行号**为准；证据均给出文件:行号。
- **验证降级（§4.1）**：本机**无 JDK / Maven** → 后端产物**未编译、未运行、未执行 `AttendanceWifiValidatorTest`（8 用例）**。本批复评对后端一律为**静态核对**，结论收敛到服务器阶段 `mvn test`，**不视为已验证**。
- **复评结论等级：有条件通过（无阻塞项 → 准予报审）**
  - 6 条必改项（含 1-b）**全部闭环**，3 项主智能体裁定（A/B/C）**已如实落文**；无阻塞项。
  - 剩余为 **2 项非阻塞拾遗（N1 引证行号 ±1、N2 前端注释残留）** + **2 项报审声明项（前端未交付、后端未编译）**，均不构成退回。
  - 满足前次报告 §8 约定（阻塞项闭环 → 通过/报审），故**准予报主智能体审批**；因仍有非阻塞拾遗，等级保守记为「有条件通过（无阻塞）」。
- 复评结论**绑定上述 v1.1 修订版**；产物任一版本再变更 → **须重评**。

### 9.1 逐条必改项闭环核对表（独立取证）

| 必改项 | 结论 | 独立核实证据（当前磁盘行号） |
| --- | --- | --- |
| **1（阻塞）设计↔契约/后端状态矛盾** | ✅ **已闭环** | 新增 §12.11「后端下沉落地对照」[boss-wifi-and-station-design.md:971-989](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L971-L989)（D1–D9 逐条→契约→实现）；§12.2 原「不动契约/不改后端」已就地**作废留痕**（[:738](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L738) 删除线 + ❌）；§11.2②③、§12.7 均改为「已落地（无待裁决）」。**Grep「请裁决是否纳入本批」= 0 命中** ✅；残余「请裁决」仅 §11.2①④（站点名册 / 读取 WiFi 形态，与单条化无关，属应保留项）。作废痕迹保留（§12.10 对照表 [:965-969](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L965-L969)） |
| **1-b（阻塞）§0 F1 引证失真** | ✅ **已闭环** | §0 F1 已按磁盘实况重写为「**多条可编辑（非只读）**」并重取行号 [:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L19)。**独立复核**：① `Grep wifiText` 于 `hrm-clients/apps/boss-h5` → **0 命中** ✅；② `hrm-demo/src/mobile/modules/boss/views/attendanceRule.vue` **存在只读版**（computed [:78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-demo/src/mobile/modules/boss/views/attendanceRule.vue#L78)、`<p class="rule-text">{{ wifiText }}</p>` [:442](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-demo/src/mobile/modules/boss/views/attendanceRule.vue#L442)）✅，与 F1「跨文件误引」定性**吻合**；③ F1 引 boss-h5 行号逐一验真：[:18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L18) 为 `wifiExtraLabel` import、[:601-602](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L601-L602) 为 `{{ wifiExtra }}`、[:625-635](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L625-L635) `v-for` 多行 + `[编辑][删除]`、[:638-660](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L638-L660) 编辑态 `maxlength=32/17`、[:664-666](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L664-L666) `+ 添加白名单`、[:148-152](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L148-L152) 原样回传全部行 —— **全部命中，无伪造** |
| **2（阻塞）旧数据 `wifiList>1` 口径** | ✅ **已闭环** | 设计新增 §12.12 [:991-1009](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L991-L1009)：① 加载取首条 + T39 ② 提交恒 0/1（`slice(0,1)`）③ 后端 `>1` 400 属兜底、前端触发即缺陷；新文案 T39 [:1007](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1007)。`api.md` 新增 §8.3 [:822-830](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L822-L830)（加载原样返回 / 提交 ≤1 / 前端收敛）—— **两点一致**（服务端不裁剪、前端取首条，语义互补无冲突）✅。主智能体实测事实已如实引用（8 条规则 / `json_length` 全 = 1 / `>1` 为 0 行，设计 [:995](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L995) 与 api.md [:824](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L824) 两处一致） |
| **3（阻塞）Mock 分叉 + 追补段归属** | ✅ **已闭环** | `api.md` §8 顶注补 `TODO(扩展): …并入 §4.0 概览与 §2.1 分段规则` [:790](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L790)；§8.5 新增**分叉清单表** [:838-851](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L838-L851)。**独立复核 Mock 实况**：[routes/attendance.js:95-99](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/routes/attendance.js#L95-L99) 实为「须数组 + 每条 `ssid` 非空」，**无长度/MAC/条数/去重校验** → 与 §8.5 表 4 行（超长可存 / 非法 MAC 可存 / 多条可存 / 不去重）**逐条吻合** ✅；§8.5 关于 `verify:mock` 断言仅覆盖「默认种子 1 条」的说法，经 [verify-mock.mjs:969-970、981](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/scripts/verify-mock.mjs#L969-L970) 核实属实 ✅。设计 §12.13 R-Mock [:1017](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1017) 同口径 |
| **4（非阻塞）NFR + 文案差异登记** | ✅ **已闭环** | 设计 §12.14 [:1042-1058](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1042-L1058)：① 性能无关（O(1)、无性能目标）② 可观测性（不新增埋点、400 由既有 http 层承接）③ 前后端两套文案各自真源（前端行内优先 / 后端兜底）。`api.md` §8.4 [:832-836](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L832-L836) 同口径，**两处一致** ✅ |
| **5-1（非阻塞）32 字符勘误** | ✅ **已闭环**（前端残留见 N2） | 三处后端表述 + 契约统一为「**32 个字符**（IEEE 802.11 客观上限 32 octets；实现按字符数校验，与 `maxlength=32` 对齐）」：[Validator:17-19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceWifiValidator.java#L17-L19)（Javadoc）、[:27](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceWifiValidator.java#L27)（字段注释）、[:57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceWifiValidator.java#L57)（行内），`api.md` §8.1 [:798](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L798)，设计 §11.2③/§12.7 [:913](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L913) —— **五处一致** ✅ |
| **5-2（非阻塞）注释勘误** | ✅ **已闭环** | [AttendanceRuleServiceImpl.java:191-194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRuleServiceImpl.java#L191-L194) 已改为「**二者可区分，服务端有能力据此 fail-closed，但按产品口径刻意不阻断**」；错误表述「服务端**无法区分**」**已消失** ✅，与 `api.md` §8.2 差量语义 [:820](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L820)、设计 §3.4 一致 |
| **6（非阻塞·报审声明）交付范围/验证降级** | ✅ **已回文** | 设计 §12.14④ [:1056](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1056) 显式声明「前端收敛未随本批交付 + 后端标未编译/未运行」；`api.md` §8.5 声明「本批不覆盖该校验」 |

### 9.2 三项主智能体裁定落文核对

| 裁定 | 是否如实落文 | 证据 |
| --- | --- | --- |
| **A · fail-open 维持 + 可用性风险登记 + 强提示** | ✅ 已落文 | 设计风险 R-A [:1015](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1015)（9103 全员失败）+ 强提示方案 §12.13.1 [:1022-1040](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1022-L1040)（常驻强警示 T40 + 保存前二次确认 T41–T43，**不阻断**）；`api.md` §8.2 风险登记 [:820](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L820) |
| **B · 不新增错误码，全走 400** | ✅ 已落文 | `api.md` §8.2 [:817](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L817)「不新增错误码…91xx 段统一规划」；设计 §12.11 D7 [:983](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L983)；实现 [:189](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRuleServiceImpl.java#L189) `ErrorCode.BAD_REQUEST` |
| **C · 文案不强制统一，登记差异** | ✅ 已落文 | 设计 §12.14③ [:1048-1054](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1048-L1054)；`api.md` §8.4 [:836](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L836) |

### 9.3 复评新发现的不一致（均为非阻塞拾遗，不构成退回理由）

| # | 位置 | 问题 | 严重度 | 建议处置 |
| --- | --- | --- | --- | --- |
| **N1** | 设计 §12.11 D1/D2/D3/D6 [:977-982](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L977-L982)、[:987](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L987) | 后端引用行号相对磁盘**整体偏 1**（指向字段/方法上方的 Javadoc 行）：`MAX_ENTRIES=1` 实为 **L30**（设计写 L29）、`SSID_MAX_LEN=32` 实为 **L28**（写 L27）、`BSSID_PATTERN` 实为 **L32**（写 L31）、`normalize` 方法实为 **L80-90**（写 L79-89）、接入 `normalize` 实为 **L259-261**（写 L258-261）。与 §12.11 自称「行号为当前磁盘版本」不符 | **低**（符号唯一、值一致，不影响可查证性） | 重取为字段/方法声明行（+1）。可随报审或下次改动一并修正 |
| **N2** | [wifiWhitelist.js:9](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/utils/wifiWhitelist.js#L9) | 前端注释仍写「IEEE 802.11 的 SSID 上限即 32 **字节**」，未与已统一口径（「32 字符，与 `maxlength=32` 对齐，802.11 上限 32 octets」）对齐（同文件 [:19](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/utils/wifiWhitelist.js#L19) 用户文案已为「32 个字符」） | **低**（落在**前端未交付窗口**内） | 随前端单条化落地一并改为「32 个字符…」表述。**不作为本批报审前置** |

> 除 N1/N2 外，**未发现新的不一致**：设计 §12.11 的 D1–D9 与 `api.md` §8.1/§8.2、后端 `AttendanceWifiValidator`（`SSID_MAX_LEN=32` / `MAX_ENTRIES=1` / `BSSID_PATTERN` / 区分大小写去重 / 校验顺序 字段→去重→条数）**逐条一致**；`api.md` §8.3 与设计 §12.12 一致；§8.4/§8.5 与设计 §12.14/§12.13 **两两一致**。

### 9.4 剩余必改项与验收标准

**无阻塞必改项。** 非阻塞拾遗 2 项（不作为报审前置，可随报审或前端落地处理）：

```
必改项 N1（低危·引证精度）：设计 §12.11 后端引用行号整体偏 1
  验收标准：D1 MAX_ENTRIES → L30；D2 SSID_MAX_LEN → L28；D3 BSSID_PATTERN → L32；
           D6 normalize 方法 → L80-90；接入 normalize → L259-261
  复核方式：Read AttendanceWifiValidator.java / AttendanceRuleServiceImpl.java 对应行，须落在声明行

必改项 N2（低危·注释残留，属前端未交付窗口）：wifiWhitelist.js:9 仍写「32 字节」
  验收标准：随前端单条化落地改为「32 个字符（与 maxlength=32 对齐；802.11 上限 32 octets）」
  复核方式：Grep wifiWhitelist.js「字节」→ 0 命中
```

### 9.5 报审前须声明事项清单（随附报审批材料）

| # | 声明事项 | 依据 |
| --- | --- | --- |
| 1 | **前端单条化收敛未交付**：`attendanceRule.vue` / `wifiWhitelist.js` 当前仍为**多条版**（`v-for` 多行 + `+ 添加白名单` + 提交原样回传全部行，已核实 [attendanceRule.vue:626/664/148-152](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/modules/boss/views/attendanceRule.vue#L626)）→ 落地前存在「**前端多行 ↔ 后端/契约单条**」**不一致窗口**，须另派前端工程师按 §12.3/§12.12 落地 | 必改项6；设计 §12.14④ [:1056](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md#L1056) |
| 2 | **后端未编译 / 未运行**：本机无 JDK/Maven，后端仅注释级改动、**未执行 `AttendanceWifiValidatorTest`（8 用例）** → 后端结论一律标「**静态核对通过 + 收敛到服务器 `mvn test` / 构建阶段**」，**不视为已验证** | §4.1；本报告 §9 抬头 |
| 3 | **Mock 与真实后端口径分叉**（已知差异，本批不同步 Mock，`verify:mock` 不校验该路径） | `api.md` §8.5 [:838-851](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md#L838-L851) |
| 4 | **T40/T41–T43 / T39 为设计侧新增文案**，随前端落地实现（当前前端无此逻辑） | 设计 §12.12/§12.13.1 |
| 5 | **安全面结论未出**：fail-open 的可用性攻击面 / `bssid` 设备信息敏感性 → **转网络安全工程师（R24 / P0.5）**，本报告不出安全结论 | 本报告 §6 末注 |

### 9.6 复评结论与准入

| 项 | 结论 |
| --- | --- |
| 结论等级 | **有条件通过（无阻塞项）** |
| 报审准入 | ✅ **准予报主智能体审批**，随附本 §9.5 声明清单；**须随报审一并声明前端未交付与后端未编译** |
| 未闭环阻塞项 | **无** |
| 非阻塞拾遗 | N1（引证行号 ±1）、N2（前端注释残留）——不作报审前置 |
| 复评触发 | 被评产物任一版本（设计 / `api.md` §8 / 后端）再次变更 → **须重评** |

> **本节为评估方复评结论，不含方案设计、不含修复代码、未修改任何被评产物、未执行任何 git 命令。** 结论绑定 v1.1 修订版；安全实质结论归网络安全工程师，操作安全评估与授权归主智能体，本报告不代判、不放行。
