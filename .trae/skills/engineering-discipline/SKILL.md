---
name: "engineering-discipline"
description: "Enforces spec→plan→build→test→review→ship workflow. MUST invoke when user says: 开发/新增/实现/重构/修复/改bug/写代码/feature/fix/refactor/feat, or any non-trivial dev task. Also triggers on: 工程规范/开发流程/按流程来. Based on addyosmani/agent-skills (97K+ stars)."
---

# 工程纪律技能 (Engineering Discipline)

> 源自 [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills)（97K+ Stars），Google 工程总监 Addy Osmani 出品。
> 将 20 年 Google 工程经验浓缩为 AI 可执行的 SOP。

## 触发条件

当用户提出以下类型请求时，自动启用本技能：
- 开发新功能（`/feature`、`feat:`、新增、实现）
- 重构代码（`/refactor`、重构、优化结构）
- 修复 Bug（`/fix`、修复、调试）
- 任何非简单查询的开发任务

## 核心流程：spec → plan → build → test → review → ship

### 1. Spec（规格说明）
- 明确需求范围，确认边界条件
- 识别影响范围（哪些模块/文件会被改动）
- 产出简短的需求描述（3-5 句话即可）

### 2. Plan（计划）
- 拆解为实现步骤，每步可独立验证
- 先探索现有代码，理解上下文再动手
- 输出计划给用户确认后再执行

### 3. Build（构建）
- 最小化改动：只改必要的文件，不碰无关代码
- 先写核心逻辑，再补边界处理
- 遵循项目现有代码风格和分层规范

### 4. Test（测试）
- 自测每个改动点：编译通过、逻辑正确、边界覆盖
- 涉及数据库变更时，确认迁移脚本正确
- 验证不影响已有功能

### 5. Review（审查）
- 检查是否有多余的调试代码、硬编码密钥
- 检查命名规范、注释质量
- 确认文档同步更新（db.md / api.md / plan.md）

### 6. Ship（交付）
- 生成规范的 commit message（`类型: 描述`）
- 确认合并目标分支正确
- 涉及部署时，走安全评估流程

## 反 AI 偷懒检测（Anti-Rationalization）

以下迹象出现时，说明 AI 在偷懒，必须制止：
- "这个很简单" → 没有深入分析
- "看起来没问题" → 没有实际验证
- "应该可以" → 没有确认
- 跳过测试直接说"已完成" → 重新执行 test 步骤
- 一次性输出大量代码不解释 → 要求分步说明

## 输出规范

- 每个步骤完成后给出简短状态更新
- 遇到阻塞立即报告，不强行推进
- 代码改动附文件路径引用