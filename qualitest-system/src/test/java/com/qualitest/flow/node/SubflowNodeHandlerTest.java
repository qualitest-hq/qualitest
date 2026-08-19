package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.SubflowNodeHandler;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.validate.FlowNodeType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 SubflowNodeHandler：子流加载、inputs/outputs、跨项目与深度限制。
 * 边界：Mock ITestFlowService；子图用 assign 桩。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubflowNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowNodeHandlerTest {

    private static final long SUBFLOW_ID = 10086L;
    private static final long PROJECT_ID = 1L;

    private ITestFlowService testFlowService;
    private FlowGraphRunner flowGraphRunner;
    private NodeHandlerRegistry childRegistry;
    private SubflowNodeHandler handler;
    private FlowRunContext parentCtx;
    private TestFlow subflow;

    @BeforeEach
    void setUp() {
        testFlowService = mock(ITestFlowService.class);
        flowGraphRunner = new FlowGraphRunner();
        childRegistry = passingAssignRegistry();
        handler = new SubflowNodeHandler(testFlowService, flowGraphRunner, childRegistry);

        Map<String, Object> flow = new HashMap<>();
        flow.put("seedIn", "parent-val");
        parentCtx = FlowRunContext.builder()
                .testProjectId(PROJECT_ID)
                .flow(flow)
                .build();

        subflow = defaultSubflow();
        when(testFlowService.selectTestFlowById(anyLong())).thenReturn(subflow);
    }

    /**
     * 前提：子图 assign 写 childOut；outputs 映射到 parentToken。
     * 期望：passed；childSteps 非空；父 flow 含种子与合并结果。
     */
    @Test
    @Order(1)
    @DisplayName("子流：outputs 合并进父 flow")
    void execute_subflow_mergesOutputs() {
        GraphNode node = subflowNode(nodeData(
                "name", "调用子流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "versionPolicy", "latest",
                "inputs", List.of(inputRow("childIn", "{{flow.seedIn}}")),
                "outputs", List.of(outputRow("childOut", "parentToken"))
        ));

        StepResult result = handler.execute(parentCtx, node, "e1");

        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNotNull(result.getSubflow());
        assertEquals("ok", parentCtx.getFlow().get("parentToken"));
        assertNotNull(result.getSubflow().get("childSteps"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childSteps = (List<Map<String, Object>>) result.getSubflow().get("childSteps");
        assertFalse(childSteps.isEmpty());
    }

    /**
     * 前提：节点 outputs 为空；子图 meta.flowOutputs 声明 childOut。
     * 期望：passed；父 flow.childOut 等于子图写入值。
     */
    @Test
    @Order(2)
    @DisplayName("子流：空 outputs 回退 meta.flowOutputs")
    void execute_emptyOutputs_usesFlowOutputsDefault() {
        GraphNode node = subflowNode(nodeData(
                "name", "默认输出映射",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "versionPolicy", "latest",
                "inputs", List.of(),
                "outputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);

        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("ok", parentCtx.getFlow().get("childOut"));
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
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_INVALID.getCode(), result.getError().getCode());
    }

    /**
     * 前提：subflowId 指向同项目任意测试流且图可跑。
     * 期望：passed；outputs 映射到 parentToken。
     */
    @Test
    @Order(4)
    @DisplayName("子流：同项目任意测试流可执行")
    void execute_anyTestFlow_passes() {
        GraphNode node = subflowNode(nodeData(
                "name", "引用普通测试流",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "versionPolicy", "latest",
                "inputs", List.of(inputRow("childIn", "{{flow.seedIn}}")),
                "outputs", List.of(outputRow("childOut", "parentToken"))
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("ok", parentCtx.getFlow().get("parentToken"));
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
                "versionPolicy", "latest",
                "inputs", List.of(),
                "outputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
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
        handler = new SubflowNodeHandler(testFlowService, flowGraphRunner, failingDelayRegistry());
        subflow.setGraphJson(loadResource("flow/subflow-child-delay-graph.json"));
        GraphNode node = subflowNode(nodeData(
                "name", "子流失败",
                "subflowId", String.valueOf(SUBFLOW_ID),
                "versionPolicy", "latest",
                "inputs", List.of(),
                "outputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getSubflow());
        assertEquals("failed", result.getSubflow().get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childSteps = (List<Map<String, Object>>) result.getSubflow().get("childSteps");
        assertEquals(1, childSteps.size());
        assertEquals("failed", childSteps.get(0).get("status"));
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
                "versionPolicy", "latest",
                "inputs", List.of(),
                "outputs", List.of()
        ));

        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_NESTED.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("嵌套超过上限"));
    }

    private static NodeHandlerRegistry passingAssignRegistry() {
        return new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.ASSIGN) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        ctx.getFlow().put("childOut", "ok");
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("assign")
                                .nodeName("stub-assign")
                                .status(StepResult.STATUS_PASSED)
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
    }

    private static NodeHandlerRegistry failingDelayRegistry() {
        return new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(StepResult.STATUS_FAILED)
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

    private static Map<String, Object> outputRow(String name, String flowKey) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("flowKey", flowKey);
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
