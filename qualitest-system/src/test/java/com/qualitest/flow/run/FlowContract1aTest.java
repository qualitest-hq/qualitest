package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.DeliverySink;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import com.qualitest.flow.node.impl.EntryNodeHandler;
import com.qualitest.flow.node.impl.ReturnNodeHandler;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.flow.validate.GraphJsonValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 开放/子流交付主链单测。
 * <p>
 * 覆盖：http-return 立刻写出、后置断言失败不改已写出包、零次返回用 fallbackOut 收束、
 * IF 未命中任何臂时仍收束兜底、subflow-return 挂父 nodeOuts、子流零次仍挂兜底且 Outcome 失败、
 * 写出前断连后图仍跑完并腾槽、同序两个 http-return 图校验失败。
 * 边界：内存图 + 内存 DeliverySink；不连库。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowContract1aTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowContract1aTest {

    private final InboundSlotRegistry slots = new InboundSlotRegistry(8);
    private final RunDelivery delivery = new RunDelivery(slots);
    private final FlowGraphRunner runner = new FlowGraphRunner(delivery);
    private final NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
            new AssignNodeHandler(),
            new ReturnNodeHandler(delivery, PlaceholderResolver.strict()),
            new EntryNodeHandler(),
            new AssertNodeHandler(),
            new ConditionNodeHandler()
    ));

    /**
     * 前提：http-entry → assign out → return → assert 故意失败。
     * 期望：sink 已收到 200/ok；Outcome 失败；槽已释放。
     */
    @Test
    @Order(1)
    @DisplayName("http-return 立刻写出后断言失败不改包")
    void returnThenAssertFail_keepsDeliveredOut() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        AtomicBoolean connected = new AtomicBoolean(true);
        String slot = slots.tryAcquire();
        assertNotNull(slot);

        GraphJson graph = httpGraphWithAssertFail();
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                got.set(out.copy());
                return true;
            }

            @Override
            public boolean isConnected() {
                return connected.get();
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertNotNull(got.get());
        assertEquals(200, got.get().getStatus());
        assertEquals(true, ((Map<?, ?>) got.get().getBody()).get("ok"));
        assertTrue(ctx.isDelivered());
        assertNull(ctx.getInboundSlotToken());
        assertEquals(0, slots.heldCount());
    }

    /**
     * 前提：http-entry → assign（无 return）。
     * 期望：settle 用 fallbackOut；Outcome 失败；槽释放。
     */
    @Test
    @Order(2)
    @DisplayName("零次 http-return 收束用 fallbackOut")
    void zeroReturn_settleFallback() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        String slot = slots.tryAcquire();
        GraphJson graph = httpGraphNoReturn();
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                got.set(out.copy());
                return true;
            }

            @Override
            public boolean isConnected() {
                return true;
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals(FlowErrorCode.TF_RETURN_MISSING.getCode(), outcome.getError().getCode());
        assertNotNull(got.get());
        assertEquals(500, got.get().getStatus());
        assertEquals("NO_RETURN", ((Map<?, ?>) got.get().getBody()).get("code"));
        assertEquals(0, slots.heldCount());
    }

    /**
     * 前提：子流 entry → assign out → return；父挂载点。
     * 期望：父 nodeOuts 有 out；子失败时仍可读（本用例先测成功挂载）。
     */
    @Test
    @Order(3)
    @DisplayName("subflow-return 挂载 nodes.<id>.out")
    void subflowReturn_mountsParentNodeOut() {
        FlowRunContext parent = FlowRunContext.builder()
                .entryKind(EntryKind.SCENARIO.getCode())
                .trigger("ui")
                .build();
        GraphJson child = subflowGraphWithReturn();
        FlowRunContext childCtx = FlowRunContext.builder()
                .entryKind(EntryKind.SUBFLOW.getCode())
                .trigger(EntryKind.SUBFLOW.getCode())
                .parentContext(parent)
                .parentSubflowNodeId("sf1")
                .build();

        FlowGraphRunner.Outcome outcome = runner.run(child, childCtx, registry);
        assertTrue(outcome.isPassed());
        Map<String, Object> mounted = parent.getNodeOut("sf1");
        assertNotNull(mounted);
        assertEquals(200, ((Number) mounted.get("status")).intValue());
    }

    /**
     * 前提：子流缺 return，有 fallbackOut；父挂载。
     * 期望：Outcome 失败；父仍可读兜底 out。
     */
    @Test
    @Order(4)
    @DisplayName("子流零次 subflow-return 仍挂兜底且失败")
    void subflowZeroReturn_fallbackReadableOnParent() {
        FlowRunContext parent = FlowRunContext.builder().entryKind(EntryKind.SCENARIO.getCode()).build();
        GraphJson child = subflowGraphNoReturn();
        FlowRunContext childCtx = FlowRunContext.builder()
                .entryKind(EntryKind.SUBFLOW.getCode())
                .trigger(EntryKind.SUBFLOW.getCode())
                .parentContext(parent)
                .parentSubflowNodeId("sf1")
                .build();

        FlowGraphRunner.Outcome outcome = runner.run(child, childCtx, registry);
        assertFalse(outcome.isPassed());
        Map<String, Object> mounted = parent.getNodeOut("sf1");
        assertNotNull(mounted);
        assertEquals(500, ((Number) mounted.get("status")).intValue());
    }

    /**
     * 前提：交付前 sink 已断开（complete 返回 false）。
     * 期望：图仍跑完；delivered=true；deliveryWriteFailed；Outcome 失败（写出失败）；槽释放。
     */
    @Test
    @Order(5)
    @DisplayName("写出前断连后图仍跑完且 Run 记失败")
    void disconnectBeforeWrite_runContinues() {
        String slot = slots.tryAcquire();
        AtomicBoolean connected = new AtomicBoolean(false);
        GraphJson graph = httpGraphNoAssert();
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                return false;
            }

            @Override
            public boolean isConnected() {
                return connected.get();
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertNotNull(outcome.getError());
        assertEquals(FlowErrorCode.TF_DELIVERY_WRITE_FAILED.getCode(), outcome.getError().getCode());
        assertTrue(ctx.isDelivered());
        assertTrue(ctx.isDeliveryWriteFailed());
        assertEquals(0, slots.heldCount());
    }

    /**
     * 前提：开放图 http-entry → 抛异常的业务步（模拟 pluginAction 失败），无 return。
     * 期望：异常转为 FAILED 步；收束写出 fallbackOut；Outcome 失败；不是未捕获冒泡。
     */
    @Test
    @Order(8)
    @DisplayName("业务步抛异常仍收束用 fallbackOut")
    void throwingStep_stillSettlesFallback() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        String slot = slots.tryAcquire();
        GraphJson graph = httpGraphNoReturn();
        NodeHandlerRegistry throwingRegistry = new NodeHandlerRegistry(List.of(
                new com.qualitest.flow.node.NodeHandler() {
                    @Override
                    public boolean supports(String type) {
                        return FlowNodeType.ASSIGN.matches(type);
                    }

                    @Override
                    public com.qualitest.flow.node.StepResult execute(
                            FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        throw new com.qualitest.flow.exception.FlowExecutionException(
                                FlowErrorCode.TF_PLUGIN_ACTION_FAILED, "模拟插件失败");
                    }
                },
                new ReturnNodeHandler(delivery, PlaceholderResolver.strict()),
                new EntryNodeHandler(),
                new AssertNodeHandler(),
                new ConditionNodeHandler()
        ));
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                got.set(out.copy());
                return true;
            }

            @Override
            public boolean isConnected() {
                return true;
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, throwingRegistry);
        assertFalse(outcome.isPassed());
        assertNotNull(got.get());
        assertEquals(500, got.get().getStatus());
        assertEquals("NO_RETURN", ((Map<?, ?>) got.get().getBody()).get("code"));
        assertTrue(ctx.isDelivered());
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "failed".equalsIgnoreCase(s.getStatus())
                        && FlowNodeType.ASSIGN.matches(s.getNodeType())));
        assertEquals(0, slots.heldCount());
    }

    /**
     * 前提：http-entry → condition（仅 IF 且条件永不命中、无 ELSE）。
     * 期望：条件步失败后仍收束写出 fallbackOut，不挂死握连接；Outcome 失败。
     */
    @Test
    @Order(6)
    @DisplayName("IF 未命中任何臂仍收束用 fallbackOut")
    void conditionUnwired_stillSettlesFallback() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        String slot = slots.tryAcquire();
        GraphJson graph = httpGraphConditionNoMatch();
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                got.set(out.copy());
                return true;
            }

            @Override
            public boolean isConnected() {
                return true;
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertNotNull(got.get());
        assertEquals(500, got.get().getStatus());
        assertEquals("NO_RETURN", ((Map<?, ?>) got.get().getBody()).get("code"));
        assertTrue(ctx.isDelivered());
        assertEquals(0, slots.heldCount());
    }

    /**
     * 前提：同序 assign → return → return。
     * 期望：校验报错。
     */
    @Test
    @Order(7)
    @DisplayName("同序双 http-return 图校验失败")
    void validate_sequentialDoubleReturn() {
        GraphJson graph = new GraphJson();
        graph.setNodes(List.of(
                entryHttp("e1"),
                node("a1", "assign", Map.of("assignments", List.of())),
                node("r1", FlowNodeType.HTTP_RETURN.getCode(), Map.of()),
                node("r2", FlowNodeType.HTTP_RETURN.getCode(), Map.of())
        ));
        graph.setEdges(List.of(
                edge("e1", "a1"),
                edge("a1", "r1"),
                edge("r1", "r2")
        ));
        graph.setMeta(new GraphMeta());
        var result = new GraphJsonValidator().validate(graph);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("多个返回节点")));
    }

    /** 构造开放 HTTP 上下文：已占名额、挂内存 sink。 */
    private FlowRunContext baseHttpCtx(String slot, DeliverySink sink) {
        return FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .entryNodeId("e1")
                .trigger(EntryKind.HTTP.getCode())
                .inboundSlotToken(slot)
                .deliverySink(sink)
                .build();
    }

    /** http-entry → assign out → http-return → assert（故意失败）。 */
    private GraphJson httpGraphWithAssertFail() {
        GraphJson g = httpGraphNoAssert();
        List<GraphNode> nodes = new ArrayList<>(g.getNodes());
        nodes.add(node("as1", "assert", "fail", Map.of(
                "rules", List.of(Map.of(
                        "left", "out.status",
                        "op", "eq",
                        "right", 999
                ))
        )));
        List<GraphEdge> edges = new ArrayList<>(g.getEdges());
        edges.add(edge("r1", "as1"));
        g.setNodes(nodes);
        g.setEdges(edges);
        return g;
    }

    /** http-entry → assign out → http-return。 */
    private GraphJson httpGraphNoAssert() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                entryHttp("e1"),
                node("a1", "assign", Map.of(
                        "assignments", List.of(
                                Map.of("scope", "flow", "name", "pkg", "op", "set", "value",
                                        Map.of("status", 200, "body", Map.of("ok", true)))
                        )
                )),
                node("r1", FlowNodeType.HTTP_RETURN.getCode(), Map.of("from", "flow.pkg"))
        ));
        g.setEdges(List.of(edge("e1", "a1"), edge("a1", "r1")));
        return g;
    }

    /** http-entry → assign，无返回节点，靠入口 fallbackOut 收束。 */
    private GraphJson httpGraphNoReturn() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                entryHttp("e1"),
                node("a1", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "out", "name", "status", "op", "set", "value", 200)
                )))
        ));
        g.setEdges(List.of(edge("e1", "a1")));
        return g;
    }

    /** http-entry → condition（IF 永不命中、无 ELSE），零次 return。 */
    private GraphJson httpGraphConditionNoMatch() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                entryHttp("e1"),
                node("c1", "condition", Map.of(
                        "branches", List.of(
                                Map.of(
                                        "id", "b_if",
                                        "kind", "if",
                                        "target", "dead",
                                        "conditions", List.of(Map.of(
                                                "left", "1",
                                                "operator", "eq",
                                                "right", "0"
                                        ))
                                )
                        )
                )),
                node("dead", "assign", Map.of("assignments", List.of()))
        ));
        g.setEdges(List.of(edge("e1", "c1"), edge("c1", "dead")));
        return g;
    }

    /** 子流：entry → assign out → subflow-return。 */
    private GraphJson subflowGraphWithReturn() {
        GraphJson g = new GraphJson();
        Map<String, Object> fb = fallbackMap();
        g.setNodes(List.of(
                node("e1", FlowNodeType.SUBFLOW_ENTRY.getCode(), Map.of("fallbackOut", fb)),
                node("a1", "assign", Map.of(
                        "assignments", List.of(
                                Map.of("scope", "flow", "name", "pkg", "op", "set", "value",
                                        Map.of("status", 200, "body", Map.of("x", 1)))
                        )
                )),
                node("r1", FlowNodeType.SUBFLOW_RETURN.getCode(), Map.of("from", "flow.pkg"))
        ));
        g.setEdges(List.of(edge("e1", "a1"), edge("a1", "r1")));
        return g;
    }

    /** 子流：entry（含 fallbackOut）→ assign，无返回节点。 */
    private GraphJson subflowGraphNoReturn() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                node("e1", FlowNodeType.SUBFLOW_ENTRY.getCode(), Map.of("fallbackOut", fallbackMap())),
                node("a1", "assign", Map.of("assignments", List.of()))
        ));
        g.setEdges(List.of(edge("e1", "a1")));
        return g;
    }

    /** 带 method/path/envId/fallbackOut 的 http-entry 节点。 */
    private static GraphNode entryHttp(String id) {
        return node(id, FlowNodeType.HTTP_ENTRY.getCode(), Map.of(
                "method", "POST",
                "path", "/v1/demo",
                "envId", "1",
                "fallbackOut", fallbackMap()
        ));
    }

    /** 入口兜底出参样例：500 + NO_RETURN。 */
    private static Map<String, Object> fallbackMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", 500);
        m.put("headers", Map.of("Content-Type", "application/json;charset=UTF-8"));
        m.put("body", Map.of("code", "NO_RETURN", "message", "未执行到返回节点"));
        return m;
    }

    private static GraphNode node(String id, String type, Map<String, Object> data) {
        return node(id, type, type, data);
    }

    private static GraphNode node(String id, String type, String name, Map<String, Object> data) {
        GraphNode n = new GraphNode();
        n.setId(id);
        n.setType(type);
        n.setName(name);
        n.setPosition(new GraphNodePosition(0, 0));
        n.setData(new HashMap<>(data));
        return n;
    }

    private static GraphEdge edge(String from, String to) {
        GraphEdge e = new GraphEdge();
        e.setId(from + "-" + to);
        e.setSource(from);
        e.setTarget(to);
        return e;
    }
}
