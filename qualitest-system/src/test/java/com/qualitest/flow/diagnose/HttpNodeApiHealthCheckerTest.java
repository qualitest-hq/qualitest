package com.qualitest.flow.diagnose;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpNodeApiHealthChecker 单元测试。
 * 覆盖：孤儿测值、抽取路径缺失/命中、API 缺失、参数名大小写忽略。
 */
class HttpNodeApiHealthCheckerTest {

    private final HttpNodeApiHealthChecker checker = new HttpNodeApiHealthChecker();

    @Test
    void pathMatchesSchema_exactAndPrefix() {
        Set<String> paths = Set.of("code", "data.token", "msg");
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data.token", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data", paths));
        assertTrue(HttpNodeApiHealthChecker.pathMatchesSchema("data.token.extra", paths));
        assertFalse(HttpNodeApiHealthChecker.pathMatchesSchema("data.userId", paths));
    }

    @Test
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

    @Test
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

    @Test
    void extractPathMissing_whenNotInSchema() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\"}")
                .responseConfig("{\"responses\":[{\"schema\":{\"code\":\"string\",\"data\":{\"token\":\"string\"}}}]}")
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

    @Test
    void extractPathOk_whenInSchema() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .requestConfig("{\"method\":\"GET\"}")
                .responseConfig("{\"responses\":[{\"schema\":{\"code\":\"string\",\"data\":{\"token\":\"string\"}}}]}")
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

    @Test
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

    @Test
    void collectParamNames_includesQueryAndBody() {
        Set<String> names = HttpNodeApiHealthChecker.collectParamNames(
                "{\"queryParams\":[{\"name\":\"q\"}],\"body\":{\"mode\":\"json\",\"json\":{\"example\":\"{\\\"a\\\":1}\"}}}",
                null);
        assertTrue(names.contains("q"));
        assertTrue(names.contains("a"));
    }

    @Test
    void checkNode_orphanAndExtractTogether() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(2L)
                .requestConfig("{\"method\":\"POST\",\"queryParams\":[{\"name\":\"keep\"}]}")
                .responseConfig("{\"responses\":[{\"schema\":{\"data\":{\"ok\":true}}}]}")
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
