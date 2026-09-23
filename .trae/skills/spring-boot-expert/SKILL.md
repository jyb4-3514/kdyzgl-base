---
name: "spring-boot-expert"
description: "Spring Boot 后端开发专家技能。覆盖依赖注入、REST API、分层架构、异常处理、安全、性能优化。源自 rrezartprebreza/spring-boot-skills + zander-zyx/java-development-skill。触发：Spring Boot/Java后端/接口开发/Controller/Service/Mapper/MyBatis/JPA/安全/事务。"
---

# Spring Boot 后端开发专家

> 综合 [rrezartprebreza/spring-boot-skills](https://github.com/rrezartprebreza/spring-boot-skills)（生产级 Spring Boot 技能） +
> [zander-zyx/java-development-skill](https://github.com/zander-zyx/java-development-skill)（Java 开发全栈技能） +
> [Ashfaqbs/software-dev-ai-claude-toolkit](https://github.com/Ashfaqbs/software-dev-ai-claude-toolkit)（后端全栈工具包）。

## 项目分层规范

```
controller/  → 接收请求、参数校验、调用 service、返回统一响应
service/     → 业务逻辑、事务管理、调用 mapper
mapper/      → 数据访问 (MyBatis/JPA)
entity/      → 数据库实体映射
dto/         → 数据传输对象 (入参)
vo/          → 视图对象 (出参)
config/      → 配置类
util/        → 工具类
```

## 强制规范

### 依赖注入
- ✅ 构造器注入 + `@RequiredArgsConstructor`（Lombok）
- ❌ 禁止 `@Autowired` 字段注入
- ❌ 禁止 `@Autowired` setter 注入

### REST API 设计
- URL：复数名词，RESTful 语义（`/api/employees` 而非 `/api/getEmployee`）
- 统一响应：`{ code, message, data }` 封装
- 分页：`pageNum` / `pageSize` 参数，返回 `{ total, list, pageNum, pageSize }`
- HTTP 语义：GET(查) POST(增) PUT(改) DELETE(删)

### 异常处理
- 统一 `@RestControllerAdvice` 全局异常处理
- 业务异常继承 `RuntimeException`，带错误码
- 不吞异常、不裸抛、敏感信息不外泄
- 日志：`error` 记堆栈、`warn` 记可恢复、`info` 记关键流转

### 安全
- 所有写操作：鉴权 + 越权校验 + 入参校验
- SQL 一律参数化，禁止字符串拼接
- 敏感字段脱敏返回（密码、身份证号、手机号）
- JWT token 过期时间合理配置

### 事务
- Service 层 `@Transactional(rollbackFor = Exception.class)`
- 只读操作加 `@Transactional(readOnly = true)`
- 避免大事务、避免事务内调用外部 API

## 代码模板

### Controller 模板
```java
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {
    private final EmployeeService employeeService;

    @GetMapping("/{id}")
    public Result<EmployeeVO> getById(@PathVariable Long id) {
        return Result.success(employeeService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody EmployeeDTO dto) {
        employeeService.create(dto);
        return Result.success();
    }
}
```

### Service 模板
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeMapper employeeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(EmployeeDTO dto) {
        // 业务校验
        // 数据转换
        // 持久化
        // 日志
    }
}
```

## 参考来源

- [rrezartprebreza/spring-boot-skills](https://github.com/rrezartprebreza/spring-boot-skills) — 生产级 Spring Boot 技能集
- [zander-zyx/java-development-skill](https://github.com/zander-zyx/java-development-skill) — Java 开发全栈技能
- [Ashfaqbs/software-dev-ai-claude-toolkit](https://github.com/Ashfaqbs/software-dev-ai-claude-toolkit) — 后端全栈工具包