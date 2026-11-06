package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 设计期断言 / 条件左值路径门禁。
 * <p>
 * 扫描 {@code http.body…} 与 {@code nodes.<节点id>.http.body…} 左值，
 * 对照绑定项目接口的响应 schema 做字段检查；响应 example 不参与硬拦。
 * <ul>
 *   <li>裸 {@code http.body…}：用沿入边找到的最近上游 project HTTP 的 schema</li>
 *   <li>{@code nodes.<id>.http.body…}：用该 id 对应 HTTP 节点绑定接口的 schema</li>
 *   <li>{@code flow.*} / {@code env.*} / {@code asset.*} 等非 body 路径跳过</li>
 *   <li>误含 Schema 关键字 {@code .items}、或写成 {@code http.body.$.…} → errors</li>
 *   <li>schema 有叶路径但字段未命中 → warnings</li>
 *   <li>schema 为空且存在待检左值 → warnings</li>
 *   <li>全图扫描且无上游、也无 nodes 引用时跳过该节点；收窄到指定节点且无上游时记 errors</li>
 * </ul>
 */
public final class AssertPathDesignGate {

    /** 路径段 {@code items}：JSON Schema 描述数组元素的关键字，不应出现在断言左值里 */
    private static final Pattern SCHEMA_ITEMS_SEGMENT = Pattern.compile("(^|\\.)items(\\.|\\[|$)");

    /** 门禁结果：errors 阻断确认/保存；warnings 仅提示。 */
    public record AssertPathGateResult(List<String> errors, List<String> warnings) {
        public static AssertPathGateResult empty() {
            return new AssertPathGateResult(List.of(), List.of());
        }

        public AssertPathGateResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private AssertPathDesignGate() {
    }

    /**
     * 扫描图中全部 assert、condition 节点，按响应 schema 校验 body 类左值。
     *
     * @param graph       待检查的图
     * @param apiResolver 按 testProjectApiId 取接口；返回 null 则该 HTTP 节点无 schema
     * @return errors 与 warnings
     */
    public static AssertPathGateResult validate(GraphJson graph, Function<Long, TestProjectApi> apiResolver) {
        return validate(graph, apiResolver, null);
    }

    /**
     * 按响应 schema 校验 body 类左值，可按节点 id 收窄范围。
     * {@code onlyNodeIds == null}：全图扫描；无上游且无 nodes 引用则跳过该节点。
     * {@code onlyNodeIds} 非空：只校验这些 assert/condition；无上游且无 nodes 引用则记 errors。
     *
     * @param graph        待检查的图
     * @param apiResolver  按 testProjectApiId 取接口
     * @param onlyNodeIds  仅校验这些节点 id；null 表示全图
     * @return errors 与 warnings
     */
    public static AssertPathGateResult validate(
            GraphJson graph,
            Function<Long, TestProjectApi> apiResolver,
            Set<String> onlyNodeIds) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return AssertPathGateResult.empty();
        }
        boolean scoped = onlyNodeIds != null && !onlyNodeIds.isEmpty();

        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null) {
                byId.put(n.getId(), n);
            }
        }
        Map<String, List<String>> incoming = new HashMap<>();
        for (GraphEdge e : edges) {
            if (e == null || e.getTarget() == null || e.getSource() == null) {
                continue;
            }
            incoming.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e.getSource());
        }

        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null || node.getId() == null) {
                continue;
            }
            if (scoped && !onlyNodeIds.contains(node.getId())) {
                continue;
            }
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if (!"assert".equals(type) && !"condition".equals(type)) {
                continue;
            }
            String nodeName = resolveName(node);
            String kindLabel = "assert".equals(type) ? "断言" : "条件";
            GraphNode upstreamHttp = findUpstreamProjectHttp(node.getId(), byId, incoming, new HashSet<>());
            boolean hasNodesBodyRef = hasNodesHttpBodyLeftToCheck(type, node.getData());
            if (upstreamHttp == null && !hasNodesBodyRef) {
                if (scoped) {
                    errors.add(kindLabel + "节点「" + nodeName + "」尚无上游项目 HTTP（含未确认 Staging 边/节点），无法校验路径；"
                            + "请先确认上游 HTTP 与入边");
                }
                continue;
            }
            Set<String> upstreamSchemaPaths = upstreamHttp != null
                    ? resolveSchemaPaths(upstreamHttp, apiResolver)
                    : Set.of();
            if (upstreamHttp != null && upstreamSchemaPaths.isEmpty() && hasBareHttpBodyLeftToCheck(type, node.getData())) {
                warnings.add(kindLabel + "节点「" + nodeName + "」上游接口无响应 schema，未做字段校验");
            }
            if ("assert".equals(type)) {
                validateRules(
                        kindLabel, nodeName, "rules", node.getData().get("rules"),
                        upstreamSchemaPaths, byId, apiResolver, errors, warnings);
            } else {
                for (Object b : GraphDataLists.asList(node.getData().get("branches"))) {
                    Map<?, ?> branch = GraphDataLists.asMap(b);
                    if (branch == null) {
                        continue;
                    }
                    validateRules(
                            kindLabel, nodeName, "conditions", branch.get("conditions"),
                            upstreamSchemaPaths, byId, apiResolver, errors, warnings);
                }
            }
        }
        return new AssertPathGateResult(errors, warnings);
    }

    /**
     * 一条待检的 body 字段左值。
     *
     * @param relative      body 后的相对路径（如 {@code data.code}）
     * @param sourceNodeId  {@code nodes.<id>} 引用的节点 id；裸 http.body 时为 null
     * @param displayLeft   规范化后的完整左值，用于错误文案
     */
    private record HttpBodyLeftRef(String relative, String sourceNodeId, String displayLeft) {
    }

    /** 节点规则里是否存在裸 {@code http.body.} 字段左值（不含 nodes 引用）。 */
    private static boolean hasBareHttpBodyLeftToCheck(String type, Map<String, Object> data) {
        return anyHttpBodyLeft(type, data, false);
    }

    /** 节点规则里是否存在 {@code nodes.<id>.http.body.} 字段左值。 */
    private static boolean hasNodesHttpBodyLeftToCheck(String type, Map<String, Object> data) {
        return anyHttpBodyLeft(type, data, true);
    }

    /**
     * 扫描 assert 的 rules 或 condition 各分支 conditions，判断是否含待检 body 左值。
     *
     * @param nodesOnly true 只认 nodes 引用；false 只认裸 http.body
     */
    private static boolean anyHttpBodyLeft(String type, Map<String, Object> data, boolean nodesOnly) {
        if ("assert".equals(type)) {
            return rulesHaveHttpBodyLeft(data.get("rules"), nodesOnly);
        }
        for (Object b : GraphDataLists.asList(data.get("branches"))) {
            Map<?, ?> branch = GraphDataLists.asMap(b);
            if (branch != null && rulesHaveHttpBodyLeft(branch.get("conditions"), nodesOnly)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rulesHaveHttpBodyLeft(Object rulesRaw, boolean nodesOnly) {
        for (Object item : GraphDataLists.asList(rulesRaw)) {
            Map<?, ?> rule = GraphDataLists.asMap(item);
            if (rule == null) {
                continue;
            }
            HttpBodyLeftRef ref = parseHttpBodyLeft(rule.get("left"));
            if (ref == null) {
                continue;
            }
            if (nodesOnly) {
                if (ref.sourceNodeId() != null) {
                    return true;
                }
            } else if (ref.sourceNodeId() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将规则 left 解析为待检的 body 字段引用。
     * 支持裸 {@code http.body.<相对路径>} 与 {@code nodes.<id>.http.body.<相对路径>}；
     * 非整段 body 字段路径（如仅 status、整段 body 无字段）返回 null。
     */
    private static HttpBodyLeftRef parseHttpBodyLeft(Object leftObj) {
        if (leftObj == null) {
            return null;
        }
        String left = PlaceholderResolver.normalizeAssertLeftPath(String.valueOf(leftObj).trim());
        PlaceholderResolver.NodesHttpRef nodesRef = PlaceholderResolver.parseNodesHttpPath(left);
        if (nodesRef != null) {
            String rest = nodesRef.httpRest();
            if (!rest.startsWith("body.") || rest.length() <= "body.".length()) {
                return null;
            }
            String relative = rest.substring("body.".length());
            return relative.isBlank() ? null : new HttpBodyLeftRef(relative, nodesRef.nodeId(), left);
        }
        if (!left.startsWith("http.body.") || left.length() <= "http.body.".length()) {
            return null;
        }
        String relative = left.substring("http.body.".length());
        return relative.isBlank() ? null : new HttpBodyLeftRef(relative, null, left);
    }

    /**
     * 逐条检查规则左值。
     * 结构错误（{@code body.$…}、误含 {@code .items}）写入 errors；
     * schema 缺字段或引用节点无 schema 写入 warnings。
     * nodes 引用用目标 HTTP 节点的接口 schema；裸 http.body 用上游 schema。
     */
    private static void validateRules(
            String kindLabel,
            String nodeName,
            String fieldName,
            Object rulesRaw,
            Set<String> upstreamSchemaPaths,
            Map<String, GraphNode> byId,
            Function<Long, TestProjectApi> apiResolver,
            List<String> errors,
            List<String> warnings) {
        List<?> rules = GraphDataLists.asList(rulesRaw);
        for (int i = 0; i < rules.size(); i++) {
            Map<?, ?> rule = GraphDataLists.asMap(rules.get(i));
            if (rule == null) {
                continue;
            }
            HttpBodyLeftRef bodyRef = parseHttpBodyLeft(rule.get("left"));
            if (bodyRef == null) {
                continue;
            }
            String relative = bodyRef.relative();
            String left = bodyRef.displayLeft();
            if (relative.startsWith("$")) {
                errors.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                        + "」不可写成 http.body.$.…");
                continue;
            }
            if (containsJsonSchemaItemsSegment(relative)) {
                errors.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                        + "」误含 JSON Schema 关键字 .items；数组请用 data[0]、data[*] 或 data[?(@.field==…)]");
                continue;
            }
            Set<String> schemaPaths = upstreamSchemaPaths;
            String schemaLabel = "上游接口";
            if (bodyRef.sourceNodeId() != null) {
                GraphNode sourceHttp = byId.get(bodyRef.sourceNodeId());
                if (sourceHttp == null) {
                    continue;
                }
                schemaPaths = resolveSchemaPaths(sourceHttp, apiResolver);
                schemaLabel = "节点「" + bodyRef.sourceNodeId() + "」接口";
                if (schemaPaths.isEmpty()) {
                    warnings.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                            + "」引用的 " + schemaLabel + "无响应 schema，未做字段校验");
                    continue;
                }
            } else if (schemaPaths == null || schemaPaths.isEmpty()) {
                continue;
            }
            if (!HttpNodeApiHealthChecker.pathMatchesSchema(relative, schemaPaths)) {
                warnings.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                        + "」在" + schemaLabel + "响应 schema 中未找到对应字段");
            }
        }
    }

    /**
     * 路径是否含 Schema 数组字段 {@code items}（如 {@code data.items.quantity}）。
     * 过滤器表达式内部的字段名不算（{@code @.items} 两侧不是段边界时由正则约束）。
     */
    static boolean containsJsonSchemaItemsSegment(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return SCHEMA_ITEMS_SEGMENT.matcher(relativePath.trim()).find();
    }

    /**
     * 在给定试算 body 上对断言左值求值。
     * {@code nodes.<id>.http…} 会先剥成 {@code http…} 再按 body 相对路径取字段；
     * 非 body 作用域或非法 {@code body.$…} 返回 null。
     *
     * @param trialBody 试算用响应体
     * @param left      断言左值原文
     * @return 求值结果；无法求值时为 null
     */
    public static Object evalAssertLeft(Object trialBody, String left) {
        if (trialBody == null || left == null) {
            return null;
        }
        String normalized = PlaceholderResolver.normalizeAssertLeftPath(left.trim());
        PlaceholderResolver.NodesHttpRef nodesRef = PlaceholderResolver.parseNodesHttpPath(normalized);
        if (nodesRef != null) {
            normalized = "http." + nodesRef.httpRest();
        }
        if ("http.body".equals(normalized)) {
            return trialBody;
        }
        if (!normalized.startsWith("http.body.")) {
            return null;
        }
        String relative = normalized.substring("http.body.".length());
        if (relative.startsWith("$")) {
            return null;
        }
        return JsonPathFacade.eval(trialBody, JsonPathFacade.toAbsolutePath(relative));
    }

    /**
     * 判断试算结果是否未命中：值为 null，或空 List / 空 JSONArray。
     * 标量与非空集合视为命中。
     */
    public static boolean isMiss(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof JSONArray arr) {
            return arr.isEmpty();
        }
        return false;
    }

    /**
     * 从上游 HTTP 节点解析响应 schema 叶路径集合。
     */
    private static Set<String> resolveSchemaPaths(GraphNode httpNode, Function<Long, TestProjectApi> apiResolver) {
        Map<String, Object> data = httpNode.getData();
        if (data == null) {
            return Set.of();
        }
        JSONObject dataJson = new JSONObject(data);
        if (!FlowHttpNodeVisitor.isProjectBoundHttp(dataJson)) {
            return Set.of();
        }
        Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(dataJson.get("testProjectApiId"));
        if (apiId == null) {
            return Set.of();
        }
        TestProjectApi api = apiResolver.apply(apiId);
        if (api == null) {
            return Set.of();
        }
        String responseConfig = TestProjectApiEffectiveConfigResolver.resolve(api).getResponseConfig();
        return FlowDesignApiSummarizer.summarizeResponsePaths(responseConfig);
    }

    /**
     * 沿入边向上游 DFS：返回最近一个「project 模式且已绑定 testProjectApiId」的 HTTP 节点。
     */
    private static GraphNode findUpstreamProjectHttp(
            String nodeId,
            Map<String, GraphNode> byId,
            Map<String, List<String>> incoming,
            Set<String> visiting) {
        if (nodeId == null || !visiting.add(nodeId)) {
            return null;
        }
        List<String> preds = incoming.getOrDefault(nodeId, List.of());
        for (String predId : preds) {
            GraphNode pred = byId.get(predId);
            if (pred == null) {
                continue;
            }
            String type = pred.getType() != null ? pred.getType().trim().toLowerCase() : "";
            if ("http".equals(type) && pred.getData() != null) {
                JSONObject dataJson = new JSONObject(pred.getData());
                if (FlowHttpNodeVisitor.isProjectBoundHttp(dataJson)
                        && FlowHttpNodeVisitor.parseTestProjectApiId(dataJson.get("testProjectApiId")) != null) {
                    return pred;
                }
            }
            GraphNode deeper = findUpstreamProjectHttp(predId, byId, incoming, visiting);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private static String resolveName(GraphNode node) {
        if (node.getData() != null) {
            Object name = node.getData().get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name).trim();
            }
        }
        return node.getId() != null ? node.getId() : "assert";
    }
}
