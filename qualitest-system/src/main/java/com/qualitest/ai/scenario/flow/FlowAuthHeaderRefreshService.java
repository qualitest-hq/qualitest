package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.RefreshAuthHeadersResult;
import com.qualitest.api.util.ManagedAuthHeaderApplier;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按当前项目鉴权配置，批量刷新本流 project HTTP 节点上的 profileManaged 托管头。
 * <p>
 * 只产出 {@link FlowDesignPatch}（updateNodes），不写 test_flow；由前端灌入 Staging 确认后保存。
 */
@Service
@RequiredArgsConstructor
public class FlowAuthHeaderRefreshService {

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    /**
     * 扫描 graphJson 中需登录的 project HTTP 节点，对变更者产出 updateNodes。
     */
    public RefreshAuthHeadersResult refresh(Long testProjectId, String graphJson) {
        if (testProjectId == null) {
            return emptyResult("testProjectId 不能为空");
        }
        GraphJson graph = GraphJson.parse(graphJson != null ? graphJson : "{}");
        if (graph == null) {
            graph = GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        String projectAuthJson = project != null ? project.getAuthConfig() : null;

        List<GraphNode> updates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();

        for (GraphNode node : nodes) {
            if (node == null || !"http".equalsIgnoreCase(trim(node.getType()))) {
                continue;
            }
            Map<String, Object> data = node.getData();
            if (data == null) {
                continue;
            }
            JSONObject dataJson = new JSONObject(data);
            if (!FlowHttpNodeVisitor.isProjectBoundHttp(dataJson)) {
                continue;
            }
            Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
            if (apiId == null) {
                continue;
            }
            TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
            if (api == null || api.getTestProjectId() == null || !api.getTestProjectId().equals(testProjectId)) {
                continue;
            }

            Map<String, Object> working = new HashMap<>(data);
            String nodeLabel = FlowHttpNodeVisitor.resolveNodeName(dataJson, node.getId());
            if (nodeLabel == null || nodeLabel.isBlank()) {
                nodeLabel = "HTTP 节点";
            }
            boolean changed = ManagedAuthHeaderApplier.applyToNodeData(
                    working,
                    api.getAuthConfig(),
                    projectAuthJson,
                    api.getApiPath(),
                    nodeLabel,
                    warnings);
            if (!changed) {
                continue;
            }
            Map<String, Object> patchData = new HashMap<>();
            patchData.put("headers", working.get("headers"));
            updates.add(GraphNode.builder()
                    .id(node.getId())
                    .type("http")
                    .data(patchData)
                    .build());
        }

        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(updates);
        patch.setSummary(updates.isEmpty()
                ? "无需刷新托管鉴权头"
                : "按项目鉴权刷新 " + updates.size() + " 个节点的托管头");

        return RefreshAuthHeadersResult.builder()
                .patch(patch)
                .warnings(warnings)
                .changedCount(updates.size())
                .message(updates.isEmpty()
                        ? "当前图中托管鉴权头已与项目配置一致，无需刷新"
                        : "已生成 " + updates.size() + " 处托管头刷新提案，请在 Staging 确认后保存")
                .build();
    }

    private static RefreshAuthHeadersResult emptyResult(String message) {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(new ArrayList<>());
        return RefreshAuthHeadersResult.builder()
                .patch(patch)
                .warnings(new ArrayList<>())
                .changedCount(0)
                .message(message)
                .build();
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
