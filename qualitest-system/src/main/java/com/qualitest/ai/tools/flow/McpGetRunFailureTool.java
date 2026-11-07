package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;

import java.util.Map;

/**
 * 失败现场工具的 MCP 调用门面。
 * <p>
 * 强制要求参数显式传入 runId，不使用请求上下文中的运行 ID；校验通过后交给 {@link GetRunFailureTool} 组装失败步骤。
 */
public class McpGetRunFailureTool implements QualitestTool {

    /** 实际查库与组装失败现场的实现 */
    private final GetRunFailureTool delegate;

    /**
     * 使用已有失败现场查询实现构造门面。
     *
     * @param delegate 失败现场查询工具
     */
    public McpGetRunFailureTool(GetRunFailureTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 按 Run 与步骤服务自行组装查询实现（便于单测）。
     *
     * @param testFlowRunService     Run 查询服务
     * @param testFlowRunStepService 步骤查询服务
     */
    public McpGetRunFailureTool(ITestFlowRunService testFlowRunService,
                                ITestFlowRunStepService testFlowRunStepService) {
        this(new GetRunFailureTool(testFlowRunService, testFlowRunStepService));
    }

    /**
     * 工具名：get_run_failure。
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_FAILURE.getId();
    }

    /**
     * 缺 runId 时返回错误 JSON；否则委托失败现场实现执行。
     *
     * @param arguments 工具参数（须含 runId）
     * @param ctx       请求上下文
     * @return 失败现场 JSON 或错误 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String missing = RunToolSupport.requireExplicitRunIdOrError(arguments);
        if (missing != null) {
            return missing;
        }
        return delegate.execute(arguments, ctx);
    }
}
