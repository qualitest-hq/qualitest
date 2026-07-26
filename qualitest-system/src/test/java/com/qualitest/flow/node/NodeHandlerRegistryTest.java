package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
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

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NodeHandlerRegistry} 单元测试：验证节点类型到 Handler 的路由与桩执行行为。
 * <p>
 * 被测对象维护 nodeType → {@link NodeHandler} 映射，提供 {@code requireHandler}（未知类型抛异常）
 * 和 {@code execute}（委托 Handler 执行并返回 {@link StepResult}）。
 * 本测试注册 http/assert/delay 三个桩 Handler，验证 MVP 节点可路由、非 MVP 节点被拒绝。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=NodeHandlerRegistryTest
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
     * {@code requireHandler} 对 http / assert / delay 返回对应实现类，且 {@code supports} 为真。
     */
    @Test
    @Order(1)
    void registry_resolvesMvpHandlers() {
        begin("registry_resolvesMvpHandlers");
        assertNotNull(registry.requireHandler("http"));
        assertNotNull(registry.requireHandler("assert"));
        assertNotNull(registry.requireHandler("delay"));
        assertTrue(registry.requireHandler("http").supports("http"));
        assertTrue(registry.requireHandler("assert").supports("assert"));
        assertTrue(registry.requireHandler("delay").supports("delay"));
        log("http / assert / delay routed");
        end("registry_resolvesMvpHandlers");
    }

    /**
     * 未知 type、{@code condition}、{@code assign}、null、空串均应抛 {@link FlowErrorCode#TF_NODE_UNSUPPORTED}。
     */
    @Test
    @Order(2)
    void registry_unknownType_throws() {
        begin("registry_unknownType_throws");
        for (String type : Arrays.asList("unknown", "condition", "assign", null, "")) {
            FlowExecutionException ex = assertThrows(
                    FlowExecutionException.class,
                    () -> registry.requireHandler(type),
                    "type=" + type
            );
            assertEquals(FlowErrorCode.TF_NODE_UNSUPPORTED.getCode(), ex.getCode(), "type=" + type);
            log("rejected type=" + (type == null || type.isBlank() ? "(empty)" : type));
        }
        end("registry_unknownType_throws");
    }

    /**
     * stub {@code execute} 返回 {@code failed}、{@code TF_STEP_ERROR}，message 含「未实现」；
     * {@code flowAfter} 为当前 {@code flow} 快照。
     */
    @Test
    @Order(3)
    void stubExecute_returnsNotImplemented() {
        begin("stubExecute_returnsNotImplemented");
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
            assertEquals(StepResult.STATUS_FAILED, result.getStatus());
            assertEquals(0, result.getDurationMs());
            assertNotNull(result.getError());
            assertEquals(FlowErrorCode.TF_STEP_ERROR.getCode(), result.getError().getCode());
            assertTrue(result.getError().getMessage().contains("未实现"), type);
            assertEquals(ctx.getFlow(), result.getFlowAfter());
            log("stub execute type=" + type);
        }
        end("stubExecute_returnsNotImplemented");
    }

    /**
     * {@code demo-graph.json} 中 {@code http}/{@code assert}/{@code delay} 可路由，
     * {@code condition}/{@code assign} 被拒绝。
     */
    @Test
    @Order(4)
    void demoGraph_mvpNodesRoutable() {
        begin("demoGraph_mvpNodesRoutable");
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
        log("supported nodes=" + supportedCount + " unsupported nodes=" + unsupportedCount);
        assertTrue(supportedCount >= 3, "demo-graph should contain http/assert/delay nodes");
        assertTrue(unsupportedCount >= 1, "demo-graph should contain condition or assign nodes");
        end("demoGraph_mvpNodesRoutable");
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
