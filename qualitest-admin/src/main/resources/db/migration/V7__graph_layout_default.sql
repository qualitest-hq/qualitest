-- 把图里的排版占位值 manual 改成 default。
-- default 表示从左往右分层并折行，只改这个字面量，不动节点坐标和连线。
-- 测试流正文、运行时保存的图快照、项目模板里的预制流都改。
-- 图是 JSON 列，转成字符串后键值之间可能带空格，无空格写法一并替换。
-- 已经是 default 或其他排版方式的行不会命中，重复执行无效果。

UPDATE test_flow
SET graph_json = CAST(
        REPLACE(
            REPLACE(
                CAST(graph_json AS CHAR CHARACTER SET utf8mb4),
                '"layout": "manual"',
                '"layout": "default"'
            ),
            '"layout":"manual"',
            '"layout":"default"'
        ) AS JSON
    )
WHERE CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout": "manual"%'
   OR CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout":"manual"%';

UPDATE test_flow_run
SET graph_json_snapshot = CAST(
        REPLACE(
            REPLACE(
                CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4),
                '"layout": "manual"',
                '"layout": "default"'
            ),
            '"layout":"manual"',
            '"layout":"default"'
        ) AS JSON
    )
WHERE CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout": "manual"%'
   OR CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout":"manual"%';

UPDATE test_project_template
SET template_flows = CAST(
        REPLACE(
            REPLACE(
                CAST(template_flows AS CHAR CHARACTER SET utf8mb4),
                '"layout": "manual"',
                '"layout": "default"'
            ),
            '"layout":"manual"',
            '"layout":"default"'
        ) AS JSON
    )
WHERE template_flows IS NOT NULL
  AND (
      CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout": "manual"%'
      OR CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"layout":"manual"%'
  );
