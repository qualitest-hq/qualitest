package com.qualitest.project.report;

import com.alibaba.fastjson2.JSONObject;

/**
 * 判定测试流失败步骤所属类别。
 * <p>
 * 类别取值：bizCode（业务码失败）、assert（断言失败）、other（其余失败）。
 */
public final class RunFailureCategoryResolver {

    /** 业务码校验未通过 */
    public static final String BIZ_CODE = "bizCode";

    /** 断言节点或断言错误码失败 */
    public static final String ASSERT = "assert";

    /** 未归入业务码或断言的其它失败 */
    public static final String OTHER = "other";

    private RunFailureCategoryResolver() {
    }

    /**
     * 根据节点类型与步骤详情 JSON 判定失败类别。
     *
     * @param nodeType 节点类型（如 http、assert）
     * @param details  步骤详情 JSON，可为 null
     * @return bizCode、assert 或 other
     */
    public static String resolve(String nodeType, JSONObject details) {
        if ("assert".equals(nodeType)) {
            return ASSERT;
        }
        if (details != null) {
            JSONObject error = details.getJSONObject("error");
            String code = error != null ? error.getString("code") : null;
            if ("TF_BIZ_CODE".equals(code)) {
                return BIZ_CODE;
            }
            if ("TF_ASSERT_FAILED".equals(code)) {
                return ASSERT;
            }
            JSONObject http = details.getJSONObject("http");
            if (http != null) {
                JSONObject bizCheck = http.getJSONObject("bizCheck");
                if (bizCheck != null && Boolean.FALSE.equals(bizCheck.getBoolean("passed"))) {
                    return BIZ_CODE;
                }
            }
        }
        return OTHER;
    }

    /**
     * 失败类别的中文展示名。
     *
     * @param category 类别 code
     * @return 业务码 / 断言 / 其他
     */
    public static String label(String category) {
        if (BIZ_CODE.equals(category)) {
            return "业务码";
        }
        if (ASSERT.equals(category)) {
            return "断言";
        }
        return "其他";
    }
}
