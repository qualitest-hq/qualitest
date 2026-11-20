package com.qualitest.flow.inbound;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphInputField;
import com.qualitest.flow.model.GraphInputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 开放入站回包组装与必填入参检查单测。
 */
class OpenInboundOutFlushTest {

    /** out 只有 status、没有 body → 失败回包 */
    @Test
    @DisplayName("缺 body → 错包")
    void missingBody() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        var r = OpenInboundOutFlush.fromOut(out);
        assertFalse(r.ok());
        assertEquals(502, r.status());
    }

    /** 完整 out：带出 status、自定义头、body */
    @Test
    @DisplayName("合格 out → status/headers/body")
    void okFlush() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-Demo", "1");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "SUCCESS");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 201);
        out.put("headers", headers);
        out.put("body", body);
        var r = OpenInboundOutFlush.fromOut(out);
        assertTrue(r.ok());
        assertEquals(201, r.status());
        assertEquals("1", r.headers().get("X-Demo"));
        assertEquals(body, r.body());
    }

    /** 必填字段未注入时返回错误文案；补齐后通过 */
    @Test
    @DisplayName("必填入参缺失")
    void missingRequired() {
        GraphInputSpec spec = GraphInputSpec.builder()
                .fields(List.of(
                        GraphInputField.builder().name("orderId").required(true).build(),
                        GraphInputField.builder().name("opt").required(false).build()
                ))
                .build();
        FlowRunContext ctx = FlowRunContext.builder().build();
        assertNotNull(OpenInboundExecutionService.missingRequiredFields(ctx, spec));
        ctx.getIn().put("orderId", "O1");
        assertNull(OpenInboundExecutionService.missingRequiredFields(ctx, spec));
    }
}
