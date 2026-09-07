# 变更日志

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
