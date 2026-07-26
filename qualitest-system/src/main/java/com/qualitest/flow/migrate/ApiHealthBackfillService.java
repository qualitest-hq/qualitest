package com.qualitest.flow.migrate;

import com.qualitest.flow.diagnose.ApiFlowHealthPersistService;
import com.qualitest.flow.diagnose.ApiFlowReferenceScanService;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 存量测试流 api_health_* 字段批量回填。
 * <p>
 * 遍历未删除的测试流，对每条做 API 语义体检：
 * <ul>
 *   <li>apply=false：只体检并汇总报告，不写库</li>
 *   <li>apply=true：体检后写入 api_health_warning_count / codes / checked_at</li>
 * </ul>
 * 报告里会统计有告警的流数量，并抽样列出部分告警流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiHealthBackfillService {

    private final ITestFlowService testFlowService;
    private final ApiFlowReferenceScanService scanService;
    private final ApiFlowHealthPersistService apiFlowHealthPersistService;

    /**
     * 执行回填扫描或写库。
     *
     * @param apply         true 写库；false 只出报告
     * @param testProjectId 非空时只处理该项目；null 处理全库未删除流
     * @param sampleLimit   报告中告警流抽样上限，&lt;=0 时按 20
     * @return 扫描/写库结果报告
     */
    public BackfillReport backfill(boolean apply, Long testProjectId, int sampleLimit) {
        if (sampleLimit <= 0) {
            sampleLimit = 20;
        }
        BackfillReport report = new BackfillReport();
        report.apply = apply;
        report.testProjectId = testProjectId;

        List<TestFlow> active = listActiveFlows(testProjectId);
        report.scannedFlows = active.size();

        for (TestFlow flow : active) {
            try {
                ApiFlowReferenceScanService.FlowApiHealthResult health;
                if (apply) {
                    health = apiFlowHealthPersistService.refreshAndGet(flow.getTestFlowId());
                    report.updatedFlows++;
                } else {
                    health = scanService.checkFlowHealth(flow.getTestFlowId());
                }
                int count = health.warnings == null ? 0 : health.warnings.size();
                if (count <= 0) {
                    continue;
                }
                report.warningFlows++;
                if (report.samples.size() < sampleLimit) {
                    FlowSample sample = new FlowSample();
                    sample.testFlowId = flow.getTestFlowId();
                    sample.flowName = flow.getFlowName();
                    sample.warningCount = count;
                    sample.warningCodes = ApiFlowHealthPersistService.aggregateCodes(health.warnings);
                    report.samples.add(sample);
                }
            } catch (Exception e) {
                report.errors.add(flow.getTestFlowId() + ": " + e.getMessage());
                log.warn("API健康回填失败: testFlowId={}, err={}", flow.getTestFlowId(), e.getMessage());
            }
        }

        log.info("ApiHealthBackfill apply={} projectId={} scanned={} warningFlows={} updated={} errors={}",
                apply, testProjectId, report.scannedFlows, report.warningFlows,
                report.updatedFlows, report.errors.size());
        return report;
    }

    /**
     * 查询未删除的测试流；可按项目过滤。
     */
    private List<TestFlow> listActiveFlows(Long testProjectId) {
        TestFlow query = new TestFlow();
        query.setDelStatus(0);
        if (testProjectId != null) {
            query.setTestProjectId(testProjectId);
        }
        List<TestFlow> flows = testFlowService.selectTestFlowList(query);
        return flows == null ? List.of() : flows;
    }

    /**
     * 回填任务执行结果。
     */
    public static class BackfillReport {
        /** 是否执行了写库 */
        public boolean apply;
        /** 限定的项目 id；null 表示全库 */
        public Long testProjectId;
        /** 扫描到的流条数 */
        public int scannedFlows;
        /** 体检后告警条数大于 0 的流数量 */
        public int warningFlows;
        /** apply=true 时成功回写的流条数 */
        public int updatedFlows;
        /** 单流失败信息，格式为「流id: 原因」 */
        public final List<String> errors = new ArrayList<>();
        /** 有告警的流抽样 */
        public final List<FlowSample> samples = new ArrayList<>();
    }

    /**
     * 报告中的单条告警流抽样。
     */
    public static class FlowSample {
        /** 测试流 id */
        public Long testFlowId;
        /** 测试流名称 */
        public String flowName;
        /** 告警条数 */
        public int warningCount;
        /** 告警类型摘要，逗号分隔 */
        public String warningCodes;
    }
}
