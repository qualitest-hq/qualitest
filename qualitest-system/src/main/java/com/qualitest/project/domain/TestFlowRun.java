package com.qualitest.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * 测试流运行对象 test_flow_run
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowRun")
public class TestFlowRun extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 运行ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    /**
     * 测试流ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /**
     * 测试项目环境ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /**
     * 运行场景ID
     */
    private String runScenarioId;

    /**
     * 运行状态（running执行中 passed成功 failed失败 paused暂停 aborted中止 cancelled取消）
     */
    private String status;

    /**
     * 流程图快照（Run 触发时固化，供审计与回放；不参与 graph schema 升级写回）。
     */
    private String graphJsonSnapshot;

    /**
     * 流程变量快照
     */
    private String flowSnapshot;

    /**
     * 暂停/续跑运行时状态 JSON（游标、上下文、快照栈）
     */
    private String runExecutionState;

    /**
     * 进入 paused 状态时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pausedAt;

    /**
     * 更新时置空 run_execution_state 与 paused_at（非表列）
     */
    private Boolean clearExecutionState;

    /**
     * 流程图指纹哈希
     */
    private String graphFingerprint;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    /**
     * 耗时
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long durationMs;

    /**
     * 失败错误码
     */
    private String errorCode;

    /**
     * 失败错误信息
     */
    private String errorMessage;

    /**
     * 触发方式（manual手动 ci持续集成 schedule定时）
     */
    private String triggerType;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
