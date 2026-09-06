package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.InputNodeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 InputNodeHandler：首次执行返回 paused，不写 flow。
 * 边界：无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=InputNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InputNodeHandlerTest {

    private final InputNodeHandler handler = new InputNodeHandler();

    /**
     * 前提：配置了 fields 的 input 节点。
     * 期望：status=paused；ctx.flow 仍为空；错误码 TF_AWAIT_INPUT。
     */
    @Test
    @Order(1)
    @DisplayName("execute 返回 paused 且不写 flow")
    void execute_returnsPausedWithoutWritingFlow() {
        FlowRunContext ctx = FlowRunContext.builder().build();
        GraphNode node = GraphNode.builder()
                .id("in1")
                .type("input")
                .data(Map.of(
                        "name", "填码",
                        "prompt", "请填写",
                        "fields", List.of(Map.of("name", "captchaCode", "type", "text", "required", true))
                ))
                .build();

        StepResult result = handler.execute(ctx, node, null);

        assertEquals(StepResult.STATUS_PAUSED, result.getStatus());
        assertEquals("in1", result.getNodeId());
        assertTrue(ctx.getFlow().isEmpty());
        assertNotNull(result.getError());
        assertEquals("TF_AWAIT_INPUT", result.getError().getCode());
    }
}
