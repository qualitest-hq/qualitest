package com.qualitest.web.controller.project;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.scenario.apidesign.ApiDesignAgent;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.IAiPromptTemplateService;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    /** SSE 流式 API 设计：推送 token、思考、工具调用与最终结果 */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping(value = "/design/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter designStream(@RequestBody ApiDesignRequest request) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long userId = getUserId();
        AgentRunListener listener = new AgentRunListener() {
            @Override
            public void onToolStart(String toolName) {
                sendStreamEvent(emitter, Map.of("type", "tool_start", "tool", toolName));
            }

            @Override
            public void onToolEnd(String toolName) {
                sendStreamEvent(emitter, Map.of("type", "tool_end", "tool", toolName));
            }

            @Override
            public void onThinkingDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    sendStreamEvent(emitter, Map.of("type", "thinking", "text", delta));
                }
            }

            @Override
            public void onTextDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    sendStreamEvent(emitter, Map.of("type", "token", "text", delta));
                }
            }
        };
        // SSE 独立线程须带回登录态，避免后续鉴权/成员校验拿不到用户
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread worker = new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ApiDesignResult result = apiDesignAgent.design(request, userId, listener);
                sendStreamEvent(emitter, Map.of("type", "done", "result", result));
                emitter.complete();
            } catch (Exception e) {
                String message = e instanceof LlmClientException ? e.getMessage() : "AI API 助手请求失败";
                try {
                    sendStreamEvent(emitter, Map.of("type", "error", "message", message));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
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

    /** 向 SSE 连接发送一条 JSON 事件。 */
    private static void sendStreamEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event()
                    .data(JSON.toJSONString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new LlmClientException("SSE 发送失败", e);
        }
    }
}
