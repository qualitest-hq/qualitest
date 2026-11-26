package com.qualitest.flow.delivery;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测谁：SettleSupport.resolveFallbackOut 完整形判定。
 * 边界：子流业务对象含数值 status；HTTP 完整 out；缺 body 不当完整形。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SettleSupportFallbackShapeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettleSupportFallbackShapeTest {

    /**
     * 前提：子流入口 fallbackOut 为 {status:200, code, message}（无 body 键）。
     * 期望：整包进 out.body，不被当成完整 HTTP out。
     */
    @Test
    @Order(1)
    @DisplayName("子流业务对象含数值 status 仍包进 body")
    void businessObjectWithNumericStatus_wrapsAsBody() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", 200);
        fallback.put("code", "NO_RETURN");
        fallback.put("message", "未执行到返回节点");
        FlowRunContext ctx = ctxOnEntry("e1");
        OutPayload out = SettleSupport.resolveFallbackOut(ctx, graph("e1", FlowNodeType.SUBFLOW_ENTRY, fallback));
        assertNotNull(out);
        assertNull(out.getStatus());
        assertInstanceOf(Map.class, out.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) out.getBody();
        assertEquals(200, body.get("status"));
        assertEquals("NO_RETURN", body.get("code"));
    }

    /**
     * 前提：HTTP 入口 fallbackOut 含 status + body。
     * 期望：按完整 OutPayload 解析，status 保留在顶层。
     */
    @Test
    @Order(2)
    @DisplayName("完整 out 形 status+body 照挂")
    void fullOutShape_keepsTopLevelStatus() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", 500);
        fallback.put("headers", Map.of("Content-Type", "application/json;charset=UTF-8"));
        fallback.put("body", Map.of("code", "NO_RETURN"));
        FlowRunContext ctx = ctxOnEntry("e1");
        OutPayload out = SettleSupport.resolveFallbackOut(ctx, graph("e1", FlowNodeType.HTTP_ENTRY, fallback));
        assertNotNull(out);
        assertEquals(Integer.valueOf(500), out.getStatus());
        assertInstanceOf(Map.class, out.getBody());
    }

    private static FlowRunContext ctxOnEntry(String entryNodeId) {
        FlowRunContext ctx = new FlowRunContext();
        ctx.setEntryNodeId(entryNodeId);
        return ctx;
    }

    private static GraphJson graph(String entryId, FlowNodeType type, Map<String, Object> fallbackOut) {
        GraphNode n = new GraphNode();
        n.setId(entryId);
        n.setType(type.getCode());
        n.setName("入口");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fallbackOut", fallbackOut);
        n.setData(data);
        GraphJson g = new GraphJson();
        g.setNodes(List.of(n));
        return g;
    }
}
