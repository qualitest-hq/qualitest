package com.qualitest.project.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.delivery.RunTrigger;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import com.qualitest.flow.node.impl.EntryNodeHandler;
import com.qualitest.flow.node.impl.ReturnNodeHandler;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.run.RunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测内置登录流的运行时分轨：同一张图，场景入口只跑不交付，子流入口返回并挂父。
 * 边界：HTTP 节点用桩（回 200 且 expectedMatch=true），不连真实被测系统。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=PrefabricatedLoginFlowRunTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("内置登录流运行时分轨")
class PrefabricatedLoginFlowRunTest {

    private final RunDelivery delivery = new RunDelivery(new InboundSlotRegistry());
    private final NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
            new EntryNodeHandler(),
            new ConditionNodeHandler(),
            new AssignNodeHandler(),
            new ReturnNodeHandler(delivery, PlaceholderResolver.strict()),
            new StubHttpHandler()));

    /**
     * 前提：从场景入口跑内置登录流，asset.adminAuth.token 已存在。
     * 期望：Run 通过；走守卫 ELSE 自然结束，不产生任何对外挂载。
     */
    @Test
    @Order(1)
    @DisplayName("场景入口只跑不交付")
    void scenarioEntry_runsWithoutDelivery() {
        Fixture fixture = fixture();
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.SCENARIO.getCode())
                .trigger(RunTrigger.UI.getCode())
                .entryNodeId(fixture.scenarioEntryId())
                .asset(fixture.asset())
                .build();

        FlowGraphRunner.Outcome outcome = new FlowGraphRunner(delivery).run(fixture.graph(), ctx, registry);

        assertTrue(outcome.isPassed(), () -> "error=" + outcome.getError());
        assertEquals(0, ctx.getReturnCount());
    }

    /**
     * 前提：从子流入口跑内置登录流，父上下文等待 nodes.&lt;id&gt;.out。
     * 期望：Run 通过；守卫 IF 命中 subflow-return，父侧读到大 out.body.token。
     */
    @Test
    @Order(2)
    @DisplayName("子流入口返回并挂父")
    void subflowEntry_deliversToParent() {
        Fixture fixture = fixture();
        FlowRunContext parent = FlowRunContext.builder()
                .entryKind(EntryKind.SCENARIO.getCode())
                .build();
        FlowRunContext child = FlowRunContext.builder()
                .entryKind(EntryKind.SUBFLOW.getCode())
                .trigger(RunTrigger.SUBFLOW.getCode())
                .entryNodeId(fixture.subflowEntryId())
                .parentContext(parent)
                .parentSubflowNodeId("sf1")
                .asset(fixture.asset())
                .build();

        FlowGraphRunner.Outcome outcome = new FlowGraphRunner(delivery).run(fixture.graph(), child, registry);

        assertTrue(outcome.isPassed(), () -> "error=" + outcome.getError());
        Map<String, Object> mounted = parent.getNodeOut("sf1");
        assertNotNull(mounted);
        assertEquals("t-1", ((Map<?, ?>) mounted.get("body")).get("token"));
    }

    /** 内置登录图 + 两个入口 id + 已就绪的 asset */
    private static Fixture fixture() {
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录子流", "POST", "/login", "adminAuth", "token", "body", "$.token",
                "GET", "/getInfo", "9001", "9002", "登录", "探活");
        JSONArray arr = JSON.parseArray(flows);
        JSONObject graphJson = arr.getJSONObject(0).getJSONObject("graphJson");
        GraphJson graph = GraphJson.parse(graphJson.toJSONString());

        Map<String, Object> asset = new HashMap<>();
        asset.put("adminAuth", new HashMap<>(Map.of("token", "t-1")));

        return new Fixture(graph, idOfType(graphJson, "scenario-entry"), idOfType(graphJson, "subflow-entry"), asset);
    }

    private static String idOfType(JSONObject graph, String type) {
        JSONArray nodes = graph.getJSONArray("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (type.equals(node.getString("type"))) {
                return node.getString("id");
            }
        }
        throw new IllegalStateException("missing node type: " + type);
    }

    /** 用例夹具：图、两个入口 id、asset 根 */
    private record Fixture(GraphJson graph, String scenarioEntryId, String subflowEntryId,
                           Map<String, Object> asset) {
    }

    /** HTTP 桩：恒回 200 / expectedMatch=true，供条件节点读取 http.* */
    private static final class StubHttpHandler implements NodeHandler {

        @Override
        public boolean supports(String type) {
            return "http".equals(type);
        }

        @Override
        public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
            ctx.putHttpResponse(node.getId(), FlowRunContext.HttpResponseSnapshot.builder()
                    .status(200)
                    .headers(Map.of())
                    .body(Map.of())
                    .bodyText("{}")
                    .responseKind("json")
                    .expectedMatch(true)
                    .durationMs(1L)
                    .build());
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType("http")
                    .nodeName(node.getName())
                    .edgeId(incomingEdgeId)
                    .status(RunStatus.PASSED.getCode())
                    .durationMs(0L)
                    .flowAfter(new HashMap<>(ctx.getFlow()))
                    .build();
        }
    }
}
