-- 测试流图乐观版本：写 graph_json 时条件更新，避免静默覆盖

ALTER TABLE `test_flow`
    ADD COLUMN `graph_revision` bigint NOT NULL DEFAULT 0 COMMENT '图版本号' AFTER `graph_json`;
