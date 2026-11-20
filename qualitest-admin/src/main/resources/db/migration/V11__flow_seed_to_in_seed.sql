-- 一次性：场景 flowSeed 合并进 inSeed 后删除（同键保留 inSeed）。
-- 按 JSON_LENGTH 遍历全部 scenarios / template_flows，不写死下标。
-- 覆盖 test_flow.graph_json、test_flow_run.graph_json_snapshot、test_project_template.template_flows。
-- 运行时代码已删除 flowSeed，无兼容回落。

DROP PROCEDURE IF EXISTS qt_v11_migrate_flow_seed_to_in_seed;

DELIMITER $$

CREATE PROCEDURE qt_v11_migrate_flow_seed_to_in_seed()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_id BIGINT;
    DECLARE v_json JSON;
    DECLARE v_tf JSON;
    DECLARE v_graph JSON;
    DECLARE v_i INT;
    DECLARE v_n INT;
    DECLARE v_j INT;
    DECLARE v_m INT;
    DECLARE v_flow JSON;
    DECLARE v_in JSON;
    DECLARE v_merged JSON;

    -- ---------- test_flow.graph_json ----------
    BEGIN
        DECLARE c_flow CURSOR FOR
            SELECT test_flow_id, graph_json
            FROM test_flow
            WHERE graph_json IS NOT NULL
              AND CAST(graph_json AS CHAR CHARACTER SET utf8mb4) LIKE '%flowSeed%';
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

        OPEN c_flow;
        flow_loop: LOOP
            FETCH c_flow INTO v_id, v_json;
            IF v_done = 1 THEN
                LEAVE flow_loop;
            END IF;

            SET v_n = IFNULL(JSON_LENGTH(JSON_EXTRACT(v_json, '$.meta.scenarios')), 0);
            SET v_i = 0;
            WHILE v_i < v_n DO
                SET v_flow = JSON_EXTRACT(v_json, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                SET v_in = JSON_EXTRACT(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'));
                IF v_flow IS NOT NULL THEN
                    SET v_merged = JSON_MERGE_PATCH(
                        IFNULL(v_flow, CAST('{}' AS JSON)),
                        IFNULL(v_in, CAST('{}' AS JSON))
                    );
                    SET v_json = JSON_SET(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), v_merged);
                    SET v_json = JSON_REMOVE(v_json, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                ELSEIF v_in IS NULL THEN
                    SET v_json = JSON_SET(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), CAST('{}' AS JSON));
                END IF;
                SET v_i = v_i + 1;
            END WHILE;

            UPDATE test_flow SET graph_json = v_json WHERE test_flow_id = v_id;
        END LOOP;
        CLOSE c_flow;
    END;

    SET v_done = 0;

    -- ---------- test_flow_run.graph_json_snapshot ----------
    BEGIN
        DECLARE c_run CURSOR FOR
            SELECT test_flow_run_id, graph_json_snapshot
            FROM test_flow_run
            WHERE graph_json_snapshot IS NOT NULL
              AND CAST(graph_json_snapshot AS CHAR CHARACTER SET utf8mb4) LIKE '%flowSeed%';
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

        OPEN c_run;
        run_loop: LOOP
            FETCH c_run INTO v_id, v_json;
            IF v_done = 1 THEN
                LEAVE run_loop;
            END IF;

            SET v_n = IFNULL(JSON_LENGTH(JSON_EXTRACT(v_json, '$.meta.scenarios')), 0);
            SET v_i = 0;
            WHILE v_i < v_n DO
                SET v_flow = JSON_EXTRACT(v_json, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                SET v_in = JSON_EXTRACT(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'));
                IF v_flow IS NOT NULL THEN
                    SET v_merged = JSON_MERGE_PATCH(
                        IFNULL(v_flow, CAST('{}' AS JSON)),
                        IFNULL(v_in, CAST('{}' AS JSON))
                    );
                    SET v_json = JSON_SET(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), v_merged);
                    SET v_json = JSON_REMOVE(v_json, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                ELSEIF v_in IS NULL THEN
                    SET v_json = JSON_SET(v_json, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), CAST('{}' AS JSON));
                END IF;
                SET v_i = v_i + 1;
            END WHILE;

            UPDATE test_flow_run SET graph_json_snapshot = v_json WHERE test_flow_run_id = v_id;
        END LOOP;
        CLOSE c_run;
    END;

    SET v_done = 0;

    -- ---------- test_project_template.template_flows[*].graphJson ----------
    BEGIN
        DECLARE c_tpl CURSOR FOR
            SELECT test_project_template_id, template_flows
            FROM test_project_template
            WHERE template_flows IS NOT NULL
              AND CAST(template_flows AS CHAR CHARACTER SET utf8mb4) LIKE '%flowSeed%';
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

        OPEN c_tpl;
        tpl_loop: LOOP
            FETCH c_tpl INTO v_id, v_tf;
            IF v_done = 1 THEN
                LEAVE tpl_loop;
            END IF;

            SET v_m = IFNULL(JSON_LENGTH(v_tf), 0);
            SET v_j = 0;
            WHILE v_j < v_m DO
                SET v_graph = JSON_EXTRACT(v_tf, CONCAT('$[', v_j, '].graphJson'));
                IF v_graph IS NOT NULL
                    AND CAST(v_graph AS CHAR CHARACTER SET utf8mb4) LIKE '%flowSeed%' THEN
                    SET v_n = IFNULL(JSON_LENGTH(JSON_EXTRACT(v_graph, '$.meta.scenarios')), 0);
                    SET v_i = 0;
                    WHILE v_i < v_n DO
                        SET v_flow = JSON_EXTRACT(v_graph, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                        SET v_in = JSON_EXTRACT(v_graph, CONCAT('$.meta.scenarios[', v_i, '].inSeed'));
                        IF v_flow IS NOT NULL THEN
                            SET v_merged = JSON_MERGE_PATCH(
                                IFNULL(v_flow, CAST('{}' AS JSON)),
                                IFNULL(v_in, CAST('{}' AS JSON))
                            );
                            SET v_graph = JSON_SET(v_graph, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), v_merged);
                            SET v_graph = JSON_REMOVE(v_graph, CONCAT('$.meta.scenarios[', v_i, '].flowSeed'));
                        ELSEIF v_in IS NULL THEN
                            SET v_graph = JSON_SET(v_graph, CONCAT('$.meta.scenarios[', v_i, '].inSeed'), CAST('{}' AS JSON));
                        END IF;
                        SET v_i = v_i + 1;
                    END WHILE;
                    SET v_tf = JSON_SET(v_tf, CONCAT('$[', v_j, '].graphJson'), v_graph);
                END IF;
                SET v_j = v_j + 1;
            END WHILE;

            UPDATE test_project_template SET template_flows = v_tf WHERE test_project_template_id = v_id;
        END LOOP;
        CLOSE c_tpl;
    END;
END $$

DELIMITER ;

CALL qt_v11_migrate_flow_seed_to_in_seed();

DROP PROCEDURE IF EXISTS qt_v11_migrate_flow_seed_to_in_seed;
