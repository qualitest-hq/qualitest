package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.flow.sync.FlowGraphCommitPatchHolder;
import com.qualitest.flow.sync.FlowGraphRevisionConflictException;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;

import java.util.ArrayList;
import java.util.List;

/**
 * 全自动模式下的隐式落盘（不暴露给大模型作独立工具）。
 * <p>
 * 把本轮已接受的 submit 内存工作图写入测试流画布。
 * 写库只硬拦地板错误（无法解析 / 缺节点 id / 边端点等）；
 * 开始节点不唯一、断言路径等只进 warnings，不拦落盘（开跑时再硬拦）。
 * 调用时机：跑流之前；Web 一整轮对话结束仍有未落盘单元时；
 * MCP 每次改图 submit 校验通过后立即落盘。
 * 半自动不走本类；模板预制流禁止落盘。
 * 写图带图版本号条件更新；冲突时重载库图、重放本轮已接受单元，最多重试若干次。
 */
public final class FlowDesignAutopilotCommitSupport {

    /** 图版本冲突时自动重放重试上限 */
    public static final int MAX_REVISION_RETRIES = 3;

    private FlowDesignAutopilotCommitSupport() {
    }

    /**
     * 尝试把本轮已接受单元写入测试流。
     * <ol>
     *   <li>非全自动 / 无已接受单元 → 跳过（ok=true, committed=false）</li>
     *   <li>尚有未确认的素材/鉴权提案、缺 flowId、空图、落库地板校验失败、流不存在 → 失败且不改库</li>
     *   <li>写库成功 → 清空 submit 捕获、推进内存工作图并通知落盘完成
     *       （完整结构/断言问题仅 warnings）</li>
     *   <li>写锁冲突 → leaseConflict；版本冲突 → 重放重试，耗尽则失败</li>
     * </ol>
     *
     * @param ctx                 须 autopilotEnabled=true，并带 submitCapture / workingGraph
     * @param testFlowService     写库
     * @param graphJsonValidator  图结构校验
     * @param patchNormalizer     提供断言路径检查与冲突重放
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

        GraphJson toSave = ctx.resolveGraphJson();
        if (toSave == null) {
            return CommitOutcome.fail("工作图为空", List.of("工作图为空，无法落盘"), List.of());
        }

        TestFlow existing = testFlowService.selectTestFlowById(ctx.getTestFlowId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            return CommitOutcome.fail("测试流不存在", List.of("测试流不存在"), List.of());
        }
        if (ctx.getTestProjectId() != null && existing.getTestProjectId() != null
                && !ctx.getTestProjectId().equals(existing.getTestProjectId())) {
            return CommitOutcome.fail("测试流不属于当前项目", List.of("测试流不属于当前项目"), List.of());
        }

        if (ctx.getBaseGraphRevision() == null) {
            ctx.setBaseGraphRevision(existing.getGraphRevision() != null ? existing.getGraphRevision() : 0L);
        }

        List<String> warnings = new ArrayList<>();
        FlowGraphRevisionConflictException lastRevisionConflict = null;

        for (int attempt = 0; attempt < MAX_REVISION_RETRIES; attempt++) {
            if (attempt > 0) {
                // 版本冲突：重载库图，重放本轮已接受单元
                TestFlow latest = testFlowService.selectTestFlowById(ctx.getTestFlowId());
                if (latest == null || (latest.getDelStatus() != null && latest.getDelStatus() != 0)) {
                    return CommitOutcome.fail("测试流不存在", List.of("测试流不存在"), warnings);
                }
                GraphJson base;
                try {
                    base = GraphJson.parse(latest.getGraphJson());
                } catch (Exception e) {
                    return CommitOutcome.fail("重载图失败: " + e.getMessage(),
                            List.of("重载图失败: " + e.getMessage()), warnings);
                }
                if (base == null) {
                    return CommitOutcome.fail("重载图为空", List.of("重载图为空，无法重放"), warnings);
                }
                FlowDesignPatch accepted = capture.getNormalizedPatch();
                FlowDesignPatchNormalizer.NormalizeResult replayed =
                        patchNormalizer.normalize(accepted, base, ctx.getTestProjectId());
                if (replayed.validation() == null || !replayed.validation().isOk()) {
                    List<String> errs = replayed.validation() != null
                            ? replayed.validation().getErrors()
                            : List.of("版本冲突重放失败");
                    return CommitOutcome.fail("版本冲突重放失败", errs, warnings);
                }
                List<String> mergeWarnings = new ArrayList<>();
                toSave = patchNormalizer.mergeOnto(base, replayed.patch(), mergeWarnings);
                addAllNonBlank(warnings, mergeWarnings);
                if (replayed.validation().getWarnings() != null) {
                    addAllNonBlank(warnings, replayed.validation().getWarnings());
                }
                long latestRev = latest.getGraphRevision() != null ? latest.getGraphRevision() : 0L;
                ctx.setBaseGraphRevision(latestRev);
            }

            List<String> errors = new ArrayList<>();
            List<String> attemptWarnings = new ArrayList<>(warnings);
            GraphValidationResult floor =
                    graphJsonValidator.validate(toSave, GraphValidationOptions.persistMinimal());
            addAllNonBlank(errors, floor.getErrors());
            addAllNonBlank(attemptWarnings, floor.getWarnings());

            GraphValidationResult full =
                    graphJsonValidator.validate(toSave, GraphValidationOptions.full());
            addSoftFindings(attemptWarnings, errors, full.getErrors());
            addSoftFindings(attemptWarnings, errors, full.getWarnings());

            AssertPathDesignGate.AssertPathGateResult assertPath =
                    AssertPathDesignGate.validate(toSave, patchNormalizer.apiResolver());
            addSoftFindings(attemptWarnings, errors, assertPath.errors());
            addSoftFindings(attemptWarnings, errors, assertPath.warnings());

            if (!errors.isEmpty()) {
                return CommitOutcome.fail("校验未通过，未写库", errors, attemptWarnings);
            }

            Long baseRevision = ctx.getBaseGraphRevision();
            if (baseRevision == null) {
                return CommitOutcome.fail("缺少 graphRevision", List.of("缺少 graphRevision，无法落盘"), attemptWarnings);
            }

            try {
                FlowExternalChangeSourceHolder.set(
                        FlowExternalChangeSourceHolder.mcpOrWebAutopilot(ctx.getAiChatSessionId() != null));
                FlowGraphCommitPatchHolder.set(
                        FlowGraphCommitPatchHolder.fromCapture(capture.getNormalizedPatch(), toSave));
                TestFlow update = new TestFlow();
                update.setTestFlowId(ctx.getTestFlowId());
                update.setTestProjectId(ctx.getTestProjectId());
                update.setGraphJson(toSave.toJsonString());
                update.setGraphRevision(baseRevision);
                testFlowService.updateTestFlow(update);
            } catch (FlowEditLeaseConflictException e) {
                return CommitOutcome.leaseConflict(e, attemptWarnings);
            } catch (FlowGraphRevisionConflictException e) {
                lastRevisionConflict = e;
                ctx.setBaseGraphRevision(e.getCurrentGraphRevision());
                continue;
            } catch (ServiceException e) {
                return CommitOutcome.fail("写库失败: " + e.getMessage(),
                        List.of("写库失败: " + e.getMessage()), attemptWarnings);
            } catch (Exception e) {
                return CommitOutcome.fail("写库异常: " + e.getMessage(),
                        List.of("写库异常: " + e.getMessage()), attemptWarnings);
            } finally {
                FlowExternalChangeSourceHolder.clear();
                FlowGraphCommitPatchHolder.clear();
            }

            capture.clearAccepted();
            ctx.advanceWorkingGraph(toSave);
            ctx.setBaseGraphRevision(baseRevision + 1);
            ctx.notifyGraphCommitted(toSave);
            return CommitOutcome.committed(String.valueOf(ctx.getTestFlowId()), attemptWarnings);
        }

        String msg = lastRevisionConflict != null
                ? lastRevisionConflict.getMessage() + "，重试已耗尽"
                : "图版本冲突，重试已耗尽";
        return CommitOutcome.fail(msg, List.of(msg), warnings);
    }

    /** 把非空文案追加到目标列表。 */
    private static void addAllNonBlank(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String item : source) {
            if (item != null && !item.isBlank()) {
                target.add(item);
            }
        }
    }

    /**
     * 把完整校验 / 断言发现写入 warnings：跳过空白，以及已在 errors/warnings 中的文案。
     */
    private static void addSoftFindings(List<String> warnings, List<String> hardErrors, List<String> findings) {
        if (findings == null || findings.isEmpty()) {
            return;
        }
        for (String item : findings) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (hardErrors.contains(item) || warnings.contains(item)) {
                continue;
            }
            warnings.add(item);
        }
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
