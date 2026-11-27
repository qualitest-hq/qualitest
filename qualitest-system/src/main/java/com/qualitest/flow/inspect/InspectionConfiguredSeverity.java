package com.qualitest.flow.inspect;

/**
 * 流检查项可配置的严重性。
 * <ul>
 *   <li>{@code ERROR}：画布提示，并在开跑时硬拦</li>
 *   <li>{@code WARNING}：仅提示，不拦开跑</li>
 *   <li>{@code OFF}：关闭该项，校验结果中不出现</li>
 * </ul>
 */
public enum InspectionConfiguredSeverity {
    ERROR,
    WARNING,
    OFF;

    /**
     * 把配置字符串解析为枚举。
     * 空白或无法识别时返回 {@code null}，由调用方决定默认或报错。
     */
    public static InspectionConfiguredSeverity fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InspectionConfiguredSeverity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
