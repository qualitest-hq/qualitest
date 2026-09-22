package com.qualitest.project.report;

import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简易 HTML 报告渲染用例：成功运行、断言失败、业务码失败与节点卡片。
 */
class RunHtmlReportRendererTest {

    @Test
    @DisplayName("成功运行：页眉 PASSED；HTTP 卡片存在且默认不展开；无失败摘要")
    void render_passedRun() {
        TestFlowRunDetailResult detail = detail("passed", List.of(
                step(1L, "http", "健康检查", "passed",
                        "{\"http\":{\"method\":\"GET\",\"url\":\"/api/health\",\"status\":200}}")
        ));
        String html = RunHtmlReportRenderer.render(detail, "演示项目", "健康流");
        assertTrue(html.contains("PASSED"));
        assertTrue(html.contains("健康流"));
        assertTrue(html.contains("演示项目"));
        assertTrue(html.contains("GET /api/health → 200"));
        assertTrue(html.contains("步骤总览"));
        assertTrue(html.contains("节点详情"));
        assertTrue(html.contains("id=\"step-1\""));
        assertTrue(html.contains("健康检查"));
        assertTrue(html.contains("<details>"));
        assertFalse(html.contains("<details open"));
        assertFalse(html.contains("失败摘要"));
        assertFalse(html.contains("响应体（截断）"));
    }

    @Test
    @DisplayName("断言失败：规则表含左值实测；失败卡片默认展开；摘要可跳转")
    void render_assertFailed() {
        String details = "{"
                + "\"error\":{\"code\":\"TF_ASSERT_FAILED\",\"message\":\"断言未通过\"},"
                + "\"assert\":{\"rules\":[{"
                + "\"left\":\"flow.amount\",\"operator\":\"eq\",\"right\":\"1\","
                + "\"passed\":false,\"leftActual\":0"
                + "}]}"
                + "}";
        TestFlowRunDetailResult detail = detail("failed", List.of(
                step(1L, "assert", "校验金额", "failed", details)
        ));
        detail.getRun().setErrorCode("TF_ASSERT_FAILED");
        detail.getRun().setErrorMessage("断言未通过");
        String html = RunHtmlReportRenderer.render(detail, null, "下单流");
        assertTrue(html.contains("FAILED"));
        assertTrue(html.contains("失败摘要"));
        assertTrue(html.contains("断言"));
        assertTrue(html.contains("校验金额"));
        assertTrue(html.contains("TF_ASSERT_FAILED"));
        assertTrue(html.contains("cat--assert"));
        assertTrue(html.contains("1 条中 0 通过"));
        assertTrue(html.contains("flow.amount"));
        assertTrue(html.contains("leftActual") || html.contains(">0<"));
        assertTrue(html.contains("href=\"#step-1\""));
        assertTrue(html.contains("<details open"));
        assertTrue(html.contains("class=\"rules\""));
        // 已有失败步骤时页眉不输出运行级错误块
        assertFalse(html.contains("hero-err"));
    }

    @Test
    @DisplayName("业务码失败：展示实际码与期望白名单；审计步不进步骤；卡片含业务码校验")
    void render_bizCodeFailed() {
        String details = "{"
                + "\"error\":{\"code\":\"TF_BIZ_CODE\",\"message\":\"业务码不匹配\"},"
                + "\"http\":{\"method\":\"POST\",\"url\":\"/api/login\",\"status\":200,"
                + "\"bizCheck\":{\"codePath\":\"code\",\"actualCode\":50001,\"successValues\":[0],\"passed\":false,\"message\":\"未登录\"}}"
                + "}";
        TestFlowRunDetailResult detail = detail("failed", List.of(
                step(0L, "runConfig", "场景加载", "passed",
                        "{\"scenarioLoaded\":{\"scenarioName\":\"默认\",\"envName\":\"测试\"}}"),
                step(1L, "http", "登录接口", "failed", details)
        ));
        String html = RunHtmlReportRenderer.render(detail, "商城", "登录流");
        assertTrue(html.contains("FAILED"));
        assertTrue(html.contains("业务码"));
        assertTrue(html.contains("登录接口"));
        assertTrue(html.contains("cat--bizCode"));
        assertTrue(html.contains("POST /api/login → 200"));
        assertTrue(html.contains("业务码校验"));
        assertTrue(html.contains("50001"));
        assertTrue(html.contains("[0]") || html.contains(">0<"));
        assertTrue(html.contains("响应体（截断）") || html.contains("业务码校验"));
        assertFalse(html.contains(">场景加载<"));
        assertTrue(html.contains("默认") || html.contains("测试"));
        assertTrue(html.contains("id=\"step-1\""));
        assertTrue(html.contains("<details open"));
    }

    @Test
    @DisplayName("通过与失败混排：仅失败卡片带 open")
    void render_mixedOpenState() {
        String okHttp = "{\"http\":{\"method\":\"GET\",\"url\":\"/ok\",\"status\":200}}";
        String badAssert = "{"
                + "\"error\":{\"code\":\"TF_ASSERT_FAILED\",\"message\":\"断言未通过\"},"
                + "\"assert\":{\"rules\":[{\"left\":\"x\",\"operator\":\"eq\",\"right\":\"1\",\"passed\":false,\"leftActual\":2}]}"
                + "}";
        TestFlowRunDetailResult detail = detail("failed", List.of(
                step(1L, "http", "探测", "passed", okHttp),
                step(2L, "assert", "校验", "failed", badAssert)
        ));
        String html = RunHtmlReportRenderer.render(detail, null, "混跑");
        assertTrue(html.contains("id=\"step-1\""));
        assertTrue(html.contains("id=\"step-2\""));
        int openCount = 0;
        int from = 0;
        while (true) {
            int i = html.indexOf("<details open", from);
            if (i < 0) {
                break;
            }
            openCount++;
            from = i + 1;
        }
        assertTrue(openCount == 1, "仅失败步应默认展开，实际 open 数=" + openCount);
    }

    @Test
    @DisplayName("URL 百分号编码在报告中解码为可读中文")
    void render_decodesPercentEncodedUrl() {
        // classroomName=质衡教室后台
        String encoded = "http://127.0.0.1:8887/list?classroomName=%E8%B4%A8%E8%B4%A1%E6%95%99%E5%AE%A4%E5%90%8E%E5%8F%B0";
        String details = "{\"http\":{\"method\":\"GET\",\"url\":\"" + encoded + "\",\"status\":200}}";
        TestFlowRunDetailResult detail = detail("passed", List.of(
                step(1L, "http", "教室列表", "passed", details)
        ));
        String html = RunHtmlReportRenderer.render(detail, null, "流");
        assertTrue(html.contains("质衡教室后台"));
        assertFalse(html.contains("%E8%B4%A8%E8%B4%A1"));
    }

    @Test
    @DisplayName("下载文件名含流名、状态、时间与运行 ID")
    void buildDownloadFileName_containsKeyParts() {
        TestFlowRunResult run = TestFlowRunResult.builder()
                .testFlowRunId(1001L)
                .status("failed")
                .finishedAt(new Date(0L))
                .build();
        String name = RunHtmlReportRenderer.buildDownloadFileName(run, "商城", "登录流", "默认");
        assertTrue(name.contains("商城"));
        assertTrue(name.contains("登录流"));
        assertTrue(name.contains("默认"));
        assertTrue(name.contains("FAILED"));
        assertTrue(name.contains("1001"));
        assertTrue(name.endsWith(".html"));
        assertFalse(name.contains(" "));
        assertFalse(name.contains("/"));
    }

    /** 构造带头信息的运行详情 */
    private static TestFlowRunDetailResult detail(String status, List<TestFlowRunStepResult> steps) {
        TestFlowRunResult run = TestFlowRunResult.builder()
                .testFlowRunId(1001L)
                .testFlowId(10L)
                .status(status)
                .triggerType("manual")
                .startedAt(new Date())
                .finishedAt(new Date())
                .durationMs(1234L)
                .build();
        return TestFlowRunDetailResult.builder().run(run).steps(steps).build();
    }

    /** 构造单步结果 */
    private static TestFlowRunStepResult step(Long index, String type, String name, String status, String details) {
        return TestFlowRunStepResult.builder()
                .stepIndex(index)
                .nodeId("n-" + index)
                .nodeType(type)
                .nodeName(name)
                .status(status)
                .durationMs(100L)
                .stepDetails(details)
                .build();
    }
}
