package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;

import java.util.Map;

/**
 * MCP 查询失败 Run 现场的门面。
 * <p>
 * 不会自动带失败 Run id，必须在参数里显式传 runId。
 */
public class McpGetRunFailureTool implements QualitestTool {

    /** 实际查库与组装失败现场的实现 */
    private final GetRunFailureTool delegate;

    /**
     * 使用已有查询实现构造
     *
     * @param delegate 失败现场查询工具
     */
    public McpGetRunFailureTool(GetRunFailureTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 单测用：按 Run 服务自行组装查询实现
     */
    public McpGetRunFailureTool(ITestFlowRunService testFlowRunService,
                                ITestFlowRunStepService testFlowRunStepService) {
        this(new GetRunFailureTool(testFlowRunService, testFlowRunStepService));
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_FAILURE.getId();
    }

    /**
     * 缺 runId 时返回明确错误；否则交由查询实现执行。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return 回执 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long runId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("runId") : null);
        if (runId == null) {
            return FlowDesignToolSupport.errorJson(
                    "缺少 runId：请传入 runId");
        }
        return delegate.execute(arguments, ctx);
    }
}
