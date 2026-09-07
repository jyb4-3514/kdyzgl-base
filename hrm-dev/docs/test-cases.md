# 快递驿站智汇系统 · 一期测试用例文档（E 阶段）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-07 |
| 关联任务 | TASK.md 阶段 E（E01-E06） |
| 关联文档 | [api.md](api.md)、[requirement.md](requirement.md)、[db.md](db.md) |
| 适用范围 | 一期员工管理模块（24 接口 + 前端 + 双库） |
| 执行前提 | D01-D05 部署通过；本机无 JDK/MySQL/Redis，实际执行需部署后进行 |

***

## 1. 测试概述

### 1.1 环境要求

| 项 | 要求 |
| ---- | ---- |
| 后端 baseURL | `http://127.0.0.1:8080/api/v1` |
| 数据库 | MySQL 8.0（默认）+ PostgreSQL（E05 专项） |
| 缓存 | Redis（登录态存放） |
| 前端 | `hrm-admin` 经 Nginx 反代 `/api` 到后端，生产同源 |
| 测试工具 | curl / ApifOX / Postman 任选；前端走查用 Chrome 或 Edge 最近两版 |
| 服务器时区 | Asia/Shanghai（JVM 与 MySQL serverTimezone 一致） |

### 1.2 公共变量定义

执行 curl 前先在 shell 中定义变量，所有用例复用：

```bash
# 公共变量（实际值由部署侧确定，仓库内只放占位）
export BASE_URL="http://127.0.0.1:8080/api/v1"
export ADMIN_USER="admin"
export ADMIN_INIT_PWD="<部署实际值，对应 change_me_admin_init_password>"
export TOKEN_ADMIN="<TOKEN>"           # 管理员登录后替换
export TOKEN_STAFF="<TOKEN>"           # 普通员工登录后替换
export TOKEN_OLD="<TOKEN>"             # 互踢测试中的旧 Token
export EMP_ID_STAFF="<员工ID>"          # STAFF 账号 ID
export EMP_ID_TO_DEL="<员工ID>"        # 待删除员工 ID
export DEPT_ID_ROOT="1"                # 种子根部门 id=1
export STATION_ID_OK="<驿站ID>"        # 启用中的驿站 ID
export STATION_ID_OFF="<驿站ID>"       # 已停用的驿站 ID
```

> Token 一律以 `<TOKEN>` 占位，禁止写真实密钥与初始密码入库入仓。

### 1.3 前置条件与造数据说明

1. **首登改密**：种子管理员 `admin / change_me_admin_init_password`（部署实际值）的 `pwd_changed=0`，必须先完成一次首登强制改密流程，方可执行后续需要 ADMIN Token 的用例。改密后新密码同样由部署侧记录，不入仓。
2. **造数据原则**：
   - 禁止使用真实员工档案、真实手机号、真实业务数据；
   - 测试用账号统一用 `testuserXX` + 测试手机号 `13800000XXX` 段（明显占位）；
   - 大批量导入用例使用脚本生成的 `.xlsx`，禁止上传真实通讯录。
3. **基线数据准备**（在 E01 之前完成）：
   - 至少 1 个根部门 + 2 个子部门（用于部门树与子部门级联筛选）；
   - 至少 2 个驿站（1 启用 / 1 停用，用于 4004 校验）；
   - 至少 2 个 STAFF 员工（其中 1 个用于禁用/删除/重置等写操作）；
   - 至少 2 个 ADMIN 员工（用于最后管理员保护 2002 验证——单 admin 时该用例需先造第二个管理员）。

### 1.4 执行顺序建议

```text
首登改密（admin） → E01 接口冒烟 → E03 权限与越权 → E04 导入导出 → E02 前端走查 → E05 PG 双库 → E06 文档一致性复核
```

> E01 优先于 E03/E04：先确认接口契约正常，再做专项；E02 前端走查依赖后端接口已通；E05 双库与 E06 复核可并行。

### 1.5 通用断言约定

| 类型 | 断言点 |
| ---- | ---- |
| 成功响应 | `code==200` + `message=="success"` + `data` 符合接口契约字段 |
| 业务错误 | HTTP 200 + `body.code` 等于对应错误码 + `message` 包含语义关键词 |
| 401/403/404 | HTTP 状态码与 `body.code` 同步（401/403/404） |
| 500 | HTTP 200 + `body.code==500` + `message=="系统繁忙，请稍后重试"`，堆栈只在日志 |
| 脱敏 | 所有 JSON 出参中手机号形如 `138****0000`；导出 xlsx 完整 |
| 分页 | `data` 含 `total/pageNum/pageSize/list` 四字段 |

***

## 2. E01 接口冒烟测试（覆盖全部 24 接口）

> 每个接口至少 1 条正常路径 + 1 条错误路径；错误路径断言到 `body.code`。

### 2.1 认证接口（4 个）

#### TC-E01-001 登录-正常路径（admin）

- 前置：admin 已完成首登改密
- 请求：

```bash
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"<部署实际新密码>\"}"
```

- 预期：`code==200`，`data.token` 非空，`data.expiresIn==86400`，`data.employee.role=="ADMIN"`，`data.employee.pwdChanged==true`，手机号脱敏 `138****0000`

#### TC-E01-002 登录-错误密码（1001）

- 请求：password 故意写错
- 预期：`code==1001`，`message` 含「用户名或密码错误」；不返回 token；`login_log` 写入一条 `login_result=0`、`fail_reason` 含「账号密码错误」

#### TC-E01-003 登录-账号不存在（1001，防探测）

- 请求：username=`notexist_user`
- 预期：`code==1001`（与密码错误同码，防账号探测）；`login_log` 写入 `employee_id=null` 的失败记录

#### TC-E01-004 登录-禁用账号（1002）

- 前置：先准备一个 `status=0` 的 STAFF 账号
- 请求：使用该账号登录
- 预期：`code==1002`，`message` 含「账号已禁用」；不创建会话；`login_log` 写入失败记录

#### TC-E01-005 登录-首登 pwdChanged=false 标识

- 前置：管理员重置某 STAFF 密码后，该账号 `pwd_changed=0`
- 请求：使用重置后的新密码登录
- 预期：`code==200`，`data.employee.pwdChanged==false`（前端据此锁定改密流程）

#### TC-E01-006 退出登录-正常

- 前置：用上一步登录拿到的 Token
- 请求：

```bash
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Authorization: Bearer $TOKEN_STAFF"
```

- 预期：`code==200`，`data==null`；用同一 Token 再调 `/auth/me` 返回 401

#### TC-E01-007 退出登录-幂等（无会话也成功）

- 前置：刚已 logout 的 Token 再调一次
- 预期：`code==200`，`data==null`（幂等设计）

#### TC-E01-008 退出登录-无 Token（401）

- 请求：不带 Authorization 头
- 预期：HTTP 401，`body.code==401`

#### TC-E01-009 当前用户信息-正常

- 请求：

```bash
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN_STAFF"
```

- 预期：`code==200`，`data` 含 `id/username/realName/phone/gender/role/deptId/deptName/stationId/stationName/entryDate/pwdChanged/lastLoginTime`；`phone` 脱敏

#### TC-E01-010 当前用户信息-无 Token（401）

- 预期：HTTP 401，`body.code==401`

#### TC-E01-011 修改本人密码-正常

- 请求：

```bash
curl -s -X PUT "$BASE_URL/auth/password" \
  -H "Authorization: Bearer $TOKEN_STAFF" \
  -H "Content-Type: application/json" \
  -d '{"oldPassword":"<旧密码>","newPassword":"NewPass1234"}'
```

- 预期：`code==200`，`data==null`；调用后旧 Token 立即 401（会话已删）；数据库 `pwd_changed=1`

#### TC-E01-012 修改本人密码-原密码错误（1004）

- 请求：oldPassword 故意写错
- 预期：`code==1004`，`message` 含「原密码错误」

#### TC-E01-013 修改本人密码-强度不足（400）

- 请求：newPassword=`12345678`（纯数字）/`abcd1234`（虽含字母数字但长度边界）/`short12`（<8 位）等
- 预期：`code==400`，`message` 含具体字段问题（如「newPassword 长度需 8-20 位」或「必须同时包含字母和数字」）

### 2.2 看板接口（1 个）

#### TC-E01-014 看板统计-正常

- 请求：

```bash
curl -s -X GET "$BASE_URL/dashboard/summary" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data` 含 `employeeTotal/stationTotal/departmentTotal/todayLoginCount` 四字段；数值与库内统计口径一致（员工含禁用、驿站含停用、部门全量、今日登录按 DISTINCT employee_id）

#### TC-E01-015 看板统计-STAFF 访问（403）

- 前置：使用 STAFF Token
- 预期：HTTP 403，`body.code==403`

#### TC-E01-016 看板统计-无 Token（401）

- 预期：HTTP 401，`body.code==401`

### 2.3 员工接口（10 个）

#### TC-E01-017 员工分页-默认参数

- 请求：

```bash
curl -s -X GET "$BASE_URL/employees" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data.pageNum==1`，`data.pageSize==10`，`data.total` 与库一致，`list[*].phone` 脱敏，按 `createTime DESC` 排序

#### TC-E01-018 员工分页-多条件组合筛选

- 请求：

```bash
curl -s -X GET "$BASE_URL/employees?pageNum=1&pageSize=20&keyword=zhang&deptId=$DEPT_ID_ROOT&stationId=$STATION_ID_OK&status=1" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`list` 中每条记录 `realName/username/phone` 任一含 `zhang`（不区分大小写）、`deptId` 在 `$DEPT_ID_ROOT` 子树内、`stationId==$STATION_ID_OK`、`status==1`

#### TC-E01-019 员工分页-pageSize 上限（≤100）

- 请求：`?pageSize=200`
- 预期：实际返回 `pageSize==100`（B03 上限生效）

#### TC-E01-020 员工分页-STAFF 访问（403）

- 预期：HTTP 403，`body.code==403`

#### TC-E01-021 员工详情-正常

- 请求：

```bash
curl -s -X GET "$BASE_URL/employees/$EMP_ID_STAFF" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data` 为员工 VO（字段与 4.3.1 list 元素一致），`phone` 脱敏

#### TC-E01-022 员工详情-不存在（404）

- 请求：`/employees/9999999`
- 预期：HTTP 404，`body.code==404`，`message` 含「员工不存在」

#### TC-E01-023 新增员工-正常

- 请求：

```bash
curl -s -X POST "$BASE_URL/employees" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser01","password":"Init1234","realName":"测试员工01","phone":"13800000001","gender":1,"deptId":1,"stationId":'"$STATION_ID_OK"',"role":"STAFF","entryDate":"2026-09-01"}'
```

- 预期：`code==200`，`data.id` 为新建员工 ID；库内 `pwd_changed=0`、`status=1`、`role=STAFF`、密码为 BCrypt 散列

#### TC-E01-024 新增员工-账号重复（1003）

- 请求：username 用已存在的 `admin` 或 `testuser01`
- 预期：`code==1003`，`message` 含「登录账号已存在」

#### TC-E01-025 新增员工-手机号重复（2003）

- 请求：phone 用已存在的 `13800000001`
- 预期：`code==2003`，`message` 含「手机号已被其他员工使用」

#### TC-E01-026 新增员工-部门不存在（3001）

- 请求：deptId=9999999
- 预期：`code==3001`

#### TC-E01-027 新增员工-驿站不存在（4001）

- 请求：stationId=9999999
- 预期：`code==4001`

#### TC-E01-028 新增员工-驿站停用（4004）

- 请求：stationId=$STATION_ID_OFF
- 预期：`code==4004`，`message` 含「驿站已停用」

#### TC-E01-029 新增员工-角色非法（400）

- 请求：role=`STATION_ADMIN`（一期未启用）或 `XYZ`
- 预期：`code==400`，`message` 含 role 字段问题

#### TC-E01-030 新增员工-账号格式非法（400）

- 请求：username=`1abc`（数字开头，违反 `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`）
- 预期：`code==400`，`message` 含 username 字段问题

#### TC-E01-031 编辑员工-正常

- 请求：

```bash
curl -s -X PUT "$BASE_URL/employees/$EMP_ID_STAFF" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"realName":"测试员工01改","phone":"13800000099","gender":2,"deptId":1,"stationId":'"$STATION_ID_OK"',"role":"STAFF","entryDate":"2026-09-02","remark":"编辑测试"}'
```

- 预期：`code==200`；列表中该员工字段已更新；`username` 不变

#### TC-E01-032 编辑员工-携带 username 字段（400）

- 请求：body 内带 `"username":"newname"`
- 预期：`code==400`，`message` 含「登录账号不可修改」类提示（接口层拒绝该字段）

#### TC-E01-033 编辑员工-不存在（404）

- 请求：`/employees/9999999`
- 预期：HTTP 404，`body.code==404`

#### TC-E01-034 编辑员工-手机号与他人重复（2003）

- 请求：phone 用另一员工已用号码
- 预期：`code==2003`（编辑时排除自身后查重）

#### TC-E01-035 删除员工-正常

- 前置：使用一个非当前登录、非最后管理员的 STAFF 账号
- 请求：

```bash
curl -s -X DELETE "$BASE_URL/employees/$EMP_ID_TO_DEL" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data==null`；列表查询该 id 返回 404；被删账号 Token 即刻 401；库内 `is_deleted=1`

#### TC-E01-036 删除员工-删自己（2001）

- 请求：删除当前登录账号 id
- 预期：`code==2001`，`message` 含「不允许对当前登录账号执行该操作」

#### TC-E01-037 删除员工-最后管理员（2002）

- 前置：库内仅剩 1 个 `status=1` 且 `is_deleted=0` 的 ADMIN
- 请求：尝试删除该 ADMIN
- 预期：`code==2002`

#### TC-E01-038 删除员工-不存在（404）

- 请求：`/employees/9999999`
- 预期：HTTP 404

#### TC-E01-039 启用/禁用-正常禁用

- 请求：

```bash
curl -s -X PUT "$BASE_URL/employees/$EMP_ID_STAFF/status" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"status":0}'
```

- 预期：`code==200`；库内 `status=0`；该账号 Token 即刻 401；该账号尝试登录返回 1002

#### TC-E01-040 启用/禁用-恢复正常启用

- 请求：`{"status":1}`
- 预期：`code==200`；账号可再次登录

#### TC-E01-041 启用/禁用-禁用自己（2001）

- 请求：对当前登录账号 id 设置 status=0
- 预期：`code==2001`

#### TC-E01-042 启用/禁用-最后管理员（2002）

- 前置：库内仅剩 1 个可用 ADMIN
- 请求：禁用该 ADMIN
- 预期：`code==2002`

#### TC-E01-043 重置密码-正常

- 请求：

```bash
curl -s -X PUT "$BASE_URL/employees/$EMP_ID_STAFF/password/reset" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"Reset1234"}'
```

- 预期：`code==200`；库内 `pwd_changed=0`、密码散列已更新；旧 Token 即刻 401；该账号用新密码登录返回 `pwdChanged==false`（首登改密）

#### TC-E01-044 重置密码-重置自己（2001）

- 请求：对当前登录账号 id 重置
- 预期：`code==2001`（提示走 4.1.4 修改本人密码）

#### TC-E01-045 重置密码-强度不足（400）

- 请求：newPassword=`123`
- 预期：`code==400`

#### TC-E01-046 下载导入模板-正常

- 请求：

```bash
curl -s -X GET "$BASE_URL/employees/import-template" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -o template.xlsx
```

- 预期：HTTP 200；`Content-Type` 为 xlsx 流；文件可正常打开；表头 8 列（姓名/登录账号/手机号/性别/部门名称/驿站名称/入职日期/备注）+ 批注说明必填与格式；**不含示例数据行**

#### TC-E01-047 下载导入模板-STAFF 访问（403）

- 预期：HTTP 403

#### TC-E01-048 Excel 导入-正常小批量

- 前置：用脚本生成一份 5 行合规 `.xlsx`
- 请求：

```bash
curl -s -X POST "$BASE_URL/employees/import" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -F "file=@batch_ok.xlsx"
```

- 预期：`code==200`，`data.total==5`，`data.successCount==5`，`data.failCount==0`；库内新增 5 条员工（`role=STAFF`、`status=1`、`pwd_changed=0`、密码散列相同）

#### TC-E01-049 Excel 导入-空文件（5001）

- 前置：构造一个 0 字节或仅有表头无数据行的 xlsx
- 预期：`code==5001`，`message` 含「导入文件为空或格式不正确」

#### TC-E01-050 Excel 导入-非 xlsx（5001）

- 前置：构造 `.csv` 或 `.pdf` 文件上传
- 预期：`code==5001`

#### TC-E01-051 Excel 导入-超 1000 行（5002）

- 前置：生成 1001 行数据
- 预期：`code==5002`，`message` 含「导入数据超过单次上限（1000 行）」

#### TC-E01-052 Excel 导入-混合脏数据（5003）

- 前置：生成一份包含重复账号、非法手机号、不存在部门、同名多部门、错误日期等的混合脏数据
- 预期：`code==5003`，`message` 含「共 N 行失败，全部数据未入库」；`data.errors` 数组每条 `{row, field, message}`，`row` 为 Excel 真实行号（表头=1，首条数据=2）；**库内无任何新增**（事务保证，无半成功）

#### TC-E01-053 Excel 导出-全量

- 请求：

```bash
curl -s -X GET "$BASE_URL/employees/export" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -o export_all.xlsx
```

- 预期：HTTP 200；`Content-Disposition` 含 `员工数据_yyyyMMdd.xlsx` 格式（日期为当日）；12 列顺序与 api.md 第 6 章一致；**手机号完整**（非脱敏）；行数 = 列表全量 total

#### TC-E01-054 Excel 导出-带筛选

- 请求：`?keyword=zhang&deptId=1&status=1`
- 预期：导出数据 = 同条件列表分页的 `total` 条；字段一致

#### TC-E01-055 Excel 导出-STAFF 访问（403）

- 预期：HTTP 403

### 2.4 部门接口（4 个）

#### TC-E01-056 部门树-正常

- 请求：

```bash
curl -s -X GET "$BASE_URL/departments/tree" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data` 为数组（多个根节点时数组多元素）；每个节点含 `id/parentId/deptName/sortOrder/employeeCount/children`；`employeeCount` 为直属员工数（不含子部门）；同级按 `sortOrder` 升序

#### TC-E01-057 部门树-无 Token（401）

- 预期：HTTP 401

#### TC-E01-058 新增部门-根部门

- 请求：

```bash
curl -s -X POST "$BASE_URL/departments" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"parentId":0,"deptName":"测试根部门","sortOrder":1}'
```

- 预期：`code==200`，`data.id` 为新部门 ID

#### TC-E01-059 新增部门-子部门

- 请求：parentId=`$DEPT_ID_ROOT`
- 预期：`code==200`，`data.id` 返回

#### TC-E01-060 新增部门-parentId 无效（3001）

- 请求：parentId=9999999
- 预期：`code==3001`

#### TC-E01-061 新增部门-同级重名（3004）

- 请求：parentId 与已存在部门同父，deptName 重复
- 预期：`code==3004`

#### TC-E01-062 编辑部门-正常

- 请求：仅传 `deptName` 与 `sortOrder`
- 预期：`code==200`；树中该节点字段已更新

#### TC-E01-063 编辑部门-携带 parentId（400）

- 请求：body 内带 `"parentId":2`（与原父级不同）
- 预期：`code==400`，`message` 含「不允许修改父级」类提示（决策 D12）

#### TC-E01-064 编辑部门-同级重名（3004）

- 请求：deptName 改为同级已有名称
- 预期：`code==3004`

#### TC-E01-065 编辑部门-不存在（404）

- 请求：`/departments/9999999`
- 预期：HTTP 404

#### TC-E01-066 删除部门-正常

- 前置：一个无子部门、无员工的叶子部门
- 请求：`DELETE /departments/{id}`
- 预期：`code==200`；树中不再返回该节点

#### TC-E01-067 删除部门-有子部门（3002）

- 前置：父部门下还有子部门
- 预期：`code==3002`

#### TC-E01-068 删除部门-有员工（3003）

- 前置：部门下直属员工 > 0
- 预期：`code==3003`

#### TC-E01-069 删除部门-不存在（404）

- 预期：HTTP 404

### 2.5 驿站接口（5 个）

#### TC-E01-070 驿站列表-全量

- 请求：

```bash
curl -s -X GET "$BASE_URL/stations" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

- 预期：`code==200`，`data` 为数组（不分页）；每个元素含 `id/code/stationName/contactPerson/contactPhone/address/status/employeeCount/remark/createTime`；`contactPhone` 脱敏

#### TC-E01-071 驿站列表-status 过滤

- 请求：`?status=0`
- 预期：`data` 仅含 `status==0` 驿站

#### TC-E01-072 驿站列表-无 Token（401）

- 预期：HTTP 401

#### TC-E01-073 新增驿站-正常

- 请求：

```bash
curl -s -X POST "$BASE_URL/stations" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"code":"TEST001","stationName":"测试驿站","contactPerson":"测试负责人","contactPhone":"13800000088","address":"测试地址 1 号","remark":"测试"}'
```

- 预期：`code==200`，`data.id` 返回

#### TC-E01-074 新增驿站-code 重复（4002）

- 请求：code 用已存在值
- 预期：`code==4002`

#### TC-E01-075 新增驿站-code 格式非法（400）

- 请求：code=`测试编码!`（含特殊字符，违反 `^[A-Za-z0-9_-]{2,50}$`）
- 预期：`code==400`

#### TC-E01-076 编辑驿站-正常

- 请求：修改 stationName 与 address
- 预期：`code==200`；列表中字段更新

#### TC-E01-077 编辑驿站-code 重复（4002）

- 请求：code 改为另一驿站已用值
- 预期：`code==4002`

#### TC-E01-078 编辑驿站-不存在（404）

- 预期：HTTP 404

#### TC-E01-079 驿站启用/停用-正常停用

- 请求：

```bash
curl -s -X PUT "$BASE_URL/stations/$STATION_ID_OK/status" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"status":0}'
```

- 预期：`code==200`；列表 status 变为 0；新增/编辑员工选择该驿站返回 4004；存量员工归属保留、可登录

#### TC-E01-080 驿站启用/停用-不存在（404）

- 请求：`/stations/9999999/status`
- 预期：HTTP 404

#### TC-E01-081 删除驿站-正常

- 前置：无归属员工的驿站
- 请求：`DELETE /stations/{id}`
- 预期：`code==200`；列表不再返回

#### TC-E01-082 删除驿站-有员工（4003）

- 前置：驿站下有员工
- 预期：`code==4003`

#### TC-E01-083 删除驿站-不存在（404）

- 预期：HTTP 404

**E01 小计：83 条用例（覆盖 24 接口，含 24 条以上正常路径 + 各错误码路径）**

***

## 3. E02 前端功能走查清单（ADMIN/STAFF 双视角）

> 走查以浏览器手工执行为主，对照 requirement.md 第 4 章验收标准与 5.1 页面清单。

### 3.1 通用交互（适用所有页面）

#### TC-E02-001 危险操作二次确认

- 范围：删除员工、禁用员工、重置密码、删除部门、删除驿站、停用驿站
- 预期：每个操作弹出二次确认框，取消不执行，确认才发请求

#### TC-E02-002 写操作反馈

- 范围：所有写操作（新增/编辑/删除/启停/重置/导入/导出）
- 预期：成功 → Element Plus Message 成功提示；失败 → 按 `body.code` 文案提示（1003/2003/4004 等针对性区分，非笼统「操作失败」）

#### TC-E02-003 表格 loading 与空态

- 范围：员工/驿站/部门列表
- 预期：请求期间表格显示 loading；无数据时显示空态图/文案

#### TC-E02-004 分页器默认值

- 范围：员工列表
- 预期：默认 10 条/页，可选 10/20/50；切换页码触发请求并保留筛选条件

### 3.2 ADMIN 视角

#### TC-E02-005 ADMIN 登录后落地页

- 操作：admin 账号（已改密）登录
- 预期：自动跳转 `/dashboard`；URL 直接访问 `/login` 已登录则跳走

#### TC-E02-006 ADMIN 菜单渲染完整性

- 预期：侧边菜单含「首页 / 员工管理 / 组织管理（部门、驿站）/ 个人中心」全量；既有 5 个占位页面（knowledge/money/performance/permission/system）不渲染

#### TC-E02-007 数据看板页

- 预期：4 个指标卡片渲染；数值与 `/dashboard/summary` 一致；网络异常时显示错误态而非空白

#### TC-E02-008 员工管理页-筛选与表格

- 预期：筛选区关键字/部门树/驿站下拉/状态；表格手机号脱敏展示；性别/角色/状态列中文映射（男/女/未知、管理员/员工、启用/禁用）

#### TC-E02-009 员工管理页-新增弹窗

- 预期：表单校验与后端规则一致（username 格式、phone 11 位、密码强度）；提交成功后列表自动刷新

#### TC-E02-010 员工管理页-编辑弹窗

- 预期：账号字段只读（不可编辑）；保存后列表字段即时更新；后端业务错误码（1003/2003/4004 等）针对性提示

#### TC-E02-011 员工管理页-启停/重置/删除

- 预期：均二次确认；成功后列表刷新；删自己/最后管理员场景显示 2001/2002 文案

#### TC-E02-012 员工导入导出

- 预期：「下载模板」拉取文件流并保存为 xlsx；导入弹窗支持上传 .xlsx，展示成功数/失败数，失败明细表格（行号/字段/原因），5003 时弹窗展示明细；「导出」按当前筛选下载，Blob 处理中文文件名

#### TC-E02-013 部门管理页

- 预期：树形展示含直属员工数与排序；新增根部门/子部门；编辑时父级不可改；删除时 3002/3003/3004 错误友好提示

#### TC-E02-014 驿站管理页

- 预期：列表（编码/名称/联系人/电话脱敏/状态/员工数/地址）；新增/编辑 code 前端格式校验；启停开关 + 确认；4002/4003 错误提示

#### TC-E02-015 个人中心页（ADMIN）

- 预期：本人信息卡（手机脱敏）；修改密码表单（原密码 + 新密码 + 强度提示）；改密成功后清登录态回登录页

### 3.3 STAFF 视角

#### TC-E02-016 STAFF 登录后落地页

- 操作：STAFF 账号（已改密）登录
- 预期：自动跳转 `/profile`（非 `/dashboard`）

#### TC-E02-017 STAFF 菜单渲染

- 预期：侧边菜单仅显示「个人中心」；不渲染首页/员工管理/组织管理

#### TC-E02-018 STAFF 越权 URL 跳转

- 操作：URL 直接访问 `/dashboard`、`/employee`、`/department`、`/station`
- 预期：路由守卫拦截，重定向到 `/profile`（不报错不闪烁）

#### TC-E02-019 STAFF 个人中心-修改密码

- 预期：可修改本人密码；改密成功后回登录页

#### TC-E02-020 STAFF 调用管理接口后端拦截（403）

- 操作：通过浏览器 devtools 或在 `/profile` 页面无法触发管理接口，但仍验证后端：用 STAFF Token 调 `/employees` 等
- 预期：后端返回 403（前端无入口不影响）

### 3.4 首登强制改密锁定

#### TC-E02-021 首登锁定路由

- 前置：种子 admin 首登或被重置密码的 STAFF 登录
- 预期：登录后跳到 `/profile` 改密卡片；URL 直接访问 `/dashboard` 等其他路由被路由守卫锁死，仍回到 `/profile` 改密卡片

#### TC-E02-022 首登改密流程闭环

- 预期：改密成功 → 清登录态 → 回登录页 → 用新密码重新登录 → `pwdChanged==true` → 按角色落地

#### TC-E02-023 首登改密-原密码错误提示

- 预期：1004 文案「原密码错误」明确区分于 1001

**E02 小计：23 条用例**

***

## 4. E03 权限与越权测试

### 4.1 无 Token 访问（401）

#### TC-E03-001 无 Token 调认证外接口

- 范围抽样：`/auth/me`、`/employees`、`/departments/tree`、`/stations`、`/dashboard/summary`、`/employees/export`
- 预期：每个均返回 HTTP 401，`body.code==401`，`message` 含「未登录或登录态已失效」

#### TC-E03-002 无 Token 调白名单接口正常

- 范围：`/auth/login`
- 预期：不受 401 影响，可正常返回 1001/1002/200

#### TC-E03-003 Token 格式非法

- 请求：`Authorization: Bearer not_a_jwt` 或 `Authorization: xxx`
- 预期：HTTP 401，`body.code==401`

### 4.2 STAFF 访问 ADMIN 接口（403）

#### TC-E03-004 STAFF 访问管理类接口

- 范围抽样：`/dashboard/summary`、`/employees`（GET/POST）、`/employees/{id}`（GET/PUT/DELETE）、`/employees/{id}/status`、`/employees/{id}/password/reset`、`/employees/import-template`、`/employees/import`、`/employees/export`、`/departments`（全部方法）、`/stations`（全部方法）
- 预期：每个均返回 HTTP 403，`body.code==403`，`message` 含「无权限访问该资源」

#### TC-E03-005 STAFF 调用自身可访问接口正常

- 范围：`/auth/me`、`/auth/password`、`/auth/logout`
- 预期：200 正常返回（不报 403）

### 4.3 会话失效与互踢

#### TC-E03-006 禁用员工后存量 Token 即刻 401

- 步骤：
  1. STAFF A 登录拿 Token A；
  2. ADMIN 调 `PUT /employees/{A_id}/status` 设 status=0；
  3. 用 Token A 调 `/auth/me`。
- 预期：第 3 步返回 401（会话已删）

#### TC-E03-007 删除员工后存量 Token 即刻 401

- 步骤：ADMIN 删除 STAFF A → 用 A 旧 Token 调 `/auth/me`
- 预期：401

#### TC-E03-008 重置密码后存量 Token 即刻 401

- 步骤：ADMIN 重置 STAFF A 密码 → 用 A 旧 Token 调 `/auth/me`
- 预期：401；且 A 用新密码登录后 `pwdChanged==false`

#### TC-E03-009 修改本人密码后旧 Token 即刻 401

- 步骤：STAFF A 调 `/auth/password` 改密 → 用旧 Token 调 `/auth/me`
- 预期：401

#### TC-E03-010 同账号新登录旧 Token 401（互踢）

- 步骤：
  1. STAFF A 在客户端 1 登录，拿 Token A1；
  2. STAFF A 在客户端 2 用同账号同密码登录，拿 Token A2；
  3. 用 Token A1 调 `/auth/me`。
- 预期：第 3 步返回 401（jti 不匹配，旧会话被覆盖）；Token A2 仍可用

#### TC-E03-011 退出后旧 Token 401

- 步骤：STAFF A 调 `/auth/logout` → 用旧 Token 调 `/auth/me`
- 预期：401

### 4.4 自我保护（2001）

#### TC-E03-012 禁用自己（2001）

- 步骤：ADMIN 调 `PUT /employees/{self_id}/status` status=0
- 预期：`code==2001`

#### TC-E03-013 删除自己（2001）

- 步骤：ADMIN 调 `DELETE /employees/{self_id}`
- 预期：`code==2001`

#### TC-E03-014 重置自己密码（2001）

- 步骤：ADMIN 调 `PUT /employees/{self_id}/password/reset`
- 预期：`code==2001`，`message` 提示走 `/auth/password`

#### TC-E03-015 角色降级自己（2001）

- 步骤：ADMIN 调 `PUT /employees/{self_id}` 将 role 从 ADMIN 改为 STAFF
- 预期：`code==2001`

### 4.5 最后管理员保护（2002）

#### TC-E03-016 禁用最后管理员（2002）

- 前置：库内 `status=1 AND is_deleted=0 AND role=ADMIN` 仅剩 1 条
- 步骤：尝试禁用该 ADMIN
- 预期：`code==2002`

#### TC-E03-017 删除最后管理员（2002）

- 前置：同上
- 步骤：尝试删除该 ADMIN
- 预期：`code==2002`

#### TC-E03-018 角色降级最后管理员（2002）

- 前置：同上
- 步骤：尝试将该 ADMIN role 改为 STAFF
- 预期：`code==2002`

#### TC-E03-019 最后管理员保护-非最后场景通过

- 前置：库内有 2 个以上可用 ADMIN
- 步骤：ADMIN A 调 `PUT /employees/{A_id}/status` status=0（自我保护会先拦截 2001）——换一个场景：ADMIN A 调 `PUT /employees/{B_id}/status` status=0，库内仍剩至少 1 个可用 ADMIN（A 自己）
- 预期：`code==200`（不触发 2002）

**E03 小计：19 条用例**

***

## 5. E04 导入导出专项

### 5.1 导入-文件级错误

#### TC-E04-001 空文件（5001）

- 前置：构造 0 字节 xlsx 或仅表头无数据行
- 预期：`code==5001`

#### TC-E04-002 非 xlsx 文件（5001）

- 前置：上传 `.csv`、`.pdf`、`.txt`、`.xls`（旧格式非 xlsx）
- 预期：`code==5001`，`message` 含「仅支持 .xlsx」

#### TC-E04-003 超 10MB 文件

- 前置：构造 > 10MB 的 xlsx
- 预期：被 `MaxUploadSizeExceededException` 拦截（B02 全局映射为 400 文件过大）或 5001；以实际实现为准，需在缺陷清单记录口径
- 设计疑问：见文末 Q1

#### TC-E04-004 数据行 > 1000（5002）

- 前置：1001 行数据
- 预期：`code==5002`，`message` 含「导入数据超过单次上限（1000 行）」

### 5.2 导入-混合脏数据（5003）

#### TC-E04-005 重复账号（库内 + 文件内）

- 前置：xlsx 中第 2 行 username=`admin`（库内已有）；第 3、4 行 username 相同（文件内重复）
- 预期：`code==5003`，`data.errors` 含 row=2/3/4（或 3/4，按去重策略，以实现为准）、field=`登录账号`、message 含「已被使用」/「文件内重复」

#### TC-E04-006 非法手机号

- 前置：phone=`12345`、`1380000000a`
- 预期：`code==5003`，errors 含 field=`手机号`、message 含「格式不正确」

#### TC-E04-007 不存在部门

- 前置：部门名称填 `不存在的部门`
- 预期：`code==5003`，errors 含 field=`部门名称`、message 含「不存在」

#### TC-E04-008 同名多部门

- 前置：库内同名部门 ≥ 2 个；xlsx 中填该名称
- 预期：`code==5003`，errors 含 message「部门名称不唯一，请先规范部门命名」

#### TC-E04-009 错误日期格式

- 前置：入职日期填 `2026/09/01`、`20260901`
- 预期：`code==5003`，errors 含 field=`入职日期`、message 含「格式」

#### TC-E04-010 驿站停用

- 前置：xlsx 中驿站名称对应库内 `status=0` 的驿站
- 预期：`code==5003`，errors 含 field=`驿站名称`、message 含「停用」/「不可归属」

#### TC-E04-011 混合脏数据事务性（无半成功）

- 前置：xlsx 共 10 行，其中第 3、7 行脏数据，其余 8 行合规
- 预期：`code==5003`，`data.failCount==2`，`data.total==10`；**库内 0 条新增**（事务回滚）；再次查询 `/employees` 总数不变

#### TC-E04-012 修正后原样重导成功

- 前置：将 TC-E04-011 的脏数据修正后重传同一文件
- 预期：`code==200`，`data.successCount==10`，`data.failCount==0`；库内新增 10 条；**所有新增员工 `pwd_changed=0`、`role=STAFF`、`status=1`、密码散列相同**（散列复用）

### 5.3 导出专项

#### TC-E04-013 导出-列序与字段一致

- 预期：xlsx 12 列顺序为 登录账号/姓名/手机号/性别/部门/驿站/角色/状态/入职日期/最后登录时间/创建时间/备注；性别中文（未知/男/女）、角色中文（管理员/员工）、状态中文（禁用/启用）

#### TC-E04-014 导出-手机号完整

- 预期：xlsx 中手机号列完整 11 位，**非脱敏**（决策 D5 例外）

#### TC-E04-015 导出-文件名格式

- 预期：`Content-Disposition` 含 `员工数据_yyyyMMdd.xlsx`（当日日期）；UTF-8 编码处理中文

#### TC-E04-016 导出-与筛选一致性

- 步骤：
  1. 在 `/employees?keyword=zhang&status=1` 查列表，记录 total；
  2. 用相同筛选 `/employees/export?keyword=zhang&status=1` 导出。
- 预期：xlsx 行数 == 列表 total；每行数据符合筛选条件

#### TC-E04-017 导出-空结果

- 前置：筛选条件命中 0 条
- 预期：xlsx 仅含表头，0 数据行；`code==200`

**E04 小计：17 条用例**

***

## 6. E05 PostgreSQL 双库验证（P2）

> 一期允许上线后补验，但脚本必须随一期交付。本节用于部署 PG 环境后执行。

### 6.1 Migration 执行

#### TC-E05-001 PG 执行 V1__init_schema.sql

- 步骤：在空 PG 库 `kdyzgl` 启动后端（`spring.flyway.locations=classpath:db/migration/{vendor}` 自动选 `postgresql/`）
- 预期：启动日志显示 Flyway 执行 `V1__init_schema.sql` 成功；4 张表（department/station/employee/login_log）建立；字段类型按 db.md 1.5 映射（SMALLINT/TIMESTAMP/IDENTITY）；索引 `idx_*` 创建

#### TC-E05-002 PG 执行 V2__init_data.sql

- 预期：种子部门「总公司」(id=1) 与 admin 账号 (id=1, role=ADMIN, pwd_changed=0) 写入；`setval` 序列重置不冲突

### 6.2 核心链路一致性比对（与 MySQL 行为一致）

#### TC-E05-003 登录/退出/me/改密一致性

- 预期：与 MySQL 环境同样返回 200/1001/1002/1004/401；`login_log` 写入正常

#### TC-E05-004 员工 CRUD 一致性

- 预期：新增/编辑/删除/启停/重置在 PG 上同样触发 1003/2003/3001/4001/4004/2001/2002 等错误码；逻辑删除、最后管理员保护一致

#### TC-E05-005 部门接口一致性

- 预期：树形递归、3002/3003/3004 错误码一致

#### TC-E05-006 驿站接口一致性

- 预期：4002/4003/4004 一致；停用驿站下员工归属保留行为一致

#### TC-E05-007 导入导出一致性

- 预期：5001/5002/5003 一致；散列复用、事务回滚一致；导出 xlsx 字段一致

#### TC-E05-008 看板统计一致性

- 预期：PG 用 `CURRENT_DATE` 替代 `CURDATE()`，`todayLoginCount` 与 MySQL 同口径结果一致

#### TC-E05-009 时间字段一致性

- 预期：`create_time`/`update_time` 由应用层 `MetaObjectHandler` 填充，PG 与 MySQL 一致；不依赖 `ON UPDATE`

#### TC-E05-010 双库差异记录

- 预期：执行中发现的所有差异记录到缺陷清单，且回写 db.md（如生成 IDENTITY 起始值差异、字符串比较大小写差异等）

**E05 小计：10 条用例**

***

## 7. E06 文档一致性复核清单

> 对照 db.md / api.md / requirement.md 与最终实现逐项比对，发现偏差回写文档并同步 sql/schema 快照。

### 7.1 数据库结构（db.md）

#### TC-E06-001 4 张表字段一致性

- 范围：department / station / employee / login_log 字段名、类型、默认值、允许空、注释
- 比对对象：实际库结构（`SHOW CREATE TABLE` / PG `\d`）vs `db.md` 第 3 章 vs `sql/schema/{mysql,postgresql}/init.sql` 快照 vs Flyway V1
- 预期：四方一致

#### TC-E06-002 索引一致性

- 范围：所有 `idx_*` 索引名与字段
- 预期：db.md、Flyway、sql/schema、实际库一致

#### TC-E06-003 逻辑删除与时间字段

- 范围：`is_deleted` 默认 0；`create_time/update_time` 应用层填充，不依赖 `ON UPDATE`
- 预期：实现一致

#### TC-E06-004 种子数据一致性

- 范围：V2__init_data.sql 含部门 id=1 + admin id=1（pwd_changed=0）；BCrypt 散列 `$2a$` cost=10
- 预期：与 db.md 5.2 一致；脚本内无明文密码

### 7.2 接口契约（api.md）

#### TC-E06-005 24 接口路径与方法

- 范围：api.md 4.0 概览表 24 条
- 预期：实现路径、HTTP 方法、权限标注完全一致

#### TC-E06-006 统一响应结构

- 范围：`{code,message,data}`；分页 `{total,pageNum,pageSize,list}`；HTTP 状态码与 body.code 映射规则
- 预期：实现一致（401/403/404 同步 HTTP 状态码）

#### TC-E06-007 错误码完整覆盖

- 范围：api.md 2.2 错误码表 19 条
- 预期：每个错误码在对应场景实际触发，且未出现文档外的错误码

#### TC-E06-008 手机号脱敏规则

- 范围：11 位 `138****5678`、非 11 位前 3 + `****`；所有 JSON 出参脱敏；导出 xlsx 完整
- 预期：实现一致

#### TC-E06-009 接口入参校验规则

- 范围：username `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`、phone `^1[3-9]\d{9}$`、station.code `^[A-Za-z0-9_-]{2,50}$`、密码 8-20 位含字母数字
- 预期：实现一致；不合规返回 400 且 message 含字段名

### 7.3 需求功能（requirement.md）

#### TC-E06-010 第 4 章 P0 功能完整性

- 范围：F1-1~F1-5、F3-1~F3-6、F4-1~F4-4、F5-1~F5-5
- 预期：全部 P0 功能验收标准通过

#### TC-E06-011 第 4 章 P1 功能闭环

- 范围：F1-5 个人中心、F2-1 看板、F3-7 导入、F3-8 导出
- 预期：上线后一周内闭环，交付物存在

#### TC-E06-012 权限矩阵（requirement.md 3.2）

- 范围：ADMIN 全量、STAFF 仅个人中心
- 预期：后端 `@RequireAdmin` 拦截 + 前端路由守卫双层一致

#### TC-E06-013 账号状态联动（requirement.md 5.4）

- 范围：禁用/删除/重置/改密/互踢/自我保护/最后管理员保护
- 预期：实现一致（E03 已覆盖）

#### TC-E06-014 首登强制改密流程（requirement.md 5.3）

- 范围：pwd_changed=0 登录后路由锁死 `/profile`；改密成功清登录态回登录页
- 预期：实现一致（E02 已覆盖）

#### TC-E06-015 决策记录一致性

- 范围：D1 员工即账号、D3 单 Token、D4 整批校验、D5 脱敏规则、D6 不建外键、D7 唯一性 Service 层、D8 应用层填充时间、D9 同源免 CORS、D10 仅引 spring-security-crypto、D11 角色字符串、D12 部门父级不可改、D13 自增主键
- 预期：实现与决策一致

**E06 小计：15 条用例**

***

## 8. 缺陷记录模板

> 实际执行后填写；编号规则 `BUG-{阶段}-{序号}`。

| 编号 | 发现日期 | 关联用例 | 现象描述（含复现步骤） | 预期结果 | 实际结果 | 严重级 | 状态 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| BUG-E01-001 |  | TC-E01-XXX |  |  |  |  | 待处理 |
| BUG-E02-001 |  | TC-E02-XXX |  |  |  |  | 待处理 |
| BUG-E03-001 |  | TC-E03-XXX |  |  |  |  | 待处理 |
| BUG-E04-001 |  | TC-E04-XXX |  |  |  |  | 待处理 |
| BUG-E05-001 |  | TC-E05-XXX |  |  |  |  | 待处理 |
| BUG-E06-001 |  | TC-E06-XXX |  |  |  |  | 待处理 |

**严重级定义**：

| 级别 | 含义 |
| ---- | ---- |
| 致命 | 阻塞主流程，无法继续测试（如登录、鉴权、核心 CRUD 不可用） |
| 严重 | 主功能不可用但有规避路径，或核心错误码与文档不符 |
| 一般 | 边界、提示文案、UI 渲染、非 P0 功能问题 |
| 轻微 | 文案、样式、不影响功能的体验问题 |

***

## 9. 用例总数统计

| 阶段 | 描述 | 用例数 |
| ---- | ---- | ---- |
| E01 | 接口冒烟（24 接口） | 83 |
| E02 | 前端功能走查 | 23 |
| E03 | 权限与越权 | 19 |
| E04 | 导入导出专项 | 17 |
| E05 | PostgreSQL 双库 | 10 |
| E06 | 文档一致性复核 | 15 |
| **总计** | | **167** |

***

## 10. 设计疑问记录

> 下列疑问为设计/编写过程中发现的文档间疑似矛盾或未明确点，需与产品/开发侧确认后回写文档，未确认前以 api.md 为准。

### Q1 导入超 10MB 文件的错误码口径

- 出处：api.md 4.3.9 / 2.2 错误码 5001 / B02 异常映射
- 现象：api.md 5001 描述「导入文件为空或格式不正确（仅支持 .xlsx）」，而上传大小超 10MB 的拦截由 `MaxUploadSizeExceededException` 全局映射为 400（B02 任务描述）。
- 疑问：超 10MB 应返回 5001 还是 400？两者都合理但口径不一致，需明确。
- 暂定：按 B02 全局映射返回 400（TC-E04-003 标注「以实际实现为准」）。

### Q2 登录接口 username 校验规则

- 出处：api.md 4.1.1（「4-30 位」）vs db.md 3.3 employee.username（`^[a-zA-Z][a-zA-Z0-9_]{3,29}$`，字母开头 + 总长 4-30）
- 现象：登录接口文档省略了「字母开头」「字符集」约束，仅写长度。若用户传 `1abc` 登录，按 db.md 规则本就不可能创建出来，但若接口层独立校验，规则需对齐。
- 建议：api.md 4.1.1 同步写明完整正则。

### Q3 驿站 contactPhone 是否允许座机

- 出处：api.md 4.5.2 「手机号格式（非必填）」、db.md 3.2 station.contact_phone 注释「联系电话（出参脱敏，规则同手机号）」
- 现象：联系人电话实际场景可能是座机，但文档要求手机号格式。
- 疑问：是否放宽为「电话号码格式」？脱敏规则是否同步放宽？
- 暂定：按 api.md 执行，仅接受手机号格式。

### Q4 编辑员工时清空 deptId / stationId 是否允许

- 出处：api.md 4.3.4 未明确
- 现象：员工 deptId/stationId 在 db.md 允许为 NULL，但编辑接口入参未明确「传 null 表示清空」的语义。
- 建议：明确传 null 与不传字段的差异（JSON 反序列化下两者等价，需约定）。

### Q5 导入成功响应字段对称性

- 出处：api.md 4.3.9
- 现象：成功响应 `{total, successCount, failCount}`，失败响应 `{total, failCount, errors}`——成功响应无 `errors` 字段、失败响应无 `successCount` 字段。
- 疑问：前端是否需要统一结构？建议成功响应也带 `errors: []`、失败响应带 `successCount: 0`，便于前端表格统一渲染。
- 暂定：按文档现状执行。

### Q6 退出登录无会话时的响应

- 出处：api.md 4.1.2「幂等，无会话也返回成功」
- 现象：无会话场景下，若 Token 本身已过期/失效，过滤器会先返回 401，根本走不到 logout 业务。
- 疑问：logout 是否应该作为白名单接口（不走 JwtAuthFilter）？还是接受 401 即可（前端清本地态即可）？
- 暂定：按 401 处理（前端响应拦截器遇 401 已自动清 store 跳登录页，等价于 logout 效果）。

### Q7 部门编辑接口对 parentId 字段的处理方式

- 出处：api.md 4.4.3「传入时报 400」
- 现象：入参定义只有 `deptName`/`sortOrder`，但请求体若多传 `parentId` 字段，行为是「忽略」还是「报 400」？文档表述「忽略并拒绝」「传入时报 400」语义偏向后者。
- 疑问：Jackson 默认对未知字段忽略（除非 `FAIL_ON_UNKNOWN_PROPERTIES`），如要报 400 需要显式校验。
- 暂定：按文档「传入 parentId 报 400」执行（TC-E01-063）；若实现为「忽略」，记入 BUG 并回写文档。

### Q8 login_log 的 fail_reason 取值口径

- 出处：db.md 3.4 `fail_reason VARCHAR(50)`，requirement.md F1-1 提到「含原因」
- 现象：fail_reason 取值未在文档列出枚举（如「账号密码错误」「账号已禁用」「账号不存在」），登录失败时具体写哪个？
- 建议：明确取值清单，便于审计筛选。
- 暂定：按 1001/1002 对应原因字符串记录。

***

## 附录 · 用例执行检查表

执行人按阶段填写，便于追踪进度：

| 阶段 | 用例数 | 已执行 | 通过 | 失败 | 阻塞 | 备注 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| E01 | 83 | 0 | 0 | 0 | 0 |  |
| E02 | 23 | 0 | 0 | 0 | 0 |  |
| E03 | 19 | 0 | 0 | 0 | 0 |  |
| E04 | 17 | 0 | 0 | 0 | 0 |  |
| E05 | 10 | 0 | 0 | 0 | 0 |  |
| E06 | 15 | 0 | 0 | 0 | 0 |  |
| 总计 | 167 | 0 | 0 | 0 | 0 |  |
