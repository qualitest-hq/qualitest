package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.http.HttpRetryUntilSupport;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.report.StepDetailsJson;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 查询某次测试流 Run 的过程报告。
 * <p>
 * 覆盖通过与失败步骤。未传 nodeId 时返回全流时间线摘要（每步一行文字摘要，不含请求体）；
 * 传入 nodeId 时只返回该节点匹配步骤的明细（断言规则、脚本日志、HTTP 请求响应摘要等）。
 */
@RequiredArgsConstructor
public class GetRunReportTool implements QualitestTool {

    /** 时间线摘要中最多预览的脚本日志行数 */
    private static final int TIMELINE_LOG_PREVIEW = 2;
    /** 明细模式中脚本日志最多保留行数 */
    private static final int DETAIL_LOG_MAX = 40;
    /** 明细模式中请求/响应 body 最大字符数 */
    private static final int DETAIL_BODY_MAX = 256;
    /** 明细模式中断言实际值最大字符数 */
    private static final int DETAIL_ACTUAL_MAX = 120;

    /** Run 头信息查询 */
    private final ITestFlowRunService testFlowRunService;
    /** Run 步骤列表查询 */
    private final ITestFlowRunStepService testFlowRunStepService;

    /**
     * 工具名：get_run_report。
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_RUN_REPORT.getId();
    }

    /**
     * 组装过程报告 JSON。
     * <p>
     * 入参：runId（可省略时用上下文运行 ID）、可选 nodeId、可选 includeBody（明细是否带截断后的 body）。
     * 出参：runId、status、mode（timeline|detail）、stepCount、steps；超限时由体积裁剪处理。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return 报告 JSON 或错误 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        RunToolSupport.LoadedRun loaded = RunToolSupport.loadRun(
                arguments, ctx, testFlowRunService,
                "缺少 runId：请传入 runId，或在带 Run 上下文的调用中使用");
        if (loaded.failed()) {
            return loaded.errorJson();
        }

        String nodeIdFilter = FlowDesignToolSupport.stringArg(arguments.get("nodeId"));
        boolean includeBody = FlowDesignToolSupport.booleanArg(arguments.get("includeBody"));
        boolean detailMode = !nodeIdFilter.isEmpty();

        List<TestFlowRunStepResult> allSteps = testFlowRunStepService.selectTestFlowRunStepResultList(
                TestFlowRunStepParams.builder().testFlowRunId(loaded.runId()).build());

        JSONArray stepsOut = new JSONArray();
        for (TestFlowRunStepResult step : allSteps) {
            if (step == null) {
                continue;
            }
            if (detailMode && !nodeIdFilter.equals(step.getNodeId())) {
                continue;
            }
            JSONObject details = StepDetailsJson.parse(step.getStepDetails());
            stepsOut.add(detailMode
                    ? buildDetailStep(step, details, includeBody)
                    : buildTimelineStep(step, details));
        }

        JSONObject result = new JSONObject();
        result.put("runId", String.valueOf(loaded.runId()));
        result.put("status", loaded.run().getStatus());
        result.put("mode", detailMode ? "detail" : "timeline");
        result.put("stepCount", stepsOut.size());
        if (detailMode) {
            result.put("nodeId", nodeIdFilter);
            if (stepsOut.isEmpty()) {
                result.put("message", "未找到匹配 nodeId 的步骤");
            }
        }
        result.put("steps", stepsOut);
        return ToolResultByteFit.fitRunReport(result, ctx.getMaxToolResultBytes());
    }

    /**
     * 组装时间线一步：公共字段 + 一行 summary 文字。
     */
    private static JSONObject buildTimelineStep(TestFlowRunStepResult step, JSONObject details) {
        JSONObject item = baseStepFields(step);
        item.put("summary", buildSummaryLine(step.getNodeType(), details, step));
        return item;
    }

    /**
     * 组装明细一步：公共字段 + 错误信息 + HTTP / 断言 / 脚本块（按 step_details 里实际存在的键写入）。
     *
     * @param includeBody 为 true 时请求/响应带截断后的 body，否则标 bodyOmitted
     */
    private static JSONObject buildDetailStep(
            TestFlowRunStepResult step, JSONObject details, boolean includeBody) {
        JSONObject item = baseStepFields(step);
        if (details == null) {
            return item;
        }
        JSONObject error = details.getJSONObject("error");
        if (error != null) {
            item.put("error", error);
        }
        putHttpDetail(item, details.getJSONObject("http"), includeBody);
        putAssertDetail(item, details.getJSONObject("assert"));
        putScriptDetail(item, details.getJSONObject("script"));
        return item;
    }

    /**
     * 写入步骤公共索引字段：序号、节点 id/类型/名称、状态、耗时。
     */
    private static JSONObject baseStepFields(TestFlowRunStepResult step) {
        JSONObject item = new JSONObject();
        item.put("stepIndex", step.getStepIndex());
        item.put("nodeId", step.getNodeId());
        item.put("nodeType", step.getNodeType());
        item.put("nodeName", step.getNodeName());
        item.put("status", step.getStatus());
        item.put("durationMs", step.getDurationMs());
        return item;
    }

    /**
     * 按节点类型生成时间线一行摘要文字。
     * HTTP 写 method/url/status/业务码；断言写通过条数；脚本写日志行数与短预览；其余写错误或类型名。
     */
    private static String buildSummaryLine(
            String nodeType, JSONObject details, TestFlowRunStepResult step) {
        if (details == null) {
            return step.getStatus() != null ? step.getStatus() : "";
        }
        if ("http".equals(nodeType)) {
            return summarizeHttp(details.getJSONObject("http"));
        }
        if ("assert".equals(nodeType)) {
            int[] counts = countAssertRules(details.getJSONObject("assert"));
            return "断言通过 " + counts[0] + "/" + counts[1];
        }
        if ("script".equals(nodeType)) {
            return summarizeScript(details.getJSONObject("script"));
        }
        JSONObject error = details.getJSONObject("error");
        if (error != null && error.getString("message") != null) {
            return truncate(error.getString("code") + ": " + error.getString("message"), 100);
        }
        return nodeType != null ? nodeType : "";
    }

    /**
     * HTTP 步骤摘要：METHOD url → status，附业务码通过/失败；
     * 若实际请求超过 1 次，再附「第 N/M 次」。
     */
    private static String summarizeHttp(JSONObject http) {
        if (http == null) {
            return "http";
        }
        StringBuilder sb = new StringBuilder();
        String method = http.getString("method");
        if (method != null) {
            sb.append(method).append(' ');
        }
        String url = http.getString("url");
        if (url != null) {
            sb.append(truncate(url, 80));
        }
        Object status = http.get("status");
        if (status != null) {
            sb.append(" → ").append(status);
        }
        JSONObject biz = http.getJSONObject("bizCheck");
        if (biz != null) {
            sb.append(Boolean.TRUE.equals(biz.getBoolean("passed")) ? " · 业务码通过" : " · 业务码失败");
        }
        JSONObject retry = http.getJSONObject("retry");
        if (retry != null) {
            sb.append(HttpRetryUntilSupport.formatAttemptSuffix(
                    retry.get("attempts"), retry.get("maxAttempts")));
        }
        return sb.toString();
    }

    /**
     * 脚本步骤摘要：日志总行数，并预览前几行短文本。
     */
    private static String summarizeScript(JSONObject script) {
        if (script == null) {
            return "script";
        }
        JSONArray logs = script.getJSONArray("logs");
        int logCount = logs != null ? logs.size() : 0;
        StringBuilder sb = new StringBuilder("script · logs=").append(logCount);
        if (logs != null && !logs.isEmpty()) {
            sb.append(" · ");
            int n = Math.min(TIMELINE_LOG_PREVIEW, logs.size());
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(truncate(String.valueOf(logs.get(i)), 40));
            }
        }
        return sb.toString();
    }

    /**
     * 写入 HTTP 明细：method/url/status、业务码校验、重试摘要、通过条件求值、
     * 请求与响应摘要、前置/后置脚本 logs 与 tests。
     */
    private static void putHttpDetail(JSONObject item, JSONObject http, boolean includeBody) {
        if (http == null) {
            return;
        }
        JSONObject out = new JSONObject();
        out.put("method", http.getString("method"));
        out.put("url", http.getString("url"));
        out.put("status", http.get("status"));
        if (http.get("bizCheck") != null) {
            out.put("bizCheck", http.get("bizCheck"));
        }
        if (http.get("retry") != null) {
            out.put("retry", http.get("retry"));
        }
        if (http.get("until") != null) {
            out.put("until", http.get("until"));
        }
        out.put("request", summarizeHttpPart(http.getJSONObject("request"), includeBody));
        out.put("response", summarizeHttpPart(http.getJSONObject("response"), includeBody));
        putNestedScript(out, "preScript", http.getJSONObject("preScript"));
        putNestedScript(out, "postScript", http.getJSONObject("postScript"));
        item.put("http", out);
    }

    /**
     * 压缩单侧请求或响应：保留 method/url/status/headers；body 按 includeBody 决定写入或标省略。
     */
    private static JSONObject summarizeHttpPart(JSONObject part, boolean includeBody) {
        if (part == null) {
            return null;
        }
        JSONObject out = new JSONObject();
        copyIfPresent(part, out, "method");
        copyIfPresent(part, out, "url");
        if (part.get("status") != null) {
            out.put("status", part.get("status"));
        }
        if (part.get("headers") != null) {
            out.put("headers", part.get("headers"));
        }
        Object body = part.get("body");
        if (body != null) {
            if (includeBody) {
                out.put("body", truncateValue(body, DETAIL_BODY_MAX));
            } else {
                out.put("bodyOmitted", true);
            }
        }
        return out;
    }

    /**
     * 若源对象含非空字符串字段则拷到目标。
     */
    private static void copyIfPresent(JSONObject from, JSONObject to, String key) {
        String value = from.getString(key);
        if (value != null) {
            to.put(key, value);
        }
    }

    /**
     * 写入 HTTP 嵌套的前置或后置脚本摘要（仅 logs、tests；有内容才落键）。
     */
    private static void putNestedScript(JSONObject httpOut, String key, JSONObject script) {
        if (script == null) {
            return;
        }
        JSONObject out = new JSONObject();
        JSONArray logs = script.getJSONArray("logs");
        if (logs != null && !logs.isEmpty()) {
            out.put("logs", trimLogs(logs, DETAIL_LOG_MAX));
        }
        JSONArray tests = script.getJSONArray("tests");
        if (tests != null && !tests.isEmpty()) {
            out.put("tests", tests);
        }
        if (!out.isEmpty()) {
            httpOut.put(key, out);
        }
    }

    /**
     * 写入断言明细：通过/失败条数，以及每条 left/operator/right/passed/leftActual（实际值截断）。
     */
    private static void putAssertDetail(JSONObject item, JSONObject assertObj) {
        if (assertObj == null) {
            return;
        }
        JSONObject out = new JSONObject();
        int[] counts = countAssertRules(assertObj);
        out.put("passedCount", counts[0]);
        out.put("failedCount", counts[1] - counts[0]);
        JSONArray rules = assertObj.getJSONArray("rules");
        if (rules != null) {
            JSONArray slim = new JSONArray();
            for (int i = 0; i < rules.size(); i++) {
                JSONObject r = rules.getJSONObject(i);
                if (r == null) {
                    continue;
                }
                JSONObject row = new JSONObject();
                row.put("left", r.get("left"));
                row.put("operator", r.get("operator"));
                row.put("right", r.get("right"));
                row.put("passed", r.get("passed"));
                if (r.containsKey("leftActual")) {
                    row.put("leftActual", truncateValue(r.get("leftActual"), DETAIL_ACTUAL_MAX));
                }
                slim.add(row);
            }
            out.put("rules", slim);
        }
        item.put("assert", out);
    }

    /**
     * 写入脚本节点明细：语言、截断后的 logs、原始 logCount、写入过的 flow 键名列表。
     */
    private static void putScriptDetail(JSONObject item, JSONObject script) {
        if (script == null) {
            return;
        }
        JSONObject out = new JSONObject();
        if (script.getString("language") != null) {
            out.put("language", script.getString("language"));
        }
        JSONArray logs = script.getJSONArray("logs");
        if (logs != null) {
            out.put("logs", trimLogs(logs, DETAIL_LOG_MAX));
            out.put("logCount", logs.size());
        }
        JSONArray writes = script.getJSONArray("writes");
        if (writes != null && !writes.isEmpty()) {
            JSONArray keys = new JSONArray();
            for (int i = 0; i < writes.size(); i++) {
                JSONObject w = writes.getJSONObject(i);
                if (w != null && w.getString("key") != null) {
                    keys.add(w.getString("key"));
                }
            }
            out.put("writeKeys", keys);
        }
        item.put("script", out);
    }

    /**
     * 统计断言规则通过数与总数。
     *
     * @return 长度为 2 的数组：[通过数, 总条数]
     */
    private static int[] countAssertRules(JSONObject assertObj) {
        if (assertObj == null) {
            return new int[]{0, 0};
        }
        JSONArray rules = assertObj.getJSONArray("rules");
        if (rules == null || rules.isEmpty()) {
            return new int[]{0, 0};
        }
        int passed = 0;
        for (int i = 0; i < rules.size(); i++) {
            JSONObject r = rules.getJSONObject(i);
            if (r != null && Boolean.TRUE.equals(r.getBoolean("passed"))) {
                passed++;
            }
        }
        return new int[]{passed, rules.size()};
    }

    /**
     * 截取日志数组前 max 行，每行再截短；超出部分追加一条剩余行数提示。
     */
    private static JSONArray trimLogs(JSONArray logs, int max) {
        JSONArray out = new JSONArray();
        int n = Math.min(max, logs.size());
        for (int i = 0; i < n; i++) {
            out.add(truncate(String.valueOf(logs.get(i)), 200));
        }
        if (logs.size() > max) {
            out.add("… +" + (logs.size() - max) + " more");
        }
        return out;
    }

    /**
     * 任意值转字符串后按最大长度截断。
     */
    private static Object truncateValue(Object value, int max) {
        if (value == null) {
            return null;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        return truncate(s, max);
    }

    /**
     * 字符串超长时截断并加省略号。
     */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
