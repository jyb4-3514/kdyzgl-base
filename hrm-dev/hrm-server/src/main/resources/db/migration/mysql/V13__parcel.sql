-- ----------------------------------------------------------------
-- Flyway 迁移：V13__parcel.sql（批次 P10 · 包裹，20 万级大表）
-- 权威设计：server-architecture.md §4.1/§4.3/§4.4（P10 → V13）、§6.2 P10、§6.4
-- 算法建议：docs/algo-hrm-server.md §13（S7 追加建议索引）
-- 字段真源：hrm-demo/src/shared/mock/parcelStore.js + constants/dict.js
--
-- 已联评：算法 §13（大表索引建议已与算法工程师方案对齐；结构变更须数据库+算法联评，规则 §6.8 / 架构 §4.3）
--
-- 为什么这样设计：
--   1. 大表建表纪律（架构 §4.3 / 任务硬约束）：CREATE TABLE 只建主键，索引一律用独立
--      CREATE INDEX 语句，便于「逐条创建 / 逐条回滚 / 低峰执行」；不使用一次性内联 KEY。
--   2. 唯一性按决策 D7 + 架构 §4.6(2)：不建数据库唯一索引，(station_id, waybill_no) 由
--      Service 层「活跃查重」保证，数据库仅建普通索引加速查重（与逻辑删除语义兼容）。
--   3. 索引对齐架构 §4.3 四条 + 算法 §13：
--      - idx_parcel_station_waybill         (station_id, waybill_no)           防重复入库查重
--      - idx_parcel_station_status          (station_id, status)               按驿站/状态筛选
--      - idx_parcel_station_status_inbound  (station_id, status, inbound_time DESC)  列表默认排序+状态筛选一次走索引
--      - idx_parcel_station_inbound         (station_id, inbound_time DESC)   趋势/近 N 天聚合
--   4. 分页（ADR-06）：默认游标分页 (inbound_time, id)（算法 S7：20 万级扫描行 38 vs 深分页 20020）；
--      OFFSET 仅作兼容兜底并限制最大页深。首版 hrm.algo.parcel.page.mode=CURSOR。
--   5. 查询纪律：禁止 SELECT *；列表只取必要列；聚合走覆盖索引；
--      EXPLAIN 须 type=range/ref 且无 Using filesort（收敛到服务器复核，算法 §12.3）。
--   6. 容量：20 万行估算约 106 MB（算法 S7），落 /data 数据盘占不足 0.3%。
--   7. receiver_name / receiver_phone 出参脱敏（maskName / maskPhone）。
--   8. 无删除语义（采集数据），但保留 is_deleted 以对齐全局逻辑删除约定与未来软删扩展。
-- ----------------------------------------------------------------

CREATE TABLE `parcel` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`         BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id，数据可见范围核心维度）',
  `waybill_no`         VARCHAR(50)  NOT NULL COMMENT '运单号（与 station_id 组合活跃唯一，Service 查重）',
  `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=待入库 1=在库待取 2=已取件 3=异常 4=已退回',
  `receiver_name`      VARCHAR(50)  DEFAULT NULL COMMENT '收件人姓名（出参脱敏）',
  `receiver_phone`     VARCHAR(20)  DEFAULT NULL COMMENT '收件人手机号（出参脱敏）',
  `shelf_code`         VARCHAR(20)  DEFAULT NULL COMMENT '货架位编码',
  `inbound_time`       DATETIME     NOT NULL COMMENT '入库时间（列表默认排序键）',
  `pickup_employee_id` BIGINT       DEFAULT NULL COMMENT '取件员工（逻辑外键 employee.id）',
  `pickup_time`        DATETIME     DEFAULT NULL COMMENT '取件时间',
  `sync_batch_no`      VARCHAR(40)  DEFAULT NULL COMMENT '来源同步批次号（逻辑外键 sync_task.batch_no）',
  `remark`             VARCHAR(255) DEFAULT NULL COMMENT '备注（异常件说明）',
  `is_deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='包裹数据（20 万级大表；结构变更须数据库+算法联评）';

-- 索引 1/4：防重复入库查重（Service 活跃查重 + 普通索引，决策 D7）
CREATE INDEX `idx_parcel_station_waybill` ON `parcel` (`station_id`, `waybill_no`) COMMENT '按驿站+运单号查重';
-- 索引 2/4：按驿站/状态筛选（架构 §4.3 / db.md §6.2 预告）
CREATE INDEX `idx_parcel_station_status` ON `parcel` (`station_id`, `status`) COMMENT '按驿站+状态筛选';
-- 索引 3/4：列表默认排序 + 状态筛选一次走索引（算法 §13 追加建议）
CREATE INDEX `idx_parcel_station_status_inbound` ON `parcel` (`station_id`, `status`, `inbound_time` DESC) COMMENT '列表默认排序+状态筛选';
-- 索引 4/4：趋势/近 N 天聚合（算法 §13 追加建议）
CREATE INDEX `idx_parcel_station_inbound` ON `parcel` (`station_id`, `inbound_time` DESC) COMMENT '趋势/近 N 天聚合';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP INDEX `idx_parcel_station_inbound`        ON `parcel`;
--   DROP INDEX `idx_parcel_station_status_inbound` ON `parcel`;
--   DROP INDEX `idx_parcel_station_status`         ON `parcel`;
--   DROP INDEX `idx_parcel_station_waybill`        ON `parcel`;
--   DROP TABLE IF EXISTS `parcel`;
--
-- 适用条件提示（MySQL 8 在线 DDL，架构 §4.6(2)）：
--   - 本表首建无历史数据，直接 CREATE。
--   - 后续大表加列：优先 ALGORITHM=INSTANT（仅限列尾追加、可空/带默认值），否则 INPLACE；
--     禁止原地 MODIFY 大表列类型（走 expand-contract）。
--   - 后续大表加索引：优先 ALGORITHM=INPLACE, LOCK=NONE（需核实列类型与索引类型兼容）。
-- ----------------------------------------------------------------
