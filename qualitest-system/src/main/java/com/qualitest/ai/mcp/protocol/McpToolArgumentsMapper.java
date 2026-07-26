package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSON;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.flow.model.GraphJson;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP {@code tools/call} 参数映射器。
 * <p>
 * 客户端在 {@code params.arguments} 中传入一个 JSON 对象。其中部分键表示跨多次调用的运行时上下文
 * （项目、测试流、画布等），其余键作为本次工具的业务入参。
 * 本类将二者拆入 {@link McpToolInvokeParams} 的信封字段与 {@link McpToolInvokeParams#getArguments()}。
 */
@Component
public class McpToolArgumentsMapper {

    /**
     * 将 tools/call 的 arguments 对象映射为编排服务所需的请求参数。
     *
     * @param rawArguments JSON 对象，可为 null 或空（表示无业务参数、无信封字段）
     * @return 填充后的 {@link McpToolInvokeParams}；业务参数为空时不设置 arguments 字段
     */
    public McpToolInvokeParams fromToolArguments(Map<String, Object> rawArguments) {
        McpToolInvokeParams params = new McpToolInvokeParams();
        if (rawArguments == null || rawArguments.isEmpty()) {
            return params;
        }
        Map<String, Object> businessArgs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawArguments.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "testProjectId" -> params.setTestProjectId(parseLong(value));
                case "testFlowId" -> params.setTestFlowId(parseLong(value));
                case "graphJson" -> params.setGraphJson(parseGraphJson(value));
                case "scopeApiIds" -> params.setScopeApiIds(parseStringList(value));
                case "contextNodeIds" -> params.setContextNodeIds(parseStringList(value));
                case "contextRunId" -> params.setContextRunId(parseLong(value));
                default -> businessArgs.put(key, value);
            }
        }
        if (!businessArgs.isEmpty()) {
            params.setArguments(businessArgs);
        }
        return params;
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private static GraphJson parseGraphJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof GraphJson graphJson) {
            return graphJson;
        }
        return JSON.parseObject(JSON.toJSONString(value), GraphJson.class);
    }
}
