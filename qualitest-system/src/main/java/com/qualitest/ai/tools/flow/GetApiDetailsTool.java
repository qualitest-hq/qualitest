package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批量查询项目接口摘要（参数、schema、designHints、响应约定、suggestedExtracts、鉴权提示）。
 * <p>
 * 入参 {@code testProjectApiIds} 为字符串数组；查单条时传长度为 1 的数组。
 * 去重后最多返回 MAX_IDS 条；超过部分不查库，并在 hint 中说明。
 * 结果超字节上限时：尾部接口写入 deferredIds，再压缩/最小化仍保留的条目，有数据时至少留 1 条。
 */
@RequiredArgsConstructor
public class GetApiDetailsTool implements QualitestTool {

    /** 单次调用最多组装并返回的接口条数（入参去重后截取）。 */
    public static final int MAX_IDS = 5;

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_API_DETAILS.getId();
    }

    /**
     * 按 id 列表加载未删除且属于当前项目的接口，组装 apis；不存在的 id 记入 missingIds。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return executeTemplateDetails(arguments, ctx);
        }
        List<Long> requested = parseApiIds(arguments != null ? arguments.get("testProjectApiIds") : null);
        if (requested.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectApiIds（字符串数组，单条也请传 [id]）");
        }
        boolean countTruncated = requested.size() > MAX_IDS;
        List<Long> ids = countTruncated ? requested.subList(0, MAX_IDS) : requested;

        String projectAuthJson = null;
        if (ctx.getTestProjectId() != null) {
            TestProject project = testProjectMapper.selectTestProjectById(ctx.getTestProjectId());
            if (project != null) {
                projectAuthJson = project.getAuthConfig();
            }
        }

        JSONArray apis = new JSONArray();
        JSONArray missingIds = new JSONArray();
        for (Long apiId : ids) {
            TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
            if (api == null || (api.getDelStatus() != null && api.getDelStatus() != 0)) {
                missingIds.add(String.valueOf(apiId));
                continue;
            }
            if (api.getTestProjectId() == null || !api.getTestProjectId().equals(ctx.getTestProjectId())) {
                missingIds.add(String.valueOf(apiId));
                continue;
            }
            String conventionJson = ProjectAuthConfigSupport.resolveResponseConventionJson(
                    api.getApiPath(), projectAuthJson);
            apis.add(ApiDetailPayloadBuilder.build(api, conventionJson, projectAuthJson));
        }

        JSONObject result = new JSONObject();
        result.put("apis", apis);
        if (!missingIds.isEmpty()) {
            result.put("missingIds", missingIds);
        }
        List<String> hints = new ArrayList<>();
        if (countTruncated) {
            hints.add("testProjectApiIds 超过上限 " + MAX_IDS + "，已截断；请缩小范围或分批再调");
        }
        return fitToByteLimit(result, hints, ctx.getMaxToolResultBytes());
    }

    private String executeTemplateDetails(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        List<String> requested = parseApiIdStrings(arguments != null ? arguments.get("testProjectApiIds") : null);
        if (requested.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectApiIds（字符串数组，单条也请传 [id]）");
        }
        boolean countTruncated = requested.size() > MAX_IDS;
        List<String> ids = countTruncated ? requested.subList(0, MAX_IDS) : requested;

        JSONArray apis = new JSONArray();
        JSONArray missingIds = new JSONArray();
        for (String apiId : ids) {
            JSONObject raw = TemplateApiCatalogSupport.findById(ctx.getTemplateApis(), apiId);
            if (raw == null) {
                missingIds.add(apiId);
                continue;
            }
            apis.add(TemplateApiCatalogSupport.buildDetail(raw, apiId));
        }
        JSONObject result = new JSONObject();
        result.put("apis", apis);
        if (!missingIds.isEmpty()) {
            result.put("missingIds", missingIds);
        }
        List<String> hints = new ArrayList<>();
        if (countTruncated) {
            hints.add("testProjectApiIds 超过上限 " + MAX_IDS + "，已截断；请缩小范围或分批再调");
        }
        return fitToByteLimit(result, hints, ctx.getMaxToolResultBytes());
    }

    private static List<String> parseApiIdStrings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                String id = arr.getString(i);
                if (id != null && !id.isBlank()) {
                    out.add(id.trim());
                }
            }
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String id = String.valueOf(item).trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /**
     * 按字节上限适配批量接口结果。
     * <ol>
     *   <li>从尾部移出多余 apis → deferredIds</li>
     *   <li>仅剩 1 条仍超限 → 去掉 schema/示例等大字段</li>
     *   <li>再超限 → 只留 id/method/path/name/鉴权与抽取建议等关键字段</li>
     *   <li>仍超限 → 应急包只保留每条 id/path/method/name</li>
     * </ol>
     */
    public static String fitToByteLimit(JSONObject result, List<String> hints, int maxBytes) {
        if (maxBytes <= 0) {
            maxBytes = AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;
        }
        JSONArray apis = result.getJSONArray("apis");
        if (apis == null) {
            apis = new JSONArray();
            result.put("apis", apis);
        }
        JSONArray deferredIds = new JSONArray();

        while (ToolResultByteFit.utf8Len(result) > maxBytes && apis.size() > 1) {
            JSONObject dropped = apis.getJSONObject(apis.size() - 1);
            apis.remove(apis.size() - 1);
            if (dropped != null && dropped.getString("testProjectApiId") != null) {
                deferredIds.add(dropped.getString("testProjectApiId"));
            }
        }
        if (!deferredIds.isEmpty()) {
            result.put("deferredIds", deferredIds);
            hints.add("结果过大，已从尾部暂缓 " + deferredIds.size()
                    + " 条（见 deferredIds），请对这些 id 再调一次 get_api_details");
        }

        if (ToolResultByteFit.utf8Len(result) > maxBytes && apis.size() == 1) {
            slimApiDetail(apis.getJSONObject(0));
            hints.add("单条摘要仍偏大，已压缩 schema/示例字段");
        }

        if (ToolResultByteFit.utf8Len(result) > maxBytes && apis.size() == 1) {
            apis.set(0, minimalApiDetail(apis.getJSONObject(0)));
            hints.add("已降为最小字段（id/method/path/name/auth/suggestedExtracts）");
        }

        boolean truncated = !hints.isEmpty()
                || Boolean.TRUE.equals(result.getBoolean("truncated"))
                || !deferredIds.isEmpty();
        if (truncated) {
            result.put("truncated", true);
        }
        if (!hints.isEmpty()) {
            result.put("hint", String.join("；", hints));
        }

        if (ToolResultByteFit.utf8Len(result) <= maxBytes) {
            return result.toJSONString();
        }
        // 连最小摘要也超限：仍返回 id 级信息，避免空壳
        JSONObject emergency = new JSONObject();
        emergency.put("truncated", true);
        emergency.put("hint", result.getString("hint") != null
                ? result.getString("hint") + "；仍超限，仅保留 id"
                : "结果过大，仅保留 id");
        JSONArray idsOnly = new JSONArray();
        for (int i = 0; i < apis.size(); i++) {
            JSONObject row = apis.getJSONObject(i);
            if (row == null) {
                continue;
            }
            JSONObject idRow = new JSONObject();
            idRow.put("testProjectApiId", row.getString("testProjectApiId"));
            idRow.put("path", row.getString("path"));
            idRow.put("method", row.getString("method"));
            idRow.put("name", row.getString("name"));
            idsOnly.add(idRow);
        }
        emergency.put("apis", idsOnly);
        if (result.getJSONArray("missingIds") != null) {
            emergency.put("missingIds", result.getJSONArray("missingIds"));
        }
        if (result.getJSONArray("deferredIds") != null) {
            emergency.put("deferredIds", result.getJSONArray("deferredIds"));
        }
        return emergency.toJSONString();
    }

    /** 去掉 schema、示例、参数明细等大体量字段，保留鉴权与抽取建议等造流常用项。 */
    static void slimApiDetail(JSONObject detail) {
        if (detail == null) {
            return;
        }
        detail.remove("bodyExample");
        detail.remove("bodySchemaLeaves");
        detail.remove("responseSchemaLeaves");
        detail.remove("responseSchemaSummary");
        detail.remove("description");
        detail.remove("queryParams");
        detail.remove("pathParams");
        detail.remove("headerParams");
        detail.remove("bodyParams");
    }

    /** 只保留 id、method、path、name、designHints、suggestedExtracts、鉴权相关字段。 */
    static JSONObject minimalApiDetail(JSONObject detail) {
        JSONObject min = new JSONObject();
        if (detail == null) {
            return min;
        }
        copyIfPresent(detail, min, "testProjectApiId");
        copyIfPresent(detail, min, "method");
        copyIfPresent(detail, min, "path");
        copyIfPresent(detail, min, "name");
        copyIfPresent(detail, min, "designHints");
        copyIfPresent(detail, min, "suggestedExtracts");
        copyIfPresent(detail, min, "auth");
        copyIfPresent(detail, min, "headerHint");
        copyIfPresent(detail, min, "responseConvention");
        return min;
    }

    private static void copyIfPresent(JSONObject from, JSONObject to, String key) {
        if (from.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }

    /** 解析 testProjectApiIds：支持 JSON 数组、List、数组或单个值；去重并保持首次出现顺序。 */
    static List<Long> parseApiIds(Object raw) {
        Set<Long> seen = new LinkedHashSet<>();
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                Long id = FlowDesignToolSupport.longArg(arr.get(i));
                if (id != null) {
                    seen.add(id);
                }
            }
            return new ArrayList<>(seen);
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = FlowDesignToolSupport.longArg(item);
                if (id != null) {
                    seen.add(id);
                }
            }
            return new ArrayList<>(seen);
        }
        if (raw instanceof Object[] array) {
            for (Object item : array) {
                Long id = FlowDesignToolSupport.longArg(item);
                if (id != null) {
                    seen.add(id);
                }
            }
            return new ArrayList<>(seen);
        }
        Long one = FlowDesignToolSupport.longArg(raw);
        if (one != null) {
            seen.add(one);
        }
        return new ArrayList<>(seen);
    }
}
