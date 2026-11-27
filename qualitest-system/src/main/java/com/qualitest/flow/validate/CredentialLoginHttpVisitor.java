package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.project.auth.ManagedAuthHeaderApplier;
import com.qualitest.project.auth.ProjectAuthConfigSupport;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;

import java.util.Set;
import java.util.function.Function;

/**
 * 遍历图中「extracts 写出项目托管头模板占位路径」的 project HTTP 节点。
 * LoginFlowKeyCollisionGate 等共用此扫描条件；空 extracts 不回调。
 */
final class CredentialLoginHttpVisitor {

    private CredentialLoginHttpVisitor() {}

    @FunctionalInterface
    interface Consumer {
        void accept(GraphNode node, JSONObject data, TestProjectApi api);
    }

    static void visit(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver,
            Consumer consumer) {
        if (graph == null || apiResolver == null || consumer == null) {
            return;
        }
        ProjectAuthConfig config = ProjectAuthConfigSupport.parse(projectAuthJson);
        Set<String> required = ManagedAuthHeaderApplier.placeholderPathsInProjectAuth(config);
        FlowHttpNodeVisitor.visitProjectBound(graph, (node, data, apiId) -> {
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null || !ManagedAuthHeaderApplier.extractsWriteAnyOf(
                    data != null ? data.get("extracts") : null, required)) {
                return;
            }
            consumer.accept(node, data, api);
        });
    }
}
