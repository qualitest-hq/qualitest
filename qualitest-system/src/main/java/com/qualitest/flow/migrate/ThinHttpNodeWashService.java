package com.qualitest.flow.migrate;

import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次性把库里测试流 graph_json 中的厚 HTTP 节点洗成薄节点。
 * <p>
 * <b>执行前必须备份 test_flow 表。</b>
 * apply=false（默认）只扫描并出报告，不写库；apply=true 才更新 graph_json。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinHttpNodeWashService {

    private final ITestFlowService testFlowService;
    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 扫描（并可选写回）测试流图数据。
     *
     * @param apply         false=只报告；true=写回 graph_json
     * @param testProjectId 只处理该项目；null 表示全库未删除的流
     * @param sampleLimit   报告里最多带多少条变更抽样，默认 20
     */
    @Transactional(rollbackFor = Exception.class)
    public WashReport wash(boolean apply, Long testProjectId, int sampleLimit) {
        if (sampleLimit <= 0) {
            sampleLimit = 20;
        }
        WashReport report = new WashReport();
        report.apply = apply;
        report.testProjectId = testProjectId;

        TestFlow query = new TestFlow();
        query.setDelStatus(0);
        if (testProjectId != null) {
            query.setTestProjectId(testProjectId);
        }
        List<TestFlow> flows = testFlowService.selectTestFlowList(query);
        report.scannedFlows = flows != null ? flows.size() : 0;

        Map<Long, TestProjectApi> apiCache = new HashMap<>();
        if (flows == null || flows.isEmpty()) {
            return report;
        }

        String operator = safeUsername();
        for (TestFlow flow : flows) {
            HttpNodeThinTransform.TransformResult tr = HttpNodeThinTransform.transformGraphJson(
                    flow.getGraphJson(),
                    id -> apiCache.computeIfAbsent(id, testProjectApiMapper::selectTestProjectApiById));
            if (tr.parseError != null) {
                report.parseErrors.add(flow.getTestFlowId() + ": " + tr.parseError);
                continue;
            }
            if (!tr.changed) {
                continue;
            }
            report.changedFlows++;
            report.changedNodes += tr.nodeChanges.size();
            if (report.samples.size() < sampleLimit) {
                FlowSample sample = new FlowSample();
                sample.testFlowId = flow.getTestFlowId();
                sample.flowName = flow.getFlowName();
                sample.nodeChangeCount = tr.nodeChanges.size();
                sample.nodeChanges = tr.nodeChanges;
                report.samples.add(sample);
            }
            if (apply) {
                TestFlow update = new TestFlow();
                update.setTestFlowId(flow.getTestFlowId());
                update.setGraphJson(tr.transformedJson);
                update.setUpdateBy(operator);
                testFlowService.updateTestFlow(update);
                report.updatedFlows++;
            }
        }
        log.info("ThinHttpNodeWash apply={} projectId={} scanned={} changedFlows={} changedNodes={} updated={}",
                apply, testProjectId, report.scannedFlows, report.changedFlows, report.changedNodes, report.updatedFlows);
        return report;
    }

    private static String safeUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "thin-http-wash";
        }
    }

    /** 洗库执行报告。 */
    public static class WashReport {
        /** 是否真正写库 */
        public boolean apply;
        public Long testProjectId;
        /** 扫描到的流数量 */
        public int scannedFlows;
        /** 至少改过一个节点的流数量 */
        public int changedFlows;
        /** 变更节点总数 */
        public int changedNodes;
        /** 实际 UPDATE 成功的流数量（仅 apply=true） */
        public int updatedFlows;
        public final List<String> parseErrors = new ArrayList<>();
        public final List<FlowSample> samples = new ArrayList<>();
    }

    /** 报告中的单条流抽样。 */
    public static class FlowSample {
        public Long testFlowId;
        public String flowName;
        public int nodeChangeCount;
        public List<HttpNodeThinTransform.NodeChange> nodeChanges;
    }
}
