package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.migrate.GraphMigrator;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.SubflowNodeHandler;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.subflow.SubflowDepth;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SubflowNodeHandler} 单元测试：子流加载、inputs 种子、outputs 合并与 childSteps。
 * <p>
 * 被测对象按 subflowId 加载同项目任意测试流，在内存中跑子图并把 outputs 写回父 flow。
 * 依赖 Mock {@link ITestFlowService}；子图 Handler 使用 assign/delay 桩，不访问数据库。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=SubflowNodeHandlerTest
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
        handler = new SubflowNodeHandler(testFlowService, flowGraphRunner, childRegistry, new GraphMigrator());

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
     * 正常路径：子图 assign 写入 childOut，outputs 映射到父 flow.parentToken。
     * 期望：passed；childSteps 非空；父 flow 含 inputs 种子与 outputs 合并结果。
     */
    @Test
    @Order(1)
    void execute_subflow_mergesOutputs() {
        begin("execute_subflow_mergesOutputs");
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
        log("outputs merged parentToken=" + parentCtx.getFlow().get("parentToken")
                + " childSteps=" + childSteps.size());
        end("execute_subflow_mergesOutputs");
    }

    /**
     * 节点 outputs 为空时，应使用子图 meta.flowOutputs 默认映射写回父 flow。
     * 期望：passed；父 flow.childOut 等于子图 assign 写入值。
     */
    @Test
    @Order(2)
    void execute_emptyOutputs_usesFlowOutputsDefault() {
        begin("execute_emptyOutputs_usesFlowOutputsDefault");
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
        log("childOut=" + parentCtx.getFlow().get("childOut"));
        end("execute_emptyOutputs_usesFlowOutputsDefault");
    }

    /**
     * 节点未配置 subflowId 时应立即失败。
     * 期望：failed；错误码 {@link FlowErrorCode#TF_SUBFLOW_INVALID}。
     */
    @Test
    @Order(3)
    void execute_missingSubflowId_fails() {
        begin("execute_missingSubflowId_fails");
        GraphNode node = subflowNode(nodeData("name", "坏节点"));
        StepResult result = handler.execute(parentCtx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_SUBFLOW_INVALID.getCode(), result.getError().getCode());
        log("error=" + result.getError().getMessage());
        end("execute_missingSubflowId_fails");
    }

    /**
     * 任意同项目测试流均可被子流节点引用并正常执行。
     * 期望：passed；outputs 映射到父 flow.parentToken。
     */
    @Test
    @Order(4)
    void execute_anyTestFlow_passes() {
        begin("execute_anyTestFlow_passes");
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
        log("parentToken=" + parentCtx.getFlow().get("parentToken"));
        end("execute_anyTestFlow_passes");
    }

    /**
     * 子流与父运行上下文不属于同一项目时应失败。
     * 期望：failed；错误消息含「不属于当前项目」。
     */
    @Test
    @Order(5)
    void execute_crossProject_fails() {
        begin("execute_crossProject_fails");
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
        log("error=" + result.getError().getMessage());
        end("execute_crossProject_fails");
    }

    /**
     * 子图任一步失败时，父 subflow 步应 failed，并附带 childSteps 摘要。
     */
    @Test
    @Order(6)
    void execute_childStepFails_propagatesError() {
        begin("execute_childStepFails_propagatesError");
        handler = new SubflowNodeHandler(testFlowService, flowGraphRunner, failingDelayRegistry(), new GraphMigrator());
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
        log("childError=" + result.getError().getMessage());
        end("execute_childStepFails_propagatesError");
    }

    /**
     * 子流嵌套深度超过 {@link SubflowDepth#MAX_DEPTH} 时应拒绝执行。
     * 期望：failed；错误码 {@link FlowErrorCode#TF_SUBFLOW_NESTED}；消息含「嵌套超过上限」。
     */
    @Test
    @Order(7)
    void execute_depthExceeded_fails() {
        begin("execute_depthExceeded_fails");
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
        log("error=" + result.getError().getCode());
        end("execute_depthExceeded_fails");
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
