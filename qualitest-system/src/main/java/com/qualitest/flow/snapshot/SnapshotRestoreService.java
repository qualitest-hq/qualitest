package com.qualitest.flow.snapshot;



import com.qualitest.flow.exception.FlowErrorCode;

import com.qualitest.flow.exception.FlowExecutionException;

import com.qualitest.flow.node.StepResult;

import com.qualitest.flow.run.StepResultWriter;

import com.qualitest.project.domain.TestProjectEnv;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;



/**

 * 暂停续跑时的数据还原服务。

 * <p>

 * 调用被测方 restore 接口，并生成还原审计步。

 * 环境未开启 allowDestructiveReset 时静默跳过，返回 null，不写审计步、不调被测端点。

 */

@Component

@RequiredArgsConstructor

public class SnapshotRestoreService {



    private final DbSnapshotAdapter snapshotAdapter;

    private final StepResultWriter stepResultWriter;



    /**

     * 按 snapshotId 还原被测数据。

     *

     * @return 还原审计步；环境未允许还原时返回 null

     */

    public StepResult restore(TestProjectEnv env, Long testFlowRunId, String snapshotId, String nodeId) {

        if (!SnapshotEnvSupport.isResetAllowed(env)) {

            return null;

        }

        String resetBase = SnapshotEnvSupport.requireResetBase(env);

        if (snapshotId == null || snapshotId.isBlank()) {

            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "snapshotId 不能为空");

        }



        long t0 = System.currentTimeMillis();

        try {

            snapshotAdapter.restore(resetBase, snapshotId, SnapshotCheckpointService.DEFAULT_TIMEOUT_MS);

        } catch (SnapshotException e) {

            throw new FlowExecutionException(

                    e.getErrorCode() != null ? e.getErrorCode() : FlowErrorCode.TF_SNAPSHOT_RESTORE_FAILED,

                    e.getMessage());

        }

        long durationMs = Math.max(0, System.currentTimeMillis() - t0);

        return stepResultWriter.toRestoreStepResult(nodeId, snapshotId, resetBase, durationMs);

    }

}


