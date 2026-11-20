package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assign 节点执行器：按 {@code data.assignments[]} 改写运行时变量。
 * <p>
 * 每条赋值含：
 * <ul>
 *   <li>{@code scope}：{@code flow}（默认，中间量）或 {@code out}（出参）；{@code in} 只读，写入会失败</li>
 *   <li>{@code name}：目标键，支持点路径（如 {@code body}、{@code headers.X-Demo}）</li>
 *   <li>{@code op}：{@code set} 写入任意值；{@code add}/{@code sub}/{@code mul}/{@code div} 只对 {@code flow} 标量做四则</li>
 *   <li>{@code value}：{@code set} 时可为字符串或对象/数组；对象叶子里的字符串会解析 {@code {{in.x}}} / {@code {{flow.x}}} 等占位</li>
 * </ul>
 * 每条 before/after 记入步骤 {@code assigns}，供运行报告展示。
 */
@Component
public class AssignNodeHandler extends AbstractStubNodeHandler {

    /** 正式 Run 使用严格占位符解析，未定义占位符抛 TF_PLACEHOLDER_UNDEFINED */
    private static final PlaceholderResolver STRICT = PlaceholderResolver.strict();

    public AssignNodeHandler() {
        super(FlowNodeType.ASSIGN);
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String nodeName = resolveNodeName(node);
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();

        JSONArray assignments = toAssignmentsArray(data.get("assignments"));
        List<Map<String, Object>> assignResults = new ArrayList<>();

        if (assignments != null) {
            for (int i = 0; i < assignments.size(); i++) {
                JSONObject row = assignments.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                Map<String, Object> result = applyAssignment(row, ctx);
                if (result != null) {
                    assignResults.add(result);
                }
            }
        }

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.ASSIGN.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.PASSED.getCode())
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .flowAfter(copyFlow(ctx))
                .assigns(assignResults)
                .build();
    }

    /**
     * 执行单条赋值。
     * 变量名为空则跳过；写 {@code out} 时禁止四则运算。
     */
    static Map<String, Object> applyAssignment(JSONObject row, FlowRunContext ctx) {
        String name = row.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        name = name.trim();
        String op = normalizeOp(row.getString("op"));
        String scope = normalizeScope(row.getString("scope"));

        if ("out".equals(scope) && isStepOp(op)) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "出参作用域 out 仅支持 set，不支持四则运算"
            );
        }

        Map<String, Object> target = "out".equals(scope) ? ensureOut(ctx) : ensureFlow(ctx);
        Object before = getByPath(target, name);
        Object after = before;

        if (isStepOp(op)) {
            double base = before != null && !"".equals(String.valueOf(before))
                    ? toDouble(before)
                    : row.getDoubleValue("ifMissing");
            double step = row.containsKey("step") ? row.getDoubleValue("step") : 1;
            double computed = switch (op) {
                case "add" -> base + step;
                case "sub" -> base - step;
                case "mul" -> base * step;
                case "div" -> step != 0 ? base / step : base;
                default -> base;
            };
            after = toNumber(computed);
            putByPath(target, name, after);
        } else {
            Object rawValue = row.get("value");
            after = resolveStructuredValue(rawValue, ctx);
            putByPath(target, name, after);
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scope", scope);
        record.put("name", name);
        record.put("op", op);
        record.put("before", before);
        record.put("after", after);
        return record;
    }

    /**
     * 解析 {@code set} 的 value。
     * 字符串整段做占位替换并尝试转成布尔/数字；对象与数组递归处理叶子字符串。
     */
    static Object resolveStructuredValue(Object raw, FlowRunContext ctx) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            Object resolved = STRICT.resolve(s, ctx);
            return coerceLiteral(resolved);
        }
        if (raw instanceof JSONObject obj) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : obj.keySet()) {
                out.put(key, resolveStructuredValue(obj.get(key), ctx));
            }
            return out;
        }
        if (raw instanceof JSONArray arr) {
            List<Object> out = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                out.add(resolveStructuredValue(arr.get(i), ctx));
            }
            return out;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), resolveStructuredValue(e.getValue(), ctx));
            }
            return out;
        }
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolveStructuredValue(item, ctx));
            }
            return out;
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        // 其它类型按字符串解析占位
        Object resolved = STRICT.resolve(String.valueOf(raw), ctx);
        return coerceLiteral(resolved);
    }

    /**
     * 规范化作用域：空或未知 → {@code flow}；{@code out} 保留；{@code in} 抛错（只读）。
     */
    static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "flow";
        }
        String s = scope.trim().toLowerCase();
        if ("out".equals(s)) {
            return "out";
        }
        if ("flow".equals(s)) {
            return "flow";
        }
        if ("in".equals(s)) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "入参作用域 in 只读，Assign 不可写入"
            );
        }
        return "flow";
    }

    private static Map<String, Object> ensureFlow(FlowRunContext ctx) {
        if (ctx.getFlow() == null) {
            ctx.setFlow(new LinkedHashMap<>());
        }
        return ctx.getFlow();
    }

    private static Map<String, Object> ensureOut(FlowRunContext ctx) {
        if (ctx.getOut() == null) {
            ctx.setOut(new LinkedHashMap<>());
        }
        return ctx.getOut();
    }

    /**
     * 按点路径从作用域取值。
     * 无点则按整键取；有点时先试整键，再按段下钻 Map。
     */
    @SuppressWarnings("unchecked")
    static Object getByPath(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            return root.get(path);
        }
        Object direct = root.get(path);
        if (direct != null) {
            return direct;
        }
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(part);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    /**
     * 按点路径写入作用域。
     * 无点则写顶层键；有点则沿路径创建缺失的中间 Map，最后一段写入值。
     * 典型用法：一次写 {@code body} 整对象，或写 {@code status} / {@code headers.某头}。
     */
    @SuppressWarnings("unchecked")
    static void putByPath(Map<String, Object> root, String path, Object value) {
        if (root == null || path == null || path.isBlank()) {
            return;
        }
        if (!path.contains(".")) {
            root.put(path, value);
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                cur.put(parts[i], created);
                cur = created;
            } else {
                cur = (Map<String, Object>) next;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }

    /** 规范化赋值运算符；空值或非法值默认 set */
    private static String normalizeOp(String op) {
        if (op == null || op.isBlank()) {
            return "set";
        }
        return switch (op) {
            case "set", "add", "sub", "mul", "div" -> op;
            default -> "set";
        };
    }

    /** 判断是否为步长类运算符（add/sub/mul/div） */
    private static boolean isStepOp(String op) {
        return "add".equals(op) || "sub".equals(op) || "mul".equals(op) || "div".equals(op);
    }

    /** 整数值存为 Long，带小数的保留 Double */
    private static Number toNumber(double val) {
        if (val == Math.rint(val) && Math.abs(val) <= Long.MAX_VALUE) {
            return (long) val;
        }
        return val;
    }

    /** 将 flow 变量值转为 double，无法解析时返回 0 */
    private static double toDouble(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 将 set 运算解析结果尝试转为布尔或数值字面量（仅纯字符串叶子） */
    private static Object coerceLiteral(Object val) {
        if (val == null) {
            return null;
        }
        if (!(val instanceof String)) {
            return val;
        }
        String s = String.valueOf(val).trim();
        if ("true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s)) {
            return false;
        }
        try {
            if (!s.contains(".")) {
                return Long.parseLong(s);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return val;
        }
    }

    /** 从节点 data 读取 assignments 数组 */
    private static JSONArray toAssignmentsArray(Object raw) {
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        return null;
    }
}
