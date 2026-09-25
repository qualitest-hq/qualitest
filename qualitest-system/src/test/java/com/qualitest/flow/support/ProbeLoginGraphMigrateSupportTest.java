package com.qualitest.flow.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ProbeLoginGraphMigrateSupport：旧探活骨架补 403 与 expectedMatch。
 * 边界：纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProbeLoginGraphMigrateSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProbeLoginGraphMigrateSupportTest {

    /**
     * 前提：旧骨架 whitelist [200,401] + Conditon 仅 status=200。
     * 期望：迁移后含 403 与 http.expectedMatch。
     */
    @Test
    @Order(1)
    @DisplayName("migrate：旧探活骨架补 403 与 expectedMatch")
    void migrate_oldProbeSkeleton() {
        String old = """
                {
                  "nodes": [
                    {
                      "id": "probe_http",
                      "type": "http",
                      "data": {
                        "name": "探活",
                        "statusCheck": {"mode": "whitelist", "values": [200, 401]},
                        "successCheck": {"mode": "off"}
                      }
                    },
                    {
                      "id": "cond_alive",
                      "type": "condition",
                      "data": {
                        "branches": [
                          {
                            "id": "b_alive_if",
                            "kind": "if",
                            "conditions": [{"left": "http.status", "operator": "eq", "right": "200"}]
                          },
                          {"id": "b_alive_else", "kind": "else", "conditions": []}
                        ]
                      }
                    }
                  ],
                  "edges": [{"id": "e1", "source": "probe_http", "target": "cond_alive"}]
                }
                """;
        String out = ProbeLoginGraphMigrateSupport.migrate(old);
        assertTrue(out.contains("403"));
        assertTrue(out.contains("http.expectedMatch"));
    }

    /**
     * 前提：已含 expectedMatch 与 403。
     * 期望：migrate 结果与入参等价（不重复追加）。
     */
    @Test
    @Order(2)
    @DisplayName("migrate：已迁移图不再改")
    void migrate_alreadyDone_noop() {
        String neo = """
                {
                  "nodes": [
                    {
                      "id": "probe_http",
                      "type": "http",
                      "data": {
                        "statusCheck": {"mode": "whitelist", "values": [200, 401, 403]},
                        "successCheck": {"mode": "off"}
                      }
                    },
                    {
                      "id": "cond_alive",
                      "type": "condition",
                      "data": {
                        "branches": [{
                          "id": "b_if",
                          "kind": "if",
                          "conditions": [
                            {"left": "http.status", "operator": "eq", "right": "200"},
                            {"left": "http.expectedMatch", "operator": "eq", "right": "true"}
                          ]
                        }]
                      }
                    }
                  ],
                  "edges": [{"source": "probe_http", "target": "cond_alive"}]
                }
                """;
        String out = ProbeLoginGraphMigrateSupport.migrate(neo);
        assertFalse(out.contains("http.expectedMatch\",\"operator\":\"eq\",\"right\":\"true\"},{\"left\":\"http.expectedMatch\""));
        assertTrue(out.contains("http.expectedMatch"));
    }
}
