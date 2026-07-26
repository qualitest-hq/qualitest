package com.qualitest.flow.snapshot;



import com.qualitest.flow.exception.FlowErrorCode;

import com.qualitest.flow.model.GraphNode;

import com.qualitest.project.domain.TestProjectEnv;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;



import java.util.HashMap;

import java.util.Map;



/**

 * 节点业务逻辑执行前的数据 checkpoint 服务。

 * <p>

 * 仅当节点开启 snapshotBefore 时才介入；否则返回 null，表示无需快照。

 * 环境未开启 allowDestructiveReset 时静默跳过（返回 null），流程图可在不同环境复用。

 * 环境已开启但端点无效时返回失败结果，由运行场景 onSnapshotFailure 决定中止/暂停/继续。

 */

@Component

@RequiredArgsConstructor

public class SnapshotCheckpointService {



    /** 调用被测 snapshot 接口的超时毫秒数 */

    public static final long DEFAULT_TIMEOUT_MS = 120_000L;



    private final DbSnapshotAdapter snapshotAdapter;



    /**

     * 按节点配置尝试打 checkpoint。

     *

     * @return null 表示未开启快照或环境未允许还原（静默跳过）；非 null 为成功或失败尝试结果

     */

    public CheckpointAttempt maybeCheckpoint(

            GraphNode node,

            Long testFlowRunId,

            TestProjectEnv env,

            RunSnapshotPolicy policy,

            FlowRunSnapshotState snapshotState

    ) {

        if (!GraphNodeSnapshotSupport.isSnapshotBefore(node)) {

            return null;

        }

        if (env == null) {

            return failureAttempt(node, FlowErrorCode.TF_SNAPSHOT_ENDPOINT, "环境不存在", policy);

        }

        if (!SnapshotEnvSupport.isResetAllowed(env)) {

            return null;

        }

        String resetBase = SnapshotEnvSupport.resolveResetBase(env);

        if (resetBase.isBlank()) {

            return failureAttempt(node, FlowErrorCode.TF_SNAPSHOT_ENDPOINT, "无法从 envUrl 派生 reset 端点", policy);

        }



        SnapshotScope scope = GraphNodeSnapshotSupport.resolveScope(node);

        Map<String, Object> meta = new HashMap<>();

        meta.put("env", env.getEnvName());

        meta.put("nodeName", GraphNodeSnapshotSupport.nodeName(node));



        String label = testFlowRunId + ":" + node.getId();

        long t0 = System.currentTimeMillis();

        try {

            SnapshotRef ref = snapshotAdapter.snapshot(SnapshotRequest.builder()

                    .resetEndpointBase(resetBase)

                    .scope(scope.getScope())

                    .tables(scope.getTables())

                    .label(label)

                    .meta(meta)

                    .timeoutMs(DEFAULT_TIMEOUT_MS)

                    .build());

            long durationMs = Math.max(0, System.currentTimeMillis() - t0);

            snapshotState.push(node.getId(), ref.getSnapshotId());

            return CheckpointAttempt.success(node, ref, resetBase, scope, label, durationMs);

        } catch (SnapshotException e) {

            long durationMs = Math.max(0, System.currentTimeMillis() - t0);

            return CheckpointAttempt.failure(node, e.getErrorCode(), e.getMessage(), resetBase, scope, label, durationMs, policy);

        }

    }



    private CheckpointAttempt failureAttempt(GraphNode node, FlowErrorCode code, String message, RunSnapshotPolicy policy) {

        return CheckpointAttempt.failure(

                node, code, message, "", GraphNodeSnapshotSupport.resolveScope(node), "", 0L, policy);

    }

}


