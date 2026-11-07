package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowRunService;

import java.util.Map;

/**
 * 测试流 Run 查询类工具的共用加载逻辑。
 * <p>
 * 负责从工具参数或请求上下文解析 runId，按 id 读库，并校验该 Run 属于当前测试流。
 */
public final class RunToolSupport {

    private RunToolSupport() {
    }

    /**
     * 解析 runId 并加载 Run 记录。
     * <p>
     * runId 优先取参数 {@code runId}；未传时回退到上下文中的运行 ID。
     * 加载失败时 {@link LoadedRun#errorJson} 非空，成功时 {@link LoadedRun#run} 非空。
     *
     * @param arguments           工具入参
     * @param ctx                 请求上下文（可带当前测试流 id、上下文运行 id）
     * @param testFlowRunService  Run 查询服务
     * @param missingRunIdMessage 参数与上下文都没有 runId 时返回的错误文案
     * @return 加载结果（成功含 runId 与 Run，失败仅含错误 JSON）
     */
    public static LoadedRun loadRun(
            Map<String, Object> arguments,
            FlowDesignToolContext ctx,
            ITestFlowRunService testFlowRunService,
            String missingRunIdMessage
    ) {
        Long runId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("runId") : null);
        if (runId == null) {
            runId = ctx.getContextRunId();
        }
        if (runId == null) {
            return LoadedRun.error(missingRunIdMessage);
        }
        TestFlowRunResult run = testFlowRunService.selectTestFlowRunResult(runId);
        if (run == null) {
            return LoadedRun.error("Run 不存在");
        }
        if (run.getTestFlowId() != null && ctx.getTestFlowId() != null
                && !run.getTestFlowId().equals(ctx.getTestFlowId())) {
            return LoadedRun.error("Run 不属于当前测试流");
        }
        return LoadedRun.ok(runId, run);
    }

    /**
     * 检查参数是否显式传入了 runId（不回退上下文运行 ID）。
     * <p>
     * 缺省时返回错误 JSON；已传入时返回 null，由调用方继续执行。
     *
     * @param arguments 工具入参
     * @return 缺 runId 时的错误 JSON，否则 null
     */
    public static String requireExplicitRunIdOrError(Map<String, Object> arguments) {
        Long runId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("runId") : null);
        if (runId == null) {
            return FlowDesignToolSupport.errorJson("缺少 runId：请传入 runId");
        }
        return null;
    }

    /**
     * Run 加载结果。
     * <p>
     * 失败：{@code errorJson} 有值，{@code runId}/{@code run} 为 null。
     * 成功：{@code errorJson} 为 null，{@code runId} 与 {@code run} 有值。
     *
     * @param errorJson 错误回执 JSON；成功时为 null
     * @param runId     运行记录 id
     * @param run       运行记录
     */
    public record LoadedRun(String errorJson, Long runId, TestFlowRunResult run) {

        /** 构造失败结果（把文案包装成错误 JSON） */
        static LoadedRun error(String message) {
            return new LoadedRun(FlowDesignToolSupport.errorJson(message), null, null);
        }

        /** 构造成功结果 */
        static LoadedRun ok(Long runId, TestFlowRunResult run) {
            return new LoadedRun(null, runId, run);
        }

        /** 是否加载失败（有错误 JSON） */
        public boolean failed() {
            return errorJson != null;
        }
    }
}
