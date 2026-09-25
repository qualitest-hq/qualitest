package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualitest.common.exception.ServiceException;

import java.util.Locale;
import java.util.UUID;

/**
 * 导入时规范化 responseConfig JSON。
 * <p>
 * 产出结构：configVersion=1、expectedResponseKind（缺省 json）、responses 数组
 *（每项含 id、name、httpStatus、contentType、schema、example）。
 * 含非法顶层 content、缺 responses 或非法 JSON 时抛错；空白输入生成一条默认成功响应。
 */
public final class ResponseConfigImportNormalizer {

    public static final int CONFIG_VERSION = ApiConfigJsonSupport.CONFIG_VERSION;

    private ResponseConfigImportNormalizer() {
    }

    /**
     * 规范化响应配置字符串。
     *
     * @param raw 客户端上传的 responseConfig JSON；空白则生成默认 bundle
     * @return 字段齐全、版本为 1 的紧凑 JSON
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return ApiConfigJsonSupport.writeCompact(emptyBundle());
        }
        try {
            JsonNode root = ApiConfigJsonSupport.readTree(raw);
            if (!root.isObject()) {
                throw new ServiceException("responseConfig 必须是 JSON 对象");
            }
            ObjectNode obj = (ObjectNode) root;
            if (obj.has("content")) {
                throw new ServiceException("responseConfig 不得包含 content 字段，请使用 responses 数组");
            }
            ApiConfigJsonSupport.requireConfigVersion(obj, "responseConfig");
            JsonNode responses = obj.get("responses");
            if (responses == null || !responses.isArray()) {
                throw new ServiceException("responseConfig 必须包含 responses 数组");
            }
            return ApiConfigJsonSupport.writeCompact(normalizeResponsesBundle(obj));
        } catch (ServiceException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ServiceException("responseConfig 不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 组装规范化后的响应配置对象。
     * 写入版本号与期望响应形态；responses 为空时补一条默认成功项。
     */
    private static ObjectNode normalizeResponsesBundle(ObjectNode root) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("configVersion", CONFIG_VERSION);
        ExpectedResponseKindSupport.putOnObjectNode(out, resolveExpectedKind(root));
        ArrayNode responses = JsonNodeFactory.instance.arrayNode();
        JsonNode arr = root.get("responses");
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                responses.add(normalizeResponseEntry(item));
            }
        }
        if (responses.isEmpty()) {
            responses.add(defaultResponseEntry());
        }
        out.set("responses", responses);
        return out;
    }

    /**
     * 解析期望响应形态：已有 expectedResponseKind 则规范化后采用；
     * 否则按 responses 条目的 contentType 推断（全 json → json，含 xml/binary → any）。
     */
    private static String resolveExpectedKind(ObjectNode root) {
        if (root != null && root.hasNonNull(ExpectedResponseKindSupport.FIELD)
                && root.get(ExpectedResponseKindSupport.FIELD).isTextual()) {
            return ExpectedResponseKindSupport.fromObjectNode(root);
        }
        return inferExpectedKindFromResponses(root);
    }

    /**
     * 按响应条目 contentType 推断期望形态。
     * 全部为 json（或缺省）→ json；出现 xml 或 binary → any。
     */
    private static String inferExpectedKindFromResponses(ObjectNode root) {
        if (root == null) {
            return ExpectedResponseKindSupport.KIND_JSON;
        }
        JsonNode arr = root.get("responses");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return ExpectedResponseKindSupport.KIND_JSON;
        }
        boolean sawNonJson = false;
        for (JsonNode item : arr) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String ct = "json";
            if (item.hasNonNull("contentType") && item.get("contentType").isTextual()) {
                ct = normalizeContentType(item.get("contentType").asText());
            }
            if (!"json".equals(ct)) {
                sawNonJson = true;
                break;
            }
        }
        return sawNonJson
                ? ExpectedResponseKindSupport.KIND_ANY
                : ExpectedResponseKindSupport.KIND_JSON;
    }

    /** 规范化单条响应：补 id、默认名称/状态码/类型，schema 缺省为 null */
    private static ObjectNode normalizeResponseEntry(JsonNode r) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        String id = null;
        if (r != null && r.isObject() && r.hasNonNull("id") && r.get("id").isTextual()) {
            id = r.get("id").asText().trim();
        }
        out.put("id", StrUtil.isNotBlank(id) ? id : newResponseId());

        String name = "成功";
        if (r != null && r.isObject() && r.hasNonNull("name") && r.get("name").isTextual()) {
            String n = r.get("name").asText().trim();
            if (StrUtil.isNotBlank(n)) {
                name = n;
            }
        }
        out.put("name", name);

        int httpStatus = 200;
        if (r != null && r.isObject() && r.has("httpStatus") && r.get("httpStatus").isNumber()) {
            int h = r.get("httpStatus").asInt();
            if (h >= 100 && h <= 599) {
                httpStatus = h;
            }
        }
        out.put("httpStatus", httpStatus);

        String contentType = "json";
        if (r != null && r.isObject() && r.hasNonNull("contentType") && r.get("contentType").isTextual()) {
            contentType = normalizeContentType(r.get("contentType").asText());
        }
        out.put("contentType", contentType);

        if (r != null && r.isObject() && r.has("schema")) {
            out.set("schema", r.get("schema"));
        } else {
            out.set("schema", NullNode.getInstance());
        }

        if (r != null && r.isObject() && r.has("example")) {
            out.set("example", r.get("example"));
        }

        return out;
    }

    /** 将 contentType 规范为 json、xml、binary 之一，无法识别时默认 json */
    private static String normalizeContentType(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "json";
        }
        String x = raw.trim().toLowerCase(Locale.ROOT);
        if ("xml".equals(x) || "binary".equals(x) || "json".equals(x)) {
            return x;
        }
        return "json";
    }

    /** 空白输入时的默认响应配置：期望 json + 一条 200/json 成功项 */
    private static ObjectNode emptyBundle() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("configVersion", CONFIG_VERSION);
        ExpectedResponseKindSupport.putOnObjectNode(root, ExpectedResponseKindSupport.KIND_JSON);
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(defaultResponseEntry());
        root.set("responses", arr);
        return root;
    }

    /** 单条默认响应：200 / json / 名称为「成功」 */
    private static ObjectNode defaultResponseEntry() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("id", newResponseId());
        n.put("name", "成功");
        n.put("httpStatus", 200);
        n.put("contentType", "json");
        n.set("schema", NullNode.getInstance());
        return n;
    }

    /** 生成导入时使用的响应项 id（服务端侧随机） */
    private static String newResponseId() {
        return "resp-" + UUID.randomUUID().toString().replace("-", "");
    }
}
