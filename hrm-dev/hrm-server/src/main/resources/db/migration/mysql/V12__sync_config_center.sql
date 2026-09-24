-- ----------------------------------------------------------------
-- Flyway 迁移：V12__sync_config_center.sql（批次 P9 · 采集配置与配置中心元数据）
-- 权威设计：server-architecture.md §4.1/§4.2/§4.4（P9 → V12）、§5.2（入库可热改）、§6.2 P9
-- 字段真源：hrm-demo/src/shared/mock/{db.js, syncConfigStore.js}
--
-- 为什么这样设计（P9 拆两文件：运行态 vs 配置元数据，单一职责）：
--   1. 本文件放「采集配置 + 配置中心四层」5 表：
--      - sync_station_config          驿站的采集配置（开关/频率/数据源/时段/最近采集状态）
--      - sync_config_item             配置项定义（含值类型与约束）
--      - sync_config_option           选项集选项（数据源/频率/时段模板）
--      - sync_config_global           全局默认值（item_key → value）
--      - sync_config_station_override 驿站覆盖值（station_id + item_key → value）
--   2. 驿站生效值 = 覆盖值优先，否则继承全局默认（Service 派生 values/sources 视图，不落列）。
--   3. sync_station_config.frequency / data_source 兼容旧自由文本码与迁移后的选项 Key，
--      由 Service 层 legacyCodes 映射统一（数据侧不硬编码映射表）。
--   4. JSON 使用说明：constraints 为架构 §4.2 明列的 JSON 字段；extra_attrs / legacy_codes 属
--      同类「低频读取、结构多变」字段（见 db.md §8 一致性核对，已登记待主智能体确认）。
--   5. 三态语义不混淆：item.scope（GLOBAL/STATION）、option.source（BUILTIN/MANUAL/MIGRATED）、
--      builtin（内置不可删，仅可停用）。
--   6. 高频查询：item_key 查重、选项集内 (set_key, option_key) 查重、驿站覆盖 (station_id, item_key)。
-- ----------------------------------------------------------------

-- 驿站采集配置（一驿一行）
CREATE TABLE `sync_station_config` (
  `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`          BIGINT      NOT NULL COMMENT '驿站（逻辑外键 station.id，一驿一行，活跃唯一）',
  `enabled`             TINYINT     NOT NULL DEFAULT 0 COMMENT '采集开关：0=关闭，1=开启',
  `frequency`           VARCHAR(20) DEFAULT NULL COMMENT '采集频率码（旧码 HOURLY/EVERY_2H/EVERY_4H/DAILY，迁移后为选项 Key）',
  `data_source`         VARCHAR(50) DEFAULT NULL COMMENT '数据源（迁移后为选项 Key；未配置为空）',
  `collect_start_time`  VARCHAR(5)  DEFAULT NULL COMMENT '采集开始（HH:mm）',
  `collect_end_time`    VARCHAR(5)  DEFAULT NULL COMMENT '采集结束（HH:mm，可 24:00）',
  `last_collect_time`   DATETIME    DEFAULT NULL COMMENT '最近采集时间',
  `last_collect_status` VARCHAR(20) NOT NULL DEFAULT 'NEVER' COMMENT '最近采集状态：SUCCESS/FAILED/NEVER',
  `status`              TINYINT     NOT NULL DEFAULT 1 COMMENT '配置行状态：0=停用，1=启用',
  `is_deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_station_config_station` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站采集配置（一驿一行）';

-- 配置项定义
CREATE TABLE `sync_config_item` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `item_key`       VARCHAR(40)  NOT NULL COMMENT '配置项 Key（活跃唯一，小写字母开头）',
  `name`           VARCHAR(20)  NOT NULL COMMENT '显示名（1-20 字）',
  `description`    VARCHAR(100) DEFAULT NULL COMMENT '说明（≤100 字）',
  `value_type`     VARCHAR(20)  NOT NULL COMMENT '值类型：SINGLE_SELECT/NUMBER/TEXT/TIME/TIME_RANGE',
  `required`       TINYINT      NOT NULL DEFAULT 0 COMMENT '驿站生效值是否必填：0=否，1=是',
  `default_value`  VARCHAR(255) DEFAULT NULL COMMENT '全局默认值（原样存储，按 value_type 由服务层转换）',
  `unit`           VARCHAR(8)   DEFAULT NULL COMMENT '单位（≤8 字）',
  `constraints`    JSON         DEFAULT NULL COMMENT '取值约束（按 value_type 结构不同，低频读取）',
  `option_set_key` VARCHAR(40)  DEFAULT NULL COMMENT '关联选项集（单选型必填）',
  `scope`          VARCHAR(10)  NOT NULL DEFAULT 'STATION' COMMENT '生效范围：GLOBAL/STATION',
  `sort`           INT          NOT NULL DEFAULT 0 COMMENT '排序（0-9999）',
  `enabled`        TINYINT      NOT NULL DEFAULT 1 COMMENT '启用：0=停用，1=启用',
  `builtin`        TINYINT      NOT NULL DEFAULT 0 COMMENT '系统内置：0=否（可删），1=是（仅可停用）',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_config_item_key` (`item_key`),
  KEY `idx_sync_config_item_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置项定义';

-- 选项集选项
CREATE TABLE `sync_config_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `set_key`      VARCHAR(40)  NOT NULL COMMENT '选项集 Key：data_source/collect_frequency/time_template',
  `option_key`   VARCHAR(40)  NOT NULL COMMENT '选项 Key（集合内活跃唯一）',
  `label`        VARCHAR(40)  NOT NULL COMMENT '显示名（1-20 字）',
  `extra_attrs`  JSON         DEFAULT NULL COMMENT '附加属性（按选项集约定，如 {intervalMinutes} / {startTime,endTime}）',
  `sort`         INT          NOT NULL DEFAULT 0 COMMENT '排序（0-9999）',
  `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '启用：0=停用，1=启用',
  `builtin`      TINYINT      NOT NULL DEFAULT 0 COMMENT '系统内置：0=否（可删），1=是（仅可停用）',
  `source`       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：BUILTIN/MANUAL/MIGRATED',
  `legacy_codes` JSON         DEFAULT NULL COMMENT '兼容读取的旧码数组（迁移用，低频读取）',
  `remark`       VARCHAR(100) DEFAULT NULL COMMENT '备注（≤100 字）',
  `is_deleted`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_config_option_set_key` (`set_key`, `option_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选项集选项';

-- 全局默认值
CREATE TABLE `sync_config_global` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `item_key`    VARCHAR(40)  NOT NULL COMMENT '配置项 Key（活跃唯一）',
  `value`       VARCHAR(255) DEFAULT NULL COMMENT '全局默认值（可为空；空=未配置，驿站走未配置判定链）',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_config_global_item_key` (`item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置中心-全局默认值';

-- 驿站覆盖值
CREATE TABLE `sync_config_station_override` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`  BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `item_key`    VARCHAR(40)  NOT NULL COMMENT '配置项 Key',
  `value`       VARCHAR(255) DEFAULT NULL COMMENT '覆盖值（可为空）',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_sync_override_station_item` (`station_id`, `item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置中心-驿站覆盖值';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `sync_config_station_override`;
--   DROP TABLE IF EXISTS `sync_config_global`;
--   DROP TABLE IF EXISTS `sync_config_option`;
--   DROP TABLE IF EXISTS `sync_config_item`;
--   DROP TABLE IF EXISTS `sync_station_config`;
-- ----------------------------------------------------------------
