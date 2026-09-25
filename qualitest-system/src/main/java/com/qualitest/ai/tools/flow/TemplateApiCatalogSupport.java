package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.ExpectedResponseKindSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模板 designMode 下内联 templateApis 的检索与详情组装。
 */
final class TemplateApiCatalogSupport {

    private TemplateApiCatalogSupport() {}

    /** 解析合成 id；优先 testProjectApiId，否则按列表下标生成 tpl-{index} */
    static String resolveSyntheticId(JSONObject api, int index) {
        if (api == null) {
            return "tpl-" + index;
        }
        String id = api.getString("testProjectApiId");
        if (id != null && !id.isBlank()) {
            return id.trim();
        }
        return "tpl-" + index;
    }

    static String resolveMethod(JSONObject api) {
        if (api == null) {
            return "GET";
        }
        JSONObject requestConfig = api.getJSONObject("requestConfig");
        if (requestConfig != null) {
            String method = requestConfig.getString("method");
            if (method != null && !method.isBlank()) {
                return method.trim().toUpperCase(Locale.ROOT);
            }
        }
        String method = api.getString("httpMethod");
        if (method != null && !method.isBlank()) {
            return method.trim().toUpperCase(Locale.ROOT);
        }
        return "GET";
    }

    static boolean matchesKeyword(JSONObject api, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        return contains(api.getString("apiName"), kw)
                || contains(api.getString("apiPath"), kw)
                || contains(api.getString("apiDescription"), kw)
                || contains(resolveMethod(api), kw);
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    /** 按关键词过滤内联接口，返回摘要项列表（已截断前的全量匹配） */
    static List<JSONObject> search(JSONArray templateApis, String keyword) {
        List<JSONObject> matched = new ArrayList<>();
        if (templateApis == null || templateApis.isEmpty()) {
            return matched;
        }
        for (int i = 0; i < templateApis.size(); i++) {
            JSONObject api = templateApis.getJSONObject(i);
            if (api == null) {
                continue;
            }
            String path = api.getString("apiPath");
            if (path == null || path.isBlank()) {
                continue;
            }
            if (!matchesKeyword(api, keyword)) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("id", resolveSyntheticId(api, i));
            item.put("method", resolveMethod(api));
            item.put("path", path.trim());
            item.put("name", api.getString("apiName") != null ? api.getString("apiName") : path.trim());
            item.put("auth", api.get("authConfig"));
            matched.add(item);
        }
        return matched;
    }

    /** 按合成 id 查找内联接口原文 */
    static JSONObject findById(JSONArray templateApis, String apiId) {
        if (templateApis == null || apiId == null || apiId.isBlank()) {
            return null;
        }
        String want = apiId.trim();
        for (int i = 0; i < templateApis.size(); i++) {
            JSONObject api = templateApis.getJSONObject(i);
            if (api == null) {
                continue;
            }
            if (want.equals(resolveSyntheticId(api, i))) {
                return api;
            }
        }
        return null;
    }

    /**
     * 将预制接口 JSON 转为造流详情摘要（尽量对齐 ApiDetailPayloadBuilder 关键字段）。
     */
    static JSONObject buildDetail(JSONObject api, String syntheticId) {
        JSONObject result = new JSONObject();
        result.put("testProjectApiId", syntheticId);
        result.put("method", resolveMethod(api));
        result.put("path", api.getString("apiPath"));
        result.put("name", api.getString("apiName"));
        result.put("description", api.getString("apiDescription") != null ? api.getString("apiDescription") : "");
        Object hints = api.get("designHints");
        if (hints instanceof JSONObject hintsObj && hintsObj.get("hints") instanceof JSONArray) {
            result.put("designHints", hintsObj.getJSONArray("hints"));
        } else if (hints instanceof JSONArray) {
            result.put("designHints", hints);
        } else {
            result.put("designHints", new JSONArray());
        }
        if (api.getString("apiGroup") != null) {
            result.put("apiGroup", api.getString("apiGroup"));
        }
        String requestConfigJson = stringifyJsonField(api.get("requestConfig"));
        String headersJson = stringifyJsonField(api.get("headers"));
        String responseConfigJson = stringifyJsonField(api.get("responseConfig"));
        JSONObject requestSummary = FlowDesignApiSummarizer.summarizeRequest(requestConfigJson, headersJson);
        result.put("queryParams", requestSummary.getJSONArray("queryParams"));
        result.put("pathParams", requestSummary.getJSONArray("pathParams"));
        result.put("headerParams", requestSummary.getJSONArray("headerParams"));
        result.put("bodyParams", requestSummary.getJSONArray("bodyParams"));
        result.put("bodyExample", requestSummary.getString("bodyExample"));
        result.put("bodySchemaLeaves", requestSummary.getJSONArray("bodySchemaLeaves"));
        result.put("responseSchemaSummary", FlowDesignApiSummarizer.summarizeResponse(responseConfigJson));
        result.put("responseSchemaLeaves", FlowDesignApiSummarizer.summarizeResponseLeaves(responseConfigJson));
        // 期望响应形态，供造探活 Conditon 使用
        result.put("expectedResponseKind",
                ExpectedResponseKindSupport.fromResponseConfigJson(responseConfigJson));
        result.put("responseConvention", new JSONObject());
        result.put("auth", api.get("authConfig"));
        return result;
    }

    private static String stringifyJsonField(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return JSONObject.toJSONString(value);
    }
}
