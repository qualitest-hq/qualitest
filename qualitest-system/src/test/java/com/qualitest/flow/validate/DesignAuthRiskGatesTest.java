package com.qualitest.flow.validate;

import com.qualitest.project.auth.AuthProfileTestFixtures;
import com.qualitest.project.auth.ProjectAuthConfigSupport;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：DesignAuthRiskGates——已去掉「图上是否写出鉴权头路径」硬拦。
 * 边界：仅有需登录 HTTP、无 extract 时不再产出 AUTH_TOKEN_MISSING。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=DesignAuthRiskGatesTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesignAuthRiskGatesTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    /**
     * 前提：图中仅有需客户端鉴权的 HTTP，无任何 extract/assign 写出头模板路径。
     * 期望：collect 结果不含 AUTH_TOKEN_MISSING（缺路径交给真跑占位符）。
     */
    @Test
    @Order(1)
    @DisplayName("不再因缺写出路径硬拦 AUTH_TOKEN_MISSING")
    void collect_noTokenPresenceHardBlock() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(httpNode("n1", 101L)))
                .build();
        List<String> errors = DesignAuthRiskGates.collect(
                graph, PROJECT_AUTH, id -> api(id, "/api/account/auth/profile", "inherit"), null);
        assertTrue(errors.stream().noneMatch(e -> e.startsWith("AUTH_TOKEN_MISSING:")),
                () -> "unexpected: " + errors);
    }

    private static GraphNode httpNode(String id, long apiId) {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(apiId));
        data.put("httpMethod", "GET");
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
