package com.qualitest.project.support;

import cn.hutool.core.util.IdUtil;
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
import java.util.Map;

/**
 * 项目模板「预制参数 / 预制测试流」解析，以及凭证规则与托管头的派生。
 * <p>
 * 预制参数 kind 仅认 flow / env / assert（对齐测试流场景初值、环境变量、断言规则）。
 * 凭证派生只认预制测试流 extracts，其次接口上残留的 loginHint；托管头不存模板表。
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
     * 一条预制参数（flow / env / asset）。
     */
    @Getter
    @Builder
    public static class PrefabParam {
        /** flow（场景初值）/ env（环境变量）/ asset（项目素材）。 */
        private final String kind;
        /** 变量名 / 素材 key。 */
        private final String name;
        /** 值；asset 可为对象或标量。 */
        private final Object value;
        /** 备注。 */
        private final String remark;
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
     * 只认 kind=flow|env|asset；其它形态整行跳过（不做旧 value/extract/assert 映射）。
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
                String kind = StrUtil.blankToDefault(row.getString("kind"), "").trim().toLowerCase(Locale.ROOT);
                if (!"flow".equals(kind) && !"env".equals(kind) && !"asset".equals(kind)) {
                    continue;
                }
                String name = StrUtil.trimToNull(row.getString("name"));
                if (name == null) {
                    continue;
                }
                out.add(PrefabParam.builder()
                        .kind(kind)
                        .name(name)
                        .value(normalizeParamValue(row.get("value")))
                        .remark(StrUtil.trimToNull(row.getString("remark")))
                        .build());
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }

    /** 字符串若是 JSON 对象/数组则解析，便于素材嵌套字段落盘。 */
    private static Object normalizeParamValue(Object raw) {
        if (!(raw instanceof String s)) {
            return raw;
        }
        String text = s.trim();
        if (text.isEmpty()) {
            return "";
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            try {
                return JSON.parse(text);
            } catch (Exception ignored) {
                return raw;
            }
        }
        return raw;
    }

    /**
     * 派生凭证规则与托管头。
     * 优先读预制测试流 extracts；其次用预制接口上残留的 loginHint。
     * 二者都没有则返回 null（不再读预制参数）。
     */
    public static DerivedCredential deriveCredential(
            String flowsJson, LoginHint legacyHint, CredentialApi legacyCredential) {
        DerivedCredential fromFlows = deriveFromFlows(flowsJson);
        if (fromFlows != null) {
            return fromFlows;
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
     * 把 flow 预制参数合并进画布默认场景的 flowSeed（同名键不覆盖）。
     * 无 scenarios 时补一条默认场景。
     */
    public static String mergeFlowSeedIntoGraph(String graphJson, List<PrefabParam> flowParams) {
        if (StrUtil.isBlank(graphJson) || flowParams == null || flowParams.isEmpty()) {
            return graphJson;
        }
        JSONObject graph = JSON.parseObject(graphJson);
        if (graph == null) {
            return graphJson;
        }
        JSONObject meta = graph.getJSONObject("meta");
        if (meta == null) {
            meta = new JSONObject();
            graph.put("meta", meta);
        }
        ensureDefaultScenario(meta);
        JSONArray scenarios = meta.getJSONArray("scenarios");
        JSONObject scenario = scenarios.getJSONObject(0);
        JSONObject flowSeed = scenario.getJSONObject("flowSeed");
        if (flowSeed == null) {
            flowSeed = new JSONObject();
            scenario.put("flowSeed", flowSeed);
        }
        boolean changed = false;
        for (PrefabParam param : flowParams) {
            if (param == null || !"flow".equals(param.getKind()) || StrUtil.isBlank(param.getName())) {
                continue;
            }
            if (flowSeed.containsKey(param.getName())) {
                continue;
            }
            flowSeed.put(param.getName(), param.getValue() != null ? param.getValue() : "");
            changed = true;
        }
        return changed ? graph.toJSONString() : graphJson;
    }

    /**
     * 把变量条目合并进 envVariables / asset_variables 同形 JSON（同 key 不覆盖）。
     * @param expectedKind env 或 asset，只合并该 kind 的参数行
     */
    public static String mergeVariableEntries(String existingJson, List<PrefabParam> params, String expectedKind) {
        if (params == null || params.isEmpty() || StrUtil.isBlank(expectedKind)) {
            return existingJson;
        }
        List<com.qualitest.project.domain.TestProjectAsset> entries;
        try {
            entries = new ArrayList<>(TestProjectVariableEntrySupport.parseEntries(
                    StrUtil.blankToDefault(existingJson, "[]"), false));
        } catch (Exception e) {
            entries = new ArrayList<>();
        }
        boolean changed = false;
        for (PrefabParam param : params) {
            if (param == null || !expectedKind.equals(param.getKind()) || StrUtil.isBlank(param.getName())) {
                continue;
            }
            if (TestProjectVariableEntrySupport.findByKey(entries, param.getName()) != null) {
                continue;
            }
            Map<String, Object> assets = new java.util.LinkedHashMap<>();
            assets.put(param.getName(), param.getValue() != null ? param.getValue() : "");
            entries.add(com.qualitest.project.domain.TestProjectAsset.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .key(param.getName())
                    .remark(param.getRemark())
                    .updateTime(TestProjectVariableEntrySupport.nowUpdateTime())
                    .assets(assets)
                    .build());
            changed = true;
        }
        if (!changed) {
            return existingJson;
        }
        return TestProjectVariableEntrySupport.toJson(
                TestProjectVariableEntrySupport.normalizeEntriesForPersist(entries));
    }

    /** 合并进环境 envVariables。 */
    public static String mergeEnvVariables(String existingJson, List<PrefabParam> envParams) {
        return mergeVariableEntries(existingJson, envParams, "env");
    }

    /** 合并进项目素材 asset_variables。 */
    public static String mergeAssetVariables(String existingJson, List<PrefabParam> assetParams) {
        return mergeVariableEntries(existingJson, assetParams, "asset");
    }

    private static void ensureDefaultScenario(JSONObject meta) {
        JSONArray scenarios = meta.getJSONArray("scenarios");
        if (scenarios != null && !scenarios.isEmpty()) {
            if (StrUtil.isBlank(meta.getString("activeScenarioId"))) {
                JSONObject first = scenarios.getJSONObject(0);
                if (first != null && StrUtil.isNotBlank(first.getString("id"))) {
                    meta.put("activeScenarioId", first.getString("id"));
                }
            }
            return;
        }
        String scenarioId = String.valueOf(IdUtil.getSnowflakeNextId());
        JSONObject scenario = new JSONObject();
        scenario.put("id", scenarioId);
        scenario.put("name", "默认（冒烟）");
        scenario.put("testProjectEnvId", "");
        scenario.put("flowSeed", new JSONObject());
        scenario.put("remark", "");
        scenarios = new JSONArray();
        scenarios.add(scenario);
        meta.put("scenarios", scenarios);
        meta.put("activeScenarioId", scenarioId);
        if (!meta.containsKey("layout")) {
            meta.put("layout", "manual");
        }
        if (!meta.containsKey("schemaVersion")) {
            meta.put("schemaVersion", 1);
        }
    }

    /**
     * 组装内置模板用的「单条登录流」JSON 数组字符串。
     * 画布含一个 HTTP 登录节点，并按入参写入 extracts / flowOutputs / 默认场景。
     */
    public static String builtinLoginFlowJson(
            String flowName, String method, String apiPath, String flowKey, String from, String expr) {
        JSONObject extract = new JSONObject();
        extract.put("from", from);
        extract.put("expr", expr);
        extract.put("scope", "flow");
        extract.put("name", flowKey);
        extract.put("entryKey", "");
        extract.put("fieldPath", "");

        JSONObject data = new JSONObject();
        data.put("name", "登录");
        data.put("callMode", "project");
        data.put("httpMethod", method);
        data.put("apiPath", apiPath);
        data.put("timeoutMs", 30000);
        JSONObject successCheck = new JSONObject();
        successCheck.put("mode", "inherit");
        data.put("successCheck", successCheck);
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
        viewport.put("x", 40);
        viewport.put("y", 40);
        viewport.put("zoom", 1);

        String scenarioId = String.valueOf(IdUtil.getSnowflakeNextId());
        JSONObject scenario = new JSONObject();
        scenario.put("id", scenarioId);
        scenario.put("name", "默认（冒烟）");
        scenario.put("testProjectEnvId", "");
        scenario.put("flowSeed", new JSONObject());
        scenario.put("remark", "");

        JSONObject meta = new JSONObject();
        meta.put("schemaVersion", 1);
        meta.put("layout", "manual");
        meta.put("flowOutputs", List.of(output));
        meta.put("viewport", viewport);
        meta.put("activeScenarioId", scenarioId);
        meta.put("scenarios", List.of(scenario));

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
