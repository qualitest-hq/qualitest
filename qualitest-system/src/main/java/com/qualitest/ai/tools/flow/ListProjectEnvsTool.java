package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.support.TestProjectVariableEntrySupport;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 列举当前项目下未删除的测试环境。
 * <p>
 * 每条含 id、名称、URL、共享状态、排序号，以及环境变量键名列表（不含值）。
 * 供绑场景环境、编写 {@code {{env.*}}} 或新建/改环境前勘察使用。
 * 返回前按 items 列表做字节上限裁剪（超限可先去掉 envVarKeys）。
 */
@RequiredArgsConstructor
public class ListProjectEnvsTool implements QualitestTool {

    private final ITestProjectEnvService testProjectEnvService;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_PROJECT_ENVS.getId();
    }

    /**
     * 按上下文项目 id 查询环境列表并组装安全回执。
     *
     * @param arguments 无业务参数（可空）
     * @param ctx       须含 testProjectId
     * @return {@code {items:[...]}} JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        TestProjectEnv probe = new TestProjectEnv();
        probe.setTestProjectId(ctx.getTestProjectId());
        List<TestProjectEnv> envs = testProjectEnvService.selectTestProjectEnvList(probe);
        JSONArray items = new JSONArray();
        for (TestProjectEnv env : envs) {
            if (env == null) {
                continue;
            }
            if (env.getDelStatus() != null && env.getDelStatus() != 0) {
                continue;
            }
            if (env.getTestProjectId() == null || !env.getTestProjectId().equals(ctx.getTestProjectId())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("id", String.valueOf(env.getTestProjectEnvId()));
            item.put("name", env.getEnvName());
            item.put("envUrl", env.getEnvUrl());
            item.put("shareStatus", env.getShareStatus());
            item.put("sortNum", env.getSortNum() != null ? env.getSortNum() : 0);
            item.put("envVarKeys", TestProjectVariableEntrySupport.extractVariableKeys(env.getEnvVariables()));
            items.add(item);
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        return ToolResultByteFit.fitItemsList(result, ctx.getMaxToolResultBytes());
    }
}
