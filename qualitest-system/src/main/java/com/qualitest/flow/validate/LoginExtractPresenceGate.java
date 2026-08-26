package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.AuthDesignWarningCodes;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 设计期门禁：绑定项目接口的登录/注册 HTTP 节点必须配置对应凭证目标的 extract。
 * <p>
 * 扫描图中 project 绑定的 HTTP 节点；若接口路径为登录类且 extracts 缺少期望的
 * credential displayPath（asset.x.y / flow.x），则返回错误文案。
 * 当托管头目标或可用 schema 能确定 expr 时，同时校验抽取路径。
 * 末尾附带 LoginFlowKeyCollisionGate：不同登录口写出同一凭证目标时硬拦。
 */
public final class LoginExtractPresenceGate {

    private LoginExtractPresenceGate() {}

    /**
     * @param graph           待检查画布
     * @param projectAuthJson 项目鉴权配置（用于解析期望的凭证目标），可空
     * @param apiResolver     按 testProjectApiId 取接口；返回 null 则跳过该节点
     * @return 错误文案列表；空表示通过
     */
    public static List<String> validate(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        CredentialLoginHttpVisitor.visit(graph, projectAuthJson, apiResolver, (node, data, api) -> {
            CredentialTarget target = LoginExtractSuggestor.resolveExpectedTarget(
                    projectAuthJson, null, api.getApiPath());
            if (target == null) {
                return;
            }
            String nodeLabel = FlowHttpNodeVisitor.resolveNodeName(data, node.getId());
            if (nodeLabel == null || nodeLabel.isBlank()) {
                nodeLabel = "HTTP 节点";
            }
            Object extracts = node.getData().get("extracts");
            if (!CredentialTargetSupport.extractsContainTarget(extracts, target)) {
                errors.add(AuthDesignWarningCodes.loginExtractMissing(nodeLabel, target.displayPath()));
                return;
            }
            JSONObject schema = FlowDesignApiSummarizer.summarizeResponse(api.getResponseConfig());
            LoginExtractSuggestor.Suggestion suggestion = LoginExtractSuggestor.suggest(
                    projectAuthJson, api.getApiPath(), schema);
            if (suggestion == null) {
                return;
            }
            String actualExpr = CredentialTargetSupport.extractExprForTarget(extracts, target);
            if (!LoginExtractSuggestor.exprsMatch(suggestion.expr(), actualExpr)) {
                errors.add(AuthDesignWarningCodes.loginExtractExprMismatch(
                        nodeLabel, target.displayPath(), suggestion.expr(), actualExpr));
            }
        });
        errors.addAll(LoginFlowKeyCollisionGate.validate(graph, projectAuthJson, apiResolver));
        return errors;
    }
}
