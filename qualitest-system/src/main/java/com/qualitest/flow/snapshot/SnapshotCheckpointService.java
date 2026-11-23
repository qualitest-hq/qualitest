package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.snapshot.PluginSnapshotSupport;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点业务逻辑执行前的数据 checkpoint 服务。
 * 仅当节点开启 snapshotBefore 时才介入；执行经已启用 onCheckpoint 插件。
 */
@Component
@RequiredArgsConstructor
public class SnapshotCheckpointService {

    private final PluginSnapshotSupport pluginSnapshotSupport;

    /**
     * 按节点配置尝试打 checkpoint。
     *
     * @return null 表示未开启快照或环境未允许还原；非 null 为成功或失败尝试结果
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
        if (!pluginSnapshotSupport.isCheckpointProviderEnabled(env.getTestProjectEnvId())) {
            return failureAttempt(node, FlowErrorCode.TF_SNAPSHOT_ENDPOINT,
                    "未启用声明 onCheckpoint 的插件（如库存档点）", policy);
        }

        SnapshotScope scope = GraphNodeSnapshotSupport.resolveScope(node);
        Map<String, Object> meta = new HashMap<>();
        meta.put("env", env.getEnvName());
        meta.put("nodeName", GraphNodeSnapshotSupport.nodeName(node));

        String label = testFlowRunId + ":" + node.getId();
        String provider = pluginSnapshotSupport.describeCheckpointProvider(env.getTestProjectEnvId());
        long t0 = System.currentTimeMillis();
        try {
            int seq = snapshotState.entries().size() + 1;
            String saveId = pluginSnapshotSupport.createSave(env, testFlowRunId, node.getId(), label, seq);
            long durationMs = Math.max(0, System.currentTimeMillis() - t0);
            SnapshotRef ref = SnapshotRef.builder()
                    .snapshotId(saveId)
                    .createdAt(null)
                    .build();
            snapshotState.push(node.getId(), ref.getSnapshotId());
            return CheckpointAttempt.success(node, ref, provider, scope, label, durationMs);
        } catch (PluginException e) {
            long durationMs = Math.max(0, System.currentTimeMillis() - t0);
            FlowErrorCode code = mapPluginError(e.getErrorCode());
            return CheckpointAttempt.failure(node, code, e.getMessage(),
                    provider, scope, label, durationMs, policy);
        } catch (Exception e) {
            long durationMs = Math.max(0, System.currentTimeMillis() - t0);
            return CheckpointAttempt.failure(node, FlowErrorCode.TF_SNAPSHOT_FAILED, e.getMessage(),
                    provider, scope, label, durationMs, policy);
        }
    }

    private FlowErrorCode mapPluginError(PluginErrorCode code) {
        if (code == null) {
            return FlowErrorCode.TF_SNAPSHOT_FAILED;
        }
        return switch (code) {
            case PROVIDER_NOT_FOUND, PACKAGE_NOT_FOUND, PROJECT_MOUNT_REQUIRED -> FlowErrorCode.TF_SNAPSHOT_ENDPOINT;
            case GATE_DENIED -> FlowErrorCode.TF_SNAPSHOT_ENDPOINT;
            case AGENT_UNREACHABLE -> FlowErrorCode.TF_SNAPSHOT_FAILED;
            default -> FlowErrorCode.TF_SNAPSHOT_FAILED;
        };
    }

    private CheckpointAttempt failureAttempt(GraphNode node, FlowErrorCode code, String message, RunSnapshotPolicy policy) {
        return CheckpointAttempt.failure(node, code, message, "none",
                GraphNodeSnapshotSupport.resolveScope(node),
                node == null ? "" : node.getId(), 0L, policy);
    }
}
