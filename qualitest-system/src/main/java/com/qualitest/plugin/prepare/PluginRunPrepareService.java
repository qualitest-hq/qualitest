package com.qualitest.plugin.prepare;

import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 跑流 onRunPrepare：委托 PluginHookRunner 按已启用插件 hooks 调度。
 */
@Service
@RequiredArgsConstructor
public class PluginRunPrepareService {

    private final PluginHookRunner hookRunner;

    /**
     * 首次跑流前调用；未启用任何带 onRunPrepare 的插件时为空操作。
     */
    public void runPrepare(TestProjectEnv env, Long runId) {
        hookRunner.runPrepare(env, runId);
    }
}
