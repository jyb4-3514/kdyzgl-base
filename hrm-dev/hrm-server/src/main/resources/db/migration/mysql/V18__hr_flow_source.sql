-- ----------------------------------------------------------------
-- Flyway 迁移：V18__hr_flow_source.sql（人事域 · 流程来源留痕）
-- 权威设计：registration-design.md §7 / §11.2（M-9）
--
-- 为什么这样设计：
--   1. M-9：自助注册提交的审批单来源为 SELF_REGISTER，与后台创建（ADMIN）须可区分留痕。
--      hr_flow 原无来源列，本迁移补 source 使「谁发起」可审计、可追溯。
--   2. NOT NULL DEFAULT 'ADMIN'：存量行（全部为后台创建）由 DEFAULT 一次性回填为 ADMIN，与业务事实一致；
--      无需额外 UPDATE 回填。MySQL 8 对「列尾追加且默认值为常量」的加列支持在线 DDL（INSTANT，无锁无重建）。
--   3. 不加索引：source 低基数（一期仅 ADMIN / SELF_REGISTER），无按来源过滤的既有查询。
--   4. 与 employee_registration.source 语义不同、并存：后者为注册渠道（STAFF_H5），本列为业务来源
--      （ADMIN / SELF_REGISTER）。
--   5. 只加列、不删列、不改类型（ADR-04）；hr_flow 非大表（行数量级为流程数），不触发联评。
-- ----------------------------------------------------------------

ALTER TABLE `hr_flow`
  ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '业务来源：ADMIN=后台创建，SELF_REGISTER=员工自助注册（M-9 来源留痕，V18 补列）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   ALTER TABLE `hr_flow` DROP COLUMN `source`;
-- 说明：回滚仅删本迁移新增列，不影响 V7/V14 既有列；数据丢失范围 = 补列后写入的来源标识。
-- ----------------------------------------------------------------
