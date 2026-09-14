package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphWalker：找开始节点、resolveNext（condition target / 唯一出边）。
 * 边界：branch-graph 与 linear-run-graph 夹具；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphWalkerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphWalkerTest {

    /**
     * 前提：branch-graph；flow.code=0 命中 IF。
     * 期望：开始 n_start；branchTaken=b_if；next=n_ok，边 e_if。
     */
    @Test
    @Order(1)
    @DisplayName("condition 命中 IF 时下一跳 n_ok")
    void resolveNext_conditionIfBranch() {
        GraphJson graph = loadGraph("flow/branch-graph.json");
        GraphWalker walker = new GraphWalker(graph);
        assertEquals("n_start", walker.findUniqueStartNodeId());

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 0);

        GraphNode condNode = walker.getNode("n_cond");
        StepResult condResult = new ConditionNodeHandler().execute(ctx, condNode, "e1");
        assertEquals("b_if", condResult.getBranchTaken().get("branchId"));

        String next = walker.resolveNextNodeId(condNode, condResult);
        assertEquals("n_ok", next);
        assertEquals("e_if", walker.findEdgeId("n_cond", next));
    }

    /**
     * 前提：flow.code 初值 1；n_start 为 assign 清零后再进 condition。
     * 期望：assign 后 next=n_cond；condition 命中 IF → n_ok。
     */
    @Test
    @Order(2)
    @DisplayName("assign 清零后 condition 命中 IF")
    void resolveNext_assignThenConditionIf() {
        GraphJson graph = loadGraph("flow/branch-graph.json");
        GraphWalker walker = new GraphWalker(graph);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 1);

        GraphNode assignNode = walker.getNode("n_start");
        StepResult assignResult = new AssignNodeHandler().execute(ctx, assignNode, null);
        assertEquals(0L, assignResult.getFlowAfter().get("code"));

        String afterAssign = walker.resolveNextNodeId(assignNode, assignResult);
        assertEquals("n_cond", afterAssign);

        GraphNode condNode = walker.getNode("n_cond");
        StepResult condResult = new ConditionNodeHandler().execute(ctx, condNode, "e1");
        assertEquals("b_if", condResult.getBranchTaken().get("branchId"));

        String next = walker.resolveNextNodeId(condNode, condResult);
        assertEquals("n_ok", next);
    }

    /**
     * 前提：linear-run-graph，n1 仅一条出边。
     * 期望：resolveNext(n1)=n2。
     */
    @Test
    @Order(3)
    @DisplayName("唯一出边时 resolveNext 为 n2")
    void resolveNext_linearSingleOutEdge() {
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        GraphWalker walker = new GraphWalker(graph);
        GraphNode n1 = walker.getNode("n1");
        StepResult stub = StepResult.builder().status(StepResult.STATUS_PASSED).build();
        assertEquals("n2", walker.resolveNextNodeId(n1, stub));
    }

    /**
     * 前提：IF 无 target；flow.code=0 命中 IF。
     * 期望：resolveNext 返回 null（本流结束）；branchTaken 带 terminal=true。
     */
    @Test
    @Order(4)
    @DisplayName("无 target 的 IF 分支 resolveNext 为 null")
    void resolveNext_terminalIfBranchEndsFlow() {
        String json = """
                {
                  "nodes":[
                    {"id":"n_cond","type":"condition","position":{"x":0,"y":0},"data":{
                      "name":"c",
                      "branches":[
                        {"id":"b_if","kind":"if","conditions":[{"left":"flow.code","operator":"eq","right":"0"}]},
                        {"id":"b_else","kind":"else","target":"n_fail"}
                      ]
                    }}
                  ],
                  "edges":[]
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        GraphWalker walker = new GraphWalker(graph);
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 0);

        GraphNode condNode = walker.getNode("n_cond");
        StepResult condResult = new ConditionNodeHandler().execute(ctx, condNode, null);
        assertEquals("b_if", condResult.getBranchTaken().get("branchId"));
        assertEquals(Boolean.TRUE, condResult.getBranchTaken().get("terminal"));

        assertNull(walker.resolveNextNodeId(condNode, condResult));
    }

    /**
     * 前提：IF 无 target；flow.code=0 命中 IF（与 Order4 同口径，保留空白 target 字面量场景）。
     * 期望：resolveNext 返回 null（无出边即结束）。
     */
    @Test
    @Order(5)
    @DisplayName("无 target 的 IF 分支 resolveNext 为 null")
    void resolveNext_blankTargetIfBranchEndsFlow() {
        String json = """
                {
                  "nodes":[
                    {"id":"n_cond","type":"condition","position":{"x":0,"y":0},"data":{
                      "name":"c",
                      "branches":[
                        {"id":"b_if","kind":"if","conditions":[{"left":"flow.code","operator":"eq","right":"0"}]},
                        {"id":"b_else","kind":"else","target":"n_fail"}
                      ]
                    }}
                  ],
                  "edges":[]
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        GraphWalker walker = new GraphWalker(graph);
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 0);

        GraphNode condNode = walker.getNode("n_cond");
        StepResult condResult = new ConditionNodeHandler().execute(ctx, condNode, null);
        assertEquals("b_if", condResult.getBranchTaken().get("branchId"));
        assertEquals(Boolean.TRUE, condResult.getBranchTaken().get("terminal"));

        assertNull(walker.resolveNextNodeId(condNode, condResult));
    }

    /** 从 classpath 加载夹具图 JSON */
    private static GraphJson loadGraph(String path) {
        try (InputStream in = GraphWalkerTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GraphJson.parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
