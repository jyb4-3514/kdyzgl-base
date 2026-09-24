-- ----------------------------------------------------------------
-- Flyway 迁移：V7__hr.sql（批次 P5 · 人事）
-- 权威设计：server-architecture.md §4.1/§4.2（P5）、§6.2 P5
-- 字段真源：hrm-demo/src/shared/mock/hrStore.js + constants/dict.js
--
-- 为什么这样设计：
--   1. hr_profile ↔ employee 1:1：以 employee_id 为「活跃唯一」逻辑键（Service 查重），
--      主键仍用自增 id（遵循一期主键约定，便于 MyBatis-Plus 统一处理）。
--   2. 离职判定以 hr_profile.leave_date 为准，而非 employee.status（status=0 同时表示
--      「禁用」与「离职」，语义重叠，Mock 已明确以 leave_date 判定）。
--   3. hr_salary 存「当前定薪」；hr_salary_log 存调薪留痕（含入职定薪），只增不改，
--      当前定薪 = 最新生效的一条 log。留痕为追加型审计表：无 is_deleted / update_time。
--   4. hr_flow 用 flow_type 区分入职/离职，同一张表；steps 拆到 hr_flow_step 子表，
--      出参由 Service 组装回 steps[]，与 Mock 契约兼容。
--      离职结算单引用 settlement_payroll_id（不反向依赖财务域，靠领域事件编排）。
--   5. 敏感字段（emergency_contact_phone / bank_account）出参一律脱敏（maskPhone / maskBankAccount）。
--   6. 高频查询：profile/定薪按 employee_id；流程列表按 (flow_type, status) + 编号/人员查重。
-- ----------------------------------------------------------------

-- 人事档案（员工 1:1）
CREATE TABLE `hr_profile` (
  `id`                         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`                BIGINT        NOT NULL COMMENT '员工（逻辑外键 employee.id，1:1，活跃唯一）',
  `education`                  VARCHAR(20)   DEFAULT NULL COMMENT '学历：MASTER/BACHELOR/COLLEGE/HIGH_SCHOOL',
  `contract_type`              VARCHAR(20)   DEFAULT NULL COMMENT '合同类型：FIXED_TERM/NON_FIXED_TERM/INTERN/DISPATCH',
  `contract_start`             DATE          DEFAULT NULL COMMENT '合同起始日',
  `contract_end`               DATE          DEFAULT NULL COMMENT '合同到期日（到期预警派生）',
  `probation_months`           INT           NOT NULL DEFAULT 0 COMMENT '试用期（月）',
  `probation_end`              DATE          DEFAULT NULL COMMENT '试用期结束日',
  `regular_date`               DATE          DEFAULT NULL COMMENT '转正日期',
  `social_security_base`       DECIMAL(12,2) DEFAULT NULL COMMENT '社保基数',
  `emergency_contact_name`     VARCHAR(50)   DEFAULT NULL COMMENT '紧急联系人姓名',
  `emergency_contact_phone`    VARCHAR(20)   DEFAULT NULL COMMENT '紧急联系人电话（出参脱敏）',
  `emergency_contact_relation` VARCHAR(20)   DEFAULT NULL COMMENT '与本人关系',
  `bank_name`                  VARCHAR(50)   DEFAULT NULL COMMENT '开户行',
  `bank_account`               VARCHAR(32)   DEFAULT NULL COMMENT '银行卡号（出参脱敏，保留末 4 位）',
  `leave_date`                 DATE          DEFAULT NULL COMMENT '离岗日期（离职判定依据，非 employee.status）',
  `is_deleted`                 TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_profile_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人事档案（员工 1:1）';

-- 当前定薪（员工 1:1）
CREATE TABLE `hr_salary` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`      BIGINT        NOT NULL COMMENT '员工（逻辑外键 employee.id，1:1，活跃唯一）',
  `basic_salary`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '基本工资',
  `post_salary`      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '岗位工资',
  `performance_base` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '绩效基数',
  `allowances`       JSON          DEFAULT NULL COMMENT '津贴项 [{key,name,amount}]（低频读取、结构多变）',
  `allowances_total` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '津贴合计',
  `total_salary`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '定薪合计',
  `effective_date`   DATE          DEFAULT NULL COMMENT '生效日期',
  `is_deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_salary_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前定薪（员工 1:1）';

-- 调薪留痕（追加型审计表）
CREATE TABLE `hr_salary_log` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`      BIGINT        NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `change_type`      VARCHAR(20)   NOT NULL COMMENT '变更类型：ENTRY=入职定薪，ADJUST=调薪',
  `basic_salary`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '基本工资（变更后）',
  `post_salary`      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '岗位工资（变更后）',
  `performance_base` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '绩效基数（变更后）',
  `allowances`       JSON          DEFAULT NULL COMMENT '津贴项快照（低频读取）',
  `allowances_total` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '津贴合计',
  `total_salary`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '定薪合计',
  `effective_date`   DATE          DEFAULT NULL COMMENT '生效日期',
  `reason`           VARCHAR(200)  DEFAULT NULL COMMENT '变更原因',
  `operator_id`      BIGINT        DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id）',
  `operator_name`    VARCHAR(50)   DEFAULT NULL COMMENT '操作人姓名快照',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留痕时间（只增不改，无 update_time）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_salary_log_emp` (`employee_id`, `effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调薪留痕（追加型，只增不改）';

-- 入职/离职流程（同一张表，flow_type 区分）
CREATE TABLE `hr_flow` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_type`             VARCHAR(16)   NOT NULL COMMENT '流程类型：ONBOARDING=入职，OFFBOARDING=离职',
  `flow_no`               VARCHAR(40)   NOT NULL COMMENT '流程编号（活跃唯一）',
  `candidate_name`        VARCHAR(50)   DEFAULT NULL COMMENT '候选人姓名（入职）',
  `employee_id`           BIGINT        DEFAULT NULL COMMENT '员工（离职；入职建档后回填）',
  `phone`                 VARCHAR(20)   DEFAULT NULL COMMENT '联系电话（入职）',
  `gender`                TINYINT       DEFAULT NULL COMMENT '性别：0=未知，1=男，2=女',
  `education`             VARCHAR(20)   DEFAULT NULL COMMENT '学历（入职）',
  `dept_id`               BIGINT        DEFAULT NULL COMMENT '部门（逻辑外键 department.id）',
  `station_id`            BIGINT        DEFAULT NULL COMMENT '驿站（逻辑外键 station.id）',
  `position`              VARCHAR(50)   DEFAULT NULL COMMENT '岗位',
  `role`                  VARCHAR(20)   DEFAULT NULL COMMENT '角色：STAFF/STATION_ADMIN',
  `expected_entry_date`   DATE          DEFAULT NULL COMMENT '预计入职日期（入职）',
  `type`                  VARCHAR(20)   DEFAULT NULL COMMENT '离职类型：RESIGN/DISMISS/RETIRE（离职，契约字段 type）',
  `reason`                VARCHAR(200)  DEFAULT NULL COMMENT '离职原因（离职）',
  `last_work_date`        DATE          DEFAULT NULL COMMENT '最后工作日（离职）',
  `settlement_payroll_id` BIGINT        DEFAULT NULL COMMENT '离职结算单 ID（离职 SETTLEMENT 步骤回填，逻辑外键 payroll.id）',
  `settlement_payroll_no` VARCHAR(40)   DEFAULT NULL COMMENT '离职结算单号快照',
  `settlement_amount`     DECIMAL(12,2) DEFAULT NULL COMMENT '结算金额快照',
  `leave_date`            DATE          DEFAULT NULL COMMENT '离岗日期（离职 LEAVE 步骤写入）',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '流程状态：IN_PROGRESS/COMPLETED/REJECTED',
  `reject_reason`         VARCHAR(200)  DEFAULT NULL COMMENT '驳回原因',
  `rejected_by`           VARCHAR(50)   DEFAULT NULL COMMENT '驳回人姓名快照',
  `rejected_time`         DATETIME      DEFAULT NULL COMMENT '驳回时间',
  `current_step_key`      VARCHAR(30)   DEFAULT NULL COMMENT '当前待办步骤键',
  `remark`                VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `is_deleted`            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_flow_no` (`flow_no`),
  KEY `idx_hr_flow_type_status` (`flow_type`, `status`),
  KEY `idx_hr_flow_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入职/离职流程（flow_type 区分）';

-- 流程步骤（子表，随主表，无独立删除）
CREATE TABLE `hr_flow_step` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_id`       BIGINT       NOT NULL COMMENT '流程（逻辑外键 hr_flow.id）',
  `step_key`      VARCHAR(30)  NOT NULL COMMENT '步骤键',
  `step_name`     VARCHAR(50)  NOT NULL COMMENT '步骤名',
  `step_order`    INT          NOT NULL DEFAULT 0 COMMENT '步骤顺序（升序）',
  `status`        VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT '步骤状态：PENDING=待办理，DONE=已完成',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '办理人（逻辑外键 employee.id）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '办理人姓名快照',
  `operate_time`  DATETIME     DEFAULT NULL COMMENT '办理时间',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '办理备注',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_flow_step_flow` (`flow_id`, `step_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入职/离职流程步骤（子表）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `hr_flow_step`;
--   DROP TABLE IF EXISTS `hr_flow`;
--   DROP TABLE IF EXISTS `hr_salary_log`;
--   DROP TABLE IF EXISTS `hr_salary`;
--   DROP TABLE IF EXISTS `hr_profile`;
-- ----------------------------------------------------------------
