-- ----------------------------------------------------------------
-- Flyway 迁移：V10__work_order.sql（批次 P8 · 工单）
-- 权威设计：server-architecture.md §4.1/§4.2（P8）、§6.2 P8
-- 字段真源：hrm-demo/src/shared/mock/{db.js, routes/workOrder.js} + constants/dict.js
--
-- 为什么这样设计：
--   1. Mock 的工单把处理时间线以 handle_log（JSON 内嵌数组）存放；本方案拆到
--      work_order_timeline 子表（架构 §4.2 明确「替代 Mock 的 handle_log JSON 内嵌，
--      须与 api.md 出参兼容 → Service 组装回 handleLog[]」），保持契约不变。
--   2. work_order_timeline 与 work_order_transfer 均为追加型留痕：无 is_deleted / update_time，
--      业务时间即 time / transfer_time。
--   3. work_order_dispatch_rule 是「企微群消息 → 工单类型/优先级」的可热改规则（算法 S6）。
--   4. SLA 阈值：sla_deadline 显式列；阈值本身按口径 Q4 待裁定（裁定前 48/24/8 等价现状）。
--   5. 高频查询：列表按 (station_id,status)/(assignee_id,status)，超时筛选按 sla_deadline，
--      详情/查重按 order_no。
-- ----------------------------------------------------------------

-- 工单
CREATE TABLE `work_order` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`      VARCHAR(40)  NOT NULL COMMENT '工单号（活跃唯一）',
  `type`          TINYINT      NOT NULL COMMENT '类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=待处理 1=处理中 2=已解决 3=已关闭',
  `priority`      TINYINT      NOT NULL DEFAULT 1 COMMENT '优先级：0=低 1=中 2=高',
  `title`         VARCHAR(100) NOT NULL COMMENT '标题（1-100 字）',
  `content`       VARCHAR(500) DEFAULT NULL COMMENT '描述（≤500 字）',
  `source`        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL=手工，AUTO_WECHAT=企微自动派发',
  `station_id`    BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `parcel_id`     BIGINT       DEFAULT NULL COMMENT '关联包裹（逻辑外键 parcel.id）',
  `waybill_no`    VARCHAR(50)  DEFAULT NULL COMMENT '关联运单号',
  `reporter_id`   BIGINT       DEFAULT NULL COMMENT '上报人（企微自动派发为空，逻辑外键 employee.id）',
  `assignee_id`   BIGINT       DEFAULT NULL COMMENT '处理人（无候选时为空=转人工，逻辑外键 employee.id）',
  `sla_deadline`  DATETIME     DEFAULT NULL COMMENT 'SLA 截止时间',
  `resolved_time` DATETIME     DEFAULT NULL COMMENT '解决时间',
  `closed_time`   DATETIME     DEFAULT NULL COMMENT '关闭时间',
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_work_order_order_no` (`order_no`),
  KEY `idx_work_order_station_status` (`station_id`, `status`),
  KEY `idx_work_order_assignee_status` (`assignee_id`, `status`),
  KEY `idx_work_order_sla` (`sla_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单（时间线见 work_order_timeline）';

-- 工单处理时间线（替代 Mock handle_log 内嵌 JSON，追加型）
CREATE TABLE `work_order_timeline` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_order_id` BIGINT       NOT NULL COMMENT '工单（逻辑外键 work_order.id）',
  `action`        VARCHAR(20)  NOT NULL COMMENT '动作：create/accept/resolve/close/reopen/assign/transfer/auto_dispatch',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人（系统/企微来源为空）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照',
  `content`       VARCHAR(500) DEFAULT NULL COMMENT '内容',
  `time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间（只插不改，无 update_time）',
  PRIMARY KEY (`id`),
  KEY `idx_work_order_timeline_order` (`work_order_id`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单处理时间线（追加型，只增不改）';

-- 工单转单留痕（追加型）
CREATE TABLE `work_order_transfer` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_order_id`      BIGINT       NOT NULL COMMENT '工单（逻辑外键 work_order.id）',
  `from_employee_id`   BIGINT       DEFAULT NULL COMMENT '原处理人（逻辑外键 employee.id）',
  `from_employee_name` VARCHAR(50)  DEFAULT NULL COMMENT '原处理人姓名快照',
  `to_employee_id`     BIGINT       NOT NULL COMMENT '新处理人（逻辑外键 employee.id）',
  `to_employee_name`   VARCHAR(50)  DEFAULT NULL COMMENT '新处理人姓名快照',
  `reason`             VARCHAR(200) NOT NULL COMMENT '转单理由（2-100 字）',
  `operator_id`        BIGINT       DEFAULT NULL COMMENT '转单操作人（逻辑外键 employee.id）',
  `operator_name`      VARCHAR(50)  DEFAULT NULL COMMENT '转单操作人姓名快照',
  `transfer_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '转单时间（只插不改）',
  PRIMARY KEY (`id`),
  KEY `idx_work_order_transfer_order` (`work_order_id`, `transfer_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单转单留痕（追加型，只增不改）';

-- 企微自动派单规则（可热改）
CREATE TABLE `work_order_dispatch_rule` (
  `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `keyword`             VARCHAR(20) NOT NULL COMMENT '群消息关键词（1-20 字）',
  `work_order_type`     TINYINT     NOT NULL COMMENT '命中后工单类型：1-4',
  `priority`            TINYINT     NOT NULL DEFAULT 1 COMMENT '命中后优先级：0-2',
  `default_assignee_id` BIGINT      DEFAULT NULL COMMENT '默认处理人（可为空，不绑定驿站，逻辑外键 employee.id）',
  `enabled`             TINYINT     NOT NULL DEFAULT 1 COMMENT '启用：0=停用，1=启用',
  `is_deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_work_order_dispatch_rule_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企微自动派单规则';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `work_order_dispatch_rule`;
--   DROP TABLE IF EXISTS `work_order_transfer`;
--   DROP TABLE IF EXISTS `work_order_timeline`;
--   DROP TABLE IF EXISTS `work_order`;
-- ----------------------------------------------------------------
