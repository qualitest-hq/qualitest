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
import java.util.Date;

/**
 * 项目运行中心单行。
 * 既用于活动缓存（排队 / 执行中），也用于最近正式 Run 历史列表。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRunHubItemResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 列表行稳定 id（活动：queue:… / run:…；最近记录：run:…） */
    private String itemId;

    /** queued / running / passed / failed / paused / aborted / cancelled 等 */
    private String status;

    /** manual / mcp / ai / suite / ci / schedule 等 */
    private String source;

    /** flow / suite / queue */
    private String kind;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    private String envName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    private String flowName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunId;

    private String suiteName;

    private String runScenarioId;

    private String triggerType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long operatorUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    /** 耗时毫秒（正式 Run 有值；排队项通常为空） */
    private Long durationMs;

    /** 列表主标题（流名 / 排队说明等，不含状态后缀） */
    private String title;
}
