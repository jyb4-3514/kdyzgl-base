-- ----------------------------------------------------------------
-- Flyway 迁移：V3__client_log.sql（批次 P1 · 运行日志）
-- 权威设计：docs/db.md §7.3 / §8.1（db.md 已定义字段与语义，本脚本仅落 DDL，不新增语义）
-- 依据：server-architecture.md §4.4（P1 → V3）、§6.2 P1、api.md §7.5
--
-- 为什么这样设计：
--   1. 本表是「只插不改+清空=物理删除」的运行日志，故不设 is_deleted / update_time；
--      业务时间即 time（沿用 login_log 的日志表例外约定，不可变数据无更新语义）。
--   2. count / first_time / last_time 承载「指纹去重」的聚合口径：
--      (message+route+code) 10 秒窗口内重复出现只累加 count 并刷新 last_time（算法 S8，
--      指纹计算与窗口判定在 Service 内完成，本表只持久化聚合结果）。
--   3. message / stack 入库前必须白名单脱敏 + textMax 截断（默认 2000）：
--      token / 密码 / 身份证 / 手机号全量 / 银行卡 / 请求响应体原文一律不入库（api.md §7.5）。
--   4. 高频筛选 level / source / 时间区间 / employee_id / keyword：
--      - level + 时间排序走 idx_client_log_time / idx_client_log_level；
--      - keyword 为 message 前缀模糊 LIKE，不建索引（日志表，量级可控）。
--   5. 索引克制（2 个）与 db.md §7.3 一致，不为日志表引入过多索引拖慢写入。
-- ----------------------------------------------------------------

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

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `client_log`;
-- ----------------------------------------------------------------
