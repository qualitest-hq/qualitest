package com.qualitest.project.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 测试项目跑流并发模式，存于 {@code test_project.run_concurrency_mode}。
 * <ul>
 *   <li>{@link #PARALLEL}：多条 Run 可同时执行，不占用环境串行槽</li>
 *   <li>{@link #SERIAL_BY_ENV}：同一测试环境同时只跑一条，其余进入该环境排队（项目默认）</li>
 * </ul>
 */
public enum RunConcurrencyMode {

    /** 允许多条 Run 同时执行 */
    PARALLEL("parallel"),

    /** 同一测试环境同时只跑一条，其余排队（默认） */
    SERIAL_BY_ENV("serial_by_env");

    private final String code;

    RunConcurrencyMode(String code) {
        this.code = code;
    }

    /** 落库 / 接口使用的字符串码 */
    public String getCode() {
        return code;
    }

    /** 是否与给定原始字符串（忽略大小写与首尾空白）匹配 */
    public boolean matches(String raw) {
        return code.equalsIgnoreCase(StrUtil.trimToEmpty(raw));
    }

    /**
     * 解析模式码。
     * 空串、未知值一律回落为按环境串行，避免误开并行。
     */
    public static RunConcurrencyMode fromCode(String raw) {
        if (PARALLEL.matches(raw)) {
            return PARALLEL;
        }
        return SERIAL_BY_ENV;
    }
}
