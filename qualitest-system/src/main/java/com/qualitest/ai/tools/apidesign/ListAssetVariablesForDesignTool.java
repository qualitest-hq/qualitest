package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.flow.ListAssetVariablesTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 列举项目素材库 key / 备注 / 子字段名（不含明文值）；委托画布侧实现。
 */
@RequiredArgsConstructor
public class ListAssetVariablesForDesignTool implements ApiDesignTool {

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
