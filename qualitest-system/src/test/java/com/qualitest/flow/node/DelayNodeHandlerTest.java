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
 * {@link DelayNodeHandler} 单元测试：验证「延时等待」节点的阻塞行为。
 * <p>
 * 被测对象读取节点 data 中的 {@code ms} 字段，调用 {@code Thread.sleep} 阻塞指定毫秒数，
 * 然后返回 passed 状态的 {@link StepResult}，{@code durationMs} 应反映实际等待时长。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=DelayNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DelayNodeHandlerTest {

    private final DelayNodeHandler handler = new DelayNodeHandler();

    /**
     * 配置延时 10ms，执行前后记录墙钟时间。
     * 期望：步骤 passed；{@code result.durationMs >= 10}；实际 elapsed >= 10ms。
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
