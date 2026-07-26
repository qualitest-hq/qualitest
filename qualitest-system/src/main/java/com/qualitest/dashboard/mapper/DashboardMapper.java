package com.qualitest.dashboard.mapper;

import com.qualitest.dashboard.params.DashboardParams;
import com.qualitest.dashboard.result.DashboardApiHealthAttentionItem;
import com.qualitest.dashboard.result.DashboardRecentRunResult;
import com.qualitest.dashboard.result.DashboardRunStatusCount;
import com.qualitest.dashboard.result.DashboardRunTrendItem;
import com.qualitest.project.result.TestProjectResult;

import java.util.List;

/**
 * 首页仪表盘 Mapper
 */
public interface DashboardMapper {

    Long countProjects(DashboardParams params);

    Long countFlows(DashboardParams params);

    Long countApis(DashboardParams params);

    Long countRuns(DashboardParams params);

    List<TestProjectResult> selectRecentProjects(DashboardParams params);

    List<DashboardRecentRunResult> selectRecentRuns(DashboardParams params);

    List<DashboardRunTrendItem> selectRunTrend(DashboardParams params);

    DashboardRunStatusCount selectRecentRunStatusCount(DashboardParams params);

    /**
     * 统计当前用户可见范围内、api_health_warning_count &gt; 0 的测试流总数。
     * 非管理员只统计其为成员的项目。
     */
    Long countApiHealthAttention(DashboardParams params);

    /**
     * 查询当前用户可见范围内仍有 API 语义告警的测试流摘要。
     * 按告警数降序、检查时间降序，最多 20 条；不读取 graph_json。
     */
    List<DashboardApiHealthAttentionItem> selectApiHealthAttention(DashboardParams params);
}
