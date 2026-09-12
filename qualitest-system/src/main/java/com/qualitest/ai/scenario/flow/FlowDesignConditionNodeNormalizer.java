package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件分支节点 data 规范化。
 * <p>
 * 缺 branches 时补默认 IF/ELSE；规范化各分支 conditions 的运算符与左值写法。
 * normalize 不删 branches[].target，以免清掉已按出边写回的值；
 * 清除模型预写的 target 用 stripBranchTargets。
 */
public final class FlowDesignConditionNodeNormalizer {

    private FlowDesignConditionNodeNormalizer() {
    }

    /**
     * 规范化 condition 节点 data：补默认分支，再整理 conditions。
     * 保留已有的 branches[].target（不在此删除）。
     */
    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        ensureDefaultBranches(data);
        FlowDesignAssertNodeNormalizer.normalizeConditionBranches(data);
    }

    /**
     * 删除 data.branches 里每一项的 target 字段。
     * 条件出口只认连线；模型预写的 target 无效，合并前先清掉，再按出边写回。
     * 若某条分支是不可变 Map，则复制为可变 Map 后再删 target。
     */
    @SuppressWarnings("unchecked")
    public static void stripBranchTargets(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object branchesRaw = data.get("branches");
        if (!(branchesRaw instanceof List<?> branches) || branches.isEmpty()) {
            return;
        }
        List<Map<String, Object>> next = new ArrayList<>(branches.size());
        boolean replacedList = false;
        for (Object item : branches) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> branch;
            try {
                branch = (Map<String, Object>) raw;
                branch.remove("target");
            } catch (UnsupportedOperationException ex) {
                branch = new HashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getKey() != null && !"target".equals(String.valueOf(e.getKey()))) {
                        branch.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                replacedList = true;
            }
            next.add(branch);
        }
        if (replacedList || next.size() != branches.size()) {
            data.put("branches", next);
        }
    }

    /**
     * data 无 branches 或 branches 为空时，写入一条 IF（默认条件 flow.code eq 0）和一条 ELSE。
     */
    public static void ensureDefaultBranches(Map<String, Object> data) {
        Object branchesRaw = data.get("branches");
        if (branchesRaw instanceof List<?> list && !list.isEmpty()) {
            return;
        }
        JSONArray branches = new JSONArray();
        JSONObject ifBranch = new JSONObject();
        ifBranch.put("id", String.valueOf(IdUtil.getSnowflakeNextId()));
        ifBranch.put("kind", "if");
        JSONArray ifConds = new JSONArray();
        JSONObject cond = new JSONObject();
        cond.put("left", "flow.code");
        cond.put("operator", "eq");
        cond.put("right", "0");
        ifConds.add(cond);
        ifBranch.put("conditions", ifConds);
        branches.add(ifBranch);

        JSONObject elseBranch = new JSONObject();
        elseBranch.put("id", String.valueOf(IdUtil.getSnowflakeNextId()));
        elseBranch.put("kind", "else");
        elseBranch.put("conditions", new JSONArray());
        branches.add(elseBranch);

        data.put("branches", branches);
    }
}
