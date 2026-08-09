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
 * 测谁：{@link AuthTokenPresenceGate} 分端缺 token soft warning。
 * 边界：仅 client / 仅 admin / 两端都缺 / extracts 已提供 / 有任一端不代表另一端过。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AuthTokenPresenceGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthTokenPresenceGateTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    /**
     * 前提：图中仅有需 clientBearer 的 HTTP，无 token 来源。
     * 期望：一条提示含 flow.token，不含 adminToken。
     */
    @Test
    @Order(1)
    @DisplayName("缺 client token 单独提示")
    void warn_missingClientToken_only() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("n1", 101L)))
                .build();
        List<String> warnings = AuthTokenPresenceGate.warn(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/profile", "inherit"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("AUTH_TOKEN_MISSING:"));
        assertTrue(warnings.get(0).contains("flow.token"));
        assertTrue(warnings.stream().noneMatch(w -> w.contains("adminToken")));
    }

    /**
     * 前提：同时有 client 与 admin HTTP，且仅 extracts 出 token。
     * 期望：仍提示缺 adminToken（不可「有任一 token 即过」）。
     */
    @Test
    @Order(2)
    @DisplayName("有 client token 仍提示缺 admin")
    void warn_hasClientButMissingAdmin_stillWarns() {
        GraphNode login = httpNode("login", 201L);
        login.getData().put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(login, httpNode("admin", 202L), httpNode("profile", 203L)))
                .build();
        List<String> warnings = AuthTokenPresenceGate.warn(graph, PROJECT_AUTH, id -> {
            if (id == 201L || id == 203L) {
                return api(id, "/api/account/auth/profile", "inherit");
            }
            return api(id, "/system/user/list", "inherit");
        });
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("AUTH_TOKEN_MISSING:"));
        assertTrue(warnings.get(0).contains("adminToken"));
    }

    /**
     * 前提：flowSeed 已有 adminToken，HTTP 仅管理端。
     * 期望：无 warning。
     */
    @Test
    @Order(3)
    @DisplayName("flowSeed 提供 adminToken 则通过")
    void warn_flowSeedProvidesAdminToken_ok() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("s1")
                .name("默认")
                .flowSeed(new HashMap<>(Map.of("adminToken", "seed")))
                .build();
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("admin", 301L)))
                .meta(GraphMeta.builder().scenarios(List.of(scenario)).build())
                .build();
        List<String> warnings = AuthTokenPresenceGate.warn(
                graph, PROJECT_AUTH, id -> api(id, "/system/user/list", "inherit"));
        assertTrue(warnings.isEmpty());
    }

    /**
     * 前提：接口 mode=none。
     * 期望：不要求 token。
     */
    @Test
    @Order(4)
    @DisplayName("免登录不提示")
    void warn_modeNone_noWarning() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("anon", 401L)))
                .build();
        List<String> warnings = AuthTokenPresenceGate.warn(
                graph, PROJECT_AUTH, id -> api(id, "/api/catalog/list", "none"));
        assertTrue(warnings.isEmpty());
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
