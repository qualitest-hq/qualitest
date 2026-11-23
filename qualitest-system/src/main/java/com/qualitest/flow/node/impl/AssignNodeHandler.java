package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assign 节点执行器（type=assign）。
 * <p>
 * 逐条处理 {@code data.assignments[]}：
 * <ul>
 *   <li>{@code scope} 缺省或非 out → 写入运行时 {@code flow}</li>
 *   <li>{@code scope=out} → 写入运行时 {@code out}（供后续 http-return / subflow-return 定稿交付）</li>
 *   <li>op=set：解析 value 占位符后覆盖；value 可为对象/数组字面量（常见于写 out.body）</li>
 *   <li>op=add/sub/mul/div：在现有值或 ifMissing 上按 step 运算，仅对 flow 生效；写 out 时一律按 set</li>
 * </ul>
 * 每条赋值的 before/after 写入 {@code StepResult.assigns} 供报告展示。
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
     * 执行单条赋值；变量名为空时跳过。
     * scope 归一为 flow 或 out；非法 op 当作 set。
     */
    static Map<String, Object> applyAssignment(JSONObject row, FlowRunContext ctx) {
        String name = row.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        name = name.trim();
        String scope = normalizeScope(row.getString("scope"));
        String op = normalizeOp(row.getString("op"));

        Map<String, Object> target = "out".equals(scope) ? ensureOut(ctx) : ctx.getFlow();
        Object before = target.get(name);
        Object after = before;

        if (isStepOp(op) && !"out".equals(scope)) {
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
        } else {
            Object raw = row.get("value");
            if (raw instanceof String s) {
                after = STRICT.resolve(s, ctx);
                after = coerceLiteral(after);
            } else if (raw != null) {
                after = raw;
            } else {
                after = null;
            }
        }

        target.put(name, after);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scope", scope);
        record.put("name", name);
        record.put("op", op);
        record.put("before", before);
        record.put("after", after);
        return record;
    }

    /** 作用域：仅识别 out；其余（含空）一律 flow */
    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "flow";
        }
        String s = scope.trim().toLowerCase();
        return "out".equals(s) ? "out" : "flow";
    }

    /** 确保 ctx.out 非 null，可直接 put */
    private static Map<String, Object> ensureOut(FlowRunContext ctx) {
        Map<String, Object> out = ctx.getOut();
        if (out == null) {
            out = new LinkedHashMap<>();
            ctx.setOut(out);
        }
        return out;
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

    /** 将 set 运算解析结果尝试转为布尔或数值字面量 */
    private static Object coerceLiteral(Object val) {
        if (val == null) {
            return null;
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

    /** 从节点 data 读取 assignments 数组（兼容 JSONArray 与 List） */
    private static JSONArray toAssignmentsArray(Object raw) {
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        if (raw instanceof List<?> list) {
            JSONArray arr = new JSONArray();
            for (Object item : list) {
                if (item instanceof JSONObject jo) {
                    arr.add(jo);
                } else if (item instanceof Map<?, ?> map) {
                    arr.add(new JSONObject(map));
                }
            }
            return arr;
        }
        return null;
    }
}
