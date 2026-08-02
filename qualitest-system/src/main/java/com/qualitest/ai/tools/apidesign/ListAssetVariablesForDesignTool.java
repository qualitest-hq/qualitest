package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.flow.ListAssetVariablesTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * API 设计 AI 工具：列举当前项目素材库的 key、备注、子字段名（不含明文值）。
 */
@RequiredArgsConstructor
public class ListAssetVariablesForDesignTool implements ApiDesignTool {

    /** 实际执行列举逻辑的实现 */
    private final ListAssetVariablesTool delegate;

    @Override
    public String getName() {
        return ApiDesignToolNames.LIST_ASSET_VARIABLES.getId();
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
