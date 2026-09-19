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
 * Assign 节点执行器。
 * <p>
 * 逐条处理 {@code data.assignments[]}，仅写入 {@code flow} 作用域：
 * <ul>
 *   <li>set：解析 value 占位符后覆盖变量</li>
 *   <li>add/sub/mul/div：在现有值或 ifMissing 基础上按 step 步长运算</li>
 * </ul>
 * 每条赋值记录 before/after 写入 {@code StepResult.assigns} 供报告展示。
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
     */
    static Map<String, Object> applyAssignment(JSONObject row, FlowRunContext ctx) {
        String name = row.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        name = name.trim();
        String op = normalizeOp(row.getString("op"));

        Map<String, Object> flow = ctx.getFlow();
        Object before = flow.get(name);
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
        } else {
            String rawValue = row.getString("value");
            after = STRICT.resolve(rawValue != null ? rawValue : "", ctx);
            after = coerceLiteral(after);
        }

        flow.put(name, after);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scope", "flow");
        record.put("name", name);
        record.put("op", op);
        record.put("before", before);
        record.put("after", after);
        return record;
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

    /** 从节点 data 读取 assignments 数组 */
    private static JSONArray toAssignmentsArray(Object raw) {
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        return null;
    }
}
