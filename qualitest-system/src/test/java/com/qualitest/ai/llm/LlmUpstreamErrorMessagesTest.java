package com.qualitest.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测上游异常到用户可见中文文案的转换。
 * 边界：纯静态逻辑，无网络、无 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=LlmUpstreamErrorMessagesTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LlmUpstreamErrorMessagesTest {

    /**
     * 前提：异常 message 为鉴权失败的整段 JSON，message 字段含脱敏 Key 尾号。
     * 期望：发现场景返回「API Key 无效…」；结果不含 Key 尾号、不含花括号。
     */
    @Test
    @Order(1)
    @DisplayName("鉴权 JSON 转为 API Key 无效提示")
    void forDiscovery_mapsSiliconFlowAuthJson() {
        String raw = "{\"error\":{\"message\":\"Authentication Fails, Your api key: ****764f is invalid\","
                + "\"type\":\"authentication_error\",\"param\":null,\"code\":\"invalid_request_error\"}}";
        RuntimeException upstream = new RuntimeException(raw);

        String message = LlmUpstreamErrorMessages.forDiscovery(upstream);

        assertEquals("API Key 无效或无权访问，请检查后重试", message);
        assertFalse(message.contains("764f"));
        assertFalse(message.contains("{"));
    }

    /**
     * 前提：异常 message 前有 status code 说明，后跟鉴权失败 JSON。
     * 期望：仍归类为 API Key 无效，不把原文拼进结果。
     */
    @Test
    @Order(2)
    @DisplayName("带 HTTP 前缀的鉴权失败仍友好提示")
    void forDiscovery_mapsPrefixedAuthFailure() {
        RuntimeException upstream = new RuntimeException(
                "status code: 401; body: {\"error\":{\"message\":\"Authentication Fails\","
                        + "\"type\":\"authentication_error\"}}");

        String message = LlmUpstreamErrorMessages.forDiscovery(upstream);

        assertEquals("API Key 无效或无权访问，请检查后重试", message);
    }

    /**
     * 前提：cause 为连接拒绝。
     * 期望：对话场景提示无法连接，并提到检查 Base URL。
     */
    @Test
    @Order(3)
    @DisplayName("连接拒绝提示检查 Base URL")
    void forChat_mapsConnectException() {
        RuntimeException wrapped = new RuntimeException("boom", new ConnectException("Connection refused"));

        String message = LlmUpstreamErrorMessages.forChat(wrapped);

        assertTrue(message.contains("无法连接模型服务"));
        assertTrue(message.contains("Base URL"));
    }

    /**
     * 前提：cause 为读超时。
     * 期望：对话场景返回超时固定中文。
     */
    @Test
    @Order(4)
    @DisplayName("读超时提示增大超时")
    void forChat_mapsTimeout() {
        RuntimeException wrapped = new RuntimeException("x", new SocketTimeoutException("Read timed out"));

        assertEquals("连接模型服务超时，请检查网络或增大读超时",
                LlmUpstreamErrorMessages.forChat(wrapped));
    }

    /**
     * 前提：非鉴权业务错误，JSON 内仅有 error.message。
     * 期望：发现场景为「拉取模型列表失败：」加上该 message。
     */
    @Test
    @Order(5)
    @DisplayName("非鉴权 JSON 抽取 error.message")
    void forDiscovery_extractsNonAuthJsonMessage() {
        RuntimeException upstream = new RuntimeException(
                "{\"error\":{\"message\":\"Model not found\",\"type\":\"invalid_request_error\"}}");

        String message = LlmUpstreamErrorMessages.forDiscovery(upstream);

        assertEquals("拉取模型列表失败：Model not found", message);
    }

    /**
     * 前提：前缀文案后跟 JSON，且 message 字符串内含花括号。
     * 期望：括号配对截取完整对象，正确读出 message 与 type。
     */
    @Test
    @Order(6)
    @DisplayName("前缀包裹下按括号配对解析 JSON")
    void parseUpstreamError_braceMatchedObject() {
        String raw = "body: {\"error\":{\"message\":\"hi {x}\",\"type\":\"authentication_error\"}}";

        var payload = LlmUpstreamErrorMessages.parseUpstreamError(raw);

        assertEquals("hi {x}", payload.message());
        assertEquals("authentication_error", payload.type());
    }
}
