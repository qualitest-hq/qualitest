package com.qualitest.flow.validate;

import com.qualitest.api.util.AuthProfileTestFixtures;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
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
 * 测 LoginExtractPresenceGate：登录口必须写出期望 flowKey；expr 仅在 hint/schema 可确定时硬拦。
 * 边界：双端模板 /login；Map schema 无 hint 只查名。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=LoginExtractPresenceGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginExtractPresenceGateTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    /**
     * 前提：双端模板；/login 节点无 extracts。
     * 期望：硬拦，文案含 adminToken。
     */
    @Test
    @Order(1)
    @DisplayName("登录口无 extract 硬拦")
    void missingExtract_fails() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("login", 1L)))
                .build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/login"));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_LOGIN_EXTRACT_MISSING:"));
        assertTrue(errors.get(0).contains("adminToken"));
    }

    /**
     * 前提：双端模板；/login 已抽取 adminToken / $.token。
     * 期望：通过。
     */
    @Test
    @Order(2)
    @DisplayName("登录口有正确 extract 通过")
    void hasMatchingExtract_ok() {
        GraphNode n = httpNode("login", 1L);
        n.getData().put("extracts", List.of(Map.of(
                "name", "adminToken",
                "scope", "flow",
                "expr", "$.token"
        )));
        GraphJson graph = GraphJson.builder().nodes(List.of(n)).build();

        assertTrue(LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/login")).isEmpty());
    }

    /**
     * 前提：双端模板；/login 名为 adminToken 但 expr=$.data.token。
     * 期望：路径不符硬拦。
     */
    @Test
    @Order(3)
    @DisplayName("loginHint 已知时错误 expr 硬拦")
    void wrongExpr_failsWhenHintKnown() {
        GraphNode n = httpNode("login", 1L);
        n.getData().put("extracts", List.of(Map.of(
                "name", "adminToken",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder().nodes(List.of(n)).build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/login"));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_LOGIN_EXTRACT_MISSING:"));
        assertTrue(errors.get(0).contains("$.token"));
        assertTrue(errors.get(0).contains("$.data.token"));
    }

    /**
     * 前提：无项目鉴权；/login schema 为 Map；extracts 名为 adminToken、expr 任意。
     * 期望：只要求名字，不拦路径。
     */
    @Test
    @Order(4)
    @DisplayName("Map schema 无 hint 时不拦路径")
    void mapSchema_doesNotBlockExpr() {
        GraphNode n = httpNode("login", 1L);
        n.getData().put("extracts", List.of(Map.of(
                "name", "adminToken",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder().nodes(List.of(n)).build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, null, id -> apiWithMapSchema(id, "/login"));

        assertTrue(errors.isEmpty());
    }

    /**
     * 前提：双端模板；客户端登录口无 extracts。
     * 期望：硬拦，文案含 token。
     */
    @Test
    @Order(5)
    @DisplayName("客户端登录口无 extract 硬拦")
    void clientLogin_missingExtract_fails() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("clientLogin", 2L)))
                .build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/login"));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_LOGIN_EXTRACT_MISSING:"));
        assertTrue(errors.get(0).contains("token"));
    }

    /**
     * 前提：双端模板；客户端登录已抽取 token / $.data.token。
     * 期望：通过。
     */
    @Test
    @Order(6)
    @DisplayName("客户端登录口有正确 extract 通过")
    void clientLogin_hasMatchingExtract_ok() {
        GraphNode n = httpNode("clientLogin", 2L);
        n.getData().put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder().nodes(List.of(n)).build();

        assertTrue(LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/login")).isEmpty());
    }

    /**
     * 前提：管理端与客户端登录口都抽出 token。
     * 期望：AUTH_LOGIN_FLOWKEY_COLLISION。
     */
    @Test
    @Order(7)
    @DisplayName("两端登录都抽 token 硬拦碰撞")
    void dualLogin_sameFlowKey_collision() {
        GraphNode admin = httpNode("adminLogin", 1L);
        admin.getData().put("extracts", List.of(
                Map.of("name", "adminToken", "scope", "flow", "expr", "$.token"),
                Map.of("name", "token", "scope", "flow", "expr", "$.token")
        ));
        GraphNode client = httpNode("clientLogin", 2L);
        client.getData().put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "flow",
                "expr", "$.data.token"
        )));
        GraphJson graph = GraphJson.builder().nodes(List.of(admin, client)).build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, id == 1L ? "/login" : "/api/account/auth/login"));

        assertTrue(errors.stream().anyMatch(e -> e.startsWith("AUTH_LOGIN_FLOWKEY_COLLISION:")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("adminToken") || e.contains("token")));
    }

    /**
     * 前提：两个相同 path 的客户端登录都抽 token。
     * 期望：不算跨端覆盖，不报 COLLISION。
     */
    @Test
    @Order(8)
    @DisplayName("同一登录口重复节点不报碰撞")
    void sameEndpoint_twoNodes_noCollision() {
        GraphNode a = httpNode("loginA", 2L);
        a.getData().put("extracts", List.of(Map.of(
                "name", "token", "scope", "flow", "expr", "$.data.token")));
        GraphNode b = httpNode("loginB", 3L);
        b.getData().put("extracts", List.of(Map.of(
                "name", "token", "scope", "flow", "expr", "$.data.token")));
        GraphJson graph = GraphJson.builder().nodes(List.of(a, b)).build();

        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/login"));

        assertTrue(errors.stream().noneMatch(e -> e.startsWith("AUTH_LOGIN_FLOWKEY_COLLISION:")));
    }

    private static GraphNode httpNode(String id, Long apiId) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", id);
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(apiId));
        return GraphNode.builder().id(id).type("http").data(data).build();
    }

    private static TestProjectApi api(Long id, String path) {
        return TestProjectApi.builder()
                .testProjectApiId(id)
                .apiPath(path)
                .authConfig("{\"mode\":\"none\"}")
                .build();
    }

    private static TestProjectApi apiWithMapSchema(Long id, String path) {
        return TestProjectApi.builder()
                .testProjectApiId(id)
                .apiPath(path)
                .authConfig("{\"mode\":\"none\"}")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "threshold":{"type":"integer"},
                          "loadFactor":{"type":"number"}
                        }}}]}
                        """)
                .build();
    }
}
