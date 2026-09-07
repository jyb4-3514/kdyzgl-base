-- ----------------------------------------------------------------
-- 快递驿站智汇系统 · 一期种子数据（MySQL 8）
-- Flyway 迁移：V2__init_data.sql（对应任务 A03）
-- 权威设计：docs/db.md 5.2（V2 种子数据）
-- 说明：
--   1. 种子仅含系统初始化结构（根部门「总公司」+ 初始管理员 admin），
--      不含任何真实业务数据；admin 手机号 13800000000 为文档约定占位号；
--   2. employee.password 为 BCrypt 散列（$2a$、cost=10、60 字符），对应
--      db.md 5.2 文档化的部署占位初始密码（明文密码不写入脚本）；
--      散列已生成并经 compareSync 回环验证（bcryptjs 2.4.3 与 3.0.3 双版本交叉验证通过）；
--   3. pwd_changed=0：首次登录强制改密（requirement.md 决策 D1 双保险，
--      部署后按 TASK.md D05 流程完成首登改密）；
--   4. create_time / update_time 由列默认值 CURRENT_TIMESTAMP 兜底填充；
--   5. MySQL 显式插入 id=1 后 AUTO_INCREMENT 计数器自动推进到 2，无需手工调整。
-- ----------------------------------------------------------------

INSERT INTO department (id, parent_id, dept_name, sort_order)
VALUES (1, 0, '总公司', 1);

INSERT INTO employee (id, username, password, real_name, phone, gender, dept_id, station_id, role, status, pwd_changed)
VALUES (1, 'admin', '$2a$10$KzIKolEcoKaIeYkyJ7jr8e1GOMrz8wHaw9pppT6a42F5TU1dMGBSu', '系统管理员', '13800000000', 0, 1, NULL, 'ADMIN', 1, 0);
