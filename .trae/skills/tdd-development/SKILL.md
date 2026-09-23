---
name: "tdd-development"
description: "Enforces TDD: red→green→refactor. MUST invoke when user says: 写测试/TDD/测试驱动/先写测试/单元测试/加测试/test/测试用例, or when developing backend APIs, service layer, or data access code. Also triggers on: 保证质量/高质量代码. Based on obra/superpowers (264K+ stars)."
---

# TDD 强制开发技能 (Test-Driven Development)

> 源自 [obra/superpowers](https://github.com/obra/superpowers)（264K+ Stars），Claude Code 生态安装量最高的非官方插件（752K+）。
> 被超过 150K 开发者纳入日常工作流。

## 触发条件

当用户明确提出以下要求时启用：
- "写测试"、"TDD"、"测试驱动"
- "先写测试再写代码"
- "保证代码质量" + 涉及核心逻辑改动
- 后端接口开发、数据库操作、业务逻辑层

## 核心流程：Red → Green → Refactor

### Step 1: Red（写失败的测试）
- 先写测试用例，描述期望行为
- 测试必须能运行且**预期失败**（因为实现还没写）
- 测试覆盖：正常路径 + 边界条件 + 异常情况

### Step 2: Green（最小实现让测试通过）
- 写最少代码让测试通过
- 不过度设计，不提前优化
- 每个测试通过即提交一次

### Step 3: Refactor（重构优化）
- 测试全部通过后，再优化代码结构
- 消除重复、改善命名、提取公共逻辑
- 重构后重新跑测试确保持续通过

## 测试用例设计模板

```
功能：{功能描述}
场景：{场景描述}
  Given {前置条件}
  When  {触发动作}
  Then  {预期结果}

示例：
功能：员工登录
场景：使用正确凭据登录
  Given 员工账号已启用且密码正确
  When  调用登录接口
  Then  返回 token 且状态码 200
```

## 调试流程（6 步证据优先法）

1. **复现**：确认问题可稳定复现
2. **隔离**：缩小范围到最小可复现单元
3. **假设**：基于证据提出根因假设
4. **验证**：用测试验证假设
5. **修复**：实施修复
6. **回归**：跑全部测试确认无副作用

## 注意

- 本技能适用于 Java 后端（JUnit/Mockito）和前端（Vitest/Jest）
- 测试文件命名：`*Test.java`（后端）、`*.test.ts`（前端）
- 不要求 100% 覆盖率，但核心业务逻辑必须覆盖