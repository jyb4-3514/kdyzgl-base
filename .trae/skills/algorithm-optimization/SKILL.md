---
name: "algorithm-optimization"
description: "算法优化与路径规划技能。覆盖调度算法、路径优化(Dijkstra/A*/RRT*)、预测统计、性能分析、复杂度评估。源自 Salesforce/agentforce-adlc + turlockmike/optimization-playbook + TimefoldAI/timefold-solver。触发：算法/调度/路径/优化/预测/统计/复杂度/性能分析。"
---

# 算法优化与路径规划

> 综合 [SalesforceAIResearch/agentforce-adlc](https://github.com/SalesforceAIResearch/agentforce-adlc) 的 Agent 生命周期方法论 +
> [turlockmike/optimization-playbook](https://gist.github.com/turlockmike/f755fa8f2c45375891c8c8c3acbf165d) 的优化手册 +
> [TimefoldAI/timefold-solver](https://github.com/TimefoldAI/timefold-solver) 的约束求解器。

## 算法设计流程

### 1. 问题建模（Problem Formulation）
- 输入：业务需求 → 数学模型
- 明确目标函数（最小化成本/最大化效率/最短路径）
- 明确约束条件（时间窗口、容量限制、优先级）
- 明确输入数据结构与规模

### 2. 算法选型决策树

```
路径规划：
├── 单源最短路径 → Dijkstra（非负权）/ Bellman-Ford（负权）
├── 全源最短路径 → Floyd-Warshall（稠密）/ Johnson（稀疏）
├── 启发式搜索 → A*（有启发函数）/ IDA*（内存受限）
├── 动态环境 → D* Lite / RRT*（连续空间）
└── 多目标优化 → NSGA-II / MOEA/D

调度优化：
├── 单机调度 → 贪心/动态规划
├── 并行调度 → 遗传算法/模拟退火
├── 约束满足 → Timefold/OR-Tools CP-SAT
└── 实时调度 → EDF/RMA

预测统计：
├── 时间序列 → ARIMA/Prophet/LSTM
├── 分类 → XGBoost/LightGBM
├── 聚类 → K-Means/DBSCAN
└── 异常检测 → Isolation Forest/LOF
```

### 3. 复杂度评估

| 算法 | 时间 | 空间 | 适用规模 |
|------|------|------|---------|
| Dijkstra | O(E log V) | O(V) | V < 10^6 |
| A* | O(E) | O(V) | V < 10^6（启发函数好） |
| Floyd-Warshall | O(V³) | O(V²) | V < 500 |
| 遗传算法 | O(G*P*F) | O(P) | 取决于适应度函数 |
| 线性规划 | O(n³) | O(n²) | n < 10^4 |

## 驿站场景专项

### 包裹路径规划
- 场景：N 个驿站、M 个包裹、K 辆配送车
- 目标：最小化总配送距离/时间
- 约束：时间窗口、车辆容量、驿站优先级
- 推荐：先聚类（K-Means 分组驿站）→ 再 TSP（每组内路径优化）

### 调度优化
- 场景：员工排班、包裹分配
- 目标：最大化利用率、最小化等待时间
- 约束：工时限制、技能匹配、优先级
- 推荐：Timefold 约束求解器 / OR-Tools

## 性能优化原则

源自 [turlockmike/optimization-playbook](https://gist.github.com/turlockmike/f755fa8f2c45375891c8c8c3acbf165d)：

1. **测量先行**：先 profile 再优化，不猜瓶颈
2. **算法优先**：O(n²)→O(n log n) 的收益远大于语言优化
3. **缓存为王**：空间换时间，合理使用 Redis/内存缓存
4. **批量处理**：减少 I/O 次数，批量读写
5. **懒计算**：延迟计算，只算需要的

## 反模式

- ❌ 过度优化：数据量 < 1000 时不需要复杂算法
- ❌ 忽略常数：O(n) 但常数巨大的算法不如 O(n log n) 常数小的
- ❌ 不测量就优化：优化不需要优化的地方
- ❌ 忽略实际数据分布：理论最优 ≠ 实际最优

## 参考来源

- [SalesforceAIResearch/agentforce-adlc](https://github.com/SalesforceAIResearch/agentforce-adlc) — Agent 生命周期
- [turlockmike/optimization-playbook](https://gist.github.com/turlockmike/f755fa8f2c45375891c8c8c3acbf165d) — 优化手册
- [TimefoldAI/timefold-solver](https://github.com/TimefoldAI/timefold-solver) — 约束求解器