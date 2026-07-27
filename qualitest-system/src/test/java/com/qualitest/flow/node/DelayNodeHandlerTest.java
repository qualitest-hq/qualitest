package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.DelayNodeHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 DelayNodeHandler：按 data.ms 阻塞后返回 passed。
 * 边界：真实 sleep（短毫秒）；存量 begin/end 保留。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=DelayNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DelayNodeHandlerTest {

    private final DelayNodeHandler handler = new DelayNodeHandler();

    /**
     * 前提：节点 ms=10。
     * 期望：status=passed；durationMs 与墙钟均 ≥10。
     */
    @Test
    @Order(1)
    void execute_waitsAndPasses() {
        begin("execute_waitsAndPasses");
        GraphNode node = GraphNode.builder()
                .id("d1")
                .type("delay")
                .data(Map.of("ms", 10))
                .build();
        long t0 = System.currentTimeMillis();
        StepResult result = handler.execute(FlowRunContext.builder().build(), node, null);
        long elapsed = System.currentTimeMillis() - t0;

        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertTrue(result.getDurationMs() >= 10);
        assertTrue(elapsed >= 10);

        log("status=" + result.getStatus() + " durationMs=" + result.getDurationMs() + " wallMs=" + elapsed);
        end("execute_waitsAndPasses");
    }
}
