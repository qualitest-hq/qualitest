package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;

/**
 * 为 AI 工具拼装精简 {@code auth} / {@code headerHint}，与 {@link AuthHeaderResolver} 同一套解析。
 */
public final class AuthHeaderHintSupport {

    private AuthHeaderHintSupport() {}

    /**
     * 写入接口鉴权摘要字段。
     * <ul>
     *   <li>{@code auth}：mode + 可选 authProfileId / header</li>
     *   <li>{@code headerHint}：需登录时的头名、模板、profileId、可选 flowKey</li>
     * </ul>
     */
    public static void putAuthFields(JSONObject target, String apiAuthJson, String projectAuthJson, String apiPath) {
        if (target == null) {
            return;
        }
        target.put("auth", buildAuthObject(apiAuthJson, projectAuthJson, apiPath, false));

        ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(apiAuthJson, projectAuthJson, apiPath);
        if (resolved == null || resolved.skipped()) {
            return;
        }
        JSONObject hint = new JSONObject();
        hint.put("name", resolved.name());
        hint.put("valueTemplate", resolved.valueTemplate());
        if (StrUtil.isNotBlank(resolved.profileId())) {
            hint.put("profileId", resolved.profileId());
        }
        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(
                ProjectAuthConfigSupport.parse(projectAuthJson), resolved.profileId());
        String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
        if (StrUtil.isNotBlank(flowKey)) {
            hint.put("flowKey", flowKey);
        }
        if (profile != null && profile.getLoginHint() != null) {
            String from = ProjectAuthConfigSupport.resolveLoginExtractFrom(profile.getLoginHint());
            String expr = ProjectAuthConfigSupport.resolveLoginExtractExpr(profile.getLoginHint());
            if (StrUtil.isNotBlank(from)) {
                hint.put("from", from);
            }
            if (StrUtil.isNotBlank(expr)) {
                hint.put("expr", expr);
            }
        }
        target.put("headerHint", hint);
    }

    /**
     * search_apis 用：只回精简 auth（mode + 解析后的 authProfileId / override header），控制体积。
     */
    public static JSONObject compactAuth(String apiAuthJson, String projectAuthJson, String apiPath) {
        return buildAuthObject(apiAuthJson, projectAuthJson, apiPath, true);
    }

    /**
     * @param fillResolvedProfileId true 时若接口未写 authProfileId，按路径解析补上
     */
    private static JSONObject buildAuthObject(
            String apiAuthJson, String projectAuthJson, String apiPath, boolean fillResolvedProfileId) {
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parseOrInherit(apiAuthJson);
        JSONObject auth = new JSONObject();
        String mode = StrUtil.blankToDefault(StrUtil.trim(apiAuth.getMode()), ApiAuthConfig.MODE_INHERIT);
        auth.put("mode", mode);
        if (ApiAuthConfig.MODE_OVERRIDE.equalsIgnoreCase(mode) && apiAuth.getHeader() != null) {
            String name = StrUtil.trimToNull(apiAuth.getHeader().getName());
            String valueTemplate = StrUtil.trimToNull(apiAuth.getHeader().getValueTemplate());
            if (name != null && valueTemplate != null) {
                JSONObject header = new JSONObject();
                header.put("name", name);
                header.put("valueTemplate", valueTemplate);
                auth.put("header", header);
            }
            return auth;
        }
        String profileId = StrUtil.trimToNull(apiAuth.getAuthProfileId());
        if (profileId == null && fillResolvedProfileId) {
            ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(apiAuthJson, projectAuthJson, apiPath);
            if (resolved != null && !resolved.skipped()) {
                profileId = StrUtil.trimToNull(resolved.profileId());
            }
        }
        if (profileId != null) {
            auth.put("authProfileId", profileId);
        }
        return auth;
    }
}
