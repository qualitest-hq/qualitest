package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AssertNodeHandler} 单元测试：验证「断言」节点对比较规则的批量求值。
 * <p>
 * 被测对象读取节点 data 中的 {@code rules} 数组，逐条调用 {@link com.qualitest.flow.context.CompareRuleEvaluator#eval}；
 * 全部通过则步骤 passed，任一失败则整步 failed（错误码 {@link FlowErrorCode#TF_ASSERT_FAILED}），
 * 并在 {@code result.assertDetails} 中记录每条规则的求值结果。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AssertNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssertNodeHandlerTest {

    private final AssertNodeHandler handler = new AssertNodeHandler();

    /**
     * 两条规则均成立：{@code flow.code eq 0} 与 {@code http.body.code eq 0}。
     * 期望：步骤 passed；{@code result.assertDetails} 非空。
     */
    @Test
    @Order(1)
    void execute_allRulesPass() {
        begin("execute_allRulesPass");
        FlowRunContext ctx = FlowRunContext.builder()
                .flow(Map.of("code", 0))
                .lastResponse(FlowRunContext.HttpResponseSnapshot.builder()
                        .status(200)
                        .body(Map.of("code", 0))
                        .build())
                .build();

        GraphNode node = GraphNode.builder()
                .id("a1")
                .type("assert")
                .data(Map.of("rules", List.of(
                        Map.of("left", "flow.code", "operator", "eq", "right", "0"),
                        Map.of("left", "http.body.code", "operator", "eq", "right", "0")
                )))
                .build();

        StepResult result = handler.execute(ctx, node, "e1");
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertNotNull(result.getAssertDetails());
        log("status=" + result.getStatus() + " rules=" + result.getAssertDetails().get("rules"));
        end("execute_allRulesPass");
    }

    /**
     * {@code flow.code == 1} 不满足 {@code flow.code eq 0}。
     * 期望：步骤 failed，错误码 {@link FlowErrorCode#TF_ASSERT_FAILED}。
     */
    @Test
    @Order(2)
    void execute_ruleFails() {
        begin("execute_ruleFails");
        FlowRunContext ctx = FlowRunContext.builder().flow(Map.of("code", 1)).build();
        GraphNode node = GraphNode.builder()
                .id("a1")
                .type("assert")
                .data(Map.of("rules", List.of(
                        Map.of("left", "flow.code", "operator", "eq", "right", "0")
                )))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED.getCode(), result.getError().getCode());
        log("status=" + result.getStatus() + " error=" + result.getError().getCode());
        end("execute_ruleFails");
    }
}
