package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.report.RunFailureCategoryResolver;
import com.qualitest.project.report.StepDetailsJson;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 查询失败运行的全部失败步骤现场。
 * <p>
 * 输出 failures（节点信息、错误码、业务码校验、子流子步骤等），
 * 并按业务码失败 / 断言失败 / 其他失败分区计数。
 * 仅有一条失败时，把节点与类别等字段额外提到结果顶层，便于直接阅读。
 * 返回前按体积上限裁剪字段，避免工具结果过大。
 */
@RequiredArgsConstructor
public class GetRunFailureTool implements QualitestTool {

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_FAILURE.getId();
    }

    /**
     * 查询指定失败运行的全部失败步骤现场。
     * runId 优先取参数；未传时使用请求上下文中的运行 ID。
     * 两者皆空则返回缺少 runId 的错误。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return 失败现场 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long runId = FlowDesignToolSupport.longArg(arguments.get("runId"));
        if (runId == null) {
            runId = ctx.getContextRunId();
        }
        if (runId == null) {
            return FlowDesignToolSupport.errorJson(
                    "缺少 runId：请传入 runId，或在带失败 Run 上下文的调用中使用");
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
            JSONObject details = StepDetailsJson.parse(rawDetails);
            enrichFailureDetails(item, step.getNodeType(), details);
            String category = RunFailureCategoryResolver.resolve(step.getNodeType(), details);
            item.put("failureCategory", category);

            String detailsText = rawDetails;
            if (detailsText != null && detailsText.length() > 1024) {
                detailsText = detailsText.substring(0, 1024);
                item.put("stepDetailsTruncated", true);
            }
            item.put("stepDetails", detailsText);
            failures.add(item);
            switch (category) {
                case RunFailureCategoryResolver.BIZ_CODE -> bizCodeFailures.add(item);
                case RunFailureCategoryResolver.ASSERT -> assertFailures.add(item);
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
        // 按失败类别放入分区列表
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
     * 从已解析的步骤详情填充错误码、错误信息、业务码校验结果，以及子流相关字段。
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
}
