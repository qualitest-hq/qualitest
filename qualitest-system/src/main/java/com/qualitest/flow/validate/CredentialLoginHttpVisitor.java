package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;

import java.util.function.Function;

/**
 * 遍历图中命中 credentialApi 的 project HTTP 节点。
 * LoginExtractPresenceGate 与 LoginFlowKeyCollisionGate 共用扫描条件。
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
        FlowHttpNodeVisitor.visitProjectBound(graph, (node, data, apiId) -> {
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null || !LoginExtractSuggestor.hasCredentialLoginHint(
                    projectAuthJson, null, api.getApiPath())) {
                return;
            }
            consumer.accept(node, data, api);
        });
    }
}
