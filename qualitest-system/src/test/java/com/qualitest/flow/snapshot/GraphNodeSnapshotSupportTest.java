package com.qualitest.flow.snapshot;

import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 GraphNodeSnapshotSupport：从节点 data 读取 snapshotBefore / snapshotScope。
 * 边界：纯函数，只解析节点 data，无 DB / HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphNodeSnapshotSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphNodeSnapshotSupportTest {

    /**
     * 前提：snapshotBefore 为布尔 true 或字符串 "true"；另测 null / 缺省节点。
     * 期望：true/"true" 为 true；缺省或 null 节点为 false。
     */
    @Test
    @Order(1)
    void isSnapshotBefore_parsesBooleanAndString() {
        begin("isSnapshotBefore_parsesBooleanAndString");
        GraphNode boolNode = node(Map.of(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, true));
        assertTrue(GraphNodeSnapshotSupport.isSnapshotBefore(boolNode));

        GraphNode strNode = node(Map.of(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, "true"));
        assertTrue(GraphNodeSnapshotSupport.isSnapshotBefore(strNode));

        assertFalse(GraphNodeSnapshotSupport.isSnapshotBefore(null));
        assertFalse(GraphNodeSnapshotSupport.isSnapshotBefore(node(Map.of())));
        log("bool=true str=true default=false");
        end("isSnapshotBefore_parsesBooleanAndString");
    }

    /**
     * 前提：节点未配置 snapshotScope。
     * 期望：scope=tables，tables 为空列表。
     */
    @Test
    @Order(2)
    void resolveScope_defaultsWhenMissing() {
        begin("resolveScope_defaultsWhenMissing");
        SnapshotScope scope = GraphNodeSnapshotSupport.resolveScope(node(Map.of()));
        assertEquals(SnapshotScope.SCOPE_TABLES, scope.getScope());
        assertTrue(scope.getTables().isEmpty());
        log("scope=tables tables=[]");
        end("resolveScope_defaultsWhenMissing");
    }

    /**
     * 前提：tables 配置含空白与空串。
     * 期望：trim 后保留 t_order、t_user。
     */
    @Test
    @Order(3)
    void resolveScope_parsesTables() {
        begin("resolveScope_parsesTables");
        Map<String, Object> snapshotScope = Map.of(
                "scope", "tables",
                "tables", List.of("t_order", " t_user ", "")
        );
        SnapshotScope scope = GraphNodeSnapshotSupport.resolveScope(
                node(Map.of(GraphNodeSnapshotSupport.KEY_SNAPSHOT_SCOPE, snapshotScope)));

        assertEquals(SnapshotScope.SCOPE_TABLES, scope.getScope());
        assertEquals(List.of("t_order", "t_user"), scope.getTables());
        log("tables=" + scope.getTables());
        end("resolveScope_parsesTables");
    }

    /**
     * 前提：data.name=下单；另测 null 节点。
     * 期望：返回「下单」；null 返回空串。
     */
    @Test
    @Order(4)
    void nodeName_readsDataName() {
        begin("nodeName_readsDataName");
        assertEquals("下单", GraphNodeSnapshotSupport.nodeName(node(Map.of("name", "下单"))));
        assertEquals("", GraphNodeSnapshotSupport.nodeName(null));
        log("nodeName=下单");
        end("nodeName_readsDataName");
    }

    private static GraphNode node(Map<String, Object> data) {
        return GraphNode.builder().id("n1").type("http").data(data).build();
    }
}
