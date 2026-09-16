package com.qualitest.web.controller.project;

import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.scenario.apidesign.ApiDesignAgent;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.web.ai.AiSseStreamSupport;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.AjaxResult;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** AI API 助手 HTTP 接口：快捷提示词列表、流式设计对话。 */
@RestController
@RequestMapping("/project/testProjectApi/ai")
@AllArgsConstructor
public class TestApiAiController extends BaseController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ApiDesignAgent apiDesignAgent;
    private final ITestProjectMemberService testProjectMemberService;
    private final IAiPromptTemplateService aiPromptTemplateService;

    /** 按会话场景返回 AI API 助手输入区可用的提示词模板（平台 + 项目） */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/promptTemplates")
    public AjaxResult listPromptTemplates(
            @RequestParam Long testProjectId,
            @RequestParam(required = false) String sessionScene) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        String scene = sessionScene != null && !sessionScene.isBlank()
                ? sessionScene
                : AiChatConversationService.SCENE_TEST_API_DESIGN;
        List<AiPromptTemplateResult> list = aiPromptTemplateService.listForDesignPanel(testProjectId, scene);
        return AjaxResult.success(list);
    }

    /**
     * SSE 流式 API 设计：推送过程事件与最终结果。
     * 客户端断连或超时会置取消标志；业务线程尽量把已产生的助手半成品写入会话后再结束。
     * 事件含 session（会话 id）、token / thinking、tool_start / tool_end、done（可含 toolTrace、interrupted）、error。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping(value = "/design/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter designStream(@RequestBody ApiDesignRequest request) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 断连 / 超时 / 发送失败 → true；设计循环步间读取后停止并尽量落盘
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AiSseStreamSupport.armCancel(emitter, cancelled);
        Long userId = getUserId();
        AgentRunListener listener = new AgentRunListener() {
            @Override
            public void onToolStart(String toolName) {
                AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "tool_start", "tool", toolName));
            }

            @Override
            public void onToolEnd(String toolName) {
                AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "tool_end", "tool", toolName));
            }

            @Override
            public void onThinkingDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "thinking", "text", delta));
                }
            }

            @Override
            public void onTextDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "token", "text", delta));
                }
            }

            @Override
            public void onSessionReady(Long aiChatSessionId) {
                // 尽早推送会话 id，取消后客户端可重拉半成品
                if (aiChatSessionId != null) {
                    AiSseStreamSupport.sendJson(emitter, cancelled, Map.of(
                            "type", "session",
                            "aiChatSessionId", String.valueOf(aiChatSessionId)));
                }
            }
        };
        // 独立线程须带回登录态，避免后续鉴权/成员校验拿不到用户
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread worker = new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ApiDesignResult result = apiDesignAgent.design(request, userId, listener, cancelled::get);
                // 即使已取消也尽量推 done（含半成品）；已断连时 sendJson 会静默跳过
                AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "done", "result", result));
                if (!cancelled.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                String message = e instanceof LlmClientException ? e.getMessage() : "AI API 助手请求失败";
                try {
                    AiSseStreamSupport.sendJson(emitter, cancelled, Map.of("type", "error", "message", message));
                    if (!cancelled.get()) {
                        emitter.complete();
                    }
                } catch (Exception ignored) {
                    if (!cancelled.get()) {
                        emitter.completeWithError(e);
                    }
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        worker.setName("api-design-ai-stream");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }
}
