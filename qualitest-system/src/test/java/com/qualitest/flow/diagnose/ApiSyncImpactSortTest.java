package com.qualitest.flow.diagnose;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导入影响汇总中，受影响流排序与条数截断的单元测试。
 */
class ApiSyncImpactSortTest {

    /** 告警多的流排前面；告警数相同时按流名称排序 */
    @Test
    void sortAndLimitFlows_ordersByWarningThenName() {
        List<AffectedFlowSummary> flows = new ArrayList<>();
        flows.add(flow(1L, "beta", 1, 0));
        flows.add(flow(2L, "alpha", 2, 3));
        flows.add(flow(3L, "gamma", 1, 3));
        flows.add(flow(4L, "delta", 1, 1));

        List<AffectedFlowSummary> sorted = ApiFlowReferenceScanService.sortAndLimitFlows(flows);
        assertEquals(List.of(2L, 3L, 4L, 1L), sorted.stream().map(f -> f.testFlowId).toList());
    }

    /** 超过上限时只保留前 N 条，且仍按告警数优先 */
    @Test
    void sortAndLimitFlows_truncatesToLimit() {
        List<AffectedFlowSummary> flows = new ArrayList<>();
        for (int i = 0; i < ApiFlowReferenceScanService.SYNC_IMPACT_FLOW_LIMIT + 5; i++) {
            flows.add(flow((long) i, "f" + i, 1, i));
        }
        List<AffectedFlowSummary> sorted = ApiFlowReferenceScanService.sortAndLimitFlows(flows);
        assertEquals(ApiFlowReferenceScanService.SYNC_IMPACT_FLOW_LIMIT, sorted.size());
        assertEquals(ApiFlowReferenceScanService.SYNC_IMPACT_FLOW_LIMIT + 4L, sorted.get(0).testFlowId);
    }

    /** 组装一条测试用的流摘要 */
    private static AffectedFlowSummary flow(Long id, String name, int refs, int warnings) {
        AffectedFlowSummary s = new AffectedFlowSummary();
        s.testFlowId = id;
        s.flowName = name;
        s.referenceNodeCount = refs;
        s.warningCount = warnings;
        return s;
    }
}
