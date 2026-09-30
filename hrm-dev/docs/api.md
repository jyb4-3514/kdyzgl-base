# 快递驿站智汇系统 · 一期 API 接口文档

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.5 |
| 编写日期 | 2026-09-06 |
| 最近修订 | 2026-09-27（v1.5 考勤「时段真源统一」契约回填：补录错误码 `9110~9114`；`GET /rule` 与 `/rule/list` 新增出参 `checkPeriodsReadonly`；`GET /status` 新增出参 `shiftConfigured`；`PUT /rule` 废弃 `checkPeriods` / `checkFrequency` / `workStartTime` / `workEndTime` 入参；`periodIndex` / `periodName` 语义与存量哨兵口径统一；班次定义侧校验 `9114`；明细缺卡改班次粒度） |
| 状态 | 待评审 |
| 服务前缀 | `/api/v1`（生产经 Nginx 同域反代，本地经 Vite proxy） |
| 接口总数 | 90（= §4.0 概览行数；含 **12** 条「契约先行」行：注册域 3 + 财务域 I-1~I-9；**I-10 本批已实现**） |
| 本次范围（v1.3） | 财务域（`/api/v1/finance/**`）**首次成文收录**：既有 15 端点（工资单 10 + 计薪规则 5）+ 薪资结算自动化新增 9（I-1~I-9）；8 态状态机入 §4.12.1；错误码 9401~9416 入 §2.2（9406~9416 为本批新增，9414 / 9416 作废）；通知类型 7/8/9 入 §4.12.21。口径真源：`payroll-automation-design.md` v1.5 §2/§3/§4。 |
| 本次范围（v1.4） | **B4a 通知联动与对账补录**（口径真源：`payroll-automation-design.md` v1.5 §4.5 与 `security-payroll-automation-review.md` M-7）：① 通知类型 7/8/9 **后端落地**（`sendSystem` 独立白名单，公告白名单维持 1..6），新增运行失败/僵死回收告警类型 **10**（§4.12.21）；② 新增 **I-10** `GET /api/v1/finance/payrolls/manual-adjustments/summary`（ADMIN，§4.12.22）。 |
| 本次范围（v1.5） | **考勤「时段真源统一」契约回填**（口径真源：`attendance-time-source-design.md` v1.2 §4~§7）：时段真源改为**该驿站启用班次**，`periodIndex = shiftOrdinal(start_time)`（早 0 / 晚 1，**非数组下标**）、`periodName` = 班次名快照。① §2.2 补录 `9110~9114`（并补注 91xx 段位）；② §4.6.1 / §4.6.2 新增出参 `checkPeriodsReadonly`（恒 true）；③ §4.6.4 新增出参 `shiftConfigured`；④ §4.6.3 废弃 `checkPeriods`（非空回 400）/ `checkFrequency` / `workStartTime` / `workEndTime` 入参；⑤ §4.6.5~§4.6.9、§4.7 统一 `periodIndex`/`periodName` 与存量哨兵口径，明细缺卡改班次粒度；⑥ §4.8.2 / §4.8.3 班次定义侧校验（`9114`）；⑦ §4.9.3 / §4.9.4 排班侧 `9110` / `9111` / `9112`。**未新增任何端点**。 |
| 关联文档 | [requirement.md](requirement.md)、[db.md](db.md)、[registration-design.md](registration-design.md)、[payroll-automation-design.md](payroll-automation-design.md) |

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

> 段位与后端 `ErrorCode` 枚举一致，本表为**全域分段总表**。已展开明细的段位：通用 + 10xx~50xx + 91xx + 93xx（见 2.2）、94xx 财务段（见 2.2，9401~9416）、96xx（见 7.2）；其余段位为后端 `ErrorCode` 已定义、本文档尚未展开。

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
| 9110 | 同日班次时间重叠 | 排班侧：同员工同天班次时间重叠（半开区间 `[start,end)`，相邻不算重叠；`hrm.algo.attendance.allowShiftOverlap=true` 可显式关闭该拒绝，默认 false，§4.9.3 / §4.9.4） |
| 9111 | 单日排班班次超过上限 | 排班侧：单日班次数超上限（`hrm.algo.attendance.maxShiftsPerDay`，默认 2，§4.9.3 / §4.9.4） |
| 9112 | 同日排班须一早一晚（时段归属冲突） | 排班侧：同日各排班次 `ordinal` 冲突（`hrm.algo.attendance.requireDistinctOrdinalPerDay` 默认 true，§4.9.3 / §4.9.4） |
| 9113 | 该驿站未配置启用班次，无法打卡，请先维护班次 | 打卡 / 补卡（申请与审批）：该驿站无 `status=1` 班次，时段无真源（§4.6.5 / §4.7.3 / §4.7.4） |
| 9114 | 班次定义非法：请检查班次名称与时段归属 | 班次定义侧（`POST` / `PUT /api/v1/shifts`）：`shiftName` trim 后 = 保留哨兵名 `全天班`，或启用班次数将超上限（2），或启用班次 `ordinal` 冲突（§4.8.2 / §4.8.3） |
| 9307 | 该手机号已有进行中的入职申请，请勿重复提交 | 注册重复提交（同 phone 存在 `SUBMITTED` 申请） |
| 9308 | 入职申请不存在 | 申请单不存在（段位保留、一期未启用） |
| 9309 | 申请状态不允许该操作 | 注册审批 / 驳回时申请状态非法 |
| 9310 | （已废弃，不使用） | 原「手机号已注册」；注册提交对「是否已注册」响应恒定，不返回该码（详见 §4.11.2） |
| 9401 | 计薪规则不存在 | 规则 id 无效（财务域，§4.12.15） |
| 9402 | 工资单不存在 | 工资单 id 无效（财务域，§4.12.4） |
| 9403 | 工资单状态不允许该操作 | 状态守卫失败（财务域；亦用于「规则被工资单引用，不可删除」，见 §4.12.15） |
| 9404 | 无权查看他人工资单 | 非 ADMIN 访问非本人单据（财务域越权，§4.12.4） |
| 9405 | 该月工资单已提交审核或已发布，不可重复生成 | 生成幂等（财务域；C-7 起判定**按驿站收敛**，§4.12.5） |
| 9406 | 该驿站尚未配置算薪设置 | 新增（I-2，§4.12.16） |
| 9407 | 算薪日取值非法（须为 1-31，月末自动钳位到当月最后一天） | 新增（I-3，§4.12.16） |
| 9408 | 算薪时间格式非法（须为 HH:mm） | 新增（I-3，§4.12.16） |
| 9409 | 运行记录不存在 | 新增；号段保留（自动算薪运行记录，§4.12.18） |
| 9410 | 该驿站该账期正在运行、已占位或当日已尝试，不可重复触发 | 新增（I-4 / claim 占位 / 日粒度闸门，§4.12.18） |
| 9411 | 工资单项键已存在 | 新增（I-6，item_key 重复，§4.12.12） |
| 9412 | 加扣款事由必填（2-200 字） | 新增；**可判定业务分支**（I-6、C-3 金额变更强制事由，§4.12.11 / §4.12.12） |
| 9413 | 工资单已发放归档，不可修改 | 新增；**可判定业务分支**（`PAID` 终态冻结，统一由 `assertMutable` 收口，§4.12.1） |
| 9414 | （已作废，不使用） | 原「补跑窗口已过期」；v1.3 作废，**保留号段不复用**（日粒度重试模型下无「超窗口」概念，§4.12.18） |
| 9415 | 该驿站未启用自动算薪 | 新增（I-4 / I-5，enabled=0 时触发或查询，§4.12.18） |
| 9416 | （已作废，不使用） | 原「重试次数耗尽」；v1.3 作废，**保留号段不复用**（不得设重试硬上限，连续失败改告警，§4.12.18） |

> **91xx 考勤 / 排班 / 补卡段位**：`9101~9109` 既有；本批补录 `9110~9112`（**排班侧**：重叠 / 单日上限 / `ordinal` 冲突，端点见 §4.9）与 `9113` / `9114`（**时段真源统一**新增：`9113` 站点无启用班次，打卡与补卡申请/审批同码；`9114` 班次定义侧保留名 / 超上限 / `ordinal` 冲突，端点见 §4.8）。`9110~9112` 与 `9114` 语义域不同（排班侧 vs 定义侧），**不得互相复用**。
> **93xx 人事 / 入离职段位**：既有 `9301~9306`（人事档案 / 入离职流程，端点见 §4.10）；注册侧续号 `9307~9309`（端点见 §4.11）。手机号查重命中复用 **`2003`**（不新增 9311）；非法短信场景复用 **`400`**（不新增 1111）；`9310` 已废弃。
> **94xx 财务 / 工资单段位**：既有 `9401~9405` 随财务域契约（§4.12）**本批首次展开**；本批新增 `9406~9415`（其中 `9412` / `9413` 为可判定业务分支，建议独立码；`9406`~`9411`、`9415` 亦可用通用 `400` + 具体文案替代）；**`9414` / `9416` 于 v1.3 作废，保留号段不复用**。文案须与后端 `ErrorCode.java` 94xx 段及前端 `errorCode.js` 同步（本次仅核对、未改代码）。

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

### 4.0 接口概览（89 个）

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
| 人事·入离职 | GET | /api/v1/hr/onboarding | ADMIN | 入职流程列表 |
| 人事·入离职 | POST | /api/v1/hr/onboarding | ADMIN | 发起入职流程 |
| 人事·入离职 | GET | /api/v1/hr/onboarding/{id} | ADMIN | 入职流程详情 |
| 人事·入离职 | POST | /api/v1/hr/onboarding/{id}/steps/{key}/complete | ADMIN | 办理入职步骤 |
| 人事·入离职 | POST | /api/v1/hr/onboarding/{id}/reject | ADMIN | 驳回入职流程 |
| 人事·入离职 | GET | /api/v1/hr/offboarding | ADMIN | 离职流程列表 |
| 人事·入离职 | POST | /api/v1/hr/offboarding | ADMIN | 发起离职流程 |
| 人事·入离职 | GET | /api/v1/hr/offboarding/{id} | ADMIN | 离职流程详情 |
| 人事·入离职 | POST | /api/v1/hr/offboarding/{id}/steps/{key}/complete | ADMIN | 办理离职步骤 |
| 人事·入离职 | POST | /api/v1/hr/offboarding/{id}/reject | ADMIN | 驳回离职流程 |
| 人事档案 | GET | /api/v1/hr/profiles | ADMIN | 人事档案列表 |
| 人事档案 | GET | /api/v1/hr/profiles/{employeeId} | 登录（越权 403） | 人事档案详情 |
| 人事档案 | PUT | /api/v1/hr/profiles/{employeeId} | ADMIN | 编辑人事档案 |
| 人事档案 | GET | /api/v1/hr/salary-structures | ADMIN | 定薪列表 |
| 人事档案 | GET | /api/v1/hr/salary-structures/{employeeId} | 登录（越权 403） | 定薪详情 |
| 人事档案 | PUT | /api/v1/hr/salary-structures/{employeeId} | ADMIN | 保存定薪 |
| 员工自助注册 | POST | /api/v1/registration | 公开 | 提交注册申请（R-2，契约先行） |
| 员工自助注册 | GET | /api/v1/registration/{applyNo} | ADMIN | 申请单详情（R-3，契约先行） |
| 员工自助注册 | POST | /api/v1/hr/onboarding/{id}/approve | ADMIN | 审批通过·聚合联动（R-6，契约先行） |
| 财务·工资单 | GET | /api/v1/finance/payrolls/my | ADMIN / STATION_ADMIN / STAFF（仅本人） | 我的工资单 |
| 财务·工资单 | GET | /api/v1/finance/payrolls | ADMIN | 工资单列表（附各状态计数） |
| 财务·工资单 | GET | /api/v1/finance/payrolls/{id} | ADMIN / 本人 | 工资单详情 |
| 财务·工资单 | POST | /api/v1/finance/payrolls/generate | ADMIN | 按月批量生成（C-7） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/submit | ADMIN | 批量提交审核（C-5） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/{id}/approve | ADMIN | 审核（通过/驳回，C-5） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/publish | ADMIN | 批量发布 / 再发布（C-2） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/{id}/confirm | ADMIN / 本人 | 员工确认（C-4） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/{id}/objection | ADMIN / 本人 | 员工异议（C-1） |
| 财务·工资单 | PUT | /api/v1/finance/payrolls/{id}/items | ADMIN | 修改人工项金额（C-3） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/{id}/items/add | ADMIN | 手工加/扣款（I-6） |
| 财务·工资单 | POST | /api/v1/finance/payrolls/{id}/pay | ADMIN | 确认发放归档（I-8） |
| 财务·工资单 | GET | /api/v1/finance/payrolls/{id}/logs | ADMIN / 本人（裁剪） | 工资单操作留痕（I-7） |
| 财务·工资单 | GET | /api/v1/finance/payrolls/manual-adjustments/summary | ADMIN | 手工调整对账汇总（I-10） |
| 财务·计薪规则 | GET | /api/v1/finance/payroll-rules | ADMIN | 规则列表 |
| 财务·计薪规则 | POST | /api/v1/finance/payroll-rules | ADMIN | 新建规则 |
| 财务·计薪规则 | GET | /api/v1/finance/payroll-rules/{id} | ADMIN | 规则详情 |
| 财务·计薪规则 | PUT | /api/v1/finance/payroll-rules/{id} | ADMIN | 编辑规则 |
| 财务·计薪规则 | DELETE | /api/v1/finance/payroll-rules/{id} | ADMIN | 删除规则 |
| 财务·算薪配置 | GET | /api/v1/finance/payroll-settings | ADMIN | 驿站算薪配置列表（I-1） |
| 财务·算薪配置 | GET | /api/v1/finance/payroll-settings/{stationId} | ADMIN | 单驿站算薪配置（I-2） |
| 财务·算薪配置 | PUT | /api/v1/finance/payroll-settings/{stationId} | ADMIN | 保存算薪配置（I-3） |
| 财务·算薪配置 | GET | /api/v1/finance/payroll-settings/{stationId}/logs | ADMIN | 算薪配置变更历史（I-9） |
| 财务·自动算薪 | POST | /api/v1/finance/payroll-runs/trigger | ADMIN | 手工触发自动算薪（I-4） |
| 财务·自动算薪 | GET | /api/v1/finance/payroll-runs | ADMIN | 自动算薪运行记录（I-5） |

> 权限列：`ADMIN` = 管理员、`STATION_ADMIN` = 站长、`STAFF` = 员工（角色码与后端 `UserContext` 一致）。考勤 / 补卡 / 班次 / 排班的查询类端点对非 ADMIN 的 `stationId` **静默收敛为本人驿站**；「本人」端点以登录身份收口，详见 §4.6~§4.9。人事 / 入离职见 §4.10（入离职 10 端点全 `ADMIN`；人事档案详情任意登录角色可达，非本人非 ADMIN → 403 越权）；员工自助注册见 §4.11（**公开仅 R-1 / R-2 两条**）；财务 / 工资单见 §4.12（`/api/v1/finance/**` 共 **25** 端点）。
> **计数说明**：本表 90 = 65（原 46 + §4.10 人事 / 入离职 16 + 注册域 3）+ **财务域 25**（工资单 14 = 既有 10 + I-6 / I-7 / I-8 / **I-10**；计薪规则 5；算薪配置 4 = I-1 / I-2 / I-3 / I-9；自动算薪 2 = I-4 / I-5）。R-1 为既有公开端点 `/api/v1/auth/sms/send` 的场景扩展（仅新增 `scene=REGISTER`），不单独计入。**标注「契约先行」者为注册域新增端点**：本批仅定稿契约，R-2 / R-3 由 B3 落地、R-6 由 B4 落地。**财务域 25 条中，既有 15 条为代码已实现（`PayrollController` 10 + `PayrollRuleController` 5），I-1~I-9 为契约先行（`payroll-automation-design.md` v1.5 §4.1），I-10 已由 B4a 实现。**

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
        "position": "店员",
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

> **员工 VO 出参字段补充**：`position` = 岗位，取值 `店员` / `站长` / `管理员`；未定岗（未登记）时为 `null`（前端显示「—」）。该字段为新增出参（只增不减）；列表（§4.3.1）与详情（§4.3.2）共用同一 VO。

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
> 错误码见 §2.2「91xx 考勤 / 排班 / 补卡」段（9101–9114）；字段校验类错误统一 `400`。
> **时段序号 / 名称统一口径（v1.4，时段真源 = 该驿站启用班次）**：本域 `periodIndex`（记录 / 补卡 / 打卡出参）恒为
> **`shiftOrdinal(shift.start_time, middayBoundaryMinute)`**——早班 `0` / 晚班 `1`（界值默认 `720`，即 12:00），
> **不是时段数组下标**（单班次晚班站点唯一时段 `periodIndex = 1`，故禁用 `periods[periodIndex]` 下标取值，一律按值查找）；
> `periodName` 为**班次名快照**（`attendance_shift.shift_name`），**历史记录保持原值不改写**（存量哨兵名 `全天班` 见下）。
> **存量哨兵兼容读取**：`periodName` 命中哨兵 `全天班` → 视为覆盖当日全部班次；`periodName` 为空 → 当日单班次归该班次、
> 多班次计全部班次（不判缺）；其余按 `(workDate, periodIndex)` 定位。该三态口径为考勤概况与明细的**共同真源**。

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
| checkFrequency | int | **只读派生** = 该驿站启用班次数 × 2（无启用班次 = `0`） |
| checkPeriods | `CheckPeriod[]` | **只读派生**打卡时段，由该驿站启用班次派生（`name` = 班次名、`startTime` / `endTime` = 班次起止）；为兼容旧客户端保留字段名与形态 |
| checkPeriodsReadonly | boolean | **恒 `true`**：`checkPeriods` 已改为由班次派生的只读值，前端据此渲染「由班次决定」并隐藏编辑 |
| allowEarlyMin / allowLateMin | int | 允许提前 / 延后打卡分钟数（时间窗余量） |
| workStartTime / workEndTime | string | **只读派生** = 启用班次按 `startTime` 升序的首班开始 / 末班结束；无启用班次为 `null` |
| lateThresholdMin / earlyLeaveThresholdMin | int | 迟到 / 早退判定阈值（分钟） |
| status | int | 0=停用，1=启用 |
| updateTime | string | 最近更新时间 `yyyy-MM-dd HH:mm:ss` |

错误码：400（缺 stationId）/ 9101（该驿站尚未配置打卡规则）。

#### 4.6.2 规则列表

`GET /api/v1/attendance/rule/list`（ADMIN）

无入参，返回全量规则 `AttendanceRuleVO[]`（按 id 升序）。元素同 `4.6.1`：`checkPeriods` / `checkFrequency` / `workStartTime` / `workEndTime` **逐条按各自驿站启用班次派生**（`checkPeriodsReadonly` 恒 `true`），服务端批量预取班次后内存派生。无业务错误码（仅 401 / 403）。

#### 4.6.3 保存打卡规则

`PUT /api/v1/attendance/rule`（ADMIN）

请求体为**差量更新**：字段缺省（`null`）表示沿用现值。**时段真源已改为「该驿站启用班次」**：`checkPeriods` / `checkFrequency` / `workStartTime` / `workEndTime` **不再由本接口写入**，出参一律按启用班次派生（见 4.6.1）。首次为某驿站保存时创建记录并套用默认规则（围栏与阈值部分，不再播种时段）。
> **发布顺序约束**：现网 web `RuleCard.vue` 与 boss-h5 `attendanceRule.vue` 若仍上报 `checkPeriods`，则「本接口非空 `checkPeriods` 回 `400`」的新口径**必须与前端去掉上报的改动同批发布**，否则现网规则保存将全面 `400`。

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| stationId | long | 是 | 须为存在驿站（4001）；缺省 → 400「缺少 stationId」 |
| ruleName | string | 否 | 1-50 字符 |
| enableWifi / enableLocation / enableTimeWindow | boolean | 否 | — |
| matchMode | string | 否 | 仅 `ALL` / `ANY` |
| wifiList | `WifiEntry[]` | 否 | 见下表「`wifiList` 约束」；缺省沿用现值、显式 `[]` 清空 |
| longitude / latitude | decimal | 否 | — |
| radius | int | 否 | > 0 |
| checkFrequency | int | 否 | **废弃：忽略入参**；出参只读派生（见 4.6.1） |
| checkPeriods | `CheckPeriod[]` | 否 | **废弃：显式非空即回 `400`**「时段已由该驿站班次决定，请维护班次」；缺省 / `[]` 放行，不再写入、不再校验段数量 / 重叠（见下「`checkPeriods` 入参口径」） |
| allowEarlyMin / allowLateMin | int | 否 | ≥ 0 |
| lateThresholdMin / earlyLeaveThresholdMin | int | 否 | ≥ 0 |
| workStartTime | string | 否 | **废弃：忽略入参**；出参只读派生 |
| workEndTime | string | 否 | **废弃：忽略入参**；出参只读派生 |
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

**`checkPeriods` 入参口径（U-5 已裁定；真源统一后时段由班次决定）**：

| 情形 | 口径 |
| ---- | ---- |
| 缺省（`null`）/ 空数组 `[]` | **放行**（不写入、不校验） |
| 显式非空数组 | **拒绝** → `400`「时段已由该驿站班次决定，请维护班次」（**不采用静默忽略**，防管理员误以为已改时段） |
| 错误码 | 统一用**通用 `400`**（`ErrorCode.BAD_REQUEST`）；**不新增 91xx、不复用 `9107` / `9113` / `9114`** |

`workStartTime` / `workEndTime` / `checkFrequency` 入参一律**忽略**（出参改派生）；时段维护入口为 `POST` / `PUT /api/v1/shifts`（见 §4.8）。

**旧数据 `wifiList.length > 1` 的边界口径**（面向将来的兜底；现网实测无历史多条数据）：

| 环节 | 口径 |
| --- | --- |
| 加载（`GET /rule`） | 对旧数据 `>1` 条**原样返回**（逐条复制，不裁剪、不改写） |
| 提交（`PUT /rule`） | 恒要求 **≤ 1 条**；`>1` 返回 `400`（兜底，防 PC 端 / 直调 API 绕过前端） |
| 前端收敛 | 加载到 `>1` 条时**只渲染首条并提示**（前端责任），服务端不代劳裁剪 |

响应 `data` 为保存后的 `AttendanceRuleVO`（`checkPeriods` / `checkFrequency` / `workStartTime` / `workEndTime` 为班次派生的只读值，`checkPeriodsReadonly=true`）。错误码：400（含非空 `checkPeriods` 拒绝）/ 4001。

#### 4.6.4 今日打卡状态

`GET /api/v1/attendance/status`（ADMIN / STATION_ADMIN / STAFF）

无入参，以登录人身份收口。响应 `data` 为 `AttendanceStatusVO`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| workDate | string | 今日 `yyyy-MM-dd` |
| hasSchedule | boolean | 今日是否有排班 |
| shift | `AttendanceShiftVO` | 今日班次；未排班时取该驿站**首个启用班次**合成的**兜底班次**（`id=null`、`shiftName` 取该班次名），无启用班次时为 `null` |
| shiftConfigured | boolean | 该驿站是否已配置启用班次（`status=1`）；`false` 表示无打卡时间真源，前端据此禁用打卡并引导「去维护班次」（打卡将回 9113） |
| onChecked / offChecked | boolean | 今日上 / 下班卡是否已完成（有效卡，排除 `ABNORMAL`） |
| onRecord / offRecord | `AttendanceRecordVO` | 今日最近一次有效上 / 下班卡（无则 `null`） |
| checkFrequency | int | **只读派生** = 启用班次数 × 2（无规则时 `null`，无启用班次 = `0`） |
| requireSummary | string | 规则要求摘要（无规则时 `null`） |
| periods | `PeriodStatus[]` | 按**启用班次**展开：`periodIndex` = 班次序号（早 `0` / 晚 `1`，**非数组下标**）/ `name` = 班次名 / `startTime` / `endTime` / `windowStart`（班次开始 − `allowEarlyMin`）/ `windowEnd`（班次结束 + `allowLateMin`）/ `onChecked` / `offChecked` / `onTime` / `offTime` |
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
| periodIndex | int | 否 | 缺省走单班次模型（以排班班次 / 首个启用班次为时间基准）；传入则按该班次序号判定（`periodIndex` = 班次序号，早 `0` / 晚 `1`，**按值查找、非数组下标**）。站点无启用班次 → 9113；无匹配序号 → 9107 |

判定链顺序（不得变更）：规则 → 时段 / 班次 → 时间窗 → 重复 → 校验项（WiFi / 定位，按 `matchMode` ALL/ANY）→ 迟到 / 早退。

服务端判定，**校验未通过仍落一条 `ABNORMAL` 留痕记录**后回码（时间窗越窗与重复打卡除外，此二者在落库前短路、不留痕）。响应 `data` 为 `AttendanceRecordVO`（其中 `periodIndex` = 命中班次序号、`periodName` = 命中班次名快照）。

错误码：400 / 9101（未配规则）/ 9113（站点无启用班次，无时间真源）/ 9107（时段 / 班次序号不存在）/ 9106（班次不存在或已停用，单班次模型）/ 9102（不在时间窗内，不留痕）/ 9105（今日该类型打卡已完成，不留痕）/ 9103（WiFi 校验未通过，留痕）/ 9104（定位未通过，留痕）。

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
> `periodIndex` / `periodName` 取值口径见 §4.6 前言（`periodIndex` = 班次序号，早 `0` / 晚 `1`；`periodName` = 写入时班次名快照；**存量记录保持原值不改写**，哨兵值 `全天班` 原样返回）。

#### 4.6.7 考勤记录导出

`GET /api/v1/attendance/export`（ADMIN / STATION_ADMIN）

入参与筛选口径同 4.6.6（**不分页，全量导出**）。响应为 CSV 文件流（`Content-Type: text/csv`，文件名 `考勤记录_yyyyMMdd.csv`），13 列：员工姓名 / 登录账号 / 所属驿站 / 日期 / **班次/时段** / 卡类型 / 打卡时间 / 打卡方式 / WiFi / 距离(米) / 状态 / 来源 / 备注（枚举列输出中文，空值输出空串；第 5 列取 `periodName` 快照，**列序与列数保持 13 列不变**）。内存流写出，不落盘。错误码：400。

#### 4.6.8 打卡概况

`GET /api/v1/attendance/summary`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 否 | 非 ADMIN 静默收敛为本人驿站 |
| date | string | 否 | 统计日期 `yyyy-MM-dd`，缺省今天；格式非法 400 |

响应 `data` 为 `AttendanceSummaryVO`：`date` / `shouldCount`（**应到 = 当天排班班次数**，一天两班计 2）/ `actualCount`（**实到 = 有效上班卡映射到班次后与应到取交** `|A∩R|`，只减不增、多打卡不超额）/ `normalCount` / `lateCount` / `earlyLeaveCount`（按有效上班卡 / 下班卡条数逐班次统计）/ `absentCount`（**缺卡 = 应到班次数去掉已到班次数**，某班次无匹配有效上班卡即缺）。缺卡粒度为可配项 `hrm.algo.attendance.absentGranularity`（默认 `PER_SHIFT` 班次粒度；`PER_DAY` 为旧「按人/天去重」回落口径）。**`absentCount` 与明细 `dim=ABSENT` 的 `total` 同源等值**（见 4.6.9）。错误码：400。

#### 4.6.9 考勤明细

`GET /api/v1/attendance/detail`（ADMIN / STATION_ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| stationId | long | 否 | 非 ADMIN 静默收敛为本人驿站 |
| dim | string | 是 | 维度，白名单 `SHOULD` / `ACTUAL` / `NORMAL` / `LATE` / `EARLY_LEAVE` / `ABSENT`，非法或缺省 400 |
| date | string | 否 | 统计日期，缺省今天；格式非法 400 |

响应 `data` 为 `AttendanceDetailVO`：`dim` / `date` / `total` / `list[]`（行含 `employeeId` / `employeeName` / `stationId` / `stationName` / `shiftName`（仅 SHOULD/ABSENT）/ `periodName` / `onCheck` / `offCheck`（各含 `time` / `status`）/ `dayState`（`MISS` / `LATE` / `EARLY_LEAVE` / `NORMAL`）/ `remark`）。排序：迟到 / 早退按命中卡时间倒序，缺卡按姓名，应到按风险优先，实到 / 正常按上班卡时间倒序。错误码：400。
> **缺卡（ABSENT）按班次粒度**（与概况同源，见 4.6.8）：明细行以「员工 × 班次」为单位，某员工某班次无匹配有效上班卡即出一行缺卡，`total` 与概况 `absentCount` 等值。**多班次站点同一员工可出多行**（单班次站点行为与改造前<b>逐项一致</b>）；`shiftName` 取该班次名，`periodName` 取代表卡 / 班次快照。**存量记录保持原值不改写**（哨兵 `全天班`、空名按 §4.6 前言三态口径读取）。

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
> `periodIndex` / `periodName` 与本域 §4.6 前言同一口径（`periodIndex` = 班次序号，早 `0` / 晚 `1`，按值查找、非数组下标；`periodName` = 班次名快照）。站点无启用班次时，**申请与审批一律回 `9113`**（同码同文案）。

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
| periodIndex | int | 是 | ≥ 0；须命中该驿站启用班次派生的某个班次序号（`periodIndex` = 班次序号，按值查找），否则 9107 |
| checkType | string | 是 | `ON` / `OFF` |
| reason | string | 是 | 2-200 字 |

校验顺序：规则 → 时段（班次派生）→ 重复申请 → 已有正常打卡。申请人为登录人本人，`stationId` 取登录人归属（未归属 → 400「当前账号未归属驿站，无法提交补卡」）。响应 `data` 为 `AttendanceMakeupVO`。

错误码：400 / 9101（该驿站尚未配置打卡规则）/ **9113（该驿站未配置启用班次，无法打卡）** / 9107（班次序号 / 时段不存在）/ 9108（该时段当日已有补卡申请或已正常打卡）。

#### 4.7.4 审批补卡

`POST /api/v1/attendance/makeup/{id}/approve`（ADMIN）

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| approved | boolean | 是 | 缺省 / 非布尔 → 400「approved 须为布尔值」 |
| approveRemark | string | 否 | ≤ 200 字 |

审批通过 → 补录打卡记录：打卡时间取该**班次**规定时间（上班卡取班次开始、下班卡取班次结束），`source=MAKEUP`，设备校验字段（`checkMode` / `wifiSsid` / `wifiMatched` / `longitude` / `latitude` / `distance` / `locationMatched`）统一置 `null`（不伪造命中值）。审批通过前会干跑校验可写性，按原因回码：**站点无启用班次 → 9113**；时段被改配置导致原班次序号不存在 → **9101**（避免落下「审批通过却无打卡记录」的矛盾数据）。

响应 `data` 为 `AttendanceMakeupVO`（含 `id` / `employeeId` / `employeeName` / `stationId` / `stationName` / `workDate` / `periodIndex` / `periodName` / `checkType` / `reason` / `status` / `applyTime` / `approverId` / `approverName` / `approveTime` / `approveRemark`）。错误码：400 / 404（补卡申请不存在）/ 9109（补卡申请状态不允许该操作）/ **9113（站点无启用班次）** / 9101（规则未配置或原班次序号不存在）。

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
| shiftName | string | 是 | 1-20 字符；**trim 后不得为保留哨兵名**（见下） |
| startTime / endTime | string | 是 | `HH:mm`；`endTime` 可 `24:00`；结束须晚于开始 |
| color | string | 是 | `#RRGGBB`（存储归一为大写） |
| restMinutes | int | 否 | ≥ 0，缺省 0 |
| status | int | 否 | 缺省 1 |

**班次定义侧校验**（`validateShift` + `validateShiftSet`，违反回 `9114`，具体由 `message` 说清）：

| 项 | 约束 | 判据（违反即 9114） |
| --- | --- | --- |
| 保留名 | `shiftName` **trim 后**不得等于计薪哨兵名 `全天班`（默认，`hrm.algo.payroll.legacyPeriodSentinel`） | trim 后 = `全天班` |
| 启用班次数上限 | 该驿站 `status=1` 班次数（含本次）≤ **2** | > 2 |
| `ordinal` 互异 | 同一驿站启用班次的 `ordinal`（`start_time` < 界值 → 早 `0`，否则晚 `1`）须互异，保证「一早一晚」 | 新增 / 编辑后启用班次 `ordinal` 冲突 |

> `9114` 为**班次定义侧**语义（保留名 / 超上限 / `ordinal` 冲突），**不复用**排班侧 `9111` / `9112`；停用（`status=0`）班次不计入启用班次数与 `ordinal` 互异校验。

响应 `data` 为 `AttendanceShiftVO`。错误码：400 / 4001 / 9114。

#### 4.8.3 编辑班次

`PUT /api/v1/shifts/{id}`（ADMIN）

入参同 4.8.2（`status` 改为 0 = 停用可把该班次移出启用集），但 `stationId` **忽略**（归属不可改）；班次定义侧校验同 4.8.2（编辑时排除自身后重新校验启用班次数与 `ordinal` 互异）。响应 `data` 为 `AttendanceShiftVO`。错误码：400 / 404（班次不存在）/ 9114（保留名 / 超上限 / `ordinal` 冲突）。

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

唯一性 = `employeeId + workDate`（同一员工同一天重复提交即覆盖）。整批为**原子提交**（`@Transactional`；与 Mock「逐条应用、中途报错留下部分改动」有意不同，实时后端不出现半成功状态）。响应 `data` 为 `{ saved, removed }`（新增 / 改派条数、清空条数）。

> **排班时段约束（触发 9110 / 9111 / 9112）**：同一员工同天班次时间**重叠**（半开区间 `[start,end)`，相邻不算）→ `9110`（`hrm.algo.attendance.allowShiftOverlap=true` 可关闭）；单日班次数超上限 `hrm.algo.attendance.maxShiftsPerDay`（默认 2）→ `9111`；同日启用班次 `ordinal` 冲突 → `9112`（`hrm.algo.attendance.requireDistinctOrdinalPerDay` 默认 true）。整批原子，命中即回码、不落半批。

错误码：400 / 9106 / 9110 / 9111 / 9112。

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

响应 `data` 为 `ScheduleStationResultVO`：`created` / `skipped` / `total`（= created + skipped）/ `violations[]`（仅智能模式返回，手动模式省略）/ `fallback`（是否走了失败降级）。排班时段约束（重叠 / 单日上限 / `ordinal` 冲突，触发 `9110` / `9111` / `9112`）同 4.9.3。错误码：400 / 4001 / 9106 / 9110 / 9111 / 9112。

### 4.10 人事 / 入离职接口

> 端点由 `HrFlowController`（入离职流程，10 个）与 `HrProfileController`（人事档案与定薪，6 个）提供，路径前缀 `/api/v1/hr`；全部端点均需登录（`Authorization: Bearer {token}`）。
> 角色门槛：入离职 10 端点**全部仅 `ADMIN`**；人事档案的**列表与写操作仅 `ADMIN`**，**详情端点**任意登录角色可达，但**越权（非本人且非 `ADMIN`）→ 403**（`STATION_ADMIN` 亦仅能查看本人档案 / 定薪）。
> 状态与步骤真源（`HrConstants`）：流程状态 `IN_PROGRESS` / `COMPLETED` / `REJECTED`；**入职步骤**顺序 `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY → DONE`；**离职步骤**顺序 `MANAGER_APPROVE → HR_APPROVE → HANDOVER → ASSET_RETURN → SETTLEMENT → LEAVE`。办理步骤时目标步须为**首个待办理（`PENDING`）步**，跳步 / 重复办理 / 状态非法统一 → 9303（入职）/ 9304（离职）。
> 错误码见 §2.2「93xx 人事 / 入离职」段（9301~9306）；入参格式 / 取值类错误统一 `400`。

#### 4.10.1 入职流程列表

`GET /api/v1/hr/onboarding`（ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |
| status | string | 否 | `IN_PROGRESS` / `COMPLETED` / `REJECTED`；其他值 400 |
| stationId | long | 否 | 驿站过滤（精确） |
| keyword | string | 否 | 候选人姓名 / 员工姓名 / 流程编号 模糊匹配 |

排序固定 `create_time DESC, id DESC`。响应 `data` 为分页结构，`list` 元素为 `HrFlowVO`（字段见 §4.10.5 后的「`HrFlowVO` 字段」）。错误码：400。

#### 4.10.2 发起入职流程

`POST /api/v1/hr/onboarding`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| candidateName | string | 是 | 2-20 字 |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| gender | int | 否 | 0/1/2（0=未知，1=男，2=女），缺省 0 |
| education | string | 否 | 白名单 `MASTER` / `BACHELOR` / `COLLEGE` / `HIGH_SCHOOL` |
| deptId | long | 否 | 部门须存在（否则 400「指定的部门不存在」） |
| stationId | long | 否 | 驿站须存在（否则 400「指定的驿站不存在」） |
| position | string | 否 | 岗位；**取值 `店员` / `站长` / `管理员`**（缺省留空；非法值 → 400「岗位仅支持 店员 / 站长 / 管理员」） |
| expectedEntryDate | string | 否 | `yyyy-MM-dd`，缺省今天 |
| remark | string | 否 | ≤ 200 字 |

行为：新建 `hr_flow`（`flow_type=ONBOARDING`、`role=STAFF`（恒）、`status=IN_PROGRESS`）并生成全部步骤（`PENDING`）；`operator_id / operator_name` 记当前登录人。响应 `data` 为 `HrFlowVO`。错误码：400。

#### 4.10.3 入职流程详情

`GET /api/v1/hr/onboarding/{id}`（ADMIN）

响应 `data` 为 `HrFlowVO`。错误码：404（流程不存在或非入职类型）。

#### 4.10.4 办理入职步骤

`POST /api/v1/hr/onboarding/{id}/steps/{key}/complete`（ADMIN）

`key` 取入职步骤键之一（非法 → 400「步骤标识非法」）。入参 `HrStepCompleteRequest` **按 `key` 取用**（未用字段忽略）：

| 入参 | 类型 | 适用步骤 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| remark | string | 全部 | ≤ 200 字 |
| expectedEntryDate | string | 全部（仅入职） | `yyyy-MM-dd` |
| username | string | `CREATE_ACCOUNT` | 字母开头、4-30 位字母数字下划线（`^[a-zA-Z][a-zA-Z0-9_]{3,29}$`） |
| password | string | `CREATE_ACCOUNT` | 8-20 位且含字母与数字 |
| deptId | long | `CREATE_ACCOUNT` / `ASSIGN_STATION` | 部门须存在（3001） |
| stationId | long | `CREATE_ACCOUNT` / `ASSIGN_STATION` | 驿站须存在（4001）且启用（4004） |
| probationMonths | int | `CREATE_ACCOUNT` | 试用期（月） |
| contractType | string | `CREATE_ACCOUNT` | 合同类型 |
| position | string | `ASSIGN_STATION` | **必填**；取值 `店员` / `站长` / `管理员`；命中则**双写 `employee.position`（权威事实）+ `hr_flow.position`（留痕）**（空白 → 400「请填写岗位」；非法 → 400「岗位仅支持 店员 / 站长 / 管理员」） |
| role | string | `ASSIGN_STATION` | 仅 `STATION_ADMIN` / `STAFF`（其他 400） |
| basicSalary / postSalary / performanceBase | decimal | `SET_SALARY` | ≥ 0（`HrSalaryValidator`） |
| allowances | `AllowanceItem[]` | `SET_SALARY` | 元素 `{key?, name(1-20), amount(≥0)}` |
| effectiveDate | string | `SET_SALARY` | `yyyy-MM-dd`，缺省取流程 `expectedEntryDate` |
| reason | string | `SET_SALARY` | 定薪原因 |

行为：通过**按序守卫**后，按 `key` 执行副作用并标记该步 `DONE`、重算 `current_step_key`；全部步骤 `DONE` 且流程 `IN_PROGRESS` → 置 `COMPLETED`。副作用：`CREATE_ACCOUNT` 建员工与账号（员工先落 `status=0` 未生效）、`ASSIGN_STATION` 分配驿站 / 岗位 / 角色、`SET_SALARY` 建档定薪、`DONE` 员工转在职（`status=1`）；`SUBMIT_MATERIALS` / `HR_REVIEW` 无副作用仅标记完成。响应 `data` 为 `HrFlowVO`。错误码：400 / 404 / 1003（账号重复）/ 3001 / 4001 / 4004 / 9303。

#### 4.10.5 驳回入职流程

`POST /api/v1/hr/onboarding/{id}/reject`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| reason | string | 是 | 2-200 字 |

行为：仅 `IN_PROGRESS` 可驳回（否则 9303）；置 `status=REJECTED`、记 `reject_reason / rejected_by / rejected_time`；**已建档员工一并禁用（`status=0`）**。响应 `data` 为 `HrFlowVO`。错误码：400 / 404 / 9303。

> **`HrFlowVO` 字段**（入职 / 离职共用，未用字段为 `null`；`phone` 脱敏）：
> `id, flowType, flowNo, candidateName, employeeId, employeeName, phone(脱敏), gender, education, educationLabel, deptId, stationId, stationName, position, role, expectedEntryDate, type, typeLabel, reason, lastWorkDate, settlementPayrollId, settlementPayrollNo, settlementAmount, leaveDate, remark, status, statusLabel, rejectReason, rejectedBy, rejectedTime, currentStepKey, currentStepName, progress{done,total}, steps[{key, name, order, status, statusLabel, operatorId, operatorName, operateTime, remark}], createTime, updateTime, operatorId, operatorName`

#### 4.10.6 离职流程列表

`GET /api/v1/hr/offboarding`（ADMIN）。入参与排序同 §4.10.1，`list` 元素为 `HrFlowVO`（离职语义字段）。错误码：400。

#### 4.10.7 发起离职流程

`POST /api/v1/hr/offboarding`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| employeeId | long | 是 | 员工须存在（404） |
| type | string | 是 | `RESIGN` / `DISMISS` / `RETIRE` |
| reason | string | 是 | 2-200 字 |
| lastWorkDate | string | 是 | `yyyy-MM-dd` |

行为：员工须**在职（`status=1`）**，否则 9302「该员工已离职或账号已禁用，不可发起离职」；**同一员工不得有进行中的离职流程**（否则 9304）。`station_id` 取该员工驿站；`operator_id / operator_name` 记当前登录人。响应 `data` 为 `HrFlowVO`。错误码：400 / 404 / 9302 / 9304。

#### 4.10.8 离职流程详情

`GET /api/v1/hr/offboarding/{id}`（ADMIN）。响应 `data` 为 `HrFlowVO`。错误码：404（流程不存在或非离职类型）。

#### 4.10.9 办理离职步骤

`POST /api/v1/hr/offboarding/{id}/steps/{key}/complete`（ADMIN）

`key` 取离职步骤键之一（非法 → 400「步骤标识非法」）；入参仅 `remark`（≤200 字）有语义。行为：通过按序守卫后标记该步 `DONE`；`SETTLEMENT` 经跨域端口创建离职结算单（失败 → 9306），`LEAVE` 完成离岗（员工转离职）。响应 `data` 为 `HrFlowVO`。错误码：400 / 404 / 9304 / 9306。

#### 4.10.10 驳回离职流程

`POST /api/v1/hr/offboarding/{id}/reject`（ADMIN）。入参与行为同 §4.10.5（状态非法 → **9304**）；**不联动禁用员工**。错误码：400 / 404 / 9304。

#### 4.10.11 人事档案列表

`GET /api/v1/hr/profiles`（ADMIN）

| 入参（Query） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNum / pageSize | int | 否 | 同 §1.1 |
| deptId | long | 否 | 部门**精确**过滤（不做子部门展开，与 §4.3.1 不同） |
| stationId | long | 否 | 驿站精确过滤 |
| keyword | string | 否 | 姓名 / 登录账号 模糊匹配 |

行为：**仅返回已存在人事档案**的员工行（无档案者不出现）；排序 `id ASC`。响应分页 `HrProfileVO[]`。错误码：仅 401 / 403。

#### 4.10.12 人事档案详情

`GET /api/v1/hr/profiles/{employeeId}`（ADMIN / STATION_ADMIN / STAFF；**越权 403**）

行为：非本人且非 `ADMIN` → 403「无权查看他人人事档案」；档案不存在 → 9301。响应 `data` 为 `HrProfileDetailVO`（= `HrProfileVO` + `salary` 定薪摘要，无定薪时 `salary=null`）。错误码：403 / 9301。

> **`HrProfileVO` 字段**（`phone` / `emergencyContactPhone` / `bankAccount` 一律脱敏）：
> `employeeId, employeeName, username, phone(脱敏), deptName, stationName, position(岗位，取值 店员 / 站长 / 管理员；未登记为 null), entryDate, education, educationLabel, contractType, contractTypeLabel, contractStart, contractEnd, probationMonths, probationEnd, regularDate, socialSecurityBase, emergencyContactName, emergencyContactPhone(脱敏), emergencyContactRelation, bankName, bankAccount(脱敏), leaveDate, createTime, updateTime`

#### 4.10.13 编辑人事档案

`PUT /api/v1/hr/profiles/{employeeId}`（ADMIN）

**白名单写入**：DTO 不含 `employeeId / leaveDate`（防越权改归属与伪造离职）；**仅写显式传入（非 null）字段**，空请求体不会误清空。

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| education | string | 否 | `MASTER` / `BACHELOR` / `COLLEGE` / `HIGH_SCHOOL` |
| contractType | string | 否 | `FIXED_TERM` / `NON_FIXED_TERM` / `INTERN` / `DISPATCH` |
| contractStart / contractEnd | string | 否 | `yyyy-MM-dd` |
| probationMonths | int | 否 | 0-12 |
| probationEnd / regularDate | string | 否 | `yyyy-MM-dd` |
| socialSecurityBase | decimal | 否 | ≥ 0 |
| emergencyContactName | string | 否 | 2-20 字 |
| emergencyContactPhone | string | 否 | 手机号格式 |
| emergencyContactRelation | string | 否 | 关系 |
| bankName | string | 否 | 2-50 字 |
| bankAccount | string | 否 | 12-25 位数字 |

行为：档案不存在 → 9301；**已离职（`leave_date` 非空）→ 9302**。响应 `data` 为 `HrProfileVO`。错误码：400 / 9301 / 9302。

#### 4.10.14 定薪列表

`GET /api/v1/hr/salary-structures`（ADMIN）。入参同 §4.10.11；**仅返回已存在定薪档案**的员工行，排序 `id ASC`。响应分页 `HrSalaryVO[]`。错误码：仅 401 / 403。

#### 4.10.15 定薪详情

`GET /api/v1/hr/salary-structures/{employeeId}`（ADMIN / STATION_ADMIN / STAFF；**越权 403**）

行为：非本人且非 `ADMIN` → 403「无权查看他人定薪档案」；定薪档案不存在 → 9305。响应 `data` 为 `HrSalaryDetailVO` = `{ current: HrSalaryVO, histories: HrSalaryLogVO[] }`（留痕按 `effective_date DESC, id DESC`）。错误码：403 / 9305。

> **`HrSalaryVO` 字段**：`employeeId, employeeName, basicSalary, postSalary, performanceBase, allowances[{key, name, amount}], allowancesTotal, totalSalary, effectiveDate, updateTime`
> **`HrSalaryLogVO` 字段**：`id, effectiveDate, changeType(ENTRY/ADJUST), changeTypeLabel, basicSalary, postSalary, performanceBase, allowances[], allowancesTotal, totalSalary, reason, operatorId, operatorName, createTime`

#### 4.10.16 保存定薪

`PUT /api/v1/hr/salary-structures/{employeeId}`（ADMIN）

| 入参 | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| basicSalary / postSalary / performanceBase | decimal | 否 | ≥ 0 |
| allowances | `AllowanceItem[]` | 否 | 传数组则整表替换；元素 `{key?, name(1-20), amount(≥0)}` |
| effectiveDate | string | 否 | `yyyy-MM-dd`，缺省今天 |
| reason | string | 否 | 2-50 字 |

行为：**覆盖当前档案 + 追加一条调薪留痕**（`changeType=ADJUST`）；未传字段保持现值；定薪档案不存在 → 9305；已离职（`leave_date` 非空）→ 9302；操作人记当前登录人。响应 `data` 为 `HrSalaryDetailVO`。错误码：400 / 9302 / 9305。

### 4.11 员工自助注册接口

> 本节为**注册域契约定稿**（依据 [registration-design.md](registration-design.md) v1.3，技术评审复评结论「通过（工程面）」）。R-1 复用既有公开端点，R-2 / R-3 / R-6 为新增端点（R-2 / R-3 由 B3 落地、R-6 由 B4 落地，见 §4.0「契约先行」标注）。
> **公开端点仅 2 个**：R-1（复用 `POST /api/v1/auth/sms/send`，仅新增 `scene=REGISTER`；路径**既已在白名单**，净新增 0 条）+ R-2（`POST /api/v1/registration`，`PublicEndpoints` **净新增 1 条**）。R-3 为 **ADMIN-only**；R-6 为 **ADMIN**。
> **越权口径**：申请人**无自助端点**（不做自助撤回 / 进度查询）；R-2 提交仅凭短信验证码（须持有该手机号）；R-3 / R-6 仅 `ADMIN`（站长 `STATION_ADMIN` 不可审）；**不得以手机号单独查询**（防遍历）。未过审数据由超时清理 + ADMIN 驳回处置。

#### 4.11.1 R-1 注册验证码下发（复用 `POST /api/v1/auth/sms/send`）

`POST /api/v1/auth/sms/send`（公开）。**本场景仅新增入参取值 `scene=REGISTER`**；端点及其余入参 / 出参沿用既有短信下发契约。

| 入参 | 类型 | 必填 | 校验 |
| ---- | ---- | ---- | ---- |
| scene | string | 否 | `REGISTER`（本方案新增）；**缺省（空 / 空白）按 `LOGIN`**；**非空非法值 → 400「不支持的短信场景」**（不再回落 `LOGIN`） |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| deviceId | string | 否 | 弱信号（参与同设备维度限频） |
| captchaTicket / captchaCode | string | 否 | `captcha-enabled=true` 时必填（1106） |

行为（**定稿**）：注册场景**不要求手机号已存在**，发码前**不判定「是否已注册」**（防枚举）；频控 4 维复用，`identifier = phone`。

**发码恒定性（本方案定稿，同时覆盖 `LOGIN` 与 `REGISTER` 两场景）**：对**任意手机号**（已注册 / 未注册 / 未绑定）返回体 `{code, message, data}`、HTTP 状态与业务码、**耗时量级逐字段一致**；未注册号作**静默成功**（不真发码）。**发码路径不再返回 1109**（消除「是否注册」的枚举差异面）。

响应（`SmsSendVO`）：`{ sent, expireIn, nextAllowedIn, requireCaptcha }`；**绝不包含验证码**。验证码存储键 `hrm:sms:code:REGISTER:{phone}`（沿用短信域键命名，前缀 `hrm:`）。

错误码：400 / 1101 / 1105 / 1106（**不出现 1109**）。

#### 4.11.2 R-2 提交注册申请

`POST /api/v1/registration`（公开，本方案唯一净新增公开端点）

| 入参（白名单，`intent*` 表「意向」） | 类型 | 必填 | 校验（违反即 400） |
| ---- | ---- | ---- | ---- |
| realName | string | 是 | 2-20 字 |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| smsCode | string | 是 | 6 位；走 `REGISTER` 场景校验（1102 / 1103，校验成功一次性作废） |
| intentStationId | long | 是 | **仅意向**；须存在（4001）且启用（4004）；**不落 `employee.station_id`** |
| intentPosition | string | 否 | ≤ 50 字（自由文本，**仅意向**） |
| password | string | 否 | 8-20 位且含字母与数字（**合规留痕，不作为员工口令**） |
| agreementVersion | string | 是 | 服务条款版本（合规留痕） |

> **硬约束（防注册注入）**：DTO **不含** `role / deptId / stationId(事实) / basicSalary / postSalary / performanceBase / allowances / status / pwdChanged`；全局 `FAIL_ON_UNKNOWN_PROPERTIES=true`，夹带未知字段 → 400。数据采集字段与审批赋值字段**严格分离**（完整字段分离表见 `registration-design.md` §11.5）。

行为（**单事务**）：

1. 校验 `REGISTER` 场景验证码（1102 / 1103）；
2. **重复提交判定**：同 `phone` 存在 `SUBMITTED` → 9307；
3. **已注册判定（响应恒定）**：即使 `employee.phone` 活跃命中，**仍按正常流程建单**（受理外观恒定），由 ADMIN 审批台识别（命中在审批台提示 + 唯一约束兜底）；**不返回 9310**；
4. 建 `employee_registration`（`status=SUBMITTED`；`password_hash=BCrypt(password)` 仅留痕；生成 `apply_no`）；
5. 建 `hr_flow`（`flow_type=ONBOARDING`、`role=STAFF`（恒）、`status=IN_PROGRESS`、`operator_id / operator_name=null`、`source=SELF_REGISTER`）；
6. 回填 `registration.flow_id`；返回 `{ applyNo, status, createTime }`。

```json
// 请求（无 role / 薪资 / deptId）
{ "realName": "李四", "phone": "13912345678", "smsCode": "123456",
  "intentStationId": 3, "intentPosition": "分拣员", "agreementVersion": "v1.0" }

// 响应（HTTP 200，对「已注册 / 未注册」逐字段一致）
{ "code": 200, "message": "success",
  "data": { "applyNo": "RG-20260926-0001", "status": "SUBMITTED", "createTime": "2026-09-26 10:00:00" } }
```

错误码：400 / 9307 / 4001 / 4004 / 1102 / 1103（**无 9310**）。

#### 4.11.3 R-3 申请单详情

`GET /api/v1/registration/{applyNo}`（ADMIN）

行为：按 `applyNo` 查申请单；出参**脱敏**（`phone` 掩码），**不返回** `password_hash / query_token_hash`。

响应 `data`：`{ applyNo, realName, phone(脱敏), intentStationId, stationName, intentPosition, status, statusLabel, rejectReason, createTime, approveTime, source, clientIp(脱敏) }`（与 §4.11.5 R-8 的 `registration` 子对象**同构**）。错误码：400 / 404（`9308` 段位保留、一期未启用）。

#### 4.11.4 R-6 注册审批通过（聚合联动）

`POST /api/v1/hr/onboarding/{id}/approve`（ADMIN）

| 入参（**ADMIN 赋值字段**） | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| username | string | 否 | 登录账号；缺省按 `u` + 手机号生成；活跃唯一（1003） |
| initialPassword | string | 是 | **ADMIN 一次性初始口令**（**不复用注册密码**）；8-20 位含字母数字 |
| deptId | long | 是 | 部门（注册不采集，审批必填）；不存在 → 3001 |
| stationId | long | 否 | 缺省取 `hr_flow.station_id`（意向）；停用 → 4004 |
| role | string | 否 | 缺省 `STAFF`；仅 `STATION_ADMIN` / `STAFF` |
| position | string | 是 | 岗位（**审批台必填**，主智能体裁定）；取值 `店员` / `站长` / `管理员`；**定岗时双写 `employee.position`（权威事实）+ `hr_flow.position`（留痕）**（空白 → 400「请填写岗位」；非法 → 400「岗位仅支持 店员 / 站长 / 管理员」） |
| probationMonths | int | 否 | 缺省取 `hrm.hr.default-probation-months` |
| contractType | string | 否 | 缺省 `FIXED_TERM` |
| basicSalary / postSalary / performanceBase / allowances | decimal / `AllowanceItem[]` | 是 | 定薪（**必填**，防工资单静默为 0） |
| effectiveDate | string | 否 | 缺省取流程 `expectedEntryDate` |
| remark | string | 否 | 0-200 字 |

行为（**单事务，仅前三步副作用——审批不自动激活**）：

1. 事务首条 `SELECT ... FOR UPDATE` 锁 `hr_flow` 单行；
2. 按序推进 5 步 `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY`（复用既有 `completeOnboardingStep`，**不含 `DONE`**）；
3. 同步 `registration.status=APPROVED` + 回填 `approved_employee_id` + 清空凭据列；
4. **不置 `COMPLETED`**：`hr_flow` 保持 `IN_PROGRESS`、`employee.status=0`、`pwd_changed=0`（**待激活**）。

**激活口径（`status=0` 不激活）**：审批通过后员工 `status=0`（**不可登录**）；须由 ADMIN 在末步 `DONE` **二次显式确认**（`completeOnboardingStep(DONE)`）方转 `status=1`。**前置不变式**：前 5 步已 `DONE`、`DONE` 为唯一 `PENDING`（`current_step_key=='DONE'`），否则 `completeOnboardingStep(DONE)` 被按序守卫拒（9303）。

错误码：400 / 404 / 3001 / 4001 / 4004 / 1003 / 9303 / 9305 / 9309。

#### 4.11.5 R-7 ~ R-9 既有端点扩展

- **R-7** 审批列表：`GET /api/v1/hr/onboarding`（ADMIN），行为不变（见 §4.10.1）。
- **R-8** 审批详情：`GET /api/v1/hr/onboarding/{id}`（ADMIN），出参**新增** `registration` 子对象（`applyNo / intentPosition / agreementVersion / source / status / createTime`）+ 展示 `employee.position`（见 §4.10.3）。
- **R-9** 驳回：`POST /api/v1/hr/onboarding/{id}/reject`（ADMIN），行为**扩展**：同事务回写 `registration.status=REJECTED` + `reject_reason` + 清空凭据列（见 §4.10.5）。

### 4.12 财务 / 工资单接口

> 前缀 `/api/v1/finance`；错误码 94xx（§2.2）。状态机、表设计与口径真源为 [payroll-automation-design.md](payroll-automation-design.md) **v1.5** §2 / §3 / §4。
> 本段共 **25** 端点：工资单 14（既有 10 + I-6 / I-7 / I-8 / **I-10**）、计薪规则 5、算薪配置 4（I-1 / I-2 / I-3 / I-9）、自动算薪 2（I-4 / I-5）。
> **标记约定**：`C-1~C-7` = 对既有端点的契约变更（相对当前实现，逐条就地标注，汇总见 §4.12.19）；`I-1~I-10` = 方案新增端点（`payroll-automation-design.md` v1.5 §4.1；I-10 为 v1.4 对账补录，见 §4.12.22）。
> **数据安全口径（全段适用）**：写操作一律鉴权 + 越权校验 + 入参校验；金额变更强制事由（`9412`）；`payroll_log` 的 `before` / `after` 白名单为「明细级 `{itemKey,itemType,itemName,amount}` + 合计级 `{additionTotal,deductionTotal,grossAmount,netAmount}`」，**禁止**写入 `rule_snapshot`、凭据与个人证件信息；`fail_reason` 由服务端构造、≤500 字符截断、不落 SQL 原文与业务数据。

#### 4.12.1 工资单状态机（8 态）与动作矩阵

**状态集合**（**权威枚举顺序 = 末尾追加**，以 `PayrollStatus.values()` 为准；下表按生命周期概念分组展示，非枚举序）：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED / OBJECTED / PAID`。

| 状态 | label | 含义 | 终态 | 明细可编辑 `isItemEditable` | 可被生成覆盖 `isOverwritable` | 可删除 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| `DRAFT` | 草稿 | 生成 / 覆盖重建期 | 否 | 是 | 是 | 是（物理重建范围） |
| `PENDING_APPROVAL` | 待审核 | 已提交，审批中 | 否 | **是（Q6 新增）** | 否 | 否 |
| `APPROVED` | 已通过 | 审批通过，待发布 | 否 | 否 | 否 | 否 |
| `REJECTED` | 已驳回 | 管理员审批驳回 | 否 | 是 | 是 | 是（物理重建范围） |
| `PUBLISHED` | 已发布 | 已发给员工，待本人确认 | 否 | 否 | 否 | **否（Q9 铁律）** |
| `CONFIRMED` | 已确认 | 员工已确认 | 否 | 否 | 否 | 否 |
| `OBJECTED` | **异议退回（新增）** | 员工提异议，退回管理员处理 | 否 | **是** | 否 | **否** |
| `PAID` | **已发放（新增）** | 管理员确认已发放，**归档冻结** | **是** | 否 | 否 | **否** |

> `OBJECTED` / `PAID` 为**追加在枚举末尾**：既有 6 键的相对顺序与名称不变，`counts` 仅在尾部多 2 个键。`OBJECTED` 为内部处理态，**不加入员工可见集**；`PAID` 加入（§4.12.2）。`generate` 覆盖重建仅限 `DRAFT` / `REJECTED`（`isOverwritable`）。

**动作矩阵**（动作级：`submit` / `approve` / `reject` / `publish` / `confirm` / `objection` / `pay`；明细级：`item-add` / `item-update`）：

| 当前状态 | 允许动作 | 前置条件 | 副作用（含留痕） |
| ---- | ---- | ---- | ---- |
| `DRAFT` | `submit`、`item-add`、`item-update`、（可被 `generate` 覆盖） | 加扣款需 `reason` 非空；被覆盖时既有 `source=MANUAL` 明细**保留迁移** | `submit` → `PENDING_APPROVAL`，清 `approve_remark`；每次改动写 `payroll_log`（action / operator / before / after / reason） |
| `PENDING_APPROVAL` | `approve`、`reject`、**`item-add`、`item-update`（Q6 新增）** | 加扣款需 `reason` 非空 | `approve` → `APPROVED`（记 `approver_*` / `approve_time`）；`reject` → `REJECTED`（记 `approve_remark`）；改金额后**重算合计**并写 log |
| `APPROVED` | `publish` | — | → `PUBLISHED`，记 `publisher_*` / `publish_time`；写 `payroll_log(PUBLISH)` |
| `REJECTED` | `submit`、`item-add`、`item-update`、（可被 `generate` 覆盖） | 同 `DRAFT`；被覆盖时 MANUAL 明细保留迁移 | 同 `DRAFT` 的 `submit`；改动写 `payroll_log` |
| `PUBLISHED` | `confirm`（本人）、`objection`（本人） | 均须本人且已发布 | `confirm` → `CONFIRMED`（记 `confirm_time`）；`objection`（`reason` 必填 2-200）→ **`OBJECTED`**（记 `objection_reason` / `objection_time`，清 `confirm_time` / `publish_time` / `publisher_*`） |
| `OBJECTED` | `item-add`、`item-update`、`publish`（**再发布**）、`submit`（可选二次审批） | 再发布前须 `reason` 非空（处理说明） | `publish` → `PUBLISHED`（记**新的** `publisher_*` / `publish_time`，清 `objection_reason` / `objection_time`）；异议历史永久留 `payroll_log`（action=`REPUBLISH`） |
| `CONFIRMED` | `pay`（**I-8**） | 仅 ADMIN；前置 `status=CONFIRMED`（来源非法回 `9403`） | → `PAID`（记 `paid_by_id` / `paid_by_name` / `paid_time`）；写 `payroll_log(PAY)`；进入 `PAID` 后冻结 |
| `PAID` | **无（终态冻结）** | — | 任何改动 / 删除一律 `9413`（统一由 `assertMutable` 收口） |

> **再发布是否需二次审批**：退回粒度为**单员工单据级**；`OBJECTED → PUBLISHED` **直发（再发布）为默认路径**，同时**保留 `submit` 作为可选路径**（供需二次审批的组织口径）。
> **自动路径状态落点**：自动算薪 `generate` 成功后**自动 `submit`**、落 `PENDING_APPROVAL`（**非** `DRAFT`），随后发通知类型 7；与 Q6「生成后先推管理员审核」一致。

**「可编辑」拆分为三判据**（各自独立真源，同时解 Q6「审核时可改」与 Q9「异议退回后可改」）：

| 判据 | 语义 | 状态集合 |
| ---- | ---- | ---- |
| `isItemEditable(status)` | 能否改 / 加明细金额 | `DRAFT`、`REJECTED`、**`PENDING_APPROVAL`**、**`OBJECTED`** |
| `isOverwritable(status)` | 能否被 `generate` 物理覆盖重建 | `DRAFT`、`REJECTED`（不变） |
| 账期锁 `isMonthLocked` | 该账期是否已出账 | `!isOverwritable(status)`（改绑 `isOverwritable`，行为零突变） |

**终态写守卫 `assertMutable(payroll, action)`**：所有工资单写入口（改明细、加扣款、`submit`、`approve`、`reject`、`publish`（含再发布）、`objection`、`pay`、`generate` 覆盖重建）在进入业务分支前**统一调用**；`status == PAID` 一律 `9413`（不区分动作）；`publish(ids)` 对非允许来源**返回显式错误码**，不得静默计入 `skipped`。

#### 4.12.2 我的工资单

`GET /api/v1/finance/payrolls/my`（ADMIN / STATION_ADMIN / STAFF）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `month?`（yyyy-MM）、`status?`（**取值 `PUBLISHED` / `CONFIRMED` / `PAID`**）、`pageNum`、`pageSize` |
| 出参 | `{ total, pageNum, pageSize, list: PayrollVO[], employeeId }`（`employeeId` = 当前登录员工 id，服务端按登录身份过滤，**不接受前端传参**） |
| 错误码 | `400`（`month` 格式非法 / `status` 取值非法） |
| 业务规则 | 员工**可见集 = `{PUBLISHED, CONFIRMED, PAID}`**；服务端强制按登录身份 `employee_id` 过滤，非本人单不出现 |
| 示例 | `GET .../payrolls/my?month=2026-09&status=PUBLISHED&pageNum=1&pageSize=10` |

> **契约变更（C-6）**：员工可见集由 `{PUBLISHED, CONFIRMED}` **加入 `PAID`**（归档态对员工可见，否则确认后单据从列表消失）；`OBJECTED` **不加入**（内部处理态）。`status` 入参合法集随之扩为三值。

#### 4.12.3 工资单列表

`GET /api/v1/finance/payrolls`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `month?`、`stationId?`、`employeeId?`、`status?`（8 态之一）、`billType?`（`MONTHLY` / `SETTLEMENT`）、`keyword?`（匹配员工姓名或工资单号）、`pageNum`、`pageSize` |
| 出参 | `{ total, pageNum, pageSize, list: PayrollVO[], counts: Map<状态,Integer>, month }` |
| 错误码 | `400`（`month` / `status` / `billType` 取值非法） |
| 业务规则 | `counts` **不含 `status` 筛选**（否则切标签页后其余计数归零），键为**全部 8 态**、键序与枚举一致 |
| 示例 | `GET .../payrolls?month=2026-09&status=OBJECTED&pageNum=1&pageSize=10` |

> **契约变更（C-6）**：`counts` 键新增 `OBJECTED` / `PAID`（尾部追加，既有 6 键相对顺序不变）；`status` 入参合法集扩为 8 态；`list[].statusLabel` 随 8 态扩展。

#### 4.12.4 工资单详情

`GET /api/v1/finance/payrolls/{id}`（ADMIN / STATION_ADMIN / STAFF）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `id`（路径） |
| 出参 | `PayrollVO`（见下） |
| 错误码 | `404`（`id` 无效 → 语义同 `9402`）、`9404`（非本人）、`9403`（非 ADMIN 且状态不在可见集，`message` = 工资单尚未发布，暂不可查看） |
| 越权口径 | 非 ADMIN：**必须复用 `detail()` 单一真源** = `employeeId == 当前登录 userId` ∧ `status ∈ {PUBLISHED, CONFIRMED, PAID}`；违规 `9404` / `9403`。**不得**仅校验角色 |
| 示例 | `GET .../payrolls/1024` |

`PayrollVO` 字段：`id / payrollNo / employeeId / employeeName / stationId / stationName / month / billType / billTypeLabel / ruleId / ruleName / items[] / additionTotal / deductionTotal / grossAmount / netAmount / status / statusLabel / remark / approveRemark / approverId / approverName / approveTime / publisherId / publisherName / publishTime / confirmTime / objectionReason / objectionTime / paidById / paidByName / paidTime / offboardingId / actions[] / createTime / updateTime`。
其中 `items[]` = `PayrollItemVO`（`key / name / type / typeLabel / source / sourceLabel / amount / detail`）；`actions[]` = 服务端下发的**当前状态允许动作**（动作级子集，前端不自行维护状态机）；`rule_snapshot` 为内部留存字段，**不在此出参**。

> **契约变更（C-6）**：`statusLabel` 新增「异议退回」「已发放」两态中文；新增出参 `paidById` / `paidByName` / `paidTime`；`actions` 随 8 态扩展（`CONFIRMED` 增 `pay`；`OBJECTED` 增 `publish`）；非 ADMIN 可见状态集加入 `PAID`。

#### 4.12.5 按月批量生成（含覆盖重建）

`POST /api/v1/finance/payrolls/generate`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ month（必填）, stationId?, deptId?, employeeIds?, ruleId? }`（员工筛选三选一 / 组合，均未传 = 全部在职员工；`ruleId` 未传取「第一个启用规则」） |
| 出参 | `{ month, ruleId, ruleName, created, payrollIds[] }`（`created` = 生成（含覆盖重建）单据数） |
| 错误码 | `400`（`month` 格式非法）、`9401`（规则不存在）、`9405`（**按驿站收敛**：同驿站该账期存在非可覆盖态单）、`9410`（claim 占位被占，见下） |
| 业务规则 | ① **9405 判定按驿站收敛**（新增 `stationId` 维度可覆盖性判定）；② 覆盖重建（`deleteExisting` → `createPayroll`）须**保留既有 `source=MANUAL` 明细**并按「规则项 + 保留项」**重算四项合计**；③ **须占用与调度相同的 `(station_id, target_month)` claim**，占位失败 → `9410`；④ 写 `payroll_log`（`GENERATE_AUTO` / `GENERATE_MANUAL`），`after` 含 `manualKept` |
| 示例 | `POST .../payrolls/generate` body `{"month":"2026-09","stationId":8,"ruleId":1}` |

> **契约变更（C-7）**：① **9405 由「账期全局级」收敛为「按驿站」**（否则多驿站自动算薪只能成功第一个站点）；② 覆盖重建**保留 `source=MANUAL` 明细**并重算合计（撤销「手工项须在 `DRAFT` / `REJECTED` 之外录入」约束）；③ 手工 `generate` **占用与调度相同的 claim**（Layer 0 硬防线覆盖全部生成路径）。

#### 4.12.6 批量提交审核

`POST /api/v1/finance/payrolls/submit`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ ids: Long[] }`（须为非空数组） |
| 出参 | `{ submitted, payrollIds[] }` |
| 错误码 | `400`（`ids` 为空）、`9403`（状态不允许 `submit`）、`9413`（`PAID` 冻结） |
| 业务规则 | 来源须为 `DRAFT` / `REJECTED`（`OBJECTED` 可选二次审批走同一端点）；写 `payroll_log(SUBMIT)` |

> **契约变更（C-5）**：无状态变更；**补写 `payroll_log(SUBMIT)`**（全链路留痕）。

#### 4.12.7 审核（通过 / 驳回）

`POST /api/v1/finance/payrolls/{id}/approve`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ approved: boolean, approveRemark? ≤200 字 }`（`approved=false` 时 `approveRemark` 记入驳回意见） |
| 出参 | `PayrollVO` |
| 错误码 | `400`（`approved` 非布尔 / 意见超 200 字）、`9403`（状态不允许）、`9413`（`PAID` 冻结） |
| 业务规则 | 来源须为 `PENDING_APPROVAL`；`true → APPROVED`（记 `approver_*` / `approve_time`）、`false → REJECTED`（记 `approve_remark`）；写 `payroll_log(APPROVE / REJECT)` |

> **契约变更（C-5）**：无状态变更；**补写 `payroll_log(APPROVE/REJECT)`**。

#### 4.12.8 批量发布 / 再发布

`POST /api/v1/finance/payrolls/publish`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ ids?: Long[], month?: string, stationId?: Long }`（`ids` 与「`month + stationId`」**二选一**；`ids` 非空时优先） |
| 出参 | `{ published, skipped, payrollIds[] }` |
| 错误码 | `400`（两者均未传 / `month` 格式非法）、`9403`（**来源非法且显式报错**，见业务规则）、`9413`（`PAID` 冻结） |
| 业务规则 | ① **允许来源扩展为 `APPROVED`（首发）或 `OBJECTED`（再发布）**；② **不限 `ids` 的 `month` 批量路径维持仅 `APPROVED`**（避免误批再发布），非 `APPROVED` 计入 `skipped`；③ **`ids` 路径**遇非允许来源（含 `PAID` / `CONFIRMED`）**显式返回错误码、不得静默计入 `skipped`**；④ 再发布成功记**新的** `publisher_*` / `publish_time`、清 `objection_*`，写 `payroll_log(REPUBLISH)`；⑤ 状态落 `PUBLISHED` 后触发通知类型 8（员工本人） |
| 示例 | `POST .../payrolls/publish` body `{"ids":[1024,1025]}` |

> **契约变更（C-2）**：发布来源由 `APPROVED` 扩展为 **`APPROVED` 或 `OBJECTED`**；`ids` 路径非法来源改为**显式错误码**（不再静默 `skipped`）；`month` 批量路径口径不变（仅 `APPROVED`）。

#### 4.12.9 员工确认

`POST /api/v1/finance/payrolls/{id}/confirm`（ADMIN / STATION_ADMIN / STAFF）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `id`（路径） |
| 出参 | `PayrollVO` |
| 错误码 | `9404`（非本人）、`9403`（已确认，无需重复确认 / 尚未发布，暂不可确认）、`9413`（`PAID` 冻结） |
| 业务规则 | 仅本人 + 已发布（`PUBLISHED → CONFIRMED`），记 `confirm_time`；写 `payroll_log(CONFIRM)` |

> **契约变更（C-4）**：无行为变更；**补写 `payroll_log(CONFIRM)`**。

#### 4.12.10 员工异议

`POST /api/v1/finance/payrolls/{id}/objection`（ADMIN / STATION_ADMIN / STAFF）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ reason: string }`（必填 2-200 字） |
| 出参 | `PayrollVO` |
| 错误码 | `400`（原因长度非 2-200）、`9404`（非本人）、`9403`（仅已发布可提异议）、`9413`（`PAID` 冻结） |
| 业务规则 | 仅本人 + 已发布；**目标状态 → `OBJECTED`**；记 `objection_reason` / `objection_time`，清 `confirm_time` / `publish_time` / `publisher_*`；写 `payroll_log(OBJECTION)`；随后发通知类型 9（管理员待处理） |
| 示例 | `POST .../payrolls/1024/objection` body `{"reason":"9 月缺勤天数与实际不符"}` |

> **契约变更（C-1）**：目标状态由 `PENDING_APPROVAL` **改为 `OBJECTED`**（原落 `PENDING_APPROVAL` 与该态 `isEditable=false` 相矛盾，致「退回后可改再发布」不可达）。清空口径沿用（清 `confirm_time` / `publish_time` / `publisher_*`）。

#### 4.12.11 修改人工项金额

`PUT /api/v1/finance/payrolls/{id}/items`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ items: [{ key, amount }] }`（非空数组；`amount` 非数字 → `400` 文案「金额须为数字」） |
| 出参 | `PayrollVO`（含重算后的四项合计） |
| 错误码 | `400`（`items` 为空 / 项不存在 / 非 `MANUAL` 项 / 金额非数字）、`9402`（工资单不存在）、`9403`（**非 `isItemEditable` 状态**）、`9412`（金额变更事由必填 2-200）、`9413`（`PAID` 冻结） |
| 业务规则 | ① 可编辑判据由 `isEditable` 改为 **`isItemEditable`**（新增 `PENDING_APPROVAL` / `OBJECTED`）；② 仍**仅允许改 `source=MANUAL` 项**；③ **`reason` 必填 2-200**；④ 每次改动写 `payroll_log(ITEM_UPDATE)`，`before` / `after` 限 §4.12 白名单；⑤ 不得覆盖 `payroll_item.detail` 中的事由全文 |

> **契约变更（C-3）**：① 可编辑判据改 `isItemEditable`；② **`reason` 由「可选」改为「必填 2-200」**（原可选与 Q3「每笔必填事由」冲突），违规回 `9412`；③ **修正既有实现**：现实现以 `400` 文案「工资单项不存在」「由规则计算，不可手工修改」「金额须为数字」，且会把 `detail` 覆盖为「人工填写」——本契约要求 `detail` 事由全文不被覆盖。

#### 4.12.12 手工加 / 扣款（I-6）

`POST /api/v1/finance/payrolls/{id}/items/add`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `{ itemType: "ADDITION" / "DEDUCTION", itemName, amount（>0）, reason（必填 2-200）, detail? }` |
| 出参 | `PayrollVO`（含重算合计） |
| 错误码 | `9402`（工资单不存在）、`9403`（状态不允许，须 `isItemEditable`）、`9411`（`item_key` 重复）、`9412`（事由必填 2-200）、`9413`（`PAID` 冻结） |
| 业务规则 | ① 一次性语义：仅在目标工资单下新增一行 `payroll_item`（`source=MANUAL`，不建员工级长期项表）；② `item_key` 由**服务端生成**、**强制 `MANUAL_` 前缀**（如 `MANUAL_<账期>_<序号>`）；③ **加款计入应发、扣款计入扣项，实发 = 应发 − 扣项**（沿用 `PayrollTotalsPolicy`，不改公式），保存后**自动重算四项合计**；④ 写 `payroll_log(ITEM_ADD, reason 必填, before/after 合计, operator_*)` |
| 示例 | `POST .../payrolls/1024/items/add` body `{"itemType":"DEDUCTION","itemName":"设备赔偿","amount":120.00,"reason":"9 月扫码枪损坏赔偿","detail":"事由：9 月扫码枪损坏赔偿"}` |

> 允许状态 = `isItemEditable`（`DRAFT` / `REJECTED` / `PENDING_APPROVAL` / `OBJECTED`）；`DRAFT` / `REJECTED` 期的 MANUAL 项在 `generate` 覆盖重建时**保留迁移**，故不禁止在 `DRAFT` 录入。金额方向为主代理解释、**待用户最终确认**（存在性登记见 §4.12.19）。

#### 4.12.13 确认发放归档（I-8）

`POST /api/v1/finance/payrolls/{id}/pay`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `id`（路径）；`{ remark? }`（可选） |
| 出参 | `PayrollVO`（`status=PAID`，含 `paidTime` / `paidByName`） |
| 错误码 | `9402`（工资单不存在）、`9403`（**来源非 `CONFIRMED`**）、`9413`（已发放归档） |
| 业务规则 | 仅 ADMIN；来源须 `CONFIRMED`（且 `confirm_time` 非空）；→ `PAID`（记 `paid_by_id` / `paid_by_name` / `paid_time`）；写 `payroll_log(PAY)`；进入 `PAID` 后受 `assertMutable` 全面冻结。**必须显式报错、不得静默 skip**（与 `publish` 的批量 `skipped` 语义不同） |
| 示例 | `POST .../payrolls/1024/pay` |

> Q9「管理员确认工资已发放 → 归档」的落地端点。`pay` **不新增错误码**：来源非法复用 `9403`；`PAID` 冻结用 `9413`。`PAID` 绝对冻结（U-03），本期**无冲正 / 反归档出口**（受限项登记见 §4.12.19）。

#### 4.12.14 工资单操作留痕（I-7）

`GET /api/v1/finance/payrolls/{id}/logs`（ADMIN / STATION_ADMIN / STAFF）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `id`（路径） |
| 出参 | 留痕时间线数组，按 `time` 倒序。**服务端按角色裁剪**：ADMIN 返回 `action / operatorName / operatorRole / time / fromStatus / toStatus / reason / before / after`；**非 ADMIN 仅返回 `action / time / reason / toStatus`**（**不返回** `before` / `after` / `operator_id` / `operator_role`） |
| 错误码 | `9402`（工资单不存在）、`9404`（越权）、`9403`（不可见状态） |
| 业务规则 | 越权判定**必须复用 `detail()` 单一真源**（`employeeId == userId` ∧ 状态 ∈ `{PUBLISHED, CONFIRMED, PAID}`）；**不得**仅校验角色而未逐单校验「本人 + 可见状态」（否则任一 STAFF 可按 id 遍历任意单据留痕 → IDOR）；**字段裁剪由服务端强制**（前端隐藏而后端照返 = 越权信息泄漏） |
| 示例 | `GET .../payrolls/1024/logs` |

> 数据源 `payroll_log`（追加型审计表，只增不改、无删除入口）。`action` 取值：`GENERATE_AUTO / GENERATE_MANUAL / ITEM_ADD / ITEM_UPDATE / SUBMIT / APPROVE / REJECT / PUBLISH / REPUBLISH / CONFIRM / OBJECTION / PAY / NOTIFY / NOTIFY_SKIP / AUTO_SUBMIT_SKIPPED`（末项为 v1.4/B4a 修补新增：自动算薪逐单自动提交失败时的留痕，`operator_type=SYSTEM`；需数据库工程师同步 `db.md` §8.6.6 取值清单，列型 `VARCHAR(32)` 无需 DDL）。

#### 4.12.15 计薪规则（5 端点）

| # | 方法 | 路径 | 权限 | 入参 | 出参 | 错误码 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 1 | GET | `/api/v1/finance/payroll-rules` | ADMIN | — | `{ list: PayrollRuleVO[] }`（规则数少，不分页，含规则项） | — |
| 2 | POST | `/api/v1/finance/payroll-rules` | ADMIN | `PayrollRuleRequest` | `PayrollRuleVO` | `400`（规则名校验 / 规则项校验） |
| 3 | GET | `/api/v1/finance/payroll-rules/{id}` | ADMIN | `id` 路径 | `PayrollRuleVO` | `9401`（规则不存在） |
| 4 | PUT | `/api/v1/finance/payroll-rules/{id}` | ADMIN | `PayrollRuleRequest`（`items` 传入即**整体覆盖**） | `PayrollRuleVO` | `9401`、`400`（校验失败） |
| 5 | DELETE | `/api/v1/finance/payroll-rules/{id}` | ADMIN | `id` 路径 | `null` | `9401`、`9403`（**规则已被工资单引用，只能改为停用**） |

`PayrollRuleRequest`：`ruleName（2-50 字；新建必填） / remark（≤200 字） / status（0=停用 1=启用） / items: PayrollRuleItemRequest[]`。
`PayrollRuleItemRequest`：`key（1-30，须匹配 ^[A-Z][A-Z0-9_]*$） / name（1-20 字） / type（ADDITION / DEDUCTION） / source（FIXED / ATTENDANCE / KPI / MANUAL） / params（结构随 source） / enabled（0/1，缺省 1） / sortOrder`。
`PayrollRuleVO`：`id / ruleName / remark / status / statusLabel / itemCount / enabledItemCount / items[]（PayrollRuleItemVO：id / key / name / type / typeLabel / source / sourceLabel / params / enabled / sortOrder） / createTime / updateTime`。

> 删除保护的 `9403` 与「工资单状态不允许该操作」**同码两语义**（沿用既有实现）；本批**不改码**，登记见 §4.12.19。

#### 4.12.16 驿站算薪配置（I-1 / I-2 / I-3）

| # | 方法 | 路径 | 权限 | 入参 | 出参 | 错误码 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| I-1 | GET | `/api/v1/finance/payroll-settings` | ADMIN | `stationId?`、`enabled?`（过滤，可空） | 驿站列表 + 各站算薪配置（`stationId / stationName / enabled / payrollDay / payrollTime / notifyEnabled / remark / updateTime`） | — |
| I-2 | GET | `/api/v1/finance/payroll-settings/{stationId}` | ADMIN | `stationId` 路径 | 单驿站算薪配置（同 I-1 字段） | `4001`（驿站不存在）、`9406`（该驿站尚未配置算薪设置） |
| I-3 | PUT | `/api/v1/finance/payroll-settings/{stationId}` | ADMIN | `{ enabled, payrollDay, payrollTime（HH:mm）, notifyEnabled, remark }` | 保存后的配置 | `4001`、`9407`（算薪日非法）、`9408`（时间格式非法） |

- `payrollDay` 范围 **1..31**（用户 U-05 裁定）；当月无该日时 `dueAt` **钳位到当月最后一天**（例 `31→4/30`、`30→2026/2/28`、`29→2024/2/29`），保证每月恒定有且仅有一个 `dueAt`。
- `enabled` 默认 **0**（默认不自动跑数）；`notifyEnabled` 默认 **1**（生成即推管理员）。
- **I-3 副作用（M-9 硬要求）**：每次保存成功即**在同一事务内追加一条** `station_payroll_setting_log`：首次创建 → `CREATE`；`enabled` `0→1` → `ENABLE`；`1→0` → `DISABLE`；其余字段变更 → `UPDATE`。**「启用 0→1」由 `action=ENABLE` 行承载、可追溯**。`before` / `after` 仅写白名单键 `{enabled,payrollDay,payrollTime,notifyEnabled,remark}`。
- **I-3 示例**：`PUT .../payroll-settings/8` body `{"enabled":1,"payrollDay":31,"payrollTime":"09:00","notifyEnabled":1,"remark":"月末结算"}`。

> **I-2 的 `9406`** 为「驿站存在但尚无配置」的可判定分支；若实现选择返回默认值而非报错，须与前端一致（登记见 §4.12.19）。新接口全 `{"ADMIN"}`，与 boss-h5 端准入（fail-closed 仅 `ADMIN`）一致。

#### 4.12.17 算薪配置变更历史（I-9）

`GET /api/v1/finance/payroll-settings/{stationId}/logs`（ADMIN）

| 项 | 内容 |
| ---- | ---- |
| 入参 | `stationId` 路径；可选时间范围、`pageNum` / `pageSize` |
| 出参 | 该驿站算薪配置**变更历史时间线**（`action / operatorName / operatorRole / time / before / after / remark`），按 `time` 倒序 |
| 错误码 | `4001`（驿站不存在） |
| 业务规则 | 数据源 `station_payroll_setting_log`（追加型审计表，只增不改、无删除入口）；`action` ∈ `CREATE / UPDATE / ENABLE / DISABLE`。**不在 I-1 / I-2 出参内嵌历史**（列表接口不背负时间线，与 I-7 单据留痕同构） |
| 示例 | `GET .../payroll-settings/8/logs?pageNum=1&pageSize=20` |

> 承载 M-9「配置变更留痕、启用 0→1 可追溯」的查询出口。

#### 4.12.18 自动算薪运行（I-4 / I-5）

| # | 方法 | 路径 | 权限 | 入参 | 出参 | 错误码 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| I-4 | POST | `/api/v1/finance/payroll-runs/trigger` | ADMIN | `{ stationId, month（yyyy-MM） }`（**无 `force` 参数**） | 本次运行结果（`runId / status / generatedCount / submittedCount / skippedCount`；或 `SKIPPED` + 原因） | `4001`、`9410`（正在运行 / 已占位 / **当日已尝试**）；命中 `9405` 时**映射为 `SKIPPED` 结果**而非错误码 |
| I-5 | GET | `/api/v1/finance/payroll-runs` | ADMIN | `stationId`、`month`、`status`、`triggerType`、`pageNum` / `pageSize` | 运行记录分页（`id / stationId / targetMonth / attemptDate / triggerType / dueAt / status / skipCode / skipReason / generatedCount / failReason / operatorName / startTime / finishTime`；本列表**不含** `submittedCount / skippedCount`，二者为触发响应运行内存态、无持久列） | — |

- **生成后自动提交待审（v1.4/B4a 修补，Q6）**：自动算薪（含 I-4 手工触发运行）在 `generate` 成功后，**对本次生成单据逐单调用既有 `POST /payrolls/submit` 同源逻辑**，落 `PENDING_APPROVAL`；随后（提交事务已提交）才投递类型 7。顺序固定为「逐单 submit 成功 → 事务提交 → 发通知」。
  - **逐单隔离**：每单各自独立事务提交（`REQUIRES_NEW`），某单失败**只影响本单**、**不回滚**本次已成功的生成与其它已提交单。
  - **失败口径**：失败的单一律保持 `DRAFT`，追加 `payroll_log(action=AUTO_SUBMIT_SKIPPED, operator_type=SYSTEM)` 留痕，运行结果 `skippedCount` 计入，由管理员后续手工 `submit`。`submittedCount + skippedCount = generatedCount`。
  - **手工 `POST /payrolls/generate`（I-3）不受影响**：仍只落 `DRAFT`，不自动提交（管理员自行核对后再提交）。
  - `submittedCount / skippedCount` 仅随 I-4 触发响应返回；`payroll_run` **无对应持久列**，I-5 列表不返回（如需持久化须走结构变更）。

- **可选错误码（§2.2 已定义、当前端点按下述口径使用）**：`9415`（该驿站未启用自动算薪）用于 I-4 对 `enabled=0` 驿站触发时的可判定分支；`9409`（运行记录不存在）**号段保留**，现有 I-4 / I-5 端点不返回。二者若实现侧不收口，亦可用 `400` + 具体文案替代（§2.2 说明）。
- **I-4 触发约束（v1.5，复-2 / 复-3）**：① 仍受**占位**约束（已 `SUCCESS` / `SKIPPED` / `RUNNING` → `9410`）；② 对**无占位**账期（前次 `FAILED` 或从未执行）受**日粒度闸门**约束 —— `now ≥ 当日 catch-up-time-of-day` **且** `(stationId, month)` **当日尚未尝试**方可执行；③ **同日重复触发一律 `9410`**（由 DB 唯一键 `uk_attempt` 硬拒绝）；④ **不提供 `force` 参数**，故无「同日再试 / 强制重跑已成功账期」能力；⑤ `targetMonth` 恒为 `dueAt` 所在月（补跑仅**执行日推后**，账期归属不因跨月改变）。
- **`triggerType`**：`AUTO`（定时到点）/ `CATCH_UP`（补跑）/ `MANUAL`（手工触发）。判定基准 `(now − dueAt) ≤ tickInterval ? AUTO : CATCH_UP`（仅可观测性标签，不影响执行语义）。
- **`status`**：`RUNNING` / `SUCCESS` / `FAILED` / `SKIPPED`。**`skipCode`**：`BLOCKED_9405` / `CONFIG_INVALID` / `DRAFT_PROTECTED`（**已移除 `EXHAUSTED`**，无重试硬上限）。
- **僵死 `RUNNING` 回收**：`start_time < now − running-timeout-minutes`（默认 30）判为僵死，回收动作（同一 `UPDATE`，四条同时执行）：`claim_key=NULL`（释放占位）+ `status=FAILED` + `fail_reason=STALE_RECLAIMED` + `finish_time=now`，**并触发失败告警**；回收后允许**次日**按日粒度闸门重试。受 `stale-reclaim-enabled` 控制，**单实例默认 `true`（开启）**。
- **失败重试**：以自然日为粒度持续重试至成功，**无硬性天数上限**；连续失败达 `alert-after-consecutive-fail-days`（默认 3）推送管理员告警（**只提醒、不停止重试**）。

> **已作废能力（登记，不沉默）**：`9414`（超窗口）/ `9416`（重试耗尽）随 v1.3 **作废、号段不复用**；「释放占位 / 强制重跑已成功账期」「配置修正后重算已 `SKIPPED(CONFIG_INVALID)` 的账期」本批**无出口**（须走新账期或 C 档人工处置）。

#### 4.12.19 契约变更点汇总（C-1~C-7）与登记项

| # | 端点 | 变更点 | 落点 |
| ---- | ---- | ---- | ---- |
| C-1 | `POST /payrolls/{id}/objection` | 目标状态 `PENDING_APPROVAL` → **`OBJECTED`**；清 `confirm_time` / `publish_time` / `publisher_*` 口径沿用 | §4.12.10 |
| C-2 | `POST /payrolls/publish` | 发布来源扩展为 **`APPROVED` 或 `OBJECTED`**；不限 `ids` 的 `month` 批量路径维持仅 `APPROVED`；`ids` 路径非法来源**显式报错**（不静默 `skipped`） | §4.12.8 |
| C-3 | `PUT /payrolls/{id}/items` | 可编辑判据 `isEditable` → **`isItemEditable`**（新增 `PENDING_APPROVAL` / `OBJECTED`）；**`reason` 必填 2-200** | §4.12.11 |
| C-4 | `POST /payrolls/{id}/confirm` | 无行为变更；补写 `payroll_log(CONFIRM)` | §4.12.9 |
| C-5 | `POST /payrolls/{id}/approve`、`POST /payrolls/submit` | 无状态变更；补写 `payroll_log(APPROVE / REJECT / SUBMIT)` | §4.12.6 / §4.12.7 |
| C-6 | `GET /payrolls`、`GET /payrolls/{id}`、`GET /payrolls/my` | `statusLabel` 新增两态中文；`counts` 键新增 `OBJECTED` / `PAID`；详情补 `paidTime` / `paidByName`、`actions` 随 8 态扩展；`my` 员工可见集加入 `PAID` | §4.12.2~§4.12.4 |
| C-7 | `POST /payrolls/generate` | ① 9405 判定**按驿站收敛**；② 覆盖重建**保留 `source=MANUAL` 明细**并重算合计；③ 手工 `generate` **占用与调度相同的 claim**（占位失败 `9410`） | §4.12.5 |

**本批登记项（不改代码，仅登记；详见 B0 交付汇报）**：
1. `PayrollStateMachine` 现为 6 态、动作集不含 `pay`，`isEditable` 单一判据；`isItemEditable` / `isOverwritable` / `assertMutable` 尚未落地 —— 与 §4.12.1 目标契约存在差距（本批为契约先行）。
2. `PayrollStatus` 枚举、`labels()`、`counts` 键序目前为 6 态，需按末尾追加补 `OBJECTED` / `PAID`。
3. `EMPLOYEE_VISIBLE_STATUS` 现为 `{PUBLISHED, CONFIRMED}`，需加 `PAID`（C-6）。
4. `PayrollGenerateGuard` 现为「账期全局级」9405 判定，需按驿站收敛（C-7）。
5. `updateItems` 现以 `400` 文案承载「项不存在 / 非 MANUAL / 金额非数字」，且覆盖 `item.detail`；`9411` / `9412` 尚未使用（C-3 / I-6）。
6. `PayrollRuleServiceImpl` 删除保护复用 `9403`（「工资单状态不允许该操作」），与规则语义不同源 —— 同码两语义，本批不改码。
7. ~~`NotificationServiceImpl.PUBLISH_TYPES = {1..6}`，尚无 `SYSTEM_TYPES = {7,8,9}` 与 `findAdminEmployeeIds()`（§4.12.21 目标契约）。~~ **（v1.4/B4a 已闭环）**：白名单拆分（`PUBLISH_TYPES{1..6}` / `LEAVE_SYSTEM_TYPES{5,6}` / `SYSTEM_TYPES{7,8,9,10}`）、`findAdminEmployeeIds()`、类型 7/8/9 投递点与失败告警 10 均已落地。
8. `payroll-settings` / `payroll-runs` 两组端点（I-1~I-5、I-9）后端尚无 Controller —— 本批契约先行。
9. 主代理解释项「加扣款金额方向（加款计入应发、扣款计入扣项、实发 = 应发 − 扣项）」**待用户最终确认**。
10. 新增错误码 `9406`（I-2「尚未配置」）、`9415`（「未启用自动算薪」）、`9409`（号段保留）在方案 §4.1 的端点错误码列中未逐条钉死（方案 §4.3 已定义文案）；本契约按语义就近归位并在 §4.12.16 / §4.12.18 标注，**实现侧如选择以通用 `400` + 文案替代，须与本契约同步回改**。
11. 代码现状与方案 v1.5 的其余已登记差异（见 `payroll-automation-design.md` §0.2、§10、§11）：多实例 / 僵死回收的前提（单实例 `fixedDelay` 不重叠）、`payroll` 单据层无 DB 唯一约束等，均为**已知残余风险**，不在本 B0 契约批内处理。

#### 4.12.20 权限与端准入一致性

| 项 | 结论 |
| ---- | ---- |
| 角色口径 | 所有**新增**接口（I-1~I-6、I-8、I-9、**I-10**）均 `{"ADMIN"}`；**I-7 为 ADMIN + 本人**（服务端按角色裁剪 `before` / `after` / `operator_*`） |
| boss-h5 端准入 | Q5 要求设置在 boss-h5；boss-h5 fail-closed 仅 `ADMIN`，与 I-1~I-3 的 `ADMIN` 口径一致 |
| web（PC 管理端） | 仅 `ADMIN`，承载 I-3~I-8 的管理操作 |
| staff-h5 | 仅涉及 C-1 / C-4（员工异议 / 确认）与 I-7（本人留痕），均为既有「本人」口径 |
| 权限放宽 | **无**。本方案不引入任何跨角色越权；`@RequireRoles` 全部沿用既有角色常量。**无「待安全评估的权限放宽项」** |
| 间接安全面（非权限放宽） | ① 新增**进程内定时调度 + 自动写库**（生产变更面）；② 新增 `payroll_run` / `payroll_log` / `station_payroll_setting_log` 可含个人薪资信息的表（数据面）—— 均已登记为需网络安全工程师评估项（`payroll-automation-design.md` §8） |

> **`@RequireRoles` 取值须为编译期字面量**（既有约定）；角色常量见 `RoleEnum`。

#### 4.12.21 通知类型扩展（7 / 8 / 9）

| 类型值 | 用途 | 触发点 | 接收人 |
| ---- | ---- | ---- | ---- |
| 7 | 工资单待审核 | 自动算薪生成**并自动提交**（落 `PENDING_APPROVAL`）后（`notify_enabled=1`） | **管理员**（单一真源 `findAdminEmployeeIds()`） |
| 8 | 工资单已发布 | `publish` / 再发布成功后（**状态已落 `PUBLISHED` 才触发**） | **员工本人**（Q8：发布后才推员工） |
| 9 | 工资单异议退回 | `objection` 后 | **管理员**（待处理） |
| 10 | 自动算薪运行失败 / 僵死回收告警 | 单驿站执行 `FAILED`、僵死 `RUNNING` 被回收（`STALE_RECLAIMED`）后（受 `notify-on-fail` 控制） | **管理员** |

- `biz_type='payroll'`、`biz_id=payroll.id`（对齐 `sendSystem` 契约）。
- **公告白名单维持 1..6**：**不得**把 `7/8/9` 并入 `PUBLISH_TYPES`（现 `1..6`）；`sendSystem` 放行集 = `PUBLISH_TYPES{1..6}`（工单 1/2、请假 5/6 等既有系统联动沿用）**∪** 薪资段 `SYSTEM_TYPES{7,8,9}` 与失败告警 `10`；而 `POST /notifications/publish` 的公告发布路径白名单维持 `1..6` 不变 —— 否则 ADMIN 可经公告接口（`ALL/STATION/EMPLOYEE` 扇出、`title/content` 任意）**仿冒**薪资通知（应用内钓鱼）。属代码改动、**不改表结构**（`type` 为 TINYINT）。**已落地（v1.4/B4a）**：`NotificationServiceImpl` 拆出 `PUBLISH_TYPES{1..6}` 与 `SYSTEM_TYPES{7,8,9,10}`，`sendSystem` 取两者并集（1..10），`publish` 仅认 `PUBLISH_TYPES`。
- **接收人单一真源**：`findAdminEmployeeIds()`（`employee.role=ADMIN AND status=1`）作为「管理员集合」唯一真源；**禁止**复用 `resolveTargets("ALL"/"STATION")`（会误推站长 / 员工）。**已落地（v1.4/B4a）**。
- **type 8 触发前置**：仅当状态已落 `PUBLISHED` 后触发；**禁止**在 `generate` / `approve` / `submit` 分支触发。断言：未发布状态下 type 8 通知数为 0。**已落地（v1.4/B4a）**：投递前以 DB 落库状态复核。
- **type 7 门槛**：受该站 `station_payroll_setting.notify_enabled` 控制（`=0` 不投递）；接收人 = 全部在职 ADMIN。
- **type 10（v1.4 新增）**：单驿站执行 `FAILED` / 僵死 `RUNNING` 回收告警，受 `hrm.payroll.schedule.notify-on-fail` 控制；**`biz_id` 传 null**（无单张工资单可跳转）。**来源：算法 v1.3 §13 失败告警；方案 §4.5 原仅定义 7/8/9**（算法 `algorithm-payroll-scheduling.md` §9 TODO-4「失败/耗尽告警通知类型」原为开放项）；本批取号 `10`，如需改号须同步本契约与上游文档。
- **通知异常不阻断**：调用侧一律 `try / catch`，失败只写 `NOTIFY_SKIP` 留痕（应用日志，不落库）、不阻断主流程。**已落地（v1.4/B4a）**：`PayrollNotifySupport` 统一收口（类型 7/8/9/10）。
- **通知类型定稿**：7 = 工资单待审核（→管理员）、8 = 工资单已发布（→员工本人）、9 = 工资单异议退回（→管理员）、10 = 自动算薪运行失败告警（→管理员）；原 1~4 不变，请假 5 / 6 见 §7.3。

#### 4.12.22 手工调整对账汇总（I-10，v1.4 新增）

| # | 方法 | 路径 | 权限 | 入参 | 出参 | 错误码 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| I-10 | GET | `/api/v1/finance/payrolls/manual-adjustments/summary` | ADMIN | `month`（yyyy-MM，**必填**）、`stationId`（可选） | 按员工汇总 + 合计行（见下） | `400`（`month` 缺失 / 格式非法） |

- **目的**：安全补偿控制 —— 回答「**谁在何时把谁加/扣了多少、理由是什么**」。
- **汇总口径（真源）**：以 `payroll_log` 的**冗余定位列** `employee_id` + `month` 为准（`generate` 覆盖重建会物理删除 `DRAFT`/`REJECTED` 单、更换 `payroll_id`；按 `payroll_id` 关联会漏「已删单」上的加扣款留痕）。
- **计入动作（v1.4/B4a 修补）**：`ITEM_ADD`（手工加/扣款，I-6）**与** `ITEM_UPDATE`（修改既有 MANUAL 项金额，C-3）**均纳入**——后者是「谁把谁的工资改成多少」的审计关键，早期只统计 `ITEM_ADD` 会漏掉改金额。
- **分类与金额**：
  - `ITEM_ADD`：取 `after.items[*]`，`itemType=ADDITION` 计加款、`DEDUCTION` 计扣款，金额取 `amount`；贡献既有的 `additionCount/additionTotal/deductionCount/deductionTotal/netImpact`（**ITEM_ADD 口径，语义不变**）。
  - `ITEM_UPDATE`：按 `itemKey` 将 `after.items[*]` 与 `before.items[*]` 配对，**净影响 = 变动后 − 变动前**（`Δ = afterAmount − beforeAmount`），再折算为对实发的方向：`ADDITION` 项计 `+Δ`、`DEDUCTION` 项计 `−Δ`；正向计入 `updateIncreaseTotal`、负向计入 `updateDecreaseTotal`（取正数）。
  - `totalNetImpact = netImpact + updateIncreaseTotal − updateDecreaseTotal`（含新增加/扣款与改金额的总净影响）。
- **驿站过滤**：传 `stationId` 时按「员工归属驿站」收敛（`employee.station_id`）；该站无员工时返回空列表 + 零合计。

**出参**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `month` | string | 账期 |
| `stationId` | number / null | 回显 |
| `list[]` | array | 按员工汇总行（`employeeId` 升序） |
| `list[].employeeId` / `employeeName` | number / string | 员工 id / 姓名 |
| `list[].additionCount` / `additionTotal` | number / number | 新增加款笔数 / 总额（`ITEM_ADD`，`ADDITION`） |
| `list[].deductionCount` / `deductionTotal` | number / number | 新增扣款笔数 / 总额（`ITEM_ADD`，`DEDUCTION`） |
| `list[].netImpact` | number | 新增净影响 = `additionTotal − deductionTotal`（`ITEM_ADD` 口径，语义不变） |
| `list[].addCount` | number | 新增动作笔数（`ITEM_ADD` 留痕条数） |
| `list[].updateCount` | number | 改金额动作笔数（`ITEM_UPDATE` 留痕条数） |
| `list[].updateIncreaseTotal` | number | 改金额对实发的净增合计（≥0；加款增额、扣款减额） |
| `list[].updateDecreaseTotal` | number | 改金额对实发的净减合计（≥0，正数表示减少额；加款减额、扣款增额） |
| `list[].totalNetImpact` | number | 总净影响 = `netImpact + updateIncreaseTotal − updateDecreaseTotal` |
| `total` | object | 合计行（`employeeId=null`、`employeeName='合计'`，字段同 `list` 项） |

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


