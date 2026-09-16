package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 AiToolTraceSupport：工具轨迹脱敏、截断、ok 判定与组装。
 * 边界：纯静态 / Recorder，无 IO。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiToolTraceSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiToolTraceSupportTest {

    /**
     * 前提：args 含 password、Authorization Bearer、嵌套 token。
     * 期望：敏感字段与 Bearer 值均为 ***。
     */
    @Test
    @Order(1)
    @DisplayName("敏感字段与 Bearer 打码")
    void sanitizePayload_redactsSecrets() {
        String raw = "{\"password\":\"secret123\",\"Authorization\":\"Bearer abc.def\","
                + "\"headers\":{\"token\":\"t1\"},\"note\":\"ok\"}";
        Object sanitized = AiToolTraceSupport.sanitizePayload(raw);
        assertInstanceOf(JSONObject.class, sanitized);
        JSONObject obj = (JSONObject) sanitized;
        assertEquals(AiToolTraceSupport.REDACTED, obj.getString("password"));
        assertEquals(AiToolTraceSupport.REDACTED, obj.getString("Authorization"));
        assertEquals(AiToolTraceSupport.REDACTED, obj.getJSONObject("headers").getString("token"));
        assertEquals("ok", obj.getString("note"));
    }

    /**
     * 前提：纯文字段超过 2KB。
     * 期望：截断并以省略号结尾。
     */
    @Test
    @Order(2)
    @DisplayName("超长字符串截断")
    void sanitizePayload_truncatesLongString() {
        String longText = "x".repeat(AiToolTraceSupport.MAX_FIELD_CHARS + 50);
        Object sanitized = AiToolTraceSupport.sanitizePayload("\"" + longText + "\"");
        assertInstanceOf(String.class, sanitized);
        String s = (String) sanitized;
        assertTrue(s.length() <= AiToolTraceSupport.MAX_FIELD_CHARS + 1);
        assertTrue(s.endsWith("…"));
    }

    /**
     * 前提：含 error / ok=false / 正常 items。
     * 期望：前两者 ok=false，后者 ok=true。
     */
    @Test
    @Order(3)
    @DisplayName("工具结果 ok 判定")
    void isCallOk_detectsFailure() {
        assertFalse(AiToolTraceSupport.isCallOk("{\"error\":\"校验未通过\"}"));
        assertFalse(AiToolTraceSupport.isCallOk("{\"ok\":false}"));
        assertFalse(AiToolTraceSupport.isCallOk("{\"is_error\":true}"));
        assertTrue(AiToolTraceSupport.isCallOk("{\"items\":[]}"));
        assertTrue(AiToolTraceSupport.isCallOk(null));
    }

    /**
     * 前提：连续记录 2 次，maxCalls=1。
     * 期望：仅保留 1 条，truncated=true，steps/max 写入。
     */
    @Test
    @Order(4)
    @DisplayName("Recorder 条数触顶截断")
    void recorder_truncatesWhenMaxCallsReached() {
        AiToolTraceSupport.Recorder recorder = new AiToolTraceSupport.Recorder();
        recorder.record("search_apis", "{\"keyword\":\"a\"}", "{\"items\":[]}", 3, 1);
        recorder.record("submit_edge", "{\"label\":\"else\"}", "{\"error\":\"fail\"}", 5, 1);

        JSONObject trace = recorder.build(2, 1);
        assertEquals(2, trace.getIntValue("stepsUsed"));
        assertEquals(1, trace.getIntValue("maxSteps"));
        assertTrue(trace.getBooleanValue("truncated"));
        JSONArray calls = trace.getJSONArray("calls");
        assertEquals(1, calls.size());
        assertEquals("search_apis", calls.getJSONObject(0).getString("name"));
        assertTrue(calls.getJSONObject(0).getBooleanValue("ok"));
    }
}
