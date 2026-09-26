-- ----------------------------------------------------------------
-- Flyway 迁移：V17__employee_registration.sql（新表 · 员工自助注册申请单）
-- 权威设计：registration-design.md §2.2（字段级设计，v1.3 复评「通过」）、§2.4（唯一性与并发口径）
-- 体例基准：db.md §8.0 通用约定（snake_case / 主键 id / 时间字段 / is_deleted / 不建物理外键）
--
-- 为什么这样设计：
--   1. 职责分离（§2.1 三选一结论③）：hr_flow 继续做审批载体（复用步骤机/权限/PC 审批台），
--      本表做「注册事实与凭据载体」，两表以 registration.flow_id ↔ hr_flow.id 1:1 关联，互不污染。
--   2. 唯一索引例外（U-17）：apply_no 增设 UNIQUE（申请编号无逻辑删除复用语义、公开端点并发高）；
--      phone 仍走 Service 查重 + 普通索引（活跃唯一由 employee/hr_flow 侧约束收口，与 D7 不矛盾）。
--   3. 凭据卫生：password_hash 仅作合规留痕、不作员工初始口令（M-4），进入任一终态
--      （APPROVED/REJECTED/EXPIRED）时由 Service 同事务置 NULL；query_token_hash 一期不启用、恒不写入，
--      列保留供后续自助查询（R-3 / U-06）。两者均允许 NULL（非空仅为注册时点）。
--   4. 审计与留存：source 记录注册渠道（一期仅 STAFF_H5，与 hr_flow.source 的 ADMIN/SELF_REGISTER
--      语义不同、并存）；client_ip 留痕口径对齐 login_log.login_ip；expire_time = create_time + 7 天（N=7）。
--   5. 不建物理外键（D6，逻辑外键由 Service 校验）；create_time/update_time 仅 DEFAULT 兜底、
--      由应用层 MetaObjectHandler 填充（D8，不使用 ON UPDATE CURRENT_TIMESTAMP）。
--   6. 索引克制（本表 1 唯一 + 3 普通），覆盖：编号唯一、手机查重、流程回关联、状态列表。
-- ----------------------------------------------------------------

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

-- TODO(扩展): M-8 清理任务（已驳回/超期保留 30 天）如按 status+expire_time 过滤，可后续补
--   idx_employee_registration_expire (status, expire_time)；本期按批次口径仅落 3 普通索引
--   （registration-design.md §2.2 / §9 B2）。
-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `employee_registration`;
-- 说明：索引为建表内联（UNIQUE KEY / KEY），随 DROP TABLE 一并删除，无需单独 DROP INDEX；
--   本表为新建空表，回滚无业务数据丢失。
-- ----------------------------------------------------------------
