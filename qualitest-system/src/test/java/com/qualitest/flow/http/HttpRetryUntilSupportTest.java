package com.qualitest.flow.http;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 HttpRetryUntilSupport：缺省解析、完整配置解析、设计态规范化（删除无意义配置 / 越界截断）、
 * 报告次数文案、可重试错误码判定。
 * 边界：脏类型、字符串数字、越界值。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpRetryUntilSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpRetryUntilSupportTest {

    /**
     * 前提：data 无 retryUntil。
     * 期望：maxAttempts=1、无 until、不需要循环。
     */
    @Test
    @Order(1)
    @DisplayName("缺省解析为 1 次")
    void resolve_absent_defaultsToOne() {
        HttpRetryUntilSupport.Resolved r = HttpRetryUntilSupport.resolve(Map.of());
        assertEquals(1, r.maxAttempts());
        assertFalse(r.hasUntil());
        assertFalse(r.needsLoop());
    }

    /**
     * 前提：maxAttempts=10、intervalMs=2000、一条 until。
     * 期望：原样解析；needsLoop=true。
     */
    @Test
    @Order(2)
    @DisplayName("完整配置可解析")
    void resolve_fullConfig() {
        Map<String, Object> data = Map.of(
                "retryUntil", Map.of(
                        "maxAttempts", 10,
                        "intervalMs", 2000,
                        "until", List.of(Map.of("left", "http.body.data.status", "operator", "eq", "right", "PAID"))));
        HttpRetryUntilSupport.Resolved r = HttpRetryUntilSupport.resolve(data);
        assertEquals(10, r.maxAttempts());
        assertEquals(2000L, r.intervalMs());
        assertTrue(r.hasUntil());
        assertTrue(r.needsLoop());
        assertEquals(1, r.untilRules().size());
    }

    /**
     * 前提：maxAttempts=1 且无 until。
     * 期望：normalize 删除整段 retryUntil。
     */
    @Test
    @Order(3)
    @DisplayName("规范化：默认形态删除键")
    void normalize_defaultShape_removesKey() {
        Map<String, Object> data = new HashMap<>();
        data.put("retryUntil", Map.of("maxAttempts", 1, "intervalMs", 1000));
        HttpRetryUntilSupport.normalizeForDesign(data);
        assertFalse(data.containsKey("retryUntil"));
    }

    /**
     * 前提：maxAttempts 字符串越界、interval 越界。
     * 期望：截断到上限。
     */
    @Test
    @Order(4)
    @DisplayName("规范化：越界截断")
    void normalize_clampsBounds() {
        Map<String, Object> data = new HashMap<>();
        data.put("retryUntil", Map.of(
                "maxAttempts", "999",
                "intervalMs", "999999",
                "until", List.of(Map.of("left", "http.status", "operator", "eq", "right", "200"))));
        HttpRetryUntilSupport.normalizeForDesign(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> retry = (Map<String, Object>) data.get("retryUntil");
        assertEquals(HttpRetryUntilSupport.MAX_MAX_ATTEMPTS, retry.get("maxAttempts"));
        assertEquals(HttpRetryUntilSupport.MAX_INTERVAL_MS, ((Number) retry.get("intervalMs")).longValue());
    }

    /**
     * 前提：attempts 为 1 与大于 1。
     * 期望：1 次返回空串；多轮返回「第 N/M 次」后缀。
     */
    @Test
    @Order(5)
    @DisplayName("报告次数后缀")
    void formatAttemptSuffix_onlyWhenMultiple() {
        assertEquals("", HttpRetryUntilSupport.formatAttemptSuffix(1, 10));
        assertEquals(" · 第 3/10 次", HttpRetryUntilSupport.formatAttemptSuffix(3, 10));
    }

    /**
     * 前提：失败步骤错误码分别为可重试与不可重试。
     * 期望：状态/业务码/断言/超时可重试；未绑定不可重试。
     */
    @Test
    @Order(6)
    @DisplayName("可重试错误码判定")
    void isRetryable_byErrorCode() {
        assertTrue(HttpRetryUntilSupport.isRetryable(failed(FlowErrorCode.TF_HTTP_STATUS)));
        assertTrue(HttpRetryUntilSupport.isRetryable(failed(FlowErrorCode.TF_BIZ_CODE)));
        assertTrue(HttpRetryUntilSupport.isRetryable(failed(FlowErrorCode.TF_ASSERT_FAILED)));
        assertTrue(HttpRetryUntilSupport.isRetryable(failed(FlowErrorCode.TF_HTTP_TIMEOUT)));
        assertFalse(HttpRetryUntilSupport.isRetryable(failed(FlowErrorCode.TF_HTTP_UNBOUND)));
        assertFalse(HttpRetryUntilSupport.isRetryable(StepResult.builder()
                .status(RunStatus.PASSED.getCode())
                .build()));
    }

    private static StepResult failed(FlowErrorCode code) {
        return StepResult.builder()
                .status(RunStatus.FAILED.getCode())
                .error(StepError.of(code, code.getDefaultMessage()))
                .build();
    }
}
