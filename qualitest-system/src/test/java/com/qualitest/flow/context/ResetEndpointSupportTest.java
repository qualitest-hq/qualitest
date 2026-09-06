package com.qualitest.flow.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 ResetEndpointSupport：由环境 URL 拼出 /test-support 根地址。
 * 边界：纯函数；覆盖 http 补路径、斜杠归一、JSON 多模块取默认、空基址。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ResetEndpointSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResetEndpointSupportTest {

    /**
     * 前提：基址为普通 http，无 path。
     * 期望：末尾补上 /test-support。
     */
    @Test
    @Order(1)
    @DisplayName("resolve：普通 http 基址补上 /test-support")
    void resolve_httpBase_appendsTestSupport() {
        assertEquals(
                "http://localhost:8801/test-support",
                ResetEndpointSupport.resolve("http://localhost:8801")
        );
    }

    /**
     * 前提：基址末尾带斜杠。
     * 期望：归一后仍得到单一 /test-support，无双斜杠。
     */
    @Test
    @Order(2)
    @DisplayName("resolve：末尾斜杠归一后仍补 /test-support")
    void resolve_trimsTrailingSlash() {
        assertEquals(
                "http://localhost:8801/test-support",
                ResetEndpointSupport.resolve("http://localhost:8801/")
        );
    }

    /**
     * 前提：环境 URL 为多模块 JSON，含「默认模块」。
     * 期望：取默认模块基址再补 /test-support。
     */
    @Test
    @Order(3)
    @DisplayName("resolve：多模块 JSON 取默认模块基址")
    void resolve_jsonModuleUsesDefaultModule() {
        String json = "{\"默认模块\":\"http://demo:8801\",\"其他\":\"http://other:9000\"}";
        assertEquals("http://demo:8801/test-support", ResetEndpointSupport.resolve(json));
    }

    /**
     * 前提：joinBaseAndPath 基址为空。
     * 期望：返回空串。
     */
    @Test
    @Order(4)
    @DisplayName("join：基址为空时返回空串")
    void joinBaseAndPath_handlesEmptyBase() {
        assertEquals("", ResetEndpointSupport.joinBaseAndPath("", "/test-support"));
    }
}
