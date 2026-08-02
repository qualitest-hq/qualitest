package com.qualitest.ai.tools.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.support.TestProjectAssetSupport;

import java.util.List;
import java.util.Map;

/**
 * 项目素材库列举：仅 key / 备注 / 子字段名，不含明文值。
 * Web 画布 AI 与 API 设计 AI 共用，避免密钥进入对话上下文。
 */
public final class AssetVariablesListingSupport {

    /** 单次最多返回的素材条数 */
    public static final int MAX_ITEMS = 200;

    private AssetVariablesListingSupport() {
    }

    /**
     * 将 asset_variables JSON 转为工具返回体（items + truncated）。
     */
    public static JSONObject buildListResult(String assetVariablesJson) {
        List<TestProjectAsset> entries = TestProjectAssetSupport.parseEntries(assetVariablesJson);
        JSONArray items = new JSONArray();
        boolean truncated = false;
        for (TestProjectAsset entry : entries) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            if (items.size() >= MAX_ITEMS) {
                truncated = true;
                break;
            }
            String key = entry.getKey().trim();
            JSONObject item = new JSONObject();
            item.put("key", key);
            if (entry.getRemark() != null && !entry.getRemark().isBlank()) {
                item.put("remark", entry.getRemark().trim());
            }
            item.put("fields", extractFieldKeys(entry));
            item.put("placeholderHint", "{{asset." + key + ".<field>}}");
            items.add(item);
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 取出素材条目的子字段名。
     * 常规结构：assets 下以 key 为名的一层 object，其属性名为字段。
     */
    static JSONArray extractFieldKeys(TestProjectAsset entry) {
        JSONArray fields = new JSONArray();
        Map<String, Object> assets = entry.getAssets();
        if (assets == null || assets.isEmpty()) {
            return fields;
        }
        String wrapKey = entry.getKey();
        Object wrapped = assets.get(wrapKey);
        if (wrapped instanceof Map<?, ?> inner) {
            appendTrimmedKeys(fields, inner.keySet());
        } else {
            appendTrimmedKeys(fields, assets.keySet());
        }
        return fields;
    }

    private static void appendTrimmedKeys(JSONArray fields, Iterable<?> keys) {
        for (Object k : keys) {
            if (k == null) {
                continue;
            }
            String name = String.valueOf(k).trim();
            if (!name.isEmpty()) {
                fields.add(name);
            }
        }
    }
}
