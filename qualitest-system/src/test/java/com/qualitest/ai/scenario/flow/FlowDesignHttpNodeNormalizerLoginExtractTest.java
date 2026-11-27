package com.qualitest.ai.scenario.flow;

import com.qualitest.api.util.AuthProfileTestFixtures;
import com.qualitest.api.util.ProjectAuthConfigSupport;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测 FlowDesignHttpNodeNormalizer 对登录口 extracts 的处理。
 * 边界：空 extracts 不自动补；已有行保持原样。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignHttpNodeNormalizerLoginExtractTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignHttpNodeNormalizerLoginExtractTest {

    /**
     * 前提：双端模板；客户端登录；extracts 为空；响应 schema 含 data.token。
     * 期望：空 extracts 不自动补行。
     */
    @Test
    @Order(1)
    @DisplayName("空 extracts 不自动补登录 extract")
    void emptyExtracts_notFilled() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/api/account/auth/login")
                .authConfig("{\"mode\":\"none\"}")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "data":{"type":"object","properties":{"token":{"type":"string"}}}
                        }}}]}
                        """)
                .build();
        String projectAuth = ProjectAuthConfigSupport.toJson(
                AuthProfileTestFixtures.adminThenClient());

        FlowDesignHttpNodeNormalizer.normalize(data, api, projectAuth);

        assertNull(data.get("extracts"));
    }

    /**
     * 前提：单端 RuoYi 模板；管理端 /login；已有 flow token + $.data.token。
     * 期望：凭证行保持原样。
     */
    @Test
    @Order(2)
    @DisplayName("已有凭证行保持原样")
    void existingCredentialRow_unchanged() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("extracts", List.of(Map.of(
                "name", "token",
                "expr", "$.data.token",
                "scope", "flow",
                "from", "body"
        )));
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/login")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "token":{"type":"string"}
                        }}}]}
                        """)
                .build();

        FlowDesignHttpNodeNormalizer.normalize(
                data, api, ProjectAuthConfigSupport.toJson(ProjectAuthConfigSupport.ruoyiBearerTemplate()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) data.get("extracts");
        assertEquals(1, extracts.size());
        assertEquals("flow", extracts.get(0).get("scope"));
        assertEquals("token", extracts.get(0).get("name"));
        assertEquals("$.data.token", extracts.get(0).get("expr"));
    }

    /**
     * 前提：双端模板；管理端 /login；extract 名为 token、expr 为 $.custom。
     * 期望：自定义路径保持原样。
     */
    @Test
    @Order(3)
    @DisplayName("自定义 expr 保持原样")
    void customExpr_unchanged() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("extracts", List.of(Map.of(
                "name", "token",
                "expr", "$.custom",
                "scope", "flow",
                "from", "body"
        )));
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/login")
                .build();

        FlowDesignHttpNodeNormalizer.normalize(
                data, api, AuthProfileTestFixtures.adminThenClientJson());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) data.get("extracts");
        assertEquals(1, extracts.size());
        assertEquals("token", extracts.get(0).get("name"));
        assertEquals("$.custom", extracts.get(0).get("expr"));
    }
}
