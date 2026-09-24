-- ----------------------------------------------------------------
-- Flyway 迁移：V9__leave.sql（批次 P7 · 请假）
-- 权威设计：docs/db.md §7.1/§7.2（字段与语义已定义，本脚本落 DDL，不新增语义）
--          server-architecture.md §4.1/§4.4（P7 → V9）、§6.2 P7
-- 字段真源：hrm-demo/src/shared/mock/leaveStore.js + constants/dict.js
--
-- 为什么这样设计：
--   1. 两级审批分槽存放：station_approver_*（初审）与 approver_*（终审）分列，
--      避免终审覆盖初审，让「谁初审的」永久可查（db.md §7.1 明确要求）。
--   2. counted_days_snapshot（JSON）在终审通过时落快照（naturalDays/countedDays/scheduleDigest），
--      使排班事后变更不影响已出账口径（算法 S5 可解释性）。
--   3. 6 态状态机 + reject_stage 区分两级驳回；origin_id 指向驳回后重提的原单。
--   4. 区间相交判定走 idx_leave_request_date (start_date, end_date)；列表/待办走
--      employee/station + status 索引。
--   5. leave_log 为追加型操作留痕（含 NOTIFY_SKIP 排障留痕），只增不改：无 is_deleted / update_time，
--      业务时间即 time；before/after 为变更前后快照（低频读取、结构多变）。
--   6. leave_setting 为单行全局开关（leaveDeductEnabled，默认 false），无删除语义，不设 is_deleted。
-- ----------------------------------------------------------------

-- 请假申请单
CREATE TABLE `leave_request` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`            BIGINT       NOT NULL COMMENT '申请人（逻辑外键 employee.id）',
  `station_id`             BIGINT       NOT NULL COMMENT '申请时归属驿站（初审站长判定依据）',
  `leave_type`             VARCHAR(20)  NOT NULL COMMENT '假别：ANNUAL/PERSONAL/SICK/COMPENSATORY/MARRIAGE/MATERNITY/PATERNITY/BEREAVEMENT/OTHER',
  `start_date`             DATE         NOT NULL COMMENT '开始日期',
  `end_date`               DATE         NOT NULL COMMENT '结束日期',
  `start_period`           VARCHAR(2)   NOT NULL COMMENT '开始半天粒度：AM/PM',
  `end_period`             VARCHAR(2)   NOT NULL COMMENT '结束半天粒度：AM/PM',
  `reason`                 VARCHAR(200) NOT NULL COMMENT '请假事由（2-200 字）',
  `natural_days`           DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '自然天数（半天粒度 0.5）',
  `counted_days`           DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '申请时预估计薪天数（逐日查排班）',
  `counted_days_snapshot`  JSON         DEFAULT NULL COMMENT '终审通过时计薪天数快照 {naturalDays,countedDays,scheduleDigest}',
  `status`                 VARCHAR(20)  NOT NULL COMMENT '状态：PENDING_STATION/PENDING_BOSS/APPROVED/REJECTED/CANCELLED/REVOKED',
  `reject_stage`           VARCHAR(10)  DEFAULT NULL COMMENT '驳回阶段：STATION/BOSS（仅 REJECTED 有值）',
  `origin_id`              BIGINT       DEFAULT NULL COMMENT '驳回后重提指向的原单（逻辑外键 leave_request.id）',
  `station_approver_id`    BIGINT       DEFAULT NULL COMMENT '初审人（逻辑外键 employee.id）',
  `station_approve_time`   DATETIME     DEFAULT NULL COMMENT '初审时间',
  `station_approve_remark` VARCHAR(200) DEFAULT NULL COMMENT '初审意见',
  `approver_id`            BIGINT       DEFAULT NULL COMMENT '终审人（逻辑外键 employee.id）',
  `approve_time`           DATETIME     DEFAULT NULL COMMENT '终审时间',
  `approve_remark`         VARCHAR(200) DEFAULT NULL COMMENT '终审意见',
  `cancel_by_id`           BIGINT       DEFAULT NULL COMMENT '撤销人（申请人本人）',
  `cancel_time`            DATETIME     DEFAULT NULL COMMENT '撤销时间',
  `revoker_id`             BIGINT       DEFAULT NULL COMMENT '撤回人（审批人）',
  `revoke_time`            DATETIME     DEFAULT NULL COMMENT '撤回时间',
  `revoke_reason`          VARCHAR(200) DEFAULT NULL COMMENT '撤回原因',
  `apply_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_leave_request_employee_status` (`employee_id`, `status`),
  KEY `idx_leave_request_station_status` (`station_id`, `status`),
  KEY `idx_leave_request_date` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请假申请单（两级审批，分槽留痕）';

-- 请假操作留痕（追加型审计表）
CREATE TABLE `leave_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leave_id`      BIGINT       NOT NULL COMMENT '请假单（逻辑外键 leave_request.id）',
  `action`        VARCHAR(24)  NOT NULL COMMENT '动作：SUBMIT/UPDATE/RESUBMIT/CANCEL/STATION_APPROVE/STATION_REJECT/FINAL_APPROVE/FINAL_REJECT/REVOKE/NOTIFY_SKIP',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照',
  `operator_role` VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色快照',
  `time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（只插不改，无 update_time）',
  `from_status`   VARCHAR(20)  DEFAULT NULL COMMENT '变更前状态',
  `to_status`     VARCHAR(20)  DEFAULT NULL COMMENT '变更后状态',
  `before`        JSON         DEFAULT NULL COMMENT '变更前快照（低频读取）',
  `after`         JSON         DEFAULT NULL COMMENT '变更后快照（低频读取）',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注（驳回原因 / 撤回原因 / 排障说明）',
  PRIMARY KEY (`id`),
  KEY `idx_leave_log_leave` (`leave_id`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请假操作留痕（追加型，只增不改）';

-- 请假全局设置（单行开关）
CREATE TABLE `leave_setting` (
  `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leave_deduct_enabled` TINYINT  NOT NULL DEFAULT 0 COMMENT '请假扣款开关：0=不扣（默认），1=请假按缺勤计',
  `create_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请假全局设置（单行开关）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `leave_setting`;
--   DROP TABLE IF EXISTS `leave_log`;
--   DROP TABLE IF EXISTS `leave_request`;
-- ----------------------------------------------------------------
