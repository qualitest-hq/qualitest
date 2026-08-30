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
import com.qualitest.api.util.ApiTestValuePeelSupport;
import com.qualitest.project.domain.TestProjectApi;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 已有接口再次导入时：合并请求/响应结构，并把上传包里的调试测值写入测值配置。
 * <p>
 * 参数列表与 schema 形状以上传包为准；同名字段类型未变时保留本地约束（pattern、min/max 等）；
 * 用户已有测值不覆盖。全新接口不走这里。
 * 整条跳过由接口行 sync_protected 在导入服务侧处理，本类不感知。
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
     * 合并一次：规范化上传包 → 合并 request/response → 测值进 test_value_config。
     *
     * @param existing            库中当前接口行
     * @param incomingRequestRaw  上传包请求 JSON
     * @param incomingResponseRaw 上传包响应 JSON
     * @return 合并后的三份配置 JSON 与摘要
     */
    public ApiImportMergeResult merge(TestProjectApi existing, String incomingRequestRaw, String incomingResponseRaw) {
        ApiImportMergeSummary summary = ApiImportMergeSummary.builder().build();

        // 上传包先规范化，缺 example 时按 schema 补一份再参与合并
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
     * 合并请求结构：上传包定参数与 body 形状，本地约束 soft merge；
     * 最后把结构里残留的 value / body.example 拆进测值（已有 bodyExample 不覆盖）。
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
        // 拆测值前先保住本地已有 bodyExample，避免被上传包示例盖掉
        JsonNode preservedBodyExample =
                testRequest.has("bodyExample") ? testRequest.get("bodyExample").deepCopy() : null;
        ApiTestValuePeelSupport.peelRequest(out, testRequest);
        if (preservedBodyExample != null) {
            testRequest.set("bodyExample", preservedBodyExample);
        }
        if (testRequest.has("bodyExample")) {
            summary.addUserPreserved("bodyExample");
        }
        return out;
    }

    /**
     * 按参数 name 合并一组参数数组。
     * <ul>
     *   <li>上传有、本地无：记新增，结构用上传项（去掉 value）</li>
     *   <li>上传无、本地有：记删除，参数名写入 removedParams</li>
     *   <li>两边都有且类型未变：结构用上传项 + 本地用户约束；类型变了则只用上传项</li>
     * </ul>
     * 测值只认测值配置列，不从本地结构 value 回填。
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
            // 结构已删的参数名记入 removedParams；测值仍留在测值配置里
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
     * 合并响应结构：按 id（或 httpStatus+name）对齐条目与 schema。
     * 上传包带的 example 写入 examplesById（已有不覆盖）；结构里不留 example。
     * 上传包删掉的条目，其测值挪到 archivedExamples。
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

        // 上传包已去掉的响应：把测值配置里对应示例挪到归档
        for (String key : existingByKey.keySet()) {
            if (!incomingByKey.containsKey(key)) {
                JsonNode old = existingByKey.get(key);
                String id = ApiConfigJsonSupport.textField(old, "id");
                if (id != null && examplesById.has(id)) {
                    archivedExamples.set(id, examplesById.get(id).deepCopy());
                    examplesById.remove(id);
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
                ApiSchemaSoftMergeSupport.setOrNull(
                        mergedEntry,
                        "schema",
                        ApiSchemaSoftMergeSupport.mergeSchema(
                                oldEntry.get("schema"), incomingEntry.get("schema")));
            }
            // 上传包结构里的 example → 测值（已有同 id 不覆盖）
            captureResponseExample(incomingEntry, id, examplesById);
            // 结构只留契约，去掉 example
            mergedEntry.remove("example");
            if (id != null && examplesById.has(id)) {
                summary.addUserPreserved("responseExample:" + id);
            }
            mergedResponses.add(mergedEntry);
        }
        out.set("responses", mergedResponses);
        return out;
    }

    /**
     * 把响应条目上的 example 写入 examplesById；同 id 已有则跳过。
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
