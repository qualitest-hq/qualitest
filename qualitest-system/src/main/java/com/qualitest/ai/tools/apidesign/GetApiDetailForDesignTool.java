package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.flow.GetApiDetailTool;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 按接口 id 拉取详情摘要。
 * <p>
 * 未传 testProjectApiId 时自动填入当前上下文接口 id。
 */
@RequiredArgsConstructor
public class GetApiDetailForDesignTool implements ApiDesignTool {

    private final GetApiDetailTool delegate;

    @Override
    public String getName() {
        return ApiDesignToolNames.GET_API_DETAIL.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        Map<String, Object> args = arguments != null ? arguments : Map.of();
        if (!args.containsKey("testProjectApiId") && ctx.getTestProjectApiId() != null) {
            args = new HashMap<>(args);
            args.put("testProjectApiId", String.valueOf(ctx.getTestProjectApiId()));
        }
        FlowDesignToolContext flowCtx = FlowDesignToolContext.builder()
                .testProjectId(ctx.getTestProjectId())
                .maxToolResultBytes(ctx.getMaxToolResultBytes())
                .build();
        return delegate.execute(args, flowCtx);
    }
}
