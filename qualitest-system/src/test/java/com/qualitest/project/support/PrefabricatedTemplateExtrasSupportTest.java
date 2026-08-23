package com.qualitest.project.support;

import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.DerivedCredential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PrefabricatedTemplateExtrasSupport：从预制测试流 / 预制参数生成凭证与托管头。
 * 覆盖：流优先、setCookie→Cookie、无来源返回 null、画布绑接口 id。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrefabricatedTemplateExtrasSupportTest {

    /**
     * 有登录抽取的预制测试流 → Bearer 头与 credentialApi。
     */
    @Test
    @Order(1)
    @DisplayName("flows extracts 派生 Bearer 与 hint")
    void derive_fromFlows_bearer() {
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录", "POST", "/login", "adminToken", "body", "$.token");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                flows, "[]", null, null);

        assertNotNull(derived);
        assertEquals("adminToken", derived.getLoginHint().getFlowKey());
        assertEquals("$.token", derived.getLoginHint().getExpr());
        assertEquals("/login", derived.getCredentialApi().getPath());
        assertEquals("Authorization", derived.getHeaderName());
        assertEquals("Bearer {{flow.adminToken}}", derived.getHeaderValueTemplate());
    }

    /** setCookie 抽取 → Cookie 托管头。 */
    @Test
    @Order(2)
    @DisplayName("setCookie 派生 Cookie 头")
    void derive_fromFlows_session() {
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "Session 登录", "POST", "/login", "jsessionId", "setCookie", "JSESSIONID");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                flows, null, null, null);

        assertNotNull(derived);
        assertEquals("Cookie", derived.getHeaderName());
        assertEquals("JSESSIONID={{flow.jsessionId}}", derived.getHeaderValueTemplate());
    }

    /** 无预制测试流时，用标记为凭证的预制参数生成。 */
    @Test
    @Order(3)
    @DisplayName("params credential 派生")
    void derive_fromParams() {
        String params = "[{\"kind\":\"extract\",\"name\":\"token\",\"bind\":{\"method\":\"POST\",\"path\":\"/login\"},"
                + "\"from\":\"body\",\"expr\":\"$.data.token\",\"credential\":true}]";

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                "[]", params, null, null);

        assertNotNull(derived);
        assertEquals("token", derived.getLoginHint().getFlowKey());
        assertEquals("$.data.token", derived.getLoginHint().getExpr());
        assertEquals("/login", derived.getCredentialApi().getPath());
    }

    /** 无流无参数时，回退接口上残留的 loginHint。 */
    @Test
    @Order(4)
    @DisplayName("回退接口 loginHint")
    void derive_legacyHint() {
        LoginHint legacy = LoginHint.builder().flowKey("token").from("body").expr("$.token").build();

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                null, null, legacy, null);

        assertNotNull(derived);
        assertEquals("token", derived.getLoginHint().getFlowKey());
        assertTrue(derived.getHeaderValueTemplate().contains("flow.token"));
    }

    /** 没有任何凭证来源时返回 null。 */
    @Test
    @Order(5)
    @DisplayName("无凭证来源返回 null")
    void derive_empty() {
        assertNull(PrefabricatedTemplateExtrasSupport.deriveCredential("[]", "[]", null, null));
    }

    /** 按 method+path 给画布 HTTP 节点写入项目接口 id。 */
    @Test
    @Order(6)
    @DisplayName("bindGraphApis 写入 apiId")
    void bindGraphApis() {
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "登录", "POST", "/login", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(
                graph, (method, path) -> "POST".equals(method) && "/login".equals(path) ? 99L : null);

        assertTrue(bound.contains("\"testProjectApiId\":\"99\""));
    }
}
