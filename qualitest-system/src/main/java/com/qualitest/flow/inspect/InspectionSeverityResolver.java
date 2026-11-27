package com.qualitest.flow.inspect;

import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.validate.FlowIssueCodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计算流检查的生效严重性，并把原始问题列表按项目配置重映射。
 * 优先级：解析地板码固定 ERROR → 锁定项固定 ERROR → 项目覆盖 → 目录默认 → 未知码按 WARNING。
 * 配置为 OFF 的码不会进入最终结果。
 */
@Component
public class InspectionSeverityResolver {

    /**
     * 解析单个诊断码的生效严重性。
     *
     * @param code      诊断码，用于在覆盖表中查找
     * @param def       目录定义；可为 null（未知码按 WARNING，避免漏登说明书意外硬拦）
     * @param overrides 项目覆盖表；可为 null，表示无覆盖
     */
    public InspectionConfiguredSeverity resolve(
            String code,
            FlowInspectionDef def,
            Map<String, InspectionConfiguredSeverity> overrides) {
        // 解析地板：不进流检查目录，也不允许历史 profile OFF
        if (FlowIssueCodes.GRAPH_JSON_INVALID.equals(code)) {
            return InspectionConfiguredSeverity.ERROR;
        }
        if (def != null && def.isLocked()) {
            return InspectionConfiguredSeverity.ERROR;
        }
        if (overrides != null && code != null) {
            InspectionConfiguredSeverity override = overrides.get(code);
            if (override != null) {
                return override;
            }
        }
        if (def != null && def.getDefaultSeverity() != null) {
            return def.getDefaultSeverity();
        }
        return InspectionConfiguredSeverity.WARNING;
    }

    /**
     * 按项目配置重写问题严重性，并丢弃 OFF 项。
     * 调用方保证 {@code issues}、{@code defsByCode} 非 null；缺少 code 的单条会被跳过。
     *
     * @param issues      收集器产出的原始问题（其上的 severity 可被改写）
     * @param defsByCode  诊断码到目录定义
     * @param profileJson 项目上保存的覆盖 JSON；空表示只用目录默认
     */
    public List<FlowIssue> applyProfile(
            List<FlowIssue> issues,
            Map<String, FlowInspectionDef> defsByCode,
            String profileJson) {
        if (issues.isEmpty()) {
            return List.of();
        }
        Map<String, InspectionConfiguredSeverity> overrides =
                InspectionProfileSupport.parseOverrides(profileJson);
        List<FlowIssue> out = new ArrayList<>(issues.size());
        for (FlowIssue issue : issues) {
            String code = issue.getCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            FlowInspectionDef def = defsByCode.get(code);
            InspectionConfiguredSeverity effective = resolve(code, def, overrides);
            if (effective == InspectionConfiguredSeverity.OFF) {
                continue;
            }
            FlowIssue.Severity sev = effective == InspectionConfiguredSeverity.WARNING
                    ? FlowIssue.Severity.WARNING
                    : FlowIssue.Severity.ERROR;
            out.add(FlowIssue.builder()
                    .code(code)
                    .severity(sev)
                    .stage(issue.getStage())
                    .nodeId(issue.getNodeId())
                    .message(issue.getMessage())
                    .build());
        }
        return out;
    }
}
