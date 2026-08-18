package com.qualitest.api.util;

/**
 * 设计期鉴权相关提示/错误的稳定机器码。
 * <p>
 * 写入 warnings 或 errors 字符串列表，格式固定为「CODE: 人类可读文案」。
 * 前端按第一个「: 」之前的 CODE 识别类型，不要用中文关键词匹配。
 * <ul>
 *   <li>AUTH_HEADER_MANAGED — 造流已自动补上托管鉴权头，仅作提示，不阻断操作</li>
 *   <li>AUTH_LOGIN_NO_BEARER — 登录/免登口剥离了误补的托管头，仅提示</li>
 *   <li>AUTH_LOGIN_EXTRACT_MISSING — 登录口未抽取 token 到 flow，应阻断造流提交、确认与保存</li>
 *   <li>AUTH_LOGIN_FLOWKEY_COLLISION — 两套不同登录口抽出同一个 flow 变量，应阻断造流提交、确认与保存</li>
 *   <li>AUTH_TOKEN_MISSING — 图中需要某端 token 但缺少变量来源，应阻断造流提交、确认与保存</li>
 * </ul>
 */
public final class AuthDesignWarningCodes {

    /** 造流已按项目鉴权配置补上托管请求头（如 Authorization），仅提示 */
    public static final String HEADER_MANAGED = "AUTH_HEADER_MANAGED";

    /** 登录口 / 免登口不需要 Bearer，已剥离误补的托管头 */
    public static final String LOGIN_NO_BEARER = "AUTH_LOGIN_NO_BEARER";

    /** 登录类接口未配置写出 token 的 extracts */
    public static final String LOGIN_EXTRACT_MISSING = "AUTH_LOGIN_EXTRACT_MISSING";

    /** 两套不同登录口抽出同一个 flow 变量，应硬拦 */
    public static final String LOGIN_FLOWKEY_COLLISION = "AUTH_LOGIN_FLOWKEY_COLLISION";

    /** 需要某端登录凭证（如 flow.token）但图中找不到写入来源，应硬拦 */
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
        String name = headerName != null && !headerName.isBlank() ? headerName : "Authorization";
        return HEADER_MANAGED + CODE_SEP
                + "HTTP 节点「" + nodeLabel + "」已按项目鉴权补全 "
                + name
                + "（托管头，Run 时随项目配置刷新）";
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
     * 生成「登录口缺 extract」错误文案。
     *
     * @param nodeLabel 节点展示名
     * @param flowKey   应收录的 flow 变量名
     */
    public static String loginExtractMissing(String nodeLabel, String flowKey) {
        return LOGIN_EXTRACT_MISSING + CODE_SEP
                + "HTTP 节点「" + nodeLabel + "」为登录口，但未抽取 flow." + flowKeyOrToken(flowKey)
                + "（请按该端 loginHint / 响应结构补 extracts）";
    }

    /**
     * 生成「登录抽取路径与 loginHint/schema 不一致」错误文案。
     *
     * @param nodeLabel     节点展示名
     * @param flowKey       应收录的 flow 变量名
     * @param expectedExpr  期望 JsonPath
     * @param actualExpr    当前 JsonPath，可空
     */
    public static String loginExtractExprMismatch(
            String nodeLabel, String flowKey, String expectedExpr, String actualExpr) {
        String expected = expectedExpr != null ? expectedExpr.trim() : "";
        String actual = actualExpr != null && !actualExpr.isBlank() ? actualExpr.trim() : "空";
        return LOGIN_EXTRACT_MISSING + CODE_SEP
                + "HTTP 节点「" + nodeLabel + "」登录抽取路径应为 "
                + expected + " → " + flowKeyOrToken(flowKey)
                + "，当前为 " + actual;
    }

    private static String flowKeyOrToken(String flowKey) {
        return flowKey != null && !flowKey.isBlank() ? flowKey.trim() : "token";
    }

    /**
     * 生成「两端登录抽到同一 flow 变量」错误文案。
     *
     * @param flowKey 被两套登录口同时写出的变量名
     */
    public static String loginFlowKeyCollision(String flowKey) {
        return LOGIN_FLOWKEY_COLLISION + CODE_SEP
                + "图中至少两套不同登录口都抽出了 flow." + flowKeyOrToken(flowKey)
                + "；客户端请用 token，管理端请用 adminToken，禁止互相覆盖";
    }

    /**
     * 生成「缺 token 来源」错误文案。
     *
     * @param profileDisplayName Profile 展示名（如「客户端 Bearer」）
     * @param flowKey            缺失的 flow 变量名（如 token、adminToken）
     */
    public static String tokenMissing(String profileDisplayName, String flowKey) {
        return TOKEN_MISSING + CODE_SEP
                + "图中使用了" + profileDisplayName
                + "（flow." + flowKey + "），但未找到该变量来源"
                + "（HTTP extracts / assign / 子流输出 / flowSeed）；请补对应端登录抽取，勿与另一端 token 混用";
    }
}
