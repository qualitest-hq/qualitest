package com.qualitest.project.report;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 解析测试流步骤详情字段 step_details。
 */
public final class StepDetailsJson {

    private StepDetailsJson() {
    }

    /**
     * 将 step_details 字符串解析为 JSON 对象。
     * 空串或非法 JSON 时返回 null。
     *
     * @param raw 原始 JSON 字符串
     * @return 解析结果，失败为 null
     */
    public static JSONObject parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
