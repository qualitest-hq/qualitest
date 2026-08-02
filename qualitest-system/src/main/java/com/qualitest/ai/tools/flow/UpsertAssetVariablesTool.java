package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.support.AssetVariablesListingSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试流 AI 工具：按 key 新增或更新项目素材库条目。
 * <p>
 * 入参为素材键名、可选备注、扁平字段对象（如 mobile / password）；
 * 服务端立即写入项目素材库。仅 Web「AI 设计」助手可调用，MCP 侧不开放。
 * 成功回执只含 key、字段名列表、动作（created / updated）与占位提示，不含字段明文值。
 */
@RequiredArgsConstructor
public class UpsertAssetVariablesTool implements QualitestTool {

    /** 项目素材库读写服务 */
    private final ITestProjectAssetService testProjectAssetService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();
    }

    /**
     * 执行写入：校验入参 → 按 key 判断新建或更新 → 落盘 → 返回无明文回执。
     *
     * @param arguments 工具入参：key（必填）、fields（必填对象）、remark（可选）
     * @param ctx       当前设计上下文（须带 testProjectId）
     * @return JSON 字符串；失败时顶层含 error
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        String key = FlowDesignToolSupport.stringArg(arguments.get("key"));
        if (key.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 key");
        }
        Map<String, Object> fields = parseFields(arguments.get("fields"));
        if (fields == null) {
            return FlowDesignToolSupport.errorJson("fields 须为非空对象，如 {\"mobile\":\"...\",\"password\":\"...\"}");
        }
        String remark = FlowDesignToolSupport.stringArg(arguments.get("remark"));
        if (remark.isEmpty()) {
            remark = null;
        }

        // assets 外层以 key 包装一层，与素材库落盘格式相同
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put(key, fields);

        TestProjectAssetResult existing = findByKeyOrNull(projectId, key);
        try {
            TestProjectAssetSaveParams.TestProjectAssetSaveParamsBuilder params = TestProjectAssetSaveParams.builder()
                    .testProjectId(projectId)
                    .key(key)
                    .assets(assets);
            TestProjectAssetResult saved;
            String action;
            if (existing == null) {
                // 项目下尚无该 key：新增
                saved = testProjectAssetService.insertTestProjectAsset(params.remark(remark).build());
                action = "created";
            } else {
                // 已有条目：按 id 更新；未传 remark 时保留原备注
                saved = testProjectAssetService.updateTestProjectAsset(params
                        .id(existing.getId())
                        .remark(remark != null ? remark : existing.getRemark())
                        .build());
                action = "updated";
            }
            return buildAck(saved, action, ctx.getMaxToolResultBytes());
        } catch (ServiceException ex) {
            return FlowDesignToolSupport.errorJson(ex.getMessage());
        }
    }

    /**
     * 按项目与 key 查询已有素材条目。
     * 查不到时返回 null（随后走新增）；其它业务异常也按「不存在」处理，由后续 insert 再报错。
     */
    private TestProjectAssetResult findByKeyOrNull(Long projectId, String key) {
        try {
            return testProjectAssetService.selectTestProjectAssetResultByKey(projectId, key);
        } catch (ServiceException ex) {
            return null;
        }
    }

    /**
     * 将工具入参 fields 转为非空扁平 Map（字段名 → 值）。
     * 非对象、空对象或有效字段名为空时返回 null。
     */
    private static Map<String, Object> parseFields(Object fieldsObj) {
        if (!(fieldsObj instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String name = String.valueOf(e.getKey()).trim();
            if (name.isEmpty()) {
                continue;
            }
            fields.put(name, e.getValue());
        }
        return fields.isEmpty() ? null : fields;
    }

    /**
     * 组装成功回执：key、字段名、备注（若有）、占位提示、action；不输出字段明文。
     */
    private static String buildAck(TestProjectAssetResult saved, String action, int maxBytes) {
        JSONObject result = AssetVariablesListingSupport.toSafeItem(TestProjectAsset.builder()
                .key(saved.getKey())
                .remark(saved.getRemark())
                .assets(saved.getAssets())
                .build());
        result.put("action", action);
        return FlowDesignToolSupport.enforceByteLimit(result, maxBytes);
    }
}
