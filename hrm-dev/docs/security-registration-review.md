# 安全技术评估报告：员工自助注册（公开未鉴权入口）威胁建模与攻击面审计

- 评估对象：拟新增的**员工自助注册**公开接口（姓名 + 手机号 + 短信验证码 + 意向驿站 + 意向岗位 + 自设密码 → 生成入职审批单 → 审批通过后联动建档/定薪/定岗/定角色），及其与既有鉴权、短信、入离职流程的衔接。
- 任务来源：主智能体派发（安全面技术结论）；依据 `项目规则1.md` §7.2 / §8 审批纪律、调度规则 **P0.5 安全评估先行**、**L7 安全评估 → 授权 → 实现**、**R24**。
- 评估方：网络安全工程师 `express-station-security-engineer`（**只出技术结论 + 风险分级 + 修复建议与验收标准；不改代码、不执行 git、不代授权**）。
- 评估方式：**纯静态源码/配置只读审计**——对现有鉴权与准入链路、短信链路、入离职流程、员工建档与薪资链路逐条取证核对（见 §4 与正文行号引用）。**未做**运行期 / 渗透 / 抓包 / 现网核查；本机无 JDK/Maven/Redis/MySQL。
- 重要前提：经取证，**当前仓库不存在任何注册相关代码或表**（全仓 `register`/注册 命中项均为无关语义）。故本报告对**设计态**做威胁建模与缺口分析，而非对既有实现做漏洞复核。
- 权限档位：A 档（只读检索 + 新建本报告）。
- 报告内**不含真实域名 / IP / 凭据**；生产域名一律写作 `<生产域名（值见运维记录，不落本文）>`。
- **结论等级：阻断（当前设计态，不得公网放行）**。一句话：该需求把「短信下发」这一**按业务成本计费的对外动作**直接架到**公网未鉴权**入口上，同时要求「审批通过即联动建号/定薪/定角色」——若按字面直译实现，将同时打开**短信轰炸/成本耗尽**、**手机号枚举**、**注册可控字段直写权限与薪资的提权链**三条独立可利用路径，且现有频控与图形码尚不足以形成有效缓解。**满足 §9 必做项并复验通过后，可降为「有条件放行」**（测试库/内网预览范围，见 §7.3）。

---

## 1. 结论摘要（先行）

**总体判定：不建议以当前设计形态将注册入口暴露公网。** 存在 **3 项严重、6 项高**；其中「短信下发到任意未注册手机号」「注册可控字段直写角色/薪资」「审批通过即激活可登录账号」为三条**硬性放行阻断项**。

### 1.1 高风险及以上清单

| 编号 | 标题 | 严重级 | 一句话影响 |
| --- | --- | --- | --- |
| REG-01 | 公网未鉴权入口可对**任意手机号**触发短信下发 | **严重** | 短信轰炸第三方、短信费被刷爆（直接财务损失）、通道被供应商封停 |
| REG-02 | 注册/发码链路存在**手机号枚举**（响应差异泄露「已注册」） | **高** | 批量探测员工手机号（PII），并为定向撞库/钓鱼提供名单 |
| REG-03 | 注册可控字段（角色/站点/薪资）被直写进 `employee` / `hr_salary` 的**提权链** | **严重** | 申请人可自选驿站甚至越权到 STATION_ADMIN，或左右定薪口径 |
| REG-04 | 入职建档链路**不校验手机号唯一** + 库层无唯一索引 → 重复手机号 | **高** | 目标手机号出现两条 `employee` → 按手机号 `selectOne` 抛异常 → 目标账号手机登录/短信登录 **DoS** |
| REG-05 | 「审批通过即自动建号并激活」可**绕过人工审核**获得可登录账号 | **严重** | 审批环节被弱化/误通过/被批量诱导通过时，直接产出账号（权限提升） |
| REG-06 | 注册申请列表/详情若未收敛为 **ADMIN-only** → IDOR | **高** | 按 ID 遍历读取他人姓名/手机号/意向（PII 批量泄露）+ 审批操纵 |
| REG-09 | 短信频控的 **IP 维度可被伪造头绕过** | **高** | 放大 REG-01；四维限频退化为「手机号 + 设备自称」两维，成本墙形同虚设 |

> **中风险 5 项、低风险 2 项**见 §5。

### 1.2 已验证为「合格」的控制（正向结论，注册方案必须复用，不得另起口径）

- **端点默认 fail-closed**：非公开端点未声明 `@RequireRoles` 一律 403（[RequireRolesInterceptor.java:34-57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L34-L57)）；公开端点是**显式白名单**（[PublicEndpoints.java:45-55](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L45-L55)），且该类注释已明确「新增任一公开端点均扩大攻击面，公网暴露前须再过 P0.5 安全评估」（[PublicEndpoints.java:13-14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)）。
- **短信验证码已有基础设施**：Redis 存码/一次性/恒定时间比对/尝试上限/失败即作废（[SmsVerifyPolicy.java:39-50](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/sms/SmsVerifyPolicy.java#L39-L50)、[AuthServiceImpl.java:647-675](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L647-L675)）；默认 6 位 / TTL 300s / 尝试上限 5（[SmsProperties.java:33-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L33-L72)）。
- **短信**频控**四维已实现**：手机号间隔 60s + 手机号日上限 10 + IP 时上限 20 + 设备时上限 10 + 账号日上限 10（[SmsCodeStore.java:93-115](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115)）；**投递失败不落可用验证码**（[AuthServiceImpl.java:303-315](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L303-L315)）。
- **短信通道生产 fail-closed**：prod 未配有效凭据或配了万能码即**启动失败**（[SmsConfigGuard.java:42-74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/sms/SmsConfigGuard.java#L42-L74)）；验证码明文**不进日志/异常/响应体**（[SmsCodeStore.java:25-29](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L25-L29)）。
- **防枚举已有一处正向先例**：短信发码对「账号不存在」与「未绑手机号」**统一 1109**（[AuthServiceImpl.java:266-269](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L266-L269)）；短信登录对「账号不存在/密码错误」统一 1001（[AuthServiceImpl.java:353-357](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L353-L357)）。
- **入离职流程有人工办理与按序守卫**：全部仅 ADMIN（[HrFlowController.java:39-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L49)），跳步/重复办理被 `HrFlowStepGuard` 拒（[HrFlowServiceImpl.java:138-141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L138-L141)）；建档默认 `role=STAFF`（[HrFlowServiceImpl.java:119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119)），角色提升受白名单限制且仅 STATION_ADMIN/STAFF（[HrFlowServiceImpl.java:364-370](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L364-L370)）。
- **员工密码链路合格**：BCrypt 存储、初始口令强制首登改密、`pwdChanged=0`（[EmployeeServiceImpl.java:139-147](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L139-L147)、[HrFlowServiceImpl.java:294-302](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L294-L302)）。

---

## 2. 评估范围与边界

**评估范围（仅此）**
1. 拟新增公开未鉴权注册接口的**滥用面**：短信轰炸/成本耗尽、手机号枚举、脚本化批量调用；
2. 注册数据污染的传导路径：从申请 → 审批 → 建档/定薪/定角色，是否可导致**权限提升**；
3. 审批联动是否可**绕过人工审核**产出可登录账号；
4. 验证码强度（位数/有效期/重试/一次性/绑定/共用频控池）与图形码/风控必要性；
5. 入参安全（注入/超长/非法枚举/越权引用）；
6. 申请列表/详情越权（IDOR）；
7. PII 最小必要、传输/存储/脱敏/留痕、**未过审数据保留期限**；
8. 限速/封禁与既有 `hrm.sms.*` 频控的关系。

**边界声明（本报告不含）**
- 不含可执行验证：无渗透、无扫描、无现网 curl；一切「线上实际行为」按**源码推断 + 待实测**处理，不假定。
- **不代改代码**：一字未改任何源码/配置/既有报告（反模式 A17）；**未执行 git**；**未调用 MCP**；未读取任何凭据明文。
- **不代授权**：仅出技术结论、风险等级与是否可放行；是否放行由主智能体按 §10.3 决策。
- 不做功能正确性测试与门禁实跑（仅安全维度）。

---

## 3. 威胁建模

### 3.1 资产清单（注册链路触达）

| 资产 | 位置 | 敏感度 |
| --- | --- | --- |
| 员工手机号（PII，且为登录标识） | `employee.phone` / `hr_flow.phone` | 高 |
| 员工账号与口令散列 | `employee.username/password`（BCrypt） | 高 |
| 角色（决定可见数据范围） | `employee.role` | 极高（提权目标） |
| 定薪档案（薪酬口径） | `hr_salary.*` | 高 |
| 审批流程与办理留痕 | `hr_flow` / `hr_flow_step` | 中（取证价值） |
| 短信通道凭据与**短信费** | 外置配置 / 供应商账户 | 极高（可盗刷、直接计费损失） |

### 3.2 信任边界

```
[公网 · 任意人] ──①──> [Nginx（尚无 limit_req；仅 80 监听样例）] ──②──> [127.0.0.1:8080 后端] ──③──> [MySQL/Redis]
      │                          │                                    │
      │                          │                                    └─ 边界③：本机回环
      │                          └─ 边界②：X-Forwarded-For 由 Nginx 透传，但客户端可预置伪造值（IpUtil 取 XFF 首个，见 3.3-⑨）
      └─ 边界①：注册端点「无凭据可达」→ 一切滥用面由此进入；现有频控为应用层唯一屏障
出网边界：[阿里云短信 API] ← 每成功一条即产生真实计费
```

### 3.3 关键既有机制（取证事实，均为静态可核验）

| # | 事实 | 依据 |
| --- | --- | --- |
| ① | 公开端点=免认证+免角色双放行 | [JwtAuthFilter.java:68-71](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java#L68-L71) 与 [RequireRolesInterceptor.java:41-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L41-L49) 共用白名单 |
| ② | **现有短信发码仅服务「已存在账号」**：按手机号查 `Employee`，查不到即 1109 | [AuthServiceImpl.java:264-269](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269) |
| ③ | 短信场景枚举仅 LOGIN / DEVICE_VERIFY / PERIODIC_REAUTH，**无注册场景** | [SmsScene.java:9-16](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L9-L16) |
| ④ | 未知场景**回落 LOGIN**（非拒绝） | [AuthServiceImpl.java:606-619](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L619) |
| ⑤ | 图形码**默认关闭**，且当前仅返回**固定占位图**（TODO 未接真实渲染） | [AuthProperties.java:70-78](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/AuthProperties.java#L70-L78)、[CaptchaStore.java:17-20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/CaptchaStore.java#L17-L20) |
| ⑥ | 员工新建校验**手机号活跃唯一（2003）** | [EmployeeServiceImpl.java:126-133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L126-L133) |
| ⑦ | **入职建档不校验手机号唯一**：只查 username，直接 `employee.setPhone(flow.getPhone())` | [HrFlowServiceImpl.java:264-305](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L305) |
| ⑧ | **手机号无数据库唯一索引**，唯一性靠 Service 查重（决策 D7） | `db.md` §1.3 |
| ⑨ | IP 取 **X-Forwarded-For 首个**值，客户端可预置伪造（Nginx 仅追加，不去除） | [IpUtil.java:17-31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/IpUtil.java#L17-L31) + `nginx.conf.example:63-64` |
| ⑩ | Nginx 样例**无 `limit_req` / `limit_conn`**，仅 80 监听 | `deploy/nginx.conf.example`（全文无 limit 指令、`listen 80`） |
| ⑪ | 建档的**账号与密码由管理员在步骤中提交**，非申请人设定 | [HrFlowServiceImpl.java:268-277](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L268-L277) |
| ⑫ | 建档即落 **status=0（未生效）**，最后一步才转在职 | [HrFlowServiceImpl.java:301](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301)、[:389-397](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L389-L397) |
| ⑬ | 建档**同步落 0 值定薪行**；定薪由管理员提交金额 | [HrFlowServiceImpl.java:328-337](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L328-L337)、[:375-386](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L375-L386) |
| ⑭ | `hr_flow` **无「来源」字段**，无法区分自助注册与管理员发起 | [HrFlow.java:29-102](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L29-L102)（字段清单无 source/origin） |
| ⑮ | 全部员工接口与流程接口**仅 ADMIN** | [EmployeeController.java:32](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/employee/EmployeeController.java#L32)、[HrFlowController.java:39-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L49) |

### 3.4 STRIDE 逐类分析

| 类别 | 注册链路适用场景 | 现状判定 |
| --- | --- | --- |
| **S**poofing 伪装 | 姓名任意、以**他人手机号**提交（非本人持有） | 有风险：短信码只证明「持有某号」，不证明「该号主本人是该姓名」；身份真实性无强凭据（REG-02/REG-03） |
| **T**ampering 篡改 | 注册载荷夹带 `role`/`stationId`/`basicSalary` 等非声明字段被直写 | 有**严重**项（REG-03） |
| **R**epudiation 抵赖 | 自助注册无独立来源标识，`hr_flow.operatorId` 为空 | 有中风险（REG-12） |
| **I**nformation Disclosure 泄露 | 「已注册」响应差异做手机号枚举；申请详情 IDOR | 有**高**项（REG-02/REG-06） |
| **D**enial of Service / 成本 | 短信轰炸第三方、短信费耗尽、通道封停 | 有**严重**项（REG-01/REG-09） |
| **E**levation of Privilege 提权 | 注册可控字段直写 role/salary；自动建号激活绕过审批 | 有**严重**项（REG-03/REG-05） |

### 3.5 关键攻击链（含前置条件）

**链 A · 短信轰炸 + 成本耗尽（前置：仅需公网可达注册页）**

1. 攻击者脚本化调用注册发码接口，`phone` 填**任意真实号码**（受害者/仇家/竞争对手）；
2. 服务端按 `phone` 生成并投递短信（对未注册号不受 1109 阻断——注册场景必然允许未注册号发码）；
3. 频控维度中：手机号维度天然被「换号」绕过；**IP 维度取 XFF 首个值**，攻击者每个请求伪造不同 `X-Forwarded-For` → 该维度失效（事实⑨/⑩）；
4. 设备维度为客户端自称（可空/可伪造，`SmsProperties.java:56-60` 自认「不作安全依据」）；
5. 唯一剩余约束为**单号 60s/日 10 条**——攻击者以「号池 × 单号 10 条」即可产生**与号码数成正比**的真实计费与骚扰量，且被轰炸方向为**第三方**。
- **结果**：短信费损失、品牌与合规风险（骚扰投诉）、通道被供应商封停（衍生可用性事故）。

**链 B · 手机号枚举（前置：仅需公网可达注册页）**

1. 若注册发码**复用现有 `sendLoginCode` 口径**：对「已注册手机号」发码成功，对「未注册」返回 1109（[AuthServiceImpl.java:264-269](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269)）→ **响应差异即枚举**；
2. 若注册提交阶段校验唯一性并返回 2003（「手机号已被其他员工使用」，[ErrorCode.java:91](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L91)）→ **同样枚举**；
3. 由事实④：若新增 `SmsScene.REGISTER` 但漏改 `resolveScene`，未知场景**静默回落 LOGIN**，恰好命中第 1 条的枚举差异（**fail-open 放大**）。
- **结果**：以号段批量探测，产出「本系统在职员工手机号」名单，供撞库/钓鱼/精准社工。

**链 C · 注册可控字段 → 权限/薪资提升（前置：注册提交 + 审批被放行）**

1. 需求原文「审批通过后**同时生成员工档案 + 设置薪资 + 设置岗位/站点/角色**」；
2. 若实现为「把注册提交中的 `意向岗位/意向驿站`（乃至夹带的 `role`/`basicSalary`）**直接写入** `employee.role` / `employee.stationId` / `hr_salary`」：
   - 越权点 1：申请人自选 `stationId` → 可指向**非本人应属驿站**，直接改变数据可见范围（二期包裹数据按驿站划分）；
   - 越权点 2：申请人夹带 `role=STATION_ADMIN` → 若未走 `HrConstants.ASSIGNABLE_ROLES` 白名单（[HrFlowServiceImpl.java:364-370](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L364-L370)），即成**站长级提权**；
   - 越权点 3：申请人影响定薪（`basicSalary` 等，[HrFlowServiceImpl.java:375-386](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L375-L386)）→ 薪酬口径污染。
3. 关键：现状 `createOnboarding` **硬编码 `role=STAFF`**（[HrFlowServiceImpl.java:119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119)）、建档**不给 role 传参**、账号/密码**由管理员提交**（事实⑪）——**现有安全默认值，会被「自动化联动」这一需求变更直接拆除**。

**链 D · 重复手机号导致目标账号登录 DoS（前置：注册提交 + 建档被执行）**

1. 建档链路**不校验手机号唯一**（事实⑦），且库层**无唯一索引**（事实⑧）；
2. 攻击者以**受害者已注册手机号**（可由链 B 枚举获得）提交注册并被建号 → `employee` 出现两条同 `phone` 行；
3. 登录/短信登录按手机号 `employeeMapper.selectOne(...)`（[AuthServiceImpl.java:264-265](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L265)、[:351-352](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L351-L352)）命中多行 → `selectOne` 抛 `TooManyResultsException` → **受害者手机号维度的登录与短信登录同时不可用**（持久 DoS，且需人工清库）；
4. 并发放大：Service 层「先查后插」存在 **TOCTOU**，两个并发注册可同时通过查重。

---

## 4. 现有安全机制取证（基线对齐）

- 已核对与对齐的既有安全报告：`security-auth-review.md`（登录体系）、`security-wifi-checkin-bypass-review.md`（P0.5 闸门结论体例）、`security-client-admission-review.md`、`security-structure-migration-review.md`、`security-release-switch-review.md`。本报告沿用其**「静态可核验复现步骤 + 严重级/可利用性/影响面 + 必做验收标准」**体例与「结论等级：通过/有条件放行/阻断」用词。
- **`PublicEndpoints` 的自我约束**：该清单类注释已把「新增公开端点 → 公网暴露前须再过 P0.5 安全评估」写成代码内纪律（[PublicEndpoints.java:13-14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)）。**本报告即该纪律的执行产物**，注册端点上线前须把其路径登记进该白名单，并同步 §2/§9 的复验。
- **历史同类严重项参照**：`SEC-AUTH-01`（`auto-dispatch` 未认证写端点）为同一根因（「白名单就位即被部署放行」）。注册端点若在安全闸门未闭环前并入白名单，**风险形态与 SEC-AUTH-01 同构**。

---

## 5. 发现项清单

> 严重级：严重/高/中/低；可利用性：可远程（无需凭据）/ 可远程（需认证）/ 需本地（部署条件）。所有复现步骤均为**静态可核验**，**勿在生产执行**。

| 编号 | 标题 | 严重级 | 可利用性 | 影响面 |
| --- | --- | --- | --- | --- |
| REG-01 | 公网未鉴权入口可对任意手机号触发短信 | 严重 | 可远程（无需凭据） | 短信费损失、第三方骚扰、通道封停 |
| REG-03 | 注册可控字段直写 role/薪资/站点 = 提权 | 严重 | 可远程（无需凭据，依赖审批放行） | 站长级提权、数据可见范围篡改、薪酬污染 |
| REG-05 | 「审批通过即建号并激活」绕过人工审核 | 严重 | 可远程（无需凭据，依赖审批被误通过） | 直接产出可登录账号 |
| REG-02 | 注册/发码链路手机号枚举 | 高 | 可远程（无需凭据） | 员工 PII 批量泄露，供撞库/钓鱼 |
| REG-04 | 建档不校验手机号唯一 + 无唯一索引 → 重复号 | 高 | 可远程（依赖建档被执行） | 目标账号手机登录 DoS（需人工清库） |
| REG-06 | 申请列表/详情 IDOR（若未 ADMIN-only） | 高 | 可远程（需任一登录态） | 遍历他人 PII + 审批操纵 |
| REG-09 | IP 维度频控可被 XFF 伪造绕过 | 高 | 可远程（无需凭据） | 放大 REG-01 |
| REG-10 | 图形码生产不可用（固定占位图、默认关闭） | 中 | 需本地（部署条件） | 无法作为注册发码的人机校验缓解 |
| REG-07 | 未过审数据无保留期限/清理机制 | 中 | 可远程（依赖入口开放） | 垃圾数据长期驻留，合规风险 |
| REG-08 | 入参安全：非法枚举/超长字段/越权引用 | 中 | 可远程（无需凭据） | 数据污染、异常、越权引用 |
| REG-12 | 自助来源无独立标识，办理人留痕缺失 | 中 | 可远程（依赖入口开放） | 抵赖、审计断链（事实⑭） |
| REG-11 | 注册请求体无 `@Valid` 强校验先例（同类端点） | 低 | 可远程（无需凭据） | 非法载荷进入业务层（现有发码端点即无 `@Valid`） |
| REG-13 | 错误文案泄露存在性（2003/1109 差异） | 低 | 可远程（无需凭据） | 与 REG-02 同源，独立计数 |

### 逐条详述（高及以上）

**REG-01 · 公网未鉴权入口可对任意手机号触发短信**
- 依据：现有发码仅服务已存在账号（事实②），注册必然需要「对未注册号发码」的新分支；四维频控中 IP 维度取 XFF 首个（事实⑨），设备维度自称（`SmsProperties.java:56-60`）；Nginx **无 `limit_req`**（事实⑩）。
- 复现（静态）：① 确认注册发码将进入白名单（免认证）→ ② 确认 `isSendBlocked` 五维判定（[SmsCodeStore.java:93-115](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115)）中 IP 来源为 `ctx.loginIp()`，其值由 `IpUtil` 取 XFF 首个 → ③ 确认 Nginx 无边缘限速 → 结论：伪造 XFF + 换号即绕过成本墙。
- 影响面：真实短信计费、第三方骚扰、供应商封停（衍生可用性事故）。

**REG-03 · 注册可控字段直写 role/薪资/站点 = 提权**
- 依据：需求要求「审批通过后同时设置薪资 + 岗位/站点/角色」；现状 `createOnboarding` 硬编码 `role=STAFF`（[HrFlowServiceImpl.java:119](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119)），账号/密码由管理员提交（事实⑪），定薪由管理员提交（事实⑬）。
- 复现（静态）：① 追踪注册载荷字段 → ② 若被写入 `HrOnboardingCreateRequest` 或步骤补全 DTO 的 `role`/`stationId`/`basicSalary` → ③ 若未过 `HrConstants.ASSIGNABLE_ROLES` 与驿站启用校验（[HrFlowServiceImpl.java:364-370](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L364-L370)、[:351-359](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L351-L359)）→ 提权成立。
- 影响面：STAFF→STATION_ADMIN 提权、越站数据可见、薪酬口径污染。

**REG-05 · 「审批通过即建号并激活」绕过人工审核**
- 依据：现状建档落 `status=0`，最后一步才转在职（事实⑫）；`HrFlowStepGuard` 保证**按序人工办理**（[HrFlowServiceImpl.java:138-141](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L138-L141)）。需求「审批通过后同时生成…」若被实现为**一键自动补全全部步骤**，则拆除该守卫。
- 复现（静态）：① 确认审批通过动作是否直接调用 `finishOnboarding` 或批量 `completeOnboardingStep` → ② 确认是否有二次人工确认 → ③ 无二次确认即「一键建号并激活」。
- 影响面：注册即账号（若同时沿用申请人密码 + `status=1`），结合链 C 即成完整提权链。

**REG-02 · 手机号枚举**
- 依据：事实②（1109 差异）、`ErrorCode.PHONE_EXISTS`（[ErrorCode.java:91](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L91)）、事实④（未知场景回落 LOGIN 放大枚举）。
- 复现（静态）：① 确认注册发码是否复用 `sendLoginCode`（1109 差异）→ ② 或注册提交是否直接返回 2003 → 任一即枚举。
- 影响面：员工手机号名单（PII）批量外泄。

**REG-04 · 重复手机号 → 目标账号登录 DoS**
- 依据：事实⑦/⑧ + `selectOne` 命中多行抛异常。对照 `EmployeeServiceImpl` 有 `existsActivePhone` 而 `HrFlowServiceImpl.createEmployeeForFlow` 无（[HrFlowServiceImpl.java:264-305](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L305) vs [EmployeeServiceImpl.java:546-555](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L546-L555)）。
- 复现（静态）：① 确认建档链路无手机号查重 → ② 确认 `employee.phone` 无唯一索引（db.md D7）→ ③ 确认登录按 `selectOne(phone)` → DoS 成立。

**REG-06 · 申请列表/详情 IDOR**
- 依据：现状 HR 接口仅 ADMIN（事实⑮），但**注册申请若另建控制器/接口，须显式声明 `@RequireRoles({"ADMIN"})`**；否则 `RequireRolesInterceptor` 会 fail-closed 拒绝（安全），但一旦误加入公开白名单即全开。
- 复现（静态）：核对新接口注解与白名单登记。

**REG-09 · IP 维度频控可被 XFF 伪造绕过**
- 依据：事实⑨/⑩（[IpUtil.java:17-31](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/IpUtil.java#L17-L31)）。
- 复现（静态）：Nginx `$proxy_add_x_forwarded_for` **追加**客户端值 → `IpUtil` 取**首个** → 客户端可预置。

---

## 6. 修复建议（分档：必做 / 建议 / 可选）与逐条验收标准

> 交对应实现角色落地（本角色只出建议与验收标准，不代改代码）。

### 6.1 必做（MUST，闭环前不得公网放行）

| # | 修复建议 | 对应发现项 | 验收标准 |
| --- | --- | --- | --- |
| M-1 | **注册发码不得复用「已存在账号」口径**：新增独立 `SmsScene.REGISTER`（或 `ONBOARD_APPLY`）；**同步修正 `resolveScene`**，未知场景一律**拒绝**而非回落 LOGIN（消除 fail-open 放大） | REG-01/REG-02 | 单测：未知 scene → 拒绝（非 LOGIN）；`REGISTER` 场景对未注册号可发码且**不**走 `sendLoginCode` 的账号查询分支 |
| M-2 | **注册发码全链路的响应与文案恒定化**：无论手机号是否已注册，发码接口**返回体、状态码、错误码、耗时量级一致**；提交阶段「已注册」错误**不在响应中区分**（如需提示，改为**不泄露存在性**的通用文案 + 站内/人工核查） | REG-02/REG-13 | 对「已注册/未注册」两组各 ≥50 次，响应 `{code,message,data}` 与 HTTP 状态**逐字段一致**；不得出现 1109/2003 差异 |
| M-3 | **注册可控字段与权限/薪资解耦**：注册仅存「意向」（`intentStationId`/`intentPosition` 等**非权威字段**）；`role` 在注册链路**恒为 STAFF 且不接受入参**；`stationId` 入审批前**仅作展示**，建档/定岗时由**管理员在服务端校验驿站存在且启用**后写入；`hr_salary` **只能由管理员在定薪步骤提交**，注册载荷中**禁止**出现 `basicSalary/postSalary/performanceBase/allowances` | REG-03 | 契约测试：注册载荷若含 `role/stationId/basicSalary` → 400（未知字段拒绝）或**被忽略且不落库**；建档后 `employee.role=='STAFF'`；定薪值来自管理员提交而非申请 |
| M-4 | **审批通过不得自动激活账号**：保留 `status=0` 与「最后一步才转在职」；自动建号仅到**待激活**态，`pwdChanged=0` 强制首登改密；密码不得沿用注册所填（改为管理员设定一次性初始口令或首登设置流程） | REG-05/REG-03 | 审批通过后 `employee.status==0`；首登强制改密生效；注册所填密码不出现在 `employee.password` 校验链 |
| M-5 | **建档链路补齐手机号唯一校验**（与 `EmployeeServiceImpl` 同口径），并对 `employee.phone` **补数据库唯一约束**（迁移脚本，含存量去重预检） | REG-04 | 迁移前提供存量重复号清单并清零；并发提交同号 → 唯一约束/查重拦截，仅 1 行落库；按手机号 `selectOne` 恒命中 ≤1 行 |
| M-6 | **申请接口收敛为 ADMIN-only**：列表/详情/审批显式 `@RequireRoles({"ADMIN"})`，**不得**加入公开白名单；公开白名单**仅**放行「发码」与「提交申请」两条 | REG-06 | `PublicEndpoints.all()` 新增项**仅为**发码与提交两条；申请列表/详情无 `@RequireRoles` 或非 ADMIN → 403 |
| M-7 | **边缘 + 应用双层限速**：Nginx 对注册发码路径加 `limit_req`（按 `$binary_remote_addr`）并对 `/api/` 全局加 `limit_conn`；应用层在现有五维频控外**增加「手机号 × 全局日上限」与「注册提交日上限」** | REG-01/REG-09 | 压测：单 IP 高频触发即 429/1101；换号轰炸命中全局上限；伪造 `X-Forwarded-For` 不影响真实源限速（Nginx 以 `$remote_addr` 计） |
| M-8 | **注册申请数据治理**：明确**未过审数据的保留期限与清理机制**（定时清理已拒绝/超期未审）；最小必要收集（不得收集与入职无关的字段）；申请详情出参对手机号/姓名**脱敏** | REG-07 | 存在清理任务与配置项（可外置）；超期数据被清理；列表出参手机号脱敏（如 `138****1234`） |
| M-9 | **补来源标识与留痕**：`hr_flow` 增 `source`（`ADMIN`/`SELF_REGISTER`）等标识；自助申请 `operatorId` 允许为空但须记录**来源 IP（脱敏）/UA/提交时间**；审批动作记办理人 | REG-12 | 自助来源可区分；审计可回溯提交与逐审批动作 |

### 6.2 建议（SHOULD）

| # | 修复建议 | 对应发现项 | 验收标准 |
| --- | --- | --- | --- |
| S-1 | **启用真实图形验证码**（替换占位图）作为注册发码前置；并评估无障碍与弱网体验 | REG-10/REG-01 | 生产 `hrm.auth.captcha-enabled=true`；占位图 TODO 消除；无有效 ticket → 1106 |
| S-2 | **注册提交也纳入频控**（当前频控仅在发码侧）：按 IP/设备/时间窗限制「提交」次数 | REG-08/REG-07 | 超限返回 1101 或等价码 |
| S-3 | **入参强校验**：全部注册 DTO 加 `@Valid` + 字段长度上限 + 枚举白名单（岗位/驿站/角色）+ 手机号正则；拒绝未知字段（`FAIL_ON_UNKNOWN_PROPERTIES`） | REG-08/REG-11 | 超长/非法枚举/未知字段 → 400；无 500 |
| S-4 | **服务端 IP 取信改造**：`IpUtil` 仅在**可信代理**场景取 XFF，并做格式校验（IPv4/IPv6 白名单），否则回退 `remoteAddr` | REG-09 | 伪造 XFF 不改变限速归属；非法 IP 串被忽略 |
| S-5 | **注册链路埋点与告警**：发码量、提交量、被拒量异常突增告警；短信日用量阈值告警 | REG-01 | 阈值告警可触发 |
| S-6 | **交接实现后安全复验**：本报告必做项闭环后由本角色复验（L7 复验环节） | 全部 | 复验结论明确（通过/有条件通过） |

### 6.3 可选（MAY）

| # | 修复建议 | 备注 |
| --- | --- | --- |
| O-1 | 短信通道侧设置**日预算/日条数硬上限**与多供应商降级 | 与运维/主智能体 C 档协同 |
| O-2 | 注册引入**行为风控**（滑块/设备指纹/时序特征） | 依赖 R09 算法选型；当前属可选增强 |
| O-3 | 对高风险号段/高风险 IP 段做**黑名单前置拦截** | 运营策略位 |

---

## 7. 结论等级与最小放行条件

### 7.1 结论等级：**阻断（公网）**

判定理由（三条独立可利用路径，任一单独成立即足以阻断公网放行）：
1. **REG-01/REG-09**：公开未鉴权入口对任意手机号发短信，且边缘无 `limit_req`、IP 维度可伪造 → 成本与骚扰不可控；
2. **REG-03/REG-05**：需求要求的「自动建号 + 设薪资 + 设角色 + 设站点」若按字面直译，将拆除现有 `role=STAFF` 硬编码与「建档未生效」两道安全默认值，形成提权链；
3. **REG-02/REG-04/REG-06**：枚举、重复号 DoS、IDOR 三条独立危害。

**判定性质说明**：本结论为**安全实质结论**（非业务口径问题）。评估方与决策方分离——是否放行由**主智能体**按 §10.3 决策；判定为高风险的，主智能体**不得直接放行，须升级用户裁定**。

### 7.2 降级路径

- 满足 §6.1 **全部必做项（M-1 ~ M-9）** 并通过 **§6.2 S-6 安全复验** → 结论降为「**有条件放行**」；
- 若 M-3 / M-4 / M-5 三项中任一未闭环 → **维持阻断**（因涉及提权与 DoS，属不可用缓解置换的硬项）。

### 7.3 最小放行条件（测试库 / 内网预览范围）

若需**先行联调**，可在**不触达公网、不接真实短信通道**的前提下放行，且必须**同时**满足：

| # | 最小放行条件 | 与生产放行的差异 |
| --- | --- | --- |
| A | 仅部署于**内网/预览环境**，Nginx 对该路径**拒绝公网来源**（仅内网 IP 白名单） | 生产为公网可达 |
| B | 短信通道保持 `provider=none` **降级通道**（不真发短信，仅日志；且日志不落验证码明文） | 生产为 `aliyun` 真实计费通道 |
| C | `hrm.sms.dev-universal-code` 仅测试环境可非空；**确认 prod profile 下启动 fail-fast** | 生产该键必须为空 |
| D | 使用**测试库**，且测试库中**不得存在真实员工 PII**；验证码/申请数据用造数 | 生产为真实 PII |
| E | 至少先闭环 **M-3（字段解耦）** 与 **M-4（不自动激活）** 两项硬项；其余必做项登记为上线前债务 | 生产须全部闭环 |
| F | 明确**联调结束后清理**测试注册数据 | 生产按保留期限治理 |

> 差异小结：内网预览放行的是「**验证功能与契约可行性**」，**不构成任何生产安全认可**；M-1/M-2/M-5/M-6/M-7/M-8/M-9 在公网放行前**必须**闭环。

---

## 8. 残余风险登记

| 编号 | 残余风险 | 触发前提 | 处置 |
| --- | --- | --- | --- |
| R-1 | 短信码仅证明「持有某手机号」，**不证明姓名与号主一致** | 号码被转借/被冒用提交 | 接受（属业务口径）：加入职材料核验作为审批依据；SEC 侧不承诺身份真实性 |
| R-2 | IP 维度限速在**未完成 S-4** 前仍受伪造影响 | M-7 已加 Nginx `$remote_addr` 限速后降级 | M-7 闭环后该残余降为「需真实多 IP 资源」，可接受 |
| R-3 | 图形码生产渲染未就绪（REG-10） | S-1 未落地前，注册发码仅靠频控 | 已列建议项；若 M-7 到位可暂缓，但需登记 |
| R-4 | 未过审数据保留期满清理若误配，可能误删待审件 | 清理任务参数不当 | 清理仅针对「已拒绝/超期未审」且灰度上线 |
| R-5 | 存量库若已存在重复手机号，M-5 唯一约束迁移会失败 | 迁移前未去重 | 迁移脚本须含存量预检与人工确认（属 C 档，主智能体授权） |
| R-6 | 「一键审批」若产品坚持保留，人工审核环节实际弱化 | 产品口径 | 需**用户口径确认**；SEC 侧要求至少一次显式人工确认动作 |

---

## 9. 上线前必须闭环清单（P0.5 + L7 闸门）

- [ ] **M-1** 新增独立注册短信场景 + `resolveScene` 未知场景拒绝（消除 fail-open 放大）
- [ ] **M-2** 发码/提交响应**完全恒定化**，杜绝手机号存在性差异（含 1109/2003）
- [ ] **M-3** 注册字段与 role/薪资/站点**解耦**；`role` 恒 STAFF；薪资仅管理员可写
- [ ] **M-4** 审批通过**不自动激活**；保留 `status=0` + 首登强制改密；不复用注册密码
- [ ] **M-5** 建档补齐手机号唯一校验 + `employee.phone` 数据库唯一约束（含存量去重）
- [ ] **M-6** 申请管理接口 ADMIN-only；白名单仅放行发码与提交
- [ ] **M-7** Nginx `limit_req`/`limit_conn` + 应用层全局日上限
- [ ] **M-8** 未过审数据保留期限与清理机制 + 最小必要 + 出参脱敏
- [ ] **M-9** `hr_flow` 补来源标识与提交/审批留痕
- [ ] **S-6** 由网络安全工程师执行**安全复验**并出具复验结论
- [ ] **登记**：将注册端点路径正式登记进 `PublicEndpoints`，并复核 `PublicEndpoints.all()` 快照回归断言

---

## 10. 事实性纠正（对派发口径与既有认知的修订）

1. **「短信链路可复用」需修正**：现有 `POST /auth/sms/send` **只服务已存在账号**，对未注册手机号返回 1109（[AuthServiceImpl.java:264-269](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269)）。注册链路**不能直接复用**该口径，且**不能**用「查不到就放行发码」的方式改造——那会立刻产生 REG-02 枚举。必须新增独立场景并恒定化响应。
2. **「审批通过即同时生成档案+薪资+岗位/站点/角色」与现有安全默认值冲突**：现状 `createOnboarding` 硬编码 `role=STAFF`，账号/密码由**管理员**在步骤中提交，建档落 `status=0`，最后一步才在职（事实⑪⑫⑬）。需求字面实现会**拆除**这三道默认值——这是本报告判定「阻断」的核心依据之一，非纯增量功能。
3. **「注册后联动建档」存在既有缺陷放大**：`HrFlowServiceImpl.createEmployeeForFlow` **不校验手机号唯一**（只校验 username），且库层按决策 D7 **无唯一索引**（事实⑦⑧）。注册放量后该缺口由「低概率」变为「可被主动构造」，且后果是**目标账号登录 DoS**（`selectOne` 多行异常），非仅数据脏。**该缺陷当前即存在**，注册上线前须先行修复。
4. **图形码不宜计入缓解**：`CaptchaStore` 当前仅返回**固定占位图**且默认关闭（事实⑤），在真实渲染落地前**不能**作为 REG-01 的有效缓解计入 P0.5 结论。
5. **IP 维度频控不可信**：`IpUtil` 取 XFF **首个**值（事实⑨），Nginx 仅追加不去除，客户端可预置伪造；且 Nginx 样例**无任何 `limit_req`**（事实⑩）。故「已有四维频控」不足以支撑公网放行结论。
6. **`hr_flow` 无来源字段**（事实⑭）：无法区分自助注册与管理员发起；「防污染/审计/清理」缺数据基础，需补列（M-9）。

---

> 本报告为**安全技术结论**，不构成放行决定。授权与放行由主智能体按 `项目规则1.md` §10.3 行使；判定为高风险项，主智能体不得直接放行，须升级用户裁定（调度规则 §9 冲突裁决表「安全风险判定 vs 进度/交付压力」）。
