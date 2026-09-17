package com.qualitest.web.controller.project;

import com.qualitest.common.core.controller.BaseController;
import com.qualitest.flow.sync.FlowExternalChangeEvent;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.web.ai.AiSseStreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 测试流外部变更的浏览器长连接。
 * 打开画布时订阅：他端写图、改素材/鉴权、开跑等成功后推事件，驱动页面轻量刷新。
 */
@Slf4j
@RestController
@RequestMapping("/project/testFlow")
@RequiredArgsConstructor
public class TestFlowEventsController extends BaseController {

    /** 单连接最长保持 2 小时；前端断线后自行重连 */
    private static final long SSE_TIMEOUT_MS = 2L * 60 * 60 * 1000;
    /** 保活 ping 间隔（秒） */
    private static final long HEARTBEAT_SECONDS = 20;

    private static final ScheduledExecutorService HEARTBEAT_POOL =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "flow-events-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final ITestFlowService testFlowService;
    private final ITestProjectMemberService testProjectMemberService;
    private final FlowExternalChangeHub flowExternalChangeHub;

    /**
     * 订阅指定测试流及所属项目的外部变更。
     * 校验流存在与项目成员后建立长连接；断开时取消订阅并停保活。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testFlowId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable("testFlowId") Long testFlowId) {
        TestFlowResult flow = testFlowService.selectTestFlowResult(testFlowId);
        if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            throw new com.qualitest.common.exception.ServiceException("测试流不存在");
        }
        if (flow.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(flow.getTestProjectId());
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AiSseStreamSupport.armCancel(emitter, cancelled);

        // 有外部写入时推 JSON 事件
        Consumer<FlowExternalChangeEvent> listener = event -> {
            if (cancelled.get() || event == null) {
                return;
            }
            AiSseStreamSupport.sendJson(emitter, cancelled, event.toPayloadMap(true));
        };

        Runnable unsubFlow = flowExternalChangeHub.subscribeFlow(testFlowId, listener);
        Runnable unsubProject = flow.getTestProjectId() != null
                ? flowExternalChangeHub.subscribeProject(flow.getTestProjectId(), listener)
                : () -> {
                };

        // 定期 ping，避免中间层掐断空闲连接
        ScheduledFuture<?> heartbeat = HEARTBEAT_POOL.scheduleAtFixedRate(() -> {
            if (cancelled.get()) {
                return;
            }
            AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "ping"));
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        Runnable cleanup = () -> {
            cancelled.set(true);
            heartbeat.cancel(false);
            unsubFlow.run();
            unsubProject.run();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        AiSseStreamSupport.sendJson(emitter, cancelled, Map.of(
                "type", "subscribed",
                "testFlowId", String.valueOf(testFlowId)));
        return emitter;
    }
}
