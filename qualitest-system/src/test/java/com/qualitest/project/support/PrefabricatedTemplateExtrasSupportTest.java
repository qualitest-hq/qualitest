package com.qualitest.project.support;

import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.DerivedCredential;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.PrefabParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PrefabricatedTemplateExtrasSupport：凭证派生、画布绑接口、flow/assert/env 合并。
 * 覆盖：流优先、setCookie→Cookie、无来源返回 null、旧 params 不再生效。
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
        // 前提：内置登录流含 body extract
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录", "POST", "/login", "adminToken", "body", "$.token");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                flows, null, null);

        // 期望：Bearer 托管头与 loginHint
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
        // 前提：setCookie extract
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "Session 登录", "POST", "/login", "jsessionId", "setCookie", "JSESSIONID");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                flows, null, null);

        // 期望：Cookie 托管头
        assertNotNull(derived);
        assertEquals("Cookie", derived.getHeaderName());
        assertEquals("JSESSIONID={{flow.jsessionId}}", derived.getHeaderValueTemplate());
    }

    /** 旧 kind=extract 预制参数不再参与凭证派生。 */
    @Test
    @Order(3)
    @DisplayName("旧 extract 参数不派生凭证")
    void derive_ignoresLegacyParams() {
        // 前提：仅有旧 extract 形态 JSON，无流
        String legacyParams = "[{\"kind\":\"extract\",\"name\":\"token\",\"bind\":{\"method\":\"POST\",\"path\":\"/login\"},"
                + "\"from\":\"body\",\"expr\":\"$.data.token\",\"credential\":true}]";

        assertTrue(PrefabricatedTemplateExtrasSupport.parseParams(legacyParams).isEmpty());
        assertNull(PrefabricatedTemplateExtrasSupport.deriveCredential("[]", null, null));
    }

    /** 无流无参数时，回退接口上残留的 loginHint。 */
    @Test
    @Order(4)
    @DisplayName("回退接口 loginHint")
    void derive_legacyHint() {
        // 前提：仅有接口残留 hint
        LoginHint legacy = LoginHint.builder().flowKey("token").from("body").expr("$.token").build();

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                null, legacy, null);

        // 期望：用 hint 拼 Bearer
        assertNotNull(derived);
        assertEquals("token", derived.getLoginHint().getFlowKey());
        assertTrue(derived.getHeaderValueTemplate().contains("flow.token"));
    }

    /** 没有任何凭证来源时返回 null。 */
    @Test
    @Order(5)
    @DisplayName("无凭证来源返回 null")
    void derive_empty() {
        assertNull(PrefabricatedTemplateExtrasSupport.deriveCredential("[]", null, null));
    }

    /** 按 method+path 给画布 HTTP 节点写入项目接口 id。 */
    @Test
    @Order(6)
    @DisplayName("bindGraphApis 写入 apiId")
    void bindGraphApis() {
        // 前提：登录图画布
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "登录", "POST", "/login", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(
                graph, (method, path) -> "POST".equals(method) && "/login".equals(path) ? 99L : null);

        // 期望：写入 testProjectApiId
        assertTrue(bound.contains("\"testProjectApiId\":\"99\""));
    }

    /** flow 参数写入默认场景 flowSeed。 */
    @Test
    @Order(7)
    @DisplayName("mergeFlowSeedIntoGraph")
    void mergeFlowSeed() {
        // 前提：空 flowSeed 登录图 + flow 参数
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "登录", "POST", "/login", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();
        List<PrefabParam> params = PrefabricatedTemplateExtrasSupport.parseParams(
                "[{\"kind\":\"flow\",\"name\":\"token\",\"value\":\"debug\"}]");

        String next = PrefabricatedTemplateExtrasSupport.mergeFlowSeedIntoGraph(graph, params);

        // 期望：scenarios[0].flowSeed.token
        assertTrue(next.contains("\"token\":\"debug\"") || next.contains("\"token\": \"debug\""));
    }

    /** asset 合并进素材库。 */
    @Test
    @Order(8)
    @DisplayName("mergeAssetVariables")
    void mergeAsset() {
        // 前提：空素材库 + asset 参数（对象值）
        List<PrefabParam> params = PrefabricatedTemplateExtrasSupport.parseParams(
                "[{\"kind\":\"asset\",\"name\":\"clientAuth\","
                        + "\"value\":{\"mobile\":\"13800000001\",\"password\":\"Test@123456\"}}]");

        String next = PrefabricatedTemplateExtrasSupport.mergeAssetVariables("[]", params);

        // 期望：写入 clientAuth 包装
        assertTrue(next.contains("clientAuth"));
        assertTrue(next.contains("13800000001"));
    }

    /** env 合并同 key 不覆盖。 */
    @Test
    @Order(9)
    @DisplayName("mergeEnvVariables 同名不覆盖")
    void mergeEnv() {
        // 前提：已有 timeout；模板再给 timeout 与新键
        String existing = "[{\"id\":1,\"key\":\"timeout\",\"assets\":{\"timeout\":1000}}]";
        List<PrefabParam> params = PrefabricatedTemplateExtrasSupport.parseParams(
                "[{\"kind\":\"env\",\"name\":\"timeout\",\"value\":\"9999\"},"
                        + "{\"kind\":\"env\",\"name\":\"region\",\"value\":\"cn\"}]");

        String next = PrefabricatedTemplateExtrasSupport.mergeEnvVariables(existing, params);

        // 期望：timeout 仍 1000；新增 region
        assertTrue(next.contains("\"timeout\":1000") || next.contains("\"timeout\": 1000"));
        assertTrue(next.contains("region"));
        assertFalse(next.contains("9999"));
    }
}
