-- =============================================================================
-- 测试项目增加跑流并发模式字段
-- parallel：多条 Run 可同时执行
-- serial_by_env：同一测试环境同时只跑一条，其余排队（列默认值）
-- =============================================================================

ALTER TABLE `test_project`
    ADD COLUMN `run_concurrency_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
        NOT NULL DEFAULT 'serial_by_env' COMMENT '跑流并发：parallel 并行；serial_by_env 按环境串行（默认）'
        AFTER `mcp_import_apis_enabled`;
