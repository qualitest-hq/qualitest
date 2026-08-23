package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig.CredentialApi;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 项目模板「预制参数 / 预制测试流」解析，以及凭证规则与托管头的派生。
 * <p>
 * 派生优先级：预制测试流 extracts → 预制参数里标记为凭证的抽取项 → 预制接口上残留的 loginHint。
 * 托管头不存模板表：Bearer 用 Authorization，Session Cookie 用 Cookie。
 */
public final class PrefabricatedTemplateExtrasSupport {

    private PrefabricatedTemplateExtrasSupport() {}

    /**
     * 派生结果：抽凭证的接口定位、loginHint、以及写入项目 Profile 的托管头。
     */
    @Getter
    @Builder
    public static class DerivedCredential {
        /** 抽凭证的 HTTP 接口（method + path）；可空。 */
        private final CredentialApi credentialApi;
        /** 抽凭证规则（flow 变量名、from、expr）。 */
        private final LoginHint loginHint;
        /** 托管请求头名，如 Authorization / Cookie。 */
        private final String headerName;
        /** 托管请求头值模板，如 Bearer {{flow.token}}。 */
        private final String headerValueTemplate;
    }

    /**
     * 一条预制测试流：名称 + 可选说明 + 画布 JSON。
     */
    @Getter
    @Builder
    public static class PrefabFlow {
        /** 流名称；种子到项目时按此去重。 */
        private final String flowName;
        /** 流说明。 */
        private final String description;
        /** 画布 JSON 字符串（nodes / edges / meta）。 */
        private final String graphJson;
    }

    /**
     * 一条预制参数。
     * kind=value：测值默认；kind=extract：抽取规则；credential=true 表示用于生成凭证。
     */
    @Getter
    @Builder
    public static class PrefabParam {
        /** value（测值）或 extract（抽取）。 */
        private final String kind;
        /** 参数名 / flow 变量名。 */
        private final String name;
        /** 绑定接口方法（来自 bind.method）。 */
        private final String method;
        /** 绑定接口路径（来自 bind.path，已规范化）。 */
        private final String path;
        /** kind=value 时的默认值。 */
        private final Object value;
        /** kind=extract 时的来源：body / setCookie / header。 */
        private final String from;
        /** kind=extract 时的表达式或 Cookie 名。 */
        private final String expr;
        /** 是否作为凭证抽取（用于生成 loginHint 与托管头）。 */
        private final boolean credential;
    }

    /**
     * 解析预制测试流 JSON。
     * 无 flowName 或无 graphJson 的条目跳过；非法 JSON 返回空列表。
     */
    public static List<PrefabFlow> parseFlows(String flowsJson) {
        List<PrefabFlow> out = new ArrayList<>();
        if (StrUtil.isBlank(flowsJson)) {
            return out;
        }
        try {
            JSONArray arr = JSON.parseArray(flowsJson);
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                String name = StrUtil.trimToNull(row.getString("flowName"));
                if (name == null) {
                    continue;
                }
                Object graph = row.get("graphJson");
                String graphJson;
                if (graph instanceof String s) {
                    graphJson = s;
                } else if (graph != null) {
                    graphJson = JSON.toJSONString(graph);
                } else {
                    continue;
                }
                out.add(PrefabFlow.builder()
                        .flowName(name)
                        .description(StrUtil.trimToNull(row.getString("description")))
                        .graphJson(graphJson)
                        .build());
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }

    /**
     * 解析预制参数 JSON。
     * 无 name 的条目跳过；非法 JSON 返回空列表。
     */
    public static List<PrefabParam> parseParams(String paramsJson) {
        List<PrefabParam> out = new ArrayList<>();
        if (StrUtil.isBlank(paramsJson)) {
            return out;
        }
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                String kind = StrUtil.blankToDefault(row.getString("kind"), "value").trim().toLowerCase(Locale.ROOT);
                String name = StrUtil.trimToNull(row.getString("name"));
                if (name == null) {
                    continue;
                }
                JSONObject bind = row.getJSONObject("bind");
                String method = bind != null ? StrUtil.trimToNull(bind.getString("method")) : null;
                String path = bind != null ? StrUtil.trimToNull(bind.getString("path")) : null;
                out.add(PrefabParam.builder()
                        .kind(kind)
                        .name(name)
                        .method(method != null ? method.toUpperCase(Locale.ROOT) : null)
                        .path(path != null ? ProjectAuthConfigSupport.normalizeApiPath(path) : null)
                        .value(row.get("value"))
                        .from(StrUtil.trimToNull(row.getString("from")))
                        .expr(StrUtil.trimToNull(row.getString("expr")))
                        .credential(Boolean.TRUE.equals(row.getBoolean("credential")))
                        .build());
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }

    /**
     * 派生凭证规则与托管头。
     * 优先读预制测试流；其次读预制参数中的凭证抽取；最后用预制接口上残留的 loginHint。
     * 三者都没有则返回 null。
     */
    public static DerivedCredential deriveCredential(
            String flowsJson, String paramsJson, LoginHint legacyHint, CredentialApi legacyCredential) {
        DerivedCredential fromFlows = deriveFromFlows(flowsJson);
        if (fromFlows != null) {
            return fromFlows;
        }
        DerivedCredential fromParams = deriveFromParams(paramsJson);
        if (fromParams != null) {
            return fromParams;
        }
        if (legacyHint == null || StrUtil.isBlank(legacyHint.getFlowKey())) {
            return null;
        }
        return buildDerived(legacyCredential, legacyHint);
    }

    /** 遍历预制测试流，取第一个带有效 extracts 的 HTTP 节点生成凭证。 */
    private static DerivedCredential deriveFromFlows(String flowsJson) {
        for (PrefabFlow flow : parseFlows(flowsJson)) {
            JSONObject graph = JSON.parseObject(flow.getGraphJson());
            if (graph == null) {
                continue;
            }
            JSONArray nodes = graph.getJSONArray("nodes");
            if (nodes == null) {
                continue;
            }
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node == null || !"http".equalsIgnoreCase(StrUtil.blankToDefault(node.getString("type"), ""))) {
                    continue;
                }
                JSONObject data = node.getJSONObject("data");
                if (data == null) {
                    continue;
                }
                JSONArray extracts = data.getJSONArray("extracts");
                LoginHint hint = firstCredentialExtract(extracts);
                if (hint == null) {
                    continue;
                }
                String method = StrUtil.blankToDefault(data.getString("httpMethod"), "POST").trim().toUpperCase(Locale.ROOT);
                String path = ProjectAuthConfigSupport.normalizeApiPath(data.getString("apiPath"));
                if (StrUtil.isBlank(path)) {
                    continue;
                }
                return buildDerived(ProjectAuthConfigSupport.credentialApi(method, path), hint);
            }
        }
        return null;
    }

    /** 遍历预制参数，取第一条 credential extract 生成凭证。 */
    private static DerivedCredential deriveFromParams(String paramsJson) {
        for (PrefabParam param : parseParams(paramsJson)) {
            if (!"extract".equals(param.getKind()) || !param.isCredential()) {
                continue;
            }
            if (StrUtil.isBlank(param.getName()) || StrUtil.isBlank(param.getExpr())) {
                continue;
            }
            LoginHint hint = LoginHint.builder()
                    .flowKey(param.getName())
                    .from(StrUtil.blankToDefault(param.getFrom(), "body"))
                    .expr(param.getExpr())
                    .build();
            CredentialApi cred = null;
            if (StrUtil.isNotBlank(param.getPath())) {
                cred = ProjectAuthConfigSupport.credentialApi(
                        StrUtil.blankToDefault(param.getMethod(), "POST"), param.getPath());
            }
            return buildDerived(cred, hint);
        }
        return null;
    }

    /**
     * 从 extracts 数组取第一条可用于凭证的行。
     * 要求：有 name、有 expr、scope 为 flow（或缺省为 flow）。
     */
    static LoginHint firstCredentialExtract(JSONArray extracts) {
        if (extracts == null || extracts.isEmpty()) {
            return null;
        }
        for (int i = 0; i < extracts.size(); i++) {
            JSONObject row = extracts.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String name = StrUtil.trimToNull(row.getString("name"));
            String expr = StrUtil.trimToNull(row.getString("expr"));
            if (name == null || expr == null) {
                continue;
            }
            String scope = StrUtil.blankToDefault(row.getString("scope"), "flow").trim();
            if (!"flow".equalsIgnoreCase(scope)) {
                continue;
            }
            return LoginHint.builder()
                    .flowKey(name)
                    .from(StrUtil.blankToDefault(row.getString("from"), "body"))
                    .expr(expr)
                    .build();
        }
        return null;
    }

    /**
     * 根据 loginHint 拼托管头：
     * from 为 setCookie / set_cookie → Cookie: 名={{flow.xxx}}；
     * 其它 → Authorization: Bearer {{flow.xxx}}。
     */
    private static DerivedCredential buildDerived(CredentialApi credentialApi, LoginHint loginHint) {
        String flowKey = StrUtil.trimToNull(loginHint.getFlowKey());
        String from = StrUtil.blankToDefault(loginHint.getFrom(), "body").trim().toLowerCase(Locale.ROOT);
        String expr = StrUtil.trimToNull(loginHint.getExpr());
        String headerName;
        String headerValueTemplate;
        if ("setcookie".equals(from) || "set_cookie".equals(from)) {
            String cookieName = StrUtil.blankToDefault(expr, "JSESSIONID");
            headerName = "Cookie";
            headerValueTemplate = cookieName + "={{flow." + flowKey + "}}";
        } else {
            headerName = "Authorization";
            headerValueTemplate = "Bearer {{flow." + flowKey + "}}";
        }
        return DerivedCredential.builder()
                .credentialApi(credentialApi)
                .loginHint(loginHint)
                .headerName(headerName)
                .headerValueTemplate(headerValueTemplate)
                .build();
    }

    /**
     * 给画布里 callMode=project 的 HTTP 节点写入 testProjectApiId。
     * 按 method+path 查项目接口；查不到的节点保持未绑定。
     */
    public static String bindGraphApis(String graphJson, java.util.function.BiFunction<String, String, Long> apiIdResolver) {
        if (StrUtil.isBlank(graphJson) || apiIdResolver == null) {
            return graphJson;
        }
        JSONObject graph = JSON.parseObject(graphJson);
        if (graph == null) {
            return graphJson;
        }
        JSONArray nodes = graph.getJSONArray("nodes");
        if (nodes == null) {
            return graphJson;
        }
        boolean changed = false;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null || !"http".equalsIgnoreCase(StrUtil.blankToDefault(node.getString("type"), ""))) {
                continue;
            }
            JSONObject data = node.getJSONObject("data");
            if (data == null) {
                continue;
            }
            if (!"project".equalsIgnoreCase(StrUtil.blankToDefault(data.getString("callMode"), "project"))) {
                continue;
            }
            String method = StrUtil.blankToDefault(data.getString("httpMethod"), "GET").trim().toUpperCase(Locale.ROOT);
            String path = ProjectAuthConfigSupport.normalizeApiPath(data.getString("apiPath"));
            if (StrUtil.isBlank(path)) {
                continue;
            }
            Long apiId = apiIdResolver.apply(method, path);
            if (apiId == null) {
                continue;
            }
            data.put("testProjectApiId", String.valueOf(apiId));
            changed = true;
        }
        return changed ? graph.toJSONString() : graphJson;
    }

    /**
     * 组装内置模板用的「单条登录流」JSON 数组字符串。
     * 画布含一个 HTTP 登录节点，并按入参写入 extracts / flowOutputs。
     */
    public static String builtinLoginFlowJson(
            String flowName, String method, String apiPath, String flowKey, String from, String expr) {
        JSONObject extract = new JSONObject();
        extract.put("from", from);
        extract.put("expr", expr);
        extract.put("scope", "flow");
        extract.put("name", flowKey);

        JSONObject data = new JSONObject();
        data.put("name", "登录");
        data.put("callMode", "project");
        data.put("httpMethod", method);
        data.put("apiPath", apiPath);
        data.put("extracts", List.of(extract));
        data.put("summary", "登录");

        JSONObject position = new JSONObject();
        position.put("x", 40);
        position.put("y", 80);
        JSONObject node = new JSONObject();
        node.put("id", "login_http");
        node.put("type", "http");
        node.put("position", position);
        node.put("data", data);

        JSONObject output = new JSONObject();
        output.put("name", flowKey);

        JSONObject viewport = new JSONObject();
        viewport.put("x", 0);
        viewport.put("y", 0);
        viewport.put("zoom", 1);
        JSONObject meta = new JSONObject();
        meta.put("schemaVersion", 1);
        meta.put("flowOutputs", List.of(output));
        meta.put("viewport", viewport);

        JSONObject graph = new JSONObject();
        graph.put("nodes", List.of(node));
        graph.put("edges", List.of());
        graph.put("meta", meta);

        JSONObject flow = new JSONObject();
        flow.put("flowName", flowName);
        flow.put("description", "登录并抽出 " + flowKey);
        flow.put("graphJson", graph);
        return JSON.toJSONString(List.of(flow));
    }
}
