package com.qualitest.web.controller.project;

import com.qualitest.ai.scenario.apidesign.ApiDesignAgent;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.web.ai.AiSseStreamSupport;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.AjaxResult;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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
        Long userId = getUserId();
        return AiSseStreamSupport.openDesignStream(
                SSE_TIMEOUT_MS,
                "api-design-ai-stream",
                "AI API 助手请求失败",
                (listener, cancelled) -> apiDesignAgent.design(request, userId, listener, cancelled));
    }
}
