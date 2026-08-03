package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.AssetUpsertCapture;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.support.AssetUpsertSupport;
import com.qualitest.ai.tools.support.AssetVariablesListingSupport;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试流 AI 工具：按 key 提出新增或更新项目素材库条目。
 * <p>
 * 入参：key（必填）、fields 扁平字段对象（必填）、remark（可选）。
 * 执行时只把提案写入本轮捕获器，不写素材库；用户确认后才真正落盘。
 * 仅 Web AI 设计助手可调用；回执含 key、字段名、action、status=pending，不含字段明文。
 */
@RequiredArgsConstructor
public class UpsertAssetVariablesTool implements QualitestTool {

    /** 用于判断项目下是否已有该 key，从而标记 created 或 updated */
    private final ITestProjectAssetService testProjectAssetService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();
    }

    /**
     * 校验入参 → 查库判定新建/更新 → 写入捕获器 → 返回无明文回执。
     *
     * @param arguments 工具入参
     * @param ctx       须含 testProjectId 与 assetUpsertCapture
     * @return 成功为安全摘要 JSON；失败顶层含 error
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
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

        AssetUpsertProposal proposal = AssetUpsertProposal.builder()
                .key(key)
                .action(action)
                .remark(remark)
                .fields(fields)
                .status(AssetUpsertProposal.STATUS_PENDING)
                .build();
        capture.record(proposal);

        return buildAck(proposal, ctx.getMaxToolResultBytes());
    }

    /**
     * 组装给模型的成功回执：key、字段名、备注、占位提示、action、status；不含字段明文。
     */
    private static String buildAck(AssetUpsertProposal proposal, int maxBytes) {
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put(proposal.getKey(), proposal.getFields());
        JSONObject result = AssetVariablesListingSupport.toSafeItem(TestProjectAsset.builder()
                .key(proposal.getKey())
                .remark(proposal.getRemark())
                .assets(assets)
                .build());
        result.put("action", proposal.getAction());
        result.put("status", AssetUpsertProposal.STATUS_PENDING);
        return FlowDesignToolSupport.enforceByteLimit(result, maxBytes);
    }
}
