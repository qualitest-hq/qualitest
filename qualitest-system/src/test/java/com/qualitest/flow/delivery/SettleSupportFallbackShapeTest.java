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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测谁：SettleSupport 的兜底形态——子流业务对象包成 {body: …}、存量完整 out 形照挂；HTTP 完整形解析。
 * 边界：纯 Map 形态判定，不跑图。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SettleSupportFallbackShapeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettleSupportFallbackShapeTest {

    /**
     * 前提：子流入口 fallbackOut 为 {status:200, code, message}（无 body 键）。
     * 期望：规范成 {body: <该对象>}，不在顶层出现 status/headers。
     */
    @Test
    @Order(1)
    @DisplayName("子流业务对象兜底包成 {body: …}")
    void subflowBusinessFallback_wrapsAsBodyPackage() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", 200);
        fallback.put("code", "NO_RETURN");
        fallback.put("message", "未执行到返回节点");
        FlowRunContext ctx = ctxOnEntry("e1");

        Map<String, Object> packageMap = SettleSupport.resolveSubflowFallbackPackage(
                ctx, graph("e1", FlowNodeType.SUBFLOW_ENTRY, fallback));

        assertFalse(packageMap.containsKey("status"));
        assertFalse(packageMap.containsKey("headers"));
        assertInstanceOf(Map.class, packageMap.get("body"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) packageMap.get("body");
        assertEquals(200, body.get("status"));
        assertEquals("NO_RETURN", body.get("code"));
    }

    /**
     * 前提：子流入口 fallbackOut 是存量完整 out 形（status + headers + body）。
     * 期望：原样整包挂，不再包 body。
     */
    @Test
    @Order(2)
    @DisplayName("子流存量完整 out 形照挂")
    void subflowFullOutFallback_keptAsIs() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", 500);
        fallback.put("headers", Map.of("Content-Type", "application/json;charset=UTF-8"));
        fallback.put("body", Map.of("code", "NO_RETURN"));
        FlowRunContext ctx = ctxOnEntry("e1");

        Map<String, Object> packageMap = SettleSupport.resolveSubflowFallbackPackage(
                ctx, graph("e1", FlowNodeType.SUBFLOW_ENTRY, fallback));

        assertEquals(500, packageMap.get("status"));
        assertInstanceOf(Map.class, packageMap.get("body"));
    }

    /**
     * 前提：子流入口未配置 fallbackOut（校验会拦，运行时兜底安全网）。
     * 期望：{body: {code: NO_RETURN, message: …}}。
     */
    @Test
    @Order(3)
    @DisplayName("子流未配兜底时用默认业务对象包 body")
    void subflowMissingFallback_defaultBodyPackage() {
        FlowRunContext ctx = ctxOnEntry("e1");

        Map<String, Object> packageMap = SettleSupport.resolveSubflowFallbackPackage(
                ctx, graph("e1", FlowNodeType.SUBFLOW_ENTRY, null));

        assertNull(packageMap.get("status"));
        assertInstanceOf(Map.class, packageMap.get("body"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) packageMap.get("body");
        assertEquals("NO_RETURN", body.get("code"));
    }

    /**
     * 前提：HTTP 入口 fallbackOut 含 status + body。
     * 期望：按完整 OutPayload 解析，status 保留在顶层。
     */
    @Test
    @Order(4)
    @DisplayName("完整 out 形 status+body 照挂")
    void fullOutShape_keepsTopLevelStatus() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", 500);
        fallback.put("headers", Map.of("Content-Type", "application/json;charset=UTF-8"));
        fallback.put("body", Map.of("code", "NO_RETURN"));
        FlowRunContext ctx = ctxOnEntry("e1");

        OutPayload out = SettleSupport.resolveFallbackOut(
                ctx, graph("e1", FlowNodeType.HTTP_ENTRY, fallback));

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
        if (fallbackOut != null) {
            data.put("fallbackOut", fallbackOut);
        }
        n.setData(data);
        GraphJson g = new GraphJson();
        g.setNodes(List.of(n));
        return g;
    }
}
