-- ----------------------------------------------------------------
-- Flyway 迁移：V4__notification.sql（批次 P2 · 通知）
-- 权威设计：server-architecture.md §4.1（P2）、§6.2 P2
-- 字段真源：hrm-demo/src/shared/mock/{db.js, routes/notification.js} + constants/dict.js NOTIFICATION_TYPE
--
-- 为什么这样设计：
--   1. 站内信是「一行一收件人」的扇出结构：手工发布的 ALL/STATION/EMPLOYEE 范围在 Service
--      展开为多条记录（与 Mock publish 一致），故本表不存接收范围，只存最终接收人 employee_id。
--   2. is_published 区分来源（0=系统联动、1=手工发布）：
--      系统联动无发布人；手工发布带 publisher_id/publisher_name/publish_scope 快照。
--   3. biz_type/biz_id 是「点击跳转」契约（work_order / sync_task / leave），非外键。
--   4. 高频查询是「本人列表 / 未读数 / 全部已读」：
--      - 未读计数与列表 isRead 筛选走 idx_notification_employee_read (employee_id, is_read)；
--      - 列表按创建时间倒序走 idx_notification_employee_time (employee_id, create_time)。
--      两索引用最左 employee_id 收敛，避免列表与计数各扫全表。
--   5. 本表可被更新（is_read/read_time），故保留 create_time/update_time（应用层填充）。
-- ----------------------------------------------------------------

CREATE TABLE `notification` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`    BIGINT       NOT NULL COMMENT '接收人（逻辑外键 employee.id）',
  `type`           TINYINT      NOT NULL COMMENT '类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果',
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

-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `notification`;
-- ----------------------------------------------------------------
