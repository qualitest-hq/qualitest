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
 * 设计期断言路径门禁。
 * <p>
 * 在 Staging 单单元确认、AI 提交 patch 预合并校验、以及写库保存 graph_json 时调用：
 * 找到 assert / condition 上游绑定项目接口的 HTTP 节点，按该接口<strong>响应 schema</strong>
 * 检查每条 {@code http.body…} 左值是否落在 schema 叶路径上；并硬拦误写的 Schema 关键字
 * {@code .items}（真实 JSON 数组没有这一层）。
 * <p>
 * 响应 example 仅供属性面板试算展示，<strong>不参与</strong>本门禁硬拦（占位 example 常与过滤器取值无关）。
 * <p>
 * 行为约定：
 * <ul>
 *   <li>仅检查左值以 {@code http.body} 开头的规则；{@code flow.*} / {@code env.*} 等跳过</li>
 *   <li>全图校验：找不到上游 project HTTP、接口加载失败、无 schema 叶路径时跳过，不报错</li>
 *   <li>按节点 id 收窄校验（Staging 确认 assert/condition）：无上游时硬拦，提示补齐上游 HTTP 与入边</li>
 *   <li>正式 Run 不经过本门禁（仅图结构校验）</li>
 * </ul>
 */
public final class AssertPathDesignGate {

    /** 路径段 {@code items}：JSON Schema 描述数组元素的关键字，不应出现在断言左值里 */
    private static final Pattern SCHEMA_ITEMS_SEGMENT = Pattern.compile("(^|\\.)items(\\.|\\[|$)");

    private AssertPathDesignGate() {
    }

    /**
     * 扫描图中全部 assert、condition 节点，按 schema 校验其 http.body 左值。
     *
     * @param graph       待检查的图（通常为合并后的副本）
     * @param apiResolver 按 testProjectApiId 取接口定义；返回 null 表示该节点跳过
     * @return 错误文案列表；空列表表示通过或无可检项
     */
    public static List<String> validate(GraphJson graph, Function<Long, TestProjectApi> apiResolver) {
        return validate(graph, apiResolver, null);
    }

    /**
     * 按 schema 校验 http.body 左值；可按节点 id 收窄范围。
     * <p>
     * {@code onlyNodeIds == null}：全图校验（保存 / AI submit）；无上游则跳过。
     * {@code onlyNodeIds} 非空：只校验这些 id 的 assert/condition；无上游则硬拦。
     *
     * @param graph        待检查的图（通常为含 pending Staging 的预览图）
     * @param apiResolver  按 testProjectApiId 取接口定义
     * @param onlyNodeIds  仅校验这些节点 id；null 表示全图
     * @return 错误文案列表
     */
    public static List<String> validate(
            GraphJson graph,
            Function<Long, TestProjectApi> apiResolver,
            Set<String> onlyNodeIds) {
        List<String> errors = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return errors;
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
            if (upstreamHttp == null) {
                if (scoped) {
                    errors.add(kindLabel + "节点「" + nodeName + "」尚无上游项目 HTTP（含未确认 Staging 边/节点），无法校验路径；"
                            + "请先确认上游 HTTP 与入边");
                }
                continue;
            }
            Set<String> schemaPaths = resolveSchemaPaths(upstreamHttp, apiResolver);
            if (schemaPaths.isEmpty()) {
                continue;
            }
            if ("assert".equals(type)) {
                validateRules(kindLabel, nodeName, "rules", node.getData().get("rules"), schemaPaths, errors);
            } else {
                for (Object b : GraphDataLists.asList(node.getData().get("branches"))) {
                    Map<?, ?> branch = GraphDataLists.asMap(b);
                    if (branch == null) {
                        continue;
                    }
                    validateRules(kindLabel, nodeName, "conditions", branch.get("conditions"), schemaPaths, errors);
                }
            }
        }
        return errors;
    }

    /**
     * 逐条检查规则左值：禁止 Schema 关键字 {@code .items}；其余与上游响应 schema 叶路径比对。
     */
    private static void validateRules(
            String kindLabel,
            String nodeName,
            String fieldName,
            Object rulesRaw,
            Set<String> schemaPaths,
            List<String> errors) {
        List<?> rules = GraphDataLists.asList(rulesRaw);
        for (int i = 0; i < rules.size(); i++) {
            Map<?, ?> rule = GraphDataLists.asMap(rules.get(i));
            if (rule == null) {
                continue;
            }
            Object leftObj = rule.get("left");
            if (leftObj == null) {
                continue;
            }
            String left = PlaceholderResolver.normalizeAssertLeftPath(String.valueOf(leftObj).trim());
            if (!left.startsWith("http.body")) {
                continue;
            }
            if ("http.body".equals(left)) {
                continue;
            }
            if (!left.startsWith("http.body.")) {
                continue;
            }
            String relative = left.substring("http.body.".length());
            if (relative.isBlank()) {
                continue;
            }
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
            if (!HttpNodeApiHealthChecker.pathMatchesSchema(relative, schemaPaths)) {
                errors.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                        + "」在上游接口响应 schema 中未找到对应字段");
            }
        }
    }

    /**
     * 路径是否含 Schema 关键字段 {@code items}（如 {@code data.items.quantity}）。
     * 过滤器表达式内部的字段名不算（{@code @.items} 两侧不是段边界时由正则约束）。
     */
    static boolean containsJsonSchemaItemsSegment(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return SCHEMA_ITEMS_SEGMENT.matcher(relativePath.trim()).find();
    }

    /**
     * 对断言左值求值（Java 侧软试算 / 单测用；属性面板软试算在前端 jsonPathTrial）。
     * 门禁硬拦不依赖本方法。
     */
    public static Object evalAssertLeft(Object trialBody, String left) {
        if (trialBody == null || left == null) {
            return null;
        }
        String normalized = PlaceholderResolver.normalizeAssertLeftPath(left.trim());
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
     * 判断试算是否未命中：null，或空 List / 空 JSONArray。
     * 标量、非空集合视为命中。供属性面板标红，不作为 Staging/保存硬拦条件。
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
