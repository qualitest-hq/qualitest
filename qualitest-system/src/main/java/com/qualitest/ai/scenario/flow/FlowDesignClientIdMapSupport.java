package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 测试流 AI 设计会话内「客户端短名 id → 画布雪花 id」映射，挂在 {@code ai_chat_session.biz_ref_json}。
 * <p>
 * 按会话隔离，避免多轮 update / 分支 target 对不上，也避免跨会话串号。
 */
public final class FlowDesignClientIdMapSupport {

    /** biz_ref_json 中的映射字段名 */
    public static final String BIZ_REF_KEY = "flowDesignClientIdMap";

    private FlowDesignClientIdMapSupport() {
    }

    /** 从 biz_ref_json 解析映射；缺省或非法时返回可变空 Map。 */
    public static Map<String, String> parseFromBizRef(String bizRefJson) {
        Map<String, String> out = new HashMap<>();
        if (bizRefJson == null || bizRefJson.isBlank()) {
            return out;
        }
        try {
            JSONObject root = JSON.parseObject(bizRefJson);
            if (root == null) {
                return out;
            }
            JSONObject map = root.getJSONObject(BIZ_REF_KEY);
            if (map == null || map.isEmpty()) {
                return out;
            }
            for (String key : map.keySet()) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                String value = map.getString(key);
                if (value != null && !value.isBlank()) {
                    out.put(key.trim(), value.trim());
                }
            }
        } catch (Exception ignored) {
            // 保持空映射
        }
        return out;
    }

    /**
     * 将 clientIdMap 写回 biz_ref_json（保留原有 testFlowId 等字段）。
     * map 为空时移除该键。
     */
    public static String writeToBizRef(String bizRefJson, Map<String, String> clientIdMap) {
        JSONObject root;
        try {
            root = bizRefJson == null || bizRefJson.isBlank()
                    ? new JSONObject()
                    : JSON.parseObject(bizRefJson);
            if (root == null) {
                root = new JSONObject();
            }
        } catch (Exception e) {
            root = new JSONObject();
        }
        if (clientIdMap == null || clientIdMap.isEmpty()) {
            root.remove(BIZ_REF_KEY);
        } else {
            JSONObject map = new JSONObject();
            for (Map.Entry<String, String> e : clientIdMap.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()
                        || e.getValue() == null || e.getValue().isBlank()) {
                    continue;
                }
                map.put(e.getKey().trim(), e.getValue().trim());
            }
            if (map.isEmpty()) {
                root.remove(BIZ_REF_KEY);
            } else {
                root.put(BIZ_REF_KEY, map);
            }
        }
        return root.toJSONString();
    }

    /**
     * 按雪花 id 集合摘除映射项（值为这些 id 的短名一并删除）。
     *
     * @return 是否有变更
     */
    public static boolean pruneBySnowflakeIds(Map<String, String> clientIdMap, Collection<String> snowflakeIds) {
        if (clientIdMap == null || clientIdMap.isEmpty()
                || snowflakeIds == null || snowflakeIds.isEmpty()) {
            return false;
        }
        java.util.Set<String> drop = new java.util.HashSet<>();
        for (String id : snowflakeIds) {
            if (id != null && !id.isBlank()) {
                drop.add(id.trim());
            }
        }
        if (drop.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<Map.Entry<String, String>> it = clientIdMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (drop.contains(e.getValue())) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /** 不可变快照，供 tool 回包。 */
    public static Map<String, String> snapshot(Map<String, String> clientIdMap) {
        if (clientIdMap == null || clientIdMap.isEmpty()) {
            return Collections.emptyMap();
        }
        return Map.copyOf(clientIdMap);
    }
}
