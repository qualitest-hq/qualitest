package com.qualitest.flow.inspect;

import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 校验链路末尾：读取项目流检查配置，对原始问题定级并过滤 OFF。
 */
@Service
@RequiredArgsConstructor
public class FlowIssueFinalizeService {

    private final FlowInspectionCatalogService catalogService;
    private final InspectionSeverityResolver severityResolver;
    private final TestProjectMapper testProjectMapper;

    /**
     * 应用项目流检查配置后返回最终问题列表。
     *
     * @param raw           各收集器合并后的原始问题；null 按空列表处理
     * @param testProjectId 项目 id；为空或项目不存在时不使用覆盖，仅按目录默认定级
     */
    public List<FlowIssue> finalizeIssues(List<FlowIssue> raw, Long testProjectId) {
        List<FlowIssue> issues = raw == null ? List.of() : raw;
        Map<String, FlowInspectionDef> defs = catalogService.defsByCode();
        String profileJson = null;
        if (testProjectId != null) {
            TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
            if (project != null) {
                profileJson = project.getInspectionProfile();
            }
        }
        return severityResolver.applyProfile(issues, Objects.requireNonNull(defs), profileJson);
    }
}
