-- ----------------------------------------------------------------
-- Flyway 迁移：V11__sync_task.sql（批次 P9 · 同步运行态）
-- 权威设计：server-architecture.md §4.1/§4.4（P9 → V11）、§6.2 P9
-- 字段真源：hrm-demo/src/shared/mock/{db.js, routes/syncTask.js} + constants/dict.js
--
-- 为什么这样设计（P9 拆两文件：运行态 vs 配置元数据，单一职责）：
--   1. 本文件只放「运行态」两表：sync_task（任务状态机四态）+ sync_task_log（批次日志）。
--   2. sync_task 四态：0=待领取 1=执行中 2=成功 3=失败；仅「失败」可重试（6001）。
--      批次号 batch_no 是采集端轮询与数据回推的对接键（活跃唯一，Service 查重）。
--   3. sync_task_log 为追加型日志：无 is_deleted / update_time，业务时间即 log_time；
--      level 与 dict.SYNC_LOG_LEVEL 一致（0=INFO 1=WARN 2=ERROR）。
--   4. 高频查询：列表按 (station_id,status) + create_time 倒序；批次号查重按 batch_no；
--      日志按 (task_id, log_time)。
-- ----------------------------------------------------------------

-- 同步任务（状态机四态）
CREATE TABLE `sync_task` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`    BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `batch_no`      VARCHAR(40)  NOT NULL COMMENT '批次号（活跃唯一，采集/回推对接键）',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=待领取 1=执行中 2=成功 3=失败',
  `parcel_total`  INT          NOT NULL DEFAULT 0 COMMENT '本批包裹总数',
  `success_count` INT          NOT NULL DEFAULT 0 COMMENT '成功条数',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败条数',
  `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `error_msg`     VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `assign_time`   DATETIME     DEFAULT NULL COMMENT '领取时间',
  `start_time`    DATETIME     DEFAULT NULL COMMENT '开始时间',
  `finish_time`   DATETIME     DEFAULT NULL COMMENT '完成时间',
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_task_batch_no` (`batch_no`),
  KEY `idx_sync_task_station_status` (`station_id`, `status`),
  KEY `idx_sync_task_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同步任务（状态机四态）';

-- 同步任务日志（追加型）
CREATE TABLE `sync_task_log` (
  `id`       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`  BIGINT        NOT NULL COMMENT '同步任务（逻辑外键 sync_task.id）',
  `batch_no` VARCHAR(40)   DEFAULT NULL COMMENT '批次号快照',
  `level`    TINYINT       NOT NULL DEFAULT 0 COMMENT '级别：0=INFO 1=WARN 2=ERROR',
  `message`  VARCHAR(1000) NOT NULL COMMENT '日志内容',
  `log_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间（只插不改，无 update_time）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_task_log_task` (`task_id`, `log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同步任务日志（追加型，只增不改）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `sync_task_log`;
--   DROP TABLE IF EXISTS `sync_task`;
-- ----------------------------------------------------------------
