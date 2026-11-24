package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.DeliverySink;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.node.impl.AssignNodeHandler;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测谁：图运行器对隐式扇出（非 condition 多出边）的执行与图校验。
 * <p>
 * 覆盖：一臂 return 一臂旁路、双臂 return 被图校验拦截、一臂失败另一臂仍跑完、汇合断言双跑。
 * 边界：使用内存图与内存 DeliverySink；各扇出臂在内存中依次执行。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowContract1bTest {

    private final InboundSlotRegistry slots = new InboundSlotRegistry(8);
    private final RunDelivery delivery = new RunDelivery(slots);
    private final FlowGraphRunner runner = new FlowGraphRunner(delivery);
    private final NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
            new AssignNodeHandler(),
            new ReturnNodeHandler(delivery, PlaceholderResolver.strict()),
            new EntryNodeHandler(),
            new AssertNodeHandler()
    ));

    /**
     * 前提：入口后节点多出边两臂，仅 B 臂 http-return；A 臂旁路 assign。
     * 期望：写出 200；Outcome 通过。
     */
    @Test
    @Order(1)
    @DisplayName("扇出一臂 return 一臂旁路")
    void fanout_oneArmReturn_oneBypass() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        String slot = slots.tryAcquire();
        GraphJson graph = fanoutHttpGraphOneReturn();
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
        assertTrue(outcome.isPassed());
        assertNotNull(got.get());
        assertEquals(200, got.get().getStatus());
    }

    /**
     * 前提：扇出两臂各含 http-return。
     * 期望：图校验不通过，错误含「至多一臂」。
     */
    @Test
    @Order(2)
    @DisplayName("扇出双臂 return 图校验失败")
    void validate_dualReturnInFanout() {
        GraphJson graph = dualReturnFanoutGraph();
        var result = new GraphJsonValidator().validate(graph);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("至多一臂")));
    }

    /**
     * 前提：扇出 A 臂断言失败；B 臂 assign 成功。
     * 期望：两臂均执行；Outcome 失败。
     */
    @Test
    @Order(3)
    @DisplayName("扇出一臂失败另一臂跑完")
    void fanout_oneArmFail_otherCompletes() {
        String slot = slots.tryAcquire();
        GraphJson graph = fanoutOneArmFailGraph();
        FlowRunContext ctx = baseHttpCtx(slot, new DeliverySink() {
            @Override
            public boolean complete(OutPayload out) {
                return true;
            }

            @Override
            public boolean isConnected() {
                return true;
            }
        });

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "bypass".equals(s.getNodeId()) && RunStatus.PASSED.matches(s.getStatus())));
        assertNotNull(outcome.getError());
    }

    /**
     * 前提：场景入口后扇出，两臂都汇入同一断言节点。
     * 期望：断言执行两次且 Outcome 通过。
     */
    @Test
    @Order(4)
    @DisplayName("扇出汇合断言双跑")
    void fanout_convergeAssert_runsTwice() {
        GraphJson graph = fanoutConvergeAssertGraph();
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.SCENARIO.getCode())
                .entryNodeId("e1")
                .trigger("ui")
                .build();

        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        long assertSteps = outcome.getSteps().stream()
                .filter(s -> "mergeAssert".equals(s.getNodeId()))
                .count();
        assertEquals(2, assertSteps);
        assertTrue(outcome.isPassed());
    }

    private FlowRunContext baseHttpCtx(String slot, DeliverySink sink) {
        return FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .entryNodeId("e1")
                .trigger(EntryKind.HTTP.getCode())
                .inboundSlotToken(slot)
                .deliverySink(sink)
                .build();
    }

    /** 入口 → 扇出点 → 旁路臂 / return 臂（无 join） */
    private GraphJson fanoutHttpGraphOneReturn() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                entryHttp("e1"),
                node("split", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "split", "op", "set", "value", true)
                ))),
                node("bypass", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "side", "op", "set", "value", "ok")
                ))),
                node("pkg", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "pkg", "op", "set", "value",
                                Map.of("status", 200, "body", Map.of("ok", true)))
                ))),
                node("r1", FlowNodeType.HTTP_RETURN.getCode(), Map.of("from", "flow.pkg"))
        ));
        g.setEdges(List.of(
                edge("e1", "split"),
                edge("split", "bypass"),
                edge("split", "pkg"),
                edge("pkg", "r1")
        ));
        return g;
    }

    private GraphJson dualReturnFanoutGraph() {
        GraphJson g = fanoutHttpGraphOneReturn();
        List<GraphNode> nodes = new ArrayList<>(g.getNodes());
        nodes.add(node("r2", FlowNodeType.HTTP_RETURN.getCode(), Map.of("from", "flow.side")));
        g.setNodes(nodes);
        List<GraphEdge> edges = new ArrayList<>(g.getEdges());
        edges.add(edge("bypass", "r2"));
        g.setEdges(edges);
        return g;
    }

    private GraphJson fanoutOneArmFailGraph() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                entryHttp("e1"),
                node("split", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "split", "op", "set", "value", true)
                ))),
                node("failArm", "assert", Map.of(
                        "rules", List.of(Map.of("left", "flow.missing", "op", "eq", "right", 1))
                )),
                node("bypass", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "done", "op", "set", "value", true)
                )))
        ));
        g.setEdges(List.of(
                edge("e1", "split"),
                edge("split", "failArm"),
                edge("split", "bypass")
        ));
        return g;
    }

    /** 场景入口 → 扇出 → 两臂汇入同一断言（断言应执行两次） */
    private GraphJson fanoutConvergeAssertGraph() {
        GraphJson g = new GraphJson();
        g.setNodes(List.of(
                node("e1", FlowNodeType.SCENARIO_ENTRY.getCode(), Map.of(
                        "doc", "",
                        "scenarios", List.of(Map.of("id", "sc1", "name", "默认", "inSeed", Map.of()))
                )),
                node("split", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "split", "op", "set", "value", true)
                ))),
                node("a", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "a", "op", "set", "value", 1)
                ))),
                node("b", "assign", Map.of("assignments", List.of(
                        Map.of("scope", "flow", "name", "b", "op", "set", "value", 2)
                ))),
                node("mergeAssert", "assert", Map.of(
                        "rules", List.of(Map.of("left", "flow.split", "op", "eq", "right", true))
                ))
        ));
        g.setEdges(List.of(
                edge("e1", "split"),
                edge("split", "a"),
                edge("split", "b"),
                edge("a", "mergeAssert"),
                edge("b", "mergeAssert")
        ));
        return g;
    }

    private static GraphNode entryHttp(String id) {
        return node(id, FlowNodeType.HTTP_ENTRY.getCode(), Map.of(
                "envId", "1",
                "method", "POST",
                "path", "/v1/parallel",
                "fallbackOut", fallbackMap()
        ));
    }

    private static Map<String, Object> fallbackMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", 500);
        m.put("headers", Map.of("Content-Type", "application/json;charset=UTF-8"));
        m.put("body", Map.of("code", "NO_RETURN", "message", "未执行到返回节点"));
        return m;
    }

    private static GraphNode node(String id, String type, Map<String, Object> data) {
        GraphNode n = new GraphNode();
        n.setId(id);
        n.setType(type);
        n.setName(type);
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
