package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.ai.tools.support.AssetVariablesListingSupport;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 列举当前项目素材库：各条目的 key、备注、子字段名与占位提示，不含字段明文。
 * <p>
 * 返回前按 items 列表形状做字节上限裁剪。
 */
@RequiredArgsConstructor
public class ListAssetVariablesTool implements QualitestTool {

    /** 读取项目 asset_variables 列 */
    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_ASSET_VARIABLES.getId();
    }

    /**
     * 读取项目素材库 JSON，组装为仅含元数据的 items 列表。
     *
     * @param arguments 无业务入参
     * @param ctx       须带 testProjectId
     * @return JSON：items、truncated；失败时含 error
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(projectId);
        JSONObject result = AssetVariablesListingSupport.buildListResult(json);
        return ToolResultByteFit.fitItemsList(result, ctx.getMaxToolResultBytes());
    }
}
