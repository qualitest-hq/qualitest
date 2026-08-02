package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.support.AssetVariablesListingSupport;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/** list_asset_variables：列举素材库 key/备注/子字段名（不含明文）；API 设计侧经委托复用 */
@RequiredArgsConstructor
public class ListAssetVariablesTool implements QualitestTool {

    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_ASSET_VARIABLES.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(projectId);
        JSONObject result = AssetVariablesListingSupport.buildListResult(json);
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }
}
