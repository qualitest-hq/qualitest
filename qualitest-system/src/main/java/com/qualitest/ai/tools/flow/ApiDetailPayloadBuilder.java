package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.AuthHeaderHintSupport;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.ResponseConventionSupport;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;

/**
 * 组装单条项目接口的造流摘要对象。
 * <p>
 * 输出字段包括：id、method、path、名称与说明、designHints、分组、请求参数与 body 示例、
 * 响应 schema、项目响应约定、suggestedExtracts（登录口优先插入 token 抽取建议）、鉴权提示。
 */
final class ApiDetailPayloadBuilder {

    private ApiDetailPayloadBuilder() {}

    /**
     * @param conventionJson  项目响应约定 JSON，可空
     * @param projectAuthJson 项目鉴权配置 JSON，可空（用于登录 extract 与 header 提示）
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
        result.put("designHints", TestProjectApiDesignHintsService.readHintList(api.getDesignHints()));
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
        JSONObject responseSchemaSummary = FlowDesignApiSummarizer.summarizeResponse(effective.getResponseConfig());
        result.put("responseSchemaSummary", responseSchemaSummary);
        result.put("responseSchemaLeaves",
                FlowDesignApiSummarizer.summarizeResponseLeaves(effective.getResponseConfig()));

        JSONObject convention = ResponseConventionSupport.toJsonObject(conventionJson);
        result.put("responseConvention", convention);
        // 按响应约定 dataPath 从 schema 推断的抽取建议
        JSONArray schemaSuggested = FlowDesignApiSummarizer.suggestExtracts(
                responseSchemaSummary, convention.getString("dataPath"));
        // 登录/注册口：优先按项目鉴权配置给出 token extract，并置于 suggestedExtracts 首位
        LoginExtractSuggestor.Suggestion loginSuggestion =
                LoginExtractSuggestor.suggest(projectAuthJson, api.getApiPath(), responseSchemaSummary);
        if (loginSuggestion != null) {
            JSONArray merged = new JSONArray();
            merged.add(new JSONObject(loginSuggestion.toExtractRow()));
            for (int i = 0; i < schemaSuggested.size(); i++) {
                JSONObject row = schemaSuggested.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                String name = row.getString("name");
                if (name != null && name.equals(loginSuggestion.name())) {
                    continue;
                }
                merged.add(row);
            }
            result.put("suggestedExtracts", merged);
        } else {
            result.put("suggestedExtracts", schemaSuggested);
        }
        AuthHeaderHintSupport.putAuthFields(result, api.getAuthConfig(), projectAuthJson, api.getApiPath());
        return result;
    }
}
