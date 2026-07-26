package com.qualitest.dashboard.result;

import com.qualitest.project.result.TestProjectResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.List;

/**
 * 首页仪表盘汇总
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardSummaryResult")
public class DashboardSummaryResult implements Serializable {

    private Long projectCount;

    private Long flowCount;

    private Long apiCount;

    private Long runCount;

    /**
     * 近7天已完成运行通过率（不含 running）
     */
    private Double recentPassRate;

    private List<TestProjectResult> recentProjects;

    private List<DashboardRecentRunResult> recentRuns;

    private List<DashboardRunTrendItem> runTrend;
}
