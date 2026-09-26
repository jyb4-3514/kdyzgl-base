# 安全技术评估报告：打卡 WiFi 校验浏览器端可绕过（P0.5 安全评估闸门）

- 评估对象：员工端 `apps/staff-h5` / 店长端 `apps/boss-h5` 打卡提交链路的 **WiFi 校验项可信性**，以及 `hrm-server` 侧的判定口径
- 任务来源：主智能体派发（安全面技术结论）；依据 `项目规则1.md` §7.2 / §8 审批纪律、调度规则 **P0.5 安全评估先行**、**L7 安全评估 → 授权 → 实现**
- 评估方：网络安全工程师 `express-station-security-engineer`（**只出技术结论 + 风险分级 + 修复建议与验收标准**；评估方与决策方分离）
- 评估方式：**纯静态源码/配置只读审计**——逐条独立复核主智能体给的 6 条背景事实；定位到具体文件与行号
- 环境：本机**无 JDK / Maven / MySQL / Redis，无服务器访问**；**未做**任何运行期 / 渗透 / 抓包 / 现网核查；**未复跑** `build:prod`（故"产物是否含该分支"按源码推断，标注为未运行）
- 权限档位：A 档（只读检索 + 新建本报告）
- 报告内**不含真实域名 / IP / 凭据**；生产域名一律写作 `<生产域名（值见运维记录，不落本文）>`
- **结论等级：阻断（当前态）** —— 一句话：浏览器端在"无真实 SSID"时会**主动回填规则白名单首项 SSID 并提交**，服务端对客户端自报值**无任何来源可信性校验**，任何持有效员工账号者可在**任意地点**完成打卡；本报告判定为**安全实质问题**（非业务口径问题）。**A 档最小修复落地并验收通过后，可降为「有条件放行」**。

---

## 1. 评估范围与本报告边界

**评估范围（仅此）**
1. 员工端/店长端 `getWifiInfo()` 的浏览器兜底分支是否会**主动伪造**可用于通过校验的 SSID；
2. 服务端 WiFi 判定是否对上报来源做可信性校验（含 Mock 与真实后端口径一致性）；
3. `GET /attendance/rule` 对 STAFF 开放是否构成"白名单泄露 → 打卡绕过"的完整攻击链；
4. 与 WiFi 判定同链的**定位**校验项是否存在可复用的同构绕过（是否放大 F-01）；
5. `enableWifi=true` 且白名单为空的 fail-open 的**安全/可用性**双重定性；
6. 与既有安全报告与设计文档的对齐、残余风险登记。

**边界声明（本报告不含）**
- **不含可执行验证**：无渗透 / 无扫描 / 无现网 curl / 无门禁实跑；一切"线上实际行为"按**源码推断 + 待实测**处理，不假定；
- **不代改代码**：本报告一字未改任何源码 / 配置 / `.env*` / `deploy/**` / 既有报告（反模式 A17）；
- **不代授权**：仅出技术结论、风险等级与是否可放行；是否放行由主智能体按 §10.3 决策（调度规则 L7）；
- **未调用 MCP、未执行 git 操作**；未读取任何凭据文件内容；
- **不做**功能正确性测试与门禁实跑（仅安全维度）。

---

## 2. 威胁建模

### 2.1 资产与信任边界

| 项 | 内容 |
| - | - |
| 受保护资产 | 考勤记录真实性（`attendance_record`，含 `wifi_matched` / `wifi_ssid` / `distance` 字段）；其下游为薪酬与考核口径 |
| 校验项 | ① WiFi SSID 命中白名单；② 定位在电子围栏内（`matchMode = ALL` 时二者需同时命中） |
| 信任边界 | **客户端 → 服务端**：`wifiSsid` / `longitude` / `latitude` 均为**请求体字段**，服务端不持独立信道 |
| 关键事实 | W3C **从未标准化"网页读取当前 SSID"**（桥接注释自述，见 [bridge.js:L43-L45](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/utils/bridge.js#L43-L48)）→ 浏览器**物理上不可能**取到真实 SSID，只能自报 |

### 2.2 威胁主体

| 主体 | 能力前提 | 动机 |
| - | - | - |
| **内部员工（STAFF）** | 持有效账号；会用浏览器打开员工端 | 迟到/缺勤免于扣罚、代他人打卡、远程"到岗" |
| 外部人员 | **需先获得有效员工账号**（无账号不可达）；无其它前置 | 代打卡（黑产形态：帮同事打卡牟利） |

> 无账号者不可达——`/attendance/check-in` 标 `@RequireRoles({"ADMIN","STATION_ADMIN","STAFF"})`（[AttendanceController.java:L86-L91](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/attendance/AttendanceController.java#L86-L91)），且 `employeeId` 由登录态收口、不接受前端传入。**故本问题属"已认证内部人滥用"型，非未授权访问。**

### 2.3 攻击链（两条，均可零工具完成）

**链 A · 客户端主动伪造（零工具，走正常 UI）**

1. 员工以 STAFF 登录员工端 `<生产域名>/staff/`，进入打卡页；
2. 页面调 `GET /attendance/rule?stationId=<本人驿站>` → 返回 `wifiList`（含真实白名单 SSID，见 §3 F-03）；
3. `useCheckIn` 调用 `getWifiInfo(rule.wifiList[0].ssid)`；**壳内无 `HrmBridge.getWifiInfo` → 桥接回落 mock 分支，把白名单首项当作"当前 WiFi"返回**（[staff useCheckIn.js:L56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/composables/useCheckIn.js#L51-L61)、[bridge.js:L51-L63](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/utils/bridge.js#L51-L64)）；
4. 提交时 `wifiSsid = wifi.ssid`（即白名单首项），**`mock` 标记被丢弃、不上报**（同上 L61）；
5. 若 `matchMode=ALL`（生产默认），再打开同页「演示辅助」开关 → 坐标被替换为**围栏中心**（[useAttendanceStatus.js:L85-L93](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance/composables/useAttendanceStatus.js#L85-L93)、[attendance.vue:L91-L104](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance.vue#L91-L104)）；
6. 服务端 `AttendanceCheckPolicy` 判定 `wifiMatched=true ∧ locationMatched=true` → **200 打卡成功**（[AttendanceCheckPolicy.java:L130-L169](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L169)）。

**前置条件**：有效员工账号 + 本人驿站白名单非空（生产种子默认 1 条，见 SESSION-STATE 记 `json_length(wifi_list)=1`）。**无需事先知道白名单内容**（客户端自己读）。

**链 B · 直连 API（绕过前端）**

持 token 直接 `POST /api/v1/attendance/check-in`，`wifiSsid` 填入从 `GET /attendance/rule` 读到的白名单值，坐标填围栏中心。**即使 A 档修复前端回填，本链依然成立**——这是判定"A 档仅提高门槛"的直接依据。

### 2.4 为何"披露了模拟"不构成缓解

打卡页自查卡确实显示"模拟"角标与说明文案（[attendanceUi.js:L52-L56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance/model/attendanceUi.js#L50-L57)），**但**：
- 披露发生在**页面展示层**，而伪造值照样进入**提交链路**——披露是"诚实"，不是"控制"；
- 服务端收到的 `wifiSsid` 与真实壳上报**完全无法区分**（同一个字段、同一个值域），故后台无痕。

---

## 3. 发现项

> 严重级按"业务影响 × 可利用性"综合；可利用性按"是否需要工具/前置知识"分档。

| ID | 发现项 | 严重级 | 可利用性 | 影响面 | 复现路径（静态可判定） |
| - | - | - | - | - | - |
| **F-01** | **客户端在无真实 SSID 时回填白名单首项并提交；服务端对上报来源零信任校验** | **高** | **高**（零工具、零前置知识，走正常 UI） | 全端（staff/boss）全站员工考勤真实性；`wifi_matched=true` 记录失真 | §2.3 链 A 第 2–6 步 |
| **F-02** | **「演示辅助」定位开关随生产构建发布，坐标可被替换为围栏中心** | **高** | **高**（同页一个 Switch） | 与 F-01 组合后 **WiFi+定位同时失效**，形成端到端绕过 | §2.3 链 A 第 5 步；[attendance.vue:L91-L104](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance.vue#L91-L104) 无任何 `import.meta.env` 门控（Grep `PROD|DEV|import.meta.env` 命中 0） |
| **F-03** | **白名单（SSID/BSSID）对 STAFF 可读，构成绕过所需的"必要输入"** | 中 | 高（一次 GET） | 打卡规则信息暴露；**使 F-01/链 B 无需猜测** | `GET /attendance/rule` 标 `@RequireRoles({"ADMIN","STATION_ADMIN","STAFF"})`，STAFF 数据范围收敛本人驿站（[AttendanceController.java:L56-L61](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/attendance/AttendanceController.java#L56-L61)、[StationScopeQuery.java:L20-L23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/dto/support/StationScopeQuery.java#L20-L24)）；出参含 `wifiList`（[AttendanceRuleServiceImpl.java:L138](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRuleServiceImpl.java#L128-L153)） |
| **F-04** | `enableWifi=true` 且白名单为空 → **允许保存（fail-open）**，该态下**所有打卡必然 9103 失败** | 低（安全）/ 中（可用性） | —（非攻破，属自伤态） | 员工无法打卡；**不构成绕过**，反而是"过严致死" | [AttendanceRuleServiceImpl.java:L191-L194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRuleServiceImpl.java#L186-L195)（代码注释明确"刻意不 fail-closed"）；[AttendanceCheckPolicy.java:L130-L133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L134)（`wifiSsids` 空 → 恒不命中） |
| **F-05** | **本质上限：SSID 与定位均为客户端自报，天然可伪造** | 高（作为系统性结论） | 高 | 决定"校验可达的真实强度上限" | 非缺陷、属设计上限；见 §7 残余风险 |

**Mock 与真实后端判定口径一致性（已核对，一致）**：Mock = `rule.wifiList.some(w => w.ssid === wifiSsid)`（[attendanceStore.js:L1090](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/attendanceStore.js#L1090)）；真实后端 = `List<String>.contains(reportedWifiSsid)`（[AttendanceCheckPolicy.java:L130-L133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L134)）——**同为"数组 any + 区分大小写精确比对"，无分叉**。故本问题**不是** Mock/真实实现差异，而是**两端共同的信任模型缺陷**。

---

## 4. 判定：安全实质问题 vs 业务口径问题

| 发现项 | 判定 | 理由 |
| - | - | - |
| **F-01 / F-02** | **安全实质问题（归安全修复）** | 违反全局规则 §4「所有写操作校验权限与数据归属」的实质——校验项**来源不可信**；且违反 D 档红线精神「不得伪装设备标识」（系统在**生产默认路径上主动替用户伪造设备标识**）。**关键区分：**"是否接受弱校验"可以谈口径，但"系统主动伪造并提交"不是口径问题，是实现缺陷 |
| **F-03** | **安全实质问题（信息暴露，中）** | 白名单是校验依据，对被测对象（STAFF）开放即"把答案发给考生"；是否必须对 STAFF 返回 `wifiList` 属可裁剪面 |
| **F-05** | **业务口径问题（需用户裁定）** | "WiFi/定位作为弱校验是否可接受"是产品风险承受度问题，超出安全单方判定；安全侧只如实给出"上限" |
| **F-04** | **业务口径问题（已有裁定）** | 已由主智能体裁定 A「维持 fail-open」（api.md §8.2 已登记）；安全侧仅登记可用性残余，不主张改口径 |

**归属结论**：**F-01 / F-02 / F-03 交实现角色修复（安全修复）**；F-05 升级用户裁定；F-04 维持口径、仅登记。

---

## 5. 修复建议（分档 + 有效性 + 验收标准）

### 5.1 A 档（最小 · 生产构建禁止伪造）——**推荐必做**

**内容**：`mock === false 且 ssid 非空` 是唯一的回填判据；`mock === true` 时**不得回填任何 SSID**，`wifiSsid` 提交为 `null`，使校验自然落 9103；UI 明确"需安装客户端才能打卡"。**此判据项目内已有先例可对齐**：[wifiWhitelist.js:L77-L79 `canAutoFillWifi`](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/utils/wifiWhitelist.js#L72-L79)（注释明确"判据用 `mock === false`，不得用『是否在壳内』"）。

**改动落点（交前端工程师；本报告只给范围与验收，不写代码）**：
- [staff useCheckIn.js:L56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/composables/useCheckIn.js#L56)（**提交链路**，主要）
- [staff useAttendanceStatus.js:L161-L164](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance/composables/useAttendanceStatus.js#L161-L164)（**自查卡展示**，次要）
- [boss useCheckIn.js:L56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/composables/useCheckIn.js#L56)（同构）
- **不含** F-02 的定位演示辅助开关（那是另一条链，见 5.3）

**有效性评估**：**仅提高门槛，不能真正阻止绕过。** 它消除"零成本自动通过"，把攻击者从"点一下打卡"推回"必须自己构造请求或自建同名热点"。链 B（直连 API）不受影响。

**验收标准（可判定）**
| # | 验收项 | 判定方法 |
| - | - | - |
| A-1 | 无壳/壳未实现 `getWifiInfo` 时**不回填** | 单测：`getWifiInfo` 桩返回 `{ssid:'X',mock:true}` → 断言 `useCheckIn` 提交体 `wifiSsid === null`（或字段缺失）；桩返回 `{ssid:'X',mock:false}` → 断言提交 `'X'` |
| A-2 | 自查卡在 mock 时**显示"未获取到"**、不显示白名单值 | 组件/单测：`wifiTextOf({ssid:'',mock:true})` → `未获取到`；且**不得**出现白名单 SSID 文本 |
| A-3 | 生产构建下提交体不含伪造 SSID | 产物/网络断言：无壳环境提交 `POST /attendance/check-in` 的请求体 `wifiSsid` 为 `null` |
| A-4 | 服务端行为符合预期（不新增错误码） | 该请求返回 **9103**（`ATTENDANCE_WIFI_MISMATCH`），与 [AttendanceCheckPolicy.java:L171-L177](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L171-L177) 一致 |
| A-5 | UI 明示"需安装客户端才能打卡" | 打卡页在 mock 态出现该文案（可判定：文案断言） |
| A-6 | 现有单测基线不弱化 | `lint` 0 error；相关 `test` 全通过、**不得下调断言**（反模式 A06） |

> **口径待确认（升级用户）**：A 档会使"浏览器端演示打卡通过"路径**按设计失效**（bridge 注释原意"使纯前端演示能走通 WiFi 校验分支"）。演示替代路径需口径确认——见 §8-U1。

### 5.2 B 档（服务端拒绝 `mock:true` 来源）——**评估结论：否决**

**可行性评估**：**不可行，不构成控制。** 理由链：
1. `mock` 是**客户端自报**的标记，请求体完全由客户端构造（现契约**根本不含**该字段，见 [AttendanceCheckInRequest.java:L14-L24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/dto/attendance/AttendanceCheckInRequest.java#L14-L24)）；
2. 若新增"可信标记"，攻击者**省略该字段或置 false** 即可，服务端无从分辨——**标记本身也可伪造**（此即主智能体设问所指）；
3. 同理，`clientType` / UA 等信号亦客户端自报，只可作**风险信号与审计**，**不得作门禁**。

**可替代的有限增量**：服务端对 `wifiSsid == null` 在 `enableWifi=true` 时**直接判未命中**（现状已具备，[AttendanceCheckPolicy.java:L130-L133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L134)）；这使 A 档的"空值"必然失败，属 A 档的服务端侧天然闭环，**不构成独立 B 档价值**。
**结论：B 档否决**（不得以"客户端标记"充当安全门禁）。

### 5.3 C 档（根治方向）——**作为立项方向，不在本批落地**

> 只需方向，不做详细设计。判定原则：**只有"服务端从独立可信信道获得证据"才能根治**；凡"客户端上报"的方案本质均为提高门槛。

| 方向 | 原理 | 有效性 | 备注 |
| - | - | - | - |
| **企业微信 / 网关侧校验** | 打卡请求来源出口 IP 属站点 WiFi 网关出口白名单 | 高（服务端侧信道） | 需与网络/网关方协作；与三期企业微信接入天然契合 |
| **一次性签到码** | 站点现场展示短时效、限次的动态码 | 高（需在场） | 属新增业务能力，需产品口径 |
| **网络准入（802.1X / NAC）** | 员工账号与 WiFi 会话绑定，服务端可核验网关日志 | 高 | 依赖现网网络设备能力 |
| **壳内设备签名 / attestation** | 原生读 SSID + 设备密钥签名上报 | 中（提高门槛，root/改机可绕） | **前置：壳侧 `HrmBridge.getWifiInfo` 未实现，须先补齐**（见 §7-R7） |

### 5.4 推荐落地档位与理由

**推荐：A 档必做（并作为当前「阻断」解除的最小条件）+ C 档立项 + B 档否决。**

- **理由**：A 档是**成本最低、可立即闭环"零成本伪造"这一最高危可利用面**的动作，且项目内已有同源判据先例（`canAutoFillWifi`），改造面清晰、可单测锁定；
- **必须同时承认**：A 档**不能**让"WiFi 校验"变成可信校验（F-05 上限不改），故**不得以此宣称"WiFi 校验已安全"**（未验证项闭环前不得声称已确认合规）；
- 根治依赖 C 档；在 C 档落地前，应在产品口径上**明确 WiFi/定位为"弱校验/留痕项"**，不以 `wifi_matched=true` 作为考勤合规的独立证据（见 §7-R5）。

---

## 6. 与既有报告 / 设计文档的对齐

| 既有材料 | 关系 | 结论 |
| - | - | - |
| [security-structure-migration-review.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-structure-migration-review.md) | **无冲突** | 该报告只覆盖"ADR 文档层真实域名入库"一项形式核对，**不含**业务校验项可信性 |
| [security-release-switch-review.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-release-switch-review.md) | **需补充**（非冲突） | 其 §3 逐条结论表覆盖 `.map` / 静态站信息泄露 / 路径隔离 / 安全响应头，**缺少"业务校验项可伪造"这一维度**；其 §7-1 仅把"演示身份"文案列为低危 UX 文案。**建议**：B7 残余风险登记中增补对本报告的引用（此项由主智能体执行） |
| [api.md §8](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/api.md) | **已登记、未升格为安全发现** | §8.2 已登记 fail-open 可用性风险（F-04）；但**对"客户端回填 → 绕过"未作安全定级** |
| [boss-wifi-and-station-design.md §3.3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/boss-wifi-and-station-design.md) | **设计覆盖盲区（本次发现）** | 该硬约束"**禁止预填任何 SSID/BSSID、不得出现『已读取/模拟』字样**"（L222/L654）**只约束店长端「打卡规则配置页」的『读取当前 WiFi』按钮**；**员工端打卡页的运行时回填未被该约束覆盖**——两处是不同场景，故本次不是"违反既有口径"，而是**设计未覆盖** |
| [tech-review-wifi-whitelist.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/tech-review-wifi-whitelist.md) | **需升格** | 其 F4/F6 已记录"仅按 SSID 精确比对""壳未实现 `getWifiInfo`、恒 mock:true"——**事实已存在但按功能/演示处置**，本报告将同一事实的**安全含义显式化并定级** |

**一句话**：既有报告**不冲突**，本报告为**增量补充**（B7 报告的安全面 + 设计文档的覆盖盲区）。

---

## 7. 残余风险登记

> 适用前提：**已落地 A 档**。以下风险在 A 档后**依然存在**，须登记并在产品口径中承认。

| ID | 残余风险 | 等级 | 说明（如实陈述上限） |
| - | - | - | - |
| **R1** | **SSID 上报天然可伪造（本质上限）** | 高 | 客户端可"自建同名热点"或"直连 API 填白名单值"；**弱校验的上限就是"连上/报对即通过"，无法证明物理在场** |
| **R2** | 员工手机连上该 WiFi 后，**在站外（家中/热点）用浏览器打卡** | 中 | 若规则未强制"壳内 + 定位"，此类行为**在判定上"符合规则"**，但无法证明人在站点 |
| **R3** | **定位项同构可绕过**（演示辅助开关 + 坐标自报） | 高 | F-02 未处置前，WiFi 修好**仍不能阻止端到端绕过**；坐标同属客户端自报 |
| **R4** | **白名单对 STAFF 可读**（F-03） | 中 | A 档不减该面；攻击者仍可一次 GET 拿到"答案" |
| **R5** | **审计盲区**：服务端无法区分真实壳上报与伪造上报 | 中 | 日志中 `wifiMatched=true` **不可作为考勤合规证据**；不得据此认定"真实到岗" |
| **R6** | **fail-open 可用性**（F-04） | 低/中 | `enableWifi=true` 且白名单空 → 全员 9103；属既定口径，仅登记 |
| **R7** | **正路仍不可用**：壳未实现 `HrmBridge.getWifiInfo` | 中 | 即使 A 档落地，"壳内真实读取"这一正路**仍不可用**，需壳实现才完成根治的一半（[bridge.js:L51-L63](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/utils/bridge.js#L51-L64)；`hrm-android-shell` 全目录 Grep `getWifiInfo` **命中 0**） |
| **R8** | 未验证项：**未复跑构建/未做现网实测** | — | 本报告的"浏览器分支已进生产产物"为**源码推断**（无 `import.meta.env` 门控），**待运维/测试实测确认**（见 §9） |

---

## 8. 待确认口径 / 需升级用户裁定项

| ID | 事项 | 为何需用户 |
| - | - | - |
| **U1** | A 档落地后，"浏览器端演示打卡通过"路径按设计失效，演示替代路径待定 | 变更演示手段属产品口径（R11/P1 口径先行） |
| **U2** | F-05：WiFi/定位作为**弱校验/留痕项**是否可接受 | 风险承受度属产品决策；安全侧只给上限 |
| **U3** | F-03：是否必须对 STAFF 返回 `wifiList`（可否裁剪出参以削减信息暴露） | 涉接口契约变更，需契约方与产品共定 |

---

## 9. 结论等级

**阻断（当前态）→ 落地 A 档并验收通过后降为「有条件放行」。**

| 项 | 结论 |
| - | - |
| 是否构成安全实质问题 | **是**（F-01 F-02 F-03）；非"纯业务口径问题" |
| 当前可否判定为"符合安全要求" | **否**（阻断）。WiFi 校验在浏览器下**事实上不成立**，且系统**主动伪造**该凭证 |
| 解除阻断的最小条件 | A 档 A-1 ~ A-6 全部验收通过（含单测锁定，不得下调断言） |
| 放行后仍受约束 | ① 不得宣称"WiFi 校验已安全"；② `wifi_matched=true` 不得作考勤合规独立证据；③ C 档立项跟踪；④ §7 残余风险全部登记 |
| 高风险升级点 | F-01/F-02 命中"服务端信任客户端自报的设备标识/位置"，与 D 档红线**精神**冲突（伪装设备标识）。**主智能体不得凭经验直接放行**，须由实现角色修复并复验后，再由主智能体按 §10.3 授权发布 |
| 未验证项（不得声称已确认合规） | §7-R8：未复跑构建、未做现网实测、未抓包；**下列结论为静态推断，待实测回填** |

**C 档授权技术输入结论（供主智能体决策，本角色不代授权）**

| 项 | 内容 |
| - | - |
| 风险等级 | **高**（F-01/F-02）；F-03 中；F-04 低 |
| 是否可放行（当前态） | **否**——须先落地 A 档并复验 |
| 缓解措施 | A 档必做（§5.1）+ UI 明示 + C 档立项 + 产品口径确认（U1–U3） |
| 复验要求 | 由网络安全工程师对本报告 A-1 ~ A-6 逐条复验，出复验结论；未复验不得报"已闭环" |

---

## 10. 未覆盖项、事实性纠正与检查点

### 10.1 未覆盖项 / 已知不足

1. **无运行期证据**：未抓包、未复跑 `build:prod`、未现网 curl；A-3 的"产物内不含伪造 SSID"为源码推断（依据：staff-h5 `attendance.vue` 无 `import.meta.env` 门控），**须实测确认**；
2. 未审后端其余鉴权 / 越权面（归 `security-auth-review.md` / `security-client-admission-review.md`）；
3. 未做薪酬结算链路的端到端口径核对（"考勤是否直接参与计薪"属 U2 口径范围，本报告不判定金额影响，仅判定"考勤真实性受损"）；
4. 未枚举企业微信/网关侧现有网络能力（C 档方向落地依赖三期企业微信接入，待该期评估）。

### 10.2 事实性纠正（对任务给的 6 条背景事实的独立复核）

| # | 上游陈述 | 复核结果 | 更正 / 精确化 |
| - | - | - | - |
| 1 | 员工端打卡页提交带 `wifiSsid`；注释为 `{ checkType, periodIndex?, wifiSsid, longitude, latitude }` | ✅ **成立** | [staff api/attendance.js:L17-L22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/api/attendance.js#L17-L22) |
| 2 | 桥接 `getWifiInfo(fallbackSsid)`：壳内走桥接，**浏览器（无壳）→ `{ssid:fallbackSsid,bssid:'',mock:true}`**；`fallbackSsid` 现实取自白名单第一项 | ⚠️ **成立但需精确化** | ① 回落分支**不限于"浏览器无壳"**——只要壳**未返回合法 ssid**（未实现 / 返回非 JSON / ssid 空）即回落，**故当前壳内同样回落**；② `fallbackSsid` 仅当白名单非空时才是首项，否则为 `''`；③ 除 [boss useCheckIn.js:56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/src/composables/useCheckIn.js#L56) 外，**staff 端有两处**：提交链 [useCheckIn.js:56](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/composables/useCheckIn.js#L56) **与** 自查卡 [useAttendanceStatus.js:164](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance/composables/useAttendanceStatus.js#L161-L164)（任务原文只提"对应处"） |
| 3 | 后端按 `wifiMatched` 判定；Mock 口径 `rule.wifiList.some(w => w.ssid === wifiSsid)`；未命中 9103；真实逻辑在 `hrm-server` | ✅ **全部成立** | 真实后端定位到 [AttendanceCheckPolicy.java:L130-L133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L134)（`List.contains`，等价 any + 精确大小写）；9103 见 [L171-L177](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L171-L177)；调用点 [AttendanceRecordServiceImpl.java:L318-L340](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRecordServiceImpl.java#L318-L340) |
| 4 | 壳未实现 `getWifiInfo`（零命中）→ **当前所有端在浏览器下都会走到白名单首项回填分支** | ⚠️ **成立但需精确化** | 零命中 ✅（`hrm-android-shell` Grep `getWifiInfo` = 0）；但"所有端在**浏览器下**"表述偏窄——**壳内也回落**（同 #2 ①）。且"回填"仅在白名单非空时发生 |
| 5 | 生产三端已上线、后端 `profile=dev`、库 `kdyzgl_test`；生产 `*.map` 不得公开（Nginx 已 404） | ✅ **成立** | SESSION-STATE 记三端入口上线、`profile=dev`/`kdyzgl_test`、线上 `.map` = 0 且 Nginx 另 404（双保险）。**本报告不落真实域名** |
| 6 | `enableWifi=true` 且白名单为空 → 后端**允许保存（fail-open）**；该态所有打卡因 WiFi 未命中失败（9103） | ✅ **成立** | [AttendanceRuleServiceImpl.java:L191-L194](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/impl/AttendanceRuleServiceImpl.java#L186-L195)（刻意不 fail-closed，注释自述）+ [AttendanceCheckPolicy.java:L130-L133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/attendance/support/AttendanceCheckPolicy.java#L130-L134)；api.md §8.2 已登记 |

**补充发现（任务未提，本报告新增）**：
- **F-02**：「演示辅助」定位开关随生产构建发布且**无环境门控**，与 F-01 组合构成端到端绕过——这是把严重级定为"高"的关键补充事实；
- **设计盲区**：`boss-wifi-and-station-design.md` 的"禁止预填 SSID"硬约束**未覆盖员工端打卡页运行时回填**（§6）。

### 10.3 检查点（M02）

- **2026-09-26**：产出 `hrm-dev/docs/security-wifi-checkin-bypass-review.md`（**新增**）。对"打卡 WiFi 校验浏览器端可绕过"做 P0.5/L7 静态安全评估：结论**阻断（当前态）**，发现项 F-01~F-05，推荐 A 档必做、B 档否决、C 档立项。
- **影响文件**：仅本文件（1 个，新建）。**未改动任何源码 / 配置 / `.env*` / `deploy/**` / 既有报告**；未调用 MCP；未执行 git；未触达生产。
- **证据来源**：`hrm-clients/apps/{staff-h5,boss-h5}/src/**`、`packages/mock/src/**`、`hrm-server` 考勤域源码、`hrm-android-shell`（Grep 零命中）、`docs/{api.md,boss-wifi-and-station-design.md,tech-review-wifi-whitelist.md,security-*-review.md}`、`SESSION-STATE.md`。
- **未运行**：任何构建 / lint / test / 扫描 / 渗透 / 抓包 / 现网 curl（本机无运行环境、无服务器访问）。
- **回滚**：删除本文件即可（纯新增文档）。
