package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.ArrayList;
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
     * 双端 Bearer 默认模板（含 demo 常用匿名 path）：
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
                .anonymousPathExact(List.copyOf(DEMO_ANONYMOUS_PATH_EXACT))
                .anonymousPathPrefix(List.copyOf(DEMO_ANONYMOUS_PATH_PREFIX))
                .build();
    }

    /** demo SecurityConfig 业务向白名单（精确）。 */
    public static final List<String> DEMO_ANONYMOUS_PATH_EXACT =
            List.of("/login", "/register", "/captchaImage");

    /** demo 业务向白名单前缀（不含纯静态资源）。 */
    public static final List<String> DEMO_ANONYMOUS_PATH_PREFIX =
            List.of("/test-support/", "/swagger-ui", "/v3/api-docs");

    /**
     * 若匿名 path 列表均为空，填入 demo 默认值（不改动已有非空配置）。
     *
     * @return true 表示发生了回填，调用方宜持久化
     */
    public static boolean fillAnonymousPathsIfAbsent(ProjectAuthConfig config) {
        if (config == null) {
            return false;
        }
        boolean exactEmpty = config.getAnonymousPathExact() == null || config.getAnonymousPathExact().isEmpty();
        boolean prefixEmpty = config.getAnonymousPathPrefix() == null || config.getAnonymousPathPrefix().isEmpty();
        if (!exactEmpty || !prefixEmpty) {
            return false;
        }
        config.setAnonymousPathExact(new ArrayList<>(DEMO_ANONYMOUS_PATH_EXACT));
        config.setAnonymousPathPrefix(new ArrayList<>(DEMO_ANONYMOUS_PATH_PREFIX));
        return true;
    }

    /**
     * 接口路径是否命中项目匿名 path（exact 全等或 prefix 段前缀）。
     */
    public static boolean matchesAnonymousPath(String apiPath, ProjectAuthConfig config) {
        if (config == null) {
            return false;
        }
        String path = normalizeApiPath(apiPath);
        if (config.getAnonymousPathExact() != null) {
            for (String raw : config.getAnonymousPathExact()) {
                if (StrUtil.isBlank(raw)) {
                    continue;
                }
                if (path.equals(normalizeApiPath(raw))) {
                    return true;
                }
            }
        }
        if (config.getAnonymousPathPrefix() != null) {
            for (String raw : config.getAnonymousPathPrefix()) {
                if (pathMatchesNormalizedPrefix(path, raw)) {
                    return true;
                }
            }
        }
        return false;
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
                String prefix = toMatchPrefix(rawPrefix);
                if (prefix == null) {
                    continue;
                }
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

    /** 规范化后的 path 是否命中配置的前缀（禁止 match=/）。 */
    static boolean pathMatchesNormalizedPrefix(String normalizedPath, String rawPrefix) {
        String prefix = toMatchPrefix(rawPrefix);
        return prefix != null && (normalizedPath + "/").startsWith(prefix);
    }

    /**
     * 将配置前缀转为可 startsWith 的规范形式；空白或 "/" 返回 null（禁止根匹配）。
     */
    static String toMatchPrefix(String rawPrefix) {
        if (StrUtil.isBlank(rawPrefix) || "/".equals(rawPrefix.trim())) {
            return null;
        }
        return normalizePrefix(rawPrefix);
    }

    /**
     * 按 id 查找 Profile；找不到返回 null。
     */
    public static ProjectAuthProfile findProfile(ProjectAuthConfig config, String profileId) {
        if (config == null || config.getAuthProfiles() == null || StrUtil.isBlank(profileId)) {
            return null;
        }
        for (ProjectAuthProfile p : config.getAuthProfiles()) {
            if (p != null && profileId.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    /**
     * Profile 展示名；无 name 时对双端默认 id 回落中文名。
     */
    public static String displayProfileName(ProjectAuthConfig config, String profileId) {
        ProjectAuthProfile profile = findProfile(config, profileId);
        if (profile != null && StrUtil.isNotBlank(profile.getName())) {
            return profile.getName().trim();
        }
        if (PROFILE_CLIENT.equals(profileId)) {
            return "客户端 Bearer";
        }
        if (PROFILE_ADMIN.equals(profileId)) {
            return "管理端 Bearer";
        }
        return StrUtil.blankToDefault(profileId, "项目鉴权");
    }

    /**
     * 登录 token 写入的 flow 变量名，只读 {@code loginHint.flowKey}。
     * <p>
     * 不从 {@code valueTemplate} 用正则猜：头模板形态不固定，且方案约定 flowKey 由 loginHint 显式声明。
     * 未配置则返回 null（分端缺 token 提示 / headerHint.flowKey 会跳过）。
     */
    public static String resolveLoginFlowKey(ProjectAuthProfile profile) {
        if (profile == null || profile.getLoginHint() == null) {
            return null;
        }
        return StrUtil.trimToNull(profile.getLoginHint().getFlowKey());
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
