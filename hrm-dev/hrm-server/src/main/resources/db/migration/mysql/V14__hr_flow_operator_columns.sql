-- ----------------------------------------------------------------
-- Flyway 迁移：V14__hr_flow_operator_columns.sql（人事域 · 表结构缺口修复）
-- 权威设计：db.md §8.5.4（hr_flow）；契约字段来源 hrm-demo/src/shared/mock/hrStore.js
--           与 HrFlowVO.operatorId / operatorName（toFlowVO 出参）
--
-- 为什么这样设计：
--   1. V7__hr.sql 建 hr_flow 时漏建「创建人」两列，导致 HrFlow 实体只能把
--      operatorId / operatorName 标注 @TableField(exist = false)，创建流程当次请求在内存回填出参，
--      列表 / 详情读取路径取不到值（见 docs/update-log.md「服务端编译修复」条目）。
--      本迁移补齐列，使创建人可持久化；补列后须由后端工程师移除 HrFlow 两字段的 exist = false
--      （联动项，非本迁移职责）。
--   2. 语义与 hr_flow_step.operator_id / operator_name、hr_salary_log.operator_id / operator_name 一致：
--      operator_id = 逻辑外键 employee.id（D6，不建物理外键）；operator_name = 姓名快照（防改名漂移）。
--   3. 只加列、不删列、不改类型（架构 §4.6 ADR-04）；两列均可空、无默认值，
--      以列尾追加方式落（不写 FIRST/AFTER，MySQL 默认选最优算法；8.0.12+ 尾部加列走 ALGORITHM=INSTANT，
--      不锁表、不重建）。不指定列位置亦避免依赖 8.0.29+ 才支持的「中间位置 INSTANT 加列」，
--      且列序与 HrFlow 实体字段声明序（operatorId/operatorName 在末尾）一致。
--   4. 该表非大表（hr_flow 行数量级为流程数），不触发「大表变更须数据库+算法联评」。
-- ----------------------------------------------------------------

ALTER TABLE `hr_flow`
  ADD COLUMN `operator_id`   BIGINT      DEFAULT NULL COMMENT '创建人（逻辑外键 employee.id）',
  ADD COLUMN `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名快照';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   ALTER TABLE `hr_flow` DROP COLUMN `operator_name`;
--   ALTER TABLE `hr_flow` DROP COLUMN `operator_id`;
-- 说明：回滚仅删本迁移新增的两列，不触碰 V7 既有列；数据丢失范围 = 补列后写入的创建人信息。
-- ----------------------------------------------------------------
