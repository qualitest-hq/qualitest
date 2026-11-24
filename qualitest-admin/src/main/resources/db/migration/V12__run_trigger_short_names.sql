-- 将运行记录 trigger_type 统一为短名：ui（画布）、mcp（MCP）、ci（CI）、suite（套件）、http（开放 HTTP）、subflow（子流）
-- 旧值换算：manual→ui、ai→mcp、schedule→ci；列默认值改为 ui
UPDATE `test_flow_run`
SET `trigger_type` = CASE `trigger_type`
                         WHEN 'manual' THEN 'ui'
                         WHEN 'ai' THEN 'mcp'
                         WHEN 'schedule' THEN 'ci'
                         ELSE `trigger_type`
    END
WHERE `trigger_type` IN ('manual', 'ai', 'schedule');

ALTER TABLE `test_flow_run`
    MODIFY COLUMN `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ui'
        COMMENT '触发方式短名：ui / mcp / ci / suite / http / subflow';
