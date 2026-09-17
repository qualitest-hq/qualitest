package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.AuthProfileUpsertCapture;
import com.qualitest.ai.tools.AuthProfileUpsertProposal;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.ai.tools.support.AuthProfileUpsertSupport;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 造流写工具：按 profileId 浅合并更新项目鉴权 Profile，或 create=true 时新建。
 * <p>
 * 半自动：只记 pending 提案，用户确认后才改 auth_config；
 * 全自动：工具内直接写库，提案 status 记为 confirmed。
 * 模板画布模式禁止写入。
 */
@RequiredArgsConstructor
public class UpsertAuthProfileTool implements QualitestTool {

    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPSERT_AUTH_PROFILE.getId();
    }

    /**
     * 校验入参与项目，预览合并结果后按半自动/全自动分别记提案或落盘。
     *
     * @param arguments 须含 patch；更新须 profileId，新建须 create=true
     * @param ctx       须含 testProjectId 与 authProfileUpsertCapture
     * @return 提案回执 JSON（profileId / action / status / changedFields / after / hint）
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持写入项目鉴权");
        }
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        AuthProfileUpsertCapture capture = ctx.getAuthProfileUpsertCapture();
        if (capture == null) {
            return FlowDesignToolSupport.errorJson("鉴权提案捕获器未就绪");
        }
        Map<String, Object> patch = AuthProfileUpsertSupport.parsePatch(arguments.get("patch"));
        if (patch == null) {
            return FlowDesignToolSupport.errorJson(
                    "缺少 patch 对象（可含 name/pathPrefix/headerName/headerValueTemplate/credentialApi）");
        }
        String profileId = FlowDesignToolSupport.stringArg(arguments.get("profileId"));
        boolean create = Boolean.TRUE.equals(arguments.get("create"))
                || "true".equalsIgnoreCase(FlowDesignToolSupport.stringArg(arguments.get("create")));
        if (profileId.isEmpty() && !create) {
            return FlowDesignToolSupport.errorJson("缺少 profileId；新建须 create=true");
        }

        TestProject project = testProjectMapper.selectTestProjectById(projectId);
        if (project == null) {
            return FlowDesignToolSupport.errorJson("项目不存在");
        }
        ProjectAuthConfig config = ProjectAuthConfigSupport.parse(project.getAuthConfig());
        ProjectAuthProfile existing = AuthProfileUpsertSupport.findProfile(config, profileId);
        if (existing == null && !create) {
            return FlowDesignToolSupport.errorJson("未找到 Profile id=" + profileId);
        }
        if (existing != null && create) {
            create = false;
        }

        Map<String, Object> before = AuthProfileUpsertSupport.snapshot(existing);
        ProjectAuthProfile previewBase = existing != null
                ? existing
                : ProjectAuthProfile.builder()
                .id(profileId.isEmpty() ? "new" : profileId)
                .apis(List.of())
                .build();
        ProjectAuthProfile preview = AuthProfileUpsertSupport.applyPatch(previewBase, patch);
        Map<String, Object> after = AuthProfileUpsertSupport.snapshot(preview);
        List<String> changed = AuthProfileUpsertSupport.changedFieldNames(before, after);
        if (changed.isEmpty() && existing != null) {
            return FlowDesignToolSupport.errorJson("patch 未改变任何字段");
        }

        String action = existing == null
                ? AuthProfileUpsertProposal.ACTION_CREATED
                : AuthProfileUpsertProposal.ACTION_UPDATED;
        boolean autopilot = ctx.isAutopilotEnabled();
        String status = AuthProfileUpsertProposal.STATUS_PENDING;
        String resolvedId = existing != null ? existing.getId() : profileId;

        if (autopilot) {
            try {
                FlowExternalChangeSourceHolder.set(
                        FlowExternalChangeSourceHolder.mcpOrWebAutopilot(ctx.getAiChatSessionId() != null));
                resolvedId = AuthProfileUpsertSupport.persistPatch(
                        testProjectMapper, projectId, profileId.isEmpty() ? null : profileId, create, patch);
                status = AuthProfileUpsertProposal.STATUS_CONFIRMED;
            } catch (ServiceException e) {
                return FlowDesignToolSupport.errorJson(
                        e.getMessage() != null ? e.getMessage() : "写入项目鉴权失败");
            } finally {
                FlowExternalChangeSourceHolder.clear();
            }
            after.put("id", resolvedId);
        }

        AuthProfileUpsertProposal proposal = AuthProfileUpsertProposal.builder()
                .profileId(resolvedId != null && !resolvedId.isBlank() ? resolvedId : profileId)
                .action(action)
                .status(status)
                .before(before.isEmpty() ? null : new LinkedHashMap<>(before))
                .patch(new LinkedHashMap<>(patch))
                .after(new LinkedHashMap<>(after))
                .changedFields(changed)
                .build();
        capture.record(proposal);
        return buildAck(proposal, ctx.getMaxToolResultBytes(), autopilot);
    }

    /**
     * 组装工具成功回执，并按字节上限裁剪。
     *
     * @param proposal  已记录的提案
     * @param maxBytes  回执字节上限
     * @param autopilot 是否全自动（影响 hint 文案）
     * @return JSON 字符串
     */
    private static String buildAck(AuthProfileUpsertProposal proposal, int maxBytes, boolean autopilot) {
        JSONObject result = new JSONObject();
        result.put("profileId", proposal.getProfileId());
        result.put("action", proposal.getAction());
        result.put("status", proposal.getStatus());
        result.put("changedFields", proposal.getChangedFields());
        result.put("after", proposal.getAfter());
        if (autopilot) {
            result.put("hint", "已写入项目鉴权 Profile");
        } else {
            result.put("hint", "提案待聊天侧确认后落盘；确认前画布仍用旧 Profile");
        }
        return ToolResultByteFit.fitAck(result, maxBytes);
    }
}
