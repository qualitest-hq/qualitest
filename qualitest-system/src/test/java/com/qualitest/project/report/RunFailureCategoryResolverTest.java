package com.qualitest.project.report;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 失败步骤类别判定用例。
 */
class RunFailureCategoryResolverTest {

    @Test
    @DisplayName("assert 节点归为断言失败")
    void assertNode() {
        assertEquals(RunFailureCategoryResolver.ASSERT,
                RunFailureCategoryResolver.resolve("assert", null));
    }

    @Test
    @DisplayName("错误码 TF_BIZ_CODE 归为业务码失败")
    void bizCodeError() {
        JSONObject details = JSONObject.parseObject(
                "{\"error\":{\"code\":\"TF_BIZ_CODE\",\"message\":\"x\"}}");
        assertEquals(RunFailureCategoryResolver.BIZ_CODE,
                RunFailureCategoryResolver.resolve("http", details));
    }

    @Test
    @DisplayName("业务码校验未通过归为业务码失败")
    void bizCheckFailed() {
        JSONObject details = JSONObject.parseObject(
                "{\"http\":{\"bizCheck\":{\"passed\":false,\"actualCode\":1}}}");
        assertEquals(RunFailureCategoryResolver.BIZ_CODE,
                RunFailureCategoryResolver.resolve("http", details));
    }
}
