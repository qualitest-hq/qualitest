package com.qualitest.ai.tools.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.support.TestProjectAssetSupport;

import java.util.List;
import java.util.Map;

/**
 * 项目素材库「安全视图」组装：只暴露 key、备注、子字段名与占位提示，不带字段明文值。
 * 供 AI 工具返回给模型，避免口令等进入对话上下文。
 */
public final class AssetVariablesListingSupport {

    /** 单次列举最多返回的素材条数，超出则 truncated=true */
    public static final int MAX_ITEMS = 200;

    private AssetVariablesListingSupport() {
    }

    /**
     * 解析项目里的 asset_variables JSON，生成列举结果。
     * 返回体含 items（安全摘要数组）与 truncated（是否因条数上限截断）。
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
            items.add(toSafeItem(entry));
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 将单条素材转为安全摘要对象。
     * 字段：key、remark（有则带）、fields（仅字段名）、placeholderHint（如 {{asset.clientAuth.字段名}}）。
     */
    public static JSONObject toSafeItem(TestProjectAsset entry) {
        String key = entry.getKey().trim();
        JSONObject item = new JSONObject();
        item.put("key", key);
        if (entry.getRemark() != null && !entry.getRemark().isBlank()) {
            item.put("remark", entry.getRemark().trim());
        }
        item.put("fields", extractFieldKeys(entry));
        item.put("placeholderHint", "{{asset." + key + ".<field>}}");
        return item;
    }

    /**
     * 取出素材条目的子字段名列表（不含值）。
     * 常规落盘结构：assets 下以本条 key 为名的一层 object，其属性名即为字段；
     * 若无该包装层，则退回 assets 顶层键名。
     */
    public static JSONArray extractFieldKeys(TestProjectAsset entry) {
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

    /** 把键名去空白后追加到 fields；空名跳过 */
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
