package com.qualitest.flow.diagnose;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignHttpNodeNormalizer;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * HTTP 节点相对当前 API 配置的语义健康检查。
 * <p>
 * 输出 warning 列表，不修改节点、不阻断保存或 Run。检查项：
 * <ul>
 *   <li>API 是否仍存在</li>
 *   <li>requestValueOverrides.paramDefaults 里的参数名是否还在 API 请求参数中</li>
 *   <li>body 类型 extracts 的路径是否还能在响应结构摘要里对上</li>
 * </ul>
 * 响应结构只做浅层摘要比对，深层字段可能漏报，属于粗检。
 */
@Component
public class HttpNodeApiHealthChecker {

    /** 未绑定 API，或绑定的 API 查不到 */
    public static final String CODE_API_MISSING = "API_MISSING";
    /** 测值覆盖参数名在 API 中已不存在（不自动删除覆盖） */
    public static final String CODE_ORPHAN_PARAM = "ORPHAN_PARAM";
    /** 抽取路径在响应结构摘要中找不到 */
    public static final String CODE_EXTRACT_PATH_MISSING = "EXTRACT_PATH_MISSING";

    /**
     * 检查整张图里所有「项目接口」模式的 HTTP 节点。
     *
     * @param graphJson   测试流 graph_json
     * @param apiResolver 按 API id 加载接口；返回 null 视为 API 缺失
     */
    public List<HttpNodeApiHealthWarning> checkGraph(
            String graphJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<HttpNodeApiHealthWarning> warnings = new ArrayList<>();
        Map<Long, TestProjectApi> cache = new LinkedHashMap<>();
        FlowHttpNodeVisitor.visit(graphJson, (nodeId, node, data) -> {
            if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
                return;
            }
            Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
            String nodeName = FlowHttpNodeVisitor.resolveNodeName(data, nodeId);
            if (apiId == null) {
                warnings.add(HttpNodeApiHealthWarning.of(
                        CODE_API_MISSING,
                        nodeId,
                        nodeName,
                        null,
                        "HTTP 节点「" + nodeName + "」未绑定有效 testProjectApiId",
                        null));
                return;
            }
            TestProjectApi api = cache.computeIfAbsent(apiId, id -> {
                try {
                    return apiResolver != null ? apiResolver.apply(id) : null;
                } catch (Exception e) {
                    return null;
                }
            });
            warnings.addAll(checkNode(nodeId, nodeName, data, apiId, api));
        });
        return warnings;
    }

    /**
     * 检查单个 HTTP 节点：API 是否存在、孤儿测值、抽取路径。
     *
     * @param api 已加载的 API；null 表示缺失，只报 API_MISSING
     */
    public List<HttpNodeApiHealthWarning> checkNode(
            String nodeId,
            String nodeName,
            JSONObject data,
            Long apiId,
            TestProjectApi api) {
        List<HttpNodeApiHealthWarning> warnings = new ArrayList<>();
        if (api == null) {
            warnings.add(HttpNodeApiHealthWarning.of(
                    CODE_API_MISSING,
                    nodeId,
                    nodeName,
                    apiId,
                    "HTTP 节点「" + nodeName + "」绑定的 API 不存在或已删除",
                    apiId != null ? String.valueOf(apiId) : null));
            return warnings;
        }

        // 结构 + 资产测值合成后的有效配置，作为参数名 / 响应路径的权威来源
        TestProjectApiEffectiveConfigResolver.EffectiveApiConfig effective =
                TestProjectApiEffectiveConfigResolver.resolve(api);
        Set<String> paramNames = collectParamNames(effective.getRequestConfig(), effective.getHeaders());
        warnings.addAll(checkOrphanParams(nodeId, nodeName, apiId, data, paramNames));

        Set<String> schemaPaths = FlowDesignApiSummarizer.summarizeResponsePaths(effective.getResponseConfig());
        warnings.addAll(checkExtracts(nodeId, nodeName, apiId, data, schemaPaths));
        return warnings;
    }

    /**
     * 检查 requestValueOverrides.paramDefaults：
     * 键名若不在 API 的 query/path/header/body 参数集合中，记为孤儿测值告警（不删覆盖）。
     * 参数名比对忽略大小写。
     */
    private List<HttpNodeApiHealthWarning> checkOrphanParams(
            String nodeId,
            String nodeName,
            Long apiId,
            JSONObject data,
            Set<String> paramNames) {
        List<HttpNodeApiHealthWarning> warnings = new ArrayList<>();
        JSONObject overrides = data.getJSONObject("requestValueOverrides");
        if (overrides == null) {
            return warnings;
        }
        JSONObject paramDefaults = overrides.getJSONObject("paramDefaults");
        if (paramDefaults == null || paramDefaults.isEmpty()) {
            return warnings;
        }
        for (String key : paramDefaults.keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String name = key.trim();
            if (!paramNames.contains(name)) {
                warnings.add(HttpNodeApiHealthWarning.of(
                        CODE_ORPHAN_PARAM,
                        nodeId,
                        nodeName,
                        apiId,
                        "HTTP 节点「" + nodeName + "」测值覆盖参数「" + name + "」已不在 API 中",
                        name));
            }
        }
        return warnings;
    }

    /**
     * 检查 body 类型 extracts：规范化表达式后，去掉 $. 前缀与响应结构摘要路径比对；
     * 对不上则告警。API 无响应结构时跳过，避免空 schema 误报。
     * header 等非 body 抽取不检查。
     */
    private List<HttpNodeApiHealthWarning> checkExtracts(
            String nodeId,
            String nodeName,
            Long apiId,
            JSONObject data,
            Set<String> schemaPaths) {
        List<HttpNodeApiHealthWarning> warnings = new ArrayList<>();
        Object raw = data.get("extracts");
        JSONArray extractsArr = null;
        if (raw instanceof JSONArray arr) {
            extractsArr = arr;
        } else if (raw instanceof List<?> list) {
            extractsArr = new JSONArray();
            extractsArr.addAll(list);
        }
        if (extractsArr == null || extractsArr.isEmpty()) {
            return warnings;
        }
        if (schemaPaths == null || schemaPaths.isEmpty()) {
            return warnings;
        }
        for (int i = 0; i < extractsArr.size(); i++) {
            Object item = extractsArr.get(i);
            JSONObject row;
            if (item instanceof JSONObject obj) {
                row = obj;
            } else if (item instanceof Map<?, ?> map) {
                row = new JSONObject();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        row.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            } else {
                continue;
            }
            String from = row.getString("from");
            if (from != null && !from.isBlank() && !"body".equalsIgnoreCase(from.trim())) {
                continue;
            }
            String expr = normalizeExtractExpr(row);
            if (expr == null || expr.isBlank() || !expr.startsWith("$.")) {
                continue;
            }
            String path = expr.substring(2).trim();
            if (path.isEmpty()) {
                continue;
            }
            if (!pathMatchesSchema(path, schemaPaths)) {
                warnings.add(HttpNodeApiHealthWarning.of(
                        CODE_EXTRACT_PATH_MISSING,
                        nodeId,
                        nodeName,
                        apiId,
                        "HTTP 节点「" + nodeName + "」抽取路径「" + expr + "」在最新响应 schema 中未找到",
                        expr));
            }
        }
        return warnings;
    }

    /**
     * 把单条 extract 规范化成 $.a.b 形式后取出 expr，不写回节点。
     * 会处理旧字段 value/path，以及单段浅路径补 data 前缀。
     */
    static String normalizeExtractExpr(JSONObject row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> wrapper = new LinkedHashMap<>();
        List<Object> extracts = new ArrayList<>();
        extracts.add(row);
        wrapper.put("extracts", extracts);
        FlowDesignHttpNodeNormalizer.normalizeExtracts(wrapper);
        Object normalized = wrapper.get("extracts");
        if (normalized instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof JSONObject obj) {
                return obj.getString("expr");
            }
            if (first instanceof Map<?, ?> map) {
                Object expr = map.get("expr");
                return expr != null ? String.valueOf(expr) : null;
            }
        }
        return row.getString("expr");
    }

    /**
     * 抽取路径是否落在响应 schema 叶路径集合内。
     * 比对前会把双方都收成点分结构路径（去掉下标、过滤器、[*]，并去掉误写的 items 段）。
     * 命中：结构路径相等，或一方是另一方的父路径（点号分隔）。
     */
    public static boolean pathMatchesSchema(String path, Set<String> schemaPaths) {
        if (path == null || path.isBlank() || schemaPaths == null || schemaPaths.isEmpty()) {
            return false;
        }
        String p = normalizeStructuralPath(path);
        if (p.isEmpty()) {
            return false;
        }
        for (String schemaPath : schemaPaths) {
            if (schemaPath == null || schemaPath.isBlank()) {
                continue;
            }
            String s = normalizeStructuralPath(schemaPath);
            if (s.isEmpty()) {
                continue;
            }
            if (s.equals(p)
                    || p.startsWith(s + ".")
                    || s.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把 JsonPath 或 schema 叶路径收成「点分结构路径」，便于和 schema 摘要比对。
     * <ul>
     *   <li>去掉 {@code $.} / {@code http.body.} 前缀</li>
     *   <li>去掉 {@code [0]}、{@code [*]}、{@code [?(…)]} 等下标与过滤器段</li>
     *   <li>去掉路径里误写的 {@code items} 段（Schema 描述数组元素时的关键字，真实 JSON 无此键）
     *       例如 {@code data.items.qty} → {@code data.qty}；{@code data[0].qty} → {@code data.qty}</li>
     * </ul>
     */
    public static String normalizeStructuralPath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        if (p.startsWith("$.")) {
            p = p.substring(2);
        } else if (p.startsWith("$")) {
            p = p.substring(1);
            if (p.startsWith(".")) {
                p = p.substring(1);
            }
        }
        if (p.startsWith("http.body.")) {
            p = p.substring("http.body.".length());
        } else if ("http.body".equals(p)) {
            return "";
        }
        // 去掉 [?(...)] / [n] / [*]
        StringBuilder out = new StringBuilder(p.length());
        for (int i = 0; i < p.length(); ) {
            char c = p.charAt(i);
            if (c == '[') {
                int close = p.indexOf(']', i);
                if (close < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                i = close + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        p = out.toString();
        // 去掉路径中的 items 段：JSON Schema 用 items 描述数组元素类型，真实响应没有这一层键
        p = p.replaceAll("(?<=^|\\.)items(?=\\.|$)", "");
        p = p.replaceAll("\\.{2,}", ".");
        if (p.startsWith(".")) {
            p = p.substring(1);
        }
        if (p.endsWith(".")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 从有效请求配置收集参数名集合（query / path / header / body），忽略大小写。
     */
    static Set<String> collectParamNames(String requestConfig, String headersJson) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        JSONObject summary = FlowDesignApiSummarizer.summarizeRequest(requestConfig, headersJson);
        appendParamNames(names, summary.getJSONArray("queryParams"));
        appendParamNames(names, summary.getJSONArray("pathParams"));
        appendParamNames(names, summary.getJSONArray("headerParams"));
        appendParamNames(names, summary.getJSONArray("bodyParams"));
        return names;
    }

    /** 把摘要数组里的 name 字段加入集合。 */
    private static void appendParamNames(Set<String> names, JSONArray arr) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof JSONObject obj) {
                String name = obj.getString("name");
                if (name != null && !name.isBlank()) {
                    names.add(name.trim());
                }
            }
        }
    }
}
