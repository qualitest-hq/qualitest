package com.qualitest.dashboard.service;

import com.qualitest.dashboard.result.DashboardApiHealthAttentionResult;
import com.qualitest.dashboard.result.DashboardSummaryResult;

/**
 * 首页仪表盘 Service
 */
public interface IDashboardService {

    /**
     * 获取首页汇总数据
     */
    DashboardSummaryResult getSummary();

    /**
     * 获取仍有 API 语义健康告警的测试流列表。
     * 读库中已落库的 api_health_*，按权限过滤；不解析 graph_json。
     */
    DashboardApiHealthAttentionResult getApiHealthAttention();
}
