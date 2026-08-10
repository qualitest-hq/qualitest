package com.qualitest.flow.validate;

import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：AuthTokenPresenceGate——图中需登录却缺对应端 flow 变量来源时返回错误。
 * 边界：仅缺 client / 仅缺 admin / 有一端不能代替另一端 / extracts 与 flowSeed 可满足 / mode=none 不检查。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AuthTokenPresenceGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthTokenPresenceGateTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    /**
     * 前提：图中仅有需客户端鉴权的 HTTP，无任何 token 写入来源。
     * 期望：一条 AUTH_TOKEN_MISSING，文案含 flow.token，不含 adminToken。
     */
    @Test
    @Order(1)
    @DisplayName("缺 client token 单独硬拦")
    void validate_missingClientToken_only() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("n1", 101L)))
                .build();
        List<String> errors = AuthTokenPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/profile", "inherit"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_TOKEN_MISSING:"));
        assertTrue(errors.get(0).contains("flow.token"));
        assertTrue(errors.stream().noneMatch(e -> e.contains("adminToken")));
    }

    /**
     * 前提：同时有客户端与管理端 HTTP，仅 extracts 写出 token。
     * 期望：仍报缺 adminToken（有客户端 token 不能代替管理端）。
     */
    @Test
    @Order(2)
    @DisplayName("有 client token 仍硬拦缺 admin")
    void validate_hasClientButMissingAdmin_stillFails() {
        GraphNode login = httpNode("login", 201L);
        login.getData().put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(login, httpNode("admin", 202L), httpNode("profile", 203L)))
                .build();
        List<String> errors = AuthTokenPresenceGate.validate(graph, PROJECT_AUTH, id -> {
            if (id == 201L || id == 203L) {
                return api(id, "/api/account/auth/profile", "inherit");
            }
            return api(id, "/system/user/list", "inherit");
        });
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_TOKEN_MISSING:"));
        assertTrue(errors.get(0).contains("adminToken"));
    }

    /**
     * 前提：场景 flowSeed 已有 adminToken，图中只有管理端 HTTP。
     * 期望：无错误。
     */
    @Test
    @Order(3)
    @DisplayName("flowSeed 提供 adminToken 则通过")
    void validate_flowSeedProvidesAdminToken_ok() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("s1")
                .name("默认")
                .flowSeed(new HashMap<>(Map.of("adminToken", "seed")))
                .build();
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("admin", 301L)))
                .meta(GraphMeta.builder().scenarios(List.of(scenario)).build())
                .build();
        List<String> errors = AuthTokenPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/system/user/list", "inherit"));
        assertTrue(errors.isEmpty());
    }

    /**
     * 前提：接口鉴权 mode=none（免登录）。
     * 期望：不要求 token，无错误。
     */
    @Test
    @Order(4)
    @DisplayName("免登录不硬拦")
    void validate_modeNone_ok() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("anon", 401L)))
                .build();
        List<String> errors = AuthTokenPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/api/catalog/list", "none"));
        assertTrue(errors.isEmpty());
    }

    private static GraphNode httpNode(String id, Long apiId) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", id);
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(apiId));
        return GraphNode.builder().id(id).type("http").data(data).build();
    }

    private static TestProjectApi api(Long id, String path, String mode) {
        return TestProjectApi.builder()
                .testProjectApiId(id)
                .apiPath(path)
                .authConfig("{\"mode\":\"" + mode + "\"}")
                .build();
    }
}
