package com.qualitest.flow.diagnose;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 HttpNodeApiHealthChecker：HTTP 节点相对 API 的健康告警。
 * 边界：纯函数/内存 API 对象；无 DB；含孤儿测值、抽取路径、API 缺失。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpNodeApiHealthCheckerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpNodeApiHealthCheckerTest {

    private final HttpNodeApiHealthChecker checker = new HttpNodeApiHealthChecker();

    /**
     * pathMatchesSchema：精确路径、父路径前缀均可命中；无关路径不命中。
     */
    @Test
    @Order(1)
    @DisplayName("路径精确与前缀匹配 schema")
    void pathMatchesSchema_exactAndPrefix() {
        Set<String> paths = Set.of("code", "data.token", "msg");
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data.token", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data.token.extra", paths));
        assertFalse(HttpNodeApiHealthChecker.pathMatchesSchema("data.userId", paths));
    }

    /**
     * 数组路径比对：下标、[*]、过滤器、误写的 .items 都会收成同一结构路径后与 data[*].xxx 叶子匹配。
     */
    @Test
    @Order(2)
    @DisplayName("数组下标/过滤器/.items 与 data[*] 叶子对齐")
    void pathMatchesSchema_arrayIndexFilterAndLegacyItems() {
        Set<String> paths = Set.of("data[*].quantity", "data[*].cartId");
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data[0].quantity", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data[*].quantity", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data[?(@.cartId=='5001')].quantity", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data.items.quantity", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("http.body.data[?(@.cartId==5001)].quantity", paths));
        assertFalse(HttpNodeApiHealthChecker.pathMatchesSchema("data.items.missing", paths));
    }

    /**
     * 前提：节点 overrides 含 API 未定义的参数 gone。
     * 期望：产生 ORPHAN_PARAM 告警，detail 含 gone。
     */
    @Test
    @Order(9)
    @DisplayName("孤儿参数产生 ORPHAN_PARAM")
    void orphanParam_warnsWhenNameMissingFromApi() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\",\"queryParams\":[{\"name\":\"pageNum\",\"value\":\"1\"}]}")
                .responseConfig("{}")
                .build();

        String graph = """
                {"nodes":[{"id":"n1","type":"http","data":{
                  "name":"列表",
                  "callMode":"project",
                  "testProjectApiId":"1",
                  "requestValueOverrides":{"paramDefaults":{"pageNum":"2","gone":"x"}}
                }}]}
                """;

        List<HttpNodeApiHealthWarning> warnings = checker.checkGraph(graph, id -> api);
        assertEquals(1, warnings.stream().filter(w -> HttpNodeApiHealthChecker.CODE_ORPHAN_PARAM.equals(w.code)).count());
        assertTrue(warnings.stream().anyMatch(w -> "gone".equals(w.detail)));
    }

    /**
     * 前提：overrides 参数名 PageNum 与 API 定义 pageNum 仅大小写不同。
     * 期望：不产生 ORPHAN_PARAM 告警。
     */
    @Test
    @Order(3)
    @DisplayName("参数名大小写不同不告警")
    void orphanParam_caseInsensitiveMatch_noWarn() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\",\"queryParams\":[{\"name\":\"pageNum\"}]}")
                .responseConfig("{}")
                .build();

        String graph = """
                {"nodes":[{"id":"n1","type":"http","data":{
                  "callMode":"project",
                  "testProjectApiId":"1",
                  "requestValueOverrides":{"paramDefaults":{"PageNum":"2"}}
                }}]}
                """;

        List<HttpNodeApiHealthWarning> warnings = checker.checkGraph(graph, id -> api);
        assertTrue(warnings.stream().noneMatch(w -> HttpNodeApiHealthChecker.CODE_ORPHAN_PARAM.equals(w.code)));
    }

    /**
     * 前提：extracts 含 schema 未定义的 $.data.userId 路径。
     * 期望：产生 EXTRACT_PATH_MISSING 告警，detail 含 userId。
     */
    @Test
    @Order(4)
    @DisplayName("抽取路径缺失告警")
    void extractPathMissing_whenNotInSchema() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\"}")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "code":{"type":"string"},
                          "data":{"type":"object","properties":{"token":{"type":"string"}}}
                        }}}]}
                        """)
                .build();

        String graph = """
                {"nodes":[{"id":"n1","type":"http","data":{
                  "name":"登录",
                  "callMode":"project",
                  "testProjectApiId":"1",
                  "extracts":[
                    {"name":"token","expr":"$.data.token","from":"body","scope":"flow"},
                    {"name":"uid","expr":"$.data.userId","from":"body","scope":"flow"}
                  ]
                }}]}
                """;

        List<HttpNodeApiHealthWarning> warnings = checker.checkGraph(graph, id -> api);
        List<HttpNodeApiHealthWarning> extractWarns = warnings.stream()
                .filter(w -> HttpNodeApiHealthChecker.CODE_EXTRACT_PATH_MISSING.equals(w.code))
                .toList();
        assertEquals(1, extractWarns.size());
        assertTrue(extractWarns.get(0).detail.contains("userId"));
    }

    /**
     * 前提：extracts 路径 $.data.token 在 response schema 中已定义。
     * 期望：不产生 EXTRACT_PATH_MISSING 告警。
     */
    @Test
    @Order(5)
    @DisplayName("抽取路径在 schema 中不告警")
    void extractPathOk_whenInSchema() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\"}")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "code":{"type":"string"},
                          "data":{"type":"object","properties":{"token":{"type":"string"}}}
                        }}}]}
                        """)
                .build();

        String graph = """
                {"nodes":[{"id":"n1","type":"http","data":{
                  "callMode":"project",
                  "testProjectApiId":"1",
                  "extracts":[{"name":"token","expr":"$.data.token","from":"body"}]
                }}]}
                """;

        List<HttpNodeApiHealthWarning> warnings = checker.checkGraph(graph, id -> api);
        assertTrue(warnings.stream().noneMatch(w -> HttpNodeApiHealthChecker.CODE_EXTRACT_PATH_MISSING.equals(w.code)));
    }

    /**
     * 前提：节点绑定 testProjectApiId=999，resolver 返回 null。
     * 期望：产生 API_MISSING 告警，共 1 条。
     */
    @Test
    @Order(6)
    @DisplayName("API 缺失产生 API_MISSING")
    void apiMissing_whenResolverReturnsNull() {
        String graph = """
                {"nodes":[{"id":"n1","type":"http","data":{
                  "name":"断链",
                  "callMode":"project",
                  "testProjectApiId":"999"
                }}]}
                """;
        Function<Long, TestProjectApi> resolver = id -> null;
        List<HttpNodeApiHealthWarning> warnings = checker.checkGraph(graph, resolver);
        assertEquals(1, warnings.size());
        assertEquals(HttpNodeApiHealthChecker.CODE_API_MISSING, warnings.get(0).code);
    }

    /**
     * 前提：requestConfig 含 queryParams 与 JSON body example 字段。
     * 期望：collectParamNames 返回 query 名与 body 内属性名。
     */
    @Test
    @Order(7)
    @DisplayName("collectParamNames 含 query 与 body")
    void collectParamNames_includesQueryAndBody() {
        Set<String> names = HttpNodeApiHealthChecker.collectParamNames(
                "{\"queryParams\":[{\"name\":\"q\"}],\"body\":{\"mode\":\"json\",\"json\":{\"example\":\"{\\\"a\\\":1}\"}}}",
                null);
        assertTrue(names.contains("q"));
        assertTrue(names.contains("a"));
    }

    /**
     * 前提：单节点同时含 orphan 参数 old 与 schema 缺失的 extract 路径。
     * 期望：同时产生 ORPHAN_PARAM 与 EXTRACT_PATH_MISSING 告警。
     */
    @Test
    @Order(8)
    @DisplayName("同节点孤儿参数与抽取缺失并存")
    void checkNode_orphanAndExtractTogether() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(2L)
                .requestConfig("{\"method\":\"POST\",\"queryParams\":[{\"name\":\"keep\"}]}")
                .responseConfig("""
                        {"responses":[{"schema":{"type":"object","properties":{
                          "data":{"type":"object","properties":{"ok":{"type":"boolean"}}}
                        }}}]}
                        """)
                .build();
        JSONObject data = new JSONObject();
        data.put("callMode", "project");
        data.put("testProjectApiId", "2");
        data.put("requestValueOverrides", JSONObject.parseObject("{\"paramDefaults\":{\"keep\":\"1\",\"old\":\"2\"}}"));
        data.put("extracts", JSON.parseArray("[{\"name\":\"x\",\"expr\":\"$.data.missing\",\"from\":\"body\"}]"));

        List<HttpNodeApiHealthWarning> warnings = checker.checkNode("n2", "节点2", data, 2L, api);
        assertTrue(warnings.stream().anyMatch(w -> HttpNodeApiHealthChecker.CODE_ORPHAN_PARAM.equals(w.code)));
        assertTrue(warnings.stream().anyMatch(w -> HttpNodeApiHealthChecker.CODE_EXTRACT_PATH_MISSING.equals(w.code)));
    }
}
