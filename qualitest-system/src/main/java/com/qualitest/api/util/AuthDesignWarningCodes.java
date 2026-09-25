package com.qualitest.api.util;

import java.util.Locale;

/**
 * 设计期鉴权相关提示/错误的稳定机器码。
 * <p>
 * 写入 warnings 或 errors 字符串列表，格式固定为「CODE: 人类可读文案」。
 * CODE 取自第一个「: 」之前的片段，勿用中文关键词匹配类型。
 * <ul>
 *   <li>AUTH_HEADER_MANAGED — 造流已自动补上托管鉴权头，仅作提示，不阻断操作</li>
 *   <li>AUTH_LOGIN_NO_BEARER — 登录/免登口剥离了误补的托管头，仅提示</li>
 *   <li>AUTH_LOGIN_FLOWKEY_COLLISION — 两套不同登录口抽出同一个凭证目标；仅运行硬拦</li>
 *   <li>AUTH_TOKEN_MISSING — 图中需要某端凭证但缺少写入来源；仅运行硬拦</li>
 * </ul>
 */
public final class AuthDesignWarningCodes {

    /** 造流已按项目鉴权配置补上托管请求头（如 Authorization），仅提示 */
    public static final String HEADER_MANAGED = "AUTH_HEADER_MANAGED";

    /** 登录口 / 免登口不需要 Bearer，已剥离误补的托管头 */
    public static final String LOGIN_NO_BEARER = "AUTH_LOGIN_NO_BEARER";

    /** 两套不同登录口抽出同一个凭证目标，应硬拦 */
    public static final String LOGIN_FLOWKEY_COLLISION = "AUTH_LOGIN_FLOWKEY_COLLISION";

    /** 需要某端登录凭证但图中找不到写入来源；运行硬拦 */
    public static final String TOKEN_MISSING = "AUTH_TOKEN_MISSING";

    /** 机器码与文案之间的分隔符 */
    public static final String CODE_SEP = ": ";

    private AuthDesignWarningCodes() {}

    /**
     * 生成「已补托管头」提示文案。
     *
     * @param nodeLabel  节点展示名
     * @param headerName 头名称，空则按 Authorization
     */
    public static String headerManaged(String nodeLabel, String headerName) {
        return headerManaged(nodeLabel, headerName, null, null);
    }

    /**
     * 生成「已补托管头」提示文案。
     * 文案末尾带 profileId；有 pathPrefix 则带上，无前缀则写「未匹配前缀/显式绑定」。
     */
    public static String headerManaged(
            String nodeLabel, String headerName, String profileId, String pathPrefix) {
        String name = headerName != null && !headerName.isBlank() ? headerName : "Authorization";
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER_MANAGED).append(CODE_SEP)
                .append("HTTP 节点「").append(nodeLabel).append("」已按项目鉴权补全 ")
                .append(name)
                .append("（托管头，Run 时随项目配置刷新");
        appendProfileMeta(sb, profileId, pathPrefix, true);
        sb.append("）");
        return sb.toString();
    }

    /**
     * 生成「登录口勿补 Bearer」提示文案。
     *
     * @param nodeLabel  节点展示名
     * @param headerName 被剥离的头名
     */
    public static String loginNoBearer(String nodeLabel, String headerName) {
        String name = headerName != null && !headerName.isBlank() ? headerName : "Authorization";
        return LOGIN_NO_BEARER + CODE_SEP
                + "HTTP 节点「" + nodeLabel + "」为登录/免登口，已去掉托管 "
                + name
                + "（本节点才产出 token，无需 Bearer）";
    }

    /**
     * 生成「两端登录抽到同一凭证目标」错误文案。
     *
     * @param displayPath 被两套登录口同时写出的凭证展示路径
     */
    public static String loginFlowKeyCollision(String displayPath) {
        return LOGIN_FLOWKEY_COLLISION + CODE_SEP
                + "图中至少两套不同登录口都抽出了 " + pathOrFallback(displayPath)
                + "；各端请使用托管头对应的不同凭证目标，禁止互相覆盖";
    }

    private static String pathOrFallback(String displayPath) {
        return displayPath != null && !displayPath.isBlank() ? displayPath.trim() : "凭证目标";
    }

    /**
     * 生成「缺凭证来源」错误文案。
     *
     * @param profileDisplayName Profile 展示名（如「客户端 Bearer」）
     * @param displayPath        缺失的凭证展示路径（如 asset.clientAuth.token、flow.token）
     */
    public static String tokenMissing(String profileDisplayName, String displayPath) {
        return tokenMissing(profileDisplayName, displayPath, null, null);
    }

    /**
     * 生成「缺凭证来源」错误文案。
     * 说明图中需要哪端凭证、缺哪条展示路径，并附上 profileId / pathPrefix 便于核对多端配置。
     *
     * @param profileDisplayName Profile 展示名（如「客户端 Bearer」）
     * @param displayPath        缺失的凭证展示路径（如 asset.clientAuth.token）
     * @param profileId          需要该凭证的 Profile id，可空
     * @param pathPrefix         解析时命中的 pathPrefix，可空
     */
    public static String tokenMissing(
            String profileDisplayName, String displayPath, String profileId, String pathPrefix) {
        String tip = wrongEndTip(profileDisplayName, displayPath);
        StringBuilder sb = new StringBuilder();
        sb.append(TOKEN_MISSING).append(CODE_SEP)
                .append("图中使用了").append(profileDisplayName)
                .append("（").append(pathOrFallback(displayPath)).append("），但未找到该凭证来源")
                .append("（HTTP extracts / assign / 子流输出 / flowSeed）；请补对应端登录抽取，勿与另一端凭证混用");
        appendProfileMeta(sb, profileId, pathPrefix, false);
        sb.append(tip);
        return sb.toString();
    }

    /**
     * 往告警文案末尾追加 profileId、pathPrefix。
     * needFallbackPathNote=true 且有 id 无前缀时，写「pathPrefix=未匹配前缀/显式绑定」。
     */
    private static void appendProfileMeta(
            StringBuilder sb, String profileId, String pathPrefix, boolean needFallbackPathNote) {
        if (profileId != null && !profileId.isBlank()) {
            sb.append("；profileId=").append(profileId.trim());
        }
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            sb.append("；pathPrefix=").append(pathPrefix.trim());
        } else if (needFallbackPathNote && profileId != null && !profileId.isBlank()) {
            sb.append("；pathPrefix=未匹配前缀/显式绑定");
        }
    }

    /**
     * 展示名像「客户端」但路径却是 adminAuth（或相反）时，追加「疑似 Profile 绑错端」提示。
     */
    static String wrongEndTip(String profileDisplayName, String displayPath) {
        if (profileDisplayName == null || displayPath == null) {
            return "";
        }
        String pn = profileDisplayName.toLowerCase(Locale.ROOT);
        String dp = displayPath.toLowerCase(Locale.ROOT);
        boolean clientProfile = pn.contains("客户端") || pn.contains("client");
        boolean adminProfile = pn.contains("管理端") || pn.contains("admin");
        boolean adminPath = dp.contains("adminauth");
        boolean clientPath = dp.contains("clientauth");
        if ((clientProfile && adminPath) || (adminProfile && clientPath)) {
            return "；疑似 Profile 绑错端，请改项目鉴权托管头";
        }
        return "";
    }
}
