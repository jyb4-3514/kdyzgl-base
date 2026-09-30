-- ----------------------------------------------------------------
-- Flyway 迁移：V21__payroll_log_locator.sql（薪资结算自动化 v1.2/v1.3/v1.4 数据层增量）
-- 权威设计：payroll-automation-design.md v1.4 §3.2（payroll_log 冗余定位列，M-2②/M-4）、
--          §3.3（payroll_run.attempt_date + uk_attempt 日粒度硬防线 + 索引精简，U-06/v1.4 裁定）、§3.6（新表 station_payroll_setting_log，M-9）
-- 算法依据：algorithm-payroll-scheduling.md v1.2 §1.4/§1.4.1（日粒度重试）、§1.5（uk_attempt + uk_claim 双唯一键）、§12.2
-- 体例基准：V20（薪资结算自动化前序同批）、V9（追加型留痕 leave_log）、V16（收紧唯一约束的手法）
-- 批次：B1（数据层）。前置：B0 契约定稿（api.md / db.md 补录）与 P0.6 技术评审「通过 / 有条件通过」方可执行。
--
-- 脚本名说明：沿用方案 §6② 指定的 `V21__payroll_log_locator.sql`（hold over）。范围已扩为四段，
--   名称未随范围改写，以保持方案 / db.md / 评审交叉引用一致；四段同批、版本号统一为 V21：
--   ① payroll_log 冗余定位列 ② payroll_run.attempt_date + uk_attempt + 索引精简 ③ 新表 station_payroll_setting_log
--   ④ notification.type 列 COMMENT 同步（方案 §4.5 通知类型 7/8/9；仅更新注释、不改列型；主代理裁定并入本批，不另开版本）。
--
-- 为什么这样设计：
--   1. payroll_log 增 employee_id / month 冗余定位列（M-2②/M-4）：generate 覆盖重建会物理删除 DRAFT/REJECTED 单，
--      原 payroll_id 可能指向已删单（孤儿）；冗余「员工 + 账期」使留痕可脱离 payroll_id 独立按「员工 + 账期」检索。
--      两列可空（历史事件可能无员工归属/账期），由写入侧填充；本表由 V20 新建、尚无存量，「加可空列」无需回填。
--      同时更新 payroll_id 列 COMMENT 标注孤儿风险（设计 §3.2；仅注释，类型 / 空性不变）。
--   2. payroll_run 增 attempt_date（U-06 日粒度重试）：承载「同一驿站同一账期每自然日至多一次尝试」闸门与连续失败天数统计。
--      非空落地采用三段式（先可空 → 收紧 NOT NULL → 撤默认），保证「表非空时不静默失败」，
--      且最终满足设计 §6② B1「NOT NULL 且无默认」——应用层必须显式写入 attempt_date。
--   3. 新增唯一键 uk_attempt (station_id, target_month, attempt_date)：与既有 uk_payroll_run_claim 正交共存——
--      前者管「日内一次」（无论 RUNNING/SUCCESS/FAILED/SKIPPED，同日第 2 条 INSERT 撞 1062 被原子拒绝）；
--      后者管「跨日终态占位」（claim_key=target_month 占位 / FAILED 置 NULL 释放）。二者共同构成 Layer 0 硬防线。
--      主代理 v1.4 裁定：`uk_attempt` 为「每自然日至多一次」的**DB 硬防线**（跨实例兜底），应用层短路降级为查询优化。
--   4. 索引精简（最小冗余结论，主代理 v1.4 §13.5 已裁定）：既有普通索引 idx_payroll_run_station_month (station_id, target_month)
--      是 uk_attempt 的最左前缀，被完全覆盖（同列同序、仅少末位列），保留即为冗余（增写成本、查询零增益）→ DROP 之。
--      原拟「扩为 (station_id, target_month, attempt_date)」的普通索引与 uk_attempt 键完全相同，会使全表出现一对
--      「同列同序、唯一/普通各一」的重复索引，故不另补——uk_attempt 即该键的唯一索引。
--      「本驿站本账期运行历史 / 日粒度判定 / 连续失败天数统计」一律走 uk_attempt 的最左前缀。
--   5. skip_code 去掉 EXHAUSTED（U-06：无硬上限，不存在「重试耗尽即放弃」，连续失败改为告警）：仅更新列 COMMENT，
--      类型 / 长度 / 空性不变，无数据转换。
--   6. station_payroll_setting_log：算薪配置变更审计（M-9），追加型（无 is_deleted / update_time），
--      形态对齐 leave_log（V9）；station_id NOT NULL（与 payroll_log.payroll_id 非空定位等价），索引 (station_id, time)。
--   7. 不加索引原则：高频筛选字段（employee_id / month / station_id / time / status）一律显式列 + 索引，不塞 JSON；
--      不建物理外键（决策 D6）；时间列仅 DEFAULT CURRENT_TIMESTAMP，update_time 由应用层维护（D8）。
--   8. notification.type 列 COMMENT 同步（方案 §4.5）：新增薪资域系统联动类型 7/8/9，仅更新列 COMMENT，
--      列型 / 长度 / 空性 / 默认值均不变（仍 TINYINT NOT NULL、无默认），无数据类型转换、无 DML；
--      `sendSystem` 白名单拆分（SYSTEM_TYPES{7,8,9} 独立、公告白名单维持 1..6）属 B4 代码改动，不在本迁移内。
--
-- 【执行前置】迁移属 C 档（结构变更），须主智能体三步授权后由运维执行；执行前先备份库。
-- 【前置校验】payroll_run / payroll_log 由 V20 同批新建，预期无存量。若执行时 payroll_run 非空：
--   ① 加 attempt_date 的三段式会以执行当日填充存量行，不会静默失败；
--   ② 但 ADD UNIQUE KEY uk_attempt 在「同 (station_id, target_month) 存量多行」时会因同日冲突报错（1062）——
--      此时须停手，按设计 §6② B1 由数据库工程师按 C 档先回填可区分的 attempt_date / 清重复行，再重跑本段。
-- 【版本依赖】段 2b 使用表达式默认 `DEFAULT (CURRENT_DATE)`，需 MySQL 8.0.13+（本项目 MySQL 8，utf8mb4_0900_ai_ci）。
--   本脚本为静态产出（本机无 MySQL，未实跑验证），正确性收敛到服务器阶段（db.md §9.4 U-14）。
-- ----------------------------------------------------------------

-- 段 1 · payroll_log 冗余定位列（M-2②/M-4：覆盖重建后留痕不成孤儿）
-- 1a 更新 payroll_id 列 COMMENT（标注孤儿风险，与冗余定位列配对；类型 / 空性不变）
ALTER TABLE `payroll_log`
  MODIFY COLUMN `payroll_id` BIGINT NOT NULL COMMENT '工资单（逻辑外键 payroll.id）；generate 覆盖重建会物理删除 DRAFT/REJECTED 单，本列可能指向已删单（孤儿），故须配合下方 employee_id / month 冗余定位列检索（V21 更新注释）';

-- 1b 补冗余定位列（可空；本表由 V20 新建、尚无存量，无需回填）
ALTER TABLE `payroll_log`
  ADD COLUMN `employee_id` BIGINT  DEFAULT NULL COMMENT '冗余定位列：留痕所属员工（逻辑外键 employee.id）；使留痕可脱离已删 payroll_id 按「员工 + 账期」独立检索（V21 补列）' AFTER `payroll_id`,
  ADD COLUMN `month`       CHAR(7) DEFAULT NULL COMMENT '冗余定位列：账期 yyyy-MM（V21 补列）' AFTER `employee_id`;

ALTER TABLE `payroll_log`
  ADD KEY `idx_payroll_log_emp_month` (`employee_id`, `month`, `time`);

-- 段 2 · payroll_run.attempt_date（U-06 日粒度闸门）+ 唯一键 + 索引精简 + skip_code / 表注释
-- 2a 先加可空列（空表 / 存量均安全）
ALTER TABLE `payroll_run`
  ADD COLUMN `attempt_date` DATE DEFAULT NULL COMMENT '本次尝试的自然日（Asia/Shanghai 墙钟；写 now.toLocalDate()）——「同一驿站同一账期每自然日至多尝试一次」的闸门依据与连续失败天数统计口径（V21 补列，U-06）' AFTER `target_month`;

-- 2b 收紧为 NOT NULL；对（预期不存在，但为防御）存量 NULL 行以执行当日填充，规避严格模式零日期报错
ALTER TABLE `payroll_run`
  MODIFY COLUMN `attempt_date` DATE NOT NULL DEFAULT (CURRENT_DATE) COMMENT '本次尝试的自然日（Asia/Shanghai 墙钟；写 now.toLocalDate()）——「同一驿站同一账期每自然日至多尝试一次」的闸门依据与连续失败天数统计口径（V21 补列，U-06）';

-- 2c 撤销临时默认，恢复设计「NOT NULL 且无默认」（应用层必须显式写入 attempt_date）
ALTER TABLE `payroll_run`
  ALTER COLUMN `attempt_date` DROP DEFAULT;

-- 2d 日粒度硬防线 + 索引精简（DROP 冗余普通索引）+ skip_code / 表注释更新
ALTER TABLE `payroll_run`
  ADD UNIQUE KEY `uk_attempt` (`station_id`, `target_month`, `attempt_date`),
  DROP INDEX `idx_payroll_run_station_month`,
  MODIFY COLUMN `skip_code` VARCHAR(24) DEFAULT NULL COMMENT '跳过码（机器可读）：BLOCKED_9405/CONFIG_INVALID/DRAFT_PROTECTED；配合 skip_reason，供指标统计与「是否重试」判定（V21 移除 EXHAUSTED：无硬上限，连续失败改为告警）',
  COMMENT = '自动算薪运行记录（认领槽位幂等 + 日粒度闸门：uk_payroll_run_claim 管跨日终态占位 [RUNNING/SUCCESS/SKIPPED 每驿站每账期至多一行]、uk_attempt 管日内一次 [同一驿站同一账期每自然日至多一条]；V20/V21）';

-- 段 3 · 新表 station_payroll_setting_log（M-9：算薪配置变更审计，追加型）
-- I-3 每次保存必写一条；启用 0→1 由 action=ENABLE 行承载、可追溯（M-9 硬要求）。
CREATE TABLE `station_payroll_setting_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`    BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id；非空，与 payroll_log.payroll_id 等价定位）',
  `action`        VARCHAR(16)  NOT NULL COMMENT '动作：CREATE=首次创建 / UPDATE=字段变更 / ENABLE=启用(0→1) / DISABLE=停用(1→0)（每次保存必写一条）',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照',
  `operator_role` VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色快照',
  `before`        JSON         DEFAULT NULL COMMENT '变更前快照（白名单键：enabled/payrollDay/payrollTime/notifyEnabled/remark；CREATE 时为 NULL）',
  `after`         JSON         DEFAULT NULL COMMENT '变更后快照（同白名单键，不含凭据 / 个人信息）',
  `time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（只插不改，无 update_time）',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_station_payroll_setting_log_station_time` (`station_id`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站算薪配置变更审计（追加型，只增不改；每次保存必写一条；V21）';

-- 段 4 · notification.type 列 COMMENT 同步（方案 §4.5：通知类型 7/8/9；仅注释、不改列型）
-- 4a 仅更新列 COMMENT 以说明新增取值；type 仍为 TINYINT NOT NULL、无默认，列型 / 长度 / 空性 / 默认值均不变，无数据转换。
--    7/8/9 为薪资域系统联动类型：7=工资单待审核（→管理员）、8=工资单已发布（→员工本人）、9=工资单异议退回（→管理员）。
ALTER TABLE `notification`
  MODIFY COLUMN `type` TINYINT NOT NULL COMMENT '类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果 7=工资单待审核（→管理员） 8=工资单已发布（→员工本人） 9=工资单异议退回（→管理员）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `station_payroll_setting_log`;
--   ALTER TABLE `notification`
--     MODIFY COLUMN `type` TINYINT NOT NULL COMMENT '类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果';
--   ALTER TABLE `payroll_run`
--     ADD INDEX `idx_payroll_run_station_month` (`station_id`, `target_month`),
--     DROP INDEX `uk_attempt`,
--     MODIFY COLUMN `skip_code` VARCHAR(24) DEFAULT NULL COMMENT '跳过码（机器可读）：BLOCKED_9405/CONFIG_INVALID/EXHAUSTED/DRAFT_PROTECTED；配合 skip_reason，供指标统计与「是否重试」判定',
--     COMMENT = '自动算薪运行记录（认领槽位幂等，占位行 RUNNING/SUCCESS/SKIPPED 每驿站每账期至多一行；V20）',
--     DROP COLUMN `attempt_date`;
--   ALTER TABLE `payroll_log`
--     MODIFY COLUMN `payroll_id` BIGINT NOT NULL COMMENT '工资单（逻辑外键 payroll.id）',
--     DROP INDEX `idx_payroll_log_emp_month`,
--     DROP COLUMN `month`,
--     DROP COLUMN `employee_id`;
-- 说明：先 DROP 新表，再逆序撤销 notification（还原 type 列 COMMENT，对齐 V4 原文）、payroll_run（先还原索引 / 注释，再删列）、payroll_log（先还原 payroll_id 注释，再删索引 / 列，对齐 V16 手法）。
--   attempt_date 回滚会一并丢失「日粒度」数据；若已产生自动算薪运行记录，回滚前须确认不再依赖该闸门。
-- ----------------------------------------------------------------
