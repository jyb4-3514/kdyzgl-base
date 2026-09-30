-- ----------------------------------------------------------------
-- Flyway 迁移：V23__operation_audit_log.sql（操作审计留痕 · 安全必做项 M-1 / REG-01）
-- 权威设计：boss-management-architecture.md §3（审计留痕设计，ARCH-S-2）、§3.2（表设计顶层）、§3.4（口令脱敏硬约束）
-- 评审依据：tech-review-boss-management.md 核对 5（审计表设计合理性）、必改项 M-7（时间列命名与既有留痕口径对齐）
-- 体例基准：payroll_log（V20/V21，追加型操作留痕）、station_payroll_setting_log（V21，10 列追加型）、leave_log（V9）
-- 批次：B1（账号角色放开 + 审计留痕）。前置：P0.6 技术评审「通过 / 有条件通过」+ 安全复验（M-1）+ C 档三步授权 + 备份。
--
-- 为什么这样设计：
--   1. 覆盖范围（M-1 / REG-01）：employee / station 的**新增 / 编辑 / 启停 / 删除 / 重置口令**共 9 个写入点当前零留痕。
--      本表为通用追加型审计表，用 target_type 区分对象、action 区分动作，可回答「**谁在何时把什么从 X 改成 Y**」。
--   2. 追加型（不可变）：**无 update_time / is_deleted**，应用层禁止 UPDATE / DELETE（对齐 payroll_log / leave_log，
--      db.md §8.0(4) 例外约定）。
--   3. 时间列命名（评审 M-7 核对结论）：既有留痕表业务时间列**统一为 `time`**——`leave_log.time`（db.md §7.2）、
--      `payroll_log.time`（§8.6.6）、`station_payroll_setting_log.time`（§8.6.8）。故本表时间列定名 **`time`**（非 create_time），
--      与既有留痕口径一致，不新造第三套命名。
--   4. 口令安全（§3.4 硬约束）：**任何字段都不得落明文或 BCrypt 散列**。`before` / `after` / `changed_fields` 一律
--      **白名单化**；涉及口令仅允许记录**布尔标记**，口径 `{"password":"SET"}`（新增设置）/ `{"password":"RESET"}`（重置），
--      绝不写口令值本身。见下方列 COMMENT 与 db.md 同步说明。验收：全表检索无口令明文/散列。
--   5. 索引按两条主路径 + 动作审计：
--        · 按目标对象查历史 → idx_operation_audit_target (target_type, target_id, time)
--        · 按操作人/时间审计 → idx_operation_audit_operator (operator_id, time)
--        · 按动作类型/时段   → idx_operation_audit_action_time (action, time)
--   6. 只 DDL、无 DML：不插种子、不写真实数据。
--
-- 【执行前置】迁移属 C 档（结构变更），须主智能体三步授权后由运维执行；执行前先备份库。
--   本表为**新表**，无存量、无回填、无预检要求。
-- ----------------------------------------------------------------

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

-- 说明：
--   1. 列尾无 update_time / is_deleted（不可变数据，对齐 db.md §8.0(4)）。
--   2. 不建物理外键（决策 D6）；target_id / operator_id 为逻辑外键，按 target_type 指向 employee / station。
--   3. 写入时机（AOP 或 Service 显式，后端定稿）与业务写事务一致性口径（同事务 / 失败可补偿）由后端定稿（架构 T9）。
--   4. `changed_fields` / `before` / `after` 的键名白名单由后端裁剪（服务端脱敏，对齐 api.md I-7 口径），非 DB 约束。
-- ----------------------------------------------------------------
-- 回滚（人工执行；不使用 Flyway undo，社区版不支持）：
--   DROP TABLE IF EXISTS `operation_audit_log`;
-- 说明：本表为纯新增表，回滚即删表；索引内联随表删。若已产生审计数据，回滚前须确认留痕已另有归档。
-- ----------------------------------------------------------------
--
-- 【待确认项】
--   · 审计范围是否扩展至更多敏感写操作（部门 / 角色 / 薪资规则等）：架构 A-⑥ 待主智能体裁定；本表本期按 M-1 最小集落表，
--     后续**只增 target_type / action 枚举**，不改表结构（TODO(扩展)）。
--   · 归档 / 保留策略：本期不做（写频低、行数可控），登记二期评估（架构 §3.5 / T6）。
-- ----------------------------------------------------------------
