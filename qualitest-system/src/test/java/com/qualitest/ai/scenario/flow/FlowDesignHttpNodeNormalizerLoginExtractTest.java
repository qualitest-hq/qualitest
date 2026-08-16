package com.qualitest.ai.scenario.flow;

import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDesignHttpNodeNormalizerLoginExtractTest {

    @Test
    @DisplayName("空 extracts 时按端补登录 extract")
    void ensureLoginExtract_whenEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/api/account/auth/login")
                .authConfig("{\"mode\":\"none\"}")
                .build();
        String projectAuth = ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.dualBearerTemplate());
        FlowDesignHttpNodeNormalizer.normalize(data, api, projectAuth);
        Object raw = data.get("extracts");
        assertInstanceOf(List.class, raw);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) raw;
        assertEquals(1, extracts.size());
        assertEquals("token", extracts.get(0).get("name"));
        assertEquals("$.data.token", extracts.get(0).get("expr"));
    }

    @Test
    @DisplayName("已有 extracts 不覆盖")
    void ensureLoginExtract_skipsWhenPresent() {
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
                data, api, ProjectAuthConfigSupport.toJson(ProjectAuthConfigSupport.dualBearerTemplate()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extracts = (List<Map<String, Object>>) data.get("extracts");
        assertEquals(1, extracts.size());
        assertTrue(String.valueOf(extracts.get(0).get("expr")).contains("custom")
                || "$.custom".equals(extracts.get(0).get("expr"))
                || "$.data.custom".equals(extracts.get(0).get("expr")));
    }
}
