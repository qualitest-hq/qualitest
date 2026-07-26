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
 * {@link FlowRunContextBuilder} 与 {@link EnvUrlSupport} 单元测试：验证运行上下文的组装。
 * <p>
 * {@link FlowRunContextBuilder#build} 将项目环境（envUrl、envVariables）、素材库 JSON、
 * 场景 flowSeed 合并为 {@link FlowRunContext} 的三个作用域：env / asset / flow。
 * {@link EnvUrlSupport} 负责从多模块 JSON envUrl 中取默认模块、补全 http 协议前缀。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowRunContextBuilderTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunContextBuilderTest {

    /**
     * 完整 build 路径：envUrl → env.baseUrl；envVariables → env.timeout；
     * assetJson → asset.defaults.clientId；flowSeed → flow.loginUser。
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
     * {@link EnvUrlSupport#resolveEnvBaseUrlForRequest}：多模块 JSON 取「默认模块」的 URL。
     * {@link EnvUrlSupport#ensureHttpSchemeForRequest}：无协议 host 自动补 http://。
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
