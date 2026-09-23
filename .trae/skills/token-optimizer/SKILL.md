---
name: "token-optimizer"
description: "PRE-CALL MANDATORY: MUST invoke at the START of EVERY task before any LLM interaction. Compresses output ~65% caveman-style, prevents token waste from the beginning. Also AUTO-trigger when context > 60% (L2) or > 80% (L3). Triggers on: 简洁/简短/直接说/别啰嗦/精简/压缩/省token. Based on JuliusBrussee/caveman (99K+ stars)."
---

# Token 优化技能 (Caveman Style)

> 源自 [JuliusBrussee/caveman](https://github.com/JuliusBrussee/caveman)（99K+ Stars），Claude Code 技能，可压缩约 65% token。
> 核心理念：用最少的词表达最多的信息。

## 触发条件

以下情况自动启用本技能：
- 上下文使用量超过 60%
- 用户明确要求"简洁"、"简短"、"直接说"
- 多步骤长任务的中后期
- 输出内容明显冗余时

## 压缩规则

### 代码输出
- 去掉无意义注释（`// 设置用户名` 这种废话）
- 保留 `// TODO(扩展): 说明` 标记
- 合并简单赋值和返回
- 去掉冗余空行（保留逻辑分组的一个空行）

### 文字输出
- 去掉"首先"、"然后"、"接下来"等过渡词
- 结论先行，不铺垫
- 一句话能说清的不说三句
- 不重复用户已知信息

### 文件操作
- 读文件只读需要的行范围
- 搜索用精确关键词，不用模糊词
- 批量操作合并为一次调用

## 反模式（禁止）

```
❌ "让我来仔细分析一下这个问题，首先我们需要理解..."
❌ "根据我的分析，我认为最佳的方案是..."
❌ "这是一个很好的问题，让我来详细解释..."
❌ 输出代码后加一段"这个实现做了X、Y、Z"的解释
❌ 重复用户刚说过的话作为开头
```

## 正模式（推荐）

```
✅ 直接给结论 + 关键理由
✅ 代码改动 + 一句话说明改了什么
✅ "已完成。改动：{文件} - {简述}"
✅ 遇到不确定时："不确定，需要查 {来源}"
```

## 压缩等级

| 等级 | 触发条件 | 行为 |
|------|---------|------|
| L1 轻度 | 默认 | 正常输出，去掉冗余废话 |
| L2 中度 | 上下文 > 60% | 结论先行，不铺垫，合并工具调用 |
| L3 重度 | 上下文 > 80% | 极简模式，只输出关键决策和代码改动 |