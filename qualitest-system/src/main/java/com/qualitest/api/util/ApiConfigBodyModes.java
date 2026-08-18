package com.qualitest.api.util;

/**
 * 判断请求体 body.mode。
 * urlencoded 认 x-www-form-urlencoded 和简写 urlencoded。
 * form-data 认 form-data、formdata、multipart。
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

    /**
     * 是否为 multipart / form-data 请求体。
     * 接受 form-data、formdata、multipart，大小写不敏感；空串返回 false。
     */
    public static boolean isFormData(String mode) {
        if (mode == null || mode.isBlank()) {
            return false;
        }
        String normalized = mode.trim();
        return "form-data".equalsIgnoreCase(normalized)
                || "formdata".equalsIgnoreCase(normalized)
                || "multipart".equalsIgnoreCase(normalized);
    }
}
