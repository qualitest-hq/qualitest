package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.snapshot.PluginSnapshotSupport;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 暂停续跑时的数据还原服务。
 * 经已启用 onRestore 插件调度还原，并生成还原审计步。
 */
@Component
@RequiredArgsConstructor
public class SnapshotRestoreService {

    private final PluginSnapshotSupport pluginSnapshotSupport;
    private final StepResultWriter stepResultWriter;

    /**
     * 按 snapshotId（存档 saveId）还原被测数据。
     */
    public StepResult restore(TestProjectEnv env, Long testFlowRunId, String snapshotId, String nodeId) {
        if (!SnapshotEnvSupport.isResetAllowed(env)) {
            throw new FlowExecutionException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT,
                    "环境未开启破坏性还原，无法还原并重试");
        }
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "snapshotId 不能为空");
        }
        if (!pluginSnapshotSupport.isRestoreProviderEnabled(env.getTestProjectEnvId())) {
            throw new FlowExecutionException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT,
                    "未启用声明 onRestore 的插件（如库存档点）");
        }

        long t0 = System.currentTimeMillis();
        try {
            pluginSnapshotSupport.restoreSave(env, testFlowRunId, snapshotId, nodeId);
        } catch (PluginException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_SNAPSHOT_RESTORE_FAILED, e.getMessage());
        }
        long durationMs = Math.max(0, System.currentTimeMillis() - t0);
        return stepResultWriter.toRestoreStepResult(
                nodeId, snapshotId,
                pluginSnapshotSupport.describeCheckpointProvider(env.getTestProjectEnvId()),
                durationMs);
    }
}
