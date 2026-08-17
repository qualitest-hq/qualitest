package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.AuthDesignWarningCodes;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 设计期门禁：绑定项目接口的登录/注册 HTTP 节点必须配置对应 flow 变量名的 extract。
 * <p>
 * 扫描图中 project 绑定的 HTTP 节点；若接口路径为登录类且 extracts 缺少期望的 flowKey，则返回错误文案。
 * 当 loginHint 或可用 schema 能确定 expr 时，同时校验抽取路径。
 */
public final class LoginExtractPresenceGate {

    private LoginExtractPresenceGate() {}

    /**
     * @param graph           待检查画布
     * @param projectAuthJson 项目鉴权配置（用于解析期望的 flowKey），可空
     * @param apiResolver     按 testProjectApiId 取接口；返回 null 则跳过该节点
     * @return 错误文案列表；空表示通过
     */
    public static List<String> validate(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return errors;
        }
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if (!type.isEmpty() && !"http".equals(type)) {
                continue;
            }
            JSONObject data = new JSONObject(node.getData());
            if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
                continue;
            }
            Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
            if (apiId == null) {
                continue;
            }
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null || !LoginExtractSuggestor.isLoginLikeApi(api.getApiPath())) {
                continue;
            }
            String flowKey = LoginExtractSuggestor.resolveExpectedFlowKey(projectAuthJson, api.getApiPath());
            if (StrUtil.isBlank(flowKey)) {
                continue;
            }
            String nodeLabel = FlowHttpNodeVisitor.resolveNodeName(data, node.getId());
            if (nodeLabel == null || nodeLabel.isBlank()) {
                nodeLabel = "HTTP 节点";
            }
            Object extracts = node.getData().get("extracts");
            if (!LoginExtractSuggestor.extractsContainFlowKey(extracts, flowKey)) {
                errors.add(AuthDesignWarningCodes.loginExtractMissing(nodeLabel, flowKey));
                continue;
            }
            JSONObject schema = FlowDesignApiSummarizer.summarizeResponse(api.getResponseConfig());
            LoginExtractSuggestor.Suggestion suggestion = LoginExtractSuggestor.suggest(
                    projectAuthJson, api.getApiPath(), schema);
            if (suggestion == null) {
                continue;
            }
            String actualExpr = LoginExtractSuggestor.extractExprForFlowKey(extracts, flowKey);
            if (!LoginExtractSuggestor.exprsMatch(suggestion.expr(), actualExpr)) {
                errors.add(AuthDesignWarningCodes.loginExtractExprMismatch(
                        nodeLabel, flowKey, suggestion.expr(), actualExpr));
            }
        }
        return errors;
    }
}
