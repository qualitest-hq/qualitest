package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestFlowMapper;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 正式运行前的就绪检查。
 * <p>
 * 依次检查：完整图结构、断言/条件路径结构错误、鉴权凭证来源、登录抽取、HTTP 必填测值。
 * 任一项失败则不应创建 Run；保存图时不跑本门禁。
 */
@Component
@RequiredArgsConstructor
public class FlowRunReadinessGate {

    private final GraphJsonValidator graphJsonValidator;
    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;
    private final TestFlowMapper testFlowMapper;

    /**
     * 收集阻止开跑的全部错误文案。
     *
     * @param graph         已解析的图
     * @param testProjectId 所属测试项目，用于读鉴权配置
     * @return 错误列表；空表示可以开跑
     */
    public List<String> collectBlockingErrors(GraphJson graph, Long testProjectId) {
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            errors.add("根对象必须是 JSON 对象");
            return errors;
        }
        GraphValidationResult structure = graphJsonValidator.validate(graph, GraphValidationOptions.full());
        errors.addAll(structure.getErrors());

        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper::selectTestProjectApiById;
        errors.addAll(AssertPathDesignGate.validate(graph, apiResolver).errors());

        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        Function<Long, GraphJson> subflowResolver = id -> {
            TestFlow sub = testFlowMapper.selectTestFlowById(id);
            if (sub == null || StrUtil.isBlank(sub.getGraphJson())) {
                return null;
            }
            try {
                return GraphJson.parse(sub.getGraphJson());
            } catch (Exception e) {
                return null;
            }
        };
        errors.addAll(DesignAuthRiskGates.collect(graph, projectAuthJson, apiResolver, subflowResolver));
        return errors;
    }

    /** 读取项目 auth_config；无项目或项目不存在时返回 null */
    private String loadProjectAuthConfig(Long testProjectId) {
        if (testProjectId == null) {
            return null;
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        return project != null ? project.getAuthConfig() : null;
    }
}
