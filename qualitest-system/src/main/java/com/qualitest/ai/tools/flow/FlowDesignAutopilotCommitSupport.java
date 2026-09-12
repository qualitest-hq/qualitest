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
 * 全自动隐式落盘：将本轮已接受的 submit_* 工作图写入 test_flow。
 * <p>
 * 由 {@link RunTestFlowTool} 在跑流前、以及 Agent 回合结束时调用；不再作为模型可调工具。
 */
public final class FlowDesignAutopilotCommitSupport {

    private FlowDesignAutopilotCommitSupport() {
    }

    /**
     * 尝试落盘本轮已接受单元。
     *
     * @return ok=true 已写库或无需写库；ok=false 校验/写库失败（未改库）
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

    /** 隐式落盘结果 */
    public record CommitOutcome(boolean ok, boolean committed, String message,
                                List<String> errors, List<String> warnings) {

        static CommitOutcome skip(String message) {
            return new CommitOutcome(true, false, message, List.of(), List.of());
        }

        static CommitOutcome committed(String testFlowId, List<String> warnings) {
            return new CommitOutcome(true, true, "已落库 testFlowId=" + testFlowId,
                    List.of(), warnings != null ? warnings : List.of());
        }

        static CommitOutcome fail(String message, List<String> errors, List<String> warnings) {
            return new CommitOutcome(false, false, message,
                    errors != null ? errors : List.of(),
                    warnings != null ? warnings : List.of());
        }

        /** 转为工具/编排可读的 JSON */
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
