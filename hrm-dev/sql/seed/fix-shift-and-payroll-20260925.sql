-- ==================================================================
-- 快递驿站智汇系统 · 数据修正脚本（班次制算薪口径 + 两处种子错配）
-- 目标库：kdyzgl_test（测试库）
--   ★★★ 仅对 kdyzgl_test 执行；严禁对 kdyzgl / courier_station 执行 ★★★
--   （courier_station 为现网系统，kdyzgl 为结构库；本脚本末段 §E 附目标库断言）
--
-- 依据：hrm-dev/docs/algo-payroll-shift.md v2.0
--   §1 口径 B5（删中班）/ T3（实发不低于 0）
--   §7.1 罚款封顶优先级链（级1 规则项 cap → 级2 配置 capRatio → 级3 allowNegativeNet）
--   §6 数据侧处置（删 8 条中班、ABSENT_FINE 150→100，均为数据变更非 DDL）
--
-- 性质：纯数据修正。全程无 DDL、无 FLYWAY 迁移脚本改动（V1–V15 冻结不动）。
-- 幂等：全部修正语句可重复执行且结果一致（见各节「幂等」说明）。
-- 前置：执行前务必先跑各节「只读核对」并把结果回填主智能体；本机无 MySQL，
--       本脚本未经实跑，静态自检结论「收敛到服务器阶段」。
-- 执行：属 C 档动作，须主智能体三步授权后由授权角色在服务器执行（MCP 主智能体独占）。
-- 回滚：见 §E（推荐执行前整表备份恢复；并提供反向 UPDATE）。
-- ==================================================================

SET NAMES utf8mb4;

-- 目标库断言：非 kdyzgl_test 时所有写语句被 WHERE @ok = 1 挡住（防误伤）
SET @ok := IF(DATABASE() = 'kdyzgl_test', 1, 0);

-- 引用关系全景（只读）：确认 shift_id / rule_id / leave_id 这三个「逻辑外键」列只在预期表出现，
-- 排除遗漏的引用点（本项目无物理外键，D6 逻辑外键，须靠此查证）。
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('shift_id', 'rule_id', 'leave_id')
ORDER BY COLUMN_NAME, TABLE_NAME;


-- ==================================================================
-- A. 班次主数据：删除「中班」（8 驿站各 1 条，共 8 条）
--    为什么先核对引用：attendance_schedule.shift_id 为逻辑外键且无 DB 级约束，
--    直接删主数据会留下悬空引用（前端班次管理页对「被排班引用」也会拦删）。
-- ==================================================================

-- A0 · 只读核对：中班清单（应 8 条）+ 被排班引用情况（可能 0~N 行）
SELECT s.id AS shift_id, s.station_id, s.shift_name, s.start_time, s.end_time, s.status, s.is_deleted
FROM attendance_shift s
WHERE s.shift_name = '中班'
ORDER BY s.station_id;

SELECT sc.shift_id, MIN(sh.station_id) AS station_id, COUNT(*) AS schedule_rows
FROM attendance_schedule sc
JOIN attendance_shift sh ON sh.id = sc.shift_id
WHERE sh.shift_name = '中班'
GROUP BY sc.shift_id
ORDER BY sc.shift_id;

-- A0b · 只读核对：删中班后不得残留 12:00 起始班次（方案 §7 middayBoundaryMinute=720 冲突校验，建议项 S6）
SELECT s.id, s.station_id, s.shift_name, s.start_time
FROM attendance_shift s
WHERE s.start_time = '12:00'
ORDER BY s.station_id, s.id;

-- A1 · 修正：把引用中班的排班行「改派到本驿站的晚班」（不改行数、不动员工与日期）
--   为什么改到晚班：方案 §7 界值 middayBoundaryMinute=720，start_time 分钟数 >= 720 归晚班序位；
--   中班 start_time=12:00 恰为 720 → 落晚班一侧。改派后站内不再出现 12:00 起始班次（满足方案假设 A1）。
--   备选（若某站缺晚班）：改派到早班（把 e.shift_name 改为 '早班'）；或删除这些排班行（数据损失，不推荐）。
--   幂等：执行后无行再引用中班 → 重跑命中 0 行。
UPDATE attendance_schedule sc
JOIN attendance_shift m ON m.id = sc.shift_id AND m.shift_name = '中班'
JOIN attendance_shift e ON e.station_id = m.station_id AND e.shift_name = '晚班' AND e.is_deleted = 0
SET sc.shift_id = e.id
WHERE @ok = 1;

-- A2 · 删除中班主数据（在 A1 之后执行，此时已无排班引用）
--   幂等：重跑无 '中班' 行 → 命中 0 行。
DELETE FROM attendance_shift
WHERE @ok = 1 AND shift_name = '中班';

-- A3 · 校验（期望 remaining_zh = 0、dangling_shift_refs = 0）
SELECT
  (SELECT COUNT(*) FROM attendance_shift WHERE shift_name = '中班') AS remaining_zh,
  (SELECT COUNT(*) FROM attendance_schedule sc
     LEFT JOIN attendance_shift sh ON sh.id = sc.shift_id
    WHERE sh.id IS NULL) AS dangling_shift_refs;


-- ==================================================================
-- B. 罚款单价：ABSENT_FINE 由 150/天 改为 100/班次
--    目标：payroll_rule_item.params.amount 150 → 100；mode 保持 PER_COUNT；cap 保持 0。
--    cap 说明（对齐方案 §7.1 封顶链）：
--      级1 规则项 cap = 0 → 按 itemCapSemantics（默认 ZERO_MEANS_NO_CAP）解读为「本级不封顶」，
--           故 cap 无需修改；级2 absentFineCapRatio（配置默认 0=关闭）、级3 allowNegativeNet=false
--           （合计层，实发不低于 0）均不入库，无需在本次数据修正中体现。
--    覆盖范围：更新全部未删除的 ABSENT_FINE 项（本库仅 1 条，挂在「实际生效的规则」上，
--           与 C 节执行顺序无关），确保「罚的是生效规则」。
-- ==================================================================

-- B0 · 只读核对：ABSENT_FINE 现状（含所属规则与启用状态、amount/mode/cap）
SELECT ri.id, ri.rule_id, pr.rule_name, pr.status AS rule_status,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.metric')) AS metric,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.mode'))   AS mode,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.amount')) AS amount,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.cap'))    AS cap
FROM payroll_rule_item ri
JOIN payroll_rule pr ON pr.id = ri.rule_id
WHERE ri.item_key = 'ABSENT_FINE'
ORDER BY ri.rule_id;

-- B1 · 修正：amount 150 → 100（仅改该键，保留 metric/mode/cap 其余字段）
--   幂等：已为 100 的行被 <> 100 过滤掉 → 重跑命中 0 行。
UPDATE payroll_rule_item ri
SET ri.params = JSON_SET(ri.params, '$.amount', 100)
WHERE @ok = 1
  AND ri.item_key = 'ABSENT_FINE'
  AND ri.is_deleted = 0
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.amount')) AS DECIMAL(12,2)) <> 100;

-- B2 · 校验（期望 amount = 100、mode = PER_COUNT、cap = 0）
SELECT ri.rule_id, pr.rule_name, pr.status,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.amount')) AS amount,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.mode'))   AS mode,
       JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.cap'))    AS cap
FROM payroll_rule_item ri
JOIN payroll_rule pr ON pr.id = ri.rule_id
WHERE ri.item_key = 'ABSENT_FINE';


-- ==================================================================
-- C. 错配 1：payroll_rule_item.rule_id 与 payroll_rule.id 不匹配
--    现状（服务器实测）：payroll_rule.id = 3（标准计薪规则，status=1）、4（旧版，status=0）；
--                        payroll_rule_item 的 8 项挂 rule_id=1、1 项挂 rule_id=2。
--    → 启用规则 3 无任何计薪项，按规则 3 算薪会得「无计薪项」。
--    处置：把 payroll_rule.id 重排为 1/2（对齐既有 rule_id=1/2、payroll.rule_id=1、快照 ruleId=1）。
--    为什么重排规则 id 而非搬运规则项（取舍见文末说明）：
--      ① 三处引用（item.rule_id=1/2、payroll.rule_id=1、rule_snapshot.ruleId=1）本就指向 1；
--         重排规则 id 使「历史引用点」无需改动即全部复原 —— 不触碰任何历史单据与快照；
--      ② 若改搬规则项到 3/4，则 payroll.rule_id=1 仍悬空，除非改写历史单据——违反「历史不突变」。
--   注意：本节按任务实测的 id（3/4）编写；若 C0 核对结果不同，请据实调整常量后重评。
-- ==================================================================

-- C0 · 只读核对
-- C0-1 payroll_rule 全量（含停用、含逻辑删除），确认 1/2 是否空闲
SELECT id, rule_name, status, is_deleted FROM payroll_rule ORDER BY id;

-- C0-2 规则项按 rule_id 分组（确认 1:8 项、2:1 项）
SELECT rule_id, COUNT(*) AS item_rows, SUM(enabled) AS enabled_rows
FROM payroll_rule_item
WHERE is_deleted = 0
GROUP BY rule_id ORDER BY rule_id;

-- C0-3 历史工资单引用的 rule_id 分布（本库期望全部 = 1）
SELECT rule_id, COUNT(*) AS payroll_rows FROM payroll GROUP BY rule_id ORDER BY rule_id;

-- C0-4 历史快照内的 ruleId 分布（历史不可变证据，期望全部 = 1）
SELECT JSON_UNQUOTE(JSON_EXTRACT(rule_snapshot, '$.ruleId')) AS snapshot_rule_id, COUNT(*) AS payroll_rows
FROM payroll
WHERE rule_snapshot IS NOT NULL
GROUP BY snapshot_rule_id ORDER BY snapshot_rule_id;

-- C1 · 修正：payroll_rule.id 3 → 1、4 → 2
--   安全闸：仅当 id 1/2 空闲（含逻辑删除）且无任何工资单引用 3/4 时才执行；
--           否则整条 UPDATE 命中 0 行（不误伤），请人工核查 C0 后再定。
--   幂等：重跑时 id 1/2 已被占用 → @c_occupied > 0 → 命中 0 行。
SET @c_occupied := (SELECT COUNT(*) FROM payroll_rule WHERE id IN (1, 2));
SET @c_ref34    := (SELECT COUNT(*) FROM payroll WHERE rule_id IN (3, 4));

UPDATE payroll_rule
SET id = CASE WHEN id = 3 THEN 1 WHEN id = 4 THEN 2 ELSE id END
WHERE @ok = 1
  AND @c_occupied = 0
  AND @c_ref34 = 0
  AND id IN (3, 4);

-- C2 · 校验（期望：启用规则下 item_count = 8；启用规则 id 与 item.rule_id 一致；无悬空 rule_id）
SELECT pr.id, pr.rule_name, pr.status,
       COUNT(ri.id) AS item_count
FROM payroll_rule pr
LEFT JOIN payroll_rule_item ri ON ri.rule_id = pr.id AND ri.is_deleted = 0
GROUP BY pr.id, pr.rule_name, pr.status
ORDER BY pr.id;

SELECT ri.rule_id, COUNT(*) AS orphan_items
FROM payroll_rule_item ri
LEFT JOIN payroll_rule pr ON pr.id = ri.rule_id
WHERE pr.id IS NULL
GROUP BY ri.rule_id;

-- C-备选方案（不推荐，仅登记取舍）：搬运规则项 id 1→3、2→4
--   UPDATE payroll_rule_item SET rule_id = 3 WHERE @ok=1 AND rule_id = 1 AND is_deleted = 0; -- 8 项
--   UPDATE payroll_rule_item SET rule_id = 4 WHERE @ok=1 AND rule_id = 2 AND is_deleted = 0; -- 1 项
--   副作用：payroll.rule_id=1 与 rule_snapshot.ruleId=1 仍悬空（历史单据/快照指向的规则不存在）。


-- ==================================================================
-- D. 错配 2：leave_log.leave_id 与 leave_request.id 不一致
--    现状：leave_log.leave_id 取值 1–12，leave_request.id 为 13–24 → 请假详情「审批日志」查不到记录。
--    处置：把日志改派到现有请假单 id（+12）。后者为已存在的真实单据，日志是「指向方」，改日志风险最低
--         （不动 leave_request 主键，回避 PK 重排与潜在软删冲突）。
-- ==================================================================

-- D0 · 只读核对
-- D0-1 两表 id 值域
SELECT MIN(leave_id) AS min_leave_id, MAX(leave_id) AS max_leave_id, COUNT(*) AS log_rows FROM leave_log;
SELECT MIN(id) AS min_request_id, MAX(id) AS max_request_id, COUNT(*) AS request_rows FROM leave_request;

-- D0-2 业务语义核对：逐条按「申请人 == SUBMIT 操作人 / 动作链顺序」验证 +12 的对应关系
--   判定：SUBMIT 行的 operator_id 应等于对应 request.employee_id；动作链应与 request.status 自洽。
--   若本查询出现大量 NULL(未命中) 或申请人错位，则 +12 不成立，须改用逐单人工映射。
SELECT l.leave_id AS old_leave_id, (l.leave_id + 12) AS expected_request_id,
       l.action, l.operator_id AS log_operator_id, l.operator_role,
       r.id AS matched_request_id, r.employee_id, r.leave_type, r.status AS request_status,
       CASE WHEN l.action = 'SUBMIT' THEN (l.operator_id = r.employee_id) ELSE NULL END AS submit_operator_ok
FROM leave_log l
LEFT JOIN leave_request r ON r.id = l.leave_id + 12
ORDER BY l.leave_id, l.time;

-- D1 · 修正：leave_log.leave_id + 12
--   安全闸：仅当「存在旧值 1–12」且「尚无 13–24 新值」（未做过迁移、无混夹）时才执行；
--           混夹（既有 1–12 又有 13–24）说明曾部分迁移，整条命中 0 行，请人工核查。
--   幂等：重跑时 @d_legacy = 0 → 命中 0 行。
SET @d_legacy := (SELECT COUNT(*) FROM leave_log WHERE leave_id BETWEEN 1 AND 12);
SET @d_fixed  := (SELECT COUNT(*) FROM leave_log WHERE leave_id BETWEEN 13 AND 24);

UPDATE leave_log
SET leave_id = leave_id + 12
WHERE @ok = 1
  AND @d_legacy > 0
  AND @d_fixed = 0
  AND leave_id BETWEEN 1 AND 12;

-- D2 · 校验（期望：orphan_logs = 0；每张请假单 log_rows >= 1）
SELECT COUNT(*) AS orphan_logs
FROM leave_log l
LEFT JOIN leave_request r ON r.id = l.leave_id
WHERE r.id IS NULL;

SELECT r.id AS request_id, COUNT(l.id) AS log_rows
FROM leave_request r
LEFT JOIN leave_log l ON l.leave_id = r.id
GROUP BY r.id ORDER BY r.id;


-- ==================================================================
-- E. 回滚
--   推荐：执行前对受影响表整表备份（幂等脚本最稳的回滚 = 备份恢复）：
--     mysqldump -h <host> -u <user> -p kdyzgl_test \
--       attendance_shift attendance_schedule payroll_rule payroll_rule_item leave_log \
--       > backup_kdyzgl_test_20260925.sql
--   （凭据由主智能体按最小必要下发，禁止写入本脚本/文档/日志）
--   —— 无备份时，可按下列反向语句回滚（各自幂等；顺序与修正相反 E-D→E-C→E-B→E-A）：
-- ==================================================================

-- E-D 回滚：日志 id 复原
--   注意：仅当修正后 leave_log 全为 13–24（无其它来源日志）时成立。
-- UPDATE leave_log SET leave_id = leave_id - 12 WHERE @ok = 1 AND leave_id BETWEEN 13 AND 24;

-- E-C 回滚：规则 id 复原
-- UPDATE payroll_rule SET id = CASE WHEN id = 1 THEN 3 WHEN id = 2 THEN 4 ELSE id END
--  WHERE @ok = 1 AND id IN (1, 2);

-- E-B 回滚：罚款单价复原为 150
-- UPDATE payroll_rule_item ri
-- SET ri.params = JSON_SET(ri.params, '$.amount', 150)
-- WHERE @ok = 1 AND ri.item_key = 'ABSENT_FINE' AND ri.is_deleted = 0;

-- E-A 回滚：中班复原（需以备份恢复排班的 shift_id 指向；下列仅恢复班次主数据）
-- INSERT INTO attendance_shift (id, station_id, shift_name, start_time, end_time, color, rest_minutes, status, is_deleted, create_time, update_time) VALUES
--   (2, 1, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (5, 2, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (8, 3, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (11, 4, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (14, 5, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (17, 6, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (20, 7, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW()),
--   (23, 8, '中班', '12:00', '20:00', '#FA8C16', 60, 1, 0, NOW(), NOW());

-- ==================================================================
-- 附：修正后一键总校验
-- ==================================================================
SELECT
  (SELECT COUNT(*) FROM attendance_shift WHERE shift_name = '中班') AS zh_remaining,
  (SELECT COUNT(*) FROM payroll_rule_item ri JOIN payroll_rule pr ON pr.id = ri.rule_id
     WHERE pr.status = 1 AND pr.is_deleted = 0 AND ri.is_deleted = 0) AS enabled_rule_items,
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(ri.params, '$.amount')) FROM payroll_rule_item ri
     WHERE ri.item_key = 'ABSENT_FINE' AND ri.is_deleted = 0 LIMIT 1) AS absent_fine_amount,
  (SELECT COUNT(*) FROM leave_log l LEFT JOIN leave_request r ON r.id = l.leave_id WHERE r.id IS NULL) AS orphan_leave_logs;
