-- 条件分支不再写 terminal：有无 target 即表示是否结束本流。
-- 只剥 if / elif 分支上的该键（紧贴 "kind":"if|elif"），避免误伤其它 JSON。
-- nodes/branches 下标不固定，MySQL 难以用 JSON_REMOVE 稳定遍历，故用幂等片段替换。

UPDATE test_project_template
SET template_flows = CAST(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        CAST(template_flows AS CHAR CHARACTER SET utf8mb4),
                        '"kind":"if","terminal":true,',
                        '"kind":"if",'
                    ),
                    '"kind":"elif","terminal":true,',
                    '"kind":"elif",'
                ),
                '"kind": "if", "terminal": true, ',
                '"kind": "if", '
            ),
            '"kind": "elif", "terminal": true, ',
            '"kind": "elif", '
        ) AS JSON
    )
WHERE CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"terminal"%'
  AND (
      CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"if","terminal"%'
      OR CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"elif","terminal"%'
      OR CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "if", "terminal"%'
      OR CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "elif", "terminal"%'
  );

UPDATE test_flow
SET graph_json = CAST(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        CAST(graph_json AS CHAR CHARACTER SET utf8mb4),
                        '"kind":"if","terminal":true,',
                        '"kind":"if",'
                    ),
                    '"kind":"elif","terminal":true,',
                    '"kind":"elif",'
                ),
                '"kind": "if", "terminal": true, ',
                '"kind": "if", '
            ),
            '"kind": "elif", "terminal": true, ',
            '"kind": "elif", '
        ) AS JSON
    )
WHERE CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"terminal"%'
  AND (
      CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"if","terminal"%'
      OR CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"elif","terminal"%'
      OR CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "if", "terminal"%'
      OR CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "elif", "terminal"%'
  );

UPDATE test_flow_run
SET graph_json_snapshot = CAST(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4),
                        '"kind":"if","terminal":true,',
                        '"kind":"if",'
                    ),
                    '"kind":"elif","terminal":true,',
                    '"kind":"elif",'
                ),
                '"kind": "if", "terminal": true, ',
                '"kind": "if", '
            ),
            '"kind": "elif", "terminal": true, ',
            '"kind": "elif", '
        ) AS JSON
    )
WHERE CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"terminal"%'
  AND (
      CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"if","terminal"%'
      OR CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind":"elif","terminal"%'
      OR CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "if", "terminal"%'
      OR CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%"kind": "elif", "terminal"%'
  );
