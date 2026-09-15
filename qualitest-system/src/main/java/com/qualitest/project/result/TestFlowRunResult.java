package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试流运行 Result 对象
 *
 * @author qualitest
 * @since 2026-06-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowRunResult")
public class TestFlowRunResult implements Serializable {

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
    @Excel(name = "运行场景ID")
    private String runScenarioId;

    /**
     * 运行状态（running执行中 passed成功 failed失败 paused暂停 aborted中止 cancelled取消）
     */
    @Excel(name = "运行状态", readConverterExp = "running=执行中,passed=成功,failed=失败,paused=暂停,aborted=中止,cancelled=取消")
    private String status;

    /**
     * 流程图快照
     */
    @Excel(name = "流程图快照")
    private String graphJsonSnapshot;

    /**
     * 流程变量快照
     */
    @Excel(name = "流程变量快照")
    private String flowSnapshot;

    /**
     * 暂停/续跑运行时状态 JSON
     */
    private String runExecutionState;

    /**
     * 进入 paused 状态时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pausedAt;

    /**
     * 流程图指纹哈希
     */
    @Excel(name = "流程图指纹哈希")
    private String graphFingerprint;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    /**
     * 耗时
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long durationMs;

    /**
     * 失败错误码
     */
    @Excel(name = "失败错误码")
    private String errorCode;

    /**
     * 失败错误信息
     */
    @Excel(name = "失败错误信息")
    private String errorMessage;

    /**
     * 触发来源：manual（画布人手）、ai（全自动）、ci、schedule 等
     */
    @Excel(name = "触发方式", readConverterExp = "manual=手动,ai=AI,ci=持续集成,schedule=定时")
    private String triggerType;


}
