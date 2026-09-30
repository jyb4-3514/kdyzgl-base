-- ----------------------------------------------------------------
-- 快递驿站智汇系统 · 当前结构快照（MySQL 8）
-- 依据：docs/db.md §5.3（sql/schema 快照同步约定）、server-architecture.md §4.5
-- 内容：== Flyway V1 + V2(种子) + V3..V23 的最终结构态；仅 DDL、无业务数据。
--   V1  department / station / employee / login_log          4 表（一期）
--   V3  client_log                                           1 表（P1）
--   V4  notification                                         1 表（P2）
--   V5  attendance 5 表                                        P3
--   V6  kpi 2 表                                               P4
--   V7  hr 5 表                                                P5
--   V8  payroll 4 表                                           P6
--   V9  leave 3 表                                             P7
--   V10 work_order 4 表                                        P8
--   V11 sync_task 2 表（运行态）                                P9
--   V12 sync_config 5 表（采集配置 + 配置中心元数据）            P9
--   V13 parcel 1 表（20 万级大表）                              P10
--   V14 hr_flow 补 2 列（operator_id / operator_name）          登录改造前置修复
--   V15 auth_trusted_device 1 表（服务端持有设备信任态）        登录体系改造
--   V16 employee 补生成列 phone_active + 唯一索引（活跃唯一）   注册前置缺陷修复 M-5
--   V17 employee_registration 1 表（注册事实与凭据载体）        员工自助注册
--   V18 hr_flow 补 source 列（NOT NULL DEFAULT 'ADMIN'）        来源留痕 M-9
--   V19 employee 补 position 列（岗位进档案）                   方案乙 U-07
--   V20 薪资结算自动化 3 表 + payroll 补 3 列（已发放）          B1（待技术评审）
--   V21 薪资结算自动化增量（payroll_log 定位列 / payroll_run.attempt_date + uk_attempt / station_payroll_setting_log / notification.type 注释补 7/8/9） B1
--   V22 attendance_schedule 补生成列 active_shift_key + 唯一键（活跃唯一）   排班多班次 B7
--   V23 operation_audit_log 1 表（操作审计留痕，追加型，口令只记布尔）       安全 M-1/REG-01 B1
-- 用途：供评审与 DBA 查看；执行来源唯一为 Flyway 目录，
--       请勿直接以本快照为起点做增量变更。
-- 变更纪律：后续 Flyway 新增 Vn 结构脚本时，必须同步刷新本快照；已执行脚本永不修改。
-- 库：kdyzgl（utf8mb4 / utf8mb4_0900_ai_ci，建库见 docs/deploy.md 与 TASK.md D02）
-- 公共设计决策（详见 db.md 第 1 章）：
--   D6 逻辑外键：不建物理 FOREIGN KEY，引用完整性由 Service 层校验；
--   D7 唯一性：由 Service 层「活跃数据查重」保证，数据库仅建普通索引加速查重（兼容逻辑删除）；
--   D8 时间字段：create_time / update_time 仅设 DEFAULT CURRENT_TIMESTAMP 兜底，
--      不使用 ON UPDATE CURRENT_TIMESTAMP，由应用层统一填充；
--   追加型日志/留痕表（login_log / client_log / hr_salary_log / leave_log /
--       work_order_timeline / work_order_transfer / sync_task_log / payroll_log /
--       station_payroll_setting_log / operation_audit_log）不设 is_deleted / update_time，
--      不可变数据无更新与删除语义（db.md 3.4 例外约定）；
--   JSON 列仅用于「低频读取、结构多变」字段（db.md §8.9 边界说明）。
-- ----------------------------------------------------------------

CREATE TABLE `department` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父部门ID，0=根节点',
  `dept_name`   VARCHAR(50) NOT NULL COMMENT '部门名称',
  `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '同级排序（升序）',
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_department_parent_id` (`parent_id`),
  KEY `idx_department_dept_name` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（树形结构）';

CREATE TABLE `station` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`           VARCHAR(50)  NOT NULL COMMENT '驿站编码，活跃唯一，二期爬虫对接标识',
  `station_name`   VARCHAR(50)  NOT NULL COMMENT '驿站名称',
  `contact_person` VARCHAR(50)  DEFAULT NULL COMMENT '负责人姓名',
  `contact_phone`  VARCHAR(20)  DEFAULT NULL COMMENT '联系电话（出参脱敏）',
  `address`        VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_station_code` (`code`),
  KEY `idx_station_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站表';

CREATE TABLE `employee` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(30)  NOT NULL COMMENT '登录账号，活跃唯一，创建后不可修改',
  `password`        VARCHAR(100) NOT NULL COMMENT '密码散列（BCrypt $2a$ cost=10）',
  `real_name`       VARCHAR(50)  NOT NULL COMMENT '员工姓名',
  `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号，活跃唯一，出参脱敏',
  `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女',
  `dept_id`         BIGINT       DEFAULT NULL COMMENT '所属部门ID（逻辑外键 department.id）',
  `station_id`      BIGINT       DEFAULT NULL COMMENT '归属驿站ID（逻辑外键 station.id）',
  `role`            VARCHAR(20)  NOT NULL DEFAULT 'STAFF' COMMENT '角色：ADMIN/STATION_ADMIN(二期)/STAFF',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `pwd_changed`     TINYINT      NOT NULL DEFAULT 0 COMMENT '密码是否已由本人修改：0=未修改，1=已修改',
  `entry_date`      DATE         DEFAULT NULL COMMENT '入职日期',
  `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后成功登录时间',
  `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  `phone_active`    VARCHAR(20)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `phone`, NULL)) STORED COMMENT '活跃手机号生成列：is_deleted=0 取 phone，否则 NULL；仅活跃行唯一（V16）',
  `position`        VARCHAR(50)  DEFAULT NULL COMMENT '岗位（员工档案属性，权威事实；自由文本，与 hr_flow.position 双写；存量未登记为 NULL；V19）',
  PRIMARY KEY (`id`),
  KEY `idx_employee_username` (`username`),
  KEY `idx_employee_phone` (`phone`),
  KEY `idx_employee_dept_id` (`dept_id`),
  KEY `idx_employee_station_id` (`station_id`),
  KEY `idx_employee_create_time` (`create_time`),
  UNIQUE KEY `uk_employee_phone_active` (`phone_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工表（员工即账号）';

CREATE TABLE `login_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`     VARCHAR(30)  NOT NULL COMMENT '尝试登录的账号名',
  `employee_id`   BIGINT       DEFAULT NULL COMMENT '员工ID（账号存在时记录）',
  `login_ip`     VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '客户端IP（取 X-Forwarded-For 首个）',
  `login_result` TINYINT      NOT NULL COMMENT '登录结果：0=失败，1=成功',
  `fail_reason`  VARCHAR(50)  DEFAULT NULL COMMENT '失败原因（账号密码错误/账号已禁用）',
  `user_agent`   VARCHAR(255) DEFAULT NULL COMMENT '浏览器UA（截断至255）',
  `login_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_login_time` (`login_time`),
  KEY `idx_login_log_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表（只插不改不删）';

-- ================================================================
-- P1 · 运行日志（V3__client_log.sql）
-- ================================================================

CREATE TABLE `client_log` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志发生时间（业务时间；本表只插不改，无 update_time）',
  `level`       VARCHAR(10)   NOT NULL DEFAULT 'ERROR' COMMENT '级别：INFO/WARN/ERROR',
  `source`      VARCHAR(10)   NOT NULL DEFAULT 'PC' COMMENT '上报端：PC/H5/SHELL',
  `employee_id` BIGINT        DEFAULT NULL COMMENT '上报人（逻辑外键 employee.id，未登录为空）',
  `route`       VARCHAR(200)  DEFAULT NULL COMMENT '前端路由',
  `message`     VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '日志内容（白名单脱敏后、textMax 截断）',
  `stack`       VARCHAR(2000) DEFAULT NULL COMMENT '错误堆栈（脱敏、截断）',
  `method`      VARCHAR(10)   DEFAULT NULL COMMENT 'HTTP 方法',
  `path`        VARCHAR(500)  DEFAULT NULL COMMENT '请求路径（已去 query/hash，防凭据随路径入库）',
  `status`      INT           DEFAULT NULL COMMENT 'HTTP 状态码',
  `code`        INT           DEFAULT NULL COMMENT '业务码',
  `duration`    INT           DEFAULT NULL COMMENT '耗时（毫秒）',
  `ua`          VARCHAR(300)  DEFAULT NULL COMMENT '浏览器 UA',
  `count`       INT           NOT NULL DEFAULT 1 COMMENT '指纹去重窗口内累计出现次数',
  `first_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现时间',
  `last_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近出现时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_log_time` (`time`),
  KEY `idx_client_log_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前端运行日志（只插不改，清空=物理删除）';

-- ================================================================
-- P2 · 通知（V4__notification.sql）
-- ================================================================

CREATE TABLE `notification` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`    BIGINT       NOT NULL COMMENT '接收人（逻辑外键 employee.id）',
  `type`           TINYINT      NOT NULL COMMENT '类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果 7=工资单待审核（→管理员） 8=工资单已发布（→员工本人） 9=工资单异议退回（→管理员）',
  `title`          VARCHAR(100) NOT NULL COMMENT '标题（1-100 字）',
  `content`        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '内容（1-500 字）',
  `biz_type`       VARCHAR(32)  DEFAULT NULL COMMENT '业务跳转类型：work_order/sync_task/leave（公告为空）',
  `biz_id`         BIGINT       DEFAULT NULL COMMENT '业务跳转对象 ID',
  `is_read`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  `read_time`      DATETIME     DEFAULT NULL COMMENT '已读时间',
  `is_published`   TINYINT      NOT NULL DEFAULT 0 COMMENT '来源：0=系统联动，1=手工发布',
  `publisher_id`   BIGINT       DEFAULT NULL COMMENT '发布人（手工发布时，逻辑外键 employee.id）',
  `publisher_name` VARCHAR(50)  DEFAULT NULL COMMENT '发布人姓名快照',
  `publish_scope`  VARCHAR(10)  DEFAULT NULL COMMENT '发布范围：ALL/STATION/EMPLOYEE（系统联动为空）',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  KEY `idx_notification_employee_read` (`employee_id`, `is_read`),
  KEY `idx_notification_employee_time` (`employee_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知（系统联动 + 手工发布）';

-- ================================================================
-- P3 · 考勤与排班（V5__attendance.sql）
-- ================================================================

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

CREATE TABLE `attendance_schedule` (
  `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`  BIGINT  NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `employee_id` BIGINT  NOT NULL COMMENT '员工（逻辑外键 employee.id）',
  `work_date`   DATE    NOT NULL COMMENT '工作日期',
  `shift_id`    BIGINT  NOT NULL COMMENT '班次（逻辑外键 attendance_shift.id）',
  `is_deleted`  TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  `active_shift_key` VARCHAR(64) GENERATED ALWAYS AS (IF(`is_deleted` = 0, CONCAT(`employee_id`, '|', `work_date`, '|', `shift_id`), NULL)) STORED COMMENT '活跃排班键生成列：is_deleted=0 时拼 employee_id|work_date|shift_id，否则 NULL；仅活跃行唯一（V22）',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_schedule_station_date` (`station_id`, `work_date`),
  KEY `idx_attendance_schedule_emp_date` (`employee_id`, `work_date`),
  UNIQUE KEY `uk_attendance_schedule_active_shift` (`active_shift_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班（员工+日期+班次 活跃唯一：生成列 active_shift_key 唯一键收口「活跃唯一」，软删行置 NULL 不占键；V22）';

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

-- ================================================================
-- P4 · KPI 考核（V6__kpi.sql）
-- ================================================================

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

-- ================================================================
-- P5 · 人事（V7__hr.sql）
-- ================================================================

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
  `operator_id`           BIGINT        DEFAULT NULL COMMENT '创建人（逻辑外键 employee.id；V14 补列）',
  `operator_name`         VARCHAR(50)   DEFAULT NULL COMMENT '创建人姓名快照（V14 补列）',
  `source`                VARCHAR(16)   NOT NULL DEFAULT 'ADMIN' COMMENT '业务来源：ADMIN=后台创建，SELF_REGISTER=员工自助注册（M-9 来源留痕；V18 补列）',
  PRIMARY KEY (`id`),
  KEY `idx_hr_flow_no` (`flow_no`),
  KEY `idx_hr_flow_type_status` (`flow_type`, `status`),
  KEY `idx_hr_flow_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入职/离职流程（flow_type 区分）';

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

-- ================================================================
-- P6 · 财务（V8__payroll.sql）
-- ================================================================

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
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED/OBJECTED/PAID（V20 补 OBJECTED/PAID 两态说明）',
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
  `paid_by_id`       BIGINT        DEFAULT NULL COMMENT '确认发放人（逻辑外键 employee.id；已发放终态；V20 补列）',
  `paid_by_name`     VARCHAR(50)   DEFAULT NULL COMMENT '确认发放人姓名快照（V20 补列）',
  `paid_time`        DATETIME      DEFAULT NULL COMMENT '确认发放时间（V20 补列）',
  PRIMARY KEY (`id`),
  KEY `idx_payroll_payroll_no` (`payroll_no`),
  KEY `idx_payroll_emp_month_bill` (`employee_id`, `month`, `bill_type`),
  KEY `idx_payroll_month_status` (`month`, `status`),
  KEY `idx_payroll_month_station` (`month`, `station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资单';

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

-- ================================================================
-- P7 · 请假（V9__leave.sql）
-- ================================================================

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

CREATE TABLE `leave_setting` (
  `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leave_deduct_enabled` TINYINT  NOT NULL DEFAULT 0 COMMENT '请假扣款开关：0=不扣（默认），1=请假按缺勤计',
  `create_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请假全局设置（单行开关）';

-- ================================================================
-- P8 · 工单（V10__work_order.sql）
-- ================================================================

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

-- ================================================================
-- P9 · 同步运行态（V11__sync_task.sql）
-- ================================================================

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

-- ================================================================
-- P9 · 采集配置与配置中心元数据（V12__sync_config_center.sql）
-- ================================================================

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

-- ================================================================
-- P10 · 包裹（V13__parcel.sql，20 万级大表；CREATE TABLE + 独立 CREATE INDEX）
-- ================================================================

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

CREATE INDEX `idx_parcel_station_waybill` ON `parcel` (`station_id`, `waybill_no`) COMMENT '按驿站+运单号查重';
CREATE INDEX `idx_parcel_station_status` ON `parcel` (`station_id`, `status`) COMMENT '按驿站+状态筛选';
CREATE INDEX `idx_parcel_station_status_inbound` ON `parcel` (`station_id`, `status`, `inbound_time` DESC) COMMENT '列表默认排序+状态筛选';
CREATE INDEX `idx_parcel_station_inbound` ON `parcel` (`station_id`, `inbound_time` DESC) COMMENT '趋势/近 N 天聚合';

-- ================================================================
-- 登录体系改造（V15__auth_trusted_device.sql）
-- 服务端持有设备信任态；前端指纹仅弱信号，不作放行依据（security-auth-review.md §4.2）。
-- 短信验证码 / 二次验证票据 / device_token 明文 / 会话 一律走 Redis，不建表（db.md §10.4）。
-- ================================================================

CREATE TABLE `auth_trusted_device` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`        BIGINT      NOT NULL COMMENT '归属员工（逻辑外键 employee.id）',
  `device_fingerprint` CHAR(64)    NOT NULL COMMENT '服务端设备指纹摘要（HMAC-SHA256 hex；弱信号，仅幂等登记/审计，非放行依据）',
  `device_token_hash`  CHAR(64)    NOT NULL COMMENT '服务端签发 device_token 的摘要（SHA-256 hex；仅存摘要，绝不存明文）',
  `device_id`          VARCHAR(64) DEFAULT NULL COMMENT '前端上报设备ID（仅展示/排障，不作放行依据）',
  `platform`           VARCHAR(16) NOT NULL COMMENT '端平台：ANDROID/IOS/H5/WEB',
  `model`              VARCHAR(64) DEFAULT NULL COMMENT '设备型号（弱信号快照，仅审计）',
  `os_version`         VARCHAR(32) DEFAULT NULL COMMENT '系统版本（弱信号快照，仅审计）',
  `app_version`        VARCHAR(32) DEFAULT NULL COMMENT '壳版本（H5 为空；弱信号快照，仅审计）',
  `last_ip`            VARCHAR(45) DEFAULT NULL COMMENT '最近来源 IP（IPv4/IPv6；出参脱敏）',
  `first_seen_time`    DATETIME    NOT NULL COMMENT '首次受信时间（信任建立时刻，重信不复位）',
  `last_seen_time`     DATETIME    NOT NULL COMMENT '最近活跃时间（每次成功校验刷新）',
  `expires_at`         DATETIME    DEFAULT NULL COMMENT '信任有效期截止（到期须重新验证；NULL=由配置周期决定）',
  `trusted`            TINYINT     NOT NULL DEFAULT 1 COMMENT '是否受信：0=否，1=是',
  `revoked`            TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已撤销：0=否，1=是（软撤销，保留审计）',
  `revoked_at`         DATETIME    DEFAULT NULL COMMENT '撤销时间（改密/强制下线/自助撤销时写入，审计）',
  `create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_trusted_device_emp_fp` (`employee_id`, `device_fingerprint`),
  KEY `idx_auth_trusted_device_fp` (`device_fingerprint`),
  KEY `idx_auth_trusted_device_token` (`device_token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受信设备（服务端持有信任态；登录体系改造 V15）';

-- ================================================================
-- 员工自助注册（V17__employee_registration.sql）
-- 注册事实与凭据载体；审批载体仍复用 hr_flow（1:1，registration.flow_id ↔ hr_flow.id）。
-- 唯一索引例外：uk_employee_registration_apply_no（U-17）；phone 走 Service 查重 + 普通索引。
-- 凭据卫生：password_hash / query_token_hash 终态置 NULL；query_token_hash 一期恒不写入。
-- 说明：V16（employee 活跃唯一）、V18（hr_flow.source）、V19（employee.position）为既有表增量，
--       对应列/唯一键已在各表内联（见 employee / hr_flow 定义），本节仅新增表。
-- ================================================================

CREATE TABLE `employee_registration` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `apply_no`             VARCHAR(32)  NOT NULL COMMENT '申请编号（唯一，形如 RG-YYYYMMDD-0001；两段式生成）',
  `flow_id`              BIGINT       DEFAULT NULL COMMENT '关联审批单（逻辑外键 hr_flow.id，提交时写入）',
  `real_name`            VARCHAR(50)  NOT NULL COMMENT '姓名（2-20，对齐既有 onboarding 校验口径）',
  `phone`                VARCHAR(20)  NOT NULL COMMENT '手机号 ^1[3-9]\\d{9}$（活跃唯一由 employee/hr_flow 侧收口）',
  `password_hash`        VARCHAR(100) DEFAULT NULL COMMENT '注册自设密码 BCrypt 散列（cost=10；仅合规留痕、非初始口令；终态置 NULL）',
  `apply_station_id`     BIGINT       DEFAULT NULL COMMENT '意向驿站（逻辑外键 station.id；仅意向）',
  `apply_position`       VARCHAR(50)  DEFAULT NULL COMMENT '意向岗位（自由文本，对齐 hr_flow.position；仅意向）',
  `source`               VARCHAR(16)  NOT NULL DEFAULT 'STAFF_H5' COMMENT '注册渠道来源（审计；一期仅 STAFF_H5）',
  `agreement_version`    VARCHAR(20)  DEFAULT NULL COMMENT '已同意的服务条款版本（合规留痕）',
  `query_token_hash`     VARCHAR(64)  DEFAULT NULL COMMENT '查询凭据 SHA-256（一期不启用，恒不写入；列保留供后续自助查询）',
  `status`               VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED/APPROVED/REJECTED/EXPIRED（CANCELLED 保留不用）',
  `reject_reason`        VARCHAR(200) DEFAULT NULL COMMENT '驳回原因快照',
  `approved_employee_id` BIGINT       DEFAULT NULL COMMENT '通过后生成的员工（逻辑外键 employee.id）',
  `approve_time`         DATETIME     DEFAULT NULL COMMENT '通过时间',
  `cancel_time`          DATETIME     DEFAULT NULL COMMENT '取消时间（一期不用，随 R-4 取消而保留列）',
  `expire_time`          DATETIME     DEFAULT NULL COMMENT '失效判定基准（create_time + 7 天，惰性判定）',
  `client_ip`            VARCHAR(50)  DEFAULT NULL COMMENT '提交来源 IP（审计；口径对齐 login_log.login_ip；出参脱敏）',
  `is_deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否，1=是',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_registration_apply_no` (`apply_no`),
  KEY `idx_employee_registration_phone` (`phone`),
  KEY `idx_employee_registration_flow` (`flow_id`),
  KEY `idx_employee_registration_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工自助注册申请单（注册事实与凭据载体；V17）';

-- ================================================================
-- 薪资结算自动化（V20__payroll_automation.sql + V21__payroll_log_locator.sql）
-- 4 新表：station_payroll_setting（一驿一条算薪配置）/ payroll_log（追加型留痕）/ payroll_run（运行记录与认领槽位幂等）
--   / station_payroll_setting_log（配置变更审计，V21）。
-- payroll 增量：paid_by_id / paid_by_name / paid_time 加列与 status COMMENT 8 态，已内联于上方 payroll 定义。
-- V21 增量：payroll_log 补冗余定位列 employee_id / month 与索引；payroll_run 补 attempt_date（NOT NULL）
--   与唯一键 uk_attempt，并 DROP 冗余索引 idx_payroll_run_station_month（被 uk_attempt 最左前缀覆盖）、
--   skip_code 去 EXHAUSTED、表注释加「每自然日至多一次」语义；新增 station_payroll_setting_log。
-- 幂等硬约束：uk_payroll_run_claim (station_id, claim_key)——claim_key = target_month，RUNNING/SUCCESS/SKIPPED
--   均写值（占位），FAILED 置 NULL（释放）；NULL 可多行，故同驿站同账期至多一条占位行、FAILED 可多行。
--   另 uk_attempt (station_id, target_month, attempt_date) 管「每自然日至多一次」（同日第 2 条 INSERT 撞 1062）。
-- 说明：payroll_log / station_payroll_setting_log 为追加型留痕，无 is_deleted / update_time（沿用 leave_log 例外约定）。
-- ================================================================

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

CREATE TABLE `payroll_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `payroll_id`    BIGINT       NOT NULL COMMENT '工资单（逻辑外键 payroll.id）；generate 覆盖重建会物理删除 DRAFT/REJECTED 单，本列可能指向已删单（孤儿），故须配合下方 employee_id / month 冗余定位列检索（V21 更新注释）',
  `employee_id`   BIGINT       DEFAULT NULL COMMENT '冗余定位列：留痕所属员工（逻辑外键 employee.id）；使留痕可脱离已删 payroll_id 按「员工 + 账期」独立检索（V21 补列）',
  `month`         CHAR(7)      DEFAULT NULL COMMENT '冗余定位列：账期 yyyy-MM（V21 补列）',
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
  KEY `idx_payroll_log_action_time` (`action`, `time`),
  KEY `idx_payroll_log_emp_month` (`employee_id`, `month`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资单操作留痕（追加型，只增不改；V20）';

CREATE TABLE `payroll_run` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`      BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id）',
  `target_month`    CHAR(7)      NOT NULL COMMENT '目标账期 yyyy-MM',
  `attempt_date`    DATE         NOT NULL COMMENT '本次尝试的自然日（Asia/Shanghai 墙钟；写 now.toLocalDate()）——「同一驿站同一账期每自然日至多尝试一次」的闸门依据与连续失败天数统计口径（V21 补列，U-06）',
  `trigger_type`    VARCHAR(16)  NOT NULL COMMENT '触发方式：AUTO=定时到点，CATCH_UP=补跑，MANUAL=手工触发',
  `due_at`          DATETIME     NOT NULL COMMENT '本次应执行时刻（Asia/Shanghai 墙钟，判定「错过」的基准）',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '结果：RUNNING/SUCCESS/FAILED/SKIPPED',
  `skip_code`       VARCHAR(24)  DEFAULT NULL COMMENT '跳过码（机器可读）：BLOCKED_9405/CONFIG_INVALID/DRAFT_PROTECTED；配合 skip_reason，供指标统计与「是否重试」判定（V21 移除 EXHAUSTED：无硬上限，连续失败改为告警）',
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
  UNIQUE KEY `uk_attempt` (`station_id`, `target_month`, `attempt_date`),
  KEY `idx_payroll_run_status_time` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动算薪运行记录（认领槽位幂等 + 日粒度闸门：uk_payroll_run_claim 管跨日终态占位 [RUNNING/SUCCESS/SKIPPED 每驿站每账期至多一行]、uk_attempt 管日内一次 [同一驿站同一账期每自然日至多一条]；V20/V21）';

CREATE TABLE `station_payroll_setting_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `station_id`    BIGINT       NOT NULL COMMENT '驿站（逻辑外键 station.id；非空，与 payroll_log.payroll_id 等价定位）',
  `action`        VARCHAR(16)  NOT NULL COMMENT '动作：CREATE=首次创建 / UPDATE=字段变更 / ENABLE=启用(0→1) / DISABLE=停用(1→0)（每次保存必写一条）',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id）',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照',
  `operator_role` VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色快照',
  `before`        JSON         DEFAULT NULL COMMENT '变更前快照（白名单键：enabled/payrollDay/payrollTime/notifyEnabled/remark；CREATE 时为 NULL）',
  `after`         JSON         DEFAULT NULL COMMENT '变更后快照（同白名单键，不含凭据 / 个人信息）',
  `time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（只插不改，无 update_time）',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_station_payroll_setting_log_station_time` (`station_id`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='驿站算薪配置变更审计（追加型，只增不改；每次保存必写一条；V21）';

-- ================================================================
-- 操作审计留痕（V23__operation_audit_log.sql）
-- 通用追加型审计：覆盖 employee / station 的新增 / 编辑 / 启停 / 删除 / 重置口令（安全必做项 M-1 / REG-01）。
-- 追加型：无 is_deleted / update_time（不可变数据，db.md §8.0(4) 例外约定）。
-- 时间列命名 time：与既有留痕 leave_log / payroll_log / station_payroll_setting_log 对齐（评审 M-7）。
-- 口令安全：before / after / changed_fields 白名单化，口令只记布尔（{"password":"SET"/"RESET"}），绝不落明文/散列（§3.4）。
-- 说明：V22（attendance_schedule 生成列 active_shift_key + 唯一键）为既有表增量，已内联于上方 attendance_schedule 定义。
-- ================================================================

CREATE TABLE `operation_audit_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id`     BIGINT       DEFAULT NULL COMMENT '操作人（逻辑外键 employee.id；SYSTEM 触发为空）',
  `operator_name`   VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名快照（对齐 payroll_log）',
  `operator_role`   VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色快照（ADMIN/STATION_ADMIN/STAFF）',
  `operator_type`   VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '操作主体：USER=人工（默认），SYSTEM=系统自动（对齐 payroll_log.operator_type）',
  `target_type`     VARCHAR(16)  NOT NULL COMMENT '目标类型：EMPLOYEE=员工账号 / STATION=驿站（扩展新对象只增取值，不改表结构）',
  `target_id`       BIGINT       NOT NULL COMMENT '目标主键（逻辑外键：按 target_type 指向 employee.id 或 station.id）',
  `target_name`     VARCHAR(64)  DEFAULT NULL COMMENT '目标名称快照（员工姓名 / 驿站名；目标逻辑删后仍可知「改的是谁/哪个驿站」，免回表）',
  `action`          VARCHAR(16)  NOT NULL COMMENT '动作：CREATE=新增 / UPDATE=编辑 / CHANGE_STATUS=启停 / DELETE=删除 / RESET_PASSWORD=重置口令',
  `before`          JSON         DEFAULT NULL COMMENT '变更前快照（白名单键；口令只允许布尔标记，如 {"password":"RESET"}，绝不落明文或散列）',
  `after`           JSON         DEFAULT NULL COMMENT '变更后快照（白名单键；同 before 口令约束，不得含明文/散列）',
  `changed_fields`  JSON         DEFAULT NULL COMMENT '发生变化的字段名白名单（JSON 数组，如 ["realName","phone"]）；口令变更只记 "password" 字段名',
  `client_ip`       VARCHAR(50)  DEFAULT NULL COMMENT '客户端 IP（Nginx 透传 X-Forwarded-For 首个；口径对齐 login_log.login_ip / employee_registration.client_ip）',
  `result`          VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS=成功，FAIL=失败',
  `fail_reason`     VARCHAR(200) DEFAULT NULL COMMENT '失败原因（截断，不落敏感信息 / 口令；result=FAIL 时可选填）',
  `time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（只插不改，无 update_time；命名对齐 leave_log/payroll_log 的 time，评审 M-7）',
  PRIMARY KEY (`id`),
  KEY `idx_operation_audit_target` (`target_type`, `target_id`, `time`),
  KEY `idx_operation_audit_operator` (`operator_id`, `time`),
  KEY `idx_operation_audit_action_time` (`action`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计留痕（追加型，只增不改；覆盖 employee/station 的增改启停删重置口令；口令只记布尔、绝不落明文/散列；V23）';
