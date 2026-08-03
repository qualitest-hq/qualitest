package com.qualitest.web.controller.project;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.scenario.flow.TestFlowDesignAgent;
import com.qualitest.ai.scenario.flow.AssetUpsertProposalService;
import com.qualitest.ai.scenario.flow.FlowDesignPatchConfirmService;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionRequest;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignResult;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.scenario.flow.TestFlowDesignAccessValidator;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 测试流 AI 设计 HTTP 接口。
 * <p>
 * 接收用户 prompt 与当前 graph_json，返回增量 patch 建议；
 * 不自动保存 test_flow，不触发 Run。
 */
@RestController
@RequestMapping("/project/testFlow/ai")
@AllArgsConstructor
public class TestFlowAiController extends BaseController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final TestFlowDesignAgent testFlowDesignAgent;
    private final FlowDesignPatchConfirmService flowDesignPatchConfirmService;
    private final AssetUpsertProposalService assetUpsertProposalService;
    private final ITestProjectMemberService testProjectMemberService;
    private final TestFlowDesignAccessValidator testFlowDesignAccessValidator;
    private final IAiPromptTemplateService aiPromptTemplateService;

    /**
     * AI 设计面板：查询可用的提示词模板（平台级 + 当前项目级）。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/promptTemplates")
    public AjaxResult listPromptTemplates(
            @RequestParam Long testProjectId,
            @RequestParam(required = false) String sessionScene) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        String scene = sessionScene != null ? sessionScene : AiChatConversationService.SCENE_TEST_FLOW_DESIGN;
        List<AiPromptTemplateResult> list = aiPromptTemplateService.listForDesignPanel(testProjectId, scene);
        return AjaxResult.success(list);
    }

    /**
     * 确认单个 Staging 变更单元。
     * <p>
     * 在内存中完成单单元合并与全图校验；不写 test_flow、不触发 Run。
     * 成功时返回 graphJson 供前端落盘；失败时保持该单元 Staging 态并展示 errors。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping("/patch/confirmUnit")
    public AjaxResult confirmPatchUnit(@RequestBody FlowDesignPatchConfirmRequest request) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        FlowDesignPatchConfirmResult result = flowDesignPatchConfirmService.confirmUnit(request);
        return AjaxResult.success(result);
    }

    /**
     * 确认素材库写入提案：校验项目成员后，把提案 fields 写入项目素材库。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping("/assetProposal/confirm")
    public AjaxResult confirmAssetProposal(@RequestBody AssetUpsertProposalDecisionRequest request) {
        return decideAssetProposal(request, true);
    }

    /**
     * 拒绝素材库写入提案：校验项目成员后，只改消息元数据状态，不写素材库。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping("/assetProposal/reject")
    public AjaxResult rejectAssetProposal(@RequestBody AssetUpsertProposalDecisionRequest request) {
        return decideAssetProposal(request, false);
    }

    /**
     * 确认或拒绝素材提案的共用入口：成员校验后调用业务服务。
     *
     * @param confirm true 确认落盘，false 拒绝
     */
    private AjaxResult decideAssetProposal(AssetUpsertProposalDecisionRequest request, boolean confirm) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        AssetUpsertProposalDecisionResult result = confirm
                ? assetUpsertProposalService.confirm(request, getUserId())
                : assetUpsertProposalService.reject(request, getUserId());
        return AjaxResult.success(result);
    }

    /**
     * 流式设计：SSE 推送 token、tool 事件与最终 TestFlowDesignResult。
     * 事件 data 为 JSON：type=token|thinking|tool_start|tool_end|done|error。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping(value = "/design/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter designStream(@RequestBody TestFlowDesignRequest request) {
        validateDesignAccess(request);
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
        Thread worker = new Thread(() -> {
            try {
                TestFlowDesignResult result = testFlowDesignAgent.design(request, userId, listener);
                sendStreamEvent(emitter, Map.of("type", "done", "result", result));
                emitter.complete();
            } catch (Exception e) {
                String message = e instanceof LlmClientException ? e.getMessage() : "AI 助手请求失败";
                try {
                    sendStreamEvent(emitter, Map.of("type", "error", "message", message));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        worker.setName("test-flow-ai-design-stream");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    /** 校验当前用户为项目成员，且 testFlowId 属于 testProjectId */
    private void validateDesignAccess(TestFlowDesignRequest request) {
        testFlowDesignAccessValidator.validateMemberAndFlow(request);
    }

    private static void sendStreamEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event()
                    .data(JSON.toJSONString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new LlmClientException("SSE 发送失败", e);
        }
    }
}
