---
name: "long-task-optimizer"
description: "Optimizes long-running multi-step AI tasks with checkpoint/resume, task decomposition, context compaction, progressive disclosure, file-based memory, sub-agent isolation, progress tracking, and failure recovery. MUST invoke when user says: 长线任务/长期任务/多步骤/分阶段/迭代/多期/跨会话/断点续传/checkpoint/恢复进度, or when task spans > 5 steps, multi-session work, or multi-phase project development. Based on agent-loop, agentpause, and Simon Last's methodology."
---

# 长线任务优化技能 (Long Task Optimizer)

> 综合 [ugubser/agent-loop](https://github.com/ugubser/agent-loop)、[Champoleello/agentpause](https://github.com/Champoleello/agentpause)、Simon Last 13 天持续 Agent 方法论、Agent Failure Taxonomy 的最佳实践。

## 触发条件

- 任务超过 5 个步骤
- 跨会话/跨天的持续开发（如"一期→二期→三期"）
- 用户说：长线任务、长期、多步骤、分阶段、迭代、断点续传、恢复进度、checkpoint
- 上下文即将耗尽需要交接

## 核心原则：上下文不堆对话里，堆文件里

对话只保留当前任务的"指针"，需要时再从文件加载。用文件系统做持久化记忆，用对话做工作内存。

---

## 六大核心机制

### 1. 任务拆分（Atomic Decomposition）
- 每个子任务 ≤ 30~60 分钟工作量
- 有明确验收标准（完成即关）
- 显式声明依赖关系

```
□ 子任务1: {描述} — 依赖: 无 — 验收: {条件}
□ 子任务2: {描述} — 依赖: 子任务1 — 验收: {条件}
□ 子任务3: {描述} — 依赖: 子任务1,2 — 验收: {条件}
```

### 2. 渐进式加载（Progressive Disclosure）— 省 token 核心

**原则：不一次性加载全部，按阶段按需加载。**

```
子智能体启动时只加载自己需要的技能：
  后端工程师：token-optimizer + engineering-discipline + tdd-development + spring-boot-expert
  不加载：vue-expert、ui-ux-design、database-design 等无关技能

子智能体执行时只加载当前阶段需要的规则：
  编码阶段：加载 spring-boot-expert 的编码规范
  不加载：spring-boot-expert 的部署规范（那是运维的事）
```

**规则：** 子智能体 SKILL.md 只列出必须的技能，不列可选的。主智能体分派任务时只传必要上下文。

### 3. 检查点机制（Checkpoint/Resume）
- 每完成一个子任务，写入 `SESSION-STATE.md`（项目根目录）
- 新会话启动时先读 `SESSION-STATE.md` 恢复进度
- 崩溃/中断后从最近检查点恢复，不重复已完成工作

**格式：**
```markdown
# 会话状态 — {任务名称}
> 最后更新: {时间}

## 进度
- [x] 子任务1: {描述} — 完成于 {时间}
- [ ] 子任务2: {描述} — 进行中

## 关键决策
- {决策及理由}

## 已修改文件
- `path/to/file` — {改动简述}

## 注意事项
- {陷阱/约束}
```

### 4. 上下文压缩（Context Compaction + 文件外存）

**触发：上下文 > 70%**

| 内容 | 处理方式 |
|------|---------|
| 已完成步骤 | 写入 SESSION-STATE.md 一行摘要，对话中丢弃 |
| 关键决策 | 保留在对话 |
| 已读文件内容 | 丢弃，需要时重新读取 |
| 工具调用详情 | 丢弃，只保留结果摘要 |
| 冗余探索过程 | 丢弃 |

**压缩验证清单：**
- [ ] 当前子任务目标已写入摘要
- [ ] 最近 3 步操作已记录
- [ ] 关键决策已保留
- [ ] 待办列表完整
- [ ] 已修改文件列表完整

### 5. 子智能体隔离（Sub-agent Isolation）— 省 token 50-70%

**原则：每个子智能体在独立上下文中运行，主智能体只看产出结果。**

```
主智能体                    子智能体
  │                           │
  │── 精简任务描述 + 必要文件 ──→│ 独立上下文运行
  │                           │ 加载自己的技能
  │                           │ 产出结果
  │←──── 最终产出摘要 ────────│
  │                           │
  │ 只看结果，不看过程
```

**隔离规则：**
- 子智能体不继承主对话的全部上下文
- 主智能体分派时只传：任务目标 + 相关文件路径 + 约束条件
- 子智能体返回时只传：产出摘要 + 改动文件列表
- 子智能体内部细节（探索过程、中间尝试）不传回主智能体

### 6. 失败恢复（Failure Recovery）
- 同一操作失败 2 次 → 强制换策略，禁止同样参数重试
- 失败子任务最多重试 3 次，超限移交人工审核

```
1 次失败 → 分析原因，调整参数重试
2 次失败 → 换策略/换工具/换思路
3 次失败 → 标记阻塞，写清原因，跳过继续
全部完成后 → 汇总阻塞项，提交人工审核
```

---

## 省 Token 专项策略

### 批量合并
独立工具调用合并为一次：读文件时并行读取，搜索时合并关键词，写文件时批量操作。

### 增量交付
- 不重复输出已有代码，只输出 diff/改动
- 修改文件用 Edit 工具（精准替换），不用 Write 重写整个文件
- 读文件只读需要的行范围，不读全文件

### 结构化输出
- 结论先行，细节后置
- 代码改动用"文件路径 + 改动简述"格式
- 去掉"首先/然后/接下来"等过渡词

---

## 反模式

| 陷阱 | 表现 | 对策 |
|------|------|------|
| 一次性加载全部 | 启动就加载所有技能/规则 | 渐进式加载，按需取用 |
| 上下文堆砌 | 所有历史都留在对话里 | 写入文件，对话只保留指针 |
| 递归思考循环 | 一直在规划不执行 | 规划限时，超时强制执行 |
| 上下文漂移 | 压缩后忘记目标 | 压缩后验证核心目标未丢失 |
| 重复劳动 | 忘记已做过的步骤 | 严格读 SESSION-STATE.md |
| 策略僵化 | 同样参数反复重试 | 2 次失败强制换策略 |
| 质量滑坡 | 长任务后期偷懒 | 每步验收标准不变 |
| 子智能体传回全量 | 返回完整上下文 | 只返回产出摘要 + 改动列表 |

## 与现有技能的协作

- `token-optimizer`：上下文压缩时联动，输出精简
- `engineering-discipline`：子任务内部走 spec→plan→build→test→review→ship
- `code-review`：每个子任务完成后审查
- `tdd-development`：涉及后端代码的子任务走 TDD