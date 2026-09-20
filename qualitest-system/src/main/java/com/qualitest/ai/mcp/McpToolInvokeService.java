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
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MCP 单次工具调用编排。
 * <p>
 * 默认只允许只读工具。
 * 项目开启「允许 MCP 自动写流」后，还可调用改图提交、新建流、素材/鉴权写入等写工具。
 * 项目开启「允许 MCP 自动跑流」后，还可调用 run_test_flow。
 * 项目开启「允许 MCP 导入接口」后，还可调用 import_apis 写入项目接口库。
 * 写流、跑流、导入是三道独立开关（跑流通常依赖写流已开）。
 * 写流/跑流/导入须带 Token 绑定的操作者用户 id；画布类 submit 校验通过后立刻写库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolInvokeService {

    /** 写锁冲突时写入回执的短提示文案 */
    private static final String LEASE_CONFLICT_HINT =
            "写锁仍被占用（可能是 Web 脏稿/其它标签或 MCP）；请稍后重试或换一流";

    /** 按工具名执行具体业务 */
    private final FlowDesignToolExecutor flowDesignToolExecutor;
    /** 组装本次调用的项目/流/画布上下文 */
    private final FlowDesignToolContextFactory contextFactory;
    /** 读取项目级 MCP 开关 */
    private final ITestProjectService testProjectService;
    /** 加载测试流、写回 graph_json */
    private final ITestFlowService testFlowService;
    /** 落盘前校验画布结构 */
    private final GraphJsonValidator graphJsonValidator;
    /** 落盘前规范化断言路径等设计期检查 */
    private final FlowDesignPatchNormalizer flowDesignPatchNormalizer;

    /**
     * 执行一次 MCP 工具调用（不传操作者，仅适合只读工具）。
     *
     * @param toolName       工具名
     * @param params         调用参数（含信封字段）
     * @param tokenProjectId Token 绑定的项目 id
     * @return 工具回执
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params, Long tokenProjectId) {
        return invoke(toolName, params, tokenProjectId, null);
    }

    /**
     * 执行一次 MCP 工具调用。
     * 按项目开关校验白名单；写工具补齐流与画布；写流/导入须有操作者；
     * submit 成功则立即写库；撞写锁时回执带 lockHeldBy。
     *
     * @param toolName       工具名
     * @param params         调用参数（含信封字段）
     * @param tokenProjectId Token 绑定的项目 id
     * @param operatorUserId Token 绑定的操作者用户 id；写流与导入时必填
     * @return 工具回执
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params,
                                Long tokenProjectId, Long operatorUserId) {
        try {
            return invokeInner(toolName, params, tokenProjectId, operatorUserId);
        } catch (FlowEditLeaseConflictException e) {
            return leaseConflictResult(toolName, e);
        }
    }

    /**
     * 正常工具执行：读三道开关 → 门控 → 写/跑工具校验操作者与信封 → 执行 → submit 成功则落盘。
     */
    private McpToolResult invokeInner(String toolName, McpToolInvokeParams params,
                                      Long tokenProjectId, Long operatorUserId) {
        // 同一次调用只查一次项目，同时读出写流、跑流与导入开关
        TestProject project = loadProject(tokenProjectId, params);
        McpProjectGates gates = McpProjectGates.from(project);
        if (!FlowDesignToolNames.isMcpCallable(toolName,
                gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled())) {
            if (FlowDesignToolNames.isMcpImportApisTool(toolName)) {
                throw new ServiceException(
                        "MCP 不支持导入接口；请在项目设置开启「允许 MCP 导入接口」");
            }
            if (FlowDesignToolNames.isMcpAutorunTool(toolName)) {
                throw new ServiceException(
                        "MCP 不支持自动跑流；请在项目设置开启「允许 MCP 自动跑流」");
            }
            if (FlowDesignToolNames.isMcpAutoWriteTool(toolName)) {
                throw new ServiceException(
                        "MCP 不支持修改测试流；请在项目设置开启「允许 MCP 自动写流」，或改用质衡 Web 端 AI 助手");
            }
            throw new ServiceException("MCP 不支持的工具: " + toolName);
        }

        boolean writeTool = FlowDesignToolNames.isMcpAutoWriteTool(toolName);
        boolean autorunTool = FlowDesignToolNames.isMcpAutorunTool(toolName);
        // 写流、跑流、导入都要落库审计人，须有 Token 绑定用户
        boolean needsOperator = writeTool || autorunTool || FlowDesignToolNames.isMcpImportApisTool(toolName);
        if (needsOperator && operatorUserId == null) {
            throw new ServiceException("Project Token 未绑定操作者");
        }
        if (writeTool || autorunTool) {
            ensureWriteEnvelope(toolName, params, tokenProjectId);
        }

        // submit 需要本调用内的单元累积器，便于校验通过后立刻落盘
        FlowDesignSubmitCapture capture = FlowDesignToolNames.isSubmitUnitTool(toolName)
                ? new FlowDesignSubmitCapture()
                : null;
        // 写流与跑流均注入全自动上下文（跑前可隐式落盘未提交单元）
        boolean autopilotCtx = writeTool || autorunTool;
        FlowDesignToolContext context = contextFactory.fromMcpRequest(
                params, tokenProjectId, capture, autopilotCtx, operatorUserId);

        // mcpMode：跑流/查失败使用 MCP 门面实现
        String resultJson = flowDesignToolExecutor.executeTool(
                toolName, params.toArgumentsJson(), context, true);

        boolean error = FlowDesignToolSupport.isErrorResult(resultJson);
        if (FlowDesignToolNames.isSubmitUnitTool(toolName) && !error) {
            CommitWrapped wrapped = commitSubmitIfNeeded(toolName, context, resultJson, tokenProjectId);
            resultJson = wrapped.resultJson();
            error = wrapped.error();
        } else if (writeTool || autorunTool || FlowDesignToolNames.isMcpImportApisTool(toolName)) {
            // 写流、跑流与导入接口均记一条审计日志（import_apis 不改画布，无 commit 包装）
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
     * 组装写锁冲突工具回执。
     * 含错误信息、持锁方标识与稍后重试/换一流的短提示。
     *
     * @param toolName 当前工具名
     * @param e        写锁冲突异常
     * @return 标记为错误的工具回执
     */
    private static McpToolResult leaseConflictResult(String toolName, FlowEditLeaseConflictException e) {
        JSONObject o = new JSONObject();
        o.put("error", e.getMessage());
        o.put("lockHeldBy", e.getLockHeldBy());
        o.put("hint", LEASE_CONFLICT_HINT);
        return McpToolResult.builder()
                .tool(toolName)
                .resultJson(o.toJSONString())
                .error(true)
                .build();
    }

    /**
     * 项目级 MCP 权限开关快照：是否允许自动写流、是否允许自动跑流、是否允许导入接口。
     *
     * @param autoWriteEnabled  是否允许改图、新建流等写流工具
     * @param autorunEnabled    是否允许 run_test_flow
     * @param importApisEnabled 是否允许 import_apis 写入项目接口库
     */
    public record McpProjectGates(boolean autoWriteEnabled, boolean autorunEnabled, boolean importApisEnabled) {

        /** 三道开关都关：仅只读能力 */
        public static final McpProjectGates READ_ONLY = new McpProjectGates(false, false, false);

        /**
         * 从项目实体读出三道开关；项目为空时返回只读快照。
         *
         * @param project 测试项目，可空
         * @return 门控快照
         */
        public static McpProjectGates from(TestProject project) {
            if (project == null) {
                return READ_ONLY;
            }
            return new McpProjectGates(
                    Boolean.TRUE.equals(project.getMcpAutoWriteEnabled()),
                    Boolean.TRUE.equals(project.getMcpAutorunEnabled()),
                    Boolean.TRUE.equals(project.getMcpImportApisEnabled()));
        }

        /**
         * 按项目 id 查库后读出三道开关；mapper 或 id 为空时返回只读快照。
         *
         * @param mapper        测试项目 Mapper，可空
         * @param testProjectId 测试项目 id，可空
         * @return 门控快照
         */
        public static McpProjectGates fromProjectId(TestProjectMapper mapper, Long testProjectId) {
            if (mapper == null || testProjectId == null) {
                return READ_ONLY;
            }
            return from(mapper.selectTestProjectById(testProjectId));
        }
    }

    /**
     * 按 Token 绑定的项目 id 查库，返回写流、跑流与导入三道开关快照。
     *
     * @param tokenProjectId Token 绑定的项目 id
     * @return 门控快照；项目不存在时为只读
     */
    public McpProjectGates resolveMcpGates(Long tokenProjectId) {
        return McpProjectGates.from(loadProject(tokenProjectId, null));
    }

    /**
     * 查询项目是否开启「允许 MCP 自动写流」。
     *
     * @param tokenProjectId Token 绑定的项目 id
     * @return true 表示可列出并调用写流类工具
     */
    public boolean isMcpAutoWriteEnabled(Long tokenProjectId) {
        return resolveMcpGates(tokenProjectId).autoWriteEnabled();
    }

    /**
     * 查询项目是否开启「允许 MCP 自动跑流」。
     *
     * @param tokenProjectId Token 绑定的项目 id
     * @return true 表示可列出并调用 run_test_flow
     */
    public boolean isMcpAutorunEnabled(Long tokenProjectId) {
        return resolveMcpGates(tokenProjectId).autorunEnabled();
    }

    /**
     * 查询项目是否开启「允许 MCP 导入接口」。
     *
     * @param tokenProjectId Token 绑定的项目 id
     * @return true 表示可列出并调用 import_apis
     */
    public boolean isMcpImportApisEnabled(Long tokenProjectId) {
        return resolveMcpGates(tokenProjectId).importApisEnabled();
    }

    /**
     * 解析项目实体：优先 Token 绑定的项目 id，缺省再用参数里的 testProjectId。
     */
    private TestProject loadProject(Long tokenProjectId, McpToolInvokeParams params) {
        Long projectId = tokenProjectId;
        if (projectId == null && params != null) {
            projectId = params.getTestProjectId();
        }
        if (projectId == null) {
            return null;
        }
        return testProjectService.selectTestProjectById(projectId);
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
     * submit 校验通过后立刻写库，并在回执中补充 committed / commitOk / hint。
     * 若因写锁失败，额外写入 lockHeldBy。
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
            if (commit.lockHeldBy() != null && !commit.lockHeldBy().isBlank()) {
                result.put("lockHeldBy", commit.lockHeldBy());
                result.put("hint", LEASE_CONFLICT_HINT);
            } else {
                result.put("hint", "请根据 commit errors 修正后再 submit_*");
            }
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
