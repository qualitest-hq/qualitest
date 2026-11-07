package com.qualitest.flow.validate;

import com.qualitest.flow.run.GraphNodeAlwaysRunSupport;
import com.qualitest.flow.snapshot.GraphNodeSnapshotSupport;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 节点 data 上布尔开关字段的设计期规则。
 * <p>
 * {@code alwaysRun}：业务失败后是否仍执行本节点；仅允许挂在 http、script、assign、subflow。
 * {@code snapshotBefore}：handler 执行前是否先打被测数据快照；仅允许挂在 http、script、assign。
 * 字段缺省合法；一旦写出则值必须是 JSON 布尔（true/false），字符串或数字视为非法。
 * 写图规范化阶段可将常见脏值收成真正布尔；无法识别的值留给校验报错。
 * 跑流时仅把布尔 true 当作开启。
 */
public final class GraphNodeBooleanFieldRules {

    /** 允许写出 alwaysRun 的节点 type；集合遍历顺序用于错误文案 */
    public static final Set<String> ALWAYS_RUN_ALLOWED_TYPES =
            Set.of(
                    FlowNodeType.HTTP.getCode(),
                    FlowNodeType.SCRIPT.getCode(),
                    FlowNodeType.ASSIGN.getCode(),
                    FlowNodeType.SUBFLOW.getCode());

    /** 允许写出 snapshotBefore 的节点 type；集合遍历顺序用于错误文案 */
    public static final Set<String> SNAPSHOT_BEFORE_ALLOWED_TYPES =
            Set.of(
                    FlowNodeType.HTTP.getCode(),
                    FlowNodeType.SCRIPT.getCode(),
                    FlowNodeType.ASSIGN.getCode());

    /** alwaysRun 允许类型的斜杠拼接文案，写入校验错误 */
    private static final String ALWAYS_RUN_ALLOWED_LABEL = joinTypes(ALWAYS_RUN_ALLOWED_TYPES);

    /** snapshotBefore 允许类型的斜杠拼接文案，写入校验错误 */
    private static final String SNAPSHOT_BEFORE_ALLOWED_LABEL = joinTypes(SNAPSHOT_BEFORE_ALLOWED_TYPES);

    private GraphNodeBooleanFieldRules() {
    }

    /**
     * 校验单个节点 data 上的 alwaysRun、snapshotBefore。
     * 键不存在则跳过；挂在不允许的节点类型、或值不是 Boolean 时写入 errors。
     *
     * @param path     节点路径前缀，如 nodes[0]
     * @param nodeType 节点 type
     * @param data     节点 data，可为 null
     * @param errors   错误列表
     */
    public static void validate(String path, String nodeType, Map<String, Object> data, List<String> errors) {
        if (data == null || errors == null) {
            return;
        }
        validateOne(
                path,
                nodeType,
                data,
                GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN,
                ALWAYS_RUN_ALLOWED_TYPES,
                ALWAYS_RUN_ALLOWED_LABEL,
                errors);
        validateOne(
                path,
                nodeType,
                data,
                GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE,
                SNAPSHOT_BEFORE_ALLOWED_TYPES,
                SNAPSHOT_BEFORE_ALLOWED_LABEL,
                errors);
    }

    /**
     * 按节点 type 把允许字段上的常见脏值收成 Boolean，就地改写 data。
     * 仅处理该 type 允许的键；已是布尔则不动；无法识别则保留原值。
     *
     * @param nodeType 节点 type
     * @param data     节点 data，就地修改
     */
    public static void coerceForNodeType(String nodeType, Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String type = normalizeType(nodeType);
        if (ALWAYS_RUN_ALLOWED_TYPES.contains(type)) {
            coerceKey(data, GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN);
        }
        if (SNAPSHOT_BEFORE_ALLOWED_TYPES.contains(type)) {
            coerceKey(data, GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE);
        }
    }

    /**
     * 校验单个布尔开关字段：键存在时检查节点类型是否允许、值是否为 Boolean。
     */
    private static void validateOne(
            String path,
            String nodeType,
            Map<String, Object> data,
            String key,
            Set<String> allowedTypes,
            String allowedLabel,
            List<String> errors
    ) {
        if (!data.containsKey(key)) {
            return;
        }
        Object value = data.get(key);
        String type = normalizeType(nodeType);
        String prefix = path != null && !path.isBlank() ? path + " " : "";
        if (!allowedTypes.contains(type)) {
            String typeLabel = type.isEmpty() ? "(空)" : type;
            errors.add(prefix + "节点类型 " + typeLabel + " 不允许设置 " + key
                    + "（仅 " + allowedLabel + "）");
            return;
        }
        if (!(value instanceof Boolean)) {
            errors.add(prefix + key + " 须为 JSON 布尔（true/false），当前为："
                    + describeValueType(value));
        }
    }

    /**
     * 将 data 中指定键的常见真假脏值写成 Boolean；键不存在、已是布尔、或无法识别则不动。
     */
    static void coerceKey(Map<String, Object> data, String key) {
        if (!data.containsKey(key)) {
            return;
        }
        Object raw = data.get(key);
        if (raw instanceof Boolean) {
            return;
        }
        Boolean coerced = tryCoerceBoolean(raw);
        if (coerced != null) {
            data.put(key, coerced);
        }
    }

    /**
     * 尝试把原始值收成布尔。
     * 接受 Boolean、数字 1/0、字符串 true/false/1/0（大小写不敏感）；其它返回 null。
     */
    static Boolean tryCoerceBoolean(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d == 1d) {
                return Boolean.TRUE;
            }
            if (d == 0d) {
                return Boolean.FALSE;
            }
            return null;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s)) {
            return Boolean.TRUE;
        }
        if ("false".equals(s) || "0".equals(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 节点 type 去空白并转小写；null 当作空串 */
    private static String normalizeType(String nodeType) {
        if (nodeType == null) {
            return "";
        }
        return nodeType.trim().toLowerCase(Locale.ROOT);
    }

    /** 生成校验错误里「当前值」的中文描述 */
    private static String describeValueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "字符串 \"" + value + "\"";
        }
        if (value instanceof Number) {
            return "数字 " + value;
        }
        return value.getClass().getSimpleName();
    }

    /** 将允许类型集合拼成 a/b/c 形式的错误文案片段 */
    private static String joinTypes(Set<String> types) {
        return String.join("/", types);
    }
}
