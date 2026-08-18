package com.qualitest.api.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 LoginExtractSuggestor：按 loginHint / 可用 schema 推荐登录 extract。
 * 边界：无 hint 且 schema 无 token 时不编路径。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=LoginExtractSuggestorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginExtractSuggestorTest {

    private static final String DUAL = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    /**
     * 前提：双端模板；客户端登录 path。
     * 期望：name=token，expr=$.data.token。
     */
    @Test
    @Order(1)
    @DisplayName("客户端登录：loginHint → $.data.token / token")
    void suggest_clientLogin_usesLoginHint() {
        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(
                DUAL, "/api/account/auth/login", null);

        assertNotNull(s);
        assertEquals("token", s.name());
        assertEquals("$.data.token", s.expr());
        assertEquals("body", s.from());
    }

    /**
     * 前提：双端模板；管理端 /login。
     * 期望：name=adminToken，expr=$.token。
     */
    @Test
    @Order(2)
    @DisplayName("管理端 /login：loginHint → $.token / adminToken")
    void suggest_adminLogin_usesLoginHint() {
        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(DUAL, "/login", null);

        assertNotNull(s);
        assertEquals("adminToken", s.name());
        assertEquals("$.token", s.expr());
    }

    /**
     * 前提：schema 同时有 data.token 与其它字段。
     * 期望：嗅探优先 data.token。
     */
    @Test
    @Order(3)
    @DisplayName("schema 嗅探：data.token 优先")
    void sniff_prefersDataToken() {
        JSONObject schema = new JSONObject();
        schema.put("code", "integer");
        schema.put("data.token", "string");

        assertEquals("$.data.token", LoginExtractSuggestor.sniffTokenJsonPath(schema));
    }

    /**
     * 前提：无项目鉴权；schema 为 Map 脏结构。
     * 期望：suggest 返回 null，不编 $.token。
     */
    @Test
    @Order(4)
    @DisplayName("无 hint 且 schema 无 token 时不编路径")
    void suggest_mapSchema_returnsNull() {
        JSONObject schema = new JSONObject();
        schema.put("threshold", "integer");
        schema.put("loadFactor", "number");

        assertNull(LoginExtractSuggestor.suggest(null, "/login", schema));
    }

    /**
     * 前提：无项目鉴权；schema 含 data.token；客户端登录 path。
     * 期望：用嗅探结果，name 按路径默认为 token。
     */
    @Test
    @Order(5)
    @DisplayName("无 hint 时用 schema 中的 token 路径")
    void suggest_noHint_usesSchemaToken() {
        JSONObject schema = new JSONObject();
        schema.put("data.token", "string");

        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(
                null, "/api/account/auth/login", schema);

        assertNotNull(s);
        assertEquals("token", s.name());
        assertEquals("$.data.token", s.expr());
    }

    /**
     * 前提：captcha 与登录 path。
     * 期望：captcha 不是登录类。
     */
    @Test
    @Order(6)
    @DisplayName("captcha 不是登录类")
    void captcha_notLoginLike() {
        assertFalse(LoginExtractSuggestor.isLoginLikeApi("/captchaImage"));
        assertTrue(LoginExtractSuggestor.isLoginLikeApi("/api/account/auth/login"));
    }

    /**
     * 前提：extracts 只有 token 行。
     * 期望：contain token 为真，adminToken 为假。
     */
    @Test
    @Order(7)
    @DisplayName("extractsContainFlowKey 按 name 判断")
    void extractsContainFlowKey() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "token");
        row.put("scope", "flow");

        assertTrue(LoginExtractSuggestor.extractsContainFlowKey(List.of(row), "token"));
        assertFalse(LoginExtractSuggestor.extractsContainFlowKey(List.of(row), "adminToken"));
    }

    /**
     * 前提：双端模板。
     * 期望：仅 credentialApi 有 hint；注册口没有。
     */
    @Test
    @Order(8)
    @DisplayName("hasCredentialLoginHint 只认发凭证口")
    void hasCredentialLoginHint_onlyCredentialApi() {
        assertTrue(LoginExtractSuggestor.hasCredentialLoginHint(DUAL, "POST", "/login"));
        assertFalse(LoginExtractSuggestor.hasCredentialLoginHint(DUAL, "POST", "/register"));
        assertFalse(LoginExtractSuggestor.hasCredentialLoginHint(DUAL, "GET", "/captchaImage"));
        assertTrue(LoginExtractSuggestor.hasCredentialLoginHint(
                DUAL, "POST", "/api/account/auth/login"));
    }
}
