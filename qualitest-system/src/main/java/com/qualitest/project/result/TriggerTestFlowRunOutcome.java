package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 触发正式跑流的回执。
 * <ul>
 *   <li>{@code started}：已占环境槽（若串行）、写入 running 记录并后台开跑，带 {@link #runId}</li>
 *   <li>{@code queued}：项目为按环境串行且环境占用中，已入队，尚无 runId；带 {@link #queueItemId} 供等待开跑</li>
 * </ul>
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TriggerTestFlowRunOutcome implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** started：已开跑；queued：已入环境排队 */
    private String state;

    /** 已开跑时的 Run 主键；排队态为空 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long runId;

    /** 排队项 id（state=queued 时有值），用于轮询绑定的 runId 或取消排队 */
    private String queueItemId;

    /** 所属项目 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 所用 / 排队所在环境 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /** 给人看的提示，例如「环境某某有任务在跑，已排队」 */
    private String message;

    /** 构造「已开跑」回执 */
    public static TriggerTestFlowRunOutcome started(Long runId, Long projectId, Long envId) {
        return TriggerTestFlowRunOutcome.builder()
                .state("started")
                .runId(runId)
                .testProjectId(projectId)
                .testProjectEnvId(envId)
                .build();
    }

    /** 构造「已入队」回执 */
    public static TriggerTestFlowRunOutcome queued(
            String queueItemId, Long projectId, Long envId, String message) {
        return TriggerTestFlowRunOutcome.builder()
                .state("queued")
                .queueItemId(queueItemId)
                .testProjectId(projectId)
                .testProjectEnvId(envId)
                .message(message)
                .build();
    }

    /** 是否已开跑并返回了 runId */
    public boolean isStarted() {
        return "started".equalsIgnoreCase(state);
    }

    /** 是否进入环境排队、尚无 runId */
    public boolean isQueued() {
        return "queued".equalsIgnoreCase(state);
    }
}
