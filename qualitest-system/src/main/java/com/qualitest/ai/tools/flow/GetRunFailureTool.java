package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 查询失败 Run 的全部 failed 步骤现场。
 * <p>
 * 输出 failures（含 failureCategory、errorCode、bizCheck、子流 childSteps 等），
 * 以及按类型分区的列表与计数。failureCategory：bizCode / assert / other。
 * 仅一条失败时，额外把 nodeId、stepDetails、failureCategory 提到顶层。
 * 返回前按失败现场形状做字节上限裁剪（去分区重复数组，再压缩/减少 failures）。
 */
@RequiredArgsConstructor
public class GetRunFailureTool implements QualitestTool {

    private static final String CATEGORY_BIZ = "bizCode";
    private static final String CATEGORY_ASSERT = "assert";
    private static final String CATEGORY_OTHER = "other";

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_FAILURE.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long runId = FlowDesignToolSupport.longArg(arguments.get("runId"));
        if (runId == null) {
            runId = ctx.getContextRunId();
        }
        if (runId == null) {
            return FlowDesignToolSupport.errorJson("缺少 runId");
        }
        TestFlowRunResult run = testFlowRunService.selectTestFlowRunResult(runId);
        if (run == null) {
            return FlowDesignToolSupport.errorJson("Run 不存在");
        }
        if (run.getTestFlowId() != null && ctx.getTestFlowId() != null
                && !run.getTestFlowId().equals(ctx.getTestFlowId())) {
            return FlowDesignToolSupport.errorJson("Run 不属于当前测试流");
        }
        TestFlowRunStepParams stepParams = TestFlowRunStepParams.builder()
                .testFlowRunId(runId)
                .build();
        List<TestFlowRunStepResult> steps = testFlowRunStepService.selectTestFlowRunStepResultList(stepParams);
        JSONArray failures = new JSONArray();
        JSONArray bizCodeFailures = new JSONArray();
        JSONArray assertFailures = new JSONArray();
        JSONArray otherFailures = new JSONArray();

        for (TestFlowRunStepResult step : steps) {
            if (step == null || !"failed".equals(step.getStatus())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("nodeId", step.getNodeId());
            item.put("nodeType", step.getNodeType());
            item.put("nodeName", step.getNodeName());
            item.put("stepIndex", step.getStepIndex());

            String rawDetails = step.getStepDetails();
            JSONObject details = tryParseDetails(rawDetails);
            enrichFailureDetails(item, step.getNodeType(), details);
            String category = resolveFailureCategory(step.getNodeType(), details);
            item.put("failureCategory", category);

            String detailsText = rawDetails;
            if (detailsText != null && detailsText.length() > 1024) {
                detailsText = detailsText.substring(0, 1024);
                item.put("stepDetailsTruncated", true);
            }
            item.put("stepDetails", detailsText);
            failures.add(item);
            switch (category) {
                case CATEGORY_BIZ -> bizCodeFailures.add(item);
                case CATEGORY_ASSERT -> assertFailures.add(item);
                default -> otherFailures.add(item);
            }
        }
        JSONObject result = new JSONObject();
        result.put("runId", String.valueOf(runId));
        result.put("status", run.getStatus());
        if (failures.isEmpty()) {
            result.put("failed", false);
            result.put("message", "未找到失败步骤");
            return ToolResultByteFit.fitRunFailure(result, ctx.getMaxToolResultBytes());
        }
        result.put("failed", true);
        result.put("failureCount", failures.size());
        result.put("failures", failures);
        // 分区列表，便于区分业务码失败与断言失败
        result.put("bizCodeFailures", bizCodeFailures);
        result.put("assertFailures", assertFailures);
        result.put("otherFailures", otherFailures);
        result.put("bizCodeFailureCount", bizCodeFailures.size());
        result.put("assertFailureCount", assertFailures.size());
        result.put("otherFailureCount", otherFailures.size());
        if (failures.size() == 1) {
            JSONObject first = failures.getJSONObject(0);
            result.put("nodeId", first.getString("nodeId"));
            result.put("nodeType", first.getString("nodeType"));
            result.put("nodeName", first.getString("nodeName"));
            result.put("stepIndex", first.get("stepIndex"));
            result.put("stepDetails", first.getString("stepDetails"));
            result.put("failureCategory", first.getString("failureCategory"));
        }
        return ToolResultByteFit.fitRunFailure(result, ctx.getMaxToolResultBytes());
    }

    /**
     * 从已解析的 step_details 填充 error / bizCheck / 子流字段。
     */
    private static void enrichFailureDetails(JSONObject item, String nodeType, JSONObject details) {
        if (details == null) {
            return;
        }
        JSONObject error = details.getJSONObject("error");
        if (error != null) {
            item.put("errorCode", error.getString("code"));
            item.put("errorMessage", error.getString("message"));
        }
        JSONObject http = details.getJSONObject("http");
        if (http != null) {
            JSONObject bizCheck = http.getJSONObject("bizCheck");
            if (bizCheck != null) {
                item.put("bizCheck", bizCheck);
            }
        }
        if (!"subflow".equals(nodeType)) {
            return;
        }
        JSONObject subflow = details.getJSONObject("subflow");
        if (subflow == null) {
            return;
        }
        item.put("subflowId", subflow.getString("subflowId"));
        item.put("subflowName", subflow.getString("subflowName"));
        item.put("subflowStatus", subflow.getString("status"));
        JSONArray childSteps = subflow.getJSONArray("childSteps");
        if (childSteps != null && !childSteps.isEmpty()) {
            item.put("childSteps", childSteps);
        }
    }

    /**
     * 判定失败类别：业务码 / 断言 / 其他。
     */
    private static String resolveFailureCategory(String nodeType, JSONObject details) {
        if ("assert".equals(nodeType)) {
            return CATEGORY_ASSERT;
        }
        if (details != null) {
            JSONObject error = details.getJSONObject("error");
            String code = error != null ? error.getString("code") : null;
            if ("TF_BIZ_CODE".equals(code)) {
                return CATEGORY_BIZ;
            }
            if ("TF_ASSERT_FAILED".equals(code)) {
                return CATEGORY_ASSERT;
            }
            JSONObject http = details.getJSONObject("http");
            if (http != null) {
                JSONObject bizCheck = http.getJSONObject("bizCheck");
                if (bizCheck != null && Boolean.FALSE.equals(bizCheck.getBoolean("passed"))) {
                    return CATEGORY_BIZ;
                }
            }
        }
        return CATEGORY_OTHER;
    }

    private static JSONObject tryParseDetails(String rawDetails) {
        if (rawDetails == null || rawDetails.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(rawDetails);
        } catch (Exception e) {
            return null;
        }
    }
}
