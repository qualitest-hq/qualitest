package com.qualitest.flow.diagnose;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestFlowMapper;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试流 API 语义健康字段回写。
 * <p>
 * 对测试流 graph 做静态体检后，把告警条数、告警类型摘要、检查时间写入
 * test_flow.api_health_warning_count / api_health_warning_codes / api_health_checked_at。
 * 告警为 0 时同样回写，便于列表侧按 count&gt;0 筛选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFlowHealthPersistService {

    /** api_health_warning_codes 字段最大字符数，超出截断 */
    public static final int WARNING_CODES_MAX_LEN = 256;

    private final ApiFlowReferenceScanService scanService;
    private final TestFlowMapper testFlowMapper;
    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 体检一条测试流并回写 api_health_*。
     * <p>
     * 流 id 为空、流不存在、已删除或体检异常时：打日志并返回 -1，不向外抛错。
     *
     * @param testFlowId 测试流 id
     * @return 回写后的告警条数；未成功回写时为 -1
     */
    public int refreshFlow(Long testFlowId) {
        if (testFlowId == null) {
            return -1;
        }
        try {
            ApiFlowReferenceScanService.FlowApiHealthResult health = refreshAndGet(testFlowId);
            return health.warnings == null ? 0 : health.warnings.size();
        } catch (Exception e) {
            log.warn("回写测试流 API 健康字段失败: testFlowId={}, err={}", testFlowId, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 体检一条测试流、回写 api_health_*，并返回本次体检结果。
     * <p>
     * 只扫图一次：既落库又把告警列表交给调用方。
     * 流不存在时抛业务异常（由 checkFlowHealth 抛出）。
     *
     * @param testFlowId 测试流 id
     * @return 含 warnings 的体检结果
     */
    public ApiFlowReferenceScanService.FlowApiHealthResult refreshAndGet(Long testFlowId) {
        ApiFlowReferenceScanService.FlowApiHealthResult health = scanService.checkFlowHealth(testFlowId);
        persistHealth(testFlowId, health.warnings);
        return health;
    }

    /**
     * 按单个 API id 回写：查出所属项目，再对该 API 的全部引用流做体检并落库。
     * API 不存在或无项目 id 时直接返回。
     *
     * @param testProjectApiId 项目 API id
     */
    public void refreshByApiId(Long testProjectApiId) {
        if (testProjectApiId == null) {
            return;
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(testProjectApiId);
        if (api == null || api.getTestProjectId() == null) {
            return;
        }
        refreshByApiIds(api.getTestProjectId(), List.of(testProjectApiId));
    }

    /**
     * 按测试流 id 列表逐条体检并回写。
     * 调用方已拿到流 id 时使用，避免再按 API 扫一遍图。
     *
     * @param flowIds 测试流 id 集合
     */
    public void refreshFlows(Collection<Long> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return;
        }
        for (Long flowId : flowIds) {
            refreshFlow(flowId);
        }
    }

    /**
     * 按项目 + API id 列表回写：先找出图中绑定了这些 API 的测试流，再逐条体检落库。
     * <p>
     * API 行已逻辑删除时仍可调用：扫的是流的 graph_json 绑定关系，不依赖 API 行是否存在。
     * 过程异常只打日志，不向外抛错。
     *
     * @param projectId 项目 id
     * @param apiIds    变更或删除的 API id 集合
     */
    public void refreshByApiIds(Long projectId, Collection<Long> apiIds) {
        if (projectId == null || apiIds == null || apiIds.isEmpty()) {
            return;
        }
        try {
            Set<Long> flowIds = scanService.findFlowIdsReferencingApis(projectId, apiIds);
            refreshFlows(flowIds);
            log.info("按 API 回写流健康字段: projectId={}, apis={}, flows={}",
                    projectId, apiIds.size(), flowIds.size());
        } catch (Exception e) {
            log.warn("按 API 回写流健康字段失败: projectId={}, apis={}, err={}",
                    projectId, apiIds.size(), e.getMessage(), e);
        }
    }

    /**
     * 把告警列表聚合成 count / codes / checked_at，UPDATE 到对应测试流。
     *
     * @param testFlowId 测试流 id
     * @param warnings   体检告警列表，可为 null
     * @return 告警条数
     */
    int persistHealth(Long testFlowId, List<HttpNodeApiHealthWarning> warnings) {
        int count = warnings == null ? 0 : warnings.size();
        String codes = aggregateCodes(warnings);

        TestFlow update = new TestFlow();
        update.setTestFlowId(testFlowId);
        update.setApiHealthWarningCount(count);
        update.setApiHealthCheckedAt(DateUtils.getNowDate());
        update.setApiHealthWarningCodes(codes);
        testFlowMapper.updateApiHealthFields(update);
        return count;
    }

    /**
     * 按出现顺序去重 code，逗号拼接；超过 WARNING_CODES_MAX_LEN 时截断。
     *
     * @param warnings 告警列表
     * @return 如 API_MISSING,ORPHAN_PARAM；无有效 code 时为 null
     */
    public static String aggregateCodes(List<HttpNodeApiHealthWarning> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (HttpNodeApiHealthWarning w : warnings) {
            if (w != null && w.code != null && !w.code.isBlank()) {
                seen.add(w.code.trim());
            }
        }
        if (seen.isEmpty()) {
            return null;
        }
        String joined = String.join(",", seen);
        if (joined.length() <= WARNING_CODES_MAX_LEN) {
            return joined;
        }
        return joined.substring(0, WARNING_CODES_MAX_LEN);
    }
}
