package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.AuthHeaderHintSupport;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.ResponseConventionSupport;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装单条项目接口的造流摘要对象。
 * <p>
 * 输出字段包括：id、method、path、名称与说明、designHints、分组、请求参数与 body 示例、
 * 响应 schema、本端响应约定四字段、鉴权提示。
 * extracts 由 AI/人按 schema 与 Profile.headerValueTemplate 自定，不另返回建议列表。
 */
final class ApiDetailPayloadBuilder {

    private ApiDetailPayloadBuilder() {}

    /**
     * @param conventionJson  该接口 path 命中端的响应约定 JSON，可空（空则按缺省四字段）
     * @param projectAuthJson 项目多端配置 JSON，可空（用于登录 designHints 与 header 提示）
     */
    static JSONObject build(TestProjectApi api, String conventionJson, String projectAuthJson) {
        TestProjectApiEffectiveConfigResolver.EffectiveApiConfig effective =
                TestProjectApiEffectiveConfigResolver.resolve(api);
        JSONObject result = new JSONObject();
        result.put("testProjectApiId", String.valueOf(api.getTestProjectApiId()));
        result.put("method", FlowDesignApiSummarizer.resolveMethod(effective.toApiView(api)));
        result.put("path", api.getApiPath());
        result.put("name", api.getApiName());
        result.put("description", api.getApiDescription() != null ? api.getApiDescription() : "");
        List<String> designHints = new ArrayList<>(
                TestProjectApiDesignHintsService.readHintList(api.getDesignHints()));
        JSONObject responseSchemaSummary = FlowDesignApiSummarizer.summarizeResponse(effective.getResponseConfig());
        LoginExtractSuggestor.Suggestion loginSuggestion =
                LoginExtractSuggestor.suggest(projectAuthJson, api.getApiPath(), responseSchemaSummary);
        prependLoginDesignHint(designHints, projectAuthJson, api.getApiPath(), loginSuggestion);
        result.put("designHints", designHints);
        if (api.getApiGroupId() != null) {
            result.put("apiGroupId", String.valueOf(api.getApiGroupId()));
        }
        if (api.getApiGroup() != null && !api.getApiGroup().isBlank()) {
            result.put("apiGroup", api.getApiGroup());
        }
        JSONObject requestSummary = FlowDesignApiSummarizer.summarizeRequest(
                effective.getRequestConfig(), effective.getHeaders());
        result.put("queryParams", requestSummary.getJSONArray("queryParams"));
        result.put("pathParams", requestSummary.getJSONArray("pathParams"));
        result.put("headerParams", requestSummary.getJSONArray("headerParams"));
        result.put("bodyParams", requestSummary.getJSONArray("bodyParams"));
        result.put("bodyExample", requestSummary.getString("bodyExample"));
        result.put("bodySchemaLeaves", requestSummary.getJSONArray("bodySchemaLeaves"));
        result.put("responseSchemaSummary", responseSchemaSummary);
        result.put("responseSchemaLeaves",
                FlowDesignApiSummarizer.summarizeResponseLeaves(effective.getResponseConfig()));

        JSONObject convention = ResponseConventionSupport.toJsonObject(conventionJson);
        result.put("responseConvention", convention);
        AuthHeaderHintSupport.putAuthFields(result, api.getAuthConfig(), projectAuthJson, api.getApiPath());
        return result;
    }

    static final String LOGIN_EXTRACT_HINT_UNKNOWN =
            "响应结构不足以确定 token 路径，跑一次后按真实 body 再改";

    /**
     * 登录类 path 在 designHints 首部补一条抽取说明（按托管头目标）；已有相同文案不重复。
     * 登录口 extracts 须按 headerValueTemplate 与响应 schema 自定。
     */
    static void prependLoginDesignHint(
            List<String> hints,
            String projectAuthJson,
            String apiPath,
            LoginExtractSuggestor.Suggestion suggestion) {
        if (hints == null || !LoginExtractSuggestor.isLoginLikeApi(apiPath)) {
            return;
        }
        String extra;
        if (suggestion != null) {
            extra = "登录抽取请用 " + suggestion.expr() + " → " + suggestion.toTarget().displayPath()
                    + "，对齐 Profile.headerValueTemplate，不要套用另一端路径";
        } else {
            extra = LOGIN_EXTRACT_HINT_UNKNOWN;
        }
        if (hints.contains(extra)) {
            return;
        }
        hints.add(0, extra);
    }
}
