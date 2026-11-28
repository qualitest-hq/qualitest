package com.qualitest.seed;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 V1 基线里的开箱登录流：4 条预制流须是 2.0 可跑形态（场景入口 + 子流入口 + entryKind 守卫）。
 * 边界：读 classpath 的 db/migration/V1__baseline.sql 并解析转义 JSON；不连库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-admin -am -Dtest=V1SeedFlowGraphsTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V1SeedFlowGraphsTest {

    @Test
    @Order(1)
    @DisplayName("V1 开箱登录流全部通过 2.0 全量校验")
    void seedFlows_passFullValidation() {
        // 前提：V1 种子里 4 条内置登录流
        // 期望：每条都通过全量图校验（可开跑）
        List<JSONObject> graphs = seedLoginGraphs();
        GraphJsonValidator validator = new GraphJsonValidator(null);

        for (JSONObject graph : graphs) {
            GraphValidationResult result = validator.validateJson(graph.toJSONString());
            assertTrue(result.isOk(), () -> "seed graph errors=" + result.getErrors());
        }
    }

    @Test
    @Order(2)
    @DisplayName("V1 开箱登录流为双入口 + 守卫返回")
    void seedFlows_dualEntryGuardedReturn() {
        // 前提：同一张登录图既要场景可跑，也要能被父流当子流挂载
        // 期望：场景/子流入口齐全、子流入口带业务兜底、守卫按 run.entryKind 分流、返回挂 flow.out
        for (JSONObject graph : seedLoginGraphs()) {
            assertNotNull(nodeOfType(graph, "scenario-entry"));
            JSONObject subEntry = nodeOfType(graph, "subflow-entry");
            assertNotNull(subEntry);
            assertEquals("NO_RETURN",
                    subEntry.getJSONObject("data").getJSONObject("fallbackOut").getString("code"));

            JSONObject subReturn = nodeOfType(graph, "subflow-return");
            assertNotNull(subReturn);
            assertEquals("flow.out", subReturn.getJSONObject("data").getString("from"));
            assertFalse(subReturn.getJSONObject("data").containsKey("status"));

            assertTrue(hasEntryKindGuard(graph));
            assertTrue(hasEdge(graph, "login_http", "pack_out"));
            assertTrue(hasEdge(graph, "guard_entry", "n_ret"));
            assertNull(graph.getJSONObject("meta").getString("startNodeId"));
        }
    }

    /** 从 V1__baseline.sql 的 template_flows 列解析出全部内置登录图 */
    private static List<JSONObject> seedLoginGraphs() {
        String sql = loadSql();
        List<JSONObject> graphs = new ArrayList<>();
        String marker = "\\\"graphJson\\\"";
        int from = 0;
        while (true) {
            int idx = sql.indexOf(marker, from);
            if (idx < 0) {
                break;
            }
            int start = sql.lastIndexOf("'[", idx);
            int end = sql.indexOf("]'", idx);
            assertTrue(start > 0 && end > idx, "无法定位 template_flows 的 SQL 字符串");
            String inner = sql.substring(start + 1, end + 1).replace("\\\"", "\"");
            JSONArray flows = JSON.parseArray(inner);
            for (int i = 0; i < flows.size(); i++) {
                graphs.add(flows.getJSONObject(i).getJSONObject("graphJson"));
            }
            from = end + 2;
        }
        assertEquals(4, graphs.size(), "V1 应有 4 条内置登录流");
        return graphs;
    }

    private static String loadSql() {
        try (InputStream in = V1SeedFlowGraphsTest.class
                .getResourceAsStream("/db/migration/V1__baseline.sql")) {
            assertNotNull(in, "缺少 V1__baseline.sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 按 type 找首个节点；无则 null */
    private static JSONObject nodeOfType(JSONObject graph, String type) {
        JSONArray nodes = graph.getJSONArray("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (type.equals(node.getString("type"))) {
                return node;
            }
        }
        return null;
    }

    /** 是否存在「run.entryKind ne scenario」的规范守卫分支 */
    private static boolean hasEntryKindGuard(JSONObject graph) {
        JSONArray nodes = graph.getJSONArray("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (!"condition".equals(node.getString("type"))) {
                continue;
            }
            JSONArray branches = node.getJSONObject("data").getJSONArray("branches");
            for (int j = 0; j < branches.size(); j++) {
                JSONArray conditions = branches.getJSONObject(j).getJSONArray("conditions");
                if (conditions == null) {
                    continue;
                }
                for (int k = 0; k < conditions.size(); k++) {
                    JSONObject condition = conditions.getJSONObject(k);
                    if ("run.entryKind".equals(condition.getString("left"))
                            && "scenario".equals(condition.getString("right"))
                            && "ne".equals(condition.getString("operator"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 是否存在指定 source→target 的边 */
    private static boolean hasEdge(JSONObject graph, String source, String target) {
        JSONArray edges = graph.getJSONArray("edges");
        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            if (source.equals(edge.getString("source")) && target.equals(edge.getString("target"))) {
                return true;
            }
        }
        return false;
    }
}
