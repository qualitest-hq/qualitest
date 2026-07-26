package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.flow.ListProjectEnvsTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 列举当前测试项目的环境及环境变量键名（不含变量值）。
 */
@RequiredArgsConstructor
public class ListProjectEnvsForDesignTool implements ApiDesignTool {

    private final ListProjectEnvsTool delegate;

    @Override
    public String getName() {
        return ApiDesignToolNames.LIST_PROJECT_ENVS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        FlowDesignToolContext flowCtx = FlowDesignToolContext.builder()
                .testProjectId(ctx.getTestProjectId())
                .maxToolResultBytes(ctx.getMaxToolResultBytes())
                .build();
        return delegate.execute(arguments != null ? arguments : Map.of(), flowCtx);
    }
}
