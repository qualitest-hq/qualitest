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
 * AI 造流写工具：按 profileId 浅合并更新多端 Profile，或 create=true 时新建。
 * <p>
 * patch 可改：name、pathPrefix、鉴权托管头、responseConvention（响应约定四字段）。
 * 半自动：须有提案捕获器；只记 pending 提案，用户确认后才写 auth_config。<br>
 * 全自动：工具内直接写库，提案 status 为 confirmed；捕获器可为空。
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
     * <p>
     * 半自动缺少捕获器时直接失败；全自动允许捕获器为空并立即写库。
     *
     * @param arguments 须含 patch；更新须 profileId，新建须 create=true
     * @param ctx       须含 testProjectId；半自动还须 authProfileUpsertCapture
     * @return 提案回执 JSON（profileId / action / status / changedFields / after / hint）
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持写入多端配置");
        }
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        boolean autopilot = ctx.isAutopilotEnabled();
        AuthProfileUpsertCapture capture = ctx.getAuthProfileUpsertCapture();
        // 半自动必须能暂存提案；全自动可跳过捕获器，直接落盘
        if (capture == null && !autopilot) {
            return FlowDesignToolSupport.errorJson("多端配置提案捕获器未就绪");
        }
        Map<String, Object> patch = AuthProfileUpsertSupport.parsePatch(arguments.get("patch"));
        if (patch == null) {
            return FlowDesignToolSupport.errorJson(
                    "缺少 patch 对象（可含 name/pathPrefix/headerName/headerValueTemplate/responseConvention）");
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
        ProjectAuthProfile existing = ProjectAuthConfigSupport.findProfile(config, profileId);
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
                        e.getMessage() != null ? e.getMessage() : "写入多端配置失败");
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
        if (capture != null) {
            capture.record(proposal);
        }
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
            result.put("hint", "已写入多端配置 Profile");
        } else {
            result.put("hint", "提案待聊天侧确认后落盘；确认前画布仍用旧 Profile");
        }
        return ToolResultByteFit.fitAck(result, maxBytes);
    }
}
