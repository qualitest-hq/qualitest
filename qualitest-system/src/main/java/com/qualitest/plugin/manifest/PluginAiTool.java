package com.qualitest.plugin.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 插件说明书 ai.tools[] 的一项：贡献给造流助手 / MCP 的 Function Tool。
 * <p>
 * 调用时平台按 capability 转发 {@code PluginInvoker}；不写画布。
 */
@Getter
@Builder
public class PluginAiTool {

    /** Function Calling 工具名（启用集内须唯一） */
    private final String name;
    /** 给人 / 模型看的说明 */
    private final String description;
    /** 本包 capabilities[].name */
    private final String capability;
    /** 工具 parameters（JSON Schema object）；可空 */
    private final Map<String, Object> parameters;

    /** parameters；未声明时为空 Map */
    public Map<String, Object> getParameters() {
        return parameters == null ? Collections.emptyMap() : parameters;
    }

    public boolean hasParameters() {
        return parameters != null && !parameters.isEmpty();
    }
}
