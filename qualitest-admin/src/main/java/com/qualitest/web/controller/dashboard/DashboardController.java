package com.qualitest.web.controller.dashboard;

import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.dashboard.result.DashboardApiHealthAttentionResult;
import com.qualitest.dashboard.result.DashboardSummaryResult;
import com.qualitest.dashboard.service.IDashboardService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页仪表盘
 */
@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class DashboardController extends BaseController {

    private final IDashboardService dashboardService;

    /**
     * 首页汇总数据（项目/流/API/运行计数、通过率、最近项、趋势等）
     */
    @GetMapping("/summary")
    public R<DashboardSummaryResult> summary() {
        return ok(dashboardService.getSummary());
    }

    /**
     * 接口变更待关注：当前用户可见项目中，api_health_warning_count &gt; 0 的测试流。
     * 返回总数与最多 20 条摘要（流名、项目名、告警数、类型、检查时间）。
     */
    @GetMapping("/apiHealthAttention")
    public R<DashboardApiHealthAttentionResult> apiHealthAttention() {
        return ok(dashboardService.getApiHealthAttention());
    }
}
