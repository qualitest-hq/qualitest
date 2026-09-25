package com.qualitest.flow.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 旧版探活再登录图画布迁移。
 * <p>
 * 识别探活 HTTP 节点及其后的 Conditon：状态码白名单补上 200/401/403，
 * IF 分支补上「实际响应符合接口期望」条件。认不出探活骨架则原样返回。
 */
public final class ProbeLoginGraphMigrateSupport {

    private ProbeLoginGraphMigrateSupport() {
    }

    /**
     * 迁移一张测试流 graphJson。
     *
     * @param graphJson 画布 JSON 全文
     * @return 迁移后的全文；无需改动或无法解析时返回入参本身
     */
    public static String migrate(String graphJson) {
        if (StrUtil.isBlank(graphJson)) {
            return graphJson;
        }
        JSONObject root;
        try {
            Object parsed = JSON.parse(graphJson);
            if (!(parsed instanceof JSONObject obj)) {
                return graphJson;
            }
            root = obj;
        } catch (Exception e) {
            return graphJson;
        }
        JSONArray nodes = root.getJSONArray("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return graphJson;
        }
        boolean changed = false;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null || !"http".equals(node.getString("type"))) {
                continue;
            }
            JSONObject data = node.getJSONObject("data");
            if (data == null || !isProbeHttp(data)) {
                continue;
            }
            if (upgradeProbeStatusCheck(data)) {
                changed = true;
            }
            JSONObject alive = findAliveCondition(nodes, root.getJSONArray("edges"), node.getString("id"));
            if (alive != null && upgradeAliveCondition(alive)) {
                changed = true;
            }
        }
        return changed ? root.toJSONString() : graphJson;
    }

    /**
     * 判断是否为探活 HTTP 节点：业务码校验关闭，且状态码白名单含 200。
     *
     * @param data 节点 data
     * @return 是探活节点时 true
     */
    static boolean isProbeHttp(JSONObject data) {
        JSONObject success = data.getJSONObject("successCheck");
        if (success == null || !"off".equalsIgnoreCase(success.getString("mode"))) {
            return false;
        }
        JSONObject statusCheck = data.getJSONObject("statusCheck");
        if (statusCheck == null || !"whitelist".equalsIgnoreCase(statusCheck.getString("mode"))) {
            return false;
        }
        JSONArray values = statusCheck.getJSONArray("values");
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (Integer.valueOf(200).equals(values.getInteger(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 探活状态码白名单补齐 200、401、403，已有其它码保留。
     *
     * @param data 探活节点 data
     * @return 白名单有改动时 true
     */
    static boolean upgradeProbeStatusCheck(JSONObject data) {
        JSONObject statusCheck = data.getJSONObject("statusCheck");
        if (statusCheck == null) {
            return false;
        }
        JSONArray values = statusCheck.getJSONArray("values");
        Set<Integer> set = new LinkedHashSet<>();
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                Integer v = values.getInteger(i);
                if (v != null) {
                    set.add(v);
                }
            }
        }
        List<Integer> before = new ArrayList<>(set);
        set.add(200);
        set.add(401);
        set.add(403);
        List<Integer> after = new ArrayList<>(set);
        if (before.equals(after)) {
            return false;
        }
        statusCheck.put("values", after);
        return true;
    }

    /**
     * 按边找到探活节点下游的 Conditon 节点。
     *
     * @param nodes       全部节点
     * @param edges       全部边
     * @param probeNodeId 探活节点 id
     * @return Conditon 节点；找不到返回 null
     */
    static JSONObject findAliveCondition(JSONArray nodes, JSONArray edges, String probeNodeId) {
        if (edges == null || StrUtil.isBlank(probeNodeId)) {
            return null;
        }
        String targetId = null;
        for (int i = 0; i < edges.size(); i++) {
            JSONObject e = edges.getJSONObject(i);
            if (e != null && probeNodeId.equals(e.getString("source"))) {
                targetId = e.getString("target");
                break;
            }
        }
        if (targetId == null) {
            return null;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            if (n != null && targetId.equals(n.getString("id")) && "condition".equals(n.getString("type"))) {
                return n;
            }
        }
        return null;
    }

    /**
     * 在 Conditon 的 IF 分支上增加「http.expectedMatch 等于 true」；已有则跳过。
     *
     * @param condNode Conditon 节点
     * @return 有改动时 true
     */
    static boolean upgradeAliveCondition(JSONObject condNode) {
        JSONObject data = condNode.getJSONObject("data");
        if (data == null) {
            return false;
        }
        JSONArray branches = data.getJSONArray("branches");
        if (branches == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            if (branch == null || !"if".equals(branch.getString("kind"))) {
                continue;
            }
            JSONArray conditions = branch.getJSONArray("conditions");
            if (conditions == null) {
                conditions = new JSONArray();
                branch.put("conditions", conditions);
            }
            if (!hasExpectedMatch(conditions)) {
                JSONObject c = new JSONObject();
                c.put("left", "http.expectedMatch");
                c.put("operator", "eq");
                c.put("right", "true");
                conditions.add(c);
                changed = true;
            }
        }
        return changed;
    }

    /** 条件列表里是否已有 expectedMatch 左值 */
    private static boolean hasExpectedMatch(JSONArray conditions) {
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject c = conditions.getJSONObject(i);
            if (c != null && "http.expectedMatch".equals(c.getString("left"))) {
                return true;
            }
        }
        return false;
    }
}
