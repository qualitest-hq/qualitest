package com.qualitest.ai.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.protocol.McpSession;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.flow.FlowDesignAutopilotCommitSupport;
import com.qualitest.ai.tools.flow.ReleaseFlowLockTool;
import com.qualitest.ai.tools.flow.TestFlowAccessSupport;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
import com.qualitest.flow.sync.FlowEditLeaseService;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 单次工具调用编排。
 * <p>
 * 默认只允许只读工具（须具备 mcp:read）。
 * 写流 / 跑流 / 导入由成员角色权限 mcp:flow:write、mcp:run、mcp:api:import 决定。
 * 写流/跑流/导入须带 Token 绑定的操作者用户 id。
 * 逐次调用会话里，画布类 submit 校验通过后立刻写库。
 * 回合会话在同一会话内累积单元与工作图，submit 回执保持 received，跑流前再写入测试流。
 * 写流/跑流工具在加载画布前先取得或续期本端写锁；同流后续调用（含只读）续期锁；
 * 成功回执可附带 lockExpiresInSec；冲突时回执带 hint 与 lockHeldBy。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolInvokeService {

    /** 写锁被他端占用时，写入工具回执 hint 字段的说明文案 */
    private static final String LEASE_CONFLICT_HINT =
            "写锁被其他端占用；请等待对方释放（MCP 空闲约 60 秒自动过期，或调用 release_flow_lock），"
                    + "或等 Web 保存/放弃草稿后再试，或换一流";

    /** 按工具名执行具体业务 */
    private final FlowDesignToolExecutor flowDesignToolExecutor;
    /** 组装本次调用的项目/流/画布上下文 */
    private final FlowDesignToolContextFactory contextFactory;
    /** 读取项目实体（流归属等） */
    private final ITestProjectService testProjectService;
    /** 按角色权限解析 MCP 门控 */
    private final McpProjectGateResolver mcpProjectGateResolver;
    /** 加载测试流、写回 graph_json */
    private final ITestFlowService testFlowService;
    /** 测试流端锁 */
    private final FlowEditLeaseService flowEditLeaseService;

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
     * 按成员角色 MCP 权限校验工具白名单；写工具补齐流与画布；写流/导入须有操作者；
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
        return invoke(toolName, params, tokenProjectId, operatorUserId, null);
    }

    /**
     * 执行一次 MCP 工具调用。
     * 回合会话复用同一份单元累积器与工作图，submit 不在本次调用写库。
     *
     * @param toolName       工具名
     * @param params         调用参数（含信封字段）
     * @param tokenProjectId Token 绑定的项目 id
     * @param operatorUserId Token 绑定的操作者用户 id；写流与导入时必填
     * @param session        当前会话；逐次调用或无会话时为 null
     * @return 工具回执
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params,
                                Long tokenProjectId, Long operatorUserId, McpSession session) {
        try {
            return invokeInner(toolName, params, tokenProjectId, operatorUserId, session);
        } catch (FlowEditLeaseConflictException e) {
            return leaseConflictResult(toolName, e);
        }
    }

    /**
     * 正常工具执行：解析角色 MCP 门控 → 校验工具白名单 → 写/跑工具校验操作者与信封 → 执行。
     * 逐次调用的 submit 成功则立刻写库；回合会话只记住工作图。
     */
    private McpToolResult invokeInner(String toolName, McpToolInvokeParams params,
                                      Long tokenProjectId, Long operatorUserId, McpSession session) {
        loadProject(tokenProjectId, params);
        McpProjectGates gates = resolveMcpGates(tokenProjectId, operatorUserId);
        boolean turn = session != null && session.isTurn();
        if (turn) {
            // 回合会话只开放画布设计工具。列流、建流、导入接口、规程版本直接拒绝。
            if (!FlowDesignToolNames.isTurnSessionCallable(
                    toolName, gates.autoWriteEnabled(), gates.autorunEnabled())) {
                if (!FlowDesignToolNames.isWebAgent(toolName)) {
                    throw new ServiceException("回合会话不提供工具: " + toolName);
                }
                rejectClosedTool(toolName, gates);
            }
            bindTurnEnvelope(session, params);
        } else if (!FlowDesignToolNames.isMcpCallable(toolName,
                gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled())) {
            rejectClosedTool(toolName, gates);
        } else if (!gates.readEnabled() && !gates.autoWriteEnabled() && !gates.importApisEnabled()
                && !gates.autorunEnabled()) {
            throw new ServiceException("当前成员角色无权使用 MCP 工具");
        }

        // release_flow_lock 不参与写前抢锁，走独立释放分支
        boolean releaseLockTool = FlowDesignToolNames.RELEASE_FLOW_LOCK.getId().equals(toolName);
        boolean writeTool = FlowDesignToolNames.isMcpAutoWriteTool(toolName);
        boolean autorunTool = FlowDesignToolNames.isMcpAutorunTool(toolName);
        // 写流、跑流、导入都要落库审计人，须有 Token 绑定用户
        boolean needsOperator = writeTool || autorunTool || FlowDesignToolNames.isMcpImportApisTool(toolName);
        if (needsOperator && operatorUserId == null) {
            throw new ServiceException("Project Token 未绑定操作者");
        }

        String endpointId = FlowEditLeaseService.mcpEndpointId(
                session != null ? session.getId() : null, tokenProjectId, operatorUserId);

        // 先加锁再读图：写/跑工具在加载画布前取得或续期本端会话锁（含回合会话）
        if ((writeTool || autorunTool) && !releaseLockTool) {
            Long leaseFlowId = params.getTestFlowId();
            if (leaseFlowId == null && turn) {
                leaseFlowId = session.getTestFlowId();
            }
            if (leaseFlowId != null) {
                flowEditLeaseService.acquireOrRenewEndpoint(
                        leaseFlowId, endpointId, FlowEditLeaseService.MCP_TTL_SECONDS);
                if (session != null) {
                    session.noteHeldFlow(leaseFlowId);
                }
            }
        }
        if (writeTool || autorunTool) {
            ensureWriteEnvelope(toolName, params, tokenProjectId);
        }

        // 该流上任意工具调用（含读）续期本端已持有的锁
        Long renewFlowId = params.getTestFlowId();
        if (renewFlowId == null && session != null) {
            renewFlowId = session.getTestFlowId();
        }
        if (renewFlowId != null) {
            flowEditLeaseService.renewIfHeldBy(
                    renewFlowId, endpointId, FlowEditLeaseService.MCP_TTL_SECONDS);
        }

        if (!turn && releaseLockTool) {
            return invokeReleaseFlowLock(params, tokenProjectId, operatorUserId, session, endpointId);
        }

        // 回合会话全程复用同一累积器，这样 run_test_flow 能看到前面收下的单元。
        // 逐次调用只在 submit 时新建累积器，校验通过后立刻写库并清空。
        FlowDesignSubmitCapture capture = turn
                ? session.getCapture()
                : (FlowDesignToolNames.isSubmitUnitTool(toolName) ? new FlowDesignSubmitCapture() : null);
        // 回合会话按可跑流的设计上下文执行：run_test_flow 在开跑前把已接受单元写入测试流
        boolean autopilotCtx = turn || writeTool || autorunTool;
        FlowDesignToolContext context = contextFactory.fromMcpRequest(
                params, tokenProjectId, capture, autopilotCtx, operatorUserId);
        context.setEditLeaseEndpoint(endpointId);

        // 最后一个参数为 false 时走画布执行：submit 回 received，不在本次调用写库。
        // 为 true 时走逐次调用门面：跑流和查失败按 MCP 回执，submit 随后立刻写库。
        String resultJson = flowDesignToolExecutor.executeTool(
                toolName, params.toArgumentsJson(), context, !turn);

        rememberTurnGraph(session, turn, context);

        boolean error = FlowDesignToolSupport.isErrorResult(resultJson);
        // 回合会话不在这里写库，也不包 committed；落盘发生在画布跑流之前
        if (!turn && FlowDesignToolNames.isSubmitUnitTool(toolName) && !error) {
            CommitWrapped wrapped = commitSubmitIfNeeded(toolName, context, resultJson, tokenProjectId);
            resultJson = wrapped.resultJson();
            error = wrapped.error();
        } else if (writeTool || autorunTool || FlowDesignToolNames.isMcpImportApisTool(toolName)) {
            // 写流、跑流与导入接口均记一条审计日志（import_apis 不改画布，无 commit 包装）
            log.info("MCP 写工具 projectId={} tool={} flowId={} error={}",
                    context.getTestProjectId(), toolName, context.getTestFlowId(), error);
        }

        resultJson = appendLockExpiresHint(resultJson, renewFlowId, endpointId);

        return McpToolResult.builder()
                .tool(toolName)
                .resultJson(resultJson)
                .error(error)
                .build();
    }

    /** 执行 release_flow_lock，并更新会话持锁集合。 */
    private McpToolResult invokeReleaseFlowLock(McpToolInvokeParams params,
                                                Long tokenProjectId,
                                                Long operatorUserId,
                                                McpSession session,
                                                String endpointId) {
        Long flowId = params != null ? params.getTestFlowId() : null;
        Map<String, Object> args = new HashMap<>();
        if (flowId != null) {
            args.put("testFlowId", String.valueOf(flowId));
        }
        FlowDesignToolContext ctx = contextFactory.fromMcpRequest(
                params, tokenProjectId, null, true, operatorUserId);
        ctx.setEditLeaseEndpoint(endpointId);
        String resultJson = new ReleaseFlowLockTool(flowEditLeaseService, endpointId)
                .execute(args, ctx);
        boolean error = FlowDesignToolSupport.isErrorResult(resultJson);
        if (!error && session != null && flowId != null) {
            try {
                JSONObject o = JSON.parseObject(resultJson);
                if (o != null && Boolean.TRUE.equals(o.getBoolean("released"))) {
                    session.clearHeldFlow(flowId);
                }
            } catch (Exception ignored) {
                // 回执解析失败不影响释放结果
            }
        }
        return McpToolResult.builder()
                .tool(FlowDesignToolNames.RELEASE_FLOW_LOCK.getId())
                .resultJson(resultJson)
                .error(error)
                .build();
    }

    /** 在成功回执上附带本端锁剩余秒数，便于模型决定何时 release_flow_lock。 */
    private String appendLockExpiresHint(String resultJson, Long flowId, String endpointId) {
        if (flowId == null || endpointId == null || endpointId.isBlank()) {
            return resultJson;
        }
        if (!flowEditLeaseService.holds(flowId, endpointId)) {
            return resultJson;
        }
        long ttl = flowEditLeaseService.remainingTtlSeconds(flowId);
        if (ttl < 0) {
            return resultJson;
        }
        try {
            JSONObject result = JSON.parseObject(resultJson);
            if (result == null || result.containsKey("error")) {
                return resultJson;
            }
            result.put("lockExpiresInSec", ttl);
            return result.toJSONString();
        } catch (Exception e) {
            return resultJson;
        }
    }

    /**
     * 工具因成员角色缺 MCP 权限而不可调用时抛出对应说明。
     * 逐次调用和回合会话共用这些说明：导入、跑流、写流各自提示缺少的权限。
     * 回合会话里不属于画布的工具不走这里，而是直接说明当前会话不提供该工具。
     */
    private static void rejectClosedTool(String toolName, McpProjectGates gates) {
        if (FlowDesignToolNames.isMcpImportApisTool(toolName)) {
            throw new ServiceException("当前成员角色无权通过 MCP 导入接口");
        }
        if (FlowDesignToolNames.isMcpAutorunTool(toolName)) {
            if (!gates.autoWriteEnabled()) {
                throw new ServiceException(
                        "MCP 跑流须同时具备写流权限；请为角色勾选 mcp:flow:write 与 mcp:run，或改用 Web 端");
            }
            throw new ServiceException("当前成员角色无权通过 MCP 跑流");
        }
        if (FlowDesignToolNames.isMcpAutoWriteTool(toolName)) {
            throw new ServiceException("当前成员角色无权通过 MCP 写流，或改用 Web 端 AI 助手");
        }
        throw new ServiceException("MCP 不支持的工具: " + toolName);
    }

    /**
     * 用回合会话上的测试流和工作图补齐本次调用。
     * 参数没带测试流时填会话绑定的流。
     * 会话里已经有工作图时，用它覆盖本次参数里的图和版本，使后面的 submit、查图、跑流都接着本轮累积，而不是回到库里的旧图。
     */
    private static void bindTurnEnvelope(McpSession session, McpToolInvokeParams params) {
        if (params.getTestFlowId() == null) {
            params.setTestFlowId(session.getTestFlowId());
        }
        if (session.getGraph() != null) {
            params.setGraphJson(session.getGraph());
            if (session.getRevision() != null) {
                params.setGraphRevision(session.getRevision());
            }
        }
    }

    /**
     * 回合会话在工具返回后，把上下文里的工作图和版本留在会话上。
     * 下一次调用会把这张图带回去。逐次调用不保留。
     */
    private static void rememberTurnGraph(McpSession session, boolean turn, FlowDesignToolContext context) {
        if (!turn || context == null) {
            return;
        }
        if (context.resolveGraphJson() != null) {
            session.setGraph(context.resolveGraphJson());
        }
        if (context.getBaseGraphRevision() != null) {
            session.setRevision(context.getBaseGraphRevision());
        }
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
     * 按 Token 绑定项目推导 MCP 能力；未传操作者时返回无能力。
     *
     * @param tokenProjectId 项目 id
     * @return MCP 门控快照
     */
    public McpProjectGates resolveMcpGates(Long tokenProjectId) {
        return resolveMcpGates(tokenProjectId, null);
    }

    /**
     * 按项目与操作者角色权限推导 MCP 能力。
     *
     * @param tokenProjectId 项目 id
     * @param operatorUserId Token 绑定用户；空则无能力
     * @return 门控快照
     */
    public McpProjectGates resolveMcpGates(Long tokenProjectId, Long operatorUserId) {
        return mcpProjectGateResolver.resolve(tokenProjectId, operatorUserId);
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
            if (params.getGraphRevision() == null) {
                params.setGraphRevision(flow.getGraphRevision() != null ? flow.getGraphRevision() : 0L);
            }
        } else if (params.getGraphRevision() == null) {
            TestFlowResult flow = access.flow();
            params.setGraphRevision(flow.getGraphRevision() != null ? flow.getGraphRevision() : 0L);
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
                FlowDesignAutopilotCommitSupport.commitIfNeeded(context, testFlowService);
        result.put("committed", commit.committed());
        result.put("commitOk", commit.ok());
        result.put("commitMessage", commit.message());
        boolean error = false;
        if (commit.committed()) {
            result.put("hint",
                    "单元已接受并立即写入测试流库；可继续 submit_* 或 run_test_flow；"
                            + "改完后请 release_flow_lock");
            Long flowId = context.getTestFlowId();
            String endpoint = context.getEditLeaseEndpoint();
            if (flowId != null && endpoint != null && flowEditLeaseService.holds(flowId, endpoint)) {
                long ttl = flowEditLeaseService.remainingTtlSeconds(flowId);
                if (ttl >= 0) {
                    result.put("lockExpiresInSec", ttl);
                }
            }
        } else if (!commit.ok()) {
            error = true;
            result.put("error", "单元已接受但落盘失败: " + commit.message());
            if (commit.lockHeldBy() != null && !commit.lockHeldBy().isBlank()) {
                result.put("lockHeldBy", commit.lockHeldBy());
                result.put("hint", LEASE_CONFLICT_HINT);
            } else if (commit.message() != null && commit.message().contains("已不存在")) {
                result.put("hint", "引用的节点或边已不存在（可能被其他端删除）；请重新勘察画布后再 submit_*");
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
