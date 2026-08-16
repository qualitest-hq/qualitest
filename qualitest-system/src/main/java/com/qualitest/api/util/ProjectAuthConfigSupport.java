package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 项目鉴权配置：解析、判空、通用/双端模板、按路径解析 Profile、写入规范化。
 */
public final class ProjectAuthConfigSupport {

    /** 项目级上传空配置时的通用种子 id（单套 Bearer，不假定双端）。 */
    public static final String PROFILE_DEFAULT = "defaultBearer";

    /** demo / 商城双端模板用：客户端。 */
    public static final String PROFILE_CLIENT = "clientBearer";

    /** demo / 商城双端模板用：管理端。 */
    public static final String PROFILE_ADMIN = "adminBearer";

    /** 空配置落库 JSON（无 Profile；Mapper 需非 null 才能清空列）。 */
    public static final String EMPTY_JSON = "{}";

    /** loginHint.from 允许值。 */
    private static final Set<String> LOGIN_HINT_FROM =
            Set.of("body", "setCookie", "header");

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

    /**
     * 规范化用户提交的鉴权配置并序列化为 JSON，用于写库。
     * <p>
     * 空白、或无 Profile 且无匿名 path → {@link #EMPTY_JSON}；
     * 非法 JSON / 规则不通过 → {@link ServiceException}。
     */
    public static String normalizeToJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return EMPTY_JSON;
        }
        ProjectAuthConfig parsed;
        try {
            parsed = JSONUtil.toBean(raw.trim(), ProjectAuthConfig.class);
        } catch (Exception e) {
            throw new ServiceException("项目鉴权配置不是合法 JSON");
        }
        if (parsed == null) {
            return EMPTY_JSON;
        }
        ProjectAuthConfig normalized = normalize(parsed);
        if (isFullyEmpty(normalized)) {
            return EMPTY_JSON;
        }
        return toJson(normalized);
    }

    /**
     * 校验并清理配置对象（Web 保存 / 服务端写库共用）。
     */
    public static ProjectAuthConfig normalize(ProjectAuthConfig input) {
        if (input == null) {
            return empty();
        }
        List<ProjectAuthProfile> profiles = normalizeProfiles(input.getAuthProfiles());
        List<String> exact = normalizeExactPaths(input.getAnonymousPathExact());
        List<String> prefix = normalizePrefixPaths(input.getAnonymousPathPrefix(), "anonymousPathPrefix");

        String defaultId = null;
        if (!profiles.isEmpty()) {
            defaultId = StrUtil.trimToNull(input.getDefaultProfileId());
            if (defaultId == null) {
                defaultId = profiles.get(0).getId();
            } else {
                final String want = defaultId;
                boolean found = profiles.stream().anyMatch(p -> want.equals(p.getId()));
                if (!found) {
                    throw new ServiceException("defaultProfileId 不在 authProfiles 中: " + defaultId);
                }
            }
        }
        return ProjectAuthConfig.builder()
                .defaultProfileId(defaultId)
                .authProfiles(profiles)
                .anonymousPathExact(exact)
                .anonymousPathPrefix(prefix)
                .build();
    }

    /** 无 Profile 且无匿名 path。 */
    private static boolean isFullyEmpty(ProjectAuthConfig config) {
        return isEmpty(config)
                && (config.getAnonymousPathExact() == null || config.getAnonymousPathExact().isEmpty())
                && (config.getAnonymousPathPrefix() == null || config.getAnonymousPathPrefix().isEmpty());
    }

    private static List<ProjectAuthProfile> normalizeProfiles(List<ProjectAuthProfile> raw) {
        List<ProjectAuthProfile> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            ProjectAuthProfile profile = raw.get(i);
            if (profile == null) {
                continue;
            }
            String id = StrUtil.trimToNull(profile.getId());
            if (id == null) {
                throw new ServiceException("authProfiles[" + i + "].id 不能为空");
            }
            if (!ids.add(id)) {
                throw new ServiceException("authProfiles.id 重复: " + id);
            }
            Header header = profile.getHeader();
            String headerName = header != null ? StrUtil.trimToNull(header.getName()) : null;
            String valueTemplate = header != null ? StrUtil.trimToNull(header.getValueTemplate()) : null;
            if (headerName == null || valueTemplate == null) {
                throw new ServiceException("Profile「" + id + "」须配置 header.name 与 header.valueTemplate");
            }
            List<String> pathPrefixes = null;
            if (profile.getMatch() != null && profile.getMatch().getPathPrefix() != null) {
                pathPrefixes = normalizePrefixPaths(profile.getMatch().getPathPrefix(),
                        "Profile「" + id + "」.match.pathPrefix");
            }
            Match match = (pathPrefixes != null && !pathPrefixes.isEmpty())
                    ? Match.builder().pathPrefix(pathPrefixes).build()
                    : null;
            out.add(ProjectAuthProfile.builder()
                    .id(id)
                    .name(StrUtil.trimToNull(profile.getName()))
                    .match(match)
                    .header(Header.builder().name(headerName).valueTemplate(valueTemplate).build())
                    .loginHint(normalizeLoginHint(profile.getLoginHint(), id))
                    .build());
        }
        return out;
    }

    private static LoginHint normalizeLoginHint(LoginHint hint, String profileId) {
        if (hint == null) {
            return null;
        }
        String flowKey = StrUtil.trimToNull(hint.getFlowKey());
        String from = StrUtil.trimToNull(hint.getFrom());
        String expr = StrUtil.trimToNull(hint.getExpr());
        String legacy = StrUtil.trimToNull(hint.getExtractJsonPath());
        if (from == null && legacy != null) {
            from = "body";
            if (expr == null) {
                expr = legacy;
            }
        }
        if (from != null) {
            String canonical = canonicalLoginFrom(from);
            if (canonical == null) {
                throw new ServiceException(
                        "Profile「" + profileId + "」.loginHint.from 仅支持 body / setCookie / header");
            }
            from = canonical;
        }
        if (flowKey == null && from == null && expr == null) {
            return null;
        }
        return LoginHint.builder()
                .flowKey(flowKey)
                .from(from)
                .expr(expr)
                .build();
    }

    /** 将 from 规范为允许值；不识别则返回 null。 */
    private static String canonicalLoginFrom(String from) {
        for (String allowed : LOGIN_HINT_FROM) {
            if (allowed.equalsIgnoreCase(from)) {
                return allowed;
            }
        }
        return null;
    }

    private static List<String> normalizeExactPaths(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            if (StrUtil.isBlank(item)) {
                continue;
            }
            out.add(normalizeApiPath(item.trim()));
        }
        return out;
    }

    /**
     * 规范化前缀列表；空白跳过；值为 {@code /} 时拒绝（禁止根匹配）。
     */
    private static List<String> normalizePrefixPaths(List<String> raw, String fieldLabel) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            if (StrUtil.isBlank(item)) {
                continue;
            }
            String trimmed = item.trim();
            if ("/".equals(trimmed)) {
                throw new ServiceException(fieldLabel + " 禁止使用 \"/\"（须写具体前缀，如 /api/）");
            }
            // 落库保留用户写法，仅保证有前导 /
            String stored = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
            out.add(stored);
        }
        return out;
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
     * 通用 Bearer 种子（项目级上传且 auth_config 为空时写入）。
     * <p>
     * 仅一套 Profile、无 pathPrefix 分端；匿名 path 写入内置免登启发式（/login 等），
     * 避免登录口被 inherit 成 Bearer。双端切分仍属项目特定，需手工或另贴 demo 模板。
     */
    public static ProjectAuthConfig defaultBearerTemplate() {
        return ProjectAuthConfig.builder()
                .defaultProfileId(PROFILE_DEFAULT)
                .authProfiles(List.of(
                        ProjectAuthProfile.builder()
                                .id(PROFILE_DEFAULT)
                                .name("Bearer")
                                .header(Header.builder()
                                        .name("Authorization")
                                        .valueTemplate("Bearer {{flow.token}}")
                                        .build())
                                .loginHint(LoginHint.builder()
                                        .flowKey("token")
                                        .from("body")
                                        .expr("$.token")
                                        .build())
                                .build()
                ))
                .anonymousPathExact(List.copyOf(BUILTIN_ANONYMOUS_AUTH_PATH_EXACT))
                .anonymousPathPrefix(List.of())
                .build();
    }

    /**
     * demo / 商城双端 Bearer 参考模板（含常用匿名 path）。
     * <p>
     * <b>不</b>作为上传自动种子；联调靶场时可手工写入项目鉴权配置。
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
                                        .from("body")
                                        .expr("$.data.token")
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
                                        .from("body")
                                        .expr("$.token")
                                        .build())
                                .build()
                ))
                .anonymousPathExact(List.copyOf(BUILTIN_ANONYMOUS_AUTH_PATH_EXACT))
                .anonymousPathPrefix(List.copyOf(DEMO_ANONYMOUS_PATH_PREFIX))
                .build();
    }

    /**
     * 内置免登鉴权路径启发式（精确，忽略尾斜杠）。
     * <p>
     * 导入/同步在 auth 为空或 inherit 时写入 mode=none；造流补头亦按此短路。
     * 与项目 {@code anonymousPath*} 叠加；用户显式 override 不改。
     * demo 双端模板的 exact 白名单与此同源，勿再维护第二份清单。
     */
    public static final List<String> BUILTIN_ANONYMOUS_AUTH_PATH_EXACT = List.of(
            "/login",
            "/register",
            "/captchaImage",
            "/api/account/auth/login",
            "/api/account/auth/register");

    /** demo 双端模板用的精确匿名 path；与 {@link #BUILTIN_ANONYMOUS_AUTH_PATH_EXACT} 同一份列表。 */
    public static final List<String> DEMO_ANONYMOUS_PATH_EXACT = BUILTIN_ANONYMOUS_AUTH_PATH_EXACT;

    /** demo 业务向白名单前缀；仅供 {@link #dualBearerTemplate()}。 */
    public static final List<String> DEMO_ANONYMOUS_PATH_PREFIX =
            List.of("/test-support/", "/swagger-ui", "/v3/api-docs");

    /**
     * 路径是否命中内置免登启发式（与项目 anonymous 配置无关）。
     */
    public static boolean matchesBuiltinAnonymousAuthPath(String apiPath) {
        String path = normalizeApiPath(apiPath);
        for (String raw : BUILTIN_ANONYMOUS_AUTH_PATH_EXACT) {
            if (path.equals(normalizeApiPath(raw))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否应按免登处理：内置启发式或项目 anonymousPath 命中。
     */
    public static boolean shouldTreatAsAnonymousAuth(String apiPath, ProjectAuthConfig config) {
        return matchesBuiltinAnonymousAuthPath(apiPath) || matchesAnonymousPath(apiPath, config);
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
        if (PROFILE_DEFAULT.equals(profileId)) {
            return "Bearer";
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
     * 登录抽取来源（与 extracts.from 对齐）。
     * 仅有旧字段 {@code extractJsonPath} 时视为 {@code body}。
     */
    public static String resolveLoginExtractFrom(LoginHint hint) {
        if (hint == null) {
            return null;
        }
        String from = StrUtil.trimToNull(hint.getFrom());
        if (from != null) {
            return from;
        }
        if (StrUtil.isNotBlank(hint.getExtractJsonPath())) {
            return "body";
        }
        return null;
    }

    /**
     * 登录抽取表达式：优先 {@code expr}；否则回退旧 {@code extractJsonPath}。
     */
    public static String resolveLoginExtractExpr(LoginHint hint) {
        if (hint == null) {
            return null;
        }
        String expr = StrUtil.trimToNull(hint.getExpr());
        if (expr != null) {
            return expr;
        }
        return StrUtil.trimToNull(hint.getExtractJsonPath());
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
