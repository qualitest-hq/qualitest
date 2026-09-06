package com.qualitest.flow.http;

import com.qualitest.api.result.DebugHttpForwardResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 HttpResponseBodyMediaSupport：裸媒体响应写出 bodyMedia 元数据（不存媒体字节）。
 * 边界：纯函数；文本/JSON 返回 null。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpResponseBodyMediaSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpResponseBodyMediaSupportTest {

    /**
     * 前提：bodyEncoding=base64，Content-Type=image/png，bodyText 含 [binary …] 提示。
     * 期望：kind=image，stored=false，bytes 从提示解析为 1234。
     */
    @Test
    @Order(1)
    @DisplayName("媒体响应写出 bodyMedia 且 stored=false")
    void buildMarker_media_returnsUnstoredMeta() {
        DebugHttpForwardResult result = DebugHttpForwardResult.success(
                200, "OK",
                Map.of("Content-Type", "image/png"),
                "[binary image/png · 1234 bytes]",
                DebugHttpForwardResult.BODY_ENCODING_BASE64,
                "aaaa");
        Map<String, Object> marker = HttpResponseBodyMediaSupport.buildMarker(result);
        assertNotNull(marker);
        assertEquals("image", marker.get("kind"));
        assertEquals("image/png", marker.get("mime"));
        assertEquals(1234L, marker.get("bytes"));
        assertEquals(false, marker.get("stored"));
        assertEquals(false, marker.get("truncated"));
    }

    /**
     * 前提：application/json 文本响应。
     * 期望：返回 null（不生成 bodyMedia）。
     */
    @Test
    @Order(2)
    @DisplayName("文本响应不写 bodyMedia")
    void buildMarker_text_returnsNull() {
        DebugHttpForwardResult result = DebugHttpForwardResult.success(
                200, "OK",
                Map.of("Content-Type", "application/json"),
                "{\"img\":\"abc\"}");
        assertNull(HttpResponseBodyMediaSupport.buildMarker(result));
    }

    /**
     * 前提：PDF 媒体响应且 bodyText 含「响应体已截断」。
     * 期望：truncated=true。
     */
    @Test
    @Order(3)
    @DisplayName("截断媒体标记 truncated")
    void buildMarker_truncated_flag() {
        DebugHttpForwardResult result = DebugHttpForwardResult.success(
                200, "OK",
                Map.of("Content-Type", "application/pdf"),
                "[binary application/pdf · 9999999 bytes]\n\n… 响应体已截断（>8388608 字节）",
                DebugHttpForwardResult.BODY_ENCODING_BASE64,
                "bb");
        Map<String, Object> marker = HttpResponseBodyMediaSupport.buildMarker(result);
        assertNotNull(marker);
        assertEquals("pdf", marker.get("kind"));
        assertEquals(true, marker.get("truncated"));
    }
}
