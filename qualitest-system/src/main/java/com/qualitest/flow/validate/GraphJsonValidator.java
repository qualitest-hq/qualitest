package com.qualitest.flow.validate;

import com.qualitest.flow.model.FlowNodeType;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.CompareRuleEvaluator;
import com.qualitest.flow.context.JsonPathFacade;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.HttpStatusCodes;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.HttpRetryUntilSupport;
import com.qualitest.flow.input.InputFieldTypes;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphNodeShellSupport;
import com.qualitest.flow.model.GraphSchemaVersions;
import com.qualitest.flow.openhttp.OpenHttpRouteCatalog;
import com.qualitest.flow.run.FanoutSupport;
import com.qualitest.flow.run.GraphWalker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 测试流画布 graph_json 结构校验。
 * <p>
 * 默认（完整）检查：节点/边形状、开始节点与入口规则、各节点类型字段、
 * 条件分支与出边对齐、scenario-entry 场景、
 * HTTP 入口须 method / 非空 path / envId、HTTP/子流入口必须配置 {@code fallbackOut}、
 * 合法返回节点仅为 {@code http-return} / {@code subflow-return}
 * （裸 {@code return} 属未知 type；仅含场景入口的图禁止出现返回节点）、
 * 各入口可达子图上的返回 type 必须匹配该入口种类、
 * 同一顺序路径上不可出现两个返回节点（条件节点各出边分别累计，互不影响）。
 * 可选「仅落库地板」：只检查节点 id、边端点，允许半成品图写入数据库。<br>
 * 可选「延后拓扑」：跳过开始节点等拓扑硬拦。<br>
 * {@code ok=true} 当且仅当 errors 为空。
 */
@Component
public class GraphJsonValidator {

    private static final Set<String> ALLOWED_EDGE_KEYS = Set.of("id", "source", "target", "label");

    /** §2.5：data 不得承载画布 UI 状态字段 */
    private static final Set<String> FORBIDDEN_DATA_UI_KEYS =
            Set.of("selected", "dragging", "highlighted", "hovered");

    /**
     * 校验已解析的图结构（完整规则）。
     */
    public GraphValidationResult validate(GraphJson graph) {
        return validate(graph, GraphValidationOptions.full());
    }

    /**
     * 按选项校验已解析的图。
     * persistMinimalOnly 为 true 时只做落库地板；deferTopologyStructureRules 为 true 时
     * 暂不校验开始节点与「条件分支 target ↔ 出边」对齐（半成品 / 待确认连线阶段）。
     */
    public GraphValidationResult validate(GraphJson graph, GraphValidationOptions options) {
        GraphValidationOptions effective = options != null ? options : GraphValidationOptions.full();
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            errors.add("根对象必须是 JSON 对象");
            return GraphValidationResult.of(errors);
        }
        if (effective.isPersistMinimalOnly()) {
            validatePersistMinimal(graph, errors);
            return GraphValidationResult.of(errors);
        }
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        Set<String> nodeIds = validateTypedNodes(nodes, errors);
        validateTypedEdges(edges, nodeIds, errors);
        validateConditionBranches(nodes, edges, errors, effective.isDeferTopologyStructureRules());
        // 半成品（拓扑暂不可信）阶段不提示开始节点问题；完整校验时才开始节点硬拦
        if (!effective.isDeferTopologyStructureRules()) {
            appendStartNodeError(nodes, edges, errors);
        }
        appendSchemaVersionFinding(graph, errors);
        appendMetaRunError(graph, errors, effective.isDeferTopologyStructureRules());
        appendReturnAndEntryRules(nodes, edges, errors);
        return GraphValidationResult.of(errors);
    }

    /**
     * 落库地板校验：节点须有非空且不重复的 id；边须有 source/target。
     * 不检查 callMode、开始节点、断言规则等字段级内容。
     */
    private void validatePersistMinimal(GraphJson graph, List<String> errors) {
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            GraphNode node = nodes.get(i);
            String id = node != null ? node.getId() : null;
            if (id == null || id.isBlank()) {
                errors.add("nodes[" + i + "] 缺少 id");
                continue;
            }
            if (!ids.add(id)) {
                errors.add("nodes 存在重复 id：" + id);
            }
        }
        for (int i = 0; i < edges.size(); i++) {
            GraphEdge edge = edges.get(i);
            if (edge == null
                    || edge.getSource() == null || edge.getSource().isBlank()
                    || edge.getTarget() == null || edge.getTarget().isBlank()) {
                errors.add("edges[" + i + "] 缺少 source/target");
            }
        }
    }

    /**
     * 校验原始 JSON 文本（完整规则）。
     */
    public GraphValidationResult validateJson(String json) {
        return validateJson(json, GraphValidationOptions.full());
    }

    /**
     * 校验原始 JSON 文本（含 {@code graph_json} / {@code graphJson} 解包）。
     * 先做 JSON 形状与节点/边字段级检查；能解析为 {@link GraphJson} 后再合并
     * typed 规则（入口/返回/扇出等），避免「JSON 已过检」假绿。
     * deferTopology 时跳过开始节点 / 条件边对齐硬拦。
     */
    public GraphValidationResult validateJson(String json, GraphValidationOptions options) {
        GraphValidationOptions effective = options != null ? options : GraphValidationOptions.full();
        List<String> errors = new ArrayList<>();
        Object parsed;
        try {
            parsed = JSON.parse(json);
        } catch (Exception e) {
            errors.add("JSON 解析失败：" + e.getMessage());
            return GraphValidationResult.of(errors);
        }
        if (!(parsed instanceof Map)) {
            errors.add("根对象必须是 JSON 对象");
            return GraphValidationResult.of(errors);
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
            return GraphValidationResult.of(errors);
        }
        JSONArray nodesArr = graph.getJSONArray("nodes");
        JSONArray edgesArr = graph.getJSONArray("edges");
        Set<String> nodeIds = validateRawNodes(nodesArr, errors);
        validateRawEdges(edgesArr, nodeIds, errors);
        validateRawConditionBranches(nodesArr, edgesArr, errors);
        if (!effective.isDeferTopologyStructureRules()) {
            appendStartNodeErrorFromRaw(nodesArr, edgesArr, errors);
            appendMetaRunErrorFromRaw(graph, errors);
        }
        try {
            GraphJson typed = GraphJson.parse(graph.toJSONString());
            GraphValidationResult typedResult = validate(typed, effective);
            if (typedResult != null && typedResult.getErrors() != null) {
                for (String e : typedResult.getErrors()) {
                    if (e != null && !errors.contains(e)) {
                        errors.add(e);
                    }
                }
            }
        } catch (Exception ignored) {
            // 形状级错误已在上方收集；无法再跑 typed 全量时不追加
        }
        return GraphValidationResult.of(errors);
    }

    /**
     * HTTP 入口开跑必填：method、规范化后非空 path、envId。
     * 保存不走本方法（半成品可落盘）；画布 errors 与开跑门禁会拦。
     */
    private void appendHttpEntryFieldErrors(GraphNode n, List<GraphNode> nodes, List<String> errors) {
        Map<String, Object> data = n.getData();
        String label = formatTypedStartNodeName(nodes, n.getId());
        String method = data != null && data.get("method") != null
                ? String.valueOf(data.get("method")).trim() : "";
        if (method.isEmpty()) {
            errors.add("HTTP 入口「" + label + "」须配置 method");
        }
        String pathRaw = data != null && data.get("path") != null
                ? String.valueOf(data.get("path")).trim() : "";
        if (OpenHttpRouteCatalog.normalizePath(pathRaw).isEmpty()) {
            errors.add("HTTP 入口「" + label + "」须配置非空 path");
        }
        Object envRaw = data != null ? data.get("envId") : null;
        String envId = envRaw == null ? "" : String.valueOf(envRaw).trim();
        if (envId.isEmpty()) {
            errors.add("HTTP 入口「" + label + "」须配置 envId");
        }
    }

    /**
     * HTTP 入口兜底出参须为完整包：status 为 100–599 的数字，且含 headers / body 键（值可为 null）。
     */
    private void appendHttpFallbackOutShapeErrors(String label, Map<?, ?> fb, List<String> errors) {
        if (HttpStatusCodes.coerce(fb.get("status")) == null) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut.status 须为 100–599 的数字");
        }
        if (!fb.containsKey("headers")) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut 须含 headers");
        }
        if (!fb.containsKey("body")) {
            errors.add("HTTP 入口「" + label + "」的 fallbackOut 须含 body");
        }
    }

    /**
     * 校验开始节点：空白图通过；有节点须至少有一个入口，且仅入口可作无入边起点。
     */
    public StartNodesValidation validateStartNodes(GraphJson graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return new StartNodesValidation(true, List.of(), "");
        }
        List<GraphNode> nodes = graph.getNodes();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        List<String> ids = findStartNodeIds(collectNodeIds(nodes), collectTypedEdgeTargets(edges));
        List<String> errors = new ArrayList<>();
        appendStartNodeError(nodes, edges, errors);
        if (errors.isEmpty()) {
            return new StartNodesValidation(true, ids, "");
        }
        return new StartNodesValidation(false, ids, errors.get(0));
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

    /**
     * 校验已解析的节点列表。
     * 先建立全图节点 id 到 type 的映射，再逐节点校验字段
     *（断言/条件左值若写 nodes.节点id.http…，用该映射检查目标是否存在且为 http）。
     */
    private Set<String> validateTypedNodes(List<GraphNode> nodes, List<String> errors) {
        Set<String> nodeIds = new HashSet<>();
        Map<String, String> nodeTypesById = new HashMap<>();
        for (GraphNode node : nodes) {
            if (node != null && node.getId() != null && !node.getId().isBlank()) {
                nodeTypesById.put(node.getId().trim(), node.getType() != null ? node.getType().trim() : "");
            }
        }
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
                    node.getName(),
                    node.getType(),
                    node.getPosition(),
                    node.getData(),
                    nodeIds,
                    nodeTypesById,
                    errors);
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

    /**
     * 校验原始 JSON 节点数组。
     * 先扫描建立 id→type，再逐节点做字段校验（含 nodes.节点id.http 引用目标检查）。
     */
    private Set<String> validateRawNodes(JSONArray nodesArr, List<String> errors) {
        Set<String> nodeIds = new HashSet<>();
        Map<String, String> nodeTypesById = new HashMap<>();
        for (int i = 0; i < nodesArr.size(); i++) {
            Object item = nodesArr.get(i);
            if (!(item instanceof JSONObject node)) {
                continue;
            }
            String id = node.getString("id");
            if (id != null && !id.isBlank()) {
                String type = node.getString("type");
                nodeTypesById.put(id.trim(), type != null ? type.trim() : "");
            }
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            String p = "nodes[" + i + "]";
            Object item = nodesArr.get(i);
            if (!(item instanceof JSONObject node)) {
                errors.add(p + " 不是有效对象");
                continue;
            }
            String id = node.getString("id");
            String nodeName = node.getString("name");
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
            validateNodeFields(p, id, nodeName, type, pos, data, nodeIds, nodeTypesById, errors);
        }
        return nodeIds;
    }

    /**
     * 校验单个节点的 id / name / type / position / data，以及按 type 分发的字段规则；
     * 末尾校验 alwaysRun、snapshotBefore 的布尔类型与允许挂载的节点类型。
     */
    private void validateNodeFields(
            String p,
            String id,
            String nodeName,
            String type,
            GraphNodePosition position,
            Map<String, Object> data,
            Set<String> nodeIds,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        if (id == null || id.isBlank()) {
            errors.add(p + " 缺少 id");
        } else if (nodeIds.contains(id)) {
            errors.add(p + " id 重复：" + id);
        } else {
            nodeIds.add(id);
        }

        if (nodeName == null || nodeName.isBlank()) {
            errors.add(p + " 缺少 name");
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
        } else {
            appendDataUiStateErrors(p, nodeDisplayName(nodeName, id), data, errors);
        }

        String displayName = nodeDisplayName(nodeName, id);

        if (FlowNodeType.HTTP.matches(type)) {
            validateHttpNodeFields(p, displayName, data, nodeTypesById, errors);
        } else if (data != null && data.containsKey(HttpRetryUntilSupport.DATA_KEY)) {
            errors.add(p + " 节点「" + displayName + "」retryUntil 仅允许挂在 HTTP 节点");
        }
        if (FlowNodeType.ASSERT.matches(type)) {
            validateAssertNodeFields(p, displayName, data, nodeTypesById, errors);
        }
        if (FlowNodeType.CONDITION.matches(type)) {
            Object branches = data != null ? data.get("branches") : null;
            if (!(branches instanceof List<?> list) || list.isEmpty()) {
                errors.add("条件节点「" + displayName + "」缺少 branches，请配置 IF/ELSE 分支");
            } else {
                validateConditionNodeFields(p, displayName, data, nodeTypesById, errors);
            }
        }
        if (FlowNodeType.ASSIGN.matches(type)) {
            validateAssignNodeFields(p, displayName, data, errors);
        }
        if (FlowNodeType.DELAY.matches(type)) {
            validateDelayNodeFields(p, displayName, data, errors);
        }
        if (FlowNodeType.INPUT.matches(type)) {
            validateInputNodeFields(p, displayName, data, errors);
        }
        validateScriptNodeFields(p, displayName, type, data, errors);
        validateSubflowNodeFields(p, displayName, type, data, errors);
        if (FlowNodeType.HTTP_RETURN.matches(type) || FlowNodeType.SUBFLOW_RETURN.matches(type)) {
            validateReturnNodeFields(p, displayName, type, data, errors);
        }
        GraphNodeBooleanFieldRules.validate(p, type, data, errors);
    }

    /** 节点展示名：顶层 name，空白则类型标签，再否则「(未知)」。 */
    public static String nodeDisplayName(GraphNode node) {
        return GraphNodeShellSupport.displayName(node);
    }

    /** 原始 JSON 节点展示名：顶层 name，空白则类型标签或 id。 */
    public static String nodeDisplayName(JSONObject node) {
        return GraphNodeShellSupport.displayName(node);
    }

    /** 仅用顶层 name 与 id 拼展示名（无类型信息时）。 */
    private static String nodeDisplayName(String topLevelName, String id) {
        return GraphNodeShellSupport.displayNameFromParts(topLevelName, id);
    }

    /**
     * §2.5：{@code data} 只存该 type 的业务配置，不得承载画布 UI 状态字段。
     */
    private static void appendDataUiStateErrors(
            String p, String displayName, Map<String, Object> data, List<String> errors) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (String key : FORBIDDEN_DATA_UI_KEYS) {
            if (data.containsKey(key)) {
                errors.add(p + " 节点「" + displayName + "」data 不得承载画布 UI 状态字段：" + key);
            }
        }
    }

    /**
     * 校验返回节点字段：from 必填；http-return 的 status 若写出须为 100–599；
     * subflow-return 禁止写 status。
     */
    private void validateReturnNodeFields(
            String p,
            String displayName,
            String type,
            Map<String, Object> data,
            List<String> errors
    ) {
        Object fromObj = data != null ? data.get("from") : null;
        String from = fromObj == null ? "" : String.valueOf(fromObj).trim();
        if (from.isEmpty()) {
            errors.add(p + " 返回节点「" + displayName + "」缺少 from");
        }
        if (FlowNodeType.HTTP_RETURN.matches(type) && data != null && data.containsKey("status")) {
            if (HttpStatusCodes.coerce(data.get("status")) == null) {
                errors.add(p + " http-return「" + displayName + "」status 须为 100–599 的整数");
            }
        }
        if (FlowNodeType.SUBFLOW_RETURN.matches(type) && data != null && data.containsKey("status")) {
            errors.add(p + " subflow-return「" + displayName + "」不允许 status");
        }
    }

    /** 校验 assert 节点：rules 非空；每条 left / JsonPath。 */
    private void validateAssertNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            Map<String, String> nodeTypesById,
            List<String> errors
    ) {
        String name = displayName;
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
            validateCompareRule(p + " 断言节点「" + name + "」rules[" + i + "]", map, nodeTypesById, errors);
        }
    }

    /**
     * assign：assignments 非空；每条 name 非空、op 为 set/add/sub/mul/div，
     * 写出的 scope 只能是 flow 或 out（缺省视为 flow；其余取值按画布异常报错）。
     */
    private void validateAssignNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = displayName;
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
            if (map.containsKey("scope") && !isSupportedAssignScope(map.get("scope"))) {
                errors.add(p + " Assign 节点「" + name + "」assignments[" + i
                        + "] scope 只能是 flow 或 out（当前为：" + describeAssignScope(map.get("scope")) + "）");
            }
        }
    }

    private static boolean isSupportedAssignOp(String op) {
        return "set".equals(op) || "add".equals(op) || "sub".equals(op)
                || "mul".equals(op) || "div".equals(op);
    }

    /**
     * assign 的 scope 是否合法：缺省不调用本方法；写出时须为 flow / out（去空白、忽略大小写），
     * 与运行时 {@code AssignNodeHandler} 的归一口径一致。
     */
    private static boolean isSupportedAssignScope(Object raw) {
        if (!(raw instanceof String s)) {
            return false;
        }
        String scope = s.trim().toLowerCase(java.util.Locale.ROOT);
        return "flow".equals(scope) || "out".equals(scope);
    }

    /** 校验错误里展示原始 scope 值 */
    private static String describeAssignScope(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof String s) {
            return "字符串 \"" + s + "\"";
        }
        if (raw instanceof Map<?, ?>) {
            return "对象";
        }
        if (raw instanceof List<?>) {
            return "数组";
        }
        return String.valueOf(raw);
    }

    /** delay：ms 可解析且不超过上限。 */
    private void validateDelayNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = displayName;
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

    /**
     * Input 节点：fields 非空；每项 name 非空且节点内唯一；type 合法；
     * select / multiselect 的 options 非空且每项 value 非空。
     */
    private void validateInputNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            List<String> errors
    ) {
        String name = displayName;
        Object raw = data != null ? data.get("fields") : null;
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errors.add(p + " Input 节点「" + name + "」fields 不能为空");
            return;
        }
        Set<String> fieldNames = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            String prefix = p + " Input 节点「" + name + "」fields[" + i + "]";
            if (!(item instanceof Map<?, ?> map)) {
                errors.add(prefix + " 不是有效对象");
                continue;
            }
            Object nameObj = map.get("name");
            String fieldName = nameObj == null ? "" : String.valueOf(nameObj).trim();
            if (fieldName.isEmpty()) {
                errors.add(prefix + " name 不能为空");
            } else if (!fieldNames.add(fieldName)) {
                errors.add(prefix + " name 重复：" + fieldName);
            }
            Object typeObj = map.get("type");
            String type = typeObj == null ? "" : String.valueOf(typeObj).trim();
            if (!type.isEmpty() && !InputFieldTypes.isKnownType(type)) {
                errors.add(prefix + " type 无效：" + type);
            }
            String normalized = InputFieldTypes.normalizeType(type);
            if (InputFieldTypes.requiresOptions(normalized)) {
                Object optionsRaw = map.get("options");
                if (!(optionsRaw instanceof List<?> options) || options.isEmpty()) {
                    errors.add(prefix + " options 不能为空");
                } else {
                    for (int oi = 0; oi < options.size(); oi++) {
                        Object opt = options.get(oi);
                        if (!(opt instanceof Map<?, ?> optMap)) {
                            errors.add(prefix + " options[" + oi + "] 不是有效对象");
                            continue;
                        }
                        Object value = optMap.get("value");
                        if (value == null || String.valueOf(value).trim().isEmpty()) {
                            errors.add(prefix + " options[" + oi + "] value 不能为空");
                        }
                    }
                }
            }
        }
    }

    /** 校验 condition 各分支 conditions 的 left / JsonPath。 */
    private void validateConditionNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            Map<String, String> nodeTypesById,
            List<String> errors
    ) {
        String name = displayName;
        Object branchesRaw = data.get("branches");
        if (!(branchesRaw instanceof List<?> branches)) {
            return;
        }
        for (int bi = 0; bi < branches.size(); bi++) {
            Object branchItem = branches.get(bi);
            if (!(branchItem instanceof Map<?, ?> branch)) {
                continue;
            }
            // 无 target 表示本分支结束本流；此处只校 conditions 规则本身
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
                        nodeTypesById,
                        errors
                );
            }
        }
    }

    /**
     * 校验单条比较规则的左值。
     * 检查：非空；作用域为 run / flow / env / in / out / asset / http / nodes；
     * 禁止 {@code http.body.$.…}；
     * {@code nodes.<id>.http…} 目标须为 http；{@code nodes.<id>.out…} 目标须为 subflow；
     * body 字段后缀须能解析为 JsonPath。
     *
     * @param prefix         错误文案前缀（含节点名与规则下标）
     * @param rule           单条规则 Map
     * @param nodeTypesById  全图节点 id → type，用于校验 nodes 引用
     * @param errors         错误列表（追加写入）
     */
    private void validateCompareRule(
            String prefix,
            Map<?, ?> rule,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        Object leftRaw = rule.get("left");
        String left = leftRaw == null ? "" : String.valueOf(leftRaw).trim();
        if (left.isEmpty()) {
            errors.add(prefix + " left 不能为空");
            return;
        }
        left = CompareRuleEvaluator.stripMustache(left);
        String normalized = PlaceholderResolver.normalizeAssertLeftPath(left);
        if (!isAllowedCompareLeftScope(normalized)) {
            errors.add(prefix + " left 作用域非法（须为 run./flow./env./in./out./asset./http./nodes.<id>.http.|out. 或 $.…）：" + left);
            return;
        }
        if (PlaceholderResolver.looksLikeNodesPath(normalized)) {
            PlaceholderResolver.NodesOutRef nodesOut = PlaceholderResolver.parseNodesOutPath(normalized);
            if (nodesOut != null) {
                validateNodesOutTarget(prefix, left, nodesOut, nodeTypesById, errors);
                return;
            }
            PlaceholderResolver.NodesHttpRef nodesRef = PlaceholderResolver.parseNodesHttpPath(normalized);
            if (nodesRef == null) {
                errors.add(prefix + " left 须为 nodes.<节点ID>.http.… 或 nodes.<节点ID>.out…：" + left);
                return;
            }
            validateNodesHttpTarget(prefix, left, nodesRef, nodeTypesById, errors);
            validateHttpBodyRelativePath(prefix, left, nodesRef.httpRest(), errors);
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

    /**
     * 校验 {@code nodes.<id>.http…} 引用的目标节点。
     * 节点不在图中，或类型不是 http 时写入 errors。
     */
    private static void validateNodesHttpTarget(
            String prefix,
            String left,
            PlaceholderResolver.NodesHttpRef nodesRef,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        Map<String, String> types = nodeTypesById != null ? nodeTypesById : Map.of();
        String targetType = types.get(nodesRef.nodeId());
        if (targetType == null) {
            errors.add(prefix + " left 引用的节点不存在：" + nodesRef.nodeId() + "（" + left + "）");
            return;
        }
        if (!FlowNodeType.HTTP.matches(targetType)) {
            errors.add(prefix + " left 只能引用 HTTP 节点，节点「" + nodesRef.nodeId()
                    + "」类型为 " + targetType + "（" + left + "）");
        }
    }

    /**
     * 校验 {@code nodes.<id>.out…} 引用的目标节点。
     * 节点不在图中，或类型不是 subflow 时写入 errors。
     */
    private static void validateNodesOutTarget(
            String prefix,
            String left,
            PlaceholderResolver.NodesOutRef nodesOut,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        Map<String, String> types = nodeTypesById != null ? nodeTypesById : Map.of();
        String targetType = types.get(nodesOut.nodeId());
        if (targetType == null) {
            errors.add(prefix + " left 引用的节点不存在：" + nodesOut.nodeId() + "（" + left + "）");
            return;
        }
        if (!FlowNodeType.SUBFLOW.matches(targetType)) {
            errors.add(prefix + " left 的 .out 只能引用子流节点，节点「" + nodesOut.nodeId()
                    + "」类型为 " + targetType + "（" + left + "）");
        }
    }

    /**
     * 校验 http 相对段里 body 字段路径是否合法。
     * 禁止 {@code body.$…}；{@code body.} 后的相对路径须能解析为 JsonPath；
     * 仅 {@code body} 或非 body 段（如 status）不做 JsonPath 检查。
     */
    private static void validateHttpBodyRelativePath(
            String prefix, String left, String httpRest, List<String> errors) {
        if (httpRest == null) {
            return;
        }
        if (httpRest.startsWith("body.$")) {
            errors.add(prefix + " left 不可写成 http.body.$.…，请用 http.body.data… 或 $.data…：" + left);
            return;
        }
        if ("body".equals(httpRest) || !httpRest.startsWith("body.")) {
            return;
        }
        String relative = httpRest.substring("body.".length());
        String abs = JsonPathFacade.toAbsolutePath(relative);
        if (!JsonPathFacade.isValidPath(abs)) {
            errors.add(prefix + " left JsonPath 无法解析：" + left);
        }
    }

    /**
     * 判断比较左值作用域是否合法。
     * 允许：run. / flow. / env. / in. / out. / asset. / http. / nodes. 前缀，以及整段 http.body。
     */
    private static boolean isAllowedCompareLeftScope(String left) {
        if (left == null || left.isEmpty()) {
            return false;
        }
        return left.startsWith("run.")
                || left.startsWith("flow.")
                || left.startsWith("env.")
                || left.startsWith("in.")
                || left.startsWith("out.")
                || left.startsWith("asset.")
                || left.startsWith("http.")
                || left.startsWith("nodes.")
                || "http.body".equals(left);
    }

    /**
     * 过滤器后紧跟数字下标：JsonPath 把 [n] 作用到每个命中元素而非结果集，常得到 []。
     * 例：{@code $[?(@.id==1)][0]}。单命中 extract 会自动解包，应去掉 [n]。
     */
    private static final Pattern FILTER_THEN_INDEX =
            Pattern.compile("\\[\\?\\(.*\\)\\]\\s*\\[\\d+\\]");

    /**
     * 校验 HTTP extracts：from=body 时 expr 非空、须以 {@code $} 开头且 JsonPath 可解析；
     * 过滤器后紧跟 [n] 时写入 errors。
     */
    private void validateHttpExtracts(
            String p,
            String name,
            Map<String, Object> data,
            List<String> errors) {
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
                continue;
            }
            if (FILTER_THEN_INDEX.matcher(expr).find()) {
                errors.add(p + " HTTP 节点「" + name + "」extracts[" + i
                        + "] 过滤器后的 [n] 作用于每个命中元素而非结果集，常得到 []；单命中会自动解包，去掉 [n] 即可："
                        + expr);
            }
        }
    }

    /**
     * 校验子流节点：须有 subflowId。
     */
    private void validateSubflowNodeFields(
            String p,
            String displayName,
            String type,
            Map<String, Object> data,
            List<String> errors
    ) {
        if (!FlowNodeType.SUBFLOW.matches(type)) {
            return;
        }
        String name = displayName;
        Object subflowId = data != null ? data.get("subflowId") : null;
        if (subflowId == null || String.valueOf(subflowId).isBlank()) {
            errors.add(p + " 子流节点「" + name + "」缺少 subflowId");
        }
    }

    /**
     * 校验 HTTP 节点：callMode、外联必填、extracts 的 JsonPath、
     * 以及可选的 retryUntil（次数、间隔、通过条件规则）。
     * project 模式未绑定接口不在此告警。
     */
    private void validateHttpNodeFields(
            String p,
            String displayName,
            Map<String, Object> data,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        String name = displayName;
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
            // project 模式未绑接口不在此告警（由 API 语义健康检查另行提示）
            validateHttpExtracts(p, name, data, errors);
            validateHttpRetryUntil(p, name, data, nodeTypesById, errors);
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
        validateHttpRetryUntil(p, name, data, nodeTypesById, errors);
    }

    /**
     * 校验 HTTP 节点上的 {@code retryUntil}：
     * 须为对象；maxAttempts 可解析且落在 1～上限；intervalMs 可解析且不超过上限；
     * until 若存在须为数组，且每条规则的 left 作用域与 JsonPath 合法。
     * 未配置该键则跳过。
     */
    private void validateHttpRetryUntil(
            String p,
            String name,
            Map<String, Object> data,
            Map<String, String> nodeTypesById,
            List<String> errors) {
        if (data == null || !data.containsKey(HttpRetryUntilSupport.DATA_KEY)) {
            return;
        }
        Object raw = data.get(HttpRetryUntilSupport.DATA_KEY);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            errors.add(p + " HTTP 节点「" + name + "」retryUntil 须为对象");
            return;
        }
        Object maxRaw = map.get("maxAttempts");
        if (maxRaw != null && !String.valueOf(maxRaw).isBlank()) {
            Integer maxAttempts = tryParsePositiveInt(maxRaw);
            if (maxAttempts == null) {
                errors.add(p + " HTTP 节点「" + name + "」retryUntil.maxAttempts 无法解析");
            } else if (maxAttempts < 1 || maxAttempts > HttpRetryUntilSupport.MAX_MAX_ATTEMPTS) {
                errors.add(p + " HTTP 节点「" + name + "」retryUntil.maxAttempts 须在 1～"
                        + HttpRetryUntilSupport.MAX_MAX_ATTEMPTS + " 之间");
            }
        }
        Object intervalRaw = map.get("intervalMs");
        if (intervalRaw != null && !String.valueOf(intervalRaw).isBlank()) {
            Long intervalMs = tryParseNonNegativeLong(intervalRaw);
            if (intervalMs == null) {
                errors.add(p + " HTTP 节点「" + name + "」retryUntil.intervalMs 无法解析");
            } else if (intervalMs > HttpRetryUntilSupport.MAX_INTERVAL_MS) {
                errors.add(p + " HTTP 节点「" + name + "」retryUntil.intervalMs 超过上限 "
                        + HttpRetryUntilSupport.MAX_INTERVAL_MS);
            }
        }
        Object untilRaw = map.get("until");
        if (untilRaw == null) {
            return;
        }
        if (!(untilRaw instanceof List<?> untilList)) {
            errors.add(p + " HTTP 节点「" + name + "」retryUntil.until 须为数组");
            return;
        }
        for (int i = 0; i < untilList.size(); i++) {
            Object item = untilList.get(i);
            if (!(item instanceof Map<?, ?> rule)) {
                continue;
            }
            // 通过条件每条规则走通用比较规则校验（left 作用域、nodes 引用、JsonPath）
            validateCompareRule(
                    p + " HTTP 节点「" + name + "」retryUntil.until[" + i + "]",
                    rule,
                    nodeTypesById,
                    errors);
        }
    }

    /** 解析为正整数；无法解析返回 null（用于校验报错，不做默认回落）。 */
    private static Integer tryParsePositiveInt(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析为长整型（可含 0）；无法解析返回 null。 */
    private static Long tryParseNonNegativeLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** script 节点：非法 language 为 error；空 source 不提示 */
    private void validateScriptNodeFields(
            String p,
            String displayName,
            String type,
            Map<String, Object> data,
            List<String> errors) {
        if (!FlowNodeType.SCRIPT.matches(type)) {
            return;
        }
        String name = displayName;
        String language = data != null && data.get("language") != null
                ? String.valueOf(data.get("language")).trim()
                : "";
        if (!com.qualitest.flow.script.ScriptConstants.isSupportedLanguage(language)) {
            errors.add(p + " script 节点「" + name + "」language 无效：" + (language.isEmpty() ? "(空)" : language));
        }
    }

    private void validateRawEdges(
            JSONArray edgesArr,
            Set<String> nodeIds,
            List<String> errors) {
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

    /**
     * 校验条件节点分支与出边是否对齐。
     * <p>
     * 分支无 {@code target}：表示命中后本流正常结束，合法。<br>
     * 分支有 {@code target}：须存在同源同目标的出边，否则记入 errors。<br>
     * 条件节点有出边：须有分支的 {@code target} 认领该目标，否则记入 errors。
     *
     * @param nodes  节点列表
     * @param edges  边列表
     * @param errors 错误收集
     */
    private void validateConditionBranches(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            List<String> errors,
            boolean deferEdgeAlignment
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
            // 已被分支 target 认领的下游节点 id
            Set<String> claimedTargets = new HashSet<>();
            for (Object branchObj : branches) {
                if (!(branchObj instanceof Map<?, ?> branch)) {
                    continue;
                }
                Object branchId = branch.get("id");
                Object target = branch.get("target");
                if (target == null || String.valueOf(target).isBlank()) {
                    continue;
                }
                String targetStr = String.valueOf(target);
                claimedTargets.add(targetStr);
                if (!deferEdgeAlignment && !hasOutgoingEdge(edges, node.getId(), targetStr)) {
                    errors.add("条件节点 " + node.getId() + " 分支 " + branchId + " 的 target 无对应出边：" + target);
                }
            }
            if (edges == null || deferEdgeAlignment) {
                continue;
            }
            for (GraphEdge edge : edges) {
                if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
                    continue;
                }
                if (!node.getId().equals(edge.getSource())) {
                    continue;
                }
                if (!claimedTargets.contains(edge.getTarget())) {
                    errors.add("条件节点 " + node.getId() + " 出边无对应 branches.target：" + edge.getTarget());
                }
            }
        }
    }

    /**
     * 对未解析成模型对象的原始 JSON 图，校验条件分支与出边是否对齐。
     * <p>
     * 分支无 {@code target}：表示命中后本流正常结束，合法。<br>
     * 分支有 {@code target}：须存在同源同目标的出边，否则记入 errors。<br>
     * 条件节点有出边：须有分支的 {@code target} 认领该目标，否则记入 errors。
     *
     * @param nodesArr 节点 JSON 数组
     * @param edgesArr 边 JSON 数组
     * @param errors   错误收集
     */
    private void validateRawConditionBranches(
            JSONArray nodesArr,
            JSONArray edgesArr,
            List<String> errors) {
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
            Set<String> claimedTargets = new HashSet<>();
            for (int j = 0; j < branches.size(); j++) {
                JSONObject branch = branches.getJSONObject(j);
                if (branch == null) {
                    continue;
                }
                String branchId = branch.getString("id");
                String target = branch.getString("target");
                if (target == null || target.isBlank()) {
                    continue;
                }
                claimedTargets.add(target);
                if (!hasRawOutgoingEdge(edgesArr, nodeId, target)) {
                    errors.add("条件节点 " + nodeId + " 分支 " + branchId + " 的 target 无对应出边：" + target);
                }
            }
            if (edgesArr == null) {
                continue;
            }
            for (Object edgeItem : edgesArr) {
                if (!(edgeItem instanceof JSONObject edge)) {
                    continue;
                }
                if (!nodeId.equals(edge.getString("source"))) {
                    continue;
                }
                String edgeTarget = edge.getString("target");
                if (edgeTarget == null || edgeTarget.isBlank()) {
                    continue;
                }
                if (!claimedTargets.contains(edgeTarget)) {
                    errors.add("条件节点 " + nodeId + " 出边无对应 branches.target：" + edgeTarget);
                }
            }
        }
    }

    /**
     * 运行场景须写在 scenario-entry.data.scenarios。
     * 纯 HTTP/子流图（无场景入口且不依赖人跑场景）可无场景列表。
     */
    /**
     * 每个场景入口各自须挂非空 scenarios（多入口不能互相顶替）；半成品阶段（defer）暂不校验。
     */
    private void appendMetaRunError(GraphJson graph, List<String> errors, boolean deferTopology) {
        List<GraphNode> nodes = graph.getNodes();
        if (nodes == null || nodes.isEmpty() || deferTopology) {
            return;
        }
        for (GraphNode n : nodes) {
            if (n == null || !FlowNodeType.SCENARIO_ENTRY.matches(n.getType())) {
                continue;
            }
            if (com.qualitest.flow.context.EntryScenarioSupport.scenariosOf(n).isEmpty()) {
                errors.add("场景入口「" + formatTypedStartNodeName(nodes, n.getId())
                        + "」须配置非空 data.scenarios（含 inSeed）");
            }
        }
    }

    /**
     * 原始 JSON 路径下的场景齐全性检查：能解析成 GraphJson 则复用对象路径规则，否则记缺场景错误。
     */
    private void appendMetaRunErrorFromRaw(JSONObject graph, List<String> errors) {
        GraphJson parsed;
        try {
            parsed = GraphJson.parse(graph.toJSONString());
        } catch (Exception e) {
            parsed = null;
        }
        if (parsed != null) {
            appendMetaRunError(parsed, errors, false);
            return;
        }
        errors.add("缺少运行场景：请在 scenario-entry 配置 scenarios");
    }

    /**
     * 开始节点校验：空白图跳过；有节点时必须至少有一个入口，且仅入口可作无入边起点。
     */
    private void appendStartNodeError(List<GraphNode> nodes, List<GraphEdge> edges, List<String> errors) {
        if (nodes.isEmpty()) {
            return;
        }
        List<String> ids = findStartNodeIds(collectNodeIds(nodes), collectTypedEdgeTargets(edges));
        boolean hasEntry = nodes.stream().anyMatch(n -> n != null && FlowNodeType.isEntry(n.getType()));
        if (!hasEntry) {
            errors.add("须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑");
            return;
        }
        appendEntryStartNodeMessage(nodes, ids, errors);
    }

    /**
     * 有入口时：
     * 所有「无入边」节点都必须是入口类型；入口本身也必须无入边；
     * 允许多个入口并存（开跑时用 entryNodeId 选定）。
     */
    private void appendEntryStartNodeMessage(List<GraphNode> nodes, List<String> startIds, List<String> errors) {
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null) {
                byId.put(n.getId(), n);
            }
        }
        if (startIds.isEmpty()) {
            errors.add("未找到开始节点（入口须无入边）");
            return;
        }
        for (String id : startIds) {
            GraphNode n = byId.get(id);
            if (n == null || !FlowNodeType.isEntry(n.getType())) {
                errors.add("存在非入口的无入边节点：" + formatTypedStartNodeName(nodes, id)
                        + "（有入口时仅入口可作开始）");
            }
        }
        for (GraphNode n : nodes) {
            if (n != null && FlowNodeType.isEntry(n.getType()) && n.getId() != null
                    && !startIds.contains(n.getId())) {
                errors.add("入口节点须无入边：" + formatTypedStartNodeName(nodes, n.getId()));
            }
        }
    }

    /**
     * schemaVersion：缺失不拦（保存会写当前版本）；显式旧版本记错误（不做热升，挡开跑）。
     */
    private void appendSchemaVersionFinding(GraphJson graph, List<String> errors) {
        Integer version = graph.getMeta() != null ? graph.getMeta().getSchemaVersion() : null;
        if (version == null) {
            return;
        }
        if (version != GraphSchemaVersions.CURRENT) {
            errors.add("meta.schemaVersion 须为 " + GraphSchemaVersions.CURRENT
                    + "（当前为 " + version + "，不做旧图热升）");
        }
    }

    /**
     * 入口与返回节点相关规则：
     * <ul>
     *   <li>{@code http-entry} / {@code subflow-entry} 的 {@code data.fallbackOut} 必须是对象</li>
     *   <li>仅含场景入口的图不得出现 {@code http-return} / {@code subflow-return}</li>
     *   <li>从各入口沿出边可达的返回节点，type 必须是该入口对应的返回 type</li>
     *   <li>同一顺序路径上返回节点超过 1 个则报错；条件节点各出边分别累计</li>
     * </ul>
     */
    private void appendReturnAndEntryRules(
            List<GraphNode> nodes, List<GraphEdge> edges, List<String> errors) {
        boolean hasScenarioEntry = false;
        boolean hasHttpEntry = false;
        boolean hasSubflowEntry = false;
        for (GraphNode n : nodes) {
            if (n == null) {
                continue;
            }
            if (FlowNodeType.SCENARIO_ENTRY.matches(n.getType())) {
                hasScenarioEntry = true;
                Map<String, Object> data = n.getData();
                if (data != null && data.get("fallbackOut") != null) {
                    errors.add("场景入口「" + formatTypedStartNodeName(nodes, n.getId())
                            + "」不得配置 fallbackOut");
                }
                appendEntryDeclarationShapeErrors(n, nodes, "inSeedKeys", errors);
            }
            if (FlowNodeType.HTTP_ENTRY.matches(n.getType()) || FlowNodeType.SUBFLOW_ENTRY.matches(n.getType())) {
                if (FlowNodeType.HTTP_ENTRY.matches(n.getType())) {
                    hasHttpEntry = true;
                    appendHttpEntryFieldErrors(n, nodes, errors);
                    appendHttpInputShapeErrors(n, nodes, errors);
                } else {
                    hasSubflowEntry = true;
                    Map<String, Object> entryData = n.getData();
                    if (entryData != null && entryData.get("inputShape") != null) {
                        errors.add("子流入口「" + formatTypedStartNodeName(nodes, n.getId())
                                + "」须改用 inputDecl（inputShape 仅 HTTP 入口支持）");
                    }
                    appendEntryDeclarationShapeErrors(n, nodes, "inputDecl", errors);
                }
                Map<String, Object> data = n.getData();
                Object fb = data != null ? data.get("fallbackOut") : null;
                if (!(fb instanceof Map<?, ?>)) {
                    errors.add("入口节点「" + formatTypedStartNodeName(nodes, n.getId())
                            + "」须配置 fallbackOut");
                } else if (FlowNodeType.HTTP_ENTRY.matches(n.getType())) {
                    appendHttpFallbackOutShapeErrors(
                            formatTypedStartNodeName(nodes, n.getId()), (Map<?, ?>) fb, errors);
                }
            }
        }

        boolean scenarioOnly = hasScenarioEntry && !hasHttpEntry && !hasSubflowEntry;
        for (GraphNode n : nodes) {
            if (n == null || !FlowNodeType.isReturn(n.getType())) {
                continue;
            }
            if (scenarioOnly) {
                errors.add("场景图不支持返回节点（含「" + formatTypedStartNodeName(nodes, n.getId()) + "」）");
            }
        }

        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null) {
                byId.put(n.getId(), n);
            }
        }
        Map<String, List<GraphEdge>> outs = new HashMap<>();
        if (edges != null) {
            for (GraphEdge e : edges) {
                if (e == null || e.getSource() == null) {
                    continue;
                }
                outs.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e);
            }
        }
        for (GraphNode entry : nodes) {
            if (entry == null || entry.getId() == null || !FlowNodeType.isEntry(entry.getType())) {
                continue;
            }
            String expectedReturn = EntryKind.expectedReturnTypeForEntry(entry.getType());
            if (FlowNodeType.HTTP_ENTRY.matches(entry.getType())) {
                detectInputAfterReturn(entry.getId(), 0, new HashSet<>(), byId, outs, errors);
            }
            if (expectedReturn != null) {
                detectReturnTypeMismatch(entry.getId(), expectedReturn, new HashSet<>(), byId, outs, errors);
            } else if (FlowNodeType.SCENARIO_ENTRY.matches(entry.getType()) && !scenarioOnly) {
                detectScenarioEntryReachableReturn(
                        entry.getId(), new HashSet<>(), nodes, byId, outs, errors);
            }
        }
        Set<String> startIds = new HashSet<>(findStartNodeIds(collectNodeIds(nodes), collectTypedEdgeTargets(edges)));
        for (String start : startIds) {
            detectSequentialDoubleReturn(start, 0, new HashSet<>(), byId, outs, errors);
        }
        GraphJson graph = new GraphJson();
        graph.setNodes(nodes);
        graph.setEdges(edges != null ? edges : List.of());
        GraphWalker walker = new GraphWalker(graph);
        appendFanoutReturnRules(nodes, walker, errors);
    }

    /**
     * 入口声明数组（scenario 的 inSeedKeys / subflow 的 inputDecl）：须为扁平数组，name 必填，
     * 不允许 children 嵌套（与 HTTP inputShape 的三节树分治）。
     */
    private void appendEntryDeclarationShapeErrors(
            GraphNode node, List<GraphNode> nodes, String key, List<String> errors) {
        if (node == null || node.getData() == null) {
            return;
        }
        Object raw = node.getData().get(key);
        if (raw == null) {
            return;
        }
        String label = formatTypedStartNodeName(nodes, node.getId());
        if (!(raw instanceof List<?> list)) {
            errors.add("入口「" + label + "」的 " + key + " 须为数组");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> row)) {
                errors.add("入口「" + label + "」" + key + "[" + i + "] 须为对象");
                continue;
            }
            Object nameObj = row.get("name");
            if (nameObj == null || String.valueOf(nameObj).trim().isEmpty()) {
                errors.add("入口「" + label + "」" + key + "[" + i + "].name 不能为空");
            }
            if (row.containsKey("children")) {
                errors.add("入口「" + label + "」" + key + "[" + i + "] 须为扁平声明，不允许 children");
            }
        }
    }

    /**
     * HTTP 入口 inputShape：仅 body/query/headers 三节；body 可嵌套 children，query/headers 扁平。
     */
    private void appendHttpInputShapeErrors(GraphNode node, List<GraphNode> nodes, List<String> errors) {
        if (node == null || node.getData() == null) {
            return;
        }
        Object raw = node.getData().get("inputShape");
        if (raw == null) {
            return;
        }
        String label = formatTypedStartNodeName(nodes, node.getId());
        if (!(raw instanceof Map<?, ?> shape)) {
            errors.add("HTTP 入口「" + label + "」的 inputShape 须为对象");
            return;
        }
        for (Object key : shape.keySet()) {
            String section = key == null ? "" : String.valueOf(key);
            if (!"body".equals(section) && !"query".equals(section) && !"headers".equals(section)) {
                errors.add("HTTP 入口「" + label + "」inputShape 只支持 body / query / headers，出现：" + section);
            }
        }
        appendShapeSection(label, "body", shape.get("body"), true, errors);
        appendShapeSection(label, "query", shape.get("query"), false, errors);
        appendShapeSection(label, "headers", shape.get("headers"), false, errors);
    }

    /** 校验 inputShape 的一节（须为数组） */
    private void appendShapeSection(
            String label, String section, Object raw, boolean allowChildren, List<String> errors) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> list)) {
            errors.add("HTTP 入口「" + label + "」inputShape." + section + " 须为数组");
            return;
        }
        appendShapeFields(label, "inputShape." + section, list, allowChildren, errors);
    }

    /** 递归校验字段表：name 必填；仅 body 允许 children */
    private void appendShapeFields(
            String label, String path, List<?> fields, boolean allowChildren, List<String> errors) {
        for (int i = 0; i < fields.size(); i++) {
            Object item = fields.get(i);
            if (!(item instanceof Map<?, ?> row)) {
                errors.add("HTTP 入口「" + label + "」" + path + "[" + i + "] 须为对象");
                continue;
            }
            Object nameObj = row.get("name");
            String name = nameObj == null ? "" : String.valueOf(nameObj).trim();
            if (name.isEmpty()) {
                errors.add("HTTP 入口「" + label + "」" + path + "[" + i + "].name 不能为空");
            }
            Object children = row.get("children");
            if (children == null) {
                continue;
            }
            if (!allowChildren) {
                errors.add("HTTP 入口「" + label + "」" + path + "[" + i + "] 须为扁平参数表，不允许 children");
            } else if (children instanceof List<?> nested) {
                appendShapeFields(label, path + "[" + name + "].children", nested, true, errors);
            } else {
                errors.add("HTTP 入口「" + label + "」" + path + "[" + i + "].children 须为数组");
            }
        }
    }

    /**
     * HTTP 入口可达路径上：返回节点之后再出现 Input → 响应已交付，入站无法暂停续跑。
     * 按路径统计（到达本节点前已遇到的返回节点数），各分支独立；遇环终止。
     */
    private void detectInputAfterReturn(
            String nodeId,
            int returnsBefore,
            Set<String> path,
            Map<String, GraphNode> byId,
            Map<String, List<GraphEdge>> outs,
            List<String> errors) {
        if (nodeId == null || path.contains(nodeId)) {
            return;
        }
        GraphNode node = byId.get(nodeId);
        if (node == null) {
            return;
        }
        if (FlowNodeType.INPUT.matches(node.getType()) && returnsBefore > 0) {
            errors.add("HTTP 入口可达路径上，返回节点之后又出现 Input「"
                    + nodeDisplayName(node) + "」；响应已交付后无法暂停续跑");
            return;
        }
        int returnsHere = returnsBefore + (FlowNodeType.isReturn(node.getType()) ? 1 : 0);
        Set<String> nextPath = new HashSet<>(path);
        nextPath.add(nodeId);
        for (GraphEdge e : outs.getOrDefault(nodeId, List.of())) {
            if (e == null || e.getTarget() == null) {
                continue;
            }
            detectInputAfterReturn(e.getTarget(), returnsHere, nextPath, byId, outs, errors);
        }
    }

    /**
     * 混图中场景入口可达子图不得含返回节点。
     */
    private void detectScenarioEntryReachableReturn(
            String nodeId,
            Set<String> path,
            List<GraphNode> nodes,
            Map<String, GraphNode> byId,
            Map<String, List<GraphEdge>> outs,
            List<String> errors) {
        if (nodeId == null || path.contains(nodeId)) {
            return;
        }
        GraphNode node = byId.get(nodeId);
        if (node == null) {
            return;
        }
        if (FlowNodeType.isReturn(node.getType())) {
            errors.add("场景入口可达子图不支持返回节点（含「"
                    + formatTypedStartNodeName(nodes, node.getId())
                    + "」）；返回节点须放在 run.entryKind 守卫的 IF 臂里（如 run.entryKind eq subflow）");
            return;
        }
        Set<String> nextPath = new HashSet<>(path);
        nextPath.add(nodeId);
        // 规范守卫：run.entryKind eq <非 scenario> / ne scenario 的臂，场景 Run 静态不会走到
        Set<String> guardedTargets = FlowNodeType.CONDITION.matches(node.getType())
                ? scenarioGuardedTargets(node)
                : Set.of();
        for (GraphEdge e : outs.getOrDefault(nodeId, List.of())) {
            if (e == null || e.getTarget() == null) {
                continue;
            }
            if (guardedTargets.contains(e.getTarget())) {
                continue;
            }
            detectScenarioEntryReachableReturn(e.getTarget(), nextPath, nodes, byId, outs, errors);
        }
    }

    /**
     * 条件节点的「场景不可达」目标集合。
     * <p>
     * 只认可一种规范守卫：分支条件包含 {@code run.entryKind eq <非 scenario>}
     * 或 {@code run.entryKind ne scenario}（大小写与别名按 {@link CompareRuleEvaluator} 归一）。
     * 同一目标若还有未被守卫的分支指过来，则不算守卫（保守口径）。
     */
    private static Set<String> scenarioGuardedTargets(GraphNode condition) {
        if (condition == null || condition.getData() == null) {
            return Set.of();
        }
        Object branchesObj = condition.getData().get("branches");
        if (!(branchesObj instanceof List<?> branches)) {
            return Set.of();
        }
        Map<String, Boolean> allGuardedByTarget = new HashMap<>();
        for (Object obj : branches) {
            if (!(obj instanceof Map<?, ?> branch)) {
                continue;
            }
            Object targetObj = branch.get("target");
            if (targetObj == null || String.valueOf(targetObj).isBlank()) {
                continue;
            }
            String target = String.valueOf(targetObj).trim();
            boolean guarded = branchExcludesScenario(branch);
            allGuardedByTarget.merge(target, guarded, (a, b) -> a && b);
        }
        Set<String> guarded = new HashSet<>();
        allGuardedByTarget.forEach((target, allGuarded) -> {
            if (Boolean.TRUE.equals(allGuarded)) {
                guarded.add(target);
            }
        });
        return guarded;
    }

    /** 分支条件是否把 entryKind 锁死为非 scenario（规范守卫） */
    private static boolean branchExcludesScenario(Map<?, ?> branch) {
        Object conditionsObj = branch.get("conditions");
        if (!(conditionsObj instanceof List<?> conditions)) {
            return false;
        }
        for (Object obj : conditions) {
            if (!(obj instanceof Map<?, ?> condition)) {
                continue;
            }
            Object leftObj = CompareRuleEvaluator.stripMustache(condition.get("left"));
            String left = leftObj == null ? "" : String.valueOf(leftObj).trim();
            if (!"run.entryKind".equals(left)) {
                continue;
            }
            String op = CompareRuleEvaluator.normalizeOperator(
                    condition.get("operator") == null ? null : String.valueOf(condition.get("operator")));
            Object rightObj = condition.get("right");
            String right = rightObj == null ? "" : String.valueOf(rightObj).trim();
            if ("eq".equals(op) && !right.isEmpty() && !"scenario".equalsIgnoreCase(right)) {
                return true;
            }
            if ("ne".equals(op) && "scenario".equalsIgnoreCase(right)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 隐式扇出校验：非 condition 多出边时，至多一臂的可达子图可含返回节点（避免二次交付）。
     */
    private void appendFanoutReturnRules(
            List<GraphNode> nodes, GraphWalker walker, List<String> errors) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (GraphNode n : nodes) {
            if (n == null || n.getId() == null || !walker.isFanoutNode(n)) {
                continue;
            }
            String display = formatTypedStartNodeName(nodes, n.getId());
            int armsWithReturn = 0;
            for (GraphEdge edge : walker.listOutgoingEdges(n.getId())) {
                if (edge == null || edge.getTarget() == null || edge.getTarget().isBlank()) {
                    continue;
                }
                if (FanoutSupport.armCanReachReturn(walker, edge.getTarget())) {
                    armsWithReturn++;
                }
            }
            if (armsWithReturn > 1) {
                errors.add("多出边扇出后至多一臂可含返回节点（节点「" + display + "」）");
            }
        }
    }

    /**
     * 从入口沿出边检查：遇到的返回节点 type 必须等于 expectedReturnType。
     */
    private void detectReturnTypeMismatch(
            String nodeId,
            String expectedReturnType,
            Set<String> path,
            Map<String, GraphNode> byId,
            Map<String, List<GraphEdge>> outs,
            List<String> errors) {
        if (nodeId == null || path.contains(nodeId)) {
            return;
        }
        GraphNode node = byId.get(nodeId);
        if (node == null) {
            return;
        }
        if (FlowNodeType.isReturn(node.getType()) && !expectedReturnType.equals(node.getType())) {
            errors.add("入口可达的返回节点「" + nodeId + "」类型须为 " + expectedReturnType
                    + "（当前为 " + node.getType() + "）");
        }
        Set<String> nextPath = new HashSet<>(path);
        nextPath.add(nodeId);
        List<GraphEdge> edgeList = outs.getOrDefault(nodeId, List.of());
        for (GraphEdge e : edgeList) {
            detectReturnTypeMismatch(e.getTarget(), expectedReturnType, nextPath, byId, outs, errors);
        }
    }

    /**
     * 从 nodeId 沿出边检测「同一路径两个返回节点」。
     * path 用于环检测；returnsSoFar 为到达本节点前已遇到的返回节点数。
     */
    private void detectSequentialDoubleReturn(
            String nodeId,
            int returnsSoFar,
            Set<String> path,
            Map<String, GraphNode> byId,
            Map<String, List<GraphEdge>> outs,
            List<String> errors) {
        if (nodeId == null || path.contains(nodeId)) {
            return;
        }
        GraphNode node = byId.get(nodeId);
        if (node == null) {
            return;
        }
        int returns = returnsSoFar;
        if (FlowNodeType.isReturn(node.getType())) {
            returns++;
            if (returns > 1) {
                errors.add("同一路径上存在多个返回节点（含「" + nodeId + "」）");
                return;
            }
        }
        Set<String> nextPath = new HashSet<>(path);
        nextPath.add(nodeId);
        // condition 与隐式扇出均对各出边分别累计返回次数（互不影响）
        List<GraphEdge> edgeList = outs.getOrDefault(nodeId, List.of());
        for (GraphEdge e : edgeList) {
            detectSequentialDoubleReturn(e.getTarget(), returns, nextPath, byId, outs, errors);
        }
    }

    private void appendStartNodeErrorFromRaw(JSONArray nodesArr, JSONArray edgesArr, List<String> errors) {
        if (nodesArr.isEmpty()) {
            return;
        }
        boolean hasEntry = false;
        for (int i = 0; i < nodesArr.size(); i++) {
            Object raw = nodesArr.get(i);
            if (!(raw instanceof JSONObject node)) {
                continue;
            }
            String type = node.getString("type");
            if (FlowNodeType.isEntry(type)) {
                hasEntry = true;
                break;
            }
        }
        if (!hasEntry) {
            errors.add("须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑");
            return;
        }
        List<String> ids = findStartNodeIds(collectRawNodeIds(nodesArr), collectRawEdgeTargets(edgesArr));
        // 有入口时：无入边须均为入口；入口须无入边
        for (String id : ids) {
            JSONObject n = findRawNodeById(nodesArr, id);
            String type = n != null ? n.getString("type") : null;
            if (!FlowNodeType.isEntry(type)) {
                errors.add("存在非入口的无入边节点：" + formatRawStartNodeName(nodesArr, id)
                        + "（有入口时仅入口可作开始）");
            }
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            Object raw = nodesArr.get(i);
            if (!(raw instanceof JSONObject node)) {
                continue;
            }
            String type = node.getString("type");
            String id = node.getString("id");
            if (FlowNodeType.isEntry(type) && id != null && !ids.contains(id)) {
                errors.add("入口节点须无入边：" + formatRawStartNodeName(nodesArr, id));
            }
        }
        if (ids.isEmpty()) {
            errors.add("未找到开始节点（入口须无入边）");
        }
    }

    private JSONObject findRawNodeById(JSONArray nodesArr, String id) {
        if (id == null || nodesArr == null) {
            return null;
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            Object raw = nodesArr.get(i);
            if (raw instanceof JSONObject node && id.equals(node.getString("id"))) {
                return node;
            }
        }
        return null;
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
                if (node.getName() != null && !node.getName().isBlank()) {
                    return node.getName().trim();
                }
                return FlowNodeType.labelOf(node.getType());
            }
        }
        return id;
    }

    private String formatRawStartNodeName(JSONArray nodesArr, String id) {
        for (Object item : nodesArr) {
            if (item instanceof JSONObject node && id.equals(node.getString("id"))) {
                String name = node.getString("name");
                if (name != null && !name.isBlank()) {
                    return name.trim();
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

    /** 是否存在 source→target 的出边。 */
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

    /** 原始 JSON 边数组中是否存在 source→target 的出边。 */
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
