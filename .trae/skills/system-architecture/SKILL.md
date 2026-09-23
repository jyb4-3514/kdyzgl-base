---
name: "system-architecture"
description: "系统架构设计专业技能。覆盖架构模式选型、技术栈评估、系统分解、非功能需求、ADR决策记录。源自 Licensed-Orphan/agentic-pipeline + elihuvillaraus/skills/architect。触发：架构设计/系统设计/技术选型/方案评审/架构图/ADR/高可用/扩展性。"
---

# 系统架构设计技能

> 综合 [Licensed-Orphan/agentic-pipeline](https://github.com/Licensed-Orphan/agentic-pipeline) 的架构阶段方法论 +
> [elihuvillaraus/skills](https://github.com/elihuvillaraus/skills) 的 architect 技能 +
> [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills) 的工程纪律。

## 核心流程：需求 → 架构 → 分解 → 验证

### 1. 需求分析（Research → Architecture）
- 上游输入：PRD / 需求文档 / 用户故事
- 产出：架构约束矩阵 + 非功能需求清单
- 方法：MECE 分解（Mutually Exclusive, Collectively Exhaustive）

### 2. 架构设计（Architecture Design）
按以下维度逐一决策，每项决策写入 ADR（Architecture Decision Record）：

| 维度 | 决策点 | 本项目默认 |
|------|--------|-----------|
| 架构风格 | 单体/微服务/模块化单体 | 模块化单体（Spring Boot 多模块） |
| 通信方式 | REST/gRPC/消息队列 | REST + Redis 缓存 |
| 数据存储 | MySQL/PostgreSQL/NoSQL | MySQL + PostgreSQL 双 schema |
| 部署方式 | 容器/虚拟机/Serverless | 宝塔 + Linux（腾讯云） |
| 前端架构 | SPA/SSR/混合 | Vue SPA + 安卓 H5 壳 |

### 3. 系统分解（System Decomposition）
- 模块边界：清晰的接口契约 + 依赖方向
- 数据边界：每个模块的数据所有权
- 通信边界：同步/异步/事件驱动

```
分解模板：
├── 模块A: {职责} — 依赖: 无 — 对外接口: {API列表}
├── 模块B: {职责} — 依赖: 模块A — 对外接口: {API列表}
└── 模块C: {职责} — 依赖: 模块A,B — 对外接口: {API列表}
```

### 4. 非功能需求（NFR）检查清单
- [ ] 性能：QPS 目标、响应时间 P99、并发数
- [ ] 可用性：SLA 目标（99.9%？）、故障转移策略
- [ ] 扩展性：水平/垂直扩展方案、瓶颈识别
- [ ] 安全性：认证授权、数据加密、SQL 注入防护
- [ ] 可维护性：日志、监控、告警、文档
- [ ] 数据一致性：强一致/最终一致、事务边界

### 5. ADR 模板

```markdown
# ADR-{序号}: {标题}
- 状态: 提议/已采纳/已废弃
- 日期: YYYY-MM-DD
- 上下文: {问题描述}
- 决策: {选择什么方案}
- 后果: {正面影响 + 负面影响 + 风险}
- 备选方案: {考虑过但未选的方案及原因}
```

## 反模式

- ❌ 过度设计：一期不需要微服务，不需要分布式事务
- ❌ 技术炫技：选最熟悉的而非最酷的
- ❌ 忽略运维：架构设计不考虑部署和监控
- ❌ 无文档：只画图不写 ADR，后续无法追溯决策理由

## 参考来源

- [Licensed-Orphan/agentic-pipeline](https://github.com/Licensed-Orphan/agentic-pipeline) — 架构阶段方法论
- [elihuvillaraus/skills/architect](https://github.com/elihuvillaraus/skills/tree/main/architect) — 架构师技能
- [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills) — Google 工程总监出品