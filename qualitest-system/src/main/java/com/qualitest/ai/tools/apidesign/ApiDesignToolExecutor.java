package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.scenario.apidesign.ApiDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.flow.GetApiDetailTool;
import com.qualitest.ai.tools.flow.ListAssetVariablesTool;
import com.qualitest.ai.tools.flow.ListProjectEnvsTool;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * AI API 助手 Function Calling 调度器。
 * <p>
 * 按工具名分发到具体实现；未知工具或参数解析失败时返回错误 JSON。
 */
@Slf4j
@Component
public class ApiDesignToolExecutor {

    public static final String GET_API_DESIGN_CONTEXT = ApiDesignToolNames.GET_API_DESIGN_CONTEXT.getId();
    public static final String GET_API_DETAIL = ApiDesignToolNames.GET_API_DETAIL.getId();
    public static final String LIST_PROJECT_ENVS = ApiDesignToolNames.LIST_PROJECT_ENVS.getId();
    public static final String LIST_ASSET_VARIABLES = ApiDesignToolNames.LIST_ASSET_VARIABLES.getId();
    public static final String SUBMIT_API_DESIGN_PATCH = ApiDesignToolNames.SUBMIT_API_DESIGN_PATCH.getId();

    private final Map<String, ApiDesignTool> tools;

    public ApiDesignToolExecutor(TestProjectApiMapper testProjectApiMapper,
                                 TestProjectMapper testProjectMapper,
                                 ITestProjectEnvService testProjectEnvService,
                                 ApiDesignPatchNormalizer apiDesignPatchNormalizer) {
        Map<String, ApiDesignTool> map = new HashMap<>();
        map.put(GET_API_DESIGN_CONTEXT, new GetApiDesignContextTool(testProjectApiMapper));
        map.put(GET_API_DETAIL, new GetApiDetailForDesignTool(new GetApiDetailTool(testProjectApiMapper, testProjectMapper)));
        map.put(LIST_PROJECT_ENVS, new ListProjectEnvsForDesignTool(new ListProjectEnvsTool(testProjectEnvService)));
        map.put(LIST_ASSET_VARIABLES, new ListAssetVariablesForDesignTool(
                new ListAssetVariablesTool(testProjectMapper)));
        map.put(SUBMIT_API_DESIGN_PATCH, new SubmitApiDesignPatchTool(apiDesignPatchNormalizer));
        this.tools = Map.copyOf(map);
    }

    /** 已注册工具名 */
    public Set<String> registeredToolNames() {
        return tools.keySet();
    }

    /** 枚举声明的全部工具名 */
    public static Set<String> allDeclaredToolNames() {
        return ApiDesignToolNames.allToolIds();
    }

    /**
     * 执行指定工具。
     *
     * @param name           工具名
     * @param argumentsJson  模型传来的 JSON 参数字符串
     * @param context        本轮上下文
     */
    public String executeTool(String name, String argumentsJson, ApiDesignToolContext context) {
        ApiDesignTool tool = tools.get(name);
        if (tool == null) {
            return FlowDesignToolSupport.errorJson("未知工具: " + name);
        }
        Map<String, Object> args;
        try {
            args = FlowDesignToolSupport.parseArgs(argumentsJson);
        } catch (IllegalArgumentException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        }
        try {
            return tool.execute(args, context);
        } catch (Exception ex) {
            log.warn("API 设计工具 {} 执行异常: {}", name, ex.getMessage(), ex);
            return FlowDesignToolSupport.errorJson("工具执行异常: " + ex.getMessage());
        }
    }
}
