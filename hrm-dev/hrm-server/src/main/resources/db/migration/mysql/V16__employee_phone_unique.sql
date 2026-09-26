-- ----------------------------------------------------------------
-- Flyway 迁移：V16__employee_phone_unique.sql（既有缺陷修复 · 员工手机号活跃唯一）
-- 权威设计：registration-design.md §2.4 / §7 / §11.6（REG-04 / M-5，v1.3 复评「通过」）
-- 依据：db.md §3.3 employee（活跃唯一口径）、§10.2（D7 唯一性策略的显式例外登记）
--
-- 为什么这样设计：
--   1. 缺陷背景：createEmployeeForFlow 仅校验 username、不校验 phone（既有 Java 事实），
--      employee.phone 无 DB 唯一索引（决策 D7 由 Service 层「活跃查重」保证）。注册为公开端点、并发高于后台，
--      且 phone 为登录标识，重复号将使按 phone selectOne 命中多行抛异常 → 目标账号登录/短信登录 DoS。
--   2. 形态（U-05 定稿，仅 mysql）：生成列 phone_active = IF(is_deleted=0, phone, NULL) STORED +
--      UNIQUE INDEX uk_employee_phone_active(phone_active)。NULL 可重复 → 已删号可复用，
--      「仅活跃行唯一」由生成列表达，无需 PG 式部分索引（postgresql/ 自 V2 冻结，本期不产出 pg 脚本）。
--   3. 这是决策 D7「不建 DB 唯一索引」的显式例外（理由：公开端点并发 + phone 为登录标识，
--      重复号后果为登录 DoS 而非仅脏数据），例外登记见 db.md §10.2。
--   4. 硬性断言：本 ALTER 语句自身即「存量重复号」的最终防线——若活跃行存在重复 phone，
--      唯一索引创建将以 1062（Duplicate entry）报错、迁移失败。MySQL 8 单条 DDL 原子，
--      失败不残留半成品（生成列与唯一索引一并回退）。
--
-- 【执行前置】迁移属 C 档（结构变更），须主智能体三步授权后由运维执行；执行前先备份库，并先跑下方预检。
--   预检 SQL（活跃员工重复手机号，必须返回 0 行；必须含 AND phone IS NOT NULL，
--   否则 is_deleted=0 且 phone IS NULL 的多行会被 GROUP BY 归为一组误报）：
--     SELECT phone,
--            COUNT(*)                     AS cnt,
--            GROUP_CONCAT(id ORDER BY id) AS ids,
--            GROUP_CONCAT(real_name)       AS names
--     FROM employee
--     WHERE is_deleted = 0
--       AND phone IS NOT NULL
--     GROUP BY phone
--     HAVING COUNT(*) > 1;
--   人工去重指引：预检非 0 行 → 先人工去重（保留哪条属数据变更，须 C 档授权 + 人工确认，
--   registration-design.md §11.6 / 安全 R-5），去重后再执行本迁移；禁止在未去重情况下强跑（必然 1062 失败）。
-- ----------------------------------------------------------------

ALTER TABLE `employee`
  ADD COLUMN `phone_active` VARCHAR(20)
      GENERATED ALWAYS AS (IF(`is_deleted` = 0, `phone`, NULL)) STORED
      COMMENT '活跃手机号生成列：is_deleted=0 取 phone，否则 NULL；用于「仅活跃行」唯一约束（V16）',
  ADD UNIQUE KEY `uk_employee_phone_active` (`phone_active`);

-- 说明：
--   1. 列尾追加（不写 FIRST/AFTER）；STORED 生成列须重建表（ALGORITHM=COPY/INPLACE），
--      employee < 5000 行，非大表，不触发「大表变更须数据库+算法联评」，锁表窗口可忽略。
--   2. 不删既有普通索引 idx_employee_phone（仍服务 phone 查重 / 关键字筛选路径）。
--   3. 不改任何既有列、不对既有列做数据回填（仅新增生成列与唯一索引）。
-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   ALTER TABLE `employee` DROP INDEX `uk_employee_phone_active`;
--   ALTER TABLE `employee` DROP COLUMN `phone_active`;
-- 说明：回滚仅撤销本次新增的唯一索引与生成列，不改数据（生成列非持久业务数据）；
--   若已发生合法数据依赖，回滚前须评估。回滚顺序必须先删索引、再删列。
-- ----------------------------------------------------------------
