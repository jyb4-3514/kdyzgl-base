---
name: "qa-lifecycle"
description: "测试全生命周期技能。覆盖测试策略、用例设计、单元测试(JUnit5/Mockito)、接口测试、Bug复现、测试报告。源自 kao273183/qa-claude-skill(32技能) + petrkindlmann/qa-skills(50技能)。触发：测试/用例/单元测试/接口测试/集成测试/回归测试/Bug复现/JUnit/JMockit/覆盖率。"
---

# 测试全生命周期

> 综合 [kao273183/qa-claude-skill](https://github.com/kao273183/qa-claude-skill)（32 个生产级 QA 技能） +
> [petrkindlmann/qa-skills](https://github.com/petrkindlmann/qa-skills)（50 个 QA 技能） +
> [obra/superpowers](https://github.com/obra/superpowers) 的 TDD 方法论。

## 测试金字塔

```
        ┌──────┐
        │ E2E  │ ← 少量，关键流程
       ┌┴──────┴┐
       │ 集成测试 │ ← 中等，接口/DB 交互
      ┌┴────────┴┐
      │  单元测试  │ ← 大量，快速反馈
     └───────────┘
```

## 测试策略（Test Strategy）

### 测试四象限
| 象限 | 类型 | 目标 | 工具 |
|------|------|------|------|
| Q1 | 单元测试 | 代码正确性 | JUnit 5 + Mockito |
| Q2 | 接口测试 | API 契约 | MockMvc / RestAssured |
| Q3 | 集成测试 | 系统交互 | Testcontainers / H2 |
| Q4 | 验收测试 | 业务验收 | 人工 / 自动化脚本 |

### 覆盖率目标
- 单元测试覆盖率 ≥ 80%
- Service 层覆盖率 ≥ 90%
- 关键业务路径 100% 覆盖

## 单元测试规范（JUnit 5 + Mockito）

### AAA 模式
```java
@Test
void calculateSalary_withOvertime_returnsCorrectTotal() {
    // Arrange - 准备数据
    Employee employee = new Employee();
    employee.setBaseSalary(new BigDecimal("5000"));
    employee.setOvertimeHours(10);
    when(overtimeRule.calculate(10)).thenReturn(new BigDecimal("500"));

    // Act - 执行
    BigDecimal result = salaryService.calculate(employee);

    // Assert - 验证
    assertThat(result).isEqualByComparingTo(new BigDecimal("5500"));
    verify(overtimeRule).calculate(10);
}
```

### 必须覆盖的场景
| 场景 | 说明 |
|------|------|
| 正常路径 | 标准输入 → 期望输出 |
| 边界值 | null、空集合、0、最大值、最小值 |
| 异常路径 | 参数非法、状态异常、业务规则不满足 |
| 并发 | 多线程场景（如适用） |

### Mock 使用规则
- 只 Mock 外部依赖（Mapper、Feign、Redis）
- 不 Mock 被测类本身
- 不 Mock 值对象（DTO/VO/Entity）
- `verify()` 验证关键交互

## 接口测试规范

```java
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void getEmployee_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/employees/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("张三"));
    }
}
```

## Bug 复现流程

1. **记录**：Bug 现象、触发条件、环境信息
2. **隔离**：最小化复现步骤，排除干扰因素
3. **定位**：二分法缩小范围，定位根因
4. **修复**：先写复现测试 → 再修复代码 → 测试通过
5. **回归**：确认修复不引入新问题

## 测试报告模板

```markdown
## 测试报告 — {模块名} v{版本}

### 测试概览
- 测试用例总数：{N}
- 通过：{P} / 失败：{F} / 跳过：{S}
- 覆盖率：{C}%
- 执行时间：{T}

### 失败用例
| 用例 | 失败原因 | 严重程度 | 状态 |
|------|---------|---------|------|
| xxx | xxx | P0/P1/P2 | 待修复/已修复 |

### 风险评估
- {风险项及缓解措施}
```

## 参考来源

- [kao273183/qa-claude-skill](https://github.com/kao273183/qa-claude-skill) — 32 个生产级 QA 技能
- [petrkindlmann/qa-skills](https://github.com/petrkindlmann/qa-skills) — 50 个 QA 技能
- [obra/superpowers](https://github.com/obra/superpowers) — TDD 方法论（264K+ Stars）