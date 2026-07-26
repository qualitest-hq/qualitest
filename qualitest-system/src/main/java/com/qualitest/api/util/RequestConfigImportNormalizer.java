package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualitest.common.exception.ServiceException;

import java.util.Locale;

/**
 * 导入时规范化 {@code requestConfig} JSON。
 * <p>
 * 补齐标准字段：{@code configVersion}、{@code method}、{@code queryParams}、
 * {@code pathParams}、{@code declaredHeaders}、{@code body}。
 * 空白、非法 JSON、版本错误或含顶层 {@code params} 键时失败。
 * <p>
 * 参数 {@code type} 处理：
 * <ul>
 *   <li>{@code text} → {@code string}（不区分大小写）</li>
 *   <li>{@code file} 只保留在 {@code body.formData}；出现在 query、path、headers、urlencoded 时改为 {@code string}</li>
 * </ul>
 */
public final class RequestConfigImportNormalizer {

    /** 当前请求配置协议版本号 */
    public static final int CONFIG_VERSION = ApiConfigJsonSupport.CONFIG_VERSION;

    private RequestConfigImportNormalizer() {
    }

    /**
     * 规范化请求配置字符串并序列化为紧凑 JSON。
     *
     * @param raw 客户端上传的 requestConfig JSON 文本
     * @return 字段齐全、类型已矫正后的紧凑 JSON
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new ServiceException("requestConfig 不能为空");
        }
        try {
            JsonNode root = ApiConfigJsonSupport.readTree(raw);
            if (!root.isObject()) {
                throw new ServiceException("requestConfig 必须是 JSON 对象");
            }
            ObjectNode obj = (ObjectNode) root;
            ApiConfigJsonSupport.requireConfigVersion(obj, "requestConfig");
            if (obj.has("params")) {
                throw new ServiceException("requestConfig 不得包含 params 键，请使用 queryParams");
            }
            return ApiConfigJsonSupport.writeCompact(normalizeObject(obj));
        } catch (ServiceException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ServiceException("requestConfig 不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 校验对象上的 {@code configVersion}（供响应配置规范化等复用）。
     *
     * @param obj   配置根对象
     * @param label 错误信息前缀
     */
    static void requireConfigVersion(ObjectNode obj, String label) {
        ApiConfigJsonSupport.requireConfigVersion(obj, label);
    }

    /**
     * 将源对象整理为标准 requestConfig：
     * 写入版本号与大写 HTTP 方法；拷贝并矫正三类参数数组的 type；
     * 规范化 body 内表单字段；body 缺失时写入 {@code mode=none}。
     *
     * @param src 已通过版本校验的源对象
     * @return 新的标准对象（不修改 src 上未 deepCopy 的部分）
     */
    private static ObjectNode normalizeObject(ObjectNode src) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("configVersion", CONFIG_VERSION);

        // HTTP 方法：缺省 GET，有值则转大写
        String method = "GET";
        if (src.hasNonNull("method") && src.get("method").isTextual()) {
            String m = src.get("method").asText().trim();
            if (StrUtil.isNotBlank(m)) {
                method = m.toUpperCase();
            }
        }
        out.put("method", method);

        ArrayNode pathParams = ApiConfigJsonSupport.copyArrayOrEmpty(src.get("pathParams"));
        ArrayNode queryParams = ApiConfigJsonSupport.copyArrayOrEmpty(src.get("queryParams"));
        ArrayNode declaredHeaders = ApiConfigJsonSupport.copyArrayOrEmpty(src.get("declaredHeaders"));
        // 路径 / 查询 / 请求头：不允许 file，text 改为 string
        normalizeParamTypes(pathParams, false);
        normalizeParamTypes(queryParams, false);
        normalizeParamTypes(declaredHeaders, false);
        out.set("pathParams", pathParams);
        out.set("queryParams", queryParams);
        out.set("declaredHeaders", declaredHeaders);

        JsonNode body = src.get("body");
        if (body != null && body.isObject()) {
            ObjectNode bodyOut = body.deepCopy();
            normalizeBodyTypes(bodyOut);
            out.set("body", bodyOut);
        } else {
            // 无 body 时补空请求体结构
            ObjectNode emptyBody = JsonNodeFactory.instance.objectNode();
            emptyBody.put("mode", "none");
            ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.putNull("schema");
            json.putNull("example");
            emptyBody.set("json", json);
            out.set("body", emptyBody);
        }
        return out;
    }

    /**
     * 规范化 body 内表单项类型。
     * {@code formData} 允许 {@code file}；{@code urlencoded} 不允许 {@code file}。
     * 两处都会把 {@code text} 改为 {@code string}。
     *
     * @param body 可写的 body 对象（深拷贝后的节点）
     */
    private static void normalizeBodyTypes(ObjectNode body) {
        if (body == null) {
            return;
        }
        JsonNode formData = body.get("formData");
        if (formData instanceof ArrayNode formDataArr) {
            normalizeParamTypes(formDataArr, true);
        }
        JsonNode urlencoded = body.get("urlencoded");
        if (urlencoded instanceof ArrayNode urlencodedArr) {
            normalizeParamTypes(urlencodedArr, false);
        }
    }

    /**
     * 原地矫正参数数组每一项的 {@code type} 字段。
     * 忽略非对象元素、无 type 或 type 非文本的项。
     *
     * @param arr       参数数组
     * @param allowFile {@code true}：保留 {@code file}（用于 formData）；
     *                  {@code false}：将 {@code file} 改为 {@code string}
     */
    private static void normalizeParamTypes(ArrayNode arr, boolean allowFile) {
        if (arr == null) {
            return;
        }
        for (JsonNode item : arr) {
            if (!(item instanceof ObjectNode obj)) {
                continue;
            }
            JsonNode typeNode = obj.get("type");
            if (typeNode == null || typeNode.isNull() || !typeNode.isTextual()) {
                continue;
            }
            String type = typeNode.asText().trim().toLowerCase(Locale.ROOT);
            if (type.isEmpty()) {
                continue;
            }
            if ("text".equals(type)) {
                obj.put("type", "string");
            } else if ("file".equals(type) && !allowFile) {
                obj.put("type", "string");
            }
        }
    }
}
