package com.qualitest.ai.llm.lc4j;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具定义格式转换。
 * <p>
 * 把业务侧维护的 OpenAI 风格 tools（type=function，内含 name / description / parameters）
 * 转成模型请求所需的工具规格对象，供 Agent 循环声明可用工具。
 */
public final class Lc4jToolSpecifications {

    private Lc4jToolSpecifications() {
    }

    /**
     * 批量转换工具列表。
     * 空入参返回空列表；单条非法（缺 name 等）则跳过，不中断整批。
     */
    @SuppressWarnings("unchecked")
    public static List<ToolSpecification> fromOpenAiMaps(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ToolSpecification> result = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            ToolSpecification spec = fromOpenAiMap(tool);
            if (spec != null) {
                result.add(spec);
            }
        }
        return result;
    }

    /**
     * 单条转换。
     * 优先读 function 子对象；若顶层已有 name 则按扁平 function 对象处理。
     * 缺少有效 name 时返回 null。
     */
    @SuppressWarnings("unchecked")
    public static ToolSpecification fromOpenAiMap(Map<String, Object> tool) {
        if (tool == null || tool.isEmpty()) {
            return null;
        }
        Object functionObj = tool.get("function");
        Map<String, Object> function;
        if (functionObj instanceof Map<?, ?> map) {
            function = (Map<String, Object>) map;
        } else if (tool.get("name") != null) {
            function = tool;
        } else {
            return null;
        }
        String name = stringVal(function.get("name"));
        if (name == null || name.isBlank()) {
            return null;
        }
        String description = stringVal(function.get("description"));
        JsonObjectSchema parameters = toObjectSchema(function.get("parameters"));
        ToolSpecification.Builder builder = ToolSpecification.builder().name(name);
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        if (parameters != null) {
            builder.parameters(parameters);
        }
        return builder.build();
    }

    /**
     * 将 parameters JSON 转为对象型 JSON Schema。
     * 入参为空时生成允许额外属性的空 object schema。
     */
    private static JsonObjectSchema toObjectSchema(Object raw) {
        if (raw == null) {
            return JsonObjectSchema.builder().additionalProperties(true).build();
        }
        JSONObject json = raw instanceof JSONObject obj ? obj : JSON.parseObject(JSON.toJSONString(raw));
        if (json == null) {
            return JsonObjectSchema.builder().additionalProperties(true).build();
        }
        return (JsonObjectSchema) toElement(json);
    }

    /**
     * 递归解析 JSON Schema 节点。
     * 支持 object / array / string / integer / number / boolean，以及带 enum 的枚举字段；
     * 缺 type 时：有 properties 视为 object，有 items 视为 array，否则默认 string。
     */
    private static JsonSchemaElement toElement(Object raw) {
        if (raw == null) {
            return JsonStringSchema.builder().build();
        }
        JSONObject json = raw instanceof JSONObject obj ? obj : JSON.parseObject(JSON.toJSONString(raw));
        if (json == null) {
            return JsonStringSchema.builder().build();
        }
        if (json.containsKey("enum") && json.get("enum") instanceof List<?> enums && !enums.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (Object e : enums) {
                if (e != null) {
                    values.add(String.valueOf(e));
                }
            }
            JsonEnumSchema.Builder enumBuilder = JsonEnumSchema.builder().enumValues(values);
            String desc = json.getString("description");
            if (desc != null) {
                enumBuilder.description(desc);
            }
            return enumBuilder.build();
        }
        String type = json.getString("type");
        if (type == null && json.containsKey("properties")) {
            type = "object";
        }
        if (type == null && json.containsKey("items")) {
            type = "array";
        }
        if (type == null) {
            type = "string";
        }
        return switch (type) {
            case "object" -> buildObject(json);
            case "array" -> buildArray(json);
            case "integer" -> {
                JsonIntegerSchema.Builder b = JsonIntegerSchema.builder();
                if (json.getString("description") != null) {
                    b.description(json.getString("description"));
                }
                yield b.build();
            }
            case "number" -> {
                JsonNumberSchema.Builder b = JsonNumberSchema.builder();
                if (json.getString("description") != null) {
                    b.description(json.getString("description"));
                }
                yield b.build();
            }
            case "boolean" -> {
                JsonBooleanSchema.Builder b = JsonBooleanSchema.builder();
                if (json.getString("description") != null) {
                    b.description(json.getString("description"));
                }
                yield b.build();
            }
            default -> {
                JsonStringSchema.Builder b = JsonStringSchema.builder();
                if (json.getString("description") != null) {
                    b.description(json.getString("description"));
                }
                yield b.build();
            }
        };
    }

    /** 构建 object schema：properties、required、additionalProperties。 */
    private static JsonObjectSchema buildObject(JSONObject json) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (json.getString("description") != null) {
            builder.description(json.getString("description"));
        }
        JSONObject properties = json.getJSONObject("properties");
        if (properties != null && !properties.isEmpty()) {
            Map<String, JsonSchemaElement> props = new LinkedHashMap<>();
            for (String key : properties.keySet()) {
                props.put(key, toElement(properties.get(key)));
            }
            builder.addProperties(props);
        }
        JSONArray required = json.getJSONArray("required");
        if (required != null && !required.isEmpty()) {
            List<String> req = new ArrayList<>();
            for (int i = 0; i < required.size(); i++) {
                String name = required.getString(i);
                if (name != null && !name.isBlank()) {
                    req.add(name);
                }
            }
            if (!req.isEmpty()) {
                builder.required(req);
            }
        }
        if (json.containsKey("additionalProperties")) {
            Object ap = json.get("additionalProperties");
            if (ap instanceof Boolean bool) {
                builder.additionalProperties(bool);
            }
        } else if (properties == null || properties.isEmpty()) {
            builder.additionalProperties(true);
        }
        return builder.build();
    }

    /** 构建 array schema；缺 items 时元素类型默认为 string。 */
    private static JsonArraySchema buildArray(JSONObject json) {
        JsonArraySchema.Builder builder = JsonArraySchema.builder();
        if (json.getString("description") != null) {
            builder.description(json.getString("description"));
        }
        Object items = json.get("items");
        if (items != null) {
            builder.items(toElement(items));
        } else {
            builder.items(JsonStringSchema.builder().build());
        }
        return builder.build();
    }

    private static String stringVal(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
