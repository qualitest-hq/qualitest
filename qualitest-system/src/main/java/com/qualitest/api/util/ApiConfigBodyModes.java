package com.qualitest.api.util;

/**
 * 请求体 {@code body.mode} 取值判断。
 * <p>
 * 表单 urlencoded 模式的标准取值为 {@link #URLENCODED}；
 * {@link #URLENCODED_SHORT} 为流程节点内嵌配置里可能出现的简写，判断时一并视为 urlencoded。
 */
public final class ApiConfigBodyModes {

    /** body.mode 标准值：application/x-www-form-urlencoded */
    public static final String URLENCODED = "x-www-form-urlencoded";

    /** body.mode 简写值：urlencoded */
    public static final String URLENCODED_SHORT = "urlencoded";

    private ApiConfigBodyModes() {
    }

    /**
     * 判断给定 mode 是否表示 urlencoded 请求体。
     */
    public static boolean isUrlencoded(String mode) {
        return URLENCODED.equals(mode) || URLENCODED_SHORT.equals(mode);
    }
}
