package com.qualitest.project.runhub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目运行中心「活动」区单行缓存结构。
 * 存在 Redis Hash（按项目分桶）中，终态或用户移除后删除；正式报告仍在库表。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRunHubActiveItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 列表行稳定 id。
     * 排队项形如 {@code queue:…}，执行中 Run 形如 {@code run:…}。
     */
    private String itemId;
    /** 活动状态：queued（排队）或 running（执行中） */
    private String status;
    /** 行类型：queue（排队）/ flow（执行中单流） */
    private String kind;
    /** 来源归类：manual / ai / mcp / suite 等 */
    private String source;
    /** 所属项目 */
    private Long testProjectId;
    /** 所用环境 */
    private Long testProjectEnvId;
    /** 环境名 */
    private String envName;
    /** 测试流 id（单流跑时有值） */
    private Long testFlowId;
    /** 测试流名 */
    private String flowName;
    /** 已开跑时的 Run 主键 */
    private Long testFlowRunId;
    /** 运行场景 id */
    private String runScenarioId;
    /** 原始触发类型字符串 */
    private String triggerType;
    /** 操作者用户 id */
    private Long operatorUserId;
    /** 最近更新时间戳（毫秒），用于排序 */
    private long updatedAtMs;
    /** 列表展示标题 */
    private String title;
}
