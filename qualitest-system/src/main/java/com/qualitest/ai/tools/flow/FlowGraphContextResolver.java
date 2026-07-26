package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 解析 AI/MCP 工具执行时使用的画布 {@link GraphJson}。
 * <p>
 * 优先级：上下文或 MCP 信封中的 graphJson → 按 arguments/信封中的 testFlowId 从库加载。
 * 用于 get_graph_summary、get_flow_meta、get_node_detail；加载失败时返回带 hint 的 error JSON。
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

    public ResolvedGraph resolve(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx.getGraphJson() != null) {
            return ResolvedGraph.ok(ctx.getGraphJson());
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
