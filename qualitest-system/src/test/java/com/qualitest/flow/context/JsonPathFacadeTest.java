package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 JsonPath 求值：下标、过滤器、通配、length()、缺叶、语法校验。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=JsonPathFacadeTest
 */
class JsonPathFacadeTest {

    private Object body;
    private JSONArray contractCases;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/flow/compare-extract-cases.json")) {
            assert in != null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            body = root.getJSONObject("mockContext").get("body") != null
                    ? root.getJSONObject("mockContext").get("body")
                    : root.getJSONObject("mockContext").getJSONObject("lastResponse").get("body");
            contractCases = root.getJSONArray("jsonPathContractCases");
        }
    }

    @Test
    @DisplayName("契约金样：path → expected")
    void contractCases_matchExpected() {
        for (int i = 0; i < contractCases.size(); i++) {
            JSONObject c = contractCases.getJSONObject(i);
            String id = c.getString("id");
            Object actual = JsonPathFacade.eval(body, c.getString("expr"));
            assertEquals(c.get("expected"), actual, "case: " + id);
        }
    }

    @Test
    @DisplayName("语法校验：合法 / 非法")
    void isValidPath() {
        assertTrue(JsonPathFacade.isValidPath("$.data.items[0]"));
        assertTrue(JsonPathFacade.isValidPath("$.data.cart[?(@.cartId==5001)]"));
        assertFalse(JsonPathFacade.isValidPath("data.token"));
        assertFalse(JsonPathFacade.isValidPath("$.data["));
    }
}
