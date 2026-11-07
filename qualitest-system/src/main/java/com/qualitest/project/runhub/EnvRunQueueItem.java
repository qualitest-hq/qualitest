package com.qualitest.project.runhub;

import com.qualitest.project.params.TriggerTestFlowRunParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按环境串行时的排队项，仅保存在 Redis 列表中，不写业务库。
 * 轮到该环境空闲时，用 {@link #triggerParams} 原样再次触发跑流。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnvRunQueueItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 排队项唯一 id，开跑后用于绑定 runId、取消排队 */
    private String queueItemId;
    /** 所属测试项目 */
    private Long testProjectId;
    /** 排队所在的测试环境（串行粒度） */
    private Long testProjectEnvId;
    /** 环境显示名（运行中心展示） */
    private String envName;
    /** 待跑的测试流 */
    private Long testFlowId;
    /** 测试流显示名 */
    private String flowName;
    /** 运行场景 id */
    private String runScenarioId;
    /** 触发来源：manual / ai / suite 等 */
    private String triggerType;
    /** 发起人用户 id */
    private Long operatorUserId;
    /** 入队时间戳（毫秒） */
    private long enqueuedAtMs;

    /**
     * 触发参数快照。
     * 提升开跑时原样传入；内部会标记 bypassEnvQueue，避免再次入队。
     */
    private TriggerTestFlowRunParams triggerParams;
}
