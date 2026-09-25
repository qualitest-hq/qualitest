package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;

import java.util.Set;
import java.util.function.Function;

/**
 * 遍历图中「extracts 写出托管头凭证目标」的 project HTTP 节点。
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
        Set<String> required = ProjectAuthConfigSupport.collectCredentialIdentityKeys(config);
        FlowHttpNodeVisitor.visitProjectBound(graph, (node, data, apiId) -> {
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null || !producesManagedCredential(data != null ? data.get("extracts") : null, required)) {
                return;
            }
            consumer.accept(node, data, api);
        });
    }

    /** extracts 是否写出项目托管头上的任一凭证目标。 */
    static boolean producesManagedCredential(Object extracts, Set<String> requiredKeys) {
        if (requiredKeys == null || requiredKeys.isEmpty()) {
            return LoginExtractSuggestor.extractsContainAnyFlowKey(
                    extracts, Set.of("token", "adminToken"));
        }
        for (CredentialTarget produced : CredentialTargetSupport.listProducedTargets(extracts)) {
            if (produced != null && requiredKeys.contains(produced.identityKey())) {
                return true;
            }
        }
        return false;
    }
}
