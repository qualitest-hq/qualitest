package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;

/**
 * 为 AI 工具拼装接口鉴权摘要：auth（mode 等）和 headerHint（需登录时的头模板与凭证目标）。
 */
public final class AuthHeaderHintSupport {

    private AuthHeaderHintSupport() {}

    /**
     * 写入 auth；需要加鉴权头时再写 headerHint（头名、值模板、profileId、凭证目标）。
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
        CredentialTarget credentialTarget = CredentialTargetSupport.primaryTarget(profile);
        if (credentialTarget != null) {
            JSONObject ct = new JSONObject();
            ct.put("displayPath", credentialTarget.displayPath());
            ct.put("scope", credentialTarget.scope());
            if (credentialTarget.isAsset()) {
                ct.put("entryKey", credentialTarget.entryKey());
                ct.put("fieldPath", credentialTarget.fieldPath());
            } else if (credentialTarget.isFlow()) {
                ct.put("flowKey", credentialTarget.flowKey());
            }
            hint.put("credentialTarget", ct);
            // 顶层兼容：AI/前端仍可读 flowKey / displayPath
            hint.put("displayPath", credentialTarget.displayPath());
            if (credentialTarget.isFlow()) {
                hint.put("flowKey", credentialTarget.flowKey());
            }
        }
        target.put("headerHint", hint);
    }

    /**
     * 只返回精简 auth（mode，以及解析出的 authProfileId 或 override 头），给接口搜索用。
     */
    public static JSONObject compactAuth(String apiAuthJson, String projectAuthJson, String apiPath) {
        return buildAuthObject(apiAuthJson, projectAuthJson, apiPath, true);
    }

    /**
     * 组装 auth 对象。fillResolvedProfileId 为 true 且接口没写 authProfileId 时，按路径补上。
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
