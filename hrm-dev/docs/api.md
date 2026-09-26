# 快递驿站智汇系统 · 一期 API 接口文档

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.1 |
| 编写日期 | 2026-09-06 |
| 状态 | 待评审 |
| 服务前缀 | `/api/v1`（生产经 Nginx 同域反代，本地经 Vite proxy） |
| 接口总数 | 46 |
| 关联文档 | [requirement.md](requirement.md)、[db.md](db.md) |

***

## 1. 通用约定

### 1.1 基础约定

| 项 | 约定 |
| ---- | ---- |
| 请求头 | 除登录外均需 `Authorization: Bearer {token}` |
| Content-Type | `application/json`；导入接口为 `multipart/form-data` |
| 时间格式 | 时间 `yyyy-MM-dd HH:mm:ss`，日期 `yyyy-MM-dd`，均为字符串，时区 Asia/Shanghai |
| 分页参数 | `pageNum`（默认 1）/ `pageSize`（默认 10，最大 100） |
| RESTful 风格 | 资源用复数名词，GET 查 / POST 增 / PUT 改 / DELETE 删 |
| 跨域 | **不做任何 CORS 配置**（决策 D9）：开发环境 Vite proxy 将 `/api` 代理到后端，生产环境 Nginx 将 `/api` 反代到后端，前后端始终同源 |
| 安全组件 | 仅引入 `spring-security-crypto` 使用其 BCrypt，不引入完整 Spring Security（决策 D10）；认证为自研 JWT 过滤器 |

### 1.2 统一响应结构

```json
{ "code": 200, "message": "success", "data": { } }
```

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| code | int | 业务状态码，见第 2 章错误码表 |
| message | string | 提示信息（可直接展示给用户；校验失败时说明具体字段问题） |
| data | object/array/null | 业务数据，无数据时为 null |

分页响应的 `data` 统一结构：

```json
{ "total": 100, "pageNum": 1, "pageSize": 10, "list": [ ] }
```

### 1.3 HTTP 状态码与业务码的关系

| 场景 | HTTP 状态码 | body.code |
| ---- | ---- | ---- |
| 成功 | 200 | 200 |
| 业务错误（含参数校验失败、业务规则冲突） | 200 | 对应业务码 |
| 未登录 / Token 失效（含被顶下线、被强制下线） | 401 | 401 |
| 已登录但无权限（STAFF 访问 ADMIN 接口） | 403 | 403 |
| 路径资源不存在（如 `/employees/{id}` 的 id 无效） | 404 | 404 |
| 系统内部错误 | 200 | 500（message 为通用提示，堆栈只进日志） |

**为什么业务错误也返回 HTTP 200**：前端 Axios 响应拦截器统一按 `body.code` 分发（401 跳登录、403 提示无权限、其余按 message 提示），避免同时维护两套状态语义；401/403/404 额外同步 HTTP 状态码，便于 Nginx/网关层直接识别。

### 1.4 手机号脱敏规则（决策 D5）

- 11 位手机号：`138****5678`（前 3 后 4）；
- 非 11 位号码：前 3 位 + `****`；
- 适用范围：**所有 JSON 出参**（员工列表/详情、个人中心、驿站联系人电话）；
- 例外：Excel 导出保留完整手机号（理由见 [requirement.md](requirement.md) 决策 D5）；
- 筛选不受影响：按手机号搜索在后端对完整值做 `LIKE` 匹配。

***

## 2. 错误码表

### 2.1 分段规则

| 段 | 含义 |
| ---- | ---- |
| 200 / 400 / 401 / 403 / 404 / 500 | 通用段（与 HTTP 语义对齐） |
| 10xx | 认证与账号 |
| 11xx | 认证增强（短信 / 图形码 / 设备信任 / 会话到期 / 端准入） |
| 20xx | 员工 |
| 30xx | 部门 |
| 40xx | 驿站 |
| 50xx | 导入导出 |
| 60xx | 同步任务 / 采集运行态 |
| 70xx | 包裹 |
| 80xx | 工单 |
| 90xx | 通知 |
| 91xx | 考勤 / 排班 / 补卡 |
| 92xx | KPI |
| 93xx | 人事 / 入离职 |
| 94xx | 财务 / 工资单 |
| 95xx | 同步配置中心 |
| 96xx | 请假 |

> 段位与后端 `ErrorCode` 枚举一致，本表为**全域分段总表**。已展开明细的段位：通用 + 10xx~50xx + 91xx（见 2.2）、96xx（见 7.2）；其余段位为后端 `ErrorCode` 已定义、本文档尚未展开。

### 2.2 错误码明细

| code | message（示例） | 说明 |
| ---- | ---- | ---- |
| 200 | success | 成功 |
| 400 | {具体字段校验失败说明} | 参数校验失败 |
| 401 | 未登录或登录态已失效 | Token 缺失/过期/被顶下线/被强制下线 |
| 403 | 无权限访问该资源 | 角色权限不足 |
| 404 | {资源}不存在 | 路径参数指向的资源不存在 |
| 500 | 系统繁忙，请稍后重试 | 系统内部错误（堆栈仅记日志） |
| 1001 | 用户名或密码错误 | 登录失败（不区分账号不存在与密码错误，防账号探测） |
| 1002 | 账号已禁用，请联系管理员 | 登录账号处于禁用状态 |
| 1003 | 登录账号已存在 | 新增员工时 username 与活跃数据重复 |
| 1004 | 原密码错误 | 修改本人密码时原密码校验失败 |
| 1101 | 验证码发送过于频繁，请稍后再试 | 短信频控命中 |
| 1102 | 验证码错误或已过期，请重新获取 | 短信验证码校验失败 |
| 1103 | 验证码尝试次数过多，请重新获取 | 单码尝试次数超限 |
| 1104 | 检测到新设备，需短信验证 | 新设备二次验证分流（HTTP 200 + needDeviceVerify，非错误提示） |
| 1105 | 短信服务暂不可用，请稍后重试 | 短信通道投递失败 |
| 1106 | 图形验证码错误或已失效，请重新输入 | captcha-enabled=true 时图形码校验失败 |
| 1107 | 该设备已被撤销，请重新登录 | 设备信任令牌已被撤销 |
| 1108 | 登录已到期，请重新登录 | 会话超重认证窗口，强制重登（不做短信续期） |
| 1109 | 该账号未绑定手机号，无法短信验证 | 短信登录/验证需已绑定手机号 |
| 1110 | 该账号无权登录此端 | 端准入拒绝（端 - 角色不匹配，或缺省 / 未知 / 非法端），详见 4.1.5 |
| 2001 | 不允许对当前登录账号执行该操作 | 禁用/删除/重置密码/角色降级作用于自身 |
| 2002 | 不允许对最后一个可用管理员执行该操作 | 最后管理员保护 |
| 2003 | 手机号已被其他员工使用 | phone 与活跃数据重复 |
| 3001 | 指定的部门不存在 | 入参引用的 deptId / parentId 无效 |
| 3002 | 存在子部门，不允许删除 | 部门删除前校验失败 |
| 3003 | 部门下存在员工，不允许删除 | 部门删除前校验失败 |
| 3004 | 同级部门名称已存在 | 同一父部门下重名 |
| 4001 | 指定的驿站不存在 | 入参引用的 stationId 无效 |
| 4002 | 驿站编码已存在 | station.code 与活跃数据重复 |
| 4003 | 驿站下存在员工，不允许删除 | 驿站删除前校验失败 |
| 4004 | 驿站已停用，不能归属员工 | 新增/编辑员工时选择了停用驿站 |
| 5001 | 导入文件为空或格式不正确（仅支持 .xlsx） | 上传文件校验失败 |
| 5002 | 导入数据超过单次上限（1000 行） | 数据行数超限 |
| 5003 | 导入数据存在校验错误 | 行级错误明细见 `data.errors` |
| 9101 | 该驿站尚未配置打卡规则 | 打卡规则未配置（考勤域） |
| 9102 | 不在打卡时间窗内 | 时间窗越窗（打卡前短路，不留痕） |
| 9103 | WiFi 校验未通过 | WiFi 未命中（打卡落 `ABNORMAL` 留痕后回码） |
| 9104 | 定位校验未通过，已超出打卡围栏范围 | 定位未通过（打卡落 `ABNORMAL` 留痕后回码） |
| 9105 | 今日该类型打卡已完成 | 重复打卡（打卡前短路，不留痕） |
| 9106 | 班次不存在或已停用 | 班次不可用（打卡 / 排班引用） |
| 9107 | 打卡时段不存在 | 时段缺失 / 规则时段配置非法（同码两语义，前端按接口区分） |
| 9108 | 该时段当日已有补卡申请或已正常打卡 | 补卡重复 |
| 9109 | 补卡申请状态不允许该操作 | 补卡状态非法 |

***

## 3. 登录态机制（决策 D3 详解）

### 3.1 流程

```text
登录成功
  ↓ 签发 JWT：HS256，claims = { sub=username, userId, role, jti=UUID, iat, exp }
  ↓ 写 Redis：key = hrm:session:{employeeId}
               value = { jti, username, role, loginIp, loginTime }（JSON）
               TTL  = jwt.expire（默认 86400 秒，与 Token 一致）
  ↓ 返回 token

每次请求（JwtAuthFilter）
  ↓ 白名单（仅 /api/v1/auth/login）直接放行
  ↓ 解析 JWT（签名 + 过期校验） → 取 userId 与 jti
  ↓ 查 Redis 会话：key 不存在 → 401；jti 不一致 → 401（旧会话已被顶掉）
  ↓ 通过 → 用户上下文（ThreadLocal UserContext）注入 controller/service 使用
```

### 3.2 会话失效动作

| 动作 | 实现 |
| ---- | ---- |
| 退出登录 | `DEL hrm:session:{employeeId}`（幂等） |
| 同账号再次登录 | 覆盖写会话（新 jti），旧 Token 因 jti 不匹配立即 401（互踢） |
| 禁用 / 删除 / 重置密码 / 修改本人密码 | 对应业务动作中 `DEL` 会话 → 强制下线 |

### 3.3 Redis Key 约定

| Key | Value | TTL | 用途 |
| ---- | ---- | ---- | ---- |
| `hrm:session:{employeeId}` | 会话 JSON（含 jti） | 86400s | 登录会话（单会话/账号） |

> 前缀 `hrm:` 隔离同 Redis 实例上的其他应用；二期新增 `crawler:lock:{station_id}` 等键沿用独立前缀，互不干扰。

***

## 4. 接口明细

### 4.0 接口概览（46 个）

| 分组 | 方法 | 路径 | 权限 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| 认证 | POST | /api/v1/auth/login | 公开 | 登录 |
| 认证 | POST | /api/v1/auth/logout | 登录 | 退出登录 |
| 认证 | GET | /api/v1/auth/me | 登录 | 当前用户信息 |
| 认证 | PUT | /api/v1/auth/password | 登录 | 修改本人密码 |
| 看板 | GET | /api/v1/dashboard/summary | ADMIN | 看板统计 |
| 员工 | GET | /api/v1/employees | ADMIN | 分页查询 |
| 员工 | GET | /api/v1/employees/{id} | ADMIN | 详情 |
| 员工 | POST | /api/v1/employees | ADMIN | 新增 |
| 员工 | PUT | /api/v1/employees/{id} | ADMIN | 编辑 |
| 员工 | DELETE | /api/v1/employees/{id} | ADMIN | 删除 |
| 员工 | PUT | /api/v1/employees/{id}/status | ADMIN | 启用/禁用 |
| 员工 | PUT | /api/v1/employees/{id}/password/reset | ADMIN | 重置密码 |
| 员工 | GET | /api/v1/employees/import-template | ADMIN | 下载导入模板 |
| 员工 | POST | /api/v1/employees/import | ADMIN | Excel 导入 |
| 员工 | GET | /api/v1/employees/export | ADMIN | Excel 导出 |
| 部门 | GET | /api/v1/departments/tree | ADMIN | 部门树 |
| 部门 | POST | /api/v1/departments | ADMIN | 新增 |
| 部门 | PUT | /api/v1/departments/{id} | ADMIN | 编辑 |
| 部门 | DELETE | /api/v1/departments/{id} | ADMIN | 删除 |
| 驿站 | GET | /api/v1/stations | ADMIN | 列表（全量） |
| 驿站 | POST | /api/v1/stations | ADMIN | 新增 |
| 驿站 | PUT | /api/v1/stations/{id} | ADMIN | 编辑 |
| 驿站 | PUT | /api/v1/stations/{id}/status | ADMIN | 启用/停用 |
| 驿站 | DELETE | /api/v1/stations/{id} | ADMIN | 删除 |
| 考勤 | GET | /api/v1/attendance/rule | ADMIN / STATION_ADMIN / STAFF（范围收敛） | 打卡规则查询 |
| 考勤 | GET | /api/v1/attendance/rule/list | ADMIN | 规则列表（全量） |
| 考勤 | PUT | /api/v1/attendance/rule | ADMIN | 保存打卡规则 |
| 考勤 | GET | /api/v1/attendance/status | ADMIN / STATION_ADMIN / STAFF | 今日打卡状态 |
| 考勤 | POST | /api/v1/attendance/check-in | ADMIN / STATION_ADMIN / STAFF | 打卡 |
| 考勤 | GET | /api/v1/attendance/records | ADMIN / STATION_ADMIN（范围收敛） | 打卡记录（分页） |
| 考勤 | GET | /api/v1/attendance/export | ADMIN / STATION_ADMIN（范围收敛） | 考勤记录导出（CSV） |
| 考勤 | GET | /api/v1/attendance/summary | ADMIN / STATION_ADMIN（范围收敛） | 打卡概况 |
| 考勤 | GET | /api/v1/attendance/detail | ADMIN / STATION_ADMIN（范围收敛） | 考勤明细 |
| 考勤 | GET | /api/v1/attendance/my | ADMIN / STATION_ADMIN / STAFF | 我的打卡（按月） |
| 补卡 | GET | /api/v1/attendance/makeup/my | ADMIN / STATION_ADMIN / STAFF | 我的补卡（分页） |
| 补卡 | GET | /api/v1/attendance/makeup/list | ADMIN | 补卡列表（分页） |
| 补卡 | POST | /api/v1/attendance/makeup | ADMIN / STATION_ADMIN / STAFF | 提交补卡 |
| 补卡 | POST | /api/v1/attendance/makeup/{id}/approve | ADMIN | 审批补卡 |
| 班次 | GET | /api/v1/shifts | ADMIN / STATION_ADMIN / STAFF（范围收敛） | 班次列表 |
| 班次 | POST | /api/v1/shifts | ADMIN | 新增班次 |
| 班次 | PUT | /api/v1/shifts/{id} | ADMIN | 编辑班次 |
| 班次 | DELETE | /api/v1/shifts/{id} | ADMIN | 删除班次 |
| 排班 | GET | /api/v1/schedules | ADMIN / STATION_ADMIN（范围收敛） | 周排班矩阵 |
| 排班 | GET | /api/v1/schedules/my | ADMIN / STATION_ADMIN / STAFF | 我的排班（按周） |
| 排班 | POST | /api/v1/schedules/batch | ADMIN | 手动批量保存排班 |
| 排班 | POST | /api/v1/schedules/batch-by-station | ADMIN | 整站排班（手动 / 智能） |

> 权限列：`ADMIN` = 管理员、`STATION_ADMIN` = 站长、`STAFF` = 员工（角色码与后端 `UserContext` 一致）。考勤 / 补卡 / 班次 / 排班的查询类端点对非 ADMIN 的 `stationId` **静默收敛为本人驿站**；「本人」端点以登录身份收口，详见 §4.6~§4.9。

### 4.1 认证接口

#### 4.1.1 登录

`POST /api/v1/auth/login`（公开，白名单）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| username | string | 是 | 4-30 位 |
| password | string | 是 | 非空 |
| clientType | string | 否 | 端类型，见 4.1.5（优先取请求头 `X-Client-Type`；本字段为兼容旧前端） |
| as | string | 否 | 入口视角 `boss`/`station`/`staff`，见 4.1.5 |
| device | object | 否 | 设备弱信号 `{deviceId,platform,model,osVersion,appVersion}`，用于设备信任判定 |

请求头（端准入，详见 4.1.5）：`X-Client-Type: ADMIN | BOSS | STAFF`；`X-Device-Id: string`（设备标识回退）。

行为：校验账号密码（BCrypt matches）→ 校验 `status=1` 且未删除 → **端准入（见 4.1.5）** → 建立会话（3.1）→ 写 `login_log`（成功/失败均记录）→ 更新 `last_login_time`。账号不存在与密码错误统一返回 1001（防账号探测）。

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx.yyy",
    "expiresIn": 86400,
    "employee": {
      "id": 1,
      "username": "admin",
      "realName": "系统管理员",
      "phone": "138****0000",
      "role": "ADMIN",
      "pwdChanged": false
    }
  }
}
```

> 前端拿到 `pwdChanged=false` 时强制进入改密流程（[requirement.md](requirement.md) 5.3）。

错误码：400 / 1001 / 1002 / **1110**（端准入拒绝，见 4.1.5）。

#### 4.1.2 退出登录

`POST /api/v1/auth/logout`（登录）

入参：无。行为：删除本人会话（幂等，无会话也返回成功）。

```json
{ "code": 200, "message": "success", "data": null }
```

#### 4.1.3 当前用户信息

`GET /api/v1/auth/me`（登录）

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "username": "zhangsan",
    "realName": "张三",
    "phone": "139****1234",
    "gender": 1,
    "role": "STAFF",
    "deptId": 1,
    "deptName": "总公司",
    "stationId": 3,
    "stationName": "城东驿站",
    "entryDate": "2026-03-01",
    "pwdChanged": true,
    "lastLoginTime": "2026-09-05 09:12:30"
  }
}
```

#### 4.1.4 修改本人密码

`PUT /api/v1/auth/password`（登录）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| oldPassword | string | 是 | 非空，须与当前散列匹配 |
| newPassword | string | 是 | 8-20 位，必须同时包含字母和数字 |

行为：校验通过 → 更新散列 → `pwd_changed=1` → 删除本人会话（需重新登录）。

错误码：400（强度不足）/ 1004（原密码错误）。

#### 4.1.5 端准入契约（`X-Client-Type` / fail-closed / 1110）

> 依据：`security-client-admission-review.md` 必改 1–7。三端**互斥准入**，账号不得混登；端类型为**产品 / 审计约束**
> （客户端自称、可伪造，**非鉴权边界**，权限恒由会话 `role` + `@RequireRoles` 决定）。

**端类型取值域（唯一真源 `ClientType`）**：`ADMIN`（PC 管理端 `pc.html`）/ `BOSS`（管理端 H5 驿站精灵，`?as=boss`）/
`STAFF`（员工端 H5 驿站助手）/ `WEB`（旧网页端 / 缺省值，按 PC 口径约束）。

**入参优先级**：请求头 `X-Client-Type`（新契约，非空即以其为准，非法即拒）→ 请求体 `clientType`（兼容旧前端）。
旧值 `H5` 按入口 `as` 派生：`as=boss` → `BOSS`；其余（无 `as` / `staff` / `station`）→ `STAFF`。

**目标准入矩阵（fail-closed）**：

| 端类型 | 端取值来源 | 允许角色 | 配置键（默认值） |
| ---- | ---- | ---- | ---- |
| PC 管理端 | `X-Client-Type: ADMIN` / `clientType: WEB` | 仅 `ADMIN` | `hrm.auth.pc-allowed-roles`（`ADMIN`） |
| 旧网页端 / 缺省值 | `clientType: WEB` | 仅 `ADMIN` | `hrm.auth.pc-allowed-roles`（`ADMIN`） |
| 管理端 H5（驿站精灵） | `X-Client-Type: BOSS` / `clientType: H5` + `as=boss` | 仅 `ADMIN` | `hrm.auth.boss-allowed-roles`（`ADMIN`） |
| 员工端 H5（驿站助手） | `X-Client-Type: STAFF` / `clientType: H5`（无 `as` 或 `as=station`） | `STAFF` + `STATION_ADMIN`（拒 `ADMIN`） | `hrm.auth.staff-allowed-roles`（`STAFF,STATION_ADMIN`） |
| **缺省 / 未知 / 非法端** | 未上报 `clientType` / `X-Client-Type`，或取值不在上述范围 | **一律拒绝（1110）** | —（fail-closed，无可配放行） |

**适用路径**（该判定由服务端统一执行，前端拦截仅为体验）：`POST /auth/login`（密码）、`POST /auth/sms/login`（短信）、
`POST /auth/device/verify` 与 `POST /auth/sms/send`（scene=`DEVICE_VERIFY`）的票据复判——四条路径共用同一策略对象。

**失败行为**：命中即 **HTTP 200 + `{ code: 1110, message: "该账号无权登录此端" }`**，停留登录页、**不签发会话**、
写 `login_log`（`fail_reason=该账号无权登录此端`）。

请求示例：

```http
POST /api/v1/auth/login
X-Client-Type: STAFF
Content-Type: application/json

{ "username": "zhangsan", "password": "******", "device": { "deviceId": "d-1", "platform": "H5" } }
```

失败示例：

```json
{ "code": 1110, "message": "该账号无权登录此端", "data": null }
```

**配置键**（全 ASCII、逗号分隔、逐项去空白并忽略大小写、非已知角色名丢弃；显式配空 = 该端 fail-closed）：

| 键 | 端 | 默认值 |
| ---- | ---- | ---- |
| `hrm.auth.pc-allowed-roles` | `ADMIN` / `WEB` | `ADMIN` |
| `hrm.auth.boss-allowed-roles` | `BOSS` | `ADMIN` |
| `hrm.auth.staff-allowed-roles` | `STAFF` | `STAFF,STATION_ADMIN` |

> **端类型上报方式**（两种，服务端均支持）：
> - **请求头 `X-Client-Type`**（取值 `ADMIN` / `BOSS` / `STAFF`）：**优先级最高**，只要存在即以其为准（非法则直接拒，不回退请求体）；
> - **请求体字段 `clientType`（+ 可选 `as`）**：现状前端均走此方式——`hrm-admin`（PC）传 `WEB`；`hrm-demo` 移动端传 `H5` 并以 `as`（`boss` / `station`）派生管理端/员工端。
>
> **必须显式上报**：缺省 / 未知 / 非法端一律 **1110**（fail-closed）。存量会话在 TTL（默认 3 天）内不失效——端准入**仅约束新登录**，属已知运营口径（「收紧即刻全量生效」需另行清会话，属运营动作）。

### 4.2 看板接口

#### 4.2.1 看板统计

`GET /api/v1/dashboard/summary`（ADMIN）

统计口径（SQL 语义，MySQL 写法，PG 用 `CURRENT_DATE`）：

```sql
SELECT COUNT(*) FROM employee  WHERE is_deleted = 0;                          -- employeeTotal（含禁用）
SELECT COUNT(*) FROM station   WHERE is_deleted = 0;                          -- stationTotal（含停用）
SELECT COUNT(*) FROM department WHERE is_deleted = 0;                         -- departmentTotal
SELECT COUNT(DISTINCT employee_id) FROM login_log
 WHERE login_result = 1 AND login_time >= CURDATE();                          -- todayLoginCount
```

> 今日登录数按「去重员工数」而非登录次数：避免单人反复登录刷高指标（口径决策）。

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "employeeTotal": 56,
    "stationTotal": 8,
    "departmentTotal": 6,
    "todayLoginCount": 23
  }
}
```

### 4.3 员工接口

#### 4.3.1 分页查询

`GET /api/v1/employees`（ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10，最大 100 |
| keyword | string | 否 | 姓名 / 登录账号 / 手机号 三字段模糊匹配（任一命中） |
| deptId | long | 否 | 部门筛选，**含其全部子部门**（后端内存递归展开，部门 < 200 无性能压力） |
| stationId | long | 否 | 驿站筛选 |
| status | int | 否 | 0=禁用，1=启用 |

排序固定 `create_time DESC`。响应示例（`list` 元素即员工 VO）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 56,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 2,
        "username": "zhangsan",
        "realName": "张三",
        "phone": "139****1234",
        "gender": 1,
        "deptId": 1,
        "deptName": "总公司",
        "stationId": 3,
        "stationName": "城东驿站",
        "role": "STAFF",
        "status": 1,
        "entryDate": "2026-03-01",
        "remark": null,
        "lastLoginTime": "2026-09-05 09:12:30",
        "createTime": "2026-03-01 10:00:00"
      }
    ]
  }
}
```

#### 4.3.2 员工详情

`GET /api/v1/employees/{id}`（ADMIN）。响应 `data` 为 4.3.1 的单个员工 VO。错误码：404（id 无效）。

#### 4.3.3 新增员工

`POST /api/v1/employees`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| username | string | 是 | `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`，活跃唯一（1003） |
| password | string | 是 | 8-20 位，含字母和数字 |
| realName | string | 是 | 1-50 字符 |
| phone | string | 是 | `^1[3-9]\d{9}$`，活跃唯一（2003） |
| gender | int | 否 | 0/1/2，默认 0 |
| deptId | long | 否 | 须为存在且未删除的部门（3001） |
| stationId | long | 否 | 须为存在、未删除且**启用**的驿站（4001 / 4004） |
| role | string | 是 | 一期仅 `ADMIN` / `STAFF`，其他值 400 |
| entryDate | string | 否 | yyyy-MM-dd |
| remark | string | 否 | ≤ 255 字符 |

行为：密码 BCrypt 加密入库；`pwd_changed=0`（首登强制改密）；`status=1`。

```json
// 请求
{ "username": "lisi", "password": "Init1234", "realName": "李四", "phone": "13912345678",
  "gender": 2, "deptId": 1, "stationId": 3, "role": "STAFF", "entryDate": "2026-09-01" }

// 响应
{ "code": 200, "message": "success", "data": { "id": 57 } }
```

错误码：400 / 1003 / 2003 / 3001 / 4001 / 4004。

#### 4.3.4 编辑员工

`PUT /api/v1/employees/{id}`（ADMIN）

入参与新增一致，但：**无 username（不可修改）、无 password（改密走专用接口）**；`role` 可修改，受自我保护（2001）与最后管理员保护（2002）约束。错误码：400 / 404 / 2001 / 2002 / 2003 / 3001 / 4001 / 4004。

#### 4.3.5 删除员工

`DELETE /api/v1/employees/{id}`（ADMIN）

行为：逻辑删除 + 删除会话（强制下线）。错误码：404 / 2001（删自己）/ 2002（最后管理员）。

```json
{ "code": 200, "message": "success", "data": null }
```

#### 4.3.6 启用/禁用

`PUT /api/v1/employees/{id}/status`（ADMIN）

| 入参 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| status | int | 是 | 0=禁用，1=启用 |

行为：禁用时删除该账号会话（强制下线）；启用无会话副作用。错误码：400 / 404 / 2001 / 2002。

#### 4.3.7 重置密码

`PUT /api/v1/employees/{id}/password/reset`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| newPassword | string | 是 | 8-20 位，含字母和数字 |

行为：更新散列 → `pwd_changed=0`（下次登录强制改密）→ 删除会话（强制下线）。错误码：400 / 404 / 2001（重置自己请走 4.1.4）。

#### 4.3.8 下载导入模板

`GET /api/v1/employees/import-template`（ADMIN）

响应：`.xlsx` 文件流（由 EasyExcel 动态生成表头 + 表头批注说明必填项与格式）。**模板不含示例数据行**——避免示例行被误导入产生脏数据，填写说明全部放批注与本文档第 5 章。

#### 4.3.9 Excel 导入

`POST /api/v1/employees/import`（ADMIN，`multipart/form-data`）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| file | file | 是 | .xlsx，≤ 10MB，数据行 ≤ 1000 |

行为（决策 D4）：

1. 文件级校验：非 .xlsx / 空 → 5001；数据行 > 1000 → 5002；
2. 逐行逐字段校验，错误收集为 `{row, field, message}`（row 为 Excel 行号，表头为第 1 行，首条数据为第 2 行）；
3. **全部通过才入库（单事务批量插入）**，存在任意错误则整体不入库并返回 5003 + 明细；
4. 导入默认值：`role=STAFF`、`status=1`、`pwd_changed=0`、初始密码取服务器配置 `hrm.employee-init-password`（占位 `change_me_init_password`）；
5. 性能：初始密码统一 → BCrypt 散列只计算 1 次复用全部行。

```json
// 存在错误时的响应（HTTP 200）
{
  "code": 5003,
  "message": "导入数据存在校验错误，共 2 行失败，全部数据未入库",
  "data": {
    "total": 100,
    "failCount": 2,
    "errors": [
      { "row": 3,  "field": "登录账号", "message": "已被使用" },
      { "row": 7,  "field": "手机号",   "message": "格式不正确，须为 11 位有效手机号" }
    ]
  }
}

// 全部成功
{ "code": 200, "message": "导入成功", "data": { "total": 100, "successCount": 100, "failCount": 0 } }
```

#### 4.3.10 Excel 导出

`GET /api/v1/employees/export`（ADMIN）

入参：与 4.3.1 相同的筛选参数（`keyword` / `deptId` / `stationId` / `status`，**不含分页参数**，按条件全量导出）。

响应：`.xlsx` 文件流，文件名 `员工数据_20260906.xlsx`（`Content-Disposition`，UTF-8 编码）。列定义见第 6 章；手机号完整输出（决策 D5）。

### 4.4 部门接口

#### 4.4.1 部门树

`GET /api/v1/departments/tree`（ADMIN）

响应示例（`employeeCount` 为直属员工数，不含子部门）：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1, "parentId": 0, "deptName": "总公司", "sortOrder": 1, "employeeCount": 5,
      "children": [
        { "id": 2, "parentId": 1, "deptName": "运营部", "sortOrder": 1, "employeeCount": 12,
          "children": [] }
      ]
    }
  ]
}
```

#### 4.4.2 新增部门

`POST /api/v1/departments`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| parentId | long | 是 | `0`=根节点；否则须为存在且未删除的部门（3001） |
| deptName | string | 是 | 1-50 字符；同级不重名（3004） |
| sortOrder | int | 否 | 默认 0 |

```json
{ "code": 200, "message": "success", "data": { "id": 7 } }
```

错误码：400 / 3001 / 3004。

#### 4.4.3 编辑部门

`PUT /api/v1/departments/{id}`（ADMIN）

| 入参 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| deptName | string | 是 | 同级不重名（3004） |
| sortOrder | int | 否 | 默认 0 |

**不允许修改 `parentId`**（决策 D12：移动子树需要环检测与子孙重挂逻辑，一期无此需求，接口层直接忽略并拒绝该字段，传入时报 400）。错误码：400 / 404 / 3004。

#### 4.4.4 删除部门

`DELETE /api/v1/departments/{id}`（ADMIN）

前置校验：无子部门（3002）、无归属员工（3003）。错误码：404 / 3002 / 3003。

### 4.5 驿站接口

#### 4.5.1 驿站列表

`GET /api/v1/stations`（ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| status | int | 否 | 不传返回全部未删除驿站；`0`/`1` 按状态过滤 |

**为什么不分页**：驿站基数 < 100，全量返回最简（供管理表格与员工表单下拉共用）；若未来驿站规模显著增长，可平级新增分页接口，不影响现有契约。

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 3,
      "code": "ST001",
      "stationName": "城东驿站",
      "contactPerson": "王五",
      "contactPhone": "137****5678",
      "address": "XX市XX区XX路 1 号",
      "status": 1,
      "employeeCount": 6,
      "remark": null,
      "createTime": "2026-01-15 10:00:00"
    }
  ]
}
```

#### 4.5.2 新增驿站

`POST /api/v1/stations`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| code | string | 是 | `^[A-Za-z0-9_-]{2,50}$`，活跃唯一（4002） |
| stationName | string | 是 | 1-50 字符 |
| contactPerson | string | 否 | ≤ 50 字符 |
| contactPhone | string | 否 | 手机号格式（非必填） |
| address | string | 否 | ≤ 255 字符 |
| remark | string | 否 | ≤ 255 字符 |

```json
{ "code": 200, "message": "success", "data": { "id": 9 } }
```

错误码：400 / 4002。

#### 4.5.3 编辑驿站

`PUT /api/v1/stations/{id}`（ADMIN）

入参同新增（全部可选字段以请求体为准）。一期允许修改 `code`（唯一校验生效）；**二期爬虫对接上线后编码冻结**（届时修改接口将拒绝 `code` 变更，属增量调整，见 [db.md](db.md) 3.2）。错误码：400 / 404 / 4002。

#### 4.5.4 启用/停用

`PUT /api/v1/stations/{id}/status`（ADMIN）

| 入参 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| status | int | 是 | 0=停用，1=启用 |

停用影响：存量员工归属保留、账号可登录；新增/编辑员工不可再归属该驿站（4004）。错误码：400 / 404。

#### 4.5.5 删除驿站

`DELETE /api/v1/stations/{id}`（ADMIN）。前置校验：无归属员工（4003）。错误码：404 / 4003。

### 4.6 考勤接口

> 端点由 `AttendanceController` 提供，路径前缀 `/api/v1/attendance`；全部端点均需登录（`Authorization: Bearer {token}`）。
> **数据范围收敛（L1）**：查询类端点的 `stationId` 对非 ADMIN **静默收敛为本人驿站**（会话缺归属时收敛为「无数据」，不报错、不越权）；`employeeId` 不参与收敛。「本人」端点（`/status`、`/check-in`、`/my`）一律以登录身份收口，不接受前端传 `employeeId` 代他人操作。
> 角色门槛：`GET /rule`、`/status`、`/check-in`、`/my` 为 `ADMIN/STATION_ADMIN/STAFF`；`/records`、`/export`、`/summary`、`/detail` 为 `ADMIN/STATION_ADMIN`；`/rule/list`、`PUT /rule` 为 `ADMIN`。
> 错误码见 §2.2「91xx 考勤 / 排班 / 补卡」段（9101–9109）；字段校验类错误统一 `400`。

#### 4.6.1 打卡规则查询

`GET /api/v1/attendance/rule`（ADMIN / STATION_ADMIN / STAFF）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 条件必填 | ADMIN 必填（缺省 → 400「缺少 stationId」）；非 ADMIN 由 L1 静默收敛为本人驿站，忽略入参 |

响应 `data` 为 `AttendanceRuleVO`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| id / stationId / stationName | long / long / string | 规则 id、驿站 id 与名称 |
| ruleName | string | 规则名称 |
| enableWifi / enableLocation / enableTimeWindow | boolean | 三项校验开关 |
| matchMode | string | `ALL` / `ANY`（多校验项组合方式） |
| wifiList | `WifiEntry[]` | 白名单（`ssid` 为判定依据、`bssid` 仅留痕）；旧数据 `>1` 条**原样返回**（见 4.6.3） |
| longitude / latitude / radius | decimal / decimal / int | 电子围栏原点与半径（米） |
| checkFrequency | int | 每日打卡次数：`2` / `4` |
| checkPeriods | `CheckPeriod[]` | 打卡时段（`name` / `startTime` / `endTime`），时间判定唯一真源 |
| allowEarlyMin / allowLateMin | int | 允许提前 / 延后打卡分钟数（时间窗余量） |
| workStartTime / workEndTime | string | **派生值** = 首段开始 / 末段结束（由 `checkPeriods` 重算，非入参真源） |
| lateThresholdMin / earlyLeaveThresholdMin | int | 迟到 / 早退判定阈值（分钟） |
| status | int | 0=停用，1=启用 |
| updateTime | string | 最近更新时间 `yyyy-MM-dd HH:mm:ss` |

错误码：400（缺 stationId）/ 9101（该驿站尚未配置打卡规则）。

#### 4.6.2 规则列表

`GET /api/v1/attendance/rule/list`（ADMIN）

无入参，返回全量规则 `AttendanceRuleVO[]`（按 id 升序）。无业务错误码（仅 401 / 403）。

#### 4.6.3 保存打卡规则

`PUT /api/v1/attendance/rule`（ADMIN）

请求体为**差量更新**：字段缺省（`null`）表示沿用现值；`checkPeriods` 是时间判定的唯一真源，保存时据此重算 `workStartTime / workEndTime`。首次为某驿站保存时创建记录并套用默认规则（默认时段与电子围栏自洽）。

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| stationId | long | 是 | 须为存在驿站（4001）；缺省 → 400「缺少 stationId」 |
| ruleName | string | 否 | 1-50 字符 |
| enableWifi / enableLocation / enableTimeWindow | boolean | 否 | — |
| matchMode | string | 否 | 仅 `ALL` / `ANY` |
| wifiList | `WifiEntry[]` | 否 | 见下表「`wifiList` 约束」；缺省沿用现值、显式 `[]` 清空 |
| longitude / latitude | decimal | 否 | — |
| radius | int | 否 | > 0 |
| checkFrequency | int | 否 | 仅 `2` / `4`（否则 9107） |
| checkPeriods | `CheckPeriod[]` | 否 | 见下表「`checkPeriods` 约束」（否则 9107） |
| allowEarlyMin / allowLateMin | int | 否 | ≥ 0 |
| lateThresholdMin / earlyLeaveThresholdMin | int | 否 | ≥ 0 |
| workStartTime | string | 否 | `HH:mm`（旧客户端兼容：无 `checkPeriods` 时映射到首段开始） |
| workEndTime | string | 否 | `HH:mm`（可 `24:00` 表示跨零点收班） |
| status | int | 否 | 0 / 1 |

**`wifiList` 约束**（`AttendanceWifiValidator`，校验顺序：逐条字段 → 去重 → 条数，返回首个命中项）：

| 字段 | 类型 | 必填 | 约束 | 判据（违反即 400） |
| --- | --- | --- | --- | --- |
| `wifiList` | `WifiEntry[]` | 否 | 至多 **1** 条（每站指定一个）；缺省沿用现值 | 条数 > 1 |
| `wifiList[].ssid` | string | 是 | trim 后长度 **1–32** 字符；保存前 trim | 空 / 纯空白；长度 > 32 |
| `wifiList[].bssid` | string \| null | 否 | 可空；非空须为 MAC（`AA:BB:CC:DD:EE:FF`，6 段十六进制、**大小写不敏感**）；空串 / 纯空白归一为 `null`；仅留痕，不参与打卡判定 | 非空且不符格式 |

去重口径：同一 `wifiList` 内 `ssid` 不得重复，按**区分大小写的精确比对**（与打卡判定 `ssid === wifiSsid` 一致，不得改为忽略大小写）。

**校验失败语义**（HTTP 200 + `body.code = 400`，`message` 明确到具体字段）：

| 场景 | message 示例 |
| --- | --- |
| `ssid` 为空 / 纯空白 | `WiFi 名称不可为空` |
| `ssid` 超长（> 32） | `WiFi 名称须为 1-32 个字符` |
| 条目为 `null` | `WiFi 白名单条目不可为空` |
| `bssid` 非空且非法 | `BSSID 须为 AA:BB:CC:DD:EE:FF 格式` |
| 同一 `wifiList` 内 `ssid` 重复 | `WiFi 名称重复：{ssid}` |
| 条数 > 1 | `WiFi 白名单同一驿站仅允许配置 1 条` |

本约束**不新增错误码**，全部沿用 `400`（`ErrorCode.BAD_REQUEST`）。`enableWifi=true` 且白名单为空**允许保存**（fail-open，「先开开关、后配 WiFi」属正当分步流程），该态下打卡将因 WiFi 未命中失败（9103），风险由前端 warning 承担。

**`checkPeriods` 约束**（统一回 9107，具体字段由 `message` 说清）：

| 项 | 约束 |
| ---- | ---- |
| 数量 | 非空数组；长度须等于 `checkFrequency / 2`（2 次 → 1 段，4 次 → 2 段） |
| `name` | 1-20 字符 |
| `startTime` / `endTime` | `HH:mm`；`endTime` 可 `24:00` |
| 单段 | 结束时间须晚于开始时间 |
| 段间 | 不允许重叠，须按开始时间升序 |

**旧数据 `wifiList.length > 1` 的边界口径**（面向将来的兜底；现网实测无历史多条数据）：

| 环节 | 口径 |
| --- | --- |
| 加载（`GET /rule`） | 对旧数据 `>1` 条**原样返回**（逐条复制，不裁剪、不改写） |
| 提交（`PUT /rule`） | 恒要求 **≤ 1 条**；`>1` 返回 `400`（兜底，防 PC 端 / 直调 API 绕过前端） |
| 前端收敛 | 加载到 `>1` 条时**只渲染首条并提示**（前端责任），服务端不代劳裁剪 |

响应 `data` 为保存后的 `AttendanceRuleVO`。错误码：400 / 4001 / 9107。

#### 4.6.4 今日打卡状态

`GET /api/v1/attendance/status`（ADMIN / STATION_ADMIN / STAFF）

无入参，以登录人身份收口。响应 `data` 为 `AttendanceStatusVO`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| workDate | string | 今日 `yyyy-MM-dd` |
| hasSchedule | boolean | 今日是否有排班 |
| shift | `AttendanceShiftVO` | 今日班次；未排班时为规则合成的**兜底班次**（`id=null`、`shiftName=默认班次`），无规则时为 `null` |
| onChecked / offChecked | boolean | 今日上 / 下班卡是否已完成（有效卡，排除 `ABNORMAL`） |
| onRecord / offRecord | `AttendanceRecordVO` | 今日最近一次有效上 / 下班卡（无则 `null`） |
| checkFrequency | int | 规则要求的每日打卡次数（无规则时 `null`） |
| requireSummary | string | 规则要求摘要（无规则时 `null`） |
| periods | `PeriodStatus[]` | 按时段展开：`periodIndex` / `name` / `startTime` / `endTime` / `windowStart`（时段开始 − `allowEarlyMin`）/ `windowEnd`（时段结束 + `allowLateMin`）/ `onChecked` / `offChecked` / `onTime` / `offTime` |
| rule | `AttendanceRuleVO` | 当前驿站规则（无规则时 `null`） |

错误码：401（会话无归属员工，防御性兜底）。

#### 4.6.5 打卡

`POST /api/v1/attendance/check-in`（ADMIN / STATION_ADMIN / STAFF）

| 入参 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 否 | 非 ADMIN 强制收敛为本人驿站（防代他人向别的驿站打卡） |
| checkType | string | 是 | `ON` / `OFF`，其他值 400 |
| wifiSsid | string | 否 | 当前 WiFi 名称（`enableWifi` 时参与判定） |
| longitude / latitude | decimal | 否 | 定位坐标（`enableLocation` 时参与判定） |
| periodIndex | int | 否 | 缺省走单班次模型（排班班次为时间基准）；传入则按时段模型判定 |

判定链顺序（不得变更）：规则 → 时段 / 班次 → 时间窗 → 重复 → 校验项（WiFi / 定位，按 `matchMode` ALL/ANY）→ 迟到 / 早退。

服务端判定，**校验未通过仍落一条 `ABNORMAL` 留痕记录**后回码（时间窗越窗与重复打卡除外，此二者在落库前短路、不留痕）。响应 `data` 为 `AttendanceRecordVO`。

错误码：400 / 9101（未配规则）/ 9107（时段不存在）/ 9106（班次不存在或已停用，单班次模型）/ 9102（不在时间窗内，不留痕）/ 9105（今日该类型打卡已完成，不留痕）/ 9103（WiFi 校验未通过，留痕）/ 9104（定位未通过，留痕）。

#### 4.6.6 打卡记录

`GET /api/v1/attendance/records`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNum / pageSize | int | 否 | 默认 1 / 10，`pageSize` 区间 [1,100]（越界 400） |
| stationId | long | 否 | 非 ADMIN 静默收敛为本人驿站 |
| employeeId | long | 否 | 员工筛选；**原样透传、不收敛**（对齐 Mock 口径） |
| status | string | 否 | `NORMAL` / `LATE` / `EARLY_LEAVE` / `ABNORMAL`，其他值 400 |
| startDate / endDate | string | 否 | `yyyy-MM-dd`（含），格式非法 400 |

响应为分页 `{ total, pageNum, pageSize, list }`，`list` 元素为 `AttendanceRecordVO`（含 `id` / `employeeId` / `employeeName` / `stationId` / `workDate` / `periodIndex` / `periodName` / `checkType` / `checkTime` / `status` / `source` / `checkMode` / `wifiSsid` / `wifiMatched` / `longitude` / `latitude` / `distance` / `locationMatched` / `remark`）。补卡补录行 `source=MAKEUP`，设备校验字段为 `null`。排序 `check_time DESC`（同刻按 id 倒序）。错误码：400。

#### 4.6.7 考勤记录导出

`GET /api/v1/attendance/export`（ADMIN / STATION_ADMIN）

入参与筛选口径同 4.6.6（**不分页，全量导出**）。响应为 CSV 文件流（`Content-Type: text/csv`，文件名 `考勤记录_yyyyMMdd.csv`），13 列：员工姓名 / 登录账号 / 所属驿站 / 日期 / 时段名称 / 卡类型 / 打卡时间 / 打卡方式 / WiFi / 距离(米) / 状态 / 来源 / 备注（枚举列输出中文，空值输出空串）。内存流写出，不落盘。错误码：400。

#### 4.6.8 打卡概况

`GET /api/v1/attendance/summary`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 否 | 非 ADMIN 静默收敛为本人驿站 |
| date | string | 否 | 统计日期 `yyyy-MM-dd`，缺省今天；格式非法 400 |

响应 `data` 为 `AttendanceSummaryVO`：`date` / `shouldCount`（应到 = 当天有排班人数）/ `actualCount`（实到 = 有有效上班卡人数，去重）/ `normalCount` / `lateCount` / `earlyLeaveCount` / `absentCount`（缺卡 = `max(0, 应到 − 实到)`）。错误码：400。

#### 4.6.9 考勤明细

`GET /api/v1/attendance/detail`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 否 | 非 ADMIN 静默收敛为本人驿站 |
| dim | string | 是 | 维度，白名单 `SHOULD` / `ACTUAL` / `NORMAL` / `LATE` / `EARLY_LEAVE` / `ABSENT`，非法或缺省 400 |
| date | string | 否 | 统计日期，缺省今天；格式非法 400 |

响应 `data` 为 `AttendanceDetailVO`：`dim` / `date` / `total` / `list[]`（行含 `employeeId` / `employeeName` / `stationId` / `stationName` / `shiftName`（仅 SHOULD/ABSENT）/ `periodName` / `onCheck` / `offCheck`（各含 `time` / `status`）/ `dayState`（`MISS` / `LATE` / `EARLY_LEAVE` / `NORMAL`）/ `remark`）。排序：迟到 / 早退按命中卡时间倒序，缺卡按姓名，应到按风险优先，实到 / 正常按上班卡时间倒序。错误码：400。

#### 4.6.10 我的打卡

`GET /api/v1/attendance/my`（ADMIN / STATION_ADMIN / STAFF）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| month | string | 否 | 账期 `yyyy-MM`，缺省当前月；格式非法 400 |

不分页，以登录人身份收口。响应 `data` 为 `MyAttendanceVO`：`month` / `list`（本人当月 `AttendanceRecordVO[]`，按打卡时间倒序）/ `todayStatus`（`AttendanceStatusVO`，账号归属员工不存在时 `null`）。错误码：400。

#### 4.6.11 NFR 与文案差异声明

- **性能无关**：`wifiList` 约束为常数级纯逻辑校验（`O(n)` 且 `n ≤ 1`）+ 本地表单态，无性能目标需求。
- **可观测性**：不新增监控埋点，`400` 由既有统一 HTTP 层承接。
- **已知差异（不强制统一）**：前端行内文案（设计 T14 / T15 / T16）与后端 `400.message` 为**两套并存、各自真源**——前端面向表单即时反馈、后端面向 API 消费方；前端行内文案优先展示，后端 `message` 作兜底，不强制逐字统一。

#### 4.6.12 Mock 与真实后端校验口径分叉（已知差异）

真实后端（`AttendanceWifiValidator`）已实现下列校验；Mock 侧（`hrm-clients/packages/mock` 的 `attendanceStore.js`、`routes/attendance.js` 与 `scripts/verify-mock.mjs`）**未同步**，演示路径与真实后端**行为分叉**（以下**仅真实后端生效**）：

| # | 校验维度 | 真实后端 | Mock 现状（未同步） |
| --- | --- | --- | --- |
| 1 | `ssid` trim 后长度 1–32 | 超 32 → `400` | 只校验非空，超长可存 |
| 2 | `bssid` 非空须 MAC 格式 | 非法 → `400` | 不校验，非法 MAC 可存 |
| 3 | 条数至多 1 条 | `>1` → `400` | 多条可存 |
| 4 | `ssid` 区分大小写去重 | 重复 → `400` | 不去重 |

影响：「演示（Mock）能存、线上 `400`」的认知差；`verify:mock` 现有断言仅覆盖「默认种子为 1 条」，不含上述非法输入用例。离线演示下勿据 Mock 结果判线上口径。

### 4.7 补卡接口

> 端点由 `AttendanceMakeupController` 提供，路径前缀 `/api/v1/attendance/makeup`。申请人一律为登录人本人，`stationId` 取登录人归属、**不接受前端传参**（防代他人申请）。

#### 4.7.1 我的补卡

`GET /api/v1/attendance/makeup/my`（ADMIN / STATION_ADMIN / STAFF）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNum / pageSize | int | 否 | 默认 1 / 10，`pageSize` 区间 [1,100]（越界 400） |
| status | string | 否 | `PENDING` / `APPROVED` / `REJECTED`，其他值 400 |
| startDate / endDate | string | 否 | `yyyy-MM-dd`（含），格式非法 400 |

数据范围以登录身份收口（`employeeId` 取登录人，不接收前端传参）。响应为分页 `AttendanceMakeupVO`。错误码：400。

#### 4.7.2 补卡列表

`GET /api/v1/attendance/makeup/list`（ADMIN）

入参同 4.7.1，另加 `stationId`（long，可选；缺省 = 全量，ADMIN 可跨站）。响应为分页 `AttendanceMakeupVO`。错误码：400。

#### 4.7.3 提交补卡

`POST /api/v1/attendance/makeup`（ADMIN / STATION_ADMIN / STAFF）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| workDate | string | 是 | `yyyy-MM-dd`；不能晚于今天 |
| periodIndex | int | 是 | ≥ 0（否则 9107） |
| checkType | string | 是 | `ON` / `OFF` |
| reason | string | 是 | 2-200 字 |

校验顺序：规则 → 时段 → 重复申请 → 已有正常打卡。申请人为登录人本人，`stationId` 取登录人归属（未归属 → 400「当前账号未归属驿站，无法提交补卡」）。响应 `data` 为 `AttendanceMakeupVO`。

错误码：400 / 9101（该驿站尚未配置打卡规则）/ 9107（时段不存在）/ 9108（该时段当日已有补卡申请或已正常打卡）。

#### 4.7.4 审批补卡

`POST /api/v1/attendance/makeup/{id}/approve`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| approved | boolean | 是 | 缺省 / 非布尔 → 400「approved 须为布尔值」 |
| approveRemark | string | 否 | ≤ 200 字 |

审批通过 → 补录打卡记录：打卡时间取该时段规定时间（上班卡取时段开始、下班卡取时段结束），`source=MAKEUP`，设备校验字段（`checkMode` / `wifiSsid` / `wifiMatched` / `longitude` / `latitude` / `distance` / `locationMatched`）统一置 `null`（不伪造命中值）。若时段被改配置导致原时段不存在，审批通过将被**拒绝**（9101），避免落下「审批通过却无打卡记录」的矛盾数据。

响应 `data` 为 `AttendanceMakeupVO`（含 `id` / `employeeId` / `employeeName` / `stationId` / `stationName` / `workDate` / `periodIndex` / `periodName` / `checkType` / `reason` / `status` / `applyTime` / `approverId` / `approverName` / `approveTime` / `approveRemark`）。错误码：400 / 404（补卡申请不存在）/ 9109（补卡申请状态不允许该操作）/ 9101。

### 4.8 班次接口

> 端点由 `ShiftController` 提供，路径前缀 `/api/v1/shifts`。列表任何登录角色可读（非 ADMIN 静默收敛为本人驿站）；增删改仅 ADMIN。

#### 4.8.1 班次列表

`GET /api/v1/shifts`（ADMIN / STATION_ADMIN / STAFF）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 条件必填 | ADMIN 必填（缺省 → 400「缺少 stationId」）；非 ADMIN 静默收敛为本人驿站 |

响应 `data` 为 `AttendanceShiftVO[]`，按开始时间升序。`AttendanceShiftVO`：`id` / `stationId` / `stationName` / `shiftName` / `startTime` / `endTime` / `color` / `restMinutes` / `status`。错误码：400。

#### 4.8.2 新增班次

`POST /api/v1/shifts`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| stationId | long | 是 | 须为存在驿站（4001） |
| shiftName | string | 是 | 1-20 字符 |
| startTime / endTime | string | 是 | `HH:mm`；`endTime` 可 `24:00`；结束须晚于开始 |
| color | string | 是 | `#RRGGBB`（存储归一为大写） |
| restMinutes | int | 否 | ≥ 0，缺省 0 |
| status | int | 否 | 缺省 1 |

响应 `data` 为 `AttendanceShiftVO`。错误码：400 / 4001。

#### 4.8.3 编辑班次

`PUT /api/v1/shifts/{id}`（ADMIN）

入参同 4.8.2，但 `stationId` **忽略**（归属不可改）。响应 `data` 为 `AttendanceShiftVO`。错误码：400 / 404（班次不存在）。

#### 4.8.4 删除班次

`DELETE /api/v1/shifts/{id}`（ADMIN）

删除受保护：**被排班引用时返回 400「该班次已被排班引用，不能删除」**（删掉会让历史排班指向空班次，打卡判定失去时间基准）。响应 `data` 为 `null`。错误码：404 / 400。

### 4.9 排班接口

> 端点由 `ScheduleController` 提供，路径前缀 `/api/v1/schedules`。周矩阵非 ADMIN 静默收敛为本人驿站；「我的排班」以登录身份收口。

#### 4.9.1 周排班矩阵

`GET /api/v1/schedules`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 条件必填 | ADMIN 必填（缺省 → 400「缺少 stationId」）；非 ADMIN 静默收敛为本人驿站 |
| weekStart | string | 否 | 周起始日期 `yyyy-MM-dd`（内部取该日期所在周的周一），缺省本周；格式非法 400 |

响应 `data` 为 `ScheduleMatrixVO`：`weekStart` / `weekEnd` / `dates[]`（本周 7 个日期，周一→周日）/ `shifts[]`（该驿站班次，按开始时间升序）/ `employees[]`（每行含 `employeeId` / `employeeName` / `days[]`，`days` 为 7 个 `DayCell`：`workDate` / `scheduleId` / `shiftId`，未排班时为 `null`）。错误码：400。

#### 4.9.2 我的排班

`GET /api/v1/schedules/my`（ADMIN / STATION_ADMIN / STAFF）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| weekStart | string | 否 | 同 4.9.1，缺省本周；格式非法 400 |

以登录人身份收口。响应 `data` 为 `MyScheduleVO`：`weekStart` / `weekEnd` / `dates[]` / `list[]`（单日含 `workDate` / `scheduleId` / `shiftId` / `shiftName` / `startTime` / `endTime` / `color` / `restMinutes`，未排班时除 `workDate` 外均为 `null`）。错误码：400。

#### 4.9.3 手动批量保存排班

`POST /api/v1/schedules/batch`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| stationId | long | 是 | 缺省 → 400「缺少 stationId」 |
| items | `Item[]` | 是 | 非空，且 ≤ **200** 条 |
| items[].employeeId | long | 是 | 须属于该驿站（否则 400「员工 {id} 不属于该驿站」） |
| items[].workDate | string | 是 | `yyyy-MM-dd` |
| items[].shiftId | long | 否 | 须存在、同驿站且 `status=1`（否则 9106）；**缺省 / 空表示清空该天排班** |

唯一性 = `employeeId + workDate`（同一员工同一天重复提交即覆盖）。整批为**原子提交**（`@Transactional`；与 Mock「逐条应用、中途报错留下部分改动」有意不同，实时后端不出现半成功状态）。响应 `data` 为 `{ saved, removed }`（新增 / 改派条数、清空条数）。错误码：400 / 9106。

#### 4.9.4 整站排班

`POST /api/v1/schedules/batch-by-station`（ADMIN）

| 入参 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 是 | 须为存在驿站（4001） |
| shiftId | long | 否 | **给定** = 手动模式（整站铺同一班次）；**缺省** = 智能模式（S3 算法） |
| startDate / endDate | string | 是 | `yyyy-MM-dd`；`endDate` 不早于 `startDate` |
| employeeIds | long[] | 否 | 参与员工，缺省 = 该驿站全部在职员工 |
| skipExisting | boolean | 否 | 已存在排班是否跳过，缺省 `true`（`false` 表示覆盖） |
| weekdays | int[] | 否 | 只排命中的星期（0=周日 … 6=周六），缺省 = 范围每天 |

- **手动模式**（`shiftId` 给定）：`shiftId` 须存在、同驿站且 `status=1`（否则 9106）。
- **智能模式**（`shiftId` 缺省）：由 S3 算法（贪心构造 + 模拟退火）逐格生成班次，尊重「每日每班最少在岗 / 连续工作上限 / 轮休均衡 / 班次均衡」约束；该驿站无启用班次时快速失败（9106）；算法超参外置于 `hrm.algo.schedule.*`（`minPerShift` / `maxConsecutiveWork` / `restCycleDays` / `weights.*` / `sa.*`），失败**降级**返回贪心解 + 违规清单（不抛异常）。

响应 `data` 为 `ScheduleStationResultVO`：`created` / `skipped` / `total`（= created + skipped）/ `violations[]`（仅智能模式返回，手动模式省略）/ `fallback`（是否走了失败降级）。错误码：400 / 4001 / 9106。

***

## 5. Excel 导入模板规范

### 5.1 模板列定义

| 列序 | 列名 | 必填 | 校验规则 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | 姓名 | 是 | 1-50 字符 | |
| 2 | 登录账号 | 是 | `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`；文件内唯一；与库内活跃账号唯一 | 导入后不可修改 |
| 3 | 手机号 | 是 | `^1[3-9]\d{9}$`；文件内唯一；库内活跃唯一 | |
| 4 | 性别 | 否 | `男` / `女`，留空=未知 | 中文枚举，其他值行级报错 |
| 5 | 部门名称 | 否 | 须为库内未删除部门；若同名部门多于 1 个 → 该行报错「部门名称不唯一，请先规范部门命名」 | 按名称精确匹配 |
| 6 | 驿站名称 | 否 | 须为库内未删除且**启用**的驿站；同名多于 1 个 → 行级报错 | 按名称精确匹配 |
| 7 | 入职日期 | 否 | `yyyy-MM-dd` | 其他格式行级报错 |
| 8 | 备注 | 否 | ≤ 255 字符 | |

### 5.2 导入行为规则

1. 仅接受 `.xlsx`，文件 ≤ 10MB，数据行 ≤ 1000（不含表头）；
2. 整批校验、全部通过才入库（单事务），任意错误则全部不入库（决策 D4）；
3. `row` 使用 Excel 真实行号（表头=1），便于用户直接定位；
4. 导入账号统一初始密码（服务器配置 `hrm.employee-init-password`，占位 `change_me_init_password`），BCrypt 散列仅计算一次复用；
5. `role=STAFF`、`status=1`、`pwd_changed=0`（首登强制改密）；
6. 模板不含「角色」「密码」列——角色由管理员后续在页面调整，明文密码不允许出现在 Excel 中流转。

## 6. Excel 导出规范

| 列序 | 列名 | 来源字段 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | 登录账号 | username | |
| 2 | 姓名 | realName | |
| 3 | 手机号 | phone | **完整输出**（决策 D5） |
| 4 | 性别 | gender | 中文（未知/男/女） |
| 5 | 部门 | dept_id → dept_name | |
| 6 | 驿站 | station_id → station_name | |
| 7 | 角色 | role | 中文（管理员/站长(二期)/员工） |
| 8 | 状态 | status | 中文（禁用/启用） |
| 9 | 入职日期 | entry_date | yyyy-MM-dd |
| 10 | 最后登录时间 | last_login_time | |
| 11 | 创建时间 | create_time | |
| 12 | 备注 | remark | |

导出范围：当前筛选条件命中的全量数据（不分页）；一期员工量级（数千行内）全量导出无压力，超过 5 万行需改分片导出时二期评估。文件流式写出（EasyExcel），不整体载入内存。

## 7. M11 请假模块与前端运行日志（Demo 增量）

> 来源：`docs/demo-leave-design.md` 附录 A / B。本段是 Demo（Mock 适配器）已落地的契约，
> 后端实现时按此表建接口；`roles` 缺省 = 不限角色（仅需登录）。三端前端封装见 `hrm-demo/src/{pc,mobile}/api/`。

### 7.1 端点清单（16 个）

| # | 方法 | 路径 | roles | 入参 | 出参 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/leave` | — | `{ leaveType, startDate, startPeriod, endDate, endPeriod, reason }` | `LeaveVO` |
| 2 | POST | `/leave/preview` | — | 同上（只算不落库，reason 可省） | `{ naturalDays, countedDays, hasRestDayExcluded }` |
| 3 | GET | `/leave/my` | — | `{ status?, leaveType?, startDate?, endDate?, pageNum, pageSize }` | 分页 `LeaveVO[]` |
| 4 | GET | `/leave/list` | ADMIN / STATION_ADMIN | 同 #3 + `stationId?`（非 ADMIN 强制覆盖为本人驿站） | 分页 `LeaveVO[]` |
| 5 | GET | `/leave/settings` | — | — | `{ leaveDeductEnabled }` |
| 6 | PUT | `/leave/settings` | ADMIN | `{ leaveDeductEnabled }` | `{ leaveDeductEnabled }` |
| 7 | GET | `/leave/:id` | — | — | `LeaveVO`（含 `handleLog[]` 与 `canEdit/canCancel/canRevoke`） |
| 8 | PUT | `/leave/:id` | — | 同 #1 | `LeaveVO` |
| 9 | POST | `/leave/:id/cancel` | — | `{}` | `LeaveVO` |
| 10 | POST | `/leave/:id/resubmit` | — | 同 #1 | `LeaveVO`（新单，带 `originId`） |
| 11 | POST | `/leave/:id/station-approve` | STATION_ADMIN | `{ approved, remark? }` | `LeaveVO` |
| 12 | POST | `/leave/:id/final-approve` | ADMIN | `{ approved, remark? }` | `LeaveVO` |
| 13 | POST | `/leave/:id/revoke` | ADMIN | `{ reason }` | `LeaveVO` |
| 14 | POST | `/system/client-logs` | — | `{ logs: ClientLogItem[] }` | `{ accepted }` |
| 15 | GET | `/system/client-logs` | ADMIN | `{ level?, source?, keyword?, startTime?, endTime?, employeeId?, pageNum, pageSize }` | 分页 + `counts` |
| 16 | POST | `/system/client-logs/clear` | ADMIN | `{}` | `{ cleared }` |

**约定**：`status=PENDING` 是服务端展开的聚合虚拟值（= `PENDING_STATION` + `PENDING_BOSS`）；
审批意见「通过时选填 0–100 字、驳回时必填 2–100 字」，撤回原因必填 2–100 字；
撤回过账期的硬约束见 9606。

### 7.2 错误码增量（96xx）

| 码 | 常量名 | 含义 | 默认文案 |
| --- | --- | --- | --- |
| 9601 | `LEAVE_NOT_EXISTS` | 请假申请不存在 | 请假申请不存在 |
| 9602 | `LEAVE_STATUS_INVALID` | 状态不允许该操作 | 该申请当前状态不支持此操作 |
| 9603 | `LEAVE_OVERLAP` | 时间段与已有申请重叠 | 该时间段与已有申请重叠 |
| 9604 | `LEAVE_DATE_INVALID` | 日期非法（早于今天 / 结束早于开始 / 超单次上限） | 请假日期不合法 |
| 9605 | `LEAVE_NO_PERMISSION` | 无权操作（跨站 / 审自己 / ADMIN 提交） | 无权操作该请假申请 |
| 9606 | `LEAVE_PAYROLL_LOCKED` | 账期工资单已生成，不可撤回 | 该账期工资单已生成，不可撤回 |
| 9607 | `LEAVE_EDIT_FORBIDDEN` | 当前状态不允许修改 | 该申请当前状态不允许修改 |

单次请假上限 30 个自然日（超限回 9604）；请假只能选**今天或未来**（与补卡「只能过去或当天」方向相反）。

### 7.3 通知类型增量

`notification.type`：**5 = 请假申请**、**6 = 请假结果**（原 1–4 不变）。发布接口的类型校验数组已同步放行 5/6。
请假通知的 `biz_type` 统一为 `leave`，`biz_id` 为请假单 id。

### 7.4 `LeaveVO` 字段

```
id, employeeId, employeeName, stationId, stationName,
leaveType, startDate, startPeriod, endDate, endPeriod, reason,
naturalDays, countedDays, countedDaysSnapshot,        // 快照 { naturalDays, countedDays, scheduleDigest }
status, rejectStage,                                  // rejectStage: null | 'STATION' | 'BOSS'
approverId, approverName, approveTime, approveRemark,  // 终审
stationApproverId, stationApproverName, stationApproveTime, stationApproveRemark,  // 初审（两级分槽）
cancelById, cancelTime, revokerId, revokerName, revokeTime, revokeReason,
originId, applyTime, updateTime, handleLog[]
```

### 7.5 前端运行日志 `ClientLogItem`

入库字段（白名单复制，未列出的一律丢弃）：`time / level(INFO|WARN|ERROR) / source(PC|H5|SHELL) / employeeId /
route / message / stack / method / path(去 query) / status / code / duration / ua`，另附 `count / firstTime / lastTime`。
**明确不得记录**：token、密码、身份证、手机号全量、银行卡、请求/响应体原文。
Mock 侧环形缓冲上限 200 条（FIFO），同 `(message + route + code)` 在 10 秒内重复只累加 `count`。


