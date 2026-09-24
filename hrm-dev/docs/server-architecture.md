# 快递驿站智汇系统 · 服务端架构方案（24 → 145 接口）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-24 |
| 作者 | 架构师 `express-station-architect` |
| 状态 | 待主智能体 Review；含开放问题（见第 8 章）需用户裁定 |
| 关联文档 | [api.md](api.md)、[db.md](db.md)、[plan.md](plan.md)、[algo-hrm-server.md](algo-hrm-server.md)、[requirement.md](requirement.md)、[deploy.md](deploy.md)、[demo-leave-design.md](demo-leave-design.md) |
| 行为规格来源 | `hrm-dev/hrm-demo/src/shared/mock/`（145 条路由 + store 行为，`API_PREFIX=/api/v1`） |
| 交付范围 | 仅架构设计：分层、模块边界、接口与数据库顶层设计、ADR、分批实施计划。**不含业务代码、DDL、迁移脚本、前端改动** |
| 运行验证 | 本机无 JDK/Maven/MySQL/Redis → 全部验收条款为「可静态审查」；编译、Flyway 迁移、端到端冒烟**收敛到服务器阶段**（见 6.4） |

---

## 0. 摘要（结论先行）

1. **架构风格不变**：继续沿用现有 `hrm-server` 的**模块化单体**（Spring Boot 3.3.4 + Java 17 + MyBatis-Plus 3.5.7 + MySQL 8 单库 + Redis + jjwt + EasyExcel + Flyway），不引入微服务、不引入消息队列。14 个新增领域以**域子包**扩展（ADR-01）。
2. **145 接口分 11 批**（P0 地基 → P1~P8 领域 → P10 大表域最后），每批可独立交付、独立验收，批次映射见 6.1/6.3。
3. **数据库由 4 表扩至约 36 表**，Flyway 从 `V3` 起只增不改，批次与版本号 1:1 对应（见 4.4）。
4. **关键结论：不支持"统一越权语义"**。Mock 的 403/404 分界在**端点**级（同一模块内混用），必须逐端点声明，任何"全局统一为 404"或"全局统一为 403"的实现都会破坏契约（ADR-07）。
5. **数据范围收敛分三层落地**：查询参数 `stationId` 全局静默收敛（对应 Mock `applyDataScope`）；角色门槛全局 403；**路径资源跨站访问按端点声明 403 或 404**（ADR-02）。
6. **与现有实现的 13 项不一致/改造点**见第 3 章；其中 5 项为对任务背景材料的**事实性补充/纠正**，见 3.3。
7. **§12.1 定向豁免**按用户授权登记，见第 7 章；`hrm-admin` 零改动、`hrm-demo` 源码零改动（Mock 保留为行为规格）。
8. **14 项开放问题**必须由用户裁定（含部署目标、公网回调、口径 Q1~Q6），见第 8 章。

---

## 1. 总体架构与分层

### 1.1 架构风格与技术栈（不变项）

| 维度 | 选型 | 是否变更 | 理由 |
| ---- | ---- | ---- | ---- |
| 架构风格 | 模块化单体（单 Jar、单进程、单库） | 不变 | 目标规模（8 驿站 / 员工 < 5000 / parcel 20 万行）单实例 MySQL 8 完全可承载；微服务只增加运维与分布式一致性成本（`system-architecture` 反模式：一期不需要微服务） |
| 通信 | REST + Redis（会话/限频） | 不变 | 145 接口均为同步请求-响应；域间跨调用走进程内方法调用与领域事件（ADR-03） |
| 数据存储 | MySQL 8 单库（库名 `kdyzgl`） | 不变（postgresql 目录已冻结，不再维护） | 项目规则与 db.md 决策 D2 已收敛为单 MySQL |
| 部署 | 宝塔 + Nginx + 宿主 MySQL（端口 3307）+ `/data` 数据盘 | 见 1.4.8 / 第 8 章 | 用户明确要求：系统盘 30G 只放系统，业务数据全落 `/data` 50G |
| 前端 | Vue 3 SPA（`hrm-demo`）+ 安卓 H5 壳 | 不变（零改动） | 本次不触碰前端；Demo 经 `VITE_MOCK_ENABLED` 在 Mock 与真实后端间切换 |
| 认证 | 自研 JWT(HS256) + Redis 单会话 jti 互踢 | 不变（语义等价） | Mock 令牌格式 `mock.{employeeId}.{jti}` 是**客户端不透明**的，前端只发 `Authorization` 头，差异在服务端消化 |

### 1.2 新增领域的包结构（ADR-01）

现有 5 域为**扁平包**（`controller/EmployeeController` 等）。14 个新增领域若继续扁平，`controller` 包将达 ~19 个类、`dto`/`vo` 各 ~150 个类，不可导航。**决策：新增领域一律用域子包，并同步将现有 5 域迁入子包（纯包移动）**。

```text
com.qiujie
├─ controller/
│  ├─ auth | dashboard | employee | department | station      # 既有 24 接口（纯移动）
│  └─ attendance | kpi | hr | finance | leave
│     workorder | notification | sync | parcel | systemlog    # 新增
├─ service/
│  ├─ {domain}/            # 接口
│  ├─ {domain}/impl/       # 实现（后缀 Impl）
│  └─ support/             # 跨域公共支撑（分页基类、导入行校验等）
├─ dto/{domain}/           # 入参（含 PageQuery 基类）
├─ vo/{domain}/            # 出参
├─ mapper/                 # 扁平（与表 1:1，便于按表名定位；@MapperScan 已递归扫描）
├─ entity/                 # 扁平（同上）
├─ domain/event/           # 领域事件定义（ADR-03）
├─ config/ common/ util/ handler/ enums/ exception/ filter/ annotation/
└─ aspect/                 # 数据范围/@DataScope 切面（现为空 package-info）
```

**取舍理由：**

- `controller/service/dto/vo` 按域聚合（数量多、按业务检索）；`mapper/entity` 保持扁平（与表 1:1、按表名检索、MyBatis-Plus 约定）。
- 现有 5 域**纯包移动**（无逻辑变更、无运行时行为变更、不影响 Spring 扫描），风险仅在 import 编译期暴露；避免长期"双风格"（用户规则 §2 精简/统一）。
- 备选方案（未采纳）：全部保持扁平 → 类爆炸不可维护；全部按域拆分 mapper/entity → 30 个实体的包层级无检索收益。

### 1.3 请求处理链路

```text
HTTP → JwtAuthFilter（认证 + 单会话 jti 校验 + 注入 UserContext）
     → RequireRolesInterceptor（角色门槛，逐端点 roles，失败 403）
     → QueryDataScopeInterceptor（查询参数 stationId 静默收敛，非 ADMIN）
     → @DataScope 切面（路径资源跨站策略：SILENT / NOT_FOUND / FORBIDDEN）
     → Controller → Service（业务校验、领域规则、跨域调用/事件）→ Mapper
     → GlobalExceptionHandler（业务码；401/403/404 同步 HTTP 状态码）
```

过滤器职责不变（决策 D3/D10）；**新增能力全部落在拦截器/切面**，不侵入既有过滤链（降低回归面）。

### 1.4 横切关注点落地

#### 1.4.1 角色门槛（全场统一 403）

- 现状：仅 `@RequireAdmin`（类/方法级）+ `RequireAdminInterceptor`，**不支持逐端点三角色白名单**。
- 决策：新增 `RoleEnum{ADMIN, STATION_ADMIN, STAFF}` 与 `@RequireRoles({"ADMIN","STATION_ADMIN"})`；拦截器按注解值与 `UserContext.getRole()` 比对，不含则 **HTTP 403 + code 403**（与 Mock engine 的 `roles.includes` 行为一致）。
- 兼容：`@RequireAdmin` 保留并标注 `@Deprecated`，语义等价于 `@RequireRoles({"ADMIN"})`；既有 5 域可先不动，P0 内统一迁移（避免双注解长期并存）。
- 公开端点：Mock 中 `auth:false` 的路由（`/auth/login`、`/work-orders/auto-dispatch`）需加入 `JwtAuthFilter` 白名单，并**逐个安全评审**（见 8-3）。

#### 1.4.2 数据范围收敛（三层，ADR-02）

Mock 用 `applyDataScope` 在调用 handler 前统一把**非 ADMIN** 的查询参数 `stationId` 覆盖为本人 `station_id`（仅收敛 `stationId`，不收敛 `employeeId`）。服务端落地为三层，**不做"在每个 Service 里重复写"**：

| 层 | 覆盖对象 | 策略 | 实现位置 |
| ---- | ---- | ---- | ---- |
| L1 查询参数收敛 | 所有列表/统计端点的 query `stationId` | **静默**（强制覆盖为本人驿站；ADMIN 放行） | `QueryDataScopeInterceptor` + 统一 `PageQuery/Query` 基类 |
| L2 角色门槛 | 端点级 roles | **403** | `RequireRolesInterceptor`（1.4.1） |
| L3 路径资源归属 | `/xxx/{id}` 等路径参数指向的资源 | **逐端点声明**：`SILENT`（不适用）/`NOT_FOUND`(404)/`FORBIDDEN`(403) | `@DataScope(policy=...)` + 切面 + 资源归属校验服务 |

- L1 只收敛 `stationId`；"只看本人"端点（`/xxx/my`、`/notifications`）一律以 `UserContext.getUserId()` 为准，**不接受前端传 employeeId**（对齐 Mock 与 finance「越权红线」注释）。
- L3 的 403/404 分界见 1.4.2 下方实测表（源自逐文件核对，**不得统一**）。

**实测 403/404 分界（源自 Mock 逐文件核对）**

| 端点 | 跨站/越权返回 |
| ---- | ---- |
| `GET /parcels/{id}` | 404 |
| `GET /work-orders/{id}` | 404 |
| `GET /sync/configs/{stationId}` | 404 |
| `GET /sync-tasks/{id}`、`GET /sync-tasks/{id}/logs` | 404 |
| `POST /sync-tasks/{id}/trigger`、`POST /sync-tasks/{id}/retry` | **403** |
| `GET /kpi/scores/{employeeId}` | 403 |
| `GET /hr/profiles/{employeeId}`、`GET /hr/salary-structures/{employeeId}` | 403 |
| `GET /leave/{id}` 等（无权操作） | 业务码 9605（HTTP 200） |
| `PUT /work-orders/{id}/assign|status`、`POST /work-orders/{id}/transfer` | 业务码 8002/8003（HTTP 200） |
| 列表类（`/parcels`、`/work-orders`、`/sync-tasks`、`/kpi/scores`、`/leave/list`、`/sync/configs`、`/attendance/*`） | 静默收敛（无错误） |

> **同一模块内混用**（`/sync-tasks` 详情 404 而触发 403）是本方案最易被"顺手统一"破坏的契约点，已登记为 ADR-07 与回归清单项。

#### 1.4.3 分页

- 契约（Mock `validate.pageSizeInvalid` + `domain/pagination.js`）：`pageNum` 默认 1；`pageSize` 默认 10、**有效区间 [1,100]，越界或非数字 → HTTP 200 + code 400**（文案「每页条数须为 1-100」）。
- 现状：`EmployeeServiceImpl` 对 `pageNum/pageSize` **钳制**（`<1→默认`、`>100→100`），**不报 400**。
- 改造：新增 `PageQuery` 基类（`pageNum/pageSize` + Bean Validation `@Min(1) @Max(100)`，缺失走默认值），所有分页端点继承；统一返回 `{ total, pageNum, pageSize, list }`（现有 `PageResult` 结构已一致，**无需改结构**）。
- 越界页（`pageNum` 超过总页数）：**返回空 list 但保留 total**（对齐 Mock `paginate`，与 SQL `LIMIT` 行为一致）。

#### 1.4.4 脱敏

- 手机号：复用 `DesensitizeUtil.maskPhone`（口径已与 api.md 1.4 一致）。
- **新增**：`maskName`（保留首字，其余 `*`，用于 parcel 收件人 / 工单相关姓名）、`maskBankAccount`（保留末 4 位，用于 hr 银行卡）——对齐 Mock `domain/mask.js`。
- 适用范围：**所有 JSON 出参**；Excel 导出例外（保留完整手机号）。查询筛选不受影响（对完整值 `LIKE`）。
- 前端 `mask.js` 的 `TODO(扩展): 后端脱敏生效后删除前端兜底`：本方案不触碰前端，前端兜底可保留（幂等，不冲突）。

#### 1.4.5 Excel

- 复用现有 EasyExcel + `ImportTemplateStyleHandler`；导入统一「整批校验 + 行级错误一次性返回」（决策 D4），错误结构 `{ errors: ImportErrorVO[] }`。
- 新增导出端点：`GET /employees/export`（既有）、`GET /attendance/export`（新增，CSV/全量，不分页）。
- **临时目录硬约束**：EasyExcel/POI 使用 `java.io.tmpdir`，若指向系统盘会写满 `/`（30G）→ 部署时 JVM 必须 `-Djava.io.tmpdir=/data/hrm-tmp`，且 `spring.servlet.multipart.location=/data/hrm-tmp`（见 5.1、ADR-04、8-14）。

#### 1.4.6 错误码与异常

- `Result`/`PageResult` 结构**与契约完全一致，无需改造**（已核对 `common/Result.java`、`common/PageResult.java`）。
- `ErrorCode` 现有 200/400/401/403/404/500 + 10xx~50xx；**需扩展至 60xx~96xx**（分段总表见附录 B）。
- `GlobalExceptionHandler` 已实现「业务错误 HTTP 200 + 业务码；401/403/404 同步 HTTP 状态码」——**无需改造**，新增错误码直接复用。
- 一处差异：`@PathVariable Long id` 传入非数字时抛 `MethodArgumentTypeMismatchException` → **400**；Mock 用正则 `([^/]+)` 匹配后按"不存在"返回 **404**。前端不会发非数字 id，登记为低优先差异（8-8）。

#### 1.4.7 路由匹配顺序（核对结论）

任务列举的 7 组"动态/静态路径冲突"（`/employees/import-template` vs `/employees/{id}` 等）：**Spring MVC 默认无需手工排序**。Boot 3 使用 `PathPatternParser`，按**模式特异性**排序（字面量段 > 变量段），字面量端点优先级更高，与 Mock「先注册者优先」等价。因此：

| 冲突组 | Spring 处理 | 结论 |
| ---- | ---- | ---- |
| `/employees/import-template`、`/employees/export` vs `/employees/{id}` | 字面量优先 | 正确 |
| `/work-orders/dispatch-rules` vs `/work-orders/{id}` | 字面量优先 | 正确 |
| `/kpi/metrics/batch` vs `/kpi/metrics/{id}` | 字面量优先（方法同为 PUT） | 正确 |
| `/sync/configs/global|export|import` vs `/sync/configs/{stationId}` | 字面量优先 | 正确 |
| `/finance/payrolls/my|generate|submit|publish` vs `/finance/payrolls/{id}` | 字面量优先 | 正确 |
| `/notifications/unread-count|read-all|publish` vs `/notifications/{id}` | 字面量优先 | 正确 |
| `/leave/preview|my|list|settings` vs `/leave/{id}` | 字面量优先 | 正确 |

**约束**：① 不得为"兼容 Mock 顺序"而人为 `@Order` 或改成 `@RequestMapping` 通配；② 必须保留字面量与 `{id}` 的**不同 HTTP 方法**组合（如 `PUT /kpi/metrics/batch` 与 `PUT /kpi/metrics/{id}` 同方法不同路径，靠特异性区分）；③ P0 加一组**回归清单**（6.4）逐条断言字面量端点可达。

#### 1.4.8 部署与存储落位（纳入架构约束）

| 项 | 约束 |
| ---- | ---- |
| 库 | 新建 `kdyzgl`（与服务器既有 `courier_station` **并存互不影响**）；应用账号 `hrm_app` 仅授权 `kdyzgl`，**不授 DROP** |
| MySQL | 宿主实例，端口 **3307**，`datadir=/data/mysql-host`，错误日志 `/data/log/mysql/error.log`，临时目录 `/data/mysql-tmp`；容器 MySQL 已停用（保留回滚） |
| 数据盘 | 系统盘 30G（`/`）仅放 OS 与系统程序；**库文件、日志、上传、导出、备份全部落 `/data`（50G，可用约 46G）** |
| 应用临时目录 | `spring.servlet.multipart.location=/data/hrm-tmp` + JVM `-Djava.io.tmpdir=/data/hrm-tmp`（不得落 `/tmp` 或工作目录） |
| 应用日志 | `logging.file.path=/data/log/hrm`（外置生产配置覆盖） |
| 容量 | parcel 20 万行估算约 106MB（算法 S7），占 `/data` 不足 0.3%，容量充裕 |
| 迁移执行 | 属 **C 档**（结构变更），须主智能体三步授权后由运维执行 |

> 本文件**不记录任何服务器 IP、账号、口令、密钥**；上述均为路径/端口级约束。生产配置一律走服务器外置 `application-prod.yml`（600 权限，不入仓库）。

---

## 2. 模块边界与依赖关系

### 2.1 领域模块清单与职责

| # | 模块 | 职责（一句话） | 接口数 | 表 |
| - | ---- | ---- | ---- | ---- |
| M0 | auth/dashboard/employee/department/station | 组织与账号底座 + 看板（既有 24 接口） | 24 | 4 |
| M1 | systemlog | 前端运行日志摄取/查询/清理（S8 聚合） | 3 | client_log |
| M2 | notification | 站内信发布/查询/已读/未读计数（leave·工单·同步共用出口） | 6 | notification |
| M3 | attendance | 打卡规则/班次/排班/打卡记录/补卡（S3 排班、S4 异常、打卡限频） | 22 | 5 |
| M4 | kpi | 指标配置 + 月度评分 + 排名（S1） | 9 | 2 |
| M5 | hr | 人事档案/定薪/调薪留痕/入职·离职流程 | 16 | 5 |
| M6 | finance | 计薪规则与规则项/工资单/明细/六态流转（S2，依赖 M3·M4·M5） | 15 | 4 |
| M7 | leave | 请假申请/两级审批/撤回/计薪天数（S5，依赖 M3·M6·M2） | 13 | 3 |
| M8 | workorder | 工单流转/转派留痕/时间线/自动派单规则（S6，依赖 M2） | 9 | 4 |
| M9 | sync | 同步任务/日志 + 采集配置 + 配置中心四层（S7 限频相关，依赖 M2） | 22 | 7 |
| M10 | parcel | 包裹查询/统计/趋势/排行/取件（S7，20 万级大表） | 6 | 1 |

### 2.2 依赖方向（严格 DAG，无环）

```text
base(department/station/employee)
  ├── attendance ──► kpi ──┐
  ├── hr ──────────────────┼──► finance ──► leave ──┐
  ├── notification ◄────────┘  (事件回写/推送)      │
  │        ▲                                        │
  │        ├── workorder ────────────────────────────┤
  │        └── sync ─────────────────────────────────┤
  └── parcel / systemlog（独立，仅依赖 base）
```

- 依赖均**单向指向左侧/上游**；`finance → hr/attendance/kpi` 为同步只读；`leave → attendance/finance/notification`。
- **断环处理**（关键）：`hr` 的离职 `SETTLEMENT` 步骤需创建财务结算单，若 `hr → finance` 直调则与 `finance → hr` 成环 → **改用领域事件**（ADR-03）。
- **数据所有权**：每个业务表只有一个域可写；跨域读通过上游域暴露的**只读接口**（如 `LeaveDaysQuery`、`PayrollLockQuery`），禁止直连他域表建 Mapper。

### 2.3 跨域调用策略（ADR-03）

| 场景 | 策略 | 理由 |
| ---- | ---- | ---- |
| finance 算薪读 定薪/考勤/KPI | **服务层直调**（同步只读） | 同库同进程，无异步收益；单向依赖不产生环 |
| leave 审批后回写考勤 `leaveCount` | **服务层直调**（`attendance.recordLeaveOccupancy`） | 保证「已批请假」即时进入考勤口径；leave→attendance 单向 |
| 离职 `SETTLEMENT` 步骤 → 生成财务结算单 | **领域事件**（`OffboardingSettlementRequired`，`@TransactionalEventListener(AFTER_COMMIT)`） | 断开 `hr ↔ finance` 环；结算在离职流程事务提交后执行，失败可重试、不阻塞离职主流程 |
| leave 终审通过 / 工单指派·流转 / 同步任务失败 → 站内信 | **领域事件**（或服务层直调 `NotificationService`） | 通知为"副作用"，量级低；统一走 notification 出口，便于 S8 限频与聚合 |
| 多实例部署后的限频原子性 | 配置文件 + **Redis 令牌桶 Lua**（TODO-6） | 单实例用进程内令牌桶即可；多实例须原子 `refill+take` |

**不引入消息队列**：一期/上线规模（8 驿站、单实例）无异步解耦刚需，MQ 只增加运维与一致性成本；Spring `ApplicationEvent` 进程内事件已足够，且可平滑演进（未来换 MQ 只改发布/监听两端）。

### 2.4 数据所有权与共享表

- 业务表**单写者**：只有归属域可写。
- `employee` 为共享主体（多域引用），但**只有 M0 可写**；其他域只读。
- `notification` 只有 M2 可写，其他域仅通过 `NotificationService` 投递。
- `client_log` 只有 M1 可写（前端上报入口唯一）。

---

## 3. 与现有 24 接口的兼容性核对

### 3.1 核对矩阵

| 核对项 | 现有实现 | 前端契约（Mock 行为规格） | 是否一致 | 处置 |
| ---- | ---- | ---- | ---- | ---- |
| 角色模型 | 仅 `ADMIN`（`@RequireAdmin`），无逐端点白名单 | `ADMIN/STATION_ADMIN/STAFF` + 逐端点 `roles` 数组 + `ALL_ROLES` | **不一致** | C-01 |
| 用户上下文 | `LoginUser/SessionInfo` **无 `stationId`** | 数据范围收敛依赖本人 `station_id` | **不一致** | C-02 |
| Token 机制 | JWT(HS256) + Redis `hrm:session:{id}` 存 jti，登录覆盖=互踢；禁用/删除/改密 DEL 会话 | `mock.{id}.{jti}` + 覆盖式互踢；禁用即 401 | 语义等价 | 无需改（前端只发 token） |
| 登录/me 出参 | `LoginVO.employee` = `LoginEmployeeVO`（仅 id/username/realName/phone/role/pwdChanged）；`MeVO` 较全 | 登录 `employee` = 完整 `toEmployeeVO`（含 stationId/stationName/deptId/deptName/gender/status/entryDate/remark/lastLoginTime/createTime）；`/auth/me` 同构 | **不一致** | C-06 |
| 统一响应 | `Result={code,message,data}` | 同 | 一致 | 无 |
| 分页结构 | `PageResult={total,pageNum,pageSize,list}` | 同 | 一致 | 无 |
| 分页越界 | **钳制**（`<1→默认`、`>100→100`），不报错 | **400**（越界/非法，文案「每页条数须为 1-100」）；越界页返回空 list 保留 total | **不一致** | C-04 |
| 错误码 | 200/400/401/403/404/500 + 10xx~50xx | 另有 60xx~96xx（14 段） | **不完整** | C-05 |
| 数据范围收敛 | 无（既有 24 接口全 ADMIN，无需） | 非 ADMIN 查询参数 `stationId` 静默收敛 | **缺失** | C-03 |
| 越权口径 | 无 | 端点级 403/404/静默 三种混用 | **缺失** | C-03 |
| 脱敏 | 仅手机号 | 另需姓名、银行卡脱敏 | **不完整** | C-07 |
| 路由顺序 | 未显式处理 | Mock 靠注册顺序 | **无需改代码** | C-09（回归清单） |
| Excel 导入/导出 | 已具备（模板/导入/导出、行级错误） | 复用 | 一致 | 无 |
| 逻辑删除/时间填充 | `@TableLogic` + `MetaObjectHandler` | 一致 | 一致 | 无 |

### 3.2 改造点清单（P0 地基批次交付）

| ID | 改造点 | 改哪里 | 怎么改 | 风险 |
| ---- | ---- | ---- | ---- | ---- |
| C-01 | 三角色 + 逐端点白名单 | `enums/RoleEnum`（新）、`annotation/RequireRoles`（新）、`config/RequireAdminInterceptor`→`RequireRolesInterceptor`、`config/WebConfig` | 注解含 `String[] value()`；拦截器取方法级优先、类级其次，`UserContext.getRole()` 不在数组内→403；`@RequireAdmin` 迁移为 `@RequireRoles({"ADMIN"})` | 中：触及既有认证链，须全量回归既有 24 接口 |
| C-02 | 会话/上下文补 `stationId` | `common/SessionInfo`、`common/LoginUser`、`service/impl/AuthServiceImpl`（写会话）、`filter/JwtAuthFilter`（注入）、`util/UserContext`（新增 `getStationId()`） | 加 `private String stationId;`（String 避免 JSON 序号兼容问题，沿用类头注释约定）；登录时写本人 `station_id` | 中：**Redis 旧会话无该字段**→上线建议清 `hrm:session:*`（或非 ADMIN 缺归属时按"收敛到无数据"安全兜底，见 8-2） |
| C-03 | 数据范围收敛三层 | `interceptor/QueryDataScopeInterceptor`（新）、`annotation/DataScope`（新）、`aspect/DataScopeAspect`（新）、`service/support/ResourceAccessChecker` | L1 收敛 query `stationId`；L3 按 `policy` 执行 NOT_FOUND/FORBIDDEN | 中：策略逐端点声明，**不得默认统一** |
| C-04 | 统一分页校验 | `dto/support/PageQuery`（新，基类）、各分页 DTO 继承、`GlobalExceptionHandler`（复用现有） | `@Min(1) @Max(100)`；越界→400（HTTP 200 + code 400） | 低：既有 `GET /employees` 行为由"钳制"改"400"，前端不传越界值，影响可控 |
| C-05 | 错误码分段扩展 | `enums/ErrorCode` | 按附录 B 补 60xx~96xx（含 9601~9607 与 Mock 逐条一致） | 低：纯枚举新增 |
| C-06 | 登录/me 出参对齐 | `vo/LoginEmployeeVO`（扩展）、`service/impl/AuthServiceImpl` | 登录 `employee` 字段集扩至与 `toEmployeeVO` 同构（含 `stationId/stationName/deptId/deptName/gender/status/entryDate/remark/lastLoginTime/createTime`） | 低：**字段只增不减**，前端零改动（前端读 `employee.stationId`，见 3.3） |
| C-07 | 脱敏工具扩展 | `util/DesensitizeUtil` | 新增 `maskName`、`maskBankAccount`（对齐 Mock `mask.js`） | 低 |
| C-08 | 公开端点白名单 | `filter/JwtAuthFilter`（`LOGIN_PATH` 扩展） | 追加 `/api/v1/work-orders/auto-dispatch`；**企微回调签名校验为 TODO(扩展)，须安全评审** | 中：新增公网面，P0.5 安全评估前置（8-3） |
| C-09 | 路由顺序回归 | 无代码改动 | 新增回归清单（6.4 第 3 项）逐条断言字面量端点可达 | 低 |
| C-10 | 包结构统一 | `controller/service/dto/vo` 下新增域子包；既有 5 域迁入 | 纯移动，无逻辑变更 | 低（编译期暴露 import） |
| C-11 | 配置外置与临时目录 | `application.yml`、`config/AlgoProperties`（新）、部署脚本/JVM 参数 | 见第 5 章 | 中：涉部署（C 档）与磁盘落位 |
| C-12 | 迁移与快照同步流程 | `resources/db/migration/mysql/`、`hrm-dev/sql/schema/mysql/init.sql` | 批次版本号规划见 4.4；流程见 4.5 | 中：结构变更属 C 档 |
| C-13 | 领域事件基础设施 | `domain/event/`、`config/AsyncConfig`（如需） | Spring `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` | 低 |

### 3.3 对任务背景材料的事实性补充/纠正

| # | 任务材料表述 | 实测结论 | 影响 |
| - | ---- | ---- | ---- |
| F-1 | 「前端不做改动（前端只发 token），差异在服务端消化」 | 对 **token** 成立；但前端 **登录后读 `data.employee`，并读 `employee.stationId`**（`hrm-demo/src/mobile/stores/auth.js`），而现有 `LoginEmployeeVO` 无 `stationId` → 属**服务端需补字段**，不是纯 token 差异 | 纳入 C-06；若不补，站长/员工端拿不到归属驿站 |
| F-2 | 「另有 7xxx（包裹）、8xxx（工单）、9xxx（人事/财务）」 | 不精确。实际分段：**90xx=通知、91xx=考勤/排班/补卡、92xx=KPI、93xx=人事、94xx=财务、95xx=同步配置中心、96xx=请假**；且原契约把考勤码定为 9001~9006，与已被通知占用的 9001/9002 冲突，Demo 已**顺延至 91xx**（见 `errorCode.js` 注释） | 错误码总表以附录 B 为准（与 Mock 一致） |
| F-3 | 「多数模块刻意把非 ADMIN 越权当 404，而 KPI/hr/finance 用 403」 | 不完整。`/sync-tasks` **详情/日志=404，但触发/重试=403**；`/parcels/{id}`、`/work-orders/{id}`、`/sync/configs/{stationId}` 跨站=404 | 403/404 分界在**端点**级，禁止统一（ADR-07、1.4.2 实测表） |
| F-4 | 「第 16、17 模块 13+3=16 条已在 api.md 第 7 章定义」 | 正确。但 **api.md §7.1 与 Mock 在 `GET /leave/settings` 上角色不一致**：api.md 记「不限角色（—）」，Mock 限 `ADMIN` | 需用户裁定（8-4） |
| F-5 | 「请核对现有 Result/PageResult 是否一致」 | **结构完全一致，无需改造**；不一致的是**分页越界行为**（现有钳制 vs 契约 400） | 纳入 C-04 |

---

## 4. 数据库设计概览

> 基准：`db.md`（表结构唯一基准）+ 迁移脚本 + `hrm-dev/sql/schema/mysql/init.sql` 快照。以下为**顶层设计与索引策略**，DDL 由数据库工程师按 P4（结构先行）产出。

### 4.1 表清单（约 36 张）

| 域 | 表 | 用途 | 批次 |
| ---- | ---- | ---- | ---- |
| 既有 | `department`、`station`、`employee`、`login_log` | 组织/账号/登录日志 | 已有 |
| 系统 | `client_log` | 前端运行日志（白名单脱敏、`count/first_time/last_time` 聚合） | P1 |
| 通知 | `notification` | 站内信（`biz_type/biz_id` 跳转、发布范围、类型含 5/6=请假） | P2 |
| 考勤 | `attendance_rule`、`attendance_shift`、`attendance_schedule`、`attendance_record`、`attendance_makeup` | 打卡规则/班次/排班/打卡/补卡 | P3 |
| KPI | `kpi_metric`、`kpi_score` | 指标配置、月度评分快照 | P4 |
| 人事 | `hr_profile`、`hr_salary`、`hr_salary_log`、`hr_flow`、`hr_flow_step` | 档案/定薪/调薪留痕/入离职流程与步骤 | P5 |
| 财务 | `payroll_rule`、`payroll_rule_item`、`payroll`、`payroll_item` | 计薪规则与规则项/工资单/明细 | P6 |
| 请假 | `leave_request`、`leave_log`、`leave_setting` | 申请单/操作留痕/全局开关（`leaveDeductEnabled`） | P7 |
| 工单 | `work_order`、`work_order_transfer`、`work_order_timeline`、`work_order_dispatch_rule` | 工单/转派留痕/处理时间线/自动派单规则 | P8 |
| 同步 | `sync_task`、`sync_task_log`、`sync_station_config`、`sync_config_item`、`sync_config_option`、`sync_config_global`、`sync_config_station_override` | 同步任务/日志 + 采集配置 + 配置中心四层（配置项定义/选项集/全局默认/驿站覆盖） | P9 |
| 包裹 | `parcel`（20 万级大表） | 包裹数据 | P10 |

> `leave_request`/`leave_log`/`client_log` 的字段与索引**已在 db.md §7 定义**，本方案不新增语义，仅沿用（含 `counted_days_snapshot` JSON、`idx_leave_request_date` 等）。

### 4.2 关键表字段与索引策略

- **通用约定**（沿用 db.md 1.1/1.2/1.4）：`snake_case`、主键 `id`（`BIGINT AUTO_INCREMENT`）、`create_time/update_time`（应用层填充）、`is_deleted`（`@TableLogic`）、**不建物理外键**、**唯一性由 Service 层活跃查重 + 普通索引**。
- **代表性表**（其余由数据库工程师按同一规范产出）：

| 表 | 关键字段 | 索引 |
| ---- | ---- | ---- |
| `attendance_rule` | `station_id`、`rule_name`、`enable_wifi/location/time_window`、`match_mode`、`wifi_list`(JSON)、`longitude/latitude/radius`、`check_frequency`、`check_periods`(JSON)、`late_threshold_min`、`status` | `uk(idx) idx_attendance_rule_station (station_id)` |
| `attendance_schedule` | `station_id`、`employee_id`、`work_date`、`shift_id` | `idx_attendance_schedule_station_date (station_id, work_date)`、`idx_attendance_schedule_emp_date (employee_id, work_date)` |
| `attendance_record` | `station_id`、`employee_id`、`work_date`、`period_index`、`check_type`、`check_time`、`status` | `idx_attendance_record_emp_date (employee_id, work_date)`、`idx_attendance_record_station_date (station_id, work_date)` |
| `kpi_score` | `employee_id`、`month`、`total_score`、`level`、`metric_detail`(JSON 快照) | `idx_kpi_score_month (month)`、`idx_kpi_score_emp_month (employee_id, month)`（Service 查重保证活跃唯一） |
| `hr_salary_log` | `employee_id`、`change_type`、`basic_salary`、`post_salary`、`performance_base`、`allowance`(JSON)、`effective_date` | `idx_hr_salary_log_emp (employee_id, effective_date)` |
| `payroll` | `employee_id`、`month`、`bill_type`(MONTHLY/SETTLEMENT)、`status`、`total_amount`、`net_amount`、`rule_snapshot`(JSON) | `idx_payroll_emp_month (employee_id, month)`、`idx_payroll_month_status (month, status)` |
| `leave_request` | 见 db.md §7.1 | 见 db.md §7.1 |
| `work_order` | `order_no`、`type`、`status`、`priority`、`station_id`、`reporter_id`、`assignee_id`、`source`、`sla_deadline`、`resolved_time`、`closed_time` | `idx_work_order_station_status (station_id, status)`、`idx_work_order_assignee_status (assignee_id, status)`、`idx_work_order_sla (sla_deadline)` |
| `work_order_timeline` | `work_order_id`、`action`、`operator_id`、`content`、`time` | `idx_work_order_timeline_order (work_order_id, time)`（替代 Mock 的 `handle_log` JSON 内嵌，**须与 api.md 出参兼容**→Service 组装回 `handleLog[]`） |
| `sync_config_station_override` | `item_key`、`station_id`、`value`、`option_key`、`enabled` | `idx_sync_override_station_item (station_id, item_key)` |
| `parcel` | `station_id`、`waybill_no`、`status`、`inbound_time`、`pickup_time`、`pickup_employee_id`、`receiver_name`、`receiver_phone`、`shelf_no`、`batch_no` | 见 4.3 |

> **JSON 列使用边界**：仅用于"低频读取、结构多变"的字段（`wifi_list`、`check_periods`、`metric_detail`、`rule_snapshot`、`allowance`、`counted_days_snapshot`、`constraints`、`params`）。**高频筛选/排序/聚合字段一律显式列 + 索引**（禁止塞 JSON）。理由见 ADR-04。

### 4.3 `parcel` 大表策略（20 万级）

- **索引**（对齐 db.md §6.2 预告 + 算法 §13 追加建议）：

| 索引 | 列 | 用途 |
| ---- | ---- | ---- |
| `uk_parcel_station_waybill` | `(station_id, waybill_no)` | 防重复入库（Service 查重 + 普通唯一索引，见 4.6 唯一性取舍） |
| `idx_parcel_station_status` | `(station_id, status)` | db.md 预告：按驿站/状态筛选 |
| `idx_parcel_station_status_inbound` | `(station_id, status, inbound_time DESC)` | **算法 §13 追加建议**：列表默认排序 + 状态筛选一次走索引 |
| `idx_parcel_station_inbound` | `(station_id, inbound_time DESC)` | 趋势/近 N 天聚合 |

- **分页（ADR-06）**：默认 **游标分页** `(inbound_time, id)`（算法 S7：20 万级扫描行 38 vs 深分页 20020）；`OFFSET` 仅作兼容兜底并限制最大页深。首版 `hrm.algo.parcel.page.mode=CURSOR`，实测不达标则强制游标（TODO-1）。
- **查询纪律**：禁止 `SELECT *`；列表只取必要列；聚合走覆盖索引；`EXPLAIN` 必须 `type=range/ref` 且无 `Using filesort`（收敛到服务器复核，算法 §12.3）。
- **结构变更**：属大表变更，**须数据库工程师 + 算法工程师联评**（P4 + 规则 §6.8），加索引须附回滚脚本、优先 `ALGORITHM=INPLACE`（列类型须核实兼容）。

### 4.4 Flyway 版本规划（与批次 1:1）

> 已有 `V1__init_schema.sql`、`V2__init_data.sql`。**已执行脚本永不修改**；每批新增版本文件，批次号 < 版本号顺序一致（保证按批次上线时版本单调递增）。

| 批次 | 迁移文件 | 内容 |
| ---- | ---- | ---- |
| P0 | 无（无 DDL；如需为 `employee` 补索引则新增 `V3__employee_role_index.sql`，属 C 档） | 兼容性改造 |
| P1 | `V3__client_log.sql` | `client_log` |
| P2 | `V4__notification.sql` | `notification` |
| P3 | `V5__attendance.sql` | 考勤 5 表 |
| P4 | `V6__kpi.sql` | KPI 2 表 |
| P5 | `V7__hr.sql` | 人事 5 表 |
| P6 | `V8__payroll.sql` | 财务 4 表 |
| P7 | `V9__leave.sql` | 请假 3 表（与 db.md §7.1/7.2 一致） |
| P8 | `V10__work_order.sql` | 工单 4 表 |
| P9 | `V11__sync_task.sql`、`V12__sync_config_center.sql` | 同步 7 表（拆两文件：运行态 vs 配置元数据，单一职责） |
| P10 | `V13__parcel.sql` | `parcel` |

- 若上线顺序调整，**只允许追加更大版本号**，不得回填/复用号段。
- 版本文件命名统一 `V{n}__{snake_case_desc}.sql`；一文件一职责。

### 4.5 快照与迁移同步要求

- 仓库同时维护：`resources/db/migration/mysql/*`（迁移）与 `hrm-dev/sql/schema/mysql/init.sql`（全量快照）；**两者必须同一批次内同步更新**（规则 6.1）。
- 上线流程：服务器 `git pull` → 备份库 → 启动触发 Flyway 迁移 → 校验快照差异（部署手册 §3.4 导出比对）。
- `postgresql/` 目录**冻结不再维护**（保留不删，避免历史引用断裂；`spring.flyway.locations=classpath:db/migration/{vendor}` 自动选 mysql）。

### 4.6 面向频繁迭代的设计（ADR-04）

用户明确要求"数据库必须适应后期频繁迭代"，展开如下：

**(1) 迁移纪律（小步增量、单一职责、可回滚）**

- 小步：一批一文件或一域一文件，禁止"大而全"迁移；单文件只做一件事。
- 已执行脚本**永不修改**（Flyway checksum 会失败）；变更写新版本号。
- 大表（`parcel`）结构变更须数据库 + 算法联评；**索引变更必须附回滚脚本**（`DROP INDEX`）。

**(2) 结构变更原则：只加列不删列、不改列类型**

- 加列：MySQL 8 支持 `ALGORITHM=INSTANT`/`INPLACE`（在线，不阻塞读写），优先采用；新列**必须可空或带默认值**，避免大表重建。
- 删列：不直接删；先"停止写入 + 保留 N 个版本"，确认无引用后再由专门版本清理（避免回滚后数据不可恢复）。
- 改列类型/重命名：走 **expand-contract**（加新列 → 双写回填 → 切换读 → 停用旧列），禁止原地 `MODIFY` 大表列。
- 唯一性：沿用 Service 层活跃查重（决策 D7）。新增"活跃唯一"场景（如 `payroll (employee_id, month, bill_type)`、`kpi_score (employee_id, month)`、`parcel (station_id, waybill_no)`）统一**Service 查重 + 普通索引**；是否升级为"数据库唯一索引（配合 `is_deleted=id` 策略）"登记为开放问题（8-12）。

**(3) 预留扩展的取舍：JSON 列 vs 频繁加列**

| 维度 | JSON 列 | 显式加列 |
| ---- | ---- | ---- |
| 迭代成本 | 低（不改结构，无迁移） | 高（每次迁移，需部署窗口） |
| 索引能力 | 弱（需生成列 `STORED/VIRTUAL` 才能索引，维护成本高） | 强（直接建索引） |
| 查询/聚合 | 差（函数解析、无法有效走索引） | 好 |
| 可读性/约束 | 差（无类型约束，脏数据风险） | 好（DDL 约束） |
| **结论** | 仅用于**低频读取、结构多变**的扩展属性（如 `rule_snapshot`、`metric_detail`、`wifi_list`、`constraints`） | 用于**高频筛选/排序/聚合/关联**的核心业务字段 |

- 明确禁止：把核心业务字段（`status`、`station_id`、时间字段、金额字段）塞进 JSON。

**(4) 存储过程 / 触发器 / 复杂外键：结论 = 不使用**

- 存储过程/触发器：逻辑隐藏、难审计、难调试、与 Flyway 版本回滚冲突、影响复制与一致性，且应用层已能覆盖（时间戳由 `MetaObjectHandler` 填充，决策 D8）；**不使用**。
- 物理外键：沿用决策 D6 逻辑外键（Service 校验）。理由：逻辑删除（`is_deleted`）与物理外键语义冲突；分库/迁移时外键是最大阻力；本系统写入并发极低，Service 校验足够。
- 复杂度归位：所有业务规则留在 Service（可单测），数据库保持"哑存储"。

**(5) 磁盘落位与容量**

- 库文件/日志/临时目录全部落 `/data`（见 1.4.8）；`parcel` 20 万行约 106MB，占 `/data` 不足 0.3%。

---

## 5. 配置外置策略

### 5.1 `application.yml` 命名空间划分

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/kdyzgl?...        # 生产外置覆盖为宿主 3307
  servlet:
    multipart:
      max-file-size: 10MB
      location: /data/hrm-tmp                          # 新增：上传落盘显式指向数据盘
  flyway:
    locations: classpath:db/migration/{vendor}
  data:
    redis: { host: 127.0.0.1, port: 6379, database: 0 }
jwt:
  secret: change_me_jwt_secret_xxx                     # 生产外置覆盖
  expire: 86400
hrm:
  employee-init-password: change_me_init_password
  storage:
    upload-dir: /data/hrm-upload                       # 新增：业务上传目录（数据盘）
    export-dir: /data/hrm-export                       # 新增：导出临时目录（数据盘）
  algo:                                                # 新增：算法参数（见 5.2 / algo-hrm-server §11）
    kpi: { linearCapRatio: 1.0, tieredTiers: [...], levels: [...], quantile: { enabled: false, tiePolicy: MID_RANK, minSamples: 20 }, weightSumTarget: 100, weightSumTolerance: 0 }
    payroll: { attendanceFieldMap: {...}, defaultCapRatio: 1.0, unknownSourcePolicy: ZERO, allowNegativeNet: false, itemCapSemantics: ZERO_MEANS_NO_CAP }
    schedule: { minPerShift: 2, maxConsecutiveWork: 5, restCycleDays: 6, weights: {...}, sa: { iterations: 6000, initialTemp: 8, cooling: 0.9995 } }
    attendance: { anomaly: { useRobust: true, lateWarn: 3.5, lateCritical: 5.0, consecutiveAbsent: 3, minSamples: 5, windowDays: 30 } }
    leave: { maxLeaveDays: 30, occupiedStatus: [PENDING_STATION, PENDING_BOSS, APPROVED], lockStatusPolicy: NON_DRAFT_REJECTED }
    dispatch: { slaHours: {0: 48, 1: 24, 2: 8}, weights: {urgency: 4.0, skill: 2.5, load: 1.5, speed: 2.0}, keywordWeights: {...}, defaultType: 4, defaultPriority: 1 }
    parcel: { forecast: {...}, capacity: {...}, outlier: { iqrK: 1.5 }, page: { pageSize: 20, mode: CURSOR } }
    ratelimit: { bucketCapacity: 10, refillPerSecond: 5, maxQueueWaitSeconds: 30, dimension: BOT_AND_EMPLOYEE }
    log: { dedupeWindowSeconds: 10, textMax: 2000, sweepEvery: 2000, ringBufferCap: 200 }
logging:
  file: { path: /data/log/hrm }                        # 新增：日志落数据盘
```

- `hrm.algo.*` 通过 `@ConfigurationProperties(prefix="hrm.algo")` 绑定**强类型嵌套类**（`config/AlgoProperties`），禁止在代码内联阈值/权重（规则 §11.4、调度反模式 A03）。
- 所有键的**默认值**必须与 `algo-hrm-server.md` §11 逐键一致（保证"上线即无行为变化"）。

### 5.2 入库可热改 vs 配置文件（ADR-05）

| 类别 | 载体 | 是否热改 | 说明 |
| ---- | ---- | ---- | ---- |
| KPI 指标（权重/目标/评分规则/适用角色） | **`kpi_metric` 表** | 是（管理端） | 业务口径，运营可调；算分时**快照**进 `kpi_score.metric_detail` |
| 计薪规则与规则项（来源/参数/金额） | **`payroll_rule` / `payroll_rule_item`** | 是 | 表驱动来源解析器（算法 S2）；算薪快照进 `payroll.rule_snapshot` |
| SLA 阈值（按优先级/工单类型） | **入库（建议）**，默认值由配置种子写入 | 是 | 口径（Q4 待裁定）：裁定前用等价现状 48/24/8；裁定后按工单类型/响应·解决拆分 |
| 自动派单规则（关键词→类型/优先级） | **`work_order_dispatch_rule`** | 是 | 算法 S6 的 `keywordWeights` 等**超参**仍走配置 |
| 同步配置中心四层（配置项/选项/全局默认/驿站覆盖） | **`sync_config_item`/`sync_config_option`/`sync_config_global`/`sync_config_station_override`** | 是 | 用户可在管理端增删改并导入导出 |
| 打卡规则（时段/围栏/WiFi/阈值） | **`attendance_rule`** | 是 | 业务规则，站长/管理员可调 |
| 请假扣款开关 | **`leave_setting`** | 是 | 全局开关（`leaveDeductEnabled`，默认 false） |
| 算法超参（SA 迭代/温度、Holt-Winters 系数、异常 z 阈值、限频桶、分页模式/页大小、权重默认值） | **配置文件 `hrm.algo.*`** | 否（重启生效） | 性能/工程参数，变更走部署 |
| 存储路径、JWT、数据源、Redis | **外置 `application-prod.yml`** | 否 | 环境相关，绝不入仓库 |

**原则**：**业务口径/运营可调 → 入库（带审计与热改）；算法超参/性能/开关 → 配置文件（重启生效、走部署流程）**。

### 5.3 配置快照（历史可解释）

沿用 Mock `kpiStore` 的"配置快照"思路：算分/算薪结果**快照当时的配置**（`kpi_score.metric_detail`、`payroll.rule_snapshot`、`leave_request.counted_days_snapshot`），使"改了权重/规则后历史结果不回改"，保证可解释与可审计（算法 §1.2）。

---

## 6. 分批实施计划

### 6.1 批次总表

> 原则：**先地基 → 核心域 → 扩展域 → 大表域（parcel）最后**；每批可独立交付、独立验收。
> 依赖列 = 该批开工前必须已完成的前置批次（同时满足 2.2 DAG）。

| 批次 | 范围（模块/接口数） | 依赖前置 | 风险 | 预估改动文件数（后端） |
| ---- | ---- | ---- | ---- | ---- |
| **P0 地基** | 兼容性改造（C-01~C-11：角色模型/数据范围/分页/错误码/脱敏/公开端点/配置/包结构）。**不新增接口** | 无 | **中** | ~20 |
| **P1 运行日志** | systemlog（3） | P0 | 低 | ~10 |
| **P2 通知** | notification（6） | P0 | 低 | ~12 |
| **P3 考勤与排班** | attendance（22），算法 S3/S4 | P0 | 中高 | ~45 |
| **P4 KPI 考核** | kpi（9），算法 S1 | P0、P3（考勤指标来源） | 中 | ~22 |
| **P5 人事** | hr（16） | P0 | 中 | ~35 |
| **P6 财务** | finance（15），算法 S2 | P0、P3、P4、P5 | 高（口径依赖） | ~30 |
| **P7 请假** | leave（13），算法 S5 | P0、P2、P3、P6 | 中 | ~22 |
| **P8 工单** | workorder（9），算法 S6 | P0、P2 | 中 | ~25 |
| **P9 同步与配置中心** | syncTask(5)+syncConfig(4)+syncConfigCenter(13)=22 | P0、P2 | 中高（配置中心四层） | ~50 |
| **P10 包裹（大表域）** | parcel（6），算法 S7 | P0 | 中（性能） | ~18 |

> 累计：24（既有）+ 3+6+22+9+16+15+13+9+22+6 = **145**。

### 6.2 各批次详述

#### P0 地基（兼容性改造 + 横切能力）

- **范围**：C-01~C-11（角色模型、会话/上下文补 `stationId`、数据范围三层、统一分页、错误码扩展、登录出参对齐、脱敏扩展、公开端点白名单、包结构统一、配置外置）。
- **依赖前置**：无。
- **验收标准（可静态审查）**：
  1. `RoleEnum`/`@RequireRoles`/`RequireRolesInterceptor` 存在；三角色判定逻辑可读；既有 24 接口的 `@RequireAdmin` 已等价迁移为 `@RequireRoles({"ADMIN"})`，**无行为变化**。
  2. `SessionInfo`/`LoginUser` 含 `stationId`；`AuthServiceImpl` 登录写会话、`JwtAuthFilter` 注入、`UserContext.getStationId()` 可读。
  3. `QueryDataScopeInterceptor` 对非 ADMIN 覆盖 query `stationId`；`@DataScope(policy)` 支持 `SILENT/NOT_FOUND/FORBIDDEN` 三种策略并被切面消费。
  4. `PageQuery` 基类含 `@Min(1) @Max(100)`，越界映射 400（HTTP 200 + code 400）。
  5. `ErrorCode` 覆盖附录 B 全部段位（逐条与 Mock `CODE_MESSAGE` 一致）。
  6. `LoginEmployeeVO` 字段集 ⊇ 契约 `toEmployeeVO`（含 `stationId/stationName`）。
  7. `DesensitizeUtil` 含 `maskName`/`maskBankAccount`。
  8. `JwtAuthFilter` 白名单含 `/api/v1/auth/login` 与 `/api/v1/work-orders/auto-dispatch`。
  9. `application.yml` 含 `hrm.algo.*`（逐键与 algo §11 一致）与 `spring.servlet.multipart.location`。
  10. 包结构迁移后**无编译期残留 import**（静态检索 `com.qiujie.controller.<旧类名>` 无遗漏）；重构后类名/路径与 6.3 映射一致。
- **收敛到服务器阶段**：编译、启动、既有 24 接口冒烟、`hrm.algo` 属性绑定校验。

#### P1 运行日志（systemlog，3 接口）

- **范围**：`POST /system/client-logs`（白名单脱敏入库 + S8 指纹去重）、`GET /system/client-logs`（分页 + `counts`）、`POST /system/client-logs/clear`（ADMIN）。
- **依赖前置**：P0（错误码、分页）。**表**：`client_log`（db.md §7.3 已定义）。
- **验收**：① 入库字段白名单 = api.md §7.5（未列出字段丢弃：token/密码/身份证/手机号全量/银行卡/请求响应体原文**一律不入库**）；② `(message+route+code)` 10 秒窗口内重复只累加 `count`（`hrm.algo.log.dedupeWindowSeconds`）；③ 单字段超 `textMax` 截断；④ 查询支持 `level/source/keyword/startTime/endTime/employeeId` + 分页 + `counts`；⑤ clear 仅 ADMIN（非 ADMIN → 403）。
- **风险**：低。

#### P2 通知（notification，6 接口）

- **范围**：`GET /notifications`（本人）、`unread-count`、`{id}`、`read-all`、`{id}/read`、`POST /publish`（ADMIN）。
- **依赖前置**：P0。**表**：`notification`。被 P7/P8/P9 依赖，**须先交付**。
- **验收**：① 列表/计数/详情一律以 `UserContext.getUserId()` 收口，**不接受前端 employeeId**；② 发布范围 `ALL/STATION/EMPLOYEE` 校验（非法 → 9002）；③ `type` 校验放行 1~6（含请假 5/6）；④ 发布类型 `is_published=1`、系统联动 `=0`；⑤ 路由顺序：`unread-count`/`read-all`/`publish` 字面量优先于 `{id}`。
- **风险**：低。

#### P3 考勤与排班（attendance，22 接口）

- **范围**：规则（`rule`/`rule/list`/PUT rule）、状态、记录（`records`/`export`/`summary`/`detail`/`my`）、补卡（`makeup/my`/`makeup/list`/POST makeup/`makeup/{id}/approve`）、打卡（`check-in`）、排班（`schedules`/`schedules/my`/`schedules/batch`/`schedules/batch-by-station`）、班次（`shifts` CRUD）。算法 S3（排班）、S4（异常检测）。
- **依赖前置**：P0。**表**：考勤 5 表。
- **验收**：① 角色门槛逐条与 Mock `roles` 一致；② `records/summary/detail/schedules` 非 ADMIN 静默收敛到本人驿站；③ 打卡判定链完整：规则未配 9101 / 时间窗 9102 / WiFi 9103 / 定位 9104 / 重复 9105 / 班次停用 9106 / 时段越界 9107；④ 跨零点班次与半天时段模型（`check_frequency` + `check_periods` 为唯一真源，`workStartTime/workEndTime` 为派生）；⑤ 排班算法参数全部外置 `hrm.algo.schedule.*`，失败降级返回贪心解 + 违规清单（**不失败**）；⑥ 异常检测降级：样本不足或 MAD=0 → 空集 + 回落静态阈值。
- **风险**：中高（面最大、状态机与时段模型复杂）。

#### P4 KPI（9 接口）

- **范围**：`kpi/metrics`（list/create/PUT batch/PUT {id}/DELETE {id}）、`scores/calculate`、`scores/ranking`、`scores`、`scores/{employeeId}`。算法 S1。
- **依赖前置**：P0、P3（出勤打卡指标数据来源）。**表**：`kpi_metric`、`kpi_score`。
- **验收**：① 权重合计校验（启用指标合计须 100%，容差 `weightSumTolerance`）→ 9202；② 无有效指标 → 9203（列表空，**不报错**，降级）；③ 算分按适用角色权重归一；④ 算分快照配置进 `metric_detail`；⑤ 越权：`scores/{employeeId}` 他人/跨站 → **403**；`scores/ranking`/`scores` 非 ADMIN 静默收敛；⑥ 等级阈值/分位模式外置（Q1/Q2 未裁定前用现状默认 90/80/70、`quantile.enabled=false`）；⑦ 路由顺序：`metrics/batch` 字面量优先于 `metrics/{id}`。
- **风险**：中。

#### P5 人事（hr，16 接口）

- **范围**：档案（`profiles`/`profiles/{employeeId}` GET/PUT）、定薪（`salary-structures`/`{employeeId}` GET/PUT）、入职（`onboarding` list/create、`{id}`、`{id}/steps/{key}/complete`、`{id}/reject`）、离职（`offboarding` list/create、`{id}`、`{id}/steps/{key}/complete`、`{id}/reject`）。
- **依赖前置**：P0。**表**：`hr_profile`、`hr_salary`、`hr_salary_log`、`hr_flow`、`hr_flow_step`。
- **验收**：① 入职步骤序与离职步骤序与 Mock 常量逐条一致（`ONBOARDING_STEPS`/`OFFBOARDING_STEPS`）；② 离职判定以 `hr_profile.leave_date`（非 `employee.status`），对齐 Mock 注释；③ 越权：`profiles/{employeeId}`、`salary-structures/{employeeId}` 他人/跨站 → **403**；④ 状态非法 → 9303/9304；员工已离职 → 9302；薪资档案缺失 → 9305；⑤ **离职 `SETTLEMENT` 步骤完成 → 发布领域事件创建财务结算单**（ADR-03），结算未完成不可离岗（9306）；⑥ 调薪留痕每次变更一条，当前定薪 = 最后一条。
- **风险**：中（流程步骤机 + 与财务的断环编排）。

#### P6 财务（finance，15 接口）

- **范围**：计薪规则（`payroll-rules` CRUD）、`payrolls/my`、`payrolls/generate|submit|publish`、`payrolls`、`payrolls/{id}`、`{id}/items`、`{id}/approve`、`{id}/confirm`、`{id}/objection`。算法 S2。
- **依赖前置**：P0、P3（考勤）、P4（KPI）、P5（定薪）。**表**：财务 4 表。
- **验收**：① **来源解析器注册表**表驱动（FIXED/ATTENDANCE/KPI/MANUAL），新增来源不改核心代码；② 六态流转矩阵与 Mock `PAYROLL_ACTIONS` 一致；③ 幂等：同月同类型重复 `generate` → 9405；④ 员工可见状态仅 `PUBLISHED/CONFIRMED`，他人工资单 → 9404/403；⑤ 未发布不得被本人看到；⑥ 未知 `source`/定薪缺失 → 按 0 计并写解释，**不中断整批**；⑦ 快照 `rule_snapshot`；⑧ 路由顺序：`payrolls/my|generate|submit|publish` 字面量优先于 `payrolls/{id}`。
- **风险**：高（口径依赖 Q3 未裁定；依赖面最广）。

#### P7 请假（leave，13 接口）

- **范围**：`POST /leave`、`preview`、`my`、`list`、`settings` GET/PUT、`{id}` GET/PUT、`{id}/cancel|resubmit|station-approve|final-approve|revoke`。算法 S5。
- **依赖前置**：P0、P2（通知）、P3（排班算计薪天数）、P6（账期锁）。**表**：请假 3 表（`leave_request`/`leave_log` 与 db.md §7.1/7.2 一致）。
- **验收**：① 状态机 6 态 + `status=PENDING` 聚合虚拟值（`PENDING_STATION`+`PENDING_BOSS`）；② 半天粒度（`start/end_period`）与区间相交判定走 `idx_leave_request_date`；③ 单次上限 30 自然日、只能选今天或未来；④ 错误码 9601~9607 逐条一致；⑤ 越权：跨站/审自己/ADMIN 提交 → 9605；⑥ 账期锁：该月工资单非 DRAFT/REJECTED → 9606；⑦ 终审通过**回写考勤 `leaveCount`**（leave→attendance 直调）；⑧ 审批/终审/撤回触发通知（type 5/6、`biz_type=leave`）。
- **风险**：中（两级审批分槽 + 与财务/考勤的联动）。

#### P8 工单（workorder，9 接口）

- **范围**：`work-orders`（list/create）、`dispatch-rules`（list/PUT {id}）、`auto-dispatch`（公开）、`{id}`、`{id}/assign`、`{id}/status`、`{id}/transfer`。算法 S6。
- **依赖前置**：P0、P2。**表**：工单 4 表。
- **验收**：① `{id}` 跨站 → **404**；`assign/status` 无权 → 业务码 8002、`transfer` → 8003（HTTP 200）；② 状态流转非法 → 8001；规则不存在 → 8005；群消息空/不可解析 → 8006；③ 时间线 `work_order_timeline` 组装回 `handleLog[]`（出参与 Mock 一致）；④ 转派留痕含跨站/站内/本人三类权限形态；⑤ **SLA 阈值外置**（Q4 未裁定前用 48/24/8 等价现状）；⑥ 派单降级：无候选 → `assignee_id=null` 转人工，工单照常创建；⑦ `auto-dispatch` 公开端点：以 `TODO(扩展)` 标注企微签名/解密，**须安全评审后方可放行**。
- **风险**：中（公开端点安全 + 派单算法衔接）。

#### P9 同步任务与配置中心（sync，22 接口）

- **范围**：`sync-tasks`（list、`{id}/logs`、`{id}`、`{id}/trigger`、`{id}/retry`）；`sync/config-items`（list/create/PUT/DELETE + `impact` + options CRUD + `impact`）= 13；`sync/overview`、`sync/configs`、`sync/configs/{stationId}` GET、`{stationId}` PUT。
- **依赖前置**：P0、P2。**表**：同步 7 表（拆 `V11`/`V12`）。
- **验收**：① **越权分界逐端点**：`{id}`/`{id}/logs` 跨站 → **404**；`{id}/trigger`/`{id}/retry` 跨站 → **403**；`configs/{stationId}` GET 跨站 → **404**；列表静默收敛；② 配置中心四层（定义/选项集/全局默认/驿站覆盖）读写与派生"生效值/来源"视图；③ 错误码 6001/6002 + 95xx（9501~9510）逐条一致；④ 内置项/选项不可删只能停用（9510）；被引用删除需 confirm（9503/9505）；⑤ CSV 导入导出（`GET /sync/configs/export`、`POST /import`）冲突策略参数校验（9509）；⑥ 路由顺序：`configs/global|export|import` 字面量优先于 `configs/{stationId}`，且**配置中心路由整体先于 `syncConfig`**（对齐 Mock index.js 注释）；⑦ `retry` 状态机校验（6001）。
- **风险**：中高（配置中心四层 + 导入导出 + 端点级越权差异）。

#### P10 包裹（parcel，6 接口，大表域最后）

- **范围**：`parcels`（list）、`summary`、`trend`、`ranking`、`{id}`、`{id}/pickup`。算法 S7。
- **依赖前置**：P0。**表**：`parcel`（唯一，20 万级）。
- **验收**：① 分页默认**游标**（`hrm.algo.parcel.page.mode=CURSOR`），深分页有上限；② 索引命中 `idx_parcel_station_status_inbound`（`type=range/ref`、无 `Using filesort`）——**收敛服务器实测**；③ `{id}` 跨站 → **404**；④ 取件状态机：非可取状态 → 7002、已被他人取件 → 7003、运单号不存在 → 7001；⑤ 趋势预测降级：历史点 < 2×季节周期 → 回落 `MA(7)` → 再不足回落近 7 日均值；⑥ 容量/热力阈值外置（`utilWarn`/`utilCritical`）；⑦ 空态：真实采集前 parcel 为空时，summary/trend/ranking 返回空/零（不报错）；⑧ 收件人姓名/手机号脱敏出参。
- **风险**：中（性能关口，但接口逻辑简单）。

### 6.3 接口 → 批次 → 角色 → 数据范围 全量映射（145 条）

> `roles` 列为 Mock 实测；"数据范围"列：`静默`=查询收敛、`本人`=以登录身份为准、`404`/`403`=路径资源越权策略、`业务码`=HTTP 200 + 业务码、`—`=不适用。
> 批次列：既有 = 现有 24 接口。

#### M0 既有（24）

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 1 | POST `/auth/login` | 公开 | — | 既有 |
| 2 | POST `/auth/logout` | ALL | — | 既有 |
| 3 | GET `/auth/me` | ALL | — | 既有 |
| 4 | PUT `/auth/password` | ALL | — | 既有 |
| 5 | GET `/dashboard/summary` | ADMIN | — | 既有 |
| 6-15 | `/employees` 10 条 | ADMIN | — | 既有 |
| 16-19 | `/departments` 4 条 | ADMIN | — | 既有 |
| 20-24 | `/stations` 5 条 | ADMIN | — | 既有 |

#### M1 systemlog（3）— P1

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 25 | POST `/system/client-logs` | ALL | 本人 | P1 |
| 26 | GET `/system/client-logs` | ADMIN | — | P1 |
| 27 | POST `/system/client-logs/clear` | ADMIN | — | P1 |

#### M2 notification（6）— P2

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 28 | GET `/notifications` | ALL | 本人 | P2 |
| 29 | GET `/notifications/unread-count` | ALL | 本人 | P2 |
| 30 | GET `/notifications/{id}` | ALL | 本人 | P2 |
| 31 | PUT `/notifications/read-all` | ALL | 本人 | P2 |
| 32 | POST `/notifications/publish` | ADMIN | — | P2 |
| 33 | PUT `/notifications/{id}/read` | ALL | 本人 | P2 |

#### M3 attendance（22）— P3

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 34 | GET `/attendance/rule` | ALL | 静默 | P3 |
| 35 | GET `/attendance/rule/list` | ADMIN | — | P3 |
| 36 | PUT `/attendance/rule` | ADMIN | — | P3 |
| 37 | GET `/attendance/status` | ALL | 本人 | P3 |
| 38 | GET `/attendance/records` | ADMIN,STATION_ADMIN | 静默 | P3 |
| 39 | GET `/attendance/export` | ADMIN,STATION_ADMIN | 静默 | P3 |
| 40 | GET `/attendance/summary` | ADMIN,STATION_ADMIN | 静默 | P3 |
| 41 | GET `/attendance/detail` | ADMIN,STATION_ADMIN | 静默 | P3 |
| 42 | GET `/attendance/my` | ALL | 本人 | P3 |
| 43 | GET `/attendance/makeup/my` | ALL | 本人 | P3 |
| 44 | GET `/attendance/makeup/list` | ADMIN | — | P3 |
| 45 | POST `/attendance/makeup` | ALL | 本人 | P3 |
| 46 | POST `/attendance/makeup/{id}/approve` | ADMIN | 业务码 | P3 |
| 47 | POST `/attendance/check-in` | ALL | 本人 | P3 |
| 48 | GET `/schedules` | ADMIN,STATION_ADMIN | 静默 | P3 |
| 49 | GET `/schedules/my` | ALL | 本人 | P3 |
| 50 | POST `/schedules/batch` | ADMIN | — | P3 |
| 51 | POST `/schedules/batch-by-station` | ADMIN | — | P3 |
| 52 | GET `/shifts` | ALL | 静默 | P3 |
| 53 | POST `/shifts` | ADMIN | — | P3 |
| 54 | PUT `/shifts/{id}` | ADMIN | — | P3 |
| 55 | DELETE `/shifts/{id}` | ADMIN | — | P3 |

#### M4 kpi（9）— P4

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 56 | GET `/kpi/metrics` | ADMIN | — | P4 |
| 57 | POST `/kpi/metrics` | ADMIN | — | P4 |
| 58 | PUT `/kpi/metrics/batch` | ADMIN | — | P4 |
| 59 | PUT `/kpi/metrics/{id}` | ADMIN | — | P4 |
| 60 | DELETE `/kpi/metrics/{id}` | ADMIN | — | P4 |
| 61 | POST `/kpi/scores/calculate` | ADMIN | — | P4 |
| 62 | GET `/kpi/scores/ranking` | ADMIN,STATION_ADMIN | 静默 | P4 |
| 63 | GET `/kpi/scores` | ADMIN,STATION_ADMIN | 静默 | P4 |
| 64 | GET `/kpi/scores/{employeeId}` | ALL | **403** | P4 |

#### M5 hr（16）— P5

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 65 | GET `/hr/profiles` | ADMIN | — | P5 |
| 66 | GET `/hr/profiles/{employeeId}` | ALL | **403** | P5 |
| 67 | PUT `/hr/profiles/{employeeId}` | ADMIN | — | P5 |
| 68 | GET `/hr/salary-structures` | ADMIN | — | P5 |
| 69 | GET `/hr/salary-structures/{employeeId}` | ALL | **403** | P5 |
| 70 | PUT `/hr/salary-structures/{employeeId}` | ADMIN | — | P5 |
| 71 | GET `/hr/onboarding` | ADMIN | — | P5 |
| 72 | POST `/hr/onboarding` | ADMIN | — | P5 |
| 73 | GET `/hr/onboarding/{id}` | ADMIN | — | P5 |
| 74 | POST `/hr/onboarding/{id}/steps/{key}/complete` | ADMIN | — | P5 |
| 75 | POST `/hr/onboarding/{id}/reject` | ADMIN | — | P5 |
| 76 | GET `/hr/offboarding` | ADMIN | — | P5 |
| 77 | POST `/hr/offboarding` | ADMIN | — | P5 |
| 78 | GET `/hr/offboarding/{id}` | ADMIN | — | P5 |
| 79 | POST `/hr/offboarding/{id}/steps/{key}/complete` | ADMIN | — | P5 |
| 80 | POST `/hr/offboarding/{id}/reject` | ADMIN | — | P5 |

#### M6 finance（15）— P6

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 81 | GET `/finance/payroll-rules` | ADMIN | — | P6 |
| 82 | POST `/finance/payroll-rules` | ADMIN | — | P6 |
| 83 | GET `/finance/payroll-rules/{id}` | ADMIN | — | P6 |
| 84 | PUT `/finance/payroll-rules/{id}` | ADMIN | — | P6 |
| 85 | DELETE `/finance/payroll-rules/{id}` | ADMIN | — | P6 |
| 86 | GET `/finance/payrolls/my` | ALL | 本人 | P6 |
| 87 | POST `/finance/payrolls/generate` | ADMIN | — | P6 |
| 88 | POST `/finance/payrolls/submit` | ADMIN | — | P6 |
| 89 | POST `/finance/payrolls/publish` | ADMIN | — | P6 |
| 90 | GET `/finance/payrolls` | ADMIN | — | P6 |
| 91 | GET `/finance/payrolls/{id}` | ALL | **403**（他人） | P6 |
| 92 | PUT `/finance/payrolls/{id}/items` | ADMIN | — | P6 |
| 93 | POST `/finance/payrolls/{id}/approve` | ADMIN | — | P6 |
| 94 | POST `/finance/payrolls/{id}/confirm` | ALL | 本人 | P6 |
| 95 | POST `/finance/payrolls/{id}/objection` | ALL | 本人 | P6 |

#### M7 leave（13）— P7

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 96 | POST `/leave` | ALL | — | P7 |
| 97 | POST `/leave/preview` | ALL | — | P7 |
| 98 | GET `/leave/my` | ALL | 本人 | P7 |
| 99 | GET `/leave/list` | ADMIN,STATION_ADMIN | 静默 | P7 |
| 100 | GET `/leave/settings` | **ADMIN（Mock）/ 待裁定（api.md）** | — | P7 |
| 101 | PUT `/leave/settings` | ADMIN | — | P7 |
| 102 | GET `/leave/{id}` | ALL | 业务码 9605 | P7 |
| 103 | PUT `/leave/{id}` | ALL | 业务码 9605/9607 | P7 |
| 104 | POST `/leave/{id}/cancel` | ALL | 业务码 9605 | P7 |
| 105 | POST `/leave/{id}/resubmit` | ALL | 业务码 9605 | P7 |
| 106 | POST `/leave/{id}/station-approve` | STATION_ADMIN | 业务码 9605 | P7 |
| 107 | POST `/leave/{id}/final-approve` | ADMIN | 业务码 9605 | P7 |
| 108 | POST `/leave/{id}/revoke` | ADMIN | 业务码 9606 | P7 |

#### M8 workorder（9）— P8

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 109 | GET `/work-orders` | ALL | 静默 | P8 |
| 110 | POST `/work-orders` | ALL | — | P8 |
| 111 | GET `/work-orders/dispatch-rules` | ADMIN | — | P8 |
| 112 | PUT `/work-orders/dispatch-rules/{id}` | ADMIN | — | P8 |
| 113 | POST `/work-orders/auto-dispatch` | **公开（auth:false）** | — | P8 |
| 114 | GET `/work-orders/{id}` | ALL | **404** | P8 |
| 115 | PUT `/work-orders/{id}/assign` | ALL | 业务码 8002 | P8 |
| 116 | PUT `/work-orders/{id}/status` | ALL | 业务码 8002 | P8 |
| 117 | POST `/work-orders/{id}/transfer` | ALL | 业务码 8003 | P8 |

#### M9 sync（22）— P9

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 118 | GET `/sync-tasks` | ADMIN,STATION_ADMIN | 静默 | P9 |
| 119 | GET `/sync-tasks/{id}/logs` | ADMIN,STATION_ADMIN | **404** | P9 |
| 120 | GET `/sync-tasks/{id}` | ADMIN,STATION_ADMIN | **404** | P9 |
| 121 | POST `/sync-tasks/{id}/trigger` | ADMIN,STATION_ADMIN | **403** | P9 |
| 122 | POST `/sync-tasks/{id}/retry` | ADMIN,STATION_ADMIN | **403** | P9 |
| 123 | GET `/sync/config-items` | ADMIN | — | P9 |
| 124 | POST `/sync/config-items` | ADMIN | — | P9 |
| 125 | PUT `/sync/config-items/{itemKey}` | ADMIN | — | P9 |
| 126 | DELETE `/sync/config-items/{itemKey}` | ADMIN | — | P9 |
| 127 | GET `/sync/config-items/{itemKey}/impact` | ADMIN | — | P9 |
| 128 | POST `/sync/config-items/{itemKey}/options` | ADMIN | — | P9 |
| 129 | PUT `/sync/config-items/{itemKey}/options/{optionKey}` | ADMIN | — | P9 |
| 130 | DELETE `/sync/config-items/{itemKey}/options/{optionKey}` | ADMIN | — | P9 |
| 131 | GET `/sync/config-items/{itemKey}/options/{optionKey}/impact` | ADMIN | — | P9 |
| 132 | GET `/sync/configs/global` | ADMIN | — | P9 |
| 133 | PUT `/sync/configs/global` | ADMIN | — | P9 |
| 134 | GET `/sync/configs/export` | ADMIN | — | P9 |
| 135 | POST `/sync/configs/import` | ADMIN | — | P9 |
| 136 | GET `/sync/overview` | ADMIN,STATION_ADMIN | 静默 | P9 |
| 137 | GET `/sync/configs` | ADMIN,STATION_ADMIN | 静默 | P9 |
| 138 | GET `/sync/configs/{stationId}` | ADMIN,STATION_ADMIN | **404** | P9 |
| 139 | PUT `/sync/configs/{stationId}` | ADMIN | — | P9 |

#### M10 parcel（6）— P10

| # | 方法 路径 | roles | 数据范围 | 批次 |
| - | ---- | ---- | ---- | ---- |
| 140 | GET `/parcels` | ALL | 静默 | P10 |
| 141 | GET `/parcels/summary` | ALL | 静默 | P10 |
| 142 | GET `/parcels/trend` | ALL | 静默 | P10 |
| 143 | GET `/parcels/ranking` | ALL | 静默 | P10 |
| 144 | GET `/parcels/{id}` | ALL | **404** | P10 |
| 145 | PUT `/parcels/{id}/pickup` | ALL | 业务码 7002/7003 | P10 |

> 合计 24 + 121 = **145**。

### 6.4 验收与收敛说明

**统一验收方式（本机无 JDK/Maven/MySQL/Redis）：**

1. **可静态审查条款**：接口清单逐条对照 Mock 的 `method+path+roles`；分层文件齐备；参数校验/错误码文案与 Mock `CODE_MESSAGE` 一致；迁移脚本号段与 4.4 一致、未改历史脚本；`hrm.algo.*` 键与 algo §11 逐键一致。
2. **单测（可在服务器阶段跑）**：纯函数（`itemScore`/`resolveItem`/`countedDaysByRange`/`scoreAssign`/游标翻页/令牌桶/指纹聚合）用固定种子容差断言（金额 ±0.01、分数 ±0.1），对齐 algo §1.4 的等价性要求。
3. **路由顺序回归清单（P0 即建立，后续批次追加）**：逐条断言字面量端点可达且不被 `{id}` 吞掉——
   `/employees/import-template`、`/employees/export`、`/work-orders/dispatch-rules`、`/work-orders/auto-dispatch`、`/kpi/metrics/batch`、`/sync/config-items/{itemKey}/impact`、`/sync/configs/global|export|import`、`/finance/payrolls/my|generate|submit|publish`、`/notifications/unread-count|read-all|publish`、`/leave/preview|my|list|settings`、`/parcels/summary|trend|ranking`。
4. **越权口径回归清单（每批必跑）**：按 1.4.2 实测表逐条断言（404/403/静默/业务码），**禁止统一**。
5. **收敛到服务器阶段（须在部署窗口执行）**：编译打包、Flyway 迁移与快照比对、既有 24 接口 + 本批接口端到端冒烟、`EXPLAIN` 索引命中与分页耗时、Redis 令牌桶并发（如适用）。

---

## 7. §12.1 定向豁免登记

| 项 | 内容 |
| ---- | ---- |
| **原条款** | `项目规则1.md` §12.1：改 `hrm-demo` 时不得触碰 `hrm-admin` / `hrm-server` |
| **本次授权** | 用户明确授权**方案 B**（为 Demo 的 145 接口写真实后端），构成对该条款的**定向豁免** |
| **豁免范围** | **仅在 `hrm-dev/hrm-server/` 内新增领域模块**（`controller/service/mapper/entity/dto/vo` + 迁移脚本 + 配置 + 测试）。**不改 `hrm-dev/hrm-admin/`**；**不改 `hrm-dev/hrm-demo/` 源码**（Mock 保留，作为行为规格与离线兜底） |
| **影响面** | 后端 24 → 145 接口；数据库 4 → 约 36 表；`hrm-demo` 可经 `VITE_MOCK_ENABLED` 在 Mock 与真实后端间切换（前端零改动）；`hrm-admin` 继续消费既有 24 接口，**零改动零影响** |
| **回滚方式** | ① 代码：按批次 `git revert` 对应提交 / 切历史 tag；② 数据库：Flyway 只增不改，回滚需按批次准备反向脚本（**属 C 档，须主智能体三步授权**）；③ 前端：切回 Mock（`VITE_MOCK_ENABLED=true`）即恢复演示态 |
| **对「Demo 可整体剥离」目标的影响与保留措施** | **不影响剥离性**：Demo 与 `hrm-server` 通过 **HTTP 契约**耦合，无源码相互引用。保留措施：① Mock 不删除；② 契约以 Mock 为唯一行为规格（本文件 6.3 逐条固化）；③ `hrm-server` 不引用 Demo 任何代码/资源；④ `hrm-admin` 零改动，仍可独立运行；⑤ `hrm-demo` 仍可整体剥离（仅需切回 Mock 即可独立演示） |
| **边界** | 豁免**不覆盖**：① 改 `hrm-admin`；② 改 `hrm-demo` 源码；③ 改 `api.md`/`db.md`/`plan.md`/`algo-hrm-server.md`；④ 涉生产结构变更的执行（仍属 C 档） |

---

## 8. 风险与遗留清单（含待用户裁定开放问题）

### 8.1 技术风险

| # | 风险 | 等级 | 影响 | 缓解 |
| - | ---- | ---- | ---- | ---- |
| R-1 | 会话结构变更（`SessionInfo` 加 `stationId`）后 **Redis 旧会话无该字段** → 非 ADMIN 数据范围收敛拿到 `null` | 中 | 数据范围失效或误放行 | 上线时清 `hrm:session:*`（或自然过期）；`stationId=null` 的非 ADMIN 按"收敛到无数据/拒绝"安全兜底（见 8-2） |
| R-2 | 越权 403/404 分界被"顺手统一"破坏 | 中高 | 契约破坏、前端错误处理错乱 | ADR-07 + 1.4.2 实测表 + 6.4 第 4 项回归清单；纳入 code-review 检查项 |
| R-3 | 新增公开端点 `/work-orders/auto-dispatch` 的攻击面 | 中高 | 伪造工单/注入 | P0.5 安全评估前置；企微签名/解密落地前**不得公网暴露**（或先内网/白名单） |
| R-4 | `java.io.tmpdir` 未重定向 → Excel 大文件写满系统盘（30G） | 中 | 服务不可用 | 1.4.8/5.1 硬约束 + 部署复核项（C 档） |
| R-5 | 迁移执行/结构变更属 C 档，误在服务器手改源码或直连执行 DDL | 中 | 数据/环境不一致 | 流程纪律：只出脚本，执行由主智能体授权；服务器禁写源码 |
| R-6 | parcel 深分页/OFFSET 在 20 万级劣化 | 中 | 接口超时 | ADR-06 游标优先 + 4.3 索引 + 服务器实测（TODO-1） |
| R-7 | 现有 24 接口行为被 P0 改造（分页 400、角色注解迁移）波及 | 中 | 回归缺陷 | P0 全部回归既有 24 接口；`hrm-admin` 不传越界分页，影响可控 |
| R-8 | 算法落地与离线原型浮点/日期差异 | 中 | 结果末位不一致 | algo §1.4 容差断言 + `LocalDate.toEpochDay()` 本地时区（不用 `new Date('YYYY-MM-DD')`） |
| R-9 | `hrm.algo.*` 参数硬编码 | 中 | 违反 §11.4 | 代码检索禁止内联常量；`@ConfigurationProperties` 强绑定 |

### 8.2 口径风险（未裁定不得实现）

| # | 口径 | 现状默认（未裁定前必须等价） |
| - | ---- | ---- |
| Q1 | KPI 等级阈值与模式 | 保持 90/80/70；`quantile.enabled=false` |
| Q2 | 分位模式排名与等级关系 | 不启用分位 |
| Q3 | 绩效 `capRatio` 上限与负净额 | `capRatio` 默认、`allowNegativeNet=false`（待确认） |
| Q4 | SLA 阈值 | 保持 48/24/8 单一口径 |
| Q5 | 派单多目标权重与丢弃上限 | 默认权重（用户定权重） |
| Q6 | 请假扣款开关与连缺阈值 | `leaveDeductEnabled=false`、连缺 3 天 |

> 均源自 `algo-hrm-server.md` §2；未裁定前实现**必须使用与现状逐位等价的默认值**（上线即无行为变化），新能力以开关形式默认关闭。

### 8.3 必须由用户裁定的开放问题

| # | 问题 | 备选/建议 | 阻塞点 |
| - | ---- | ---- | ---- |
| 1 | **算法口径 Q1~Q6**（见 8.2） | 见 algo §2 备选方案 | 未裁定前按等价现状默认；裁定后方可实现新能力 |
| 2 | **数据范围兜底语义**：非 ADMIN 且 `stationId=null` 时如何处理 | A. 拒绝（401/403）；B. 收敛到"无数据"（空列表）。建议 B（不改变登录态，前端表现为空态） | P0 C-02/C-03 |
| 3 | **公开端点 `/work-orders/auto-dispatch`**：是否允许公网可达？企微回调签名/加密（`msg_signature`/`EncodingAESKey`）方案与上线前是否先内网 | A. 先内网/白名单，企微对接后放公网；B. 立即公网 + 签名校验。建议 A | P8 + P0.5 安全评估 |
| 4 | `GET /leave/settings` **角色口径**：api.md §7.1 记"不限角色"，Mock 限 ADMIN | A. 以 Mock 为准（ADMIN）；B. 以 api.md 为准（不限角色）。需明确唯一真源 | P7 |
| 5 | **错误码 90xx 段冲突**是否确认按 Demo 顺延（通知 90xx、考勤 91xx） | A. 确认顺延（与 Mock 一致）；B. 重划 90xx 归考勤并迁移通知码（回归面大） | P0 C-05 |
| 6 | **越权 403/404 差异**是否确认为契约（保留差异，不统一） | A. 确认保留（与 Mock 一致）；B. 统一（会破坏契约，不推荐） | P0 ADR-07 |
| 7 | **分页越界行为**：现有钳制 → 契约 400，是否确认改为 400 | A. 改为 400（对齐 Mock）；B. 保持钳制（偏离契约） | P0 C-04 |
| 8 | **`{id}` 非数字**：现有 400 vs Mock 404，是否需对齐 | A. 保持 400（前端不发非数字）；B. 加全局转换兜底为 404 | P0（低） |
| 9 | **部署目标**：本次后端是否与现网 `courier-server` **并存**？端口/域名如何分配（同域 Nginx 反代 `/api` vs 独立子域）？ | A. 独立端口 + 子域（隔离度最高）；B. 同域反代不同路径。需运维与用户确定 | 部署（C 档） |
| 10 | **`hrm-demo` 切换真实后端的构建/开关**由谁决策？`hrm-admin` 是否也切真实后端（涉及 §12.1 边界） | A. 仅 Demo 切；B. 两端都切（需另行授权 hrm-admin） | 前端集成 |
| 11 | **parcel 初始数据来源**：真实采集（二期爬虫）未上线前，`parcel` 为空时趋势/排行接口的空态与算法降级是否可接受 | A. 接受空态；B. 提供演示种子数据 | P10 |
| 12 | **唯一约束策略**：Service 查重 + 普通索引（现状）vs 数据库唯一索引（配合 `is_deleted=id`） | A. 保持 Service 查重（低并发够用）；B. 升级强约束。建议 A | 4.6 |
| 13 | **可热改配置的审批与审计责任**：谁可改 KPI 权重/计薪规则/SLA，是否需操作留痕（审计表） | A. 仅 ADMIN 可改 + 操作留痕；B. 仅 ADMIN 可改不留痕 | P4/P6 |
| 14 | **数据保留/归档**：`login_log`/`client_log`/`sync_task_log`/`notification` 增长与归档策略 | A. 定期归档（N 月）；B. 暂不归档 | 上线后 |
| 15 | **多实例部署**：是否需要 Redis 令牌桶 Lua 原子限频（TODO-6） | A. 单实例（进程内令牌桶即可）；B. 多实例（补 Lua） | P2/P9 |

---

## 附录 A. ADR 决策记录

> 说明：按角色约定 ADR 独立成 `adr-{序号}-{标题}.md`；本轮交付为单一架构文档，故 ADR **内嵌**于本附录（便于与主文档同步评审）。若主智能体要求拆分，可按下列 7 条各自落文件。

**ADR-01 模块化单体 + 域子包（含既有 5 域迁移）**
- 状态：提议（待 Review）｜日期：2026-09-24
- 上下文：14 个新增领域将使扁平包类数量爆炸（controller ~19、dto/vo 各 ~150）。
- 决策：单体不变；`controller/service/dto/vo` 按域子包，`mapper/entity` 保持扁平；既有 5 域纯包移动。
- 后果：可导航性与边界清晰度提升；代价是一次纯移动重构（编译期暴露风险，运行期零变更）。
- 备选：全扁平（拒绝，不可维护）；全按域拆 mapper/entity（拒绝，无检索收益）。

**ADR-02 数据范围收敛三层机制**
- 上下文：Mock 以 `applyDataScope` 统一收敛 query `stationId`；越权口径却逐端点不同（404/403/静默/业务码）。
- 决策：L1 查询参数静默收敛（拦截器）+ L2 角色门槛全局 403 + L3 路径资源按 `@DataScope(policy)` 逐端点声明。
- 后果：跨切面逻辑一处收口，Service 不重复；必须逐端点维护策略（6.3 映射表即清单）。
- 备选：全局统一越权语义（拒绝，破坏契约）。

**ADR-03 跨域调用：服务层直调 + 领域事件断环，不引入 MQ**
- 上下文：`finance→hr` 与 `hr→finance`（离职结算）会成环。
- 决策：默认同步只读直调（单向）；双向依赖处用 Spring `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)`；不引入 MQ。
- 后果：无分布式复杂度；事件为进程内、随单体演进，未来可平滑换 MQ。
- 备选：引入 MQ（拒绝，规模不需要）；hr 直调 finance（拒绝，成环）。

**ADR-04 数据库面向频繁迭代的设计原则**
- 决策：迁移小步、已执行脚本永不改；只加列不删列、不改列类型（expand-contract）；大表变更联评；索引变更附回滚脚本；**不使用存储过程/触发器/物理外键**；JSON 仅用于低频扩展属性，高频字段用显式列 + 索引。
- 后果：迭代成本与风险可控；JSON 滥用需靠评审约束。
- 备选：JSON 承载核心字段（拒绝）；物理外键（拒绝，D6 已定逻辑外键）。

**ADR-05 配置分区：业务口径入库热改，算法超参走配置文件**
- 决策：KPI 指标/计薪规则/SLA/派单规则/同步配置中心/打卡规则/请假开关 → 入库（带审计与快照）；算法超参/性能/路径 → `hrm.algo.*` 与外置配置。
- 后果：运营可调、历史可解释；配置文件变更走部署（不可热改）。
- 备选：全部入库（拒绝，算分算薪超参无需热改）；全部配置化（拒绝，违反可运营性）。

**ADR-06 `parcel` 大表分页：游标优先，OFFSET 兜底**
- 决策：默认 `mode=CURSOR`（键 `(inbound_time, id)`）；OFFSET 限最大页深；索引按 4.3；服务器实测不达标则强制游标（TODO-1）。
- 后果：20 万级扫描行恒定（38 vs 20020）；代价是前端需支持游标翻页（Mock 已按游标语义设计）。
- 备选：深分页 OFFSET（拒绝，线性劣化 A）。

**ADR-07 越权语义保留差异（禁止统一）**
- 决策：403/404/静默/业务码 按端点声明，**不做全局统一**；见 1.4.2 实测表与 6.3 映射。
- 后果：完全对齐 Mock 契约；需回归清单守护（6.4 第 4 项）。
- 备选：统一 404 或统一 403（拒绝，破坏契约）。

## 附录 B. 错误码分段总表

> 依据 Mock `constants/errorCode.js`（`CODE`/`CODE_MESSAGE`）与 `api.md` §2/§7.2。**与 Mock 逐条一致**；`ErrorCode` 枚举按此扩展。

| 段 | 域 | 码值（示例） | 来源 |
| ---- | ---- | ---- | ---- |
| 通用 | 与 HTTP 语义对齐 | 200 / 400 / 401 / 403 / 404 / 500 | 现有 |
| 10xx | 认证与账号 | 1001~1004 | 现有 |
| 20xx | 员工 | 2001~2003 | 现有 |
| 30xx | 部门 | 3001~3004 | 现有 |
| 40xx | 驿站 | 4001~4004 | 现有 |
| 50xx | 导入导出 | 5001~5003 | 现有 |
| 60xx | 同步任务/采集运行态 | 6001, 6002 | plan.md 3.4 已定段位 |
| 70xx | 包裹 | 7001~7003 | plan.md 3.4 已定段位 |
| 80xx | 工单 | 8001~8006 | Demo 提案 |
| **90xx** | **通知** | **9001, 9002** | Demo 提案 |
| **91xx** | **考勤/排班/补卡** | **9101~9109** | Demo 提案（原契约 9001~9006 与通知冲突，顺延至 91xx） |
| **92xx** | **KPI** | **9201~9204** | Demo 提案 |
| **93xx** | **人事/入离职** | **9301~9306** | Demo 提案 |
| **94xx** | **财务/工资单** | **9401~9405** | Demo 提案 |
| **95xx** | **同步配置中心** | **9501~9510** | Demo 提案 |
| **96xx** | **请假** | **9601~9607** | api.md §7.2（D） |

- 分段冲突处置登记：原"考勤 9001~9006"与"通知 9001/9002"冲突，Demo 已按 91xx 实现考勤；若用户裁定重划，须同步迁移通知码并全量回归（8-5）。
- `TODO(扩展)`：以上 60xx~96xx 为 Demo 提案段位，三期专项设计定稿后需与后端错误码表对齐并保持文案同源。

## 附录 C. 待同步文档清单（交付后由主智能体统筹）

| 文档 | 需同步内容 | 性质 |
| ---- | ---- | ---- |
| `api.md` | 明确 `/leave/settings` GET 角色（8-4）；其余 145 接口契约无需改动（以 Mock 为准） | 口径确认后 |
| `db.md` | 新增 ~32 张表的表结构详设（由数据库工程师按 P4 产出后回填）；§6.2 `parcel` 索引预告追加 4.3 两条 | 结构先行 |
| `plan.md` | 迭代阶段与批次对应关系（本项目由"一期/二期/三期"细化为 P0~P10） | 建议 |
| `sql/schema/mysql/init.sql` | 与迁移脚本同批同步 | 强制 |
| `update-log.md` | 本文档新增记录 | 强制 |

---

> **交接声明**：本文件为**架构设计产物**，不含业务代码/DDL/迁移脚本/前端改动。下一步由主智能体 Review → 通过后交**数据库工程师**（按第 4 章出 DDL 与迁移）与**后端工程师**（按第 1/2/6 章落地）。所有"收敛到服务器阶段"的实测项（编译、迁移、索引命中、分页耗时、令牌桶并发）须在部署窗口复核。
