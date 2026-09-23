package com.qualitest.project.report;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.http.HttpStepDetailsDesensitizer;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将一次测试流运行渲染为单文件简易 HTML 报告。
 * <p>
 * 报告含页眉结论、失败摘要（可跳转、挂关联变量）、业务步骤总览表、节点结构化卡片与页脚；
 * 场景加载 / 快照 / 还原 / 恢复决策等审计步不进入步骤表与卡片。
 * 失败步卡片默认展开；通过步默认收起。
 */
public final class RunHtmlReportRenderer {

    /** 不写入步骤表与卡片的审计节点类型 */
    private static final Set<String> AUDIT_NODE_TYPES = Set.of(
            StepResultWriter.NODE_TYPE_RUN_CONFIG,
            StepResultWriter.NODE_TYPE_SNAPSHOT,
            StepResultWriter.NODE_TYPE_RESTORE,
            StepResultWriter.NODE_TYPE_RESUME_DECISION
    );

    /** 失败步响应体截断长度 */
    private static final int BODY_SNIPPET_MAX = 400;

    /** 通过步响应体不展示；脚本日志最多展示条数 */
    private static final int SCRIPT_LOG_MAX = 20;

    private RunHtmlReportRenderer() {
    }

    /**
     * 渲染完整 HTML 报告字符串。
     *
     * @param detail      运行详情（头信息 + 步骤）
     * @param projectName 项目名称，可为 null
     * @param flowName    测试流名称，可为 null
     * @return 可直接下载的 HTML 全文
     */
    public static String render(TestFlowRunDetailResult detail, String projectName, String flowName) {
        TestFlowRunResult run = detail != null ? detail.getRun() : null;
        if (run == null) {
            throw new IllegalArgumentException("run 不能为空");
        }
        List<TestFlowRunStepResult> allSteps = detail.getSteps() != null ? detail.getSteps() : List.of();
        MetaFromSteps meta = extractMeta(allSteps, run);
        List<StepView> views = buildStepViews(allSteps);
        List<StepView> failures = views.stream().filter(s -> "failed".equals(s.status)).toList();

        StringBuilder sb = new StringBuilder(16384);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\"/>\n");
        sb.append("<title>").append(esc(titleOf(projectName, flowName, run))).append("</title>\n");
        sb.append("<style>\n").append(css()).append("\n</style>\n</head>\n<body>\n");

        // 已有失败步骤时不在页眉再贴运行级错误，避免同一错误显示两遍
        appendHeader(sb, run, projectName, flowName, meta, failures.isEmpty());
        if (!failures.isEmpty()) {
            appendFailureSummary(sb, failures);
        }
        appendStepTable(sb, views);
        appendNodeCards(sb, views);
        appendFooter(sb, run);

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * 渲染报告并生成建议下载文件名。
     *
     * @param detail      运行详情
     * @param projectName 项目名称，可为 null
     * @param flowName    测试流名称，可为 null
     * @return HTML 与文件名
     */
    public static RunHtmlReportFile toReportFile(TestFlowRunDetailResult detail, String projectName, String flowName) {
        String html = render(detail, projectName, flowName);
        TestFlowRunResult run = detail.getRun();
        List<TestFlowRunStepResult> allSteps = detail.getSteps() != null ? detail.getSteps() : List.of();
        MetaFromSteps meta = extractMeta(allSteps, run);
        String fileName = buildDownloadFileName(run, projectName, flowName, meta.scenarioName);
        return new RunHtmlReportFile(html, fileName);
    }

    /**
     * 组装下载文件名：项目_流名_场景_状态_时间_运行ID.html。
     * 缺省段自动省略；非法文件名字符替换为下划线。
     */
    static String buildDownloadFileName(TestFlowRunResult run, String projectName, String flowName,
                                        String scenarioName) {
        StringBuilder name = new StringBuilder();
        appendFilePart(name, projectName);
        appendFilePart(name, flowName != null && !flowName.isBlank() ? flowName : "测试流");
        appendFilePart(name, scenarioName);
        appendFilePart(name, run.getStatus() != null ? run.getStatus().toUpperCase(Locale.ROOT) : "RUN");
        Date when = run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt();
        if (when != null) {
            appendFilePart(name, new SimpleDateFormat("yyyyMMdd-HHmmss").format(when));
        }
        appendFilePart(name, run.getTestFlowRunId() != null ? String.valueOf(run.getTestFlowRunId()) : "unknown");
        if (name.isEmpty()) {
            name.append("qualitest-run");
        }
        return name + ".html";
    }

    /** 追加文件名片段（清洗非法字符，过长截断） */
    private static void appendFilePart(StringBuilder name, String part) {
        String safe = sanitizeFilePart(part);
        if (safe == null) {
            return;
        }
        if (!name.isEmpty()) {
            name.append('_');
        }
        name.append(safe);
    }

    /**
     * 清洗文件名片段：去掉路径分隔与通配符，空白改下划线，最长 32 字符。
     */
    private static String sanitizeFilePart(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_");
        if (s.isBlank()) {
            return null;
        }
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        return s;
    }

    /** 组装浏览器标签页标题：项目 · 流名 · 运行 ID */
    private static String titleOf(String projectName, String flowName, TestFlowRunResult run) {
        String name = flowName != null && !flowName.isBlank() ? flowName : "测试流";
        if (projectName != null && !projectName.isBlank()) {
            name = projectName + " · " + name;
        }
        return "质衡报告 · " + name + " · " + run.getTestFlowRunId();
    }

    /**
     * 写入页眉：状态徽标与流名同行，项目与 Run ID 一行，场景/环境/触发/耗时/起止为两列网格。
     * showRunError 为 true 且存在错误码或错误消息时，在页眉底部再写一行运行级错误。
     */
    private static void appendHeader(StringBuilder sb, TestFlowRunResult run, String projectName,
                                     String flowName, MetaFromSteps meta, boolean showRunError) {
        String status = run.getStatus() != null ? run.getStatus() : "-";
        sb.append("<header class=\"hero hero--").append(esc(status)).append("\">\n");
        sb.append("<div class=\"hero-top\">");
        sb.append("<span class=\"badge\">").append(esc(statusLabel(status))).append("</span> ");
        sb.append("<h1>").append(esc(flowName != null && !flowName.isBlank() ? flowName : "测试流")).append("</h1>");
        sb.append("</div>\n");
        sb.append("<p class=\"meta-line\">");
        if (projectName != null && !projectName.isBlank()) {
            sb.append(esc(projectName)).append(" · ");
        }
        sb.append("Run ").append(esc(String.valueOf(run.getTestFlowRunId())));
        sb.append("</p>\n");
        sb.append("<div class=\"meta-grid\">\n");
        metaCell(sb, "场景", blankToDash(meta.scenarioName != null ? meta.scenarioName : run.getRunScenarioId()));
        metaCell(sb, "环境", blankToDash(meta.envName));
        metaCell(sb, "触发", blankToDash(triggerLabel(run.getTriggerType())));
        metaCell(sb, "耗时", formatDuration(run.getDurationMs()));
        metaCell(sb, "开始", formatTime(run.getStartedAt()));
        metaCell(sb, "结束", formatTime(run.getFinishedAt()));
        sb.append("</div>\n");
        if (showRunError && (run.getErrorCode() != null || run.getErrorMessage() != null)) {
            String err = (run.getErrorCode() != null ? run.getErrorCode() : "")
                    + (run.getErrorMessage() != null ? " " + run.getErrorMessage() : "");
            sb.append("<p class=\"hero-err\">").append(esc(err.trim())).append("</p>\n");
        }
        sb.append("</header>\n");
    }

    /** 页眉元信息网格中的一格：左侧标签、右侧值 */
    private static void metaCell(StringBuilder sb, String k, String v) {
        sb.append("<div class=\"meta-cell\"><span class=\"meta-k\">").append(esc(k))
                .append("</span><span class=\"meta-v\">").append(esc(v)).append("</span></div>\n");
    }

    /**
     * 写入失败摘要：失败类别、节点名（锚点链到下方卡片）、类型、错误一行、关联变量列表。
     *
     * @param sb       HTML 缓冲
     * @param failures 失败步骤视图
     */
    private static void appendFailureSummary(StringBuilder sb, List<StepView> failures) {
        sb.append("<section>\n<h2>失败摘要</h2>\n<ul class=\"fail-list\">\n");
        for (StepView s : failures) {
            sb.append("<li>");
            sb.append("<span class=\"cat cat--").append(esc(s.category)).append("\">")
                    .append(esc(RunFailureCategoryResolver.label(s.category))).append("</span> ");
            sb.append("<a class=\"fail-link\" href=\"#step-").append(s.index).append("\">")
                    .append("<strong>").append(esc(s.nodeName)).append("</strong></a>");
            sb.append(" <span class=\"muted\">(").append(esc(nodeTypeLabel(s.nodeType))).append(")</span>");
            if (s.errorLine != null && !s.errorLine.isBlank()) {
                sb.append("<div class=\"err\">").append(esc(s.errorLine)).append("</div>");
            }
            appendRelatedVars(sb, s.relatedVars);
            sb.append("</li>\n");
        }
        sb.append("</ul>\n</section>\n");
    }

    /**
     * 写入关联变量定义列表（键 / 短值）；无内容则不输出。
     *
     * @param sb          HTML 缓冲
     * @param relatedVars 键 → 展示值
     */
    private static void appendRelatedVars(StringBuilder sb, Map<String, String> relatedVars) {
        if (relatedVars == null || relatedVars.isEmpty()) {
            return;
        }
        sb.append("<dl class=\"rel-vars\">\n");
        for (Map.Entry<String, String> e : relatedVars.entrySet()) {
            sb.append("<div class=\"rel-var\"><dt>").append(esc(e.getKey())).append("</dt><dd>")
                    .append(esc(e.getValue())).append("</dd></div>\n");
        }
        sb.append("</dl>\n");
    }

    /** 写入业务步骤总览表：序号、节点、中文类型、状态、耗时、一行摘要 */
    private static void appendStepTable(StringBuilder sb, List<StepView> views) {
        sb.append("<section>\n<h2>步骤总览</h2>\n<table class=\"steps\">\n");
        sb.append("<thead><tr><th>#</th><th>节点</th><th>类型</th><th>状态</th><th>耗时</th><th>摘要</th></tr></thead>\n<tbody>\n");
        for (StepView s : views) {
            sb.append("<tr class=\"st--").append(esc(s.status)).append("\">");
            sb.append("<td><a href=\"#step-").append(s.index).append("\">").append(s.index).append("</a></td>");
            sb.append("<td>").append(esc(s.nodeName)).append("</td>");
            sb.append("<td>").append(esc(nodeTypeLabel(s.nodeType))).append("</td>");
            sb.append("<td>").append(esc(statusLabel(s.status))).append("</td>");
            sb.append("<td>").append(esc(formatDuration(s.durationMs))).append("</td>");
            sb.append("<td>").append(esc(s.summary)).append("</td>");
            sb.append("</tr>\n");
        }
        if (views.isEmpty()) {
            sb.append("<tr><td colspan=\"6\" class=\"muted\">无业务步骤</td></tr>\n");
        }
        sb.append("</tbody></table>\n</section>\n");
    }

    /**
     * 写入节点详情卡片时间线。
     * 失败步 details 默认 open；通过步默认收起。
     */
    private static void appendNodeCards(StringBuilder sb, List<StepView> views) {
        sb.append("<section>\n<h2>节点详情</h2>\n");
        if (views.isEmpty()) {
            sb.append("<p class=\"muted\">无业务步骤</p>\n</section>\n");
            return;
        }
        for (StepView s : views) {
            boolean failed = "failed".equals(s.status);
            sb.append("<article class=\"step-card step-card--").append(esc(s.status))
                    .append("\" id=\"step-").append(s.index).append("\">\n");
            sb.append("<details").append(failed ? " open" : "").append(">\n<summary class=\"step-sum\">");
            sb.append("<span class=\"step-num\">#").append(s.index).append("</span> ");
            sb.append("<span class=\"step-name\">").append(esc(s.nodeName)).append("</span> ");
            sb.append("<span class=\"muted\">").append(esc(nodeTypeLabel(s.nodeType))).append("</span> ");
            sb.append("<span class=\"run-badge run-badge--").append(esc(s.status)).append("\">")
                    .append(esc(statusLabel(s.status))).append("</span> ");
            sb.append("<span class=\"muted\">").append(esc(formatDuration(s.durationMs))).append("</span>");
            if (failed && s.category != null) {
                sb.append(" <span class=\"cat cat--").append(esc(s.category)).append("\">")
                        .append(esc(RunFailureCategoryResolver.label(s.category))).append("</span>");
            }
            sb.append("</summary>\n");
            sb.append("<div class=\"step-body\">\n");
            if (failed && s.errorLine != null && !s.errorLine.isBlank()) {
                sb.append("<p class=\"err\">").append(esc(s.errorLine)).append("</p>\n");
            }
            appendTypedDetail(sb, s);
            sb.append("</div>\n</details>\n</article>\n");
        }
        sb.append("</section>\n");
    }

    /** 按节点类型写入结构化详情（表格 / 定义列表），不堆生 JSON */
    private static void appendTypedDetail(StringBuilder sb, StepView s) {
        JSONObject details = s.details;
        if (details == null) {
            sb.append("<p class=\"muted\">无步骤详情</p>\n");
            return;
        }
        String type = s.nodeType;
        if ("http".equals(type)) {
            appendHttpDetail(sb, details, "failed".equals(s.status));
            return;
        }
        if ("assert".equals(type)) {
            appendAssertDetail(sb, details);
            return;
        }
        if ("script".equals(type)) {
            appendScriptDetail(sb, details);
            return;
        }
        if ("condition".equals(type)) {
            appendConditionDetail(sb, details);
            return;
        }
        if ("assign".equals(type)) {
            appendAssignDetail(sb, details);
            return;
        }
        if ("subflow".equals(type)) {
            appendSubflowDetail(sb, details);
            return;
        }
        if (s.errorLine != null) {
            sb.append("<p>").append(esc(s.errorLine)).append("</p>\n");
        } else if (s.summary != null) {
            sb.append("<p>").append(esc(s.summary)).append("</p>\n");
        } else {
            sb.append("<p class=\"muted\">无更多明细</p>\n");
        }
    }

    /** HTTP：方法/URL/状态、业务码分项、提取变量；失败步另附截断响应体 */
    private static void appendHttpDetail(StringBuilder sb, JSONObject details, boolean failed) {
        JSONObject http = details.getJSONObject("http");
        if (http == null) {
            sb.append("<p class=\"muted\">无 HTTP 详情</p>\n");
            return;
        }
        sb.append("<table class=\"kv\">\n");
        kvRow(sb, "方法", http.getString("method"));
        kvRow(sb, "URL", decodeUrlForDisplay(http.getString("url")));
        Object st = http.get("status");
        kvRow(sb, "HTTP 状态", st != null ? String.valueOf(st) : null);
        kvRow(sb, "调用模式", http.getString("callMode"));
        sb.append("</table>\n");

        JSONObject biz = http.getJSONObject("bizCheck");
        if (biz != null) {
            sb.append("<h3>业务码校验</h3>\n<table class=\"kv\">\n");
            kvRow(sb, "字段路径", biz.getString("codePath"));
            kvRow(sb, "实际值", stringify(biz.get("actualCode")));
            Object successValues = biz.get("successValues");
            kvRow(sb, "期望白名单", successValues != null ? stringify(successValues) : null);
            Boolean passed = biz.getBoolean("passed");
            kvRow(sb, "是否通过", passed == null ? null : (passed ? "是" : "否"));
            kvRow(sb, "消息", biz.getString("message"));
            sb.append("</table>\n");
        }

        appendExtracts(sb, details.getJSONArray("extracts"));
        if (details.getJSONArray("extracts") == null) {
            appendExtracts(sb, http.getJSONArray("extracts"));
        }

        if (failed) {
            Object resp = http.get("response");
            if (resp instanceof JSONObject respObj) {
                Object body = respObj.get("body");
                if (body != null) {
                    sb.append("<h3>响应体（截断）</h3>\n<pre>")
                            .append(esc(shorten(stringify(body), BODY_SNIPPET_MAX)))
                            .append("</pre>\n");
                }
            }
        }
    }

    /** 断言：逐条规则表（通过、左值表达式、运算符、右值、实测） */
    private static void appendAssertDetail(StringBuilder sb, JSONObject details) {
        JSONObject assertObj = details.getJSONObject("assert");
        if (assertObj == null) {
            sb.append("<p class=\"muted\">无断言详情</p>\n");
            return;
        }
        JSONArray rules = assertObj.getJSONArray("rules");
        if (rules == null || rules.isEmpty()) {
            sb.append("<p class=\"muted\">无断言规则</p>\n");
            return;
        }
        sb.append("<table class=\"rules\">\n<thead><tr>")
                .append("<th>#</th><th>结果</th><th>左值</th><th>运算</th><th>右值</th><th>实测</th>")
                .append("</tr></thead>\n<tbody>\n");
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            boolean ok = Boolean.TRUE.equals(rule.getBoolean("passed"));
            sb.append("<tr class=\"").append(ok ? "rule-ok" : "rule-fail").append("\">");
            sb.append("<td>").append(i + 1).append("</td>");
            sb.append("<td>").append(ok ? "通过" : "失败").append("</td>");
            sb.append("<td>").append(esc(blankToDash(rule.getString("left")))).append("</td>");
            sb.append("<td>").append(esc(blankToDash(stringify(rule.get("operator"))))).append("</td>");
            sb.append("<td>").append(esc(blankToDash(stringify(rule.get("right"))))).append("</td>");
            sb.append("<td>").append(esc(blankToDash(stringify(rule.get("leftActual"))))).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
    }

    /** 脚本：语言、日志（限条）、变量写入前后 */
    private static void appendScriptDetail(StringBuilder sb, JSONObject details) {
        JSONObject script = details.getJSONObject("script");
        if (script == null) {
            sb.append("<p class=\"muted\">无脚本详情</p>\n");
            return;
        }
        sb.append("<table class=\"kv\">\n");
        kvRow(sb, "语言", script.getString("language"));
        sb.append("</table>\n");
        JSONArray logs = script.getJSONArray("logs");
        if (logs != null && !logs.isEmpty()) {
            sb.append("<h3>日志</h3>\n<ul class=\"log-list\">\n");
            int n = Math.min(logs.size(), SCRIPT_LOG_MAX);
            for (int i = 0; i < n; i++) {
                sb.append("<li><code>").append(esc(stringify(logs.get(i)))).append("</code></li>\n");
            }
            if (logs.size() > SCRIPT_LOG_MAX) {
                sb.append("<li class=\"muted\">…另有 ").append(logs.size() - SCRIPT_LOG_MAX).append(" 条</li>\n");
            }
            sb.append("</ul>\n");
        }
        JSONArray writes = script.getJSONArray("writes");
        if (writes != null && !writes.isEmpty()) {
            sb.append("<h3>写入</h3>\n<table class=\"rules\">\n<thead><tr><th>键</th><th>之前</th><th>之后</th></tr></thead>\n<tbody>\n");
            for (int i = 0; i < writes.size(); i++) {
                JSONObject w = writes.getJSONObject(i);
                if (w == null) {
                    continue;
                }
                sb.append("<tr>");
                sb.append("<td>").append(esc(blankToDash(stringify(w.get("key"))))).append("</td>");
                sb.append("<td>").append(esc(blankToDash(stringify(w.get("before"))))).append("</td>");
                sb.append("<td>").append(esc(blankToDash(stringify(w.get("value"))))).append("</td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table>\n");
        }
    }

    /** 分支：命中分支 ID 与种类 */
    private static void appendConditionDetail(StringBuilder sb, JSONObject details) {
        JSONObject branch = details.getJSONObject("branchTaken");
        if (branch == null) {
            sb.append("<p class=\"muted\">无分支信息</p>\n");
            return;
        }
        sb.append("<table class=\"kv\">\n");
        kvRow(sb, "命中分支", branch.getString("branchId"));
        kvRow(sb, "种类", branch.getString("kind"));
        sb.append("</table>\n");
    }

    /** 赋值：键值表 */
    private static void appendAssignDetail(StringBuilder sb, JSONObject details) {
        JSONArray assigns = details.getJSONArray("assigns");
        if (assigns == null || assigns.isEmpty()) {
            sb.append("<p class=\"muted\">无赋值记录</p>\n");
            return;
        }
        sb.append("<table class=\"rules\">\n<thead><tr><th>键</th><th>值</th></tr></thead>\n<tbody>\n");
        for (int i = 0; i < assigns.size(); i++) {
            JSONObject a = assigns.getJSONObject(i);
            if (a == null) {
                continue;
            }
            String key = firstString(a, "key", "name", "var");
            Object val = a.containsKey("value") ? a.get("value") : a.get("to");
            sb.append("<tr><td>").append(esc(blankToDash(key))).append("</td><td>")
                    .append(esc(blankToDash(stringify(val)))).append("</td></tr>\n");
        }
        sb.append("</tbody></table>\n");
    }

    /** 子流：名称/状态；内层失败子步骤浅列 */
    private static void appendSubflowDetail(StringBuilder sb, JSONObject details) {
        JSONObject subflow = details.getJSONObject("subflow");
        if (subflow == null) {
            sb.append("<p class=\"muted\">无子流详情</p>\n");
            return;
        }
        sb.append("<table class=\"kv\">\n");
        kvRow(sb, "子流名称", firstString(subflow, "subflowName", "name"));
        kvRow(sb, "子流 ID", subflow.getString("subflowId"));
        kvRow(sb, "状态", subflow.getString("status"));
        sb.append("</table>\n");
        JSONArray childSteps = subflow.getJSONArray("childSteps");
        if (childSteps == null || childSteps.isEmpty()) {
            return;
        }
        sb.append("<h3>内层步骤（失败优先）</h3>\n<ul class=\"child-list\">\n");
        int shown = 0;
        for (int i = 0; i < childSteps.size(); i++) {
            JSONObject child = childSteps.getJSONObject(i);
            if (child == null) {
                continue;
            }
            String st = child.getString("status");
            if (!"failed".equals(st) && shown >= 8) {
                continue;
            }
            sb.append("<li>");
            sb.append(esc(blankToDash(firstString(child, "nodeName", "name"))));
            sb.append(" · ").append(esc(blankToDash(st)));
            String msg = child.getString("errorMessage");
            if (msg == null && child.getJSONObject("error") != null) {
                msg = child.getJSONObject("error").getString("message");
            }
            if (msg != null) {
                sb.append(" · ").append(esc(shorten(msg, 80)));
            }
            sb.append("</li>\n");
            shown++;
        }
        sb.append("</ul>\n");
    }

    /** 提取变量表 */
    private static void appendExtracts(StringBuilder sb, JSONArray extracts) {
        if (extracts == null || extracts.isEmpty()) {
            return;
        }
        sb.append("<h3>提取变量</h3>\n<table class=\"rules\">\n<thead><tr><th>名称</th><th>作用域</th><th>值</th></tr></thead>\n<tbody>\n");
        for (int i = 0; i < extracts.size(); i++) {
            JSONObject e = extracts.getJSONObject(i);
            if (e == null) {
                continue;
            }
            sb.append("<tr>");
            sb.append("<td>").append(esc(blankToDash(e.getString("name")))).append("</td>");
            sb.append("<td>").append(esc(blankToDash(e.getString("scope")))).append("</td>");
            sb.append("<td>").append(esc(blankToDash(shorten(stringify(e.get("value")), 120)))).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
    }

    /** 写入页脚：产品名、运行 ID、报告生成时间 */
    private static void appendFooter(StringBuilder sb, TestFlowRunResult run) {
        sb.append("<footer>\n");
        sb.append("<p>质衡 Qualitest · Run ID ").append(esc(String.valueOf(run.getTestFlowRunId())));
        sb.append(" · 生成于 ").append(esc(formatTime(new Date()))).append("</p>\n");
        sb.append("</footer>\n");
    }

    /**
     * 将落库步骤转为报告行视图。
     * 跳过审计步；含 HTTP 时脱敏；失败步计算类别、错误行与关联变量。
     */
    private static List<StepView> buildStepViews(List<TestFlowRunStepResult> steps) {
        List<StepView> out = new ArrayList<>();
        int index = 0;
        for (TestFlowRunStepResult step : steps) {
            if (step == null) {
                continue;
            }
            String type = step.getNodeType();
            if (type != null && AUDIT_NODE_TYPES.contains(type)) {
                continue;
            }
            index++;
            JSONObject details = StepDetailsJson.parse(step.getStepDetails());
            if (details != null && details.getJSONObject("http") != null) {
                desensitizeHttpInPlace(details);
            }
            boolean failed = "failed".equals(step.getStatus());
            StepView v = new StepView();
            v.index = index;
            v.nodeName = blankToDash(step.getNodeName());
            v.nodeType = blankToDash(step.getNodeType());
            v.status = step.getStatus() != null ? step.getStatus() : "-";
            v.durationMs = step.getDurationMs();
            v.details = details;
            v.category = failed
                    ? RunFailureCategoryResolver.resolve(step.getNodeType(), details)
                    : null;
            v.summary = buildSummary(step.getNodeType(), details, v.status);
            if (failed) {
                v.errorLine = buildErrorLine(details);
                v.relatedVars = RelatedFlowVars.pick(details);
            }
            out.add(v);
        }
        return out;
    }

    /**
     * 从场景加载步或图快照中解析页眉用的场景名、环境名。
     */
    private static MetaFromSteps extractMeta(List<TestFlowRunStepResult> steps, TestFlowRunResult run) {
        MetaFromSteps meta = new MetaFromSteps();
        for (TestFlowRunStepResult step : steps) {
            if (step == null || !StepResultWriter.NODE_TYPE_RUN_CONFIG.equals(step.getNodeType())) {
                continue;
            }
            JSONObject details = StepDetailsJson.parse(step.getStepDetails());
            if (details == null) {
                break;
            }
            JSONObject loaded = details.getJSONObject("scenarioLoaded");
            if (loaded == null) {
                break;
            }
            meta.scenarioName = loaded.getString("scenarioName");
            meta.envName = loaded.getString("envName");
            break;
        }
        if (meta.scenarioName == null || meta.scenarioName.isBlank()) {
            meta.scenarioName = resolveScenarioNameFromSnapshot(run.getGraphJsonSnapshot(), run.getRunScenarioId());
        }
        return meta;
    }

    /**
     * 在触发时固化的流程图快照里，按场景 ID 查找场景显示名。
     */
    private static String resolveScenarioNameFromSnapshot(String graphJsonSnapshot, String runScenarioId) {
        if (graphJsonSnapshot == null || graphJsonSnapshot.isBlank() || runScenarioId == null) {
            return null;
        }
        try {
            JSONObject graph = JSON.parseObject(graphJsonSnapshot);
            if (graph == null) {
                return null;
            }
            JSONObject meta = graph.getJSONObject("meta");
            if (meta == null) {
                return null;
            }
            JSONArray scenarios = meta.getJSONArray("scenarios");
            if (scenarios == null) {
                return null;
            }
            for (int i = 0; i < scenarios.size(); i++) {
                JSONObject sc = scenarios.getJSONObject(i);
                if (sc != null && runScenarioId.equals(String.valueOf(sc.get("id")))) {
                    String name = sc.getString("name");
                    return name != null && !name.isBlank() ? name.trim() : null;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * 按节点类型生成步骤表「摘要」列文案。
     * HTTP：方法+路径+状态码；断言：N 条中 M 通过；分支/脚本：命中分支或日志条数。
     */
    private static String buildSummary(String nodeType, JSONObject details, String status) {
        if (details == null) {
            return statusLabel(status);
        }
        if ("http".equals(nodeType)) {
            JSONObject http = details.getJSONObject("http");
            if (http != null) {
                String method = http.getString("method");
                String url = decodeUrlForDisplay(http.getString("url"));
                Object st = http.get("status");
                StringBuilder line = new StringBuilder();
                if (method != null) {
                    line.append(method).append(' ');
                }
                if (url != null) {
                    line.append(shorten(url, 80));
                }
                if (st != null) {
                    line.append(" → ").append(st);
                }
                JSONObject biz = http.getJSONObject("bizCheck");
                if (biz != null) {
                    line.append(" · 业务码=").append(biz.get("actualCode"));
                    if (Boolean.FALSE.equals(biz.getBoolean("passed"))) {
                        line.append(" 未通过");
                    }
                }
                return line.length() > 0 ? line.toString() : statusLabel(status);
            }
        }
        if ("assert".equals(nodeType)) {
            JSONObject assertObj = details.getJSONObject("assert");
            if (assertObj != null) {
                JSONArray rules = assertObj.getJSONArray("rules");
                if (rules != null && !rules.isEmpty()) {
                    int passed = 0;
                    for (int i = 0; i < rules.size(); i++) {
                        JSONObject rule = rules.getJSONObject(i);
                        if (rule != null && Boolean.TRUE.equals(rule.getBoolean("passed"))) {
                            passed++;
                        }
                    }
                    return rules.size() + " 条中 " + passed + " 通过";
                }
            }
        }
        if ("condition".equals(nodeType)) {
            JSONObject branch = details.getJSONObject("branchTaken");
            if (branch != null) {
                return "命中 " + blankToDash(branch.getString("branchId"));
            }
        }
        if ("script".equals(nodeType)) {
            JSONObject script = details.getJSONObject("script");
            if (script != null) {
                JSONArray logs = script.getJSONArray("logs");
                int n = logs != null ? logs.size() : 0;
                return "日志 " + n + " 条";
            }
        }
        JSONObject error = details.getJSONObject("error");
        if (error != null) {
            String msg = error.getString("message");
            if (msg != null && !msg.isBlank()) {
                return shorten(msg, 80);
            }
        }
        return statusLabel(status);
    }

    /** 从步骤详情提取「错误码: 错误信息」单行文案 */
    private static String buildErrorLine(JSONObject details) {
        if (details == null) {
            return null;
        }
        JSONObject error = details.getJSONObject("error");
        if (error != null) {
            String code = error.getString("code");
            String message = error.getString("message");
            if (code != null && message != null) {
                return code + ": " + message;
            }
            if (message != null) {
                return message;
            }
            return code;
        }
        return null;
    }

    /** 对步骤详情中的 HTTP 请求/响应字段做敏感信息脱敏后写回 */
    private static void desensitizeHttpInPlace(JSONObject details) {
        JSONObject http = details.getJSONObject("http");
        if (http == null) {
            return;
        }
        Map<String, Object> map = new LinkedHashMap<>(http);
        Map<String, Object> masked = HttpStepDetailsDesensitizer.desensitize(map);
        details.put("http", new JSONObject(masked));
    }

    /** 节点类型 code 转中文展示名 */
    private static String nodeTypeLabel(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return "-";
        }
        return switch (nodeType) {
            case "http" -> "HTTP";
            case "assert" -> "断言";
            case "condition" -> "分支";
            case "assign" -> "赋值";
            case "script" -> "脚本";
            case "subflow" -> "子流";
            case "awaitInput" -> "等待输入";
            default -> nodeType;
        };
    }

    /** 追加键值表一行；值为空则跳过 */
    private static void kvRow(StringBuilder sb, String k, String v) {
        if (v == null || v.isBlank()) {
            return;
        }
        sb.append("<tr><th>").append(esc(k)).append("</th><td>").append(esc(v)).append("</td></tr>\n");
    }

    /** 运行/步骤状态转为报告页大写英文展示 */
    private static String statusLabel(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "passed" -> "PASSED";
            case "failed" -> "FAILED";
            case "running" -> "RUNNING";
            case "paused" -> "PAUSED";
            case "aborted" -> "ABORTED";
            case "cancelled" -> "CANCELLED";
            case "skipped" -> "SKIPPED";
            default -> status.toUpperCase(Locale.ROOT);
        };
    }

    /** 触发方式 code 转为中文或简写标签 */
    private static String triggerLabel(String triggerType) {
        if (triggerType == null || triggerType.isBlank()) {
            return "-";
        }
        return switch (triggerType) {
            case "manual" -> "手动";
            case "ai" -> "AI 跑流";
            case "ci" -> "CI";
            case "schedule" -> "定时";
            default -> triggerType;
        };
    }

    /** 格式化时间，空值显示为短横线 */
    private static String formatTime(Date date) {
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /** 格式化耗时：不足 1 秒用毫秒，否则用秒 */
    private static String formatDuration(Long ms) {
        if (ms == null) {
            return "-";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", ms / 1000.0);
    }

    /**
     * 将 URL 中的百分号编码解码为可读文案，仅用于报告展示。
     * 不含 %、或解码失败时返回原文。
     */
    private static String decodeUrlForDisplay(String url) {
        if (url == null || url.isBlank() || url.indexOf('%') < 0) {
            return url;
        }
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return url;
        }
    }

    /** 空串显示为短横线 */
    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** 任意对象转展示字符串 */
    private static String stringify(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof Number || o instanceof Boolean) {
            return String.valueOf(o);
        }
        try {
            return JSON.toJSONString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** 从对象中取第一个非空字符串字段 */
    private static String firstString(JSONObject obj, String... keys) {
        if (obj == null) {
            return null;
        }
        for (String key : keys) {
            String v = obj.getString(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** 超长文本截断并加省略号 */
    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    /** HTML 特殊字符转义，防止步骤文案破坏页面结构 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 报告内联样式：总览表、失败摘要与关联变量、节点卡片、规则表 */
    private static String css() {
        return """
                @page{size:A4;margin:16mm}
                *{box-sizing:border-box}
                body{font-family:"Microsoft YaHei","PingFang SC","Noto Sans SC",sans-serif;color:#1c2430;font-size:11pt;line-height:1.45;margin:0;padding:16px 20px;background:#fff}
                h1{font-size:14pt;margin:0;display:inline;font-weight:700;line-height:1.3}
                h2{font-size:12pt;margin:16px 0 6px;padding-bottom:3px;border-bottom:1.5px solid #1f6feb;page-break-after:avoid}
                h3{font-size:10.5pt;margin:10px 0 4px}
                .hero{padding:8px 12px;border-left:4px solid #5b6775;background:#f6f8fb;margin-bottom:10px}
                .hero--passed{border-left-color:#1a7f37}
                .hero--failed{border-left-color:#cf222e}
                .hero--aborted,.hero--cancelled{border-left-color:#5b6775}
                .hero-top{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
                .badge{display:inline-block;font-weight:700;letter-spacing:.03em;font-size:10.5pt;padding:1px 8px;border-radius:3px;background:#eaeef2}
                .hero--passed .badge{background:#dcfce7;color:#166534}
                .hero--failed .badge{background:#fee2e2;color:#b91c1c}
                .meta-line{color:#5b6775;margin:2px 0 6px;font-size:9.5pt}
                .meta-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 16px;font-size:9.5pt}
                .meta-cell{display:flex;gap:6px;min-width:0}
                .meta-k{color:#5b6775;flex:0 0 auto}
                .meta-v{font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .hero-err{margin:6px 0 0;padding:4px 8px;background:#fff5f5;border:1px solid #ffd7d5;color:#82071e;font-size:9.5pt;line-height:1.4}
                .kv,.steps,.rules{width:100%;border-collapse:collapse;margin-top:6px;font-size:10pt}
                .kv th,.kv td,.steps th,.steps td,.rules th,.rules td{border:1px solid #d5dbe3;padding:3px 6px;text-align:left;vertical-align:top}
                .kv th{width:22%;background:#f3f6fb;font-weight:600}
                .steps th,.rules th{background:#f3f6fb}
                .fail-list{list-style:none;padding:0;margin:0}
                .fail-list li{padding:6px 8px;margin:0 0 4px;background:#fff5f5;border:1px solid #ffd7d5}
                .rel-vars{margin:6px 0 0;padding:4px 0 0;border-top:1px dashed #ffd7d5}
                .rel-var{display:flex;gap:8px;font-size:9pt;font-family:Consolas,"Courier New",monospace;line-height:1.35;margin:2px 0}
                .rel-var dt{flex:0 0 auto;color:#9a3412;font-weight:700;margin:0}
                .rel-var dd{flex:1 1 auto;margin:0;color:#7f1d1d;word-break:break-all}
                .fail-link{color:#1f6feb;text-decoration:none}
                .fail-link:hover{text-decoration:underline}
                .cat{display:inline-block;font-size:9pt;padding:1px 6px;border-radius:3px;background:#eaeef2}
                .cat--bizCode{background:#fff1e5}
                .cat--assert{background:#ddf4ff}
                .cat--other{background:#eaeef2}
                .err{margin:6px 0;color:#82071e;font-size:10pt}
                .muted{color:#5b6775}
                .step-card{margin:0 0 10px;border:1px solid #d5dbe3;border-radius:4px;background:#fff;page-break-inside:avoid}
                .step-card--failed{border-color:#ffd7d5;background:#fffafa}
                .step-card details{margin:0;border:0;padding:0}
                .step-sum{padding:8px 12px;cursor:pointer;list-style:none}
                .step-sum::-webkit-details-marker{display:none}
                .step-num{font-weight:700;color:#5b6775}
                .step-name{font-weight:700}
                .step-body{padding:0 12px 12px}
                .run-badge{display:inline-block;padding:1px 6px;border-radius:4px;font-size:9.5pt;font-weight:700}
                .run-badge--passed{background:#dcfce7;color:#166534}
                .run-badge--failed{background:#fee2e2;color:#b91c1c}
                .run-badge--running{background:#dbeafe;color:#1d4ed8}
                .run-badge--paused{background:#fef3c7;color:#92400e}
                .run-badge--aborted,.run-badge--cancelled,.run-badge--skipped{background:#f3f4f6;color:#4b5563}
                .rule-fail td{background:#fff5f5}
                .rule-ok td{background:#f0fdf4}
                .log-list,.child-list{margin:4px 0 0;padding-left:1.2em}
                pre{white-space:pre-wrap;word-break:break-word;font-family:Consolas,"Sarasa Mono SC",monospace;font-size:9.5pt;margin:6px 0 0;background:#f6f8fb;padding:8px;border-radius:4px}
                footer{margin-top:28px;color:#5b6775;font-size:9.5pt;border-top:1px solid #d5dbe3;padding-top:8px}
                a{color:#1f6feb}
                """;
    }

    /** 页眉用的场景名与环境名 */
    private static final class MetaFromSteps {
        /** 运行场景显示名 */
        private String scenarioName;
        /** 环境显示名 */
        private String envName;
    }

    /** 步骤总览与节点卡片共用的行视图 */
    private static final class StepView {
        /** 业务步骤序号（从 1 起，用于锚点） */
        private int index;
        /** 节点显示名 */
        private String nodeName;
        /** 节点类型 code */
        private String nodeType;
        /** 步骤状态 code */
        private String status;
        /** 耗时毫秒 */
        private Long durationMs;
        /** 失败类别；非失败为 null */
        private String category;
        /** 步骤表摘要列 */
        private String summary;
        /** 失败一行错误文案 */
        private String errorLine;
        /** 失败摘要关联变量：键 → 短展示值；非失败为 null */
        private Map<String, String> relatedVars;
        /** 已解析（且 HTTP 已脱敏）的步骤详情 */
        private JSONObject details;
    }
}
