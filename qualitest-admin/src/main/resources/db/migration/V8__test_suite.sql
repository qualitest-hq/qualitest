-- 测试套件定义与批量执行历史

CREATE TABLE IF NOT EXISTS `test_suite`
(
    `test_suite_id`     bigint                                                        NOT NULL COMMENT '套件ID',
    `test_project_id`   bigint                                                        NOT NULL COMMENT '测试项目ID',
    `suite_name`        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '套件名称',
    `suite_description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '套件说明',
    `stop_on_non_pass`  tinyint                                                       NOT NULL DEFAULT 1 COMMENT '非通过即停（1是 0否）',
    `del_status`        tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`       datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`       datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_suite_id`) USING BTREE,
    KEY `idx_ts_project_del` (`test_project_id`, `del_status`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试套件'
  ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `test_suite_item`
(
    `test_suite_item_id` bigint                                                       NOT NULL COMMENT '套件项ID',
    `test_suite_id`      bigint                                                       NOT NULL COMMENT '套件ID',
    `test_flow_id`       bigint                                                       NOT NULL COMMENT '测试流ID',
    `run_scenario_id`    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '场景ID（空=默认场景）',
    `sort_num`           int                                                          NOT NULL DEFAULT 0 COMMENT '排序',
    `del_status`         tinyint                                                      NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`        datetime                                                     NOT NULL COMMENT '创建时间',
    `update_time`        datetime                                                     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_suite_item_id`) USING BTREE,
    KEY `idx_tsi_suite_del` (`test_suite_id`, `del_status`),
    KEY `idx_tsi_flow` (`test_flow_id`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试套件项'
  ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `test_suite_run`
(
    `test_suite_run_id`   bigint                                                        NOT NULL COMMENT '套件运行ID',
    `test_project_id`     bigint                                                        NOT NULL COMMENT '测试项目ID',
    `test_suite_id`       bigint                                                        NULL     DEFAULT NULL COMMENT '套件ID（空=临时多选）',
    `suite_name_snapshot` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '套件名称快照',
    `status`              varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '状态 running/passed/failed/aborted',
    `stop_on_non_pass`    tinyint                                                       NOT NULL DEFAULT 1 COMMENT '非通过即停',
    `test_project_env_id` bigint                                                        NULL     DEFAULT NULL COMMENT '统一环境覆盖',
    `trigger_type`        varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT 'suite' COMMENT '触发方式',
    `passed_count`        int                                                           NOT NULL DEFAULT 0 COMMENT '通过数',
    `failed_count`        int                                                           NOT NULL DEFAULT 0 COMMENT '失败数',
    `paused_count`        int                                                           NOT NULL DEFAULT 0 COMMENT '暂停数',
    `skipped_count`       int                                                           NOT NULL DEFAULT 0 COMMENT '跳过数',
    `other_count`         int                                                           NOT NULL DEFAULT 0 COMMENT '其他数',
    `started_at`          datetime                                                      NOT NULL COMMENT '开始时间',
    `finished_at`         datetime                                                      NULL     DEFAULT NULL COMMENT '结束时间',
    `operator_user_id`    bigint                                                        NULL     DEFAULT NULL COMMENT '操作者',
    `del_status`          tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`         datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`         datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_suite_run_id`) USING BTREE,
    KEY `idx_tsr_project_started` (`test_project_id`, `started_at`),
    KEY `idx_tsr_suite` (`test_suite_id`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试套件运行'
  ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `test_suite_run_item`
(
    `test_suite_run_item_id` bigint                                                        NOT NULL COMMENT '套件运行项ID',
    `test_suite_run_id`      bigint                                                        NOT NULL COMMENT '套件运行ID',
    `sort_num`               int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `test_flow_id`           bigint                                                        NOT NULL COMMENT '测试流ID',
    `flow_name_snapshot`     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流名称快照',
    `run_scenario_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '场景ID',
    `scenario_name_snapshot` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '场景名称快照',
    `test_flow_run_id`       bigint                                                        NULL     DEFAULT NULL COMMENT '关联正式 Run',
    `status`                 varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '项状态',
    `error_message`          text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci         NULL COMMENT '错误信息',
    `duration_ms`            bigint                                                        NULL     DEFAULT NULL COMMENT '耗时',
    `del_status`             tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`            datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`            datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_suite_run_item_id`) USING BTREE,
    KEY `idx_tsri_run_sort` (`test_suite_run_id`, `sort_num`),
    KEY `idx_tsri_flow_run` (`test_flow_run_id`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试套件运行项'
  ROW_FORMAT = Dynamic;
