package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：OpenHttpRouteCollisionGate。
 * 边界：同流双入口、跨流占用、改 path 后不冲突、排除自身流。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OpenHttpRouteCollisionGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenHttpRouteCollisionGateTest {

    /**
     * 前提：同一图两个 http-entry 同 method+path。
     * 期望：一条本流冲突错误。
     */
    @Test
    @Order(1)
    @DisplayName("同流双入口冲突")
    void sameGraphDuplicate() {
        GraphJson graph = graphWithEntries(
                entry("e1", "下单A", "POST", "/order"),
                entry("e2", "下单B", "POST", "/order"));
        List<String> errors = OpenHttpRouteCollisionGate.validate(graph, 1L, List.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("本流"));
    }

    /**
     * 前提：本图 path 已被其它流占用。
     * 期望：跨流冲突文案含对方流名。
     */
    @Test
    @Order(2)
    @DisplayName("跨流冲突硬拦")
    void crossFlowConflict() {
        GraphJson graph = graphWithEntries(entry("e1", "本口", "GET", "/ping"));
        List<OpenHttpRouteCollisionGate.PeerRoute> peers = List.of(
                new OpenHttpRouteCollisionGate.PeerRoute(
                        99L, "已有流", "old", "旧口", "GET", "/ping"));
        List<String> errors = OpenHttpRouteCollisionGate.validate(graph, 1L, peers);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("已有流"));
    }

    /**
     * 前提：peer 属于当前流自身（更新保存）。
     * 期望：不报跨流冲突。
     */
    @Test
    @Order(3)
    @DisplayName("排除自身流行")
    void ignoreSelfPeer() {
        GraphJson graph = graphWithEntries(entry("e1", "本口", "GET", "/ping"));
        List<OpenHttpRouteCollisionGate.PeerRoute> peers = List.of(
                new OpenHttpRouteCollisionGate.PeerRoute(
                        1L, "本流", "e1", "本口", "GET", "/ping"));
        List<String> errors = OpenHttpRouteCollisionGate.validate(graph, 1L, peers);
        assertTrue(errors.isEmpty());
    }

    /**
     * 前提：本图 path 与 peer 不同。
     * 期望：通过。
     */
    @Test
    @Order(4)
    @DisplayName("不同 path 可通过")
    void differentPathOk() {
        GraphJson graph = graphWithEntries(entry("e1", "本口", "POST", "/order/v2"));
        List<OpenHttpRouteCollisionGate.PeerRoute> peers = List.of(
                new OpenHttpRouteCollisionGate.PeerRoute(
                        2L, "其它", "x", "旧", "POST", "/order"));
        assertTrue(OpenHttpRouteCollisionGate.validate(graph, 1L, peers).isEmpty());
    }

    /** 构造只含给定入口节点的图 */
    private static GraphJson graphWithEntries(GraphNode... entries) {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(entries));
        return g;
    }

    /** 构造开放 HTTP 入口节点：指定名称、method 与 path，带兜底出参 */
    private static GraphNode entry(String id, String name, String method, String path) {
        GraphNode n = new GraphNode();
        n.setId(id);
        n.setType(FlowNodeType.HTTP_ENTRY.getCode());
        n.setName(name);
        n.setData(Map.of("method", method, "path", path, "fallbackOut", Map.of(
                "status", 500,
                "headers", Map.of(),
                "body", Map.of())));
        return n;
    }
}
