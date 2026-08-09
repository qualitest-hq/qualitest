package com.qualitest.api.util;

/**
 * 设计期鉴权 soft warning 的稳定机器码。
 * <p>
 * warnings 仍是字符串列表（不改 API 契约），格式：{@code CODE: 人类可读文案}。
 * 前端按 {@link #CODE_SEP} 前的 code 筛选，勿再依赖中文关键词。
 */
public final class AuthDesignWarningCodes {

    /** 造流已按项目鉴权补上/刷新托管头 */
    public static final String HEADER_MANAGED = "AUTH_HEADER_MANAGED";

    /** 图中用到了某端 Bearer，但缺少对应 flow 变量来源 */
    public static final String TOKEN_MISSING = "AUTH_TOKEN_MISSING";

    /** code 与文案分隔符 */
    public static final String CODE_SEP = ": ";

    private AuthDesignWarningCodes() {}

    public static String headerManaged(String nodeLabel, String headerName) {
        String name = headerName != null && !headerName.isBlank() ? headerName : "Authorization";
        return HEADER_MANAGED + CODE_SEP
                + "HTTP 节点「" + nodeLabel + "」已按项目鉴权补全 "
                + name
                + "（托管头，Run 时随项目配置刷新）";
    }

    public static String tokenMissing(String profileDisplayName, String flowKey) {
        return TOKEN_MISSING + CODE_SEP
                + "图中使用了" + profileDisplayName
                + "（flow." + flowKey + "），但未找到该变量来源"
                + "（HTTP extracts / assign / 子流输出 / flowSeed）；请补对应端登录抽取，勿与另一端 token 混用";
    }
}
