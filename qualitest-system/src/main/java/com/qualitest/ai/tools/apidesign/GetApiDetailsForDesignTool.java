package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.flow.GetApiDetailsTool;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口设计助手：按 id 数组拉取接口造流摘要。
 * <p>
 * 未传 testProjectApiIds 时，自动填入当前上下文中的接口 id。
 */
@RequiredArgsConstructor
public class GetApiDetailsForDesignTool implements ApiDesignTool {

    private final GetApiDetailsTool delegate;

    @Override
    public String getName() {
        return ApiDesignToolNames.GET_API_DETAILS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        Map<String, Object> args = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
        Object rawIds = args.get("testProjectApiIds");
        boolean empty = rawIds == null
                || (rawIds instanceof List<?> list && list.isEmpty())
                || (rawIds instanceof Object[] arr && arr.length == 0);
        if (empty && ctx.getTestProjectApiId() != null) {
            List<String> one = new ArrayList<>();
            one.add(String.valueOf(ctx.getTestProjectApiId()));
            args.put("testProjectApiIds", one);
        }
        FlowDesignToolContext flowCtx = FlowDesignToolContext.builder()
                .testProjectId(ctx.getTestProjectId())
                .maxToolResultBytes(ctx.getMaxToolResultBytes())
                .build();
        return delegate.execute(args, flowCtx);
    }
}
