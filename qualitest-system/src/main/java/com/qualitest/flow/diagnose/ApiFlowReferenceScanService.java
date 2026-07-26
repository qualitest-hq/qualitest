package com.qualitest.flow.diagnose;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试流 HTTP 节点与项目 API 的反向查找，以及节点相对 API 的语义健康检查。
 * <p>
 * 库表没有按 API id 的索引，因此通过遍历各测试流的 graph_json，
 * 找出绑定了指定 API 的 HTTP 节点，再按需做告警检测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFlowReferenceScanService {

    /** 导入影响汇总中，返回的测试流条数上限 */
    static final int SYNC_IMPACT_FLOW_LIMIT = 50;

    /** 每条受影响流上保留的告警文案样例条数上限 */
    static final int SYNC_IMPACT_SAMPLE_WARNINGS = 3;

    private final ITestFlowService testFlowService;
    private final TestProjectApiMapper testProjectApiMapper;
    private final HttpNodeApiHealthChecker healthChecker;

    /**
     * 列出项目内所有绑定了该 API 的测试流 HTTP 节点。
     * 只返回引用关系，不做语义健康检查。
     */
    public List<ApiFlowReferenceItem> listReferences(Long testProjectApiId) {
        return scanProjectForApi(testProjectApiId, false).references;
    }

    /**
     * 列出绑定了该 API 的测试流 HTTP 节点，并对每个命中节点做语义健康检查。
     * 引用收集与健康检查在同一次图遍历中完成。
     */
    public DiagnoseImpactsResult diagnoseImpacts(Long testProjectApiId) {
        return scanProjectForApi(testProjectApiId, true);
    }

    /**
     * 按库中已保存的 graph_json 检查该测试流全部「项目接口」HTTP 节点的语义健康。
     * <p>
     * 告警类型：API 缺失、测值覆盖参数在接口中已不存在、抽取路径在响应结构中找不到。
     * 本方法只返回结果，是否写回 api_health_* 由调用方决定。
     */
    public FlowApiHealthResult checkFlowHealth(Long testFlowId) {
        TestFlow flow = requireActiveFlow(testFlowId);
        return checkGraphHealth(flow, flow.getGraphJson());
    }

    /**
     * 按 testFlowId 加载流元数据，再对调用方传入的 graphJson 做语义体检。
     * <p>
     * 用途：画布未保存时，用页面上的草稿图做预检。
     * 不读取库中的图内容，也不回写 api_health_*。
     */
    public FlowApiHealthResult checkGraphHealth(Long testFlowId, String graphJson) {
        return checkGraphHealth(requireActiveFlow(testFlowId), graphJson);
    }

    /**
     * 对指定测试流对象与给定 graphJson 做语义体检。
     * <p>
     * flow 仅用于填充返回体中的流 id / 项目 id / 流名称，以及确认流未删除；
     * 实际检查的节点与绑定来自 graphJson。
     * 不写库。
     */
    public FlowApiHealthResult checkGraphHealth(TestFlow flow, String graphJson) {
        if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            throw new ServiceException("测试流不存在");
        }
        FlowApiHealthResult result = new FlowApiHealthResult();
        result.testFlowId = flow.getTestFlowId();
        result.testProjectId = flow.getTestProjectId();
        result.flowName = flow.getFlowName();

        Map<Long, TestProjectApi> apiCache = new HashMap<>();
        List<HttpNodeApiHealthWarning> warnings = healthChecker.checkGraph(
                graphJson,
                id -> apiCache.computeIfAbsent(id, this::loadActiveApi));
        for (HttpNodeApiHealthWarning w : warnings) {
            w.testFlowId = flow.getTestFlowId();
            w.flowName = flow.getFlowName();
        }
        result.warnings = warnings;
        return result;
    }

    /**
     * 按 id 加载未删除的测试流；不存在或已删除则抛业务异常。
     */
    private TestFlow requireActiveFlow(Long testFlowId) {
        TestFlow flow = testFlowService.selectTestFlowById(testFlowId);
        if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            throw new ServiceException("测试流不存在");
        }
        return flow;
    }

    /**
     * 按一批 API id，收集项目内图中绑定了这些 API 的测试流 id。
     * 结果去重、不截断；不校验 API 行是否仍存在（已删除的 API 也能靠图上的绑定 id 找到流）。
     *
     * @param projectId 项目 id
     * @param apiIds    API id 集合
     * @return 引用了任一目标 API 的测试流 id 集合
     */
    public Set<Long> findFlowIdsReferencingApis(Long projectId, Collection<Long> apiIds) {
        Set<Long> flowIds = new LinkedHashSet<>();
        Set<Long> targets = normalizeApiIds(apiIds);
        if (projectId == null || targets.isEmpty()) {
            return flowIds;
        }
        visitBoundHttpNodes(projectId, targets, (flow, nodeId, data, boundId, nodeName) ->
                flowIds.add(flow.getTestFlowId()));
        return flowIds;
    }

    /**
     * 按一批变更过的 API id，扫描项目内测试流并汇总影响。
     * <p>
     * 只统计绑定落在这批 API 上的 HTTP 节点；对命中节点做语义健康检查。
     * 结果写入 syncImpact 返回体；同时填充 allAffectedFlowIds（完整流 id，供回写 api_health_*）。
     * projectId 为空或变更 id 集合为空时返回空汇总。
     */
    public ApiSyncImpactSummary diagnoseSyncImpact(Long projectId, Collection<Long> changedApiIds) {
        ApiSyncImpactSummary summary = new ApiSyncImpactSummary();
        Set<Long> targets = normalizeApiIds(changedApiIds);
        if (projectId == null || targets.isEmpty()) {
            return summary;
        }
        summary.changedApiCount = targets.size();

        long started = System.currentTimeMillis();
        Map<Long, AffectedFlowSummary> byFlow = new LinkedHashMap<>();
        Map<Long, TestProjectApi> apiCache = new HashMap<>();

        // 按流聚合：引用节点数、告警数、告警样例文案
        visitBoundHttpNodes(projectId, targets, (flow, nodeId, data, boundId, nodeName) -> {
            AffectedFlowSummary flowSummary = byFlow.computeIfAbsent(flow.getTestFlowId(), id -> {
                AffectedFlowSummary s = new AffectedFlowSummary();
                s.testFlowId = flow.getTestFlowId();
                s.flowName = flow.getFlowName();
                return s;
            });
            flowSummary.referenceNodeCount++;

            TestProjectApi boundApi = apiCache.computeIfAbsent(boundId, this::loadActiveApi);
            for (HttpNodeApiHealthWarning w : healthChecker.checkNode(
                    nodeId, nodeName, data, boundId, boundApi)) {
                flowSummary.warningCount++;
                if (flowSummary.sampleWarnings.size() < SYNC_IMPACT_SAMPLE_WARNINGS
                        && w.message != null && !w.message.isBlank()) {
                    flowSummary.sampleWarnings.add(w.message);
                }
            }
        });

        for (AffectedFlowSummary s : byFlow.values()) {
            summary.warningCount += s.warningCount;
        }
        summary.affectedFlowCount = byFlow.size();
        // 完整流 id 集合，供导入后回写 api_health_*（flows 列表仍按上限截断）
        summary.allAffectedFlowIds = new LinkedHashSet<>(byFlow.keySet());
        summary.flows = sortAndLimitFlows(new ArrayList<>(byFlow.values()));

        log.info("API同步影响扫描: projectId={}, changedApis={}, flows={}, warnings={}, costMs={}",
                projectId, targets.size(), summary.affectedFlowCount, summary.warningCount,
                System.currentTimeMillis() - started);
        return summary;
    }

    /**
     * 对受影响流列表排序并截断。
     * 排序：告警数从多到少，同告警数按流名称、再按流 id；超过上限只保留前若干条。
     */
    static List<AffectedFlowSummary> sortAndLimitFlows(List<AffectedFlowSummary> flows) {
        flows.sort(Comparator
                .comparingInt((AffectedFlowSummary f) -> f.warningCount).reversed()
                .thenComparing(f -> f.flowName == null ? "" : f.flowName)
                .thenComparing(f -> f.testFlowId == null ? 0L : f.testFlowId));
        if (flows.size() <= SYNC_IMPACT_FLOW_LIMIT) {
            return flows;
        }
        return new ArrayList<>(flows.subList(0, SYNC_IMPACT_FLOW_LIMIT));
    }

    /**
     * 扫描单个 API 在所属项目下的引用。
     *
     * @param withHealth true 时对每个命中节点做语义健康检查并写入 warnings；false 时只收集引用
     */
    private DiagnoseImpactsResult scanProjectForApi(Long testProjectApiId, boolean withHealth) {
        TestProjectApi api = requireApi(testProjectApiId);

        DiagnoseImpactsResult result = new DiagnoseImpactsResult();
        result.testProjectApiId = testProjectApiId;
        result.testProjectId = api.getTestProjectId();
        result.references = new ArrayList<>();
        result.warnings = new ArrayList<>();

        Map<Long, TestProjectApi> apiCache = new HashMap<>();
        apiCache.put(testProjectApiId, api);

        visitBoundHttpNodes(api.getTestProjectId(), Set.of(testProjectApiId),
                (flow, nodeId, data, boundId, nodeName) -> {
                    ApiFlowReferenceItem item = new ApiFlowReferenceItem();
                    item.testFlowId = flow.getTestFlowId();
                    item.flowName = flow.getFlowName();
                    item.nodeId = nodeId;
                    item.nodeName = nodeName;
                    item.callMode = data.getString("callMode");
                    item.testProjectApiId = testProjectApiId;
                    result.references.add(item);

                    if (!withHealth) {
                        return;
                    }
                    TestProjectApi boundApi = apiCache.computeIfAbsent(boundId, this::loadActiveApi);
                    for (HttpNodeApiHealthWarning w : healthChecker.checkNode(
                            nodeId, nodeName, data, boundId, boundApi)) {
                        w.testFlowId = flow.getTestFlowId();
                        w.flowName = flow.getFlowName();
                        result.warnings.add(w);
                    }
                });
        return result;
    }

    /**
     * 遍历项目内未删除的测试流，对绑定落在指定 API id 集合中的 HTTP 节点逐个回调。
     * 非项目接口节点、未绑定或绑定不在集合内的节点会跳过。
     */
    private void visitBoundHttpNodes(Long projectId, Set<Long> apiIds, BoundHttpNodeConsumer consumer) {
        List<TestFlow> flows = listProjectFlows(projectId);
        if (flows.isEmpty() || apiIds == null || apiIds.isEmpty()) {
            return;
        }
        for (TestFlow flow : flows) {
            FlowHttpNodeVisitor.visit(flow.getGraphJson(), (nodeId, node, data) -> {
                if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
                    return;
                }
                Long boundId = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
                if (boundId == null || !apiIds.contains(boundId)) {
                    return;
                }
                consumer.accept(flow, nodeId, data, boundId,
                        FlowHttpNodeVisitor.resolveNodeName(data, nodeId));
            });
        }
    }

    /**
     * 查询项目下未删除的测试流列表；无数据时返回空列表。
     */
    private List<TestFlow> listProjectFlows(Long projectId) {
        TestFlow query = new TestFlow();
        query.setDelStatus(0);
        query.setTestProjectId(projectId);
        List<TestFlow> flows = testFlowService.selectTestFlowList(query);
        return flows == null ? List.of() : flows;
    }

    /**
     * 去掉空 id，得到去重后的 API id 集合。
     */
    private static Set<Long> normalizeApiIds(Collection<Long> apiIds) {
        Set<Long> targets = new HashSet<>();
        if (apiIds == null) {
            return targets;
        }
        for (Long id : apiIds) {
            if (id != null) {
                targets.add(id);
            }
        }
        return targets;
    }

    /**
     * 按 id 加载未删除的项目 API。
     * 查无或 del_status!=0 时返回 null，健康检查会据此产生 API_MISSING 告警。
     */
    private TestProjectApi loadActiveApi(Long id) {
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(id);
        if (api == null || (api.getDelStatus() != null && api.getDelStatus() != 0)) {
            return null;
        }
        return api;
    }

    /**
     * 按 id 加载未删除的项目 API；id 为空或不存在时抛业务异常。
     */
    private TestProjectApi requireApi(Long testProjectApiId) {
        if (testProjectApiId == null) {
            throw new ServiceException("testProjectApiId 不能为空");
        }
        TestProjectApi api = loadActiveApi(testProjectApiId);
        if (api == null) {
            throw new ServiceException("API 不存在或已删除");
        }
        return api;
    }

    /**
     * 命中「绑定到目标 API 的 HTTP 节点」时的回调。
     */
    @FunctionalInterface
    private interface BoundHttpNodeConsumer {

        /**
         * @param flow       所在测试流
         * @param nodeId     画布节点 id
         * @param data       节点 data
         * @param boundApiId 节点绑定的项目 API id
         * @param nodeName   节点展示名
         */
        void accept(TestFlow flow, String nodeId, JSONObject data, Long boundApiId, String nodeName);
    }

    /**
     * 按单个 API 诊断时的返回体：引用列表，以及可选的语义告警列表。
     */
    public static class DiagnoseImpactsResult {
        /** 被诊断的 API id */
        public Long testProjectApiId;
        /** 该 API 所属项目 id */
        public Long testProjectId;
        /** 引用该 API 的流与节点 */
        public List<ApiFlowReferenceItem> references;
        /** 命中节点上的语义告警；未做健康检查时为空列表 */
        public List<HttpNodeApiHealthWarning> warnings;
    }

    /**
     * 单条测试流健康检查的返回体。
     */
    public static class FlowApiHealthResult {
        /** 测试流 id */
        public Long testFlowId;
        /** 所属项目 id */
        public Long testProjectId;
        /** 测试流名称 */
        public String flowName;
        /** 该流上全部项目接口 HTTP 节点的语义告警 */
        public List<HttpNodeApiHealthWarning> warnings;
    }
}
