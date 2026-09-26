# 仓库级（静态）安全审计报告 · 全量复核

> 文档性质：**安全技术结论**（网络安全工程师 `express-station-security-engineer` 产出，非授权结论）
> 审计范围：`hrm-dev/` 全量只读审计（`hrm-server`、`deploy`、`hrm-clients`、`hrm-demo`、`hrm-admin`、`sql`、`collector`）+ 仓库卫生
> 审计方式：**纯静态源码/配置只读审计**。**本机无 JDK / Maven / MySQL / Redis / Docker（无服务器访问），动态验证未做，全部收敛到静态审计**；一切「线上实际行为」按源码/配置推断 + 待实测处理，不假定。
> 权限档位：A 档（只读检索 + 新建本报告）。**未修改任何既有文件、未调用 MCP、未执行 git 操作、未读取任何凭据明文。**
> 报告内**不含真实凭据明文**；涉及真实域名 / IP 一律以「值按纪律不落本文」表述。
> 日期：2026-09-26｜分支：`feature/前端演示项目拆分与精细化`
> 上游依据：`security-auth-review.md`、`security-registration-review.md`、`security-client-admission-review.md`、`security-release-switch-review.md`、`security-structure-migration-review.md`、`security-wifi-checkin-bypass-review.md`

---

## 1. 结论摘要（先行）

**总体判定：后端鉴权主线（JWT 验签链、Redis 会话比对、角色门槛 fail-closed、逐端点归属校验）设计方向正确、未发现可直接利用的越权/绕过/注入漏洞；但「部署与外部暴露面」仍是主要风险聚集区。**

- **未发现**：SQL 注入（Mapper 无 `${}` 拼接、排序用 Lambda 列、`.last()` 均为常量）、路径穿越（导入/导出均流式，无磁盘路径入参）、前端 XSS sink（无 `v-html`/`eval` 实际使用）、Mock 误入生产（`=== 'true'` 显式开关 + 动态 import）、凭据明文入库（占位符齐全）。
- **仍存在（含历史未闭环项）**：1 项严重（`auto-dispatch` 未认证公开写端点，代码层仍公开放行）、7 项高、6 项中、5 项低。
- **主要风险集中在**：① 未认证公开写端点；② 登录无失败锁定；③ 生产 Nginx 未强制 HTTPS / 无安全头 / 无 `.map` 拦截（样例）；④ 后端默认监听 `0.0.0.0`；⑤ nohup 回退以 root 运行；⑥ 考勤 WiFi/定位校验信任客户端自报（系统性上限，历史已升级用户裁定）。

> **本报告仅出技术结论与修复建议**；发现的漏洞交对应实现角色修复，修复后由本角色复验；**不构成放行决定**，C 档授权由主智能体按 `项目规则1.md` §10.3 行使。

---

## 2. 审计范围（含未覆盖部分）

| 范围 | 覆盖情况 |
| --- | --- |
| `hrm-server/src/main/java/com/qiujie/**` | 已覆盖：认证/会话（`util/*`、`filter/JwtAuthFilter`、`config/RequireRolesInterceptor`、`config/WebConfig`、`service/auth/**`）、授权与越权（`@RequireRoles`、`ResourceAccessChecker`、各域 `*AccessPolicy`）、公开端点白名单、注入面（全量 Mapper/注解/`.last()`）、上传导入导出（EasyExcel、CSV、文件下载头）、异常返回、日志脱敏、短信/图形码/设备信任、数据范围收敛 |
| `hrm-server/src/main/resources/**` | 已覆盖：`application.yml`、Flyway V1–V15；**未逐行审计全部迁移脚本业务字段**（结构类非安全面） |
| `hrm-server/pom.xml` | 已覆盖依赖与版本（CVE 见 §5、§7） |
| `hrm-dev/deploy/**` | 已覆盖：`docker-demo/{docker-compose.yml,Dockerfile,nginx.conf,.env.example}`、`deploy.sh`、`hrm-server.service`、`nginx.conf.example`、`scripts/zz-kdyzgl-storage.cnf`、`scripts/hids-log-rotate.*` |
| `hrm-dev/hrm-clients/**`、`hrm-demo/**`、`hrm-admin/**` | 已覆盖：token 存储、请求封装、XSS sink、sourcemap、Mock 开关、演示口令、`.env*` 内容 |
| `hrm-dev/sql/**` | 已覆盖：种子脚本凭据形态（未逐行核 64 行数据） |
| `hrm-dev/collector/**` | 仅覆盖 `scripts/env-setup.ps1` 凭据生成方式（该端 P0 未开工） |
| 仓库卫生 | 已覆盖：`.gitignore`、`env.example`、凭据型模式检索 |

**未覆盖 / 明确未做**

1. **动态验证全部未做**：未编译、未运行、未跑单测/E2E、未做渗透/抓包/现网 curl；本机无运行环境与服务器访问。
2. **生产现网实际配置未核**：`nginx -T` 快照、`.map` 现网是否已被 403、443/证书现状、UFW/安全组规则、Redis 实际绑定与口令强度、`jps/ps` 实际进程属主 —— 均属**服务器阶段待实测项**（§7）。
3. **bcrypt 散列明文未验证**：本机无 `bcryptjs`（已实测 node 存在但包缺失），**无法回算** `V2__init_data.sql` 中 admin 散列对应的明文口令（§3 SEC-FULL-10 标「未验证」）。
4. **未做 git 历史扫描**（按任务约束，仅审工作区当前内容）。
5. **依赖 CVE 未断言**：无扫描器，仅标注版本与「需查 NVD 复核」。

---

## 3. 发现清单（按严重级排序）

> 统一字段：`编号 | 标题 | 严重级 | 位置 | 证据 | 可利用性 | 影响面 | 复现/验证方法 | 修复建议 | 验收标准`
> 可利用性取「可利用 / 理论 / 未验证」。所有复现步骤均为**静态可核验**，**勿在生产执行**。

### 严重

#### SEC-FULL-01 · `POST /api/v1/work-orders/auto-dispatch` 未认证公开写端点（代码层仍开放）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **严重** |
| 位置 | `hrm-dev/hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java:26,45-47`；`.../controller/workorder/WorkOrderController.java:89-92`；`.../service/workorder/impl/WorkOrderServiceImpl.java:258-322` |
| 证据 | `PublicEndpoints.java:45-47`：<br>`private static final Set<String> PATHS = Set.of(`<br>`        AUTH_LOGIN, WORK_ORDER_AUTO_DISPATCH,`<br>`        AUTH_SMS_SEND, AUTH_SMS_LOGIN, AUTH_DEVICE_VERIFY, AUTH_CAPTCHA);`<br>`WorkOrderController.java:89-92`：<br>`@PostMapping("/auto-dispatch")`<br>`public Result<WorkOrderDetailVO> autoDispatch(@RequestBody AutoDispatchRequest request) {`<br>`    return Result.ok(workOrderService.autoDispatch(request));`<br>`}`<br>（`WorkOrderServiceImpl.java:264-267`：`stationId` 缺省回落 `DEFAULT_STATION_ID`，仅校验「驿站存在」） |
| 可利用性 | **可利用**（可远程、无需任何凭据） |
| 影响面 | 任意网络可达者可**无凭据建单**（写 `work_order` + `work_order_timeline`）、触发被指派员工系统通知（通知轰炸）、污染 SLA/负载统计；`stationId` 可跨驿站构造；返回 `WorkOrderDetailVO` 泄露工单内容 |
| 复现/验证方法 | ① 确认路径在白名单（`PublicEndpoints.isPublic` 精确匹配）→ ② 确认方法未声明 `@RequireRoles` → ③ `RequireRolesInterceptor` 因白名单直接放行 → ④ 静态结论：`curl -X POST .../auto-dispatch -d '{"content":"x","stationId":1}'` 无需凭据即可建单（**仅静态推证，勿在生产执行**） |
| 修复建议 | 交后端/架构实现：① 接入企微回调**签名验签与解密**（`msg_signature`/`timestamp`/`nonce` + `EncodingAESKey`），验签失败 401；② 以「配置开关 + 默认关闭」替代裸白名单（未配置验签密钥 → 该端点 404/403，fail-closed）；③ 过渡期在 Nginx 层默认拒绝公网 + `limit_req`；④ 记录来源标识替代 `reporter_id=null` |
| 验收标准 | 未带有效签名的请求 → 非 200 业务成功；未配置密钥的部署 → 404/403；合法签名回调 → 建单成功；非法签名/重放时间戳被拒 |

> 说明：`SESSION-STATE.md:229` 记载生产 `courier-nginx` 已对 `/hrm-api/v1/work-orders/auto-dispatch` **显式 403**（R1 硬要求）——**该现网缓解未由本报告实测核验**，且**不改代码层公开性**；换环境/漏配即失效。

---

### 高

#### SEC-FULL-02 · 登录接口无失败锁定 / 无频控 / 无验证码

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高** |
| 位置 | `.../service/auth/impl/AuthServiceImpl.java:117-142`；`.../dto/auth/LoginRequest.java` |
| 证据 | `AuthServiceImpl.java:123-130`：<br>`if (employee == null) { recordLoginLog(...); throw new BusinessException(ErrorCode.LOGIN_FAILED); }`<br>`if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {`<br>`    recordLoginLog(...); throw new BusinessException(ErrorCode.LOGIN_FAILED);`<br>`}`<br>（全仓检索无 `lock/locked/failCount/loginFail` 于登录路径；失败仅写日志后立即返回） |
| 可利用性 | **可利用**（可远程、无需凭据） |
| 影响面 | 在线口令爆破。初始口令为**全局共享**的 `hrm.employee-init-password`（`application.yml:79` 占位），一旦该值弱/泄露，可对全量员工账号批量尝试 |
| 复现/验证方法 | 通读 `login` 分支：失败仅 `recordLoginLog` 后抛 1001，无计数/延迟/锁定/验证码；对比短信路径有 `isSendBlocked` 五维限频（`SmsCodeStore.java:93-115`）而密码路径没有 |
| 修复建议 | 交后端/运维：① Nginx 对 `/auth/login` 加 `limit_req`（按 `$binary_remote_addr`）；② 应用层按「账号 + IP」失败计数与指数退避/锁定；③ 高失败率触发图形验证码 |
| 验收标准 | 阈值配置存在且触发限流返回 429/等价码；连续 N 次错误 → 锁定/退避；配置与响应证据可核 |

#### SEC-FULL-03 · 生产 Nginx 样例未强制 HTTPS、无 HSTS/安全响应头

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高** |
| 位置 | `hrm-dev/deploy/nginx.conf.example:22-24,80-94`；`hrm-dev/deploy/docker-demo/nginx.conf` |
| 证据 | `nginx.conf.example:23-24`：<br>`listen 80;`<br>`listen [::]:80;`<br>`nginx.conf.example:80-94`：HTTPS 段**整体注释为「可选」**，且全文无 `Strict-Transport-Security` / `X-Content-Type-Options` / `Referrer-Policy`；全 `deploy/` 检索 `limit_req`/`add_header`（安全头）= 0 |
| 可利用性 | **理论**（需本地/中间人：公共 Wi-Fi、同网段、恶意出口） |
| 影响面 | 登录口令、`Authorization: Bearer <token>`、JWT（3 天有效）在公网明文传输 → **账户接管与会话劫持**；前端 token 落 `localStorage`（`api-client/src/authStorage.js:29-33`），一旦截获长期可用 |
| 复现/验证方法 | 阅读样例 → 确认唯一 `listen` 为 80、无 443 块、无 HSTS 与安全头；`docker-demo/nginx.conf` 同样无安全头（有 `server_tokens off`） |
| 修复建议 | 交运维（C 档授权）：全站 HTTPS（443 + TLS1.2/1.3）+ 80→301 跳转 + HSTS（`max-age≥31536000; includeSubDomains`）；新 location 内补 `X-Content-Type-Options: nosniff`、`Referrer-Policy` |
| 验收标准 | `curl -I https://<占位>/` 返回 200 且含 HSTS/nosniff；`curl -I http://<占位>/` 返回 301/308；证书非自签 |

#### SEC-FULL-04 · 生产前端 `*.map` 可被直接下载（源码样例无 `.map` 拦截）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高**（命中项目规则 §10.4「生产 `*.map` 不得公开」红线） |
| 位置 | `hrm-clients/apps/web/vite.config.js:187`、`apps/staff-h5/vite.config.js:73`、`apps/boss-h5/vite.config.js:74`；`deploy/nginx.conf.example:44-49` |
| 证据 | 三端：`sourcemap: mode === 'production' ? 'hidden' : false`（生成 `.map`，仅不写 `sourceMappingURL`）<br>`nginx.conf.example:45-49`：<br>`location /assets/ {`<br>`    expires 30d;`<br>`    add_header Cache-Control "public, immutable";`<br>`    try_files $uri =404;`<br>`}`<br>（**全文无 `\.map$` 拒绝**；`.map` 与 `.js` 同目录同名，可被直接猜中下载） |
| 可利用性 | **理论**（需先获知带哈希的 chunk 名；但 hidden map 下 `.map` 与 `.js` 一一对应，可枚举/由前端报错路径泄露） |
| 影响面 | 源码结构/内部实现**完整还原**（历史实测三端全部 `.map` 含 `sourcesContent`），加剧漏洞挖掘与凭据/逻辑推断 |
| 复现/验证方法 | ① 确认三端生产构建产出 `.map`；② 确认 `nginx.conf.example` 无 `.map` 拒绝；③ 现网核实（§7）：`curl -o /dev/null -w '%{http_code}' https://<占位>/web/assets/<真实名>.js.map` 是否 403/404 |
| 修复建议 | 交运维/前端：① Nginx 三端站点内各加 `location ~* \.map$ { return 404; }`（先于通用扩展名规则）；② 或构建侧 `sourcemap:false`。**注**：`docker-demo/nginx.conf:55-57` 已有正确先例，可照搬 |
| 验收标准 | 对三端各取真实 `.js` 名换 `.map` 探测 → 403/404（非 200）；构建产物 `dist/**/*.map` 计数 = 0 或全部不可达 |

#### SEC-FULL-05 · `nohup` 回退分支以 root 运行后端（违反 D 档红线）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高** |
| 位置 | `hrm-dev/deploy/deploy.sh:236-246,269-279`；`hrm-dev/deploy/hrm-server.service:38-39` |
| 证据 | `deploy.sh:242`：`RESTART_MODE="nohup"`（`auto` 模式下无 systemd 单元即回退）<br>`deploy.sh:273-276`：<br>`nohup "$JAVA_BIN" $JAVA_OPTS -jar "$RUNTIME_JAR" \`<br>`  --spring.profiles.active=prod \`<br>`  --spring.config.additional-location="file:$CONFIG_DIR/" \`<br>`  >> "$LOG_DIR/stdout.log" 2>&1 &`<br>（脚本使用说明自述「**在服务器上执行，root 身份**」，回退分支**未降权**） |
| 可利用性 | **理论**（需本地/部署条件；进程被攻破即获 root） |
| 影响面 | 违反项目规则 §10.4「不得以 root 运行应用进程」；扩大容器/主机提权面 |
| 复现/验证方法 | 阅读 `deploy.sh`：`nohup` 分支无 `runuser -u www --` / `setpriv` 降权；对照 `hrm-server.service:38-39` `User=www` 正确 |
| 修复建议 | 交运维/后端：`nohup` 分支显式降权（`runuser -u www --` 或 `setpriv --reuid`），或文档强制「生产必须 systemd/宝塔非 root 托管」并纳入放行条件 |
| 验收标准 | 服务器 `ps -o user= -p <pid>` 为非 root；或在放行记录中固化「禁用 nohup 模式」的书面口径 |

#### SEC-FULL-06 · 后端默认监听 `0.0.0.0`（无回环绑定），与文档口径不符

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高** |
| 位置 | `hrm-server/src/main/resources/application.yml:13-16`；`deploy/nginx.conf.example:14-15` |
| 证据 | `application.yml:13-16`：<br>`server:`<br>`  port: 8080`<br>`  servlet:`<br>`    context-path: /`<br>（**无 `server.address`** → Spring Boot 默认绑定 `0.0.0.0`）<br>对照 `nginx.conf.example:14-15` 注释：「**后端只在回环地址监听，8080 不对公网开放**」 |
| 可利用性 | **理论**（需本地/部署条件：防火墙/安全组漏配即直连暴露，且**绕过 Nginx 的 TLS/限流**） |
| 影响面 | 一旦 UFW/安全组放行 8080/8081，后端**直接公网可达**，HTTPS 与边缘限流全部失效 |
| 复现/验证方法 | 读 `application.yml` 确认无 `server.address`；`SESSION-STATE.md:230` 亦记「监听地址 `127.0.0.1` → `0.0.0.0`」（本轮已放宽），依赖 UFW 定向放行 8081 |
| 修复建议 | 交后端/运维：显式 `server.address=127.0.0.1`（若容器反代需宿主地址，则用明确单地址而非全接口）；并在文档/验收中固化「8080/8081 不得对公网」 |
| 验收标准 | 配置含 `server.address`（非 `0.0.0.0`）或书面登记「接口暴露范围仅宿主/容器网段」并有防火墙规则快照佐证 |

#### SEC-FULL-07 · 考勤 WiFi/定位校验完全信任客户端自报（系统性上限，A 档修复后残余）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高**（作为系统性结论；历史已升级用户裁定） |
| 位置 | `hrm-server/.../attendance/support/AttendanceCheckPolicy.java:130-133`；`hrm-clients/apps/{staff-h5,boss-h5}/src/composables/useCheckIn.js:19-23` |
| 证据 | 服务端：`List<String> wifiSsids` 判 `contains(reportedWifiSsid)`（`wifiSsid`/`longitude`/`latitude` 均为请求体字段）<br>客户端（现状已按 A 档修复）：<br>`function resolveWifiSsid(wifi, rule) {`<br>`  if (wifi.mock === false && wifi.ssid) return wifi.ssid`<br>`  if (!DEMO_ENABLED) return null`<br>`  return rule && rule.wifiList && rule.wifiList.length ? String(rule.wifiList[0].ssid) : null`<br>`}` |
| 可利用性 | **可利用**（持有效员工账号者：直连 API 填白名单 SSID + 围栏中心坐标即通过） |
| 影响面 | 考勤真实性；`wifi_matched=true` 记录失真；下游薪酬/考核口径 |
| 复现/验证方法 | 静态：`POST /attendance/check-in` 的 `wifiSsid`/坐标由客户端构造，服务端无来源可信性校验；`GET /attendance/rule` 对 STAFF 返回 `wifiList`（攻击者无需猜测） |
| 修复建议 | 交架构/后端（C 档方向）：只有「服务端独立可信信道」才能根治 —— 企业微信/网关侧出口 IP 白名单、一次性现场签到码、802.1X/NAC。**根源在服务端信任客户端自报，不是前端 bug** |
| 验收标准 | 服务端对打卡来源具备独立校验（如网关出口 IP / 动态码），伪造 `wifiSsid`/坐标不再能通过；在此之前**不得**把 `wifi_matched=true` 作为考勤合规独立证据 |

> **与历史差异**：历史 `security-wifi-checkin-bypass-review.md` 的 **F-01 / F-02 已修复**（A 档：`resolveWifiSsid` 生产态不回填、`演示辅助`开关由 `VITE_MOCK_ENABLED==='true'` 编译期门控，见 `staff-h5/views/staff/attendance.vue:18-19,95`）；**F-05（本质上限）仍存在**，维持「高」，已在历史报告中升级用户裁定（U2）。

#### SEC-FULL-08 · `/auth/sms/send` 可枚举「手机号是否为在职员工」

| 字段 | 内容 |
| --- | --- |
| 严重级 | **高** |
| 位置 | `.../service/auth/impl/AuthServiceImpl.java:259-279`；`.../common/PublicEndpoints.java:31` |
| 证据 | `AuthServiceImpl.java:264-269`：<br>`Employee employee = employeeMapper.selectOne(`<br>`        new LambdaQueryWrapper<Employee>().eq(Employee::getPhone, phone));`<br>`// 账号不存在与未绑手机号统一 1109，避免以不同码暴露手机号是否已注册（防枚举）`<br>`if (employee == null) {`<br>`    throw new BusinessException(ErrorCode.PHONE_NOT_BOUND);`<br>`}`<br>（账号存在且启用 → 走发码返回 200；**响应码差异即存在性差异**） |
| 可利用性 | **可利用**（可远程、无需凭据） |
| 影响面 | 以号段批量探测，产出「本系统员工手机号」名单（PII），供撞库/钓鱼/精准社工。IP 维度限频（20/小时）仅降低速率、不消除枚举 |
| 复现/验证方法 | 静态：对「已注册/未注册」手机号各调用 `POST /auth/sms/send` → 前者 200（发码）、后者 1109；响应体 `{code,message,data}` 逐字段可区分 |
| 修复建议 | 交后端：无论手机号是否已注册，发码接口**返回体/状态码/错误码/耗时量级一致**（对未注册号作「静默成功」或统一通用文案），阻断存在性差异 |
| 验收标准 | 对两组各 ≥50 次，响应 `{code,message,data}` 与 HTTP 状态**逐字段一致**；不得出现 1109 差异 |

---

### 中

#### SEC-FULL-09 · 首登强制改密仅前端实现，服务端未强制

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** | 
| 位置 | `.../service/auth/impl/AuthServiceImpl.java:205,734-735`；全仓 `pwdChanged` 检索 |
| 证据 | `AuthServiceImpl.java:734-735`：<br>`// pwd_changed=0 时前端强制进入改密流程（requirement.md 5.3）`<br>`vo.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);`<br>（`pwdChanged` 仅作出参；过滤器/拦截器无任何判定） |
| 可利用性 | **可利用**（以初始口令登录后，可携带 token 直接调用全部业务接口，不触发改密） |
| 影响面 | 配合全局共享初始口令 `hrm.employee-init-password`，未改密账号等同「默认凭据可用的真实账号」 |
| 复现/验证方法 | 检索 `pwdChanged` 全部使用点：仅赋值出参，无服务端拦截；`JwtAuthFilter` 亦不判定 |
| 修复建议 | 交后端：`JwtAuthFilter` 或专用拦截器对 `pwd_changed=0` 会话，除 `/auth/me`、`/auth/password`、`/auth/logout` 外一律 403（业务码如 1005「须先修改初始密码」） |
| 验收标准 | `pwd_changed=0` 会话调用业务接口 → 403 且提示改密；仅放行改密/登出/me 三个端点 |

#### SEC-FULL-10 · 生产种子 admin 为固定 BCrypt 散列 + `pwd_changed=0`（明文未验证）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** |
| 位置 | `hrm-server/src/main/resources/db/migration/mysql/V2__init_data.sql:20-21` |
| 证据 | ``INSERT INTO employee (id, username, password, ..., role, status, pwd_changed)``<br>``VALUES (1, 'admin', '$2a$10$KzIKolEcoKaIeYkyJ7jr8e1GOMrz8wHaw9pppT6a42F5TU1dMGBSu', '系统管理员', '13800000000', 0, 1, NULL, 'ADMIN', 1, 0);``<br>（注释称「对应 db.md 5.2 文档化的部署占位初始密码」） |
| 可利用性 | **未验证**（本机无 `bcryptjs`，**无法回算散列明文**，故不判定该口令是否等于文档占位值） |
| 影响面 | 若部署未替换该口令且 SEC-FULL-09 未闭环 → admin 级默认凭据可用（最高权限） |
| 复现/验证方法 | ① 静态确认 `V2` 固定散列 + `pwd_changed=0`；② **待复核**（§7 OQ-1）：以部署文档占位口令回算/比对散列；确认生产库 `admin.password` 是否已替换 |
| 修复建议 | 交运维/数据库（C 档）：部署流程强制「首登改密」并把「生产 admin 口令非占位」纳入放行条件；文档口径 `db.md:390-391` 已声明「真实密码只存服务器侧，绝不写入 Git」 |
| 验收标准 | 生产库 `admin.password` 非种子散列；或首登强制改密（SEC-FULL-09）闭环；二者其一 |

#### SEC-FULL-11 · 测试种子/文档含明文演示口令与固定短信码（非生产）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** |
| 位置 | `hrm-dev/docs/demo-accounts.md:8,14,141-146`；`hrm-dev/sql/seed/kdyzgl_test_seed.sql:92+`；`application.yml:150` |
| 证据 | `demo-accounts.md:141`：「密码通道：用户名 + 口令 `demo1234`（所有 64 个账号通用）」<br>`demo-accounts.md:143`：「测试环境固定短信验证码为 `000000`」<br>`application.yml:150`：`dev-fixed-code: "000000"`（`provider: none` 时生效；生产必须 `aliyun` 否则 fail-fast） |
| 可利用性 | **理论**（仅测试库 `kdyzgl_test`；生产 fail-fast 保证 `dev-fixed-code` 不生效） |
| 影响面 | 演示口令/固定码为**长期公开**的通用凭据；若误用于可达环境或与业务库混用即账户接管。属文档/种子**既有约定**（`demo-design.md:104` 声明「只存在于 Mock/演示，非任何环境真实凭据」） |
| 复现/验证方法 | 静态：文档与种子明文可读；`SmsConfigGuard` 保证生产未配有效凭据即启动失败（`SmsConfigGuard.java:42-74`） |
| 修复建议 | 交后端/运维：① 确保 `kdyzgl_test` 与生产库/网络隔离；② 生产 `provider=aliyun` 且 `dev-fixed-code`/`dev-universal-code` 不生效（已有 fail-fast，需实测确认）；③ 建议文档不再罗列明文口令（改为「按部署约定」） |
| 验收标准 | 生产 profile 启动日志无降级通道；`dev-universal-code` 为空；演示站与生产库网络隔离 |

#### SEC-FULL-12 · 客户端 IP 取 `X-Forwarded-For` 首个值，可被伪造

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** |
| 位置 | `.../util/IpUtil.java:17-31`；`deploy/nginx.conf.example:64`；`SmsCodeStore.java:102-106` |
| 证据 | `IpUtil.java:19-24`：<br>`String forwarded = request.getHeader("X-Forwarded-For");`<br>`if (forwarded != null && !forwarded.isBlank()) {`<br>`    int commaIndex = forwarded.indexOf(',');`<br>`    String ip = (commaIndex > 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();`<br>`    if (!ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) { return ip; }`<br>`}`<br>Nginx：`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`（**追加**客户端自带值） |
| 可利用性 | **可利用**（构造请求头） |
| 影响面 | 登录日志/审计失真；短信「IP 维度」限频（`LIMIT_IP_PREFIX + ip`）被伪造绕过 → 放大短信刷量能力 |
| 复现/验证方法 | 客户端发 `X-Forwarded-For: 1.2.3.4` → Nginx 转发为 `1.2.3.4, <真实IP>` → `IpUtil` 取到 `1.2.3.4` |
| 修复建议 | 交后端/运维：优先 `X-Real-IP`（由 `$remote_addr` 覆写，不可伪造）或取 XFF **最后**一段；Nginx 改为**覆写** XFF；`IpUtil` 加 IP 格式校验，非法即回退 `remoteAddr` |
| 验收标准 | 伪造 XFF 不改变限速归属与登录日志 IP；非法 IP 串被忽略 |

#### SEC-FULL-13 · 宿主机 MySQL 绑定 docker0 / courier-net 网段地址

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** |
| 位置 | `hrm-dev/deploy/scripts/zz-kdyzgl-storage.cnf:13-15` |
| 证据 | `bind-address = 127.0.0.1,172.17.0.1,172.19.0.1`<br>（注释：172.17.0.1=docker0，172.19.0.1=courier-net，供容器经 `host.docker.internal` 访问） |
| 可利用性 | **理论**（需能起容器/接入 docker 网段；或同网段主机） |
| 影响面 | **任意 docker0 网段容器**与 courier-net 容器可直达 MySQL（3307），攻击面由「本机回环」扩为「容器网段」；风险随后续 MySQL 账号口令强度与 `host` 授权范围而定 |
| 复现/验证方法 | 阅读 cnf：`bind-address` 含两个 docker 网关地址 |
| 修复建议 | 交运维（C 档）：评估是否可由 `127.0.0.1` + 容器 `network_mode: host` 或 socket 替代；至少确认 MySQL 账号 `Host` 精确限定、口令强随机、UFW 定向放行最小网段 |
| 验收标准 | 非本机/非授权容器的 `3307` 连接被拒；MySQL 用户授权 `host` 为最小集合的规则快照 |

#### SEC-FULL-14 · 日志凭据擦除正则未覆盖验证码/图形码类键名

| 字段 | 内容 |
| --- | --- |
| 严重级 | **中** |
| 位置 | `.../service/support/ClientLogSanitizer.java:47-49` |
| 证据 | `private static final Pattern CREDENTIAL = Pattern.compile(`<br>`        "(token=|accessToken=|refreshToken=|password=|pwd=)([^\\s&#,;)]+)", Pattern.CASE_INSENSITIVE);`<br>（未覆盖 `code=`/`smsCode=`/`verifyCode=`/`captcha=` 等形态；白名单复制虽已挡字段级凭据，但 `message`/`stack` 文本内的验证码不会被擦） |
| 可利用性 | **理论**（需验证码明文出现在前端上报的 `message`/`stack` 中） |
| 影响面 | 若前端异常/埋点把验证码写入文案，会随运行日志入库（`client_log`）——违反「验证码绝不入日志」红线 |
| 复现/验证方法 | 阅读正则；构造 `message="code=123456"` → 不被擦除（可单测复现） |
| 修复建议 | 交后端：正则补 `code=|smsCode=|verifyCode=|captcha=|otp=` 形态（注意避免误擦 `code=200` 业务码：按长度/上下文收敛） |
| 验收标准 | 单测：含 `code=123456`/`verifyCode=...` 的 `message` 被擦；业务码 `code=200` 不被误擦 |

---

### 低

#### SEC-FULL-15 · 图形验证码返回固定占位图且默认关闭（不可作缓解）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `.../service/auth/impl/AuthServiceImpl.java:88-89`；`.../service/auth/support/CaptchaStore.java:18-19`；`application.yml:104` |
| 证据 | `AuthServiceImpl.java:88-89`：`CAPTCHA_PLACEHOLDER_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB..."`（1×1 透明 PNG）<br>`application.yml:104`：`captcha-enabled: false`<br>`CaptchaStore.java:18-19`：`TODO(扩展): 当前返回固定占位图…` |
| 可利用性 | **理论** | 
| 影响面 | 真实渲染落地前**不能**把图形码计入任何安全缓解；当前默认关闭，登录/发码无有效人机校验 |
| 修复建议 | 交后端/前端：接入真实图形码渲染；生产启用后再计入缓解 |
| 验收标准 | 生产 `captcha-enabled=true` 且返回真实图形；无有效 ticket → 1106 |

#### SEC-FULL-16 · `ResponseWriter` 手拼 JSON（当前无注入，需防腐化）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `.../util/ResponseWriter.java:25-30` |
| 证据 | `response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");`<br>（当前 `message` 均为 `ErrorCode` 固定串，无可利用注入） |
| 可利用性 | **理论**（仅当后续向 `message` 注入用户可控内容） |
| 影响面 | 若引入动态内容（如反射 URL），手拼 JSON 存在转义缺失隐患 |
| 修复建议 | 交后端：改用 Jackson 序列化；禁止向手工 JSON 注入用户可控内容 |
| 验收标准 | 全仓无手工 JSON 拼接（或存在转义/序列化封装） |

#### SEC-FULL-17 · JWT 无 `iss`/`aud`/`kid`，无密钥轮换机制

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `.../util/JwtUtil.java:72-83,90-98` |
| 证据 | `generate(...)` 仅设 `sub/userId/role/jti/iat/exp`，`signWith(key, HS256)`；`parse` 走 `parseClaimsJws`（强制验签，`setSigningKey` + 到期宽限） |
| 可利用性 | **理论** | 
| 影响面 | 自签自用场景 `iss/aud` 缺失风险有限；无 `kid`/密钥版本位 → 密钥轮换需全员重登，疑似泄露时无平滑吊销 |
| 修复建议 | 交后端：引入 `kid` 或密钥版本 claim，支持双密钥并行过渡；按需加 `iss/aud` 固定校验 |
| 验收标准 | 支持双密钥并存过渡；轮换不需全员强制重登 |

#### SEC-FULL-18 · 未知短信场景静默回落 `LOGIN`（fail-open 放大）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `.../service/auth/impl/AuthServiceImpl.java:605-619` |
| 证据 | `log.warn("未知短信场景，按 LOGIN 处理：{}", raw);`<br>`return SmsScene.LOGIN;` |
| 可利用性 | **理论**（当前仅 3 个已知场景，暂无实际影响） |
| 影响面 | 若后续新增场景而漏改 `resolveScene`，未知值会静默走 `LOGIN` 分支（fail-open），可能放大枚举/刷量 |
| 修复建议 | 交后端：未知场景一律**拒绝**而非回落；新增场景同步更新映射并加单测 |
| 验收标准 | 单测：未知 scene → 拒绝（非 `LOGIN`） |

#### SEC-FULL-19 · 404/403 端点口径差异可探测资源存在性（已知并接受）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `.../service/support/ResourceAccessChecker.java:27-44`；各域 `*AccessPolicy` |
| 证据 | `NOT_FOUND` 策略：跨站资源按「不存在」返回（不暴露存在性）；`FORBIDDEN` 策略：无权 → 403，不存在 → 404，**可区分** |
| 可利用性 | **理论** |
| 影响面 | 信息量极小的端点差异；ADR-07 有意为之 |
| 修复建议 | 维持现状（业务契约需要）；确认无高敏感资源依赖 404 隐藏存在性 |
| 验收标准 | 风险登记留档；高敏感资源不得用 404 隐藏存在性 |

#### SEC-FULL-20 · 文档层含真实生产域名 / 服务器 IP / 内网地址（仓库卫生）

| 字段 | 内容 |
| --- | --- |
| 严重级 | **低** |
| 位置 | `SESSION-STATE.md:229,914,1258` 等；`.github/CONTRIBUTING.md`、`.trae/rules/项目规则1.md`、`hrm-dev/docs/{deploy,demo-docker-deploy,multi-client-split-plan,tech-review-structure-migration}.md` |
| 证据 | 生产域名命中 9 个文档、公网服务器 IPv4 命中 2 个文档、内网采集机 IPv4 命中 `SESSION-STATE.md`（**值按纪律不落本文**）；`security-structure-migration-review.md` 已判定「文档层出现公开生产域名**不构成新增暴露面**，风险低（规范类）」 |
| 可利用性 | **理论**（域名/IP 属公开信息，公共 DNS 可解析；不与凭据组合即非攻击链） |
| 影响面 | 违反「凭据/服务器 IP 不进仓库」的**表述纪律**，非实质暴露面；与既有结论一致 |
| 复现/验证方法 | Grep 全仓文档（非源码）命中域名与 IPv4 字面量 |
| 修复建议 | 交主智能体裁定是否另立「全仓文档收敛」任务（历史已列 §4.3-2 建议）；本报告不主张作为阻塞项 |
| 验收标准 | 或收敛为占位；或书面登记「公开服务域名不计入敏感信息」口径 |

---

## 4. 与历史评审结论的差异（已修 / 仍存在 / 新增）

| 历史项 | 历史结论 | 本次复核 | 判定 |
| --- | --- | --- | --- |
| `SEC-AUTH-01`（auto-dispatch 未认证写） | 严重 | 代码层仍公开（`PublicEndpoints.java:46`），未加验签；生产 Nginx 据 `SESSION-STATE.md` 已 403（未实测） | **仍存在**（→ SEC-FULL-01） |
| `SEC-AUTH-02`（HTTPS/HSTS） | 高 | `nginx.conf.example` 仍仅 `listen 80`、HTTPS 段注释为可选、无 HSTS | **仍存在**（→ SEC-FULL-03） |
| `SEC-AUTH-03`（登录无频控/锁定） | 高 | `login` 仍无计数/锁定/验证码 | **仍存在**（→ SEC-FULL-02） |
| `SEC-AUTH-04`（白名单已就位即放行） | 高 | 仍为裸白名单；端点注释保留警示，未加配置开关 | **仍存在**（与 01 同源） |
| `SEC-AUTH-05`（首登改密服务端未强制） | 中 | `pwdChanged` 仍仅出参，无拦截 | **仍存在**（→ SEC-FULL-09） |
| `SEC-AUTH-06`（XFF 可伪造） | 中 | `IpUtil` 仍取 XFF 首个，Nginx 仍追加 | **仍存在**（→ SEC-FULL-12） |
| `SEC-AUTH-08`（nohup root 回退） | 中 | `deploy.sh` nohup 分支仍不降权 | **仍存在**（→ SEC-FULL-05，本报告上调为高） |
| `SEC-AUTH-09`（依赖未扫描） | 中（待证据） | 版本未变，仍无扫描报告 | **仍存在**（§5、§7 OQ-3） |
| `SEC-AUTH-10`（生产 `.map` 未拒绝） | 低 | 三端仍产 hidden `.map`；源码 `nginx.conf.example` 仍无 `.map` 拒绝 | **仍存在并上调为高**（→ SEC-FULL-04） |
| `SEC-AUTH-13`（ResponseWriter 手拼 JSON） | 低 | 未变 | **仍存在**（→ SEC-FULL-16） |
| `security-client-admission-review` §三-①（`hrm-admin` 未上报端 → 1110 拒登） | 中（阻断交付） | [hrm-admin/src/views/login/index.vue:101-107](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-admin/src/views/login/index.vue#L101-L107) 现已显式 `clientType: 'WEB'` | **已修复** |
| 端准入 fail-closed / 单一真源 | 已落实 | `ClientAdmissionPolicy` 单点、缺省即 1110 | **已修复**（维持） |
| `security-wifi-checkin-bypass-review` F-01/F-02 | 高 | [useCheckIn.js:19-23](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/composables/useCheckIn.js#L19-L23) 生产态不回填；`演示辅助`开关由 `VITE_MOCK_ENABLED` 编译期门控（[attendance.vue:18-19,95](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/views/staff/attendance.vue#L18-L19)） | **已修复（A 档）** |
| 同上 F-05（客户端自报本质上限） | 高 | 服务端判定未变 | **仍存在**（→ SEC-FULL-07） |
| `security-registration-review` REG-*（注册链路） | 设计态阻断 | 全仓仍**无注册相关代码/表**；本次新增 **SEC-FULL-08**（现有发码链路可枚举手机号）为**存量**问题，非注册引入 | **维持设计态结论；新增存量枚举项** |
| `security-structure-migration-review`（文档域名/IP） | 低（规范类） | 复核一致 | **仍存在**（→ SEC-FULL-20） |
| 本次**新增**发现 | — | SEC-FULL-06（后端默认 `0.0.0.0`）、SEC-FULL-08（手机号枚举）、SEC-FULL-10（种子 admin 散列）、SEC-FULL-11（明文演示口令/固定码）、SEC-FULL-13（MySQL 绑定 docker 网段）、SEC-FULL-14（日志正则覆盖不足）、SEC-FULL-18（未知场景 fail-open） | **新增** |

---

## 5. 安全基线核对表

| 基线项 | 判定 | 依据 |
| --- | --- | --- |
| **凭据入库** | **通过（占位符齐全）** | `application.yml` 全为 `change_me_*`；`env.example` 全为占位；`.gitignore` 覆盖 `.env*`/`application-*.yml`/`*.pem`/`*.key`/`.secrets/`；全仓凭据型检索**无真实密钥**。**残留（中）**：测试种子/文档明文演示口令与固定码（SEC-FULL-11）；`deploy.sh:315` 探针账号 `deploy_probe`（**非真实凭据**，仅存活探针） |
| **SQL 参数化** | **通过** | Mapper **无 XML 文件**、注解/文本块中 `#{}` 全参数化；全仓 `${` 仅出现在 `@Value` 与注释；排序一律 `LambdaQueryWrapper.orderByXxx(Entity::getX)`；`.last("LIMIT 1")` 为常量（`TrustedDeviceRegistry.java:53,86`、`ClientLogServiceImpl.java:163`）；无原生 `Statement`/`createStatement` |
| **鉴权与越权** | **基本通过（有前提）** | 非公开端点未声明 `@RequireRoles` → **fail-closed 403**（`RequireRolesInterceptor.java:41-50`）；公开端点仅 6 条显式白名单；逐端点归属校验：包裹（`ParcelServiceImpl.java:277-282,294-295`）、工单（`WorkOrderAccessPolicy`）、请假（`LeaveAccessPolicy`）、HR 档案（`HrProfileServiceImpl.java:312-319`）、通知（`NotificationServiceImpl` 以 `UserContext` 收口）、同步任务（`SyncTaskServiceImpl.assertVisible`）均做归属判定。**前提**：`@DataScope` 注解**当前无任何方法使用**（仅切面存在），逐端点归属依赖各 Service 自觉 → 新增端点若无 `@RequireRoles` 会 fail-closed（安全），但若漏做 Service 归属判定且角色门槛放行，则无第二道防线 |
| **日志脱敏** | **基本通过（有缺口）** | `ClientLogItem` **白名单复制**（无 token/password/idCard 字段，天然丢弃）；`ClientLogSanitizer` 凭据正则擦除 + 截断 + path 去 query；出参手机号/姓名/银行卡/IP 用 `DesensitizeUtil` 脱敏（`AuthServiceImpl.me()` 用 `maskPhone`）。**缺口（中）**：`ClientLogSanitizer.java:48-49` 正则未覆盖验证码类键名（SEC-FULL-14）；`LoggingSmsSender` 仅打印脱敏手机号与码长（正向） |
| **默认口令** | **存在风险（中）** | 生产种子 `admin` 为固定 BCrypt 散列 + `pwd_changed=0`（SEC-FULL-10，明文未验证）；首登改密服务端未强制（SEC-FULL-09）；短信固定码/万能码在生产被 `SmsConfigGuard` fail-fast 挡住（正向） |
| **依赖与供应链** | **未扫描（不可凭记忆断言）** | `pom.xml`：Spring Boot 3.3.4、MyBatis-Plus 3.5.7、jjwt 0.11.5、EasyExcel 3.3.4、mysql-connector-j（Boot 托管）、Flyway（Boot 托管）。**本报告不对具体 CVE 作任何断言**；须以 `OWASP Dependency-Check`/`Trivy fs` 扫描报告为准，重点审 EasyExcel/POI 传递依赖（Excel 属不可信输入面，已有 10MB + `.xlsx` 白名单缓解） |

**其余已核实为「合格」的控制**：Mock 仅 `VITE_MOCK_ENABLED === 'true'` + 动态 `import()` 装配（[web/main.js:97-104](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/src/main.js#L97-L104)），生产整块剔除；`.env.production` 三端 `VITE_MOCK_ENABLED=false`；无 `CORS` 配置（同源，[WebConfig.java:9](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-server/src/main/java/com/qiujie/config/WebConfig.java#L9)）；无 `actuator` 依赖（pom 未见）；前端无 `v-html`/`eval`/`innerHTML` 实际使用；导入/导出全流式无路径穿越（`EmployeeServiceImpl.java:621-652`、`setDownloadHeaders:699-704`）；短信 `SecureRandom` + 恒定时间比较 + 一次性（`SmsCodeGenerator`、`SmsCodeStore`）；设备信任令牌 HttpOnly+Secure Cookie、改密即失效。

---

## 6. 修复建议汇总（分档）

**必做（MUST，闭环前不得给出公网放行结论）**

1. `auto-dispatch`：验签/解密落地或配置开关 fail-closed（SEC-FULL-01）。
2. 全站 HTTPS + HSTS + 安全响应头（SEC-FULL-03）；`.map` 拒绝（SEC-FULL-04）。
3. 登录接口边缘 + 应用双层限流与失败锁定（SEC-FULL-02）。
4. 后端进程非 root（SEC-FULL-05）；显式回环/最小地址监听（SEC-FULL-06）。
5. 首登强制改密服务端强制，或书面登记「已知接受风险」并升级用户（SEC-FULL-09 + SEC-FULL-10）。

**建议（SHOULD）**

6. 发码接口响应恒定化，阻断手机号枚举（SEC-FULL-08）。
7. `IpUtil` 取信改造 + Nginx 覆写 XFF（SEC-FULL-12）。
8. 日志正则补验证码形态（SEC-FULL-14）；未知短信场景拒绝（SEC-FULL-18）。
9. MySQL 绑定与授权范围收敛（SEC-FULL-13）；文档域名/IP 收敛（SEC-FULL-20）。

**可选（MAY）**：图形码真实渲染（SEC-FULL-15）；`ResponseWriter` 改 Jackson（SEC-FULL-16）；JWT `kid` 轮换（SEC-FULL-17）。

---

## 7. 未能确认、需复核的开放问题

| ID | 开放问题 | 为何未确认 | 建议复核方式（服务器阶段） |
| --- | --- | --- | --- |
| **OQ-1** | `V2__init_data.sql` 中 admin 散列对应的明文口令是否为文档占位值（弱默认凭据） | 本机无 `bcryptjs`，无法回算 | 离线以 `bcryptjs`/`python-bcrypt` 比对；核对生产库 `admin.password` 是否已替换 |
| **OQ-2** | 现网 `courier-nginx` 是否已对 `/hrm-api/v1/work-orders/auto-dispatch` **实际 403**、是否已加 `.map` 拒绝/HSTS/安全头/`limit_req` | 无服务器访问，仅据 `SESSION-STATE.md` 记载（未采信为结论依据） | `nginx -T` 快照 + `curl -I` 只读断言（**不在生产执行写操作**） |
| **OQ-3** | 依赖是否存在未处置的严重 CVE | 无扫描器，禁止凭记忆断言 | `OWASP Dependency-Check` / `Trivy fs` 报告 |
| **OQ-4** | 生产 `admin`/初始口令实际强度与是否首登已改密；`hrm.employee-init-password` 实际值熵 | 无环境、不得读取凭据明文 | 只读核对配置来源与 `pwd_changed` 分布（**不得读取明文口令**） |
| **OQ-5** | Redis 实际绑定（是否仅 `127.0.0.1`）、口令强度、会话 key TTL 实际值 | 无环境 | `redis-cli` 只读检查（不读凭据明文） |
| **OQ-6** | 应用进程实际属主（systemd vs nohup vs 宝塔）、8081 实际监听地址 | 无环境 | `ps -o user= -p <pid>` + `ss -ltnp` |
| **OQ-7** | 三端生产产物是否实际含 `.map` 及其是否可达 | 未复跑构建、无现网访问 | 检查 `dist/**/*.map` 计数 + 现网探测（OQ-2） |
| **OQ-8** | 未过审/存量数据、`kdyzgl_test` 与生产库的实际隔离与网络可达性 | 无环境 | 运维核查库隔离与网络策略 |

---

## 8. 交付边界与升级事项

1. **本报告仅出技术结论与修复建议 + 验收标准**；仅新建本文件，**未修改任何源码/配置/既有报告**（反模式 A17）。发现的漏洞交对应实现角色修复，修复后由本角色按各条「验收标准」复验。
2. **升级主智能体**：
   - SEC-FULL-01/03/04 涉**外部暴露面与红线**（`.map`、未认证写端点、HTTPS），建议在闭环前**不给出公网放行结论**；SEC-FULL-04 若不能落地/验证，命中 §10.4 D 档红线，**须升级用户裁定**（历史 B7 M1 同源）。
   - SEC-FULL-05（root 运行）、SEC-FULL-09（首登改密）若暂不修复，须由主智能体按 §9 升级**用户裁定**并留档。
   - SEC-FULL-07（客户端自报本质上限）为**产品风险承受度**问题，历史已列 U2，须用户裁定（安全侧只给上限，不主张口径）。
3. **复验触发点**：实现角色提交修复后，由本角色按各发现项「验收标准」复验并出复验结论，方可视为闭环；**未验证项（§7）闭环前不得声称「已确认合规」**。

---

## 9. 附录 · 审计证据文件清单（绝对路径，节选）

- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\common\PublicEndpoints.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\filter\JwtAuthFilter.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\config\{RequireRolesInterceptor,WebConfig,RedisConfig,AuthProperties}.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\util\{JwtUtil,IpUtil,SessionUtil,ResponseWriter,DesensitizeUtil}.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\service\auth\impl\AuthServiceImpl.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\service\support\ClientLogSanitizer.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\service\support\sms\{SmsConfigGuard,SmsCodeGenerator}.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\service\support\ResourceAccessChecker.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\java\com\qiujie\controller\{auth\AuthController,workorder\WorkOrderController}.java`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\resources\application.yml`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\src\main\resources\db\migration\mysql\V2__init_data.sql`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-server\pom.xml`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\deploy\{deploy.sh,nginx.conf.example,hrm-server.service}`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\deploy\docker-demo\{docker-compose.yml,Dockerfile,nginx.conf,.env.example}`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\deploy\scripts\zz-kdyzgl-storage.cnf`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients\apps\{web,staff-h5,boss-h5}\{vite.config.js,.env.production}`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients\packages\api-client\src\{authStorage.js,createHttp.js}`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients\apps\staff-h5\src\composables\useCheckIn.js`
- `d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\sql\seed\kdyzgl_test_seed.sql`、`hrm-dev\docs\demo-accounts.md`
- `d:\Users\16626\Desktop\kdyzgl-base\{.gitignore,env.example,SESSION-STATE.md}`

- **未运行**：任何编译 / 单测 / E2E / 扫描 / 渗透 / 抓包 / 现网 curl（本机无 JDK/Maven/MySQL/Redis/Docker，无服务器访问）。
- **回滚**：删除本文件即可（纯新增文档）。
