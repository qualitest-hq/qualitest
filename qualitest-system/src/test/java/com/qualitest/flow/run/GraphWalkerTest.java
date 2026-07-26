package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GraphWalker} 单元测试：验证流程图遍历器的节点定位与下一跳解析。
 * <p>
 * 被测对象负责：从 {@link GraphJson} 中定位唯一开始节点、按节点类型解析下一跳
 * （普通节点沿出边、condition 节点沿 branchTaken 指定的 target），并查找对应边 id。
 * 本测试结合真实 {@link ConditionNodeHandler}、{@link AssignNodeHandler} 产生 branchTaken。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=GraphWalkerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphWalkerTest {

    /**
     * 分支图 {@code branch-graph.json}：{@code flow.code=0} 命中 IF 分支。
     * 期望：开始节点 n_start；condition 步 branchTaken=b_if；
     * resolveNext 返回 n_ok，入边 id=e_if。
     */
    @Test
    @Order(1)
    void resolveNext_conditionIfBranch() {
        begin("resolveNext_conditionIfBranch");
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
        log("next=" + next + " edge=" + walker.findEdgeId("n_cond", next));
        end("resolveNext_conditionIfBranch");
    }

    /**
     * assign 节点将 {@code flow.code} 从 1 清零为 0，随后 condition 仍按新值求值。
     * 期望：assign 后下一跳为 n_cond；condition 命中 IF → n_ok。
     */
    @Test
    @Order(2)
    void resolveNext_assignThenConditionIf() {
        begin("resolveNext_assignThenConditionIf");
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
        log("afterAssign=" + afterAssign + " next=" + next);
        end("resolveNext_assignThenConditionIf");
    }

    /**
     * 线性图每个节点仅有一条出边，resolveNext 应沿唯一出边前进。
     * 期望：n1 → n2。
     */
    @Test
    @Order(3)
    void resolveNext_linearSingleOutEdge() {
        begin("resolveNext_linearSingleOutEdge");
        GraphJson graph = loadGraph("flow/linear-run-graph.json");
        GraphWalker walker = new GraphWalker(graph);
        GraphNode n1 = walker.getNode("n1");
        StepResult stub = StepResult.builder().status(StepResult.STATUS_PASSED).build();
        assertEquals("n2", walker.resolveNextNodeId(n1, stub));
        log("n1 -> n2");
        end("resolveNext_linearSingleOutEdge");
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
