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
 * {@link GraphNodeSnapshotSupport} 单元测试：从节点 data 读取 snapshotBefore / snapshotScope。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=GraphNodeSnapshotSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphNodeSnapshotSupportTest {

    /**
     * snapshotBefore 支持布尔 true 与字符串 "true"。
     * 期望：缺省或 null 节点为 false。
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
     * 未配置 snapshotScope。
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
     * 配置 tables 含空白与空串。
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
     * data.name 存在时读取展示名。
     * 期望：nodeName=下单；null 节点返回空串。
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
