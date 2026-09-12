package com.qualitest.web.controller.project;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.scenario.flow.TestFlowDesignAgent;
import com.qualitest.ai.scenario.flow.AssetUpsertProposalService;
import com.qualitest.ai.scenario.flow.FlowDesignPatchConfirmService;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionRequest;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignSavePrecheckRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignSavePrecheckResult;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignResult;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.scenario.flow.TestFlowDesignAccessValidator;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.AjaxResult;
import com.qualitest.flow.model.GraphJson;
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
 * 半自动：返回增量 patch / 素材提案，不自动保存 test_flow，不触发 Run。
 * 全自动（请求体 autopilotEnabled=true）：可经 run_test_flow 隐式写库并运行；
 * SSE 可推送 graphCommitted，前端据此清 Staging 并 reload 画布。
 */
@RestController
@RequestMapping("/project/testFlow/ai")
@AllArgsConstructor
public class TestFlowAiController extends BaseController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final TestFlowDesignAgent testFlowDesignAgent;
    private final FlowDesignPatchConfirmService flowDesignPatchConfirmService;
    private final FlowDesignPatchNormalizer flowDesignPatchNormalizer;
    private final AssetUpsertProposalService assetUpsertProposalService;
    private final ITestProjectMemberService testProjectMemberService;
    private final TestFlowDesignAccessValidator testFlowDesignAccessValidator;
    private final IAiPromptTemplateService aiPromptTemplateService;

    /**
     * AI 设计面板：查询可用的提示词模板（平台级 + 当前项目级）。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query') or @ss.hasPermi('project:testProjectTemplate:list') or @ss.hasPermi('project:testProjectTemplate:edit') or @ss.hasPermi('project:testProjectTemplate:query')")
    @GetMapping("/promptTemplates")
    public AjaxResult listPromptTemplates(
            @RequestParam(required = false) Long testProjectId,
            @RequestParam(required = false) String sessionScene) {
        if (testProjectId != null) {
            testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        }
        String scene = sessionScene != null ? sessionScene : AiChatConversationService.SCENE_TEST_FLOW_DESIGN;
        List<AiPromptTemplateResult> list = aiPromptTemplateService.listForDesignPanel(
                testProjectId != null ? testProjectId : null, scene);
        return AjaxResult.success(list);
    }

    /**
     * 确认单个 Staging 变更单元。
     * <p>
     * 内存中合并该单元并校验，不写 test_flow、不触发 Run。
     * 成功返回 graphJson；本轮已无未决单元时还可带 saveRiskWarnings（不阻断本次确认）。
     * 失败保持该单元 Staging 态，errors 供前端展示。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query') or @ss.hasPermi('project:testProjectTemplate:list') or @ss.hasPermi('project:testProjectTemplate:edit') or @ss.hasPermi('project:testProjectTemplate:query')")
    @PostMapping("/patch/confirmUnit")
    public AjaxResult confirmPatchUnit(@RequestBody FlowDesignPatchConfirmRequest request) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        FlowDesignPatchConfirmResult result = flowDesignPatchConfirmService.confirmUnit(request);
        return AjaxResult.success(result);
    }

    /**
     * 运行风险预检接口。
     * 检查鉴权凭证来源、登录抽取、HTTP 必填测值是否齐全；不写库、不阻断保存。
     * 返回错误列表供画布提示；正式开跑前会再次用同类规则拦截。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query') or @ss.hasPermi('project:testProjectTemplate:list') or @ss.hasPermi('project:testProjectTemplate:edit') or @ss.hasPermi('project:testProjectTemplate:query')")
    @PostMapping("/patch/savePrecheck")
    public AjaxResult savePrecheck(@RequestBody FlowDesignSavePrecheckRequest request) {
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        GraphJson graph = null;
        if (request.getGraphJson() != null && !request.getGraphJson().isBlank()) {
            try {
                graph = GraphJson.parse(request.getGraphJson());
            } catch (Exception e) {
                return AjaxResult.success(FlowDesignSavePrecheckResult.builder()
                        .ok(false)
                        .errors(List.of("graphJson 无法解析"))
                        .build());
            }
        }
        FlowDesignSavePrecheckResult result = flowDesignPatchNormalizer.savePrecheck(
                graph, request.getTestProjectId());
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
     * 流式设计：SSE 推送过程事件与最终结果。
     * <ul>
     *   <li>token / thinking：模型增量文本</li>
     *   <li>tool_start / tool_end：工具调用起止</li>
     *   <li>graphCommitted：全自动隐式写库成功（带 testFlowId），前端应清 Staging 并 reload</li>
     *   <li>done：整轮结束，data.result 为 TestFlowDesignResult</li>
     *   <li>error：失败文案</li>
     * </ul>
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query') or @ss.hasPermi('project:testProjectTemplate:list') or @ss.hasPermi('project:testProjectTemplate:edit') or @ss.hasPermi('project:testProjectTemplate:query')")
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

            @Override
            public void onGraphCommitted(Long testFlowId) {
                // 全自动写库成功：通知前端清 Staging 并重新加载该测试流
                if (testFlowId != null) {
                    sendStreamEvent(emitter, Map.of(
                            "type", "graphCommitted",
                            "testFlowId", String.valueOf(testFlowId)));
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
