# 登录体系改造 · 安全威胁建模与鉴权/会话安全审计报告

> 文档性质：**安全技术结论**（网络安全工程师产出，非授权结论）
> 审计范围：`hrm-dev/hrm-server/src/main/java/com/qiujie/` 鉴权与会话实现（P0 地基批次）+ 拟改造登录口径的威胁建模
> 审计方式：**静态代码与配置审计**（本机无 JDK/Maven/Redis/Docker，未运行编译与端到端验证，运行时项见 §6）
> 结论用途：作为主智能体 C 档授权（§10.3）的**技术输入**；本报告不构成放行决定，授权与放行由主智能体按 §9/§10.3 行使
> 日期：2026-09-24

---

## 1. 结论摘要（先行）

**总体判定：不允许在当前形态下将后端公网暴露。** 存在 **1 项严重、3 项高风险**；其中 `auto-dispatch` 未认证写端点与传输层未强制 HTTPS 为两条**硬性放行阻断项**。已实现的鉴权主线（JWT 校验链、Redis 会话比对、角色门槛 fail-closed、路径资源归属校验）**设计方向正确、无发现可直接利用的越权/绕过漏洞**；主要问题集中在**公开暴露面、传输与部署、以及拟改造方案（设备信任 / 短信通道 / 多端会话）的固有设计缺陷**。

### 1.1 高风险及以上清单

| 编号 | 标题 | 严重级 | 一句话影响 |
| --- | --- | --- | --- |
| SEC-AUTH-01 | `POST /api/v1/work-orders/auto-dispatch` 未认证公开写端点 | **严重** | 任意人（无凭据）可伪造工单、触发通知轰炸、污染 SLA 统计 |
| SEC-AUTH-02 | 传输层未强制 HTTPS、无 HSTS（Nginx 样例默认仅 80） | **高** | 口令与 Bearer token 明文过公网，会话可被中间人劫持 |
| SEC-AUTH-03 | 登录接口无频控 / 无失败锁定 / 无验证码 | **高** | 弱口令账号可被在线爆破 |
| SEC-AUTH-04 | 公开端点白名单与 `auth:false` 语义仅覆盖登录与派单，但派单端点无签名验签落地 | **高** | 白名单已就位即被部署放行（代码注释自认不得公网暴露），风险外溢 |

> 说明：SEC-AUTH-04 与 SEC-AUTH-01 为同一根因的两面（白名单已具备 vs 验签未落地）。**中风险 6 项、低风险 4 项**见 §3。

### 1.2 已验证为「合格」的控制（正向结论，不重复列举）

- **JWT 密钥来源与强度**：`jwt.secret` 读配置、无硬编码；构造期校验 `≥32 字节`，不足即启动失败（[JwtUtil.java:27-36](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/JwtUtil.java#L27-L36)）。算法固定 `signWith(key, HS256)`，解析走 `parseClaimsJws`（强制签名校验）（[JwtUtil.java:48-72](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/JwtUtil.java#L48-L72)）。
- **会话吊销有效**：登出 / 改密 / 禁用 / 删除 / 重置密码均 `sessionUtil.delete`，`JwtAuthFilter` 以 Redis `jti` 比对实现「登出即失效 / 被顶下线 / 强制下线」（[JwtAuthFilter.java:83-96](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/filter/JwtAuthFilter.java#L83-L96)、[AuthServiceImpl.java:130-137](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L130-L137)、[EmployeeServiceImpl.java:597-604](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L597-L604)）。
- **角色门槛 fail-closed**：非公开端点未声明 `@RequireRoles` 一律 403，杜绝「漏声明即放行」；角色以 Redis 会话为权威（[RequireRolesInterceptor.java:40-58](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L40-L58)）；全部 23 个控制器均位于 `/api/**` 拦截范围内（已核对）。
- **横向越权（IDOR）抽查通过**：`hr/profiles/{employeeId}`、`hr/salary-structures/{employeeId}`（仅 ADMIN 或本人，否则 403）、`kpi/scores/{employeeId}`（STAFF 限本人、站长限本站）、`finance/payrolls/{id}`（本人或 ADMIN）均在 Service 内做归属判定，未发现「传入他人 id 即读取他人数据」。详见 §3.4 已验证项。
- **报错不外泄**：`GlobalExceptionHandler` 兜底 500 仅记日志、对外通用文案；401/403/404 文案为固定串（[GlobalExceptionHandler.java:125-130](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/handler/GlobalExceptionHandler.java#L125-L130)）。无 `*_change_me` 真实凭据入仓（`application.yml` 全为占位符）。
- **日志清洗有正向先例**：`ClientLogSanitizer` 采用**白名单复制 + 凭据正则擦除 + 截断**，明确丢弃请求/响应体原文（[ClientLogSanitizer.java:47-85](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L47-L85)）——该模式应被短信方案直接复用（见 §4）。

---

## 2. 威胁建模（STRIDE）

### 2.1 资产清单（按敏感度）

| 资产 | 位置 | 敏感度 |
| --- | --- | --- |
| 员工口令散列（BCrypt，cost=10） | `employee.password` | 高 |
| JWT 签名密钥 | `jwt.secret`（prod 外置配置） | 极高（泄露=可伪造任意身份） |
| Redis 登录会话（jti/role/stationId/loginIp） | `hrm:session:{employeeId}` | 高 |
| 登录日志（IP/UA/失败原因） | `login_log` | 中（取证价值） |
| 人事档案 / 定薪 / 银行卡 / 手机号 | `hr_profile` / `hr_salary` / `employee.phone` | 高（PII） |
| 考勤经纬度 | `attendance_record` | 高（位置属敏感个人信息） |
| 工单（含群消息原文、处理人） | `work_order` / `work_order_timeline` | 中 |
| 短信 AccessKey / 高德 Key（拟接入） | 外置配置 | 极高（可盗刷/计费） |

### 2.2 信任边界

```
[公网] ──①──> [Nginx 80/443 反代] ──②──> [127.0.0.1:8080 后端] ──③──> [MySQL/Redis 本机]
   │                      │                        │
   │                      │                        └─ 边界③：本机回环，须"仅本机监听 + 强口令"
   │                      └─ 边界②：应用只信 Nginx 透传的 X-Real-IP/X-Forwarded-For（当前可被客户端伪造，见 SEC-AUTH-06）
   └─ 边界①：HTTPS 未强制（SEC-AUTH-02）── 所有凭据与 token 在此边界内明文
外部第三方（拟）：[阿里云短信 API] / [高德 API] ── 出网边界，凭据与用户位置数据跨界（见 §4）
```

### 2.3 攻击面图（登录体系）

```
公开（无需凭据）            已认证（Bearer）
├─ POST /auth/login ★漏洞:爆破/枚举   ├─ /auth/{logout,me,password}
├─ POST /work-orders/auto-dispatch ★★严重:未认证写   ├─ 各域业务端点（角色门槛 fail-closed）
└─（拟）POST /auth/sms/send ★新面:短信轰炸/枚举     └─ 路径资源（@DataScope 归属校验）
   （拟）POST /auth/login-sms ★新面:账户接管
   （拟）GET  /amap/*       ★新面:Key 泄露/位置外传
```

### 2.4 STRIDE 逐类分析

| 类别 | 适用场景 | 现状判定 |
| --- | --- | --- |
| **S**poofing 伪装 | JWT 伪造（密钥强度 ✅ 已校验、算法固定 ✅）；**设备指纹伪装（拟，核心风险）**；IP 伪装（SEC-AUTH-06） | 现状：JWT 侧合格；**新方案有高危** |
| **T**ampering 篡改 | JWT 篡改签名/声明（jjwt 强制验签 ✅）；工单内容经公开端点被任意构造（SEC-AUTH-01） | 有严重项 |
| **R**epudiation 抵赖 | 登录/操作留痕 ✅（登录日志、工单时间线）；**公开端点无来源可追溯**（reporter_id 置 null，[WorkOrderServiceImpl.java:292-293](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/workorder/impl/WorkOrderServiceImpl.java#L292-L293)）；IP 可伪造削弱取证 | 有高项 |
| **I**nformation Disclosure 信息泄露 | 脱敏 ✅（手机/姓名/银行卡）；`*.map` 未显式禁（SEC-AUTH-10）；404/403 存在性探测（SEC-AUTH-12）；会话 key 可枚举（需 Redis 权限，可接受） | 低-中 |
| **D**enial of Service 拒绝服务 | 登录无频控（SEC-AUTH-03）；**短信轰炸成本放大（拟）**；Excel 10MB 上限 ✅ | 有高项（新方案更甚） |
| **E**levation of Privilege 提权 | 角色门槛 fail-closed ✅；路径资源归属 ✅；**首登改密服务端未强制（SEC-AUTH-05）**；多端会话吊销粒度粗（拟） | 有中项 |

---

## 3. 发现项清单

> 严重级：严重 / 高 / 中 / 低；可利用性：可远程 / 需认证 / 需本地（部署条件）。
> 所有「复现步骤」均为**静态可核验**（阅读代码/配置即可确认），不涉及生产验证。

### SEC-AUTH-01 · `POST /api/v1/work-orders/auto-dispatch` 未认证公开写端点

- **严重级**：严重
- **可利用性**：可远程（**无需任何凭据**）
- **影响面**：任意网络可达者可创建工单（写入 `work_order` + `work_order_timeline`），并对被指派员工发送系统通知；`stationId` 缺省回落 `DEFAULT_STATION_ID` 且只校验"存在"，可跨驿站构造；返回 `WorkOrderDetailVO` 泄露工单内容；大量调用可污染 SLA/负载统计并使通知轰炸。
- **依据**：端点被加入公开白名单（[PublicEndpoints.java:18-25](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L18-L25)）；控制器方法**未声明** `@RequireRoles`（[WorkOrderController.java:79-92](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/controller/workorder/WorkOrderController.java#L79-L92)）；业务逻辑直接建单+发通知（[WorkOrderServiceImpl.java:256-322](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/workorder/impl/WorkOrderServiceImpl.java#L256-L322)）。代码/架构本身已标注「签名/解密落地前不得公网暴露」（[server-architecture.md R-3](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/server-architecture.md)）。
- **复现步骤（静态）**：① 确认端点非认证路径 → ② 确认方法无 `@RequireRoles`、`RequireRolesInterceptor` 因 `PublicEndpoints.isPublic` 直接放行（[RequireRolesInterceptor.java:41-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/RequireRolesInterceptor.java#L41-L49)）→ ③ 结论：`curl -X POST .../auto-dispatch -d '{"content":"x","stationId":1}'` 无需凭据即可建单（**勿在生产执行**，仅静态推证）。
- **修复建议**（交实现角色，非本角色改码）：
  1. 接入企业微信回调**签名验签与解密**（`msg_signature`/`timestamp`/`nonce` + `EncodingAESKey`），验签失败 401；
  2. 验签落地前，**Nginx 层拒绝公网访问该路径**（仅内网/企微出口 IP 白名单）并加 `limit_req`；
  3. 业务侧对 `stationId` 增加「可信来源映射校验」，不信任请求体自称归驿站；
  4. 记录来源标识（企微回调时间戳/签名摘要）替代 `reporter_id=null`，补齐抵赖面。
- **验收标准**：未带有效企微签名的请求返回 401；带签名的合法回调可建单；非法签名/重放时间戳被拒。

### SEC-AUTH-02 · 传输层未强制 HTTPS、无 HSTS

- **严重级**：高
- **可利用性**：需本地（网络中间人，公共 Wi-Fi/同网段/恶意出口）
- **影响面**：登录口令、`Authorization: Bearer <token>`、JWT（3 天时长后危害放大）在公网明文传输 → **账户接管与会话劫持**。前端 token 落 `localStorage`（[auth.js:4-35](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/stores/auth.js)），一旦被中间人截获即长期可用。
- **依据**：生产 Nginx 样例 `listen 80` 为默认态，HTTPS 段**整体被注释为「可选」**，且无 HSTS（[nginx.conf.example:22-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L22-L24)、[:80-94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L80-L94)）。
- **复现步骤（静态）**：阅读上述 Nginx 样例 → 确认唯一的 `listen` 是 80、无 443 块、无 `Strict-Transport-Security`。
- **修复建议**：全站 HTTPS（443 + TLS1.2/1.3）+ 80 → 301 跳转 + HSTS（`max-age≥31536000; includeSubDomains`）；确认证书有效并非自签。属运维执行、主智能体授权（C 档）。

### SEC-AUTH-03 · 登录接口无频控 / 无失败锁定 / 无验证码

- **严重级**：高
- **可利用性**：可远程（无需凭据）
- **影响面**：在线口令爆破。初始口令为**全局共享**的 `hrm.employee-init-password`（[EmployeeServiceImpl.java:93-94,328-332](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L93-L94)），一旦该值弱/泄露，可对全量员工账号批量尝试。
- **依据**：`AuthServiceImpl.login` 全程无尝试计数、无锁定、无验证码、无节流（[AuthServiceImpl.java:52-94](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L52-L94)）；`LoginRequest` 无验证码字段（[LoginRequest.java:11-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/dto/auth/LoginRequest.java#L11-L18)）。
- **复现步骤（静态）**：通读 `login` 分支 → 失败仅 `recordLoginLog` 后抛 1001，无任何计数/延迟/锁定。
- **修复建议**：按 `账号 + IP` 维度限流（Nginx `limit_req` 至少一层 + 应用层计数）；失败 N 次锁定或指数退避；高失败率触发图形验证码。

### SEC-AUTH-04 · 公开白名单 `auto-dispatch` 缺验签即已就位，存在「部署即放行」风险

- **严重级**：高
- **可利用性**：需本地（部署决策条件）
- **影响面**：白名单代码已合入主干，部署脚本不感知该端点是否已具备签名校验（[deploy.sh](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh) 无相关门禁），存在「签名 TODO 未做但白名单已生效 → 直接公网暴露」的流程缺口。代码注释虽已警示（[PublicEndpoints.java:18-22](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L18-L22)），但**注释不是门禁**。
- **修复建议**：以「配置开关 + 默认关闭」替代裸白名单（未配置企微签名密钥时该端点统一 404/403）；或按 SEC-AUTH-01 以 Nginx 层默认拒绝 + 显式放行清单落地。
- **验收标准**：未配置签名密钥的部署环境中，请求该端点返回 404/403（fail-closed）。

### SEC-AUTH-05 · 首登强制改密仅前端实现，服务端未强制（共享初始口令长期有效）

- **严重级**：中
- **可利用性**：需认证（以初始口令登录即可）
- **影响面**：`pwd_changed=0` 的用户在前端被引导改密，但**服务端不拦截**——可直接携带登录返回的 token 调用全部业务接口，读取本人 PII 等。配合全局共享初始口令，未改密账号等同"默认凭据可用的真实账号"。
- **依据**：`pwdChanged` 仅作为出参下发（[AuthServiceImpl.java:109-110](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L109-L110)、[MeVO.java](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/vo/auth/MeVO.java)）；全仓检索 `pwdChanged` 无服务端拦截逻辑。
- **复现步骤（静态）**：检索 `pwdChanged` 使用点 → 仅赋值出参，无过滤器/拦截器判定。
- **修复建议**：`JwtAuthFilter` 或专用拦截器对 `pwd_changed=0` 的会话，除 `/auth/me`、`/auth/password`、`/auth/logout` 外一律 403（业务码建议 1005 类"须先修改初始密码"）。当前若不修，须在放行说明中登记为**已知接受风险**。

### SEC-AUTH-06 · 登录日志 IP 可被客户端伪造（XFF 取首个值）

- **严重级**：中
- **可利用性**：可远程（构造请求头）
- **影响面**：登录日志失真 → 取证/审计失效；未来按 IP 限流或封禁可被绕过。
- **依据**：`IpUtil` 取 `X-Forwarded-For` **首个**非空值（[IpUtil.java:17-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/IpUtil.java#L17-L24)）；Nginx 侧 `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` 会**保留客户端自带 XFF**（[nginx.conf.example:64](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L64)），拼接后首段即攻击者可控值。
- **复现步骤（静态）**：客户端发送 `X-Forwarded-For: 1.2.3.4` → Nginx 转发为 `1.2.3.4, <真实IP>` → `IpUtil` 取到 `1.2.3.4`。
- **修复建议**：优先使用 `X-Real-IP`（由 `$remote_addr` 覆写、不可伪造），或取 XFF 最后一段；Nginx 改为 **覆写** XFF 而非追加。

### SEC-AUTH-07 · 单账号单会话模型在多端并存改造下的固有缺陷（拟改造项）

- **严重级**：中（改造前）
- **可利用性**：需认证 / 需本地（取决于最终实现）
- **影响面**：当前 `hrm:session:{employeeId}` 为**单键单会话**，三端独立客户端共用同一后端时必然互踢（[SessionUtil.java:19,31-33](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/SessionUtil.java#L19)）；若改造为多端并存却设计不当，会引入**会话数量膨胀、吊销粒度变粗、单 token 泄露横向影响面扩大**。
- **修复建议**：见 §4「三端共用后端 + 单会话并存」条目。
- **是否 C 档授权前置条件**：**是**（会话模型属结构变更）。

### SEC-AUTH-08 · 应用进程可能以 root 运行（部署回退分支）

- **严重级**：中
- **可利用性**：需本地（部署方式条件）
- **影响面**：违反 D 档红线「不得以 root 运行应用进程」。进程若被攻破，攻击者直接获得 root，扩大容器/主机提权面。
- **依据**：`deploy.sh` 强制 root 运行（[deploy.sh:131-133](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh#L131-L133)），其 `nohup` 分支**直接以当前身份（root）拉起 java，未降权**（[deploy.sh:269-279](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/deploy.sh#L269-L279)）。systemd 单元已正确配置 `User=www/Group=www`（[hrm-server.service:38-39](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/hrm-server.service#L38-L39)），但 `RESTART_MODE=auto` 在无 systemd 环境下会回退 nohup。
- **修复建议**：`nohup` 分支显式降权（如 `runuser -u www --` / `setpriv --reuid`）；或文档强制「生产必须 systemd/宝塔非 root 托管」，并在放行条件中固化。

### SEC-AUTH-09 · 依赖与供应链未做漏洞扫描（不可凭记忆断言）

- **严重级**：中（待证据）
- **依据**：`pom.xml` 关键依赖 Spring Boot 3.3.4、jjwt 0.11.5、MyBatis-Plus 3.5.7、EasyExcel 3.3.4（[pom.xml:7-117](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/pom.xml#L7-L117)）。**本报告不对具体 CVE 作任何断言**（禁止凭记忆判定库漏洞）。
- **修复建议**：实施 `OWASP Dependency-Check` 或 `Trivy fs` 扫描，以**扫描报告**为准判定；重点审 EasyExcel/POI 传递依赖（Excel 解析属不可信输入面，已有 10MB/xlsx 白名单缓解）。新增阿里云短信/高德 SDK 时同样先锁版本、核来源（官方 Maven 仓库）、审传递依赖。

### SEC-AUTH-10 · 生产 `*.map` 未在 Nginx 显式拒绝

- **严重级**：低
- **影响面**：源码结构泄露（D 档红线相关：禁止公开生产 `*.map`）。
- **依据**：`hrm-admin` Vite 构建**未开启** sourcemap（[vite.config.js:17-20](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/vite.config.js#L17-L20)）→ 当前**不产出** `.map`，风险暂不生效；但 `hrm-demo` 产 hidden `.map` 且文档已提示必须禁访问，生产 Nginx 样例**未见**任何 `.map` 拒绝规则（[nginx.conf.example:44-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L44-L49) 的 `/assets/` 会按 `try_files` 直接投递实际存在的 `.map`）。
- **修复建议**：Nginx 增加 `location ~* \.map$ { return 404; }`；构建侧显式 `build.sourcemap=false` 并纳入验收。

### SEC-AUTH-11 · JWT 无 `iss`/`aud`/`nbf` 校验，且无密钥轮换机制

- **严重级**：低
- **影响面**：自签发自用场景下 `iss/aud` 缺失风险有限；无 `kid`/密钥版本位 → 密钥轮换需全员重登，密钥疑似泄露时无平滑吊销手段。
- **依据**：`generate` 仅设 `sub/userId/role/jti/iat/exp`（[JwtUtil.java:48-59](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/util/JwtUtil.java#L48-L59)）。
- **修复建议**：引入 `kid` 或密钥版本 claim，支持双密钥并行过渡；按需加 `iss/aud` 固定校验。`alg=none`/算法混淆的实际拒绝行为建议在服务器阶段以构造样例验证（见 §6）。

### SEC-AUTH-12 · 404/403 逐端点口径差异可被用于资源存在性探测

- **严重级**：低（设计取舍，已知并接受）
- **影响面**：同一资源在不同端点口径不一致（ADR-07 有意为之）——`NOT_FOUND` 端点不泄露跨站资源存在性，但 `FORBIDDEN` 端点会对**存在的无权资源**返回 403、对不存在返回 404，从而可区分「存在但无权」与「不存在」。属信息量极小的端点差异。
- **依据**：[DataScopePolicy.java:9-18](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/enums/DataScopePolicy.java#L9-L18)、[ResourceAccessChecker.java:27-44](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ResourceAccessChecker.java#L27-L44)。控制器注释明确「403/404 分界不得统一」。
- **判定**：**接受该风险**（业务契约需要），但需确认**无高敏感资源依赖 404 隐藏存在性**。建议保留现状并记录。

### SEC-AUTH-13 · `ResponseWriter` 手工拼接 JSON（当前无注入，需防腐化）

- **严重级**：低
- **影响面**：当前 `message` 均为 `ErrorCode` 固定中文串，**无可利用注入**；但若后续引入动态内容（如 `GlobalExceptionHandler.handleNoHandlerFound` 已反射 `getRequestURL()`，[GlobalExceptionHandler.java:119-123](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/handler/GlobalExceptionHandler.java#L119-L123)），手拼 JSON 存在转义缺失隐患。
- **修复建议**：`ResponseWriter` 改用 Jackson 序列化；禁止向手工 JSON 注入用户可控内容。

### 3.4 横向越权（IDOR）核查结论：抽查通过

| 端点 | 门槛 | 服务端归属判定 | 结论 |
| --- | --- | --- | --- |
| `GET /hr/profiles/{employeeId}` | 任意登录角色 | ADMIN 或本人，否则 403（[HrProfileServiceImpl.java:312-319](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/hr/impl/HrProfileServiceImpl.java#L312-L319)） | 合格（站长亦被拒，比注释更严） |
| `GET /hr/salary-structures/{employeeId}` | 任意登录角色 | 同上 `checkAccess` | 合格 |
| `GET /kpi/scores/{employeeId}` | 任意登录角色 | STAFF 限本人、站长限本站，否则 403 | 合格 |
| `GET/POST /finance/payrolls/{id}` | 任意登录角色 | 非 ADMIN 限本人（`employeeId` 比对） | 合格 |
| `POST /work-orders/{id}/assign,status,transfer` | 任意登录角色 | `WorkOrderAccessPolicy` + 业务码 8002/8003 | 合格（口径已在 Mock 契约内） |

> 「`applyDataScope` 只收敛 `stationId` 不收敛 `employeeId`」的担忧**未形成实际横向越权**——因为涉及 `employeeId` 的端点均在 Service 内单独做了本人/本站判定，未依赖 L1 收敛。风险点在于「**依赖逐端点自觉**」：新增端点若忘记声明 `@DataScope` 且未自行判定，则无第二道防线。建议在架构层补「按资源类型解析归属」的解析器注册表（代码 TODO 已记，[DataScope.java:23-24](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/annotation/DataScope.java#L23-L24)）。

---

## 4. 对拟改造登录方案的风险评估

> 逐条给「方案设计缺陷 / 建议加固措施 / 是否可作为 C 档授权前置条件」。方案尚未定稿，以下为**按下述口径建模**的结论。

### 4.1 双通道登录（密码 + 短信并存）

| 项 | 内容 |
| --- | --- |
| 方案设计缺陷 | ① 短信通道是**新的账户接管入口**：短信登录若不受与密码通道一致的限流/锁定约束，则整体安全水位被拉低；② 需明确短信登录是否也受「首登强制改密（SEC-AUTH-05）」约束，否则可绕过；③ 新增公开端点 `/auth/sms/send`、`/auth/login-sms` 扩大攻击面 |
| 建议加固 | 两通道共享同一套失败计数/锁定/审计（写入同一登录日志）；短信登录后仍须满足 `pwd_changed=1`；`send` 与 `login` 均纳入 IP+账号+设备多维限流 |
| C 档授权前置 | **是**（新增公开端点，须先过 P0.5 安全评估再公网暴露） |

### 4.2 设备信任（新设备才须短信）

| 项 | 内容 |
| --- | --- |
| **方案设计缺陷（本方案核心风险）** | 若「是否新设备」依赖**纯前端采集的设备指纹**（UA/屏幕/时区/Canvas 等），则该指纹**可被伪造或重放**：攻击者只要复现一个"已信任"指纹，即可让服务端判定为老设备，从而**绕过短信二次验证**——短信 2FA 实际降级为可选。这是本方案的**高危设计缺陷**，必须在设计阶段消除 |
| 建议加固 | ① 信任状态**必须由服务端持有**：验证通过后由服务端签发独立 `device_token`（建议 `HttpOnly + Secure + SameSite` Cookie 承载，或作为独立 claim 且与主会话解绑），前端指纹仅作弱信号、**不得作为放行依据**；② 信任项绑定 `userId + 弱指纹摘要 + 首次信任时间`，设**有效期（如 30 天）**与单账号最大信任设备数；③ 信任令牌在改密/异常/强制下线时失效；④ 设备列表的查看/撤销严格限本人（`userId` 归属校验）；⑤ 设备列表不得泄露他人信息（仅返回本人设备） |
| C 档授权前置 | **是**（设备信任登记 = 会话/身份模型变更，且直接决定 2FA 是否可被绕过） |

### 4.3 会话 3 天 + 每 3 天短信重认证

| 项 | 内容 |
| --- | --- |
| 方案设计缺陷 | ① 若重认证状态存放在**客户端**或依赖**客户端时钟**，可被改系统时间绕过；② 若仅靠 token 长 TTL，则"每 3 天重认证"无强制力；③ 若把 `lastAuthAt` 放进 JWT claim，则**旧 token 重放**即可保持"未过期"状态；④ 到期边界（59 分 vs 61 分）存在竞态 |
| 建议加固 | ① `lastAuthAt` 存 **Redis 会话**（服务端权威），每次请求校验 `now − lastAuthAt ≤ 3d`，超期返回**专用 401（须重认证）**而非普通失效；② 时间一律取**服务端单调时钟**，忽略客户端时间；③ 重认证成功才更新 `lastAuthAt`，且**轮换会话 jti**（旧 token 因 jti 不再匹配而立即失效，天然防重放）；④ 边界用"服务端请求时刻单次判定"，避免双端计时竞态 |
| C 档授权前置 | **是** |

### 4.4 阿里云短信（Key 待申请 + 未配置降级）

| 项 | 内容 |
| --- | --- |
| 方案设计缺陷 | ① **随机性**：验证码若用 `Math.random()`/`Random` 可被预测，必须 `SecureRandom`；② **频控缺失** → 短信轰炸（SMS bombing）与**成本放大攻击**；③ **枚举**：`send` 接口可被用于探测「手机号是否已注册」；④ **未配置降级若退化为"固定码/跳过校验"= 默认不安全**；⑤ AccessKey 泄露 = 可直接盗刷；⑥ **明文红线**：验证码可能进入日志/异常/响应体 |
| 建议加固 | ① `SecureRandom` 生成 ≥6 位；② 一次性、TTL≤5min、尝试上限≤5、**恒定时间比较**（防时序）；③ 同手机号/同 IP/同设备/同账号**多维限频** + 达阈值前置图形验证码；④ `send` 对"未注册手机号"与"已注册"**返回一致响应**（或统一延后提示），阻断枚举；⑤ Key 走**最小权限**（仅短信发送 API）+ 外置 + 轮换（C 档）；⑥ **降级必须是 fail-closed**：未配置 Key 时**禁用短信通道并明确提示**，绝不弱兜底；⑦ **验证码绝不入日志/响应/异常**——复用 `ClientLogSanitizer` 白名单，并在其凭据擦除正则中**补充 `smsCode=`/`verifyCode=`/`code=`/`captcha=` 形态**（现有正则仅覆盖 token/password/pwd，[ClientLogSanitizer.java:47-49](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/service/support/ClientLogSanitizer.java#L47-L49)）；验证码校验失败文案不得回显验证码 |
| C 档授权前置 | **是**（凭据轮换下发属 C 档；新增出网依赖） |

### 4.5 三端独立客户端共用同一后端（单会话互踢问题）

| 项 | 内容 |
| --- | --- |
| 方案设计缺陷 | 现 `hrm:session:{employeeId}` **单键单会话**：三端并存必然互踢；若简单改为"多会话集合"而不控制，则**会话数量膨胀、吊销粒度变粗（只能全踢）、单 token 泄露的横向影响面扩大**；且三端应各自适用不同角色口径（员工端禁管理端点等），若共用一套角色门槛则可能越权 |
| 建议加固 | ① 会话键改 `hrm:session:{userId}:{deviceId}`（或 `{jti}`）+ 索引集合 `hrm:sessions:{userId}`；② JWT 增 `deviceId`，请求校验该 `jti` 存在于集合；③ **精准吊销**：登出/强制下线按 `jti` 或按设备维度撤销，而非整账号清空；④ 单账号**最大并发设备数**限制；⑤ **设备维度授权**：员工端/老板端/网页端各自声明允许的角色集合（`@RequireRoles` 之外增加"端标识"约束），防止低权端冒充高权端 |
| C 档授权前置 | **是**（结构变更） |

### 4.6 高德定位 API（Key 待申请）

| 项 | 内容 |
| --- | --- |
| 方案设计缺陷 | ① 位置属**敏感个人信息**；② 前端直连高德会**暴露 Key**（可被盗刷额度/计费）；③ 将用户坐标上传第三方涉**隐私合规**（告知同意、最小必要）；④ 现考勤已存经纬度（[AttendanceRecord.java:66-69](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/entity/AttendanceRecord.java#L66-L69)），且打卡坐标为**客户端上报**（可伪造） |
| 建议加固 | ① Key **仅服务端持有**，由后端代转发高德 Web 服务 API，**不下发前端**；② 坐标按最小必要精度上传与留存，明确留存期限与访问控制；③ 落隐私政策与告知同意；④ 明确"客户端坐标不可信"这一事实，定位仅作**辅助信号**，核心校验不单点依赖可伪造的 GPS 上报 |
| C 档授权前置 | **是**（新增出网依赖 + 凭据下发 + 隐私合规） |

---

## 5. 上线前必须闭环的放行条件（硬性清单）

> 主智能体 C 档授权前，以下各项须有**闭环证据**（修复提交/配置快照/复验结论）；未闭环不得给出"可公网暴露"结论。

| # | 放行条件 | 关联发现 | 闭环判据 |
| --- | --- | --- | --- |
| **R1** | `auto-dispatch` 在接入企微签名验签前，**Nginx 层默认拒绝公网访问**（仅内网/企微出口 IP 白名单）+ 限流；或改为"未配置验签密钥即 404"的 fail-closed | SEC-AUTH-01 / 04 | 外部 IP 请求返回 401/403/404 的证据；合法签名回调放行证据 |
| **R2** | **全站 HTTPS 强制**（443 + TLS1.2/1.3 + 80→301）+ HSTS | SEC-AUTH-02 | 生效配置 + `curl -I` 证据（HSTS 头、301 跳转） |
| **R3** | 登录接口**至少一层限流**（Nginx `limit_req` 或应用层）+ 失败锁定/退避 | SEC-AUTH-03 | 阈值配置 + 触发限流的响应证据 |
| **R4** | 所有 `change_me_*` 占位符在 **prod 外置配置**中已替换（含 `jwt.secret`、数据源、Redis 口令、初始口令）；外置配置权限 600 且不入 Git | 配置基线 | `check_external_config` 通过证据 + 配置权限快照（**不得在报告中出现真实值**） |
| **R5** | 应用进程**非 root 运行**（systemd `User=www`；nohup 分支降权；宝塔以非 root 托管） | SEC-AUTH-08 | `ps -o user= -p <pid>` 为非 root 的证据 |
| **R6** | **首登强制改密服务端强制**（`pwd_changed=0` 除改密/登出/me 外一律拒），或书面登记为"已知接受风险"并给缓解（缩短初始口令有效期/首登后限时） | SEC-AUTH-05 | 拦截生效证据 或 风险接受记录（由主智能体按 §9 升级用户裁定） |
| **R7** | MySQL/Redis **不对公网开放**、强口令、`8080/3306/6379` 仅本机；Nginx 禁止 `*.map` | SEC-AUTH-10 | 安全组/防火墙规则 + Nginx 规则快照 |
| **R8** | 依赖漏洞**扫描报告**（OWASP Dependency-Check/Trivy）无未处置的严重项 | SEC-AUTH-09 | 扫描报告（不凭记忆断言 CVE） |
| **R9** | 拟改造新增的**任一公开端点**（短信 send/login、高德代理）在公网暴露前**再次过 P0.5 安全评估** | SEC 新面 | 评估结论 + 授权表单 |

**明确结论**：**R1、R2 为「硬阻断项」——未闭环则不得将后端置于公网可达状态。**

---

## 6. 未审计到 / 需运行时验证的项（收敛到服务器阶段）

> 本机**无 JDK / Maven / Redis / Docker**，以下项**未运行**，不得视为"已确认合规"。

| # | 待验证项 | 验证方法 | 收敛阶段 |
| --- | --- | --- | --- |
| V1 | 编译与单测（含 `ResourceAccessCheckerTest`、`DesensitizeUtilTest` 等安全相关用例） | `mvn -B clean test` | 服务器/有 JDK 环境 |
| V2 | 端到端鉴权链：登录→并发互踢→登出→禁用/重置密码强制下线，均返回 401 | 接口实测（勿在生产库做破坏性数据操作） | 联调环境 |
| V3 | JWT `alg=none`/篡改签名/过期 token 一律 401 | 构造样例 token 断言 | 联调环境 |
| V4 | Redis 实际绑定（仅 127.0.0.1）、鉴权与口令强度、会话 key 实际内容与 TTL（`jwt.expire` 实际值） | `redis-cli` 只读检查（不得读取真实凭据明文） | 服务器 |
| V5 | Nginx **实际生效**配置：是否仅 80、是否已加 HSTS/`.map` 拒绝、`/api` 是否限制外网 | `nginx -T` 快照核对 | 服务器 |
| V6 | Spring Boot 错误页配置（`server.error.include-stacktrace/include-message`）与是否存在 actuator | 配置核对 + 探测 `/actuator/*`（pom 未见 actuator，需确认） | 服务器 |
| V7 | 生产前端构建产物是否含 `.map` | 检查 `dist/` 产物 | 构建环境 |
| V8 | 依赖漏洞扫描报告 | Dependency-Check / Trivy | 构建/服务器 |
| V9 | `jwt.secret` 实际**熵**（配置仅校验 ≥32 字节，不保证随机性） | 只读核对来源与生成方式（**不得读取明文**） | 服务器 |
| V10 | 考勤经纬度留存与访问控制现状（是否被越权读取） | 端点越权实测 + 数据留存策略核对 | 联调/服务器 |
| V11 | `auto-dispatch` 公网可达性实测（Nginx 放行范围） | 外网探测（**仅静态推证或授权后内网验证，不得在生产做写入验证**） | 服务器（授权后） |

---

## 7. 交付边界与升级事项

1. **本报告仅出技术结论与修复建议**，**未修改任何代码/配置/文档**（仅新增本报告文件）。发现的漏洞交对应实现角色修复，修复后由本角色复验。
2. **升级主智能体的事项**：
   - SEC-AUTH-01/02 为**硬阻断项**，建议在主智能体完成 R1/R2 闭环前**不给出公网放行结论**；
   - SEC-AUTH-05（首登改密服务端未强制）与 SEC-AUTH-08（root 运行回退分支）涉及红线口径，若暂不修复，须由主智能体按 §9 升级**用户裁定**并留档；
   - 拟改造方案的设备信任（§4.2）与高德定位（§4.6）涉及**设计缺陷与新出网依赖**，应在架构方案定稿前纳入评审，不得在实现后补评估。
3. **复验触发点**：实现角色提交修复后，由本角色按各发现项的「验收标准」复验并出复验结论，方可视为闭环。

---

> 附录：本次审计的静态证据文件清单（绝对路径）
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\common\PublicEndpoints.java`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\filter\JwtAuthFilter.java`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\util\{JwtUtil,SessionUtil,IpUtil,ResponseWriter,DesensitizeUtil,UserContext}.java`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\config\{WebConfig,RequireRolesInterceptor,QueryDataScopeInterceptor,RedisConfig,PasswordConfig}.java`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\{aspect\DataScopeAspect.java,service\support\ResourceAccessChecker.java,enums\DataScopePolicy.java}`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\{service\auth\impl\AuthServiceImpl.java,controller\auth\AuthController.java,controller\workorder\WorkOrderController.java,service\workorder\impl\WorkOrderServiceImpl.java,handler\GlobalExceptionHandler.java}`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\resources\application.yml`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\deploy\{nginx.conf.example,hrm-server.service,deploy.sh}`
> - `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-admin\{vite.config.js,src\stores\auth.js,src\utils\request.js}`
