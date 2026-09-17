package com.qualitest.flow.sync;

import com.qualitest.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 组装并发布外部变更通知。
 * 本机先扇出给已订阅的画布连接，再经 Redis 广播到其它应用实例。
 */
@Component
@RequiredArgsConstructor
public class FlowExternalChangePublisher {

    private final FlowExternalChangeHub hub;
    private final FlowExternalChangeRedisBridge redisBridge;

    /** 图已落库（无人手增量片段时，前端整图重拉） */
    public void publishGraphCommitted(Long testFlowId, Long testProjectId, String source, Date updateTime) {
        publishGraphCommitted(testFlowId, testProjectId, source, updateTime, null);
    }

    /**
     * 图已落库。
     *
     * @param patch 本批节点/边增量；空则前端走整图重拉
     */
    public void publishGraphCommitted(Long testFlowId, Long testProjectId, String source, Date updateTime,
                                      FlowGraphCommitPatchHolder.PatchPayload patch) {
        if (testFlowId == null) {
            return;
        }
        FlowExternalChangeEvent.FlowExternalChangeEventBuilder b = FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_GRAPH_COMMITTED)
                .testFlowId(testFlowId)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .updateTime(formatTime(updateTime));
        if (patch != null && !patch.isEmpty()) {
            b.changedNodeIds(patch.changedNodeIds() != null ? patch.changedNodeIds() : List.of())
                    .nodePatches(patch.nodePatches() != null ? patch.nodePatches() : List.of())
                    .edgePatches(patch.edgePatches() != null ? patch.edgePatches() : List.of())
                    .deletedNodeIds(patch.deletedNodeIds() != null ? patch.deletedNodeIds() : List.of())
                    .deletedEdgeIds(patch.deletedEdgeIds() != null ? patch.deletedEdgeIds() : List.of());
        }
        publish(b.build());
    }

    /** 项目素材变量已写入 */
    public void publishAssetVariablesChanged(Long testProjectId, String source, List<String> keys) {
        if (testProjectId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_ASSET_VARIABLES_CHANGED)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .keys(keys != null ? keys : List.of())
                .build());
    }

    /** 项目鉴权配置已写入 */
    public void publishAuthConfigChanged(Long testProjectId, String source) {
        if (testProjectId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_AUTH_CONFIG_CHANGED)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .build());
    }

    /** 项目环境列表已变更 */
    public void publishProjectEnvsChanged(Long testProjectId, String source) {
        if (testProjectId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_PROJECT_ENVS_CHANGED)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .build());
    }

    /** 已触发一次测试流 Run */
    public void publishRunStarted(Long testFlowId, Long testProjectId, Long runId, String source) {
        if (testFlowId == null || runId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_RUN_STARTED)
                .testFlowId(testFlowId)
                .testProjectId(testProjectId)
                .runId(runId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .build());
    }

    /** 流名称等元数据变更（不含 graph_json） */
    public void publishFlowMetaChanged(Long testFlowId, Long testProjectId, String source) {
        if (testFlowId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_FLOW_META_CHANGED)
                .testFlowId(testFlowId)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .build());
    }

    /** 新建测试流 */
    public void publishFlowCreated(Long testFlowId, Long testProjectId, String source) {
        if (testFlowId == null) {
            return;
        }
        publish(FlowExternalChangeEvent.builder()
                .type(FlowExternalChangeEvent.TYPE_FLOW_CREATED)
                .testFlowId(testFlowId)
                .testProjectId(testProjectId)
                .source(source != null ? source : FlowExternalChangeSourceHolder.getOrDefault())
                .build());
    }

    /** 本机订阅者推送 + Redis 广播其它实例 */
    public void publish(FlowExternalChangeEvent event) {
        hub.publish(event);
        if (redisBridge != null) {
            redisBridge.broadcast(event);
        }
    }

    private static String formatTime(Date updateTime) {
        if (updateTime == null) {
            return DateUtils.getTime();
        }
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, updateTime);
    }
}
