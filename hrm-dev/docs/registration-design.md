# 员工自助注册 · 架构设计方案（含接口契约草案 / 数据表设计 / 流程设计）

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | **v1.3（必改项修订，待复评）** |
| 编写日期 | 2026-09-26（v1.3 修订；v1.2 定稿于同日） |
| 状态 | **v1.2 经技术评审（[tech-review-registration.md](tech-review-registration.md)）判「打回」→ v1.3 已按必改项 M1~M4 逐条修订，待复评**（修订对照见 **§12**）。定稿 = 口径类问题已全部有终局裁定（见 §11.4 裁定结果表），不再存"待裁定/替代方案"项；**本方案仍属方案阶段产物**，依 **P0.6 / L8 技术评审闸门**，报到主智能体审批与实施前**必须先经技术评审工程师（`express-station-tech-reviewer`）评估通过（通过 / 有条件通过）**；结论「打回」须退回本架构师修订后重评。**本方案不宣称已通过评审；v1.3 尚未取得复评结论。** |
| 产出角色 | 架构师 `express-station-architect`（只出设计；不写实现代码、不执行 git/部署/MCP） |
| 上游需求口径 | **用户已确认**（见 §0.3）；本方案只做设计，不改需求 |
| 关联文档（契约真源） | [api.md](api.md)（**契约唯一真源**：体例、编号、路径前缀、错误码）、[db.md](db.md)（表结构规范真源）、[requirement.md](requirement.md)、[security-auth-review.md](security-auth-review.md)、[multi-client-architecture.md](multi-client-architecture.md)、[plan.md](plan.md) |
| 安全面并行产物 | ① [security-registration-review.md](security-registration-review.md)（331 行，结论**「阻断（公网）」**，必做 M-1~M-9）→ 本版 §11.2 全量采纳；② **新增** [security-full-review-20260926.md](security-full-review-20260926.md)（仓库级全量审计）→ 其 **SEC-FULL-08（现有 `/auth/sms/send` 可枚举在职手机号，存量）** 与 M-1/M-2 **合并为同一口径**，见 **§11.13**；SEC-FULL-18（未知场景 fail-open）= M-1 同项 |
| 工程面并行产物 | **新增** [tech-review-full-20260926.md](tech-review-full-20260926.md)（项目级技术评审，结论**「有条件通过」**）→ 其 **M1（`api.md` 契约缺口 83 端点）** 与本方案 §3.5 / §9 批次**合并**；**M5（后端零可运行验证）** 落为各后端批次验收口径（本机无 JDK → 收敛到服务器 `mvn test`），见 §11.14 |
| 本版定稿依据 | **用户 5 条裁定全部生效**（§0.5）：① 岗位功能要做 → **方案乙**（新增 `employee.position`，C 档，已授权）；② M-1~M-9 **全部整改**（无"后续做"）；③ `api.md` 全量补录 83 端点（本方案出清单/规范/批次/验收，补录由后端执行）；④ 审批通过**不自动激活**、公开端点**仅 2 个**、授权修手机号唯一约束、授权加 Nginx 限速；⑤ 原 U-01~U-17 按 §11.4 建议值定稿 |
| 本方案边界 | 只产出设计；**不含**业务实现代码、不含 Flyway 脚本正文、不执行部署/MCP/git |

***

## 0. 取证方式与已核实事实

### 0.1 取证方式

只读代码与文档，未运行任何构建/测试（本机无 JDK/MySQL/Redis，见 [TASK.md](../TASK.md) 纪律 2）。全部结论标注 `文件:行号` 出处；不确定处标「需确认」并给确认方式。

### 0.2 事实性纠正（任务描述 / 既有材料 与实测出入）

| # | 描述 | 实测事实 | 出处 |
| --- | --- | --- | --- |
| ① | 任务称「hrm-server 存在，含入离职/员工/薪资/短信」 | **成立**（首次 `LS` 因输出截断未见 `hrm-server`，`Glob` 复核后确认存在） | — |
| ② | 任务称登录页在 `hrm-clients/apps/staff-h5/src/views/login/index.vue` | **成立**（该文件存在，双 Tab + `createCountdown` + `isPhone` 均在） | [index.vue:74-102](../hrm-clients/apps/staff-h5/src/views/login/index.vue#L74-L102) |
| ③ | 任务称存在 `docs/security-registration-review.md`（并行安全评估） | **~~不存在~~ 已被修订推翻**：该报告**已产出（331 行，结论「阻断（公网）」）**，本方案 v1.1 已逐条响应，见 **§11** | `security-registration-review.md` |
| ④ | [api.md](api.md) 头部标「接口总数 46」，§4.0 概览亦为 46 | **~~与代码不一致／历史版本残留~~ 已被本次复核推翻**：§4.0 的 46 **已自洽**（认证4+看板1+员工10+部门4+驿站5+考勤10+补卡4+班次4+排班4＝46，考勤域 22 端点为**主智能体已补录**）。但全仓 controller 方法级映射共 **151** 个，§4.0 仅覆盖 46、§7 覆盖 16，**缺口 83 端点**——新接口沿用**体例**而非沿用**计数**；复核结论见 **§11.10** | [api.md:184-234](api.md)、[api.md:1134](api.md)；controller 目录见 [controller/](../hrm-server/src/main/java/com/qiujie/controller/) |
| ⑤ | [requirement.md](requirement.md) 明确「忘记密码自助找回」**不做**（走线下） | 成立：「忘记密码自助找回 → 线下联系管理员重置」 | [requirement.md:336](requirement.md) |
| ⑥ | 任务称「岗位」可能是实体 | **不存在「岗位」实体**。`position` 在 `hr_flow` 为 `VARCHAR(50)` 自由文本，`employee` 表**无 position 列**（岗位仅存于流程表）。**不得臆造岗位表** | [HrFlow.java:56-57](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L56-L57)、[db.md:766](db.md)、[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66) |

### 0.3 用户已确认的需求口径（不改，只映射）

1. 入口：驿站助手（员工端 H5，`apps/staff-h5`）**登录页**新增「员工注册」入口。
2. 注册字段：姓名 + 手机号 + **短信验证码** + 意向驿站 + 意向岗位 + 本人设置的密码。
3. 提交后：生成**入职审批单**，进入**既有入离职模块的入职审批**流程（人工审批）。
4. 审批通过后三件事**同时完成**：生成员工档案 + 设置薪资 + 设置岗位/站点/角色。
5. 本接口**公网可达、无需登录**（未鉴权入口）。

### 0.4 既有可复用资产（本方案的落点，均为实测）

| 资产 | 关键事实 | 出处 |
| --- | --- | --- |
| 入职流程状态机 | `HrFlow.status` ∈ `IN_PROGRESS` / `COMPLETED` / `REJECTED`（**无** CANCELLED/EXPIRED） | [HrConstants.java:28-30](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L28-L30)、[db.md:776](db.md) |
| 入职步骤（顺序真源） | `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY → DONE` | [HrConstants.java:59-65](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L59-L65) |
| 「三件事」= 既有 3 个步骤的副作用 | 建档=`createEmployeeForFlow`；设岗位站点角色=`assignForFlow`；设薪=`salaryForFlow`；最后 `finishOnboarding` 转在职 | [HrFlowServiceImpl.java:264-398](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L398) |
| `hr_flow` 已含注册要素字段 | `candidate_name` / `phone` / `station_id` / `position` / `role` / `dept_id` / `expected_entry_date` | [HrFlow.java:35-63](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L35-L63) |
| 建档要求入参 | `CREATE_ACCOUNT` 需 `username`（字母开头 4-30）+ `password`（8-20 含字母数字）+ `deptId` + `stationId`；建档即写 `employee` + `hr_profile` + **0 值 `hr_salary`** | [HrFlowServiceImpl.java:264-338](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L338) |
| 建档口径 | 首落 `employee.status=0`（未生效）、`pwd_changed=0`（首登强制改密），最后一歩才置 1 | [HrFlowServiceImpl.java:301-303](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301-L303)、[:397](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L397) |
| 角色赋值 | `assignForFlow` 仅接受 `STATION_ADMIN` / `STAFF`；`createOnboarding` 恒建 `role=STAFF` | [HrFlowServiceImpl.java:364-370](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L364-L370)、[:119](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119) |
| 入离职端点权限 | **10 个端点全部仅 ADMIN** | [HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111) |
| 手机号唯一性 | `employee.phone` 活跃唯一由 **Service 查重**（不建 DB 唯一索引），重复回 `2003` | [EmployeeServiceImpl.java:126-132](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L126-L132)、[db.md:42-50](db.md) |
| 短信场景枚举 | `SmsScene` ∈ `LOGIN` / `DEVICE_VERIFY` / `PERIODIC_REAUTH`；**未知 scene 回落 LOGIN** | [SmsScene.java:12-16](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L12-L16)、[AuthServiceImpl.java:606-618](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L618) |
| 短信发码前置 | `LOGIN` 场景**要求手机号已绑定账号**，否则 `1109`（防枚举）；频控 4 维（手机号/IP/设备/账号） | [AuthServiceImpl.java:259-279](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L259-L279)、[SmsCodeStore.java:93-115](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115) |
| 短信配置 | `hrm.sms.*`：`provider`（`none` 降级/`aliyun` 生产）、频控/TTL/尝试上限全部外置 | [SmsProperties.java:19-116](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L19-L116) |
| 公开端点单一真源 | `PublicEndpoints.PATHS` 6 条；新增公开端点**扩大攻击面，公网暴露前须再过 P0.5 安全评估** | [PublicEndpoints.java:13-47](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L47) |
| 驿站接口 | `GET /api/v1/stations` **仅 ADMIN**，当前**无公开驿站选项端点** | [StationController.java:27-39](../hrm-server/src/main/java/com/qiujie/controller/station/StationController.java#L27-L39) |
| 迁移现状（实测，v1.3 更正） | 现最大版本 **`V15`**（**仅 `mysql/` 目录**，实测 `V1~V15`）；~~`mysql/` 与 `postgresql/` 双目录各至 V15~~（**v1.2 表述错误，已推翻**：`postgresql/` 实测**仅 `V1`、`V2`**）。快照唯一同步点 = `sql/schema/mysql/init.sql`。**PostgreSQL 迁移目录自 V2 起冻结，本期不产出 pg 脚本与 pg 快照**（[db.md:8](db.md)「MySQL 8.0 单库…`postgresql/` 目录冻结不再维护」、[db.md:401](db.md)「`postgresql/init.sql` 冻结…`{vendor}` 只会选中 `mysql/`」）。**本方案续号 V16~V19，仅 mysql 脚本**（见 §7/§11.6/§11.9） | [migration/mysql/](../hrm-server/src/main/resources/db/migration/mysql/)、[migration/postgresql/](../hrm-server/src/main/resources/db/migration/postgresql/)、[sql/schema/mysql/init.sql](../sql/schema/mysql/init.sql)、[db.md:8](db.md)、[db.md:401](db.md) |
| 岗位存储（实测） | `position` **仅**存 `hr_flow.position VARCHAR(50)`（自由文本）；`employee` **无** position 列 → 方案乙需加列（V19，C 档，已授权） | [HrFlow.java:56-57](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L56-L57)、[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66)、[db.md:766](db.md) |

### 0.5 本版定稿依据（用户裁定 + 新增上游产物，全部生效）

| # | 依据 | 落点 |
| --- | --- | --- |
| 裁定① | **岗位功能要做 → 采纳方案乙**：新增员工岗位属性（成为员工档案属性），属 **C 档结构变更**，用户已授权；需 迁移 + 快照同步 + `db.md` 更新 + 前端展示改造 | §11.9、§7 V19、§9 批次 B2/B3/B6 |
| 裁定② | **M-1 ~ M-9 全部整改**（不允许"后续做"） | §11.2（逐条采纳）、§9 总批次表 |
| 裁定③ | **`api.md` 全部补录**（缺口 **83 端点**）——本方案只出**补录清单 + 规范 + 批次 + 验收标准**，实际补录由后端执行 | §3.5、§9 批次 B1-C1~C6、§11.10 |
| 裁定④ | 审批通过**不自动激活**（`status=0` + ADMIN 一次性口令 + 首登强制改密 + **不复用注册密码**）；**公开端点仅 2 个**（发码 + 提交）；**授权修** `employee.phone` 唯一约束（含存量去重）；**授权加** Nginx 限速（**只增不改**） | §11.4 U-01/U-02/U-03/U-04/U-05/U-06、§11.6、§11.8 |
| 裁定⑤ | 原 **U-01~U-17 按 §11.4 建议值直接定稿**，删"替代方案"（降为备注），达到可直接实施状态 | §11.4 裁定结果表 |
| 新增产物 A | [security-full-review-20260926.md](security-full-review-20260926.md) · **SEC-FULL-08**（现有发码链路可枚举在职手机号，**存量**）与 M-1/M-2 **合并统一口径**；SEC-FULL-18 = M-1 | §11.13 |
| 新增产物 B | [tech-review-full-20260926.md](tech-review-full-20260926.md) · 结论「有条件通过」，必改项 **M1**（契约缺口）/ **M5**（可运行验证）**纳入本方案**；M2（真实域名）归安全、不由本方案处置；M3/M4/M6/M7/M8/M9 与本方案无产物依赖 | §11.14、§3.5、§9 |

***

## 1. 流程设计

### 1.1 端到端流程（文字状态机）

```text
[staff-h5 登录页] 「员工注册」入口
        │
        ▼
S1 填手机号 → 点「获取验证码」→ POST /auth/sms/send (scene=REGISTER)
        │        频控命中 → 1101（前端倒计时，复用 createCountdown）
        │        通道失败 → 1105
        │        （M-2/SEC-FULL-08：对已注册/未注册响应恒定，文案统一「验证码已发送」）
        ▼
S2 填 姓名 / 验证码 / 意向驿站 / 意向岗位 / 密码(仅留痕) → POST /registration
        │        验证码错/过期 → 1102；尝试超限 → 1103
        │        （M-2：提交响应对「是否已注册」恒定，不再返回 9310）
        ▼
   建 employee_registration（status=SUBMITTED, apply_no；query_token 一期不生成）
   + 建 hr_flow（flow_type=ONBOARDING, source=SELF_REGISTER, operator=null, status=IN_PROGRESS）
   + 回填 registration.flow_id      ← 单事务
        │
        ▼
S3 返回 { applyNo, status, createTime }（一期无 queryToken；进度由 ADMIN 线下/短信告知）
        │
        ▼
[A] 进入既有入离职模块：GET /hr/onboarding 列表可见该单（人工审批）
        │
        ├── ADMIN 驳回 ── POST /hr/onboarding/{id}/reject
        │        → hr_flow.status=REJECTED；registration.status=REJECTED
        │        → 申请人可重新注册（生成**新**申请；旧单保留痕迹）
        │
        ▼
S4 ADMIN 审批通过 ── POST /hr/onboarding/{id}/approve   【聚合联动，见 §4】
        │        同一事务按序执行既有 **3** 步副作用：
        │          CREATE_ACCOUNT(建档 employee+hr_profile+0值薪资)
        │          → ASSIGN_STATION(站点/部门/角色 + **岗位双写 flow.position & employee.position**)
        │          → SET_SALARY(定薪)
        │          ~~→ DONE(转在职 status=1)~~   ← **已被安全评估 M-4 推翻**（审批通过不得自动激活，见 §11.2/M-4、§11.8）
        │          → 定稿：仅前 3 步，停在 SET_SALARY；hr_flow 保持 IN_PROGRESS，employee.status 保持 0（待激活）
        │        → registration.status=APPROVED, approved_employee_id 回填
        │        → DONE（转在职/激活）改由 ADMIN 二次显式确认（复用 completeOnboardingStep DONE 或新端点）
        ▼
S5 员工首次登录（员工端）：**须先经 DONE 激活（status=1）**；初始口令由 ADMIN 一次性设定、`pwd_changed=0` 首登强制改密；口径见 §1.4 与 §11.8
```

### 1.2 状态机（两张表，职责分离）

**`registration.status`（申请侧权威状态）**

| 状态 | 含义 | 进入条件 | 出边 |
| --- | --- | --- | --- |
| `SUBMITTED` | 已提交，审批中 | 提交成功（单事务） | → `APPROVED` / `REJECTED` / `EXPIRED` |
| `APPROVED` | 审批通过且联动成功 | 聚合审批事务提交成功 | 终态 |
| `REJECTED` | 已驳回 | ADMIN 驳回 | 终态（可另起新申请） |
| ~~`CANCELLED`~~ | ~~申请人撤回~~ | **一期不产生**（R-4 取消，U-06）；枚举保留不用 | — |
| `EXPIRED` | 超时失效 | `now ≥ expire_time` 且仍 `SUBMITTED`（惰性判定或定时任务） | 终态 |

**`hr_flow.status`（审批载体，沿用既有三态，不改枚举）**

| 事件 | hr_flow 处理 | 说明 |
| --- | --- | --- |
| 提交 | 新建 `IN_PROGRESS` | 步骤全部 `PENDING`；`source=SELF_REGISTER` |
| 驳回 | `REJECTED` | 复用既有 `rejectOnboarding` |
| ~~撤回~~ / 超时 | **置 `REJECTED`**，`reject_reason` 写「超时失效」 | ~~「申请人撤回」~~ 随 R-4 取消而移除（U-06）；**不改 `hr_flow` 状态枚举**（避免动既有契约） |

> **为什么申请侧与流程侧状态分离**：`hr_flow` 是**审批载体**（既有域，权限 ADMIN，端点已定稿），`registration` 是**注册事实与凭据载体**（含密码散列、查询凭据、来源留痕，且申请人尚非 `employee`）。合并到一张表会让"未登录申请人"的凭据与审计信息污染既有流程表语义，并迫使修改既有状态枚举（契约破坏）。

### 1.3 关键分支

| 分支 | 判定口径 | 产出 |
| --- | --- | --- |
| **重复提交** | 同 `phone` 存在 `status=SUBMITTED` 的 registration | `9307`（§3.3）；不新建单 |
| **已注册账号（定稿，M-2 严格形态）** | 提交时查 `employee.phone` 活跃记录命中 → **不区分响应** | **仍按正常建单**；由 ADMIN 审批时识别（R-8 提示 + M-5 唯一约束兜底，冲突则驳回）；**不发 `9310`**（§3.4） |
| **驳回** | ADMIN 驳回，原因 2-200 字（复用 `HrFlowRejectRequest` 校验） | 两表同时置 `REJECTED`；申请人重新注册 = 新单 |
| ~~**撤回**~~ | ~~申请人凭 `applyNo + queryToken`~~ | **一期取消**（R-4 取消，U-06）；`CANCELLED` 枚举保留不用 |
| **超时** | `expire_time = create_time + N 天`（**定稿 N=7**，配置项 `hrm.registration.expire-days`） | 两表同时置终态（hr_flow→`REJECTED`，`reject_reason="超时失效"`）；**默认惰性判定**（查询/审批前判），自动任务 `TODO(扩展)` |

### 1.4 员工首次登录与改密口径（**定稿，U-02**）

既有口径：建档 `pwd_changed=0` → 首登强制改密（[HrFlowServiceImpl.java:302](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L302)）。

**定稿（M-4 + 用户裁定④）**：**不复用注册自设密码**。初始口令由 **ADMIN 在 `CREATE_ACCOUNT` 一次性设定**（`initialPassword`，R-6 必填），`pwd_changed=0` **首登强制改密**。理由：切断"注册可控数据 → 账号凭据"链路（安全链 C 的凭据面）；注册密码未经理工端二次强度确认，不宜直通账号。

> ~~原建议「自设密码场景 `pwd_changed=1`」~~ 已作废（M-4 及用户裁定④推翻）。注册页「密码」字段：**U-02 定稿保留为仅留痕**（`registration.password_hash`，终态清 NULL），**不作员工口令**；`TODO(扩展): 若产品接受，可整体移除注册密码字段`。
>
> **服务端强制（R6）**：`pwd_changed=0` 时除改密/登出/me 外一律拒——属 R6 范围，本方案不改变其实现状态（现状为"仅前端实现，服务端未强制"，见 [security-auth-review.md:132](security-auth-review.md)）；但本次 M-4 依赖它生效，故 **SEC-FULL-09（首登强制改密服务端强制）列为公网放行前必闭环的关联项**（§11.14）。

***

## 2. 数据设计

### 2.1 三选一取舍论证

| 方案 | 论证 | 结论 |
| --- | --- | --- |
| ① 只新增专用表 `employee_registration` | 注册事实与既有 `hr_flow` **完全平行**：审批步骤机、步骤条、驳回理由、操作留痕、PC 审批台全部要在新表**重造一遍**，与既有入离职模块形成两套审批实现（违反规则 §2 复用）。且需求明确「进入**既有**入离职模块的入职审批」 | **否决** |
| ② 只复用 `hr_flow` 承载 | `hr_flow` 已含 `candidate_name/phone/station_id/position/role`，且**步骤机即审批**，零新增表。但缺三样**必须**承载物：⒜ 申请人自设密码的 BCrypt 散列（`hr_flow` 无密码列，把散列塞进流程表属语义污染）；⒝ 未登录申请人的**查询/撤回凭据**（申请人尚无 `employee_id`，无身份锚点）；⒞ 注册来源/条款同意/申请编号等**注册事实审计**。另需改 `hr_flow` 加列（扩大既有表语义面） | **否决** |
| ③ **申请表 + 流程单（推荐）** | `hr_flow` 继续做**审批载体**（复用步骤机/权限/PC 审批台，零改造）；新增 `employee_registration` 做**注册事实与凭据载体**（密码散列、查询凭据、来源审计），两表以 `registration.flow_id ↔ hr_flow.id` 1:1 关联。职责单一、互不污染、复用最大化 | **采纳** |

> 与 [db.md:474-480](db.md) 「字段真源 + DDL 落位」体例一致；与 [db.md:436-459](db.md) `leave_request`（申请单独立建表 + 状态机 + 审批分槽）为**同构先例**。

### 2.2 新表 `employee_registration` 字段级设计

> 遵循 [db.md:482-501](db.md) §8.0：`snake_case`、主键 `id`、时间 `create_time/update_time`、逻辑删除 `is_deleted`、**不建物理外键**。**唯一索引例外**：本表对 `apply_no` 增设 `UNIQUE(apply_no)`（**U-17 定稿**，公开端点并发高于后台且无复用语义）；`phone` 走 Service 查重 + 普通索引。**（v1.3，M1）下表 `PG` 列仅为类型对照留档；`postgresql/` 目录自 V2 起冻结，本期不产出 pg 脚本与 pg 快照**（[db.md:8](db.md)/[db.md:401](db.md)）。

| 字段 | MySQL | PG | 允许空 | 默认 | 注释 |
| --- | --- | --- | --- | --- | --- |
| id | BIGINT AUTO_INCREMENT | BIGINT IDENTITY | 否 | - | 主键 |
| apply_no | VARCHAR(32) | VARCHAR(32) | 否 | - | 申请编号（**`UNIQUE`，U-17**，形如 `RG-YYYYMMDD-0001`，编号生成对齐 `flow_no` 两段式：先占位插入取 id 再回填，见 [HrFlowServiceImpl.java:452-471](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L452-L471)） |
| flow_id | BIGINT | BIGINT | 是 | NULL | 关联审批单（逻辑外键 `hr_flow.id`，提交时写入） |
| real_name | VARCHAR(50) | VARCHAR(50) | 否 | - | 姓名（2-20，**对齐既有** `validateOnboardingCreate` 口径） |
| phone | VARCHAR(20) | VARCHAR(20) | 否 | - | 手机号（`^1[3-9]\d{9}$`） |
| password_hash | VARCHAR(100) | VARCHAR(100) | **是** | NULL | 注册自设密码 BCrypt 散列（规格同 `employee.password`，cost=10）；**M-4 定稿：不作员工初始口令**（仅合规留痕）；**终态一律置 NULL**（凭据卫生，见下） |
| apply_station_id | BIGINT | BIGINT | 是 | NULL | 意向驿站（逻辑外键 `station.id`；**仅意向**，M-3） |
| apply_position | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 意向岗位（**自由文本**，对齐 `hr_flow.position`；**无岗位实体**，见 §0.2-⑥；**仅意向**，M-3） |
| source | VARCHAR(16) | VARCHAR(16) | 否 | 'STAFF_H5' | 注册渠道来源（审计；一期仅 `STAFF_H5`；与 `hr_flow.source`（业务来源 `SELF_REGISTER`）**语义不同、并存**） |
| agreement_version | VARCHAR(20) | VARCHAR(20) | 是 | NULL | 已同意的服务条款版本（合规留痕） |
| query_token_hash | VARCHAR(64) | VARCHAR(64) | **是** | NULL | ~~查询凭据 SHA-256（明文仅在提交响应返回一次）~~ → **一期不启用**（R-3 转 ADMIN-only，U-06）：**恒不写入**；列保留供后续自助查询 |
| status | VARCHAR(16) | VARCHAR(16) | 否 | 'SUBMITTED' | `SUBMITTED`/`APPROVED`/`REJECTED`/`EXPIRED`（`CANCELLED` 保留不用） |
| reject_reason | VARCHAR(200) | VARCHAR(200) | 是 | NULL | 驳回原因快照 |
| approved_employee_id | BIGINT | BIGINT | 是 | NULL | 通过后生成的员工（逻辑外键 `employee.id`） |
| approve_time | DATETIME | TIMESTAMP | 是 | NULL | 通过时间 |
| cancel_time | DATETIME | TIMESTAMP | 是 | NULL | （一期不用，随 R-4 取消保留列） |
| expire_time | DATETIME | TIMESTAMP | 是 | NULL | 失效判定基准（`create_time + N 天`，**N=7** 定稿） |
| client_ip | VARCHAR(50) | VARCHAR(50) | 是 | NULL | 提交来源 IP（审计；口径对齐 `login_log.login_ip`，[db.md:206](db.md)；出参脱敏） |
| is_deleted | TINYINT | SMALLINT | 否 | 0 | 逻辑删除：0=否，1=是 |
| create_time / update_time | DATETIME | TIMESTAMP | 否 | CURRENT_TIMESTAMP | 应用层 `MetaObjectHandler` 填充 |

**索引**（唯一键 `uk_表名_字段`；普通索引 `idx_表名_字段`）：

| 索引名 | 类型 | 字段 | 用途 |
| --- | --- | --- | --- |
| uk_employee_registration_apply_no | **UNIQUE** | apply_no | 申请编号唯一（U-17） |
| idx_employee_registration_phone | 普通 | phone | 重复提交查重 |
| idx_employee_registration_flow | 普通 | flow_id | 审批单 ↔ 申请单回关联 |
| idx_employee_registration_status | 普通 | status, create_time | 列表（按状态 + 时间倒序）；**M-8 清理任务**亦用 `status+expire_time`（可加 `idx_employee_registration_expire`，见 §11.2/M-8） |

**凭据卫生规则**：`password_hash` / `query_token_hash` 在进入任一终态（`APPROVED`/`REJECTED`/`EXPIRED`）时**同事务置 NULL**——避免"已失效申请长期残留可用凭据"。这也是把密码放申请单而非 `hr_flow` 的附加理由。

### 2.3 与既有表的字段映射

| 注册侧 | → 落点 | 映射口径 |
| --- | --- | --- |
| `real_name` | `hr_flow.candidate_name` → `employee.real_name` | 建档时 `employee.real_name = flow.candidate_name`（[HrFlowServiceImpl.java:295](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L295)）；**姓名不可在审批时被静默改写** |
| `phone` | `hr_flow.phone` → `employee.phone` | 同值传递（[HrFlowServiceImpl.java:296](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L296)） |
| `apply_station_id` | `hr_flow.station_id` → `employee.station_id` | **意向**，审批时可由 ADMIN 改定（`assignForFlow` 允许覆盖） |
| `apply_position` | `hr_flow.position` **+** `employee.position`（方案乙，U-07 定稿） | **双写、以 `employee.position` 为权威事实**：审批定岗时由 ADMIN 提交的 `position` 同时写 `flow.position`（流程留痕）与 `employee.position`（档案属性）；注册阶段仅存 `apply_position`（意向）。详见 **§11.9** |
| ~~`password_hash`~~ | ~~→ `employee.password`~~ | **~~新增通道：以申请单散列作为初始密码~~ 已被安全评估 M-4 推翻**：不得复用注册所填密码；初始口令由 ADMIN 在 `CREATE_ACCOUNT` 一次性设定（既有事实⑪），`pwd_changed=0` 强制首登改密。注册密码去向见 §11.8（**U-01/U-02 定稿**） |
| （注册不采集） | `employee.username` | 注册**不采集**登录账号；由审批人提供或按 **U-11** 规则生成 |
| （注册不采集） | `hr_flow.dept_id` → `employee.dept_id` | 注册**不采集部门**；`CREATE_ACCOUNT` 要求 `deptId` 非空，故须审批时必填（**U-09 定稿**） |
| （注册不采集） | `employee.role` | 恒 `STAFF`（既有 `createOnboarding` 口径）；`STATION_ADMIN` 仅可由审批人在 `ASSIGN_STATION` 显式授予（见 §5.4） |
| （注册不采集） | 薪资（`hr_salary`） | 注册**不采集**薪资；须审批时提供（防"注册即定薪"注入，见 §5.4） |

### 2.4 唯一性与并发口径

- **`employee.phone`（定稿，U-05）**：增设**活跃唯一**约束（M-5 硬项），**例外于** [db.md:42-50](db.md) 决策 D7「不建 DB 唯一索引」——理由：注册是公开端点、并发高于后台，且 `phone` 为登录标识，重复号的后果是登录 DoS（非仅脏数据）。形态见 §11.6（**仅 mysql**：生成列 `phone_active` + `UNIQUE`；~~PG 部分唯一索引~~ 因 `postgresql/` 冻结，本期不产出）。
- **`employee_registration.apply_no`（定稿，U-17）**：**增设** `UNIQUE(apply_no)`（无逻辑删除复用语义，与 D7 不矛盾）。`phone` 在申请表侧仍走 Service 查重 + 普通索引（活跃唯一由 `hr_flow`/`employee` 侧约束收口）。
- **并发兜底（定稿）**：DB 唯一约束为**最终防线**，Service 查重仅为友好报错，消除 TOCTOU 双提交窗口；审批联动并发见 §4.5 与 §11.7（`SELECT ... FOR UPDATE` 固定加锁顺序）。

***

## 3. 接口契约草案

> 体例与编号严格对齐 [api.md](api.md)：路径 `/api/v1`、统一响应 `{code,message,data}`、分页 `pageNum/pageSize`、出参手机号脱敏（[api.md:59-65](api.md)）。**本节仅为草案**，定稿须由后端写入 `api.md`（P5 契约先行）。

### 3.1 端点清单（概览）

| # | 方法 | 路径 | 权限 | 是否新增 | 说明 |
| --- | --- | --- | --- | --- | --- |
| R-1 | POST | `/api/v1/auth/sms/send` | **公开** | **复用**（新增 `scene=REGISTER`） | 注册验证码下发（**公开端点 1/2**） |
| R-2 | POST | `/api/v1/registration` | **公开** | 新增 | 提交注册申请（**公开端点 2/2**；`PublicEndpoints` 净新增唯一 1 条） |
| R-3 | GET | `/api/v1/registration/{applyNo}` | **ADMIN** | 新增 | **申请单详情（按申请编号）**，出参与 R-8 的 `registration` 子对象同构 |
| ~~R-4~~ | ~~POST~~ | ~~`/api/v1/registration/{applyNo}/cancel`~~ | — | **取消** | ~~本人撤回申请~~ → **U-06 定稿取消**：一期不做申请人撤回；未过审数据由超时清理（M-8）+ ADMIN 驳回替代 |
| ~~R-5~~ | ~~GET~~ | ~~`/api/v1/registration/stations`~~ | — | **取消** | ~~意向驿站选项（公开）~~ → **U-04 定稿不公开**：意向驿站选项改由前端**构建期静态配置**提供（见 §11.4 U-04 备注） |
| R-6 | POST | `/api/v1/hr/onboarding/{id}/approve` | ADMIN | 新增 | **审批通过 + 三件事联动（仅前三步）**（§4；激活见 U-01） |
| R-7 | GET | `/api/v1/hr/onboarding` | ADMIN | **复用** | 审批列表（既有） |
| R-8 | GET | `/api/v1/hr/onboarding/{id}` | ADMIN | **复用**（出参扩展 `registration` 子对象） | 审批详情 |
| R-9 | POST | `/api/v1/hr/onboarding/{id}/reject` | ADMIN | **复用** | 驳回（须同事务回写 `registration`） |

> **定稿（U-04 / U-06，依据用户裁定④「公开端点仅 2 个」+ M-6）**：公开白名单**仅**放行 **R-1（复用既有 `/auth/sms/send`，仅加 `scene=REGISTER`）** 与 **R-2（提交）** 两条 → `PublicEndpoints.all()` **净新增 1 条**（R-2；`/auth/sms/send` 既已白名单，[PublicEndpoints.java:31](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L31)）。**R-3 转 ADMIN-only**、**R-4 取消**、**R-5 取消公开**（前端静态配置）。全部扩大外部攻击面 → 公网暴露前须过 **P0.5 安全评估**（[PublicEndpoints.java:13-14](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)、[security-auth-review.md:281](security-auth-review.md) R9）。
>
> **副作用（定稿）**：R-3 退出公开后 `queryToken` 机制**一期不启用** → `employee_registration.query_token_hash` 列**保留但恒不写入**（`TODO(扩展): 申请人自助进度查询需新公开端点 + 过 P0.5`）；申请人进度告知走**线下/短信**（由 ADMIN 触发）。`9308` 错误码随之**一期不启用**（列保留）。

### 3.2 逐端点（对齐 api.md 体例）

#### R-1 注册验证码下发（复用 `/auth/sms/send`）

`POST /api/v1/auth/sms/send`（公开，**既有端点**）。入参新增取值：`scene = "REGISTER"`。

| 入参 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| scene | string | 否 | `REGISTER`（本方案新增）；**缺省（空/空白）仍按 `LOGIN`**；**非空非法值 → 400 `不支持的短信场景`（M-1 定稿：未知场景拒绝，消除 fail-open）** |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| deviceId | string | 否 | 弱信号（参与同设备维度限频） |
| captchaTicket / captchaCode | string | 否 | `captcha-enabled=true` 时必填 |

行为（**定稿**）：本场景**不要求手机号已存在**（注册者尚未成为员工）；发码前**不判定"是否已注册"**（防枚举）。频控 4 维复用，`identifier = phone`（[SmsCodeStore.java:169-175](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L169-L175)）。

> **M-2 + SEC-FULL-08 合并定稿（发码恒定性，覆盖 LOGIN 与 REGISTER 两场景，见 §11.13）**：发码接口对**任意手机号**（已注册 / 未注册 / 未绑定）返回体 `{code,message,data}`、HTTP 状态、业务码与**耗时量级一致**；未注册号作**静默成功**（不真发码），消除现有 `LOGIN` 场景 1109 差异（[AuthServiceImpl.java:264-269](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269)）。**该改造同时修掉存量 `SEC-FULL-08`**，属**既有端点行为变更**（非纯新增），须前端同步文案（统一「验证码已发送」）与回归。

响应（复用 `SmsSendVO`，[SmsSendVO.java:11-24](../hrm-server/src/main/java/com/qiujie/vo/auth/SmsSendVO.java#L11-L24)）：`{ sent, expireIn, nextAllowedIn, requireCaptcha }`。**绝不包含验证码**。

错误码：400 / 1101 / 1105 / 1106（**不出现 1109**，见上）。

> **副作用清单（须同步）**：`SmsScene` 增 `REGISTER`；`resolveScene` 未知非空值拒绝（M-1）；`SmsProperties.Aliyun` 增 `template-code-register`；`application.yml` 增键；[multi-client-architecture.md:497](multi-client-architecture.md) 场景表与 [api.md:331-386](api.md) 端准入适用路径说明同步。

#### R-2 提交注册申请

`POST /api/v1/registration`（公开，**本方案唯一净新增公开端点**）

| 入参（DTO 白名单，**M-3 定稿，字段名以 `intent*` 表达"意向"**） | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| realName | string | 是 | 2-20 字（对齐 [HrFlowServiceImpl.java:638-639](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L638-L639)） |
| phone | string | 是 | `^1[3-9]\d{9}$` |
| smsCode | string | 是 | 6 位；走 `SmsScene.REGISTER` 校验（校验成功一次性作废） |
| intentStationId | long | 是 | **仅意向**；须存在、未删除且**启用**（否则 4001 / 4004）；**不落 `employee.station_id`** |
| intentPosition | string | 否 | ≤50 字（自由文本，**仅意向**） |
| password | string | **否**（U-02 定稿：不作为员工口令；留作合规留痕，终态清散列） | 8-20 位且含字母与数字（**不进入 `employee.password` 链**） |
| agreementVersion | string | 是 | 服务条款版本（合规留痕） |

> **硬约束（M-3/S-3）**：DTO **编译期不含** `role/deptId/stationId(事实)/basicSalary/postSalary/performanceBase/allowances/status/pwdChanged`；Spring 侧 `FAIL_ON_UNKNOWN_PROPERTIES=true` → 夹带未知字段 **400**（**不复用** `HrStepCompleteRequest`）。完整字段分离表见 §11.5。

行为（**单事务**）：

1. 频控/短信校验：校验 `REGISTER` 场景验证码（1102/1103）；
2. **重复提交判定**：同 `phone` 存在 `SUBMITTED` → `9307`；
3. **已注册判定（M-2 严格形态定稿，U-03）**：`employee.phone` 活跃命中**不在响应中区分**——**仍按正常流程建单**（受理外观恒定），由 ADMIN 审批时识别（审批台提示 + M-5 唯一约束兜底，冲突则 ADMIN 驳回）；**不返回 `9310`**；
4. 建 `employee_registration`（`status=SUBMITTED`；`password_hash=BCrypt(password)`（**U-02 定稿**，仅留痕）；生成 `apply_no`；**不生成 `query_token`**，见 §3.1 副作用）；
5. 建 `hr_flow`（`flow_type=ONBOARDING`，`candidate_name/phone/station_id(=intentStationId)/position(=intentPosition)` 落值，`role=STAFF`（**恒**），`status=IN_PROGRESS`，步骤全 `PENDING`；`operator_id/operator_name = null`（U-13 定稿）+ **`hr_flow.source=SELF_REGISTER`**（V18/M-9）+ `client_ip`（脱敏留痕））；
6. 回填 `registration.flow_id`；
7. 返回 `{ applyNo, status, createTime }`（**响应对"是否已注册"恒定**）。

```json
// 请求（M-3：字段名 intent*，无 role/薪资/deptId）
{ "realName": "李四", "phone": "13912345678", "smsCode": "123456",
  "intentStationId": 3, "intentPosition": "分拣员", "password": "Init1234", "agreementVersion": "v1.0" }

// 响应（HTTP 200，对"已注册/未注册"逐字段一致）
{ "code": 200, "message": "success",
  "data": { "applyNo": "RG-20260926-0001", "status": "SUBMITTED", "createTime": "2026-09-26 10:00:00" } }
```

错误码：400 / 9307 / 4001 / 4004 / 1102 / 1103（**无 9310**）。

#### R-3 申请单详情（ADMIN，U-06 定稿：退出公开白名单）

`GET /api/v1/registration/{applyNo}`（**ADMIN**）

行为：按 `applyNo` 查申请单，出参与 R-8 的 `registration` 子对象**同构**（见下）。出参**脱敏**：`phone` 掩码；**不返回** `password_hash` / `query_token_hash`。

出参：`{ applyNo, realName, phone(脱敏), intentStationId, stationName, intentPosition, status, statusLabel, rejectReason, createTime, approveTime, source, clientIp(脱敏) }`。

错误码：400 / 404 / 9308（`9308` 一期保留未启用）。

#### ~~R-4 撤回申请（本人）~~ · 已取消（U-06 定稿）

> 依 M-6 + 用户裁定④「公开端点仅 2 个」，**一期不做申请人自助撤回**。替代：未过审数据由 **M-8 超时清理**处置；申请人如需取消，走**线下联系 ADMIN 驳回**。`TODO(扩展): 若产品后续要求自助撤回，须新增公开端点并重过 P0.5 安全评估`。

#### ~~R-5 意向驿站选项（公开）~~ · 已取消公开（U-04 定稿）

> 依 M-6 + 用户裁定④，**不新增公开驿站端点**。
> **定稿方案**：staff-h5 注册页的「意向驿站」选项由**前端构建期静态配置**提供（`apps/staff-h5` 内配置项，随驿站变更由运维/前端同步；**仅用于填单，非事实**）；**服务端**在 R-2 校验 `intentStationId` 存在 + 启用（4001/4004）。
> **备注（已降级，不采纳）**：一期"不含意向驿站的注册表单（由 ADMIN 审批定岗）"更省维护，但减少需求字段——若评审认为静态配置存在漂移风险，可退至此形态（见 §11.4 U-04 备注）。
> **残余风险**：静态配置与 `station` 表可能漂移 → 由 R-2 服务端校验兜底（漂移项直接 4001/4004，不落库）。

#### R-6 审批通过（ADMIN，聚合联动）

`POST /api/v1/hr/onboarding/{id}/approve`（ADMIN）

| 入参（**表 B：ADMIN 赋值字段**，见 §11.5） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 否 | 登录账号（缺省按 `u`+手机号规则生成，U-11 定稿；活跃唯一 1003） |
| initialPassword | string | **是** | **ADMIN 一次性初始口令**（U-02/M-4 定稿；**不复用注册密码**）；8-20 位含字母数字 |
| deptId | long | **是** | 部门（注册不采集；U-09 定稿审批时必填；不存在 → 3001） |
| stationId | long | 否 | 缺省取 `hr_flow.station_id`（意向）；停用 → 4004 |
| role | string | 否 | 缺省 `STAFF`；仅 `STATION_ADMIN` / `STAFF`（[HrConstants.java:52](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L52)） |
| position | string | 否 | 缺省取 `hr_flow.position`（意向）；**定岗时双写 `hr_flow.position` + `employee.position`（方案乙，U-07）** |
| probationMonths | int | 否 | 缺省 `hrm.hr.default-probation-months`（[HrProperties.java:22](../hrm-server/src/main/java/com/qiujie/config/HrProperties.java#L22)） |
| contractType | string | 否 | 缺省 `FIXED_TERM` |
| basicSalary / postSalary / performanceBase / allowances | — | **是**（U-10 定稿：薪资必填，防工资单静默为 0） | 定薪（`HrSalaryValidator` 校验） |
| effectiveDate | string | 否 | 缺省取 `expected_entry_date` |
| remark | string | 否 | 0-200 字 |

行为（**定稿，单事务，仅前三步副作用——M-4 不自动激活**）：

1. 事务首条 `SELECT ... FOR UPDATE` 锁 `hr_flow` 单行（§11.7 固定加锁顺序）；
2. 按既有顺序复用 `CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY` **三步**（[HrFlowServiceImpl.java:264-398](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L398) 既有方法，**不重写**）；
3. `assignForFlow` 增**岗位双写**（`employee.setPosition(...)` + `flow.setPosition(...)`，[HrFlowServiceImpl.java:361-363](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L361-L363) 现仅写 flow）；
4. 同步 `registration.status=APPROVED` + 回填 `approved_employee_id` + 清空 `registration` 凭据列；
5. **不做 `DONE`**：`hr_flow` 保持 `IN_PROGRESS`、`employee.status` 保持 `0`、`pwd_changed=0`（**待激活**）。

> **激活（U-01 定稿）**：`DONE`（转在职 `status=1`）由 ADMIN **二次显式确认**（复用既有 `completeOnboardingStep` 的 `DONE` 步骤或新端点），**审批通过不自动激活**。详见 §11.8。

错误码：400 / 404 / 3001 / 4001 / 4004 / 1003 / 9303 / 9305 / 9309。

#### R-7~R-9 复用既有端点

- **R-7** 列表：`GET /api/v1/hr/onboarding`（ADMIN），行为不变。
- **R-8** 详情：`GET /api/v1/hr/onboarding/{id}`（ADMIN），出参**新增** `registration` 子对象（`applyNo/intentPosition/agreementVersion/source/createTime/status`）+ **`employee.position` 展示（方案乙）**，供审批台展示注册来源与岗位。
- **R-9** 驳回：`POST /api/v1/hr/onboarding/{id}/reject`（ADMIN），行为**须扩展**：同事务同步 `registration.status=REJECTED` + `reject_reason` + 清空凭据列。

### 3.3 错误码（93xx 续号，定稿）

> 段位沿用 **93xx = 人事/入离职**（[api.md:88](api.md)、[ErrorCode.java:194-207](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L194-L207) 当前占用 9301~9306）。**新增从 9307 起**。

| code | 常量名 | message | 一期启用 | 说明 |
| --- | --- | --- | --- | --- |
| 9307 | `REGISTRATION_DUPLICATE` | 该手机号已有进行中的入职申请，请勿重复提交 | **是** | 重复提交 |
| 9308 | `REGISTRATION_NOT_EXISTS` | 入职申请不存在 | **否（列保留）** | 原"凭据无效"语义随 R-3 转 ADMIN-only 消失；一期不启用 |
| 9309 | `REGISTRATION_STATUS_INVALID` | 申请状态不允许该操作 | **是** | 审批/驳回时状态非法 |
| ~~9310~~ | ~~`REGISTRATION_PHONE_TAKEN`~~ | ~~该手机号已注册，请直接登录~~ | **废弃（M-2 严格形态，U-03）** | 提交段**不区分是否已注册**，故不使用；常量不新增 |

- 手机号查重命中（M-5）复用既有 **2003 `PHONE_EXISTS`**（U-15 定稿），**不新增** 9311。
- 非法短信场景复用 **400 `BAD_REQUEST`（"不支持的短信场景"）**（U-16 定稿），**不新增** 1111。
- 短信类错误复用既有 **1101 / 1102 / 1103 / 1105 / 1106**，**不新增**；**1109 在发码路径不再出现**（§11.13）。

> 联动项：`ErrorCode` 枚举、[api.md:95-142](api.md) §2.2 明细（现仅展开 10xx~50xx，93xx 尚未展开）、Mock `constants/errorCode.js` 文案同源（[ErrorCode.java:6](../hrm-server/src/main/java/com/qiujie/enums/ErrorCode.java#L6)）。

### 3.4 关键判定口径（**定稿，全部可判定**）

| 口径 | 定稿 |
| --- | --- |
| **手机号唯一性** | 以 `employee.phone` **活跃记录**为准：Service 查重（`existsActivePhone` 同口径，命中 → **2003**）**+** DB 活跃唯一约束（V16，U-05）双保险 |
| **"已注册"判定时机** | **发码环节不判定**；提交环节判定但**响应不区分**（M-2 严格形态）；已注册识别推迟到 **ADMIN 审批台**（内网可见），不构成对公网的枚举面 |
| **发码环节一致性** | 对**任意手机号**响应 `{code,message,data}` / HTTP 状态 / 业务码 / 耗时量级**逐字段一致**（同时覆盖 `LOGIN` 与 `REGISTER`，修存量 SEC-FULL-08，见 §11.13） |
| **重复提交** | 同 `phone` 且 `registration.status=SUBMITTED` → `9307`；已 `REJECTED`/`EXPIRED` 的**不阻断**新提交（另起新单） |
| **越权口径** | 一期**无申请人自助端点**：R-2 提交仅凭 `smsCode`（持有手机号）；R-3/R-8/R-9 全部 **ADMIN-only**；不得以手机号单独查询（防遍历） |
| **审批越权** | R-6/R-9 仅 `ADMIN`（与既有 hr 域 10 端点一致，[HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111)）；**站长（`STATION_ADMIN`）不可审**（U-12 定稿） |
| **激活口径** | 审批通过 → `employee.status=0`（**不可登录**）；`DONE` 二次确认 → `status=1`（U-01） |
| **初始口令** | ADMIN 一次性设定 + `pwd_changed=0` 首登强制改密；**不复用注册密码**（U-02） |
| **岗位口径** | 注册仅 `intentPosition`（意向）；审批定岗写 `employee.position`（**权威事实**）+ `hr_flow.position`（流程留痕），**以 `employee.position` 为准**（U-07/§11.9） |

### 3.5 `api.md` 更新点清单（交后端定稿；含 83 端点补录批次）

> **契约唯一真源 = `api.md`**（对齐 [tech-review-full-20260926.md](tech-review-full-20260926.md) **M1**；现有"以 Mock 为准"口径 [server-architecture.md:934](server-architecture.md) **予以废止**，改由 `api.md` 收口，Mock 降为前端脱机演示）。补录由**后端执行**，本方案只出清单/规范/批次/验收。

**A. 注册侧新增与既有域补录（本批必做）**

1. §2.1 分段表：追加 93xx 注册续号（**9307/9308/9309**；9310 废弃不列）；
2. §2.2 错误码明细：追加 9307~9309；
3. §4.0 概览：追加 R-2/R-3/R-6 行，**并修正该表"46 个"计数**（见 §11.10）；
4. 新增章节「员工自助注册」（R-1 场景扩展 / R-2 提交 / R-3 ADMIN 详情）；
5. **新建 `§4.10 人事/入离职`**：补录 **入离职 10 + 人事档案 6 = 16 端点**（R-6/R-7/R-8/R-9 与既有 10 个 hr 端点同章）；
6. §4.1.x 短信下发：补 `scene=REGISTER` 语义、**发码恒定性（SEC-FULL-08）**与端准入适用性说明；
7. §3.3 Redis Key：追加 `hrm:sms:code:REGISTER:{phone}`（沿用 [SmsCodeStore.java:35-41](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L35-L41) 命名）；
8. §1.1：补一行说明**生产前缀 `/hrm-api/v1` 由 Nginx 改写**（`api.md` 声明 `/api/v1`），避免误判（技术评审 P2-2）。

**B. 其余域补录批次（83 端点 = §4 缺口，见 §9 批次 B1-C1~C6）**

| 子批 | 域 | 端点数 | 优先级 |
| --- | --- | --- | --- |
| B1-C1 | 人事/入离职 10 + 人事档案 6 | 16 | **P0（本批，注册审批落点）** |
| B1-C2 | 财务 `payroll` 10 + 资薪规则 `payroll-rule` 5 | 15 | P1 |
| B1-C3 | KPI 9（score 4 + metric 5）+ 工单 9 | 18 | P1 |
| B1-C4 | 同步 22（task 5 + config 4 + config-center 13） | 22 | P1 |
| B1-C5 | 包裹 6 + 通知 6 | 12 | P2 |
| B1-C6 | **认证增强 6 端点补 §4.0 概览 + §4.1.x**（sms/send、sms/login、device/verify、devices、devices/{id}、captcha） | 6（概览补录，非新章） | P2 |
| — | **合计 §4 缺口** | **83** | — |

> **批次验收标准（B1-C1，交付即验）**：`api.md` 中 `§4.10` 端点行数 = **16**，与 `HrFlowController`+`HrProfileController` 方法级 `@*Mapping` 逐条可对上；差值 = 0。其余子批同法（以 Controller 注解清单为准逐条比对，差集为空）。B1-C6 验收：§4.0 概览表行数与 `AuthController` 方法级映射差集 = 0。

***

## 4. 审批通过后的联动设计

### 4.1 「三件事」的数据落点（均为既有实体，零新增业务表）

| 用户口径 | 真实落点 | 既有实现 |
| --- | --- | --- |
| 生成员工档案 | `employee`（+ `hr_profile` + **0 值 `hr_salary`** 占位） | `createEmployeeForFlow`（[HrFlowServiceImpl.java:264-338](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L338)） |
| 设置岗位/站点/角色/部门 | `employee.station_id` / `employee.role` / `employee.dept_id`；**`employee.position`（方案乙，V19 新增，权威事实）** / `hr_flow.position` / `hr_flow.role` | `assignForFlow`（[:340-373](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L340-L373)），**须增 `employee.setPosition(...)` 双写**（[HrFlowServiceImpl.java:361-363](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L361-L363) 现仅写 flow） |
| 设置薪资 | `hr_salary`（当前定薪）+ `hr_salary_log`（`change_type=ENTRY` 留痕） | `HrSalaryWriter.save(...)`（[:375-386](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L375-L386)） |

### 4.2 触发方式：聚合 vs 分步（**已定稿：A1'**）

| 方案 | 说明 | 评价 |
| --- | --- | --- |
| **A1' 聚合一次通过 + 按序推进 5 步（定稿，U-01）** | 新增 `POST /hr/onboarding/{id}/approve`，**单事务内按序复用 `completeOnboardingStep` 推进 5 步**：`SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY`（**不含 `DONE`**）；`hr_flow` 留 `IN_PROGRESS`、`employee.status=0` 待激活。**口径详见 §4.2.1** | 满足用户"三件事同时完成"；复用既有方法零重写；满足 M-4「审批不自动激活」；满足 U-01「`DONE` 为唯一剩余 PENDING 步，激活路径可执行」；**代价**：绕过既有"逐步骤 `completeOnboardingStep`"的对外形态，属**既有契约的用法扩展**（不删旧端点，旧端点保留供 PC 手工办理） |
| A2 保持逐步骤 | 审批只完成 `SUBMIT_MATERIALS`+`HR_REVIEW`；三件事由 ADMIN 在入职页逐条办理 | **不满足**用户"同时完成"；落实最快（已否决） |
| A3 状态机改造 | 把 6 步压缩为 1 步 | 破坏既有步骤契约与 Mock 契约（[hrStore.js:54-60](../hrm-clients/packages/mock/src/hrStore.js#L54-L60)），**否决** |

> **定稿说明（U-01，v1.3 修订 M2）**：A1' 触及既有 `hr_flow` 步骤机对外形态（属"设计判断与既有契约交互关系"范畴），已由 **M-4 安全结论 + 用户裁定④** 共同定稿为「A1 去掉 `DONE`」形态。~~R-6 的可选实现细节（复用 `completeOnboardingStep` 还是专设聚合服务方法）由后端在技术评审通过后定~~ → **v1.3 已收口为确定口径（见 §4.2.1）**：R-6 **必须按序推进 5 步**（逐 step 复用既有 `completeOnboardingStep`，其内部含 `markStepDone`），**不得只推进含副作用的 3 步**；否则 `DONE` 激活会被步骤守卫拒（9303），与 U-01 自相矛盾。A3 明确否决。

#### 4.2.1 R-6 步骤推进确定口径（v1.3 新增，依据 M2）

**结论：R-6 在同一事务内，按 `HrConstants.ONBOARDING_STEPS` 顺序逐 step 复用既有 `completeOnboardingStep(id, key, body)` 推进 5 步；`DONE` 不由 R-6 推进（M-4）。**

| 序 | stepKey | R-6 动作 | 代码依据 |
| --- | --- | --- | --- |
| 1 | `SUBMIT_MATERIALS` | 复用 `completeOnboardingStep` → switch 命中 default，仅 `markStepDone`（无副作用） | [HrFlowServiceImpl.java:143-152](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L143-L152) |
| 2 | `HR_REVIEW` | 同上（仅 `markStepDone`） | 同上 |
| 3 | `CREATE_ACCOUNT` | `createEmployeeForFlow`（建档）+ `markStepDone` | [HrFlowServiceImpl.java:264-338](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L264-L338) |
| 4 | `ASSIGN_STATION` | `assignForFlow`（含 `employee.position` 双写）+ `markStepDone` | [HrFlowServiceImpl.java:340-373](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L340-L373) |
| 5 | `SET_SALARY` | `salaryForFlow`（`HrSalaryWriter` 单次）+ `markStepDone` | [HrFlowServiceImpl.java:375-386](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L375-L386) |

**为何必须推进 5 步（不得只推 3 步）——代码依据：**
1. 流程建单时**全部 6 步为 `PENDING`**、`current_step_key = SUBMIT_MATERIALS`（[insertFlowWithSteps:459-470](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L459-L470)）。
2. `markStepDone` 每调用一次即置该步 `DONE` 并**重算 `current_step_key` = `firstPending(steps)`**；仅当无 PENDING 时置 `COMPLETED`（[HrFlowServiceImpl.java:474-499](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L474-L499)）。
3. 激活守卫要求**目标步 = 首个 PENDING 步**（`HrFlowStepGuard.checkOrder` → `firstPending().key().equals(targetKey)`，[HrFlowStepGuard.java:47-50](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrFlowStepGuard.java#L47-L50)）。
4. ⇒ 若只推进 3 个副作用步，`SUBMIT_MATERIALS`（step_order=1）仍 `PENDING` → 成为 `firstPending` → ADMIN 调 `DONE` 必被拒（9303，`HR_ONBOARDING_STATUS_INVALID`），**U-01 激活路径不可执行**；且审批页步骤条与已生效副作用（建档/定薪）不一致。**故 5 步全部推进为唯一自洽口径。**

**可执行性保证「审批通过 → `status=0` 建档 + 岗位双写 + 不激活」：**
- 5 步推进后 `flow.current_step_key == "DONE"`、`flow.status == "IN_PROGRESS"`（`DONE` 仍 `PENDING`，`markStepDone` 不置 `COMPLETED`，[:496-498](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L496-L498)）；
- `employee.status == 0`（建档口径不变，[:301-303](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301-L303)）；`employee.position`（权威）+ `flow.position` 双写；
- 后续 ADMIN 调 `completeOnboardingStep(DONE)` → 守卫通过（首个 PENDING 即 `DONE`）→ `finishOnboarding` 置 `status=1`（[HrFlowServiceImpl.java:389-398](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L389-L398)）。
- **实现约束（供后端 B4 遵守）**：逐 step 复用 `completeOnboardingStep`，各步 `HrStepCompleteRequest` 按需带 `deptId/stationId/position/role/薪资四项/initialPassword`；`SUBMIT_MATERIALS`/`HR_REVIEW` 用空 body（`validateStepRequest` 对 key 无字段强校验，[HrFlowServiceImpl.java:686-702](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L686-L702)）。**R-6 不得调用 `DONE`**（M-4）。**实现细节若偏离（如专设聚合方法），须同步满足上述 4 项不变式。**

### 4.3 事务边界

- **一个事务**：`@Transactional(rollbackFor = Exception.class)`（对齐既有全程用法，[HrFlowServiceImpl.java:107](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L107)）。
- 事务内顺序（**定稿，v1.3 修订 M2**）：`hr_flow FOR UPDATE` → 按序推进 5 步 `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT`（+ M-5 phone 查重）`→ ASSIGN_STATION`（**含 `employee.position` 双写**）`→ SET_SALARY`（每步复用 `completeOnboardingStep`，内部含 `markStepDone`，见 §4.2.1）→ `registration.status=APPROVED` → 清空 `registration` 凭据列。**不含 `DONE`**（M-4，见 §11.2；`DONE` 为推进后唯一剩余 `PENDING` 步，供 U-01 激活）。
- **为什么必须同事务**：`SET_SALARY` 前置依赖 `CREATE_ACCOUNT` 写入的 0 值 `hr_salary` 行（[:326-337](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L326-L337)）；`ASSIGN_STATION` 依赖 `employeeId` 已回填。任一步失败若部分提交，将产生"有员工无薪资/有账号未在职"的半成品。

### 4.4 失败处理（部分成功）

| 失败点 | 行为 |
| --- | --- |
| 建档失败（账号重复 1003 / 部门、驿站非法 3001/4004） | 抛 `BusinessException` → **整单回滚**；`hr_flow` 保持 `IN_PROGRESS`，`registration.status` 保持 `SUBMITTED`；前端提示具体字段，审批人修正后重试 |
| 定薪非法（`HrSalaryValidator` 拒绝，[HrFlowServiceImpl.java:697-701](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L697-L701)） | 同上，整单回滚 |
| `registration` 回写失败 | 同事务，随主流程回滚 |

> **结论**：采用"**全成功或全回滚**"，**不存在**需人工补偿的部分成功态。响应不回滚的只是 HTTP 层提示。

### 4.5 幂等与并发

| 场景 | 设计 |
| --- | --- |
| 重复审批（同一单点两次） | 复用既有守卫：`hr_flow.status != IN_PROGRESS` → `9303`（[:161-164](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L161-L164)）；新建档时 `flow.employeeId != null` 亦拒（[:265-267](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L265-L267)）；再加 `registration.status != SUBMITTED` → `9309` |
| 并发审批（两请求同时进） | **定稿**：R-6/R-9 进入事务后**首条**对 `hr_flow` 行 `SELECT ... FOR UPDATE`（固定加锁顺序，见 §11.7），消除并发双建/双定薪；属必改项（原 §8-R3），已并入 B4 验收 |
| 审批 vs 驳回/超时并发 | 均以 `registration.status=SUBMITTED` 为状态守卫（其一必失败），并在同事务内**先锁 `hr_flow` 行**再写两表状态 |
| 「三件事」本身幂等 | 建档/定薪/分配均由流程状态守卫覆盖；`SET_SALARY` 走 `HrSalaryWriter` **非幂等**（已核：`update`+`insert` 无幂等键，[HrSalaryWriter.java:57-105](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L105)）→ **必须在同一事务内仅调用一次**，由 FOR UPDATE 行锁 + 状态守卫保证（§11.7） |

### 4.6 薪资与岗位信息由谁提供（**定稿**）

**结论：申请时填「意向」（`intent*`），审批时由 ADMIN 定「事实」。**

| 项 | 申请时（申请人可控） | 审批时（ADMIN 定） | 理由 |
| --- | --- | --- | --- |
| 姓名 / 手机号 | ✅ 填 | 只读（不可静默改写） | 注册事实 |
| 意向驿站 `intentStationId` | ✅ 填（`registration.apply_station_id`） | 可改（`stationId`）；**须再审存在+启用（4004）** | 意向 ≠ 定岗；亦防停用驿站 |
| 意向岗位 `intentPosition` | ✅ 填（`registration.apply_position`） | 可改（`position`）；**双写 `employee.position`（权威）+ `hr_flow.position`** | 意向 ≠ 定岗；方案乙（U-07） |
| 部门 `deptId` | ❌ 不填 | ✅ **必填**（U-09） | `CREATE_ACCOUNT` 强依赖 |
| 角色 `role` | ❌ **不可填** | ✅ 可授（默认 `STAFF`，白名单；U-12） | **安全红线**：防注册注入 `STATION_ADMIN`（§5.4） |
| 薪资 | ❌ **不可填** | ✅ **必填**（U-10） | **安全红线**：防注册自定薪（§5.4） |
| 密码 | ~~✅ 自设（作初始口令）~~ → **不采集或仅留痕**（U-02） | ✅ **ADMIN 一次性口令**（`initialPassword`，必填） | M-4：不复用注册密码 |
| 登录账号 `username` | ❌ 不采集 | ✅ 缺省 `u`+手机号，可覆盖（U-11） | — |

***

## 5. 权限与安全边界

### 5.1 未鉴权入口的收敛口径

| 面 | 收敛措施（定稿） |
| --- | --- |
| **边缘限速（M-7，必做）** | Nginx 对 `/auth/sms/send`（含注册）加 `limit_req zone`（按 `$binary_remote_addr`）+ 对 `/api/` 加全局 `limit_conn`；**只增不改既有站点配置**（用户裁定④已授权；执行归运维 B/C 档）。应用层在既有五维频控外**增**「手机号×全局日上限」与「**注册提交日上限**」（S-2 并入 M-7） |
| **应用频控** | 复用 `hrm.sms.*` 五维限频（手机号/IP/设备/账号 + 全局，[SmsCodeStore.java:93-115](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L93-L115)）。`hrm.sms.ip-hourly-limit` 对公开注册尤为关键；**IP 维度可信性**由 M-7 的 Nginx `$remote_addr` 限速兜底（应用层 `IpUtil` 改造见 §11.3 S-4，独立批） |
| **发码恒定性（M-2 + SEC-FULL-08）** | 发码响应对任意手机号**逐字段一致**（消除 1109/存在性差异），同时覆盖 `LOGIN` 与 `REGISTER`；未注册号静默成功。详见 §11.13 |
| **验证码为提交前置** | R-2 必须先过 `REGISTER` 验证码；无有效码不得提交（阻断"无成本刷单"） |
| **字段白名单（M-3/S-3）** | R-2 入参仅 §11.5 表 A 的 7 字段（`intent*` 命名）；**禁止**接受 `role/deptId/stationId(事实)/salary/pwdChanged/status` 等；独立 DTO + `@Valid` + 长度上限 + 枚举白名单 + 手机号正则 + `FAIL_ON_UNKNOWN_PROPERTIES`（**不用** `HrStepCompleteRequest`） |
| **出参白名单** | R-3/R-8 出参**脱敏手机号**、不回 `password_hash`/`query_token_hash`；申请管理端点 ADMIN-only（M-6） |
| **幂等与防重放** | 验证码一次性作废（[SmsCodeStore.java:64-66](../hrm-server/src/main/java/com/qiujie/service/auth/support/SmsCodeStore.java#L64-L66)）；重复提交 `9307`；审批行锁 §11.7 |
| **凭据形态** | `queryToken` 机制**一期不启用**（R-3 转 ADMIN-only，U-06）；`query_token_hash` 列保留恒 NULL；验证码/初始口令**绝不回显**（[SmsSendVO.java:8](../hrm-server/src/main/java/com/qiujie/vo/auth/SmsSendVO.java#L8)） |
| **日志红线** | 注册请求/响应体**不入** `client_log`；验证码/密码/queryToken 不进任何日志；`ClientLogSanitizer` 凭据正则**补** `code=|smsCode=|verifyCode=|captcha=|otp=` 形态（SEC-FULL-14 / 安全 §4.4⑦；注意不误擦业务码 `code=200`，按长度收敛） |

### 5.2 申请数据的可见范围

| 角色 | 可见 |
| --- | --- |
| 申请人本人 | **一期无自助端点**（R-3/R-4 已取消/转 ADMIN，U-06）；进度由 ADMIN 线下/短信告知，`TODO(扩展)` 见 §3.1 |
| ADMIN | 所有注册申请 + 审批单（R-3 按 `applyNo`、R-7 列表、R-8 详情） |
| STATION_ADMIN / STAFF | **不可见**（无对应端点，M-6） |

> 申请数据含**未加密的姓名/手机号**（出参脱敏），**不含**薪资（薪资仅审批时产生）。合规上应登记个人信息处理说明并明确保留期限（**U-14 定稿：已拒绝/超期保留 N=30 天（配置项）+ 终态清凭据**）。

### 5.3 审批权限

**仅 `ADMIN`（定稿，U-12）**。理由：既有 hr 域全部端点仅 ADMIN（[HrFlowController.java:39-111](../hrm-server/src/main/java/com/qiujie/controller/hr/HrFlowController.java#L39-L111)）；开放站长审批须新设计两级审批（同 [leave_request](db.md) 的 `PENDING_STATION/PENDING_BOSS`），属**范围扩张**，**已否决**。`TODO(扩展): 如需站长初审，另立方案 + 用户口径确认`。

### 5.4 可控字段与审批后赋值字段的**严格分离**（防注册注入）

| 类别 | 字段 | 谁能写 |
| --- | --- | --- |
| **申请人可控（注册时）** | `real_name`、`phone`、`apply_station_id`(意向)、`apply_position`(意向)、`password`(仅留痕，不作口令)、`agreement_version` | 注册接口（公开，R-2） |
| **仅审批人可赋（审批时）** | `employee.username`、`employee.dept_id`、`employee.station_id`(事实)、`employee.role`、**`employee.position`（方案乙）**、`hr_salary.*`、`hr_profile.*`、`pwd_changed`、`status` | 审批接口（ADMIN，R-6） |

**硬约束**：

1. 注册 DTO **不得**包含任何"仅审批人可赋"字段（编译期即隔离，M-3）；
2. 建档时 `employee.role` **恒取** `"STAFF"`（对齐既有 `createOnboarding` 的 `flow.setRole("STAFF")`，[:119](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L119)），`STATION_ADMIN` 只在 `ASSIGN_STATION` 由审批人显式授予；
3. 注册**不写** `hr_salary`（虽 `CREATE_ACCOUNT` 会落 0 值占位，但那是**审批时**产生，非注册时）；
4. `intentStationId` 在注册阶段仅为"意向"，落地员工前须**再审**存在性/启用状态（复用 `STATION_DISABLED` 4004）；
5. **`employee.position` 仅由审批人写**（R-6）；注册侧 `apply_position` 不得直通 `employee.position`（防岗位注入）。

***

## 6. NFR

| 维度 | 结论 |
| --- | --- |
| **性能** | 影响面**极小**：注册为低频写（单人一次），`employee_registration` 与 `employee` 同量级（< 5000 行）；公开端点无列表/无聚合查询。唯一需关注的是 **`send` 端点的短信成本**（非性能，是成本面）——由五维频控 + Nginx 限流约束。BCrypt 每请求 1 次（cost=10 ≈ 数十 ms），可接受 |
| **可用性** | **短信通道不可用**：`provider=none`（降级）时 `LoggingSmsSender` 只记日志、验证码无人可取 → **注册事实上不可用**；生产 `provider=aliyun` 且凭据缺失则启动期 fail-fast（[SmsProperties.java:22-26](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L22-L26)）。投递失败回 `1105`（[AuthServiceImpl.java:308-312](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L308-L312)）。**降级必须 fail-closed**，不得弱兜底为固定码（[security-auth-review.md:246](security-auth-review.md) §4.4⑥）。生产 `dev-fixed-code`/`dev-universal-code` 均被 `SmsConfigGuard` 禁（[SmsProperties.java:74-99](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java#L74-L99)） |
| **可观测性** | 关键流转 `info`：注册提交（`apply_no` + 脱敏手机号）、审批通过（`flow_no` + `employee_id`）、驳回/超时；`warn`：短信投递失败、频控命中。**验证码/密码/queryToken 一律不入日志**。审批留痕复用 `hr_flow_step.operator_id/operator_name/operate_time`（[db.md:794](db.md)） |
| **可维护性** | **复用最大化**：审批复用 `hr_flow` 步骤机与既有 10 端点；短信复用 `SmsSender`/`SmsCodeStore`（仅增 1 个 scene）；错误码复用段位。**仅 1 张新表**（`employee_registration`）；结构变更 = `employee.phone` 活跃唯一 + `hr_flow.source` + `employee.position`（V16/V18/V19）。参数（TTL/频控/超时天数/试用期/保留期）全部外置（[SmsProperties](../hrm-server/src/main/java/com/qiujie/config/SmsProperties.java)、[HrProperties](../hrm-server/src/main/java/com/qiujie/config/HrProperties.java)） |
| **一致性** | 审批联动**强一致（单事务）**；注册提交亦**单事务**（申请表 + 流程表） |

***

## 7. 兼容与迁移（定稿：V16~V19）

> **现最大版本 = `V15`**（实测，**仅 `mysql/` 目录** `V1~V15`）。~~`mysql/` 与 `postgresql/` 双目录~~（**v1.2 表述错误，已推翻**：实测 `postgresql/` 仅 `V1`、`V2`）。**PostgreSQL 迁移目录自 V2 起冻结，本期仅产出 mysql 脚本与 mysql 快照，与 [db.md:8](db.md)/[db.md:401](db.md) 口径一致**（v1.3 更正，依据 M1）。**本方案续号 V16~V19，各一份 mysql 脚本；严禁修改任何历史脚本**（[智能体调度规则 §5 L4](.trae/rules/智能体调度规则.md)）。

| 版本 | 脚本名（**仅 mysql**） | DDL 要点 | 实现要点（仅 mysql） | 回滚 | C 档 |
| --- | --- | --- | --- | --- | --- |
| **V16** | `V16__employee_phone_unique.sql` | `employee.phone` **活跃唯一**（M-5 前置，先于注册上线） | 生成列 `phone_active VARCHAR(20) GENERATED ALWAYS AS (IF(is_deleted=0, phone, NULL)) STORED` + `UNIQUE INDEX uk_employee_phone_active(phone_active)`（NULL 可重复 → 已删号可复用） | `DROP INDEX` + `DROP COLUMN phone_active` | **是** |
| **V17** | `V17__employee_registration.sql` | `CREATE TABLE employee_registration`（§2.2 字段；`UNIQUE(apply_no)`（U-17）+ 3 普通索引） | BIGINT `AUTO_INCREMENT`、`DATETIME`、`TINYINT`、`COMMENT`（对齐既有 mysql 脚本体例） | `DROP TABLE` | **是** |
| **V18** | `V18__hr_flow_source.sql` | `hr_flow` 加 `source VARCHAR(16) NOT NULL DEFAULT 'ADMIN'`（M-9；存量行回填默认 `ADMIN`） | **不加索引**（低基数） | `DROP COLUMN source` | **是** |
| **V19** | `V19__employee_position.sql` | `employee` 加 `position VARCHAR(50) NULL`（**方案乙，U-07/用户裁定①**） | **不加索引** | `DROP COLUMN position` | **是** |

**迁移顺序与依赖**：`V16`（既有缺陷修复，**与注册解耦、可先行**）→ `V17`（新表）→ `V18`（M-9 来源标识）→ `V19`（方案乙岗位）。**V16 为其他项的前置**（M-5 硬项：唯一约束先于注册上线）。

**迁移安全性与前置**：
- **V16（既有表结构变更）**：执行前须跑**存量重复号预检**（SQL 见 §11.6②）**必须返回 0 行**；非 0 → 先人工去重（**数据变更，C 档 + 人工确认**，安全 R-5）；执行前**备份**。
- **V17 纯 `CREATE TABLE`**（无既有表变更、无回填，风险最低）。
- **V18 为既有表加列**：`NOT NULL DEFAULT` 需 MySQL 8 在线 DDL 支持（[V13 先例](../hrm-server/src/main/resources/db/migration/mysql/V13__parcel.sql#L47-L68)）；执行前备份。
- **V19 为既有表加列**（`NULL` 允许）：**无回填**（存量员工 `position` 保持 NULL，见 §11.9「历史回填」）。
- 所有脚本**注释附回滚语句**（[db.md:494](db.md) §8.0-5）。

| 同步项 | 结论 |
| --- | --- |
| **快照同步** | **仅同步 `sql/schema/mysql/init.sql`**（唯一同步点）；~~`sql/schema/postgresql/init.sql`~~ **已冻结不再刷新**（[db.md:401](db.md)）：`employee` 加 `position`/活跃唯一、`hr_flow` 加 `source`、新增 `employee_registration`；**表总数 38 → 39** |
| **`db.md` 更新** | ① `§3.3 employee`（[db.md:155](db.md)）加 `position` 列 + 活跃唯一索引说明；② `§8.5 人事域`（[db.md:766](db.md) 附近 `hr_flow`）加 `source` 列；③ 新增「`employee_registration`（V17）」小节（建议 §8.5 续号或新章）；④ 头部表总数 38→39（[db.md:22](db.md)） |
| **`api.md` 更新** | 见 §3.5（A 组 8 项 + B 组 6 子批） |
| **`multi-client-architecture.md`** | §4.3 场景表、§4.4.3 键规范补 `REGISTER`（[multi-client-architecture.md:497](multi-client-architecture.md)） |
| **`update-log.md`** | 按章节追加一行（[智能体调度规则 §12 M02]） |
| **Mock 同步** | **需要**，但**联调基线以真实后端为准**（Mock 只做前端脱机演示）。需同步：`packages/mock/src/routes/auth.js`（`scene=REGISTER` + 发码恒定化）、新增 `registration` 路由与 store、`constants/errorCode.js`（**9307/9309**；9310 废弃）、`hrm-demo` 同名副本、`employee` VO 补 `position` |
| **前端** | ① staff-h5：注册页/路由 + 登录页入口（登录页仅 `sendSms` 已存在，[auth.js:15](../hrm-clients/apps/staff-h5/src/api/auth.js#L15)，需扩 api 层）+ **发码文案统一改为「验证码已发送」**（SEC-FULL-08）；② PC 审批页（`web/src/views/onboard/index.vue`）读 `registration` 子对象 + 新增「审批通过」表单（含 `initialPassword`/`deptId`/薪资必填）+ **岗位展示改造**（§11.9） |
| **不兼容项** | 均为新增端点/新增列/新增表，**无删除/改类型**。两处**语义/行为扩展**须显式登记：① `hr_flow.status=REJECTED` 复用承载"超时/驳回"（§1.2 已修正：**不再承载"撤回"**，因 R-4 取消）；② **`POST /auth/sms/send` 对未注册手机号由 1109 改为恒定受理外观**（SEC-FULL-08，属**既有端点行为变更**，前端须同步）。 |

***

## 8. 风险与假设登记

### 8.1 口径类裁定（**已全部定稿，无待裁定项**）

> **v1.2 定稿**：原 §8.1 Q1~Q13 已去重合并进 **§11.4 裁定结果表（U-01~U-17）**，并**按用户裁定⑤逐条定稿**。本节只保留映射索引，**不再列"待裁定/替代方案"**——完整裁定结果与依据见 §11.4。

| 原编号 | 映射至 | 定稿结论（详见 §11.4） |
| --- | --- | --- |
| Q1 | **U-01** | `DONE` 二次人工确认才激活（审批仅前三步） |
| Q2 / Q3 | **U-02** | 不复用注册密码；ADMIN 一次性口令 + `pwd_changed=0` |
| Q4 | **U-11** | `username` 缺省 `u`+手机号，ADMIN 可覆盖 |
| Q5 | **U-12** | 仅 ADMIN 审批（站长不可审） |
| Q6 | **U-06** | R-3 转 ADMIN-only、R-4 取消 |
| Q7 | **U-03** | M-2 严格形态：提交不区分「是否已注册」，`9310` 废弃 |
| Q8 | **U-07** | **方案乙**：新增 `employee.position`（用户裁定①，推翻原"方案甲"建议） |
| Q9 | **U-09** | `deptId` 审批时必填 |
| Q10 | **U-10** | 审批时薪资必填 |
| Q11 | **U-17** | `apply_no` 增设 `UNIQUE` |
| Q12 | **U-13** | `operator=null` + `hr_flow.source=SELF_REGISTER` + 来源 IP/UA/时间 |
| Q13 | **U-14** | 已拒绝/超期保留 **30 天**（配置项）+ 终态清凭据 |
| — | U-04 | R-5 取消公开；意向驿站改前端静态配置 |
| — | U-05 | `employee.phone` 活跃唯一（**仅 mysql**：生成列 + `UNIQUE`；PG 冻结不产出） |
| — | U-08 | `hr_flow.source` 独立脚本 V18、与注册同批 |
| — | U-15 | 手机号查重复用 `2003` |
| — | U-16 | 非法短信场景复用 `400` |

> **实现闸门**：口径已定稿，**技术评审通过后即可实现**（P0.6/L8）。任何后续口径变更仍须回到 §11.4 登记并**重评**。

### 8.2 安全风险（**须网络安全工程师评估，P0.5**）

| # | 风险 | 依据 | 处置（定稿） |
| --- | --- | --- | --- |
| S1 | **净新增 1 个公开端点（R-2）+ 1 个公开场景（`scene=REGISTER`）**，扩大攻击面（枚举/刷量/短信轰炸/成本放大） | [PublicEndpoints.java:13-14](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L13-L14)、[security-auth-review.md:281](security-auth-review.md) R9 | **公网暴露前必须过 P0.5**，结论 + 授权表单归档；未过不得暴露。公开端点**仅 2 个**（R-1 复用 + R-2 新增） |
| S2 | **存量：`/auth/sms/send` 可枚举在职手机号（SEC-FULL-08）** | [security-full-review-20260926.md](security-full-review-20260926.md) SEC-FULL-08、[AuthServiceImpl.java:264-269](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269) | **与 M-2 合并**：发码响应对任意手机号恒定（覆盖 LOGIN + REGISTER），消除 1109；见 §11.13 |
| S3 | **短信轰炸/成本放大** | 安全 §3.5 链 A | M-7 双层限流（Nginx `limit_req` + 全局 `limit_conn` + 应用层全局日上限）+ M-1 独立场景；图形码（S-1）**本批不做**、登记残余 R-3 |
| S4 | **验证码明文红线** | [security-auth-review.md:246](security-auth-review.md) §4.4⑦ | 复用 `SmsSendVO`（无码）；`ClientLogSanitizer` 正则**补**验证码形态（SEC-FULL-14，M-8 同批） |
| S5 | **密码/凭据残留** | 本方案 | 终态清空 `password_hash/query_token_hash`（§2.2）；`query_token_hash` 一期恒 NULL |
| S6 | **注册注入角色/薪资/站点** | 本方案 | 字段白名单 + DTO 隔离（M-3，§5.4/§11.5） |
| S7 | **意向驿站选项来源（R-5 取消公开后）** | 本方案 | 前端**构建期静态配置**；服务端 R-2 再审 `intentStationId` 存在+启用（4001/4004）兜底漂移；残余登记 §11.9/§8.3-E8 |
| S8 | **登录前无频控**（公开注册同属"登录前"面） | [security-auth-review.md:115](security-auth-review.md) SEC-AUTH-03 | M-7 至少一层限流（Nginx + 应用） |
| S9 | **未强制 HTTPS / 首登强制改密未服务端强制** | SEC-AUTH-02 / R6；[security-full-review-20260926.md](security-full-review-20260926.md) SEC-FULL-03/09 | 本方案不改变其状态，但**注册依赖其闭环**（M-4 依赖首登强制改密）→ 列为公网放行前关联必闭环项（§11.14） |

### 8.3 工程风险

| # | 风险 | 处置（定稿） |
| --- | --- | --- |
| E1 | 并发审批无行锁（§4.5） | **定稿**：`SELECT ... FOR UPDATE` 锁 `hr_flow` 行 + 固定加锁顺序（§11.7）；列为**必改项**，并入 B4 验收 |
| E2 | 复用 `hr_flow` 承载审批，需回写 `registration`（驳回/超时两处；撤回已取消） | 两处均须同事务回写，漏一处即状态漂移；测试须覆盖状态矩阵 |
| E3 | `HrSalaryWriter` **非幂等**（已核，§11.7） | 幂等责任在流程状态守卫（FOR UPDATE + 状态校验）；R-6 保证事务内**仅调用一次** |
| E4 | `registration.flow_id` 与 `hr_flow.employee_id` 双向关联的一致性 | §4.5 守卫中同时校验两表状态（`hr_flow IN_PROGRESS` + `registration SUBMITTED`） |
| E5 | 既有 `hr_flow.status` 复用 `REJECTED` 承载"超时" | 文档显式登记（§1.2）；若不接受须扩枚举（契约变更，另评） |
| **E6（方案乙新增）** | **`employee.position` 与 `hr_flow.position` 双写一致性** | **定稿**：以 `employee.position` 为权威事实；仅在 `assignForFlow` 一处双写（同方法、同事务），禁止他处单独写；**审批历史单可改写 `flow.position`**，但员工事实以 `employee.position` 为准（§11.9） |
| **E7（方案乙新增）** | **存量员工 `position` 为 NULL（历史空值）** | 定稿**不回填**；前端以「—」展示；如需回填，另立 C 档脚本（从最近 `COMPLETED` 的 ONBOARDING 单取 `flow.position`）并须授权（§11.9） |
| **E8（方案乙新增）** | **前端改造面扩大**（员工档案/列表/导出/Mock/审批页） | 纳入 B6 验收；导出模板新增「岗位」列；Mock `toEmployeeVO` 同步补 `position`（§9 B6/§11.9） |
| **E9（M-3 新增）** | **R-2 入参字段改名 `stationId→intentStationId`/`position→intentPosition`** | staff-h5 注册页**尚未实现**（无存量调用），改名零兼容成本；Mock 同步；`TODO(扩展): 若未来有外部调用方须版本化` |

### 8.4 技术评审补漏风险（G 系列，v1.3 新增，依据 M4）

> 来源：[tech-review-registration.md](tech-review-registration.md) §2.5 / 必改项 4。逐条登记与处置，**结论绑定 v1.3**。

| # | 遗漏风险（评审登记） | 处置（v1.3） | 状态 |
| --- | --- | --- | --- |
| **G1** | R-6 未推进 `hr_flow_step` → 激活路径被守卫阻断（9303）/ 步骤条与副作用不一致 | **已定稿闭环**：R-6 按序推进 5 步口径（§4.2.1）；B4 增可判定验收（§9） | **已闭环**（M2） |
| **G2** | PG 目录冻结与「双库脚本」要求冲突（未登记） | **已消解**：改「仅 MySQL」路径并显式声明 PG 冻结（§0.4/§7），与 `db.md:8/401` 一致；本行登记为已闭环 | **已闭环**（M1） |
| **G3** | 提交段 `9307`（`REGISTRATION_DUPLICATE`，§3.4 错误码明细）仍是「该手机号存在进行中注册」的存在性 oracle（**低危**：利用需先持有有效短信码，即须实际持有该号） | **接受为残余风险**：`9307` 为**幂等/防重放**所必需（同一号重复提交须可区分），且触发前提是**已通过 `REGISTER` 场景发码校验**；相较 `1109`（发码段枚举，M-2 已消除）风险面显著收窄。登记为残余，`TODO(扩展): 若需进一步收窄，可改为「静默受理 + 审批台去重」（属口径变更，须用户确认）` | **登记为残余** |
| **G4** | V16 预检 SQL 对 `NULL phone` 误报 | **已修复**：预检条件补 `AND phone IS NOT NULL`（§11.6②，M3） | **已闭环**（M3） |

> **与 E/S/R/T 系列的编号不冲突**：G 系列为**评审补漏专用**，不复用 E/S/R/T 编号。

***

## 9. 总实施批次表（B0~B7 与 api.md 补录批次合并 · 定稿）

> **依赖顺序**：安全基线 → 契约补录 → 数据库 → 后端 → UI/UX → 前端 → 测试/部署。每批**可独立验收**。
> **C 档动作**（迁移执行、Nginx 配置变更、部署、存量去重）须主智能体按 §10.3 三步表单授权；**Nginx 限速为「只增不改」**（用户裁定④）。
> **后端批次验收口径（tech-review M5）**：本机**无 JDK/Maven/MySQL/Redis** → 编译/单测/迁移**收敛到服务器**实跑（`mvn -q clean package` + `mvn test` + Flyway 空库迁移），结果归档 `docs/`；本地只做静态审查。

| 批次 | 内容 | 依赖 | 验收标准（可判定） | C 档 |
| --- | --- | --- | --- | --- |
| **B0 安全基线** | 安全评估结论已产出（`security-registration-review.md`＝阻断；`security-full-review-20260926.md` 存量项）；**M-1~M-9 全部纳入 B2~B4/B7（无"后续做"）**；SEC-FULL-08/18 与 M-1/M-2 合并（§11.13） | 本方案（R25 评审后） | 评估报告存在且结论明确；**M 项 100% 落到本表批次**（逐条对得上）；B7 公网暴露前须 **S-6 复验** | 否 |
| **B1 契约补录（拆 C1~C6）** | 见下 6 子批（后端写入 `api.md`） | B0 | `api.md`（契约唯一真源）覆盖：**C1 必录 16 + C2 15 + C3 18 + C4 22 + C5 12 = 83 端点**，另 C6 概览补 6 行；各子批以 Controller 方法级注解逐条比对，**差集 = 0** | 否 |
| **B1-C1（P0，本批）** | 人事/入离职 10 + 人事档案 6 = **16 端点**（新建 `§4.10`）+ 注册侧 R-1/R-2/R-3 + 错误码 9307~9309 + §1.1 前缀说明 | B0 | `§4.10` 行数 = **16**；与 `HrFlowController`+`HrProfileController` 方法级映射差集 = 0；`PublicEndpointsTest` 快照回归更新 | 否 |
| **B1-C2（P1）** | 财务 `payroll` 10 + 资薪规则 `payroll-rule` 5 = **15** | B1-C1 | 与 `PayrollController` + 资薪规则 Controller 映射差集 = 0 | 否 |
| **B1-C3（P1）** | KPI 9（score 4 + metric 5）+ 工单 9 = **18** | B1-C1 | 与对应 Controller 映射差集 = 0 | 否 |
| **B1-C4（P1）** | 同步 22（task 5 + config 4 + config-center 13） | B1-C1 | 同上 | 否 |
| **B1-C5（P2）** | 包裹 6 + 通知 6 = **12** | B1-C1 | 同上 | 否 |
| **B1-C6（P2）** | 认证增强 6 端点补 §4.0 概览 + §4.1.x（sms/send、sms/login、device/verify、devices、devices/{id}、captcha） | B1-C1 | §4.0 概览行数与 `AuthController` 映射差集 = 0 | 否 |
| **B2 数据库** | **V16** `employee_phone_unique`（M-5 前置）+ **V17** `employee_registration` + **V18** `hr_flow_source`（M-9）+ **V19** `employee_position`（方案乙）；**快照同步（38→39 表，仅 `sql/schema/mysql/`）** + `db.md` 4 处更新 + 回滚语句 | B1-C1 | **仅 mysql 脚本齐（pg 冻结，不产出 pg 脚本/pg 快照，与 `db.md:8/401` 一致）**；**未改任何历史脚本**；快照与迁移一致（表/索引逐条对齐）；每脚本含回滚语句；**V16 预检返回 0 行**方可执行 | **是** |
| **B3 后端-注册侧** | 新表实体/Mapper/Service/Controller（R-2/R-3）；**M-1**（`SmsScene.REGISTER` + `resolveScene` 未知非空拒绝）；**M-2 + SEC-FULL-08**（发码恒定化，覆盖 LOGIN+REGISTER）；**M-3**（独立 DTO `intent*` + `FAIL_ON_UNKNOWN_PROPERTIES`）；**M-6**（`PublicEndpoints` **净增 1 条**）；**M-7 应用层**（全局日上限 + 提交日上限）；**M-8**（保留期配置 + 清理任务 + 出参脱敏）；`ErrorCode` 9307/9309（9310 不新增） | B2, B1-C1 | 服务器 `mvn test` 通过；单测覆盖：验证码前置、`9307`、非法 scene→**拒**（非 LOGIN）、发码恒定（两组各 ≥50 次逐字段一致，无 1109）、未知字段→400、R-5 白名单、清理任务、`PublicEndpointsTest` 更新 | 否 |
| **B4 后端-审批联动** | R-6 聚合（**按序推进 5 步** `SUBMIT_MATERIALS→HR_REVIEW→CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY`，**不含 `DONE`**，M-4；口径见 §4.2.1）；`assignForFlow` **岗位双写**（方案乙）；R-9 驳回回写；R-8 出参扩展；**M-5 Service 查重 + 建档 phone 查重**；R-6/R-9 `SELECT ... FOR UPDATE` + 固定加锁顺序；**M-9** 审批留痕 | B3 | 服务器 `mvn test` 通过；单测：三件事同事务（失败全回滚）、`employee.status==0`（不激活）、`employee.role=='STAFF'`、`employee.position` 双写一致、重复审批 `9303`/`9309`、并发守卫、终态清凭据、phone 冲突 `2003`；**step 状态核对：R-6 后 5 个步骤状态均 = `DONE`、`flow.current_step_key=='DONE'`、`flow.status=='IN_PROGRESS'`、`DONE` 仍为 `PENDING`；激活（首次登录）成功：ADMIN 调 `completeOnboardingStep(DONE)` 不被守卫拒（无 9303）→ `employee.status==1`** | 否 |
| **B5 UI/UX** | 注册页与「申请进度（ADMIN 侧）」设计规范（Tokens 复用、四态、对比度；**含「岗位」字段展示规范**） | B1-C1 | 设计规范冻结（Tokens 明确）；四态（loading/empty/error/normal）齐；对比度 ≥ 4.5:1 | 否 |
| **B6 前端** | ① staff-h5：注册页 + 登录页入口 + api 层 + **发码文案统一「验证码已发送」**；② PC：审批页「审批通过」表单（`initialPassword`/`deptId`/薪资必填）+ `registration` 展示 + **员工档案/列表/导出「岗位」展示改造（方案乙）**；③ Mock 同步 | B4, B5 | `build`/`lint` 通过；按 Tokens 还原；四态覆盖；`vitest` 通过；员工列表/导出含「岗位」列、空值显示「—」 | 否 |
| **B7 测试与部署** | 接口/E2E + 门禁实跑；**M-7 Nginx**（`limit_req`/`limit_conn`，只增不改）；**存量重复号去重执行**（若预检非 0）；部署（C 档）；**S-6 安全复验** | B6, B0 | 用例覆盖 §3.4 全部口径且回归不弱化；Nginx 压测：单 IP 高频→429/1101、伪造 XFF 不影响（`$remote_addr`）；**B0 复验结论为「可放行」且 M-1~M-9 + SEC-FULL-08/18 全闭环**方可公网暴露 | **是** |

> **批次可并行性**：B1-C2~C6 与 B2 可并行（无产物依赖）；B1-C1 必须先于 B2/B3（契约先行 P5）；B3/B4 串行；B5 与 B2/B3/B4 可并行（写入路径不重叠）。**并发写入同一工作区仍须单一写者**（[智能体调度规则 §12 M04]）。
>
> **M-1~M-9 → 批次映射（可逐条核对）**：M-1→B3；M-2→B3；M-3→B3；M-4→B4；M-5→B2(V16)+B4(Service)；M-6→B3；M-7→B3(应用)+B7(Nginx)；M-8→B3；M-9→B2(V18)+B4(留痕)；**S-6→B7**。

***

## 10. 遗留与 TODO(扩展)

| # | 项 | 说明 |
| --- | --- | --- |
| T1 | `HrSalaryWriter.save` 幂等性 | **已核实（§11.7）**：`save()` = `updateById`（覆盖）+ `insert`（追加留痕），**无幂等键 → 非幂等**；幂等责任在流程状态守卫（FOR UPDATE + 状态校验），R-6 须保证单事务内仅调用一次 |
| T2 | 并发审批行锁 | **已核实（[:502-503]）并定稿（§11.7）**：R-6/R-9 事务首条对 `hr_flow` 行 `SELECT ... FOR UPDATE`，固定加锁顺序；验收并入 B4 |
| T3 | 超时失效执行方式 | **定稿**：`expire-days=7`（配置项）；一期**惰性判定**（查询/审批前判 `expire_time`），自动任务 `TODO(扩展): 规模上升后引入定时任务`；**M-8 清理任务**（已拒绝/超期保留 30 天）为本批范围，B3 落地 |
| T4 | 短信 `template-code-register` | 阿里云模板须新申请（属凭据/外部依赖，**主智能体归口**） |
| T5 | `ClientLogSanitizer` 凭据正则补 `code=|smsCode=|verifyCode=|captcha=|otp=` | SEC-FULL-14 / 安全 §4.4⑦；**并入 M-8 批次**（防误擦 `code=200`） |
| T6 | 通知联动 | 审批通过/驳回是否发站内通知（`notification.type` 需新增 7/8），本方案**未纳入**，`TODO(扩展): 按运营需要另批设计` |
| T7 | 注册渠道扩展 | 一期仅 `STAFF_H5`；`TODO(扩展): 企微/安卓壳渠道`（三期） |
| T8 | 安全评估响应 | **已完成**：M-1~M-9 闭环对照（§11.2）、S/O 处置（§11.3）、裁定结果表（§11.4）、存量项合并（§11.13） |
| T9 | 岗位字典化 | **不纳入本批**（方案乙仍用 `VARCHAR(50)` 自由文本，对齐既有 `hr_flow.position`）；`TODO(扩展): 岗位字典（新建实体，属范围扩张，须用户口径确认）` |
| **T10（方案乙新增）** | 存量员工 `position` 历史回填 | **定稿不回填**（NULL 表示未登记）；如需回填，另立 C 档脚本 + 备份 + 授权（§11.9/E7 已登记） |
| **T11（新增）** | SEC-FULL-08 前端文案回归 | 发码恒定化后，staff-h5/boss-h5 登录页「该手机号未注册」类提示须删除/统一为「验证码已发送」；并入 B6 |
| **T12（关联项）** | tech-review M5 可运行验证归档 | 服务器实跑 `mvn -q clean package` / `mvn test` / Flyway 空库迁移，结果归档 `docs/`；并入 B3/B4/B7 验收（§11.14） |

***

## 11. 安全与工程上游响应（v1.2 定稿 · 逐条闭环）

> 响应对象（4 份）：① [security-registration-review.md](security-registration-review.md)（331 行，「阻断（公网）」，M-1~M-9）；② [security-full-review-20260926.md](security-full-review-20260926.md)（SEC-FULL-08/18 等存量项）；③ [tech-review-full-20260926.md](tech-review-full-20260926.md)（「有条件通过」，M1/M5 纳入）；④ 用户 5 条裁定（§0.5）。
> 行文约定：对**已被上游推翻**的原表述一律用 `~~删除线~~` 并标注改点。
> **闸门声明**：本方案为**方案阶段产物**，**仍须经技术评审工程师评估（P0.6 / L8）**，结论「通过 / 有条件通过」后方可报主智能体审批与实施；**本方案不宣称已通过评审**。

### 11.1 总体处置

- 安全三条硬阻断：**REG-01/09**（短信轰炸 + 成本耗尽）、**REG-03/05**（提权 + 绕过人工审核）、**REG-02/04/06**（枚举 + 重复号 DoS + IDOR）。
- 降级路径（安全 §7.2）：**M-1~M-9 全部闭环 + S-6 复验** → 「有条件放行」；**M-3 / M-4 / M-5 任一未闭环 → 维持阻断**。
- **定稿（用户裁定②）**：本方案对 **M-1~M-9 全部采纳并全部落入实施批次（§9）**，**无"后续做"项**；其中 **M-4 / M-6 / M-9** 触发对 v1.0 设计的**实质性推翻**（原文已加删除线）。
- **新增合并（§11.13/§11.14）**：SEC-FULL-08（存量发码枚举）与 M-2 合并为**同一口径**；SEC-FULL-18 = M-1 同项；tech-review **M1**（契约缺口 83）并入 §3.5/§9-B1，**M5**（可运行验证）落为各后端批次验收口径。
- **S 项**按 §11.3 分档（S-1/S-5/O-* 仍为后续批，**不属 M 项、不阻塞 M 闭环**）。

### 11.2 M-1 ~ M-9 闭环对照表

| 编号 | 安全要求（摘要） | 本方案设计落点（章节/字段/接口/事务） | 是否需改原设计（改了什么） | 验收对应（安全 §6.1 → 本方案） |
| --- | --- | --- | --- | --- |
| **M-1** | 独立 `SmsScene.REGISTER`；`resolveScene` 未知场景**拒绝**而非回落 LOGIN | §3.2 R-1 增 `scene=REGISTER`；**新增约束**：`SmsScene` 增 `REGISTER` 后，`resolveScene` 对**非空且非法** scene 抛 `BusinessException(BAD_REQUEST,"不支持的短信场景")`，**仅空/空白**保持回落 `LOGIN`（保既有前端契约，[AuthServiceImpl.java:606-619](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L619)） | **改**：原 §3.2 R-1 只写"缺省仍按 LOGIN"，**未处理非法值 fail-open** → 增补 `resolveScene` 改造约束（[SmsScene.java:18-29](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L18-L29)） | 单测：未知**非空** scene → 拒（非 LOGIN）；`REGISTER` 对未注册号可发码且**不**走 `sendLoginCode` 账号查询分支 |
| **M-2** | 发码/提交响应**完全恒定化**（含 1109/2003 差异消除） | §3.4「发码环节一致性」**采纳**（发码恒定，已注册/未注册都发码）；**提交段定稿（U-03）**：采用 **M-2 严格形态**，`9310` **废弃**，已注册由 ADMIN 审批台识别 | **部分推翻**：~~§3.4「验证码通过后告知已注册不构成枚举」~~ → 定稿改为**提交也不区分**（严格形态）；`9310` 从错误码表删除 | 已注册/未注册两组各 ≥50 次，发码与提交响应 `{code,message,data}` 与 HTTP 状态**逐字段一致**；不得出现 1109/2003/9310 差异 |
| **M-3** | 注册字段与 role/薪资/站点解耦；`role` 恒 `STAFF`；薪资仅 ADMIN 可写 | §5.4 + **§11.5 完整字段分离表**；注册 DTO 字段更名 `intentStationId`/`intentPosition`（**废弃**顶层 `stationId` 语义）；增 `@Valid` + `FAIL_ON_UNKNOWN_PROPERTIES`（并入 S-3） | **改**：原 §3.2 R-2 入参 `stationId`（必填）→ `intentStationId`，并明确"仅意向/仅展示，建档时由 ADMIN 校验后写入" | 契约测试：载荷含 `role/stationId/basicSalary` → 400 或忽略不落库；建档后 `employee.role=='STAFF'`；定薪值来自 ADMIN 提交 |
| **M-4** | 审批通过**不自动激活**；保留 `status=0` + 首登强制改密；**不复用注册密码** | §11.8；R-6 聚合**仅前 3 步**（CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY），停在 `SET_SALARY`，`hr_flow` 保持 `IN_PROGRESS`；`employee.status` 恒 0；`DONE`（激活）改由 ADMIN **二次显式确认** | **推翻**：~~§1.1 S4「…→DONE(转在职 status=1)」~~、~~§4.3 事务顺序含 DONE~~、~~§2.3「password_hash → employee.password」~~、~~§8-Q3「申请单散列作初始密码」~~（均加删除线）；初始口令由 ADMIN 在 `CREATE_ACCOUNT` 一次性设定（既有事实⑪） | 审批通过后 `employee.status==0`；首登强制改密生效；注册所填密码**不出现**在 `employee.password` 链 |
| **M-5** | 建档补手机号唯一校验 + `employee.phone` **DB 唯一约束**（含存量去重） | §11.6 既有缺陷修复（Service 查重 + DB 活跃唯一 + 预检 SQL + 迁移 V16/回滚） | **改**：原 §2.4「不建 DB 唯一索引」（D7）对 `employee.phone` **增设例外**（M-5 硬项）；原 §4.1 `CREATE_ACCOUNT` 须加 phone 查重 | 存量重复号清单清零；并发同号仅 1 行落库；`selectOne(phone)` 恒命中 ≤1 行 |
| **M-6** | 申请接口 ADMIN-only；公开白名单**仅**发码与提交两条 | §3.1 **收敛定稿**：公开白名单仅 R-1（发码，复用）+ R-2（提交）；**R-3 转 ADMIN-only**、**R-4 取消**、**R-5 取消公开**（U-04/U-06） | **推翻**：~~§3.1「新增公开端点 4 个（R-2~R-5）」~~（加删除线）；**净新增白名单 = 1 条**（R-2） | `PublicEndpoints.all()` 新增仅 1 条；R-3/R-7~R-9 非 ADMIN → 403 |
| **M-7** | Nginx `limit_req`/`limit_conn` + 应用层全局日上限 | §5.1 表增量：Nginx `limit_req zone`（按 `$binary_remote_addr`）+ 全局 `limit_conn`（**只增不改**）；应用层在既有五维外**增**「手机号×全局日上限」+「注册提交日上限」 | **改**：原 §5.1 仅"**建议** Nginx `limit_req`" → 升为**必做**并具体化（用户裁定④已授权）；Nginx 配置执行归运维（B/C 档，主智能体授权） | 单 IP 高频 → 429/1101；换号轰炸命中全局上限；伪造 `X-Forwarded-For` 不影响（Nginx 以 `$remote_addr` 计） |
| **M-8** | 未过审数据保留期限 + 清理；最小必要；出参脱敏 | §2.2（`source`/`agreement_version`/终态清凭据）+ `retention` 配置项与清理任务（**U-14 定稿：已拒绝/超期保留 30 天**）；R-3/R-8 出参脱敏 | **改**：原 §2.2 **未定**保留期；原 T3 仅"超时惰性判定" → 增"已拒绝/超期"清理任务与配置项 | 清理任务 + 配置项存在（可外置）；超期数据被清理；列表手机号脱敏（`138****1234`） |
| **M-9** | `hr_flow` 增来源标识；记来源 IP（脱敏）/UA/提交时间；审批动作留办理人 | **新增 `hr_flow.source` 列**（迁移脚本，批次见 U-08）；`registration` 保留 `source`/`client_ip` 作注册侧审计补充；审批留痕复用 `hr_flow_step.operator_id/operator_name/operate_time` | **部分推翻**：~~§2.1 方案②"否决"理由之一为"避免动 `hr_flow` 加列"~~ → M-9 要求 `hr_flow` 加 `source`；两表 `source` **并存**（`hr_flow.source` 供既有审批台，`registration.source` 供注册审计） | 自助来源可区分；提交与逐审批动作可回溯 |

### 11.3 S-1 ~ S-6 与 O-1 ~ O-3 处置

| 项 | 处置 | 理由 |
| --- | --- | --- |
| **S-1** 启用真实图形验证码 | **后续做** | 现状为固定占位图 + 默认关闭（REG-10，[CaptchaStore.java:17-20](../hrm-server/src/main/java/com/qiujie/service/auth/support/CaptchaStore.java#L17-L20)），需独立改造；本批以 **M-7 双层限流**兜底，登记残余风险 R-3 |
| **S-2** 注册提交纳入频控 | **本批做** | 并入 M-7 应用层"注册提交日上限" |
| **S-3** 入参强校验 | **本批做** | DTO `@Valid` + `FAIL_ON_UNKNOWN_PROPERTIES` + 枚举白名单 + 长度上限 + 手机号正则（并入 M-3） |
| **S-4** 服务端 IP 取信改造 | **本批做（Nginx 侧）+ 后续做（应用层）** | M-7 验收要求"伪造 XFF 不影响限速"由 Nginx `$remote_addr` 满足（本批）；应用层 `IpUtil` 可信代理白名单改造**影响全局**，独立批（[IpUtil.java:17-31](../hrm-server/src/main/java/com/qiujie/util/IpUtil.java#L17-L31)） |
| **S-5** 埋点与告警 | **后续做** | 依赖监控体系，独立批；本批仅要求 `info` 级关键流转日志 |
| **S-6** 交接实现后安全复验 | **必做（流程项）** | 公网放行前由网络安全工程师复验并出具结论（L7） |
| **O-1** 短信通道日预算/多供应商降级 | **后续做** | 运维 + 主智能体 C 档协同 |
| **O-2** 行为风控（滑块/设备指纹） | **不做** | 依赖算法工程师 R09 选型，**超本方案范围** |
| **O-3** 高风险号段/IP 黑名单 | **后续做** | 运营策略位 |

### 11.4 裁定结果表（**定稿 · U-01~U-17 全部有终局结论**）

> **本表为定稿表**：所有项均已裁定，**不再列"替代方案"**（原替代方案降为「备注」列，仅备追溯）。
> 依据来源：`M-*` = [security-registration-review.md](security-registration-review.md) 必做项；`裁定n` = 用户裁定（§0.5）；`SEC-FULL-*` = [security-full-review-20260926.md](security-full-review-20260926.md)；`TR-*` = [tech-review-full-20260926.md](tech-review-full-20260926.md)。

| 编号 | 裁定问题 | **裁定结果（定稿）** | 依据 | 备注（原替代方案，不采纳） |
| --- | --- | --- | --- | --- |
| **U-01** | 审批激活时点 | **`DONE` 二次人工确认才激活**；审批仅前三步，`employee.status` 恒 0 | M-4、裁定④ | 审批即激活（违反 M-4） |
| **U-02** | 初始口令来源 | **不复用注册密码**；ADMIN 一次性口令（`initialPassword` 必填）+ `pwd_changed=0` 首登强制改密；注册密码仅留痕、终态清散列 | M-4、裁定④ | 复用注册密码 + `pwd_changed=1`（违反 M-4） |
| **U-03** | 提交段是否保留 `9310` | **废弃 `9310`**：采用 M-2 严格形态，提交响应对「是否已注册」恒定；已注册由 ADMIN 审批台识别 | M-2、裁定② | 保留 `9310`（与 M-2 严格口径冲突，原 §11.4"保留"建议**已推翻**） |
| **U-04** | R-5 驿站选项是否公开 | **取消公开**；意向驿站选项改由**前端构建期静态配置**提供；服务端 R-2 再审存在+启用 | M-6、裁定④ | 不含驿站的注册表单（可退，见 §3.2 R-5 备注） |
| **U-05** | `employee.phone` 唯一约束形态 | **活跃唯一（仅 mysql 实现）**：生成列 `phone_active` + `UNIQUE`；~~PG 部分唯一索引 `WHERE is_deleted=0`~~（PG 目录冻结，本期不产出） | M-5、裁定④ | 直接 `UNIQUE(phone)`（破坏逻辑删除复用） |
| **U-06** | R-3/R-4 去向 | **R-3 转 ADMIN-only**（按 `applyNo` 查申请单）；**R-4 取消**（超时清理 + ADMIN 驳回替代）；`queryToken` 机制一期不启用 | M-6、裁定④ | 保留凭据式公开（需安全豁免）；完全取消两者 |
| **U-07** | 岗位：方案甲 vs 方案乙 | **方案乙**：新增 `employee.position VARCHAR(50) NULL`（C 档结构变更，**已授权**），成为员工档案属性；与 `hr_flow.position` 双写、以 `employee.position` 为权威 | **用户裁定①** | ~~方案甲（零结构变更）~~ **原 §11.9 建议已推翻** |
| **U-08** | `hr_flow.source` 迁移批次 | 与注册同批判审、**独立脚本 V18** | M-9 | 延后至注册上线后 |
| **U-09** | `deptId` 由谁提供 | **审批时必填**（注册不采集） | §3.2 R-6 | 注册时采集（增表单复杂度） |
| **U-10** | 审批时薪资是否必填 | **必填**（防工资单静默为 0） | §3.2 R-6 | 允许缺省 0 |
| **U-11** | `username` 口径 | 缺省 **`u`+手机号**（满足 `^[a-zA-Z][a-zA-Z0-9_]{3,29}$`），ADMIN 可覆盖 | §3.2 R-6 | ADMIN 必填 / 拼音生成 |
| **U-12** | 审批权限 | **仅 ADMIN**（站长不可审） | M-6、§5.3 | 两级审批（范围扩张，已否决） |
| **U-13** | 注册建 `hr_flow` 的操作人记什么 | **`operator_id/operator_name = null`** + `hr_flow.source=SELF_REGISTER` + 来源 IP（脱敏）/UA/提交时间 | M-9 | 系统哨兵（如 0/"自助注册"） |
| **U-14** | 保留期限与个人信息说明 | 已拒绝/超期保留 **30 天**（配置项 `hrm.registration.retention-days`）+ 终态清凭据 + 出参脱敏 | M-8 | — |
| **U-15** | 手机号查重命中返回码 | 复用 **`PHONE_EXISTS(2003)`**（与 `EmployeeServiceImpl` 同口径） | M-5 | 新增 `9311` |
| **U-16** | 非法短信场景返回码 | 复用 **`400 BAD_REQUEST`（"不支持的短信场景"）** | M-1 | 新增 `1111` |
| **U-17** | `apply_no` 是否增设唯一索引 | **增设 `UNIQUE(apply_no)`**（D7 例外，无逻辑删除复用语义） | §2.4 | 不加（接受并发双单） |

> **裁定结果对代码/文档的落点**：全部已在 §1~§9 内按定稿口径展开；**无遗留待裁定项**。若后续任一口径变更，须回到本表追加新行并**重评**（P0.6/L8）。

### 11.5 字段分离完整表（M-3 落地）

**表 A：注册 DTO 采集白名单（公开接口 R-2，注册方**仅能**影响本表字段）**

| 字段（DTO） | 来源 | 必填 | 校验 | 可否被注册方影响 | 落点 |
| --- | --- | --- | --- | --- | --- |
| `realName` | 申请人 | 是 | 2-20 字（[HrFlowServiceImpl.java:638-639](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L638-L639)） | 是（仅本单姓名） | `registration.real_name` → `flow.candidate_name` |
| `phone` | 申请人 | 是 | `^1[3-9]\d{9}$` | 是 | `registration.phone` → `flow.phone` |
| `smsCode` | 短信 | 是 | 6 位；`REGISTER` 场景一次性作废 | **否**（服务端校验） | 不落库 |
| `intentStationId` | 申请人 | 是 | 存在 + 启用（4001/4004） | 是（**仅意向**） | `registration.apply_station_id` → `flow.station_id`（审批可改） |
| `intentPosition` | 申请人 | 否 | ≤50 字 | 是（**仅意向**） | `registration.apply_position` → `flow.position`（审批可改） |
| `password` | 申请人 | **否**（U-02 定稿） | 8-20 位含字母数字 | 是 | **不作员工初始口令**；仅留痕（`registration.password_hash`），终态清 NULL（§11.8） |
| `agreementVersion` | 申请人 | 是 | 版本串 | 是 | `registration.agreement_version` |

> **硬约束**：表 B 字段**编译期不得出现在注册 DTO**（独立 DTO，**不复用** `HrStepCompleteRequest`）；Spring 侧 `FAIL_ON_UNKNOWN_PROPERTIES=true`，夹带未知字段 → 400（S-3）。

**表 B：审批时 ADMIN 赋值字段（注册方**不可**影响）**

| 字段 | 来源 | 必填 | 校验 | 落点 |
| --- | --- | --- | --- | --- |
| `username` | ADMIN / 按规则生成 | 是 | `^[a-zA-Z][a-zA-Z0-9_]{3,29}$` + 活跃唯一（1003） | `employee.username` |
| `initialPassword` | **ADMIN（M-4）** | 是 | 8-20 位含字母数字（[:275-277](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L275-L277)） | `employee.password`（BCrypt），`pwd_changed=0` |
| `deptId` | ADMIN | 是 | 存在（3001） | `employee.dept_id` |
| `stationId`（事实） | ADMIN（缺省取意向） | 是 | 存在 + 启用（4004） | `employee.station_id` |
| `role` | ADMIN | 否（默认 `STAFF`） | `ASSIGNABLE_ROLES` 白名单（[HrConstants.java:52](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L52)） | `employee.role` |
| `position`（事实） | ADMIN（缺省取意向） | 否 | ≤50 字 | **`employee.position`（权威，方案乙）+ `flow.position`（流程留痕），双写（U-07/§11.9）** |
| `basicSalary/postSalary/performanceBase/allowances` | **仅 ADMIN** | **是**（U-10 定稿） | `HrSalaryValidator`（[:697-701](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L697-L701)） | `hr_salary` |
| `pwdChanged` | 系统 | — | **恒 0**（M-4/U-02） | `employee.pwd_changed` |
| `status` | 系统 | — | **恒 0**（M-4；激活由 `DONE` 步骤，见 U-01） | `employee.status` |

### 11.6 既有缺陷：手机号唯一（REG-04 / M-5）

**现状（实测，缺陷当前即存在，安全要求"先于注册上线修复"）**

- `createEmployeeForFlow` **仅校验 username**（[:272-274](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L272-L274)），**无 phone 查重**，直接 `employee.setPhone(flow.getPhone())`（[:296](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L296)）。
- 对照 `EmployeeServiceImpl` **有** `existsActivePhone`（[:131-133](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L131-L133)、[:547-556](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L547-L556)），返回 `2003`。
- `employee.phone` **无 DB 唯一索引**（[db.md:48](db.md) 决策 D7；[:184](db.md)/[:286](db.md) `idx_employee_phone` 为**普通索引**）。
- 登录按 phone `selectOne` → 命中多行抛异常 → 目标账号登录/短信登录 **DoS**（安全链 D）。

**① 修复设计**

- **Service 层**：`createEmployeeForFlow` 在 username 校验后**增** phone 活跃查重（复用 `existsActivePhone` 同口径），命中 → **`PHONE_EXISTS(2003)`（U-15 定稿，不新增 9311）**；`assignForFlow` 若后续允许改 phone 亦须查重（当前不改 phone，登记为约束）。
- **DB 层**：`employee.phone` 增设**活跃唯一**约束（M-5 硬项，**形态 U-05 定稿**）：
  - MySQL：生成列 `phone_active VARCHAR(20) GENERATED ALWAYS AS (IF(is_deleted=0, phone, NULL)) STORED` + `UNIQUE INDEX uk_employee_phone_active(phone_active)`（NULL 可重复 → 已删号可复用）。
  - ~~PostgreSQL：`CREATE UNIQUE INDEX uk_employee_phone_active ON employee(phone) WHERE is_deleted = 0;`~~（**v1.3，M1：`postgresql/` 自 V2 冻结，本期不产出 pg 脚本**，与 [db.md:8](db.md)/[db.md:401](db.md) 一致）
- **并发兜底**：DB 唯一约束即最终防线，Service 查重仅为友好报错（消除 TOCTOU 双提交窗口）。

**② 存量重复号预检 SQL（迁移前置，必须返回 0 行）**

```sql
-- MySQL：活跃员工中重复手机号（v1.3 修订 M3：排除 phone IS NULL，避免 NULL 归一组误报）
SELECT phone,
       COUNT(*)                       AS cnt,
       GROUP_CONCAT(id ORDER BY id)    AS ids,
       GROUP_CONCAT(real_name)         AS names
FROM employee
WHERE is_deleted = 0
  AND phone IS NOT NULL
GROUP BY phone
HAVING COUNT(*) > 1;

-- PostgreSQL（冻结目录，仅留档说明；本期不产出 pg 脚本）：
-- SELECT phone, COUNT(*) cnt, string_agg(id::text, ',' ORDER BY id) ids, string_agg(real_name, ',') names
-- FROM employee WHERE is_deleted = 0 AND phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1;
```

> **v1.3 说明（M3）**：MySQL `GROUP BY phone` 会把 `is_deleted=0 且 phone IS NULL` 的多行归为一组 → `COUNT(*)>1` **误报「重复号」**（生成列 `phone_active` 对 NULL 不去重，NULL 允许重复）；故预检条件补 **`AND phone IS NOT NULL`**，避免无谓阻断迁移。回滚/判据不变。

> 若返回非空：**先去重再迁移**；去重（保留哪条）属**数据变更**，须主智能体 C 档授权 + 人工确认。

**③ 迁移影响与回滚**

- 脚本：新版本号 **`V16__employee_phone_unique.sql`**（~~mysql + pg~~ → **仅 mysql**，v1.3 依据 M1；当前最大 [V15](../hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql)），**禁止改历史脚本**；**仅同步 `sql/schema/mysql/init.sql` 快照**（pg 快照冻结）与 `db.md`。
- 影响：**结构变更（C 档）**；执行前须**备份**；对既有数据无回填（仅加约束/生成列）。
- 回滚：`DROP INDEX`（脚本注释附回滚语句），**不改数据**；若已发生合法数据依赖，回滚前须评估。
- 风险：预检非 0 → 迁移失败（安全 **R-5**），须先人工去重。

**④ 档位标注**

**结构变更（C 档）**，且该缺口**当前即存在**、**先于注册公网上线修复**；授权须主智能体按 §10.3 三步表单，**无安全评估结论不得授权**（[智能体调度规则 §10.3]）。

### 11.7 事务与并发（行锁 / 加锁顺序 / 死锁规避 + `HrSalaryWriter` 幂等结论）

- **事务边界（按 M-4 修订，v1.3 修订 M2）**：R-6 聚合事务 = 按序推进 5 步 `SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY` → `registration.status=APPROVED` → 清 `registration` 凭据列；**不含 `DONE`**（§4.2.1/§4.3，原文已标注推翻）。**5 步推进为 U-01 激活路径的前置不变式**（否则 `DONE` 被守卫拒 9303）。
- **锁粒度与位置**：既有 `requireFlow` = `hrFlowMapper.selectById(id)`（[:502-503](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L502-L503)），**无行锁**。R-6/R-9 进入事务后**首条**执行 `SELECT ... FOR UPDATE` 锁 **`hr_flow` 单行**，再校验状态。
- **固定加锁顺序（防死锁）**：① `hr_flow`(FOR UPDATE) → ② `employee_registration`（按 `flow_id` FOR UPDATE 或先写状态）→ ③ `employee`（insert，无锁）→ ④ `hr_salary`（update）。**所有审批/驳回/撤回路径必须遵循同一顺序**。
- **死锁规避**：(a) 固定加锁顺序；(b) 事务内**不做**远程调用/短信；(c) 先锁后校验再操作，缩短持锁时间；(d) `hr_flow` 为**单行锁**，冲突面小；`innodb_lock_wait_timeout` 由运维配置化。
- **审批 vs 撤回 并发**：两路径均以 `registration.status=SUBMITTED` 守卫为准，同一行锁下**其一必失败**（`registration` 加锁顺序一致）。
- **`HrSalaryWriter` 幂等性核对结论（原 T1，已核）**：[HrSalaryWriter.java:57-105](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L105) 的 `save()` = `updateById`（覆盖当前值）+ `insert`（追加留痕），**无幂等键 → 非幂等**：每次调用追加一条 `hr_salary_log`（当前值不变）。**故幂等责任在流程状态守卫**（`hr_flow.status` / `registration.status` + FOR UPDATE），R-6 须保证事务内**仅调用一次**；可选加固：以 `(employee_id, change_type='ENTRY', effective_date)` 或流程 ID 作去重键。另注：`selectByEmployeeId` 取 `list.get(0)`（[:114-118](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L114-L118)），若 1:1 被破坏存在取错行风险 → 与 M-5 同批治理。

### 11.8 不激活口径（M-4 落地细则）

- **`status=0` 含义**：员工档案已建但**未生效**，**不可登录**、不可被引用为在职（既有口径 [:301](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L301)；[Employee.java:47-48](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L47-L48)）。
- **首次登录路径**：审批通过后 `employee.status=0` → **无法登录**；须 ADMIN 在流程末步 `DONE` **二次显式确认**（复用 `completeOnboardingStep` DONE）后方转 `status=1`，此后员工方可登录。**前置不变式（v1.3 修订 M2）**：R-6 须已按序推进前 5 步 → `DONE` 为唯一剩余 `PENDING`，`completeOnboardingStep(DONE)` 才不被 `HrFlowStepGuard` 拒；否则返回 9303、激活不可执行（口径与代码依据见 §4.2.1，验收见 §9 B4）。
- **密码来源（M-4）**：由 **ADMIN 在 `CREATE_ACCOUNT` 一次性设定**（既有事实⑪ [:268-277](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L268-L277)），**不复用注册自设密码**；`pwd_changed=0` → 首登强制改密（R6 口径）。
- **与注册自设密码的关系（定稿）**：注册采集的 password **不作为员工初始口令**；去向 = **U-02 定稿 (B) 仅作合规留痕、终态清空散列**。**为何不复用**：切断"注册可控数据 → 账号凭据"链路（安全链 C 的凭据面），且注册密码未经理工端二次强度确认不宜直通账号。
- **与用户口径的张力（已裁定，非遗留）**：用户原始口径「审批通过后同时…+ **本人设置密码**」中「账号激活可登录」与「本人密码」两点**与 M-4 冲突** → **已由用户裁定④定稿为形态 A**（不自动激活 + 不复用注册密码），原"形态 B"字面实现**不予采纳**。

**定稿形态（A · M-4 严格）**

| 维度 | 定稿（形态 A，M-4 严格） | ~~形态 B（用户口径字面）~~ |
| --- | --- | --- |
| 账号激活 | 审批通过后 `status=0`，`DONE` 二次确认才激活（**U-01**） | ~~审批通过即 `status=1`~~ |
| 初始口令 | ADMIN 一次性口令 + `pwd_changed=0`（**U-02**） | ~~复用注册自设密码 + `pwd_changed=1`~~ |
| 人工审核强度 | 两道（审批 + 激活确认） | 一道（审批即账号） |
| 安全结论 | 满足 M-4 | **不满足 M-4，维持阻断** |

> **前置依赖（必闭环）**：形态 A 依赖「`pwd_changed=0` 时服务端强制拦截」真正生效——现状为**仅前端实现**（SEC-FULL-09 / 安全 §4.4）。故 **SEC-FULL-09 列为公网放行前必闭环的关联项**（§11.14），**不属于本方案实现范围**但**阻塞本方案放行**。

### 11.9 「岗位」方案乙 · 完整落地设计（用户裁定①，U-07 定稿）

> **背景（实测）**：`position` 原**仅**存 `hr_flow.position`（`VARCHAR(50)` 自由文本，[HrFlow.java:56-57](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L56-L57)）；`employee` 表**无** position 列（[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66)）；全仓 VO 中仅 [HrFlowVO.java:44](../hrm-server/src/main/java/com/qiujie/vo/hr/HrFlowVO.java#L44) 有 `position`。
> **裁定（U-07）**：**采纳方案乙** —— 新增 `employee.position`，成为**员工档案属性**；**原"方案甲（零结构变更，推荐）"建议已被用户裁定①推翻**。

| 设计项 | 定稿 |
| --- | --- |
| **字段名** | `employee.position`（对齐既有 `hr_flow.position` 命名） |
| **类型 / 长度** | `VARCHAR(50)`（**与 `hr_flow.position` 完全一致**，[HrFlow.java:56-57](../hrm-server/src/main/java/com/qiujie/entity/HrFlow.java#L56-L57)）；**仅 mysql 落脚本**（PG 目录冻结，不产出 pg 脚本） |
| **允许空 / 默认** | **NULL**（存量员工无岗位 → NULL 表示"未登记"）；无默认值 |
| **是否必填** | 列**不强制**；但 **R-6 审批定岗时建议必填**（前端表单必填，后端允许缺省取意向） |
| **是否字典化** | **不字典化（最小形态）**。理由：① 既有 `hr_flow.position` 即自由文本，字典化会造成**两套口径**；② 无"岗位实体"（§0.2-⑥），引入字典表属**范围扩张**且需用户口径确认；③ 一期价值在"岗位进档案"，不在枚举治理。`TODO(扩展): 岗位字典（T9）` |
| **索引** | **不加索引**（低基数、无按岗位过滤的既有查询） |
| **迁移** | **V19__employee_position.sql**（**仅 mysql**；`ALTER TABLE employee ADD COLUMN position VARCHAR(50) NULL`；回滚 `DROP COLUMN`）→ **C 档** |
| **快照 / `db.md` 更新点** | ① **仅 `sql/schema/mysql/init.sql`** 的 `employee` 加列（**仅列增，不改表数**；表总数 38→39 由 **V17** 带来；pg 快照冻结不刷新）；② `db.md §3.3 employee`（[db.md:155](db.md)）加一行 `position` + 注释；③ 与 `hr_flow.position`（[db.md:766](db.md)）建立**口径说明**（谁是权威） |
| **与 `hr_flow.position` 关系** | **双写、单一权威**：以 **`employee.position` 为权威事实**（档案属性），`hr_flow.position` 为该次流程的**过程值与留痕**。**双写点唯一**：`assignForFlow`（[HrFlowServiceImpl.java:361-363](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L361-L363)）在**同一方法、同一事务**内 `employee.setPosition(...)` + `flow.setPosition(...)`；**禁止他处单独写 `employee.position`** |
| **历史回填** | **定稿不回填**：存量 `employee.position` 保持 NULL；**不以历史 `flow.position` 伪造档案事实**。如需回填，**另立 C 档脚本**（取该员工最近一次 `COMPLETED` 的 ONBOARDING 单的 `flow.position`）+ 备份 + 授权（**E7/T10 已登记**） |
| **前端展示位置** | ① **员工档案详情/编辑**（`EmployeeVO` 增 `position`，[EmployeeVO.java:15-45](../hrm-server/src/main/java/com/qiujie/vo/employee/EmployeeVO.java#L15-L45) 现无该字段）；② **员工列表**新增「岗位」列；③ **员工导出模板**新增「岗位」列（`EmployeeServiceImpl` 导入/导出，[:621-652](../hrm-server/src/main/java/com/qiujie/service/employee/impl/EmployeeServiceImpl.java#L621-L652)）；④ **审批页**（`web/src/views/onboard/index.vue`）定岗表单含「岗位」+ `registration` 子对象展示意向岗位；⑤ **Mock** `toEmployeeVO` 同步补 `position`（`packages/mock` 与 `hrm-demo` 同名副本） |
| **后端改动面** | `Employee` 实体加字段；`EmployeeVO` 加字段；`EmployeeCreateRequest`（若允许建档改岗）加字段；`assignForFlow` 增双写；`HrFlowVO`/审批出参沿用既有 `position` |
| **对报表 / 工资单 / 考勤的影响** | **无**（**逐个数据源核对**）：工资单取 `hr_salary`（不读 `position`）；考勤取排班（`schedule`/`shift`，不读 `position`）；现有报表/KPI 取工单/包裹/薪资/考勤（均不读 `position`）。**唯一影响面**：员工列表与导出**新增一列**（展示面，非计算面） |
| **验收标准（B2/B3/B4/B6）** | V19 脚本（**仅 mysql**）+ mysql 快照 + `db.md` 一致；`mvn test` 通过；单测：`assignForFlow` 后 `employee.position == flow.position`（双写一致）；已删/建档不写 `employee.position`；前端列表/导出含「岗位」列、NULL 显示「—」 |

> **方案甲（已否决，保留追溯）**：~~一期不入档案、复用 `flow.position`~~。否决理由：不满足用户裁定①「岗位成为员工档案属性」；且历史多单时"取最新 ONBOARDING 单"的口径脆弱。

### 11.10 `api.md` 实况复核（v1.2，与 tech-review-full M1 合并）

复核方式：重新 `Grep` 全仓 controller 方法级 `@*Mapping`（**23 文件 / 151 个映射**）+ 读 `api.md` §4.0 概览与 §7。

| 结论 | 实测 |
| --- | --- |
| §4.0 概览计数「46」 | **已自洽**：认证 4 + 看板 1 + 员工 10 + 部门 4 + 驿站 5 + 考勤 10 + 补卡 4 + 班次 4 + 排班 4 = 46——考勤域 **22 端点**为**主智能体已补录**，**非**历史残留。~~原 §0.2-④「与代码不一致／历史残留」~~ 已被本次复核**推翻**（[api.md:184-234](api.md)） |
| 代码端点总量 | **151**（controller 方法级映射计数）；`api.md` §4.0 覆盖 **46**、§7 覆盖 **16**（请假 13 + 客户端日志 3），**合计 62 / 151** |
| **§4 未建章节的域（缺口 83）** | 入离职 `hr_flow` **10**、人事档案 `hr_profile` **6**、财务 `payroll` **10**、资薪规则 `payroll-rule` **5**、KPI **9**（score 4 + metric 5）、工单 **9**、同步 **22**（task 5 + config 4 + config-center 13）、包裹 **6**、通知 **6** |
| 认证增强未入 §4.0 概览表 | `sms/send`、`sms/login`、`device/verify`、`devices`、`devices/{id}`、`captcha` 共 **6** 端点（§4.1.5 为端准入契约说明，非端点表） |

→ 需**补录 83 端点（§4 章节）+ 概览表补 6 行**；其中与注册方案**直接相关**的是 **入离职 10 + 人事档案 6 = 16**（R-6 落点）。

### 11.11 契约批次（B0 / B1）定稿

- **B0**：安全评估**已产出**（结论「阻断（公网）」）→ 由"待评估"转为"**已出结论 + 须闭环 M-1~M-9（全部入批）**"；B7 公网暴露前须 **S-6 复验**（§9 已同步）。
- **B1 范围定稿**（验收标准见 §9 总表）：
  1. **B1-C1（前置必录）**：补录 **入离职 10 + 人事档案 6 = 16 端点**（新建 `§4.10 人事/入离职`），R-6/R-7/R-8/R-9 与之同章；
  2. **B1-C2~C5**：财务 15 + KPI/工单 18 + 同步 22 + 包裹/通知 12 = **67 端点**，拆独立子批、**不阻塞**注册方案；
  3. **B1-C6**：认证增强 6 端点补 §4.0 概览（概览补录，非新章）；
  4. **注册侧公开仅 R-1（场景扩展）+ R-2（提交）**；R-3 转 ADMIN-only、R-4/R-5 取消（U-04/U-06）；
  5. **错误码**：`9307`/`9309` 启用；~~`9310` 废弃~~（U-03）；`9308` 保留未启用；非法短信场景复用 `400`（U-16）；手机号查重复用 `2003`（U-15）；
  6. **`PublicEndpoints` 净新增 1 条**（R-2；发码路径 `/auth/sms/send` 既已白名单，[PublicEndpoints.java:31](../hrm-server/src/main/java/com/qiujie/common/PublicEndpoints.java#L31)）；
  7. **验收**：与 §11.2 各 M 项验收逐条对齐；`PublicEndpointsTest` 快照回归断言更新；**契约唯一真源 = `api.md`**（tech-review M1）。

### 11.12 事实性纠正（v1.1 / v1.2 / v1.3 累计）

| # | 旧认知 | 实测 | 出处 |
| --- | --- | --- | --- |
| ③′ | `security-registration-review.md` 不存在 | **已存在（331 行，结论阻断）** | [security-registration-review.md](security-registration-review.md) |
| ④′ | `api.md`「46」为历史残留、与代码不一致 | **§4.0 的 46 已自洽**（考勤域 22 已补录）；但仅覆盖代码 151 端点的 62/151（含 §7），**缺口 83** | [api.md:184-234](api.md)、[api.md:1134](api.md) + controller 计数 151 |
| T1′ | `HrSalaryWriter` 幂等性"未验证" | **已核：非幂等**（`update` + `insert` 无幂等键） | [HrSalaryWriter.java:57-105](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrSalaryWriter.java#L57-L105) |
| 新⑥ | — | `resolveScene` 对未知**非空** scene **回落 LOGIN**（fail-open，放大枚举） | [AuthServiceImpl.java:606-619](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L606-L619)、[SmsScene.java:18-29](../hrm-server/src/main/java/com/qiujie/service/support/sms/SmsScene.java#L18-L29) |
| **新⑦（v1.2）** | 岗位"不入档案"（v1.1 §11.9 推荐方案甲） | **用户裁定①采纳方案乙**：`employee` 需加 `position`（C 档，已授权）；**原方案甲建议作废** | 用户裁定①、[Employee.java:19-66](../hrm-server/src/main/java/com/qiujie/entity/Employee.java#L19-L66) |
| **新⑧（v1.2）** | "发码 1109 是防枚举的合格控制" | **存量缺陷 SEC-FULL-08**：1109 与 200 的差异**本身即枚举面**；须与 M-2 合并改为**响应恒定** | [security-full-review-20260926.md](security-full-review-20260926.md) SEC-FULL-08、[AuthServiceImpl.java:264-269](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269) |
| **新⑨（v1.2）** | "契约真源可继续以 Mock 为准" | **tech-review M1 否定**：契约唯一真源收敛回 **`api.md`**（[server-architecture.md:934](server-architecture.md) 的"以 Mock 为准"废止） | [tech-review-full-20260926.md](tech-review-full-20260926.md) M1/P1-2 |
| **新⑩（v1.2）** | 迁移 max = V15、本方案 V16~V18 | **方案乙增 V19**；本方案迁移共 **V16~V19 四支** | 用户裁定①、[migration/mysql/](../hrm-server/src/main/resources/db/migration/mysql/) |
| **新⑪（v1.3）** | "`mysql/` 与 `postgresql/` **双目录各至 V15**"（v1.2 §0.4/§7） | **事实错误，已推翻**：`postgresql/` 实测**仅 `V1`、`V2`**；且 [db.md:8](db.md)/[db.md:401](db.md) 明确 **PG 冻结**。→ 本方案改**仅 mysql** 路径（M1） | `db/migration/postgresql/`（仅 `V1__init_schema.sql`、`V2__init_data.sql`）、[db.md:8](db.md)、[db.md:401](db.md) |

### 11.13 存量项合并处置：SEC-FULL-08 / SEC-FULL-18（与 M-1/M-2 统一口径）

> **来源**：[security-full-review-20260926.md](security-full-review-20260926.md)（仓库级全量审计）。**目的：避免"注册一套口径、存量一套口径"** —— 注册改造与存量缺陷**合并为同一份实现与同一份验收**。

| 存量编号 | 问题（存量，非注册引入） | **合并处置（统一口径）** | 与 M 项关系 | 批次 |
| --- | --- | --- | --- | --- |
| **SEC-FULL-08**（高） | `POST /auth/sms/send` 对「已注册/未注册」返回 200/1109 **可枚举在职手机号**（[AuthServiceImpl.java:264-269](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L264-L269)） | **发码响应恒定化**：对任意手机号，返回体 `{code,message,data}` / HTTP 状态 / 业务码 / **耗时量级一致**；未注册号**静默成功**（不真发码）；**同时覆盖 `LOGIN` 与 `REGISTER` 两场景**，一套实现 | **= M-2 的发码段**（M-2 另含"提交段不区分"，见 U-03） | B3 |
| **SEC-FULL-18**（低） | `resolveScene` 对未知场景静默回落 `LOGIN`（fail-open，[AuthServiceImpl.java:605-619](../hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java#L605-L619)） | **未知非空 scene → 拒绝（400）**；仅空/空白回落 `LOGIN` | **= M-1 同项** | B3 |
| **SEC-FULL-14**（中，关联） | `ClientLogSanitizer` 凭据正则未覆盖验证码类键名 | 正则补 `code=|smsCode=|verifyCode=|captcha=|otp=`（防误擦业务码 `code=200`） | 并入 M-8（日志与数据治理） | B3 |
| **SEC-FULL-09**（中，关联·阻塞放行） | 首登强制改密**仅前端实现**，服务端未强制 | **不在本方案实现范围**；但 M-4（不激活 + 首登强制改密）**依赖其生效** → 列为**公网放行前必闭环关联项** | 前置依赖 | **B0 复验检查项** |

**统一验收（B3）**：① 对「已注册/未注册/未绑定」三组各 ≥50 次，`/auth/sms/send` 响应逐字段一致（**不得出现 1109**）；② 未知非空场景 → 400（非 LOGIN）；③ `REGISTER` 场景对未注册号可发码且**不**走 `sendLoginCode` 账号查询分支；④ 该改造属**既有端点行为变更**，前端多端（staff-h5/boss-h5）文案须同步回归（T11）。

### 11.14 tech-review-full 必改项合并（M1 / M5）

> **来源**：[tech-review-full-20260926.md](tech-review-full-20260926.md)（项目级技术评审，结论「**有条件通过**」，须完成第 4 节必改项后报审）。**只纳入与本方案有产物依赖的项**。

| TR 编号 | 内容 | 与本方案的关系 | **本方案处置** | 批次 |
| --- | --- | --- | --- | --- |
| **TR-M1**（P1，阻塞报审） | `api.md` 契约缺口 83+ 端点；契约真源与文档错位（现落 Mock） | **直接相关**（R-6/R-8/R-9 须与既有 hr 域同章补录） | **合并**：§3.5 A/B 组 + §9 B1-C1~C6；**契约唯一真源 = `api.md`**（"以 Mock 为准"废止）；验收「差集 = 0」 | **B1** |
| **TR-M5**（P1，阻塞报审） | 后端零可运行验证（本机无 JDK/Maven） | **直接相关**（本方案后端批次无环境实跑） | **合并**：各后端批次验收口径写为「**本机无 JDK → 收敛到服务器 `mvn -q clean package` + `mvn test` + Flyway 空库迁移**，结果归档 `docs/`」 | B3/B4/B7 |
| TR-M2（P1，待安全结论） | 真实域名入库 | **归网络安全工程师判定**，不由架构师/本方案处置 | **不纳入**；仅登记"与本方案无产物依赖" | — |
| TR-M3/M4/M6/M7/M8/M9 | 可观测性 / 架构文档 / 双份实现 / 事实陈旧 / 参数登记 / 被砍模块 | 与本方案无产物依赖 | **不纳入**（各自独立批） | — |

> **报审前置说明**：TR 结论为「有条件通过」，**TR-M1/M5 未闭环不得进入验收**；本方案作为**方案阶段产物**，其自身报审须另过 **P0.6/L8 技术评审闸门**（§11 头部声明）。

***

## 附：本方案自身检查（设计侧）

| □ | 检查项 | 结论 |
| --- | --- | --- |
| □ | 一切结论有出处（文件:行号 / 条款） | ✅ 全文标注；设计判断显式标注为设计项，**无无源结论** |
| □ | 无臆造实体/字段/接口 | ✅ 「**无岗位实体**」明确声明（§0.2-⑥）；`employee.position` 为**方案乙新增列**（用户已授权），非臆造；字段/接口均标注定稿依据 |
| □ | 未改任何代码 / 未改其它文档 / 未执行 git | ✅ 本次修订**仅改本文件**（`registration-design.md`）；未改代码、未改任何其它文档、未执行 git、未调用 MCP |
| □ | 与 `api.md` / `db.md` 一致 | ✅ 体例、编号、命名沿用；差异**显式登记**：① `api.md` 计数与 83 缺口（§11.10）；② `hr_flow.status=REJECTED` 复用（§1.2/E5）；③ `employee.phone` 活跃唯一为 **D7 例外**（§11.6/U-05）；④ `apply_no` 唯一为 **D7 例外**（§2.4/U-17）；⑤ `employee.position` 为方案乙新增（§11.9）；⑥ **PG 迁移目录冻结 → 本方案仅 mysql**（§0.4/§7，与 [db.md:8](db.md)/[db.md:401](db.md) 一致，v1.3/M1） |
| □ | 需求口径未被改动 | ✅ 原文照录 §0.3；与 M-4 冲突的两点（自设密码 / 自动激活）**已由用户裁定④定稿**，非本方案擅自取舍 |
| □ | 口径类裁定齐备且定稿 | ✅ **§11.4 裁定结果表 U-01~U-17 全部有终局结果**（含依据与降级备注），**无"待裁定/替代方案"待决项**；§8.1 保留 Q→U 映射索引 |
| □ | 安全面逐条闭环 | ✅ M-1~M-9 全量采纳并**全部入批**（§11.2/§9）；S/O 处置（§11.3）；**存量 SEC-FULL-08/14/18 与 M 项合并**（§11.13）；被推翻表述已加删除线 |
| □ | 工程面（tech-review）纳入 | ✅ **TR-M1**（契约缺口）并入 §3.5/§9-B1；**TR-M5**（可运行验证）落为后端批次验收口径（§11.14）；TR-M2 明确归安全、不由本方案处置 |
| □ | 实施批次可独立验收 | ✅ §9 总表含批次/依赖/验收标准/C 档标注；**后端批次注明「本机无 JDK → 收敛到服务器 `mvn test`」** |
| □ | 方案阶段产物 → 报到主智能体前须经技术评审 | ✅ 状态标「**v1.3 必改项修订，待复评**」；v1.2 评审结论「打回」已按 M1~M4 修订（§12）；**本文件不宣称已通过评审**；须过 **P0.6/L8** 复评后报审；方案变更须重评 |
| □ | 技术评审打回必改项闭环（v1.3） | ✅ M1（PG 前提→仅 mysql）/ M2（R-6 推进 5 步口径 §4.2.1）/ M3（预检补 `phone IS NOT NULL`）/ M4（G1~G4 登记 §8.4）逐条闭环，对照见 **§12** |
| □ | 涉及 C 档动作（迁移执行 / Nginx / 去重 / 部署） | ✅ 已标注须主智能体三步授权；**本方案不执行**任何 C 档动作 |

***

## 12. 技术评审打回修订对照（v1.3 · 待复评）

> **上游结论**：[tech-review-registration.md](tech-review-registration.md) 对 **v1.2** 判**「打回」**（1 处与 `db.md` 未声明冲突 + 1 处事实错误出处 + 1 处激活路径功能缺口）。本节逐条登记必改项 M1~M4 的**落点与依据**；**结论绑定 v1.3**，本方案**不宣称已通过评审**，须复评（P0.6/L8）后方可报主智能体审批。

| 必改项 | 评审要点 | v1.3 落点 | 依据 | 闭环 |
| --- | --- | --- | --- | --- |
| **M1［阻塞］** | PG 前提错误（方案称双目录各至 V15，实测 pg 仅 V1/V2）+ 与 `db.md` 未声明冲突 | ① 主智能体裁定**走「仅 MySQL」路径**；② **显式声明「PostgreSQL 迁移目录自 V2 起冻结，本期不产出 pg 脚本与 pg 快照」**（§0.4:61、§7:564 及表头/各行、§2.2 注）；③ 复核并改写 §3 / §8.1 / §9-B2 / §11.4 / §11.6①③ / §11.9 全部「双库/pg 脚本/pg 快照」字样；④ **快照同步点仅保留 `sql/schema/mysql/`**（§7 同步表、§11.9） | [db.md:8](db.md)、[db.md:401](db.md)；`db/migration/postgresql/` 实测仅 `V1__init_schema.sql`、`V2__init_data.sql`；`db/migration/mysql/` 实测 `V1~V15` | ✅ |
| **M2［阻塞］** | R-6 不推进 `hr_flow_step`，与 U-01 自相矛盾 | ① 新增 **§4.2.1** 给出**确定口径**：R-6 单事务内**按序推进 5 步** `SUBMIT_MATERIALS→HR_REVIEW→CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY`，逐 step 复用既有 `completeOnboardingStep`（内含 `markStepDone`），**不含 `DONE`**；附 4 项不变式与代码依据；② §4.2/§4.3/§11.7/§11.8 同步改口径；③ **§9 B4 验收补**「step 状态核对（5 步 `DONE`、`current_step_key=='DONE'`、`status=='IN_PROGRESS'`）+ 激活成功（`completeOnboardingStep(DONE)` 无 9303 → `status==1`）」 | `markStepDone` 仅由 `completeOnboardingStep` 调用（[HrFlowServiceImpl.java:152](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L152)、[:474-499](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L474-L499)）；建单全步 `PENDING`、`current_step_key=SUBMIT_MATERIALS`（[:459-470](../hrm-server/src/main/java/com/qiujie/service/hr/impl/HrFlowServiceImpl.java#L459-L470)）；守卫要求目标步=首个 PENDING（[HrFlowStepGuard.java:47-50](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrFlowStepGuard.java#L47-L50)）；步骤顺序见 [HrConstants.java:58-64](../hrm-server/src/main/java/com/qiujie/service/hr/support/HrConstants.java#L58-L64) | ✅ |
| **M3［非阻塞］** | V16 预检 SQL 对 `NULL phone` 误报 | §11.6② 预检条件补 **`AND phone IS NOT NULL`**（MySQL 与 PG 留档写法同步），并加 M3 说明 | MySQL `GROUP BY phone` 对 NULL 归一组致 `COUNT(*)>1` 误报；生成列 `phone_active` 对 NULL 不去重 | ✅ |
| **M4［非阻塞］** | 风险登记补录 G1~G4 | 新增 **§8.4 G 系列风险表**：G1 step 一致性（已闭环/M2）、G2 PG 冻结冲突（已闭环/M1）、G3 `9307` 存在性 oracle（低危，登记为残余）、G4 NULL 预检误报（已闭环/M3） | 评审 §2.5 / 必改项 4 | ✅ |

**上轮派发口径纠错（U-03）：** 上轮派发描述「U-03 保留 9310」**与 v1.2 不符**——v1.2 已定稿为 **`9310` 废弃、提交段响应恒定**。本次**不回改**：方案正文与 §11.4 U-03、§3.2/§3.3/§3.4、§8.1 Q7、§11.11-5、§11.6 Mock 同步均一致保持「**废弃 9310**」（见 §11.4 U-03）。派发口述为旧口径，**以本方案 v1.3 正文为准**。

**复评范围冻结声明：** 本节仅闭环评审已列必改项，**不新增无关要求**；M1/M2 未闭环则复评维持打回。**本方案 v1.3 仍属方案阶段产物，报审前须先取得复评结论「通过 / 有条件通过」。**
