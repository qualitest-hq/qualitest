package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 汇总当前接口给模型阅读的上下文：
 * method/path/说明、参数约束与测值预览、body/response schema 叶节点、脚本预览等。
 * <p>
 * 读取时叠加测值层到有效配置；脚本预览优先用请求里的草稿。
 * 返回前按设计上下文形状做字节上限裁剪（先删大体量字段，再裁参数数组）。
 */
@RequiredArgsConstructor
public class GetApiDesignContextTool implements ApiDesignTool {

    /** 脚本预览最大字符数，超出截断加省略号 */
    private static final int SCRIPT_PREVIEW_MAX = 400;

    private final TestProjectApiMapper testProjectApiMapper;

    @Override
    public String getName() {
        return ApiDesignToolNames.GET_API_DESIGN_CONTEXT.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        Long apiId = ctx.getTestProjectApiId();
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

        TestProjectApiEffectiveConfigResolver.EffectiveApiConfig effective =
                TestProjectApiEffectiveConfigResolver.resolve(api);

        JSONObject result = new JSONObject();
        result.put("anchorType", "api_asset");
        result.put("testProjectApiId", String.valueOf(api.getTestProjectApiId()));
        result.put("name", api.getApiName());
        result.put("apiDescription", api.getApiDescription() != null ? api.getApiDescription() : "");
        result.put("method", FlowDesignApiSummarizer.resolveMethod(effective.toApiView(api)));
        result.put("path", api.getApiPath());

        JSONObject requestSummary = FlowDesignApiSummarizer.summarizeRequest(
                effective.getRequestConfig(), effective.getHeaders());
        result.put("queryParams", requestSummary.getJSONArray("queryParams"));
        result.put("pathParams", requestSummary.getJSONArray("pathParams"));
        result.put("headerParams", requestSummary.getJSONArray("headerParams"));
        result.put("bodyParams", requestSummary.getJSONArray("bodyParams"));
        result.put("bodyExample", requestSummary.getString("bodyExample"));
        result.put("bodySchemaLeaves", requestSummary.getJSONArray("bodySchemaLeaves"));
        result.put("responseSchemaLeaves",
                FlowDesignApiSummarizer.summarizeResponseLeaves(effective.getResponseConfig()));
        result.put("testValueSummary",
                FlowDesignApiSummarizer.summarizeTestValueConfig(api.getTestValueConfig()));

        String pre = resolveScript(ctx.getPreRequestScript(), api.getPreRequestScript());
        String post = resolveScript(ctx.getPostRequestScript(), api.getPostRequestScript());
        result.put("hasPreScript", hasText(pre));
        result.put("hasPostScript", hasText(post));
        result.put("preRequestScriptPreview", truncate(pre, SCRIPT_PREVIEW_MAX));
        result.put("postRequestScriptPreview", truncate(post, SCRIPT_PREVIEW_MAX));

        if (ctx.getWorkbenchSnapshot() != null && !ctx.getWorkbenchSnapshot().isBlank()) {
            result.put("workbenchSnapshot", tryParseOrRaw(ctx.getWorkbenchSnapshot()));
        }

        return ToolResultByteFit.fitApiDesignContext(result, ctx.getMaxToolResultBytes());
    }

    /** 优先草稿，否则库内脚本 */
    private static String resolveScript(String draft, String stored) {
        if (draft != null) {
            return draft;
        }
        return stored != null ? stored : "";
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /** 尝试把快照解析为 JSON，失败则原样返回字符串 */
    private static Object tryParseOrRaw(String raw) {
        try {
            return JSON.parse(raw);
        } catch (Exception ignored) {
            return raw;
        }
    }
}
