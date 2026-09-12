package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.AssetUpsertCapture;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.ai.tools.support.AssetUpsertSupport;
import com.qualitest.ai.tools.support.AssetVariablesListingSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 key 提出新增或更新项目素材库条目。
 * <p>
 * 半自动：只把提案写入本轮捕获器，不写库；用户确认后才落盘。<br>
 * 全自动：工具内直接写库，提案状态为 confirmed。
 * 回执含 key、字段名、action、status，不含字段明文。
 */
@RequiredArgsConstructor
public class UpsertAssetVariablesTool implements QualitestTool {

    /** 用于判断项目下是否已有该 key，以及全自动时落盘 */
    private final ITestProjectAssetService testProjectAssetService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();
    }

    /**
     * 校验入参 → 查库判定新建/更新 → 半自动记提案 / 全自动落盘 → 返回无明文回执。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持写入项目素材库");
        }
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        AssetUpsertCapture capture = ctx.getAssetUpsertCapture();
        if (capture == null) {
            return FlowDesignToolSupport.errorJson("素材提案捕获器未就绪");
        }
        String key = FlowDesignToolSupport.stringArg(arguments.get("key"));
        if (key.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 key");
        }
        Map<String, Object> fields = AssetUpsertSupport.parseFlatFields(arguments.get("fields"));
        if (fields == null) {
            return FlowDesignToolSupport.errorJson("fields 须为非空对象，如 {\"mobile\":\"...\",\"password\":\"...\"}");
        }
        String remark = FlowDesignToolSupport.stringArg(arguments.get("remark"));
        if (remark.isEmpty()) {
            remark = null;
        }

        TestProjectAssetResult existing = AssetUpsertSupport.findByKeyOrNull(
                testProjectAssetService, projectId, key);
        String action = existing == null
                ? AssetUpsertProposal.ACTION_CREATED
                : AssetUpsertProposal.ACTION_UPDATED;

        boolean autopilot = ctx.isAutopilotEnabled();
        String status = AssetUpsertProposal.STATUS_PENDING;
        if (autopilot) {
            try {
                AssetUpsertSupport.persistAsset(testProjectAssetService, projectId, key, fields, remark);
            } catch (ServiceException e) {
                return FlowDesignToolSupport.errorJson(
                        e.getMessage() != null ? e.getMessage() : "全自动写入素材库失败");
            } catch (Exception e) {
                return FlowDesignToolSupport.errorJson("全自动写入素材库异常: " + e.getMessage());
            }
            status = AssetUpsertProposal.STATUS_CONFIRMED;
        }

        AssetUpsertProposal proposal = AssetUpsertProposal.builder()
                .key(key)
                .action(action)
                .remark(remark)
                .fields(fields)
                .status(status)
                .build();
        capture.record(proposal);

        return buildAck(proposal, ctx.getMaxToolResultBytes(), autopilot);
    }

    /**
     * 组装给模型的成功回执：key、字段名、备注、占位提示、action、status；不含字段明文。
     */
    private static String buildAck(AssetUpsertProposal proposal, int maxBytes, boolean autopilot) {
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put(proposal.getKey(), proposal.getFields());
        JSONObject result = AssetVariablesListingSupport.toSafeItem(TestProjectAsset.builder()
                .key(proposal.getKey())
                .remark(proposal.getRemark())
                .assets(assets)
                .build());
        result.put("action", proposal.getAction());
        result.put("status", proposal.getStatus());
        if (autopilot) {
            result.put("hint", "全自动已写入素材库；可继续 submit_* / run_test_flow");
        }
        return ToolResultByteFit.fitAck(result, maxBytes);
    }
}
