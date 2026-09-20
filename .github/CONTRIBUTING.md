# 快递驿站智汇系统 · 项目协作与开发规范

> Java + Spring Boot 后端 | Vue 管理前端 | 安卓 H5 壳 | 宝塔部署 | Trae AI 辅助
> 迭代路线：一期员工管理 → 二期驿站数据同步 → 三期工单
> 代码托管：**Gitee**（国内稳定，服务器直连拉取）
> 全局通用准则见 `全局规则.md`，本文件仅定义项目特有约束。

## 核心原则

1. **反幻觉：** 遇到不确定的 API、语法、命令、版本，必须查阅官方源文档，禁止编造、禁止凭记忆臆断。查不到时说"不确定"并给出验证方案。
2. **精简优先：** 完成功能前提下，以精简并删除历史冗余代码为荣，以堆砌重复实现为耻。同一逻辑不得重复实现三次以上。
3. **中文注释：** 所有注释使用中文，写"为什么"不写废话。`TODO(扩展): 说明` 标注预留代码。

## 0. 对话对齐（每轮对话启动时执行）

> 新会话首轮工具调用中，必须完成项目对齐，确保不跑偏。

**对齐步骤：**

1. 读取 `SESSION-STATE.md`（如存在）→ 恢复上次进度
2. 读取 `hrm-dev/docs/plan.md` → 确认当前迭代阶段
3. 确认当前分支 → 确保在正确的 feature/dev 分支上
4. 对齐后一句话输出当前状态：`[对齐] 当前阶段：一期/员工管理 | 进度：{x}/{y} | 分支：{branch}`

**输出格式：**
```
[项目对齐] 一期员工管理 | 进度：3/8 | 分支：feature/员工管理模块
```

## 技术栈与仓库结构

| 层 | 技术 | 路径 |
|----|------|------|
| 后端 | Java + Spring Boot | `hrm-dev/hrm-server/` |
| 数据库 | MySQL / PostgreSQL + Flyway | `hrm-dev/sql/schema/` |
| 缓存 | Redis | — |
| 管理前端 | Vue + Vue Router + Vuex | `hrm-dev/hrm-admin/` |
| 移动端 | 安卓 H5 壳 | 内嵌 H5，走同一后端 API |
| 部署 | 宝塔 + Linux（腾讯云） | 生产环境只拉 Git 代码 |
| 文档 | db / api / plan / deploy | `hrm-dev/docs/` |

## 1. 分支与工作流

| 分支 | 用途 | 规则 |
|------|------|------|
| `main` | 生产环境 | **禁止直接提交**，只接受 `dev` 合并 |
| `dev` | 日常开发 | 所有功能汇入这里 |
| `feature/*` | 功能分支 | 从 `dev` 切出，完成后合并回 `dev` |
| `fix/*` | 缺陷修复 | 从 `dev` 或 `main` 切出 |

**流程：** `feature 分支开发 → 自测 → 合并 dev 联调 → 验证通过 → 合并 main，打 tag → 服务器 pull 部署`

**命名：** `feature/员工管理模块` `fix/登录bug` | **版本标签：** `v1.0.0-一期员工管理`

## 2. Commit 规范

格式：`类型: 中文简短描述`（动词开头，50 字以内）

| 类型 | 含义 | 类型 | 含义 |
|------|------|------|------|
| `feat` | 新增功能 | `fix` | 修复 bug |
| `refactor` | 代码重构 | `docs` | 文档更新 |
| `style` | 格式调整 | `test` | 测试代码 |
| `chore` | 构建/部署/配置 | | |

**禁止** `更新代码` `改bug` `fix bug` 等模糊信息。

## 3. 环境与配置

| 环境 | 分支 | 数据库 | 用途 |
|------|------|--------|------|
| 本地 | feature 分支 | 本地测试库 | Trae 本地运行 |
| 测试 | 服务器 `dev` | 测试库 | 联调 |
| 生产 | 服务器 `main` | 正式库 | 对外服务 |

**红线：** 配置文件（`.env*` `application-*.yml` `*.pem` `*.key` `*.jks` 数据库备份）绝不进 Git。仓库只提交 `env.example` 模板（无真实密码）。服务器通过本地配置/环境变量读取密码。

**生产域名：** `kongzhen1.com`（HTTPS，宝塔 Nginx 反向代理，证书走 Let's Encrypt 自动续签）。前端接口基址与后端 CORS 白名单统一以该域名配置。

## 4. 部署与安全

1. 合并 `main` → 打 tag → 服务器 `git pull` → 编译 → 重启
2. 上线前备份数据库
3. 只允许 `git pull`，禁止 AI 直接在服务器写源码
4. 宝塔 MCP / SSH MCP 操作须经主智能体审批：安全评估 → 应急预案 → 确认执行。高危操作（删除/重启/数据库变更）必须人工确认
5. 回滚：切换到历史 tag → 恢复上一版 Jar 包

## 5. 数据库规范

- 建表脚本存 `hrm-dev/sql/schema/{mysql,postgresql}/`，Flyway 迁移存 `hrm-server/src/main/resources/db/migration/`
- 只存 DDL，不放业务数据
- 禁止直接改线上表结构，变更先写 SQL 脚本版本化管理
- 命名：snake_case，主键 `id`，时间 `create_time`/`update_time`，索引 `idx_表名_字段`

## 6. 安全红线

1. ❌ 服务器 IP、MCP 密钥、数据库密码、JWT 密钥提交 Git
2. ❌ 直接向 `main` 提交
3. ❌ 生产服务器直接编辑源码
4. ❌ 提交真实业务数据（包裹/员工档案/数据库备份）

## 7. 提交前自检

- [ ] 人工 Review，无调试输出、无硬编码密钥
- [ ] Commit 符合「类型: 描述」规范
- [ ] 未提交敏感文件
- [ ] 表结构变更已写入 `sql/schema/` 脚本
- [ ] 相关 `docs/` 文档已同步更新
- [ ] 合并目标分支正确

## 8. 智能体调用规范

> 遇到对应任务必须通过 Agent 调用对应子智能体，禁止主对话直接产出。

| 任务 | 智能体 | Agent 标识 |
|------|--------|-----------|
| 架构设计/技术选型/方案评审 | 架构师 | `express-station-architect` |
| 后端接口/业务逻辑/数据同步 | 后端工程师 | `express-station-backend-engineer` |
| UI/UX 设计/设计系统/视觉规范 | UI/UX 设计师 | `express-station-ui-ux-designer` |
| 前端/Vue/H5/UI 实现 | 前端工程师 | `express-station-frontend-engineer` |
| 算法/调度/路径优化/预测 | 算法工程师 | `express-station-algorithm-engineer` |
| 建表/SQL/Flyway/索引 | 数据库工程师 | `express-station-database-engineer` |
| 测试/用例/Bug 复现 | 测试工程师 | `express-station-test-engineer` |
| 部署/CI/CD/宝塔/Nginx/监控 | 运维工程师 | `express-station-ops-engineer` |

**权限：** 主智能体 = 最高权限，负责需求拆解、编排调度、安全审查、代码 Review。MCP 工具由主智能体独占调用。子智能体产出须经主智能体 Review 后提交。上下文 > 80% 自动压缩。

### 8.1 设计 → 前端 协作链路

> **硬性要求：** 涉及界面视觉/交互的任务，必须"先设计后实现"，禁止前端跳过设计直接写样式。

```
需求 → UI/UX 设计师（出设计系统/规范/视觉稿）→ 主智能体 Review → 前端工程师（实现 Vue 组件）→ 测试
```

| 阶段 | 负责 | 产出 |
|------|------|------|
| 设计 | UI/UX 设计师 | 设计方向、Design Tokens、组件规范、视觉稿 |
| 审核 | 主智能体 | 设计评审通过 |
| 实现 | 前端工程师 | Vue 组件（严格按 Tokens 落地） |
| 验证 | 测试工程师 | 视觉/交互/响应式验收 |

**必须派 UI/UX 设计师的场景：** 新增页面或组件、设计系统与 Design Tokens、配色/字体/布局规范、H5 移动端适配、动效与微交互、无障碍与对比度、视觉走查与还原度评审。

**可直接由前端工程师处理的场景：** 按既有设计系统改文案/间距等微调、纯逻辑修复、已有组件复用装配。

## 9. AI 技能强制启用

### 9.1 通用强制技能（主智能体 + 子智能体）

| 技能 | 时机 | 行为 |
|------|------|------|
| `token-optimizer` | **每次任务第一轮**（最高优先级） | L1 去废话 → L2 结论先行 → L3 极简 |
| `engineering-discipline` | 开发/重构/修复类请求 | spec→plan→build→test→review→ship |
| `tdd-development` | 后端接口/Service/Mapper 编码前 | red→green→refactor |
| `code-review` | 提交前/合并前/上线前 | 安全/正确性/性能/可维护性四维审查 |
| `long-task-optimizer` | 任务 > 5 步/跨会话/多期迭代 | 任务拆分→检查点→压缩→失败恢复→进度追踪 |

### 9.2 领域专业技能（子智能体按角色加载）

| 技能 | 角色 | 来源 |
|------|------|------|
| `system-architecture` | 架构师 | Licensed-Orphan/agentic-pipeline + elihuvillaraus/skills/architect |
| `spring-boot-expert` | 后端工程师 | rrezartprebreza/spring-boot-skills + zander-zyx/java-development-skill |
| `vue-expert` | 前端工程师 | vuejs-ai/skills + KIMJINWOO4/vue-skills |
| `ui-ux-design` | UI/UX 设计师 | nextlevelbuilder/ui-ux-pro-max-skill(62.6K Stars) + plugin87/ux-ui-agent-skills + superdesigndev/superdesign-skill |
| `algorithm-optimization` | 算法工程师 | Salesforce/agentforce-adlc + TimefoldAI/timefold-solver |
| `database-design` | 数据库工程师 | docfork/db-skills + sanjay3290/ai-skills |
| `qa-lifecycle` | 测试工程师 | kao273183/qa-claude-skill + petrkindlmann/qa-skills |
| `devops-pipeline` | 运维工程师 | abdullahkhawer/devops-skills + pfangueiro/claude-code-agents |

### 9.3 执行纪律

1. 第一轮工具调用必须加载匹配技能，不得先动手再补加载
2. 严格按技能内定义流程执行，不得跳过步骤
3. 多技能同时匹配时全部加载
4. 宁可多加载不可漏加载
5. 子智能体按各自 SKILL.md 完整工作流执行，每阶段有明确产出