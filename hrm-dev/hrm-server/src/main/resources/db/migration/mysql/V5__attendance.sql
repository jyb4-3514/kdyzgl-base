-- ----------------------------------------------------------------
-- Flyway 迁移：V5__attendance.sql（批次 P3 · 考勤与排班）
-- 权威设计：server-architecture.md §4.1/§4.2（P3）、§6.2 P3
-- 字段真源：hrm-demo/src/shared/mock/attendanceStore.js + constants/dict.js
--
-- 为什么这样设计（时段模型是唯一真源）：
--   1. attendance_rule.check_periods（JSON）是打卡时间的唯一真源，check_frequency 只决定
--      每日几段；work_start_time / work_end_time 是「首段开始 / 末段结束」的派生值，
--      与 Mock saveRule 的重算口径一致，避免两个口径各说各话。
--   2. 一驿一规则：station_id 由 Service 活跃查重保证唯一（决策 D7），此处只建普通索引。
--   3. attendance_schedule 的 (employee_id, work_date) 既是「活跃唯一查重」键，也是排班
--      与打卡记录自洽的引用口径；查询矩阵走 (station_id, work_date) 与 (employee_id, work_date)。
--   4. attendance_record 的 status（NORMAL/LATE/EARLY_LEAVE/ABNORMAL）是打卡事实枚举；
--      出勤口径（应到=排班人数、实到=非 ABNORMAL 上班卡）在 Service 聚合，本表只存事实。
--      source 区分正常打卡（NORMAL）与补卡补录（MAKEUP）；补卡补录无设备校验，校验列置空。
--   5. attendance_makeup 的审批只改状态与审批信息，不物理删除；驳回后可重新申请。
--   6. 高频筛选均显式列：records 按 employee/station + work_date 区间、makeup 按 employee/station + status。
--   7. 打卡次数类字段不塞 JSON：period_index / check_type / status / source 均显式列。
-- ----------------------------------------------------------------

-- 打卡规则（一驿一条）
CREATE TABLE `attendance_rule` (
  `id`                        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`                BIGINT        NOT NULL COMMENT '驿站（逻辑外键 station.id，一驿一条，活跃唯一）',
  `rule_name`                 VARCHAR(50)   NOT NULL COMMENT '规则名称',
  `enable_wifi`               TINYINT       NOT NULL DEFAULT 1 COMMENT '启用 WiFi 校验：0/1',
  `enable_location`           TINYINT       NOT NULL DEFAULT 1 COMMENT '启用定位校验：0/1',
  `enable_time_window`        TINYINT       NOT NULL DEFAULT 1 COMMENT '启用时间窗校验：0/1',
  `match_mode`                VARCHAR(10)   NOT NULL DEFAULT 'ALL' COMMENT '校验项组合：ALL=全部满足，ANY=任一满足',
  `wifi_list`                 JSON          DEFAULT NULL COMMENT 'WiFi 白名单 [{ssid,bssid}]（低频读取、结构多变）',
  `longitude`                 DECIMAL(10,6) DEFAULT NULL COMMENT '电子围栏中心经度',
  `latitude`                  DECIMAL(10,6) DEFAULT NULL COMMENT '电子围栏中心纬度',
  `radius`                    INT           DEFAULT NULL COMMENT '电子围栏半径（米）',
  `check_frequency`           INT           NOT NULL DEFAULT 2 COMMENT '每日打卡次数：2=单时段，4=双时段',
  `check_periods`             JSON          DEFAULT NULL COMMENT '时段明细 [{name,startTime,endTime}]（时间判定唯一真源）',
  `allow_early_min`           INT           NOT NULL DEFAULT 30 COMMENT '时间窗提前量（分钟）',
  `allow_late_min`            INT           NOT NULL DEFAULT 60 COMMENT '时间窗延后量（分钟）',
  `work_start_time`           VARCHAR(5)    DEFAULT NULL COMMENT '派生：首时段开始（HH:mm）',
  `work_end_time`             VARCHAR(5)    DEFAULT NULL COMMENT '派生：末时段结束（HH:mm，可 24:00）',
  `late_threshold_min`        INT           NOT NULL DEFAULT 30 COMMENT '迟到判定阈值（分钟）',
  `early_leave_threshold_min` INT           NOT NULL DEFAULT 30 COMMENT '早退判定阈值（分钟）',
  `status`                    TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `is_deleted`                TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_rule_station` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡规则（一驿一条，时段表为唯一真源）';

-- 班次
CREATE TABLE `attendance_shift` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`   BIGINT      NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `shift_name`   VARCHAR(20) NOT NULL COMMENT '班次名',
  `start_time`   VARCHAR(5)  NOT NULL COMMENT '开始（HH:mm）',
  `end_time`     VARCHAR(5)  NOT NULL COMMENT '结束（HH:mm，可 24:00 表示跨零点收班）',
  `color`        VARCHAR(20) DEFAULT NULL COMMENT '班次色值（前端展示 Token）',
  `rest_minutes` INT         NOT NULL DEFAULT 60 COMMENT '休息时长（分钟）',
  `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `is_deleted`   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_shift_station` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班次';

-- 排班（按周矩阵铺表）
CREATE TABLE `attendance_schedule` (
  `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`  BIGINT  NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `employee_id` BIGINT  NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `work_date`   DATE    NOT NULL COMMENT '工作日期',
  `shift_id`    BIGINT  NOT NULL COMMENT '班次（逻辑外键 attendance_shift.id）',
  `is_deleted`  TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_schedule_station_date` (`station_id`, `work_date`),
  KEY `idx_attendance_schedule_emp_date` (`employee_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班（员工+日期活跃唯一，Service 查重）';

-- 打卡记录（打卡事实）
CREATE TABLE `attendance_record` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`      BIGINT        NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `station_id`       BIGINT        NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `work_date`        DATE          NOT NULL COMMENT '工作日期',
  `period_index`     INT           NOT NULL DEFAULT 0 COMMENT '时段序号（从 0 起，与规则 check_periods 对齐）',
  `period_name`      VARCHAR(20)   DEFAULT NULL COMMENT '时段名快照',
  `check_type`       VARCHAR(5)    NOT NULL COMMENT '打卡类型：ON=上班卡，OFF=下班卡',
  `check_time`       DATETIME      NOT NULL COMMENT '打卡时间',
  `status`           VARCHAR(16)   NOT NULL COMMENT '打卡状态：NORMAL/LATE/EARLY_LEAVE/ABNORMAL',
  `source`           VARCHAR(10)   NOT NULL DEFAULT 'NORMAL' COMMENT '来源：NORMAL=正常打卡，MAKEUP=补卡补录',
  `check_mode`       VARCHAR(20)   DEFAULT NULL COMMENT '命中校验项：WIFI/LOCATION/WIFI+LOCATION（补卡为空）',
  `wifi_ssid`        VARCHAR(64)   DEFAULT NULL COMMENT '打卡时 WiFi SSID',
  `wifi_matched`     TINYINT       DEFAULT NULL COMMENT 'WiFi 是否命中：0/1（补卡为空）',
  `longitude`        DECIMAL(10,6) DEFAULT NULL COMMENT '打卡经度',
  `latitude`         DECIMAL(10,6) DEFAULT NULL COMMENT '打卡纬度',
  `distance`         DECIMAL(10,1) DEFAULT NULL COMMENT '距围栏中心距离（米）',
  `location_matched` TINYINT       DEFAULT NULL COMMENT '定位是否命中：0/1（补卡为空）',
  `remark`           VARCHAR(255)  DEFAULT NULL COMMENT '备注（迟到/早退/异常原因）',
  `is_deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_record_emp_date` (`employee_id`, `work_date`),
  KEY `idx_attendance_record_station_date` (`station_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡记录（打卡事实；异常卡不计入出勤口径）';

-- 补卡申请
CREATE TABLE `attendance_makeup` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`    BIGINT       NOT NULL COMMENT '申请人（逻辑外键 employee.id）',
  `station_id`     BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `work_date`      DATE         NOT NULL COMMENT '补卡日期',
  `period_index`   INT          NOT NULL DEFAULT 0 COMMENT '时段序号',
  `period_name`    VARCHAR(20)  DEFAULT NULL COMMENT '时段名快照',
  `check_type`     VARCHAR(5)   NOT NULL COMMENT '打卡类型：ON/OFF',
  `reason`         VARCHAR(200) NOT NULL COMMENT '补卡理由',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
  `apply_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `approver_id`    BIGINT       DEFAULT NULL COMMENT '审批人（ADMIN，逻辑外键 employee.id）',
  `approve_time`   DATETIME     DEFAULT NULL COMMENT '审批时间',
  `approve_remark` VARCHAR(200) DEFAULT NULL COMMENT '审批意见',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_makeup_emp_status` (`employee_id`, `status`),
  KEY `idx_attendance_makeup_station_status` (`station_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补卡申请（审批通过回写打卡记录）';

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `attendance_makeup`;
--   DROP TABLE IF EXISTS `attendance_record`;
--   DROP TABLE IF EXISTS `attendance_schedule`;
--   DROP TABLE IF EXISTS `attendance_shift`;
--   DROP TABLE IF EXISTS `attendance_rule`;
-- ----------------------------------------------------------------
