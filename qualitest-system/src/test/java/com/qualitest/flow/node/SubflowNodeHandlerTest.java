package com.qualitest.flow.node;


import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.EntryNodeHandler;
import com.qualitest.flow.node.impl.ReturnNodeHandler;
import com.qualitest.flow.node.impl.SubflowNodeHandler;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 SubflowNodeHandler：子流加载、inputs、跨项目与深度限制、定稿挂 nodes.&lt;id&gt;.out、就绪门禁。
 * 边界：Mock ITestFlowService / FlowRunReadinessGate；子图用 assign 桩。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubflowNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowNodeHandlerTest {

    private static final long SUBFLOW_ID = 10086L;
    private static final long PROJECT_ID = 1L;

    private ITestFlowService testFlowService;
    private ParsedGraphCache parsedGraphCache;
    private FlowGraphRunner flowGraphRunner;
    private FlowRunReadinessGate flowRunReadinessGate;
    private NodeHandlerRegistry childRegistry;
    private SubflowNodeHandler handler;
    private FlowRunContext parentCtx;
    private TestFlow subflow;

    @BeforeEach
    void setUp() {
        testFlowService = mock(ITestFlowService.class);
        parsedGraphCache = mock(ParsedGraphCache.class);
        flowRunReadinessGate = mock(FlowRunReadinessGate.class);
        when(flowRunReadinessGate.collectBlockingErrors(any(), any())).thenReturn(List.of());
        RunDelivery delivery = new RunDelivery(new InboundSlotRegistry(4));
        flowGraphRunner = new FlowGraphRunner(delivery);
        childRegistry = passingAssignRegistry(delivery);

        Map<String, Object> flow = new HashMap<>();
        flow.put("seedIn", "parent-val");
        parentCtx = FlowRunContext.builder()
                .testProjectId(PROJECT_ID)
                .flow(flow)
                .build();

        subflow = defaultSubflow();
        when(testFlowService.selectTestFlowById(anyLong())).thenReturn(subflow);
        when(parsedGraphCache.getFrom(org.mockito.ArgumentMatchers.any(TestFlow.class))).thenAnswer(inv ->
                subflow.getGraphJson() != null ? GraphJson.parse(subflow.getGraphJson()) : null);
        handler = new SubflowNodeHandler(
                testFlowService, parsedGraphCache, flowGraphRunner, flowRunReadinessGate, childRegistry);
    }

    /**
     * 前提：子图 return 定稿 out；父节点仅配 inputs。
     * 期望：passed；定稿挂 nodes.sf-node.out；父 flow 不自动带上子 flow 键。
     */
    @Test
    @Order(1)
    @DisplayName("子流：定稿挂 nodes.<id>.out")
    void execute_subflow_mountsNodeOut() {
        GraphNode node = subflowNode(nodeData(
                "name", "调用子流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of(inputRow("childIn", "{{flow.seedIn}}"))
        ));

        StepResult result = handler.execute(parentCtx, node, "e1");

        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNotNull(result.getSubflow());
        assertNull(parentCtx.getFlow().get("parentToken"));
        assertNull(parentCtx.getFlow().get("childOut"));
        Map<String, Object> mounted = parentCtx.getNodeOut("sf-node");
        assertNotNull(mounted);
        assertNotNull(result.getSubflow().get("out"));
        assertNotNull(result.getSubflow().get("childSteps"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childSteps = (List<Map<String, Object>>) result.getSubflow().get("childSteps");
        assertFalse(childSteps.isEmpty());
    }

    /**
     * 前提：父节点无 inputs；子图仍会 return。
     * 期望：passed；父 flow 不自动合并子 flow 键，定稿仍挂 nodes.&lt;id&gt;.out。
     */
    @Test
    @Order(2)
    @DisplayName("子流：无 inputs 时仍只挂 nodes.<id>.out")
    void execute_withoutInputs_stillMountsNodeOutOnly() {
        GraphNode node = subflowNode(nodeData(
                "name", "无输入映射",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);

        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNull(parentCtx.getFlow().get("childOut"));
        assertNotNull(parentCtx.getNodeOut("sf-node"));
    }

    /**
     * 前提：节点未配置 subflowId。
     * 期望：failed；错误码 TF_SUBFLOW_INVALID。
     */
    @Test
    @Order(3)
    @DisplayName("子流：缺少 subflowId 返回 TF_SUBFLOW_INVALID")
    void execute_missingSubflowId_fails() {
        GraphNode node = subflowNode(nodeData("name", "坏节点"));
        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_INVALID.getCode(), result.getError().getCode());
    }

    /**
     * 前提：subflowId 指向同项目任意测试流且图可跑。
     * 期望：passed；定稿挂 nodes.sf-node.out。
     */
    @Test
    @Order(4)
    @DisplayName("子流：同项目任意测试流可执行")
    void execute_anyTestFlow_passes() {
        GraphNode node = subflowNode(nodeData(
                "name", "引用普通测试流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of(inputRow("childIn", "{{flow.seedIn}}"))
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNotNull(parentCtx.getNodeOut("sf-node"));
    }

    /**
     * 前提：子流 testProjectId 与父上下文不一致。
     * 期望：failed；消息含「不属于当前项目」。
     */
    @Test
    @Order(5)
    @DisplayName("子流：跨项目拒绝")
    void execute_crossProject_fails() {
        subflow.setTestProjectId(99L);
        GraphNode node = subflowNode(nodeData(
                "name", "跨项目子流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_INVALID.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("不属于当前项目"));
    }

    /**
     * 前提：子图任一步返回 failed。
     * 期望：父 subflow 步 failed，且含 childSteps 摘要。
     */
    @Test
    @Order(6)
    @DisplayName("子流：子步失败向上传播")
    void execute_childStepFails_propagatesError() {
        handler = new SubflowNodeHandler(
                testFlowService, parsedGraphCache, flowGraphRunner, flowRunReadinessGate, failingDelayRegistry());
        subflow.setGraphJson(loadResource("flow/subflow-child-delay-graph.json"));
        GraphNode node = subflowNode(nodeData(
                "name", "子流失败",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertNotNull(result.getSubflow());
        assertEquals("failed", result.getSubflow().get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childSteps = (List<Map<String, Object>>) result.getSubflow().get("childSteps");
        assertEquals(2, childSteps.size());
        assertEquals("failed", childSteps.get(1).get("status"));
        assertNotNull(result.getError());
    }

    /**
     * 前提：父上下文嵌套深度已达 MAX_DEPTH。
     * 期望：failed；TF_SUBFLOW_NESTED；消息含「嵌套超过上限」。
     */
    @Test
    @Order(7)
    @DisplayName("子流：嵌套超限返回 TF_SUBFLOW_NESTED")
    void execute_depthExceeded_fails() {
        parentCtx.setSubflowDepth(SubflowDepth.MAX_DEPTH);
        GraphNode node = subflowNode(nodeData(
                "name", "嵌套过深",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_NESTED.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("嵌套超过上限"));
    }

    /**
     * 前提：父节点指定错误的 entryNodeId（非 subflow-entry）。
     * 期望：failed；TF_SUBFLOW_INVALID；消息含 subflow-entry。
     */
    @Test
    @Order(8)
    @DisplayName("子流：entryNodeId 非 subflow-entry 拒绝")
    void execute_entryNodeIdNotSubflowEntry_fails() {
        GraphNode node = subflowNode(nodeData(
                "name", "错入口",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "entryNodeId", "c1",
                "inputs", List.of()
        ));
        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_INVALID.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("subflow-entry"));
    }

    /**
     * 前提：就绪门禁返回缺 fallbackOut 等 blocking 错误。
     * 期望：failed；TF_GRAPH_INVALID；不进入子 runner（无 childSteps）。
     */
    @Test
    @Order(9)
    @DisplayName("子流：图未就绪则硬拦不进 runner")
    void execute_childGraphNotReady_failsWithoutRunning() {
        FlowGraphRunner runnerSpy = org.mockito.Mockito.spy(flowGraphRunner);
        when(flowRunReadinessGate.collectBlockingErrors(any(), any()))
                .thenReturn(List.of("子流入口须配置 fallbackOut"));
        handler = new SubflowNodeHandler(
                testFlowService, parsedGraphCache, runnerSpy, flowRunReadinessGate, childRegistry);
        GraphNode node = subflowNode(nodeData(
                "name", "半成品子流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "inputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);

        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_GRAPH_INVALID.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("fallbackOut"));
        assertNotNull(result.getSubflow());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childSteps = (List<Map<String, Object>>) result.getSubflow().get("childSteps");
        assertTrue(childSteps == null || childSteps.isEmpty());
        verify(runnerSpy, never()).run(any(), any(), any());
    }

    private static NodeHandlerRegistry passingAssignRegistry(RunDelivery delivery) {
        return new NodeHandlerRegistry(List.of(
                new EntryNodeHandler(),
                new AssignNodeHandler(),
                new ReturnNodeHandler(delivery, PlaceholderResolver.strict())
        ));
    }

    private static NodeHandlerRegistry failingDelayRegistry() {
        return new NodeHandlerRegistry(List.of(
                new EntryNodeHandler(),
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(RunStatus.FAILED.getCode())
                                .durationMs(1)
                                .error(StepError.of(FlowErrorCode.TF_STEP_ERROR, "子步骤失败"))
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
    }

    private static TestFlow defaultSubflow() {
        TestFlow flow = new TestFlow();
        flow.setTestFlowId(SUBFLOW_ID);
        flow.setTestProjectId(PROJECT_ID);
        flow.setFlowName("测试子流");
        flow.setGraphJson(loadResource("flow/subflow-child-graph.json"));
        flow.setDelStatus(0);
        return flow;
    }

    private static GraphNode subflowNode(Map<String, Object> data) {
        return GraphNode.builder()
                .id("sf-node")
                .type("subflow")
                .data(data)
                .build();
    }

    private static Map<String, Object> nodeData(Object... entries) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            data.put((String) entries[i], entries[i + 1]);
        }
        return data;
    }

    private static Map<String, Object> inputRow(String name, String value) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("value", value);
        return row;
    }

    private static String loadResource(String path) {
        InputStream in = SubflowNodeHandlerTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing resource: " + path);
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
