package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AbstractStubNodeHandler;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.validate.FlowNodeType;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowGraphRunner：内存图遍历、线性多步、步数上限、alwaysRun 失败后继续。
 * 边界：失败后未标记节点 bypassed；分支扇出汇合去重；continued_failed 不掩盖首错；回边遇已访问则停；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphRunnerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphRunnerTest {

    private FlowGraphRunner runner;
    private NodeHandlerRegistry registry;
    private FlowRunContext ctx;

    @BeforeEach
    void setUp() {
        runner = new FlowGraphRunner();
        registry = passingDelayRegistry();
        ctx = FlowRunContext.builder().flow(new HashMap<>()).build();
    }

    /**
     * 前提：单 delay 节点图，registry 全部通过。
     * 期望：run 返回 passed，执行 1 步。
     */
    @Test
    @Order(1)
    @DisplayName("单 delay 节点图执行通过")
    void run_singleDelayNode_passes() {
        String json = """
                {
                  "nodes":[{"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"等待","ms":1}}],
                  "edges":[],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
    }

    /**
     * 前提：线性两 delay 节点图，registry 全部通过。
     * 期望：run 返回 passed，按边顺序执行 d1→d2 两步。
     */
    @Test
    @Order(2)
    @DisplayName("线性两节点按边顺序通过")
    void run_linearTwoNodes_passes() {
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("d1", outcome.getSteps().get(0).getNodeId());
        assertEquals("d2", outcome.getSteps().get(1).getNodeId());
    }

    /**
     * 前提：线性两节点图，首步 d1 handler 返回 failed；d2 未标 alwaysRun。
     * 期望：d1 failed 后进入失败后继续，d2 记 bypassed；outcome 失败且主错误为「首步失败」。
     */
    @Test
    @Order(3)
    @DisplayName("首步失败后后续未标记节点记 bypassed")
    void run_failedStep_bypassesUnmarkedFollowers() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        boolean fail = "d1".equals(node.getId());
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(fail ? RunStatus.FAILED.getCode() : RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .error(fail ? StepError.of(FlowErrorCode.TF_STEP_ERROR, "首步失败") : null)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals(RunStatus.FAILED.getCode(), outcome.getSteps().get(0).getStatus());
        assertEquals(RunStatus.BYPASSED.getCode(), outcome.getSteps().get(1).getStatus());
        assertEquals("d2", outcome.getSteps().get(1).getNodeId());
        assertNotNull(outcome.getError());
        assertEquals("首步失败", outcome.getError().getMessage());
    }

    /**
     * 前提：图含 subflow 节点，registry 注册 subflow 桩 handler。
     * 期望：run 返回 passed，执行 1 步 subflow 节点。
     */
    @Test
    @Order(4)
    @DisplayName("含 subflow 节点的图可执行")
    void run_graphWithSubflowNode_executes() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.SUBFLOW) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("subflow")
                                .nodeName("嵌套")
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        String json = loadResource("flow/subflow-nested-graph.json");
        GraphJson graph = GraphJson.parse(json);
        FlowGraphRunner.Outcome outcome = runner.run(graph, ctx, registry);
        assertTrue(outcome.isPassed());
        assertEquals(1, outcome.getSteps().size());
    }

    /**
     * 前提：线性两节点，场景策略 maxSteps=1。
     * 期望：先执行 d1；第二步超限时为 d2 合成 failed 步骤，错误含节点名与上限。
     */
    @Test
    @Order(5)
    @DisplayName("超过 maxSteps 时合成失败步并指出节点")
    void run_exceedsMaxSteps_failsWithSyntheticStep() {
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        RunSnapshotPolicy policy = RunSnapshotPolicy.builder().maxSteps(1).build();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph, ctx, registry,
                FlowNodePreExecuteHook.NONE,
                RunContinuation.fresh(),
                policy,
                null);
        assertFalse(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        StepResult limitStep = outcome.getSteps().get(1);
        assertEquals("d2", limitStep.getNodeId());
        assertEquals(RunStatus.FAILED.getCode(), limitStep.getStatus());
        assertNotNull(limitStep.getError());
        assertEquals(FlowErrorCode.TF_RUN_STEP_LIMIT.getCode(), limitStep.getError().getCode());
        assertTrue(limitStep.getError().getMessage().contains("超过单次运行步数上限（1）"));
        assertTrue(limitStep.getError().getMessage().contains("d2"));
        assertEquals(FlowErrorCode.TF_RUN_STEP_LIMIT.getCode(), outcome.getError().getCode());
    }

    /**
     * 前提：线性两节点；注册每步完成回调。
     * 期望：每步完成后立刻回调，顺序与最终 steps 一致。
     */
    @Test
    @Order(6)
    @DisplayName("onStep 在每步完成后立即回调")
    void run_onStep_invokedPerStep() {
        GraphJson graph = GraphJson.parse(loadResource("flow/runner-linear-two-delay.json"));
        java.util.ArrayList<String> seen = new java.util.ArrayList<>();
        FlowGraphRunner.Outcome outcome = runner.run(
                graph, ctx, registry,
                FlowNodePreExecuteHook.NONE,
                RunContinuation.fresh(),
                RunSnapshotPolicy.defaults(),
                step -> {
                    if (step != null && step.getNodeId() != null) {
                        seen.add(step.getNodeId());
                    }
                });
        assertTrue(outcome.isPassed());
        assertEquals(List.of("d1", "d2"), seen);
        assertEquals(2, outcome.getSteps().size());
    }

    /**
     * 前提：d1 失败 → mid 未标记 → clean 标 alwaysRun；delay 桩。
     * 期望：Run 失败且主错误来自 d1；mid bypassed；clean 为 continued。
     */
    @Test
    @Order(7)
    @DisplayName("失败后 alwaysRun 记 continued")
    void run_alwaysRun_executesAfterFailure() {
        registry = delayRegistryFailing("d1");
        String json = """
                {
                  "nodes":[
                    {"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"业务","ms":1}},
                    {"id":"mid","type":"delay","position":{"x":100,"y":0},"data":{"name":"中间","ms":1}},
                    {"id":"clean","type":"delay","position":{"x":200,"y":0},"data":{"name":"清理","ms":1,"alwaysRun":true}}
                  ],
                  "edges":[
                    {"id":"e1","source":"d1","target":"mid"},
                    {"id":"e2","source":"mid","target":"clean"}
                  ],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        FlowGraphRunner.Outcome outcome = runner.run(GraphJson.parse(json), ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals(3, outcome.getSteps().size());
        assertEquals(RunStatus.FAILED.getCode(), outcome.getSteps().get(0).getStatus());
        assertEquals(RunStatus.BYPASSED.getCode(), outcome.getSteps().get(1).getStatus());
        assertEquals("mid", outcome.getSteps().get(1).getNodeId());
        assertEquals(RunStatus.CONTINUED.getCode(), outcome.getSteps().get(2).getStatus());
        assertEquals("clean", outcome.getSteps().get(2).getNodeId());
        assertEquals("首步失败", outcome.getError().getMessage());
    }

    /**
     * 前提：d1 失败 → c1/c2 均 alwaysRun，且 c1 故意失败。
     * 期望：主错误仍是 d1；c1 continued_failed 后 c2 仍为 continued；Run 不暂停。
     */
    @Test
    @Order(8)
    @DisplayName("continued_failed 不掩盖首错且继续后续 alwaysRun")
    void run_alwaysRun_continuedFailedKeepsPrimaryError() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        boolean fail = "d1".equals(node.getId()) || "c1".equals(node.getId());
                        String msg = "d1".equals(node.getId()) ? "业务失败" : "清理失败";
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(fail ? RunStatus.FAILED.getCode() : RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .error(fail ? StepError.of(FlowErrorCode.TF_STEP_ERROR, msg) : null)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        String json = """
                {
                  "nodes":[
                    {"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"业务","ms":1}},
                    {"id":"c1","type":"delay","position":{"x":100,"y":0},"data":{"name":"清1","ms":1,"alwaysRun":true}},
                    {"id":"c2","type":"delay","position":{"x":200,"y":0},"data":{"name":"清2","ms":1,"alwaysRun":true}}
                  ],
                  "edges":[
                    {"id":"e1","source":"d1","target":"c1"},
                    {"id":"e2","source":"c1","target":"c2"}
                  ],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        FlowGraphRunner.Outcome outcome = runner.run(GraphJson.parse(json), ctx, registry);
        assertFalse(outcome.isPassed());
        assertFalse(outcome.isPaused());
        assertEquals(3, outcome.getSteps().size());
        assertEquals("业务失败", outcome.getError().getMessage());
        assertEquals(RunStatus.CONTINUED_FAILED.getCode(), outcome.getSteps().get(1).getStatus());
        assertEquals("c1", outcome.getSteps().get(1).getNodeId());
        assertEquals(RunStatus.CONTINUED.getCode(), outcome.getSteps().get(2).getStatus());
        assertEquals("c2", outcome.getSteps().get(2).getNodeId());
    }

    /**
     * 前提：d1 失败 → condition 双分支均汇合到 clean(alwaysRun)。
     * 期望：condition 与分支中段 bypassed；clean 只执行一次且为 continued。
     */
    @Test
    @Order(9)
    @DisplayName("分支前失败扇出汇合清理只跑一次")
    void run_alwaysRun_fanOutJoinRunsCleanupOnce() {
        registry = delayRegistryFailing("d1");
        String json = """
                {
                  "nodes":[
                    {"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"业务","ms":1}},
                    {"id":"cond","type":"condition","position":{"x":100,"y":0},"data":{"name":"分支","branches":[
                      {"id":"b_a","kind":"if","target":"a","conditions":[{"left":"1","operator":"eq","right":"1"}]},
                      {"id":"b_b","kind":"else","target":"b","conditions":[]}
                    ]}},
                    {"id":"a","type":"delay","position":{"x":200,"y":-40},"data":{"name":"A","ms":1}},
                    {"id":"b","type":"delay","position":{"x":200,"y":40},"data":{"name":"B","ms":1}},
                    {"id":"clean","type":"delay","position":{"x":300,"y":0},"data":{"name":"清理","ms":1,"alwaysRun":true}}
                  ],
                  "edges":[
                    {"id":"e0","source":"d1","target":"cond"},
                    {"id":"ea","source":"cond","target":"a"},
                    {"id":"eb","source":"cond","target":"b"},
                    {"id":"eac","source":"a","target":"clean"},
                    {"id":"ebc","source":"b","target":"clean"}
                  ],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        FlowGraphRunner.Outcome outcome = runner.run(GraphJson.parse(json), ctx, registry);
        assertFalse(outcome.isPassed());
        long cleanContinued = outcome.getSteps().stream()
                .filter(s -> "clean".equals(s.getNodeId()) && RunStatus.CONTINUED.matches(s.getStatus()))
                .count();
        assertEquals(1, cleanContinued);
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "cond".equals(s.getNodeId()) && RunStatus.BYPASSED.matches(s.getStatus())));
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "a".equals(s.getNodeId()) && RunStatus.BYPASSED.matches(s.getStatus())));
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "b".equals(s.getNodeId()) && RunStatus.BYPASSED.matches(s.getStatus())));
        assertEquals("首步失败", outcome.getError().getMessage());
    }

    /**
     * 前提：start 通过 → condition 走 A → a 失败 → clean alwaysRun；B 支未进入。
     * 期望：只清理 A 支后续；b 不会出现在步骤中。
     */
    @Test
    @Order(10)
    @DisplayName("分支内失败只清理本支后续")
    void run_alwaysRun_failureInsideBranch_onlyCleansThatBranch() {
        registry = new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        boolean fail = "a".equals(node.getId());
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(fail ? RunStatus.FAILED.getCode() : RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .error(fail ? StepError.of(FlowErrorCode.TF_STEP_ERROR, "A失败") : null)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                },
                new AbstractStubNodeHandler(FlowNodeType.CONDITION) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        java.util.Map<String, Object> branchTaken = new HashMap<>();
                        branchTaken.put("branchId", "b_a");
                        branchTaken.put("kind", "if");
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("condition")
                                .nodeName(node.getId())
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .branchTaken(branchTaken)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
        String json = """
                {
                  "nodes":[
                    {"id":"start","type":"delay","position":{"x":0,"y":0},"data":{"name":"开始","ms":1}},
                    {"id":"cond","type":"condition","position":{"x":100,"y":0},"data":{"name":"分支","branches":[
                      {"id":"b_a","kind":"if","target":"a","conditions":[{"left":"1","operator":"eq","right":"1"}]},
                      {"id":"b_b","kind":"else","target":"b","conditions":[]}
                    ]}},
                    {"id":"a","type":"delay","position":{"x":200,"y":-40},"data":{"name":"A","ms":1}},
                    {"id":"b","type":"delay","position":{"x":200,"y":40},"data":{"name":"B","ms":1}},
                    {"id":"clean","type":"delay","position":{"x":300,"y":0},"data":{"name":"清理","ms":1,"alwaysRun":true}}
                  ],
                  "edges":[
                    {"id":"e0","source":"start","target":"cond"},
                    {"id":"ea","source":"cond","target":"a"},
                    {"id":"eb","source":"cond","target":"b"},
                    {"id":"eac","source":"a","target":"clean"},
                    {"id":"ebc","source":"b","target":"clean"}
                  ],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        FlowGraphRunner.Outcome outcome = runner.run(GraphJson.parse(json), ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals("A失败", outcome.getError().getMessage());
        assertTrue(outcome.getSteps().stream().noneMatch(s -> "b".equals(s.getNodeId())));
        assertTrue(outcome.getSteps().stream().anyMatch(s ->
                "clean".equals(s.getNodeId()) && RunStatus.CONTINUED.matches(s.getStatus())));
        assertEquals(4, outcome.getSteps().size());
    }

    /**
     * 前提：d1 失败 → clean alwaysRun 自环出边回到自身。
     * 期望：clean 只执行一次（continued）后因已访问终止，不打满步数。
     */
    @Test
    @Order(11)
    @DisplayName("失败后继续回边碰到已访问节点则停止")
    void run_alwaysRun_backEdgeStopsOnRevisit() {
        registry = delayRegistryFailing("d1");
        String json = """
                {
                  "nodes":[
                    {"id":"d1","type":"delay","position":{"x":0,"y":0},"data":{"name":"业务","ms":1}},
                    {"id":"clean","type":"delay","position":{"x":100,"y":0},"data":{"name":"清理","ms":1,"alwaysRun":true}}
                  ],
                  "edges":[
                    {"id":"e1","source":"d1","target":"clean"},
                    {"id":"eloop","source":"clean","target":"clean"}
                  ],
                  "meta":{"activeScenarioId":"s1","scenarios":[{"id":"s1","name":"d","testProjectEnvId":"","flowSeed":{}}]}
                }
                """;
        FlowGraphRunner.Outcome outcome = runner.run(GraphJson.parse(json), ctx, registry);
        assertFalse(outcome.isPassed());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("clean", outcome.getSteps().get(1).getNodeId());
        assertEquals(RunStatus.CONTINUED.getCode(), outcome.getSteps().get(1).getStatus());
    }

    /** delay 桩：指定节点 id 返回失败「首步失败」，其余通过 */
    private static NodeHandlerRegistry delayRegistryFailing(String failNodeId) {
        return new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        boolean fail = failNodeId.equals(node.getId());
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(fail ? RunStatus.FAILED.getCode() : RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .error(fail ? StepError.of(FlowErrorCode.TF_STEP_ERROR, "首步失败") : null)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
    }

    /** delay 桩：全部节点通过 */
    private static NodeHandlerRegistry passingDelayRegistry() {
        return new NodeHandlerRegistry(List.of(
                new AbstractStubNodeHandler(FlowNodeType.DELAY) {
                    @Override
                    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
                        return StepResult.builder()
                                .nodeId(node.getId())
                                .nodeType("delay")
                                .nodeName(node.getId())
                                .status(RunStatus.PASSED.getCode())
                                .durationMs(1)
                                .flowAfter(new HashMap<>(ctx.getFlow()))
                                .build();
                    }
                }
        ));
    }

    private static String loadResource(String path) {
        InputStream in = FlowGraphRunnerTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing resource: " + path);
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
