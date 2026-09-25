package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.api.util.ExpectedResponseKindSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.api.util.RequestConfigImportNormalizer;
import com.qualitest.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 MCP import_apis 入参里的结构化 items 转成项目接口导入包。
 * <p>
 * path 会做规范化（补前导斜杠、去尾斜杠等）。
 * headerParams 写入请求配置的 declaredHeaders。
 * sourceSystem 固定为 mcp-agent，标明数据来自 MCP Agent。
 */
public final class McpApiImportItemMapper {

    /** 写入接口记录的来源系统标识 */
    public static final String SOURCE_SYSTEM = "mcp-agent";

    private McpApiImportItemMapper() {
    }

    /**
     * 整批映射结果。
     *
     * @param params    可直接交给导入服务的参数包
     * @param metaFlags 与 apiList 一一对应：是否显式传了分组/注释，以及规范化后的方法与 path
     */
    public record MappedBatch(
            ApiImportParams params,
            List<MetaFlags> metaFlags
    ) {
    }

    /**
     * 单条 item 的元数据标记，供更新时决定是否保留库内分组/注释。
     *
     * @param groupProvided       是否传了非空 apiGroup
     * @param descriptionProvided 是否传了非空 description
     * @param method              大写 HTTP 方法
     * @param normalizedPath      规范化后的接口 path
     * @param name                接口显示名
     */
    public record MetaFlags(
            boolean groupProvided,
            boolean descriptionProvided,
            String method,
            String normalizedPath,
            String name
    ) {
    }

    /**
     * 将 arguments.items 转为导入包。
     *
     * @param itemsArg items 数组（List 或 JSONArray）
     * @throws ServiceException items 为空、非数组或单条缺必填字段时抛出
     */
    public static MappedBatch toImportParams(Object itemsArg) {
        List<?> rawItems = asList(itemsArg);
        if (rawItems.isEmpty()) {
            throw new ServiceException("items 不能为空");
        }
        List<ApiImportParams.ApiImportItem> apiList = new ArrayList<>();
        List<MetaFlags> flags = new ArrayList<>();
        for (Object raw : rawItems) {
            JSONObject item = toObject(raw);
            if (item == null) {
                throw new ServiceException("items 元素须为对象");
            }
            MappedItem mapped = mapOne(item);
            apiList.add(mapped.item());
            flags.add(mapped.flags());
        }
        ApiImportParams params = ApiImportParams.builder()
                .configVersion(ApiConfigJsonSupport.CONFIG_VERSION)
                .apiList(apiList)
                .build();
        return new MappedBatch(params, flags);
    }

    /** 单条映射的中间结果 */
    private record MappedItem(ApiImportParams.ApiImportItem item, MetaFlags flags) {
    }

    /**
     * 映射单条 item：校验 method/path/name，拼 requestConfig / responseConfig / auth。
     */
    private static MappedItem mapOne(JSONObject item) {
        String method = str(item.get("method"));
        String path = str(item.get("path"));
        String name = str(item.get("name"));
        if (method == null || method.isBlank()) {
            throw new ServiceException("items[].method 不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new ServiceException("items[].path 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new ServiceException("items[].name 不能为空");
        }
        method = method.trim().toUpperCase(Locale.ROOT);
        String normalizedPath = ProjectAuthConfigSupport.normalizeApiPath(path);

        boolean groupProvided = hasNonBlank(item, "apiGroup");
        boolean descriptionProvided = hasNonBlank(item, "description");
        String apiGroup = groupProvided ? str(item.get("apiGroup")).trim() : null;
        String description = descriptionProvided ? str(item.get("description")).trim() : null;

        String requestConfig = buildRequestConfig(
                method,
                item.get("pathParams"),
                item.get("queryParams"),
                item.get("headerParams"),
                item.get("bodyParams"),
                item.get("bodyExample"));
        String responseConfig = buildResponseConfig(
                item.get("responseSchemaSummary"),
                item.get("expectedResponseKind"));
        ApiAuthConfig auth = mapAuth(item.get("authSuggestion"));

        ApiImportParams.ApiImportItem apiItem = ApiImportParams.ApiImportItem.builder()
                .apiName(name.trim())
                .apiPath(normalizedPath)
                .apiGroup(apiGroup)
                .apiDescription(description)
                .protocolType("http")
                .apiStatus("normal")
                .requestConfig(requestConfig)
                .responseConfig(responseConfig)
                .sourceSystem(SOURCE_SYSTEM)
                .auth(auth)
                .build();
        MetaFlags flags = new MetaFlags(groupProvided, descriptionProvided, method, normalizedPath, name.trim());
        return new MappedItem(apiItem, flags);
    }

    /**
     * 组装请求配置 JSON：方法、路径/查询/头参数、body，再走导入侧规范化。
     * headerParams 写入 declaredHeaders。
     */
    private static String buildRequestConfig(
            String method,
            Object pathParams,
            Object queryParams,
            Object headerParams,
            Object bodyParams,
            Object bodyExample) {
        JSONObject rc = new JSONObject();
        rc.put("configVersion", ApiConfigJsonSupport.CONFIG_VERSION);
        rc.put("method", method);
        rc.put("pathParams", toJsonArray(pathParams));
        rc.put("queryParams", toJsonArray(queryParams));
        rc.put("declaredHeaders", toJsonArray(headerParams));
        rc.put("body", buildBody(bodyParams, bodyExample));
        return RequestConfigImportNormalizer.normalize(rc.toJSONString());
    }

    /**
     * 组装 body 节点。
     * 无参数且无示例 → mode=none；
     * 有参数或示例 → mode=json，参数名列表转成极简 object schema，示例写入 example。
     */
    private static JSONObject buildBody(Object bodyParams, Object bodyExample) {
        JSONObject body = new JSONObject();
        JSONArray params = toJsonArray(bodyParams);
        Object exampleNode = coerceJson(bodyExample);
        boolean hasParams = params != null && !params.isEmpty();
        boolean hasExample = exampleNode != null;
        if (!hasParams && !hasExample) {
            body.put("mode", "none");
            return body;
        }
        body.put("mode", "json");
        JSONObject json = new JSONObject();
        if (hasParams) {
            // 用参数名列表拼极简 object schema，便于入库后展示
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            JSONObject properties = new JSONObject();
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                JSONObject row = toObject(p);
                String pname = row != null ? str(row.get("name")) : null;
                if (pname == null || pname.isBlank()) {
                    continue;
                }
                JSONObject prop = new JSONObject();
                String type = row.getString("type");
                prop.put("type", type != null && !type.isBlank() ? type : "string");
                if (row.getString("description") != null) {
                    prop.put("description", row.getString("description"));
                }
                properties.put(pname, prop);
            }
            schema.put("properties", properties);
            json.put("schema", schema);
        }
        if (hasExample) {
            json.put("example", exampleNode);
        }
        body.put("json", json);
        return body;
    }

    /**
     * 组装响应配置：固定一条 HTTP 200 成功响应；
     * 有 responseSchemaSummary 则写入 schema，否则空对象。
     * expectedResponseKind 为接口期望响应形态，缺省 json。
     */
    private static String buildResponseConfig(Object responseSchemaSummary, Object expectedResponseKind) {
        Object schema = coerceJson(responseSchemaSummary);
        JSONObject root = new JSONObject();
        root.put("configVersion", ApiConfigJsonSupport.CONFIG_VERSION);
        root.put("expectedResponseKind",
                ExpectedResponseKindSupport.normalize(str(expectedResponseKind)));
        JSONArray responses = new JSONArray();
        JSONObject resp = new JSONObject();
        resp.put("id", "default");
        resp.put("name", "成功");
        resp.put("httpStatus", 200);
        if (schema != null) {
            resp.put("schema", schema);
        } else {
            resp.put("schema", new JSONObject());
        }
        responses.add(resp);
        root.put("responses", responses);
        return root.toJSONString();
    }

    /**
     * 映射鉴权建议：mode 必填；可选 authProfileId、header(name + valueTemplate)。
     * 无有效 mode 时返回 null（不写鉴权）。
     */
    private static ApiAuthConfig mapAuth(Object raw) {
        if (raw == null) {
            return null;
        }
        JSONObject o = toObject(raw);
        if (o == null || o.isEmpty()) {
            return null;
        }
        String mode = str(o.get("mode"));
        if (mode == null || mode.isBlank()) {
            return null;
        }
        ApiAuthConfig.ApiAuthConfigBuilder b = ApiAuthConfig.builder().mode(mode.trim());
        String profileId = str(o.get("authProfileId"));
        if (profileId != null && !profileId.isBlank()) {
            b.authProfileId(profileId.trim());
        }
        JSONObject header = toObject(o.get("header"));
        if (header != null) {
            String hName = str(header.get("name"));
            String hVal = str(header.get("valueTemplate"));
            if (hName != null && !hName.isBlank()) {
                b.header(ApiAuthConfig.Header.builder()
                        .name(hName.trim())
                        .valueTemplate(hVal != null ? hVal : "")
                        .build());
            }
        }
        return b.build();
    }

    /** 判断对象上某字符串字段是否存在且非空白 */
    private static boolean hasNonBlank(JSONObject item, String key) {
        if (!item.containsKey(key) || item.get(key) == null) {
            return false;
        }
        String s = str(item.get(key));
        return s != null && !s.isBlank();
    }

    /** 将 items 入参规范为 List；不支持的类型抛业务异常 */
    private static List<?> asList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list;
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        throw new ServiceException("items 须为数组");
    }

    /**
     * 转为 JSONObject：已是对象 / Map / JSON 字符串均可；解析失败或类型不对返回 null。
     */
    private static JSONObject toObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONObject jo) {
            return jo;
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return JSON.parseObject(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 转为 JSONArray：null → 空数组；已是数组或 List 则包装；其它类型 → 空数组。
     */
    private static JSONArray toJsonArray(Object raw) {
        if (raw == null) {
            return new JSONArray();
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        if (raw instanceof List<?> list) {
            JSONArray arr = new JSONArray();
            arr.addAll(list);
            return arr;
        }
        return new JSONArray();
    }

    /**
     * 宽松解析 JSON 值：字符串尝试 parse；对象/数组/Map/List 原样返回；空白串视为 null。
     * 用于 bodyExample、responseSchemaSummary 等既可能是对象也可能是 JSON 字符串的字段。
     */
    private static Object coerceJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return null;
            }
            try {
                return JSON.parse(s);
            } catch (Exception e) {
                return s;
            }
        }
        if (raw instanceof JSONObject || raw instanceof JSONArray || raw instanceof Map || raw instanceof List) {
            return raw;
        }
        return raw;
    }

    /** null 安全的字符串化 */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
