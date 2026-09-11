package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 解析只读查图工具使用的画布。
 * <p>
 * 优先级：上下文工作图（本轮已接受 submit 合并结果）或基准 graphJson →
 * 再按 arguments/上下文中的 testFlowId 从库加载。
 * 用于 graph_summary、flow_meta、node/edge/scenario 详情、api health 等。
 */
@RequiredArgsConstructor
public class FlowGraphContextResolver {

    private final ITestFlowService testFlowService;

    public record ResolvedGraph(GraphJson graph, String errorJson) {
        public static ResolvedGraph ok(GraphJson graph) {
            return new ResolvedGraph(graph, null);
        }

        public static ResolvedGraph err(String errorJson) {
            return new ResolvedGraph(null, errorJson);
        }

        public boolean isOk() {
            return errorJson == null;
        }
    }

    /**
     * 解析当前应读的图；失败时 errorJson 含 hint，供模型改参数重试。
     */
    public ResolvedGraph resolve(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx.getGraphJson() != null || (ctx.resolveGraphJson() != null)) {
            GraphJson g = ctx.resolveGraphJson();
            if (g != null) {
                return ResolvedGraph.ok(g);
            }
        }
        Long flowId = FlowDesignToolSupport.longArg(arguments.get("testFlowId"));
        if (flowId == null) {
            flowId = ctx.getTestFlowId();
        }
        if (flowId == null) {
            return ResolvedGraph.err(FlowDesignToolSupport.missingGraphJsonError());
        }
        TestFlowAccessSupport.FlowAccess access = TestFlowAccessSupport.resolveFlowInProject(
                flowId, ctx.getTestProjectId(), testFlowService);
        if (!access.isOk()) {
            return ResolvedGraph.err(FlowDesignToolSupport.errorJson(access.errorMessage()));
        }
        return ResolvedGraph.ok(TestFlowAccessSupport.parseGraphJson(access.flow().getGraphJson()));
    }
}
