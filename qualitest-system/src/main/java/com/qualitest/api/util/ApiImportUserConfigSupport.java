package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;

/**
 * 导入更新时 headers、cookies、前后置脚本的本地优先写入规则。
 * <p>
 * 这些字段通常由设计页/调试页填写。本地已有有效内容时，上传包不能覆盖；
 * 本地为空时，才采用上传包内容。
 */
public final class ApiImportUserConfigSupport {

    private ApiImportUserConfigSupport() {
    }

    /**
     * 判断 JSON 是否表示用户已配置内容。
     * null、空白、"{}"、"[]"、字面量 "null" 视为未配置。
     */
    public static boolean isNonEmptyUserJson(String json) {
        if (StrUtil.isBlank(json)) {
            return false;
        }
        String trimmed = json.trim();
        return !"{}".equals(trimmed) && !"[]".equals(trimmed) && !"null".equalsIgnoreCase(trimmed);
    }

    /**
     * 判断脚本是否已配置：trim 后仍有字符即为已配置。
     */
    public static boolean isNonEmptyUserScript(String script) {
        return StrUtil.isNotBlank(script);
    }

    /**
     * 更新导入时写入 JSON 覆盖层字段（如 headers、cookies）。
     * 本地非空：不调用 setter，保留库中值。
     * 本地为空：写入上传包；上传包也为空时写入 "{}"。
     */
    public static void applyJsonFieldOnUpdate(String localValue, String incomingValue, StringSetter setter) {
        if (isNonEmptyUserJson(localValue)) {
            return;
        }
        setter.set(isNonEmptyUserJson(incomingValue) ? incomingValue : "{}");
    }

    /**
     * 更新导入时写入脚本文本字段。
     * 本地非空：不调用 setter。
     * 本地为空：写入上传包内容（可为 null）。
     */
    public static void applyScriptFieldOnUpdate(String localValue, String incomingValue, StringSetter setter) {
        if (isNonEmptyUserScript(localValue)) {
            return;
        }
        setter.set(incomingValue);
    }

    /**
     * 覆盖层字段最终赋值回调；可在回调内做 JSON 校验后再 set 到实体。
     */
    @FunctionalInterface
    public interface StringSetter {
        void set(String value);
    }
}
