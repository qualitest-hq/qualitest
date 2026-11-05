package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
import com.qualitest.flow.sync.FlowEditLeaseService;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;

import java.util.List;

/**
 * 全自动模式下的隐式落盘（不暴露给大模型作独立工具）。
 * <p>
 * 把本轮已接受的 submit 内存单元增量应用到库中最新图。
 * 写库只硬拦地板错误（无法解析 / 缺节点 id / 边端点等）与引用已不存在；
 * 开始节点不唯一、断言路径等只进 warnings，不拦落盘（开跑时再硬拦）。
 * 调用时机：跑流之前；Web 一整轮对话结束仍有未落盘单元时；
 * MCP 每次改图 submit 校验通过后立即落盘。
 * 半自动不走本类；模板预制流禁止落盘。
 * 落盘时持端锁，并在数据库行锁内重读最新图后只合并本轮 patch。
 */
public final class FlowDesignAutopilotCommitSupport {

    private FlowDesignAutopilotCommitSupport() {
    }

    /**
     * 尝试把本轮已接受单元写入测试流。
     * <ol>
     *   <li>非全自动 / 无已接受单元 → 跳过（ok=true, committed=false）</li>
     *   <li>尚有未确认的素材/鉴权提案、缺 flowId、落库失败 → 失败且不改库</li>
     *   <li>写库成功 → 清空 submit 捕获、推进内存工作图并通知落盘完成</li>
     *   <li>写锁冲突 → leaseConflict</li>
     * </ol>
     *
     * @param ctx             须 autopilotEnabled=true，并带 submitCapture / workingGraph
     * @param testFlowService 写库（增量合并与地板校验在 applyGraphPatch 内完成）
     * @return 落盘结果；ok=false 表示未写库
     */
    public static CommitOutcome commitIfNeeded(FlowDesignToolContext ctx,
                                               ITestFlowService testFlowService) {
        if (ctx == null || !ctx.isAutopilotEnabled()) {
            return CommitOutcome.skip("非全自动，跳过隐式落盘");
        }
        if (ctx.isTemplateDesignMode()) {
            return CommitOutcome.fail("模板预制流不支持落盘", List.of("模板预制流不支持落盘"), List.of());
        }
        if (ctx.getTestFlowId() == null) {
            return CommitOutcome.fail("缺少 testFlowId", List.of("缺少 testFlowId"), List.of());
        }
        String pendingUpsert = ctx.pendingUpsertBlockReason();
        if (pendingUpsert != null) {
            return CommitOutcome.fail(
                    pendingUpsert,
                    List.of(pendingUpsert + "，请先确认后再落盘"),
                    List.of());
        }
        FlowDesignSubmitCapture capture = ctx.getSubmitCapture();
        if (capture == null || !capture.hasAccepted()) {
            return CommitOutcome.skip("本轮无已接受 submit_*，无需落盘");
        }

        TestFlow existing = testFlowService.selectTestFlowById(ctx.getTestFlowId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            return CommitOutcome.fail("测试流不存在", List.of("测试流不存在"), List.of());
        }
        if (ctx.getTestProjectId() != null && existing.getTestProjectId() != null
                && !ctx.getTestProjectId().equals(existing.getTestProjectId())) {
            return CommitOutcome.fail("测试流不属于当前项目", List.of("测试流不属于当前项目"), List.of());
        }

        FlowDesignPatch accepted = capture.getNormalizedPatch();
        if (accepted == null) {
            return CommitOutcome.fail("已接受单元为空", List.of("已接受单元为空，无法落盘"), List.of());
        }

        // 解析本端锁标识后，把已接受 patch 增量合并进库中最新图
        String endpointId = resolveEndpointId(ctx);
        try {
            FlowExternalChangeSourceHolder.set(
                    FlowExternalChangeSourceHolder.mcpOrWebAutopilot(ctx.getAiChatSessionId() != null));
            ITestFlowService.ApplyGraphPatchResult applied = testFlowService.applyGraphPatch(
                    ctx.getTestFlowId(),
                    ctx.getTestProjectId(),
                    accepted,
                    endpointId);
            capture.clearAccepted();
            GraphJson saved = applied.graph();
            ctx.advanceWorkingGraph(saved);
            ctx.setBaseGraphRevision(applied.revision());
            ctx.notifyGraphCommitted(saved);
            return CommitOutcome.committed(String.valueOf(ctx.getTestFlowId()), applied.warnings());
        } catch (FlowEditLeaseConflictException e) {
            return CommitOutcome.leaseConflict(e, List.of());
        } catch (ServiceException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "写库失败";
            return CommitOutcome.fail(msg, List.of(msg), List.of());
        } catch (Exception e) {
            String msg = "写库异常: " + e.getMessage();
            return CommitOutcome.fail(msg, List.of(msg), List.of());
        } finally {
            FlowExternalChangeSourceHolder.clear();
        }
    }

    /**
     * 解析本次落盘的端 ID。
     * Web AI 用 web-autopilot:对话 id；否则用 MCP 无会话的 token 端标识占位，
     * 真正的 mcp:sessionId 由 MCP 层在调用前抢锁时使用。
     */
    static String resolveEndpointId(FlowDesignToolContext ctx) {
        if (ctx == null) {
            return null;
        }
        String fromCtx = ctx.getEditLeaseEndpoint();
        if (fromCtx != null && !fromCtx.isBlank()) {
            return fromCtx.trim();
        }
        if (ctx.getAiChatSessionId() != null) {
            return FlowEditLeaseService.webAutopilotEndpointId(ctx.getAiChatSessionId());
        }
        return FlowEditLeaseService.mcpEndpointId(
                null, ctx.getTestProjectId(), ctx.getOperatorUserId());
    }

    /**
     * 隐式落盘结果。
     *
     * @param ok          true=成功或跳过；false=失败未写库
     * @param committed   true=本次确实写入了测试流
     * @param message     人类可读说明
     * @param errors      失败时的错误列表
     * @param warnings    校验警告（成功也可能带）
     * @param lockHeldBy  写锁冲突时的持锁方摘要；无冲突为 null
     */
    public record CommitOutcome(boolean ok, boolean committed, String message,
                                List<String> errors, List<String> warnings, String lockHeldBy) {

        /** 无需落盘（半自动，或本轮没有已接受单元） */
        static CommitOutcome skip(String message) {
            return new CommitOutcome(true, false, message, List.of(), List.of(), null);
        }

        /** 已写入测试流 */
        static CommitOutcome committed(String testFlowId, List<String> warnings) {
            return new CommitOutcome(true, true, "已落库 testFlowId=" + testFlowId,
                    List.of(), warnings != null ? warnings : List.of(), null);
        }

        /** 校验或写库失败，库未改 */
        static CommitOutcome fail(String message, List<String> errors, List<String> warnings) {
            return new CommitOutcome(false, false, message,
                    errors != null ? errors : List.of(),
                    warnings != null ? warnings : List.of(),
                    null);
        }

        /** 写锁被他端占用，库未改 */
        static CommitOutcome leaseConflict(FlowEditLeaseConflictException e, List<String> warnings) {
            String msg = e != null ? e.getMessage() : "测试流写锁冲突";
            String held = e != null ? e.getLockHeldBy() : "unknown";
            return new CommitOutcome(false, false, msg, List.of(msg),
                    warnings != null ? warnings : List.of(), held);
        }

        /** 组装回执 JSON（ok / committed / message / errors / warnings / lockHeldBy） */
        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            o.put("committed", committed);
            o.put("message", message);
            if (!errors.isEmpty()) {
                o.put("errors", errors);
            }
            if (!warnings.isEmpty()) {
                o.put("warnings", warnings);
            }
            if (lockHeldBy != null && !lockHeldBy.isBlank()) {
                o.put("lockHeldBy", lockHeldBy);
            }
            return o;
        }
    }
}
