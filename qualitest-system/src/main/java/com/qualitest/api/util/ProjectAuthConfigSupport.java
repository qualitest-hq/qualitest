package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.CredentialApi;
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
 * 写出含 authProfiles（扁平头 + credentialApi + 预制接口）；不写 loginHint。
 * 免登：配置为空时用内置 /login 等路径；有 Profile 后只认预制口 mode=none。
 * 抽凭证：credentialApi 标明登录口；托管头占位符标明写入目标。
 */
public final class ProjectAuthConfigSupport {

    /** RuoYi Bearer 种子的 Profile id。 */
    public static final String PROFILE_RUOYI = "ruoyiBearer";

    /** 客户端 Bearer 的 Profile id。 */
    public static final String PROFILE_CLIENT = "clientBearer";

    /** 管理端 Bearer 的 Profile id。 */
    public static final String PROFILE_ADMIN = "adminBearer";

    /** 无 Profile 时落库的空 JSON。 */
    public static final String EMPTY_JSON = "{}";

    private ProjectAuthConfigSupport() {}

    /**
     * 解析库中 JSON。空白或非法返回空配置。
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
            return cfg;
        } catch (Exception e) {
            return empty();
        }
    }

    /**
     * 序列化成当前结构 JSON：根只有 authProfiles。
     * 不含嵌套 header、loginHint；apis 的 auth 不含 loginHint。
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

    /** 写出一条 Profile：id、name、match、扁平头、credentialApi、apis（不写 loginHint）。 */
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
        CredentialApi cred = profile.getCredentialApi();
        if (cred != null && StrUtil.isNotBlank(cred.getPath())) {
            Map<String, Object> credMap = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(cred.getMethod())) {
                credMap.put("method", cred.getMethod());
            }
            credMap.put("path", cred.getPath());
            map.put("credentialApi", credMap);
        }
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
            putIfNotBlank(row, "apiName", api.getApiName());
            row.put("apiPath", api.getApiPath());
            putIfNotBlank(row, "apiGroup", api.getApiGroup());
            putIfNotBlank(row, "protocolType", api.getProtocolType());
            putIfNotBlank(row, "apiStatus", api.getApiStatus());
            putIfNotBlank(row, "apiDescription", api.getApiDescription());
            putIfNotNull(row, "requestConfig", api.getRequestConfig());
            putIfNotNull(row, "headers", api.getHeaders());
            putIfNotNull(row, "cookies", api.getCookies());
            putIfNotNull(row, "responseConfig", api.getResponseConfig());
            putIfNotNull(row, "testValueConfig", api.getTestValueConfig());
            putIfNotNull(row, "bizCodeConfig", api.getBizCodeConfig());
            if (api.getAuthConfig() != null) {
                row.put("authConfig", writeAuthConfig(api.getAuthConfig()));
            }
            putIfNotNull(row, "designHints", api.getDesignHints());
            putIfNotBlank(row, "preRequestScript", api.getPreRequestScript());
            putIfNotBlank(row, "postRequestScript", api.getPostRequestScript());
            out.add(row);
        }
        return out;
    }

    /** 非空字符串才写入。 */
    private static void putIfNotBlank(Map<String, Object> row, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            row.put(key, value);
        }
    }

    /** 非 null 才写入。 */
    private static void putIfNotNull(Map<String, Object> row, String key, Object value) {
        if (value != null) {
            row.put(key, value);
        }
    }

    /** 写出接口鉴权：预制口只带 mode / profileId / override 头，不含 loginHint。 */
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
     * 校验并清理配置对象，只保留当前结构。
     */
    public static ProjectAuthConfig normalize(ProjectAuthConfig input) {
        if (input == null) {
            return empty();
        }
        List<ProjectAuthProfile> profiles = normalizeProfiles(input.getAuthProfiles());
        return ProjectAuthConfig.builder()
                .authProfiles(profiles)
                .build();
    }

    /** 整理 credentialApi：path 必填，method 转大写。 */
    private static CredentialApi normalizeCredentialApi(CredentialApi raw) {
        if (raw == null || StrUtil.isBlank(raw.getPath())) {
            return null;
        }
        String method = StrUtil.trimToNull(raw.getMethod());
        if (method != null) {
            method = method.toUpperCase(Locale.ROOT);
        }
        return CredentialApi.builder()
                .method(method)
                .path(normalizeApiPath(raw.getPath()))
                .build();
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
                    .credentialApi(normalizeCredentialApi(profile.getCredentialApi()))
                    .loginHint(null)
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
                        .build();
            }
            out.add(PrefabricatedApi.builder()
                    .apiName(StrUtil.blankToDefault(StrUtil.trimToNull(api.getApiName()), path))
                    .apiPath(path)
                    .apiGroup(StrUtil.trimToNull(api.getApiGroup()))
                    .protocolType(StrUtil.blankToDefault(StrUtil.trimToNull(api.getProtocolType()), "http"))
                    .apiStatus(StrUtil.blankToDefault(StrUtil.trimToNull(api.getApiStatus()), "normal"))
                    .apiDescription(StrUtil.trimToNull(api.getApiDescription()))
                    .requestConfig(api.getRequestConfig())
                    .headers(api.getHeaders())
                    .cookies(api.getCookies())
                    .responseConfig(api.getResponseConfig())
                    .testValueConfig(api.getTestValueConfig())
                    .bizCodeConfig(api.getBizCodeConfig())
                    .authConfig(auth)
                    .designHints(api.getDesignHints())
                    .preRequestScript(StrUtil.trimToNull(api.getPreRequestScript()))
                    .postRequestScript(StrUtil.trimToNull(api.getPostRequestScript()))
                    .build());
        }
        return out;
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

    /** 空配置：没有 Profile。列表可变，调用方可追加。 */
    public static ProjectAuthConfig empty() {
        return ProjectAuthConfig.builder().authProfiles(new ArrayList<>()).build();
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
     * RuoYi Bearer 种子：含登录/注册/验证码三口，均为免登。
     * 登录抽凭证写 asset.adminAuth.token（见内置登录流）。不含客户端 /api/account/auth 路径。
     */
    public static ProjectAuthConfig ruoyiBearerTemplate() {
        return ProjectAuthConfig.builder()
                .authProfiles(List.of(
                        ProjectAuthProfile.builder()
                                .id(PROFILE_RUOYI)
                                .name("RuoYi Bearer")
                                .headerName("Authorization")
                                .headerValueTemplate("Bearer {{asset.adminAuth.token}}")
                                .credentialApi(credentialApi("POST", "/login"))
                                .apis(ruoyiBearerApis())
                                .build()
                ))
                .build();
    }

    /**
     * RuoYi Bearer 与管理端共用的三口：POST /login、POST /register、GET /captchaImage。
     * 均为 mode=none；抽凭证规则在 Profile 上，不写在 apis 里。
     */
    public static List<PrefabricatedApi> ruoyiBearerApis() {
        List<PrefabricatedApi> apis = new ArrayList<>();
        apis.add(prefabricatedNone(
                "登录", "/login", "系统.登录", "POST",
                Map.of("username", "", "password", "", "code", "", "uuid", ""),
                requestBodyTestValue(Map.of("username", "admin", "password", "admin123")),
                loginResponseConfig(Map.of("code", 200, "msg", "操作成功", "token", "...")),
                "token 在 $.token，不要写成 $.data.token"));
        apis.add(prefabricatedNone(
                "注册", "/register", "系统.登录", "POST",
                Map.of("username", "", "password", ""),
                null, null, null));
        apis.add(prefabricatedNone(
                "验证码", "/captchaImage", "系统.登录", "GET",
                null, null, null, null));
        return apis;
    }

    /** 客户端两口：POST /api/account/auth/login 和 register。 */
    public static List<PrefabricatedApi> clientBearerApis() {
        List<PrefabricatedApi> apis = new ArrayList<>();
        apis.add(prefabricatedNone(
                "登录", "/api/account/auth/login", "客户端.账号", "POST",
                Map.of("mobile", "", "password", ""),
                requestBodyTestValue(Map.of("mobile", "13800000001", "password", "Test@123456")),
                loginResponseConfig(Map.of("code", 200, "msg", "操作成功",
                        "data", Map.of("token", "..."))),
                "客户端 token 在 $.data.token，不要写成 $.token"));
        apis.add(prefabricatedNone(
                "注册", "/api/account/auth/register", "客户端.账号", "POST",
                Map.of("mobile", "", "password", ""),
                null, null, null));
        return apis;
    }

    /**
     * 组装一条免登预制接口：请求结构只有 schema；响应 example 拆进测值配置。
     */
    private static PrefabricatedApi prefabricatedNone(
            String apiName,
            String apiPath,
            String apiGroup,
            String method,
            Map<String, Object> bodySkeleton,
            Object testValueConfig,
            Object responseConfig,
            String designHint) {
        Object designHints = null;
        if (StrUtil.isNotBlank(designHint)) {
            designHints = Map.of("hints", List.of(designHint));
        }
        Map<String, Object> tv = new LinkedHashMap<>();
        if (testValueConfig instanceof Map<?, ?> existingTv) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) existingTv;
            tv.putAll(cast);
        }
        Object responseOut = peelPrefabResponseIntoTestValue(responseConfig, tv);
        return PrefabricatedApi.builder()
                .apiName(apiName)
                .apiPath(apiPath)
                .apiGroup(apiGroup)
                .protocolType("http")
                .apiStatus("normal")
                .requestConfig(minimalRequestConfigStructure(method, bodySkeleton))
                .testValueConfig(tv.isEmpty() ? null : tv)
                .responseConfig(responseOut)
                .authConfig(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build())
                .designHints(designHints)
                .build();
    }

    /** 测值：调试用默认登录 body，形状为 request.bodyExample。 */
    private static Map<String, Object> requestBodyTestValue(Map<String, Object> bodyExample) {
        return Map.of("request", Map.of("bodyExample", bodyExample));
    }

    /** 登录响应草稿：带 schema 与 example；组装时会把 example 拆进测值。 */
    private static Map<String, Object> loginResponseConfig(Map<String, Object> example) {
        return Map.of(
                "configVersion", 1,
                "responses", List.of(Map.of(
                        "id", "resp-login",
                        "name", "成功",
                        "httpStatus", 200,
                        "contentType", "json",
                        "schema", inferObjectSchema(example),
                        "example", example)));
    }

    /**
     * 把响应里的 example 拆进测值 Map，返回去掉 example 后的响应对象。
     */
    @SuppressWarnings("unchecked")
    private static Object peelPrefabResponseIntoTestValue(Object responseConfig, Map<String, Object> tv) {
        if (responseConfig == null) {
            return null;
        }
        String tvJson = (tv == null || tv.isEmpty()) ? null : JSONUtil.toJsonStr(tv);
        ApiTestValuePeelSupport.PeelResult peeled = ApiTestValuePeelSupport.peel(
                "{}", JSONUtil.toJsonStr(responseConfig), tvJson);
        if (tv != null) {
            tv.clear();
            Map<String, Object> nextTv = JSONUtil.toBean(peeled.getTestValueConfig(), Map.class);
            if (nextTv != null && !nextTv.isEmpty()) {
                tv.putAll(nextTv);
            }
        }
        return JSONUtil.toBean(peeled.getResponseConfig(), Map.class);
    }

    /** 预制请求结构：只有 method / 空参数数组 / body.schema，不含 example。 */
    private static Object minimalRequestConfigStructure(String method, Map<String, Object> bodySkeleton) {
        Map<String, Object> body;
        if ("GET".equalsIgnoreCase(method) || bodySkeleton == null || bodySkeleton.isEmpty()) {
            body = Map.of("mode", "none");
        } else {
            body = Map.of("mode", "json", "json", Map.of("schema", inferObjectSchema(bodySkeleton)));
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

    private static Map<String, Object> inferObjectSchema(Map<String, Object> example) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (example != null) {
            for (Map.Entry<String, Object> e : example.entrySet()) {
                props.put(e.getKey(), Map.of("type", schemaTypeOf(e.getValue())));
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    private static String schemaTypeOf(Object v) {
        if (v instanceof Boolean) {
            return "boolean";
        }
        if (v instanceof Integer || v instanceof Long) {
            return "integer";
        }
        if (v instanceof Number) {
            return "number";
        }
        if (v instanceof Map<?, ?>) {
            return "object";
        }
        if (v instanceof List<?>) {
            return "array";
        }
        return "string";
    }

    /** 发凭证口：method + 规范化 path。 */
    public static CredentialApi credentialApi(String method, String path) {
        return CredentialApi.builder()
                .method(StrUtil.trimToNull(method) != null ? method.trim().toUpperCase(Locale.ROOT) : null)
                .path(normalizeApiPath(path))
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
        if (PROFILE_RUOYI.equals(profileId)) {
            return "RuoYi Bearer";
        }
        if (PROFILE_CLIENT.equals(profileId)) {
            return "客户端 Bearer";
        }
        if (PROFILE_ADMIN.equals(profileId)) {
            return "管理端 Bearer";
        }
        return StrUtil.blankToDefault(profileId, "项目鉴权");
    }

    /** 读扁平头名称。 */
    public static String resolveHeaderName(ProjectAuthProfile profile) {
        if (profile == null) {
            return null;
        }
        return StrUtil.trimToNull(profile.getHeaderName());
    }

    /** 读扁平头值模板。 */
    public static String resolveHeaderValueTemplate(ProjectAuthProfile profile) {
        if (profile == null) {
            return null;
        }
        return StrUtil.trimToNull(profile.getHeaderValueTemplate());
    }

    /** 收集各 Profile 托管头上的凭证 identityKey。 */
    public static Set<String> collectCredentialIdentityKeys(ProjectAuthConfig config) {
        Set<String> keys = new LinkedHashSet<>();
        if (config == null || config.getAuthProfiles() == null) {
            return keys;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null) {
                continue;
            }
            for (CredentialTargetSupport.CredentialTarget target : CredentialTargetSupport.targetsOnProfile(profile)) {
                keys.add(target.identityKey());
            }
        }
        return keys;
    }

    /**
     * 按 method+path 找发凭证 Profile：接口须在某 Profile.apis 中，且命中该条 credentialApi。
     */
    public static ProjectAuthProfile findCredentialProfile(ProjectAuthConfig config, String method, String apiPath) {
        if (isEmpty(config) || StrUtil.isBlank(apiPath)) {
            return null;
        }
        String path = normalizeApiPath(apiPath);
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null || !profileContainsApi(profile, method, path)) {
                continue;
            }
            if (matchesCredential(profile, method, path)) {
                return profile;
            }
            return null;
        }
        return null;
    }

    /** 该 Profile 的 apis 是否含此 method+path。 */
    private static boolean profileContainsApi(ProjectAuthProfile profile, String method, String path) {
        if (profile.getApis() == null) {
            return false;
        }
        String wantMethod = StrUtil.trimToNull(method);
        if (wantMethod != null) {
            wantMethod = wantMethod.toUpperCase(Locale.ROOT);
        }
        for (PrefabricatedApi api : profile.getApis()) {
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            if (!path.equals(normalizeApiPath(api.getApiPath()))) {
                continue;
            }
            String apiMethod = prefabricatedHttpMethod(api);
            if (apiMethod == null || wantMethod == null || apiMethod.equals(wantMethod)) {
                return true;
            }
        }
        return false;
    }

    /** 是否为本 Profile 声明的发凭证口。 */
    private static boolean matchesCredential(ProjectAuthProfile profile, String method, String path) {
        CredentialApi cred = profile.getCredentialApi();
        if (cred == null || StrUtil.isBlank(cred.getPath())) {
            return false;
        }
        if (!path.equals(normalizeApiPath(cred.getPath()))) {
            return false;
        }
        String credMethod = StrUtil.trimToNull(cred.getMethod());
        String wantMethod = StrUtil.trimToNull(method);
        if (credMethod == null || wantMethod == null) {
            return true;
        }
        return credMethod.equalsIgnoreCase(wantMethod);
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
