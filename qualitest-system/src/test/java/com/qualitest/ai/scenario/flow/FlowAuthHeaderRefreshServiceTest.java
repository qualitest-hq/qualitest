package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.RefreshAuthHeadersResult;
import com.qualitest.project.auth.AuthHeaderResolver;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * FlowAuthHeaderRefreshService：清除设计期误落的托管头（补头仅运行期）。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowAuthHeaderRefreshServiceTest
 */
@ExtendWith(MockitoExtension.class)
class FlowAuthHeaderRefreshServiceTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long API_ID = 200L;

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
                .authConfig(com.qualitest.project.auth.AuthProfileTestFixtures.adminThenClientJson())
                .build());
    }

    /**
     * 前提：节点上有旧 profileManaged 行。
     * 期望：产出剥离 patch，headers 中不再有托管行。
     */
    @Test
    @DisplayName("清除旧 profileManaged 托管头")
    void refresh_stripsStaleManagedHeader() {
        when(apiMapper.selectTestProjectApiById(API_ID)).thenReturn(clientApi());

        Map<String, Object> stale = new HashMap<>();
        stale.put("_enabled", true);
        stale.put("name", "Authorization");
        stale.put("value", "Bearer {{flow.oldToken}}");
        stale.put(AuthHeaderResolver.PROFILE_MANAGED, true);

        GraphJson graph = graphWithHttp("n1", "查资料", API_ID, List.of(stale));
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());

        assertEquals(1, result.getChangedCount());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> headers =
                (List<Map<String, Object>>) result.getPatch().getUpdateNodes().get(0).getData().get("headers");
        assertTrue(headers == null || headers.stream().noneMatch(AuthHeaderResolver::isProfileManaged));
    }

    /**
     * 前提：仅有显式非托管头。
     * 期望：无变更。
     */
    @Test
    @DisplayName("显式非托管头不产生变更")
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

    /**
     * 前提：无托管行。
     * 期望：无变更。
     */
    @Test
    @DisplayName("无设计期托管头时无变更")
    void refresh_noManaged_noChange() {
        when(apiMapper.selectTestProjectApiById(API_ID)).thenReturn(clientApi());
        GraphJson graph = graphWithHttp("n1", "查资料", API_ID, new ArrayList<>());
        RefreshAuthHeadersResult result = service.refresh(PROJECT_ID, graph.toJsonString());
        assertEquals(0, result.getChangedCount());
    }

    private static TestProjectApi clientApi() {
        return TestProjectApi.builder()
                .testProjectApiId(API_ID)
                .testProjectId(PROJECT_ID)
                .apiName("资料")
                .apiPath("/api/account/auth/profile")
                .authConfig("{\"mode\":\"inherit\"}")
                .requestConfig("{\"method\":\"GET\"}")
                .build();
    }

    private static GraphJson graphWithHttp(String id, String name, Long apiId, List<Map<String, Object>> headers) {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(apiId));
        data.put("headers", new ArrayList<>(headers));
        GraphNode node = GraphNode.builder().id(id).type("http").name(name).data(data).build();
        return GraphJson.builder().nodes(new ArrayList<>(List.of(node))).edges(new ArrayList<>()).build();
    }
}
