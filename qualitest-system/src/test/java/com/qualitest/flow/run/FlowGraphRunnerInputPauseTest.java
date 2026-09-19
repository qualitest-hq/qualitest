package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.node.impl.InputNodeHandler;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowGraphRunner 对 Input：首次执行 await_input 暂停，以及 COMPLETE_NODE 续跑。
 * 边界：内存小图；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerInputPauseTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerInputPauseTest {

    /**
     * 前提：单节点 input 图。
     * 期望：paused；pauseReason=await_input；步 status=paused。
     */
    @Test
    @Order(1)
    @DisplayName("Input 节点首次执行进入 await_input")
    void inputNode_pausesWithAwaitInput() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(GraphNode.builder()
                        .id("in1")
                        .type("input")
                        .data(Map.of(
                                "name", "填码",
                                "fields", List.of(Map.of("name", "code", "type", "text", "required", true))
                        ))
                        .build()))
                .edges(List.of())
                .build();

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(new InputNodeHandler()));
        FlowGraphRunner.Outcome outcome = new FlowGraphRunner().run(
                graph,
                FlowRunContext.builder().build(),
                registry,
                FlowNodePreExecuteHook.NONE,
                RunContinuation.fresh(),
                RunSnapshotPolicy.defaults()
        );

        assertTrue(outcome.isPaused());
        assertEquals(RunExecutionState.PAUSE_REASON_AWAIT_INPUT, outcome.getPauseReason());
        assertEquals("in1", outcome.getPauseNodeId());
        assertEquals(RunStatus.PAUSED.getCode(), outcome.getSteps().get(0).getStatus());
    }

    /**
     * 前提：COMPLETE_NODE 续跑，flow 已含 code；下一节点为 delay stub。
     * 期望：Input 步 passed 含 assigns；随后 delay 执行。
     */
    @Test
    @Order(2)
    @DisplayName("COMPLETE_NODE 落 passed 后前进")
    void completeNode_writesPassedAndContinues() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        GraphNode.builder()
                                .id("in1")
                                .type("input")
                                .data(Map.of("name", "填码", "fields", List.of(Map.of("name", "code", "type", "text"))))
                                .build(),
                        GraphNode.builder().id("d1").type("delay").data(Map.of("name", "等", "ms", 1)).build()
                ))
                .edges(List.of(
                        com.qualitest.flow.model.GraphEdge.builder().id("e1").source("in1").target("d1").build()
                ))
                .build();

        Map<String, Object> assignRow = new HashMap<>();
        assignRow.put("scope", "flow");
        assignRow.put("name", "code");
        assignRow.put("op", "set");
        assignRow.put("before", null);
        assignRow.put("after", "ok");
        List<Map<String, Object>> assigns = List.of(assignRow);

        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new InputNodeHandler(),
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(0)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));

        Map<String, Object> flow = new HashMap<>();
        flow.put("code", "ok");
        FlowGraphRunner.Outcome outcome = new FlowGraphRunner().run(
                graph,
                FlowRunContext.builder().flow(flow).build(),
                registry,
                FlowNodePreExecuteHook.NONE,
                RunContinuation.builder()
                        .startNodeId("in1")
                        .resumeMode(RunContinuation.ResumeMode.COMPLETE_NODE)
                        .completionAssigns(assigns)
                        .build(),
                RunSnapshotPolicy.defaults()
        );

        assertTrue(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals(RunStatus.PASSED.getCode(), outcome.getSteps().get(0).getStatus());
        assertEquals("in1", outcome.getSteps().get(0).getNodeId());
        assertEquals("d1", outcome.getSteps().get(1).getNodeId());
    }
}
