-- ----------------------------------------------------------------
-- Flyway 迁移：V15__auth_trusted_device.sql（登录体系改造 · 服务端持有设备信任态）
-- 权威设计：multi-client-architecture.md §4.2.1 表1 `auth_trusted_device`（设备信任模型）
-- 安全依据：security-auth-review.md §3 加固建议（信任态必须由服务端持有，前端指纹仅弱信号、
--           不得作放行依据）、§4.2（服务端签发 device_token）
-- 字段真源：multi-client-architecture.md §4.1.3（设备信息采集字段）
--
-- 为什么这样设计：
--   1. 信任态由服务端持有（安全整改核心）：登录/二次验证通过后由服务端签发 device_token，
--      本表只存其 SHA-256 摘要（device_token_hash），**绝不落明文**；前端采集的设备属性仅作弱信号，
--      服务端再以 HMAC-SHA256 生成 device_fingerprint（外置盐，不入库），仅用于幂等登记与审计。
--   2. 幂等登记：以 (employee_id, device_fingerprint) 唯一键 upsert，同一员工同设备重复登录只刷新
--      last_seen_time / expires_at，不产生重复行（架构 §4.2.1「唯一，幂等 upsert」）。
--      这是决策 D7「不建数据库唯一索引」的**显式例外**：本表撤销以业务标志 revoked 表达（非 is_deleted
--      逻辑删除），撤销后可复用同一行重新受信，不与唯一键冲突（论证见 db.md §10.2 / §9.1 Q-DB-5）。
--   3. 本表**不设 is_deleted / 不适用追加型例外**：撤销 = 业务状态（revoked / revoked_at），
--      需在设备列表中可查、可审计，且须与唯一键 upsert 兼容；故偏离 db.md §8.0「全表用 is_deleted」的
--      默认约定，作为显式登记例外（见 db.md §10.2 例外声明）。
--   4. 时效：expires_at 为信任有效期截止（到期须重新短信验证，具体周期由配置决定）；
--      first_seen_time 记首次受信时刻、last_seen_time 记最近活跃，均为服务端权威时间。
--   5. 弱信号快照（device_id / platform / model / os_version / app_version）**仅审计与展示**，
--      不作为放行依据；last_ip 出参须脱敏（安全报告 §4.2）。
--   6. 不建物理外键（D6），employee_id 为逻辑外键；create_time / update_time 仅 DEFAULT 兜底、
--      由应用层填充（D8），不使用 ON UPDATE CURRENT_TIMESTAMP。
--   7. 索引克制（每表 ≤ 3 条），覆盖：按员工列设备（唯一键最左前缀）、登录按指纹反查、按令牌摘要校验。
--
-- 不落库的数据（见 db.md §10.4）：短信验证码 / 二次验证票据 / device_token 明文 / 会话 —— 一律走 Redis，
-- 不建表；本迁移**不**建 `auth_sms_log`（架构 §4.2.1 表2「可选但建议」，本轮未纳入，待主智能体裁定）。
-- ----------------------------------------------------------------

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

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `auth_trusted_device`;
-- 说明：索引为建表内联（UNIQUE KEY / KEY），随 DROP TABLE 一并删除，无需单独 DROP INDEX。
--   本表为新建空表，回滚无业务数据丢失；但撤销信任记录将一并清除，回滚后所有设备视为未信任。
-- ----------------------------------------------------------------
