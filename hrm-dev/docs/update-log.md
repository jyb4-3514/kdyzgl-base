# 变更日志

## 2026-09-24 · 后端 M4 批（登录契约改造）：短信验证码登录 + 设备信任 + 3 天到期强制重登 — 与前端 Mock 逐条对齐

### 概述
落地 `hrm-server` 登录契约改造 **M4**：新增 A1 `/auth/sms/send`、A2 `/auth/sms/login`、B2 `/auth/device/verify`、C1/C2 `/auth/devices`、D1 `/auth/captcha`；改造 B1 `/auth/login`（仅追加**可选**入参与可选出参，既有字段零变更）。会话时效统一为 **3 天**（`jwt.expire` 86400→**259200**，与 `hrm.auth.session-ttl-seconds` 双控）；到期按用户裁定**强制重新登录**，返回**专用码 1108（HTTP 200 + code=1108）**，与前端 Mock `engine.js#resolveUser` 完全一致。设备信任改为**服务端签发 `device_token`**（HttpOnly + Secure Cookie 下发，库中只存 SHA-256 摘要），落实 `security-auth-review.md` §3/§4 六条加固建议。**B3 `/auth/session/renew` 不实现**（用户裁定不做短信续期）。未改任何迁移脚本、`api.md`/`db.md` 与被保护文档。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `enums`·ErrorCode 11xx 段 | 补 1101–1109（1101 频控/1102 验证码错/1103 尝试超限/1104 分流码/1105 通道不可用/1106 图形码错/1107 设备撤销/1108 到期/**1109 未绑手机号**），文案逐字对齐 Mock `CODE_MESSAGE` |
| **新增** `service/auth/support/` 七个支撑类 | `DeviceFingerprint`（HMAC-SHA256 指纹 + 生产空盐 fail-fast）、`DeviceTokenCodec`（256bit 令牌 + SHA-256 摘要 + 恒定时间比较）、`TrustedDevicePolicy`、`SmsCodeStore`（验证码/失败计数/全维频控 Redis）、`DeviceTicketStore` + `DeviceTicket` + `DeviceSnapshot`、`CaptchaStore`、`DeviceCookieSupport`、`TrustedDeviceRegistry`、`AuthRequestContext`、`SessionExpiryPolicy`、`EndAdmissionPolicy` |
| **新增** `entity/AuthTrustedDevice` + `mapper/AuthTrustedDeviceMapper` | 映射 V15 已建表（18 列），只存令牌摘要与 HMAC 指纹；撤销软标志 `revoked/revoked_at` |
| **新增** DTO/VO | `DeviceInfo`、`SmsSendRequest`、`SmsLoginRequest`、`DeviceVerifyRequest`；`SmsSendVO`、`TrustedDeviceVO`、`CaptchaVO` |
| `controller/auth/AuthController` | 新增 6 端点 + 3 处设备令牌 Cookie 下发（登录/短信登录/设备验证）；`/auth/devices*` 显式 `@RequireRoles` |
| `service/auth/AuthService(+Impl)` | 新增 sendSms/smsLogin/deviceVerify/listDevices/revokeDevice/captcha；`login` 追加可选能力（端准入/设备信任分流）；`changePassword` 追加「改密即失效」 |
| `filter/JwtAuthFilter` | 新增**重认证窗口判定**：超 `hrm.auth.periodic-reauth-seconds` 返回 **HTTP 200 + code 1108**（会话在→取会话认证时刻；会话已随 TTL 消失→取已验签 JWT `iat`） |
| `util/JwtUtil` | 新增 `expiry-grace-seconds` 校验宽限（`setAllowedClockSkewSeconds`，仅使「刚过期」可被解析以返回 1108，**不延长可用期**）；保留 2 参构造器（单测兼容） |
| `util/DesensitizeUtil` | 新增 `maskIp`（末段打码，对齐 Mock） |
| `common/PublicEndpoints` | 白名单 2 → **6**（新增 A1/A2/B2/D1；`/auth/devices*` 不入白名单） |
| `common/SessionInfo` | 新增 `loginEpochSeconds`（服务端权威 `lastAuthAt`） |
| `config/AuthProperties` | 新增 `expiry-grace-seconds`/`captcha-ttl-seconds`/`device-ticket-ttl-seconds`/`max-trusted-devices-per-employee`/`device-fingerprint-salt` |
| `config/SmsProperties` | 新增 `ip-hourly-limit`/`device-hourly-limit`/`account-daily-limit`/`dev-fixed-code`（降级通道联调用，生产不可达） |
| `resources/application.yml` | `jwt.expire: 259200`；`hrm.auth.*`/`hrm.sms.*` 新增键逐键注释（**全 ASCII 键**，规避 YAML 非 ASCII 键被 Spring 静默归属） |
| `entity/HrFlow` | **移除** `operatorId`/`operatorName` 的 `@TableField(exist = false)`（V14 已补列，含 2 处 `TODO(扩展)` 已消解） |
| `service/employee/impl/EmployeeServiceImpl` | `forceOffline` 追加「使该员工已信任设备全部失效」（禁用/删除/重置密码/角色降级同口径） |
| 单测 | 新增 10 个测试类（见下）；`PublicEndpointsTest` 白名单断言由 2 更新为 6 |

### 关键设计取舍
- **1108 与 401 必须区分**：`HTTP 200 + code=1108` 为「正常安全生命周期到期」，前端据此显示「登录已到期」提示条并带 `redirect` 回跳；`401` 为「登出/被顶下线/被强制下线」（静默回登录页）。与前端 Mock `engine.js` 口径逐字一致。
- **到期判定用服务端单调时钟**：优先取会话内 `loginEpochSeconds`；会话键已随 Redis TTL 消失时，退回取**已验签 JWT** 的 `iat`（客户端不可篡改）。
- **`expiry-grace-seconds`（默认 300s）的引入理由**：`jwt.expire` 与窗口均为 259200s 时，两者在同一瞬间到期，JWT 解析会先抛过期异常 → 只能给出裸 401，前端拿不到 1108。该宽限**仅放宽 JWT 校验**，使服务端能在超窗后仍识别请求并返回 1108；**鉴权窗口恒为 3 天，宽限期内不存在任何「可用」请求**（超窗即 1108 拒登）。
- **设备信任凭据换代**（安全报告 §4.2 高危项整改）：Mock 以「前端 deviceId 命中受信记录」判定放行，属可伪造路径；后端改为**服务端签发 `device_token`**（HttpOnly + Secure + SameSite=Lax，限 Path=/api/v1/auth）+ **摘要匹配**，前端 deviceId/platform/model 等仅作弱信号（指纹/审计/展示），**不作放行依据**。前端无需改代码（同源请求自动携带 Cookie；令牌对 JS 不可见）。
- **验证码一次性 + 全维限频 + 恒定时间比较**：`SecureRandom` 生成、TTL 走配置、失败计数达标即作废；同号间隔/同号日限/同 IP 时限/同设备时限/同账号日限五维；比较用 `MessageDigest.isEqual`。
- **改密/禁用等安全事件**：既删该员工全部会话，又使其全部受信设备失效（否则旧设备凭旧令牌免二次验证）。
- **降级通道固定码**：仅当未配置厂商凭据（非生产；生产 `SmsConfigGuard` fail-fast）时使用 `hrm.sms.dev-fixed-code`（默认 `000000`，与前端演示资产一致），绝不入日志/响应体。

### 事实性纠正
- 任务称「`jwt.expire` 仍是 86400，本批统一为 259200，并同步核对 `expiresIn` 出参语义变化」——已核对：`expiresIn` 语义仍为「Token 有效期（秒）」，**仅时长由 1 天改为 3 天**，字段名/类型/位置未变，不构成契约变更。
- 任务称设备信任表为「18 列 / 3 索引」——与 V15 实测一致（唯一键 `uk_auth_trusted_device_emp_fp` + 2 普通索引）。
- 任务附表 2 `auth_sms_log`（架构「可选但建议」）**未随 V15 建表**，故本批不写短信审计表；短信审计仅落应用日志（**不含验证码**），如需落表须由主智能体排期另立迁移（属 C 档）。
- 任务称「前端短信场景仅需 `twoFactorTicket`」——实测前端 `sendSms({scene:'DEVICE_VERIFY', twoFactorTicket, deviceId})` **确实不传手机号**，与 Mock 一致，故 A1 设备场景以票据定位员工（验证码按 employeeId 暂存）。

### 验证状态
本机无 JDK/Maven：**未编译、未执行任何单测**。静态自审通过（导入/注解/签名/分层/参数化 SQL/配置键与强类型类逐键对应/Lombok getter/无密钥硬编码）。新增 JUnit 覆盖：会话 3 天与小时窗边界、端准入（WEB→1110、H5+boss、空值/非法值）、设备令牌熵与摘要、受信设备有效期与撤销、票据到期、指纹 HMAC 与生产空盐 fail-fast、验证码 TTL/尝试上限/恒定时间比较、全维频控窗口、受信设备 upsert/撤销/全量失效、11xx 码值与文案。**编译与单测执行、Flyway 与联调实测收敛到服务器阶段**（`mvn -B clean test` + 接口联调）。

### 联动项（非本角色改动）
- `api.md` 需补 11xx 段与 A1/A2/B2/C1/C2/D1 端点（**本批禁改该文档**，须主智能体排期）。
- 生产外置配置需补齐：`jwt.secret`、`hrm.auth.device-fingerprint-salt`、`hrm.sms.aliyun.*`（真实值由主智能体托管下发，仓库只留 `change_me_*`）。
- 新增 4 个公开端点（A1/A2/B2/D1）使外部攻击面扩大，**公网暴露前须再过 P0.5 安全评估**（security-auth-review §5-R9）。

### 回滚
- 代码回滚：反向 diff 还原本批新增/修改的 `hrm-server` 文件（无迁移、无数据变更，回滚无数据丢失）。
- 兼容性：既有 4 端点出入参与错误码未变，老前端不受影响；`PublicEndpoints` 若回滚则新增端点随之失效（前端调用返回 401）。
- 会话时效：`jwt.expire` 回滚为 86400 后，存量 3 天 Token 仍有效至其自身 `exp`。

## 2026-09-24 · 服务端数据库 V14 + V15（`hr_flow` 补创建人两列 + 新建设备信任表 `auth_trusted_device`）— 只出脚本与文档，未执行迁移

### 概述
登录体系改造批次 **M3** 的结构先行（P4）产出：① **V14** 修复 `hr_flow` 缺失创建人列（V7 建表缺口，曾致 `HrFlow.operatorId/operatorName` 只能 `@TableField(exist = false)`）；② **V15** 新建 `auth_trusted_device`（服务端持有设备信任态）。同步刷新快照 `sql/schema/mysql/init.sql` 与文档 `docs/db.md`（新增第 10 章）。**本轮只出脚本与文档，不执行任何迁移（执行属 C 档，须主智能体三步授权后由运维执行）**；V1~V13 未改动，表总数 37 → 38。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `hrm-server/src/main/resources/db/migration/mysql/V14__hr_flow_operator_columns.sql` | `ALTER TABLE hr_flow ADD COLUMN operator_id BIGINT / operator_name VARCHAR(50)`（均可空、列尾追加、INSTANT 无锁无重建）；自带 `DROP COLUMN` 回滚段 |
| **新增** `hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql` | 新建 `auth_trusted_device`（18 列；唯一键 `uk_auth_trusted_device_emp_fp` + 2 普通索引）；自带 `DROP TABLE` 回滚段 |
| `sql/schema/mysql/init.sql`（快照） | 头部版本清单更新至 V3..V15；`hr_flow` 追加两列；末尾追加 `auth_trusted_device` 建表 |
| `docs/db.md` | 文档版本 v2.0 → v2.1；§8.5.4 补 `hr_flow` V14 字段与联动项；§8.13 补 V14/V15；新增 **§10 登录体系改造表结构设计**（字段/索引/枚举/逻辑关系/不建表取舍/一致性核对）；§9.1 增 Q-DB-5/6、§9.3 计数与检查项、§9.4 增 U-11/12 |

### 关键设计取舍
- **设备信任态由服务端持有**（`security-auth-review.md` §4.2 高危缺陷整改）：库中只存服务端签发 `device_token` 的 **SHA-256 摘要** `device_token_hash`，**绝不存明文**；前端设备属性经服务端外置盐 HMAC 后落 `device_fingerprint`，**仅弱信号/审计用，不作放行依据**。
- **唯一索引为决策 D7 的显式例外**：`uk_auth_trusted_device_emp_fp (employee_id, device_fingerprint)` 支持幂等 upsert；本表撤销用业务标志 `revoked`/`revoked_at`（**不用 `is_deleted`**），撤销后复用同一行重信，规避 D7 担心的「逻辑删除后唯一键阻止复用」场景。已登记为 §9.1 Q-DB-5，如相关方要求严格回 D7 则另立版本。
- **敏感/短生命周期数据不建表**：短信验证码、验证码失败计数、二次验证票据、`device_token` 明文、登录会话、指纹盐一律走 Redis/外置配置（db.md §10.4），守「验证码绝不入日志/审计」红线。
- **索引命名纠正**：架构 §4.2.1 用缩写前缀 `auth_device`，与 db.md「索引名带完整表名前缀（双库可对照）」冲突，改按表名 `auth_trusted_device` 前缀（`uk_auth_trusted_device_emp_fp` 等），语义不变。

### 事实性纠正
- 任务背景称设备信任模型在 `multi-client-architecture.md` **§3.2**；实测该文档 **§3 为「服务器拆分方案」**，设备信任模型实为 **§4.2**（§4.2.1 表清单）。V15 与 db.md §10 依据按 §4.2 落地。
- 任务称快照为「37 张业务表」；本批后为 **38 张**（+`auth_trusted_device`）。
- 任务称「唯一性沿用 D7 … 除非架构明确要求唯一索引」：架构 §4.2.1 确对设备信任表**明确要求唯一键**，故按唯一索引落地并登记为 D7 例外（非疏漏）。

### 联动项（非本角色改动）
- 补列完成后，由**后端工程师**移除 `entity/HrFlow.java` 中 `operatorId` / `operatorName` 两字段的 `@TableField(exist = false)`（含 2 处 `TODO(扩展)` 注释），使创建人可持久化并回填 `HrFlowVO` 出参。
- 若纳入 `auth_sms_log`（架构 §4.2.1 表2「可选但建议」）须由主智能体排期另立 V16（见 db.md §9.1 Q-DB-6）。

### 验证状态
本机无 MySQL：**未实跑迁移**。静态自检通过（语法/索引名全局唯一/表名与列名无冲突/新列可空/快照与迁移逐表一致/无 `SELECT *`/无存储过程·触发器·物理外键/无真实数据与凭据）。**DDL 实跑与 `SHOW CREATE TABLE` 比对、Flyway `flyway_schema_history` 校验、唯一键 upsert 行为验证收敛到服务器阶段**（db.md §9.4 U-1/U-2/U-11/U-12）。

### 回滚
- 结构回滚：按各脚本尾部 `-- 回滚:` 段人工执行（V14 `DROP COLUMN` 两列；V15 `DROP TABLE auth_trusted_device`）。
- 文档/快照回滚：反向 diff 还原 `init.sql` 与 `db.md` 本章改动即可（不涉生产数据）。
- 迁移**执行**属 C 档：须主智能体三步授权（影响范围/可逆性/回滚步骤）+ 上线前备份库，由运维执行。

## 2026-09-24 · PC 端仅 ADMIN 可登录（服务端强制校验）— 新增可选头约束 + 新错误码 1110，零既有出入参变更

### 概述
落地用户需求「员工和站长无权限登录 PC 端，只有管理员（ADMIN）账号可登录」，并落实 `security-auth-review.md` §4.5「三端应各自适用不同角色口径」的加固建议。服务端在 `POST /api/v1/auth/login` 登录链路中新增**端维度角色强制校验**：当端类型归一为 PC 管理端（`ClientType.ADMIN`）时，仅允许角色在 `hrm.auth.pc-allowed-roles` 集合内（默认 `ADMIN`）；站长/员工声明该端登录一律拒绝。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `main/java/com/qiujie/service/auth/support/ClientRolePolicy.java` | 纯逻辑类：端类型归一判定 + 角色-端允许集合判定 + 逗号分隔配置解析（去空白/忽略大小写/丢弃非法角色名），无 Spring/Redis 依赖，可离线单测 |
| `main/java/com/qiujie/config/AuthProperties.java` | 新增 `hrm.auth.pc-allowed-roles`（`List<String>`，默认 `["ADMIN"]`） |
| `main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java` | 注入 `AuthProperties`；在「密码 + 状态校验通过之后、签发 Token 之前」新增端维度角色校验，不通过则写失败登录日志（原因「该账号无权登录此端」）并抛 `ErrorCode.LOGIN_CLIENT_NOT_ALLOWED(1110)` |
| `main/resources/application.yml` | `hrm.auth` 下新增 `pc-allowed-roles: ADMIN`（标量、全 ASCII），并补逐键注释 |
| **新增** `test/java/com/qiujie/service/auth/support/ClientRolePolicyTest.java` | 覆盖端类型缺失/非法/大小写混合、三角色 × 各端、配置空/含非法角色名/含空格/null 的边界 |
| **新增** `test/java/com/qiujie/enums/ClientTypeTest.java` | 端类型归一单测（缺失/非法/大小写/含空白） |
| `test/java/com/qiujie/enums/ErrorCodeSegmentTest.java` | 补 `LOGIN_CLIENT_NOT_ALLOWED(1110)` 码值与文案断言（防回归） |

### 契约影响（逐项）
- **入参**：`/auth/login` JSON 入参结构**不变**；仅既有可选请求头 `X-Client-Type` 从「仅归一存储」升级为「参与登录角色约束」（缺失/非法仍回落 WEB，不施加约束）。
- **出参**：`LoginVO` 结构**不变**。
- **错误码**：新增可能返回 `1110 该账号无权登录此端`（HTTP 200 + `{code:1110}`，与 10xx 业务码同映射）；未命中该场景时行为与改造前逐位一致。
- **未改** `/auth/logout`、`/auth/me`、`/auth/password` 及任何其他接口；未实现 M3/M4 内容（设备信任表、短信端点、3 天重认证状态机）。
- **`api.md` 未同步**（本批属禁改清单文件），须由主智能体后续回填：`/auth/login` 的可选头语义与 11xx 段 1110。

### 安全定位（为什么不是安全边界）
端类型来自客户端可伪造/可省略的 `X-Client-Type` 头，故本约束是**产品/审计约束**而非鉴权边界；真正的安全边界恒为「角色 + 数据范围」——授权以会话中的真实 `role` 为准。伪造端类型（如声明 `H5` → 归一为 `WEB`）绕过本校验者，其权限仍由 `role` 决定，**不会获得任何额外权限**。

### 事实性纠正
- 任务要求「新增专用错误码」，但工作区 `ErrorCode.java`（未提交改动）**已存在** `LOGIN_CLIENT_NOT_ALLOWED(1110, "该账号无权登录此端")` 且编号不与 M4 规划的 1101–1109 冲突，故**复用不重复新增**（重复定义将导致枚举常量重复编译错误）。
- 任务「§3 加固建议第 5 条」在 `security-auth-review.md` 中无逐字对应；实际依据为 §4.5「三端独立客户端共用同一后端」条目中「三端应各自适用不同角色口径（员工端禁管理端点等）」及 §1.1 高风险清单，本批即该建议在**登录入口**侧的最小落地。
- 需求「端类型为 PC（网页端/PC 管理端）」与「不传 `X-Client-Type` 按 WEB 且不限制角色」需调和：本批按**仅 `ClientType.ADMIN`（PC 管理端）受约束**实现，`WEB`（缺省回落）不施加约束以保持改造前行为；已在 `ClientRolePolicy` 标注 `TODO(扩展)`（若产品要求 WEB 同样收紧，属契约/口径变更，须先经用户确认）。

### 验证状态
本机无 JDK/Maven：**未编译未实跑**，IDE 诊断 0 报错，静态自审通过（包引用/方法签名/注解/Lombok getter/配置键与强类型逐键对应/未对不可变集合传入 null），**收敛到服务器阶段**由主智能体 `mvn package` + `mvn test` + 启动复验（重点：`pc-allowed-roles` 绑定成功；`X-Client-Type: ADMIN` + STAFF 账号返回 1110；不传该头行为与改造前一致）。

### 回滚
反向 diff 还原上述 6 个改动文件 + 删除 2 个新增测试类即可（不涉 DB、不涉既有出入参结构；默认值 `ADMIN` 生效后 PC 端行为仅对显式声明 `X-Client-Type: ADMIN` 的客户端变化）。

## 2026-09-24 · 服务端首次启动配置绑定失败修复（`hrm.algo.dispatch.keyword-weights`）— 零契约/零口径变更

### 概述
服务器首次启动 `hrm-server.jar` 报 `Failed to bind properties under 'hrm.algo.dispatch.keyword-weights' to java.util.Map<String,Double>`（`ConverterNotFoundException: Double → Map`）。编译与 Flyway 均已通过，仅此一项阻塞启动。已排除 YAML 语法与文件编码（`file -i` = utf-8，`od -c` 中文为正确 UTF-8 字节），属**框架行为边界**。改 2 个文件（Java + YAML）。

### 根因（框架层）
Spring Boot 绑定前将属性名经 `ConfigurationPropertyName.adapt()` 规范化，该方法**静默丢弃非 ASCII 字符**。YAML 拍平后的子键 `hrm.algo.dispatch.keyword-weights.破损`（及 丢失/故障/投诉）被归并为同一父名 `hrm.algo.dispatch.keyword-weights`，Binder 遂把该父名的**首个值**（`1.0`）当作整张 Map 的值 → 目标类型 `Map<String,Double>` 找不到 `Double→Map` 转换器而失败。这解释了「两次报错 `Value` 都是 `1.0`、`Origin` 随首个值移动」的现场特征。官方 Externalized Configuration 亦载明「Map 键含非小写字母数字须用 `[key]` 记法」。故非编码/语法问题，flow 与 block 两种写法均无效。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| `main/resources/application.yml` | `dispatch.keywordWeights`（嵌套 Map + 中文键）→ `dispatch.keywordWeightList`（列表，中文只作**值**）；订正原「block mapping 可修复」的误导注释 |
| `main/java/com/qiujie/config/AlgoProperties.java` | `Dispatch.keywordWeights` 字段（`Map<String,Double>`）→ `keywordWeightList`（`List<KeywordWeight>`，ASCII 字段名）；新增派生只读 `getKeywordWeights()`（消费方签名不变）；新增 `KeywordWeight` 内部类 |

### 取舍
选「列表承载 + 派生 Map」（方案 A）：中文关键词作**值**不触发框架限制；ASCII 字段名可正常绑定；新增任意中文关键词无需改代码（优于方案 B 的 ASCII 键映射表，无需维护映射常量）；消费方 `WorkOrderServiceImpl:466` 与 `WorkOrderKeywordClassifier.classifyByScore` 签名不变。方案 C（自定义 Converter）复杂度高且仍绕不开非 ASCII 名，不选。

### 同类风险排查
逐项核对 `hrm.algo.*` 全部嵌套 Map / 复杂集合：`kpi.tieredTiers`、`kpi.levels`、`kpi.quantile`、`payroll.attendanceFieldMap`、`leave.countModeMap`、`schedule.weights`/`sa`、`attendance.anomaly`、`dispatch.slaHours`（数字键，"0"/"1"/"2" 属合法名字符）、`dispatch.weights`、`parcel.forecast`/`capacity`/`outlier`/`page`、`ratelimit`、`log`、`sync` —— **键均为 ASCII，无风险**；全库 yml 仅 `keywordWeights` 一处非 ASCII 键（其余 CJK 均出现在注释或值中），已修复。

### 事实性纠正
任务提示「block mapping + 键加引号可稳定解析」不成立：报错 `Origin` 随首个值行号移动（189:15 → 新位置）即证明换写法无效，根因在属性名规范化而非 YAML 形态。

### 验证状态
本机无 JDK/Maven：**未编译未实跑**，IDE 诊断 0 报错，静态自审通过，**收敛到服务器阶段**由主智能体 `mvn package` + 启动复验（重点：启动不再报 keyword-weights 绑定失败；`keywordWeighted=false` 时派单行为与现状逐位一致）。

### 遗留与 TODO(扩展)
- `TODO(扩展)`：`algo-hrm-server.md §11.6`、`server-architecture.md §5.1` 仍登记旧键 `hrm.algo.dispatch.keywordWeights`（属禁改清单文件），待主智能体回填为 `keywordWeightList`。
- 若外置 `application-prod.yml` 曾覆盖 `hrm.algo.dispatch.keyword-weights.*`，该覆盖已失效，须改用 `keywordWeightList` 列表形式（当前仓库无此类外置配置）。

### 回滚
反向 diff 还原上述 2 个文件即可（不涉 DB、不涉 HTTP 契约与业务口径；`keywordWeighted` 默认 false、权重组默认值逐位不变）。

## 2026-09-24 · 服务端单元测试修复（`mvn test` 441 run / 8F / 10E）— 零 HTTP 契约与口径变更

### 概述
服务器首次实跑 `mvn test`：441 用例，8 failures + 10 errors，集中在 6 个类。逐条比对 Mock（行为规格）/ `demo-sync-config-design.md` 后判定：**2 处实现缺陷、6 处测试/夹具缺陷**。修复后应全绿。改 8 个文件（2 实现 + 5 测试 + 1 夹具），不动 `pom.xml`、不删用例、不弱化断言、不加 `@Disabled`。

### 逐条判定
| # | 测试类.方法 | 判定 | 契约依据 | 修复动作 |
| - | ---- | ---- | ---- | ---- |
| 9–17 | `ClientLogSanitizerTest`（9 用例 NPE） | 实现 bug | 白名单 `LEVELS/SOURCES` 为 `List.of` 不可变列表，`contains(null)` 抛 NPE | `ClientLogSanitizer.sanitize` 对 level/source 先判空再白名单命中 |
| 5 | `SyncConfigValidatorTest.timeRangeBoundary` | 实现 bug | `demo-sync-config-design.md:87`「结束允许 24:00」；`SyncClock.isEndClock` 及 `normalizeExtraAttrs` 同口径 | `validateTimeRange` 结束位改判 `isEndClock` |
| 1 | `AttendanceDetailPolicyTest.otherDimensions` | 测试 bug | Mock `attendanceStore.detailMembers`「按卡状态过滤 + 去重」→ NORMAL 命中 {1,3} | NORMAL 期望 1 → 2 |
| 2 | `HrProfileValidatorTest.invalidDictionaryRejected` | 测试 bug | Mock `routes/hr.js:74/76` 拼的是 `EDUCATION_KEYS/CONTRACT_TYPE_KEYS`（枚举键），非 `*_LABEL`（中文标签） | 期望文案改英文枚举键 |
| 3 | `LeaveIntervalPolicyTest.countedDaysExcludesRestDays` | 测试 bug | Mock `leaveStore.halfUnitsOf/countedDaysOf`；起始日 PM 仅计 0.5，10-03 计 1.0 | 期望 2.0 → 1.5 |
| 4 | `SyncConfigValidatorTest.normalizeConstraints` | 测试 bug | Mock `syncConfigStore.js:996` `minLen` 缺省归一为 0 | 删除误置的 `text.put("minLen", 1)` 夹具行，期望 0 不变 |
| 6/7/8 | `SyncCsvImporterTest.planAllUpdate/planAllSkip`、`SyncCsvRoundTripTest.semanticRoundTrip` | 夹具 bug | Mock 种子 `syncConfigStore.js:111` `timeout_minutes` 约束 `{min:1,max:120}`（非 0-10） | `SyncTestFixtures.items()` 给 `timeout_minutes` 独立约束 1-120 |
| 18 | `SessionUtilTest.saveBySidEvictsSameClientSameDevice` | 测试 bug | Mockito 约束：matcher 与字面量不可混用 | 字面量参数改用 `eq()` |

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| `main/.../service/support/ClientLogSanitizer.java` | level/source 白名单命中前判空（+2 行注释） |
| `main/.../service/sync/support/SyncConfigValidator.java` | `validateTimeRange` 格式判定 `isClock(end)` → `isEndClock(end)` |
| `test/.../service/attendance/support/AttendanceDetailPolicyTest.java` | NORMAL 期望 2（+ 契约注释） |
| `test/.../service/hr/support/HrProfileValidatorTest.java` | 字典非法文案改枚举键（+ 契约注释） |
| `test/.../service/leave/support/LeaveIntervalPolicyTest.java` | 期望 1.5（+ 注释订正） |
| `test/.../service/sync/support/SyncConfigValidatorTest.java` | 去掉误置 minLen 夹具行（+ 契约注释） |
| `test/.../service/sync/support/SyncTestFixtures.java` | `timeout_minutes` 独立约束 1-120 |
| `test/.../util/SessionUtilTest.java` | 第 97 行改 `eq()` matcher（+ import） |

### 事实性纠正
- 任务提示「#2 属实现缺陷、Mock 用中文标签」不成立：中文标签是**出参 `educationLabel`** 用（`hrStore.js:384`），校验文案仍是枚举键（`routes/hr.js:74`）。实现与 Mock 一致，故改测试。
- Mock `syncConfigStore.js:535` 的 `validateTimeRange` 用 `isClock(end)`，与 `demo-sync-config-design.md:87`、`syncConfig.js`/`syncConfigCsv.js` 的 `isEndClock` 口径**自相矛盾**（`allowEnd2400` 分支在此变为死代码）。禁改 `hrm-demo`，故按设计文档口径修 Java 实现；Mock 该行为偏差登记为遗留（交主智能体裁定是否单独修演示工程）。

### 验证状态
本机无 JDK/Maven（`java` 未识别）：**未编译未实跑**，静态自审 + IDE 诊断 0 报错，**收敛到服务器阶段**由主智能体 `mvn test` 复验。

### 遗留与 TODO(扩展)
- `TODO(扩展)`：Mock `syncConfigStore.js:535` 与设计文档口径不一致，待主智能体裁定是否修正演示工程（本批不动）。
- 无产品/口径待裁定项（本次修复均不作口径变更）。

### 回滚
反向 diff 还原上述 8 个文件即可（不涉 DB、不涉前端、不涉 HTTP 契约与业务口径）。

## 2026-09-24 · 服务端编译修复（HrFlow 创建人字段 / KPI 排名承载类型）— 零契约变更

### 概述
首次在服务器（JDK 17 + Maven 3.6.3）执行 `mvn -B -DskipTests package`，EXIT=1；450+ 源文件中**仅 2 个文件 10 处错误**。按「修根因、最小改动、不改 HTTP 契约与业务口径」修复，共改 2 个文件。

### 改动
| 文件 | 改动摘要 | 依据 |
| ---- | ---- | ---- |
| `entity/HrFlow.java` | 新增 `operatorId` / `operatorName` 两字段，标注 `@TableField(exist = false)`（非持久化） | hr_flow 表无 `operator_id` / `operator_name` 列（db.md §8.5.4、V7__hr.sql），不能映射为持久化列 |
| `service/kpi/impl/KpiScoreServiceImpl.java` | `detail()` 内 `entry` 承载类型由 `KpiRankingPolicy.Entry` 改回本类私有 `RankedEntry`（1 行 + 注释） | `KpiRankingPolicy.Entry` 是「排名输入行」（3 参，被 `competitionRanks` 消费、被 `KpiRankingPolicyTest` 固定），不承载 rank/level |

### 根因
1. **HrFlow**：Service 按 Mock `toFlowVO.operatorId/operatorName`（流程**创建人**）在实体上 `setXxx/getXxx`，但 `hr_flow` 建表**缺**这两列（其余含操作人的表——`hr_flow_step`/`hr_salary_log`/`leave_log`/`work_order_timeline` 等——均已落列）。属**表结构与出参契约的缺口**，非实体漏写。为不生成不存在列导致运行时 SQL 报错，标 `exist = false` 暂不持久化。
2. **KPI**：`detail()` 的局部变量类型被写成 `KpiRankingPolicy.Entry`，而 `rankedEntry()` 实际返回私有 `RankedEntry`——一次改名/抽取后的**类型注解残留**。二者非重复定义：`Entry`（id + 总分 + 达成率，排名输入）≠ `RankedEntry`（rank + 汇总 + 等级，明细页结果承载）。

### 同类隐患排查（方法 + 结论）
1. **实体缺 getter/setter**：javac 本次仅报 10 处错误（远低于默认 `-Xmaxerrs=100` 上限）⇒ 报错清单完整；且若 Lombok 未生效会对全部实体报同类错。**结论：无其他实体成员缺失**（P4/P5 的 `KpiScore`/`KpiMetric`/`HrFlowStep`/`HrSalary` 等本次服务端调用点均解析通过）。
2. **support 纯逻辑类与内部类同名/同义重复**：枚举 `service/**` 全部内部 `record`/`class`（`PayrollServiceImpl.Operator`、`LeaveServiceImpl.LeaveForm`、`SyncConfigCenterServiceImpl.ItemSet`、`SyncCsvImporter.RowResult/ExtraParse`、`LogFingerprintAggregator.Entry`、`KpiScoreServiceImpl.RankedEntry`），并对 `Operator/LeaveForm/ItemSet/Entry/RankedEntry` 做定义处检索。**结论：除 KPI 外无同名/同义重复；`KpiRankingPolicy.Entry` 命名空间内唯一，KPI 一例经判定亦非重复**。
3. **类型混用（BigDecimal ↔ int）**：核对 P7 `AttendanceStat`（`int,int,BigDecimal,int,BigDecimal`）的构造点 `PayrollContextProvider` 与取值口 `byField()`（全 BigDecimal）以及财务消费点。**结论：无 int/BigDecimal 混用；`KpiScoreServiceImpl` 内 int 得分 / BigDecimal 汇总亦一致**。

### 验证状态
本机无 JDK/Maven：**未编译**（静态自审 + IDE 诊断 0 报错），**收敛到服务器阶段**由主智能体 `mvn package` 复验。

### 变更清单（表结构，交数据库工程师；执行属 C 档）
`hr_flow` 补 `operator_id BIGINT NULL COMMENT '创建人（逻辑外键 employee.id）'`、`operator_name VARCHAR(50) NULL COMMENT '创建人姓名快照'`；同步 `init.sql` 快照与 `db.md §8.5.4`，用**新版本号**迁移（禁改历史脚本）。补列后移除 `HrFlow` 两字段的 `exist = false` 即持久化。可逆（DROP COLUMN）。不补列时出参 `operatorId/operatorName` 仅创建响应有值、读取路径为 null（前端未消费，无功能影响）。

### 遗留与 TODO(扩展)
- `HrFlow`：`TODO(扩展): hr_flow 补列后去掉 exist = false`（已就地标注）。
- `RankedEntry` 与 `KpiScoreVO` 字段部分重叠，可在后续批次合并（本批不做，避免超范围）。

### 回滚
反向 diff 还原上述 2 个文件即可（不涉 DB、不涉前端、不涉契约与口径）。

## 2026-09-24 · 前端演示工程（hrm-demo）角色称呼「老板」统一为「管理员 / 管理端」— 纯文案变更

### 概述
按口径「前端的老板称呼全部改为管理员」统一演示工程对外称呼：指「人」用「管理员」，指「端」用「管理端」。
系统角色标识仍为 `ADMIN`；**不含任何标识符 / 路由 / 字段 / 接口 / 装配规则变更**（`boss`、`BOSS`、`bossRoutes`、`/boss/**`、`data-testid`、CSS 类名一律未动）。

### 交付
| 范围 | 内容 |
| ---- | ---- |
| 展示文案 | 角色显示名（`constants/dict.js` KPI 适用角色、`demo/accounts.js` 演示身份 label）、登录品牌副标题、终审/审批状态与驳回阶段文案、空态与提示语等 |
| 代码注释 | 三端源码内中文注释同步 |
| 测试与脚本 | `e2e/*.spec.js` 选择器/断言文本、`scripts/verify-mock.mjs` 与 `verify-mobile-t13-t16.mjs` 的 check 标签（仅改字面量，断言强度未弱化） |
| 文档 | `hrm-dev/docs/demo-*.md` 12 篇术语同步 |

### 边界与未动项
- `e2e` 中 `shot()` 截图名实参（如 `A3-2-老板端首页`）对应已入库产物文件名，按「文件名不动」保留。
- 禁改清单文档（`algo-hrm-server.md` / `multi-client-architecture.md` / `project-tree.md` / `security-auth-review.md`）与本文历史条目未回改（M03 只追加不重写）。
- 环境开关与 Mock 装配（`installMock`、`VITE_MOCK_ENABLED` fail-safe、`resolve.dedupe`）零改动。

### 验证（hrm-demo 实跑）
`verify:mock` 892/892、`verify:mobile` 48/48、`lint` 0 error、`build` EXIT=0；`test` 429 通过 / 1 既有失败（`useAttendanceStatus.spec.js:228`，时间相关，未改其断言）。

### 回滚
按反向 diff 还原受影响文件，或整体丢弃本次未提交改动（不涉生产工程、不涉 `hrm-admin` / `hrm-server`）。

## 2026-09-24 · 服务端 M1 + M2（会话多端化地基 + 配置命名空间 + 短信/地理适配器端口）— 零契约变更

### 概述
按架构师方案 `multi-client-architecture.md` §4 与网络安全评估 `security-auth-review.md` §3/§4 落地 **M1（会话多端化地基）** 与 **M2（配置命名空间 + 适配器端口与降级）** 两批。
**两批均不改变任何 HTTP 契约**（URL / 入参 JSON / 出参 / 错误码一律不动，不新增对外端点，不改 `jwt.expire`）；**不执行任何 DB 迁移**（M3 的设备信任表不在本批）；不实现 M3/M4/M5（设备信任表、登录新端点、11xx 错误码、三端拆分）。

### 交付（M1 · 会话多端化地基）
| 项 | 载体 |
| ---- | ---- |
| sid 生成（SecureRandom 256 bit / 64 位十六进制，不承载可推导信息） | 新增 `util/SessionIdGenerator` |
| 多会话键规范 `hrm:session:{sid}` + 员工索引 `hrm:session:idx:{employeeId}`；互踢粒度降为「端 + 设备」；并发上限淘汰最旧 | 改写 `util/SessionUtil`（新增 `saveBySid/getBySid/deleteBySid/updateBySid/listSids/deleteAllOfEmployee`；旧 `save/get/delete(Long)` 保留兼容） |
| 淘汰决策纯逻辑（同端同设备覆盖 + 最大并发数淘汰最旧，确定性排序） | 新增 `util/SessionEvictionPolicy` |
| 会话解析（sid 优先 + 旧 token 回退 + jti 一致性） | 新增 `util/SessionResolution`；改写 `filter/JwtAuthFilter` |
| 会话/上下文扩字段（只增不减，旧会话反序列化兼容） | `common/SessionInfo`（+sid/employeeId/clientType/deviceId）、`common/LoginUser`（+clientType/deviceId）；`util/UserContext` 增 `getJti()` |
| 端类型归一（客户端自称、不参与鉴权；未知回落 WEB） | 新增 `enums/ClientType` |
| 强制下线语义保持（禁用/删除/改密/重置密码删全部会话） | `util/SessionUtil.deleteAllOfEmployee`；接入 `EmployeeServiceImpl.forceOffline`、`AuthServiceImpl.changePassword` |
| 登录按端+设备写会话；登出按 sid 精确注销 | `service/auth/AuthService(+Impl)`、`controller/auth/AuthController`（读可选头 `X-Client-Type`/`X-Device-Id`） |

### 交付（M2 · 配置命名空间 + 适配器端口与降级）
| 命名空间 | 强类型类 | 键（默认值） |
| ---- | ---- | ---- |
| `hrm.auth.*` | 新增 `config/AuthProperties` | `session-ttl-seconds(259200)`、`max-sessions-per-employee(5)`、`device-trust-enabled(true)`、`device-trust-ttl-seconds(2592000)`、`periodic-reauth-seconds(259200)`、`captcha-enabled(false)` |
| `hrm.sms.*` | 新增 `config/SmsProperties` | `provider(none)`、`aliyun.access-key-id/secret/sign-name/template-code-login/device/reauth(placeholders)`、`code-length(6)`、`code-ttl-seconds(300)`、`send-interval-seconds(60)`、`daily-limit-per-phone(10)`、`max-verify-attempts(5)` |
| `hrm.geo.*` | 新增 `config/GeoProperties` | `provider(haversine)`、`amap.key/security-code(placeholders)`、`fence-radius-meters(200)`、`timeout-millis(3000)` |

- 短信：端口 `service/support/sms/SmsSender` + 枚举 `SmsScene(LOGIN/DEVICE_VERIFY/PERIODIC_REAUTH)` + 结果 `SmsSendResult`；实现 `impl/AliyunSmsSender`（`@Primary`，仅 Key 有效时装配，SDK/HTTP 调用留 `TODO(扩展)`）、`impl/LoggingSmsSender`（降级，只记脱敏日志、绝不打码）；纯逻辑 `SmsCodeGenerator`（SecureRandom + 恒定时间比较）、`SmsThrottlePolicy`（频控窗口/TTL/尝试上限）；守卫 `SmsConfigGuard`（**生产 prod 未配置 Key 启动 fail-fast**）。
- 地理：端口 `service/support/geo/GeoService` + `GeoAddress`；实现 `impl/HaversineGeoService`（默认/降级，逐位等价现有 `HaversineCalculator`）、`impl/AmapGeoService`（`@Primary`，Key 未配置时**不被装配**，逆地理编码留 `TODO(扩展)`）。
- 装配条件：`config/condition/ExternalAdapterConditions`（provider 开关 + 凭据非占位判定）。
- 考勤围栏改走端口：`AttendanceCheckPolicy` 增 `evaluate(ClockFacts, GeoService)` 重载（默认 1 参重载保持原 Haversine 路径），`AttendanceRecordServiceImpl` 注入 `GeoService` 调用；**判定链顺序与 9104 语义不变**。

### 契约不变证明（本批零契约变更）
- 4 个 auth 端点逐项核对：`POST /api/v1/auth/login`（URL/入参 JSON/出参 `LoginVO`/错误码 1001/1002 不变；仅**新增可选请求头** `X-Client-Type`/`X-Device-Id`，缺失即回落 WEB/空，不影响任何既有客户端）；`POST /auth/logout`（200/401 不变，仅注销粒度由「账号」变为「当前 sid」）；`GET /auth/me`；`PUT /auth/password`（成功后仍全端下线）。
- **未改** `jwt.expire`（仍 86400）→ `/auth/login` 出参 `expiresIn` 取值不变；Redis 会话 TTL 取 `hrm.auth.session-ttl-seconds(259200)`，与 JWT exp 双控、取较短者生效（当前有效上限仍为 1 天）。
- 未新增/删除任何端点，未改 `PublicEndpoints`，未改 `ErrorCode`（11xx 属 M4）。

### 安全加固落实（对照 `security-auth-review.md` §3 第 1–5 条）
1. **§4.5①② 会话键可枚举 → 多会话 + 索引**：已采纳——sid 为 256 bit 高熵串，键 `hrm:session:{sid}` 不可枚举；索引 `hrm:session:idx:{employeeId}` 支撑精准/全量吊销。
2. **§4.5③ 精准吊销**：已采纳——登出按 sid；改密/禁用/删除/重置密码按索引删全部。
3. **§4.5④ 单账号最大并发设备数**：已采纳——`hrm.auth.max-sessions-per-employee`（默认 5），超限淘汰最旧。
4. **§4.5⑤ 端维度授权（防低权端冒充高权端）**：**部分采纳**——端类型仅归一存储、**不参与任何鉴权**（权限恒取服务端会话 role），杜绝「自称高权限端」提权；端→角色集合的显式约束属 M5，已标 `TODO(扩展)`。
5. **§4.4④ 短信未配置 Key 的弱兜底**：已采纳——降级仅日志（非生产），**生产 fail-fast**；验证码 SecureRandom、恒定时间比较、频控/尝试上限走配置；日志只打印脱敏手机号与位数，**绝不打码**。
6. **§4.4⑦ 验证码不入日志/异常/响应**：已采纳（本批）——`SmsSender` 端口不落码；`LoggingSmsSender`/`AliyunSmsSender` 日志均不含码。`ClientLogSanitizer` 正则补充 `smsCode/verifyCode/code/captcha` 形态属 P1 日志域改造，**未采纳（本轮不做，见遗留）**。

### 单测（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`util/{SessionIdGeneratorTest, SessionEvictionPolicyTest, SessionResolutionTest, SessionUtilTest(Mockito)}`、`service/support/sms/{SmsCodeGeneratorTest, SmsThrottlePolicyTest, SmsConfigGuardTest}`、`service/support/sms/impl/LoggingSmsSenderTest`、`config/condition/ExternalAdapterConditionsTest`、`service/support/geo/impl/HaversineGeoServiceTest`、`service/attendance/support/AttendanceCheckPolicyPortTest`。
覆盖：sid 格式/高熵/isValid 边界、并发上限淘汰最旧与同端同设备互踢与去重、旧 token 回退与 jti 不一致、索引增删与强制下线、频控窗口/TTL/尝试上限边界、恒定时间比较全分支、生产 fail-fast、日志不含码、端口与 Haversine 等价且确被消费。

### 未验证项（收敛到服务器阶段）
编译打包；`hrm.auth/sms/geo` 属性绑定；Spring 上下文（双 Bean @Primary 解析、`@Conditional` 装配、prod profile fail-fast）；Redis 多会话读写与 TTL；端到端：登录（新 token 走 sid）→ 多端并存 → 单端登出 → 改密全端下线 → 旧 token 回退仍可访问；考勤打卡 9104 行为回归。

### 事实性纠正
1. 架构 §3.5 示例把 `hrm.sms.provider` 写为 `aliyun`——本批仓库内取 **`none`**（无 Key 也可启动，降级明确；生产由 `SmsConfigGuard` fail-fast 强制为 aliyun+Key）。若照抄 `aliyun`，在未配 Key 的开发/联调环境会出现「生产实现被装配但无凭据」的误导态。
2. 架构 §3.5 的 `hrm.geo.provider` 示例为 `amap`——本批仓库内取 **`haversine`**（Key 未配置时 amap 实现不被装配，避免无 Key 空转）。
3. 架构 M1 验收④要求把会话键规范「同步写入 `server-architecture.md`」——与本次任务禁改清单（禁改 `server-architecture.md`）冲突；本批**未改该文档**，键规范已写入 `SessionUtil`/`SessionInfo` 类注释与本文，文档同步按 `multi-client-architecture.md` §8.3 待主智能体排期。

### TODO(扩展)
- 旧 token 回退分支移除条件与路径：`JwtAuthFilter` 已标注（待存量旧会话自然过期且确认无旧键写入方后移除回退 + `SessionUtil` 旧方法）。
- 兼容窗口说明：升级后新登不再写旧键、亦不删旧键，故**升级前签发的旧 token 在新登录后仍可用至其 JWT 到期**（≤ `jwt.expire` 1 天）；如需即时失效须改 `jwt.expire` 或清理旧键（属 M4 契约/运维范畴）。
- `AliyunSmsSender` 接入 SDK/HTTP（`com.aliyun:dysmsapi20170525` 或 HMAC-SHA1 签名）与失败归类 1105；`AmapGeoService.regeo` 接入高德 Web 服务；均须服务器端到端验证后再启用。
- 端 → 允许角色集合的显式约束（M5，防低权端冒充高权端的功能性兜底）。
- `hrm.auth` / `hrm.sms` / `hrm.geo` 三命名空间与新增键待回填 `server-architecture.md §5.1`（受禁改约束，本轮未改）。
- `ClientLogSanitizer` 凭据正则补充 `smsCode/verifyCode/code/captcha`（M4 落地短信端点时同步）。

---

## 2026-09-24 · 服务端 P10（包裹域 6 接口 · 算法 S7 趋势预测/容量热力/游标分页）— 145 接口收官

### 概述
新增 M10 parcel（6 接口：列表/看板/趋势/排行/详情/取件核销），145 接口**最后一批**。算法 **S7** 三项落地：
**S7-1 趋势预测**（Holt-Winters 加性季节 + 降级链）、**S7-2 容量与热力**（利用率预警 + IQR 离群）、
**S7-3 查询性能**（默认游标分页 + 覆盖索引 + 禁止 `SELECT *`）。
**未执行任何 DB 迁移**（`V13__parcel.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
**契约不可破坏**：6 接口入参/出参字段集与结构默认与 Mock 逐位一致；S7 的预测/容量热力以**配置开关控制的可选附加字段**输出（默认关闭），
游标翻页以**可选 `cursor` 入参 + `nextCursor` 出参** opt-in 提供——三项均登记为契约扩展（见下）。

### 交付
| 域 | 接口（6） | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P10 parcel（6） | `GET /parcels`、`GET /parcels/summary`、`GET /parcels/trend`、`GET /parcels/ranking`、`GET /parcels/{id}`、`PUT /parcels/{id}/pickup` | `controller/parcel/ParcelController`、`service/parcel/{ParcelService}+impl/ParcelServiceImpl`、`mapper/ParcelMapper`、`entity/Parcel`、`dto/parcel/*`、`vo/parcel/*` |

实体/映射：`entity/Parcel` + `mapper/ParcelMapper`（显式列 SQL：分页/计数/看板聚合/趋势分桶/驿站聚合/条件更新）。
纯逻辑支撑（可离线单测，`service/parcel/support/`）：`ParcelConstants`、`ParcelMetrics`、`ParcelForecaster`、`ParcelCapacityAnalyzer`、`ParcelCursorCodec`、`ParcelStatusMachine`、`ParcelSummaryRow`、`ParcelDailyCount`、`ParcelStationStat`。
参数外置：`AlgoProperties.Parcel` 扩展（`page.maxOffsetDepth/exposeCursor`、`forecast.minSamples/maWindow/appendForecast`、`capacity.appendToRanking`、`trend.minDays/maxDays`、`ranking.defaultMetric/sortableMetrics`）默认值与 Mock 等价。

### S7 落地要点
- **S7-1 预测**：Holt-Winters 加性季节公式 1:1 对照离线原型 `docs/algo-scripts/s7-parcel.mjs`（同参数 14 点周期序列未来 3 步 = 10/20/30，与原型一致；原型回测 MAPE **5.34%**）。降级链：历史点 < `minSamples`（默认 14 = 2×季节周期）→ MA(7) → 再不足 → 均值恒定预测；空序列/单点/全零均不抛错。
- **S7-2 容量**：`utilization = pendingPickup / shelfCapacity`；`WARN ≥ utilWarn(0.8)`、`CRITICAL ≥ utilCritical(0.95)`；IQR（Tukey 1.5×）识别异常驿站（全站集合计算，与原型 `iqrOutliers` 同口径）。
- **S7-3 分页**：默认 `page.mode=CURSOR`。首页 keyset（`ORDER BY inbound_time DESC, id ASC LIMIT ? OFFSET 0`）；深页 OFFSET 兜底并以 `page.maxOffsetDepth`（默认 1000）限深；显式 `cursor` 入参启用完整 keyset（扫描行恒定 `O(log N + pageSize)`，算法 S7：20 万级 **38 行** vs 深分页 **20020 行**）。
- **索引预期**（服务器实测收敛，db.md U-3）：按驿站+状态 → `idx_parcel_station_status_inbound`、`type=ref`、**无 `Using filesort`**（InnoDB 二级索引隐式追加主键升序，恰配 `inbound_time DESC, id ASC`）；仅按驿站 → `idx_parcel_station_inbound`；运单号精确查 → `idx_parcel_station_waybill`。无 station 维度的全量/仅状态筛选无适配索引，会有 filesort（大表纪律见架构 §4.3）。
- **容量预期**：20 万行约 **105.83MB**（算法 S7），落 `/data` 不足 0.3%（本批不播种数据，运行时空表返回 0/空数组）。

### 契约扩展登记（默认关闭 / opt-in，不改变既有字段语义）
| 扩展 | 载体 | 开关 | 说明 |
| ---- | ---- | ---- | ---- |
| 趋势预测叠加 | `GET /parcels/trend` 出参每点新增 `forecastInbound` | `hrm.algo.parcel.forecast.appendForecast`（默认 false） | 数组长度与既有字段不变；仅附加字段 |
| 容量/热力叠加 | `GET /parcels/ranking` 出参每行新增 `pendingPickup/utilization/capacityLevel/outlier` | `hrm.algo.parcel.capacity.appendToRanking`（默认 false） | 既有 5 字段语义不变 |
| 游标翻页 | 请求可选 `cursor`；响应可选 `nextCursor` | `hrm.algo.parcel.page.exposeCursor`（默认 false） | 默认不出参 `nextCursor`，结构同 Mock |

### 单测（本机无 JDK/Maven，收敛到服务器阶段）
`service/parcel/support/{ParcelForecasterTest, ParcelCapacityAnalyzerTest, ParcelCursorCodecTest, ParcelMetricsTest, ParcelStatusMachineTest}` + `controller/parcel/ParcelRouteOrderTest`。
覆盖边界：预测空序列/单点/全零/样本不足降级/未知模型回退、IQR 四分位边界/全同值/单站/空集合、游标往返/空白/非法 Base64/结构错误/非法 id、比率空态归零、MAE/MAPE 跳过零值、取件状态迁移全分支、字面量路由（summary/trend/ranking）优先于 `{id}`。

### 未验证项（收敛到服务器阶段）
编译打包；Flyway 迁移与快照比对；Redis 会话下端到端越权冒烟（详情跨站 404、取件跨站 7001、非 ADMIN 列表静默收敛）；`EXPLAIN` 索引命中与 `Using filesort` 复核；游标翻页与深分页上限的真实表现；20 万级列表/看板/趋势/排行耗时与落盘容量（`SHOW TABLE STATUS`）。

### TODO(扩展)
- 深分页超 `maxOffsetDepth` 返回空 list（正确 total）为契约外行为，是否改为显式业务码待主智能体裁定。
- 未来 `horizonDays` 预测点作为独立出参暴露需契约裁定；节假日因子（算法 S7 TODO-7）。
- `AlgoProperties.Parcel` 新增键待回填 `algo-hrm-server.md §11.7` 与 `server-architecture.md §5.1`。

---

## 2026-09-24 · 服务端 P9（同步域 22 接口 · 同步任务 5 + 驿站采集配置 4 + 配置中心 13）

### 概述
新增 M9 sync（22 接口）：**同步任务**（列表/日志/详情/触发/重试，状态机四态）、**驿站采集配置**（总览/列表/单站读/单站写，采集状态四态派生）、**配置中心**（配置项与选项集 CRUD + 影响面 + 全局默认读写 + CSV 导入导出）。
**未执行任何 DB 迁移**（`V11__sync_task.sql` / `V12__sync_config_center.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
配置中心实现 **四层模型**：配置项定义 / 选项集（元数据层）→ 全局默认 → 驿站覆盖；生效值 = `覆盖 ?? 全局默认`，并派生 `OVERRIDE/INHERIT` 来源。
**路由顺序复核结论**：Spring Boot 3 `PathPatternParser` 按模式特异性排序（字面量段 > 变量段），`/sync/configs/global|export|import` 天然优先于 `/sync/configs/{stationId}`，**与控制器注册先后无关、无需 @Order**（不沿用 Mock 的注册顺序依赖）。

### 交付
| 域 | 接口（22） | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| 同步任务（5） | `GET /sync-tasks`、`GET /sync-tasks/{id}/logs`、`GET /sync-tasks/{id}`、`POST /sync-tasks/{id}/trigger`、`POST /sync-tasks/{id}/retry` | `controller/sync/SyncTaskController`、`service/sync/{SyncTaskService}+impl/SyncTaskServiceImpl` |
| 驿站采集配置（4） | `GET /sync/overview`、`GET /sync/configs`、`GET /sync/configs/{stationId}`、`PUT /sync/configs/{stationId}` | `controller/sync/SyncConfigController`、`service/sync/{StationSyncConfigService}+impl/StationSyncConfigServiceImpl` |
| 配置中心（13） | `GET/POST /sync/config-items`、`PUT/DELETE /sync/config-items/{itemKey}`、`GET /sync/config-items/{itemKey}/impact`、`POST /sync/config-items/{itemKey}/options`、`PUT/DELETE /sync/config-items/{itemKey}/options/{optionKey}`、`GET .../impact`、`GET/PUT /sync/configs/global`、`GET /sync/configs/export`、`POST /sync/configs/import` | `controller/sync/SyncConfigCenterController`、`service/sync/{SyncConfigCenterService}+impl/SyncConfigCenterServiceImpl` |

实体/映射（同步 7 表）：`entity/{SyncTask, SyncTaskLog, SyncStationConfig, SyncConfigItem, SyncConfigOption, SyncConfigGlobal, SyncConfigStationOverride}` + `mapper/*` 7 个 + JSON 类型处理器 `handler/SyncConfig{Constraints,ExtraAttrs,LegacyCodes}TypeHandler`（复用/扩展 `util/JsonUtil#mapType`）。
纯逻辑支撑（可离线单测，`service/sync/support/`）：`SyncConstants`、`SyncClock`、`SyncValueCodec`、`SyncConfigValidator`、`SyncConfigMerger`、`SyncConfigImpactCalculator`、`SyncConfigStore`、`SyncOptionSet`、`SyncConfigCatalog(+Snapshot)`、`SyncCsvCodec`、`SyncCsvExporter`、`SyncCsvImporter`。
内建种子（幂等 ApplicationRunner，替代种子迁移）：`service/sync/impl/SyncConfigSeeder`。
参数外置：`AlgoProperties.Sync`（`hrm.algo.sync.importRowLimit=1000`、`csvColumnCount=16`，默认与 Mock 等价）；导出走内存流，落盘目录预留 `hrm.storage.export-dir`（数据盘）。

### 单测（本机无 JDK/Maven，收敛到服务器阶段）
`service/sync/support/{SyncConfigMergerTest, SyncConfigValidatorTest, SyncConfigImpactCalculatorTest, SyncCsvCodecTest, SyncCsvExporterTest, SyncCsvImporterTest, SyncCsvRoundTripTest}` + `controller/sync/SyncRouteOrderTest`（路由顺序回归）。
覆盖边界：空配置项 / 无覆盖 INHERIT / 有覆盖 OVERRIDE / 重置键 / CSV 缺列与多列 / 非法记录类型 / 未知冲突策略 / dryRun 不落库 / 内置项删除被拒（9510）/ NUMBER 与 TIME_RANGE 约束边界 / **导出即导入往返一致（文本层 + 语义层双重断言）**。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、Redis 会话下的端到端越权口径（详情/logs 404、trigger/retry 403、configs/{stationId} 404）冒烟、CSV 大文件导出内存占用与真实落盘目录可用性、ApplicationRunner 播种幂等性（重启二次启动无重复行）。

---

## 2026-09-24 · 服务端 P8（工单 9 接口 · 状态机 4 态 + 算法 S6 多目标派单）

### 概述
新增 M8 workorder（9 接口：列表/新建/派单规则读改/企微自动派单/详情/指派/流转/转派）；算法 **S6 工单多目标派单**落地为**加权标量化排序内核**（SLA 紧迫度 × 技能匹配 × 负载均衡），用于 `auto-dispatch` 与 `assign` 的默认处理人推导。
**未执行任何 DB 迁移**（`V10__work_order.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
复用 P2 `NotificationService`（指派/转派 type 1、解决 type 2，`biz_type=work_order`）。**未启用 `auto-dispatch` 公网暴露**（仅实现接口逻辑；公网可达须先经网络安全工程师评估，P0.5）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P8 workorder（9） | `GET/POST /work-orders`、`GET /work-orders/dispatch-rules`、`PUT /work-orders/dispatch-rules/{id}`、`POST /work-orders/auto-dispatch`、`GET /work-orders/{id}`、`PUT /work-orders/{id}/assign`、`PUT /work-orders/{id}/status`、`POST /work-orders/{id}/transfer` | `controller/workorder/WorkOrderController`、`service/workorder/{WorkOrderService}+impl/WorkOrderServiceImpl`、`mapper/{WorkOrderMapper,WorkOrderTimelineMapper,WorkOrderTransferMapper,WorkOrderDispatchRuleMapper}`、`entity/{WorkOrder,WorkOrderTimeline,WorkOrderTransfer,WorkOrderDispatchRule}`、`dto/workorder/*`、`vo/workorder/*` |

纯逻辑支撑（可离线单测）：`service/workorder/support/{WorkOrderConstants, WorkOrderStateMachine, WorkOrderSlaPolicy, WorkOrderAccessPolicy, WorkOrderKeywordClassifier, WorkOrderDispatchScorer}`。

### 关键实现口径（对齐 Mock `routes/workOrder.js` / `db.js` / `dict.js`）
- **状态机 4 态**：`TRANSITIONS={0:[1,3],1:[2],2:[1,3],3:[]}` 集中于 `WorkOrderStateMachine`（表驱动）；非法流转 8001；动作名 `3→close`/`2→resolve`/`1→accept`（严格对齐 Mock 三元表达式）。
- **SLA（Q4 未裁定）**：阈值外置 `hrm.algo.dispatch.slaHours`（默认 `{0:48,1:24,2:8}` 与现状逐位等价）+ 键缺失兜底 `defaultSlaHours=24`；「超时未处理」= **仅未处理完（0/1）且 now 严格晚于 deadline**（终态不计，恰好到点不算）；出参同给 `overdueUnhandled` 与旧别名 `overSla`。
- **S6 多目标派单**：`score(e)=wU·urgency·readiness + wS·skill + wL·load + wSp·readiness`（与原型逐式一致）；权重外置 `hrm.algo.dispatch.weights.*`（默认 4.0/2.5/1.5/2.0）；信号来源：技能画像（`skillWindowDays` 内已了结工单类型占比，样本 < `minSkillSamples` 按 0）、负载（`loadWindowDays` 内接单量归一）、就绪度（未完成量 × `estimatedServiceHours` 相对 SLA）。**并列保序取先出现者**；**权重为负拒绝**；**无候选 → 不指派（转人工），不阻塞建单**。
- **关键词规则与多目标排序的关系**：`dispatchRules` 仍负责「关键词 → 类型/优先级」判定（默认**顺序命中**，与 Mock 逐位等价；特异度加权（12 条歧义样本 0.8333→1.0000）由 `hrm.algo.dispatch.keywordWeighted` 开关控制，默认关）；S6 多目标排序只作**处理人选择**内核，规则未命中仍走兜底 `{type:4, priority:1}`。
- **越权口径逐端点**（架构 §6.2 P8，不得统一）：`GET /{id}` 跨站 → **404**；`assign`/`status` 无权 → **8002**；`transfer` 无权 → **8003**；`transfer` 对象非法（不存在/停用/转自己/**非 ADMIN 跨站**）→ **8004**；规则不存在 → **8005**；群消息空 → **8006**。
- **时间线**：`work_order_timeline` 记录 `create/assign/accept/resolve/close/transfer/auto_dispatch`，出参按 `time ASC, id ASC` 组装回 `handleLog[]`（替代 Mock 的 `handle_log` 内嵌 JSON，顺序与 Mock 一致）；转单另落 `work_order_transfer`（详情 `transfers[]` 按转单时间倒序）。
- **单号**：`WO-yyyyMMdd-####`；服务端以「插入取得自增 id 后回填单号」等价 Mock 的内存序号拼号（order_no 无唯一索引）。
- **通知联动**：建单指派/指派/转单 → type 1「工单指派 / 工单转单」；解决 → type 2「工单流转」通知上报人（无上报人则跳过）。
- **路由字面量优先**：`/work-orders/dispatch-rules`、`/work-orders/auto-dispatch` 优先于 `/work-orders/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `WorkOrderRouteOrderTest`）。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **`hrm.algo.dispatch.*` 新增 6 键**：`defaultSlaHours` / `loadWindowDays` / `skillWindowDays` / `minSkillSamples` / `estimatedServiceHours` / `keywordWeighted`（默认值取算法方案默认组，与 Mock 状态等价）；`algo-hrm-server.md §11.6` 与 `server-architecture.md §5.1` 的 dispatch 段尚未登记，待回填。
2. **`auto-dispatch` 处理人**：Mock 规则 `default_assignee_id` 全空 → 现状「只定类型/优先级、不指派」；本批规则为空时改由 S6 多目标排序推导（无候选仍为空）。
3. **`assign` 缺省处理人**：Mock 在 `assigneeId` 缺失时回 400；本批非空入参行为不变，缺失时先按 S6 推导，推导无果仍回 400（新增能力，不影响既有路径）。
4. **`auto-dispatch` 时间字段**：`create_time` 以消息发送时间为准（SLA 起算口径一致）；`update_time` 由框架自动填充刷新为入库时间（与 Mock 的 sendTime 有毫秒级差异，不影响任何判定）。
5. **`PUT dispatch-rules/{id}` 清空 `defaultAssigneeId`**：受 JSON「字段缺失 vs 显式 null」无法区分限制，本端点 null 视为「不改」（种子规则本为 null，实际影响可忽略）——`TODO(扩展)` 待前端约定清空哨兵值。

### 边界声明
- 本轮仅改 `hrm-dev/hrm-server/**`（新增 workorder 域 30 个主源码 + 6 个单测，改 `config/AlgoProperties`、`resources/application.yml` 的 `hrm.algo.dispatch` 段）。
- `hrm-demo/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md`/`plan.md`/`server-architecture.md`/`algo-hrm-server.md`/迁移脚本/`.trae/rules/` 零改动。
- 未运行项（须服务器复核）：编译、Flyway V10 执行、9 接口端到端冒烟、S6 基准复现（原型 78.3%→100% SLA / 9.447h→1.504h）、6 个单测执行。
- 待裁决：`algo-hrm-server.md §2` 的 **Q4（SLA 阈值 48/24/8）与 Q5（三目标权重）**；裁定前默认值与现状等价。


## 2026-09-24 · 服务端 P7（请假 13 接口 · 两级审批 6 态 + 算法 S5 半天单元区间）

### 概述
新增 M7 leave（13 接口：申请/试算/我的/列表/设置/详情/编辑/撤销/重提/初审/终审/撤回）；算法 **S5 请假计薪天数与重叠判定**落地为**半天单元整数区间 + 二分**（逐日扫描 → 区间查询）。
**未执行任何 DB 迁移**（`V9__leave.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
接入 P6 账期锁只读端口 `PayrollLockQueryService`（撤回校验 9606）；向 P6 财务域暴露只读端口 `ApprovedLeaveDaysPort`（已批请假天数 + 扣款开关），替换 `PayrollContextProvider` 预留的 `TODO(扩展)`。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P7 leave（13） | `POST /leave`、`POST /leave/preview`、`GET /leave/my`、`GET /leave/list`、`GET/PUT /leave/settings`、`GET /leave/{id}`、`PUT /leave/{id}`、`POST /leave/{id}/cancel`、`POST /leave/{id}/resubmit`、`POST /leave/{id}/station-approve`、`POST /leave/{id}/final-approve`、`POST /leave/{id}/revoke` | `controller/leave/LeaveController`、`service/leave/{LeaveService}+impl/LeaveServiceImpl`、`service/leave/port/{ScheduledDatesQuery}+impl/{LeaveScheduledDatesQueryImpl,ApprovedLeaveDaysPortImpl}`、`mapper/{LeaveRequestMapper,LeaveLogMapper,LeaveSettingMapper}`、`entity/{LeaveRequest,LeaveLog,LeaveSetting}`、`dto/leave/*`、`vo/leave/*` |

纯逻辑支撑（可离线单测）：`service/leave/support/{LeaveConstants, LeaveIntervalPolicy（S5 算法）, LeaveUnitRange, OccupiedLeaveInterval, LeaveStateMachine, LeaveAccessPolicy}`。

### 关键实现口径（对齐 Mock `leaveStore.js` / `routes/leave.js`）
- **6 态状态机**：`SUBMIT/UPDATE/RESUBMIT/CANCEL/STATION_APPROVE|REJECT/FINAL_APPROVE|REJECT/REVOKE` 迁移矩阵集中 `LeaveStateMachine`；初始态判定 = STAFF 有站长 → 待初审、站长本人或无站长 → 待终审（T1 + Q4 降级共用一条判定）。
- **S5 区间算法**：`unitIndex(date, period)=epochDay×2+(AM?0:1)`；自然天数 O(1)；计薪天数在「排班单元升序数组」上两次二分 O(log m)；重叠用区间相交（有序集合上 O(log k)）；账期跨度 O(1)。**与逐日法 0 结果差异**（单测等价性断言 + S5 原型对照）。
- **计薪天数**：`SCHEDULED` 逐日查排班（复用 `attendance_schedule`），`NATURAL` 与排班无关；假别 → countMode 唯一真源 `hrm.algo.leave.countModeMap`（默认逐位等于 Mock `dict.LEAVE_TYPE`）。
- **重叠校验**：日级粗筛（走 `idx_leave_request_date`，同员工 + 占用态 + 区间相交）→ 半天单元精确判定；占用态集 `hrm.algo.leave.occupiedStatus`。
- **越权口径**：跨站/审自己/ADMIN 提交 → 9605；仅状态不匹配 → 9602；编辑非待初审 → 9607；列表/我的/详情按角色收敛（ADMIN 全量 / 站长本站 / 本人）。
- **账期锁（9606）**：撤回前逐月问 `PayrollLockQueryService`（口径 `NON_DRAFT_REJECTED`），命中即以「{月} 工资单已生成」回 9606。
- **留痕**：`leave_log` 记录 action/operator/fromStatus/toStatus/before/after/remark，出参 `handleLog[]` 与 Mock 同序；通知目标缺失留 `NOTIFY_SKIP`。
- **通知**：申请/结果走 P2 `NotificationService.sendSystem`（type 5/6、`biz_type=leave`），开关 `hrm.algo.leave.notifyEnabled`（默认 true）。
- **路由字面量优先**：`/leave/preview|my|list|settings` 优先于 `/leave/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `LeaveRouteOrderTest`）。

### 跨域端口（入 + 出）
- **入（P6 → P7）**：复用 P6 的 `PayrollLockQueryService`（只读），用于撤回账期锁判定。
- **出（P7 → P6）**：`service/finance/port/ApprovedLeaveDaysPort`（财务域声明接口、请假域实现，依赖倒置断环）：
  `approvedLeaveDays(employeeId, startDate, endDate)`（已批请假计薪天数，半天粒度，跨月由调用方按账期切分）与 `leaveDeductEnabled()`。
  请假域实现 `service/leave/port/impl/ApprovedLeaveDaysPortImpl`；财务域降级兜底 `service/finance/port/impl/{UnavailableApprovedLeaveDaysPort,FinanceLeavePortConfig}`（`@ConditionalOnMissingBean`，同 P5 手法）。
- **财务域最小接线（改动点）**：`service/finance/impl/PayrollContextProvider` 接入端口，`absentCount = max(0, 应到 − 实到 − excludeLeaveDays)`、`excludeLeaveDays = leaveDeductEnabled ? 0 : leaveDays`；`service/finance/support/AttendanceStat` 的 `absentCount`/`leaveCount` 由 `int` 扩为 `BigDecimal`（半天粒度无法用整数无损承载），`AttendanceItemResolver` 随之改用 `BigDecimal` + `PayrollNumberFormat.plain`（文案不变）。**财务域其余逻辑零改动。**

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **`GET /leave/settings` 角色口径**：Mock 限 ADMIN；`api.md §7.1` 记「不限角色」。按主智能体裁决**以 Mock（ADMIN）为准**，待 api.md 收口（已登记 §待收口）。
2. **`hrm.algo.leave.*` 新增 3 键**：`countModeMap` / `deductEnabledDefault` / `notifyEnabled`（默认与 Mock 逐位等价）；`algo-hrm-server.md §11.5` 与 `server-architecture.md §5.1` 的 leave 段尚未登记，待回填。
3. **扣款开关落库**：`leave_setting` 单行表首次写入才插入；无行时按 `deductEnabledDefault`（=false，Q6 未裁定）。V9 未种入初始行，行为与 Mock 默认一致。

### 边界声明
- 本轮仅改 `hrm-dev/hrm-server/**`：新增 leave 域（controller/service/mapper/entity/dto/vo/support + 2 个端口实现 + 4 个单测），改 `config/AlgoProperties`、`resources/application.yml`、`service/finance/**` 3 文件 + `PayrollItemResolverTest` 夹具 1 处。
- `hrm-demo/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md`/`plan.md`/`server-architecture.md`/`algo-hrm-server.md`/迁移脚本/`.trae/rules/` 零改动。
- 未运行项（须服务器复核）：编译、Flyway V9 执行、13 接口端到端冒烟、S5 基准复现。
- 待裁决：`algo-hrm-server.md §2` 的 Q6（请假扣款开关 + 连续缺卡阈值）。

## 2026-09-24 · 服务端 P6（财务 15 接口 · 工资单六态 + 算法 S2 注册表 + P5 端口接入）

### 概述
新增 M6 finance（15 接口：计薪规则 5 / 工资单 10）；算法 **S2 工资试算口径引擎**落地为**来源解析器注册表**（FIXED/ATTENDANCE/KPI/MANUAL，表驱动，新增来源不改核心代码）。
**未执行任何 DB 迁移**（`V8__payroll.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
接入 P5 人事域跨域端口 `PayrollSettlementPort`（提供真实 Bean，使降级实现自动失效）；向 P7 暴露账期锁只读端口 `PayrollLockQueryService`。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P6 finance（15） | 规则 `GET/POST /finance/payroll-rules`、`GET/PUT/DELETE /finance/payroll-rules/{id}`；工资单 `GET /finance/payrolls/my`、`POST .../generate|submit|publish`、`GET /finance/payrolls`、`GET /finance/payrolls/{id}`、`PUT .../{id}/items`、`POST .../{id}/approve|confirm|objection` | `controller/finance/{PayrollRuleController,PayrollController}`、`service/finance/{PayrollRuleService,PayrollService,PayrollLockQueryService}+impl/{PayrollRuleServiceImpl,PayrollServiceImpl,PayrollLockQueryServiceImpl,PayrollContextProvider}`、`service/finance/port/PayrollSettlementAdapter`、`mapper/{PayrollRuleMapper,PayrollRuleItemMapper,PayrollMapper,PayrollItemMapper}`、`entity/{PayrollRule,PayrollRuleItem,Payroll,PayrollItem}`、`handler/PayrollItemParamsTypeHandler`、`dto/finance/*`、`vo/finance/*` |

纯逻辑支撑（可离线单测）：`service/finance/support/{PayrollItemResolver + FixedItemResolver/AttendanceItemResolver/KpiItemResolver/ManualItemResolver/PayrollResolverRegistry, PayrollItemParamAccessor, PayrollCalcContext, AttendanceStat, PayrollSalaryField, PayrollTotals(+Policy), PayrollItemDraft, PayrollStateMachine, PayrollGenerateGuard, PayrollLockPolicy, PayrollRuleValidator, PayrollNoGenerator, PayrollSnapshotBuilder, PayrollNumberFormat, PayrollStatus/PayrollSource/PayrollItemType/PayrollBillType}`。

### 关键实现口径（对齐 Mock `financeStore.js` / `routes/finance.js`）
- **S2 注册表**：构造时收集容器内全部 `PayrollItemResolver` 注册为 `Map<source, resolver>`；未知来源按 `hrm.algo.payroll.unknownSourcePolicy`（默认 ZERO → 按 0 计并 warn，不中断整批）。
- **四类来源公式**与 Mock 逐式一致：FIXED（定薪字段/津贴合计/指定津贴项）、ATTENDANCE（PER_COUNT×单价+`cap>0` 封顶；BONUS_IF_ZERO 次数为 0 才发）、KPI（`round(绩效基数 × clamp(KPI/100, 0, capRatio))`）、MANUAL（`defaultValue`）；解释文案逐字对齐 Mock。
- **合计**：`grossAmount = 增项合计`；`netAmount = 增项 − 扣项`。**Q3 未裁定** → 默认与 Mock 一致（**允许负净额、不钳制**）。
- **参数外置**：`hrm.algo.payroll.*`（`defaultCapRatio=1.0`、`unknownSourcePolicy=ZERO`、`allowNegativeNet`、`itemCapSemantics=ZERO_MEANS_NO_CAP`、`attendanceFieldMap`）。**种子 `capRatio=1.2` 来自规则数据，代码不写死**。
- **幂等（9405）**：同月任一月度单为非 DRAFT/REJECTED → **整批拒绝**（回填阻断单据号）；草稿/驳回 → 覆盖重建（删旧单+明细再生成）。
- **六态状态机**：动作矩阵与 Mock `PAYROLL_ACTIONS` 逐条一致；`submit/approve/reject/publish/confirm/objection` 非法流转 → 9403（文案含状态中文）。
- **数据隔离**：`payrolls/my` 只返回本人且仅 PUBLISHED/CONFIRMED；`payrolls/{id}` 非 ADMIN 仅本人（他人 9404、未发布 9403）。
- **规则快照**：`payroll.rule_snapshot` 存算薪时的规则与规则项（含停用项），保证历史可解释；**不出现在 API 出参**（与 Mock 一致）。
- **路由字面量优先**：`payrolls/my|generate|submit|publish` 优先于 `payrolls/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `PayrollRouteOrderTest`）。

### 跨域端口（入 + 出）
- **入（P5 → finance）**：`service/finance/port/PayrollSettlementAdapter` 实现人事域 `PayrollSettlementPort`，`createSettlement` 委托财务服务创建 `billType=SETTLEMENT` 草稿单（同员工同月幂等），返回 `{payrollId, payrollNo, amount=netAmount}`；注册后 `HrPortConfig` 的降级 Bean 经 `@ConditionalOnMissingBean` 自动失效，**人事域代码零改动**。
- **出（finance → P7）**：`PayrollLockQueryService`（`isMonthLocked(employeeId, month)` / `findLockingPayroll(...)`），口径 `NON_DRAFT_REJECTED`（走 `hrm.algo.leave.lockStatusPolicy`），与 Mock `findLockingPayroll` 一致（不限单据类型）。
- **上游只读**：`PayrollContextProvider` 经 P5 服务层 `HrSalaryWriter.selectByEmployeeId` 读定薪；读 P3 `attendance_record/schedule`（复用 `AttendanceConstants.isValidCard`）与 P4 `kpi_score.total_score`。依赖方向恒为 `finance → hr/attendance/kpi`，无环。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **列表计数的员工姓名字段**：Mock 单表内嵌 `employeeName`；服务端 JOIN 员工表组装，`keyword` 命中姓名时先查员工 id 集再过滤（出参同形）。
2. **`amount` 宽松强转**：Mock `Number(item.amount)` 对 `undefined`/非数字给业务文案；服务端 `PayrollItemAdjustRequest.amount` 声明为 `Object` 并按 JS 语义强转（`null`/空串→0，非数字→「金额须为数字」）。缺失字段（Java 无法区分「未传」与「显式 null」）按 0 处理，属低优先差异。
3. **`rule_snapshot`**：Mock 无该字段，为 db.md §8.6.3 新增（历史可解释），仅内部留存、不出参。
4. **规则项 `id`**：Mock 为「每规则内 index+1」，服务端为全局自增；前端编辑整体覆盖、不依赖其稳定。

### 配置修正（1 处，见返回摘要「事实性纠正」）
`hrm.algo.payroll.allowNegativeNet` 默认由 `false` 改为 **`true`**（`AlgoProperties` + `application.yml`）：`algo-hrm-server.md §11.2` 记 false（标注待 Q3 确认）与 P6 验收「默认与 Mock 一致（允许）」冲突；按后者落实为 Mock 等价行为，Q3 裁定后改回 false 即切换、无需改算力代码。

### 验证状态
本机无 JDK/Maven：**未编译、未跑测**。纯逻辑单测已就位（解析器/合计/状态机/幂等与账期锁/校验器/路由顺序），**收敛到服务器阶段执行**，并与 `docs/algo-scripts/s2-payroll.mjs` 逐例对照（公式与边界一致；原型与 Mock 的说明文案本就不同，本批以 **Mock 契约**为准）。

## 2026-09-24 · 服务端 P5（人事 16 接口 · 入离职流程 + 跨域断环端口）

### 概述
新增 M5 hr（16 接口：档案 3 / 定薪 3 / 入职 5 / 离职 5）；复用 P0 地基（`@RequireRoles` / `PageQuery` / `ErrorCode` 93xx / `FieldValidator` / `PasswordUtil` / `DesensitizeUtil`）。
**未执行任何 DB 迁移**（`V7__hr.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
离职 `SETTLEMENT` 步骤经**跨域端口**（依赖倒置断环）创建财务结算单，P6 未就绪时降级为业务异常（9306）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P5 hr（16） | 档案 `GET/PUT /hr/profiles[/{employeeId}]`、定薪 `GET/PUT /hr/salary-structures[/{employeeId}]`、入职 `GET/POST /hr/onboarding`、`GET /hr/onboarding/{id}`、`POST .../{id}/steps/{key}/complete`、`POST .../{id}/reject`、离职同构 5 个 | `controller/hr/{HrProfileController,HrFlowController}`、`service/hr/{HrProfileService,HrFlowService}+impl/{HrProfileServiceImpl,HrFlowServiceImpl,HrSalaryWriter}`、`mapper/{HrProfileMapper,HrSalaryMapper,HrSalaryLogMapper,HrFlowMapper,HrFlowStepMapper}`、`entity/{HrProfile,HrSalary,HrSalaryLog,HrFlow,HrFlowStep,HrAllowance}`、`handler/HrAllowanceListTypeHandler`、`config/HrProperties`、`dto/hr/*`、`vo/hr/*` |

纯逻辑支撑（可离线单测）：`service/hr/support/{HrConstants,HrFlowStepGuard,HrProfileValidator,HrSalaryValidator,HrSalaryCalculator,HrValidateSupport}`。

### 关键实现口径（对齐 Mock）
- **步骤序真源**：`HrConstants.ONBOARDING_STEPS` = SUBMIT_MATERIALS→HR_REVIEW→CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY→DONE；`OFFBOARDING_STEPS` = MANAGER_APPROVE→HR_APPROVE→HANDOVER→ASSET_RETURN→SETTLEMENT→LEAVE，与 Mock 常量逐条一致。
- **按序守卫**：`HrFlowStepGuard.checkOrder` 逐条复刻 Mock `completeStep` 判序与文案（非进行中 / 步骤不存在 / 重复办理 / 跳步），**跳步一律拒绝**，错误码 9303/9304。
- **建档**：账号正则 + 强密码（`FieldValidator`）；部门不存在 3001、驿站不存在 4001、停用 4004；员工先落 `status=0`、`pwd_changed=0`、`entry_date=预计入职日期`；同步落 0 值定薪行（否则卡在定薪步骤）；**仅 DONE 步骤置 `status=1`**；驳回时已建档员工一并禁用。
- **离职判定真源**：`hr_profile.leave_date`（非 `employee.status`）；`HrSalaryWriter.isResigned` 与建档/定薪复用同一判据。
- **离职流程**：发起须在职且无进行中流程（`status!=1` → 9302；重复 → 9304）；`SETTLEMENT` 经端口创建结算单并回填引用；**无结算单不得离岗**（9306）；离岗后员工 `status=0` + 写 `profile.leave_date`。
- **定薪**：保存 = 覆盖当前档案 + 追加调薪留痕（只增不改），`total = basic + post + performanceBase + allowancesTotal`；已离职拒 9302、无档案 9305。**调薪属计费口径**，本批仅沿用 Mock 口径，未引入新规则。
- **越权**：档案详情/定薪详情非本人非 ADMIN → 403（对齐 Mock `canAccessEmployee`）。
- **参数外置**：试用期默认月数、默认合同期限走 `hrm.hr.*`（`HrProperties`，默认 3/3，与 Mock 一致），未硬编码。

### 跨域端口设计（ADR-03 断环，P6 接入点）
- `service/hr/port/PayrollSettlementPort`（`createSettlement(PayrollSettlementCommand) → PayrollSettlementRef`），人事域只依赖自身 port 包，财务域（P6）注册实现即完成接入，依赖方向恒为 `finance → hr`，无环。
- P6 未就绪时 `HrPortConfig` 以 `@ConditionalOnMissingBean` 装配降级实现 `UnavailablePayrollSettlementPort`（抛 9306「财务域未就绪」）；P6 提供真实 Bean 后降级自动失效，**无需改人事域代码**。
- `TODO(扩展)` 标注：P6 落地后可另加 `OffboardingSettlementRequired` 领域事件（AFTER_COMMIT）做补偿/重试。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **密码存储**：Mock 存明文，服务端一律 BCrypt（`PasswordUtil.hash`）。
2. **定薪操作人**：Mock `salaryForFlow` 误用「流程创建人」为定薪留痕操作人；服务端改为**当前办理人**（谁办理谁留痕）。
3. **档案编辑 null 语义**：Mock 可用显式 `null` 清空字段；Java 无法区分「未传」与「显式 null」，改为**仅写非 null 字段**（需清空传空串），空请求体不会误清空档案（与 P4 同类处理一致）。
4. **列表不加 status 过滤**：对齐 Mock `activeEmployees`（仅排除逻辑删除），禁用/在册员工档案仍可管理；列表返回不含 `salary` 键（详情才带）。

### 跨域：离职 SETTLEMENT 策略选择（待主智能体裁定，见返回摘要「事实性纠正」）
架构 §2.3 该行记「领域事件」，但 Mock 契约要求 SETTLEMENT 步骤**同步**回填 `settlementPayrollId/No/Amount`；`@TransactionalEventListener(AFTER_COMMIT)` 为提交后异步，无法满足。本批以「跨域端口 + 服务层直调」实现同等断环且保持同步语义。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`HrFlowStepGuardTest`（空流程/跳步/重复办理/步骤不存在/非进行中/离职离岗首步）、`HrProfileValidatorTest`（字典/日期先后/试用期边界/金额与敏感字段）、`HrSalarySupportTest`（津贴合计与定薪合计口径、保存 vs 步骤两态校验）、`HrSalaryWriterTest`（无档案 9305 / 已离职 9302 / 覆盖+留痕合计）、`HrProfileServiceAccessTest`（员工查他人档案/定薪 403、本人与 ADMIN 放行）、`HrFlowServiceStepTest`（入职跳步 9303 / 离职跳步 9304 / 无结算离岗 9306 / 结算端口未就绪 9306 / 流程不存在 404 文案）、`HrRouteOrderTest`（静态段与变量段互不遮蔽）。

### 事实性纠正 / 遗留
- `api.md` 无人事章（P5 契约真源为 Mock + 架构 §6.2 P5）；本批按指令**未改 `api.md`**，契约更新待主智能体授权。
- `V7__hr.sql` **无人事种子数据**（Mock 有档案/定薪/流程种子），服务端上线后列表为空，需数据库工程师补数据迁移或经流程/建档产生数据。
- 通知联动：Mock `hrStore/routes` **未接入通知域**，故本批不调用 `NotificationService`（避免无依据的过度实现）。

## 2026-09-24 · 服务端 P4（KPI 考核 9 接口 · 算法 S1 落地）

### 概述
新增 M4 kpi（9 接口：指标配置 5 / 算分与查询 4）；落地算法 S1（达成率 + 三模式单项得分 + 加权归一总分 + 竞赛排名 + 等级映射 + 权重守卫 + 可选分位映射）；
复用 P0 地基（`@RequireRoles` / `StationScopedQuery` / `PageQuery` / `ErrorCode` 92xx / `AlgoProperties`）与 P3 考勤能力，**未新增依赖**。
**未执行任何 DB 迁移**（`V6__kpi.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P4 kpi（9） | `GET /kpi/metrics`（ADMIN）/ `POST /kpi/metrics`（ADMIN）/ `PUT /kpi/metrics/batch`（ADMIN）/ `PUT /kpi/metrics/{id}`（ADMIN）/ `DELETE /kpi/metrics/{id}`（ADMIN）/ `POST /kpi/scores/calculate`（ADMIN）/ `GET /kpi/scores/ranking`（ADMIN,STATION_ADMIN）/ `GET /kpi/scores`（同）/ `GET /kpi/scores/{employeeId}`（ALL，越权 403） | `controller/kpi/{KpiMetricController,KpiScoreController}`、`service/kpi/{KpiMetricService,KpiScoreService,KpiActualValueSource}+impl`、`mapper/{KpiMetricMapper,KpiScoreMapper}`、`entity/{KpiMetric,KpiScore}`、`handler/KpiMetricDetailListTypeHandler`、`dto/kpi/*`、`vo/kpi/*` |

纯逻辑支撑（可离线单测）：`service/kpi/support/{KpiConstants,KpiAchievementPolicy,KpiScorePolicy,KpiRankingPolicy,KpiWeightGuard,KpiQuantileMapper,KpiSimulatedData}`。

### S1 算法落地（参数全外置，禁硬编码）
- 参数键全部取 `hrm.algo.kpi.*`（`linearCapRatio` / `tieredTiers` / `levels` / `quantile.{enabled,tiePolicy,minSamples}` / `weightSumTarget` / `weightSumTolerance`），与 `algo-hrm-server.md` §11.1 **逐键一致，未新增键**。
- **默认值逐位等价现状**（因 Q1/Q2 未裁定）：`tieredTiers`={1,0.9,0.8,0.6,0}、`levels`=90/80/70/0、`linearCapRatio`=1.0、`quantile.enabled`=**false**、`weightSumTarget`=100、`weightSumTolerance`=0。故「上线即无行为变化」。
- **阶梯参数化 + 分位开关**：TIERED 改为查 `tieredTiers`（降序，首个 `达成率 ≥ minAchievement`），分位 QUANTILE 为**全局开关**（非单指标模式，与 V6 列枚举 `LINEAR/TIERED/BINARY` 一致），默认关闭；开启且样本量 ≥ `minSamples` 时按同 scope 员工分布取百分位，否则回落绝对评分。
- **Node 原型对照（本机实跑 `node s1-kpi.mjs`）**：等价性回归 **1200 项 0 不一致**；边界实测 LINEAR(cap=1) 500%→100、LINEAR(cap=1.2)→120、TIERED→100；`target=0` 四例（DOWN 0/0→1、DOWN 0/2→0、UP 0/0→0、UP 0/5→1）；全同值分位 MID_RANK=0.5 / MIN=0 / MAX=1；权重守卫 100 通过、105 拒绝、空集不校验。Java 侧 `KpiScorePolicyTest`/`KpiAchievementPolicyTest`/`KpiQuantileMapperTest`/`KpiWeightGuardTest` 以此为期望值断言。
- **模拟数据源逐位等价**：`KpiSimulatedData` 复刻 Mock `kpiStore` 的 FNV-1a + mulberry32 + `actualValueOf`；本机实跑 Node 参考脚本得 `PARCEL#1#2026-09=919`、`PICKUP_TIMELY#1#2026-09=89.1`、`COMPLAINT#1#2026-09=0`、`ATTENDANCE#1#2026-09=92.4`、`SERVICE#1#2026-09=5`、`WORK_ORDER#2#2026-09=19`，Java 单测逐一断言。

### 与 Mock 的有意差异（契约出参/错误码不变）
1. **考勤类指标取数改走 P3 真实明细**（主智能体 P4 指令第 8 条）：`metricType=ATTENDANCE` 的 `actualValue` 由 `KpiAttendanceMetricSource` 以「实到天数/应到天数×100」统计（复用 P3 的 `AttendanceConstants.isValidCard` 有效卡判据，不重写考勤统计），**不再走哈希模拟**；故 ATTENDANCE 指标得分与 Mock 演示值不逐位相等。其余类型（PARCEL/PICKUP/…）业务表未接，仍走确定性模拟（标 `TODO(扩展)`，入口收敛在 `KpiActualValueResolver`）。
2. **算分幂等=覆盖重建**：`kpi_score` 一员工一账期一行（架构 §4.2），重算整行替换 `metric_detail` 快照，等价 Mock 的「更新适用指标行 + 删除已停用指标旧行」，重复算分结果恒定。
3. **批量保存原子化**：`PUT /kpi/metrics/batch` 一次校验、全部成功或全部失败（错误码与文案逐条对齐 Mock）。
4. **`month` 收紧为非空**：`calculate` 要求 `month` 非空（Mock `isMonth` 对空值放行会落 `month=null` 脏数据，属潜在缺陷）；查询类 `month` 空值回落当月（对齐 Mock `monthOf`）。
5. **JSON `null` 语义差异**：Mock 可用显式 `null` 清空 `roleScope`/`remark`；Java POJO 无法区分「未传」与「显式 null」，故编辑时二者均按「不改」处理（低频边角，见返回摘要遗留项）。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`KpiAchievementPolicyTest`（UP/DOWN、target=0、target 缺失、>1 与 =1）、`KpiScorePolicyTest`（三模式、线性封顶、单指标、加权归一、等级阈值）、`KpiWeightGuardTest`（空集/99/100/101/容差）、`KpiRankingPolicyTest`（竞赛排名 1-1-3 / 1-2-2、排序键）、`KpiQuantileMapperTest`（全同值三策略 0.5/0/1、n=1、混合并列平均秩）、`KpiSimulatedDataTest`（六项 Node 参考值逐位等价、确定性、值域）、`KpiRouteOrderTest`（`metrics/batch` 与 `scores/ranking` 字面量优先）。

### 事实性纠正 / 遗留
- `api.md` 无 KPI 章（P4 契约真源为 Mock + 架构 §6.2 P4）；本批按指令**未改 `api.md`**，契约更新待主智能体授权。
- `V6__kpi.sql` **无 `kpi_metric` 种子数据**（Mock 有 7 项），服务端上线后指标列表为空、算分回 9203，需数据库工程师补数据迁移或 ADMIN 手工新增。

## 2026-09-24 · 服务端 P3（考勤与排班 22 接口 · 算法 S3/S4 落地）

### 概述
新增 M3 attendance（22 接口：规则 3 / 状态+打卡 2 / 记录 4 / 我的 1 / 补卡 4 / 排班 4 / 班次 4）；
落地算法 S3（排班生成：贪心构造 + 模拟退火）与 S4（异常检测：稳健 z + 连缺游程）；
复用 P0 地基（`@RequireRoles` / `StationScopeQuery(StationScopedQuery)` / `PageQuery` / `ErrorCode` 91xx / `AlgoProperties`），**未新增依赖**。
**未执行任何 DB 迁移**（`V5__attendance.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P3 attendance（10） | `GET /attendance/rule`（ALL）/ `GET /rule/list`（ADMIN）/ `PUT /rule`（ADMIN）/ `GET /status`（ALL）/ `POST /check-in`（ALL）/ `GET /records`（ADMIN,STATION_ADMIN）/ `GET /export`（同）/ `GET /summary`（同）/ `GET /detail`（同）/ `GET /my`（ALL） | `controller/attendance/AttendanceController`、`service/attendance/{AttendanceRuleService,AttendanceRecordService}+impl`、`mapper/Attendance{Rule,Shift,Schedule,Record,Makeup}Mapper`、`entity/{AttendanceRule,AttendanceShift,AttendanceSchedule,AttendanceRecord,AttendanceMakeup,CheckPeriod,WifiEntry}`、`handler/{CheckPeriodListTypeHandler,WifiEntryListTypeHandler}`、`util/JsonUtil`、`dto/attendance/*`、`vo/attendance/*` |
| P3 makeup（4） | `GET /attendance/makeup/my`（ALL）/ `GET /makeup/list`（ADMIN）/ `POST /attendance/makeup`（ALL）/ `POST /makeup/{id}/approve`（ADMIN） | `controller/attendance/AttendanceMakeupController`、`service/attendance/AttendanceMakeupService+impl` |
| P3 schedule（4） | `GET /schedules`（ADMIN,STATION_ADMIN）/ `GET /schedules/my`（ALL）/ `POST /schedules/batch`（ADMIN）/ `POST /schedules/batch-by-station`（ADMIN） | `controller/attendance/ScheduleController`、`service/attendance/AttendanceScheduleService+impl` |
| P3 shift（4） | `GET /shifts`（ALL）/ `POST /shifts`（ADMIN）/ `PUT /shifts/{id}`（ADMIN）/ `DELETE /shifts/{id}`（ADMIN） | `controller/attendance/ShiftController`、`service/attendance/AttendanceShiftService+impl` |

纯逻辑支撑（可离线单测）：`service/attendance/support/{HaversineCalculator,AttendancePeriodResolver,AttendanceCheckPolicy,AttendanceSummaryPolicy,AttendanceDetailPolicy,SchedulePlanner,AttendanceAnomalyDetector,CsvSupport,AttendanceSupport,AttendanceConstants,AttendanceCard}`。

### S3 算法落地（参数全外置，禁硬编码）
- 选型：**贪心构造（轮休错峰 + 最短缺优先） + 模拟退火（同日班次互换 / 在岗↔轮休对调）**；固定迭代数与固定种子（可复现，禁用「时间到即停」）。
- 参数全部取 `hrm.algo.schedule.*`（`minPerShift / maxConsecutiveWork / restCycleDays / weights.{minStaff,consecutive,coverageDeficit,shiftBalance,restSpread} / sa.{iterations,initialTemp,cooling}`），与 `algo-hrm-server.md` §11.3 逐键一致，未新增键。
- **失败降级**：无可行解/算法异常 → 返回贪心解 + 违规清单（`MIN_STAFF` / `CONSECUTIVE`），**不失败**（`SchedulePlanner.Plan.fallback`）。
- Node 原型实测（`node s3-schedule.mjs`，本机实跑）：基线朴素轮转 覆盖率 **0.7778**、最少在岗违规 **20/90**、J=20266.667；贪心 覆盖率 **1.0**、违规 **0**、J=8.667；贪心+退火 覆盖率 **1.0**、违规 **0**、J=**2.667**。Java 侧 `SchedulePlannerTest` 断言「贪心覆盖率 1.0 且违规 0」「退火 J ≤ 贪心 J」「同种子逐位一致」。

### S4 算法落地（参数全外置）
- 选型：**稳健 z（median/MAD，1.4826 归一） + 连缺游程**；参数取 `hrm.algo.attendance.anomaly.*`（`useRobust/lateWarn/lateCritical/consecutiveAbsent/minSamples/windowDays`），与 §11.4 逐键一致。
- 降级：样本量 < `minSamples` → 整体空集；稳健尺度（1.4826·MAD）=0 → 「迟到频次」检测器空集（连缺为阈值型检测器，不受 MAD 影响）。
- Node 原型实测（`node s4-anomaly.mjs`，本机实跑）：稳健 z F1 **0.8889**（P 1.0 / R 0.8）；普通 z F1 0.6667（漏报一半）；连缺阈值 2 天 F1 0.4545、阈值 3 天 F1 1.0。
- **接口承载**：Mock 的 `summary/detail` 出参无异常字段，契约为唯一真源 → 本能力以内部服务 `AttendanceAnomalyService` 形态提供（不新增接口、不改出参），待 `api.md` 定义后接入（已标 `TODO(扩展)`）。

### 与 Mock 的有意差异（契约出参/错误码不变）
1. **`POST /schedules/batch-by-station` 智能排班模式**：`shiftId` 缺省时由 S3 生成（S3 的接口承载），响应追加 `violations/fallback` 且仅在智能模式返回；`shiftId` 给定时与 Mock **逐位一致**。**属对既有端点的可选扩展，需主智能体确认是否固化进 `api.md`**（已标 `TODO(扩展)`）。
2. **写操作原子化**：`POST /schedules/batch` 由 Mock 的「逐条应用、中途报错留部分改动」改为 `@Transactional` 原子提交（错误码与文案逐条一致）。
3. **`check-in` 不加事务**（有意）：校验未通过的尝试必须落 ABNORMAL 留痕，包事务会随异常回滚而丢失留痕。
4. 收班 `'24:00'`、围栏距离 `<= radius`、ABNORMAL 不计入实到/正常/迟到/早退、缺卡 = 应到−实到，均与 Mock 同口径。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`HaversineCalculatorTest`（同点/量级/null→NaN/对称）、`AttendancePeriodResolverTest`（24:00、非法串 NaN、截断、跨天进位、兜底）、`AttendanceCheckPolicyTest`（时段/单班次两套窗口闭区间两端、ALL/ANY、围栏半径 0 边界、迟到/早退阈值 ±1 分钟、首失败码 9103/9104）、`AttendanceSummaryPolicyTest`（口径 + 缺卡不为负）、`AttendanceDetailPolicyTest`（六维名单、到达态优先级、最早卡、风险序）、`SchedulePlannerTest`（§5.4 十项边界）、`AttendanceAnomalyDetectorTest`（§6.4 六项边界 + 稳健 z 12.005）。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、22 接口端到端冒烟、JSON 列类型处理器（`attendance_rule.wifi_list/check_periods`）真实读写、字面量路由回归、越权口径回归（非 ADMIN 静默收敛 / 403）、CSV 导出流与 `Content-Disposition`、`hrm.algo.schedule.* / hrm.algo.attendance.anomaly.*` 属性绑定校验、S3 退火真实耗时。

### 事实性纠正（不擅自改语义，交主智能体裁定）
1. **导出临时目录配置键命名冲突**：任务书写 `hrm.storage.export-path`，架构 §5.1 写 `hrm.storage.export-dir`（`application.yml` 已落后者）。本批导出走**内存流不落盘**，未消费任一键；建议以架构为准并同步任务书。
2. `OPEN_AHEAD_MIN/CLOSE_DELAY_MIN/围栏默认 300` 为单班次模型/默认规则的等价常量；因 `hrm.algo.*` 须与 §11 逐键一致，**未新增配置键**，已标 `TODO(扩展)`。
3. `pageNum < 1` 服务端按 `@Min(1)` 报 400，而 Mock `paginate` 静默钳制为 1 —— 属 **P0 `PageQuery` 既有口径**（非本批引入），登记待裁定。
4. 非法数值入参（如 `radius:"abc"`）由 Spring 绑定报 400「参数格式不正确」，Mock 报 400「围栏半径须大于 0」：**同为 400、文案不同**（前端不发非法类型，低优先）。

## 2026-09-24 · 服务端 P1+P2（运行日志 3 + 通知 6 = 9 接口 · S8 指纹聚合落地）

### 概述
新增 M1 systemlog（3 接口）与 M2 notification（6 接口）真实后端；落 S8（日志指纹去重 + 时间窗口聚合）；
复用 P0 地基（`@RequireRoles` / `PageQuery` / `ErrorCode` / `AlgoProperties`），未新增依赖。
**未执行任何 DB 迁移**（`V3__client_log.sql` / `V4__notification.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P1 systemlog（3） | `POST /system/client-logs`（ALL）/ `GET`（ADMIN）/ `POST /clear`（ADMIN） | `controller/systemlog/ClientLogController`、`service/systemlog/{ClientLogService,impl/ClientLogServiceImpl}`、`service/support/{ClientLogSanitizer,ClientLogQueryValidator,LogFingerprintAggregator}`、`mapper/ClientLogMapper`、`entity/ClientLog`、`dto/systemlog/*`、`vo/systemlog/*` |
| P2 notification（6） | `GET /notifications`（本人）/ `GET /unread-count` / `GET /{id}` / `PUT /read-all` / `PUT /{id}/read` / `POST /publish`（ADMIN） | `controller/notification/NotificationController`、`service/notification/{NotificationService,impl/NotificationServiceImpl}`、`mapper/NotificationMapper`、`entity/Notification`、`dto/notification/*`、`vo/notification/*` |

### S8 算法落地（阈值全外置，禁硬编码）
- 日志指纹 = `message|route|code`；去重窗口判定用 `hrm.algo.log.dedupeWindowSeconds`（窗口内同指纹只累加 `count`、刷新 `last_time`）；
- 内存指纹索引（Map + 惰性清扫 + 容量兜底）用 `hrm.algo.log.sweepEvery` / `hrm.algo.log.ringBufferCap`；文本截断用 `hrm.algo.log.textMax`；
- 服务端持久化（`client_log.count/first_time/last_time`）+ 回库兜底（跨实例/重启仍不重复建行）；
- 通知限频令牌桶（`hrm.algo.ratelimit.*`）**本批不适用**（站内信为库内扇出，非外部通道限频；S8 限频对象是「机器人/员工维度的发送请求流」，对应三期企微/短信通道），保留键位待三期接入。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`ClientLogSanitizerTest`（白名单/截断/凭据擦除/path 去 query/指纹）、`LogFingerprintAggregatorTest`（窗口边界/乱序/时钟回拨/清扫/容量）、`ClientLogQueryValidatorTest`、`NotificationRouteOrderTest`（字面量优先于 `{id}`）。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、9 接口端到端冒烟、字面量路由回归实跑、越权口径回归（通知 9001 / 角色 403）、`hrm.algo.log.*` 属性绑定校验。

## 2026-09-24 · 服务端 P0 地基批次（24 接口兼容性改造 · C-01~C-11 · 不新增业务接口）

### 概述

按 [server-architecture.md](server-architecture.md) §3.2 / §1.4 / §5.1 / §6.2 P0 段落地 P0 地基批次：
将既有 24 接口的横切能力对齐 145 接口新契约（三角色模型、会话 stationId、数据范围收敛三层、统一分页校验、
错误码补齐、登录出参对齐、脱敏扩展、公开端点白名单、路由回归、包结构统一、配置外置）。**不新增任何业务接口**，
**不改 `api.md` / `db.md` / `plan.md` / `server-architecture.md` / `algo-hrm-server.md` / `V1`/`V2` 迁移脚本 / `.trae/rules/`**，
**不改 `hrm-demo` / `hrm-admin` 源码**，**不执行任何迁移或部署**。本机无 JDK/Maven → 仅静态自审，编译与端到端冒烟收敛服务器阶段。

### 交付（C-01~C-11）

| ID | 改造点 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| C-01 | 三角色 + 逐端点白名单 + fail-closed | `enums/RoleEnum`（新）、`annotation/RequireRoles`（新）、`annotation/RequireAdmin`（废弃保留）、`config/RequireRolesInterceptor`（新，替代 `RequireAdminInterceptor`）、`config/WebConfig`、5 个控制器注解迁移 |
| C-02 | 会话/上下文补 `stationId` | `common/SessionInfo`、`common/LoginUser`、`util/UserContext#getStationId`、`filter/JwtAuthFilter`（旧会话回查员工表补齐并回写）、`service/auth/impl/AuthServiceImpl` |
| C-03 | 数据范围收敛三层 | `util/DataScopeContext`（新）、`config/QueryDataScopeInterceptor`（新）、`dto/support/StationScopedQuery`（新）、`annotation/DataScope` + `annotation/DataScopeStationId`（新）、`aspect/DataScopeAspect`（新）、`service/support/ResourceAccessChecker`（新） |
| C-04 | 统一分页校验（越界 400） | `dto/support/PageQuery`（新）、`dto/employee/EmployeeQuery`（继承）、`controller/employee/EmployeeController#page`（`@Valid`）、`service/employee/impl/EmployeeServiceImpl#page`（去钳制） |
| C-05 | 错误码补齐至附录 B | `enums/ErrorCode`（补 60xx~96xx，文案逐条对齐 Mock `CODE_MESSAGE`） |
| C-06 | 登录出参字段集扩展 | `vo/auth/LoginEmployeeVO`、`vo/employee/EmployeeVO`（补 `pwdChanged`）、`AuthServiceImpl`、`EmployeeServiceImpl#toVOList` |
| C-07 | 脱敏扩展 | `util/DesensitizeUtil`（新增 `maskName` / `maskBankAccount`） |
| C-08 | 公开端点白名单 | `common/PublicEndpoints`（新，单一真源）、`filter/JwtAuthFilter` |
| C-09 | 路由顺序复核 + 回归清单 | `src/test/java/com/qiujie/architecture/RouteOrderRegressionTest`（新，复核结论：Spring 字面量优先，无需改代码） |
| C-10 | 包结构统一（纯移动） | `controller/service/dto/vo` 下新增域子包，既有 5 域 37 个类迁入（`git mv`，无逻辑变更） |
| C-11 | 配置外置与临时目录骨架 | `config/AlgoProperties`（新，强类型绑定 `hrm.algo.*`）、`application.yml`（补 `hrm.algo.*` / `hrm.storage.*` / `spring.servlet.multipart.location`） |

### 新增依赖（唯一）

- `org.springframework.boot:spring-boot-starter-aop`：C-03 L3 的 `@Aspect` 切面需 aspectjweaver，Spring Boot 未默认引入。
  仅用于横切能力，未引入完整 Spring Security 等重型组件（决策 D10）。

### 有意变更清单（相对改造前 24 接口行为差异，仅此 5 类）

1. **分页越界改 400**：`GET /employees` 的 `pageSize>100 / <1 / 非数字` 由「钳制」改为 HTTP 200 + code 400（文案「每页条数须为 1-100」，与 Mock 一致）；`pageNum<1` 由「钳制为 1」改为 400（同时消除负 OFFSET 风险）。`GET /employees/export` 不加 `@Valid`，与 Mock 一致（导出不校验分页）。
2. **错误码扩展**：`ErrorCode` 新增 60xx~96xx 段，既有 24 接口可用码值/文案零变化。
3. **脱敏范围扩展**：`DesensitizeUtil` 新增姓名/银行卡脱敏，24 接口既有手机号脱敏口径零变化。
4. **登录出参新增 `stationId` 等字段**：`POST /auth/login` 的 `data.employee` 只增不减（新增 `gender/deptId/deptName/stationId/stationName/status/entryDate/remark/lastLoginTime/createTime`）；`GET /employees` 出参新增 `pwdChanged`。既有字段名/类型/含义不变。
5. **角色注解迁移**：`@RequireAdmin` → `@RequireRoles({"ADMIN"})`，语义等价；`/auth/logout|me|password` 显式声明「任意登录角色」，行为等价（原为无注解放行）。**新增 fail-closed 纪律**：非公开端点若未声明角色门槛一律 403（当前 24 接口全部已声明，无行为变化）。

### 未验证项（收敛到服务器阶段）

- 编译打包、应用启动、`hrm.algo.*` 属性绑定校验、既有 24 接口端到端冒烟、`spring.servlet.multipart.location` 目录预创建。
- 单测（新增 6 个测试类：`RoleEnumTest` / `PublicEndpointsTest` / `PageQueryConstraintTest` / `ResourceAccessCheckerTest` / `ErrorCodeSegmentTest` / `RouteOrderRegressionTest`，并扩展 `DesensitizeUtilTest`）本机无法执行。

### 事实性纠正（架构文档 vs 源码实际）

- 架构 §3.2 C-03 记 `interceptor/QueryDataScopeInterceptor`，但 §1.2 包结构与既有实现均无 `interceptor` 包 → 实装于 `config/`（与既有 `RequireAdminInterceptor` 位置一致），避免新增孤立包。
- `HandlerInterceptor` 无法替换交给 handler 的 `HttpServletRequest`，故 L1 以「`QueryDataScopeInterceptor` 写入 `DataScopeContext` + 统一 Query 基类读取」等价实现（原文档仅写「拦截器覆盖 query stationId」）。

### 遗留与 `TODO(扩展)`

- 数据范围兜底：非 ADMIN 无归属时按架构 8-2 推荐项 B 收敛为「无数据」（`stationId=-1` 哨兵）；若用户裁定为 A（拒绝 403）需改 `QueryDataScopeInterceptor`（已标注 TODO）。
- 公开端点 `/api/v1/work-orders/auto-dispatch`：仅白名单就位，企微签名/解密与安全评审前置（P8）。
- `@DataScope` L3 资源归属解析器注册表（`resourceKey → resolver`）待后续批次接入。
- `@RequireAdmin` 及拦截器兼容分支待下一批次确认无引用后删除。
- `logging.file.path` 未写入仓库 yml（注释形式）：避免开发机无 `/data` 目录时启动写日志失败，生产由外置配置打开。
- **文档待同步（交测试工程师）**：`test-cases.md` TC-E01-019「员工分页-pageSize 上限（≤100）」预期仍为「钳制为 100」，与 C-04「越界 → 400」冲突，需按有意变更清单同步修订。

---

## 2026-09-24 · 服务端 24 → 145 接口 · 数据库设计（P1~P10 共 33 张新表）

### 概述

按 [server-architecture.md](server-architecture.md) §4 与 [algo-hrm-server.md](algo-hrm-server.md) §11/§13，
将数据库从一期 4 表扩展至 **37 表**（既有 4 + 新增 33）；产出 B 档 Flyway 迁移脚本 V3~V13（**未执行**，
执行属 C 档，待主智能体三步授权）、全量快照与设计文档。**本轮不写 Java 业务代码**。

### 数据库工程师交付

| 变更 | 文件 |
| ---- | ---- |
| 新增 P1 运行日志迁移 | `hrm-server/src/main/resources/db/migration/mysql/V3__client_log.sql` |
| 新增 P2 通知迁移 | `.../mysql/V4__notification.sql` |
| 新增 P3 考勤 5 表迁移 | `.../mysql/V5__attendance.sql` |
| 新增 P4 KPI 2 表迁移 | `.../mysql/V6__kpi.sql` |
| 新增 P5 人事 5 表迁移 | `.../mysql/V7__hr.sql` |
| 新增 P6 财务 4 表迁移 | `.../mysql/V8__payroll.sql` |
| 新增 P7 请假 3 表迁移 | `.../mysql/V9__leave.sql` |
| 新增 P8 工单 4 表迁移 | `.../mysql/V10__work_order.sql` |
| 新增 P9 同步运行态 2 表迁移 | `.../mysql/V11__sync_task.sql` |
| 新增 P9 配置中心 5 表迁移 | `.../mysql/V12__sync_config_center.sql` |
| 新增 P10 parcel 大表迁移 | `.../mysql/V13__parcel.sql`（CREATE TABLE + 独立 CREATE INDEX，含 DROP INDEX 回滚） |
| 刷新 MySQL 全量快照 | `sql/schema/mysql/init.sql`（== V1+V3..V13，37 表） |
| 更新数据库设计文档 | `docs/db.md` v2.0（新增第 8 章表结构设计 + 第 9 章静态自检/待确认/未验证项） |
| 变更日志 | `docs/update-log.md`（本条） |

- **批次与版本 1:1**：P1→V3 … P10→V13；版本号单调，**未发生顺延**（`employee` 经核对无需补索引，理由见 db.md §9.2）。
- **历史脚本未改**：`V1__init_schema.sql` / `V2__init_data.sql` 零改动；`postgresql/` 目录冻结不再维护。
- **未实跑**：本机无 JDK/MySQL/Redis，仅静态自检；DDL 实跑与 EXPLAIN 验证收敛服务器阶段（db.md §9.4）。
- **待确认**：`parcel` 查重索引命名（`idx_` vs 架构 §4.3 的 `uk_`）、配置中心 2 个 JSON 列、`hr_flow.type` 命名（db.md §9.1）。
- **事实性纠正**：架构 §4.2 `parcel.shelf_no/batch_no` 与 Mock 真源不一致，按 Mock 落为 `shelf_code/sync_batch_no`；`payroll.total_amount` 按 Mock 落为 `gross_amount`（db.md §8.12）。

### 表清单（新增 33 张）

| 批次 | 版本 | 表（索引数） |
| ---- | ---- | ---- |
| P1 | V3 | client_log(2) |
| P2 | V4 | notification(2) |
| P3 | V5 | attendance_rule(1) / attendance_shift(1) / attendance_schedule(2) / attendance_record(2) / attendance_makeup(2) |
| P4 | V6 | kpi_metric(2) / kpi_score(3) |
| P5 | V7 | hr_profile(1) / hr_salary(1) / hr_salary_log(1) / hr_flow(3) / hr_flow_step(1) |
| P6 | V8 | payroll_rule(1) / payroll_rule_item(1) / payroll(4) / payroll_item(1) |
| P7 | V9 | leave_request(3) / leave_log(1) / leave_setting(0) |
| P8 | V10 | work_order(4) / work_order_timeline(1) / work_order_transfer(1) / work_order_dispatch_rule(1) |
| P9 | V11 | sync_task(3) / sync_task_log(1) |
| P9 | V12 | sync_station_config(1) / sync_config_item(2) / sync_config_option(1) / sync_config_global(1) / sync_config_station_override(1) |
| P10 | V13 | parcel(4) |

---

## 2026-09-06 ~ 2026-09-07 · 一期员工管理 · 初始交付

### 概述

从空骨架（仅目录结构 + 规范文档 + 配置模板）完成一期"员工管理"全量代码与文档交付。
采用智能体流水线：架构师 → 数据库工程师 → 后端工程师 → 前端工程师 → 运维工程师 → 测试工程师，主智能体编排调度与 Review。

### A 阶段 · 数据库

| 变更 | 文件 |
| ---- | ---- |
| 新增 MySQL 建表脚本（4 表 11 索引） | `hrm-server/src/main/resources/db/migration/mysql/V1__init_schema.sql` |
| 新增 MySQL 种子数据（admin + 总公司部门） | `hrm-server/src/main/resources/db/migration/mysql/V2__init_data.sql` |
| 新增 PostgreSQL 建表脚本 | `hrm-server/src/main/resources/db/migration/postgresql/V1__init_schema.sql` |
| 新增 PostgreSQL 种子数据（含 setval 序列重置） | `hrm-server/src/main/resources/db/migration/postgresql/V2__init_data.sql` |
| 新增 MySQL 结构快照 | `sql/schema/mysql/init.sql` |
| 新增 PostgreSQL 结构快照 | `sql/schema/postgresql/init.sql` |

- BCrypt 哈希经 bcryptjs 双版本回环验证（$2a$ cost=10，与 Spring BCryptPasswordEncoder 兼容）
- 双库表结构逐列一致，种子数据一致

### B 阶段 · 后端（Spring Boot）

| 变更 | 说明 |
| ---- | ---- |
| 新增 pom.xml | Spring Boot 3.3.4 / Java 17 / MyBatis-Plus 3.5.7 / jjwt 0.11.5 / EasyExcel 3.3.4 / Flyway / Lombok / spring-security-crypto |
| 新增 application.yml | 端口 8080，全部配置 change_me 占位，无敏感信息 |
| 新增公共设施 | Result/PageResult/BusinessException/GlobalExceptionHandler/ErrorCode(26 码)/JwtUtil/JwtAuthFilter/RequireAdmin 拦截器/UserContext/SessionUtil/DesensitizeUtil/IpUtil/PasswordUtil |
| 新增 MyBatis-Plus 配置 | 分页插件 + 逻辑删除 + 时间填充 + MapperScan |
| 新增 Redis 配置 | RedisTemplate(Jackson 序列化) + 会话工具(key=hrm:session:{employeeId}) |
| 新增 4 实体 + 4 Mapper | Department/Station/Employee/LoginLog |
| 新增 12 DTO + 10 VO | 请求/响应对象，@Valid 校验中文文案 |
| 新增 5 Service + 5 Impl | Auth/Employee/Department/Station/Dashboard |
| 新增 ImportRowValidator | 行级校验器（纯逻辑，离线可测） |
| 新增 5 Controller | 24 接口 100% 对齐 api.md |
| 新增 4 离线单测 | JwtUtilTest/DesensitizeUtilTest/FieldValidatorTest/ImportRowValidatorTest |

- 偏离 api.md 2 处（从严实现）：上传超 10MB → code 400；启用自己 → 2001
- 编译验证收敛到部署阶段（本机无 JDK）

### C 阶段 · 前端（Vue 3 + Vite）

| 变更 | 说明 |
| ---- | ---- |
| 新增工程基础 | package.json / vite.config.js / index.html / main.js / App.vue |
| 新增请求封装 | request.js（Bearer 注入、code 分发、401/403 处理、blob 解析） |
| 新增下载工具 | download.js（Content-Disposition 解析 + 中文文件名） |
| 新增 Pinia store | auth.js（token/user 持久化 localStorage） |
| 新增路由 + 守卫 | 4 条守卫规则（白名单/未登录/首登锁定/STAFF 越权） |
| 新增 5 API 模块 | auth/dashboard/employee/department/station（24 接口封装） |
| 新增布局 | layout/index.vue（侧边菜单按角色渲染） |
| 新增 7 页面 | login/dashboard/employee/department/station/profile + 5 占位保留 |

- `npm install` 成功（96 packages）
- `npm run build` 成功（1696 modules，dist 产物 21 文件）
- 24 接口封装与 api.md 100% 对齐，无偏离

### D 阶段 · 部署

| 变更 | 文件 |
| ---- | ---- |
| 新增部署脚本 | `deploy/deploy.sh`（幂等，set -e，双模式 source/jar，安全防呆三处） |
| 新增 systemd 单元 | `deploy/hrm-server.service` |
| 新增 Nginx 配置样例 | `deploy/nginx.conf.example`（SPA 回退 + /api 反代 + 透传 IP） |
| 新增部署手册 | `docs/deploy.md`（D01-D05 全覆盖 + 回滚 + FAQ + 附录） |

- bash -n 语法检查通过
- 全部占位符，无真实 IP/密钥

### E 阶段 · 测试

| 变更 | 文件 |
| ---- | ---- |
| 新增验收测试用例 | `docs/test-cases.md`（167 条用例 + 8 条设计疑问） |

- E01 接口冒烟 83 条 / E02 前端走查 23 条 / E03 权限越权 19 条 / E04 导入导出 17 条 / E05 PG 双库 10 条 / E06 文档复核 15 条
- 实际执行需部署后进行

### 设计文档

| 变更 | 文件 |
| ---- | ---- |
| 新增需求文档 | `docs/requirement.md`（21 项功能 + 13 项决策 + 验收标准） |
| 新增数据库设计 | `docs/db.md`（4 表 ER + 字段定义 + 索引设计 + 双库映射） |
| 新增接口文档 | `docs/api.md`（24 接口 + 26 错误码 + 登录态机制） |
| 新增迭代规划 | `docs/plan.md`（三期里程碑 + 风险登记） |
| 新增任务清单 | `TASK.md`（44 项原子任务，A/B/C/D/E 分组） |

### TASK.md 状态汇总

| 阶段 | 任务数 | 已完成 | 收敛项 |
| ---- | ---- | ---- | ---- |
| A 数据库 | 5 | 5 | — |
| B 后端 | 17 | 16 | B17 编译验证收敛到 D03 |
| C 前端 | 11 | 10 | C11 联调冒烟收敛到 E01/E02 |
| D 部署 | 5 | 0 | 文档/脚本已就绪，实际执行需服务器 |
| E 测试 | 6 | 0 | 用例已就绪，实际执行需部署后 |

### 遗留风险

1. **本机无 JDK/Maven**：后端编译验证未执行，收敛到服务器部署阶段
2. **本机无 MySQL/Redis**：Flyway 执行与接口冒烟未执行
3. **8 条设计疑问**（见 test-cases.md §10）：需人工确认后决定是否调整接口/文档
4. **2 处从严偏离**：上传超限→400、启用自己→2001，需确认是否符合预期
5. **前端脱敏字段编辑体验**：手机号脱敏导致编辑时需重新输入完整号码（契约必然结果）
6. **宝塔 Java 项目管理器**：deploy.sh bt 模式不含自动重启（面板无稳定 CLI），需人工点击

---

## 2026-09-24 · 三端演示 Demo · 移动端通知阅读页（NoticeReader）设计规范

### 概述

缺陷：移动端「消息」的通知项点击后打不开、读不全（用户原话「消息里的所有文件应该是可以打开的，现在打不开，点一下就确认了」）。
本轮**只产出设计规范**，零源码改动。产物为 `docs/demo-ux-improvement.md` 新增 D 章（D1–D14）。

### 变更

| 变更 | 文件 |
| ---- | ---- |
| 新增 D 章「移动端通知阅读页（NoticeReader）」共 14 节 | `docs/demo-ux-improvement.md`（第 1584–2143 行） |
| 登记本次新增 | `docs/update-log.md`（本节） |

### 缺陷定位（全量代码核查结论）

| # | 根因 | 位置 |
| --- | --- | --- |
| R-1 | 点击处理函数无兜底分支：按 `bizType` 分流到第 152 行即结束，第 153 行只有 `TODO`，**无 `else`** | `hrm-demo/src/mobile/components/NoticeList.vue:105-154` |
| R-2 | 全工程不存在通知详情/阅读页（移动端路由表无 noticeDetail / notificationDetail） | `src/mobile/router/index.js`、`src/mobile/modules/boss/router.js` |
| R-3 | 列表正文只渲染一行且无 `line-clamp`，长正文把行撑到十几行 | `NoticeList.vue:228` + `src/mobile/styles/mobile.scss:269-274` |
| R-4 | 既有缺陷：`payroll` 通知固定跳 `/staff/payroll/:id`（`meta.roles` 为 `STAFF_ROLES`），老板端（ADMIN）点击必被守卫拦下 | `NoticeList.vue:137-140` vs `router/index.js:190-194` |

**术语校正：** 用户说的「文件」= 通知/公告条目本身（公文语义），不是附件。全工程无附件数据概念；附件能力登记为 `TODO(扩展): 通知附件字段与预览能力`，本轮不做。

### 关键设计结论

| 项 | 结论 |
| ---- | ---- |
| 路由契约 | **两条新增路由**指向同一新组件：`/staff/message/notice`（`staffNoticeReader`，`roles: STAFF_ROLES`）+ `/boss/message/notice`（`bossNoticeReader`，`roles: [ADMIN]`），均 `title: '通知详情'`、无 `tabbar`；参数用 **`?id=`**（缺参可自渲染错误态，不落全局 404）。`/boss/*` 既有 path/name/meta 一字未动 |
| 组件落点 | `src/mobile/views/message/NoticeReader.vue`（跨端共用，不进任何一端域目录） |
| 点击行为 | **一律先进阅读页**，业务动作降为页内底部「去处理」（不保留直接跳转）；标记已读的发起**从列表移到阅读页**（详情取回成功后），失败**不阻断**阅读，仅给提示条 |
| 动作区 | 复用 `ActionBar`；`work_order`/`parcel`/`payroll`/`sync_task`/`flow`/`leave` 逐格给出「`bizType` × 角色」目标表；`payroll` 老板端改落 `/boss/payroll/{bizId}`（关闭 R-4）；`sync_task` STAFF 禁用 + note 说明；无 `bizType` 时 `ActionBar` 整块隐藏 |
| 返回路径 | 列表进入走 `router.back()` 并恢复原 Tab 与滚动位置 + `is-highlight` 高亮；深链走 `router.replace` 兜底（`PageNav` 新增可选 prop `backFallback`，默认 `''` 保持原行为） |
| 状态最小集 | 7 态逐项落点；**不单独做「无权限」态**（路由级由守卫拦截；数据级非本人统一回 9001，不泄露存在性）；阅读页**不做已读/未读视觉区分**，仅失败时给提示条 |
| 复用与新增 | 复用 `PageNav`/`PageState`/`StatusTag`/`ActionBar`/`van-notice-bar`；**新增页面 1 个**（`NoticeReader.vue`）、**新增通用子组件 0 个**、**新增 Token 0 个**；明确不复用 `ListItemCard`/`BossScopeNote` 并给出理由 |
| Mock 契约 | **需要新增 `GET /notifications/:id`**（`roles: ALL_ROLES`，出参复用 `toNotificationVO`，失败统一 9001）。列表 VO 已含全部展示字段，**不新增 VO 字段、不改种子、不改 db 结构**；新路由须注册在 `/notifications/unread-count` 之后（引擎取首个命中） |
| 无障碍 | 对比度**实算**：给出公式 + 11 个 L 中间量 + 8 组比值（正文 14.68:1、元信息 4.83:1、主按钮 6.16:1、提示条 4.83:1、chevron 3.24:1）；登记 1 处反例——`--text-placeholder` 对白底仅 **2.52:1**，禁止用于图标；正文/元信息**强制落在 `--surface-card`**（落页面底色仅 4.50:1 无余量）；触控逐元素 ≥44px |

### 验收

- D12.1 静态审查 12 条（含「零十六进制色值」「零新增依赖」「`/boss/**` 零改动」）
- D12.2 e2e 可断言 15 条（含深链 9001 态、老板端 payroll 回归用例、无横向滚动、`role=toolbar` 计数）
- D14 规范自身自检 10 项全通过

### 遗留与待裁决

见 D 章 D13，共 10 条开放问题，其中涉安全/数据面 3 条（已读回执 #1、正文 URL 外跳 #8、附件能力 #9），需主智能体或用户裁决后方可进入实现阶段。

### 边界声明

- 本轮**零源码改动**：仅新增/修改 `hrm-dev/docs/` 下两个文档
- `hrm-admin/**`、`hrm-server/**`、`hrm-android-shell/**` 零改动
- 未引入新第三方依赖、未使用图片资源

---

## 2026-09-24 · 服务端算法方案（algo-hrm-server）

### 概述

为 `hrm-server` 将 Mock 行为规格算法化，覆盖 8 个场景，均按「①问题建模 ②算法选型与复杂度 ③依据来源 ④可验证指标与基准」四件套交付。
本轮**只出算法方案与离线原型**，不写 Java 业务代码、不改 `hrm-demo`/`hrm-server` 源码、不改 `api.md`/`db.md`。

### 变更

| 变更 | 文件 |
| ---- | ---- |
| 新增算法方案主文档（15 节，含 6 条待裁定口径 + 完整 `hrm.algo.*` 参数表） | `hrm-dev/docs/algo-hrm-server.md` |
| 新增离线原型脚本（无第三方依赖，`.mjs`，固定种子） | `hrm-dev/docs/algo-scripts/{lib/rng.mjs, lib/stats.mjs, s1-kpi.mjs, s2-payroll.mjs, s3-schedule.mjs, s4-anomaly.mjs, s5-leave.mjs, s6-dispatch.mjs, s7-parcel.mjs, s8-ratelimit.mjs, run-all.mjs}` |
| 登记本次新增 | `hrm-dev/docs/update-log.md`（本节） |

### 关键结论（离线实测）

| 场景 | 基线 → 算法化 |
| ---- | ------------- |
| S1 KPI | 阶梯参数化等价性 1200 项 **0 不一致**；新增分位映射可选（等级阈值须同步重标定，已列 Q2） |
| S2 工资 | 来源解析器注册表，**新增计薪项不改核心代码**；同月重复生成 **0 净额差异** |
| S3 排班 | 覆盖率 0.7778 → **1.0**，最少在岗违规 20 → **0** 格；发现 Mock 朴素轮休 `%6==5` 蕴含 `%3==2` 导致晚班人次仅一半（80/80/40） |
| S4 异常检测 | 稳健 z F1 **0.889**（普通 z 0.667）；连缺阈值 3 天 F1 **1.0** |
| S5 请假 | 半天单元区间算法：计薪天数 **18.5×**、重叠判定 k=2000 时 **6147×**，**0 结果差异** |
| S6 派单 | 平均完成时长 9.447h → **1.504h**，SLA 78.3% → **100%**，技能匹配 89% → **100%**（代价：负载 Jain 1.000→0.978） |
| S7 包裹 | Holt-Winters MAPE **5.34%**（朴素 6.10%）；20 万级保守落盘 **105.83 MB = 数据盘 46G 的 0.22%**；游标分页扫描 38 行 vs 深分页 20020 行 |
| S8 限频/日志 | 峰值 23→**14**/s；10 万条日志聚合 **114×**、压缩比 **199×** |

### 边界声明

- 本轮**零源码改动**：仅新增 `hrm-dev/docs/algo-hrm-server.md` 与 `hrm-dev/docs/algo-scripts/`（11 文件）
- `hrm-demo/**`、`hrm-server/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md` 零改动
- 基准数据全部由固定种子合成，**不含任何真实业务数据**
- 未运行项（须服务器复核）：MySQL 真实耗时、InnoDB 真实落盘字节、Redis 令牌桶并发
- 待裁决：`algo-hrm-server.md` §2 的 Q1–Q6 共 6 条口径（KPI 等级阈值与模式、绩效 capRatio 与负净额、SLA 阈值、派单权重、请假扣款开关与连缺阈值）

---

## 2026-09-24 · M4 服务器实跑单元测试 5 项失败修复（后端工程师）

### 缺陷逐条判定

| # | 用例 | 判定 | 契约依据 | 修复动作 |
| --- | --- | --- | --- | --- |
| 1 | `EndAdmissionPolicyTest.h5BossView` | **实现 bug** | `hrm-demo/src/shared/mock/routes/auth.js:62-69`（`clientType==='H5' && as==='boss'` 仅 ADMIN/STATION_ADMIN） | `AS_BOSS` 常量为小写 `"boss"`，与 `normalizeToken(as)`（已转大写）直接 `equals` 永不命中 → boss 视图角色约束被静默跳过；改 `equalsIgnoreCase` |
| 2 | `SmsCodeStoreTest.saveCodeSetsTtlAndClearsAttempts` | **测试 bug** | `docs/db.md:1210-1211`（码 TTL 300s 校验成功即删；失败计数 TTL 300s） | 断言误期望连 `CODE_KEY` 一并删除（会删掉刚 `set` 写入的码，与口径矛盾）→ 改为精确断言只清 `ATTEMPT_KEY` |
| 3-5 | `TrustedDeviceRegistryTest.upsertReactivatesExistingRow / revokeByDeviceId / revokeAllOfEmployee` | **测试 bug** | MyBatis-Plus 3.5.7 `LambdaUpdateWrapper.set(...)` 即时解析列名，需 `TableInfo` 缓存（`TableInfoHelper.initTableInfo` → `LambdaUtils.installCache`） | 纯单测无 Spring/MP 上下文 → 测试加 `@BeforeAll TableInfoHelper.initTableInfo(...)` 注册 `AuthTrustedDevice` 元数据 |

### 变更文件

| 文件 | 说明 |
| ---- | ---- |
| `hrm-server/src/main/java/com/qiujie/service/auth/support/EndAdmissionPolicy.java` | `as` 归一后忽略大小写比较，修复 boss 视图约束失效 |
| `hrm-server/src/test/java/com/qiujie/service/auth/support/SmsCodeStoreTest.java` | 修正 `saveCode` 删除断言；移除未用 `import java.util.List` |
| `hrm-server/src/test/java/com/qiujie/service/auth/support/TrustedDeviceRegistryTest.java` | 新增 `@BeforeAll` 初始化 MyBatis-Plus `TableInfo` |

### 未验证 / 收敛与纪律

- 本机无 JDK/Maven，**仅静态自审**（诊断零告警；`TableInfoHelper.initTableInfo` 签名、`GlobalConfig` 默认处理器非空、`MybatisConfiguration` 无参构造器均按 3.5.7 源码核对）；`mvn test` 复验收敛服务器阶段，目标 509 用例全绿。
- HTTP 契约、业务口径、迁移脚本、`hrm-demo`/`hrm-admin` 零改动；断言未弱化（#2 属纠错且更精确），未加 `@Disabled`、未改 `pom.xml`。
- 观察项（低风险，无需裁定）：`as` 比较改为大小写不敏感，较 Mock 的精确 `=== 'boss'` 略宽松；与既有 `clientType` 归一（`EndAdmissionPolicyTest.webIsCaseInsensitive` 已确立）一致，方向上更偏 fail-closed。

