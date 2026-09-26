# 快递驿站智汇系统 · 一期 API 接口文档

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-06 |
| 状态 | 待评审 |
| 服务前缀 | `/api/v1`（生产经 Nginx 同域反代，本地经 Vite proxy） |
| 接口总数 | 24 |
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

### 4.0 接口概览（24 个）

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

## 8. 打卡规则 WiFi 白名单约束（`PUT /api/v1/attendance/rule`）

> 说明：一期 api.md 原未收录考勤域端点；本节为**追补**，只固化本次下沉到后端的 `wifiList` 约束与失败语义，不动其它段落。
> TODO(扩展): 考勤域正式收录时，本节并入 §4.0 接口概览（现为「24 个」，未含考勤端点）与 §2.1 分段规则（现未收录 9xxx 段），本节降为引用，避免编号/概览重叠。
> 写权限：仅 **ADMIN** 可写（`AttendanceController.saveRule` 标 `@RequireRoles({"ADMIN"})`，与端准入一致）。请求体为差量更新：`wifiList` 缺省（`null`）= 沿用现值；显式提交空数组 = 清空白名单。

### 8.1 `wifiList` 约束

| 字段 | 类型 | 必填 | 约束 | 判据（违反即 400） |
| --- | --- | --- | --- | --- |
| `wifiList` | `WifiEntry[]` | 否 | 至多 **1** 条（每站指定一个）；缺省沿用现值 | 条数 > 1 |
| `wifiList[].ssid` | string | 是 | trim 后长度 **1–32** 字符；保存前 trim | 空/纯空白；长度 > 32 |
| `wifiList[].bssid` | string \| null | 否 | 可空；非空须为 MAC（`AA:BB:CC:DD:EE:FF`，6 段十六进制、**大小写不敏感**）；空串/纯空白归一为 `null`；仅留痕，不参与打卡判定 | 非空且不符格式 |

**去重口径：** 同一 `wifiList` 内 `ssid` 不得重复，按**区分大小写的精确比对**（必须与打卡判定 `ssid === wifiSsid` 一致，不得改为忽略大小写）。
**校验顺序：** 逐条字段（ssid → bssid）→ 去重 → 条数，返回首个命中项。

### 8.2 校验失败语义

沿用统一响应 `{ code, message, data }`，HTTP 200、`body.code = 400`（`ErrorCode.BAD_REQUEST`），`message` 明确到具体字段：

| 场景 | message 示例 |
| --- | --- |
| `ssid` 为空 / 纯空白 | `WiFi 名称不可为空` |
| `ssid` 超长（> 32） | `WiFi 名称须为 1-32 个字符` |
| 条目为 `null` | `WiFi 白名单条目不可为空` |
| `bssid` 非空且非法 | `BSSID 须为 AA:BB:CC:DD:EE:FF 格式` |
| 同一 `wifiList` 内 `ssid` 重复 | `WiFi 名称重复：{ssid}` |
| 条数 > 1 | `WiFi 白名单同一驿站仅允许配置 1 条` |

**错误码：** 本约束**不新增错误码**，全部沿用 `ErrorCode.BAD_REQUEST`（`body.code = 400`）；如需字段级可编程分流码，后续在 **91xx 段统一规划**（现用 9101–9109），本次不动码表。

**`enableWifi=true` 且白名单为空：** **允许保存**（不阻断，fail-open），属「先开开关、后配 WiFi」的分步配置正当流程；风险由前端 warning 提示承担（见设计规范 §3.4）。
> **可用性风险登记（主智能体裁定 A，维持 fail-open）**：该态下打卡判定将因 WiFi 未命中而失败（错误码 **9103**）。缓解：前端 warning + 本接口仅 **ADMIN** 可写；**刻意不改 fail-closed**——差量更新下 `wifiList` 缺省（`null`）表示「不提交/沿用现值」、显式 `[]` 表示「清空」，二者语义可区分、服务端有能力据此阻断，但「先开开关、后配名单」属正当分步流程，故不阻断。

### 8.3 旧数据 `wifiList.length > 1` 的边界口径

> 实测依据（主智能体对线上测试库核查）：`kdyzgl_test.attendance_rule` 共 **8 条规则**，`json_length(wifi_list)` **全部 = 1**，`>1` 的行为 **0** 条 → **现网无历史多条数据**；本口径面向将来，属兜底。

| 环节 | 口径 |
| --- | --- |
| **加载**（`GET /api/v1/attendance/rule`） | 对旧数据 `>1` 条**原样返回**（`toVO` / `copyWifi` 逐条复制，不裁剪、不改写），避免读路径隐式改写数据 |
| **提交**（`PUT /api/v1/attendance/rule`） | 恒要求 **≤ 1 条**；`>1` 返回 `400`（`WiFi 白名单同一驿站仅允许配置 1 条`）——**兜底**，防止 PC 端 / 直调 API 绕过前端 |
| **前端收敛** | 加载到 `>1` 条时**只渲染首条并提示**，由前端承担（**前端责任**）；服务端不代劳裁剪 |

### 8.4 NFR 与已知差异声明

- **性能无关**：本约束为**常数级纯逻辑校验**（`O(n)` 且 `n ≤ 1`）+ 本地表单态，**无性能目标需求**。
- **可观测性**：**不新增监控埋点**，`400` 由既有统一 http 层承接。
- **已知差异（主智能体裁定 C，不强制统一）**：前端行内文案（设计 T14 / T15 / T16）与后端 `400.message`（§8.2）为**两套并存、各自真源**——前端面向表单即时反馈、后端面向 API 消费方；**前端行内文案优先展示，后端 message 作兜底**，**不强制逐字统一**。

### 8.5 Mock 与真实后端校验口径分叉（已知差异）

> **TODO(扩展): Mock 侧尚未同步本约束，联调基线以真实后端为准；Mock 同步由前端侧另行落地。**

真实后端（`AttendanceWifiValidator`）已补 4 条校验；Mock 侧（`hrm-clients/packages/mock` 的 `attendanceStore.js`、`routes/attendance.js` 与 `scripts/verify-mock.mjs`）**未同步**，演示路径与真实后端**行为分叉**。分叉清单（**仅真实后端生效**）：

| # | 校验维度 | 真实后端 | Mock 现状（未同步） |
| --- | --- | --- | --- |
| 1 | `ssid` trim 后长度 1–32 | 超 32 → `400` | 只校验非空，超长可存 |
| 2 | `bssid` 非空须 MAC 格式 | 非法 → `400` | 不校验，非法 MAC 可存 |
| 3 | 条数至多 1 条 | `>1` → `400` | 多条可存 |
| 4 | `ssid` 区分大小写去重 | 重复 → `400` | 不去重 |

> 影响：「演示（Mock）能存、线上 `400`」的认知差；`verify:mock` 现有断言仅覆盖「默认种子为 1 条」，不含上述非法输入用例。**本批不覆盖该校验**，离线演示下勿据 Mock 结果判线上口径。


