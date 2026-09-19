package com.qualitest.flow.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 单步执行结果。
 * <p>
 * 索引列字段（{@code node_id}、{@code node_type}、{@code status} 等）与 {@code step_details} JSON 分离落库；
 * 类型专属键（{@code http}、{@code assert}、{@code branchTaken}、{@code assigns} 等）写入 {@code step_details}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    /** 图内节点 id */
    private String nodeId;

    /** 节点 type，如 {@code http} / {@code assert} */
    private String nodeType;

    /** 展示名：{@code data.name} 或节点类型标签 */
    private String nodeName;

    /** 入边 id；开始节点为 null */
    private String edgeId;

    /**
     * 步骤状态 code：{@code passed} / {@code failed} / {@code paused} / {@code skipped}。
     * paused 表示步骤主动暂停（当前仅 Input：等待人工输入），图遍历器据此将 Run 置为 paused。
     */
    private String status;

    /** 本步耗时（毫秒） */
    private long durationMs;

    /** 本步结束后 {@code flow} 变量快照 */
    @Builder.Default
    private Map<String, Object> flowAfter = new HashMap<>();

    /** 失败时的错误码与消息 */
    private StepError error;

    /** http 步骤详情 */
    private Map<String, Object> http;

    /** assert 步骤详情 */
    private Map<String, Object> assertDetails;

    /** http 提取结果列表 */
    private Object extracts;

    /**
     * condition 节点命中分支摘要，写入 step_details.branchTaken。
     * 键：branchId（分支稳定 id）、kind（if/elif/else）。
     */
    private Map<String, Object> branchTaken;

    /**
     * assign / input 等写入结果列表，落入 step_details.assigns。
     * 每项通常含 scope、name、op、before、after。
     */
    private Object assigns;

    /**
     * script 节点执行详情，写入 step_details.script。
     * 含 language、logs、writes。
     */
    private Map<String, Object> script;

    /**
     * subflow 节点落库详情（step_details.subflow）。
     * 含 subflowId、subflowName、status、childSteps（内层步骤摘要）、outputsMerged（成功时）。
     */
    private Map<String, Object> subflow;

    /**
     * 节点前 checkpoint 审计详情，序列化到 step_details.snapshot。
     * 含 snapshotId、scope、tables、label、resetEndpoint、耗时等；失败时 step_details.error 另有说明。
     */
    private Map<String, Object> snapshot;
}
