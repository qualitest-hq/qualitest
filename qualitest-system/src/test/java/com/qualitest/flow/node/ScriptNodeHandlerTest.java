package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.ScriptNodeHandler;
import com.qualitest.flow.script.ScriptRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ScriptNodeHandler：委托 ScriptRuntime 执行节点脚本并写入 StepResult。
 * 边界：真实 Graal；成功写入 / 空源码失败；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ScriptNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptNodeHandlerTest {

    private final ScriptNodeHandler handler = new ScriptNodeHandler(new ScriptRuntime(null));

    /**
     * 前提：javascript 源码 ctx.setFlow('demo','ok')。
     * 期望：passed；flow.demo=ok；result.script 含 language 与非空 writes。
     */
    @Test
    @Order(1)
    @DisplayName("执行：脚本成功写入 flow 并记录 writes")
    void execute_success() {
        GraphNode node = scriptNode("javascript", "ctx.setFlow('demo', 'ok');");
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, "e1");
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("ok", ctx.getFlow().get("demo"));
        assertNotNull(result.getScript());
        assertEquals("javascript", result.getScript().get("language"));
        assertFalse(((java.util.List<?>) result.getScript().get("writes")).isEmpty());
    }

    /**
     * 前提：source 为空串。
     * 期望：failed，错误码 TF_SCRIPT_ERROR。
     */
    @Test
    @Order(2)
    @DisplayName("执行：空源码失败并返回 TF_SCRIPT_ERROR")
    void execute_emptySource_fails() {
        GraphNode node = scriptNode("javascript", "");
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getError());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR.getCode(), result.getError().getCode());
    }

    private static GraphNode scriptNode(String language, String source) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "测试脚本");
        data.put("language", language);
        data.put("source", source);
        data.put("timeoutMs", 5000);
        return GraphNode.builder()
                .id("n-script")
                .type("script")
                .data(data)
                .build();
    }
}
