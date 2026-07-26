package com.qualitest.api.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualitest.api.result.ApiImportMergeResult;
import com.qualitest.api.result.ApiImportMergeSummary;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.api.util.ApiImportConfigPipeline;
import com.qualitest.api.util.ApiSchemaSoftMergeSupport;
import com.qualitest.project.domain.TestProjectApi;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 已有 API 重复上传时的 request/response 结构合并。
 * <p>
 * 上传包决定参数列表与 schema 形状；同名字段且类型未变时保留本地 pattern/min/max 等约束；
 * 用户调试默认值、body/响应 example 写入 test_value_config。新增接口不走本类，由导入入口直接全量落库。
 */
@Service
public class ApiImportMergeService {

    /** request 中 query 参数数组字段名 */
    private static final String FIELD_QUERY = "queryParams";
    /** request 中 path 参数数组字段名 */
    private static final String FIELD_PATH = "pathParams";
    /** request 中声明式 Header 参数数组字段名 */
    private static final String FIELD_HEADERS = "declaredHeaders";

    /**
     * 对库中已有 API 做一次结构合并。
     *
     * @param existing            库中当前 API（含 request_config、response_config、test_value_config）
     * @param incomingRequestRaw  上传包 requestConfig 原始 JSON
     * @param incomingResponseRaw 上传包 responseConfig 原始 JSON
     * @return 合并后的 request_config、response_config、test_value_config（及单测用摘要）
     */
    public ApiImportMergeResult merge(TestProjectApi existing, String incomingRequestRaw, String incomingResponseRaw) {
        ApiImportMergeSummary summary = ApiImportMergeSummary.builder().build();

        // 上传包先规范化并补缺失 example，再参与合并
        ObjectNode incomingRequest = ApiConfigJsonSupport.parseObjectOrEmpty(
                ApiImportConfigPipeline.normalizeAndEnrichRequest(incomingRequestRaw));
        ObjectNode incomingResponse = ApiConfigJsonSupport.parseObjectOrEmpty(
                ApiImportConfigPipeline.normalizeAndEnrichResponse(incomingResponseRaw));

        ObjectNode existingRequest = ApiConfigJsonSupport.parseObjectOrEmpty(existing.getRequestConfig());
        ObjectNode existingResponse = ApiConfigJsonSupport.parseObjectOrEmpty(existing.getResponseConfig());

        ObjectNode testValueRoot = ApiConfigJsonSupport.parseObjectOrEmpty(existing.getTestValueConfig());
        ObjectNode testRequest = ApiConfigJsonSupport.ensureObject(testValueRoot, "request");
        ObjectNode testResponse = ApiConfigJsonSupport.ensureObject(testValueRoot, "response");

        ObjectNode mergedRequest = mergeRequestConfig(existingRequest, incomingRequest, testRequest, summary);
        ObjectNode mergedResponse = mergeResponseConfig(existingResponse, incomingResponse, testResponse, summary);

        summary.setSchemaUpdated(true);

        return ApiImportMergeResult.builder()
                .requestConfig(ApiConfigJsonSupport.writeCompact(mergedRequest))
                .responseConfig(ApiConfigJsonSupport.writeCompact(mergedResponse))
                .testValueConfig(ApiConfigJsonSupport.writeCompact(testValueRoot))
                .mergeSummary(summary)
                .build();
    }

    /**
     * 合并请求结构：以上传包为形状基线，按参数名 soft merge 三类参数数组，
     * 再对 body.json.schema 做字段级 soft merge，并处理 body 示例迁移。
     */
    private static ObjectNode mergeRequestConfig(
            ObjectNode existing,
            ObjectNode incoming,
            ObjectNode testRequest,
            ApiImportMergeSummary summary) {
        ObjectNode out = incoming.deepCopy();
        out.put("configVersion", ApiConfigJsonSupport.CONFIG_VERSION);

        mergeParamArray(FIELD_QUERY, existing, incoming, out, testRequest, summary);
        mergeParamArray(FIELD_PATH, existing, incoming, out, testRequest, summary);
        mergeParamArray(FIELD_HEADERS, existing, incoming, out, testRequest, summary);

        mergeBodySchema(existing, out);
        mergeBodyExample(existing, out, testRequest, summary);
        return out;
    }

    /**
     * 按参数 name 对齐一组参数数组。
     * <ul>
     *   <li>上传有、本地无：记为新增，结构层采用上传项（去掉 value）</li>
     *   <li>上传无、本地有：默认值写入 paramDefaults，参数名记入 removedParams，结构层不再保留该参数</li>
     *   <li>两边都有且类型未变：结构用上传项 + 本地用户约束；类型变更则仅用上传项</li>
     * </ul>
     */
    private static void mergeParamArray(
            String field,
            ObjectNode existing,
            ObjectNode incoming,
            ObjectNode out,
            ObjectNode testRequest,
            ApiImportMergeSummary summary) {
        ArrayNode existingArr = ApiConfigJsonSupport.arrayOrEmpty(existing.get(field));
        ArrayNode incomingArr = ApiConfigJsonSupport.arrayOrEmpty(incoming.get(field));

        Map<String, JsonNode> existingByName = indexByName(existingArr);
        Map<String, JsonNode> incomingByName = indexByName(incomingArr);
        Set<String> removedNames = new HashSet<>(existingByName.keySet());
        removedNames.removeAll(incomingByName.keySet());

        for (String removed : removedNames) {
            captureParamValue(existingByName.get(removed), removed, testRequest);
            trackParamRemoved(summary, field, removed);
            appendRemovedParam(testRequest, removed);
        }

        ArrayNode mergedArr = JsonNodeFactory.instance.arrayNode();
        for (JsonNode incomingParam : incomingArr) {
            String name = ApiConfigJsonSupport.textField(incomingParam, "name");
            ObjectNode stripped = stripParamValue(incomingParam);
            if (name == null) {
                mergedArr.add(stripped);
                continue;
            }
            JsonNode localParam = existingByName.get(name);
            if (localParam == null) {
                trackParamAdded(summary, field, name);
                mergedArr.add(stripped);
            } else {
                captureParamValue(localParam, name, testRequest);
                mergedArr.add(ApiSchemaSoftMergeSupport.mergeParamNode(localParam, stripped));
            }
        }
        out.set(field, mergedArr);
    }

    /**
     * 对 body.json.schema 做字段级 soft merge：形状跟上传包，未变字段保留本地约束。
     */
    private static void mergeBodySchema(ObjectNode existing, ObjectNode out) {
        JsonNode outBody = out.get("body");
        if (outBody == null || !outBody.isObject()) {
            return;
        }
        JsonNode outJson = outBody.get("json");
        if (outJson == null || !outJson.isObject()) {
            return;
        }

        JsonNode localSchema = null;
        JsonNode existingBody = existing.get("body");
        if (existingBody != null && existingBody.isObject()) {
            JsonNode existingJson = existingBody.get("json");
            if (existingJson != null && existingJson.isObject()) {
                localSchema = existingJson.get("schema");
            }
        }

        ApiSchemaSoftMergeSupport.setOrNull(
                (ObjectNode) outJson,
                "schema",
                ApiSchemaSoftMergeSupport.mergeSchema(localSchema, outJson.get("schema")));
    }

    /**
     * 把本地 body.json.example 迁到 test_value_config.request.bodyExample；
     * 有 bodyExample 时从结构层删掉 example，避免两处重复存同一份示例。
     */
    private static void mergeBodyExample(
            ObjectNode existing,
            ObjectNode out,
            ObjectNode testRequest,
            ApiImportMergeSummary summary) {
        String legacyExample = extractBodyExample(existing.get("body"));
        if (legacyExample != null && !testRequest.has("bodyExample")) {
            testRequest.put("bodyExample", legacyExample);
        }

        if (testRequest.has("bodyExample")) {
            summary.addUserPreserved("bodyExample");
            stripBodyExampleFromStructure(out);
        }
    }

    /**
     * 合并响应结构：按响应 id（或 httpStatus+name）对齐。
     * 用户 example 写入 examplesById，合并结果里按 id 回填；
     * 上传包已删除的响应，其 example 归档到 archivedExamples。
     */
    private static ObjectNode mergeResponseConfig(
            ObjectNode existing,
            ObjectNode incoming,
            ObjectNode testResponse,
            ApiImportMergeSummary summary) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("configVersion", ApiConfigJsonSupport.CONFIG_VERSION);

        ArrayNode existingResponses = ApiConfigJsonSupport.arrayOrEmpty(existing.get("responses"));
        ArrayNode incomingResponses = ApiConfigJsonSupport.arrayOrEmpty(incoming.get("responses"));
        ObjectNode examplesById = ApiConfigJsonSupport.ensureObject(testResponse, "examplesById");
        ObjectNode archivedExamples = ApiConfigJsonSupport.ensureObject(testResponse, "archivedExamples");

        Map<String, JsonNode> existingByKey = indexResponses(existingResponses);
        Map<String, JsonNode> incomingByKey = indexResponses(incomingResponses);

        // 上传包不再包含的响应：把 example 归档
        for (String key : existingByKey.keySet()) {
            if (!incomingByKey.containsKey(key)) {
                JsonNode old = existingByKey.get(key);
                String id = ApiConfigJsonSupport.textField(old, "id");
                if (id != null && old.has("example") && !old.get("example").isNull()) {
                    archivedExamples.set(id, old.get("example").deepCopy());
                }
            }
        }

        ArrayNode mergedResponses = JsonNodeFactory.instance.arrayNode();
        for (JsonNode incomingEntry : incomingResponses) {
            ObjectNode mergedEntry = incomingEntry.deepCopy();
            String id = ApiConfigJsonSupport.textField(incomingEntry, "id");
            String key = responseKey(incomingEntry);

            JsonNode oldEntry = existingByKey.get(key);
            if (oldEntry != null) {
                captureResponseExample(oldEntry, id, examplesById);
                ApiSchemaSoftMergeSupport.setOrNull(
                        mergedEntry,
                        "schema",
                        ApiSchemaSoftMergeSupport.mergeSchema(
                                oldEntry.get("schema"), incomingEntry.get("schema")));
            }

            // 用户已有 example 时回填到结构层，调试/设计页可直接看到
            if (id != null && examplesById.has(id)) {
                mergedEntry.set("example", examplesById.get(id).deepCopy());
                summary.addUserPreserved("responseExample:" + id);
            }
            mergedResponses.add(mergedEntry);
        }
        out.set("responses", mergedResponses);
        return out;
    }

    /**
     * 把参数上的非空 value 写入 test_value_config.request.paramDefaults。
     * 无 value 或空串时不写。
     */
    private static void captureParamValue(JsonNode param, String name, ObjectNode testRequest) {
        if (param == null || name == null) {
            return;
        }
        JsonNode valueNode = param.get("value");
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        String value = valueNode.isTextual() ? valueNode.asText() : ApiConfigJsonSupport.writeCompact(valueNode);
        if (StrUtil.isBlank(value)) {
            return;
        }
        ApiConfigJsonSupport.ensureObject(testRequest, "paramDefaults").put(name, value);
    }

    /**
     * 把响应项上的 example 写入 examplesById；同 id 已存在时不覆盖（保留用户值）。
     */
    private static void captureResponseExample(JsonNode entry, String id, ObjectNode examplesById) {
        if (id == null || entry == null || !entry.has("example") || entry.get("example").isNull()) {
            return;
        }
        if (!examplesById.has(id)) {
            examplesById.set(id, entry.get("example").deepCopy());
        }
    }

    /**
     * 把已从结构中删除的参数名记入 removedParams（去重）。
     * 仅作数据归档，不用于导入通知。
     */
    private static void appendRemovedParam(ObjectNode testRequest, String name) {
        ArrayNode removed = ApiConfigJsonSupport.arrayOrEmpty(testRequest.get("removedParams"));
        for (JsonNode n : removed) {
            if (n.isTextual() && name.equals(n.asText())) {
                return;
            }
        }
        removed.add(name);
        testRequest.set("removedParams", removed);
    }

    /** 复制参数节点并去掉 value，使结构层只描述定义、不含测试默认值。 */
    private static ObjectNode stripParamValue(JsonNode param) {
        ObjectNode copy = param.deepCopy();
        copy.remove("value");
        return copy;
    }

    /** 从 request 结构中删除 body.json.example。 */
    private static void stripBodyExampleFromStructure(ObjectNode request) {
        JsonNode body = request.get("body");
        if (body == null || !body.isObject()) {
            return;
        }
        JsonNode jsonPart = body.get("json");
        if (jsonPart != null && jsonPart.isObject()) {
            ((ObjectNode) jsonPart).remove("example");
        }
    }

    /**
     * 读取 body.json.example：文本则原样返回，对象/数组则序列化为紧凑 JSON；无 example 返回 null。
     */
    private static String extractBodyExample(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode jsonPart = body.get("json");
        if (jsonPart == null || !jsonPart.isObject()) {
            return null;
        }
        JsonNode example = jsonPart.get("example");
        if (example == null || example.isNull()) {
            return null;
        }
        return example.isTextual() ? example.asText() : ApiConfigJsonSupport.writeCompact(example);
    }

    /** 按参数 name 建索引；同名后者覆盖。 */
    private static Map<String, JsonNode> indexByName(ArrayNode arr) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode node : arr) {
            String name = ApiConfigJsonSupport.textField(node, "name");
            if (name != null) {
                map.put(name, node);
            }
        }
        return map;
    }

    /** 按响应对齐键建索引。 */
    private static Map<String, JsonNode> indexResponses(ArrayNode arr) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode node : arr) {
            map.put(responseKey(node), node);
        }
        return map;
    }

    /**
     * 响应对齐键：有 id 用 id；否则用 httpStatus + name。
     */
    private static String responseKey(JsonNode entry) {
        String id = ApiConfigJsonSupport.textField(entry, "id");
        if (StrUtil.isNotBlank(id)) {
            return "id:" + id;
        }
        int status = entry != null && entry.has("httpStatus") ? entry.get("httpStatus").asInt(200) : 200;
        String name = entry != null && entry.has("name") ? entry.get("name").asText("") : "";
        return "status:" + status + ":name:" + name;
    }

    /** 记账：query/path 新增参数名（declaredHeaders 不记入摘要列表）。 */
    private static void trackParamAdded(ApiImportMergeSummary summary, String field, String name) {
        switch (field) {
            case FIELD_QUERY -> summary.getQueryParamsAdded().add(name);
            case FIELD_PATH -> summary.getPathParamsAdded().add(name);
            default -> { }
        }
    }

    /** 记账：query/path 删除参数名。 */
    private static void trackParamRemoved(ApiImportMergeSummary summary, String field, String name) {
        switch (field) {
            case FIELD_QUERY -> summary.getQueryParamsRemoved().add(name);
            case FIELD_PATH -> summary.getPathParamsRemoved().add(name);
            default -> { }
        }
    }
}
