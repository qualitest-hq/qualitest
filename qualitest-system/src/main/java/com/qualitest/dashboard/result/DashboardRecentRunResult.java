package com.qualitest.dashboard.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.Date;

/**
 * 首页最近运行记录
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardRecentRunResult")
public class DashboardRecentRunResult implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    private String flowName;

    private String projectName;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long durationMs;

    private String triggerType;
}
