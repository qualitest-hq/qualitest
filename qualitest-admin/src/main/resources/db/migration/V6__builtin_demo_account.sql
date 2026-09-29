-- 内置演示账号（勿改 V1；本脚本为唯一升级入口）
-- 新库：V1 出 projectOwner/test → 本脚本收敛为 admin + demo
-- 旧库：同样幂等执行；不修改 admin 口令（公网由运维仓 DEMO_SEED 覆盖）

-- 清理运维仓旧 DEMO_SEED 残留（若存在）
DELETE FROM sys_user_role WHERE user_id = 100 OR role_id = 100;
DELETE FROM sys_role_menu WHERE role_id = 100;
DELETE FROM sys_user WHERE user_id = 100;
DELETE FROM sys_role WHERE role_id = 100;

-- 角色 role_id=2 → 演示访客
UPDATE sys_role
SET role_name   = '演示访客',
    role_key    = 'demo',
    remark      = '开箱演示受限角色（正式环境请改密或停用对应账号）',
    update_by   = 'admin',
    update_time = NOW()
WHERE role_id = 2;

UPDATE sys_role
SET del_flag = '2',
    update_by = 'admin',
    update_time = NOW()
WHERE role_id = 3;

-- 演示角色菜单整组重写：测试可测可改流 + AI；无删项目/模板写；无系统管理
DELETE FROM sys_role_menu WHERE role_id = 2;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 2000), (2, 2001), (2, 2002), (2, 2003), (2, 2004), (2, 2006),
(2, 2020), (2, 2021), (2, 2022), (2, 2023), (2, 2024), (2, 2025),
(2, 2026), (2, 2027), (2, 2062),
(2, 2030),
(2, 2031), (2, 2032), (2, 2033), (2, 2034), (2, 2035), (2, 2036),
(2, 2037), (2, 2038), (2, 2039), (2, 2040), (2, 2041), (2, 2042),
(2, 2043), (2, 2044), (2, 2045), (2, 2046), (2, 2047), (2, 2048),
(2, 2049), (2, 2050), (2, 2051), (2, 2052), (2, 2053), (2, 2054),
(2, 2055), (2, 2056), (2, 2057), (2, 2058), (2, 2059), (2, 2060);

-- user_id=2 → demo / demo123
UPDATE sys_user
SET user_name   = 'demo',
    nick_name   = '演示账号',
    email       = '',
    phonenumber = '',
    password    = '$2a$10$Gmc1UBAWeuskYjIHv8UqFuJCXWd8/LFsYqZ8HIAE5RfUoxGhbL8FK',
    status      = '0',
    del_flag    = '0',
    remark      = '开箱演示账号 demo/demo123（正式环境请改密或停用）',
    update_by   = 'admin',
    update_time = NOW()
WHERE user_id = 2;

DELETE FROM sys_user_role WHERE user_id = 2;
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);

-- 软删旧 project* 废号
DELETE FROM sys_user_role WHERE user_id IN (3, 4, 5);
UPDATE sys_user
SET status = '1',
    del_flag = '2',
    update_by = 'admin',
    update_time = NOW()
WHERE user_id IN (3, 4, 5);
