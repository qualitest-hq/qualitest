package com.qualitest.project.params;

import lombok.Data;

/**
 * 测试流图校验入参。
 */
@Data
public class FlowGraphValidateParams {

    /** 图 JSON 文本 */
    private String graphJson;

    /** 所属项目 id（路由 / 鉴权） */
    private Long testProjectId;

    /** 当前流 id（排除自身已占路由） */
    private Long testFlowId;

    /**
     * 仅 stage=canvas：有未确认 Staging 边时为 true，延后开始节点等拓扑硬拦。
     */
    private Boolean deferTopology;

    /**
     * 仅 stage=run：环境 id，用于调度已启用插件的 onFlowInspect。
     */
    private Long testProjectEnvId;
}
