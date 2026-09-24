-- 测试流目录分组：可嵌套目录表 + 测试流挂组字段

CREATE TABLE IF NOT EXISTS `test_flow_group`
(
    `flow_group_id`   bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '测试流分组ID',
    `test_project_id` bigint                                                        NOT NULL COMMENT '测试项目ID',
    `parent_id`       bigint                                                        NOT NULL DEFAULT 0 COMMENT '父分组ID',
    `ancestors`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '祖级列表',
    `group_name`      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
    `sort_num`        int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `del_status`      tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`     datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`     datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`flow_group_id`) USING BTREE,
    KEY `idx_tfg_project_del` (`test_project_id`, `del_status`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试流分组'
  ROW_FORMAT = Dynamic;

ALTER TABLE `test_flow`
    ADD COLUMN `flow_group_id` bigint NULL DEFAULT NULL COMMENT '测试流分组ID' AFTER `test_project_id`,
    ADD KEY `idx_tf_flow_group_id` (`flow_group_id`);
