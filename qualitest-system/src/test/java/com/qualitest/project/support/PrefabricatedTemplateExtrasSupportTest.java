package com.qualitest.project.support;

import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.DerivedCredential;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.PrefabParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PrefabricatedTemplateExtrasSupport：凭证派生、画布绑接口、flow/assert/env 合并。
 * 覆盖：流优先、setCookie→Cookie、无来源返回 null、旧 params 不再生效。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=PrefabricatedTemplateExtrasSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrefabricatedTemplateExtrasSupportTest {

    /**
     * 有登录抽取的预制测试流 → Bearer 头与 credentialApi。
     */
    @Test
    @Order(1)
    @DisplayName("flows extracts 派生 Bearer 与 asset 头")
    void derive_fromFlows_bearer() {
        // 前提：内置登录流含 body extract → asset.adminAuth.token
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录", "POST", "/login", "adminAuth", "token", "body", "$.token");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(flows);

        // 期望：Bearer 托管头指向 asset
        assertNotNull(derived);
        assertEquals("/login", derived.getCredentialApi().getPath());
        assertEquals("Authorization", derived.getHeaderName());
        assertEquals("Bearer {{asset.adminAuth.token}}", derived.getHeaderValueTemplate());
    }

    /** setCookie 抽取 → Cookie 托管头。 */
    @Test
    @Order(2)
    @DisplayName("setCookie 派生 Cookie 头")
    void derive_fromFlows_session() {
        // 前提：setCookie extract → asset.adminAuth.jsessionId
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "Session 登录", "POST", "/login", "adminAuth", "jsessionId", "setCookie", "JSESSIONID");

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(flows);

        // 期望：Cookie 托管头
        assertNotNull(derived);
        assertEquals("Cookie", derived.getHeaderName());
        assertEquals("JSESSIONID={{asset.adminAuth.jsessionId}}", derived.getHeaderValueTemplate());
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
        assertNull(PrefabricatedTemplateExtrasSupport.deriveCredential("[]"));
    }

    /** 没有任何凭证来源时返回 null。 */
    @Test
    @Order(4)
    @DisplayName("无凭证来源返回 null")
    void derive_empty() {
        assertNull(PrefabricatedTemplateExtrasSupport.deriveCredential("[]"));
    }

    /** 按 method+path 给画布 HTTP 节点写入项目接口 id。 */
    @Test
    @Order(5)
    @DisplayName("bindGraphApis 写入探活与登录 apiId")
    void bindGraphApis() {
        // 前提：探活再登录图
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "登录", "POST", "/login", "adminAuth", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(graph, (method, path) -> {
            if ("POST".equals(method) && "/login".equals(path)) {
                return 99L;
            }
            if ("GET".equals(method) && "/getInfo".equals(path)) {
                return 88L;
            }
            return null;
        });

        // 期望：探活与登录均写入 testProjectApiId
        assertTrue(bound.contains("\"testProjectApiId\":\"99\""));
        assertTrue(bound.contains("\"testProjectApiId\":\"88\""));
        assertTrue(bound.contains("\"statusCheck\"") || bound.contains("statusCheck"));
    }

    /** 合成 id 优先 remap，不再依赖 path（薄节点可无 apiPath）。 */
    @Test
    @Order(5)
    @DisplayName("bindGraphApis 合成 id remap")
    void bindGraphApis_synthRemap() {
        // 前提：节点仅有合成 id，无 path
        String graph = """
                {"nodes":[{"id":"login_http","type":"http","data":{
                  "callMode":"project","httpMethod":"POST","testProjectApiId":"tpl_ab_login"
                }},{"id":"probe_http","type":"http","data":{
                  "callMode":"project","httpMethod":"GET","testProjectApiId":"tpl_ab_getInfo"
                }}],"edges":[],"meta":{}}
                """;
        Map<String, Long> synth = Map.of("tpl_ab_login", 101L, "tpl_ab_getInfo", 102L);

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(
                graph, (method, path) -> null, synth);

        assertTrue(bound.contains("\"testProjectApiId\":\"101\""));
        assertTrue(bound.contains("\"testProjectApiId\":\"102\""));
        assertFalse(bound.contains("tpl_ab_login"));
    }

    /**
     * 前提：节点挂作者期雪花数字 id，map 指向项目主键。
     * 期望：优先 remap，不被「纯数字=项目主键」短路。
     */
    @Test
    @Order(6)
    @DisplayName("bindGraphApis 数字作者期 id 也 remap")
    void bindGraphApis_numericAuthoringIdRemap() {
        String graph = """
                {"nodes":[{"id":"login_http","type":"http","data":{
                  "callMode":"project","httpMethod":"POST","testProjectApiId":"2100000000000004101"
                }}],"edges":[],"meta":{}}
                """;
        Map<String, Long> synth = Map.of("2100000000000004101", 501L);

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(
                graph, (method, path) -> null, synth);

        assertTrue(bound.contains("\"testProjectApiId\":\"501\""));
        assertFalse(bound.contains("2100000000000004101"));
    }

    /** 已是数字项目 id 时跳过；legacy 无 id 仍按 path。 */
    @Test
    @Order(7)
    @DisplayName("bindGraphApis legacy path 与数字 id 跳过")
    void bindGraphApis_legacyAndNumeric() {
        String graph = """
                {"nodes":[
                  {"id":"a","type":"http","data":{"callMode":"project","httpMethod":"POST","apiPath":"/login"}},
                  {"id":"b","type":"http","data":{"callMode":"project","httpMethod":"GET","testProjectApiId":"55"}}
                ],"edges":[],"meta":{}}
                """;

        String bound = PrefabricatedTemplateExtrasSupport.bindGraphApis(graph, (method, path) -> {
            if ("POST".equals(method) && "/login".equals(path)) {
                return 77L;
            }
            return 999L;
        }, Map.of());

        assertTrue(bound.contains("\"testProjectApiId\":\"77\""));
        assertTrue(bound.contains("\"testProjectApiId\":\"55\""));
        assertFalse(bound.contains("\"999\""));
    }

    /** 内置登录图可写入合成 id。 */
    @Test
    @Order(9)
    @DisplayName("builtinLoginFlowJson 写入合成 apiId")
    void builtinLoginFlow_writesSynthIds() {
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录", "POST", "/login", "adminAuth", "token", "body", "$.token",
                "GET", "/getInfo", "tpl_ab_login", "tpl_ab_getInfo", "登录", "获取用户信息");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        assertTrue(graph.contains("tpl_ab_login"));
        assertTrue(graph.contains("tpl_ab_getInfo"));
        assertTrue(graph.contains("获取用户信息"));
    }

    /** 探活再登录图含 Condition + 探活 whitelist。 */
    @Test
    @Order(9)
    @DisplayName("builtinLoginFlowJson 含探活分支")
    void builtinLoginFlow_hasProbeBranches() {
        // 前提：默认探活 /getInfo
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "管理端登录", "POST", "/login", "adminAuth", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        // 期望：存在凭证 Condition、探活 whitelist、登录 extract 与口令占位符
        assertTrue(graph.contains("cond_token"));
        assertTrue(graph.contains("probe_http"));
        assertTrue(graph.contains("cond_alive"));
        assertTrue(graph.contains("login_http"));
        assertTrue(graph.contains("whitelist"));
        assertTrue(graph.contains("asset.adminAuth.token"));
        assertTrue(graph.contains("{{asset.adminAuth.username}}"));
        assertTrue(graph.contains("/getInfo"));
    }

    /** 客户端登录图 body 绑 mobile/password 素材。 */
    @Test
    @Order(10)
    @DisplayName("builtinLoginFlowJson 客户端绑 mobile 口令")
    void builtinLoginFlow_clientAuthBody() {
        // 前提：entryKey=clientAuth
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "客户端登录", "POST", "/api/account/auth/login", "clientAuth", "token", "body", "$.data.token",
                "GET", "/api/account/auth/profile");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();

        // 期望：登录 body 引用 clientAuth 口令，不用 username
        assertTrue(graph.contains("{{asset.clientAuth.mobile}}"));
        assertTrue(graph.contains("{{asset.clientAuth.password}}"));
        assertFalse(graph.contains("username"));
    }

    /** flow 参数写入默认场景 flowSeed。 */
    @Test
    @Order(8)
    @DisplayName("mergeFlowSeedIntoGraph")
    void mergeFlowSeed() {
        // 前提：空 flowSeed 登录图 + flow 参数
        String flows = PrefabricatedTemplateExtrasSupport.builtinLoginFlowJson(
                "登录", "POST", "/login", "adminAuth", "token", "body", "$.token");
        String graph = PrefabricatedTemplateExtrasSupport.parseFlows(flows).get(0).getGraphJson();
        List<PrefabParam> params = PrefabricatedTemplateExtrasSupport.parseParams(
                "[{\"kind\":\"flow\",\"name\":\"token\",\"value\":\"debug\"}]");

        String next = PrefabricatedTemplateExtrasSupport.mergeFlowSeedIntoGraph(graph, params);

        // 期望：scenarios[0].flowSeed.token
        assertTrue(next.contains("\"token\":\"debug\"") || next.contains("\"token\": \"debug\""));
    }

    /** asset 合并进素材库。 */
    @Test
    @Order(7)
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
    @Order(8)
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

    @Test
    @Order(9)
    @DisplayName("parseEnvs 读名称/URL/变量；空条目跳过")
    void parseEnvs() {
        List<PrefabricatedTemplateExtrasSupport.PrefabEnv> envs = PrefabricatedTemplateExtrasSupport.parseEnvs(
                "[{\"envName\":\"默认环境\",\"envUrl\":\"http://localhost:8801\",\"envVariables\":[]},"
                        + "{\"envName\":\"\",\"envUrl\":\"\",\"envVariables\":[]}]");
        assertEquals(1, envs.size());
        assertEquals("默认环境", envs.get(0).getEnvName());
        assertEquals("http://localhost:8801", envs.get(0).getEnvUrl());
        assertEquals("[]", envs.get(0).getEnvVariablesJson());
    }

    @Test
    @Order(10)
    @DisplayName("isPlaceholderEnvUrl 认空串与 127.0.0.1")
    void isPlaceholderEnvUrl() {
        assertTrue(PrefabricatedTemplateExtrasSupport.isPlaceholderEnvUrl(null));
        assertTrue(PrefabricatedTemplateExtrasSupport.isPlaceholderEnvUrl(""));
        assertTrue(PrefabricatedTemplateExtrasSupport.isPlaceholderEnvUrl("http://127.0.0.1"));
        assertFalse(PrefabricatedTemplateExtrasSupport.isPlaceholderEnvUrl("http://localhost:8801"));
    }

    @Test
    @Order(11)
    @DisplayName("paramsFromEnvVariablesJson 转 kind=env")
    void paramsFromEnvVariablesJson() {
        List<PrefabParam> params = PrefabricatedTemplateExtrasSupport.paramsFromEnvVariablesJson(
                "[{\"key\":\"timeout\",\"remark\":\"毫秒\",\"assets\":{\"timeout\":5000}}]");
        assertEquals(1, params.size());
        assertEquals("env", params.get(0).getKind());
        assertEquals("timeout", params.get(0).getName());
        assertEquals(5000, params.get(0).getValue());
        assertEquals("毫秒", params.get(0).getRemark());
    }

    @Test
    @Order(12)
    @DisplayName("unbindGraphApis 把项目 apiId 改回合成 id")
    void unbindGraphApis_rewritesProjectIds() {
        // 前提：图内 HTTP 绑项目主键 101
        String graph = "{\"nodes\":[{\"id\":\"h1\",\"type\":\"http\",\"data\":{"
                + "\"callMode\":\"project\",\"testProjectApiId\":\"101\",\"apiPath\":\"/login\"}}],\"edges\":[]}";

        String unbound = PrefabricatedTemplateExtrasSupport.unbindGraphApis(
                graph, Map.of("101", "900001"));

        // 期望：换成合成 id；collect 仍能读到
        assertTrue(unbound.contains("900001"));
        assertFalse(unbound.contains("\"101\""));
        assertEquals(List.of("101"), PrefabricatedTemplateExtrasSupport.collectGraphHttpApiIds(graph));
        assertTrue(PrefabricatedTemplateExtrasSupport.graphHasHttpExtracts(
                "{\"nodes\":[{\"type\":\"http\",\"data\":{\"extracts\":[{\"expr\":\"$.token\"}]}}]}"));
    }
}
