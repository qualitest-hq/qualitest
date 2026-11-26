package com.qualitest.flow.node;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.delivery.EntryKind;
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

        assertEquals(RunStatus.PAUSED.getCode(), result.getStatus());
        assertEquals("in1", result.getNodeId());
        assertTrue(ctx.getFlow().isEmpty());
        assertNotNull(result.getError());
        assertEquals("TF_AWAIT_INPUT", result.getError().getCode());
    }

    /**
     * 前提：开放 HTTP 已交付响应后，又走到 Input。
     * 期望：不再暂停（响应已发出），改为 failed 并说明不可续跑。
     */
    @Test
    @Order(2)
    @DisplayName("HTTP 已交付后再遇 Input 记为失败")
    void execute_afterHttpDelivery_failsInsteadOfPausing() {
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .delivered(true)
                .build();
        GraphNode node = GraphNode.builder().id("in1").type("input").data(Map.of()).build();

        StepResult result = handler.execute(ctx, node, null);

        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertNotNull(result.getError());
        assertEquals("TF_AWAIT_INPUT", result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("响应已交付"));
    }

    /**
     * 前提：子流已挂父后，又走到 Input。
     * 期望：仍按暂停处理（子流可续跑，不适用开放入站的限制）。
     */
    @Test
    @Order(3)
    @DisplayName("子流已交付后再遇 Input 仍暂停")
    void execute_afterSubflowDelivery_stillPauses() {
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.SUBFLOW.getCode())
                .delivered(true)
                .build();
        GraphNode node = GraphNode.builder().id("in1").type("input").data(Map.of()).build();

        StepResult result = handler.execute(ctx, node, null);

        assertEquals(RunStatus.PAUSED.getCode(), result.getStatus());
    }
}
