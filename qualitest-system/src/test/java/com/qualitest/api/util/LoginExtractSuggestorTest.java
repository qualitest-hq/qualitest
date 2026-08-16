package com.qualitest.api.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=LoginExtractSuggestorTest
 */
class LoginExtractSuggestorTest {

    private static final String DUAL = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    @Test
    @DisplayName("客户端登录：loginHint → $.data.token / token")
    void suggest_clientLogin_usesLoginHint() {
        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(
                DUAL, "/api/account/auth/login", null);
        assertNotNull(s);
        assertEquals("token", s.name());
        assertEquals("$.data.token", s.expr());
        assertEquals("body", s.from());
    }

    @Test
    @DisplayName("管理端 /login：loginHint → $.token / adminToken")
    void suggest_adminLogin_usesLoginHint() {
        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(DUAL, "/login", null);
        assertNotNull(s);
        assertEquals("adminToken", s.name());
        assertEquals("$.token", s.expr());
    }

    @Test
    @DisplayName("schema 嗅探：data.token 优先")
    void sniff_prefersDataToken() {
        JSONObject schema = new JSONObject();
        schema.put("code", "integer");
        schema.put("data.token", "string");
        assertEquals("$.data.token", LoginExtractSuggestor.sniffTokenJsonPath(schema));
    }

    @Test
    @DisplayName("captcha 不是登录类")
    void captcha_notLoginLike() {
        assertFalse(LoginExtractSuggestor.isLoginLikeApi("/captchaImage"));
        assertTrue(LoginExtractSuggestor.isLoginLikeApi("/api/account/auth/login"));
    }

    @Test
    @DisplayName("extractsContainFlowKey")
    void extractsContainFlowKey() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "token");
        row.put("scope", "flow");
        assertTrue(LoginExtractSuggestor.extractsContainFlowKey(List.of(row), "token"));
        assertFalse(LoginExtractSuggestor.extractsContainFlowKey(List.of(row), "adminToken"));
    }
}
