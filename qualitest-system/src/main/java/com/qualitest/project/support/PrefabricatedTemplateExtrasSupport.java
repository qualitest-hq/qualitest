package com.qualitest.project.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig.CredentialApi;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialExtract;
import com.qualitest.api.util.CredentialTargetSupport.ManagedHeaderTemplate;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 项目模板「预制参数 / 预制测试流 / 预制提示词」解析，以及凭证规则与托管头的派生。
 * <p>
 * 预制参数 kind 仅认 flow / env / asset。
 * 凭证派生只认预制测试流 extracts（优先 scope=asset）；托管头不存模板表，不再写 loginHint。
 */
public final class PrefabricatedTemplateExtrasSupport {

    private PrefabricatedTemplateExtrasSupport() {}

    /**
     * 派生结果：抽凭证的接口定位、以及写入项目 Profile 的托管头。
     */
    @Getter
    @Builder
    public static class DerivedCredential {
        /** 抽凭证的 HTTP 接口（method + path）；可空。 */
        private final CredentialApi credentialApi;
        /** 托管请求头名，如 Authorization / Cookie。 */
        private final String headerName;
        /** 托管请求头值模板，如 Bearer {{asset.adminAuth.token}}。 */
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
     * 一条预制 AI 提示词（种子为项目级 ai_prompt_template）。
     */
    @Getter
    @Builder
    public static class PrefabPrompt {
        /** 标题；同项目同场景下按此去重。 */
        private final String title;
        /** 说明（胶囊副文案）。 */
        private final String description;
        /** 正文。 */
        private final String content;
        /** 会话场景，默认 test_flow_design。 */
        private final String sessionScene;
        /** 排序。 */
        private final Integer sortNum;
        /** 备注（如场景 ID）。 */
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

    /**
     * 解析预制 AI 提示词 JSON。
     * 缺 title 或 content 的条目跳过；非法 JSON 返回空列表。
     */
    public static List<PrefabPrompt> parsePrompts(String promptsJson) {
        List<PrefabPrompt> out = new ArrayList<>();
        if (StrUtil.isBlank(promptsJson)) {
            return out;
        }
        try {
            JSONArray arr = JSON.parseArray(promptsJson);
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                String title = StrUtil.trimToNull(row.getString("title"));
                String content = StrUtil.trimToNull(row.getString("content"));
                if (title == null || content == null) {
                    continue;
                }
                String scene = StrUtil.blankToDefault(row.getString("sessionScene"), "test_flow_design").trim();
                if (scene.isEmpty()) {
                    scene = "test_flow_design";
                }
                Integer sortNum = row.getInteger("sortNum");
                out.add(PrefabPrompt.builder()
                        .title(title)
                        .description(StrUtil.trimToNull(row.getString("description")))
                        .content(content)
                        .sessionScene(scene)
                        .sortNum(sortNum != null ? sortNum : 0)
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
     * 只读预制测试流 extracts（优先 asset，其次 flow）；无则返回 null。
     */
    public static DerivedCredential deriveCredential(String flowsJson) {
        return deriveFromFlows(flowsJson);
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
                CredentialExtract extract = CredentialTargetSupport.firstCredentialExtract(data.getJSONArray("extracts"));
                if (extract == null) {
                    continue;
                }
                String method = StrUtil.blankToDefault(data.getString("httpMethod"), "POST").trim().toUpperCase(Locale.ROOT);
                String path = ProjectAuthConfigSupport.normalizeApiPath(data.getString("apiPath"));
                if (StrUtil.isBlank(path)) {
                    continue;
                }
                return buildDerived(
                        ProjectAuthConfigSupport.credentialApi(method, path), extract);
            }
        }
        return null;
    }

    private static DerivedCredential buildDerived(CredentialApi credentialApi, CredentialExtract extract) {
        if (extract == null) {
            return null;
        }
        ManagedHeaderTemplate header = CredentialTargetSupport.managedHeaderFor(
                extract.target(), extract.from(), extract.expr());
        if (header == null) {
            return null;
        }
        return DerivedCredential.builder()
                .credentialApi(credentialApi)
                .headerName(header.headerName())
                .headerValueTemplate(header.headerValueTemplate())
                .build();
    }

    /**
     * 给画布里 callMode=project 的 HTTP 节点写入/重写 testProjectApiId。
     * 优先：模板作者期 id（雪花数字或历史 tpl_*）经 synthToProjectId remap；
     * 已是数字且不在 map → 视为项目主键跳过；
     * legacy：无可用映射时按 method+path 查项目接口。
     */
    public static String bindGraphApis(
            String graphJson,
            java.util.function.BiFunction<String, String, Long> apiIdResolver) {
        return bindGraphApis(graphJson, apiIdResolver, null);
    }

    /**
     * @param synthToProjectId 模板合成 id → 项目接口主键；可空（仅 legacy path）
     */
    public static String bindGraphApis(
            String graphJson,
            java.util.function.BiFunction<String, String, Long> apiIdResolver,
            Map<String, Long> synthToProjectId) {
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
            String rawId = StrUtil.trimToNull(data.getString("testProjectApiId"));
            // 模板作者期 id（雪花或历史 tpl_*）优先经 map remap；勿把数字模板 id 当成已是项目主键
            if (rawId != null && synthToProjectId != null && synthToProjectId.containsKey(rawId)) {
                data.put("testProjectApiId", String.valueOf(synthToProjectId.get(rawId)));
                changed = true;
                continue;
            }
            if (rawId != null && isNumericProjectApiId(rawId)) {
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

    /** 纯数字字符串（项目主键，或未命中 remap 表时的作者期雪花 id）。 */
    public static boolean isNumericProjectApiId(String raw) {
        if (StrUtil.isBlank(raw)) {
            return false;
        }
        String id = raw.trim();
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return !id.isEmpty();
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
     * 组装内置模板用的「探活再登录」流 JSON 数组字符串。
     * 默认探活 GET /getInfo；extract 写入 asset.{entryKey}.{fieldPath}。
     */
    public static String builtinLoginFlowJson(
            String flowName,
            String method,
            String apiPath,
            String entryKey,
            String fieldPath,
            String from,
            String expr) {
        return builtinLoginFlowJson(flowName, method, apiPath, entryKey, fieldPath, from, expr,
                "GET", "/getInfo", null, null, null, null);
    }

    /**
     * 组装内置模板用的「探活再登录」流 JSON 数组字符串。
     * 图：Condition(asset 凭证 exists) → 探活(statusCheck whitelist 200/401)
     * → Condition(http.status=200) 否则登录；extract 写入 asset.{entryKey}.{fieldPath}。
     */
    public static String builtinLoginFlowJson(
            String flowName,
            String method,
            String apiPath,
            String entryKey,
            String fieldPath,
            String from,
            String expr,
            String probeMethod,
            String probePath) {
        return builtinLoginFlowJson(flowName, method, apiPath, entryKey, fieldPath, from, expr,
                probeMethod, probePath, null, null, null, null);
    }

    /**
     * @param loginApiId   预制登录口合成 id（可空，兼容旧图）
     * @param probeApiId   预制探活口合成 id（可空）
     * @param loginApiName 登录口展示名
     * @param probeApiName 探活口展示名
     */
    public static String builtinLoginFlowJson(
            String flowName,
            String method,
            String apiPath,
            String entryKey,
            String fieldPath,
            String from,
            String expr,
            String probeMethod,
            String probePath,
            String loginApiId,
            String probeApiId,
            String loginApiName,
            String probeApiName) {
        String assetPath = "asset." + entryKey + "." + fieldPath;
        String safeProbeMethod = StrUtil.blankToDefault(probeMethod, "GET").trim().toUpperCase(Locale.ROOT);
        String safeProbePath = ProjectAuthConfigSupport.normalizeApiPath(
                StrUtil.blankToDefault(probePath, "/getInfo"));
        String safeLoginMethod = StrUtil.blankToDefault(method, "POST").trim().toUpperCase(Locale.ROOT);
        String safeLoginPath = ProjectAuthConfigSupport.normalizeApiPath(apiPath);
        String probeName = StrUtil.blankToDefault(probeApiName, "探活");
        String loginName = StrUtil.blankToDefault(loginApiName, "登录");

        JSONObject extract = new JSONObject();
        extract.put("from", from);
        extract.put("expr", expr);
        extract.put("scope", "asset");
        extract.put("name", fieldPath);
        extract.put("entryKey", entryKey);
        extract.put("fieldPath", fieldPath);

        // —— 节点 ——
        JSONObject condToken = new JSONObject();
        condToken.put("id", "cond_token");
        condToken.put("type", "condition");
        condToken.put("position", pos(80, 260));
        JSONObject condTokenData = new JSONObject();
        condTokenData.put("name", "凭证是否存在");
        condTokenData.put("summary", "凭证是否存在");
        condTokenData.put("branches", List.of(
                branch("b_token_if", "if", "probe_http", List.of(
                        condition(assetPath, "exists", ""))),
                branch("b_token_else", "else", "login_http", List.of())
        ));
        condToken.put("data", condTokenData);

        JSONObject probeHttp = new JSONObject();
        probeHttp.put("id", "probe_http");
        probeHttp.put("type", "http");
        probeHttp.put("position", pos(480, 60));
        JSONObject probeData = new JSONObject();
        probeData.put("name", "探活");
        probeData.put("callMode", "project");
        probeData.put("httpMethod", safeProbeMethod);
        probeData.put("apiPath", safeProbePath);
        if (StrUtil.isNotBlank(probeApiId)) {
            probeData.put("testProjectApiId", probeApiId.trim());
            probeData.put("apiName", probeName);
        }
        probeData.put("timeoutMs", 30000);
        JSONObject statusCheck = new JSONObject();
        statusCheck.put("mode", "whitelist");
        statusCheck.put("values", List.of(200, 401));
        probeData.put("statusCheck", statusCheck);
        JSONObject probeSuccess = new JSONObject();
        probeSuccess.put("mode", "off");
        probeData.put("successCheck", probeSuccess);
        probeData.put("extracts", List.of());
        probeData.put("summary", StrUtil.isNotBlank(probeApiId)
                ? safeProbeMethod + " " + probeName
                : "探活");
        probeHttp.put("data", probeData);

        JSONObject condAlive = new JSONObject();
        condAlive.put("id", "cond_alive");
        condAlive.put("type", "condition");
        condAlive.put("position", pos(960, 60));
        JSONObject condAliveData = new JSONObject();
        condAliveData.put("name", "凭证是否有效");
        condAliveData.put("summary", "凭证是否有效");
        condAliveData.put("branches", List.of(
                terminalBranch("b_alive_if", "if", List.of(
                        condition("http.status", "eq", "200"))),
                branch("b_alive_else", "else", "login_http", List.of())
        ));
        condAlive.put("data", condAliveData);

        JSONObject loginHttp = new JSONObject();
        loginHttp.put("id", "login_http");
        loginHttp.put("type", "http");
        loginHttp.put("position", pos(680, 440));
        JSONObject loginData = new JSONObject();
        loginData.put("name", "登录");
        loginData.put("callMode", "project");
        loginData.put("httpMethod", safeLoginMethod);
        loginData.put("apiPath", safeLoginPath);
        if (StrUtil.isNotBlank(loginApiId)) {
            loginData.put("testProjectApiId", loginApiId.trim());
            loginData.put("apiName", loginName);
        }
        loginData.put("timeoutMs", 30000);
        JSONObject loginSuccess = new JSONObject();
        loginSuccess.put("mode", "inherit");
        loginData.put("successCheck", loginSuccess);
        loginData.put("extracts", List.of(extract));
        loginData.put("summary", StrUtil.isNotBlank(loginApiId)
                ? safeLoginMethod + " " + loginName
                : "登录");
        loginHttp.put("data", loginData);

        // —— 边（与 branches.target 对齐，供画布展示） ——
        List<JSONObject> edges = List.of(
                edge("e_token_if", "cond_token", "probe_http"),
                edge("e_token_else", "cond_token", "login_http"),
                edge("e_probe", "probe_http", "cond_alive"),
                edge("e_alive_else", "cond_alive", "login_http")
        );

        JSONObject output = new JSONObject();
        output.put("name", entryKey + "." + fieldPath);

        JSONObject viewport = new JSONObject();
        viewport.put("x", 0);
        viewport.put("y", 0);
        viewport.put("zoom", 0.85);

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
        graph.put("nodes", List.of(condToken, probeHttp, condAlive, loginHttp));
        graph.put("edges", edges);
        graph.put("meta", meta);

        JSONObject flow = new JSONObject();
        flow.put("flowName", flowName);
        flow.put("description", "探活复用或登录，抽出 asset." + entryKey + "." + fieldPath);
        flow.put("graphJson", graph);
        return JSON.toJSONString(List.of(flow));
    }

    private static JSONObject pos(int x, int y) {
        JSONObject p = new JSONObject();
        p.put("x", x);
        p.put("y", y);
        return p;
    }

    private static JSONObject condition(String left, String operator, String right) {
        JSONObject c = new JSONObject();
        c.put("left", left);
        c.put("operator", operator);
        c.put("right", right);
        return c;
    }

    private static JSONObject branch(String id, String kind, String target, List<JSONObject> conditions) {
        JSONObject b = new JSONObject();
        b.put("id", id);
        b.put("kind", kind);
        b.put("target", target);
        b.put("conditions", conditions);
        return b;
    }

    /** IF/ELIF 结束流程分支（无 target）。 */
    private static JSONObject terminalBranch(String id, String kind, List<JSONObject> conditions) {
        JSONObject b = new JSONObject();
        b.put("id", id);
        b.put("kind", kind);
        b.put("terminal", true);
        b.put("conditions", conditions);
        return b;
    }

    private static JSONObject edge(String id, String source, String target) {
        JSONObject e = new JSONObject();
        e.put("id", id);
        e.put("source", source);
        e.put("target", target);
        return e;
    }
}
