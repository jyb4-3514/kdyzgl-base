-- ----------------------------------------------------------------
-- Flyway 迁移：V8__payroll.sql（批次 P6 · 财务）
-- 权威设计：server-architecture.md §4.1/§4.2（P6）、§5.2/§5.3、§6.2 P6
-- 字段真源：hrm-demo/src/shared/mock/financeStore.js + constants/dict.js
--
-- 为什么这样设计：
--   1. 表驱动算薪：payroll_rule + payroll_rule_item 描述「来源(source)+参数(params)」，
--      新增规则项不改代码（算法 S2）。params 为低频读取、结构随来源而变，用 JSON 承载。
--   2. payroll 一行 = 一员工一账期一类型（月度/离职结算），(employee_id, month, bill_type)
--      由 Service 活跃查重；rule_snapshot 存算薪时的规则快照，保证历史可解释。
--   3. 出参金额字段以 Mock 契约为准：addition_total / deduction_total / gross_amount(应发)
--      / net_amount(实发)。架构 §4.2 写作 total_amount，对应本表 gross_amount（事实性核对见 db.md）。
--   4. 六态状态机（DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED）：
--      员工仅见 PUBLISHED/CONFIRMED（Service 收口），approve/publish/confirm/objection 时间显式列。
--   5. payroll_item 为工资单明细子表（替代 Mock 内嵌 items[]），Service 组装回 items[]。
--      金额只存正数，增/扣语义由 item_type 承载（与 dict.PAYROLL_ITEM_TYPE 一致）。
--   6. 高频查询：列表按 (month, status) + station 收敛；详情/幂等按 (employee_id, month, bill_type)；
--      单号查重/定位按 payroll_no。
-- ----------------------------------------------------------------

-- 计薪规则
CREATE TABLE `payroll_rule` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name`   VARCHAR(50)  NOT NULL COMMENT '规则名',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_rule_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计薪规则（表驱动）';

-- 计薪规则项
CREATE TABLE `payroll_rule_item` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id`     BIGINT      NOT NULL COMMENT '规则（逻辑外键 payroll_rule.id）',
  `item_key`    VARCHAR(40) NOT NULL COMMENT '规则项键',
  `item_name`   VARCHAR(50) NOT NULL COMMENT '规则项名',
  `item_type`   VARCHAR(16) NOT NULL COMMENT '类型：ADDITION=增项，DEDUCTION=扣项',
  `source`      VARCHAR(16) NOT NULL COMMENT '来源：FIXED/ATTENDANCE/KPI/MANUAL',
  `params`      JSON        DEFAULT NULL COMMENT '来源解析参数（字段/指标/模式/金额/上限等，结构随来源而变）',
  `enabled`     TINYINT     NOT NULL DEFAULT 1 COMMENT '启用：0=停用，1=启用',
  `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_rule_item_rule` (`rule_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计薪规则项';

-- 工资单
CREATE TABLE `payroll` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `payroll_no`       VARCHAR(40)   NOT NULL COMMENT '工资单号（活跃唯一）',
  `employee_id`      BIGINT        NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `station_id`       BIGINT        DEFAULT NULL COMMENT '归属驿站（数据范围收敛用，逻辑外键 station.id）',
  `month`            CHAR(7)       NOT NULL COMMENT '账期 yyyy-MM',
  `bill_type`        VARCHAR(16)   NOT NULL DEFAULT 'MONTHLY' COMMENT '单据类型：MONTHLY=月度工资单，SETTLEMENT=离职结算单',
  `rule_id`          BIGINT        DEFAULT NULL COMMENT '计薪规则（逻辑外键 payroll_rule.id）',
  `rule_name`        VARCHAR(50)   DEFAULT NULL COMMENT '规则名快照',
  `rule_snapshot`    JSON          DEFAULT NULL COMMENT '算薪时的规则快照（历史可解释）',
  `addition_total`   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '增项合计',
  `deduction_total`  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '扣项合计',
  `gross_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '应发合计（=增项合计）',
  `net_amount`       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实发净额（应发-扣项）',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED',
  `remark`           VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `approve_remark`   VARCHAR(255)  DEFAULT NULL COMMENT '审核意见',
  `approver_id`      BIGINT        DEFAULT NULL COMMENT '审核人（逻辑外键 employee.id）',
  `approver_name`    VARCHAR(50)   DEFAULT NULL COMMENT '审核人姓名快照',
  `approve_time`     DATETIME      DEFAULT NULL COMMENT '审核时间',
  `publisher_id`     BIGINT        DEFAULT NULL COMMENT '发布人（逻辑外键 employee.id）',
  `publisher_name`   VARCHAR(50)   DEFAULT NULL COMMENT '发布人姓名快照',
  `publish_time`     DATETIME      DEFAULT NULL COMMENT '发布时间',
  `confirm_time`     DATETIME      DEFAULT NULL COMMENT '员工确认时间',
  `objection_reason` VARCHAR(255)  DEFAULT NULL COMMENT '员工异议原因',
  `objection_time`   DATETIME      DEFAULT NULL COMMENT '异议时间',
  `offboarding_id`   BIGINT        DEFAULT NULL COMMENT '离职流程（结算单来源，逻辑外键 hr_flow.id）',
  `is_deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_payroll_no` (`payroll_no`),
  KEY `idx_payroll_emp_month_bill` (`employee_id`, `month`, `bill_type`),
  KEY `idx_payroll_month_status` (`month`, `status`),
  KEY `idx_payroll_month_station` (`month`, `station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资单';

-- 工资单明细（子表）
CREATE TABLE `payroll_item` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `payroll_id`  BIGINT        NOT NULL COMMENT '工资单（逻辑外键 payroll.id）',
  `item_key`    VARCHAR(40)   NOT NULL COMMENT '规则项键',
  `item_name`   VARCHAR(50)   NOT NULL COMMENT '规则项名',
  `item_type`   VARCHAR(16)   NOT NULL COMMENT '类型：ADDITION=增项，DEDUCTION=扣项',
  `source`      VARCHAR(16)   NOT NULL COMMENT '来源：FIXED/ATTENDANCE/KPI/MANUAL',
  `amount`      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '金额（正数，增/扣由 item_type 承载）',
  `detail`      VARCHAR(255)  DEFAULT NULL COMMENT '取数解释文案',
  `sort_order`  INT           NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `is_deleted`  TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_item_payroll` (`payroll_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资单明细（子表）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `payroll_item`;
--   DROP TABLE IF EXISTS `payroll`;
--   DROP TABLE IF EXISTS `payroll_rule_item`;
--   DROP TABLE IF EXISTS `payroll_rule`;
-- ----------------------------------------------------------------
