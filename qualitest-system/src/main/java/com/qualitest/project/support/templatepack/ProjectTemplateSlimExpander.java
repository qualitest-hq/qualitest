package com.qualitest.project.support.templatepack;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ExpandResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimApi;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimCredential;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimEnv;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 精简冷启动 JSON → 可入库的 TestProjectTemplate。
 *
 * 会展开：接口、素材、环境、路径匹配（matchConfig）。
 * 不会生成：templateFlows、templatePrompts（固定写成 []）。
 * 若 JSON 里带了非空 flows，只进 warnings，不落库。
 * 需要登录流时：在测试项目配好流后「另存为项目模板」，或导入带 flows 的完整包。
 */
@Component
public class ProjectTemplateSlimExpander {

    /** 允许的 authStyle 取值 */
    private static final Set<String> AUTH_STYLES = Set.of("bearer", "session", "header", "none", "custom");

    /**
     * 校验并展开精简包。
     * 缺必填或非法取值抛业务异常；flows、_uncertain 等只进 warnings / summary.note。
     */
    public ExpandResult expand(SlimTemplate slim) {
        if (slim == null) {
            throw new ServiceException("模板 JSON 不能为空");
        }
        List<String> warnings = new ArrayList<>();
        // 精简包故意不落流：提示调用方去另存，而不是在模板管理里画流
        if (slim.getFlows() != null && !slim.getFlows().isNull()
                && !(slim.getFlows().isArray() && slim.getFlows().isEmpty())) {
            warnings.add("flows 已忽略：导入不生成预制测试流；登录流请从跑通项目另存为模板");
        }
        if (slim.getUncertain() != null) {
            for (String item : slim.getUncertain()) {
                if (StrUtil.isNotBlank(item)) {
                    warnings.add("不确定项: " + item.trim());
                }
            }
        }

        String templateName = StrUtil.trimToNull(slim.getTemplateName());
        if (templateName == null) {
            throw new ServiceException("templateName 不能为空");
        }
        String authStyle = StrUtil.blankToDefault(slim.getAuthStyle(), "").trim().toLowerCase(Locale.ROOT);
        if (!AUTH_STYLES.contains(authStyle)) {
            throw new ServiceException("authStyle 无效，允许: bearer/session/header/none/custom");
        }
        if (slim.getApis() == null || slim.getApis().isEmpty()) {
            throw new ServiceException("apis 不能为空");
        }

        String matchConfig = buildMatchConfig(slim, warnings);
        List<PrefabricatedApi> apis = expandApis(slim, warnings);
        String templateParams = expandAssets(slim.getAssets());
        String templateEnvs = expandEnvs(slim);

        TestProjectTemplate entity = TestProjectTemplate.builder()
                .templateName(templateName)
                .matchConfig(matchConfig)
                .templateApis(JSONUtil.toJsonStr(apis))
                .templateParams(templateParams)
                .templateEnvs(templateEnvs)
                .templateFlows("[]")
                .templatePrompts("[]")
                .builtinStatus(0)
                .enableStatus(slim.getEnableStatus() != null ? slim.getEnableStatus() : 1)
                .sortNum(slim.getSortNum() != null ? slim.getSortNum() : 0)
                .delStatus(0)
                .build();
        entity.setRemark(slim.getRemark());

        ExpandResult result = new ExpandResult();
        result.setEntity(entity);
        result.setWarnings(warnings);
        Map<String, Object> summary = result.getExpandedSummary();
        summary.put("apiCount", apis.size());
        summary.put("assetCount", slim.getAssets() == null ? 0 : slim.getAssets().size());
        summary.put("envCount", countEnvs(slim));
        summary.put("flowCount", 0);
        summary.put("authStyle", authStyle);
        // 给导入预览用：说明本次没生成预制流
        summary.put("note", "未生成预制测试流；登录流请从跑通项目另存为模板");

        Map<String, Object> preview = result.getPreview();
        preview.put("templateName", templateName);
        preview.put("matchConfig", matchConfig == null ? null : JSONUtil.parse(matchConfig));
        preview.put("templateApis", apis);
        preview.put("templateParams", JSONUtil.parseArray(templateParams));
        preview.put("templateEnvs", JSONUtil.parseArray(templateEnvs));
        preview.put("templateFlows", List.of());
        return result;
    }

    /** 统计 env + envs 条数。 */
    private static int countEnvs(SlimTemplate slim) {
        int n = 0;
        if (slim.getEnv() != null) {
            n++;
        }
        if (slim.getEnvs() != null) {
            n += slim.getEnvs().size();
        }
        return n;
    }

    /**
     * 组装 matchConfig：pathPrefix、authStyle、credential。
     * pathPrefix 为空只 warning；单独 "/" 直接失败。
     */
    private String buildMatchConfig(SlimTemplate slim, List<String> warnings) {
        Map<String, Object> match = new LinkedHashMap<>();
        List<String> prefixes = slim.getPathPrefix();
        if (prefixes != null && !prefixes.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String p : prefixes) {
                String t = StrUtil.trimToNull(p);
                if (t == null) {
                    continue;
                }
                if ("/".equals(t)) {
                    throw new ServiceException("pathPrefix 禁止使用 \"/\"");
                }
                cleaned.add(t);
            }
            if (!cleaned.isEmpty()) {
                match.put("pathPrefix", cleaned);
            }
        } else {
            warnings.add("pathPrefix 为空：勾选进项目后不做路径前缀匹配");
        }
        if (StrUtil.isNotBlank(slim.getAuthStyle())) {
            match.put("authStyle", slim.getAuthStyle().trim().toLowerCase(Locale.ROOT));
        }
        if (slim.getCredential() != null) {
            match.put("credential", slimCredentialToMap(slim.getCredential()));
        }
        if (match.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(match);
    }

    /** credential 对象 → 写入 matchConfig 的 map（去掉空字符串字段）。 */
    private static Map<String, Object> slimCredentialToMap(SlimCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        putTrimmed(m, "asset", c.getAsset());
        if (c.getExtract() != null) {
            m.put("extract", c.getExtract());
        }
        putTrimmed(m, "tokenField", c.getTokenField());
        putTrimmed(m, "cookieName", c.getCookieName());
        putTrimmed(m, "headerName", c.getHeaderName());
        putTrimmed(m, "headerValueTemplate", c.getHeaderValueTemplate());
        return m;
    }

    /** 非空白字符串才放入 map。 */
    private static void putTrimmed(Map<String, Object> target, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }

    /**
     * apis[] → 预制接口列表。
     * 生成 requestConfig / responseConfig / 测值；首个接口可附带鉴权 designHints。
     */
    private List<PrefabricatedApi> expandApis(SlimTemplate slim, List<String> warnings) {
        List<PrefabricatedApi> out = new ArrayList<>();
        SlimCredential credential = slim.getCredential();
        String hint = buildCredentialHint(credential);
        int index = 0;
        for (SlimApi api : slim.getApis()) {
            index++;
            if (api == null) {
                continue;
            }
            String name = StrUtil.trimToNull(api.getName());
            String path = StrUtil.trimToNull(api.getPath());
            String method = StrUtil.blankToDefault(api.getMethod(), "GET").trim().toUpperCase(Locale.ROOT);
            if (name == null || path == null) {
                throw new ServiceException("apis[" + (index - 1) + "] 需要 name 与 path");
            }
            String authMode = StrUtil.blankToDefault(api.getAuthMode(), "none").trim().toLowerCase(Locale.ROOT);
            if (!ApiAuthConfig.MODE_NONE.equals(authMode) && !ApiAuthConfig.MODE_INHERIT.equals(authMode)) {
                warnings.add("apis[" + name + "] authMode=" + authMode + "，已按字面写入");
            }

            Map<String, Object> requestConfig = buildRequestConfig(api, method);
            Map<String, Object> headers = api.getHeaders() == null ? Map.of() : new LinkedHashMap<>(api.getHeaders());
            Map<String, Object> testValue = new LinkedHashMap<>();
            if (api.getBody() != null && !api.getBody().isEmpty() && !"none".equalsIgnoreCase(api.getBodyMode())) {
                testValue.put("request", Map.of("bodyExample", api.getBody()));
            }
            Object responseConfig = null;
            if (api.getResponse() != null && !api.getResponse().isEmpty()) {
                responseConfig = buildResponseConfig(api.getResponse());
                peelResponseExample(responseConfig, testValue);
            }

            List<String> hints = new ArrayList<>();
            if (index == 1 && StrUtil.isNotBlank(hint)) {
                hints.add(hint);
            }

            int syncProtected = resolveSyncProtected(api.getSyncProtected());
            out.add(PrefabricatedApi.builder()
                    .testProjectApiId(String.valueOf(IdUtil.getSnowflakeNextId()))
                    .apiName(name)
                    .apiPath(path)
                    .apiGroup(StrUtil.blankToDefault(api.getApiGroup(), ""))
                    .protocolType("http")
                    .apiStatus("normal")
                    .requestConfig(requestConfig)
                    .headers(headers)
                    .cookies(Map.of())
                    .responseConfig(responseConfig == null ? Map.of("configVersion", 1, "responses", List.of()) : responseConfig)
                    .testValueConfig(testValue.isEmpty() ? Map.of() : testValue)
                    .bizCodeConfig(Map.of())
                    .authConfig(ApiAuthConfig.builder().mode(authMode).build())
                    .designHints(Map.of("hints", hints))
                    .syncProtected(syncProtected)
                    .build());
        }
        if (out.isEmpty()) {
            throw new ServiceException("apis 不能为空");
        }
        return out;
    }

    /** syncProtected 缺省为 1（保护）；兼容布尔 / 数字 / 字符串。 */
    private static int resolveSyncProtected(Object raw) {
        if (raw == null) {
            return 1;
        }
        if (raw instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0 ? 1 : 0;
        }
        String s = String.valueOf(raw).trim();
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return 0;
        }
        return 1;
    }

    /** 根据 credential 生成给人看的鉴权提示短句。 */
    private static String buildCredentialHint(SlimCredential credential) {
        if (credential == null) {
            return null;
        }
        String asset = StrUtil.blankToDefault(credential.getAsset(), "adminAuth").trim();
        String extractExpr = extractExpr(credential.getExtract());
        String tokenField = StrUtil.trimToNull(credential.getTokenField());
        if (tokenField == null && extractExpr != null) {
            tokenField = lastPathSegment(extractExpr);
        }
        if (tokenField == null) {
            tokenField = "token";
        }
        StringBuilder sb = new StringBuilder("鉴权提示: ");
        if (extractExpr != null) {
            sb.append("extract=").append(extractExpr);
        }
        sb.append(" → asset.").append(asset).append('.').append(tokenField);
        if (StrUtil.isNotBlank(credential.getCookieName())) {
            sb.append("; cookieName=").append(credential.getCookieName().trim());
        }
        if (StrUtil.isNotBlank(credential.getHeaderName())) {
            sb.append("; header=").append(credential.getHeaderName().trim());
        }
        return sb.toString();
    }

    /**
     * 从 extract（字符串或带 expr 的对象）取出 JsonPath 表达式文本。
     */
    static String extractExpr(Object extract) {
        return PrefabricatedTemplateExtrasSupport.matchConfigExtractExpr(extract);
    }

    /**
     * 取 JSONPath 末段字段名，用于在未写 tokenField 时推断素材字段
     * （如 $.data.accessToken → accessToken）。
     */
    static String lastPathSegment(String jsonPath) {
        return PrefabricatedTemplateExtrasSupport.lastPathSegment(jsonPath);
    }

    /** 由 method / query / headers / body 组装 requestConfig。 */
    private Map<String, Object> buildRequestConfig(SlimApi api, String method) {
        Map<String, Object> rc = new LinkedHashMap<>();
        rc.put("configVersion", 1);
        rc.put("method", method);
        rc.put("queryParams", mapToNameExampleList(api.getQuery()));
        rc.put("pathParams", List.of());
        rc.put("declaredHeaders", mapToNameExampleList(api.getHeaders()));

        String bodyMode = StrUtil.blankToDefault(api.getBodyMode(), "json").trim().toLowerCase(Locale.ROOT);
        Map<String, Object> body = new LinkedHashMap<>();
        if ("GET".equals(method) || "none".equals(bodyMode) || api.getBody() == null || api.getBody().isEmpty()) {
            body.put("mode", "none");
        } else if ("form".equals(bodyMode)) {
            body.put("mode", "form");
            body.put("form", Map.of("schema", inferObjectSchema(api.getBody())));
        } else {
            body.put("mode", "json");
            body.put("json", Map.of("schema", inferObjectSchema(api.getBody())));
        }
        rc.put("body", body);
        return rc;
    }

    /** 简易 map → requestConfig 里的 name/example 参数列表。 */
    private static List<Map<String, Object>> mapToNameExampleList(Map<String, Object> map) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (map == null || map.isEmpty()) {
            return rows;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (StrUtil.isBlank(e.getKey())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getKey().trim());
            row.put("example", e.getValue() == null ? "" : String.valueOf(e.getValue()));
            rows.add(row);
        }
        return rows;
    }

    /** 响应样例 → responseConfig（含推断 schema 与一条成功响应）。 */
    private static Map<String, Object> buildResponseConfig(Map<String, Object> example) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", "resp-" + IdUtil.fastSimpleUUID().substring(0, 8));
        resp.put("name", "成功");
        resp.put("httpStatus", 200);
        resp.put("contentType", "json");
        resp.put("schema", inferObjectSchema(example));
        resp.put("example", example);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("configVersion", 1);
        cfg.put("responses", List.of(resp));
        return cfg;
    }

    /**
     * 把响应 example 挪到 testValueConfig.response，并从 responseConfig 去掉 example，
     * 避免预制接口配置里重复存一份大样例。
     */
    @SuppressWarnings("unchecked")
    private static void peelResponseExample(Object responseConfig, Map<String, Object> testValue) {
        if (!(responseConfig instanceof Map<?, ?> cfg)) {
            return;
        }
        Object responses = cfg.get("responses");
        if (!(responses instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> resp)) {
            return;
        }
        Object example = resp.get("example");
        if (example != null) {
            Map<String, Object> responseTv = new LinkedHashMap<>();
            responseTv.put("example", example);
            testValue.put("response", responseTv);
            Map<String, Object> mutable = (Map<String, Object>) first;
            mutable.remove("example");
        }
    }

    /** 根据样例值类型推断简易 object schema。 */
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

    /** Java 值 → JSON Schema type 字符串。 */
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

    /** assets map → templateParams JSON（每项 kind=asset）。 */
    private String expandAssets(Map<String, Map<String, Object>> assets) {
        if (assets == null || assets.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : assets.entrySet()) {
            if (StrUtil.isBlank(e.getKey())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "asset");
            row.put("name", e.getKey().trim());
            row.put("value", e.getValue() == null ? Map.of() : e.getValue());
            row.put("remark", "");
            rows.add(row);
        }
        return JSONUtil.toJsonStr(rows);
    }

    /** env / envs → templateEnvs JSON 数组字符串。 */
    private String expandEnvs(SlimTemplate slim) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (slim.getEnv() != null) {
            Map<String, Object> one = envToRow(slim.getEnv());
            if (one != null) {
                rows.add(one);
            }
        }
        if (slim.getEnvs() != null) {
            for (SlimEnv env : slim.getEnvs()) {
                Map<String, Object> one = envToRow(env);
                if (one != null) {
                    rows.add(one);
                }
            }
        }
        return JSONUtil.toJsonStr(rows);
    }

    /** 单条精简环境 → 入库环境行；全空则跳过。 */
    private static Map<String, Object> envToRow(SlimEnv env) {
        if (env == null) {
            return null;
        }
        String envName = StrUtil.blankToDefault(env.getEnvName(), "默认环境");
        String envUrl = StrUtil.trimToEmpty(env.getEnvUrl());
        List<Map<String, Object>> vars = new ArrayList<>();
        if (env.getVariables() != null) {
            for (Map.Entry<String, Object> e : env.getVariables().entrySet()) {
                if (StrUtil.isBlank(e.getKey())) {
                    continue;
                }
                String key = e.getKey().trim();
                Map<String, Object> assets = new LinkedHashMap<>();
                assets.put(key, e.getValue() == null ? "" : e.getValue());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", key);
                entry.put("remark", "");
                entry.put("assets", assets);
                vars.add(entry);
            }
        }
        if (envUrl.isEmpty() && vars.isEmpty() && StrUtil.isBlank(env.getEnvName())) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("envName", envName);
        row.put("envUrl", envUrl);
        row.put("envVariables", vars);
        return row;
    }
}
