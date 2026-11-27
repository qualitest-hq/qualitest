package com.qualitest.project.auth;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.project.auth.AuthHeaderResolver.ResolvedAuthHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * 为造流工具拼装接口鉴权摘要字段。
 * <p>
 * 写入 {@code auth}（mode 等）；需要加托管头时再写 {@code headerHint}
 * （头名、值模板、profileId、模板内占位路径）。是否免登只看接口 auth.mode。
 */
public final class AuthHeaderHintSupport {

    private AuthHeaderHintSupport() {}

    /**
     * 向目标对象写入 auth；解析后需要加托管头时再写 headerHint。
     * 接口 mode=none 时只有 auth、没有 headerHint。
     */
    public static void putAuthFields(
            JSONObject target, String apiAuthJson, String projectAuthJson, String apiPath) {
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
        // 展示用保留模板原文大小写；比对用的小写集合在 ManagedAuthHeaderApplier 内
        List<String> displayPaths = new ArrayList<>();
        for (String inner : com.qualitest.flow.context.MustacheScan.listInners(resolved.valueTemplate())) {
            if (StrUtil.isNotBlank(inner)) {
                displayPaths.add(inner.trim());
            }
        }
        if (!displayPaths.isEmpty()) {
            JSONArray arr = new JSONArray();
            arr.addAll(displayPaths);
            hint.put("placeholderPaths", arr);
            JSONObject ct = credentialTargetFromPath(displayPaths.get(0));
            if (ct != null) {
                hint.put("credentialTarget", ct);
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

    private static JSONObject buildAuthObject(
            String apiAuthJson,
            String projectAuthJson,
            String apiPath,
            boolean fillResolvedProfileId) {
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parse(apiAuthJson);
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

    /** 将 asset.x.y / flow.k 拆成旧 credentialTarget 字段，供提示词兼容。 */
    private static JSONObject credentialTargetFromPath(String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        String p = path.trim();
        int dot = p.indexOf('.');
        if (dot <= 0 || dot >= p.length() - 1) {
            return null;
        }
        String scope = p.substring(0, dot);
        String rest = p.substring(dot + 1);
        JSONObject ct = new JSONObject();
        ct.put("scope", scope);
        ct.put("displayPath", p);
        if ("asset".equals(scope)) {
            int next = rest.indexOf('.');
            if (next <= 0 || next >= rest.length() - 1) {
                return null;
            }
            ct.put("entryKey", rest.substring(0, next));
            ct.put("fieldPath", rest.substring(next + 1));
            return ct;
        }
        if ("flow".equals(scope) || "env".equals(scope)) {
            ct.put("flowKey", rest);
            return ct;
        }
        return null;
    }
}
