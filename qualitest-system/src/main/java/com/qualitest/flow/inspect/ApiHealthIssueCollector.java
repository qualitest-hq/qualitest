package com.qualitest.flow.inspect;

import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.flow.diagnose.HttpNodeApiHealthWarning;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 把 HTTP 节点 API 语义健康检查结果转成统一的流问题，并入画布 / 开跑校验。
 * 产出默认按 ERROR 写入；最终是否硬拦由项目流检查配置决定。
 */
@Component
@RequiredArgsConstructor
public class ApiHealthIssueCollector {

    private final HttpNodeApiHealthChecker apiHealthChecker;
    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 对图 JSON 跑 API 语义检查并投影为问题列表。
     *
     * @param graphJson 图文本；空白时返回空列表
     * @param stage     问题阶段；为空时按画布阶段
     */
    public List<FlowIssue> collect(String graphJson, FlowIssue.Stage stage) {
        if (graphJson == null || graphJson.isBlank()) {
            return List.of();
        }
        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        List<HttpNodeApiHealthWarning> warnings = apiHealthChecker.checkGraph(graphJson, apiResolver);
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        List<FlowIssue> issues = new ArrayList<>(warnings.size());
        for (HttpNodeApiHealthWarning w : warnings) {
            if (w == null || w.code == null || w.code.isBlank()) {
                continue;
            }
            String message = w.message != null && !w.message.isBlank() ? w.message : w.code;
            issues.add(FlowIssue.error(w.code.trim(), effective, w.nodeId, message));
        }
        return issues;
    }
}
