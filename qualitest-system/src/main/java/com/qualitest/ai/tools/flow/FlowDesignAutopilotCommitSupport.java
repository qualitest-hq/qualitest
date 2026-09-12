package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;

import java.util.ArrayList;
import java.util.List;

/**
 * 全自动模式下的隐式落盘逻辑（不暴露给大模型）。
 * <p>
 * 把本轮已接受的 submit_* 内存工作图校验后写入 test_flow.graph_json。
 * 调用时机：run_test_flow 跑流之前；以及 Agent 一整轮对话结束仍有未落盘单元时。
 * 半自动不走本类；模板预制流禁止落盘。
 */
public final class FlowDesignAutopilotCommitSupport {

    private FlowDesignAutopilotCommitSupport() {
    }

    /**
     * 尝试把本轮已接受单元写入测试流。
     * <ol>
     *   <li>非全自动 / 无已接受单元 → 跳过（ok=true, committed=false）</li>
     *   <li>尚有 pending 素材提案、缺 flowId、空图、校验失败、流不存在 → 失败且不改库</li>
     *   <li>updateTestFlow 成功 → 清空 SubmitCapture、推进内存工作图、触发 onGraphCommitted</li>
     * </ol>
     *
     * @param ctx                 须 autopilotEnabled=true，并带 submitCapture / workingGraph
     * @param testFlowService     写库
     * @param graphJsonValidator  全图结构校验
     * @param patchNormalizer     提供断言路径门禁所需的 API 解析器
     * @return 落盘结果；ok=false 表示未写库
     */
    public static CommitOutcome commitIfNeeded(FlowDesignToolContext ctx,
                                               ITestFlowService testFlowService,
                                               GraphJsonValidator graphJsonValidator,
                                               FlowDesignPatchNormalizer patchNormalizer) {
        if (ctx == null || !ctx.isAutopilotEnabled()) {
            return CommitOutcome.skip("非全自动，跳过隐式落盘");
        }
        if (ctx.isTemplateDesignMode()) {
            return CommitOutcome.fail("模板预制流不支持落盘", List.of("模板预制流不支持落盘"), List.of());
        }
        if (ctx.getTestFlowId() == null) {
            return CommitOutcome.fail("缺少 testFlowId", List.of("缺少 testFlowId"), List.of());
        }
        if (ctx.getAssetUpsertCapture() != null && ctx.getAssetUpsertCapture().hasPendingProposals()) {
            return CommitOutcome.fail(
                    "尚有未确认的素材库提案",
                    List.of("尚有未确认的素材库提案，请先确认后再落盘"),
                    List.of());
        }
        FlowDesignSubmitCapture capture = ctx.getSubmitCapture();
        if (capture == null || !capture.hasAccepted()) {
            return CommitOutcome.skip("本轮无已接受 submit_*，无需落盘");
        }

        GraphJson toSave = ctx.resolveGraphJson();
        if (toSave == null) {
            return CommitOutcome.fail("工作图为空", List.of("工作图为空，无法落盘"), List.of());
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        GraphValidationResult validation =
                graphJsonValidator.validate(toSave, GraphValidationOptions.full());
        if (validation.getErrors() != null) {
            errors.addAll(validation.getErrors());
        }
        if (validation.getWarnings() != null) {
            warnings.addAll(validation.getWarnings());
        }
        AssertPathDesignGate.AssertPathGateResult assertPath =
                AssertPathDesignGate.validate(toSave, patchNormalizer.apiResolver());
        if (assertPath.errors() != null) {
            errors.addAll(assertPath.errors());
        }
        if (assertPath.warnings() != null) {
            warnings.addAll(assertPath.warnings());
        }
        if (!errors.isEmpty()) {
            return CommitOutcome.fail("校验未通过，未写库", errors, warnings);
        }

        TestFlow existing = testFlowService.selectTestFlowById(ctx.getTestFlowId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            return CommitOutcome.fail("测试流不存在", List.of("测试流不存在"), warnings);
        }
        if (ctx.getTestProjectId() != null && existing.getTestProjectId() != null
                && !ctx.getTestProjectId().equals(existing.getTestProjectId())) {
            return CommitOutcome.fail("测试流不属于当前项目", List.of("测试流不属于当前项目"), warnings);
        }

        try {
            TestFlow update = new TestFlow();
            update.setTestFlowId(ctx.getTestFlowId());
            update.setGraphJson(toSave.toJsonString());
            testFlowService.updateTestFlow(update);
        } catch (ServiceException e) {
            return CommitOutcome.fail("写库失败: " + e.getMessage(),
                    List.of("写库失败: " + e.getMessage()), warnings);
        } catch (Exception e) {
            return CommitOutcome.fail("写库异常: " + e.getMessage(),
                    List.of("写库异常: " + e.getMessage()), warnings);
        }

        capture.clearAccepted();
        ctx.advanceWorkingGraph(toSave);
        ctx.notifyGraphCommitted(toSave);
        return CommitOutcome.committed(String.valueOf(ctx.getTestFlowId()), warnings);
    }

    /**
     * 隐式落盘结果。
     *
     * @param ok        true=成功或跳过；false=失败未写库
     * @param committed true=本次确实执行了 updateTestFlow
     * @param message   人类可读说明
     * @param errors    失败时的错误列表
     * @param warnings  校验警告（成功也可能带）
     */
    public record CommitOutcome(boolean ok, boolean committed, String message,
                                List<String> errors, List<String> warnings) {

        /** 无需落盘（例如半自动、或本轮没有已接受单元） */
        static CommitOutcome skip(String message) {
            return new CommitOutcome(true, false, message, List.of(), List.of());
        }

        /** 已写入 test_flow */
        static CommitOutcome committed(String testFlowId, List<String> warnings) {
            return new CommitOutcome(true, true, "已落库 testFlowId=" + testFlowId,
                    List.of(), warnings != null ? warnings : List.of());
        }

        /** 校验或写库失败，库未改 */
        static CommitOutcome fail(String message, List<String> errors, List<String> warnings) {
            return new CommitOutcome(false, false, message,
                    errors != null ? errors : List.of(),
                    warnings != null ? warnings : List.of());
        }

        /** 组装给工具回执或日志用的 JSON（含 ok / committed / message / errors / warnings） */
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
            return o;
        }
    }
}
