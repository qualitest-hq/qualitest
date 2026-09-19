package com.qualitest.ai.tools.flow;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;

import java.util.Map;

/**
 * MCP 跑流工具门面：触发指定测试流正式 Run。
 * <p>
 * 必须提供测试流 id（工具参数或请求信封），且上下文须带 Token 绑定的操作者用户 id。
 * 不使用「当前打开的流」隐式目标；场景/环境仅认显式参数。
 */
public class McpRunTestFlowTool implements QualitestTool {

    /** 跑流核心 */
    private final TestFlowRunTriggerCore runCore;

    /**
     * 使用已组装的跑流核心构造
     *
     * @param runCore 跑流核心
     */
    public McpRunTestFlowTool(TestFlowRunTriggerCore runCore) {
        this.runCore = runCore;
    }

    /**
     * 单测用：按依赖自行组装跑流核心
     */
    public McpRunTestFlowTool(ITestFlowExecutionService testFlowExecutionService,
                              ITestFlowRunService testFlowRunService,
                              ITestFlowRunStepService testFlowRunStepService,
                              ITestFlowService testFlowService,
                              GraphJsonValidator graphJsonValidator,
                              FlowDesignPatchNormalizer patchNormalizer) {
        this(new TestFlowRunTriggerCore(
                testFlowExecutionService, testFlowRunService, testFlowRunStepService,
                testFlowService, graphJsonValidator, patchNormalizer));
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.RUN_TEST_FLOW.getId();
    }

    /**
     * 校验操作者与测试流 id 后交给跑流核心；不用画布默认场景/环境。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文（须含操作者）
     * @return 回执 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx == null || ctx.getOperatorUserId() == null) {
            return FlowDesignToolSupport.errorJson("Project Token 未绑定操作者");
        }
        TestFlowRunTriggerCore.ResolvedRunArgs args =
                TestFlowRunTriggerCore.resolveArgs(arguments, ctx, false);
        if (args.testFlowId() == null) {
            return FlowDesignToolSupport.errorJson("缺少 testFlowId：请显式传入");
        }
        return runCore.run(args.testFlowId(), args.runScenarioId(), args.testProjectEnvId(),
                ctx.getOperatorUserId(), ctx);
    }
}
