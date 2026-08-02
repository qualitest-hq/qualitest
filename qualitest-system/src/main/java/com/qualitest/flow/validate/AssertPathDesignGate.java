package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.JsonSchemaExampleGenerator;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 设计期断言路径门禁。
 * <p>
 * 在 Staging 单单元确认、AI 提交 patch 预合并校验时调用：
 * 找到 assert / condition 上游绑定项目接口的 HTTP 节点，用该接口响应示例（无示例时按 schema 生成）
 * 对每条 {@code http.body…} 左值做 JsonPath 试算。
 * 试算结果为 {@code null} 或空数组时记为错误，阻止错路径（例如把 Schema 关键字 {@code items}
 * 写成 {@code data.items}）进入画布。
 * <p>
 * 行为约定：
 * <ul>
 *   <li>仅检查左值以 {@code http.body} 开头的规则；{@code flow.*} / {@code env.*} 等跳过</li>
 *   <li>找不到上游 project HTTP、接口加载失败、无 example 且无法从 schema 生成时跳过，不报错</li>
 *   <li>普通保存与正式 Run 不经过本门禁</li>
 * </ul>
 */
public final class AssertPathDesignGate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssertPathDesignGate() {
    }

    /**
     * 扫描图中全部 assert、condition 节点，试算其 http.body 左值。
     *
     * @param graph       待检查的图（通常为合并后的副本）
     * @param apiResolver 按 testProjectApiId 取接口定义；返回 null 表示该节点跳过
     * @return 错误文案列表；空列表表示通过或无可检项
     */
    public static List<String> validate(GraphJson graph, Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return errors;
        }
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        // 节点 id → 节点
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null) {
                byId.put(n.getId(), n);
            }
        }
        // 目标节点 id → 入边来源 id 列表（用于向上游回溯）
        Map<String, List<String>> incoming = new HashMap<>();
        for (GraphEdge e : edges) {
            if (e == null || e.getTarget() == null || e.getSource() == null) {
                continue;
            }
            incoming.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e.getSource());
        }

        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if (!"assert".equals(type) && !"condition".equals(type)) {
                continue;
            }
            GraphNode upstreamHttp = findUpstreamProjectHttp(node.getId(), byId, incoming, new HashSet<>());
            if (upstreamHttp == null) {
                continue;
            }
            Object trialBody = resolveTrialBody(upstreamHttp, apiResolver);
            if (trialBody == null) {
                continue;
            }
            String nodeName = resolveName(node);
            String kindLabel = "assert".equals(type) ? "断言" : "条件";
            if ("assert".equals(type)) {
                // assert 节点规则在 data.rules[]
                validateRules(kindLabel, nodeName, "rules", node.getData().get("rules"), trialBody, errors);
            } else {
                // condition 各分支条件在 branches[].conditions[]
                for (Object b : asList(node.getData().get("branches"))) {
                    Map<?, ?> branch = asMap(b);
                    if (branch == null) {
                        continue;
                    }
                    validateRules(kindLabel, nodeName, "conditions", branch.get("conditions"), trialBody, errors);
                }
            }
        }
        return errors;
    }

    /**
     * 逐条检查规则左值：规范化后若以 http.body 开头则试算，未命中则追加错误。
     *
     * @param kindLabel  文案前缀（断言 / 条件）
     * @param nodeName   节点展示名
     * @param fieldName  字段名（rules / conditions），写入错误定位
     * @param rulesRaw   规则列表原始值
     * @param trialBody  试算用的响应体
     * @param errors     错误收集列表
     */
    private static void validateRules(
            String kindLabel,
            String nodeName,
            String fieldName,
            Object rulesRaw,
            Object trialBody,
            List<String> errors) {
        List<?> rules = asList(rulesRaw);
        for (int i = 0; i < rules.size(); i++) {
            Map<?, ?> rule = asMap(rules.get(i));
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
            Object actual = evalAssertLeft(trialBody, left);
            if (isMiss(actual)) {
                errors.add(kindLabel + "节点「" + nodeName + "」" + fieldName + "[" + i + "] 左值「" + left
                        + "」在上游接口响应示例上试算未命中（空或 []）。"
                        + "数组请用 data[0]/data[*]/data[?(@.field==…)]，不要写 JSON Schema 关键字 .items");
            }
        }
    }

    /**
     * 对断言左值求值。
     * <ul>
     *   <li>{@code http.body} → 返回整个 trialBody</li>
     *   <li>{@code http.body.xxx} → 去掉前缀后按 JsonPath 求值</li>
     *   <li>{@code http.body.$.…} 或非 http.body 前缀 → 返回 null（非法或非本门禁范围）</li>
     * </ul>
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
     * 标量、非空集合视为命中。
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
     * 从上游 HTTP 节点解析试算 body：须为绑定了 testProjectApiId 的 project 调用，
     * 再取其生效 responseConfig 中的响应示例。
     */
    private static Object resolveTrialBody(GraphNode httpNode, Function<Long, TestProjectApi> apiResolver) {
        Map<String, Object> data = httpNode.getData();
        if (data == null) {
            return null;
        }
        JSONObject dataJson = new JSONObject(data);
        if (!FlowHttpNodeVisitor.isProjectBoundHttp(dataJson)) {
            return null;
        }
        Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(dataJson.get("testProjectApiId"));
        if (apiId == null) {
            return null;
        }
        TestProjectApi api = apiResolver.apply(apiId);
        if (api == null) {
            return null;
        }
        String responseConfig = TestProjectApiEffectiveConfigResolver.resolve(api).getResponseConfig();
        return extractResponseExample(responseConfig);
    }

    /**
     * 从 responseConfig JSON 取试算用响应体。
     * 优先用 {@code responses[0].example}（字符串会先尝试解析为 JSON）；
     * 没有 example 时用首个响应的 schema 生成一份示例；都没有则返回 null。
     */
    public static Object extractResponseExample(String responseConfig) {
        if (responseConfig == null || responseConfig.isBlank()) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(responseConfig);
            if (root == null) {
                return null;
            }
            JSONArray responses = root.getJSONArray("responses");
            if (responses == null || responses.isEmpty()) {
                return null;
            }
            JSONObject first = responses.getJSONObject(0);
            if (first == null) {
                return null;
            }
            Object example = first.get("example");
            if (example != null && !(example instanceof String && ((String) example).isBlank())) {
                if (example instanceof String s) {
                    try {
                        return JSON.parse(s);
                    } catch (Exception e) {
                        return s;
                    }
                }
                return example;
            }
            Object schema = first.get("schema");
            if (schema == null) {
                return null;
            }
            JsonNode schemaNode = MAPPER.readTree(JSON.toJSONString(schema));
            JsonNode generated = JsonSchemaExampleGenerator.generate(schemaNode);
            if (generated == null || generated.isNull()) {
                return null;
            }
            return JSON.parse(generated.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 沿入边向上游 DFS：返回最近一个「project 模式且已绑定 testProjectApiId」的 HTTP 节点。
     * {@code visiting} 防环；外联 HTTP 或未绑定接口的节点继续往更上游找。
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

    /** 错误文案用的节点名：优先 data.name，否则节点 id。 */
    private static String resolveName(GraphNode node) {
        if (node.getData() != null) {
            Object name = node.getData().get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name).trim();
            }
        }
        return node.getId() != null ? node.getId() : "assert";
    }

    /** Map 或 JSONObject 转为 Map 视图；其它类型返回 null。 */
    private static Map<?, ?> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        if (raw instanceof JSONObject obj) {
            return obj;
        }
        return null;
    }

    /** List 或 JSONArray 转为列表视图；其它类型返回空列表。 */
    private static List<?> asList(Object raw) {
        if (raw instanceof List<?> list) {
            return list;
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        return List.of();
    }
}
