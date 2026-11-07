package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;

import java.util.Map;

/**
 * 过程报告工具的 MCP 调用门面。
 * <p>
 * 强制要求参数显式传入 runId，不使用请求上下文中的运行 ID；校验通过后交给 {@link GetRunReportTool} 组装报告。
 */
public class McpGetRunReportTool implements QualitestTool {

    /** 实际加载 Run 并组装过程报告的实现 */
    private final GetRunReportTool delegate;

    /**
     * 使用已有报告查询实现构造门面。
     *
     * @param delegate 过程报告查询工具
     */
    public McpGetRunReportTool(GetRunReportTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 按 Run 与步骤服务自行组装查询实现（便于单测）。
     *
     * @param testFlowRunService     Run 查询服务
     * @param testFlowRunStepService 步骤查询服务
     */
    public McpGetRunReportTool(ITestFlowRunService testFlowRunService,
                               ITestFlowRunStepService testFlowRunStepService) {
        this(new GetRunReportTool(testFlowRunService, testFlowRunStepService));
    }

    /**
     * 工具名：get_run_report。
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_REPORT.getId();
    }

    /**
     * 缺 runId 时返回错误 JSON；否则委托过程报告实现执行。
     *
     * @param arguments 工具参数（须含 runId）
     * @param ctx       请求上下文
     * @return 报告 JSON 或错误 JSON
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
