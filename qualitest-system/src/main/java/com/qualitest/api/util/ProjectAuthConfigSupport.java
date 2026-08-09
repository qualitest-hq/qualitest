package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.List;

/**
 * 项目鉴权配置：解析、判空、双端默认模板、按路径解析 Profile。
 */
public final class ProjectAuthConfigSupport {

    public static final String PROFILE_CLIENT = "clientBearer";
    public static final String PROFILE_ADMIN = "adminBearer";

    private ProjectAuthConfigSupport() {}

    /**
     * 从库中 JSON 解析；空白或非法时返回空配置（不含 profiles）。
     */
    public static ProjectAuthConfig parse(String json) {
        if (StrUtil.isBlank(json)) {
            return empty();
        }
        try {
            ProjectAuthConfig cfg = JSONUtil.toBean(json, ProjectAuthConfig.class);
            return cfg != null ? cfg : empty();
        } catch (Exception e) {
            return empty();
        }
    }

    public static String toJson(ProjectAuthConfig config) {
        return JSONUtil.toJsonStr(config != null ? config : empty());
    }

    public static ProjectAuthConfig empty() {
        return ProjectAuthConfig.builder().authProfiles(List.of()).build();
    }

    /**
     * 是否尚未配置可用 Profile（应触发种子写入）。
     */
    public static boolean isEmpty(ProjectAuthConfig config) {
        return config == null
                || config.getAuthProfiles() == null
                || config.getAuthProfiles().isEmpty();
    }

    /**
     * 双端 Bearer 默认模板：
     * /api/ → 客户端 token；/system|/monitor|/tool|/web/ → 管理端 adminToken；其余默认管理端。
     */
    public static ProjectAuthConfig dualBearerTemplate() {
        return ProjectAuthConfig.builder()
                .defaultProfileId(PROFILE_ADMIN)
                .authProfiles(List.of(
                        ProjectAuthProfile.builder()
                                .id(PROFILE_CLIENT)
                                .name("客户端 Bearer")
                                .match(Match.builder().pathPrefix(List.of("/api/")).build())
                                .header(Header.builder()
                                        .name("Authorization")
                                        .valueTemplate("Bearer {{flow.token}}")
                                        .build())
                                .loginHint(LoginHint.builder()
                                        .flowKey("token")
                                        .extractJsonPath("$.data.token")
                                        .build())
                                .build(),
                        ProjectAuthProfile.builder()
                                .id(PROFILE_ADMIN)
                                .name("管理端 Bearer")
                                .match(Match.builder()
                                        .pathPrefix(List.of("/system/", "/monitor/", "/tool/", "/web/"))
                                        .build())
                                .header(Header.builder()
                                        .name("Authorization")
                                        .valueTemplate("Bearer {{flow.adminToken}}")
                                        .build())
                                .loginHint(LoginHint.builder()
                                        .flowKey("adminToken")
                                        .extractJsonPath("$.token")
                                        .build())
                                .build()
                ))
                .build();
    }

    /**
     * 按接口路径解析应使用的 Profile id。
     * 最长 pathPrefix 命中优先；无人命中则用 defaultProfileId。
     *
     * @return profile id，配置为空时可能为 null
     */
    public static String resolveProfileId(String apiPath, ProjectAuthConfig config) {
        if (isEmpty(config)) {
            return null;
        }
        String path = normalizeApiPath(apiPath);
        String bestId = null;
        int bestLen = -1;
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null || StrUtil.isBlank(profile.getId()) || profile.getMatch() == null) {
                continue;
            }
            List<String> prefixes = profile.getMatch().getPathPrefix();
            if (prefixes == null) {
                continue;
            }
            for (String rawPrefix : prefixes) {
                if (StrUtil.isBlank(rawPrefix) || "/".equals(rawPrefix.trim())) {
                    // 禁止用 "/" 当 match
                    continue;
                }
                String prefix = normalizePrefix(rawPrefix);
                // path+/ 再比，使 /api 也能命中前缀 /api/
                if ((path + "/").startsWith(prefix) && prefix.length() > bestLen) {
                    bestLen = prefix.length();
                    bestId = profile.getId().trim();
                }
            }
        }
        if (bestId != null) {
            return bestId;
        }
        return StrUtil.trimToNull(config.getDefaultProfileId());
    }

    /**
     * 规范化接口路径：补前导 /，去掉末尾 /（根路径除外）。
     */
    public static String normalizeApiPath(String apiPath) {
        if (StrUtil.isBlank(apiPath)) {
            return "/";
        }
        String p = apiPath.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 规范化 pathPrefix：补前导 /；若不以 / 结尾则补上，便于 startsWith 匹配段边界。
     */
    static String normalizePrefix(String prefix) {
        String p = prefix.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        return p;
    }
}
