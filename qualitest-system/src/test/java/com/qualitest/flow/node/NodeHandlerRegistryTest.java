package com.qualitest.flow.node;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 NodeHandlerRegistry：类型路由、未知类型拒绝、桩 execute 行为。
 * 边界：仅注册 http/assert/delay 桩；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=NodeHandlerRegistryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NodeHandlerRegistryTest {

    /** 被测注册表 */
    private NodeHandlerRegistry registry;

    /** 带 flow 变量的 Mock 运行时上下文 */
    private FlowRunContext ctx;

    /**
     * 每个 @Test 执行前重建注册表与上下文。
     */
    @BeforeEach
    void setUp() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.HTTP) {},
                new AbstractStubNodeHandler(FlowNodeType.ASSERT) {},
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {}
        ));
        Map<String, Object> flow = new HashMap<>();
        flow.put("loginUser", "admin");
        flow.put("token", "mock-token");
        ctx = FlowRunContext.builder().flow(flow).build();
    }

    /**
     * 前提：registry 已注册 http/assert/delay 桩 handler。
     * 期望：requireHandler 返回非 null，supports 均为 true。
     */
    @Test
    @Order(1)
    @DisplayName("注册表：可解析 http/assert/delay 桩")
    void registry_resolvesMvpHandlers() {
        assertNotNull(registry.requireHandler("http"));
        assertNotNull(registry.requireHandler("assert"));
        assertNotNull(registry.requireHandler("delay"));
        assertTrue(registry.requireHandler("http").supports("http"));
        assertTrue(registry.requireHandler("assert").supports("assert"));
        assertTrue(registry.requireHandler("delay").supports("delay"));
    }

    /**
     * 前提：requireHandler 入参为 unknown/condition/assign/null/空串。
     * 期望：均抛 TF_NODE_UNSUPPORTED 异常。
     */
    @Test
    @Order(2)
    @DisplayName("注册表：未知类型抛 TF_NODE_UNSUPPORTED")
    void registry_unknownType_throws() {
        for (String type : Arrays.asList("unknown", "condition", "assign", null, "")) {
            FlowExecutionException ex = assertThrows(
                    FlowExecutionException.class,
                    () -> registry.requireHandler(type),
                    "type=" + type
            );
            assertEquals(FlowErrorCode.TF_NODE_UNSUPPORTED.getCode(), ex.getCode(), "type=" + type);
        }
    }

    /**
     * 前提：http/assert/delay 桩 handler 执行各类型节点。
     * 期望：返回 failed + TF_STEP_ERROR，message 含「未实现」，flowAfter 为当前 flow 快照。
     */
    @Test
    @Order(3)
    @DisplayName("桩执行：返回未实现失败结果")
    void stubExecute_returnsNotImplemented() {
        for (String type : List.of("http", "assert", "delay")) {
            GraphNode node = GraphNode.builder()
                    .id("n-" + type)
                    .type(type)
                    .data(Map.of("name", "测试-" + type))
                    .build();
            StepResult result = registry.execute(ctx, node, "e1");
            assertEquals("n-" + type, result.getNodeId());
            assertEquals(type, result.getNodeType());
            assertEquals("测试-" + type, result.getNodeName());
            assertEquals("e1", result.getEdgeId());
            assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
            assertEquals(0, result.getDurationMs());
            assertNotNull(result.getError());
            assertEquals(FlowErrorCode.TF_STEP_ERROR.getCode(), result.getError().getCode());
            assertTrue(result.getError().getMessage().contains("未实现"), type);
            assertEquals(ctx.getFlow(), result.getFlowAfter());
        }
    }

    /**
     * 前提：demo-graph.json 含 http/assert/delay 与 condition/assign 节点。
     * 期望：MVP 类型可路由，condition/assign 抛 TF_NODE_UNSUPPORTED。
     */
    @Test
    @Order(4)
    @DisplayName("demo-graph：MVP 可路由，condition/assign 拒绝")
    void demoGraph_mvpNodesRoutable() {
        String json = loadResource("flow/demo-graph.json");
        GraphJson graph = GraphJson.parse(json);
        int supportedCount = 0;
        int unsupportedCount = 0;
        for (GraphNode node : graph.getNodes()) {
            String type = node.getType();
            if (FlowNodeType.HTTP.matches(type)
                    || FlowNodeType.ASSERT.matches(type)
                    || FlowNodeType.DELAY.matches(type)) {
                assertDoesNotThrow(() -> registry.requireHandler(type), node.getId());
                supportedCount++;
            } else if (FlowNodeType.CONDITION.matches(type) || FlowNodeType.ASSIGN.matches(type)) {
                FlowExecutionException ex = assertThrows(
                        FlowExecutionException.class,
                        () -> registry.requireHandler(type),
                        node.getId()
                );
                assertEquals(FlowErrorCode.TF_NODE_UNSUPPORTED.getCode(), ex.getCode());
                unsupportedCount++;
            }
        }
        assertTrue(supportedCount >= 3, "demo-graph should contain http/assert/delay nodes");
        assertTrue(unsupportedCount >= 1, "demo-graph should contain condition or assign nodes");
    }

    /**
     * 从 classpath {@code src/test/resources/} 加载 JSON 文本。
     */
    private static String loadResource(String path) {
        InputStream in = NodeHandlerRegistryTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing resource: " + path);
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
