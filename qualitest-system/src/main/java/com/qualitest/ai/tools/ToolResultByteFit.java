package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI 工具返回 JSON 的字节上限适配。
 * <p>
 * 每种结果形状用独立方法裁剪；共享的只有长度计算、截断标记、尾部删条等小原语。
 * 裁剪始终改原对象：不另起一份 JSON，不删数组键（空数组也保留）。
 * 有业务数组时尽量至少保留 1 条，避免结果退化成只有 truncated/hint。
 */
public final class ToolResultByteFit {

    private ToolResultByteFit() {}

    /** 计算 JSON 对象序列化后的 UTF-8 字节数。 */
    public static int utf8Len(JSONObject obj) {
        return obj.toJSONString().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 标记 truncated=true，并写入 hint。
     * 只保留当前这一条，不把多步文案拼在一起。
     */
    public static void markTruncated(JSONObject result, String hint) {
        result.put("truncated", true);
        if (hint == null || hint.isBlank()) {
            return;
        }
        result.put("hint", hint);
    }

    /**
     * 从数组尾部删除元素，直到剩余条数不超过 keepMin；返回删除条数。
     */
    public static int dropTail(JSONArray arr, int keepMin) {
        if (arr == null || arr.size() <= keepMin) {
            return 0;
        }
        int removed = 0;
        while (arr.size() > keepMin) {
            arr.remove(arr.size() - 1);
            removed++;
        }
        return removed;
    }

    /** 当前结果是否超过字节上限。 */
    private static boolean over(JSONObject result, int maxBytes) {
        return maxBytes > 0 && utf8Len(result) > maxBytes;
    }

    /** 上限无效时回落到平台默认工具结果字节数。 */
    private static int effectiveMax(int maxBytes) {
        return maxBytes > 0 ? maxBytes : AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;
    }

    /**
     * 适配 {@code items[]} 列表结果。
     * <p>
     * 按行内字段探测瘦身（有则裁）：去掉 envVarKeys、清空 flowDescription、去掉 auth；
     * 仍超限则从尾部减条，至少保留 1 条。
     */
    public static String fitItemsList(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        JSONArray items = result.getJSONArray("items");
        if (items == null) {
            return shrinkStringsInPlace(result, maxBytes);
        }
        // 去掉环境变量键名列表
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = items.getJSONObject(i);
            if (row != null && row.containsKey("envVarKeys") && over(result, maxBytes)) {
                row.remove("envVarKeys");
                markTruncated(result, "已省略 envVarKeys");
            }
        }
        // 清空流描述长文本
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = items.getJSONObject(i);
            if (row != null && row.containsKey("flowDescription") && over(result, maxBytes)) {
                row.put("flowDescription", "");
                markTruncated(result, "已清空 flowDescription");
            }
        }
        // 去掉鉴权摘要
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = items.getJSONObject(i);
            if (row != null && row.containsKey("auth") && over(result, maxBytes)) {
                row.remove("auth");
                markTruncated(result, "已省略 items[].auth");
            }
        }
        if (over(result, maxBytes) && items.size() > 1) {
            int n = dropTail(items, 1);
            if (n > 0) {
                markTruncated(result, "结果过大，已从尾部减少 " + n + " 条 items，请缩小范围");
            }
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配子流模板列表：先清空/裁剪项目内子流，再裁平台模板（至少留 1 条平台模板）。
     */
    public static String fitSubflowTemplates(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        JSONArray project = result.getJSONArray("projectSubflows");
        JSONArray platform = result.getJSONArray("platformTemplates");
        if (project != null) {
            while (over(result, maxBytes) && project.size() > 0) {
                project.remove(project.size() - 1);
                markTruncated(result, "结果过大，已裁剪 projectSubflows");
            }
        }
        if (platform != null) {
            while (over(result, maxBytes) && platform.size() > 1) {
                platform.remove(platform.size() - 1);
                markTruncated(result, "结果过大，已裁剪 platformTemplates");
            }
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配图拓扑摘要：超限时先清空 edges，再从尾部减少 nodes；nodeCount/edgeCount 计数字段保留。
     * 减到 1 个 node 仍超限时去掉节点 name，只留 id；不删 nodes/edges 键。
     */
    public static String fitGraphTopology(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        JSONArray edges = result.getJSONArray("edges");
        JSONArray nodes = result.getJSONArray("nodes");
        if (edges != null && over(result, maxBytes)) {
            edges.clear();
            markTruncated(result, "结果过大，已省略 edges；请用 get_node_detail 看单点");
        }
        while (nodes != null && over(result, maxBytes) && nodes.size() > 1) {
            nodes.remove(nodes.size() - 1);
            markTruncated(result, "结果过大，已从尾部减少 nodes");
        }
        if (nodes != null && over(result, maxBytes)) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node == null || !node.containsKey("name")) {
                    continue;
                }
                node.remove("name");
                markTruncated(result, "结果过大，节点已只保留 id");
                if (!over(result, maxBytes)) {
                    break;
                }
            }
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配单节点详情：去掉 position，再按序删除 data 内脚本/body/配置等大字段；
     * 仍超限则 data 只留 name/summary/testProjectApiId；再不行只返回 id/type。
     */
    public static String fitNodeDetail(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        if (!over(result, maxBytes)) {
            return result.toJSONString();
        }
        result.remove("position");
        markTruncated(result, "已省略 position");
        Object dataObj = result.get("data");
        if (dataObj instanceof Map<?, ?> map && over(result, maxBytes)) {
            JSONObject data = new JSONObject(map);
            for (String k : List.of("source", "preScript", "postScript", "requestBody", "requestConfig", "responseConfig")) {
                if (data.containsKey(k)) {
                    data.remove(k);
                    markTruncated(result, "已压缩 data." + k);
                    if (!over(result, maxBytes)) {
                        break;
                    }
                }
            }
            if (over(result, maxBytes)) {
                JSONObject slim = new JSONObject();
                if (data.get("name") != null) {
                    slim.put("name", data.get("name"));
                }
                if (data.get("summary") != null) {
                    slim.put("summary", data.get("summary"));
                }
                if (data.get("testProjectApiId") != null) {
                    slim.put("testProjectApiId", data.get("testProjectApiId"));
                }
                result.put("data", slim);
                markTruncated(result, "data 已降为关键字段，完整内容请缩小节点或分段查看");
            } else {
                result.put("data", data);
            }
        }
        if (over(result, maxBytes)) {
            JSONObject minimal = new JSONObject();
            minimal.put("id", result.get("id"));
            minimal.put("type", result.get("type"));
            minimal.put("truncated", true);
            minimal.put("hint", "节点 data 过大，仅返回 id/type；请缩小脚本/body 后再查");
            return minimal.toJSONString();
        }
        return result.toJSONString();
    }

    /**
     * 适配测试流整包记录：超限时去掉 graphJson；仍超限则清空 flowDescription。
     */
    public static String fitFlowRecord(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        if (!over(result, maxBytes)) {
            return result.toJSONString();
        }
        result.remove("graphJson");
        markTruncated(result, "graphJson 过大已省略；请用 get_graph_summary / get_subflow_detail 看拓扑");
        if (over(result, maxBytes)) {
            result.put("flowDescription", "");
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配流 meta：先去掉 scenarios 内 flowSeedKeys/remark，再从尾部减 scenarios，
     * 仍超限则清空 flowOutputNames。
     */
    public static String fitFlowMeta(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        JSONArray scenarios = result.getJSONArray("scenarios");
        if (scenarios != null) {
            for (int i = 0; i < scenarios.size() && over(result, maxBytes); i++) {
                JSONObject sc = scenarios.getJSONObject(i);
                if (sc != null) {
                    sc.remove("flowSeedKeys");
                    sc.remove("remark");
                }
            }
            while (over(result, maxBytes) && scenarios.size() > 1) {
                scenarios.remove(scenarios.size() - 1);
                markTruncated(result, "结果过大，已裁剪 scenarios");
            }
        }
        JSONArray outputs = result.getJSONArray("flowOutputNames");
        if (outputs != null && over(result, maxBytes)) {
            outputs.clear();
            markTruncated(result, "已省略 flowOutputNames");
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配运行失败现场：去掉与 failures 重复的分区数组，压缩每条 stepDetails/childSteps，
     * 再从尾部减 failures；仍超限去掉顶层展开的 stepDetails/nodeName。
     */
    public static String fitRunFailure(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        if (!over(result, maxBytes)) {
            return result.toJSONString();
        }
        // 分区列表与 failures 内容重复，超限只保留 failures 与计数
        result.remove("bizCodeFailures");
        result.remove("assertFailures");
        result.remove("otherFailures");
        markTruncated(result, "已省略分区失败列表（保留 failures 与计数）");

        JSONArray failures = result.getJSONArray("failures");
        if (failures != null) {
            for (int i = 0; i < failures.size() && over(result, maxBytes); i++) {
                JSONObject f = failures.getJSONObject(i);
                if (f == null) {
                    continue;
                }
                f.remove("childSteps");
                Object details = f.get("stepDetails");
                if (details instanceof String s && s.length() > 256) {
                    f.put("stepDetails", s.substring(0, 256));
                    f.put("stepDetailsTruncated", true);
                }
            }
            while (over(result, maxBytes) && failures.size() > 1) {
                failures.remove(failures.size() - 1);
                markTruncated(result, "结果过大，已从尾部减少 failures");
            }
        }
        if (over(result, maxBytes)) {
            result.remove("stepDetails");
            result.remove("nodeName");
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配 API 健康体检：先去掉 warning 明细字段，再从尾部减 warnings。
     */
    public static String fitApiHealth(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        JSONArray warnings = result.getJSONArray("warnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.size() && over(result, maxBytes); i++) {
                JSONObject w = warnings.getJSONObject(i);
                if (w != null) {
                    w.remove("detail");
                    w.remove("apiPath");
                    w.remove("apiName");
                }
            }
            while (over(result, maxBytes) && warnings.size() > 1) {
                warnings.remove(warnings.size() - 1);
                markTruncated(result, "结果过大，已裁剪 warnings");
            }
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配接口设计上下文：按体积从大到小删字段，再裁剪 query/path 参数数组尾部。
     */
    public static String fitApiDesignContext(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        String[] dropOrder = {
                "workbenchSnapshot",
                "responseSchemaLeaves",
                "bodySchemaLeaves",
                "bodyExample",
                "preRequestScriptPreview",
                "postRequestScriptPreview",
                "testValueSummary",
                "bodyParams",
                "headerParams"
        };
        for (String key : dropOrder) {
            if (over(result, maxBytes) && result.containsKey(key)) {
                result.remove(key);
                markTruncated(result, "已省略 " + key);
            }
        }
        for (String arrKey : List.of("queryParams", "pathParams")) {
            JSONArray arr = result.getJSONArray(arrKey);
            while (arr != null && over(result, maxBytes) && arr.size() > 1) {
                arr.remove(arr.size() - 1);
                markTruncated(result, "已裁剪 " + arrKey);
            }
        }
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 适配小回执：裁剪 designHints 数组，去掉 remark，尽量保留 ok 与业务 id。
     */
    public static String fitAck(JSONObject result, int maxBytes) {
        maxBytes = effectiveMax(maxBytes);
        if (!over(result, maxBytes)) {
            return result.toJSONString();
        }
        if (result.containsKey("designHints")) {
            JSONArray hints = result.getJSONArray("designHints");
            while (hints != null && over(result, maxBytes) && hints.size() > 0) {
                hints.remove(hints.size() - 1);
                markTruncated(result, "已裁剪 designHints");
            }
        }
        result.remove("remark");
        return shrinkStringsInPlace(result, maxBytes);
    }

    /**
     * 结构裁完仍超限：在原对象上压缩字符串，不另起 JSON、不删数组键。
     * 先缩短 hint，再把嵌套对象里过长的字符串截断（id 不截）。
     */
    private static String shrinkStringsInPlace(JSONObject result, int maxBytes) {
        if (!over(result, maxBytes)) {
            return result.toJSONString();
        }
        String hint = result.getString("hint");
        if (hint != null && !hint.isBlank()) {
            while (over(result, maxBytes) && hint.length() > 12) {
                hint = hint.substring(0, Math.max(12, hint.length() / 2));
                result.put("hint", hint);
            }
            if (over(result, maxBytes)) {
                result.put("hint", "结果过大");
            }
        }
        int cap = 64;
        while (over(result, maxBytes) && cap >= 1) {
            truncateLongStrings(result, cap);
            if (cap == 1) {
                break;
            }
            cap = Math.max(1, cap / 2);
        }
        return result.toJSONString();
    }

    /** 把对象树里长度超过 maxLen 的字符串截到 maxLen；跳过 truncated 与 id。 */
    private static void truncateLongStrings(Object node, int maxLen) {
        if (node instanceof JSONObject obj) {
            for (String key : List.copyOf(obj.keySet())) {
                if ("truncated".equals(key) || "id".equals(key)) {
                    continue;
                }
                Object v = obj.get(key);
                if (v instanceof String s && s.length() > maxLen) {
                    obj.put(key, s.substring(0, maxLen));
                } else {
                    truncateLongStrings(v, maxLen);
                }
            }
        } else if (node instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                Object v = arr.get(i);
                if (v instanceof String s && s.length() > maxLen) {
                    arr.set(i, s.substring(0, maxLen));
                } else {
                    truncateLongStrings(v, maxLen);
                }
            }
        }
    }
}
