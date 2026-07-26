package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.support.TestProjectAssetSupport;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 列出项目素材库：返回 key、备注、子字段名，供模型选用 {{asset.key.field}}。
 * <p>
 * 不返回字段明文值，避免密钥进入对话上下文。
 */
@RequiredArgsConstructor
public class ListAssetVariablesTool implements ApiDesignTool {

    /** 单次最多返回的素材条数 */
    private static final int MAX_ITEMS = 200;

    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return ApiDesignToolNames.LIST_ASSET_VARIABLES.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(projectId);
        List<TestProjectAsset> entries = TestProjectAssetSupport.parseEntries(json);
        JSONArray items = new JSONArray();
        int count = 0;
        for (TestProjectAsset entry : entries) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            if (count >= MAX_ITEMS) {
                break;
            }
            JSONObject item = new JSONObject();
            item.put("key", entry.getKey().trim());
            if (entry.getRemark() != null && !entry.getRemark().isBlank()) {
                item.put("remark", entry.getRemark().trim());
            }
            item.put("fields", extractFieldKeys(entry));
            item.put("placeholderHint", "{{asset." + entry.getKey().trim() + ".<field>}}");
            items.add(item);
            count++;
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("truncated", entries.size() > MAX_ITEMS);
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }

    /**
     * 取出素材条目的子字段名。
     * 常规结构：assets 下以 key 为名的一层 object，其属性名为字段。
     */
    private static JSONArray extractFieldKeys(TestProjectAsset entry) {
        JSONArray fields = new JSONArray();
        Map<String, Object> assets = entry.getAssets();
        if (assets == null || assets.isEmpty()) {
            return fields;
        }
        String wrapKey = entry.getKey();
        Object wrapped = assets.get(wrapKey);
        if (!(wrapped instanceof Map<?, ?> inner)) {
            for (String k : assets.keySet()) {
                if (k != null && !k.isBlank()) {
                    fields.add(k.trim());
                }
            }
            return fields;
        }
        for (Object k : inner.keySet()) {
            if (k == null) {
                continue;
            }
            String name = String.valueOf(k).trim();
            if (!name.isEmpty()) {
                fields.add(name);
            }
        }
        return fields;
    }
}
