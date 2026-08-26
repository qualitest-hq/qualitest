package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.RefreshAuthHeadersResult;
import com.qualitest.api.util.AuthHeaderResolver;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * FlowAuthHeaderRefreshService：按项目配置批量刷新托管头。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowAuthHeaderRefreshServiceTest
 */
@ExtendWith(MockitoExtension.class)
class FlowAuthHeaderRefreshServiceTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long API_ID = 200L;
    private static final Long ANON_API_ID = 201L;

    @Mock
    private TestProjectApiMapper apiMapper;
    @Mock
    private TestProjectMapper projectMapper;

    private FlowAuthHeaderRefreshService service;

    @BeforeEach
    void setUp() {
        service = new FlowAuthHeaderRefreshService(apiMapper, projectMapper);
        when(projectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig(com.qualitest.api.util.AuthProfileTestFixtures.adminThenClientJson())
                .build());
    }

    @Test
    @DisplayName("旧 profileManaged 模板按当前项目配置刷新")
    void refresh_updatesStaleManagedHeader() {
        when(apiMapper.selectTestProjectApiById(API_ID)).thenReturn(clientApi());

        Map<String, Object> stale = new HashMap<>();
        stale.put("_enabled", true);
        stale.put("name", "Authorization");
        stale.put("value", "Bearer {{flow.oldToken}}");
        stale.put(AuthHeaderResolver.PROFILE_MANAGED, true);

        GraphJson graph = graphWithHttp("n1", "查资料", API_ID, List.of(stale));
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());

        assertEquals(1, result.getChangedCount());
        assertNotNull(result.getPatch());
        assertEquals(1, result.getPatch().getUpdateNodes().size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> headers =
                (List<Map<String, Object>>) result.getPatch().getUpdateNodes().get(0).getData().get("headers");
        Map<String, Object> auth = headers.stream()
                .filter(h -> "Authorization".equalsIgnoreCase(String.valueOf(h.get("name"))))
                .findFirst()
                .orElseThrow();
        assertEquals("Bearer {{asset.clientAuth.token}}", auth.get("value"));
        assertTrue(AuthHeaderResolver.isProfileManaged(auth));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.startsWith("AUTH_HEADER_MANAGED:")));
    }

    @Test
    @DisplayName("显式非托管头不被刷新覆盖")
    void refresh_skipsExplicitHeader() {
        when(apiMapper.selectTestProjectApiById(API_ID)).thenReturn(clientApi());

        Map<String, Object> explicit = new HashMap<>();
        explicit.put("_enabled", true);
        explicit.put("name", "Authorization");
        explicit.put("value", "Bearer hacked");

        GraphJson graph = graphWithHttp("n1", "越权", API_ID, List.of(explicit));
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());

        assertEquals(0, result.getChangedCount());
        assertTrue(result.getPatch().getUpdateNodes() == null
                || result.getPatch().getUpdateNodes().isEmpty());
    }

    @Test
    @DisplayName("mode=none 不补头")
    void refresh_noneMode_noChange() {
        when(apiMapper.selectTestProjectApiById(ANON_API_ID)).thenReturn(TestProjectApi.builder()
                .testProjectApiId(ANON_API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("分类")
                .apiPath("/api/mall/category/list")
                .authConfig("{\"mode\":\"none\"}")
                .build());

        GraphJson graph = graphWithHttp("n2", "分类", ANON_API_ID, new ArrayList<>());
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());

        assertEquals(0, result.getChangedCount());
        assertTrue(result.getWarnings().stream().noneMatch(w -> w.startsWith("AUTH_HEADER_MANAGED:")));
    }

    @Test
    @DisplayName("已与项目配置一致时无变更")
    void refresh_alreadyCurrent_empty() {
        when(apiMapper.selectTestProjectApiById(API_ID)).thenReturn(clientApi());

        Map<String, Object> current = new HashMap<>();
        current.put("_enabled", true);
        current.put("name", "Authorization");
        current.put("value", "Bearer {{asset.clientAuth.token}}");
        current.put(AuthHeaderResolver.PROFILE_MANAGED, true);

        GraphJson graph = graphWithHttp("n1", "查资料", API_ID, List.of(current));
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());

        assertEquals(0, result.getChangedCount());
        assertTrue(result.getPatch().getUpdateNodes() == null
                || result.getPatch().getUpdateNodes().isEmpty());
    }

    private static TestProjectApi clientApi() {
        return TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("当前用户")
                .apiPath("/api/account/auth/profile")
                .authConfig("{\"mode\":\"inherit\",\"authProfileId\":\"clientBearer\"}")
                .build();
    }

    private static GraphJson graphWithHttp(
            String nodeId, String name, Long apiId, List<Map<String, Object>> headers) {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("name", name);
        data.put("testProjectApiId", String.valueOf(apiId));
        data.put("headers", headers);
        GraphNode node = GraphNode.builder()
                .id(nodeId)
                .type("http")
                .data(data)
                .build();
        return GraphJson.builder()
                .nodes(new ArrayList<>(List.of(node)))
                .edges(new ArrayList<>())
                .build();
    }
}
