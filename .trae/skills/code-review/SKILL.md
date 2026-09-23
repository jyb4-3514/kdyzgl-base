---
name: "code-review"
description: "Reviews code for bugs, security, best practices, style. MUST invoke when user says: 审查/review/检查代码/代码审查/code review/帮我看看/检查一下, or before merging PRs, after feature completion. Also triggers on: 提交前/合并前/上线前检查. Based on awesome-agent-skills community."
---

# 代码审查技能 (Code Review)

> 源自社区最佳实践（awesome-agent-skills 合集），覆盖代码质量、安全、性能、可维护性四大维度。

## 触发条件

当用户明确提出以下要求时启用：
- "审查代码"、"review"、"检查代码"
- 合并 PR 前
- 功能开发完成后
- 发现可疑代码时

## 审查维度

### 1. 安全（最高优先级）
- [ ] 无硬编码密钥、密码、Token
- [ ] SQL 使用参数化查询，无字符串拼接
- [ ] 写操作有鉴权 + 越权校验
- [ ] 敏感字段已脱敏返回
- [ ] 无调试端点暴露到生产环境

### 2. 正确性
- [ ] 逻辑正确，边界条件覆盖
- [ ] 空值/NPE 处理
- [ ] 异常不吞、不裸抛
- [ ] 并发安全（共享资源加锁）

### 3. 性能
- [ ] 无 N+1 查询
- [ ] 大数据量操作有分页
- [ ] 避免循环内数据库调用
- [ ] 合理使用缓存

### 4. 可维护性
- [ ] 命名规范（Java 驼峰、DB snake_case）
- [ ] 单一职责，函数不过长
- [ ] 重复代码已抽取
- [ ] 注释写"为什么"而非"做什么"

### 5. 项目规范
- [ ] 分层正确（controller/service/mapper/entity）
- [ ] 统一响应格式 `{ code, message, data }`
- [ ] RESTful 命名（复数名词）
- [ ] Commit message 符合规范

## 审查报告模板

```
## 代码审查报告

### 严重问题（必须修复）
- [ ] {文件路径#L行号} - {问题描述} → {修复建议}

### 建议改进（推荐修复）
- [ ] {文件路径#L行号} - {问题描述} → {改进建议}

### 正面反馈
- {做得好的地方}

### 评分
- 安全性：X/10
- 正确性：X/10
- 性能：X/10
- 可维护性：X/10
```

## 自动修复

对于以下类型的问题，审查后可直接修复：
- 命名不规范
- 缺少 `@Override` 注解
- 未使用的 import
- 明显的空指针风险（加 null 检查）

涉及业务逻辑的修复需要用户确认后再改。