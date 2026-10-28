package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 批量串行触发正式 Run 的请求体。
 * <p>
 * 优先使用 {@code items}（可同流多场景）；若为空则回退 {@code testFlowIds}（每流默认场景）。
 * 适合短批或服务端调用；单次请求会阻塞至整批结束，长批请由客户端逐条 trigger 并轮询。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunBatchParams {

    /**
     * 队列项（保序）。同流多个场景写成多条。
     */
    private List<TriggerTestFlowRunBatchItemParams> items;

    /**
     * 仅流 id 列表（保序去重）；仅当 {@code items} 为空时生效，每流走默认场景。
     */
    private List<Long> testFlowIds;

    /** 可选，统一覆盖各场景绑定的环境 */
    private Long testProjectEnvId;

    /**
     * 触发来源，写入运行记录；空则按 suite。
     */
    private String triggerType;

    /** 操作者用户 id；未传则用当前登录用户 */
    private Long operatorUserId;

    /**
     * 非通过（含 failed / paused / aborted / cancelled / 触发失败）时是否停止后续项。
     * 空或 true 表示停止。
     */
    private Boolean stopOnNonPass;

    /**
     * 单条 Run 等待终态的最长毫秒数；空则 600000；上限 1800000。
     */
    private Long perRunTimeoutMs;
}
