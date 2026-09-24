-- ----------------------------------------------------------------
-- Flyway 迁移：V6__kpi.sql（批次 P4 · KPI 考核）
-- 权威设计：server-architecture.md §4.1/§4.2（P4）、§5.2/§5.3（配置快照）、§6.2 P4
-- 字段真源：hrm-demo/src/shared/mock/kpiStore.js + constants/dict.js
--
-- 为什么这样设计：
--   1. kpi_metric 是「业务口径可热改」的配置表（权重/目标/评分规则/适用角色），
--      运营可在管理端调整；算分时把当时的配置整体快照进 kpi_score.metric_detail，
--      保证「改了权重后历史结果不回改」（可解释、可审计）。
--   2. score_rule（mode+fullScore）与 role_scope 拆为显式列：它们结构固定、且需要
--      可读与约束，避免用 JSON 承载核心配置（架构 §4.6(3) 明确禁止核心字段进 JSON）。
--      role_scope 以逗号分隔角色串存储（NULL/空 = 全员适用），由 Service 与数组互转。
--   3. kpi_score 一行 = 一员工一账期（活跃唯一 (employee_id, month)，Service 查重），
--      总分/达成率/等级显式列 + metric_detail JSON 承载逐指标明细（低频读取、结构多变）。
--   4. station_id 为「数据范围收敛」与「驿站排行」所需（架构 §6.2 P4「非 ADMIN 静默收敛」），
--      属必要补充列（架构 §4.2 未列出，但列表/排名需要覆盖索引支撑，禁止 SELECT * 友好化）。
--   5. 高频查询与排序显式建索引：详情/(employee,month)、范围收敛与排名/(month,station_id)、
--      排名排序/(month,total_score)。
-- ----------------------------------------------------------------

-- KPI 指标配置
CREATE TABLE `kpi_metric` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `metric_key`   VARCHAR(40)   NOT NULL COMMENT '指标键（活跃唯一；评分快照按此可读）',
  `metric_name`  VARCHAR(50)   NOT NULL COMMENT '指标名',
  `metric_type`  VARCHAR(20)   NOT NULL COMMENT '类型：PARCEL/PICKUP/COMPLAINT/ATTENDANCE/SERVICE/WORK_ORDER/TRAINING/OTHER',
  `weight`       INT           NOT NULL DEFAULT 0 COMMENT '权重（启用指标合计须 100）',
  `target_value` DECIMAL(12,2) DEFAULT NULL COMMENT '目标值',
  `unit`         VARCHAR(10)   DEFAULT NULL COMMENT '单位',
  `direction`    VARCHAR(5)    NOT NULL DEFAULT 'UP' COMMENT '方向：UP=越高越好，DOWN=越低越好',
  `score_mode`   VARCHAR(10)   NOT NULL DEFAULT 'LINEAR' COMMENT '评分规则：LINEAR/TIERED/BINARY',
  `full_score`   DECIMAL(6,2)  NOT NULL DEFAULT 100 COMMENT '单项满分',
  `role_scope`   VARCHAR(60)   DEFAULT NULL COMMENT '适用角色，逗号分隔（ADMIN/STATION_ADMIN/STAFF），空=全员',
  `enabled`      TINYINT       NOT NULL DEFAULT 1 COMMENT '启用：0=停用，1=启用',
  `sort_order`   INT           NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `remark`       VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `is_deleted`   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_kpi_metric_key` (`metric_key`),
  KEY `idx_kpi_metric_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='KPI 指标配置（业务口径可热改）';

-- KPI 月度评分（一员工一账期一行，含算分配置快照）
CREATE TABLE `kpi_score` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`      BIGINT       NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `station_id`       BIGINT       DEFAULT NULL COMMENT '统计时归属驿站（数据范围收敛/驿站排行用，逻辑外键 station.id）',
  `month`            CHAR(7)      NOT NULL COMMENT '考核月份 yyyy-MM',
  `total_score`      DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '加权总分',
  `achievement_rate` DECIMAL(6,4) NOT NULL DEFAULT 0 COMMENT '平均达成率',
  `level`            VARCHAR(16)  DEFAULT NULL COMMENT '等级：EXCELLENT/GOOD/PASS/IMPROVE',
  `metric_count`     INT          NOT NULL DEFAULT 0 COMMENT '参与指标数',
  `weight_sum`       INT          NOT NULL DEFAULT 0 COMMENT '参与权重合计',
  `metric_detail`    JSON         DEFAULT NULL COMMENT '逐指标算分快照（目标/实际/达成率/单项分/加权分/评分规则）',
  `calculate_time`   DATETIME     DEFAULT NULL COMMENT '算分时间',
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_kpi_score_emp_month` (`employee_id`, `month`),
  KEY `idx_kpi_score_month_station` (`month`, `station_id`),
  KEY `idx_kpi_score_month_score` (`month`, `total_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='KPI 月度评分（(employee_id,month) 活跃唯一，Service 查重）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `kpi_score`;
--   DROP TABLE IF EXISTS `kpi_metric`;
-- ----------------------------------------------------------------
