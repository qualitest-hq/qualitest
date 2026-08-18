package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 项目鉴权配置的解析、校验、写出和运行期查询。
 * <p>
 * 解析时把历史 JSON 迁成当前结构；写出只含 authProfiles（扁平头 + 预制接口）。
 * 免登：配置为空时用内置 /login 等路径；有 Profile 后只认预制口 mode=none。
 */
public final class ProjectAuthConfigSupport {

    /** 默认 Bearer 种子的 Profile id。 */
    public static final String PROFILE_DEFAULT = "defaultBearer";

    /** 客户端 Bearer 的 Profile id。 */
    public static final String PROFILE_CLIENT = "clientBearer";

    /** 管理端 Bearer 的 Profile id。 */
    public static final String PROFILE_ADMIN = "adminBearer";

    /** 无 Profile 时落库的空 JSON。 */
    public static final String EMPTY_JSON = "{}";

    /** loginHint.from 允许的取值。 */
    private static final Set<String> LOGIN_HINT_FROM =
            Set.of("body", "setCookie", "header");

    private ProjectAuthConfigSupport() {}

    /**
     * 解析库中 JSON。空白或非法返回空配置。
     * 会把历史字段迁到当前结构：拍平头、loginHint 挂到登录口、免登路径变成 none 预制口。
     */
    public static ProjectAuthConfig parse(String json) {
        if (StrUtil.isBlank(json)) {
            return empty();
        }
        try {
            ProjectAuthConfig cfg = JSONUtil.toBean(json, ProjectAuthConfig.class);
            if (cfg == null) {
                return empty();
            }
            migrateLegacyInPlace(cfg);
            return cfg;
        } catch (Exception e) {
            return empty();
        }
    }

    /**
     * 序列化成当前结构 JSON：根只有 authProfiles。
     * 不含 defaultProfileId、anonymousPath、嵌套 header、Profile 级 loginHint。
     */
    public static String toJson(ProjectAuthConfig config) {
        if (isEmpty(config)) {
            return EMPTY_JSON;
        }
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile != null) {
                profiles.add(writeProfile(profile));
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("authProfiles", profiles);
        return JSONUtil.toJsonStr(root);
    }

    /** 写出一条 Profile：id、name、match、扁平头、apis。 */
    private static Map<String, Object> writeProfile(ProjectAuthProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.getId());
        if (StrUtil.isNotBlank(profile.getName())) {
            map.put("name", profile.getName());
        }
        if (profile.getMatch() != null && profile.getMatch().getPathPrefix() != null
                && !profile.getMatch().getPathPrefix().isEmpty()) {
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("pathPrefix", profile.getMatch().getPathPrefix());
            map.put("match", match);
        }
        map.put("headerName", profile.getHeaderName());
        map.put("headerValueTemplate", profile.getHeaderValueTemplate());
        map.put("apis", writeApis(profile.getApis()));
        return map;
    }

    /** 写出预制接口列表，跳过没有 path 的项。 */
    private static List<Map<String, Object>> writeApis(List<PrefabricatedApi> apis) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (apis == null) {
            return out;
        }
        for (PrefabricatedApi api : apis) {
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(api.getApiName())) {
                row.put("apiName", api.getApiName());
            }
            row.put("apiPath", api.getApiPath());
            if (StrUtil.isNotBlank(api.getApiGroup())) {
                row.put("apiGroup", api.getApiGroup());
            }
            if (StrUtil.isNotBlank(api.getProtocolType())) {
                row.put("protocolType", api.getProtocolType());
            }
            if (StrUtil.isNotBlank(api.getApiStatus())) {
                row.put("apiStatus", api.getApiStatus());
            }
            if (api.getRequestConfig() != null) {
                row.put("requestConfig", api.getRequestConfig());
            }
            if (api.getAuthConfig() != null) {
                row.put("authConfig", writeAuthConfig(api.getAuthConfig()));
            }
            if (api.getDesignHints() != null) {
                row.put("designHints", api.getDesignHints());
            }
            out.add(row);
        }
        return out;
    }

    /** 写出接口鉴权，loginHint 只带 flowKey/from/expr。 */
    private static Map<String, Object> writeAuthConfig(ApiAuthConfig auth) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(auth.getMode())) {
            map.put("mode", auth.getMode());
        }
        if (StrUtil.isNotBlank(auth.getAuthProfileId())) {
            map.put("authProfileId", auth.getAuthProfileId());
        }
        if (auth.getHeader() != null) {
            map.put("header", auth.getHeader());
        }
        LoginHint hint = auth.getLoginHint();
        if (hint != null) {
            Map<String, Object> hintMap = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(hint.getFlowKey())) {
                hintMap.put("flowKey", hint.getFlowKey());
            }
            if (StrUtil.isNotBlank(hint.getFrom())) {
                hintMap.put("from", hint.getFrom());
            }
            if (StrUtil.isNotBlank(hint.getExpr())) {
                hintMap.put("expr", hint.getExpr());
            }
            if (!hintMap.isEmpty()) {
                map.put("loginHint", hintMap);
            }
        }
        return map;
    }

    /**
     * 校验并序列化用户提交的 JSON，用于写库。
     * 空白或没有 Profile 写成 {}；非法 JSON 或规则失败抛业务异常。
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
        if (isEmpty(normalized)) {
            return EMPTY_JSON;
        }
        return toJson(normalized);
    }

    /**
     * 校验并清理配置对象：迁完历史字段后只保留当前结构。
     */
    public static ProjectAuthConfig normalize(ProjectAuthConfig input) {
        if (input == null) {
            return empty();
        }
        migrateLegacyInPlace(input);
        List<ProjectAuthProfile> profiles = normalizeProfiles(input.getAuthProfiles());
        return ProjectAuthConfig.builder()
                .authProfiles(profiles)
                .build();
    }

    /**
     * 把历史字段迁到当前结构：拍平头、免登精确路径变成 none 口、loginHint 挂到登录口、指定默认条调到第一。
     * 不抛业务异常。
     */
    static void migrateLegacyInPlace(ProjectAuthConfig config) {
        if (config == null || config.getAuthProfiles() == null) {
            return;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            flattenHeader(profile);
        }
        moveDefaultProfileFirst(config);
        distributeExactAnonymousApis(config);
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            attachLegacyLoginHint(profile);
            profile.setHeader(null);
            profile.setLoginHint(null);
        }
        config.setDefaultProfileId(null);
        config.setAnonymousPathExact(null);
        config.setAnonymousPathPrefix(null);
    }

    /** 嵌套 header 拍平到 headerName、headerValueTemplate。 */
    private static void flattenHeader(ProjectAuthProfile profile) {
        if (profile == null) {
            return;
        }
        if (StrUtil.isBlank(profile.getHeaderName()) && profile.getHeader() != null) {
            profile.setHeaderName(StrUtil.trimToNull(profile.getHeader().getName()));
        }
        if (StrUtil.isBlank(profile.getHeaderValueTemplate()) && profile.getHeader() != null) {
            profile.setHeaderValueTemplate(StrUtil.trimToNull(profile.getHeader().getValueTemplate()));
        }
        profile.setHeaderName(StrUtil.trimToNull(profile.getHeaderName()));
        profile.setHeaderValueTemplate(StrUtil.trimToNull(profile.getHeaderValueTemplate()));
    }

    /** 把 defaultProfileId 对应的那条调到数组第一位。 */
    private static void moveDefaultProfileFirst(ProjectAuthConfig config) {
        String defaultId = StrUtil.trimToNull(config.getDefaultProfileId());
        List<ProjectAuthProfile> profiles = config.getAuthProfiles();
        if (defaultId == null || profiles == null || profiles.size() < 2) {
            return;
        }
        int idx = -1;
        for (int i = 0; i < profiles.size(); i++) {
            ProjectAuthProfile p = profiles.get(i);
            if (p != null && defaultId.equals(p.getId())) {
                idx = i;
                break;
            }
        }
        if (idx > 0) {
            ProjectAuthProfile first = profiles.remove(idx);
            profiles.add(0, first);
        }
    }

    /**
     * 把历史免登精确路径变成 path-only、mode=none 的预制口。
     * 按 pathPrefix 分到对应 Profile，未命中进第一条。历史 prefix 列表不处理。
     */
    private static void distributeExactAnonymousApis(ProjectAuthConfig config) {
        List<String> exact = config.getAnonymousPathExact();
        if (exact == null || exact.isEmpty() || config.getAuthProfiles().isEmpty()) {
            return;
        }
        for (String raw : exact) {
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String path = normalizeApiPath(raw.trim());
            if (findPrefabricatedApi(config, null, path) != null) {
                continue;
            }
            String profileId = resolveProfileIdByPrefixOnly(path, config);
            ProjectAuthProfile target = findProfile(config, profileId);
            if (target == null) {
                target = config.getAuthProfiles().get(0);
            }
            ensureApisList(target).add(PrefabricatedApi.builder()
                    .apiName(path)
                    .apiPath(path)
                    .protocolType("http")
                    .apiStatus("normal")
                    .authConfig(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build())
                    .build());
        }
    }

    /** 把 Profile 级 loginHint 挂到登录口；没有登录口则补一条 POST /login。 */
    private static void attachLegacyLoginHint(ProjectAuthProfile profile) {
        if (profile == null) {
            return;
        }
        LoginHint hint = normalizeLoginHintQuiet(profile.getLoginHint());
        if (hint == null) {
            return;
        }
        PrefabricatedApi loginApi = findLoginLikeApiOnProfile(profile);
        if (loginApi == null) {
            loginApi = PrefabricatedApi.builder()
                    .apiName("登录")
                    .apiPath("/login")
                    .apiGroup("系统.登录")
                    .protocolType("http")
                    .apiStatus("normal")
                    .requestConfig(minimalRequestConfig("POST", null))
                    .authConfig(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).loginHint(hint).build())
                    .build();
            ensureApisList(profile).add(0, loginApi);
            return;
        }
        ApiAuthConfig auth = loginApi.getAuthConfig();
        if (auth == null) {
            auth = ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build();
            loginApi.setAuthConfig(auth);
        }
        if (auth.getLoginHint() == null) {
            auth.setLoginHint(hint);
        }
        if (StrUtil.isBlank(auth.getMode())) {
            auth.setMode(ApiAuthConfig.MODE_NONE);
        }
        if (prefabricatedHttpMethod(loginApi) == null) {
            loginApi.setRequestConfig(minimalRequestConfig("POST", loginApi.getRequestConfig()));
        }
    }

    /** 在本 Profile 的 apis 里找 path 以 /login 结尾的口，没有则找 /register。 */
    private static PrefabricatedApi findLoginLikeApiOnProfile(ProjectAuthProfile profile) {
        if (profile == null || profile.getApis() == null) {
            return null;
        }
        PrefabricatedApi register = null;
        for (PrefabricatedApi api : profile.getApis()) {
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            String path = normalizeApiPath(api.getApiPath()).toLowerCase(Locale.ROOT);
            if (path.endsWith("/login")) {
                return api;
            }
            if (register == null && path.endsWith("/register")) {
                register = api;
            }
        }
        return register;
    }

    /** 确保 Profile.apis 非 null。 */
    private static List<PrefabricatedApi> ensureApisList(ProjectAuthProfile profile) {
        if (profile.getApis() == null) {
            profile.setApis(new ArrayList<>());
        }
        return profile.getApis();
    }

    /** 校验 Profile：id 非空且不重复，必须有头名称和值模板，禁止 pathPrefix=/。 */
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
            String headerName = StrUtil.trimToNull(profile.getHeaderName());
            String valueTemplate = StrUtil.trimToNull(profile.getHeaderValueTemplate());
            if (headerName == null || valueTemplate == null) {
                throw new ServiceException("Profile「" + id + "」须配置 headerName 与 headerValueTemplate");
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
                    .headerName(headerName)
                    .headerValueTemplate(valueTemplate)
                    .apis(normalizePrefabricatedApis(profile.getApis(), id))
                    .build());
        }
        return out;
    }

    /** 校验预制接口：path 去重，补默认 protocol/status，规范 auth.mode。 */
    private static List<PrefabricatedApi> normalizePrefabricatedApis(List<PrefabricatedApi> raw, String profileId) {
        List<PrefabricatedApi> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            PrefabricatedApi api = raw.get(i);
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            String path = normalizeApiPath(api.getApiPath().trim());
            String method = prefabricatedHttpMethod(api);
            String identity = (method != null ? method : "") + " " + path;
            if (!seen.add(identity)) {
                continue;
            }
            ApiAuthConfig auth = api.getAuthConfig();
            if (auth == null || StrUtil.isBlank(auth.getMode())) {
                auth = ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build();
            } else {
                String mode = ApiAuthConfigSupport.canonicalizeMode(auth.getMode());
                if (mode == null) {
                    throw new ServiceException(
                            "Profile「" + profileId + "」.apis[" + i + "].authConfig.mode 不支持: " + auth.getMode());
                }
                auth = ApiAuthConfig.builder()
                        .mode(mode)
                        .authProfileId(StrUtil.trimToNull(auth.getAuthProfileId()))
                        .header(auth.getHeader())
                        .loginHint(normalizeLoginHint(auth.getLoginHint(), profileId))
                        .build();
            }
            out.add(PrefabricatedApi.builder()
                    .apiName(StrUtil.blankToDefault(StrUtil.trimToNull(api.getApiName()), path))
                    .apiPath(path)
                    .apiGroup(StrUtil.trimToNull(api.getApiGroup()))
                    .protocolType(StrUtil.blankToDefault(StrUtil.trimToNull(api.getProtocolType()), "http"))
                    .apiStatus(StrUtil.blankToDefault(StrUtil.trimToNull(api.getApiStatus()), "normal"))
                    .requestConfig(api.getRequestConfig())
                    .authConfig(auth)
                    .designHints(api.getDesignHints())
                    .build());
        }
        return out;
    }

    /**
     * 整理 loginHint：from 只允许 body/setCookie/header；历史 extractJsonPath 当作 body+expr。
     */
    static LoginHint normalizeLoginHint(LoginHint hint, String profileId) {
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

    /** 把 from 规范成允许的小写取值，不认识则返回 null。 */
    private static String canonicalLoginFrom(String from) {
        for (String allowed : LOGIN_HINT_FROM) {
            if (allowed.equalsIgnoreCase(from)) {
                return allowed;
            }
        }
        return null;
    }

    /** 整理 pathPrefix：去空、补前导斜杠，单独的 / 拒绝。 */
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
            String stored = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
            out.add(stored);
        }
        return out;
    }

    /** 空配置：没有 Profile。 */
    public static ProjectAuthConfig empty() {
        return ProjectAuthConfig.builder().authProfiles(List.of()).build();
    }

    /**
     * 是否还没有可用 Profile。
     */
    public static boolean isEmpty(ProjectAuthConfig config) {
        return config == null
                || config.getAuthProfiles() == null
                || config.getAuthProfiles().isEmpty();
    }

    /**
     * 已有 Profile 但预制接口全空时为 true，需要提示去勾选模板。
     */
    public static boolean needsAuthTemplateHint(ProjectAuthConfig config) {
        if (isEmpty(config)) {
            return false;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile != null && profile.getApis() != null && !profile.getApis().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 通用 Bearer 种子：名称「默认 Bearer」，含登录/注册/验证码三口，均为免登。
     * 登录口从 $.token 抽到 flow.token。不含客户端 /api/account/auth 路径。
     */
    public static ProjectAuthConfig defaultBearerTemplate() {
        return ProjectAuthConfig.builder()
                .authProfiles(List.of(
                        ProjectAuthProfile.builder()
                                .id(PROFILE_DEFAULT)
                                .name("默认 Bearer")
                                .headerName("Authorization")
                                .headerValueTemplate("Bearer {{flow.token}}")
                                .apis(defaultBearerApis("token", "$.token"))
                                .build()
                ))
                .build();
    }

    /**
     * 默认 Bearer 与管理端共用的三口：POST /login、POST /register、GET /captchaImage。
     * flowKey 用来区分写入 token 还是 adminToken。
     */
    public static List<PrefabricatedApi> defaultBearerApis(String flowKey, String tokenExpr) {
        LoginHint hint = LoginHint.builder().flowKey(flowKey).from("body").expr(tokenExpr).build();
        String design = "token".equals(flowKey)
                ? "token 在 " + tokenExpr + "，不要写成 $.data.token"
                : "管理端 token 在 " + tokenExpr + " → " + flowKey + "，不要写成 $.data.token";
        List<PrefabricatedApi> apis = new ArrayList<>();
        apis.add(prefabricatedNone(
                "登录", "/login", "系统.登录", "POST",
                Map.of("username", "", "password", "", "code", "", "uuid", ""),
                hint, design));
        apis.add(prefabricatedNone(
                "注册", "/register", "系统.登录", "POST",
                Map.of("username", "", "password", ""),
                null, null));
        apis.add(prefabricatedNone(
                "验证码", "/captchaImage", "系统.登录", "GET",
                null, null, null));
        return apis;
    }

    /** 客户端两口：POST /api/account/auth/login（$.data.token→token）和 register。 */
    public static List<PrefabricatedApi> clientBearerApis() {
        LoginHint hint = LoginHint.builder().flowKey("token").from("body").expr("$.data.token").build();
        List<PrefabricatedApi> apis = new ArrayList<>();
        apis.add(prefabricatedNone(
                "登录", "/api/account/auth/login", "客户端.账号", "POST",
                Map.of("mobile", "", "password", ""),
                hint, "客户端 token 在 $.data.token，不要写成 $.token"));
        apis.add(prefabricatedNone(
                "注册", "/api/account/auth/register", "客户端.账号", "POST",
                Map.of("mobile", "", "password", ""),
                null, null));
        return apis;
    }

    /** 组装一条免登预制口。 */
    private static PrefabricatedApi prefabricatedNone(
            String apiName,
            String apiPath,
            String apiGroup,
            String method,
            Map<String, Object> bodyExample,
            LoginHint loginHint,
            String designHint) {
        ApiAuthConfig auth = ApiAuthConfig.builder()
                .mode(ApiAuthConfig.MODE_NONE)
                .loginHint(loginHint)
                .build();
        Object designHints = null;
        if (StrUtil.isNotBlank(designHint)) {
            designHints = Map.of("hints", List.of(designHint));
        }
        return PrefabricatedApi.builder()
                .apiName(apiName)
                .apiPath(apiPath)
                .apiGroup(apiGroup)
                .protocolType("http")
                .apiStatus("normal")
                .requestConfig(minimalRequestConfig(method, bodyExample))
                .authConfig(auth)
                .designHints(designHints)
                .build();
    }

    /**
     * 最小 requestConfig：补齐 version、method、空参数数组和 body。
     * 已有完整配置则只改 method；传入 example Map 则写成 json body。
     */
    public static Object minimalRequestConfig(String method, Object existingOrExample) {
        Map<String, Object> body;
        if (existingOrExample instanceof Map<?, ?> existing && existing.containsKey("configVersion")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> copied = new LinkedHashMap<>((Map<String, Object>) existing);
            if (StrUtil.isNotBlank(method)) {
                copied.put("method", method.toUpperCase(Locale.ROOT));
            }
            return copied;
        }
        if (existingOrExample instanceof Map<?, ?> example && !example.containsKey("configVersion")) {
            body = Map.of("mode", "json", "json", Map.of("example", example));
        } else if ("GET".equalsIgnoreCase(method) || existingOrExample == null) {
            body = Map.of("mode", "none");
        } else {
            body = Map.of("mode", "json", "json", Map.of("example", existingOrExample));
        }
        Map<String, Object> rc = new LinkedHashMap<>();
        rc.put("configVersion", 1);
        rc.put("method", method != null ? method.toUpperCase(Locale.ROOT) : "GET");
        rc.put("queryParams", List.of());
        rc.put("pathParams", List.of());
        rc.put("declaredHeaders", List.of());
        rc.put("body", body);
        return rc;
    }

    /**
     * 配置完全空时使用的免登路径（去尾斜杠后精确比）。
     * 有 Profile 之后不再用这份列表。
     */
    public static final List<String> BUILTIN_ANONYMOUS_AUTH_PATH_EXACT = List.of(
            "/login",
            "/register",
            "/captchaImage",
            "/api/account/auth/login",
            "/api/account/auth/register");

    /**
     * 路径是否命中内置免登列表。只给空配置兜底用。
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
     * 是否应按免登处理：配置空则 builtin；有 Profile 则只认预制 mode=none 口。
     */
    public static boolean shouldTreatAsAnonymousAuth(String method, String apiPath, ProjectAuthConfig config) {
        if (isEmpty(config)) {
            return matchesBuiltinAnonymousAuthPath(apiPath);
        }
        PrefabricatedApi api = findPrefabricatedApi(config, method, apiPath);
        if (api == null || api.getAuthConfig() == null) {
            return false;
        }
        return ApiAuthConfig.MODE_NONE.equalsIgnoreCase(StrUtil.trim(api.getAuthConfig().getMode()));
    }

    /**
     * 按 method+apiPath 查找预制接口；method 空或预制口 method 空时只比 path。
     */
    public static PrefabricatedApi findPrefabricatedApi(ProjectAuthConfig config, String method, String apiPath) {
        if (config == null || config.getAuthProfiles() == null || StrUtil.isBlank(apiPath)) {
            return null;
        }
        String path = normalizeApiPath(apiPath);
        String wantMethod = StrUtil.trimToNull(method);
        if (wantMethod != null) {
            wantMethod = wantMethod.toUpperCase(Locale.ROOT);
        }
        PrefabricatedApi pathOnly = null;
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null || profile.getApis() == null) {
                continue;
            }
            for (PrefabricatedApi api : profile.getApis()) {
                if (api == null || StrUtil.isBlank(api.getApiPath())) {
                    continue;
                }
                if (!path.equals(normalizeApiPath(api.getApiPath()))) {
                    continue;
                }
                String apiMethod = prefabricatedHttpMethod(api);
                if (apiMethod == null || wantMethod == null) {
                    if (pathOnly == null) {
                        pathOnly = api;
                    }
                    if (apiMethod == null && wantMethod == null) {
                        return api;
                    }
                    continue;
                }
                if (apiMethod.equals(wantMethod)) {
                    return api;
                }
            }
        }
        return pathOnly;
    }

    /**
     * 预制口 HTTP 方法；requestConfig.method 缺失则返回 null（path-only）。
     */
    public static String prefabricatedHttpMethod(PrefabricatedApi api) {
        if (api == null || api.getRequestConfig() == null) {
            return null;
        }
        Object rc = api.getRequestConfig();
        if (rc instanceof Map<?, ?> map) {
            Object m = map.get("method");
            if (m != null && StrUtil.isNotBlank(String.valueOf(m))) {
                return String.valueOf(m).trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * 按接口路径解析应使用的 Profile id。
     * 最长 pathPrefix 命中优先；无人命中则用数组第一条。
     *
     * @return profile id，配置为空时可能为 null
     */
    public static String resolveProfileId(String apiPath, ProjectAuthConfig config) {
        if (isEmpty(config)) {
            return null;
        }
        String byPrefix = resolveProfileIdByPrefixOnly(apiPath, config);
        if (byPrefix != null) {
            return byPrefix;
        }
        ProjectAuthProfile first = config.getAuthProfiles().get(0);
        return first != null ? StrUtil.trimToNull(first.getId()) : null;
    }

    /** 只按 pathPrefix 选 Profile，未命中返回 null（不回落到第一条）。 */
    private static String resolveProfileIdByPrefixOnly(String apiPath, ProjectAuthConfig config) {
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
        return bestId;
    }

    /** 把前缀整理成可匹配形式；空或单独 / 返回 null。 */
    static String toMatchPrefix(String rawPrefix) {
        if (StrUtil.isBlank(rawPrefix) || "/".equals(rawPrefix.trim())) {
            return null;
        }
        return normalizePrefix(rawPrefix);
    }

    /** 按 id 查找 Profile。 */
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
     * Profile 展示名。没有 name 时按常见 id 回落中文。
     */
    public static String displayProfileName(ProjectAuthConfig config, String profileId) {
        ProjectAuthProfile profile = findProfile(config, profileId);
        if (profile != null && StrUtil.isNotBlank(profile.getName())) {
            return profile.getName().trim();
        }
        if (PROFILE_DEFAULT.equals(profileId)) {
            return "默认 Bearer";
        }
        if (PROFILE_CLIENT.equals(profileId)) {
            return "客户端 Bearer";
        }
        if (PROFILE_ADMIN.equals(profileId)) {
            return "管理端 Bearer";
        }
        return StrUtil.blankToDefault(profileId, "项目鉴权");
    }

    /** 读扁平头名称，没有则读历史嵌套 header.name。 */
    public static String resolveHeaderName(ProjectAuthProfile profile) {
        if (profile == null) {
            return null;
        }
        String name = StrUtil.trimToNull(profile.getHeaderName());
        if (name != null) {
            return name;
        }
        Header header = profile.getHeader();
        return header != null ? StrUtil.trimToNull(header.getName()) : null;
    }

    /** 读扁平头值模板，没有则读历史嵌套 header.valueTemplate。 */
    public static String resolveHeaderValueTemplate(ProjectAuthProfile profile) {
        if (profile == null) {
            return null;
        }
        String value = StrUtil.trimToNull(profile.getHeaderValueTemplate());
        if (value != null) {
            return value;
        }
        Header header = profile.getHeader();
        return header != null ? StrUtil.trimToNull(header.getValueTemplate()) : null;
    }

    /**
     * 该 Profile 第一条带 loginHint 的预制口上的 flowKey。
     */
    public static String resolveLoginFlowKey(ProjectAuthProfile profile) {
        LoginHint hint = firstLoginHintOnProfile(profile);
        return hint != null ? StrUtil.trimToNull(hint.getFlowKey()) : null;
    }

    /** 本 Profile 第一条有效 loginHint；预制口没有则读历史 Profile 级字段。 */
    public static LoginHint firstLoginHintOnProfile(ProjectAuthProfile profile) {
        if (profile == null) {
            return null;
        }
        if (profile.getApis() != null) {
            for (PrefabricatedApi api : profile.getApis()) {
                if (api != null && api.getAuthConfig() != null && api.getAuthConfig().getLoginHint() != null) {
                    LoginHint hint = normalizeLoginHintQuiet(api.getAuthConfig().getLoginHint());
                    if (hint != null) {
                        return hint;
                    }
                }
            }
        }
        return normalizeLoginHintQuiet(profile.getLoginHint());
    }

    /** 收集配置里所有 loginHint.flowKey。 */
    public static Set<String> collectLoginFlowKeys(ProjectAuthConfig config) {
        Set<String> keys = new LinkedHashSet<>();
        if (config == null || config.getAuthProfiles() == null) {
            return keys;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null || profile.getApis() == null) {
                continue;
            }
            for (PrefabricatedApi api : profile.getApis()) {
                if (api == null || api.getAuthConfig() == null || api.getAuthConfig().getLoginHint() == null) {
                    continue;
                }
                String flowKey = StrUtil.trimToNull(api.getAuthConfig().getLoginHint().getFlowKey());
                if (flowKey != null) {
                    keys.add(flowKey);
                }
            }
        }
        return keys;
    }

    /** 按 method+path 找预制口上的 loginHint。 */
    public static LoginHint findLoginHint(ProjectAuthConfig config, String method, String apiPath) {
        PrefabricatedApi api = findPrefabricatedApi(config, method, apiPath);
        if (api == null || api.getAuthConfig() == null) {
            return null;
        }
        return normalizeLoginHintQuiet(api.getAuthConfig().getLoginHint());
    }

    /** 整理 loginHint，非法 from 时不抛异常。 */
    private static LoginHint normalizeLoginHintQuiet(LoginHint hint) {
        try {
            return normalizeLoginHint(hint, "parse");
        } catch (ServiceException e) {
            return hint;
        }
    }

    /** 抽取来源：优先 from，没有则历史 extractJsonPath 视为 body。 */
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

    /** 抽取表达式：优先 expr，没有则用历史 extractJsonPath。 */
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

    /** 路径前缀匹配用：补前导 /，并保证以 / 结尾。 */
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
