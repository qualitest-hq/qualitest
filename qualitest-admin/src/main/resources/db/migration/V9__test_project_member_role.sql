-- =============================================================================
-- V9：测试项目成员角色与细粒度权限
-- 1. 新建全站项目成员角色表、角色权限表
-- 2. 成员表 member_role 扩为 varchar，并把历史值 developer 改为 backend
-- 3. 写入 owner/admin/backend/frontend/tester 五类默认角色及各自权限编码
-- 4. 追加「项目角色」管理菜单；管理员角色绑定全部按钮，演示角色仅绑定查询
-- =============================================================================

-- 全站成员角色定义（member_role 存 role_key）
CREATE TABLE IF NOT EXISTS `test_project_member_role`
(
    `test_project_member_role_id` bigint                                                        NOT NULL COMMENT '项目成员角色ID',
    `role_key`                    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '角色标识（member_role 存此值）',
    `role_name`                   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '角色名称',
    `sort_num`                    int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `enable_status`               tinyint                                                       NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
    `remark`                      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '备注',
    `del_status`                  tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`                 datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`                 datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_project_member_role_id`) USING BTREE,
    UNIQUE KEY `uk_tpmr_role_key` (`role_key`),
    KEY `idx_tpmr_enable_del_sort` (`enable_status`, `del_status`, `sort_num`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目成员角色'
  ROW_FORMAT = Dynamic;

-- 角色与权限多对多（perm_code 为业务权限编码）
CREATE TABLE IF NOT EXISTS `test_project_member_role_perm`
(
    `test_project_member_role_perm_id` bigint                                                       NOT NULL COMMENT '角色权限ID',
    `test_project_member_role_id`      bigint                                                       NOT NULL COMMENT '项目成员角色ID',
    `perm_code`                        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
    `create_time`                      datetime                                                     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`test_project_member_role_perm_id`) USING BTREE,
    UNIQUE KEY `uk_tpmrp_role_perm` (`test_project_member_role_id`, `perm_code`),
    KEY `idx_tpmrp_perm` (`perm_code`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目成员角色权限'
  ROW_FORMAT = Dynamic;

-- 历史成员角色 developer 统一为 backend
UPDATE `test_project_member`
SET `member_role` = 'backend'
WHERE `member_role` = 'developer';

-- 成员角色列扩长并注释指向 role_key
ALTER TABLE `test_project_member`
    MODIFY COLUMN `member_role` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'backend'
        COMMENT '成员角色（对应 test_project_member_role.role_key）';

-- 种子角色行（owner/admin/backend/frontend/tester）
INSERT IGNORE INTO `test_project_member_role`
(`test_project_member_role_id`, `role_key`, `role_name`, `sort_num`, `enable_status`, `remark`,
 `del_status`, `create_time`, `update_time`)
VALUES
(1, 'owner', '所有者', 1, 1, '默认种子', 0, NOW(), NULL),
(2, 'admin', '管理员', 2, 1, '默认种子', 0, NOW(), NULL),
(3, 'backend', '后端开发者', 3, 1, '默认种子；原 developer', 0, NOW(), NULL),
(4, 'frontend', '前端开发者', 4, 1, '默认种子', 0, NOW(), NULL),
(5, 'tester', '测试员', 5, 1, '默认种子', 0, NOW(), NULL);

-- owner：默认挂载全部权限
INSERT IGNORE INTO `test_project_member_role_perm` (`test_project_member_role_perm_id`, `test_project_member_role_id`, `perm_code`, `create_time`)
VALUES
(102, 1, 'project:edit', NOW()), (103, 1, 'project:delete', NOW()), (104, 1, 'project:transfer', NOW()),
(105, 1, 'member:view', NOW()), (106, 1, 'member:add', NOW()), (107, 1, 'member:edit', NOW()), (108, 1, 'member:remove', NOW()),
(109, 1, 'api:view', NOW()), (110, 1, 'api:add', NOW()), (111, 1, 'api:edit', NOW()), (112, 1, 'api:remove', NOW()),
(115, 1, 'flow:view', NOW()), (116, 1, 'flow:add', NOW()), (117, 1, 'flow:edit', NOW()), (118, 1, 'flow:remove', NOW()),
(120, 1, 'flow:run', NOW()), (124, 1, 'flow:external', NOW()),
(125, 1, 'suite:view', NOW()), (126, 1, 'suite:add', NOW()), (127, 1, 'suite:edit', NOW()), (128, 1, 'suite:remove', NOW()), (129, 1, 'suite:run', NOW()),
(131, 1, 'env:view', NOW()), (132, 1, 'env:edit', NOW()), (133, 1, 'asset:view', NOW()), (134, 1, 'asset:edit', NOW()),
(135, 1, 'ai:api:design', NOW()), (136, 1, 'ai:flow:design', NOW()), (137, 1, 'ai:flow:write', NOW()),
(138, 1, 'token:use', NOW()), (139, 1, 'plugin:api:import', NOW()),
(140, 1, 'mcp:read', NOW()), (141, 1, 'mcp:flow:write', NOW()), (142, 1, 'mcp:api:import', NOW()), (145, 1, 'mcp:run', NOW());

-- admin：除删除/转让项目外与 owner 同档
INSERT IGNORE INTO `test_project_member_role_perm` (`test_project_member_role_perm_id`, `test_project_member_role_id`, `perm_code`, `create_time`)
VALUES
(202, 2, 'project:edit', NOW()),
(205, 2, 'member:view', NOW()), (206, 2, 'member:add', NOW()), (207, 2, 'member:edit', NOW()), (208, 2, 'member:remove', NOW()),
(209, 2, 'api:view', NOW()), (210, 2, 'api:add', NOW()), (211, 2, 'api:edit', NOW()), (212, 2, 'api:remove', NOW()),
(215, 2, 'flow:view', NOW()), (216, 2, 'flow:add', NOW()), (217, 2, 'flow:edit', NOW()), (218, 2, 'flow:remove', NOW()),
(220, 2, 'flow:run', NOW()), (224, 2, 'flow:external', NOW()),
(225, 2, 'suite:view', NOW()), (226, 2, 'suite:add', NOW()), (227, 2, 'suite:edit', NOW()), (228, 2, 'suite:remove', NOW()), (229, 2, 'suite:run', NOW()),
(231, 2, 'env:view', NOW()), (232, 2, 'env:edit', NOW()), (233, 2, 'asset:view', NOW()), (234, 2, 'asset:edit', NOW()),
(235, 2, 'ai:api:design', NOW()), (236, 2, 'ai:flow:design', NOW()), (237, 2, 'ai:flow:write', NOW()),
(238, 2, 'token:use', NOW()), (239, 2, 'plugin:api:import', NOW()),
(240, 2, 'mcp:read', NOW()), (241, 2, 'mcp:flow:write', NOW()), (242, 2, 'mcp:api:import', NOW()), (245, 2, 'mcp:run', NOW());

-- backend：研发写权限，不含项目设置写与成员管理
INSERT IGNORE INTO `test_project_member_role_perm` (`test_project_member_role_perm_id`, `test_project_member_role_id`, `perm_code`, `create_time`)
VALUES
(309, 3, 'api:view', NOW()), (310, 3, 'api:add', NOW()), (311, 3, 'api:edit', NOW()), (312, 3, 'api:remove', NOW()),
(315, 3, 'flow:view', NOW()), (316, 3, 'flow:add', NOW()), (317, 3, 'flow:edit', NOW()), (318, 3, 'flow:remove', NOW()),
(320, 3, 'flow:run', NOW()), (324, 3, 'flow:external', NOW()),
(325, 3, 'suite:view', NOW()), (326, 3, 'suite:add', NOW()), (327, 3, 'suite:edit', NOW()), (328, 3, 'suite:remove', NOW()), (329, 3, 'suite:run', NOW()),
(331, 3, 'env:view', NOW()), (332, 3, 'env:edit', NOW()), (333, 3, 'asset:view', NOW()), (334, 3, 'asset:edit', NOW()),
(335, 3, 'ai:api:design', NOW()), (336, 3, 'ai:flow:design', NOW()), (337, 3, 'ai:flow:write', NOW()),
(338, 3, 'token:use', NOW()), (339, 3, 'plugin:api:import', NOW()),
(340, 3, 'mcp:read', NOW()), (341, 3, 'mcp:flow:write', NOW()), (342, 3, 'mcp:api:import', NOW()), (345, 3, 'mcp:run', NOW());

-- frontend：查看与执行为主，含 token 与 MCP 只读
INSERT IGNORE INTO `test_project_member_role_perm` (`test_project_member_role_perm_id`, `test_project_member_role_id`, `perm_code`, `create_time`)
VALUES
(409, 4, 'api:view', NOW()),
(415, 4, 'flow:view', NOW()), (420, 4, 'flow:run', NOW()),
(425, 4, 'suite:view', NOW()), (429, 4, 'suite:run', NOW()),
(431, 4, 'env:view', NOW()), (433, 4, 'asset:view', NOW()),
(438, 4, 'token:use', NOW()), (440, 4, 'mcp:read', NOW());

-- tester：同 frontend 的 Web 执行能力，不含 token/MCP
INSERT IGNORE INTO `test_project_member_role_perm` (`test_project_member_role_perm_id`, `test_project_member_role_id`, `perm_code`, `create_time`)
VALUES
(509, 5, 'api:view', NOW()),
(515, 5, 'flow:view', NOW()), (520, 5, 'flow:run', NOW()),
(525, 5, 'suite:view', NOW()), (529, 5, 'suite:run', NOW()),
(531, 5, 'env:view', NOW()), (533, 5, 'asset:view', NOW());

-- 测试管理：项目角色菜单（挂测试管理目录 parent=2000）
INSERT IGNORE INTO `sys_menu` VALUES (2100, '项目角色', 2000, 3, 'testProjectMemberRole', 'project/testProjectMemberRole/index', NULL, '', 1, 0, 'C', '0', '0', 'project:testProjectMemberRole:list', 'peoples', 'admin', NOW(), '', NULL, '全站项目角色与权限');
INSERT IGNORE INTO `sys_menu` VALUES (2101, '项目角色查询', 2100, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMemberRole:query', '#', 'admin', NOW(), '', NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2102, '项目角色新增', 2100, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMemberRole:add', '#', 'admin', NOW(), '', NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2103, '项目角色修改', 2100, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMemberRole:edit', '#', 'admin', NOW(), '', NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2104, '项目角色删除', 2100, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMemberRole:remove', '#', 'admin', NOW(), '', NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2105, '项目角色导出', 2100, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMemberRole:export', '#', 'admin', NOW(), '', NULL, '');

INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2100);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2101);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2102);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2103);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2104);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 2105);
-- 演示访客仅可查看项目角色列表/详情，无写权限
INSERT IGNORE INTO `sys_role_menu` VALUES (2, 2100);
INSERT IGNORE INTO `sys_role_menu` VALUES (2, 2101);
