-- ----------------------------------------------------------------
-- 快递驿站智汇系统 · 一期表结构（MySQL 8）
-- Flyway 迁移：V1__init_schema.sql（对应任务 A01）
-- 权威设计：docs/db.md 第 3/4 章（本脚本与 db.md 4.1 参考 DDL 逐项一致）
-- 公共设计决策（详见 db.md 第 1 章）：
--   D6 逻辑外键：不建物理 FOREIGN KEY，引用完整性由 Service 层校验；
--   D7 唯一性：username / phone / station.code / 部门同级名称由 Service 层
--      「活跃数据查重」保证，数据库仅建普通索引加速查重（兼容逻辑删除）；
--   D8 时间字段：create_time / update_time 仅设 DEFAULT CURRENT_TIMESTAMP 兜底，
--      不使用 ON UPDATE CURRENT_TIMESTAMP，由应用层统一填充（双库行为一致）；
--   login_log 为只插不改不删的日志表，不设 is_deleted / update_time（db.md 3.4 例外约定）。
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
  PRIMARY KEY (`id`),
  KEY `idx_employee_username` (`username`),
  KEY `idx_employee_phone` (`phone`),
  KEY `idx_employee_dept_id` (`dept_id`),
  KEY `idx_employee_station_id` (`station_id`),
  KEY `idx_employee_create_time` (`create_time`)
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
