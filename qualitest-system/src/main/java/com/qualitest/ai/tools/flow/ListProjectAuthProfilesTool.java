package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.ai.tools.support.AuthProfileUpsertSupport;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * AI 造流只读工具：列举当前项目鉴权 Profile。
 * <p>
 * 回执含 id、名称、pathPrefix、托管头、credentialTarget、credentialApi；不含密钥明文。
 * 模板画布模式无项目鉴权，直接返回错误。
 */
@RequiredArgsConstructor
public class ListProjectAuthProfilesTool implements QualitestTool {

    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_PROJECT_AUTH_PROFILES.getId();
    }

    /**
     * 读取项目 auth_config，组装 Profile 摘要列表并按字节上限裁剪。
     *
     * @param arguments 无必填参数
     * @param ctx       须含 testProjectId；模板模式拒绝
     * @return JSON 字符串（items / truncated 或 error）
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布无项目鉴权 Profile");
        }
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        var project = testProjectMapper.selectTestProjectById(projectId);
        if (project == null) {
            return FlowDesignToolSupport.errorJson("项目不存在");
        }
        JSONObject result = AuthProfileUpsertSupport.buildListResult(project.getAuthConfig());
        return ToolResultByteFit.fitItemsList(result, ctx.getMaxToolResultBytes());
    }
}
