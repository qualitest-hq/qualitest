package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.RefreshAuthHeadersResult;
import com.qualitest.project.auth.ManagedAuthHeaderApplier;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
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
 * 清掉本流 project HTTP 节点上误落的 profileManaged 托管头。
 * <p>
 * 托管头只在运行期按项目配置补；本服务只产出剥离 patch（updateNodes），
 * 不写 test_flow；由前端灌入 Staging 确认后保存。
 */
@Service
@RequiredArgsConstructor
public class FlowAuthHeaderRefreshService {

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    /**
     * 扫描 graphJson，对含 profileManaged 行的 project HTTP 节点产出剥离 patch。
     */
    public RefreshAuthHeadersResult refresh(Long testProjectId, String graphJson) {
        if (testProjectId == null) {
            return emptyResult("testProjectId 不能为空");
        }
        // 保留查项目：无项目时仍允许剥离（不依赖当前 auth 配置）
        if (testProjectMapper != null) {
            testProjectMapper.selectTestProjectById(testProjectId);
        }
        GraphJson graph = GraphJson.parse(graphJson != null ? graphJson : "{}");
        if (graph == null) {
            graph = GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        }

        List<GraphNode> updates = new ArrayList<>();
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();

        for (GraphNode node : nodes) {
            if (node == null || !"http".equalsIgnoreCase(StrUtil.trimToEmpty(node.getType()))) {
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
            boolean changed = ManagedAuthHeaderApplier.stripManagedFromNodeData(working);
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
                ? "节点上无设计期托管鉴权头，开跑时按项目配置自动带"
                : "清除 " + updates.size() + " 个节点上的设计期托管头（开跑时按项目配置自动带）");

        return RefreshAuthHeadersResult.builder()
                .patch(patch)
                .changedCount(updates.size())
                .message(updates.isEmpty()
                        ? "节点上无设计期托管鉴权头"
                        : "已生成清除 " + updates.size() + " 个节点托管头的提案")
                .build();
    }

    private static RefreshAuthHeadersResult emptyResult(String message) {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setUpdateNodes(List.of());
        patch.setSummary(message);
        return RefreshAuthHeadersResult.builder()
                .patch(patch)
                .changedCount(0)
                .message(message)
                .build();
    }
}
