package com.qualitest.flow.context;

import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 测 FlowRunContextBuilder / EnvUrlSupport：组装 env/asset/flow 与多模块 envUrl。
 * 边界：无协议 host 补 http；存量 begin/end 保留。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunContextBuilderTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunContextBuilderTest {

    /**
     * 前提：TestProjectEnv 含 envUrl、envVariables；assetJson 与 flowSeed 非空。
     * 期望：ctx.env 含 baseUrl/timeout，flow 含 loginUser，asset.defaults 含 clientId。
     */
    @Test
    @Order(1)
    void build_injectsEnvBaseUrlAndAssetScope() {
        begin("build_injectsEnvBaseUrlAndAssetScope");
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8080")
                .envVariables("[{\"id\":1,\"key\":\"timeout\",\"assets\":{\"timeout\":5000}}]")
                .build();

        String assetJson = "[{\"id\":2,\"key\":\"defaults\",\"assets\":{\"defaults\":{\"clientId\":\"app-1\"}}}]";

        FlowRunContext ctx = FlowRunContextBuilder.build(env, assetJson, Map.of("loginUser", "admin"));

        assertEquals("http://localhost:8080", ctx.getEnv().get("baseUrl"));
        assertEquals(5000, ctx.getEnv().get("timeout"));
        assertEquals("admin", ctx.getFlow().get("loginUser"));

        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) ctx.getAsset().get("defaults");
        assertNotNull(defaults);
        assertEquals("app-1", defaults.get("clientId"));

        log("env.baseUrl=" + ctx.getEnv().get("baseUrl"));
        log("env.timeout=" + ctx.getEnv().get("timeout"));
        log("flow.loginUser=" + ctx.getFlow().get("loginUser"));
        log("asset.defaults.clientId=" + defaults.get("clientId"));
        end("build_injectsEnvBaseUrlAndAssetScope");
    }

    /**
     * 前提：envUrl 为多模块 JSON；host 无协议前缀。
     * 期望：resolve 取默认模块 URL；ensureHttpScheme 补 http:// 前缀。
     */
    @Test
    @Order(2)
    void envUrlSupport_resolvesJsonModule() {
        begin("envUrlSupport_resolvesJsonModule");
        String json = "{\"默认模块\":\"https://api.example.com\",\"other\":\"https://other.example.com\"}";
        String resolved = EnvUrlSupport.resolveEnvBaseUrlForRequest(json);
        String withScheme = EnvUrlSupport.ensureHttpSchemeForRequest("api.example.com");
        assertEquals("https://api.example.com", resolved);
        assertEquals("http://api.example.com", withScheme);
        log("resolveEnvBaseUrlForRequest -> " + resolved);
        log("ensureHttpSchemeForRequest(api.example.com) -> " + withScheme);
        end("envUrlSupport_resolvesJsonModule");
    }
}
