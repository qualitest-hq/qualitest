package com.qualitest.flow.node;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 AssertNodeHandler：rules 全过则 passed，任一条失败则 TF_ASSERT_FAILED。
 * 边界：flow / http.body 路径；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssertNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssertNodeHandlerTest {

    private final AssertNodeHandler handler = new AssertNodeHandler();

    /**
     * 前提：flow.code=0 且 http.body.code=0，两条 eq 规则。
     * 期望：passed；assertDetails 非空。
     */
    @Test
    @Order(1)
    @DisplayName("执行：全部规则通过时 passed")
    void execute_allRulesPass() {
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
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertNotNull(result.getAssertDetails());
    }

    /**
     * 前提：flow.code=1，规则要求 eq 0。
     * 期望：failed，错误码 TF_ASSERT_FAILED。
     */
    @Test
    @Order(2)
    @DisplayName("执行：规则失败时返回 TF_ASSERT_FAILED")
    void execute_ruleFails() {
        FlowRunContext ctx = FlowRunContext.builder().flow(Map.of("code", 1)).build();
        GraphNode node = GraphNode.builder()
                .id("a1")
                .type("assert")
                .data(Map.of("rules", List.of(
                        Map.of("left", "flow.code", "operator", "eq", "right", "0")
                )))
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED.getCode(), result.getError().getCode());
    }
}
