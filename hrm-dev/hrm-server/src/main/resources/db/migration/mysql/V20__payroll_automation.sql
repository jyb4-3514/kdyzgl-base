-- ----------------------------------------------------------------
-- Flyway 迁移：V20__payroll_automation.sql（薪资结算自动化与全链路留痕）
-- 权威设计：payroll-automation-design.md §3（表设计 v1.1，claim 槽位语义）
--          §3.1 station_payroll_setting / §3.2 payroll_log / §3.3 payroll_run / §3.5 payroll 加列
-- 体例基准：V5（一驿一条配置）、V8（payroll 现状与列 COMMENT 枚举）、V9（leave_log 追加型留痕 / leave_setting 单行）
-- 批次：B1（数据层）。前置：B0 契约定稿（api.md / db.md 补录）与 P0.6 技术评审「通过 / 有条件通过」方可执行本脚本。
--
-- 为什么这样设计：
--   1. station_payroll_setting：一驿一条算薪配置（Q4），形态对齐 attendance_rule（V5）——
--      station_id NOT NULL + 普通索引，活跃唯一由 Service 查重保证（决策 D7），不建 DB 唯一索引。
--      enabled 默认 0（默认不自动跑数，安全）；notify_enabled 默认 1（生成即推管理员，Q8）。
--   2. payroll_log：追加型操作留痕（Q3/Q6/Q9 全链路追溯），形态对齐 leave_log（V9）——
--      只增不改，无 is_deleted / update_time；业务时间即 `time`；before/after 为低频读取的变更快照。
--      reason 为手工加扣款事由（Q3 必填，由 Service 校验，非 DB 约束）。
--   3. payroll_run：自动算薪运行记录与补跑幂等（Q7）。认领槽位硬唯一键 uk_payroll_run_claim
--      (station_id, claim_key) 是「重叠 tick / 多实例重复触发」的最后一道防线：
--      claim_key = target_month，RUNNING/SUCCESS/SKIPPED 均写值（占位），FAILED 置 NULL（释放，允许重试）；
--      MySQL 唯一索引允许多个 NULL → 同驿站同账期至多一条占位行，FAILED 可多行。
--      RUNNING 亦占位：第二个并发执行者 INSERT 即在唯一约束上被原子拒绝（DuplicateKey 1062），零重复算薪。
--   4. payroll 加列（§3.5）：paid_by_id / paid_by_name / paid_time 承载「已发放」终态的当前态展示
--      （事件详情入 payroll_log）；status 列宽 VARCHAR(20) 已足以容纳 OBJECTED(7) / PAID(4)，
--      本次仅更新列 COMMENT 的枚举说明，不改列宽 / 类型 / 默认值 / 空性。
--   5. 索引克制：station_payroll_setting 1 普通；payroll_log 2 普通；payroll_run 2 普通 + 1 唯一。
--      高频筛选字段（station_id / target_month / status / action / time）一律显式列 + 索引，不塞 JSON。
--   6. 不建物理外键（决策 D6）；create_time / update_time 仅 DEFAULT 兜底，由应用层 MetaObjectHandler 填充（D8）。
--
-- 【执行前置】迁移属 C 档（结构变更），须主智能体三步授权后由运维执行；执行前先备份库。
--   本脚本为静态产出（本机无 MySQL，未实跑验证），正确性收敛到服务器阶段（db.md §9.4 U-13）。
-- ----------------------------------------------------------------

-- 驿站级算薪配置（一驿一条，Q4/Q5）
CREATE TABLE `station_payroll_setting` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`     BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id，一驿一条，活跃唯一由 Service 查重）',
  `enabled`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用自动算薪：0=停用（默认，安全），1=启用',
  `payroll_day`    INT          NOT NULL DEFAULT 1 COMMENT '算薪日=每月第几天（1-31；月末缺日由调度钳位到当月最后一天；Service 校验）',
  `payroll_time`   VARCHAR(5)   NOT NULL DEFAULT '09:00' COMMENT '执行时间 HH:mm（Asia/Shanghai 墙钟）',
  `notify_enabled` TINYINT      NOT NULL DEFAULT 1 COMMENT '生成后是否推送管理员：0=不推，1=推（默认，Q8）',
  `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_station_payroll_setting_station` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站级算薪配置（一驿一条，活跃唯一由 Service 查重；V20）';

-- 工资单操作留痕（追加型审计表，Q3/Q6/Q9）
CREATE TABLE `payroll_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `payroll_id`    BIGINT       NOT NULL COMMENT '工资单（逻辑外键 payroll.id）',
  `action`        VARCHAR(32)  NOT NULL COMMENT '动作：GENERATE_AUTO/GENERATE_MANUAL/ITEM_ADD/ITEM_UPDATE/SUBMIT/APPROVE/REJECT/PUBLISH/REPUBLISH/CONFIRM/OBJECTION/PAY/NOTIFY/NOTIFY_SKIP',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id；SYSTEM 为空）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照',
  `operator_role` VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色快照',
  `operator_type` VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '操作主体：USER=人工（默认），SYSTEM=自动调度',
  `time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（只插不改，无 update_time）',
  `from_status`   VARCHAR(20)  DEFAULT NULL COMMENT '变更前状态',
  `to_status`     VARCHAR(20)  DEFAULT NULL COMMENT '变更后状态',
  `reason`        VARCHAR(200) DEFAULT NULL COMMENT '事由：手工加扣款必填（Q3）/ 异议原因 / 驳回意见 / 再发布处理说明（Service 校验，非 DB 约束）',
  `before`        JSON         DEFAULT NULL COMMENT '变更前快照（金额 / 合计等，低频读取、结构多变）',
  `after`         JSON         DEFAULT NULL COMMENT '变更后快照（金额 / 合计等，低频读取、结构多变）',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注 / 排障说明',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_log_payroll` (`payroll_id`, `time`),
  KEY `idx_payroll_log_action_time` (`action`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资单操作留痕（追加型，只增不改；V20）';

-- 自动算薪运行记录与补跑幂等（Q7）
CREATE TABLE `payroll_run` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`      BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `target_month`    CHAR(7)      NOT NULL COMMENT '目标账期 yyyy-MM',
  `trigger_type`    VARCHAR(16)  NOT NULL COMMENT '触发方式：AUTO=定时到点，CATCH_UP=补跑，MANUAL=手工触发',
  `due_at`          DATETIME     NOT NULL COMMENT '本次应执行时刻（Asia/Shanghai 墙钟，判定「错过」的基准）',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '结果：RUNNING/SUCCESS/FAILED/SKIPPED',
  `skip_code`       VARCHAR(24)  DEFAULT NULL COMMENT '跳过码（机器可读）：BLOCKED_9405/CONFIG_INVALID/EXHAUSTED/DRAFT_PROTECTED；配合 skip_reason，供指标统计与「是否重试」判定',
  `skip_reason`     VARCHAR(200) DEFAULT NULL COMMENT '跳过原因（人类可读，如「该账期已生成 9405（单号 …）」）',
  `generated_count` INT          DEFAULT NULL COMMENT '生成单据数',
  `fail_reason`     VARCHAR(500) DEFAULT NULL COMMENT '失败原因（截断，不落敏感信息）',
  `claim_key`       CHAR(7)      DEFAULT NULL COMMENT '认领槽位 = target_month：RUNNING/SUCCESS/SKIPPED 写值（占位）；FAILED 置 NULL（释放，允许重试）（配合 uk_payroll_run_claim）',
  `operator_id`     BIGINT       DEFAULT NULL COMMENT '手工触发人（MANUAL 时，逻辑外键 employee.id）',
  `start_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finish_time`     DATETIME     DEFAULT NULL COMMENT '结束时间',
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是（只增不删、无删除入口，业务永不置位，Q-DB-9）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payroll_run_claim` (`station_id`, `claim_key`),
  KEY `idx_payroll_run_station_month` (`station_id`, `target_month`),
  KEY `idx_payroll_run_status_time` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动算薪运行记录（认领槽位幂等，占位行 RUNNING/SUCCESS/SKIPPED 每驿站每账期至多一行；V20）';

-- payroll 加列（「已发放」终态当前态展示，§3.5）+ status 列 COMMENT 枚举更新（8 态）
ALTER TABLE `payroll`
  ADD COLUMN `paid_by_id`   BIGINT      DEFAULT NULL COMMENT '确认发放人（逻辑外键 employee.id；已发放终态）',
  ADD COLUMN `paid_by_name` VARCHAR(50) DEFAULT NULL COMMENT '确认发放人姓名快照',
  ADD COLUMN `paid_time`    DATETIME    DEFAULT NULL COMMENT '确认发放时间',
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED/OBJECTED/PAID';

-- 说明：
--   1. payroll 加列均为列尾追加（不写 FIRST/AFTER），三列可空、无默认（NULL 表示「未发放」）。
--   2. status 仅更新 COMMENT 枚举说明（新增 OBJECTED / PAID 两态；枚举顺序按 PayrollStatus 末尾追加，
--      见 payroll-automation-design.md §2.7），类型 / 长度 / 默认值 / 空性均不变，不产生数据转换。
--   3. payroll 非大表（一员工一账期一行，量级为员工数 × 账期数），不触发「大表变更须数据库 + 算法联评」
--      （该约束针对 V13 parcel 20 万级大表）。
--   4. 不加索引：paid_* 仅当前态展示，无按发放人 / 发放时间过滤的既有查询。
-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   ALTER TABLE `payroll`
--     DROP COLUMN `paid_time`,
--     DROP COLUMN `paid_by_name`,
--     DROP COLUMN `paid_by_id`,
--     MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED';
--   DROP TABLE IF EXISTS `payroll_run`;
--   DROP TABLE IF EXISTS `payroll_log`;
--   DROP TABLE IF EXISTS `station_payroll_setting`;
-- 说明：先撤销 payroll 加列 / COMMENT，再 DROP 三张新表（逆依赖顺序）。
--   status 的 COMMENT 回滚会把说明还原为 6 态；回滚前须确认无 OBJECTED / PAID 存量数据，否则状态语义失真。
-- ----------------------------------------------------------------
