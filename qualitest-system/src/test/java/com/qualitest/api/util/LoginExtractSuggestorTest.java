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
 * 测 LoginExtractSuggestor：按托管头凭证目标 / 可用 schema 推荐登录 extract。
 * 边界：无目标且 schema 无 token 时不编路径；有目标时返回 asset 行。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=LoginExtractSuggestorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginExtractSuggestorTest {

    private static final String DUAL = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    /**
     * 前提：双端模板；客户端登录 path；schema 含 data.token。
     * 期望：asset.clientAuth.token，expr=$.data.token。
     */
    @Test
    @Order(1)
    @DisplayName("客户端登录：asset 目标 → $.data.token")
    void suggest_clientLogin_usesAssetTarget() {
        JSONObject schema = new JSONObject();
        schema.put("data.token", "string");

        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(
                DUAL, "/api/account/auth/login", schema);

        assertNotNull(s);
        assertEquals("asset", s.scope());
        assertEquals("clientAuth", s.entryKey());
        assertEquals("token", s.fieldPath());
        assertEquals("token", s.name());
        assertEquals("$.data.token", s.expr());
        assertEquals("body", s.from());
    }

    /**
     * 前提：双端模板；管理端 /login；schema 含 token。
     * 期望：asset.adminAuth.token，expr=$.token。
     */
    @Test
    @Order(2)
    @DisplayName("管理端 /login：asset 目标 → $.token")
    void suggest_adminLogin_usesAssetTarget() {
        JSONObject schema = new JSONObject();
        schema.put("token", "string");

        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(DUAL, "/login", schema);

        assertNotNull(s);
        assertEquals("asset", s.scope());
        assertEquals("adminAuth", s.entryKey());
        assertEquals("token", s.fieldPath());
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
    @DisplayName("无目标且 schema 无 token 时不编路径")
    void suggest_mapSchema_returnsNull() {
        JSONObject schema = new JSONObject();
        schema.put("threshold", "integer");
        schema.put("loadFactor", "number");

        assertNull(LoginExtractSuggestor.suggest(null, "/login", schema));
    }

    /**
     * 前提：无项目鉴权；schema 含 data.token；客户端登录 path。
     * 期望：用嗅探结果，name 按路径默认为 token（flow）。
     */
    @Test
    @Order(5)
    @DisplayName("无目标时用 schema 中的 token 路径")
    void suggest_noHint_usesSchemaToken() {
        JSONObject schema = new JSONObject();
        schema.put("data.token", "string");

        LoginExtractSuggestor.Suggestion s = LoginExtractSuggestor.suggest(
                null, "/api/account/auth/login", schema);

        assertNotNull(s);
        assertEquals("token", s.name());
        assertEquals("$.data.token", s.expr());
        assertEquals("flow", s.scope());
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
     * 期望：仅 credentialApi 有凭证口；注册口没有。
     */
    @Test
    @Order(8)
    @DisplayName("isCredentialApiEndpoint 只认发凭证口")
    void isCredentialApiEndpoint_onlyCredentialApi() {
        assertTrue(LoginExtractSuggestor.isCredentialApiEndpoint(DUAL, "POST", "/login"));
        assertFalse(LoginExtractSuggestor.isCredentialApiEndpoint(DUAL, "POST", "/register"));
        assertFalse(LoginExtractSuggestor.isCredentialApiEndpoint(DUAL, "GET", "/captchaImage"));
        assertTrue(LoginExtractSuggestor.isCredentialApiEndpoint(
                DUAL, "POST", "/api/account/auth/login"));
    }
}
