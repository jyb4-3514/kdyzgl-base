-- 二期采集端 · MySQL 建表脚本（仅 DDL，无任何业务数据）
-- 目标库：yizhan_collector（MySQL 8.0.46，utf8mb4 / utf8mb4_0900_ai_ci，仅监听 127.0.0.1）
-- 执行方式：由 collector.db.migrate 幂等执行（CREATE TABLE IF NOT EXISTS），不引入 Flyway（Java 侧才用）。
--
-- 命名遵循项目规范：snake_case、主键 id、时间 create_time/update_time、索引 idx_表名_字段、唯一键 uk_表名_字段。
-- 时间口径：所有 DATETIME 一律存 **UTC**（连接建立时执行 SET time_zone='+00:00'），避免采集机本地时区漂移。
-- 红线：本表**不得**出现任何 Cookie、密码、token、签名值；登录态只以落盘路径与计数留痕。

CREATE TABLE IF NOT EXISTS pdd_login_session (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_masked     VARCHAR(64)     NOT NULL COMMENT '脱敏账号（如 166****9983），仅用于人工核对',
    account_hash       CHAR(64)        NOT NULL COMMENT '账号 SHA-256 十六进制串，去重与幂等键',
    site               VARCHAR(255)    NOT NULL COMMENT '登录站点 URL（站点可配，入口与工作台可能不同域名）',
    login_status       VARCHAR(20)     NOT NULL COMMENT '登录态：PENDING/SUCCESS/FAILED/MANUAL_REQUIRED/EXPIRED',
    login_at           DATETIME        NULL COMMENT '最近一次登录成功时间（UTC）',
    expire_at          DATETIME        NULL COMMENT '登录态预计失效时间（UTC，按 session_ttl_hours 估算，未实测）',
    storage_state_ref  VARCHAR(512)    NULL COMMENT 'Playwright storage_state 落盘路径（只存路径，不存内容）',
    cookie_count       INT             NOT NULL DEFAULT 0 COMMENT '登录态 cookie 条数（仅计数，不留值）',
    last_check_at      DATETIME        NULL COMMENT '最近一次登录态探测时间（UTC）',
    fail_reason        VARCHAR(512)    NULL COMMENT '失败或人工介入原因（已脱敏，禁止写入凭据明文）',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdd_login_session_account_site (account_hash, site),
    KEY idx_pdd_login_session_login_status (login_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '拼多多登录会话（登录态持久化，一账号一站点一行，幂等 upsert）';

CREATE TABLE IF NOT EXISTS pdd_collect_cursor (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    station_code  VARCHAR(64)     NOT NULL COMMENT '驿站编码（对接一期 station.code）',
    task_key      VARCHAR(128)    NOT NULL COMMENT '任务键，如 waybill_full / waybill_incr',
    cursor_value  VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '游标值，如分页 pageNo 或增量窗口结束时间',
    last_run_at   DATETIME        NULL COMMENT '最近一次推进时间（UTC）',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdd_collect_cursor_station_task (station_code, task_key),
    KEY idx_pdd_collect_cursor_task_key (task_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '采集游标（断点续采，为 P1-03 持久化游标打地基）';
