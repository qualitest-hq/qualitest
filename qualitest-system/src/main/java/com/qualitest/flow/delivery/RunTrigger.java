package com.qualitest.flow.delivery;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

/**
 * 运行触发源（写入 {@code run.trigger} 与运行记录的 triggerType），表示谁发起了这次运行。
 * <p>
 * 与入口种类 entryKind 正交：同一种入口可以由不同触发源发起。
 */
public enum RunTrigger {

    /** 画布人手点跑 */
    UI("ui", "画布"),
    /** CI 管线 */
    CI("ci", "CI"),
    /** 套件串跑 */
    SUITE("suite", "套件"),
    /** 开放 HTTP 入站 */
    HTTP("http", "开放 HTTP"),
    /** MCP / AI 跑流工具 */
    MCP("mcp", "MCP"),
    /** 父流子流节点调起 */
    SUBFLOW("subflow", "子流");

    /** 稳定短名，用于落库与运行上下文 */
    private final String code;
    /** 展示名，用于报告与界面 */
    private final String name;

    /**
     * 构造触发源枚举项。
     *
     * @param code 稳定短名
     * @param name 展示名
     */
    RunTrigger(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** 获取稳定短名（落库 / 上下文） */
    public String getCode() {
        return code;
    }

    /** 获取展示名 */
    public String getName() {
        return name;
    }

    /**
     * 规范化触发源：空白按 ui 处理，其余去首尾空白并转小写。
     * 不校验是否为已知触发源，未知值以小写串原样返回。
     *
     * @param raw 原始触发源文本
     * @return 规范化后的短名
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return UI.code;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 按短名解析枚举，忽略大小写与首尾空白。
     *
     * @param raw 短名文本
     * @return 对应枚举；空白或未知时返回 null
     */
    public static RunTrigger fromCode(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String c = raw.trim().toLowerCase(Locale.ROOT);
        for (RunTrigger t : values()) {
            if (t.code.equals(c)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 判断给定文本是否表示当前触发源（先规范化再比较，空白视为 ui）。
     *
     * @param raw 原始触发源文本
     * @return 是否匹配
     */
    public boolean matches(String raw) {
        return code.equals(normalize(raw));
    }

    /**
     * 取报告 / 界面展示文案。
     *
     * @param raw 原始触发源文本
     * @return 已知触发源返回展示名；空白返回 {@code -}；未知短名返回去空白后的原文
     */
    public static String displayLabel(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "-";
        }
        RunTrigger t = fromCode(raw);
        return t != null ? t.name : raw.trim();
    }

    /**
     * 取运行中心的来源归类：直接使用规范化后的短名。
     *
     * @param raw 原始触发源文本
     * @return 来源归类短名
     */
    public static String hubSource(String raw) {
        return normalize(raw);
    }
}
