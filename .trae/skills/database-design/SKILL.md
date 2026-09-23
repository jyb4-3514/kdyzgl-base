---
name: "database-design"
description: "数据库设计与迁移技能。覆盖建表规范、索引优化、Flyway 迁移、SQL 审查、MySQL/PostgreSQL 双 schema 维护。源自 docfork/db-skills + sanjay3290/ai-skills。触发：建表/SQL/索引/迁移/Flyway/数据库设计/DDL/查询优化/schema。"
---

# 数据库设计与迁移

> 综合 [docfork/db-skills](https://github.com/docfork/db-skills)（数据库技能集） +
> [sanjay3290/ai-skills](https://github.com/sanjay3290/ai-skills)（MySQL/PostgreSQL 技能） +
> 项目特有 Flyway 双 schema 规范。

## 命名规范

| 对象 | 规范 | 示例 |
|------|------|------|
| 表名 | snake_case，复数或单数统一 | `employees` / `parcels` |
| 字段名 | snake_case | `employee_name`, `create_time` |
| 主键 | `id`，BIGINT 自增 | `id BIGINT PRIMARY KEY AUTO_INCREMENT` |
| 时间 | `create_time` / `update_time` | DATETIME(3) |
| 索引 | `idx_表名_字段名` | `idx_employees_status` |
| 唯一索引 | `uk_表名_字段名` | `uk_employees_phone` |
| 外键 | `fk_表名_关联表` | `fk_parcels_station_id` |

## 建表模板

```sql
CREATE TABLE employees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    employee_name VARCHAR(50) NOT NULL COMMENT '员工姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-在职 2-离职',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_employees_phone (phone),
    KEY idx_employees_status (status),
    KEY idx_employees_name (employee_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';
```

## 字段类型规范

| 场景 | MySQL | PostgreSQL |
|------|-------|-----------|
| 主键 | BIGINT AUTO_INCREMENT | BIGSERIAL |
| 布尔 | TINYINT(1) | BOOLEAN |
| 金额 | DECIMAL(18,2) | DECIMAL(18,2) |
| 长文本 | TEXT | TEXT |
| JSON | JSON | JSONB |
| 枚举 | TINYINT + 注释 | SMALLINT + 注释 |
| 时间 | DATETIME(3) | TIMESTAMP(3) |

## 索引设计原则

1. **选择性高的列优先**：区分度 > 10% 才建索引
2. **最左前缀**：联合索引 `(a, b, c)` 可用于 `a`、`a,b`、`a,b,c` 查询
3. **覆盖索引**：查询字段都在索引中，避免回表
4. **避免过多索引**：每表索引 ≤ 5 个，写入性能影响
5. **避免索引失效**：函数/计算/类型转换/前置模糊查询会使索引失效

## Flyway 迁移规范

### 命名格式
```
V{版本号}__{描述}.sql
示例：V1__init_schema.sql
     V2__add_employee_status.sql
     V3__create_parcel_table.sql
```

### 脚本要求
- 每个版本一个文件，不可逆修改
- 只做 DDL（建表/加字段/加索引），不放业务数据
- 脚本存放：`hrm-dev/hrm-server/src/main/resources/db/migration/`
- 建表脚本存放：`hrm-dev/sql/schema/{mysql,postgresql}/`

### 变更前检查
- [ ] 大表加字段：是否允许锁表？是否需要在线 DDL？
- [ ] 加索引：是否在低峰期执行？
- [ ] 删字段：确认无代码依赖
- [ ] 改字段类型：是否有数据转换风险？

## SQL 审查清单

- [ ] 禁止 `SELECT *`，明确列出字段
- [ ] WHERE 条件字段有索引覆盖
- [ ] 分页查询使用 `LIMIT offset, size` 或游标分页
- [ ] JOIN 字段类型一致，有索引
- [ ] 子查询评估能否改写为 JOIN
- [ ] 批量操作使用批量 INSERT/UPDATE 而非逐条
- [ ] 参数化查询，禁止拼接 SQL

## 参考来源

- [docfork/db-skills](https://github.com/docfork/db-skills) — 数据库技能集
- [sanjay3290/ai-skills](https://github.com/sanjay3290/ai-skills) — MySQL/PostgreSQL 技能