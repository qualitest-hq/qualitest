package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * AI / 设计 patch 落图前，规范化 condition 节点：
 * 空/null {@code branches} → 默认 IF + ELSE（与 UI {@code defaultConditionBranches} 对齐），
 * 再规范化各分支 conditions 的运算符与左值方言。
 */
public final class FlowDesignConditionNodeNormalizer {

    private FlowDesignConditionNodeNormalizer() {
    }

    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        ensureDefaultBranches(data);
        FlowDesignAssertNodeNormalizer.normalizeConditionBranches(data);
    }

    /** 缺 branches 时写入一条 IF + 一条 ELSE。 */
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
