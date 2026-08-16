package com.qualitest.flow.validate;

import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginExtractPresenceGateTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    @Test
    @DisplayName("登录口无 extract 硬拦")
    void missingExtract_fails() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("login", 1L)))
                .build();
        List<String> errors = LoginExtractPresenceGate.validate(
                graph, PROJECT_AUTH, id -> api(id, "/login"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("AUTH_LOGIN_EXTRACT_MISSING:"));
        assertTrue(errors.get(0).contains("adminToken") || errors.get(0).contains("token"));
    }

    @Test
    @DisplayName("登录口有 extract 通过")
    void hasExtract_ok() {
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
}
