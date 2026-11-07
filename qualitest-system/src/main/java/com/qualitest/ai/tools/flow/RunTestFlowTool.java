package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.service.IProjectRunHubService;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;

import java.util.Map;

/**
 * 造流助手工具：触发当前测试流正式 Run，返回 runId、status、失败摘要。
 * <p>
 * 仅全自动上下文可调用；半自动直接拒绝。
 * 测试流 id 来自画布上下文或参数；未传操作者时由触发服务使用当前登录用户。
 * 场景/环境参数可省略，省略时用设计请求带来的默认值。
 * 若项目按环境串行且触发进入排队，工具内会等待排队开跑后再等终态。
 */
public class RunTestFlowTool implements QualitestTool {

    /** 跑流核心 */
    private final TestFlowRunTriggerCore runCore;

    /**
     * 使用已组装的跑流核心构造
     *
     * @param runCore 跑流核心
     */
    public RunTestFlowTool(TestFlowRunTriggerCore runCore) {
        this.runCore = runCore;
    }

    /**
     * 单测用：按依赖自行组装跑流核心
     */
    public RunTestFlowTool(ITestFlowExecutionService testFlowExecutionService,
                           IProjectRunHubService projectRunHubService,
                           ITestFlowRunService testFlowRunService,
                           ITestFlowRunStepService testFlowRunStepService,
                           ITestFlowService testFlowService) {
        this(new TestFlowRunTriggerCore(
                testFlowExecutionService, projectRunHubService, testFlowRunService,
                testFlowRunStepService, testFlowService));
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.RUN_TEST_FLOW.getId();
    }

    /**
     * 解析参数后交给跑流核心执行；允许用画布默认场景/环境补全。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return 回执 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        TestFlowRunTriggerCore.ResolvedRunArgs args =
                TestFlowRunTriggerCore.resolveArgs(arguments, ctx, true);
        if (args.testFlowId() == null) {
            return FlowDesignToolSupport.errorJson("缺少 testFlowId");
        }
        Long operatorUserId = ctx != null ? ctx.getOperatorUserId() : null;
        return runCore.run(args.testFlowId(), args.runScenarioId(), args.testProjectEnvId(),
                operatorUserId, ctx);
    }
}
