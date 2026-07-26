package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.ScriptNodeHandler;
import com.qualitest.flow.script.ScriptRuntime;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScriptNodeHandler} 单元测试：验证流程图中「脚本」类型节点的执行逻辑。
 * <p>
 * 被测对象从节点 data 读取 {@code language}、{@code source}、{@code timeoutMs}，
 * 委托 {@link ScriptRuntime} 执行脚本，并将执行结果（语言、写入记录、错误信息）写入 {@link StepResult}。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ScriptNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptNodeHandlerTest {

    private final ScriptNodeHandler handler = new ScriptNodeHandler(new ScriptRuntime(null));

    /**
     * 正常 JavaScript 脚本：{@code ctx.setFlow('demo', 'ok')} 写入 flow 变量。
     * 期望：步骤 passed；{@code flow.demo == 'ok'}；
     * {@code result.script} 含 language 与非空 writes 列表。
     */
    @Test
    @Order(1)
    void execute_success() {
        begin("execute_success");
        GraphNode node = scriptNode("javascript", "ctx.setFlow('demo', 'ok');");
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, "e1");
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("ok", ctx.getFlow().get("demo"));
        assertNotNull(result.getScript());
        assertEquals("javascript", result.getScript().get("language"));
        assertFalse(((java.util.List<?>) result.getScript().get("writes")).isEmpty());
        log("flow.demo=" + ctx.getFlow().get("demo"));
        end("execute_success");
    }

    /**
     * 脚本源码为空字符串时不应执行。
     * 期望：步骤 failed，错误码 {@link FlowErrorCode#TF_SCRIPT_ERROR}。
     */
    @Test
    @Order(2)
    void execute_emptySource_fails() {
        begin("execute_emptySource_fails");
        GraphNode node = scriptNode("javascript", "");
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getError());
        assertEquals(FlowErrorCode.TF_SCRIPT_ERROR.getCode(), result.getError().getCode());
        log("error=" + result.getError().getCode());
        end("execute_emptySource_fails");
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
