package com.qualitest.flow.diagnose;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导入影响汇总中，受影响流排序与条数截断的单元测试。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiSyncImpactSortTest
 */
class ApiSyncImpactSortTest {

    /**
     * 前提：多条流摘要 warningCount 与 flowName 各不相同。
     * 期望：按告警数降序、同数按名称排序，顺序为 2→3→4→1。
     */
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

    /**
     * 前提：流数量超过 SYNC_IMPACT_FLOW_LIMIT。
     * 期望：截断至上限条数，且告警数最多的流仍排首位。
     */
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
