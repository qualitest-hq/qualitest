package com.qualitest.ai.tools;

/**
 * AI 流程设计场景下的标识符解析工具。
 * <p>
 * 统一处理 mention id、API id、JSON 字段等来源的数字解析，解析失败返回 null 而不抛异常。
 */
public final class FlowDesignIds {

    private FlowDesignIds() {
    }

    /**
     * 将任意对象解析为 Long。
     * <p>
     * 支持 {@link Number} 与可 trim 的数字字符串；空值或非法格式返回 null。
     *
     * @param raw 原始值，常见于节点 data 或请求参数
     * @return 解析成功的 Long，否则 null
     */
    public static Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将字符串形式的 id 解析为 Long。
     * <p>
     * 用于 @ 引用、run 上下文等以字符串存储的数值 id。
     *
     * @param id 字符串 id，空白时返回 null
     * @return 解析成功的 Long，否则 null
     */
    public static Long parseLongId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
