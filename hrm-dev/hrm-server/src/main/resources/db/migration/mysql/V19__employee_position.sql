-- ----------------------------------------------------------------
-- Flyway 迁移：V19__employee_position.sql（员工档案 · 岗位属性，方案乙）
-- 权威设计：registration-design.md §7 / §11.9（U-07 用户裁定①，采纳方案乙）
--
-- 为什么这样设计：
--   1. 背景：position 原仅存 hr_flow.position（流程过程值），employee 表无 position 列；用户裁定①采纳
--      方案乙——岗位须成为员工档案属性，故新增 employee.position（原「方案甲（零结构变更）」已被推翻）。
--   2. 类型 VARCHAR(50) 与 hr_flow.position 完全一致（同命名、同长度，避免两套口径）。
--   3. NULL 允许、无默认：存量员工无岗位 → NULL 表示「未登记」；**历史不回填**（不以历史 flow.position
--      伪造档案事实）；如需回填另立 C 档脚本（registration-design.md T10 / E7）。
--   4. 权威口径：以 employee.position 为权威事实，hr_flow.position 为该次流程的过程值与留痕；
--      双写点唯一（assignForFlow 同方法同事务内双写，禁止他处单独写 employee.position）。
--   5. 不加索引（低基数、无按岗位过滤的既有查询）；不字典化（最小形态，TODO(扩展): 岗位字典 T9）。
--   6. 只加列、不删列、不改类型；列尾追加（INSTANT，无锁无重建）；employee 非大表，不触发联评。
-- ----------------------------------------------------------------

ALTER TABLE `employee`
  ADD COLUMN `position` VARCHAR(50) DEFAULT NULL COMMENT '岗位（员工档案属性，权威事实；自由文本，与 hr_flow.position 双写；存量未登记为 NULL；V19 方案乙）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   ALTER TABLE `employee` DROP COLUMN `position`;
-- 说明：回滚仅删本迁移新增列，不影响既有列；数据丢失范围 = 补列后写入的岗位信息。
-- ----------------------------------------------------------------
