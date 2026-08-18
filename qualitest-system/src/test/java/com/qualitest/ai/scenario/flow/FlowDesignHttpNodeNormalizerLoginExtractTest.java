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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowDesignHttpNodeNormalizer 登录 extract 对齐。
 * 边界：有 loginHint 才补/纠；Map schema 不编路径；自定义 expr 不改。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignHttpNodeNormalizerLoginExtractTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignHttpNodeNormalizerLoginExtractTest {

    /**
     * 前提：双端模板；客户端登录；extracts 为空。
     * 期望：补 token / $.data.token。
     */
    @Test
    @Order(1)
    @DisplayName("空 extracts 时按 loginHint 补登录 extract")
    void alignLoginExtract_whenEmpty_fillsFromHint() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/api/account/auth/login")
                .authConfig("{\"mode\":\"none\"}")
                .build();
        String projectAuth = ProjectAuthConfigSupport.toJson(
                AuthProfileTestFixtures.adminThenClient());

        FlowDesignHttpNodeNormalizer.normalize(data, api, projectAuth);

        Object raw = data.get("extracts");
        assertInstanceOf(List.class, raw);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) raw;
        assertEquals(1, extracts.size());
        assertEquals("token", extracts.get(0).get("name"));
        assertEquals("$.data.token", extracts.get(0).get("expr"));
    }

    /**
     * 前提：双端模板；管理端 /login；AI 写成 token + $.data.token。
     * 期望：纠成 adminToken + $.token。
     */
    @Test
    @Order(2)
    @DisplayName("管理端错误凭证行按 hint 纠正")
    void alignLoginExtract_rewritesWrongAdminPath() {
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
                .build();

        FlowDesignHttpNodeNormalizer.normalize(
                data, api, AuthProfileTestFixtures.adminThenClientJson());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) data.get("extracts");
        assertEquals(1, extracts.size());
        assertEquals("adminToken", extracts.get(0).get("name"));
        assertEquals("$.token", extracts.get(0).get("expr"));
    }

    /**
     * 前提：双端模板；管理端 /login；extract 名为 token、expr 为 $.custom。
     * 期望：不改自定义路径。
     */
    @Test
    @Order(3)
    @DisplayName("自定义 expr 不覆盖")
    void alignLoginExtract_skipsCustomExpr() {
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

    /**
     * 前提：无项目鉴权；/login 响应 schema 为 Map；已有 token + $.data.token。
     * 期望：不编、不改写成 $.token。
     */
    @Test
    @Order(4)
    @DisplayName("Map schema 无 hint 时不改 extracts")
    void alignLoginExtract_mapSchema_doesNotInventPath() {
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
                          "threshold":{"type":"integer"},
                          "loadFactor":{"type":"number"}
                        }}}]}
                        """)
                .build();

        FlowDesignHttpNodeNormalizer.normalize(data, api, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) data.get("extracts");
        assertEquals("token", extracts.get(0).get("name"));
        assertEquals("$.data.token", extracts.get(0).get("expr"));
    }

    /**
     * 前提：无项目鉴权；/login；extracts 为空；schema 为 Map。
     * 期望：不凭空插入 extract。
     */
    @Test
    @Order(5)
    @DisplayName("Map schema 空 extracts 不自动补路径")
    void alignLoginExtract_mapSchemaEmpty_doesNotFill() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/login")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "threshold":{"type":"integer"}
                        }}}]}
                        """)
                .build();

        FlowDesignHttpNodeNormalizer.normalize(data, api, null);

        assertNull(data.get("extracts"));
        assertTrue(data.containsKey("successCheck") || data.get("successCheck") != null);
    }
}
