package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.CompareRuleEvaluator;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.graph.ConditionBranchTerminalSupport;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 测试流画布 graph_json 结构校验（保存、确认合并、运行前）。
 * <p>
 * 图级：节点/边、唯一开始节点、condition 分支与出边。<br>
 * HTTP：callMode、外联必填项；extracts 中 body 表达式须为可解析的 {@code $…} JsonPath。<br>
 * Assert / Condition：rules/branches 不可空；规则 left 非空、作用域合法、禁止 {@code http.body.$.…}、http.body 后缀 JsonPath 可解析。<br>
 * Assign：assignments 非空，name/op 合法。<br>
 * Delay：ms 可解析且不超过上限。<br>
 * Script / Subflow：language、subflowId 等。<br>
 * {@code ok=true} 当且仅当 errors 为空。
 */
@Component
public class GraphJsonValidator {

    private static final Set<String> ALLOWED_EDGE_KEYS = Set.of("id", "source", "target", "label");

    /**
     * 存在未确认连线时：开始节点「看起来像多个入口」的延后提示（写入 warnings，不硬拦）。
     */
    static final String DEFERRED_MULTI_START_WARNING =
            "尚有未确认的连线；确认边之后将只保留一个开始节点（当前看起来像多个入口）。";
    /**
     * 存在未确认连线时：开始节点「看起来缺失」的延后提示（写入 warnings）。
     */
    static final String DEFERRED_NO_START_WARNING =
            "尚有未确认的连线；确认边之后再校验开始节点（当前每个节点都有入边或图不完整）。";

    /**
     * 校验已解析的图结构（完整规则）。
     */
    public GraphValidationResult validate(GraphJson graph) {
        return validate(graph, GraphValidationOptions.full());
    }

    /**
     * 带选项的图校验。Staging 分批确认时可延后拓扑结构规则。
     */
    public GraphValidationResult validate(GraphJson graph, GraphValidationOptions options) {
        GraphValidationOptions effective = options != null ? options : GraphValidationOptions.full();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (graph == null) {
            errors.add("根对象必须是 JSON 对象");
            return GraphValidationResult.of(errors, warnings);
        }
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        Set<String> nodeIds = validateTypedNodes(nodes, errors, warnings);
        validateTypedEdges(edges, nodeIds, errors);
        validateConditionBranches(nodes, edges, warnings);
        if (effective.isDeferTopologyStructureRules()) {
            appendDeferredStartNodeWarning(nodes, edges, warnings);
        } else {
            appendStartNodeError(nodes, edges, errors);
        }
        appendMetaRunError(graph, errors);
        if (nodes.isEmpty()) {
            warnings.add("nodes 为空，导入后将得到空白画布");
        }
        return GraphValidationResult.of(errors, warnings);
    }

    /**
     * 校验原始 JSON 文本（含 {@code graph_json} / {@code graphJson} 解包）。
     */
    public GraphValidationResult validateJson(String json) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Object parsed;
        try {
            parsed = JSON.parse(json);
        } catch (Exception e) {
            errors.add("JSON 解析失败：" + e.getMessage());
            return GraphValidationResult.of(errors, warnings);
        }
        if (!(parsed instanceof Map)) {
            errors.add("根对象必须是 JSON 对象");
            return GraphValidationResult.of(errors, warnings);
        }
        JSONObject root = parsed instanceof JSONObject ? (JSONObject) parsed : new JSONObject((Map<?, ?>) parsed);
        JSONObject graph = unwrapGraphPayload(root);
        if (!(graph.get("nodes") instanceof JSONArray)) {
            errors.add("缺少 nodes 数组");
        }
        if (!(graph.get("edges") instanceof JSONArray)) {
            errors.add("缺少 edges 数组");
        }
        if (!errors.isEmpty()) {
            return GraphValidationResult.of(errors, warnings);
        }
        JSONArray nodesArr = graph.getJSONArray("nodes");
        JSONArray edgesArr = graph.getJSONArray("edges");
        Set<String> nodeIds = validateRawNodes(nodesArr, errors, warnings);
        validateRawEdges(edgesArr, nodeIds, errors, warnings);
        validateRawConditionBranches(nodesArr, edgesArr, warnings);
        appendStartNodeErrorFromRaw(nodesArr, edgesArr, errors);
        appendMetaRunErrorFromRaw(graph, errors);
        if (nodesArr.isEmpty()) {
            warnings.add("nodes 为空，导入后将得到空白画布");
        }
        return GraphValidationResult.of(errors, warnings);
    }

    /**
     * 校验流程是否恰有一个开始节点（无入边节点）。
     */
    public StartNodesValidation validateStartNodes(GraphJson graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return new StartNodesValidation(true, List.of(), "");
        }
        List<GraphNode> nodes = graph.getNodes();
        List<String> ids = findStartNodeIds(
                collectNodeIds(nodes),
                collectTypedEdgeTargets(graph.getEdges())
        );
        return buildStartNodesValidation(ids, id -> formatTypedStartNodeName(nodes, id));
    }

    /**
     * 从 {@code graph_json} / {@code graphJson} 包装中取出图对象。
     */
    public JSONObject unwrapGraphPayload(JSONObject raw) {
        if (raw == null) {
            return new JSONObject();
        }
        if (raw.get("graph_json") instanceof JSONObject) {
            return raw.getJSONObject("graph_json");
        }
        if (raw.get("graphJson") instanceof JSONObject) {
            return raw.getJSONObject("graphJson");
        }
        return raw;
    }

    private Set<String> validateTypedNodes(List<GraphNode> nodes, List<String> errors, List<String> warnings) {
        Set<String> nodeIds = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            String p = "nodes[" + i + "]";
            GraphNode node = nodes.get(i);
            if (node == null) {
                errors.add(p + " 不是有效对象");
                continue;
            }
            validateNodeFields(
                    p,
                    node.getId(),
                    node.getType(),
                    node.getPosition(),
                    node.getData(),
                    nodeIds,
                    errors,
                    warnings
            );
        }
        return nodeIds;
    }

    private void validateTypedEdges(
            List<GraphEdge> edges,
            Set<String> nodeIds,
            List<String> errors
    ) {
        Set<String> edgeIds = new HashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            String p = "edges[" + i + "]";
            GraphEdge edge = edges.get(i);
            if (edge == null) {
                errors.add(p + " 不是有效对象");
                continue;
            }
            String id = edge.getId();
            if (id == null || id.isBlank()) {
                errors.add(p + " 缺少 id");
            } else if (edgeIds.contains(id)) {
                errors.add(p + " id 重复：" + id);
            } else {
                edgeIds.add(id);
            }
            String source = edge.getSource();
            if (source == null || source.isBlank()) {
                errors.add(p + " 缺少 source");
            } else if (!nodeIds.contains(source)) {
                errors.add(p + " source 不存在：" + source);
            }
            String target = edge.getTarget();
            if (target == null || target.isBlank()) {
                errors.add(p + " 缺少 target");
            } else if (!nodeIds.contains(target)) {
                errors.add(p + " target 不存在：" + target);
            }
        }
    }

    private Set<String> validateRawNodes(JSONArray nodesArr, List<String> errors, List<String> warnings) {
        Set<String> nodeIds = new HashSet<>();
        for (int i = 0; i < nodesArr.size(); i++) {
            String p = "nodes[" + i + "]";
            Object item = nodesArr.get(i);
            if (!(item instanceof JSONObject node)) {
                errors.add(p + " 不是有效对象");
                continue;
            }
            String id = node.getString("id");
            String type = node.getString("type");
            JSONObject position = node.getJSONObject("position");
            JSONObject data = node.getJSONObject("data");
            GraphNodePosition pos = null;
            if (position != null) {
                pos = GraphNodePosition.builder()
                        .x(position.getDoubleValue("x"))
                        .y(position.getDoubleValue("y"))
                        .build();
            }
            validateNodeFields(p, id, type, pos, data, nodeIds, errors, warnings);
        }
        return nodeIds;
    }

    private void validateNodeFields(
            String p,
            String id,
            String type,
            GraphNodePosition position,
            Map<String, Object> data,
            Set<String> nodeIds,
            List<String> errors,
            List<String> warnings
    ) {
        if (id == null || id.isBlank()) {
            errors.add(p + " 缺少 id");
        } else if (nodeIds.contains(id)) {
            errors.add(p + " id 重复：" + id);
        } else {
            nodeIds.add(id);
        }

        if (type == null || type.isBlank() || !FlowNodeType.isKnown(type)) {
            errors.add(p + " type 无效：" + (type == null || type.isBlank() ? "(空)" : type));
        }
        if (position == null
                || !isFiniteNumber(position.getX())
                || !isFiniteNumber(position.getY())) {
            errors.add(p + " position 需包含数字 x / y");
        }
        if (data == null) {
            errors.add(p + " 缺少 data 对象");
        }

        if (FlowNodeType.HTTP.matches(type)) {
            validateHttpNodeFields(p, id, data, errors, warnings);
        }
        if (FlowNodeType.ASSERT.matches(type)) {
            validateAssertNodeFields(p, id, data, errors);
        }
        if (FlowNodeType.CONDITION.matches(type)) {
            Object branches = data != null ? data.get("branches") : null;
            if (!(branches instanceof List<?> list) || list.isEmpty()) {
                String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
                errors.add("条件节点「" + name + "」缺少 branches，请配置 IF/ELSE 分支");
            } else {
                validateConditionNodeFields(p, id, data, errors);
            }
        }
        if (FlowNodeType.ASSIGN.matches(type)) {
            validateAssignNodeFields(p, id, data, errors);
        }
        if (FlowNodeType.DELAY.matches(type)) {
            validateDelayNodeFields(p, id, data, errors);
        }
        validateScriptNodeFields(p, id, type, data, errors, warnings);
        validateSubflowNodeFields(p, id, type, data, errors, warnings);
    }

    /** 校验 assert 节点：rules 非空；每条 left / JsonPath。 */
    private void validateAssertNodeFields(
            String p,
            String id,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object rulesRaw = data != null ? data.get("rules") : null;
        if (!(rulesRaw instanceof List<?> rules) || rules.isEmpty()) {
            errors.add(p + " 断言节点「" + name + "」rules 不能为空");
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            Object item = rules.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            validateCompareRule(p + " 断言节点「" + name + "」rules[" + i + "]", map, errors);
        }
    }

    /** assign：assignments 非空；每条 name 非空且 op 合法。 */
    private void validateAssignNodeFields(
            String p,
            String id,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object raw = data != null ? data.get("assignments") : null;
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errors.add(p + " Assign 节点「" + name + "」assignments 不能为空");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                errors.add(p + " Assign 节点「" + name + "」assignments[" + i + "] 不是有效对象");
                continue;
            }
            Object nameObj = map.get("name");
            String varName = nameObj == null ? "" : String.valueOf(nameObj).trim();
            if (varName.isEmpty()) {
                errors.add(p + " Assign 节点「" + name + "」assignments[" + i + "] name 不能为空");
            }
            Object opObj = map.get("op");
            String op = opObj == null ? "" : String.valueOf(opObj).trim();
            if (op.isEmpty()) {
                errors.add(p + " Assign 节点「" + name + "」assignments[" + i + "] op 无效：(空)");
            } else if (!isSupportedAssignOp(op)) {
                errors.add(p + " Assign 节点「" + name + "」assignments[" + i + "] op 无效：" + op);
            }
        }
    }

    private static boolean isSupportedAssignOp(String op) {
        return "set".equals(op) || "add".equals(op) || "sub".equals(op)
                || "mul".equals(op) || "div".equals(op);
    }

    /** delay：ms 可解析且不超过上限。 */
    private void validateDelayNodeFields(
            String p,
            String id,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object raw = data != null ? data.get("ms") : null;
        Long ms = com.qualitest.flow.delay.DelayConstants.tryParseMs(raw);
        if (ms == null) {
            errors.add(p + " Delay 节点「" + name + "」缺少 ms 或无法解析");
            return;
        }
        if (ms > com.qualitest.flow.delay.DelayConstants.MAX_DELAY_MS) {
            errors.add(p + " Delay 节点「" + name + "」ms 超过上限 "
                    + com.qualitest.flow.delay.DelayConstants.MAX_DELAY_MS);
        }
    }

    /** 校验 condition 各分支 conditions 的 left / JsonPath。 */
    private void validateConditionNodeFields(
            String p,
            String id,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object branchesRaw = data.get("branches");
        if (!(branchesRaw instanceof List<?> branches)) {
            return;
        }
        for (int bi = 0; bi < branches.size(); bi++) {
            Object branchItem = branches.get(bi);
            if (!(branchItem instanceof Map<?, ?> branch)) {
                continue;
            }
            boolean terminal = ConditionBranchTerminalSupport.isTerminalBranch(branch);
            Object kindObj = branch.get("kind");
            String kind = kindObj != null ? String.valueOf(kindObj).trim() : "";
            Object targetObj = branch.get("target");
            String target = targetObj != null ? String.valueOf(targetObj).trim() : "";
            if (terminal) {
                if ("else".equals(kind)) {
                    errors.add(p + " 条件节点「" + name + "」ELSE 分支不可设为结束流程");
                } else if (!target.isEmpty()) {
                    errors.add(p + " 条件节点「" + name + "」branches[" + bi + "] terminal 与 target 不可同时配置");
                }
            }
            Object conditionsRaw = branch.get("conditions");
            if (!(conditionsRaw instanceof List<?> conditions)) {
                continue;
            }
            for (int ci = 0; ci < conditions.size(); ci++) {
                Object cond = conditions.get(ci);
                if (!(cond instanceof Map<?, ?> map)) {
                    continue;
                }
                validateCompareRule(
                        p + " 条件节点「" + name + "」branches[" + bi + "].conditions[" + ci + "]",
                        map,
                        errors
                );
            }
        }
    }

    /**
     * 校验单条比较规则：left 非空；作用域合法；禁止 {@code http.body.$.…}；
     * {@code http.body.} 后缀须为可解析的 JsonPath。
     */
    private void validateCompareRule(String prefix, Map<?, ?> rule, List<String> errors) {
        Object leftRaw = rule.get("left");
        String left = leftRaw == null ? "" : String.valueOf(leftRaw).trim();
        if (left.isEmpty()) {
            errors.add(prefix + " left 不能为空");
            return;
        }
        left = CompareRuleEvaluator.stripMustache(left);
        String normalized = PlaceholderResolver.normalizeAssertLeftPath(left);
        if (!isAllowedCompareLeftScope(normalized)) {
            errors.add(prefix + " left 作用域非法（须为 flow./env./asset./http. 或 $.…）：" + left);
            return;
        }
        if (normalized.startsWith("http.body.$")) {
            errors.add(prefix + " left 不可写成 http.body.$.…，请用 http.body.data… 或 $.data…：" + left);
            return;
        }
        if ("http.body".equals(normalized)) {
            return;
        }
        if (normalized.startsWith("http.body.")) {
            String relative = normalized.substring("http.body.".length());
            String abs = JsonPathFacade.toAbsolutePath(relative);
            if (!JsonPathFacade.isValidPath(abs)) {
                errors.add(prefix + " left JsonPath 无法解析：" + left);
            }
        }
    }

    /** left 允许的作用域前缀：flow / env / asset / http（含整段 http.body）。 */
    private static boolean isAllowedCompareLeftScope(String left) {
        if (left == null || left.isEmpty()) {
            return false;
        }
        return left.startsWith("flow.")
                || left.startsWith("env.")
                || left.startsWith("asset.")
                || left.startsWith("http.")
                || "http.body".equals(left);
    }

    /**
     * 校验 HTTP extracts：from=body 时 expr 非空、须以 {@code $} 开头且 JsonPath 可解析。
     */
    private void validateHttpExtracts(
            String p,
            String name,
            Map<String, Object> data,
            List<String> errors
    ) {
        Object extractsRaw = data != null ? data.get("extracts") : null;
        if (!(extractsRaw instanceof List<?> extracts) || extracts.isEmpty()) {
            return;
        }
        for (int i = 0; i < extracts.size(); i++) {
            Object item = extracts.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object fromObj = map.get("from");
            String from = fromObj == null ? "body" : String.valueOf(fromObj).trim().toLowerCase(Locale.ROOT);
            if (!"body".equals(from)) {
                continue;
            }
            Object exprObj = map.get("expr");
            String expr = exprObj == null ? "" : String.valueOf(exprObj).trim();
            if (expr.isEmpty()) {
                errors.add(p + " HTTP 节点「" + name + "」extracts[" + i + "] body 表达式不能为空");
                continue;
            }
            if (!expr.startsWith("$")) {
                errors.add(p + " HTTP 节点「" + name + "」extracts[" + i + "] body 表达式须以 $ 开头：" + expr);
                continue;
            }
            if (!JsonPathFacade.isValidPath(expr)) {
                errors.add(p + " HTTP 节点「" + name + "」extracts[" + i + "] JsonPath 无法解析：" + expr);
            }
        }
    }

    private void validateSubflowNodeFields(
            String p,
            String id,
            String type,
            Map<String, Object> data,
            List<String> errors,
            List<String> warnings
    ) {
        if (!FlowNodeType.SUBFLOW.matches(type)) {
            return;
        }
        // subflowId 缺失阻断保存/运行；inputs/outputs 空映射合法（asset 跨流 / meta.flowOutputs 回退）
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object subflowId = data != null ? data.get("subflowId") : null;
        if (subflowId == null || String.valueOf(subflowId).isBlank()) {
            errors.add(p + " 子流节点「" + name + "」缺少 subflowId");
        }
        Object policy = data != null ? data.get("versionPolicy") : null;
        if (policy != null && !String.valueOf(policy).isBlank()) {
            String pv = String.valueOf(policy).trim();
            if (!"pinned".equals(pv) && !"latest".equals(pv)) {
                errors.add(p + " 子流节点「" + name + "」versionPolicy 无效：" + pv);
            }
        }
    }

    private void validateHttpNodeFields(
            String p,
            String id,
            Map<String, Object> data,
            List<String> errors,
            List<String> warnings
    ) {
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        Object callModeObj = data != null ? data.get("callMode") : null;
        if (callModeObj == null || String.valueOf(callModeObj).isBlank()) {
            errors.add(p + " HTTP 节点「" + name + "」缺少 callMode");
            return;
        }
        String callMode = String.valueOf(callModeObj).trim();
        if (!FlowHttpCallMode.isKnown(callMode)) {
            errors.add(p + " HTTP 节点「" + name + "」callMode 无效：" + callMode);
            return;
        }
        if (FlowHttpCallMode.isProject(callMode)) {
            if (!hasTestProjectApiId(data)) {
                warnings.add("HTTP 节点「" + name + "」未绑定 testProjectApiId");
            }
            validateHttpExtracts(p, name, data, errors);
            return;
        }
        Object externalUrl = data.get("externalUrl");
        if (externalUrl == null || String.valueOf(externalUrl).isBlank()) {
            errors.add(p + " HTTP 节点「" + name + "」外联模式缺少 externalUrl");
        }
        Object httpMethod = data.get("httpMethod");
        if (httpMethod == null || String.valueOf(httpMethod).isBlank()) {
            errors.add(p + " HTTP 节点「" + name + "」外联模式缺少 httpMethod");
        }
        if (hasTestProjectApiId(data)) {
            errors.add(p + " HTTP 节点「" + name + "」外联模式不可填写 testProjectApiId");
        }
        validateHttpExtracts(p, name, data, errors);
    }

    /** script 节点：非法 language 为 error，空 source 为 warning */
    private void validateScriptNodeFields(
            String p,
            String id,
            String type,
            Map<String, Object> data,
            List<String> errors,
            List<String> warnings
    ) {
        if (!FlowNodeType.SCRIPT.matches(type)) {
            return;
        }
        String name = data != null && data.get("name") != null ? String.valueOf(data.get("name")) : id;
        String language = data != null && data.get("language") != null
                ? String.valueOf(data.get("language")).trim()
                : "";
        if (!com.qualitest.flow.script.ScriptConstants.isSupportedLanguage(language)) {
            errors.add(p + " script 节点「" + name + "」language 无效：" + (language.isEmpty() ? "(空)" : language));
        }
        String source = data != null && data.get("source") != null ? String.valueOf(data.get("source")) : "";
        if (source.isBlank()) {
            warnings.add("Script 节点「" + name + "」source 为空");
        }
    }

    private void validateRawEdges(
            JSONArray edgesArr,
            Set<String> nodeIds,
            List<String> errors,
            List<String> warnings
    ) {
        Set<String> edgeIds = new HashSet<>();
        for (int i = 0; i < edgesArr.size(); i++) {
            String p = "edges[" + i + "]";
            Object item = edgesArr.get(i);
            if (!(item instanceof JSONObject edge)) {
                errors.add(p + " 不是有效对象");
                continue;
            }
            String id = edge.getString("id");
            if (id == null || id.isBlank()) {
                errors.add(p + " 缺少 id");
            } else if (edgeIds.contains(id)) {
                errors.add(p + " id 重复：" + id);
            } else {
                edgeIds.add(id);
            }
            String source = edge.getString("source");
            if (source == null || source.isBlank()) {
                errors.add(p + " 缺少 source");
            } else if (!nodeIds.contains(source)) {
                errors.add(p + " source 不存在：" + source);
            }
            String target = edge.getString("target");
            if (target == null || target.isBlank()) {
                errors.add(p + " 缺少 target");
            } else if (!nodeIds.contains(target)) {
                errors.add(p + " target 不存在：" + target);
            }
            appendDisallowedEdgeFieldErrors(edge, id, i, errors);
        }
    }

    private void appendDisallowedEdgeFieldErrors(JSONObject edge, String id, int index, List<String> errors) {
        String ref = id != null && !id.isBlank() ? id : String.valueOf(index);
        for (String key : edge.keySet()) {
            if (!ALLOWED_EDGE_KEYS.contains(key)) {
                errors.add("边 " + ref + " 含不允许的字段：" + key);
            }
        }
    }

    private void validateConditionBranches(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            List<String> warnings
    ) {
        for (GraphNode node : nodes) {
            if (node == null || !FlowNodeType.CONDITION.matches(node.getType())) {
                continue;
            }
            Map<String, Object> data = node.getData();
            Object branchesObj = data != null ? data.get("branches") : null;
            if (!(branchesObj instanceof List<?> branches)) {
                continue;
            }
            for (Object branchObj : branches) {
                if (!(branchObj instanceof Map<?, ?> branch)) {
                    continue;
                }
                Object branchId = branch.get("id");
                Object target = branch.get("target");
                if (ConditionBranchTerminalSupport.isTerminalBranch(branch)) {
                    continue;
                }
                if (target == null || String.valueOf(target).isBlank()) {
                    warnings.add("条件节点 " + node.getId() + " 分支 " + (branchId != null ? branchId : "?") + " 未绑定 target");
                } else if (!hasOutgoingEdge(edges, node.getId(), String.valueOf(target))) {
                    warnings.add("条件节点 " + node.getId() + " 分支 " + branchId + " 的 target 无对应出边：" + target);
                }
            }
        }
    }

    private void validateRawConditionBranches(JSONArray nodesArr, JSONArray edgesArr, List<String> warnings) {
        for (Object item : nodesArr) {
            if (!(item instanceof JSONObject node)) {
                continue;
            }
            if (!FlowNodeType.CONDITION.matches(node.getString("type"))) {
                continue;
            }
            String nodeId = node.getString("id");
            JSONArray branches = node.getJSONObject("data") != null
                    ? node.getJSONObject("data").getJSONArray("branches")
                    : null;
            if (branches == null) {
                continue;
            }
            for (int j = 0; j < branches.size(); j++) {
                JSONObject branch = branches.getJSONObject(j);
                if (branch == null) {
                    continue;
                }
                String branchId = branch.getString("id");
                String target = branch.getString("target");
                if (ConditionBranchTerminalSupport.isTerminalBranch(branch)) {
                    continue;
                }
                if (target == null || target.isBlank()) {
                    warnings.add("条件节点 " + nodeId + " 分支 " + (branchId != null ? branchId : "?") + " 未绑定 target");
                } else if (!hasRawOutgoingEdge(edgesArr, nodeId, target)) {
                    warnings.add("条件节点 " + nodeId + " 分支 " + branchId + " 的 target 无对应出边：" + target);
                }
            }
        }
    }

    private void appendMetaRunError(GraphJson graph, List<String> errors) {
        if (graph.getMeta() == null) {
            errors.add("缺少 meta.scenarios");
            return;
        }
        if (graph.getMeta().getScenarios() == null
                || graph.getMeta().getScenarios().isEmpty()) {
            errors.add("meta.scenarios 不能为空");
        }
    }

    private void appendMetaRunErrorFromRaw(JSONObject graph, List<String> errors) {
        JSONObject meta = graph.getJSONObject("meta");
        if (meta == null) {
            errors.add("缺少 meta.scenarios");
            return;
        }
        JSONArray scenarios = meta.getJSONArray("scenarios");
        if (scenarios == null || scenarios.isEmpty()) {
            errors.add("meta.scenarios 不能为空");
        }
    }

    private void appendStartNodeError(List<GraphNode> nodes, List<GraphEdge> edges, List<String> errors) {
        if (nodes.isEmpty()) {
            return;
        }
        List<String> ids = findStartNodeIds(collectNodeIds(nodes), collectTypedEdgeTargets(edges));
        appendStartNodeMessage(ids, id -> formatTypedStartNodeName(nodes, id), errors);
    }

    /** Staging 分批确认：拓扑问题暂记 warning，待本批全部确认/拒绝后再做 error 校验 */
    private void appendDeferredStartNodeWarning(List<GraphNode> nodes, List<GraphEdge> edges, List<String> warnings) {
        if (nodes.isEmpty()) {
            return;
        }
        List<String> ids = findStartNodeIds(collectNodeIds(nodes), collectTypedEdgeTargets(edges));
        if (ids.isEmpty()) {
            warnings.add(DEFERRED_NO_START_WARNING);
            return;
        }
        if (ids.size() > 1) {
            warnings.add(DEFERRED_MULTI_START_WARNING);
        }
    }

    private void appendStartNodeErrorFromRaw(JSONArray nodesArr, JSONArray edgesArr, List<String> errors) {
        if (nodesArr.isEmpty()) {
            return;
        }
        List<String> ids = findStartNodeIds(collectRawNodeIds(nodesArr), collectRawEdgeTargets(edgesArr));
        appendStartNodeMessage(ids, id -> formatRawStartNodeName(nodesArr, id), errors);
    }

    private void appendStartNodeMessage(
            List<String> startIds,
            Function<String, String> nameFormatter,
            List<String> errors
    ) {
        StartNodesValidation check = buildStartNodesValidation(startIds, nameFormatter);
        if (!check.isOk()) {
            errors.add(check.getMessage());
        }
    }

    private StartNodesValidation buildStartNodesValidation(
            List<String> ids,
            Function<String, String> nameFormatter
    ) {
        if (ids.isEmpty()) {
            return new StartNodesValidation(
                    false,
                    ids,
                    "未找到开始节点（每个节点都有入边，可能存在无法触发的子图）"
            );
        }
        if (ids.size() > 1) {
            String names = ids.stream().map(nameFormatter).collect(Collectors.joining("、"));
            return new StartNodesValidation(
                    false,
                    ids,
                    "流程只能有一个开始节点，当前有 " + ids.size() + " 个：" + names
            );
        }
        return new StartNodesValidation(true, ids, "");
    }

    private List<String> findStartNodeIds(Set<String> nodeIds, Set<String> incomingTargets) {
        List<String> startIds = new ArrayList<>();
        for (String nodeId : nodeIds) {
            if (!incomingTargets.contains(nodeId)) {
                startIds.add(nodeId);
            }
        }
        return startIds;
    }

    private Set<String> collectNodeIds(List<GraphNode> nodes) {
        Set<String> nodeIds = new HashSet<>();
        for (GraphNode node : nodes) {
            if (node != null && node.getId() != null && !node.getId().isBlank()) {
                nodeIds.add(node.getId());
            }
        }
        return nodeIds;
    }

    private Set<String> collectRawNodeIds(JSONArray nodesArr) {
        Set<String> nodeIds = new HashSet<>();
        for (Object item : nodesArr) {
            if (item instanceof JSONObject node) {
                String id = node.getString("id");
                if (id != null && !id.isBlank()) {
                    nodeIds.add(id);
                }
            }
        }
        return nodeIds;
    }

    private Set<String> collectTypedEdgeTargets(List<GraphEdge> edges) {
        Set<String> targets = new HashSet<>();
        if (edges == null) {
            return targets;
        }
        for (GraphEdge edge : edges) {
            if (edge != null && edge.getTarget() != null) {
                targets.add(edge.getTarget());
            }
        }
        return targets;
    }

    private Set<String> collectRawEdgeTargets(JSONArray edgesArr) {
        Set<String> targets = new HashSet<>();
        for (Object item : edgesArr) {
            if (item instanceof JSONObject edge) {
                String target = edge.getString("target");
                if (target != null) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    private String formatTypedStartNodeName(List<GraphNode> nodes, String id) {
        for (GraphNode node : nodes) {
            if (node != null && id.equals(node.getId())) {
                Map<String, Object> data = node.getData();
                if (data != null && data.get("name") != null) {
                    return String.valueOf(data.get("name"));
                }
                return FlowNodeType.labelOf(node.getType());
            }
        }
        return id;
    }

    private String formatRawStartNodeName(JSONArray nodesArr, String id) {
        for (Object item : nodesArr) {
            if (item instanceof JSONObject node && id.equals(node.getString("id"))) {
                JSONObject data = node.getJSONObject("data");
                if (data != null && data.get("name") != null) {
                    return data.getString("name");
                }
                return FlowNodeType.labelOf(node.getString("type"));
            }
        }
        return id;
    }

    private boolean hasTestProjectApiId(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object value = data.get("testProjectApiId");
        return value != null && !String.valueOf(value).isBlank();
    }

    private boolean hasOutgoingEdge(List<GraphEdge> edges, String source, String target) {
        if (edges == null) {
            return false;
        }
        for (GraphEdge edge : edges) {
            if (edge != null && source.equals(edge.getSource()) && target.equals(edge.getTarget())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRawOutgoingEdge(JSONArray edgesArr, String source, String target) {
        for (Object item : edgesArr) {
            if (item instanceof JSONObject edge) {
                if (source.equals(edge.getString("source")) && target.equals(edge.getString("target"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFiniteNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
