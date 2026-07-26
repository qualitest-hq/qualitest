package com.qualitest.dashboard.service.impl;

import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.dashboard.mapper.DashboardMapper;
import com.qualitest.dashboard.params.DashboardParams;
import com.qualitest.dashboard.result.DashboardApiHealthAttentionResult;
import com.qualitest.dashboard.result.DashboardRunStatusCount;
import com.qualitest.dashboard.result.DashboardRunTrendItem;
import com.qualitest.dashboard.result.DashboardSummaryResult;
import com.qualitest.dashboard.service.IDashboardService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页仪表盘 Service 实现
 */
@Service
@AllArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardSummaryResult getSummary() {
        DashboardParams params = buildParams();

        Long projectCount = defaultLong(dashboardMapper.countProjects(params));
        Long flowCount = defaultLong(dashboardMapper.countFlows(params));
        Long apiCount = defaultLong(dashboardMapper.countApis(params));
        Long runCount = defaultLong(dashboardMapper.countRuns(params));

        DashboardRunStatusCount statusCount = dashboardMapper.selectRecentRunStatusCount(params);
        double recentPassRate = computePassRate(statusCount);

        List<DashboardRunTrendItem> runTrend = fillRunTrend(dashboardMapper.selectRunTrend(params));

        return DashboardSummaryResult.builder()
                .projectCount(projectCount)
                .flowCount(flowCount)
                .apiCount(apiCount)
                .runCount(runCount)
                .recentPassRate(recentPassRate)
                .recentProjects(defaultList(dashboardMapper.selectRecentProjects(params)))
                .recentRuns(defaultList(dashboardMapper.selectRecentRuns(params)))
                .runTrend(runTrend)
                .build();
    }

    /**
     * 组装接口变更待关注：总数 + 截断列表。
     * 非管理员仅包含当前用户作为成员的项目下的流。
     */
    @Override
    public DashboardApiHealthAttentionResult getApiHealthAttention() {
        DashboardParams params = buildParams();
        return DashboardApiHealthAttentionResult.builder()
                .total(defaultLong(dashboardMapper.countApiHealthAttention(params)))
                .list(defaultList(dashboardMapper.selectApiHealthAttention(params)))
                .build();
    }

    /**
     * 构造仪表盘查询参数：非管理员写入 memberUserId 做项目成员过滤。
     */
    private DashboardParams buildParams() {
        DashboardParams.DashboardParamsBuilder builder = DashboardParams.builder();
        if (!SecurityUtils.isAdmin()) {
            builder.memberUserId(SecurityUtils.getUserId());
        }
        return builder.build();
    }

    private double computePassRate(DashboardRunStatusCount statusCount) {
        if (statusCount == null || statusCount.getFinishedCount() == null || statusCount.getFinishedCount() == 0) {
            return 0D;
        }
        long passed = statusCount.getPassedCount() == null ? 0L : statusCount.getPassedCount();
        return Math.round(passed * 10000.0 / statusCount.getFinishedCount()) / 100.0;
    }

    private List<DashboardRunTrendItem> fillRunTrend(List<DashboardRunTrendItem> rawTrend) {
        Map<String, DashboardRunTrendItem> trendMap = defaultList(rawTrend).stream()
                .collect(Collectors.toMap(DashboardRunTrendItem::getDate, item -> item, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<DashboardRunTrendItem> filled = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).format(DATE_FMT);
            DashboardRunTrendItem item = trendMap.get(date);
            if (item == null) {
                filled.add(DashboardRunTrendItem.builder()
                        .date(date)
                        .passed(0L)
                        .failed(0L)
                        .cancelled(0L)
                        .total(0L)
                        .build());
            } else {
                filled.add(item);
            }
        }
        return filled;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<T> defaultList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
