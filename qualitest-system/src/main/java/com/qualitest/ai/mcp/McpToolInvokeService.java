package com.qualitest.ai.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.flow.FlowDesignAutopilotCommitSupport;
import com.qualitest.ai.tools.flow.TestFlowAccessSupport;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MCP 单次工具调用编排。
 * <p>
 * 默认只允许只读工具。项目开启「允许 MCP 全自动写流」后，还可调用改图 submit、
 * create_flow、素材/鉴权写入、跑流等写工具；每次 submit 校验通过后立刻把画布写入测试流库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolInvokeService {

    /** 按工具名执行具体业务 */
    private final FlowDesignToolExecutor flowDesignToolExecutor;
    /** 组装本次调用的项目/流/画布上下文 */
    private final FlowDesignToolContextFactory contextFactory;
    /** 读取项目级 MCP 全自动开关 */
    private final ITestProjectService testProjectService;
    /** 加载测试流、写回 graph_json */
    private final ITestFlowService testFlowService;
    /** 落盘前校验画布结构 */
    private final GraphJsonValidator graphJsonValidator;
    /** 落盘前规范化断言路径等设计期检查 */
    private final FlowDesignPatchNormalizer flowDesignPatchNormalizer;

    /**
     * 执行一次 MCP tools/call。
     * 先按项目开关做白名单校验；写工具补齐测试流与画布后执行；
     * submit 成功则立即写库，并把落盘结果写进回执 JSON。
     *
     * @param toolName       工具名
     * @param params         信封（项目/流/画布）与业务参数
     * @param tokenProjectId 请求头 Token 解析出的项目 id
     * @return 工具回执（含 resultJson、是否失败）
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params, Long tokenProjectId) {
        boolean mcpAutopilot = isMcpAutopilotEnabled(tokenProjectId, params);
        if (!FlowDesignToolNames.isMcpCallable(toolName, mcpAutopilot)) {
            if (FlowDesignToolNames.isMcpAutopilotWriteTool(toolName)) {
                throw new ServiceException(
                        "MCP 不支持修改测试流；请在项目设置开启「允许 MCP 全自动写流」，或改用质衡 Web 端 AI 助手");
            }
            throw new ServiceException("MCP 不支持的工具: " + toolName);
        }

        boolean writeTool = FlowDesignToolNames.isMcpAutopilotWriteTool(toolName);
        if (writeTool) {
            ensureWriteEnvelope(toolName, params, tokenProjectId);
        }

        // submit 需要本调用内的单元累积器，便于校验通过后立刻落盘
        FlowDesignSubmitCapture capture = FlowDesignToolNames.isSubmitUnitTool(toolName)
                ? new FlowDesignSubmitCapture()
                : null;
        FlowDesignToolContext context = contextFactory.fromMcpRequest(
                params, tokenProjectId, capture, writeTool);

        String resultJson = flowDesignToolExecutor.executeTool(toolName, params.toArgumentsJson(), context);

        boolean error = FlowDesignToolSupport.isErrorResult(resultJson);
        if (FlowDesignToolNames.isSubmitUnitTool(toolName) && !error) {
            CommitWrapped wrapped = commitSubmitIfNeeded(toolName, context, resultJson, tokenProjectId);
            resultJson = wrapped.resultJson();
            error = wrapped.error();
        } else if (writeTool) {
            log.info("MCP 写工具 projectId={} tool={} flowId={} error={}",
                    context.getTestProjectId(), toolName, context.getTestFlowId(), error);
        }

        return McpToolResult.builder()
                .tool(toolName)
                .resultJson(resultJson)
                .error(error)
                .build();
    }

    /**
     * 查询项目是否开启 MCP 全自动写流。
     *
     * @param tokenProjectId Token 绑定的项目 id
     * @return true 表示可列出并调用写工具
     */
    public boolean isMcpAutopilotEnabled(Long tokenProjectId) {
        return isMcpAutopilotEnabled(tokenProjectId, null);
    }

    /**
     * 解析项目 id 后读取是否允许 MCP 全自动写流。
     * 优先用 Token 绑定的项目 id，缺省时用参数里的 testProjectId。
     */
    private boolean isMcpAutopilotEnabled(Long tokenProjectId, McpToolInvokeParams params) {
        Long projectId = tokenProjectId;
        if (projectId == null && params != null) {
            projectId = params.getTestProjectId();
        }
        if (projectId == null) {
            return false;
        }
        TestProject project = testProjectService.selectTestProjectById(projectId);
        return project != null && Boolean.TRUE.equals(project.getMcpAutopilotEnabled());
    }

    /**
     * 补齐写图/跑流所需信封：必须有 testFlowId，且属于当前项目；
     * 未传 graphJson 时从库中加载当前画布。素材 upsert、接口 hint 追加、create_flow 不要求流 id。
     */
    private void ensureWriteEnvelope(String toolName, McpToolInvokeParams params, Long tokenProjectId) {
        boolean needsFlow = FlowDesignToolNames.isSubmitUnitTool(toolName)
                || FlowDesignToolNames.RUN_TEST_FLOW.getId().equals(toolName);
        if (!needsFlow) {
            return;
        }
        Long projectId = params.getTestProjectId() != null ? params.getTestProjectId() : tokenProjectId;
        if (params.getTestFlowId() == null) {
            throw new ServiceException("MCP 写工具须提供 testFlowId（可先 create_flow 或在 Web 新建空测试流）");
        }
        TestFlowAccessSupport.FlowAccess access = TestFlowAccessSupport.resolveFlowInProject(
                params.getTestFlowId(), projectId, testFlowService);
        if (!access.isOk()) {
            throw new ServiceException(access.errorMessage());
        }
        if (params.getGraphJson() == null) {
            TestFlowResult flow = access.flow();
            params.setGraphJson(TestFlowAccessSupport.parseGraphJson(flow.getGraphJson()));
        }
    }

    /**
     * submit 回执为 received=true 时，把本调用已接受的画布单元写入测试流库，
     * 并在回执中补充 committed / commitOk / hint 等字段。
     *
     * @return 更新后的回执 JSON，以及是否因落盘失败标为 error
     */
    private CommitWrapped commitSubmitIfNeeded(String toolName,
                                               FlowDesignToolContext context,
                                               String resultJson,
                                               Long tokenProjectId) {
        JSONObject result;
        try {
            result = JSON.parseObject(resultJson);
        } catch (Exception e) {
            return new CommitWrapped(resultJson, false);
        }
        if (result == null || !Boolean.TRUE.equals(result.getBoolean("received"))) {
            return new CommitWrapped(resultJson, false);
        }

        FlowDesignAutopilotCommitSupport.CommitOutcome commit =
                FlowDesignAutopilotCommitSupport.commitIfNeeded(
                        context, testFlowService, graphJsonValidator, flowDesignPatchNormalizer);
        result.put("committed", commit.committed());
        result.put("commitOk", commit.ok());
        result.put("commitMessage", commit.message());
        boolean error = false;
        if (commit.committed()) {
            result.put("hint", "单元已接受并立即写入测试流库；可继续 submit_* 或 run_test_flow");
        } else if (!commit.ok()) {
            error = true;
            result.put("error", "单元已接受但落盘失败: " + commit.message());
            result.put("hint", "请根据 commit errors 修正后再 submit_*");
            result.put("commitErrors", commit.errors());
        }
        if (commit.warnings() != null && !commit.warnings().isEmpty()) {
            result.put("commitWarnings", commit.warnings());
        }

        log.info("MCP submit 落盘 projectId={} tool={} flowId={} committed={} ok={}",
                context.getTestProjectId() != null ? context.getTestProjectId() : tokenProjectId,
                toolName,
                context.getTestFlowId(),
                commit.committed(),
                commit.ok());
        return new CommitWrapped(result.toJSONString(), error);
    }

    /**
     * submit 落盘后的回执包装。
     *
     * @param resultJson 返回给客户端的 JSON 文本
     * @param error      true 表示落盘失败，应标为工具错误
     */
    private record CommitWrapped(String resultJson, boolean error) {
    }
}
