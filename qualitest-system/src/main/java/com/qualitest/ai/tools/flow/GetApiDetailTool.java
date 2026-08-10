package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.api.util.AuthHeaderHintSupport;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.support.ResponseConventionSupport;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 查询单条项目接口的请求/响应摘要。
 * <p>
 * 返回方法、路径、参数与 body 示例、响应 schema 摘要；
 * 并附带项目响应约定（responseConvention）、建议 extracts（suggestedExtracts），
 * 造流设计提示 designHints（人机维护，导入不覆盖），
 * 以及鉴权摘要 {@code auth} / {@code headerHint}（与造流/Run 同一套解析）。
 * 建议 extracts 只列出业务数据包装字段（如 data）下的路径，方便生成 HTTP 节点提取规则。
 */
@RequiredArgsConstructor
public class GetApiDetailTool implements QualitestTool {

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_API_DETAIL.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long apiId = FlowDesignToolSupport.longArg(arguments.get("testProjectApiId"));
        if (apiId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectApiId");
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
        if (api == null || api.getDelStatus() != null && api.getDelStatus() != 0) {
            return FlowDesignToolSupport.errorJson("接口不存在");
        }
        if (api.getTestProjectId() == null || !api.getTestProjectId().equals(ctx.getTestProjectId())) {
            return FlowDesignToolSupport.errorJson("接口不属于当前项目");
        }
        // 叠加上测试值层中的参数默认值、body/响应示例，再做语义摘要
        TestProjectApiEffectiveConfigResolver.EffectiveApiConfig effective =
                TestProjectApiEffectiveConfigResolver.resolve(api);
        JSONObject result = new JSONObject();
        result.put("testProjectApiId", String.valueOf(api.getTestProjectApiId()));
        result.put("method", FlowDesignApiSummarizer.resolveMethod(
                effective.toApiView(api)));
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

        // 附带项目响应约定、鉴权摘要；并按 dataPath 从 schema 推导建议 extracts
        String conventionJson = null;
        String projectAuthJson = null;
        if (ctx.getTestProjectId() != null) {
            TestProject project = testProjectMapper.selectTestProjectById(ctx.getTestProjectId());
            if (project != null) {
                conventionJson = project.getResponseConvention();
                projectAuthJson = project.getAuthConfig();
            }
        }
        JSONObject convention = ResponseConventionSupport.toJsonObject(conventionJson);
        result.put("responseConvention", convention);
        result.put("suggestedExtracts", FlowDesignApiSummarizer.suggestExtracts(
                responseSchemaSummary, convention.getString("dataPath")));
        AuthHeaderHintSupport.putAuthFields(result, api.getAuthConfig(), projectAuthJson, api.getApiPath());
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }
}
