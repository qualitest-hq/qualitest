package com.qualitest.api.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：{@link AuthHeaderHintSupport} 工具回传 auth / headerHint。
 * 边界：none 不加 hint；inherit 按路径命中 client/admin；override 自定义头；compact 精简字段。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AuthHeaderHintSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthHeaderHintSupportTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    /**
     * 前提：接口 mode=none。
     * 期望：auth.mode=none，无 headerHint。
     */
    @Test
    @Order(1)
    @DisplayName("none 不加 headerHint")
    void putAuthFields_none_skipsHint() {
        JSONObject target = new JSONObject();
        AuthHeaderHintSupport.putAuthFields(
                target, "{\"mode\":\"none\"}", PROJECT_AUTH, "/api/catalog/list");
        assertEquals("none", target.getJSONObject("auth").getString("mode"));
        assertNull(target.get("headerHint"));
    }

    /**
     * 前提：/api 路径 inherit、项目双端模板。
     * 期望：headerHint 指向 clientBearer 与 flow.token。
     */
    @Test
    @Order(2)
    @DisplayName("client 路径回传 Bearer 模板")
    void putAuthFields_clientPath_returnsTokenHint() {
        JSONObject target = new JSONObject();
        AuthHeaderHintSupport.putAuthFields(
                target, "{\"mode\":\"inherit\"}", PROJECT_AUTH, "/api/account/auth/profile");
        assertEquals("inherit", target.getJSONObject("auth").getString("mode"));
        JSONObject hint = target.getJSONObject("headerHint");
        assertEquals("Authorization", hint.getString("name"));
        assertEquals("Bearer {{flow.token}}", hint.getString("valueTemplate"));
        assertEquals("clientBearer", hint.getString("profileId"));
        assertEquals("token", hint.getString("flowKey"));
        assertEquals("body", hint.getString("from"));
        assertEquals("$.data.token", hint.getString("expr"));
    }

    /**
     * 前提：compactAuth 对 inherit 客户端接口。
     * 期望：含 mode 与回填的 authProfileId。
     */
    @Test
    @Order(3)
    @DisplayName("compactAuth 含 profileId")
    void compactAuth_fillsProfileId() {
        JSONObject auth = AuthHeaderHintSupport.compactAuth(
                "{\"mode\":\"inherit\"}", PROJECT_AUTH, "/api/cart");
        assertEquals("inherit", auth.getString("mode"));
        assertEquals("clientBearer", auth.getString("authProfileId"));
        assertFalse(auth.containsKey("valueTemplate"));
    }

    /**
     * 前提：管理端路径。
     * 期望：adminBearer + adminToken。
     */
    @Test
    @Order(4)
    @DisplayName("admin 路径回传 adminToken")
    void putAuthFields_adminPath_returnsAdminToken() {
        JSONObject target = new JSONObject();
        AuthHeaderHintSupport.putAuthFields(
                target, null, PROJECT_AUTH, "/system/user/list");
        assertEquals("adminBearer", target.getJSONObject("headerHint").getString("profileId"));
        assertEquals("adminToken", target.getJSONObject("headerHint").getString("flowKey"));
        assertTrue(target.getJSONObject("headerHint").getString("valueTemplate").contains("adminToken"));
    }

    /**
     * 前提：mode=override 自定义无效 Bearer。
     * 期望：headerHint 用接口模板；auth 含 header；无 profileId/flowKey。
     */
    @Test
    @Order(5)
    @DisplayName("override 回传自定义 headerHint")
    void putAuthFields_override_returnsCustomHeader() {
        String apiAuth = """
                {"mode":"override","header":{"name":"Authorization","valueTemplate":"Bearer invalid-token"}}
                """;
        JSONObject target = new JSONObject();
        AuthHeaderHintSupport.putAuthFields(target, apiAuth, PROJECT_AUTH, "/api/orders");
        assertEquals("override", target.getJSONObject("auth").getString("mode"));
        assertEquals("Authorization", target.getJSONObject("auth").getJSONObject("header").getString("name"));
        JSONObject hint = target.getJSONObject("headerHint");
        assertEquals("Authorization", hint.getString("name"));
        assertEquals("Bearer invalid-token", hint.getString("valueTemplate"));
        assertFalse(hint.containsKey("profileId"));
        assertFalse(hint.containsKey("flowKey"));
    }
}
