-- =============================================================================
-- V11：平台插件落库（新表；不改 test_project / test_project_env）
-- 1. test_plugin_package：发现/登记的说明书快照
-- 2. test_plugin_env_binding：环境启用绑定与 config
-- 3. test_plugin_project_mount：项目级挂载（如存档点 bindAlias）
-- 总闸仍用 test_project_env.allow_destructive_reset，本脚本不加列
-- =============================================================================

CREATE TABLE IF NOT EXISTS `test_plugin_package`
(
    `test_plugin_package_id` bigint                                                        NOT NULL COMMENT '插件包登记ID',
    `plugin_id`              varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '插件标识（如 qualitest.sut-db-archive-point）',
    `version`                varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '语义化版本',
    `display_name`           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名',
    `manifest_json`          json                                                          NOT NULL COMMENT '说明书快照 JSON',
    `content_hash`           varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '整包内容指纹（说明书+runtime/agent/ui 等；变更则更新登记）',
    `del_status`             tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`            datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`            datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_plugin_package_id`) USING BTREE,
    UNIQUE KEY `uk_tpp_plugin_id` (`plugin_id`),
    KEY `idx_tpp_plugin_del` (`plugin_id`, `del_status`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '平台插件包登记（每个 plugin_id 仅一份当前版本）'
  ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `test_plugin_env_binding`
(
    `test_plugin_env_binding_id` bigint                                                        NOT NULL COMMENT '环境插件绑定ID',
    `test_project_id`            bigint                                                        NOT NULL COMMENT '测试项目ID',
    `test_project_env_id`        bigint                                                        NOT NULL COMMENT '测试项目环境ID',
    `plugin_id`                  varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '插件标识（跟登记表当前版本，不锁旧版）',
    `enabled`                    tinyint                                                       NOT NULL DEFAULT 0 COMMENT '是否启用（0否 1是）',
    `config_json`                json                                                          NOT NULL COMMENT '环境插件配置 JSON',
    `del_status`                 tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`                datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`                datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_plugin_env_binding_id`) USING BTREE,
    UNIQUE KEY `uk_tpeb_env_plugin` (`test_project_env_id`, `plugin_id`),
    KEY `idx_tpeb_project_del` (`test_project_id`, `del_status`),
    KEY `idx_tpeb_env_enabled` (`test_project_env_id`, `enabled`, `del_status`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试环境插件绑定'
  ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `test_plugin_project_mount`
(
    `test_plugin_project_mount_id` bigint                                                        NOT NULL COMMENT '项目插件挂载ID',
    `test_project_id`              bigint                                                        NOT NULL COMMENT '测试项目ID',
    `plugin_id`                    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '插件标识',
    `mount_json`                   json                                                          NOT NULL COMMENT '挂载配置 JSON（如 bindAlias、storeKeys）',
    `del_status`                   tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`                  datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`                  datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_plugin_project_mount_id`) USING BTREE,
    UNIQUE KEY `uk_tppm_project_plugin` (`test_project_id`, `plugin_id`),
    KEY `idx_tppm_project_del` (`test_project_id`, `del_status`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目插件挂载'
  ROW_FORMAT = Dynamic;
