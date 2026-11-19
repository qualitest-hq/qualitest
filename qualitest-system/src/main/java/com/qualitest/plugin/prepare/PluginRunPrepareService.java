package com.qualitest.plugin.prepare;

import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 跑流 onRunPrepare：委托 PluginHookRunner 按已启用插件 hooks 调度，并产出可落库的审计步。
 */
@Service
@RequiredArgsConstructor
public class PluginRunPrepareService {

    private final PluginHookRunner hookRunner;
    private final StepResultWriter stepResultWriter;

    /**
     * 首次跑流前调用；未启用任何带 onRunPrepare 的插件时返回空列表。
     *
     * @return 每次插件调用对应一条步骤结果，供执行器写入时间线
     */
    public List<StepResult> runPrepare(TestProjectEnv env, Long runId) {
        long t0 = System.currentTimeMillis();
        List<PluginHookRunner.HookInvokeOutcome> outcomes = hookRunner.runPrepare(env, runId);
        List<StepResult> steps = new ArrayList<>();
        if (outcomes == null || outcomes.isEmpty()) {
            return steps;
        }
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);
        long per = Math.max(0, durationMs / outcomes.size());
        for (PluginHookRunner.HookInvokeOutcome o : outcomes) {
            PluginInvokeResult r = o.getResult();
            String summary = r == null ? null : (r.getParsedJson() != null ? r.getParsedJson() : r.getStdout());
            steps.add(stepResultWriter.toPluginInvokeStepResult(
                    o.getPluginId(),
                    o.getCapability(),
                    "onRunPrepare",
                    r != null && r.isOk(),
                    summary,
                    per));
        }
        return steps;
    }
}
