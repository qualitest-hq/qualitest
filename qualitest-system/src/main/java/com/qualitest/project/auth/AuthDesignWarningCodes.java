package com.qualitest.project.auth;

/**
 * 设计期鉴权相关错误的稳定机器码。
 * <p>
 * 写入 errors / issues 字符串列表，格式固定为「CODE: 人类可读文案」。
 * CODE 取自第一个「: 」之前的片段，勿用中文关键词匹配类型。
 * <ul>
 *   <li>AUTH_LOGIN_FLOWKEY_COLLISION — 两套不同登录口写出同一占位路径</li>
 * </ul>
 * 图上是否已写出鉴权头所用路径不再做硬拦（缺路径交给真跑占位符）。
 */
public final class AuthDesignWarningCodes {

    /** 两套不同登录口写出同一个占位路径 */
    public static final String LOGIN_FLOWKEY_COLLISION = "AUTH_LOGIN_FLOWKEY_COLLISION";

    /** 机器码与文案之间的分隔符 */
    public static final String CODE_SEP = ": ";

    private AuthDesignWarningCodes() {}

    /**
     * 生成「两端登录写出同一占位路径」错误文案。
     *
     * @param displayPath 被两套登录口同时写出的占位路径
     */
    public static String loginFlowKeyCollision(String displayPath) {
        return LOGIN_FLOWKEY_COLLISION + CODE_SEP
                + "图中至少两套不同登录口都写出了 " + pathOrFallback(displayPath)
                + "；各端请使用托管头对应的不同占位路径，禁止互相覆盖";
    }

    private static String pathOrFallback(String displayPath) {
        return displayPath != null && !displayPath.isBlank() ? displayPath.trim() : "占位路径";
    }
}
